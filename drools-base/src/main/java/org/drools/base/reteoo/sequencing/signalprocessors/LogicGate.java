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

    private SignalProcessor reversionOutput;

    private final LongBiPredicate predicate;

    private final int gateIndex;

    private LogicGate[] inputGates = EMPTY_INPUT_GATES;

    private final int[] filterIndexes;

    private int[] signalAdapterIndexes;

    private boolean statusCanRevert;

    /**
     * When true, {@link #activate} does not register this gate's signal adapters. They are
     * registered later by {@link #arm}, when the gate that owns this one in {@link #armOnMatch}
     * becomes MATCHED. Used for an {@code absent()} written after a {@code oneOf()}/{@code noneOrAll()}
     * inside a folded composite: its veto window opens only once the preceding gate is satisfied.
     */
    private boolean deferredActivation;

    /** Deferred gates to arm when this gate transitions to MATCHED. */
    private LogicGate[] armOnMatch = EMPTY_INPUT_GATES;

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

    public LogicGate(LongBiPredicate predicate, int gateIndex, int[] filterIndexes,
                     int[] signalAdapterIndexes, int nbrOfInputGates, boolean statusCanRevert) {
        this(predicate, gateIndex, filterIndexes, signalAdapterIndexes, nbrOfInputGates);
        this.statusCanRevert = statusCanRevert;
    }

    public boolean isStatusCanRevert() {
        return statusCanRevert;
    }

    public int[] getSignalAdapterIndexes() {
        return signalAdapterIndexes;
    }

    public void setDeferredActivation(boolean deferredActivation) {
        this.deferredActivation = deferredActivation;
    }

    public void addArmOnMatch(LogicGate gate) {
        LogicGate[] extended = java.util.Arrays.copyOf(armOnMatch, armOnMatch.length + 1);
        extended[armOnMatch.length] = gate;
        armOnMatch = extended;
    }

    /** Registers the signal adapters of a deferred gate; a no-op for adapters that are already active. */
    public void arm(SequenceMemory memory) {
        for (int i = 0; i < filterIndexes.length; i++) {
            if (memory.getActiveSignalAdapters()[signalAdapterIndexes[i]] == null) {
                memory.activateSignalAdapter(filterIndexes[i], this, signalAdapterIndexes[i], i + 1);
            }
        }
    }

    public void setInputGates(LogicGate... inputGates) {
        this.inputGates = inputGates;
    }

    public void setOutput(SignalProcessor output) {
        this.output = output;
    }

    public void setReversionOutput(SignalProcessor reversionOutput) {
        this.reversionOutput = reversionOutput;
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
        } else if (!matched && statusCanRevert) {
            if (memory.isLogicGateMatched(gateIndex)) {
                // Gate was previously MATCHED and is now reverting.
                memory.setLogicGateMatched(gateIndex, false);
                if (reversionOutput != null) {
                    // Dedicated reversion handler (e.g. VetoSignalProcessor for XOR) — takes
                    // priority over the default clearParentBit path.
                    reversionOutput.consume(memory, valueResolver);
                } else if (output instanceof LogicGateOutputSignalProcessor) {
                    // Default path: clear the parent gate's contribution bit so the parent
                    // AND/OR gate no longer sees this gate as matched (used by XNOR).
                    ((LogicGateOutputSignalProcessor) output).clearParentBit(memory, valueResolver);
                }
            }
        }
    }

    public void clearInputBit(int signalBitIndex, SequenceMemory memory, ValueResolver valueResolver) {
        long currentMatched = memory.getLogicGateMemory()[gateIndex] & ~(1L << (signalBitIndex - 1));
        memory.getLogicGateMemory()[gateIndex] = currentMatched;

        boolean wasMatched = memory.isLogicGateMatched(gateIndex);
        boolean matched = predicate.test(currentMatched, allMatched);

        if (!wasMatched && matched) {
            memory.setLogicGateMatched(gateIndex, true);
            propagate(memory, valueResolver);
        } else if (wasMatched && !matched) {
            memory.setLogicGateMatched(gateIndex, false);
            if (output instanceof LogicGateOutputSignalProcessor) {
                ((LogicGateOutputSignalProcessor) output).clearParentBit(memory, valueResolver);
            }
        }
    }

    public void propagate(SequenceMemory memory, ValueResolver valueResolver) {
        resetPrior(memory, valueResolver);
        // Arm before consuming the output: the output may complete the step, whose deactivation
        // must then also remove the adapters armed here.
        for (LogicGate gate : armOnMatch) {
            gate.arm(memory);
        }
        output.consume(memory, valueResolver);
    }

    public void resetPrior(SequenceMemory memory, ValueResolver valueResolver) {
        for (LogicGate gate : inputGates) {
            gate.reset(memory, valueResolver);
        }

        if (!statusCanRevert) {
            memory.resetLogicGateMemory(gateIndex, valueResolver);
        }
    }

    public void reset(SequenceMemory memory, ValueResolver valueResolver) {
        resetPrior(memory, valueResolver);
        output.reset(memory, valueResolver);
    }

    public void activate(SequenceMemory memory, ValueResolver valueResolver) {
        if (!deferredActivation) {
            for (int i = 0; i < filterIndexes.length; i++) {
                memory.activateSignalAdapter(filterIndexes[i], this, signalAdapterIndexes[i], i + 1); // bit indexes start at 1
            }
        }
        if (!memory.isLogicGateMatched(gateIndex) && predicate.test(memory.getLogicGateMemory()[gateIndex], allMatched)) {
            memory.setLogicGateMatched(gateIndex, true);
            propagate(memory, valueResolver);
        }
    }

    public void deactivate(SequenceMemory memory, ValueResolver valueResolver) {
        for (int i = 0; i < filterIndexes.length; i++) {
            memory.deactivateSignalAdapter(filterIndexes[i], signalAdapterIndexes[i]);
        }

        memory.resetLogicGateMemory(gateIndex, valueResolver);
    }

}
