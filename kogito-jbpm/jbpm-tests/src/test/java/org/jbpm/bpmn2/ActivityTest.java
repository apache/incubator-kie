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
package org.jbpm.bpmn2;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.drools.compiler.rule.builder.PackageBuildContext;
import org.jbpm.bpmn2.activity.ScriptTaskFEELModel;
import org.jbpm.bpmn2.activity.ScriptTaskFEELProcess;
import org.jbpm.bpmn2.activity.ScriptTaskModel;
import org.jbpm.bpmn2.activity.ScriptTaskProcess;
import org.jbpm.bpmn2.activity.ScriptTaskWithIOModel;
import org.jbpm.bpmn2.activity.ScriptTaskWithIOProcess;
import org.jbpm.bpmn2.activity.UserTaskWithBooleanOutputModel;
import org.jbpm.bpmn2.activity.UserTaskWithBooleanOutputProcess;
import org.jbpm.bpmn2.activity.UserTaskWithDataStoreModel;
import org.jbpm.bpmn2.activity.UserTaskWithDataStoreProcess;
import org.jbpm.bpmn2.activity.UserTaskWithIOexpressionModel;
import org.jbpm.bpmn2.activity.UserTaskWithIOexpressionProcess;
import org.jbpm.bpmn2.activity.UserTaskWithParametrizedInputModel;
import org.jbpm.bpmn2.activity.UserTaskWithParametrizedInputProcess;
import org.jbpm.bpmn2.activity.UserTaskWithSimulationMetaDataModel;
import org.jbpm.bpmn2.activity.UserTaskWithSimulationMetaDataProcess;
import org.jbpm.bpmn2.adhoc.SubProcessInAdHocProcessModel;
import org.jbpm.bpmn2.adhoc.SubProcessInAdHocProcessProcess;
import org.jbpm.bpmn2.flow.CompositeWithDIGraphicalModel;
import org.jbpm.bpmn2.flow.CompositeWithDIGraphicalProcess;
import org.jbpm.bpmn2.flow.MinimalImplicitModel;
import org.jbpm.bpmn2.flow.MinimalImplicitProcess;
import org.jbpm.bpmn2.flow.MinimalMetadataModel;
import org.jbpm.bpmn2.flow.MinimalMetadataProcess;
import org.jbpm.bpmn2.flow.MinimalModel;
import org.jbpm.bpmn2.flow.MinimalProcess;
import org.jbpm.bpmn2.flow.MinimalWithDIGraphicalModel;
import org.jbpm.bpmn2.flow.MinimalWithDIGraphicalProcess;
import org.jbpm.bpmn2.flow.MinimalWithGraphicalModel;
import org.jbpm.bpmn2.flow.MinimalWithGraphicalProcess;
import org.jbpm.bpmn2.flow.ProcessCustomDescriptionMetaDataModel;
import org.jbpm.bpmn2.flow.ProcessCustomDescriptionMetaDataProcess;
import org.jbpm.bpmn2.flow.ProcessVariableCustomDescriptionMetaDataModel;
import org.jbpm.bpmn2.flow.ProcessVariableCustomDescriptionMetaDataProcess;
import org.jbpm.bpmn2.flow.ProcessWithVariableNameModel;
import org.jbpm.bpmn2.flow.ProcessWithVariableNameProcess;
import org.jbpm.bpmn2.flow.SubProcessModel;
import org.jbpm.bpmn2.flow.SubProcessProcess;
import org.jbpm.bpmn2.flow.UserTaskActorGroupModel;
import org.jbpm.bpmn2.flow.UserTaskActorGroupProcess;
import org.jbpm.bpmn2.flow.UserTaskActorModel;
import org.jbpm.bpmn2.flow.UserTaskActorProcess;
import org.jbpm.bpmn2.flow.UserTaskGroupModel;
import org.jbpm.bpmn2.flow.UserTaskGroupProcess;
import org.jbpm.bpmn2.flow.UserTaskModel;
import org.jbpm.bpmn2.flow.UserTaskNoneModel;
import org.jbpm.bpmn2.flow.UserTaskNoneProcess;
import org.jbpm.bpmn2.flow.UserTaskProcess;
import org.jbpm.bpmn2.flow.XORSameTargetModel;
import org.jbpm.bpmn2.flow.XORSameTargetProcess;
import org.jbpm.bpmn2.objects.Account;
import org.jbpm.bpmn2.objects.Address;
import org.jbpm.bpmn2.objects.HelloService;
import org.jbpm.bpmn2.objects.Person;
import org.jbpm.bpmn2.objects.TestUserTaskWorkItemHandler;
import org.jbpm.bpmn2.objects.TestWorkItemHandler;
import org.jbpm.bpmn2.service.ServiceProcessModel;
import org.jbpm.bpmn2.service.ServiceProcessProcess;
import org.jbpm.bpmn2.service.ServiceProcessWithMvelCollectionTransformationModel;
import org.jbpm.bpmn2.service.ServiceProcessWithMvelCollectionTransformationProcess;
import org.jbpm.bpmn2.service.ServiceProcessWithMvelJaxbTransformationModel;
import org.jbpm.bpmn2.service.ServiceProcessWithMvelJaxbTransformationProcess;
import org.jbpm.bpmn2.service.ServiceProcessWithMvelTransformationModel;
import org.jbpm.bpmn2.service.ServiceProcessWithMvelTransformationProcess;
import org.jbpm.bpmn2.service.ServiceTaskModel;
import org.jbpm.bpmn2.service.ServiceTaskProcess;
import org.jbpm.bpmn2.service.ServiceTaskWebServiceModel;
import org.jbpm.bpmn2.service.ServiceTaskWebServiceProcess;
import org.jbpm.bpmn2.subprocess.AssignmentProcessModel;
import org.jbpm.bpmn2.subprocess.AssignmentProcessProcess;
import org.jbpm.bpmn2.subprocess.AssignmentSubProcessProcess;
import org.jbpm.bpmn2.subprocess.CallActivity2Model;
import org.jbpm.bpmn2.subprocess.CallActivity2Process;
import org.jbpm.bpmn2.subprocess.CallActivityMIModel;
import org.jbpm.bpmn2.subprocess.CallActivityMIProcess;
import org.jbpm.bpmn2.subprocess.CallActivityModel;
import org.jbpm.bpmn2.subprocess.CallActivityProcess;
import org.jbpm.bpmn2.subprocess.CallActivityProcessBoundaryErrorModel;
import org.jbpm.bpmn2.subprocess.CallActivityProcessBoundaryErrorProcess;
import org.jbpm.bpmn2.subprocess.CallActivityProcessWithBoundaryEventModel;
import org.jbpm.bpmn2.subprocess.CallActivityProcessWithBoundaryEventProcess;
import org.jbpm.bpmn2.subprocess.CallActivitySubProcessBoundaryErrorModel;
import org.jbpm.bpmn2.subprocess.CallActivitySubProcessBoundaryErrorProcess;
import org.jbpm.bpmn2.subprocess.CallActivitySubProcessModel;
import org.jbpm.bpmn2.subprocess.CallActivitySubProcessProcess;
import org.jbpm.bpmn2.subprocess.CallActivitySubProcessWithBoundaryEventModel;
import org.jbpm.bpmn2.subprocess.CallActivitySubProcessWithBoundaryEventProcess;
import org.jbpm.bpmn2.subprocess.CallActivityWithBoundaryEventModel;
import org.jbpm.bpmn2.subprocess.CallActivityWithBoundaryEventProcess;
import org.jbpm.bpmn2.subprocess.CallActivityWithIOexpressionModel;
import org.jbpm.bpmn2.subprocess.CallActivityWithIOexpressionProcess;
import org.jbpm.bpmn2.subprocess.ErrorsBetweenProcessModel;
import org.jbpm.bpmn2.subprocess.ErrorsBetweenProcessProcess;
import org.jbpm.bpmn2.subprocess.ErrorsBetweenSubProcessProcess;
import org.jbpm.bpmn2.subprocess.FlowChild1Model;
import org.jbpm.bpmn2.subprocess.FlowChild1Process;
import org.jbpm.bpmn2.subprocess.FlowChild2Model;
import org.jbpm.bpmn2.subprocess.FlowChild2Process;
import org.jbpm.bpmn2.subprocess.FlowChild3Model;
import org.jbpm.bpmn2.subprocess.FlowChild3Process;
import org.jbpm.bpmn2.subprocess.FlowMainModel;
import org.jbpm.bpmn2.subprocess.FlowMainProcess;
import org.jbpm.bpmn2.subprocess.InputMappingUsingValueModel;
import org.jbpm.bpmn2.subprocess.InputMappingUsingValueProcess;
import org.jbpm.bpmn2.subprocess.MainGroupAssignmentModel;
import org.jbpm.bpmn2.subprocess.MainGroupAssignmentProcess;
import org.jbpm.bpmn2.subprocess.SingleTaskWithVarDefModel;
import org.jbpm.bpmn2.subprocess.SingleTaskWithVarDefProcess;
import org.jbpm.bpmn2.subprocess.SubProcessWithEntryExitScriptsModel;
import org.jbpm.bpmn2.subprocess.SubProcessWithEntryExitScriptsProcess;
import org.jbpm.bpmn2.subprocess.SubProcessWithTerminateEndEventModel;
import org.jbpm.bpmn2.subprocess.SubProcessWithTerminateEndEventProcess;
import org.jbpm.bpmn2.subprocess.SubProcessWithTerminateEndEventProcessScopeModel;
import org.jbpm.bpmn2.subprocess.SubProcessWithTerminateEndEventProcessScopeProcess;
import org.jbpm.bpmn2.subprocess.SubProcessWithTypeVariableModel;
import org.jbpm.bpmn2.subprocess.SubProcessWithTypeVariableProcess;
import org.jbpm.bpmn2.subprocess.SubprocessGroupAssignmentModel;
import org.jbpm.bpmn2.subprocess.SubprocessGroupAssignmentProcess;
import org.jbpm.bpmn2.subprocess.UserTaskChildModel;
import org.jbpm.bpmn2.subprocess.UserTaskChildProcess;
import org.jbpm.bpmn2.subprocess.UserTaskMainModel;
import org.jbpm.bpmn2.subprocess.UserTaskMainProcess;
import org.jbpm.bpmn2.task.ReceiveTaskModel;
import org.jbpm.bpmn2.task.ReceiveTaskProcess;
import org.jbpm.bpmn2.task.SendTaskModel;
import org.jbpm.bpmn2.task.SendTaskProcess;
import org.jbpm.bpmn2.test.RequirePersistence;
import org.jbpm.process.builder.ActionBuilder;
import org.jbpm.process.builder.AssignmentBuilder;
import org.jbpm.process.builder.ProcessBuildContext;
import org.jbpm.process.builder.ProcessClassBuilder;
import org.jbpm.process.builder.ReturnValueEvaluatorBuilder;
import org.jbpm.process.builder.dialect.ProcessDialect;
import org.jbpm.process.builder.dialect.ProcessDialectRegistry;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.event.listeners.RuleAwareProcessEventListener;
import org.jbpm.process.instance.event.listeners.TriggerRulesEventListener;
import org.jbpm.process.workitem.builtin.DoNothingWorkItemHandler;
import org.jbpm.process.workitem.builtin.ReceiveTaskHandler;
import org.jbpm.process.workitem.builtin.SendTaskHandler;
import org.jbpm.process.workitem.builtin.SystemOutWorkItemHandler;
import org.jbpm.test.util.ProcessCompletedCountDownProcessEventListener;
import org.jbpm.test.utils.EventTrackerProcessListener;
import org.jbpm.test.utils.ProcessTestHelper;
import org.jbpm.workflow.core.impl.DataAssociation;
import org.jbpm.workflow.core.impl.DataDefinition;
import org.jbpm.workflow.core.node.ActionNode;
import org.jbpm.workflow.core.node.Assignment;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.jbpm.workflow.instance.WorkflowRuntimeException;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.jbpm.workflow.instance.node.DynamicUtils;
import org.jbpm.workflow.instance.node.EndNodeInstance;
import org.jbpm.workflow.instance.node.StartNodeInstance;
import org.jbpm.workflow.instance.node.WorkItemNodeInstance;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kie.api.definition.process.Node;
import org.kie.api.definition.process.NodeContainer;
import org.kie.api.definition.process.Process;
import org.kie.api.definition.process.WorkflowElementIdentifier;
import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.event.process.ProcessNodeEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.event.process.ProcessVariableChangedEvent;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.MatchCancelledEvent;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.kogito.Application;
import org.kie.kogito.auth.SecurityPolicy;
import org.kie.kogito.handlers.HelloService_hello__2_Handler;
import org.kie.kogito.handlers.HelloService_validate__2_Handler;
import org.kie.kogito.handlers.Interface1_operation1_EAID_3344916D_2BAC_4ab6_92D4_F739156D0933_Handler;
import org.kie.kogito.handlers.Interface1_operation1_ServiceTask_2_Handler;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.kie.kogito.internal.process.runtime.KogitoNode;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcessInstance;
import org.kie.kogito.internal.process.workitem.InvalidTransitionException;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.process.workitems.InternalKogitoWorkItem;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.jbpm.test.utils.ProcessTestHelper.findRemovedInstance;

