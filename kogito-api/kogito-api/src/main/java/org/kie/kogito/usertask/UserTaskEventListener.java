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
package org.kie.kogito.usertask;

import org.kie.kogito.usertask.events.UserTaskAssignmentEvent;
import org.kie.kogito.usertask.events.UserTaskAttachmentEvent;
import org.kie.kogito.usertask.events.UserTaskCommentEvent;
import org.kie.kogito.usertask.events.UserTaskDeadlineEvent;
import org.kie.kogito.usertask.events.UserTaskStateEvent;
import org.kie.kogito.usertask.events.UserTaskVariableEvent;

/**
 * <b>User Task Event Listener</b> extension point.
 *
 * <p>
 * Listens for human task lifecycle events, including state transitions,
 * assignment changes, input/output variable updates, comments, attachments, and deadline expirations.
 */
public interface UserTaskEventListener {

    /**
     * Invoked when a user task deadline or notification timer triggers.
     *
     * @param event the non-null {@link UserTaskDeadlineEvent}
     */
    default void onUserTaskDeadline(UserTaskDeadlineEvent event) {
        // nothing
    }

    /**
     * Invoked when a user task undergoes a state transition.
     *
     * @param event the non-null {@link UserTaskStateEvent}
     */
    default void onUserTaskState(UserTaskStateEvent event) {
        // nothing
    }

    /**
     * Invoked when a user task assignment or ownership changes.
     *
     * @param event the non-null {@link UserTaskAssignmentEvent}
     */
    default void onUserTaskAssignment(UserTaskAssignmentEvent event) {
        // nothing
    }

    /**
     * Invoked when an input variable is assigned to or modified on a user task.
     *
     * @param event the non-null {@link UserTaskVariableEvent}
     */
    default void onUserTaskInputVariable(UserTaskVariableEvent event) {
        // nothing
    }

    /**
     * Invoked when an output variable is set or modified on a user task.
     *
     * @param event the non-null {@link UserTaskVariableEvent}
     */
    default void onUserTaskOutputVariable(UserTaskVariableEvent event) {
        // nothing
    }

    /**
     * Invoked when an attachment is added to a user task.
     *
     * @param event the non-null {@link UserTaskAttachmentEvent}
     */
    default void onUserTaskAttachmentAdded(UserTaskAttachmentEvent event) {
        // nothing
    }

    /**
     * Invoked when an attachment is removed from a user task.
     *
     * @param event the non-null {@link UserTaskAttachmentEvent}
     */
    default void onUserTaskAttachmentDeleted(UserTaskAttachmentEvent event) {
        // nothing
    }

    /**
     * Invoked when an attachment on a user task is updated.
     *
     * @param event the non-null {@link UserTaskAttachmentEvent}
     */
    default void onUserTaskAttachmentChange(UserTaskAttachmentEvent event) {
        // nothing
    }

    /**
     * Invoked when a comment on a user task is updated.
     *
     * @param event the non-null {@link UserTaskCommentEvent}
     */
    default void onUserTaskCommentChange(UserTaskCommentEvent event) {
        // nothing
    }

    /**
     * Invoked when a comment is added to a user task.
     *
     * @param event the non-null {@link UserTaskCommentEvent}
     */
    default void onUserTaskCommentAdded(UserTaskCommentEvent event) {
        // nothing
    }

    /**
     * Invoked when a comment is deleted from a user task.
     *
     * @param event the non-null {@link UserTaskCommentEvent}
     */
    default void onUserTaskCommentDeleted(UserTaskCommentEvent event) {
        // nothing
    }
}
