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
package org.jbpm.workflow.instance.impl;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.regex.Matcher;

import org.drools.core.common.InternalKnowledgeRuntime;
import org.jbpm.process.core.Context;
import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.core.context.exception.ExceptionScope;
import org.jbpm.process.core.context.exclusive.ExclusiveGroup;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.ContextInstance;
import org.jbpm.process.instance.ContextInstanceContainer;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.context.exception.ExceptionScopeInstance;
import org.jbpm.process.instance.context.exclusive.ExclusiveGroupInstance;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.process.instance.impl.Action;
import org.jbpm.process.instance.impl.ConstraintEvaluator;
import org.jbpm.util.ContextFactory;
import org.jbpm.util.PatternConstants;
import org.jbpm.workflow.core.Constraint;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.impl.NodeImpl;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.jbpm.workflow.instance.WorkflowRuntimeException;
import org.jbpm.workflow.instance.node.ActionNodeInstance;
import org.jbpm.workflow.instance.node.CompositeNodeInstance;
import org.kie.api.definition.process.Connection;
import org.kie.api.definition.process.WorkflowElementIdentifier;
import org.kie.api.runtime.process.NodeInstanceContainer;
import org.kie.kogito.internal.process.runtime.KogitoNode;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstanceContainer;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.process.ProcessInstanceExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jbpm.ruleflow.core.Metadata.HIDDEN;
import static org.jbpm.ruleflow.core.Metadata.INCOMING_CONNECTION;
import static org.jbpm.ruleflow.core.Metadata.OUTGOING_CONNECTION;
import static org.jbpm.workflow.instance.WorkflowProcessParameters.WORKFLOW_PARAM_MULTIPLE_CONNECTIONS;
import static org.jbpm.workflow.instance.WorkflowProcessParameters.WORKFLOW_PARAM_TRANSACTIONS;
import static org.kie.kogito.internal.process.runtime.KogitoProcessInstance.STATE_ACTIVE;

/**
 * Default implementation of a RuleFlow node instance.
 * 
 */
public abstract class NodeInstanceImpl implements org.jbpm.workflow.instance.NodeInstance, Serializable {

    private static final long serialVersionUID = 510l;
    protected static final Logger logger = LoggerFactory.getLogger(NodeInstanceImpl.class);

    private String id;
    private WorkflowElementIdentifier nodeId;
    private WorkflowProcessInstance processInstance;
    private org.jbpm.workflow.instance.NodeInstanceContainer nodeInstanceContainer;
    private Map<String, Object> metaData = new HashMap<>();
    private int level;

    protected int slaCompliance = KogitoProcessInstance.SLA_NA;
    protected Date slaDueDate;
    protected String slaTimerId;
    protected Date triggerTime;
    protected Date leaveTime;
    protected boolean isRetrigger;

    protected transient CancelType cancelType;

    protected transient Map<String, Object> dynamicParameters;

