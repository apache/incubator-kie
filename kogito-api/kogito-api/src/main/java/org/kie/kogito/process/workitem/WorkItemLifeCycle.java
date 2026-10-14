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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * Complete life cycle that can be applied to work items. Defines the set of phases
 * and allows transitioning between them.
 */
public interface WorkItemLifeCycle {

    default List<WorkItemLifeCyclePhase> phaseByStatus(String phaseStatus) {
        return phases().stream().filter(e -> Objects.equals(e.sourceStatus().getName(), phaseStatus)).toList();
    }

    default WorkItemLifeCyclePhase phaseById(String phaseId, String phaseStatus) {
        return phases().stream()
                .filter(e -> Objects.equals(e.id(), phaseId) && Objects.equals(e.sourceStatus().getName(), phaseStatus))
                .findAny().orElse(null);
    }

    Collection<WorkItemLifeCyclePhase> phases();

    Optional<WorkItemTransition> transitionTo(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition);

    default Set<WorkItemLifeCyclePhase> allowedPhases(String phaseStatus) {
        return this.phaseByStatus(phaseStatus).stream().collect(toSet());
    }

    WorkItemTransition newTransition(String transitionId, String phaseStatus, Map<String, Object> map, Policy... policy);
}
