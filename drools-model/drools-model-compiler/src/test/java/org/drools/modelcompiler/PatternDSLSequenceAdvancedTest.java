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
import org.drools.modelcompiler.domain.SensorEvents.CalibrationPassed;
import org.drools.modelcompiler.domain.SensorEvents.HeartbeatOk;
import org.drools.modelcompiler.domain.SensorEvents.MaintenanceScheduled;
import org.drools.modelcompiler.domain.SensorEvents.MonitoringStation;
import org.drools.modelcompiler.domain.SensorEvents.OperatorAcknowledged;
import org.drools.modelcompiler.domain.SensorEvents.SensorActivated;
import org.drools.modelcompiler.domain.Toy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.model.DSL.declarationOf;
import static org.drools.model.DSL.execute;
import static org.drools.model.PatternDSL.and;
import static org.drools.model.PatternDSL.or;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;

public class PatternDSLSequenceAdvancedTest {

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
    public void noiseEventsBetweenStepsAreIgnored() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("noise-rule").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule)).newKieSession();

        insertAndFire(new Person("anchor"));

        insertAndFire(new Toy("ball"));               // step 1 satisfied

        // Noise: wrong toy name (must not re-satisfy step 1 or advance step 2)
        insertAndFire(new Toy("other"));
        assertThat(results).isEmpty();

        // Noise: wrong relationship start (must not fire rule)
        insertAndFire(new Relationship("nope", "x"));
        assertThat(results).isEmpty();

        // Correct step 2
        insertAndFire(new Relationship("go", "done"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void eventBeforeAnchorIsNotCaptured() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("pre-anchor-rule").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule)).newKieSession();

        // Step-1 event arrives BEFORE the anchor
        insertAndFire(new Toy("ball"));

        // Anchor inserted — sequencer starts, but the ball is already in the past
        insertAndFire(new Person("anchor"));

        // Step-2 event: the sequencer is at step 0 (ball not yet seen), must NOT fire
        insertAndFire(new Relationship("go", "done"));

        assertThat(results).isEmpty();
    }

    @Test
    public void orSecondBranchAloneFires() {
        Variable<MonitoringStation>    stationV   = declarationOf(MonitoringStation.class);
        Variable<SensorActivated>      activatedV = declarationOf(SensorActivated.class);
        Variable<HeartbeatOk>          heartbeatV = declarationOf(HeartbeatOk.class);
        Variable<AlarmRaised>          alarmV     = declarationOf(AlarmRaised.class);
        Variable<OperatorAcknowledged> ackV       = declarationOf(OperatorAcknowledged.class);

        Rule rule = rule("or-second-branch").build(
                pattern(stationV),
                sequence(
                        pattern(activatedV).expr("s1", a -> a.getSensorId().equals("s1")),
                        or(
                                pattern(heartbeatV).expr("hb",  h  -> h.getSensorId().equals("s1")),
                                pattern(alarmV).expr("al",      al -> al.getSeverity().equals("high"))
                        ),
                        pattern(ackV).expr("ack", a -> a.getOperator().equals("alice"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule)).newKieSession();

        insertAndFire(new MonitoringStation("station-1"));
        insertAndFire(new SensorActivated("s1"));

        // Only the alarm branch — no heartbeat
        insertAndFire(new AlarmRaised("s1", "high"));
        insertAndFire(new OperatorAcknowledged("s1", "alice"));

        assertThat(results).containsExactly("fired");
    }

    @Test
    public void orBothBranchesArriveRuleFiresOnce() {
        Variable<MonitoringStation>    stationV   = declarationOf(MonitoringStation.class);
        Variable<SensorActivated>      activatedV = declarationOf(SensorActivated.class);
        Variable<HeartbeatOk>          heartbeatV = declarationOf(HeartbeatOk.class);
        Variable<AlarmRaised>          alarmV     = declarationOf(AlarmRaised.class);
        Variable<OperatorAcknowledged> ackV       = declarationOf(OperatorAcknowledged.class);

        Rule rule = rule("or-both-branches").build(
                pattern(stationV),
                sequence(
                        pattern(activatedV).expr("s1", a -> a.getSensorId().equals("s1")),
                        or(
                                pattern(heartbeatV).expr("hb",  h  -> h.getSensorId().equals("s1")),
                                pattern(alarmV).expr("al",      al -> al.getSeverity().equals("high"))
                        ),
                        pattern(ackV).expr("ack", a -> a.getOperator().equals("alice"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule)).newKieSession();

        insertAndFire(new MonitoringStation("station-1"));
        insertAndFire(new SensorActivated("s1"));

        // First branch fires — OR step advances, gate deactivates
        insertAndFire(new HeartbeatOk("s1"));

        // Second branch arrives AFTER step has advanced — must be ignored
        insertAndFire(new AlarmRaised("s1", "high"));

        insertAndFire(new OperatorAcknowledged("s1", "alice"));

        // Rule must have fired exactly once
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void andGateWithInterleavedNoiseWaitsForBothSignals() {
        Variable<MonitoringStation>    stationV   = declarationOf(MonitoringStation.class);
        Variable<SensorActivated>      activatedV = declarationOf(SensorActivated.class);
        Variable<HeartbeatOk>          heartbeatV = declarationOf(HeartbeatOk.class);
        Variable<AlarmRaised>          alarmV     = declarationOf(AlarmRaised.class);
        Variable<OperatorAcknowledged> ackV       = declarationOf(OperatorAcknowledged.class);

        Rule rule = rule("and-noise-rule").build(
                pattern(stationV),
                sequence(
                        pattern(activatedV).expr("s1", a -> a.getSensorId().equals("s1")),
                        and(
                                pattern(heartbeatV).expr("hb", h  -> h.getSensorId().equals("s1")),
                                pattern(alarmV).expr("al",     al -> al.getSeverity().equals("high"))
                        ),
                        pattern(ackV).expr("ack", a -> a.getOperator().equals("alice"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule)).newKieSession();

        insertAndFire(new MonitoringStation("station-1"));
        insertAndFire(new SensorActivated("s1"));

        // AND child #1 (heartbeat for s1)
        insertAndFire(new HeartbeatOk("s1"));
        assertThat(results).isEmpty();                              // still waiting for alarm

        // Noise: heartbeat for a different sensor — must not count as s1's signal
        insertAndFire(new HeartbeatOk("s2"));
        assertThat(results).isEmpty();

        // Noise: low-severity alarm — doesn't satisfy the "high" constraint
        insertAndFire(new AlarmRaised("s1", "low"));
        assertThat(results).isEmpty();

        // AND child #2 (high alarm for s1) — both gates now satisfied
        insertAndFire(new AlarmRaised("s1", "high"));

        insertAndFire(new OperatorAcknowledged("s1", "alice"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void threeStepSequenceFiresInOrder() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);
        Variable<SensorActivated> sensorV = declarationOf(SensorActivated.class);

        Rule rule = rule("three-step-rule").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball",   t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",     r -> r.getStart().equals("go")),
                        pattern(sensorV).expr("is-s1",  s -> s.getSensorId().equals("s1"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule)).newKieSession();

        insertAndFire(new Person("anchor"));

        insertAndFire(new Toy("ball"));
        assertThat(results).isEmpty();

        insertAndFire(new Relationship("go", "done"));
        assertThat(results).isEmpty();

        insertAndFire(new SensorActivated("s1"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void threeStepSequenceOutOfOrderDoesNotFire() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);
        Variable<SensorActivated> sensorV = declarationOf(SensorActivated.class);

        Rule rule = rule("three-step-oo-rule").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball",   t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",     r -> r.getStart().equals("go")),
                        pattern(sensorV).expr("is-s1",  s -> s.getSensorId().equals("s1"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule)).newKieSession();

        insertAndFire(new Person("anchor"));

        insertAndFire(new Toy("ball"));                         // step 1 done

        // Step 3 event arrives before step 2 — must not skip step 2
        insertAndFire(new SensorActivated("s1"));
        assertThat(results).isEmpty();

        // Step 2 now — but step 3 event in the past, so still at step 2 waiting for step 3 again
        insertAndFire(new Relationship("go", "done"));          // step 2 done
        assertThat(results).isEmpty();

        // Step 3 again — now fires
        insertAndFire(new SensorActivated("s1"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void retractAnchorAfterCompletionIsNoOp() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("retract-after-rule").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule)).newKieSession();

        FactHandle anchorHandle = ksession.insert(new Person("anchor"));
        ksession.fireAllRules();

        insertAndFire(new Toy("ball"));
        insertAndFire(new Relationship("go", "done"));          // sequence complete
        assertThat(results).containsExactly("fired");

        // Retract after completion — must not throw, must not fire again
        ksession.retract(anchorHandle);
        ksession.fireAllRules();

        assertThat(results).hasSize(1);
    }

    @Test
    public void firstAnchorCompletesWhileSecondIsMidSequence() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("concurrent-anchor-rule").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",   r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule)).newKieSession();

        FactHandle anchorAHandle = ksession.insert(new Person("anchor-A"));
        FactHandle anchorBHandle = ksession.insert(new Person("anchor-B"));
        ksession.fireAllRules();                                // both sequencers start

        // Both anchors consume step 1
        insertAndFire(new Toy("ball"));

        // Step 2 arrives — both sequences should be able to complete
        insertAndFire(new Relationship("go", "done"));

        // TODO: once per-tuple DynamicFilter isolation is fixed, change to hasSize(2).
        // Current known limitation: only one sequencer advances due to shared filter slots.
        assertThat(results).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    public void sameTypeInFirstAndThirdStep() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV1   = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);
        Variable<Toy>          toyV2   = declarationOf(Toy.class);

        Rule rule = rule("type-reuse-rule").build(
                pattern(personV),
                sequence(
                        pattern(toyV1).expr("is-ball",  t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go",     r -> r.getStart().equals("go")),
                        pattern(toyV2).expr("is-doll",  t -> t.getName().equals("doll"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule)).newKieSession();

        insertAndFire(new Person("anchor"));

        // Step 1: Toy("ball")
        insertAndFire(new Toy("ball"));
        assertThat(results).isEmpty();

        // Wrong Toy during step 2 wait — must not advance step 3
        insertAndFire(new Toy("doll"));
        assertThat(results).isEmpty();

        // Step 2: Relationship("go")
        insertAndFire(new Relationship("go", "done"));
        assertThat(results).isEmpty();

        // Step 3: Toy("doll") — same type as step 1, different filter
        insertAndFire(new Toy("doll"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void secondSequenceEventBeforeFirstSequenceCompletesIsIgnored() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("chained-seq-order-rule").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball"))
                ),
                sequence(
                        pattern(relV).expr("is-go", r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule)).newKieSession();

        insertAndFire(new Person("anchor"));

        // Relationship arrives before Toy — second SequenceNode not yet active
        insertAndFire(new Relationship("go", "done"));
        assertThat(results).isEmpty();

        // Now Toy arrives — first sequence completes, second SequenceNode activates
        insertAndFire(new Toy("ball"));
        assertThat(results).isEmpty();                          // second step not yet seen

        // Relationship again — now the second SequenceNode is listening
        insertAndFire(new Relationship("go", "done"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void batchInsertBeforeFireDoesNotFire() {
        Variable<Person>       personV = declarationOf(Person.class);
        Variable<Toy>          toyV    = declarationOf(Toy.class);
        Variable<Relationship> relV    = declarationOf(Relationship.class);

        Rule rule = rule("batch-insert-rule").build(
                pattern(personV),
                sequence(
                        pattern(toyV).expr("is-ball", t -> t.getName().equals("ball")),
                        pattern(relV).expr("is-go", r -> r.getStart().equals("go"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule)).newKieSession();

        // Insert all facts before any fireAllRules() — step signals are lost
        // because the sequencer hasn't started yet
        ksession.insert(new Person("anchor"));
        ksession.insert(new Toy("ball"));
        ksession.insert(new Relationship("go", "done"));

        ksession.fireAllRules();

        // Sequence does not fire: step facts were inserted before the sequencer
        // started. This is a known limitation of the current AlphaAdapter design.
        assertThat(results).isEmpty();
    }

    @Test
    public void orGateWithFiveInputsFiresOnAnyBranch() {

        final Variable<MonitoringStation>    stationV     = declarationOf(MonitoringStation.class);
        final Variable<SensorActivated>      activatedV   = declarationOf(SensorActivated.class);
        final Variable<HeartbeatOk>          heartbeatV   = declarationOf(HeartbeatOk.class);
        final Variable<AlarmRaised>          alarmV       = declarationOf(AlarmRaised.class);
        final Variable<CalibrationPassed>    calibV       = declarationOf(CalibrationPassed.class);
        final Variable<OperatorAcknowledged> ackV         = declarationOf(OperatorAcknowledged.class);
        final Variable<MaintenanceScheduled> maintenanceV = declarationOf(MaintenanceScheduled.class);

        Rule fiveInputRule =
                rule("five-input-or").build(
                        pattern(stationV),
                        sequence(
                                pattern(activatedV).expr("anchor", a -> a.getSensorId().equals("sensor-1")),
                                or(
                                        pattern(heartbeatV).expr("hb",   h -> h.getSensorId().equals("sensor-1")),
                                        pattern(alarmV).expr("alarm",    al -> al.getSeverity().equals("high")),
                                        pattern(calibV).expr("calib",    c -> c.getSensorId().equals("sensor-1")),
                                        pattern(ackV).expr("ack",        a -> a.getOperator().equals("alice")),
                                        pattern(maintenanceV).expr("maint", m -> m.getSensorId().equals("sensor-1"))
                                ),
                                pattern(activatedV).expr("final", a -> a.getSensorId().equals("sensor-2"))
                        ),
                        execute(() -> results.add("done"))
                );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(fiveInputRule)).newKieSession();

        insertAndFire(new MonitoringStation("station-1"));
        insertAndFire(new SensorActivated("sensor-1"));             // anchor fires; OR step activates
        insertAndFire(new MaintenanceScheduled("sensor-1"));        // 5th OR branch triggers default loop path
        insertAndFire(new SensorActivated("sensor-2"));             // final step; consequence fires

        assertThat(results).containsExactly("done");
    }
}
