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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.drools.base.base.ValueType;
import org.drools.compiler.rule.builder.EvaluatorDefinition;
import org.drools.drl.parser.impl.Operator;
import org.drools.base.util.TimeIntervalParser;
import org.drools.core.common.DefaultEventHandle;
import org.drools.mvel.evaluators.VariableRestriction.TemporalVariableContextEntry;
import org.drools.mvel.evaluators.VariableRestriction.VariableContextEntry;
import org.drools.base.rule.accessor.Evaluator;
import org.drools.base.rule.accessor.FieldValue;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.base.time.Interval;
import org.kie.api.runtime.rule.FactHandle;

/**
 * <p>The implementation of the <code>finishes</code> evaluator definition.</p>
 * 
 * <p>The <b><code>finishes</code></b> evaluator correlates two events and matches when the current event's 
 * start timestamp happens after the correlated event's start timestamp, but both end timestamps occur at
 * the same time.</p> 
 * 
 * <p>Lets look at an example:</p>
 * 
 * <pre>$eventA : EventA( this finishes $eventB )</pre>
 *
 * <p>The previous pattern will match if and only if the $eventA starts after $eventB starts and finishes
 * at the same time $eventB finishes. In other words:</p>
 * 
 * <pre> 
 * $eventB.startTimestamp < $eventA.startTimestamp &&
 * $eventA.endTimestamp == $eventB.endTimestamp 
 * </pre>
 * 
 * <p>The <b><code>finishes</code></b> evaluator accepts one optional parameter. If it is defined, it determines
 * the maximum distance between the end timestamp of both events in order for the operator to match. Example:</p>
 * 
 * <pre>$eventA : EventA( this finishes[ 5s ] $eventB )</pre>
 * 
 * Will match if and only if:
 * 
 * <pre> 
 * $eventB.startTimestamp < $eventA.startTimestamp &&
 * abs( $eventA.endTimestamp - $eventB.endTimestamp ) <= 5s
 * </pre>
 * 
 * <p><b>NOTE:</b> it makes no sense to use a negative interval value for the parameter and the 
 * engine will raise an exception if that happens.</p>
 */
