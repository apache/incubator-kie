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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.common.BaseNode;
import org.drools.core.common.DefaultEventHandle;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.PropagationContextFactory;
import org.drools.core.common.SuperCacheFixer;
import org.drools.core.common.TupleSets;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.AbstractTerminalNode;
import org.drools.core.reteoo.AccumulateNode.AccumulateContext;
import org.drools.core.reteoo.AccumulateNode.AccumulateMemory;
import org.drools.core.reteoo.AlphaTerminalNode;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.FromNode.FromMemory;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.LeftTupleNode;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.LeftTupleSinkNode;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.ObjectSink;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.PathEndNode;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.QueryElementNode;
import org.drools.core.reteoo.RightInputAdapterNode;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.reteoo.RuleTerminalNodeLeftTuple;
import org.drools.core.reteoo.RuntimeComponentFactory;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.core.reteoo.SegmentPrototypeRegistry;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.TupleFactory;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.TupleMemory;
import org.drools.core.reteoo.TupleToObjectNode;
import org.drools.core.reteoo.TupleToObjectNode.SubnetworkPathMemory;
import org.drools.core.reteoo.WindowNode;
import org.drools.base.util.FastIterator;
import org.kie.api.definition.rule.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drools.core.phreak.BuildtimeSegmentUtilities.isRootNode;
import static org.drools.core.phreak.EagerPhreakBuilder.deleteLeftTuple;
import static org.drools.core.phreak.EagerPhreakBuilder.Add.attachAdapterAndPropagate;

class LazyPhreakBuilder implements PhreakBuilder {

    private static final Logger log = LoggerFactory.getLogger(LazyPhreakBuilder.class);

    /**
     * This method is called after the rule nodes have been added to the network
     * For add tuples are processed after the segments and pmems have been adjusted
     */
    @Override
    public void addRule(InternalRuleBase kBase, Collection<InternalWorkingMemory> wms, TerminalNode tn) {
        if (log.isTraceEnabled()) {
            log.trace("Adding Rule {}", tn.getRule().getName());
        }

        boolean hasProtos = kBase.getSegmentPrototypeRegistry().hasSegmentPrototypes();
        boolean hasWms = !wms.isEmpty();

        if (!hasProtos && !hasWms) {
            return;
        }

        RuleImpl rule = tn.getRule();
        LeftTupleNode firstSplit = getNetworkSplitPoint(tn);
        PathEndNodes pathEndNodes = getPathEndNodes(kBase.getSegmentPrototypeRegistry(), rule, firstSplit, tn, hasProtos, hasWms);

        // Insert the facts for the new paths. This will iterate each new path from EndNode to the splitStart - but will not process the splitStart itself (as tha already exist).
        // It does not matter that the prior segments have not yet been processed for splitting, as this will only apply for branches of paths that did not exist before

        for (InternalWorkingMemory wm : wms) {
            wm.flushPropagations();

            if (NodeTypeEnums.isLeftInputAdapterNode(firstSplit) && firstSplit.getAssociatedTerminalsSize() == 1) {
                // rule added with no sharing
                insertLiaFacts(wm, firstSplit);
            } else {
                PathEndNodeMemories tnms = getPathEndMemories(wm, pathEndNodes);

                if (tnms.subjectPmem == null) {
                    // If the existing PathMemories are not yet initialized there are no Segments or tuples to process
                    continue;
                }

                Map<PathMemory, SegmentMemory[]> prevSmemsLookup = reInitPathMemories(tnms.otherPmems, null);

                // must collect all visited SegmentMemories, for link notification
                Set<SegmentMemory> smemsToNotify = handleExistingPaths(wm, tn, prevSmemsLookup, tnms.otherPmems,
                        ExistingPathStrategy.ADD_STRATEGY);

                addNewPaths(wm, smemsToNotify, tnms.subjectPmems);

                processLeftTuples(wm, rule, firstSplit, true);

                notifySegments(smemsToNotify);
            }
        }

        if (hasWms) {
            insertFacts(wms, pathEndNodes);
        } else {
            for (PathEndNode node : pathEndNodes.otherEndNodes) {
                node.resetPathMemSpec(null);
            }
        }
    }

    /**
     * This method is called before the rule nodes are removed from the network.
     * For remove tuples are processed before the segments and pmems have been adjusted
     */
    @Override
    public void removeRule(InternalRuleBase kBase, Collection<InternalWorkingMemory> wms, TerminalNode tn) {
        if (log.isTraceEnabled()) {
            log.trace("Removing Rule {}", tn.getRule().getName());
        }

        boolean hasProtos = kBase.getSegmentPrototypeRegistry().hasSegmentPrototypes();
        boolean hasWms = !wms.isEmpty();

        if (!hasProtos && !hasWms) {
            return;
        }

        RuleImpl rule = tn.getRule();
        LeftTupleNode firstSplit = getNetworkSplitPoint(tn);
        PathEndNodes pathEndNodes = getPathEndNodes(kBase.getSegmentPrototypeRegistry(), rule, firstSplit, tn, hasProtos, hasWms);

        for (InternalWorkingMemory wm : wms) {
            wm.flushPropagations();

            PathEndNodeMemories tnms = getPathEndMemories(wm, pathEndNodes);

            if (!tnms.subjectPmems.isEmpty()) {
                if (NodeTypeEnums.isLeftInputAdapterNode(firstSplit) && firstSplit.getAssociatedTerminalsSize() == 1) {
                    if (tnms.subjectPmem != null) {
                        flushStagedTuples(wm, firstSplit, tnms.subjectPmem);
                    }

                    processLeftTuples(wm, tn.getRule(), firstSplit, false);

                    removeNewPaths(wm, tnms.subjectPmems);
                } else {
                    flushStagedTuples(wm, tn, tnms.subjectPmem, pathEndNodes.subjectSplits);

                    processLeftTuples(wm, tn.getRule(), firstSplit, false);

                    removeNewPaths(wm, tnms.subjectPmems);

                    Map<PathMemory, SegmentMemory[]> prevSmemsLookup = reInitPathMemories(tnms.otherPmems, tn);

                    // must collect all visited SegmentMemories, for link notification
                    Set<SegmentMemory> smemsToNotify = handleExistingPaths(wm, tn, prevSmemsLookup, tnms.otherPmems,
                            ExistingPathStrategy.REMOVE_STRATEGY);

                    notifySegments(smemsToNotify);
                }
            }

            if (tnms.subjectPmem != null && tnms.subjectPmem.isInitialized() && tnms.subjectPmem.getRuleAgendaItem()
                    .isQueued()) {
                // SubjectPmem can be null, if it was never initialized
                tnms.subjectPmem.getRuleAgendaItem().dequeue();
            }
        }

        if (!hasWms) {
            for (PathEndNode node : pathEndNodes.otherEndNodes) {
                node.resetPathMemSpec(null);
            }
        }
    }

