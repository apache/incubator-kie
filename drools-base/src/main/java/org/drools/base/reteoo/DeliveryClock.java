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

import org.kie.api.runtime.rule.FactHandle;

/**
 * One clock per sequence node memory, shared by all of its {@link DynamicFilter}s.
 *
 * <h3>Cross-adapter guard (epoch)</h3>
 * <p>A fact reaches a sequence node through one adapter per matching object type, so it is
 * delivered to the active filters in several separate passes. Delivering it can move an anchor to
 * another step, and that registers a {@link SignalAdapter} on a filter that a later pass has not
 * reached yet. Such a registration must not receive the fact that caused it.
 *
 * <p>The first filter that handles a fact opens its delivery ({@link #beginDelivery(FactHandle)});
 * every later filter, in this pass or in a pass of another adapter, sees the same fact (same handle
 * ID) and keeps the same start epoch. A signal adapter is stamped with the epoch current at
 * registration, so it is skipped for a fact whose start epoch is not greater than its stamp.
 *
 * <h3>Re-insert guard (registration handle ID)</h3>
 * <p>When {@code KieBaseMutabilityOption.DISABLED} is in effect, updating a fact whose handle is
 * disconnected causes the same {@code FactHandle} object to be re-inserted. Any intervening
 * delivery will have advanced the clock's {@code currentFactId}, so the re-insert opens a new
 * epoch and the epoch guard alone cannot prevent the re-inserted handle from reaching a step it
 * already advanced to. The clock therefore also exposes the handle ID current at registration
 * time ({@link #currentDeliveryHandleId()}), which {@link DynamicFilter} stamps onto every
 * newly registered {@link SignalAdapter}. Before delivering to an adapter,
 * {@link DynamicFilter#assertObject} checks whether the adapter was registered during an earlier
 * delivery of the same handle via {@link SignalAdapter#wasRegisteredDuring(long)} and, if so,
 * skips it.
 */
public class DeliveryClock {
    private long epoch;
    private long startEpoch;
    private long currentFactId = -1;

    /** Epoch to stamp on a signal adapter that is registering now. */
    public long registrationEpoch() {
        return epoch;
    }

    /** Start epoch of the delivery in progress. */
    public long startEpoch() {
        return startEpoch;
    }

    /**
     * The ID of the fact handle most recently delivered ({@code -1} if none since construction or
     * {@link #reset()}). Stamped onto a signal adapter when it registers, so that re-deliveries of
     * the same handle to adapters registered during that handle's earlier delivery can be detected
     * and suppressed.
     */
    public long currentDeliveryHandleId() {
        return currentFactId;
    }

    /**
     * Returns the clock to its initial state. Must be called when the owning node memory is reset
     * in place, because the session's handle IDs restart from the beginning and a stale
     * {@code currentFactId} would collide with the new IDs.
     */
    public void reset() {
        epoch         = 0;
        startEpoch    = 0;
        currentFactId = -1;
    }

    /**
     * Opens the delivery of {@code factHandle} unless it is already the fact in flight.
     *
     * <p>Two deliveries of the same handle are treated as one when they share the same handle ID.
     * This covers the cross-adapter case (a {@code Toy} matching both an {@code Object} and a
     * {@code Toy} OTN in the same propagation pass — same reference, same ID) without opening a
     * second epoch. A re-insert of a disconnected handle (e.g. under
     * {@code KieBaseMutabilityOption.DISABLED}) that arrives in a later propagation pass opens a
     * new epoch because {@code currentFactId} was updated by any intervening delivery; the
     * {@link DynamicFilter} guards against that case via {@link SignalAdapter#wasRegisteredDuring}.
     */
    public void beginDelivery(FactHandle factHandle) {
        long id = factHandle.getId();
        if (id != currentFactId) {
            currentFactId = id;
            startEpoch    = ++epoch;
        }
    }
}
