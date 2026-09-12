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
package org.drools.base.reteoo.sequencing.signalprocessors;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.sequencing.signalprocessors.LogicCircuit.LongBiPredicate;
import org.drools.base.reteoo.sequencing.Sequence.SequenceMemory;

public class LogicGate extends SignalProcessor {
    protected long allMatched;

    private SignalProcessor output;

    private final LongBiPredicate predicate;

    private final int gateIndex;

    private LogicGate[] inputGates = EMPTY_INPUT_GATES;

    private final int[] filterIndexes;

    private int[] signalAdapterIndexes;

    private static final LogicGate[] EMPTY_INPUT_GATES = new LogicGate[0];

    public LogicGate(LongBiPredicate predicate, int gateIndex, int[] filterIndexes, int[] signalAdapterIndexes, int nbrOfInputGates) {
        this.predicate = predicate;

        this.filterIndexes        = filterIndexes;
        this.signalAdapterIndexes = signalAdapterIndexes;

        int totalInputs = signalAdapterIndexes.length + nbrOfInputGates;
        if (totalInputs > Long.SIZE) {
            throw new IllegalArgumentException(
                    "LogicGate supports at most " + Long.SIZE + " inputs; got " + totalInputs);
        }
        for (int i = 0; i < totalInputs; i++) {
            allMatched = allMatched | (1L << i);
        }

        this.gateIndex = gateIndex;
    }

    public int[] getSignalAdapterIndexes() {
        return signalAdapterIndexes;
    }

    public void setInputGates(LogicGate... inputGates) {
        this.inputGates = inputGates;
    }

    public void setOutput(SignalProcessor output) {
        this.output = output;
    }

    @Override
    public void consume(SequenceMemory memory, ValueResolver valueResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void consume(int signalBitIndex, SequenceMemory memory, ValueResolver valueResolver) {
        long currentMatched = memory.getLogicGateMemory()[gateIndex] | (1L << (signalBitIndex - 1));
        memory.getLogicGateMemory()[gateIndex] = currentMatched;

        boolean matched = predicate.test(currentMatched, allMatched);

        if (matched && !memory.isLogicGateMatched(gateIndex)) {
            memory.setLogicGateMatched(gateIndex, true);
            propagate(memory, valueResolver);
        }
    }

    public void propagate(SequenceMemory memory, ValueResolver valueResolver) {
        resetPrior(memory, valueResolver);
        output.consume(memory, valueResolver);
    }

    public void resetPrior(SequenceMemory memory, ValueResolver valueResolver) {
        for (LogicGate gate : inputGates) {
            gate.reset(memory, valueResolver);
        }

        memory.resetLogicGateMemory(gateIndex, valueResolver);
    }

    public void reset(SequenceMemory memory, ValueResolver valueResolver) {
        resetPrior(memory, valueResolver);
        output.reset(memory, valueResolver);
    }

    public void activate(SequenceMemory memory) {
        for (int i = 0; i < filterIndexes.length; i++) {
            memory.activateSignalAdapter(filterIndexes[i], this, signalAdapterIndexes[i], i + 1); // bit indexes start at 1
        }

    }

    public void deactivate(SequenceMemory memory, ValueResolver valueResolver) {
        for (int i = 0; i < filterIndexes.length; i++) {
            memory.deactivateSignalAdapter(filterIndexes[i], signalAdapterIndexes[i]);
        }

        memory.resetLogicGateMemory(gateIndex, valueResolver);
    }

}
