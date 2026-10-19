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

import java.util.List;

import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.core.datatype.impl.type.ObjectDataType;
import org.jbpm.process.core.datatype.impl.type.StringDataType;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.jbpm.ruleflow.core.WorkflowElementIdentifierFactory;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.junit.jupiter.api.Test;
import org.kie.api.definition.process.WorkflowElementIdentifier;
import org.kie.api.definition.process.WorkflowProcess;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.stmt.SwitchEntry;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkItemNodeModelMetaDataTest {

    private static final WorkflowElementIdentifier one = WorkflowElementIdentifierFactory.fromExternalFormat("one");
    private static final WorkflowElementIdentifier two = WorkflowElementIdentifierFactory.fromExternalFormat("two");
    private static final WorkflowElementIdentifier three = WorkflowElementIdentifierFactory.fromExternalFormat("three");

    @Test
    public void testWorkItemNodeModelGeneration() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.service");
        factory
                .variable("requestData", new StringDataType())
                .variable("responseData", new ObjectDataType("com.myspace.demo.Result"))
                .name("service")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one).name("start").done()
                .workItemNode(two)
                .name("RestCall")
                .workName("Rest")
                .workParameter("url", "https://api.example.com")
                .inMapping("requestData", "payload")
                .outMapping("result", "responseData")
                .done()
                .endNode(three).name("end").terminate(false).done()
                .connection(one, two)
                .connection(two, three);

        WorkflowProcess process = factory.validate().getProcess();
        VariableScope variableScope = (VariableScope) ((RuleFlowProcess) process).getDefaultContext(VariableScope.VARIABLE_SCOPE);
        WorkItemNode workItemNode = (WorkItemNode) ((RuleFlowProcess) process).getNode(two);

        WorkItemNodeModelMetaData metaData = new WorkItemNodeModelMetaData(
                process.getPackageName(), variableScope, variableScope, workItemNode, process.getId());

        assertThat(metaData.getName()).isEqualTo("RestCall");
        assertThat(metaData.getInputModelClassName()).isEqualTo("com.myspace.demo.Service_two_TaskInput");
        assertThat(metaData.getOutputModelClassName()).isEqualTo("com.myspace.demo.Service_two_TaskOutput");
        assertThat(metaData.getTaskModelClassName()).isEqualTo("com.myspace.demo.Service_two_TaskModel");
        assertThat(metaData.isAdHoc()).isFalse();
        assertThat(metaData.getWorkItemNode()).isSameAs(workItemNode);
        assertThat(metaData.getVariableScope()).isSameAs(variableScope);

        // Verify compilation units
        CompilationUnit inputUnit = StaticJavaParser.parse(metaData.generateInput());
        assertThat(inputUnit.getClassByName("Service_two_TaskInput")).isPresent();
        List<String> inputFields = inputUnit.findAll(FieldDeclaration.class).stream()
                .flatMap(f -> f.getVariables().stream())
                .map(v -> v.getNameAsString())
                .toList();
        assertThat(inputFields).containsExactlyInAnyOrder("payload", "url");

        CompilationUnit outputUnit = StaticJavaParser.parse(metaData.generateOutput());
        assertThat(outputUnit.getClassByName("Service_two_TaskOutput")).isPresent();
        List<String> outputFields = outputUnit.findAll(FieldDeclaration.class).stream()
                .flatMap(f -> f.getVariables().stream())
                .map(v -> v.getNameAsString())
                .toList();
        assertThat(outputFields).containsExactly("result");

        String modelSource = metaData.generateModel();
        assertThat(modelSource)
                .doesNotContain("$TaskInput$")
                .doesNotContain("$TaskOutput$")
                .doesNotContain("$TaskModel$")
                .doesNotContain("$TaskName$");
        CompilationUnit modelUnit = StaticJavaParser.parse(modelSource);
        assertThat(modelUnit.getClassByName("Service_two_TaskModel")).isPresent();

        SwitchEntry switchEntry = metaData.getModelSwitchEntry();
        assertThat(switchEntry.getLabels()).hasSize(1);
        assertThat(switchEntry.getLabels().get(0).asStringLiteralExpr().getValue()).isEqualTo("two");
    }

    @Test
    public void testAdHocWorkItemNode() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.adhocservice");
        factory
                .name("adhocservice")
                .packageName("com.myspace.demo")
                .dynamic(true)
                .version("1.0")
                .workItemNode(one)
                .name("AdHocService")
                .workName("CustomService")
                .done();

        WorkflowProcess process = factory.validate().getProcess();
        VariableScope variableScope = (VariableScope) ((RuleFlowProcess) process).getDefaultContext(VariableScope.VARIABLE_SCOPE);
        WorkItemNode workItemNode = (WorkItemNode) ((RuleFlowProcess) process).getNode(one);

        WorkItemNodeModelMetaData metaData = new WorkItemNodeModelMetaData(
                process.getPackageName(), variableScope, variableScope, workItemNode, process.getId());

        assertThat(metaData.isAdHoc()).isTrue();
    }
}
