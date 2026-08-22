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
import org.drools.mvel.evaluators.VariableRestriction.LeftStartRightEndContextEntry;
import org.drools.mvel.evaluators.VariableRestriction.VariableContextEntry;
import org.drools.base.rule.accessor.Evaluator;
import org.drools.base.rule.accessor.FieldValue;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.base.time.Interval;
import org.kie.api.runtime.rule.FactHandle;

/**
 * <p>The implementation of the <code>meets</code> evaluator definition.</p>
 * 
 * <p>The <b><code>meets</code></b> evaluator correlates two events and matches when the current event's 
 * end timestamp happens at the same time as the correlated event's start timestamp.</p> 
 * 
 * <p>Lets look at an example:</p>
 * 
 * <pre>$eventA : EventA( this meets $eventB )</pre>
 *
 * <p>The previous pattern will match if and only if the $eventA finishes at the same time $eventB starts. 
 * In other words:</p>
 * 
 * <pre> 
 * abs( $eventB.startTimestamp - $eventA.endTimestamp ) == 0
 * </pre>
 * 
 * <p>The <b><code>meets</code></b> evaluator accepts one optional parameter. If it is defined, it determines
 * the maximum distance between the end timestamp of current event and the start timestamp of the correlated
 * event in order for the operator to match. Example:</p>
 * 
 * <pre>$eventA : EventA( this meets[ 5s ] $eventB )</pre>
 * 
 * Will match if and only if:
 * 
 * <pre> 
 * abs( $eventB.startTimestamp - $eventA.endTimestamp) <= 5s 
 * </pre>
 * 
 * <p><b>NOTE:</b> it makes no sense to use a negative interval value for the parameter and the 
 * engine will raise an exception if that happens.</p>
 */
public class MeetsEvaluatorDefinition
    implements
        EvaluatorDefinition {

    protected static final String meetsOp = Operator.BuiltInOperator.MEETS.getSymbol();

    public static final Operator MEETS = Operator.determineOperator( meetsOp, false );

    public static final Operator MEETS_NOT = Operator.determineOperator( meetsOp, true );

    private static final String[] SUPPORTED_IDS = new String[] { meetsOp };

    private Map<String, MeetsEvaluator> cache         = Collections.emptyMap();

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        cache = (Map<String, MeetsEvaluator>) in.readObject();
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
        MeetsEvaluator eval = this.cache.get( key );
        if ( eval == null ) {
            long[] params = TimeIntervalParser.parse( parameterText );
            eval = new MeetsEvaluator( type,
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
     * Implements the 'meets' evaluator itself
     */
    public static class MeetsEvaluator extends BaseEvaluator {
        private static final long serialVersionUID = 510l;

        private long              finalRange;
        private String            paramText;

        public MeetsEvaluator() {
        }

        public MeetsEvaluator(final ValueType type,
                              final boolean isNegated,
                              final long[] parameters,
                              final String paramText) {
            super( type,
                   isNegated ? MEETS_NOT : MEETS );
            this.paramText = paramText;
            this.setParameters( parameters );
        }

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            super.readExternal( in );
            finalRange = in.readLong();
            paramText = (String) in.readObject();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal( out );
            out.writeLong( finalRange );
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
            throw new RuntimeException( "The 'meets' operator can only be used to compare one event to another, and never to compare to literal constraints." );
        }

        public boolean evaluateCachedRight(final GlobalResolver globalResolver,
                                           final VariableContextEntry context,
                                           final FactHandle left) {
            if ( context.rightNull || 
                    context.declaration.getExtractor().isNullValue( globalResolver, left.getObject() )) {
                return false;
            }
            
            long leftStartTS = ((DefaultEventHandle) left).getStartTimestamp();
            long dist = Math.abs( leftStartTS - ((LeftStartRightEndContextEntry) context).timestamp );
            return this.getOperator().isNegated() ^ (dist <= this.finalRange);
        }

        public boolean evaluateCachedLeft(final GlobalResolver globalResolver,
                                          final VariableContextEntry context,
                                          final FactHandle right) {
            if ( context.leftNull ||
                    context.extractor.isNullValue( globalResolver, right.getObject() ) ) {
                return false;
            }
            
            long leftStartTS =  ((LeftStartRightEndContextEntry) context).timestamp;
            long dist = Math.abs( leftStartTS - ((DefaultEventHandle) right).getEndTimestamp());
            return this.getOperator().isNegated() ^ (dist <= this.finalRange);
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
            
            long obj2StartTS = ((DefaultEventHandle) handle2).getStartTimestamp();
            long dist = Math.abs( obj2StartTS - ((DefaultEventHandle) handle1).getEndTimestamp());
            return this.getOperator().isNegated() ^ (dist <= this.finalRange);
        }

        public String toString() {
            return "meets[" + ((paramText != null) ? paramText : "") + "]";
        }

        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = super.hashCode();
            result = PRIME * result + (int) (finalRange ^ (finalRange >>> 32));
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
            final MeetsEvaluator other = (MeetsEvaluator) obj;
            return finalRange == other.finalRange;
        }

        /**
         * This methods sets the parameters appropriately.
         *
         * @param parameters
         */
        private void setParameters(long[] parameters) {
            if ( parameters == null || parameters.length == 0 ) {
                this.finalRange = 0;
            } else if ( parameters.length == 1 ) {
                if ( parameters[0] >= 0 ) {
                    // defined max distance
                    this.finalRange = parameters[0];
                } else {
                    throw new RuntimeException( "[Meets Evaluator]: Not possible to use negative parameter: '" + paramText + "'" );
                }
            } else {
                throw new RuntimeException( "[Meets Evaluator]: Not possible to use " + parameters.length + " parameters: '" + paramText + "'" );
            }
        }
    }
}
