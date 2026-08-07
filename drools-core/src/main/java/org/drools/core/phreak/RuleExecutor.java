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

import org.drools.base.base.SalienceInteger;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.rule.consequence.Consequence;
import org.drools.base.rule.consequence.ConsequenceException;
import org.drools.core.common.ActivationsManager;
import org.drools.core.common.EventSupport;
import org.drools.core.common.InternalActivationGroup;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.conflict.MatchConflictResolver;
import org.drools.core.conflict.RuleAgendaConflictResolver;
import org.drools.core.event.RuleEventListenerSupport;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.reteoo.RuleTerminalNodeLeftTuple;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.core.rule.consequence.KnowledgeHelper;
import org.drools.base.util.LinkedList;
import org.drools.core.util.Queue;
import org.drools.core.util.QueueFactory;
import org.drools.core.util.index.TupleList;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.MatchCancelledCause;
import org.kie.api.runtime.rule.AgendaFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuleExecutor {

    private static final boolean DEBUG_DORMANT_TUPLE = false;

    protected static final Logger log = LoggerFactory.getLogger(RuleExecutor.class);

    private final PathMemory pmem;
    private final RuleAgendaItem ruleAgendaItem;
    private final TupleList activeMatches;
    private final LinkedList<TupleImpl> dormantMatches;
    private final Queue<InternalMatch> queue;
    private volatile boolean dirty;
    private final boolean declarativeAgendaEnabled;
    private boolean fireExitedEarly;

    public RuleExecutor(final PathMemory pmem,
            RuleAgendaItem ruleAgendaItem,
            boolean declarativeAgendaEnabled) {
        this.pmem = pmem;
        this.ruleAgendaItem = ruleAgendaItem;
        this.activeMatches = new TupleList();
        this.dormantMatches = new LinkedList<>();
        this.declarativeAgendaEnabled = declarativeAgendaEnabled;
        this.queue = ruleAgendaItem.getRule().getSalience().isDynamic() ? QueueFactory.createQueue(MatchConflictResolver.INSTANCE) : null;
    }

    public int evaluateNetworkAndFire( ReteEvaluator reteEvaluator,
                                       AgendaFilter filter,
                                       int fireCount,
                                       int fireLimit ) {
        evaluateNetworkIfDirty(reteEvaluator);
        return fire(reteEvaluator, pmem.getActualActivationsManager( ), filter, fireCount, fireLimit);
    }

    public int evaluateNetworkAndFire(ReteEvaluator reteEvaluator, ActivationsManager activationsManager, AgendaFilter filter, int fireCount, int fireLimit ) {
        evaluateNetworkIfDirty(reteEvaluator, activationsManager );

        if ( reteEvaluator.getRuleSessionConfiguration().isDirectFiring() ) {
            return doDirectFirings(activationsManager, filter, reteEvaluator);
        }

        return fire( reteEvaluator, activationsManager, filter, fireCount, fireLimit );
    }

    private int doDirectFirings(ActivationsManager activationsManager, AgendaFilter filter, ReteEvaluator reteEvaluator) {
        RuleTerminalNode rtn = (RuleTerminalNode) pmem.getPathEndNode();
        int directFirings = activeMatches.size();

        for (RuleTerminalNodeLeftTuple tuple = (RuleTerminalNodeLeftTuple) activeMatches.getFirst(); tuple != null; tuple = (RuleTerminalNodeLeftTuple) activeMatches.getFirst()) {
            if (cancelAndContinue(reteEvaluator, rtn, tuple, filter)) {
                directFirings--;
            } else {
                fireActivationEvent(reteEvaluator, activationsManager, tuple, tuple.getConsequence());
            }
            removeActiveTuple(tuple );
        }
        ruleAgendaItem.remove();
        return directFirings;
    }

    public void fire(ReteEvaluator reteEvaluator, ActivationsManager activationsManager) {
        fire(reteEvaluator, activationsManager, null, 0, Integer.MAX_VALUE);
    }

    public int fire( ReteEvaluator reteEvaluator, ActivationsManager activationsManager, AgendaFilter filter, int fireCount, int fireLimit) {
        int localFireCount = 0;

        if (!activeMatches.isEmpty()) {
            if (!fireExitedEarly && isDeclarativeAgendaEnabled()) {
                // Network Evaluation can notify meta rules, which should be given a chance to fire first
                RuleAgendaItem nextRule = activationsManager.peekNextRule();
                if (!isHigherSalience( nextRule )) {
                    fireExitedEarly = true;
                    return localFireCount;
                }
            }

            RuleTerminalNode rtn = (RuleTerminalNode) pmem.getPathEndNode();
            boolean ruleIsAllMatches = rtn.getRule().isAllMatches();
            Tuple tuple = getNextTuple();

            if (ruleIsAllMatches) {
                fireConsequenceEvent(reteEvaluator, activationsManager, (InternalMatch) tuple, ActivationsManager.ON_BEFORE_ALL_FIRES_CONSEQUENCE_NAME);
            }

            Tuple lastTuple = null;
            for (; tuple != null; lastTuple = tuple, tuple = getNextTuple()) {

                //check if the rule is not effective or
                // if the current Rule is no-loop and the origin rule is the same then return
                if (cancelAndContinue(reteEvaluator, rtn, tuple, filter)) {
                    continue;
                }

                InternalMatch item = (InternalMatch) tuple;
                if (activationsManager.getActivationsFilter() != null && !activationsManager.getActivationsFilter().accept(item)) {
                    // only relevant for serialization, to not refire Matches already fired
                    continue;
                }

                fireActivation( reteEvaluator, activationsManager, item );
                localFireCount++;

                if (rtn.getLeftTupleSource() == null) {
                    break; // The activation firing removed this rule from the rule base
                }

                activationsManager.flushPropagations();

                int salience = ruleAgendaItem.getSalience(); // dynamic salience may have updated it, so get again.
                if (queue != null && !queue.isEmpty() && salience != queue.peek().getSalience()) {
                    ruleAgendaItem.dequeue();
                    ruleAgendaItem.setSalience(queue.peek().getSalience());
                    ruleAgendaItem.getAgendaGroup().add( ruleAgendaItem );
                }

                if (!ruleIsAllMatches) { // if firing rule is @All don't give way to other rules
                    if ( firingHalted(activationsManager) ||
                         fireLimitReached(fireCount, fireLimit, localFireCount) ||
                         ruleWithHigherSalienceActivated(activationsManager) ) {
                        break;
                    }
                    if (!reteEvaluator.isSequential()) {
                        evaluateNetworkIfDirty(reteEvaluator, activationsManager );
                    }
                }
            }

            if (ruleIsAllMatches) {
                fireConsequenceEvent(reteEvaluator, activationsManager, (InternalMatch) lastTuple, ActivationsManager.ON_AFTER_ALL_FIRES_CONSEQUENCE_NAME);
            }
        }

        removeRuleAgendaItemWhenEmpty();

        fireExitedEarly = false;
        return localFireCount;
    }

    private TupleImpl getNextTuple() {
        if (activeMatches.isEmpty()) {
            return null;
        }
        TupleImpl leftTuple;
        if (queue != null) {
            leftTuple = (TupleImpl) queue.dequeue();
            activeMatches.remove(leftTuple);
        } else {
            leftTuple = activeMatches.removeFirst();
            ((InternalMatch) leftTuple).setQueued(false);
        }

        addDormantTuple((RuleTerminalNodeLeftTuple) leftTuple);
        return leftTuple;
    }

    public PathMemory getPathMemory() {
        return pmem;
    }

    public void removeRuleAgendaItemWhenEmpty() {
        if (!dirty && activeMatches.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("Removing RuleAgendaItem " + ruleAgendaItem);
            }
            ruleAgendaItem.remove();
            if ( ruleAgendaItem.getRule().isQuery() ) {
                pmem.getActualActivationsManager( ).removeQueryAgendaItem( ruleAgendaItem );
            } else if ( ruleAgendaItem.getRule().isEager() ) {
                pmem.getActualActivationsManager( ).removeEagerRuleAgendaItem(ruleAgendaItem);
            }
        }
    }

    public void evaluateNetwork(ReteEvaluator reteEvaluator, ActivationsManager activationsManager) {
        reteEvaluator.getRuleNetworkEvaluator().evaluateNetwork( activationsManager, this, pmem );
        setDirty( false );
    }
    
    
    public void evaluateNetworkIfDirty(ReteEvaluator reteEvaluator) {
        evaluateNetworkIfDirty(reteEvaluator, pmem.getActualActivationsManager( ));
    }
    
    public void evaluateNetworkIfDirty(ReteEvaluator reteEvaluator, ActivationsManager activationsManager) {
        if ( isDirty() ) {
            evaluateNetwork(reteEvaluator, activationsManager);
        }
    }

    public RuleAgendaItem getRuleAgendaItem() {
        return ruleAgendaItem;
    }

    private boolean cancelAndContinue(ReteEvaluator reteEvaluator,
                                      RuleTerminalNode rtn,
                                      Tuple leftTuple,
                                      AgendaFilter filter) {
        // NB. stopped setting the LT.object to Boolean.TRUE, that Reteoo did.
        RuleImpl rule = rtn.getRule();
        if ( !rule.isEffective(leftTuple, rtn.getEnabledDeclarations(), reteEvaluator) ) {
            return true;
        }

        if (rule.hasCalendars()) {
            long timestamp = reteEvaluator.getSessionClock().getCurrentTime();
            for (String cal : rule.getCalendars()) {
                if (!reteEvaluator.getCalendars().get(cal).isTimeIncluded(timestamp)) {
                    return true;
                }
            }
        }

        return filter != null && !filter.accept((InternalMatch) leftTuple);
    }

    private static boolean firingHalted(ActivationsManager activationsManager) {
        return !activationsManager.isFiring();
    }

    private boolean ruleWithHigherSalienceActivated(ActivationsManager activationsManager) {
        // The eager list must be evaluated first, as dynamic salience rules will impact the results of peekNextRule
        activationsManager.evaluateEagerList();
        RuleAgendaItem nextRule = activationsManager.peekNextRule();
        if (nextRule == ruleAgendaItem || nextRule == null) {
            return false;
        }
        return !ruleAgendaItem.getAgendaGroup().equals(nextRule.getAgendaGroup()) || !isHigherSalience(nextRule);
    }

    private static boolean fireLimitReached(int fireCount, int fireLimit, int localFireCount) {
        return fireLimit >= 0 && (localFireCount + fireCount >= fireLimit);
    }

    private boolean isHigherSalience(RuleAgendaItem nextRule) {
        return RuleAgendaConflictResolver.doCompare(ruleAgendaItem, nextRule) >= 0;
    }

    public TupleList getActiveMatches() {
        return activeMatches;
    }

    public LinkedList<TupleImpl> getDormantMatches() {
        return dormantMatches;
    }

    public void addDormantTuple(RuleTerminalNodeLeftTuple tuple) {
        if (DEBUG_DORMANT_TUPLE) {
            if (tuple.isDormant()) {
                throw new IllegalStateException();
            }
        }
        dormantMatches.add(tuple);
        if (DEBUG_DORMANT_TUPLE) {
            tuple.setDormant(true);
        }
    }

    public void removeDormantTuple(RuleTerminalNodeLeftTuple tuple) {
        if (DEBUG_DORMANT_TUPLE) {
            if (!tuple.isDormant()) {
                throw new IllegalStateException();
            }
        }
        if (tuple.getPrevious() != null || dormantMatches.getFirst() == tuple) {
            dormantMatches.remove(tuple);
        } else {
            log.warn("Skipping removeDormantTuple for orphan tuple {} on rule \"{}\" — tuple is not present in dormantMatches",
                      tuple, ruleAgendaItem.getRule().getName());
        }
        if (DEBUG_DORMANT_TUPLE) {
            tuple.setDormant(false);
        }
   }

    public void addActiveTuple(RuleTerminalNodeLeftTuple tuple) {
        tuple.setQueued(true);
        if (DEBUG_DORMANT_TUPLE) {
            if (tuple.isDormant()) {
                throw new IllegalStateException();
            }
        }
        this.activeMatches.add(tuple);
        if (queue != null) {
            addQueuedLeftTuple(tuple);
        }
    }

    public void modifyActiveTuple(RuleTerminalNodeLeftTuple tuple) {
        removeDormantTuple(tuple);
        addActiveTuple(tuple);
    }

    public void removeActiveTuple(RuleTerminalNodeLeftTuple tuple) {
        tuple.setQueued(false);
        activeMatches.remove(tuple);
        if (tuple.getStagedType() != Tuple.DELETE) {
            addDormantTuple(tuple);
        } // if Tuple.DELETE, it's not in activeMatches nor dormantMatches. Basically, unlinked from parent nodes, so will be GC'ed.
          // Note: there could be a case where such a tuple will be retained and activated again. (e.g., accPropCtx.getResultLeftTuple())
        if (queue != null) {
            removeQueuedLeftTuple(tuple);
        }
    }

    public void addQueuedLeftTuple(RuleTerminalNodeLeftTuple tuple) {
        int currentSalience = queue.isEmpty() ? 0 : queue.peek().getSalience();
        queue.enqueue(tuple);
        updateSalience(currentSalience);
    }

    private void removeQueuedLeftTuple(RuleTerminalNodeLeftTuple tuple) {
        int currentSalience = queue.isEmpty() ? 0 : queue.peek().getSalience();
        queue.dequeue(tuple);
        updateSalience(currentSalience);
    }

    private void updateSalience(int currentSalience) {
        // the queue may be empty if no more matches are left, so reset it to default salience 0
        int newSalience = queue.isEmpty() ? SalienceInteger.DEFAULT_SALIENCE.getValue() : queue.peek().getSalience();
        if (currentSalience != newSalience) {
            // salience changed, so the RuleAgendaItem needs to be removed and re-added, for sorting
            ruleAgendaItem.remove();
        }
        if (!ruleAgendaItem.isQueued()) {
            ruleAgendaItem.setSalience(newSalience);
            ruleAgendaItem.getAgendaGroup().add(ruleAgendaItem);
        }
    }

    public void cancel(ReteEvaluator reteEvaluator, EventSupport es) {
        while (!activeMatches.isEmpty()) {
            RuleTerminalNodeLeftTuple rtnLt = (RuleTerminalNodeLeftTuple) activeMatches.removeFirst();
            if (queue != null) {
                queue.dequeue(rtnLt);
            }

            es.getAgendaEventSupport().fireActivationCancelled(rtnLt, reteEvaluator, MatchCancelledCause.CLEAR);
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(final boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDeclarativeAgendaEnabled() {
        return this.declarativeAgendaEnabled;
    }

    public void fireActivation(ReteEvaluator reteEvaluator, ActivationsManager activationsManager, InternalMatch internalMatch) throws ConsequenceException {
        // We do this first as if a node modifies a fact that causes a recursion
        // on an empty pattern
        // we need to make sure it re-activates
        BeforeMatchFiredEvent beforeMatchFiredEvent = activationsManager.getAgendaEventSupport().fireBeforeActivationFired(internalMatch, reteEvaluator);

        if (internalMatch.getActivationGroupNode() != null ) {
            // We know that this rule will cancel all other activations in the group
            // so lets remove the information now, before the consequence fires
            final InternalActivationGroup activationGroup = internalMatch.getActivationGroupNode().getActivationGroup();
            activationGroup.removeActivation(internalMatch);
            activationsManager.clearAndCancelActivationGroup( activationGroup );
        }
        internalMatch.setQueued(false);

        fireActivationEvent(reteEvaluator, activationsManager, internalMatch, internalMatch.getConsequence());
        activationsManager.getAgendaEventSupport().fireAfterActivationFired(internalMatch, reteEvaluator, beforeMatchFiredEvent);
    }

    public void fireConsequenceEvent(ReteEvaluator reteEvaluator, ActivationsManager activationsManager, InternalMatch internalMatch, String consequenceName) {
        Consequence consequence = internalMatch.getRule().getNamedConsequence(consequenceName);
        if (consequence != null) {
            fireActivationEvent(reteEvaluator, activationsManager, internalMatch, consequence);
        }
    }

    private void fireActivationEvent(ReteEvaluator reteEvaluator, ActivationsManager activationsManager, InternalMatch internalMatch, Consequence consequence) {
        KnowledgeHelper knowledgeHelper = activationsManager.getKnowledgeHelper();
        try {
            knowledgeHelper.setActivation(internalMatch);

            if ( log.isTraceEnabled() ) {
                log.trace("Fire event {} for rule \"{}\" \n{}", consequence.getName(), internalMatch.getRule().getName(), internalMatch.getTuple());
            }

            RuleEventListenerSupport ruleEventSupport = reteEvaluator.getRuleEventSupport();
            ruleEventSupport.onBeforeMatchFire(internalMatch);
            consequence.evaluate(knowledgeHelper, reteEvaluator);
            ruleEventSupport.onAfterMatchFire(internalMatch);

            internalMatch.setActive(false);
            knowledgeHelper.reset();
        } catch ( final Exception e ) {
            knowledgeHelper.restoreActivationOnConsequenceFailure(internalMatch);
            activationsManager.handleException(internalMatch, e);
        } finally {
            if (internalMatch.getActivationFactHandle() != null ) {
                // update the Activation in the WM
                InternalFactHandle factHandle = internalMatch.getActivationFactHandle();
                reteEvaluator.getDefaultEntryPoint().getEntryPointNode().modifyActivation(factHandle, internalMatch.getPropagationContext(), reteEvaluator);
            }
        }
    }
}
