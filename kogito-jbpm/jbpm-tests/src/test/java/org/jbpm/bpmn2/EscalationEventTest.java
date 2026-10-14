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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jbpm.bpmn2.escalation.EscalationBoundaryEventModel;
import org.jbpm.bpmn2.escalation.EscalationBoundaryEventProcess;
import org.jbpm.bpmn2.escalation.EscalationBoundaryEventWithTaskModel;
import org.jbpm.bpmn2.escalation.EscalationBoundaryEventWithTaskProcess;
import org.jbpm.bpmn2.escalation.EscalationEndEventHandlingModel;
import org.jbpm.bpmn2.escalation.EscalationEndEventHandlingProcess;
import org.jbpm.bpmn2.escalation.EscalationEndEventModel;
import org.jbpm.bpmn2.escalation.EscalationEndEventProcess;
import org.jbpm.bpmn2.escalation.EscalationWithDataMappingModel;
import org.jbpm.bpmn2.escalation.EscalationWithDataMappingProcess;
import org.jbpm.bpmn2.escalation.EventSubprocessEscalationModel;
import org.jbpm.bpmn2.escalation.EventSubprocessEscalationProcess;
import org.jbpm.bpmn2.escalation.IntermediateThrowEventEscalationModel;
import org.jbpm.bpmn2.escalation.IntermediateThrowEventEscalationProcess;
import org.jbpm.bpmn2.escalation.MultiEscalationModel;
import org.jbpm.bpmn2.escalation.MultiEscalationProcess;
import org.jbpm.bpmn2.escalation.TopLevelEscalationModel;
import org.jbpm.bpmn2.escalation.TopLevelEscalationProcess;
import org.jbpm.bpmn2.objects.TestUserTaskWorkItemHandler;
import org.jbpm.bpmn2.objects.TestWorkItemHandler;
import org.jbpm.test.utils.EventTrackerProcessListener;
import org.jbpm.test.utils.ProcessTestHelper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.event.process.SignalEvent;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.kogito.Application;
import org.kie.kogito.auth.SecurityPolicy;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.kie.kogito.internal.process.event.KogitoProcessEventListener;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.SignalFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jbpm.test.utils.ProcessTestHelper.left;
import static org.jbpm.test.utils.ProcessTestHelper.triggered;

public class EscalationEventTest extends JbpmBpmn2TestCase {

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