    public interface ExistingPathStrategy {

        ExistingPathStrategy ADD_STRATEGY = new AddExistingPaths();
        ExistingPathStrategy REMOVE_STRATEGY = new RemoveExistingPaths();

        SegmentMemory[] getSegmenMemories(PathMemory pmem);

        void adjustSegment(InternalWorkingMemory wm,
                           Set<SegmentMemory> smemsToNotify,
                           SegmentMemory smem,
                           int smemSplitAdjustAmount);

        void handleSplit(InternalWorkingMemory wm,
                         PathMemory pmem,
                         SegmentMemory[] prevSmems,
                         SegmentMemory[] smems,
                         int smemIndex,
                         int prevSmemIndex,
                         LeftTupleNode parentNode,
                         LeftTupleNode node,
                         TerminalNode tn,
                         Set<LeftTupleNode> visited,
                         Set<SegmentMemory> smemsToNotify,
                         Map<LeftTupleNode, SegmentMemory> nodeToSegmentMap);

        void processSegmentMemories(PathMemory pmem, SegmentMemory[] smems);

        int incSmemIndex1(int smemIndex);

        int incSmemIndex2(int smemIndex);

        int incPrevSmemIndex1(int prevSmemIndex);

        int incPrevSmemIndex2(int prevSmemIndex);
    }

    public static class AddExistingPaths implements ExistingPathStrategy {

        @Override
        public SegmentMemory[] getSegmenMemories(PathMemory pmem) {
            return pmem.getSegmentMemories();
        }

        @Override
        public void adjustSegment(InternalWorkingMemory wm,
                                  Set<SegmentMemory> smemsToNotify,
                                  SegmentMemory smem,
                                  int smemSplitAdjustAmount) {
            smemsToNotify.add(smem);
            smem.unlinkSegment();
            smem.correctSegmentMemoryAfterSplitOnAdd(smemSplitAdjustAmount);
        }

        @Override
        public void handleSplit(InternalWorkingMemory wm,
                                PathMemory pmem,
                                SegmentMemory[] prevSmems,
                                SegmentMemory[] smems,
                                int smemIndex,
                                int prevSmemIndex,
                                LeftTupleNode parentNode,
                                LeftTupleNode node,
                                TerminalNode tn,
                                Set<LeftTupleNode> visited,
                                Set<SegmentMemory> smemsToNotify,
                                Map<LeftTupleNode, SegmentMemory> nodeToSegmentMap) {
            if (smems[smemIndex - 1] != null) {
                SegmentMemory sm2 = nodeToSegmentMap.get(node);
                if (sm2 == null) {
                    SegmentMemory sm1 = smems[smemIndex - 1];
                    correctMemoryOnSplitsChanged(wm, parentNode, null);
                    sm2 = splitSegment(wm, sm1, parentNode);
                    nodeToSegmentMap.put(node, sm2);
                    smemsToNotify.add(sm1);
                    smemsToNotify.add(sm2);
                }

                smems[smemIndex] = sm2;
            }
        }

        @Override
        public void processSegmentMemories(PathMemory pmem, SegmentMemory[] smems) {

        }

        @Override
        public int incSmemIndex1(int smemIndex) {
            return smemIndex + 1;
        }

        @Override
        public int incPrevSmemIndex1(int prevSmemIndex) {
            return prevSmemIndex;
        }

        @Override
        public int incSmemIndex2(int smemIndex) {
            return smemIndex;
        }

        @Override
        public int incPrevSmemIndex2(int prevSmemIndex) {
            return prevSmemIndex + 1;
        }
    }

    public static class RemoveExistingPaths implements ExistingPathStrategy {

        @Override
        public SegmentMemory[] getSegmenMemories(PathMemory pmem) {
            return new SegmentMemory[pmem.getSegmentMemories().length];
        }

        @Override
        public void adjustSegment(InternalWorkingMemory wm,
                                  Set<SegmentMemory> smemsToNotify,
                                  SegmentMemory smem,
                                  int smemSplitAdjustAmount) {
            smemsToNotify.add(smem);
            smem.unlinkSegment();
            smem.correctSegmentMemoryAfterSplitOnRemove(smemSplitAdjustAmount);
        }

        @Override
        public void handleSplit(InternalWorkingMemory wm,
                                PathMemory pmem,
                                SegmentMemory[] prevSmems,
                                SegmentMemory[] smems,
                                int smemIndex,
                                int prevSmemIndex,
                                LeftTupleNode parentNode,
                                LeftTupleNode node,
                                TerminalNode tn,
                                Set<LeftTupleNode> visited,
                                Set<SegmentMemory> smemsToNotify,
                                Map<LeftTupleNode, SegmentMemory> nodeToSegmentMap) {
            if (visited.contains(node)) {
                return;
            }

            correctMemoryOnSplitsChanged(wm, parentNode, tn);

            SegmentMemory sm1 = smems[smemIndex];
            SegmentMemory sm2 = prevSmems[prevSmemIndex];

            if (sm1 != null || sm2 != null) {
                // Temporarily remove the terminal node of the rule to be removed from the rete network to avoid that
                // its path memory could be added to an existing segment memory during the merge of 2 segments
                LeftTupleSource removedTerminalSource = tn.getLeftTupleSource();
                removedTerminalSource.removeTupleSink(tn);

                if (sm1 == null) {
                    sm1 = wm.getSegmentMemorySupport().createChildSegmentLazily(parentNode);
                    smems[smemIndex] = sm1;
                    sm1.add(sm2);
                } else if (sm2 == null) {
                    sm2 = wm.getSegmentMemorySupport().createChildSegmentLazily(node);
                    prevSmems[prevSmemIndex] = sm2;
                    sm1.add(sm2);
                }

                sm1.mergeSegment(sm2);
                smemsToNotify.add(sm1);
                sm1.unlinkSegment();
                sm2.unlinkSegment();
                visited.add(node);

                // Add back the the terminal node of the rule to be removed into the rete network to permit the network
                // traversal up from it and the removal of all the nodes exclusively belonging to the removed rule
                removedTerminalSource.addTupleSink(tn);
            }
        }

