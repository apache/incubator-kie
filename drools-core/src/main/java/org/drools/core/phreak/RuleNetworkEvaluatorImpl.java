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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.drools.base.common.NetworkNode;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.common.ActivationsManager;
import org.drools.core.common.Memory;
import org.drools.core.common.NodeMemories;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.SegmentMemorySupport;
import org.drools.core.common.TupleSets;
import org.drools.core.common.TupleSetsImpl;
import org.drools.core.reteoo.AbstractTerminalNode;
import org.drools.core.reteoo.AccumulateNode;
import org.drools.core.reteoo.AccumulateNode.AccumulateMemory;
import org.drools.core.reteoo.AsyncReceiveNode;
import org.drools.core.reteoo.AsyncReceiveNode.AsyncReceiveMemory;
import org.drools.core.reteoo.AsyncSendNode;
import org.drools.core.reteoo.AsyncSendNode.AsyncSendMemory;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.ConditionalBranchNode;
import org.drools.core.reteoo.ConditionalBranchNode.ConditionalBranchMemory;
import org.drools.core.reteoo.EvalConditionNode;
import org.drools.core.reteoo.EvalConditionNode.EvalMemory;
import org.drools.core.reteoo.ExistsNode;
import org.drools.core.reteoo.FromNode;
import org.drools.core.reteoo.FromNode.FromMemory;
import org.drools.core.reteoo.JoinNode;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.LeftTupleSinkNode;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.ObjectSink;
import org.drools.core.reteoo.PathEndNode;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.QueryElementNode;
import org.drools.core.reteoo.QueryElementNode.QueryElementNodeMemory;
import org.drools.core.reteoo.QueryTerminalNode;
import org.drools.core.reteoo.ReactiveFromNode;
import org.drools.core.reteoo.RightInputAdapterNode;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.core.reteoo.SubnetworkTuple;
import org.drools.core.reteoo.TimerNode;
import org.drools.core.reteoo.TimerNode.TimerNodeMemory;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.TupleFactory;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.TupleToObjectNode;
import org.drools.base.util.LinkedList;

import static org.drools.core.common.TupleSetsImpl.createLeftTupleTupleSets;

public class RuleNetworkEvaluatorImpl implements RuleNetworkEvaluator {

    
    private final PhreakJoinNode         pJoinNode;
    private final PhreakEvalNode         pEvalNode;
    private final PhreakFromNode         pFromNode;
    private final PhreakReactiveFromNode pReactiveFromNode;
    private final PhreakNotNode          pNotNode;
    private final PhreakExistsNode       pExistsNode;
    private final PhreakAccumulateNode   pAccNode;
    private final PhreakAccumulateNode   pGroupByNode;
    private final PhreakBranchNode       pBranchNode;
    private final PhreakQueryNode        pQueryNode;
    private final PhreakTimerNode        pTimerNode;
    private final PhreakAsyncSendNode    pSendNode;
    private final PhreakAsyncReceiveNode pReceiveNode;
    private final PhreakRuleTerminalNode pRtNode;
    private final PhreakQueryTerminalNode pQtNode;

    private static int cycle = 0;

    private NodeMemories nodeMemories;

    private SegmentMemorySupport segmentMemorySupport;

    public RuleNetworkEvaluatorImpl(ReteEvaluator reteEvaluator, NodeMemories nodeMemories, SegmentMemorySupport segmentMemorySupport) {
        this.nodeMemories = nodeMemories;
        this.segmentMemorySupport = segmentMemorySupport;
        pJoinNode   = PhreakNetworkNodeFactory.Factory.get().createPhreakJoinNode(reteEvaluator);
        pEvalNode   = PhreakNetworkNodeFactory.Factory.get().createPhreakEvalNode(reteEvaluator);
        pFromNode   = PhreakNetworkNodeFactory.Factory.get().createPhreakFromNode(reteEvaluator);
        pReactiveFromNode = PhreakNetworkNodeFactory.Factory.get().createPhreakReactiveFromNode(reteEvaluator);
        pNotNode    = PhreakNetworkNodeFactory.Factory.get().createPhreakNotNode(reteEvaluator);
        pExistsNode = PhreakNetworkNodeFactory.Factory.get().createPhreakExistsNode(reteEvaluator);
        pAccNode    = PhreakNetworkNodeFactory.Factory.get().createPhreakAccumulateNode(reteEvaluator);
        pGroupByNode = PhreakNetworkNodeFactory.Factory.get().createPhreakGroupByNode(reteEvaluator);
        pBranchNode = PhreakNetworkNodeFactory.Factory.get().createPhreakBranchNode(reteEvaluator);
        pQueryNode  = PhreakNetworkNodeFactory.Factory.get().createPhreakQueryNode(reteEvaluator);
        pTimerNode  = PhreakNetworkNodeFactory.Factory.get().createPhreakTimerNode(reteEvaluator);
        pSendNode   = PhreakNetworkNodeFactory.Factory.get().createPhreakAsyncSendNode(reteEvaluator);
        pReceiveNode = PhreakNetworkNodeFactory.Factory.get().createPhreakAsyncReceiveNode(reteEvaluator);
        pRtNode     = PhreakNetworkNodeFactory.Factory.get().createPhreakRuleTerminalNode(reteEvaluator);
        pQtNode     = PhreakNetworkNodeFactory.Factory.get().createPhreakQueryTerminalNode(reteEvaluator);
    }

