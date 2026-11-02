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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jbpm.process.core.impl.WorkImpl;
import org.jbpm.ruleflow.core.WorkflowElementIdentifierFactory;
import org.jbpm.workflow.core.node.HumanTaskNode;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.Application;
import org.kie.kogito.MappableToModel;
import org.kie.kogito.Model;
import org.kie.kogito.config.ConfigBean;
import org.kie.kogito.internal.process.runtime.KogitoNode;
import org.kie.kogito.process.BaseEventDescription;
import org.kie.kogito.process.EventDescription;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstances;
import org.kie.kogito.process.Signal;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.process.flexible.AdHocFragment;
import org.kie.kogito.uow.UnitOfWorkManager;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ProcessServiceImpl.signalProcessInstance.
 * Verifies signal routing for traditional signal events, message events, ad hoc nodes,
 * non-existent instances, and non-matching signals.
 */
class ProcessServiceImplSignalTest {

    private ProcessServiceImpl processService;
    private org.kie.kogito.uow.UnitOfWork unitOfWork;
    private Process<TestModel> process;
    private ProcessInstances<TestModel> processInstances;
    private ProcessInstance<TestModel> processInstance;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        Application application = mock(Application.class);
        process = mock(Process.class);
        processInstances = mock(ProcessInstances.class);
        processInstance = mock(ProcessInstance.class);
        UnitOfWorkManager unitOfWorkManager = mock(UnitOfWorkManager.class);
        ConfigBean configBean = mock(ConfigBean.class);

        when(application.unitOfWorkManager()).thenReturn(unitOfWorkManager);
        when(application.config()).thenReturn(mock(org.kie.kogito.Config.class));
        when(application.config().get(ConfigBean.class)).thenReturn(configBean);
        when(configBean.processInstanceLimit()).thenReturn((short) 100);
        when(process.instances()).thenReturn(processInstances);

        // Make the UoW execute the supplied callable immediately
        unitOfWork = mock(org.kie.kogito.uow.UnitOfWork.class);
        when(unitOfWorkManager.newUnitOfWork()).thenReturn(unitOfWork);
        when(unitOfWorkManager.currentUnitOfWork()).thenReturn(unitOfWork);
        doAnswer(invocation -> {
            invocation.<org.kie.kogito.uow.WorkUnit<?>> getArgument(0).perform();
            return null;
        }).when(unitOfWork).intercept(any());

