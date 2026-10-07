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

package org.kie.kogito.usertask.lifecycle;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.kie.kogito.auth.IdentityProvider;
import org.kie.kogito.jobs.descriptors.UserTaskInstanceJobDescription;
import org.kie.kogito.usertask.UserTaskInstance;

/**
 * <b>User Task Lifecycle</b> extension point.
 *
 * <p>
 * Defines the state machine, valid transition paths, and execution hooks governing Human Task instances.
 */
public interface UserTaskLifeCycle {

    /**
     * Returns the transition identifier used to activate a newly created user task.
     *
     * @return the non-null starting transition identifier
     */
    String startTransition();

    /**
     * Returns the transition identifier used for task reassignment.
     *
     * @return the non-null reassignment transition identifier
     */
    String reassignTransition();

    /**
     * Returns the transition identifier used for task abort.
     *
     * @return the non-null abort transition identifier
     */
    String abortTransition();

    /**
     * Executes a state transition on the target user task instance.
     *
     * @param userTaskInstance the non-null user task instance undergoing transition
     * @param transition the non-null transition token defining source, target, and payload data
     * @param identity the non-null caller identity requesting the transition
     * @return an {@link Optional} containing a chained follow-up transition token, or {@link Optional#empty()} when complete
     * @throws UserTaskTransitionException if the requested transition is invalid from the current task state
     */
    Optional<UserTaskTransitionToken> transition(UserTaskInstance userTaskInstance, UserTaskTransitionToken transition, IdentityProvider identity);

    /**
     * Creates a new transition token for the specified transition ID, current task instance state, and data payload.
     *
     * @param transitionId the non-null transition identifier
     * @param userTaskInstance the non-null target task instance
     * @param data the data parameters to include in the transition
     * @return a new non-null {@link UserTaskTransitionToken}
     */
    UserTaskTransitionToken newTransitionToken(String transitionId, UserTaskInstance userTaskInstance, Map<String, Object> data);

    /**
     * Creates an optional reassignment transition token.
     *
     * @param defaultUserTaskInstance the non-null user task instance
     * @param data data parameters including the target assignee
     * @return an {@link Optional} containing the reassignment transition token, or {@link Optional#empty()} if unsupported
     */
    default Optional<UserTaskTransitionToken> newReassignmentTransitionToken(UserTaskInstance defaultUserTaskInstance, Map<String, Object> data) {
        return Optional.empty();
    }

    /**
     * Creates a completion transition token for this user task.
     *
     * @param userTaskInstance the non-null target user task instance
     * @param data output data results to set on the completed task
     * @return a new non-null completion {@link UserTaskTransitionToken}
     */
    UserTaskTransitionToken newCompleteTransitionToken(UserTaskInstance userTaskInstance, Map<String, Object> data);

    /**
     * Creates an abort transition token for this user task.
     *
     * @param userTaskInstance the non-null target user task instance
     * @param data optional abort metadata
     * @return a new non-null abort {@link UserTaskTransitionToken}
     */
    UserTaskTransitionToken newAbortTransitionToken(UserTaskInstance userTaskInstance, Map<String, Object> data);

    /**
     * Returns all valid target transitions allowed from the current state of the user task instance for the specified identity.
     *
     * @param ut the non-null target user task instance
     * @param identity the non-null requesting user identity
     * @return an unmodifiable {@link List} of allowed {@link UserTaskTransition} instances
     */
    List<UserTaskTransition> allowedTransitions(UserTaskInstance ut, IdentityProvider identity);

    /**
     * Handles timer-triggered deadlines and escalations for this user task instance.
     *
     * @param jobDescription the non-null timer job descriptor
     * @param userTaskInstance the non-null associated user task instance
     */
    default void handleTimer(UserTaskInstanceJobDescription jobDescription, UserTaskInstance userTaskInstance) {

    }

}
