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
package org.jbpm.flow.migration;

import java.util.Collections;

import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.jbpm.workflow.instance.impl.ExtendedNodeInstanceImpl;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.kie.kogito.process.Processes;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jbpm.ruleflow.core.WorkflowElementIdentifierFactory.fromExternalFormat;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
public class MigrationPlanServiceTest {

    Processes processes;
    org.kie.kogito.process.Process processB;

    @BeforeAll
    public void init() {
        processes = Mockito.mock(Processes.class);
        when(processes.processIds()).thenReturn(Collections.singletonList("process_B"));

        processB = Mockito.mock(org.kie.kogito.process.Process.class);
        when(processes.processById("process_B")).thenReturn(processB);
        when(processB.id()).thenReturn("process_B");
        when(processB.version()).thenReturn("2");
    }

    @Test
    public void testMigrationProcessInstanceSameProcessDefinition() {
        MigrationPlanService service = new MigrationPlanService();

        WorkflowProcessInstanceImpl processImpl = new RuleFlowProcessInstance();
        processImpl.setInternalProcess(new DummyProcess("process_A", "1"));
        processImpl.setProcessId("process_A");
        processImpl.setProcessVersion("1");
        service.migrateProcessElement(processes, processImpl);

        assertThat(processImpl)
                .hasFieldOrPropertyWithValue("processId", "process_A")
                .hasFieldOrPropertyWithValue("processVersion", "1");

    }

    @Test
    public void testMigrationProcessInstance() {
        MigrationPlanService service = new MigrationPlanService();

        WorkflowProcessInstanceImpl processImpl = new RuleFlowProcessInstance();
        processImpl.setInternalProcess(new DummyProcess("process_B", "2"));
        processImpl.setProcessId("process_A");
        processImpl.setProcessVersion("1");
        service.migrateProcessElement(processes, processImpl);

        assertThat(processImpl)
                .hasFieldOrPropertyWithValue("processId", "process_B")
                .hasFieldOrPropertyWithValue("processVersion", "2");

    }

    @Test
    public void testMigrationProcessInstanceNotMatchingVersion() {
        MigrationPlanService service = new MigrationPlanService();

        WorkflowProcessInstanceImpl processImpl = new RuleFlowProcessInstance();
        processImpl.setProcess(new DummyProcess("process_D", "1"));
        service.migrateProcessElement(processes, processImpl);

        assertThat(processImpl)
                .hasFieldOrPropertyWithValue("processId", "process_D")
                .hasFieldOrPropertyWithValue("processVersion", "1");

    }

    @Test
    public void testMigrationProcessNonExisting() {
        MigrationPlanService service = new MigrationPlanService();
        WorkflowProcessInstanceImpl processImpl = new RuleFlowProcessInstance();
        processImpl.setProcess(new DummyProcess("process_C", "1"));
        service.migrateProcessElement(processes, processImpl);

        assertThat(processImpl)
                .hasFieldOrPropertyWithValue("processId", "process_C")
                .hasFieldOrPropertyWithValue("processVersion", "1");
    }

    @Test
    public void testMigrationProcessNotRightVersion() {
        MigrationPlanService service = new MigrationPlanService();
        WorkflowProcessInstanceImpl processImpl = new RuleFlowProcessInstance();
        processImpl.setProcess(new DummyProcess("process_A", "3"));
        service.migrateProcessElement(processes, processImpl);

        assertThat(processImpl)
                .hasFieldOrPropertyWithValue("processId", "process_A")
                .hasFieldOrPropertyWithValue("processVersion", "3");
    }

    @Test
    public void testMigrationProcessInstanceVariables() {
        MigrationPlanService service = new MigrationPlanService();

        RuleFlowProcessInstance processImpl = new RuleFlowProcessInstance();
        processImpl.setInternalProcess(new DummyProcess("process_B", "2"));
        processImpl.setProcessId("process_E");
        processImpl.setProcessVersion("1");
        VariableScopeInstance variableScope = new VariableScopeInstance();
        processImpl.setContextInstance(VariableScope.VARIABLE_SCOPE, variableScope);
        variableScope.internalSetVariable("custName", "John");
        variableScope.internalSetVariable("approvedBy", "manager");
        variableScope.internalSetVariable("tempFlag", Boolean.TRUE);

        service.migrateProcessElement(processes, processImpl);

        assertThat(processImpl)
                .hasFieldOrPropertyWithValue("processId", "process_B")
                .hasFieldOrPropertyWithValue("processVersion", "2");
        assertThat(variableScope.getVariables())
                .containsEntry("customerName", "John")
                .containsEntry("custName", null)
                .containsEntry("riskLevel", "LOW")
                .containsEntry("approvedBy", "manager")
                .containsEntry("tempFlag", null);
    }

