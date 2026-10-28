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
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jbpm.process.instance.ProcessInstance;
import org.jbpm.workflow.core.node.EventSubProcessNode;
import org.jbpm.workflow.core.node.StartNode;
import org.jbpm.workflow.instance.NodeInstanceContainer;
import org.kie.api.definition.process.NodeContainer;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;

import static org.jbpm.workflow.instance.node.TimerNodeInstance.TIMER_TRIGGERED_EVENT;

public class EventSubProcessNodeInstance extends CompositeContextNodeInstance {

    private static final long serialVersionUID = 7095736653568661510L;

    @Override
    protected EventSubProcessNode getCompositeNode() {
        return (EventSubProcessNode) getNode();
    }

    @Override
    public NodeContainer getNodeContainer() {
        return getCompositeNode();
    }

    @Override
    protected String getActivationType() {
        return "RuleFlowStateEventSubProcess-" + getProcessInstance().getProcessId() + "-" + getCompositeNode().getUniqueId();
    }

    @Override
    public void internalTrigger(KogitoNodeInstance from, String type) {
        super.internalTriggerOnlyParent(from, type);
    }

    @Override
    public void signalEvent(String type, Object event, Function<String, Object> varResolver) {
        if (triggerTime == null) {
            triggerTime = new Date();
        }
        if (getNodeInstanceContainer().getNodeInstances().contains(this) || type.startsWith("Error-") || type.equals(TIMER_TRIGGERED_EVENT)) {
            // start it only if it was not already started - meaning there are node instances
            if (this.getNodeInstances().isEmpty()) {
                StartNode startNode = getCompositeNode().findStartNode();
                if (resolveVariables(((EventSubProcessNode) getEventBasedNode()).getEvents()).contains(type) || type.equals(TIMER_TRIGGERED_EVENT)) {
                    // BPMN 2.0 13.4.4: once an interrupting handler is started, no new handlers can be started
                    if (isInterruptedByRunningHandler()) {
                        return;
                    }
                    if (isInterrupting(startNode)) {
                        interruptContainer();
                    }
                    NodeInstance nodeInstance = getNodeInstance(startNode);
                    ((StartNodeInstance) nodeInstance).signalEvent(type, event);
                }
            }
        }
        super.signalEvent(type, event, varResolver);
    }

    private boolean isInterrupting(StartNode startNode) {
        return getCompositeNode().isKeepActive() && startNode.isInterrupting();
    }

    private static boolean isRunningInterruptingHandler(NodeInstance nodeInstance) {
        return nodeInstance instanceof EventSubProcessNodeInstance handler
                && !handler.getNodeInstances().isEmpty()
                && handler.isInterrupting(handler.getCompositeNode().findStartNode());
    }

    private boolean isInterruptedByRunningHandler() {
        return getNodeInstanceContainer().getNodeInstances().stream()
                .anyMatch(ni -> ni != this && isRunningInterruptingHandler(ni));
    }

    /**
     * BPMN 2.0 13.4.4 / Activity lifecycle: when an interrupting event sub process is initiated the parent is interrupted,
     * i.e. all its other active node instances are terminated (the handler itself keeps running), and no new event sub
     * process can be started. The parent itself reaches its final state once the handler completes.
     */
    private void interruptContainer() {
        for (NodeInstance nodeInstance : new ArrayList<>(getNodeInstanceContainer().getNodeInstances())) {
            if (nodeInstance != this && !isRunningInterruptingHandler(nodeInstance) && getNodeInstanceContainer().getNodeInstances().contains(nodeInstance)) {
                ((org.jbpm.workflow.instance.NodeInstance) nodeInstance).cancel(org.jbpm.workflow.instance.NodeInstance.CancelType.ABORTED);
            }
        }
    }

    @Override
    public void nodeInstanceCompleted(org.jbpm.workflow.instance.NodeInstance nodeInstance, String outType) {
        if (nodeInstance instanceof EndNodeInstance) {
            if (getCompositeNode().isKeepActive()) {
                StartNode startNode = getCompositeNode().findStartNode();
                triggerCompleted(true);
                if (startNode.isInterrupting()) {
                    String faultName = getProcessInstance().getOutcome() == null ? "" : getProcessInstance().getOutcome();

                    if (startNode.getMetaData("FaultCode") != null) {
                        faultName = (String) startNode.getMetaData("FaultCode");
                    }
                    if (getNodeInstanceContainer() instanceof ProcessInstance) {
                        ((ProcessInstance) getProcessInstance()).setState(KogitoProcessInstance.STATE_ABORTED, faultName);
                    } else {
                        ((NodeInstanceContainer) getNodeInstanceContainer()).setState(KogitoProcessInstance.STATE_ABORTED);
                    }

                }
            }
        } else {
            throw new IllegalArgumentException(
                    "Completing a node instance that has no outgoing connection not supported.");
        }
    }

    protected List<String> resolveVariables(List<String> events) {
        return events.stream().map(event -> resolveExpression(event)).collect(Collectors.toList());
    }
}
