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
import org.drools.base.reteoo.sequencing.Sequence.SequenceMemory;

/**
 * Output processor for the absence leaf gate inside a folded composite step.
 *
 * <p>When the absence pattern fires — i.e. a matching fact is inserted while the step is
 * active — this processor resets the sequence: it deactivates the current step, resets
 * the step pointer to 0, and re-activates step 0 so the sequence starts listening again
 * from the beginning.
 *
 * <h3>How it is wired</h3>
 * <p>Inside a {@code sequence()}, any {@code not()} or {@code nor()} condition is compiled
 * into an <em>absence leaf gate</em> whose output is set to this processor.  The absence
 * leaf gate is folded together with the following positive step into one
 * {@link LogicCircuit}, so both run inside the same step.  The absence side-channel is
 * independent: it is not wired into the positive gate's parent AND composite.
 * Example topology for {@code sequence(A, not(X), B)}:
 * <pre>
 *   step 0  -&gt;  [gate: A matches]  -&gt;  TerminatingSignalProcessor  -&gt;  advance to step 1
 *   step 1  -&gt;  [gate: X arrives]  -&gt;  VetoSignalProcessor          -&gt;  reset to step 0
 *           \   [gate: B matches]  -&gt;  TerminatingSignalProcessor  -&gt;  advance to step 2
 * </pre>
 *
 * <h3>Full-reset semantics (why always step 0)</h3>
 * <p>The reset always targets step 0, regardless of which step in the sequence contains
 * the veto gate.  In a multi-not sequence such as
 * {@code sequence(A, not(X), B, not(Y), C)}, a veto while waiting at step 2 discards
 * all earlier progress and restarts from step 0.  This is intentional, for three reasons:
 * <ol>
 *   <li><b>A not-guard invalidates the whole attempt.</b>  {@code not(X)} means "X must
 *       not appear during this attempt."  When X does appear, the entire attempt is
 *       invalid — there is no partially valid prefix to retain.</li>
 *   <li><b>Partial reset is observationally ambiguous.</b>  Resetting to the step
 *       preceding the veto gate would imply that facts matched during the previous attempt
 *       still count toward the new attempt.  Whether a previously matched fact is still
 *       the <em>right</em> fact is undefined; retaining bindings across a veto would
 *       require a dedicated binding-retention mechanism that does not exist.</li>
 *   <li><b>Consistency with CEP restart semantics.</b>  Drools temporal constraints
 *       ({@code after}, {@code before}) restart pattern matching from scratch when a match
 *       is invalidated.  Partial restart from a mid-sequence checkpoint would introduce a
 *       new restart model not justified by any current use case.</li>
 * </ol>
 *
 * <h3>Singleton design</h3>
 * <p>This class is a stateless singleton ({@link #get()}).  The inability to encode a
 * target step index is a deliberate design forcing function: if partial reset were ever
 * needed, a distinct processor type (e.g. {@code PartialResetSignalProcessor(int targetStep)})
 * would need to be introduced as an explicit API change requiring its own design review.
 * The {@link #consume(int, SequenceMemory, ValueResolver)} overload (used by gates that
 * report back to a parent composite) is therefore not supported and throws
 * {@link UnsupportedOperationException}.
 */
public class VetoSignalProcessor extends SignalProcessor {

    private static final VetoSignalProcessor INSTANCE = new VetoSignalProcessor();

    private VetoSignalProcessor() {}

    public static VetoSignalProcessor get() {
        return INSTANCE;
    }

    @Override
    public void consume(SequenceMemory memory, ValueResolver valueResolver) {
        int step = memory.getStep();
        memory.getSequence().getSteps()[step].deactivate(memory, valueResolver);
        memory.setStep(0);
        memory.getSequence().getSteps()[0].activate(memory, valueResolver);
    }

    @Override
    public void consume(int signalBitIndex, SequenceMemory memory, ValueResolver valueResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void reset(SequenceMemory memory, ValueResolver valueResolver) {
        // No state to reset.
    }
}
