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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.drools.base.base.ValueResolver;
import org.drools.base.rule.accessor.DataProvider;
import org.drools.base.rule.constraint.AlphaNodeFieldConstraint;
import org.drools.core.common.BetaConstraints;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.reteoo.TupleFactory;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.FromNode;
import org.drools.core.reteoo.FromNode.FromMemory;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.reteoo.TupleMemory;
import org.drools.base.util.FastIterator;
import org.drools.base.util.LinkedList;
import org.kie.api.runtime.rule.FactHandle;

import static org.drools.core.phreak.PhreakJoinNode.updateChildLeftTuple;
import static org.drools.core.phreak.PhreakNodeOperations.unlinkAndDeleteChildLeftTuple;
import static org.drools.core.phreak.PhreakNodeOperations.useLeftMemory;

public class PhreakFromNode {
    
    private final ReteEvaluator reteEvaluator;

    public PhreakFromNode(ReteEvaluator reteEvaluator) {
        this.reteEvaluator = reteEvaluator;
    }
    
    public void doNode(FromNode fromNode,
                       FromMemory fm,
                       LeftTupleSink sink,
                       TupleSets srcLeftTuples,
                       TupleSets stagedLeftTuples,
                       TupleSets trgLeftTuples) {

        if (srcLeftTuples.getDeleteFirst() != null) {
            doLeftDeletes(fm, srcLeftTuples, trgLeftTuples, stagedLeftTuples);
        }

        if (srcLeftTuples.getUpdateFirst() != null) {
            doLeftUpdates(fromNode, fm, sink, srcLeftTuples, trgLeftTuples, stagedLeftTuples);
        }

        if (srcLeftTuples.getInsertFirst() != null) {
            doLeftInserts(fromNode, fm, sink, srcLeftTuples, trgLeftTuples);
        }

        srcLeftTuples.resetAll();
    }

    public void doLeftInserts(FromNode fromNode,
                              FromMemory fm,
                              LeftTupleSink sink,
                              TupleSets srcLeftTuples,
                              TupleSets trgLeftTuples) {

        BetaMemory bm = fm.getBetaMemory();
        Object context = bm.getContext();
        BetaConstraints betaConstraints = fromNode.getBetaConstraints();
        AlphaNodeFieldConstraint[] alphaConstraints = fromNode.getAlphaConstraints();
        DataProvider dataProvider = fromNode.getDataProvider();
        Class<?> resultClass = fromNode.getResultClass();

        for (TupleImpl leftTuple = srcLeftTuples.getInsertFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();

            PropagationContext propagationContext = leftTuple.getPropagationContext();

            Map<Object, RightTuple> matches       = null;
            boolean                 useLeftMemory = useLeftMemory(fromNode, leftTuple);

            if (useLeftMemory) {
                fm.getBetaMemory().getLeftTupleMemory().add(leftTuple);
                matches = new LinkedHashMap<>();
                leftTuple.setContextObject( matches );
            }

            betaConstraints.updateFromTuple(context, reteEvaluator, leftTuple);

            for (final java.util.Iterator<?> it = dataProvider.getResults(leftTuple, reteEvaluator,
                                                                          fm.providerContext); it.hasNext(); ) {
                final Object object = it.next();
                if ( (object == null) || !resultClass.isAssignableFrom( object.getClass() ) ) {
                    continue; // skip anything if it not assignable
                }

                RightTuple rightTuple = fromNode.createRightTuple(leftTuple, propagationContext, reteEvaluator, object);

                if ( isAllowed( rightTuple.getFactHandle(), alphaConstraints, reteEvaluator, fm ) ) {
                    propagate( sink, leftTuple, rightTuple, betaConstraints, propagationContext, context, useLeftMemory, trgLeftTuples, null );
                }

                if (useLeftMemory) {
                    fromNode.addToCreatedHandlesMap(matches,
                                                    rightTuple);
                }
            }

            leftTuple.clearStaged();
            leftTuple = next;
        }
        betaConstraints.resetTuple(context);
    }

