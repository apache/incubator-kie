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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.drools.base.definitions.rule.impl.QueryImpl;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.common.ActivationGroupImpl;
import org.drools.core.common.ActivationGroupNode;
import org.drools.core.common.ActivationsFilter;
import org.drools.core.common.AgendaGroupsManager;
import org.drools.core.common.EventSupport;
import org.drools.core.common.InternalActivationGroup;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalAgendaGroup;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalRuleFlowGroup;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.InternalWorkingMemoryEntryPoint;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.RuleFlowGroup;
import org.drools.core.concurrent.GroupEvaluator;
import org.drools.core.concurrent.ParallelGroupEvaluator;
import org.drools.core.concurrent.SequentialGroupEvaluator;
import org.drools.core.event.AgendaEventSupport;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.phreak.ExecutableEntry;
import org.drools.core.phreak.PropagationEntry;
import org.drools.core.phreak.PropagationList;
import org.drools.core.phreak.RuleAgendaItem;
import org.drools.core.phreak.RuleExecutor;
import org.drools.core.phreak.SynchronizedBypassPropagationList;
import org.drools.core.phreak.SynchronizedPropagationList;
import org.drools.core.phreak.ThreadUnsafePropagationList;
import org.drools.core.reteoo.AgendaComponentFactory;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.RuleTerminalNodeLeftTuple;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.rule.accessor.FactHandleFactory;
import org.drools.core.rule.consequence.ConsequenceExceptionHandler;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.core.rule.consequence.KnowledgeHelper;
import org.drools.core.util.index.TupleList;
import org.drools.util.StringUtils;
import org.drools.wiring.api.ComponentsFactory;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.event.rule.MatchCancelledCause;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.AgendaGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rule-firing Agenda.
 * 
 * <p>
 * Since many rules may be matched by a single assertObject(...) all scheduled
 * actions are placed into the <code>Agenda</code>.
 * </p>
 * 
 * <p>
 * While processing a scheduled action, it may update or retract objects in
 * other scheduled actions, which must then be removed from the agenda.
 * Non-invalidated actions are left on the agenda, and are executed in turn.
 * </p>
 */
public class DefaultAgenda implements InternalAgenda {

    protected static final Logger log = LoggerFactory.getLogger( DefaultAgenda.class );

    private static final long serialVersionUID = 510l;

    protected final InternalWorkingMemory workingMemory;

    private final Map<String, InternalActivationGroup> activationGroups;

    private final org.drools.base.util.LinkedList<RuleAgendaItem> eager = new org.drools.base.util.LinkedList<>();

    private final Map<QueryImpl, RuleAgendaItem> queries = new ConcurrentHashMap<>();

    private ConsequenceExceptionHandler legacyConsequenceExceptionHandler;

    private org.kie.api.runtime.rule.ConsequenceExceptionHandler consequenceExceptionHandler;

    protected int activationCounter;

    private final boolean declarativeAgenda;
    private final boolean sequential;

    private ActivationsFilter activationsFilter;

    private final List<PropagationContext> expirationContexts;

    private final GroupEvaluator groupEvaluator;

    private final PropagationList propagationList;

    private final ExecutionStateMachine executionStateMachine;

    private final AgendaGroupsManager agendaGroupsManager;

	private FactHandleFactory factHandleFactory;

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------

    public DefaultAgenda(InternalRuleBase kieBase, InternalWorkingMemory workingMemory, FactHandleFactory factHandleFactory) {
        this(kieBase, workingMemory, factHandleFactory, null);
    }

    DefaultAgenda(InternalRuleBase kieBase, InternalWorkingMemory workingMemory, FactHandleFactory factHandleFactory, ExecutionStateMachine executionStateMachine) {
        this.workingMemory = workingMemory;
		this.factHandleFactory = factHandleFactory;
        this.agendaGroupsManager = AgendaGroupsManager.create(kieBase, workingMemory, factHandleFactory);
        this.activationGroups = new HashMap<>();

        if (executionStateMachine != null) {
            this.executionStateMachine = executionStateMachine;
        } else {
            this.executionStateMachine = workingMemory.getRuleSessionConfiguration().isThreadSafe() ?
                    new ConcurrentExecutionStateMachine() :
                    new UnsafeExecutionStateMachine();
        }
        RuleBaseConfiguration ruleBaseConf = kieBase.getRuleBaseConfiguration();
        Object object = ComponentsFactory.createConsequenceExceptionHandler( ruleBaseConf.getConsequenceExceptionHandler(),
                                                                             ruleBaseConf.getClassLoader() );
        if ( object instanceof ConsequenceExceptionHandler ) {
            this.legacyConsequenceExceptionHandler = (ConsequenceExceptionHandler) object;
        } else {
            this.consequenceExceptionHandler = (org.kie.api.runtime.rule.ConsequenceExceptionHandler) object;
        }

        this.declarativeAgenda = ruleBaseConf.isDeclarativeAgenda();
        this.sequential = ruleBaseConf.isSequential();
        this.expirationContexts = ruleBaseConf.getEventProcessingMode() == EventProcessingOption.STREAM ? new ArrayList<>() : null;

         // for fully parallel execution the parallelism is implemented at the level of CompositeDefaultAgenda
         this.groupEvaluator = ruleBaseConf.isParallelEvaluation() && !ruleBaseConf.isParallelExecution() ?
                 new ParallelGroupEvaluator( kieBase, workingMemory, this ) :
                 new SequentialGroupEvaluator( kieBase, workingMemory, this );

        this.propagationList = createPropagationList();
    }

