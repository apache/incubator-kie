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
import java.util.List;

import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.constraint.QueryNameConstraint;
import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.NodeMemories;
import org.drools.core.common.SegmentMemorySupport;
import org.drools.core.reteoo.AbstractTerminalNode;
import org.drools.core.reteoo.AccumulateNode;
import org.drools.core.reteoo.AlphaNode;
import org.drools.core.reteoo.AsyncReceiveNode;
import org.drools.core.reteoo.AsyncSendNode;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.ConditionalBranchNode;
import org.drools.core.reteoo.EvalConditionNode;
import org.drools.core.reteoo.FromNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftTupleNode;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.LeftTupleSinkNode;
import org.drools.core.reteoo.LeftTupleSinkPropagator;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.ObjectSink;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.PathEndNode;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.QueryElementNode;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.core.reteoo.SegmentPrototypeRegistry;
import org.drools.core.reteoo.TimerNode;
import org.drools.core.reteoo.TupleToObjectNode;
import org.drools.core.reteoo.LeftInputAdapterNode.LiaNodeMemory;
import org.drools.core.reteoo.SegmentMemory.SegmentPrototype;
import org.drools.core.reteoo.TupleToObjectNode.SubnetworkPathMemory;

import static org.drools.core.phreak.BuildtimeSegmentUtilities.JOIN_NODE_BIT;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.NOT_NODE_BIT;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.REACTIVE_EXISTS_NODE_BIT;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.canBeDisabled;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.isNonTerminalTipNode;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.isRootNode;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.isSet;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.nextNodePosMask;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.updateNodeTypesMask;
import static org.drools.core.phreak.EagerPhreakBuilder.isInsideSubnetwork;

public class SegmentMemorySupportImpl implements SegmentMemorySupport {

    private final NodeMemories nodeMemories;
    private final SegmentPrototypeRegistry segmentPrototypeRegistry;
    private final WorkingMemoryEntryPoint defaultEntryPoint;

    public SegmentMemorySupportImpl(NodeMemories nodeMemories,
                                    SegmentPrototypeRegistry segmentPrototypeRegistry,
                                    WorkingMemoryEntryPoint defaultEntryPoint) {
        this.nodeMemories = nodeMemories;
        this.segmentPrototypeRegistry = segmentPrototypeRegistry;
        this.defaultEntryPoint = defaultEntryPoint;
    }

    /**
     * Initialises the NodeSegment memory for all nodes in the segment.
     */
    public SegmentMemory getOrCreateSegmentMemory(LeftTupleNode node) {
        return getOrCreateSegmentMemory(node, nodeMemories.getNodeMemory(
                (MemoryFactory<? extends Memory>) node));
    }

    /**
     * Initialises the NodeSegment memory for all nodes in the segment.
     */
    public SegmentMemory getOrCreateSegmentMemory(LeftTupleNode node, Memory memory) {
        SegmentMemory smem = memory.getSegmentMemory();
        if (smem != null) {
            return smem;
        }

        // find segment root
        LeftTupleNode segmentRoot = BuildtimeSegmentUtilities.findSegmentRoot(node);

        smem = restoreSegmentFromPrototype(segmentRoot);
        if (smem != null) {
            if (NodeTypeEnums.isBetaNode(segmentRoot) && segmentRoot.inputIsTupleToObjectNode()) {
                createSubnetworkSegmentMemory((BetaNode) segmentRoot);
            }
            return smem;
        }

        // it should not be possible to reach here, for BuildTimeSegmentProtos
        return createSegmentMemoryLazily(segmentRoot);
    }

    private SegmentMemory createSegmentMemoryLazily(LeftTupleNode segmentRoot) {
        if (NodeTypeEnums.isTerminalNode(segmentRoot)) {
            Memory memory = nodeMemories.getNodeMemory((MemoryFactory) segmentRoot);
            return createChildSegmentForTerminalNode(segmentRoot, memory);
        }
        return createSegmentMemoryLazily((LeftTupleSource) segmentRoot);
    }

