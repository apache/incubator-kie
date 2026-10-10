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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jbpm.bpmn2.compensation.BookResourceProcess;
import org.jbpm.bpmn2.compensation.BookingModel;
import org.jbpm.bpmn2.compensation.BookingProcess;
import org.jbpm.bpmn2.compensation.CancelResourceProcess;
import org.jbpm.bpmn2.compensation.IntermediateThrowEventModel;
import org.jbpm.bpmn2.compensation.IntermediateThrowEventProcess;
import org.jbpm.bpmn2.compensation.ParallelOrderedCompensationIntermediateThrowEventModel;
import org.jbpm.bpmn2.compensation.ParallelOrderedCompensationIntermediateThrowEventProcess;
import org.jbpm.bpmn2.compensation.ThrowSpecificForSubProcessModel;
import org.jbpm.bpmn2.compensation.ThrowSpecificForSubProcessProcess;
import org.jbpm.bpmn2.compensation.UserTaskBeforeAssociatedActivityModel;
import org.jbpm.bpmn2.compensation.UserTaskBeforeAssociatedActivityProcess;
import org.jbpm.bpmn2.compensation.UserTaskCompensationModel;
import org.jbpm.bpmn2.compensation.UserTaskCompensationProcess;
import org.jbpm.bpmn2.objects.TestWorkItemHandler;
import org.jbpm.process.core.context.exception.CompensationScope;
import org.jbpm.process.workitem.builtin.SystemOutWorkItemHandler;
import org.jbpm.test.utils.ProcessTestHelper;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.kogito.Application;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.kie.kogito.internal.process.event.KogitoProcessEventListener;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.SignalFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class CompensationTest extends JbpmBpmn2TestCase {

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

    @BeforeEach
    public void prepare() {
        clearHistory();
    }

    /**
     * TESTS
     */

    @Test
    public void compensationViaIntermediateThrowEventProcess() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<IntermediateThrowEventModel> process = IntermediateThrowEventProcess.newProcess(app);
        IntermediateThrowEventModel model = process.createModel();
        model.setX("0");
        ProcessInstance<IntermediateThrowEventModel> processInstance = process.createInstance(model);
        processInstance.start();

        processInstance.completeWorkItem(workItemHandler.getWorkItem().getStringId(), null);

        // compensation activity (assoc. with script task) signaled *after* script task
        assertThat(processInstance.status()).isEqualTo(org.jbpm.process.instance.ProcessInstance.STATE_COMPLETED);
        assertThat(processInstance.variables().getX()).isEqualTo("1");
    }

    @Test
    public void compensationTwiceViaSignal() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<IntermediateThrowEventModel> process = IntermediateThrowEventProcess.newProcess(app);
        IntermediateThrowEventModel model = process.createModel();
        model.setX("0");
        ProcessInstance<IntermediateThrowEventModel> processInstance = process.createInstance(model);
        processInstance.start();

        processInstance.send(SignalFactory.of("Compensation", CompensationScope.IMPLICIT_COMPENSATION_PREFIX + "IntermediateThrowEvent"));
        processInstance.completeWorkItem(workItemHandler.getWorkItem().getStringId(), null);

        assertThat(processInstance.status()).isEqualTo(org.jbpm.process.instance.ProcessInstance.STATE_COMPLETED);
        assertThat(processInstance.variables().getX()).isEqualTo("2");
    }

    @Test
    public void compensationViaEventSubProcess() throws Exception {
        kruntime = createKogitoProcessRuntime("compensation/BPMN2-Compensation-EventSubProcess.bpmn2");
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        Map<String, Object> params = new HashMap<>();
        params.put("x", "0");
        KogitoProcessInstance processInstance = kruntime.startProcess("CompensationEventSubProcess", params);
        WorkflowProcessInstanceImpl pi = (WorkflowProcessInstanceImpl) processInstance;
        pi.reconnect();
        assertProcessInstanceActive(processInstance.getStringId(), kruntime);
        kruntime.getKogitoWorkItemManager().completeWorkItem(workItemHandler.getWorkItem().getStringId(), null);
        assertProcessVarValue(processInstance, "x", "1");
    }

    @Test
    public void compensationOnlyAfterAssociatedActivityHasCompleted() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerProcessEventListener(app, LOGGING_EVENT_LISTENER);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<UserTaskBeforeAssociatedActivityModel> process = UserTaskBeforeAssociatedActivityProcess.newProcess(app);
        UserTaskBeforeAssociatedActivityModel model = process.createModel();
        model.setX("0");
        ProcessInstance<UserTaskBeforeAssociatedActivityModel> processInstance = process.createInstance(model);
        processInstance.start();

        processInstance.send(SignalFactory.of("Compensation", "_3"));

        processInstance.completeWorkItem(workItemHandler.getWorkItem().getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(org.jbpm.process.instance.ProcessInstance.STATE_COMPLETED);
        assertThat(processInstance.variables().getX()).isEqualTo("1");
    }

    @Test
    public void orderedCompensation() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<ParallelOrderedCompensationIntermediateThrowEventModel> process = ParallelOrderedCompensationIntermediateThrowEventProcess.newProcess(app);
        ParallelOrderedCompensationIntermediateThrowEventModel model = process.createModel();
        model.setX("");
        ProcessInstance<ParallelOrderedCompensationIntermediateThrowEventModel> processInstance = process.createInstance(model);
        processInstance.start();

        List<KogitoWorkItem> workItems = workItemHandler.getWorkItems();
        final List<String> workItemIds = new ArrayList<>();

        workItems.stream().filter(workItem -> "Thr".equals(workItem.getParameter("NodeName")))
                .forEach(workItem -> workItemIds.add(workItem.getStringId()));

        workItems.stream().filter(workItem -> "Two".equals(workItem.getParameter("NodeName")))
                .forEach(workItem -> workItemIds.add(workItem.getStringId()));

        workItems.stream().filter(workItem -> "One".equals(workItem.getParameter("NodeName")))
                .forEach(workItem -> workItemIds.add(workItem.getStringId()));

        workItemIds.forEach(id -> processInstance.completeWorkItem(id, null));

        // user task -> script task (associated with compensation) --> intermeidate throw compensation event
        String xVal = processInstance.variables().getX();
        // Compensation happens in the *REVERSE* order of completion
        // Ex: if the order is 3, 17, 282, then compensation should happen in the order of 282, 17, 3
        // Compensation did not fire in the same order as the associated activities completed.
        assertThat(xVal).isEqualTo("_171:_131:_141:_151:");
    }

    @Test
    public void compensationInSubSubProcesses() throws Exception {
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/compensation/BPMN2-InSubSubProcess.bpmn2");
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        Map<String, Object> params = new HashMap<>();
        params.put("x", "0");
        KogitoProcessInstance processInstance = kruntime.startProcess("InSubSubProcess", params);
        WorkflowProcessInstanceImpl pi = (WorkflowProcessInstanceImpl) processInstance;
        pi.reconnect();
        kruntime.signalEvent("Compensation", "_C-2", processInstance.getStringId());

        kruntime.getKogitoWorkItemManager().completeWorkItem(workItemHandler.getWorkItem().getStringId(), null);
        kruntime.getKogitoWorkItemManager().completeWorkItem(workItemHandler.getWorkItem().getStringId(), null);
        kruntime.getKogitoWorkItemManager().completeWorkItem(workItemHandler.getWorkItem().getStringId(), null);

        // compensation activity (assoc. with script task) signaled *after* script task
        assertProcessInstanceCompleted(processInstance.getStringId(), kruntime);
        assertProcessVarValue(processInstance, "x", "2");
    }

    @Test
    public void specificCompensationOfASubProcess() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<ThrowSpecificForSubProcessModel> process = ThrowSpecificForSubProcessProcess.newProcess(app);
        ThrowSpecificForSubProcessModel model = process.createModel();
        model.setX(1);
        ProcessInstance<ThrowSpecificForSubProcessModel> processInstance = process.createInstance(model);
        processInstance.start();

        // compensation activity (assoc. with script task) signaled *after* to-compensate script task
        assertThat(processInstance.status()).isEqualTo(org.jbpm.process.instance.ProcessInstance.STATE_COMPLETED);

        assertThat(processInstance.variables().getX()).isNull();
    }

    @Test
    @Disabled
    public void compensationViaCancellation() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<IntermediateThrowEventModel> process = IntermediateThrowEventProcess.newProcess(app);
        IntermediateThrowEventModel model = process.createModel();
        model.setX("0");
        ProcessInstance<IntermediateThrowEventModel> processInstance = process.createInstance(model);
        processInstance.start();

        processInstance.send(SignalFactory.of("Cancel", null));
        processInstance.completeWorkItem(workItemHandler.getWorkItem().getStringId(), null);

        // compensation activity (assoc. with script task) signaled *after* script task
        assertThat(processInstance.status()).isEqualTo(org.jbpm.process.instance.ProcessInstance.STATE_COMPLETED);
        assertThat(processInstance.variables().getX()).isEqualTo("1");
    }

    @Test
    public void compensationInvokingSubProcess() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<UserTaskCompensationModel> process = UserTaskCompensationProcess.newProcess(app);
        UserTaskCompensationModel model = process.createModel();
        model.setCompensation("True");
        ProcessInstance<UserTaskCompensationModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(org.jbpm.process.instance.ProcessInstance.STATE_COMPLETED);
        assertThat(processInstance.variables().getCompensation()).isEqualTo("compensation");
    }

    /**
     * Test to demonstrate that Compensation Events work with Reusable
     * Subprocesses
     *
     */
    @Test
    public void compensationWithReusableSubprocess() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<BookingModel> process = BookingProcess.newProcess(app);
        BookResourceProcess.newProcess(app);
        CancelResourceProcess.newProcess(app);
        ProcessInstance<BookingModel> processInstance = process.createInstance(process.createModel());
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(org.jbpm.process.instance.ProcessInstance.STATE_COMPLETED);
    }

}