    @Override
    public RuleAgendaItem createRuleAgendaItem(final int salience,
                                               final PathMemory rs,
                                               final TerminalNode rtn ) {
        String ruleFlowGroupName = rtn.getRule().getRuleFlowGroup();
        return AgendaComponentFactory.get().createAgendaItem( salience, rs, rtn, isDeclarativeAgenda(),
                (InternalAgendaGroup) getAgendaGroup( !StringUtils.isEmpty(ruleFlowGroupName) ? ruleFlowGroupName : rtn.getRule().getAgendaGroup() ));
    }

    @Override
    public InternalMatch createAgendaItem(RuleTerminalNodeLeftTuple rtnLeftTuple,
                                          final int salience,
                                          final PropagationContext context,
                                          RuleAgendaItem ruleAgendaItem,
                                          InternalAgendaGroup agendaGroup) {
        rtnLeftTuple.init(activationCounter++, salience, context, ruleAgendaItem, agendaGroup);
        return rtnLeftTuple;
    }

    protected PropagationList createPropagationList() {
        if (!workingMemory.getRuleSessionConfiguration().isThreadSafe()) {
            return new ThreadUnsafePropagationList( workingMemory );
        }

        return workingMemory.getRuleSessionConfiguration().hasForceEagerActivationFilter() ?
               new SynchronizedBypassPropagationList( workingMemory ) :
               new SynchronizedPropagationList( workingMemory );
    }

    public PropagationList getPropagationList() {
        return propagationList;
    }

    @Override
    public InternalWorkingMemory getWorkingMemory() {
        return this.workingMemory;
    }

    @Override
    public void addEagerRuleAgendaItem(RuleAgendaItem item) {
        if ( sequential ) {
            return;
        }

        if ( item.isInList(eager) ) {
            return;
        }

        if ( log.isTraceEnabled() ) {
            log.trace("Added {} to eager evaluation list.", item.getRule().getName() );
        }
        eager.add( item );
    }

    @Override
    public void removeEagerRuleAgendaItem(RuleAgendaItem item) {
        if ( !item.isInList(eager) ) {
            return;
        }

        if ( log.isTraceEnabled() ) {
            log.trace( "Removed {} from eager evaluation list.", item.getRule().getName() );
        }
        eager.remove( item );
    }

    @Override
    public void addQueryAgendaItem(RuleAgendaItem item) {
        queries.put( (QueryImpl) item.getRule(), item );
        if ( log.isTraceEnabled() ) {
            log.trace( "Added {} to query evaluation list.", item.getRule().getName() );
        }
    }

    @Override
    public void removeQueryAgendaItem(RuleAgendaItem item) {
        queries.remove( (QueryImpl) item.getRule() );
        if ( log.isTraceEnabled() ) {
            log.trace("Removed {} from query evaluation list.", item.getRule().getName() );
        }
    }

    @Override
    public void addItemToActivationGroup(final InternalMatch internalMatch) {
        String group = internalMatch.getRule().getActivationGroup();
        if ( !StringUtils.isEmpty(group) ) {
            InternalActivationGroup actgroup = getActivationGroup( group );

            // Don't allow lazy activations to activate, from before it's last trigger point
            if ( actgroup.getTriggeredForRecency() != 0 &&
                 actgroup.getTriggeredForRecency() >= internalMatch.getPropagationContext().getFactHandle().getRecency() ) {
                return;
            }

            actgroup.addActivation(internalMatch);
        }
    }

    @Override
    public RuleAgendaItem peekNextRule() {
        return getAgendaGroupsManager().peekNextRule();
    }

    @Override
    public boolean isDeclarativeAgenda() {
        return declarativeAgenda;
    }

