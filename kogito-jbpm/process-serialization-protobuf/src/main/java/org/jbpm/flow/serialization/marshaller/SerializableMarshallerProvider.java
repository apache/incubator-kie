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
package org.jbpm.flow.serialization.marshaller;

import java.io.Serializable;

import org.infinispan.protostream.BaseMarshaller;
import org.infinispan.protostream.SerializationContext;

/**
 * Restores marshaller resolution for {@link Serializable}-typed variables on ProtoStream 6+.
 * <p>
 * Up to ProtoStream 5, registering {@link SerializableProtostreamBaseMarshaller} also indexed it
 * under its Java type, so lookups by {@code Serializable.class} (as generated message marshallers
 * perform for {@code kogito.Serializable} fields) resolved it. ProtoStream 6 no longer indexes
 * interface types, so this provider answers those class-based lookups instead.
 * <p>
 * {@link SerializationContext.MarshallerProvider} is deprecated in favor of the instance-based
 * provider, but remains the only hook consulted for lookups where just the declared class is known.
 */
@SuppressWarnings("deprecation")
public class SerializableMarshallerProvider implements SerializationContext.MarshallerProvider {

    private final SerializableProtostreamBaseMarshaller marshaller = new SerializableProtostreamBaseMarshaller();

    @Override
    public BaseMarshaller<?> getMarshaller(String typeName) {
        return marshaller.getTypeName().equals(typeName) ? marshaller : null;
    }

    @Override
    public BaseMarshaller<?> getMarshaller(Class<?> javaClass) {
        return Serializable.class.isAssignableFrom(javaClass) ? marshaller : null;
    }
}
