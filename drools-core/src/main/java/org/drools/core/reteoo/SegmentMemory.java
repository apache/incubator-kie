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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.NodeMemories;
import org.drools.core.common.SegmentMemorySupport;
import org.drools.core.common.TupleSets;
import org.drools.core.common.TupleSetsImpl;
import org.drools.core.phreak.BuildtimeSegmentUtilities;
import org.drools.core.reteoo.AsyncReceiveNode.AsyncReceiveMemory;
import org.drools.core.reteoo.QueryElementNode.QueryElementNodeMemory;
import org.drools.core.reteoo.TupleToObjectNode.SubnetworkPathMemory;
import org.drools.core.reteoo.TimerNode.TimerNodeMemory;
import org.drools.base.util.LinkedList;
import org.drools.base.util.DoubleLinkedEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drools.core.phreak.BuildtimeSegmentUtilities.nextNodePosMask;

public class SegmentMemory extends LinkedList<SegmentMemory>
                           implements
                           DoubleLinkedEntry<SegmentMemory> {

    protected static final Logger log = LoggerFactory.getLogger(SegmentMemory.class);
    protected static final boolean IS_LOG_TRACE_ENABLED = log.isTraceEnabled();

    private SegmentPrototype proto;
    private Memory[] nodeMemories;
    private final List<PathMemory> pathMemories = new ArrayList<>(1);;
    private final TupleSets stagedLeftTuples = new TupleSetsImpl();
    private long linkedNodeMask;
    private long dirtyNodeMask;
    private long allLinkedMaskTest;
    private long segmentPosMaskBit;
    private int pos = -1;
    private boolean active;
    private SegmentMemory previous;
    private SegmentMemory next;

    private transient List<PathMemory> dataDrivenPathMemories;

    private transient List<SegmentMemory> peersWithDataDrivenPathMemories;

    public SegmentMemory() {

    }

    public SegmentMemory(LeftTupleNode rootNode) {
        this.proto = new SegmentPrototype(rootNode, null);
    }

    public LeftTupleNode getRootNode() {
        return proto.getRootNode();
    }

    public boolean isOnlyLiaSegment() {
        return getRootNode() == getTipNode();
    }
    
    public SegmentPrototype getSegmentPrototype() {
        return proto;
    }

    public LeftTupleNode getTipNode() {
        return proto.getTipNode();
    }

    public void setTipNode(LeftTupleNode tipNode) {
        this.proto.setTipNode(tipNode);
    }

    public int nodeSegmentPosition(LeftTupleNode splitNode) {
        LeftTupleNode lt = splitNode;
        int nodePos = 0;
        while (lt != proto.getRootNode()) {
            lt = lt.getLeftTupleSource();
            nodePos++;
        }
        return nodePos;
    }

    public LeftTupleSink getSinkFactory() {
        return (LeftTupleSink) proto.getRootNode();
    }

    public Memory[] getNodeMemories() {
        return nodeMemories;
    }

    public void setNodeMemories(Memory[] nodeMemories) {
        this.nodeMemories = nodeMemories;
    }

    public long getLinkedNodeMask() {
        return linkedNodeMask;
    }

    public void setLinkedNodeMask(long linkedNodeMask) {
        this.linkedNodeMask = linkedNodeMask;
    }

    public long getDirtyNodeMask() {
        return dirtyNodeMask;
    }

    public void setDirtyNodeMask(long dirtyNodeMask) {
        this.dirtyNodeMask = dirtyNodeMask;
    }

    public void updateDirtyNodeMask(long mask) {
        dirtyNodeMask |= mask;
    }

    public void updateCleanNodeMask(long mask) {
        dirtyNodeMask &= (~mask);
    }

    public String getRuleNames() {
        StringBuilder sbuilder = new StringBuilder();
        for (int i = 0; i < pathMemories.size(); i++) {
            if (i > 0) {
                sbuilder.append(", ");
            }
            sbuilder.append(pathMemories.get(i));
        }

        return sbuilder.toString();
    }

    public boolean linkNode(long mask) {
        linkedNodeMask |= mask;
        dirtyNodeMask |= mask;
        if (IS_LOG_TRACE_ENABLED) {
            log.trace("LinkNode notify=true nmask={} smask={} spos={} rules={}", mask, linkedNodeMask, pos,
                    getRuleNames());
        }

        return notifyRuleLinkSegment();
    }

    public boolean linkNodeWithoutRuleNotify(long mask) {
        linkedNodeMask |= mask;
        dirtyNodeMask |= mask;
        if (IS_LOG_TRACE_ENABLED) {
            log.trace("LinkNode notify=false nmask={} smask={} spos={} rules={}", mask, linkedNodeMask, pos,
                    getRuleNames());
        }

        return linkSegmentWithoutRuleNotify();
    }

    public boolean linkSegmentWithoutRuleNotify(long mask) {
        dirtyNodeMask |= mask;
        return linkSegmentWithoutRuleNotify();
    }

    private boolean linkSegmentWithoutRuleNotify() {
        boolean dataDrivePmemLinked = false;
        if (isSegmentLinked()) {
            for (int i = 0, length = pathMemories.size(); i < length; i++) {
                // do not use foreach, don't want Iterator object creation
                PathMemory pmem = pathMemories.get(i);
                pmem.linkSegmentWithoutRuleNotify(segmentPosMaskBit);
                dataDrivePmemLinked |= (pmem.isDataDriven() && pmem.isRuleLinked());
            }
        }
        return dataDrivePmemLinked;
    }

    public boolean notifyRuleLinkSegment(long mask) {
        dirtyNodeMask |= mask;
        return notifyRuleLinkSegment();
    }

    public boolean notifyRuleLinkSegment() {
        boolean dataDrivePmemLinked = false;
        if (isSegmentLinked()) {
            for (int i = 0, length = pathMemories.size(); i < length; i++) {
                // do not use foreach, don't want Iterator object creation
                PathMemory pmem = pathMemories.get(i);
                notifyRuleLinkSegment(pmem);
                dataDrivePmemLinked |= (pmem.isDataDriven() && pmem.isRuleLinked());
            }
        }
        return dataDrivePmemLinked;
    }

    public void notifyRuleLinkSegment(PathMemory pmem) {
        pmem.linkSegment(segmentPosMaskBit);
    }

    public boolean unlinkNode(long mask) {
        boolean dataDrivePmemLinked = false;
        boolean linked = isSegmentLinked();
        // some node unlinking does not unlink the segment, such as nodes after a Branch CE
        linkedNodeMask &= ~mask;
        dirtyNodeMask |= mask;

        if (IS_LOG_TRACE_ENABLED) {
            log.trace("UnlinkNode notify=true nmask={} smask={} spos={} rules={}", mask, linkedNodeMask, pos,
                    getRuleNames());
        }

        if (linked && !isSegmentLinked()) {
            for (int i = 0, length = pathMemories.size(); i < length; i++) {
                // do not use foreach, don't want Iterator object creation
                PathMemory pmem = pathMemories.get(i);
                // the data driven pmem has to be flushed only if the pmem was formerly linked
                dataDrivePmemLinked |= (pmem.isDataDriven() && pmem.isRuleLinked());
                pmem.unlinkedSegment(segmentPosMaskBit);
            }
        } else {
            // if not unlinked, then we still need to notify if the rule is linked
            for (int i = 0, length = pathMemories.size(); i < length; i++) {
                // do not use foreach, don't want Iterator object creation
                if (pathMemories.get(i).isRuleLinked()) {
                    pathMemories.get(i).doLinkRule();
                }
            }
        }
        return dataDrivePmemLinked;
    }

    public void unlinkSegment() {
        for (int i = 0, length = pathMemories.size(); i < length; i++) {
            // do not use foreach, don't want Iterator object creation
            pathMemories.get(i).unlinkedSegment(segmentPosMaskBit);
        }
    }

    public void unlinkNodeWithoutRuleNotify(long mask) {
        linkedNodeMask &= ~mask;
        if (IS_LOG_TRACE_ENABLED) {
            log.trace("UnlinkNode notify=false nmask={} smask={} spos={} rules={}", mask, linkedNodeMask, pos,
                    getRuleNames());
        }
    }

    public long getAllLinkedMaskTest() {
        return allLinkedMaskTest;
    }

    public void setAllLinkedMaskTest(long allLinkedTestMask) {
        this.allLinkedMaskTest = allLinkedTestMask;
    }

    public boolean isSegmentLinked() {
        return (linkedNodeMask & allLinkedMaskTest) == allLinkedMaskTest;
    }

    public List<PathMemory> getPathMemories() {
        return pathMemories;
    }

    public void addPathMemory(PathMemory pathMemory) {
        if (pathMemories.contains(pathMemory)) {
            System.out.println("!!!");
        }

        pathMemories.add(pathMemory);
        if (getAllLinkedMaskTest() > 0 && isSegmentLinked()) {
            pathMemory.linkSegmentWithoutRuleNotify(segmentPosMaskBit);
        }
        if (pathMemory.isDataDriven()) {
            if (dataDrivenPathMemories == null) {
                dataDrivenPathMemories = new ArrayList<>();
            }
            dataDrivenPathMemories.add(pathMemory);
        }
    }

    public void splitBitMasks(SegmentMemory sm, int pos) {
        int splitPos = pos + 1; // +1 as zero based
        long currentAllLinkedMaskTest = allLinkedMaskTest;
        long currentLinkedNodeMask = linkedNodeMask;
        long mask = (1L << splitPos) - 1;

        this.allLinkedMaskTest = mask & currentAllLinkedMaskTest;
        this.linkedNodeMask = linkedNodeMask & allLinkedMaskTest;

        mask = currentAllLinkedMaskTest >> splitPos;
        sm.allLinkedMaskTest = mask;
        sm.linkedNodeMask = mask & (currentLinkedNodeMask >> splitPos);
    }

    public void splitBitMasks(SegmentMemory sm, long currentLinkedNodeMask) {
        // @TODO Haven't made this work for more than 64 nodes, as per SegmentUtilities.nextNodePosMask (mdp)
        int splitPos = proto.getNodesInSegment().length; // +1 as zero based
        long currentDirtyNodeMask = dirtyNodeMask;
        long splitMask = ((1L << (splitPos)) - 1);

        this.dirtyNodeMask = currentDirtyNodeMask & splitMask;
        this.linkedNodeMask = currentLinkedNodeMask & splitMask;

        sm.linkedNodeMask = currentLinkedNodeMask >> splitPos;
        sm.dirtyNodeMask = currentDirtyNodeMask >> splitPos;
    }

    public void mergeBitMasks(SegmentMemory sm) {
        long mask = sm.allLinkedMaskTest << sm.nodeMemories.length;
        this.allLinkedMaskTest = mask & allLinkedMaskTest;

        mask = sm.allLinkedMaskTest << sm.nodeMemories.length;
        this.linkedNodeMask = mask & linkedNodeMask;
    }

    public void mergeBitMasks(SegmentMemory sm, LeftTupleNode[] origNodes, long currentLinkedNodeMask) {
        // @TODO Haven't made this work for more than 64 nodes, as per SegmentUtilities.nextNodePosMask (mdp)
        int shiftBits = origNodes.length;

        long linkedBitsToAdd = sm.linkedNodeMask << shiftBits;
        long dirtyBitsToAdd = sm.dirtyNodeMask << shiftBits;
        this.linkedNodeMask = linkedBitsToAdd | currentLinkedNodeMask;
        this.dirtyNodeMask = dirtyBitsToAdd | dirtyNodeMask;
    }

    public void mergePathMemories(SegmentMemory segmentMemory) {
        for (PathMemory pmem : segmentMemory.getPathMemories()) {
            if (isAssociatedWith(pmem)) {
                addPathMemory(pmem);
            }
        }
    }

    private boolean isAssociatedWith(PathMemory pmem) {
        if (NodeTypeEnums.TupleToObjectNode == pmem.getNodeType()) {
            for (PathEndNode endNode : pmem.getPathEndNode().getPathEndNodes()) {
                if (NodeTypeEnums.isTerminalNode(endNode)) {
                    if (proto.getRootNode().hasAssociatedTerminal((AbstractTerminalNode) endNode)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return proto.getRootNode().hasAssociatedTerminal((AbstractTerminalNode) pmem.getPathEndNode());
    }

    public void removePathMemory(PathMemory pathMemory) {
        pathMemories.remove(pathMemory);
        if (pathMemory.isDataDriven()) {
            dataDrivenPathMemories.remove(pathMemory);
            if (dataDrivenPathMemories.isEmpty()) {
                dataDrivenPathMemories = null;
            }
        }
    }

    public PathMemory getFirstDataDrivenPathMemory() {
        return dataDrivenPathMemories == null ? null : dataDrivenPathMemories.get(0);
    }

    public boolean hasDataDrivenPathMemories() {
        return dataDrivenPathMemories != null;
    }

    public List<PathMemory> getDataDrivenPathMemories() {
        return dataDrivenPathMemories;
    }

    public long getSegmentPosMaskBit() {
        return segmentPosMaskBit;
    }

    public void setSegmentPosMaskBit(long segmentPosMaskBit) {
        this.segmentPosMaskBit = segmentPosMaskBit;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean evaluating) {
        this.active = evaluating;
    }

    public int getPos() {
        return pos;
    }

    public void setPos(int pos) {
        this.pos = pos;
    }

    public TupleSets getStagedLeftTuples() {
        return stagedLeftTuples;
    }

    @Override
    public void add(SegmentMemory segmentMemory) {
        super.add(segmentMemory);
        if (segmentMemory.hasDataDrivenPathMemories()) {
            if (peersWithDataDrivenPathMemories == null) {
                peersWithDataDrivenPathMemories = new ArrayList<>();
            }
            peersWithDataDrivenPathMemories.add(segmentMemory);
        }
    }

    @Override
    public void remove(SegmentMemory segmentMemory) {
        super.remove(segmentMemory);
        if (peersWithDataDrivenPathMemories != null) {
            peersWithDataDrivenPathMemories.remove(segmentMemory);
            if (peersWithDataDrivenPathMemories.isEmpty()) {
                peersWithDataDrivenPathMemories = null;
            }
        }
    }

    public Iterator<SegmentMemory> getPeersWithDataDrivenPathMemoriesIterator() {
        return peersWithDataDrivenPathMemories == null ? Collections.emptyIterator() : peersWithDataDrivenPathMemories
                .iterator();
    }

    public SegmentMemory getNext() {
        return this.next;
    }

    public void setNext(SegmentMemory next) {
        this.next = next;
        if (this.next == this) {
            throw new RuntimeException();
        }
    }

    public SegmentMemory getPrevious() {
        return this.previous;
    }

    public void setPrevious(SegmentMemory previous) {
        this.previous = previous;
    }

    public void clear() {
        previous = null;
        next = null;
    }

    @Override
    public int hashCode() {
        return proto.getRootNode().getId();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof SegmentMemory &&
                               proto.getRootNode().getId() == ((SegmentMemory) obj).proto.getRootNode().getId() &&
                               proto.getTipNode().getId() == ((SegmentMemory) obj).proto.getTipNode().getId());
    }

    public void reset(SegmentPrototype segmentPrototype) {
        this.dirtyNodeMask = 0L;
        this.linkedNodeMask = segmentPrototype != null ? segmentPrototype.linkedNodeMask : 0L;
        stagedLeftTuples.resetAll();
    }

    public void splitNodeMemories(SegmentMemory sm, int pos) {
        List<Memory> smNodeMemories1 = new ArrayList<>(Arrays.asList(getNodeMemories()));
        List<Memory> smNodeMemories2 = new ArrayList<>();

        Memory mem = smNodeMemories1.get(0);
        long nodePosMask = 1;
        for (int i = 0, length = smNodeMemories1.size(); i < length; i++) {
            Memory next = mem.getNext();
            if (i > pos) {
                smNodeMemories1.remove(mem);
                addToMemoryList(smNodeMemories2, mem);
                mem.setSegmentMemory(sm);

                // correct the NodePosMaskBit
                if (mem instanceof SegmentNodeMemory) {
                    ((SegmentNodeMemory) mem).setNodePosMaskBit(nodePosMask);
                }
                nodePosMask = nextNodePosMask(nodePosMask);
            }
            mem = next;
        }
        this.nodeMemories = smNodeMemories1.toArray(new Memory[smNodeMemories1.size()]);
        sm.nodeMemories = smNodeMemories2.toArray(new Memory[smNodeMemories2.size()]);
    }

    public void mergeNodeMemories(SegmentMemory sm) {
        List<Memory> mergedMemories = new ArrayList<>();

        int nodePosMask = 1;
        for (Memory mem : nodeMemories) {
            nodePosMask = nodePosMask >> 1;
            mergedMemories.add(mem);
        }

        for (Memory mem : sm.nodeMemories) {
            addToMemoryList(mergedMemories, mem);
            mem.setSegmentMemory(this);

            // correct the NodePosMaskBit
            if (mem instanceof SegmentNodeMemory) {
                ((SegmentNodeMemory) mem).setNodePosMaskBit(nodePosMask);
            }
            nodePosMask = nodePosMask >> 1;
        }

        this.nodeMemories = mergedMemories.toArray(new Memory[mergedMemories.size()]);
    }

    public void mergeSegment(SegmentMemory other) {
        if (NodeTypeEnums.isLeftInputAdapterNode(getTipNode()) && !other.stagedLeftTuples.isEmpty()) {
            // If a rule has not been linked, lia can still have child segments with staged tuples that did not get flushed
            // these are safe to just move to the parent SegmentMemory
            stagedLeftTuples.addAll(other.stagedLeftTuples);
        }

        // sm1 may not be linked yet to sm2 because sm2 has been just created
        if (contains(other)) {
            remove(other);
        }

        if (other.getFirst() != null) {
            for (SegmentMemory sm = other.getFirst(); sm != null;) {
                SegmentMemory next = sm.getNext();
                other.remove(sm);
                add(sm);
                sm = next;
            }
        }
        // re-assigned tip nodes
        setTipNode(other.getTipNode());

        mergeNodeMemories(other);

        mergeBitMasks(other);
    }

    public void mergeSegment(SegmentMemory other,
                             SegmentPrototype proto1,
                             LeftTupleNode[] origNodes) {
        if (NodeTypeEnums.isLeftInputAdapterNode(getTipNode()) && !other.getStagedLeftTuples().isEmpty()) {
            // If a rule has not been linked, lia can still have child segments with staged tuples that did not get flushed
            // these are safe to just move to the parent SegmentMemory
            getStagedLeftTuples().addAll(other.getStagedLeftTuples());
        }

        // sm1 may not be linked yet to sm2 because sm2 has been just created
        if (contains(other)) {
            remove(other);
        }

        // add all child sms
        if (other.getFirst() != null) {
            for (SegmentMemory sm = other.getFirst(); sm != null;) {
                SegmentMemory next = sm.getNext();
                other.remove(sm);
                add(sm);
                sm = next;
            }
        }

        // preserve values that get changed updateSegmentMemory, for merge
        long currentLinkedNodeMask = getLinkedNodeMask();
        updateFromPrototype(proto1);
        mergeBitMasks(other, origNodes, currentLinkedNodeMask);
    }

    public SegmentMemory splitSegmentOn(SegmentMemory other, LeftTupleNode splitNode) {
        // Move the children of this segment to other segment
        if (getFirst() != null) {
            for (SegmentMemory sm = getFirst(); sm != null;) {
                SegmentMemory next = getNext();
                remove(sm);
                other.add(sm);
                sm = next;
            }
        }

        add(other);

        other.setPos(getPos()); // clone for now, it's corrected later
        other.setSegmentPosMaskBit(getSegmentPosMaskBit()); // clone for now, it's corrected later
        other.setLinkedNodeMask(getLinkedNodeMask()); // clone for now, it's corrected later

        other.mergePathMemories(this);

        // re-assigned tip nodes
        other.setTipNode(getTipNode());
        setTipNode(splitNode); // splitNode is now tip of original segment

        if (NodeTypeEnums.isLeftInputAdapterNode(getTipNode())) {
            if (!getStagedLeftTuples().isEmpty()) {
                // Segments with only LiaNode's cannot have staged LeftTuples, so move them down to the new Segment
                other.getStagedLeftTuples().addAll(getStagedLeftTuples());
            }
        }

        // find the pos of the node in the segment
        int pos = nodeSegmentPosition(splitNode);

        splitNodeMemories(other, pos);
        splitBitMasks(other, pos);
        other.correctSegmentMemoryAfterSplitOnAdd(1);

        return other;
    }

    private void addToMemoryList(List<Memory> smNodeMemories, Memory mem) {
        if (!smNodeMemories.isEmpty()) {
            Memory last = smNodeMemories.get(smNodeMemories.size() - 1);
            last.setNext(mem);
            mem.setPrevious(last);
        }
        smNodeMemories.add(mem);
    }

    public void correctSegmentMemoryAfterSplitOnAdd() {
        correctSegmentMemoryAfterSplitOnAdd(1);
    }

    public void correctSegmentMemoryAfterSplitOnAdd(int i) {
        this.pos = pos + i;
        this.segmentPosMaskBit = segmentPosMaskBit << i;
    }

    public void correctSegmentMemoryAfterSplitOnRemove(int i) {
        this.pos = pos - i;
        this.segmentPosMaskBit = segmentPosMaskBit >> i;
    }

    public void updateFromPrototype(SegmentPrototype proto) {
        this.proto = proto;
        this.allLinkedMaskTest = proto.getAllLinkedMaskTest();
        this.segmentPosMaskBit = proto.getSegmentPosMaskBit();
        this.linkedNodeMask = proto.getLinkedNodeMask();
        this.pos = proto.getPos();
    }

    @Override
    public String toString() {
        return "Segment root " + proto.getRootNode() + " tip " + proto.getTipNode();
    }

    public static class SegmentPrototype {

        private LeftTupleNode rootNode;
        private LeftTupleNode tipNode;
        long linkedNodeMask;
        long allLinkedMaskTest;
        long segmentPosMaskBit;
        int pos;

        boolean requiresEager;

        int nodeTypesInSegment = 0;

        MemoryPrototype[] memories;

        LeftTupleNode[] nodesInSegment;

        PathEndNode[] pathEndNodes;

        public SegmentPrototype(LeftTupleNode rootNode, LeftTupleNode tipNode) {
            this.rootNode = rootNode;
            this.tipNode = tipNode;
        }

        public SegmentMemory newSegmentMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport) {
            SegmentMemory smem = new SegmentMemory();
            updateSegmentMemory(nodeMemories, segmentMemorySupport, smem);
            return smem;
        }

        public SegmentMemory shallowNewSegmentMemory() {
            SegmentMemory smem = new SegmentMemory();
            smem.updateFromPrototype(this);
            return smem;
        }

        public void updateSegmentMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport, SegmentMemory smem) {
            smem.updateFromPrototype(this);
            Memory[] updatedMemories = new Memory[getNodesInSegment().length];
            for (int i = 0; i < memories.length; i++) {
                Memory mem = nodeMemories.getNodeMemory((MemoryFactory) getNodesInSegment()[i]);
                if (i > 0) {
                    mem.setPrevious(updatedMemories[i - 1]);
                    updatedMemories[i - 1].setNext(mem);
                }
                updatedMemories[i] = mem;
                mem.setSegmentMemory(smem);
                MemoryPrototype proto = memories[i];
                if (proto != null) {
                    proto.populateMemory(nodeMemories, segmentMemorySupport, mem);
                }
            }
            smem.setNodeMemories(updatedMemories);
        }

        public SegmentPrototype initFromSegmentMemory(SegmentMemory smem) {
            this.linkedNodeMask = smem.linkedNodeMask;
            this.allLinkedMaskTest = smem.allLinkedMaskTest;
            this.segmentPosMaskBit = smem.segmentPosMaskBit;
            this.pos = smem.pos;
            int i = 0;
            memories = new MemoryPrototype[smem.nodeMemories.length];
            for (Memory mem : smem.nodeMemories) {
                memories[i++] = MemoryPrototype.get(mem);
            }
            return this;
        }

        public LeftTupleNode getRootNode() {
            return rootNode;
        }

        public LeftTupleNode getTipNode() {
            return tipNode;
        }

        public void setTipNode(LeftTupleNode tipNode) {
            this.tipNode = tipNode;
        }

        public void linkNode(long mask) {
            linkedNodeMask |= mask;
        }

        public long getLinkedNodeMask() {
            return linkedNodeMask;
        }

        public void setLinkedNodeMask(long linkedNodeMask) {
            this.linkedNodeMask = linkedNodeMask;
        }

        public void setAllLinkedMaskTest(long allLinkedMaskTest) {
            this.allLinkedMaskTest = allLinkedMaskTest;
        }

        public long getAllLinkedMaskTest() {
            return allLinkedMaskTest;
        }

        public void setSegmentPosMaskBit(long segmentPosMaskBit) {
            this.segmentPosMaskBit = segmentPosMaskBit;
        }

        public long getSegmentPosMaskBit() {
            return segmentPosMaskBit;
        }

        public int getPos() {
            return pos;
        }

        public void setPos(int pos) {
            this.pos = pos;
        }

        public MemoryPrototype[] getMemories() {
            return memories;
        }

        public void setMemories(MemoryPrototype[] memories) {
            this.memories = memories;
        }

        public LeftTupleNode[] getNodesInSegment() {
            return nodesInSegment;
        }

        public void setNodesInSegment(LeftTupleNode[] nodesInSegment) {
            this.nodesInSegment = nodesInSegment;
        }

        public int getNodeTypesInSegment() {
            return nodeTypesInSegment;
        }

        public void setNodeTypesInSegment(int nodeTypesInSegment) {
            this.nodeTypesInSegment = nodeTypesInSegment;
            requiresEager = BuildtimeSegmentUtilities.requiresAnEagerSegment(nodeTypesInSegment);
        }

        public boolean requiresEager() {
            return requiresEager;
        }

        public PathEndNode[] getPathEndNodes() {
            return pathEndNodes;
        }

        public void setPathEndNodes(PathEndNode[] pathEndNodes) {
            this.pathEndNodes = pathEndNodes;
        }

        public void splitProtos(SegmentPrototype proto2, LeftTupleNode splitNode) {
            setTipNode(splitNode);

            LeftTupleNode[] nodes = getNodesInSegment();

            MemoryPrototype[] mems = getMemories();

            int arraySplit = splitNode.getPathIndex() - getRootNode().getPathIndex() + 1;
            LeftTupleNode[] proto1Nodes = new LeftTupleNode[arraySplit];
            LeftTupleNode[] proto2Nodes = new LeftTupleNode[nodes.length - arraySplit];
            System.arraycopy(nodes, 0, proto1Nodes, 0, proto1Nodes.length);
            System.arraycopy(nodes, arraySplit, proto2Nodes, 0, proto2Nodes.length);
            setNodesInSegment(proto1Nodes);
            proto2.setNodesInSegment(proto2Nodes);

            setNodeTypes(proto1Nodes);
            proto2.setNodeTypes(proto2Nodes);

            // Split the memory protos across proto1 and proto2
            MemoryPrototype[] proto1Mems = new MemoryPrototype[proto1Nodes.length];
            MemoryPrototype[] proto2Mems = new MemoryPrototype[proto2Nodes.length];
            System.arraycopy(mems, 0, proto1Mems, 0, proto1Mems.length);
            setMemories(proto1Mems);

            // proto2Mems needs updating, no point in arraycopy, so just use standard for loop to copy
            int bitPos = 1;
            for (int i = 0; i < proto2Mems.length; i++) {
                proto2Mems[i] = mems[i + arraySplit];
                proto2Mems[i].setNodePosMaskBit(bitPos);
                bitPos = bitPos << 1;
            }

            proto2.setMemories(proto2Mems);
            splitBitMasks(proto2);
        }

        public void splitEagerProtos(boolean proto1WasEager, SegmentPrototype other, PathEndNode endNode) {
            if (proto1WasEager) { // if it wasn't eager before, nothing can be eager after
                SegmentPrototype[] eager = endNode.getEagerSegmentPrototypes();
                if (requiresEager() && other.requiresEager()) {
                    // keep proto1 and add proto2
                    SegmentPrototype[] newEager = new SegmentPrototype[eager.length + 1];
                    System.arraycopy(eager, 0, newEager, 0, eager.length);
                    newEager[newEager.length - 1] = other; // I don't think order matters, so just add to the end
                    endNode.setEagerSegmentPrototypes(newEager);
                } else if (other.requiresEager()) {
                    // proto2 is no longer eager, find proto1 and swap proto1 with proto2
                    for (int i = 0; i < eager.length; i++) {
                        if (eager[i] == this) {
                            eager[i] = other;
                            break;
                        }
                    }
                } // else if ( requiresEager() && !proto2.requiresEager()) do nothing as proto1 already in the array
            }
        }

        public void mergeProtos(SegmentPrototype other, LeftTupleNode[] origNodes) {
            setTipNode(other.getTipNode());
            LeftTupleNode[] nodes = new LeftTupleNode[getNodesInSegment().length + other.getNodesInSegment().length];

            System.arraycopy(getNodesInSegment(), 0, nodes,
                    0, getNodesInSegment().length);

            System.arraycopy(other.getNodesInSegment(), 0, nodes,
                    getNodesInSegment().length, other.getNodesInSegment().length);

            setNodesInSegment(nodes);

            MemoryPrototype[] protoMems = new MemoryPrototype[getMemories().length + other.getMemories().length];

            System.arraycopy(getMemories(), 0, protoMems,
                    0, getMemories().length);

            System.arraycopy(other.getMemories(), 0, protoMems,
                    getMemories().length, other.getMemories().length);

            setNodesInSegment(nodes);
            setMemories(protoMems);

            int bitPos = 1;
            for (MemoryPrototype protoMem : protoMems) {
                protoMem.setNodePosMaskBit(bitPos);
                bitPos = bitPos << 1;
            }
            setNodeTypes(nodes);

            mergeBitMasks(other, origNodes);
        }

        public void mergeEagerProtos(SegmentPrototype proto2, boolean proto2WasEager, PathEndNode endNode) {
            if (!requiresEager() && !proto2.requiresEager()) {
                return;
            }

            SegmentPrototype[] eager = endNode.getEagerSegmentPrototypes();
            if (requiresEager() && proto2.requiresEager()) {
                // keep proto1 and remove proto2
                SegmentPrototype[] newEager = new SegmentPrototype[eager.length - 1];
                copyWithRemoval(eager, newEager, proto2);
                endNode.setEagerSegmentPrototypes(newEager);
            } else if (requiresEager() && proto2WasEager) {
                // find proto2 and swap proto2 with proto1
                for (int i = 0; i < eager.length; i++) {
                    if (eager[i] == proto2) {
                        eager[i] = this;
                        break;
                    }
                }
            } // else if ( requiresEager() && !proto2.requiresEager()) do nothing as proto1 already in the array
        }

        private static void copyWithRemoval(SegmentPrototype[] orinalProtos,
                                            SegmentPrototype[] newProtos,
                                            SegmentPrototype protoToRemove) {
            for (int i = 0, j = 0; i < orinalProtos.length; i++) {
                if (orinalProtos[i] == protoToRemove) {
                    // j is not increased here.
                    continue;
                }
                newProtos[j] = orinalProtos[i];
                j++;
            }
        }

        public void setNodeTypes(LeftTupleNode[] protoNodes) {
            int nodeTypesInSegment = 0;
            for (LeftTupleNode node : protoNodes) {
                nodeTypesInSegment = BuildtimeSegmentUtilities.updateNodeTypesMask(node, nodeTypesInSegment);
            }
            setNodeTypesInSegment(nodeTypesInSegment);
        }

        public void mergeBitMasks(SegmentPrototype sm2, LeftTupleNode[] origNodes) {
            // @TODO Haven't made this work for more than 64 nodes, as per SegmentUtilities.nextNodePosMask (mdp)
            int shiftBits = origNodes.length;

            long currentLinkedNodeMask = linkedNodeMask;
            long currentAllLinkedMaskTest = allLinkedMaskTest;

            long linkedBitsToAdd = sm2.linkedNodeMask << shiftBits;
            long allBitsToAdd = sm2.allLinkedMaskTest << shiftBits;

            this.linkedNodeMask = linkedBitsToAdd | currentLinkedNodeMask;
            this.allLinkedMaskTest = allBitsToAdd | currentAllLinkedMaskTest;
        }

        public void splitBitMasks(SegmentPrototype sm) {
            // @TODO Haven't made this work for more than 64 nodes, as per SegmentUtilities.nextNodePosMask (mdp)
            int splitPos = nodesInSegment.length; // +1 as zero based
            long splitMask = ((1L << (splitPos)) - 1);

            long currentLinkedNodeMask = linkedNodeMask;
            long currentAllLinkedMaskTest = allLinkedMaskTest;

            this.linkedNodeMask = currentLinkedNodeMask & splitMask;
            this.allLinkedMaskTest = currentAllLinkedMaskTest & splitMask;

            sm.linkedNodeMask = currentLinkedNodeMask >> splitPos;
            sm.allLinkedMaskTest = currentAllLinkedMaskTest >> splitPos;

            sm.segmentPosMaskBit = getSegmentPosMaskBit() << 1;
        }

        public String toString() {
            StringBuilder sbuilder = new StringBuilder();
            sbuilder.append("SegmentMemory[");
            sbuilder.append("root " + rootNode + " tip " + tipNode + ", ");
            sbuilder.append("linkedNodeMask " + linkedNodeMask + ", ");
            sbuilder.append("allLinkedMaskTest " + allLinkedMaskTest + ", ");
            sbuilder.append("segmentPosMaskBit " + segmentPosMaskBit + ", ");
            sbuilder.append("pos " + pos + ", ");
            sbuilder.append("nodeTypesInSegment " + nodeTypesInSegment + " ");
            sbuilder.append("nodes ");
            if (nodesInSegment != null) {
                Arrays.stream(nodesInSegment).forEach(sbuilder::append);
            }
            sbuilder.append("]");
            return sbuilder.toString();
        }
    }

    public abstract static class MemoryPrototype {

        protected long nodePosMaskBit;

        public static MemoryPrototype get(Memory memory) {
            if (memory instanceof BetaMemory) {
                BetaMemory betaMemory = (BetaMemory) memory;
                return new BetaMemoryPrototype(betaMemory.getNodePosMaskBit(), betaMemory
                        .getSubnetworkPathMemory() != null ? betaMemory.getSubnetworkPathMemory().getTupleToObjectNode()
                                : null);
            }
            if (memory instanceof LeftInputAdapterNode.LiaNodeMemory) {
                return new LiaMemoryPrototype(((LeftInputAdapterNode.LiaNodeMemory) memory).getNodePosMaskBit());
            }
            if (memory instanceof QueryElementNode.QueryElementNodeMemory) {
                QueryElementNode.QueryElementNodeMemory queryMemory = (QueryElementNode.QueryElementNodeMemory) memory;
                return new QueryMemoryPrototype(queryMemory.getNodePosMaskBit(), queryMemory.getNode());
            }
            if (memory instanceof TimerNodeMemory) {
                return new TimerMemoryPrototype(((TimerNodeMemory) memory).getNodePosMaskBit());
            }
            if (memory instanceof AccumulateNode.AccumulateMemory) {
                BetaMemory betaMemory = ((AccumulateNode.AccumulateMemory) memory).getBetaMemory();
                return new AccumulateMemoryPrototype(new BetaMemoryPrototype(betaMemory.getNodePosMaskBit(), betaMemory
                        .getSubnetworkPathMemory() != null ? betaMemory.getSubnetworkPathMemory().getTupleToObjectNode()
                                : null));
            }
            if (memory instanceof ReactiveFromNode.ReactiveFromMemory) {
                return new ReactiveFromMemoryPrototype(((ReactiveFromNode.ReactiveFromMemory) memory)
                        .getNodePosMaskBit());
            }
            return null;
        }

        public abstract void populateMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport, Memory memory);

        public void setNodePosMaskBit(long nodePosMaskBit) {
            this.nodePosMaskBit = nodePosMaskBit;
        }

        public long getNodePosMaskBit() {
            return nodePosMaskBit;
        }
    }

    public static class BetaMemoryPrototype extends MemoryPrototype {

        private final TupleToObjectNode tton;

        public BetaMemoryPrototype(long nodePosMaskBit, TupleToObjectNode tton) {
            this.nodePosMaskBit = nodePosMaskBit;
            this.tton = tton;
        }

        @Override
        public void populateMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport, Memory memory) {
            BetaMemory betaMemory = (BetaMemory) memory;
            betaMemory.setNodePosMaskBit(nodePosMaskBit);
            if (tton != null) {
                SubnetworkPathMemory riaMem = (SubnetworkPathMemory) nodeMemories.peekNodeMemory(
                        tton);
                if (riaMem == null) {
                    riaMem = (SubnetworkPathMemory) segmentMemorySupport.initializePathMemory(tton);
                }
                betaMemory.setSubnetworkPathMemory(riaMem);
            }
        }
    }

    public static class LiaMemoryPrototype extends MemoryPrototype {

        public LiaMemoryPrototype(long nodePosMaskBit) {
            this.nodePosMaskBit = nodePosMaskBit;
        }

        @Override
        public void populateMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport, Memory liaMemory) {
            ((SegmentNodeMemory) liaMemory).setNodePosMaskBit(nodePosMaskBit);
        }
    }

    public static class ReactiveFromMemoryPrototype extends MemoryPrototype {

        public ReactiveFromMemoryPrototype(long nodePosMaskBit) {
            this.nodePosMaskBit = nodePosMaskBit;
        }

        @Override
        public void populateMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport, Memory memory) {
            ((ReactiveFromNode.ReactiveFromMemory) memory).setNodePosMaskBit(nodePosMaskBit);
        }
    }

    public static class ConditionalBranchMemoryPrototype extends MemoryPrototype {

        public ConditionalBranchMemoryPrototype() {}

        @Override
        public void populateMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport, Memory memory) {}
    }

    public static class RightInputAdapterPrototype extends MemoryPrototype {

        public RightInputAdapterPrototype() {}

        @Override
        public void populateMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport, Memory memory) {}
    }

    public static class TerminalPrototype extends MemoryPrototype {

        public TerminalPrototype() {}

        @Override
        public void populateMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport, Memory memory) {}
    }

    public static class FromMemoryPrototype extends MemoryPrototype {

        public FromMemoryPrototype() {}

        @Override
        public void populateMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport, Memory memory) {}
    }

    public static class EvalMemoryPrototype extends MemoryPrototype {

        public EvalMemoryPrototype() {}

        @Override
        public void populateMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport, Memory memory) {}
    }

    public static class QueryMemoryPrototype extends MemoryPrototype {

        private final QueryElementNode queryNode;

        public QueryMemoryPrototype(long nodePosMaskBit, QueryElementNode queryNode) {
            this.nodePosMaskBit = nodePosMaskBit;
            this.queryNode = queryNode;
        }

        @Override
        public void populateMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport, Memory mem) {
            QueryElementNodeMemory qmem = (QueryElementNodeMemory) mem;
            SegmentMemory querySmem = segmentMemorySupport.getQuerySegmentMemory(queryNode);
            qmem.setQuerySegmentMemory(querySmem);
            qmem.setNodePosMaskBit(nodePosMaskBit);
        }
    }

    public static class TimerMemoryPrototype extends MemoryPrototype {

        public TimerMemoryPrototype(long nodePosMaskBit) {
            this.nodePosMaskBit = nodePosMaskBit;
        }

        @Override
        public void populateMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport, Memory mem) {
            TimerNodeMemory tmem = (TimerNodeMemory) mem;
            tmem.setNodePosMaskBit(nodePosMaskBit);
        }
    }

    public static class AsyncSendMemoryPrototype extends MemoryPrototype {

        public AsyncSendMemoryPrototype() {}

        @Override
        public void populateMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport, Memory mem) {}
    }

    public static class AsyncReceiveMemoryPrototype extends MemoryPrototype {

        public AsyncReceiveMemoryPrototype(long nodePosMaskBit) {
            this.nodePosMaskBit = nodePosMaskBit;
        }

        @Override
        public void populateMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport, Memory mem) {
            AsyncReceiveMemory amem = (AsyncReceiveMemory) mem;
            amem.setNodePosMaskBit(nodePosMaskBit);
        }
    }

    public static class AccumulateMemoryPrototype extends MemoryPrototype {

        private final BetaMemoryPrototype betaProto;

        public AccumulateMemoryPrototype(BetaMemoryPrototype betaProto) {
            this.betaProto = betaProto;
        }

        @Override
        public void populateMemory(NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport, Memory accMemory) {
            betaProto.populateMemory(nodeMemories, segmentMemorySupport, ((AccumulateNode.AccumulateMemory) accMemory).getBetaMemory());
        }
    }
}
