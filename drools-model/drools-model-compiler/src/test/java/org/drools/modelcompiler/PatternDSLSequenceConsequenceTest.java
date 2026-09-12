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
package org.drools.modelcompiler;

import java.util.ArrayList;
import java.util.List;

import org.drools.model.Rule;
import org.drools.model.Variable;
import org.drools.model.impl.ModelImpl;
import org.drools.modelcompiler.domain.Person;
import org.drools.modelcompiler.domain.Relationship;
import org.drools.modelcompiler.domain.SensorEvents.AlarmRaised;
import org.drools.modelcompiler.domain.SensorEvents.HeartbeatOk;
import org.drools.modelcompiler.domain.SensorEvents.MonitoringStation;
import org.drools.modelcompiler.domain.SensorEvents.SensorActivated;
import org.drools.modelcompiler.domain.Toy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.model.DSL.declarationOf;
import static org.drools.model.PatternDSL.on;
import static org.drools.model.PatternDSL.or;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;
import static org.drools.model.PatternDSL.when;

public class PatternDSLSequenceConsequenceTest {

    private final List<String> results = new ArrayList<>();
    private KieSession ksession;

    @AfterEach
    public void tearDown() {
        results.clear();
        if (ksession != null) {
            ksession.dispose();
            ksession = null;
        }
    }

    private void insertAndFire(Object... facts) {
        for (Object f : facts) { ksession.insert(f); }
        ksession.fireAllRules();
    }

    @Test
    public void anchorVariableBoundInConsequence() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("anchor-binding-rule").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                on(personV).execute(p -> results.add("fired:" + p.getName()))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        insertAndFire(new Person("alice"));
        insertAndFire(new Toy("ball"));
        insertAndFire(new Relationship("go", "done"));

