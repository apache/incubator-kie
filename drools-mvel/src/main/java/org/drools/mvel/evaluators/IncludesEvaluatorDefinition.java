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

import org.drools.base.base.ValueResolver;
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
 * <p>The implementation of the <code>includes</code> evaluator definition.</p>
 * 
 * <p>The <b><code>includes</code></b> evaluator correlates two events and matches when the event 
 * being correlated happens during the current event. It is the symmetrical opposite of <code>during</code>
 * evaluator.</p> 
 * 
 * <p>Lets look at an example:</p>
 * 
 * <pre>$eventA : EventA( this includes $eventB )</pre>
 *
 * <p>The previous pattern will match if and only if the $eventB starts after $eventA starts and finishes
 * before $eventA finishes. In other words:</p>
 * 
 * <pre> $eventA.startTimestamp < $eventB.startTimestamp <= $eventB.endTimestamp < $eventA.endTimestamp </pre>
 * 
 * <p>The <b><code>includes</code></b> operator accepts 1, 2 or 4 optional parameters as follow:</p>
 * 
 * <ul><li>If one value is defined, this will be the maximum distance between the start timestamp of both
 * event and the maximum distance between the end timestamp of both events in order to operator match. Example:</li></lu>
 * 
 * <pre>$eventA : EventA( this includes[ 5s ] $eventB )</pre>
 * 
 * Will match if and only if:
 * 
 * <pre> 
 * 0 < $eventB.startTimestamp - $eventA.startTimestamp <= 5s &&
 * 0 < $eventA.endTimestamp - $eventB.endTimestamp <= 5s
 * </pre>
 * 
 * <ul><li>If two values are defined, the first value will be the minimum distance between the timestamps
 * of both events, while the second value will be the maximum distance between the timestamps of both events. 
 * Example:</li></lu>
 * 
 * <pre>$eventA : EventA( this includes[ 5s, 10s ] $eventB )</pre>
 * 
 * Will match if and only if:
 * 
 * <pre> 
 * 5s <= $eventB.startTimestamp - $eventA.startTimestamp <= 10s &&
 * 5s <= $eventA.endTimestamp - $eventB.endTimestamp <= 10s
 * </pre>
 * 
 * <ul><li>If four values are defined, the first two values will be the minimum and maximum distances between the 
 * start timestamp of both events, while the last two values will be the minimum and maximum distances between the 
 * end timestamp of both events. Example:</li></lu>
 * 
 * <pre>$eventA : EventA( this includes[ 2s, 6s, 4s, 10s ] $eventB )</pre>
 * 
 * Will match if and only if:
 * 
 * <pre> 
 * 2s <= $eventB.startTimestamp - $eventA.startTimestamp <= 6s &&
 * 4s <= $eventA.endTimestamp - $eventB.endTimestamp <= 10s
 * </pre>
 */
