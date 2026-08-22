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
package org.drools.modelcompiler.constraints;

import java.util.Objects;

import org.drools.base.base.ValueType;
import org.drools.base.base.extractors.BaseObjectClassFieldReader;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.model.functions.Function1;

public class LambdaReadAccessor extends BaseObjectClassFieldReader implements ReadAccessor {

    private final Function1 lambda;

    public LambdaReadAccessor( Class<?> fieldType, Function1 lambda ) {
        this(0, fieldType, lambda);
    }

    public LambdaReadAccessor( int index, Class<?> fieldType, Function1 lambda ) {
        super(index, fieldType, ValueType.determineValueType( fieldType ));
        this.lambda = lambda;
    }

    @Override
    public Object getValue(GlobalResolver valueResolver, Object object) {
        return lambda.apply( object );
    }

    @Override
    public boolean equals( Object o ) {
        if ( this == o ) return true;
        if ( o == null || getClass() != o.getClass() ) return false;
        if ( !super.equals( o ) ) return false;
        LambdaReadAccessor that = ( LambdaReadAccessor ) o;
        return Objects.equals( lambda, that.lambda );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), lambda);
    }
}
