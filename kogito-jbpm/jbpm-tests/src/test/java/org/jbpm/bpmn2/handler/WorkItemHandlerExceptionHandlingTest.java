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
package org.jbpm.bpmn2.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jbpm.bpmn2.objects.TestWorkItemHandler;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.workitem.builtin.ProcessWorkItemHandlerExceptionHandler;
import org.jbpm.test.utils.ProcessTestHelper;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.runtime.process.ProcessWorkItemHandlerException.HandlingStrategy;
import org.kie.kogito.Application;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.kie.kogito.internal.process.event.KogitoProcessEventListener;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jbpm.process.core.context.variable.VariableScope.VARIABLE_STRICT_ENABLED_PROPERTY;

public class WorkItemHandlerExceptionHandlingTest {

    private static Boolean strictVariableSetting = Boolean.parseBoolean(System.getProperty(VARIABLE_STRICT_ENABLED_PROPERTY, Boolean.FALSE.toString()));

    @BeforeAll
    public static void setup() throws Exception {
        VariableScope.setVariableStrictOption(false);
    }

    @AfterAll
    public static void clean() throws Exception {
        VariableScope.setVariableStrictOption(strictVariableSetting);
    }

    @Test
    public void testErrornousHandlerWithStrategyComplete() throws Exception {
        ErrornousWorkItemHandler workItemHandler = new ErrornousWorkItemHandler("ScriptTask", HandlingStrategy.COMPLETE);

        Application application = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(application, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskWithBooleanOutputModel> processDefinition = UserTaskWithBooleanOutputProcess.newProcess(application);
        ScriptTaskProcess.newProcess(application);

        UserTaskWithBooleanOutputModel model = processDefinition.createModel();
        model.setIsChecked(false);
        org.kie.kogito.process.ProcessInstance<UserTaskWithBooleanOutputModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getIsChecked()).isTrue();

        KogitoWorkItem handledWorkItem = (KogitoWorkItem) workItemHandler.getWorkItem();
        assertThat(handledWorkItem.getState()).isEqualTo(KogitoWorkItem.COMPLETED);
    }

    @Test
    public void testErrornousHandlerWithStrategyCompleteWaitState() throws Exception {
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterProcessCompleted(ProcessCompletedEvent event) {
                if (event.getProcessInstance() instanceof WorkflowProcessInstanceImpl impl && "UserTaskWithBooleanOutput".equals(impl.getProcessId())) {
                    assertThat(impl.getState()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
                    assertThat((Boolean) impl.getVariable("isChecked")).isTrue();
                }
            }
        };
        TestWorkItemHandler testHandler = new TestWorkItemHandler();
        ErrornousWorkItemHandler workItemHandler = new ErrornousWorkItemHandler("ReceiveTask", HandlingStrategy.COMPLETE);

        Application application = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(application, "Human Task", workItemHandler);
        ProcessTestHelper.registerHandler(application, "Receive Task", testHandler);
        ProcessTestHelper.registerProcessEventListener(application, listener);
        org.kie.kogito.process.Process<UserTaskWithBooleanOutputModel> processDefinition = UserTaskWithBooleanOutputProcess.newProcess(application);
        org.kie.kogito.process.Process<ReceiveTaskModel> receiveDefinition = ReceiveTaskProcess.newProcess(application);

        UserTaskWithBooleanOutputModel model = processDefinition.createModel();
        model.setIsChecked(false);
        org.kie.kogito.process.ProcessInstance<UserTaskWithBooleanOutputModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);

