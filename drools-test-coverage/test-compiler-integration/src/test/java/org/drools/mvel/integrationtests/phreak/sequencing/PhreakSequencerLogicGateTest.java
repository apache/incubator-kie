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
import org.drools.core.common.InternalFactHandle;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicCircuit;
import org.drools.base.reteoo.sequencing.signalprocessors.Gates;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicGate;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicGateOutputSignalProcessor;
import org.drools.base.reteoo.sequencing.Sequence;
import org.drools.base.reteoo.sequencing.signalprocessors.SignalIndex;
import org.drools.base.reteoo.sequencing.steps.Step;
import org.drools.base.reteoo.sequencing.signalprocessors.TerminatingSignalProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PhreakSequencerLogicGateTest extends AbstractPhreakSequencerSubsequenceTest {

    @BeforeEach
    public void setup() {
        initKBaseWithEmptyRule();
    }

    @Test
    public void testAnd() {
        LogicGate gate1 = new LogicGate((inputMask, sourceMask) -> Gates.and(inputMask, sourceMask),0,
                                        new int[] {0, 1}, // B and C
                                        new int[] {0, 1}, // Each SignalAdapter must be in a unique index  for the Sequence
                                        0);

        gate1.setOutput(TerminatingSignalProcessor.get());

        LogicCircuit circuit1 = new LogicCircuit(gate1);

        Sequence seq = new Sequence(0, Step.of(circuit1));
        seq.setFilters(new Pattern[]{bpattern, cpattern, dpattern, epattern});
        rule.addSequence(seq);
        kbase.addPackage(pkg);

        createSession();
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0); // step 0
        session.insert(new BEvent(0, "b"));
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0); // step 0
         session.insert(new CEvent(0, "c"));
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(-1); // terminated

        // reverse B and C
        createSession();
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0); // step 0
        session.insert(new CEvent(0, "c"));

        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0); // step 0
        session.insert(new BEvent(0, "b"));
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(-1); // terminated
    }

    @Test
    public void testOr() {
        LogicGate gate1 = new LogicGate((inputMask, sourceMask) -> Gates.or(inputMask, sourceMask),0,
                                        new int[] {0, 1}, // B and C
                                        new int[] {0, 1}, // Each SignalAdapter must be in a unique index  for the Sequence
                                        0);

        gate1.setOutput(TerminatingSignalProcessor.get());

        LogicCircuit circuit1 = new LogicCircuit(gate1);

        Sequence seq = new Sequence(0, Step.of(circuit1));
        seq.setFilters(new Pattern[]{bpattern, cpattern, dpattern, epattern});
        rule.addSequence(seq);
        kbase.addPackage(pkg);

        createSession();
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0); // step 0
        session.insert(new BEvent(0, "b"));

        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(-1); // terminated

        // reverse B and C
        createSession();
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0); // step 0
        InternalFactHandle fhC0 = (InternalFactHandle) session.insert(new CEvent(0, "c"));

        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(-1); // terminated
    }


    @Test
    public void testComposite() {
        LogicGate gate1 = new LogicGate((inputMask, sourceMask) -> Gates.and(inputMask, sourceMask),0,
                                        new int[] {0, 1}, // B and C
                                        new int[] {0, 1}, // Each SignalAdapter must be in a unique index  for the Sequence
                                        0);

        LogicGate gate2 = new LogicGate((inputMask, sourceMask) -> Gates.and(inputMask, sourceMask),1,
                                        new int[] {2}, // D
                                        new int[] {2}, // D
                                        1);

        gate1.setOutput(new LogicGateOutputSignalProcessor(SignalIndex.of(gate2, 2))); // gate bits must come after signals
        gate2.setOutput(TerminatingSignalProcessor.get());
        gate2.setInputGates(gate1);

        LogicCircuit circuit1 = new LogicCircuit(gate1, gate2);

        Sequence seq = new Sequence(0, Step.of(circuit1));
        seq.setFilters(new Pattern[]{bpattern, cpattern, dpattern, epattern});
        rule.addSequence(seq);
        kbase.addPackage(pkg);

        // D last
        createSession();
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0); // step 0
        session.insert(new BEvent(0, "b"));
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0); // step 0
        session.insert(new CEvent(0, "c"));
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0); // step 0

        // now it'll transition
        session.insert(new DEvent(0, "d"));
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(-1); // terminated

        // change order, D first
        createSession();
        session.insert(new DEvent(0, "d"));
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0); // step 0

        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0); // step 0
        session.insert(new BEvent(0, "b"));
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(0); // step 0

        // now it'll transition
        session.insert(new CEvent(0, "c"));
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(-1); // terminated
    }

}
