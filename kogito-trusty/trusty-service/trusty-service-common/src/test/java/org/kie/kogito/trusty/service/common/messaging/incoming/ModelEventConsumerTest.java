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
package org.kie.kogito.trusty.service.common.messaging.incoming;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.trusty.service.common.TrustyService;
import org.kie.kogito.trusty.service.common.TrustyServiceTestUtils;
import org.kie.kogito.trusty.storage.api.StorageExceptionsProvider;
import org.kie.kogito.trusty.storage.api.model.decision.Decision;

import io.smallrye.context.SmallRyeManagedExecutor;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelEventConsumerTest {

    private TrustyService trustyService;
    private StorageExceptionsProvider storageExceptionsProvider;
    private ModelEventConsumer consumer;

    @BeforeEach
    void setup() {
        trustyService = mock(TrustyService.class);
        storageExceptionsProvider = mock(StorageExceptionsProvider.class);
        consumer = new ModelEventConsumer(trustyService,
                TrustyServiceTestUtils.MAPPER,
                storageExceptionsProvider,
                SmallRyeManagedExecutor.builder().build());
    }

    @Test
    void testCorrectCloudEvent() {
        Message<String> message = mockMessage(TrustyServiceTestUtils.buildCloudEventJsonString(TrustyServiceTestUtils.buildCorrectModelEvent()));
        testNumberOfInvocations(message, 1);
    }

    @Test
    void testCloudEventWithoutData() {
        Message<String> message = mockMessage(TrustyServiceTestUtils.buildCloudEventWithoutDataJsonString());
        testNumberOfInvocations(message, 0);
    }

    @Test
    void testGibberishPayload() {
        Message<String> message = mockMessage("DefinitelyNotASerializedCloudEvent123456");
        testNumberOfInvocations(message, 0);
    }

    @Test
    void testExceptionsAreCatched() {
        Message<String> message = mockMessage(TrustyServiceTestUtils.buildCloudEventJsonString(TrustyServiceTestUtils.buildCorrectTraceEvent(TrustyServiceTestUtils.CORRECT_CLOUDEVENT_ID)));

        doThrow(new RuntimeException("Something really bad")).when(trustyService).storeDecision(any(String.class), any(Decision.class));
        assertThatCode(() -> consumer.handleMessage(message)).doesNotThrowAnyException();
    }

    @Test
    void testUnsupportedExecutionTypesAreNotHandled() {
        Message<String> message = mockMessage(TrustyServiceTestUtils.buildCloudEventJsonString(TrustyServiceTestUtils.buildTraceEventWithNullType("test")));
        testNumberOfInvocations(message, 0);
    }

    private Message<String> mockMessage(String payload) {
        Message<String> message = mock(Message.class);
        when(message.getPayload()).thenReturn(payload);
        return message;
    }

    private void testNumberOfInvocations(final Message<String> message,
            final int wantedNumberOfServiceInvocations) {
        consumer.handleMessage(message);

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () -> {
                            verify(trustyService, times(wantedNumberOfServiceInvocations)).storeModel(any());
                            verify(message, times(1)).ack();
                        });
    }
}
