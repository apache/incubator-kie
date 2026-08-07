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

import org.drools.core.common.BetaConstraints;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.JoinNode;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.TupleFactory;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.TupleMemory;
import org.drools.core.util.AbstractHashTable;
import org.drools.base.util.FastIterator;

import static org.drools.core.phreak.PhreakNodeOperations.doUpdatesReorderLeftMemory;
import static org.drools.core.phreak.PhreakNodeOperations.doUpdatesReorderRightMemory;
import static org.drools.core.phreak.PhreakNodeOperations.unlinkAndDeleteChildLeftTuple;
import static org.drools.core.phreak.PhreakNodeOperations.useLeftMemory;

public class PhreakJoinNode {

    private final ReteEvaluator reteEvaluator;

    public PhreakJoinNode(ReteEvaluator reteEvaluator) {
        this.reteEvaluator = reteEvaluator;
    }

    public void doNode(JoinNode joinNode,
                       LeftTupleSink sink,
                       BetaMemory bm,
                       TupleSets srcLeftTuples,
                       TupleSets stagedLeftTuples,
                       TupleSets trgLeftTuples) {

        TupleSets srcRightTuples = bm.getStagedRightTuples().takeAll();

        if (srcRightTuples.getDeleteFirst() != null) {
            doRightDeletes(bm, srcRightTuples, trgLeftTuples, stagedLeftTuples);
        }

        if (srcLeftTuples.getDeleteFirst() != null) {
            doLeftDeletes(bm, srcLeftTuples, trgLeftTuples, stagedLeftTuples);
        }

        if (srcRightTuples.getUpdateFirst() != null) {
            doUpdatesReorderRightMemory(bm, srcRightTuples);
        }

        if (srcLeftTuples.getUpdateFirst() != null ) {
            doUpdatesReorderLeftMemory(bm, srcLeftTuples);
        }

        if (srcRightTuples.getUpdateFirst() != null) {
            doRightUpdates(joinNode, sink, bm, srcRightTuples, trgLeftTuples, stagedLeftTuples);
        }

        if (srcLeftTuples.getUpdateFirst() != null ) {
            doLeftUpdates(joinNode, sink, bm, srcLeftTuples, trgLeftTuples, stagedLeftTuples);
        }

        if (srcRightTuples.getInsertFirst() != null) {
            doRightInserts(joinNode, sink, bm, srcRightTuples, trgLeftTuples);
        }

        if (srcLeftTuples.getInsertFirst() != null) {
            doLeftInserts(joinNode, sink, bm, srcLeftTuples, trgLeftTuples);
        }

        srcRightTuples.resetAll();
        srcLeftTuples.resetAll();
    }

    public void doLeftInserts(JoinNode joinNode,
                              LeftTupleSink sink,
                              BetaMemory<?> bm,
                              TupleSets srcLeftTuples,
                              TupleSets trgLeftTuples) {
        TupleMemory ltm = bm.getLeftTupleMemory();
        TupleMemory rtm = bm.getRightTupleMemory();
        Object contextEntry = bm.getContext();
        BetaConstraints constraints = joinNode.getRawConstraints();

        for (TupleImpl leftTuple = srcLeftTuples.getInsertFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();

            boolean useLeftMemory = useLeftMemory(joinNode, leftTuple);

            if (useLeftMemory) {
                ltm.add(leftTuple);
            }

            FastIterator<TupleImpl> it = joinNode.getRightIterator( rtm );

            constraints.updateFromTuple( contextEntry,
                                         reteEvaluator,
                                         leftTuple );

            for (TupleImpl rightTuple = joinNode.getFirstRightTuple( leftTuple, rtm, it ); rightTuple != null; rightTuple = it.next(rightTuple)) {
                if (constraints.isAllowedCachedLeft( contextEntry, rightTuple.getFactHandle() )) {
                    insertChildLeftTuple(trgLeftTuples,
                                         leftTuple,
                                         rightTuple,
                                         null,
                                         null,
                                         sink,
                                         useLeftMemory);
                }

            }
            leftTuple.clearStaged();
            leftTuple = next;
        }
        constraints.resetTuple( contextEntry );
    }

