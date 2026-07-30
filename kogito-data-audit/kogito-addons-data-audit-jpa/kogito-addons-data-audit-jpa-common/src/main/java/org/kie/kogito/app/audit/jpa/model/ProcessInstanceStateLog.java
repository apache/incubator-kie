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
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "Process_Instance_State_Log",
        indexes = {
                @Index(name = "ix_pisl_pid", columnList = "process_instance_id"),
                @Index(name = "ix_pisl_state", columnList = "state"),
                @Index(name = "ix_pisl_key", columnList = "business_key"),
                @Index(name = "ix_pisl_event_date", columnList = "event_date"),
                @Index(name = "ix_pisl_pid_pver", columnList = "process_id, process_version"),
                @Index(name = "ix_pisl_rpid_rpver", columnList = "root_process_id, root_process_version")
        })
@SequenceGenerator(name = "processInstanceStateLogIdSeq", sequenceName = "PROCESS_INSTANCE_STATE_LOG_ID_SEQ")
public class ProcessInstanceStateLog extends AbstractProcessInstanceLog {

    @Column(name = "root_process_version")
    private String rootProcessVersion;

    public String getRootProcessVersion() {
        return rootProcessVersion;
    }

    public void setRootProcessVersion(String rootProcessVersion) {
        this.rootProcessVersion = rootProcessVersion;
    }

    public enum ProcessStateLogType {
        ACTIVE,
        COMPLETED,
        MIGRATED,
        SLA_VIOLATION,
        STATE_UPDATED,
        RETRIGGERED,
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "processInstanceStateLogIdSeq")
    @Column(name = "id")
    private long id;

    @Column(name = "event_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ProcessStateLogType eventType;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "state")
    private String state;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "sla_due_date")
    private Date slaDueDate;

    @ElementCollection
    @CollectionTable(name = "Process_Instance_State_Roles_Log", joinColumns = @JoinColumn(name = "process_instance_state_log_id", foreignKey = @ForeignKey(name = "fk_process_instance_state_pid")))
    @Column(name = "role")
    private Set<String> roles;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public ProcessStateLogType getEventType() {
        return eventType;
    }

    public void setEventType(ProcessStateLogType eventType) {
        this.eventType = eventType;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public Date getSlaDueDate() {
        return slaDueDate;
    }

    public void setSlaDueDate(Date slaDueDate) {
        this.slaDueDate = slaDueDate;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public Set<String> getRoles() {
        return roles;
    }

}