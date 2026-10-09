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
package org.kie.kogito.process.workitems;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.kie.kogito.Application;
import org.kie.kogito.internal.process.workitem.WorkItemLifeCycle;
import org.kie.kogito.internal.process.workitem.WorkItemLifeCyclePhase;
import org.kie.kogito.internal.process.workitem.WorkItemPhaseState;
import org.kie.kogito.internal.process.workitem.WorkItemRecordParameters;
import org.kie.kogito.internal.process.workitem.WorkItemTerminationType;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.process.workitem.Policy;
import org.kie.kogito.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.impl.DefaultWorkItemLifeCycle;
import org.kie.kogito.process.workitems.impl.DefaultWorkItemLifeCyclePhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toSet;

public class DefaultKogitoWorkItemHandler implements KogitoWorkItemHandler {

    public static final String TRANSITION_COMPLETE = "complete";
    public static final String TRANSITION_ABORT = "abort";
    public static final String TRANSITION_ACTIVATE = "activate";
    public static final String TRANSITION_SKIP = "skip";

    public static final WorkItemPhaseState INITIALIZED = WorkItemPhaseState.initialized();
    public static final WorkItemPhaseState COMPLETED = WorkItemPhaseState.of("Completed", WorkItemTerminationType.COMPLETE);
    public static final WorkItemPhaseState ABORTED = WorkItemPhaseState.of("Aborted", WorkItemTerminationType.ABORT);
    public static final WorkItemPhaseState ACTIVATED = WorkItemPhaseState.of("Activated");

    private static Logger LOG = LoggerFactory.getLogger(DefaultKogitoWorkItemHandler.class);

    protected Application application;

    protected WorkItemLifeCycle workItemLifeCycle;

    public DefaultKogitoWorkItemHandler(WorkItemLifeCycle workItemLifeCycle) {
        this.workItemLifeCycle = workItemLifeCycle;
    }

    public DefaultKogitoWorkItemHandler() {
        this.workItemLifeCycle = initialize();
    }

    public WorkItemLifeCycle initialize() {
        DefaultWorkItemLifeCyclePhase complete = new DefaultWorkItemLifeCyclePhase(TRANSITION_COMPLETE, ACTIVATED, COMPLETED, this::completeWorkItemHandler);
        DefaultWorkItemLifeCyclePhase abort = new DefaultWorkItemLifeCyclePhase(TRANSITION_ABORT, ACTIVATED, ABORTED, this::abortWorkItemHandler);
        DefaultWorkItemLifeCyclePhase active = new DefaultWorkItemLifeCyclePhase(TRANSITION_ACTIVATE, INITIALIZED, ACTIVATED, this::activateWorkItemHandler);
        DefaultWorkItemLifeCyclePhase skip = new DefaultWorkItemLifeCyclePhase(TRANSITION_SKIP, ACTIVATED, COMPLETED, this::skipWorkItemHandler);
        return new DefaultWorkItemLifeCycle(active, skip, abort, complete);
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    @Override
    public Application getApplication() {
        return this.application;
    }

    @Override
    public WorkItemTransition startingTransition(Map<String, Object> data, Policy... policies) {
        return workItemLifeCycle.newTransition(TRANSITION_ACTIVATE, null, data, policies);
    }

    @Override
    public WorkItemTransition abortTransition(String phaseStatus, Policy... policies) {
        return workItemLifeCycle.newTransition(TRANSITION_ABORT, phaseStatus, emptyMap(), policies);
    }

    @Override
    public WorkItemTransition completeTransition(String phaseStatus, Map<String, Object> data, Policy... policies) {
        return workItemLifeCycle.newTransition(TRANSITION_COMPLETE, phaseStatus, data, policies);
    }

    @Override
    public Optional<WorkItemTransition> transitionToPhase(KogitoWorkItemManager manager, KogitoWorkItem workItem, WorkItemTransition transition) {
        LOG.debug("workItem {} handled by {} transition {}", workItem, this.getName(), transition);
        try {
            return workItemLifeCycle.transitionTo(manager, this, workItem, transition);
        } catch (RuntimeException e) {
            LOG.info("error workItem {} handled by {} transition {} ", workItem, this.getName(), transition, e);
            throw e;
        }
    }

    @Override
    public Set<String> allowedTransitions(String phaseStatus) {
        return workItemLifeCycle.allowedPhases(phaseStatus).stream().map(WorkItemLifeCyclePhase::id).collect(toSet());
    }

    @Override
    public WorkItemTransition newTransition(String phaseId, String phaseStatus, Map<String, Object> map, Policy... policy) {
        return workItemLifeCycle.newTransition(phaseId, phaseStatus, map, policy);
    }

    public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workitem, WorkItemTransition transition) {
        return Optional.empty();
    }

    public Optional<WorkItemTransition> completeWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workitem, WorkItemTransition transition) {
        WorkItemRecordParameters.recordOutputParameters(workitem, transition.data());
        return Optional.empty();
    }

    public Optional<WorkItemTransition> abortWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workitem, WorkItemTransition transition) {
        return Optional.empty();
    }

    public Optional<WorkItemTransition> skipWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workitem, WorkItemTransition transition) {
        return Optional.empty();
    }
}