    @Override
    public void evaluateNetwork(RuleExecutor executor,
                                PathMemory pmem) {
        evaluateNetwork(pmem.getActualActivationsManager(), executor, pmem);
    }

    @Override
    public void evaluateNetwork(ActivationsManager activationsManager,
                                RuleExecutor executor,
                                PathMemory pmem) {

        if (pmem.getSegmentMemories()[0] == null) {
            // if there's no first smem it's a pure alpha firing and then doesn't require any further evaluation
            return;
        }
        
        SegmentCursor sc = SegmentCursor.createSegmentCursor(pmem);

        
        sc.traceNetworkEvaluation();
        outerEval(activationsManager, executor, sc);
    }

    @Override
    public void evaluate(PathMemory pmem,
                          ActivationsManager activationsManager,
                          NetworkNode sink,
                          Memory tm,
                          TupleSets trgLeftTuples) {
        SegmentCursor segmentCursor = SegmentCursor.createSegmentCursor(pmem, sink, tm, trgLeftTuples);
        
        outerEval(activationsManager, pmem.getRuleAgendaItem().getRuleExecutor(), segmentCursor);
    }

    
    
    @Override
    public void forceFlushWhenSubnetwork(PathMemory pmem) {
        forceFlushPaths(findPathsToFlushFromSubnetwork(pmem));
    }
    
    @Override
    public void forceFlushPaths(Collection<PathMemory> pmems) {
        for (PathMemory outPmem : pmems) {
            forceFlushPath(outPmem);
        }
    }
    
    
    @Override
    public List<PathMemory> findPathsToFlushFromSubnetwork(PathMemory pmem) {
        List<PathMemory> paths = null;
        if (pmem.isDataDriven() && pmem.getNodeType() == NodeTypeEnums.TupleToObjectNode) {
            for (PathEndNode pnode : pmem.getPathEndNode().getPathEndNodes()) {
                if (NodeTypeEnums.isTerminalNode(pnode)) {
                    PathMemory outPmem = nodeMemories.getNodeMemory(pnode);
                    if (outPmem.isDataDriven()) {
                        if (paths == null) {
                            paths = new ArrayList<>();
                        }
                        paths.add(outPmem);
                    }
                }
            }
        }
        return paths == null ? Collections.emptyList() : paths;
    }
    
    
    @Override
    public void forceFlushPath(PathMemory outPmem) {
        SegmentMemory outSmem = outPmem.getSegmentMemories()[0];
        if (outSmem != null) {
            forceFlushLeftTuple(outPmem, outSmem, new TupleSetsImpl());
        }
    }
    
    
    @Override
    public void forceFlushLeftTuple(PathMemory pmem,
                                    SegmentMemory sm, 
                                    TupleSets leftTupleSets) {

        SegmentCursor segmentCursor = SegmentCursor.createSegmentCursor(pmem, sm, leftTupleSets);

        PathMemory rtnPmem = NodeTypeEnums.isTerminalNode(pmem.getPathEndNode()) ? pmem : nodeMemories.getNodeMemory(
                (AbstractTerminalNode) pmem.getPathEndNode().getPathEndNodes()[0]);

        ActivationsManager activationsManager = pmem.getActualActivationsManager();

        
        outerEval(activationsManager, rtnPmem.getOrCreateRuleAgendaItem().getRuleExecutor(), segmentCursor);
    }


    
    @Override
    public boolean flushLeftTupleIfNecessary(SegmentMemory sm, boolean streamMode) {
        return flushLeftTupleIfNecessary(sm, null, streamMode, Tuple.NONE);
    }

