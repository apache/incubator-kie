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
package org.drools.mvel.evaluators;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.drools.base.base.ValueType;
import org.drools.compiler.rule.builder.EvaluatorDefinition;
import org.drools.drl.parser.impl.Operator;
import org.drools.mvel.evaluators.VariableRestriction.ObjectVariableContextEntry;
import org.drools.mvel.evaluators.VariableRestriction.VariableContextEntry;
import org.drools.base.rule.accessor.Evaluator;
import org.drools.base.rule.accessor.FieldValue;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.base.rule.accessor.ReadAccessor;
import org.kie.api.runtime.rule.FactHandle;

/**
 * <p>The implementation of the 'str' evaluator definition.</p>
 * 
 * <p>The <b><code>str</code></b> compares two string values.</p> 
 * 
 * <p>Lets look at some examples:</p>
 * 
 * <pre>$m : Message( routingValue str[startsWith] "R1" )</pre>
 * <pre>$m : Message( routingValue str[endsWith] "R2" )</pre>
 * <pre>$m : Message( routingValue str[length] 17 )</pre>
 */
public class StrEvaluatorDefinition implements EvaluatorDefinition {

    protected static final String strOp = Operator.BuiltInOperator.STR.getSymbol();

    public static final Operator STR_COMPARE = Operator.determineOperator( strOp, false );
    public static final Operator NOT_STR_COMPARE = Operator.determineOperator( strOp, true );

    private static final String[] SUPPORTED_IDS = new String[] { strOp };

    public enum Operations {
        startsWith, endsWith, length;
    }

    private Evaluator[] evaluator;

    /**
     * @inheridDoc
     */
    public Evaluator getEvaluator(ValueType type, Operator operator) {
        return this.getEvaluator(type, operator.getOperatorString(), operator
                .isNegated(), null);
    }

    /**
     * @inheridDoc
     */
    public Evaluator getEvaluator(ValueType type, Operator operator,
            String parameterText) {
        return this.getEvaluator(type, operator.getOperatorString(), operator
                .isNegated(), parameterText);
    }

    /**
     * @inheridDoc
     */
    public Evaluator getEvaluator(ValueType type, String operatorId,
            boolean isNegated, String parameterText) {
        return getEvaluator(type, operatorId, isNegated, parameterText,
                Target.FACT, Target.FACT);
    }

    /**
     * @inheridDoc
     */
    public Evaluator getEvaluator(ValueType type, String operatorId,
            boolean isNegated, String parameterText, Target leftTarget,
            Target rightTarget) {
        StrEvaluator evaluator = new StrEvaluator(type, isNegated);
        evaluator.setParameterText(parameterText);
        return evaluator;
    }

    /**
     * @inheridDoc
     */
    public String[] getEvaluatorIds() {
        return SUPPORTED_IDS;
    }

    /**
     * @inheridDoc
     */
    public Target getTarget() {
        return Target.FACT;
    }

    /**
     * @inheridDoc
     */
    public boolean isNegatable() {
        return true;
    }

    /**
     * @inheridDoc
     */
    public boolean supportsType(ValueType type) {
        return true;
    }

    /**
     * @inheridDoc
     */
    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        evaluator = (Evaluator[]) in.readObject();
    }

    /**
     * @inheridDoc
     */
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(evaluator);
    }

    public static class StrEvaluator extends BaseEvaluator {
        private Operations parameter;

        public void setParameterText(String parameterText) {
            this.parameter = Operations.valueOf(parameterText);
        }

        public Operations getParameter() {
            return parameter;
        }

        public StrEvaluator() { }
        
        public StrEvaluator(final ValueType type, final boolean isNegated) {
            super(type, isNegated ? NOT_STR_COMPARE : STR_COMPARE);
        }

        /**
         * @inheridDoc
         */
        public boolean evaluate(final GlobalResolver globalResolver,
                                final ReadAccessor extractor,
                                final FactHandle factHandle,
                                final FieldValue value) {
            final Object objectValue = extractor.getValue(globalResolver, factHandle.getObject());

            switch (parameter) {
                case startsWith:
                    return this.getOperator().isNegated() ^ (((String)objectValue).startsWith( (String)value.getValue() ));
                case endsWith:
                    return this.getOperator().isNegated() ^ (((String)objectValue).endsWith( (String)value.getValue() ));
                case length:
                    return this.getOperator().isNegated() ^ (((String)objectValue).length() == value.getLongValue() );
                default:
                    throw new IllegalAccessError("Illegal str comparison parameter");
            }
        }

        public boolean evaluate(final GlobalResolver globalResolver,
                                final ReadAccessor leftExtractor, final FactHandle left,
                                final ReadAccessor rightExtractor, final FactHandle right) {
            final Object value1 = leftExtractor.getValue(globalResolver, left.getObject());
            final Object value2 = rightExtractor.getValue(globalResolver, right.getObject());

            switch (parameter) {
                case startsWith:
                    return this.getOperator().isNegated() ^ (((String)value1).startsWith( (String) value2 ));
                case endsWith:
                    return this.getOperator().isNegated() ^ (((String)value1).endsWith( (String) value2 ));
                case length:
                    return this.getOperator().isNegated() ^ (((String)value1).length() == ((Number) value2).longValue() );
                default:
                    throw new IllegalAccessError("Illegal str comparison parameter");
            }

        }

        public boolean evaluateCachedLeft(final GlobalResolver globalResolver,
                                          final VariableContextEntry context,
                                          final FactHandle right) {

                switch (parameter) {
                case startsWith:
                    return this.getOperator().isNegated() ^ (((String)right.getObject()).startsWith( (String)((ObjectVariableContextEntry)
                            context).left) );
                case endsWith:
                    return this.getOperator().isNegated() ^ (((String)right.getObject()).endsWith( (String)((ObjectVariableContextEntry)
                            context).left));
                case length:
                    return this.getOperator().isNegated() ^ (((String)right.getObject()).length() ==  ((Number)((ObjectVariableContextEntry)
                            context).left).longValue());
                default:
                    throw new IllegalAccessError("Illegal str comparison parameter");
                }

        }

        public boolean evaluateCachedRight(final GlobalResolver globalResolver,
                                           final VariableContextEntry context,
                                           final FactHandle left) {
                switch (parameter) {
                case startsWith:
                    return this.getOperator().isNegated() ^ (((String)left.getObject()).startsWith((String)((ObjectVariableContextEntry)
                            context).right));
                case endsWith:
                    return this.getOperator().isNegated() ^ (((String)left.getObject()).endsWith((String)((ObjectVariableContextEntry)
                            context).right));
                case length:
                    return this.getOperator().isNegated() ^ (((String)left.getObject()).length() == ((Number)((ObjectVariableContextEntry)
                            context).right).longValue());
                default:
                    throw new IllegalAccessError("Illegal str comparison parameter");
                }

        }

        @Override
        public String toString() {
            return "StrEvaluatorDefinition str";

        }

    }

}
