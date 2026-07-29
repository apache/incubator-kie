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
package org.kie.kogito.app.audit.graphql.type;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;

public class ProcessInstanceNodeTO {
    private String eventId;

    private OffsetDateTime eventDate;

    private String processType;

    private String processId;

    private String processVersion;

    private String parentProcessInstanceId;

    private String rootProcessId;

    private String rootProcessVersion;

    private String rootProcessInstanceId;

    private String processInstanceId;

    private String businessKey;

    private String eventType;

    private String nodeType;

    private String nodeName;

    private String nodeInstanceId;

    private String connection;

    private String workItemId;

    private OffsetDateTime slaDueDate;

    private Object eventData;

    private String eventUser;

    private Object inputArgs;

    private Object outputArgs;

    public ProcessInstanceNodeTO(String eventId, Date eventDate, String processType, String processId,
            String processVersion, String parentProcessInstanceId, String rootProcessId, String rootProcessVersion, String rootProcessInstanceId, String processInstanceId,
            String businessKey, String eventType, String nodeType,
            String nodeName, String nodeInstanceId, String connection, String workItemId, Date slaDueDate, String eventData, String eventUser,
            String inputArgs, String outputArgs) {
        this.eventId = eventId;
        this.eventDate = OffsetDateTime.ofInstant(eventDate.toInstant(), ZoneId.of("UTC"));
        this.processType = processType;
        this.processId = processId;
        this.processVersion = processVersion;
        this.parentProcessInstanceId = parentProcessInstanceId;
        this.rootProcessId = rootProcessId;
        this.rootProcessVersion = rootProcessVersion;
        this.rootProcessInstanceId = rootProcessInstanceId;
        this.processInstanceId = processInstanceId;
        this.businessKey = businessKey;
        this.eventType = eventType;
        this.nodeType = nodeType;
        this.nodeName = nodeName;
        this.nodeInstanceId = nodeInstanceId;
        this.connection = connection;
        this.workItemId = workItemId;
        if (slaDueDate != null) {
            this.slaDueDate = OffsetDateTime.ofInstant(slaDueDate.toInstant(), ZoneId.of("UTC"));
        }
        this.eventData = JsonUtil.toJsonNode(eventData);
        this.eventUser = eventUser;
        this.inputArgs = JsonUtil.toJsonNode(inputArgs);
        this.outputArgs = JsonUtil.toJsonNode(outputArgs);
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public OffsetDateTime getEventDate() {
        return eventDate;
    }

    public void setEventDate(OffsetDateTime eventDate) {
        this.eventDate = eventDate;
    }

    public String getProcessType() {
        return processType;
    }

    public void setProcessType(String processType) {
        this.processType = processType;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getProcessVersion() {
        return processVersion;
    }

    public void setProcessVersion(String processVersion) {
        this.processVersion = processVersion;
    }

    public String getParentProcessInstanceId() {
        return parentProcessInstanceId;
    }

    public void setParentProcessInstanceId(String parentProcessInstanceId) {
        this.parentProcessInstanceId = parentProcessInstanceId;
    }

    public String getRootProcessId() {
        return rootProcessId;
    }

    public void setRootProcessId(String rootProcessId) {
        this.rootProcessId = rootProcessId;
    }

    public String getRootProcessVersion() {
        return rootProcessVersion;
    }

    public void setRootProcessVersion(String rootProcessVersion) {
        this.rootProcessVersion = rootProcessVersion;
    }

    public String getRootProcessInstanceId() {
        return rootProcessInstanceId;
    }

    public void setRootProcessInstanceId(String rootProcessInstanceId) {
        this.rootProcessInstanceId = rootProcessInstanceId;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getNodeInstanceId() {
        return nodeInstanceId;
    }

    public void setNodeInstanceId(String nodeInstanceId) {
        this.nodeInstanceId = nodeInstanceId;
    }

    public String getConnection() {
        return connection;
    }

    public void setConnection(String connection) {
        this.connection = connection;
    }

    public String getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(String workItemId) {
        this.workItemId = workItemId;
    }

    public OffsetDateTime getSlaDueDate() {
        return slaDueDate;
    }

    public void setSlaDueDate(OffsetDateTime slaDueDate) {
        this.slaDueDate = slaDueDate;
    }

    public Object getEventData() {
        return eventData;
    }

    public void setEventData(Object eventData) {
        this.eventData = eventData;
    }

    public String getEventUser() {
        return eventUser;
    }

    public void setEventUser(String eventUser) {
        this.eventUser = eventUser;
    }

    public Object getInputArgs() {
        return inputArgs;
    }

    public void setInputArgs(Object inputArgs) {
        this.inputArgs = inputArgs;
    }

    public Object getOutputArgs() {
        return outputArgs;
    }

    public void setOutputArgs(Object outputArgs) {
        this.outputArgs = outputArgs;
    }
}
