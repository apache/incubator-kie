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

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.quarkus.runtime.StartupEvent;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InfinispanConfigurationTest {

    private InfinispanConfiguration tested;

    @BeforeEach
    public void setUp() {
        tested = new InfinispanConfiguration();
    }

    @Test
    void initializeCaches(@Mock Event<InfinispanInitialized> initializedEvent,
            @Mock RemoteCacheManager remoteCacheManager,
            @Mock Instance<RemoteCacheManager> instance,
            @Mock RemoteCache<Object, Object> cache) {
        when(instance.get()).thenReturn(remoteCacheManager);
        when(remoteCacheManager.getMarshaller()).thenReturn(new ProtoStreamMarshaller());
        when(remoteCacheManager.getCache(InfinispanConfiguration.Caches.JOB_DETAILS)).thenReturn(cache);

        assertThat(tested.isInitialized()).isFalse();
        tested.initializeCaches(new StartupEvent(), instance, initializedEvent);
        verify(remoteCacheManager).getCache(eq(InfinispanConfiguration.Caches.JOB_DETAILS));

        verify(initializedEvent).fire(any(InfinispanInitialized.class));
        assertThat(tested.isInitialized()).isTrue();

    }
}