    @Test
    public void testMultiEscalation() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void onSignal(SignalEvent event) {
                logger.info("on signal {}", event);
            }
        });
        org.kie.kogito.process.Process<MultiEscalationModel> definition = MultiEscalationProcess.newProcess(app);
        MultiEscalationModel model = definition.createModel();
        model.setData("data");
        model.setEnddata("end_data");
        org.kie.kogito.process.ProcessInstance<MultiEscalationModel> instance = definition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testTopLevelEscalation() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        TestUserTaskWorkItemHandler workItemHandler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        List<String> instances = new ArrayList<>();
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void beforeProcessStarted(ProcessStartedEvent event) {
                instances.add(event.getProcessInstance().getId());
            }
        });
        org.kie.kogito.process.Process<TopLevelEscalationModel> definition = TopLevelEscalationProcess.newProcess(app);
        TopLevelEscalationModel model = definition.createModel();
        model.setData("data");
        definition.send(SignalFactory.of("Escalation-START_NEW", "data"));
        assertThat(instances).hasSize(1);
    }

    @Test
    public void testEventSubprocessEscalation() throws Exception {
        final List<String> executednodes = new ArrayList<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName().equals("Script Task 1")) {
                    executednodes.add(((KogitoNodeInstance) event.getNodeInstance()).getStringId());
                }
            }

        };
        EventTrackerProcessListener tracker = new EventTrackerProcessListener();
        TestUserTaskWorkItemHandler workItemHandler = new TestUserTaskWorkItemHandler();

        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerProcessEventListener(app, tracker);
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);

        org.kie.kogito.process.Process<EventSubprocessEscalationModel> processDefinition = EventSubprocessEscalationProcess.newProcess(app);
        org.kie.kogito.process.ProcessInstance<EventSubprocessEscalationModel> instance = processDefinition.createInstance(processDefinition.createModel());
        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();

        ProcessTestHelper.completeWorkItem(instance, Collections.emptyMap(), "john");

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(executednodes).hasSize(1);

        assertThat(tracker.tracked())
                .anyMatch(triggered("start"))
                .anyMatch(triggered("User Task 1"))
                .anyMatch(triggered("end"))
                .anyMatch(left("Sub Process 1"))
                .anyMatch(left("start-sub"))
                .anyMatch(triggered("Script Task 1"))
                .anyMatch(triggered("end-sub"));

    }

    @Test
    public void testEscalationBoundaryEvent() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<EscalationBoundaryEventModel> processDefinition = EscalationBoundaryEventProcess.newProcess(app);

        EscalationBoundaryEventModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<EscalationBoundaryEventModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEscalationBoundaryEventInterrupting() throws Exception {
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/escalation/BPMN2-EscalationBoundaryEventInterrupting.bpmn2");
        TestWorkItemHandler handler = new TestWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("MyTask", handler);
        KogitoProcessInstance processInstance = kruntime.startProcess("EscalationBoundaryEventInterrupting");
        assertProcessInstanceCompleted(processInstance);
    }

    @Test
    @Disabled("Escalation does not cancel work items yet.")
    // TODO: make escalation interrupt tasks -- or look more closely at the spec to make sure that's the case? 
    public void testEscalationBoundaryEventInterruptsTask() throws Exception {
        kruntime = createKogitoProcessRuntime("escalation/BPMN2-EscalationBoundaryEventInterrupting.bpmn2");
        TestWorkItemHandler handler = new TestWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("MyTask", handler);
        KogitoProcessInstance processInstance = kruntime.startProcess("EscalationBoundaryEvent");
        assertProcessInstanceCompleted(processInstance);

        // Check for cancellation of task
        assertThat(handler.getWorkItem().getState()).as("WorkItem was not cancelled!").isEqualTo(KogitoWorkItem.ABORTED);
    }

    @Test
    public void testEscalationIntermediateThrowEventProcess() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<IntermediateThrowEventEscalationModel> processDefinition = IntermediateThrowEventEscalationProcess.newProcess(app);
        IntermediateThrowEventEscalationModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<IntermediateThrowEventEscalationModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testGeneralEscalationBoundaryEventWithTask() throws Exception {
        TestUserTaskWorkItemHandler handler = new TestUserTaskWorkItemHandler();

        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<EscalationBoundaryEventWithTaskModel> processDefinition = EscalationBoundaryEventWithTaskProcess.newProcess(app);
        EscalationBoundaryEventWithTaskModel model = processDefinition.createModel();
        model.setX("0");
        org.kie.kogito.process.ProcessInstance<EscalationBoundaryEventWithTaskModel> instance = processDefinition.createInstance(model);
        instance.start();
        ProcessTestHelper.completeWorkItem(instance, Collections.emptyMap(), "john");

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getX()).isEqualTo("1");

    }

    @Test
    public void testInterruptingEscalationBoundaryEventOnTask() throws Exception {
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/escalation/BPMN2-EscalationBoundaryEventOnTaskInterrupting.bpmn2");

        TestUserTaskWorkItemHandler workItemHandler = new TestUserTaskWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        kruntime.getProcessEventManager().addEventListener(LOGGING_EVENT_LISTENER);
        KogitoProcessInstance processInstance = kruntime.startProcess("EscalationBoundaryEventOnTaskInterrupting");

        List<KogitoWorkItem> workItems = workItemHandler.getWorkItems();
        assertThat(workItems).hasSize(2);

        KogitoWorkItem workItem = workItems.get(0);
        if (!"john".equalsIgnoreCase((String) workItem.getParameter("ActorId"))) {
            workItem = workItems.get(1);
        }

        kruntime.getKogitoWorkItemManager().completeWorkItem(workItem.getStringId(), Collections.emptyMap(), SecurityPolicy.of("john", Collections.emptyList()));
        assertProcessInstanceFinished(processInstance, kruntime);
    }

    @Test
    @Disabled("Non interrupting escalation has not yet been implemented.")
    // TODO: implement non-interrupting escalation
    public void testNonInterruptingEscalationBoundaryEventOnTask() throws Exception {
        kruntime = createKogitoProcessRuntime("escalation/BPMN2-EscalationBoundaryEventOnTask.bpmn2");
        TestWorkItemHandler handler = new TestWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task", handler);
        kruntime.getProcessEventManager().addEventListener(LOGGING_EVENT_LISTENER);
        KogitoProcessInstance processInstance = kruntime.startProcess("non-interrupting-escalation");

        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).hasSize(2);

        KogitoWorkItem johnsWork = workItems.get(0);
        KogitoWorkItem marysWork = workItems.get(1);
        if (!"john".equalsIgnoreCase((String) johnsWork.getParameter("ActorId"))) {
            marysWork = johnsWork;
            johnsWork = workItems.get(1);
        }

        // end event after task triggers escalation 
        kruntime.getKogitoWorkItemManager().completeWorkItem(johnsWork.getStringId(), null);
        // escalation should have run.. 

        // should finish process
        kruntime.getKogitoWorkItemManager().completeWorkItem(marysWork.getStringId(), null);
        assertProcessInstanceCompleted(processInstance);
    }

    @Test
    public void testEscalationEndEventProcess() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<EscalationEndEventModel> processDefinition = EscalationEndEventProcess.newProcess(app);
        EscalationEndEventModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<EscalationEndEventModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testEscalationBoundaryEventAndIntermediate() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<EscalationWithDataMappingModel> processDefinition = EscalationWithDataMappingProcess.newProcess(app);
        EscalationWithDataMappingModel model = processDefinition.createModel();
        model.setProperty_2("java.lang.RuntimeException");
        org.kie.kogito.process.ProcessInstance<EscalationWithDataMappingModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getProperty_3()).isEqualTo("java.lang.RuntimeException");
    }

    @Test
    public void testHandledEscalationEndEventProcess() {
        Application app = ProcessTestHelper.newApplication();

        org.kie.kogito.process.Process<EscalationEndEventHandlingModel> processDefinition = EscalationEndEventHandlingProcess.newProcess(app);
        EscalationEndEventHandlingModel model = processDefinition.createModel();
        model.setHello(70);

        org.kie.kogito.process.ProcessInstance<EscalationEndEventHandlingModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }
}
