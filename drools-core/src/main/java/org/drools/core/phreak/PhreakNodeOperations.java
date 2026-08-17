/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.drools.core.phreak;

import org.drools.core.base.DroolsQueryImpl;
import org.drools.core.common.BetaConstraints;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.reteoo.SubnetworkTuple;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.TupleMemory;
import org.drools.base.util.FastIterator;

import static org.drools.core.phreak.PhreakNotNode.updateBlockersAndPropagate;
import static org.drools.core.reteoo.BetaNode.getBetaMemory;

public class PhreakNodeOperations {
    
    public static boolean useLeftMemory(LeftTupleSource tupleSource,
                                        TupleImpl tuple) {
        boolean useLeftMemory = true;
        if (!tupleSource.isLeftTupleMemoryEnabled()) {
            // This is a hack, to not add closed DroolsQuery objects
            Object object = tuple.getRootTuple().getFactHandle().getObject();
            if (!(object instanceof DroolsQueryImpl) || !((DroolsQueryImpl) object).isOpen()) {
                useLeftMemory = false;
            }
        }
        return useLeftMemory;
    }

    public static void normalizeStagedTuples(TupleSets stagedLeftTuples,
                                             TupleImpl childLeftTuple) {
        if (!childLeftTuple.isStagedOnRight()) {
            switch (childLeftTuple.getStagedType()) {
                // handle clash with already staged entries
                case LeftTuple.INSERT:
                    stagedLeftTuples.removeInsert(childLeftTuple);
                    break;
                case LeftTuple.UPDATE:
                    stagedLeftTuples.removeUpdate(childLeftTuple);
                    break;
            }
        }
    }
    
    public static void findLeftTupleBlocker(BetaNode betaNode,
                                            TupleMemory rtm,
                                            Object contextEntry,
                                            BetaConstraints constraints,
                                            TupleImpl leftTuple,
                                            boolean useLeftMemory) {
        // This method will also remove rightTuples that are from subnetwork where no leftmemory use used
        FastIterator it = betaNode.getRightIterator(rtm);
        for (RightTuple rightTuple = betaNode.getFirstRightTuple(leftTuple, rtm, it); rightTuple != null;) {
            RightTuple nextRight = (RightTuple) it.next(rightTuple);
            if (constraints.isAllowedCachedLeft(contextEntry,
                                                rightTuple.getFactHandleForEvaluation())) {
                leftTuple.setBlocker(rightTuple);

                if (useLeftMemory) {
                    rightTuple.addBlocked((LeftTuple) leftTuple);
                    break;
                } else if (betaNode.getRightInput().inputIsTupleToObjectNode()) {
                    // If we aren't using leftMemory and the right input is a TupleToObjectNode, then we must iterate and find all subetwork right tuples and remove them
                    // so we don't break
                    rtm.remove(rightTuple);
                } else {
                    break;
                }
            }
            rightTuple = nextRight;
        }
    }
    
    public static void unlinkAndDeleteChildLeftTuple(TupleSets trgLeftTuples,
                                                     TupleSets stagedLeftTuples,
                                                     TupleImpl childLeftTuple) {
        childLeftTuple.unlinkFromRightParent();
        childLeftTuple.unlinkFromLeftParent();
        deleteChildLeftTuple(childLeftTuple, trgLeftTuples, stagedLeftTuples);
    }
    
    
    public static void deleteChildLeftTuple(TupleImpl childLeftTuple, TupleSets trgLeftTuples, TupleSets stagedLeftTuples) {
        if (childLeftTuple.isStagedOnRight()) {
            ((SubnetworkTuple) childLeftTuple).moveStagingFromRightToLeft();
        } else {
            switch (childLeftTuple.getStagedType()) {
                // handle clash with already staged entries
                case Tuple.INSERT:
                    stagedLeftTuples.removeInsert(childLeftTuple);
                    trgLeftTuples.addNormalizedDelete(childLeftTuple);
                    return;
                case Tuple.UPDATE:
                    stagedLeftTuples.removeUpdate(childLeftTuple);
                    break;
            }
        }
        trgLeftTuples.addDelete(childLeftTuple);
    }
    

