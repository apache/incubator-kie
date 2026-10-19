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
import org.jbpm.process.core.datatype.impl.type.IntegerDataType;
import org.jbpm.process.core.datatype.impl.type.StringDataType;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.jbpm.ruleflow.core.WorkflowElementIdentifierFactory;
import org.jbpm.workflow.core.node.HumanTaskNode;
import org.junit.jupiter.api.Test;
import org.kie.api.definition.process.WorkflowElementIdentifier;
import org.kie.api.definition.process.WorkflowProcess;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.stmt.SwitchEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HumanTaskWorkItemModelMetaDataTest {

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

        WorkflowProcess process = factory.validate().getProcess();
        VariableScope variableScope = (VariableScope) ((RuleFlowProcess) process).getDefaultContext(VariableScope.VARIABLE_SCOPE);
        HumanTaskNode taskNode = (HumanTaskNode) ((RuleFlowProcess) process).getNode(two);

        HumanTaskWorkItemModelMetaData metaData = new HumanTaskWorkItemModelMetaData(
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
                .map(v -> v.getNameAsString())
                .toList();
        assertThat(inputFields).containsExactly("inputA");

        String outputSource = metaData.generateOutput();
        assertThat(outputSource)
                .doesNotContain("XXXTaskOutput")
                .contains("public static Approval_Approval_TaskOutput fromMap(Map<String, Object> params)");
        CompilationUnit outputUnit = StaticJavaParser.parse(outputSource);
        List<String> outputFields = outputUnit.findAll(FieldDeclaration.class).stream()
                .flatMap(f -> f.getVariables().stream())
                .map(v -> v.getNameAsString())
                .toList();
        assertThat(outputFields).containsExactly("outputA");

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
    public void testMultipleHumanTaskNodesAggregation() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.approval");
        factory
                .variable("varA", new StringDataType())
                .variable("varB", new IntegerDataType())
                .variable("outA", new StringDataType())
                .variable("outB", new IntegerDataType())
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
                .humanTaskNode(three)
                .name("Second Step")
                .taskName("Approval")
                .inMapping("varB", "inputB")
                .outMapping("outputB", "outB")
                .done()
                .endNode(four).name("end").terminate(false).done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        WorkflowProcess process = factory.validate().getProcess();
        VariableScope variableScope = (VariableScope) ((RuleFlowProcess) process).getDefaultContext(VariableScope.VARIABLE_SCOPE);
        HumanTaskNode taskNode1 = (HumanTaskNode) ((RuleFlowProcess) process).getNode(two);
        HumanTaskNode taskNode2 = (HumanTaskNode) ((RuleFlowProcess) process).getNode(three);

        HumanTaskWorkItemModelMetaData metaData = new HumanTaskWorkItemModelMetaData(
                process.getPackageName(), variableScope, "Approval", process.getId());
        metaData.add(taskNode1, variableScope);
        metaData.add(taskNode2, variableScope);

        assertThat(metaData.getNodes()).hasSize(2);

        // Verify aggregated input fields (union)
        CompilationUnit inputUnit = StaticJavaParser.parse(metaData.generateInput());
        List<String> inputFields = inputUnit.findAll(FieldDeclaration.class).stream()
                .flatMap(f -> f.getVariables().stream())
                .map(v -> v.getNameAsString())
                .toList();
        assertThat(inputFields).containsExactlyInAnyOrder("inputA", "inputB");

        // Verify aggregated output fields (union)
        CompilationUnit outputUnit = StaticJavaParser.parse(metaData.generateOutput());
        List<String> outputFields = outputUnit.findAll(FieldDeclaration.class).stream()
                .flatMap(f -> f.getVariables().stream())
                .map(v -> v.getNameAsString())
                .toList();
        assertThat(outputFields).containsExactlyInAnyOrder("outputA", "outputB");

        // Verify multi-case switch entry
        SwitchEntry switchEntry = metaData.getModelSwitchEntry();
        List<String> labels = switchEntry.getLabels().stream()
                .map(l -> l.asStringLiteralExpr().getValue())
                .toList();
        assertThat(labels).containsExactlyInAnyOrder("two", "three");
    }

    @Test
    public void testConflictingInputParameterTypesThrowsException() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.approval");
        factory
                .variable("varA", new StringDataType())
                .variable("varB", new IntegerDataType())
                .name("approval")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one).name("start").done()
                .humanTaskNode(two)
                .name("Step 1")
                .taskName("Approval")
                .inMapping("varA", "sharedParam") // String
                .done()
                .humanTaskNode(three)
                .name("Step 2")
                .taskName("Approval")
                .inMapping("varB", "sharedParam") // Integer
                .done()
                .endNode(four).name("end").terminate(false).done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        WorkflowProcess process = factory.validate().getProcess();
        VariableScope variableScope = (VariableScope) ((RuleFlowProcess) process).getDefaultContext(VariableScope.VARIABLE_SCOPE);
        HumanTaskNode taskNode1 = (HumanTaskNode) ((RuleFlowProcess) process).getNode(two);
        HumanTaskNode taskNode2 = (HumanTaskNode) ((RuleFlowProcess) process).getNode(three);

        HumanTaskWorkItemModelMetaData metaData = new HumanTaskWorkItemModelMetaData(
                process.getPackageName(), variableScope, "Approval", process.getId());
        metaData.add(taskNode1, variableScope);

        assertThatThrownBy(() -> metaData.add(taskNode2, variableScope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conflicting types for input parameter 'sharedParam' in task 'Approval' of process 'demo.approval'");
    }

    @Test
    public void testConflictingOutputParameterTypesThrowsException() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.approval");
        factory
                .variable("outA", new StringDataType())
                .variable("outB", new IntegerDataType())
                .name("approval")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one).name("start").done()
                .humanTaskNode(two)
                .name("Step 1")
                .taskName("Approval")
                .outMapping("sharedOut", "outA") // String
                .done()
                .humanTaskNode(three)
                .name("Step 2")
                .taskName("Approval")
                .outMapping("sharedOut", "outB") // Integer
                .done()
                .endNode(four).name("end").terminate(false).done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        WorkflowProcess process = factory.validate().getProcess();
        VariableScope variableScope = (VariableScope) ((RuleFlowProcess) process).getDefaultContext(VariableScope.VARIABLE_SCOPE);
        HumanTaskNode taskNode1 = (HumanTaskNode) ((RuleFlowProcess) process).getNode(two);
        HumanTaskNode taskNode2 = (HumanTaskNode) ((RuleFlowProcess) process).getNode(three);

        HumanTaskWorkItemModelMetaData metaData = new HumanTaskWorkItemModelMetaData(
                process.getPackageName(), variableScope, "Approval", process.getId());
        metaData.add(taskNode1, variableScope);

        assertThatThrownBy(() -> metaData.add(taskNode2, variableScope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conflicting types for output parameter 'sharedOut' in task 'Approval' of process 'demo.approval'");
    }

    @Test
    public void testAdHocStatus() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.adhoc");
        factory
                .name("adhoc")
                .packageName("com.myspace.demo")
                .dynamic(true)
                .version("1.0")
                .humanTaskNode(one)
                .name("AdHoc Step")
                .taskName("AdHocTask")
                .done();

        WorkflowProcess process = factory.validate().getProcess();
        VariableScope variableScope = (VariableScope) ((RuleFlowProcess) process).getDefaultContext(VariableScope.VARIABLE_SCOPE);
        HumanTaskNode taskNode = (HumanTaskNode) ((RuleFlowProcess) process).getNode(one);

        HumanTaskWorkItemModelMetaData metaData = new HumanTaskWorkItemModelMetaData(
                process.getPackageName(), variableScope, "AdHocTask", process.getId());
        metaData.add(taskNode, variableScope);

        assertThat(metaData.isAdHoc()).isTrue();
    }
}
