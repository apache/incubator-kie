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
import org.drools.modelcompiler.domain.SensorEvents.AlarmRaised;
import org.drools.modelcompiler.domain.SensorEvents.CalibrationPassed;
import org.drools.modelcompiler.domain.SensorEvents.HeartbeatOk;
import org.drools.modelcompiler.domain.SensorEvents.MonitoringStation;
import org.drools.modelcompiler.domain.SensorEvents.OperatorAcknowledged;
import org.drools.modelcompiler.domain.SensorEvents.SensorActivated;
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

public class PatternDSLSequenceCompositeTest {

    private final Variable<MonitoringStation>     station         = declarationOf(MonitoringStation.class);
    private final Variable<SensorActivated>       sensorActivated = declarationOf(SensorActivated.class);
    private final Variable<HeartbeatOk>           heartbeat       = declarationOf(HeartbeatOk.class);
    private final Variable<AlarmRaised>           alarm           = declarationOf(AlarmRaised.class);
    private final Variable<CalibrationPassed>     calibration     = declarationOf(CalibrationPassed.class);
    private final Variable<OperatorAcknowledged>  ack             = declarationOf(OperatorAcknowledged.class);

    private KieSession ksession;

    @AfterEach
    public void tearDown() {
        if (ksession != null) {
            ksession.dispose();
        }
    }

    private void insertAndFire(Object... facts) {
        for (Object fact : facts) {
            ksession.insert(fact);
        }
        ksession.fireAllRules();
    }

    @Test

    public void sequenceFiresWithOrOfTwo() {
        // Step 1 is or(heartbeat, alarm). Either signal is enough to advance.
        final List<String> results = new ArrayList<>();

        Rule orRule =
                rule("or-rule").build(
                        pattern(station),
                        sequence(
                                pattern(sensorActivated).expr("anchor", a -> a.getSensorId().equals("sensor-1")),
                                or(
                                        pattern(heartbeat).expr("ok", h -> h.getSensorId().equals("sensor-1")),
                                        pattern(alarm).expr("hi", al -> al.getSeverity().equals("high"))
                                ),
                                pattern(ack).expr("ack", a -> a.getOperator().equals("alice"))
                        ),
                        execute(() -> results.add("acknowledged"))
                );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(orRule)).newKieSession();

        insertAndFire(new MonitoringStation("station-1"));
        insertAndFire(new SensorActivated("sensor-1"));
        insertAndFire(new HeartbeatOk("sensor-1"));            // OR child #1 fires → step advances
        insertAndFire(new OperatorAcknowledged("sensor-1", "alice"));