    @Override
    public boolean flushLeftTupleIfNecessary(SegmentMemory sm,
                                                    TupleImpl leftTuple,
                                                    boolean streamMode,
                                                    short stagedType) {
        PathMemory pmem = findPathToFlush(sm, leftTuple, streamMode);

        if (pmem == null) {
            return false;
        }

        forceFlushLeftTuple(pmem, sm, createLeftTupleTupleSets(leftTuple, stagedType));
        forceFlushWhenSubnetwork(pmem);
        return true;
    }

    public static PathMemory findPathToFlush(SegmentMemory sm, TupleImpl leftTuple, boolean streamMode) {
        boolean forceFlush = streamMode || (leftTuple != null && leftTuple.getFactHandle() != null && leftTuple
                .getFactHandle().isEvent());
        return forceFlush ? sm.getPathMemories().get(0) : sm.getFirstDataDrivenPathMemory();
    }
    
    private void outerEval(ActivationsManager activationsManager,
                           RuleExecutor executor,
                           SegmentCursor sc) {

         LinkedList<StackEntry> stack = new LinkedList<>();
         innerEval(activationsManager, executor, stack, sc, true);
         while (!stack.isEmpty()) {
             // eval
             StackEntry entry = stack.removeLast();
             evalStackEntry(activationsManager, executor, stack, entry);
         }
     }

    private void evalStackEntry(ActivationsManager activationsManager,
                               RuleExecutor executor,
                               LinkedList<StackEntry> stack,
                               StackEntry entry) {
        NetworkNode node = entry.getNode();
        Memory nodeMem = entry.getNodeMem();
        if (node.getType() == NodeTypeEnums.QueryElementNode) {
            // copy across the results, if any from the query node memory
            QueryElementNodeMemory qmem = (QueryElementNodeMemory) nodeMem;
            qmem.setNodeCleanWithoutNotify();
            qmem.getResultLeftTuples().addTo(entry.getTrgTuples());
        }

        SegmentCursor sc;
        if (entry.isResumeFromNextNode()) {
            
            if (node == entry.getSmems()[entry.getSmemIndex()].getTipNode()) {
                propagate(entry.getSmems()[entry.getSmemIndex()], entry.getTrgTuples());
            }

            sc = SegmentCursor.createForResume(entry);
        } else {
            sc = SegmentCursor.createForNonResume(entry);
        }
        sc.traceResumeFromStack();
        innerEval(activationsManager, executor, stack, sc, entry.isProcessSubnetwork());
    }

    private void innerEval(ActivationsManager activationsManager,
                           RuleExecutor executor,
                           LinkedList<StackEntry> stack,
                           SegmentCursor sc,
                           boolean processSubnetwork) {
        
        while (true) {
            sc.traceStartOfEvaluation(++cycle);

            if (!(NodeTypeEnums.isBetaNodeWithSubnetwork(sc.getCurrentNode()))) {
                // The engine cannot skip a TupleToObjectNode node, as the dirty might be several levels deep
                if (sc.isOnEmptyOrNonDirtySegment()) {
                    // empty sources and segment is not dirty, skip to non empty src tuples or dirty segment.
                    boolean foundDirty = moveToFirstDirtyOrNonEmptySegment(sc);
                    if (!foundDirty) {
                        break;
                    }
                }
                sc.traceMoveToDirtySegment(cycle);
            }

            if (sc.hasNoSourceTuples()) {
                sc.moveToNextAvailableSegment();
            }
            
            if (NodeTypeEnums.isEndNode(sc.getCurrentNode())) {
                evaluateEndNode(activationsManager, executor, stack, sc);
                break;
            }

            prepareStagedLeftTuples(sc);

            sc.setSourceTuples(evaluateNonTerminalNode(activationsManager, executor, stack, sc, processSubnetwork));
            if (sc.getSourceTuples() == null) {
                break; // Queries exists and has been placed StackEntry, and there are no current trgTuples to process
            }
            
            if (sc.isAtEndOfSegmentMemory()) {
                // end of SegmentMemory, so we know that stagedLeftTuples is not null
                sc.restoreStagedLeftTuples(); // must put back all the LTs
                propagate(sc.getCurrentSegment(), sc.getSourceTuples());
            }

            sc.moveToNextNodeOrSegment();
            processSubnetwork = true; //  make sure it's reset, so ria nodes are processed
        }

        if (sc.stagedLeftTuplesAreNotEmpty()) {
            sc.restoreStagedLeftTuples(); // must put back all the LTs
        }
    }


