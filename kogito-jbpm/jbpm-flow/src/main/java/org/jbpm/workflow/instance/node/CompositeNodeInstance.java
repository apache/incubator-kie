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
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.node.CompositeNode;
import org.jbpm.workflow.core.node.EventNode;
import org.jbpm.workflow.core.node.EventNodeInterface;
import org.jbpm.workflow.core.node.EventSubProcessNode;
import org.jbpm.workflow.core.node.StartNode;
import org.jbpm.workflow.instance.NodeInstance;
import org.jbpm.workflow.instance.NodeInstanceContainer;
import org.jbpm.workflow.instance.impl.NodeInstanceFactory;
import org.jbpm.workflow.instance.impl.NodeInstanceFactoryRegistry;
import org.jbpm.workflow.instance.impl.NodeInstanceImpl;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.kie.api.definition.process.Connection;
import org.kie.api.definition.process.NodeContainer;
import org.kie.api.definition.process.WorkflowElementIdentifier;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstanceContainer;

import static org.jbpm.ruleflow.core.Metadata.IS_FOR_COMPENSATION;
import static org.jbpm.workflow.instance.impl.DummyEventListener.EMPTY_EVENT_LISTENER;
import static org.kie.kogito.internal.process.runtime.KogitoProcessInstance.STATE_ABORTED;
import static org.kie.kogito.internal.process.runtime.KogitoProcessInstance.STATE_ACTIVE;

/**
 * Runtime counterpart of a composite node.
 *
 */
public class CompositeNodeInstance extends StateBasedNodeInstance implements NodeInstanceContainer, EventNodeInstanceInterface, EventBasedNodeInstanceInterface {

    private static final long serialVersionUID = 510l;

    private final List<NodeInstance> nodeInstances = new ArrayList<>();

    private int state = STATE_ACTIVE;
    private Map<String, Integer> iterationLevels = new HashMap<>();
    private int currentLevel;

    @Override
    public int getLevelForNode(String uniqueID) {
        if (Boolean.parseBoolean(System.getProperty("jbpm.loop.level.disabled"))) {
            return 1;
        }
        Integer value = iterationLevels.get(uniqueID);
        if (value == null && currentLevel == 0) {
            value = Integer.valueOf(1);
        } else if ((value == null && currentLevel > 0) || (value != null && currentLevel > 0 && value > currentLevel)) {
            value = Integer.valueOf(currentLevel);
        } else {
            value++;
        }

        iterationLevels.put(uniqueID, value);
        return value;
    }

    protected CompositeNode getCompositeNode() {
        return (CompositeNode) getNode();
    }

    @Override
    public NodeContainer getNodeContainer() {
        return getCompositeNode();
    }

    @Override
    public void internalTrigger(KogitoNodeInstance from, String type) {
        super.internalTrigger(from, type);
        // if node instance was cancelled, abort
        if (getNodeInstanceContainer().getNodeInstance(getStringId()) == null) {
            return;
        }
        CompositeNode.NodeAndType nodeAndType = getCompositeNode().internalGetLinkedIncomingNode(type);
        if (nodeAndType != null) {
            List<Connection> connections = nodeAndType.getNode().getIncomingConnections(nodeAndType.getType());
            for (Connection connection : connections) {
                if ((connection.getFrom() instanceof CompositeNode.CompositeNodeStart) &&
                        (from == null ||
                                ((CompositeNode.CompositeNodeStart) connection.getFrom()).getInNode().getId().equals(from.getNodeId()))) {
                    NodeInstance nodeInstance = getNodeInstance(connection.getFrom());
                    nodeInstance.trigger(null, nodeAndType.getType());
                    return;
                }
            }
        } else {
            // try to search for start nodes
            boolean found = false;
            for (org.kie.api.definition.process.Node node : getCompositeNode().getNodes()) {
                if (node instanceof StartNode) {
                    StartNode startNode = (StartNode) node;
                    if (startNode.getTriggers() == null || startNode.getTriggers().isEmpty()) {
                        NodeInstance nodeInstance = getNodeInstance(startNode);
                        nodeInstance
                                .trigger(null, null);
                        found = true;
                    }
                }
            }
            if (found) {
                return;
            }
        }
        if (isLinkedIncomingNodeRequired()) {
            throw new IllegalArgumentException(
                    "Could not find start for composite node: " + type);
        }
    }