        assertThat(results).containsExactly("acknowledged");
    }

    @Test
    public void sequenceFiresWithAndOfTwo() {
        // Step 1 is and(heartbeat, alarm). Both signals must arrive before step advances.
        final List<String> results = new ArrayList<>();

        Rule andRule =
                rule("and-rule").build(
                        pattern(station),
                        sequence(
                                pattern(sensorActivated).expr("anchor", a -> a.getSensorId().equals("sensor-1")),
                                and(
                                        pattern(heartbeat).expr("ok", h -> h.getSensorId().equals("sensor-1")),
                                        pattern(alarm).expr("hi", al -> al.getSeverity().equals("high"))
                                ),
                                pattern(ack).expr("ack", a -> a.getOperator().equals("alice"))
                        ),
                        execute(() -> results.add("acknowledged"))
                );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(andRule)).newKieSession();

        insertAndFire(new MonitoringStation("station-1"));
        insertAndFire(new SensorActivated("sensor-1"));
        insertAndFire(new HeartbeatOk("sensor-1"));               // AND child #1 fires → step still waiting
        insertAndFire(new AlarmRaised("sensor-1", "high"));       // AND child #2 fires → step advances
        insertAndFire(new OperatorAcknowledged("sensor-1", "alice"));

        assertThat(results).containsExactly("acknowledged");
    }

    @Test
    public void sequenceFiresWithNestedAndInsideOr() {
        // Step 1 is or(heartbeat, and(calibration, alarm)).
        // Either branch suffices: a heartbeat alone, or calibration + alarm together.
        final List<String> results = new ArrayList<>();

        Rule nestedRule =
                rule("nested-rule").build(
                        pattern(station),
                        sequence(
                                pattern(sensorActivated).expr("anchor", a -> a.getSensorId().equals("sensor-1")),
                                or(
                                        pattern(heartbeat).expr("ok", h -> h.getSensorId().equals("sensor-1")),
                                        and(
                                                pattern(calibration).expr("calibrated", c -> c.getSensorId().equals("sensor-1")),
                                                pattern(alarm).expr("hi", al -> al.getSeverity().equals("high"))
                                        )
                                ),
                                pattern(ack).expr("ack", a -> a.getOperator().equals("alice"))
                        ),
                        execute(() -> results.add("acknowledged"))
                );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(nestedRule)).newKieSession();

        insertAndFire(new MonitoringStation("station-1"));
        insertAndFire(new SensorActivated("sensor-1"));
        // The AND branch matches: calibration + alarm together, no heartbeat needed.
        insertAndFire(new CalibrationPassed("sensor-1"));
        insertAndFire(new AlarmRaised("sensor-1", "high"));
        insertAndFire(new OperatorAcknowledged("sensor-1", "alice"));

        assertThat(results).containsExactly("acknowledged");
    }

    @Test
    public void sequenceDoesNotFireWhenAndPartial() {
        // Same shape as sequenceFiresWithAndOfTwo, but only one of the AND children fires.
        // AND requires every child matched; one match is not enough; step never advances.
        final List<String> results = new ArrayList<>();

        Rule andRule =
                rule("and-partial-rule").build(
                        pattern(station),
                        sequence(
                                pattern(sensorActivated).expr("anchor", a -> a.getSensorId().equals("sensor-1")),
                                and(
                                        pattern(heartbeat).expr("ok", h -> h.getSensorId().equals("sensor-1")),
                                        pattern(alarm).expr("hi", al -> al.getSeverity().equals("high"))
                                ),
                                pattern(ack).expr("ack", a -> a.getOperator().equals("alice"))
                        ),
                        execute(() -> results.add("acknowledged"))
                );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(andRule)).newKieSession();

        insertAndFire(new MonitoringStation("station-1"));
        insertAndFire(new SensorActivated("sensor-1"));
        insertAndFire(new HeartbeatOk("sensor-1"));               // AND child #1 fires; child #2 never does.
        insertAndFire(new OperatorAcknowledged("sensor-1", "alice"));   // Step 1 still active; ack is ignored.

        assertThat(results).isEmpty();
    }

    @Test
    public void orBranchRetractAfterStepConsumedIsNoOp() {
        final List<String> localResults = new ArrayList<>();

        Rule orRule =
                rule("or-retract-noop").build(
                        pattern(station),
                        sequence(
                                pattern(sensorActivated).expr("anchor", a -> a.getSensorId().equals("sensor-1")),
                                or(
                                        pattern(heartbeat).expr("ok", h -> h.getSensorId().equals("sensor-1")),
                                        pattern(alarm).expr("hi", al -> al.getSeverity().equals("high"))
                                ),
                                pattern(ack).expr("ack", a -> a.getOperator().equals("alice"))
                        ),
                        execute(() -> localResults.add("acknowledged"))
                );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(orRule)).newKieSession();

        insertAndFire(new MonitoringStation("station-1"));
        insertAndFire(new SensorActivated("sensor-1"));                   // anchor fires; OR step activates

        FactHandle hbHandle = ksession.insert(new HeartbeatOk("sensor-1"));
        ksession.fireAllRules();                                          // OR branch fires; step deactivates, ack step activates

        ksession.retract(hbHandle);
        ksession.fireAllRules();                                          // no-op: OR step already deactivated

        insertAndFire(new AlarmRaised("sensor-1", "high"));               // no-op: OR step gone, no listener
        insertAndFire(new OperatorAcknowledged("sensor-1", "alice"));     // ack step matches; rule fires

        assertThat(localResults).containsExactly("acknowledged");
    }

    @Test
    public void orBranchRetractAndReinsertAfterStepConsumedIsNoOp() {
        final List<String> localResults = new ArrayList<>();

        Rule orRule =
                rule("or-reinsert-noop").build(
                        pattern(station),
                        sequence(
                                pattern(sensorActivated).expr("anchor", a -> a.getSensorId().equals("sensor-1")),
                                or(
                                        pattern(heartbeat).expr("ok", h -> h.getSensorId().equals("sensor-1")),
                                        pattern(alarm).expr("hi", al -> al.getSeverity().equals("high"))
                                ),
                                pattern(ack).expr("ack", a -> a.getOperator().equals("alice"))
                        ),
                        execute(() -> localResults.add("acknowledged"))
                );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(orRule)).newKieSession();

        insertAndFire(new MonitoringStation("station-1"));
        insertAndFire(new SensorActivated("sensor-1"));                   // anchor fires; OR step activates

        FactHandle hbHandle = ksession.insert(new HeartbeatOk("sensor-1"));
        ksession.fireAllRules();                                          // OR branch fires; step deactivated, ack step activates

        ksession.retract(hbHandle);
        ksession.fireAllRules();                                          // no-op: OR step already deactivated

        insertAndFire(new HeartbeatOk("sensor-1"));                       // no-op: no listener for OR branch any more
        insertAndFire(new OperatorAcknowledged("sensor-1", "alice"));     // ack step matches; rule fires

        assertThat(localResults).containsExactly("acknowledged");
    }

    @Test
    public void nestedOrInsideOrAdvancesOnAnyLeaf() {
        final List<String> results = new ArrayList<>();

        Rule rule =
                rule("nested-or-rule").build(
                        pattern(station),
                        sequence(
                                pattern(sensorActivated).expr("anchor", a -> a.getSensorId().equals("sensor-1")),
                                or(
                                        or(
                                                pattern(heartbeat).expr("hb", h -> h.getSensorId().equals("sensor-1")),
                                                pattern(alarm).expr("hi", al -> al.getSeverity().equals("high"))
                                        ),
                                        pattern(calibration).expr("cal", c -> c.getSensorId().equals("sensor-1"))
                                ),
                                pattern(ack).expr("ack", a -> a.getOperator().equals("alice"))
                        ),
                        execute(() -> results.add("acknowledged"))
                );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule)).newKieSession();

        insertAndFire(new MonitoringStation("station-1"));
        insertAndFire(new SensorActivated("sensor-1"));
        // Trigger the inner-OR branch (alarm), which feeds the outer OR → step advances
        insertAndFire(new AlarmRaised("sensor-1", "high"));
        insertAndFire(new OperatorAcknowledged("sensor-1", "alice"));

        assertThat(results).containsExactly("acknowledged");
    }

    @Test
    public void andChildReceivesDuplicateSignalDoesNotDoubleCount() {
        final List<String> results = new ArrayList<>();

        Rule rule =
                rule("and-dup-rule").build(
                        pattern(station),
                        sequence(
                                pattern(sensorActivated).expr("anchor", a -> a.getSensorId().equals("sensor-1")),
                                and(
                                        pattern(heartbeat).expr("hb", h -> h.getSensorId().equals("sensor-1")),
                                        pattern(alarm).expr("hi", al -> al.getSeverity().equals("high"))
                                ),
                                pattern(ack).expr("ack", a -> a.getOperator().equals("alice"))
                        ),
                        execute(() -> results.add("acknowledged"))
                );

        ksession = KieBaseBuilder.createKieBaseFromModel(new ModelImpl().addRule(rule)).newKieSession();

        insertAndFire(new MonitoringStation("station-1"));
        insertAndFire(new SensorActivated("sensor-1"));

        // AND child #1 arrives twice — must not advance step on second arrival
        insertAndFire(new HeartbeatOk("sensor-1"));
        assertThat(results).isEmpty();                              // still waiting for alarm

        insertAndFire(new HeartbeatOk("sensor-1"));                 // duplicate — no-op for the gate
        assertThat(results).isEmpty();

        // AND child #2 arrives — both distinct children now satisfied
        insertAndFire(new AlarmRaised("sensor-1", "high"));
        insertAndFire(new OperatorAcknowledged("sensor-1", "alice"));

        assertThat(results).containsExactly("acknowledged");
    }
}
