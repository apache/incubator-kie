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
package org.kie.kogito.app.audit.jpa;

import java.io.IOException;
import java.net.MalformedURLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.kie.kogito.app.audit.api.DataAuditContext;
import org.kie.kogito.app.audit.api.DataAuditQuery;
import org.kie.kogito.app.audit.jpa.model.AbstractProcessInstanceLog;
import org.kie.kogito.app.audit.jpa.model.AbstractUserTaskInstanceLog;
import org.kie.kogito.app.audit.jpa.model.AuditQuery;
import org.kie.kogito.app.audit.jpa.model.JobExecutionLog;
import org.kie.kogito.app.audit.jpa.model.ProcessInstanceErrorLog;
import org.kie.kogito.app.audit.jpa.model.ProcessInstanceNodeLog;
import org.kie.kogito.app.audit.jpa.model.ProcessInstanceNodeLog.NodeLogType;
import org.kie.kogito.app.audit.jpa.model.ProcessInstanceStateLog;
import org.kie.kogito.app.audit.jpa.model.ProcessInstanceStateLog.ProcessStateLogType;
import org.kie.kogito.app.audit.jpa.model.ProcessInstanceVariableLog;
import org.kie.kogito.app.audit.jpa.model.UserTaskInstanceAssignmentLog;
import org.kie.kogito.app.audit.jpa.model.UserTaskInstanceAttachmentLog;
import org.kie.kogito.app.audit.jpa.model.UserTaskInstanceCommentLog;
import org.kie.kogito.app.audit.jpa.model.UserTaskInstanceDeadlineLog;
import org.kie.kogito.app.audit.jpa.model.UserTaskInstanceStateLog;
import org.kie.kogito.app.audit.jpa.model.UserTaskInstanceVariableLog;
import org.kie.kogito.app.audit.jpa.model.UserTaskInstanceVariableLog.VariableType;
import org.kie.kogito.app.audit.spi.DataAuditStore;
import org.kie.kogito.event.job.JobInstanceDataEvent;
import org.kie.kogito.event.process.ProcessInstanceDataEvent;
import org.kie.kogito.event.process.ProcessInstanceErrorDataEvent;
import org.kie.kogito.event.process.ProcessInstanceNodeDataEvent;
import org.kie.kogito.event.process.ProcessInstanceNodeEventBody;
import org.kie.kogito.event.process.ProcessInstanceSLADataEvent;
import org.kie.kogito.event.process.ProcessInstanceStateDataEvent;
import org.kie.kogito.event.process.ProcessInstanceStateEventBody;
import org.kie.kogito.event.process.ProcessInstanceVariableDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceAssignmentDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceAttachmentDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceCommentDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceDeadlineDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceStateDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceVariableDataEvent;
import org.kie.kogito.jobs.service.model.ScheduledJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.persistence.EntityManager;

