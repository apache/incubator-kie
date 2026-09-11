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
package org.drools.ruleunits.dsl.attribute;

import java.util.concurrent.TimeUnit;

import org.drools.core.common.ReteEvaluator;
import org.drools.ruleunits.api.RuleUnitInstance;
import org.drools.ruleunits.api.RuleUnitProvider;
import org.drools.ruleunits.api.conf.ClockType;
import org.drools.ruleunits.api.conf.RuleConfig;
import org.drools.ruleunits.dsl.domain.Person;
import org.drools.ruleunits.impl.AbstractRuleUnitInstance;
import org.junit.jupiter.api.Test;
import org.kie.api.time.SessionPseudoClock;

import static org.assertj.core.api.Assertions.assertThat;

public class RuleUnitsAttributesTest {

    @Test
    public void timer() {
        TimerUnit unit = new TimerUnit();
        unit.getStrings().add("Hello Timer");

        RuleConfig ruleConfig = RuleUnitProvider.get().newRuleConfig();
        ruleConfig.setClockType(ClockType.PSEUDO);

        try (RuleUnitInstance<TimerUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit, ruleConfig)) {
            SessionPseudoClock pseudoClock = unitInstance.getClock();

            assertThat(unitInstance.fire()).isZero();
            assertThat(unit.getResults()).isEmpty();

            pseudoClock.advanceTime(40L, TimeUnit.MINUTES);
            assertThat(unitInstance.fire()).isEqualTo(1);
            assertThat(unit.getResults()).containsExactly("timer fired");
        }
    }

    @Test
    public void calendars() {
        CalendarsUnit unit = new CalendarsUnit();

        RuleConfig ruleConfig = RuleUnitProvider.get().newRuleConfig();
        ruleConfig.setClockType(ClockType.PSEUDO);

        try (RuleUnitInstance<CalendarsUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit, ruleConfig)) {
            SessionPseudoClock pseudoClock = unitInstance.getClock();
            long cutoff = 60_000L;
            ReteEvaluator evaluator = (ReteEvaluator) ((AbstractRuleUnitInstance) unitInstance).getEvaluator();
            evaluator.getCalendars().set("myCalendar", timestamp -> timestamp >= cutoff);

            unit.getPersons().add(new Person("Mario", 40));
            assertThat(unitInstance.fire()).isZero();
            assertThat(unit.getResults()).isEmpty();

            pseudoClock.advanceTime(1L, TimeUnit.MINUTES);
            unit.getPersons().add(new Person("Toshiya", 50));
            assertThat(unitInstance.fire()).isEqualTo(1);
            assertThat(unit.getResults()).containsExactly("calendar fired: Toshiya");
        }
    }

    @Test
    public void dateEffectiveAndDateExpires() {
        DateAttributeUnit unit = new DateAttributeUnit();
        unit.getStrings().add("Hello World");

        try (RuleUnitInstance<DateAttributeUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit)) {
            assertThat(unitInstance.fire()).isEqualTo(1);
            assertThat(unit.getResults()).containsExactly("active");
        }
    }

    @Test
    public void activationGroup() {
        ActivationGroupUnit unit = new ActivationGroupUnit();
        unit.getStrings().add("Hello World");

        try (RuleUnitInstance<ActivationGroupUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit)) {
            assertThat(unitInstance.fire()).isEqualTo(1);
            assertThat(unit.getResults()).containsExactly("groupHigh");
        }
    }

    @Test
    public void enabled() {
        EnabledUnit unit = new EnabledUnit();
        unit.getStrings().add("Hello World");

        try (RuleUnitInstance<EnabledUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit)) {
            assertThat(unitInstance.fire()).isEqualTo(1);
            assertThat(unit.getResults()).containsExactly("enabled");
        }
    }

    @Test
    public void noLoop() {
        NoLoopUnit unit = new NoLoopUnit();
        unit.getPersons().add(new Person("Mario", 40));

        try (RuleUnitInstance<NoLoopUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit)) {
            assertThat(unitInstance.fire()).isEqualTo(1);
            assertThat(unit.getResults()).containsExactly("fired-40");
        }
    }

    @Test
    public void salience() {
        SalienceUnit unit = new SalienceUnit();
        unit.getStrings().add("Hello World");

        try (RuleUnitInstance<SalienceUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit)) {
            assertThat(unitInstance.fire()).isEqualTo(2);
            assertThat(unit.getResults()).containsExactly("high", "low");
        }
    }
}