    public void doRightInserts(JoinNode joinNode,
                               LeftTupleSink sink,
                               BetaMemory<?> bm,
                               TupleSets srcRightTuples,
                               TupleSets trgLeftTuples) {
        TupleMemory ltm = bm.getLeftTupleMemory();
        TupleMemory rtm = bm.getRightTupleMemory();
        Object contextEntry = bm.getContext();
        BetaConstraints constraints = joinNode.getRawConstraints();

        if (srcRightTuples.getInsertSize() > 32 && rtm instanceof AbstractHashTable ) {
            ((AbstractHashTable) rtm).ensureCapacity(srcRightTuples.getInsertSize());
        }

        for (TupleImpl rightTuple = srcRightTuples.getInsertFirst(); rightTuple != null; ) {
            TupleImpl next = rightTuple.getStagedNext();
            rtm.add( rightTuple );

            if ( ltm != null && ltm.size() > 0 ) {
                FastIterator it = joinNode.getLeftIterator( ltm );

                constraints.updateFromFactHandle( contextEntry,
                                                  reteEvaluator,
                                                  rightTuple.getFactHandleForEvaluation() );

                for ( TupleImpl leftTuple = joinNode.getFirstLeftTuple(rightTuple, ltm, it ); leftTuple != null; leftTuple = (TupleImpl) it.next(leftTuple ) ) {
                    if ( leftTuple.getStagedType() == LeftTuple.UPDATE ) {
                        // ignore, as it will get processed via left iteration. Children cannot be processed twice
                        continue;
                    }

                    if ( constraints.isAllowedCachedRight(leftTuple, contextEntry
                                                         ) ) {
                        insertChildLeftTuple( trgLeftTuples,
                                              leftTuple,
                                              rightTuple,
                                              null,
                                              null,
                                              sink,
                                              true );
                    }
                }
            }
            rightTuple.clearStaged();
            rightTuple = next;
        }
        constraints.resetFactHandle( contextEntry );
    }

    public void doLeftUpdates(JoinNode joinNode,
                              LeftTupleSink sink,
                              BetaMemory<?> bm,
                              TupleSets srcLeftTuples,
                              TupleSets trgLeftTuples,
                              TupleSets stagedLeftTuples) {
        TupleMemory rtm = bm.getRightTupleMemory();
        Object contextEntry = bm.getContext();
        BetaConstraints constraints = joinNode.getRawConstraints();

        for (TupleImpl leftTuple = srcLeftTuples.getUpdateFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();

            constraints.updateFromTuple(contextEntry,
                                        reteEvaluator,
                                        leftTuple);

            FastIterator it = joinNode.getRightIterator(rtm);
            TupleImpl rightTuple = joinNode.getFirstRightTuple(leftTuple, rtm, it);

            // first check our index (for indexed nodes only) hasn't changed and we are returning the same bucket
            // if rightTuple is null, we assume there was a bucket change and that bucket is empty
            if (rtm.isIndexed() && !it.isFullIterator()) {
                // our index has changed, so delete all the previous propagations
                for (TupleImpl childLeftTuple = leftTuple.getFirstChild(); childLeftTuple != null; ) {
                    TupleImpl nextChild = childLeftTuple.getHandleNext();
                    if (rightTuple == null || rightTuple.getMemory() != childLeftTuple.getRightParent().getMemory()) {
                        unlinkAndDeleteChildLeftTuple(trgLeftTuples, stagedLeftTuples, childLeftTuple);
                    }
                    childLeftTuple = nextChild;
                }
            }

            // we can't do anything if RightTupleMemory is empty
            if (rightTuple != null) {
                doLeftUpdatesProcessChildren(leftTuple.getFirstChild(), leftTuple, rightTuple, stagedLeftTuples, contextEntry, constraints, sink, it, trgLeftTuples);
            }
            leftTuple.clearStaged();
            leftTuple = next;
        }
        constraints.resetTuple(contextEntry);
    }

    public TupleImpl doLeftUpdatesProcessChildren(TupleImpl childLeftTuple,
                                                  TupleImpl leftTuple,
                                                  TupleImpl rightTuple,
                                                  TupleSets stagedLeftTuples,
                                                  Object contextEntry,
                                                  BetaConstraints constraints,
                                                  LeftTupleSink sink,
                                                  FastIterator<TupleImpl> it,
                                                  TupleSets trgLeftTuples) {
        if (childLeftTuple == null) {
            // either we are indexed and changed buckets or
            // we had no children before, but there is a bucket to potentially match, so try as normal assert
            for (; rightTuple != null; rightTuple = it.next(rightTuple)) {
                if (constraints.isAllowedCachedLeft(contextEntry,
                                                    rightTuple.getFactHandle())) {
                    insertChildLeftTuple(trgLeftTuples,
                                         leftTuple,
                                         rightTuple,
                                         null,
                                         null,
                                         sink,
                                         true);
                }
            }
        } else {
            // in the same bucket, so iterate and compare
            for (; rightTuple != null; rightTuple = it.next(rightTuple)) {
                if (constraints.isAllowedCachedLeft(contextEntry,
                                                    rightTuple.getFactHandle())) {
                    // insert, childLeftTuple is not updated
                    if (childLeftTuple == null || childLeftTuple.getRightParent() != rightTuple) {
                        insertChildLeftTuple(trgLeftTuples,
                                             leftTuple,
                                             rightTuple,
                                             childLeftTuple,
                                             null,
                                             sink,
                                             true);
                    } else {
                        // update, childLeftTuple is updated
                        childLeftTuple.setPropagationContext(rightTuple.getPropagationContext());
                        updateChildLeftTuple(childLeftTuple, stagedLeftTuples, trgLeftTuples);

                        TupleImpl nextChildLeftTuple = childLeftTuple.getHandleNext();
                        childLeftTuple.reAddRight();
                        childLeftTuple = nextChildLeftTuple;
                    }
                } else if (childLeftTuple != null && childLeftTuple.getRightParent() == rightTuple) {
                    // delete, childLeftTuple is updated
                    TupleImpl nextChild = childLeftTuple.getHandleNext();
                    unlinkAndDeleteChildLeftTuple(trgLeftTuples, stagedLeftTuples, childLeftTuple);
                    childLeftTuple = nextChild;
                }
            }
        }

        return childLeftTuple;
    }

