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

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.WorkflowProcess;
import org.jbpm.workflow.instance.NodeInstance;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.jbpm.workflow.instance.impl.NodeInstanceImpl;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.jbpm.workflow.instance.node.WorkItemNodeInstance;
import org.kie.api.runtime.process.ProcessRuntime;
import org.kie.internal.process.CorrelationAwareProcessRuntime;
import org.kie.internal.process.CorrelationKey;
import org.kie.internal.process.CorrelationProperty;
import org.kie.kogito.Model;
import org.kie.kogito.Models;
import org.kie.kogito.correlation.CompositeCorrelation;
import org.kie.kogito.correlation.Correlation;
import org.kie.kogito.correlation.CorrelationInstance;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.Policy;
import org.kie.kogito.internal.process.workitem.WorkItemNotFoundException;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.jobs.TimerDescription;
import org.kie.kogito.process.EventDescription;
import org.kie.kogito.process.MutableProcessInstances;
import org.kie.kogito.process.NodeInstanceNotFoundException;
import org.kie.kogito.process.NodeNotFoundException;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessError;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstanceExecutionException;
import org.kie.kogito.process.ProcessInstanceNotFoundException;
import org.kie.kogito.process.Signal;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.process.flexible.AdHocFragment;
import org.kie.kogito.process.flexible.Milestone;
import org.kie.kogito.process.impl.lock.ContextAwareProcessInstanceLockStrategy;
import org.kie.kogito.process.impl.lock.ProcessInstanceAtomicLockStrategy;
import org.kie.kogito.process.impl.lock.ProcessInstanceLockStrategy;
import org.kie.kogito.process.workitems.InternalKogitoWorkItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractProcessInstance<T extends Model> implements ProcessInstance<T> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractProcessInstance.class);

    private static final String KOGITO_PROCESS_INSTANCE = "KogitoProcessInstance";

    protected final T variables;
    protected final AbstractProcess<T> process;
    protected InternalProcessRuntime rt;
    protected WorkflowProcessInstance processInstance;

    protected Integer status;
    protected Date startDate;
    protected String id;
    protected CorrelationKey correlationKey;
    protected String description;

    protected String errorMessage;
    protected String nodeInError;
    protected String nodeInstanceIdInError;
    protected Throwable errorCause;
    protected ProcessError processError;

    protected Consumer<AbstractProcessInstance<?>> reloadSupplier;

    protected long version;

    private Optional<CorrelationInstance> correlationInstance = Optional.empty();

    private ProcessInstanceLockStrategy processInstanceLockStrategy;

    public AbstractProcessInstance(AbstractProcess<T> process, T variables, ProcessRuntime rt) {
        this(process, variables, null, rt);
    }

    public AbstractProcessInstance(AbstractProcess<T> process, T variables, String businessKey, ProcessRuntime rt) {
        this(process, variables, businessKey, rt, null);
    }

    public AbstractProcessInstance(AbstractProcess<T> process, T variables, String businessKey, ProcessRuntime rt, CompositeCorrelation correlation) {
        this.process = process;
        this.rt = (InternalProcessRuntime) rt;
        this.variables = variables;
        this.processInstanceLockStrategy = new ContextAwareProcessInstanceLockStrategy(ProcessInstanceAtomicLockStrategy.instance());
        setCorrelationKey(businessKey);
        Map<String, Object> map = bind(variables);

        org.kie.api.definition.process.Process processDefinition = process.get();
        if (processDefinition instanceof WorkflowProcess) {
            try {
                ((WorkflowProcess) processDefinition).getInputValidator().ifPresent(v -> v.validate(map));
            } catch (IllegalArgumentException e) {
                throw new ProcessInstanceExecutionException(id, null, null, e.getMessage(), e);
            }
        }
        String processId = processDefinition.getId();

        WorkflowProcessInstance workflowProcessInstance = (WorkflowProcessInstance) ((CorrelationAwareProcessRuntime) rt).createProcessInstance(processId, correlationKey, map);
        syncWorkflowInstanceState(workflowProcessInstance);
        workflowProcessInstance.setMetaData(KOGITO_PROCESS_INSTANCE, this);
        internalSetProcessInstance(workflowProcessInstance);
        if (Objects.nonNull(correlation)) {
            this.correlationInstance = Optional.of(process.correlations().create(correlation, id()));
        }
    }

    /**
     * Without providing a ProcessRuntime the ProcessInstance can only be used as read-only
     * 
     * @param process
     * @param variables
     * @param wpi
     */
    public AbstractProcessInstance(AbstractProcess<T> process, T variables, org.kie.api.runtime.process.WorkflowProcessInstance wpi) {
        this(process, variables, null, wpi);
    }

    public AbstractProcessInstance(AbstractProcess<T> process, T variables, ProcessRuntime rt, org.kie.api.runtime.process.WorkflowProcessInstance wpi) {
        this.process = process;
        this.rt = (InternalProcessRuntime) rt;
        this.variables = variables;
        this.processInstanceLockStrategy = new ContextAwareProcessInstanceLockStrategy(ProcessInstanceAtomicLockStrategy.instance());

        syncWorkflowInstanceState((WorkflowProcessInstance) wpi);
    }

    private void syncWorkflowInstanceState(WorkflowProcessInstance wpi) {
        status = wpi.getState();
        id = wpi.getStringId();
        description = wpi.getDescription();
        startDate = wpi.getStartDate();
        errorMessage = wpi.getErrorMessage();
        nodeInError = wpi.getNodeIdInError();
        nodeInstanceIdInError = wpi.getNodeInstanceIdInError();
        errorCause = wpi.getErrorCause().orElse(null);

        if (this.status == STATE_ERROR) {
            this.processError = buildProcessError();
        }

        unbind(variables, wpi.getVariables());
        setCorrelationKey(wpi.getCorrelationKey());

        if (this.status == STATE_COMPLETED || this.status == STATE_ERROR) {
            try {
                ((WorkflowProcess) process.get()).getOutputValidator().ifPresent(v -> v.validate(wpi.getVariables()));
            } catch (IllegalArgumentException e) {
                throw new ProcessInstanceExecutionException(id, null, null, e.getMessage(), e);
            }
        }
    }

    private boolean isProcessInstanceConnected() {
        return this.rt != null;
    }

    public WorkflowProcessInstanceImpl internalLoadProcessInstanceState() {
        LOG.debug("internal reload process instance {}", id);
        internalLoadState();
        if (isProcessInstanceConnected()) {
            reconnect();
        }
        syncWorkflowInstanceState(processInstance);
        return (WorkflowProcessInstanceImpl) this.processInstance;
    }

    public void internalLoadState() {
        if (this.processInstance == null) {
            reloadSupplier.accept(this);
            if (this.processInstance == null) {
                throw new ProcessInstanceNotFoundException(id);
            }
        }
    }

    protected void reconnect() {
        LOG.debug("reconnect process instance {}", id);
        if (correlationInstance.isEmpty()) {
            correlationInstance = process().correlations().findByCorrelatedId(id());
        }

        if (processInstance.getKnowledgeRuntime() == null) {
            processInstance.setKnowledgeRuntime(getProcessRuntime().getInternalKieRuntime());
        }
        getProcessRuntime().getProcessInstanceManager().setLock(((MutableProcessInstances<T>) process.instances()).lock());
        processInstance.setMetaData(KOGITO_PROCESS_INSTANCE, this);

        restoreProcessInstanceContext();

        processInstance.reconnect();
    }

    private void restoreProcessInstanceContext() {
        Map<String, List<String>> headers = processInstance.getHeaders();
        if (headers != null && !headers.isEmpty()) {
            Map<String, String> contextMap = convertHeadersToContextMap(headers);
            if (!contextMap.isEmpty()) {
                org.kie.kogito.services.context.ProcessInstanceContext.setContextFromAsync(contextMap);
                LOG.debug("Restored process instance context for {} with {} keys", id, contextMap.size());
            }
        }
    }

    private Map<String, String> convertHeadersToContextMap(Map<String, List<String>> headers) {
        Map<String, String> contextMap = new java.util.HashMap<>();

        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                contextMap.put(entry.getKey(), entry.getValue().get(0));
            }
        }

        return contextMap;
    }

    public void internalUnloadProcessInstanceState() {
        if (processInstance == null) {
            LOG.debug("internal unload process instance {} invocation. already disconnected", id);
            return;
        }
        LOG.debug("internal unload process instance {}", id);
        syncWorkflowInstanceState(processInstance);
        if (status == STATE_ERROR) {
            processError = buildProcessError();
        }
        if (processInstance.getKnowledgeRuntime() != null) {
            disconnect();
        }
        internalUnloadState();
    }

    public void internalUnloadState() {
        switch (status) {
            case STATE_COMPLETED, STATE_ABORTED:
                // we left the instance in read only mode once it is completed
                this.rt = null;
                break;
            case STATE_PENDING:
                break;
            default:
                // already persisted. PENDING means that it has not started yet
                processInstance = null;
                break;
        }
    }

    protected void disconnect() {
        if (processInstance == null) {
            return;
        }
        LOG.debug("disconnect process instance state {}", processInstance.getId());
        processInstance.disconnect();
        processInstance.getMetaData().remove(KOGITO_PROCESS_INSTANCE);
    }

    private void setCorrelationKey(String businessKey) {
        if (businessKey != null && !businessKey.trim().isEmpty()) {
            correlationKey = new StringCorrelationKey(businessKey);
        }
    }

    @Override
    public Optional<Correlation<?>> correlation() {
        return correlationInstance.map(CorrelationInstance::getCorrelation);
    }

    public WorkflowProcessInstance internalGetProcessInstance() {
        return processInstance;
    }

    public void internalSetProcessInstance(WorkflowProcessInstance processInstance) {
        this.processInstance = processInstance;
        processInstance.wrap(this);
    }

    public void internalSetReloadSupplier(Consumer<AbstractProcessInstance<?>> reloadSupplier) {
        this.reloadSupplier = reloadSupplier;
    }

    public boolean hasHeader(String headerName) {
        return executeInWorkflowProcessInstanceRead(pi -> {
            return pi.getHeaders().containsKey(headerName);
        });
    }

    @Override
    public void start() {
        start(Collections.emptyMap());
    }

    @Override
    public void start(Map<String, List<String>> headers) {
        start(null, null, headers);
    }

    @Override
    public void start(String trigger, String referenceId) {
        start(trigger, referenceId, Collections.emptyMap());
    }

    @Override
    public void start(String trigger, String referenceId, Map<String, List<String>> headers) {
        executeInWorkflowProcessInstanceWrite(pi -> {
            if (pi.getState() != KogitoProcessInstance.STATE_PENDING) {
                throw new IllegalStateException("Impossible to start process instance that already has started");
            }
            syncPersistence((WorkflowProcessInstanceImpl) pi);
            if (referenceId != null) {
                pi.setReferenceId(referenceId);
            }

            if (headers != null) {
                pi.setHeaders(headers);
            }

            getProcessRuntime().getProcessInstanceManager().setLock(((MutableProcessInstances<T>) process.instances()).lock());
            getProcessRuntime().getKogitoProcessRuntime().startProcessInstance(pi.getId(), trigger);
            return null;
        });
    }

    @Override
    public void abort() {
        checkWriteOnly();
        processInstanceLockStrategy.executeOperation(id, () -> {
            WorkflowProcessInstanceImpl workflowProcessInstance = internalLoadProcessInstanceState();
            if (isProcessInstanceConnected()) {
                getProcessRuntime().getProcessInstanceManager().addProcessInstance(workflowProcessInstance);
            }
            ((MutableProcessInstances<T>) process.instances()).remove(this.id());
            String pid = workflowProcessInstance.getStringId();
            getProcessRuntime().getKogitoProcessRuntime().abortProcessInstance(pid);

            if (isProcessInstanceConnected()) {
                getProcessRuntime().getProcessInstanceManager().removeProcessInstance(workflowProcessInstance);
            }
            internalUnloadProcessInstanceState();
            return null;
        });
    }

    private InternalProcessRuntime getProcessRuntime() {
        if (rt == null) {
            throw new UnsupportedOperationException("Process instance is not connected to a Process Runtime");
        } else {
            return rt;
        }
    }

    @Override
    public <S> void send(Signal<S> signal) {
        executeInWorkflowProcessInstanceWrite(pi -> {
            if (signal.referenceId() != null) {
                pi.setReferenceId(signal.referenceId());
            }
            pi.signalEvent(signal.channel(), signal.payload());
            return null;
        });
    }

    @Override
    public Process<T> process() {
        return process;
    }

    @Override
    public T variables() {
        return delegateIfPresent(variables, p -> {
            return variables;
        });
    }

    public void reload() {
        executeInWorkflowProcessInstance(Function.identity());
    }

    @Override
    public int status() {
        return delegateIfPresent(status, p -> p.getState());
    }

    @Override
    public String id() {
        return this.id;
    }

    @Override
    public String businessKey() {
        return this.correlationKey == null ? null : this.correlationKey.getName();
    }

    @Override
    public String description() {
        return this.description;
    }

    @Override
    public Date startDate() {
        return delegateIfPresent(startDate, p -> p.getStartDate());
    }

    private <R> R delegateIfPresent(R defaultValue, Function<WorkflowProcessInstance, R> data) {
        return this.processInstance == null ? defaultValue : data.apply((WorkflowProcessInstanceImpl) this.processInstance);
    }

    @Override
    public long version() {
        return this.version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    @Override
    public T updateVariables(T updates) {
        Map<String, Object> map = bind(updates);
        variables.update(map);
        return updateVariables(map);
    }

    @Override
    public T updateVariablesPartially(T updates) {
        return updateVariables(this.variables.updatePartially(bind(updates)));
    }

    private T updateVariables(Map<String, Object> map) {
        return executeInWorkflowProcessInstanceWrite(pi -> {
            for (Entry<String, Object> entry : map.entrySet()) {
                pi.setVariable(entry.getKey(), entry.getValue());
            }
            return variables;
        });
    }

    @Override
    public Optional<ProcessError> error() {
        return Optional.ofNullable(this.processError);
    }

    @Override
    public void startFrom(String nodeId) {
        startFrom(nodeId, Collections.emptyMap());
    }

    @Override
    public void startFrom(String nodeId, Map<String, List<String>> headers) {
        startFrom(nodeId, null, headers);
    }

    @Override
    public void startFrom(String nodeId, String referenceId) {
        startFrom(nodeId, referenceId, Collections.emptyMap());
    }

    @Override
    public void startFrom(String nodeId, String referenceId, Map<String, List<String>> headers) {
        executeInWorkflowProcessInstanceWrite(pi -> {
            syncPersistence((WorkflowProcessInstanceImpl) pi);
            pi.setStartDate(new Date());
            pi.setState(STATE_ACTIVE);
            getProcessRuntime().getProcessInstanceManager().addProcessInstance(pi);
            if (referenceId != null) {
                pi.setReferenceId(referenceId);
            }
            if (headers != null) {
                pi.setHeaders(headers);
            }

            internalTriggerNode(pi, nodeId);
            return null;
        });
    }

    @Override
    public void triggerNode(String nodeId) {
        executeInWorkflowProcessInstanceWrite(pi -> {
            internalTriggerNode(pi, nodeId);
            return null;
        });
    }

    private void internalTriggerNode(WorkflowProcessInstance wfpi, String nodeId) {
        RuleFlowProcess rfp = ((RuleFlowProcess) wfpi.getProcess());

        // we avoid create containers incorrectly
        NodeInstance nodeInstance = wfpi.getNodeByPredicate(rfp,
                ni -> Objects.equals(nodeId, ni.getName()) || Objects.equals(nodeId, ni.getId().toExternalFormat()));
        if (nodeInstance == null) {
            throw new NodeNotFoundException(this.id, nodeId);
        }
        nodeInstance.trigger(null, Node.CONNECTION_DEFAULT_TYPE);
    }

    @Override
    public void cancelNodeInstance(String nodeInstanceId) {
        executeInWorkflowProcessInstanceWrite(pi -> {
            NodeInstance nodeInstance = pi
                    .getNodeInstances(true)
                    .stream()
                    .filter(ni -> ni.getStringId().equals(nodeInstanceId))
                    .findFirst()
                    .orElseThrow(() -> new NodeInstanceNotFoundException(this.id, nodeInstanceId));
            nodeInstance.cancel();
            return null;
        });
    }

    @Override
    public void retriggerNodeInstance(String nodeInstanceId) {
        executeInWorkflowProcessInstanceWrite(pi -> {
            NodeInstance nodeInstance = pi
                    .getNodeInstances(true)
                    .stream()
                    .filter(ni -> ni.getStringId().equals(nodeInstanceId))
                    .findFirst()
                    .orElseThrow(() -> new NodeInstanceNotFoundException(this.id, nodeInstanceId));
            ((NodeInstanceImpl) nodeInstance).retrigger(true);
            return null;
        });
    }

    @Override
    public void updateNodeInstanceSla(String nodeInstanceId, ZonedDateTime slaDueDate) {
        executeInWorkflowProcessInstanceWrite(pi -> {
            NodeInstance nodeInstance = pi.getNodeInstances(true)
                    .stream()
                    .filter(ni -> ni.getId().equals(nodeInstanceId))
                    .findFirst()
                    .orElseThrow(() -> new NodeInstanceNotFoundException(this.id, nodeInstanceId));
            ((NodeInstanceImpl) nodeInstance).rescheduleSlaTimer(slaDueDate);
            return null;
        });
    }

    @Override
    public void updateProcessInstanceSla(ZonedDateTime slaDueDate) {
        executeInWorkflowProcessInstanceWrite(pi -> {
            pi.rescheduleSlaTimer(slaDueDate);
            return null;
        });
    }

    public <R> R executeInWorkflowProcessInstanceWrite(Function<WorkflowProcessInstanceImpl, R> execution) {
        checkWriteOnly();
        return executeInWorkflowProcessInstance(execution);
    }

    private void checkWriteOnly() {
        if (this.rt == null) {
            throw new UnsupportedOperationException("Process instance " + id + " in read mode");
        }
    }

    /**
     * this is intended to be used internal. Sometimes is required to perform low level operations that require some
     * internal state of the process like obtaining the SLA or operating nodes instances.
     *
     * @param <R>
     * @param execution
     * @return
     */
    public <R> R executeInWorkflowProcessInstanceRead(Function<WorkflowProcessInstanceImpl, R> execution) {
        return processInstanceLockStrategy.executeOperation(id, () -> {
            WorkflowProcessInstanceImpl workflowProcessInstance = internalLoadProcessInstanceState();
            R outcome = execution.apply(workflowProcessInstance);
            internalUnloadProcessInstanceState();
            return outcome;
        });
    }

    private <R> R executeInWorkflowProcessInstance(Function<WorkflowProcessInstanceImpl, R> execution) {
        // Check if this is a reentrant call before entering the lock
        boolean isReentrant = processInstanceLockStrategy.isLockedByCurrentThread(id);

        return processInstanceLockStrategy.executeOperation(id, () -> {
            WorkflowProcessInstanceImpl workflowProcessInstance = internalLoadProcessInstanceState();
            if (isProcessInstanceConnected()) {
                getProcessRuntime().getProcessInstanceManager().addProcessInstance(workflowProcessInstance);
            }
            R outcome = null;
            try {
                outcome = execution.apply(workflowProcessInstance);
            } catch (Throwable th) {
                // clean up after non-expected error
                if (isProcessInstanceConnected()) {
                    getProcessRuntime().getProcessInstanceManager().removeProcessInstance(workflowProcessInstance);
                }
                if (workflowProcessInstance.getKnowledgeRuntime() != null) {
                    disconnect();
                }
                internalUnloadState();
                throw th;
            }

            if (isProcessInstanceConnected()) {
                syncPersistence(workflowProcessInstance);
                getProcessRuntime().getProcessInstanceManager().removeProcessInstance(workflowProcessInstance);
            }

            // Only unload state if this is not a reentrant call, this prevents nested calls from unloading the parent's state
            if (!isReentrant) {
                internalUnloadProcessInstanceState();
            }

            return outcome;
        });
    }

    @Override
    public Collection<KogitoNodeInstance> findNodes(Predicate<KogitoNodeInstance> predicate) {
        return executeInWorkflowProcessInstanceRead(pi -> pi.getKogitoNodeInstances(predicate, true));
    }

    @Override
    public WorkItem workItem(String workItemId, Policy... policies) {
        return executeInWorkflowProcessInstanceRead(pi -> {
            WorkItemNodeInstance nodeInstance = pi.getNodeInstances(true).stream()
                    .filter(WorkItemNodeInstance.class::isInstance)
                    .map(WorkItemNodeInstance.class::cast)
                    .filter(ni -> ni.getWorkItemId().equals(workItemId))
                    .findAny()
                    .orElseThrow(() -> new WorkItemNotFoundException("Work item with id '" + workItemId + "' was not found in process instance '" + id() + "'", workItemId));

            // Enforcing policies after WorkItem is found
            Stream.of(policies).forEach(p -> p.enforce(nodeInstance.getWorkItem()));

            return toBaseWorkItem(nodeInstance);
        });
    }

    @Override
    public List<WorkItem> workItems(Policy... policies) {
        return workItems(WorkItemNodeInstance.class::isInstance, policies);
    }

    @Override
    public List<WorkItem> workItems(Predicate<KogitoNodeInstance> p, Policy... policies) {
        return executeInWorkflowProcessInstanceRead(pi -> {
            return pi.getNodeInstances(true).stream()
                    .filter(p::test)
                    .filter(WorkItemNodeInstance.class::isInstance)
                    .map(WorkItemNodeInstance.class::cast)
                    .filter(w -> enforce(w.getWorkItem(), policies))
                    .map(this::toBaseWorkItem)
                    .toList();
        });
    }

    private WorkItem toBaseWorkItem(WorkItemNodeInstance workItemNodeInstance) {
        InternalKogitoWorkItem workItem = workItemNodeInstance.getWorkItem();
        return new BaseWorkItem(
                workItemNodeInstance.getStringId(),
                workItemNodeInstance.getWorkItemId(),
                workItemNodeInstance.getNode().getId(),
                (String) workItem.getParameters().getOrDefault("TaskName", workItemNodeInstance.getNodeName()),
                workItem.getName(),
                workItem.getState(),
                workItem.getPhaseId(),
                workItem.getPhaseStatus(),
                workItem.getParameters(),
                workItem.getResults(),
                workItem.getExternalReferenceId());
    }

    private boolean enforce(KogitoWorkItem kogitoWorkItem, Policy... policies) {
        try {
            Stream.of(policies).forEach(p -> p.enforce(kogitoWorkItem));
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    @Override
    public void completeWorkItem(String workItemId, Map<String, Object> variables, Policy... policies) {
        executeInWorkflowProcessInstanceWrite(pi -> {
            getProcessRuntime().getKogitoProcessRuntime().getKogitoWorkItemManager().completeWorkItem(workItemId, variables, policies);
            return null;
        });
    }

    @Override
    public <R> R updateWorkItem(String workItemId, Function<KogitoWorkItem, R> updater, Policy... policies) {
        return executeInWorkflowProcessInstanceWrite(pi -> {
            R result = getProcessRuntime().getKogitoProcessRuntime().getKogitoWorkItemManager().updateWorkItem(workItemId, updater, policies);
            return result;
        });
    }

    @Override
    public void abortWorkItem(String workItemId, Policy... policies) {
        executeInWorkflowProcessInstanceWrite(pi -> {
            getProcessRuntime().getKogitoProcessRuntime().getKogitoWorkItemManager().abortWorkItem(workItemId, policies);
            return null;
        });
    }

    @Override
    public void transitionWorkItem(String workItemId, WorkItemTransition transition) {
        executeInWorkflowProcessInstanceWrite(pi -> {
            getProcessRuntime().getKogitoProcessRuntime().getKogitoWorkItemManager().transitionWorkItem(workItemId, transition);
            return null;
        });
    }

    @Override
    public Set<EventDescription<?>> events() {
        return executeInWorkflowProcessInstanceRead(pi -> {
            return pi.getEventDescriptions();
        });
    }

    @Override
    public Collection<Milestone> milestones() {
        return executeInWorkflowProcessInstanceRead(pi -> {
            return pi.milestones();
        });
    }

    @Override
    public Collection<TimerDescription> timers() {
        return executeInWorkflowProcessInstanceRead(pi -> {
            return pi.timers();
        });
    }

    @Override
    public Collection<AdHocFragment> adHocFragments() {
        return executeInWorkflowProcessInstanceRead(pi -> {
            return pi.adHocFragments();
        });
    }

    protected void syncPersistence(WorkflowProcessInstanceImpl workflowProcessInstanceImpl) {
        switch (workflowProcessInstanceImpl.getState()) {
            case KogitoProcessInstance.STATE_ABORTED, KogitoProcessInstance.STATE_COMPLETED:
                correlationInstance.map(CorrelationInstance::getCorrelation).ifPresent(c -> process.correlations().delete(c));
                ((MutableProcessInstances<T>) process.instances()).remove(this.id());
                break;
            case KogitoProcessInstance.STATE_PENDING:
                if (reloadSupplier == null) {
                    ((MutableProcessInstances<T>) process.instances()).create(this.id(), this);
                } else {
                    ((MutableProcessInstances<T>) process.instances()).update(this.id(), this);
                }
                break;
            case KogitoProcessInstance.STATE_ACTIVE, KogitoProcessInstance.STATE_ERROR, KogitoProcessInstance.STATE_SUSPENDED:
                ((MutableProcessInstances<T>) process.instances()).update(this.id(), this);
                break;
        }
    }

    // this must be overridden at compile time
    protected Map<String, Object> bind(T variables) {
        HashMap<String, Object> vmap = new HashMap<>();
        if (variables == null) {
            return vmap;
        }
        try {
            for (Field f : variables.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object v = f.get(variables);
                vmap.put(f.getName(), v);
            }
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
        vmap.put("$v", variables);
        return vmap;
    }

    protected void unbind(T variables, Map<String, Object> vmap) {
        if (vmap == null) {
            return;
        }
        Models.setId(variables, id);
        try {
            for (Field f : variables.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                f.set(variables, vmap.get(f.getName()));
            }
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
        vmap.put("$v", variables);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((status == null) ? 0 : status.hashCode());
        return result;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AbstractProcessInstance other = (AbstractProcessInstance) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (status == null) {
            if (other.status != null)
                return false;
        } else if (!status.equals(other.status))
            return false;
        return true;
    }

    protected ProcessError buildProcessError() {
        return new ProcessError() {

            @Override
            public String failedNodeId() {
                return nodeInError;
            }

            @Override
            public String failedNodeInstanceId() {
                return nodeInstanceIdInError;
            }

            @Override
            public String errorMessage() {
                return errorMessage;
            }

            @Override
            public Throwable errorCause() {
                return errorCause;
            }

            @Override
            public void retrigger() {
                executeInWorkflowProcessInstanceWrite(pi -> {
                    NodeInstance nodeInstanceInError = pi.getNodeInstance(nodeInstanceIdInError, true);
                    NodeInstanceImpl ni = (NodeInstanceImpl) pi.getByNodeDefinitionId(nodeInError, pi.getNodeContainer());

                    clearError(pi);

                    getProcessRuntime().getProcessEventSupport().fireProcessRetriggered(pi, pi.getKnowledgeRuntime());
                    org.kie.api.runtime.process.NodeInstanceContainer nodeInstanceContainer = ni.getNodeInstanceContainer();
                    if (nodeInstanceContainer instanceof NodeInstance) {
                        ((NodeInstance) nodeInstanceContainer).internalSetTriggerTime(new Date());
                    }

                    if (nodeInstanceInError != null && nodeInstanceInError.getLeaveTime() == null && nodeInstanceInError.getCancelType() == null) {
                        // Cancelling the node instance in error before retriggering if it is active to avoid duplicated node instances.
                        // This is required when dealing with work items (ej: Human Tasks)
                        nodeInstanceInError.cancel();
                    }

                    ni.internalSetRetrigger(true);
                    ni.trigger(null, Node.CONNECTION_DEFAULT_TYPE);
                    return null;
                });
            }

            @Override
            public void skip() {
                executeInWorkflowProcessInstanceWrite(pi -> {
                    NodeInstanceImpl ni = (NodeInstanceImpl) pi.getByNodeDefinitionId(nodeInError, pi.getNodeContainer());
                    clearError(pi);
                    ni.triggerCompleted(Node.CONNECTION_DEFAULT_TYPE, true);
                    return null;
                });
            }

            private void clearError(WorkflowProcessInstanceImpl pInstance) {
                pInstance.setState(STATE_ACTIVE);
                pInstance.internalSetErrorNodeId(null);
                pInstance.internalSetErrorNodeInstanceId(null);
                pInstance.internalSetErrorMessage(null);
            }
        };
    }

    private class StringCorrelationKey implements CorrelationKey {

        private final String correlationKey;

        public StringCorrelationKey(String correlationKey) {
            this.correlationKey = correlationKey;
        }

        @Override
        public String getName() {
            return correlationKey;
        }

        @Override
        public List<CorrelationProperty<?>> getProperties() {
            return Collections.emptyList();
        }

        @Override
        public String toExternalForm() {
            return correlationKey;
        }

    }
}
