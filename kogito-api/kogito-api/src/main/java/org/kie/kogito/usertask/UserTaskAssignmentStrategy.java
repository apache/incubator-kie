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

import java.util.Optional;

import org.kie.kogito.auth.IdentityProvider;

/**
 * <b>User Task Assignment Strategy</b> extension point.
 *
 * <p>
 * Computes user task assignments dynamically when human tasks are created or reassigned.
 */
public interface UserTaskAssignmentStrategy {

    static final String DEFAULT_NAME = "default";

    /**
     * Returns the unique strategy name identifying this assignment implementation.
     *
     * @return the non-null strategy identifier; defaults to the fully qualified class name
     */
    default String getName() {
        return getClass().getName();
    }

    /**
     * Computes the assigned user for the specified user task instance.
     *
     * @param userTaskInstance the non-null user task instance requiring assignment
     * @param identityProvider the non-null caller security identity
     * @return an {@link Optional} containing the assigned username, or {@link Optional#empty()} if no assignment is resolved
     */
    Optional<String> computeAssignment(UserTaskInstance userTaskInstance, IdentityProvider identityProvider);

}
