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
 * <p>The implementation of the <code>overlaps</code> evaluator definition.</p>
 * 
 * <p>The <b><code>overlaps</code></b> evaluator correlates two events and matches when the current event 
 * starts before the correlated event starts and finishes after the correlated event starts, but before
 * the correlated event finishes. In other words, both events have an overlapping period.</p> 
 * 
 * <p>Lets look at an example:</p>
 * 
 * <pre>$eventA : EventA( this overlaps $eventB )</pre>
 *
 * <p>The previous pattern will match if and only if:</p>
 * 
 * <pre> $eventA.startTimestamp < $eventB.startTimestamp < $eventA.endTimestamp < $eventB.endTimestamp </pre>
 * 
 * <p>The <b><code>overlaps</code></b> operator accepts 1 or 2 optional parameters as follow:</p>
 * 
 * <ul><li>If one parameter is defined, this will be the maximum distance between the start timestamp of the
 * correlated event and the end timestamp of the current event. Example:</li></lu>
 * 
 * <pre>$eventA : EventA( this overlaps[ 5s ] $eventB )</pre>
 * 
 * Will match if and only if:
 * 
 * <pre> 
 * $eventA.startTimestamp < $eventB.startTimestamp < $eventA.endTimestamp < $eventB.endTimestamp &&
 * 0 <= $eventA.endTimestamp - $eventB.startTimestamp <= 5s 
 * </pre>
 * 
 * <ul><li>If two values are defined, the first value will be the minimum distance and the second value will be 
 * the maximum distance between the start timestamp of the correlated event and the end timestamp of the current 
 * event. Example:</li></lu>
 * 
 * <pre>$eventA : EventA( this overlaps[ 5s, 10s ] $eventB )</pre>
 * 
 * Will match if and only if:
 * 
 * <pre> 
 * $eventA.startTimestamp < $eventB.startTimestamp < $eventA.endTimestamp < $eventB.endTimestamp &&
 * 5s <= $eventA.endTimestamp - $eventB.startTimestamp <= 10s 
 * </pre>
 */
public class OverlapsEvaluatorDefinition
    implements
        EvaluatorDefinition {

    public static final String overlapsOp = Operator.BuiltInOperator.OVERLAPS.getSymbol();

    public static final Operator OVERLAPS = Operator.determineOperator( overlapsOp, false );

    public static final Operator OVERLAPS_NOT = Operator.determineOperator( overlapsOp, true );

    private static final String[] SUPPORTED_IDS = new String[] { overlapsOp };

    private Map<String, OverlapsEvaluator> cache         = Collections.emptyMap();

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        cache = (Map<String, OverlapsEvaluator>) in.readObject();
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
        OverlapsEvaluator eval = this.cache.get( key );
        if ( eval == null ) {
            long[] params = TimeIntervalParser.parse( parameterText );
            eval = new OverlapsEvaluator( type,
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
     * Implements the 'overlaps' evaluator itself
     */
    public static class OverlapsEvaluator extends BaseEvaluator {
        private static final long serialVersionUID = 510l;

        private long              minDev, maxDev;
        private String            paramText;

        public OverlapsEvaluator() {
        }

        public OverlapsEvaluator(final ValueType type,
                                 final boolean isNegated,
                                 final long[] parameters,
                                 final String paramText) {
            super( type,
                   isNegated ? OVERLAPS_NOT : OVERLAPS );
            this.paramText = paramText;
            this.setParameters( parameters );
        }

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            super.readExternal( in );
            minDev = in.readLong();
            maxDev = in.readLong();
            paramText = (String) in.readObject();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal( out );
            out.writeLong( minDev );
            out.writeLong( maxDev );
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
            return new Interval( Interval.MIN,
                                 0 );
        }

        public boolean evaluate(final GlobalResolver globalResolver,
                                final ReadAccessor extractor,
                                final FactHandle object1,
                                final FieldValue object2) {
            throw new RuntimeException( "The 'overlaps' operator can only be used to compare one event to another, and never to compare to literal constraints." );
        }

        public boolean evaluateCachedRight(final GlobalResolver globalResolver,
                                           final VariableContextEntry context,
                                           final FactHandle left) {
            if ( context.rightNull || 
                    context.declaration.getExtractor().isNullValue( globalResolver, left.getObject() )) {
                return false;
            }
            long leftStartTS = ((DefaultEventHandle) left).getStartTimestamp();
            long rightEndTS = ((TemporalVariableContextEntry) context).endTS;
            long dist = rightEndTS - leftStartTS;
            return this.getOperator().isNegated() ^ ( ((TemporalVariableContextEntry) context).startTS < leftStartTS &&
                                                      rightEndTS < ((DefaultEventHandle) left).getEndTimestamp() &&
                                                      dist >= this.minDev && dist <= this.maxDev );
        }

        public boolean evaluateCachedLeft(final GlobalResolver globalResolver,
                                          final VariableContextEntry context,
                                          final FactHandle right) {
            if ( context.leftNull ||
                    context.extractor.isNullValue( globalResolver, right.getObject() ) ) {
                return false;
            }
            
            long leftStartTS = ((TemporalVariableContextEntry) context).startTS;
            long rightEndTS = ((DefaultEventHandle) right).getEndTimestamp();
            long dist = rightEndTS - leftStartTS;
            return this.getOperator().isNegated() ^ (((DefaultEventHandle) right).getStartTimestamp() < leftStartTS &&
                    rightEndTS < ((TemporalVariableContextEntry) context).endTS &&
                    dist >= this.minDev && dist <= this.maxDev );
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
            
            long startTS = ((DefaultEventHandle) handle2).getStartTimestamp();
            long endTS = ((DefaultEventHandle) handle1).getEndTimestamp();
            long dist = endTS - startTS;
            return this.getOperator().isNegated() ^ (((DefaultEventHandle) handle1).getStartTimestamp() < startTS &&
                    endTS < ((DefaultEventHandle) handle2).getEndTimestamp() &&
                    dist >= this.minDev && dist <= this.maxDev );
        }

        public String toString() {
            return "overlaps[" + ( ( paramText != null ) ? paramText : "" ) + "]";
        }

        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = super.hashCode();
            result = PRIME * result + (int) (maxDev ^ (maxDev >>> 32));
            result = PRIME * result + (int) (minDev ^ (minDev >>> 32));
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
            final OverlapsEvaluator other = (OverlapsEvaluator) obj;
            return maxDev == other.maxDev && minDev == other.minDev ;
        }

        /**
         * This methods sets the parameters appropriately.
         *
         * @param parameters
         */
        private void setParameters(long[] parameters) {
            if ( parameters == null || parameters.length == 0 ) {
                // open bounded range
                this.minDev = 1;
                this.maxDev = Long.MAX_VALUE;
            } else if ( parameters.length == 1 ) {
                // open bounded ranges
                this.minDev = 1;
                this.maxDev = parameters[0];
            } else if ( parameters.length == 2 ) {
                // open bounded ranges
                this.minDev = parameters[0];
                this.maxDev = parameters[1];
            } else {
                throw new RuntimeException( "[Overlaps Evaluator]: Not possible to use " + parameters.length + " parameters: '" + paramText + "'" );
            }
        }


    }

}
