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
package org.drools.mvel;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.IntervalProviderConstraint;
import org.drools.base.rule.MutableTypeConstraint;
import org.drools.base.rule.accessor.Evaluator;
import org.drools.base.rule.accessor.FieldValue;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.base.time.Interval;
import org.drools.mvel.evaluators.MvelEvaluator;
import org.drools.mvel.evaluators.VariableRestriction;
import org.drools.mvel.evaluators.VariableRestriction.VariableContextEntry;
import org.kie.api.runtime.rule.FactHandle;
import org.drools.base.rule.ContextEntry;

public class EvaluatorConstraint extends MutableTypeConstraint<ContextEntry> implements IntervalProviderConstraint {

    protected Declaration[] declarations;
    protected Evaluator evaluator;
    protected ReadAccessor rightReadAccessor;
    protected FieldValue field;

    public EvaluatorConstraint() { }

    public EvaluatorConstraint(FieldValue field, Evaluator evaluator, ReadAccessor extractor) {
        this.field = field;
        this.declarations = new Declaration[0];
        this.evaluator = evaluator;
        this.rightReadAccessor = extractor;
    }

    public EvaluatorConstraint(Declaration[] declarations, Evaluator evaluator, ReadAccessor extractor) {
        this.declarations = declarations;
        this.evaluator = evaluator;
        this.rightReadAccessor = extractor;
    }

    protected boolean isLiteral() {
        return declarations.length == 0;
    }

    public boolean isAllowed(FactHandle handle, ValueResolver valueResolver) {
        if (isLiteral()) {
            return evaluator.evaluate(valueResolver.getGlobalResolver(), rightReadAccessor, handle, field);
        }

        return evaluator.evaluate( valueResolver.getGlobalResolver(),
                                   rightReadAccessor,
                                   handle,
                                   declarations[0].getExtractor(),
                                   handle );
    }

    public boolean isAllowedCachedLeft(ContextEntry context, FactHandle handle) {
        if (isLiteral()) {
            return evaluator.evaluate( ((LiteralContextEntry) context).valueResolver.getGlobalResolver(),
                                       ((LiteralContextEntry) context).getFieldExtractor(),
                                       handle,
                                       field );
        }

        return ((MvelEvaluator) evaluator).evaluateCachedLeft( ((VariableContextEntry) context).valueResolver.getGlobalResolver(),
                                             (VariableContextEntry) context,
                                             handle );
    }

    public boolean isAllowedCachedRight(BaseTuple tuple, ContextEntry context) {
        if (isLiteral()) {
            return evaluator.evaluate( ((LiteralContextEntry) context).valueResolver.getGlobalResolver(),
                                       ((LiteralContextEntry) context).getFieldExtractor(),
                                       ((LiteralContextEntry) context).getFactHandle(),
                                       field );
        }

        return ((MvelEvaluator) evaluator).evaluateCachedRight( ((VariableContextEntry) context).valueResolver.getGlobalResolver(),
                                              (VariableContextEntry) context,
                                              tuple.get(declarations[0]));
    }

    public void replaceDeclaration(Declaration oldDecl, Declaration newDecl) {
        if ( declarations[0].equals( oldDecl ) ) {
            declarations[0] = newDecl;
        }
    }

    public Declaration[] getRequiredDeclarations() {
        return declarations;
    }

    public boolean isTemporal() {
        return evaluator != null && evaluator.isTemporal();
    }

    public boolean isSelf() {
        return rightReadAccessor.isSelfReference();
    }

    public Interval getInterval() {
        return evaluator == null ? null : evaluator.getInterval();
    }

    public Evaluator getEvaluator() {
        return evaluator;
    }

    public FieldValue getField() {
        return field;
    }

    protected ReadAccessor getRightReadAccessor() {
        return rightReadAccessor;
    }

    public EvaluatorConstraint clone() {
        if (isLiteral()) {
            return new EvaluatorConstraint(field, evaluator, rightReadAccessor);
        }

        Declaration[] clonedDeclarations = new Declaration[declarations.length];
        System.arraycopy(declarations, 0, clonedDeclarations, 0, declarations.length);
        return new EvaluatorConstraint(clonedDeclarations, evaluator, rightReadAccessor);
    }

    public ContextEntry createContext() {
        return isLiteral() ? new LiteralContextEntry(rightReadAccessor) : VariableRestriction.createContextEntry(rightReadAccessor, declarations[0], evaluator);
    }

    // Externalizable

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeObject(field);
        out.writeObject(declarations);
        out.writeObject(rightReadAccessor);
        out.writeObject(evaluator);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        field = (FieldValue) in.readObject();
        declarations = (Declaration[]) in.readObject();
        rightReadAccessor = (ReadAccessor) in.readObject();
        evaluator = (Evaluator) in.readObject();
    }

    protected static class LiteralContextEntry implements ContextEntry {

        private static final long   serialVersionUID = 510l;
        public ReadAccessor         extractor;
        public FactHandle           factHandle;
        public ContextEntry         next;
        public ValueResolver        valueResolver;

        public LiteralContextEntry() {
        }

        public LiteralContextEntry(final ReadAccessor extractor) {
            this.extractor = extractor;
        }

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            extractor = (ReadAccessor) in.readObject();
            factHandle = ( FactHandle ) in.readObject();
            next = (ContextEntry) in.readObject();
            valueResolver = ( ValueResolver ) in .readObject();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject( extractor );
            out.writeObject( factHandle );
            out.writeObject( next );
            out.writeObject( valueResolver );
        }

        public ReadAccessor getFieldExtractor() {
            return this.extractor;
        }

        public FactHandle getFactHandle() {
            return this.factHandle;
        }

        public ContextEntry getNext() {
            return this.next;
        }

        public void setNext(final ContextEntry entry) {
            this.next = entry;
        }

        public void updateFromFactHandle(final ValueResolver valueResolver,
                                         final FactHandle handle) {
            this.factHandle = handle;
            this.valueResolver = valueResolver;
        }

        public void updateFromTuple(final ValueResolver valueResolver,
                                    final BaseTuple tuple) {
            this.valueResolver = valueResolver;
        }

        public void resetTuple() {
        }

        public void resetFactHandle() {
            this.factHandle = null;
        }

    }

    @Override
    public boolean equals( Object o ) {
        if ( this == o ) return true;
        if ( !( o instanceof EvaluatorConstraint ) ) return false;

        EvaluatorConstraint that = (EvaluatorConstraint) o;

        if ( !Arrays.equals( declarations, that.declarations ) ) return false;
        if ( evaluator != null ? !evaluator.equals( that.evaluator ) : that.evaluator != null ) return false;
        if ( field != null ? !field.equals( that.field ) : that.field != null ) return false;
        if ( rightReadAccessor != null ? !rightReadAccessor.equals( that.rightReadAccessor ) : that.rightReadAccessor != null )
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = declarations != null ? Arrays.hashCode( declarations ) : 0;
        result = 31 * result + ( evaluator != null ? evaluator.hashCode() : 0 );
        result = 31 * result + ( rightReadAccessor != null ? rightReadAccessor.hashCode() : 0 );
        result = 31 * result + ( field != null ? field.hashCode() : 0 );
        return result;
    }
}
