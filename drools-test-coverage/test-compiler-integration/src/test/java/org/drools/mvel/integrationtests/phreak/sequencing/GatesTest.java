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

import org.drools.base.reteoo.sequencing.signalprocessors.Gates;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicGate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GatesTest {

    // AND gate: matched when current == allMatched (all bits set)
    @Test
    public void testAndAllSet() {
        assertThat(Gates.and(0b111L, 0b111L)).isTrue();
        assertThat(Gates.and(0b110L, 0b111L)).isFalse();
    }

    // OR gate: matched when at least one bit of allMatched is set in current
    @Test
    public void testOrBasic() {
        assertThat(Gates.or(0b001L, 0b111L)).isTrue();
        assertThat(Gates.or(0b100L, 0b111L)).isTrue();
        assertThat(Gates.or(0b000L, 0b111L)).isFalse();
    }

    @Test
    public void testOrBit63() {
        long bit63 = Long.MIN_VALUE; // 0x8000_0000_0000_0000 — only sign bit set
        // OR gate: allMatched = bit63, current = bit63 → should match
        assertThat(Gates.or(bit63, bit63)).isTrue();
    }

    @Test
    public void testLogicGateTooManyInputsThrows() {
        // Build an int[] of 65 filterIndexes/signalAdapterIndexes to exceed the limit.
        int[] indexes = new int[65];
        assertThatThrownBy(() -> new LogicGate((a, b) -> a == b, 0, indexes, indexes, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64");
    }

    @Test
    public void testLogicGateExactly64InputsAllowed() {
        int[] indexes = new int[32];
        // 32 signal adapters + 32 input gates = 64 total — should not throw.
        new LogicGate((a, b) -> a == b, 0, indexes, indexes, 32);
    }
    // XOR: true when exactly one bit of allMatched is set in currentMatched.
    @Test
    public void testXorNoneMatched() {
        assertThat(Gates.xor(0b00L, 0b11L)).isFalse();
    }

    @Test
    public void testXorOneMatchedLow() {
        assertThat(Gates.xor(0b01L, 0b11L)).isTrue();
    }

    @Test
    public void testXorOneMatchedHigh() {
        assertThat(Gates.xor(0b10L, 0b11L)).isTrue();
    }

    @Test
    public void testXorBothMatched() {
        assertThat(Gates.xor(0b11L, 0b11L)).isFalse();
    }

    @Test
    public void testXorThreeInputsExactlyOne() {
        assertThat(Gates.xor(0b001L, 0b111L)).isTrue();
        assertThat(Gates.xor(0b010L, 0b111L)).isTrue();
        assertThat(Gates.xor(0b100L, 0b111L)).isTrue();
        assertThat(Gates.xor(0b011L, 0b111L)).isFalse();
        assertThat(Gates.xor(0b111L, 0b111L)).isFalse();
    }

    @Test
    public void testXnorTruthTable() {
        // 2 inputs: allMatched = 0b11L
        assertThat(Gates.xnor(0b00L, 0b11L)).isTrue();  // none matched
        assertThat(Gates.xnor(0b01L, 0b11L)).isFalse(); // exactly one matched
        assertThat(Gates.xnor(0b10L, 0b11L)).isFalse(); // exactly one matched
        assertThat(Gates.xnor(0b11L, 0b11L)).isTrue();  // all matched

        // 3 inputs: allMatched = 0b111L
        assertThat(Gates.xnor(0b000L, 0b111L)).isTrue();  // none
        assertThat(Gates.xnor(0b001L, 0b111L)).isFalse(); // exactly one
        assertThat(Gates.xnor(0b010L, 0b111L)).isFalse(); // exactly one
        assertThat(Gates.xnor(0b100L, 0b111L)).isFalse(); // exactly one
        assertThat(Gates.xnor(0b011L, 0b111L)).isFalse(); // exactly two
        assertThat(Gates.xnor(0b111L, 0b111L)).isTrue();  // all
    }
}
