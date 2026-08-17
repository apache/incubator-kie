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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.phreak.actions.AbstractPropagationEntry;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.WorkingMemoryAction;
import org.drools.core.time.TimerService;
import org.drools.core.time.impl.CommandServiceTimerJobFactoryManager;
import org.drools.core.time.impl.ThreadSafeTrackableTimeJobFactoryManager;
import org.jbpm.process.core.event.EventFilter;
import org.jbpm.process.core.event.EventTypeFilter;
import org.jbpm.process.instance.event.DefaultSignalManagerFactory;
import org.jbpm.process.instance.event.KogitoProcessEventSupportImpl;
import org.jbpm.process.instance.impl.DefaultProcessInstanceManagerFactory;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.workflow.core.impl.DataAssociation;
import org.jbpm.workflow.core.impl.NodeIoHelper;
import org.jbpm.workflow.core.node.EventTrigger;
import org.jbpm.workflow.core.node.StartNode;
import org.jbpm.workflow.core.node.Trigger;
import org.kie.api.command.ExecutableCommand;
import org.kie.api.definition.process.Node;
import org.kie.api.definition.process.Process;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent;
import org.kie.api.runtime.Context;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.EventListener;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.internal.command.RegistryContext;
import org.kie.internal.process.CorrelationKey;
import org.kie.internal.runtime.StatefulKnowledgeSession;
import org.kie.kogito.Application;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.jobs.JobsService;
import org.kie.kogito.services.identity.NoOpIdentityProvider;
import org.kie.kogito.services.jobs.impl.LegacyInMemoryJobService;
import org.kie.kogito.services.uow.CollectingUnitOfWorkFactory;
import org.kie.kogito.services.uow.DefaultUnitOfWorkManager;
import org.kie.kogito.signal.SignalManager;
import org.kie.kogito.uow.UnitOfWorkManager;

import static org.jbpm.ruleflow.core.Metadata.TRIGGER_MAPPING_INPUT;

public class ProcessRuntimeImpl extends AbstractProcessRuntime {

    private InternalKnowledgeRuntime kruntime;
    private ProcessInstanceManager processInstanceManager;
    private UnitOfWorkManager unitOfWorkManager;

    public ProcessRuntimeImpl(Application application, InternalWorkingMemory workingMemory) {
        super(application);
        TimerService timerService = workingMemory.getTimerService();
        if (!(timerService.getTimerJobFactoryManager() instanceof CommandServiceTimerJobFactoryManager)) {
            timerService.setTimerJobFactoryManager(new ThreadSafeTrackableTimeJobFactoryManager());
        }

        this.kruntime = workingMemory.getKnowledgeRuntime();
        initProcessInstanceManager();
        initSignalManager();
        unitOfWorkManager = new DefaultUnitOfWorkManager(new CollectingUnitOfWorkFactory());
        jobService = new LegacyInMemoryJobService(kogitoProcessRuntime, unitOfWorkManager);
        this.processEventSupport = new KogitoProcessEventSupportImpl(new NoOpIdentityProvider());
        if (isActive()) {
            initProcessEventListeners();
            initStartTimers();
        }
        initProcessActivationListener();
    }

    public void initStartTimers() {
        initStartTimers(kruntime.getKieBase().getProcesses(), kruntime);
    }

    private void initProcessInstanceManager() {
        processInstanceManager = new DefaultProcessInstanceManagerFactory().createProcessInstanceManager(kruntime);
    }

    private void initSignalManager() {
        signalManager = new DefaultSignalManagerFactory().createSignalManager(kruntime);
    }

    @Override
    public KogitoProcessRuntime getKogitoProcessRuntime() {
        return kogitoProcessRuntime;
    }

    @Override
    public ProcessInstance startProcess(String processId) {
        return kogitoProcessRuntime.startProcess(processId, null, null, null);
    }

    @Override
    public ProcessInstance startProcess(String processId, Map<String, Object> parameters) {
        return kogitoProcessRuntime.startProcess(processId, parameters, null, null);
    }