    private void evaluateEndNode(ActivationsManager activationsManager,
                          RuleExecutor executor,
                          LinkedList<StackEntry> stack,
                          SegmentCursor sc) {
        switch (sc.getCurrentNode().getType()) {
            case NodeTypeEnums.RuleTerminalNode:
                pRtNode.doNode(activationsManager, executor, (AbstractTerminalNode) sc.getCurrentNode(), sc.getSourceTuples());
                break;
            case NodeTypeEnums.QueryTerminalNode:
                pQtNode.doNode(stack, (QueryTerminalNode) sc.getCurrentNode(), sc.getSourceTuples());
                break;
            case NodeTypeEnums.TupleToObjectNode:
                doSubnetwork2((TupleToObjectNode) sc.getCurrentNode(), sc.getSourceTuples());
                break;
            default:
                // Nothing to do
        }
    }

    private boolean moveToFirstDirtyOrNonEmptySegment(SegmentCursor sc) {
        for (int i = sc.getSegmentMemoryIndex() + 1, length = sc.getSegmentMemorySize(); i < length; i++) {
            sc.traceSkipNonDirtySegment(i);

            // this is needed for subnetworks that feed into a parent network that has no right inputs,
            // and may not yet be initialized
            if (!NodeTypeEnums.isTerminalNode(sc.getCurrentSegment().getTipNode())) {
                segmentMemorySupport.initializeChildSegmentsIfNeeded(sc.getCurrentSegment());
            }
            
            sc.moveToSegment(i);
            if (sc.isDirtySegmentOrIsSubnetwork()) {
                // break if dirty or if we reach a subnetwork. It must break for subnetworks, so they can be searched.
                return true;
            }
        }
        return false;
    }

    private TupleSets evaluateNonTerminalNode(ActivationsManager activationsManager,
                              RuleExecutor executor,
                              LinkedList<StackEntry> stack,
                              SegmentCursor sc, 
                              boolean processSubnetwork) {
        if (NodeTypeEnums.isBetaNode(sc.getCurrentNode())) {
            return evaluateBetaNode(activationsManager, executor, stack, sc, processSubnetwork);
        } else {
            return evaluateNonBetaNonTerminalNode(activationsManager, executor, stack, sc);
                
        }
    }

    public TupleSets evaluateNonBetaNonTerminalNode(ActivationsManager activationsManager,
                                                    RuleExecutor executor,
                                                    LinkedList<StackEntry> stack,
                                                    SegmentCursor sc) {
        TupleSets trgTuples = new TupleSetsImpl();
        LeftTupleSinkNode sink = sc.getFirstLeftTupleSink();
        boolean exitInnerEval = false;
        switch (sc.getCurrentNode().getType()) {
            case NodeTypeEnums.EvalConditionNode: {
                pEvalNode.doNode((EvalConditionNode) sc.getCurrentNode(), (EvalMemory) sc.getCurrentNodeMemory(), sink,
                        sc.getSourceTuples(), sc.getStagedLeftTuples(), trgTuples);
                break;

            }
            case NodeTypeEnums.FromNode: {
                pFromNode.doNode((FromNode) sc.getCurrentNode(), (FromMemory) sc.getCurrentNodeMemory(), sink,
                        sc.getSourceTuples(), sc.getStagedLeftTuples(), trgTuples);
                break;
            }
            case NodeTypeEnums.ReactiveFromNode: {
                pReactiveFromNode.doNode((ReactiveFromNode) sc.getCurrentNode(), (ReactiveFromNode.ReactiveFromMemory) sc.getCurrentNodeMemory(), sink,
                                         sc.getSourceTuples(), sc.getStagedLeftTuples(), trgTuples);
                break;
            }
            case NodeTypeEnums.QueryElementNode: {
                exitInnerEval =  evaluateQueryNode(stack, sc, trgTuples);
                break;
            }
            case NodeTypeEnums.TimerConditionNode: {
                pTimerNode.doNode(activationsManager, sc, (TimerNode) sc.getCurrentNode(), (TimerNodeMemory) sc.getCurrentNodeMemory(), sink, sc.getSourceTuples(), sc.getStagedLeftTuples(), trgTuples);
                break;
            }
            case NodeTypeEnums.ConditionalBranchNode: {
                pBranchNode.doNode(activationsManager, executor, (ConditionalBranchNode) sc.getCurrentNode(),
                        (ConditionalBranchMemory) sc.getCurrentNodeMemory(), sink, sc.getSourceTuples(), sc.getStagedLeftTuples(), trgTuples);
                break;
            }
            case NodeTypeEnums.AsyncSendNode: {
                pSendNode.doNode((AsyncSendNode) sc.getCurrentNode(), (AsyncSendMemory) sc.getCurrentNodeMemory(), sc.getSourceTuples());
                break;
            }
            case NodeTypeEnums.AsyncReceiveNode: {
                pReceiveNode.doNode((AsyncReceiveNode) sc.getCurrentNode(), (AsyncReceiveMemory) sc.getCurrentNodeMemory(), sink, sc.getSourceTuples(), trgTuples);
                break;
            }
        }
        if (exitInnerEval && trgTuples.isEmpty()) {
            return null;
        } else {
            return trgTuples;
        }
    }

