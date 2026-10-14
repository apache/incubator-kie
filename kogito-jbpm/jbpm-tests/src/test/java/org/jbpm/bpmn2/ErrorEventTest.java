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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jbpm.bpmn2.error.BoundaryErrorEventCatchingOnEntryExceptionModel;
import org.jbpm.bpmn2.error.BoundaryErrorEventCatchingOnEntryExceptionProcess;
import org.jbpm.bpmn2.error.BoundaryErrorEventCatchingOnExitExceptionModel;
import org.jbpm.bpmn2.error.BoundaryErrorEventCatchingOnExitExceptionProcess;
import org.jbpm.bpmn2.error.BoundaryErrorEventDefaultHandlerByErrorCodeModel;
import org.jbpm.bpmn2.error.BoundaryErrorEventDefaultHandlerByErrorCodeProcess;
import org.jbpm.bpmn2.error.BoundaryErrorEventDefaultHandlerWithErrorCodeWithStructureRefModel;
import org.jbpm.bpmn2.error.BoundaryErrorEventDefaultHandlerWithErrorCodeWithStructureRefProcess;
import org.jbpm.bpmn2.error.BoundaryErrorEventDefaultHandlerWithErrorCodeWithoutStructureRefModel;
import org.jbpm.bpmn2.error.BoundaryErrorEventDefaultHandlerWithErrorCodeWithoutStructureRefProcess;
import org.jbpm.bpmn2.error.BoundaryErrorEventStructureRefModel;
import org.jbpm.bpmn2.error.BoundaryErrorEventStructureRefProcess;
import org.jbpm.bpmn2.error.EndErrorModel;
import org.jbpm.bpmn2.error.EndErrorProcess;
import org.jbpm.bpmn2.error.EndErrorWithEventSubprocessModel;
import org.jbpm.bpmn2.error.EndErrorWithEventSubprocessProcess;
import org.jbpm.bpmn2.error.ErrorBoundaryEventInterruptingModel;
import org.jbpm.bpmn2.error.ErrorBoundaryEventInterruptingProcess;
import org.jbpm.bpmn2.error.ErrorBoundaryEventOnServiceTaskModel;
import org.jbpm.bpmn2.error.ErrorBoundaryEventOnServiceTaskProcess;
import org.jbpm.bpmn2.error.ErrorBoundaryEventOnTaskModel;
import org.jbpm.bpmn2.error.ErrorBoundaryEventOnTaskProcess;
import org.jbpm.bpmn2.error.ErrorVariableModel;
import org.jbpm.bpmn2.error.ErrorVariableProcess;
import org.jbpm.bpmn2.error.EventSubProcessErrorWithScriptModel;
import org.jbpm.bpmn2.error.EventSubProcessErrorWithScriptProcess;
import org.jbpm.bpmn2.error.EventSubprocessErrorHandlingWithErrorCodeModel;
import org.jbpm.bpmn2.error.EventSubprocessErrorHandlingWithErrorCodeProcess;
import org.jbpm.bpmn2.error.EventSubprocessErrorModel;
import org.jbpm.bpmn2.error.EventSubprocessErrorProcess;
import org.jbpm.bpmn2.event.BoundaryErrorEventDefaultHandlerWithoutErrorCodeWithStructureRefModel;
import org.jbpm.bpmn2.event.BoundaryErrorEventDefaultHandlerWithoutErrorCodeWithStructureRefProcess;
import org.jbpm.bpmn2.event.BoundaryErrorEventDefaultHandlerWithoutErrorCodeWithoutStructureRefModel;
import org.jbpm.bpmn2.event.BoundaryErrorEventDefaultHandlerWithoutErrorCodeWithoutStructureRefProcess;
import org.jbpm.bpmn2.event.BoundaryErrorEventSubProcessExceptionMappingModel;
import org.jbpm.bpmn2.event.BoundaryErrorEventSubProcessExceptionMappingProcess;
import org.jbpm.bpmn2.objects.ExceptionOnPurposeHandler;
import org.jbpm.bpmn2.objects.ExceptionService;
import org.jbpm.bpmn2.objects.MyError;
import org.jbpm.bpmn2.objects.Person;
import org.jbpm.bpmn2.objects.TestUserTaskWorkItemHandler;
import org.jbpm.bpmn2.objects.TestWorkItemHandler;
import org.jbpm.bpmn2.service.ExceptionServiceProcessErrorSignallingModel;
import org.jbpm.bpmn2.service.ExceptionServiceProcessErrorSignallingProcess;
import org.jbpm.bpmn2.subprocess.ExceptionServiceProcessSignallingModel;
import org.jbpm.bpmn2.subprocess.ExceptionServiceProcessSignallingProcess;
import org.jbpm.process.instance.event.listeners.RuleAwareProcessEventListener;
import org.jbpm.process.workitem.builtin.DoNothingWorkItemHandler;
import org.jbpm.process.workitem.builtin.SignallingTaskHandlerDecorator;
import org.jbpm.process.workitem.builtin.SystemOutWorkItemHandler;
import org.jbpm.test.utils.EventTrackerProcessListener;
import org.jbpm.test.utils.ProcessTestHelper;
import org.junit.jupiter.api.Test;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.kogito.Application;
import org.kie.kogito.handlers.AlwaysThrowingComponent_throwException__8DA0CD88_0714_43C1_B492_A70FADE42361_Handler;
import org.kie.kogito.handlers.ExceptionService_handleException__X_2_Handler;
import org.kie.kogito.handlers.ExceptionService_throwException__2_Handler;
import org.kie.kogito.handlers.ExceptionService_throwException__3_Handler;
import org.kie.kogito.handlers.HelloService_helloException_ServiceTask_2_Handler;
import org.kie.kogito.handlers.LoggingComponent_logException__E5B0E78B_0112_42F4_89FF_0DCC4FCB6BCD_Handler;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.kie.kogito.internal.process.event.KogitoProcessEventListener;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.process.workitem.WorkItemExecutionException;
import org.kie.kogito.process.workitem.WorkItemTransition;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.workitem.DefaultKogitoWorkItemHandler;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIterable;

