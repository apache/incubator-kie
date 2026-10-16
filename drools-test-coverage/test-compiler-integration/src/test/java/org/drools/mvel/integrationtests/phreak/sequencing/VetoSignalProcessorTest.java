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
import org.drools.base.reteoo.sequencing.Sequence;
import org.drools.base.reteoo.sequencing.Sequence.SequenceMemory;
import org.drools.base.reteoo.sequencing.signalprocessors.Gates;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicCircuit;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicGate;
import org.drools.base.reteoo.sequencing.signalprocessors.TerminatingSignalProcessor;
import org.drools.base.reteoo.sequencing.signalprocessors.VetoSignalProcessor;
import org.drools.base.reteoo.sequencing.steps.Step;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class VetoSignalProcessorTest extends AbstractPhreakSequencerSubsequenceTest {

    private SequenceMemory sequenceMemory;

    @BeforeEach
    public void setup() {
        initKBaseWithEmptyRule();

        LogicGate gate1 = new LogicGate((inputMask, sourceMask) -> Gates.and(inputMask, sourceMask), 0,
                                        new int[]{0},  // filter index 0 (bpattern)
                                        new int[]{0},  // signal adapter index 0
                                        0);
        gate1.setOutput(TerminatingSignalProcessor.get());

        LogicCircuit circuit1 = new LogicCircuit(gate1);

        seq0 = new Sequence(0, Step.of(circuit1));
        seq0.setFilters(new Pattern[]{bpattern});
        rule.addSequence(seq0);
        kbase.addPackage(pkg);

        createSession();
        sequenceMemory = sequencerMemory.getSequenceMemory(seq0);
    }

    @Test
    public void vetoProcessorResetsSequenceToStep0() {
        // consume() deactivates the current step, resets to step 0, and re-activates it.
        VetoSignalProcessor.get().consume(sequenceMemory, session);

        assertThat(sequenceMemory.getStep()).isEqualTo(0);
    }

    @Test
    public void vetoProcessorIntOverloadThrows() {
        assertThatThrownBy(() -> VetoSignalProcessor.get().consume(1, sequenceMemory, session))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
