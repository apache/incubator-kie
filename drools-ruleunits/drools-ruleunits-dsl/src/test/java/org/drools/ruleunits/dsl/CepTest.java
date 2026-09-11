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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.ReteEvaluator;
import org.drools.model.Index;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStream;
import org.drools.ruleunits.api.RuleUnitInstance;
import org.drools.ruleunits.api.RuleUnitProvider;
import org.drools.ruleunits.api.conf.ClockType;
import org.drools.ruleunits.api.conf.EventProcessing;
import org.drools.ruleunits.api.conf.EventProcessingType;
import org.drools.ruleunits.api.conf.RuleConfig;
import org.drools.ruleunits.dsl.domain.StockTick;
import org.drools.ruleunits.impl.AbstractRuleUnitInstance;
import org.junit.jupiter.api.Test;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.time.SessionPseudoClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.drools.model.Index.ConstraintType.EQUAL;
import static org.drools.ruleunits.dsl.Accumulators.sum;

public class CepTest {

    // --- Configuration tests ---

    @Test
    public void streamAnnotationProducesStreamKieBase() {
        StreamAfterUnit unit = new StreamAfterUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamAfterUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            ReteEvaluator evaluator = getEvaluator(instance);
            assertThat(evaluator.getKnowledgeBase().getRuleBaseConfiguration().getEventProcessingMode())
                    .isEqualTo(EventProcessingOption.STREAM);
        }
    }

    @Test
    public void explicitCloudAnnotationProducesCloudKieBase() {
        ExplicitCloudUnit unit = new ExplicitCloudUnit();
        try (RuleUnitInstance<ExplicitCloudUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit)) {
            ReteEvaluator evaluator = getEvaluator(instance);
            assertThat(evaluator.getKnowledgeBase().getRuleBaseConfiguration().getEventProcessingMode())
                    .isEqualTo(EventProcessingOption.CLOUD);
        }
    }

    @Test
    public void unannotatedUnitDefaultsToCloud() {
        UnannotatedUnit unit = new UnannotatedUnit();
        try (RuleUnitInstance<UnannotatedUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit)) {
            ReteEvaluator evaluator = getEvaluator(instance);
            assertThat(evaluator.getKnowledgeBase().getRuleBaseConfiguration().getEventProcessingMode())
                    .isEqualTo(EventProcessingOption.CLOUD);
        }
    }

    // --- Temporal constraint: after ---

    @Test
    public void afterMatchesWithinBounds() {
        StreamAfterUnit unit = new StreamAfterUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamAfterUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("DROO"));
            clock.advanceTime(6, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("ACME"));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void afterDoesNotMatchBelowLowerBound() {
        StreamAfterUnit unit = new StreamAfterUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamAfterUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("DROO"));
            clock.advanceTime(4999, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("ACME"));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
        }
    }

    @Test
    public void afterDoesNotMatchAboveUpperBound() {
        StreamAfterUnit unit = new StreamAfterUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamAfterUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("DROO"));
            clock.advanceTime(8001, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("ACME"));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
        }
    }

    @Test
    public void afterMatchesAtExactLowerBound() {
        StreamAfterUnit unit = new StreamAfterUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamAfterUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("DROO"));
            clock.advanceTime(5000, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("ACME"));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void afterMatchesAtExactUpperBound() {
        StreamAfterUnit unit = new StreamAfterUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamAfterUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("DROO"));
            clock.advanceTime(8000, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("ACME"));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void afterWithNonzeroDurationUsesEndToStart() {
        StreamAfterUnit unit = new StreamAfterUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamAfterUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // DROO starts at 0 with 3s duration, so end(DROO) = 3s
            unit.getStockTicks().append(new StockTick("DROO", 3000));
            // ACME starts at 9s, gap = start(ACME) - end(DROO) = 9s - 3s = 6s, within [5s, 8s]
            clock.advanceTime(9, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("ACME"));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void afterWithMillisecondUnit() {
        StreamAfterMillisUnit unit = new StreamAfterMillisUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamAfterMillisUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("DROO"));
            clock.advanceTime(600, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("ACME"));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    // --- Temporal constraint: before ---

    @Test
    public void beforeMatchesWithinBounds() {
        StreamBeforeUnit unit = new StreamBeforeUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamBeforeUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // ACME occurs first (the "before" event)
            unit.getStockTicks().append(new StockTick("ACME"));
            clock.advanceTime(6, TimeUnit.SECONDS);
            // DROO occurs second
            unit.getStockTicks().append(new StockTick("DROO"));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void beforeDoesNotMatchOutsideBounds() {
        StreamBeforeUnit unit = new StreamBeforeUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamBeforeUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("ACME"));
            clock.advanceTime(4999, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("DROO"));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
        }
    }

    @Test
    public void beforeWithNonzeroDurationUsesEndToStart() {
        StreamBeforeUnit unit = new StreamBeforeUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamBeforeUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // ACME starts at 0 with 3s duration, so end(ACME) = 3s
            unit.getStockTicks().append(new StockTick("ACME", 3000));
            // DROO starts at 9s, gap = start(DROO) - end(ACME) = 9s - 3s = 6s, within [5s, 8s]
            clock.advanceTime(9, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("DROO"));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    // --- Explicit expiration ---

    @Test
    public void expiresRemovesEventAfterDuration() {
        StreamExpirationUnit unit = new StreamExpirationUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamExpirationUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("DROO"));
            instance.fire();

            EntryPoint entryPoint = findNonDefaultEntryPoint(instance);
            assertThat(entryPoint.getFactHandles()).hasSize(1);

            clock.advanceTime(11, TimeUnit.SECONDS);
            instance.fire();

            assertThat(entryPoint.getFactHandles()).isEmpty();
        }
    }

    // --- Combined CEP scenario ---

    @Test
    public void combinedCepScenario() {
        StreamAfterUnit unit = new StreamAfterUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamAfterUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();

            // 1. Append a zero-duration DROO event at time zero
            unit.getStockTicks().append(new StockTick("DROO"));

            // 2. Advance 6 seconds and append ACME
            clock.advanceTime(6, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("ACME"));

            // 3. Fire and assert the after(5, 8, SECONDS) rule records ACME
            instance.fire();
            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");

            // 4. Both events should still be in the entry point
            EntryPoint entryPoint = findNonDefaultEntryPoint(instance);
            assertThat(entryPoint.getFactHandles()).hasSize(2);

            // 5. Advance another 5 seconds (total = 11s) and fire
            clock.advanceTime(5, TimeUnit.SECONDS);
            instance.fire();

            // 6. DROO was inserted at t=0 with @Expires("10s"), so it should be expired
            //    ACME was inserted at t=6 with @Expires("10s"), so it should still be present
            Collection<FactHandle> remaining = entryPoint.getFactHandles();
            assertThat(remaining).hasSize(1);
            assertThat(((StockTick) ((InternalFactHandle) remaining.iterator().next()).getObject()).getCompany())
                    .isEqualTo("ACME");
        }
    }

    // --- Unsupported aggregate calls ---

    @Test
    public void afterOnGroupByThrowsUnsupported() {
        assertThatThrownBy(() -> {
            new GroupByTemporalUnit();
        }).isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("groupBy");
    }

    @Test
    public void afterOnAccumulateThrowsUnsupported() {
        assertThatThrownBy(() -> {
            new AccumulateTemporalUnit();
        }).isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("accumulate");
    }

    // --- Helpers ---

    @SuppressWarnings("rawtypes")
    private ReteEvaluator getEvaluator(RuleUnitInstance<?> instance) {
        return (ReteEvaluator) ((AbstractRuleUnitInstance) instance).getEvaluator();
    }

    private EntryPoint findNonDefaultEntryPoint(RuleUnitInstance<?> instance) {
        ReteEvaluator evaluator = getEvaluator(instance);
        for (EntryPoint ep : evaluator.getEntryPoints()) {
            if (!"DEFAULT".equals(ep.getEntryPointId())) {
                return ep;
            }
        }
        throw new IllegalStateException("No non-DEFAULT entry point found");
    }

    // --- Unit definitions ---

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamAfterUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamAfterUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            // A=$a : /stockTicks [ company == "DROO" ]
            // B=$b : /stockTicks [ company == "ACME", this after[5s,8s] $a ]
            // → B after A: gap = start(B) - end(A) must be in [5s, 8s]
            rulesFactory.rule("ACME after DROO")
                    .on(stockTicks)                                      // pattern A
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)                    // pattern B
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .after(5, 8, TimeUnit.SECONDS)                      // B after A
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamAfterMillisUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamAfterMillisUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            // Same as StreamAfterUnit but with millisecond bounds [500ms, 1000ms]
            rulesFactory.rule("ACME after DROO millis")
                    .on(stockTicks)                                      // pattern A
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)                    // pattern B
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .after(500, 1000, TimeUnit.MILLISECONDS)             // B after A
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamBeforeUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamBeforeUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            // A=$a : /stockTicks [ company == "DROO" ]
            // B=$b : /stockTicks [ company == "ACME", this before[5s,8s] $a ]
            // → B before A: gap = start(A) - end(B) must be in [5s, 8s]
            rulesFactory.rule("ACME before DROO")
                    .on(stockTicks)                                      // pattern A
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)                    // pattern B
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .before(5, 8, TimeUnit.SECONDS)                      // B before A
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamExpirationUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamExpirationUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("collect all")
                    .on(stockTicks)
                    .execute(results, (r, tick) -> r.add(tick));
        }
    }

    @EventProcessing(EventProcessingType.CLOUD)
    public static class ExplicitCloudUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public ExplicitCloudUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("collect all")
                    .on(stockTicks)
                    .execute(results, (r, tick) -> r.add(tick));
        }
    }

    public static class UnannotatedUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public UnannotatedUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("collect all")
                    .on(stockTicks)
                    .execute(results, (r, tick) -> r.add(tick));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class GroupByTemporalUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<Integer> results = new ArrayList<>();

        public GroupByTemporalUnit() {
            this.stockTicks = DataSource.createStream();
            defineRules(new RulesFactory(this));
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<Integer> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("group temporal")
                    .groupBy(rule -> rule.on(stockTicks),
                            (StockTick t) -> t.getCompany(),
                            sum(StockTick::getDuration))
                    .after(5, 8, TimeUnit.SECONDS);
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class AccumulateTemporalUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<Integer> results = new ArrayList<>();

        public AccumulateTemporalUnit() {
            this.stockTicks = DataSource.createStream();
            defineRules(new RulesFactory(this));
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<Integer> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("accumulate temporal")
                    .on(stockTicks)
                    .accumulate(rule -> rule.on(stockTicks),
                            sum(StockTick::getDuration))
                    .after(5, 8, TimeUnit.SECONDS);
        }
    }
}