    private static SegmentMemory createChildSegmentForTerminalNode(LeftTupleNode node, Memory memory) {
        SegmentMemory childSmem = new SegmentMemory(node); // rtns or TupleToObjectNodes don't need a queue
        PathMemory pmem = (PathMemory) memory;

        childSmem.setPos(pmem.getSegmentMemories().length - 1);
        pmem.setSegmentMemory(childSmem);
        pmem.addSegmentToPathMemory(childSmem);

        childSmem.setTipNode(node);
        childSmem.setNodeMemories(new Memory[]{memory});
        return childSmem;
    }

    private SegmentMemory restoreSegmentFromPrototype(LeftTupleNode segmentRoot) {
        SegmentPrototype proto = segmentPrototypeRegistry.getSegmentPrototype(segmentRoot);
        if (proto == null || proto.getNodesInSegment() == null) {
            return null;
        }

        LeftTupleNode lastNode = proto.getNodesInSegment()[proto.getNodesInSegment().length - 1];

        if (NodeTypeEnums.isTerminalNode(lastNode)) {
            // If the last node is a tn and it's pmem does not exist, instantiate it separately to avoid a recursive smem/pmem creation.
            // As the smem will create the pmem and thus peek will no longer work.
            PathMemory pmem = (PathMemory) nodeMemories.peekNodeMemory(lastNode);
            if (pmem == null) {
                pmem = initializePathMemory((PathEndNode) lastNode);
            }

            SegmentMemory smem = pmem.getSegmentMemories()[proto.getPos()];
            if (smem != null) {
                return smem;
            }
        }

        SegmentMemory smem = segmentPrototypeRegistry.createSegmentFromPrototype(proto, nodeMemories, this);

        updateSubnetworkAndTerminalMemory(smem, proto);

        return smem;
    }

    private void restoreSegmentFromPrototypeLazily(LeftTupleSource segmentRoot,
                                                   int nodeTypesInSegment) {
        SegmentMemory smem = segmentPrototypeRegistry.createSegmentFromPrototype(nodeMemories, this, segmentRoot);
        if (smem != null) {
            updateSubnetworkAndTerminalMemoryLazily(segmentRoot, segmentRoot, smem, true, nodeTypesInSegment);
        }
    }

    public SegmentMemory getQuerySegmentMemory(QueryElementNode queryNode) {
        ObjectTypeNode queryOtn = defaultEntryPoint.getEntryPointNode().getQueryNode();
        LeftInputAdapterNode liaNode = getQueryLiaNode(queryOtn, queryNode.getQueryElement().getQueryName());
        LiaNodeMemory liam = nodeMemories.getNodeMemory(liaNode);
        SegmentMemory querySmem = liam.getSegmentMemory();
        if (querySmem == null) {
            querySmem = getOrCreateSegmentMemory(liaNode, liam);
        }
        return querySmem;
    }

    private TupleToObjectNode createSubnetworkSegmentMemory(BetaNode betaNode) {
        TupleToObjectNode tton = (TupleToObjectNode) betaNode.getRightInput().getParent();

        LeftTupleSource subnetworkLts = tton.getStartTupleSource();

        Memory rootSubNetwokrMem = nodeMemories.getNodeMemory((MemoryFactory) subnetworkLts);
        SegmentMemory subNetworkSegmentMemory = rootSubNetwokrMem.getSegmentMemory();
        if (subNetworkSegmentMemory == null) {
            // we need to stop recursion here
            getOrCreateSegmentMemory(subnetworkLts, rootSubNetwokrMem);
        }
        return tton;
    }

    public SegmentMemory createChildSegmentLazily(LeftTupleNode node) {
        Memory memory = nodeMemories.getNodeMemory((MemoryFactory) node);
        if (memory.getSegmentMemory() == null) {
            if (NodeTypeEnums.isEndNode(node)) {
                // RTNS and TupleToObjectNode's have their own segment, if they are the child of a split.
                createChildSegmentForTerminalNode(node, memory);
            } else {
                createSegmentMemoryLazily((LeftTupleSource) node);
            }
        }
        return memory.getSegmentMemory();
    }