    @Override
    public boolean isRuleActiveInRuleFlowGroup(String ruleflowGroupName, String ruleName, String processInstanceId) {
        propagationList.flush();
        RuleFlowGroup systemRuleFlowGroup = this.getRuleFlowGroup( ruleflowGroupName );

        for ( RuleAgendaItem item : ((InternalAgendaGroup)systemRuleFlowGroup).getActivations() ) {
            // The lazy RuleAgendaItem must be fully evaluated, to see if there is a rule match
            RuleExecutor ruleExecutor = item.getRuleExecutor();
            ruleExecutor.evaluateNetwork(workingMemory, this);
            TupleList list = ruleExecutor.getActiveMatches();
            for (RuleTerminalNodeLeftTuple lt = (RuleTerminalNodeLeftTuple) list.getFirst(); lt != null; lt = (RuleTerminalNodeLeftTuple) lt.getNext()) {
                if ( ruleName.equals( lt.getRule().getName() ) && ( lt.checkProcessInstance( workingMemory, processInstanceId ) )) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void cancelActivation(final InternalMatch internalMatch) {
        InternalMatch item = internalMatch;

        workingMemory.cancelActivation(internalMatch, isDeclarativeAgenda());

        if ( isDeclarativeAgenda() ) {
            if (internalMatch.getActivationFactHandle() == null) {
                // This a control rule activation, nothing to do except update counters. As control rules are not in agenda-groups etc.
                return;
            }
            // we are cancelling an actual Activation, so also it's handle from the WM.
            if (internalMatch.getActivationGroupNode() != null ) {
                internalMatch.getActivationGroupNode().getActivationGroup().removeActivation(internalMatch);
            }
        }

        if ( internalMatch.isQueued() ) {
            if (internalMatch.getActivationGroupNode() != null ) {
                internalMatch.getActivationGroupNode().getActivationGroup().removeActivation(internalMatch);
            }

            workingMemory.getAgendaEventSupport().fireActivationCancelled(internalMatch,
                                                                          workingMemory,
                                                                          MatchCancelledCause.WME_MODIFY );
        }

        if (item.getRuleAgendaItem() != null) {
            item.getRuleAgendaItem().getRuleExecutor().fireConsequenceEvent( this.workingMemory, this, item, ON_DELETE_MATCH_CONSEQUENCE_NAME );
        }

        workingMemory.getRuleEventSupport().onDeleteMatch( item );
    }

    @Override
    public boolean setFocus(final String name) {
        return setFocus( null, name );
    }

    public boolean setFocus(final PropagationContext ctx, final String name) {
        InternalAgendaGroup agendaGroup = getAgendaGroupsManager().getAgendaGroup( name );
        agendaGroup.setAutoFocusActivator( ctx );
        return getAgendaGroupsManager().setFocus( agendaGroup );
    }

    @Override
    public AgendaGroupsManager getAgendaGroupsManager() {
        return agendaGroupsManager;
    }

    @Override
    public AgendaEventSupport getAgendaEventSupport() {
        return this.workingMemory.getAgendaEventSupport();
    }

    @Override
    public Map<String, InternalActivationGroup> getActivationGroupsMap() {
        return this.activationGroups;
    }

    @Override
    public InternalActivationGroup getActivationGroup(final String name) {
        return this.activationGroups.computeIfAbsent(name, k -> new ActivationGroupImpl( this, k ));
    }

    @Override
    public RuleFlowGroup getRuleFlowGroup(final String name) {
        return ( RuleFlowGroup ) getAgendaGroup(name);
    }

    @Override
    public void activateRuleFlowGroup(final String name) {
        InternalRuleFlowGroup group =  (InternalRuleFlowGroup) getRuleFlowGroup( name );
        activateRuleFlowGroup( group, -1, null );
    }

    @Override
    public void activateRuleFlowGroup(final String name,
                                      String processInstanceId,
                                      String nodeInstanceId) {
        InternalRuleFlowGroup ruleFlowGroup = (InternalRuleFlowGroup) getRuleFlowGroup( name );
        activateRuleFlowGroup( ruleFlowGroup, processInstanceId, nodeInstanceId );
    }

    public void activateRuleFlowGroup(final InternalRuleFlowGroup group, Object processInstanceId, String nodeInstanceId) {
        workingMemory.getAgendaEventSupport().fireBeforeRuleFlowGroupActivated( group, workingMemory );
        group.setActive( true );
        group.hasRuleFlowListener(true);
        if ( !StringUtils.isEmpty( nodeInstanceId ) ) {
            group.addNodeInstance( processInstanceId, nodeInstanceId );
            group.setActive( true );
        }
        group.setFocus();
        workingMemory.getAgendaEventSupport().fireAfterRuleFlowGroupActivated( group, workingMemory );
        propagationList.notifyWaitOnRest();
    }

    @Override
    public void clear() {
        agendaGroupsManager.reset(true);

        // reset all activation groups.
        for ( InternalActivationGroup group : this.activationGroups.values() ) {
            group.setTriggeredForRecency(factHandleFactory.getRecency());
            group.reset();
        }
        propagationList.reset();
    }

    @Override
    public AgendaGroup getAgendaGroup(String name) {
        return getAgendaGroupsManager().getAgendaGroup(name);
    }

    @Override
    public void reset() {
        agendaGroupsManager.reset(false);

        // reset all activation groups.
        for ( InternalActivationGroup group : this.activationGroups.values() ) {
            group.setTriggeredForRecency(factHandleFactory.getRecency() );
            group.reset();
        }

        eager.clear();
        activationCounter = 0;
        executionStateMachine.reset();
        propagationList.reset();
    }

    @Override
    public void clearAndCancel() {
        // Cancel all items and fire a Cancelled event for each Activation
        agendaGroupsManager.clearAndCancel(this);

        // cancel all activation groups.
        for ( InternalActivationGroup group : this.activationGroups.values() ) {
            clearAndCancelActivationGroup( group );
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.kie.common.AgendaI#clearAgendaGroup(java.lang.String)
     */
    @Override
    public void clearAndCancelAgendaGroup(final String name) {
        agendaGroupsManager.clearAndCancelAgendaGroup(name, this);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.kie.common.AgendaI#clearActivationGroup(java.lang.String)
     */
    @Override
    public void clearAndCancelActivationGroup(final String name) {
        final InternalActivationGroup activationGroup = this.activationGroups.get( name );
        if ( activationGroup != null ) {
            clearAndCancelActivationGroup( activationGroup );
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.kie.common.AgendaI#clearActivationGroup(org.kie.spi.ActivationGroup)
     */
    @Override
    public void clearAndCancelActivationGroup(final InternalActivationGroup activationGroup) {
        final EventSupport eventsupport = this.workingMemory;

        activationGroup.setTriggeredForRecency(factHandleFactory.getRecency() );

        for ( final Iterator it = activationGroup.iterator(); it.hasNext(); ) {
            final ActivationGroupNode node = (ActivationGroupNode) it.next();
            final InternalMatch internalMatch = node.getActivation();
            internalMatch.setActivationGroupNode(null);

            if ( internalMatch.isQueued() ) {
                internalMatch.remove();
                eventsupport.getAgendaEventSupport().fireActivationCancelled(internalMatch,
                                                                             this.workingMemory,
                                                                             MatchCancelledCause.CLEAR );
            }
        }
        activationGroup.reset();
    }

    @Override
    public void clearAndCancelRuleFlowGroup(final String name) {
        agendaGroupsManager.clearAndCancelAgendaGroup(name, this);
    }

    @Override
    public void evaluateEagerList() {
        while ( !eager.isEmpty() ) {
            RuleAgendaItem item = eager.removeFirst();
            if (item.isRuleInUse()) { // this rule could have been removed by an incremental compilation
                evaluateQueriesForRule( item );
                item.getRuleExecutor().evaluateNetwork(workingMemory, this);
            }
        }
    }

    public void evaluateQueriesForRule(RuleAgendaItem item) {
        RuleImpl rule = item.getRule();
        if (!rule.isQuery()) {
            for (QueryImpl query : rule.getDependingQueries()) {
                RuleAgendaItem queryAgendaItem = queries.remove(query);
                if (queryAgendaItem != null) {
                    queryAgendaItem.getRuleExecutor().evaluateNetwork(workingMemory, this);
                }
            }
        }
    }

    @Override
    public int sizeOfRuleFlowGroup(String name) {
        return agendaGroupsManager.sizeOfRuleFlowGroup(name);
    }

    @Override
    public String getFocusName() {
        return this.agendaGroupsManager.getFocusName();
    }

    @Override
    public void fireUntilHalt() {
        fireUntilHalt( null );
    }

    @Override
    public void fireUntilHalt(final AgendaFilter agendaFilter) {
        if ( log.isTraceEnabled() ) {
            log.trace("Starting Fire Until Halt");
        }
        if (executionStateMachine.toFireUntilHalt()) {
            internalFireUntilHalt( agendaFilter, true );
        }
        if ( log.isTraceEnabled() ) {
            log.trace("Ending Fire Until Halt");
        }
    }

    void internalFireUntilHalt( AgendaFilter agendaFilter, boolean isInternalFire ) {
        propagationList.setFiringUntilHalt( true );
        try {
            fireLoop( agendaFilter, -1, RestHandler.FIRE_UNTIL_HALT, isInternalFire );
        } finally {
            propagationList.setFiringUntilHalt( false );
        }
    }

    @Override
    public int fireAllRules(AgendaFilter agendaFilter, int fireLimit) {
        if (!executionStateMachine.toFireAllRules()) {
            return 0;
        }
        if ( log.isTraceEnabled() ) {
            log.trace("Starting Fire All Rules");
        }
        int fireCount = internalFireAllRules( agendaFilter, fireLimit, true );
        if ( log.isTraceEnabled() ) {
            log.trace("Ending Fire All Rules");
        }
        return fireCount;
    }

    int internalFireAllRules( AgendaFilter agendaFilter, int fireLimit, boolean isInternalFire ) {
        return fireLoop( agendaFilter, fireLimit, RestHandler.FIRE_ALL_RULES, isInternalFire );
    }

    private int fireLoop(AgendaFilter agendaFilter, int fireLimit, RestHandler restHandler, boolean isInternalFire) {
        int fireCount = 0;
        try {
            PropagationEntry head = takePropagationHead();
            int returnedFireCount;

            boolean limitReached = fireLimit == 0; // -1 or > 0 will return false. No reason for user to give 0, just handled for completeness.

            // The engine comes to potential rest (inside the loop) when there are no propagations and no rule firings.
            // It's potentially at rest, because we cannot guarantee it is at rest.
            // This is because external async actions (timer rules) can populate the queue that must be executed immediately.
            // A final takeAll within the sync point determines if it can safely come to rest.
            // if takeAll returns null, the engine is now safely at rest. If it returns something
            // the engine is not at rest and the loop continues.
            //
            // When FireUntilHalt comes to a safe rest, the thread is put into a wait state,
            // when the queue is populated the thread is notified and the loop begins again.
            //
            // When FireAllRules comes to a safe rest it will put the engine into an INACTIVE state
            // and the loop can exit.
            //
            // When a halt() command is added to the propagation queue and that queue is flushed
            // the engine is put into a INACTIVE state. At this point isFiring returns false and
            // no more rules can fire. However the loop will continue until rest point has been safely
            // entered, i.e. the queue returns null within that sync point.
            //
            // The loop is susceptable to never return on extremely greedy behaviour.
            //
            // Note that if a halt() command is given, the engine is changed to INACTIVE,
            // and isFiring returns false allowing it to exit before all rules are fired.
            //
            while ( isFiring() || executionStateMachine.getCurrentState().isHalting() )  {
                if ( head != null ) {
                    // it is possible that there are no action propagations, but there are rules to fire.
                    propagationList.flush(head);
                    head = null;
                }

                evaluateEagerList();
                InternalAgendaGroup group = getAgendaGroupsManager().getNextFocus();
                if ( group != null && !limitReached ) {
                    // only fire rules while the limit has not reached.
                    // if halt is called, then isFiring will be false.
                    // The while loop may continue to loop, to keep flushing the action propagation queue
                    returnedFireCount = groupEvaluator.evaluateAndFire( group, agendaFilter, fireCount, fireLimit );
                    fireCount += returnedFireCount;

                    limitReached = ( fireLimit > 0 && fireCount >= fireLimit );
                    head = takePropagationHead();
                } else {
                    returnedFireCount = 0; // no rules fired this iteration, so we know this is 0
                    group = null; // set the group to null in case the fire limit has been reached
                }

                if ( returnedFireCount == 0 && head == null && ( group == null || ( group.isEmpty() && !group.isAutoDeactivate() ) ) && !flushExpirations() ) {
                    // if true, the engine is now considered potentially at rest
                    head = restHandler.handleRest( this, isInternalFire );
                    if ( ( !isInternalFire || executionStateMachine.getCurrentState().isHalting() ) && head == null) {
                        break;
                    }
                }
            }

            agendaGroupsManager.deactivateMainGroupWhenEmpty();
        } finally {
            // makes sure the engine is inactive, if an exception is thrown.
            // if it safely returns, then the engine should already be inactive
            if (isInternalFire) {
                executionStateMachine.immediateHalt(propagationList);
            }
        }
        return fireCount;
    }

    private PropagationEntry takePropagationHead() {
        if (executionStateMachine.getCurrentState().isHalting()) {
            return null;
        }
        return propagationList.takeAll();
    }

    interface RestHandler {
        RestHandler FIRE_ALL_RULES = new FireAllRulesRestHandler();
        RestHandler FIRE_UNTIL_HALT = new FireUntilHaltRestHandler();

        PropagationEntry handleRest(DefaultAgenda agenda, boolean isInternalFire);

        class FireAllRulesRestHandler implements RestHandler {
            @Override
            public PropagationEntry handleRest(DefaultAgenda agenda, boolean isInternalFire) {
                synchronized (agenda.executionStateMachine.getStateMachineLock()) {
                    PropagationEntry head = agenda.propagationList.takeAll();
                    if (isInternalFire && head == null) {
                        agenda.internalHalt();
                    }
                    return head;
                }
            }
        }

        class FireUntilHaltRestHandler implements RestHandler {
            @Override
            public PropagationEntry handleRest(DefaultAgenda agenda, boolean isInternalFire) {
                boolean deactivated = false;
                if (isInternalFire && agenda.executionStateMachine.getCurrentState() == ExecutionStateMachine.ExecutionState.FIRING_UNTIL_HALT) {
                    agenda.executionStateMachine.inactiveOnFireUntilHalt();
                    deactivated = true;
                }

                PropagationEntry head;
                // this must use the same sync target as takeAllPropagations, to ensure this entire block is atomic, up to the point of wait
                synchronized (agenda.propagationList) {
                    head = agenda.takePropagationHead();

                    // if halt() has called, the thread should not be put into a wait state
                    // instead this is just a safe way to make sure the queue is flushed before exiting the loop
                    if (head == null && (
                            agenda.executionStateMachine.getCurrentState() == ExecutionStateMachine.ExecutionState.FIRING_UNTIL_HALT ||
                            agenda.executionStateMachine.getCurrentState() == ExecutionStateMachine.ExecutionState.INACTIVE_ON_FIRING_UNTIL_HALT )) {
                        agenda.propagationList.waitOnRest();
                        head = agenda.takePropagationHead();
                    }
                }

                if (deactivated) {
                    agenda.executionStateMachine.toFireUntilHalt();
                }

                return head;
            }
        }
    }

    @Override
    public boolean isFiring() {
        return executionStateMachine.isFiring();
    }

    @Override
    public void executeTask( ExecutableEntry executable ) {
        if ( !executionStateMachine.toExecuteTask( executable ) ) {
            return;
        }

        try {
            executable.execute();
        } finally {
            executionStateMachine.immediateHalt(propagationList);
        }
    }

    @Override
    public void executeFlush() {
        if (!executionStateMachine.toExecuteTaskState()) {
            return;
        }

        try {
            flushPropagations();
        } finally {
            executionStateMachine.immediateHalt(propagationList);
        }
    }

    @Override
    public void activate() {
        executionStateMachine.activate(this, propagationList);
    }

    @Override
    public void deactivate() {
        executionStateMachine.deactivate();
    }

    @Override
    public boolean tryDeactivate() {
        return executionStateMachine.tryDeactivate();
    }

    static class Halt extends PropagationEntry.AbstractPropagationEntry {

        private final ExecutionStateMachine executionStateMachine;

        protected Halt( ExecutionStateMachine executionStateMachine ) {
            this.executionStateMachine = executionStateMachine;
        }

        @Override
        public void internalExecute(ReteEvaluator reteEvaluator ) {
            executionStateMachine.internalHalt();
            reteEvaluator.getActivationsManager().haltGroupEvaluation();
        }

        @Override
        public String toString() {
            return "Halt";
        }
    }

    static class ImmediateHalt extends PropagationEntry.AbstractPropagationEntry {

        private final ExecutionStateMachine executionStateMachine;
        private final PropagationList propagationList;

        protected ImmediateHalt( ExecutionStateMachine executionStateMachine, PropagationList propagationList ) {
            this.executionStateMachine = executionStateMachine;
            this.propagationList = propagationList;
        }

        @Override
        public void internalExecute(ReteEvaluator reteEvaluator ) {
            executionStateMachine.immediateHalt(propagationList);
            reteEvaluator.getActivationsManager().haltGroupEvaluation();
        }

        @Override
        public String toString() {
            return "ImmediateHalt";
        }
    }

    @Override
    public synchronized void halt() {
        // only attempt halt an engine that is currently firing
        // This will place a halt command on the propagation queue
        // that will allow the engine to halt safely
        if ( isFiring() ) {
            PropagationEntry halt = executionStateMachine.getCurrentState() == ExecutionStateMachine.ExecutionState.FIRING_ALL_RULES ?
                    new ImmediateHalt(executionStateMachine, propagationList) :
                    new Halt(executionStateMachine);
            propagationList.addEntry(halt);
        }
    }

    @Override
    public boolean dispose(InternalWorkingMemory wm) {
        propagationList.dispose();
        return executionStateMachine.dispose( wm );
    }

    @Override
    public boolean isAlive() {
        return executionStateMachine.isAlive();
    }

    public void internalHalt() {
        executionStateMachine.internalHalt();
    }

    @Override
    public void setActivationsFilter(ActivationsFilter filter) {
        this.activationsFilter = filter;
    }

    @Override
    public ActivationsFilter getActivationsFilter() {
        return this.activationsFilter;
    }

    @Override
    public void handleException(InternalMatch internalMatch, Exception e) {
        if ( this.legacyConsequenceExceptionHandler != null ) {
            this.legacyConsequenceExceptionHandler.handleException(internalMatch, getWorkingMemory(), e);
        } else if ( this.consequenceExceptionHandler != null ) {
            this.consequenceExceptionHandler.handleException(internalMatch, getWorkingMemory().getKnowledgeRuntime(), e);
        } else {
            throw new RuntimeException( e );
        }
    }

    @Override
    public KnowledgeHelper getKnowledgeHelper() {
        return groupEvaluator.getKnowledgeHelper();
    }

    @Override
    public void resetKnowledgeHelper() {
        groupEvaluator.resetKnowledgeHelper();
    }

    @Override
    public void haltGroupEvaluation() {
        groupEvaluator.haltEvaluation();
    }

    @Override
    public void addPropagation(PropagationEntry propagationEntry) {
        propagationList.addEntry( propagationEntry );
    }

    @Override
    public void flushPropagations() {
        propagationList.flush();
    }

    @Override
    public void notifyWaitOnRest() {
        propagationList.notifyWaitOnRest();
    }

    @Override
    public Iterator<PropagationEntry> getActionsIterator() {
        return propagationList.iterator();
    }

    @Override
    public boolean hasPendingPropagations() {
        return !propagationList.isEmpty();
    }

    interface ExecutionStateMachine {
        enum ExecutionState {         // fireAllRule | fireUntilHalt | executeTask <-- required action
            INACTIVE( false, true ),         // fire        | fire          | exec
            FIRING_ALL_RULES( true, true ),  // do nothing  | wait + fire   | enqueue
            FIRING_UNTIL_HALT( true, true ), // do nothing  | do nothing    | enqueue
            INACTIVE_ON_FIRING_UNTIL_HALT( true, true ),
            HALTING( false, true ),          // wait + fire | wait + fire   | enqueue
            EXECUTING_TASK( false, true ),   // wait + fire | wait + fire   | wait + exec
            DEACTIVATED( false, true ),      // wait + fire | wait + fire   | wait + exec
            DISPOSING( false, false ),       // no further action is allowed
            DISPOSED( false, false );        // no further action is allowed

            private final boolean firing;
            private final boolean alive;

            ExecutionState( boolean firing, boolean alive ) {
                this.firing = firing;
                this.alive = alive;
            }

            public boolean isFiring() {
                return firing;
            }

            public boolean isHalting() {
                return this == HALTING;
            }

            public boolean isAlive() {
                return alive;
            }
        }

        boolean isFiring();

        void reset();

        boolean toFireAllRules();

        boolean toFireUntilHalt();

        boolean toExecuteTask( ExecutableEntry executable );

        boolean toExecuteTaskState();

        void activate(DefaultAgenda agenda, PropagationList propagationList);

        void deactivate();

        boolean tryDeactivate();

        void immediateHalt(PropagationList propagationList);

        void inactiveOnFireUntilHalt();

        void internalHalt();

        boolean dispose(InternalWorkingMemory workingMemory);

        boolean isAlive();

        ExecutionState getCurrentState();

        Object getStateMachineLock();
    }

    static class UnsafeExecutionStateMachine implements ExecutionStateMachine {

        private final Object stateMachineLock = new Object();

        private ExecutionState currentState = ExecutionState.INACTIVE;

        @Override
        public boolean isFiring() {
            return currentState.isFiring();
        }

        @Override
        public void reset() {
            currentState = ExecutionState.INACTIVE;
        }

        @Override
        public boolean toFireAllRules() {
            currentState = ExecutionState.FIRING_ALL_RULES;
            return true;
        }

        @Override
        public boolean toFireUntilHalt() {
            throw new UnsupportedOperationException( "Not permitted in non-thread-safe mode" );
        }

        @Override
        public boolean toExecuteTask( ExecutableEntry executable ) {
            throw new UnsupportedOperationException( "Not permitted in non-thread-safe mode" );
        }

        @Override
        public boolean toExecuteTaskState() {
            currentState = ExecutionState.EXECUTING_TASK;
            return true;
        }

        @Override
        public void activate( DefaultAgenda agenda, PropagationList propagationList ) {
        }

        @Override
        public void deactivate() {
            currentState = ExecutionState.DEACTIVATED;
        }

        @Override
        public boolean tryDeactivate() {
            currentState = ExecutionState.DEACTIVATED;
            return true;
        }

        @Override
        public void immediateHalt( PropagationList propagationList ) {
            currentState = ExecutionState.INACTIVE;
        }

        @Override
        public void inactiveOnFireUntilHalt() {
            throw new UnsupportedOperationException( "Not permitted in non-thread-safe mode" );
        }

        @Override
        public void internalHalt() {
            if (isFiring()) {
                currentState = ExecutionState.HALTING;
            }
        }

        @Override
        public boolean dispose( InternalWorkingMemory workingMemory ) {
            currentState = ExecutionState.DISPOSED;
            return true;
        }

        @Override
        public boolean isAlive() {
            return currentState.isAlive();
        }

        @Override
        public ExecutionState getCurrentState() {
            return currentState;
        }

        @Override
        public Object getStateMachineLock() {
            return stateMachineLock;
        }
    }

    static class ConcurrentExecutionStateMachine implements ExecutionStateMachine {
        private volatile ExecutionState currentState = ExecutionState.INACTIVE;
        private volatile boolean wasFiringUntilHalt = false;

        private final Object stateMachineLock = new Object();

        private long fireUntilHaltThreadId = -1;

        public boolean isFiring() {
            return currentState.isFiring();
        }

        public void reset() {
            currentState = ExecutionState.INACTIVE;
            wasFiringUntilHalt = false;
        }

        public boolean toFireAllRules() {
            synchronized (stateMachineLock) {
                if (currentState.isFiring() || !currentState.isAlive()) {
                    return false;
                }
                waitAndEnterExecutionState( ExecutionState.FIRING_ALL_RULES );
            }
            return true;
        }

        public boolean toFireUntilHalt() {
            synchronized (stateMachineLock) {
                if ( currentState == ExecutionState.FIRING_UNTIL_HALT || currentState == ExecutionState.HALTING ) {
                    return false;
                }
                waitAndEnterExecutionState( ExecutionState.FIRING_UNTIL_HALT );
            }
            return true;
        }

        public boolean toExecuteTask( ExecutableEntry executable ) {
            synchronized (stateMachineLock) {
                // state is never changed outside of a sync block, so this is safe.
                if (isFiring()) {
                    executable.enqueue();
                    return false;
                }

                if (currentState != ExecutionState.EXECUTING_TASK) {
                    waitAndEnterExecutionState( ExecutionState.EXECUTING_TASK );
                }
                return true;
            }
        }

        public boolean toExecuteTaskState() {
            synchronized (stateMachineLock) {
                // state is never changed outside of a sync block, so this is safe.
                if (!currentState.isAlive() || currentState.isFiring()) {
                    return false;
                }
                waitAndEnterExecutionState( ExecutionState.EXECUTING_TASK );
                return true;
            }
        }

        private void waitAndEnterExecutionState( ExecutionState newState ) {
            waitInactive();
            setCurrentState( newState );
        }

        private void waitInactive() {
            while ( currentState != ExecutionState.INACTIVE && currentState != ExecutionState.INACTIVE_ON_FIRING_UNTIL_HALT && currentState != ExecutionState.DISPOSED ) {
                try {
                    stateMachineLock.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException( e );
                }
            }
        }

        private void setCurrentState(ExecutionState state) {
            if ( log.isDebugEnabled() ) {
                log.debug("State was {} is now {}", currentState, state);
            }
            if (currentState != ExecutionState.DISPOSED) {
                currentState = state;
            }
        }

        public void activate(DefaultAgenda agenda, PropagationList propagationList) {
            if ( currentState.isAlive() ) {
                boolean restoreFireUntilHalt = wasFiringUntilHalt;
                wasFiringUntilHalt = false;
                boolean restoreFiringOnSameThread = restoreFireUntilHalt && fireUntilHaltThreadId == Thread.currentThread().getId();
                fireUntilHaltThreadId = -1L;

                immediateHalt( propagationList );

                if ( restoreFireUntilHalt ) {
                    // restoring a fire until halt after an incremental compilation should either happen on the same thread
                    // where the fireUntilHalt was running before the compilation or on a brand new thread
                    if (restoreFiringOnSameThread) {
                        agenda.fireUntilHalt();
                    } else {
                        new Thread(agenda::fireUntilHalt).start();
                    }
                }
            }
        }

        public void deactivate() {
            synchronized (stateMachineLock) {
                pauseFiringUntilHalt();
                if ( currentState != ExecutionState.DEACTIVATED && currentState.isAlive() ) {
                    waitAndEnterExecutionState( ExecutionState.DEACTIVATED );
                }
            }
        }

        public boolean tryDeactivate() {
            synchronized (stateMachineLock) {
                if ( !currentState.isAlive() ) {
                    return true;
                }
                pauseFiringUntilHalt();
                if ( currentState == ExecutionState.INACTIVE || currentState == ExecutionState.INACTIVE_ON_FIRING_UNTIL_HALT ) {
                    setCurrentState( ExecutionState.DEACTIVATED );
                    return true;
                }
            }
            return false;
        }

        private void pauseFiringUntilHalt() {
            if ( currentState == ExecutionState.FIRING_UNTIL_HALT) {
                wasFiringUntilHalt = true;
                setCurrentState( ExecutionState.HALTING );
                waitInactive();
            }
        }

        public void immediateHalt(PropagationList propagationList) {
            synchronized (stateMachineLock) {
                if (currentState != ExecutionState.INACTIVE) {
                    setCurrentState( ExecutionState.INACTIVE );
                    stateMachineLock.notifyAll();
                    propagationList.onEngineInactive();
                    if (wasFiringUntilHalt) {
                        // if it is halting a thread that was running a fireUntilHalt registers its id
                        fireUntilHaltThreadId = Thread.currentThread().getId();
                    }
                }
            }
        }

        public void inactiveOnFireUntilHalt() {
            synchronized (stateMachineLock) {
                if (currentState != ExecutionState.INACTIVE && currentState != ExecutionState.INACTIVE_ON_FIRING_UNTIL_HALT) {
                    setCurrentState( ExecutionState.INACTIVE_ON_FIRING_UNTIL_HALT );
                    stateMachineLock.notifyAll();
                }
            }
        }

        public void internalHalt() {
            synchronized (stateMachineLock) {
                if (isFiring()) {
                    setCurrentState( ExecutionState.HALTING );
                }
            }
        }

        public boolean dispose(InternalWorkingMemory workingMemory) {
            synchronized (stateMachineLock) {
                if (!currentState.isAlive()) {
                    return false;
                }
                if (currentState.isFiring() && currentState != ExecutionState.INACTIVE_ON_FIRING_UNTIL_HALT) {
                    setCurrentState( ExecutionState.DISPOSING );
                    workingMemory.notifyWaitOnRest();
                }
                waitAndEnterExecutionState( ExecutionState.DISPOSED );
                stateMachineLock.notifyAll();
                return true;
            }
        }

        public boolean isAlive() {
            synchronized (stateMachineLock) {
                return currentState.isAlive();
            }
        }

        public ExecutionState getCurrentState() {
            return currentState;
        }

        public Object getStateMachineLock() {
            return stateMachineLock;
        }
    }

    @Override
    public void registerExpiration(PropagationContext ectx) {
        // it is safe to add into the expirationContexts list without any synchronization because
        // the state machine already guarantees that only one thread at time can access it
        expirationContexts.add(ectx);
    }

    private boolean flushExpirations() {
        if (expirationContexts == null || expirationContexts.isEmpty() || propagationList.hasEntriesDeferringExpiration()) {
            return false;
        }
        for (PropagationContext ectx : expirationContexts) {
            doRetract( ectx );
        }
        expirationContexts.clear();
        return true;
    }

    protected void doRetract( PropagationContext ectx ) {
        InternalFactHandle factHandle = (InternalFactHandle) ectx.getFactHandle();
        retractFactHandle(ectx, factHandle);
        if (isPendingRemoveFactHandleFromStore(factHandle)) {
            String epId = factHandle.getEntryPointName();
            ( (InternalWorkingMemoryEntryPoint) workingMemory.getEntryPoint( epId ) ).removeFromObjectStore( factHandle );
        }
    }

    protected void retractFactHandle(PropagationContext ectx, InternalFactHandle factHandle) {
        ObjectTypeNode.retractLeftTuples(factHandle, ectx, workingMemory );
        ObjectTypeNode.retractRightTuples(factHandle, ectx, workingMemory );
    }

    protected boolean isPendingRemoveFactHandleFromStore(InternalFactHandle factHandle) {
        return factHandle.isPendingRemoveFromStore();
    }

    @Override
    public boolean isParallelAgenda() {
        return false;
    }
}
