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

import java.util.Collection;

import org.drools.base.phreak.ReactiveObject;
import org.drools.base.phreak.actions.AbstractPropagationEntry;
import org.drools.base.reteoo.BaseTuple;
import org.drools.core.common.BetaConstraints;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.reteoo.LeftTupleSinkNode;
import org.drools.core.reteoo.ReactiveFromNode;
import org.drools.core.reteoo.ReactiveFromNodeLeftTuple;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.TupleImpl;

import static org.drools.core.phreak.PhreakFromNode.deleteChildLeftTuple;
import static org.drools.core.phreak.PhreakFromNode.isAllowed;
import static org.drools.core.phreak.PhreakFromNode.propagate;
import static org.drools.core.phreak.PhreakNodeOperations.useLeftMemory;

public class ReactiveObjectUtil {

    public enum ModificationType {
        NONE, MODIFY, ADD, REMOVE
    }

    public static void notifyModification(ReactiveObject reactiveObject) {
        notifyModification(reactiveObject, reactiveObject.getTuples(), ModificationType.MODIFY);
    }

    public static void notifyModification(Object object, Collection<BaseTuple> leftTuples, ModificationType type) {
        for (BaseTuple baseTuple : leftTuples) {
            Tuple tuple = (Tuple) baseTuple;
            if (!( (ReactiveFromNodeLeftTuple) tuple ).updateModificationState( object, type )) {
                continue;
            }
            PropagationContext propagationContext = tuple.getPropagationContext();
            ReactiveFromNode node = (ReactiveFromNode) tuple.getSink();

            LeftTupleSinkNode sink = node.getSinkPropagator().getFirstLeftTupleSink();
            ReteEvaluator reteEvaluator =((InternalFactHandle) propagationContext.getFactHandle()).getReteEvaluator();

            reteEvaluator.addPropagation(new ReactivePropagation(object, (ReactiveFromNodeLeftTuple)tuple, propagationContext, node, sink, type));
        }
    }

    static class ReactivePropagation extends AbstractPropagationEntry<ReteEvaluator> {

        private final Object object;
        private final ReactiveFromNodeLeftTuple leftTuple;
        private final PropagationContext propagationContext;
        private final ReactiveFromNode node;
        private final LeftTupleSinkNode sink;
        private final ModificationType type;

        ReactivePropagation( Object object, ReactiveFromNodeLeftTuple leftTuple, PropagationContext propagationContext, ReactiveFromNode node, LeftTupleSinkNode sink, ModificationType type ) {
            this.object = object;
            this.leftTuple = leftTuple;
            this.propagationContext = propagationContext;
            this.node = node;
            this.sink = sink;
            this.type = type;
        }

        @Override
        public void internalExecute(ReteEvaluator reteEvaluator ) {
            if ( leftTuple.resetModificationState( object ) == ModificationType.NONE ) {
                return;
            }

            ReactiveFromNode.ReactiveFromMemory mem = reteEvaluator.getNodeMemory(node);
            InternalFactHandle factHandle = node.createFactHandle( reteEvaluator, object );

            if ( type != ModificationType.REMOVE && isAllowed( factHandle, node.getAlphaConstraints(), reteEvaluator, mem ) ) {
                Object context = mem.getBetaMemory().getContext();
                BetaConstraints betaConstraints = node.getBetaConstraints();
                betaConstraints.updateFromTuple( context, reteEvaluator, leftTuple );

                propagate( sink,
                           leftTuple,
                           new RightTuple(factHandle ),
                           betaConstraints,
                           propagationContext,
                           context,
                           useLeftMemory(node, leftTuple),
                           mem.getStagedLeftTuples(),
                           null );
            } else {
                TupleImpl childLeftTuple = leftTuple.getFirstChild();
                while (childLeftTuple != null) {
                    TupleImpl next = childLeftTuple.getHandleNext();
                    if ( object == childLeftTuple.getFactHandle().getObject() ) {
                        deleteChildLeftTuple( propagationContext, mem.getStagedLeftTuples(), null, childLeftTuple );
                    }
                    childLeftTuple = next;
                }
            }

            mem.getBetaMemory().setNodeDirty(node, reteEvaluator);
        }
    }
}
