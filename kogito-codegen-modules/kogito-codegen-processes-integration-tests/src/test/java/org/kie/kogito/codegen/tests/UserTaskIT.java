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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.kie.kogito.Application;
import org.kie.kogito.Model;
import org.kie.kogito.auth.IdentityProvider;
import org.kie.kogito.auth.IdentityProviders;
import org.kie.kogito.auth.SecurityPolicy;
import org.kie.kogito.codegen.AbstractCodegenIT;
import org.kie.kogito.codegen.data.Person;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.kie.kogito.internal.process.event.ProcessWorkItemTransitionEvent;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.workitem.NotAuthorizedException;
import org.kie.kogito.internal.process.workitem.Policy;
import org.kie.kogito.jbpm.usertask.handler.UserTaskKogitoWorkItemHandler;
import org.kie.kogito.jbpm.usertask.handler.UserTaskKogitoWorkItemHandlerProcessListener;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessConfig;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.Processes;
import org.kie.kogito.process.VariableViolationException;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.usertask.UserTaskConfig;
import org.kie.kogito.usertask.UserTaskEventListener;
import org.kie.kogito.usertask.UserTaskInstance;
import org.kie.kogito.usertask.UserTaskInstanceNotAuthorizedException;
import org.kie.kogito.usertask.UserTasks;
import org.kie.kogito.usertask.events.UserTaskAssignmentEvent;
import org.kie.kogito.usertask.events.UserTaskDeadlineEvent;
import org.kie.kogito.usertask.events.UserTaskDeadlineEvent.DeadlineType;
import org.kie.kogito.usertask.impl.lifecycle.DefaultUserTaskLifeCycle;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kie.kogito.usertask.impl.lifecycle.DefaultUserTaskLifeCycle.CLAIM;
import static org.kie.kogito.usertask.impl.lifecycle.DefaultUserTaskLifeCycle.COMPLETE;
import static org.kie.kogito.usertask.impl.lifecycle.DefaultUserTaskLifeCycle.RELEASE;

public class UserTaskIT extends AbstractCodegenIT {

    private Policy securityPolicy = SecurityPolicy.of("john", emptyList());

    @Test
    public void testBasicUserTaskProcess() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/UserTasksProcess.bpmn2");
        assertThat(app).isNotNull();
        final List<String> workItemTransitionEvents = new ArrayList<>();
        app.config().get(ProcessConfig.class).processEventListeners().listeners().add(new DefaultKogitoProcessEventListener() {

            @Override
            public void beforeWorkItemTransition(ProcessWorkItemTransitionEvent event) {
                workItemTransitionEvents.add("BEFORE:: " + event);
            }

            @Override
            public void afterWorkItemTransition(ProcessWorkItemTransitionEvent event) {
                workItemTransitionEvents.add("AFTER:: " + event);
            }
        });

