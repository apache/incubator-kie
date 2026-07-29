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
package org.kie.kogito.app.audit.quarkus;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.kie.kogito.event.job.JobInstanceDataEvent;
import org.kie.kogito.event.process.ProcessInstanceDataEvent;
import org.kie.kogito.event.process.ProcessInstanceErrorDataEvent;
import org.kie.kogito.event.process.ProcessInstanceErrorEventBody;
import org.kie.kogito.event.process.ProcessInstanceEventMetadata;
import org.kie.kogito.event.process.ProcessInstanceNodeDataEvent;
import org.kie.kogito.event.process.ProcessInstanceNodeEventBody;
import org.kie.kogito.event.process.ProcessInstanceStateDataEvent;
import org.kie.kogito.event.process.ProcessInstanceStateEventBody;
import org.kie.kogito.event.process.ProcessInstanceVariableDataEvent;
import org.kie.kogito.event.process.ProcessInstanceVariableEventBody;
import org.kie.kogito.event.usertask.UserTaskInstanceAssignmentDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceAssignmentEventBody;
import org.kie.kogito.event.usertask.UserTaskInstanceAttachmentDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceAttachmentEventBody;
import org.kie.kogito.event.usertask.UserTaskInstanceCommentDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceCommentEventBody;
import org.kie.kogito.event.usertask.UserTaskInstanceDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceDeadlineDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceDeadlineEventBody;
import org.kie.kogito.event.usertask.UserTaskInstanceEventMetadata;
import org.kie.kogito.event.usertask.UserTaskInstanceStateDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceStateEventBody;
import org.kie.kogito.event.usertask.UserTaskInstanceVariableDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceVariableEventBody;
import org.kie.kogito.jobs.service.model.JobStatus;
import org.kie.kogito.jobs.service.model.ScheduledJob;
import org.kie.kogito.process.ProcessInstance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class DataAuditTestUtils {

    public static final String ADDONS = "jobs-management,prometheus-monitoring,process-management";

    private static String toURIEndpoint(String processId) {
        return URI.create("http://localhost:8080/" + processId).toString();
    }

    public static String wrapQuery(String query) {
        return "{ \"query\" : \"" + query + " \"}";
    }

    public static JobInstanceDataEvent newJobEvent(String jobId, String nodeInstanceId, Integer priority,
            String processId, String procesInstanceId, Long repeatInterval, Integer repeatLimit, String rootProcessId, String rootProcessInstanceId,
            JobStatus state, Integer executionCounter) throws Exception {
        return newJobEvent(jobId, nodeInstanceId, priority, processId, procesInstanceId, repeatInterval, repeatLimit, rootProcessId, rootProcessInstanceId, state, executionCounter, null, null);
    }

    public static JobInstanceDataEvent newJobEvent(String jobId, String nodeInstanceId, Integer priority,
            String processId, String procesInstanceId, Long repeatInterval, Integer repeatLimit, String rootProcessId, String rootProcessInstanceId,
            JobStatus state, Integer executionCounter, String exceptionMessage, String exceptionDetails) throws Exception {

        ScheduledJob job = buildScheduledJob(jobId, nodeInstanceId, priority, processId, procesInstanceId,
                repeatInterval, repeatLimit, rootProcessId, rootProcessInstanceId,
                state, executionCounter, exceptionMessage, exceptionDetails);

        return JobInstanceDataEvent.builder()
                .type("JobEvent")
                .source(toURIEndpoint(processId))
                .data(new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsBytes(job))
                .kogitoProcessInstanceId(procesInstanceId)
                .kogitoRootProcessInstanceId(rootProcessInstanceId)
                .kogitoProcessId(processId)
                .kogitoProcessVersion(null)
                .kogitoRootProcessId(rootProcessId)
                .kogitoRootProcessVersion(null)
                .kogitoIdentity("identity")
                .build();
    }

    public static JobInstanceDataEvent newJobEventWithVersion(String jobId, String nodeInstanceId, Integer priority,
            String processId, String processVersion, String processInstanceId, Long repeatInterval, Integer repeatLimit,
            String rootProcessId, String rootProcessVersion, String rootProcessInstanceId,
            JobStatus state, Integer executionCounter) throws Exception {

        ScheduledJob job = buildScheduledJob(jobId, nodeInstanceId, priority, processId, processInstanceId,
                repeatInterval, repeatLimit, rootProcessId, rootProcessInstanceId,
                state, executionCounter, null, null);

        return JobInstanceDataEvent.builder()
                .type("JobEvent")
                .source(toURIEndpoint(processId))
                .data(new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsBytes(job))
                .kogitoProcessInstanceId(processInstanceId)
                .kogitoRootProcessInstanceId(rootProcessInstanceId)
                .kogitoProcessId(processId)
                .kogitoProcessVersion(processVersion)
                .kogitoRootProcessId(rootProcessId)
                .kogitoRootProcessVersion(rootProcessVersion)
                .kogitoIdentity("identity")
                .build();
    }

    private static ScheduledJob buildScheduledJob(String jobId, String nodeInstanceId, Integer priority,
            String processId, String processInstanceId, Long repeatInterval, Integer repeatLimit,
            String rootProcessId, String rootProcessInstanceId,
            JobStatus state, Integer executionCounter, String exceptionMessage, String exceptionDetails) {

        ScheduledJob base = new ScheduledJob();
        base.setId(jobId);
        base.setNodeInstanceId(nodeInstanceId);
        base.setCallbackEndpoint("https://callback");
        base.setPriority(priority);
        base.setProcessId(processId);
        base.setProcessInstanceId(processInstanceId);
        base.setRepeatInterval(repeatInterval);
        base.setRepeatLimit(repeatLimit);
        base.setRootProcessId(rootProcessId);
        base.setRootProcessInstanceId(rootProcessInstanceId);

        return ScheduledJob.builder()
                .job(base)
                .status(state)
                .executionCounter(executionCounter) // Set retries to match executionCounter for testing
                .retries(executionCounter)
                .scheduledId("my scheduler")
                .expirationTime(ZonedDateTime.now())
                .exceptionMessage(exceptionMessage)
                .exceptionDetails(exceptionDetails)
                .build();
    }

    public static JobInstanceDataEvent deriveNewState(JobInstanceDataEvent jobEvent, Integer executionCounter, JobStatus state) throws Exception {
        return deriveNewState(jobEvent, executionCounter, state, null, null);
    }

    public static JobInstanceDataEvent deriveNewState(JobInstanceDataEvent jobEvent, Integer executionCounter, JobStatus state, String exceptionMessage, String exceptionDetails) throws Exception {
        ScheduledJob job = new ObjectMapper().registerModule(new JavaTimeModule()).readValue(jobEvent.getData(), ScheduledJob.class);
        job = ScheduledJob.builder()
                .job(job)
                .status(state)
                .executionCounter(executionCounter)
                .retries(executionCounter) // Set retries to match executionCounter for testing
                .scheduledId("my scheduler")
                .expirationTime(ZonedDateTime.now())
                .exceptionMessage(exceptionMessage)
                .exceptionDetails(exceptionDetails)
                .build();

        return JobInstanceDataEvent.builder()
                .type("JobEvent")
                .source(jobEvent.getSource().toString())
                .data(new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsBytes(job))
                .kogitoProcessInstanceId(jobEvent.getKogitoProcessInstanceId())
                .kogitoRootProcessInstanceId(jobEvent.getKogitoRootProcessInstanceId())
                .kogitoProcessId(jobEvent.getKogitoProcessId())
                .kogitoProcessVersion(jobEvent.getKogitoProcessVersion())
                .kogitoRootProcessId(jobEvent.getKogitoRootProcessId())
                .kogitoRootProcessVersion(jobEvent.getKogitoRootProcessVersion())
                .kogitoIdentity("identity")
                .build();
    }

    public static ProcessInstanceStateDataEvent newProcessInstanceStateEvent(
            String processId, String processInstanceId, Integer status, String rootProcessInstanceId, String rootProcessId,
            String parentProcessInstanceId, String identity, int eventType) {

        String processVersion = "1.0";
        String rootProcessVersion = "2.0";
        String processType = "BPMN2";
        ProcessInstanceStateEventBody body = ProcessInstanceStateEventBody.create()
                .processInstanceId(processInstanceId)
                .parentInstanceId(parentProcessInstanceId)
                .rootProcessInstanceId(rootProcessInstanceId)
                .rootProcessId(rootProcessId)
                .processId(processId)
                .processType(processType)
                .processVersion(processVersion)
                .processName(UUID.randomUUID().toString())
                .eventDate(new Date())
                .state(status)
                .businessKey("BusinessKey" + processInstanceId)
                .roles("admin", "role2")
                .eventUser(identity)
                .eventType(eventType)
                .build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ProcessInstanceEventMetadata.PROCESS_INSTANCE_ID_META_DATA, processInstanceId);
        metadata.put(ProcessInstanceEventMetadata.PROCESS_VERSION_META_DATA, processVersion);
        metadata.put(ProcessInstanceEventMetadata.PROCESS_ID_META_DATA, processId);
        metadata.put(ProcessInstanceEventMetadata.PROCESS_INSTANCE_STATE_META_DATA, String.valueOf(status));
        metadata.put(ProcessInstanceEventMetadata.PROCESS_TYPE_META_DATA, processType);
        metadata.put(ProcessInstanceEventMetadata.PARENT_PROCESS_INSTANCE_ID_META_DATA, parentProcessInstanceId);
        metadata.put(ProcessInstanceEventMetadata.ROOT_PROCESS_ID_META_DATA, rootProcessId);
        metadata.put(ProcessInstanceEventMetadata.ROOT_PROCESS_VERSION_META_DATA, rootProcessVersion);
        metadata.put(ProcessInstanceEventMetadata.ROOT_PROCESS_INSTANCE_ID_META_DATA, rootProcessInstanceId);

        return ProcessInstanceStateDataEvent.builder()
                .source(toURIEndpoint(processId))
                .kogitoAddons(ADDONS)
                .kogitoIdentity(identity)
                .metaData(metadata)
                .data(body)
                .kogitoBusinessKey(body.getBusinessKey())
                .build();
    }

    public static ProcessInstanceStateDataEvent newProcessInstanceStateEventWithVersion(
            String processId, String processVersion, String processInstanceId, Integer status,
            String rootProcessInstanceId, String rootProcessId, String rootProcessVersion,
            String parentProcessInstanceId, String identity, int eventType) {

        String processType = "BPMN2";
        ProcessInstanceStateEventBody body = ProcessInstanceStateEventBody.create()
                .processInstanceId(processInstanceId)
                .parentInstanceId(parentProcessInstanceId)
                .rootProcessInstanceId(rootProcessInstanceId)
                .rootProcessId(rootProcessId)
                .processId(processId)
                .processType(processType)
                .processVersion(processVersion)
                .processName(UUID.randomUUID().toString())
                .eventDate(new Date())
                .state(status)
                .businessKey("BusinessKey" + processInstanceId)
                .roles("admin", "role2")
                .eventUser(identity)
                .eventType(eventType)
                .build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ProcessInstanceEventMetadata.PROCESS_INSTANCE_ID_META_DATA, processInstanceId);
        metadata.put(ProcessInstanceEventMetadata.PROCESS_VERSION_META_DATA, processVersion);
        metadata.put(ProcessInstanceEventMetadata.PROCESS_ID_META_DATA, processId);
        metadata.put(ProcessInstanceEventMetadata.PROCESS_INSTANCE_STATE_META_DATA, String.valueOf(status));
        metadata.put(ProcessInstanceEventMetadata.PROCESS_TYPE_META_DATA, processType);
        metadata.put(ProcessInstanceEventMetadata.PARENT_PROCESS_INSTANCE_ID_META_DATA, parentProcessInstanceId);
        metadata.put(ProcessInstanceEventMetadata.ROOT_PROCESS_ID_META_DATA, rootProcessId);
        metadata.put(ProcessInstanceEventMetadata.ROOT_PROCESS_VERSION_META_DATA, rootProcessVersion);
        metadata.put(ProcessInstanceEventMetadata.ROOT_PROCESS_INSTANCE_ID_META_DATA, rootProcessInstanceId);

        return ProcessInstanceStateDataEvent.builder()
                .source(toURIEndpoint(processId))
                .kogitoAddons(ADDONS)
                .kogitoIdentity(identity)
                .metaData(metadata)
                .data(body)
                .kogitoBusinessKey(body.getBusinessKey())
                .build();
    }

    public static ProcessInstanceStateDataEvent deriveProcessInstanceStateEvent(ProcessInstanceStateDataEvent event, String identity, int status, int eventType) {

        ProcessInstanceStateEventBody body = ProcessInstanceStateEventBody.create()
                .processInstanceId(event.getData().getProcessInstanceId())
                .parentInstanceId(event.getData().getParentInstanceId())
                .rootProcessInstanceId(event.getData().getRootProcessInstanceId())
                .rootProcessId(event.getData().getRootProcessId())
                .processId(event.getData().getProcessId())
                .processType(event.getData().getProcessType())
                .processVersion(event.getData().getProcessVersion())
                .processName(event.getData().getProcessName())
                .eventDate(new Date())
                .state(status)
                .businessKey(event.getData().getBusinessKey())
                .roles("admin")
                .eventUser(identity)
                .eventType(eventType)
                .build();

        return ProcessInstanceStateDataEvent.builder()
                .source(toURIEndpoint(body.getProcessId()))
                .kogitoAddons(ADDONS)
                .kogitoIdentity(identity)
                .metaData(extractProcessInstanceEventMetadata(event))
                .data(body)
                .kogitoBusinessKey(body.getBusinessKey())
                .build();
    }

    public static ProcessInstanceNodeDataEvent newProcessInstanceNodeEvent(
            ProcessInstanceStateDataEvent pEvent,
            String nodeType, String nodeDefintionId, String nodeInstanceId, String nodeName, String connection, String identity, int eventType) {

        ProcessInstanceNodeEventBody body = ProcessInstanceNodeEventBody.create()
                .processInstanceId(pEvent.getData().getProcessInstanceId())
                .processId(pEvent.getData().getProcessId())
                .processVersion(pEvent.getData().getProcessVersion())
                .eventDate(new Date())
                .eventUser(identity)
                .eventType(eventType)
                .nodeName(nodeName)
                .nodeType(nodeType)
                .nodeDefinitionId(nodeDefintionId)
                .nodeInstanceId(nodeInstanceId)
                .connectionNodeDefinitionId(connection)
                .build();

        return ProcessInstanceNodeDataEvent.builder()
                .source(toURIEndpoint(body.getProcessId()))
                .kogitoAddons(ADDONS)
                .kogitoIdentity(identity)
                .metaData(extractProcessInstanceEventMetadata(pEvent))
                .data(body)
                .kogitoBusinessKey(pEvent.getKogitoBusinessKey())
                .build();
    }

    public static ProcessInstanceVariableDataEvent newProcessInstanceVariableEvent(
            ProcessInstanceStateDataEvent pEvent,
            String variableId, String variableName, Object variableValue, String identity) {

        ProcessInstanceVariableEventBody body = ProcessInstanceVariableEventBody.create()
                .eventDate(new Date())
                .eventUser(identity)
                .processId(pEvent.getData().getProcessId())
                .processVersion(pEvent.getData().getProcessVersion())
                .processInstanceId(pEvent.getData().getProcessInstanceId())
                .variableId(variableId)
                .variableName(variableName)
                .variableValue(variableValue)
                .build();

        return ProcessInstanceVariableDataEvent.builder()
                .source(toURIEndpoint(body.getProcessId()))
                .kogitoAddons(ADDONS)
                .kogitoIdentity(identity)
                .metaData(extractProcessInstanceEventMetadata(pEvent))
                .data(body)
                .kogitoBusinessKey(pEvent.getKogitoBusinessKey())
                .build();
    }

    public static ProcessInstanceErrorDataEvent newProcessInstanceErrorEvent(
            ProcessInstanceStateDataEvent pEvent,
            String nodeDefintionId, String nodeInstanceId, String errorMessage, String identity) {

        ProcessInstanceErrorEventBody body = ProcessInstanceErrorEventBody.create()
                .eventDate(new Date())
                .eventUser(identity)
                .processId(pEvent.getData().getProcessId())
                .processVersion(pEvent.getData().getProcessVersion())
                .processInstanceId(pEvent.getData().getProcessInstanceId())
                .nodeDefinitionId(nodeDefintionId)
                .nodeInstanceId(nodeInstanceId)
                .errorMessage(errorMessage)
                .build();

        return ProcessInstanceErrorDataEvent.builder()
                .source(toURIEndpoint(body.getProcessId()))
                .kogitoAddons(ADDONS)
                .kogitoIdentity(identity)
                .metaData(extractProcessInstanceEventMetadata(pEvent))
                .data(body)
                .kogitoBusinessKey(pEvent.getKogitoBusinessKey())
                .build();
    }

    private static Map<String, Object> extractProcessInstanceEventMetadata(ProcessInstanceDataEvent<?> pEvent) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ProcessInstanceEventMetadata.PROCESS_INSTANCE_ID_META_DATA, pEvent.getKogitoProcessInstanceId());
        metadata.put(ProcessInstanceEventMetadata.PROCESS_VERSION_META_DATA, pEvent.getKogitoProcessVersion());
        metadata.put(ProcessInstanceEventMetadata.PROCESS_ID_META_DATA, pEvent.getKogitoProcessId());
        metadata.put(ProcessInstanceEventMetadata.PROCESS_INSTANCE_STATE_META_DATA, pEvent.getKogitoProcessInstanceState());
        metadata.put(ProcessInstanceEventMetadata.PROCESS_TYPE_META_DATA, pEvent.getKogitoProcessType());
        metadata.put(ProcessInstanceEventMetadata.PARENT_PROCESS_INSTANCE_ID_META_DATA, pEvent.getKogitoParentProcessInstanceId());
        metadata.put(ProcessInstanceEventMetadata.ROOT_PROCESS_ID_META_DATA, pEvent.getKogitoRootProcessId());
        metadata.put(ProcessInstanceEventMetadata.ROOT_PROCESS_VERSION_META_DATA, pEvent.getKogitoRootProcessVersion());
        metadata.put(ProcessInstanceEventMetadata.ROOT_PROCESS_INSTANCE_ID_META_DATA, pEvent.getKogitoRootProcessInstanceId());
        return metadata;
    }

    // user task instance stuff
    public static UserTaskInstanceStateDataEvent newUserTaskInstanceStateEvent(
            String eventUser, String userTaskDefinitionId, String userTaskInstanceId, String userTaskName, String eventType,
            String userTaskDescription, String userTaskPriority, String userTaskReferenceName, String state, String actualOwner, String processInstanceId) {

        String processId = UUID.randomUUID().toString();
        String processVersion = "1.0";
        String processType = "BPMN2";

        UserTaskInstanceStateEventBody body = UserTaskInstanceStateEventBody.create()
                .eventUser(eventUser)
                .eventDate(new Date())
                .userTaskDefinitionId(userTaskDefinitionId)
                .userTaskInstanceId(userTaskInstanceId)
                .userTaskName(userTaskName)
                .eventType(eventType)
                .userTaskDescription(userTaskDescription)
                .userTaskDescription(userTaskPriority)
                .userTaskReferenceName(userTaskReferenceName)
                .state(state)
                .actualOwner(actualOwner)
                .processInstanceId(processInstanceId)
                .build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ProcessInstanceEventMetadata.PROCESS_INSTANCE_ID_META_DATA, processInstanceId);
        metadata.put(ProcessInstanceEventMetadata.PROCESS_VERSION_META_DATA, processVersion);
        metadata.put(ProcessInstanceEventMetadata.PROCESS_ID_META_DATA, processId);
        metadata.put(ProcessInstanceEventMetadata.PROCESS_INSTANCE_STATE_META_DATA, String.valueOf(ProcessInstance.STATE_ACTIVE));
        metadata.put(ProcessInstanceEventMetadata.PROCESS_TYPE_META_DATA, processType);
        metadata.put(ProcessInstanceEventMetadata.PARENT_PROCESS_INSTANCE_ID_META_DATA, null);
        metadata.put(ProcessInstanceEventMetadata.ROOT_PROCESS_ID_META_DATA, null);
        metadata.put(ProcessInstanceEventMetadata.ROOT_PROCESS_VERSION_META_DATA, null);
        metadata.put(ProcessInstanceEventMetadata.ROOT_PROCESS_INSTANCE_ID_META_DATA, null);
        metadata.put(UserTaskInstanceEventMetadata.USER_TASK_INSTANCE_ID_META_DATA, userTaskInstanceId);
        metadata.put(UserTaskInstanceEventMetadata.USER_TASK_INSTANCE_STATE_META_DATA, state);

        return UserTaskInstanceStateDataEvent.builder()
                .source(toURIEndpoint(processId))
                .kogitoAddons(ADDONS)
                .kogitoIdentity(eventUser)
                .metaData(metadata)
                .data(body)
                .kogitoBusinessKey(UUID.randomUUID().toString())
                .build();
    }

    public static UserTaskInstanceVariableDataEvent newUserTaskInstanceVariableEvent(UserTaskInstanceStateDataEvent pEvent,
            String eventUser, String variableId, String variableName, String variableType, Object variableValue) {

        UserTaskInstanceVariableEventBody body = UserTaskInstanceVariableEventBody.create()
                .eventUser(eventUser)
                .eventDate(new Date())
                .userTaskDefinitionId(pEvent.getData().getUserTaskDefinitionId())
                .userTaskInstanceId(pEvent.getData().getUserTaskInstanceId())
                .userTaskName(pEvent.getData().getUserTaskName())
                .variableId(variableId)
                .variableName(variableName)
                .variableType(variableType)
                .variableValue(variableValue)
                .build();

        return UserTaskInstanceVariableDataEvent.builder()
                .source(toURIEndpoint(pEvent.getKogitoProcessId()))
                .kogitoAddons(ADDONS)
                .kogitoIdentity(eventUser)
                .metaData(extractUserTaskInstanceEventMetadata(pEvent))
                .data(body)
                .kogitoBusinessKey(pEvent.getKogitoBusinessKey())
                .build();
    }

    public static UserTaskInstanceAssignmentDataEvent newUserTaskInstanceAssignmentEvent(UserTaskInstanceStateDataEvent pEvent,
            String eventUser, String assigmentType, String... users) {

        UserTaskInstanceAssignmentEventBody body = UserTaskInstanceAssignmentEventBody.create()
                .eventUser(eventUser)
                .eventDate(new Date())
                .userTaskDefinitionId(pEvent.getData().getUserTaskDefinitionId())
                .userTaskInstanceId(pEvent.getData().getUserTaskInstanceId())
                .userTaskName(pEvent.getData().getUserTaskName())
                .assignmentType(assigmentType)
                .users(users)
                .build();

        return UserTaskInstanceAssignmentDataEvent.builder()
                .source(toURIEndpoint(pEvent.getKogitoProcessId()))
                .kogitoAddons(ADDONS)
                .kogitoIdentity(eventUser)
                .metaData(extractUserTaskInstanceEventMetadata(pEvent))
                .data(body)
                .kogitoBusinessKey(pEvent.getKogitoBusinessKey())
                .build();
    }

    public static UserTaskInstanceAttachmentDataEvent newUserTaskInstanceAttachmentEvent(UserTaskInstanceStateDataEvent pEvent,
            String eventUser, String attachmentId, String attachmentName, URI attachmentURI, Integer eventType) {

        UserTaskInstanceAttachmentEventBody body = UserTaskInstanceAttachmentEventBody.create()
                .eventUser(eventUser)
                .eventDate(new Date())
                .userTaskDefinitionId(pEvent.getData().getUserTaskDefinitionId())
                .userTaskInstanceId(pEvent.getData().getUserTaskInstanceId())
                .userTaskName(pEvent.getData().getUserTaskName())
                .attachmentId(attachmentId)
                .attachmentName(attachmentName)
                .attachmentURI(attachmentURI)
                .eventType(eventType)
                .build();

        return UserTaskInstanceAttachmentDataEvent.builder()
                .source(toURIEndpoint(pEvent.getKogitoProcessId())).kogitoAddons(ADDONS)
                .kogitoIdentity(eventUser)
                .metaData(extractUserTaskInstanceEventMetadata(pEvent))
                .data(body)
                .kogitoBusinessKey(pEvent.getKogitoBusinessKey())
                .build();
    }

    public static UserTaskInstanceDeadlineDataEvent newUserTaskInstanceDeadlineEvent(UserTaskInstanceStateDataEvent pEvent,
            String eventUser, Map<String, Object> inputs, Map<String, Object> notifications) {

        UserTaskInstanceDeadlineEventBody body = UserTaskInstanceDeadlineEventBody.create()
                .eventUser(eventUser)
                .eventDate(new Date())
                .userTaskDefinitionId(pEvent.getData().getUserTaskDefinitionId())
                .userTaskInstanceId(pEvent.getData().getUserTaskInstanceId())
                .userTaskName(pEvent.getData().getUserTaskName())
                .inputs(inputs)
                .notification(notifications)
                .build();

        return UserTaskInstanceDeadlineDataEvent.builder()
                .source(toURIEndpoint(pEvent.getKogitoProcessId()))
                .kogitoAddons(ADDONS)
                .kogitoIdentity(eventUser)
                .metaData(extractUserTaskInstanceEventMetadata(pEvent))
                .data(body)
                .kogitoBusinessKey(pEvent.getKogitoBusinessKey())
                .build();
    }

    public static UserTaskInstanceCommentDataEvent newUserTaskInstanceCommentEvent(UserTaskInstanceStateDataEvent pEvent,
            String eventUser, String commentId, String commentContent, Integer eventType) {

        UserTaskInstanceCommentEventBody body = UserTaskInstanceCommentEventBody.create()
                .eventUser(eventUser)
                .eventDate(new Date())
                .userTaskDefinitionId(pEvent.getData().getUserTaskDefinitionId())
                .userTaskInstanceId(pEvent.getData().getUserTaskInstanceId())
                .userTaskName(pEvent.getData().getUserTaskName())
                .commentId(commentId)
                .commentContent(commentContent)
                .eventType(eventType)
                .build();

        return UserTaskInstanceCommentDataEvent.builder()
                .source(toURIEndpoint(pEvent.getKogitoProcessId()))
                .kogitoAddons(ADDONS)
                .kogitoIdentity(eventUser)
                .metaData(extractUserTaskInstanceEventMetadata(pEvent))
                .data(body)
                .kogitoBusinessKey(pEvent.getKogitoBusinessKey())
                .build();
    }

    private static Map<String, Object> extractUserTaskInstanceEventMetadata(UserTaskInstanceDataEvent<?> pEvent) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ProcessInstanceEventMetadata.PROCESS_INSTANCE_ID_META_DATA, pEvent.getKogitoProcessInstanceId());
        metadata.put(ProcessInstanceEventMetadata.PROCESS_VERSION_META_DATA, pEvent.getKogitoProcessVersion());
        metadata.put(ProcessInstanceEventMetadata.PROCESS_ID_META_DATA, pEvent.getKogitoProcessId());
        metadata.put(ProcessInstanceEventMetadata.PROCESS_INSTANCE_STATE_META_DATA, pEvent.getKogitoProcessInstanceState());
        metadata.put(ProcessInstanceEventMetadata.PROCESS_TYPE_META_DATA, pEvent.getKogitoProcessType());
        metadata.put(ProcessInstanceEventMetadata.PARENT_PROCESS_INSTANCE_ID_META_DATA, pEvent.getKogitoParentProcessInstanceId());
        metadata.put(ProcessInstanceEventMetadata.ROOT_PROCESS_ID_META_DATA, pEvent.getKogitoRootProcessId());
        metadata.put(ProcessInstanceEventMetadata.ROOT_PROCESS_VERSION_META_DATA, pEvent.getKogitoRootProcessVersion());
        metadata.put(ProcessInstanceEventMetadata.ROOT_PROCESS_INSTANCE_ID_META_DATA, pEvent.getKogitoRootProcessInstanceId());
        metadata.put(UserTaskInstanceEventMetadata.USER_TASK_INSTANCE_ID_META_DATA, pEvent.getKogitoUserTaskInstanceId());
        metadata.put(UserTaskInstanceEventMetadata.USER_TASK_INSTANCE_STATE_META_DATA, pEvent.getKogitoUserTaskInstanceState());
        return metadata;
    }
}