    public void doRightUpdates(JoinNode joinNode,
                               LeftTupleSink sink,
                               BetaMemory bm,
                               TupleSets srcRightTuples,
                               TupleSets trgLeftTuples,
                               TupleSets stagedLeftTuples) {
        TupleMemory ltm = bm.getLeftTupleMemory();
        Object contextEntry = bm.getContext();
        BetaConstraints constraints = joinNode.getRawConstraints();

        for (TupleImpl rightTuple = srcRightTuples.getUpdateFirst(); rightTuple != null; ) {
            TupleImpl next = rightTuple.getStagedNext();

            if ( ltm != null && ltm.size() > 0 ) {
                FastIterator it = joinNode.getLeftIterator( ltm );
                TupleImpl leftTuple = joinNode.getFirstLeftTuple(rightTuple, ltm, it );

                constraints.updateFromFactHandle( contextEntry,
                                                  reteEvaluator,
                                                  rightTuple.getFactHandleForEvaluation() );

                // first check our index (for indexed nodes only) hasn't changed and we are returning the same bucket
                // We assume a bucket change if leftTuple == null
                TupleImpl childLeftTuple = rightTuple.getFirstChild();
                if ( childLeftTuple != null && ltm.isIndexed() && !it.isFullIterator() && ( leftTuple == null || ( leftTuple.getMemory() != childLeftTuple.getLeftParent().getMemory() ) ) ) {
                    // our index has changed, so delete all the previous propagations
                    while ( childLeftTuple != null ) {
                        childLeftTuple.setPropagationContext( rightTuple.getPropagationContext() );
                        TupleImpl nextChild = childLeftTuple.getRightParentNext();
                        unlinkAndDeleteChildLeftTuple(trgLeftTuples, stagedLeftTuples, childLeftTuple);
                        childLeftTuple = nextChild;
                    }
                    // childLeftTuple is now null, so the next check will attempt matches for new bucket
                }

                // we can't do anything if LeftTupleMemory is empty
                if ( leftTuple != null ) {
                    doRightUpdatesProcessChildren( childLeftTuple, leftTuple, rightTuple, stagedLeftTuples, contextEntry, constraints, sink, it, trgLeftTuples );
                }
            }
            rightTuple.clearStaged();
            rightTuple = next;
        }
        constraints.resetFactHandle(contextEntry);
    }

    public TupleImpl doRightUpdatesProcessChildren(TupleImpl childLeftTuple,
                                                   TupleImpl leftTuple,
                                                   TupleImpl rightTuple,
                                                   TupleSets stagedLeftTuples,
                                                   Object contextEntry,
                                                   BetaConstraints constraints,
                                                   LeftTupleSink sink,
                                                   FastIterator it,
                                                   TupleSets trgLeftTuples) {
        if (childLeftTuple == null) {
            // either we are indexed and changed buckets or
            // we had no children before, but there is a bucket to potentially match, so try as normal assert
            for (; leftTuple != null; leftTuple = (TupleImpl) it.next(leftTuple)) {
                if (leftTuple.getStagedType() == LeftTuple.UPDATE) {
                    // ignore, as it will get processed via left iteration. Children cannot be processed twice
                    continue;
                }

                if (constraints.isAllowedCachedRight(leftTuple, contextEntry
                                                    )) {
                    insertChildLeftTuple(trgLeftTuples,
                                         leftTuple,
                                         rightTuple,
                                         null,
                                         null,
                                         sink,
                                         true);
                }
            }
        } else {
            // in the same bucket, so iterate and compare
            for (; leftTuple != null; leftTuple = (TupleImpl) it.next(leftTuple)) {
                if (leftTuple.getStagedType() == LeftTuple.UPDATE) {
                    // ignore, as it will get processed via left iteration. Children cannot be processed twice
                    continue;
                }
                if (constraints.isAllowedCachedRight(leftTuple, contextEntry
                                                    )) {
                    // insert, childLeftTuple is not updated
                    if (childLeftTuple == null || childLeftTuple.getLeftParent() != leftTuple) {
                        insertChildLeftTuple(trgLeftTuples,
                                             leftTuple,
                                             rightTuple,
                                             null,
                                             childLeftTuple,
                                             sink,
                                             true);
                    } else {
                        // update, childLeftTuple is updated
                        childLeftTuple.setPropagationContext(rightTuple.getPropagationContext());
                        updateChildLeftTuple(childLeftTuple, stagedLeftTuples, trgLeftTuples);

                        TupleImpl nextChildLeftTuple = childLeftTuple.getRightParentNext();
                        childLeftTuple.reAddLeft();
                        childLeftTuple = nextChildLeftTuple;
                    }
                } else if (childLeftTuple != null && childLeftTuple.getLeftParent() == leftTuple) {
                    // delete, childLeftTuple is updated
                    childLeftTuple.setPropagationContext(rightTuple.getPropagationContext());
                    TupleImpl nextChild = childLeftTuple.getRightParentNext();
                    unlinkAndDeleteChildLeftTuple(trgLeftTuples, stagedLeftTuples, childLeftTuple);
                    childLeftTuple = nextChild;
                }
            }
        }

        return childLeftTuple;
    }