    public void setId(final String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getStringId() {
        return this.id;
    }

    @Override
    public boolean isRetrigger() {
        return isRetrigger;
    }

    public void setNodeId(WorkflowElementIdentifier nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public WorkflowElementIdentifier getNodeId() {
        return this.nodeId;
    }

    @Override
    public String getNodeName() {
        org.kie.api.definition.process.Node node = getNode();
        return node == null ? "" : node.getName();
    }

    @Override
    public String getNodeDefinitionId() {
        return getNode().getUniqueId();
    }

    @Override
    public int getLevel() {
        return this.level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setProcessInstance(final WorkflowProcessInstance processInstance) {
        this.processInstance = processInstance;
    }

    @Override
    public WorkflowProcessInstance getProcessInstance() {
        return this.processInstance;
    }

    @Override
    public KogitoNodeInstanceContainer getNodeInstanceContainer() {
        return this.nodeInstanceContainer;
    }

    public void setNodeInstanceContainer(KogitoNodeInstanceContainer nodeInstanceContainer) {
        this.nodeInstanceContainer = (org.jbpm.workflow.instance.NodeInstanceContainer) nodeInstanceContainer;
        if (nodeInstanceContainer != null) {
            this.nodeInstanceContainer.addNodeInstance(this);
        }
    }

    @Override
    public org.kie.api.definition.process.Node getNode() {
        if (nodeId == null) {
            return null;
        }
        try {
            return ((org.jbpm.workflow.core.NodeContainer) this.nodeInstanceContainer.getNodeContainer()).internalGetNode(this.nodeId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown node id: " + this.nodeId
                            + " for node instance " + getUniqueId()
                            + " for process instance " + this.processInstance,
                    e);
        }
    }

    @Override
    public CancelType getCancelType() {
        return cancelType;
    }

    public final void cancel() {
        cancel(CancelType.ABORTED);
    }

    @Override
    public void cancel(CancelType cancelType) {
        this.cancelType = cancelType;

        if (triggerTime == null) {
            triggerTime = new Date();
        }

        leaveTime = new Date();
        boolean hidden = false;
        org.kie.api.definition.process.Node node = getNode();
        if (node != null && node.getMetaData().get(HIDDEN) != null) {
            hidden = true;
        }
        if (!hidden) {
            InternalKnowledgeRuntime kruntime = getProcessInstance().getKnowledgeRuntime();
            ((InternalProcessRuntime) kruntime.getProcessRuntime())
                    .getProcessEventSupport().fireBeforeNodeLeft(this, kruntime);
        }
        nodeInstanceContainer.removeNodeInstance(this);
        nodeInstanceContainer.nodeInstanceCancelled(this);
        if (!hidden) {
            InternalKnowledgeRuntime kruntime = getProcessInstance().getKnowledgeRuntime();
            ((InternalProcessRuntime) kruntime.getProcessRuntime())
                    .getProcessEventSupport().fireAfterNodeLeft(this, kruntime);
        }
    }

    @Override
    public final void trigger(KogitoNodeInstance from, String type) {
        boolean hidden = false;
        if (getNode().getMetaData().get(HIDDEN) != null) {
            hidden = true;
        }

        if (from != null) {
            int level = ((org.jbpm.workflow.instance.NodeInstance) from).getLevel();
            ((org.jbpm.workflow.instance.NodeInstanceContainer) getNodeInstanceContainer()).setCurrentLevel(level);
            Collection<Connection> incoming = getNode().getIncomingConnections(type);
            for (Connection conn : incoming) {
                if (conn.getFrom().getId().equals(from.getNodeId())) {
                    this.metaData.put(INCOMING_CONNECTION, conn.getUniqueId());
                    break;
                }
            }
        }
        if (dynamicParameters != null) {
            for (Entry<String, Object> entry : dynamicParameters.entrySet()) {
                setVariable(entry.getKey(), entry.getValue());
            }
        }
        configureSla();

        InternalKnowledgeRuntime kruntime = getProcessInstance().getKnowledgeRuntime();
        if (!hidden) {
            ((InternalProcessRuntime) kruntime.getProcessRuntime())
                    .getProcessEventSupport().fireBeforeNodeTriggered(this, kruntime);
        }

        captureExecutionException(() -> internalTrigger(from, type));

        if (!hidden) {
            ((InternalProcessRuntime) kruntime.getProcessRuntime())
                    .getProcessEventSupport().fireAfterNodeTriggered(this, kruntime);
        }
    }

    protected void captureExecutionException(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            if (!WORKFLOW_PARAM_TRANSACTIONS.get(getProcessInstance().getProcess())) {
                logger.error("Error executing node instance '{}' (node '{}' id: '{}') in process instance '{}' (process: '{}') in a non transactional environment  ", getStringId(), getNodeName(),
                        getNodeDefinitionId(), processInstance.getId(), processInstance.getProcessId());
                captureError(e);
            } else {
                // Checking if the exception has been already wrapped by the actual node instance to avoid unnecessary wrappings.
                if (e instanceof ProcessInstanceExecutionException executionException) {
                    wrapException(executionException);
                }
                logger.error("Error {} executing node instance '{}' (node '{}' id: '{}') in process instance '{}' (process: '{}') in a transactional environment (Wrapping)", e.getMessage(),
                        getStringId(), getNodeName(),
                        getNodeDefinitionId(), processInstance.getId(), processInstance.getProcessId());
                throw new ProcessInstanceExecutionException(this.getProcessInstance().getId(), this.getNodeDefinitionId(), this.getId(), e.getMessage(), e);
            }
        }
    }

    protected void wrapException(ProcessInstanceExecutionException executionException) {
        if (getId().equals(executionException.getFailedNodeInstanceId())) {
            logger.debug("Exception already wrapped by node instance '{}' (node '{}' id: '{}') in process instance '{}' (process: '{}')... propagating exception.", getStringId(),
                    getNodeName(),
                    getNodeDefinitionId(), processInstance.getId(), processInstance.getProcessId());
            throw executionException;
        }
    }

    protected void captureError(Exception e) {
        getProcessInstance().setErrorState(this, e);
    }

    public abstract void internalTrigger(KogitoNodeInstance from, String type);

    /**
     * This method is used in both instances of the {@link ExtendedNodeInstanceImpl}
     * and {@link ActionNodeInstance} instances in order to handle
     * exceptions thrown when executing actions.
     * 
     * @param action An {@link Action} instance.
     */
    protected void executeAction(Action action, KogitoProcessContext context) {
        try {
            action.execute(context);
        } catch (Exception e) {
            ExceptionScopeInstance exceptionScopeInstance = (ExceptionScopeInstance) resolveContextInstance(ExceptionScope.EXCEPTION_SCOPE, e);
            if (exceptionScopeInstance == null) {
                if (!WORKFLOW_PARAM_TRANSACTIONS.get(getProcessInstance().getProcess())) {
                    logger.error("Error executing node instance '{}' (node '{}' id: '{}') in process instance '{}' (process: '{}') in a non transactional environment  ", getStringId(), getNodeName(),
                            getNodeDefinitionId(), processInstance.getId(), processInstance.getProcessId());
                    captureError(e);
                } else {
                    logger.error("Error {} executing node instance '{}' (node '{}' id: '{}') in process instance '{}' (process: '{}') in a transactional environment (Wrapping)", e.getMessage(),
                            getStringId(), getNodeName(),
                            getNodeDefinitionId(), processInstance.getId(), processInstance.getProcessId());
                    throw new ProcessInstanceExecutionException(this.getProcessInstance().getId(), this.getNodeDefinitionId(), this.getId(), e.getMessage(), e);
                }
                throw new WorkflowRuntimeException(this, getProcessInstance(), "Unable to execute Action: " + e.getMessage(), e);
            }
            context.getContextData().put("Exception", e);
            exceptionScopeInstance.handleException(e, context);
            cancel(CancelType.ERROR);
        }
    }

    protected void executeAction(Action action) {
        executeAction(action, ContextFactory.fromNode(this));
    }

    public void triggerCompleted(String type, boolean remove) {
        leaveTime = new Date();
        org.kie.api.definition.process.Node node = getNode();
        if (node != null) {
            String uniqueId = node.getUniqueId();
            if (uniqueId == null) {
                uniqueId = ((NodeImpl) node).getUniqueId();
            }
            ((WorkflowProcessInstanceImpl) processInstance).addCompletedNodeId(uniqueId);
            ((WorkflowProcessInstanceImpl) processInstance).getIterationLevels().remove(uniqueId);
        }

        // if node instance was cancelled, or containing container instance was cancelled
        if ((getNodeInstanceContainer().getNodeInstance(getStringId()) == null)
                || (((org.jbpm.workflow.instance.NodeInstanceContainer) getNodeInstanceContainer()).getState() != STATE_ACTIVE)) {
            return;
        }

        if (remove) {
            ((org.jbpm.workflow.instance.NodeInstanceContainer) getNodeInstanceContainer())
                    .removeNodeInstance(this);
        }

        List<Connection> connections = null;
        if (node != null) {
            if (WORKFLOW_PARAM_MULTIPLE_CONNECTIONS.get(getProcessInstance().getProcess()) && !((NodeImpl) node).getConstraints().isEmpty()) {
                int priority;
                connections = ((NodeImpl) node).getDefaultOutgoingConnections();
                boolean found = false;
                List<NodeInstanceTrigger> nodeInstances =
                        new ArrayList<>();
                List<Connection> outgoingCopy = new ArrayList<>(connections);
                while (!outgoingCopy.isEmpty()) {
                    priority = Integer.MAX_VALUE;
                    Connection selectedConnection = null;
                    ConstraintEvaluator selectedConstraint = null;
                    for (final Connection connection : outgoingCopy) {
                        Collection<Constraint> constraints = ((NodeImpl) node).getConstraints(connection);
                        if (constraints != null) {
                            for (Constraint constraint : constraints) {
                                if (constraint instanceof ConstraintEvaluator && constraint.getPriority() < priority
                                        && !constraint.isDefault()) {
                                    priority = constraint.getPriority();
                                    selectedConnection = connection;
                                    selectedConstraint = (ConstraintEvaluator) constraint;
                                }

                            }
                        }
                    }
                    if (selectedConstraint == null) {
                        break;
                    }
                    if (selectedConstraint.evaluate(this,
                            selectedConnection,
                            selectedConstraint)) {
                        nodeInstances.add(new NodeInstanceTrigger(followConnection(selectedConnection), selectedConnection.getToType()));
                        found = true;
                    }
                    outgoingCopy.remove(selectedConnection);
                }
                for (NodeInstanceTrigger nodeInstance : nodeInstances) {
                    // stop if this process instance has been aborted / completed
                    if (((org.jbpm.workflow.instance.NodeInstanceContainer) getNodeInstanceContainer()).getState() != STATE_ACTIVE) {
                        return;
                    }
                    triggerNodeInstance(nodeInstance.getNodeInstance(), nodeInstance.getToType());
                }
                if (!found) {
                    for (final Connection connection : connections) {
                        Collection<Constraint> constraints = ((NodeImpl) node).getConstraints(connection);
                        if (constraints != null) {
                            for (Constraint constraint : constraints) {
                                if (constraint.isDefault()) {
                                    triggerConnection(connection);
                                    found = true;
                                    break;
                                }
                            }
                        }
                        if (found) {
                            break;
                        }
                    }
                }
                if (!found) {
                    throw new IllegalArgumentException("Uncontrolled flow node could not find at least one valid outgoing connection " + getNode().getName());
                }
                return;
            } else {
                connections = node.getOutgoingConnections(type);
            }
        }
        if (connections == null || connections.isEmpty()) {
            boolean hidden = false;
            org.kie.api.definition.process.Node currentNode = getNode();
            if (currentNode != null && currentNode.getMetaData().get(HIDDEN) != null) {
                hidden = true;
            }
            InternalKnowledgeRuntime kruntime = getProcessInstance().getKnowledgeRuntime();
            if (!hidden) {
                ((InternalProcessRuntime) kruntime.getProcessRuntime())
                        .getProcessEventSupport().fireBeforeNodeLeft(this, kruntime);
            }
            // notify container
            ((org.jbpm.workflow.instance.NodeInstanceContainer) getNodeInstanceContainer())
                    .nodeInstanceCompleted(this, type);
            if (!hidden) {
                ((InternalProcessRuntime) kruntime.getProcessRuntime())
                        .getProcessEventSupport().fireAfterNodeLeft(this, kruntime);
            }
        } else {
            Map<org.jbpm.workflow.instance.NodeInstance, String> nodeInstances = new HashMap<>();
            for (Connection connection : connections) {
                nodeInstances.put(followConnection(connection), connection.getToType());
            }
            for (Map.Entry<org.jbpm.workflow.instance.NodeInstance, String> nodeInstance : nodeInstances.entrySet()) {
                // stop if this process instance has been aborted / completed
                if (((org.jbpm.workflow.instance.NodeInstanceContainer) getNodeInstanceContainer()).getState() != STATE_ACTIVE) {
                    return;
                }
                triggerNodeInstance(nodeInstance.getKey(), nodeInstance.getValue());
            }
        }
    }

    protected org.jbpm.workflow.instance.NodeInstance followConnection(Connection connection) {
        // check for exclusive group first
        KogitoNodeInstanceContainer parent = getNodeInstanceContainer();
        if (parent instanceof ContextInstanceContainer) {
            List<ContextInstance> contextInstances = ((ContextInstanceContainer) parent).getContextInstances(ExclusiveGroup.EXCLUSIVE_GROUP);
            if (contextInstances != null) {
                for (ContextInstance contextInstance : new ArrayList<>(contextInstances)) {
                    ExclusiveGroupInstance groupInstance = (ExclusiveGroupInstance) contextInstance;
                    if (groupInstance.containsNodeInstance(this)) {
                        for (KogitoNodeInstance nodeInstance : groupInstance.getNodeInstances()) {
                            if (nodeInstance != this) {
                                ((org.jbpm.workflow.instance.NodeInstance) nodeInstance).cancel(CancelType.OBSOLETE);
                            }
                        }
                        ((ContextInstanceContainer) parent).removeContextInstance(ExclusiveGroup.EXCLUSIVE_GROUP, contextInstance);
                    }

                }
            }
        }
        return ((org.jbpm.workflow.instance.NodeInstanceContainer) getNodeInstanceContainer())
                .getNodeInstance(connection.getTo());
    }

    protected void triggerNodeInstance(org.jbpm.workflow.instance.NodeInstance nodeInstance, String type) {
        triggerNodeInstance(nodeInstance, type, true);
    }

    protected void triggerNodeInstance(org.jbpm.workflow.instance.NodeInstance nodeInstance, String type, boolean fireEvents) {
        leaveTime = new Date();
        boolean hidden = false;
        if (getNode().getMetaData().get(HIDDEN) != null) {
            hidden = true;
        }
        InternalKnowledgeRuntime kruntime = getProcessInstance().getKnowledgeRuntime();
        if (!hidden && fireEvents) {
            ((InternalProcessRuntime) kruntime.getProcessRuntime())
                    .getProcessEventSupport().fireBeforeNodeLeft(this, kruntime);
        }

        // trigger next node
        captureExecutionException(() -> nodeInstance.trigger(this, type));

        Collection<Connection> outgoing = getNode().getOutgoingConnections(type);
        for (Connection conn : outgoing) {
            if (conn.getTo().getId().equals(nodeInstance.getNodeId())) {
                this.metaData.put(OUTGOING_CONNECTION, conn.getUniqueId());
                break;
            }
        }
        if (!hidden && fireEvents) {
            ((InternalProcessRuntime) kruntime.getProcessRuntime())
                    .getProcessEventSupport().fireAfterNodeLeft(this, kruntime);
        }
    }

    protected void triggerConnection(Connection connection) {
        triggerNodeInstance(followConnection(connection), connection.getToType());
    }

    @Override
    public void retrigger(boolean remove) {
        if (remove) {
            cancel();
        }
        retriggerNode(nodeId, !remove);
    }

    private void retriggerNode(WorkflowElementIdentifier nodeId, boolean fireEvents) {
        NodeInstanceImpl nodeInstance = (NodeInstanceImpl) ((org.jbpm.workflow.instance.NodeInstanceContainer) getNodeInstanceContainer())
                .getNodeInstance(((KogitoNode) getNode()).getParentContainer().getNode(nodeId));

        nodeInstance.internalSetRetrigger(true);

        triggerNodeInstance(nodeInstance, Node.CONNECTION_DEFAULT_TYPE, fireEvents);
    }

    public Context resolveContext(String contextId, Object param) {
        if (getNode() == null) {
            return null;
        }
        return ((NodeImpl) getNode()).resolveContext(contextId, param);
    }

    @Override
    public ContextInstance resolveContextInstance(String contextId, Object param) {
        Context context = resolveContext(contextId, param);
        if (context == null) {
            return null;
        }
        ContextInstanceContainer contextInstanceContainer = getContextInstanceContainer(context.getContextContainer());
        if (contextInstanceContainer == null) {
            throw new IllegalArgumentException(
                    "Could not find context instance container for context");
        }
        return contextInstanceContainer.getContextInstance(context);
    }

    private ContextInstanceContainer getContextInstanceContainer(ContextContainer contextContainer) {
        ContextInstanceContainer contextInstanceContainer;
        if (this instanceof ContextInstanceContainer) {
            contextInstanceContainer = (ContextInstanceContainer) this;
        } else {
            contextInstanceContainer = getEnclosingContextInstanceContainer(this);
        }
        while (contextInstanceContainer != null) {
            if (contextInstanceContainer.getContextContainer() == contextContainer) {
                return contextInstanceContainer;
            }
            contextInstanceContainer = getEnclosingContextInstanceContainer(
                    (KogitoNodeInstance) contextInstanceContainer);
        }
        return null;
    }

    private ContextInstanceContainer getEnclosingContextInstanceContainer(KogitoNodeInstance nodeInstance) {
        NodeInstanceContainer nodeInstanceContainer = nodeInstance.getNodeInstanceContainer();
        while (true) {
            if (nodeInstanceContainer instanceof ContextInstanceContainer) {
                return (ContextInstanceContainer) nodeInstanceContainer;
            }
            if (nodeInstanceContainer instanceof KogitoNodeInstance) {
                nodeInstanceContainer = ((KogitoNodeInstance) nodeInstanceContainer).getNodeInstanceContainer();
            } else {
                return null;
            }
        }
    }

    @Override
    public Object getVariable(String variableName) {
        VariableScopeInstance variableScope = (VariableScopeInstance) resolveContextInstance(VariableScope.VARIABLE_SCOPE, variableName);
        if (variableScope == null) {
            variableScope = (VariableScopeInstance) getProcessInstance().getContextInstance(VariableScope.VARIABLE_SCOPE);
        }
        return variableScope.getVariable(variableName);
    }

    @Override
    public void setVariable(String variableName, Object value) {
        VariableScopeInstance variableScope = (VariableScopeInstance) resolveContextInstance(VariableScope.VARIABLE_SCOPE, variableName);
        if (variableScope == null) {
            variableScope = (VariableScopeInstance) getProcessInstance().getContextInstance(VariableScope.VARIABLE_SCOPE);
            if (variableScope.getVariableScope().findVariable(variableName) == null) {
                variableScope = null;
            }
        }
        if (variableScope == null) {
            logger.error("Could not find variable {}", variableName);
            logger.error("Using process-level scope");
            variableScope = (VariableScopeInstance) getProcessInstance().getContextInstance(VariableScope.VARIABLE_SCOPE);
        }
        variableScope.setVariable(this, variableName, value);
    }

    public String getUniqueId() {
        StringBuilder result = new StringBuilder("" + getStringId());
        NodeInstanceContainer parent = getNodeInstanceContainer();
        while (parent instanceof CompositeNodeInstance) {
            CompositeNodeInstance nodeInstance = (CompositeNodeInstance) parent;
            result.insert(0, nodeInstance.getStringId() + ":");
            parent = nodeInstance.getNodeInstanceContainer();
        }
        return result.toString();
    }

    @Override
    public Map<String, Object> getMetaData() {
        return this.metaData;
    }

    public Object getMetaData(String name) {
        return this.metaData.get(name);
    }

    public void setMetaData(String name, Object data) {
        this.metaData.put(name, data);
    }

    protected static class NodeInstanceTrigger {
        private org.jbpm.workflow.instance.NodeInstance nodeInstance;
        private String toType;

        public NodeInstanceTrigger(org.jbpm.workflow.instance.NodeInstance nodeInstance, String toType) {
            this.nodeInstance = nodeInstance;
            this.toType = toType;
        }

        public org.jbpm.workflow.instance.NodeInstance getNodeInstance() {
            return nodeInstance;
        }

        public String getToType() {
            return toType;
        }
    }

    @Override
    public void setDynamicParameters(Map<String, Object> dynamicParameters) {
        this.dynamicParameters = dynamicParameters;
    }

    protected void configureSla() {

    }

    public void rescheduleSlaTimer(ZonedDateTime slaDueDate) {
        throw new UnsupportedOperationException("Unsupported operation");
    }

    @Override
    public int getSlaCompliance() {
        return slaCompliance;
    }

    public void internalSetSlaCompliance(int slaCompliance) {
        this.slaCompliance = slaCompliance;
    }

    @Override
    public Date getSlaDueDate() {
        return slaDueDate;
    }

    public void internalSetSlaDueDate(Date slaDueDate) {
        this.slaDueDate = slaDueDate;
    }

    @Override
    public String getSlaTimerId() {
        return slaTimerId;
    }

    public void internalSetSlaTimerId(String slaTimerId) {
        this.slaTimerId = slaTimerId;
    }

    @Override
    public Date getTriggerTime() {
        return triggerTime;
    }

    public void internalSetTriggerTime(Date triggerTime) {
        this.triggerTime = triggerTime;
    }

    @Override
    public Date getLeaveTime() {
        return leaveTime;
    }

    protected Object resolveValue(Object value) {
        return (value instanceof String) ? resolveExpression((String) value) : value;
    }

    protected boolean isExpression(String expression) {
        return expression != null && PatternConstants.PARAMETER_MATCHER.matcher(expression).find();
    }

    /**
     * Extracts the first variable/expression name from a #{...} template.
     * For example, {@code "#{processName}"} returns {@code "processName"} and
     * {@code "#{a + b}-suffix"} returns {@code "a + b"}.
     */
    protected static String extractFirstVariableName(String expression) {
        Matcher m = PatternConstants.PARAMETER_MATCHER.matcher(expression);
        return m.find() ? m.group(1) : expression;
    }

    public String resolveExpression(String expression) {
        return isExpression(expression) ? (String) resolveValue(expression) : expression;
    }

    protected Object resolveValue(String expression) {
        return resolveValue(expression, (replacements) -> {
            String expr = expression;
            for (Map.Entry<String, Object> replacement : replacements.entrySet()) {
                expr = expr.replace("#{" + replacement.getKey() + "}", replacement.getValue() != null ? replacement.getValue().toString() : "");
            }
            return expr;
        });
    }

    // resolve expression based on variables or mvel expressions
    protected Object resolveValue(String expression, Function<Map<String, Object>, Object> converter) {
        if (expression == null) {
            return null;
        }
        Object outcome = null;
        // cannot parse delay, trying to interpret it
        Map<String, Object> replacements = new HashMap<>();
        Matcher matcher = PatternConstants.PARAMETER_MATCHER.matcher(expression);
        if (matcher.find()) {
            matcher.reset();
            while (matcher.find()) {
                String paramName = matcher.group(1);
                if (replacements.get(paramName) == null) {
                    VariableScopeInstance variableScopeInstance = (VariableScopeInstance) resolveContextInstance(VariableScope.VARIABLE_SCOPE, paramName);
                    if (variableScopeInstance != null) {
                        Object variableValue = variableScopeInstance.getVariable(paramName);
                        replacements.put(paramName, variableValue);
                    } else {
                        try {
                            Object variableValue = MVELProcessHelper.evaluator().eval(paramName, new NodeInstanceResolverFactory(this));
                            replacements.put(paramName, variableValue);
                        } catch (Exception t) {
                            logger.error("MVEL failed to replace variable {} in process {} for node {}. Continuing without setting process id", paramName, processInstance.getProcessId(),
                                    getNodeName(), t);
                        }
                    }
                }
            }
            outcome = converter.apply(replacements);
        } else if (getVariable(expression) != null) {
            outcome = getVariable(expression);
        } else {
            outcome = expression;
        }
        return outcome;
    }

    protected void mapDynamicOutputData(Map<String, Object> results) {
        if (results != null && !results.isEmpty()) {
            VariableScope variableScope = (VariableScope) ((ContextContainer) getProcessInstance().getProcess()).getDefaultContext(VariableScope.VARIABLE_SCOPE);
            VariableScopeInstance variableScopeInstance = (VariableScopeInstance) getProcessInstance().getContextInstance(VariableScope.VARIABLE_SCOPE);
            for (Entry<String, Object> result : results.entrySet()) {
                String variableName = result.getKey();
                Variable variable = variableScope.findVariable(variableName);
                if (variable != null) {
                    variableScopeInstance.getVariableScope().validateVariable(getProcessInstance().getProcessName(), variableName, result.getValue());
                    variableScopeInstance.setVariable(this, variableName, result.getValue());
                }
            }
        }
    }

    public void internalSetRetrigger(boolean isRetrigger) {
        this.isRetrigger = isRetrigger;

    }
}