    protected void internalTriggerOnlyParent(KogitoNodeInstance from, String type) {
        super.internalTrigger(from, type);
    }

    protected boolean isLinkedIncomingNodeRequired() {
        return true;
    }

    public void triggerCompleted(String outType) {
        boolean cancelRemainingInstances = getCompositeNode().isCancelRemainingInstances();
        ((org.jbpm.workflow.instance.NodeInstanceContainer) getNodeInstanceContainer()).setCurrentLevel(getLevel());
        if (cancelRemainingInstances) {
            List<NodeInstance> currentNodeInstances = new ArrayList<>(nodeInstances);
            currentNodeInstances.forEach(ni -> ni.cancel(CancelType.OBSOLETE));
        }
        triggerCompleted(outType, cancelRemainingInstances);
    }

    @Override
    public void cancel(CancelType cancelType) {
        while (!nodeInstances.isEmpty()) {
            NodeInstance nodeInstance = nodeInstances.get(0);
            nodeInstance.cancel(cancelType);
        }
        super.cancel(cancelType);
    }

    @Override
    public void addNodeInstance(final NodeInstance nodeInstance) {
        if (nodeInstance.getStringId() == null) {
            // assign new id only if it does not exist as it might already be set by marshalling
            // it's important to keep same ids of node instances as they might be references e.g. exclusive group
            ((NodeInstanceImpl) nodeInstance).setId(UUID.randomUUID().toString());
        }
        this.nodeInstances.add(nodeInstance);
    }

    @Override
    public void removeNodeInstance(final NodeInstance nodeInstance) {
        this.nodeInstances.remove(nodeInstance);
    }

    @Override
    public Collection<org.kie.api.runtime.process.NodeInstance> getNodeInstances() {
        return Collections.unmodifiableCollection(nodeInstances);
    }

    @Override
    public org.kie.api.runtime.process.NodeInstance getNodeInstance(long l) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<NodeInstance> getNodeInstances(boolean recursive) {
        Collection<NodeInstance> result = nodeInstances;
        if (recursive) {
            result = new ArrayList<>(result);
            for (NodeInstance nodeInstance : nodeInstances) {
                if (nodeInstance instanceof NodeInstanceContainer) {
                    result.addAll(((NodeInstanceContainer) nodeInstance).getNodeInstances(true));
                }
            }
        }
        return Collections.unmodifiableCollection(result);
    }

    @Override
    public Collection<KogitoNodeInstance> getKogitoNodeInstances(Predicate<KogitoNodeInstance> filter,
            boolean recursive) {
        Collection<KogitoNodeInstance> result = new ArrayList<>();
        for (NodeInstance nodeInstance : nodeInstances) {

            if (nodeInstance instanceof KogitoNodeInstance && filter.test(nodeInstance)) {
                result.add(nodeInstance);

            }
            if (nodeInstance instanceof KogitoNodeInstanceContainer && recursive) {
                result.addAll(((KogitoNodeInstanceContainer) nodeInstance).getKogitoNodeInstances(
                        filter, true));
            }
        }
        return result;
    }

    @Override
    public NodeInstance getNodeInstance(String nodeInstanceId) {
        for (NodeInstance nodeInstance : nodeInstances) {
            if (nodeInstance.getStringId().equals(nodeInstanceId)) {
                return nodeInstance;
            }
        }
        return null;
    }

    @Override
    public NodeInstance getNodeInstance(String nodeInstanceId, boolean recursive) {
        for (NodeInstance nodeInstance : getNodeInstances(recursive)) {
            if (nodeInstance.getStringId().equals(nodeInstanceId)) {
                return nodeInstance;
            }
        }
        return null;
    }

    @Override
    public NodeInstance getFirstNodeInstance(WorkflowElementIdentifier nodeId) {
        for (final NodeInstance nodeInstance : this.nodeInstances) {
            if (nodeInstance.getNodeId().equals(nodeId) && nodeInstance.getLevel() == getCurrentLevel()) {
                return nodeInstance;
            }
        }
        return null;
    }