    private void prepareStagedLeftTuples(SegmentCursor sc) {
        if (sc.isAtEndOfSegmentMemory()) {
            // we are about to process the segment tip, allow it to merge insert/update/delete clashes
            segmentMemorySupport.initializeChildSegmentsIfNeeded(sc.getCurrentSegment());
            sc.saveStagedLeftTuples();
        } else {
            sc.resetStagedLeftTuples();
        }
    }

    private boolean evaluateQueryNode(LinkedList<StackEntry> stack,
                                      SegmentCursor sc1,
                                      TupleSets trgTuples) {
        QueryElementNodeMemory qmem = (QueryElementNodeMemory) sc1.getCurrentNodeMemory();

        if (sc1.getSourceTuples().isEmpty() && qmem.getResultLeftTuples().isEmpty()) {
            // no point in evaluating query element, and setting up stack, if there is nothing to process
            return false;
        }
        sc1.traceQueryResultTuples();

        // result tuples can happen when reactivity occurs inside of the query, prior to evaluation
        // we will need special behaviour to add the results again, when this query result resumes
        qmem.getResultLeftTuples().addTo(trgTuples);
        qmem.setNodeCleanWithoutNotify();

        if (sc1.getSourceTuples().isEmpty()) {
            return false;
        } 
        // only process the Query Node if there are src tuples
        StackEntry stackEntry = sc1.saveForResumeAfterQueryExecution(trgTuples);
        stack.add(stackEntry);

        pQueryNode.doNode(stackEntry, (QueryElementNode) sc1.getCurrentNode(), (QueryElementNodeMemory) sc1.getCurrentNodeMemory(),
                sc1.getSourceTuples(), sc1.getStagedLeftTuples(), trgTuples);

        SegmentMemory qsmem = ((QueryElementNodeMemory) sc1.getCurrentNodeMemory()).getQuerySegmentMemory();
        List<PathMemory> qpmems = qsmem.getPathMemories();

        // Build the evaluation information for each 'or' branch
        for (int i = 0; i < qpmems.size() ; i++) {
            PathMemory qpmem = qpmems.get(i);
            SegmentCursor scForOrQueries = SegmentCursor.createSegmentCursorForQueryExecution(qpmem);
            StackEntry stackEntryForQueryBranchEvaluation = scForOrQueries.saveForQueryBranchEvaluation();
            scForOrQueries.traceOrQueryBranchQueued(i);
            stack.add(stackEntryForQueryBranchEvaluation);
        }
        return true;

    }