    public ProcessInstance startProcess(String processId, Map<String, Object> parameters, String trigger) {
        return kogitoProcessRuntime.startProcess(processId, parameters, trigger, null);
    }

    @Override
    public ProcessInstance startProcess(String processId, AgendaFilter agendaFilter) {
        return kogitoProcessRuntime.startProcess(processId, null, null, agendaFilter);
    }

    @Override
    public ProcessInstance startProcess(String processId, Map<String, Object> parameters, AgendaFilter agendaFilter) {
        return kogitoProcessRuntime.startProcess(processId, parameters, null, agendaFilter);
    }

    @Override
    public ProcessInstance startProcessFromNodeIds(String s, Map<String, Object> map, String... strings) {
        throw new UnsupportedOperationException("org.jbpm.process.instance.ProcessRuntimeImpl.startProcessFromNodeIds -> TODO");

    }

    @Override
    public KogitoProcessInstance createProcessInstance(String processId,
            Map<String, Object> parameters) {
        return createProcessInstance(processId, null, parameters);
    }

    @Override
    public ProcessInstance startProcessInstance(String l) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProcessInstance startProcess(String processId, CorrelationKey correlationKey, Map<String, Object> parameters) {
        ProcessInstance processInstance = createProcessInstance(processId, correlationKey, parameters);
        if (processInstance != null) {
            return startProcessInstance(processInstance.getId());
        }
        return null;
    }

    @Override
    public KogitoProcessInstance createProcessInstance(String processId, CorrelationKey correlationKey, Map<String, Object> parameters) {
        final Process process = kruntime.getKieBase().getProcess(processId);
        if (process == null) {
            throw new IllegalArgumentException("Unknown process ID: " + processId);
        }
        return startProcess(process, correlationKey, parameters);
    }

    @Override
    public ProcessInstance startProcessFromNodeIds(String s, CorrelationKey correlationKey, Map<String, Object> map, String... strings) {
        throw new UnsupportedOperationException("org.jbpm.process.instance.ProcessRuntimeImpl.startProcessFromNodeIds -> TODO");

    }

    @Override
    public ProcessInstance getProcessInstance(CorrelationKey correlationKey) {
        throw new UnsupportedOperationException("org.jbpm.process.instance.ProcessRuntimeImpl.getProcessInstance -> TODO");

    }

    private org.jbpm.process.instance.ProcessInstance startProcess(Process process, CorrelationKey correlationKey, Map<String, Object> parameters) {
        ProcessInstanceFactory conf = ProcessInstanceFactoryRegistry.INSTANCE.getProcessInstanceFactory(process);
        if (conf == null) {
            throw new IllegalArgumentException("Illegal process type: " + process.getClass());
        }
        return conf.createProcessInstance(process,
                correlationKey,
                kruntime,
                parameters);
    }

    @Override
    public ProcessInstanceManager getProcessInstanceManager() {
        return processInstanceManager;
    }

    @Override
    public JobsService getJobsService() {
        return jobService;
    }

    @Override
    public SignalManager getSignalManager() {
        return signalManager;
    }

    @Override
    public Collection<ProcessInstance> getProcessInstances() {
        return (Collection<ProcessInstance>) (Object) processInstanceManager.getProcessInstances();
    }

    @Override
    public void abortProcessInstance(String l) {
        throw new UnsupportedOperationException();
    }

    public KogitoProcessInstance getProcessInstance(String id) {
        return getProcessInstance(id, false);
    }

    public KogitoProcessInstance getProcessInstance(String id, boolean readOnly) {
        return processInstanceManager.getProcessInstance(id, readOnly);
    }

    public void removeProcessInstance(KogitoProcessInstance processInstance) {
        processInstanceManager.removeProcessInstance(processInstance);
    }

    public void initProcessEventListeners() {
        for (Process process : kruntime.getKieBase().getProcesses()) {
            initProcessEventListener(process);
        }
    }

