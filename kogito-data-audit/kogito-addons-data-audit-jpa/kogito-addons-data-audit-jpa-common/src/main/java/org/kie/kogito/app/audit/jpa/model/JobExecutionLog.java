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

import java.sql.Timestamp;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "Job_Execution_Log",
        indexes = {
                @Index(name = "ix_jel_pid", columnList = "process_instance_id"),
                @Index(name = "ix_jel_jid", columnList = "job_id"),
                @Index(name = "ix_jel_status", columnList = "status")
        })
@SequenceGenerator(name = "jobExecutionHistoryIdSeq", sequenceName = "JOB_EXECUTION_HISTORY_ID_SEQ")
public class JobExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "jobExecutionHistoryIdSeq")
    @Column(name = "id")
    private Long id;

    @Column(name = "job_id")
    private String jobId;

    @Column(name = "expiration_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date expirationTime;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "process_instance_id")
    private String processInstanceId;

    @Column(name = "node_instance_id")
    private String nodeInstanceId;

    @Column(name = "repeat_interval")
    private Long repeatInterval;

    @Column(name = "repeat_limit")
    private Integer repeatLimit;

    @Column(name = "scheduled_id")
    private String scheduledId;

    @Column(name = "retries")
    private Integer retries;

    @Column(name = "status")
    private String status;

    @Column(name = "execution_counter")
    private Integer executionCounter;

    @Column(name = "event_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date eventDate;

    @Column(name = "process_id")
    private String processId;

    @Column(name = "process_version")
    private String processVersion;

    @Column(name = "root_process_id")
    private String rootProcessId;

    @Column(name = "root_process_version")
    private String rootProcessVersion;

    @Column(name = "exception_message")
    private String exceptionMessage;

    @Column(name = "exception_details", columnDefinition = "TEXT")
    private String exceptionDetails;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public Date getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(Timestamp expirationTime) {
        this.expirationTime = expirationTime;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public String getNodeInstanceId() {
        return nodeInstanceId;
    }

    public void setNodeInstanceId(String nodeInstanceId) {
        this.nodeInstanceId = nodeInstanceId;
    }

    public Long getRepeatInterval() {
        return repeatInterval;
    }

    public void setRepeatInterval(Long repeatInterval) {
        this.repeatInterval = repeatInterval;
    }

    public Integer getRepeatLimit() {
        return repeatLimit;
    }

    public void setRepeatLimit(Integer repeatLimit) {
        this.repeatLimit = repeatLimit;
    }

    public String getScheduledId() {
        return scheduledId;
    }

    public void setScheduledId(String scheduledId) {
        this.scheduledId = scheduledId;
    }

    public Integer getRetries() {
        return retries;
    }

    public void setRetries(Integer retries) {
        this.retries = retries;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getExecutionCounter() {
        return executionCounter;
    }

    public void setExecutionCounter(Integer executionCounter) {
        this.executionCounter = executionCounter;
    }

    public Date getEventDate() {
        return eventDate;
    }

    public void setEventDate(Timestamp eventDate) {
        this.eventDate = eventDate;
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

    public String getExceptionDetails() {
        return exceptionDetails;
    }

    public void setExceptionDetails(String exceptionDetails) {
        this.exceptionDetails = exceptionDetails;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }
}