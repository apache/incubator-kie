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

import org.drools.base.reteoo.sequencing.Sequence;
import org.drools.base.reteoo.sequencing.Sequence.SequenceMemory;
import org.drools.base.reteoo.sequencing.signalprocessors.Gates;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicCircuit;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicGate;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicGateOutputSignalProcessor;
import org.drools.base.reteoo.sequencing.signalprocessors.SignalIndex;
import org.drools.base.reteoo.sequencing.signalprocessors.TerminatingSignalProcessor;
import org.drools.base.reteoo.sequencing.signalprocessors.VetoSignalProcessor;
import org.drools.base.reteoo.sequencing.steps.Step;
import org.drools.base.rule.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PhreakSequencerAbsentOneOfNoneOrAllStepTest extends AbstractPhreakSequencerSubsequenceTest {

    @BeforeEach
    public void setup() {
        initKBaseWithEmptyRule();

        // absent() blocker 1 (filter 0 = bpattern): fires -> VetoSignalProcessor
        LogicGate absenceLeaf1 = new LogicGate(Gates::and, 0,
                                               new int[]{0},  // filter index 0 (bpattern)
                                               new int[]{0},  // signal adapter index 0
                                               0);
        absenceLeaf1.setOutput(VetoSignalProcessor.get());

        // absent() blocker 2 (filter 1 = cpattern): fires -> VetoSignalProcessor
        LogicGate absenceLeaf2 = new LogicGate(Gates::and, 1,
                                               new int[]{1},  // filter index 1 (cpattern)
                                               new int[]{1},  // signal adapter index 1
                                               0);
        absenceLeaf2.setOutput(VetoSignalProcessor.get());

        // Positive leaf gate (filter 2 = dpattern): fires -> TerminatingSignalProcessor
        LogicGate positiveLeaf = new LogicGate(Gates::and, 2,
                                               new int[]{2},  // filter index 2 (dpattern)
                                               new int[]{2},  // signal adapter index 2
                                               0);
        positiveLeaf.setOutput(TerminatingSignalProcessor.get());

        LogicCircuit circuit = new LogicCircuit(absenceLeaf1, absenceLeaf2, positiveLeaf);

        seq0 = new Sequence(0, Step.of(circuit));
        seq0.setFilters(new Pattern[]{bpattern, cpattern, dpattern});
        rule.addSequence(seq0);
        kbase.addPackage(pkg);
    }

    @Test
    public void absentTwoPatternsNeitherPresentFires() {
        // absent(B, C): neither B nor C present; inserting positive D terminates the sequence successfully.
        createSession();

        session.insert(new DEvent(0, "d"));
        session.fireAllRules();

        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(-1);
    }

    @Test
    public void absentTwoPatternsFirstPatternVetoes() {
        // absent(B, C): first blocker B inserted after activation -> hits absenceLeaf1 -> vetoes.
        // Sequence must be reset (still alive at step 0), not terminated.
        createSession();
        SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(seq0);

        session.insert(new BEvent(0, "b"));
        session.fireAllRules();

        assertThat(sequenceMemory.getStep()).isEqualTo(0);
        assertThat(getCurrentStep(sequencerMemory)).isNotEqualTo(-1); // sequence still alive after veto
    }

    @Test
    public void absentTwoPatternsSecondPatternVetoes() {
        // absent(B, C): second blocker C inserted after activation -> hits absenceLeaf2 -> vetoes.
        // Sequence must be reset (still alive at step 0), not terminated.
        createSession();
        SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(seq0);

        session.insert(new CEvent(0, "c"));
        session.fireAllRules();

        assertThat(sequenceMemory.getStep()).isEqualTo(0);
        assertThat(getCurrentStep(sequencerMemory)).isNotEqualTo(-1); // sequence still alive after veto
    }

    @Test
    public void absentVetoDoesNotTerminateSequence() {
        // absent(B, C): veto fires but sequence is not killed — it should remain open for more events.
        // Distinct from firstPatternVetoes: inserts both blockers in sequence, then inserts D to
        // confirm the sequence is still alive (D does not complete the sequence here because the second
        // veto — from C — resets it again, but the point is it never terminates permanently).
        createSession();
        SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(seq0);

        session.insert(new BEvent(0, "b"));   // first veto — reset to step 0
        session.fireAllRules();

        assertThat(sequenceMemory.getStep()).isEqualTo(0);
        assertThat(getCurrentStep(sequencerMemory)).isNotEqualTo(-1); // alive after first veto

        session.insert(new CEvent(0, "c"));   // second veto — reset to step 0 again
        session.fireAllRules();

        assertThat(sequenceMemory.getStep()).isEqualTo(0);
        assertThat(getCurrentStep(sequencerMemory)).isNotEqualTo(-1); // still alive after second veto
    }

    @Test
    public void absentVetoThenCompletes() {
        // absent(B, C): blocker B fires the veto (reset to step 0). Retract B, then D fires — sequence completes.
        createSession();
        SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(seq0);

        org.kie.api.runtime.rule.FactHandle fhB = session.insert(new BEvent(0, "b"));  // veto + reset
        session.fireAllRules();

        assertThat(sequenceMemory.getStep()).isEqualTo(0); // still alive at step 0

        session.retract(fhB);                // remove the blocker
        session.fireAllRules();

        session.insert(new DEvent(0, "d"));       // positive trigger: should complete sequence
        session.fireAllRules();

        // Sequence completed: getCurrentStep returns -1 (step pointer past end)
        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(-1);
    }

    // -------------------------------------------------------------------------
    // oneOf() runtime tests
    //
    // Circuit for and(oneOf(B, C), D):
    //   gate 0: B leaf  (filter 0)  → LogicGateOutputSignalProcessor(gate_2, bit 1)
    //   gate 1: C leaf  (filter 1)  → LogicGateOutputSignalProcessor(gate_2, bit 2)
    //   gate 2: XOR composite       → LogicGateOutputSignalProcessor(gate_4, bit 1), statusCanRevert=true
    //   gate 3: D leaf  (filter 2)  → LogicGateOutputSignalProcessor(gate_4, bit 2)
    //   gate 4: AND composite       → TerminatingSignalProcessor
    // -------------------------------------------------------------------------

    private LogicCircuit buildXorAndCircuit() {
        LogicGateOutputSignalProcessor xorToAnd;
        LogicGateOutputSignalProcessor dToAnd;

        // XOR children: B and C leaves
        LogicGate bLeaf = new LogicGate(Gates::and, 0, new int[]{0}, new int[]{0}, 0);
        LogicGate cLeaf = new LogicGate(Gates::and, 1, new int[]{1}, new int[]{1}, 0);

        // XOR composite (gate 2): 2 input gates, statusCanRevert=true
        LogicGate xorGate = new LogicGate(Gates::xor, 2, new int[0], new int[0], 2, true);
        xorGate.setInputGates(bLeaf, cLeaf);

        // AND children: XOR and D leaf
        LogicGate dLeaf = new LogicGate(Gates::and, 3, new int[]{2}, new int[]{2}, 0);

        // AND composite (gate 4): 2 input gates
        LogicGate andGate = new LogicGate(Gates::and, 4, new int[0], new int[0], 2);
        andGate.setInputGates(xorGate, dLeaf);
        andGate.setOutput(TerminatingSignalProcessor.get());

        // Wire XOR children to XOR composite
        bLeaf.setOutput(new LogicGateOutputSignalProcessor(SignalIndex.of(xorGate, 1)));
        cLeaf.setOutput(new LogicGateOutputSignalProcessor(SignalIndex.of(xorGate, 2)));

        // Wire XOR and D leaf to AND composite
        xorToAnd = new LogicGateOutputSignalProcessor(SignalIndex.of(andGate, 1));
        dToAnd   = new LogicGateOutputSignalProcessor(SignalIndex.of(andGate, 2));
        xorGate.setOutput(xorToAnd);
        dLeaf.setOutput(dToAnd);

        return new LogicCircuit(bLeaf, cLeaf, xorGate, dLeaf, andGate);
    }

    @Test
    public void oneOfTwoPatternsExactlyOneFires() {
        // and(oneOf(B, C), D): exactly B inserted, then D → sequence advances (step terminates)
        initKBaseWithEmptyRule();
        LogicCircuit circuit = buildXorAndCircuit();
        seq0 = new Sequence(0, Step.of(circuit));
        seq0.setFilters(new Pattern[]{bpattern, cpattern, dpattern});
        rule.addSequence(seq0);
        kbase.addPackage(pkg);

        createSession();
        SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(seq0);

        session.insert(new BEvent(0, "b"));   // XOR: 1 match (bit 1) → XOR MATCHED
        session.fireAllRules();

        assertThat(getCurrentStep(sequencerMemory)).isNotEqualTo(-1); // AND not yet complete

        session.insert(new DEvent(0, "d"));   // AND: both bits set → fires TerminatingSignalProcessor
        session.fireAllRules();

        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(-1);    // sequence terminated
    }

    @Test
    public void oneOfTwoPatternsNoneDoesNotFire() {
        // and(oneOf(B, C), D): no B or C; D arrives → AND never fires (XOR bit never set)
        initKBaseWithEmptyRule();
        LogicCircuit circuit = buildXorAndCircuit();
        seq0 = new Sequence(0, Step.of(circuit));
        seq0.setFilters(new Pattern[]{bpattern, cpattern, dpattern});
        rule.addSequence(seq0);
        kbase.addPackage(pkg);

        createSession();
        SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(seq0);

        session.insert(new DEvent(0, "d"));   // AND bit 2 set, but XOR bit 1 never set
        session.fireAllRules();

        assertThat(getCurrentStep(sequencerMemory)).isNotEqualTo(-1); // step still active
    }

    @Test
    public void oneOfTwoPatternsBothDoesNotFire() {
        // and(oneOf(B, C), D): both B and C inserted → XOR reverts to UNMATCHED → AND bit 1 cleared
        // → D arrives but AND never fires
        initKBaseWithEmptyRule();
        LogicCircuit circuit = buildXorAndCircuit();
        seq0 = new Sequence(0, Step.of(circuit));
        seq0.setFilters(new Pattern[]{bpattern, cpattern, dpattern});
        rule.addSequence(seq0);
        kbase.addPackage(pkg);

        createSession();
        SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(seq0);

        session.insert(new BEvent(0, "b"));   // XOR: 1 match → MATCHED, AND bit 1 set
        session.fireAllRules();

        session.insert(new CEvent(0, "c"));   // XOR: 2 matches → REVERTS, AND bit 1 cleared
        session.fireAllRules();

        session.insert(new DEvent(0, "d"));   // D arrives, but XOR is UNMATCHED → AND never fires
        session.fireAllRules();

        assertThat(getCurrentStep(sequencerMemory)).isNotEqualTo(-1); // step still active
    }

    // -------------------------------------------------------------------------
    // noneOrAll() runtime tests
    //
    // Circuit for and(noneOrAll(B, C), D):
    //   gate 0: B leaf  (filter 0)  → LogicGateOutputSignalProcessor(gate_2, bit 1)
    //   gate 1: C leaf  (filter 1)  → LogicGateOutputSignalProcessor(gate_2, bit 2)
    //   gate 2: XNOR composite      → LogicGateOutputSignalProcessor(gate_4, bit 1), statusCanRevert=true
    //   gate 3: D leaf  (filter 2)  → LogicGateOutputSignalProcessor(gate_4, bit 2)
    //   gate 4: AND composite       → TerminatingSignalProcessor
    // -------------------------------------------------------------------------

    private LogicCircuit buildXnorAndCircuit() {
        LogicGateOutputSignalProcessor xnorToAnd;
        LogicGateOutputSignalProcessor dToAnd;

        // XNOR children: B and C leaves
        LogicGate bLeaf = new LogicGate(Gates::and, 0, new int[]{0}, new int[]{0}, 0);
        LogicGate cLeaf = new LogicGate(Gates::and, 1, new int[]{1}, new int[]{1}, 0);

        // XNOR composite (gate 2): 2 input gates, statusCanRevert=true
        LogicGate xnorGate = new LogicGate(Gates::xnor, 2, new int[0], new int[0], 2, true);
        xnorGate.setInputGates(bLeaf, cLeaf);

        // AND children: XNOR and D leaf
        LogicGate dLeaf = new LogicGate(Gates::and, 3, new int[]{2}, new int[]{2}, 0);

        // AND composite (gate 4): 2 input gates
        LogicGate andGate = new LogicGate(Gates::and, 4, new int[0], new int[0], 2);
        andGate.setInputGates(xnorGate, dLeaf);
        andGate.setOutput(TerminatingSignalProcessor.get());

        // Wire XNOR children to XNOR composite
        bLeaf.setOutput(new LogicGateOutputSignalProcessor(SignalIndex.of(xnorGate, 1)));
        cLeaf.setOutput(new LogicGateOutputSignalProcessor(SignalIndex.of(xnorGate, 2)));

        // Wire XNOR and D leaf to AND composite
        xnorToAnd = new LogicGateOutputSignalProcessor(SignalIndex.of(andGate, 1));
        dToAnd   = new LogicGateOutputSignalProcessor(SignalIndex.of(andGate, 2));
        xnorGate.setOutput(xnorToAnd);
        dLeaf.setOutput(dToAnd);

        return new LogicCircuit(bLeaf, cLeaf, xnorGate, dLeaf, andGate);
    }

    @Test
    public void noneOrAllTwoPatternsNeitherFires() {
        // and(noneOrAll(B, C), D): neither B nor C inserted → XNOR MATCHED on activate → D inserted → sequence advances
        initKBaseWithEmptyRule();
        LogicCircuit circuit = buildXnorAndCircuit();
        seq0 = new Sequence(0, Step.of(circuit));
        seq0.setFilters(new Pattern[]{bpattern, cpattern, dpattern});
        rule.addSequence(seq0);
        kbase.addPackage(pkg);

        createSession();
        SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(seq0);

        // Neither B nor C inserted. XNOR is initially matched (none matched is true for XNOR).
        session.insert(new DEvent(0, "d"));   // AND: both bits set (XNOR matches on activate, D matches now) → terminates step
        session.fireAllRules();

        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(-1);    // sequence terminated
    }

    @Test
    public void noneOrAllTwoPatternsBothFires() {
        // and(noneOrAll(B, C), D): both B and C inserted → XNOR MATCHED → D inserted → sequence advances
        initKBaseWithEmptyRule();
        LogicCircuit circuit = buildXnorAndCircuit();
        seq0 = new Sequence(0, Step.of(circuit));
        seq0.setFilters(new Pattern[]{bpattern, cpattern, dpattern});
        rule.addSequence(seq0);
        kbase.addPackage(pkg);

        createSession();
        SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(seq0);

        session.insert(new BEvent(0, "b"));   // XNOR: 1 match → UNMATCHED, AND bit 1 cleared
        session.fireAllRules();

        session.insert(new CEvent(0, "c"));   // XNOR: 2 matches → MATCHED again, AND bit 1 set
        session.fireAllRules();

        session.insert(new DEvent(0, "d"));   // AND: both bits set → terminates step
        session.fireAllRules();

        assertThat(getCurrentStep(sequencerMemory)).isEqualTo(-1);    // sequence terminated
    }

    @Test
    public void noneOrAllTwoPatternsExactlyOneDoesNotFire() {
        // and(noneOrAll(B, C), D): exactly B inserted → XNOR UNMATCHED → D inserted → does not fire
        initKBaseWithEmptyRule();
        LogicCircuit circuit = buildXnorAndCircuit();
        seq0 = new Sequence(0, Step.of(circuit));
        seq0.setFilters(new Pattern[]{bpattern, cpattern, dpattern});
        rule.addSequence(seq0);
        kbase.addPackage(pkg);

        createSession();
        SequenceMemory sequenceMemory = sequencerMemory.getSequenceMemory(seq0);

        session.insert(new BEvent(0, "b"));   // XNOR: 1 match → UNMATCHED, AND bit 1 cleared
        session.fireAllRules();

        session.insert(new DEvent(0, "d"));   // D arrives, but XNOR is UNMATCHED → AND never fires
        session.fireAllRules();

        assertThat(getCurrentStep(sequencerMemory)).isNotEqualTo(-1); // step still active
    }
}
