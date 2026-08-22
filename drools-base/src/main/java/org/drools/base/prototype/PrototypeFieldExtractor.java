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
package org.drools.base.prototype;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;

import org.drools.base.base.ValueType;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.util.ClassUtils;
import org.kie.api.prototype.Prototype;
import org.kie.api.prototype.PrototypeFactInstance;

public class PrototypeFieldExtractor implements Externalizable, ReadAccessor {

    private static final long serialVersionUID = 510l;
    private Prototype prototype;
    private String fieldName;
    private int fieldIndex;

    public PrototypeFieldExtractor() {

    }

    public PrototypeFieldExtractor(Prototype prototype, String fieldName) {
        this.prototype = prototype;
        this.fieldName = fieldName;
        this.fieldIndex = prototype.getFieldIndex(fieldName);
    }

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        prototype = (Prototype) in.readObject();
        fieldName = in.readUTF();
        fieldIndex = in.readInt();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject( prototype );
        out.writeUTF( fieldName );
        out.writeInt( fieldIndex );
    }

    public ValueType getValueType() {
        return ValueType.determineValueType(getExtractToClass());
    }

    public Object getValue(GlobalResolver valueResolver, final Object object) {
        return ((PrototypeFactInstance) object).get(this.fieldName );
    }

    public int getIndex() {
        return this.fieldIndex;
    }

    public Class getExtractToClass() {
        Prototype.Field field = this.prototype.getField( fieldName );
        return field != null ? field.getClass() : Object.class;
    }

    public String getExtractToClassName() {
        return ClassUtils.canonicalName( getExtractToClass() );
    }

    public boolean getBooleanValue(GlobalResolver valueResolver, Object object) {
        return (Boolean) ((PrototypeFactInstance) object).get(fieldName);
    }


    public double getDecimalValue(GlobalResolver valueResolver, Object object) {
        return ((Number) ((PrototypeFactInstance) object).get( fieldName )).doubleValue();
    }

    public long getWholeNumberValue(GlobalResolver valueResolver, Object object) {
        return ((Number) ((PrototypeFactInstance) object).get( fieldName )).longValue();
    }


    public Method getNativeReadMethod() {
        try {
            return this.getClass().getDeclaredMethod("getValue",
                    GlobalResolver.class, Object.class);
        } catch ( final Exception e ) {
            throw new RuntimeException( "This is a bug. Please report to development team: " + e.getMessage(),
                                        e );
        }
    }

    public String getNativeReadMethodName() {
        return "getValue";
    }

    public int getHashCode(GlobalResolver valueResolver, Object object) {
        return getValue( valueResolver, object ).hashCode();
    }

    public boolean isGlobal() {
        return false;
    }

    public boolean isSelfReference() {
        return false;
    }

    public boolean isNullValue(GlobalResolver valueResolver, Object object) {
        return ((PrototypeFactInstance) object).get( this.fieldName ) == null;
    }

    public int getHashCode(Object object) {
        return getHashCode( null, object );
    }

    public Object getValue(Object object) {
        return getValue( null, object );
    }

    public boolean isNullValue(Object object) {
        return isNullValue( null, object );
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((prototype == null) ? 0 : prototype.hashCode());
        result = prime * result + fieldIndex;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if ( this == obj ) {
            return true;
        }
        if ( obj == null ) {
            return false;
        }
        if ( getClass() != obj.getClass() ) {
            return false;
        }
        PrototypeFieldExtractor other = (PrototypeFieldExtractor) obj;
        
        if ( prototype == null ) {
            if ( other.prototype != null ) {
            return false;
            }
        } else if ( !prototype.equals( other.prototype ) ) {
            return false;
        }
        
        if ( fieldIndex != other.fieldIndex ) {
            return false;
        }
        return true;
    }
   
}