    @Override
    public NodeInstance getNodeInstance(final org.kie.api.definition.process.Node node) {
        if (node instanceof CompositeNode.CompositeNodeStart) {
            return buildCompositeNodeInstance(new CompositeNodeStartInstance(), node);
        }
        if (node instanceof CompositeNode.CompositeNodeEnd) {
            return buildCompositeNodeInstance(new CompositeNodeEndInstance(), node);
        }

        org.kie.api.definition.process.Node actualNode = resolveAsync(node);

        NodeInstanceFactory conf = NodeInstanceFactoryRegistry.getInstance(getProcessInstance().getKnowledgeRuntime().getEnvironment()).getProcessNodeInstanceFactory(actualNode);
        if (conf == null) {
            throw new IllegalArgumentException("Illegal node type: " + node.getClass());
        }
        NodeInstanceImpl nodeInstance = (NodeInstanceImpl) conf.getNodeInstance(actualNode, getProcessInstance(), this);
        if (nodeInstance == null) {
            throw new IllegalArgumentException("Illegal node type: " + node.getClass());
        }
        return nodeInstance;
    }

    private NodeInstance buildCompositeNodeInstance(NodeInstanceImpl nodeInstance, org.kie.api.definition.process.Node node) {
        nodeInstance.setNodeId(node.getId());
        nodeInstance.setNodeInstanceContainer(this);
        nodeInstance.setProcessInstance(getProcessInstance());
        return nodeInstance;
    }

    @Override
    public void signalEvent(String type, Object event, Function<String, Object> varResolver) {
        List<org.kie.api.runtime.process.NodeInstance> currentView = new ArrayList<>(this.nodeInstances);
        super.signalEvent(type, event);

        for (org.kie.api.definition.process.Node node : getCompositeNode().internalGetNodes()) {
            if (node instanceof EventNodeInterface
                    && ((EventNodeInterface) node).acceptsEvent(type, event, ((WorkflowProcessInstanceImpl) this.getProcessInstance()).getEventFilterResolver(this, node, currentView))) {
                if (node instanceof EventNode && ((EventNode) node).getFrom() == null || node instanceof EventSubProcessNode) {
                    EventNodeInstanceInterface eventNodeInstance = (EventNodeInstanceInterface) getNodeInstance(node);
                    eventNodeInstance.signalEvent(type, event, varResolver);
                } else {
                    List<org.kie.api.runtime.process.NodeInstance> nodeInstances = getNodeInstances(node.getId(), currentView);
                    if (nodeInstances != null && !nodeInstances.isEmpty()) {
                        for (org.kie.api.runtime.process.NodeInstance nodeInstance : nodeInstances) {
                            ((EventNodeInstanceInterface) nodeInstance).signalEvent(type, event, varResolver);
                        }
                    }
                }
            }
            if (type.equals(node.getName()) && node.getIncomingConnections().isEmpty()) {
                NodeInstance nodeInstance = getNodeInstance(node);
                if (event != null) {
                    Map<String, Object> dynamicParams = new HashMap<>(getProcessInstance().getVariables());
                    if (event instanceof Map) {
                        dynamicParams.putAll((Map<String, Object>) event);
                    } else {
                        dynamicParams.put("Data", event);
                    }
                    nodeInstance.setDynamicParameters(dynamicParams);
                }
                nodeInstance.trigger(null, Node.CONNECTION_DEFAULT_TYPE);
            }
        }
    }

    @Override
    public void signalEvent(String type, Object event) {
        this.signalEvent(type, event, varName -> this.getVariable(varName));
    }

    public List<NodeInstance> getNodeInstances(WorkflowElementIdentifier nodeId) {
        List<NodeInstance> result = new ArrayList<>();
        for (final NodeInstance nodeInstance : this.nodeInstances) {
            if (nodeInstance.getNodeId().equals(nodeId)) {
                result.add(nodeInstance);
            }
        }
        return result;
    }