public class ActivityTest extends JbpmBpmn2TestCase {

    @Test
    public void testMinimalProcess() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<MinimalModel> minimalProcess = MinimalProcess.newProcess(app);
        MinimalModel model = minimalProcess.createModel();
        org.kie.kogito.process.ProcessInstance<MinimalModel> instance = minimalProcess.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMinimalProcessImplicit() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<MinimalImplicitModel> minimalImplicitProcess = MinimalImplicitProcess.newProcess(app);
        MinimalImplicitModel model = minimalImplicitProcess.createModel();
        ProcessInstance<MinimalImplicitModel> instance = minimalImplicitProcess.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMinimalProcessWithGraphical() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<MinimalWithGraphicalModel> minimalWithGraphicalProcess = MinimalWithGraphicalProcess.newProcess(app);
        MinimalWithGraphicalModel model = minimalWithGraphicalProcess.createModel();
        ProcessInstance<MinimalWithGraphicalModel> instance = minimalWithGraphicalProcess.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMinimalProcessWithDIGraphical() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<MinimalWithDIGraphicalModel> minimalWithDIGraphicalProcess = MinimalWithDIGraphicalProcess.newProcess(app);
        MinimalWithDIGraphicalModel model = minimalWithDIGraphicalProcess.createModel();
        ProcessInstance<MinimalWithDIGraphicalModel> instance = minimalWithDIGraphicalProcess.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMinimalProcessMetaData() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        final List<String> list1 = new ArrayList<>();
        final List<String> list2 = new ArrayList<>();
        final List<String> list3 = new ArrayList<>();
        final List<String> list4 = new ArrayList<>();

        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                logger.debug("before node");
                Map<String, Object> metaData = event.getNodeInstance().getNode().getMetaData();
                for (Map.Entry<String, Object> entry : metaData.entrySet()) {
                    logger.debug(entry.getKey() + " " + entry.getValue());
                }
                String customTag = (String) metaData.get("customTag");
                if (customTag != null) {
                    list1.add(customTag);
                }
                String customTag2 = (String) metaData.get("customTag2");
                if (customTag2 != null) {
                    list2.add(customTag2);
                }
            }

            @Override
            public void afterVariableChanged(ProcessVariableChangedEvent event) {
                logger.debug("after variable");
                VariableScope variableScope = (VariableScope) ((org.jbpm.process.core.impl.ProcessImpl) event.getProcessInstance().getProcess())
                        .resolveContext(VariableScope.VARIABLE_SCOPE, event.getVariableId());
                if (variableScope == null) {
                    return;
                }
                Map<String, Object> metaData = variableScope.findVariable(event.getVariableId()).getMetaData();
                for (Map.Entry<String, Object> entry : metaData.entrySet()) {
                    logger.debug(entry.getKey() + " " + entry.getValue());
                }
                String customTag = (String) metaData.get("customTagVar");
                if (customTag != null) {
                    list3.add(customTag);
                }
            }

            @Override
            public void afterProcessStarted(ProcessStartedEvent event) {
                logger.debug("after process");
                Map<String, Object> metaData = event.getProcessInstance().getProcess().getMetaData();
                for (Map.Entry<String, Object> entry : metaData.entrySet()) {
                    logger.debug(entry.getKey() + " " + entry.getValue());
                }
                String customTag = (String) metaData.get("customTagProcess");
                if (customTag != null) {
                    list4.add(customTag);
                }
            }
        });

        org.kie.kogito.process.Process<MinimalMetadataModel> process = MinimalMetadataProcess.newProcess(app);
        MinimalMetadataModel model = process.createModel();
        model.setX("krisv");
        ProcessInstance<MinimalMetadataModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(list1).hasSize(3);
        assertThat(list2).hasSize(2);
        assertThat(list3).hasSize(1);
        assertThat(list4).hasSize(1);
    }

    @Test
    public void testCompositeProcessWithDIGraphical() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<CompositeWithDIGraphicalModel> compositeWithDIGraphicalProcess = CompositeWithDIGraphicalProcess.newProcess(app);
        CompositeWithDIGraphicalModel model = compositeWithDIGraphicalProcess.createModel();
        ProcessInstance<CompositeWithDIGraphicalModel> instance = compositeWithDIGraphicalProcess.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testScriptTask() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<ScriptTaskModel> scriptTasklProcess = ScriptTaskProcess.newProcess(app);
        ScriptTaskModel model = scriptTasklProcess.createModel();
        ProcessInstance<ScriptTaskModel> instance = scriptTasklProcess.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testScriptTaskWithIO() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<ScriptTaskWithIOModel> scriptTaskWithIOProcess = ScriptTaskWithIOProcess.newProcess(app);
        ScriptTaskWithIOModel model = scriptTaskWithIOProcess.createModel();
        model.setName("john");
        ProcessInstance<ScriptTaskWithIOModel> instance = scriptTaskWithIOProcess.createInstance(model);
        instance.start();
        assertThat(scriptTaskWithIOProcess).isNotNull();
        Collection<KogitoNode> nodes = scriptTaskWithIOProcess.findNodes(Objects::nonNull);
        assertThat(nodes).hasSize(3);
        assertThat(nodes).filteredOn(n -> n instanceof ActionNode).allMatch(n -> ((ActionNode) n).getInAssociations().size() == 1 && ((ActionNode) n).getOutAssociations().size() == 1);
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testRuleTask() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-RuleTask.bpmn2",
                "BPMN2-RuleTask.drl");

        List<String> list = new ArrayList<>();
        kruntime.getKieSession().setGlobal("list", list);
        KogitoProcessInstance processInstance = kruntime.startProcess("RuleTask");
        assertThat(list).hasSize(1);
        assertProcessInstanceFinished(processInstance, kruntime);
    }

    @Test
    public void testRuleTask2() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-RuleTask2.bpmn2", "BPMN2-RuleTask2.drl");

        List<String> list = new ArrayList<>();
        kruntime.getKieSession().setGlobal("list", list);
        Map<String, Object> params = new HashMap<>();
        params.put("x", "SomeString");
        KogitoProcessInstance processInstance = kruntime.startProcess("RuleTask",
                params);
        assertThat(list).isEmpty();
        assertProcessInstanceFinished(processInstance, kruntime);
    }

    @Test
    public void testRuleTaskSetVariableWithReconnect() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-RuleTask2.bpmn2", "BPMN2-RuleTaskSetVariableReconnect.drl");

        List<String> list = new ArrayList<>();
        kruntime.getKieSession().setGlobal("list", list);
        Map<String, Object> params = new HashMap<>();
        params.put("x", "SomeString");

        KogitoProcessInstance processInstance = kruntime.startProcess("RuleTask",
                params);
        assertThat(list).hasSize(1);

        assertProcessVarValue(processInstance, "x", "AnotherString");
        assertProcessInstanceFinished(processInstance, kruntime);
    }

    @Test
    @RequirePersistence(false)
    public void testRuleTaskWithFacts() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-RuleTaskWithFact.bpmn2", "BPMN2-RuleTask3.drl");

        kruntime.getKieSession().addEventListener(new AgendaEventListener() {
            public void matchCreated(MatchCreatedEvent event) {
            }

            public void matchCancelled(MatchCancelledEvent event) {
            }

            public void beforeRuleFlowGroupDeactivated(
                    org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent event) {
            }

            public void beforeRuleFlowGroupActivated(
                    org.kie.api.event.rule.RuleFlowGroupActivatedEvent event) {
            }

            public void beforeMatchFired(BeforeMatchFiredEvent event) {
            }

            public void agendaGroupPushed(
                    org.kie.api.event.rule.AgendaGroupPushedEvent event) {
            }

            public void agendaGroupPopped(
                    org.kie.api.event.rule.AgendaGroupPoppedEvent event) {
            }

            public void afterRuleFlowGroupDeactivated(
                    org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent event) {
            }

            public void afterRuleFlowGroupActivated(
                    org.kie.api.event.rule.RuleFlowGroupActivatedEvent event) {
                kruntime.getKieSession().fireAllRules();
            }

            public void afterMatchFired(AfterMatchFiredEvent event) {
            }

        });

        Map<String, Object> params = new HashMap<>();
        params.put("x", "SomeString");
        KogitoProcessInstance processInstance = kruntime.startProcess("RuleTask",
                params);
        assertProcessInstanceFinished(processInstance, kruntime);

        params = new HashMap<>();

        KogitoProcessRuntime kruntime2 = createKogitoProcessRuntime("BPMN2-RuleTaskWithFact.bpmn2", "BPMN2-RuleTask3.drl");
        processInstance = kruntime2.startProcess("RuleTask", params);

        assertThat(processInstance.getState()).isEqualTo(KogitoProcessInstance.STATE_ERROR);

        KogitoProcessRuntime kruntime3 = createKogitoProcessRuntime("BPMN2-RuleTaskWithFact.bpmn2", "BPMN2-RuleTask3.drl");
        params = new HashMap<>();
        params.put("x", "SomeString");
        processInstance = kruntime3.startProcess("RuleTask", params);
        assertProcessInstanceFinished(processInstance, kruntime3);
    }

    @Test
    public void testRuleTaskAcrossSessions() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-RuleTask.bpmn2", "BPMN2-RuleTask.drl");
        KogitoProcessRuntime kruntime2 = createKogitoProcessRuntime("BPMN2-RuleTask.bpmn2", "BPMN2-RuleTask.drl");

        List<String> list1 = new ArrayList<>();
        kruntime.getKieSession().setGlobal("list", list1);
        List<String> list2 = new ArrayList<>();
        kruntime2.getKieSession().setGlobal("list", list2);
        KogitoProcessInstance processInstance1 = kruntime.startProcess("RuleTask");
        KogitoProcessInstance processInstance2 = kruntime2.startProcess("RuleTask");
        assertProcessInstanceFinished(processInstance1, kruntime);
        assertProcessInstanceFinished(processInstance2, kruntime2);
    }

    @Test
    public void testUserTaskWithDataStoreScenario() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DoNothingWorkItemHandler());
        org.kie.kogito.process.Process<UserTaskWithDataStoreModel> processDefinition = UserTaskWithDataStoreProcess.newProcess(app);
        UserTaskWithDataStoreModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<UserTaskWithDataStoreModel> instance = processDefinition.createInstance(model);
        instance.start();
        // we can't test further as user tasks are asynchronous.
    }

    @Test
    public void testUserTask() {
        Application app = ProcessTestHelper.newApplication();
        TestUserTaskWorkItemHandler workItemHandler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskModel> processDefinition = UserTaskProcess.newProcess(app);
        UserTaskModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<UserTaskModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameter("ActorId")).isEqualTo("john");
        instance.completeWorkItem(workItem.getStringId(), emptyMap(), SecurityPolicy.of("john", Collections.emptyList()));
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testUserTaskActorAssignment() {
        Application app = ProcessTestHelper.newApplication();
        TestUserTaskWorkItemHandler workItemHandler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskActorModel> processDefinition = UserTaskActorProcess.newProcess(app);
        UserTaskActorModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<UserTaskActorModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameter("ActorId")).isEqualTo("john");

        ProcessTestHelper.completeWorkItem(instance, emptyMap(), "john");

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testUserTaskActorAssignmentActorFailure() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskActorModel> processDefinition = UserTaskActorProcess.newProcess(app);
        UserTaskActorModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<UserTaskActorModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();

        ProcessTestHelper.completeWorkItem(instance, emptyMap(), "mary");

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testUserTaskActorAssignmentNoPolicyFailure() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskActorModel> processDefinition = UserTaskActorProcess.newProcess(app);
        UserTaskActorModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<UserTaskActorModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();

        ProcessTestHelper.completeWorkItem(instance, emptyMap(), "bayron");

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testUserTaskGroupAssignment() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskGroupModel> processDefinition = UserTaskGroupProcess.newProcess(app);
        UserTaskGroupModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<UserTaskGroupModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();

        ProcessTestHelper.completeWorkItem(instance, emptyMap(), "john", "IT");

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testUserTaskGroupAssignmentFailure() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskGroupModel> processDefinition = UserTaskGroupProcess.newProcess(app);
        UserTaskGroupModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<UserTaskGroupModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();

        ProcessTestHelper.completeWorkItem(instance, emptyMap(), "john", "HR");

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testUserTaskGroupAssignmentNoPolicyFailure() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskGroupModel> processDefinition = UserTaskGroupProcess.newProcess(app);
        UserTaskGroupModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<UserTaskGroupModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();

        ProcessTestHelper.completeWorkItem(instance, emptyMap(), "bayron");

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testUserTaskNoneAssignmentFailure() {
        Application app = ProcessTestHelper.newApplication();
        TestUserTaskWorkItemHandler workItemHandler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskNoneModel> processDefinition = UserTaskNoneProcess.newProcess(app);
        UserTaskNoneModel model = processDefinition.createModel();
        ProcessInstance<UserTaskNoneModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        try {
            ProcessTestHelper.completeWorkItem(instance, emptyMap(), "john", "HR");
        } catch (Throwable e) {
            assertThat(e).isInstanceOf(InvalidTransitionException.class);
        }
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testUserTaskNoneAssignmentNoPolicyFailure() {
        Application app = ProcessTestHelper.newApplication();
        TestUserTaskWorkItemHandler workItemHandler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskNoneModel> processDefinition = UserTaskNoneProcess.newProcess(app);
        UserTaskNoneModel model = processDefinition.createModel();
        ProcessInstance<UserTaskNoneModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        try {
            ProcessTestHelper.completeWorkItem(instance, emptyMap(), null);
        } catch (Throwable e) {
            assertThat(e).isInstanceOf(InvalidTransitionException.class);
        }
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testUserTaskActorAndGroupAssignmentWithActor() {
        Application app = ProcessTestHelper.newApplication();
        TestUserTaskWorkItemHandler workItemHandler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskActorGroupModel> processDefinition = UserTaskActorGroupProcess.newProcess(app);
        UserTaskActorGroupModel model = processDefinition.createModel();
        ProcessInstance<UserTaskActorGroupModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();

        ProcessTestHelper.completeWorkItem(instance, emptyMap(), "john");

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testUserTaskActorAndGroupAssignmentWithGroup() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskActorGroupModel> processDefinition = UserTaskActorGroupProcess.newProcess(app);
        UserTaskActorGroupModel model = processDefinition.createModel();
        ProcessInstance<UserTaskActorGroupModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();

        ProcessTestHelper.completeWorkItem(instance, emptyMap(), "mary", "IT");

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testUserTaskActorAndGroupAssignmentFailure() {
        Application app = ProcessTestHelper.newApplication();
        TestUserTaskWorkItemHandler workItemHandler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskActorGroupModel> processDefinition = UserTaskActorGroupProcess.newProcess(app);
        UserTaskActorGroupModel model = processDefinition.createModel();
        ProcessInstance<UserTaskActorGroupModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();

        ProcessTestHelper.completeWorkItem(instance, emptyMap(), "mary", "HR");

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testUserTaskActorAndGroupAssignmentNoPolicyFailure() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskActorGroupModel> processDefinition = UserTaskActorGroupProcess.newProcess(app);
        UserTaskActorGroupModel model = processDefinition.createModel();
        ProcessInstance<UserTaskActorGroupModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();

        ProcessTestHelper.completeWorkItem(instance, emptyMap(), "bayron");

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testUserTaskVerifyParameters() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskModel> process = UserTaskProcess.newProcess(app);
        UserTaskModel model = process.createModel();
        ProcessInstance<UserTaskModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        org.kie.kogito.internal.process.workitem.KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameter("ActorId")).isEqualTo("john");
        final String pId = processInstance.id();
        Optional<ProcessInstance<UserTaskModel>> processInstance1 = process.instances().stream()
                .filter(instance -> instance.id().equals(pId)).findFirst();
        assertThat(processInstance1).isNotNull();
        Assertions.assertTrue(processInstance1.isPresent());
        Optional<KogitoNodeInstance> nodeInstance = processInstance1.get().findNodes(node -> node.getStringId().equals(workItem.getNodeInstance().getStringId()))
                .stream().findFirst();
        assertThat(nodeInstance).isNotNull();
        Assertions.assertTrue(nodeInstance.isPresent());
        assertThat(nodeInstance.get()).isInstanceOf(WorkItemNodeInstance.class);
        String deploymentId = ((InternalKogitoWorkItem) workItem).getDeploymentId();
        String nodeInstanceId = ((InternalKogitoWorkItem) workItem).getNodeInstanceStringId();
        WorkflowElementIdentifier nodeId = ((InternalKogitoWorkItem) workItem).getNodeId();

        assertThat(deploymentId).isEqualTo(((InternalKogitoWorkItem) workItem).getDeploymentId());
        assertThat(nodeId).isEqualTo(((InternalKogitoWorkItem) workItem).getNodeId());
        assertThat(nodeInstanceId).isEqualTo(((InternalKogitoWorkItem) workItem).getNodeInstanceStringId());
        processInstance.completeWorkItem(workItem.getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testCallActivityWithContantsAssignment() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "CustomTask", workItemHandler);
        org.kie.kogito.process.Process<SingleTaskWithVarDefModel> singleTaskWithVarDefModelProcess = SingleTaskWithVarDefProcess.newProcess(app);

        org.kie.kogito.process.Process<InputMappingUsingValueModel> processDefinition = InputMappingUsingValueProcess.newProcess(app);
        InputMappingUsingValueModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<InputMappingUsingValueModel> instance = processDefinition.createInstance(model);
        instance.start();

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        Object value = workItem.getParameter("TaskName");
        assertThat(value).isNotNull().isEqualTo("test string");

        ProcessInstance<SingleTaskWithVarDefModel> singleTaskWithVarDefModelProcessInstance = singleTaskWithVarDefModelProcess.instances().stream().findAny().orElse(null);
        singleTaskWithVarDefModelProcessInstance.completeWorkItem(workItem.getStringId(), emptyMap());

        assertThat(findRemovedInstance(app, instance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testSubProcessWithEntryExitScripts() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        EventTrackerProcessListener listener = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerHandler(app, "Human Task", new TestUserTaskWorkItemHandler());

        org.kie.kogito.process.Process<SubProcessWithEntryExitScriptsModel> process = SubProcessWithEntryExitScriptsProcess.newProcess(app);
        ProcessInstance<SubProcessWithEntryExitScriptsModel> processInstance = process.createInstance(process.createModel());
        processInstance.start();

        assertThat(listener.tracked()).anyMatch(ProcessTestHelper.triggered("Task1"));
        assertThat(processInstance.variables().getVar1()).isNotNull().hasToString("10");
        assertThat(listener.tracked()).anyMatch(ProcessTestHelper.triggered("Task2"));
        assertThat(processInstance.variables().getVar2()).isNotNull().hasToString("20");
        assertThat(listener.tracked()).anyMatch(ProcessTestHelper.triggered("Task3"));
        assertThat(processInstance.variables().getVar3()).isNotNull().hasToString("30");
        assertThat(listener.tracked()).anyMatch(ProcessTestHelper.triggered("SubProcess"));
        assertThat(processInstance.variables().getVar4()).isNotNull().hasToString("40");
        assertThat(processInstance.variables().getVar5()).isNotNull().hasToString("50");

        ProcessTestHelper.completeWorkItem(processInstance, emptyMap(), "john");
        assertThat(processInstance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testCallActivity() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<CallActivityModel> processDefinition = CallActivityProcess.newProcess(app);
        CallActivityModel model = processDefinition.createModel();
        model.setX("oldValue");
        ProcessInstance<CallActivityModel> instance = processDefinition.createInstance(model);

        CallActivitySubProcessProcess.newProcess(app);

        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getY()).isEqualTo("new value");
    }

    @Test
    public void testCallActivityMI() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task",
                workItemHandler);
        final List<String> subprocessStarted = new ArrayList<>();
        DefaultKogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {

            @Override
            public void beforeProcessStarted(ProcessStartedEvent event) {
                if (event.getProcessInstance().getProcessId().equals("CallActivitySubProcess")) {
                    subprocessStarted.add(((KogitoProcessInstance) event.getProcessInstance()).getStringId());
                }
            }

        };
        ProcessTestHelper.registerProcessEventListener(app, listener);
        CallActivitySubProcessProcess.newProcess(app);
        org.kie.kogito.process.Process<CallActivityMIModel> process = CallActivityMIProcess.newProcess(app);
        CallActivityMIModel model = process.createModel();
        List<String> list = new ArrayList<>();
        list.add("first");
        list.add("second");
        List<String> listOut = new ArrayList<>();
        model.setX("oldValue");
        model.setList(list);
        model.setListOut(listOut);
        ProcessInstance<CallActivityMIModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(org.jbpm.process.instance.ProcessInstance.STATE_COMPLETED);
        assertThat(subprocessStarted).hasSize(2);
        listOut = processInstance.variables().getListOut();
        assertThat(listOut).isNotNull().hasSize(2).containsExactly("new value", "new value");
    }

    @Test
    public void testCallActivity2() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task",
                workItemHandler);
        org.kie.kogito.process.Process<CallActivitySubProcessModel> callActivitySubProcess = CallActivitySubProcessProcess.newProcess(app);
        org.kie.kogito.process.Process<CallActivity2Model> process = CallActivity2Process.newProcess(app);
        CallActivity2Model model = process.createModel();
        model.setX("oldValue");
        ProcessInstance<CallActivity2Model> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(org.jbpm.process.instance.ProcessInstance.STATE_ACTIVE);
        assertThat(processInstance.variables().getY()).isEqualTo("new value");
        org.kie.kogito.internal.process.workitem.KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameter("ActorId")).isEqualTo("krisv");
        processInstance.completeWorkItem(workItem.getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(org.jbpm.process.instance.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testCallActivityByName() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-CallActivityByName.bpmn2",
                "org/jbpm/bpmn2/subprocess/BPMN2-CallActivitySubProcess.bpmn2",
                "BPMN2-CallActivitySubProcessV2.bpmn2");

        Map<String, Object> params = new HashMap<>();
        params.put("x", "oldValue");
        KogitoProcessInstance processInstance = kruntime.startProcess(
                "ParentProcess", params);
        assertProcessInstanceCompleted(processInstance);
        assertThat(((KogitoWorkflowProcessInstance) processInstance).getVariable("y")).isEqualTo("new value V2");
    }

    @Test
    public void testSubProcess() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void afterProcessStarted(ProcessStartedEvent event) {
                logger.debug(event.toString());
            }

            @Override
            public void beforeVariableChanged(ProcessVariableChangedEvent event) {
                logger.debug(event.toString());
            }

            @Override
            public void afterVariableChanged(ProcessVariableChangedEvent event) {
                logger.debug(event.toString());
            }
        });
        org.kie.kogito.process.Process<SubProcessModel> definition = SubProcessProcess.newProcess(app);
        ProcessInstance<SubProcessModel> processInstance = definition.createInstance(definition.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testInvalidSubProcess() throws Exception {
        try {
            kruntime = createKogitoProcessRuntime("BPMN2-SubProcessInvalid.bpmn2");
            fail("Process should be invalid, there should be build errors");
        } catch (RuntimeException e) {
            // there should be build errors
        }
    }

    @Test
    public void testSubProcessWrongStartEvent() throws Exception {
        try {
            kruntime = createKogitoProcessRuntime("BPMN2-SubProcessWrongStartEvent.bpmn2");
            fail("Process should be invalid, there should be build errors");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Embedded subprocess can only have none start event.");
        }
    }

    @Test
    public void testSubProcessWrongStartEventTimer() throws Exception {
        try {
            kruntime = createKogitoProcessRuntime("SubprocessWithTimer.bpmn2");
            fail("Process should be invalid, there should be build errors");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Embedded subprocess can only have none start event.");
        }
    }

    @Test
    public void testMultiinstanceSubProcessWrongStartEvent() throws Exception {
        try {
            kruntime = createKogitoProcessRuntime("MultipleSubprocessWithSignalStartEvent.bpmn2");
            fail("Process should be invalid, there should be build errors");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("MultiInstance subprocess can only have none start event.");
        }
    }

    @Test
    public void testSubProcessWithTerminateEndEvent() {
        Application app = ProcessTestHelper.newApplication();
        EventTrackerProcessListener listener = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<SubProcessWithTerminateEndEventModel> processDefinition = SubProcessWithTerminateEndEventProcess.newProcess(app);
        SubProcessWithTerminateEndEventModel model = processDefinition.createModel();

        org.kie.kogito.process.ProcessInstance<SubProcessWithTerminateEndEventModel> instance = processDefinition.createInstance(model);
        instance.start();
        Set<NodeInstance> processNodeEvents = listener.tracked().stream()
                .map(ProcessNodeEvent::getNodeInstance)
                .collect(Collectors.toSet());
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(processNodeEvents).hasSize(7);

    }

    @Test
    public void testSubProcessWithTerminateEndEventProcessScope() {

        Application app = ProcessTestHelper.newApplication();
        final List<String> nodeList = new ArrayList<>();
        EventTrackerProcessListener listener = new EventTrackerProcessListener() {

            @Override
            public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
                NodeInstance nodeInstance = event.getNodeInstance();
                if (!(nodeInstance instanceof EndNodeInstance || nodeInstance instanceof StartNodeInstance)) {
                    nodeList.add(nodeInstance.getNodeName());
                }
            }
        };
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<SubProcessWithTerminateEndEventProcessScopeModel> processDefinition = SubProcessWithTerminateEndEventProcessScopeProcess.newProcess(app);
        SubProcessWithTerminateEndEventProcessScopeModel model = processDefinition.createModel();

        org.kie.kogito.process.ProcessInstance<SubProcessWithTerminateEndEventProcessScopeModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(nodeList).hasSize(3);
    }

    @Test
    public void testAdHocProcess() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-AdHocProcess.bpmn2");

        KogitoProcessInstance processInstance = kruntime.startProcess("AdHocProcess");
        assertThat(processInstance.getState()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        logger.debug("Triggering node");
        kruntime.signalEvent("Task1", null, processInstance.getStringId());
        assertProcessInstanceActive(processInstance);
        kruntime.signalEvent("User1", null, processInstance.getStringId());
        assertProcessInstanceActive(processInstance);
        kruntime.getKieSession().insert(new Person());
        kruntime.signalEvent("Task3", null, processInstance.getStringId());
        assertProcessInstanceFinished(processInstance, kruntime);
    }

    @Test
    public void testAdHocProcessDynamicTask() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-AdHocProcess.bpmn2");

        KogitoProcessInstance processInstance = kruntime.startProcess("AdHocProcess");
        assertThat(processInstance.getState()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        logger.debug("Triggering node");
        kruntime.signalEvent("Task1", null, processInstance.getStringId());
        assertProcessInstanceActive(processInstance);
        TestWorkItemHandler workItemHandler2 = new TestWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("OtherTask",
                workItemHandler2);
        DynamicUtils.addDynamicWorkItem(processInstance, kruntime.getKieSession(), "OtherTask", new HashMap<>());
        org.kie.kogito.internal.process.workitem.KogitoWorkItem workItem = workItemHandler2.getWorkItem();
        assertThat(workItem).isNotNull();
        kruntime.getKogitoWorkItemManager().completeWorkItem(workItem.getStringId(), null);
        kruntime.signalEvent("User1", null, processInstance.getStringId());
        assertProcessInstanceActive(processInstance);
        kruntime.getKieSession().insert(new Person());
        kruntime.signalEvent("Task3", null, processInstance.getStringId());
        assertProcessInstanceFinished(processInstance, kruntime);
    }

    @Test
    public void testAdHocProcessDynamicSubProcess() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-AdHocProcess.bpmn2",
                "org/jbpm/bpmn2/flow/BPMN2-MinimalProcess.bpmn2");

        KogitoProcessInstance processInstance = kruntime.startProcess("AdHocProcess");
        assertThat(processInstance.getState()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        logger.debug("Triggering node");
        kruntime.signalEvent("Task1", null, processInstance.getStringId());
        assertProcessInstanceActive(processInstance);
        TestWorkItemHandler workItemHandler2 = new TestWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("OtherTask",
                workItemHandler2);
        DynamicUtils.addDynamicSubProcess(processInstance, kruntime.getKieSession(), "Minimal",
                new HashMap<>());
        kruntime.signalEvent("User1", null, processInstance.getStringId());
        assertProcessInstanceActive(processInstance);
        kruntime.getKieSession().insert(new Person());
        kruntime.signalEvent("Task3", null, processInstance.getStringId());
        assertProcessInstanceFinished(processInstance, kruntime);
    }

    @Test
    public void testServiceTask() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "org.jbpm.bpmn2.objects.HelloService_hello__2_Handler", new HelloService_hello__2_Handler());
        org.kie.kogito.process.Process<ServiceProcessModel> definition = ServiceProcessProcess.newProcess(app);

        ServiceProcessModel model = definition.createModel();
        model.setS("john");
        org.kie.kogito.process.ProcessInstance<ServiceProcessModel> instance = definition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getS()).isEqualTo("Hello john!");

    }

    @Test
    public void testServiceTaskNoInterfaceName() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "org.jbpm.bpmn2.services.Interface1_operation1_ServiceTask_2_Handler", new Interface1_operation1_ServiceTask_2_Handler());
        org.kie.kogito.process.Process<ServiceTaskWebServiceModel> definition = ServiceTaskWebServiceProcess.newProcess(app);

        ServiceTaskWebServiceModel model = definition.createModel();
        org.kie.kogito.process.ProcessInstance<ServiceTaskWebServiceModel> instance = definition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testSendTask() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Send Task", new SendTaskHandler());
        org.kie.kogito.process.Process<SendTaskModel> processDefinition = SendTaskProcess.newProcess(app);
        SendTaskModel model = processDefinition.createModel();
        model.setS("john");
        org.kie.kogito.process.ProcessInstance<SendTaskModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testReceiveTask() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ReceiveTaskHandler receiveTaskHandler = new ReceiveTaskHandler();
        ProcessTestHelper.registerHandler(app, "Receive Task", receiveTaskHandler);
        org.kie.kogito.process.Process<ReceiveTaskModel> processDefinition = ReceiveTaskProcess.newProcess(app);
        ReceiveTaskModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<ReceiveTaskModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);

        Map<String, Object> results = new HashMap<>();
        results.put("Message", "Hello john!");

        ProcessTestHelper.completeWorkItem(instance, results);

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    @RequirePersistence(false)
    public void testBusinessRuleTask() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-BusinessRuleTask.bpmn2", "BPMN2-BusinessRuleTask.drl");
        kruntime.getProcessEventManager().addEventListener(new RuleAwareProcessEventListener());
        KogitoProcessInstance processInstance = kruntime.startProcess("BPMN2-BusinessRuleTask");
        assertProcessInstanceFinished(processInstance, kruntime);
    }

    @Test
    @RequirePersistence(true)
    public void testBusinessRuleTaskWithPersistence() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-BusinessRuleTask.bpmn2", "BPMN2-BusinessRuleTask.drl");
        kruntime.getProcessEventManager().addEventListener(new RuleAwareProcessEventListener());
        KogitoProcessInstance processInstance = kruntime
                .startProcess("BPMN2-BusinessRuleTask");

        kruntime.getProcessEventManager().addEventListener(new RuleAwareProcessEventListener());

        assertProcessInstanceFinished(processInstance, kruntime);

    }

    @Test
    public void testBusinessRuleTaskDynamic() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-BusinessRuleTaskDynamic.bpmn2",
                "BPMN2-BusinessRuleTask.drl");

        kruntime.getProcessEventManager().addEventListener(new RuleAwareProcessEventListener());

        Map<String, Object> params = new HashMap<>();
        params.put("dynamicrule", "MyRuleFlow");
        KogitoProcessInstance processInstance = kruntime.startProcess(
                "BPMN2-BusinessRuleTask", params);

        assertProcessInstanceFinished(processInstance, kruntime);
    }

    @Test
    public void testBusinessRuleTaskWithDataInputsWithPersistence()
            throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-BusinessRuleTaskWithDataInputs.bpmn2",
                "BPMN2-BusinessRuleTaskWithDataInput.drl");

        Map<String, Object> params = new HashMap<>();
        params.put("person", new Person());
        KogitoProcessInstance processInstance = kruntime.startProcess(
                "BPMN2-BusinessRuleTask", params);

        assertProcessInstanceFinished(processInstance, kruntime);
    }

    @Test
    public void testBusinessRuleTaskWithDataInputs2WithPersistence()
            throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-BusinessRuleTaskWithDataInput.bpmn2",
                "BPMN2-BusinessRuleTaskWithDataInput.drl");

        Map<String, Object> params = new HashMap<>();
        params.put("person", new Person());
        KogitoProcessInstance processInstance = kruntime.startProcess(
                "BPMN2-BusinessRuleTask", params);

        assertProcessInstanceFinished(processInstance, kruntime);
    }

    @ParameterizedTest
    @CsvSource({ "JohnsRuleFlow, john", "MarysRuleFlow, mary" })
    public void testBusinessRuleTaskWithDataInputsWithDynamicUnitName(String ruleFlowName, String expectedName) throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-BusinessRuleTaskWithDynamicUnitName.bpmn2",
                "BPMN2-BusinessRuleTaskWithDynamicRule.drl");

        Person person = new Person();
        Map<String, Object> params = new HashMap<>();
        params.put("person", person);
        params.put("unitName", ruleFlowName);
        KogitoProcessInstance processInstance = kruntime.startProcess(
                "BPMN2-BusinessRuleTask", params);

        assertProcessInstanceFinished(processInstance, kruntime);
        assertThat(person.getName()).isEqualTo(expectedName);
    }

    @Test
    public void testBusinessRuleTaskWithContionalEvent() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-ConditionalEventRuleTask.bpmn2",
                "BPMN2-ConditionalEventRuleTask.drl");
        List<String> list = new ArrayList<>();
        kruntime.getKieSession().setGlobal("list", list);
        KogitoProcessInstance processInstance = kruntime.startProcess("TestFlow");
        assertThat(processInstance.getState()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);
        Person person = new Person();
        person.setName("john");
        kruntime.getKieSession().insert(person);

        assertProcessInstanceCompleted(processInstance.getStringId(), kruntime);
        assertThat(list).hasSize(1);
    }

    @Test
    public void testScriptTaskWithVariableByName() throws Exception {

        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<ProcessWithVariableNameModel> processDefinition = ProcessWithVariableNameProcess.newProcess(app);
        ProcessWithVariableNameModel model = processDefinition.createModel();
        model.setMyVar("test");
        org.kie.kogito.process.ProcessInstance<ProcessWithVariableNameModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testCallActivityWithBoundaryEvent() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        ProcessCompletedCountDownProcessEventListener listener = new ProcessCompletedCountDownProcessEventListener();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<CallActivitySubProcessWithBoundaryEventModel> callActivitySubProcessWithBoundaryEventProcess = CallActivitySubProcessWithBoundaryEventProcess.newProcess(app);
        org.kie.kogito.process.Process<CallActivityWithBoundaryEventModel> process = CallActivityWithBoundaryEventProcess.newProcess(app);
        CallActivityWithBoundaryEventModel model = process.createModel();
        model.setX("oldValue");
        ProcessInstance<CallActivityWithBoundaryEventModel> processInstance = process.createInstance(model);
        processInstance.start();
        listener.waitTillCompleted(15000);

        assertThat(findRemovedInstance(app, processInstance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

        Collection<String> processNodes = process.findNodes(Objects::nonNull).stream().map(Node::getName).collect(Collectors.toSet());
        Collection<String> subProcessNodes = callActivitySubProcessWithBoundaryEventProcess.findNodes(Objects::nonNull).stream().map(Node::getName).collect(Collectors.toSet());
        assertThat(processNodes.containsAll(List.of("StartProcess",
                "CallActivity", "Boundary event", "Script Task", "end"))).isTrue();
        assertThat(subProcessNodes.containsAll(List.of("StartProcess2",
                "User Task"))).isTrue();
    }

    @Test
    public void testCallActivityWithSubProcessWaitState() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        EventTrackerProcessListener listener = new EventTrackerProcessListener();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        ProcessTestHelper.registerProcessEventListener(app, listener);

        org.kie.kogito.process.Process<CallActivitySubProcessWithBoundaryEventModel> callActivitySubProcessWithBoundaryEventProcess = CallActivitySubProcessWithBoundaryEventProcess.newProcess(app);

        org.kie.kogito.process.Process<CallActivityProcessWithBoundaryEventModel> process = CallActivityProcessWithBoundaryEventProcess.newProcess(app);
        CallActivityProcessWithBoundaryEventModel model = process.createModel();
        ProcessInstance<CallActivityProcessWithBoundaryEventModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        org.kie.kogito.internal.process.workitem.KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();

        ProcessInstance<CallActivitySubProcessWithBoundaryEventModel> subProcessInstance = callActivitySubProcessWithBoundaryEventProcess.instances().stream().findAny().get();

        subProcessInstance.completeWorkItem(workItem.getStringId(), emptyMap());

        assertThat(findRemovedInstance(app, processInstance.id()))
                .isPresent().get()
                .extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThat(listener.tracked()).anyMatch(ProcessTestHelper.triggered("Call Activity 1"));
        assertThat(listener.tracked()).anyMatch(ProcessTestHelper.triggered("StartProcess"));
        assertThat(listener.tracked()).anyMatch(ProcessTestHelper.triggered("EndProcess"));
        assertThat(listener.tracked()).anyMatch(ProcessTestHelper.triggered("StartProcess2"));
        assertThat(listener.tracked()).anyMatch(ProcessTestHelper.triggered("User Task"));
    }

    @Test
    public void testUserTaskWithBooleanOutput() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskWithBooleanOutputModel> process = UserTaskWithBooleanOutputProcess.newProcess(app);
        UserTaskWithBooleanOutputModel model = process.createModel();
        model.setIsChecked(true);
        ProcessInstance<UserTaskWithBooleanOutputModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        org.kie.kogito.internal.process.workitem.KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameter("ActorId")).isEqualTo("john");
        HashMap<String, Object> output = new HashMap<>();
        output.put("isCheckedCheckbox", true);
        processInstance.completeWorkItem(workItem.getStringId(), output);
        assertThat(processInstance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testUserTaskWithSimData() {
        Application app = ProcessTestHelper.newApplication();
        TestUserTaskWorkItemHandler workItemHandler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskWithSimulationMetaDataModel> processDefinition = UserTaskWithSimulationMetaDataProcess.newProcess(app);
        UserTaskWithSimulationMetaDataModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<UserTaskWithSimulationMetaDataModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameter("ActorId")).isEqualTo("john");
        instance.completeWorkItem(workItem.getStringId(), emptyMap(), SecurityPolicy.of("john", Collections.emptyList()));
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testCallActivityWithBoundaryErrorEvent() {
        Application app = ProcessTestHelper.newApplication();
        SystemOutWorkItemHandler workItemHandler = new SystemOutWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "task1", workItemHandler);
        ProcessCompletedCountDownProcessEventListener listener = new ProcessCompletedCountDownProcessEventListener();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<CallActivitySubProcessBoundaryErrorModel> callActivitySubProcessBoundaryErrorProcess = CallActivitySubProcessBoundaryErrorProcess.newProcess(app);
        org.kie.kogito.process.Process<CallActivityProcessBoundaryErrorModel> process = CallActivityProcessBoundaryErrorProcess.newProcess(app);
        CallActivityProcessBoundaryErrorModel model = process.createModel();
        ProcessInstance<CallActivityProcessBoundaryErrorModel> processInstance = process.createInstance(model);
        processInstance.start();
        assertThat(processInstance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);

        Collection<String> processNodes = process.findNodes(Objects::nonNull).stream().map(Node::getName).collect(Collectors.toSet());
        Collection<String> subProcessNodes = callActivitySubProcessBoundaryErrorProcess.findNodes(Objects::nonNull).stream()
                .map(Node::getName).collect(Collectors.toSet());
        assertThat(processNodes.containsAll(List.of("StartProcess",
                "Call Activity 1", "Boundary event", "Task Parent", "End2"))).isTrue();
        assertThat(subProcessNodes.containsAll(List.of("StartProcess", "Task 1", "End"))).isTrue();
    }

    @Test
    public void testCallActivityWithBoundaryErrorEventWithWaitState() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "task1", workItemHandler);

        org.kie.kogito.process.Process<CallActivitySubProcessBoundaryErrorModel> callActivitySubProcessBoundaryErrorProcess = CallActivitySubProcessBoundaryErrorProcess.newProcess(app);
        org.kie.kogito.process.Process<CallActivityProcessBoundaryErrorModel> process = CallActivityProcessBoundaryErrorProcess.newProcess(app);

        CallActivityProcessBoundaryErrorModel model = process.createModel();
        ProcessInstance<CallActivityProcessBoundaryErrorModel> processInstance = process.createInstance(model);

        processInstance.start();

        org.kie.kogito.internal.process.workitem.KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();

        ProcessInstance<CallActivitySubProcessBoundaryErrorModel> subProcessInstance = callActivitySubProcessBoundaryErrorProcess.instances().stream().findFirst().get();
        subProcessInstance.completeWorkItem(workItem.getStringId(), emptyMap());

        workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        processInstance.completeWorkItem(workItem.getStringId(), emptyMap());

        assertThat(findRemovedInstance(app, processInstance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

        Collection<String> processNodes = process.findNodes(Objects::nonNull).stream().map(Node::getName).collect(Collectors.toSet());
        Collection<String> subProcessNodes = callActivitySubProcessBoundaryErrorProcess.findNodes(Objects::nonNull).stream()
                .map(Node::getName).collect(Collectors.toSet());
        assertThat(processNodes.containsAll(List.of("StartProcess",
                "Call Activity 1", "Boundary event", "Task Parent", "End2"))).isTrue();
        assertThat(subProcessNodes.containsAll(List.of("StartProcess", "Task 1", "End"))).isTrue();
    }

    @Test
    @Timeout(10)
    public void testInvalidServiceTask() {
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> createKogitoProcessRuntime("BPMN2-InvalidServiceProcess.bpmn2"));
    }

    @Test
    public void testServiceTaskInterface() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        Interface1_operation1_EAID_3344916D_2BAC_4ab6_92D4_F739156D0933_Handler handler = new Interface1_operation1_EAID_3344916D_2BAC_4ab6_92D4_F739156D0933_Handler();
        ProcessTestHelper.registerHandler(app, handler.getName(), handler);
        org.kie.kogito.process.Process<ServiceTaskModel> definition = ServiceTaskProcess.newProcess(app);
        org.kie.kogito.process.ProcessInstance<ServiceTaskModel> instance = definition.createInstance(definition.createModel());
        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @SuppressWarnings("unchecked")
    @Test
    @Disabled("Transfomer has been disabled")
    public void testBusinessRuleTaskWithTransformation() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-RuleTaskWithTransformation.bpmn2",
                "BPMN2-RuleTaskWithTransformation.drl");

        List<String> data = new ArrayList<>();

        kruntime.getKieSession().setGlobal("data", data);

        Map<String, Object> params = new HashMap<>();
        params.put("name", "JoHn");
        KogitoProcessInstance processInstance = kruntime.startProcess("BPMN2-RuleTaskWithTransformation", params);

        assertProcessInstanceFinished(processInstance, kruntime);

        data = (List<String>) kruntime.getKieSession().getGlobal("data");
        assertThat(data).isNotNull().hasSize(1);
        assertThat(data.get(0)).isEqualTo("JOHN");

        String nameVar = getProcessVarValue(processInstance, "name");
        assertThat(nameVar).isNotNull().isEqualTo("john");

    }

    @Test
    @Disabled("Transformer has been disabled")
    public void testCallActivityWithTransformation() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-CallActivityWithTransformation.bpmn2", "BPMN2-CallActivitySubProcess.bpmn2");

        final List<KogitoProcessInstance> instances = new ArrayList<>();
        kruntime.getProcessEventManager().addEventListener(new DefaultKogitoProcessEventListener() {

            @Override
            public void beforeProcessStarted(ProcessStartedEvent event) {
                instances.add((KogitoProcessInstance) event.getProcessInstance());
            }

        });

        Map<String, Object> params = new HashMap<>();
        params.put("x", "oldValue");
        KogitoProcessInstance processInstance = kruntime.startProcess("ParentProcess", params);
        assertProcessInstanceCompleted(processInstance);

        assertThat(instances).hasSize(2);
        // assert variables of parent process, first in start (input transformation, then on end output transformation)
        assertThat(((KogitoWorkflowProcessInstance) instances.get(0)).getVariable("x")).isEqualTo("oldValue");
        assertThat(((KogitoWorkflowProcessInstance) instances.get(0)).getVariable("y")).isEqualTo("NEW VALUE");
        // assert variables of subprocess, first in start (input transformation, then on end output transformation)
        assertThat(((KogitoWorkflowProcessInstance) instances.get(1)).getVariable("subX")).isEqualTo("OLDVALUE");
        assertThat(((KogitoWorkflowProcessInstance) instances.get(1)).getVariable("subY")).isEqualTo("new value");
    }

    @Test
    public void testServiceTaskWithMvelTransformation() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "org.jbpm.bpmn2.objects.HelloService_hello__2_Handler", new HelloService_hello__2_Handler());
        org.kie.kogito.process.Process<ServiceProcessWithMvelTransformationModel> definition = ServiceProcessWithMvelTransformationProcess.newProcess(app);
        ServiceProcessWithMvelTransformationModel model = definition.createModel();

        model.setS("JoHn");

        org.kie.kogito.process.ProcessInstance<ServiceProcessWithMvelTransformationModel> instance = definition.createInstance(model);
        instance.start();

        assertThat(instance.variables().getS()).isEqualTo("hello john!");

    }

    @Test
    public void testServiceTaskWithMvelCollectionTransformation() throws Exception {

        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "org.jbpm.bpmn2.objects.HelloService_hello__2_Handler", new HelloService_hello__2_Handler());
        org.kie.kogito.process.Process<ServiceProcessWithMvelCollectionTransformationModel> definition = ServiceProcessWithMvelCollectionTransformationProcess.newProcess(app);
        ServiceProcessWithMvelCollectionTransformationModel model = definition.createModel();

        model.setS("john,poul,mary");

        org.kie.kogito.process.ProcessInstance<ServiceProcessWithMvelCollectionTransformationModel> instance = definition.createInstance(model);
        instance.start();

        List<String> result = (List<String>) instance.variables().getList();
        assertThat(result).hasSize(3);

    }

    @Test
    public void testServiceTaskWithMvelJaxbTransformation() throws Exception {
        HelloService.VALIDATE_STRING = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><person><id>123</id><name>john</name></person>";

        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "org.jbpm.bpmn2.objects.HelloService_validate__2_Handler", new HelloService_validate__2_Handler());
        org.kie.kogito.process.Process<ServiceProcessWithMvelJaxbTransformationModel> definition = ServiceProcessWithMvelJaxbTransformationProcess.newProcess(app);
        ServiceProcessWithMvelJaxbTransformationModel model = definition.createModel();

        Person person = new Person();
        person.setId(123);
        person.setName("john");
        model.setS(person);

        org.kie.kogito.process.ProcessInstance<ServiceProcessWithMvelJaxbTransformationModel> instance = definition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testErrorBetweenProcessesProcess() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void afterProcessCompleted(ProcessCompletedEvent event) {
                if ("ErrorsBetweenSubProcess".equals(event.getProcessInstance().getProcessId())) {
                    assertThat(event.getProcessInstance().getState()).isEqualTo(ProcessInstance.STATE_ABORTED);
                }
            }
        });
        ErrorsBetweenSubProcessProcess.newProcess(app);
        org.kie.kogito.process.Process<ErrorsBetweenProcessModel> process = ErrorsBetweenProcessProcess.newProcess(app);
        ErrorsBetweenProcessModel model = process.createModel();
        model.setTipoEvento("error");
        model.setPasoVariable(3);
        ProcessInstance<ErrorsBetweenProcessModel> processInstance = process.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

        assertThat(processInstance.variables().getEvent()).isEqualTo("error desde Subproceso");
    }

    @Test
    public void testProcessCustomDescriptionMetaData() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<ProcessCustomDescriptionMetaDataModel> process = ProcessCustomDescriptionMetaDataProcess.newProcess(app);
        ProcessCustomDescriptionMetaDataModel model = process.createModel();
        ProcessInstance<ProcessCustomDescriptionMetaDataModel> processInstance = process.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        String description = processInstance.description();
        assertThat(description).isNotNull().isEqualTo("my process with description");
    }

    @Test
    public void testProcessVariableCustomDescriptionMetaData() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<ProcessVariableCustomDescriptionMetaDataModel> process = ProcessVariableCustomDescriptionMetaDataProcess.newProcess(app);
        ProcessVariableCustomDescriptionMetaDataModel model = process.createModel();
        model.setX("variable name for process");
        ProcessInstance<ProcessVariableCustomDescriptionMetaDataModel> processInstance = process.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        String description = processInstance.description();
        assertThat(description).isNotNull().isEqualTo("variable name for process");
    }

    @Test
    public void testInvalidSubProcessNoOutgoingSF() throws Exception {
        try {
            createKogitoProcessRuntime("subprocess/BPMN2-InvalidEmdeddedSubProcess.bpmn2");
            fail("Process should be invalid, there should be build errors");
        } catch (RuntimeException e) {
            // there should be build errors
        }
    }

    @Test
    public void testSubProcessWithTypeVariable() {
        Application app = ProcessTestHelper.newApplication();
        final List<String> list = new ArrayList<>();
        DefaultKogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {

            public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
                if (event.getNodeInstance().getNodeName().equals("Read Map")) {
                    list.add(event.getNodeInstance().getNodeName());
                }
            }
        };
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<SubProcessWithTypeVariableModel> process = SubProcessWithTypeVariableProcess.newProcess(app);
        SubProcessWithTypeVariableModel model = process.createModel();
        ProcessInstance<SubProcessWithTypeVariableModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(list).hasSize(2);
    }

    @Test
    public void testUserTaskParametrizedInput() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskWithParametrizedInputModel> process = UserTaskWithParametrizedInputProcess.newProcess(app);
        UserTaskWithParametrizedInputModel model = process.createModel();
        ProcessInstance<UserTaskWithParametrizedInputModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        org.kie.kogito.internal.process.workitem.KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameter("Description").toString().trim()).isEqualTo("Executing task of process instance " + processInstance.id() + " as work item with Hello");
        processInstance.completeWorkItem(workItem.getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMultipleBusinessRuleTaskWithDataInputsWithPersistence()
            throws Exception {
        kruntime = createKogitoProcessRuntime(
                "BPMN2-MultipleRuleTasksWithDataInput.bpmn2",
                "BPMN2-MultipleRuleTasks.drl");

        kruntime.getKieSession().addEventListener(new TriggerRulesEventListener(kruntime));

        List<String> listPerson = new ArrayList<>();
        List<String> listAddress = new ArrayList<>();

        kruntime.getKieSession().setGlobal("listPerson", listPerson);
        kruntime.getKieSession().setGlobal("listAddress", listAddress);

        Person person = new Person();
        person.setName("john");

        Address address = new Address();
        address.setStreet("5th avenue");

        Map<String, Object> params = new HashMap<>();
        params.put("person", person);
        params.put("address", address);
        KogitoProcessInstance processInstance = kruntime.startProcess("multiple-rule-tasks", params);

        assertThat(listPerson).hasSize(1);
        assertThat(listAddress).hasSize(1);
        assertProcessInstanceFinished(processInstance, kruntime);
    }

    @Test
    public void testSubProcessInAdHocProcess() {
        // JBPM-5374
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<SubProcessInAdHocProcessModel> processDefinition = SubProcessInAdHocProcessProcess.newProcess(app);
        SubProcessInAdHocProcessModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<SubProcessInAdHocProcessModel> instance = processDefinition.createInstance(model);
        instance.start();

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        org.kie.kogito.internal.process.workitem.KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        instance.completeWorkItem(workItem.getStringId(), emptyMap());
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testCallActivityWithDataAssignment() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<AssignmentProcessModel> assignmentProcess = AssignmentProcessProcess.newProcess(app);
        AssignmentSubProcessProcess.newProcess(app);

        AssignmentProcessModel assignmentSubProcessModel = assignmentProcess.createModel();
        assignmentSubProcessModel.setName("oldValue");
        org.kie.kogito.process.ProcessInstance<AssignmentProcessModel> instance = assignmentProcess.createInstance(assignmentSubProcessModel);
        instance.start();

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getMessage()).isEqualTo("Hello Genworth welcome to jBPMS!");
    }

    @Test
    public void testAbortChildProcessFromParentProcess() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskMainModel> main = UserTaskMainProcess.newProcess(app);
        org.kie.kogito.process.Process<UserTaskChildModel> child = UserTaskChildProcess.newProcess(app);

        UserTaskMainModel assignmentSubProcessModel = main.createModel();
        org.kie.kogito.process.ProcessInstance<UserTaskMainModel> instance = main.createInstance(assignmentSubProcessModel);
        instance.start();
        instance.abort();

        assertThat(child.instances().stream().count()).isEqualTo(0);
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testAbortFirstChildProcessFromParentProcess() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler());
        org.kie.kogito.process.Process<FlowMainModel> main = FlowMainProcess.newProcess(app);
        org.kie.kogito.process.Process<FlowChild1Model> child1 = FlowChild1Process.newProcess(app);
        org.kie.kogito.process.Process<FlowChild2Model> child2 = FlowChild2Process.newProcess(app);
        org.kie.kogito.process.Process<FlowChild3Model> child3 = FlowChild3Process.newProcess(app);

        // abort from first child
        org.kie.kogito.process.ProcessInstance<FlowMainModel> instance = main.createInstance(main.createModel());
        instance.start();
        assertThat(child1.instances().stream().count()).isEqualTo(1);
        instance.abort();

        assertThat(child1.instances().stream().count()).isEqualTo(0);
        assertThat(child2.instances().stream().count()).isEqualTo(0);
        assertThat(child3.instances().stream().count()).isEqualTo(0);
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testAbortSecondChildProcessFromParentProcess() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler());
        org.kie.kogito.process.Process<FlowMainModel> main = FlowMainProcess.newProcess(app);
        org.kie.kogito.process.Process<FlowChild1Model> child1 = FlowChild1Process.newProcess(app);
        org.kie.kogito.process.Process<FlowChild2Model> child2 = FlowChild2Process.newProcess(app);
        org.kie.kogito.process.Process<FlowChild3Model> child3 = FlowChild3Process.newProcess(app);

        org.kie.kogito.process.ProcessInstance<FlowMainModel> instance = main.createInstance(main.createModel());
        instance.start();

        assertThat(child1.instances().stream().count()).isEqualTo(1);
        org.kie.kogito.process.ProcessInstance<FlowChild1Model> childInstance1 = child1.instances().stream().findAny().get();
        ProcessTestHelper.completeWorkItem(childInstance1, emptyMap());
        assertThat(child2.instances().stream().count()).isEqualTo(1);

        instance.abort();
        assertThat(child1.instances().stream().count()).isEqualTo(0);
        assertThat(child2.instances().stream().count()).isEqualTo(0);
        assertThat(child3.instances().stream().count()).isEqualTo(0);
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testAbortThirdChildProcessFromParentProcess() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler());
        org.kie.kogito.process.Process<FlowMainModel> main = FlowMainProcess.newProcess(app);
        org.kie.kogito.process.Process<FlowChild1Model> child1 = FlowChild1Process.newProcess(app);
        org.kie.kogito.process.Process<FlowChild2Model> child2 = FlowChild2Process.newProcess(app);
        org.kie.kogito.process.Process<FlowChild3Model> child3 = FlowChild3Process.newProcess(app);

        org.kie.kogito.process.ProcessInstance<FlowMainModel> instance = main.createInstance(main.createModel());
        instance.start();

        assertThat(child1.instances().stream().count()).isEqualTo(1);
        org.kie.kogito.process.ProcessInstance<FlowChild1Model> childInstance1 = child1.instances().stream().findAny().get();
        ProcessTestHelper.completeWorkItem(childInstance1, emptyMap());

        assertThat(child2.instances().stream().count()).isEqualTo(1);
        org.kie.kogito.process.ProcessInstance<FlowChild2Model> childInstance2 = child2.instances().stream().findAny().get();
        ProcessTestHelper.completeWorkItem(childInstance2, emptyMap());

        assertThat(child3.instances().stream().count()).isEqualTo(1);
        instance.abort();
        assertThat(child1.instances().stream().count()).isEqualTo(0);
        assertThat(child2.instances().stream().count()).isEqualTo(0);
        assertThat(child3.instances().stream().count()).isEqualTo(0);
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testRetriggerCallActivityNodeInstance() {
        final String flow1NodeUniqueId = "_B45422B2-F0D4-40D2-A2BF-72BE1F23EC81";

        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler());
        org.kie.kogito.process.Process<FlowMainModel> main = FlowMainProcess.newProcess(app);
        org.kie.kogito.process.Process<FlowChild1Model> child1 = FlowChild1Process.newProcess(app);
        FlowChild2Process.newProcess(app);
        FlowChild3Process.newProcess(app);

        org.kie.kogito.process.ProcessInstance<FlowMainModel> instance = main.createInstance(main.createModel());
        instance.start();

        assertThat(child1.instances().stream().count()).isEqualTo(1);

        Collection<KogitoNodeInstance> childNodeInstances = instance.findNodes(kogitoNodeInstance -> kogitoNodeInstance.getNodeDefinitionId().equals(flow1NodeUniqueId));

        assertThat(childNodeInstances).hasSize(1);

        KogitoNodeInstance flow1NodeInstance = childNodeInstances.iterator().next();

        assertThat(flow1NodeInstance)
                .isNotNull()
                .hasFieldOrProperty("id")
                .hasFieldOrPropertyWithValue("isRetrigger", false);

        instance.retriggerNodeInstance(flow1NodeInstance.getId());

        Collection<KogitoNodeInstance> childNodeInstancesAfterRetrigger = instance.findNodes(kogitoNodeInstance -> kogitoNodeInstance.getNodeDefinitionId().equals(flow1NodeUniqueId));

        assertThat(childNodeInstancesAfterRetrigger).hasSize(1);

        KogitoNodeInstance retriggeredFlow1NodeInstance = childNodeInstancesAfterRetrigger
                .iterator()
                .next();

        assertThat(retriggeredFlow1NodeInstance)
                .isNotNull()
                .hasFieldOrPropertyWithValue("isRetrigger", true)
                .hasFieldOrProperty("id")
                .extracting("id")
                .isNotEqualTo(flow1NodeInstance.getId());

        instance.abort();
    }

    @Test
    public void testAbortChildProcessUponCompletion() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskMainModel> main = UserTaskMainProcess.newProcess(app);
        org.kie.kogito.process.Process<UserTaskChildModel> child = UserTaskChildProcess.newProcess(app);

        UserTaskMainModel assignmentSubProcessModel = main.createModel();
        org.kie.kogito.process.ProcessInstance<UserTaskMainModel> instance = main.createInstance(assignmentSubProcessModel);
        instance.start();

        assertThat(child.instances().stream().count()).isEqualTo(1);
        org.kie.kogito.process.ProcessInstance<UserTaskChildModel> childInstance = child.instances().stream().findFirst().get();

        childInstance.abort();

        assertThat(child.instances().stream().count()).isEqualTo(0);
        assertThat(findRemovedInstance(app, instance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testDMNBusinessRuleTask() throws Exception {
        kruntime = createKogitoProcessRuntime(
                "dmn/BPMN2-BusinessRuleTaskDMN.bpmn2", "dmn/0020-vacation-days.dmn");

        // first run 16, 1 and expected days is 27
        Map<String, Object> params = new HashMap<>();
        params.put("age", 16);
        params.put("yearsOfService", 1);
        KogitoProcessInstance processInstance = kruntime.startProcess("BPMN2-BusinessRuleTask", params);

        assertProcessInstanceFinished(processInstance, kruntime);
        BigDecimal vacationDays = (BigDecimal) ((KogitoWorkflowProcessInstance) processInstance).getVariable("vacationDays");
        assertThat(vacationDays).isEqualTo(BigDecimal.valueOf(27));

        // second run 44, 20 and expected days is 24
        params = new HashMap<>();
        params.put("age", 44);
        params.put("yearsOfService", 20);
        processInstance = kruntime.startProcess("BPMN2-BusinessRuleTask", params);

        assertProcessInstanceFinished(processInstance, kruntime);
        vacationDays = (BigDecimal) ((KogitoWorkflowProcessInstance) processInstance).getVariable("vacationDays");
        assertThat(vacationDays).isEqualTo(BigDecimal.valueOf(24));

        // second run 50, 30 and expected days is 30
        params = new HashMap<>();
        params.put("age", 50);
        params.put("yearsOfService", 30);
        processInstance = kruntime.startProcess("BPMN2-BusinessRuleTask", params);

        assertProcessInstanceFinished(processInstance, kruntime);
        vacationDays = (BigDecimal) ((KogitoWorkflowProcessInstance) processInstance).getVariable("vacationDays");
        assertThat(vacationDays).isEqualTo(BigDecimal.valueOf(30));
    }

    @Disabled
    @Test
    public void testDMNBusinessRuleTaskByDecisionName() throws Exception {
        kruntime = createKogitoProcessRuntime(
                "dmn/BPMN2-BusinessRuleTaskDMNByDecisionName.bpmn2", "dmn/0020-vacation-days.dmn");

        // first run 16, 1 and expected days is 5
        Map<String, Object> params = new HashMap<>();
        params.put("age", 16);
        params.put("yearsOfService", 1);
        KogitoProcessInstance processInstance = kruntime.startProcess("BPMN2-BusinessRuleTask", params);

        assertProcessInstanceFinished(processInstance, kruntime);
        BigDecimal vacationDays = (BigDecimal) ((KogitoWorkflowProcessInstance) processInstance).getVariable("vacationDays");
        assertThat(vacationDays).isEqualTo(BigDecimal.valueOf(5));
    }

    @ParameterizedTest
    @CsvSource({ "dynamic-dmn1, https://www.drools.org/kie-dmn1, Extra days case 1, 5",
            "dynamic-dmn1, https://www.drools.org/kie-dmn1, Extra days case 2, 5",
            "dynamic-dmn2, https://www.drools.org/kie-dmn2, Extra days case 1, 10",
            "dynamic-dmn2, https://www.drools.org/kie-dmn2, Extra days case 2, 10" })
    public void testDMNBusinessRuleTaskWithDynamicVariables(String dmn, String namespace, String decision, int expectedVacationDays) throws Exception {
        kruntime = createKogitoProcessRuntime(
                "dmn/BPMN2-BusinessRuleTaskDMNByDynamicVariable.bpmn2", String.format("dmn/%s.dmn", dmn));

        Map<String, Object> params = new HashMap<>();
        params.put("age", 16);
        params.put("yearsOfService", 1);
        params.put("model_name", dmn);
        params.put("namespace", namespace);
        params.put("decision_name", decision);
        KogitoProcessInstance processInstance = kruntime.startProcess("BPMN2-BusinessRuleTask", params);

        assertProcessInstanceFinished(processInstance, kruntime);
        BigDecimal vacationDays = (BigDecimal) ((KogitoWorkflowProcessInstance) processInstance).getVariable("vacationDays");
        assertThat(vacationDays).isEqualTo(BigDecimal.valueOf(expectedVacationDays));
    }

    @Disabled
    @Test
    public void testDMNBusinessRuleTaskMultipleDecisionsOutput() throws Exception {
        kruntime = createKogitoProcessRuntime(
                "dmn/BPMN2-BusinessRuleTaskDMNMultipleDecisionsOutput.bpmn2", "dmn/0020-vacation-days.dmn");

        // first run 16, 1 and expected days is 5
        Map<String, Object> params = new HashMap<>();
        params.put("age", 16);
        params.put("yearsOfService", 1);
        KogitoProcessInstance processInstance = kruntime.startProcess("BPMN2-BusinessRuleTask", params);

        assertProcessInstanceFinished(processInstance, kruntime);
        BigDecimal vacationDays = (BigDecimal) ((KogitoWorkflowProcessInstance) processInstance).getVariable("vacationDays");
        assertThat(vacationDays).isEqualTo(BigDecimal.valueOf(27));
        BigDecimal extraDays = (BigDecimal) ((KogitoWorkflowProcessInstance) processInstance).getVariable("extraDays");
        assertThat(extraDays).isEqualTo(BigDecimal.valueOf(5));
    }

    @Disabled
    @Test
    public void testDMNBusinessRuleTaskInvalidExecution() throws Exception {
        kruntime = createKogitoProcessRuntime(
                "dmn/BPMN2-BusinessRuleTaskDMNByDecisionName.bpmn2", "dmn/0020-vacation-days.dmn");
        Map<String, Object> params = new HashMap<>();
        params.put("age", 16);

        try {
            kruntime.startProcess("BPMN2-BusinessRuleTask", params);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(WorkflowRuntimeException.class);
            assertThat(e.getCause()).isInstanceOf(RuntimeException.class).hasMessageContaining("DMN result errors");
        }
    }

    @Disabled
    @Test
    public void testDMNBusinessRuleTaskModelById() throws Exception {
        kruntime = createKogitoProcessRuntime(
                "dmn/BPMN2-BusinessRuleTaskDMNModelById.bpmn2", "dmn/0020-vacation-days.dmn");

        // first run 16, 1 and expected days is 27
        Map<String, Object> params = new HashMap<>();
        params.put("age", 16);
        params.put("yearsOfService", 1);
        KogitoProcessInstance processInstance = kruntime.startProcess("BPMN2-BusinessRuleTask", params);

        assertProcessInstanceFinished(processInstance, kruntime);
        BigDecimal vacationDays = (BigDecimal) ((KogitoWorkflowProcessInstance) processInstance).getVariable("vacationDays");
        assertThat(vacationDays).isEqualTo(BigDecimal.valueOf(27));

        // second run 44, 20 and expected days is 24
        params = new HashMap<>();
        params.put("age", 44);
        params.put("yearsOfService", 20);
        processInstance = kruntime.startProcess("BPMN2-BusinessRuleTask", params);

        assertProcessInstanceFinished(processInstance, kruntime);
        vacationDays = (BigDecimal) ((KogitoWorkflowProcessInstance) processInstance).getVariable("vacationDays");
        assertThat(vacationDays).isEqualTo(BigDecimal.valueOf(24));

        // second run 50, 30 and expected days is 30
        params = new HashMap<>();
        params.put("age", 50);
        params.put("yearsOfService", 30);
        processInstance = kruntime.startProcess("BPMN2-BusinessRuleTask", params);

        assertProcessInstanceFinished(processInstance, kruntime);
        vacationDays = (BigDecimal) ((KogitoWorkflowProcessInstance) processInstance).getVariable("vacationDays");
        assertThat(vacationDays).isEqualTo(BigDecimal.valueOf(30));
    }

    @Test
    public void testBusinessRuleTaskFireLimit() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-BusinessRuleTaskLoop.bpmn2",
                "BPMN2-BusinessRuleTaskInfiniteLoop.drl");

        kruntime.getKieSession().insert(new Person());
        KogitoProcessInstance processInstance = kruntime.startProcess("BPMN2-BusinessRuleTask");

        assertThat(processInstance.getState()).isEqualTo(KogitoProcessInstance.STATE_ERROR);
        assertThat(((WorkflowProcessInstanceImpl) processInstance).getErrorMessage()).contains("Fire rule limit reached 10000");
    }

    @Test
    public void testBusinessRuleTaskFireLimitAsParameter() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-BusinessRuleTaskWithDataInputLoop.bpmn2",
                "BPMN2-BusinessRuleTaskInfiniteLoop.drl");

        kruntime.getKieSession().insert(new Person());

        Map<String, Object> parameters = Collections.singletonMap("limit", 5);

        KogitoProcessInstance processInstance = kruntime.startProcess("BPMN2-BusinessRuleTask", parameters);
        assertThat(processInstance.getState()).isEqualTo(KogitoProcessInstance.STATE_ERROR);
        assertThat(((WorkflowProcessInstanceImpl) processInstance).getErrorMessage()).contains("Fire rule limit reached 5");
    }

    @Test
    public void testScriptTaskFEEL() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        EventTrackerProcessListener tracker = new EventTrackerProcessListener();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        ProcessTestHelper.registerProcessEventListener(app, tracker);
        org.kie.kogito.process.Process<ScriptTaskFEELModel> processDefinition = ScriptTaskFEELProcess.newProcess(app);
        ScriptTaskFEELModel model = processDefinition.createModel();
        model.setName("krisv");
        Person person = new Person();
        person.setName("krisv");
        model.setPerson(person);
        ProcessInstance<ScriptTaskFEELModel> processInstance = processDefinition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.variables().getX()).isEqualTo("Entry");
        assertThat(processInstance.variables().getY()).isNull();
        ProcessTestHelper.completeWorkItem(processInstance, emptyMap());
        assertThat(processInstance.variables().getY()).isEqualTo("Exit");
        assertThat(processInstance.variables().getSurname()).isEqualTo("tester");
        Set<String> nodes = tracker.tracked().stream().map(event -> event.getNodeInstance().getNodeName()).collect(Collectors.toSet());
        assertThat(nodes.contains("Script1")).isTrue();
    }

    @Test
    public void testBusinessRuleTaskException() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-BusinessRuleTask.bpmn2",
                "BPMN2-BusinessRuleTaskWithException.drl");

        kruntime.getKieSession().insert(new Person());
        KogitoProcessInstance processInstance = kruntime.startProcess("BPMN2-BusinessRuleTask");

        assertThat(processInstance.getState()).isEqualTo(KogitoProcessInstance.STATE_ERROR);
        assertThat(((WorkflowProcessInstanceImpl) processInstance).getErrorMessage()).contains("On purpose");
    }

    @Test
    public void testXORWithSameTargetProcess() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<XORSameTargetModel> processDefinition = XORSameTargetProcess.newProcess(app);
        XORSameTargetModel model = processDefinition.createModel();
        model.setChoice(1);
        org.kie.kogito.process.ProcessInstance<XORSameTargetModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        processDefinition = XORSameTargetProcess.newProcess(app);
        model = processDefinition.createModel();
        model.setChoice(2);
        instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testUserTaskWithExpressionsForIO() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskWithIOexpressionModel> processDefinition = UserTaskWithIOexpressionProcess.newProcess(app);
        UserTaskWithIOexpressionModel model = processDefinition.createModel();
        model.setPerson(new Person("john"));
        org.kie.kogito.process.ProcessInstance<UserTaskWithIOexpressionModel> instance = processDefinition.createInstance(model);
        instance.start();

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        org.kie.kogito.internal.process.workitem.KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameter("ActorId")).isEqualTo("john");
        assertThat(workItem.getParameter("personName")).isEqualTo("john");
        instance.completeWorkItem(workItem.getStringId(), Collections.singletonMap("personAge", 50));
        Person person = instance.variables().getPerson();
        assertThat(person.getAge()).isEqualTo(50);
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testCallActivitykWithExpressionsForIO() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        CallActivitySubProcessProcess.newProcess(app);
        org.kie.kogito.process.Process<CallActivityWithIOexpressionModel> processDefinition = CallActivityWithIOexpressionProcess.newProcess(app);
        CallActivityWithIOexpressionModel model = processDefinition.createModel();
        model.setPerson(new Person("john"));
        org.kie.kogito.process.ProcessInstance<CallActivityWithIOexpressionModel> instance = processDefinition.createInstance(model);
        instance.start();

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        Person person = instance.variables().getPerson();
        assertThat(person.getName()).isEqualTo("new value");
        org.kie.kogito.internal.process.workitem.KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameter("ActorId")).isEqualTo("krisv");
        instance.completeWorkItem(workItem.getStringId(), emptyMap());
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testCallSubprocessWithGroup() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);

        org.kie.kogito.process.Process<MainGroupAssignmentModel> processDefinition = MainGroupAssignmentProcess.newProcess(app);
        org.kie.kogito.process.Process<SubprocessGroupAssignmentModel> subprocessGroupAssignmentProcess = SubprocessGroupAssignmentProcess.newProcess(app);

        MainGroupAssignmentModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<MainGroupAssignmentModel> instance = processDefinition.createInstance(model);
        instance.start();

        ProcessInstance<SubprocessGroupAssignmentModel> subInstance = subprocessGroupAssignmentProcess.instances().stream().findFirst().get();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        assertThat(subInstance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        List<WorkItem> workItems = subInstance.workItems();
        assertThat(workItems).hasSize(1);
        for (WorkItem workItem : workItems) {
            assertThat(workItem).isNotNull();
            assertThat(workItem.getParameters().get("GroupId")).isEqualTo("GRUPA TESTOWA");
            subInstance.completeWorkItem(workItem.getId(), emptyMap());
        }
        assertThat(subInstance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);

        subInstance = subprocessGroupAssignmentProcess.instances().stream().findFirst().get();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        assertThat(subInstance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        workItems = subInstance.workItems();
        assertThat(workItems).hasSize(1);
        for (WorkItem workItem : workItems) {
            assertThat(workItem).isNotNull();
            assertThat(workItem.getParameters().get("GroupId")).isEqualTo("GRUPA TESTOWA");
            subInstance.completeWorkItem(workItem.getId(), emptyMap());
        }
        assertThat(subInstance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);

        assertThat(findRemovedInstance(app, instance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    @RequirePersistence(false)
    public void testBusinessRuleTaskWithExpressionsForIO() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-BusinessRuleTaskWithDataInputIOExpression.bpmn2",
                "BPMN2-BusinessRuleTaskWithDataInput.drl");
        kruntime.getProcessEventManager().addEventListener(new RuleAwareProcessEventListener());

        Map<String, Object> params = new HashMap<>();
        params.put("person", new Person(null));
        params.put("account", new Account());
        KogitoProcessInstance processInstance = kruntime
                .startProcess("BPMN2-BusinessRuleTask", params);
        assertProcessInstanceFinished(processInstance, kruntime);
        Person person = (Person) processInstance.getVariables().get("person");
        assertThat(person.getName()).isEqualTo("john");

        Account account = (Account) processInstance.getVariables().get("account");
        assertThat(account.getPerson()).isNotNull();
    }

    @Test
    @Disabled("this was not working")
    public void testUserTaskWithAssignment() throws Exception {
        ProcessDialectRegistry.setDialect("custom", new ProcessDialect() {

            @Override
            public ReturnValueEvaluatorBuilder getReturnValueEvaluatorBuilder() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ProcessClassBuilder getProcessClassBuilder() {
                return null;
            }

            @Override
            public AssignmentBuilder getAssignmentBuilder() {
                return new AssignmentBuilder() {

                    @Override
                    public void build(PackageBuildContext context, Assignment assignment,
                            List<DataDefinition> sourceExpr, DataDefinition targetExpr) {
                        assertThat(assignment.getFrom().getExpression()).isEqualTo("from_expression");
                        assertThat(assignment.getTo().getExpression()).isEqualTo("to_expression");
                    }
                };
            }

            @Override
            public ActionBuilder getActionBuilder() {
                return null;
            }

            @Override
            public void addProcess(ProcessBuildContext context) {

            }
        });
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/activity/BPMN2-DataOutputAssignmentCustomExpressionLang.bpmn2");

        Process scriptProcess = kruntime.getKieBase().getProcess("DataOutputAssignmentCustomExpressionLang");
        assertThat(scriptProcess).isNotNull();
        Node[] nodes = ((NodeContainer) scriptProcess).getNodes();
        assertThat(nodes).hasSize(3);
        assertThat(nodes).filteredOn(n -> n instanceof WorkItemNode).allMatch(this::matchExpectedAssociationSetup);

        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);

        Map<String, Object> params = new HashMap<>();
        params.put("name", "John");
        KogitoProcessInstance processInstance = kruntime.startProcess("DataOutputAssignmentCustomExpressionLang", params);

        kruntime.abortProcessInstance(processInstance.getStringId());

        assertProcessInstanceAborted(processInstance);
    }

    protected boolean matchExpectedAssociationSetup(Node node) {
        List<DataAssociation> inputs = ((WorkItemNode) node).getInAssociations();
        List<DataAssociation> outputs = ((WorkItemNode) node).getOutAssociations();

        assertThat(inputs).hasSize(1);
        assertThat(outputs).hasSize(1);

        DataAssociation association = inputs.get(0);
        assertThat(association.getAssignments()).hasSize(1);
        assertThat(association.getSources()).hasSize(2);

        Assignment assignment = association.getAssignments().get(0);
        assertThat(assignment.getDialect()).isEqualTo("custom");
        assertThat(assignment.getFrom().getExpression()).isEqualTo("from_expression");
        assertThat(assignment.getTo().getExpression()).isEqualTo("to_expression");

        association = outputs.get(0);
        assertThat(association.getAssignments()).hasSize(1);
        assertThat(association.getSources()).hasSize(2);

        assignment = association.getAssignments().get(0);
        assertThat(assignment.getDialect()).isEqualTo("custom");
        assertThat(assignment.getFrom().getExpression()).isEqualTo("from_expression");
        assertThat(assignment.getTo().getExpression()).isEqualTo("to_expression");

        return true;
    }
}
