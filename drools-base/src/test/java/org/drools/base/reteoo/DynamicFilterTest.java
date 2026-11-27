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

import java.lang.reflect.Field;

import org.drools.base.base.ValueResolver;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.constraint.NegConstraint;
import org.junit.Test;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DynamicFilterTest {

    @Test
    public void adapterRegisteredBeforeEpochCounterWrapsStillReceivesSignal() throws Exception {
        // NegConstraint(false) allows any non-negated handle and has no required declarations (alpha path).
        DeliveryClock clock = new DeliveryClock();
        DynamicFilter filter = new DynamicFilter(new DynamicFilterProto(new NegConstraint(false), 0), clock);

        // Put the delivery epoch counter on the edge of overflow, as after ~2^31 deliveries on one node.
        setEpoch(clock, Integer.MAX_VALUE);

        RecordingSignalAdapter adapter = new RecordingSignalAdapter();
        filter.addSignalAdapter(adapter);

        // This assertObject increments the epoch counter past Integer.MAX_VALUE.
        filter.assertObject(new TestFactHandle(), null);

        assertThat(adapter.received)
                .as("a live adapter must still receive the signal when the delivery epoch counter wraps")
                .isEqualTo(1);
    }

    @Test
    public void adapterRegisteredBeforeEpochCounterWrapsAtLongMaxStillReceivesSignal() throws Exception {
        // Same invariant as the Integer.MAX_VALUE test but exercising the true long wrap:
        // epoch overflows Long.MAX_VALUE → Long.MIN_VALUE, making startEpoch negative.
        // A pre-existing adapter has registrationEpoch == Long.MAX_VALUE; the guard
        //   registrationEpoch >= startEpoch  →  Long.MAX_VALUE >= Long.MIN_VALUE  →  true
        // must NOT fire: the adapter was registered before the delivery, so it must receive.
        DeliveryClock clock = new DeliveryClock();
        DynamicFilter filter = new DynamicFilter(new DynamicFilterProto(new NegConstraint(false), 0), clock);

        // Seed at Long.MAX_VALUE - 1 so the first assertObject increments to Long.MAX_VALUE
        // (registrationEpoch of the adapter) and the second assertObject wraps to Long.MIN_VALUE.
        setEpoch(clock, Long.MAX_VALUE - 1);

        // First delivery: epoch becomes Long.MAX_VALUE.
        filter.assertObject(new TestFactHandle(), null);

        // Register the adapter while epoch == Long.MAX_VALUE.
        RecordingSignalAdapter adapter = new RecordingSignalAdapter();
        filter.addSignalAdapter(adapter);

        // Second delivery: epoch wraps to Long.MIN_VALUE; startEpoch == Long.MIN_VALUE.
        // The adapter's registrationEpoch == Long.MAX_VALUE, which is > Long.MIN_VALUE,
        // so the guard fires and the adapter is skipped — this is the bug under test.
        filter.assertObject(new TestFactHandle(), null);

        assertThat(adapter.received)
                .as("a live adapter must still receive the signal when the delivery epoch counter wraps at Long.MAX_VALUE")
                .isEqualTo(1);
    }

    @Test
    public void nonBetaConstraintWithRequiredDeclarationsFailsWithExplanatoryException() {
        // A constraint that is NOT a BetaConstraint but reports required declarations: test() must fail
        // with a clear IllegalStateException rather than a bare ClassCastException.
        NegConstraint nonBetaWithDeclarations = new NegConstraint(false) {
            @Override
            public Declaration[] getRequiredDeclarations() {
                return new Declaration[]{new Declaration("var", null, null)};
            }
        };
        DynamicFilter filter = new DynamicFilter(new DynamicFilterProto(nonBetaWithDeclarations, 0), new DeliveryClock());
        filter.addSignalAdapter(new RecordingSignalAdapter());

        assertThatThrownBy(() -> filter.assertObject(new TestFactHandle(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-beta constraint");
    }

    @Test
    public void filterDeactivatedByEarlierSnapshotEntryIsSkippedWithinDelivery() {
        // Scenario: two filters on the same adapter (same object type), representing two
        // different active sequences waiting at the same step. When the fact arrives:
        //   1. Filter A receives it and, as a side-effect, removes all signal adapters from
        //      filter B (simulating a step advance or reset that deactivates filter B's step).
        //   2. The snapshot walk must detect isEmpty() on filter B and skip it entirely.
        //
        // This is the within-delivery deactivation case that isEmpty() was designed for.
        // It is distinct from agenda interleaving, which cannot occur on the single engine thread.
        DeliveryClock clock = new DeliveryClock();
        DynamicFilter filterA = new DynamicFilter(new DynamicFilterProto(new NegConstraint(false), 0), clock);
        DynamicFilter filterB = new DynamicFilter(new DynamicFilterProto(new NegConstraint(false), 0), clock);

        RecordingSignalAdapter adapterB = new RecordingSignalAdapter();
        filterB.addSignalAdapter(adapterB);

        // adapterA's receive() empties filterB's adapter list (simulates a step transition).
        RecordingSignalAdapter adapterA = new RecordingSignalAdapter() {
            @Override
            public void receive(ValueResolver reteEvaluator, FactHandle factHandle) {
                super.receive(reteEvaluator, factHandle);
                filterB.removeSignalAdapter(adapterB);
            }
        };
        filterA.addSignalAdapter(adapterA);

        // Simulate the snapshot loop in AlphaAdapter.assertObject: deliver to filterA first,
        // then to filterB — but only if its adapter list is non-empty.
        TestFactHandle fact = new TestFactHandle();
        clock.beginDelivery(fact);
        filterA.assertObject(fact, null);
        if (!filterB.getSignalAdapters().isEmpty()) {
            filterB.assertObject(fact, null);
        }

        assertThat(adapterA.received).isEqualTo(1);
        assertThat(adapterB.received)
                .as("filter B was deactivated by filter A's delivery; it must be skipped")
                .isZero();
    }

    @Test
    public void adapterRegisteredWhileFactIsInFlightDoesNotReceiveItOnAnotherFilterOfTheSameClock() {
        // Two filters share one clock, as filters of different object-type adapters of one sequence node do.
        // The fact is delivered to filter A, which registers an adapter on filter B; B then handles the same
        // fact and must not hand it to that adapter.
        DeliveryClock clock = new DeliveryClock();
        DynamicFilter filterA = new DynamicFilter(new DynamicFilterProto(new NegConstraint(false), 0), clock);
        DynamicFilter filterB = new DynamicFilter(new DynamicFilterProto(new NegConstraint(false), 1), clock);
        RecordingSignalAdapter lateAdapter = new RecordingSignalAdapter();
        RecordingSignalAdapter registrar = new RecordingSignalAdapter() {
            @Override
            public void receive(ValueResolver reteEvaluator, FactHandle factHandle) {
                super.receive(reteEvaluator, factHandle);
                filterB.addSignalAdapter(lateAdapter);
            }
        };
        filterA.addSignalAdapter(registrar);

        TestFactHandle fact = new TestFactHandle();
        filterA.assertObject(fact, null);
        filterB.assertObject(fact, null);

        assertThat(registrar.received).isEqualTo(1);
        assertThat(lateAdapter.received)
                .as("an adapter registered while the fact is in flight must not receive that same fact")
                .isZero();

        filterB.assertObject(new TestFactHandle(), null);
        assertThat(lateAdapter.received)
                .as("the same adapter receives the next fact")
                .isEqualTo(1);
    }

    @Test
    public void adapterRegisteredDuringDeliveryDoesNotReceiveSameHandleOnRedelivery() {
        // Simulates the KieBaseMutabilityOption.DISABLED re-insert scenario:
        // a handle that consumed step-1 (advancing the sequence and registering the step-2 adapter)
        // is re-inserted in a later propagation pass. The step-2 adapter must not fire on it.
        DeliveryClock clock = new DeliveryClock();
        DynamicFilter step1Filter = new DynamicFilter(new DynamicFilterProto(new NegConstraint(false), 0), clock);
        DynamicFilter step2Filter = new DynamicFilter(new DynamicFilterProto(new NegConstraint(false), 0), clock);

        RecordingSignalAdapter step2Adapter = new RecordingSignalAdapter();

        // step1Adapter: when it receives the first ball, registers step2Adapter (simulates step advance).
        RecordingSignalAdapter step1Adapter = new RecordingSignalAdapter() {
            @Override
            public void receive(ValueResolver reteEvaluator, FactHandle factHandle) {
                super.receive(reteEvaluator, factHandle);
                step2Filter.addSignalAdapter(step2Adapter);
            }
        };
        step1Filter.addSignalAdapter(step1Adapter);

        // First delivery of "ball": step1 consumed, step2 adapter registered.
        TestFactHandle ball = new TestFactHandle();
        step1Filter.assertObject(ball, null);

        assertThat(step1Adapter.received).isEqualTo(1);
        assertThat(step2Adapter.received).as("step-2 must not fire during step-1 delivery").isZero();

        // Deliver an intervening fact so that DeliveryClock no longer tracks ball as the current fact.
        // (With NegConstraint(false) the constraint accepts everything, so other advances step2 once.)
        step2Filter.assertObject(new TestFactHandle(), null);
        assertThat(step2Adapter.received).as("other fact advances step-2 once").isEqualTo(1);

        // Re-delivery of ball (simulates KieBaseMutabilityOption.DISABLED re-insert after update).
        step2Filter.assertObject(ball, null);

        assertThat(step2Adapter.received)
                .as("re-delivery of the handle that advanced step-1 must not fire step-2 again")
                .isEqualTo(1);   // must NOT have incremented

        // A genuinely new fact (different handle, same type) must still be delivered.
        step2Filter.assertObject(new TestFactHandle(), null);
        assertThat(step2Adapter.received)
                .as("a new fact must still be delivered to the step-2 adapter")
                .isEqualTo(2);
    }

    @Test
    public void adapterRegisteredAfterClockResetReceivesHandleWithRecycledId() {
        // Session.reset() restarts handle IDs while the node memory is reset in place.
        // A stale currentFactId in the clock must not make the first post-reset fact look "in flight".
        DeliveryClock clock = new DeliveryClock();
        DynamicFilter filter = new DynamicFilter(new DynamicFilterProto(new NegConstraint(false), 0), clock);

        filter.addSignalAdapter(new RecordingSignalAdapter());
        filter.assertObject(new TestFactHandle(5), null);          // last pre-reset delivery, id 5

        clock.reset();

        RecordingSignalAdapter adapter = new RecordingSignalAdapter();
        filter.addSignalAdapter(adapter);                           // anchor re-registers after reset
        filter.assertObject(new TestFactHandle(5), null);          // IDs restarted: new fact reuses id 5

        assertThat(adapter.received)
                .as("a fact whose ID equals the last pre-reset delivery must reach an adapter registered after reset")
                .isEqualTo(1);
    }

    private static void setEpoch(DeliveryClock clock, long value) throws Exception {
        Field field = DeliveryClock.class.getDeclaredField("epoch");
        field.setAccessible(true);
        field.setLong(clock, value);
    }

    private static class RecordingSignalAdapter extends SignalAdapter {
        int received;

        RecordingSignalAdapter() {
            super(null, 0, 0, null);
        }

        @Override
        public BaseTuple getAnchorTuple() {
            return null;
        }

        @Override
        public void receive(ValueResolver reteEvaluator, FactHandle factHandle) {
            received++;
        }
    }

    private static class TestFactHandle implements FactHandle {
        private static long nextId = 1;

        private final long id;

        TestFactHandle() {
            this.id = nextId++;
        }

        TestFactHandle(long id) {
            this.id = id;
        }

        @Override public Object getObject() { return new Object(); }
        @Override public boolean isNegated() { return false; }
        @Override public boolean isEvent() { return false; }
        @Override public long getId() { return id; }
        @Override public long getRecency() { return 1L; }
        @Override public <K> K as(Class<K> klass) { return null; }
        @Override public boolean isValid() { return true; }
        @Override public String toExternalForm() { return "handle:" + id; }
    }
}
