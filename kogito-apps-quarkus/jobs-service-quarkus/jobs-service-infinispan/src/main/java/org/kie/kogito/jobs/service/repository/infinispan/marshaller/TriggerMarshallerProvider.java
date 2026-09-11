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
package org.kie.kogito.jobs.service.repository.infinispan.marshaller;

import org.infinispan.protostream.BaseMarshaller;
import org.infinispan.protostream.SerializationContext;
import org.kie.kogito.timer.Trigger;

/**
 * Restores marshaller resolution for {@link Trigger}-typed values on ProtoStream 6+.
 * <p>
 * Up to ProtoStream 5, registering {@link TriggerMarshaller} also indexed it under its Java type,
 * so lookups by the {@code Trigger} interface (as {@link JobDetailsMarshaller} performs when
 * writing the trigger field) resolved it. ProtoStream 6 no longer indexes interface types, so this
 * provider answers those class-based lookups instead.
 * <p>
 * {@link SerializationContext.MarshallerProvider} is deprecated in favor of the instance-based
 * provider, but remains the only hook consulted for lookups where just the declared class is known.
 */
@SuppressWarnings("deprecation")
public class TriggerMarshallerProvider implements SerializationContext.MarshallerProvider {

    private final TriggerMarshaller marshaller = new TriggerMarshaller();

    @Override
    public BaseMarshaller<?> getMarshaller(String typeName) {
        return marshaller.getTypeName().equals(typeName) ? marshaller : null;
    }

    @Override
    public BaseMarshaller<?> getMarshaller(Class<?> javaClass) {
        return Trigger.class.isAssignableFrom(javaClass) ? marshaller : null;
    }
}
