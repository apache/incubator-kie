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
package org.acme;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kie.kogito.incubation.application.AppRoot;
import org.kie.kogito.incubation.common.EmptyDataContext;
import org.kie.kogito.incubation.common.ExtendedDataContext;
import org.kie.kogito.incubation.common.MapDataContext;
import org.kie.kogito.incubation.processes.ProcessIds;
import org.kie.kogito.incubation.processes.ProcessInstanceId;
import org.kie.kogito.incubation.processes.TaskInstanceId;
import org.kie.kogito.incubation.processes.services.StatefulProcessService;
import org.kie.kogito.incubation.processes.services.contexts.Policy;
import org.kie.kogito.incubation.processes.services.contexts.ProcessMetaDataContext;
import org.kie.kogito.incubation.processes.services.contexts.TaskMetaDataContext;
import org.kie.kogito.incubation.processes.services.contexts.TaskWorkItemDataContext;
import org.kie.kogito.incubation.processes.services.humantask.HumanTaskService;

import com.example.Payload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class StatefulProcessResourceTest {

    String body =
            "{\n" +
                    "    \"traveller\" : {\n" +
                    "        \"firstName\" : \"John\",\n" +
                    "        \"lastName\" : \"Doe\",\n" +
                    "        \"email\" : \"jon.doe@example.com\",\n" +
                    "        \"nationality\" : \"American\",\n" +
                    "        \"address\" : {\n" +
                    "          \t\"street\" : \"main street\",\n" +
                    "          \t\"city\" : \"Boston\",\n" +
                    "          \t\"zipCode\" : \"10005\",\n" +
                    "          \t\"country\" : \"US\"\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n";

    @Inject
    AppRoot appRoot;
    @Inject
    StatefulProcessService processSvc;
    @Inject
    HumanTaskService taskSvc;
    @Inject
    ObjectMapper mapper;

    @Test
    public void createProcess() throws JsonProcessingException {
        /// /processes/approvals
        var id = appRoot.get(ProcessIds.class).get("approvals");
        var ctx = mapper.readValue(body, MapDataContext.class);

        var approvalsCreated = processSvc.create(id, ctx);

        var pid = approvalsCreated.meta()
                .as(ProcessMetaDataContext.class)
                .id(ProcessInstanceId.class);

        var data = approvalsCreated.data().as(MapDataContext.class);
        assertThat(pid.processId()).isEqualTo(id);
        assertThat(data.get("approver")).isNull();
    }

    @Test
    public void getProcess() throws JsonProcessingException {
        /// /processes/approvals
        var id = appRoot.get(ProcessIds.class).get("approvals");
        var ctx = mapper.readValue(body, MapDataContext.class);

        var approvalsCreated = processSvc.create(id, ctx);

        var pid = approvalsCreated.meta()
                .as(ProcessMetaDataContext.class)
                .id(ProcessInstanceId.class);

        assertThat(pid.processId()).isEqualTo(id);

        ExtendedDataContext result = processSvc.get(pid);
        var data = result.data().as(MapDataContext.class);
        assertThat(data.get("approver")).isNull();

    }

    @Test
    public void updateProcess() throws JsonProcessingException {
        /// /processes/approvals
        var id = appRoot.get(ProcessIds.class).get("approvals");
        var ctx = mapper.readValue(body, MapDataContext.class);

        var approvalsCreated = processSvc.create(id, ctx);

        var pid = approvalsCreated.meta()
                .as(ProcessMetaDataContext.class)
                .id(ProcessInstanceId.class);

        var data = approvalsCreated.data().as(MapDataContext.class);
        assertThat(pid.processId()).isEqualTo(id);
        assertThat(data.get("approver")).isNull();

        MapDataContext traveller = ctx.get("traveller", MapDataContext.class);
        traveller.set("firstName", "Josh");
        ctx.set("traveller", traveller);

        ExtendedDataContext updatedResult = processSvc.update(pid, ctx);
        assertThat(updatedResult.data().as(MapDataContext.class)
                .get("traveller", MapDataContext.class).get("firstName")).isEqualTo("Josh");
    }

    @Test
    public void abortProcess() throws JsonProcessingException {
        /// /processes/approvals
        var id = appRoot.get(ProcessIds.class).get("approvals");
        var ctx = mapper.readValue(body, MapDataContext.class);

        var approvalsCreated = processSvc.create(id, ctx);
        var pid = approvalsCreated.meta()
                .as(ProcessMetaDataContext.class)
                .id(ProcessInstanceId.class);

        ExtendedDataContext result = processSvc.abort(pid);
        assertThat(result.data().as(MapDataContext.class).get("approver")).isNull();
    }

    @Test
    @Disabled("Revisited after ht endpoints")
    public void completeTask() throws JsonProcessingException {
        /// /processes/approvals
        var id = appRoot.get(ProcessIds.class).get("approvals");
        var ctx = mapper.readValue(body, MapDataContext.class);

        var approvalsCreated = processSvc.create(id, ctx);

        var pid = approvalsCreated.meta()
                .as(ProcessMetaDataContext.class)
                .id(ProcessInstanceId.class);

        /// set policies for the task (as metadata)
        var taskMeta = TaskMetaDataContext.of(
                Policy.of("admin", List.of("managers")));

        /// /processes/my.process/instances/my.instance.id/tasks
        var tasks = taskSvc.get(pid.tasks(), taskMeta);

        /// /processes/my.process/instances/my.instance.id/tasks/some.task.id

        /// get first active task id
        List<TaskInstanceId> taskIdList = tasks.meta().as(TaskWorkItemDataContext.class).tasks();
        assertThat(taskIdList.size()).isEqualTo(1);
        var taskId = taskIdList.get(0);

        ExtendedDataContext result = taskSvc.complete(taskId,
                ExtendedDataContext.of(
                        taskMeta,
                        MapDataContext.of(Map.of("some", "value"))));

        assertThat(result.data().as(MapDataContext.class).get("approver")).isEqualTo(taskMeta.policy().user());

    }

    @Test
    @Disabled("Revisited after ht endpoints")
    public void completeProcess() throws JsonProcessingException {
        /// /processes/approvals
        var id = appRoot.get(ProcessIds.class).get("approvals");
        var ctx = mapper.readValue(body, MapDataContext.class);

        var approvalsCreated = processSvc.create(id, ctx);

        var pid = approvalsCreated.meta()
                .as(ProcessMetaDataContext.class)
                .id(ProcessInstanceId.class);

        /// set policies for the task (as metadata)
        var taskMeta = TaskMetaDataContext.of(
                Policy.of("admin", List.of("managers")));

        /// /processes/my.process/instances/my.instance.id/tasks
        var tasks = taskSvc.get(pid.tasks(), taskMeta);

        /// /processes/my.process/instances/my.instance.id/tasks/some.task.id

        /// get first active task id
        List<TaskInstanceId> taskIdList = tasks.meta().as(TaskWorkItemDataContext.class).tasks();
        assertThat(taskIdList.size()).isEqualTo(1);
        var taskId = taskIdList.get(0);

        ExtendedDataContext result = taskSvc.complete(taskId,
                ExtendedDataContext.of(
                        taskMeta,
                        MapDataContext.of(Map.of("some", "value"))));

        assertThat(result.data().as(MapDataContext.class).get("approver")).isEqualTo(taskMeta.policy().user());

        /// /processes/my.process/instances/my.instance.id/tasks
        taskMeta = TaskMetaDataContext.of(
                Policy.of("john", List.of("managers")));

        tasks = taskSvc.get(pid.tasks(), taskMeta);

        /// /processes/my.process/instances/my.instance.id/tasks/some.task.id

        /// get first active task id
        taskIdList = tasks.meta().as(TaskWorkItemDataContext.class).tasks();
        assertThat(taskIdList.size()).isEqualTo(1);
        taskId = taskIdList.get(0);

        result = taskSvc.complete(taskId,
                ExtendedDataContext.of(
                        taskMeta,
                        MapDataContext.of(Map.of("some", "value"))));

        assertThat(result.data().as(MapDataContext.class).get("approver")).isEqualTo("admin");

    }

    @Test
    public void signalTaskProcess() {
        var id = appRoot.get(ProcessIds.class).get("flexible");
        MapDataContext dc = MapDataContext.create();
        Payload payload = new Payload();
        payload.setValue("started");
        dc.set("test", payload);

        var created = processSvc.create(id, dc);

        var pid = created.meta()
                .as(ProcessMetaDataContext.class)
                .id(ProcessInstanceId.class);

        assertThat(created.data().as(MapDataContext.class)
                .get("test", Payload.class).getValue()).isEqualTo("started");

        TaskMetaDataContext tmdc = TaskMetaDataContext.of(Policy.of("manager", Collections.emptyList()));

        // send signal to complete (empty data context for this signal)
        ExtendedDataContext taskCreated = taskSvc.create(pid.tasks().get("InitialTask"), ExtendedDataContext.of(tmdc, EmptyDataContext.Instance));

        String tid = taskCreated.data().as(MapDataContext.class).get("id", String.class);
        TaskInstanceId taskInstanceId = pid.tasks().get("InitialTask").instances().get(tid);

        ExtendedDataContext result = taskSvc.complete(taskInstanceId, ExtendedDataContext.of(tmdc, EmptyDataContext.Instance));

        assertThat(result.data().as(MapDataContext.class).get("test", Payload.class).getValue()).isEqualTo("ad-hoc");

    }

    @Test
    @Disabled("Revisited after ht endpoints")
    public void completeProcessTask() {
        var id = appRoot.get(ProcessIds.class).get("signal");
        MapDataContext dc = MapDataContext.create();
        Payload payload = new Payload();
        payload.setValue("started");
        dc.set("test", payload);

        var created = processSvc.create(id, dc);

        var pid = created.meta()
                .as(ProcessMetaDataContext.class)
                .id(ProcessInstanceId.class);

        assertThat(created.data().as(MapDataContext.class)
                .get("test", Payload.class).getValue()).isEqualTo("started");

        // send signal to complete (empty data context for this signal)
        ExtendedDataContext result = processSvc.signal(pid.signals().get("sig"), EmptyDataContext.Instance);

        assertThat(result.data().as(MapDataContext.class)
                .get("test", Payload.class).getValue()).isEqualTo("done");

    }
}
