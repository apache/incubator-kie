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
package org.drools.base.rule.accessor;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;

import org.drools.base.base.AcceptsClassObjectType;
import org.drools.base.base.ClassObjectType;
import org.drools.base.base.ObjectType;
import org.drools.base.base.extractors.BaseObjectClassFieldReader;
import org.drools.util.ClassUtils;
import org.kie.api.prototype.PrototypeFactInstance;

public class PatternExtractor extends BaseObjectClassFieldReader
    implements
    ReadAccessor,
        AcceptsClassObjectType,
    Externalizable {

    private static final long serialVersionUID = 510l;
    private ObjectType objectType;

    public PatternExtractor() {
    }

    public PatternExtractor(final ObjectType objectType) {
        this.objectType = objectType;
        if (objectType instanceof ClassObjectType cot) {
            setClassObjectType(cot);
        }
    }

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        objectType = (ObjectType) in.readObject();
        setIndex( in.readInt() );
        setFieldType( ((ClassObjectType) objectType).getClassType() );
        setValueType( objectType.getValueType() );
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject( objectType );
        out.writeInt( getIndex() );
    }
    
    public void setClassObjectType(ClassObjectType objectType) {
        this.objectType = objectType;
        setIndex( -1 );
        setFieldType( objectType.getClassType() );
        setValueType( objectType.getValueType() );        
    }

    public Object getValue(GlobalResolver valueResolver,
                           final Object object) {
        return object;
    }

    public ObjectType getObjectType() {
        return this.objectType;
    }

    public Class<?> getExtractToClass() {
        // @todo : this is a bit nasty, but does the trick
        if ( this.objectType instanceof ClassObjectType ) {
            return ((ClassObjectType) this.objectType).getClassType();
        } else {
            return PrototypeFactInstance.class;
        }
    }

    public String getExtractToClassName() {
        Class<?> clazz = this.objectType instanceof ClassObjectType ?
                         ((ClassObjectType) this.objectType).getClassType() :
                         PrototypeFactInstance.class;
        return ClassUtils.canonicalName( clazz );
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

    public int hashCode() {
        return this.objectType.hashCode();
    }

    public boolean equals(final Object obj) {
        if ( this == obj ) {
            return true;
        }
        if ( !(obj instanceof PatternExtractor) ) {
            return false;
        }
        final PatternExtractor other = (PatternExtractor) obj;
        return this.objectType.equals( other.objectType );
    }

    public boolean isGlobal() {
        return false;
    }

    public boolean isSelfReference() {
        return true;
    }

}
