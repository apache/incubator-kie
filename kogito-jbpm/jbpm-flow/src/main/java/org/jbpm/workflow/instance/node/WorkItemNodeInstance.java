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
package org.jbpm.workflow.instance.node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.jbpm.process.core.Context;
import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.core.ParameterDefinition;
import org.jbpm.process.core.Work;
import org.jbpm.process.core.context.exception.ExceptionScope;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.ContextInstance;
import org.jbpm.process.instance.ContextInstanceContainer;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.KogitoProcessContextImpl;
import org.jbpm.process.instance.ProcessInstance;
import org.jbpm.process.instance.context.exception.ExceptionScopeInstance;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.process.instance.impl.ContextInstanceFactory;
import org.jbpm.process.instance.impl.ContextInstanceFactoryRegistry;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.impl.DataAssociation;
import org.jbpm.workflow.core.impl.NodeIoHelper;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.jbpm.workflow.instance.WorkflowRuntimeException;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.process.EventListener;
import org.kie.api.runtime.process.ProcessWorkItemHandlerException;
import org.kie.api.runtime.process.WorkItem;
import org.kie.kogito.Model;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.internal.process.runtime.KogitoWorkItemNodeInstance;
import org.kie.kogito.process.EventDescription;
import org.kie.kogito.process.GroupedNamedDataType;
import org.kie.kogito.process.IOEventDescription;
import org.kie.kogito.process.NamedDataType;
import org.kie.kogito.process.Processes;
import org.kie.kogito.process.impl.AbstractProcessInstance;
import org.kie.kogito.process.workitem.KogitoWorkItemHandlerNotFoundException;
import org.kie.kogito.process.workitems.InternalKogitoWorkItem;
import org.kie.kogito.process.workitems.InternalKogitoWorkItemManager;
import org.kie.kogito.process.workitems.impl.KogitoWorkItemImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jbpm.process.core.context.variable.VariableScope.VARIABLE_SCOPE;
import static org.kie.api.runtime.process.WorkItem.ABORTED;
import static org.kie.api.runtime.process.WorkItem.ACTIVE;
import static org.kie.api.runtime.process.WorkItem.COMPLETED;
import static org.kie.kogito.internal.process.runtime.KogitoProcessInstance.STATE_ABORTED;
import static org.kie.kogito.internal.process.runtime.KogitoProcessInstance.STATE_COMPLETED;

/**
 * Runtime counterpart of a work item node.
 *
 */
public class WorkItemNodeInstance extends StateBasedNodeInstance implements EventListener, ContextInstanceContainer, KogitoWorkItemNodeInstance {

    private static final long serialVersionUID = 510l;
    private static final Logger logger = LoggerFactory.getLogger(WorkItemNodeInstance.class);

    private static List<String> defaultOutputVariables = Arrays.asList("ActorId");

    private Map<String, List<ContextInstance>> subContextInstances = new HashMap<>();

    private String workItemId;
    private transient InternalKogitoWorkItem workItem;
    private String exceptionHandlingProcessInstanceId;

    private int triggerCount = 0;

    protected WorkItemNode getWorkItemNode() {
        return (WorkItemNode) getNode();
    }

    @Override
    public InternalKogitoWorkItem getWorkItem() {
        if (workItem == null && workItemId != null) {
            workItem = ((InternalKogitoWorkItemManager) getProcessInstance().getKnowledgeRuntime().getWorkItemManager()).getWorkItem(workItemId);
        }
        return workItem;
    }

    public String getWorkItemId() {
        return workItemId;
    }

    public void internalSetWorkItemId(String workItemId) {
        this.workItemId = workItemId;
    }

    public void internalSetWorkItem(InternalKogitoWorkItem workItem) {
        this.workItem = workItem;
        this.workItem.setProcessInstance(getProcessInstance());
        this.workItem.setNodeInstance(this);
    }

