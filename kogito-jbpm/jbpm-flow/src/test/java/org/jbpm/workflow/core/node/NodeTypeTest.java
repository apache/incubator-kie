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

package org.jbpm.workflow.core.node;

import org.junit.jupiter.api.Test;
import org.kie.api.definition.process.Node;
import org.kie.api.definition.process.NodeType;

import static org.assertj.core.api.Assertions.assertThat;

public class NodeTypeTest {

    @Test
    public void testNodeTypeSpec() {

        Node node = new ActionNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.SCRIPT_TASK);
        node = new ForEachNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.FOR_EACH);
        node = new MilestoneNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.MILESTONE);
        node = new FaultNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.FAULT);
        node = new Join();
        assertThat(node.getNodeType()).isEqualTo(NodeType.COMPLEX_GATEWAY);
        node = new Split(Split.TYPE_AND);
        assertThat(node.getNodeType()).isEqualTo(NodeType.PARALLEL_GATEWAY);
        node = new Split(Split.TYPE_OR);
        assertThat(node.getNodeType()).isEqualTo(NodeType.INCLUSIVE_GATEWAY);
        node = new Split(Split.TYPE_XOR);
        assertThat(node.getNodeType()).isEqualTo(NodeType.EXCLUSIVE_GATEWAY);
        node = new Split(Split.TYPE_XAND);
        assertThat(node.getNodeType()).isEqualTo(NodeType.EVENT_BASED_GATEWAY);
        node = new ThrowLinkNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.THROW_LINK);
        node = new CatchLinkNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.CATCH_LINK);
        node = new RuleSetNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.BUSINESS_RULE);
        node = new TimerNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.TIMER);
        node = new WorkItemNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.WORKITEM_TASK);
        node = new SubProcessNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.SUBPROCESS);
        node = new StateNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.CONDITIONAL);
        node = new StartNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.START);
        node = new HumanTaskNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.HUMAN_TASK);
        node = new EventNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.CATCH_EVENT);
        node = new EndNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.END);
        node = new DynamicNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.AD_HOC_SUBPROCESS);
        node = new EventSubProcessNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.EVENT_SUBPROCESS);
        node = new BoundaryEventNode();
        assertThat(node.getNodeType()).isEqualTo(NodeType.BOUNDARY_EVENT);
    }
}
