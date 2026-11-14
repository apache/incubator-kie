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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.CloudEvent;
import io.cloudevents.jackson.JsonFormat;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import io.vertx.core.eventbus.EventBus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public abstract class BaseQuarkusDecisionTracingTest {

    private static final String TEST_EXECUTION_ID = "7c50581e-6e5b-407b-91d6-2ffb1d47ebc0";
    private static final String TEST_SERVICE_URL = "localhost:8080";
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
        assertThat(events).hasSize(getEvaluationEventCount());
        testCollector(events, model);
    }

    @Test
    void testSyncListenerAndCollectorWithRealEventsIsWorking() throws IOException {
        final DMNRuntime runtime = buildDMNRuntime();
        final DecisionModel model = buildDecisionModel(runtime);
        final List<EvaluateEvent> events = testListener(false, runtime, model);
        assertThat(events).hasSize(getEvaluationEventCount());
        testCollector(events, model);
    }

    private DMNRuntime buildDMNRuntime() {
        return DMNKogito.createGenericDMNRuntime(new java.io.InputStreamReader(
                BaseQuarkusDecisionTracingTest.class.getResourceAsStream(TEST_RESOURCE)));
    }

    private DecisionModel buildDecisionModel(DMNRuntime runtime) {
        return new DmnDecisionModel(runtime, getTestModelNameSpace(), getTestModelName(), () -> TEST_EXECUTION_ID);
    }

    private List<EvaluateEvent> testListener(boolean asyncEnabled, DMNRuntime runtime, DecisionModel model) {

        final EventBus mockedEventBus = mock(EventBus.class);
        final QuarkusDecisionTracingCollector mockedCollector = mock(QuarkusDecisionTracingCollector.class);

        QuarkusDecisionTracingListener listener = new QuarkusDecisionTracingListener(mockedEventBus, mockedCollector, asyncEnabled);
        runtime.addListener(listener);

        final DMNContext context = model.newContext(getContextVariables());
        model.evaluateAll(context);

        ArgumentCaptor<EvaluateEvent> eventCaptor = ArgumentCaptor.forClass(EvaluateEvent.class);

        if (asyncEnabled) {
            verify(mockedEventBus, times(getEvaluationEventCount())).send(eq("kogito-tracing-decision_EvaluateEvent"), eventCaptor.capture());
            verify(mockedCollector, never()).onEvent(any());
        } else {
            verify(mockedEventBus, never()).send(eq("kogito-tracing-decision_EvaluateEvent"), any());
            verify(mockedCollector, times(getEvaluationEventCount())).onEvent(eventCaptor.capture());
        }

        return eventCaptor.getAllValues();
    }

    private void testCollector(List<EvaluateEvent> events, DecisionModel model) throws IOException {
        AssertSubscriber<String> subscriber = AssertSubscriber.create(1);

        final DecisionModels mockedDecisionModels = mock(DecisionModels.class);
        when(mockedDecisionModels.getDecisionModel(getTestModelNameSpace(), getTestModelName())).thenReturn(model);
        final Application mockedApplication = mock(Application.class);
        when(mockedApplication.get(any())).thenReturn(mockedDecisionModels);
        final ConfigBean configBean = new StaticConfigBean(TEST_SERVICE_URL, true, null);
        final QuarkusTraceEventEmitter eventEmitter = new QuarkusTraceEventEmitter();

        QuarkusDecisionTracingCollector collector = new QuarkusDecisionTracingCollector(eventEmitter, configBean, mockedApplication);
        eventEmitter.getEventPublisher().subscribe(subscriber);
        events.forEach(collector::onEvent);

        subscriber.assertNotTerminated();

        List<String> items = subscriber.getItems();
        assertThat(items).hasSize(1);

        CloudEvent cloudEvent = CloudEventUtils
                .decode(items.get(0))
                .orElseThrow(() -> new IllegalStateException("Can't decode CloudEvent"));

        assertThat(cloudEvent.getId()).isEqualTo(TEST_EXECUTION_ID);
        assertThat(cloudEvent.getData()).isNotNull();

        TraceEvent traceEvent = MAPPER.readValue(cloudEvent.getData().toBytes(), TraceEvent.class);

        assertThat(traceEvent).isNotNull();
        assertThat(traceEvent.getHeader().getResourceId().getServiceUrl()).isEqualTo(TEST_SERVICE_URL);
    }
}
