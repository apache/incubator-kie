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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.drools.base.definitions.rule.impl.QueryImpl;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.core.common.ActivationGroupImpl;
import org.drools.core.common.ActivationGroupNode;
import org.drools.core.common.ActivationsFilter;
import org.drools.core.common.ActivationsManager;
import org.drools.core.common.AgendaGroupsManager;
import org.drools.core.common.InternalActivationGroup;
import org.drools.core.common.InternalAgendaGroup;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemoryEntryPoint;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.concurrent.GroupEvaluator;
import org.drools.core.concurrent.SequentialGroupEvaluator;
import org.drools.core.event.AgendaEventSupport;
import org.drools.core.phreak.ExecutableEntry;
import org.drools.base.phreak.PropagationEntry;
import org.drools.core.phreak.PropagationList;
import org.drools.core.phreak.RuleAgendaItem;
import org.drools.core.phreak.RuleExecutor;
import org.drools.core.phreak.SynchronizedPropagationList;
import org.drools.core.reteoo.AgendaComponentFactory;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.RuleTerminalNodeLeftTuple;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.rule.accessor.FactHandleFactory;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.core.rule.consequence.KnowledgeHelper;
import org.drools.util.StringUtils;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.event.rule.MatchCancelledCause;
import org.kie.api.runtime.rule.AgendaFilter;

public class ActivationsManagerImpl implements ActivationsManager {

    private final ReteEvaluator reteEvaluator;
    private final AgendaGroupsManager agendaGroupsManager;

    private int activationCounter = 0;

    private final AgendaEventSupport agendaEventSupport = new AgendaEventSupport();

    private final Map<String, InternalActivationGroup> activationGroups = new HashMap<>();

    private final PropagationList propagationList;

    private final GroupEvaluator groupEvaluator;

    private boolean firing = false;

    private final org.drools.core.util.LinkedList<RuleAgendaItem> eager = new org.drools.core.util.LinkedList<>();

    private final Map<QueryImpl, RuleAgendaItem> queries = new ConcurrentHashMap<>();

    private List<PropagationContext> expirationContexts;
	private FactHandleFactory factHandleFactory;

    public ActivationsManagerImpl(InternalRuleBase ruleBase, ReteEvaluator reteEvaluator, FactHandleFactory factHandleFactory) {
        this.reteEvaluator = reteEvaluator;
		this.factHandleFactory = factHandleFactory;
        this.agendaGroupsManager = new AgendaGroupsManager.SimpleAgendaGroupsManager(ruleBase, reteEvaluator, factHandleFactory);
        this.propagationList = new SynchronizedPropagationList(reteEvaluator);
        this.groupEvaluator = new SequentialGroupEvaluator( ruleBase, reteEvaluator, this );
        if (ruleBase.getRuleBaseConfiguration().getEventProcessingMode() == EventProcessingOption.STREAM) {
            expirationContexts = new ArrayList<>();
        }
    }

    @Override
    public AgendaGroupsManager getAgendaGroupsManager() {
        return agendaGroupsManager;
    }

    @Override
    public AgendaEventSupport getAgendaEventSupport() {
        return agendaEventSupport;
    }

    @Override
    public ActivationsFilter getActivationsFilter() {
        // TODO this is only used by protobuf serialization which is not implemented here for now
        return null;
    }

    @Override
    public void addEagerRuleAgendaItem(RuleAgendaItem item) {
        if ( item.isInList(eager) ) {
            return;
        }
        eager.add( item );
    }

    @Override
    public void removeEagerRuleAgendaItem(RuleAgendaItem item) {
        if ( !item.isInList(eager) ) {
            return;
        }
        eager.remove( item );
    }

    @Override
    public void addQueryAgendaItem(RuleAgendaItem item) {
        queries.put( (QueryImpl) item.getRule(), item );
    }

    @Override
    public void removeQueryAgendaItem(RuleAgendaItem item) {
        queries.remove(item.getRule());
    }

    @Override
    public void registerExpiration(PropagationContext ectx) {
        // it is safe to add into the expirationContexts list without any synchronization because
        // the state machine already guarantees that only one thread at time can access it
        expirationContexts.add(ectx);
    }

    @Override
    public void clearAndCancelActivationGroup(final String name) {
        final InternalActivationGroup activationGroup = this.activationGroups.get( name );
        if ( activationGroup != null ) {
            clearAndCancelActivationGroup( activationGroup );
        }
    }

    @Override
    public void clearAndCancelActivationGroup(final InternalActivationGroup activationGroup) {
        activationGroup.setTriggeredForRecency(factHandleFactory.getRecency() );

        for (final Iterator it = activationGroup.iterator(); it.hasNext(); ) {
            final ActivationGroupNode node = (ActivationGroupNode) it.next();
            final InternalMatch internalMatch = node.getActivation();
            internalMatch.setActivationGroupNode(null);

            if ( internalMatch.isQueued() ) {
                internalMatch.remove();
                getAgendaEventSupport().fireActivationCancelled(internalMatch, this.reteEvaluator, MatchCancelledCause.CLEAR);
            }
        }
        activationGroup.reset();
    }

