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
package org.jbpm.compiler.canonical.workitem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jbpm.compiler.canonical.ProcessToExecModelGenerator;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.workflow.core.node.WorkItemNode;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;

import static org.jbpm.ruleflow.core.Metadata.CUSTOM_AUTO_START;
import static org.kie.kogito.internal.utils.ConversionUtils.sanitizeClassName;

public class HumanTaskWorkItemModelMetaData extends WorkItemModelMetaData {

    public record WorkItemNodeContext(WorkItemNode workItemNode, VariableScope variableScope) {
    }

    private final List<WorkItemNodeContext> nodes = new ArrayList<>();

    public HumanTaskWorkItemModelMetaData(String packageName, VariableScope processVariableScope, String taskName, String processId) {
        super(packageName, processVariableScope, taskName,
                sanitizeClassName(ProcessToExecModelGenerator.extractProcessId(processId) + "_" + sanitizeClassName(taskName)),
                processId);
    }

    public HumanTaskWorkItemModelMetaData add(WorkItemNode workItemNode, VariableScope nodeVariableScope) {
        nodes.add(new WorkItemNodeContext(workItemNode, nodeVariableScope));

        Map<String, ParameterDefinition> nodeInputs = extractInputParameters(
                workItemNode, processVariableScope, nodeVariableScope, true);
        mergeParameters(inputParamDefs, nodeInputs, "input");

        Map<String, ParameterDefinition> nodeOutputs = extractOutputParameters(
                workItemNode, processVariableScope, nodeVariableScope, true);
        mergeParameters(outputParamDefs, nodeOutputs, "output");

        return this;
    }

    private void mergeParameters(Map<String, ParameterDefinition> target, Map<String, ParameterDefinition> source, String direction) {
        for (Map.Entry<String, ParameterDefinition> entry : source.entrySet()) {
            ParameterDefinition existing = target.get(entry.getKey());
            if (existing != null) {
                if (!existing.type().equals(entry.getValue().type())) {
                    throw new IllegalArgumentException(String.format(
                            "Conflicting types for %s parameter '%s' in task '%s' of process '%s': %s vs %s",
                            direction, entry.getValue().originalKey(), taskName, processId, existing.type(), entry.getValue().type()));
                }
            } else {
                target.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public List<WorkItemNodeContext> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    @Override
    public boolean isAdHoc() {
        return nodes.stream().anyMatch(nodeCtx -> isNodeAdHoc(nodeCtx.workItemNode()));
    }

    private boolean isNodeAdHoc(WorkItemNode workItemNode) {
        return !Boolean.parseBoolean((String) workItemNode.getMetaData(CUSTOM_AUTO_START))
                && (workItemNode.getIncomingConnections() == null || workItemNode.getIncomingConnections().isEmpty());
    }

    @Override
    public SwitchEntry getModelSwitchEntry() {
        SwitchEntry entry = new SwitchEntry();
        NodeList<Expression> labels = new NodeList<>();
        for (WorkItemNodeContext nodeCtx : nodes) {
            labels.add(new StringLiteralExpr(nodeCtx.workItemNode().getId().toExternalFormat()));
        }
        entry.setLabels(labels);
        entry.addStatement(new ReturnStmt(new MethodCallExpr(new NameExpr(
                taskModelClassSimpleName), new SimpleName("from")).addArgument(WORK_ITEM)));
        return entry;
    }
}