    public void removeProcessEventListeners() {
        for (Process process : kruntime.getKieBase().getProcesses()) {
            removeProcessEventListener(process);
        }
    }

    private void removeProcessEventListener(Process process) {
        if (process instanceof RuleFlowProcess) {
            String type = (String) ((RuleFlowProcess) process).getRuntimeMetaData().get("StartProcessEventType");
            StartProcessEventListener listener = (StartProcessEventListener) ((RuleFlowProcess) process).getRuntimeMetaData().get("StartProcessEventListener");
            if (type != null && listener != null) {
                signalManager.removeEventListener(type, listener);
            }
        }
    }

    private void initProcessEventListener(Process process) {
        if (process instanceof RuleFlowProcess) {
            for (Node node : ((RuleFlowProcess) process).getNodes()) {
                if (node instanceof StartNode) {
                    StartNode startNode = (StartNode) node;
                    if (startNode != null) {
                        List<Trigger> triggers = startNode.getTriggers();
                        if (triggers != null) {
                            for (Trigger trigger : triggers) {
                                if (trigger instanceof EventTrigger) {
                                    final List<EventFilter> filters = ((EventTrigger) trigger).getEventFilters();
                                    String type = null;
                                    for (EventFilter filter : filters) {
                                        if (filter instanceof EventTypeFilter) {
                                            type = ((EventTypeFilter) filter).getType();
                                        }
                                    }
                                    StartProcessEventListener listener = new StartProcessEventListener(startNode, trigger, process.getId(), filters);
                                    signalManager.addEventListener(type, listener);
                                    ((RuleFlowProcess) process).getRuntimeMetaData().put("StartProcessEventType", type);
                                    ((RuleFlowProcess) process).getRuntimeMetaData().put("StartProcessEventListener", listener);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void initProcessActivationListener() {
        kruntime.addEventListener(new DefaultAgendaEventListener() {
            @Override
            public void matchCreated(MatchCreatedEvent event) {
                String ruleFlowGroup = ((RuleImpl) event.getMatch().getRule()).getRuleFlowGroup();
                if ("DROOLS_SYSTEM".equals(ruleFlowGroup)) {
                    // new activations of the rule associate with a state node
                    // signal process instances of that state node
                    String ruleName = event.getMatch().getRule().getName();
                    if (ruleName.startsWith("RuleFlowStateNode-")) {
                        int index = ruleName.indexOf('-',
                                18);
                        index = ruleName.indexOf('-',
                                index + 1);
                        String eventType = ruleName.substring(0,
                                index);

                        ((ReteEvaluator) kruntime).addPropagation(new SignalManagerSignalAction(eventType, event));
                    } else if (ruleName.startsWith("RuleFlowStateEventSubProcess-")
                            || ruleName.startsWith("RuleFlowStateEvent-")
                            || ruleName.startsWith("RuleFlow-Milestone-")
                            || ruleName.startsWith("RuleFlow-AdHocComplete-")
                            || ruleName.startsWith("RuleFlow-AdHocActivate-")) {
                        ((ReteEvaluator) kruntime).addPropagation(new SignalManagerSignalAction(ruleName, event));
                    }
                } else {
                    String ruleName = event.getMatch().getRule().getName();
                    if (ruleName.startsWith("RuleFlow-Start-")) {
                        String processId = ruleName.replace("RuleFlow-Start-", "");

                        startProcessWithParamsAndTrigger(processId, null, "conditional");
                    }
                }
            }
        });

        kruntime.addEventListener(new DefaultAgendaEventListener() {
            @Override
            public void afterRuleFlowGroupDeactivated(final RuleFlowGroupDeactivatedEvent event) {
                if (kruntime instanceof StatefulKnowledgeSession) {
                    signalManager.signalEvent("RuleFlowGroup_" + event.getRuleFlowGroup().getName() + "_" + ((StatefulKnowledgeSession) kruntime).getIdentifier(),
                            null);
                } else {
                    signalManager.signalEvent("RuleFlowGroup_" + event.getRuleFlowGroup().getName(), null);
                }
            }
        });
    }

    private void startProcessWithParamsAndTrigger(String processId, Map<String, Object> params, String type) {

        startProcess(processId, params, type);
    }

    @Override
    public WorkItemManager getWorkItemManager() {
        return kruntime.getWorkItemManager();
    }

    @Override
    public UnitOfWorkManager getUnitOfWorkManager() {
        return unitOfWorkManager;
    }

    @Override
    public void signalEvent(String type, Object event) {
        signalManager.signalEvent(type, event);
    }

    @Override
    public void signalEvent(String s, Object o, String l) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void dispose() {
        this.processEventSupport.reset();
        kruntime = null;
    }

    @Override
    public void clearProcessInstances() {
        this.processInstanceManager.clearProcessInstances();
    }

    @Override
    public void clearProcessInstancesState() {
        this.processInstanceManager.clearProcessInstancesState();
    }

    public boolean isActive() {
        Boolean active = (Boolean) kruntime.getEnvironment().get("Active");
        if (active == null) {
            return true;
        }

        return active.booleanValue();
    }

    @Override
    public InternalKnowledgeRuntime getInternalKieRuntime() {
        return this.kruntime;
    }

    private class StartProcessEventListener implements EventListener {

        private String processId;
        private List<EventFilter> eventFilters;
        private StartNode startNode;
        private Trigger trigger;

        public StartProcessEventListener(StartNode startNode, Trigger trigger, String processId, List<EventFilter> eventFilters) {
            this.processId = processId;
            this.eventFilters = eventFilters;
            this.trigger = trigger;
            this.startNode = startNode;
        }

        @Override
        public String[] getEventTypes() {
            return new String[0];
        }

        @Override
        public void signalEvent(final String type, Object event) {
            for (EventFilter filter : eventFilters) {
                if (!filter.acceptsEvent(type, event, varName -> null)) {
                    return;
                }
            }
            Map<String, Object> outputSet = new HashMap<>();
            for (Map.Entry<String, String> entry : trigger.getInMappings().entrySet()) {
                outputSet.put(entry.getKey(), entry.getKey());
            }

            // data association needs to be corrected as it is not input mapping but output mapping
            boolean eventFound = false;
            for (DataAssociation dataAssociation : trigger.getInAssociations()) {
                if ("event".equals(dataAssociation.getSources().get(0).getLabel())) {
                    eventFound = true;
                }
            }

            if (!eventFound && !trigger.getInAssociations().isEmpty()) {
                String inputLabel = (String) startNode.getMetaData(TRIGGER_MAPPING_INPUT);
                outputSet.put(inputLabel, event);
            } else {
                outputSet.put("event", event);
            }

            Map<String, Object> parameters = NodeIoHelper.processOutputs(trigger.getInAssociations(), key -> outputSet.get(key));
            startProcessWithParamsAndTrigger(processId, parameters, type);

        }
    }

    private class StartProcessWithTypeCommand implements ExecutableCommand<Void> {

        private static final long serialVersionUID = -8890906804846111698L;

        private String processId;
        private Map<String, Object> params;
        private String type;

        private StartProcessWithTypeCommand(String processId, Map<String, Object> params, String type) {
            this.processId = processId;
            this.params = params;
            this.type = type;
        }

        @Override
        public Void execute(Context context) {
            KieSession ksession = ((RegistryContext) context).lookup(KieSession.class);
            ((ProcessRuntimeImpl) ((InternalKnowledgeRuntime) ksession).getProcessRuntime()).startProcess(processId,
                    params, type);

            return null;
        }
    }

    public class SignalManagerSignalAction extends AbstractPropagationEntry<ReteEvaluator> implements WorkingMemoryAction {

        private String type;
        private Object event;

        public SignalManagerSignalAction(String type, Object event) {
            this.type = type;
            this.event = event;
        }

        @Override
        public void internalExecute(ReteEvaluator reteEvaluator) {
            signalEvent(type, event);
        }
    }
}
