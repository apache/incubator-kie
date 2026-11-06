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
package org.drools.commands.fluent;

import org.drools.commands.SetActiveAgendaGroup;
import org.drools.commands.runtime.DisposeCommand;
import org.drools.commands.runtime.GetGlobalCommand;
import org.drools.commands.runtime.rule.FireAllRulesCommand;
import org.drools.commands.runtime.rule.InsertObjectCommand;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.builder.fluent.ExecutableBuilder;
import org.kie.internal.builder.fluent.KieSessionFluent;

public class KieSessionFluentImpl extends BaseBatchWithProcessFluent<KieSessionFluent, ExecutableBuilder> implements KieSessionFluent {

    public KieSessionFluentImpl(ExecutableImpl fluentCtx) {
        super(fluentCtx);
    }

    @Override
    public KieSessionFluent fireAllRules() {
        fluentCtx.addCommand(new FireAllRulesCommand());
        return this;
    }

    @Override
    public KieSessionFluent setGlobal(String identifier, Object object) {
        return this;
    }

    @Override
    public KieSessionFluent getGlobal(String identifier) {
        fluentCtx.addCommand(new GetGlobalCommand(identifier));
        return this;
    }

    @Override
    public KieSessionFluent insert(Object object) {
        fluentCtx.addCommand(new InsertObjectCommand(object));
        return this;
    }

    @Override
    public KieSessionFluent update(FactHandle handle, Object object) {
        return this;
    }

    @Override
    public KieSessionFluent delete(FactHandle handle) {
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This method currently delegates to {@link #setActiveAgendaGroup(String)}
     * and does not fire {@code RuleFlowGroupActivated}/{@code RuleFlowGroupDeactivated} events.
     * A future version will align its behaviour with {@link org.kie.api.runtime.rule.RuleFlowGroup#activate()}.
     */
    @Override
    public KieSessionFluent setActiveRuleFlowGroup(String ruleFlowGroup) {
        return setActiveAgendaGroup(ruleFlowGroup);
    }

    @Override
    public KieSessionFluent setActiveAgendaGroup(String agendaGroup) {
        fluentCtx.addCommand(new SetActiveAgendaGroup(agendaGroup));
        return this;
    }

    @Override
    public ExecutableBuilder dispose() {
        fluentCtx.addCommand(new DisposeCommand());
        return fluentCtx.getExecutableBuilder();
    }
}