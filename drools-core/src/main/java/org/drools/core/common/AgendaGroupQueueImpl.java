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
package org.drools.core.common;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

import org.drools.core.conflict.RuleAgendaConflictResolver;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.marshalling.MarshallerReaderContext;
import org.drools.base.phreak.PropagationEntry;
import org.drools.base.phreak.actions.AbstractPropagationEntry;
import org.drools.core.phreak.RuleAgendaItem;
import org.drools.core.util.ArrayQueue;
import org.drools.core.util.Queue;
import org.drools.core.util.QueueFactory;

/**
 * <code>AgendaGroup</code> implementation that uses a <code>PriorityQueue</code> to prioritise the evaluation of added
 * <code>ActivationQueue</code>s. The <code>AgendaGroup</code> also maintains a <code>Map</code> of <code>ActivationQueues</code>
 * for requested salience values.
 *
 * @see PriorityQueue
 */
public class AgendaGroupQueueImpl
        implements
        InternalAgendaGroup,
        InternalRuleFlowGroup {
    private static final long serialVersionUID = 510l;

    private static final RuleAgendaItem VISITED_AGENDA_GROUP = new RuleAgendaItem();
    private final    String             name;
    /**
     * Items in the agenda.
     */
    private          Queue<RuleAgendaItem> priorityQueue;
    private volatile boolean            active;
    private          PropagationContext autoFocusActivator;
    private          long               activatedForRecency;
    private          long               clearedForRecency;

    private ReteEvaluator reteEvaluator;
    private boolean               autoDeactivate = true;
    private Map<Object, String>   nodeInstances  = new ConcurrentHashMap<>();

    private volatile              boolean hasRuleFlowLister;

    private RuleAgendaItem        lastRemoved;
    private final boolean         sequential;

    public AgendaGroupQueueImpl(final String name,
                                final InternalRuleBase kBase) {
        this.name = name;
        this.sequential = kBase.getRuleBaseConfiguration().isSequential();

        this.clearedForRecency = -1;
    }

    @Override
    public void visited() {
        if (sequential) {
            lastRemoved = VISITED_AGENDA_GROUP;
        }
    }

    /* (non-Javadoc)
     * @see org.kie.spi.AgendaGroup#getName()
     */
    public String getName() {
        return this.name;
    }

    public void setReteEvaluator(ReteEvaluator reteEvaluator) {
        this.reteEvaluator = reteEvaluator;
        // workingMemory can be null during deserialization
        if (reteEvaluator != null && reteEvaluator.getRuleSessionConfiguration().isDirectFiring()) {
            this.priorityQueue = new ArrayQueue<>();
        } else {
            this.priorityQueue = QueueFactory.createQueue(RuleAgendaConflictResolver.INSTANCE);
        }
    }

    @Override
    public void hasRuleFlowListener(boolean hasRuleFlowLister) {
        this.hasRuleFlowLister = hasRuleFlowLister;
    }

    @Override
    public boolean isRuleFlowListener() {
        return hasRuleFlowLister;
    }

    public void clear() {
        reteEvaluator.addPropagation( new ClearAction( this.name ) );
    }

    public class ClearAction extends AbstractPropagationEntry<ReteEvaluator> {

        private final String name;

        public ClearAction( String name ) {
            this.name = name;
        }

        @Override
        public void internalExecute(ReteEvaluator reteEvaluator ) {
            ((InternalAgenda) reteEvaluator.getActivationsManager()).clearAndCancelAgendaGroup(this.name);
        }
    }

    public void setFocus() {
        reteEvaluator.addPropagation( new SetFocusAction( this.name ) );
    }

    public class SetFocusAction extends AbstractPropagationEntry<ReteEvaluator> {

        private final String name;

        public SetFocusAction( String name ) {
            this.name = name;
        }

        @Override
        public void internalExecute(ReteEvaluator reteEvaluator ) {
            if ( ((InternalAgenda) reteEvaluator.getActivationsManager()).setFocus(this.name) ) {
                reteEvaluator.getActivationsManager().haltGroupEvaluation();
            }
        }

        @Override
        public boolean defersExpiration() {
            return true;
        }
    }

    public void reset() {
        this.priorityQueue.clear();
        this.lastRemoved = null;
    }

    public int size() {
        return this.priorityQueue.size();
    }

    public void add(final RuleAgendaItem activation) {
        if ( lastRemoved != null ) {
            // this will only be set if sequential. Do not add Match's that are higher in salience + load order than the lastRemoved (fired)
            if ( lastRemoved == activation || lastRemoved == VISITED_AGENDA_GROUP || RuleAgendaConflictResolver.doCompare(lastRemoved, activation) < 0 ) {
                return;
            }
        }
        this.priorityQueue.enqueue( activation);
    }

    public RuleAgendaItem remove() {
        RuleAgendaItem match = this.priorityQueue.dequeue();
        if ( sequential ) {
            lastRemoved = match;
        }
        return match;
    }

    public RuleAgendaItem peek() {
        return this.priorityQueue.peek();
    }

    public boolean isActive() {
        return this.active;
    }

    public boolean isAutoDeactivate() {
        return autoDeactivate;
    }

    public void setAutoDeactivate(boolean autoDeactivate) {
        this.autoDeactivate = autoDeactivate;
    }

    public void addNodeInstance(Object processInstanceId,
                                String nodeInstanceId) {
        nodeInstances.put( processInstanceId,
                           nodeInstanceId );
    }

    public void removeNodeInstance(Object processInstanceId,
                                   String nodeInstanceId) {
        nodeInstances.put( processInstanceId,
                           nodeInstanceId );
    }

    public void setActive(final boolean activate) {
        this.active = activate;
    }

    public PropagationContext getAutoFocusActivator() {
        return this.autoFocusActivator;
    }

    public void setAutoFocusActivator(PropagationContext autoFocusActivator) {
        this.autoFocusActivator = autoFocusActivator;
    }

    public boolean isEmpty() {
        return this.priorityQueue.isEmpty();
    }

    public Collection<RuleAgendaItem> getActivations() {
        return this.priorityQueue.getAll();
    }

    @Override
    public Map<Object, String> getNodeInstances() {
        return nodeInstances;
    }

    public String toString() {
        return "AgendaGroup '" + this.name + "'";
    }

    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        return ((AgendaGroupQueueImpl) object).name.equals( this.name );
    }

    public int hashCode() {
        return this.name.hashCode();
    }

    public void remove(final RuleAgendaItem activation) {
        this.priorityQueue.dequeue(activation);
    }

    public long getActivatedForRecency() {
        return this.activatedForRecency;
    }

    public void setActivatedForRecency(long recency) {
        this.activatedForRecency = recency;
    }

    public long getClearedForRecency() {
        return this.clearedForRecency;
    }

    public void setClearedForRecency(long recency) {
        this.clearedForRecency = recency;
    }

    public static class DeactivateCallback
            extends AbstractPropagationEntry<ReteEvaluator>
            implements WorkingMemoryAction {

        private static final long     serialVersionUID = 510l;

        protected final InternalRuleFlowGroup ruleFlowGroup;

        public DeactivateCallback(InternalRuleFlowGroup ruleFlowGroup) {
            this.ruleFlowGroup = ruleFlowGroup;
        }

        public DeactivateCallback(MarshallerReaderContext context) throws IOException {
            this.ruleFlowGroup = (InternalRuleFlowGroup) context.getWorkingMemory().getAgenda().getRuleFlowGroup( context.readUTF() );
        }

        public void internalExecute(ReteEvaluator reteEvaluator) {
            // check whether ruleflow group is still empty first
            if ( this.ruleFlowGroup.isEmpty() ) {
                // deactivate ruleflow group
                this.ruleFlowGroup.setActive( false );
            }
        }
    }

    public boolean isSequential() {
        return sequential;
    }

}
