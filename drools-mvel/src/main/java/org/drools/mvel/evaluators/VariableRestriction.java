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

import org.drools.base.base.ValueResolver;
import org.drools.base.base.ValueType;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.ContextEntry;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.mvel.evaluators.AfterEvaluatorDefinition.AfterEvaluator;
import org.drools.mvel.evaluators.BeforeEvaluatorDefinition.BeforeEvaluator;
import org.drools.mvel.evaluators.MeetsEvaluatorDefinition.MeetsEvaluator;
import org.drools.mvel.evaluators.MetByEvaluatorDefinition.MetByEvaluator;
import org.drools.core.common.DefaultEventHandle;
import org.drools.base.rule.accessor.Evaluator;
import org.kie.api.runtime.rule.EventHandle;
import org.kie.api.runtime.rule.FactHandle;

public class VariableRestriction {

    private VariableRestriction() { }

    public static VariableContextEntry createContextEntry(ReadAccessor fieldExtractor,
                                                          Declaration declaration,
                                                          Evaluator evaluator) {
        ValueType coerced = evaluator.getCoercedValueType();
        
        if ( evaluator.isTemporal() ) {
            if ( evaluator instanceof BeforeEvaluator || evaluator instanceof MeetsEvaluator) {
                return new LeftStartRightEndContextEntry( fieldExtractor,
                                                         declaration, 
                                                         evaluator );
            }
            
            if ( evaluator instanceof AfterEvaluator || evaluator instanceof MetByEvaluator  ) {
                return new LeftEndRightStartContextEntry( fieldExtractor,
                                                         declaration, 
                                                         evaluator );
            }        

            // else
            return new TemporalVariableContextEntry( fieldExtractor,
                                                     declaration,
                                                     evaluator );          
        }
        


        if ( coerced.isBoolean() ) {
            return new BooleanVariableContextEntry( fieldExtractor,
                                                    declaration,
                                                    evaluator );
        } else if ( coerced.isDecimalNumber() ) {
            return new DecimalVariableContextEntry( fieldExtractor,
                                                    declaration,
                                                    evaluator );
        } else if ( coerced.isIntegerNumber() || coerced.isEvent() ) {
            return new LongVariableContextEntry( fieldExtractor,
                                                 declaration,
                                                 evaluator );
        } else if ( coerced.isChar() ) {
            return new CharVariableContextEntry( fieldExtractor,
                                                 declaration,
                                                 evaluator );
        } else {
            return new ObjectVariableContextEntry( fieldExtractor,
                                                   declaration,
                                                   evaluator );
        }
    }

    public static abstract class VariableContextEntry
        implements
            ContextEntry {
        public ReadAccessor          extractor;
        public Evaluator             evaluator;
        public Object                object;
        public Declaration           declaration;
        public BaseTuple             tuple;
        public ContextEntry          entry;
        public boolean               leftNull;
        public boolean               rightNull;
        public ValueResolver         valueResolver;

        public VariableContextEntry() {
        }

        public VariableContextEntry(final ReadAccessor extractor,
                                    final Declaration declaration,
                                    final Evaluator evaluator) {
            this.extractor = extractor;
            this.declaration = declaration;
            this.evaluator = evaluator;
        }

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            valueResolver = (ValueResolver) in.readObject();
            extractor = (ReadAccessor) in.readObject();
            evaluator = (Evaluator) in.readObject();
            object = in.readObject();
            declaration = (Declaration) in.readObject();
            tuple = (BaseTuple) in.readObject();
            entry = (ContextEntry) in.readObject();
            leftNull = in.readBoolean();
            rightNull = in.readBoolean();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject( valueResolver );
            out.writeObject( extractor );
            out.writeObject( evaluator );
            out.writeObject( object );
            out.writeObject( declaration );
            out.writeObject( tuple );
            out.writeObject( entry );
            out.writeBoolean( leftNull );
            out.writeBoolean( rightNull );
        }

        public ContextEntry getNext() {
            return this.entry;
        }

        public void setNext(final ContextEntry entry) {
            this.entry = entry;
        }

        public ReadAccessor getFieldExtractor() {
            return this.extractor;
        }

        public Object getObject() {
            return this.object;
        }

        public BaseTuple getTuple() {
            return this.tuple;
        }

        public Declaration getVariableDeclaration() {
            return this.declaration;
        }

        public boolean isLeftNull() {
            return this.leftNull;
        }

        public boolean isRightNull() {
            return this.rightNull;
        }

        public void resetTuple() {
            this.tuple = null;
        }

