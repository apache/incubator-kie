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

import java.io.Serializable;

import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.common.ActivationsFilter;
import org.drools.core.common.ActivationsManager;
import org.drools.core.common.InternalAgendaGroup;
import org.drools.core.common.Memory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.phreak.RuleAgendaItem;
import org.drools.base.util.AbstractLinkedListNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PathMemory extends AbstractLinkedListNode<Memory>
                        implements
                        Serializable, Memory {

    protected static final Logger log = LoggerFactory.getLogger(PathMemory.class);
    protected static final boolean isLogTraceEnabled = log.isTraceEnabled();

    private long linkedSegmentMask;
    private long allLinkedMaskTest;
    private final PathEndNode pathEndNode;
    private RuleAgendaItem agendaItem;
    private SegmentMemory[] segmentMemories;
    private SegmentMemory segmentMemory;

    public final boolean dataDriven;

    private ActivationsManager activationsManager;

    public PathMemory(PathEndNode pathEndNode, ReteEvaluator reteEvaluator) {
        this.pathEndNode = pathEndNode;
        this.linkedSegmentMask = 0L;
        this.dataDriven = initDataDriven(reteEvaluator);
        this.activationsManager = reteEvaluator.getActivationsManager().getPartitionedAgendaForNode(getPathEndNode());
    }

    protected boolean initDataDriven(ReteEvaluator reteEvaluator) {
        return isRuleDataDriven(reteEvaluator, getRule());
    }

    protected boolean isRuleDataDriven(ReteEvaluator reteEvaluator, RuleImpl rule) {
        return rule != null &&
               (rule.isDataDriven() || (reteEvaluator != null && reteEvaluator.getRuleSessionConfiguration()
                       .getForceEagerActivationFilter().accept(rule)));
    }

    public PathEndNode getPathEndNode() {
        return pathEndNode;
    }

    public RuleImpl getRule() {
        return NodeTypeEnums.isTerminalNode(pathEndNode) ? ((TerminalNode) pathEndNode).getRule() : null;
    }

    public RuleAgendaItem getRuleAgendaItem() {
        return agendaItem;
    }

    public long getLinkedSegmentMask() {
        return linkedSegmentMask;
    }

    public void setLinkedSegmentMask(long mask) {
        this.linkedSegmentMask = mask;
    }

    public long getAllLinkedMaskTest() {
        return allLinkedMaskTest;
    }

    public void setAllLinkedMaskTest(long allLinkedTestMask) {
        this.allLinkedMaskTest = allLinkedTestMask;
    }

    public void linkSegmentWithoutRuleNotify(long mask) {
        linkedSegmentMask |= mask;
    }

    public void linkSegment(long mask) {
        linkedSegmentMask |= mask;
        if (isLogTraceEnabled) {
            if (NodeTypeEnums.isTerminalNode(getPathEndNode())) {
                TerminalNode rtn = (TerminalNode) getPathEndNode();
                log.trace("  LinkSegment smask={} rmask={} name={}", mask, linkedSegmentMask, rtn.getRule().getName());
            } else {
                log.trace("  LinkSegment smask={} rmask={}", mask, "TupleToObjectNode");
            }
        }
        if (isRuleLinked()) {
            doLinkRule();
        }
    }

    public RuleAgendaItem getOrCreateRuleAgendaItem() {
        ensureAgendaItemCreated();
        return agendaItem;
    }

    private TerminalNode ensureAgendaItemCreated() {
        AbstractTerminalNode rtn = (AbstractTerminalNode) getPathEndNode();
        if (agendaItem == null) {
            int salience = rtn.getRule().getSalience().isDynamic() ? 0 : rtn.getRule().getSalience().getValue();
            agendaItem = activationsManager.createRuleAgendaItem(salience, this, rtn);
        }
        return rtn;
    }

    public void doLinkRule() {
        TerminalNode rtn = ensureAgendaItemCreated();
        if (isLogTraceEnabled) {
            log.trace(" LinkRule name={}", rtn.getRule().getName());
        }

        queueRuleAgendaItem();
    }

    public void doUnlinkRule() {
        TerminalNode rtn = ensureAgendaItemCreated();
        if (isLogTraceEnabled) {
            log.trace("    UnlinkRule name={}", rtn.getRule().getName());
        }

        agendaItem.getRuleExecutor().setDirty(true);
        if (!agendaItem.isQueued()) {
            if (isLogTraceEnabled) {
                log.trace("Queue RuleAgendaItem {}", agendaItem);
            }
            InternalAgendaGroup ag = agendaItem.getAgendaGroup();
            ag.add(agendaItem);
        }
    }

    public void queueRuleAgendaItem() {
        agendaItem.getRuleExecutor().setDirty(true);

        ActivationsFilter activationFilter = activationsManager.getActivationsFilter();
        if (activationFilter != null) {
            activationFilter.accept(agendaItem);
        }

        if (!agendaItem.isQueued()) {
            if (isLogTraceEnabled) {
                log.trace("Queue RuleAgendaItem {}", agendaItem);
            }
            InternalAgendaGroup ag = agendaItem.getAgendaGroup();
            ag.add(agendaItem);
        }

        if (agendaItem.getRule().isQuery()) {
            activationsManager.addQueryAgendaItem(agendaItem);
        } else if (agendaItem.getRule().isEager()) {
            activationsManager.addEagerRuleAgendaItem(agendaItem);
        }
    }

    public void unlinkedSegment(long mask) {
        boolean linkedRule = isRuleLinked();
        linkedSegmentMask &= ~mask;
        if (isLogTraceEnabled) {
            log.trace("  UnlinkSegment smask={} rmask={} name={}", mask, linkedSegmentMask, this);
        }
        if (linkedRule && !isRuleLinked()) {
            doUnlinkRule();
        }
    }

    public boolean isRuleLinked() {
        return (linkedSegmentMask & allLinkedMaskTest) == allLinkedMaskTest;
    }

    public boolean isDataDriven() {
        return dataDriven;
    }

    public int getNodeType() {
        return NodeTypeEnums.RuleTerminalNode;
    }

    public boolean isInitialized() {
        return agendaItem != null && segmentMemories != null;
    }

    public SegmentMemory[] getSegmentMemories() {
        return segmentMemories;
    }

    public void setSegmentMemory(int index, SegmentMemory sm) {
        this.segmentMemories[index] = sm;
    }

    public void setSegmentMemories(SegmentMemory[] segmentMemories) {
        this.segmentMemories = segmentMemories;
    }

    public SegmentMemory getSegmentMemory() {
        return this.segmentMemory;
    }

    public void setSegmentMemory(SegmentMemory sm) {
        this.segmentMemory = sm;
    }

    public String toString() {
        return "PathEnd(" + getPathEndNode().getId() + ") [" + getRule().getName() + "]";
    }

    public void reset() {
        this.linkedSegmentMask = 0L;
        // TODO we could reset the agandaItem instead of throwing it away
        this.agendaItem = null;
    }

    public ActivationsManager getActualActivationsManager() {
        return activationsManager;
    }
    
    public void addSegmentToPathMemory(SegmentMemory smem) {
        if (smem.getRootNode().getPathIndex() >= getPathEndNode().getStartTupleSource().getPathIndex()) {
            smem.addPathMemory(this);
            setSegmentMemory(smem.getPos(), smem);
        }

    }
}
