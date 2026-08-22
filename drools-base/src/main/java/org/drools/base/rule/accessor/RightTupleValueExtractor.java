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

import org.drools.base.base.ValueResolver;
import org.drools.base.base.ValueType;
import org.drools.base.reteoo.BaseTuple;

public class RightTupleValueExtractor implements TupleValueExtractor {
    private ReadAccessor extractor;

    public RightTupleValueExtractor(ReadAccessor extractor) {
        this.extractor = extractor;
    }

    @Override
    public ValueType getValueType() {
        return extractor.getValueType();
    }

    @Override
    public Object getValue(ValueResolver valueResolver, BaseTuple tuple) {
        return extractor.getValue(valueResolver != null? valueResolver.getGlobalResolver() : null, tuple.getFactHandle().getObject());
    }

    @Override
    public TupleValueExtractor clone() {
        return new RightTupleValueExtractor(extractor);
    }
}