public class FinishesEvaluatorDefinition
    implements
        EvaluatorDefinition {

    protected static final String finishesOp = Operator.BuiltInOperator.FINISHES.getSymbol();

    public static final Operator FINISHES = Operator.determineOperator( finishesOp, false );
    public static final Operator FINISHES_NOT = Operator.determineOperator( finishesOp, true );

    private static final String[] SUPPORTED_IDS = new String[] { finishesOp };

    private Map<String, FinishesEvaluator>      cache         = Collections.emptyMap();

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        cache = (Map<String, FinishesEvaluator>) in.readObject();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject( cache );
    }

    /**
     * @inheridDoc
     */
    public Evaluator getEvaluator(ValueType type,
                                  Operator operator) {
        return this.getEvaluator( type,
                                  operator.getOperatorString(),
                                  operator.isNegated(),
                                  null );
    }

    /**
     * @inheridDoc
     */
    public Evaluator getEvaluator(ValueType type,
                                  Operator operator,
                                  String parameterText) {
        return this.getEvaluator( type,
                                  operator.getOperatorString(),
                                  operator.isNegated(),
                                  parameterText );
    }

    /**
     * @inheritDoc
     */
    public Evaluator getEvaluator(final ValueType type,
                                  final String operatorId,
                                  final boolean isNegated,
                                  final String parameterText) {
        return this.getEvaluator( type,
                                  operatorId,
                                  isNegated,
                                  parameterText,
                                  Target.HANDLE,
                                  Target.HANDLE );
        
    }
    
    /**
     * @inheritDoc
     */
    public Evaluator getEvaluator(final ValueType type,
                                  final String operatorId,
                                  final boolean isNegated,
                                  final String parameterText,
                                  final Target left,
                                  final Target right ) {
        if ( this.cache == Collections.EMPTY_MAP ) {
            this.cache = new HashMap<>();
        }
        String key = isNegated + ":" + parameterText;
        FinishesEvaluator eval = this.cache.get( key );
        if ( eval == null ) {
            long[] params = TimeIntervalParser.parse( parameterText );
            eval = new FinishesEvaluator( type,
                                          isNegated,
                                          params,
                                          parameterText );
            this.cache.put( key,
                            eval );
        }
        return eval;
    }

    /**
     * @inheritDoc
     */
    public String[] getEvaluatorIds() {
        return SUPPORTED_IDS;
    }

    /**
     * @inheritDoc
     */
    public boolean isNegatable() {
        return true;
    }

    /**
     * @inheritDoc
     */
    public Target getTarget() {
        return Target.HANDLE;
    }

    /**
     * @inheritDoc
     */
    public boolean supportsType(ValueType type) {
        // supports all types, since it operates over fact handles
        // Note: should we change this interface to allow checking of event classes only?
        return true;
    }

    /**
     * Implements the 'finishes' evaluator itself
     */
    public static class FinishesEvaluator extends BaseEvaluator {
        private static final long serialVersionUID = 510l;

        private long              endDev;
        private String            paramText;

        public FinishesEvaluator() {
        }

        public FinishesEvaluator(final ValueType type,
                                 final boolean isNegated,
                                 final long[] parameters,
                                 final String paramText) {
            super( type,
                   isNegated ? FINISHES_NOT : FINISHES );
            this.paramText = paramText;
            this.setParameters( parameters );
        }

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            super.readExternal( in );
            endDev = in.readLong();
            paramText = (String) in.readObject();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal( out );
            out.writeLong( endDev );
            out.writeObject( paramText );
        }

        @Override
        public boolean isTemporal() {
            return true;
        }

        @Override
        public Interval getInterval() {
            if ( this.getOperator().isNegated() ) {
                return new Interval( Interval.MIN,
                                     Interval.MAX );
            }
            return new Interval( 0,
                                 Interval.MAX );
        }

        public boolean evaluate(final GlobalResolver globalResolver,
                                final ReadAccessor extractor,
                                final FactHandle object1,
                                final FieldValue object2) {
            throw new RuntimeException( "The 'finishes' operator can only be used to compare one event to another, and never to compare to literal constraints." );
        }

        public boolean evaluateCachedRight(final GlobalResolver globalResolver,
                                           final VariableContextEntry context,
                                           final FactHandle left) {
            if ( context.rightNull || 
                    context.declaration.getExtractor().isNullValue( globalResolver, left.getObject() )) {
                return false;
            }
            
            long distStart = ((TemporalVariableContextEntry) context).startTS - ((DefaultEventHandle) left).getStartTimestamp();
            long distEnd = Math.abs(((DefaultEventHandle) left).getEndTimestamp() - ((TemporalVariableContextEntry) context).endTS);
            return this.getOperator().isNegated() ^ (distStart > 0 && distEnd <= this.endDev);
        }

        public boolean evaluateCachedLeft(GlobalResolver globalResolver,
                                          final VariableContextEntry context,
                                          final FactHandle right) {
            if ( context.leftNull ||
                    context.extractor.isNullValue( globalResolver, right.getObject() ) ) {
                return false;
            }
            
            long distStart = ((DefaultEventHandle) right).getStartTimestamp() - ((TemporalVariableContextEntry) context).startTS;
            long distEnd = Math.abs( ((TemporalVariableContextEntry) context).endTS - ((DefaultEventHandle) right).getEndTimestamp());
            return this.getOperator().isNegated() ^ (distStart > 0 && distEnd <= this.endDev);
        }

        public boolean evaluate(final GlobalResolver globalResolver,
                                final ReadAccessor extractor1,
                                final FactHandle handle1,
                                final ReadAccessor extractor2,
                                final FactHandle handle2) {
            if ( extractor1.isNullValue( globalResolver, handle1.getObject() ) ||
                    extractor2.isNullValue( globalResolver, handle2.getObject() ) ) {
                return false;
            }
            
            long distStart = ((DefaultEventHandle) handle1).getStartTimestamp() - ((DefaultEventHandle) handle2).getStartTimestamp();
            long distEnd = Math.abs(((DefaultEventHandle) handle2).getEndTimestamp() - ((DefaultEventHandle) handle1).getEndTimestamp());
            return this.getOperator().isNegated() ^ (distStart > 0 && distEnd <= this.endDev);
        }

        public String toString() {
            return "finishes[" + ((paramText != null) ? paramText : "") + "]";
        }

        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = super.hashCode();
            result = PRIME * result + (int) (endDev ^ (endDev >>> 32));
            return result;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if ( this == obj ) return true;
            if ( !super.equals( obj ) ) return false;
            if ( getClass() != obj.getClass() ) return false;
            final FinishesEvaluator other = (FinishesEvaluator) obj;
            return endDev == other.endDev;
        }

        /**
         * This methods sets the parameters appropriately.
         *
         * @param parameters
         */
        private void setParameters(long[] parameters) {
            if ( parameters == null || parameters.length == 0 ) {
                this.endDev = 0;
            } else if ( parameters.length == 1 ) {
                if( parameters[0] >= 0 ) {
                    // defined deviation for end timestamp
                    this.endDev = parameters[0];
                } else {
                    throw new RuntimeException("[Finishes Evaluator]: Not possible to use negative parameter: '" + paramText + "'");
                }
            } else {
                throw new RuntimeException( "[Finishes Evaluator]: Not possible to use " + parameters.length + " parameters: '" + paramText + "'" );
            }
        }

    }

}
