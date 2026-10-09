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

/**
 * @deprecated Use {@link org.kie.kogito.process.workitem.KogitoWorkItemHandler} instead.
 */
@Deprecated(since = "9.1.1-SNAPSHOT", forRemoval = true)
public interface KogitoWorkItemHandler extends org.kie.kogito.process.workitem.KogitoWorkItemHandler {

    default Optional<WorkItemTransition> transitionToPhase(KogitoWorkItemManager manager, KogitoWorkItem workItem, WorkItemTransition transition) {
        return transitionToPhase((org.kie.kogito.process.workitem.KogitoWorkItemManager) manager, (org.kie.kogito.process.workitem.KogitoWorkItem) workItem,
                (org.kie.kogito.process.workitem.WorkItemTransition) transition)
                        .map(t -> (WorkItemTransition) t);
    }

    default WorkItemTransition newTransition(String phaseId, String phaseStatus, Map<String, Object> map, Policy... policy) {
        return (WorkItemTransition) newTransition(phaseId, phaseStatus, map, (org.kie.kogito.process.workitem.Policy[]) policy);
    }

    default WorkItemTransition startingTransition(Map<String, Object> data, Policy... policies) {
        return (WorkItemTransition) startingTransition(data, (org.kie.kogito.process.workitem.Policy[]) policies);
    }

    default WorkItemTransition completeTransition(String phaseStatus, Map<String, Object> data, Policy... policies) {
        return (WorkItemTransition) completeTransition(phaseStatus, data, (org.kie.kogito.process.workitem.Policy[]) policies);
    }

    default WorkItemTransition abortTransition(String phaseStatus, Policy... policies) {
        return (WorkItemTransition) abortTransition(phaseStatus, (org.kie.kogito.process.workitem.Policy[]) policies);
    }
}