    @Override
    public void initializeChildSegmentsIfNeeded(SegmentMemory smem) {
        LeftTupleSinkPropagator sinkPropagator = smem.getTipNode().getSinkPropagator();
        if (!smem.isEmpty() && sinkPropagator.size() == 1) {
            return; // threading guard: single-sink child already initialized
        }
        // When the tip node has multiple sinks (segment split by a sharing rule), some children
        // may already have been added to smem while sibling children are still missing.
        // Iterate all sinks, create any missing child segment, and add it only if not already present.
        for (LeftTupleSinkNode sink = sinkPropagator.getFirstLeftTupleSink(); sink != null; sink = sink
                .getNextLeftTupleSinkNode()) {
            SegmentMemory childSmem = PhreakBuilder.isEagerSegmentCreation() ? createChildSegment(sink)
                    : createChildSegmentLazily(sink);
            if (childSmem != null && !smem.contains(childSmem)) {
                smem.add(childSmem);
            }
        }
    }

    public SegmentMemory createChildSegment(LeftTupleNode node) {
        Memory memory = nodeMemories.getNodeMemory((MemoryFactory) node);
        if (memory.getSegmentMemory() == null) {
            getOrCreateSegmentMemory(node, memory);
        }
        return memory.getSegmentMemory();
    }

    /**
     * This adds the segment memory to the terminal node or TupleToObjectNode node's list of memories.
     * In the case of the terminal node this allows it to know that all segments from
     * the tip to root are linked.
     * In the case of the TupleToObjectNode node its all the segments up to the start of the subnetwork.
     * This is because the TupleToObjectNode only cares if all of it's segments are linked, then
     * it sets the bit of node it is the right input for.
     */
    private void updateSubnetworkAndTerminalMemory(SegmentMemory smem, SegmentPrototype proto) {
        for (PathEndNode endNode : proto.getPathEndNodes()) {
            if (!isInsideSubnetwork(endNode, proto)) {
                // While SegmentPrototypes are added for entire path, for traversal reasons.
                // SegmenrMemory's themselves are only added to the PathMemory for path or subpath the are part of.
                continue;
            }

            PathMemory pmem = (PathMemory) nodeMemories.peekNodeMemory(endNode);
            if (pmem != null) {
                pmem.addSegmentToPathMemory(smem);
            } else {
                pmem = nodeMemories.getNodeMemory((MemoryFactory<? extends PathMemory>) endNode);
                pmem.addSegmentToPathMemory(smem); // this needs to be set before init, to avoid recursion during eager segment initialisation
                pmem.setSegmentMemory(smem.getPos(), smem);
                initializePathMemory(endNode, pmem);
            }

            if (smem.getAllLinkedMaskTest() > 0 && smem.isSegmentLinked()) {
                // not's can cause segments to be linked, and the rules need to be notified for evaluation
                smem.notifyRuleLinkSegment();
            }
        }
    }

    public PathMemory initializePathMemory(PathEndNode pathEndNode) {
        PathMemory pmem = nodeMemories.getNodeMemory(pathEndNode);
        initializePathMemory(pathEndNode, pmem);
        return pmem;
    }

    private void initializePathMemory(PathEndNode pathEndNode, PathMemory pmem) {
        if (pathEndNode.getEagerSegmentPrototypes() != null) {
            for (SegmentPrototype eager : pathEndNode.getEagerSegmentPrototypes()) {
                if (pmem.getSegmentMemories()[eager.getPos()] == null) {
                    getOrCreateSegmentMemory(eager.getRootNode());
                }
            }
        }
    }

    private LeftInputAdapterNode getQueryLiaNode(ObjectTypeNode queryOtn, String queryName) {
        for (ObjectSink sink : queryOtn.getObjectSinkPropagator().getSinks()) {
            AlphaNode alphaNode = (AlphaNode) sink;
            QueryNameConstraint nameConstraint = (QueryNameConstraint) alphaNode.getConstraint();
            if (queryName.equals(nameConstraint.getQueryName())) {
                return (LeftInputAdapterNode) alphaNode.getObjectSinkPropagator().getSinks()[0];
            }
        }

        throw new RuntimeException("Unable to find query '" + queryName + "'");
    }

