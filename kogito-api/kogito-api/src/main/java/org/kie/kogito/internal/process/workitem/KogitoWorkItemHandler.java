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
package org.kie.kogito.internal.process.workitem;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.kie.kogito.Application;

/**
 * <b>Work Item Handler</b> extension point.
 *
 * <p>
 * Defines the contract for executing custom business logic when a BPMN Custom Task
 * is reached by the workflow engine. Implementations can execute synchronously or initiate asynchronous
 * execution and complete at a later time via the work item manager.
 */
public interface KogitoWorkItemHandler {

    /**
     * Returns the {@link Application} context associated with this handler.
     *
     * @return the associated {@link Application} instance, or {@code null} if unassigned
     */
    Application getApplication();

    /**
     * Sets the {@link Application} context for this handler.
     *
     * @param app the {@link Application} context to associate
     */
    void setApplication(Application app);

    /**
     * Returns the unique task name this handler is bound to in BPMN definitions.
     *
     * @return the non-null task name identifying this handler; defaults to the simple class name
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Transitions a work item from its current lifecycle phase to the target phase.
     *
     * @param manager the non-null work item manager governing the execution
     * @param workItem the non-null work item instance undergoing phase transition
     * @param transition the non-null requested phase transition
     * @return an {@link Optional} containing the next chained transition token, or {@link Optional#empty()} when execution completes
     */
    Optional<WorkItemTransition> transitionToPhase(KogitoWorkItemManager manager, KogitoWorkItem workItem, WorkItemTransition transition);

    /**
     * Returns the set of allowed target phase transitions from the specified phase status.
     *
     * @param phaseStatus the non-null current phase status
     * @return an unmodifiable {@link Set} of allowed transition phase identifiers
     */
    Set<String> allowedTransitions(String phaseStatus);

    /**
     * Creates a new work item phase transition token.
     *
     * @param phaseId the non-null target phase identifier
     * @param phaseStatus the status of the phase
     * @param map the data payload associated with the transition
     * @param policy optional security or execution policies
     * @return a new non-null {@link WorkItemTransition} token
     */
    WorkItemTransition newTransition(String phaseId, String phaseStatus, Map<String, Object> map, Policy... policy);

    /**
     * Creates the initial starting transition token when a work item is activated.
     *
     * @param data initial input data parameters passed from the task node
     * @param policies optional execution policies
     * @return a new non-null starting {@link WorkItemTransition} token
     */
    WorkItemTransition startingTransition(Map<String, Object> data, Policy... policies);

    /**
     * Creates a completion transition token for this work item.
     *
     * @param phaseStatus the target phase status upon completion
     * @param data output data results to be mapped back to process variables
     * @param policies optional execution policies
     * @return a new non-null completion {@link WorkItemTransition} token
     */
    WorkItemTransition completeTransition(String phaseStatus, Map<String, Object> data, Policy... policies);

    /**
     * Creates an abort transition token when a work item is cancelled or terminated.
     *
     * @param phaseStatus the target phase status upon abort
     * @param policies optional execution policies
     * @return a new non-null abort {@link WorkItemTransition} token
     */
    WorkItemTransition abortTransition(String phaseStatus, Policy... policies);

}
