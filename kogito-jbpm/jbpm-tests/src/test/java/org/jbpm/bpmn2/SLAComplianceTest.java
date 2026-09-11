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

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jbpm.bpmn2.objects.TestWorkItemHandler;
import org.jbpm.bpmn2.sla.IntermediateCatchEventSignalWithSLAOnEventModel;
import org.jbpm.bpmn2.sla.IntermediateCatchEventSignalWithSLAOnEventProcess;
import org.jbpm.bpmn2.sla.UserTaskWithSLAExprModel;
import org.jbpm.bpmn2.sla.UserTaskWithSLAExprProcess;
import org.jbpm.bpmn2.sla.UserTaskWithSLAModel;
import org.jbpm.bpmn2.sla.UserTaskWithSLAOnTaskModel;
import org.jbpm.bpmn2.sla.UserTaskWithSLAOnTaskProcess;
import org.jbpm.bpmn2.sla.UserTaskWithSLAProcess;
import org.jbpm.process.workitem.builtin.SystemOutWorkItemHandler;
import org.jbpm.test.utils.ProcessTestHelper;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.jbpm.workflow.instance.node.EventNodeInstance;
import org.jbpm.workflow.instance.node.HumanTaskNodeInstance;
import org.junit.jupiter.api.Test;
import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.SLAViolatedEvent;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.kogito.Application;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.kie.kogito.internal.process.event.KogitoProcessEventListener;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.SignalFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jbpm.test.utils.ProcessTestHelper.executeInWorkflowState;

public class SLAComplianceTest extends JbpmBpmn2TestCase {

    @Test
    public void testSLAonProcessViolated() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger processSlaCompliance = new AtomicInteger(-1);

        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {

            @Override
            public void afterSLAViolated(SLAViolatedEvent event) {
                latch.countDown();
            }

            @Override
            public void afterProcessCompleted(ProcessCompletedEvent event) {
                WorkflowProcessInstanceImpl impl = (WorkflowProcessInstanceImpl) event.getProcessInstance();
                processSlaCompliance.set(impl.getSlaCompliance());
            }

        };

        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<UserTaskWithSLAModel> processDefinition = UserTaskWithSLAProcess.newProcess(app);
        ProcessInstance<UserTaskWithSLAModel> processInstance = processDefinition.createInstance(processDefinition.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameter("ActorId")).isEqualTo("john");

        boolean slaViolated = latch.await(10, TimeUnit.SECONDS);
        assertThat(slaViolated).as("SLA was not violated while it is expected").isTrue();

