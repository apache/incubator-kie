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

import org.jbpm.compiler.canonical.ProcessToExecModelGenerator;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.workflow.core.node.WorkItemNode;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;

import static org.jbpm.ruleflow.core.Metadata.CUSTOM_AUTO_START;
import static org.kie.kogito.internal.utils.ConversionUtils.sanitizeClassName;

public class WorkItemNodeModelMetaData extends WorkItemModelMetaData {

    private final WorkItemNode workItemNode;
    private final VariableScope variableScope;

    public WorkItemNodeModelMetaData(String packageName, VariableScope processVariableScope, VariableScope variableScope, WorkItemNode workItemNode, String processId) {
        super(packageName, processVariableScope,
                (String) workItemNode.getWork().getParameters().getOrDefault(TASK_NAME, workItemNode.getName()),
                sanitizeClassName(ProcessToExecModelGenerator.extractProcessId(processId) + "_" + workItemNode.getId().toExternalFormat()),
                processId,
                extractInputParameters(workItemNode, processVariableScope, variableScope, false),
                extractOutputParameters(workItemNode, processVariableScope, variableScope, false));
        this.workItemNode = workItemNode;
        this.variableScope = variableScope;
    }

    public WorkItemNode getWorkItemNode() {
        return workItemNode;
    }

    public VariableScope getVariableScope() {
        return variableScope;
    }

    @Override
    public boolean isAdHoc() {
        return !Boolean.parseBoolean((String) workItemNode.getMetaData(CUSTOM_AUTO_START))
                && (workItemNode.getIncomingConnections() == null || workItemNode.getIncomingConnections().isEmpty());
    }

    @Override
    public SwitchEntry getModelSwitchEntry() {
        SwitchEntry entry = new SwitchEntry();
        entry.setLabels(NodeList.nodeList(new StringLiteralExpr(workItemNode.getId().toExternalFormat())));
        entry.addStatement(new ReturnStmt(new MethodCallExpr(new NameExpr(
                taskModelClassSimpleName), new SimpleName("from")).addArgument(WORK_ITEM)));
        return entry;
    }
}
