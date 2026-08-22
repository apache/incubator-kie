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
package org.drools.base.base.extractors;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import org.drools.base.base.BaseClassFieldReader;
import org.drools.base.base.ValueType;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.util.FloatHelper;

public abstract class BaseObjectClassFieldReader extends BaseClassFieldReader {

    private static final long serialVersionUID = 510l;

    public BaseObjectClassFieldReader() {

    }

    protected BaseObjectClassFieldReader(final int index,
                                         final Class< ? > fieldType,
                                         final ValueType valueType) {
        super( index,
               fieldType,
               valueType );
    }

    public abstract Object getValue(GlobalResolver valueResolver, Object object);

    public boolean getBooleanValue(GlobalResolver valueResolver, Object object) {
        final Object value = getValue( valueResolver, object );

        if ( value instanceof Boolean b ) {
            return b.booleanValue();
        }

        throw new RuntimeException( "Conversion to boolean not supported from " + getExtractToClass().getName() );
    }


    public double getDecimalValue(GlobalResolver valueResolver, Object object) {
        final Object value = getValue( valueResolver, object );

        if( value instanceof Character c ) {
            return c.charValue();
        } else if ( value instanceof Number n ) {
            return FloatHelper.cleanDouble( n.doubleValue() );
        }

        throw new RuntimeException( "Conversion to double not supported from " +  getExtractToClass().getName() );
    }


    public long getWholeNumberValue(GlobalResolver valueResolver, Object object) {
        final Object value = getValue( valueResolver, object );

        if( value instanceof Character c ) {
            return c.charValue();
        } else if ( value instanceof Number n ) {
            return n.longValue();
        } else if ( value instanceof Date d ) {
            return d.getTime();
        } else if ( value instanceof LocalDate ld ) {
            return Date.from( ld.atStartOfDay().atZone( ZoneId.systemDefault() ).toInstant() ).getTime();
        } else if ( value instanceof LocalDateTime ldt ) {
            return Date.from( ldt.atZone( ZoneId.systemDefault() ).toInstant() ).getTime();
        } else if ( value instanceof ZonedDateTime zdt ) {
            return Date.from( zdt.toInstant() ).getTime();
        }

        throw new RuntimeException( "Conversion to long not supported from " +  getExtractToClass().getName() );
    }

    public boolean isNullValue(GlobalResolver valueResolver, Object object) {
        return object == null || getValue( valueResolver, object ) == null;
    }


    public Method getNativeReadMethod() {
        try {
            return this.getClass().getMethod(getNativeReadMethodName(), GlobalResolver.class, Object.class);
        } catch ( final Exception e ) {
            throw new RuntimeException( "This is a bug. Please report to development team: " + e.getMessage(), e );
        }
    }

    public String getNativeReadMethodName() {
        Class<?> type = getExtractToClass();
        if (!type.isPrimitive()) {
            return "getValue";
        } else if (type == int.class || type == long.class || type == short.class || type == byte.class || type == char.class) {
            return "getWholeNumberValue";
        } else if (type == float.class || type == double.class) {
            return "getDecimalValue";
        }
        return "get" + type.getName().substring(0, 1).toUpperCase() + type.getName().substring(1) + "Value";
    }

    public int getHashCode(GlobalResolver valueResolver, Object object) {
        final Object value = getValue( valueResolver, object );
        return (value != null) ? value.hashCode() : 0;
    }
}
