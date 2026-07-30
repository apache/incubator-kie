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

package org.kie.kogito.app.audit.jpa.model;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;

import static org.kie.kogito.app.audit.jpa.model.ModelConstants.BUSINESS_KEY_LOG_LENGTH;

@MappedSuperclass
public abstract class AbstractProcessInstanceLog {

    @Transient
    private static final Logger logger = LoggerFactory.getLogger(AbstractProcessInstanceLog.class);

    @Column(name = "event_id")
    private String eventId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "event_date")
    private Date eventDate;

    @Column(name = "event_user")
    private String eventUser;

    @Column(name = "process_type")
    private String processType;

    @Column(name = "process_id")
    private String processId;

    @Column(name = "process_version")
    private String processVersion;

    @Column(name = "parent_process_instance_id")
    private String parentProcessInstanceId;

    @Column(name = "root_process_id")
    private String rootProcessId;

    @Column(name = "root_process_instance_id")
    private String rootProcessInstanceId;

    @Column(name = "process_instance_id")
    private String processInstanceId;

    @Column(name = "business_key")
    private String businessKey;

    public String getRootProcessId() {
        return rootProcessId;
    }

    public void setRootProcessId(String rootProcessId) {
        this.rootProcessId = rootProcessId;
    }

    public String getRootProcessInstanceId() {
        return rootProcessInstanceId;
    }

    public void setRootProcessInstanceId(String rootProcessInstanceId) {
        this.rootProcessInstanceId = rootProcessInstanceId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getProcessType() {
        return processType;
    }

    public void setProcessType(String processType) {
        this.processType = processType;
    }

    public String getProcessVersion() {
        return processVersion;
    }

    public void setProcessVersion(String processVersion) {
        this.processVersion = processVersion;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getParentProcessInstanceId() {
        return parentProcessInstanceId;
    }

    public void setParentProcessInstanceId(String parentProcessInstanceId) {
        this.parentProcessInstanceId = parentProcessInstanceId;
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
        String trimmedBusinesskey = null;
        if (businessKey != null && businessKey.length() > BUSINESS_KEY_LOG_LENGTH) {
            trimmedBusinesskey = businessKey.substring(0, BUSINESS_KEY_LOG_LENGTH);
            logger.warn("Business Key content was trimmed as it was too long (more than {} characters)", BUSINESS_KEY_LOG_LENGTH);
        } else {
            trimmedBusinesskey = businessKey;
        }
        this.businessKey = trimmedBusinesskey;
    }

    public Date getEventDate() {
        return eventDate;
    }

    public void setEventDate(Date eventDate) {
        this.eventDate = eventDate;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventUser() {
        return eventUser;
    }

    public void setEventUser(String eventUser) {
        this.eventUser = eventUser;
    }
}
