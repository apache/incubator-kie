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
package org.kie.kogito.explainability;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.kie.kogito.explainability.api.BaseExplainabilityResult;
import org.kie.kogito.explainability.api.CounterfactualExplainabilityResult;
import org.kie.kogito.explainability.api.ExplainabilityStatus;
import org.kie.kogito.explainability.api.FeatureImportanceModel;
import org.kie.kogito.explainability.api.LIMEExplainabilityResult;
import org.kie.kogito.explainability.api.NamedTypedValue;
import org.kie.kogito.explainability.api.SaliencyModel;
import org.kie.kogito.explainability.handlers.CounterfactualExplainerServiceHandler;
import org.kie.kogito.explainability.handlers.LimeExplainerServiceHandler;
import org.kie.kogito.explainability.handlers.LocalExplainerServiceHandlerRegistry;
import org.kie.kogito.explainability.local.counterfactual.CounterfactualExplainer;
import org.kie.kogito.explainability.local.lime.LimeExplainer;
import org.kie.kogito.explainability.model.Prediction;
import org.kie.kogito.explainability.model.PredictionProvider;

import jakarta.enterprise.inject.Instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.kie.kogito.explainability.TestUtils.COUNTERFACTUAL_ID;
import static org.kie.kogito.explainability.TestUtils.COUNTERFACTUAL_REQUEST;
import static org.kie.kogito.explainability.TestUtils.COUNTERFACTUAL_RESULT;
import static org.kie.kogito.explainability.TestUtils.EXECUTION_ID;
import static org.kie.kogito.explainability.TestUtils.FEATURE_IMPORTANCE_1;
import static org.kie.kogito.explainability.TestUtils.LIME_REQUEST;
import static org.kie.kogito.explainability.TestUtils.SALIENCY;
import static org.kie.kogito.explainability.TestUtils.SALIENCY_MAP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class ExplanationServiceImplTest {

    private static final Long MAX_RUNNING_TIME_SECONDS = 60L;

    @SuppressWarnings("rawtype")
    Instance instance;
    ExplanationServiceImpl explanationService;
    LimeExplainer limeExplainerMock;
    LimeExplainerServiceHandler limeExplainerServiceHandlerMock;
    CounterfactualExplainer cfExplainerMock;
    CounterfactualExplainerServiceHandler cfExplainerServiceHandlerMock;
    LocalExplainerServiceHandlerRegistry explainerServiceHandlerRegistryMock;
    PredictionProvider predictionProviderMock;
    Consumer<BaseExplainabilityResult> callbackMock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void init() {
        instance = mock(Instance.class);
        limeExplainerMock = mock(LimeExplainer.class);
        cfExplainerMock = mock(CounterfactualExplainer.class);
        PredictionProviderFactory predictionProviderFactory = mock(PredictionProviderFactory.class);
        explainerServiceHandlerRegistryMock = new LocalExplainerServiceHandlerRegistry(instance);
        limeExplainerServiceHandlerMock = spy(new LimeExplainerServiceHandler(limeExplainerMock,
                predictionProviderFactory));
        cfExplainerServiceHandlerMock = spy(new CounterfactualExplainerServiceHandler(cfExplainerMock,
                predictionProviderFactory,
                MAX_RUNNING_TIME_SECONDS));

        predictionProviderMock = mock(PredictionProvider.class);
        callbackMock = mock(Consumer.class);
        explanationService = new ExplanationServiceImpl(explainerServiceHandlerRegistryMock);
        when(predictionProviderFactory.createPredictionProvider(any(), any(), any())).thenReturn(predictionProviderMock);
    }

    @Test
    void testLIMEExplainAsyncSucceeded() throws Throwable {
        testLIMEExplainAsyncSuccess(() -> explanationService.explainAsync(LIME_REQUEST, callbackMock)
                .toCompletableFuture()
                .get(Config.INSTANCE.getAsyncTimeout(), Config.INSTANCE.getAsyncTimeUnit()));
    }

    @Test
    void testLIMEExplainAsyncSucceededWithoutCallback() throws Throwable {
        testLIMEExplainAsyncSuccess(() -> explanationService.explainAsync(LIME_REQUEST)
                .toCompletableFuture()
                .get(Config.INSTANCE.getAsyncTimeout(), Config.INSTANCE.getAsyncTimeUnit()));
    }

    @SuppressWarnings("unchecked")
    void testLIMEExplainAsyncSuccess(ThrowingSupplier<BaseExplainabilityResult> invocation) throws Throwable {
        when(instance.stream()).thenReturn(Stream.of(limeExplainerServiceHandlerMock));
        when(limeExplainerMock.explainAsync(any(Prediction.class),
                eq(predictionProviderMock),
                any(Consumer.class)))
                        .thenReturn(CompletableFuture.completedFuture(SALIENCY_MAP));

        BaseExplainabilityResult result = invocation.get();

        assertThat(result).isNotNull();
        assertThat((result instanceof LIMEExplainabilityResult)).isTrue();
        LIMEExplainabilityResult limeResult = (LIMEExplainabilityResult) result;

        assertThat(limeResult.getExecutionId()).isEqualTo(EXECUTION_ID);
        assertThat(limeResult.getStatus()).isSameAs(ExplainabilityStatus.SUCCEEDED);
        assertThat(limeResult.getStatusDetails()).isNull();
        assertThat(limeResult.getSaliencies().size()).isEqualTo(SALIENCY_MAP.size());

        SaliencyModel saliency = limeResult.getSaliencies().iterator().next();
        assertThat(saliency.getFeatureImportance().size()).isEqualTo(SALIENCY.getPerFeatureImportance().size());

        FeatureImportanceModel featureImportance1 = saliency.getFeatureImportance().get(0);
        assertThat(featureImportance1.getFeatureName()).isEqualTo(FEATURE_IMPORTANCE_1.getFeature().getName());
        assertThat(featureImportance1.getFeatureScore()).isCloseTo(FEATURE_IMPORTANCE_1.getScore(), within(0.01));
    }

    @Test
    void testCounterfactualsExplainAsyncSucceeded() throws Throwable {
        testCounterfactualsExplainAsyncSuccess(() -> explanationService.explainAsync(COUNTERFACTUAL_REQUEST, callbackMock)
                .toCompletableFuture()
                .get(Config.INSTANCE.getAsyncTimeout(), Config.INSTANCE.getAsyncTimeUnit()));
    }

    @Test
    void testCounterfactualsExplainAsyncSucceededWithoutCallback() throws Throwable {
        testCounterfactualsExplainAsyncSuccess(() -> explanationService.explainAsync(COUNTERFACTUAL_REQUEST)
                .toCompletableFuture()
                .get(Config.INSTANCE.getAsyncTimeout(), Config.INSTANCE.getAsyncTimeUnit()));
    }

    @SuppressWarnings("unchecked")
    void testCounterfactualsExplainAsyncSuccess(ThrowingSupplier<BaseExplainabilityResult> invocation) throws Throwable {
        when(instance.stream()).thenReturn(Stream.of(cfExplainerServiceHandlerMock));

        when(cfExplainerMock.explainAsync(any(Prediction.class),
                eq(predictionProviderMock),
                any(Consumer.class))).thenReturn(CompletableFuture.completedFuture(COUNTERFACTUAL_RESULT));

        BaseExplainabilityResult result = invocation.get();

        assertThat(result).isNotNull();
        assertThat((result instanceof CounterfactualExplainabilityResult)).isTrue();
        CounterfactualExplainabilityResult counterfactualResult = (CounterfactualExplainabilityResult) result;

        assertThat(counterfactualResult.getExecutionId()).isEqualTo(EXECUTION_ID);
        assertThat(counterfactualResult.getCounterfactualId()).isEqualTo(COUNTERFACTUAL_ID);
        assertThat(counterfactualResult.getStatus()).isSameAs(ExplainabilityStatus.SUCCEEDED);
        assertThat(counterfactualResult.getStatusDetails()).isNull();
        assertThat(counterfactualResult.getInputs().size()).isEqualTo(COUNTERFACTUAL_RESULT.getEntities().size());
        assertThat(counterfactualResult.getOutputs().size()).isEqualTo(COUNTERFACTUAL_RESULT.getOutput().size());
        assertThat(counterfactualResult.getOutputs().stream().anyMatch(o -> o.getName().equals("output1"))).isTrue();

        NamedTypedValue value = counterfactualResult.getOutputs().iterator().next();
        assertThat(value.getValue().isUnit()).isTrue();
        assertThat(value.getValue().toUnit().getType()).isEqualTo(Double.class.getSimpleName());
        assertThat(value.getValue().toUnit().getValue().asDouble()).isEqualTo(555.0);
    }

    @Test
    void testServiceCallFailed() {
        String errorMessage = "Something bad happened";
        RuntimeException exception = new RuntimeException(errorMessage);

        when(instance.stream()).thenReturn(Stream.of(limeExplainerServiceHandlerMock));
        doThrow(exception).when(limeExplainerServiceHandlerMock).supports(any());

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> explanationService.explainAsync(LIME_REQUEST, callbackMock)
                .toCompletableFuture()
                .get(Config.INSTANCE.getAsyncTimeout(), Config.INSTANCE.getAsyncTimeUnit()));
    }

    @Test
    void testServiceCallFailedNoMatchingServiceHandlers() {
        when(instance.stream()).thenReturn(Stream.of());

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> explanationService.explainAsync(LIME_REQUEST, callbackMock)
                .toCompletableFuture()
                .get(Config.INSTANCE.getAsyncTimeout(), Config.INSTANCE.getAsyncTimeUnit()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testLIMEExplainAsyncFailed() {
        String errorMessage = "Something bad happened";
        RuntimeException exception = new RuntimeException(errorMessage);

        when(instance.stream()).thenReturn(Stream.of(limeExplainerServiceHandlerMock));
        when(limeExplainerMock.explainAsync(any(Prediction.class),
                eq(predictionProviderMock),
                any(Consumer.class)))
                        .thenThrow(exception);

        final CompletableFuture<BaseExplainabilityResult> future = explanationService.explainAsync(LIME_REQUEST, callbackMock)
                .toCompletableFuture();
        assertThatCode(() -> future.get(Config.INSTANCE.getAsyncTimeout(), Config.INSTANCE.getAsyncTimeUnit())).doesNotThrowAnyException();
        BaseExplainabilityResult result = future.join();

        assertThat(result).isNotNull();
        assertThat((result instanceof LIMEExplainabilityResult)).isTrue();
        LIMEExplainabilityResult exceptionResult = (LIMEExplainabilityResult) result;

        assertThat(exceptionResult.getExecutionId()).isEqualTo(EXECUTION_ID);
        assertThat(exceptionResult.getStatus()).isSameAs(ExplainabilityStatus.FAILED);
        assertThat(exceptionResult.getStatusDetails()).isEqualTo(errorMessage);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCounterfactualsxplainAsyncFailed() {
        String errorMessage = "Something bad happened";
        RuntimeException exception = new RuntimeException(errorMessage);

        when(instance.stream()).thenReturn(Stream.of(cfExplainerServiceHandlerMock));
        when(cfExplainerMock.explainAsync(any(Prediction.class),
                eq(predictionProviderMock),
                any(Consumer.class)))
                        .thenThrow(exception);

        final CompletableFuture<BaseExplainabilityResult> future = explanationService.explainAsync(COUNTERFACTUAL_REQUEST, callbackMock)
                .toCompletableFuture();
        assertThatCode(() -> future.get(Config.INSTANCE.getAsyncTimeout(), Config.INSTANCE.getAsyncTimeUnit())).doesNotThrowAnyException();
        BaseExplainabilityResult result = future.join();

        assertThat(result).isNotNull();
        assertThat((result instanceof CounterfactualExplainabilityResult)).isTrue();
        CounterfactualExplainabilityResult exceptionResult = (CounterfactualExplainabilityResult) result;

        assertThat(exceptionResult.getExecutionId()).isEqualTo(EXECUTION_ID);
        assertThat(exceptionResult.getStatus()).isSameAs(ExplainabilityStatus.FAILED);
        assertThat(exceptionResult.getStatusDetails()).isEqualTo(errorMessage);
    }

    @Test
    void testServiceHandlerLookupLIME() {
        when(instance.stream()).thenReturn(Stream.of(limeExplainerServiceHandlerMock, cfExplainerServiceHandlerMock));

        when(limeExplainerMock.explainAsync(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(SALIENCY_MAP));

        final CompletableFuture<BaseExplainabilityResult> future = explanationService.explainAsync(LIME_REQUEST, callbackMock)
                .toCompletableFuture();
        assertThatCode(() -> future.get(Config.INSTANCE.getAsyncTimeout(), Config.INSTANCE.getAsyncTimeUnit())).doesNotThrowAnyException();
        BaseExplainabilityResult result = future.join();

        assertThat(result).isNotNull();
        assertThat((result instanceof LIMEExplainabilityResult)).isTrue();
    }

    @Test
    void testServiceHandlerLookupCounterfactuals() {
        when(instance.stream()).thenReturn(Stream.of(limeExplainerServiceHandlerMock, cfExplainerServiceHandlerMock));

        when(cfExplainerMock.explainAsync(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(COUNTERFACTUAL_RESULT));

        final CompletableFuture<BaseExplainabilityResult> future = explanationService.explainAsync(COUNTERFACTUAL_REQUEST, callbackMock)
                .toCompletableFuture();
        assertThatCode(() -> future.get(Config.INSTANCE.getAsyncTimeout(), Config.INSTANCE.getAsyncTimeUnit())).doesNotThrowAnyException();
        BaseExplainabilityResult result = future.join();

        assertThat(result).isNotNull();
        assertThat((result instanceof CounterfactualExplainabilityResult)).isTrue();
    }
}
