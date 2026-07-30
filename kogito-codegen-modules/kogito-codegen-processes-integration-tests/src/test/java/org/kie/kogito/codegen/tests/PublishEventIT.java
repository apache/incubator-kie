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
package org.kie.kogito.codegen.tests;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.kie.kogito.Application;
import org.kie.kogito.Model;
import org.kie.kogito.auth.SecurityPolicy;
import org.kie.kogito.codegen.AbstractCodegenIT;
import org.kie.kogito.event.DataEvent;
import org.kie.kogito.event.EventPublisher;
import org.kie.kogito.event.process.ProcessInstanceErrorDataEvent;
import org.kie.kogito.event.process.ProcessInstanceNodeDataEvent;
import org.kie.kogito.event.process.ProcessInstanceNodeEventBody;
import org.kie.kogito.event.process.ProcessInstanceStateDataEvent;
import org.kie.kogito.event.process.ProcessInstanceStateEventBody;
import org.kie.kogito.event.process.ProcessInstanceVariableDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceStateDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceStateEventBody;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.workitem.Policy;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessError;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.Processes;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.uow.UnitOfWork;
import org.kie.kogito.usertask.UserTaskConfig;
import org.kie.kogito.usertask.UserTaskEventListener;
import org.kie.kogito.usertask.events.UserTaskStateEvent;
import org.kie.kogito.usertask.impl.DefaultUserTaskEventListenerConfig;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class PublishEventIT extends AbstractCodegenIT {

    private Policy securityPolicy = SecurityPolicy.of("john", emptyList());

    @Test
    public void testProcessWithMilestoneEvents() throws Exception {
        Application app = generateCodeProcessesOnly("cases/milestones/SimpleMilestone.bpmn");

        assertThat(app).isNotNull();
        TestEventPublisher publisher = new TestEventPublisher();
        app.unitOfWorkManager().eventManager().setService("http://myhost");
        app.unitOfWorkManager().eventManager().addPublisher(publisher);

        UnitOfWork uow = app.unitOfWorkManager().newUnitOfWork();
        uow.start();

        Process<? extends Model> p = app.get(Processes.class).processById("TestCase.SimpleMilestone");

        ProcessInstance<?> processInstance = p.createInstance(p.createModel());
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

        uow.end();

        List<DataEvent<?>> events = publisher.extract();

        DataEvent<?> event = findProcessInstanceEvent(events, ProcessInstance.STATE_COMPLETED).get();
        assertThat(event).isInstanceOf(ProcessInstanceStateDataEvent.class);
        ProcessInstanceStateDataEvent processDataEvent = (ProcessInstanceStateDataEvent) event;
        assertThat(processDataEvent.getKogitoProcessInstanceId()).isNotNull();
        assertThat(processDataEvent.getKogitoProcessVersion()).isEqualTo("1.0");
        assertThat(processDataEvent.getKogitoParentProcessInstanceId()).isNull();
        assertThat(processDataEvent.getKogitoRootProcessInstanceId()).isNull();
        assertThat(processDataEvent.getKogitoProcessId()).isEqualTo("TestCase.SimpleMilestone");
        assertThat(processDataEvent.getKogitoProcessInstanceState()).isEqualTo("2");
        assertThat(processDataEvent.getSource()).hasToString("http://myhost/SimpleMilestone");

        List<ProcessInstanceNodeDataEvent> milestoneEvents = events.stream().filter(ProcessInstanceNodeDataEvent.class::isInstance).map(ProcessInstanceNodeDataEvent.class::cast)
                .filter(e -> e.getData().getNodeType().equals("MilestoneNode") && e.getData().getEventType() == ProcessInstanceNodeEventBody.EVENT_TYPE_EXIT).collect(Collectors.toList());

        assertThat(milestoneEvents)
                .extracting(e -> e.getData().getNodeName(), e -> e.getData().getEventType())
                .containsExactlyInAnyOrder(tuple("AutoStartMilestone", ProcessInstanceNodeEventBody.EVENT_TYPE_EXIT), tuple("SimpleMilestone", ProcessInstanceNodeEventBody.EVENT_TYPE_EXIT));

    }

    @Test
    public void testCompensationProcess() throws Exception {
        Map<TYPE, List<String>> resourcesTypeMap = new HashMap<>();
        resourcesTypeMap.put(TYPE.PROCESS, Collections.singletonList("compensation/compensateAll.bpmn2"));
        Application app = generateCode(resourcesTypeMap);
        assertThat(app).isNotNull();
        TestEventPublisher publisher = new TestEventPublisher();
        app.unitOfWorkManager().eventManager().setService("http://myhost");
        app.unitOfWorkManager().eventManager().addPublisher(publisher);

        UnitOfWork uow = app.unitOfWorkManager().newUnitOfWork();
        uow.start();

        Process<? extends Model> p = app.get(Processes.class).processById("compensateAll");

        ProcessInstance<?> processInstance = p.createInstance(p.createModel());
        processInstance.start();
        uow.end();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        Model result = (Model) processInstance.variables();
        assertThat(result.toMap()).hasSize(2).containsKeys("counter", "counter2");

        List<DataEvent<?>> events = publisher.extract();

        Optional<DataEvent<?>> event = findProcessInstanceEvent(events, ProcessInstance.STATE_COMPLETED);
        assertThat(event).as("There is no process instance event being published").isPresent();
        ProcessInstanceStateDataEvent processDataEvent = (ProcessInstanceStateDataEvent) event.orElseThrow();
        assertThat(processDataEvent.getKogitoProcessInstanceId()).isNotNull();
        assertThat(processDataEvent.getKogitoProcessVersion()).isEqualTo("1.0");
        assertThat(processDataEvent.getKogitoParentProcessInstanceId()).isNull();
        assertThat(processDataEvent.getKogitoRootProcessInstanceId()).isNull();
        assertThat(processDataEvent.getKogitoProcessId()).isEqualTo("compensateAll");
        assertThat(processDataEvent.getKogitoProcessInstanceState()).isEqualTo("2");
        assertThat(processDataEvent.getSource()).hasToString("http://myhost/compensateAll");

        assertProcessInstanceEvent(event.get(), "compensateAll", "Compensate All", ProcessInstanceStateEventBody.EVENT_TYPE_ENDED);

        List<ProcessInstanceNodeEventBody> nodes = findNodeInstanceEvents(events, ProcessInstanceNodeEventBody.EVENT_TYPE_EXIT);
        assertThat(nodes).hasSize(9).extractingResultOf("getNodeType").contains("StartNode", "ActionNode", "BoundaryEventNode", "EndNode");

    }

    private Optional<UserTaskInstanceStateDataEvent> findUserTaskInstanceEvent(List<DataEvent<?>> events, String taskName, String status) {
        return events.stream().filter(UserTaskInstanceStateDataEvent.class::isInstance)
                .map(e -> (UserTaskInstanceStateDataEvent) e)
                .filter(e -> status.equals(e.getData().getState()) && e.getData().getUserTaskName().equals(taskName))
                .findAny();
    }

    private Optional<DataEvent<?>> findProcessInstanceEvent(List<DataEvent<?>> events, int state) {
        return events.stream().filter(ProcessInstanceStateDataEvent.class::isInstance).filter(e -> ((ProcessInstanceStateEventBody) e.getData()).getEventType() == state).findAny();
    }

    private List<ProcessInstanceNodeEventBody> findNodeInstanceEvents(List<DataEvent<?>> events, int eventType) {
        return events.stream().filter(ProcessInstanceNodeDataEvent.class::isInstance).map(e -> (ProcessInstanceNodeEventBody) e.getData()).filter(e -> e.getEventType() == eventType)
                .collect(Collectors.toList());
    }

    @Test
    public void testBasicUserTaskProcess() throws Exception {
        Application app = generateCodeProcessesOnly("usertask/UserTasksProcess.bpmn2");
        assertThat(app).isNotNull();

        ((DefaultUserTaskEventListenerConfig) app.config().get(UserTaskConfig.class).userTaskEventListeners()).addUserTaskEventListener(new UserTaskEventListener() {

            @Override
            public void onUserTaskState(UserTaskStateEvent event) {
                System.out.println(event);

            }

        });

        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        TestEventPublisher publisher = new TestEventPublisher();
        app.unitOfWorkManager().eventManager().setService("http://myhost");
        app.unitOfWorkManager().eventManager().addPublisher(publisher);

        UnitOfWork uow = app.unitOfWorkManager().newUnitOfWork();
        uow.start();

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();
        uow.end();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        List<DataEvent<?>> events = publisher.extract();

        Optional<DataEvent<?>> processEvent = findProcessInstanceEvent(events, ProcessInstance.STATE_ACTIVE);
        assertProcessInstanceEvent(processEvent.get(), "UserTasksProcess", "UserTasksProcess", 1);

        List<ProcessInstanceNodeEventBody> triggered = findNodeInstanceEvents(events, 1);
        assertThat(triggered).hasSize(2).extractingResultOf("getNodeType").containsOnly("StartNode", "HumanTaskNode");

        List<ProcessInstanceNodeEventBody> left = findNodeInstanceEvents(events, 2);
        assertThat(left).hasSize(1).extractingResultOf("getNodeType").containsOnly("StartNode");

        Optional<UserTaskInstanceStateDataEvent> userFirstTask = findUserTaskInstanceEvent(events, "FirstTask", "Ready");
        assertThat(userFirstTask).isPresent();
        assertUserTaskInstanceEvent(userFirstTask.get(), "FirstTask", null, "1", "Ready", "UserTasksProcess", "First Task");

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        assertThat(workItems.get(0).getName()).isEqualTo("FirstTask");

        uow = app.unitOfWorkManager().newUnitOfWork();
        uow.start();
        processInstance.completeWorkItem(workItems.get(0).getId(), null, securityPolicy);
        uow.end();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        events = publisher.extract();

        triggered = findNodeInstanceEvents(events, 1);
        assertThat(triggered).hasSize(1).extractingResultOf("getNodeType").containsOnly("HumanTaskNode");

        left = findNodeInstanceEvents(events, 1);
        assertThat(left).hasSize(1).extractingResultOf("getNodeType").containsOnly("HumanTaskNode");

        Optional<UserTaskInstanceStateDataEvent> firstUserTaskInstance = findUserTaskInstanceEvent(events, "SecondTask", "Ready");
        // we completed through the work item handler so the user task is really obsolete
        Optional<UserTaskInstanceStateDataEvent> secondUserTaskInstance = findUserTaskInstanceEvent(events, "FirstTask", "Obsolete");

        assertUserTaskInstanceEvent(firstUserTaskInstance.get(), "SecondTask", null, "1", "Ready", "UserTasksProcess", "Second Task");
        assertUserTaskInstanceEvent(secondUserTaskInstance.get(), "FirstTask", null, "1", "Obsolete", "UserTasksProcess", "First Task");

        workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        assertThat(workItems.get(0).getName()).isEqualTo("SecondTask");

        uow = app.unitOfWorkManager().newUnitOfWork();
        uow.start();
        processInstance.completeWorkItem(workItems.get(0).getId(), null, securityPolicy);
        uow.end();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        events = publisher.extract();
        List<ProcessInstanceStateDataEvent> userTaskEvents =
                events.stream().filter(ProcessInstanceStateDataEvent.class::isInstance).map(ProcessInstanceStateDataEvent.class::cast).collect(Collectors.toList());
        assertProcessInstanceEvent(userTaskEvents.get(0), "UserTasksProcess", "UserTasksProcess", 2);

        triggered = findNodeInstanceEvents(events, 1);
        assertThat(triggered).hasSize(1).extractingResultOf("getNodeType").containsOnly("EndNode");

        left = findNodeInstanceEvents(events, 2);
        assertThat(left).hasSize(2).extractingResultOf("getNodeType").containsOnly("HumanTaskNode", "EndNode");

        assertUserTaskInstanceEvent(events.get(0), "SecondTask", null, "1", "Obsolete", "UserTasksProcess", "Second Task");
    }

    @Test
    public void testBasicUserTaskProcessAbort() throws Exception {
        Application app = generateCodeProcessesOnly("usertask/UserTasksProcess.bpmn2");
        assertThat(app).isNotNull();

        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        TestEventPublisher publisher = new TestEventPublisher();
        app.unitOfWorkManager().eventManager().setService("http://myhost");
        app.unitOfWorkManager().eventManager().addPublisher(publisher);

        UnitOfWork uow = app.unitOfWorkManager().newUnitOfWork();
        uow.start();

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();
        uow.end();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        List<DataEvent<?>> events = publisher.extract();

        Optional<DataEvent<?>> active = findProcessInstanceEvent(events, ProcessInstance.STATE_ACTIVE);
        assertProcessInstanceEvent(active.get(), "UserTasksProcess", "UserTasksProcess", ProcessInstance.STATE_ACTIVE);

        List<ProcessInstanceNodeEventBody> triggered = findNodeInstanceEvents(events, 1);
        assertThat(triggered).hasSize(2).extractingResultOf("getNodeName").containsOnly("StartProcess", "First Task");

        Optional<UserTaskInstanceStateDataEvent> event = findUserTaskInstanceEvent(events, "FirstTask", "Ready");
        assertThat(event).isPresent();
        assertUserTaskInstanceEvent(event.get(), "FirstTask", null, "1", "Ready", "UserTasksProcess", "First Task");

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        assertThat(workItems.get(0).getName()).isEqualTo("FirstTask");

        uow = app.unitOfWorkManager().newUnitOfWork();
        uow.start();
        processInstance.abort();
        uow.end();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);
        events = publisher.extract();
        assertThat(events).hasSize(3);

        triggered = findNodeInstanceEvents(events, ProcessInstanceNodeEventBody.EVENT_TYPE_ABORTED);
        assertThat(triggered).hasSize(1).extractingResultOf("getNodeName").containsOnly("First Task");

        assertProcessInstanceEvent(events.get(2), "UserTasksProcess", "UserTasksProcess", ProcessInstance.STATE_ABORTED);

    }

    @Test
    public void testBasicUserTaskProcessWithSecurityRoles() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/UserTasksProcessWithSecurityRoles.bpmn2");
        assertThat(app).isNotNull();

        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        TestEventPublisher publisher = new TestEventPublisher();
        app.unitOfWorkManager().eventManager().setService("http://myhost");
        app.unitOfWorkManager().eventManager().addPublisher(publisher);

        UnitOfWork uow = app.unitOfWorkManager().newUnitOfWork();
        uow.start();

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();
        uow.end();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        List<DataEvent<?>> events = publisher.extract();

        Optional<DataEvent<?>> completed = findProcessInstanceEvent(events, ProcessInstanceStateEventBody.EVENT_TYPE_STARTED);
        ProcessInstanceStateEventBody body = assertProcessInstanceEvent(completed.get(), "UserTasksProcess", "UserTasksProcess", 1);
        assertThat(body.getRoles()).hasSize(2).contains("employees", "managers");

        List<ProcessInstanceNodeEventBody> triggered = findNodeInstanceEvents(events, 1);
        assertThat(triggered).hasSize(2).extractingResultOf("getNodeType").containsOnly("StartNode", "HumanTaskNode");

        List<ProcessInstanceNodeEventBody> left = findNodeInstanceEvents(events, 2);
        assertThat(left).hasSize(1).extractingResultOf("getNodeType").containsOnly("StartNode");

        Optional<UserTaskInstanceStateDataEvent> userTask = findUserTaskInstanceEvent(events, "FirstTask", "Ready");
        assertThat(userTask).isPresent();
        assertUserTaskInstanceEvent(userTask.get(), "FirstTask", null, "1", "Ready", "UserTasksProcess", "First Task");
    }

    @Test
    public void testBasicCallActivityTask() throws Exception {

        Application app = generateCodeProcessesOnly("subprocess/CallActivity.bpmn2", "subprocess/CallActivitySubProcess.bpmn2");
        assertThat(app).isNotNull();

        Process<? extends Model> p = app.get(Processes.class).processById("ParentProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("x", "a");
        parameters.put("y", "b");
        m.fromMap(parameters);

        TestEventPublisher publisher = new TestEventPublisher();
        app.unitOfWorkManager().eventManager().setService("http://myhost");
        app.unitOfWorkManager().eventManager().addPublisher(publisher);

        UnitOfWork uow = app.unitOfWorkManager().newUnitOfWork();
        uow.start();

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        uow.end();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        Model result = (Model) processInstance.variables();
        assertThat(result.toMap()).hasSize(2).containsKeys("x", "y")
                .isNotNull().containsEntry("y", "new value")
                .isNotNull().containsEntry("x", "a");

        List<DataEvent<?>> events = publisher.extract();

        List<DataEvent<?>> parentEvents = events.stream().filter(e -> e.getKogitoProcessId().equals("ParentProcess")).collect(Collectors.toList());
        List<DataEvent<?>> childEvents = events.stream().filter(e -> e.getKogitoProcessId().equals("SubProcess")).collect(Collectors.toList());

        DataEvent<?> parentBody = findProcessInstanceEvent(parentEvents, ProcessInstanceStateEventBody.EVENT_TYPE_ENDED).get();
        DataEvent<?> childBody = findProcessInstanceEvent(childEvents, ProcessInstanceStateEventBody.EVENT_TYPE_ENDED).get();

        assertProcessInstanceEvent(parentBody, "ParentProcess", "Parent Process", ProcessInstanceStateEventBody.EVENT_TYPE_ENDED);
        assertThat(findNodeInstanceEvents(parentEvents, ProcessInstanceStateEventBody.EVENT_TYPE_ENDED)).hasSize(3).extractingResultOf("getNodeType").containsOnly("StartNode", "SubProcessNode",
                "EndNode");

        assertProcessInstanceEventWithParentId(childBody, "SubProcess", "Sub Process", ProcessInstanceStateEventBody.EVENT_TYPE_ENDED);
        assertThat(findNodeInstanceEvents(childEvents, ProcessInstanceStateEventBody.EVENT_TYPE_ENDED)).hasSize(3).extractingResultOf("getNodeType").containsOnly("StartNode", "ActionNode", "EndNode");

    }

    @Test
    public void testExclusiveGatewayStartToEnd() throws Exception {

        Application app = generateCodeProcessesOnly("gateway/ExclusiveSplit.bpmn2");
        assertThat(app).isNotNull();
        TestEventPublisher publisher = new TestEventPublisher();
        app.unitOfWorkManager().eventManager().setService("http://myhost");
        app.unitOfWorkManager().eventManager().addPublisher(publisher);

        UnitOfWork uow = app.unitOfWorkManager().newUnitOfWork();
        uow.start();

        Process<? extends Model> p = app.get(Processes.class).processById("ExclusiveSplit");

        Map<String, Object> params = new HashMap<>();
        params.put("x", "First");
        params.put("y", "None");
        Model m = p.createModel();
        m.fromMap(params);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        Model result = (Model) processInstance.variables();
        assertThat(result.toMap()).hasSize(2).containsKeys("x", "y");
        uow.end();

        List<DataEvent<?>> events = publisher.extract();

        List<ProcessInstanceStateDataEvent> processInstanceStateEvents =
                events.stream().filter(ProcessInstanceStateDataEvent.class::isInstance).map(ProcessInstanceStateDataEvent.class::cast).toList();
        assertThat(processInstanceStateEvents).hasSize(2);
        assertThat(processInstanceStateEvents)
                .extracting(ProcessInstanceStateDataEvent::getData)
                .extracting(ProcessInstanceStateEventBody::getState)
                .containsExactlyInAnyOrder(KogitoProcessInstance.STATE_ACTIVE, KogitoProcessInstance.STATE_COMPLETED);

        ProcessInstanceStateDataEvent processDataEvent = processInstanceStateEvents.stream().filter(e -> e.getData().getState() == 2).findFirst().orElseThrow();
        assertThat(processDataEvent.getKogitoProcessInstanceId()).isNotNull();
        assertThat(processDataEvent.getKogitoProcessVersion()).isEqualTo("1.0");
        assertThat(processDataEvent.getKogitoParentProcessInstanceId()).isNull();
        assertThat(processDataEvent.getKogitoRootProcessInstanceId()).isNull();
        assertThat(processDataEvent.getKogitoProcessId()).isEqualTo("ExclusiveSplit");
        assertThat(processDataEvent.getKogitoProcessInstanceState()).isEqualTo("2");

        assertProcessInstanceEvent(processDataEvent, "ExclusiveSplit", "Test", 2);

        List<ProcessInstanceNodeEventBody> nodes = findNodeInstanceEvents(events, 2);
        assertThat(nodes).hasSize(6).extractingResultOf("getNodeType").contains("StartNode", "ActionNode", "Split", "Join", "EndNode", "WorkItemNode");

    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testServiceTaskProcessWithError() throws Exception {

        Application app = generateCodeProcessesOnly("servicetask/ServiceProcessDifferentOperations.bpmn2");
        assertThat(app).isNotNull();
        TestEventPublisher publisher = new TestEventPublisher();
        app.unitOfWorkManager().eventManager().setService("http://myhost");
        app.unitOfWorkManager().eventManager().addPublisher(publisher);

        UnitOfWork uow = app.unitOfWorkManager().newUnitOfWork();
        uow.start();

        Process<? extends Model> p = app.get(Processes.class).processById("ServiceProcessDifferentOperations");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        ProcessInstance processInstance = p.createInstance(m);
        processInstance.start();

        uow.end();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ERROR);
        List<DataEvent<?>> rawEvents = publisher.extract();
        List<DataEvent<?>> events = rawEvents.stream().filter(ProcessInstanceStateDataEvent.class::isInstance).collect(Collectors.toList());
        assertThat(events).hasSize(1);

        assertProcessInstanceEvent(events.get(0), "ServiceProcessDifferentOperations", "Service Process", KogitoProcessInstance.STATE_ACTIVE);

        events = rawEvents.stream().filter(ProcessInstanceErrorDataEvent.class::isInstance).collect(Collectors.toList());
        assertThat(events).hasSize(1);

        List<ProcessInstanceErrorDataEvent> errorEvents =
                rawEvents.stream().filter(ProcessInstanceErrorDataEvent.class::isInstance).map(ProcessInstanceErrorDataEvent.class::cast).collect(Collectors.toList());
        assertThat(errorEvents).hasSize(1);
        assertThat(errorEvents.get(0).getData().getErrorMessage()).contains("java.lang.NullPointerException");
        assertThat(errorEvents.get(0).getData().getNodeDefinitionId()).isEqualTo("_38E04E27-3CCA-47F9-927B-E37DC4B8CE25");

        parameters.put("s", "john");
        m.fromMap(parameters);
        uow = app.unitOfWorkManager().newUnitOfWork();
        uow.start();
        processInstance.updateVariables(m);
        uow.end();

        events = publisher.extract();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ProcessInstanceVariableDataEvent.class);

        uow = app.unitOfWorkManager().newUnitOfWork();
        uow.start();
        int stateEventCount;
        if (processInstance.error().isPresent()) {
            stateEventCount = 2;
            ((ProcessError) processInstance.error().get()).retrigger();
        } else {
            stateEventCount = 1;
        }

        uow.end();

        events = publisher.extract().stream().filter(ProcessInstanceStateDataEvent.class::isInstance).collect(Collectors.toList());
        assertThat(events).hasSize(stateEventCount);

        assertProcessInstanceEvent(events.get(stateEventCount - 1), "ServiceProcessDifferentOperations", "Service Process", 2);

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        Model result = (Model) processInstance.variables();
        assertThat(result.toMap()).hasSize(1).containsKeys("s")
                .isNotNull().containsEntry("s", "Goodbye Hello john!!");
    }

    @Test
    public void testUserTaskNodeEnterEventContainsWorkItemId() throws Exception {
        Application app = generateCodeProcessesOnly("usertask/UserTasksProcess.bpmn2");
        assertThat(app).isNotNull();

        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model model = p.createModel();
        model.fromMap(Collections.emptyMap());

        TestEventPublisher publisher = new TestEventPublisher();
        app.unitOfWorkManager().eventManager().setService("http://myhost");
        app.unitOfWorkManager().eventManager().addPublisher(publisher);

        UnitOfWork uow = app.unitOfWorkManager().newUnitOfWork();
        uow.start();

        ProcessInstance<?> processInstance = p.createInstance(model);
        processInstance.start();

        uow.end();

        assertThat(processInstance.status())
                .isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);

        assertThat(workItems).hasSize(1);
        assertThat(workItems.get(0).getName()).isEqualTo("FirstTask");
        assertThat(workItems.get(0).getId()).isNotNull();

        List<DataEvent<?>> events = publisher.extract();

        List<ProcessInstanceNodeEventBody> nodeEnterEvents = findNodeInstanceEvents(
                events,
                ProcessInstanceNodeEventBody.EVENT_TYPE_ENTER);

        ProcessInstanceNodeEventBody humanTaskNodeEvent = nodeEnterEvents.stream()
                .filter(event -> "HumanTaskNode".equals(event.getNodeType()))
                .filter(event -> "First Task".equals(event.getNodeName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "HumanTaskNode ENTER event was not published"));

        assertThat(humanTaskNodeEvent.getWorkItemId())
                .as("HumanTaskNode ENTER event must contain the created work item ID")
                .isNotNull()
                .isEqualTo(workItems.get(0).getId());
    }

    /*
     * Helper methods
     */

    protected ProcessInstanceNodeEventBody assertNodeInstanceEvent(DataEvent<?> event, String processInstanceId, String nodeName, Integer eventType) {

        assertThat(event).isInstanceOf(ProcessInstanceNodeDataEvent.class);
        ProcessInstanceNodeEventBody body = ((ProcessInstanceNodeDataEvent) event).getData();
        assertThat(body).isNotNull();
        assertThat(body.getProcessInstanceId()).isNotNull();
        assertThat(body.getProcessInstanceId()).isEqualTo(processInstanceId);
        assertThat(body.getEventType()).isEqualTo(eventType);
        assertThat(body.getNodeName()).isEqualTo(nodeName);

        return body;
    }

    protected ProcessInstanceStateEventBody assertProcessInstanceEvent(DataEvent<?> event, String processId, String processName, Integer state) {

        assertThat(event).isInstanceOf(ProcessInstanceStateDataEvent.class);
        ProcessInstanceStateEventBody body = ((ProcessInstanceStateDataEvent) event).getData();
        assertThat(body).isNotNull();
        assertThat(body.getProcessInstanceId()).isNotNull();
        assertThat(body.getProcessVersion()).isNotNull();
        assertThat(body.getEventDate()).isNotNull();
        assertThat(body.getParentInstanceId()).isNull();
        assertThat(body.getRootProcessInstanceId()).isNull();
        assertThat(body.getProcessId()).isEqualTo(processId);
        assertThat(body.getProcessName()).isEqualTo(processName);
        assertThat(body.getState()).isEqualTo(state);

        assertThat(event.getSource()).hasToString("http://myhost/" + processId);
        assertThat(event.getTime()).isBeforeOrEqualTo(ZonedDateTime.now().toOffsetDateTime());

        assertThat(((ProcessInstanceStateDataEvent) event).getKogitoAddons()).isEqualTo("test");

        return body;
    }

    protected UserTaskInstanceStateEventBody assertUserTaskInstanceEvent(DataEvent<?> event, String taskName, String taskDescription, String taskPriority, String taskState, String processId,
            String nodeName) {
        assertThat(event).isInstanceOf(UserTaskInstanceStateDataEvent.class);
        UserTaskInstanceStateEventBody body = ((UserTaskInstanceStateDataEvent) event).getData();
        assertThat(body).isNotNull();
        assertThat(body.getUserTaskInstanceId()).isNotNull();
        assertThat(body.getUserTaskName()).isEqualTo(taskName);
        assertThat(body.getUserTaskDescription()).isEqualTo(taskDescription);
        assertThat(body.getUserTaskReferenceName()).isEqualTo(nodeName);
        assertThat(body.getUserTaskPriority()).isEqualTo(taskPriority);
        assertThat(body.getEventDate()).isNotNull();
        assertThat(body.getState()).isEqualTo(taskState);
        assertThat(event.getSource()).hasToString("http://myhost/" + processId);
        assertThat(event.getTime()).isBeforeOrEqualTo(ZonedDateTime.now().toOffsetDateTime());

        assertThat(((UserTaskInstanceStateDataEvent) event).getKogitoAddons()).isEqualTo("test");

        return body;
    }

    protected ProcessInstanceStateEventBody assertProcessInstanceEventWithParentId(DataEvent<?> event, String processId, String processName, Integer state) {

        assertThat(event).isInstanceOf(ProcessInstanceStateDataEvent.class);
        ProcessInstanceStateEventBody body = ((ProcessInstanceStateDataEvent) event).getData();
        assertThat(body).isNotNull();
        assertThat(body.getProcessInstanceId()).isNotNull();
        assertThat(body.getProcessVersion()).isNotNull();
        assertThat(body.getEventDate()).isNotNull();
        assertThat(body.getParentInstanceId()).isNotNull();
        assertThat(body.getRootProcessInstanceId()).isNotNull();
        assertThat(body.getProcessId()).isEqualTo(processId);
        assertThat(body.getProcessName()).isEqualTo(processName);
        assertThat(body.getEventType()).isEqualTo(state);

        return body;
    }

    private class TestEventPublisher implements EventPublisher {

        private List<DataEvent<?>> events = new ArrayList<>();

        @Override
        public void publish(DataEvent<?> event) {
            this.events.add(event);
        }

        @Override
        public void publish(Collection<DataEvent<?>> events) {
            this.events.addAll(events);
        }

        public List<DataEvent<?>> extract() {
            List<DataEvent<?>> copied = new ArrayList<>(this.events);
            this.events.clear();
            return copied;
        }
    }
}