    public void internalRegisterWorkItem() {
        ((InternalKogitoWorkItemManager) getProcessInstance().getKnowledgeRuntime().getWorkItemManager()).internalAddWorkItem(workItem);
    }

    public void internalRemoveWorkItem() {
        InternalKogitoWorkItem wi = getWorkItem();
        if (wi != null) {
            ((InternalKogitoWorkItemManager) getProcessInstance().getKnowledgeRuntime().getWorkItemManager()).internalRemoveWorkItem(wi.getStringId());
        }
    }

    @Override
    public void internalTrigger(final KogitoNodeInstance from, String type) {
        super.internalTrigger(from, type);
        // if node instance was cancelled, abort
        if (getNodeInstanceContainer().getNodeInstance(getStringId()) == null) {
            return;
        }
        WorkItemNode workItemNode = getWorkItemNode();
        createWorkItem(workItemNode);
        if (workItemNode.isWaitForCompletion()) {
            addWorkItemListener();
        }
        String deploymentId = (String) getProcessInstance().getKnowledgeRuntime().getEnvironment().get(EnvironmentName.DEPLOYMENT_ID);
        workItem.setDeploymentId(deploymentId);
        workItem.setNodeInstanceId(this.getStringId());
        workItem.setNodeId(getNodeId());
        workItem.setNodeInstance(this);
        workItem.setProcessInstance(getProcessInstance());

        if (workItemNode.getWork().getWorkParametersFactory() != null) {
            Map<String, Object> parameters = workItemNode.getWork().getWorkParametersFactory().apply(workItem);
            parameters.forEach(workItem::setParameter);
        }

        processWorkItemHandler(() -> ((InternalKogitoWorkItemManager) InternalProcessRuntime.asKogitoProcessRuntime(getProcessInstance().getKnowledgeRuntime()).getKogitoWorkItemManager())
                .internalExecuteWorkItem(workItem));
        if (!workItemNode.isWaitForCompletion()) {
            triggerCompleted();
        }
        this.workItemId = workItem.getStringId();
    }

