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
package org.jbpm.compiler.canonical;

import java.util.List;
import java.util.Objects;

import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.workflow.core.node.WorkItemNode;

/**
 * Model of one single regular (non human task) work item node. Regular work items are never grouped: each node
 * has its own model classes, named after the node id, and its own REST endpoints, named after the node name.
 */
public class WorkItemNodeModelMetaData extends WorkItemModelMetaData {

    private final WorkItemNodeContext nodeContext;

    public WorkItemNodeModelMetaData(WorkItemNode workItemNode, VariableScope nodeVariableScope, String processId, String packageName,
            VariableScope processVariableScope) {
        super(workItemNode.getName(), workItemNode.getId().toExternalFormat(), processId, packageName, processVariableScope);
        this.work = Objects.requireNonNull(workItemNode.getWork(), "WorkItemNode work cannot be null");
        this.nodeContext = new WorkItemNodeContext(workItemNode, nodeVariableScope);
        addNodeParameters(workItemNode, nodeVariableScope);
    }

    public WorkItemNode getWorkItemNode() {
        return nodeContext.workItemNode();
    }

    @Override
    public List<WorkItemNodeContext> getNodes() {
        return List.of(nodeContext);
    }
}
