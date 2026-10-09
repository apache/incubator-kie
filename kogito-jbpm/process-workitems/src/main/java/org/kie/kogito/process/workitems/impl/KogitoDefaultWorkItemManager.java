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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.drools.core.process.WorkItem;
import org.kie.internal.runtime.Closeable;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandlerNotFoundException;
import org.kie.kogito.internal.process.workitem.WorkItemNotFoundException;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.Policy;
import org.kie.kogito.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.InternalKogitoWorkItem;
import org.kie.kogito.process.workitems.InternalKogitoWorkItemManager;

import static java.util.Collections.emptyMap;

public class KogitoDefaultWorkItemManager implements InternalKogitoWorkItemManager {
    private Map<String, InternalKogitoWorkItem> workItems;
    private Map<String, KogitoWorkItemHandler> workItemHandlers;
    private KogitoProcessRuntime kruntime;

    public KogitoDefaultWorkItemManager(KogitoProcessRuntime kruntime) {
        this.kruntime = kruntime;
        this.workItems = new ConcurrentHashMap<>();
        this.workItemHandlers = new HashMap<>();
    }

    @Override
    public void clear() {
        this.workItems.clear();
    }

    @Override
    public void signalEvent(String type, Object event) {
        this.kruntime.signalEvent(type, event);
    }

    @Override
    public void dispose() {
        if (workItemHandlers != null) {
            for (Map.Entry<String, KogitoWorkItemHandler> handlerEntry : workItemHandlers.entrySet()) {
                if (handlerEntry.getValue() instanceof Closeable) {
                    ((Closeable) handlerEntry.getValue()).close();
                }
            }
        }
    }

    @Override
    public <T> T updateWorkItem(String id, Function<KogitoWorkItem, T> updater, Policy... policies) {
        KogitoWorkItem workItem = workItems.get(id);
        if (workItem == null) {
            throw new WorkItemNotFoundException(id);
        }

        Stream.of(policies).forEach(p -> p.enforce(workItem));
        return updater.apply(workItem);

    }

    @Override
    public Collection<String> getHandlerIds() {
        return this.workItemHandlers.keySet();
    }

    @Override
    public KogitoWorkItemHandler getKogitoWorkItemHandler(String name) {
        return this.workItemHandlers.get(name);
    }

    @Override
    public void registerWorkItemHandler(String workItemName, KogitoWorkItemHandler handler) {
        this.workItemHandlers.put(workItemName, handler);
    }

    @Override
    public void transitionWorkItem(String id, WorkItemTransition transition) {
        InternalKogitoWorkItem workItem = getWorkItem(id);
        if (workItem == null) {
            throw new WorkItemNotFoundException(id);
        }
        transitionWorkItem(workItem, transition, true);
    }

    public void transitionWorkItem(InternalKogitoWorkItem workItem, WorkItemTransition transition, boolean signal) {

        KogitoWorkItemHandler handler = this.workItemHandlers.get(workItem.getName());
        if (handler == null) {
            throw new KogitoWorkItemHandlerNotFoundException(workItem.getName());
        }
        WorkItemTransition lastTransition = null;
        Optional<WorkItemTransition> nextTransition = Optional.of(transition);
        while (nextTransition.isPresent()) {
            lastTransition = nextTransition.get();
            nextTransition = handler.transitionToPhase(this, workItem, nextTransition.get());
            this.kruntime.getProcessEventSupport().fireBeforeWorkItemTransition(workItem.getProcessInstance(), workItem, lastTransition, this.kruntime.getKieRuntime());
            workItem.getProcessInstance().signalEvent("workItemTransition", transition);
            this.kruntime.getProcessEventSupport().fireAfterWorkItemTransition(workItem.getProcessInstance(), workItem, lastTransition, this.kruntime.getKieRuntime());
        }

        if (lastTransition.termination().isPresent()) {
            internalRemoveWorkItem(workItem.getStringId());
            if (signal) {
                switch (lastTransition.termination().get()) {
                    case COMPLETE:
                        workItem.setState(KogitoWorkItem.COMPLETED);
                        workItem.getProcessInstance().signalEvent("workItemCompleted", workItem);
                        break;
                    case ABORT:
                        workItem.setState(KogitoWorkItem.ABORTED);
                        workItem.getProcessInstance().signalEvent("workItemAborted", workItem);
                        break;
                }
            }
        }

    }

