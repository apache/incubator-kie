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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.workflow.core.node.HumanTaskNode;

/**
 * Model of the human tasks of a process that share the same <code>TaskName</code>: they are generated as one single set of
 * input, output and task model classes, and exposed through one single set of REST endpoints.
 */
public class HumanTaskWorkItemModelMetaData extends WorkItemModelMetaData {

    private final List<WorkItemNodeContext> nodes = new ArrayList<>();

    public HumanTaskWorkItemModelMetaData(String taskName, String processId, String packageName, VariableScope processVariableScope) {
        super(taskName, normalizeTaskName(taskName), processId, packageName, processVariableScope);
    }

    public HumanTaskWorkItemModelMetaData add(HumanTaskNode humanTaskNode, VariableScope nodeVariableScope) {
        String nodeTaskName = resolveTaskName(humanTaskNode);
        if (!taskName.equals(nodeTaskName)) {
            throw new IllegalArgumentException(String.format(
                    CONFLICTING_TASK_NAMES_ERROR_TEMPLATE,
                    taskName, nodeTaskName, processId, taskModelClassName));
        }

        if (this.work == null) {
            this.work = Objects.requireNonNull(humanTaskNode.getWork(), "WorkItemNode work cannot be null");
        }

        nodes.add(new WorkItemNodeContext(humanTaskNode, nodeVariableScope));
        addNodeParameters(humanTaskNode, nodeVariableScope);
        return this;
    }

    @Override
    public List<WorkItemNodeContext> getNodes() {
        return Collections.unmodifiableList(nodes);
    }
}
