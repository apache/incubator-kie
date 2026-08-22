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

public abstract class BaseWholeNumberClassFieldReader extends BaseClassFieldReader {

    private static final long serialVersionUID = 510l;

    /**
     * This constructor is not supposed to be used from outside the class hirarchy
     * 
     * @param index
     * @param fieldType
     * @param valueType
     */
    protected BaseWholeNumberClassFieldReader(final int index,
                                              final Class fieldType,
                                              final ValueType valueType) {
        super( index,
               fieldType,
               valueType );
    }

    public BaseWholeNumberClassFieldReader() {
    }

    public Object getValue(GlobalResolver valueResolver, final Object object) {
        long value = getWholeNumberValue( valueResolver, object );
        if(getExtractToClass() == byte.class) {
            return Byte.valueOf((byte) value);
        } else if(getExtractToClass() == short.class) {
            return Short.valueOf((short) value);
        } else if(getExtractToClass() == int.class) {
            return Integer.valueOf((int) value);
        } else if(getExtractToClass() == char.class) {
            return Character.valueOf((char) value);
        } else {
            return Long.valueOf(value);
        }
    }

    public Object getInternalValue(Object object) {
        long value = getWholeNumberValue( null, object );
        return Long.valueOf(value);
    }

    public boolean getBooleanValue(GlobalResolver valueResolver, final Object object) {
        throw new RuntimeException( "Conversion to boolean not supported from long" );
    }


    public double getDecimalValue(GlobalResolver valueResolver, final Object object) {
        return getWholeNumberValue( valueResolver, object );
    }


    public abstract long getWholeNumberValue(GlobalResolver valueResolver, Object object);


    public boolean isNullValue(GlobalResolver valueResolver, final Object object) {
        return false;
    }

    public Method getNativeReadMethod() {
        try {
            return this.getClass().getDeclaredMethod("getWholeNumberValue",
                                                     GlobalResolver.class, Object.class);
        } catch ( final Exception e ) {
            throw new RuntimeException( "This is a bug. Please report to development team: " + e.getMessage(),
                                        e );
        }
    }

    public int getHashCode(GlobalResolver valueResolver, final Object object) {
        final long temp = getWholeNumberValue( valueResolver, object );
        return (int) (temp ^ (temp >>> 32));
    }

}
