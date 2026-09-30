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
package org.kie.kogito.wih;

import java.util.Collections;
import java.util.Optional;

import org.kie.kogito.BranchExecutionCounter;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;

import io.quarkus.arc.Arc;

/**
 * Work item handler for the asyncsplit race-condition reproducer.
 * Sleeps for {@code waitTime} ms (to widen the race window) then increments
 * a non-transactional {@link BranchExecutionCounter} singleton.
 * Because the counter is outside the JTA transaction, its value reflects
 * total execution attempts — not just committed ones — making it the correct
 * observable for detecting optimistic-locking retries.
 */
public class BranchCounterWorkItemHandler extends DefaultKogitoWorkItemHandler {

    @Override
    public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler,
            KogitoWorkItem workItem, WorkItemTransition transition) {
        Long waitTime = (Long) workItem.getParameter("waitTime");
        if (waitTime != null && waitTime > 0) {
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Arc.container().select(BranchExecutionCounter.class).get().increment();
        return Optional.of(handler.completeTransition(workItem.getPhaseStatus(), Collections.emptyMap()));
    }

    @Override
    public Optional<WorkItemTransition> abortWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler,
            KogitoWorkItem workItem, WorkItemTransition transition) {
        return Optional.of(handler.abortTransition(workItem.getPhaseStatus()));
    }
}