    @Override
    public void internalAddWorkItem(InternalKogitoWorkItem workItem) {
        workItems.put(workItem.getStringId(), workItem);
    }

    @Override
    public void abortWorkItem(String id, Policy... policies) {
        InternalKogitoWorkItem workItem = getWorkItem(id);
        Stream.of(policies).forEach(p -> p.enforce(workItem));
        internalAbortWorkItem(workItem.getStringId());
        workItem.setState(KogitoWorkItem.ABORTED);
        this.kruntime.signalEvent("workItemAborted", workItem, workItem.getProcessInstanceId());
    }

    @Override
    public void internalAbortWorkItem(String id) {
        InternalKogitoWorkItem workItem = getWorkItem(id);
        KogitoWorkItemHandler handler = this.workItemHandlers.get(workItem.getName());
        if (handler == null) {
            throw new KogitoWorkItemHandlerNotFoundException(workItem.getName());
        }
        WorkItemTransition transition = handler.abortTransition(workItem.getPhaseStatus());
        transitionWorkItem(workItem, transition, false);
    }

    @Override
    public void completeWorkItem(String id, Map<String, Object> results, Policy... policies) {
        InternalKogitoWorkItem workItem = getWorkItem(id);
        Stream.of(policies).forEach(p -> p.enforce(workItem));
        workItem.setResults(results != null ? results : emptyMap());
        internalCompleteWorkItem(workItem);
        workItem.setState(KogitoWorkItem.COMPLETED);
        this.kruntime.signalEvent("workItemCompleted", workItem, workItem.getProcessInstanceId());
    }

    @Override
    public void internalCompleteWorkItem(InternalKogitoWorkItem workItem) {
        KogitoWorkItemHandler handler = this.workItemHandlers.get(workItem.getName());
        if (handler == null) {
            throw new KogitoWorkItemHandlerNotFoundException(workItem.getName());
        }
        WorkItemTransition transition = handler.completeTransition(workItem.getPhaseStatus(), workItem.getResults());
        transitionWorkItem(workItem, transition, false);
        internalRemoveWorkItem(workItem.getStringId());
    }

    @Override
    public void internalExecuteWorkItem(InternalKogitoWorkItem workItem) {
        internalAddWorkItem(workItem);
        KogitoWorkItemHandler handler = this.workItemHandlers.get(workItem.getName());
        if (handler == null) {
            throw new KogitoWorkItemHandlerNotFoundException(workItem.getName());
        }
        WorkItemTransition transition = handler.startingTransition(Collections.emptyMap());
        transitionWorkItem(workItem, transition, true);

    }

    @Override
    public InternalKogitoWorkItem getWorkItem(String id) {
        return workItems.get(id);
    }

    @Override
    public void internalRemoveWorkItem(String id) {
        workItems.remove(id);
    }

    @Override
    public void signalEvent(String type, Object event, String processInstanceId) {
        this.kruntime.signalEvent(type, event, processInstanceId);
    }

    @Override
    public void retryWorkItem(String workItemId, Map<String, Object> params) {
        Map<String, Object> normalizedParams = params != null && params.isEmpty() ? Collections.emptyMap() : params;
        this.retryWorkItemWithParams(workItemId, normalizedParams);
    }

    public void retryWorkItemWithParams(String workItemId, Map<String, Object> map) {
        InternalKogitoWorkItem workItem = workItems.get(workItemId);
        workItem.setPhaseId(null);
        workItem.setPhaseStatus(null);
        workItem.setParameters(map);
        internalExecuteWorkItem(workItem);
    }

    @Override
    public Set<WorkItem> getWorkItems() {
        return workItems.values().stream().collect(Collectors.toSet());
    }

    @Override
    public void completeWorkItem(long id, Map<String, Object> results) {
        throw new UnsupportedOperationException();

    }

    @Override
    public void abortWorkItem(long id) {
        throw new UnsupportedOperationException();
    }

}
