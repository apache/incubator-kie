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

import org.drools.base.common.NetworkNode;
import org.drools.core.event.AgendaEventSupport;
import org.drools.core.phreak.ExecutableEntry;
import org.drools.base.phreak.PropagationEntry;
import org.drools.core.phreak.RuleAgendaItem;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.RuleTerminalNodeLeftTuple;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.core.rule.consequence.KnowledgeHelper;
import org.kie.api.runtime.rule.AgendaFilter;

public interface ActivationsManager {

    String ON_BEFORE_ALL_FIRES_CONSEQUENCE_NAME = "$onBeforeAllFire$";
    String ON_AFTER_ALL_FIRES_CONSEQUENCE_NAME = "$onAfterAllFire$";
    String ON_DELETE_MATCH_CONSEQUENCE_NAME = "$onDeleteMatch$";

    AgendaGroupsManager getAgendaGroupsManager();

    AgendaEventSupport getAgendaEventSupport();

    ActivationsFilter getActivationsFilter();

    void addEagerRuleAgendaItem(RuleAgendaItem item);
    void removeEagerRuleAgendaItem(RuleAgendaItem item);

    void addQueryAgendaItem(final RuleAgendaItem item);
    void removeQueryAgendaItem(final RuleAgendaItem item);

    void registerExpiration(PropagationContext expirationContext);

    void clearAndCancelActivationGroup(String name);
    void clearAndCancelActivationGroup(InternalActivationGroup activationGroup);

    RuleAgendaItem createRuleAgendaItem(int salience, PathMemory pathMemory, TerminalNode rtn);

    InternalMatch createAgendaItem(RuleTerminalNodeLeftTuple rtnLeftTuple,
                                   int salience,
                                   PropagationContext context,
                                   RuleAgendaItem ruleAgendaItem,
                                   InternalAgendaGroup agendaGroup);

    void cancelActivation(final InternalMatch internalMatch);

    void addItemToActivationGroup(InternalMatch internalMatch);

    RuleAgendaItem peekNextRule();

    void flushPropagations();

    boolean isFiring();

    void evaluateEagerList();
    void evaluateQueriesForRule(RuleAgendaItem item);

    KnowledgeHelper getKnowledgeHelper();
    void resetKnowledgeHelper();

    void haltGroupEvaluation();

    void executeTask(ExecutableEntry executableEntry);

    default void handleException(InternalMatch internalMatch, Exception e) {
        throw new RuntimeException(e);
    }

    int fireAllRules(AgendaFilter agendaFilter, int fireLimit);

    void addPropagation(PropagationEntry<ReteEvaluator> propagationEntry);

    default void stageLeftTuple(RuleAgendaItem ruleAgendaItem, InternalMatch justified) {
        if (!ruleAgendaItem.isQueued()) {
            ruleAgendaItem.getRuleExecutor().getPathMemory().queueRuleAgendaItem();
        }
        ruleAgendaItem.getRuleExecutor().modifyActiveTuple((RuleTerminalNodeLeftTuple) justified.getTuple() );
    }

    default ActivationsManager getPartitionedAgenda(int partitionNr) {
        return this;
    }

    default ActivationsManager getPartitionedAgendaForNode(NetworkNode node) {
        return this;
    }
}
