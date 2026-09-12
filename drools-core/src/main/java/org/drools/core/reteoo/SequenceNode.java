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
package org.drools.core.reteoo;

import org.drools.base.base.ObjectType;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.reteoo.DynamicFilter;
import org.drools.base.reteoo.DynamicFilterProto;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.reteoo.sequencing.Sequence.SequenceMemory;
import org.drools.base.rule.Pattern;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.common.TupleSetsImpl;
import org.drools.core.common.UpdateContext;
import org.drools.core.reteoo.SequenceNode.SequenceNodeMemory;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.base.reteoo.sequencing.Sequencer;
import org.drools.core.reteoo.sequencing.SequencerMemoryImpl;
import org.drools.base.reteoo.sequencing.SequencerMemory;
import org.drools.base.util.AbstractLinkedListNode;
import org.drools.base.util.LinkedList;
import org.drools.core.util.index.TupleList;
import org.drools.util.bitmask.BitMask;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;

public class SequenceNode extends LeftTupleSource
    implements
    LeftTupleNode,
    LeftTupleSinkNode,
    MemoryFactory<SequenceNodeMemory> {

    private static final long      serialVersionUID = 510l;

    protected boolean              tupleMemoryEnabled;

    private Sequencer sequencer;

    private AlphaAdapter[] alphaAdapters;

    private DynamicFilterProto[] dynamicFilters;

    private LeftTupleSinkNode      previousTupleSinkNode;

    private LeftTupleSinkNode      nextTupleSinkNode;

    public SequenceNode() {

    }

    public SequenceNode(final int id,
                        final LeftTupleSource tupleSource,
                        final BuildContext context) {
        super(id, context);
        setLeftTupleSource(tupleSource);
        this.setObjectCount(leftInput.getObjectCount()); // 'sequence' nodes do not increase the object count
        this.tupleMemoryEnabled = context.isTupleMemoryEnabled();

        initMasks(context);

        hashcode = calculateHashCode();
        setStreamMode(true);
    }

    public void setAlphaAdapters(AlphaAdapter[] adapters) {
        this.alphaAdapters = adapters;
    }

    public DynamicFilterProto[] getDynamicFilters() {
        return dynamicFilters;
    }

    public void setDynamicFilters(DynamicFilterProto[] dynamicFilters) {
        this.dynamicFilters = dynamicFilters;
    }

    public void doAttach(BuildContext context) {
        super.doAttach(context);
        this.leftInput.addTupleSink( this, context );
    }

    public void networkUpdated(UpdateContext updateContext) {
        this.leftInput.networkUpdated(updateContext);
    }

    public String toString() {
        return "[SequenceNode(" + this.id + ")]]";
    }

    private int calculateHashCode() {
        int result = this.leftInput.hashCode();
        if (this.sequencer != null) {
            result = 31 * result + System.identityHashCode(this.sequencer.getSequence());
        }
        return result;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }

        if ( object == null || !(object instanceof SequenceNode) || this.hashCode() != object.hashCode() ) {
            return false;
        }

        SequenceNode other = (SequenceNode) object;
        return this.leftInput.getId() == other.leftInput.getId()
            && this.sequencer.getSequence() == other.sequencer.getSequence();
    }

    public SequenceNodeMemory createMemory(final RuleBaseConfiguration config, ReteEvaluator reteEvaluator) {
        LinkedList<DynamicFilter>[] activeFilters = new LinkedList[alphaAdapters.length];
        SequenceNodeMemory          memory  = new SequenceNodeMemory(this, activeFilters, new DynamicFilter[dynamicFilters.length]);
        return memory;
    }

    public SequencerMemory createSequencerMemory(TupleImpl lt, LeftTupleSink sink, SequenceNodeMemory nodeMemory) {
        SequencerMemory sequencerMemory = new SequencerMemoryImpl(sequencer, lt, sink, nodeMemory);

        return sequencerMemory;
    }

    public Sequencer getSequencer() {
        return sequencer;
    }

    public void setSequencer(Sequencer sequencer) {
        this.sequencer = sequencer;
        this.hashcode = calculateHashCode();
    }

    public boolean isLeftTupleMemoryEnabled() {
        return tupleMemoryEnabled;
    }

    public LeftTupleSinkNode getNextLeftTupleSinkNode() {
        return this.nextTupleSinkNode;
    }

    public void setNextLeftTupleSinkNode(final LeftTupleSinkNode next) {
        this.nextTupleSinkNode = next;
    }

    public LeftTupleSinkNode getPreviousLeftTupleSinkNode() {
        return this.previousTupleSinkNode;
    }

    public void setPreviousLeftTupleSinkNode(final LeftTupleSinkNode previous) {
        this.previousTupleSinkNode = previous;
    }

    public int getType() {
        return NodeTypeEnums.SequenceNode;
    }

    public static class SequenceNodeMemory extends AbstractLinkedListNode<Memory>
        implements
        Externalizable,
        Memory {

        private static final long serialVersionUID = 510l;

        private TupleMemory       leftTupleMemory;

        private TupleSets         stagedChildTuples;

        private SegmentMemory     memory;

        private LinkedList<DynamicFilter>[] activeFilters;

        private DynamicFilter[] filters;

        private SequenceNode node;

        private long          nodePosMaskBit;

        public SequenceNodeMemory(SequenceNode node, LinkedList<DynamicFilter>[] activeFilters, DynamicFilter[] filters) {
            this.node = node;
            this.stagedChildTuples = new TupleSetsImpl();
            this.leftTupleMemory = new TupleList();
            this.activeFilters = activeFilters;
            this.filters = filters;
        }

        public void addActiveFilter(DynamicFilter filter) {
            int adapterIdx = filter.getAdapterIndex();
            if (this.activeFilters[adapterIdx] == null) {
                this.activeFilters[adapterIdx] = new LinkedList<>();
            }
            this.activeFilters[adapterIdx].add(filter);
        }

        public void removeActiveFilter(DynamicFilter filter) {
            this.activeFilters[filter.getAdapterIndex()].remove(filter);
        }

        public LinkedList<DynamicFilter>[] getActiveFilters() {
            return this.activeFilters;
        }

        public DynamicFilter[] getFilters() {
            return filters;
        }

        public void setFilters(DynamicFilter[] filters) {
            this.filters = filters;
        }

        public TupleMemory getLeftTupleMemory() {
            return leftTupleMemory;
        }

        public TupleSets getStagedChildTuples() {
            return stagedChildTuples;
        }

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            throw new UnsupportedOperationException("SequenceNodeMemory does not support serialization");
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            throw new UnsupportedOperationException("SequenceNodeMemory does not support serialization");
        }

        public int getNodeType() {
            return NodeTypeEnums.SequenceNode;
        }

        public void setSegmentMemory(SegmentMemory smem) {
            this.memory = smem;
        }

        public SegmentMemory getSegmentMemory() {
            return this.memory;
        }

        public void reset() {
            leftTupleMemory.clear();
            stagedChildTuples.resetAll();
            for (int i = 0; i < activeFilters.length; i++) {
                if (activeFilters[i] != null) {
                    activeFilters[i].clear();
                }
            }
            java.util.Arrays.fill(filters, null);
        }

        public DynamicFilter getActiveDynamicFilter(int filterIndex) {
            DynamicFilter filter = filters[filterIndex];

            if (filter == null) {
                DynamicFilterProto proto = node.getDynamicFilters()[filterIndex];
                filter = new DynamicFilter(proto);
                filters[filterIndex] = filter;
                addActiveFilter(filter);
            } else if (filter.getSignalAdapters().isEmpty()) {
                addActiveFilter(filter);
            }

            return filter;
        }

        public void setNodePosMaskBit(long nodePosMaskBit) {
            this.nodePosMaskBit = nodePosMaskBit;
        }

        public long getNodePosMaskBit() {
            return nodePosMaskBit;
        }
    }

    @Override
    public ObjectTypeNode getObjectTypeNode() {
        return leftInput.getObjectTypeNode();
    }

    protected boolean doRemove(final RuleRemovalContext context,
                               final ReteooBuilder builder) {
        if (!this.isInUse()) {
            getLeftTupleSource().removeTupleSink(this);

            if (alphaAdapters != null) {
                for (AlphaAdapter adapter : alphaAdapters) {
                    ((ObjectSource) adapter.getParent()).removeObjectSink(adapter);
                }
            }
            return true;
        }
        return false;
    }


    public static class AlphaAdapter extends ObjectSource
            implements
            ObjectSinkNode,
            RightTupleSink {
        private int          adapterIndex;

        private ObjectTypeNodeId otnId;

        private SequenceNode node;


        public AlphaAdapter(int id, ObjectSource source, RuleBasePartitionId partitionId, SequenceNode node, int adapterIndex) {
            super(id, source, partitionId);
            this.adapterIndex = adapterIndex;
            this.node = node;
        }

        public void assertObject(final InternalFactHandle factHandle,
                                 final PropagationContext pctx,
                                 final ReteEvaluator reteEvaluator) {
            SequenceNodeMemory memory = reteEvaluator.getNodeMemory(node);

            LinkedList<DynamicFilter> filters = memory.getActiveFilters()[adapterIndex];
            if (filters != null) {
                for (DynamicFilter f = filters.getFirst(); f != null; ) {
                    DynamicFilter next = f.getNext();
                    f.assertObject(factHandle, reteEvaluator);
                    f = next;
                }
            }
        }

        public void propagate() {

        }

        public void modifyObject(InternalFactHandle factHandle,
                                 ModifyPreviousTuples modifyPreviousTuples,
                                 PropagationContext context,
                                 ReteEvaluator reteEvaluator) {

        }

        @Override
        public void retractRightTuple(TupleImpl rightTuple, PropagationContext context, ReteEvaluator reteEvaluator) {
            // for now assuming we don't have any deletes, but we could add this later.
        }

        @Override
        public void modifyRightTuple(TupleImpl rightTuple, PropagationContext context, ReteEvaluator reteEvaluator) {
            // for now assuming we don't have any modifies, but we could add this later.
            // typically just gets treated as another add, as the processor is not that stateful (for this use case) - to keep it fast.
        }

        public int getId() {
            return super.getId();
        }

        public void byPassModifyToBetaNode(InternalFactHandle factHandle,
                                           ModifyPreviousTuples modifyPreviousTuples,
                                           PropagationContext context,
                                           ReteEvaluator reteEvaluator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ObjectSinkNode getNextObjectSinkNode() {
            return null;
        }

        @Override
        public void setNextObjectSinkNode(ObjectSinkNode next) {
            // not used — AlphaAdapter is not part of the sink propagation chain
        }

        @Override
        public ObjectSinkNode getPreviousObjectSinkNode() {
            return null;
        }

        @Override
        public void setPreviousObjectSinkNode(ObjectSinkNode previous) {
            // not used — AlphaAdapter is not part of the sink propagation chain
        }

        @Override
        public BitMask calculateDeclaredMask(Pattern pattern, ObjectType modifiedType, List<String> settableProperties) {
            return null;
        }

        @Override
        public void updateSink(ObjectSink sink, PropagationContext context, InternalWorkingMemory workingMemory) {

        }

        @Override
        public ObjectTypeNodeId getInputOtnId() {
            return otnId;
        }

        public int getType() {
            return NodeTypeEnums.SequenceNode;
        }

        public void doAttach(BuildContext context) {
            super.doAttach(context);
            this.source.addObjectSink(this);
        }
    }

    public static class PhreakSequenceNode {
        private final ReteEvaluator reteEvaluator;

        public PhreakSequenceNode(ReteEvaluator reteEvaluator) {
            this.reteEvaluator = reteEvaluator;
        }

        public void doNode(SequenceNode node,
                           SequenceNodeMemory memory,
                           LeftTupleSink sink,
                           TupleSets srcLeftTuples,
                           TupleSets trgLeftTuples,
                           TupleSets stagedLeftTuples) {
            if (srcLeftTuples.getDeleteFirst() != null) {
                doLeftDeletes(node, srcLeftTuples, trgLeftTuples);
            }

            if (srcLeftTuples.getUpdateFirst() != null) {
                doLeftUpdates(node, srcLeftTuples, trgLeftTuples);
            }

            if (srcLeftTuples.getInsertFirst() != null) {
                doLeftInserts(node, memory, sink, srcLeftTuples);
            }

            srcLeftTuples.resetAll();

            if (memory.getStagedChildTuples().getInsertFirst() != null) {
                for (TupleImpl tuple = memory.getStagedChildTuples().getInsertFirst(); tuple != null; ) {
                    TupleImpl next = tuple.getStagedNext();
                    trgLeftTuples.addInsert(tuple);
                    tuple.clearStaged();
                    tuple = next;
                }
                
                memory.getStagedChildTuples().resetAll();
            }

            memory.getSegmentMemory().updateCleanNodeMask(memory.getNodePosMaskBit());
        }

        private void doLeftUpdates(SequenceNode node,
                                   TupleSets srcLeftTuples,
                                   TupleSets trgLeftTuples) {
            for (TupleImpl leftTuple = srcLeftTuples.getUpdateFirst(); leftTuple != null; ) {
                TupleImpl next = leftTuple.getStagedNext();

                SequencerMemory sequencerMemory = (SequencerMemory) leftTuple.getContextObject();
                SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(sequencerMemory.getSequencer().getSequence());
                node.getSequencer().stop(sequenceMemory, reteEvaluator);
                SequenceNodeMemory nodeMemoryForUpdate = reteEvaluator.getNodeMemory(node);
                deleteChildren(nodeMemoryForUpdate.getStagedChildTuples(), trgLeftTuples, leftTuple);

                node.getSequencer().start(sequencerMemory, reteEvaluator);
                leftTuple.getMemory().removeAdd(leftTuple);

                leftTuple.clearStaged();
                leftTuple = next;
            }
        }

        private void doLeftDeletes(SequenceNode node,
                                   TupleSets srcLeftTuples,
                                   TupleSets trgLeftTuples) {
            for (TupleImpl leftTuple = srcLeftTuples.getDeleteFirst(); leftTuple != null; ) {
                TupleImpl next = leftTuple.getStagedNext();

                SequencerMemory sequencerMemory = (SequencerMemory) leftTuple.getContextObject();
                SequenceNodeMemory nodeMemory = reteEvaluator.getNodeMemory(node);

                SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(sequencerMemory.getSequencer().getSequence());
                node.getSequencer().stop(sequenceMemory, reteEvaluator);
                leftTuple.getMemory().remove(leftTuple);
                leftTuple.setContextObject(null);

                deleteChildren(nodeMemory.getStagedChildTuples(), trgLeftTuples, leftTuple);

                leftTuple.clearStaged();
                leftTuple = next;
            }
        }

        private static void deleteChildren(TupleSets stagedChildTuples, TupleSets trgLeftTuples, TupleImpl leftTuple) {
            if (leftTuple.getFirstChild() != null) {
                TupleImpl childLeftTuple = leftTuple.getFirstChild();
                while (childLeftTuple != null) {
                    TupleImpl nextChild = childLeftTuple.getHandleNext();
                    childLeftTuple.unlinkFromLeftParent();
                    if (childLeftTuple.getStagedType() == Tuple.INSERT) {
                        stagedChildTuples.removeInsert(childLeftTuple);
                    } else {
                        trgLeftTuples.addDelete(childLeftTuple);
                    }
                    childLeftTuple = nextChild;
                }
            }
        }

        private void doLeftInserts(SequenceNode node,
                                   SequenceNodeMemory memory,
                                   LeftTupleSink sink,
                                   TupleSets srcLeftTuples) {
            for (TupleImpl leftTuple = srcLeftTuples.getInsertFirst(); leftTuple != null; ) {
                TupleImpl next = leftTuple.getStagedNext();

                memory.getLeftTupleMemory().add(leftTuple);

                SequencerMemory sequencerMemory = memory.node.createSequencerMemory(leftTuple, sink, memory);
                leftTuple.setContextObject(sequencerMemory);
                node.getSequencer().start(sequencerMemory, reteEvaluator);

                leftTuple.clearStaged();
                leftTuple = next;
            }
        }

    }
}