    @Test
    public void testMigrationProcessInstanceRenameMissingVariable() {
        MigrationPlanService service = new MigrationPlanService();

        RuleFlowProcessInstance processImpl = new RuleFlowProcessInstance();
        processImpl.setInternalProcess(new DummyProcess("process_B", "2"));
        processImpl.setProcessId("process_E");
        processImpl.setProcessVersion("1");
        VariableScopeInstance variableScope = new VariableScopeInstance();
        processImpl.setContextInstance(VariableScope.VARIABLE_SCOPE, variableScope);
        variableScope.internalSetVariable("customerName", "Jane");

        service.migrateProcessElement(processes, processImpl);

        assertThat(variableScope.getVariables())
                .containsEntry("customerName", "Jane")
                .doesNotContainKey("custName");
    }

    @Test
    public void testMigrationNode() {
        MigrationPlanService service = new MigrationPlanService();
        WorkflowProcessInstanceImpl processImpl = new RuleFlowProcessInstance();
        processImpl.setInternalProcess(new DummyProcess("process_B", "2"));
        processImpl.setProcessId("process_A");
        processImpl.setProcessVersion("1");
        ExtendedNodeInstanceImpl nodeInstanceImpl = new ExtendedNodeInstanceImpl() {
        };
        nodeInstanceImpl.setProcessInstance(processImpl);
        nodeInstanceImpl.setNodeId(fromExternalFormat("node_1"));
        service.migrateNodeElement(processes, nodeInstanceImpl);

        assertThat(nodeInstanceImpl)
                .hasFieldOrPropertyWithValue("nodeId", fromExternalFormat("node_2"));
    }

    @Test
    public void testMigrationNodeNextItem() {
        MigrationPlanService service = new MigrationPlanService();
        WorkflowProcessInstanceImpl processImpl = new RuleFlowProcessInstance();
        processImpl.setInternalProcess(new DummyProcess("process_B", "2"));
        processImpl.setProcessId("process_A");
        processImpl.setProcessVersion("1");
        ExtendedNodeInstanceImpl nodeInstanceImpl = new ExtendedNodeInstanceImpl() {
        };
        nodeInstanceImpl.setProcessInstance(processImpl);
        nodeInstanceImpl.setNodeId(fromExternalFormat("node_2"));
        service.migrateNodeElement(processes, nodeInstanceImpl);

        assertThat(nodeInstanceImpl)
                .hasFieldOrPropertyWithValue("nodeId", fromExternalFormat("node_3"));
    }

    @Test
    public void testMigrationNodeNonExistent() {
        MigrationPlanService service = new MigrationPlanService();
        WorkflowProcessInstanceImpl processImpl = new RuleFlowProcessInstance();
        processImpl.setProcess(new DummyProcess("process_A", "1"));
        ExtendedNodeInstanceImpl nodeInstanceImpl = new ExtendedNodeInstanceImpl() {
        };
        nodeInstanceImpl.setProcessInstance(processImpl);
        nodeInstanceImpl.setNodeId(fromExternalFormat("node_3"));
        service.migrateNodeElement(processes, nodeInstanceImpl);

        assertThat(nodeInstanceImpl)
                .hasFieldOrPropertyWithValue("nodeId", fromExternalFormat("node_3"));
    }

    @Test
    public void testMigrationNodeNotMigratedWrongProcess() {
        MigrationPlanService service = new MigrationPlanService();
        WorkflowProcessInstanceImpl processImpl = new RuleFlowProcessInstance();
        processImpl.setProcess(new DummyProcess("process_C", "1"));
        ExtendedNodeInstanceImpl nodeInstanceImpl = new ExtendedNodeInstanceImpl() {
        };
        nodeInstanceImpl.setProcessInstance(processImpl);
        nodeInstanceImpl.setNodeId(fromExternalFormat("node_3"));
        service.migrateNodeElement(processes, nodeInstanceImpl);

        assertThat(nodeInstanceImpl)
                .hasFieldOrPropertyWithValue("nodeId", fromExternalFormat("node_3"));
    }
}
