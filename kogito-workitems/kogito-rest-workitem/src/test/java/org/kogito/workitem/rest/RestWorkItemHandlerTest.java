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
package org.kogito.workitem.rest;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.jbpm.process.core.Process;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.core.datatype.impl.type.ObjectDataType;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.workflow.core.impl.IOSpecification;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.jbpm.workflow.instance.node.WorkItemNodeInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.jackson.utils.ObjectMapperFactory;
import org.kie.kogito.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.impl.KogitoWorkItemImpl;
import org.kogito.workitem.rest.bodybuilders.DefaultWorkItemHandlerBodyBuilder;
import org.kogito.workitem.rest.resulthandlers.DefaultRestWorkItemHandlerResult;
import org.kogito.workitem.rest.resulthandlers.RestWorkItemHandlerResult;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.vertx.core.http.HttpMethod;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kogito.workitem.rest.RestWorkItemHandler.BODY_BUILDER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RestWorkItemHandlerTest {

    private static final String DEFAULT_WORKFLOW_VAR = "workflow";

    private Map<String, Object> parameters;

    @Mock
    private KogitoWorkItemManager manager;

    @Mock
    private HttpResponse<Buffer> response;

    @Mock
    private HttpRequest<Buffer> request;

    @Mock
    private HttpRequest<Buffer> sslRequest;

    @Mock
    private HttpResponse<Buffer> sslResponse;

    private KogitoWorkItemImpl workItem;

    @Mock
    private VariableScope variableScope;

    @Mock
    private WorkItemNodeInstance nodeInstance;

    @Mock
    private IOSpecification ioSpecification;

    @Mock
    private WorkItemNode node;

    @Captor
    private ArgumentCaptor<Map<String, Object>> bodyCaptor;

    private ObjectNode workflowData;

    private RestWorkItemHandler handler;

    @BeforeEach
    public void init() {
        WebClient webClient = mock(WebClient.class);
        WebClient sslClient = mock(WebClient.class);
        ObjectMapper mapper = new ObjectMapper();
        when(webClient.request(any(HttpMethod.class), eq(8080), eq("localhost"), anyString()))
                .thenReturn(request);
        when(sslClient.request(any(HttpMethod.class), eq(443), eq("localhost"), anyString())).thenReturn(sslRequest);
        when(sslRequest.sendAndAwait()).thenReturn(sslResponse);
        when(sslResponse.bodyAsJson(ObjectNode.class)).thenReturn(ObjectMapperFactory.get().createObjectNode().put("num", 2));
        when(sslResponse.statusCode()).thenReturn(200);

        when(request.sendJsonAndAwait(any())).thenReturn(response);
        when(request.sendAndAwait()).thenReturn(response);
        when(response.bodyAsJson(ObjectNode.class)).thenReturn(ObjectMapperFactory.get().createObjectNode().put("num", 1));
        when(response.statusCode()).thenReturn(200);

        workItem = new KogitoWorkItemImpl();
        workItem.setId("2");
        parameters = workItem.getParameters();
        parameters.put(RestWorkItemHandler.PROTOCOL, "http");
        parameters.put(RestWorkItemHandler.HOST, "localhost");
        parameters.put(RestWorkItemHandler.PORT, 8080);
        parameters.put(RestWorkItemHandler.URL, "/results/sum");
        parameters.put(RestWorkItemHandler.CONTENT_DATA, workflowData);

        Process process = mock(Process.class);
        WorkflowProcessInstance processInstance = mock(WorkflowProcessInstance.class);
        workItem.setProcessInstance(processInstance);

        workflowData = mapper.createObjectNode().put("id", 26).put("name", "pepe");

        when(processInstance.getProcess()).thenReturn(process);
        when(processInstance.getVariables()).thenReturn(Collections.singletonMap(DEFAULT_WORKFLOW_VAR, workflowData));

        Variable variable = new Variable();
        variable.setName(DEFAULT_WORKFLOW_VAR);
        variable.setType(new ObjectDataType(ObjectNode.class.getName()));
        variable.setValue(workflowData);

        when(process.getDefaultContext(VariableScope.VARIABLE_SCOPE)).thenReturn(variableScope);
        when(variableScope.findVariable(DEFAULT_WORKFLOW_VAR)).thenReturn(variable);

        when(node.getIoSpecification()).thenReturn(ioSpecification);
        workItem.setNodeInstance(nodeInstance);
        when(nodeInstance.getNode()).thenReturn(node);
        when(nodeInstance.getProcessInstance()).thenReturn(processInstance);
        when(node.resolveContext(VariableScope.VARIABLE_SCOPE, DEFAULT_WORKFLOW_VAR)).thenReturn(variableScope);

        Map<String, String> outputMapping = Collections.singletonMap(RestWorkItemHandler.RESULT, DEFAULT_WORKFLOW_VAR);
        when(ioSpecification.getOutputMappingBySources()).thenReturn(outputMapping);

        handler = new RestWorkItemHandler(webClient, sslClient);
    }

    @Test
    public void testEmptyInputModel() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode objectNode = objectMapper.createObjectNode().put("id", 26).put("name", "pepe");
        RestWorkItemHandlerResult resultHandler = new DefaultRestWorkItemHandlerResult();
        HttpResponse<Buffer> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.bodyAsJson(ObjectNode.class)).thenReturn(objectNode);
        assertThat(resultHandler.apply(response, ObjectNode.class)).isSameAs(objectNode);
    }

    @Test
    public void testGetRestTaskHandler() {
        parameters.put("id", 26);
        parameters.put("name", "kogito is whitespace friendly");
        parameters.put(RestWorkItemHandler.URL, "http://localhost:8080/results/{id}?name={name}");
        parameters.put(RestWorkItemHandler.METHOD, "GET");
        parameters.put(RestWorkItemHandler.CONTENT_DATA, workflowData);

        WorkItemTransition transition = handler.startingTransition(parameters);
        workItem.setPhaseStatus("Activated");

        assertResult(handler.activateWorkItemHandler(manager, handler, workItem, transition));
    }

    @Test
    public void testEmptyGet() {
        parameters.put("id", 25);
        parameters.put(RestWorkItemHandler.URL, "http://localhost:8080/results/{id}");
        parameters.put(RestWorkItemHandler.METHOD, "GET");

        when(ioSpecification.getOutputMappingBySources()).thenReturn(Collections.singletonMap(RestWorkItemHandler.RESULT, DEFAULT_WORKFLOW_VAR));

        Optional<WorkItemTransition> transition = handler.transitionToPhase(manager, workItem, handler.startingTransition(parameters));

        assertThat(transition).isPresent();
        assertThat(transition.get().data()).hasSize(1);
    }

    @Test
    public void testParametersPostRestTaskHandler() {
        parameters.put("id", 26);
        parameters.put("name", "pepe");
        parameters.put(RestWorkItemHandler.METHOD, "POST");
        parameters.put(BODY_BUILDER, new DefaultWorkItemHandlerBodyBuilder());

        assertResult(handler.transitionToPhase(manager, workItem, handler.startingTransition(parameters)));

        verify(request).sendJsonAndAwait(bodyCaptor.capture());
        Map<String, Object> bodyMap = bodyCaptor.getValue();
        assertThat(bodyMap).containsEntry("id", 26)
                .containsEntry("name", "pepe");

    }

    @Test
    public void testContentDataPostRestTaskHandler() {
        parameters.put(RestWorkItemHandler.METHOD, "POST");
        parameters.put(BODY_BUILDER, new DefaultWorkItemHandlerBodyBuilder());
        parameters.put(RestWorkItemHandler.CONTENT_DATA, workflowData);

        assertResult(handler.transitionToPhase(manager, workItem, handler.startingTransition(parameters)));

        ArgumentCaptor<ObjectNode> bodyCaptor = ArgumentCaptor.forClass(ObjectNode.class);
        verify(request).sendJsonAndAwait(bodyCaptor.capture());
        ObjectNode bodyMap = bodyCaptor.getValue();
        assertThat(bodyMap.get("id").asInt()).isEqualTo(26);
        assertThat(bodyMap.get("name").asText()).isEqualTo("pepe");

    }

    @Test
    public void testParametersPostWithCustomParamWithDefaultBuilder() {
        testParametersPostWithCustomParam(null);
    }

    @Test
    public void testParametersPostWithCustomParamWithClassBuilder() {
        testParametersPostWithCustomParam(DefaultWorkItemHandlerBodyBuilder.class.getName());
    }

    private void testParametersPostWithCustomParam(String bodyBuilderClass) {
        final VariableScopeInstance contextInstance = mock(VariableScopeInstance.class);
        when(nodeInstance.resolveContextInstance(VariableScope.VARIABLE_SCOPE, DEFAULT_WORKFLOW_VAR)).thenReturn(contextInstance);
        when(contextInstance.getVariable(DEFAULT_WORKFLOW_VAR)).thenReturn(workflowData);

        parameters.put(RestWorkItemHandler.METHOD, "POST");
        parameters.put("name", "tiago");
        parameters.put("id", 123);
        //test expression evaluation in the work item parameter
        final String customParameter = "custom parameter";
        parameters.put(customParameter, workflowData);
        Optional.ofNullable(bodyBuilderClass).ifPresent(builder -> parameters.put(BODY_BUILDER, builder));

        assertResult(handler.transitionToPhase(manager, workItem, handler.startingTransition(parameters)));

        verify(request).sendJsonAndAwait(bodyCaptor.capture());

        Map<String, Object> bodyMap = bodyCaptor.getValue();
        assertThat(bodyMap).containsEntry("id", 123)
                .containsEntry("name", "tiago")
                //assert the evaluated expression with a process variable
                .containsEntry(customParameter, workflowData);

    }

    @Test
    public void testContentPostRestTaskHandler() {
        parameters.put(RestWorkItemHandler.METHOD, "POST");
        parameters.put(RestWorkItemHandler.CONTENT_DATA, workflowData);

        assertResult(handler.transitionToPhase(manager, workItem, handler.startingTransition(parameters)));

        ArgumentCaptor<ObjectNode> bodyCaptor = ArgumentCaptor.forClass(ObjectNode.class);
        verify(request).sendJsonAndAwait(bodyCaptor.capture());
        ObjectNode bodyMap = bodyCaptor.getValue();
        assertThat(bodyMap.get("id").asInt()).isEqualTo(26);
        assertThat(bodyMap.get("name").asText()).isEqualTo("pepe");
    }

    @Test
    public void testSSLRequestProtocol() {
        parameters.remove(RestWorkItemHandler.PORT);
        parameters.put(RestWorkItemHandler.PROTOCOL, "https");
        assertSSLResult(handler.transitionToPhase(manager, workItem, handler.startingTransition(parameters)));
    }

    @Test
    public void testSSLRequestPort() {
        parameters.remove(RestWorkItemHandler.PROTOCOL);
        parameters.put(RestWorkItemHandler.PORT, 443);
        assertSSLResult(handler.transitionToPhase(manager, workItem, handler.startingTransition(parameters)));
    }

    public void assertResult(Optional<WorkItemTransition> transition) {
        Map<String, Object> results = transition.get().data();
        assertThat(results).hasSize(1).containsKey(RestWorkItemHandler.RESULT);
        Object result = results.get(RestWorkItemHandler.RESULT);
        assertThat(result).isInstanceOf(ObjectNode.class);
        assertThat(((ObjectNode) result).get("num").asInt()).isOne();
    }

    public void assertSSLResult(Optional<WorkItemTransition> transition) {
        Map<String, Object> results = transition.get().data();
        assertThat(results).hasSize(1).containsKey(RestWorkItemHandler.RESULT);
        Object result = results.get(RestWorkItemHandler.RESULT);
        assertThat(result).isInstanceOf(ObjectNode.class);
        assertThat(((ObjectNode) result).get("num").asInt()).isEqualTo(2);
    }
}
