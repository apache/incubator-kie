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

    public DynamicFilter(DynamicFilterProto proto) {
        this.constraint    = proto.getConstraint();
        this.adapterIndex  = proto.getAdapterIndex();
        this.signalAdapters = new LinkedList<>();
    }

    public int getAdapterIndex() {
        return adapterIndex;
    }

    public void addSignalAdapter(SignalAdapter signalAdapter) {
        signalAdapters.add(signalAdapter);
    }

    public void removeSignalAdapter(SignalAdapter signalAdapter) {
        signalAdapters.remove(signalAdapter);
    }

    public LinkedList<SignalAdapter> getSignalAdapters() {
        return signalAdapters;
    }

    public void assertObject(final FactHandle factHandle,
                             final ValueResolver valueResolver) {

        if (constraint.getRequiredDeclarations().length == 0) {
            if (constraint.isAllowed(factHandle, valueResolver)) {
                for (SignalAdapter current = signalAdapters.getFirst(); current != null; ) {
                    SignalAdapter next = current.getNext();
                    current.receive(valueResolver, factHandle);
                    current = next;
                }
            }
        } else {
            for (SignalAdapter current = signalAdapters.getFirst(); current != null; ) {
                SignalAdapter next = current.getNext();
                if (test(factHandle, valueResolver, current.getAnchorTuple())) {
                    current.receive(valueResolver, factHandle);
                }
                current = next;
            }
        }
    }

    private boolean test(FactHandle factHandle, ValueResolver valueResolver, BaseTuple tuple) {
        if (constraint instanceof BetaConstraint) {
            BetaConstraint<ContextEntry> betaConstraint = (BetaConstraint<ContextEntry>) constraint;
            ContextEntry ctx = betaConstraint.createContext();
            ctx.updateFromTuple(valueResolver, tuple);
            return betaConstraint.isAllowedCachedLeft(ctx, factHandle);
        }
        throw new IllegalStateException("test() called with a non-beta constraint: " + constraint.getClass());
    }
}