public class ErrorEventTest extends JbpmBpmn2TestCase {

    @Test
    public void testEventSubprocessError() {
        Application app = ProcessTestHelper.newApplication();

        final List<String> executedNodes = new ArrayList<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName().equals("Script Task 1")) {
                    executedNodes.add(event.getNodeInstance().getId());
                }
            }
        };
        EventTrackerProcessListener eventTrackerProcessListener = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerProcessEventListener(app, eventTrackerProcessListener);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<EventSubprocessErrorModel> processDefinition = EventSubprocessErrorProcess.newProcess(app);
        ProcessInstance<EventSubprocessErrorModel> processInstance = processDefinition.createInstance(processDefinition.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();

        processInstance.completeWorkItem(workItem.getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(executedNodes).hasSize(1);
        List<String> trackedNodes = eventTrackerProcessListener.tracked().stream().map(event -> event.getNodeInstance().getNodeName()).toList();
        assertThatIterable(trackedNodes).contains(
                "start",
                "User Task 1",
                "end",
                "Sub Process 1",
                "start-sub",
                "Script Task 1",
                "end-sub");

    }

    @Test
    public void testEventSubprocessErrorThrowOnTask() throws Exception {
        Application app = ProcessTestHelper.newApplication();

        // Track executed nodes
        final List<String> executedNodes = new ArrayList<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName().equals("Script Task 1")) {
                    executedNodes.add(((KogitoNodeInstance) event.getNodeInstance()).getStringId());
                }
            }
        };

        // Handler that throws MyError when activated
        TestWorkItemHandler handler = new TestWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager,
                    KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                throw new MyError();
            }
        };

        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        Process<EventSubprocessErrorModel> processDefinition = EventSubprocessErrorProcess.newProcess(app);
        EventSubprocessErrorModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<EventSubprocessErrorModel> processInstance =
                processDefinition.createInstance(model);
        processInstance.start();

        // Verify process finished and was aborted (error was caught by event subprocess)
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);

        // Verify the event subprocess script task was executed
        assertThat(executedNodes).hasSize(1);
    }

    @Test
    public void testEventSubprocessErrorWithErrorCode() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        final List<String> executedNodes = new ArrayList<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName().equals("Script2")) {
                    executedNodes.add(event.getNodeInstance().getId());
                }
            }
        };
        EventTrackerProcessListener eventTrackerProcessListener = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerProcessEventListener(app, eventTrackerProcessListener);
        org.kie.kogito.process.Process<EventSubprocessErrorHandlingWithErrorCodeModel> processDefinition = EventSubprocessErrorHandlingWithErrorCodeProcess.newProcess(app);
        ProcessInstance<EventSubprocessErrorHandlingWithErrorCodeModel> processInstance = processDefinition.createInstance(processDefinition.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);
        assertThat(eventTrackerProcessListener.tracked())
                .anyMatch(ProcessTestHelper.left("start"))
                .anyMatch(ProcessTestHelper.left("Script1"))
                .anyMatch(ProcessTestHelper.left("starterror"))
                .anyMatch(ProcessTestHelper.left("Script2"))
                .anyMatch(ProcessTestHelper.left("end2"))
                .anyMatch(ProcessTestHelper.left("eventsubprocess"));

        assertThat(processInstance.variables().getCapturedException()).isInstanceOf(RuntimeException.class);
        assertThat(((RuntimeException) processInstance.variables().getCapturedException()).getMessage()).isEqualTo("XXX");
        assertThat(executedNodes).hasSize(1);
    }

    @Test
    public void testEventSubprocessErrorWithOutErrorCode() throws Exception {
        kruntime = createKogitoProcessRuntime("subprocess/EventSubprocessErrorHandlingWithOutErrorCode.bpmn2");
        final List<String> executednodes = new ArrayList<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName()
                        .equals("Script2")) {
                    executednodes.add(((KogitoNodeInstance) event.getNodeInstance()).getStringId());
                }
            }

        };
        kruntime.getProcessEventManager().addEventListener(listener);

        KogitoProcessInstance processInstance = kruntime.startProcess("order-fulfillment-bpm.ccc");

        assertProcessInstanceFinished(processInstance, kruntime);
        assertNodeTriggered(processInstance.getStringId(), "start", "Script1", "starterror", "Script2", "end2", "eventsubprocess");
        assertProcessVarValue(processInstance, "CapturedException", "java.lang.RuntimeException: XXX");
        assertThat(executednodes).hasSize(1);

    }

    @Test
    public void testErrorBoundaryEvent() {
        Application application = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(application, "MyTask",
                new DoNothingWorkItemHandler());
        Process<ErrorBoundaryEventInterruptingModel> process = ErrorBoundaryEventInterruptingProcess.newProcess(application);
        ProcessInstance<ErrorBoundaryEventInterruptingModel> instance = process.createInstance(process.createModel());
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testErrorBoundaryEventOnTask() throws Exception {
        Application app = ProcessTestHelper.newApplication();

        final List<String> triggeredNodes = new ArrayList<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
                triggeredNodes.add(event.getNodeInstance().getNodeName());
            }
        };
        ProcessTestHelper.registerProcessEventListener(app, listener);

        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        Process<ErrorBoundaryEventOnTaskModel> processDefinition = ErrorBoundaryEventOnTaskProcess.newProcess(app);
        ErrorBoundaryEventOnTaskModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<ErrorBoundaryEventOnTaskModel> processInstance = processDefinition.createInstance(model);
        processInstance.start();

        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).hasSize(2);

        KogitoWorkItem workItem = workItems.get(0);
        if (!"john".equalsIgnoreCase((String) workItem.getParameter("ActorId"))) {
            workItem = workItems.get(1);
        }

        processInstance.completeWorkItem(workItem.getStringId(), emptyMap());

        // In v9, when error is caught by boundary event and handled, process completes successfully
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(triggeredNodes).contains("start", "split", "User Task", "User task error attached",
                "error end event", "Script Task", "error2");
        assertThat(triggeredNodes).doesNotContain("error1");
    }

    @Test
    public void testErrorBoundaryEventOnServiceTask() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        EventTrackerProcessListener listener = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        TestUserTaskWorkItemHandler handler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        ProcessTestHelper.registerHandler(app, "org.jbpm.bpmn2.objects.HelloService_helloException_ServiceTask_2_Handler", new HelloService_helloException_ServiceTask_2_Handler());

        org.kie.kogito.process.Process<ErrorBoundaryEventOnServiceTaskModel> definition = ErrorBoundaryEventOnServiceTaskProcess.newProcess(app);

        ErrorBoundaryEventOnServiceTaskModel model = definition.createModel();
        model.setS("test");
        org.kie.kogito.process.ProcessInstance<ErrorBoundaryEventOnServiceTaskModel> instance = definition.createInstance(model);
        instance.start();

        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).hasSize(1);
        ProcessTestHelper.completeWorkItem(instance, Collections.emptyMap(), "john");

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

        assertThat(listener.tracked())
                .anyMatch(ProcessTestHelper.triggered("start"))
                .anyMatch(ProcessTestHelper.triggered("split"))
                .anyMatch(ProcessTestHelper.triggered("User Task"))
                .anyMatch(ProcessTestHelper.triggered("Service task error attached"))
                .anyMatch(ProcessTestHelper.triggered("end0"))
                .anyMatch(ProcessTestHelper.triggered("Script Task"))
                .anyMatch(ProcessTestHelper.triggered("error2"))
                .noneMatch(ProcessTestHelper.triggered("end"));

    }

    @Test
    public void testErrorBoundaryEventOnBusinessRuleTask() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-ErrorBoundaryEventOnBusinessRuleTask.bpmn2",
                "BPMN2-ErrorBoundaryEventOnBusinessRuleTask.drl");
        kruntime.getProcessEventManager().addEventListener(new RuleAwareProcessEventListener());

        KogitoProcessInstance processInstance = kruntime.startProcess("BPMN2-ErrorBoundaryEventOnBusinessRuleTask");

        assertProcessInstanceFinished(processInstance, kruntime);
        assertNodeTriggered(processInstance.getStringId(), "start", "business rule task error attached", "error1");
    }

    @Test
    public void testMultiErrorBoundaryEventsOnBusinessRuleTask() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-MultiErrorBoundaryEventsOnBusinessRuleTask.bpmn2",
                "BPMN2-MultiErrorBoundaryEventsOnBusinessRuleTask.drl");
        kruntime.getProcessEventManager().addEventListener(new RuleAwareProcessEventListener());

        Map<String, Object> params = new HashMap<>();
        params.put("person", new Person());

        KogitoProcessInstance processInstance = kruntime.startProcess("BPMN2-MultiErrorBoundaryEventeOnBusinessRuleTask", params);

        assertProcessInstanceFinished(processInstance, kruntime);
        assertNodeTriggered(processInstance.getStringId(), "start", "business rule task error attached",
                "NPE Script Task", "error1");

        kruntime.getKieSession().dispose();

        kruntime = createKogitoProcessRuntime("BPMN2-MultiErrorBoundaryEventsOnBusinessRuleTask.bpmn2",
                "BPMN2-MultiErrorBoundaryEventsOnBusinessRuleTask.drl");

        kruntime.getProcessEventManager().addEventListener(new RuleAwareProcessEventListener());
        params = new HashMap<>();
        params.put("person", new Person("unsupported"));
        KogitoProcessInstance processInstance2 = kruntime.startProcess("BPMN2-MultiErrorBoundaryEventeOnBusinessRuleTask", params);
        assertProcessInstanceFinished(processInstance2, kruntime);
        assertNodeTriggered(processInstance2.getStringId(), "start", "business rule task error attached",
                "UOE Script Task", "error2");
    }

    @Test
    public void testCatchErrorBoundaryEventOnTask() throws Exception {
        Application app = ProcessTestHelper.newApplication();

        final List<String> executedNodes = new ArrayList<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
                executedNodes.add(event.getNodeInstance().getNodeName());
            }
        };
        ProcessTestHelper.registerProcessEventListener(app, listener);

        // Custom handler that throws MyError when actor is "mary"
        TestWorkItemHandler handler = new TestWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager,
                    KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                if (workItem.getParameter("ActorId").equals("mary")) {
                    throw new MyError();
                }
                return Optional.empty();
            }

            @Override
            public Optional<WorkItemTransition> abortWorkItemHandler(KogitoWorkItemManager manager,
                    KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                return Optional.empty();
            }
        };

        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        Process<ErrorBoundaryEventOnTaskModel> processDefinition = ErrorBoundaryEventOnTaskProcess.newProcess(app);
        ErrorBoundaryEventOnTaskModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<ErrorBoundaryEventOnTaskModel> processInstance = processDefinition.createInstance(model);
        processInstance.start();

        // Verify process is still active (mary's task threw error, but john's task is still pending)
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        assertThat(executedNodes).contains("start", "split", "User Task", "User task error attached",
                "Script Task", "error2");
    }

    @Test
    public void testErrorSignallingExceptionServiceTask() throws Exception {
        String input = "this is my service input";

        Application app = ProcessTestHelper.newApplication();
        SignallingTaskHandlerDecorator signallingTaskWrapper = new SignallingTaskHandlerDecorator(ExceptionService_throwException__3_Handler.class, "Error-code");
        signallingTaskWrapper.setWorkItemExceptionParameterName(ExceptionService.exceptionParameterName);
        Object[] caughtEventObjectHolder = new Object[1];
        caughtEventObjectHolder[0] = null;
        ExceptionService.setCaughtEventObjectHolder(caughtEventObjectHolder);

        ProcessTestHelper.registerHandler(app, "Human Task", new TestWorkItemHandler());
        ProcessTestHelper.registerHandler(app, "org.jbpm.bpmn2.objects.ExceptionService_throwException__3_Handler", signallingTaskWrapper);
        ProcessTestHelper.registerHandler(app, "org.jbpm.bpmn2.objects.ExceptionService_handleException__X_2_Handler", new ExceptionService_handleException__X_2_Handler());
        org.kie.kogito.process.Process<ExceptionServiceProcessErrorSignallingModel> definition = ExceptionServiceProcessErrorSignallingProcess.newProcess(app);

        ExceptionServiceProcessErrorSignallingModel model = definition.createModel();
        model.setServiceInputItem(input);
        org.kie.kogito.process.ProcessInstance<ExceptionServiceProcessErrorSignallingModel> instance = definition.createInstance(model);
        instance.start();

        ProcessTestHelper.completeWorkItem(instance, Collections.emptyMap());
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ABORTED);
        assertThat(caughtEventObjectHolder[0] != null && caughtEventObjectHolder[0] instanceof KogitoWorkItem).withFailMessage("Event was not passed to Event Subprocess.").isTrue();
    }

    @Test
    public void testSignallingExceptionServiceTask() throws Exception {
        String input = "this is my service input";

        SignallingTaskHandlerDecorator signallingTaskWrapper = new SignallingTaskHandlerDecorator(ExceptionService_throwException__2_Handler.class, "exception-signal");
        signallingTaskWrapper.setWorkItemExceptionParameterName(ExceptionService.exceptionParameterName);
        Object[] caughtEventObjectHolder = new Object[1];
        caughtEventObjectHolder[0] = null;
        ExceptionService.setCaughtEventObjectHolder(caughtEventObjectHolder);

        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new TestWorkItemHandler());
        ProcessTestHelper.registerHandler(app, "org.jbpm.bpmn2.objects.ExceptionService_throwException__2_Handler", signallingTaskWrapper);
        ProcessTestHelper.registerHandler(app, "org.jbpm.bpmn2.objects.ExceptionService_handleException__X_2_Handler", new ExceptionService_handleException__X_2_Handler());
        org.kie.kogito.process.Process<ExceptionServiceProcessSignallingModel> definition = ExceptionServiceProcessSignallingProcess.newProcess(app);

        ExceptionServiceProcessSignallingModel model = definition.createModel();
        model.setServiceInputItem(input);
        org.kie.kogito.process.ProcessInstance<ExceptionServiceProcessSignallingModel> instance = definition.createInstance(model);
        instance.start();

        ProcessTestHelper.completeWorkItem(instance, Collections.emptyMap(), "john");
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThat(caughtEventObjectHolder[0] != null && caughtEventObjectHolder[0] instanceof KogitoWorkItem).withFailMessage("Event was not passed to Event Subprocess.").isTrue();

    }

    @Test
    public void testEventSubProcessErrorWithScript() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Request Handler", new SignallingTaskHandlerDecorator(new ExceptionOnPurposeHandler(), "Error-90277"));
        ProcessTestHelper.registerHandler(app, "Error Handler", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<EventSubProcessErrorWithScriptModel> processDefinition = EventSubProcessErrorWithScriptProcess.newProcess(app);
        ProcessInstance<EventSubProcessErrorWithScriptModel> processInstance = processDefinition.createInstance(processDefinition.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);
        assertThat(((org.kie.kogito.process.impl.AbstractProcessInstance<?>) processInstance)
                .internalGetProcessInstance().getOutcome()).isEqualTo("90277");
    }

    @Test
    public void testErrorBoundaryEventOnEntry() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<BoundaryErrorEventCatchingOnEntryExceptionModel> processDefinition = BoundaryErrorEventCatchingOnEntryExceptionProcess.newProcess(app);
        BoundaryErrorEventCatchingOnEntryExceptionModel model = processDefinition.createModel();
        ProcessInstance<BoundaryErrorEventCatchingOnEntryExceptionModel> processInstance = processDefinition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        assertThat(workItemHandler.getWorkItems()).hasSize(1);
    }

    @Test
    public void testErrorBoundaryEventOnExit() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<BoundaryErrorEventCatchingOnExitExceptionModel> processDefinition = BoundaryErrorEventCatchingOnExitExceptionProcess.newProcess(app);
        BoundaryErrorEventCatchingOnExitExceptionModel model = processDefinition.createModel();
        ProcessInstance<BoundaryErrorEventCatchingOnExitExceptionModel> processInstance = processDefinition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        processInstance.completeWorkItem(workItem.getStringId(), Collections.emptyMap());
        assertThat(workItemHandler.getWorkItems()).hasSize(1);
    }

    @Test
    public void testBoundaryErrorEventDefaultHandlerWithErrorCodeWithStructureRef() {
        Application app = ProcessTestHelper.newApplication();
        ExceptionWorkItemHandler handler = new ExceptionWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        org.kie.kogito.process.Process<BoundaryErrorEventDefaultHandlerWithErrorCodeWithStructureRefModel> processDefinition =
                BoundaryErrorEventDefaultHandlerWithErrorCodeWithStructureRefProcess.newProcess(app);
        ProcessInstance<BoundaryErrorEventDefaultHandlerWithErrorCodeWithStructureRefModel> processInstance = processDefinition.createInstance(processDefinition.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testBoundaryErrorEventDefaultHandlerWithWorkItemExecutionError() {
        Application app = ProcessTestHelper.newApplication();
        WorkItemExecutionErrorWorkItemHandler handler = new WorkItemExecutionErrorWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        org.kie.kogito.process.Process<BoundaryErrorEventDefaultHandlerByErrorCodeModel> processDefinition = BoundaryErrorEventDefaultHandlerByErrorCodeProcess.newProcess(app);
        ProcessInstance<BoundaryErrorEventDefaultHandlerByErrorCodeModel> processInstance = processDefinition.createInstance(processDefinition.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testBoundaryErrorEventDefaultHandlerWithErrorCodeWithoutStructureRef() {
        Application app = ProcessTestHelper.newApplication();
        ExceptionWorkItemHandler handler = new ExceptionWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        org.kie.kogito.process.Process<BoundaryErrorEventDefaultHandlerWithErrorCodeWithoutStructureRefModel> definition =
                BoundaryErrorEventDefaultHandlerWithErrorCodeWithoutStructureRefProcess.newProcess(app);
        org.kie.kogito.process.ProcessInstance<BoundaryErrorEventDefaultHandlerWithErrorCodeWithoutStructureRefModel> instance = definition.createInstance(definition.createModel());
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_ERROR);
    }

    @Test
    public void testBoundaryErrorEventDefaultHandlerWithoutErrorCodeWithStructureRef() {
        Application app = ProcessTestHelper.newApplication();
        EventTrackerProcessListener listener = new EventTrackerProcessListener();
        ExceptionWorkItemHandler handler = new ExceptionWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<BoundaryErrorEventDefaultHandlerWithoutErrorCodeWithStructureRefModel> definition =
                BoundaryErrorEventDefaultHandlerWithoutErrorCodeWithStructureRefProcess.newProcess(app);
        org.kie.kogito.process.ProcessInstance<BoundaryErrorEventDefaultHandlerWithoutErrorCodeWithStructureRefModel> instance = definition.createInstance(definition.createModel());
        instance.start();

        assertThat(listener.tracked())
                .anyMatch(ProcessTestHelper.triggered("Start"))
                .anyMatch(ProcessTestHelper.triggered("User Task"))
                .anyMatch(ProcessTestHelper.left("MyBoundaryErrorEvent"));

    }

    @Test
    public void testBoundaryErrorEventDefaultHandlerWithoutErrorCodeWithoutStructureRef() throws Exception {

        Application app = ProcessTestHelper.newApplication();
        EventTrackerProcessListener listener = new EventTrackerProcessListener();
        ExceptionWorkItemHandler handler = new ExceptionWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<BoundaryErrorEventDefaultHandlerWithoutErrorCodeWithoutStructureRefModel> definition =
                BoundaryErrorEventDefaultHandlerWithoutErrorCodeWithoutStructureRefProcess.newProcess(app);
        org.kie.kogito.process.ProcessInstance<BoundaryErrorEventDefaultHandlerWithoutErrorCodeWithoutStructureRefModel> instance = definition.createInstance(definition.createModel());
        instance.start();

        assertThat(listener.tracked())
                .anyMatch(ProcessTestHelper.triggered("Start"))
                .anyMatch(ProcessTestHelper.triggered("User Task"))
                .anyMatch(ProcessTestHelper.left("MyBoundaryErrorEvent"));
    }

    @Test
    public void testBoundaryErrorEventSubProcessExceptionMapping() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ExceptionWorkItemHandler handler = new ExceptionWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        org.kie.kogito.process.Process<BoundaryErrorEventSubProcessExceptionMappingModel> definition =
                BoundaryErrorEventSubProcessExceptionMappingProcess.newProcess(app);
        org.kie.kogito.process.ProcessInstance<BoundaryErrorEventSubProcessExceptionMappingModel> instance = definition.createInstance(definition.createModel());
        instance.start();

        assertThat(instance.variables().getVar1())
                .isNotNull()
                .isInstanceOf(RuntimeException.class);

    }

    @Test
    public void testBoundaryErrorEventStructureRef() {
        Application app = ProcessTestHelper.newApplication();
        ExceptionWorkItemHandler handler = new ExceptionWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        final List<String> executedNodes = new ArrayList<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                executedNodes.add(event.getNodeInstance().getNodeName());
            }
        };
        EventTrackerProcessListener eventTrackerProcessListener = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerProcessEventListener(app, eventTrackerProcessListener);
        org.kie.kogito.process.Process<BoundaryErrorEventStructureRefModel> processDefinition = BoundaryErrorEventStructureRefProcess.newProcess(app);
        ProcessInstance<BoundaryErrorEventStructureRefModel> processInstance = processDefinition.createInstance(processDefinition.createModel());
        processInstance.start();
        assertThat(eventTrackerProcessListener.tracked())
                .anyMatch(ProcessTestHelper.left("Start"))
                .anyMatch(ProcessTestHelper.left("User Task"))
                .anyMatch(ProcessTestHelper.left("MyBoundaryErrorEvent"));
    }

    @Test
    public void testEndErrorWithSubprocess() {
        Application app = ProcessTestHelper.newApplication();
        EventTrackerProcessListener tracker = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, tracker);
        org.kie.kogito.process.Process<EndErrorWithEventSubprocessModel> processDefinition = EndErrorWithEventSubprocessProcess.newProcess(app);
        ProcessInstance<EndErrorWithEventSubprocessModel> processInstance = processDefinition.createInstance(processDefinition.createModel());
        processInstance.start();
        assertThat(tracker.tracked()).anyMatch(ProcessTestHelper.triggered("start"));
        assertThat(tracker.tracked()).anyMatch(ProcessTestHelper.triggered("task"));
        assertThat(tracker.tracked()).anyMatch(ProcessTestHelper.triggered("subprocess-task"));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEndError() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        EventTrackerProcessListener eventTrackerProcessListener = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, eventTrackerProcessListener);
        org.kie.kogito.process.Process<EndErrorModel> processDefinition = EndErrorProcess.newProcess(app);
        org.kie.kogito.process.ProcessInstance<EndErrorModel> instance = processDefinition.createInstance(processDefinition.createModel());
        instance.start();

        assertThat(eventTrackerProcessListener.tracked()).anyMatch(ProcessTestHelper.triggered("start"));
        assertThat(eventTrackerProcessListener.tracked()).anyMatch(ProcessTestHelper.triggered("task"));
        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_ABORTED);

    }

    @Test
    public void testErrorVariable() throws Exception {

        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Service Task", new WorkItemExecutionErrorWorkItemHandler("MY_ERROR"));
        ProcessTestHelper.registerHandler(app, "org.jbpm.bpmn2.services.AlwaysThrowingComponent_throwException__8DA0CD88_0714_43C1_B492_A70FADE42361_Handler",
                new AlwaysThrowingComponent_throwException__8DA0CD88_0714_43C1_B492_A70FADE42361_Handler());
        ProcessTestHelper.registerHandler(app, "org.jbpm.bpmn2.services.LoggingComponent_logException__E5B0E78B_0112_42F4_89FF_0DCC4FCB6BCD_Handler",
                new LoggingComponent_logException__E5B0E78B_0112_42F4_89FF_0DCC4FCB6BCD_Handler());
        org.kie.kogito.process.Process<ErrorVariableModel> processDefinition = ErrorVariableProcess.newProcess(app);
        ErrorVariableModel model = processDefinition.createModel();
        model.setTheException("theException");
        org.kie.kogito.process.ProcessInstance<ErrorVariableModel> instance = processDefinition.createInstance(model);
        instance.start();

        assertThat(instance.variables().getTheException()).isInstanceOf(WorkItemExecutionException.class);
        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
    }

    class ExceptionWorkItemHandler extends DefaultKogitoWorkItemHandler {

        @Override
        public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workitem, WorkItemTransition transition) {
            throw new RuntimeException();
        }

    }

    class WorkItemExecutionErrorWorkItemHandler extends DefaultKogitoWorkItemHandler {

        private final String errorCode;

        public WorkItemExecutionErrorWorkItemHandler() {
            this("500");
        }

        public WorkItemExecutionErrorWorkItemHandler(String errorCode) {
            this.errorCode = errorCode;
        }

        @Override
        public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workitem, WorkItemTransition transition) {
            throw new WorkItemExecutionException(errorCode);
        }

    }
}
