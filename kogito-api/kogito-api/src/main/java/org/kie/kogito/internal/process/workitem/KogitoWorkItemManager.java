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
import java.util.function.Function;

/**
 * @deprecated Use {@link org.kie.kogito.process.workitem.KogitoWorkItemManager} instead.
 */
@Deprecated(since = "9.1.1-SNAPSHOT", forRemoval = true)
public interface KogitoWorkItemManager extends org.kie.kogito.process.workitem.KogitoWorkItemManager {

    default void completeWorkItem(String id, Map<String, Object> results, Policy... policies) {
        completeWorkItem(id, results, (org.kie.kogito.process.workitem.Policy[]) policies);
    }

    default void abortWorkItem(String id, Policy... policies) {
        abortWorkItem(id, (org.kie.kogito.process.workitem.Policy[]) policies);
    }

    default <T> T updateWorkItem(String id, Function<KogitoWorkItem, T> updater, Policy... policies) {
        return updateWorkItem(id, (org.kie.kogito.process.workitem.KogitoWorkItem item) -> updater.apply((KogitoWorkItem) item), (org.kie.kogito.process.workitem.Policy[]) policies);
    }

    default void registerWorkItemHandler(String workItemName, KogitoWorkItemHandler handler) {
        registerWorkItemHandler(workItemName, (org.kie.kogito.process.workitem.KogitoWorkItemHandler) handler);
    }

    default void transitionWorkItem(String id, WorkItemTransition transition) {
        transitionWorkItem(id, (org.kie.kogito.process.workitem.WorkItemTransition) transition);
    }

    @Override
    KogitoWorkItemHandler getKogitoWorkItemHandler(String name);
}
