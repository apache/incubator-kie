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
package org.kie.kogito.tracing.decision;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.kogito.Application;
import org.kie.kogito.config.ConfigBean;
import org.kie.kogito.config.StaticConfigBean;
import org.kie.kogito.decision.DecisionModel;
import org.kie.kogito.decision.DecisionModels;
import org.kie.kogito.dmn.DMNKogito;
import org.kie.kogito.dmn.DmnDecisionModel;
import org.kie.kogito.event.cloudevents.utils.CloudEventUtils;
import org.kie.kogito.tracing.decision.event.evaluate.EvaluateEvent;
import org.kie.kogito.tracing.event.trace.TraceEvent;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.CloudEvent;
import io.cloudevents.jackson.JsonFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public abstract class BaseSpringBootDecisionTracingTest {

    private static final String TEST_EXECUTION_ID = "7c50581e-6e5b-407b-91d6-2ffb1d47ebc0";
    private static final String TEST_SERVICE_URL = "localhost:8080";
    private static final String TEST_KAFKA_TOPIC = "kogito-tracing-decision";
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(JsonFormat.getCloudEventJacksonModule());

    private final String TEST_RESOURCE = "/" + getTestModelName() + ".dmn";

    protected abstract String getTestModelName();

    protected abstract String getTestModelNameSpace();

    protected abstract Map<String, Object> getContextVariables();

    protected abstract int getEvaluationEventCount();

    @Test
    void testAsyncListenerAndCollectorWithRealEventsIsWorking() throws IOException {
        final DMNRuntime runtime = buildDMNRuntime();
        final DecisionModel model = buildDecisionModel(runtime);
        final List<EvaluateEvent> events = testListener(true, runtime, model);
        testCollector(events, model);
    }

    @Test
    void testSyncListenerAndCollectorWithRealEventsIsWorking() throws IOException {
        final DMNRuntime runtime = buildDMNRuntime();
        final DecisionModel model = buildDecisionModel(runtime);
        final List<EvaluateEvent> events = testListener(false, runtime, model);
        testCollector(events, model);
    }

    private DMNRuntime buildDMNRuntime() {
        return DMNKogito.createGenericDMNRuntime(Collections.emptySet(), false, Map.of(TEST_RESOURCE, StandardCharsets.UTF_8.name()));
    }

    private DecisionModel buildDecisionModel(DMNRuntime runtime) {
        return new DmnDecisionModel(runtime, getTestModelNameSpace(), getTestModelName(), () -> TEST_EXECUTION_ID);
    }

    private List<EvaluateEvent> testListener(boolean asyncEnabled, DMNRuntime runtime, DecisionModel model) {

        final ApplicationEventPublisher mockedEventPublisher = mock(ApplicationEventPublisher.class);
        final SpringBootDecisionTracingCollector mockedCollector = mock(SpringBootDecisionTracingCollector.class);

        SpringBootDecisionTracingListener listener = new SpringBootDecisionTracingListener(mockedEventPublisher, mockedCollector, asyncEnabled);
        runtime.addListener(listener);

        final DMNContext context = model.newContext(getContextVariables());
        model.evaluateAll(context);

        ArgumentCaptor<EvaluateEvent> eventCaptor = ArgumentCaptor.forClass(EvaluateEvent.class);

        if (asyncEnabled) {
            verify(mockedEventPublisher, times(getEvaluationEventCount())).publishEvent(eventCaptor.capture());
            verify(mockedCollector, never()).onApplicationEvent(any());
        } else {
            verify(mockedEventPublisher, never()).publishEvent(any());
            verify(mockedCollector, times(getEvaluationEventCount())).onApplicationEvent(eventCaptor.capture());
        }

        return eventCaptor.getAllValues();
    }

    @SuppressWarnings("unchecked")
    private void testCollector(List<EvaluateEvent> events, DecisionModel model) throws IOException {
        final DecisionModels mockedDecisionModels = mock(DecisionModels.class);
        when(mockedDecisionModels.getDecisionModel(getTestModelNameSpace(), getTestModelName())).thenReturn(model);

        final Application mockedApplication = mock(Application.class);
        when(mockedApplication.get(any())).thenReturn(mockedDecisionModels);

        final ConfigBean configBean = new StaticConfigBean(TEST_SERVICE_URL, true, null);

        final KafkaTemplate<String, String> mockedTemplate = mock(KafkaTemplate.class);
        final SpringBootTraceEventEmitter eventEmitter = new SpringBootTraceEventEmitter(mockedTemplate, TEST_KAFKA_TOPIC);

        SpringBootDecisionTracingCollector collector = new SpringBootDecisionTracingCollector(eventEmitter, configBean, mockedApplication);
        events.forEach(collector::onApplicationEvent);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockedTemplate).send(eq(TEST_KAFKA_TOPIC), payloadCaptor.capture());

        CloudEvent cloudEvent = CloudEventUtils
                .decode(payloadCaptor.getValue())
                .orElseThrow(() -> new IllegalStateException("Can't decode CloudEvent"));

        assertThat(cloudEvent.getId()).isEqualTo(TEST_EXECUTION_ID);
        assertThat(cloudEvent.getData()).isNotNull();

        TraceEvent traceEvent = MAPPER.readValue(cloudEvent.getData().toBytes(), TraceEvent.class);

        assertThat(traceEvent).isNotNull();
        assertThat(traceEvent.getHeader().getResourceId().getServiceUrl()).isEqualTo(TEST_SERVICE_URL);
    }
}
