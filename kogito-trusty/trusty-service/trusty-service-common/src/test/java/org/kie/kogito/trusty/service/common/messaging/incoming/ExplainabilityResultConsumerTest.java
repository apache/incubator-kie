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

import java.net.URI;
import java.time.Duration;
import java.util.stream.Stream;

import org.apache.commons.lang3.builder.CompareToBuilder;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.event.cloudevents.utils.CloudEventUtils;
import org.kie.kogito.explainability.api.BaseExplainabilityResult;
import org.kie.kogito.explainability.api.CounterfactualExplainabilityResult;
import org.kie.kogito.explainability.api.FeatureImportanceModel;
import org.kie.kogito.explainability.api.LIMEExplainabilityResult;
import org.kie.kogito.trusty.service.common.TrustyService;
import org.kie.kogito.trusty.service.common.TrustyServiceTestUtils;
import org.kie.kogito.trusty.service.common.handlers.CounterfactualExplainabilityResultsManagerDuplicates;
import org.kie.kogito.trusty.service.common.handlers.CounterfactualExplainabilityResultsManagerSlidingWindow;
import org.kie.kogito.trusty.service.common.handlers.CounterfactualExplainerServiceHandler;
import org.kie.kogito.trusty.service.common.handlers.ExplainerServiceHandler;
import org.kie.kogito.trusty.service.common.handlers.ExplainerServiceHandlerRegistry;
import org.kie.kogito.trusty.service.common.handlers.LIMEExplainerServiceHandler;
import org.kie.kogito.trusty.storage.api.StorageExceptionsProvider;
import org.kie.kogito.trusty.storage.common.TrustyStorageService;

import io.cloudevents.CloudEvent;
import io.smallrye.context.SmallRyeManagedExecutor;