    public void checkEagerSegmentCreation(LeftTupleSource lt, int nodeTypesInSegment) {
        // A Not node has to be eagerly initialized unless in its segment there is at least a join node
        if (isSet(nodeTypesInSegment, NOT_NODE_BIT) &&
            !isSet(nodeTypesInSegment, JOIN_NODE_BIT) &&
            !isSet(nodeTypesInSegment, REACTIVE_EXISTS_NODE_BIT)) {
            getOrCreateSegmentMemory(lt);
        }
    }

    private int checkSegmentBoundary(LeftTupleSource lt, int nodeTypesInSegment) {
        if (isRootNode(lt, null)) {
            // we are in a new child segment
            checkEagerSegmentCreation(lt.getLeftTupleSource(), nodeTypesInSegment);
            nodeTypesInSegment = 0;
        }
        return updateNodeTypesMask(lt, nodeTypesInSegment);
    }

    /**
     * This adds the segment memory to the terminal node or TupleToObjectNode node's list of memories.
     * In the case of the terminal node this allows it to know that all segments from
     * the tip to root are linked.
     * In the case of the ria node its all the segments up to the start of the subnetwork.
     * This is because the TupleToObjectNode only cares if all of it's segments are linked, then
     * it sets the bit of node it is the right input for.
     */
    private int updateSubnetworkAndTerminalMemoryLazily(LeftTupleSource lt,
                                                        LeftTupleSource originalLt,
                                                        SegmentMemory smem,
                                                        boolean fromPrototype,
                                                        int nodeTypesInSegment) {

        nodeTypesInSegment = checkSegmentBoundary(lt, nodeTypesInSegment);

        PathMemory pmem = null;
        for (LeftTupleSink sink : lt.getSinkPropagator().getSinks()) {
            if (NodeTypeEnums.isLeftTupleSource(sink)) {
                nodeTypesInSegment = updateSubnetworkAndTerminalMemoryLazily((LeftTupleSource) sink,
                        originalLt, smem, fromPrototype, nodeTypesInSegment);
            } else if (sink.getType() == NodeTypeEnums.TupleToObjectNode) {
                // Even though we don't add the pmem and smem together, all pmem's for all pathend nodes must be initialized
                SubnetworkPathMemory subnMem = (SubnetworkPathMemory) nodeMemories.getNodeMemory((MemoryFactory) sink);
                // Only add the TupleToObjectNode, if the LeftTupleSource is part of the TupleToObjectNode subnetwork
                if (inSubNetwork((TupleToObjectNode) sink, originalLt)) {
                    pmem = subnMem;

                    if (fromPrototype) {
                        ObjectSink[] nodes = ((TupleToObjectNode) sink).getObjectSinkPropagator().getSinks();
                        for (ObjectSink node : nodes) {
                            // check if the SegmentMemory has been already created by the BetaNode and if so avoid to build it twice
                            if (NodeTypeEnums.isLeftTupleSource(node) && nodeMemories.getNodeMemory(
                                    (MemoryFactory) node).getSegmentMemory() == null) {
                                restoreSegmentFromPrototypeLazily((LeftTupleSource) node, nodeTypesInSegment);
                            }
                        }
                    } else if ((pmem.getAllLinkedMaskTest() & (1L << pmem.getSegmentMemories().length)) == 0) {
                        // must eagerly initialize child segment memories
                        ObjectSink[] nodes = ((TupleToObjectNode) sink).getObjectSinkPropagator().getSinks();
                        for (ObjectSink node : nodes) {
                            if (NodeTypeEnums.isLeftTupleSource(node)) {
                                getOrCreateSegmentMemory((LeftTupleSource) node);
                            }
                        }
                    }
                }

            } else if (NodeTypeEnums.isTerminalNode(sink)) {
                pmem = nodeMemories.getNodeMemory((AbstractTerminalNode) sink);
            }

            if (pmem != null && smem.getPos() < pmem.getSegmentMemories().length) {
                pmem.addSegmentToPathMemory(smem);
                if (smem.isSegmentLinked()) {
                    // not's can cause segments to be linked, and the rules need to be notified for evaluation
                    smem.notifyRuleLinkSegment();
                }
                checkEagerSegmentCreation(sink.getLeftTupleSource(), nodeTypesInSegment);
                pmem = null;
            }
        }
        return nodeTypesInSegment;
    }

