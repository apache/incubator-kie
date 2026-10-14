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
package org.kie.kogito.process.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jbpm.process.core.ProcessSupplier;
import org.jbpm.process.core.timer.DateTimeUtils;
import org.jbpm.process.core.timer.Timer;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.LightProcessRuntime;
import org.jbpm.process.instance.LightProcessRuntimeServiceProvider;
import org.jbpm.process.instance.ProcessRuntimeServiceProvider;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.kie.api.runtime.process.EventListener;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.kie.kogito.Application;
import org.kie.kogito.Model;
import org.kie.kogito.correlation.CorrelationService;
import org.kie.kogito.event.correlation.DefaultCorrelationService;
import org.kie.kogito.internal.process.runtime.KogitoNode;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.internal.utils.ConversionUtils;
import org.kie.kogito.jobs.DurationExpirationTime;
import org.kie.kogito.jobs.ExactExpirationTime;
import org.kie.kogito.jobs.ExpirationTime;
import org.kie.kogito.process.MutableProcessInstances;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessConfig;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstanceReadMode;
import org.kie.kogito.process.ProcessInstances;
import org.kie.kogito.process.ProcessInstancesFactory;
import org.kie.kogito.process.ProcessVersionResolver;
import org.kie.kogito.process.Signal;
import org.kie.kogito.process.SignalFactory;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.process.workitem.Policy;
import org.kie.kogito.process.workitem.WorkItemTransition;
import org.kie.kogito.signal.ProcessInstanceResolver;
import org.kie.kogito.signal.SignalManagerHub;

import static org.kie.kogito.internal.process.workitem.KogitoWorkItemHandlerFactory.findAllKogitoWorkItemHandlersRegistered;

@SuppressWarnings("unchecked")
public abstract class AbstractProcess<T extends Model> implements Process<T>, ProcessSupplier {

    protected final ProcessRuntimeServiceProvider services;
    protected ProcessInstancesFactory processInstancesFactory;
    protected MutableProcessInstances<T> instances;
    protected CompletionEventListener completionEventListener = new CompletionEventListener();

    protected Application app;

    protected boolean activated;
    protected KogitoProcessRuntime processRuntime;
    protected InternalProcessRuntime internalProcessRuntime;

    private org.kie.api.definition.process.Process process;
    private Lock processInitLock = new ReentrantLock();
    private CorrelationService correlations;
    private ProcessVersionResolver versionResolver;
    private ProcessInstanceResolver<T> processInstanceResolver;

    protected AbstractProcess() {
        this(null, new LightProcessRuntimeServiceProvider());
    }

    protected AbstractProcess(ProcessConfig config, Application application) {
        this(application, new ConfiguredProcessServices(config));
    }

    protected AbstractProcess(Application application, ProcessRuntimeServiceProvider services) {
        this(application, services, Collections.emptyList(), null, null, null);
    }

    protected AbstractProcess(Application app, Collection<KogitoWorkItemHandler> handlers, CorrelationService correlations) {
        this(app, handlers, correlations, null);
    }

    protected AbstractProcess(Application app, Collection<KogitoWorkItemHandler> handlers, CorrelationService correlations, ProcessInstancesFactory factory) {
        this(app, new ConfiguredProcessServices(app.config().get(ProcessConfig.class)), handlers, correlations, factory, app.config().get(ProcessConfig.class).versionResolver());

    }

    protected AbstractProcess(Application app, ProcessRuntimeServiceProvider services, Collection<KogitoWorkItemHandler> handlers, CorrelationService correlations, ProcessInstancesFactory factory,
            ProcessVersionResolver versionResolver) {
        this.app = app;
        this.services = services;
        this.processInstancesFactory = factory;
        this.correlations = Optional.ofNullable(correlations).orElseGet(() -> new DefaultCorrelationService());
        this.versionResolver = Optional.ofNullable(versionResolver).orElse(p -> get().getVersion());
        KogitoWorkItemManager workItemManager = services.getKogitoWorkItemManager();

        // loading defaults
        Collection<String> handlerIds = workItemManager.getHandlerIds();
        findAllKogitoWorkItemHandlersRegistered().stream().filter(e -> !handlerIds.contains(e.getName())).forEach(workItemHandler -> {
            workItemHandler.setApplication(app);
            workItemManager.registerWorkItemHandler(workItemHandler.getName(), workItemHandler);
        });
        // overriding kogito work item handlers
        for (KogitoWorkItemHandler workItemHandler : handlers) {
            workItemHandler.setApplication(app);
            workItemManager.registerWorkItemHandler(workItemHandler.getName(), workItemHandler);
        }
    }

