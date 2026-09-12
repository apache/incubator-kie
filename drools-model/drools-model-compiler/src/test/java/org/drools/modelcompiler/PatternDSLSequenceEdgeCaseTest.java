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
import org.drools.modelcompiler.domain.Result;
import org.drools.modelcompiler.domain.SensorEvents.AlarmRaised;
import org.drools.modelcompiler.domain.SensorEvents.HeartbeatOk;
import org.drools.modelcompiler.domain.SensorEvents.MonitoringStation;
import org.drools.modelcompiler.domain.SensorEvents.SensorActivated;
import org.drools.modelcompiler.domain.Toy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.drools.model.DSL.declarationOf;
import static org.drools.model.DSL.execute;
import static org.drools.model.PatternDSL.and;
import static org.drools.model.PatternDSL.on;
import static org.drools.model.PatternDSL.or;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;
import static org.drools.model.PatternDSL.when;

public class PatternDSLSequenceEdgeCaseTest {

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

    @Test
    public void consequenceBranchDoesNotFallThroughToSequence() {
        Variable<Result>       resultV = declarationOf(Result.class);
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("c1-rule").build(
                pattern(resultV),
                pattern(personV),
                when("cond", personV, p -> p.getAge() < 30).then(
                        on(personV, resultV).breaking().execute((p, r) -> r.setValue("young"))
                ).elseWhen().then(
                        on(personV, resultV).breaking().execute((p, r) -> r.setValue("other"))
                ),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("seq-fired"))
        );

