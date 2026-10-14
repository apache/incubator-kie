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
import java.util.List;

import org.jbpm.bpmn2.objects.TestWorkItemHandler;
import org.jbpm.bpmn2.task.MultipleUserTasksModel;
import org.jbpm.bpmn2.task.MultipleUserTasksProcess;
import org.jbpm.test.utils.ProcessTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.Application;
import org.kie.kogito.auth.SecurityPolicy;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.NotAuthorizedException;
import org.kie.kogito.process.workitem.WorkItemNotFoundException;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.WorkItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProcessWorkItemSecurityTest extends JbpmBpmn2TestCase {

    private static final String TASK_1 = "Task1";
    private static final String TASK_2 = "Task2";
    private static final String TASK_3 = "Task3";

    private Application application;
    private TestWorkItemHandler workItemHandler;

    @BeforeEach
    public void setUp() {
        application = ProcessTestHelper.newApplication();
        workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(application, "Human Task", workItemHandler);
    }

    @Test
    public void testGetWorkItemWithAllWorkItemsAccess() {
        Process<MultipleUserTasksModel> process = MultipleUserTasksProcess.newProcess(application);

        ProcessInstance<MultipleUserTasksModel> instance = process.createInstance(process.createModel());

        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<KogitoWorkItem> allWorkItems = workItemHandler.getWorkItems();

        assertThat(allWorkItems).hasSize(3);

        String task1WorkItemId = getTaskWorkItem(allWorkItems, TASK_1);
        String task2WorkItemId = getTaskWorkItem(allWorkItems, TASK_2);
        String task3WorkItemId = getTaskWorkItem(allWorkItems, TASK_3);

        List<WorkItem> assignedWorItems = instance.workItems(SecurityPolicy.of("john", Collections.emptyList()));

        assertThat(assignedWorItems).hasSize(3);

        WorkItem task1WorkItem = instance.workItem(task1WorkItemId, SecurityPolicy.of("john", Collections.emptyList()));

        assertThat(task1WorkItem).isNotNull()
                .hasFieldOrPropertyWithValue("id", task1WorkItemId);

        WorkItem task2WorkItem = instance.workItem(task2WorkItemId, SecurityPolicy.of("john", Collections.emptyList()));

        assertThat(task2WorkItem).isNotNull()
                .hasFieldOrPropertyWithValue("id", task2WorkItemId);

        WorkItem task3WorkItem = instance.workItem(task3WorkItemId, SecurityPolicy.of("john", Collections.emptyList()));

        assertThat(task3WorkItem).isNotNull()
                .hasFieldOrPropertyWithValue("id", task3WorkItemId);

        instance.abort();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);

        assertThat(workItemHandler.getWorkItems()).hasSize(0);
    }

    @Test
    public void testGetWorkItemWithRestrictedWorkItemsAccess() {
        Process<MultipleUserTasksModel> process = MultipleUserTasksProcess.newProcess(application);

        ProcessInstance<MultipleUserTasksModel> instance = process.createInstance(process.createModel());

        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<KogitoWorkItem> allWorkItems = workItemHandler.getWorkItems();

        assertThat(allWorkItems).hasSize(3);

        String task1WorkItemId = getTaskWorkItem(allWorkItems, TASK_1);
        String task2WorkItemId = getTaskWorkItem(allWorkItems, TASK_2);
        String task3WorkItemId = getTaskWorkItem(allWorkItems, TASK_3);

        List<WorkItem> assignedWorItems = instance.workItems(SecurityPolicy.of("alice", Collections.emptyList()));

        assertThat(assignedWorItems).hasSize(1)
                .anyMatch(workItem -> workItem.getId().equals(task3WorkItemId));

        assertThatThrownBy(() -> {
            instance.workItem(task1WorkItemId, SecurityPolicy.of("alice", Collections.emptyList()));
        }).isInstanceOf(NotAuthorizedException.class)
                .hasMessage("Identity 'alice' with roles '[]' is not allowed to access workItem '" + task1WorkItemId + "'");

        assertThatThrownBy(() -> {
            instance.workItem(task2WorkItemId, SecurityPolicy.of("alice", Collections.emptyList()));
        }).isInstanceOf(NotAuthorizedException.class)
                .hasMessage("Identity 'alice' with roles '[]' is not allowed to access workItem '" + task2WorkItemId + "'");

        WorkItem task3WorkItem = instance.workItem(task3WorkItemId, SecurityPolicy.of("alice", Collections.emptyList()));

        assertThat(task3WorkItem).isNotNull()
                .hasFieldOrPropertyWithValue("id", task3WorkItemId);

        instance.abort();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);

        assertThat(workItemHandler.getWorkItems()).hasSize(0);
    }

    @Test
    public void testGetUnexistingWorkItem() {
        Process<MultipleUserTasksModel> process = MultipleUserTasksProcess.newProcess(application);

        ProcessInstance<MultipleUserTasksModel> instance = process.createInstance(process.createModel());

        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        assertThatThrownBy(() -> {
            instance.workItem("wrongWorkItemIdThatWillFail", SecurityPolicy.of("alice", Collections.emptyList()));
        }).isInstanceOf(WorkItemNotFoundException.class)
                .hasMessage("Work item with id 'wrongWorkItemIdThatWillFail' was not found in process instance '" + instance.id() + "'");

        instance.abort();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);
    }

    String getTaskWorkItem(Collection<KogitoWorkItem> allWorkItems, String taskName) {
        return allWorkItems.stream()
                .filter(kogitoWorkItem -> taskName.equals(kogitoWorkItem.getParameter("TaskName")))
                .map(KogitoWorkItem::getStringId)
                .findFirst()
                .orElseThrow();
    }
}