        assertThat(results).containsExactly("fired:alice");
    }

    @Test
    public void stepVariableBoundInConsequence() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("step-binding-rule").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                on(toyV, relV).execute((t, r) -> results.add(t.getName() + "+" + r.getStart()))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("ball"));
        insertAndFire(new Relationship("go", "done"));

        assertThat(results).containsExactly("ball+go");
    }

    @Test
    public void namedConsequenceAnchorVariableIsAccessible() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("named-consequence-anchor").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                when("cond", personV, p -> p.getAge() < 50).then(
                        on(personV).breaking().execute(p -> results.add("young:" + p.getName()))
                ).elseWhen().then(
                        on(personV).breaking().execute(p -> results.add("old:" + p.getName()))
                )
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        insertAndFire(new Person("alice", 25));
        insertAndFire(new Toy("ball"));
        insertAndFire(new Relationship("go", "done"));

        assertThat(results).containsExactly("young:alice");
    }

    @Test
    public void namedConsequenceStepVariablesAreAccessible() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("named-consequence-step-vars").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                when("cond", personV, p -> p.getAge() < 50).then(
                        on(toyV, relV).breaking().execute((t, r) ->
                                results.add("named:" + t.getName() + "+" + r.getStart()))
                ).elseWhen().then(
                        on(toyV, relV).breaking().execute((t, r) ->
                                results.add("named-old:" + t.getName() + "+" + r.getStart()))
                )
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        insertAndFire(new Person("alice", 25));
        insertAndFire(new Toy("ball"));
        insertAndFire(new Relationship("go", "done"));

        assertThat(results).containsExactly("named:ball+go");
    }

    @Test
    public void stepVarsResolvedWhenPatternChainsAfterSequence() {
        Variable<Person>       personV  = declarationOf(Person.class);
        Variable<Toy>          toyV     = declarationOf(Toy.class);
        Variable<Relationship> relV     = declarationOf(Relationship.class);
        Variable<Person>       extra    = declarationOf(Person.class);

        Rule rule = rule("step-vars-after-chain").build(
                pattern(personV).expr("is-anchor", p -> p.getName().equals("anchor")),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                // extra pattern chained AFTER the sequence
                pattern(extra).expr("is-observer", p -> p.getName().equals("observer")),
                on(toyV, relV, extra).execute((t, r, p) ->
                        results.add(t.getName() + "+" + r.getStart() + "+" + p.getName()))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        // Insert the extra fact first so it is available when the sequence completes
        insertAndFire(new Person("observer"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("ball"));
        insertAndFire(new Relationship("go", "done"));

        assertThat(results).containsExactly("ball+go+observer");
    }

    @Test
    public void stepVariableConsequenceFiredTwice() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("step-binding-fires-twice").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                on(toyV, relV).execute((t, r) -> results.add(t.getName() + "+" + r.getStart()))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        // Two anchors — rule fires once per anchor, second fire hits fetchFacts()
        insertAndFire(new Person("alice"));
        insertAndFire(new Person("bob"));
        insertAndFire(new Toy("ball"));
        insertAndFire(new Relationship("go", "done"));

        assertThat(results).containsExactlyInAnyOrder("ball+go", "ball+go");
    }

    @Test
    public void stepVariableWithDroolsInConsequence() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("step-binding-with-drools").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                on(toyV).execute((drools, t) -> results.add(t.getName()))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("ball"));
        insertAndFire(new Relationship("go", "done"));

        assertThat(results).containsExactly("ball");
    }

    @Test
    public void orStepSecondBranchVariableIsAccessibleInConsequence() {
        Variable<MonitoringStation> stationV   = declarationOf(MonitoringStation.class);
        Variable<SensorActivated>   activatedV = declarationOf(SensorActivated.class);
        Variable<HeartbeatOk>       heartbeatV = declarationOf(HeartbeatOk.class);
        Variable<AlarmRaised>       alarmV     = declarationOf(AlarmRaised.class);

        Rule rule = rule("or-step-second-branch").build(
                pattern(stationV),
                sequence(
                        pattern(activatedV).expr("act", a -> a.getSensorId().equals("s1")),
                        or(
                                pattern(heartbeatV).expr("hb", h -> h.getSensorId().equals("s1")),
                                pattern(alarmV).expr("hi",     al -> al.getSeverity().equals("high"))
                        )
                ),
                on(alarmV).execute(al -> results.add("alarm:" + al.getSeverity()))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        insertAndFire(new MonitoringStation("s1"));
        insertAndFire(new SensorActivated("s1"));
        insertAndFire(new AlarmRaised("s1", "high"));

        assertThat(results).containsExactly("alarm:high");
    }

    @Test
    public void orStepFirstBranchVariableIsAccessibleInConsequence() {
        Variable<MonitoringStation> stationV   = declarationOf(MonitoringStation.class);
        Variable<SensorActivated>   activatedV = declarationOf(SensorActivated.class);
        Variable<HeartbeatOk>       heartbeatV = declarationOf(HeartbeatOk.class);
        Variable<AlarmRaised>       alarmV     = declarationOf(AlarmRaised.class);

        Rule rule = rule("or-step-first-branch").build(
                pattern(stationV),
                sequence(
                        pattern(activatedV).expr("act", a -> a.getSensorId().equals("s1")),
                        or(
                                pattern(heartbeatV).expr("hb", h -> h.getSensorId().equals("s1")),
                                pattern(alarmV).expr("hi",     al -> al.getSeverity().equals("high"))
                        )
                ),
                on(heartbeatV).execute(h -> results.add("heartbeat:" + h.getSensorId()))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        insertAndFire(new MonitoringStation("s1"));
        insertAndFire(new SensorActivated("s1"));
        insertAndFire(new HeartbeatOk("s1"));   // first OR child — filterIndex 1, appended at position 1

        assertThat(results).containsExactly("heartbeat:s1");
    }

    @Test
    public void twoSequencesInOneRuleStepVariablesResolveFromCorrectMemory() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);     // seq1, filterIndex=0
        Variable<Relationship> relV    = declarationOf(Relationship.class); // seq2, filterIndex=0 (collision)

        Rule rule = rule("two-sequences-variable-collision").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball"))
                ),
                sequence(
                        pattern(relV).expr("is-go", r -> r.getStart().equals("go"))
                ),
                on(toyV, relV).execute((t, r) -> results.add(t.getName() + "+" + r.getStart()))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("ball"));
        insertAndFire(new Relationship("go", "done"));

        assertThat(results).containsExactly("ball+go");
    }

    @Test
    public void twoSequencesInOneRuleMultipleAnchorsBothFire() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("two-sequences-multiple-anchors").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball"))
                ),
                sequence(
                        pattern(relV).expr("is-go", r -> r.getStart().equals("go"))
                ),
                on(personV, toyV, relV).execute((p, t, r) -> results.add(p.getName() + ":" + t.getName() + "+" + r.getStart()))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        // Multiple anchors (Person)
        insertAndFire(new Person("alice"));
        insertAndFire(new Person("bob"));
        insertAndFire(new Toy("ball"));
        insertAndFire(new Relationship("go", "done"));

        assertThat(results).containsExactlyInAnyOrder(
                "alice:ball+go",
                "bob:ball+go"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"single-anchor", "two-anchors", "three-anchors"})
    public void twoSequencesInOneRuleMultipleAnchorsAndBindings(String mode) {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("two-sequences-" + mode).build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball"))
                ),
                sequence(
                        pattern(relV).expr("is-go", r -> r.getStart().equals("go"))
                ),
                on(personV, toyV, relV).execute((p, t, r) -> results.add(p.getName() + ":" + t.getName() + "+" + r.getStart()))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        switch (mode) {
            case "single-anchor":
                insertAndFire(new Person("alice"));
                insertAndFire(new Toy("ball"));
                insertAndFire(new Relationship("go", "done"));
                assertThat(results).containsExactly("alice:ball+go");
                break;
            case "two-anchors":
                insertAndFire(new Person("alice"));
                insertAndFire(new Person("bob"));
                insertAndFire(new Toy("ball"));
                insertAndFire(new Relationship("go", "done"));
                assertThat(results).containsExactlyInAnyOrder("alice:ball+go", "bob:ball+go");
                break;
            case "three-anchors":
                insertAndFire(new Person("alice"));
                insertAndFire(new Person("bob"));
                insertAndFire(new Person("charlie"));
                insertAndFire(new Toy("ball"));
                insertAndFire(new Relationship("go", "done"));
                assertThat(results).containsExactlyInAnyOrder("alice:ball+go", "bob:ball+go", "charlie:ball+go");
                break;
        }
    }

    @Test
    public void stepVariableResolvedWhenLhsHasFirstLevelOr() {
        Variable<Person>       personV  = declarationOf(Person.class);
        Variable<Toy>          toyV     = declarationOf(Toy.class);
        Variable<Relationship> relV     = declarationOf(Relationship.class);

        Rule rule = rule("step-var-with-first-level-or").build(
                or(
                        pattern(personV).expr("is-alice", p -> p.getName().equals("alice")),
                        pattern(personV).expr("is-bob",   p -> p.getName().equals("bob"))
                ),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                on(toyV, relV).execute((t, r) -> results.add(t.getName() + "+" + r.getStart()))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        insertAndFire(new Person("alice"));
        insertAndFire(new Toy("ball"));
        insertAndFire(new Relationship("go", "done"));

        assertThat(results).containsExactly("ball+go");
    }
}