    public List<org.kie.api.runtime.process.NodeInstance> getNodeInstances(WorkflowElementIdentifier nodeId, List<org.kie.api.runtime.process.NodeInstance> currentView) {
        List<org.kie.api.runtime.process.NodeInstance> result = new ArrayList<>();
        for (org.kie.api.runtime.process.NodeInstance nodeInstance : currentView) {
            if (nodeInstance.getNodeId().equals(nodeId)) {
                result.add(nodeInstance);
            }
        }
        return result;
    }

    public static class CompositeNodeStartInstance extends NodeInstanceImpl {

        private static final long serialVersionUID = 510l;

        public CompositeNode.CompositeNodeStart getCompositeNodeStart() {
            return (CompositeNode.CompositeNodeStart) getNode();
        }

        @Override
        public void internalTrigger(KogitoNodeInstance from, String type) {
            triggerTime = new Date();
            triggerCompleted();
        }

        public void triggerCompleted() {
            super.triggerCompleted(Node.CONNECTION_DEFAULT_TYPE, true);
        }

    }

    public class CompositeNodeEndInstance extends NodeInstanceImpl {

        private static final long serialVersionUID = 510l;

        public CompositeNode.CompositeNodeEnd getCompositeNodeEnd() {
            return (CompositeNode.CompositeNodeEnd) getNode();
        }

        @Override
        public void internalTrigger(KogitoNodeInstance from, String type) {
            triggerTime = new Date();
            triggerCompleted();
        }

        public void triggerCompleted() {
            CompositeNodeInstance.this.triggerCompleted(
                    getCompositeNodeEnd().getOutType());
        }

    }

    @Override
    public void addEventListeners() {
        super.addEventListeners();
        for (NodeInstance nodeInstance : nodeInstances) {
            if (nodeInstance instanceof EventBasedNodeInstanceInterface) {
                ((EventBasedNodeInstanceInterface) nodeInstance).addEventListeners();
            }
        }
        registerExternalEventNodeListeners();
    }

    private void registerExternalEventNodeListeners() {
        for (org.kie.api.definition.process.Node node : getCompositeNode().getNodes()) {
            if (node instanceof EventNode) {
                if ("external".equals(((EventNode) node).getScope())) {
                    getProcessInstance().addEventListener(
                            ((EventNode) node).getType(), EMPTY_EVENT_LISTENER, true);
                }
            } else if (node instanceof EventSubProcessNode) {
                List<String> events = ((EventSubProcessNode) node).getEvents();
                for (String type : events) {
                    getProcessInstance().addEventListener(type, EMPTY_EVENT_LISTENER, true);
                }
            }
        }
    }

    @Override
    public void removeEventListeners() {
        super.removeEventListeners();
        for (NodeInstance nodeInstance : nodeInstances) {
            if (nodeInstance instanceof EventBasedNodeInstanceInterface) {
                ((EventBasedNodeInstanceInterface) nodeInstance).removeEventListeners();
            }
        }
    }

    @Override
    public void nodeInstanceCompleted(NodeInstance nodeInstance, String outType) {
        org.kie.api.definition.process.Node nodeInstanceNode = nodeInstance.getNode();
        if (nodeInstanceNode != null) {
            Object compensationBoolObj = nodeInstanceNode.getMetaData().get(IS_FOR_COMPENSATION);
            if (compensationBoolObj != null && (Boolean) compensationBoolObj) {
                return;
            }
        }
        if (nodeInstance instanceof FaultNodeInstance || nodeInstance instanceof EndNodeInstance || nodeInstance instanceof EventSubProcessNodeInstance) {
            if (getCompositeNode().isAutoComplete() && nodeInstances.isEmpty()) {
                triggerCompleted(Node.CONNECTION_DEFAULT_TYPE);

            }
        } else {
            throw new IllegalArgumentException(
                    "Completing a node instance that has no outgoing connection not supported.");
        }
    }

    @Override
    public void setState(final int state) {
        this.state = state;
        if (state == STATE_ABORTED) {
            cancel();
        }
    }

    @Override
    public int getState() {
        return this.state;
    }

    @Override
    public int getCurrentLevel() {
        return currentLevel;
    }

    @Override
    public void setCurrentLevel(int currentLevel) {
        this.currentLevel = currentLevel;
    }

    @Override
    public Map<String, Integer> getIterationLevels() {
        return iterationLevels;
    }

}
