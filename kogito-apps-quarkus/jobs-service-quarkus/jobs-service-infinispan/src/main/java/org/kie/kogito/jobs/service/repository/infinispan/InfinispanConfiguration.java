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
package org.kie.kogito.jobs.service.repository.infinispan;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.Readiness;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.marshall.MarshallerUtil;
import org.kie.kogito.infinispan.health.InfinispanHealthCheck;
import org.kie.kogito.jobs.service.repository.infinispan.marshaller.TriggerMarshallerProvider;

import io.quarkus.runtime.StartupEvent;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.interceptor.Interceptor;

import static org.kie.kogito.jobs.service.repository.infinispan.InfinispanConfiguration.Caches.JOB_DETAILS;

@ApplicationScoped
public class InfinispanConfiguration {

    private AtomicBoolean initialized = new AtomicBoolean(Boolean.FALSE);

    /**
     * Constants for Caches
     */
    public static class Caches {

        private Caches() {

        }

        public static final String JOB_DETAILS = "JOB_DETAILS_V2";
    }

    @Produces
    @Readiness
    public HealthCheck infinispanHealthCheck(Instance<RemoteCacheManager> cacheManagerInstance) {
        return new InfinispanHealthCheck(cacheManagerInstance);
    }

    void initializeCaches(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE) StartupEvent startupEvent,
            Instance<RemoteCacheManager> remoteCacheManager,
            Event<InfinispanInitialized> initializedEvent) {
        // ProtoStream 6 no longer indexes interface-typed marshallers by Java class; this provider
        // keeps Trigger-typed values resolvable (see TriggerMarshallerProvider). Trigger marshalling
        // is mandatory for this repository, so a missing SerializationContext must abort startup.
        MarshallerUtil.getSerializationContext(remoteCacheManager.get())
                .registerMarshallerProvider(new TriggerMarshallerProvider());
        Optional.ofNullable(remoteCacheManager.get().getCache(JOB_DETAILS))
                .ifPresent(c -> {
                    initializedEvent.fire(new InfinispanInitialized());
                    initialized.set(Boolean.TRUE);
                });
    }

    protected Boolean isInitialized() {
        return initialized.get();
    }

}