    public void doLeftDeletes(BetaMemory bm,
                              TupleSets srcLeftTuples,
                              TupleSets trgLeftTuples,
                              TupleSets stagedLeftTuples) {
        TupleMemory ltm = bm.getLeftTupleMemory();

        for (TupleImpl leftTuple = srcLeftTuples.getDeleteFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();
            if (leftTuple.getMemory() != null) {
                // it may have been staged and never actually added
                ltm.remove(leftTuple);
            }

            if (leftTuple.getFirstChild() != null) {
                TupleImpl childLeftTuple = leftTuple.getFirstChild();

                while (childLeftTuple != null) {
                    TupleImpl nextChild = childLeftTuple.getHandleNext();
                    unlinkAndDeleteChildLeftTuple(trgLeftTuples, stagedLeftTuples, childLeftTuple);
                    childLeftTuple = nextChild;
                }
            }
            leftTuple.clearStaged();
            leftTuple = next;
        }
    }

    public void doRightDeletes(BetaMemory bm,
                               TupleSets srcRightTuples,
                               TupleSets trgLeftTuples,
                               TupleSets stagedLeftTuples) {
        TupleMemory rtm = bm.getRightTupleMemory();

        for (TupleImpl rightTuple = srcRightTuples.getDeleteFirst(); rightTuple != null; ) {
            TupleImpl next = rightTuple.getStagedNext();
            if (rightTuple.getMemory() != null) {
                // it may have been staged and never actually added
                rtm.remove(rightTuple);
            }

            if (rightTuple.getFirstChild() != null) {
                TupleImpl childLeftTuple = rightTuple.getFirstChild();
                childLeftTuple.setPropagationContext(rightTuple.getPropagationContext());
                while (childLeftTuple != null) {
                    TupleImpl nextChild = childLeftTuple.getRightParentNext();
                    unlinkAndDeleteChildLeftTuple(trgLeftTuples, stagedLeftTuples, childLeftTuple);
                    childLeftTuple = nextChild;
                }
            }
            rightTuple.clearStaged();
            rightTuple = next;
        }
    }

    public static void updateChildLeftTuple(TupleImpl childLeftTuple,
                                            TupleSets stagedLeftTuples,
                                            TupleSets trgLeftTuples) {
        if (!childLeftTuple.isStagedOnRight()) {
            switch ( childLeftTuple.getStagedType() ) {
                // handle clash with already staged entries
                case LeftTuple.INSERT:
                    // Was insert before, should continue as insert
                    stagedLeftTuples.removeInsert( childLeftTuple );
                    trgLeftTuples.addInsert( childLeftTuple );
                    break;
                case LeftTuple.UPDATE:
                    stagedLeftTuples.removeUpdate( childLeftTuple );
                    trgLeftTuples.addUpdate( childLeftTuple );
                    break;
                default:
                    // no clash, so just add
                    trgLeftTuples.addUpdate( childLeftTuple );
            }
        }
    }

    private static void insertChildLeftTuple( TupleSets trgLeftTuples,
                                              TupleImpl leftTuple,
                                              TupleImpl rightTuple,
                                              TupleImpl currentLeftChild,
                                              TupleImpl currentRightChild,
                                              LeftTupleSink sink,
                                              boolean leftTupleMemoryEnabled ) {
        if (!leftTuple.isExpired() && !rightTuple.isExpired()) {
            trgLeftTuples.addInsert(TupleFactory.createLeftTuple(leftTuple,
                                                                 rightTuple,
                                                                 currentLeftChild,
                                                                 currentRightChild,
                                                                 sink,
                                                                 leftTupleMemoryEnabled));
        }
    }
}
