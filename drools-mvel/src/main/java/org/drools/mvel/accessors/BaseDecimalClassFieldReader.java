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

import java.lang.reflect.Method;

import org.drools.base.base.BaseClassFieldReader;
import org.drools.base.base.ValueType;
import org.drools.base.rule.accessor.GlobalResolver;

public abstract class BaseDecimalClassFieldReader extends BaseClassFieldReader {

    private static final long serialVersionUID = 510l;

    /**
     * This constructor is not supposed to be used from outside the class hirarchy
     * 
     * @param index
     * @param fieldType
     * @param valueType
     */
    protected BaseDecimalClassFieldReader(final int index,
                                          final Class fieldType,
                                          final ValueType valueType) {
        super( index,
               fieldType,
               ValueType.DOUBLE_TYPE );
    }

    public BaseDecimalClassFieldReader() {
    }

    public Object getValue(GlobalResolver valueResolver, final Object object) {
        double value = getDecimalValue(valueResolver, object);
        // Return the correct wrapper type based on the original field type
        if (getExtractToClass() == float.class) {
            return Float.valueOf((float) value);
        } else {
            return Double.valueOf(value);
        }
    }

    public boolean getBooleanValue(GlobalResolver valueResolver, final Object object) {
        throw new RuntimeException( "Conversion to boolean not supported from double" );
    }


    public abstract double getDecimalValue(GlobalResolver valueResolver, Object object);


    public long getWholeNumberValue(GlobalResolver valueResolver, final Object object) {
        return (long) getDecimalValue( valueResolver, object );
    }


    public boolean isNullValue(GlobalResolver valueResolver, final Object object) {
        return false;
    }

    public Method getNativeReadMethod() {
        try {
            return this.getClass().getDeclaredMethod("getDecimalValue",
                                                     GlobalResolver.class, Object.class);
        } catch ( final Exception e ) {
            throw new RuntimeException( "This is a bug. Please report to development team: " + e.getMessage(),
                                        e );
        }
    }

    public int getHashCode(GlobalResolver valueResolver, final Object object) {
        final long temp = Double.doubleToLongBits( getDecimalValue( valueResolver, object ) );
        return (int) (temp ^ (temp >>> 32));
    }

}