    private TupleSets evaluateBetaNode(ActivationsManager activationsManager,
                                 RuleExecutor executor,
                                 LinkedList<StackEntry> stack,
                                 SegmentCursor sc,
                                 boolean processSubnetwork) {

        BetaMemory bm;
        AccumulateMemory am = null;
        if (NodeTypeEnums.AccumulateNode == sc.getCurrentNode().getType()) {
            am = (AccumulateMemory) sc.getCurrentNodeMemory();
            bm = am.getBetaMemory();
        } else {
            bm = (BetaMemory) sc.getCurrentNodeMemory();
        }

        if (processSubnetwork && ((BetaNode) sc.getCurrentNode()).getRightInput().inputIsTupleToObjectNode()) {
            // if the subnetwork is nested in this segment, it will create srcTuples containing
            // peer LeftTuples, suitable for the node in the main path.
            doSubnetwork(activationsManager, executor, stack, sc, bm);
            return null; // return here, TupleToObjectNode queues the evaluation on the stack, which is necessary to handled nested query nodes
        }
        return switchOnDoBetaNode(sc, bm, am);
    }

    private TupleSets switchOnDoBetaNode(SegmentCursor sc,
                                    BetaMemory bm,
                                    AccumulateMemory am) {
        TupleSets trgTuples = new TupleSetsImpl();
        sc.traceSwitchOnBetaNodes(bm);

        LeftTupleSinkNode sink = sc.getFirstLeftTupleSink();
        switch (sc.getCurrentNode().getType()) {
            case NodeTypeEnums.JoinNode: {
                pJoinNode.doNode((JoinNode) sc.getCurrentNode(), sink, bm,
                        sc.getSourceTuples(), sc.getStagedLeftTuples(), trgTuples);
                break;
            }
            case NodeTypeEnums.NotNode: {
                pNotNode.doNode((NotNode) sc.getCurrentNode(), sink, bm,
                        sc.getSourceTuples(), sc.getStagedLeftTuples(), trgTuples);
                break;
            }
            case NodeTypeEnums.ExistsNode: {
                pExistsNode.doNode((ExistsNode) sc.getCurrentNode(), sink, bm,
                        sc.getSourceTuples(), sc.getStagedLeftTuples(), trgTuples);
                break;
            }
            case NodeTypeEnums.AccumulateNode: {
                AccumulateNode accumulateNode = (AccumulateNode) sc.getCurrentNode();
                if (accumulateNode.getAccumulate().isGroupBy()) {
                    pGroupByNode.doNode(accumulateNode, sink, am, sc.getSourceTuples(), sc.getStagedLeftTuples(), trgTuples);
                } else {
                    pAccNode.doNode(accumulateNode, sink, am, sc.getSourceTuples(), sc.getStagedLeftTuples(), trgTuples);
                }
                break;
            }
        }
        return trgTuples;
    }

    private void doSubnetwork(ActivationsManager activationsManager,
                              RuleExecutor executor,
                              LinkedList<StackEntry> stack,
                              SegmentCursor sc,
                              BetaMemory bm) {


        StackEntry stackEntry = sc.saveForResumeAfterSubnetworkExecution();
        stack.add(stackEntry);
        sc.traceSubnetworkQueue();

        PathMemory pathMem = bm.getSubnetworkPathMemory();
        SegmentCursor scNew = SegmentCursor.createForSubNetwork(pathMem);
        
        innerEval(activationsManager, executor, stack, scNew, true);
    }

