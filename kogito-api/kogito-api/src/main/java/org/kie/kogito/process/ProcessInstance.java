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
package org.kie.kogito.process;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.kie.kogito.correlation.Correlation;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.jobs.TimerDescription;
import org.kie.kogito.process.flexible.AdHocFragment;
import org.kie.kogito.process.flexible.Milestone;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.Policy;
import org.kie.kogito.process.workitem.WorkItemTransition;

public interface ProcessInstance<T> {

    int STATE_PENDING = 0;
    int STATE_ACTIVE = 1;
    int STATE_COMPLETED = 2;
    int STATE_ABORTED = 3;
    int STATE_SUSPENDED = 4;
    int STATE_ERROR = 5;

    /**
     * Returns process definition associated with this process instance
     *
     * @return process definition of this process instance
     */
    Process<T> process();

    /**
     * Starts process instance
     */
    void start();

    /**
     * Starts process instance with trigger
     *
     * @param trigger name of the trigger that will indicate what start node to trigger
     * @param referenceId optional reference id that points to a another component triggering this instance
     */
    void start(String trigger, String referenceId);

    /**
     * Starts process instance with trigger
     *
     * @param trigger name of the trigger that will indicate what start node to trigger
     * @param referenceId optional reference id that points to a another component triggering this instance
     * @param headers process headers
     */
    void start(String trigger, String referenceId, Map<String, List<String>> headers);

    /**
     * Starts process instance with trigger and headers
     *
     * @param trigger name of the trigger that will indicate what start node to trigger
     * @param headers list of headers
     */
    void start(Map<String, List<String>> headers);

    /**
     * Starts process instance from given node
     *
     * @param nodeId node id that should be used as the first node
     */
    void startFrom(String nodeId);

    /**
     * Starts process instance from given node and headers
     *
     * @param nodeId node id that should be used as the first node
     * @param headers list of headers
     */
    void startFrom(String startFromNodeId, Map<String, List<String>> headers);

    /**
     * Starts process instance from given node
     *
     * @param nodeId node id that should be used as the first node
     * @param referenceId optional reference id that points to a another component triggering this instance
     */
    void startFrom(String nodeId, String referenceId);

    /**
     * Starts process instance from given node
     *
     * @param nodeId node id that should be used as the first node
     * @param referenceId optional reference id that points to a another component triggering this instance
     * @param headers process headers
     */
    void startFrom(String nodeId, String referenceId, Map<String, List<String>> headers);

    /**
     * Sends given signal into this process instance
     *
     * @param signal signal to be processed
     */
    <S> void send(Signal<S> signal);

    /**
     * Aborts this process instance
     */
    void abort();

    /**
     * Returns process variables of this process instance
     *
     * @return variables of the process instance
     */
    T variables();

    /**
     * Updates process variables of this process instance
     */
    T updateVariables(T updates);

    /**
     * Partially updates process variables of this process instance
     * Partial means that null values are ignored.
     */
    T updateVariablesPartially(T updates);

    /**
     * Returns current status of this process instance
     *
     * @return the current status
     */
    int status();

    /**
     * Completes work item belonging to this process instance with given variables
     *
     * @param id id of the work item to complete
     * @param variables optional variables
     * @param policies optional list of policies to be enforced
     */
    void completeWorkItem(String id, Map<String, Object> variables, Policy... policies);

    /**
     * Updates work item according to provided consumer
     * 
     * @param id the id of the work item that has been completed
     * @param updater consumer implementation that contains the logic to update workitem
     * @param policies optional security information
     * @return result of the operation performed by the updater
     */
    <R> R updateWorkItem(String id, Function<KogitoWorkItem, R> updater, Policy... policies);

    /**
     * Aborts work item belonging to this process instance
     *
     * @param id id of the work item to complete
     * @param policies optional list of policies to be enforced
     */
    void abortWorkItem(String id, Policy... policies);

    /**
     * Transition work item belonging to this process instance not another life cycle phase
     *
     * @param id id of the work item to complete
     * @param transition target transition including phase, identity and data
     */
    void transitionWorkItem(String id, WorkItemTransition transition);

    /**
     * Returns work item identified by given id if found
     *
     * @param workItemId id of the work item
     * @param policies optional list of policies to be enforced
     * @return work item with its parameters if found
     */
    WorkItem workItem(String workItemId, Policy... policies);

    /**
     * Return nodes that fulfills a particular filter
     * 
     * @filter filter the returned nodes should fulfill
     * @return collections of nodes that match the filter
     */
    Collection<KogitoNodeInstance> findNodes(Predicate<KogitoNodeInstance> filter);

    /**
     * Returns list of currently active work items.
     *
     * @param policies optional list of policies to be enforced
     * @return list of work items
     */
    List<WorkItem> workItems(Policy... policies);

    /**
     * Returns list of filtered work items
     * 
     * @param p the predicate to be applied to the node holding the work item
     * @return list of work items
     */
    List<WorkItem> workItems(Predicate<KogitoNodeInstance> p, Policy... policies);

    /**
     * Returns identifier of this process instance
     *
     * @return id of the process instance
     */
    String id();

    /**
     * Returns optional business key associated with this process instance
     *
     * @return business key if available otherwise null
     */
    String businessKey();

    /**
     * Returns optional description of this process instance
     *
     * @return description of the process instance
     */
    String description();

    /**
     * Returns startDate of this process instance
     * 
     * @return
     */
    Date startDate();

    /**
     * Returns process error in case process instance is in error state.
     *
     * @return returns process error
     */
    Optional<ProcessError> error();

    default ProcessInstance<T> checkError() {
        Optional<ProcessError> error = error();
        if (error.isPresent()) {
            throw ProcessInstanceExecutionException.fromError(this);
        }
        return this;
    }

    void triggerNode(String nodeId);

    void cancelNodeInstance(String nodeInstanceId);

    void retriggerNodeInstance(String nodeInstanceId);

    Set<EventDescription<?>> events();

    /**
     * Returns the process milestones
     *
     * @return All the process milestones with their current status
     */
    Collection<Milestone> milestones();

    Collection<TimerDescription> timers();

    /**
     * Returns the process adHocFragments
     *
     * @return All the {@link AdHocFragment} in the process
     */
    Collection<AdHocFragment> adHocFragments();

    long version();

    Optional<? extends Correlation<?>> correlation();

    void updateNodeInstanceSla(String nodeInstanceId, ZonedDateTime slaDueDate);

    void updateProcessInstanceSla(ZonedDateTime slaDueDate);
}
