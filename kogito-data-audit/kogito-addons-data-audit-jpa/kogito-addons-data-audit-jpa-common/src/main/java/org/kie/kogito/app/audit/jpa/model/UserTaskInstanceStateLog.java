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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "Task_Instance_State_Log",
        indexes = {
                @Index(name = "ix_utsl_utid", columnList = "user_task_instance_id"),
                @Index(name = "ix_utsl_state", columnList = "state"),
                @Index(name = "ix_utsl_pid", columnList = "process_instance_id"),
                @Index(name = "ix_utsl_key", columnList = "business_key"),
                @Index(name = "ix_utsl_event_date", columnList = "event_date"),
                @Index(name = "ix_utsl_proc_id", columnList = "process_id"),
                @Index(name = "ix_utsl_pid_pver", columnList = "process_id, process_version"),
                @Index(name = "ix_utsl_rpid_rpver", columnList = "root_process_id, root_process_version")
        })
@SequenceGenerator(name = "taskInstanceStateLogIdSeq", sequenceName = "TASK_INSTANCE_STATE_LOG_ID_SEQ")
public class UserTaskInstanceStateLog extends AbstractUserTaskInstanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "taskInstanceStateLogIdSeq")
    @Column(name = "id")
    private Long id;

    @Column(name = "process_id")
    private String processId;

    @Column(name = "process_version")
    private String processVersion;

    @Column(name = "root_process_id")
    private String rootProcessId;

    @Column(name = "root_process_version")
    private String rootProcessVersion;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "actual_user")
    private String actualUser;

    @Column(name = "state")
    private String state;

    @Column(name = "event_type")
    private String eventType;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getActualUser() {
        return actualUser;
    }

    public void setActualUser(String actualUser) {
        this.actualUser = actualUser;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
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

}