    public static void doAddExistentialRightMemoryForReorder(TupleMemory rtm,
                                                              RightTuple rightTuple,
                                                              boolean resumeFromCurrent) {
        rtm.add(rightTuple);

        if (resumeFromCurrent) {
            if (rightTuple.getBlocked() != null && rightTuple.getTempNextRightTuple() == null) {
                // the next RightTuple was null, but current RightTuple was added back into the same bucket, so reset as root blocker to re-match can be attempted
                rightTuple.setTempNextRightTuple(rightTuple);
            }
        }

        doUpdatesReorderChildLeftTuple(rightTuple);
    }

    public static void doRemoveExistentialRightMemoryForReorder(TupleMemory rtm,
                                                                 RightTuple rightTuple,
                                                                 boolean resumeFromCurrent) {
        if (rightTuple.getMemory() != null) {

            if (resumeFromCurrent) {
                if (rightTuple.getBlocked() != null) {
                    // look for a non-staged right tuple first forward ...
                    RightTuple tempRightTuple = (RightTuple) rightTuple.getNext();
                    while (tempRightTuple != null && tempRightTuple.getStagedType() != LeftTuple.NONE) {
                        // next cannot be an updated or deleted rightTuple
                        tempRightTuple = (RightTuple) tempRightTuple.getNext();
                    }

                    // ... and if cannot find one try backward
                    if (tempRightTuple == null) {
                        tempRightTuple = (RightTuple) rightTuple.getPrevious();
                        while (tempRightTuple != null && tempRightTuple.getStagedType() != LeftTuple.NONE) {
                            // next cannot be an updated or deleted rightTuple
                            tempRightTuple = (RightTuple) tempRightTuple.getPrevious();
                        }
                    }

                    rightTuple.setTempNextRightTuple(tempRightTuple);
                }
            }

            rightTuple.setTempBlocked(rightTuple.getBlocked());
            rightTuple.setBlocked(null);
            rtm.remove(rightTuple);
        }
    }

    

    public static void doUpdatesReorderRightMemory(BetaMemory bm,
                                                   TupleSets srcRightTuples) {
        TupleMemory rtm = bm.getRightTupleMemory();

        for (TupleImpl rightTuple = srcRightTuples.getUpdateFirst(); rightTuple != null; rightTuple = rightTuple.getStagedNext()) {
            if (rightTuple.getMemory() != null) {
                rtm.removeAdd(rightTuple);
                doUpdatesReorderChildLeftTuple(rightTuple);
            }
        }
    }

    public static void doUpdatesReorderChildLeftTuple(TupleImpl rightTuple) {
        for (TupleImpl childLeftTuple = rightTuple.getFirstChild(); childLeftTuple != null;) {
            TupleImpl childNext = childLeftTuple.getRightParentNext();
            childLeftTuple.reAddLeft();
            childLeftTuple = childNext;
        }
    }
    

    public static void doExistentialUpdatesReorderChildLeftTuple(ReteEvaluator reteEvaluator,
                                                                 NotNode notNode,
                                                                 RightTuple rightTuple) {
        BetaMemory bm = getBetaMemory(notNode, reteEvaluator);
        TupleMemory rtm = bm.getRightTupleMemory();

        boolean resumeFromCurrent = !(notNode.isIndexedUnificationJoin() || rtm.getIndexType().isComparison());
        doRemoveExistentialRightMemoryForReorder(rtm, rightTuple, resumeFromCurrent);
        doAddExistentialRightMemoryForReorder(rtm, rightTuple, resumeFromCurrent);

        updateBlockersAndPropagate(notNode, rightTuple, reteEvaluator, rtm, bm.getContext(), notNode.getRawConstraints(), !resumeFromCurrent, null, null, null);
    }
    

