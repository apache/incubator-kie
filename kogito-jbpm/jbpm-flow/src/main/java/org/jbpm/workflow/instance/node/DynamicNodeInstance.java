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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.core.common.InternalAgenda;
import org.jbpm.util.ContextFactory;
import org.jbpm.workflow.core.node.DynamicNode;
import org.kie.api.definition.process.Node;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.kogito.event.process.ContextAwareEventListener;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;

import static org.jbpm.ruleflow.core.Metadata.IS_FOR_COMPENSATION;
import static org.jbpm.workflow.core.Node.CONNECTION_DEFAULT_TYPE;
import static org.jbpm.workflow.core.impl.ExtendedNodeImpl.EVENT_NODE_ENTER;

public class DynamicNodeInstance extends CompositeContextNodeInstance {

    private static final long serialVersionUID = 510l;

    private ProcessEventListener completeEventListener;
    private ProcessEventListener activationEventListener;

    public DynamicNodeInstance() {
        activationEventListener = ContextAwareEventListener.using(listener -> {
            if (canActivate() && getState() == KogitoProcessInstance.STATE_PENDING) {
                triggerActivated();
            }
        });
        completeEventListener = ContextAwareEventListener.using(listener -> {
            // autocomplete is only evaluated when a child node instance completes (see nodeInstanceCompleted),
            // otherwise an empty ad hoc subprocess would be closed by any unrelated event, e.g. a variable change
            if (!getDynamicNode().isAutoComplete() && canComplete() && getState() == KogitoProcessInstance.STATE_ACTIVE) {
                triggerCompleted(CONNECTION_DEFAULT_TYPE);
            }
        });
    }

    private String getRuleFlowGroupName() {
        return getNodeName();
    }

    protected DynamicNode getDynamicNode() {
        return (DynamicNode) getNode();
    }

    @Override
    public String getNodeName() {
        return resolveExpression(super.getNodeName());
    }

    @Override
    public void internalTrigger(KogitoNodeInstance from, String type) {
        triggerTime = new Date();
        triggerEvent(EVENT_NODE_ENTER);

        // if node instance was cancelled, abort
        if (getNodeInstanceContainer().getNodeInstance(getStringId()) == null) {
            return;
        }
        if (canActivate()) {
            triggerActivated();
        } else {
            setState(KogitoProcessInstance.STATE_PENDING);
        }
    }

    private void triggerActivated() {
        setState(KogitoProcessInstance.STATE_ACTIVE);
        // activate ad hoc fragments if they are marked as such
        List<Node> autoStartNodes = getDynamicNode().getAutoStartNodes();
        autoStartNodes.forEach(autoStartNode -> triggerSelectedNode(autoStartNode, null));
    }

    private boolean canActivate() {
        return getDynamicNode().canActivate(ContextFactory.fromNode(this));
    }

    private boolean canComplete() {
        return getNodeInstances(false).isEmpty() && getDynamicNode().canComplete(ContextFactory.fromNode(this));
    }

    @Override
    public void addEventListeners() {
        super.addEventListeners();
        getProcessInstance().getKnowledgeRuntime().getProcessRuntime().addEventListener(activationEventListener);
        getProcessInstance().getKnowledgeRuntime().getProcessRuntime().addEventListener(completeEventListener);

    }

    @Override
    public void removeEventListeners() {
        super.removeEventListeners();
        getProcessInstance().getKnowledgeRuntime().getProcessRuntime().removeEventListener(activationEventListener);
        getProcessInstance().getKnowledgeRuntime().getProcessRuntime().removeEventListener(completeEventListener);
    }

    @Override
    public void nodeInstanceCompleted(org.jbpm.workflow.instance.NodeInstance nodeInstance, String outType) {
        Node nodeInstanceNode = nodeInstance.getNode();
        if (nodeInstanceNode != null) {
            Object compensationBoolObj = nodeInstanceNode.getMetaData().get(IS_FOR_COMPENSATION);
            if (Boolean.TRUE.equals(compensationBoolObj)) {
                return;
            }
        }
        // TODO what if we reach the end of one branch but others might still need to be created ?
        // TODO are we sure there will always be node instances left if we are not done yet?
        if (isTerminated(nodeInstance) || canComplete()) {
            triggerCompleted(CONNECTION_DEFAULT_TYPE);
        }
    }

    @Override
    public void triggerCompleted(String outType) {
        setState(KogitoProcessInstance.STATE_COMPLETED);
        if (getProcessInstance().getKnowledgeRuntime().getAgenda() != null) {
            ((InternalAgenda) getProcessInstance().getKnowledgeRuntime().getAgenda()).getAgendaGroupsManager()
                    .deactivateRuleFlowGroup(getRuleFlowGroupName());
        }
        super.triggerCompleted(outType);
    }

    protected boolean isTerminated(KogitoNodeInstance from) {
        if (from instanceof EndNodeInstance) {
            return ((EndNodeInstance) from).getEndNode().isTerminate();
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    protected void triggerSelectedNode(Node node, Object event) {
        org.jbpm.workflow.instance.NodeInstance nodeInstance = getNodeInstance(node);
        if (event != null) {
            Map<String, Object> dynamicParams = new HashMap<>();
            if (event instanceof Map) {
                dynamicParams.putAll((Map<String, Object>) event);
            } else {
                dynamicParams.put("Data", event);
            }
            nodeInstance.setDynamicParameters(dynamicParams);
        }
        nodeInstance.trigger(null, CONNECTION_DEFAULT_TYPE);
    }
}
