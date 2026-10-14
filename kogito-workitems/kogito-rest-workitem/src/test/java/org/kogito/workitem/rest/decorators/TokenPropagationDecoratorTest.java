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
package org.kogito.workitem.rest.decorators;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.drools.core.common.InternalKnowledgeRuntime;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.Application;
import org.kie.kogito.Config;
import org.kie.kogito.auth.AuthTokenProvider;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.process.ProcessConfig;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.mutiny.ext.web.client.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TokenPropagationDecoratorTest {

    private static final String CONFIGURED_TOKEN = "configured-token-12345";
    private static final String PROPAGATED_TOKEN = "propagated-token-67890";

    @Mock
    private KogitoWorkItem workItem;

    @Mock
    private KogitoNodeInstance nodeInstance;

    @Mock
    private HttpRequest<?> httpRequest;

    @Mock
    private KogitoProcessInstance processInstance;

    @Mock
    private org.jbpm.process.instance.ProcessInstance jbpmProcessInstance;

    @Mock
    private InternalKnowledgeRuntime knowledgeRuntime;

    @Mock
    private InternalProcessRuntime internalProcessRuntime;

    @Mock
    private KogitoProcessRuntime kogitoProcessRuntime;

    @Mock
    private Application application;

    @Mock
    private Config config;

    @Mock
    private ProcessConfig processConfig;

    @Mock
    private AuthTokenProvider authTokenProvider;

    private TokenPropagationDecorator decorator;
    private Map<String, Object> parameters;

    @BeforeEach
    void setUp() {
        decorator = new TokenPropagationDecorator();
        parameters = new HashMap<>();

        lenient().when(workItem.getNodeInstance()).thenReturn(nodeInstance);
        lenient().when(nodeInstance.getId()).thenReturn("test-node-123");
        lenient().when(workItem.getName()).thenReturn("TestTask");
        lenient().when(workItem.getProcessInstance()).thenReturn(processInstance);
        lenient().when(processInstance.getProcessId()).thenReturn("testProcess");

        // Setup the chain to access AuthTokenProvider
        lenient().when(jbpmProcessInstance.getKnowledgeRuntime()).thenReturn(knowledgeRuntime);
        lenient().when(knowledgeRuntime.getProcessRuntime()).thenReturn(internalProcessRuntime);
        lenient().when(internalProcessRuntime.getKogitoProcessRuntime()).thenReturn(kogitoProcessRuntime);
        lenient().when(kogitoProcessRuntime.getApplication()).thenReturn(application);
        lenient().when(application.config()).thenReturn(config);
        lenient().when(config.get(ProcessConfig.class)).thenReturn(processConfig);
        lenient().when(processConfig.authTokenProvider()).thenReturn(authTokenProvider);
    }

    @Test
    void testGetBearerToken_ConfiguredStrategy_WithToken() {
        System.setProperty("kogito.processes.testProcess.TestTask.access_token", CONFIGURED_TOKEN);
        try {
            parameters.put(TokenPropagationDecorator.ACCESS_TOKEN_ACQUISITION_STRATEGY, "configured");

            Optional<String> result = decorator.getBearerToken(workItem, parameters);

            assertThat(result).isPresent().contains(CONFIGURED_TOKEN);
        } finally {
            System.clearProperty("kogito.processes.testProcess.TestTask.access_token");
        }
    }

    @Test
    void testGetBearerToken_ConfiguredStrategy_WithoutToken() {
        parameters.put(TokenPropagationDecorator.ACCESS_TOKEN_ACQUISITION_STRATEGY, "configured");

        Optional<String> result = decorator.getBearerToken(workItem, parameters);

        assertThat(result).isEmpty();
    }

    @Test
    void testGetBearerToken_PropagateStrategy_WithPropagatedTokenFromHeaders() {
        parameters.put(TokenPropagationDecorator.ACCESS_TOKEN_ACQUISITION_STRATEGY, "propagated");
        when(workItem.getProcessInstance()).thenReturn((KogitoProcessInstance) jbpmProcessInstance);
        when(authTokenProvider.getAuthToken()).thenReturn(Optional.of(PROPAGATED_TOKEN));

        Optional<String> result = decorator.getBearerToken(workItem, parameters);

        assertThat(result).isPresent().contains(PROPAGATED_TOKEN);
    }

    @Test
    void testGetBearerToken_PropagateStrategy_NoTokensAvailable() {
        parameters.put(TokenPropagationDecorator.ACCESS_TOKEN_ACQUISITION_STRATEGY, "propagated");

        assertThatThrownBy(() -> decorator.getBearerToken(workItem, parameters))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void testGetBearerToken_PropagateStrategy_NoProcessInstance() {
        parameters.put(TokenPropagationDecorator.ACCESS_TOKEN_ACQUISITION_STRATEGY, "propagated");
        when(workItem.getProcessInstance()).thenReturn(null);

        assertThatThrownBy(() -> decorator.getBearerToken(workItem, parameters))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGetBearerToken_PropagateStrategy_EmptyToken() {
        parameters.put(TokenPropagationDecorator.ACCESS_TOKEN_ACQUISITION_STRATEGY, "propagated");
        when(workItem.getProcessInstance()).thenReturn((KogitoProcessInstance) jbpmProcessInstance);
        when(authTokenProvider.getAuthToken()).thenReturn(Optional.empty());

        Optional<String> result = decorator.getBearerToken(workItem, parameters);

        assertThat(result).isEmpty();
    }

    @Test
    void testGetBearerToken_PropagateStrategy_AuthTokenProviderNotAvailable() {
        parameters.put(TokenPropagationDecorator.ACCESS_TOKEN_ACQUISITION_STRATEGY, "propagated");
        when(workItem.getProcessInstance()).thenReturn((KogitoProcessInstance) jbpmProcessInstance);
        when(processConfig.authTokenProvider()).thenReturn(null);

        assertThatThrownBy(() -> decorator.getBearerToken(workItem, parameters))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGetBearerToken_NoneStrategy() {
        parameters.put(TokenPropagationDecorator.ACCESS_TOKEN_ACQUISITION_STRATEGY, "none");

        Optional<String> result = decorator.getBearerToken(workItem, parameters);

        assertThat(result).isEmpty();
    }

    @Test
    void testGetBearerToken_UnknownStrategy_DefaultsToNone() {
        parameters.put(TokenPropagationDecorator.ACCESS_TOKEN_ACQUISITION_STRATEGY, "unknown-strategy");

        Optional<String> result = decorator.getBearerToken(workItem, parameters);

        assertThat(result).isEmpty();
    }

    @Test
    void testGetBearerToken_NoStrategySpecified_DefaultsToNone() {
        Optional<String> result = decorator.getBearerToken(workItem, parameters);

        assertThat(result).isEmpty();
    }

    @Test
    void testDecorate_WithToken_AddsAuthenticationHeader() {
        System.setProperty("kogito.processes.testProcess.TestTask.access_token", CONFIGURED_TOKEN);
        try {
            parameters.put(TokenPropagationDecorator.ACCESS_TOKEN_ACQUISITION_STRATEGY, "configured");

            decorator.decorate(workItem, parameters, httpRequest);

            verify(httpRequest).bearerTokenAuthentication(CONFIGURED_TOKEN);
        } finally {
            System.clearProperty("kogito.processes.testProcess.TestTask.access_token");
        }
    }

    @Test
    void testDecorate_WithoutToken_DoesNotAddAuthenticationHeader() {
        parameters.put(TokenPropagationDecorator.ACCESS_TOKEN_ACQUISITION_STRATEGY, "none");

        decorator.decorate(workItem, parameters, httpRequest);

        verify(httpRequest, never()).bearerTokenAuthentication(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void testDecorate_PropagateStrategy_UsesPropagatedTokenFromHeaders() {
        parameters.put(TokenPropagationDecorator.ACCESS_TOKEN_ACQUISITION_STRATEGY, "propagated");
        when(workItem.getProcessInstance()).thenReturn((KogitoProcessInstance) jbpmProcessInstance);
        when(authTokenProvider.getAuthToken()).thenReturn(Optional.of(PROPAGATED_TOKEN));

        decorator.decorate(workItem, parameters, httpRequest);

        verify(httpRequest).bearerTokenAuthentication(PROPAGATED_TOKEN);
    }

    @Test
    void testAccessTokenAcquisitionStrategy_FromName() {
        assertThat(AccessTokenAcquisitionStrategy.fromName("none")).isEqualTo(AccessTokenAcquisitionStrategy.NONE);
        assertThat(AccessTokenAcquisitionStrategy.fromName("configured")).isEqualTo(AccessTokenAcquisitionStrategy.CONFIGURED);
        assertThat(AccessTokenAcquisitionStrategy.fromName("propagated")).isEqualTo(AccessTokenAcquisitionStrategy.PROPAGATED);
        assertThat(AccessTokenAcquisitionStrategy.fromName("invalid")).isEqualTo(AccessTokenAcquisitionStrategy.NONE);
        assertThat(AccessTokenAcquisitionStrategy.fromName(null)).isEqualTo(AccessTokenAcquisitionStrategy.NONE);
    }
}
