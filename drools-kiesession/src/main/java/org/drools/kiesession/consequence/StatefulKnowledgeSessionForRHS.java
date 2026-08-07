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
package org.drools.kiesession.consequence;

import org.drools.base.RuleBase;
import org.drools.base.beliefsystem.Mode;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.factmodel.traits.Thing;
import org.drools.base.factmodel.traits.TraitableBean;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.core.RuleSessionConfiguration;
import org.drools.core.SessionConfiguration;
import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.common.ActivationsManager;
import org.drools.core.common.EndOperationListener;
import org.drools.core.common.EventSupport;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.core.common.InternalWorkingMemoryActions;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.NodeMemories;
import org.drools.core.common.ObjectStore;
import org.drools.core.common.ObjectTypeConfigurationRegistry;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.SegmentMemorySupport;
import org.drools.core.event.AgendaEventSupport;
import org.drools.core.event.RuleEventListenerSupport;
import org.drools.core.event.RuleRuntimeEventSupport;
import org.drools.base.phreak.PropagationEntry;
import org.drools.core.phreak.RuleNetworkEvaluator;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.rule.accessor.FactHandleFactory;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.core.runtime.process.InternalProcessRuntime;
import org.drools.core.time.TimerService;
import org.drools.util.bitmask.BitMask;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.kie.api.KieBase;
import org.kie.api.command.Command;
import org.kie.api.event.kiebase.KieBaseEventListener;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.logger.KieRuntimeLogger;
import org.kie.api.runtime.Calendars;
import org.kie.api.runtime.Channel;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.Globals;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.ObjectFilter;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.FactHandle.State;
import org.kie.api.runtime.rule.LiveQuery;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.ViewChangedEventListener;
import org.kie.api.time.SessionClock;
import org.kie.internal.event.rule.RuleEventListener;
import org.kie.internal.process.CorrelationKey;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.Lock;

/**
 * Wrapper of ReteEvaluator so to intercept call from RHS internal Drools execution and proxy or delegate method call as appropriate.
 */