        public void resetFactHandle() {
            this.object = null;
        }
    }

    public static class ObjectVariableContextEntry extends VariableContextEntry {

        private static final long serialVersionUID = 510l;
        public Object             left;
        public Object             right;

        public ObjectVariableContextEntry() {
        }

        public ObjectVariableContextEntry(final ReadAccessor extractor,
                                          final Declaration declaration,
                                          final Evaluator evaluator) {
            super( extractor,
                   declaration,
                   evaluator );
        }

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            super.readExternal( in );
            left = in.readObject();
            right = in.readObject();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal( out );
            out.writeObject( left );
            out.writeObject( right );
        }

        public void updateFromTuple(final ValueResolver valueResolver,
                                    final BaseTuple tuple) {
            this.tuple = tuple;
            this.valueResolver = valueResolver;
            this.leftNull = this.declaration.getExtractor().isNullValue( valueResolver.getGlobalResolver(),
                                                                         tuple.getObject( this.declaration ) );
            this.left = this.declaration.getExtractor().getValue( valueResolver.getGlobalResolver(),
                                                                  tuple.getObject( this.declaration ) );
        }

        public void updateFromFactHandle(final ValueResolver valueResolver,
                                         final FactHandle handle) {
            this.object = handle.getObject();
            this.valueResolver = valueResolver;
            this.rightNull = this.extractor.isNullValue( valueResolver.getGlobalResolver(),
                                                         handle.getObject() );
            this.right = this.extractor.getValue( valueResolver.getGlobalResolver(),
                                                  handle.getObject() );
        }

        public void resetTuple() {
            this.left = null;
            this.tuple = null;
        }

        public void resetFactHandle() {
            this.right = null;
            this.object = null;
        }
    }

    public static class LongVariableContextEntry extends VariableContextEntry {

        private static final long serialVersionUID = 510l;

        public long               left;
        public long               right;

        public LongVariableContextEntry() {
        }

        public LongVariableContextEntry(final ReadAccessor extractor,
                                        final Declaration declaration,
                                        final Evaluator evaluator) {
            super( extractor,
                   declaration,
                   evaluator );
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal( in );
            left = in.readLong();
            right = in.readLong();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal( out );
            out.writeLong( left );
            out.writeLong( right );
        }

        public void updateFromTuple(final ValueResolver valueResolver,
                                    final BaseTuple tuple) {
            this.tuple = tuple;
            this.valueResolver = valueResolver;
            this.leftNull = this.declaration.getExtractor().isNullValue( valueResolver.getGlobalResolver(),
                                                                         tuple.getObject( this.declaration ) );

            if ( !leftNull ) {
                this.left = this.declaration.getExtractor().getWholeNumberValue( valueResolver.getGlobalResolver(),
                                                                          tuple.getObject( this.declaration ) );
            } else {
                this.left = 0;
            }
        }

        public void updateFromFactHandle(final ValueResolver valueResolver,
                                         final FactHandle handle) {
            this.object = handle.getObject();
            this.valueResolver = valueResolver;
            this.rightNull = this.extractor.isNullValue( valueResolver.getGlobalResolver(),
                                                         handle.getObject());

            if ( !rightNull ) { // avoid a NullPointerException
                this.right = this.extractor.getWholeNumberValue( valueResolver.getGlobalResolver(),
                                                          handle.getObject() );
            } else {
                this.right = 0;
            }
        }
    }

    public static class CharVariableContextEntry extends VariableContextEntry {

        private static final long serialVersionUID = 510l;

        public char               left;
        public char               right;

        public CharVariableContextEntry() {
        }

        public CharVariableContextEntry(final ReadAccessor extractor,
                                        final Declaration declaration,
                                        final Evaluator evaluator) {
            super( extractor,
                   declaration,
                   evaluator );
        }

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            super.readExternal( in );
            left = in.readChar();
            right = in.readChar();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal( out );
            out.writeChar( left );
            out.writeChar( right );
        }

        public void updateFromTuple(final ValueResolver valueResolver,
                                    final BaseTuple tuple) {
            this.tuple = tuple;
            this.valueResolver = valueResolver;
            this.leftNull = this.declaration.getExtractor().isNullValue( valueResolver.getGlobalResolver(),
                                                                         tuple.getObject( this.declaration ));

            if ( !leftNull ) {
                this.left = (char) this.declaration.getExtractor().getWholeNumberValue( valueResolver.getGlobalResolver(),
                                                                          tuple.getObject( this.declaration ) );
            } else {
                this.left = 0;
            }
        }

        public void updateFromFactHandle(final ValueResolver valueResolver,
                                         final FactHandle handle) {
            this.object = handle.getObject();
            this.valueResolver = valueResolver;
            this.rightNull = this.extractor.isNullValue( valueResolver.getGlobalResolver(),
                                                         handle.getObject());

            if ( !rightNull ) { // avoid a NullPointerException
                this.right = (char) this.extractor.getWholeNumberValue( valueResolver.getGlobalResolver(),
                                                          handle.getObject() );
            } else {
                this.right = 0;
            }
        }
    }

    public static class DecimalVariableContextEntry extends VariableContextEntry {

        private static final long serialVersionUID = 510l;

        public double             left;
        public double             right;

        public DecimalVariableContextEntry() {
        }

        public DecimalVariableContextEntry(final ReadAccessor extractor,
                                           final Declaration declaration,
                                           final Evaluator evaluator) {
            super( extractor,
                   declaration,
                   evaluator );
        }

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            super.readExternal( in );
            left = in.readDouble();
            right = in.readDouble();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal( out );
            out.writeDouble( left );
            out.writeDouble( right );
        }

        public void updateFromTuple(final ValueResolver valueResolver,
                                    final BaseTuple tuple) {
            this.tuple = tuple;
            this.valueResolver = valueResolver;
            this.leftNull = this.declaration.getExtractor().isNullValue( valueResolver.getGlobalResolver(),
                                                                         tuple.getObject( this.declaration ) );

            if ( !leftNull ) {
                this.left = this.declaration.getExtractor().getDecimalValue( valueResolver.getGlobalResolver(),
                                                                            tuple.getObject( this.declaration ) );
            } else {
                this.left = 0;
            }
        }

        public void updateFromFactHandle(final ValueResolver valueResolver,
                                         final FactHandle handle) {
            this.object = handle.getObject();
            this.valueResolver = valueResolver;
            this.rightNull = this.extractor.isNullValue( valueResolver.getGlobalResolver(),
                                                         handle.getObject() );

            if ( !rightNull ) { // avoid a NullPointerException
                this.right = this.extractor.getDecimalValue( valueResolver.getGlobalResolver(),
                                                            handle.getObject() );
            } else {
                this.right = 0;
            }
        }
    }

    public static class BooleanVariableContextEntry extends VariableContextEntry {

        private static final long serialVersionUID = 510l;
        public boolean            left;
        public boolean            right;

        public BooleanVariableContextEntry() {
        }

        public BooleanVariableContextEntry(final ReadAccessor extractor,
                                           final Declaration declaration,
                                           final Evaluator evaluator) {
            super( extractor,
                   declaration,
                   evaluator );
        }

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            super.readExternal( in );
            left = in.readBoolean();
            right = in.readBoolean();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal( out );
            out.writeBoolean( left );
            out.writeBoolean( right );
        }

        public void updateFromTuple(final ValueResolver valueResolver,
                                    final BaseTuple tuple) {
            this.tuple = tuple;
            this.valueResolver = valueResolver;
            this.leftNull = this.declaration.getExtractor().isNullValue( valueResolver.getGlobalResolver(),
                                                                         tuple.getObject( this.declaration ) );

            this.left = !leftNull && this.declaration.getExtractor().getBooleanValue( valueResolver.getGlobalResolver(), tuple.getObject( this.declaration ) );
        }

        public void updateFromFactHandle(final ValueResolver valueResolver,
                                         final FactHandle handle) {
            this.object = handle.getObject();
            this.valueResolver = valueResolver;
            this.rightNull = this.extractor.isNullValue( valueResolver.getGlobalResolver(),
                                                         handle.getObject() );

            // avoid a NullPointerException
            this.right = !rightNull && this.extractor.getBooleanValue( valueResolver.getGlobalResolver(), handle.getObject() );
        }
    }
    
    public static abstract class TimestampedContextEntry extends VariableContextEntry {
        public long               timestamp;

        public TimestampedContextEntry() {
        }

        public TimestampedContextEntry(final ReadAccessor extractor,
                                             final Declaration declaration,
                                             final Evaluator evaluator) {
            super( extractor,
                   declaration,
                   evaluator );
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal( in );
            timestamp = in.readLong();
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal( out );
            out.writeLong( timestamp );
        }

        protected abstract long getTimestampFromTuple( BaseTuple tuple );
        protected abstract long getTimestampFromFactHandle( FactHandle handle );

        @Override
        public void updateFromTuple(final ValueResolver valueResolver,
                                    final BaseTuple tuple) {
            this.tuple = tuple;
            this.valueResolver = valueResolver;

            if ( this.declaration.getExtractor().isSelfReference() ) {
                this.timestamp = getTimestampFromTuple( tuple );
            } else {
                this.leftNull = this.declaration.getExtractor().isNullValue( valueResolver.getGlobalResolver(),
                                                                             tuple.getObject( this.declaration ) );
                if ( !leftNull ) { // avoid a NullPointerException
                    this.timestamp = this.declaration.getExtractor().getWholeNumberValue( valueResolver.getGlobalResolver(),
                                                                                   tuple.getObject( this.declaration ) );
                } else {
                    this.timestamp = 0;
                }
            }
        }

        @Override
        public void updateFromFactHandle(final ValueResolver valueResolver,
                                         final FactHandle handle) {
            this.object = handle.getObject();
            this.valueResolver = valueResolver;

            if ( this.extractor.isSelfReference() ) {
                this.timestamp = getTimestampFromFactHandle( handle );
            } else {
                this.rightNull = this.extractor.isNullValue( valueResolver.getGlobalResolver(),
                                                             handle.getObject());
                if ( !rightNull ) { // avoid a NullPointerException
                    this.timestamp = this.extractor.getWholeNumberValue( valueResolver.getGlobalResolver(),
                                                                  handle.getObject() );
                } else {
                    this.timestamp = 0;
                }
            }
        }
    }

    public static class LeftStartRightEndContextEntry extends TimestampedContextEntry {

        private static final long serialVersionUID = 510l;

        public LeftStartRightEndContextEntry() {
        }

        public LeftStartRightEndContextEntry(final ReadAccessor extractor,
                                             final Declaration declaration,
                                             final Evaluator evaluator) {
            super( extractor,
                   declaration,
                   evaluator );
        }

        @Override
        protected long getTimestampFromTuple( BaseTuple tuple ) {
            return ((EventHandle) tuple.get(this.declaration)).getStartTimestamp();
        }

        @Override
        protected long getTimestampFromFactHandle( FactHandle handle ) {
            return ((EventHandle)handle).getEndTimestamp();
        }
    }
    
    public static class LeftEndRightStartContextEntry extends TimestampedContextEntry {

        private static final long serialVersionUID = 510l;

        public LeftEndRightStartContextEntry() {
        }

        public LeftEndRightStartContextEntry(final ReadAccessor extractor,
                                            final Declaration declaration,
                                            final Evaluator evaluator) {
            super( extractor,
                   declaration,
                   evaluator );
        }

        @Override
        protected long getTimestampFromTuple( BaseTuple tuple ) {
            return ((EventHandle) tuple.get(this.declaration)).getEndTimestamp();
        }

        @Override
        protected long getTimestampFromFactHandle( FactHandle handle ) {
            return ((EventHandle)handle).getStartTimestamp();
        }
    }
    
    public static class TemporalVariableContextEntry extends VariableContextEntry {

        private static final long serialVersionUID = 510l;

        public long               startTS;
        public long               endTS;

        public TemporalVariableContextEntry() {
        }

        public TemporalVariableContextEntry(final ReadAccessor extractor,
                                            final Declaration declaration,
                                            final Evaluator evaluator) {
            super( extractor,
                   declaration,
                   evaluator );
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal( in );
            startTS = in.readLong();
            endTS = in.readLong();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal( out );
            out.writeLong( startTS );
            out.writeLong( startTS );
        }

        public void updateFromTuple(final ValueResolver valueResolver,
                                    final BaseTuple tuple) {
            this.tuple = tuple;
            this.valueResolver = valueResolver;
            
            if ( this.declaration.getExtractor().isSelfReference() ) {
                DefaultEventHandle efh = ((DefaultEventHandle)tuple.get(this.declaration));
                this.startTS = efh.getStartTimestamp();
                this.endTS = efh.getEndTimestamp();
            } else {
              this.leftNull = this.declaration.getExtractor().isNullValue( valueResolver.getGlobalResolver(),
                                                                           tuple.getObject( this.declaration ) );
              if ( !leftNull ) { // avoid a NullPointerException
                  this.startTS = this.declaration.getExtractor().getWholeNumberValue( valueResolver.getGlobalResolver(),
                                                                             tuple.getObject( this.declaration ) );
                } else {
                    this.startTS = 0;
                }    
              endTS = startTS;
            }
        }

        public void updateFromFactHandle(final ValueResolver valueResolver,
                                         final FactHandle handle) {
            this.object = handle.getObject(); 
            this.valueResolver = valueResolver;
            
            if ( this.extractor.isSelfReference() ) {
                this.startTS = ((DefaultEventHandle)handle).getStartTimestamp();
                this.endTS = ((DefaultEventHandle)handle).getEndTimestamp();
            } else {
              this.rightNull = this.extractor.isNullValue( valueResolver.getGlobalResolver(),
                                                           handle.getObject());
              if ( !rightNull ) { // avoid a NullPointerException
                  this.startTS = this.extractor.getWholeNumberValue( valueResolver.getGlobalResolver(),
                                                              handle.getObject() );
              } else {
                    this.startTS = 0;
              }  
              endTS = startTS;
            }
        }
    }       
}
