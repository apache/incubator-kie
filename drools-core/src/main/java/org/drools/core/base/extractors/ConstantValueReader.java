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
package org.drools.core.base.extractors;

import java.lang.reflect.Method;

import org.drools.base.base.ValueType;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.base.rule.accessor.ReadAccessor;

import static org.drools.base.util.TimeIntervalParser.getTimestampFromDate;

public class ConstantValueReader implements ReadAccessor {

    private final Object value;

    public ConstantValueReader(Object value) {
        this.value = value;
    }

    public Object getValue(GlobalResolver valueResolver, Object object) {
        return value;
    }



    public long getWholeNumberValue(GlobalResolver valueResolver, Object object) {
        return value instanceof Long ? (Long)value : getTimestampFromDate( value );
    }

    public double getDecimalValue(GlobalResolver valueResolver, Object object) {
        return (Double)value;
    }

    public boolean getBooleanValue(GlobalResolver valueResolver, Object object) {
        return (Boolean)value;
    }

    public boolean isNullValue(GlobalResolver valueResolver, Object object) {
        return value == null;
    }

    public int getHashCode(GlobalResolver valueResolver, Object object) {
        return value.hashCode();
    }

    public boolean isGlobal() {
        return false;
    }

    public boolean isSelfReference() {
        return false;
    }

    public Object getValue(Object object) {
        return value;
    }

    public boolean isNullValue(Object object) {
        throw new UnsupportedOperationException("org.drools.core.base.extractors.ConstantValueReader.isNullValue -> TODO");
    }

    public ValueType getValueType() {
        throw new UnsupportedOperationException("org.drools.core.base.extractors.ConstantValueReader.getValueType -> TODO");
    }

    public Class<?> getExtractToClass() {
        throw new UnsupportedOperationException("org.drools.core.base.extractors.ConstantValueReader.getExtractToClass -> TODO");
    }

    public String getExtractToClassName() {
        throw new UnsupportedOperationException("org.drools.core.base.extractors.ConstantValueReader.getExtractToClassName -> TODO");
    }

    public Method getNativeReadMethod() {
        throw new UnsupportedOperationException("org.drools.core.base.extractors.ConstantValueReader.getNativeReadMethod -> TODO");
    }

    public String getNativeReadMethodName() {
        throw new UnsupportedOperationException("org.drools.core.base.extractors.ConstantValueReader.getNativeReadMethodName -> TODO");
    }

    public int getHashCode(Object object) {
        throw new UnsupportedOperationException("org.drools.core.base.extractors.ConstantValueReader.getHashCode -> TODO");
    }

    public int getIndex() {
        throw new UnsupportedOperationException("org.drools.core.base.extractors.ConstantValueReader.getIndex -> TODO");
    }
}
