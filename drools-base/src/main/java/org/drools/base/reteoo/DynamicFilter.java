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
import org.drools.base.rule.ContextEntry;
import org.drools.base.rule.constraint.AlphaNodeFieldConstraint;
import org.drools.base.rule.constraint.BetaConstraint;
import org.drools.base.util.AbstractLinkedListNode;
import org.drools.base.util.LinkedList;
import org.kie.api.runtime.rule.FactHandle;

public class DynamicFilter extends AbstractLinkedListNode<DynamicFilter> {
    private AlphaNodeFieldConstraint  constraint;
    private LinkedList<SignalAdapter> signalAdapters;
    private int                       adapterIndex;
    private final DeliveryClock       clock;

    public DynamicFilter(DynamicFilterProto proto, DeliveryClock clock) {
        this.constraint    = proto.getConstraint();
        this.adapterIndex  = proto.getAdapterIndex();
        this.signalAdapters = new LinkedList<>();
        this.clock         = clock;
    }

    public int getAdapterIndex() {
        return adapterIndex;
    }

    public void addSignalAdapter(SignalAdapter signalAdapter) {
        signalAdapter.setRegistrationEpoch(clock.registrationEpoch());
        signalAdapter.setRegistrationHandleId(clock.currentDeliveryHandleId());
        signalAdapters.add(signalAdapter);
    }

    public void removeSignalAdapter(SignalAdapter signalAdapter) {
        signalAdapters.remove(signalAdapter);
    }

    public LinkedList<SignalAdapter> getSignalAdapters() {
        return signalAdapters;
    }
    /**
     * Delivers the fact to the signal adapters registered before its delivery began. The delivery is
     * opened on the shared {@link DeliveryClock} by the first filter that sees the fact, so an adapter
     * registered while the fact is in flight (an anchor advanced or was reset), on this filter or on
     * a filter handled in another adapter's pass, does not receive the same fact.
     *
     * <p>An adapter registered during a prior delivery of the same handle is also skipped. This
     * prevents a re-insert of a disconnected handle (e.g. under
     * {@code KieBaseMutabilityOption.DISABLED}) from reaching a step that the same handle advanced
     * to in an earlier delivery.
     */
    public void assertObject(final FactHandle factHandle,
                             final ValueResolver valueResolver) {

        clock.beginDelivery(factHandle);
        final long startEpoch    = clock.startEpoch();
        final long deliveryHandleId = factHandle.getId();

        if (constraint.getRequiredDeclarations().length == 0) {
            if (constraint.isAllowed(factHandle, valueResolver)) {
                for (SignalAdapter current = signalAdapters.getFirst(); current != null; ) {
                    if (current.getRegistrationEpoch() - startEpoch >= 0) {
                        break;
                    }
                    SignalAdapter next = current.getNext();
                    if (!current.wasRegisteredDuring(deliveryHandleId)) {
                        current.receive(valueResolver, factHandle);
                    }
                    current = next;
                }
            }
        } else {
            for (SignalAdapter current = signalAdapters.getFirst(); current != null; ) {
                if (current.getRegistrationEpoch() - startEpoch >= 0) {
                    break;
                }
                SignalAdapter next = current.getNext();
                if (!current.wasRegisteredDuring(deliveryHandleId) && test(factHandle, current.getAnchorTuple(), valueResolver)) {
                    current.receive(valueResolver, factHandle);
                }
                current = next;
            }
        }
    }

    private boolean test(final FactHandle factHandle, final BaseTuple anchorTuple, final ValueResolver valueResolver) {
        if (constraint instanceof BetaConstraint) {
            BetaConstraint<ContextEntry> betaConstraint = (BetaConstraint<ContextEntry>) constraint;
            ContextEntry ctx = betaConstraint.createContext();
            ctx.updateFromTuple(valueResolver, anchorTuple);
            return betaConstraint.isAllowedCachedLeft(ctx, factHandle);
        }
        throw new IllegalStateException("test() called with a non-beta constraint: " + constraint.getClass());
    }
}
