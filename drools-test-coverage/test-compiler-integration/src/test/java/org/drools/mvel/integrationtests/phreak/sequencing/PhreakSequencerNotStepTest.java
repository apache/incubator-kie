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
package org.drools.mvel.integrationtests.phreak.sequencing;

import org.drools.base.rule.Pattern;
import org.drools.base.reteoo.SignalAdapter;
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

/**
 * Runtime-layer tests for the continuous absence guard (ADR 0002).
 *
 * Builds a Sequence directly with the folded composite layout:
 *   - absenceLeaf gate: filterIndex=0 (bpattern), outputs to VetoSignalProcessor
 *   - positiveLeaf gate: filterIndex=1 (cpattern), outputs to TerminatingSignalProcessor
 *
 * Veto tests: insert B("b") → hits filter 0 → VetoSignalProcessor fires → sequence resets to step 0.
 * Advance test: insert C("c") → hits filter 1 → TerminatingSignalProcessor fires.
 */
public class PhreakSequencerNotStepTest extends AbstractPhreakSequencerSubsequenceTest {

    @BeforeEach
    public void setup() {
        initKBaseWithEmptyRule();

        // Absence leaf gate (filter 0 = bpattern): fires → VetoSignalProcessor
        LogicGate absenceLeaf = new LogicGate(Gates::and, 0,
                                              new int[]{0},  // filter index 0 (bpattern)
                                              new int[]{0},  // signal adapter index 0
                                              0);
        absenceLeaf.setOutput(VetoSignalProcessor.get());

        // Positive leaf gate (filter 1 = cpattern): fires → TerminatingSignalProcessor
        LogicGate positiveLeaf = new LogicGate(Gates::and, 1,
                                               new int[]{1},  // filter index 1 (cpattern)
                                               new int[]{1},  // signal adapter index 1
                                               0);
        positiveLeaf.setOutput(TerminatingSignalProcessor.get());

        LogicCircuit circuit = new LogicCircuit(absenceLeaf, positiveLeaf);

        seq0 = new Sequence(0, Step.of(circuit));
        seq0.setFilters(new Pattern[]{bpattern, cpattern});
        rule.addSequence(seq0);
        kbase.addPackage(pkg);
    }

    @Test
    public void absenceGuardAdvancesWhenNoBlocker() {
        // No B inserted — positive C fires → TerminatingSignalProcessor → sequence terminates.
        createSession();
        SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(seq0);


        // Insert C to fire positiveLeaf → TerminatingSignalProcessor → sequence terminates
        session.insert(new CEvent(0, "c"));
        session.fireAllRules();

        // -1 means the sequence has terminated (no active leaf sequences)
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(-1);
    }

    @Test
    public void absenceGuardResetsOnLiveInsert() {
        // B inserted after activation → absence gate fires → sequence resets to step 0.
        createSession();
        SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(seq0);


        // Insert B — the live signal adapter fires into VetoSignalProcessor → reset to step 0.
        session.insert(new BEvent(0, "b"));
        session.fireAllRules();

        // After reset: veto flag is cleared, step is 0, sequence is still active.
        assertThat(sequenceMemory.getStep()).isEqualTo(0);
        assertThat(getCurrentStep(sequencerMemory)).isNotEqualTo(-1);
    }

    @Test
    public void absenceGuardReactivatesAdaptersAfterReset() {
        // After reset, step 0 is re-activated so signal adapters are re-registered (not null).
        createSession();
        SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(seq0);

        session.insert(new BEvent(0, "b"));
        session.fireAllRules();

        // After reset, step 0 re-activated: at least one adapter must be active.
        assertThat(sequenceMemory.getStep()).isEqualTo(0);
        boolean anyActive = false;
        for (SignalAdapter adapter : sequenceMemory.getActiveSignalAdapters()) {
            if (adapter != null) {
                anyActive = true;
                break;
            }
        }
        assertThat(anyActive).isTrue();
    }
    /**
     * Regression test for: not() in a sequence leaves the blocker fact in WM after reset,
     * causing immediate re-veto on the next activation cycle.
     *
     * Sequence: step 0 = wait for B  →  step 1 = not(C) + positive D
     *
     * Run 1: insert B (advance to step 1), insert C (veto fires, reset to step 0) — OK.
     * Run 2: insert B again (advance to step 1). The C fact is still in WM.
     *        LogicCircuitStep.activate() must NOT fire the veto on the pre-existing C.
     *        The sequence should stay at step 1 and wait for a new D or a new C, not
     *        silently re-veto and reset again.
     *
     * Expected (correct): step == 1 after advancing on the second run.
     * Actual (buggy):     step == 0 because activate() re-fires the veto immediately.
     */
    @Test
    public void absenceGuardDoesNotRevetoOnPreExistingBlockerAfterReset() {
        initKBaseWithEmptyRule();

        // Step 0: gate on B (filter 0) → TerminatingSignalProcessor (advances sequence)
        LogicGate step0Gate = new LogicGate(Gates::and, 0,
                                            new int[]{0},  // filter index 0 = bpattern
                                            new int[]{0},  // signal adapter index 0
                                            0);
        step0Gate.setOutput(TerminatingSignalProcessor.get());
        LogicCircuit circuit0 = new LogicCircuit(step0Gate);

        // Step 1, absence leaf: gate on C (filter 1) → VetoSignalProcessor
        LogicGate absenceLeaf = new LogicGate(Gates::and, 1,
                                              new int[]{1},  // filter index 1 = cpattern
                                              new int[]{1},  // signal adapter index 1
                                              0);
        absenceLeaf.setOutput(VetoSignalProcessor.get());

        // Step 1, positive leaf: gate on D (filter 2) → TerminatingSignalProcessor
        LogicGate positiveLeaf = new LogicGate(Gates::and, 2,
                                               new int[]{2},  // filter index 2 = dpattern
                                               new int[]{2},  // signal adapter index 2
                                               0);
        positiveLeaf.setOutput(TerminatingSignalProcessor.get());

        LogicCircuit circuit1 = new LogicCircuit(absenceLeaf, positiveLeaf);

        seq0 = new Sequence(0, Step.of(circuit0), Step.of(circuit1));
        seq0.setFilters(new Pattern[]{bpattern, cpattern, dpattern});
        rule.addSequence(seq0);
        kbase.addPackage(pkg);

        createSession();
        SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(seq0);

        // --- Run 1: advance to step 1, then veto with C ---
        session.insert(new BEvent(0, "b"));
        session.fireAllRules();
        assertThat(sequenceMemory.getStep()).as("after B: should advance to step 1").isEqualTo(1);

        session.insert(new CEvent(0, "c"));
        session.fireAllRules();
        // Veto fires, sequence resets to step 0.
        assertThat(sequenceMemory.getStep()).as("after C veto: should reset to step 0").isEqualTo(0);

        // --- Run 2: advance to step 1 again — C is still in WM ---
        session.insert(new BEvent(0, "b"));
        session.fireAllRules();

        // Bug: activate() finds the old C in WM and fires the veto again → step == 0.
        // Correct behaviour: activate() must not check pre-existing WM facts → step == 1.
        assertThat(sequenceMemory.getStep())
                .as("after second B with stale C in WM: activate() must not re-veto — step should be 1")
                .isEqualTo(1);
    }
}