        // we wired user tasks and processes
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));

        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        assertThat(workItems.get(0).getName()).isEqualTo("FirstTask");
        WorkItem wi_1 = workItems.get(0);

        UserTasks userTasks = app.get(UserTasks.class);

        UserTaskInstance userTaskInstance_1 = userTasks.instances().findById(wi_1.getExternalReferenceId()).get();
        assertThat(userTaskInstance_1).isNotNull();

        List<UserTaskInstance> userTaskList = userTasks.instances().findByIdentity(IdentityProviders.of("mary"));
        assertThat(userTaskList).hasSize(1);

        userTaskList = userTasks.instances().findByIdentity(IdentityProviders.of("invalid"));
        assertThat(userTaskList).hasSize(0);

        userTaskList = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskList).hasSize(1);

        userTaskInstance_1 = userTaskList.get(0);
        userTaskInstance_1.transition(CLAIM, emptyMap(), IdentityProviders.of("john"));
        userTaskInstance_1.transition(COMPLETE, emptyMap(), IdentityProviders.of("john"));

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        assertThat(workItems.get(0).getName()).isEqualTo("SecondTask");
        WorkItem wi_2 = workItems.get(0);

        UserTaskInstance userTaskInstance_2 = userTasks.instances().findById(wi_2.getExternalReferenceId()).get();
        assertThat(userTaskInstance_2).isNotNull();

        userTaskList = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskList).hasSize(1);

        userTaskInstance_1 = userTaskList.get(0);
        userTaskInstance_2.transition(COMPLETE, emptyMap(), IdentityProviders.of("john"));

        assertThat(p.instances().stream().count()).isEqualTo(0);

        assertThat(workItemTransitionEvents).hasSize(8);
    }

    @Test
    public void testDoubleLinkUserTaskProcesses() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/UserTasksProcess.bpmn2");
        assertThat(app).isNotNull();

        // we wired user tasks and processes
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));

        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<WorkItem> workItems = processInstance.workItems();
        assertThat(workItems).hasSize(1);
        assertThat(workItems.get(0).getName()).isEqualTo("FirstTask");
        WorkItem wi_1 = workItems.get(0);

        UserTasks userTasks = app.get(UserTasks.class);

        UserTaskInstance userTaskInstance_1 = userTasks.instances().findById(wi_1.getExternalReferenceId()).get();
        assertThat(userTaskInstance_1).isNotNull();

        List<UserTaskInstance> userTaskList = userTasks.instances().findByIdentity(IdentityProviders.of("mary"));
        assertThat(userTaskList).hasSize(1);
        // now we check the external reference properly sets one to the other
        Optional<UserTaskInstance> utLinked = userTasks.instances().findById(wi_1.getExternalReferenceId());
        assertThat(utLinked).isPresent().get().extracting(UserTaskInstance::getExternalReferenceId).isEqualTo(wi_1.getId());
        assertThat(wi_1.getExternalReferenceId()).isEqualTo(utLinked.get().getId());

        processInstance.abort();
    }

    @Test
    public void testBasicUserTaskProcessPhases() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/UserTasksProcess.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        WorkItem wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("FirstTask");

        UserTasks userTasks = app.get(UserTasks.class);
        List<UserTaskInstance> userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_1 = userTaskInstances.get(0);
        ut_1.transition(CLAIM, emptyMap(), IdentityProviders.of("john"));
        ut_1.transition(COMPLETE, emptyMap(), IdentityProviders.of("john"));

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("SecondTask");

        userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_2 = userTaskInstances.get(0);
        ut_2.transition(COMPLETE, emptyMap(), IdentityProviders.of("john"));

        assertThat(p.instances().stream().count()).isEqualTo(0);

    }

    @Test
    public void testBasicUserTaskProcessClaimAndCompletePhases() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/UserTasksProcess.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        WorkItem wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("FirstTask");
        assertThat(wi.getResults()).isEmpty();

        UserTasks userTasks = app.get(UserTasks.class);
        List<UserTaskInstance> userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_1 = userTaskInstances.get(0);
        ut_1.transition(CLAIM, emptyMap(), IdentityProviders.of("john"));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        ut_1.transition(COMPLETE, Map.of("test", "value"), IdentityProviders.of("john"));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_2 = userTaskInstances.get(0);
        assertThat(ut_2.getStatus()).isEqualTo(DefaultUserTaskLifeCycle.RESERVED);

        processInstance.abort();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testDuplicateTaskNamesProcessExecution() throws Exception {
        Application app = generateCodeProcessesOnly("usertask/DuplicateTaskNamesProcess.bpmn");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));

        List<UserTaskInstance> completedUserTasks = new ArrayList<>();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskEventListener() {
            @Override
            public void onUserTaskState(org.kie.kogito.usertask.events.UserTaskStateEvent event) {
                if (event.getNewStatus().isTerminate()) {
                    completedUserTasks.add(event.getUserTaskInstance());
                }
            }
        });

        Process<? extends Model> p = app.get(Processes.class).processById("DuplicateTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("varA", "initialA");
        parameters.put("varB", "initialB");
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        // First task
        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        WorkItem wi1 = workItems.get(0);
        assertThat(wi1.getName()).isEqualTo("SharedTaskName");
        assertThat(wi1.getParameters()).containsEntry("varA", "initialA");

        processInstance.completeWorkItem(wi1.getId(), Collections.singletonMap("varA", "updatedA"), securityPolicy);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        // Second task sharing the same taskName
        workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        WorkItem wi2 = workItems.get(0);
        assertThat(wi2.getName()).isEqualTo("SharedTaskName");
        assertThat(wi2.getParameters()).containsEntry("varB", "initialB");

        processInstance.completeWorkItem(wi2.getId(), Collections.singletonMap("varB", "updatedB"), securityPolicy);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

        Model result = (Model) processInstance.variables();
        Map<String, Object> resultMap = result.toMap();
        assertThat(resultMap).containsEntry("varA", "updatedA");
        assertThat(resultMap).containsEntry("varB", "updatedB");

        // Verify that completed/obsoleted user task instances received the outputs from work item completion
        assertThat(completedUserTasks).hasSize(2);
        assertThat(completedUserTasks.get(0).getOutputs()).containsEntry("varA", "updatedA");
        assertThat(completedUserTasks.get(1).getOutputs()).containsEntry("varB", "updatedB");
    }

    @Test
    public void testBasicUserTaskProcessReleaseAndCompletePhases() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/UserTasksProcess.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        WorkItem wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("FirstTask");
        assertThat(wi.getPhaseStatus()).isEqualTo(UserTaskKogitoWorkItemHandler.ACTIVATED.getName());
        assertThat(wi.getResults()).isEmpty();

        UserTasks userTasks = app.get(UserTasks.class);
        List<UserTaskInstance> userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_1 = userTaskInstances.get(0);
        ut_1.transition(CLAIM, emptyMap(), IdentityProviders.of("john"));
        assertThat(ut_1.getStatus()).isEqualTo(DefaultUserTaskLifeCycle.RESERVED);
        assertThat(ut_1.getActualOwner()).isEqualTo("john");

        ut_1.transition(RELEASE, emptyMap(), IdentityProviders.of("john"));
        assertThat(ut_1.getStatus()).isEqualTo(DefaultUserTaskLifeCycle.ACTIVE);
        assertThat(ut_1.getActualOwner()).isNull();

        ut_1.transition(DefaultUserTaskLifeCycle.CLAIM, emptyMap(), IdentityProviders.of("john"));
        assertThat(ut_1.getStatus()).isEqualTo(DefaultUserTaskLifeCycle.RESERVED);
        assertThat(ut_1.getActualOwner()).isEqualTo("john");

        ut_1.transition(DefaultUserTaskLifeCycle.COMPLETE, emptyMap(), IdentityProviders.of("john"));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("SecondTask");

        userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_2 = userTaskInstances.get(0);
        assertThat(ut_2.getStatus()).isEqualTo(DefaultUserTaskLifeCycle.RESERVED);

        processInstance.abort();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);
        assertThat(userTasks.instances().findByIdentity(IdentityProviders.of("john"))).hasSize(0);
    }

    @Test
    public void testBasicUserTaskProcessClaimAndCompletePhasesWithIdentity() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/UserTasksProcess.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        final List<String> workItemTransitionEvents = new ArrayList<>();
        app.config().get(ProcessConfig.class).processEventListeners().listeners().add(new DefaultKogitoProcessEventListener() {

            @Override
            public void beforeWorkItemTransition(ProcessWorkItemTransitionEvent event) {
                workItemTransitionEvents.add("BEFORE:: " + event);
            }

            @Override
            public void afterWorkItemTransition(ProcessWorkItemTransitionEvent event) {
                workItemTransitionEvents.add("AFTER:: " + event);
            }
        });

        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        WorkItem wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("FirstTask");
        assertThat(wi.getPhaseStatus()).isEqualTo(UserTaskKogitoWorkItemHandler.ACTIVATED.getName());
        assertThat(wi.getResults()).isEmpty();

        UserTasks userTasks = app.get(UserTasks.class);
        List<UserTaskInstance> userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_1 = userTaskInstances.get(0);
        assertThat(ut_1.getStatus()).isEqualTo(DefaultUserTaskLifeCycle.ACTIVE);
        ut_1.transition(CLAIM, emptyMap(), IdentityProviders.of("john"));
        assertThat(ut_1.getStatus()).isEqualTo(DefaultUserTaskLifeCycle.RESERVED);
        ut_1.transition(COMPLETE, emptyMap(), IdentityProviders.of("john"));
        assertThat(ut_1.getStatus()).isEqualTo(DefaultUserTaskLifeCycle.COMPLETED);

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_2 = userTaskInstances.get(0);
        assertThat(ut_2.getStatus()).isEqualTo(DefaultUserTaskLifeCycle.RESERVED);

        processInstance.abort();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);

        assertThat(workItemTransitionEvents).hasSize(8);
    }

    @Test
    public void testBasicUserTaskProcessClaimAndCompleteWrongUser() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/UserTasksProcess.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        WorkItem wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("FirstTask");
        assertThat(wi.getPhaseStatus()).isEqualTo(UserTaskKogitoWorkItemHandler.ACTIVATED.getName());
        assertThat(wi.getResults()).isEmpty();

        final String wiId = wi.getId();
        IdentityProvider identity = IdentityProviders.of("kelly");

        // if user that is not authorized to work on work item both listing and getting by id should apply it
        List<WorkItem> securedWorkItems = processInstance.workItems(SecurityPolicy.of(identity));
        assertThat(securedWorkItems).isEmpty();

        assertThatExceptionOfType(NotAuthorizedException.class).isThrownBy(() -> processInstance.workItem(wiId, SecurityPolicy.of(identity)));

        UserTasks userTasks = app.get(UserTasks.class);
        List<UserTaskInstance> userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance utInvalid = userTaskInstances.get(0);
        assertThatExceptionOfType(UserTaskInstanceNotAuthorizedException.class).isThrownBy(() -> utInvalid.transition(CLAIM, emptyMap(), IdentityProviders.of("invalid")));

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("FirstTask");
        assertThat(wi.getPhaseStatus()).isEqualTo(UserTaskKogitoWorkItemHandler.ACTIVATED.getName());
        assertThat(wi.getResults()).isEmpty();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_1 = userTaskInstances.get(0);
        ut_1.transition(CLAIM, emptyMap(), IdentityProviders.of("john"));
        ut_1.transition(COMPLETE, emptyMap(), IdentityProviders.of("john"));

        workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("SecondTask");
        assertThat(wi.getPhaseStatus()).isEqualTo(UserTaskKogitoWorkItemHandler.ACTIVATED.getName());
        assertThat(wi.getResults()).isEmpty();

        userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_2 = userTaskInstances.get(0);
        assertThat(ut_2.getStatus()).isEqualTo(DefaultUserTaskLifeCycle.RESERVED);

        processInstance.abort();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);
        assertThat(userTasks.instances().findByIdentity(IdentityProviders.of("john"))).hasSize(0);
    }

    @Test
    public void testApprovalWithExcludedOwnerViaPhases() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/approval.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        Process<? extends Model> p = app.get(Processes.class).processById("approvals");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);

        IdentityProvider identity = IdentityProviders.of("manager", emptyList());
        SecurityPolicy policy = SecurityPolicy.of(identity);

        processInstance.workItems(policy);

        List<WorkItem> workItems = processInstance.workItems(policy);
        assertThat(workItems).hasSize(1);

        UserTasks userTasks = app.get(UserTasks.class);
        List<UserTaskInstance> userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("manager"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_1 = userTaskInstances.get(0);
        ut_1.transition(COMPLETE, emptyMap(), IdentityProviders.of("manager"));

        // actual owner of the first task is excluded owner on the second task so won't find it
        workItems = processInstance.workItems(policy);
        assertThat(workItems).isEmpty();

        identity = IdentityProviders.of("john", singletonList("managers"));
        policy = SecurityPolicy.of(identity);

        workItems = processInstance.workItems(policy);
        assertThat(workItems).hasSize(1);

        userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("admin", singletonList("managers")));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_2 = userTaskInstances.get(0);
        // attempt to claim task with excluded user (manager)
        assertThrows(UserTaskInstanceNotAuthorizedException.class, () -> {
            ut_2.transition(CLAIM, emptyMap(), IdentityProviders.of("manager", singletonList("managers")));
        });
        // claim and complete task with different user (admin)
        ut_2.transition(CLAIM, emptyMap(), IdentityProviders.of("admin", singletonList("managers")));
        ut_2.transition(COMPLETE, emptyMap(), IdentityProviders.of("admin", singletonList("managers")));

        assertThat(p.instances().stream().count()).isEqualTo(0);
    }

    @Test
    public void testApprovalWithExcludedOwner() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/approval.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        Process<? extends Model> p = app.get(Processes.class).processById("approvals");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("approver", "manager");
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);

        IdentityProvider identity = IdentityProviders.of("manager", emptyList());
        SecurityPolicy policy = SecurityPolicy.of(identity);

        List<WorkItem> workItems = processInstance.workItems(policy);
        assertThat(workItems).hasSize(1);

        processInstance.completeWorkItem(workItems.get(0).getId(), singletonMap("ActorId", "manager"), policy);
        // actual owner of the first task is excluded owner on the second task so won't find it
        workItems = processInstance.workItems(policy);
        assertThat(workItems).isEmpty();

        identity = IdentityProviders.of("john", Collections.singletonList("managers"));
        policy = SecurityPolicy.of(identity);

        workItems = processInstance.workItems(policy);
        assertThat(workItems).hasSize(1);

        assertThat(workItems).hasSize(1);

        UserTasks userTasks = app.get(UserTasks.class);
        List<UserTaskInstance> userTaskInstances = userTasks.instances().findByIdentity(identity);
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_1 = userTaskInstances.get(0);
        ut_1.transition(CLAIM, emptyMap(), identity);
        ut_1.transition(COMPLETE, emptyMap(), identity);

        assertThat(p.instances().stream().count()).isEqualTo(0);
    }

    @Test
    public void testBasicUserTaskProcessCancelAndTriggerNode() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/UserTasksProcess.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        UserTasks userTasks = app.get(UserTasks.class);
        List<UserTaskInstance> userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_1 = userTaskInstances.get(0);
        ut_1.transition(CLAIM, emptyMap(), IdentityProviders.of("john"));
        ut_1.transition(COMPLETE, emptyMap(), IdentityProviders.of("john"));

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        WorkItem wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("SecondTask");
        assertThat(wi.getPhaseStatus()).isEqualTo(UserTaskKogitoWorkItemHandler.ACTIVATED.getName());

        String firstSecondTaskNodeInstanceId = wi.getNodeInstanceId();

        processInstance.cancelNodeInstance(wi.getNodeInstanceId());
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        processInstance.triggerNode("UserTask_2");
        workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("SecondTask");
        assertThat(wi.getPhaseStatus()).isEqualTo(UserTaskKogitoWorkItemHandler.ACTIVATED.getName());
        // since it was triggered again it must have different node instance id
        assertThat(wi.getNodeInstanceId()).isNotEqualTo(firstSecondTaskNodeInstanceId);

        userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_2 = userTaskInstances.get(0);
        ut_2.transition(COMPLETE, emptyMap(), IdentityProviders.of("john"));

        assertThat(p.instances().stream().count()).isEqualTo(0);
    }

    @Test
    public void testBasicUserTaskProcessCancelAndRetriggerNode() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/UserTasksProcess.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        WorkItem wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("FirstTask");

        assertThat(wi.getPhaseStatus()).isEqualTo(UserTaskKogitoWorkItemHandler.ACTIVATED.getName());

        UserTasks userTasks = app.get(UserTasks.class);
        List<UserTaskInstance> userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_1 = userTaskInstances.get(0);
        ut_1.transition(CLAIM, emptyMap(), IdentityProviders.of("john"));
        ut_1.transition(COMPLETE, emptyMap(), IdentityProviders.of("john"));

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("SecondTask");
        assertThat(wi.getPhaseStatus()).isEqualTo(UserTaskKogitoWorkItemHandler.ACTIVATED.getName());

        String firstSecondTaskNodeInstanceId = wi.getNodeInstanceId();

        processInstance.retriggerNodeInstance(wi.getNodeInstanceId());
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("SecondTask");

        assertThat(wi.getPhaseStatus()).isEqualTo(UserTaskKogitoWorkItemHandler.ACTIVATED.getName());
        // since it was retriggered it must have different node instance id
        assertThat(wi.getNodeInstanceId()).isNotEqualTo(firstSecondTaskNodeInstanceId);

        userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_2 = userTaskInstances.get(0);
        ut_2.transition(COMPLETE, emptyMap(), IdentityProviders.of("john"));

        assertThat(p.instances().stream().count()).isEqualTo(0);
    }

    @Test
    public void testBasicUserTaskProcessClaimReleaseClaimAndCompletePhases() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/UserTasksProcess.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        WorkItem wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("FirstTask");
        assertThat(wi.getPhaseStatus()).isEqualTo(UserTaskKogitoWorkItemHandler.ACTIVATED.getName());
        assertThat(wi.getResults()).isEmpty();

        UserTasks userTasks = app.get(UserTasks.class);
        List<UserTaskInstance> userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_1 = userTaskInstances.get(0);
        ut_1.transition(CLAIM, emptyMap(), IdentityProviders.of("john"));
        assertThat(ut_1.getStatus()).isEqualTo(DefaultUserTaskLifeCycle.RESERVED);
        ut_1.transition(RELEASE, emptyMap(), IdentityProviders.of("john"));
        assertThat(ut_1.getStatus()).isEqualTo(DefaultUserTaskLifeCycle.ACTIVE);
        assertThat(ut_1.getActualOwner()).isNull();
        ut_1.transition(CLAIM, emptyMap(), IdentityProviders.of("john"));
        assertThat(ut_1.getActualOwner()).isEqualTo("john");
        ut_1.transition(COMPLETE, emptyMap(), IdentityProviders.of("john"));

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("SecondTask");
        assertThat(wi.getPhaseStatus()).isEqualTo(UserTaskKogitoWorkItemHandler.ACTIVATED.getName());
        assertThat(wi.getResults()).isEmpty();

        userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_2 = userTaskInstances.get(0);
        assertThat(ut_2.getStatus()).isEqualTo(DefaultUserTaskLifeCycle.RESERVED);

        processInstance.abort();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testApprovalWithReadonlyVariableTags() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/approval-with-readonly-variable-tags.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        Class<?> resourceClazz = Class.forName("org.acme.travels.ApprovalsModel", true, testClassLoader());
        assertThat(resourceClazz).isNotNull();

        Field approverField = resourceClazz.getDeclaredField("approver");
        assertThat(approverField).isNotNull();
        assertThat(approverField.getType().getCanonicalName()).isEqualTo(String.class.getCanonicalName());

        Process<? extends Model> p = app.get(Processes.class).processById("approvals");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("approver", "john");
        m.fromMap(parameters);

        ProcessInstance processInstance = p.createInstance(m);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);

        final Model updates = p.createModel();
        parameters = new HashMap<>();
        parameters.put("approver", "mary");
        updates.fromMap(parameters);
        // updating readonly variable should fail
        assertThatExceptionOfType(VariableViolationException.class).isThrownBy(() -> processInstance.updateVariables(updates));

        processInstance.abort();

        assertThat(processInstance.status()).isEqualTo(KogitoProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testApprovalWithInternalVariableTags() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/approval-with-internal-variable-tags.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        Class<?> resourceClazz = Class.forName("org.acme.travels.ApprovalsModel", true, testClassLoader());
        assertThat(resourceClazz).isNotNull();
        // internal variables are not exposed on the model
        assertThatExceptionOfType(NoSuchFieldException.class).isThrownBy(() -> resourceClazz.getDeclaredField("approver"));

        Process<? extends Model> p = app.get(Processes.class).processById("approvals");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);

        processInstance.abort();

        assertThat(processInstance.status()).isEqualTo(KogitoProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testApprovalWithRequiredVariableTags() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/approval-with-required-variable-tags.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        Process<? extends Model> p = app.get(Processes.class).processById("approvals");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        assertThatExceptionOfType(VariableViolationException.class).isThrownBy(() -> {
            ProcessInstance<?> processInstance = p.createInstance(m);
            processInstance.start();
        });
    }

    @Test
    public void testApprovalWithIOVariableTags() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/approval-with-io-variable-tags.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        Class<?> modelClazz = Class.forName("org.acme.travels.ApprovalsModel", true, testClassLoader());
        assertThat(modelClazz).isNotNull();
        assertThat(modelClazz.getDeclaredField("decision")).isNotNull();
        assertThat(modelClazz.getDeclaredField("approver")).isNotNull();

        Class<?> inputModelClazz = Class.forName("org.acme.travels.ApprovalsModelInput", true, testClassLoader());
        assertThat(inputModelClazz).isNotNull();
        assertThat(inputModelClazz.getDeclaredField("approver")).isNotNull();
        assertThatExceptionOfType(NoSuchFieldException.class).isThrownBy(() -> inputModelClazz.getDeclaredField("decision"));
        assertThatExceptionOfType(NoSuchFieldException.class).isThrownBy(() -> inputModelClazz.getDeclaredField("id"));

        Class<?> outputModelClazz = Class.forName("org.acme.travels.ApprovalsModelOutput", true, testClassLoader());
        assertThat(outputModelClazz).isNotNull();
        assertThat(outputModelClazz.getDeclaredField("decision")).isNotNull();
        assertThat(outputModelClazz.getDeclaredField("id")).isNotNull();
        assertThatExceptionOfType(NoSuchFieldException.class).isThrownBy(() -> outputModelClazz.getDeclaredField("approver"));

        Process<? extends Model> p = app.get(Processes.class).processById("approvals");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("approver", "mary");
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);

        processInstance.abort();

        assertThat(processInstance.status()).isEqualTo(KogitoProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testUserTaskWithIOexpressionProcess() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/UserTaskWithIOexpression.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        Process<? extends Model> p = app.get(Processes.class).processById("UserTask");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("person", new Person("john", 0));
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        assertThat(workItems.get(0).getName()).isEqualTo("Hello");
        assertThat(workItems.get(0).getParameters()).containsEntry("personName", "john");

        processInstance.completeWorkItem(workItems.get(0).getId(), singletonMap("personAge", 50), securityPolicy);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

        Model output = (Model) processInstance.variables();
        Person person = (Person) output.toMap().get("person");
        assertThat(person.getAge()).isEqualTo(50);
    }

    @Test
    public void testBasicUserTaskProcessWithBusinessKey() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/UserTasksProcess.bpmn2");
        assertThat(app).isNotNull();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskKogitoWorkItemHandlerProcessListener(app.get(Processes.class)));
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        // assign custom business key for process instance
        String businessKey = "business key";
        ProcessInstance<?> processInstance = p.createInstance(businessKey, m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        // verify that custom business key is assigned properly
        assertThat(processInstance.businessKey()).isEqualTo(businessKey);

        // find the process instance by ID and verify business key
        Optional<? extends ProcessInstance<? extends Model>> processInstanceByBussinesKey = p.instances().findById(processInstance.id());
        assertThat(processInstanceByBussinesKey).isPresent();
        assertThat(processInstanceByBussinesKey.get().businessKey()).isEqualTo(businessKey);

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        assertThat(workItems.get(0).getName()).isEqualTo("FirstTask");

        UserTasks userTasks = app.get(UserTasks.class);
        List<UserTaskInstance> userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_1 = userTaskInstances.get(0);
        ut_1.transition(CLAIM, emptyMap(), IdentityProviders.of("john"));
        ut_1.transition(COMPLETE, emptyMap(), IdentityProviders.of("john"));

        workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        assertThat(workItems.get(0).getName()).isEqualTo("SecondTask");

        userTaskInstances = userTasks.instances().findByIdentity(IdentityProviders.of("john"));
        assertThat(userTaskInstances).isNotNull().hasSize(1);
        UserTaskInstance ut_2 = userTaskInstances.get(0);
        ut_2.transition(COMPLETE, emptyMap(), IdentityProviders.of("john"));

        assertThat(p.instances().stream().count()).isEqualTo(0);
    }

    @Test
    public void testBasicUserTaskProcessWithDuplicatedBusinessKey() throws Exception {

        Application app = generateCodeProcessesOnly("usertask/UserTasksProcess.bpmn2");
        assertThat(app).isNotNull();

        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        // assign custom business key for process instance
        String businessKey = "business key";
        ProcessInstance<?> processInstance = p.createInstance(businessKey, m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        // verify that custom business key is assigned properly
        assertThat(processInstance.businessKey()).isEqualTo(businessKey);

        // start another process instance with assigned duplicated business key of already active instance
        ProcessInstance<? extends Model> otherProcessInstance = p.createInstance(businessKey, m);
        assertThat(otherProcessInstance.id()).isNotEqualTo(processInstance.id());
        assertThat(otherProcessInstance.businessKey()).isEqualTo(processInstance.businessKey()).isEqualTo(businessKey);
    }

    @Test
    public void testUserTaskNotStartedDeadlineWithExpressionReplacement() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        Application app = generateCodeProcessesOnly("usertask/UserTasksNotStartedDeadline.bpmn2");
        assertThat(app).isNotNull();
        List<String> subjects = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        List<UserTaskDeadlineEvent.DeadlineType> types = new ArrayList<>();

        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskEventListener() {
            @Override
            public void onUserTaskDeadline(UserTaskDeadlineEvent event) {
                subjects.add((String) event.getNotification().get("subject"));
                bodies.add((String) event.getNotification().get("body"));
                types.add(event.getType());
                latch.countDown();
            }

        });
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksDeadline");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("subject", "this is my subject");
        parameters.put("body", "this is my body");
        m.fromMap(parameters);

        // assign custom business key for process instance
        String businessKey = "business key";
        ProcessInstance<?> processInstance = p.createInstance(businessKey, m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        // verify that custom business key is assigned properly
        assertThat(processInstance.businessKey()).isEqualTo(businessKey);

        // start another process instance with assigned duplicated business key of already active instance
        ProcessInstance<? extends Model> otherProcessInstance = p.createInstance(businessKey, m);
        assertThat(otherProcessInstance.id()).isNotEqualTo(processInstance.id());
        assertThat(otherProcessInstance.businessKey()).isEqualTo(processInstance.businessKey()).isEqualTo(businessKey);
        latch.await(5L, TimeUnit.SECONDS);
        String subject = "Task is ready for ${owners[0].id}";
        String body = "<html>\n"
                + "                    <body>\n"
                + "                    Reason this is my subject<br/>\n"
                + "                    body of notification this is my body\n"
                + "                    </body>\n"
                + "                  </html>";
        assertThat(subjects).containsExactly(subject, subject);
        assertThat(bodies).containsExactly(body, body);
        assertThat(types).containsExactly(DeadlineType.Started, DeadlineType.Started);
    }

    @Test
    public void testUserTaskNotStartedDeadlineWithExpressionReplacementStopTimer() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        Application app = generateCodeProcessesOnly("usertask/UserTasksNotStartedDeadline.bpmn2");
        assertThat(app).isNotNull();

        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskEventListener() {
            @Override
            public void onUserTaskDeadline(UserTaskDeadlineEvent event) {
                latch.countDown();
            }

        });
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksDeadline");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("subject", "this is my subject");
        parameters.put("body", "this is my body");
        m.fromMap(parameters);

        // assign custom business key for process instance
        String businessKey = "business key";
        ProcessInstance<?> processInstance = p.createInstance(businessKey, m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        // verify that custom business key is assigned properly
        assertThat(processInstance.businessKey()).isEqualTo(businessKey);
        IdentityProvider mary = IdentityProviders.of("mary");
        List<UserTaskInstance> userTaskInstances = app.get(UserTasks.class).instances().findByIdentity(mary);
        userTaskInstances.forEach(e -> e.transition(DefaultUserTaskLifeCycle.CLAIM, Collections.emptyMap(), mary));

        // start another process instance with assigned duplicated business key of already active instance
        ProcessInstance<? extends Model> otherProcessInstance = p.createInstance(businessKey, m);
        assertThat(otherProcessInstance.id()).isNotEqualTo(processInstance.id());
        assertThat(otherProcessInstance.businessKey()).isEqualTo(processInstance.businessKey()).isEqualTo(businessKey);
        assertThat(latch.await(5L, TimeUnit.SECONDS)).isFalse();

    }

    @Test
    public void testUserTaskNotStartedReassign() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Application app = generateCodeProcessesOnly("usertask/UserTasksNotStartedReasssign.bpmn2");
        assertThat(app).isNotNull();

        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskEventListener() {
            @Override
            public void onUserTaskAssignment(UserTaskAssignmentEvent event) {
                List<String> users = Arrays.asList(event.getNewUsersId());
                if (users.size() == 1 && users.contains("mike")) {
                    latch.countDown();
                }
            }
        });
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksDeadline");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("subject", "this is my subject");
        parameters.put("body", "this is my body");
        m.fromMap(parameters);

        // assign custom business key for process instance
        String businessKey = "business key";
        ProcessInstance<?> processInstance = p.createInstance(businessKey, m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        // verify that custom business key is assigned properly
        assertThat(processInstance.businessKey()).isEqualTo(businessKey);

        // start another process instance with assigned duplicated business key of already active instance
        ProcessInstance<? extends Model> otherProcessInstance = p.createInstance(businessKey, m);
        assertThat(otherProcessInstance.id()).isNotEqualTo(processInstance.id());
        assertThat(otherProcessInstance.businessKey()).isEqualTo(processInstance.businessKey()).isEqualTo(businessKey);
        assertThat(latch.await(5L, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testUserTaskNotStartedReassignStopTimers() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Application app = generateCodeProcessesOnly("usertask/UserTasksNotStartedReasssign.bpmn2");
        assertThat(app).isNotNull();

        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskEventListener() {
            @Override
            public void onUserTaskAssignment(UserTaskAssignmentEvent event) {
                List<String> users = Arrays.asList(event.getNewUsersId());
                if (users.size() == 1 && users.contains("mike")) {
                    latch.countDown();
                }
            }
        });
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksDeadline");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("subject", "this is my subject");
        parameters.put("body", "this is my body");
        m.fromMap(parameters);

        // assign custom business key for process instance
        String businessKey = "business key";
        ProcessInstance<?> processInstance = p.createInstance(businessKey, m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        // verify that custom business key is assigned properly
        assertThat(processInstance.businessKey()).isEqualTo(businessKey);
        IdentityProvider mary = IdentityProviders.of("mary");
        List<UserTaskInstance> userTaskInstances = app.get(UserTasks.class).instances().findByIdentity(mary);
        userTaskInstances.forEach(e -> e.transition(DefaultUserTaskLifeCycle.CLAIM, Collections.emptyMap(), mary));

        // start another process instance with assigned duplicated business key of already active instance
        ProcessInstance<? extends Model> otherProcessInstance = p.createInstance(businessKey, m);
        assertThat(otherProcessInstance.id()).isNotEqualTo(processInstance.id());
        assertThat(otherProcessInstance.businessKey()).isEqualTo(processInstance.businessKey()).isEqualTo(businessKey);
        assertThat(latch.await(2L, TimeUnit.SECONDS)).isFalse();
    }

    @Test
    public void testUserTaskNotCompletedDeadlineWithReplacement() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Application app = generateCodeProcessesOnly("usertask/UserTasksNotCompletedDeadline.bpmn2");
        assertThat(app).isNotNull();

        List<String> subjects = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        List<UserTaskDeadlineEvent.DeadlineType> types = new ArrayList<>();
        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskEventListener() {
            @Override
            public void onUserTaskDeadline(UserTaskDeadlineEvent event) {
                subjects.add((String) event.getNotification().get("subject"));
                bodies.add((String) event.getNotification().get("body"));
                types.add(event.getType());
                latch.countDown();
            }
        });
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksDeadline");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("body", "my body!!!");
        m.fromMap(parameters);

        // assign custom business key for process instance
        String businessKey = "business key";
        ProcessInstance<?> processInstance = p.createInstance(businessKey, m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        // verify that custom business key is assigned properly
        assertThat(processInstance.businessKey()).isEqualTo(businessKey);

        // start another process instance with assigned duplicated business key of already active instance
        ProcessInstance<? extends Model> otherProcessInstance = p.createInstance(businessKey, m);
        assertThat(otherProcessInstance.id()).isNotEqualTo(processInstance.id());
        assertThat(otherProcessInstance.businessKey()).isEqualTo(processInstance.businessKey()).isEqualTo(businessKey);
        assertTrue(latch.await(5L, TimeUnit.SECONDS));
        latch.await(5L, TimeUnit.SECONDS);
        String subject = "Not completedTask is ready for ${owners[0].id}";
        String body = "my body!!!";
        assertThat(subjects).containsExactly(subject);
        assertThat(bodies).containsExactly(body);
        assertThat(types).containsExactly(DeadlineType.Completed);
    }

    @Test
    public void testUserTaskNotCompletedDeadlineWithReplacementStopTimer() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        Application app = generateCodeProcessesOnly("usertask/UserTasksNotCompletedDeadline.bpmn2");
        assertThat(app).isNotNull();

        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskEventListener() {
            @Override
            public void onUserTaskDeadline(UserTaskDeadlineEvent event) {
                latch.countDown();
            }

        });
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksDeadline");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("subject", "this is my subject");
        parameters.put("body", "this is my body");
        m.fromMap(parameters);

        // assign custom business key for process instance
        String businessKey = "business key";
        ProcessInstance<?> processInstance = p.createInstance(businessKey, m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        // verify that custom business key is assigned properly
        assertThat(processInstance.businessKey()).isEqualTo(businessKey);
        IdentityProvider mary = IdentityProviders.of("mary");
        List<UserTaskInstance> userTaskInstances = app.get(UserTasks.class).instances().findByIdentity(mary);
        userTaskInstances.forEach(e -> {
            e.transition(DefaultUserTaskLifeCycle.CLAIM, emptyMap(), mary);
            e.transition(DefaultUserTaskLifeCycle.COMPLETE, emptyMap(), mary);
        });

        // start another process instance with assigned duplicated business key of already active instance
        ProcessInstance<? extends Model> otherProcessInstance = p.createInstance(businessKey, m);
        assertThat(otherProcessInstance.id()).isNotEqualTo(processInstance.id());
        assertThat(otherProcessInstance.businessKey()).isEqualTo(processInstance.businessKey()).isEqualTo(businessKey);
        assertThat(latch.await(5L, TimeUnit.SECONDS)).isFalse();

    }

    @Test
    public void testUserTaskNotCompletedReassign() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Application app = generateCodeProcessesOnly("usertask/UserTasksNotCompletedReassign.bpmn2");
        assertThat(app).isNotNull();

        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskEventListener() {
            @Override
            public void onUserTaskAssignment(UserTaskAssignmentEvent event) {
                List<String> users = Arrays.asList(event.getNewUsersId());
                if (users.size() == 1 && users.contains("mike")) {
                    latch.countDown();
                }
            }
        });
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksDeadline");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("body", "my body!!!");
        m.fromMap(parameters);

        // assign custom business key for process instance
        String businessKey = "business key";
        ProcessInstance<?> processInstance = p.createInstance(businessKey, m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        // verify that custom business key is assigned properly
        assertThat(processInstance.businessKey()).isEqualTo(businessKey);

        // start another process instance with assigned duplicated business key of already active instance
        ProcessInstance<? extends Model> otherProcessInstance = p.createInstance(businessKey, m);
        assertThat(otherProcessInstance.id()).isNotEqualTo(processInstance.id());
        assertThat(otherProcessInstance.businessKey()).isEqualTo(processInstance.businessKey()).isEqualTo(businessKey);
        assertTrue(latch.await(5L, TimeUnit.SECONDS));

    }

    @Test
    public void testUserTaskNotCompletedReassignStopTimer() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Application app = generateCodeProcessesOnly("usertask/UserTasksNotCompletedReassign.bpmn2");
        assertThat(app).isNotNull();

        app.config().get(UserTaskConfig.class).userTaskEventListeners().listeners().add(new UserTaskEventListener() {
            @Override
            public void onUserTaskAssignment(UserTaskAssignmentEvent event) {
                List<String> users = Arrays.asList(event.getNewUsersId());
                if (users.size() == 1 && users.contains("mike")) {
                    latch.countDown();
                }
            }

        });
        Process<? extends Model> p = app.get(Processes.class).processById("UserTasksDeadline");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("subject", "this is my subject");
        parameters.put("body", "this is my body");
        m.fromMap(parameters);

        // assign custom business key for process instance
        String businessKey = "business key";
        ProcessInstance<?> processInstance = p.createInstance(businessKey, m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        // verify that custom business key is assigned properly
        assertThat(processInstance.businessKey()).isEqualTo(businessKey);
        IdentityProvider mary = IdentityProviders.of("mary");
        List<UserTaskInstance> userTaskInstances = app.get(UserTasks.class).instances().findByIdentity(mary);
        userTaskInstances.forEach(e -> {
            e.transition(DefaultUserTaskLifeCycle.CLAIM, emptyMap(), mary);
            e.transition(DefaultUserTaskLifeCycle.COMPLETE, emptyMap(), mary);
        });

        // start another process instance with assigned duplicated business key of already active instance
        ProcessInstance<? extends Model> otherProcessInstance = p.createInstance(businessKey, m);
        assertThat(otherProcessInstance.id()).isNotEqualTo(processInstance.id());
        assertThat(otherProcessInstance.businessKey()).isEqualTo(processInstance.businessKey()).isEqualTo(businessKey);
        assertThat(latch.await(2L, TimeUnit.SECONDS)).isFalse();

    }
}
