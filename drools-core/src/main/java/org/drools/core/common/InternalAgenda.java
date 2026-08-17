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

import java.util.Iterator;
import java.util.Map;

import org.drools.base.phreak.PropagationEntry;
import org.kie.api.runtime.rule.Agenda;
import org.kie.api.runtime.rule.AgendaFilter;

public interface InternalAgenda extends Agenda, ActivationsManager {

    /**
     * Returns the WorkignMemory for this Agenda
     * @return
     *      The WorkingMemory
     */
    InternalWorkingMemory getWorkingMemory();

    /**
     * Sets the Agenda's focus to the specified AgendaGroup
     *
     * @return true if the AgendaGroup is changed
     */
    boolean setFocus(String name);

    /**
     * Activates the <code>RuleFlowGroup</code> with the given name.
     * All activations in the given <code>RuleFlowGroup</code> are added to the agenda.
     * As long as the <code>RuleFlowGroup</code> remains active,
     * its activations are automatically added to the agenda.
     */
    void activateRuleFlowGroup(String name);

    /**
     * Activates the <code>RuleFlowGroup</code> with the given name.
     * All activations in the given <code>RuleFlowGroup</code> are added to the agenda.
     * As long as the <code>RuleFlowGroup</code> remains active,
     * its activations are automatically added to the agenda.
     * The given processInstanceId and nodeInstanceId define the process context
     * in which this <code>RuleFlowGroup</code> is used.
     */
    void activateRuleFlowGroup(String name, String processInstanceId, String nodeInstanceId);

    /**
     * Clears all Activations from the Agenda
     */
    void clearAndCancel();

    /**
     * Clears all Activations from an Agenda Group. Any Activations that are also in an Xor Group are removed the
     * the Xor Group.
     */
    void clearAndCancelAgendaGroup(String name);

    /**
     * Clears all Activations from an Activation-Group. Any Activations that are also in an Agenda Group are removed
     * from the Agenda Group.
     */
    void clearAndCancelActivationGroup(String name);

    void clearAndCancelRuleFlowGroup(final String name);

    /**
     * Returns the name of the agenda group that currently
     * has the focus
     */
    String getFocusName();

    boolean isDeclarativeAgenda();

    /**
     * Fires all activations currently in agenda that match the given agendaFilter
     * until the fireLimit is reached or no more activations exist.
     *
     *
     * @param agendaFilter the filter on which activations may fire.
     * @param fireLimit the maximum number of activations that may fire. If -1, then it will
     *                  fire until no more activations exist.
     *
     * @return the number of rules that were actually fired
     */
    int fireAllRules(AgendaFilter agendaFilter,
                            int fireLimit);

    /**
     * Stop agenda from firing any other rule. It will finish the current rule
     * execution though.
     */
    void halt();

    /**
     * Keeps firing activations until a halt is called. If in a given moment, there is
     * no activation to fire, it will wait for an activation to be added to an active
     * agenda group or rule flow group.
     */
    void fireUntilHalt();

    /**
     * Keeps firing activations until a halt is called. If in a given moment, there is
     * no activation to fire, it will wait for an activation to be added to an active
     * agenda group or rule flow group.
     *
     * @param agendaFilter filters the activations that may fire
     */
    void fireUntilHalt(AgendaFilter agendaFilter);

    boolean dispose(InternalWorkingMemory wm);

    boolean isAlive();

    void reset();

    /**
     * Sets a filter that prevents activations from being added to
     * the agenda.
     */
    void setActivationsFilter( ActivationsFilter filter );

    void executeFlush();

    void activate();
    void deactivate();
    boolean tryDeactivate();

    Map<String,InternalActivationGroup> getActivationGroupsMap();

    int sizeOfRuleFlowGroup(String s);

    boolean isRuleActiveInRuleFlowGroup(String ruleflowGroupName, String ruleName, String processInstanceId);

    void notifyWaitOnRest();
    Iterator<PropagationEntry<ReteEvaluator>> getActionsIterator();
    boolean hasPendingPropagations();

    boolean isParallelAgenda();
}
