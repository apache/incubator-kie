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

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.ContextEntry;
import org.drools.base.rule.Declaration;
import org.junit.Test;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;

public class CombinedConstraintTest {

    @Test
    public void mixedStepWithNegConstraintMustEvaluateAlphaDuringLeftEvaluation() {
        // Real production AlphaNodeFieldConstraint from drools-base:
        // NegConstraint(true) checks handle.isNegated() == true.
        // For a normal (non-negated) fact handle, isAllowed() returns false.
        NegConstraint failingNegConstraint = new NegConstraint(true);
        DummyBetaConstraint passingBeta = new DummyBetaConstraint(true);

        CombinedConstraint combined = new CombinedConstraint(new AlphaNodeFieldConstraint[]{
                failingNegConstraint,
                passingBeta
        });

        CombinedConstraint.CombinedContextEntry context = combined.createContext();
        context.updateFromTuple(null, null);

        // FactHandle where isNegated() == false
        FactHandle nonNegatedHandle = new FactHandle() {
            @Override public Object getObject() { return new Object(); }
            @Override public boolean isNegated() { return false; }
            @Override public boolean isEvent() { return false; }
            @Override public long getId() { return 1L; }
            @Override public long getRecency() { return 1L; }
            @Override public <K> K as(Class<K> klass) { return null; }
            @Override public boolean isValid() { return true; }
            @Override public String toExternalForm() { return "handle:1"; }
        };

        // NegConstraint returns false for non-negated handle, so isAllowedCachedLeft must return false.
        // Without the fix, NegConstraint is skipped in isAllowedCachedLeft because !(NegConstraint instanceof BetaConstraint).
        boolean allowed = combined.isAllowedCachedLeft(context, nonNegatedHandle);
        assertThat(allowed)
                .as("NegConstraint failing inside mixed step must cause isAllowedCachedLeft to return false")
                .isFalse();
    }

    private static class DummyBetaConstraint implements AlphaNodeFieldConstraint, BetaConstraint<DummyContextEntry> {

        private final boolean allowed;

        public DummyBetaConstraint(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public boolean isAllowed(FactHandle handle, ValueResolver valueResolver) {
            return allowed;
        }

        @Override
        public boolean isAllowedCachedLeft(DummyContextEntry context, FactHandle handle) {
            return allowed;
        }

        @Override
        public boolean isAllowedCachedRight(BaseTuple tuple, DummyContextEntry context) {
            return true;
        }

        @Override
        public DummyContextEntry createContext() {
            return new DummyContextEntry();
        }

        @Override
        public Declaration[] getRequiredDeclarations() {
            return new Declaration[]{new Declaration("var", null, null)};
        }

        @Override
        public void replaceDeclaration(Declaration oldDecl, Declaration newDecl) {
        }

        @Override
        public ConstraintType getType() {
            return ConstraintType.BETA;
        }

        @Override
        public boolean isTemporal() {
            return false;
        }

        @Override
        public AlphaNodeFieldConstraint cloneIfInUse() {
            return this;
        }

        @Override
        public AlphaNodeFieldConstraint clone() {
            return this;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        }
    }

    private static class DummyContextEntry implements ContextEntry {
        @Override
        public void updateFromTuple(ValueResolver valueResolver, BaseTuple tuple) {
        }

        @Override
        public void updateFromFactHandle(ValueResolver valueResolver, FactHandle handle) {
        }

        @Override
        public void resetTuple() {
        }

        @Override
        public void resetFactHandle() {
        }

        @Override
        public ContextEntry getNext() {
            return null;
        }

        @Override
        public void setNext(ContextEntry entry) {
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        }
    }
}
