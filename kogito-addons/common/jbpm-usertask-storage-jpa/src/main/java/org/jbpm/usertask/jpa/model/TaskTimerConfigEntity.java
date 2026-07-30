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

package org.jbpm.usertask.jpa.model;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class TaskTimerConfigEntity<T> extends AbstractTaskEntity<T> {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    protected Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "task_id")
    protected UserTaskInstanceEntity taskInstance;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public UserTaskInstanceEntity getTaskInstance() {
        return taskInstance;
    }

    public void setTaskInstance(UserTaskInstanceEntity taskInstance) {
        this.taskInstance = taskInstance;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, javaType, id, taskInstance.getId());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        TaskTimerConfigEntity other = (TaskTimerConfigEntity) obj;
        return super.equals(obj) && Objects.equals(id, other.id) && Objects.equals(taskInstance.getId(), other.taskInstance.getId());
    }

}