public class StatefulKnowledgeSessionForRHS
        implements KieSession,
                   InternalWorkingMemoryActions,
                   EventSupport,
                   KieRuntime,
                   Externalizable {

    protected StatefulKnowledgeSessionImpl delegate;

    public StatefulKnowledgeSessionForRHS(StatefulKnowledgeSessionImpl reteEvaluator) {
        super();
        this.delegate = reteEvaluator;
    }

    /**
     * This should be used just by deserialization. Please avoid using this empty constructor in your code.
     */
    public StatefulKnowledgeSessionForRHS() {
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        delegate = (StatefulKnowledgeSessionImpl) in.readObject();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(delegate);
    }

    @Override
    public QueryResults getQueryResults(String queryName, Object... arguments) {
        return delegate.getQueryResultsFromRHS(queryName, arguments);
    }

    // -- then just delegate

    public KieRuntimeLogger getLogger() {
        return delegate.getLogger();
    }

    public void setLogger(KieRuntimeLogger logger) {
        delegate.setLogger(logger);
    }

    public <T> T getKieRuntime(Class<T> cls) {
        return delegate.getKieRuntime(cls);
    }

    public <T> T createRuntimeService(Class<T> cls) {
        return delegate.createRuntimeService(cls);
    }

    public void addEventListener(ProcessEventListener listener) {
        delegate.addEventListener(listener);
    }

    public Collection<ProcessEventListener> getProcessEventListeners() {
        return delegate.getProcessEventListeners();
    }

    public void removeEventListener(ProcessEventListener listener) {
        delegate.removeEventListener(listener);
    }

    public KieBase getKieBase() {
        return delegate.getKieBase();
    }

    public boolean isAlive() {
        return delegate.isAlive();
    }

    public void destroy() {
        delegate.destroy();
    }

    public void update(FactHandle factHandle) {
        delegate.update(factHandle);
    }

    public void abortProcessInstance(String id) {
        delegate.abortProcessInstance(id);
    }

    public void signalEvent(String type, Object event) {
        delegate.signalEvent(type, event);
    }

    public void signalEvent(String type, Object event, String processInstanceId) {
        delegate.signalEvent(type, event, processInstanceId);
    }

    public Globals getGlobals() {
        return delegate.getGlobals();
    }

    public <T> T execute(Command<T> command) {
        return delegate.execute(command);
    }

    public LiveQuery openLiveQuery(String query, Object[] arguments, ViewChangedEventListener listener) {
        return delegate.openLiveQuery(query, arguments, listener);
    }

    public void reset(int handleId, long handleCounter, long propagationCounter) {
        delegate.reset(handleId, handleCounter, propagationCounter);
    }

    public void addEventListener(RuleEventListener listener) {
        delegate.addEventListener(listener);
    }

    public void removeEventListener(RuleEventListener listener) {
        delegate.removeEventListener(listener);
    }

    public int getId() {
        return delegate.getId();
    }

    public void fireUntilHalt() {
        delegate.fireUntilHalt();
    }

    public void fireUntilHalt(AgendaFilter agendaFilter) {
        delegate.fireUntilHalt(agendaFilter);
    }

    public RuleRuntimeEventSupport getRuleRuntimeEventSupport() {
        return delegate.getRuleRuntimeEventSupport();
    }

    @Override
    public RuleEventListenerSupport getRuleEventSupport() {
        return delegate.getRuleEventSupport();
    }

    @Override
    public AgendaEventSupport getAgendaEventSupport() {
        return delegate.getAgendaEventSupport();
    }

    public ProcessInstance createProcessInstance(String processId, Map<String, Object> parameters) {
        return delegate.createProcessInstance(processId, parameters);
    }

    public ProcessInstance startProcessInstance(String processInstanceId) {
        return delegate.startProcessInstance(processInstanceId);
    }

    public ProcessInstance createProcessInstance(String processId, CorrelationKey correlationKey,
                                                 Map<String, Object> parameters) {
        return delegate.createProcessInstance(processId, correlationKey, parameters);
    }

    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    public ProcessInstance getProcessInstance(CorrelationKey correlationKey) {
        return delegate.getProcessInstance(correlationKey);
    }

    public int hashCode() {
        return delegate.hashCode();
    }

    public ProcessInstance startProcess(String processId, CorrelationKey correlationKey,
                                        Map<String, Object> parameters) {
        return delegate.startProcess(processId, correlationKey, parameters);
    }

    public void registerChannel(String name, Channel channel) {
        delegate.registerChannel(name, channel);
    }

    public void unregisterChannel(String name) {
        delegate.unregisterChannel(name);
    }

    public void setEndOperationListener(EndOperationListener listener) {
        delegate.setEndOperationListener(listener);
    }

    public String toString() {
        return delegate.toString();
    }

    public void addEventListener(KieBaseEventListener listener) {
        delegate.addEventListener(listener);
    }

    @Override
    public void enableTMS() {
        delegate.enableTMS();
    }

    @Override
    public boolean isTMSEnabled() {
        return delegate.isTMSEnabled();
    }

    public FactHandle insert(Object object) {
        return delegate.insert(object);
    }

    public void submit(AtomicAction action) {
        delegate.submit(action);
    }

    public void removeEventListener(KieBaseEventListener listener) {
        delegate.removeEventListener(listener);
    }

    public void addEventListener(RuleRuntimeEventListener listener) {
        delegate.addEventListener(listener);
    }

    public Collection<KieBaseEventListener> getKieBaseEventListeners() {
        return delegate.getKieBaseEventListeners();
    }

    public FactHandle insert(Object object, boolean dynamic) {
        return delegate.insert(object, dynamic);
    }

    public void update(FactHandle handle, Object object, BitMask mask, Class<?> modifiedClass, InternalMatch internalMatch) {
        delegate.update(handle, object, mask, modifiedClass, internalMatch);
    }

    public void removeEventListener(RuleRuntimeEventListener listener) {
        delegate.removeEventListener(listener);
    }

    public void delete(FactHandle factHandle, RuleImpl rule, TerminalNode terminalNode, State fhState) {
        delegate.delete(factHandle, rule, terminalNode, fhState);
    }

    public void delete(FactHandle handle, RuleImpl rule, TerminalNode terminalNode) {
        delegate.delete(handle, rule, terminalNode);
    }

    @Override
    public FactHandle insert(Object object, boolean dynamic, RuleImpl rule, TerminalNode terminalNode) {
        return delegate.insert(object, dynamic, rule, terminalNode);
    }

    @Override
    public FactHandle insertAsync(Object object) {
        return delegate.insert(object);
    }

    public Collection<RuleRuntimeEventListener> getRuleRuntimeEventListeners() {
        return delegate.getRuleRuntimeEventListeners();
    }

    public void addEventListener(AgendaEventListener listener) {
        delegate.addEventListener(listener);
    }

    public String getEntryPointId() {
        return delegate.getEntryPointId();
    }

    public void retract(FactHandle handle) {
        delegate.retract(handle);
    }

    public InternalAgenda getAgenda() {
        return delegate.getAgenda();
    }

    public long getIdentifier() {
        return delegate.getIdentifier();
    }

    public void setIdentifier(long id) {
        delegate.setIdentifier(id);
    }

    public void setRuleRuntimeEventSupport(RuleRuntimeEventSupport workingMemoryEventSupport) {
        delegate.setRuleRuntimeEventSupport(workingMemoryEventSupport);
    }

    public void removeEventListener(AgendaEventListener listener) {
        delegate.removeEventListener(listener);
    }

    public void updateTraits(InternalFactHandle h, BitMask mask, Class<?> modifiedClass, InternalMatch internalMatch) {
        delegate.updateTraits(h, mask, modifiedClass, internalMatch);
    }

    public void update(FactHandle handle, Object object) {
        delegate.update(handle, object);
    }

    public void update(FactHandle handle, Object object, String... modifiedProperties) {
        delegate.update(handle, object, modifiedProperties);
    }

    public void setAgendaEventSupport(AgendaEventSupport agendaEventSupport) {
        delegate.setAgendaEventSupport(agendaEventSupport);
    }

    public void setGlobal(String identifier, Object value) {
        delegate.setGlobal(identifier, value);
    }

    public void reset() {
        delegate.reset();
    }

    public <T, K, X extends TraitableBean> Thing<K> shed(InternalMatch internalMatch, TraitableBean<K, X> core,
                                                         Class<T> trait) {
        return delegate.shed(internalMatch, core, trait);
    }

    public <T extends Memory> T getNodeMemory(MemoryFactory<T> node) {
        return delegate.getNodeMemory(node);
    }

    public FactHandleFactory getHandleFactory() {
        return delegate.getHandleFactory();
    }

    public Collection<AgendaEventListener> getAgendaEventListeners() {
        return delegate.getAgendaEventListeners();
    }

    public void clearNodeMemory(MemoryFactory node) {
        delegate.clearNodeMemory(node);
    }

    public <T, K> T don(InternalMatch internalMatch, K core, Collection<Class<? extends Thing>> traits, boolean b,
                        Mode[] modes) {
        return delegate.don(internalMatch, core, traits, b, modes);
    }

    public NodeMemories getNodeMemories() {
        return delegate.getNodeMemories();
    }
    
    public SegmentMemorySupport getSegmentMemorySupport() {
        return delegate.getSegmentMemorySupport();
    }

    public long getNextPropagationIdCounter() {
        return delegate.getNextPropagationIdCounter();
    }

    public ObjectStore getObjectStore() {
        return delegate.getObjectStore();
    }

    public <T, K> T don(InternalMatch internalMatch, K core, Class<T> trait, boolean b, Mode[] modes) {
        return delegate.don(internalMatch, core, trait, b, modes);
    }

    public void delete(FactHandle handle) {
        delegate.delete(handle);
    }

    public void dispose() {
        delegate.dispose();
    }

    public FactHandleFactory getFactHandleFactory() {
        return delegate.getFactHandleFactory();
    }

    public Object getGlobal(String identifier) {
        return delegate.getGlobal(identifier);
    }

    public EntryPointId getEntryPoint() {
        return delegate.getEntryPoint();
    }

    @Override
    public ReteEvaluator getReteEvaluator() {
        return delegate.getReteEvaluator();
    }

    public EntryPointNode getEntryPointNode() {
        return delegate.getEntryPointNode();
    }

    public WorkingMemoryEntryPoint getEntryPoint(String name) {
        return delegate.getEntryPoint(name);
    }

    public void delete(FactHandle handle, State fhState) {
        delegate.delete(handle, fhState);
    }

    public Environment getEnvironment() {
        return delegate.getEnvironment();
    }

    public void setGlobalResolver(GlobalResolver globalResolver) {
        delegate.setGlobalResolver(globalResolver);
    }

    public GlobalResolver getGlobalResolver() {
        return delegate.getGlobalResolver();
    }
    
    @Override
    public RuleNetworkEvaluator getRuleNetworkEvaluator() {
        return delegate.getRuleNetworkEvaluator();
    }

    @Override
    public ActivationsManager getActivationsManager() {
        return delegate.getActivationsManager();
    }

    public InternalKnowledgeBase getKnowledgeBase() {
        return delegate.getKnowledgeBase();
    }

    public Lock getLock() {
        return delegate.getLock();
    }

    public boolean isSequential() {
        return delegate.isSequential();
    }

    public int fireAllRules() {
        return delegate.fireAllRules();
    }

    public ObjectTypeConfigurationRegistry getObjectTypeConfigurationRegistry() {
        return delegate.getObjectTypeConfigurationRegistry();
    }

    public InternalFactHandle getInitialFactHandle() {
        return delegate.getInitialFactHandle();
    }

    public int fireAllRules(AgendaFilter agendaFilter) {
        return delegate.fireAllRules(agendaFilter);
    }

    public Calendars getCalendars() {
        return delegate.getCalendars();
    }

    public TimerService getTimerService() {
        return delegate.getTimerService();
    }

    public int fireAllRules(int fireLimit) {
        return delegate.fireAllRules(fireLimit);
    }

    public InternalKnowledgeRuntime getKnowledgeRuntime() {
        return delegate.getKnowledgeRuntime();
    }

    public int fireAllRules(AgendaFilter agendaFilter, int fireLimit) {
        return delegate.fireAllRules(agendaFilter, fireLimit);
    }

    public Map<String, Channel> getChannels() {
        return delegate.getChannels();
    }

    public Collection<? extends EntryPoint> getEntryPoints() {
        return delegate.getEntryPoints();
    }

    public Object getObject(FactHandle handle) {
        return delegate.getObject(handle);
    }

    public RuleSessionConfiguration getRuleSessionConfiguration() {
        return delegate.getRuleSessionConfiguration();
    }

    public SessionConfiguration getSessionConfiguration() {
        return delegate.getSessionConfiguration();
    }

    public Collection<? extends Object> getObjects() {
        return delegate.getObjects();
    }

    public void startBatchExecution() {
        delegate.startBatchExecution();
    }

    public void endBatchExecution() {
        delegate.endBatchExecution();
    }

    public void startOperation(InternalOperationType operationType) {
        delegate.startOperation(operationType);
    }

    public InternalFactHandle getFactHandle(Object object) {
        return delegate.getFactHandle(object);
    }

    public Iterator<?> iterateObjects() {
        return delegate.iterateObjects();
    }

    public void endOperation(InternalOperationType operationType) {
        delegate.endOperation(operationType);
    }

    public Iterator<?> iterateObjects(ObjectFilter filter) {
        return delegate.iterateObjects(filter);
    }

    public Collection<? extends Object> getObjects(ObjectFilter filter) {
        return delegate.getObjects(filter);
    }

    @Override
    public long getCurrentTime() {
        return this.delegate.getCurrentTime();
    }

    @Override
    public RuleBase getRuleBase() {
        return delegate.getRuleBase();
    }

    public long getIdleTime() {
        return delegate.getIdleTime();
    }

    public <T extends FactHandle> Collection<T> getFactHandles() {
        return delegate.getFactHandles();
    }

    public long getTimeToNextJob() {
        return delegate.getTimeToNextJob();
    }

    public Iterator<InternalFactHandle> iterateFactHandles() {
        return delegate.iterateFactHandles();
    }

    public <T extends FactHandle> Collection<T> getFactHandles(ObjectFilter filter) {
        return delegate.getFactHandles(filter);
    }

    public void updateEntryPointsCache() {
        delegate.updateEntryPointsCache();
    }

    public Iterator<InternalFactHandle> iterateFactHandles(ObjectFilter filter) {
        return delegate.iterateFactHandles(filter);
    }

    public long getFactCount() {
        return delegate.getFactCount();
    }

    public long getTotalFactCount() {
        return delegate.getTotalFactCount();
    }

    public void setFocus(String focus) {
        delegate.setFocus(focus);
    }

    public InternalProcessRuntime getProcessRuntime() {
        return delegate.getProcessRuntime();
    }

    public InternalProcessRuntime internalGetProcessRuntime() {
        return delegate.internalGetProcessRuntime();
    }

    public void closeLiveQuery(InternalFactHandle factHandle) {
        delegate.closeLiveQuery(factHandle);
    }

    public void addPropagation(PropagationEntry<ReteEvaluator> propagationEntry) {
        delegate.addPropagation(propagationEntry);
    }

    public void flushPropagations() {
        delegate.flushPropagations();
    }

    public void activate() {
        delegate.activate();
    }

    public void deactivate() {
        delegate.deactivate();
    }

    public boolean tryDeactivate() {
        return delegate.tryDeactivate();
    }

    public Iterator<? extends PropagationEntry<ReteEvaluator>> getActionsIterator() {
        return delegate.getActionsIterator();
    }

    public void removeGlobal(String identifier) {
        delegate.removeGlobal(identifier);
    }

    public void notifyWaitOnRest() {
        delegate.notifyWaitOnRest();
    }

    public void cancelActivation(InternalMatch internalMatch, boolean declarativeAgenda) {
        delegate.cancelActivation(internalMatch, declarativeAgenda);
    }

    public void clearAgenda() {
        delegate.clearAgenda();
    }

    public void clearAgendaGroup(String group) {
        delegate.clearAgendaGroup(group);
    }

    public void clearActivationGroup(String group) {
        delegate.clearActivationGroup(group);
    }

    public void clearRuleFlowGroup(String group) {
        delegate.clearRuleFlowGroup(group);
    }

    public ProcessInstance startProcess(String processId) {
        return delegate.startProcess(processId);
    }

    public ProcessInstance startProcess(String processId, Map<String, Object> parameters) {
        return delegate.startProcess(processId, parameters);
    }

    public ProcessInstance startProcess(String processId, AgendaFilter agendaFilter) {
        return delegate.startProcess(processId, agendaFilter);
    }

    public ProcessInstance startProcess(String processId, Map<String, Object> parameters, AgendaFilter agendaFilter) {
        return delegate.startProcess(processId, parameters, agendaFilter);
    }

    public Collection<ProcessInstance> getProcessInstances() {
        return delegate.getProcessInstances();
    }

    public ProcessInstance getProcessInstance(String id) {
        return delegate.getProcessInstance(id);
    }

    public ProcessInstance getProcessInstance(String id, boolean readOnly) {
        return delegate.getProcessInstance(id, readOnly);
    }

    public WorkItemManager getWorkItemManager() {
        return delegate.getWorkItemManager();
    }

    public void halt() {
        delegate.halt();
    }

    public SessionClock getSessionClock() {
        return delegate.getSessionClock();
    }

    public ProcessInstance startProcessFromNodeIds(String processId, Map<String, Object> params, String... nodeIds) {
        return delegate.startProcessFromNodeIds(processId, params, nodeIds);
    }
}
