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
package org.kie.kogito.process.workitems.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.kie.kogito.internal.process.workitem.InvalidTransitionException;
import org.kie.kogito.internal.process.workitem.WorkItemLifeCycle;
import org.kie.kogito.internal.process.workitem.WorkItemLifeCyclePhase;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.process.workitem.Policy;
import org.kie.kogito.process.workitem.WorkItemTransition;

public class DefaultWorkItemLifeCycle implements WorkItemLifeCycle {

    List<WorkItemLifeCyclePhase> phases;

    public DefaultWorkItemLifeCycle(WorkItemLifeCyclePhase... phases) {
        this.phases = List.of(phases);
    }

    @Override
    public Optional<WorkItemTransition> transitionTo(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
        WorkItemLifeCyclePhase phase = phaseById(transition.id(), workItem.getPhaseStatus());
        if (phase == null) {
            throw new InvalidTransitionException("transition id " + transition.id() + " is not allowed for handler " + workItem.getName() + " from " + workItem.getPhaseStatus());
        }
        transition.policies().stream().forEach(policy -> policy.enforce(workItem));
        if (workItem instanceof KogitoWorkItemImpl impl) {
            impl.setPhaseId(phase.id());
            impl.setPhaseStatus(phase.targetStatus().getName());
            impl.setResults((Map<String, Object>) transition.data());
        }

        return phase.execute(manager, handler, workItem, transition);
    }

    @Override
    public Collection<WorkItemLifeCyclePhase> phases() {
        return phases;
    }

    @Override
    public WorkItemTransition newTransition(String transitionId, String currentPhaseStatus, Map<String, Object> data, Policy... policy) {
        WorkItemLifeCyclePhase phase = phaseById(transitionId, currentPhaseStatus);
        if (phase == null) {
            throw new InvalidTransitionException("new transition id " + transitionId + " is not allowed from " + currentPhaseStatus + " is invalid");
        }
        return new DefaultWorkItemTransitionImpl(transitionId, phase.targetStatus().getTermination().orElse(null), data, policy);
    }

}