        @Override
        public void processSegmentMemories(PathMemory pmem, SegmentMemory[] smems) {
            for (int i = 0; i < smems.length; i++) {
                if (smems[i] != null) {
                    pmem.setSegmentMemory(smems[i].getPos(), smems[i]);
                }
            }
        }

        @Override
        public int incSmemIndex1(int smemIndex) {
            return smemIndex;
        }

        @Override
        public int incPrevSmemIndex1(int prevSmemIndex) {
            return prevSmemIndex + 1;
        }

        @Override
        public int incSmemIndex2(int smemIndex) {
            return smemIndex + 1;
        }

        @Override
        public int incPrevSmemIndex2(int prevSmemIndex) {
            return prevSmemIndex;
        }
    }

    private static Set<SegmentMemory> handleExistingPaths(InternalWorkingMemory wm,
                                                          TerminalNode tn,
                                                          Map<PathMemory, SegmentMemory[]> prevSmemsLookup,
                                                          List<PathMemory> pmems,
                                                          ExistingPathStrategy strategy) {
        Set<SegmentMemory> smemsToNotify = new HashSet<>();
        Set<SegmentMemory> visitedSegments = new HashSet<>();
        Set<LeftTupleNode> visitedNodes = new HashSet<>();
        Map<LeftTupleNode, SegmentMemory> nodeToSegmentMap = new HashMap<>();

        for (PathMemory pmem : pmems) {
            LeftTupleNode[] nodes = pmem.getPathEndNode().getPathNodes();

            SegmentMemory[] prevSmems = prevSmemsLookup.get(pmem);
            SegmentMemory[] smems = strategy.getSegmenMemories(pmem);

            LeftTupleNode node;
            int prevSmemIndex = 0;
            int smemIndex = 0;
            int smemSplitAdjustAmount = 0;
            int nodeIndex = 0;
            int nodeTypesInSegment = 0;

            // excluding the rule just added iterate while not split (i.e. find the next split, prior to this rule being added)
            // note it's checking for when the parent is the split, and thus node is the next root root.

            smems[smemIndex] = prevSmems[prevSmemIndex];
            do {
                node = nodes[nodeIndex++];
                LeftTupleSource parentNode = node.getLeftTupleSource();
                nodeTypesInSegment = BuildtimeSegmentUtilities.updateNodeTypesMask(parentNode, nodeTypesInSegment);
                if (isSplit(parentNode)) {
                    smemIndex = strategy.incSmemIndex1(smemIndex);
                    prevSmemIndex = strategy.incPrevSmemIndex1(prevSmemIndex);
                    if (isSplit(parentNode, tn)) { // check if the split is there even without the processed rule
                        smemIndex = strategy.incSmemIndex2(smemIndex);
                        prevSmemIndex = strategy.incPrevSmemIndex2(prevSmemIndex);
                        smems[smemIndex] = prevSmems[prevSmemIndex];
                        if (smems[smemIndex] != null && smemSplitAdjustAmount > 0 && visitedSegments.add(
                                smems[smemIndex])) {
                            strategy.adjustSegment(wm, smemsToNotify, smems[smemIndex], smemSplitAdjustAmount);
                        }
                    } else {
                        strategy.handleSplit(wm, pmem, prevSmems, smems, smemIndex,
                                prevSmemIndex, parentNode, node, tn,
                                visitedNodes, smemsToNotify, nodeToSegmentMap);
                        smemSplitAdjustAmount++;
                    }
                    wm.getSegmentMemorySupport().checkEagerSegmentCreation(parentNode, nodeTypesInSegment);
                    nodeTypesInSegment = 0;
                }
            } while (!NodeTypeEnums.isEndNode(node));
            strategy.processSegmentMemories(pmem, smems);
        }
        return smemsToNotify;
    }

    private static void addNewPaths(InternalWorkingMemory wm,
                                    Set<SegmentMemory> smemsToNotify,
                                    List<PathMemory> pmems) {
        // Multiple paths may be renetrant, in the case of a second subnetwork on the same rule.
        // Must make sure we don't duplicate the child smem, and when found, just update it with new pmem.
        Set<LeftTupleNode> visited = new HashSet<>();
        for (PathMemory pmem : pmems) {
            LeftTupleSink tipNode = pmem.getPathEndNode();

            LeftTupleNode child = tipNode;
            LeftTupleNode parent = tipNode.getLeftTupleSource();

            while (true) {
                if (visited.add(child)) {
                    if (parent != null && parent.getAssociatedTerminalsSize() != 1 && child
                            .getAssociatedTerminalsSize() == 1) {
                        // This is the split point that the new path enters an existing path.
                        // If the parent has other child SegmentMemorys then it must create a new child SegmentMemory
                        // If the parent is a query node, then it's internal data structure needs changing
                        // all right input data must be propagated
                        Memory mem = wm.getNodeMemories().peekNodeMemory(parent);
                        if (mem != null && mem.getSegmentMemory() != null) {
                            SegmentMemory sm = mem.getSegmentMemory();
                            if (sm.getFirst() != null && sm.size() < parent.getSinkPropagator().size()) {
                                LeftTupleSink[] sinks = parent.getSinkPropagator().getSinks();
                                for (int i = sm.size(); i < sinks.length; i++) {
                                    SegmentMemory childSmem = wm.getSegmentMemorySupport().createChildSegmentLazily(sinks[i]);
                                    sm.add(childSmem);
                                    pmem.setSegmentMemory(childSmem.getPos(), childSmem);
                                    smemsToNotify.add(childSmem);
                                }
                            }
                            correctMemoryOnSplitsChanged(wm, parent, null);
                        }
                    } else {
                        Memory mem = wm.getNodeMemories().peekNodeMemory(child);
                        // The root of each segment
                        if (mem != null) {
                            SegmentMemory sm = mem.getSegmentMemory();
                            if (sm != null && !sm.getPathMemories().contains(pmem)) {
                                pmem.addSegmentToPathMemory(sm);
                                sm.notifyRuleLinkSegment(pmem);
                            }
                        }
                    }
                } else {
                    Memory mem = wm.getNodeMemories().peekNodeMemory(child);
                    if (mem != null) {
                        mem.getSegmentMemory().notifyRuleLinkSegment(pmem);
                    }
                }

                if (parent == null) {
                    break;
                }

                child = parent;
                parent = parent.getLeftTupleSource();
            }
        }
    }

