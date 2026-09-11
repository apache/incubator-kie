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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jbpm.bpmn2.activity.BoundarySignalEventOnTaskWithTransformationModel;
import org.jbpm.bpmn2.activity.BoundarySignalEventOnTaskWithTransformationProcess;
import org.jbpm.bpmn2.event.BoundarySignalWithNameEventOnTaskModel;
import org.jbpm.bpmn2.event.BoundarySignalWithNameEventOnTaskProcess;
import org.jbpm.bpmn2.event.BoundaryTimerCycleCronModel;
import org.jbpm.bpmn2.event.BoundaryTimerCycleCronProcess;
import org.jbpm.bpmn2.event.BoundaryTimerCycleCronVariableModel;
import org.jbpm.bpmn2.event.BoundaryTimerCycleCronVariableProcess;
import org.jbpm.bpmn2.event.BoundaryTimerCycleISOModel;
import org.jbpm.bpmn2.event.BoundaryTimerCycleISOProcess;
import org.jbpm.bpmn2.event.BoundaryTimerCycleISOVariableModel;
import org.jbpm.bpmn2.event.BoundaryTimerCycleISOVariableProcess;
import org.jbpm.bpmn2.event.MultipleBoundaryTimerCycleCronVariableModel;
import org.jbpm.bpmn2.event.MultipleBoundaryTimerCycleCronVariableProcess;
import org.jbpm.bpmn2.intermediate.*;
import org.jbpm.bpmn2.loop.MultiInstanceLoopBoundaryTimerModel;
import org.jbpm.bpmn2.loop.MultiInstanceLoopBoundaryTimerProcess;
import org.jbpm.bpmn2.loop.MultiInstanceLoopCharacteristicsProcessSequentialModel;
import org.jbpm.bpmn2.loop.MultiInstanceLoopCharacteristicsProcessSequentialProcess;
import org.jbpm.bpmn2.loop.MultiInstanceLoopCharacteristicsProcessWithOutputAndScriptsModel;
import org.jbpm.bpmn2.loop.MultiInstanceLoopCharacteristicsProcessWithOutputAndScriptsProcess;
import org.jbpm.bpmn2.loop.MultiInstanceLoopCharacteristicsTaskModel;
import org.jbpm.bpmn2.loop.MultiInstanceLoopCharacteristicsTaskProcess;
import org.jbpm.bpmn2.loop.MultiInstanceLoopCharacteristicsTaskSequentialModel;
import org.jbpm.bpmn2.loop.MultiInstanceLoopCharacteristicsTaskSequentialProcess;
import org.jbpm.bpmn2.loop.MultiInstanceLoopCharacteristicsTaskWithOutputCmpCondSequentialModel;
import org.jbpm.bpmn2.loop.MultiInstanceLoopCharacteristicsTaskWithOutputCmpCondSequentialProcess;
import org.jbpm.bpmn2.loop.MultiInstanceLoopSubprocessBoundaryTimerModel;
import org.jbpm.bpmn2.loop.MultiInstanceLoopSubprocessBoundaryTimerProcess;
import org.jbpm.bpmn2.objects.Person;
import org.jbpm.bpmn2.objects.TestUserTaskWorkItemHandler;
import org.jbpm.bpmn2.objects.TestWorkItemHandler;
import org.jbpm.bpmn2.start.IntermediateThrowEventSignalModel;
import org.jbpm.bpmn2.start.IntermediateThrowEventSignalProcess;
import org.jbpm.bpmn2.subprocess.DynamicSignalChildModel;
import org.jbpm.bpmn2.subprocess.DynamicSignalChildProcess;
import org.jbpm.bpmn2.subprocess.DynamicSignalParentModel;
import org.jbpm.bpmn2.subprocess.DynamicSignalParentProcess;
import org.jbpm.bpmn2.subprocess.EventSubprocessConditionalModel;
import org.jbpm.bpmn2.subprocess.EventSubprocessConditionalProcess;
import org.jbpm.bpmn2.subprocess.EventSubprocessMessageModel;
import org.jbpm.bpmn2.subprocess.EventSubprocessMessageProcess;
import org.jbpm.bpmn2.subprocess.EventSubprocessSignalWithTransformationModel;
import org.jbpm.bpmn2.subprocess.EventSubprocessSignalWithTransformationProcess;
import org.jbpm.bpmn2.test.RequirePersistence;
import org.jbpm.bpmn2.timer.IntermediateTimerEventMIModel;
import org.jbpm.bpmn2.timer.IntermediateTimerEventMIProcess;
import org.jbpm.bpmn2.timer.IntermediateTimerParallelGatewayModel;
import org.jbpm.bpmn2.timer.IntermediateTimerParallelGatewayProcess;
import org.jbpm.bpmn2.timer.TimerBoundaryEventCycle1Model;
import org.jbpm.bpmn2.timer.TimerBoundaryEventCycle1Process;
import org.jbpm.bpmn2.timer.TimerBoundaryEventCycle2Model;
import org.jbpm.bpmn2.timer.TimerBoundaryEventCycle2Process;
import org.jbpm.bpmn2.timer.TimerBoundaryEventCycleISOModel;
import org.jbpm.bpmn2.timer.TimerBoundaryEventCycleISOProcess;
import org.jbpm.bpmn2.timer.TimerBoundaryEventDateISOModel;
import org.jbpm.bpmn2.timer.TimerBoundaryEventDateISOProcess;
import org.jbpm.bpmn2.timer.TimerBoundaryEventDurationISOModel;
import org.jbpm.bpmn2.timer.TimerBoundaryEventDurationISOProcess;
import org.jbpm.bpmn2.timer.TimerBoundaryEventDurationModel;
import org.jbpm.bpmn2.timer.TimerBoundaryEventDurationProcess;
import org.jbpm.bpmn2.timer.TimerBoundaryEventInterruptingModel;
import org.jbpm.bpmn2.timer.TimerBoundaryEventInterruptingOnTaskCancelTimerModel;
import org.jbpm.bpmn2.timer.TimerBoundaryEventInterruptingOnTaskCancelTimerProcess;
import org.jbpm.bpmn2.timer.TimerBoundaryEventInterruptingOnTaskModel;
import org.jbpm.bpmn2.timer.TimerBoundaryEventInterruptingOnTaskProcess;
import org.jbpm.bpmn2.timer.TimerBoundaryEventInterruptingProcess;
import org.jbpm.process.core.datatype.impl.type.StringDataType;
import org.jbpm.process.instance.event.listeners.RuleAwareProcessEventListener;
import org.jbpm.process.workitem.builtin.DoNothingWorkItemHandler;
import org.jbpm.process.workitem.builtin.ReceiveTaskHandler;
import org.jbpm.process.workitem.builtin.SendTaskHandler;
import org.jbpm.process.workitem.builtin.SystemOutWorkItemHandler;
import org.jbpm.test.util.NodeLeftCountDownProcessEventListener;
import org.jbpm.test.util.ProcessCompletedCountDownProcessEventListener;
import org.jbpm.test.utils.EventTrackerProcessListener;
import org.jbpm.test.utils.ProcessTestHelper;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.kie.api.event.process.*;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.kogito.Application;
import org.kie.kogito.event.impl.MessageProducer;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.kie.kogito.internal.process.event.KogitoProcessEventListener;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.process.EventDescription;
import org.kie.kogito.process.NamedDataType;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.SignalFactory;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.process.impl.AbstractProcessInstance;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIterable;
import static org.jbpm.workflow.instance.node.TimerNodeInstance.TIMER_TRIGGERED_EVENT;
import static org.junit.jupiter.api.Assertions.*;

public class IntermediateEventTest extends JbpmBpmn2TestCase {

    private KogitoProcessEventListener LOGGING_EVENT_LISTENER = new DefaultKogitoProcessEventListener() {

        @Override
        public void afterNodeLeft(ProcessNodeLeftEvent event) {
            logger.info("After node left {}", event.getNodeInstance().getNodeName());
        }

        @Override
        public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
            logger.info("After node triggered {}", event.getNodeInstance().getNodeName());
        }

        @Override
        public void beforeNodeLeft(ProcessNodeLeftEvent event) {
            logger.info("Before node left {}", event.getNodeInstance().getNodeName());
        }

