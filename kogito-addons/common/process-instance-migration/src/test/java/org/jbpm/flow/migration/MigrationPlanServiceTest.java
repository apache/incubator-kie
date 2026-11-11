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

import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.core.datatype.impl.type.ObjectDataType;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.jbpm.workflow.core.impl.WorkflowProcessImpl;
import org.jbpm.workflow.instance.impl.ExtendedNodeInstanceImpl;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.kie.kogito.internal.process.event.KogitoProcessEventSupport;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.process.Processes;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jbpm.ruleflow.core.WorkflowElementIdentifierFactory.fromExternalFormat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    public void testMigrationProcessInstanceRenameOverwritesTarget() {
        MigrationPlanService service = new MigrationPlanService();

        RuleFlowProcessInstance processImpl = newProcessInstanceToMigrate(new DummyProcess("process_B", "2"));
        VariableScopeInstance variableScope = (VariableScopeInstance) processImpl.getContextInstance(VariableScope.VARIABLE_SCOPE);
        variableScope.internalSetVariable("custName", "John");
        variableScope.internalSetVariable("customerName", "Jane");

        service.migrateProcessElement(processes, processImpl);

        assertThat(variableScope.getVariables())
                .containsEntry("customerName", "John")
                .containsEntry("custName", null);
    }

    @Test
    public void testMigrationProcessInstanceVariablesFireEvents() {
        MigrationPlanService service = new MigrationPlanService();
        KogitoProcessEventSupport events = Mockito.mock(KogitoProcessEventSupport.class);
        KogitoProcessRuntime runtime = Mockito.mock(KogitoProcessRuntime.class);
        when(runtime.getProcessEventSupport()).thenReturn(events);

        RuleFlowProcessInstance processImpl = newProcessInstanceToMigrate(new DummyProcess("process_B", "2"));
        VariableScopeInstance variableScope = (VariableScopeInstance) processImpl.getContextInstance(VariableScope.VARIABLE_SCOPE);
        variableScope.internalSetVariable("custName", "John");
        variableScope.internalSetVariable("approvedBy", "manager");
        variableScope.internalSetVariable("tempFlag", Boolean.TRUE);

        service.migrateProcessElement(runtime, processes, processImpl);

        verifyVariableChanged(events, processImpl, "customerName", null, "John");
        verifyVariableChanged(events, processImpl, "custName", "John", null);
        verifyVariableChanged(events, processImpl, "riskLevel", null, "LOW");
        verifyVariableChanged(events, processImpl, "tempFlag", Boolean.TRUE, null);
        verify(events, never()).fireAfterVariableChanged(eq("approvedBy"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testMigrationProcessInstanceVariableTypeMismatch() {
        MigrationPlanService service = new MigrationPlanService();

        RuleFlowProcess targetProcess = new RuleFlowProcess();
        targetProcess.setId("process_B");
        targetProcess.setVersion("2");
        Variable riskLevel = new Variable();
        riskLevel.setName("riskLevel");
        riskLevel.setType(new ObjectDataType(Integer.class));
        targetProcess.getVariableScope().addVariable(riskLevel);

        RuleFlowProcessInstance processImpl = newProcessInstanceToMigrate(targetProcess);

        assertThatThrownBy(() -> service.migrateProcessElement(processes, processImpl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("riskLevel")
                .hasMessageContaining(Integer.class.getName());
    }

    @Test
    public void testMigrationProcessInstanceWithoutVariableScope() {
        MigrationPlanService service = new MigrationPlanService();

        // definition without a variable scope
        WorkflowProcessImpl targetProcess = new WorkflowProcessImpl();
        targetProcess.setId("process_B");
        targetProcess.setVersion("2");

        RuleFlowProcessInstance processImpl = new RuleFlowProcessInstance();
        processImpl.setInternalProcess(targetProcess);
        processImpl.setProcessId("process_E");
        processImpl.setProcessVersion("1");

        service.migrateProcessElement(processes, processImpl);

        assertThat(processImpl.getContextInstance(VariableScope.VARIABLE_SCOPE)).isNull();
        assertThat(processImpl)
                .hasFieldOrPropertyWithValue("processId", "process_B")
                .hasFieldOrPropertyWithValue("processVersion", "2");
    }

    private static RuleFlowProcessInstance newProcessInstanceToMigrate(org.kie.api.definition.process.Process targetProcess) {
        RuleFlowProcessInstance processImpl = new RuleFlowProcessInstance();
        processImpl.setInternalProcess(targetProcess);
        processImpl.setProcessId("process_E");
        processImpl.setProcessVersion("1");
        processImpl.setContextInstance(VariableScope.VARIABLE_SCOPE, new VariableScopeInstance());
        return processImpl;
    }

    private static void verifyVariableChanged(KogitoProcessEventSupport events, RuleFlowProcessInstance processImpl, String name, Object oldValue, Object newValue) {
        verify(events).fireBeforeVariableChanged(eq(name), eq(name), eq(oldValue), eq(newValue), any(), same(processImpl), isNull(), any());
        verify(events).fireAfterVariableChanged(eq(name), eq(name), eq(oldValue), eq(newValue), any(), same(processImpl), isNull(), any());
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
