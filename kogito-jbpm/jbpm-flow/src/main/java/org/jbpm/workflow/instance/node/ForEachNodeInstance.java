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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.ContextInstance;
import org.jbpm.process.instance.ContextableInstance;
import org.jbpm.process.instance.KogitoProcessContextImpl;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.process.instance.impl.Action;
import org.jbpm.process.instance.impl.ReturnValueEvaluator;
import org.jbpm.ruleflow.core.Metadata;
import org.jbpm.util.ContextFactory;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.node.ForEachNode;
import org.jbpm.workflow.core.node.ForEachNode.ForEachJoinNode;
import org.jbpm.workflow.core.node.ForEachNode.ForEachSplitNode;
import org.jbpm.workflow.instance.NodeInstance;
import org.jbpm.workflow.instance.NodeInstanceContainer;
import org.jbpm.workflow.instance.impl.MVELProcessHelper;
import org.jbpm.workflow.instance.impl.NodeInstanceImpl;
import org.jbpm.workflow.instance.impl.NodeInstanceResolverFactory;
import org.kie.api.definition.process.Connection;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.impl.SimpleValueResolver;

import static org.jbpm.workflow.instance.WorkflowProcessParameters.WORKFLOW_PARAM_MULTIPLE_CONNECTIONS;

/**
 * Runtime counterpart of a for each node.
 */
public class ForEachNodeInstance extends CompositeContextNodeInstance {

    private static final long serialVersionUID = 510L;
    private static final Set<Class<? extends org.kie.api.runtime.process.NodeInstance>> NOT_SERIALIZABLE_CLASSES = Set.of(ForEachJoinNodeInstance.class); // using Arrays.asList to allow multiple exclusions

    public static final String TEMP_OUTPUT_VAR = "foreach_output";

    private int totalInstances;
    private int executedInstances;

    public ForEachNode getForEachNode() {
        return (ForEachNode) getNode();
    }

    public int getExecutedInstances() {
        return this.executedInstances;
    }

    public void setExecutedInstances(int executedInstances) {
        this.executedInstances = executedInstances;
    }

    public int getTotalInstances() {
        return this.totalInstances;
    }

    public void setTotalInstances(int totalInstances) {
        this.totalInstances = totalInstances;
    }

    /**
     * Retained for serialisation compatibility only — the async-fallback logic has been removed.
     * The field is still present in the protobuf schema (field 5) to avoid wire-format breakage.
     */
    @Deprecated
    public boolean getHasAsyncInstances() {
        return false;
    }

    /** @see #getHasAsyncInstances() */
    @Deprecated
    public void setHasAsyncInstances(boolean hasAsyncInstances) {
        // no-op: field is kept in the wire format but is no longer used at runtime
    }

    @Override
    public NodeInstance getNodeInstance(final org.kie.api.definition.process.Node currentNode) {
        org.kie.api.definition.process.Node node = resolveAsync(currentNode);
        if (node instanceof ForEachSplitNode) {
            ForEachSplitNodeInstance nodeInstance = new ForEachSplitNodeInstance();
            nodeInstance.setNodeId(node.getId());
            nodeInstance.setNodeInstanceContainer(this);
            nodeInstance.setProcessInstance(getProcessInstance());
            int level = this.getLevelForNode(node.getUniqueId());
            nodeInstance.setLevel(level);
            return nodeInstance;

        } else if (node instanceof ForEachJoinNode || currentNode instanceof ForEachJoinNode) {
            Optional<NodeInstance> existingNodeInstance = Optional.ofNullable(getFirstNodeInstance(node.getId()));
            if (existingNodeInstance.isPresent()) {
                return existingNodeInstance.get();
            }
            if (node instanceof ForEachJoinNode) {
                ForEachJoinNodeInstance nodeInstance = new ForEachJoinNodeInstance();
                nodeInstance.setNodeId(node.getId());
                nodeInstance.setNodeInstanceContainer(this);
                nodeInstance.setProcessInstance(getProcessInstance());
                String uniqueID = node.getUniqueId();
                if (uniqueID == null) {
                    uniqueID = node.getId() + "";
                }
                int level = this.getLevelForNode(uniqueID);
                nodeInstance.setLevel(level);
                return nodeInstance;
            }
        }
        return super.getNodeInstance(currentNode);
    }

    @Override
    public ContextContainer getContextContainer() {
        return getForEachNode().getCompositeNode();
    }

    private boolean isSequential() {
        return getForEachNode().isSequential();
    }

    public class ForEachSplitNodeInstance extends NodeInstanceImpl implements ContextableInstance {

        private static final long serialVersionUID = 510l;

        public ForEachSplitNode getForEachSplitNode() {
            return (ForEachSplitNode) getNode();
        }

