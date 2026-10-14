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
import java.util.Map;
import java.util.Objects;

import org.drools.core.common.EndOperationListener;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.core.impl.EnvironmentImpl;
import org.drools.core.time.TimerService;
import org.jbpm.workflow.instance.impl.CodegenNodeInstanceFactoryRegistry;
import org.kie.api.KieBase;
import org.kie.api.command.Command;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.process.ProcessEventManager;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.logger.KieRuntimeLogger;
import org.kie.api.runtime.Calendars;
import org.kie.api.runtime.Channel;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.Globals;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.ObjectFilter;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.LiveQuery;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.ViewChangedEventListener;
import org.kie.api.time.SessionClock;
import org.kie.kogito.Application;
import org.kie.kogito.calendar.BusinessCalendar;
import org.kie.kogito.internal.process.event.KogitoProcessEventSupport;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.jobs.JobsService;
import org.kie.kogito.process.ProcessConfig;
import org.kie.kogito.process.workitem.KogitoWorkItemManager;

import static org.jbpm.process.core.constants.CalendarConstants.BUSINESS_CALENDAR_ENVIRONMENT_KEY;
import static org.jbpm.workflow.instance.impl.NodeInstanceFactoryRegistry.NODE_INSTANCE_FACTORY_REGISTRY_KEY;

/**
 * A severely limited implementation of the WorkingMemory interface.
 * It only exists for legacy reasons.
 */
class DummyKnowledgeRuntime implements InternalKnowledgeRuntime, KogitoProcessRuntime {

    private final EnvironmentImpl environment;
    private InternalProcessRuntime processRuntime;

    DummyKnowledgeRuntime(InternalProcessRuntime processRuntime) {
        this.processRuntime = processRuntime;
        this.environment = new EnvironmentImpl();
        // register codegen-based node instances factories
        BusinessCalendar calendar = processRuntime.getApplication().config().get(ProcessConfig.class).getBusinessCalendar();
        if (Objects.nonNull(calendar)) {
            environment.set(BUSINESS_CALENDAR_ENVIRONMENT_KEY, calendar);
        }
        environment.set(NODE_INSTANCE_FACTORY_REGISTRY_KEY, CodegenNodeInstanceFactoryRegistry.class.getName());
    }

    @Override
    public InternalAgenda getAgenda() {
        return null;
    }

    @Override
    public void setIdentifier(long id) {

    }

    @Override
    public void setEndOperationListener(EndOperationListener listener) {

    }

    @Override
    public long getLastIdleTimestamp() {
        return 0;
    }

    @Override
    public InternalProcessRuntime getProcessRuntime() {
        return this.processRuntime;
    }

    @Override
    public KogitoProcessEventSupport getProcessEventSupport() {
        return processRuntime.getProcessEventSupport();
    }

    @Override
    public ProcessEventManager getProcessEventManager() {
        return processRuntime;
    }

    @Override
    public Environment getEnvironment() {
        return environment;
    }

    @Override
    public JobsService getJobsService() {
        return null;
    }

    @Override
    public KieRuntime getKieRuntime() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T execute(Command<T> command) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends SessionClock> T getSessionClock() {
        return null;
    }

    @Override
    public void setGlobal(String identifier, Object value) {

    }

    @Override
    public Object getGlobal(String identifier) {
        return null;
    }

    @Override
    public Globals getGlobals() {
        return null;
    }

    @Override
    public Calendars getCalendars() {
        return null;
    }

    @Override
    public KieBase getKieBase() {
        return null;
    }

    @Override
    public KieSession getKieSession() {
        return null;
    }

    @Override
    public void registerChannel(String name, Channel channel) {

    }

    @Override
    public void unregisterChannel(String name) {

    }

    @Override
    public Map<String, Channel> getChannels() {
        return Collections.emptyMap();
    }

    @Override
    public KieSessionConfiguration getSessionConfiguration() {
        return null;
    }

    @Override
    public KieRuntimeLogger getLogger() {
        return null;
    }

    @Override
    public void addEventListener(ProcessEventListener listener) {

    }

    @Override
    public void removeEventListener(ProcessEventListener listener) {

    }

    @Override
    public Collection<ProcessEventListener> getProcessEventListeners() {
        return Collections.emptyList();
    }

    @Override
    public void addEventListener(RuleRuntimeEventListener listener) {

    }

    @Override
    public void removeEventListener(RuleRuntimeEventListener listener) {

    }

    @Override
    public Collection<RuleRuntimeEventListener> getRuleRuntimeEventListeners() {
        return Collections.emptyList();
    }

    @Override
    public void addEventListener(AgendaEventListener listener) {

    }

    @Override
    public void removeEventListener(AgendaEventListener listener) {

    }

