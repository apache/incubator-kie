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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.*;

@Entity
@NamedNativeQuery(
        name = UserTaskInstanceEntity.DELETE_BY_ID,
        query = "delete from jbpm_user_tasks where id = :taskId")
@Table(name = "jbpm_user_tasks")
public class UserTaskInstanceEntity {
    public static final String DELETE_BY_ID = "UserTaskInstanceEntity.DeleteById";

    // Base query for finding instances by identity
    public static final String BASE_IDENTITY_QUERY =
            "select userTask from UserTaskInstanceEntity userTask " +
                    "left join userTask.adminGroups adminGroup " +
                    "left join userTask.potentialGroups potentialGroup " +
                    "where (:userId member of userTask.adminUsers " +
                    "or adminGroup in (:roles) " +
                    "or userTask.actualOwner = :userId " +
                    "or (userTask.actualOwner is null " + // checking if task is not reserved, we cannot check by status since lifecycle can be customized
                    "and :userId not member of userTask.excludedUsers " +
                    "and (:userId member of userTask.potentialUsers or potentialGroup in (:roles))))";

    public static final String PROCESS_ID_FILTER_CLAUSE = " and userTask.processInfo.processId = :processId";
    public static final String PROCESS_INSTANCE_ID_FILTER_CLAUSE = " and userTask.processInfo.processInstanceId = :processInstanceId";
    public static final String TASKNAME_FILTER_CLAUSE = " and userTask.taskName = :taskName";
    public static final String STATUS_FILTER_CLAUSE = " and userTask.status in (:statusFilter)";

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "user_task_id")
    private String userTaskId;

    @Column(name = "task_name")
    private String taskName;

    @Column(name = "task_description")
    private String taskDescription;

    @Column(name = "task_priority")
    private String taskPriority;

    @Column(name = "status")
    private String status;

    @Column(name = "termination_type")
    private String terminationType;

    @Column(name = "actual_owner")
    private String actualOwner;

    @Column(name = "external_reference_id")
    private String externalReferenceId;

    @Embedded
    private TaskProcessInfoEntity processInfo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "jbpm_user_tasks_potential_users", joinColumns = @JoinColumn(name = "task_id", foreignKey = @ForeignKey(name = "fk_jbpm_user_tasks_potential_users_tid")))
    @Column(name = "user_id", nullable = false)
    private Set<String> potentialUsers = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "jbpm_user_tasks_potential_groups", joinColumns = @JoinColumn(name = "task_id"),
            foreignKey = @ForeignKey(name = "fk_jbpm_user_tasks_potential_groups_tid"))
    @Column(name = "group_id")
    private Set<String> potentialGroups = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "jbpm_user_tasks_admin_users", joinColumns = @JoinColumn(name = "task_id", foreignKey = @ForeignKey(name = "fk_jbpm_user_tasks_admin_users_tid")))
    @Column(name = "user_id", nullable = false)
    private Set<String> adminUsers = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "jbpm_user_tasks_admin_groups", joinColumns = @JoinColumn(name = "task_id"),
            foreignKey = @ForeignKey(name = "fk_jbpm_user_tasks_admin_groups_tid"))
    @Column(name = "group_id")
    private Set<String> adminGroups = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "jbpm_user_tasks_excluded_users", joinColumns = @JoinColumn(name = "task_id", foreignKey = @ForeignKey(name = "fk_jbpm_user_tasks_excluded_users_tid")))
    @Column(name = "user_id", nullable = false)
    private Set<String> excludedUsers = new HashSet<>();

    @OneToMany(mappedBy = "taskInstance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AttachmentEntity> attachments = new ArrayList<>();

    @OneToMany(mappedBy = "taskInstance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CommentEntity> comments = new ArrayList<>();

    @OneToMany(mappedBy = "taskInstance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaskInputEntity> inputs = new ArrayList<>();

    @OneToMany(mappedBy = "taskInstance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaskOutputEntity> outputs = new ArrayList<>();

    @OneToMany(mappedBy = "taskInstance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaskMetadataEntity> metadata = new ArrayList<>();

    @OneToMany(mappedBy = "taskInstance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaskDeadlineEntity> deadlines;

    @OneToMany(mappedBy = "taskInstance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaskDeadlineTimerEntity> deadlineTimers;

    @OneToMany(mappedBy = "taskInstance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaskReassignmentEntity> reassignments;

    @OneToMany(mappedBy = "taskInstance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaskReassignmentTimerEntity> reassignmentTimers;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public TaskProcessInfoEntity getProcessInfo() {
        return processInfo;
    }

    public void setProcessInfo(TaskProcessInfoEntity taskProcessInfoEntity) {
        this.processInfo = taskProcessInfoEntity;
    }

    public String getActualOwner() {
        return actualOwner;
    }

    public void setActualOwner(String actualOwner) {
        this.actualOwner = actualOwner;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    public String getTaskPriority() {
        return taskPriority;
    }

    public void setTaskPriority(String taskPriority) {
        this.taskPriority = taskPriority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExternalReferenceId() {
        return externalReferenceId;
    }

    public void setExternalReferenceId(String externalReferenceId) {
        this.externalReferenceId = externalReferenceId;
    }

    public void setPotentialUsers(Set<String> potentialUsers) {
        this.potentialUsers.clear();
        this.potentialUsers.addAll(potentialUsers);
    }

    public Set<String> getPotentialUsers() {
        return potentialUsers;
    }

    public Set<String> getPotentialGroups() {
        return potentialGroups;
    }

    public void setPotentialGroups(Set<String> potentialGroups) {
        this.potentialGroups.clear();
        this.potentialGroups.addAll(potentialGroups);
    }

    public Set<String> getAdminUsers() {
        return adminUsers;
    }

    public void setAdminUsers(Set<String> adminUsers) {
        this.adminUsers.clear();
        this.adminUsers.addAll(adminUsers);
    }

    public Collection<String> getAdminGroups() {
        return adminGroups;
    }

    public void setAdminGroups(Set<String> adminGroups) {
        this.adminGroups.clear();
        this.adminGroups.addAll(adminGroups);
    }

    public Collection<String> getExcludedUsers() {
        return excludedUsers;
    }

    public void setExcludedUsers(Set<String> excludedUsers) {
        this.excludedUsers.clear();
        this.excludedUsers.addAll(excludedUsers);
    }

    public void clearAttachments() {
        this.attachments.clear();
    }

    public Collection<AttachmentEntity> getAttachments() {
        return attachments;
    }

    public void addAttachment(AttachmentEntity attachment) {
        attachment.setTaskInstance(this);
        this.attachments.add(attachment);
    }

    public void removeAttachment(AttachmentEntity attachmentEntity) {
        this.attachments.remove(attachmentEntity);
    }

    public void setAttachments(Collection<AttachmentEntity> attachments) {
        this.clearAttachments();
        this.attachments.addAll(attachments);
    }

    public void clearComments() {
        this.comments.clear();
    }

    public void removeComment(CommentEntity comment) {
        this.comments.remove(comment);
    }

    public Collection<CommentEntity> getComments() {
        return comments;
    }

    public void addComment(CommentEntity comment) {
        comment.setTaskInstance(this);
        this.comments.add(comment);
    }

    public void setComments(Collection<CommentEntity> comments) {
        this.clearComments();
        this.comments.addAll(comments);
    }

    public void clearInputs() {
        this.inputs.clear();
    }

    public Collection<TaskInputEntity> getInputs() {
        return inputs;
    }

    public void setInputs(Collection<TaskInputEntity> inputs) {
        this.clearInputs();
        this.inputs.addAll(inputs);
    }

    public void addInput(TaskInputEntity input) {
        input.setTaskInstance(this);
        this.inputs.add(input);
    }

    public void removeInput(TaskInputEntity input) {
        this.inputs.remove(input);
    }

    public void clearOutputs() {
        this.outputs.clear();
    }

    public Collection<TaskOutputEntity> getOutputs() {
        return outputs;
    }

    public void addOutput(TaskOutputEntity output) {
        output.setTaskInstance(this);
        this.outputs.add(output);
    }

    public void removeOutput(TaskOutputEntity output) {
        this.outputs.remove(output);
    }

    public void setOutputs(Collection<TaskOutputEntity> outputs) {
        this.clearOutputs();
        this.outputs.addAll(outputs);
    }

    public void clearMetadata() {
        this.metadata.clear();
    }

    public Collection<TaskMetadataEntity> getMetadata() {
        return metadata;
    }

    public void addMetadata(TaskMetadataEntity metadata) {
        metadata.setTaskInstance(this);
        this.metadata.add(metadata);
    }

    public void removeMetadata(TaskMetadataEntity metadata) {
        this.metadata.remove(metadata);
    }

    public void setMetadata(Collection<TaskMetadataEntity> metadata) {
        this.clearMetadata();
        this.metadata.addAll(metadata);
    }

    public void setUserTaskId(String userTaskId) {
        this.userTaskId = userTaskId;
    }

    public String getUserTaskId() {
        return userTaskId;
    }

    public void setTerminationType(String terminationType) {
        this.terminationType = terminationType;
    }

    public String getTerminationType() {
        return terminationType;
    }

    public List<TaskDeadlineEntity> getDeadlines() {
        return deadlines;
    }

    public void setDeadlines(List<TaskDeadlineEntity> deadlines) {
        this.deadlines = deadlines;
    }

    public List<TaskReassignmentEntity> getReassignments() {
        return reassignments;
    }

    public void setReassignments(List<TaskReassignmentEntity> reassignments) {
        this.reassignments = reassignments;
    }

    public List<TaskDeadlineTimerEntity> getDeadlineTimers() {
        return deadlineTimers;
    }

    public void setDeadlineTimers(List<TaskDeadlineTimerEntity> deadlineTimers) {
        this.deadlineTimers = deadlineTimers;
    }

    public List<TaskReassignmentTimerEntity> getReassignmentTimers() {
        return reassignmentTimers;
    }

    public void setReassignmentTimers(List<TaskReassignmentTimerEntity> reassignmentTimers) {
        this.reassignmentTimers = reassignmentTimers;
    }

    @Override
    public int hashCode() {
        return Objects.hash(actualOwner, adminGroups, adminUsers, attachments, comments, deadlineTimers, deadlines, excludedUsers, externalReferenceId, id, inputs, metadata, outputs, potentialGroups,
                potentialUsers, reassignmentTimers, reassignments, status, taskDescription, taskName, taskPriority, terminationType, userTaskId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        UserTaskInstanceEntity other = (UserTaskInstanceEntity) obj;
        return Objects.equals(actualOwner, other.actualOwner) && Objects.equals(adminGroups, other.adminGroups) && Objects.equals(adminUsers, other.adminUsers)
                && Objects.equals(attachments, other.attachments) && Objects.equals(comments, other.comments) && Objects.equals(deadlineTimers, other.deadlineTimers)
                && Objects.equals(deadlines, other.deadlines) && Objects.equals(excludedUsers, other.excludedUsers) && Objects.equals(externalReferenceId, other.externalReferenceId)
                && Objects.equals(id, other.id) && Objects.equals(inputs, other.inputs) && Objects.equals(metadata, other.metadata) && Objects.equals(outputs, other.outputs)
                && Objects.equals(potentialGroups, other.potentialGroups) && Objects.equals(potentialUsers, other.potentialUsers) && Objects.equals(reassignmentTimers, other.reassignmentTimers)
                && Objects.equals(reassignments, other.reassignments) && Objects.equals(status, other.status) && Objects.equals(taskDescription, other.taskDescription)
                && Objects.equals(taskName, other.taskName) && Objects.equals(taskPriority, other.taskPriority) && Objects.equals(terminationType, other.terminationType)
                && Objects.equals(userTaskId, other.userTaskId);
    }

}