import jakarta.enterprise.inject.Instance;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExplainabilityResultConsumerTest {

    private static final String TEST_EXECUTION_ID = "test";
    private static final String TEST_COUNTERFACTUAL_ID = "counterfactualId";
    private static final String TEST_SOLUTION_ID = "solutionId";

    private TrustyService trustyService;
    private StorageExceptionsProvider storageExceptionsProvider;
    private ExplainabilityResultConsumer consumer;
    private LIMEExplainerServiceHandler limeExplainerServiceHandler;
    private CounterfactualExplainerServiceHandler counterfactualExplainerServiceHandler;
    private Instance<ExplainerServiceHandler<?>> explanationHandlers;
    private ExplainerServiceHandlerRegistry explainerServiceHandlerRegistry;
    private TrustyStorageService trustyStorage;

    private static CloudEvent buildLIMEExplainabilityCloudEvent(LIMEExplainabilityResult result) {
        return CloudEventUtils.build(
                result.getExecutionId(),
                URI.create("explainabilityResult/test"),
                result,
                LIMEExplainabilityResult.class).get();
    }

    private static String buildLIMECloudEventJsonString(LIMEExplainabilityResult result) {
        return CloudEventUtils.encode(buildLIMEExplainabilityCloudEvent(result)).orElseThrow(IllegalStateException::new);
    }

    private static CloudEvent buildCounterfactualExplainabilityCloudEvent(CounterfactualExplainabilityResult result) {
        return CloudEventUtils.build(
                result.getExecutionId(),
                URI.create("explainabilityResult/test"),
                result,
                CounterfactualExplainabilityResult.class).get();
    }

    private static String buildCounterfactualCloudEventJsonString(CounterfactualExplainabilityResult result) {
        return CloudEventUtils.encode(buildCounterfactualExplainabilityCloudEvent(result)).orElseThrow(IllegalStateException::new);
    }

    private static CloudEvent buildUnknownExplainabilityCloudEvent(BaseExplainabilityResult result) {
        return CloudEventUtils.build(
                result.getExecutionId(),
                URI.create("explainabilityResult/test"),
                result,
                BaseExplainabilityResult.class).get();
    }

    private static String buildUnknownExplainabilityCloudEventJsonString(BaseExplainabilityResult result) {
        return CloudEventUtils.encode(buildUnknownExplainabilityCloudEvent(result)).orElseThrow(IllegalStateException::new);
    }

    private static int compareFeatureImportance(FeatureImportanceModel expected, FeatureImportanceModel actual) {
        return new CompareToBuilder()
                .append(expected.getFeatureName(), actual.getFeatureName())
                .toComparison();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        trustyService = mock(TrustyService.class);
        storageExceptionsProvider = mock(StorageExceptionsProvider.class);
        trustyStorage = mock(TrustyStorageService.class);
        limeExplainerServiceHandler = new LIMEExplainerServiceHandler(trustyStorage);
        counterfactualExplainerServiceHandler = new CounterfactualExplainerServiceHandler(trustyStorage,
                mock(CounterfactualExplainabilityResultsManagerSlidingWindow.class),
                mock(CounterfactualExplainabilityResultsManagerDuplicates.class));
        explanationHandlers = mock(Instance.class);
        when(explanationHandlers.stream()).thenReturn(Stream.of(limeExplainerServiceHandler,
                counterfactualExplainerServiceHandler));
        explainerServiceHandlerRegistry = new ExplainerServiceHandlerRegistry(explanationHandlers);
        consumer = new ExplainabilityResultConsumer(trustyService,
                explainerServiceHandlerRegistry,
                TrustyServiceTestUtils.MAPPER,
                storageExceptionsProvider,
                SmallRyeManagedExecutor.builder().build());
    }

    @Test
    void testCorrectLIMECloudEvent() {
        Message<String> message = mockMessage(buildLIMECloudEventJsonString(LIMEExplainabilityResult.buildSucceeded(TEST_EXECUTION_ID,
                emptyList())));
        doNothing().when(trustyService).storeExplainabilityResult(any(String.class), any(BaseExplainabilityResult.class));

        testNumberOfInvocations(message, 1);
    }

    @Test
    void testCorrectCounterfactualCloudEvent() {
        Message<String> message = mockMessage(buildCounterfactualCloudEventJsonString(CounterfactualExplainabilityResult.buildSucceeded(TEST_EXECUTION_ID,
                TEST_COUNTERFACTUAL_ID,
                TEST_SOLUTION_ID,
                0L,
                Boolean.TRUE,
                CounterfactualExplainabilityResult.Stage.FINAL,
                emptyList(),
                emptyList())));
        doNothing().when(trustyService).storeExplainabilityResult(any(String.class), any(BaseExplainabilityResult.class));

        testNumberOfInvocations(message, 1);
    }

    @Test
    void testInvalidPayload() {
        Message<String> message = mockMessage("Not a cloud event");
        testNumberOfInvocations(message, 0);
    }

    @Test
    void testInvalidPayloadUnknownExplanationType() {
        BaseExplainabilityResult result = new BaseExplainabilityResult() {
            @Override
            public String getExecutionId() {
                return TEST_EXECUTION_ID;
            }
        };
        Message<String> message = mockMessage(buildUnknownExplainabilityCloudEventJsonString(result));
        testNumberOfInvocations(message, 0);
    }

    @Test
    void testExceptionsAreCaught() {
        Message<String> message = mockMessage(buildLIMECloudEventJsonString(LIMEExplainabilityResult.buildSucceeded(TEST_EXECUTION_ID,
                emptyList())));

        doThrow(new RuntimeException("Something really bad")).when(trustyService).storeExplainabilityResult(any(String.class), any(BaseExplainabilityResult.class));
        assertThatCode(() -> consumer.handleMessage(message)).doesNotThrowAnyException();
    }

    private Message<String> mockMessage(String payload) {
        @SuppressWarnings("unchecked")
        Message<String> message = mock(Message.class);
        when(message.getPayload()).thenReturn(payload);
        return message;
    }

    private void testNumberOfInvocations(Message<String> message, int wantedNumberOfServiceInvocations) {
        consumer.handleMessage(message);

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () -> {
                            verify(trustyService, times(wantedNumberOfServiceInvocations)).storeExplainabilityResult(any(), any());
                            verify(message, times(1)).ack();
                        });
    }
}