    private void processWorkItemHandler(Runnable handler) {
        try {
            handler.run();
        } catch (KogitoWorkItemHandlerNotFoundException wihnfe) {
            getProcessInstance().setState(STATE_ABORTED);
            throw wihnfe;
        } catch (ProcessWorkItemHandlerException handlerException) {
            if (triggerCount++ < handlerException.getRetries() + 1) {
                this.workItemId = workItem.getStringId();
                handleWorkItemHandlerException(handlerException, workItem);
            } else {
                throw handlerException;
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    protected void handleException(String exceptionName, Exception e) {
        getExceptionScopeInstance(exceptionName, e).handleException(exceptionName, getProcessContext(e));
    }

    protected void handleException(Exception e) {
        getExceptionScopeInstance(e, e).handleException(e, getProcessContext(e));
    }

    private ExceptionScopeInstance getExceptionScopeInstance(Object context, Exception e) {
        ExceptionScopeInstance exceptionScopeInstance = (ExceptionScopeInstance) resolveContextInstance(ExceptionScope.EXCEPTION_SCOPE, context);
        if (exceptionScopeInstance == null) {
            throw new WorkflowRuntimeException(this, getProcessInstance(), "Unable to execute Action: " + e.getMessage(), e);
        }
        // workItemId must be set otherwise cancel activity will not find the right work item
        this.workItemId = workItem.getStringId();
        Collection<String> strings = exceptionScopeInstance.getExceptionScope().getExceptionHandlers().keySet();
        logger.info("exception scope {} with exception handlers {}", context, strings, e);
        return exceptionScopeInstance;
    }

    protected InternalKogitoWorkItem newWorkItem() {
        return new KogitoWorkItemImpl();
    }

    protected InternalKogitoWorkItem createWorkItem(WorkItemNode workItemNode) {
        Work work = workItemNode.getWork();
        workItem = newWorkItem();
        workItem.setId(UUID.randomUUID().toString());
        workItem.setName(work.getName());
        workItem.setProcessInstanceId(getProcessInstance().getStringId());
        workItem.setProcessInstance(this.getKogitoProcessInstance());
        workItem.setNodeInstance(this);
        workItem.setNodeInstanceId(this.getId());
        workItem.setStartDate(new Date());
        workItem.setState(ACTIVE);

        Map<String, Object> resolvedParameters = new LinkedHashMap<>();

        Collection<String> metaParameters = work.getMetaParameters();

        for (Entry<String, Object> e : work.getParameters().entrySet()) {
            if (!metaParameters.contains(e.getKey()) && e.getValue() != null) {
                resolvedParameters.put(e.getKey(), e.getValue());
                if (e.getValue() instanceof String) {
                    // we try first is a variable
                    Object value = this.getVariable((String) e.getValue());
                    if (value != null) {
                        resolvedParameters.put(e.getKey(), value);
                    } else {
                        resolvedParameters.put(e.getKey(), resolveValue(e.getValue()));
                    }
                }
            }
        }

        Function<String, Object> varResolver = (varRef) -> {
            if (resolvedParameters.containsKey(varRef)) {
                return resolvedParameters.get(varRef);
            }
            return getVariable(varRef);
        };
        Map<String, Object> inputSet = NodeIoHelper.processInputs(this, varResolver);

        inputSet.putAll(resolvedParameters);
        if (dynamicParameters != null) {
            inputSet.putAll(dynamicParameters);
        }

        inputSet.forEach(workItem::setParameter);
        return workItem;
    }

    public void triggerCompleted(InternalKogitoWorkItem workItem) {
        this.workItem = workItem;
        WorkItemNode workItemNode = getWorkItemNode();

        if (workItemNode != null && workItem.getState() == COMPLETED) {
            validateWorkItemResultVariable(getProcessInstance().getProcessName(), workItemNode.getOutAssociations(), workItem);
            Map<String, Object> outputs = new HashMap<>(workItem.getResults());
            if (workItem.getActualOwner() != null && !outputs.containsKey("ActorId")) {
                outputs.put("ActorId", workItem.getActualOwner());
            }
            NodeIoHelper.processOutputs(this, varRef -> outputs.get(varRef), varName -> this.getVariable(varName));
        }

        if (getNode() == null) {
            setMetaData("NodeType", workItem.getName());
            mapDynamicOutputData(workItem.getResults());
        }

        internalRemoveWorkItem();
        triggerCompleted();

    }

    @Override
    public void cancel(CancelType cancelType) {
        InternalKogitoWorkItem item = getWorkItem();
        if (item != null && !List.of(COMPLETED, ABORTED).contains(item.getState())) {
            try {
                ((InternalKogitoWorkItemManager) getProcessInstance().getKnowledgeRuntime().getWorkItemManager()).internalAbortWorkItem(item.getStringId());
            } catch (KogitoWorkItemHandlerNotFoundException wihnfe) {
                logger.error("The workitem {} is being aborted but not workitem handlers was associated with {}", item.getStringId(), item.getName());
            }
        }

        if (exceptionHandlingProcessInstanceId != null) {
            KogitoProcessRuntime kruntime = getKieRuntimeForSubprocess();
            ProcessInstance processInstance = (ProcessInstance) kruntime.getProcessInstance(exceptionHandlingProcessInstanceId);
            if (processInstance != null) {
                processInstance.setState(STATE_ABORTED);
            }
        }
        super.cancel(cancelType);
    }

    @Override
    public void addEventListeners() {
        super.addEventListeners();
        addWorkItemListener();
        addExceptionProcessListener();
    }

    protected void addWorkItemListener() {
        getProcessInstance().addEventListener("workItemCompleted", this, false);
        getProcessInstance().addEventListener("workItemAborted", this, false);
    }

    protected void removeWorkItemListener() {
        getProcessInstance().removeEventListener("workItemCompleted", this, false);
        getProcessInstance().removeEventListener("workItemAborted", this, false);
    }

    @Override
    public void removeEventListeners() {
        super.removeEventListeners();
        removeWorkItemListener();

    }

    @Override
    public void signalEvent(String type, Object event) {
        if ("workItemCompleted".equals(type)) {
            InternalKogitoWorkItem item = (InternalKogitoWorkItem) event;
            workItemCompleted(item);
        } else if ("workItemAborted".equals(type)) {
            InternalKogitoWorkItem item = (InternalKogitoWorkItem) event;
            workItemAborted(item);
        } else if (("processInstanceCompleted:" + exceptionHandlingProcessInstanceId).equals(type)) {
            exceptionHandlingCompleted((WorkflowProcessInstance) event, null);
        } else if (type.equals("RuleFlow-Activate" + getProcessInstance().getProcessId() + "-" + getNode().getUniqueId())) {
            trigger(null, Node.CONNECTION_DEFAULT_TYPE);
        } else {
            super.signalEvent(type, event);
        }
    }

    @Override
    public String[] getEventTypes() {
        if (exceptionHandlingProcessInstanceId != null) {
            return new String[] { "workItemCompleted", "processInstanceCompleted:" + exceptionHandlingProcessInstanceId };
        } else {
            return new String[] { "workItemCompleted" };
        }
    }

    public void workItemAborted(InternalKogitoWorkItem workItem) {
        if (workItem.getStringId().equals(workItemId) || (workItemId == null && getWorkItem().getStringId().equals(workItem.getStringId()))) {
            workItem.setState(WorkItem.ABORTED);
            removeEventListeners();
            triggerCompleted(workItem);
        }
    }

    public void workItemCompleted(InternalKogitoWorkItem workItem) {
        if (workItem.getStringId().equals(workItemId) || (workItemId == null && getWorkItem().getStringId().equals(workItem.getStringId()))) {
            workItem.setState(WorkItem.COMPLETED);
            removeEventListeners();
            triggerCompleted(workItem);
        }
    }

    @Override
    public String getNodeName() {
        org.kie.api.definition.process.Node node = getNode();
        if (node == null) {
            String nodeName = "[Dynamic]";
            InternalKogitoWorkItem item = getWorkItem();
            if (item != null) {
                nodeName += " " + item.getParameter("TaskName");
            }
            return nodeName;
        }
        return super.getNodeName();
    }

    @Override
    public List<ContextInstance> getContextInstances(String contextId) {
        return this.subContextInstances.get(contextId);
    }

    @Override
    public void addContextInstance(String contextId, ContextInstance contextInstance) {
        this.subContextInstances
                .computeIfAbsent(contextId, k -> new ArrayList<>())
                .add(contextInstance);
    }

    @Override
    public void removeContextInstance(String contextId, ContextInstance contextInstance) {
        List<ContextInstance> list = this.subContextInstances.get(contextId);
        if (list != null) {
            list.remove(contextInstance);
        }
    }

    @Override
    public ContextInstance getContextInstance(String contextId, long id) {
        List<ContextInstance> instances = subContextInstances.get(contextId);
        if (instances != null) {
            for (ContextInstance contextInstance : instances) {
                if (contextInstance.getContextId() == id) {
                    return contextInstance;
                }
            }
        }
        return null;
    }

    @Override
    public ContextInstance getContextInstance(Context context) {
        ContextInstanceFactory conf = ContextInstanceFactoryRegistry.INSTANCE.getContextInstanceFactory(context);
        if (conf == null) {
            throw new IllegalArgumentException("Illegal context type (registry not found): " + context.getClass());
        }
        ContextInstance contextInstance = conf.getContextInstance(context, this, getProcessInstance());
        if (contextInstance == null) {
            throw new IllegalArgumentException("Illegal context type (instance not found): " + context.getClass());
        }
        return contextInstance;
    }

    @Override
    public ContextContainer getContextContainer() {
        return getWorkItemNode();
    }

    public void validateWorkItemResultVariable(String processName, List<DataAssociation> outputs, InternalKogitoWorkItem workItem) {
        // in case work item results are skip validation as there is no notion of mandatory data outputs
        if (!VariableScope.isVariableStrictEnabled() || workItem.getResults().isEmpty()) {
            return;
        }

        List<String> outputNames = new ArrayList<>();
        for (DataAssociation association : outputs) {
            if (association.getSources() != null) {
                outputNames.add(association.getSources().get(0).getLabel());
            }
            if (association.getAssignments() != null) {
                association.getAssignments().forEach(a -> outputNames.add(a.getFrom().getLabel()));
            }
        }

        for (String outputName : workItem.getResults().keySet()) {
            if (!outputNames.contains(outputName) && !defaultOutputVariables.contains(outputName)) {
                throw new IllegalArgumentException("Data output '" + outputName + "' is not defined in process '" + processName + "' for task '" + workItem.getParameter("NodeName") + "'");
            }
        }
    }

    /*
     * Work item handler exception handling
     */

    private void handleWorkItemHandlerException(ProcessWorkItemHandlerException handlerException, InternalKogitoWorkItem workItem) {
        KogitoProcessRuntime kruntime = getKieRuntimeForSubprocess();

        org.kie.kogito.process.Process<? extends Model> process = kruntime.getApplication().get(Processes.class).processById(handlerException.getProcessId());

        if (process == null) {
            logger.error("Cannot find process {}. Aborting error handling", handlerException.getProcessId());
            return;
        }

        AbstractProcessInstance<?> kogitoProcessInstance = (AbstractProcessInstance<?>) process.createInstance(process.createModel());

        WorkflowProcessInstance processInstance = kogitoProcessInstance.internalGetProcessInstance();
        processInstance.setParentProcessInstanceId(getProcessInstance().getStringId());
        processInstance.setSignalCompletion(true);
        processInstance.setMetaData("ParentProcessInstanceId", getProcessInstance().getStringId());
        processInstance.setMetaData("ParentNodeInstanceId", getUniqueId());

        // add all parameters of the work item to the newly started process instance
        VariableScopeInstance variableScopeInstance = (VariableScopeInstance) processInstance.getContextInstance(VariableScope.VARIABLE_SCOPE);
        variableScopeInstance.setVariable("DeploymentId", workItem.getDeploymentId());
        variableScopeInstance.setVariable("ProcessInstanceId", workItem.getProcessInstanceStringId());
        variableScopeInstance.setVariable("WorkItemId", workItem.getStringId());
        variableScopeInstance.setVariable("NodeInstanceId", this.getStringId());
        variableScopeInstance.setVariable("ErrorMessage", handlerException.getMessage());
        variableScopeInstance.setVariable("Error", handlerException);
        for (Map.Entry<String, Object> entry : workItem.getParameters().entrySet()) {
            variableScopeInstance.setVariable(entry.getKey(), entry.getValue());
        }

        kogitoProcessInstance.start();

        // start change the id
        this.exceptionHandlingProcessInstanceId = kogitoProcessInstance.id();

        if (processInstance.getState() == STATE_COMPLETED
                || processInstance.getState() == STATE_ABORTED) {
            exceptionHandlingCompleted(processInstance, handlerException);
        } else {
            addExceptionProcessListener();
        }
    }

    private void exceptionHandlingCompleted(WorkflowProcessInstance processInstance, ProcessWorkItemHandlerException handlerException) {
        Object errorVariable = processInstance.getVariable("Error");
        // allow child override
        if (errorVariable instanceof ProcessWorkItemHandlerException) {
            handlerException = (ProcessWorkItemHandlerException) errorVariable;
        }
        InternalKogitoWorkItemManager kogitoWorkItemManager =
                (InternalKogitoWorkItemManager) InternalProcessRuntime.asKogitoProcessRuntime(getProcessInstance().getKnowledgeRuntime()).getKogitoWorkItemManager();
        switch (handlerException.getStrategy()) {
            case ABORT:
                kogitoWorkItemManager.abortWorkItem(getWorkItem().getStringId());
                break;
            case RETHROW:
                String exceptionName = handlerException.getCause().getClass().getName();
                ExceptionScopeInstance exceptionScopeInstance = (ExceptionScopeInstance) resolveContextInstance(ExceptionScope.EXCEPTION_SCOPE, exceptionName);
                if (exceptionScopeInstance == null) {
                    throw new WorkflowRuntimeException(this, getProcessInstance(), "Unable to execute work item " + handlerException.getMessage(), handlerException.getCause());
                }
                KogitoProcessContextImpl context = new KogitoProcessContextImpl(this.getProcessInstance().getKnowledgeRuntime());
                context.setProcessInstance(this.getProcessInstance());
                context.setNodeInstance(this);
                context.getContextData().put("Exception", handlerException.getCause());
                exceptionScopeInstance.handleException(exceptionName, context);
                break;
            case RETRY:
                Map<String, Object> parameters = new HashMap<>(getWorkItem().getParameters());
                parameters.putAll(processInstance.getVariables());
                processWorkItemHandler(() -> kogitoWorkItemManager.retryWorkItem(getWorkItem().getStringId(), parameters));
                break;
            case COMPLETE:
                kogitoWorkItemManager.completeWorkItem(getWorkItem().getStringId(), processInstance.getVariables());
                break;
        }

    }

    public void addExceptionProcessListener() {
        if (exceptionHandlingProcessInstanceId != null) {
            getProcessInstance().addEventListener("processInstanceCompleted:" + exceptionHandlingProcessInstanceId, this, true);
        }
    }

    public void removeExceptionProcessListeners() {
        super.removeEventListeners();
        getProcessInstance().removeEventListener("processInstanceCompleted:" + exceptionHandlingProcessInstanceId, this, true);
    }

    public String getExceptionHandlingProcessInstanceId() {
        return exceptionHandlingProcessInstanceId;
    }

    public void internalSetProcessInstanceId(String processInstanceId) {
        if (processInstanceId != null && !processInstanceId.isEmpty()) {
            this.exceptionHandlingProcessInstanceId = processInstanceId;
        }
    }

    protected KogitoProcessRuntime getKieRuntimeForSubprocess() {
        return InternalProcessRuntime.asKogitoProcessRuntime(getProcessInstance().getKnowledgeRuntime());
    }

    @Override
    public Set<EventDescription<?>> getEventDescriptions() {
        List<NamedDataType> inputs = new ArrayList<>();
        for (ParameterDefinition paramDef : getWorkItemNode().getWork().getParameterDefinitions()) {
            inputs.add(new NamedDataType(paramDef.getName(), paramDef.getType()));
        }

        List<NamedDataType> outputs = new ArrayList<>();
        VariableScope variableScope = (VariableScope) getProcessInstance().getContextContainer().getDefaultContext(VARIABLE_SCOPE);
        getWorkItemNode().getOutAssociations()
                .forEach(da -> da.getSources().forEach(s -> outputs.add(new NamedDataType(s.getLabel(), variableScope.findVariable(da.getTarget().getLabel()).getType()))));

        GroupedNamedDataType dataTypes = new GroupedNamedDataType();
        dataTypes.add("Input", inputs);
        dataTypes.add("Output", outputs);

        // return just the main completion type of an event
        return Collections.singleton(new IOEventDescription("workItemCompleted", getNodeDefinitionId(), getNodeName(), "workItem", getWorkItemId(), getProcessInstance().getStringId(), dataTypes));
    }
}