    @Override
    public String id() {
        return get().getId();
    }

    @Override
    public String name() {
        return get().getName();
    }

    @Override
    public WorkItemTransition newTransition(WorkItem workItem, String transitionId, Map<String, Object> map, Policy... policy) {
        KogitoWorkItemHandler handler = getKogitoWorkItemHandler(workItem.getWorkItemHandlerName());
        return handler.newTransition(transitionId, workItem.getPhaseStatus(), map, policy);
    }

    @Override
    public KogitoWorkItemHandler getKogitoWorkItemHandler(String workItemHandlerName) {
        return services.getKogitoWorkItemManager().getKogitoWorkItemHandler(workItemHandlerName);
    }

    @Override
    public String version() {
        return versionResolver.apply(this);
    }

    @Override
    public String type() {
        return get().getType();
    }

    @Override
    public T createModel() {
        return null;
    }

    @Override
    public ProcessInstance<T> createInstance(String businessKey, Model m) {
        return createInstance(businessKey, m);
    }

    public abstract ProcessInstance<T> createInstance(WorkflowProcessInstance wpi);

    public abstract ProcessInstance<T> createReadOnlyInstance(WorkflowProcessInstance wpi);

    @Override
    public Collection<KogitoNode> findNodes(Predicate<KogitoNode> filter) {
        RuleFlowProcess p = (RuleFlowProcess) this.process;
        return p.getNodesRecursively().stream().map(n -> (KogitoNode) n).filter(filter).collect(Collectors.toList());
    }

    @Override
    public ProcessInstances<T> instances() {
        return instances;
    }

    @Override
    public CorrelationService correlations() {
        return correlations;
    }

    @Override
    public <S> void send(Signal<S> signal) {
        getProcessRuntime().signalEvent(signal.channel(), signal.payload());
    }

    public Process<T> configure() {
        registerListeners();
        if (isProcessFactorySet()) {
            this.instances = (MutableProcessInstances<T>) processInstancesFactory.createProcessInstances(this);
        } else {
            this.instances = new MapProcessInstances<>(this);
        }
        return this;
    }

    protected void registerListeners() {

    }

    public KogitoProcessRuntime getProcessRuntime() {
        return this.processRuntime;
    }

    @Override
    public void activate() {
        if (this.activated) {
            return;
        }
        this.internalProcessRuntime = LightProcessRuntime.of(app, Collections.singletonList(get()), services);
        this.processRuntime = createProcessRuntime().getKogitoProcessRuntime();
        configure();
        // this belongs to only for the work item handler so we keep within the context of the current process instance loaded in memory
        if (this.services.getSignalManager() instanceof SignalManagerHub signalManagerHub) {
            processInstanceResolver = new ProcessInstanceResolver<T>() {

                @Override
                public List<ProcessInstance<T>> waitingForEvents(String eventType) {
                    List<ProcessInstance<T>> list = instances.waitingForEventType(eventType, ProcessInstanceReadMode.MUTABLE)
                            .map(e -> (AbstractProcessInstance<T>) e)
                            .map(pi -> {
                                KogitoProcessRuntime runtime = getProcessRuntime();
                                KogitoProcessInstance instance = runtime.getProcessInstance(pi.id());
                                if (instance != null) {
                                    return (AbstractProcessInstance<T>) instance.unwrap();
                                }
                                return pi;
                            })
                            .map(e -> (ProcessInstance<T>) e)
                            .toList();
                    return list;
                }

                @Override
                public ProcessInstance<T> findById(String processInstanceId) {
                    Optional<ProcessInstance<T>> instance = instances.findById(processInstanceId);
                    return instance.orElse(null);
                }
            };
            signalManagerHub.addProcessInstanceResolver(processInstanceResolver);
        }

        this.activated = true;
    }

