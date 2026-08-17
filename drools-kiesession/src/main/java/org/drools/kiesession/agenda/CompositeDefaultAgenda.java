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
package org.drools.kiesession.agenda;

import org.drools.base.common.NetworkNode;
import org.drools.base.common.PartitionsManager;
import org.drools.core.common.ActivationsFilter;
import org.drools.core.common.AgendaGroupsManager;
import org.drools.core.common.InternalActivationGroup;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalAgendaGroup;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.RuleFlowGroup;
import org.drools.core.event.AgendaEventSupport;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.phreak.ExecutableEntry;
import org.drools.base.phreak.PropagationEntry;
import org.drools.core.phreak.PropagationList;
import org.drools.core.phreak.RuleAgendaItem;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.RuleTerminalNodeLeftTuple;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.rule.accessor.FactHandleFactory;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.core.rule.consequence.KnowledgeHelper;
import org.drools.core.util.CompositeIterator;
import org.kie.api.runtime.rule.AgendaFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.runAsync;
import static org.drools.base.common.PartitionsManager.doOnForkJoinPool;

public class CompositeDefaultAgenda implements Externalizable, InternalAgenda {

    protected static final Logger log = LoggerFactory.getLogger( CompositeDefaultAgenda.class );

    private final DefaultAgenda.ExecutionStateMachine executionStateMachine = new DefaultAgenda.ConcurrentExecutionStateMachine();

    private DefaultAgenda[] agendas;

    private PropagationList propagationList;

    public CompositeDefaultAgenda() { }

    public CompositeDefaultAgenda(InternalRuleBase kieBase, InternalWorkingMemory workingMemory, FactHandleFactory factHandleFactory) {
        this.agendas = new DefaultAgenda[kieBase.getParallelEvaluationSlotsCount()];
        for ( int i = 0; i < this.agendas.length; i++ ) {
            agendas[i] = new PartitionedDefaultAgenda(kieBase, workingMemory, factHandleFactory, executionStateMachine, i);
        }
        // this composite agenda and the first partitioned one share the same propagation list
        this.propagationList = agendas[0].getPropagationList();
    }

    @Override
    public void writeExternal( ObjectOutput out ) throws IOException {
        out.writeObject( agendas );
    }

    @Override
    public void readExternal( ObjectInput in ) throws IOException, ClassNotFoundException {
        agendas = (DefaultAgenda[]) in.readObject();
    }

    @Override
    public DefaultAgenda getPartitionedAgenda(int partitionNr) {
        return agendas[partitionNr];
    }

    @Override
    public DefaultAgenda getPartitionedAgendaForNode(NetworkNode node) {
        return getPartitionedAgenda( node.getPartitionId().getParallelEvaluationSlot() );
    }

    @Override
    public InternalWorkingMemory getWorkingMemory() {
        return agendas[0].getWorkingMemory();
    }

    @Override
    public AgendaGroupsManager getAgendaGroupsManager() {
        return agendas[0].getAgendaGroupsManager();
    }

    @Override
    public AgendaEventSupport getAgendaEventSupport() {
        return agendas[0].getAgendaEventSupport();
    }

    @Override
    public RuleAgendaItem peekNextRule() {
        return getAgendaGroupsManager().peekNextRule();
    }

    @Override
    public int fireAllRules( AgendaFilter agendaFilter, int fireLimit ) {
        if (!executionStateMachine.toFireAllRules()) {
            return 0;
        }

        if ( log.isTraceEnabled() ) {
            log.trace("Starting Fire All Rules");
        }

        int fireCount = 0;

        try {
            int iterationFireCount = parallelFire( agendaFilter, fireLimit );
            fireCount += iterationFireCount;
            boolean limitReached = ( fireLimit > 0 && fireCount >= fireLimit );

            while ( iterationFireCount > 0 && !limitReached && hasPendingPropagations() ) {
                iterationFireCount = parallelFire( agendaFilter, fireLimit - fireCount );
                fireCount += iterationFireCount;
                limitReached = ( fireLimit > 0 && fireCount >= fireLimit );
            }
        } finally {
            executionStateMachine.immediateHalt(propagationList);
        }

        if ( log.isTraceEnabled() ) {
            log.trace("Ending Fire All Rules");
        }

        return fireCount;
    }

    private int parallelFire( AgendaFilter agendaFilter, int fireLimit ) {
        return doOnForkJoinPool(() -> Stream.of(agendas).parallel().mapToInt(a -> a.internalFireAllRules( agendaFilter, fireLimit, false )).sum() );
    }

    @Override
    public RuleAgendaItem createRuleAgendaItem( int salience, PathMemory rs, TerminalNode rtn ) {
        return getPartitionedAgendaForNode(rtn).createRuleAgendaItem( salience, rs, rtn );
    }

