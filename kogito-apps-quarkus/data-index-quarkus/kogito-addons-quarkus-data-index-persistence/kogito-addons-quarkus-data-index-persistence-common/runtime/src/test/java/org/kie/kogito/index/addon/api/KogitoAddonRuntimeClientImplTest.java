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
package org.kie.kogito.index.addon.api;

import java.nio.Buffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.Application;
import org.kie.kogito.index.addon.api.models.TestModel;
import org.kie.kogito.index.api.ExecuteArgs;
import org.kie.kogito.index.model.*;
import org.kie.kogito.index.model.Timer;
import org.kie.kogito.index.service.DataIndexServiceException;
import org.kie.kogito.index.service.KogitoRuntimeCommonClient;
import org.kie.kogito.index.service.auth.DataIndexAuthTokenReader;
import org.kie.kogito.index.test.TestUtils;
import org.kie.kogito.jackson.utils.ObjectMapperFactory;
import org.kie.kogito.jobs.TimerDescription;
import org.kie.kogito.process.ProcessError;
import org.kie.kogito.process.ProcessInstanceExecutionException;
import org.kie.kogito.process.ProcessInstances;
import org.kie.kogito.process.Processes;
import org.kie.kogito.process.impl.AbstractProcess;
import org.kie.kogito.services.uow.CollectingUnitOfWorkFactory;
import org.kie.kogito.services.uow.DefaultUnitOfWorkManager;
import org.kie.kogito.source.files.SourceFilesProvider;
import org.kie.kogito.svg.ProcessSvgService;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class KogitoAddonRuntimeClientImplTest {

    private static int ACTIVE = 1;
    private static int ERROR = 5;
    private static String SERVICE_URL = "http://runtimeURL.com";
    private static String PROCESS_INSTANCE_ID = "pId";
    private static final String NODE_ID = "nodeId";
    private static String TASK_ID = "taskId";
    private static String JOB_ID = "jobId";
    private static String AUTHORIZED_TOKEN = "Bearer authToken";

    @Mock
    public Vertx vertx;

    @Mock
    private DataIndexAuthTokenReader authTokenReader;

    @Mock
    private WebClient webClientMock;

    @Mock
    private HttpRequest httpRequestMock;

    public static final String NODE_ID_ERROR = "processInstanceIdError";

    private KogitoAddonRuntimeClient client;

    @Mock
    private ProcessSvgService processSvgService;

    @Mock
    private SourceFilesProvider sourceFilesProvider;

    @Mock
    private Processes processes;

    @Mock
    private AbstractProcess process;

    @Mock
    private ProcessInstances instances;

    @Mock
    private org.kie.kogito.process.ProcessInstance processInstance;

    @Mock
    private ProcessError error;

    TestModel model;

    @Mock
    private Application application;

    @BeforeEach
    public void setup() {
        model = spy(new TestModel());
        lenient().when(processes.processById(anyString())).thenReturn(process);
        lenient().when(process.createModel()).thenReturn(model);
        lenient().when(process.createInstance(model)).thenReturn(processInstance);
        lenient().when(process.instances()).thenReturn(instances);
        lenient().when(instances.findById(anyString())).thenReturn(Optional.of(processInstance));
        lenient().when(processInstance.error()).thenReturn(Optional.of(error));
        lenient().when(processInstance.variables()).thenReturn(model);
        lenient().when(processInstance.id()).thenReturn(PROCESS_INSTANCE_ID);
        lenient().when(processInstance.status()).thenReturn(org.kie.kogito.process.ProcessInstance.STATE_ERROR);
        lenient().when(processInstance.updateVariables(any())).then(AdditionalAnswers.returnsFirstArg());
        lenient().when(error.failedNodeId()).thenReturn(NODE_ID_ERROR);
        lenient().when(error.errorMessage()).thenReturn("Test error message");
        lenient().when(application.unitOfWorkManager()).thenReturn(new DefaultUnitOfWorkManager(new CollectingUnitOfWorkFactory()));

        client = spy(new KogitoAddonRuntimeClient(Optional.empty(), authTokenReader, Vertx.vertx(), List.of(processSvgService), List.of(sourceFilesProvider), List.of(processes), List.of(application),
                ManagedExecutor.builder().build()));
        client.addServiceWebClient(SERVICE_URL, webClientMock);
    }

    private org.kie.kogito.process.ProcessInstance mockProcessInstanceStatusActive() {
        return doAnswer((v) -> {
            when(processInstance.status()).thenReturn(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
            return null;
        }).when(processInstance);
    }

    private org.kie.kogito.process.ProcessInstance mockProcessInstanceStatusError() {
        return doAnswer((v) -> {
            when(processInstance.status()).thenReturn(org.kie.kogito.process.ProcessInstance.STATE_ERROR);
            return null;
        }).when(processInstance);
    }

    private ProcessError mockProcessInstanceStatusActiveOnError() {
        return doAnswer((v) -> {
            when(processInstance.status()).thenReturn(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
            return null;
        }).when(error);
    }

    @Test
    public void testGetProcessInstanceTimers() throws Exception {
        ProcessInstance pI = createProcessInstance(PROCESS_INSTANCE_ID, ACTIVE);

        WorkflowProcessInstance workflowProcessInstance = mock(WorkflowProcessInstance.class);
        when(workflowProcessInstance.getProcessId()).thenReturn(pI.getProcessId());
        when(workflowProcessInstance.getId()).thenReturn(pI.getId());

        List<TimerDescription> timers = new ArrayList<>();
        timers.add(TimerDescription.Builder
                .ofProcessInstance(workflowProcessInstance)
                .timerId("timerId")
                .timerDescription("SLA")
                .build());

        when(processInstance.timers()).thenReturn(timers);

        CompletableFuture<List<Timer>> result = client.getProcessInstanceTimers(SERVICE_URL, pI);

        verify(processes, times(1)).processById(anyString());
        verify(instances, times(1)).findById(anyString());
        verify(processInstance, times(1)).timers();

        assertThat(result).isNotNull()
                .isDone();

        assertThat(result.get())
                .hasSize(1)
                .element(0)
                .hasFieldOrPropertyWithValue("processId", pI.getProcessId())
                .hasFieldOrPropertyWithValue("processInstanceId", pI.getId())
                .hasFieldOrPropertyWithValue("timerId", "timerId")
                .hasFieldOrPropertyWithValue("description", "SLA");
    }

    @Test
    public void testGetProcessInstanceTimersErrorWithWrongProcessId() {
        ProcessInstance pI = createProcessInstance(PROCESS_INSTANCE_ID, ACTIVE);

        when(processes.processById(anyString())).thenReturn(null);

        assertThatThrownBy(() -> client.getProcessInstanceTimers(SERVICE_URL, pI))
                .isInstanceOf(DataIndexServiceException.class)
                .hasMessage("Cannot get timers for process instance 'pId': process with id 'travels' cannot be found");

        verify(processes, times(1)).processById(anyString());
        verify(instances, never()).findById(anyString());
        verify(processInstance, never()).timers();
    }

    @Test
    public void testGetProcessInstanceTimersErrorWithWrongProcessInstanceId() {
        ProcessInstance pI = createProcessInstance(PROCESS_INSTANCE_ID, ACTIVE);

        when(instances.findById(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> client.getProcessInstanceTimers(SERVICE_URL, pI))
                .isInstanceOf(DataIndexServiceException.class)
                .hasMessage("Cannot get timers for process instance 'pId': instance cannot be found in process 'travels'");

        verify(processes, times(1)).processById(anyString());
        verify(instances, times(1)).findById(anyString());
        verify(processInstance, never()).timers();
    }

    @Test
    public void testGetProcessInstanceTimersErrorWithUnexpectedErrorGettingTimers() {
        ProcessInstance pI = createProcessInstance(PROCESS_INSTANCE_ID, ACTIVE);

        when(processInstance.timers()).thenThrow(new RuntimeException("Something went wrong"));

        assertThatThrownBy(() -> client.getProcessInstanceTimers(SERVICE_URL, pI))
                .isInstanceOf(DataIndexServiceException.class)
                .hasMessage("Failure getting timers for process instance 'pId'")
                .hasRootCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("Something went wrong");

        verify(processes, times(1)).processById(anyString());
        verify(instances, times(1)).findById(anyString());
        verify(processInstance, times(1)).timers();
    }

    @Test
    void testExecuteAfterSuccess() {
        ProcessDefinition definition = new ProcessDefinition();
        definition.setId("infra");
        JsonNode body = ObjectMapperFactory.get().createObjectNode().put("name", "javierito");
        client.executeProcessInstance(definition, ExecuteArgs.of(body));
        verify(model).update(Map.of("name", "javierito"));
        verify(processInstance).start();
    }

    @Test
    void testAbortProcessInstanceSuccess() {
        ProcessInstance pI = createProcessInstance(PROCESS_INSTANCE_ID, ACTIVE);
        mockProcessInstanceStatusActive().abort();
        client.abortProcessInstance(SERVICE_URL, pI);
        verify(processInstance, times(0)).error();
        verify(processInstance, times(1)).abort();
    }

    @Test
    void testAbortProcessInstanceError() {
        ProcessInstance pI = createProcessInstance(PROCESS_INSTANCE_ID, ACTIVE);
        mockProcessInstanceStatusError().abort();
        assertThatExceptionOfType(ProcessInstanceExecutionException.class).isThrownBy(() -> client.abortProcessInstance(SERVICE_URL, pI));
        verify(processInstance, times(1)).error();
        verify(processInstance, times(1)).abort();
    }

    @Test
    void testRetryProcessInstance() {
        mockProcessInstanceStatusActiveOnError().retrigger();
        ProcessInstance pI = createProcessInstance(PROCESS_INSTANCE_ID, ACTIVE);
        client.retryProcessInstance(SERVICE_URL, pI);
        verify(processInstance, times(1)).error();
        verify(error, times(1)).retrigger();
        verify(error, times(0)).skip();
    }

    @Test
    void testSkipProcessInstance() {
        mockProcessInstanceStatusActiveOnError().skip();
        ProcessInstance pI = createProcessInstance(PROCESS_INSTANCE_ID, ACTIVE);
        client.skipProcessInstance(SERVICE_URL, pI);
        verify(processInstance, times(1)).error();
        verify(error, times(0)).retrigger();
        verify(error, times(1)).skip();
    }

    @Test
    void testTriggerNodeInstance() {
        mockProcessInstanceStatusActive().triggerNode(NODE_ID);
        ProcessInstance pI = createProcessInstance(PROCESS_INSTANCE_ID, ACTIVE);
        client.triggerNodeInstance(SERVICE_URL, pI, NODE_ID);
        verify(processInstance, times(0)).error();
        verify(processInstance, times(1)).triggerNode(NODE_ID);
    }

    @Test
    void testRetriggerNodeInstance() {
        mockProcessInstanceStatusActive().retriggerNodeInstance(NODE_ID);
        ProcessInstance pI = createProcessInstance(PROCESS_INSTANCE_ID, ACTIVE);
        client.retriggerNodeInstance(SERVICE_URL, pI, NODE_ID);
        verify(processInstance, times(0)).error();
        verify(processInstance, times(1)).retriggerNodeInstance(NODE_ID);
    }

    @Test
    void testCancelNodeInstance() {
        mockProcessInstanceStatusActive().cancelNodeInstance(NODE_ID);
        ProcessInstance pI = createProcessInstance(PROCESS_INSTANCE_ID, ACTIVE);
        client.cancelNodeInstance(SERVICE_URL, pI, NODE_ID);
        verify(processInstance, times(0)).error();
        verify(processInstance, times(1)).cancelNodeInstance(NODE_ID);
    }

    @Test
    protected void testCancelJobRest() {
        setupIdentityMock();
        when(webClientMock.delete(anyString())).thenReturn(httpRequestMock);
        Job job = createJob(JOB_ID, PROCESS_INSTANCE_ID, "SCHEDULED");
        client.cancelJob(SERVICE_URL, job);

        verify(client).sendDeleteClientRequest(webClientMock,
                format(KogitoRuntimeCommonClient.CANCEL_JOB_PATH, job.getId()),
                "CANCEL Job with id: " + JOB_ID);
        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(httpRequestMock).send(handlerCaptor.capture());
        checkResponseHandling(handlerCaptor.getValue());
    }

    @Test
    protected void testRescheduleWithoutJobServiceInstance() {
        String newJobData = "{\"expirationTime\": \"2023-08-27T04:35:54.631Z\",\"retries\": 2}";
        setupIdentityMock();
        when(webClientMock.patch(anyString())).thenReturn(httpRequestMock);

        Job job = createJob(JOB_ID, PROCESS_INSTANCE_ID, "SCHEDULED");

        client.rescheduleJob(SERVICE_URL, job, newJobData);
        ArgumentCaptor<JsonObject> jsonCaptor = ArgumentCaptor.forClass(JsonObject.class);

        verify(client).sendPatchClientRequest(eq(webClientMock),
                eq(format(KogitoRuntimeCommonClient.RESCHEDULE_JOB_PATH, JOB_ID)),
                eq("RESCHEDULED JOB with id: " + job.getId()),
                jsonCaptor.capture());

        assertThat(jsonCaptor.getValue().getString("expirationTime")).isEqualTo("2023-08-27T04:35:54.631Z");
        assertThat(jsonCaptor.getValue().getString("retries")).isEqualTo("2");

        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        JsonObject jsonOject = new JsonObject(newJobData);
        verify(httpRequestMock).sendJson(eq(jsonOject), handlerCaptor.capture());
        verify(httpRequestMock).putHeader("Authorization", AUTHORIZED_TOKEN);
        checkResponseHandling(handlerCaptor.getValue());
    }

    @Test
    public void testWebClientToURLOptions() {
        String defaultHost = "localhost";
        int defaultPort = 8180;
        WebClientOptions webClientOptions = client.getWebClientToURLOptions("http://" + defaultHost + ":" + defaultPort);
        assertThat(webClientOptions.getDefaultHost()).isEqualTo(defaultHost);
        assertThat(webClientOptions.getDefaultPort()).isEqualTo(defaultPort);
    }

    @Test
    public void testWebClientToURLOptionsWithoutPort() {
        String dataIndexUrl = "http://service.com";
        WebClientOptions webClientOptions = client.getWebClientToURLOptions(dataIndexUrl);
        assertThat(webClientOptions.getDefaultPort()).isEqualTo(80);
        assertThat(webClientOptions.getDefaultHost()).isEqualTo("service.com");
        assertThat(webClientOptions.isSsl()).isFalse();
    }

    @Test
    public void testWebClientToURLOptionsWithoutPortSSL() {
        String dataIndexurl = "https://service.com";
        WebClientOptions webClientOptions = client.getWebClientToURLOptions(dataIndexurl);
        assertThat(webClientOptions.getDefaultPort()).isEqualTo(443);
        assertThat(webClientOptions.getDefaultHost()).isEqualTo("service.com");
        assertThat(webClientOptions.isSsl()).isTrue();
    }

    @Test
    public void testMalformedURL() {
        assertThat(client.getWebClientToURLOptions("malformedURL")).isNull();
    }

    @Test
    void testOverrideURL() {
        String host = "host.testcontainers.internal";
        client.setGatewayTargetUrl(Optional.of(host));
        WebClientOptions webClientOptions = client.getWebClientToURLOptions("http://service.com");
        assertThat(webClientOptions.getDefaultHost()).isEqualTo(host);
    }

    @Test
    public void testGetAuthHeader() {
        when(authTokenReader.readToken()).thenReturn(AUTHORIZED_TOKEN);

        String token = client.getAuthHeader();
        verify(authTokenReader, times(1)).readToken();
        assertThat(token).isEqualTo(AUTHORIZED_TOKEN);

        when(authTokenReader.readToken()).thenReturn(null);
        token = client.getAuthHeader();
        assertThat(token).isEqualTo("");
    }

    @Test
    void testUpdateProcessInstanceVariables_exception() throws Exception {

        ObjectMapper mapper = ObjectMapperFactory.get();
        JsonNode originalVariables = mapper.createObjectNode()
                .put("key", "value");

        ProcessInstance pI = createProcessInstance(PROCESS_INSTANCE_ID, ACTIVE);

        assertThatThrownBy(() -> client.updateProcessInstanceVariables("http://example.com", pI, mapper.writeValueAsString(originalVariables))).isInstanceOf(DataIndexServiceException.class)
                .hasMessageContaining("Failure to update the variables");
    }

    @Test
    void testUpdateProcessInstanceVariablesSuccess() throws Exception {
        ObjectMapper mapper = ObjectMapperFactory.get();
        JsonNode originalVariables = mapper.createObjectNode()
                .put("name", "jdoe")
                .put("age", 20)
                .put("adult", true);

        ProcessInstance pI = createProcessInstance(PROCESS_INSTANCE_ID, ACTIVE);

        CompletableFuture<String> result = client.updateProcessInstanceVariables("http://service.url", pI, mapper.writeValueAsString(originalVariables));

        assertThat(result).isNotNull();
        verify(processInstance).updateVariables(any());

        String updatedVariablesString = result.get();
        JsonNode updatedVariables = mapper.readTree(updatedVariablesString);
        assertThat(updatedVariables).isEqualTo(originalVariables);
    }

    private ProcessInstance createProcessInstance(String processInstanceId, int status) {
        return TestUtils.getProcessInstance("travels", processInstanceId, status, null, null);
    }

    private Job createJob(String jobId, String processInstanceId, String status) {
        return TestUtils.getJob(jobId, "travels", processInstanceId, null, null, status);
    }

    private AsyncResult createResponseMocks(HttpResponse response, boolean succeed, int statusCode) {
        AsyncResult asyncResultMock = mock(AsyncResult.class);
        when(asyncResultMock.succeeded()).thenReturn(succeed);
        when(asyncResultMock.result()).thenReturn(response);
        when(response.statusCode()).thenReturn(statusCode);
        return asyncResultMock;
    }

    protected void checkResponseHandling(Handler<AsyncResult<HttpResponse<Buffer>>> handler) {
        HttpResponse response = mock(HttpResponse.class);
        HttpResponse responseWithoutError = mock(HttpResponse.class);

        handler.handle(createResponseMocks(response, false, 404));
        verify(response).statusMessage();
        verify(response).body();
        verify(response, never()).bodyAsString();

        handler.handle(createResponseMocks(responseWithoutError, true, 200));
        verify(responseWithoutError, never()).statusMessage();
        verify(responseWithoutError, never()).body();
        verify(responseWithoutError).bodyAsString();
    }

    protected void setupIdentityMock() {
        when(authTokenReader.readToken()).thenReturn(AUTHORIZED_TOKEN);
        when(httpRequestMock.putHeader(eq("Authorization"), eq(AUTHORIZED_TOKEN))).thenReturn(httpRequestMock);
    }
}
