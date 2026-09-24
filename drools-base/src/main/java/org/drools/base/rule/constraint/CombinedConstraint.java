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
package org.drools.base.rule.constraint;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;
import java.util.stream.Stream;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.ContextEntry;
import org.drools.base.rule.Declaration;
import org.kie.api.runtime.rule.FactHandle;

/**
 * ANDs two or more constraints into a single constraint for use in sequence steps.
 * Supports pure-alpha steps (all children have no required declarations) and mixed
 * alpha+beta steps (at least one child references the anchor pattern).
 *
 * <p>When the step is pure-alpha, {@link #getRequiredDeclarations()} returns an empty
 * array and {@link org.drools.base.reteoo.DynamicFilter} takes the alpha path, calling
 * {@link #isAllowed(FactHandle, ValueResolver)} directly.
 *
 * <p>When the step is mixed (or pure-beta), {@link #getRequiredDeclarations()} returns
 * the aggregate of all children's declarations (non-empty), so
 * {@link org.drools.base.reteoo.DynamicFilter} takes the beta path and calls
 * {@link #isAllowedCachedLeft} with the anchor tuple. Alpha children are evaluated via
 * {@link AlphaNodeFieldConstraint#isAllowed} and beta children via
 * {@link BetaConstraint#isAllowedCachedLeft}, short-circuiting on first failure.
 */
public class CombinedConstraint
        implements AlphaNodeFieldConstraint, BetaConstraint<CombinedConstraint.CombinedContextEntry> {

    private AlphaNodeFieldConstraint[] constraints;

    private transient Declaration[] requiredDeclarations;

    public CombinedConstraint(AlphaNodeFieldConstraint[] constraints) {
        this.constraints = constraints;
    }

    @Override
    public boolean isAllowed(FactHandle handle, ValueResolver valueResolver) {
        for (AlphaNodeFieldConstraint c : constraints) {
            if (!c.isAllowed(handle, valueResolver)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Declaration[] getRequiredDeclarations() {
        if (requiredDeclarations == null) {
            requiredDeclarations = Arrays.stream(constraints)
                    .flatMap(c -> Stream.of(c.getRequiredDeclarations()))
                    .distinct()
                    .toArray(Declaration[]::new);
        }
        return requiredDeclarations;
    }

    @Override
    public void replaceDeclaration(Declaration oldDecl, Declaration newDecl) {
    }

    @Override
    public ConstraintType getType() {
        return getRequiredDeclarations().length == 0 ? ConstraintType.ALPHA : ConstraintType.BETA;
    }

    @Override
    public boolean isTemporal() {
        return false;
    }

    @Override
    public CombinedContextEntry createContext() {
        return new CombinedContextEntry();
    }

    /**
     * Evaluates all children against the supplied fact handle and the anchor tuple
     * stored in {@code context}. Alpha children (no required declarations) are
     * evaluated via {@link AlphaNodeFieldConstraint#isAllowed}; beta children are
     * evaluated via {@link BetaConstraint#isAllowedCachedLeft} with a fresh per-child
     * context populated from the anchor tuple.
     */
    @Override
    public boolean isAllowedCachedLeft(CombinedContextEntry context, FactHandle handle) {
        for (AlphaNodeFieldConstraint c : constraints) {
            if (c instanceof BetaConstraint) {
                @SuppressWarnings("unchecked")
                BetaConstraint<ContextEntry> beta = (BetaConstraint<ContextEntry>) c;
                ContextEntry ctx = beta.createContext();
                ctx.updateFromTuple(context.valueResolver, context.tuple);
                if (!beta.isAllowedCachedLeft(ctx, handle)) {
                    return false;
                }
            } else {
                if (!c.isAllowed(handle, context.valueResolver)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isAllowedCachedRight(BaseTuple tuple, CombinedContextEntry context) {
        throw new UnsupportedOperationException(
                "CombinedConstraint does not support right-cached evaluation");
    }

    @Override
    public CombinedConstraint clone() {
        throw new UnsupportedOperationException("CombinedConstraint is not cloned");
    }

    @Override
    public AlphaNodeFieldConstraint cloneIfInUse() {
        throw new UnsupportedOperationException("CombinedConstraint is not cloned");
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        throw new UnsupportedOperationException("CombinedConstraint is not serialized");
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        throw new UnsupportedOperationException("CombinedConstraint is not serialized");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CombinedConstraint)) return false;
        return Arrays.equals(constraints, ((CombinedConstraint) o).constraints);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(constraints);
    }

    // -----------------------------------------------------------------------
    // ContextEntry for the beta evaluation path
    // -----------------------------------------------------------------------

    public static class CombinedContextEntry implements ContextEntry {
        private BaseTuple     tuple;
        private ValueResolver valueResolver;

        @Override
        public void updateFromTuple(ValueResolver valueResolver, BaseTuple tuple) {
            this.valueResolver = valueResolver;
            this.tuple         = tuple;
        }

        @Override public void updateFromFactHandle(ValueResolver valueResolver, FactHandle handle) { /* not used */ }
        @Override public void resetTuple()      { tuple = null; valueResolver = null; }
        @Override public void resetFactHandle() { /* not used */ }

        @Override public ContextEntry getNext()                     { throw new UnsupportedOperationException(); }
        @Override public void         setNext(ContextEntry entry)   { throw new UnsupportedOperationException(); }

        @Override public void writeExternal(ObjectOutput out) throws IOException                          { throw new UnsupportedOperationException("CombinedContextEntry is not serialized"); }
        @Override public void readExternal(ObjectInput in)    throws IOException, ClassNotFoundException  { throw new UnsupportedOperationException("CombinedContextEntry is not serialized"); }
    }
}
