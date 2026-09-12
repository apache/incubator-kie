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
import org.drools.base.reteoo.sequencing.signalprocessors.Gates;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicCircuit;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicGate;
import org.drools.base.reteoo.sequencing.signalprocessors.TerminatingSignalProcessor;
import org.drools.base.reteoo.sequencing.steps.Step;
import org.drools.base.rule.RuleConditionElement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SequenceCloneTest {

    @Test
    public void cloneDoesNotThrow() {
        LogicGate gate = new LogicGate(
                (a, b) -> Gates.and(a, b), 0,
                new int[]{0}, new int[]{0}, 0);
        gate.setOutput(TerminatingSignalProcessor.get());

        Sequence original = new Sequence(0, Step.of(new LogicCircuit(gate)));

        RuleConditionElement clone = original.clone();

        assertThat(clone).isNotNull();
        assertThat(clone).isNotSameAs(original);
    }

    @Test
    public void clonePreservesDescriptors() {
        LogicGate gate = new LogicGate(
                (a, b) -> Gates.and(a, b), 0,
                new int[]{0}, new int[]{0}, 0);
        gate.setOutput(TerminatingSignalProcessor.get());

        Sequence original = new Sequence(0, Step.of(new LogicCircuit(gate)));

        Sequence clone = (Sequence) original.clone();

        assertThat(clone.getSteps()).isSameAs(original.getSteps());
        assertThat(clone.getGates()).isSameAs(original.getGates());
        assertThat(clone.getFilters()).isSameAs(original.getFilters());
        assertThat(clone).isNotSameAs(original);
    }
}
