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
package org.kie.kogito.event.impl.adapter;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.kie.api.event.process.ProcessEvent;
import org.kie.api.event.process.ProcessNodeEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.kogito.event.process.ProcessInstanceNodeDataEvent;
import org.kie.kogito.event.process.ProcessInstanceNodeEventBody;
import org.kie.kogito.event.process.ProcessInstanceStateDataEvent;
import org.kie.kogito.event.process.ProcessInstanceStateEventBody;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoWorkItemNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcessInstance;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.WorkItemRecordParameters;

public abstract class AbstractDataEventAdapter implements DataEventAdapter {

    private DataEventAdapterConfig config;

    private Class<?> type;

    public AbstractDataEventAdapter(Class<?> type) {
        this.type = type;
    }

    @Override
    public void setup(DataEventAdapterConfig config) {
        this.config = config;
    }

    public DataEventAdapterConfig getConfig() {
        return config;
    }

    @Override
    public Class<?> type() {
        return type;
    }

    protected ProcessInstanceStateDataEvent adapt(ProcessEvent event, Integer eventType) {
        return adapt(event, eventType, event.getEventDate());
    }

    protected ProcessInstanceStateDataEvent adapt(ProcessEvent event, Integer eventType, Date date) {
        Map<String, Object> metadata = AdapterHelper.buildProcessMetadata((KogitoWorkflowProcessInstance) event.getProcessInstance());

        KogitoWorkflowProcessInstance pi = (KogitoWorkflowProcessInstance) event.getProcessInstance();

        ProcessInstanceStateEventBody.Builder builder = ProcessInstanceStateEventBody.create()
                .eventDate(date)
                .eventUser(event.getEventIdentity())
                .eventType(eventType)
                .processId(pi.getProcessId())
                .processVersion(pi.getProcessVersion())
                .processInstanceId(pi.getId())
                .processName(pi.getProcessName())
                .processVersion(pi.getProcessVersion())
                .processType(pi.getProcess().getType())
                .parentInstanceId(pi.getParentProcessInstanceId())
                .rootProcessId(pi.getRootProcessId())
                .rootProcessVersion(pi.getRootProcessVersion())
                .rootProcessInstanceId(pi.getRootProcessInstanceId())
                .businessKey(pi.getBusinessKey())
                .slaDueDate(pi.getSlaDueDate());
        Map<String, List<String>> headers = pi.getHeaders();
        if (headers != null) {
            String id = getHeader(headers, "id");
            if (id != null) {
                builder.cloudEvent(id, getHeader(headers, "source"));
            }
        }

        // this is due to the listener order execution. we cannot trust the state of the process if it start and finishes at the same time.
        if (eventType == ProcessInstanceStateEventBody.EVENT_TYPE_STARTED) {
            builder.state(KogitoProcessInstance.STATE_ACTIVE);
        } else {
            builder.state(pi.getState());
        }

        String securityRoles = (String) event.getProcessInstance().getProcess().getMetaData().get("securityRoles");
        if (securityRoles != null) {
            builder.roles(securityRoles.split(","));
        }

        ProcessInstanceStateEventBody body = builder.build();
        return ProcessInstanceStateDataEvent.builder()
                .source(AdapterHelper.buildSource(getConfig().service(), event.getProcessInstance().getProcessId()))
                .kogitoAddons(getConfig().addons().toString())
                .kogitoIdentity(event.getEventIdentity())
                .metaData(metadata)
                .data(body)
                .kogitoBusinessKey(pi.getBusinessKey())
                .build();
    }

    /**
     * Node events are adapted after the node logic has run (e.g. after a synchronous work item completes),
     * so the adaptation time is not the time the node was actually entered or left.
     * Use the times recorded by the node instance when available.
     */
    private static Date nodeEventDate(KogitoNodeInstance nodeInstance, int eventType) {
        Date date = null;
        if (eventType == ProcessInstanceNodeEventBody.EVENT_TYPE_ENTER) {
            date = nodeInstance.getTriggerTime();
        } else if (eventType != ProcessInstanceNodeEventBody.EVENT_TYPE_UPDATED) {
            date = nodeInstance.getLeaveTime();
        }
        return date != null ? date : new Date();
    }

    private String getHeader(Map<String, List<String>> headers, String key) {
        List<String> value = headers.get(key);
        return value != null ? value.get(0).toString() : null;
    }

    protected ProcessInstanceNodeDataEvent toProcessInstanceNodeEvent(ProcessNodeTriggeredEvent event, int eventType) {
        return toProcessInstanceNodeEvent(event, eventType, (k, v) -> k.setRetrigger(v.isRetrigger()));
    }

    protected ProcessInstanceNodeDataEvent toProcessInstanceNodeEvent(ProcessNodeEvent event, int eventType) {
        return toProcessInstanceNodeEvent(event, eventType, (k, v) -> {
        });
    }

    protected <T extends ProcessNodeEvent> ProcessInstanceNodeDataEvent toProcessInstanceNodeEvent(T event, int eventType, BiConsumer<ProcessInstanceNodeEventBody.Builder, T> consumer) {
        Map<String, Object> metadata = AdapterHelper.buildProcessMetadata((KogitoWorkflowProcessInstance) event.getProcessInstance());
        KogitoWorkflowProcessInstance pi = (KogitoWorkflowProcessInstance) event.getProcessInstance();
        KogitoNodeInstance nodeInstance = (KogitoNodeInstance) event.getNodeInstance();
        Date eventDate = nodeEventDate(nodeInstance, eventType);
        ProcessInstanceNodeEventBody.Builder builder = ProcessInstanceNodeEventBody.create()
                .eventDate(eventDate)
                .eventUser(event.getEventIdentity())
                .eventType(eventType)
                .processId(pi.getProcessId())
                .processVersion(pi.getProcessVersion())
                .processInstanceId(pi.getId())
                .nodeName(nodeInstance.getNodeName())
                .nodeType(nodeInstance.getNode().getClass().getSimpleName())
                .nodeInstanceId(nodeInstance.getId())
                .nodeDefinitionId(nodeInstance.getNode().getUniqueId())
                .slaDueDate(nodeInstance.getSlaDueDate())
                .inputParameters(WorkItemRecordParameters.getInputParameters(nodeInstance))
                .outputParameters(WorkItemRecordParameters.getOutputParameters(nodeInstance));
        consumer.accept(builder, event);
        if (nodeInstance instanceof KogitoWorkItemNodeInstance workItemNodeInstance && workItemNodeInstance.getWorkItem() != null) {
            KogitoWorkItem workItem = workItemNodeInstance.getWorkItem();
            builder.workItemId(workItem.getStringId());
            builder.data("WorkItemId", workItem.getStringId());
            builder.data("WorkItemExternalReferenceId", workItem.getExternalReferenceId());
        }

        if (eventType == ProcessInstanceNodeEventBody.EVENT_TYPE_ENTER) {
            builder.connectionNodeDefinitionId((String) nodeInstance.getMetaData().get("IncomingConnection"));
        } else {
            builder.connectionNodeDefinitionId((String) nodeInstance.getMetaData().get("OutgoingConnection"));
        }

        ProcessInstanceNodeEventBody body = builder.build();

        return ProcessInstanceNodeDataEvent.builder()
                .time(eventDate.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime())
                .source(AdapterHelper.buildSource(getConfig().service(), pi.getProcessId()))
                .kogitoAddons(getConfig().addons().toString())
                .kogitoIdentity(event.getEventIdentity())
                .metaData(metadata)
                .data(body)
                .kogitoBusinessKey(pi.getBusinessKey())
                .build();
    }
}