        processInstance = processDefinition.instances().findById(processInstance.id()).orElse(null);
        assertThat(processInstance).isNotNull();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        int slaCompliance = executeInWorkflowState(processInstance, p -> p.getSlaCompliance());
        assertThat(slaCompliance).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_VIOLATED);

        ProcessTestHelper.completeWorkItem(processInstance, Collections.emptyMap(), "john");
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

        assertThat(processSlaCompliance.get()).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_VIOLATED);
    }

    @Test
    public void testSLAonProcessMet() throws Exception {
        AtomicInteger processSlaCompliance = new AtomicInteger(-1);

        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {

            @Override
            public void afterProcessCompleted(ProcessCompletedEvent event) {
                WorkflowProcessInstanceImpl impl = (WorkflowProcessInstanceImpl) event.getProcessInstance();
                processSlaCompliance.set(impl.getSlaCompliance());
            }

        };

        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<UserTaskWithSLAModel> processDefinition = UserTaskWithSLAProcess.newProcess(app);
        ProcessInstance<UserTaskWithSLAModel> processInstance = processDefinition.createInstance(processDefinition.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameter("ActorId")).isEqualTo("john");
        ProcessTestHelper.completeWorkItem(processInstance, Collections.emptyMap(), "john");
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

        assertThat(processSlaCompliance.get()).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_MET);
    }

    @Test
    public void testSLAonUserTaskViolated() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger processSlaCompliance = new AtomicInteger(-1);
        AtomicInteger nodeSlaComplianceAtViolation = new AtomicInteger(-1);
        AtomicInteger nodeSlaComplianceAfterCompletion = new AtomicInteger(-1);

        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {

            @Override
            public void afterSLAViolated(SLAViolatedEvent event) {
                if (event.getNodeInstance() instanceof HumanTaskNodeInstance nodeInstance) {
                    nodeSlaComplianceAtViolation.set(nodeInstance.getSlaCompliance());
                }
                latch.countDown();
            }

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance() instanceof HumanTaskNodeInstance nodeInstance) {
                    nodeSlaComplianceAfterCompletion.set(nodeInstance.getSlaCompliance());
                }
            }

            @Override
            public void afterProcessCompleted(ProcessCompletedEvent event) {
                WorkflowProcessInstanceImpl impl = (WorkflowProcessInstanceImpl) event.getProcessInstance();
                processSlaCompliance.set(impl.getSlaCompliance());
            }

        };

        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<UserTaskWithSLAOnTaskModel> processDefinition = UserTaskWithSLAOnTaskProcess.newProcess(app);
        ProcessInstance<UserTaskWithSLAOnTaskModel> processInstance = processDefinition.createInstance(processDefinition.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameter("ActorId")).isEqualTo("john");

        boolean slaViolated = latch.await(10, TimeUnit.SECONDS);
        assertThat(slaViolated).as("SLA was not violated while it is expected").isTrue();

        processInstance = processDefinition.instances().findById(processInstance.id()).orElse(null);
        assertThat(processInstance).isNotNull();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        int slaCompliance = executeInWorkflowState(processInstance, p -> p.getSlaCompliance());
        assertThat(slaCompliance).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_NA);

        Collection<NodeInstance> active = executeInWorkflowState(processInstance, p -> p.getNodeInstances());
        assertThat(active).hasSize(1);

        assertThat(nodeSlaComplianceAtViolation.get()).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_VIOLATED);

        ProcessTestHelper.completeWorkItem(processInstance, Collections.emptyMap(), "john");
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

        assertThat(processSlaCompliance.get()).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_NA);
        assertThat(nodeSlaComplianceAfterCompletion.get()).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_VIOLATED);

    }

    @Test
    public void testSLAonUserTaskMet() {
        AtomicInteger processSlaCompliance = new AtomicInteger(-1);
        AtomicInteger nodeSlaCompliance = new AtomicInteger(-1);

        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance() instanceof HumanTaskNodeInstance nodeInstance) {
                    nodeSlaCompliance.set(nodeInstance.getSlaCompliance());
                }
            }

            @Override
            public void afterProcessCompleted(ProcessCompletedEvent event) {
                WorkflowProcessInstanceImpl impl = (WorkflowProcessInstanceImpl) event.getProcessInstance();
                processSlaCompliance.set(impl.getSlaCompliance());
            }

        };

        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<UserTaskWithSLAOnTaskModel> processDefinition = UserTaskWithSLAOnTaskProcess.newProcess(app);
        ProcessInstance<UserTaskWithSLAOnTaskModel> processInstance = processDefinition.createInstance(processDefinition.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameter("ActorId")).isEqualTo("john");
        processInstance = processDefinition.instances().findById(processInstance.id()).orElse(null);
        assertThat(processInstance).isNotNull();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        Collection<NodeInstance> activeNodes = executeInWorkflowState(processInstance, p -> p.getNodeInstances());
        assertThat(activeNodes).hasSize(1);
        ProcessTestHelper.completeWorkItem(processInstance, Collections.emptyMap(), "john");
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

        assertThat(processSlaCompliance.get()).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_NA);
        assertThat(nodeSlaCompliance.get()).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_MET);
    }

    @Test
    public void testSLAonProcessViolatedWithExpression() throws Exception {
        AtomicInteger processSlaCompliance = new AtomicInteger(-1);

        CountDownLatch latch = new CountDownLatch(1);
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterSLAViolated(SLAViolatedEvent event) {
                latch.countDown();
            }

            @Override
            public void afterProcessCompleted(ProcessCompletedEvent event) {
                WorkflowProcessInstanceImpl impl = (WorkflowProcessInstanceImpl) event.getProcessInstance();
                processSlaCompliance.set(impl.getSlaCompliance());
            }
        };

        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<UserTaskWithSLAExprModel> processDefinition = UserTaskWithSLAExprProcess.newProcess(app);
        UserTaskWithSLAExprModel model = processDefinition.createModel();
        model.setS("3s");
        ProcessInstance<UserTaskWithSLAExprModel> processInstance = processDefinition.createInstance(model);

        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameter("ActorId")).isEqualTo("john");

        boolean slaViolated = latch.await(10, TimeUnit.SECONDS);
        assertThat(slaViolated).as("SLA was not violated while it is expected").isTrue();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        int slaCompliance = executeInWorkflowState(processInstance, p -> p.getSlaCompliance());
        assertThat(slaCompliance).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_VIOLATED);

        ProcessTestHelper.completeWorkItem(processInstance, Collections.emptyMap(), "john");
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(processSlaCompliance.get()).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_VIOLATED);
    }

    @Test
    public void testSLAonCatchEventViolated() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger processSlaCompliance = new AtomicInteger(-1);
        AtomicInteger nodeSlaComplianceAtViolation = new AtomicInteger(-1);
        AtomicInteger nodeSlaComplianceAfterCompletion = new AtomicInteger(-1);

        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {

            @Override
            public void afterSLAViolated(SLAViolatedEvent event) {
                if (event.getNodeInstance() instanceof EventNodeInstance nodeInstance) {
                    nodeSlaComplianceAtViolation.set(nodeInstance.getSlaCompliance());
                }
                latch.countDown();
            }

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance() instanceof EventNodeInstance nodeInstance) {
                    nodeSlaComplianceAfterCompletion.set(nodeInstance.getSlaCompliance());
                }
            }

            @Override
            public void afterProcessCompleted(ProcessCompletedEvent event) {
                WorkflowProcessInstanceImpl impl = (WorkflowProcessInstanceImpl) event.getProcessInstance();
                processSlaCompliance.set(impl.getSlaCompliance());
            }

        };

        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<IntermediateCatchEventSignalWithSLAOnEventModel> processDefinition =
                IntermediateCatchEventSignalWithSLAOnEventProcess.newProcess(app);
        ProcessInstance<IntermediateCatchEventSignalWithSLAOnEventModel> processInstance = processDefinition.createInstance(processDefinition.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        boolean slaViolated = latch.await(5, TimeUnit.SECONDS);
        assertThat(slaViolated).as("SLA should be violated by timer").isTrue();

        processInstance = processDefinition.instances().findById(processInstance.id()).orElse(null);
        assertThat(processInstance).isNotNull();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        Collection<NodeInstance> active = executeInWorkflowState(processInstance, p -> p.getNodeInstances());
        assertThat(active).hasSize(1);

        assertThat(nodeSlaComplianceAtViolation.get()).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_VIOLATED);

        processInstance.send(SignalFactory.of("MyMessage", null));

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

        assertThat(processSlaCompliance.get()).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_NA);
        assertThat(nodeSlaComplianceAfterCompletion.get()).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_VIOLATED);

    }

    @Test
    public void testSLAonCatchEventNotViolated() throws Exception {
        AtomicInteger processSlaCompliance = new AtomicInteger(-1);
        AtomicInteger nodeSlaCompliance = new AtomicInteger(-1);

        CountDownLatch latch = new CountDownLatch(1);
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterSLAViolated(SLAViolatedEvent event) {
                latch.countDown();
            }

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance() instanceof EventNodeInstance nodeInstance) {
                    nodeSlaCompliance.set(nodeInstance.getSlaCompliance());
                }
            }

            @Override
            public void afterProcessCompleted(ProcessCompletedEvent event) {
                WorkflowProcessInstanceImpl impl = (WorkflowProcessInstanceImpl) event.getProcessInstance();
                processSlaCompliance.set(impl.getSlaCompliance());
            }
        };
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<IntermediateCatchEventSignalWithSLAOnEventModel> processDefinition = IntermediateCatchEventSignalWithSLAOnEventProcess.newProcess(app);
        ProcessInstance<IntermediateCatchEventSignalWithSLAOnEventModel> processInstance = processDefinition.createInstance(processDefinition.createModel());
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        Collection<NodeInstance> activeNodes = executeInWorkflowState(processInstance, p -> p.getNodeInstances());
        assertThat(activeNodes).hasSize(1);

        processInstance.send(SignalFactory.of("MyMessage", null));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

        assertThat(processSlaCompliance.get()).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_NA);
        assertThat(nodeSlaCompliance.get()).isEqualTo(org.kie.api.runtime.process.ProcessInstance.SLA_MET);
        boolean slaViolated = latch.await(3, TimeUnit.SECONDS);
        assertThat(slaViolated).as("SLA should not be violated by timer").isFalse();
    }

}
