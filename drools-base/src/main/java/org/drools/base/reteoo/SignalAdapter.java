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
package org.drools.base.reteoo;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.sequencing.Sequence.SequenceMemory;
import org.drools.base.reteoo.sequencing.signalprocessors.SignalProcessor;
import org.drools.base.util.AbstractLinkedListNode;
import org.kie.api.runtime.rule.FactHandle;

public class SignalAdapter extends AbstractLinkedListNode<SignalAdapter> {
    private SignalProcessor output;
    private int             signalBitIndex;
    private int             filterIndex;
    private SequenceMemory  memory;

    public SignalAdapter(SignalProcessor output, int signalBitIndex, int filterIndex, SequenceMemory memory) {
        this.output         = output;
        this.signalBitIndex = signalBitIndex;
        this.filterIndex    = filterIndex;
        this.memory         = memory;
    }

    public BaseTuple getAnchorTuple() {
        return memory.getSequencerMemory().getLeftTuple();
    }

    public void receive(ValueResolver reteEvaluator, FactHandle factHandle) {
        memory.setData(filterIndex, factHandle);
        output.consume(signalBitIndex, memory, reteEvaluator);
    }
}