    public static void doUpdatesReorderLeftMemory(BetaMemory bm,
                                                  TupleSets srcLeftTuples) {
        TupleMemory ltm = bm.getLeftTupleMemory();

        // sides must first be re-ordered, to ensure iteration integrity
        for (TupleImpl leftTuple = srcLeftTuples.getUpdateFirst(); leftTuple != null; leftTuple = leftTuple.getStagedNext()) {
            if (leftTuple.getMemory() != null) {
                ltm.remove(leftTuple);
            }
        }

        for (TupleImpl leftTuple = srcLeftTuples.getUpdateFirst(); leftTuple != null; leftTuple = leftTuple.getStagedNext()) {
            ltm.add(leftTuple);
            for (TupleImpl childLeftTuple = leftTuple.getFirstChild(); childLeftTuple != null;) {
                TupleImpl childNext = childLeftTuple.getHandleNext();
                childLeftTuple.reAddRight();
                childLeftTuple = childNext;
            }
        }
    }
    
    public static void doUpdatesExistentialReorderLeftMemory(BetaMemory bm,
                                                             TupleSets srcLeftTuples) {
        TupleMemory ltm = bm.getLeftTupleMemory();

        // sides must first be re-ordered, to ensure iteration integrity
        for (TupleImpl leftTuple = srcLeftTuples.getUpdateFirst(); leftTuple != null; leftTuple = leftTuple.getStagedNext()) {
            if (leftTuple.getMemory() != null) {
                ltm.remove(leftTuple);
            }
        }

        for (TupleImpl leftTuple = srcLeftTuples.getUpdateFirst(); leftTuple != null; leftTuple = leftTuple.getStagedNext()) {
            RightTuple blocker = leftTuple.getBlocker();
            if (blocker == null) {
                ltm.add(leftTuple);
                for (TupleImpl childLeftTuple = leftTuple.getFirstChild(); childLeftTuple != null;) {
                    TupleImpl childNext = childLeftTuple.getHandleNext();
                    childLeftTuple.reAddRight();
                    childLeftTuple = childNext;
                }
            } else if (blocker.getStagedType() != LeftTuple.NONE) {
                // it's blocker is also being updated, so remove to force it to start from the beginning
                blocker.removeBlocked((LeftTuple) leftTuple);
            }
        }
    }

    
    public static void doUpdatesExistentialReorderRightMemory(BetaMemory bm,
                                                              BetaNode betaNode,
                                                              TupleSets srcRightTuples) {
        TupleMemory rtm = bm.getRightTupleMemory();

        boolean resumeFromCurrent = !(betaNode.isIndexedUnificationJoin() || rtm.getIndexType().isComparison());

        // remove all the staged rightTuples from the memory before to readd them all
        // this is to avoid split bucket when an updated rightTuple hasn't been moved yet
        // and so it is the first entry in the wrong bucket

        if (rtm.getIndexType() != TupleMemory.IndexType.NONE) {
            for (TupleImpl rightTuple = srcRightTuples.getDeleteFirst(); rightTuple != null; rightTuple = rightTuple.getStagedNext()) {
                rtm.remove(rightTuple);
            }
        }

        for (TupleImpl rightTuple = srcRightTuples.getUpdateFirst(); rightTuple != null; rightTuple = rightTuple.getStagedNext()) {
            doRemoveExistentialRightMemoryForReorder(rtm, (RightTuple) rightTuple, resumeFromCurrent);
        }

        for (TupleImpl rightTuple = srcRightTuples.getUpdateFirst(); rightTuple != null; rightTuple = rightTuple.getStagedNext()) {
            doAddExistentialRightMemoryForReorder(rtm, (RightTuple) rightTuple, resumeFromCurrent);
        }

        if (rtm.getIndexType() != TupleMemory.IndexType.NONE) {
            for (TupleImpl rightTuple = srcRightTuples.getDeleteFirst(); rightTuple != null; rightTuple = rightTuple.getStagedNext()) {
                rtm.add(rightTuple);
            }
        }
    }

}