        @Override
        public void internalTrigger(KogitoNodeInstance from, String type) {
            triggerTime = new Date();
            Collection<?> collection = evaluateCollectionExpression();
            setTotalInstances(collection.size());

            ((NodeInstanceContainer) getNodeInstanceContainer()).removeNodeInstance(this);
            if (collection.isEmpty()) {
                ForEachNodeInstance.this.triggerCompleted(Node.CONNECTION_DEFAULT_TYPE, true);
            } else {
                List<NodeInstance> nodeInstances = new ArrayList<>();
                for (Object o : collection) {
                    String variableName = getForEachNode().getVariableName();
                    NodeInstance nodeInstance = ((NodeInstanceContainer) getNodeInstanceContainer()).getNodeInstance(getForEachSplitNode().getTo().getTo());
                    VariableScopeInstance variableScopeInstance = (VariableScopeInstance) nodeInstance.resolveContextInstance(VariableScope.VARIABLE_SCOPE, variableName);
                    variableScopeInstance.setVariable(nodeInstance, variableName, o);
                    nodeInstances.add(nodeInstance);
                }

                for (NodeInstance nodeInstance : nodeInstances) {
                    logger.debug("Triggering [{}] in multi-instance loop.", nodeInstance.getNodeId());
                    nodeInstance.trigger(this, getForEachSplitNode().getTo().getToType());

                    if (isSequential()) {
                        // for sequential mode trigger only first item from the list
                        break;
                    }
                }

                if (!getForEachNode().isWaitForCompletion()) {
                    ForEachNodeInstance.this.triggerCompleted(Node.CONNECTION_DEFAULT_TYPE, false);
                }
            }
        }

        private Collection<?> evaluateCollectionExpression() {
            Object collection;
            String collectionExpression = getForEachNode().getCollectionExpression();
            VariableScopeInstance variableScopeInstance = (VariableScopeInstance) resolveContextInstance(
                    VariableScope.VARIABLE_SCOPE, collectionExpression);
            if (variableScopeInstance != null) {
                collection = variableScopeInstance.getVariable(collectionExpression);
            } else if (getForEachNode().getEvaluateExpression() != null) {
                collection = getForEachNode().getEvaluateExpression().eval(getVariable((String) getForEachNode().getMetaData(Metadata.VARIABLE)), Collection.class, ContextFactory.fromNode(this));
            } else {
                try {
                    collection = MVELProcessHelper.evaluator().eval(collectionExpression, new NodeInstanceResolverFactory(
                            this));
                } catch (Exception t) {
                    throw new IllegalArgumentException(
                            "Could not find collection " + collectionExpression);
                }
            }
            if (collection == null) {
                return Collections.emptyList();
            }
            if (collection instanceof Collection<?>) {
                return (Collection<?>) collection;
            }
            if (collection.getClass().isArray()) {
                List<Object> list = new ArrayList<>();
                Collections.addAll(list, (Object[]) collection);
                return list;
            }
            throw new IllegalArgumentException(
                    "Unexpected collection type: " + collection.getClass());
        }

        @Override
        public ContextInstance getContextInstance(String contextId) {
            return ForEachNodeInstance.this.getContextInstance(contextId);
        }
    }

    public class ForEachJoinNodeInstance extends NodeInstanceImpl implements ContextableInstance {

        private static final long serialVersionUID = 510l;

        public ForEachJoinNode getForEachJoinNode() {
            return (ForEachJoinNode) getNode();
        }