        KieBase kieBase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        assertThat(kieBase).isNotNull();
    }

    @Test
    public void longSequenceDoesNotOverflowCircularBuffer() {
        Variable<Person> personV = declarationOf(Person.class);
        Variable<Toy>    toyV    = declarationOf(Toy.class);

        Rule rule = rule("c2-rule").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball"))
                ),
                execute(() -> results.add("fired"))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        Person anchor = new Person("anchor");
        FactHandle anchorHandle = ksession.insert(anchor);
        ksession.fireAllRules();

        for (int i = 0; i < 110; i++) {
            ksession.insert(new Toy("ball"));
            ksession.fireAllRules();                          // sequence completes
            ksession.update(anchorHandle, anchor);            // restart sequencer
            ksession.fireAllRules();
        }

        assertThat(results).hasSize(110);
    }

    @Test
    public void retractAnchorWithOrStepDoesNotLeakSiblingAdapters() {
        Variable<MonitoringStation> stationV  = declarationOf(MonitoringStation.class);
        Variable<SensorActivated>   activatedV = declarationOf(SensorActivated.class);
        Variable<HeartbeatOk>       heartbeatV = declarationOf(HeartbeatOk.class);
        Variable<AlarmRaised>       alarmV     = declarationOf(AlarmRaised.class);

        Rule rule = rule("c3-rule").build(
                pattern(stationV),
                sequence(
                        pattern(activatedV).expr("s1", a -> a.getSensorId().equals("s1")),
                        or(
                                pattern(heartbeatV).expr("hb", h -> h.getSensorId().equals("s1")),
                                pattern(alarmV).expr("al", al -> al.getSeverity().equals("high"))
                        )
                ),
                execute(() -> results.add("seq-fired"))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        FactHandle stationHandle = ksession.insert(new MonitoringStation("station-1"));
        ksession.fireAllRules();                              // sequencer starts

        ksession.insert(new SensorActivated("s1"));
        ksession.fireAllRules();                              // step-1 consumed; OR step now active

        // Retract the anchor — stop() must deactivate BOTH OR siblings
        ksession.retract(stationHandle);
        ksession.fireAllRules();

        // Insert a heartbeat — the leaked OR sibling adapter must NOT respond
        ksession.insert(new HeartbeatOk("s1"));
        ksession.fireAllRules();

        assertThat(results).isEmpty();
    }

    @Test
    public void retractAnchorMidAndStepDoesNotLeakChildAdapters() {
        Variable<MonitoringStation> stationV   = declarationOf(MonitoringStation.class);
        Variable<SensorActivated>   activatedV = declarationOf(SensorActivated.class);
        Variable<HeartbeatOk>       heartbeatV = declarationOf(HeartbeatOk.class);
        Variable<AlarmRaised>       alarmV     = declarationOf(AlarmRaised.class);

        Rule rule = rule("c3b-rule").build(
                pattern(stationV),
                sequence(
                        pattern(activatedV).expr("s1", a -> a.getSensorId().equals("s1")),
                        and(
                                pattern(heartbeatV).expr("hb", h -> h.getSensorId().equals("s1")),
                                pattern(alarmV).expr("al", al -> al.getSeverity().equals("high"))
                        )
                ),
                execute(() -> results.add("seq-fired"))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        FactHandle stationHandle = ksession.insert(new MonitoringStation("station-1"));
        ksession.fireAllRules();                              // sequencer starts

        ksession.insert(new SensorActivated("s1"));
        ksession.fireAllRules();                              // step-1 consumed; AND step now active

        // Satisfy the first AND child only — AND step is now partially satisfied
        ksession.insert(new HeartbeatOk("s1"));
        ksession.fireAllRules();
        assertThat(results).isEmpty();                        // still waiting for alarm

        // Retract the anchor — stop() must deactivate BOTH AND children
        ksession.retract(stationHandle);
        ksession.fireAllRules();

        // Insert the second AND child — the AND adapter must NOT respond
        ksession.insert(new AlarmRaised("s1", "high"));
        ksession.fireAllRules();

        assertThat(results).isEmpty();
    }

    @Test
    public void twoAnchorsOrStepOnlyOneAnchorSatisfied() {
        Variable<MonitoringStation> stationV   = declarationOf(MonitoringStation.class);
        Variable<SensorActivated>   activatedAV = declarationOf(SensorActivated.class);
        Variable<SensorActivated>   activatedBV = declarationOf(SensorActivated.class);
        Variable<HeartbeatOk>       heartbeatV  = declarationOf(HeartbeatOk.class);
        Variable<AlarmRaised>       alarmV      = declarationOf(AlarmRaised.class);

        // Rule anchored on MonitoringStation: sequence is SensorActivated → or(Heartbeat, Alarm)
        Rule rule = rule("c5-rule").build(
                pattern(stationV),
                sequence(
                        pattern(activatedAV).expr("s1-act", a -> a.getSensorId().equals("s1")),
                        or(
                                pattern(heartbeatV).expr("hb", h -> h.getSensorId().equals("s1")),
                                pattern(alarmV).expr("al", al -> al.getSeverity().equals("high"))
                        )
                ),
                execute(() -> results.add("seq-fired"))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        // Start two independent sequencers via two separate anchor tuples
        ksession.insert(new MonitoringStation("station-A"));
        ksession.insert(new MonitoringStation("station-B"));
        ksession.fireAllRules();

        // Both sequencers consume step-1 (SensorActivated s1); both are now at the OR step
        ksession.insert(new SensorActivated("s1"));
        ksession.fireAllRules();
        assertThat(results).isEmpty();                        // OR step not yet satisfied for either

        // OR step satisfied — both sequencers complete; each must fire exactly once
        ksession.insert(new HeartbeatOk("s1"));
        ksession.fireAllRules();

        assertThat(results).hasSize(2);
    }

    @Test
    public void twoSequencesInOneRuleBothComplete() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("c4-two-seq-rule").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball"))
                ),
                sequence(
                        pattern(relV).expr("is-go", r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("fired"))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        ksession.insert(new Person("anchor"));
        ksession.fireAllRules();                        // both sequencers start

        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();                        // first sequence completes

        // Both SequenceNodes have independent state. Completing the first sequence
        // must not affect the second; the rule fires only after both complete.
        ksession.insert(new Relationship("go", "done"));
        ksession.fireAllRules();                        // second sequence completes

        assertThat(results).containsExactly("fired");
    }

    @Test
    public void twoRulesWithSequencesTrackIndependently() {
        Variable<MonitoringStation> stationAV   = declarationOf(MonitoringStation.class);
        Variable<MonitoringStation> stationBV   = declarationOf(MonitoringStation.class);
        Variable<SensorActivated>   activated1V = declarationOf(SensorActivated.class);
        Variable<SensorActivated>   activated2V = declarationOf(SensorActivated.class);
        Variable<HeartbeatOk>       heartbeat1V = declarationOf(HeartbeatOk.class);
        Variable<HeartbeatOk>       heartbeat2V = declarationOf(HeartbeatOk.class);

        // Rule 1: station-A → sequence(sensorActivated(s1) → heartbeat(s1))
        Rule rule1 = rule("c4-rule-1").build(
                pattern(stationAV).expr("is-a", s -> s.getId().equals("station-A")),
                sequence(
                        pattern(activated1V).expr("s1-act", a -> a.getSensorId().equals("s1")),
                        pattern(heartbeat1V).expr("s1-hb",  h -> h.getSensorId().equals("s1"))
                ),
                execute(() -> results.add("rule-1-fired"))
        );

        // Rule 2: station-B → sequence(sensorActivated(s2) → heartbeat(s2))
        Rule rule2 = rule("c4-rule-2").build(
                pattern(stationBV).expr("is-b", s -> s.getId().equals("station-B")),
                sequence(
                        pattern(activated2V).expr("s2-act", a -> a.getSensorId().equals("s2")),
                        pattern(heartbeat2V).expr("s2-hb",  h -> h.getSensorId().equals("s2"))
                ),
                execute(() -> results.add("rule-2-fired"))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(
                new ModelImpl().addRule(rule1).addRule(rule2));
        ksession = kbase.newKieSession();

        // Start both sequencers
        ksession.insert(new MonitoringStation("station-A"));
        ksession.insert(new MonitoringStation("station-B"));
        ksession.fireAllRules();

        // Complete rule-1's sequence
        ksession.insert(new SensorActivated("s1"));
        ksession.fireAllRules();
        ksession.insert(new HeartbeatOk("s1"));
        ksession.fireAllRules();

        // Complete rule-2's sequence
        ksession.insert(new SensorActivated("s2"));
        ksession.fireAllRules();
        ksession.insert(new HeartbeatOk("s2"));
        ksession.fireAllRules();

        // Both rules must have fired exactly once
        assertThat(results).containsExactlyInAnyOrder("rule-1-fired", "rule-2-fired");
    }

    @Test
    public void twoRulesWithSharedAnchorAndDifferentSequencesFireIndependently() {
        // Both rules anchor on an undecorated pattern(person) — they will share the same
        // LeftInputAdapterNode, which is the minimal condition to trigger the node-reuse bug.
        Variable<Person>       personV1 = declarationOf(Person.class);
        Variable<Person>       personV2 = declarationOf(Person.class);
        Variable<Toy>          toyV1    = declarationOf(Toy.class);
        Variable<Toy>          toyV2    = declarationOf(Toy.class);
        Variable<Relationship> relV1    = declarationOf(Relationship.class);
        Variable<Relationship> relV2    = declarationOf(Relationship.class);

        // Rule 1: sequence(toy named "ball" → relationship starting "go")
        Rule rule1 = rule("shared-anchor-seq-1").build(
                pattern(personV1),
                sequence(
                        pattern(toyV1).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV1).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("rule-1-fired"))
        );

        // Rule 2: same anchor type, completely different step predicates.
        // With the bug, this SequenceNode is discarded and rule-2 is wired to rule-1's
        // Sequencer, so it never fires its own consequence.
        Rule rule2 = rule("shared-anchor-seq-2").build(
                pattern(personV2),
                sequence(
                        pattern(toyV2).expr("is-robot", t -> t.getName().equals("robot")),
                        pattern(relV2).expr("is-stop",  r -> r.getStart().equals("stop"))
                ),
                execute(() -> results.add("rule-2-fired"))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(
                new ModelImpl().addRule(rule1).addRule(rule2));
        ksession = kbase.newKieSession();

        // Insert the shared anchor — starts both sequencers
        ksession.insert(new Person("anchor"));
        ksession.fireAllRules();

        // Complete rule-1's sequence only
        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();
        ksession.insert(new Relationship("go", "done"));
        ksession.fireAllRules();

        assertThat(results)
                .as("rule-1 must fire after its own sequence completes")
                .contains("rule-1-fired");
        assertThat(results)
                .as("rule-2 must not fire — its step predicates were not satisfied")
                .doesNotContain("rule-2-fired");

        // Now complete rule-2's sequence
        ksession.insert(new Toy("robot"));
        ksession.fireAllRules();
        ksession.insert(new Relationship("stop", "done"));
        ksession.fireAllRules();

        assertThat(results)
                .as("rule-2 must fire after its own sequence completes")
                .containsExactlyInAnyOrder("rule-1-fired", "rule-2-fired");
    }

    @Test
    public void betaExprInSequenceStepFiltersAgainstAnchor() {
        Variable<Person> personV = declarationOf(Person.class);
        Variable<Toy>    toyV    = declarationOf(Toy.class);

        Rule rule = rule("beta-in-seq").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("name-matches-anchor", personV,
                                (t, p) -> t.getName().equals(p.getName()))
                ),
                on(personV).execute(p -> results.add("fired:" + p.getName()))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        ksession.insert(new Person("alice"));
        ksession.fireAllRules();

        ksession.insert(new Toy("bob"));     // name does NOT match — must not fire
        ksession.fireAllRules();
        assertThat(results).isEmpty();

        ksession.insert(new Toy("alice"));   // name matches — must fire
        ksession.fireAllRules();
        assertThat(results).containsExactly("fired:alice");
    }

    @Test
    public void betaExprInSequenceStepOnlyMatchesOwnAnchor() {
        Variable<Person> personV = declarationOf(Person.class);
        Variable<Toy>    toyV    = declarationOf(Toy.class);

        Rule rule = rule("beta-in-seq-multi-anchor").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("name-matches-anchor", personV,
                                (t, p) -> t.getName().equals(p.getName()))
                ),
                on(personV).execute(p -> results.add("fired:" + p.getName()))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule));
        ksession = kbase.newKieSession();

        ksession.insert(new Person("alice"));
        ksession.insert(new Person("bob"));
        ksession.fireAllRules();   // both sequencers start

        ksession.insert(new Toy("alice"));   // only alice's sequencer should advance
        ksession.fireAllRules();
        assertThat(results).containsExactly("fired:alice");

        ksession.insert(new Toy("bob"));     // only bob's sequencer should advance
        ksession.fireAllRules();
        assertThat(results).containsExactlyInAnyOrder("fired:alice", "fired:bob");
    }

    @Test
    public void twoRulesWithIdenticalSequenceSegmentBothFire() {
        // Two rules share the exact same anchor type and identical step predicates.
        // When the sequence fires, smem.getPathMemories() contains two PathMemory entries
        // (one per rule). SequencerMemoryImpl.match() flushes only get(0), so only
        // rule-1's consequence is triggered — rule-2 is silently dropped.
        Variable<Person>       personV1 = declarationOf(Person.class);
        Variable<Person>       personV2 = declarationOf(Person.class);
        Variable<Toy>          toyV1    = declarationOf(Toy.class);
        Variable<Toy>          toyV2    = declarationOf(Toy.class);

        Rule rule1 = rule("shared-seg-1").build(
                pattern(personV1),
                sequence(
                        pattern(toyV1).expr("is-ball-1", t -> t.getName().equals("ball"))
                ),
                execute(() -> results.add("rule-1-fired"))
        );

        Rule rule2 = rule("shared-seg-2").build(
                pattern(personV2),
                sequence(
                        pattern(toyV2).expr("is-ball-2", t -> t.getName().equals("ball"))
                ),
                execute(() -> results.add("rule-2-fired"))
        );

        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(
                new ModelImpl().addRule(rule1).addRule(rule2));
        ksession = kbase.newKieSession();

        ksession.insert(new Person("anchor"));
        ksession.fireAllRules();           // both sequencers start

        ksession.insert(new Toy("ball"));
        ksession.fireAllRules();           // sequence completes — both rules must fire

        assertThat(results)
                .as("both rules whose sequences completed must fire")
                .containsExactlyInAnyOrder("rule-1-fired", "rule-2-fired");
    }

    @Test
    public void emptySequenceThrowsIllegalArgument() {
        assertThatThrownBy(() -> sequence())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one step");
    }

    @Test
    public void nullStepsThrowsIllegalArgument() {
        assertThatThrownBy(() -> sequence((org.drools.model.SequenceStep[]) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one step");
    }
}
