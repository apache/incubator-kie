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
    private long            registrationEpoch;
    /**
     * Id of the fact handle most recently delivered when this adapter was registered
     * ({@code -1} if none had been delivered yet since the clock was created or reset).
     * Used by {@link DynamicFilter#assertObject} to suppress re-delivery of the same handle
     * to a step it advanced to in an earlier delivery.
     */
    private long            registrationHandleId = -1;

    public SignalAdapter(SignalProcessor output, int signalBitIndex, int filterIndex, SequenceMemory memory) {
        this.output         = output;
        this.signalBitIndex = signalBitIndex;
        this.filterIndex    = filterIndex;
        this.memory         = memory;
    }

    public BaseTuple getAnchorTuple() {
        return memory.getSequencerMemory().getLeftTuple();
    }

    public long getRegistrationEpoch() {
        return registrationEpoch;
    }

    public void setRegistrationEpoch(long registrationEpoch) {
        this.registrationEpoch = registrationEpoch;
    }

    /**
     * Returns {@code true} if this adapter was registered while {@code handleId} was being
     * delivered. A re-delivery of the same handle must not reach adapters that were created
     * as a consequence of that earlier delivery.
     */
    public boolean wasRegisteredDuring(long handleId) {
        return registrationHandleId == handleId;
    }

    /** Called by {@link DynamicFilter#addSignalAdapter} to record the delivery context at registration. */
    public void setRegistrationHandleId(long handleId) {
        this.registrationHandleId = handleId;
    }

    public void receive(ValueResolver reteEvaluator, FactHandle factHandle) {
        memory.setData(filterIndex, factHandle);
        output.consume(signalBitIndex, memory, reteEvaluator);
    }
}
