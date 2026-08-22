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
package org.drools.mvel.accessors;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;

import org.drools.base.base.FieldAccessor;
import org.drools.base.base.ValueType;
import org.drools.base.rule.accessor.AcceptsReadAccessor;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.base.rule.accessor.ReadAccessor;

/**
 * This is a wrapper for a ClassFieldExtractor that provides
 * default values and a simpler interface for non-used parameters
 * like the working memory, when the field extractor is used outside
 * the working memory scope.
 */
public class ClassFieldAccessor implements AcceptsReadAccessor, FieldAccessor, Externalizable {

    private static final long serialVersionUID = 510l;
    private ClassFieldReader  reader;
    private ClassFieldWriter  writer;

    public ClassFieldAccessor() {
    }

    public ClassFieldAccessor(final ClassFieldReader reader,
                              final ClassFieldWriter writer) {
        this.reader = reader;
        this.writer = writer;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject( reader );
        out.writeObject( writer );
    }

    public void readExternal(final ObjectInput is) throws ClassNotFoundException,
                                                  IOException {
        this.reader = (ClassFieldReader) is.readObject();
        this.writer = (ClassFieldWriter) is.readObject();
    }

    public void setReadAccessor(ReadAccessor readAccessor) {
        this.reader = (ClassFieldReader) readAccessor;
    }

    public int getIndex() {
        return this.reader.getIndex();
    }

    @Override
    public Object getValue(GlobalResolver valueResolver, Object object) {
        return this.reader.getValue(valueResolver, object);
    }



    @Override
    public long getWholeNumberValue(GlobalResolver valueResolver, Object object) {
        return this.reader.getWholeNumberValue(valueResolver, object);
    }

    @Override
    public double getDecimalValue(GlobalResolver valueResolver, Object object) {
        return this.reader.getDecimalValue(valueResolver, object);
    }

    @Override
    public boolean getBooleanValue(GlobalResolver valueResolver, Object object) {
        return this.reader.getBooleanValue(valueResolver, object);
    }

    @Override
    public boolean isNullValue(GlobalResolver valueResolver, Object object) {
        return this.reader.isNullValue(valueResolver, object);
    }

    @Override
    public int getHashCode(GlobalResolver valueResolver, Object object) {
        return this.reader.getHashCode(valueResolver, object);
    }

    public String getFieldName() {
        return this.reader.getFieldName();
    }

    public Object getValue(final Object object) {
        return this.reader.getValue( null,
                                     object );
    }

    public ValueType getValueType() {
        return this.reader.getValueType();
    }

    public Class< ? > getExtractToClass() {
        return this.reader.getExtractToClass();
    }

    public String getExtractToClassName() {
        return this.reader.getExtractToClassName();
    }

    public String toString() {
        return this.reader.toString();
    }

    @Override
    public int hashCode() {
        return reader.getClassName().hashCode() ^ reader.getFieldName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj ) return true;
        if ( obj == null ) return false;
        if ( !(obj instanceof ClassFieldAccessor) ) return false;
        ClassFieldAccessor other = (ClassFieldAccessor) obj;
        if ( reader == null ) {
            if ( other.reader != null ) return false;
        } else if ( !reader.getClassName().equals( other.reader.getClassName() ) || !reader.getFieldName().equals( other.reader.getFieldName() ) ) return false;
        if ( writer == null ) {
            if ( other.writer != null ) return false;
        } else if ( !writer.getClassName().equals( other.writer.getClassName() ) || !writer.getFieldName().equals( other.writer.getFieldName() ) ) return false;
        return true;
    }

    public boolean isNullValue(final Object object) {
        return this.reader.isNullValue( null,
                                        object );
    }

    public Method getNativeReadMethod() {
        return this.reader.getNativeReadMethod();
    }

    public String getNativeReadMethodName() {
        return this.reader.getNativeReadMethodName();
    }

    public int getHashCode(final Object object) {
        return this.reader.getHashCode( null,
                                        object );
    }

    public boolean isGlobal() {
        return reader.isGlobal();
    }

    @Override
    public boolean isSelfReference() {
        return reader.isSelfReference();
    }

    public Class< ? > getFieldType() {
        return writer.getFieldType();
    }

    public Method getNativeWriteMethod() {
        return writer.getNativeWriteMethod();
    }

    public void setBooleanValue(Object bean,
                                boolean value) {
        writer.setBooleanValue( bean,
                                value );
    }

    public void setByteValue(Object bean,
                             byte value) {
        writer.setWholeNumberValue( bean,
                             value );
    }

    public void setCharValue(Object bean,
                             char value) {
        writer.setWholeNumberValue( bean,
                             value );
    }

    public void setDecimalValue(Object bean,
                                double value) {
        writer.setDecimalValue( bean,
                               value );
    }

    public void setWholeNumberValue(Object bean,
                                    long value) {
        writer.setWholeNumberValue( bean,
                             value );
    }

    public void setShortValue(Object bean,
                              short value) {
        writer.setWholeNumberValue( bean,
                              value );
    }

    public void setValue(Object bean,
                         Object value) {
        writer.setValue( bean,
                         value );
    }
}