    @Override
    public Collection<AgendaEventListener> getAgendaEventListeners() {
        return Collections.emptyList();
    }

    @Override
    public KogitoProcessInstance startProcess(String processId) {
        return (KogitoProcessInstance) processRuntime.startProcess(processId);
    }

    @Override
    public KogitoProcessInstance startProcess(String processId, Map<String, Object> parameters) {
        return (KogitoProcessInstance) processRuntime.startProcess(processId, parameters);
    }

    @Override
    public KogitoProcessInstance startProcess(String processId, AgendaFilter agendaFilter) {
        return (KogitoProcessInstance) processRuntime.startProcess(processId, agendaFilter);
    }

    @Override
    public KogitoProcessInstance startProcess(String processId, Map<String, Object> parameters, AgendaFilter agendaFilter) {
        return (KogitoProcessInstance) processRuntime.startProcess(processId, parameters, agendaFilter);
    }

    @Override
    public ProcessInstance startProcessFromNodeIds(String s, Map<String, Object> map, String... strings) {
        return processRuntime.startProcessFromNodeIds(s, map, strings);

    }

    @Override
    public KogitoProcessInstance createProcessInstance(String processId, Map<String, Object> parameters) {
        return (KogitoProcessInstance) processRuntime.createProcessInstance(processId, null, parameters);
    }

    @Override
    public KogitoProcessInstance startProcessInstance(String processInstanceId) {
        return processRuntime.getKogitoProcessRuntime().startProcessInstance(processInstanceId);
    }

    @Override
    public KogitoProcessInstance startProcessInstance(String processInstanceId, String trigger) {
        return processRuntime.getKogitoProcessRuntime().startProcessInstance(processInstanceId, trigger);
    }

    @Override
    public void signalEvent(String type, Object event) {
        processRuntime.getKogitoProcessRuntime().signalEvent(type, event);
    }

    @Override
    public void signalEvent(String type, Object event, String processInstanceId) {
        processRuntime.getKogitoProcessRuntime().signalEvent(type, event, processInstanceId);
    }

    @Override
    public Collection<ProcessInstance> getProcessInstances() {
        return Collections.emptyList();
    }

    @Override
    public Collection<KogitoProcessInstance> getKogitoProcessInstances() {
        return Collections.emptyList();
    }

    @Override
    public WorkItemManager getWorkItemManager() {
        return (WorkItemManager) getKogitoWorkItemManager();
    }

    @Override
    public KogitoProcessInstance getProcessInstance(String processInstanceId) {
        return (KogitoProcessInstance) processRuntime.getProcessInstance(processInstanceId);
    }

    @Override
    public KogitoProcessInstance getProcessInstance(String processInstanceId, boolean readonly) {
        return null;
    }

    @Override
    public void abortProcessInstance(String processInstanceId) {

    }

    @Override
    public KogitoWorkItemManager getKogitoWorkItemManager() {
        return (KogitoWorkItemManager) this.processRuntime.getWorkItemManager();
    }

    @Override
    public void halt() {

    }

    @Override
    public EntryPoint getEntryPoint(String name) {
        return null;
    }

    @Override
    public Collection<? extends EntryPoint> getEntryPoints() {
        return Collections.emptyList();
    }

    @Override
    public QueryResults getQueryResults(String query, Object... arguments) {
        return null;
    }

    @Override
    public LiveQuery openLiveQuery(String query, Object[] arguments, ViewChangedEventListener listener) {
        return null;
    }

    @Override
    public String getEntryPointId() {
        return null;
    }

    @Override
    public FactHandle insert(Object object) {
        return null;
    }

    @Override
    public void retract(FactHandle handle) {

    }

    @Override
    public void delete(FactHandle handle) {

    }

    @Override
    public void delete(FactHandle handle, FactHandle.State fhState) {

    }

    @Override
    public void update(FactHandle handle, Object object) {

    }

    @Override
    public void update(FactHandle handle, Object object, String... modifiedProperties) {

    }

    @Override
    public FactHandle getFactHandle(Object object) {
        return null;
    }

    @Override
    public Object getObject(FactHandle factHandle) {
        return null;
    }

    @Override
    public Collection<? extends Object> getObjects() {
        return Collections.emptyList();
    }

    @Override
    public Collection<? extends Object> getObjects(ObjectFilter filter) {
        return Collections.emptyList();
    }

    @Override
    public <T extends FactHandle> Collection<T> getFactHandles() {
        return Collections.emptyList();
    }

    @Override
    public <T extends FactHandle> Collection<T> getFactHandles(ObjectFilter filter) {
        return Collections.emptyList();
    }

    @Override
    public long getFactCount() {
        return 0;
    }

    @Override
    public TimerService getTimerService() {
        return null;
    }

    @Override
    public Application getApplication() {
        return processRuntime.getApplication();
    }
}