    @Override
    public InternalMatch createAgendaItem(RuleTerminalNodeLeftTuple rtnLeftTuple, int salience, PropagationContext context, RuleAgendaItem ruleAgendaItem, InternalAgendaGroup agendaGroup) {
        return getPartitionedAgendaForNode(ruleAgendaItem.getTerminalNode()).createAgendaItem( rtnLeftTuple, salience, context, ruleAgendaItem, agendaGroup );
    }

    @Override
    public void fireUntilHalt() {
        fireUntilHalt( null );
    }

    @Override
    public void fireUntilHalt( AgendaFilter agendaFilter ) {
        if ( log.isTraceEnabled() ) {
            log.trace("Starting Fire Until Halt");
        }

        ExecutorService fireUntilHaltExecutor = PartitionsManager.borrowFireUntilHaltExecutors();
        if (executionStateMachine.toFireUntilHalt()) {
            try {
                while ( isFiring() ) {
                    CompletableFuture<Void>[] futures = new CompletableFuture[agendas.length];
                    for ( int i = 0; i < futures.length; i++ ) {
                        final int j = i;
                        futures[j] = runAsync( () -> agendas[j].internalFireUntilHalt( agendaFilter, false ), fireUntilHaltExecutor );
                    }
                    CompletableFuture.allOf(futures).join();
                }
            } finally {
                executionStateMachine.immediateHalt( propagationList );
                PartitionsManager.offerFireUntilHaltExecutors(fireUntilHaltExecutor);
            }
        }

        if ( log.isTraceEnabled() ) {
            log.trace("Ending Fire Until Halt");
        }
    }

    @Override
    public boolean dispose(InternalWorkingMemory wm) {
        for ( int i = 0; i < agendas.length; i++ ) {
            agendas[i].getPropagationList().dispose();
        }
        return executionStateMachine.dispose( wm );
    }

    @Override
    public boolean isAlive() {
        return executionStateMachine.isAlive();
    }

    @Override
    public void halt() {
        if ( isFiring() ) {
            propagationList.addEntry(new CompositeHalt( executionStateMachine, this ) );
        }
    }

    static class CompositeHalt extends DefaultAgenda.Halt {

        private final CompositeDefaultAgenda compositeAgenda;

        protected CompositeHalt( DefaultAgenda.ExecutionStateMachine executionStateMachine, CompositeDefaultAgenda compositeAgenda ) {
            super( executionStateMachine );
            this.compositeAgenda = compositeAgenda;
        }

        @Override
        public void internalExecute(ReteEvaluator reteEvaluator ) {
            super.internalExecute( reteEvaluator );
            compositeAgenda.notifyWaitOnRest();
        }
    }

    @Override
    public boolean isFiring() {
        return executionStateMachine.isFiring();
    }

    @Override
    public void addPropagation( PropagationEntry<ReteEvaluator> propagationEntry ) {
        if (propagationEntry.isPartitionSplittable()) {
            for ( int i = 0; i < agendas.length; i++ ) {
                agendas[i].addPropagation( propagationEntry.getSplitForPartition( i ) );
            }
        } else {
            propagationList.addEntry( propagationEntry );
        }
    }

    @Override
    public void flushPropagations() {
        for ( int i = 0; i < agendas.length; i++ ) {
            agendas[i].flushPropagations();
        }
    }

    @Override
    public void notifyWaitOnRest() {
        for ( int i = 0; i < agendas.length; i++ ) {
            agendas[i].notifyWaitOnRest();
        }
    }

    @Override
    public void haltGroupEvaluation() {
        for ( int i = 0; i < agendas.length; i++ ) {
            agendas[i].haltGroupEvaluation();
        }
    }

    @Override
    public Iterator<PropagationEntry<ReteEvaluator>> getActionsIterator() {
        return new CompositeIterator<>( Stream.of( agendas ).map( DefaultAgenda::getActionsIterator ).toArray(Iterator[]::new) );
    }

    @Override
    public boolean hasPendingPropagations() {
        for ( int i = 0; i < agendas.length; i++ ) {
            if (agendas[i].hasPendingPropagations()) {
                return true;
            }
        }
        return false;
    }

    public void handleException(InternalMatch internalMatch, Exception e) {
        agendas[0].handleException(internalMatch, e);
    }

    @Override
    public void clear() {
        for ( int i = 0; i < agendas.length; i++ ) {
            agendas[i].clear();
        }
    }

    @Override
    public void reset() {
        for ( int i = 0; i < agendas.length; i++ ) {
            agendas[i].reset();
        }
    }

    @Override
    public void executeTask( ExecutableEntry executable ) {
        agendas[0].executeTask( executable );
    }

