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
package org.drools.ruleunits.dsl;

import org.drools.core.ClockType;
import org.drools.core.SessionConfiguration;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.core.reteoo.ReteDumper;
import org.drools.model.Model;
import org.drools.modelcompiler.KieBaseBuilder;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.RuleUnit;
import org.drools.ruleunits.api.RuleUnitData;
import org.drools.ruleunits.api.RuleUnitInstance;
import org.drools.ruleunits.api.conf.RuleConfig;
import org.drools.ruleunits.impl.EntryPointDataProcessor;
import org.drools.ruleunits.impl.ReteEvaluatorBasedRuleUnitInstance;
import org.drools.ruleunits.impl.RuleUnitProviderImpl;
import org.drools.ruleunits.impl.factory.AbstractRuleUnit;
import org.drools.ruleunits.impl.sessions.RuleUnitExecutorImpl;
import org.kie.api.runtime.rule.EntryPoint;

import java.util.Map;

public class RuleUnitProviderForDSL extends RuleUnitProviderImpl {

    private static final boolean DUMP_GENERATED_RETE = false;

    @Override
    public int servicePriority() {
        return 1;
    }

    @Override
    protected <T extends RuleUnitData> Map<String, RuleUnit> generateRuleUnit(T ruleUnitData) {
        if (ruleUnitData instanceof RuleUnitDefinition) {
            RuleUnitDefinition ruleUnitDef = (RuleUnitDefinition) ruleUnitData;
            RulesFactory rulesFactory = new RulesFactory(ruleUnitDef);
            ruleUnitDef.defineRules(rulesFactory);
            RuleUnit<T> ruleUnit = new ModelRuleUnit<>((Class<T>) ruleUnitData.getClass(), rulesFactory.toModel(), rulesFactory.getUnitGlobalsResolver());
            return Map.of(getRuleUnitName( ruleUnitData ), ruleUnit);
        }
        return super.generateRuleUnit(ruleUnitData);
    }

    public static class ModelRuleUnit<T extends RuleUnitData> extends AbstractRuleUnit<T> {

        private final UnitGlobalsResolver unitGlobalsResolver;
        private final InternalRuleBase ruleBase;

        public ModelRuleUnit(Class<T> type, Model model, UnitGlobalsResolver unitGlobalsResolver) {
            super(type);
            this.unitGlobalsResolver = unitGlobalsResolver;
            this.ruleBase = KieBaseBuilder.createKieBaseFromModel( model );
            if (DUMP_GENERATED_RETE) {
                ReteDumper.dumpRete(this.ruleBase);
            }
        }

        @Override
        public RuleUnitInstance<T> internalCreateInstance(T data, RuleConfig ruleConfig) {
            ReteEvaluator reteEvaluator;
            org.drools.ruleunits.api.conf.ClockType clockType = ruleConfig.getClockType();
            if (clockType != null) {
                SessionConfiguration sessionConfiguration = RuleBaseFactory.newKnowledgeSessionConfiguration().as(SessionConfiguration.KEY);
                sessionConfiguration.setClockType(clockType == org.drools.ruleunits.api.conf.ClockType.PSEUDO ? ClockType.PSEUDO_CLOCK : ClockType.REALTIME_CLOCK);
                reteEvaluator = new RuleUnitExecutorImpl(ruleBase, sessionConfiguration);
            } else {
                reteEvaluator = new RuleUnitExecutorImpl(ruleBase);
            }
            return new DSLRuleUnitInstance<>(this, data, reteEvaluator, unitGlobalsResolver, ruleConfig);
        }
    }

    public static class DSLRuleUnitInstance<T extends RuleUnitData> extends ReteEvaluatorBasedRuleUnitInstance<T> {

        public DSLRuleUnitInstance(RuleUnit<T> unit, T workingMemory, ReteEvaluator reteEvaluator, UnitGlobalsResolver unitGlobalsResolver) {
            super(unit, workingMemory, reteEvaluator);
            internalBind(unitGlobalsResolver);
        }

        public DSLRuleUnitInstance(RuleUnit<T> unit, T workingMemory, ReteEvaluator reteEvaluator, UnitGlobalsResolver unitGlobalsResolver, RuleConfig ruleConfig) {
            super(unit, workingMemory, reteEvaluator, ruleConfig);
            internalBind(unitGlobalsResolver);
        }

        protected void bind(ReteEvaluator reteEvaluator, T workingMemory) {
            // empty to allow a subsequent bind also using the RulesContext
        }

        private void internalBind(UnitGlobalsResolver unitGlobalsResolver) {
            for (String dataSourceName : unitGlobalsResolver.getGlobalNames()) {
                Object v = unitGlobalsResolver.resolveGlobalObject(ruleUnitData(), dataSourceName);
                if (v instanceof DataSource) {
                    DataSource<?> o = (DataSource<?>) v;
                    EntryPoint ep = getEvaluator().getEntryPoint(dataSourceName);
                    if (ep != null) { // can be null if this DataSource isn't used in the LHS of any rule
                        o.subscribe(new EntryPointDataProcessor(ep));
                    }
                }
                try {
                    getEvaluator().setGlobal(dataSourceName, v);
                } catch (RuntimeException e) {
                    // ignore if the global doesn't exist
                }
            }
        }
    }
}