        processService = new ProcessServiceImpl(application);
    }

    // --- helpers ---

    private static EventDescription<?> eventDesc(String eventName) {
        return new BaseEventDescription(eventName, "node-1", "Node", "signal", "ni-1", "pi-1", null);
    }

    private void givenInstanceWithEvents(String id, Set<EventDescription<?>> events, List<AdHocFragment> adHocFragments) {
        when(processInstances.findById(id)).thenReturn(Optional.of(processInstance));
        when(processInstance.events()).thenReturn(events);
        when(processInstance.adHocFragments()).thenReturn(adHocFragments);
        when(processInstance.checkError()).thenReturn(processInstance);
        when(processInstance.variables()).thenReturn(new TestModel());
    }

    // --- signal event tests ---

    @Test
    void signalAccepted_whenInstanceWaitingForSignalEvent() {
        String id = "pi-1";
        String signalName = "HelloMartin";

        givenInstanceWithEvents(id, Set.of(eventDesc(signalName)), Collections.emptyList());

        Optional<TestModel> result = processService.signalProcessInstance(process, id, "data", signalName);

        assertThat(result).isPresent();
        verify(processInstance).send(any());
    }

    @Test
    void signalAccepted_whenInstanceHasMatchingAdHocFragment() {
        String id = "pi-3";
        String adHocName = "AdHocTask";
        AdHocFragment fragment = new AdHocFragment.Builder(adHocName).withName(adHocName).withAutoStart(false).build();

        givenInstanceWithEvents(id, Collections.emptySet(), List.of(fragment));

        Optional<TestModel> result = processService.signalProcessInstance(process, id, null, adHocName);

        assertThat(result).isPresent();
        verify(processInstance).send(any());
    }

    @Test
    void signalAccepted_whenInstanceHasBothSignalEventAndAdHocFragment() {
        String id = "pi-4";
        String signalName = "HelloMartin";
        String adHocName = "AdHocTask";
        AdHocFragment fragment = new AdHocFragment.Builder(adHocName).withName(adHocName).withAutoStart(false).build();

        givenInstanceWithEvents(id, Set.of(eventDesc(signalName)), List.of(fragment));

        assertThat(processService.signalProcessInstance(process, id, "data", signalName)).isPresent();
        assertThat(processService.signalProcessInstance(process, id, null, adHocName)).isPresent();
    }

    @Test
    void signalAccepted_whenInstanceHasMultipleAdHocFragments() {
        String id = "pi-5";
        String node1 = "AdHocTask1";
        String node2 = "AdHocTask2";
        AdHocFragment f1 = new AdHocFragment.Builder(node1).withName(node1).withAutoStart(false).build();
        AdHocFragment f2 = new AdHocFragment.Builder(node2).withName(node2).withAutoStart(true).build();

        givenInstanceWithEvents(id, Collections.emptySet(), List.of(f1, f2));

        assertThat(processService.signalProcessInstance(process, id, null, node1)).isPresent();
        assertThat(processService.signalProcessInstance(process, id, null, node2)).isPresent();
    }

    @Test
    void signalRejected_whenEventDescriptionContainsRawExpressionInsteadOfResolvedName() {
        // Regression: before the fix, getEventDescriptions() could return the raw #{signalName}
        // expression instead of the resolved value. The filter must not match it.
        String id = "pi-expr";
        String signalName = "myVarSignal";

        givenInstanceWithEvents(id, Set.of(eventDesc("#{signalName}")), Collections.emptyList());

        Optional<TestModel> result = processService.signalProcessInstance(process, id, "data", signalName);

        assertThat(result).isEmpty();
    }

    // --- rejection / empty result tests ---

    @Test
    void signalRejected_whenInstanceNotWaitingForSignal() {
        String id = "pi-6";

        givenInstanceWithEvents(id, Set.of(eventDesc("OtherSignal")), Collections.emptyList());

        Optional<TestModel> result = processService.signalProcessInstance(process, id, "data", "HelloMartin");

        assertThat(result).isEmpty();
    }

    @Test
    void signalThrows_whenSignalNameIsNull() {
        assertThatThrownBy(() -> processService.signalProcessInstance(process, "pi-1", "data", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("signalName");
    }

    @Test
    void signalRejected_whenInstanceDoesNotExist() {
        when(processInstances.findById("non-existent")).thenReturn(Optional.empty());

        Optional<TestModel> result = processService.signalProcessInstance(process, "non-existent", "data", "HelloMartin");

        assertThat(result).isEmpty();
    }

    @Test
    void signalRejected_whenInstanceHasNoEventsAndNoAdHocFragments() {
        String id = "pi-7";

        givenInstanceWithEvents(id, Collections.emptySet(), Collections.emptyList());

        Optional<TestModel> result = processService.signalProcessInstance(process, id, "data", "HelloMartin");

        assertThat(result).isEmpty();
    }

    // --- process level signal tests ---

    @Test
    void signalProcess_sendsSignalWithinUnitOfWork() {
        processService.signalProcess(process, "data", "Message-test");

        ArgumentCaptor<Signal> signal = ArgumentCaptor.forClass(Signal.class);
        InOrder order = inOrder(unitOfWork, process);
        order.verify(unitOfWork).start();
        order.verify(process).send(signal.capture());
        order.verify(unitOfWork).end();
        assertThat(signal.getValue().channel()).isEqualTo("Message-test");
        assertThat(signal.getValue().payload()).isEqualTo("data");
    }

    @Test
    void signalProcess_abortsUnitOfWorkWhenSendFails() {
        doThrow(new IllegalStateException("boom")).when(process).send(any());

        assertThatThrownBy(() -> processService.signalProcess(process, "data", "Message-test"))
                .isInstanceOf(IllegalStateException.class);

        verify(unitOfWork).abort();
        verify(unitOfWork, never()).end();
    }

    @Test
    void signalProcess_throwsWhenSignalNameIsNull() {
        assertThatThrownBy(() -> processService.signalProcess(process, "data", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("signalName");
    }

    // --- work item trigger tests ---

    private static HumanTaskNode humanTask(String id, String name, String taskName) {
        HumanTaskNode node = new HumanTaskNode();
        node.setId(WorkflowElementIdentifierFactory.fromExternalFormat(id));
        node.setName(name);
        node.getWork().setParameter("TaskName", taskName);
        return node;
    }

    private static WorkItemNode regularWorkItem(String id, String name) {
        WorkItemNode node = new WorkItemNode();
        node.setId(WorkflowElementIdentifierFactory.fromExternalFormat(id));
        node.setName(name);
        WorkImpl work = new WorkImpl();
        work.setName("Rest");
        node.setWork(work);
        return node;
    }

    private static WorkItem workItemOf(WorkItemNode node) {
        WorkItem workItem = mock(WorkItem.class);
        when(workItem.getNodeId()).thenReturn(node.getId());
        when(workItem.getId()).thenReturn("wi-" + node.getId().toExternalFormat());
        return workItem;
    }

    private void givenWorkItemNodes(String id, List<WorkItemNode> nodes, List<WorkItem> workItems) {
        when(processInstances.findById(id)).thenReturn(Optional.of(processInstance));
        when(processInstance.process()).thenReturn(process);
        when(process.findNodes(any())).thenAnswer(invocation -> {
            Predicate<KogitoNode> filter = invocation.getArgument(0);
            return nodes.stream().map(KogitoNode.class::cast).filter(filter).toList();
        });
        when(processInstance.workItems()).thenReturn(workItems);
    }

    private List<String> signalsSent() {
        ArgumentCaptor<Signal> signal = ArgumentCaptor.forClass(Signal.class);
        verify(processInstance, atLeast(0)).send(signal.capture());
        return signal.getAllValues().stream().map(Signal::channel).toList();
    }

    @Test
    void signalWorkItem_regularWorkItemIsResolvedByNodeNameWithSpaces() {
        WorkItemNode first = regularWorkItem("1", "Rest Call 1");
        WorkItemNode second = regularWorkItem("2", "Rest Call 2");
        WorkItem secondWorkItem = workItemOf(second);
        givenWorkItemNodes("pi-1", List.of(first, second), List.of(workItemOf(first), secondWorkItem));

        Optional<WorkItem> result = processService.signalWorkItem(process, "pi-1", "Rest Call 2");

        assertThat(result).containsSame(secondWorkItem);
        assertThat(signalsSent()).containsExactly("Rest Call 2");
    }

    @Test
    void signalWorkItem_regularWorkItemIsResolvedBySanitizedNodeName() {
        WorkItemNode first = regularWorkItem("1", "Rest Call 1");
        WorkItemNode second = regularWorkItem("2", "Rest Call 2");
        WorkItem secondWorkItem = workItemOf(second);
        givenWorkItemNodes("pi-1", List.of(first, second), List.of(workItemOf(first), secondWorkItem));

        Optional<WorkItem> result = processService.signalWorkItem(process, "pi-1", "Rest_Call_2");

        assertThat(result).containsSame(secondWorkItem);
        assertThat(signalsSent()).containsExactly("Rest Call 2");
    }

    @Test
    void signalWorkItem_humanTasksSharingTaskNameAreAllSignaled() {
        HumanTaskNode first = humanTask("1", "User Task 1", "Shared Task");
        HumanTaskNode second = humanTask("2", "User Task 2", "Shared Task");
        HumanTaskNode other = humanTask("3", "User Task 3", "Other");
        WorkItem firstWorkItem = workItemOf(first);
        givenWorkItemNodes("pi-1", List.of(first, second, other), List.of(firstWorkItem, workItemOf(other)));

        Optional<WorkItem> result = processService.signalWorkItem(process, "pi-1", "Shared Task");

        assertThat(result).containsSame(firstWorkItem);
        assertThat(signalsSent()).containsExactlyInAnyOrder("User Task 1", "User Task 2");
    }

    @Test
    void signalWorkItem_humanTasksSharingTaskNameAreResolvedBySanitizedTaskName() {
        HumanTaskNode first = humanTask("1", "User Task 1", "Shared Task");
        HumanTaskNode second = humanTask("2", "User Task 2", "Shared Task");
        WorkItem firstWorkItem = workItemOf(first);
        givenWorkItemNodes("pi-1", List.of(first, second), List.of(firstWorkItem, workItemOf(second)));

        Optional<WorkItem> result = processService.signalWorkItem(process, "pi-1", "Shared_Task");

        assertThat(result).containsSame(firstWorkItem);
        assertThat(signalsSent()).containsExactlyInAnyOrder("User Task 1", "User Task 2");
    }

    @Test
    void signalWorkItem_workItemIsResolvedByTaskNameParameterWhenPresent() {
        WorkItemNode node = regularWorkItem("1", "Rest Call");
        node.getWork().setParameter("TaskName", "Rest");
        WorkItem workItem = workItemOf(node);
        givenWorkItemNodes("pi-1", List.of(node), List.of(workItem));

        assertThat(processService.signalWorkItem(process, "pi-1", "Rest")).containsSame(workItem);
        assertThat(signalsSent()).containsExactly("Rest Call");
    }

    @Test
    void signalWorkItem_unknownNameReturnsEmpty() {
        WorkItemNode node = regularWorkItem("1", "Rest Call");
        givenWorkItemNodes("pi-1", List.of(node), List.of(workItemOf(node)));

        assertThat(processService.signalWorkItem(process, "pi-1", "Unknown")).isEmpty();
        assertThat(signalsSent()).isEmpty();
    }

    @Test
    void signalWorkItem_unknownInstanceReturnsEmpty() {
        when(processInstances.findById("missing")).thenReturn(Optional.empty());

        assertThat(processService.signalWorkItem(process, "missing", "Rest Call")).isEmpty();
    }

    // --- test model ---

    static class TestModel implements MappableToModel<TestModel>, Model {
        @Override
        public TestModel toModel() {
            return this;
        }
    }
}