    public void doLeftUpdates(FromNode fromNode,
                              FromMemory fm,
                              LeftTupleSink sink,
                              TupleSets srcLeftTuples,
                              TupleSets trgLeftTuples,
                              TupleSets stagedLeftTuples) {
        BetaMemory bm = fm.getBetaMemory();
        Object context = bm.getContext();
        BetaConstraints betaConstraints = fromNode.getBetaConstraints();
        AlphaNodeFieldConstraint[] alphaConstraints = fromNode.getAlphaConstraints();
        DataProvider dataProvider = fromNode.getDataProvider();
        Class<?> resultClass = fromNode.getResultClass();

        for (TupleImpl leftTuple = srcLeftTuples.getUpdateFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();

            PropagationContext propagationContext = leftTuple.getPropagationContext();

            final Map<Object, RightTuple> previousMatches = (Map<Object, RightTuple>) leftTuple.getContextObject();
            final Map<Object, RightTuple> newMatches      = new HashMap<>();
            leftTuple.setContextObject( newMatches );

            betaConstraints.updateFromTuple(context, reteEvaluator, leftTuple);

            FastIterator<TupleImpl> rightIt = LinkedList.fastIterator;
            for (final java.util.Iterator<?> it = dataProvider.getResults(leftTuple, reteEvaluator, fm.providerContext); it.hasNext(); ) {
                final Object object = it.next();
                if ( (object == null) || !resultClass.isAssignableFrom( object.getClass() ) ) {
                    continue; // skip anything if it not assignable
                }

                RightTuple rightTuple = previousMatches.remove(object);

                if (rightTuple == null) {
                    // new match, propagate assert
                    rightTuple = fromNode.createRightTuple(leftTuple, propagationContext, reteEvaluator, object);
                } else {
                    // previous match, so reevaluate and propagate modify
                    if (rightIt.next(rightTuple) != null) {
                        // handle the odd case where more than one object has the same hashcode/equals value
                        previousMatches.put(object, (RightTuple) rightIt.next(rightTuple));
                        rightTuple.setNext(null);
                    }
                }

                if ( isAllowed( rightTuple.getFactHandle(), alphaConstraints, reteEvaluator, fm ) ) {
                    propagate( sink, leftTuple, rightTuple, betaConstraints, propagationContext, context, true, trgLeftTuples, stagedLeftTuples );
                    fromNode.addToCreatedHandlesMap(newMatches, rightTuple);
                } else {
                    deleteChildLeftTuple(propagationContext, trgLeftTuples, stagedLeftTuples, rightTuple.getFirstChild());
                }
            }

            for (RightTuple rightTuple : previousMatches.values()) {
                for (RightTuple current = rightTuple; current != null; current = (RightTuple) rightIt.next(current)) {
                    deleteChildLeftTuple(propagationContext, trgLeftTuples, stagedLeftTuples, current.getFirstChild());
                }
            }

            leftTuple.clearStaged();
            leftTuple = next;
        }
        betaConstraints.resetTuple(context);
    }

    public void doLeftDeletes(FromMemory fm,
                              TupleSets srcLeftTuples,
                              TupleSets trgLeftTuples,
                              TupleSets stagedLeftTuples) {
        BetaMemory bm = fm.getBetaMemory();
        TupleMemory ltm = bm.getLeftTupleMemory();

        for (TupleImpl leftTuple = srcLeftTuples.getDeleteFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();

            ltm.remove(leftTuple);

            if (leftTuple.getFirstChild() != null) {
                TupleImpl childLeftTuple = leftTuple.getFirstChild();

                while (childLeftTuple != null) {
                    childLeftTuple.setPropagationContext( leftTuple.getPropagationContext());
                    TupleImpl nextChild = childLeftTuple.getHandleNext();
                    unlinkAndDeleteChildLeftTuple(trgLeftTuples, stagedLeftTuples, childLeftTuple);
                    childLeftTuple = nextChild;
                }
            }

            leftTuple.clearStaged();
            leftTuple = next;
        }
    }

    public static boolean isAllowed( FactHandle factHandle,
                                     AlphaNodeFieldConstraint[] alphaConstraints,
                                     ValueResolver valueResolver,
                                     FromMemory fm ) {
        if (alphaConstraints != null) {
            for (int i = 0, length = alphaConstraints.length; i < length; i++) {
                if (!alphaConstraints[i].isAllowed(factHandle, valueResolver)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void propagate( LeftTupleSink sink,
                                  TupleImpl leftTuple,
                                  TupleImpl rightTuple,
                                  BetaConstraints betaConstraints,
                                  PropagationContext propagationContext,
                                  Object context,
                                  boolean useLeftMemory,
                                  TupleSets trgLeftTuples,
                                  TupleSets stagedLeftTuples ) {
        if (betaConstraints.isAllowedCachedLeft(context, rightTuple.getFactHandleForEvaluation())) {

            if (rightTuple.getFirstChild() == null) {
                // this is a new match, so propagate as assert
                TupleImpl childLeftTuple = TupleFactory.createLeftTuple(leftTuple,
                                                                        rightTuple,
                                                                        null,
                                                                        null,
                                                                        sink,
                                                                        useLeftMemory);
                childLeftTuple.setPropagationContext(propagationContext);
                trgLeftTuples.addInsert(childLeftTuple);
            } else {
                TupleImpl childLeftTuple = rightTuple.getFirstChild();
                childLeftTuple.setPropagationContext(propagationContext);
                updateChildLeftTuple(childLeftTuple, stagedLeftTuples, trgLeftTuples);
            }
        } else {
            deleteChildLeftTuple(propagationContext, trgLeftTuples, stagedLeftTuples, rightTuple.getFirstChild());
        }
    }

    public static void deleteChildLeftTuple(PropagationContext propagationContext,
                                            TupleSets trgLeftTuples,
                                            TupleSets stagedLeftTuples,
                                            TupleImpl childLeftTuple) {
        if (childLeftTuple != null) {
            childLeftTuple.setPropagationContext( propagationContext );
            unlinkAndDeleteChildLeftTuple(trgLeftTuples, stagedLeftTuples, childLeftTuple);
        }
    }
}
