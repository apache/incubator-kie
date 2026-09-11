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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.drools.core.base.RuleNameStartsWithAgendaFilter;
import org.drools.core.time.impl.JDKTimerService;
import org.drools.ruleunits.api.DataHandle;
import org.drools.ruleunits.api.DataProcessor;
import org.drools.ruleunits.api.RuleUnitInstance;
import org.drools.ruleunits.api.RuleUnitProvider;
import org.drools.ruleunits.api.conf.ClockType;
import org.drools.ruleunits.api.conf.RuleConfig;
import org.drools.ruleunits.dsl.domain.Cheese;
import org.drools.ruleunits.dsl.domain.Person;
import org.drools.ruleunits.impl.listener.TestAgendaEventListener;
import org.drools.ruleunits.impl.listener.TestRuleEventListener;
import org.drools.ruleunits.impl.listener.TestRuleRuntimeEventListener;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.time.SessionClock;
import org.kie.api.time.SessionPseudoClock;

import static org.assertj.core.api.Assertions.assertThat;

public class RuleUnitsTest {

    @Test
    public void helloWorld() {
        HelloWorldUnit unit = new HelloWorldUnit();
        unit.getStrings().add("Hello World");

        RuleUnitInstance<HelloWorldUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit);

        assertThat(unitInstance.fire()).isEqualTo(2);
        assertThat(unit.getResults()).containsExactlyInAnyOrder("it worked!", "it also worked with HELLO WORLD");

        unit.getResults().clear();
        unit.getInts().add(11);
        assertThat(unitInstance.fire()).isEqualTo(1);
        assertThat(unit.getResults()).containsExactly("String 'Hello World' is 11 characters long");

