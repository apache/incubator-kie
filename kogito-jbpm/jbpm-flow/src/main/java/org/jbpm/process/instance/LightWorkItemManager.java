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
package org.jbpm.process.instance;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.drools.core.process.WorkItem;
import org.kie.kogito.internal.process.event.KogitoProcessEventSupport;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.KogitoWorkItemHandlerNotFoundException;
import org.kie.kogito.process.workitem.Policy;
import org.kie.kogito.process.workitem.WorkItemNotFoundException;
import org.kie.kogito.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.InternalKogitoWorkItem;
import org.kie.kogito.process.workitems.InternalKogitoWorkItemManager;
import org.kie.kogito.signal.SignalManager;

import static java.util.Collections.emptyMap;

public class LightWorkItemManager implements InternalKogitoWorkItemManager {

    private Map<String, InternalKogitoWorkItem> workItems = new ConcurrentHashMap<>();
    private Map<String, KogitoWorkItemHandler> workItemHandlers = new HashMap<>();

    private final ProcessInstanceManager processInstanceManager;
    private final SignalManager signalManager;
    private final KogitoProcessEventSupport eventSupport;

    public LightWorkItemManager(ProcessInstanceManager processInstanceManager, SignalManager signalManager, KogitoProcessEventSupport eventSupport) {
        this.processInstanceManager = processInstanceManager;
        this.signalManager = signalManager;
        this.eventSupport = eventSupport;
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
    public void internalAddWorkItem(InternalKogitoWorkItem workItem) {
        workItems.put(workItem.getStringId(), workItem);
    }

    @Override
    public InternalKogitoWorkItem getWorkItem(String workItemId) {
        InternalKogitoWorkItem workItem = workItems.get(workItemId);
        if (workItem == null) {
            throw new WorkItemNotFoundException("Work Item (" + workItemId + ") does not exist", workItemId);
        }
        return workItem;
    }

    @Override
    public void internalRemoveWorkItem(String id) {
        workItems.remove(id);
    }

    @Override
    public void internalExecuteWorkItem(InternalKogitoWorkItem workItem) {
        internalAddWorkItem(workItem);
        KogitoWorkItemHandler handler = getWorkItemHandler(workItem);
        WorkItemTransition transition = handler.startingTransition(Collections.emptyMap());
        transitionWorkItem(workItem, transition, true);
    }

    public KogitoWorkItemHandler getWorkItemHandler(String workItemId) throws KogitoWorkItemHandlerNotFoundException {
        InternalKogitoWorkItem workItem = workItems.get(workItemId);
        if (workItem == null) {
            throw new WorkItemNotFoundException(workItemId);
        }
        return getWorkItemHandler(workItem);
    }

    public KogitoWorkItemHandler getWorkItemHandler(InternalKogitoWorkItem workItem) throws KogitoWorkItemHandlerNotFoundException {
        KogitoWorkItemHandler handler = this.workItemHandlers.get(workItem.getName());
        if (handler == null) {
            throw new KogitoWorkItemHandlerNotFoundException(workItem.getName());
        }
        return handler;
    }

    @Override
    public void internalAbortWorkItem(String workItemId) {
        InternalKogitoWorkItem workItem = getWorkItem(workItemId);
        KogitoWorkItemHandler handler = getWorkItemHandler(workItem);
        transitionWorkItem(workItem, handler.abortTransition(workItem.getPhaseStatus()), false);
    }

    @Override
    public void retryWorkItem(String workItemId, Map<String, Object> params) {
        InternalKogitoWorkItem workItem = workItems.get(workItemId);

        if (workItem == null) {
            throw new WorkItemNotFoundException(workItemId);
        }

        workItem.setPhaseId(null);
        workItem.setPhaseStatus(null);

        if (params != null && !params.isEmpty()) {
            workItem.setParameters(params);
        }
        KogitoWorkItemHandler handler = getWorkItemHandler(workItem);
        WorkItemTransition transition = handler.startingTransition(Collections.emptyMap());
        transitionWorkItem(workItem, transition, true);
    }

    @Override
    public void completeWorkItem(String workItemId, Map<String, Object> data, Policy... policies) {
        InternalKogitoWorkItem workItem = getWorkItem(workItemId);
        KogitoWorkItemHandler handler = getWorkItemHandler(workItem);
        transitionWorkItem(workItem, handler.completeTransition(workItem.getPhaseStatus(), data, policies), false);
        // process instance may have finished already
        KogitoProcessInstance processInstance = processInstanceManager.getProcessInstance(workItem.getProcessInstanceStringId());
        workItem.setState(KogitoWorkItem.COMPLETED);
        processInstance.signalEvent("workItemCompleted", workItem);
    }

    @Override
    public void abortWorkItem(String workItemId, Policy... policies) {
        InternalKogitoWorkItem workItem = getWorkItem(workItemId);
        KogitoWorkItemHandler handler = getWorkItemHandler(workItemId);
        transitionWorkItem(workItem, handler.abortTransition(workItem.getPhaseStatus(), policies), false);
        // process instance may have finished already
        KogitoProcessInstance processInstance = processInstanceManager.getProcessInstance(workItem.getProcessInstanceStringId());
        workItem.setState(KogitoWorkItem.ABORTED);
        processInstance.signalEvent("workItemAborted", workItem);

    }

    @Override
    public <T> T updateWorkItem(String workItemId, Function<KogitoWorkItem, T> updater, Policy... policies) {
        InternalKogitoWorkItem workItem = getWorkItem(workItemId);
        Stream.of(policies).forEach(p -> p.enforce(workItem));
        T results = updater.apply(workItem);
        return results;

    }

    @Override
    public void internalCompleteWorkItem(InternalKogitoWorkItem workItem) {
        KogitoWorkItemHandler handler = getWorkItemHandler(workItem);
        transitionWorkItem(workItem.getStringId(), handler.completeTransition(workItem.getPhaseStatus(), emptyMap()));
    }

    @Override
    public void transitionWorkItem(String workItemId, WorkItemTransition transition) {
        InternalKogitoWorkItem workItem = getWorkItem(workItemId);
        transitionWorkItem(workItem, transition, true);
    }

    @Override
    public Collection<String> getHandlerIds() {
        return this.workItemHandlers.keySet();
    }

    private void transitionWorkItem(InternalKogitoWorkItem workItem, WorkItemTransition transition, boolean signal) {
        // work item may have been aborted
        KogitoWorkItemHandler handler = this.workItemHandlers.get(workItem.getName());
        if (handler == null) {
            throw new KogitoWorkItemHandlerNotFoundException(workItem.getName());
        }

        KogitoProcessInstance processInstance = processInstanceManager.getProcessInstance(workItem.getProcessInstanceStringId());
        WorkItemTransition lastTransition = null;
        Optional<WorkItemTransition> nextTransition = Optional.of(transition);
        while (nextTransition.isPresent()) {
            lastTransition = nextTransition.get();
            this.eventSupport.fireBeforeWorkItemTransition(workItem.getProcessInstance(), workItem, lastTransition, null);
            nextTransition = handler.transitionToPhase(this, workItem, nextTransition.get());
            processInstance.signalEvent("workItemTransition", transition);
            this.eventSupport.fireAfterWorkItemTransition(workItem.getProcessInstance(), workItem, lastTransition, null);
        }

        if (lastTransition.termination().isPresent()) {

            internalRemoveWorkItem(workItem.getStringId());
            if (signal) {
                switch (lastTransition.termination().get()) {
                    case COMPLETE:
                        workItem.setState(KogitoWorkItem.COMPLETED);
                        processInstance.signalEvent("workItemCompleted", workItem);
                        break;
                    case ABORT:
                        workItem.setState(KogitoWorkItem.ABORTED);
                        processInstance.signalEvent("workItemAborted", workItem);
                        break;
                }
            }

        }
    }

    @Override
    public void clear() {
        this.workItems.clear();
    }

    @Override
    public void signalEvent(String type, Object event) {
        this.signalManager.signalEvent(type, event);
    }

    @Override
    public void signalEvent(String type, Object event, String processInstanceId) {
        this.signalManager.signalEvent(processInstanceId, type, event);
    }

    // deprecated functions
    @Override
    public void dispose() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<WorkItem> getWorkItems() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void completeWorkItem(long l, Map<String, Object> map) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void abortWorkItem(long l) {
        throw new UnsupportedOperationException();
    }

}