    private static void removeNewPaths(InternalWorkingMemory wm, List<PathMemory> pmems) {
        Set<Integer> visitedNodes = new HashSet<>();
        for (PathMemory pmem : pmems) {
            LeftTupleSink tipNode = pmem.getPathEndNode();

            LeftTupleNode child = tipNode;
            LeftTupleNode parent = tipNode.getLeftTupleSource();

            while (true) {
                if (child.getAssociatedTerminalsSize() == 1 && NodeTypeEnums.isBetaNode(child)) {
                    // If this is a beta node, it'll delete all the right input data
                    deleteRightInputData(wm, (LeftTupleSink) child);
                }

                if (parent != null && parent.getAssociatedTerminalsSize() != 1 && child
                        .getAssociatedTerminalsSize() == 1) {
                    // This is the split point that the new path enters an existing path.
                    // If the parent has other child SegmentMemorys then it must create a new child SegmentMemory
                    // If the parent is a query node, then it's internal data structure needs changing
                    // all right input data must be propagated
                    if (!visitedNodes.contains(child.getId())) {
                        Memory mem = wm.getNodeMemories().peekNodeMemory(parent);
                        if (mem != null && mem.getSegmentMemory() != null) {
                            SegmentMemory sm = mem.getSegmentMemory();
                            if (sm.getFirst() != null) {
                                SegmentMemory childSm = wm.getNodeMemories().peekNodeMemory(child).getSegmentMemory();
                                sm.remove(childSm);
                            }
                        }
                    }
                } else {
                    Memory mem = wm.getNodeMemories().peekNodeMemory(child);
                    // The root of each segment
                    if (mem != null) {
                        SegmentMemory sm = mem.getSegmentMemory();
                        if (sm != null && sm.getPathMemories().contains(pmem)) {
                            mem.getSegmentMemory().removePathMemory(pmem);
                        }
                    }
                }

                if (parent == null) {
                    break;
                }

                visitedNodes.add(child.getId());
                child = parent;
                parent = parent.getLeftTupleSource();
            }
        }
    }

    private static boolean isSplit(LeftTupleNode node) {
        return isSplit(node, null);
    }

    private static boolean isSplit(LeftTupleNode node, TerminalNode removingTN) {
        return node != null && BuildtimeSegmentUtilities.isTipNode(node, removingTN);
    }

    public static class Flushed {

        PathMemory pathMemory;
        SegmentMemory segmentMemory;

        public Flushed(PathMemory pathMemory, SegmentMemory segmentMemory) {
            this.pathMemory = pathMemory;
            this.segmentMemory = segmentMemory;
        }
    }

    public static void flushStagedTuples(InternalWorkingMemory wm,
                                         TerminalNode tn,
                                         PathMemory pmem,
                                         List<LeftTupleNode> splits) {
        // first flush the subject rule, then flush any staging lists that are part of a merge
        if (pmem.isInitialized()) {
            wm.getRuleNetworkEvaluator().evaluateNetwork(pmem.getRuleAgendaItem().getRuleExecutor(), pmem);
        }

        // With the removing rules being flushed, we need to check any splits that will be merged, to see if they need flushing
        // Beware that flushing a higher up node, might again cause lower nodes to have more staged items. So track flushed items
        // incase they need to be reflushed
        List<Flushed> flushed = new ArrayList<>();

        for (LeftTupleNode node : splits) {
            if (!isSplit(node, tn)) { // check if the split is there even without the processed rule
                Memory mem = wm.getNodeMemories().peekNodeMemory(node);
                if (mem != null) {
                    SegmentMemory smem = mem.getSegmentMemory();

                    if (!smem.isEmpty()) {
                        for (SegmentMemory childSmem = smem.getFirst(); childSmem != null; childSmem = childSmem
                                .getNext()) {
                            if (!childSmem.getStagedLeftTuples().isEmpty()) {
                                PathMemory childPmem = childSmem.getPathMemories().get(0);
                                flushed.add(new Flushed(childPmem, childSmem));
                                wm.getRuleNetworkEvaluator().forceFlushLeftTuple(childPmem, childSmem, childSmem.getStagedLeftTuples()
                                .takeAll());
                            }
                        }
                    }
                }
            }
        }

        int flushCount = 1; // need to ensure that there is one full iteration, without any flushing. To avoid one flush causing populat of another already flushed segment
        while (!flushed.isEmpty() && flushCount != 0) {
            flushCount = 0;
            for (Flushed path : flushed) {
                if (!path.segmentMemory.getStagedLeftTuples().isEmpty()) {
                    flushCount++;
                    wm.getRuleNetworkEvaluator().forceFlushLeftTuple(pmem, path.segmentMemory, path.segmentMemory.getStagedLeftTuples()
                    .takeAll());
                }
            }
        }
    }

    private static void flushStagedTuples(InternalWorkingMemory wm, LeftTupleNode splitStartNode, PathMemory pmem) {
        if (!pmem.isInitialized()) {
            // The rule has never been linked in and evaluated, so there will be nothing to flush.
            return;
        }
        int smemIndex = getSegmentPos(splitStartNode); // index before the segments are merged
        SegmentMemory[] smems = pmem.getSegmentMemories();

        SegmentMemory sm = null;

        // If there is no sharing, then there will not be any staged tuples in later segemnts, and thus no need to search for them if the current sm is empty.
        int length = smems.length;
        if (splitStartNode.getAssociatedTerminalsSize() == 1) {
            length = 1;
        }

        while (smemIndex < length) {
            sm = smems[smemIndex];
            if (sm != null && !sm.getStagedLeftTuples().isEmpty()) {
                break;
            }
            smemIndex++;
        }

        if (smemIndex < length) {
            // it only found a SM that needed flushing, if smemIndex < length
            wm.getRuleNetworkEvaluator().forceFlushLeftTuple(pmem, sm, sm.getStagedLeftTuples().takeAll());
        }
    }