    @Override
    public void deactivate() {
        if (this.services.getSignalManager() instanceof SignalManagerHub signalManagerHub) {
            signalManagerHub.removeProcessInstanceResolver(processInstanceResolver);
        }
        this.internalProcessRuntime.dispose();
        this.internalProcessRuntime = null;
        this.activated = false;
    }

    protected ExpirationTime configureTimerInstance(Timer timer) {
        switch (timer.getTimeType()) {
            case Timer.TIME_CYCLE:
                // when using ISO date/time period is not set
                long[] repeatValues;
                if (DateTimeUtils.isCronExpression(timer.getDelay())) {
                    repeatValues = DateTimeUtils.parseCronAsRepeatableInterval(timer.getDelay());
                } else {
                    repeatValues = DateTimeUtils.parseRepeatableDateTime(timer.getDelay());
                }
                if (repeatValues.length == 3) {
                    int parsedReapedCount = (int) repeatValues[0];
                    if (parsedReapedCount <= -1) {
                        parsedReapedCount = Integer.MAX_VALUE;
                    }
                    return DurationExpirationTime.repeat(repeatValues[1], repeatValues[2], parsedReapedCount);
                } else if (repeatValues.length == 2) {
                    return DurationExpirationTime.repeat(repeatValues[0], repeatValues[1], Integer.MAX_VALUE);
                } else {
                    return DurationExpirationTime.repeat(repeatValues[0], repeatValues[0], Integer.MAX_VALUE);
                }

            case Timer.TIME_DURATION:
                long duration = DateTimeUtils.parseDuration(timer.getDelay());
                return DurationExpirationTime.repeat(duration);

            case Timer.TIME_DATE:

                return ExactExpirationTime.of(timer.getDate());

            default:
                throw new UnsupportedOperationException("Not supported timer definition");
        }
    }

    @Override
    public org.kie.api.definition.process.Process get() {
        processInitLock.lock();
        try {
            if (process == null) {
                process = process();
            }
        } finally {
            processInitLock.unlock();
        }
        return process;
    }

    protected abstract org.kie.api.definition.process.Process process();

    protected InternalProcessRuntime createProcessRuntime() {
        return internalProcessRuntime;
    }

    protected boolean isProcessFactorySet() {
        return processInstancesFactory != null;
    }

    public void setProcessInstancesFactory(ProcessInstancesFactory processInstancesFactory) {
        this.processInstancesFactory = processInstancesFactory;
    }

    public EventListener eventListener() {
        return completionEventListener;
    }

    protected class CompletionEventListener implements EventListener {

        public CompletionEventListener() {
            //Do nothing
        }

        @Override
        public void signalEvent(String type, Object event) {
            if (type.startsWith("processInstanceCompleted:")) {
                KogitoProcessInstance pi = (KogitoProcessInstance) event;
                String parentProcessInstanceId = pi.getParentProcessInstanceId();
                if (!id().equals(pi.getProcessId()) && ConversionUtils.isNotEmpty(parentProcessInstanceId)) {
                    //checking if parent is present in ProcessInstanceManager (in-memory local transaction)
                    KogitoProcessInstance parentKogitoProcessInstance = services.getProcessInstanceManager().getProcessInstance(parentProcessInstanceId);
                    if (parentKogitoProcessInstance != null) {
                        parentKogitoProcessInstance.signalEvent(type, event);
                    } else {
                        //if not present ProcessInstanceManager try to signal instance from repository
                        instances().findById(pi.getParentProcessInstanceId()).ifPresent(p -> p.send(SignalFactory.of(type, event)));
                    }
                }
            }
        }

        @Override
        public String[] getEventTypes() {
            return new String[0];
        }
    }

    @Override
    public int hashCode() {
        return id().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AbstractProcess other = (AbstractProcess) obj;
        return Objects.equals(id(), other.id());
    }
}