    /**
     * Is the LeftTupleSource a node in the sub network for the RightInputAdapterNode
     * To be in the same network, it must be a node is after the two output of the parent
     * and before the TupleToObjectNode.
     */
    private static boolean inSubNetwork(TupleToObjectNode tton, LeftTupleSource leftTupleSource) {
        LeftTupleSource startTupleSource = tton.getStartTupleSource().getLeftTupleSource();
        LeftTupleSource current = tton.getLeftTupleSource();

        while (current != startTupleSource) {
            if (current == leftTupleSource) {
                return true;
            }
            current = current.getLeftTupleSource();
        }

        return false;
    }

    public SegmentMemory createSegmentMemoryLazily(LeftTupleSource segmentRoot) {
        LeftTupleSource tupleSource = segmentRoot;
        SegmentMemory smem = new SegmentMemory(segmentRoot);

        // Iterate all nodes on the same segment, assigning their position as a bit mask value
        // allLinkedTestMask is the resulting mask used to test if all nodes are linked in
        long nodePosMask = 1;
        long allLinkedTestMask = 0;
        boolean updateNodeBit = true; // nodes after a branch CE can notify, but they cannot impact linking

        int nodeTypesInSegment = 0;
        List<Memory> memories = new ArrayList<>();
        while (true) {
            nodeTypesInSegment = updateNodeTypesMask(tupleSource, nodeTypesInSegment);
            if (NodeTypeEnums.isBetaNode(tupleSource)) {
                allLinkedTestMask = processBetaNode((BetaNode) tupleSource, smem, memories, nodePosMask,
                        allLinkedTestMask,
                        updateNodeBit);
            } else {
                switch (tupleSource.getType()) {
                    case NodeTypeEnums.LeftInputAdapterNode:
                    case NodeTypeEnums.AlphaTerminalNode:
                        allLinkedTestMask = processLiaNode((LeftInputAdapterNode) tupleSource, smem, memories,
                                nodePosMask, allLinkedTestMask);
                        break;
                    case NodeTypeEnums.EvalConditionNode:
                        processEvalNode((EvalConditionNode) tupleSource, smem, memories);
                        break;
                    case NodeTypeEnums.ConditionalBranchNode:
                        updateNodeBit = processBranchNode((ConditionalBranchNode) tupleSource, smem, memories);
                        break;
                    case NodeTypeEnums.FromNode:
                        processFromNode((FromNode) tupleSource, smem, memories);
                        break;
                    case NodeTypeEnums.ReactiveFromNode:
                        processReactiveFromNode((MemoryFactory) tupleSource, smem, memories, nodePosMask);
                        break;
                    case NodeTypeEnums.TimerConditionNode:
                        processTimerNode((TimerNode) tupleSource, smem, memories, nodePosMask);
                        break;
                    case NodeTypeEnums.AsyncSendNode:
                        processAsyncSendNode((AsyncSendNode) tupleSource, smem, memories);
                        break;
                    case NodeTypeEnums.AsyncReceiveNode:
                        processAsyncReceiveNode((AsyncReceiveNode) tupleSource, smem, memories, nodePosMask);
                        break;
                    case NodeTypeEnums.QueryElementNode:
                        updateNodeBit = processQueryNode((QueryElementNode) tupleSource, smem, memories,
                                nodePosMask);
                        break;
                }
            }

            nodePosMask = nextNodePosMask(nodePosMask);

            if (tupleSource.getSinkPropagator().size() == 1) {
                LeftTupleSinkNode sink = tupleSource.getSinkPropagator().getFirstLeftTupleSink();
                if (NodeTypeEnums.isLeftTupleSource(sink)) {
                    tupleSource = (LeftTupleSource) sink;
                } else {
                    // rtn or TupleToObjectNode
                    // While not technically in a segment, we want to be able to iterate easily from the last node memory to the TupleToObjectNode/rtn memory
                    // we don't use createNodeMemory, as these may already have been created by, but not added, by the method updateTupleToObjectAndTerminalMemory
                    Memory memory = nodeMemories.getNodeMemory((MemoryFactory) sink);
                    if (sink.getType() == NodeTypeEnums.TupleToObjectNode) {
                        PathMemory subnMem = (SubnetworkPathMemory) memory;
                        memories.add(subnMem);

                        TupleToObjectNode tton = (TupleToObjectNode) sink;
                        ObjectSink[] nodes = tton.getObjectSinkPropagator().getSinks();
                        for (ObjectSink node : nodes) {
                            if (NodeTypeEnums.isLeftTupleSource(node)) {
                                getOrCreateSegmentMemory((LeftTupleSource) node);
                            }
                        }
                    } else if (NodeTypeEnums.isTerminalNode(sink)) {
                        memories.add(memory);
                    }
                    memory.setSegmentMemory(smem);
                    smem.setTipNode(sink);
                    break;
                }
            } else {
                // not in same segment
                smem.setTipNode(tupleSource);
                break;
            }
        }
        smem.setAllLinkedMaskTest(allLinkedTestMask);
        smem.setNodeMemories(memories.toArray(new Memory[memories.size()]));

        // Update the memory linked references
        Memory lastMem = null;
        for (Memory mem : memories) {
            if (lastMem != null) {
                mem.setPrevious(lastMem);
                lastMem.setNext(mem);
            }
            lastMem = mem;
        }

        // iterate to find root and determine the SegmentNodes position in the RuleSegment
        LeftTupleSource pathRoot = segmentRoot;
        int ruleSegmentPosMask = 1;
        int counter = 0;
        while (!NodeTypeEnums.isLeftInputAdapterNode(pathRoot)) {
            LeftTupleSource leftTupleSource = pathRoot.getLeftTupleSource();
            if (isNonTerminalTipNode(leftTupleSource, null)) {
                // for each new found segment, increase the mask bit position
                ruleSegmentPosMask = ruleSegmentPosMask << 1;
                counter++;
            }
            pathRoot = leftTupleSource;
        }
        smem.setSegmentPosMaskBit(ruleSegmentPosMask);
        smem.setPos(counter);

        updateSubnetworkAndTerminalMemoryLazily(tupleSource, tupleSource, smem, false, nodeTypesInSegment);

        segmentPrototypeRegistry.registerSegmentPrototype(segmentRoot, smem.getSegmentPrototype().initFromSegmentMemory(
                smem));

        return smem;
    }

