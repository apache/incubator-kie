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
package org.jbpm.process.workitem.builtin;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.kogito.Application;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.internal.process.workitem.Policy;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;

public abstract class AbstractExceptionHandlingTaskHandler extends DefaultKogitoWorkItemHandler {

    private KogitoWorkItemHandler originalTaskHandler;

    public AbstractExceptionHandlingTaskHandler(KogitoWorkItemHandler originalTaskHandler) {
        this.originalTaskHandler = originalTaskHandler;
    }

    public AbstractExceptionHandlingTaskHandler(Class<? extends KogitoWorkItemHandler> originalTaskHandlerClass) {
        Class<?>[] clsParams = {};
        Object[] objParams = {};
        try {
            this.originalTaskHandler = originalTaskHandlerClass.getConstructor(clsParams).newInstance(objParams);
        } catch (Exception e) {
            throw new UnsupportedOperationException("The " + WorkItemHandler.class.getSimpleName() + " parameter must have a public no-argument constructor.");
        }
    }

    @Override
    public Application getApplication() {
        return originalTaskHandler.getApplication();
    }

    @Override
    public void setApplication(Application app) {
        originalTaskHandler.setApplication(app);
    }

    @Override
    public Optional<WorkItemTransition> transitionToPhase(KogitoWorkItemManager manager, KogitoWorkItem workItem, WorkItemTransition transition) {
        try {
            return this.originalTaskHandler.transitionToPhase(manager, workItem, transition);
        } catch (Throwable cause) {
            handleException(manager, originalTaskHandler, workItem, transition, cause);
            return Optional.empty();
        }
    }

    public KogitoWorkItemHandler getOriginalTaskHandler() {
        return originalTaskHandler;
    }

    /**
     * Delegates to the wrapped handler so that the decorator registers under
     * the same task name that the manager and BPMN engine use for lookup.
     */
    @Override
    public String getName() {
        return originalTaskHandler.getName();
    }

    @Override
    public Set<String> allowedTransitions(String phaseStatus) {
        return originalTaskHandler.allowedTransitions(phaseStatus);
    }

    @Override
    public WorkItemTransition newTransition(String phaseId, String phaseStatus, Map<String, Object> map, Policy... policy) {
        return originalTaskHandler.newTransition(phaseId, phaseStatus, map, policy);
    }

    @Override
    public WorkItemTransition startingTransition(Map<String, Object> data, Policy... policies) {
        return originalTaskHandler.startingTransition(data, policies);
    }

    @Override
    public WorkItemTransition completeTransition(String phaseStatus, Map<String, Object> data, Policy... policies) {
        return originalTaskHandler.completeTransition(phaseStatus, data, policies);
    }

    @Override
    public WorkItemTransition abortTransition(String phaseStatus, Policy... policies) {
        return originalTaskHandler.abortTransition(phaseStatus, policies);
    }

    public abstract void handleException(KogitoWorkItemManager manager, KogitoWorkItemHandler originalTaskHandler, KogitoWorkItem workItem, WorkItemTransition transition, Throwable cause);

}
