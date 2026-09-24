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

    @Test
    public void afterNoArg() {
        StreamAfterNoArgUnit unit = new StreamAfterNoArgUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamAfterNoArgUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("DROO"));
            clock.advanceTime(1, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("ACME"));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void afterSingleBound() {
        StreamAfterSingleBoundUnit unit = new StreamAfterSingleBoundUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamAfterSingleBoundUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("DROO"));
            clock.advanceTime(5, TimeUnit.SECONDS);
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

    @Test
    public void beforeNoArg() {
        StreamBeforeNoArgUnit unit = new StreamBeforeNoArgUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamBeforeNoArgUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("ACME"));
            clock.advanceTime(1, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("DROO"));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void beforeSingleBound() {
        StreamBeforeSingleBoundUnit unit = new StreamBeforeSingleBoundUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamBeforeSingleBoundUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("ACME"));
            clock.advanceTime(5, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("DROO"));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    // --- Temporal constraint: coincides ---

    @Test
    public void coincidesExactMatch() {
        StreamCoincidesUnit unit = new StreamCoincidesUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamCoincidesUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            unit.getStockTicks().append(new StockTick("ACME", 5000));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void coincidesDoesNotMatchWhenDifferent() {
        StreamCoincidesUnit unit = new StreamCoincidesUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamCoincidesUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            clock.advanceTime(100, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 5000));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
        }
    }

    @Test
    public void coincidesWithDevMatchesWithinDeviation() {
        StreamCoincidesDevUnit unit = new StreamCoincidesDevUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamCoincidesDevUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            // start deviation 500ms <= 1s dev, duration diff 0 <= 1s dev
            clock.advanceTime(500, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 5000));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void coincidesWithDevDoesNotMatchWhenExceedingDeviation() {
        StreamCoincidesDevUnit unit = new StreamCoincidesDevUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamCoincidesDevUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            // start deviation 1500ms > 1s dev
            clock.advanceTime(1500, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 5000));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
        }
    }

    @Test
    public void coincidesWithStartEndDevMatchesWithinDeviation() {
        StreamCoincidesStartEndDevUnit unit = new StreamCoincidesStartEndDevUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamCoincidesStartEndDevUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            // start dev 500ms <= 1s startDev, end dev 1500ms <= 2s endDev (DROO ends at 5s, ACME ends at 0.5s+6s = 6.5s)
            clock.advanceTime(500, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 6000));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void coincidesWithStartEndDevDoesNotMatchWhenExceedingDeviation() {
        StreamCoincidesStartEndDevUnit unit = new StreamCoincidesStartEndDevUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamCoincidesStartEndDevUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            // start dev 500ms <= 1s, but end dev = |(0.5s + 8s) - 5s| = 3.5s > 2s endDev
            clock.advanceTime(500, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 8000));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
        }
    }

    // --- Temporal constraint: during / includes ---

    @Test
    public void duringMatches() {
        StreamDuringUnit unit = new StreamDuringUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamDuringUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // A starts at 0, duration 10s (ends at 10s)
            unit.getStockTicks().append(new StockTick("DROO", 10000));
            // B starts at 2s, duration 4s (ends at 6s) -> start dist = 2s in [1s, 10s], end dist = 4s in [1s, 10s]
            clock.advanceTime(2, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 4000));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void duringDoesNotMatchWhenNotContained() {
        StreamDuringUnit unit = new StreamDuringUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamDuringUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // A starts at 0, duration 5s (ends at 5s)
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            // B starts at 2s, duration 5s (ends at 7s > end(A)) -> not during A
            clock.advanceTime(2, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 5000));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
        }
    }

    @Test
    public void includesMatches() {
        StreamIncludesUnit unit = new StreamIncludesUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamIncludesUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // B (ACME) starts at 0 with 10s duration (ends at 10s)
            unit.getStockTicks().append(new StockTick("ACME", 10000));
            // A (DROO) starts at 2s with 4s duration (ends at 6s) -> start dist = 2s in [1s, 10s], end dist = 4s in [1s, 10s]
            clock.advanceTime(2, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("DROO", 4000));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void includesDoesNotMatchWhenNotIncluding() {
        StreamIncludesUnit unit = new StreamIncludesUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamIncludesUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // B (ACME) starts at 0 with 5s duration (ends at 5s)
            unit.getStockTicks().append(new StockTick("ACME", 5000));
            // A (DROO) starts at 2s with 5s duration (ends at 7s > end(B)) -> B does not include A
            clock.advanceTime(2, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
        }
    }

    // --- Temporal constraint: overlaps / overlappedby ---

    @Test
    public void overlapsMatches() {
        StreamOverlapsUnit unit = new StreamOverlapsUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamOverlapsUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // B (ACME) starts at 0s, duration 5s (ends at 5s)
            unit.getStockTicks().append(new StockTick("ACME", 5000));
            // A (DROO) starts at 3s, duration 5s (ends at 8s)
            // B starts before A (0 < 3), B ends after A starts but before A ends (3 < 5 < 8)
            // overlap dist = end(B) - start(A) = 5 - 3 = 2s in [1s, 5s]
            clock.advanceTime(3, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void overlapsDoesNotMatchWhenNoOverlap() {
        StreamOverlapsUnit unit = new StreamOverlapsUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamOverlapsUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // B (ACME) starts at 0s, duration 2s (ends at 2s)
            unit.getStockTicks().append(new StockTick("ACME", 2000));
            // A (DROO) starts at 3s, duration 5s (ends at 8s) -> end(B) < start(A), no overlap
            clock.advanceTime(3, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
        }
    }

    @Test
    public void overlappedbyMatches() {
        StreamOverlappedbyUnit unit = new StreamOverlappedbyUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamOverlappedbyUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // A (DROO) starts at 0s, duration 5s (ends at 5s)
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            // B (ACME) starts at 3s, duration 5s (ends at 8s)
            // B starts after A starts but before A ends (0 < 3 < 5), B ends after A (8 > 5)
            // overlap dist = end(A) - start(B) = 5 - 3 = 2s in [1s, 5s]
            clock.advanceTime(3, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 5000));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void overlappedbyDoesNotMatchWhenNoOverlap() {
        StreamOverlappedbyUnit unit = new StreamOverlappedbyUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamOverlappedbyUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // A (DROO) starts at 0s, duration 2s (ends at 2s)
            unit.getStockTicks().append(new StockTick("DROO", 2000));
            // B (ACME) starts at 3s, duration 5s (ends at 8s) -> start(B) > end(A), no overlap
            clock.advanceTime(3, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 5000));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
        }
    }

    // --- Temporal constraint: meets / metby ---

    @Test
    public void meetsMatchesWithinDeviation() {
        StreamMeetsUnit unit = new StreamMeetsUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamMeetsUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // B (ACME) starts at 0, duration 3s (ends at 3s)
            unit.getStockTicks().append(new StockTick("ACME", 3000));
            // A (DROO) starts at 3.5s -> |start(A) - end(B)| = 500ms <= 1s dev
            clock.advanceTime(3500, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("DROO", 3000));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void meetsDoesNotMatchWhenExceedingDeviation() {
        StreamMeetsUnit unit = new StreamMeetsUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamMeetsUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // B (ACME) starts at 0, duration 3s (ends at 3s)
            unit.getStockTicks().append(new StockTick("ACME", 3000));
            // A (DROO) starts at 5s -> |start(A) - end(B)| = 2s > 1s dev
            clock.advanceTime(5, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("DROO", 3000));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
        }
    }

    @Test
    public void metbyMatchesWithinDeviation() {
        StreamMetbyUnit unit = new StreamMetbyUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamMetbyUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // A (DROO) starts at 0, duration 3s (ends at 3s)
            unit.getStockTicks().append(new StockTick("DROO", 3000));
            // B (ACME) starts at 3.5s -> |start(B) - end(A)| = 500ms <= 1s dev
            clock.advanceTime(3500, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 3000));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void metbyDoesNotMatchWhenExceedingDeviation() {
        StreamMetbyUnit unit = new StreamMetbyUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamMetbyUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // A (DROO) starts at 0, duration 3s (ends at 3s)
            unit.getStockTicks().append(new StockTick("DROO", 3000));
            // B (ACME) starts at 5s -> |start(B) - end(A)| = 2s > 1s dev
            clock.advanceTime(5, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 3000));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
        }
    }

    // --- Temporal constraint: starts / startedby ---

    @Test
    public void startsMatchesWithinDeviation() {
        StreamStartsUnit unit = new StreamStartsUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamStartsUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // A (DROO) starts at 0, duration 5s (ends at 5s)
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            // B (ACME) starts at 500ms (diff 500ms <= 1s dev), duration 3s (ends at 3.5s < 5s)
            clock.advanceTime(500, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 3000));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void startsDoesNotMatchWhenExceedingDeviation() {
        StreamStartsUnit unit = new StreamStartsUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamStartsUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // A (DROO) starts at 0, duration 5s (ends at 5s)
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            // B (ACME) starts at 1.5s (diff 1.5s > 1s dev)
            clock.advanceTime(1500, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 2000));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
        }
    }

    @Test
    public void startedbyMatchesWithinDeviation() {
        StreamStartedbyUnit unit = new StreamStartedbyUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamStartedbyUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // A (DROO) starts at 0, duration 3s (ends at 3s)
            unit.getStockTicks().append(new StockTick("DROO", 3000));
            // B (ACME) starts at 500ms (diff 500ms <= 1s dev), duration 5s (ends at 5.5s > 3s)
            clock.advanceTime(500, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 5000));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void startedbyDoesNotMatchWhenExceedingDeviation() {
        StreamStartedbyUnit unit = new StreamStartedbyUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamStartedbyUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // A (DROO) starts at 0, duration 3s (ends at 3s)
            unit.getStockTicks().append(new StockTick("DROO", 3000));
            // B (ACME) starts at 1.5s (diff 1.5s > 1s dev)
            clock.advanceTime(1500, TimeUnit.MILLISECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 5000));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
        }
    }

    // --- Temporal constraint: finishes / finishedby ---

    @Test
    public void finishesMatchesWithinDeviation() {
        StreamFinishesUnit unit = new StreamFinishesUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamFinishesUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // A (DROO) starts at 0, duration 5s (ends at 5s)
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            // B (ACME) starts at 2s, duration 3.5s (ends at 5.5s, |end(B) - end(A)| = 500ms <= 1s dev)
            clock.advanceTime(2, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 3500));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void finishesDoesNotMatchWhenExceedingDeviation() {
        StreamFinishesUnit unit = new StreamFinishesUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamFinishesUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // A (DROO) starts at 0, duration 5s (ends at 5s)
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            // B (ACME) starts at 2s, duration 5s (ends at 7s, |end(B) - end(A)| = 2s > 1s dev)
            clock.advanceTime(2, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("ACME", 5000));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
        }
    }

    @Test
    public void finishedbyMatchesWithinDeviation() {
        StreamFinishedbyUnit unit = new StreamFinishedbyUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamFinishedbyUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // B (ACME) starts at 0, duration 5s (ends at 5s)
            unit.getStockTicks().append(new StockTick("ACME", 5000));
            // A (DROO) starts at 2s, duration 3.5s (ends at 5.5s, |end(B) - end(A)| = 500ms <= 1s dev)
            clock.advanceTime(2, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("DROO", 3500));
            instance.fire();

            assertThat(unit.getResults()).hasSize(1);
            assertThat(unit.getResults().get(0).getCompany()).isEqualTo("ACME");
        }
    }

    @Test
    public void finishedbyDoesNotMatchWhenExceedingDeviation() {
        StreamFinishedbyUnit unit = new StreamFinishedbyUnit();
        RuleConfig config = RuleUnitProvider.get().newRuleConfig();
        config.setClockType(ClockType.PSEUDO);
        try (RuleUnitInstance<StreamFinishedbyUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit, config)) {
            SessionPseudoClock clock = instance.getClock();
            // B (ACME) starts at 0, duration 5s (ends at 5s)
            unit.getStockTicks().append(new StockTick("ACME", 5000));
            // A (DROO) starts at 2s, duration 5s (ends at 7s, |end(B) - end(A)| = 2s > 1s dev)
            clock.advanceTime(2, TimeUnit.SECONDS);
            unit.getStockTicks().append(new StockTick("DROO", 5000));
            instance.fire();

            assertThat(unit.getResults()).isEmpty();
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
    public static class StreamAfterNoArgUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamAfterNoArgUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME after DROO no-arg")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .after()
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamAfterSingleBoundUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamAfterSingleBoundUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME after DROO single bound")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .after(4, TimeUnit.SECONDS)
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
    public static class StreamBeforeNoArgUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamBeforeNoArgUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME before DROO no-arg")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .before()
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamBeforeSingleBoundUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamBeforeSingleBoundUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME before DROO single bound")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .before(4, TimeUnit.SECONDS)
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamCoincidesUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamCoincidesUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME coincides DROO")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .coincides()
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamCoincidesDevUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamCoincidesDevUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME coincides DROO dev")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .coincides(1, TimeUnit.SECONDS)
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamCoincidesStartEndDevUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamCoincidesStartEndDevUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME coincides DROO start/end dev")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .coincides(1, TimeUnit.SECONDS, 2, TimeUnit.SECONDS)
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamDuringUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamDuringUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME during DROO")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .during(1, TimeUnit.SECONDS, 10, TimeUnit.SECONDS)
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamIncludesUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamIncludesUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME includes DROO")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .includes(1, TimeUnit.SECONDS, 10, TimeUnit.SECONDS)
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamOverlapsUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamOverlapsUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME overlaps DROO")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .overlaps(1, TimeUnit.SECONDS, 5, TimeUnit.SECONDS)
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamOverlappedbyUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamOverlappedbyUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME overlappedby DROO")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .overlappedby(1, TimeUnit.SECONDS, 5, TimeUnit.SECONDS)
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamMeetsUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamMeetsUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME meets DROO")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .meets(1, TimeUnit.SECONDS)
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamMetbyUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamMetbyUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME metby DROO")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .metby(1, TimeUnit.SECONDS)
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamStartsUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamStartsUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME starts DROO")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .starts(1, TimeUnit.SECONDS)
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamStartedbyUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamStartedbyUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME startedby DROO")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .startedby(1, TimeUnit.SECONDS)
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamFinishesUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamFinishesUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME finishes DROO")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .finishes(1, TimeUnit.SECONDS)
                    .execute(results, (r, droo, acme) -> r.add(acme));
        }
    }

    @EventProcessing(EventProcessingType.STREAM)
    public static class StreamFinishedbyUnit implements RuleUnitDefinition {
        private final DataStream<StockTick> stockTicks;
        private final List<StockTick> results = new ArrayList<>();

        public StreamFinishedbyUnit() {
            this.stockTicks = DataSource.createStream();
        }

        public DataStream<StockTick> getStockTicks() { return stockTicks; }
        public List<StockTick> getResults() { return results; }

        @Override
        public void defineRules(RulesFactory rulesFactory) {
            rulesFactory.rule("ACME finishedby DROO")
                    .on(stockTicks)
                    .filter(StockTick::getCompany, EQUAL, "DROO")
                    .join(rule -> rule.on(stockTicks)
                            .filter(StockTick::getCompany, EQUAL, "ACME"))
                    .finishedby(1, TimeUnit.SECONDS)
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
