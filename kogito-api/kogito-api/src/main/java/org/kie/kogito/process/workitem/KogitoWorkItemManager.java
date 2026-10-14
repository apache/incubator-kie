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
package org.kie.kogito.process.workitem;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

public interface KogitoWorkItemManager {

    /**
     * Notifies the work item manager that the work item with the given
     * id has been completed. Results related to the execution of this
     * work item can be passed.
     *
     * @param id the id of the work item that has been completed
     * @param results the results related to this work item, or <code>null</code> if there are no results
     */
    void completeWorkItem(String id, Map<String, Object> results, Policy... policies);

    /**
     * Notifies the work item manager that the work item with the given
     * id could not be executed and should be aborted.
     *
     * @param id the id of the work item that should be aborted
     */
    void abortWorkItem(String id, Policy... policies);

    /**
     * Updates work item, performing operation indicated by updater
     * 
     * @param id the id of the work item that has been completed
     * @param updater consumer implementation that contains the logic to update workitem
     * @param policies optional security information
     * @return result of the operation performed by updater consumer
     */
    <T> T updateWorkItem(String id, Function<KogitoWorkItem, T> updater, Policy... policies);

    /**
     * Register the given handler for all work items of the given
     * type of work
     *
     * @param workItemName the type of work this work item handler can execute
     * @param handler the handler for executing work items
     */
    void registerWorkItemHandler(String workItemName, KogitoWorkItemHandler handler);

    /**
     * Transition work item with given id into the next life cycle phase.
     *
     * @param id work item id to be transitioned
     * @param transition actual transition to apply to work item
     */
    void transitionWorkItem(String id, WorkItemTransition transition);

    /**
     * retrieves the handlers names registered in the work item handler
     * 
     * @return
     */
    Collection<String> getHandlerIds();

    /**
     * retrieeves the handle registered by the name
     * 
     * @param name
     * @return
     */
    KogitoWorkItemHandler getKogitoWorkItemHandler(String name);
}