    private static Map<PathMemory, SegmentMemory[]> reInitPathMemories(List<PathMemory> pathMems,
                                                                       TerminalNode removingTN) {
        Map<PathMemory, SegmentMemory[]> previousSmems = new HashMap<>();
        for (PathMemory pmem : pathMems) {
            // Re initialise all the PathMemories
            previousSmems.put(pmem, pmem.getSegmentMemories());

            PathEndNode pathEndNode = pmem.getPathEndNode();
            pathEndNode.resetPathMemSpec(removingTN); // re-initialise the PathMemory
            AbstractTerminalNode.initPathMemory(pathEndNode, pmem);
        }
        return previousSmems;
    }

    private static void notifySegments(Set<SegmentMemory> smems) {
        for (SegmentMemory sm : smems) {
            sm.notifyRuleLinkSegment();
        }
    }

    private static void correctMemoryOnSplitsChanged(InternalWorkingMemory wm,
                                                     LeftTupleNode splitStart,
                                                     TerminalNode removingTN) {
        if (splitStart.getType() == NodeTypeEnums.QueryElementNode) {
            QueryElementNode.QueryElementNodeMemory mem = (QueryElementNode.QueryElementNodeMemory) wm.getNodeMemories()
                    .peekNodeMemory(splitStart);
            if (mem != null) {
                mem.correctMemoryOnSinksChanged(removingTN);
            }
        }
    }

    private static int getSegmentPos(LeftTupleNode lts) {
        int counter = 0;
        while (!NodeTypeEnums.isLeftInputAdapterNode(lts)) {
            lts = lts.getLeftTupleSource();
            if (BuildtimeSegmentUtilities.isTipNode(lts, null)) {
                counter++;
            }
        }
        return counter;
    }

    private static void insertLiaFacts(InternalWorkingMemory wm, LeftTupleNode startNode) {
        // rule added with no sharing
        PropagationContextFactory pctxFactory = RuntimeComponentFactory.get().getPropagationContextFactory();
        final PropagationContext pctx = pctxFactory.createPropagationContext(wm.getNextPropagationIdCounter(),
                PropagationContext.Type.RULE_ADDITION, null, null, null);
        LeftInputAdapterNode lian = (LeftInputAdapterNode) startNode;
        attachAdapterAndPropagate(wm, lian, pctx);
    }

    private static void insertFacts(Collection<InternalWorkingMemory> wms, PathEndNodes endNodes) {
        Set<LeftTupleNode> visited = new HashSet<>();

        for (PathEndNode endNode : endNodes.subjectEndNodes) {
            LeftTupleNode[] nodes = endNode.getPathNodes();
            for (int i = 0; i < nodes.length; i++) {
                LeftTupleNode node = nodes[i];
                if (NodeTypeEnums.isBetaNode(node) && node.getAssociatedTerminalsSize() == 1) {
                    if (!visited.add(node)) {
                        continue;// this is to avoid rentering a path, and processing nodes twice. This can happen for nested subnetworks.
                    }
                    BetaNode bn = (BetaNode) node;

                    if (!bn.getRightInput().inputIsTupleToObjectNode()) {
                        for (InternalWorkingMemory wm : wms) {
                            attachAdapterAndPropagate(wm, bn);
                        }
                    }
                }
            }
        }
    }

    private static void deleteRightInputData(InternalWorkingMemory wm, LeftTupleSink node) {
        if (wm.getNodeMemories().peekNodeMemory(node) != null) {
            BetaNode bn = (BetaNode) node;
            BetaMemory bm;
            if (bn.getType() == NodeTypeEnums.AccumulateNode) {
                bm = ((AccumulateMemory) wm.getNodeMemory(bn)).getBetaMemory();
            } else {
                bm = (BetaMemory) wm.getNodeMemory(bn);
            }

            TupleMemory rtm = bm.getRightTupleMemory();
            FastIterator<TupleImpl> it = rtm.fullFastIterator();
            for (TupleImpl rightTuple = BetaNode.getFirstTuple(rtm, it); rightTuple != null;) {
                TupleImpl next = it.next(rightTuple);
                rtm.remove(rightTuple);
                rightTuple.unlinkFromRightParent();
                rightTuple = next;
            }

            if (!bm.getStagedRightTuples().isEmpty()) {
                bm.setNodeDirtyWithoutNotify();
            }
            TupleSets srcRightTuples = bm.getStagedRightTuples().takeAll();

            unlinkRightTuples(srcRightTuples.getInsertFirst());
            unlinkRightTuples(srcRightTuples.getUpdateFirst());
            unlinkRightTuples(srcRightTuples.getDeleteFirst());

            deleteFactsFromRightInput(wm, bn);
        }
    }

    private static void deleteFactsFromRightInput(InternalWorkingMemory wm, BetaNode bn) {
        BaseNode source = bn.getRightInput().getParent();
        if (source.getType() == NodeTypeEnums.WindowNode) {
            WindowNode.WindowMemory memory = wm.getNodeMemory(((WindowNode) source));
            for (DefaultEventHandle factHandle : memory.getFactHandles()) {
                factHandle.forEachRightTuple(rt -> {
                    if (source.equals(rt.getSink())) {
                        rt.unlinkFromRightParent();
                    }
                });
            }
        }
    }

    private static void unlinkRightTuples(TupleImpl rightTuple) {
        for (TupleImpl rt = rightTuple; rt != null;) {
            TupleImpl next = rt.getStagedNext();
            // this RightTuple could have been already unlinked by the former cycle
            if (rt.getFactHandle() != null) {
                rt.unlinkFromRightParent();
            }
            rt = next;
        }
    }