    private boolean processQueryNode(QueryElementNode queryNode,
                                     SegmentMemory smem,
                                     List<Memory> memories,
                                     long nodePosMask) {
        // Initialize the QueryElementNode and have it's memory reference the actual query SegmentMemory
        SegmentMemory querySmem = getQuerySegmentMemory(queryNode);
        QueryElementNode.QueryElementNodeMemory queryNodeMem = nodeMemories.getNodeMemory(queryNode);
        queryNodeMem.setNodePosMaskBit(nodePosMask);
        queryNodeMem.setQuerySegmentMemory(querySmem);
        queryNodeMem.setSegmentMemory(smem);
        memories.add(queryNodeMem);
        return !queryNode.getQueryElement().isAbductive();
    }

    private void processFromNode(MemoryFactory tupleSource,
                                 SegmentMemory smem,
                                 List<Memory> memories) {
        Memory mem = nodeMemories.getNodeMemory(tupleSource);
        memories.add(mem);
        mem.setSegmentMemory(smem);
    }

    private void processAsyncSendNode(MemoryFactory tupleSource,
                                      SegmentMemory smem,
                                      List<Memory> memories) {
        Memory mem = nodeMemories.getNodeMemory(tupleSource);
        mem.setSegmentMemory(smem);
        memories.add(mem);
    }

    private void processAsyncReceiveNode(AsyncReceiveNode tupleSource,
                                         SegmentMemory smem,
                                         List<Memory> memories,
                                         long nodePosMask) {
        AsyncReceiveNode.AsyncReceiveMemory tnMem = nodeMemories.getNodeMemory(tupleSource);
        memories.add(tnMem);
        tnMem.setNodePosMaskBit(nodePosMask);
        tnMem.setSegmentMemory(smem);
    }

