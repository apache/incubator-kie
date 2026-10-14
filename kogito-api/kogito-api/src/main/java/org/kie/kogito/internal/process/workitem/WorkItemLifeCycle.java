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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.process.workitem.Policy;
import org.kie.kogito.process.workitem.WorkItemTransition;

import static java.util.stream.Collectors.toSet;

/**
 * Complete life cycle that can be applied to work items. It defines
 * set of phases and allow to get access to each of them by id.
 *
 * @param <T> defines the type of data managed through this life cycle
 */
public interface WorkItemLifeCycle {

    default List<WorkItemLifeCyclePhase> phaseByStatus(String phaseStatus) {
        return phases().stream().filter(e -> Objects.equals(e.sourceStatus().getName(), phaseStatus)).toList();
    }

    /**
     * Returns phase by its id if exists.
     *
     * @param phaseId phase id to be used for look up
     * @return life cycle phase if exists otherwise null
     */
    default WorkItemLifeCyclePhase phaseById(String phaseId, String phaseStatus) {
        return phases().stream().filter(e -> Objects.equals(e.id(), phaseId) && Objects.equals(e.sourceStatus().getName(), phaseStatus)).findAny().orElse(null);
    }

    /**
     * Returns all phases associated with this life cycle
     *
     * @return list of phases
     */
    Collection<WorkItemLifeCyclePhase> phases();

    /**
     * Perform actual transition to the target phase defined via given transition
     *
     * @param workItem work item that is being transitioned
     * @param manager work item manager for given work item
     * @param transition actual transition
     * @return returns work item data after the transition
     */
    Optional<WorkItemTransition> transitionTo(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition);

    /**
     * Returns the set of phases the provided phase is able to be transitioned to
     *
     * @param phaseId the phase we want to obtain which phases can be transitioned to
     * @return stream containing all phases that can be transitioned from the provided phase
     */
    default Set<WorkItemLifeCyclePhase> allowedPhases(String phaseStatus) {
        return this.phaseByStatus(phaseStatus).stream().collect(toSet());
    }

    WorkItemTransition newTransition(String transitionId, String phaseStatus, Map<String, Object> map, Policy... policy);

}