    /**
     * Populates the SegmentMemory with staged LeftTuples. If the parent is not a Beta or From node, it iterates up to find the first node with memory. If necessary
     * It traverses to the LiaNode's ObjectTypeNode. It then iterates the LeftTuple chains, where an existing LeftTuple is staged
     * as delete. Or a new LeftTuple is created and staged as an insert.
     */
    private static void processLeftTuples(InternalWorkingMemory wm, Rule rule, LeftTupleNode node, boolean insert) {
        // *** if you make a fix here, it most likely needs to be in PhreakActivationIteratorToo ***

        // Must iterate up until a node with memory is found, this can be followed to find the LeftTuples
        // which provide the potential peer of the tuple being added or removed

        if (node.getType() == NodeTypeEnums.AlphaTerminalNode) {
            processLeftTuplesOnLian(wm, rule, (LeftInputAdapterNode) node, insert);
            return;
        }

        Memory memory = wm.getNodeMemories().peekNodeMemory(node);
        if (memory == null || memory.getSegmentMemory() == null) {
            // segment has never been initialized, which means the rule(s) have never been linked and thus no Tuples to fix
            return;
        }
        SegmentMemory sm = memory.getSegmentMemory();

        while (!NodeTypeEnums.isLeftInputAdapterNode(node)) {

            if (NodeTypeEnums.isBetaNode(node)) {
                BetaMemory bm;
                if (NodeTypeEnums.AccumulateNode == node.getType()) {
                    AccumulateMemory am = (AccumulateMemory) memory;
                    bm = am.getBetaMemory();
                    FastIterator it = bm.getLeftTupleMemory().fullFastIterator();
                    Tuple lt = BetaNode.getFirstTuple(bm.getLeftTupleMemory(), it);
                    for (; lt != null; lt = (TupleImpl) it.next(lt)) {
                        AccumulateContext accctx = (AccumulateContext) lt.getContextObject();
                        visitChild(wm, rule, (TupleImpl) accctx.getResultLeftTuple(), insert);
                    }
                } else if (NodeTypeEnums.ExistsNode == node.getType() && !node.inputIsTupleToObjectNode()) { // do not process exists with subnetworks
                    // If there is a subnetwork, then there is no populated RTM, but the LTM is populated,
                    // so this would be processed in the "else".

                    bm = (BetaMemory) wm.getNodeMemory((MemoryFactory) node);
                    FastIterator it = bm.getRightTupleMemory().fullFastIterator(); // done off the RightTupleMemory, as exists only have unblocked tuples on the left side
                    for (RightTuple rt = (RightTuple) BetaNode.getFirstTuple(bm.getRightTupleMemory(),
                            it); rt != null; rt = (RightTuple) it.next(rt)) {
                        for (LeftTuple lt = rt.getBlocked(); lt != null; lt = lt.getBlockedNext()) {
                            visitLeftTuple(wm, rule, lt, insert);
                        }
                    }
                } else {
                    bm = (BetaMemory) wm.getNodeMemory((MemoryFactory) node);
                    FastIterator<TupleImpl> it = bm.getLeftTupleMemory().fullFastIterator();
                    for (TupleImpl lt = BetaNode.getFirstTuple(bm.getLeftTupleMemory(), it); lt != null; lt = it.next(
                            lt)) {
                        visitLeftTuple(wm, rule, lt, insert);
                    }
                }
                return;
            } else if (NodeTypeEnums.FromNode == node.getType()) {
                FromMemory fm = (FromMemory) wm.getNodeMemory((MemoryFactory) node);
                TupleMemory ltm = fm.getBetaMemory().getLeftTupleMemory();
                FastIterator<TupleImpl> it = ltm.fullFastIterator();
                for (TupleImpl lt = ltm.getFirst(null); lt != null; lt = it.next(lt)) {
                    visitChild(wm, rule, lt, insert);
                }
                return;
            }
            if (sm.getRootNode() == node) {
                sm = wm.getNodeMemory((MemoryFactory<Memory>) node.getLeftTupleSource()).getSegmentMemory();
            }
            node = node.getLeftTupleSource();
        }

        // No beta or from nodes, so must retrieve LeftTuples from the LiaNode.
        // This is done by scanning all the LeftTuples referenced from the FactHandles in the ObjectTypeNode
        processLeftTuplesOnLian(wm, rule, (LeftInputAdapterNode) node, insert);
    }

    private static void processLeftTuplesOnLian(InternalWorkingMemory wm,
                                                Rule rule,
                                                LeftInputAdapterNode lian,
                                                boolean insert) {
        BaseNode os = lian.getObjectSource();
        while (os.getType() != NodeTypeEnums.ObjectTypeNode) {
            os = os.getParent();
        }

        ObjectTypeNode otn = (ObjectTypeNode) os;
        Iterator<InternalFactHandle> it = otn.getFactHandlesIterator(wm);
        while (it.hasNext()) {
            InternalFactHandle fh = it.next();
            fh.forEachLeftTuple(lt -> {
                TupleImpl nextLt = lt.getHandleNext();

                // Each lt is for a different lian, skip any lian not associated with the rule. Need to use lt parent (souce) not child to check the lian.
                if (SuperCacheFixer.getLeftTupleSource(lt).isAssociatedWith(rule)) {
                    visitChild(wm, rule, lt, insert);

                    if (lt.getHandlePrevious() != null && nextLt != null) {
                        lt.getHandlePrevious().setHandleNext(nextLt);
                        nextLt.setHandlePrevious(lt.getHandlePrevious());
                    }
                }
            });
        }
    }

    private static void visitLeftTuple(InternalWorkingMemory wm, Rule rule, TupleImpl lt, boolean insert) {
        TupleImpl childLt = lt.getFirstChild();
        while (childLt != null) {
            TupleImpl nextLt = childLt.getHandleNext();
            visitChild(wm, rule, childLt, insert);
            childLt = nextLt;
        }
    }