public class JPADataAuditStore implements DataAuditStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(JPADataAuditStore.class);

    private ObjectMapper mapper;

    public JPADataAuditStore() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void storeProcessInstanceDataEvent(DataAuditContext context, ProcessInstanceStateDataEvent event) {
        ProcessInstanceStateLog log = new ProcessInstanceStateLog();

        setProcessCommonAttributes(log, event);
        log.setRootProcessVersion(getOnlyIfFilled(event::getKogitoRootProcessVersion));
        log.setState(String.valueOf(event.getData().getState()));
        log.setRoles(event.getData().getRoles());
        log.setSlaDueDate(event.getData().getSlaDueDate());

        log.setEventType(fromProcessInstanceStateDataEvent(event));
        log.setEventUser(event.getData().getEventUser());

        EntityManager entityManager = context.getContext();

        entityManager.persist(log);
    }

    private ProcessStateLogType fromProcessInstanceStateDataEvent(ProcessInstanceStateDataEvent event) {
        return switch (event.getData().getEventType()) {
            case ProcessInstanceStateEventBody.EVENT_TYPE_STARTED -> ProcessStateLogType.ACTIVE;
            case ProcessInstanceStateEventBody.EVENT_TYPE_ENDED -> ProcessStateLogType.COMPLETED;
            case ProcessInstanceStateEventBody.EVENT_TYPE_MIGRATED -> ProcessStateLogType.MIGRATED;
            case ProcessInstanceStateEventBody.EVENT_TYPE_UPDATED -> ProcessStateLogType.STATE_UPDATED;
            case ProcessInstanceStateEventBody.EVENT_TYPE_RETRIGGERED -> ProcessStateLogType.RETRIGGERED;
            default -> throw new IllegalArgumentException("Unknown ProcessInstanceStateDataEvent type " + event.getData().getEventType());
        };
    }

    @Override
    public void storeProcessInstanceDataEvent(DataAuditContext context, ProcessInstanceErrorDataEvent event) {
        ProcessInstanceErrorLog log = new ProcessInstanceErrorLog();

        setProcessCommonAttributes(log, event);

        log.setErrorMessage(event.getData().getErrorMessage());
        log.setNodeDefinitionId(event.getData().getNodeDefinitionId());
        log.setNodeInstanceId(event.getData().getNodeInstanceId());

        log.setEventUser(event.getData().getEventUser());

        EntityManager entityManager = context.getContext();
        entityManager.persist(log);
    }

    @Override
    public void storeProcessInstanceDataEvent(DataAuditContext context, ProcessInstanceNodeDataEvent event) {
        ProcessInstanceNodeLog log = new ProcessInstanceNodeLog();

        setProcessCommonAttributes(log, event);

        log.setConnection(event.getData().getConnectionNodeDefinitionId());
        log.setNodeDefinitionId(event.getData().getNodeDefinitionId());
        log.setNodeType(event.getData().getNodeType());
        log.setSlaDueDate(event.getData().getSlaDueDate());

        log.setNodeInstanceId(event.getData().getNodeInstanceId());
        log.setNodeName(event.getData().getNodeName());

        log.setEventType(fromProcessInstanceNodeDataEvent(event));
        log.setEventUser(event.getData().getEventUser());

        log.setWorkItemId(event.getData().getWorkItemId());

        if (event.getData().getInputParameters() != null) {
            log.setInputArgs(toJsonString(event.getData().getInputParameters()));
        }
        if (event.getData().getOutputParameters() != null) {
            log.setOutputArgs(toJsonString(event.getData().getOutputParameters()));
        }

        EntityManager entityManager = context.getContext();
        entityManager.persist(log);
    }

    private NodeLogType fromProcessInstanceNodeDataEvent(ProcessInstanceNodeDataEvent event) {
        return switch (event.getData().getEventType()) {
            case ProcessInstanceNodeEventBody.EVENT_TYPE_ENTER -> NodeLogType.ENTER;
            case ProcessInstanceNodeEventBody.EVENT_TYPE_EXIT -> NodeLogType.EXIT;
            case ProcessInstanceNodeEventBody.EVENT_TYPE_ABORTED -> NodeLogType.ABORTED;
            case ProcessInstanceNodeEventBody.EVENT_TYPE_SKIPPED -> NodeLogType.SKIPPED;
            case ProcessInstanceNodeEventBody.EVENT_TYPE_OBSOLETE -> NodeLogType.OBSOLETE;
            case ProcessInstanceNodeEventBody.EVENT_TYPE_ERROR -> NodeLogType.ERROR;
            case ProcessInstanceNodeEventBody.EVENT_TYPE_UPDATED -> NodeLogType.STATE_UPDATED;
            default -> throw new IllegalArgumentException("Unknown ProcessInstanceNodeDataEvent type " + event.getData().getEventType());
        };
    }

    @Override
    public void storeProcessInstanceDataEvent(DataAuditContext context, ProcessInstanceSLADataEvent event) {
        EntityManager entityManager = context.getContext();

        if (event.getData().getNodeDefinitionId() == null) {
            ProcessInstanceStateLog log = new ProcessInstanceStateLog();
            setProcessCommonAttributes(log, event);
            log.setEventType(ProcessStateLogType.SLA_VIOLATION);
            log.setSlaDueDate(event.getData().getSlaDueDate());
            entityManager.persist(log);
        } else {
            ProcessInstanceNodeLog log = new ProcessInstanceNodeLog();
            setProcessCommonAttributes(log, event);
            log.setNodeDefinitionId(event.getData().getNodeDefinitionId());
            log.setNodeInstanceId(event.getData().getNodeInstanceId());
            log.setNodeName(event.getData().getNodeName());
            log.setNodeType(event.getData().getNodeType());
            log.setEventType(NodeLogType.SLA_VIOLATION);
            log.setSlaDueDate(event.getData().getSlaDueDate());
            entityManager.persist(log);
        }
    }

    @Override
    public void storeProcessInstanceDataEvent(DataAuditContext context, ProcessInstanceVariableDataEvent event) {

        ProcessInstanceVariableLog log = new ProcessInstanceVariableLog();

        setProcessCommonAttributes(log, event);

        log.setVariableId(event.getData().getVariableId());
        log.setVariableName(event.getData().getVariableName());
        log.setVariableValue(toJsonString(event.getData().getVariableValue()));

        log.setEventUser(event.getData().getEventUser());

        EntityManager entityManager = context.getContext();
        entityManager.persist(log);

    }

    private void setProcessCommonAttributes(AbstractProcessInstanceLog log, ProcessInstanceDataEvent<?> event) {
        log.setEventId(event.getId());
        log.setEventDate(new Date(event.getTime().toInstant().toEpochMilli()));
        log.setProcessType(event.getKogitoProcessType());
        log.setProcessId(event.getKogitoProcessId());
        log.setProcessVersion(event.getKogitoProcessVersion());
        log.setProcessInstanceId(event.getKogitoProcessInstanceId());
        log.setParentProcessInstanceId(getOnlyIfFilled(event::getKogitoParentProcessInstanceId));
        log.setRootProcessId(getOnlyIfFilled(event::getKogitoRootProcessId));
        log.setRootProcessInstanceId(getOnlyIfFilled(event::getKogitoRootProcessInstanceId));
        log.setBusinessKey(event.getKogitoBusinessKey());
    }

    private String getOnlyIfFilled(Supplier<String> producer) {
        String data = producer.get();
        return data != null && !data.isBlank() ? data : null;
    }

    @Override
    public void storeUserTaskInstanceDataEvent(DataAuditContext context, UserTaskInstanceAssignmentDataEvent event) {
        UserTaskInstanceAssignmentLog log = new UserTaskInstanceAssignmentLog();

        setUserTaskCommonAttributes(log, event);
        log.setUserTaskDefinitionId(event.getData().getUserTaskDefinitionId());
        log.setEventUser(event.getData().getEventUser());
        log.setUsers(event.getData().getUsers());
        log.setAssignmentType(event.getData().getAssignmentType());
        EntityManager entityManager = context.getContext();
        entityManager.persist(log);

    }

    @Override
    public void storeUserTaskInstanceDataEvent(DataAuditContext context, UserTaskInstanceAttachmentDataEvent event) {
        UserTaskInstanceAttachmentLog log = new UserTaskInstanceAttachmentLog();

        setUserTaskCommonAttributes(log, event);
        log.setUserTaskDefinitionId(event.getData().getUserTaskDefinitionId());
        log.setEventUser(event.getData().getEventUser());
        log.setAttachmentId(event.getData().getAttachmentId());
        log.setAttachmentName(event.getData().getAttachmentName());

        if (event.getData().getAttachmentURI() != null) {
            try {
                log.setAttachmentURI(event.getData().getAttachmentURI().toURL());
            } catch (MalformedURLException e) {
                LOGGER.error("Could not serialize url {}", e);
            }
        }

        log.setEventType(event.getData().getEventType());
        EntityManager entityManager = context.getContext();
        entityManager.persist(log);

    }

    @Override
    public void storeUserTaskInstanceDataEvent(DataAuditContext context, UserTaskInstanceCommentDataEvent event) {
        UserTaskInstanceCommentLog log = new UserTaskInstanceCommentLog();

        setUserTaskCommonAttributes(log, event);
        log.setUserTaskDefinitionId(event.getData().getUserTaskDefinitionId());
        log.setEventUser(event.getData().getEventUser());
        log.setCommentId(event.getData().getCommentId());
        log.setCommentContent(event.getData().getCommentContent());
        log.setEventType(event.getData().getEventType());
        EntityManager entityManager = context.getContext();
        entityManager.persist(log);

    }

    @Override
    public void storeUserTaskInstanceDataEvent(DataAuditContext context, UserTaskInstanceDeadlineDataEvent event) {
        UserTaskInstanceDeadlineLog log = new UserTaskInstanceDeadlineLog();

        setUserTaskCommonAttributes(log, event);
        log.setUserTaskDefinitionId(event.getData().getUserTaskDefinitionId());
        log.setEventUser(event.getData().getEventUser());
        if (event.getData().getNotification() != null) {
            Map<String, String> data = new HashMap<>();
            for (Map.Entry<String, Object> entry : event.getData().getNotification().entrySet()) {
                data.put(entry.getKey(), entry.getValue().toString());
            }
            log.setNotification(data);
        }
        EntityManager entityManager = context.getContext();
        entityManager.persist(log);
    }

    @Override
    public void storeUserTaskInstanceDataEvent(DataAuditContext context, UserTaskInstanceStateDataEvent event) {
        UserTaskInstanceStateLog log = new UserTaskInstanceStateLog();

        setUserTaskCommonAttributes(log, event);
        log.setUserTaskDefinitionId(event.getData().getUserTaskDefinitionId());
        log.setActualUser(event.getData().getActualOwner());
        log.setName(event.getData().getUserTaskName());
        log.setDescription(event.getData().getUserTaskDescription());
        log.setState(event.getData().getState());
        log.setEventType(event.getData().getEventType());
        log.setEventUser(event.getData().getEventUser());
        log.setProcessId(event.getKogitoProcessId());
        log.setProcessVersion(event.getKogitoProcessVersion());
        log.setRootProcessId(getOnlyIfFilled(event::getKogitoRootProcessId));
        log.setRootProcessVersion(getOnlyIfFilled(event::getKogitoRootProcessVersion));

        EntityManager entityManager = context.getContext();
        entityManager.persist(log);

    }

    @Override
    public void storeUserTaskInstanceDataEvent(DataAuditContext context, UserTaskInstanceVariableDataEvent event) {
        UserTaskInstanceVariableLog log = new UserTaskInstanceVariableLog();

        setUserTaskCommonAttributes(log, event);

        log.setEventUser(event.getData().getEventUser());

        log.setUserTaskDefinitionId(event.getData().getUserTaskDefinitionId());
        log.setVariableId(event.getData().getVariableId());
        log.setVariableName(event.getData().getVariableName());
        log.setVariableValue(toJsonString(event.getData().getVariableValue()));

        switch (event.getData().getVariableType()) {
            case "INPUT":
                log.setVariableType(VariableType.INPUT);
                break;
            case "OUTPUT":
                log.setVariableType(VariableType.OUTPUT);
                break;
        }
        EntityManager entityManager = context.getContext();
        entityManager.persist(log);
    }

    private void setUserTaskCommonAttributes(AbstractUserTaskInstanceLog log, UserTaskInstanceDataEvent<?> event) {
        log.setEventId(event.getId());
        log.setEventDate(event.getTime() != null ? Date.from(event.getTime().toInstant()) : Date.from(Instant.now()));
        log.setProcessInstanceId(event.getKogitoProcessInstanceId());
        log.setBusinessKey(event.getKogitoBusinessKey());
        log.setUserTaskInstanceId(event.getKogitoUserTaskInstanceId());
    }

    @Override
    public void storeJobDataEvent(DataAuditContext context, JobInstanceDataEvent jobDataEvent) {

        ScheduledJob job = toObject(ScheduledJob.class, jobDataEvent.getData());

        JobExecutionLog log = new JobExecutionLog();
        log.setJobId(job.getId());
        if (job.getExpirationTime() != null) {
            log.setExpirationTime(Timestamp.from(job.getExpirationTime().toInstant()));
        }
        log.setPriority(job.getPriority());
        log.setProcessInstanceId(job.getProcessInstanceId());
        log.setNodeInstanceId(job.getNodeInstanceId());
        log.setRepeatInterval(job.getRepeatInterval());
        log.setRepeatLimit(job.getRepeatLimit());
        log.setScheduledId(job.getScheduledId());
        log.setRetries(job.getRetries());
        log.setProcessId(jobDataEvent.getKogitoProcessId());
        log.setProcessVersion(jobDataEvent.getKogitoProcessVersion());
        log.setRootProcessId(getOnlyIfFilled(jobDataEvent::getKogitoRootProcessId));
        log.setRootProcessVersion(getOnlyIfFilled(jobDataEvent::getKogitoRootProcessVersion));

        if (job.getStatus() != null) {
            log.setStatus(job.getStatus().name());
        }

        log.setExecutionCounter(job.getExecutionCounter());
        log.setEventDate(Timestamp.from(Instant.now()));
        log.setExceptionMessage(job.getExceptionMessage());
        log.setExceptionDetails(job.getExceptionDetails());

        EntityManager entityManager = context.getContext();
        entityManager.persist(log);
    }

    private <T> T toObject(Class<T> clazz, byte[] bytes) {
        try {
            return clazz.cast(mapper.readValue(bytes, clazz));
        } catch (IOException e) {
            LOGGER.error("could not convert to json string {}", new String(bytes), e);
            return null;
        }
    }

    private String toJsonString(Object data) {
        try {
            if (data == null) {
                return null;
            }

            return mapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            LOGGER.error("could not convert to json string {}", data, e);
            return null;
        }
    }

    @Override
    public void storeQuery(DataAuditContext context, DataAuditQuery dataAuditQuery) {
        EntityManager entityManager = context.getContext();
        AuditQuery auditQuery = new AuditQuery();
        auditQuery.setIdentifier(dataAuditQuery.getIdentifier());
        auditQuery.setGraphQLDefinition(dataAuditQuery.getGraphQLDefinition());
        auditQuery.setQuery(dataAuditQuery.getQuery());
        entityManager.merge(auditQuery);
    }

    @Override
    public List<DataAuditQuery> findQueries(DataAuditContext context) {
        EntityManager entityManager = context.getContext();
        List<AuditQuery> queries = entityManager.createQuery("SELECT o FROM AuditQuery o", AuditQuery.class).getResultList();
        return queries.stream().map(this::to).collect(Collectors.toList());
    }

    private DataAuditQuery to(AuditQuery auditQuery) {
        DataAuditQuery dataAuditQuery = new DataAuditQuery();
        dataAuditQuery.setIdentifier(auditQuery.getIdentifier());
        dataAuditQuery.setGraphQLDefinition(auditQuery.getGraphQLDefinition());
        dataAuditQuery.setQuery(auditQuery.getQuery());
        return dataAuditQuery;
    }
}
