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

import java.util.Optional;

public class DefaultWorkItemLifeCyclePhase implements WorkItemLifeCyclePhase {

    public interface WorkItemLifeCyclePhaseExecutor {
        Optional<WorkItemTransition> execute(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workitem, WorkItemTransition transition);
    }

    private String id;
    private WorkItemPhaseState sourceStatus;
    private WorkItemPhaseState targetStatus;
    private WorkItemLifeCyclePhaseExecutor execution;

    public DefaultWorkItemLifeCyclePhase(String id, WorkItemPhaseState sourceStatus, WorkItemPhaseState targetStatus, WorkItemLifeCyclePhaseExecutor execution) {
        this.id = id;
        this.sourceStatus = sourceStatus;
        this.targetStatus = targetStatus;
        this.execution = execution;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public WorkItemPhaseState sourceStatus() {
        return sourceStatus;
    }

    @Override
    public WorkItemPhaseState targetStatus() {
        return targetStatus;
    }

    @Override
    public Optional<WorkItemTransition> execute(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workitem, WorkItemTransition transition) {
        return this.execution.execute(manager, handler, workitem, transition);
    }

    @Override
    public boolean isStartingPhase() {
        return sourceStatus != null;
    }

    @Override
    public String toString() {
        return "DefaultWorkItemLifeCyclePhase[transition=" + id + ", oldStatus=" + sourceStatus + ", newStatus=" + targetStatus + ", isStarting=" + isStartingPhase() + "]";
    }
}
