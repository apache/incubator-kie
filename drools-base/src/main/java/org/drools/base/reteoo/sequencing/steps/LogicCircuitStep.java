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
package org.drools.base.reteoo.sequencing.steps;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicCircuit;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicGate;
import org.drools.base.reteoo.sequencing.Sequence;
import org.drools.base.reteoo.sequencing.Sequence.SequenceMemory;

public class LogicCircuitStep extends AbstractStep implements Step {
    private final LogicCircuit circuit;

    public LogicCircuitStep(Sequence sequence, LogicCircuit circuit) {
        super(sequence);
        this.circuit = circuit;
    }

    public LogicCircuit getCircuit() {
        return circuit;
    }

    public void activate(SequenceMemory sequenceMemory, ValueResolver valueResolver) {
        for (LogicGate gate : circuit.getGates()) {
            gate.activate(sequenceMemory);
        }
    }

    public void deactivate(SequenceMemory sequenceMemory, ValueResolver valueResolver) {
        for (LogicGate gate : circuit.getGates()) {
            gate.deactivate(sequenceMemory, valueResolver);
        }
    }
}