    private static void visitChild(InternalWorkingMemory wm, Rule rule, TupleImpl lt, boolean insert) {
        TupleImpl prevLt = null;
        LeftTupleSinkNode sink = (LeftTupleSinkNode) lt.getSink();

        for (; sink != null; sink = sink.getNextLeftTupleSinkNode()) {

            if (lt != null) {
                if (lt.getSink().isAssociatedWith(rule)) {

                    if (lt.getSink().getAssociatedTerminalsSize() > 1) {
                        if (lt.getFirstChild() != null) {
                            for (TupleImpl child = lt.getFirstChild(); child != null; child = child.getHandleNext()) {
                                visitChild(wm, rule, child, insert);
                            }
                        } else if (lt.getSink().getType() == NodeTypeEnums.TupleToObjectNode) {
                            insertPeerRightTuple(wm, rule, lt, insert);
                        }
                    } else if (!insert) {
                        iterateLeftTuple(wm, lt);
                        TupleImpl lt2 = null;
                        for (TupleImpl peerLt = lt.getPeer(); peerLt != null && peerLt.getSink().isAssociatedWith(
                                rule) && peerLt.getSink().getAssociatedTerminalsSize() == 1; peerLt = peerLt
                                        .getPeer()) {
                            iterateLeftTuple(wm, peerLt);
                            lt2 = peerLt;
                        }

                        // this sink is not shared and is associated with the rule being removed delete it's children
                        deleteLeftTuple(lt, lt2, prevLt);
                        break; // only one rule is deleted at a time, we know there are no more peers to delete so break.
                    }
                }

                prevLt = lt;
                lt = lt.getPeer();
            } else {
                // there is a sink without a peer LT, so create the peer LT
                prevLt = insertPeerLeftTuple(wm, sink, prevLt, insert);
            }
        }
    }

    private static void insertPeerRightTuple(InternalWorkingMemory wm, Rule rule, TupleImpl lt, boolean insert) {
        // There's a shared RightInputAdaterNode, so check if one of its sinks is associated only to the new rule
        TupleImpl prevLt = null;
        TupleToObjectNode tton = (TupleToObjectNode) lt.getSink();

        for (ObjectSink sink : tton.getObjectSinkPropagator().getSinks()) {
            if (lt != null) {
                if (prevLt != null && !insert && sink.isAssociatedWith(rule) && sink
                        .getAssociatedTerminalsSize() == 1) {
                    prevLt.setPeer(null);
                }
                prevLt = lt;
                lt = lt.getPeer();
            } else if (insert) {
                BetaNode bn = ((RightInputAdapterNode) sink).getBetaNode();
                BetaMemory bm = (BetaMemory) wm.getNodeMemory(bn);
                prevLt = TupleFactory.createPeer(tton, prevLt);
                bm.linkNode(bn, wm);
                bm.getStagedRightTuples().addInsert(prevLt);
            }
        }
    }

    /**
     * Create all missing peers
     */
    private static TupleImpl insertPeerLeftTuple(InternalWorkingMemory wm,
                                                 LeftTupleSinkNode node,
                                                 TupleImpl lt,
                                                 boolean insert) {
        TupleImpl peer = TupleFactory.createPeer(node, lt);

        if (node.getLeftTupleSource().getType() == NodeTypeEnums.AlphaTerminalNode) {
            if (insert) {
                TerminalNode rtn = (TerminalNode) node;
                InternalAgenda agenda = wm.getAgenda();
                RuleAgendaItem agendaItem = AlphaTerminalNode.getRuleAgendaItem(wm, rtn, insert);
                PhreakRuleTerminalNode.doLeftTupleInsert(wm, rtn, agendaItem.getRuleExecutor(), agenda, agendaItem,
                        (RuleTerminalNodeLeftTuple) peer);
            }
            return peer;
        }

        LeftInputAdapterNode.LiaNodeMemory liaMem = null;
        if (NodeTypeEnums.isLeftInputAdapterNode(node.getLeftTupleSource())) {
            liaMem = wm.getNodeMemory(((LeftInputAdapterNode) node.getLeftTupleSource()));
        }

        Memory memory = wm.getNodeMemories().peekNodeMemory(node);
        if (memory == null || memory.getSegmentMemory() == null) {
            throw new IllegalStateException(
                    "Defensive Programming: this should not be possilbe, as the addRule code should init child segments if they are needed ");
        }

        if (liaMem == null) {
            memory.getSegmentMemory().getStagedLeftTuples().addInsert(peer);
        } else {
            // If parent is Lian, then this must be called, so that any linking or unlinking can be done.
            List<PathMemory> pathsToFlush = LeftInputAdapterNode.doInsertSegmentMemory(wm, true, liaMem, memory.getSegmentMemory(), peer, node
                                .getLeftTupleSource().isStreamMode() );
            wm.getRuleNetworkEvaluator().forceFlushPaths(pathsToFlush);
        }

        return peer;
    }

    private static void iterateLeftTuple(InternalWorkingMemory wm, TupleImpl lt) {
        if (NodeTypeEnums.isTerminalNode(lt.getSink())) {
            PathMemory pmem = (PathMemory) wm.getNodeMemories().peekNodeMemory(lt.getSink());
            if (pmem != null) {
                PhreakRuleTerminalNode.doLeftDelete(pmem.getActualActivationsManager(), pmem.getRuleAgendaItem()
                        .getRuleExecutor(), (RuleTerminalNodeLeftTuple) lt);
            }
        } else {
            if (lt.getContextObject() instanceof AccumulateContext) {
                TupleImpl resultLt = (TupleImpl) ((AccumulateContext) lt.getContextObject()).getResultLeftTuple();
                if (resultLt != null) {
                    iterateLeftTuple(wm, resultLt);
                }
            }
            for (TupleImpl child = lt.getFirstChild(); child != null; child = child.getHandleNext()) {
                for (TupleImpl peer = child; peer != null; peer = peer.getPeer()) {
                    if (peer.getPeer() == null) {
                        // it's unnnecessary to visit the unshared networks, so only iterate the last peer
                        iterateLeftTuple(wm, peer);
                    }
                }
            }
        }
    }

    private static LeftTupleNode getNetworkSplitPoint(LeftTupleNode node) {
        while (!NodeTypeEnums.isLeftInputAdapterNode(node) && node.getAssociatedTerminalsSize() == 1) {
            node = node.getLeftTupleSource();
        }

        return node;
    }

    public static SegmentMemory splitSegment(InternalWorkingMemory wm, SegmentMemory sm1, LeftTupleNode splitNode) {
        // create new segment, starting after split
        LeftTupleNode childNode = splitNode.getSinkPropagator().getFirstLeftTupleSink();
        SegmentMemory sm2 = new SegmentMemory(childNode); // we know there is only one sink
        wm.getNodeMemories().peekNodeMemory(childNode).setSegmentMemory(sm2);

        return sm1.splitSegmentOn(sm2, splitNode);
    }