    private void doSubnetwork2(TupleToObjectNode tton,
                               TupleSets srcTuples) {

        ObjectSink[] sinks = tton.getObjectSinkPropagator().getSinks();

        BetaNode betaNode = ((RightInputAdapterNode)sinks[0]).getBetaNode();
        BetaMemory bm;
        Memory nodeMem = nodeMemories.getNodeMemory(betaNode);
        if (NodeTypeEnums.AccumulateNode == betaNode.getType()) {
            bm = ((AccumulateMemory) nodeMem).getBetaMemory();
        } else {
            bm = (BetaMemory) nodeMem;
        }
        TupleSets rightTuples = bm.getStagedRightTuples();

        // Build up iteration array for other sinks
        BetaNode[] bns = null;
        BetaMemory[] bms = null;
        int length = sinks.length;
        if (length > 1) {
            bns = new BetaNode[sinks.length - 1];
            bms = new BetaMemory[sinks.length - 1];
            for (int i = 1; i < length; i++) {
                bns[i - 1] = ((RightInputAdapterNode)sinks[i]).getBetaNode();
                Memory nodeMem2 = nodeMemories.getNodeMemory(bns[i - 1]);
                if (NodeTypeEnums.AccumulateNode == betaNode.getType()) {
                    bms[i - 1] = ((AccumulateMemory) nodeMem2).getBetaMemory();
                } else {
                    bms[i - 1] = (BetaMemory) nodeMem2;
                }
            }
        }

        length--; // subtract one, as first is not in the array;
        for (SubnetworkTuple subnetworkTuple = (SubnetworkTuple) srcTuples.getInsertFirst(); subnetworkTuple != null;) {
            SubnetworkTuple next = (SubnetworkTuple) subnetworkTuple.getStagedNext();

            if (bm.getStagedRightTuples().isEmpty()) {
                bm.setNodeDirtyWithoutNotify();
            }

            subnetworkTuple.prepareStagingOnRight();
            rightTuples.addInsert(subnetworkTuple);

            if (bns != null) {
                for (int i = 0; i < length; i++) {
                    if (bms[i].getStagedRightTuples().isEmpty()) {
                        bms[i].setNodeDirtyWithoutNotify();
                    }
                    subnetworkTuple = (SubnetworkTuple) TupleFactory.createPeer(tton,
                                                                                subnetworkTuple);
                    bms[i].getStagedRightTuples().addInsert(subnetworkTuple);
                }
            }
            subnetworkTuple = next;
        }

        for (SubnetworkTuple subnetworkTuple = (SubnetworkTuple) srcTuples.getDeleteFirst(); subnetworkTuple != null;) {
            SubnetworkTuple next = (SubnetworkTuple) subnetworkTuple.getStagedNext();

            if (rightTuples.isEmpty()) {
                bm.setNodeDirtyWithoutNotify();
            }

            switch (subnetworkTuple.getStagedTypeOnRight()) {
                // handle clash with already staged entries
                case Tuple.INSERT:
                    rightTuples.removeInsert(subnetworkTuple.moveStagingFromLeftToRight());
                    break;
                case Tuple.UPDATE:
                    rightTuples.removeUpdate(subnetworkTuple.moveStagingFromLeftToRight());
                    break;
            }

            subnetworkTuple.prepareStagingOnRight();
            rightTuples.addDelete(subnetworkTuple);

            if (bns != null) {
                for (int i = 0; i < length; i++) {
                    subnetworkTuple = (SubnetworkTuple) subnetworkTuple.getPeer();
                    if (bms[i].getStagedRightTuples().isEmpty()) {
                        bms[i].setNodeDirtyWithoutNotify();
                    }
                    bms[i].getStagedRightTuples().addDelete(subnetworkTuple);
                    subnetworkTuple.setStagedOnRight();
                }
            }
            subnetworkTuple = next;
        }

        for (SubnetworkTuple subnetworkTuple = (SubnetworkTuple) srcTuples.getUpdateFirst(); subnetworkTuple != null;) {
            SubnetworkTuple next = (SubnetworkTuple) subnetworkTuple.getStagedNext();

            if (rightTuples.isEmpty()) {
                bm.setNodeDirtyWithoutNotify();
            }

            subnetworkTuple.prepareStagingOnRight();
            rightTuples.addUpdate(subnetworkTuple);

            if (bns != null) {
                for (int i = 0; i < length; i++) {
                    subnetworkTuple = (SubnetworkTuple) subnetworkTuple.getPeer();

                    if (bms[i].getStagedRightTuples().isEmpty()) {
                        bms[i].setNodeDirtyWithoutNotify();
                    }
                    bms[i].getStagedRightTuples().addUpdate(subnetworkTuple);
                    subnetworkTuple.setStagedOnRight();
                }
            }
            subnetworkTuple = next;
        }
        srcTuples.resetAll();
    }
    
    @Override
    public void propagate(SegmentMemory sourceSegment, TupleSets leftTuples) {
        if (leftTuples.isEmpty()) {
            return;
        }
        
        segmentMemorySupport.initializeChildSegmentsIfNeeded(sourceSegment);
                
        processPeers(sourceSegment, leftTuples);

        Iterator<SegmentMemory> peersIterator = sourceSegment.getPeersWithDataDrivenPathMemoriesIterator();
        while (peersIterator.hasNext()) {
            SegmentMemory smem = peersIterator.next();
            for (PathMemory dataDrivenPmem : smem.getDataDrivenPathMemories()) {
                if (smem.getStagedLeftTuples().getDeleteFirst() == null &&
                    smem.getStagedLeftTuples().getUpdateFirst() == null &&
                    !dataDrivenPmem.isRuleLinked()) {
                    // skip flushing segments that have only inserts staged and the path is not linked
                    continue;
                }
                forceFlushLeftTuple(dataDrivenPmem, smem, smem.getStagedLeftTuples());
                forceFlushWhenSubnetwork(dataDrivenPmem);
            }
        }
    }