public class IncludesEvaluatorDefinition
    implements
        EvaluatorDefinition {

    public static final String includesOp = Operator.BuiltInOperator.INCLUDES.getSymbol();

    public static final Operator INCLUDES = Operator.determineOperator( includesOp, false );

    public static final Operator INCLUDES_NOT = Operator.determineOperator( includesOp, true );

    private static final String[] SUPPORTED_IDS = new String[] { includesOp };

    private Map<String, IncludesEvaluator> cache       = Collections.emptyMap();

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        cache = (Map<String, IncludesEvaluator>) in.readObject();
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
        IncludesEvaluator eval = this.cache.get( key );
        if ( eval == null ) {
            long[] params = TimeIntervalParser.parse( parameterText );
            eval = new IncludesEvaluator( type,
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
     * Implements the 'includes' evaluator itself
     */
    public static class IncludesEvaluator extends BaseEvaluator {
        private static final long serialVersionUID = 510l;

        private long              startMinDev, startMaxDev;
        private long              endMinDev, endMaxDev;
        private String            paramText;

        public IncludesEvaluator() {
        }

        public IncludesEvaluator(final ValueType type,
                                 final boolean isNegated,
                                 final long[] parameters,
                                 final String paramText) {
            super( type,
                   isNegated ? INCLUDES_NOT : INCLUDES );
            this.paramText = paramText;
            this.setParameters( parameters );
        }

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            super.readExternal( in );
            startMinDev = in.readLong();
            startMaxDev = in.readLong();
            endMinDev = in.readLong();
            endMaxDev = in.readLong();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal( out );
            out.writeLong( startMinDev );
            out.writeLong( startMaxDev );
            out.writeLong( endMinDev );
            out.writeLong( endMaxDev );
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
            throw new RuntimeException( "The 'includes' operator can only be used to compare one event to another, and never to compare to literal constraints." );
        }

        public boolean evaluateCachedRight(final GlobalResolver globalResolver,
                                           final VariableContextEntry context,
                                           final FactHandle left) {
            if ( context.rightNull || 
                    context.declaration.getExtractor().isNullValue( globalResolver, left.getObject() )) {
                return false;
            }
            
            long distStart = ((DefaultEventHandle) left).getStartTimestamp() - ((TemporalVariableContextEntry) context).startTS;
            long distEnd = ((TemporalVariableContextEntry) context).endTS - ((DefaultEventHandle) left).getEndTimestamp();
            return this.getOperator().isNegated() ^ (distStart >= this.startMinDev && distStart <= this.startMaxDev && distEnd >= this.endMinDev && distEnd <= this.endMaxDev);
        }

        public boolean evaluateCachedLeft(final GlobalResolver globalResolver,
                                          final VariableContextEntry context,
                                          final FactHandle right) {
            if ( context.leftNull ||
                    context.extractor.isNullValue( globalResolver, right.getObject() ) ) {
                return false;
            }
            
            long distStart = ((TemporalVariableContextEntry) context).startTS- ((DefaultEventHandle) right).getStartTimestamp();
            long distEnd = ((DefaultEventHandle) right).getEndTimestamp() - ((TemporalVariableContextEntry) context).endTS;
            return this.getOperator().isNegated() ^ (distStart >= this.startMinDev && distStart <= this.startMaxDev && distEnd >= this.endMinDev && distEnd <= this.endMaxDev);
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
            
            long distStart = ((DefaultEventHandle) handle2).getStartTimestamp() - ((DefaultEventHandle) handle1).getStartTimestamp();
            long distEnd = ((DefaultEventHandle) handle1).getEndTimestamp() - ((DefaultEventHandle) handle2).getEndTimestamp();
            return this.getOperator().isNegated() ^ (distStart >= this.startMinDev && distStart <= this.startMaxDev && distEnd >= this.endMinDev && distEnd <= this.endMaxDev);
        }

        public String toString() {
            return "includes[" + startMinDev + ", " + startMaxDev + ", " + endMinDev + ", " + endMaxDev + "]";
        }

        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int PRIME = 31;
            int result = super.hashCode();
            result = PRIME * result + (int) (endMaxDev ^ (endMaxDev >>> 32));
            result = PRIME * result + (int) (endMinDev ^ (endMinDev >>> 32));
            result = PRIME * result + (int) (startMaxDev ^ (startMaxDev >>> 32));
            result = PRIME * result + (int) (startMinDev ^ (startMinDev >>> 32));
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
            final IncludesEvaluator other = (IncludesEvaluator) obj;
            return endMaxDev == other.endMaxDev && endMinDev == other.endMinDev && startMaxDev == other.startMaxDev && startMinDev == other.startMinDev;
        }

        /**
         * This methods sets the parameters appropriately.
         *
         * @param parameters
         */
        private void setParameters(long[] parameters) {
            if ( parameters == null || parameters.length == 0 ) {
                // open bounded range
                this.startMinDev = 1;
                this.startMaxDev = Long.MAX_VALUE;
                this.endMinDev = 1;
                this.endMaxDev = Long.MAX_VALUE;
            } else if ( parameters.length == 1 ) {
                // open bounded ranges
                this.startMinDev = 1;
                this.startMaxDev = parameters[0];
                this.endMinDev = 1;
                this.endMaxDev = parameters[0];
            } else if ( parameters.length == 2 ) {
                // open bounded ranges
                this.startMinDev = parameters[0];
                this.startMaxDev = parameters[1];
                this.endMinDev = parameters[0];
                this.endMaxDev = parameters[1];
            } else if ( parameters.length == 4 ) {
                // open bounded ranges
                this.startMinDev = parameters[0];
                this.startMaxDev = parameters[1];
                this.endMinDev = parameters[2];
                this.endMaxDev = parameters[3];
            } else {
                throw new RuntimeException( "[During Evaluator]: Not possible to use " + parameters.length + " parameters: '" + paramText + "'" );
            }
        }

    }

}
