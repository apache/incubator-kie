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
package org.drools.core.impl;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.drools.core.common.DefaultEventHandle;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.WorkingMemoryAction;
import org.drools.core.marshalling.MarshallerReaderContext;
import org.drools.base.phreak.PropagationEntry;
import org.drools.base.phreak.actions.AbstractPropagationEntry;
import org.drools.core.phreak.actions.AbstractPartitionedPropagationEntry;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.RightTuple;

import static org.drools.core.common.PhreakPropagationContextFactory.createPropagationContextForFact;

public class WorkingMemoryReteExpireAction
        extends AbstractPropagationEntry<ReteEvaluator>
        implements WorkingMemoryAction, Externalizable {

    protected DefaultEventHandle factHandle;
    protected ObjectTypeNode node;

    public WorkingMemoryReteExpireAction() { }

    public WorkingMemoryReteExpireAction(final DefaultEventHandle factHandle) {
        this.factHandle = factHandle;
    }

    public WorkingMemoryReteExpireAction(final DefaultEventHandle factHandle,
                                         final ObjectTypeNode node) {
        this(factHandle);
        this.node = node;
        factHandle.increaseOtnCount();
    }

    public DefaultEventHandle getFactHandle() {
        return factHandle;
    }

    public void setFactHandle(DefaultEventHandle factHandle) {
        this.factHandle = factHandle;
    }

    public ObjectTypeNode getNode() {
        return node;
    }

    public void setNode(ObjectTypeNode node) {
        this.node = node;
    }

    public WorkingMemoryReteExpireAction(MarshallerReaderContext context) throws IOException {
        this.factHandle = (DefaultEventHandle)context.getHandles().get(context.readLong());
        final int nodeId = context.readInt();
        this.node = (ObjectTypeNode) context.getSinks().get(nodeId);
    }

    public void internalExecute(ReteEvaluator reteEvaluator) {
        if (!factHandle.isValid()) {
            return;
        }

        PropagationContext context = createPropagationContextForFact( reteEvaluator, factHandle, PropagationContext.Type.EXPIRATION );
        reteEvaluator.getActivationsManager().registerExpiration( context );

        factHandle.forEachLeftTuple( ObjectTypeNode::expireLeftTuple );
        factHandle.forEachRightTuple( rt -> {
            ((RightTuple)rt).setExpired(reteEvaluator, context);
            ObjectTypeNode.expireRightTuple(rt);
        } );

        expireFactHandle( factHandle );
    }

    private static void expireFactHandle( DefaultEventHandle factHandle) {
        factHandle.decreaseOtnCount();
        if (factHandle.getOtnCount() <= 0) {
            factHandle.setExpired( true );
            factHandle.setPendingRemoveFromStore( true );
        }
    }

    @Override
    public boolean isPartitionSplittable() {
        return true;
    }

    @Override
    public PropagationEntry getSplitForPartition( int partitionNr ) {
        return new PartitionAwareWorkingMemoryReteExpireAction( factHandle, node, partitionNr );
    }

    @Override
    public String toString() {
        return "Expiration of " + factHandle.getObject();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(new DefaultEventHandle(factHandle.getId(), factHandle.getEntryPointId())); // only for STORES_ONLY strategy. Just keep id and entryPointId to be rewired
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.factHandle = (DefaultEventHandle) in.readObject();
    }

    public static class PartitionAwareWorkingMemoryReteExpireAction extends AbstractPartitionedPropagationEntry<ReteEvaluator> {
        private final DefaultEventHandle factHandle;
        private final ObjectTypeNode node;

        public PartitionAwareWorkingMemoryReteExpireAction(DefaultEventHandle factHandle, ObjectTypeNode node, int partition) {
            super( partition );
            this.factHandle = factHandle;
            this.node = node;
        }

        @Override
        public void internalExecute(ReteEvaluator reteEvaluator ) {
            if (!factHandle.isValid()) {
                return;
            }

            PropagationContext context = createPropagationContextForFact( reteEvaluator, factHandle, PropagationContext.Type.EXPIRATION );
            reteEvaluator.getActivationsManager().getPartitionedAgenda( partition ).registerExpiration( context );

            DefaultFactHandle.CompositeLinkedTuples linkedTuples = ( (DefaultFactHandle.CompositeLinkedTuples) factHandle.getLinkedTuples() );
            linkedTuples.forEachLeftTuple( partition, ObjectTypeNode::expireLeftTuple );
            linkedTuples.forEachRightTuple( partition, rt -> {
                ((RightTuple)rt).setExpired(reteEvaluator, context);
                ObjectTypeNode.expireRightTuple(rt);
            });

            if (isMainPartition()) {
                expireFactHandle( factHandle );
            }
        }

        @Override
        public String toString() {
            return "Expiration of " + factHandle.getObject() + " for partition " + partition;
        }
    }

}
