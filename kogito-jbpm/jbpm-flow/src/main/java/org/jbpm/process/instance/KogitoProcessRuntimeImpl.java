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
import java.util.Map;

import org.kie.api.KieBase;
import org.kie.api.event.process.ProcessEventManager;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.kogito.Application;
import org.kie.kogito.internal.process.event.KogitoProcessEventSupport;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.jobs.JobsService;
import org.kie.kogito.process.workitem.KogitoWorkItemManager;

public class KogitoProcessRuntimeImpl implements KogitoProcessRuntime {

    private final InternalProcessRuntime delegate;

    public KogitoProcessRuntimeImpl(InternalProcessRuntime delegate) {
        this.delegate = delegate;
    }

    @Override
    public KogitoProcessInstance startProcess(String processId) {
        return (KogitoProcessInstance) delegate.startProcess(processId);
    }

    @Override
    public KogitoProcessInstance startProcess(String processId, Map<String, Object> parameters) {
        return (KogitoProcessInstance) delegate.startProcess(processId, parameters);
    }

    @Override
    public KogitoProcessInstance createProcessInstance(String processId, Map<String, Object> parameters) {
        return (KogitoProcessInstance) delegate.createProcessInstance(processId, parameters);
    }

    @Override
    public KogitoProcessInstance startProcess(String processId, AgendaFilter agendaFilter) {
        return (KogitoProcessInstance) delegate.startProcess(processId, agendaFilter);
    }

    @Override
    public KogitoProcessInstance startProcess(String processId, Map<String, Object> parameters, AgendaFilter agendaFilter) {
        return (KogitoProcessInstance) delegate.startProcess(processId, parameters, agendaFilter);
    }

    @Override
    public KogitoProcessInstance startProcessInstance(String processInstanceId) {
        return startProcessInstance(processInstanceId, null);
    }

    @Override
    public KogitoProcessInstance startProcessInstance(String processInstanceId, String trigger) {
        return startProcessInstance(processInstanceId, trigger, null);
    }

    @Override
    public void signalEvent(String type, Object event) {
        delegate.signalEvent(type, event);
    }

    @Override
    public void signalEvent(String type, Object event, String processInstanceId) {
        delegate.getSignalManager().signalEvent(processInstanceId, type, event);
    }

    @Override
    public Collection<KogitoProcessInstance> getKogitoProcessInstances() {
        return (Collection<KogitoProcessInstance>) (Object) delegate.getProcessInstances();
    }

    @Override
    public KogitoProcessInstance getProcessInstance(String id) {
        return getProcessInstance(id, false);
    }

    @Override
    public KogitoProcessInstance getProcessInstance(String id, boolean readOnly) {
        return delegate.getProcessInstanceManager().getProcessInstance(id, readOnly);
    }

    @Override
    public void abortProcessInstance(String processInstanceId) {
        org.kie.api.runtime.process.ProcessInstance processInstance = getProcessInstance(processInstanceId);
        if (processInstance == null) {
            throw new IllegalArgumentException("Could not find process instance for id " + processInstanceId);
        }
        ((org.jbpm.process.instance.ProcessInstance) processInstance).setState(KogitoProcessInstance.STATE_ABORTED);
    }

    @Override
    public KogitoWorkItemManager getKogitoWorkItemManager() {
        return (KogitoWorkItemManager) delegate.getWorkItemManager();
    }

    @Override
    public KogitoProcessEventSupport getProcessEventSupport() {
        return delegate.getProcessEventSupport();
    }

    @Override
    public ProcessEventManager getProcessEventManager() {
        return delegate;
    }

    @Override
    public JobsService getJobsService() {
        return delegate.getJobsService();
    }

    @Override
    public KieRuntime getKieRuntime() {
        return delegate.getInternalKieRuntime();
    }

    @Override
    public KieBase getKieBase() {
        return delegate.getInternalKieRuntime().getKieBase();
    }

    @Override
    public KieSession getKieSession() {
        if (delegate.getInternalKieRuntime() instanceof KieSession) {
            return (KieSession) delegate.getInternalKieRuntime();
        }
        return null;
    }

    public KogitoProcessInstance startProcess(String processId, Map<String, Object> parameters, String trigger, AgendaFilter agendaFilter) {
        KogitoProcessInstance processInstance = createProcessInstance(processId, parameters);
        if (processInstance != null) {
            // start process instance
            return startProcessInstance(processInstance.getStringId(), trigger, agendaFilter);
        }
        return null;
    }

    public KogitoProcessInstance startProcessInstance(String processInstanceId, String trigger, AgendaFilter agendaFilter) {
        KogitoProcessInstance processInstance = getProcessInstance(processInstanceId);
        org.jbpm.process.instance.ProcessInstance jbpmProcessInstance = (org.jbpm.process.instance.ProcessInstance) processInstance;

        jbpmProcessInstance.configureTimers();
        delegate.getProcessEventSupport().fireBeforeProcessStarted(processInstance, delegate.getInternalKieRuntime());
        jbpmProcessInstance.setAgendaFilter(agendaFilter);
        jbpmProcessInstance.start(trigger);
        delegate.getProcessEventSupport().fireAfterProcessStarted(processInstance, delegate.getInternalKieRuntime());
        return jbpmProcessInstance;
    }

    @Override
    public Application getApplication() {
        return delegate.getApplication();
    }
}
