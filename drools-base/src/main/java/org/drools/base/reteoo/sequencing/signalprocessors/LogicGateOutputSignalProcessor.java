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

public class LogicGateOutputSignalProcessor extends SignalProcessor {
    private final LogicGate gate;
    private final int       index;

    public LogicGateOutputSignalProcessor(SignalIndex signalIndex) {
        this.gate  = signalIndex.getGate();
        this.index = signalIndex.getBitIndex();
    }

    @Override
    public void consume(SequenceMemory memory, ValueResolver valueResolver) {
        gate.consume(index, memory, valueResolver);
    }

    @Override
    public void consume(int signalBitIndex, SequenceMemory memory, ValueResolver valueResolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void reset(SequenceMemory memory, ValueResolver valueResolver) {
        // No-op: LogicGateOutputSignalProcessor is a fan-out node whose reset is driven
        // by each downstream LogicGate resetting itself via resetPrior(). There is no
        // state held here that needs clearing.
    }
}
