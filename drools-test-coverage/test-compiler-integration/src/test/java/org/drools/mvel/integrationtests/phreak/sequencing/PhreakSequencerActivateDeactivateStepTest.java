/**
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
package org.drools.mvel.integrationtests.phreak.sequencing;

import org.drools.base.rule.Pattern;
import org.drools.base.reteoo.DynamicFilter;
import org.drools.base.reteoo.sequencing.Sequence.SequenceMemory;
import org.drools.base.reteoo.SignalAdapter;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicCircuit;
import org.drools.base.reteoo.sequencing.signalprocessors.Gates;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicGate;
import org.drools.base.reteoo.sequencing.Sequence;
import org.drools.base.reteoo.sequencing.steps.Step;
import org.drools.base.reteoo.sequencing.signalprocessors.TerminatingSignalProcessor;
import org.drools.mvel.integrationtests.phreak.A;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PhreakSequencerActivateDeactivateStepTest extends AbstractPhreakSequencerSubsequenceTest {

    private SequenceMemory sequenceMemory;

    @BeforeEach
    public void setup() {
        initKBaseWithEmptyRule();

        LogicGate gate1 = new LogicGate((inputMask, sourceMask) -> Gates.and(inputMask, sourceMask),0,
                                        new int[] {0, 1}, // B and C
                                        new int[] {0, 1}, // Each SignalAdapter must be in a unique index  for the Sequence
                                        0);
        gate1.setOutput(TerminatingSignalProcessor.get());

        LogicCircuit circuit1 = new LogicCircuit(gate1);

        LogicGate gate2 = new LogicGate((inputMask, sourceMask) -> Gates.and(inputMask, sourceMask),1,
                                        new int[] {1, 2}, // C and D, C Filter is re-used
                                        new int[] {2, 3}, // Each SignalAdapter must be in a unique index  for the Sequence
                                        0);
        gate2.setOutput(TerminatingSignalProcessor.get());

        LogicCircuit circuit2 = new LogicCircuit(gate2);

        seq0 = new Sequence(0, Step.of(circuit1), Step.of(circuit2));
        seq0.setFilters(new Pattern[]{bpattern, cpattern, dpattern});
        rule.addSequence(seq0);
        kbase.addPackage(pkg);

        createSession();
        sequenceMemory = sequencerMemory.getSequenceMemory(seq0);
    }

    @Test
    public void testActivateAfterFirstCircuitStep() {
        // activate LogicCircuit1 and check filters are adapters are created and made active
        DynamicFilter filter0 = nodeMemory.getFilters()[0]; // B
        DynamicFilter filter1 = nodeMemory.getFilters()[1]; // C
        // [2] D is null

        // DynamicFilters are created on demand from Protos
        assertThat(nodeMemory.getFilters()).usingRecursiveComparison().isEqualTo(new DynamicFilter[] {filter0, filter1, null });

        // Check correct activation of DynamicFilters
        assertThat(nodeMemory.getActiveFilters()[0].size()).isEqualTo(1); // B is active
        assertThat(nodeMemory.getActiveFilters()[0].getFirst()).isSameAs(filter0);
        assertThat(nodeMemory.getActiveFilters()[1].size()).isEqualTo(1); // C is active
        assertThat(nodeMemory.getActiveFilters()[1].getFirst()).isSameAs(filter1);
        assertThat(nodeMemory.getActiveFilters()[2]).isNull(); // D is not yet active

        // Check correct activation of SignalAdapters
        SignalAdapter signal0 = sequenceMemory.getSignalAdapters()[0];
        SignalAdapter signal1 = sequenceMemory.getSignalAdapters()[1];
        assertThat(sequenceMemory.getSignalAdapters()).usingRecursiveComparison().isEqualTo(new SignalAdapter[] {signal0, signal1, null, null });
        assertThat(sequenceMemory.getActiveSignalAdapters()).usingRecursiveComparison().isEqualTo(new SignalAdapter[] {signal0, signal1, null, null });
    }

    @Test
    public void testDeactivateAndActivateAfterSecondCircuitStep() {
        DynamicFilter filter0 = nodeMemory.getFilters()[0]; // B
        DynamicFilter filter1 = nodeMemory.getFilters()[1]; // C

        SignalAdapter signal0 = sequenceMemory.getSignalAdapters()[0];
        SignalAdapter signal1 = sequenceMemory.getSignalAdapters()[1];

        session.insert(new BEvent(0, "b"));
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0); // still step 0

        session.insert(new CEvent(0, "c"));
        // Next step, check everything was for LogicCircuit was de-activated and LogicCircuit1 was activated;
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(1); // Now step 1

        DynamicFilter filter2 = nodeMemory.getFilters()[2]; // D was created from its Proto

        assertThat(nodeMemory.getFilters()).usingRecursiveComparison().isEqualTo(new DynamicFilter[] {filter0, filter1, filter2 }); // all three now created

        // Check correct activation of DynamicFilters
        assertThat(nodeMemory.getActiveFilters()[0].size()).isEqualTo(0); // B is deactived and now empty
        assertThat(nodeMemory.getActiveFilters()[1].size()).isEqualTo(1); // C remains active
        assertThat(nodeMemory.getActiveFilters()[1].getFirst()).isSameAs(filter1);
        assertThat(nodeMemory.getActiveFilters()[2].size()).isEqualTo(1); // D is now active
        assertThat(nodeMemory.getActiveFilters()[2].getFirst()).isSameAs(filter2);

        // Check correct activation of SignalAdapters
        SignalAdapter signal2 = sequenceMemory.getSignalAdapters()[2];
        SignalAdapter signal3 = sequenceMemory.getSignalAdapters()[3];

        assertThat(sequenceMemory.getSignalAdapters()).usingRecursiveComparison().isEqualTo(new SignalAdapter[] {signal0, signal1, signal2, signal3 });
        assertThat(sequenceMemory.getActiveSignalAdapters()).usingRecursiveComparison().isEqualTo(new SignalAdapter[] {null, null, signal2, signal3 }); // 0 and 1 are no longer active
    }

    @Test
    public void testDeactivateAfterEndStep() {
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0); // step 0
        session.insert(new BEvent(0, "b"));
        session.insert(new CEvent(0, "c"));
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(1); // step 1
        session.insert(new CEvent(0, "c"));
        session.insert(new DEvent(0, "d"));

        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(-1); // Now step -1, which means it's finished.

        // make sure there are no active SignalAapters or active Filters.
        assertThat(sequenceMemory.getActiveSignalAdapters()).usingRecursiveComparison().isEqualTo(new SignalAdapter[] {null, null, null, null }); // 0 and 1 are no longer active
        assertThat(nodeMemory.getActiveFilters()[0].size()).isEqualTo(0);
        assertThat(nodeMemory.getActiveFilters()[1].size()).isEqualTo(0);
        assertThat(nodeMemory.getActiveFilters()[2].size()).isEqualTo(0);
    }

    @Test
    public void testUpdateOnDrivingTupleRestartsSequence() {
        // Advance to step 1: insert B then C to satisfy gate 1
        session.insert(new BEvent(0, "b"));
        session.insert(new CEvent(0, "c"));
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(1); // sequence is at step 1

        // Update A — should stop the running sequence and restart it at step 0.
        // fireAllRules() is required to flush the Phreak left-tuple update queue.
        A a = (A) fhA0.getObject();
        session.update(fhA0, a);
        session.fireAllRules();

        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0);
    }
}
