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

import java.io.Externalizable;

import org.drools.base.base.ValueType;
import org.drools.base.rule.accessor.GlobalResolver;

/**
 * A special field extractor for the self reference "this".
 */
public class SelfReferenceClassFieldReader extends BaseObjectClassFieldReader implements Externalizable  {

    private static final long serialVersionUID = 510l;
    
    public SelfReferenceClassFieldReader() {
        
    }

    public SelfReferenceClassFieldReader(final Class<?> clazz) {
        super( 0, // index
               clazz, // fieldType
               ValueType.determineValueType( clazz ) ); // value type
    }

    public Object getValue(GlobalResolver valueResolver, final Object object) {
        return object;
    }
    
    public boolean isNullValue(GlobalResolver valueResolver, final Object object) {
        return getValue( valueResolver, object ) == null;
    }
    
    @Override
    public boolean isSelfReference() {
        return true;
    }
}