        unitInstance.close();
    }

    @Test
    public void inference() {
        InferenceUnit unit = new InferenceUnit();
        unit.getStrings().add("test");

        RuleUnitInstance<InferenceUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit);

        assertThat(unitInstance.fire()).isEqualTo(0);

        AtomicBoolean success = new AtomicBoolean(false);
        unit.getStrings().subscribe(new DataProcessor<String>() {
            @Override
            public FactHandle insert(DataHandle handle, String object) {
                if (object.equals("ok")) {
                    success.set(true);
                }
                return null;
            }

            @Override
            public void update(DataHandle handle, String object) {

            }

            @Override
            public void delete(DataHandle handle) {

            }
        });

        unit.getStrings().add("Mario");
        assertThat(unitInstance.fire()).isEqualTo(3);
        assertThat(success.get()).isTrue();

        unitInstance.close();
    }

    @Test
    public void selfJoin() {
        SelfJoinUnit unit = new SelfJoinUnit();
        unit.getStrings().add("abc");
        unit.getStrings().add("bcd");
        unit.getStrings().add("xyz");

        RuleUnitInstance<SelfJoinUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit);
        assertThat(unitInstance.fire()).isEqualTo(1);
        assertThat(unit.getResults()).containsExactly("Found 'abc' and 'bcd'");

        unitInstance.close();
    }

    @Test
    public void accumulate() {
        AccumulateUnit unit = new AccumulateUnit();
        unit.getStrings().add("A1");
        unit.getStrings().add("A123");
        unit.getStrings().add("B12");
        unit.getStrings().add("ABCDEF");
        unit.getStrings().add("BCDEF");
        unit.getStrings().add("Cx");

        RuleUnitInstance<AccumulateUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit);

        int fireNr = unitInstance.fire();
        assertThat(fireNr).isEqualTo(3);
        assertThat(unit.getResults()).containsExactlyInAnyOrder(
                "Sum of length of Strings starting with A is 12",
                "Max length of Strings not starting with A is 5",
                "Count of Strings above threshold is 0"
        );

        unit.getResults().clear();

        unit.getThreshold().set(4);
        fireNr = unitInstance.fire();
        assertThat(fireNr).isEqualTo(2);
        assertThat(unit.getResults()).containsExactlyInAnyOrder(
                "Average length of Strings longer than threshold 4 is 5.5",
                "Count of Strings above threshold is 2"
        );

        unitInstance.close();
    }

    @Test
    public void groupBy() {
        GroupByUnit unit = new GroupByUnit();

        unit.getPersons().add(new Person("Mario", 48));
        unit.getPersons().add(new Person("Matteo", 10));
        unit.getPersons().add(new Person("Marilena", 17));
        unit.getPersons().add(new Person("Edson", 38));
        unit.getPersons().add(new Person("Edoardo", 33));
        unit.getPersons().add(new Person("Mark", 45));
        unit.getPersons().add(new Person("Daniele", 13));

        RuleUnitInstance<GroupByUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit);

        assertThat(unitInstance.fire()).isEqualTo(2);
        assertThat(unit.getResults().keySet()).containsExactlyInAnyOrder("M", "E");
        assertThat(unit.getResults().get("M")).isEqualTo(93);
        assertThat(unit.getResults().get("E")).isEqualTo(71);

        unit.getResults().clear();
        unit.getThreshold().set(12);

        assertThat(unitInstance.fire()).isEqualTo(3);
        assertThat(unit.getResults().keySet()).containsExactlyInAnyOrder("M", "E", "D");
        assertThat((List)unit.getResults().get("M")).containsExactlyInAnyOrder("Mario", "Marilena", "Mark");
        assertThat((List)unit.getResults().get("E")).containsExactlyInAnyOrder("Edson", "Edoardo");
        assertThat((List)unit.getResults().get("D")).containsExactlyInAnyOrder("Daniele");

        unitInstance.close();
    }

    @Test
    public void existential() {
        ExistentialUnit unit = new ExistentialUnit();

        RuleUnitInstance<ExistentialUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit);

        assertThat(unitInstance.fire()).isEqualTo(1);
        assertThat(unit.getResults()).contains("There's no Hello World");
        unit.getResults().clear();

        unit.getStrings().add("test");
        assertThat(unitInstance.fire()).isEqualTo(0);

        unit.getStrings().add("Hello World");
        assertThat(unitInstance.fire()).isEqualTo(1);
        assertThat(unit.getResults()).contains("There is at least one Hello World");
        unit.getResults().clear();

        unit.getStrings().add("Hello World");
        assertThat(unitInstance.fire()).isEqualTo(0);

        unit.getThreshold().set(20);
        assertThat(unitInstance.fire()).isEqualTo(0);

        unit.getStrings().add("This is a very long String");
        assertThat(unitInstance.fire()).isEqualTo(1);
        assertThat(unit.getResults()).contains("There is at least a String longer than threshold 20");

        unitInstance.close();
    }

    @Test
    public void multiJoin() {
        MultiJoinUnit unit = new MultiJoinUnit();

        RuleUnitInstance<MultiJoinUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit);

        unit.getStrings().add("Hello World");
        unit.getInts().add(11);
        unit.getPersons().add(new Person("Sofia", 4));
        unit.getCheeses().add(new Cheese("Gorgonzola", 20));
        unit.getCheeses().add(new Cheese("Mozzarella", 12));

        assertThat(unitInstance.fire()).isEqualTo(0);

        unit.getPersons().add(new Person("Mark", 47));
        assertThat(unitInstance.fire()).isEqualTo(0);

        unit.getPersons().add(new Person("Mario", 48));
        assertThat(unitInstance.fire()).isEqualTo(1);
        assertThat(unit.getResults()).containsExactly("Found Mario who eats Mozzarella");

        unitInstance.close();
    }

    @Test
    public void unitsCoordination() {
        UnitOne unitOne = new UnitOne();
        UnitTwo unitTwo = new UnitTwo(unitOne.getInts());

        RuleUnitInstance<UnitOne> unitInstanceOne = RuleUnitProvider.get().createRuleUnitInstance(unitOne);
        RuleUnitInstance<UnitTwo> unitInstanceTwo = RuleUnitProvider.get().createRuleUnitInstance(unitTwo);

        unitOne.getStrings().add("Hello World");
        assertThat(unitInstanceOne.fire()).isEqualTo(1);
        assertThat(unitInstanceTwo.fire()).isEqualTo(1);
        assertThat(unitTwo.getResults()).containsExactly("Found 11");

        unitInstanceOne.close();
        unitInstanceTwo.close();
    }

    @Test
    public void selfJoinWithInferenceAndNot() {
        SelfJoinWithInferenceAndNotUnit unit = new SelfJoinWithInferenceAndNotUnit();
        unit.getStrings().add("abc");
        unit.getStrings().add("axy");

        RuleUnitInstance<SelfJoinWithInferenceAndNotUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit);
        assertThat(unitInstance.fire()).isEqualTo(1);

        unitInstance.close();
    }

    @Test
    public void logicalAdd() {
        LogicalAddUnit unit = new LogicalAddUnit();
        DataHandle dh = unit.getStrings().add("abc");

        RuleUnitInstance<LogicalAddUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit);

        assertThat(unitInstance.fire()).isEqualTo(2);
        assertThat(unit.getResults()).containsExactly("exists");

        unit.getResults().clear();

        unit.getStrings().remove(dh);
        assertThat(unitInstance.fire()).isEqualTo(1);
        assertThat(unit.getResults()).containsExactly("not exists");

        unitInstance.close();
    }

    @Test
    public void ruleUnitDefinitionReuse() {
        // DROOLS-7181
        SumAccumulateUnit unit = new SumAccumulateUnit();
        RuleUnitInstance<SumAccumulateUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit);
        int fireNr = unitInstance.fire();

        assertThat(fireNr).isEqualTo(1);
        assertThat(unit.getResults()).containsExactly(0);

        SumAccumulateUnit unit2 = new SumAccumulateUnit();
        unit2.getIntegers().add(17);
        RuleUnitInstance<SumAccumulateUnit> unitInstance2 = RuleUnitProvider.get().createRuleUnitInstance(unit2);
        int fireNr2 = unitInstance2.fire();
        assertThat(fireNr2).isEqualTo(1);
        assertThat(unit2.getResults()).containsExactly(Integer.valueOf(17));

        unitInstance.close();
        unitInstance2.close();
    }

    @Test
    public void addEventListeners() {
        TestAgendaEventListener testAgendaEventListener = new TestAgendaEventListener();
        TestRuleRuntimeEventListener testRuleRuntimeEventListener = new TestRuleRuntimeEventListener();
        TestRuleEventListener testRuleEventListener = new TestRuleEventListener();

        RuleConfig ruleConfig = RuleUnitProvider.get().newRuleConfig();
        ruleConfig.getAgendaEventListeners().add(testAgendaEventListener);
        ruleConfig.getRuleRuntimeListeners().add(testRuleRuntimeEventListener);
        ruleConfig.getRuleEventListeners().add(testRuleEventListener);

        HelloWorldUnit unit = new HelloWorldUnit();
        unit.getStrings().add("Hello World");

        try (RuleUnitInstance<HelloWorldUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit, ruleConfig)) {

            assertThat(unitInstance.fire()).isEqualTo(2);
            assertThat(unit.getResults()).containsExactlyInAnyOrder("it worked!", "it also worked with HELLO WORLD");

            assertThat(testAgendaEventListener.getResults()).hasSize(6);
            assertThat(testAgendaEventListener.getResults().get(0)).startsWith("matchCreated");
            assertThat(testAgendaEventListener.getResults().get(1)).startsWith("matchCreated");
            assertThat(testAgendaEventListener.getResults().get(2)).startsWith("beforeMatchFired");
            assertThat(testAgendaEventListener.getResults().get(3)).startsWith("afterMatchFired");
            assertThat(testAgendaEventListener.getResults().get(4)).startsWith("beforeMatchFired");
            assertThat(testAgendaEventListener.getResults().get(5)).startsWith("afterMatchFired");
            assertThat(testRuleRuntimeEventListener.getResults()).containsExactly("objectInserted : Hello World");
            assertThat(testRuleEventListener.getResults()).hasSize(4);
            assertThat(testRuleEventListener.getResults().get(0)).startsWith("onBeforeMatchFire");
            assertThat(testRuleEventListener.getResults().get(1)).startsWith("onAfterMatchFire");
            assertThat(testRuleEventListener.getResults().get(2)).startsWith("onBeforeMatchFire");
            assertThat(testRuleEventListener.getResults().get(3)).startsWith("onAfterMatchFire");
        }
    }

    @Test
    public void pseudoClockDoesNotAffectSubsequentDefaultInstance() {
        HelloWorldUnit unit1 = new HelloWorldUnit();
        unit1.getStrings().add("Hello World");

        RuleConfig pseudoConfig = RuleUnitProvider.get().newRuleConfig();
        pseudoConfig.setClockType(ClockType.PSEUDO);

        try (RuleUnitInstance<HelloWorldUnit> pseudoInstance = RuleUnitProvider.get().createRuleUnitInstance(unit1, pseudoConfig)) {
            SessionClock clock1 = pseudoInstance.getClock();
            assertThat(clock1).isInstanceOf(SessionPseudoClock.class);
        }

        HelloWorldUnit unit2 = new HelloWorldUnit();
        unit2.getStrings().add("Hello World");

        try (RuleUnitInstance<HelloWorldUnit> defaultInstance = RuleUnitProvider.get().createRuleUnitInstance(unit2)) {
            SessionClock clock2 = defaultInstance.getClock();
            assertThat(clock2).isNotInstanceOf(SessionPseudoClock.class);
            assertThat(clock2).isInstanceOf(JDKTimerService.class);
        }
    }

    @Test
    public void fireWithAgendaFilter() {
        RuleNameUnit unit = new RuleNameUnit();
        unit.getStrings().add("Hello World");

        try (RuleUnitInstance<RuleNameUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(unit)) {
            assertThat(unitInstance.fire(new RuleNameStartsWithAgendaFilter("GoodBye"))).isEqualTo(1);
            assertThat(unit.getResults()).containsExactly("GoodByeWorld");
        }
    }
}