    private void processReactiveFromNode(MemoryFactory tupleSource,
                                         SegmentMemory smem,
                                         List<Memory> memories,
                                         long nodePosMask) {
        FromNode.FromMemory mem = ((FromNode.FromMemory) nodeMemories.getNodeMemory(tupleSource));
        memories.add(mem);
        mem.setSegmentMemory(smem);
        mem.setNodePosMaskBit(nodePosMask);
    }

    private boolean processBranchNode(ConditionalBranchNode tupleSource,
                                      SegmentMemory smem,
                                      List<Memory> memories) {
        ConditionalBranchNode.ConditionalBranchMemory branchMem = nodeMemories.getNodeMemory(tupleSource);
        memories.add(branchMem);
        branchMem.setSegmentMemory(smem);
        // nodes after a branch CE can notify, but they cannot impact linking
        return false;
    }

    private void processEvalNode(EvalConditionNode tupleSource,
                                 SegmentMemory smem,
                                 List<Memory> memories) {
        EvalConditionNode.EvalMemory evalMem = nodeMemories.getNodeMemory(tupleSource);
        memories.add(evalMem);
        evalMem.setSegmentMemory(smem);
    }

    private void processTimerNode(TimerNode tupleSource,
                                  SegmentMemory smem,
                                  List<Memory> memories,
                                  long nodePosMask) {
        TimerNode.TimerNodeMemory tnMem = nodeMemories.getNodeMemory(tupleSource);
        memories.add(tnMem);
        tnMem.setNodePosMaskBit(nodePosMask);
        tnMem.setSegmentMemory(smem);
    }

    private long processLiaNode(LeftInputAdapterNode tupleSource,
                                SegmentMemory smem,
                                List<Memory> memories,
                                long nodePosMask,
                                long allLinkedTestMask) {
        LeftInputAdapterNode.LiaNodeMemory liaMemory = nodeMemories.getNodeMemory(tupleSource);
        memories.add(liaMemory);
        liaMemory.setSegmentMemory(smem);
        liaMemory.setNodePosMaskBit(nodePosMask);
        allLinkedTestMask = allLinkedTestMask | nodePosMask;
        return allLinkedTestMask;
    }

    private long processBetaNode(BetaNode betaNode,
                                 SegmentMemory smem,
                                 List<Memory> memories,
                                 long nodePosMask,
                                 long allLinkedTestMask,
                                 boolean updateNodeBit) {
        BetaMemory bm;
        if (NodeTypeEnums.AccumulateNode == betaNode.getType()) {
            AccumulateNode.AccumulateMemory accMemory = ((AccumulateNode.AccumulateMemory) nodeMemories.getNodeMemory(
                    betaNode));
            memories.add(accMemory);
            accMemory.setSegmentMemory(smem);

            bm = accMemory.getBetaMemory();
        } else {
            bm = (BetaMemory) nodeMemories.getNodeMemory(betaNode);
            memories.add(bm);
        }

        bm.setSegmentMemory(smem);

        // this must be set first, to avoid recursion as sub networks can be initialised multiple ways
        // and bm.getSegmentMemory == null check can be used to avoid recursion.
        bm.setSegmentMemory(smem);

        if (betaNode.getRightInput().inputIsTupleToObjectNode()) {
            TupleToObjectNode tton = createSubnetworkSegmentMemory(betaNode);

            PathMemory subnetworkPathMemory = nodeMemories.getNodeMemory(tton);
            bm.setSubnetworkPathMemory((SubnetworkPathMemory) subnetworkPathMemory);
            if (updateNodeBit && canBeDisabled(betaNode) && subnetworkPathMemory.getAllLinkedMaskTest() > 0) {
                // only TupleToObjectNode's with reactive subnetworks can be disabled and thus need checking
                allLinkedTestMask = allLinkedTestMask | nodePosMask;
            }
        } else if (updateNodeBit && canBeDisabled(betaNode)) {
            allLinkedTestMask = allLinkedTestMask | nodePosMask;

        }
        bm.setNodePosMaskBit(nodePosMask);
        if (NodeTypeEnums.NotNode == betaNode.getType()) {
            // not nodes start up linked in
            smem.linkNodeWithoutRuleNotify(bm.getNodePosMaskBit());
        }
        return allLinkedTestMask;
    }

}