    private static PathEndNodeMemories getPathEndMemories(InternalWorkingMemory wm,
                                                          PathEndNodes pathEndNodes) {
        PathEndNodeMemories tnMems = new PathEndNodeMemories();

        for (LeftTupleNode node : pathEndNodes.otherEndNodes) {
            if (node.getType() == NodeTypeEnums.TupleToObjectNode) {
                SubnetworkPathMemory subnMem = (SubnetworkPathMemory) wm.getNodeMemories().peekNodeMemory(node);
                if (subnMem != null) {
                    tnMems.otherPmems.add(subnMem);
                }
            } else {
                PathMemory pmem = (PathMemory) wm.getNodeMemories().peekNodeMemory(node);
                if (pmem != null) {
                    tnMems.otherPmems.add(pmem);
                }
            }
        }

        tnMems.subjectPmem = (PathMemory) wm.getNodeMemories().peekNodeMemory(pathEndNodes.subjectEndNode);
        if (tnMems.subjectPmem == null && !tnMems.otherPmems.isEmpty()) {
            // If "other pmem's are initialized, then the subject needs to be initialized too.
            tnMems.subjectPmem = wm.getNodeMemory(pathEndNodes.subjectEndNode);
        }

        for (LeftTupleNode node : pathEndNodes.subjectEndNodes) {
            if (node.getType() == NodeTypeEnums.TupleToObjectNode) {
                SubnetworkPathMemory subnMem = (SubnetworkPathMemory) wm.getNodeMemories().peekNodeMemory(node);
                if (subnMem == null && !tnMems.otherPmems.isEmpty()) {
                    subnMem = (SubnetworkPathMemory) wm.getNodeMemory((MemoryFactory<Memory>) node);
                }
                if (subnMem != null) {
                    tnMems.subjectPmems.add(subnMem);
                }
            } else {
                PathMemory pmem = (PathMemory) wm.getNodeMemories().peekNodeMemory(node);
                if (pmem != null) {
                    tnMems.subjectPmems.add(pmem);
                }
            }
        }

        return tnMems;
    }

    private static class PathEndNodeMemories {

        PathMemory subjectPmem;
        List<PathMemory> subjectPmems = new ArrayList<>();
        List<PathMemory> otherPmems = new ArrayList<>();
    }

    private static PathEndNodes getPathEndNodes(SegmentPrototypeRegistry segmentPrototypeRegistry,
                                                Rule processedRule,
                                                LeftTupleNode lt,
                                                TerminalNode tn,
                                                boolean hasProtos,
                                                boolean hasWms) {
        PathEndNodes endNodes = new PathEndNodes();
        endNodes.subjectEndNode = tn;
        endNodes.subjectEndNodes.add(tn);
        if (hasWms && BuildtimeSegmentUtilities.isTipNode(lt, null)) {
            endNodes.subjectSplit = lt;
            endNodes.subjectSplits.add(lt);
        }

        if (hasProtos) {
            invalidateRootNode(segmentPrototypeRegistry, lt);
        }

        collectPathEndNodes(segmentPrototypeRegistry, processedRule, lt, endNodes, tn, hasProtos, hasWms, hasProtos && isSplit(lt));

        return endNodes;
    }

    private static void collectPathEndNodes(SegmentPrototypeRegistry segmentPrototypeRegistry,
                                            Rule processedRule,
                                            LeftTupleNode lt,
                                            PathEndNodes endNodes,
                                            TerminalNode tn,
                                            boolean hasProtos,
                                            boolean hasWms,
                                            boolean isBelowNewSplit) {
        // Traverses the sinks in reverse order in order to collect PathEndNodes so that
        // the outermost (sub)network are evaluated before the innermost one
        for (LeftTupleSinkNode sink = lt.getSinkPropagator().getLastLeftTupleSink(); sink != null; sink = sink
                .getPreviousLeftTupleSinkNode()) {
            if (sink == tn) {
                continue;
            }
            if (hasProtos) {
                if (isBelowNewSplit) {
                    if (isRootNode(sink, null)) {
                        segmentPrototypeRegistry.invalidateSegmentPrototype(sink);
                    }
                } else {
                    isBelowNewSplit = isSplit(sink);
                    if (isBelowNewSplit) {
                        invalidateRootNode(segmentPrototypeRegistry, sink);
                    }
                }
            }
            if (NodeTypeEnums.isLeftTupleSource(sink)) {
                if (hasWms && BuildtimeSegmentUtilities.isTipNode(sink, null)) {
                    if (!BuildtimeSegmentUtilities.isTipNode(sink, tn)) {
                        endNodes.subjectSplits.add(sink);
                    }
                }

                collectPathEndNodes(segmentPrototypeRegistry, processedRule, sink, endNodes, tn, hasProtos, hasWms, isBelowNewSplit);
            } else if (NodeTypeEnums.isTerminalNode(sink)) {
                endNodes.otherEndNodes.add((PathEndNode) sink);
            } else if (NodeTypeEnums.TupleToObjectNode == sink.getType()) {
                if (sink.isAssociatedWith(processedRule)) {
                    endNodes.subjectEndNodes.add((PathEndNode) sink);
                }
                if (sink.getAssociatedTerminalsSize() > 1 || !sink.isAssociatedWith(processedRule)) {
                    endNodes.otherEndNodes.add((PathEndNode) sink);
                }
            } else {
                throw new RuntimeException("Error: Unknown Node. Defensive programming test..");
            }
        }
    }

    private static void invalidateRootNode(SegmentPrototypeRegistry segmentPrototypeRegistry, LeftTupleNode lt) {
        while (!isRootNode(lt, null)) {
            lt = lt.getLeftTupleSource();
        }
        segmentPrototypeRegistry.invalidateSegmentPrototype(lt);
    }

    private static class PathEndNodes {

        PathEndNode subjectEndNode;
        LeftTupleNode subjectSplit;
        List<PathEndNode> subjectEndNodes = new ArrayList<>();
        List<LeftTupleNode> subjectSplits = new ArrayList<>();
        List<PathEndNode> otherEndNodes = new ArrayList<>();
    }
}