    @Override
    public RuleAgendaItem createRuleAgendaItem(int salience, PathMemory pathMemory, TerminalNode rtn) {
        return AgendaComponentFactory.get().createAgendaItem( salience, pathMemory, rtn, false, agendaGroupsManager.getMainAgendaGroup());
    }

    @Override
    public InternalMatch createAgendaItem(RuleTerminalNodeLeftTuple rtnLeftTuple, int salience, PropagationContext context, RuleAgendaItem ruleAgendaItem, InternalAgendaGroup agendaGroup) {
        rtnLeftTuple.init(activationCounter++, salience, context, ruleAgendaItem, agendaGroup);
        return rtnLeftTuple;
    }

    @Override
    public void cancelActivation(InternalMatch internalMatch) {
        if ( internalMatch.isQueued() ) {
            if (internalMatch.getActivationGroupNode() != null ) {
                internalMatch.getActivationGroupNode().getActivationGroup().removeActivation(internalMatch);
            }

            getAgendaEventSupport().fireActivationCancelled(internalMatch, reteEvaluator, MatchCancelledCause.WME_MODIFY);
        }

        if (internalMatch.getRuleAgendaItem() != null) {
            internalMatch.getRuleAgendaItem().getRuleExecutor().fireConsequenceEvent( this.reteEvaluator, this, internalMatch, ON_DELETE_MATCH_CONSEQUENCE_NAME );
        }

        reteEvaluator.getRuleEventSupport().onDeleteMatch(internalMatch);
    }

    @Override
    public void addItemToActivationGroup(InternalMatch internalMatch) {
        String group = internalMatch.getRule().getActivationGroup();
        if ( !StringUtils.isEmpty(group) ) {
            InternalActivationGroup actgroup = this.activationGroups.computeIfAbsent(group, k -> new ActivationGroupImpl( this, k ));

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
    public void flushPropagations() {
        propagationList.flush();
    }

    @Override
    public boolean isFiring() {
        return firing;
    }

    @Override
    public void evaluateEagerList() {
        while ( !eager.isEmpty() ) {
            RuleAgendaItem item = eager.removeFirst();
            if (item.isRuleInUse()) { // this rule could have been removed by an incremental compilation
                evaluateQueriesForRule( item );
                RuleExecutor ruleExecutor = item.getRuleExecutor();
                ruleExecutor.evaluateNetwork(reteEvaluator, this);
            }
        }
    }

    @Override
    public void evaluateQueriesForRule(RuleAgendaItem item) {
        RuleImpl rule = item.getRule();
        if (!rule.isQuery()) {
            for (QueryImpl query : rule.getDependingQueries()) {
                RuleAgendaItem queryAgendaItem = queries.remove(query);
                if (queryAgendaItem != null) {
                    queryAgendaItem.getRuleExecutor().evaluateNetwork(reteEvaluator, this);
                }
            }
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
    public void executeTask(ExecutableEntry executableEntry) {
        executableEntry.execute();
    }

    @Override
    public void addPropagation(PropagationEntry<ReteEvaluator> propagationEntry) {
        propagationList.addEntry( propagationEntry );
    }

    @Override
    public int fireAllRules(AgendaFilter agendaFilter, int fireLimit) {
        return fireLoop( agendaFilter, fireLimit );
    }

    private int fireLoop(AgendaFilter agendaFilter, int fireLimit) {
        firing = true;
        int fireCount = 0;
        PropagationEntry<ReteEvaluator> head = propagationList.takeAll();
        int returnedFireCount;

        boolean limitReached = fireLimit == 0; // -1 or > 0 will return false. No reason for user to give 0, just handled for completeness.

        while ( isFiring()  )  {
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
                head = propagationList.takeAll();
            } else {
                returnedFireCount = 0; // no rules fired this iteration, so we know this is 0
                group = null; // set the group to null in case the fire limit has been reached
            }

            if ( returnedFireCount == 0 && head == null && ( group == null || ( group.isEmpty() && !group.isAutoDeactivate() ) ) && !flushExpirations() ) {
                // if true, the engine is now considered potentially at rest
                head = handleRest();
            }
        }

        agendaGroupsManager.deactivateMainGroupWhenEmpty();
        return fireCount;
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

    private void doRetract( PropagationContext ectx ) {
        InternalFactHandle factHandle = (InternalFactHandle) ectx.getFactHandle();
        ObjectTypeNode.retractLeftTuples( factHandle, ectx, reteEvaluator );
        ObjectTypeNode.retractRightTuples( factHandle, ectx, reteEvaluator );
        if ( factHandle.isPendingRemoveFromStore() ) {
            String epId = factHandle.getEntryPointName();
            ( (InternalWorkingMemoryEntryPoint) reteEvaluator.getEntryPoint( epId ) ).removeFromObjectStore( factHandle );
        }
    }

    private PropagationEntry<ReteEvaluator> handleRest() {
        PropagationEntry<ReteEvaluator> head = propagationList.takeAll();
        if (head == null) {
            firing = false;
        }
        return head;
    }
}
