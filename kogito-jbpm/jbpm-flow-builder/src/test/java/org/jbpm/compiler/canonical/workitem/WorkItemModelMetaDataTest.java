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

import org.jbpm.compiler.canonical.WorkItemModelMetaData;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.core.datatype.impl.type.IntegerDataType;
import org.jbpm.process.core.datatype.impl.type.StringDataType;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.jbpm.ruleflow.core.WorkflowElementIdentifierFactory;
import org.jbpm.workflow.core.node.HumanTaskNode;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.junit.jupiter.api.Test;
import org.kie.api.definition.process.WorkflowElementIdentifier;
import org.kie.api.definition.process.WorkflowProcess;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.stmt.SwitchEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jbpm.compiler.canonical.WorkItemModelMetaData.*;

public class WorkItemModelMetaDataTest {

    private static final WorkflowElementIdentifier one = WorkflowElementIdentifierFactory.fromExternalFormat("one");
    private static final WorkflowElementIdentifier two = WorkflowElementIdentifierFactory.fromExternalFormat("two");
    private static final WorkflowElementIdentifier three = WorkflowElementIdentifierFactory.fromExternalFormat("three");
    private static final WorkflowElementIdentifier four = WorkflowElementIdentifierFactory.fromExternalFormat("four");

    @Test
    public void testSingleHumanTaskNode() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.approval");
        factory
                .variable("varA", new StringDataType())
                .variable("outA", new StringDataType())
                .name("approval")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one).name("start").done()
                .humanTaskNode(two)
                .name("First Step")
                .taskName("Approval")
                .inMapping("varA", "inputA")
                .outMapping("outputA", "outA")
                .done()
                .endNode(three).name("end").terminate(false).done()
                .connection(one, two)
                .connection(two, three);

        RuleFlowProcess process = factory.validate().getProcess();
        VariableScope variableScope = (VariableScope) process.getDefaultContext(VariableScope.VARIABLE_SCOPE);
        HumanTaskNode taskNode = (HumanTaskNode) process.getNode(two);

        WorkItemModelMetaData metaData = new WorkItemModelMetaData(
                process.getPackageName(), variableScope, "Approval", process.getId());
        metaData.add(taskNode, variableScope);

        assertThat(metaData.getName()).isEqualTo("Approval");
        assertThat(metaData.getInputModelClassName()).isEqualTo("com.myspace.demo.Approval_Approval_TaskInput");
        assertThat(metaData.getOutputModelClassName()).isEqualTo("com.myspace.demo.Approval_Approval_TaskOutput");
        assertThat(metaData.getTaskModelClassName()).isEqualTo("com.myspace.demo.Approval_Approval_TaskModel");
        assertThat(metaData.isAdHoc()).isFalse();
        assertThat(metaData.getNodes()).hasSize(1);

        // Verify compilation units
        CompilationUnit inputUnit = StaticJavaParser.parse(metaData.generateInput());
        ClassOrInterfaceDeclaration inputClass = inputUnit.getClassByName("Approval_Approval_TaskInput").orElseThrow();
        NormalAnnotationExpr userTaskAnn = inputClass.getAnnotationByName("UserTask").orElseThrow().asNormalAnnotationExpr();
        assertThat(userTaskAnn.getPairs()).anyMatch(p -> p.getNameAsString().equals("taskName") && p.getValue().asStringLiteralExpr().getValue().equals("Approval"));

        List<String> inputFields = inputUnit.findAll(FieldDeclaration.class).stream()
                .flatMap(f -> f.getVariables().stream())
                .map(NodeWithSimpleName::getNameAsString)
                .toList();
        assertThat(inputFields).containsExactly("inputA");

        String outputSource = metaData.generateOutput();
        assertThat(outputSource)
                .doesNotContain("$TaskInput$")
                .doesNotContain("$TaskOutput$")
                .doesNotContain("$TaskModel$")
                .doesNotContain("$TaskName$");
        CompilationUnit outputUnit = StaticJavaParser.parse(outputSource);
        assertThat(outputUnit.getClassByName("Approval_Approval_TaskOutput")).isPresent();

        String modelSource = metaData.generateModel();
        assertThat(modelSource)
                .doesNotContain("$TaskInput$")
                .doesNotContain("$TaskOutput$")
                .doesNotContain("$TaskModel$")
                .doesNotContain("$TaskName$");
        CompilationUnit modelUnit = StaticJavaParser.parse(modelSource);
        assertThat(modelUnit.getClassByName("Approval_Approval_TaskModel")).isPresent();

        SwitchEntry switchEntry = metaData.getModelSwitchEntry();
        assertThat(switchEntry.getLabels()).hasSize(1);
        assertThat(switchEntry.getLabels().get(0).asStringLiteralExpr().getValue()).isEqualTo("two");
    }

    @Test
    public void testMultipleHumanTaskNodesSharingTaskName() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.approval");
        factory.variable("varA", new StringDataType())
                .variable("varB", new StringDataType())
                .variable("outA", new StringDataType())
                .variable("outB", new StringDataType())
                .name("sharedapproval")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one).name("start").done()
                .humanTaskNode(two)
                .name("First Step")
                .taskName("SharedApproval")
                .inMapping("varA", "paramA")
                .outMapping("resultA", "outA")
                .done()
                .humanTaskNode(three)
                .name("Second Step")
                .taskName("SharedApproval")
                .inMapping("varB", "paramB")
                .outMapping("resultB", "outB")
                .done()
                .endNode(four).name("end").terminate(false).done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        RuleFlowProcess process = factory.validate().getProcess();
        VariableScope variableScope = (VariableScope) process.getDefaultContext(VariableScope.VARIABLE_SCOPE);
        HumanTaskNode node1 = (HumanTaskNode) process.getNode(two);
        HumanTaskNode node2 = (HumanTaskNode) process.getNode(three);

        WorkItemModelMetaData metaData = new WorkItemModelMetaData(
                process.getPackageName(), variableScope, "SharedApproval", process.getId());
        metaData.add(node1, variableScope);
        metaData.add(node2, variableScope);

        assertThat(metaData.getName()).isEqualTo("SharedApproval");
        assertThat(metaData.getNodes()).hasSize(2);

        // Union of input fields from both nodes
        CompilationUnit inputUnit = StaticJavaParser.parse(metaData.generateInput());
        List<String> inputFields = inputUnit.findAll(FieldDeclaration.class).stream()
                .flatMap(f -> f.getVariables().stream())
                .map(NodeWithSimpleName::getNameAsString)
                .toList();
        assertThat(inputFields).containsExactlyInAnyOrder("paramA", "paramB");

        // Union of output fields from both nodes
        CompilationUnit outputUnit = StaticJavaParser.parse(metaData.generateOutput());
        List<String> outputFields = outputUnit.findAll(FieldDeclaration.class).stream()
                .flatMap(f -> f.getVariables().stream())
                .map(NodeWithSimpleName::getNameAsString)
                .toList();
        assertThat(outputFields).containsExactlyInAnyOrder("resultA", "resultB");

        // SwitchEntry should contain labels for both node IDs
        SwitchEntry switchEntry = metaData.getModelSwitchEntry();
        List<String> labelValues = switchEntry.getLabels().stream()
                .map(expr -> expr.asStringLiteralExpr().getValue())
                .toList();
        assertThat(labelValues).containsExactlyInAnyOrder("two", "three");
    }

    @Test
    public void testConflictingInputParameterTypesThrowsException() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.conflictinginput");
        factory.variable("varA", new StringDataType())
                .variable("varB", new IntegerDataType())
                .name("conflictinginput")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one).name("start").done()
                .humanTaskNode(two)
                .name("First Step")
                .taskName("SharedTask")
                .inMapping("varA", "sharedParam")
                .done()
                .humanTaskNode(three)
                .name("Second Step")
                .taskName("SharedTask")
                .inMapping("varB", "sharedParam")
                .done()
                .endNode(four).name("end").terminate(false).done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        RuleFlowProcess process = factory.validate().getProcess();
        VariableScope variableScope = (VariableScope) process.getDefaultContext(VariableScope.VARIABLE_SCOPE);
        HumanTaskNode node1 = (HumanTaskNode) process.getNode(two);
        HumanTaskNode node2 = (HumanTaskNode) process.getNode(three);

        WorkItemModelMetaData metaData = new WorkItemModelMetaData(
                process.getPackageName(), variableScope, "SharedTask", process.getId());
        metaData.add(node1, variableScope);

        assertThatThrownBy(() -> metaData.add(node2, variableScope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format(
                        CONFLICTING_TYPES_ERROR_TEMPLATE,
                        "input", "sharedParam", "SharedTask", "demo.conflictinginput", "java.lang.String", "java.lang.Integer"));
    }

    @Test
    public void testConflictingOutputParameterTypesThrowsException() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.conflictingoutput");
        factory.variable("outA", new StringDataType())
                .variable("outB", new IntegerDataType())
                .name("conflictingoutput")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one).name("start").done()
                .humanTaskNode(two)
                .name("First Step")
                .taskName("SharedTask")
                .outMapping("sharedOutput", "outA")
                .done()
                .humanTaskNode(three)
                .name("Second Step")
                .taskName("SharedTask")
                .outMapping("sharedOutput", "outB")
                .done()
                .endNode(four).name("end").terminate(false).done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        RuleFlowProcess process = factory.validate().getProcess();
        VariableScope variableScope = (VariableScope) process.getDefaultContext(VariableScope.VARIABLE_SCOPE);
        HumanTaskNode node1 = (HumanTaskNode) process.getNode(two);
        HumanTaskNode node2 = (HumanTaskNode) process.getNode(three);

        WorkItemModelMetaData metaData = new WorkItemModelMetaData(
                process.getPackageName(), variableScope, "SharedTask", process.getId());
        metaData.add(node1, variableScope);

        assertThatThrownBy(() -> metaData.add(node2, variableScope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format(
                        CONFLICTING_TYPES_ERROR_TEMPLATE,
                        "output", "sharedOutput", "SharedTask", "demo.conflictingoutput", "java.lang.String", "java.lang.Integer"));
    }

    @Test
    public void testConflictingWorkItemDefinitionTypesThrowsException() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.conflictingtypes");
        factory.name("conflictingtypes")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one).name("start").done()
                .humanTaskNode(two)
                .name("First Step")
                .taskName("SharedName")
                .done()
                .workItemNode(three)
                .name("SharedName")
                .workName("SignatureDataNode")
                .done()
                .endNode(four).name("end").terminate(false).done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        RuleFlowProcess process = factory.validate().getProcess();
        VariableScope variableScope = (VariableScope) process.getDefaultContext(VariableScope.VARIABLE_SCOPE);
        HumanTaskNode humanNode = (HumanTaskNode) process.getNode(two);
        WorkItemNode customNode = (WorkItemNode) process.getNode(three);

        WorkItemModelMetaData metaData = new WorkItemModelMetaData(
                process.getPackageName(), variableScope, "SharedName", process.getId());
        metaData.add(humanNode, variableScope);

        assertThatThrownBy(() -> metaData.add(customNode, variableScope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format(
                        MISMATCHED_DEFINITION_ERROR_TEMPLATE,
                        "SharedName", "Human Task", "SignatureDataNode", "demo.conflictingtypes"));
    }

    @Test
    public void testMultipleCustomWorkItemNodesSharingTaskName() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.customworkitems");
        factory.variable("var1", new StringDataType())
                .variable("var2", new StringDataType())
                .name("customworkitems")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one).name("start").done()
                .workItemNode(two)
                .name("Node1")
                .workName("SignatureDataNode")
                .inMapping("var1", "input1")
                .done()
                .workItemNode(three)
                .name("Node2")
                .workName("SignatureDataNode")
                .inMapping("var2", "input2")
                .done()
                .endNode(four).name("end").terminate(false).done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        RuleFlowProcess process = factory.validate().getProcess();
        VariableScope variableScope = (VariableScope) process.getDefaultContext(VariableScope.VARIABLE_SCOPE);
        WorkItemNode node1 = (WorkItemNode) process.getNode(two);
        WorkItemNode node2 = (WorkItemNode) process.getNode(three);

        WorkItemModelMetaData metaData = new WorkItemModelMetaData(
                process.getPackageName(), variableScope, "SignatureDataNode", process.getId());
        metaData.add(node1, variableScope);
        metaData.add(node2, variableScope);

        assertThat(metaData.getName()).isEqualTo("SignatureDataNode");
        assertThat(metaData.getInputModelClassName()).isEqualTo("com.myspace.demo.Customworkitems_SignatureDataNode_TaskInput");
        assertThat(metaData.getOutputModelClassName()).isEqualTo("com.myspace.demo.Customworkitems_SignatureDataNode_TaskOutput");
        assertThat(metaData.getTaskModelClassName()).isEqualTo("com.myspace.demo.Customworkitems_SignatureDataNode_TaskModel");
        assertThat(metaData.getNodes()).hasSize(2);

        CompilationUnit inputUnit = StaticJavaParser.parse(metaData.generateInput());
        List<String> inputFields = inputUnit.findAll(FieldDeclaration.class).stream()
                .flatMap(f -> f.getVariables().stream())
                .map(NodeWithSimpleName::getNameAsString)
                .toList();
        assertThat(inputFields).containsExactlyInAnyOrder("input1", "input2");

        SwitchEntry switchEntry = metaData.getModelSwitchEntry();
        List<String> labelValues = switchEntry.getLabels().stream()
                .map(expr -> expr.asStringLiteralExpr().getValue())
                .toList();
        assertThat(labelValues).containsExactlyInAnyOrder("two", "three");
    }

    @Test
    public void testAdHocHumanTaskNode() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.adhoctask");
        factory.name("adhoctask")
                .packageName("com.myspace.demo")
                .dynamic(true)
                .version("1.0")
                .humanTaskNode(one)
                .name("AdHocStep")
                .taskName("AdHocTask");

        RuleFlowProcess process = factory.validate().getProcess();
        VariableScope variableScope = (VariableScope) process.getDefaultContext(VariableScope.VARIABLE_SCOPE);
        HumanTaskNode taskNode = (HumanTaskNode) process.getNode(one);

        WorkItemModelMetaData metaData = new WorkItemModelMetaData(
                process.getPackageName(), variableScope, "AdHocTask", process.getId());
        metaData.add(taskNode, variableScope);

        assertThat(metaData.isAdHoc()).isTrue();
    }
}