    @Override
    public void executeFlush() {
        if (!executionStateMachine.toExecuteTaskState()) {
            return;
        }

        try {
            for ( int i = 0; i < agendas.length; i++ ) {
                agendas[i].flushPropagations();
            }
        } finally {
            executionStateMachine.immediateHalt(propagationList);
        }
    }

    @Override
    public void activate() {
        agendas[0].activate();
    }

    @Override
    public void deactivate() {
        agendas[0].deactivate();
    }

    @Override
    public boolean tryDeactivate() {
        return agendas[0].tryDeactivate();
    }

    @Override
    public void activateRuleFlowGroup( String name ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.activateRuleFlowGroup -> TODO" );
    }

    @Override
    public void activateRuleFlowGroup( String name, String processInstanceId, String nodeInstanceId ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.activateRuleFlowGroup -> TODO" );
    }

    @Override
    public void clearAndCancel() {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.clearAndCancel -> TODO" );
    }

    @Override
    public void clearAndCancelAgendaGroup( String name ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.clearAndCancelAgendaGroup -> TODO" );
    }

    @Override
    public void clearAndCancelActivationGroup( String name ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.clearAndCancelActivationGroup -> TODO" );
    }

    @Override
    public void clearAndCancelActivationGroup( InternalActivationGroup activationGroup ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.clearAndCancelActivationGroup -> TODO" );
    }

    @Override
    public void clearAndCancelRuleFlowGroup( String name ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.clearAndCancelRuleFlowGroup -> TODO" );
    }

    @Override
    public String getFocusName() {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.getFocusName -> TODO" );
    }

    @Override
    public void cancelActivation( InternalMatch internalMatch) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.cancelActivation -> TODO" );
    }

    @Override
    public boolean isDeclarativeAgenda() {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.isDeclarativeAgenda -> TODO" );
    }

    @Override
    public InternalAgendaGroup getAgendaGroup( String name ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.getAgendaGroup -> TODO" );
    }

    @Override
    public boolean setFocus( String name ) {
        boolean groupChanged = false;
        for ( int i = 0; i < agendas.length; i++ ) {
            groupChanged |= agendas[i].setFocus( name );
        }
        return groupChanged;
    }

    @Override
    public InternalActivationGroup getActivationGroup( String name ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.getActivationGroup -> TODO" );
    }

    @Override
    public RuleFlowGroup getRuleFlowGroup( String name ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.getRuleFlowGroup -> TODO" );
    }

    @Override
    public void setActivationsFilter( ActivationsFilter filter ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.setActivationsFilter -> TODO" );
    }

    @Override
    public ActivationsFilter getActivationsFilter() {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.getActivationsFilter -> TODO" );
    }

    @Override
    public void addEagerRuleAgendaItem( RuleAgendaItem item ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.addEagerRuleAgendaItem -> TODO" );
    }

    @Override
    public void removeEagerRuleAgendaItem( RuleAgendaItem item ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.removeEagerRuleAgendaItem -> TODO" );
    }

    @Override
    public void addQueryAgendaItem( RuleAgendaItem item ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.addQueryAgendaItem -> TODO" );
    }

    @Override
    public void removeQueryAgendaItem( RuleAgendaItem item ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.removeQueryAgendaItem -> TODO" );
    }

    @Override
    public void stageLeftTuple( RuleAgendaItem ruleAgendaItem, InternalMatch justified) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.stageLeftTuple -> TODO" );
    }

    @Override
    public void evaluateEagerList() {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.evaluateEagerList -> TODO" );
    }

    @Override
    public void evaluateQueriesForRule(RuleAgendaItem item) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, InternalActivationGroup> getActivationGroupsMap() {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.getActivationGroupsMap -> TODO" );
    }

    @Override
    public int sizeOfRuleFlowGroup( String s ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.sizeOfRuleFlowGroup -> TODO" );
    }

    @Override
    public void addItemToActivationGroup( InternalMatch item) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.addItemToActivationGroup -> TODO" );
    }

    @Override
    public boolean isRuleActiveInRuleFlowGroup( String ruleflowGroupName, String ruleName, String processInstanceId ) {
        throw new UnsupportedOperationException( "org.drools.core.common.CompositeDefaultAgenda.isRuleActiveInRuleFlowGroup -> TODO" );
    }

    @Override
    public void registerExpiration( PropagationContext expirationContext ) {
        throw new UnsupportedOperationException( "This method has to be called on the single partitioned agendas" );
    }

    @Override
    public KnowledgeHelper getKnowledgeHelper() {
        throw new UnsupportedOperationException( "This method has to be called on the single partitioned agendas" );
    }

    @Override
    public void resetKnowledgeHelper() {
        throw new UnsupportedOperationException( "This method has to be called on the single partitioned agendas" );
    }

    @Override
    public boolean isParallelAgenda() {
        return true;
    }
}