        @Override
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public void internalTrigger(KogitoNodeInstance from, String type) {
            triggerTime = new Date();
            setExecutedInstances(getExecutedInstances() + 1);

            Map<String, Object> tempVariables = new HashMap<>();
            if (getForEachNode().getOutputVariableName() != null) {
                Object outputVariable = from.getVariable(getForEachNode().getOutputVariableName());

                Collection<Object> outputCollection = (Collection<Object>) this.getVariable(TEMP_OUTPUT_VAR);
                if (outputCollection == null) {
                    outputCollection = new ArrayList<>();
                }

                outputCollection.add(outputVariable);

                setVariable(TEMP_OUTPUT_VAR, outputCollection);
                tempVariables.put(getForEachNode().getOutputVariableName(), outputVariable);

                String outputCollectionName = getForEachNode().getOutputCollectionExpression();
                if (outputCollectionName != null) {
                    tempVariables.put(outputCollectionName, outputCollection);
                }

            }

            boolean isCompletionConditionMet = getForEachNode().hasCompletionCondition() && evaluateCompletionCondition(getForEachNode().getCompletionConditionExpression(), tempVariables);
            if (isSequential() && !isCompletionConditionMet && !areNodeInstancesCompleted()) {
                getFirstCompositeNodeInstance()
                        .ifPresent(nodeInstance -> {
                            logger.debug("Triggering [{}] in multi-instance loop.", nodeInstance.getNodeId());
                            nodeInstance.trigger(null, getForEachNode().getForEachSplitNode().getTo().getToType());
                        });
            }

            if (areNodeInstancesCompleted() || isCompletionConditionMet) {
                String outputCollection = getForEachNode().getOutputCollectionExpression();
                Action outputAction = getForEachNode().getCompletionAction();

                if (outputCollection != null) {
                    Collection<?> outputVariable = (Collection<?>) getVariable(outputCollection);
                    Collection collectedValues = (Collection) getVariable(TEMP_OUTPUT_VAR);
                    if (outputVariable != null) {
                        outputVariable.addAll(collectedValues);
                    } else {
                        outputVariable = collectedValues;
                    }
                    setVariable(outputCollection, outputVariable);
                } else if (outputAction != null) {
                    try {
                        outputAction.execute(ContextFactory.fromNode(this));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }

                ((NodeInstanceContainer) getNodeInstanceContainer()).removeNodeInstance(this);

                if (getForEachNode().isWaitForCompletion()) {
                    if (!WORKFLOW_PARAM_MULTIPLE_CONNECTIONS.get(getProcessInstance().getProcess())) {
                        triggerConnection(getForEachJoinNode().getTo());
                    } else {
                        List<Connection> connections = getForEachJoinNode().getOutgoingConnections(Node.CONNECTION_DEFAULT_TYPE);
                        for (Connection connection : connections) {
                            triggerConnection(connection);
                        }
                    }
                }
            }
        }

        private Optional<NodeInstance> getFirstCompositeNodeInstance() {
            return ((CompositeNodeInstance) getNodeInstanceContainer()).getNodeInstances(false).stream()
                    .filter(CompositeContextNodeInstance.class::isInstance)
                    .filter(NodeInstance.class::isInstance)
                    .map(NodeInstance.class::cast)
                    .findFirst();
        }

        private boolean areNodeInstancesCompleted() {
            return getNodeInstanceContainer().getNodeInstances().size() == 1;
        }

        private boolean evaluateCompletionCondition(ReturnValueEvaluator completeConditionEvaluator, Map<String, Object> tempVariables) {
            try {
                KogitoProcessContextImpl context = (KogitoProcessContextImpl) ContextFactory.fromNode(this);
                context.setContextData(tempVariables);
                return (Boolean) completeConditionEvaluator.evaluate(context);
            } catch (Exception t) {
                throw new IllegalArgumentException("Could not evaluate completion condition  " + completeConditionEvaluator, t);
            }
        }

        @Override
        public ContextInstance getContextInstance(String contextId) {
            return ForEachNodeInstance.this.getContextInstance(contextId);
        }
    }

    @Override
    public ContextInstance getContextInstance(String contextId) {
        ContextInstance contextInstance = super.getContextInstance(contextId);
        if (contextInstance == null) {
            contextInstance = resolveContextInstance(contextId, TEMP_OUTPUT_VAR);
            setContextInstance(contextId, contextInstance);
        }

        return contextInstance;
    }

    @Override
    public int getLevelForNode(String uniqueID) {
        // always 1 for each
        return 1;
    }

    @Override
    public Collection<org.kie.api.runtime.process.NodeInstance> getSerializableNodeInstances() {
        return getNodeInstances().stream().filter(ForEachNodeInstance::isSerializable).collect(Collectors.toUnmodifiableList());
    }

    /**
     * Check if the given <code>org.kie.api.runtime.process.NodeInstance</code> is serializable.
     *
     * @param toCheck
     * @return
     */
    static boolean isSerializable(org.kie.api.runtime.process.NodeInstance toCheck) {
        return !NOT_SERIALIZABLE_CLASSES.contains(toCheck.getClass());
    }

    private class ForEachNodeInstanceResolverFactory extends NodeInstanceResolverFactory {

        private static final long serialVersionUID = -8856846610671009685L;

        private Map<String, Object> tempVariables;

        public ForEachNodeInstanceResolverFactory(NodeInstance nodeInstance, Map<String, Object> tempVariables) {
            super(nodeInstance);
            this.tempVariables = tempVariables;
        }

        @Override
        public boolean isResolveable(String name) {
            boolean result = tempVariables.containsKey(name);
            if (result) {
                return result;
            }
            return super.isResolveable(name);
        }

        @Override
        public VariableResolver getVariableResolver(String name) {
            if (tempVariables.containsKey(name)) {
                return new SimpleValueResolver(tempVariables.get(name));
            }
            return super.getVariableResolver(name);
        }
    }
}