        Map<String, Object> results = new HashMap<>();
        results.put("Message", true);
        org.kie.kogito.process.ProcessInstance<ReceiveTaskModel> exception = receiveDefinition.instances().stream().findAny().orElse(null);
        assertThat(exception).isNotNull();
        ProcessTestHelper.completeWorkItem(exception, results);

    }

    @Test
    public void testErrornousHandlerWithStrategyAbort() throws Exception {

        ErrornousWorkItemHandler workItemHandler = new ErrornousWorkItemHandler("ScriptTask", HandlingStrategy.ABORT);

        Application application = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(application, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskWithBooleanOutputModel> processDefinition = UserTaskWithBooleanOutputProcess.newProcess(application);
        ScriptTaskProcess.newProcess(application);

        UserTaskWithBooleanOutputModel model = processDefinition.createModel();
        model.setIsChecked(false);
        org.kie.kogito.process.ProcessInstance<UserTaskWithBooleanOutputModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getIsChecked()).isFalse();

        KogitoWorkItem handledWorkItem = (KogitoWorkItem) workItemHandler.getWorkItem();
        assertThat(handledWorkItem.getState()).isEqualTo(KogitoWorkItem.ABORTED);

    }

    @Test
    public void testErrornousHandlerWithStrategyAbortWaitState() throws Exception {
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterProcessCompleted(ProcessCompletedEvent event) {
                if (event.getProcessInstance() instanceof WorkflowProcessInstanceImpl impl && "UserTaskWithBooleanOutput".equals(impl.getProcessId())) {
                    assertThat(impl.getState()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
                    assertThat((Boolean) impl.getVariable("isChecked")).isTrue();
                }
            }
        };
        TestWorkItemHandler testHandler = new TestWorkItemHandler();
        ErrornousWorkItemHandler workItemHandler = new ErrornousWorkItemHandler("ReceiveTask", HandlingStrategy.COMPLETE);

        Application application = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(application, "Human Task", workItemHandler);
        ProcessTestHelper.registerHandler(application, "Receive Task", testHandler);
        ProcessTestHelper.registerProcessEventListener(application, listener);
        org.kie.kogito.process.Process<UserTaskWithBooleanOutputModel> processDefinition = UserTaskWithBooleanOutputProcess.newProcess(application);
        org.kie.kogito.process.Process<ReceiveTaskModel> receiveDefinition = ReceiveTaskProcess.newProcess(application);

        UserTaskWithBooleanOutputModel model = processDefinition.createModel();
        model.setIsChecked(false);
        org.kie.kogito.process.ProcessInstance<UserTaskWithBooleanOutputModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);

        Map<String, Object> results = new HashMap<>();
        results.put("Message", true);
        org.kie.kogito.process.ProcessInstance<ReceiveTaskModel> exception = receiveDefinition.instances().stream().findAny().orElse(null);
        assertThat(exception).isNotNull();
        ProcessTestHelper.completeWorkItem(exception, results);

    }

    @Test
    public void testErrornousHandlerWithStrategyRethrow() throws Exception {
        ErrornousWorkItemHandler workItemHandler = new ErrornousWorkItemHandler("ScriptTask", HandlingStrategy.RETHROW);

        Application application = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(application, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskWithBooleanOutputModel> processDefinition = UserTaskWithBooleanOutputProcess.newProcess(application);
        ScriptTaskProcess.newProcess(application);

        UserTaskWithBooleanOutputModel model = processDefinition.createModel();
        model.setIsChecked(false);
        org.kie.kogito.process.ProcessInstance<UserTaskWithBooleanOutputModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_ERROR);
        assertThat(instance.variables().getIsChecked()).isFalse();

    }

    @Test
    public void testErrornousHandlerWithStrategyRetry() throws Exception {
        ErrornousWorkItemHandler workItemHandler = new ErrornousWorkItemHandler("ScriptTask", HandlingStrategy.RETRY);

        Application application = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(application, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskWithBooleanOutputModel> processDefinition = UserTaskWithBooleanOutputProcess.newProcess(application);
        ScriptTaskProcess.newProcess(application);

        org.kie.kogito.process.ProcessInstance<UserTaskWithBooleanOutputModel> instance = processDefinition.createInstance(processDefinition.createModel());
        instance.start();
        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getIsChecked()).isTrue();

    }

    @Test
    public void testErrornousHandlerWithStrategyRetryWaitState() throws Exception {
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterProcessCompleted(ProcessCompletedEvent event) {
                if (event.getProcessInstance() instanceof WorkflowProcessInstanceImpl impl && "UserTaskWithBooleanOutput".equals(impl.getProcessId())) {
                    assertThat(impl.getState()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
                    assertThat((Boolean) impl.getVariable("isChecked")).isTrue();
                }
            }
        };

        TestWorkItemHandler testHandler = new TestWorkItemHandler();
        ErrornousWorkItemHandler workItemHandler = new ErrornousWorkItemHandler("ReceiveTask", HandlingStrategy.RETRY);

        Application application = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(application, "Human Task", workItemHandler);
        ProcessTestHelper.registerHandler(application, "Receive Task", testHandler);
        ProcessTestHelper.registerProcessEventListener(application, listener);
        org.kie.kogito.process.Process<UserTaskWithBooleanOutputModel> processDefinition = UserTaskWithBooleanOutputProcess.newProcess(application);
        org.kie.kogito.process.Process<ReceiveTaskModel> receiveDefinition = ReceiveTaskProcess.newProcess(application);

        org.kie.kogito.process.ProcessInstance<UserTaskWithBooleanOutputModel> instance = processDefinition.createInstance(processDefinition.createModel());
        instance.start();
        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);

        Map<String, Object> results = new HashMap<>();
        results.put("Message", true);
        org.kie.kogito.process.ProcessInstance<ReceiveTaskModel> exception = receiveDefinition.instances().stream().findAny().orElse(null);
        assertThat(exception).isNotNull();
        ProcessTestHelper.completeWorkItem(exception, results);

    }

    @Test
    public void testDecoratorWithStrategyComplete() throws Exception {
        DefaultKogitoWorkItemHandler plainHandler = new DefaultKogitoWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                workItem.getParameters().put(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLER_PROCESS_ID, "ScriptTask");
                workItem.getParameters().put(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLING_STRATEGY, "COMPLETE");
                if (workItem.getParameter("isCheckedCheckbox") != null) {
                    return Optional.of(this.workItemLifeCycle.newTransition("complete", workItem.getPhaseStatus(), workItem.getParameters()));
                }
                throw new RuntimeException("Simulated failure in wrapped WIH");
            }
        };

        ProcessWorkItemHandlerExceptionHandler decoratedHandler = new ProcessWorkItemHandlerExceptionHandler(plainHandler);

        Application application = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(application, "Human Task", decoratedHandler);
        org.kie.kogito.process.Process<UserTaskWithBooleanOutputModel> processDefinition = UserTaskWithBooleanOutputProcess.newProcess(application);
        ScriptTaskProcess.newProcess(application);

        UserTaskWithBooleanOutputModel model = processDefinition.createModel();
        model.setIsChecked(false);
        org.kie.kogito.process.ProcessInstance<UserTaskWithBooleanOutputModel> instance = processDefinition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getIsChecked()).isTrue();
    }

    @Test
    public void testDecoratorWithStrategyRetry() throws Exception {
        DefaultKogitoWorkItemHandler plainHandler = new DefaultKogitoWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                workItem.getParameters().put(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLER_PROCESS_ID, "ScriptTask");
                workItem.getParameters().put(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLING_STRATEGY, "RETRY");
                if (workItem.getParameter("isCheckedCheckbox") != null) {
                    return Optional.of(this.workItemLifeCycle.newTransition("complete", workItem.getPhaseStatus(), workItem.getParameters()));
                }
                throw new RuntimeException("Simulated failure in wrapped WIH");
            }
        };

        ProcessWorkItemHandlerExceptionHandler decoratedHandler = new ProcessWorkItemHandlerExceptionHandler(plainHandler);

        Application application = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(application, "Human Task", decoratedHandler);
        org.kie.kogito.process.Process<UserTaskWithBooleanOutputModel> processDefinition = UserTaskWithBooleanOutputProcess.newProcess(application);
        ScriptTaskProcess.newProcess(application);

        org.kie.kogito.process.ProcessInstance<UserTaskWithBooleanOutputModel> instance = processDefinition.createInstance(processDefinition.createModel());
        instance.start();

        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getIsChecked()).isTrue();
    }

    @Test
    public void testDecoratorWithStrategyAbort() throws Exception {
        DefaultKogitoWorkItemHandler plainHandler = new DefaultKogitoWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                workItem.getParameters().put(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLER_PROCESS_ID, "ScriptTask");
                workItem.getParameters().put(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLING_STRATEGY, "ABORT");
                throw new RuntimeException("Simulated failure in wrapped WIH");
            }
        };

        ProcessWorkItemHandlerExceptionHandler decoratedHandler = new ProcessWorkItemHandlerExceptionHandler(plainHandler);

        Application application = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(application, "Human Task", decoratedHandler);
        org.kie.kogito.process.Process<UserTaskWithBooleanOutputModel> processDefinition = UserTaskWithBooleanOutputProcess.newProcess(application);
        ScriptTaskProcess.newProcess(application);

        UserTaskWithBooleanOutputModel model = processDefinition.createModel();
        model.setIsChecked(false);
        org.kie.kogito.process.ProcessInstance<UserTaskWithBooleanOutputModel> instance = processDefinition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getIsChecked()).isFalse();
    }

    @Test
    public void testDecoratorWithStrategyRethrow() throws Exception {
        DefaultKogitoWorkItemHandler plainHandler = new DefaultKogitoWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                workItem.getParameters().put(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLER_PROCESS_ID, "ScriptTask");
                workItem.getParameters().put(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLING_STRATEGY, "RETHROW");
                throw new RuntimeException("Simulated failure in wrapped WIH");
            }
        };

        ProcessWorkItemHandlerExceptionHandler decoratedHandler = new ProcessWorkItemHandlerExceptionHandler(plainHandler);

        Application application = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(application, "Human Task", decoratedHandler);
        org.kie.kogito.process.Process<UserTaskWithBooleanOutputModel> processDefinition = UserTaskWithBooleanOutputProcess.newProcess(application);
        ScriptTaskProcess.newProcess(application);

        UserTaskWithBooleanOutputModel model = processDefinition.createModel();
        model.setIsChecked(false);
        org.kie.kogito.process.ProcessInstance<UserTaskWithBooleanOutputModel> instance = processDefinition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_ERROR);
        assertThat(instance.variables().getIsChecked()).isFalse();
    }
}