    private static void processPeers(SegmentMemory sourceSegment, TupleSets leftTuples) {
        SegmentMemory firstSmem = sourceSegment.getFirst();

        processPeerDeletes( leftTuples.getDeleteFirst(), firstSmem );
        processPeerDeletes( leftTuples.getNormalizedDeleteFirst(), firstSmem );
        processPeerUpdates( leftTuples, firstSmem );
        processPeerInserts( leftTuples, firstSmem );

        firstSmem.getStagedLeftTuples().addAll( leftTuples );
        leftTuples.resetAll();
    }

    private static void processPeerInserts(TupleSets leftTuples, SegmentMemory firstSmem) {
        for (TupleImpl leftTuple = leftTuples.getInsertFirst(); leftTuple != null; leftTuple =  leftTuple.getStagedNext()) {
            SegmentMemory smem = firstSmem.getNext();
            if ( smem != null ) {
                // It's possible for a deleted tuple and set of peers, to be cached on a delete (such as with accumulates).
                // So we should check if the instances already exist and use them if they do.
                if (leftTuple.getPeer() == null) {
                    TupleImpl peer = leftTuple;
                    // peers do not exist, so create and add them.
                    for (; smem != null; smem = smem.getNext()) {
                        LeftTupleSink sink = smem.getSinkFactory();
                        peer = TupleFactory.createPeer(sink, peer); // pctx is set during peer cloning
                        smem.getStagedLeftTuples().addInsert( peer );
                    }
                } else {
                    TupleImpl peer = leftTuple.getPeer();
                    // peers exist, so update them as an insert, which also handles staged clashing.
                    for (; smem != null; smem = smem.getNext()) {
                        peer.setPropagationContext( leftTuple.getPropagationContext() );
                        // ... and update the staged LeftTupleSets according to its current staged state
                        updateChildLeftTupleDuringInsert(peer, smem.getStagedLeftTuples(), smem.getStagedLeftTuples());
                        peer = peer.getPeer();
                    }
                }
            }
        }
    }

    private static void processPeerUpdates(TupleSets leftTuples, SegmentMemory firstSmem) {
        for (TupleImpl leftTuple = leftTuples.getUpdateFirst(); leftTuple != null; leftTuple = leftTuple.getStagedNext()) {
            SegmentMemory smem = firstSmem.getNext();
            if ( smem != null ) {
                for ( TupleImpl peer = leftTuple.getPeer(); peer != null; peer = peer.getPeer() ) {
                    // only stage, if not already staged, if insert, leave as insert
                    if ( peer.getStagedType() == LeftTuple.NONE ) {
                        peer.setPropagationContext( leftTuple.getPropagationContext() );
                        smem.getStagedLeftTuples().addUpdate( peer );
                    }

                    smem = smem.getNext();
                }
            }
        }
    }

    private static void updateChildLeftTupleDuringInsert(TupleImpl childLeftTuple, TupleSets stagedLeftTuples, TupleSets trgLeftTuples) {
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
                if (childLeftTuple.getSink().getType() == NodeTypeEnums.AccumulateNode ) {
                    trgLeftTuples.addInsert(childLeftTuple);
                } else {
                    trgLeftTuples.addUpdate(childLeftTuple);
                }
        }
    }

    private static void processPeerDeletes(TupleImpl leftTuple, SegmentMemory firstSmem) {
        for (; leftTuple != null; leftTuple = leftTuple.getStagedNext()) {
            SegmentMemory smem = firstSmem.getNext();
            if ( smem != null ) {
                for ( TupleImpl peer = leftTuple.getPeer(); peer != null; peer = peer.getPeer() ) {
                    peer.setPropagationContext( leftTuple.getPropagationContext() );
                    TupleSets stagedLeftTuples = smem.getStagedLeftTuples();
                    // if the peer is already staged as insert or update the LeftTupleSets will reconcile it internally
                    stagedLeftTuples.addDelete( peer );
                    smem = smem.getNext();
                }
            }
        }
    }


}