        @Override
        public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
            logger.info("Before node triggered {}", event.getNodeInstance().getNodeName());
        }
    };

    /*
     * TESTS!
     */

    @Test
    public void testBoundaryTimerCycleISO() {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener listener = new NodeLeftCountDownProcessEventListener("Send Update Timer", 3);
        ProcessTestHelper.registerHandler(app, "Human Task", new TestUserTaskWorkItemHandler());
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<BoundaryTimerCycleISOModel> definition = BoundaryTimerCycleISOProcess
                .newProcess(app);
        org.kie.kogito.process.ProcessInstance<BoundaryTimerCycleISOModel> instance = definition
                .createInstance(definition.createModel());
        instance.start();
        listener.waitTillCompleted();
        List<WorkItem> allWorkItems = instance.workItems();
        assertThat(allWorkItems).hasSize(4);
        List<WorkItem> workItems = allWorkItems.stream().filter(e -> "Finish Work".equals(e.getName())).toList();
        assertThat(workItems).hasSize(1);
        instance.completeWorkItem(workItems.get(0).getId(), Collections.emptyMap());
        assertThat(ProcessTestHelper.findRemovedInstance(app, instance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testBoundaryTimerCycleISOVariable() {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener listener = new NodeLeftCountDownProcessEventListener("Send Update Timer", 3);
        ProcessTestHelper.registerHandler(app, "Human Task", new TestUserTaskWorkItemHandler());
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<BoundaryTimerCycleISOVariableModel> definition = BoundaryTimerCycleISOVariableProcess
                .newProcess(app);
        BoundaryTimerCycleISOVariableModel model = definition.createModel();
        model.setCronStr("R3/PT0.1S");
        org.kie.kogito.process.ProcessInstance<BoundaryTimerCycleISOVariableModel> instance = definition
                .createInstance(model);
        instance.start();
        listener.waitTillCompleted();
        List<WorkItem> allWorkItems = instance.workItems();
        assertThat(allWorkItems).hasSize(4);
        List<WorkItem> workItems = allWorkItems.stream().filter(e -> "Finish Work".equals(e.getName())).toList();
        assertThat(workItems).hasSize(1);
        instance.completeWorkItem(workItems.get(0).getId(), Collections.emptyMap());
        assertThat(ProcessTestHelper.findRemovedInstance(app, instance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testTimerBoundaryEventCronCycle() {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener listener = new NodeLeftCountDownProcessEventListener("Send Update Timer", 3);
        ProcessTestHelper.registerHandler(app, "Human Task", new TestUserTaskWorkItemHandler());
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<BoundaryTimerCycleCronModel> definition = BoundaryTimerCycleCronProcess.newProcess(app);
        org.kie.kogito.process.ProcessInstance<BoundaryTimerCycleCronModel> instance = definition
                .createInstance(definition.createModel());
        instance.start();
        listener.waitTillCompleted();
        List<WorkItem> allWorkItems = instance.workItems();
        assertThat(allWorkItems).hasSize(4);
        List<WorkItem> workItems = allWorkItems.stream().filter(e -> "Finish Work".equals(e.getName())).toList();
        assertThat(workItems).hasSize(1);
        instance.completeWorkItem(workItems.get(0).getId(), Collections.emptyMap());
        assertThat(ProcessTestHelper.findRemovedInstance(app, instance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testTimerBoundaryEventCronCycleVariable() {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener listener = new NodeLeftCountDownProcessEventListener("Send Update Timer", 3);
        ProcessTestHelper.registerHandler(app, "Human Task", new TestUserTaskWorkItemHandler());
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<BoundaryTimerCycleCronVariableModel> definition = BoundaryTimerCycleCronVariableProcess.newProcess(app);
        BoundaryTimerCycleCronVariableModel model = definition.createModel();
        model.setCronStr("0/1 * * * * ?");
        org.kie.kogito.process.ProcessInstance<BoundaryTimerCycleCronVariableModel> instance = definition
                .createInstance(model);
        instance.start();
        listener.waitTillCompleted();
        List<WorkItem> allWorkItems = instance.workItems();
        assertThat(allWorkItems).hasSize(4);
        List<WorkItem> workItems = allWorkItems.stream().filter(e -> "Finish Work".equals(e.getName())).toList();
        assertThat(workItems).hasSize(1);
        instance.completeWorkItem(workItems.get(0).getId(), Collections.emptyMap());
        assertThat(ProcessTestHelper.findRemovedInstance(app, instance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMultipleTimerBoundaryEventCronCycleVariable() {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener listener = new NodeLeftCountDownProcessEventListener("Send Update Timer", 2);
        ProcessTestHelper.registerHandler(app, "Human Task", new TestUserTaskWorkItemHandler());
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<MultipleBoundaryTimerCycleCronVariableModel> definition =
                MultipleBoundaryTimerCycleCronVariableProcess.newProcess(app);
        MultipleBoundaryTimerCycleCronVariableModel model = definition.createModel();
        model.setCronStr("0/1 * * * * ?");
        org.kie.kogito.process.ProcessInstance<MultipleBoundaryTimerCycleCronVariableModel> instance =
                definition.createInstance(model);
        instance.start();
        List<WorkItem> workItems = instance.workItems();
        assertThat(workItems).isNotNull();
        assertThat(workItems.size()).isEqualTo(1);
        listener.waitTillCompleted();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        workItems = instance.workItems();
        assertThat(workItems).hasSizeGreaterThanOrEqualTo(3);
        List<WorkItem> finishWork = workItems.stream().filter(e -> "Finish Work".equals(e.getName())).toList();
        assertThat(finishWork).hasSize(1);
        instance.completeWorkItem(finishWork.get(0).getId(), Collections.emptyMap());
        assertThat(ProcessTestHelper.findRemovedInstance(app, instance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testSignalBoundaryEvent() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new TestWorkItemHandler());

        org.kie.kogito.process.Process<BoundarySignalEventOnTaskModel> signalEventOnTaskProcess = BoundarySignalEventOnTaskProcess.newProcess(app);
        org.kie.kogito.process.Process<IntermediateThrowEventSignalModel> throwEventSignalProcess = IntermediateThrowEventSignalProcess.newProcess(app);

        org.kie.kogito.process.ProcessInstance<BoundarySignalEventOnTaskModel> taskProcessInstance = signalEventOnTaskProcess.createInstance(signalEventOnTaskProcess.createModel());
        taskProcessInstance.start();

        Set<EventDescription<?>> eventDescriptions = taskProcessInstance.events();
        assertThat(eventDescriptions).hasSize(2).extracting("event").contains("MySignal");
        assertThat(eventDescriptions).extracting("eventType").contains("signal");
        assertThat(eventDescriptions).extracting("processInstanceId").contains(taskProcessInstance.id());
        assertThat(eventDescriptions).filteredOn("eventType", "signal").hasSize(1).extracting("properties", Map.class)
                .anyMatch(m -> m.containsKey("AttachedToID") && m.containsKey("AttachedToName"));

        org.kie.kogito.process.ProcessInstance<IntermediateThrowEventSignalModel> signalProcessInstance = throwEventSignalProcess.createInstance(throwEventSignalProcess.createModel());
        signalProcessInstance.start();

        assertThat(signalProcessInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(ProcessTestHelper.findRemovedInstance(app, taskProcessInstance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testSignalBoundaryNonEffectiveEvent() {
        final String signal = "signalTest";
        final AtomicBoolean eventAfterNodeLeftTriggered = new AtomicBoolean(false);
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (signal.equals(event.getNodeInstance().getNodeName())) {
                    eventAfterNodeLeftTriggered.set(true);
                }
            }
        });

        org.kie.kogito.process.Process<BoundaryEventWithNonEffectiveSignalModel> processDefinition =
                BoundaryEventWithNonEffectiveSignalProcess.newProcess(app);
        ProcessInstance<BoundaryEventWithNonEffectiveSignalModel> processInstance =
                processDefinition.createInstance(processDefinition.createModel());
        processInstance.start();
        KogitoWorkItem workItem = handler.getWorkItem();
        assertThat(workItem).isNotNull();
        processInstance.completeWorkItem(workItem.getStringId(), null);
        processInstance.send(SignalFactory.of(signal, signal));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(eventAfterNodeLeftTriggered).isTrue();
    }

    @Test
    public void testSignalBoundaryEventOnTask() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        ProcessTestHelper.registerProcessEventListener(app, LOGGING_EVENT_LISTENER);

        org.kie.kogito.process.Process<BoundarySignalEventOnTaskModel> processDefinition = BoundarySignalEventOnTaskProcess.newProcess(app);
        BoundarySignalEventOnTaskModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<BoundarySignalEventOnTaskModel> instance = processDefinition.createInstance(model);
        instance.start();

        Set<EventDescription<?>> eventDescriptions = instance.events();
        assertThat(eventDescriptions).hasSize(2).extracting("event").contains("MySignal", "workItemCompleted");
        assertThat(eventDescriptions).extracting("eventType").contains("signal", "workItem");
        assertThat(eventDescriptions).extracting("nodeId").contains("BoundaryEvent_2", "UserTask_1");
        assertThat(eventDescriptions).extracting("processInstanceId").contains(instance.id());
        assertThat(eventDescriptions).filteredOn("eventType", "signal").hasSize(1).extracting("properties", Map.class)
                .anyMatch(m -> m.containsKey("AttachedToID") && m.containsKey("AttachedToName"));
        assertThat(eventDescriptions).filteredOn("eventType", "signal").hasSize(1).extracting("nodeInstanceId")
                .containsOnlyNulls();
        assertThat(eventDescriptions).filteredOn("eventType", "workItem").hasSize(1).extracting("nodeInstanceId")
                .doesNotContainNull();

        instance.send(SignalFactory.of("MySignal", "value"));
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testSignalBoundaryEventOnTaskWithSignalName() throws Exception {

        Application app = ProcessTestHelper.newApplication();

        ProcessTestHelper.registerHandler(app, "Human Task", new TestWorkItemHandler());
        ProcessTestHelper.registerProcessEventListener(app, LOGGING_EVENT_LISTENER);
        org.kie.kogito.process.Process<BoundarySignalWithNameEventOnTaskModel> definition = BoundarySignalWithNameEventOnTaskProcess
                .newProcess(app);
        org.kie.kogito.process.ProcessInstance<BoundarySignalWithNameEventOnTaskModel> instance = definition
                .createInstance(definition.createModel());
        instance.start();

        instance.send(SignalFactory.of("MySignal", "value"));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testSignalBoundaryEventOnTaskComplete() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        ProcessTestHelper.registerProcessEventListener(app, LOGGING_EVENT_LISTENER);
        org.kie.kogito.process.Process<BoundarySignalEventOnTaskModel> processDefinition = BoundarySignalEventOnTaskProcess.newProcess(app);
        BoundarySignalEventOnTaskModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<BoundarySignalEventOnTaskModel> instance = processDefinition.createInstance(model);
        instance.start();
        KogitoWorkItem workItem = handler.getWorkItem();
        instance.completeWorkItem(workItem.getStringId(), null);
        workItem = handler.getWorkItem();
        instance.completeWorkItem(workItem.getStringId(), null);
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testSignalBoundaryEventInterrupting() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "MyTask", new DoNothingWorkItemHandler());
        org.kie.kogito.process.Process<SignalBoundaryEventInterruptingModel> processDefinition = SignalBoundaryEventInterruptingProcess.newProcess(app);
        SignalBoundaryEventInterruptingModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<SignalBoundaryEventInterruptingModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        Set<EventDescription<?>> eventDescriptions = instance.events();
        assertThat(eventDescriptions).hasSize(2).extracting("event").contains("MyMessage", "workItemCompleted");
        assertThat(eventDescriptions).extracting("eventType").contains("signal", "workItem");
        assertThat(eventDescriptions).extracting("processInstanceId").contains(instance.id());
        assertThat(eventDescriptions).filteredOn("eventType", "signal").hasSize(1).extracting("properties", Map.class)
                .anyMatch(m -> m.containsKey("AttachedToID") && m.containsKey("AttachedToName"));
        instance.send(SignalFactory.of("MyMessage", null));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testSignalIntermediateThrow() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<IntermediateThrowEventSignalModel> processDefinition = IntermediateThrowEventSignalProcess.newProcess(app);
        IntermediateThrowEventSignalModel model = processDefinition.createModel();
        model.setX("MyValue");
        org.kie.kogito.process.ProcessInstance<IntermediateThrowEventSignalModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testSignalBetweenProcesses() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<IntermediateCatchSignalSingleModel> catchSignalSingleProcess = IntermediateCatchSignalSingleProcess.newProcess(app);
        org.kie.kogito.process.Process<IntermediateThrowEventSignalModel> throwEventSignalProcess = IntermediateThrowEventSignalProcess.newProcess(app);

        org.kie.kogito.process.ProcessInstance<IntermediateCatchSignalSingleModel> signalSingleProcessInstance = catchSignalSingleProcess.createInstance(catchSignalSingleProcess.createModel());
        signalSingleProcessInstance.start();
        assertThat(signalSingleProcessInstance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        KogitoWorkItem workItem = handler.getWorkItem();
        assertThat(workItem).isNotNull();
        signalSingleProcessInstance.completeWorkItem(workItem.getStringId(), null);

        org.kie.kogito.process.ProcessInstance<IntermediateThrowEventSignalModel> throwEventSignalProcessInstance = throwEventSignalProcess.createInstance(throwEventSignalProcess.createModel());
        throwEventSignalProcessInstance.start();
        assertThat(throwEventSignalProcessInstance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThat(ProcessTestHelper.findRemovedInstance(app, signalSingleProcessInstance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEventBasedSplit() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Email1", new SystemOutWorkItemHandler());
        ProcessTestHelper.registerHandler(app, "Email2", new SystemOutWorkItemHandler());

        org.kie.kogito.process.Process<EventBasedSplitModel> processDefinition = EventBasedSplitProcess.newProcess(app);
        EventBasedSplitModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<EventBasedSplitModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        Set<EventDescription<?>> eventDescriptions = instance.events();
        assertThat(eventDescriptions).hasSize(2).extracting(EventDescription::getEvent).contains("Yes", "No");
        assertThat(eventDescriptions).extracting(EventDescription::getEventType).contains("signal");
        assertThat(eventDescriptions).extracting(EventDescription::getDataType).hasOnlyElementsOfType(NamedDataType.class)
                .extracting("dataType").hasOnlyElementsOfType(StringDataType.class);
        assertThat(eventDescriptions).extracting(EventDescription::getProcessInstanceId).contains(instance.id());
        assertThat(eventDescriptions).extracting(EventDescription::getNodeInstanceId).doesNotContainNull();

        instance.send(SignalFactory.of("Yes", "YesValue"));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

        instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        instance.send(SignalFactory.of("No", "NoValue"));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEventBasedSplitBefore() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Email1", new DoNothingWorkItemHandler());
        ProcessTestHelper.registerHandler(app, "Email2", new DoNothingWorkItemHandler());

        org.kie.kogito.process.Process<EventBasedSplitModel> processDefinition = EventBasedSplitProcess.newProcess(app);
        EventBasedSplitModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<EventBasedSplitModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        instance.send(SignalFactory.of("Yes", "YesValue"));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        instance.send(SignalFactory.of("No", "NoValue"));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testEventBasedSplitAfter() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Email1", new SystemOutWorkItemHandler());
        ProcessTestHelper.registerHandler(app, "Email2", new DoNothingWorkItemHandler());

        org.kie.kogito.process.Process<EventBasedSplitModel> processDefinition = EventBasedSplitProcess.newProcess(app);
        EventBasedSplitModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<EventBasedSplitModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        instance.send(SignalFactory.of("Yes", "YesValue"));
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);

        instance.send(SignalFactory.of("No", "NoValue"));
    }

    @Test
    @Timeout(10000L)
    public void testEventBasedSplit2() {
        ProcessCompletedCountDownProcessEventListener countDownListener = new ProcessCompletedCountDownProcessEventListener(1);
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        ProcessTestHelper.registerHandler(app, "Email1", new SystemOutWorkItemHandler());
        ProcessTestHelper.registerHandler(app, "Email2", new SystemOutWorkItemHandler());

        org.kie.kogito.process.Process<EventBasedSplit2Model> processDefinition = EventBasedSplit2Process.newProcess(app);
        EventBasedSplit2Model model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<EventBasedSplit2Model> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        Set<EventDescription<?>> eventDescriptions = instance.events();
        assertThat(eventDescriptions).hasSize(2).extracting("event").contains("Yes", TIMER_TRIGGERED_EVENT);
        assertThat(eventDescriptions).extracting("eventType").contains("signal", "timer");
        assertThat(eventDescriptions).filteredOn(i -> i.getDataType() != null).extracting("dataType")
                .hasOnlyElementsOfType(NamedDataType.class).extracting("dataType")
                .hasOnlyElementsOfType(StringDataType.class);
        assertThat(eventDescriptions).extracting("processInstanceId").contains(instance.id());
        assertThat(eventDescriptions).filteredOn("eventType", "timer").hasSize(1).extracting("properties", Map.class)
                .anyMatch(m -> m.containsKey("TimerID") && m.containsKey("Delay"));

        instance.send(SignalFactory.of("Yes", "YesValue"));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        countDownListener.reset(1);

        instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        countDownListener.await();
        assertThat(ProcessTestHelper.findRemovedInstance(app, instance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEventBasedSplit3() throws Exception {
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/intermediate/BPMN2-EventBasedSplit3.bpmn2");

        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        Person jack = new Person();
        jack.setName("Jack");
        // Yes
        KogitoProcessInstance processInstance = kruntime.startProcess("EventBasedSplit3");
        assertProcessInstanceActive(processInstance);

        Set<EventDescription<?>> eventDescriptions = processInstance.getEventDescriptions();
        assertThat(eventDescriptions).hasSize(2).extracting("event").contains("Yes");
        assertThat(eventDescriptions).extracting("eventType").contains("signal", "conditional");
        assertThat(eventDescriptions).filteredOn(i -> i.getDataType() != null).extracting("dataType")
                .hasOnlyElementsOfType(NamedDataType.class).extracting("dataType")
                .hasOnlyElementsOfType(StringDataType.class);
        assertThat(eventDescriptions).extracting("processInstanceId").contains(processInstance.getStringId());

        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        kruntime.signalEvent("Yes", "YesValue", processInstance.getStringId());
        assertProcessInstanceFinished(processInstance, kruntime);
        // Condition
        processInstance = kruntime.startProcess("EventBasedSplit3");
        assertProcessInstanceActive(processInstance);
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Email1", new SystemOutWorkItemHandler());
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Email2", new SystemOutWorkItemHandler());
        kruntime.getKieSession().insert(jack);

        assertProcessInstanceFinished(processInstance, kruntime);

    }

    @Test
    public void testEventBasedSplit4() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Email1", new SystemOutWorkItemHandler());
        ProcessTestHelper.registerHandler(app, "Email2", new SystemOutWorkItemHandler());

        org.kie.kogito.process.Process<EventBasedSplit4Model> processDefinition = EventBasedSplit4Process.newProcess(app);
        EventBasedSplit4Model model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<EventBasedSplit4Model> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        Set<EventDescription<?>> eventDescriptions = instance.events();
        assertThat(eventDescriptions).hasSize(2)
                .extracting(EventDescription::getEvent)
                .contains("Message-YesMessage", "Message-NoMessage");
        assertThat(eventDescriptions).extracting(EventDescription::getEventType)
                .contains("message", "message");
        assertThat(eventDescriptions).extracting(EventDescription::getDataType)
                .hasOnlyElementsOfType(NamedDataType.class);
        assertThat(eventDescriptions).extracting(event -> ((NamedDataType) event.getDataType()).getDataType())
                .hasOnlyElementsOfType(StringDataType.class);
        assertThat(eventDescriptions).extracting(EventDescription::getProcessInstanceId)
                .contains(instance.id());

        instance.send(SignalFactory.of("Message-YesMessage", "YesValue"));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

        instance = processDefinition.createInstance(model);
        instance.start();
        instance.send(SignalFactory.of("Message-NoMessage", "NoValue"));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEventBasedSplit5() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Email1", new SystemOutWorkItemHandler());
        ProcessTestHelper.registerHandler(app, "Email2", new SystemOutWorkItemHandler());
        ReceiveTaskHandler receiveTaskHandler = new ReceiveTaskHandler();
        ProcessTestHelper.registerHandler(app, "Receive Task", receiveTaskHandler);

        org.kie.kogito.process.Process<EventBasedSplit5Model> processDefinition = EventBasedSplit5Process.newProcess(app);
        EventBasedSplit5Model model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<EventBasedSplit5Model> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        Assertions.assertNull(instance.variables().getX());

        receiveTaskHandler.getWorkItemId().stream().findFirst().ifPresent(id -> instance.completeWorkItem(id, Map.of("Message", "YesValue")));
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getX()).isEqualTo("YesValue");

        processDefinition = EventBasedSplit5Process.newProcess(app);
        model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<EventBasedSplit5Model> instance2 = processDefinition.createInstance(model);
        instance2.start();
        assertThat(instance2.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        Assertions.assertNull(instance2.variables().getX());

        receiveTaskHandler.getWorkItemId().stream().findFirst().ifPresent(id -> instance2.completeWorkItem(id, Map.of("Message", "NoValue")));
        assertThat(instance2.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(instance2.variables().getX()).isEqualTo("NoValue");

    }

    @Test
    public void testEventBasedSplitWithSubprocess() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<ExclusiveEventBasedGatewayInSubprocessModel> processDefinition =
                ExclusiveEventBasedGatewayInSubprocessProcess.newProcess(app);
        ExclusiveEventBasedGatewayInSubprocessModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<ExclusiveEventBasedGatewayInSubprocessModel> instance =
                processDefinition.createInstance(model);
        // Stop
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        instance.send(SignalFactory.of("StopSignal", ""));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        // Continue and Stop
        instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        instance.send(SignalFactory.of("ContinueSignal", ""));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        instance.send(SignalFactory.of("StopSignal", ""));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEventSubprocessSignal() {
        String[] nodes = { "start", "User Task 1", "end", "Sub Process 1", "start-sub", "sub-script", "end-sub" };
        Application app = ProcessTestHelper.newApplication();

        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        List<String> executedNodes = new ArrayList<>();

        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName().equals("sub-script")) {
                    executedNodes.add(((KogitoNodeInstance) event.getNodeInstance()).getStringId());
                }
            }
        };

        EventTrackerProcessListener eventTrackerProcessListener = new EventTrackerProcessListener();

        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerProcessEventListener(app, eventTrackerProcessListener);

        org.kie.kogito.process.Process<EventSubprocessSignalModel> processDefinition = EventSubprocessSignalProcess.newProcess(app);
        EventSubprocessSignalModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<EventSubprocessSignalModel> instance = processDefinition.createInstance(model);

        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        Set<EventDescription<?>> eventDescriptions = instance.events();
        assertThat(eventDescriptions).hasSize(2) // Adjusted to expect two events
                .extracting(EventDescription::getEvent)
                .contains("MySignal", "workItemCompleted");
        assertThat(eventDescriptions).extracting(EventDescription::getEventType)
                .contains("signal", "workItem");
        assertThat(eventDescriptions).extracting(EventDescription::getProcessInstanceId)
                .contains(instance.id());

        for (int i = 0; i < 4; i++) {
            instance.send(SignalFactory.of("MySignal"));
            assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        }

        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        instance.completeWorkItem(workItem.getStringId(), null);

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThatIterable(eventTrackerProcessListener.tracked()).extracting(a -> a.getNodeInstance().getNodeName()).contains(nodes);
        assertThat(executedNodes).hasSize(4);
    }

    @Test
    public void testEventSubprocessSignalNested() {
        String[] nodes = { "Start", "Sub Process", "Sub Start", "Sub Sub Process", "Sub Sub Start", "Sub Sub User Task", "Sub Sub Sub Process", "start-sub", "sub-script", "end-sub", "Sub Sub End",
                "Sub End", "End" };
        Application app = ProcessTestHelper.newApplication();

        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        List<String> executedNodes = new ArrayList<>();

        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName().equals("sub-script")) {
                    executedNodes.add(((KogitoNodeInstance) event.getNodeInstance()).getStringId());
                }
            }
        };

        EventTrackerProcessListener eventTrackerProcessListener = new EventTrackerProcessListener();

        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerProcessEventListener(app, eventTrackerProcessListener);

        org.kie.kogito.process.Process<EventSubprocessSignalNestedModel> processDefinition = EventSubprocessSignalNestedProcess.newProcess(app);
        EventSubprocessSignalNestedModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<EventSubprocessSignalNestedModel> instance = processDefinition.createInstance(model);

        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        Set<EventDescription<?>> eventDescriptions = instance.events();
        assertThat(eventDescriptions).hasSize(2) // Adjusted to expect two events
                .extracting(EventDescription::getEvent)
                .contains("MySignal", "workItemCompleted");
        assertThat(eventDescriptions).extracting(EventDescription::getEventType)
                .contains("signal", "workItem");
        assertThat(eventDescriptions).extracting(EventDescription::getProcessInstanceId)
                .contains(instance.id());

        for (int i = 0; i < 4; i++) {
            instance.send(SignalFactory.of("MySignal"));
            assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        }

        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        instance.completeWorkItem(workItem.getStringId(), null);

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThatIterable(eventTrackerProcessListener.tracked()).extracting(a -> a.getNodeInstance().getNodeName()).contains(nodes);
        assertThat(executedNodes).hasSize(4);
    }

    @Test
    public void testEventSubprocessSignalWithStateNode() {
        String[] nodes = { "start", "User Task 1", "end", "Sub Process 1", "start-sub", "User Task 2", "end-sub" };
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        List<String> executedNodes = new ArrayList<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName().equals("User Task 2")) {
                    executedNodes.add(((KogitoNodeInstance) event.getNodeInstance()).getStringId());
                }
            }
        };
        EventTrackerProcessListener eventTrackerProcessListener = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerProcessEventListener(app, eventTrackerProcessListener);

        org.kie.kogito.process.Process<EventSubprocessSignalWithStateNodeModel> processDefinition = EventSubprocessSignalWithStateNodeProcess.newProcess(app);
        EventSubprocessSignalWithStateNodeModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<EventSubprocessSignalWithStateNodeModel> instance = processDefinition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItemTopProcess = workItemHandler.getWorkItem();
        instance.send(SignalFactory.of("MySignal"));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        instance.completeWorkItem(workItem.getStringId(), null);
        instance.send(SignalFactory.of("MySignal"));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        instance.completeWorkItem(workItem.getStringId(), null);
        instance.send(SignalFactory.of("MySignal"));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        instance.completeWorkItem(workItem.getStringId(), null);
        instance.send(SignalFactory.of("MySignal"));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        instance.completeWorkItem(workItem.getStringId(), null);

        assertThat(workItemTopProcess).isNotNull();
        instance.completeWorkItem(workItemTopProcess.getStringId(), null);

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThatIterable(eventTrackerProcessListener.tracked()).extracting(a -> a.getNodeInstance().getNodeName()).contains(nodes);
        assertThat(executedNodes).hasSize(4);
    }

    @Test
    public void testEventSubprocessSignalInterrupting() {
        String[] nodes = { "start", "User Task 1", "Sub Process 1", "start-sub", "Script Task 1", "end-sub" };
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        List<String> executedNodes = new ArrayList<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName().equals("Script Task 1")) {
                    executedNodes.add(((KogitoNodeInstance) event.getNodeInstance()).getStringId());
                }
            }
        };
        EventTrackerProcessListener tracker = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerProcessEventListener(app, tracker);

        org.kie.kogito.process.Process<EventSubprocessSignalInterruptingModel> processDefinition = EventSubprocessSignalInterruptingProcess.newProcess(app);
        EventSubprocessSignalInterruptingModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<EventSubprocessSignalInterruptingModel> instance = processDefinition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        instance.send(SignalFactory.of("MySignal"));

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);
        assertThat(executedNodes).hasSize(1);
        assertThatIterable(tracker.tracked()).extracting(a -> a.getNodeInstance().getNodeName()).contains(nodes);
    }

    @Test
    public void testEventSubprocessMessage() throws Exception {
        Application app = ProcessTestHelper.newApplication();

        TestUserTaskWorkItemHandler workItemHandler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        List<String> executednodes = new ArrayList<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName().equals("Script Task 1")) {
                    executednodes.add(((KogitoNodeInstance) event.getNodeInstance()).getNodeId().toExternalFormat());
                }
            }

        };
        ProcessTestHelper.registerProcessEventListener(app, listener);

        EventTrackerProcessListener trackerListener = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, trackerListener);

        org.kie.kogito.process.Process<EventSubprocessMessageModel> definition = EventSubprocessMessageProcess
                .newProcess(app);
        org.kie.kogito.process.ProcessInstance<EventSubprocessMessageModel> instance = definition
                .createInstance(definition.createModel());

        instance.start();
        Set<EventDescription<?>> eventDescriptions = instance.events();
        assertThat(eventDescriptions).hasSize(3).extracting("event").contains("Message-HelloMessage",
                "workItemCompleted", "HelloMessage");
        assertThat(eventDescriptions).extracting("eventType").contains("signal", "workItem");
        assertThat(eventDescriptions).extracting("processInstanceId").contains(instance.id());

        instance.send(SignalFactory.of("Message-HelloMessage"));
        definition.send(SignalFactory.of("Message-HelloMessage"));
        definition.send(SignalFactory.of("Message-HelloMessage"));
        definition.send(SignalFactory.of("Message-HelloMessage"));

        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        ProcessTestHelper.completeWorkItem(instance, Collections.emptyMap(), "john");

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

        assertThat(trackerListener.tracked()).anyMatch(ProcessTestHelper.triggered("start"))
                .anyMatch(ProcessTestHelper.triggered("User Task 1")).anyMatch(ProcessTestHelper.triggered("end"))
                .anyMatch(ProcessTestHelper.left("Sub Process 1"))
                .anyMatch(ProcessTestHelper.left("start-sub"))
                .anyMatch(ProcessTestHelper.triggered("Script Task 1"))
                .anyMatch(ProcessTestHelper.triggered("end-sub"));

        assertThat(executednodes).hasSize(4);

    }

    @Test
    @Timeout(10)
    public void testEventSubprocessTimer() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener(
                "Script Task 1", 1);
        EventTrackerProcessListener nodeTriggeredProcessEventListener = new EventTrackerProcessListener();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        ProcessTestHelper.registerProcessEventListener(app, nodeTriggeredProcessEventListener);

        org.kie.kogito.process.Process<EventSubprocessTimerModel> definition = EventSubprocessTimerProcess.newProcess(app);
        org.kie.kogito.process.ProcessInstance<EventSubprocessTimerModel> instance = definition.createInstance(definition.createModel());
        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        Set<EventDescription<?>> eventDescriptions = instance.events();
        assertThat(eventDescriptions).hasSize(2).extracting("event").contains("workItemCompleted",
                TIMER_TRIGGERED_EVENT);
        assertThat(eventDescriptions).extracting("eventType").contains("workItem", "timer");
        assertThat(eventDescriptions).extracting("processInstanceId").contains(instance.id());
        assertThat(eventDescriptions).filteredOn("eventType", "timer").hasSize(1).extracting("properties", Map.class)
                .anyMatch(m -> m.containsKey("TimerID") && m.containsKey("Delay"));

        countDownListener.waitTillCompleted();

        eventDescriptions = instance.events();
        assertThat(eventDescriptions).hasSize(1).extracting("event").contains("workItemCompleted");
        assertThat(eventDescriptions).extracting("eventType").contains("workItem");
        assertThat(eventDescriptions).extracting("processInstanceId").contains(instance.id());

        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        ProcessTestHelper.completeWorkItem(instance, Collections.emptyMap(), "john");

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        List<String> trackedNodes = nodeTriggeredProcessEventListener.tracked().stream()
                .map(node -> node.getNodeInstance().getNodeName()).toList();
        assertTrue(trackedNodes.containsAll(List.of("start", "User Task 1", "end", "Sub Process 1", "start-sub",
                "Script Task 1", "end-sub")));
    }

    @Test
    public void testEventSubprocessTimerCycle() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener(
                "Script Task 1", 4);
        EventTrackerProcessListener nodeTriggeredProcessEventListener = new EventTrackerProcessListener();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        ProcessTestHelper.registerProcessEventListener(app, nodeTriggeredProcessEventListener);

        org.kie.kogito.process.Process<EventSubprocessTimerCycleModel> definition = EventSubprocessTimerCycleProcess.newProcess(app);
        org.kie.kogito.process.ProcessInstance<EventSubprocessTimerCycleModel> instance = definition.createInstance(definition.createModel());
        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        Set<EventDescription<?>> eventDescriptions = instance.events();
        assertThat(eventDescriptions).hasSize(2).extracting("event").contains("workItemCompleted",
                TIMER_TRIGGERED_EVENT);
        assertThat(eventDescriptions).extracting("eventType").contains("workItem", "timer");
        assertThat(eventDescriptions).extracting("processInstanceId").contains(instance.id());
        assertThat(eventDescriptions).filteredOn("eventType", "timer").hasSize(1).extracting("properties", Map.class)
                .anyMatch(m -> m.containsKey("TimerID") && m.containsKey("Period"));

        countDownListener.waitTillCompleted();

        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        ProcessTestHelper.completeWorkItem(instance, Collections.emptyMap(), "john");

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        List<String> trackedNodes = nodeTriggeredProcessEventListener.tracked().stream()
                .map(node -> node.getNodeInstance().getNodeName()).toList();
        assertTrue(trackedNodes.containsAll(List.of("start", "User Task 1", "end", "Sub Process 1", "start-sub",
                "Script Task 1", "end-sub")));
    }

    @Test
    public void testEventSubprocessConditional() throws Exception {
        Application app = ProcessTestHelper.newApplication();

        List<String> executednodes = new ArrayList<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName().equals("Script Task 1")) {
                    executednodes.add(((KogitoNodeInstance) event.getNodeInstance()).getStringId());
                }
            }

        };
        ProcessTestHelper.registerProcessEventListener(app, listener);
        EventTrackerProcessListener trackerListener = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, trackerListener);
        TestUserTaskWorkItemHandler workItemHandler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);

        org.kie.kogito.process.Process<EventSubprocessConditionalModel> definition = EventSubprocessConditionalProcess
                .newProcess(app);
        org.kie.kogito.process.ProcessInstance<EventSubprocessConditionalModel> instance = definition
                .createInstance(definition.createModel());

        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        Person person = new Person("john");
        EventSubprocessConditionalModel model = definition.createModel();
        model.setPerson(person);
        instance.updateVariables(model);

        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();

        ProcessTestHelper.completeWorkItem(instance, Collections.emptyMap(), "john");

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

        assertThat(trackerListener.tracked()).anyMatch(ProcessTestHelper.triggered("start"))
                .anyMatch(ProcessTestHelper.triggered("User Task 1"))
                .anyMatch(ProcessTestHelper.triggered("end"))
                .anyMatch(ProcessTestHelper.left("Sub Process 1"))
                .anyMatch(ProcessTestHelper.left("start-sub"))
                .anyMatch(ProcessTestHelper.triggered("Script Task 1"))
                .anyMatch(ProcessTestHelper.triggered("end-sub"));

        assertThat(executednodes).hasSize(1);

    }

    @Test
    public void testEventSubprocessMessageWithLocalVars() {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("timer", 1);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        final Set<String> variableValues = new HashSet<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {

                @SuppressWarnings("unchecked")
                Map<String, String> variable = (Map<String, String>) event.getNodeInstance().getVariable("richiesta");
                if (variable != null) {
                    variableValues.addAll(variable.keySet());
                }
            }
        };
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<EventSubProcessWithLocalVariablesModel> process = EventSubProcessWithLocalVariablesProcess.newProcess(app);
        ProcessInstance<EventSubProcessWithLocalVariablesModel> processInstance = process.createInstance(process.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        Map<String, String> data = new HashMap<>();
        processInstance.send(SignalFactory.of("Message-MAIL", data));
        countDownListener.waitTillCompleted();
        assertThat(ProcessTestHelper.findRemovedInstance(app, processInstance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ABORTED);
        assertThat(variableValues).hasSize(2).contains("SCRIPT1", "SCRIPT2");
    }

    @Test
    public void testMessageIntermediateThrow() throws Exception {
        Application app = ProcessTestHelper.newApplication();

        ProcessTestHelper.registerHandler(app, "Send Task", new SendTaskHandler());
        IntermediateThrowEventMessageProcess definition = (IntermediateThrowEventMessageProcess) IntermediateThrowEventMessageProcess
                .newProcess(app);
        StringBuilder builder = new StringBuilder();
        definition.setProducer__2(new MessageProducer<String>() {
            @Override
            public void produce(KogitoProcessInstance pi, String eventData) {
                builder.append(eventData);
            }
        });
        IntermediateThrowEventMessageModel model = definition.createModel();

        model.setX("MyValue");

        org.kie.kogito.process.ProcessInstance<IntermediateThrowEventMessageModel> instance = definition
                .createInstance(model);
        instance.start();

        assertThat(builder.toString()).isEqualTo("MyValue");
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testMessageBoundaryEventOnTask() {
        Application app = ProcessTestHelper.newApplication();
        List<String> triggeredNodes = new ArrayList<>();

        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                triggeredNodes.add(event.getNodeInstance().getNodeName());
            }
        };
        ProcessTestHelper.registerProcessEventListener(app, listener);

        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);

        org.kie.kogito.process.Process<BoundaryMessageEventOnTaskModel> process = BoundaryMessageEventOnTaskProcess.newProcess(app);
        ProcessInstance<BoundaryMessageEventOnTaskModel> processInstance = process.createInstance(process.createModel());
        processInstance.start();

        processInstance.send(SignalFactory.of("Message-HelloMessage", "message data"));

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(triggeredNodes).containsExactlyInAnyOrder("StartProcess", "User Task", "Boundary event", "Condition met", "End2");
    }

    @Test
    public void testMessageBoundaryEventOnTaskComplete() {
        String[] nodes = { "StartProcess", "User Task", "User Task2", "End1" };
        Application app = ProcessTestHelper.newApplication();
        EventTrackerProcessListener listener = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);

        org.kie.kogito.process.Process<BoundaryMessageEventOnTaskModel> process = BoundaryMessageEventOnTaskProcess.newProcess(app);
        ProcessInstance<BoundaryMessageEventOnTaskModel> processInstance = process.createInstance(process.createModel());
        processInstance.start();

        processInstance.completeWorkItem(workItemHandler.getWorkItem().getStringId(), null);
        processInstance.send(SignalFactory.of("Message-HelloMessage", "message data"));
        processInstance.completeWorkItem(workItemHandler.getWorkItem().getStringId(), null);

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThatIterable(listener.tracked()).extracting(a -> a.getNodeInstance().getNodeName()).contains(nodes);
    }

    @Test
    public void testTimerBoundaryEventDuration() {
        Application app = ProcessTestHelper.newApplication();
        ProcessCompletedCountDownProcessEventListener countDownListener = new ProcessCompletedCountDownProcessEventListener(2);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        ProcessTestHelper.registerHandler(app, "MyTask", new DoNothingWorkItemHandler());
        org.kie.kogito.process.Process<TimerBoundaryEventDurationModel> process = TimerBoundaryEventDurationProcess.newProcess(app);
        ProcessInstance<TimerBoundaryEventDurationModel> processInstance = process.createInstance(process.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        Set<EventDescription<?>> eventDescriptions = processInstance.events();
        assertThat(eventDescriptions).hasSize(2)
                .extracting("event")
                .contains("workItemCompleted", TIMER_TRIGGERED_EVENT);
        assertThat(eventDescriptions).extracting("eventType")
                .contains("workItem", "timer");
        assertThat(eventDescriptions).extracting("processInstanceId")
                .contains(processInstance.id());
        assertThat(eventDescriptions).filteredOn("eventType", "timer")
                .hasSize(1)
                .extracting("properties", Map.class)
                .anyMatch(m -> m.containsKey("TimerID") && m.containsKey("Period"));
        countDownListener.waitTillCompleted();
        assertThat(ProcessTestHelper.findRemovedInstance(app, processInstance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testTimerBoundaryEventDurationISO() {
        Application app = ProcessTestHelper.newApplication();
        ProcessCompletedCountDownProcessEventListener countDownListener = new ProcessCompletedCountDownProcessEventListener(2);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        ProcessTestHelper.registerHandler(app, "MyTask", new DoNothingWorkItemHandler());

        org.kie.kogito.process.Process<TimerBoundaryEventDurationISOModel> definition = TimerBoundaryEventDurationISOProcess.newProcess(app);
        TimerBoundaryEventDurationISOModel model = definition.createModel();
        org.kie.kogito.process.ProcessInstance<TimerBoundaryEventDurationISOModel> processInstance = definition.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        countDownListener.waitTillCompleted();
        assertThat(ProcessTestHelper.findRemovedInstance(app, processInstance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testTimerBoundaryEventDateISO() {
        Application app = ProcessTestHelper.newApplication();
        ProcessCompletedCountDownProcessEventListener countDownListener = new ProcessCompletedCountDownProcessEventListener(2);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        ProcessTestHelper.registerHandler(app, "MyTask", new DoNothingWorkItemHandler());

        org.kie.kogito.process.Process<TimerBoundaryEventDateISOModel> definition = TimerBoundaryEventDateISOProcess.newProcess(app);
        TimerBoundaryEventDateISOModel model = definition.createModel();

        OffsetDateTime plusTwoSeconds = OffsetDateTime.now().plusSeconds(2);
        model.setDate(plusTwoSeconds.toString());

        org.kie.kogito.process.ProcessInstance<TimerBoundaryEventDateISOModel> processInstance = definition.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        countDownListener.waitTillCompleted();
        assertThat(ProcessTestHelper.findRemovedInstance(app, processInstance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testTimerBoundaryEventCycle1() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("TimerEvent", 3);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        ProcessTestHelper.registerHandler(app, "MyTask", new DoNothingWorkItemHandler());

        org.kie.kogito.process.Process<TimerBoundaryEventCycle1Model> definition = TimerBoundaryEventCycle1Process.newProcess(app);
        TimerBoundaryEventCycle1Model model = definition.createModel();
        org.kie.kogito.process.ProcessInstance<TimerBoundaryEventCycle1Model> processInstance = definition.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        countDownListener.waitTillCompleted();
        assertThat(ProcessTestHelper.findRemovedInstance(app, processInstance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testTimerBoundaryEventCycle2() {
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("TimerEvent", 3);
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "MyTask", new DoNothingWorkItemHandler());
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);

        org.kie.kogito.process.Process<TimerBoundaryEventCycle2Model> processDefinition = TimerBoundaryEventCycle2Process.newProcess(app);
        TimerBoundaryEventCycle2Model model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<TimerBoundaryEventCycle2Model> instance = processDefinition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        countDownListener.waitTillCompleted();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        instance.abort();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ABORTED);
    }

    @Test
    @RequirePersistence(false)
    public void testTimerBoundaryEventCycleISO() {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("TimerEvent", 2);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        ProcessTestHelper.registerHandler(app, "MyTask", new DoNothingWorkItemHandler());

        org.kie.kogito.process.Process<TimerBoundaryEventCycleISOModel> definition = TimerBoundaryEventCycleISOProcess.newProcess(app);
        TimerBoundaryEventCycleISOModel model = definition.createModel();

        org.kie.kogito.process.ProcessInstance<TimerBoundaryEventCycleISOModel> processInstance = definition.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        countDownListener.waitTillCompleted();
        assertThat(processInstance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        processInstance.abort();
    }

    @Test
    public void testTimerBoundaryEventInterrupting() {
        ProcessCompletedCountDownProcessEventListener countDownListener = new ProcessCompletedCountDownProcessEventListener(2);
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "MyTask", new DoNothingWorkItemHandler());
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);

        org.kie.kogito.process.Process<TimerBoundaryEventInterruptingModel> processDefinition = TimerBoundaryEventInterruptingProcess.newProcess(app);
        TimerBoundaryEventInterruptingModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<TimerBoundaryEventInterruptingModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        countDownListener.waitTillCompleted();
        logger.debug("Firing timer");
        assertThat(ProcessTestHelper.findRemovedInstance(app, instance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testTimerBoundaryEventInterruptingOnTask() {
        ProcessCompletedCountDownProcessEventListener countDownListener = new ProcessCompletedCountDownProcessEventListener(2); // Expecting 2 completion events
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new TestWorkItemHandler());
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);

        org.kie.kogito.process.Process<TimerBoundaryEventInterruptingOnTaskModel> processDefinition = TimerBoundaryEventInterruptingOnTaskProcess.newProcess(app);
        TimerBoundaryEventInterruptingOnTaskModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<TimerBoundaryEventInterruptingOnTaskModel> instance = processDefinition.createInstance(model);
        instance.start();

        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        countDownListener.waitTillCompleted();
        logger.debug("Firing timer");
        assertThat(ProcessTestHelper.findRemovedInstance(app, instance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testTimerBoundaryEventInterruptingOnTaskCancelTimer() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        org.kie.kogito.process.Process<TimerBoundaryEventInterruptingOnTaskCancelTimerModel> definition =
                TimerBoundaryEventInterruptingOnTaskCancelTimerProcess.newProcess(app);
        TimerBoundaryEventInterruptingOnTaskCancelTimerModel model = definition.createModel();
        ProcessInstance<TimerBoundaryEventInterruptingOnTaskCancelTimerModel> processInstance = definition.createInstance(model);

        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        KogitoWorkItem workItem = handler.getWorkItem();
        if (workItem != null) {
            processInstance.completeWorkItem(workItem.getStringId(), null);
        }
        workItem = handler.getWorkItem();
        if (workItem != null) {
            processInstance.completeWorkItem(workItem.getStringId(), null);
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testIntermediateCatchEventSignal() {
        Application app = ProcessTestHelper.newApplication();
        List<String> triggeredNodes = new ArrayList<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                triggeredNodes.add(event.getNodeInstance().getNodeName());
            }
        };
        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<IntermediateCatchEventSignalModel> process = IntermediateCatchEventSignalProcess.newProcess(app);
        ProcessInstance<IntermediateCatchEventSignalModel> processInstance = process.createInstance(process.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.send(SignalFactory.of("MyMessage", "SomeValue"));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(triggeredNodes).containsExactlyInAnyOrder("StartProcess", "UserTask", "Event", "event", "EndProcess");
    }

    @Test
    public void testIntermediateCatchEventMessage() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<IntermediateCatchEventMessageModel> processDefinition = IntermediateCatchEventMessageProcess.newProcess(app);
        IntermediateCatchEventMessageModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<IntermediateCatchEventMessageModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        instance.send(SignalFactory.of("Message-HelloMessage", "SomeValue"));
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testIntermediateCatchEventMessageWithRef() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<IntermediateCatchEventMessageWithRefModel> process = IntermediateCatchEventMessageWithRefProcess.newProcess(app);
        ProcessInstance<IntermediateCatchEventMessageWithRefModel> processInstance = process.createInstance(process.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        // now signal process instance
        processInstance.send(SignalFactory.of("Message-HelloMessage", "SomeValue"));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testIntermediateCatchEventTimerDuration() {

        Application app = ProcessTestHelper.newApplication();

        ProcessCompletedCountDownProcessEventListener countDownListener = new ProcessCompletedCountDownProcessEventListener(1);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        ProcessTestHelper.registerHandler(app, "Human Task", new DoNothingWorkItemHandler());

        org.kie.kogito.process.Process<IntermediateCatchEventTimerDurationModel> processDefinition = IntermediateCatchEventTimerDurationProcess.newProcess(app);
        IntermediateCatchEventTimerDurationModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<IntermediateCatchEventTimerDurationModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        countDownListener.waitTillCompleted();
        assertThat(ProcessTestHelper.findRemovedInstance(app, instance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testIntermediateCatchEventTimerDateISO() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessCompletedCountDownProcessEventListener countDownListener = new ProcessCompletedCountDownProcessEventListener(2);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        ProcessTestHelper.registerHandler(app, "Human Task", new DoNothingWorkItemHandler());

        org.kie.kogito.process.Process<IntermediateCatchEventTimerDateISOModel> definition = IntermediateCatchEventTimerDateISOProcess.newProcess(app);
        IntermediateCatchEventTimerDateISOModel model = definition.createModel();

        OffsetDateTime plusTwoSeconds = OffsetDateTime.now().plusSeconds(2);
        model.setDate(plusTwoSeconds.toString());

        ProcessInstance<IntermediateCatchEventTimerDateISOModel> processInstance = definition.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        // Wait for the timer to trigger
        countDownListener.waitTillCompleted();

        assertThat(ProcessTestHelper.findRemovedInstance(app, processInstance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testIntermediateCatchEventTimerDurationISO() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessCompletedCountDownProcessEventListener countDownListener = new ProcessCompletedCountDownProcessEventListener(2);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        ProcessTestHelper.registerHandler(app, "Human Task", new DoNothingWorkItemHandler());

        org.kie.kogito.process.Process<IntermediateCatchEventTimerDurationISOModel> definition = IntermediateCatchEventTimerDurationISOProcess.newProcess(app);
        IntermediateCatchEventTimerDurationISOModel model = definition.createModel();

        ProcessInstance<IntermediateCatchEventTimerDurationISOModel> processInstance = definition.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        // Wait for the timer to trigger
        countDownListener.waitTillCompleted();

        assertThat(ProcessTestHelper.findRemovedInstance(app, processInstance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testIntermediateCatchEventTimerCycle1() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessCompletedCountDownProcessEventListener countDownListener = new ProcessCompletedCountDownProcessEventListener(2);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        ProcessTestHelper.registerHandler(app, "Human Task", new DoNothingWorkItemHandler());

        org.kie.kogito.process.Process<IntermediateCatchEventTimerCycle1Model> definition = IntermediateCatchEventTimerCycle1Process.newProcess(app);
        IntermediateCatchEventTimerCycle1Model model = definition.createModel();

        ProcessInstance<IntermediateCatchEventTimerCycle1Model> processInstance = definition.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        // Wait for the timer to trigger
        countDownListener.waitTillCompleted();

        assertThat(ProcessTestHelper.findRemovedInstance(app, processInstance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testIntermediateCatchEventTimerCycleISO() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("timer", 5);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        ProcessTestHelper.registerHandler(app, "Human Task", new DoNothingWorkItemHandler());

        org.kie.kogito.process.Process<IntermediateCatchEventTimerCycleISOModel> definition = IntermediateCatchEventTimerCycleISOProcess.newProcess(app);
        IntermediateCatchEventTimerCycleISOModel model = definition.createModel();

        org.kie.kogito.process.ProcessInstance<IntermediateCatchEventTimerCycleISOModel> processInstance = definition.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        countDownListener.waitTillCompleted();

        assertThat(processInstance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        processInstance.abort();
    }

    @Test
    public void testIntermediateCatchEventTimerCycle2() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("timer", 3);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        ProcessTestHelper.registerHandler(app, "Human Task", new DoNothingWorkItemHandler());

        org.kie.kogito.process.Process<IntermediateCatchEventTimerCycle2Model> definition = IntermediateCatchEventTimerCycle2Process.newProcess(app);
        IntermediateCatchEventTimerCycle2Model model = definition.createModel();

        org.kie.kogito.process.ProcessInstance<IntermediateCatchEventTimerCycle2Model> processInstance = definition.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        countDownListener.waitTillCompleted();

        assertThat(processInstance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        processInstance.abort();
    }

    @Test
    public void testIntermediateCatchEventCondition() throws Exception {
        kruntime = createKogitoProcessRuntime(
                "org/jbpm/bpmn2/intermediate/BPMN2-IntermediateCatchEventCondition.bpmn2");
        KogitoProcessInstance processInstance = kruntime.startProcess("IntermediateCatchEventCondition");
        assertProcessInstanceActive(processInstance);
        // now activate condition
        Person person = new Person();
        person.setName("Jack");
        kruntime.getKieSession().insert(person);
        assertProcessInstanceFinished(processInstance, kruntime);
    }

    @Test
    public void testIntermediateCatchEventConditionFilterByProcessInstance() throws Exception {
        kruntime = createKogitoProcessRuntime(
                "org/jbpm/bpmn2/intermediate/BPMN2-IntermediateCatchEventConditionFilterByProcessInstance.bpmn2");

        Map<String, Object> params1 = new HashMap<>();
        params1.put("personId", Long.valueOf(1L));
        Person person1 = new Person();
        person1.setId(1L);
        KogitoProcessInstance pi1 = kruntime
                .createProcessInstance("IntermediateCatchEventConditionFilterByProcessInstance", params1);
        String pi1id = pi1.getStringId();

        kruntime.getKieSession().insert(pi1);
        FactHandle personHandle1 = kruntime.getKieSession().insert(person1);

        kruntime.startProcessInstance(pi1.getStringId());

        Map<String, Object> params2 = new HashMap<>();
        params2.put("personId", Long.valueOf(2L));
        Person person2 = new Person();
        person2.setId(2L);

        KogitoProcessInstance pi2 = kruntime
                .createProcessInstance("IntermediateCatchEventConditionFilterByProcessInstance", params2);
        String pi2id = pi2.getStringId();

        kruntime.getKieSession().insert(pi2);
        FactHandle personHandle2 = kruntime.getKieSession().insert(person2);

        kruntime.startProcessInstance(pi2.getStringId());

        person1.setName("John");
        kruntime.getKieSession().update(personHandle1, person1);

        // First process should be completed
        assertThat(kruntime.getProcessInstance(pi1id)).isNull();
        // Second process should NOT be completed
        assertThat(kruntime.getProcessInstance(pi2id)).isNotNull();

    }

    @Test
    @RequirePersistence(false)
    public void testIntermediateCatchEventTimerCycleWithError() {

        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("timer", 3);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);

        org.kie.kogito.process.Process<IntermediateCatchEventTimerCycleWithErrorModel> definition = IntermediateCatchEventTimerCycleWithErrorProcess.newProcess(app);
        IntermediateCatchEventTimerCycleWithErrorModel model = definition.createModel();
        model.setX(0);
        org.kie.kogito.process.ProcessInstance<IntermediateCatchEventTimerCycleWithErrorModel> processInstance = definition.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        countDownListener.waitTillCompleted();
        ((AbstractProcessInstance) processInstance).reload();
        assertThat(processInstance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        Integer xValue = processInstance.variables().getX();
        assertThat(xValue).isGreaterThan(0);
        assertThat(xValue).isLessThanOrEqualTo(3);
        processInstance.abort();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);

    }

    @Test
    @RequirePersistence
    public void testIntermediateCatchEventTimerCycleWithErrorWithPersistence() {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("timer", 2);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        ProcessTestHelper.registerHandler(app, "Human Task", new DoNothingWorkItemHandler());

        org.kie.kogito.process.Process<IntermediateCatchEventTimerCycleWithErrorModel> definition =
                IntermediateCatchEventTimerCycleWithErrorProcess.newProcess(app);

        IntermediateCatchEventTimerCycleWithErrorModel model = definition.createModel();
        ProcessInstance<IntermediateCatchEventTimerCycleWithErrorModel> processInstance = definition.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        model.setX(0);
        processInstance.updateVariables(model);

        // Wait for the timer to trigger
        countDownListener.waitTillCompleted();

        ((AbstractProcessInstance<IntermediateCatchEventTimerCycleWithErrorModel>) processInstance).reload();
        // Retrieve the updated model variables
        model = processInstance.variables();
        Integer xValue = model.getX();
        assertThat(xValue).isGreaterThanOrEqualTo(1);

        processInstance.abort();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testNoneIntermediateThrow() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<IntermediateThrowEventNoneModel> process = IntermediateThrowEventNoneProcess.newProcess(app);
        IntermediateThrowEventNoneModel model = process.createModel();
        ProcessInstance<IntermediateThrowEventNoneModel> processInstance = process.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testLinkIntermediateEvent() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<IntermediateLinkEventModel> process = IntermediateLinkEventProcess.newProcess(app);
        IntermediateLinkEventModel model = process.createModel();
        ProcessInstance<IntermediateLinkEventModel> processInstance = process.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testLinkEventCompositeProcess() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<LinkEventCompositeProcessModel> process = LinkEventCompositeProcessProcess.newProcess(app);
        LinkEventCompositeProcessModel model = process.createModel();
        ProcessInstance<LinkEventCompositeProcessModel> processInstance = process.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testConditionalBoundaryEventOnTask() throws Exception {
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/intermediate/BPMN2-BoundaryConditionalEventOnTask.bpmn2");
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task", new TestWorkItemHandler());
        KogitoProcessInstance processInstance = kruntime.startProcess("BoundaryConditionalEventOnTask");
        WorkflowProcessInstanceImpl pi = (WorkflowProcessInstanceImpl) processInstance;
        pi.reconnect();
        Person person = new Person();
        person.setName("john");
        kruntime.getKieSession().insert(person);
        assertProcessInstanceFinished(processInstance, kruntime);
        assertNodeTriggered(processInstance.getStringId(), "StartProcess", "User Task", "Boundary event",
                "Condition met", "End2");
    }

    @Test
    public void testConditionalBoundaryEventOnTaskComplete() throws Exception {
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/intermediate/BPMN2-BoundaryConditionalEventOnTask.bpmn2");
        TestWorkItemHandler handler = new TestWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task", handler);
        KogitoProcessInstance processInstance = kruntime.startProcess("BoundaryConditionalEventOnTask");

        kruntime.getKogitoWorkItemManager().completeWorkItem(handler.getWorkItem().getStringId(), null);
        Person person = new Person();
        person.setName("john");
        // as the node that boundary event is attached to has been completed insert will
        // not have any effect
        kruntime.getKieSession().insert(person);
        kruntime.getKogitoWorkItemManager().completeWorkItem(handler.getWorkItem().getStringId(), null);
        assertProcessInstanceFinished(processInstance, kruntime);
        assertNodeTriggered(processInstance.getStringId(), "StartProcess", "User Task", "User Task2", "End1");

    }

    @Test
    public void testConditionalBoundaryEventOnTaskActiveOnStartup() throws Exception {
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/intermediate/BPMN2-BoundaryConditionalEventOnTask.bpmn2");
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task", new TestWorkItemHandler());

        KogitoProcessInstance processInstance = kruntime.startProcess("BoundaryConditionalEventOnTask");
        WorkflowProcessInstanceImpl pi = (WorkflowProcessInstanceImpl) processInstance;
        pi.reconnect();
        Person person = new Person();
        person.setName("john");
        kruntime.getKieSession().insert(person);

        assertProcessInstanceCompleted(processInstance);
        assertNodeTriggered(processInstance.getStringId(), "StartProcess", "User Task", "Boundary event",
                "Condition met", "End2");

    }

    @Test
    public void testConditionalBoundaryEventInterrupting() throws Exception {
        kruntime = createKogitoProcessRuntime(
                "org/jbpm/bpmn2/intermediate/BPMN2-ConditionalBoundaryEventInterrupting.bpmn2");
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("MyTask", new DoNothingWorkItemHandler());
        KogitoProcessInstance processInstance = kruntime.startProcess("ConditionalBoundaryEventInterrupting");
        WorkflowProcessInstanceImpl pi = (WorkflowProcessInstanceImpl) processInstance;
        pi.reconnect();
        assertProcessInstanceActive(processInstance);

        Person person = new Person();
        person.setName("john");
        kruntime.getKieSession().insert(person);

        assertProcessInstanceFinished(processInstance, kruntime);
        assertNodeTriggered(processInstance.getStringId(), "StartProcess", "Hello", "StartSubProcess", "Task",
                "BoundaryEvent", "Goodbye", "EndProcess");

    }

    @Test
    public void testSignalBoundaryEventOnSubprocessTakingDifferentPaths() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<SignalBoundaryOnSubProcessModel> definition = SignalBoundaryOnSubProcessProcess.newProcess(app);

        ProcessInstance<SignalBoundaryOnSubProcessModel> processInstance = definition.createInstance(definition.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.send(SignalFactory.of("continue", null));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        processInstance = definition.createInstance(definition.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.send(SignalFactory.of("forward", null));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testIntermediateCatchEventSameSignalOnTwokruntimes() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());

        org.kie.kogito.process.Process<IntermediateCatchEventSignalModel> definition1 = IntermediateCatchEventSignalProcess
                .newProcess(app);
        org.kie.kogito.process.Process<IntermediateCatchEventSignal2Model> definition2 = IntermediateCatchEventSignal2Process
                .newProcess(app);

        org.kie.kogito.process.ProcessInstance<IntermediateCatchEventSignalModel> instance1 = definition1
                .createInstance(definition1.createModel());
        org.kie.kogito.process.ProcessInstance<IntermediateCatchEventSignal2Model> instance2 = definition2
                .createInstance(definition2.createModel());

        instance1.start();
        instance2.start();
        assertThat(instance1.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        assertThat(instance2.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        instance1.send(SignalFactory.of("MyMessage", "SomeValue"));
        assertThat(instance1.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThat(instance2.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        instance2.send(SignalFactory.of("MyMessage", "SomeValue"));
        assertThat(instance1.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThat(instance2.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

    }

    @Test
    @Disabled
    public void testIntermediateCatchEventNoIncommingConnection() throws Exception {
        try {
            kruntime = createKogitoProcessRuntime("BPMN2-IntermediateCatchEventNoIncommingConnection.bpmn2");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isNotNull();
            assertThat(e.getMessage()).contains("has no incoming connection");
        }

    }

    @Test
    public void testSignalBoundaryEventOnMultiInstanceSubprocess() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<MultiInstanceSubprocessWithBoundarySignalModel> definition = MultiInstanceSubprocessWithBoundarySignalProcess.newProcess(app);
        MultiInstanceSubprocessWithBoundarySignalModel model = definition.createModel();
        model.setApprovers(Arrays.asList("john", "john"));
        ProcessInstance<MultiInstanceSubprocessWithBoundarySignalModel> processInstance = definition.createInstance(model);

        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(2);

        processInstance.send(SignalFactory.of("Outside", null));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testSignalBoundaryEventNoInteruptOnMultiInstanceSubprocess() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<MultiInstanceSubprocessWithBoundarySignalNoInteruptingModel> definition =
                MultiInstanceSubprocessWithBoundarySignalNoInteruptingProcess.newProcess(app);
        MultiInstanceSubprocessWithBoundarySignalNoInteruptingModel model = definition.createModel();
        model.setApprovers(Arrays.asList("john", "john"));

        ProcessInstance<MultiInstanceSubprocessWithBoundarySignalNoInteruptingModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(2);

        processInstance.send(SignalFactory.of("Outside", null));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        for (KogitoWorkItem wi : workItems) {
            processInstance.completeWorkItem(wi.getStringId(), null);
        }

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testErrorBoundaryEventOnMultiInstanceSubprocess() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        org.kie.kogito.process.Process<MultiInstanceSubprocessWithBoundaryErrorModel> definition =
                MultiInstanceSubprocessWithBoundaryErrorProcess.newProcess(app);
        MultiInstanceSubprocessWithBoundaryErrorModel model = definition.createModel();
        model.setApprovers(Arrays.asList("john", "john"));
        ProcessInstance<MultiInstanceSubprocessWithBoundaryErrorModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        Set<EventDescription<?>> eventDescriptions = processInstance.events();
        assertThat(eventDescriptions).hasSize(3)
                .extracting("event")
                .contains("workItemCompleted", "Inside", "Error-_D83CFC28-3322-4ABC-A12D-83476B08C7E8-MyError");
        assertThat(eventDescriptions).extracting("eventType")
                .contains("workItem", "signal");
        assertThat(eventDescriptions).extracting("processInstanceId")
                .contains(processInstance.id());
        assertThat(eventDescriptions).filteredOn("eventType", "signal").hasSize(2)
                .extracting("properties", Map.class)
                .anyMatch(m -> m.containsKey("AttachedToID") && m.containsKey("AttachedToName"));
        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(2);
        processInstance.send(SignalFactory.of("Inside", null));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testIntermediateCatchEventSignalAndBoundarySignalEvent() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        org.kie.kogito.process.Process<BoundaryEventWithSignalsModel> process = BoundaryEventWithSignalsProcess.newProcess(app);
        BoundaryEventWithSignalsModel model = process.createModel();
        ProcessInstance<BoundaryEventWithSignalsModel> processInstance = process.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.send(SignalFactory.of("moveon", ""));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem wi = handler.getWorkItem();
        assertThat(wi).isNotNull();
        processInstance.send(SignalFactory.of("moveon", ""));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    @Disabled("Transfomer has been disabled")
    public void testSignalIntermediateThrowEventWithTransformation() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-BoundarySignalEventOnTaskbpmn2.bpmn",
                "BPMN2-IntermediateThrowEventSignalWithTransformation.bpmn2");

        TestWorkItemHandler handler = new TestWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task", handler);

        Map<String, Object> params = new HashMap<>();
        params.put("x", "john");
        KogitoProcessInstance processInstance = kruntime.startProcess("BoundarySignalOnTask");

        KogitoProcessInstance processInstance2 = kruntime.startProcess("SignalIntermediateEvent", params);
        assertProcessInstanceFinished(processInstance2, kruntime);

        assertProcessInstanceFinished(processInstance, kruntime);

        String var = getProcessVarValue(processInstance, "x");
        assertThat(var).isEqualTo("JOHN");
    }

    @Test
    public void testSignalBoundaryEventWithTransformation() throws Exception {
        Application application = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(application, "Human Task", handler);
        org.kie.kogito.process.Process<BoundarySignalEventOnTaskWithTransformationModel> processBoundary = BoundarySignalEventOnTaskWithTransformationProcess
                .newProcess(application);
        org.kie.kogito.process.Process<IntermediateThrowEventSignalModel> processIntermediate = IntermediateThrowEventSignalProcess
                .newProcess(application);

        ProcessInstance<BoundarySignalEventOnTaskWithTransformationModel> instanceBoundary = processBoundary
                .createInstance(processBoundary.createModel());
        instanceBoundary.start();
        IntermediateThrowEventSignalModel modelIntermediate = processIntermediate.createModel();
        modelIntermediate.setX("john");
        ProcessInstance<IntermediateThrowEventSignalModel> instanceIntermediate = processIntermediate
                .createInstance(modelIntermediate);
        instanceIntermediate.start();
        assertThat(instanceIntermediate).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
        Optional<WorkflowProcessInstance> impl = ProcessTestHelper.findRemovedInstance(application, instanceBoundary.id());
        assertThat(impl)
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThat(impl.get().getVariables()).containsEntry("x", "JOHN");
    }

    @Test
    public void testMessageIntermediateThrowWithTransformation() throws Exception {
        StringBuilder messageContent = new StringBuilder();
        Application application = ProcessTestHelper.newApplication();
        IntermediateThrowEventMessageWithTransformationProcess definition = (IntermediateThrowEventMessageWithTransformationProcess) IntermediateThrowEventMessageWithTransformationProcess
                .newProcess(application);
        definition.setProducer__2(new MessageProducer<String>() {
            @Override
            public void produce(KogitoProcessInstance pi, String eventData) {
                messageContent.append(eventData);
            }
        });
        IntermediateThrowEventMessageWithTransformationModel model = definition.createModel();
        model.setX("MyValue");
        ProcessInstance<IntermediateThrowEventMessageWithTransformationModel> instance = definition
                .createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(messageContent).hasToString("MYVALUE");

    }

    @Test
    public void testIntermediateCatchEventSignalWithTransformation() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        EventTrackerProcessListener listener = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<IntermediateCatchEventSignalWithTransformationModel> definition = IntermediateCatchEventSignalWithTransformationProcess
                .newProcess(app);

        org.kie.kogito.process.ProcessInstance<IntermediateCatchEventSignalWithTransformationModel> instance = definition
                .createInstance(definition.createModel());
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        instance.send(SignalFactory.of("MyMessage", "SomeValue"));

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getX()).isNotNull().isEqualTo("SOMEVALUE");
        assertThat(listener.tracked()).anyMatch(ProcessTestHelper.triggered("StartProcess"))
                .anyMatch(ProcessTestHelper.triggered("UserTask")).anyMatch(ProcessTestHelper.triggered("EndProcess"))
                .anyMatch(ProcessTestHelper.triggered("event"));

    }

    @Test
    public void testIntermediateCatchEventTimerCycle3() throws Exception {
        Application app = ProcessTestHelper.newApplication();

        final CountDownLatch latch = new CountDownLatch(3);
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void afterProcessCompleted(ProcessCompletedEvent event) {
                latch.countDown();
            }
        });
        org.kie.kogito.process.Process<IntermediateCatchEventTimerCycle3Model> definition = IntermediateCatchEventTimerCycle3Process
                .newProcess(app);
        org.kie.kogito.process.ProcessInstance<IntermediateCatchEventTimerCycle3Model> instance = definition
                .createInstance(definition.createModel());
        instance.start();
        latch.await(5, TimeUnit.SECONDS);
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        instance.abort();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testIntermediateCatchEventTimerCycleCron() throws Exception {
        Application app = ProcessTestHelper.newApplication();

        final CountDownLatch latch = new CountDownLatch(3);
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void afterProcessCompleted(ProcessCompletedEvent event) {
                latch.countDown();
            }
        });
        org.kie.kogito.process.Process<IntermediateCatchEventTimerCycleCronModel> definition = IntermediateCatchEventTimerCycleCronProcess
                .newProcess(app);
        org.kie.kogito.process.ProcessInstance<IntermediateCatchEventTimerCycleCronModel> instance = definition
                .createInstance(definition.createModel());
        instance.start();
        latch.await(5, TimeUnit.SECONDS);
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        instance.abort();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testIntermediateCatchSignalBetweenUserTasksModel() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<IntermediateCatchSignalBetweenUserTasksModel> definition = IntermediateCatchSignalBetweenUserTasksProcess
                .newProcess(app);
        org.kie.kogito.process.ProcessInstance<IntermediateCatchSignalBetweenUserTasksModel> instance = definition
                .createInstance(definition.createModel());
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        instance.send(SignalFactory.of("MySignal", null));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testIntermediateCatchEventTimerDurationWithError() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void afterProcessCompleted(ProcessCompletedEvent event) {
                latch.countDown();
            }
        });
        org.kie.kogito.process.Process<IntermediateCatchEventTimerDurationWithErrorModel> definition = IntermediateCatchEventTimerDurationWithErrorProcess
                .newProcess(app);
        IntermediateCatchEventTimerDurationWithErrorProcessInstance instance = (IntermediateCatchEventTimerDurationWithErrorProcessInstance) definition
                .createInstance(definition.createModel());

        instance.start();

        latch.await(10L, TimeUnit.SECONDS);
        Optional<WorkflowProcessInstance> impl = ProcessTestHelper.findRemovedInstance(app, instance.id());
        assertThat(impl)
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

        assertThat(impl.get().getVariables()).containsEntry("TestOK", Boolean.TRUE);

    }

    @Test
    public void testIntermediateCatchEventMessageWithTransformation() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<IntermediateCatchEventMessageWithTransformationModel> definition = IntermediateCatchEventMessageWithTransformationProcess
                .newProcess(app);

        org.kie.kogito.process.ProcessInstance<IntermediateCatchEventMessageWithTransformationModel> instance = definition
                .createInstance(definition.createModel());
        instance.start();
        instance.send(SignalFactory.of("Message-HelloMessage", "SomeValue"));

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getX()).isNotNull().isEqualTo("SOMEVALUE");

    }

    @Test

    public void testEventSubprocessSignalWithTransformation() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);

        EventTrackerProcessListener trackerListener = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, trackerListener);
        org.kie.kogito.process.Process<EventSubprocessSignalWithTransformationModel> definition = EventSubprocessSignalWithTransformationProcess
                .newProcess(app);
        org.kie.kogito.process.ProcessInstance<EventSubprocessSignalWithTransformationModel> instance = definition
                .createInstance(definition.createModel());

        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        instance.send(SignalFactory.of("MySignal", "john"));

        assertThat(trackerListener.tracked()).anyMatch(ProcessTestHelper.triggered("start"))
                .anyMatch(ProcessTestHelper.triggered("User Task 1"))
                .anyMatch(ProcessTestHelper.left("Sub Process 1"))
                .anyMatch(ProcessTestHelper.left("start-sub"))
                .anyMatch(ProcessTestHelper.triggered("end-sub"));

        assertThat(instance.variables().getX()).isNotNull().isEqualTo("JOHN");
    }

    @Test
    public void testMultipleMessageSignalSubprocess() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<MultipleMessageSignalSubprocessModel> process = MultipleMessageSignalSubprocessProcess.newProcess(app);
        MultipleMessageSignalSubprocessModel model = process.createModel();
        ProcessInstance<MultipleMessageSignalSubprocessModel> processInstance = process.createInstance(model);
        processInstance.start();
        logger.debug("Parent Process ID: " + processInstance.id());
        processInstance.send(SignalFactory.of("Message-Message 1", "Test"));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.send(SignalFactory.of("Message-Message 1", "Test"));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testIntermediateCatchEventSignalWithRef() {
        String[] nodes = { "StartProcess", "UserTask", "EndProcess", "event" };
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        EventTrackerProcessListener tracker = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, tracker);
        org.kie.kogito.process.Process<IntermediateCatchEventSignalWithRefModel> process = IntermediateCatchEventSignalWithRefProcess.newProcess(app);
        IntermediateCatchEventSignalWithRefModel model = process.createModel();
        ProcessInstance<IntermediateCatchEventSignalWithRefModel> processInstance = process.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.send(SignalFactory.of("Signal1", "SomeValue"));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThatIterable(tracker.tracked()).extracting(a -> a.getNodeInstance().getNodeName()).contains(nodes);
    }

    @Test
    public void testMultiInstanceLoopBoundaryTimer() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("timer", 2);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        TestUserTaskWorkItemHandler handler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<MultiInstanceLoopBoundaryTimerModel> definition = MultiInstanceLoopBoundaryTimerProcess.newProcess(app);
        org.kie.kogito.process.ProcessInstance<MultiInstanceLoopBoundaryTimerModel> instance = definition.createInstance(definition.createModel());
        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        countDownListener.waitTillCompleted();

        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(3);

        ProcessTestHelper.completeWorkItem(instance, "john", Collections.emptyMap());
        ProcessTestHelper.completeWorkItem(instance, "mary", Collections.emptyMap());
        ProcessTestHelper.completeWorkItem(instance, "krisv", Collections.emptyMap());

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    @Timeout(10000L)
    public void testMultiInstanceLoopSubprocessBoundaryTimer() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("Script2", 1);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        TestUserTaskWorkItemHandler handler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<MultiInstanceLoopSubprocessBoundaryTimerModel> definition = MultiInstanceLoopSubprocessBoundaryTimerProcess.newProcess(app);
        MultiInstanceLoopSubprocessBoundaryTimerModel model = definition.createModel();
        model.setMi_input(List.of("PT1S", "PT2S", "PT3S"));
        org.kie.kogito.process.ProcessInstance<MultiInstanceLoopSubprocessBoundaryTimerModel> instance = definition.createInstance(model);
        instance.start();

        countDownListener.reset(1);
        assertThat(countDownListener.await()).isTrue();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        countDownListener.reset(1);
        assertThat(countDownListener.await()).isTrue();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        countDownListener.reset(1);
        assertThat(countDownListener.await()).isTrue();
        assertThat(ProcessTestHelper.findRemovedInstance(app, instance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsProcessSequential() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        TestUserTaskWorkItemHandler handler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<MultiInstanceLoopCharacteristicsProcessSequentialModel> definition = MultiInstanceLoopCharacteristicsProcessSequentialProcess.newProcess(app);
        MultiInstanceLoopCharacteristicsProcessSequentialModel model = definition.createModel();
        model.setList(List.of(1, 2, 3));
        org.kie.kogito.process.ProcessInstance<MultiInstanceLoopCharacteristicsProcessSequentialModel> instance = definition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);

        assertThat(handler.getWorkItems()).isNotNull().hasSize(1);
        ProcessTestHelper.completeWorkItem(instance, "john", Collections.emptyMap());

        assertThat(handler.getWorkItems()).isNotNull().hasSize(1);
        ProcessTestHelper.completeWorkItem(instance, "john", Collections.emptyMap());

        assertThat(handler.getWorkItems()).isNotNull().hasSize(1);
        ProcessTestHelper.completeWorkItem(instance, "john", Collections.emptyMap());
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsProcessWithOutputAndScripts() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<MultiInstanceLoopCharacteristicsProcessWithOutputAndScriptsModel> definition =
                MultiInstanceLoopCharacteristicsProcessWithOutputAndScriptsProcess.newProcess(app);
        MultiInstanceLoopCharacteristicsProcessWithOutputAndScriptsModel model = definition.createModel();
        model.setList(new ArrayList<>(List.of("1", "2", "3")));
        model.setScriptList(new ArrayList<String>());
        org.kie.kogito.process.ProcessInstance<MultiInstanceLoopCharacteristicsProcessWithOutputAndScriptsModel> instance = definition.createInstance(model);
        instance.start();

        assertThat(instance.variables().getListOut()).containsExactly("1 changed", "2 changed", "3 changed");

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsTask() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        TestUserTaskWorkItemHandler handler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<MultiInstanceLoopCharacteristicsTaskModel> definition = MultiInstanceLoopCharacteristicsTaskProcess.newProcess(app);
        MultiInstanceLoopCharacteristicsTaskModel model = definition.createModel();
        model.setList(new ArrayList<>(List.of("1", "2", "3")));

        org.kie.kogito.process.ProcessInstance<MultiInstanceLoopCharacteristicsTaskModel> instance = definition.createInstance(model);
        instance.start();

        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(3);
        assertThat(workItems.get(0).getParameter("Item")).isEqualTo("1");
        assertThat(workItems.get(1).getParameter("Item")).isEqualTo("2");
        assertThat(workItems.get(2).getParameter("Item")).isEqualTo("3");
        ProcessTestHelper.completeWorkItem(instance, "john", Collections.emptyMap());
        ProcessTestHelper.completeWorkItem(instance, "john", Collections.emptyMap());
        ProcessTestHelper.completeWorkItem(instance, "john", Collections.emptyMap());

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsTaskSequential() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        TestUserTaskWorkItemHandler handler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<MultiInstanceLoopCharacteristicsTaskSequentialModel> definition = MultiInstanceLoopCharacteristicsTaskSequentialProcess.newProcess(app);
        MultiInstanceLoopCharacteristicsTaskSequentialModel model = definition.createModel();
        model.setList(new ArrayList<>(List.of("1", "2", "3")));

        org.kie.kogito.process.ProcessInstance<MultiInstanceLoopCharacteristicsTaskSequentialModel> instance = definition.createInstance(model);
        instance.start();

        List<String> parameters = new ArrayList<>();
        List<KogitoWorkItem> workItems = null;
        workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(1);
        parameters.add((String) workItems.get(0).getParameter("Item"));
        ProcessTestHelper.completeWorkItem(instance, "john", Collections.emptyMap());

        workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(1);
        parameters.add((String) workItems.get(0).getParameter("Item"));
        ProcessTestHelper.completeWorkItem(instance, "john", Collections.emptyMap());

        workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(1);
        parameters.add((String) workItems.get(0).getParameter("Item"));
        ProcessTestHelper.completeWorkItem(instance, "john", Collections.emptyMap());

        // order is not guaranteed
        assertThat(parameters).containsExactlyInAnyOrder("1", "2", "3");
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsTaskWithOutputCmpCondSequential() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        TestUserTaskWorkItemHandler handler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<MultiInstanceLoopCharacteristicsTaskWithOutputCmpCondSequentialModel> definition =
                MultiInstanceLoopCharacteristicsTaskWithOutputCmpCondSequentialProcess.newProcess(app);
        MultiInstanceLoopCharacteristicsTaskWithOutputCmpCondSequentialModel model = definition.createModel();
        model.setList(new ArrayList<>(List.of("1", "2", "3")));
        model.setListOut(new ArrayList());

        org.kie.kogito.process.ProcessInstance<MultiInstanceLoopCharacteristicsTaskWithOutputCmpCondSequentialModel> instance = definition.createInstance(model);
        instance.start();

        List<KogitoWorkItem> workItems = null;
        workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(1);
        assertThat(workItems.get(0).getParameter("Item")).isEqualTo("1");
        ProcessTestHelper.completeWorkItem(instance, "john", Collections.emptyMap());

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getListOut()).hasSize(1);
    }

    @Test
    public void testIntermediateTimerParallelGateway() {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener countDownListener1 = new NodeLeftCountDownProcessEventListener("Timer1", 1);
        NodeLeftCountDownProcessEventListener countDownListener2 = new NodeLeftCountDownProcessEventListener("Timer2", 1);
        NodeLeftCountDownProcessEventListener countDownListener3 = new NodeLeftCountDownProcessEventListener("Timer3", 1);
        ProcessCompletedCountDownProcessEventListener countDownProcessEventListener = new ProcessCompletedCountDownProcessEventListener();

        ProcessTestHelper.registerProcessEventListener(app, countDownListener1);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener2);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener3);
        ProcessTestHelper.registerProcessEventListener(app, countDownProcessEventListener);

        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<IntermediateTimerParallelGatewayModel> process = IntermediateTimerParallelGatewayProcess.newProcess(app);
        IntermediateTimerParallelGatewayModel model = process.createModel();
        ProcessInstance<IntermediateTimerParallelGatewayModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        countDownListener1.waitTillCompleted();
        countDownListener2.waitTillCompleted();
        countDownListener3.waitTillCompleted();
        countDownProcessEventListener.waitTillCompleted();

        assertThat(ProcessTestHelper.findRemovedInstance(app, processInstance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testIntermediateTimerEventMI() {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("After timer", 3);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);

        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<IntermediateTimerEventMIModel> process = IntermediateTimerEventMIProcess.newProcess(app);
        IntermediateTimerEventMIModel model = process.createModel();
        ProcessInstance<IntermediateTimerEventMIModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        countDownListener.waitTillCompleted();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        processInstance.abort();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testThrowIntermediateSignalWithScope() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<IntermediateThrowEventScopeModel> process = IntermediateThrowEventScopeProcess.newProcess(app);
        IntermediateThrowEventScopeModel model1 = process.createModel();
        IntermediateThrowEventScopeModel model2 = process.createModel();

        ProcessInstance<IntermediateThrowEventScopeModel> processInstance1 = process.createInstance(model1);
        ProcessInstance<IntermediateThrowEventScopeModel> processInstance2 = process.createInstance(model2);

        processInstance1.start();
        processInstance2.start();

        assertThat(processInstance1.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        assertThat(processInstance2.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<KogitoWorkItem> items = handler.getWorkItems();
        KogitoWorkItem wi1 = items.get(0);
        Map<String, Object> result = new HashMap<>();
        result.put("_output", "sending event");

        processInstance1.completeWorkItem(wi1.getStringId(), result);

        assertThat(processInstance1.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(processInstance2.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        KogitoWorkItem wi2 = items.get(1);
        processInstance2.completeWorkItem(wi2.getStringId(), result);

        assertThat(processInstance2.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testThrowEndSignalWithScope() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<EndThrowEventScopeModel> process = EndThrowEventScopeProcess.newProcess(app);
        EndThrowEventScopeModel model1 = process.createModel();
        EndThrowEventScopeModel model2 = process.createModel();

        ProcessInstance<EndThrowEventScopeModel> processInstance1 = process.createInstance(model1);
        ProcessInstance<EndThrowEventScopeModel> processInstance2 = process.createInstance(model2);

        processInstance1.start();
        processInstance2.start();

        assertThat(processInstance1.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        assertThat(processInstance2.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<KogitoWorkItem> items = handler.getWorkItems();
        KogitoWorkItem wi1 = items.get(0);
        Map<String, Object> result = new HashMap<>();
        result.put("_output", "sending event");

        processInstance1.completeWorkItem(wi1.getStringId(), result);

        assertThat(processInstance1.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(processInstance2.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        KogitoWorkItem wi2 = items.get(1);
        processInstance2.completeWorkItem(wi2.getStringId(), result);

        assertThat(processInstance2.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testThrowIntermediateSignalWithExternalScope() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        KogitoWorkItemHandler externalHandler = new DefaultKogitoWorkItemHandler() {

            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                String signal = (String) workItem.getParameter("Signal");
                workItem.getProcessInstance().signalEvent(signal, null);
                return Optional.of(this.workItemLifeCycle.newTransition("complete", workItem.getPhaseStatus(), Collections.emptyMap()));
            }

        };
        ProcessTestHelper.registerHandler(app, "External Send Task", externalHandler);
        org.kie.kogito.process.Process<IntermediateThrowEventExternalScopeModel> process = IntermediateThrowEventExternalScopeProcess.newProcess(app);
        IntermediateThrowEventExternalScopeModel model = process.createModel();
        ProcessInstance<IntermediateThrowEventExternalScopeModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        List<KogitoWorkItem> items = handler.getWorkItems();
        assertThat(items).hasSize(1);
        KogitoWorkItem wi = items.get(0);
        Map<String, Object> result = new HashMap<>();
        result.put("_output", "sending event");
        processInstance.completeWorkItem(wi.getStringId(), result);

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testIntermediateCatchEventSignalWithVariable() {
        Application app = ProcessTestHelper.newApplication();
        List<String> triggeredNodes = new ArrayList<>();

        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                triggeredNodes.add(event.getNodeInstance().getNodeName());
            }
        };

        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());

        String signalVar = "myVarSignal";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("signalName", signalVar);

        org.kie.kogito.process.Process<IntermediateCatchEventSignalWithVariableModel> process = IntermediateCatchEventSignalWithVariableProcess.newProcess(app);
        IntermediateCatchEventSignalWithVariableModel model = process.createModel();
        model.fromMap(parameters);

        ProcessInstance<IntermediateCatchEventSignalWithVariableModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        // events() must expose the resolved value, not the raw #{signalName} expression
        Set<EventDescription<?>> eventDescriptions = processInstance.events();
        assertThat(eventDescriptions)
                .hasSize(1)
                .extracting(EventDescription::getEvent).contains(signalVar);

        // a signal with a different name must not advance the instance
        processInstance.send(SignalFactory.of("wrongSignalName", "WrongValue"));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        processInstance.send(SignalFactory.of(signalVar, "SomeValue"));

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(triggeredNodes).containsExactlyInAnyOrder("StartProcess", "UserTask", "Event", "event", "EndProcess");
    }

    @Test
    public void testSignalIntermediateThrowWithVariable() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        List<String> triggeredNodes = new ArrayList<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                triggeredNodes.add(event.getNodeInstance().getNodeName());
            }
        };
        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        String signalVar = "myVarSignal";
        Map<String, Object> catchParameters = new HashMap<>();
        catchParameters.put("signalName", signalVar);
        org.kie.kogito.process.Process<IntermediateCatchEventSignalWithVariableModel> catchProcess = IntermediateCatchEventSignalWithVariableProcess.newProcess(app);
        IntermediateCatchEventSignalWithVariableModel catchModel = catchProcess.createModel();
        catchModel.fromMap(catchParameters);
        ProcessInstance<IntermediateCatchEventSignalWithVariableModel> catchProcessInstance = catchProcess.createInstance(catchModel);
        catchProcessInstance.start();
        assertThat(catchProcessInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        Map<String, Object> throwParameters = new HashMap<>();
        throwParameters.put("x", "MyValue");
        throwParameters.put("signalName", signalVar);
        org.kie.kogito.process.Process<IntermediateThrowEventSignalWithVariableModel> throwProcess = IntermediateThrowEventSignalWithVariableProcess.newProcess(app);
        IntermediateThrowEventSignalWithVariableModel throwModel = throwProcess.createModel();
        throwModel.fromMap(throwParameters);
        ProcessInstance<IntermediateThrowEventSignalWithVariableModel> throwProcessInstance = throwProcess.createInstance(throwModel);
        throwProcessInstance.start();
        assertThat(throwProcessInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(ProcessTestHelper.findRemovedInstance(app, catchProcessInstance.id()))
                .isPresent().get().extracting(WorkflowProcessInstance::getState).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThat(triggeredNodes).contains("StartProcess", "UserTask", "Event", "event", "EndProcess");
    }

    @Test
    public void testInvalidDateTimerBoundary() throws Exception {
        try {
            createKogitoProcessRuntime("timer/BPMN2-TimerBoundaryEventDateInvalid.bpmn2");
            fail("Should fail as timer expression is not valid");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Could not parse date 'abcdef'");
        }
    }

    @Test
    public void testInvalidDurationTimerBoundary() throws Exception {
        try {
            createKogitoProcessRuntime("timer/BPMN2-TimerBoundaryEventDurationInvalid.bpmn2");
            fail("Should fail as timer expression is not valid");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("Could not parse delay 'abcdef'");
        }
    }

    @Test
    public void testInvalidCycleTimerBoundary() throws Exception {
        try {
            createKogitoProcessRuntime("timer/BPMN2-TimerBoundaryEventCycleInvalid.bpmn2");
            fail("Should fail as timer expression is not valid");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("Could not parse delay 'abcdef'");
        }
    }

    @Test
    public void testIntermediateCatchEventConditionSetVariableAfter() throws Exception {
        kruntime = createKogitoProcessRuntime(
                "org/jbpm/bpmn2/intermediate/BPMN2-IntermediateCatchEventConditionSetVariableAfter.bpmn2");
        kruntime.getProcessEventManager().addEventListener(new RuleAwareProcessEventListener());
        KogitoProcessInstance processInstance = kruntime
                .startProcess("IntermediateCatchEventConditionSetVariableAfter");
        assertProcessInstanceActive(processInstance);

        kruntime.getProcessEventManager().addEventListener(new RuleAwareProcessEventListener());

        Collection<? extends Object> processInstances = kruntime.getKieSession().getObjects(object -> {
            if (object instanceof KogitoProcessInstance) {
                return true;
            }
            return false;
        });
        assertThat(processInstances).isNotNull().hasSize(1);

        // now activate condition
        Person person = new Person();
        person.setName("Jack");
        kruntime.getKieSession().insert(person);
        assertProcessInstanceFinished(processInstance, kruntime);

        processInstances = kruntime.getKieSession().getObjects(object -> {
            if (object instanceof KogitoProcessInstance) {
                return true;
            }
            return false;
        });
        assertThat(processInstances).isNotNull().isEmpty();
    }

    @Test
    public void testIntermediateCatchEventConditionRemovePIAfter() throws Exception {
        kruntime = createKogitoProcessRuntime(
                "org/jbpm/bpmn2/intermediate/BPMN2-IntermediateCatchEventCondition.bpmn2");
        kruntime.getProcessEventManager().addEventListener(new RuleAwareProcessEventListener());
        KogitoProcessInstance processInstance = kruntime.startProcess("IntermediateCatchEventCondition");
        assertProcessInstanceActive(processInstance);

        kruntime.getProcessEventManager().addEventListener(new RuleAwareProcessEventListener());

        Collection<? extends Object> processInstances = kruntime.getKieSession().getObjects(object -> {
            if (object instanceof KogitoProcessInstance) {
                return true;
            }
            return false;
        });
        assertThat(processInstances).isNotNull().hasSize(1);

        // now activate condition
        Person person = new Person();
        person.setName("Jack");
        kruntime.getKieSession().insert(person);
        assertProcessInstanceFinished(processInstance, kruntime);

        processInstances = kruntime.getKieSession().getObjects(object -> {
            if (object instanceof KogitoProcessInstance) {
                return true;
            }
            return false;
        });
        assertThat(processInstances).isNotNull().isEmpty();
    }

    @Test
    public void testEventBasedSplitWithCronTimerAndSignal() throws Exception {
        System.setProperty("jbpm.enable.multi.con", "true");
        try {
            NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener(
                    "Request photos of order in use", 1);
            NodeLeftCountDownProcessEventListener countDownListener2 = new NodeLeftCountDownProcessEventListener(
                    "Request an online review", 1);
            NodeLeftCountDownProcessEventListener countDownListener3 = new NodeLeftCountDownProcessEventListener(
                    "Send a thank you card", 1);
            NodeLeftCountDownProcessEventListener countDownListener4 = new NodeLeftCountDownProcessEventListener(
                    "Request an online review", 1);
            kruntime = createKogitoProcessRuntime("timer/BPMN2-CronTimerWithEventBasedGateway.bpmn2");

            TestWorkItemHandler handler = new TestWorkItemHandler();
            kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task", handler);
            kruntime.getProcessEventManager().addEventListener(countDownListener);
            kruntime.getProcessEventManager().addEventListener(countDownListener2);
            kruntime.getProcessEventManager().addEventListener(countDownListener3);
            kruntime.getProcessEventManager().addEventListener(countDownListener4);

            KogitoProcessInstance processInstance = kruntime.startProcess("timerWithEventBasedGateway");
            assertProcessInstanceActive(processInstance.getStringId(), kruntime);

            countDownListener.waitTillCompleted();
            logger.debug("First timer triggered");
            countDownListener2.waitTillCompleted();
            logger.debug("Second timer triggered");
            countDownListener3.waitTillCompleted();
            logger.debug("Third timer triggered");
            countDownListener4.waitTillCompleted();
            logger.debug("Fourth timer triggered");

            List<KogitoWorkItem> wi = handler.getWorkItems();
            assertThat(wi).isNotNull();
            assertThat(wi).hasSize(3);

            kruntime.abortProcessInstance(processInstance.getStringId());
        } finally {
            // clear property only as the only relevant value is when it's set to true
            System.clearProperty("jbpm.enable.multi.con");
        }
    }

    @Test
    public void testEventSubprocessWithEmbeddedSignals() throws Exception {
        Application app = ProcessTestHelper.newApplication();

        org.kie.kogito.process.Process<EventSubprocessErrorSignalEmbeddedModel> definition = EventSubprocessErrorSignalEmbeddedProcess
                .newProcess(app);
        org.kie.kogito.process.ProcessInstance<EventSubprocessErrorSignalEmbeddedModel> instance = definition
                .createInstance(definition.createModel());
        instance.start();
        instance.send(SignalFactory.of("signal1", null));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        instance.send(SignalFactory.of("signal2", null));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ACTIVE);
        instance.send(SignalFactory.of("signal3", null));
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testEventSubprocessWithExpression() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<EventSubprocessSignalExpressionModel> process = EventSubprocessSignalExpressionProcess.newProcess(app);
        EventSubprocessSignalExpressionModel model = process.createModel();
        model.setX("signalling");
        ProcessInstance<EventSubprocessSignalExpressionModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        processInstance.send(SignalFactory.of("signalling"));

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testConditionalProcessFactInsertedBefore() throws Exception {
        kruntime = createKogitoProcessRuntime(
                "org/jbpm/bpmn2/intermediate/BPMN2-IntermediateCatchEventConditionPI.bpmn2",
                "org/jbpm/bpmn2/intermediate/BPMN2-IntermediateCatchEventSignal.bpmn2");
        TestWorkItemHandler handler = new TestWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task", handler);
        Person person0 = new Person("john");
        kruntime.getKieSession().insert(person0);
        Map<String, Object> params0 = new HashMap<>();
        params0.put("name", "john");
        KogitoProcessInstance pi0 = kruntime.startProcess("IntermediateCatchEventSignal", params0);
        kruntime.getKieSession().insert(pi0);
        Person person = new Person("Jack");
        kruntime.getKieSession().insert(person);
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Poul");
        KogitoProcessInstance pi = kruntime.startProcess("IntermediateCatchEventConditionPI", params);
        kruntime.getKieSession().insert(pi);
        pi = kruntime.getProcessInstance(pi.getStringId());
        assertThat(pi).isNotNull();
        Person person2 = new Person("Poul");
        kruntime.getKieSession().insert(person2);
        pi = kruntime.getProcessInstance(pi.getStringId());
        assertThat(pi).isNull();
    }

    @Test
    public void testBoundarySignalEventOnSubprocessWithVariableResolution() {
        Application app = ProcessTestHelper.newApplication();

        org.kie.kogito.process.Process<SubprocessWithSignalEndEventAndSignalBoundaryEventModel> process =
                SubprocessWithSignalEndEventAndSignalBoundaryEventProcess.newProcess(app);

        Map<String, Object> params = new HashMap<>();
        params.put("document-ref", "signalling");
        params.put("message", "hello");

        SubprocessWithSignalEndEventAndSignalBoundaryEventModel model = process.createModel();
        model.fromMap(params);

        ProcessInstance<SubprocessWithSignalEndEventAndSignalBoundaryEventModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testSignalEndWithData() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<IntermediateThrowEventSignalWithDataModel> definition =
                IntermediateThrowEventSignalWithDataProcess.newProcess(app);
        IntermediateThrowEventSignalWithDataModel model = definition.createModel();
        ProcessInstance<IntermediateThrowEventSignalWithDataModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.send(SignalFactory.of("mysignal", null));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    void testDynamicCatchEventSignal() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<DynamicSignalParentModel> processDefinition = DynamicSignalParentProcess.newProcess(app);
        DynamicSignalParentModel model = processDefinition.createModel();
        ProcessInstance<DynamicSignalParentModel> processInstance = processDefinition.createInstance(model);
        org.kie.kogito.process.Process<DynamicSignalChildModel> childProcessDefinition = DynamicSignalChildProcess.newProcess(app);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        List<ProcessInstance<DynamicSignalChildModel>> childInstances = childProcessDefinition.instances().stream().toList();
        assertThat(childInstances).hasSize(3);
        childInstances.forEach(instance -> assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE));
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        processInstance.completeWorkItem(workItem.getStringId(), Collections.emptyMap());
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    @Disabled("On Exit not supported, see https://issues.redhat.com/browse/KOGITO-2067")
    public void testDynamicCatchEventSignalWithVariableUpdated() throws Exception {
        kruntime = createKogitoProcessRuntime("subprocess/dynamic-signal-parent.bpmn2",
                "subprocess/dynamic-signal-child.bpmn2");
        TestWorkItemHandler handler = new TestWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task", handler);
        final List<String> instances = new ArrayList<>();

        kruntime.getProcessEventManager().addEventListener(new DefaultKogitoProcessEventListener() {

            @Override
            public void beforeProcessStarted(ProcessStartedEvent event) {
                instances.add(((KogitoProcessInstance) event.getProcessInstance()).getStringId());
            }

        });

        KogitoProcessInstance processInstance = kruntime.startProcess("src.father");
        assertProcessInstanceActive(processInstance);
        assertThat(instances).hasSize(4);

        // remove the parent process instance
        instances.remove(processInstance.getStringId());

        for (String id : instances) {
            KogitoProcessInstance child = kruntime.getProcessInstance(id);
            assertProcessInstanceActive(child);
        }

        // change one child process instance variable (fatherId) to something else then
        // original fatherId
        String changeProcessInstanceId = instances.remove(0);
        Map<String, Object> updatedVariables = new HashMap<>();
        updatedVariables.put("fatherId", "999");
        kruntime.getKieSession()
                .execute(new KogitoSetProcessInstanceVariablesCommand(changeProcessInstanceId, updatedVariables));

        // now complete user task to signal all child instances to stop
        KogitoWorkItem workItem = handler.getWorkItem();
        assertThat(workItem).isNotNull();

        kruntime.getKogitoWorkItemManager().completeWorkItem(workItem.getStringId(), null);
        assertProcessInstanceFinished(processInstance, kruntime);

        for (String id : instances) {
            assertThat(kruntime.getProcessInstance(id)).as("Child process instance has not been finished.").isNull();
        }

        KogitoProcessInstance updatedChild = kruntime.getProcessInstance(changeProcessInstanceId);
        assertProcessInstanceActive(updatedChild);

        kruntime.signalEvent("stopChild:999", null, changeProcessInstanceId);
        assertProcessInstanceFinished(updatedChild, kruntime);
    }

    @RequirePersistence
    @Test
    @Disabled("On Exit not supported, see https://issues.redhat.com/browse/KOGITO-2067")
    public void testDynamicCatchEventSignalWithVariableUpdatedBroadcastSignal() throws Exception {
        kruntime = createKogitoProcessRuntime("subprocess/dynamic-signal-parent.bpmn2",
                "subprocess/dynamic-signal-child.bpmn2");
        TestWorkItemHandler handler = new TestWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task", handler);
        final List<String> instances = new ArrayList<>();

        kruntime.getProcessEventManager().addEventListener(new DefaultKogitoProcessEventListener() {

            @Override
            public void beforeProcessStarted(ProcessStartedEvent event) {
                instances.add(((KogitoProcessInstance) event.getProcessInstance()).getStringId());
            }

        });

        KogitoProcessInstance processInstance = kruntime.startProcess("src.father");
        assertProcessInstanceActive(processInstance);
        assertThat(instances).hasSize(4);

        // remove the parent process instance
        instances.remove(processInstance.getStringId());

        for (String id : instances) {
            KogitoProcessInstance child = kruntime.getProcessInstance(id);
            assertProcessInstanceActive(child);
        }

        // change one child process instance variable (fatherId) to something else then
        // original fatherId
        String changeProcessInstanceId = instances.remove(0);
        Map<String, Object> updatedVariables = new HashMap<>();
        updatedVariables.put("fatherId", "999");
        kruntime.getKieSession()
                .execute(new KogitoSetProcessInstanceVariablesCommand(changeProcessInstanceId, updatedVariables));

        // now complete user task to signal all child instances to stop
        KogitoWorkItem workItem = handler.getWorkItem();
        assertThat(workItem).isNotNull();

        kruntime.getKogitoWorkItemManager().completeWorkItem(workItem.getStringId(), null);
        assertProcessInstanceFinished(processInstance, kruntime);

        for (String id : instances) {
            assertThat(kruntime.getProcessInstance(id)).as("Child process instance has not been finished.").isNull();
        }

        KogitoProcessInstance updatedChild = kruntime.getProcessInstance(changeProcessInstanceId);
        assertProcessInstanceActive(updatedChild);

        kruntime.signalEvent("stopChild:999", null, updatedChild.getStringId());
        assertProcessInstanceFinished(updatedChild, kruntime);
    }

    @Test
    public void testSignalProjectScope() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        org.kie.kogito.process.Process<IntermediateCatchEventSignalForScopeModel> catchEventSignalForScope = IntermediateCatchEventSignalForScopeProcess.newProcess(app);
        org.kie.kogito.process.Process<IntermediateThrowEventProjectScopeModel> throwEventProjectScopeProcess = IntermediateThrowEventProjectScopeProcess.newProcess(app);

        ProcessInstance<IntermediateCatchEventSignalForScopeModel> catchEventInstance = catchEventSignalForScope.createInstance(catchEventSignalForScope.createModel());
        catchEventInstance.start();
        assertThat(catchEventInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        ProcessInstance<IntermediateThrowEventProjectScopeModel> throwEventProjectScopeInstance = throwEventProjectScopeProcess.createInstance(throwEventProjectScopeProcess.createModel());
        throwEventProjectScopeInstance.start();
        assertThat(throwEventProjectScopeInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(ProcessTestHelper.findRemovedInstance(app, catchEventInstance.id()))
                .isPresent()
                .get()
                .extracting(WorkflowProcessInstance::getState)
                .isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

}
