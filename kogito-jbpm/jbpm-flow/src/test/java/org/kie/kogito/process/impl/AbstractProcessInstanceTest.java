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
package org.kie.kogito.process.impl;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.ProcessInstanceManager;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.instance.NodeInstance;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.definition.process.Process;
import org.kie.kogito.Model;
import org.kie.kogito.correlation.CorrelationService;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.process.MutableProcessInstances;
import org.kie.kogito.uow.UnitOfWork;
import org.kie.kogito.uow.UnitOfWorkManager;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AbstractProcessInstanceTest {

    private static final String NODE_ID = "my_node_id";

    @Mock
    private ProcessInstanceManager pim;

    @Mock
    private WorkflowProcessInstanceImpl wpi;

    @Mock
    private UnitOfWork unitOfWork;

    @Mock
    private MutableProcessInstances instances;

    private AbstractProcessInstance<TestModel> processInstance;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);

        AbstractProcess<TestModel> process = mock(AbstractProcess.class);
        Process piProcess = mock(Process.class);
        when(process.process()).thenReturn(piProcess);
        when(process.instances()).thenReturn(instances);
        when(process.get()).thenReturn(piProcess);
        InternalProcessRuntime pr = mock(InternalProcessRuntime.class);
        when(pr.createProcessInstance(any(), any(), any())).thenReturn(wpi);
        when(pr.getProcessInstanceManager()).thenReturn(pim);
        UnitOfWorkManager unitOfWorkManager = mock(UnitOfWorkManager.class);
        when(pr.getUnitOfWorkManager()).thenReturn(unitOfWorkManager);
        KogitoProcessRuntime kogitoProcessRuntime = mock(KogitoProcessRuntime.class);
        when(pr.getKogitoProcessRuntime()).thenReturn(kogitoProcessRuntime);
        when(unitOfWorkManager.currentUnitOfWork()).thenReturn(unitOfWork);
        when(wpi.getStringId()).thenReturn(UUID.randomUUID().toString());
        CorrelationService correlationService = mock(CorrelationService.class);
        when(process.correlations()).thenReturn(correlationService);
        when(correlationService.findByCorrelatedId(any())).thenReturn(Optional.empty());
        processInstance = new TestProcessInstance(process, new TestModel(), pr);
    }

    @Test
    public void testCreateProcessInstance() {

        assertThat(processInstance.status()).isEqualTo(KogitoProcessInstance.STATE_PENDING);
        assertThat(processInstance.id()).isNotNull();
        assertThat(processInstance.businessKey()).isNull();

        verify(pim, never()).addProcessInstance(any());
    }

    @Test
    public void shouldTriggerNodeWhenStartFrom() {
        NodeInstance nodeInstance = givenExistingNode(NODE_ID);

        processInstance.startFrom(NODE_ID);

        verify(nodeInstance).trigger(null, Node.CONNECTION_DEFAULT_TYPE);
        verify(unitOfWork, times(0)).intercept(any());
    }

    @Test
    public void shouldTriggerNodeWhenTrigger() {
        NodeInstance nodeInstance = givenExistingNode(NODE_ID);

        processInstance.triggerNode(NODE_ID);

        verify(nodeInstance).trigger(null, Node.CONNECTION_DEFAULT_TYPE);
        verify(unitOfWork, times(0)).intercept(any());
    }

    private NodeInstance givenExistingNode(String nodeId) {
        RuleFlowProcess process = mock(RuleFlowProcess.class);
        when(wpi.getProcess()).thenReturn(process);
        org.kie.api.definition.process.Node node = mock(org.kie.api.definition.process.Node.class);
        when(node.getUniqueId()).thenReturn(nodeId);
        when(process.getNodesRecursively()).thenReturn(Arrays.asList(node));

        NodeInstance nodeInstance = mock(NodeInstance.class);
        when(wpi.getNodeByPredicate(any(), any())).thenReturn(nodeInstance);
        return nodeInstance;
    }

    @Test
    public void testVersion() {
        processInstance.setVersion(10L);
        assertThat(processInstance.version()).isEqualTo(10l);
    }

    static class TestProcessInstance extends AbstractProcessInstance<TestModel> {

        public TestProcessInstance(AbstractProcess<TestModel> process, TestModel variables, InternalProcessRuntime rt) {
            super(process, variables, rt);
        }
    }

    static class TestModel implements Model {

        @Override
        public void update(Map<String, Object> params) {
            fromMap(params);
        }

        @Override
        public Map<String, Object> toMap() {
            return null;
        }

        @Override
        public TestModel fromMap(Map<String, Object> params) {
            return this;
        }
    }
}
