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

import java.util.Collections;
import java.util.List;

import org.jbpm.process.core.datatype.impl.type.IntegerDataType;
import org.jbpm.process.core.datatype.impl.type.ObjectDataType;
import org.jbpm.process.core.datatype.impl.type.StringDataType;
import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.jbpm.ruleflow.core.WorkflowElementIdentifierFactory;
import org.junit.jupiter.api.Test;
import org.kie.api.definition.process.Process;
import org.kie.api.definition.process.WorkflowElementIdentifier;
import org.kie.api.definition.process.WorkflowProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jbpm.compiler.canonical.WorkItemModelMetaData.CONFLICTING_TYPES_ERROR_TEMPLATE;
import static org.jbpm.compiler.canonical.WorkItemModelMetaData.MISMATCHED_DEFINITION_ERROR_TEMPLATE;

public class ProcessToExecModelGeneratorTest {

    private static final Logger logger = LoggerFactory.getLogger(ProcessToExecModelGeneratorTest.class);

    private static final WorkflowElementIdentifier one = WorkflowElementIdentifierFactory.fromExternalFormat("one");
    private static final WorkflowElementIdentifier two = WorkflowElementIdentifierFactory.fromExternalFormat("two");
    private static final WorkflowElementIdentifier three = WorkflowElementIdentifierFactory.fromExternalFormat("three");
    private static final WorkflowElementIdentifier four = WorkflowElementIdentifierFactory.fromExternalFormat("four");

    @Test
    public void testScriptAndWorkItemGeneration() {

        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.orders");
        factory
                .variable("order", new ObjectDataType("com.myspace.demo.Order"))
                .variable("approver", new ObjectDataType("String"))
                .name("orders")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .workItemNode(one)
                .name("Log")
                .workName("Log")
                .done()
                .actionNode(two)
                .name("Dump order")
                .action("java", "System.out.println(\"Order has been created \" + order);")
                .done()
                .endNode(three)
                .name("end")
                .terminate(false)
                .done()
                .startNode(four)
                .name("start")
                .done()
                .connection(two, one)
                .connection(four, two)
                .connection(one, three);

        WorkflowProcess process = factory.validate().getProcess();

        ProcessMetaData processMetadata = ProcessToExecModelGenerator.INSTANCE.generate(process);
        assertThat(processMetadata).as("Dumper should return non null class for process").isNotNull();

        logger.debug(processMetadata.getGeneratedClassModel().toString());

        assertThat(processMetadata.getExtractedProcessId()).isEqualTo("orders");
        assertThat(processMetadata.getProcessId()).isEqualTo("demo.orders");
        assertThat(processMetadata.getProcessName()).isEqualTo("orders");
        assertThat(processMetadata.getProcessVersion()).isEqualTo("1.0");
        assertThat(processMetadata.getProcessClassName()).isEqualTo("com.myspace.demo.OrdersProcess");
        assertThat(processMetadata.getGeneratedClassModel()).isNotNull();
        assertThat(processMetadata.getWorkItems()).hasSize(1);
    }

    @Test
    public void testScriptAndWorkItemModelGeneration() {

        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.orders");
        factory
                .variable("order", new ObjectDataType("com.myspace.demo.Order"))
                .variable("approver", new ObjectDataType("String"))
                .name("orders")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .workItemNode(one)
                .name("Log")
                .workName("Log")
                .done()
                .actionNode(two)
                .name("Dump order")
                .action("java", "System.out.println(\"Order has been created \" + order);")
                .done()
                .endNode(three)
                .name("end")
                .terminate(false)
                .done()
                .startNode(four)
                .name("start")
                .done()
                .connection(two, one)
                .connection(four, two)
                .connection(one, three);

        Process process = factory.validate().getProcess();

        ModelMetaData modelMetadata = ProcessToExecModelGenerator.INSTANCE.generateModel((WorkflowProcess) process);
        assertThat(modelMetadata).as("Dumper should return non null class for process").isNotNull();
        assertThat(modelMetadata.getModelClassName()).isEqualTo("com.myspace.demo.OrdersModel");
    }

    @Test
    public void testScriptVariableWithDefaultValue() {

        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.orders");
        factory
                .variable("order", new ObjectDataType("com.myspace.demo.Order"))
                .variable("approver", new StringDataType(), "john", Collections.singletonMap("customTags", null))
                .variable("age", new IntegerDataType(), "1", Collections.singletonMap("customTags", null))
                .name("orders")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .workItemNode(one)
                .name("Log")
                .workName("Log")
                .done()
                .actionNode(two)
                .name("Dump order")
                .action("java", "System.out.println(\"Order has been created \" + order);")
                .done()
                .endNode(three)
                .name("end")
                .terminate(false)
                .done()
                .startNode(four)
                .name("start")
                .done()
                .connection(two, one)
                .connection(four, two)
                .connection(one, three);

        WorkflowProcess process = factory.validate().getProcess();

        ProcessMetaData processMetadata = ProcessToExecModelGenerator.INSTANCE.generate(process);
        assertThat(processMetadata).as("Dumper should return non null class for process").isNotNull();
    }

    @Test
    public void testScriptsWithTrailingComment() {

        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.orders");
        factory
                .variable("order", new ObjectDataType("com.myspace.demo.Order"))
                .variable("approver", new StringDataType(), "john", Collections.singletonMap("customTags", null))
                .variable("age", new IntegerDataType(), "1", Collections.singletonMap("customTags", null))
                .name("orders")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .actionNode(one)
                .name("Dump order")
                .action("java", "System.out.println();\n// this is a comment")
                .done()
                .stateNode(two)
                .name("OnEntry")
                .onEntryAction("java", "System.out.println();\n// this is a comment")
                .done()
                .endNode(three)
                .name("end")
                .terminate(false)
                .done()
                .startNode(four)
                .name("start")
                .done()
                .connection(two, one)
                .connection(four, two)
                .connection(one, three);

        WorkflowProcess process = factory.validate().getProcess();

        ProcessMetaData processMetadata = ProcessToExecModelGenerator.INSTANCE.generate(process);
        assertThat(processMetadata).as("Dumper should return non null class for process").isNotNull();
    }

    @Test
    public void testUppercaseVariableGetterHasJsonProperty() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.uppercase");

        factory
                .variable("FirstName", new StringDataType())
                .name("uppercase")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one)
                .name("start")
                .done()
                .endNode(two)
                .name("end")
                .terminate(false)
                .done()
                .connection(one, two);

        WorkflowProcess process = factory.validate().getProcess();

        ModelMetaData modelMetadata = ProcessToExecModelGenerator.INSTANCE.generateModel(process);

        CompilationUnit generatedModel = modelMetadata.generateUnit();

        MethodDeclaration getter = generatedModel
                .findFirst(MethodDeclaration.class,
                        method -> method.getNameAsString().equals("getFirstName"))
                .orElseThrow(() -> new AssertionError("Generated getter getFirstName() was not found"));

        NormalAnnotationExpr jsonProperty = getter
                .getAnnotationByName(JsonProperty.class.getCanonicalName())
                .orElseThrow(() -> new AssertionError(
                        "Generated getter getFirstName() does not have @JsonProperty"))
                .asNormalAnnotationExpr();

        assertThat(jsonProperty.getPairs()).hasSize(1);
        assertThat(jsonProperty.getPairs().get(0).getNameAsString())
                .isEqualTo("value");
        assertThat(jsonProperty.getPairs().get(0)
                .getValue()
                .asStringLiteralExpr()
                .asString())
                        .isEqualTo("FirstName");
    }

    @Test
    public void testWorkItemModelHyphenatedParamsSanitization() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.test");
        factory
                .name("test-process")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .workItemNode(one)
                .name("RestService")
                .workName("Rest")
                .inMapping("contentType", "HEADER_content-type")
                .inMapping("maxSize", "QUERY_max-size")
                .outMapping("Result", "result")
                .done()
                .startNode(two)
                .name("start")
                .done()
                .endNode(three)
                .name("end")
                .terminate(false)
                .done()
                .connection(two, one)
                .connection(one, three);

        WorkflowProcess process = factory.validate().getProcess();

        List<WorkItemModelMetaData> workItemModels = ProcessToExecModelGenerator.INSTANCE.generateWorkItemModel(process);
        assertThat(workItemModels).hasSize(1);

        WorkItemModelMetaData metaData = workItemModels.get(0);
        String inputSource = metaData.generateInput();
        String outputSource = metaData.generateOutput();

        CompilationUnit inputUnit = StaticJavaParser.parse(inputSource);
        CompilationUnit outputUnit = StaticJavaParser.parse(outputSource);

        // Check input model field names are sanitized
        List<FieldDeclaration> inputFields = inputUnit.findAll(FieldDeclaration.class);
        List<String> inputFieldNames = inputFields.stream()
                .flatMap(f -> f.getVariables().stream())
                .map(NodeWithSimpleName::getNameAsString)
                .toList();
        assertThat(inputFieldNames).contains("HEADER_content_type", "QUERY_max_size");
        assertThat(inputFieldNames).doesNotContain("HEADER_content-type", "QUERY_max-size");

        // Verify that the original hyphenated name is preserved as string literal for map lookups
        assertThat(inputSource).contains("\"HEADER_content-type\"");
        assertThat(inputSource).contains("\"QUERY_max-size\"");

        // Check output model field names
        List<FieldDeclaration> outputFields = outputUnit.findAll(FieldDeclaration.class);
        List<String> outputFieldNames = outputFields.stream()
                .flatMap(f -> f.getVariables().stream())
                .map(NodeWithSimpleName::getNameAsString)
                .toList();
        assertThat(outputFieldNames).contains("Result");
    }

    @Test
    public void testDuplicateTaskNameUserTasksAggregation() {
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
                .startNode(one)
                .name("start")
                .done()
                .humanTaskNode(two)
                .name("First Approval Step")
                .taskName("Approval")
                .inMapping("varA", "inputA")
                .outMapping("outputA", "outA")
                .done()
                .humanTaskNode(three)
                .name("Second Approval Step")
                .taskName("Approval")
                .inMapping("varB", "inputB")
                .outMapping("outputB", "outB")
                .done()
                .endNode(four)
                .name("end")
                .terminate(false)
                .done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        WorkflowProcess process = factory.validate().getProcess();

        List<WorkItemModelMetaData> workItemModels = ProcessToExecModelGenerator.INSTANCE.generateWorkItemModel(process);
        assertThat(workItemModels).hasSize(1);

        WorkItemModelMetaData metaData = workItemModels.get(0);
        assertThat(metaData.getName()).isEqualTo("Approval");
        assertThat(metaData.getInputModelClassName()).isEqualTo("com.myspace.demo.Approval_Approval_TaskInput");
        assertThat(metaData.getOutputModelClassName()).isEqualTo("com.myspace.demo.Approval_Approval_TaskOutput");
        assertThat(metaData.getTaskModelClassName()).isEqualTo("com.myspace.demo.Approval_Approval_TaskModel");

        // Verify input model contains union of both task inputs
        String inputSource = metaData.generateInput();
        CompilationUnit inputUnit = StaticJavaParser.parse(inputSource);
        List<String> inputFieldNames = inputUnit.findAll(FieldDeclaration.class).stream()
                .flatMap(f -> f.getVariables().stream())
                .map(NodeWithSimpleName::getNameAsString)
                .toList();
        assertThat(inputFieldNames).containsExactlyInAnyOrder("inputA", "inputB");

        // Verify output model contains union of both task outputs
        String outputSource = metaData.generateOutput();
        CompilationUnit outputUnit = StaticJavaParser.parse(outputSource);
        List<String> outputFieldNames = outputUnit.findAll(FieldDeclaration.class).stream()
                .flatMap(f -> f.getVariables().stream())
                .map(NodeWithSimpleName::getNameAsString)
                .toList();
        assertThat(outputFieldNames).containsExactlyInAnyOrder("outputA", "outputB");

        // Verify model switch entry contains both node IDs
        var switchEntry = metaData.getModelSwitchEntry();
        List<String> switchLabels = switchEntry.getLabels().stream()
                .map(l -> l.asStringLiteralExpr().getValue())
                .toList();
        assertThat(switchLabels).containsExactlyInAnyOrder("two", "three");
    }

    @Test
    public void testDuplicateTaskNameConflictingTypesThrowsException() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.approval");
        factory
                .variable("varA", new StringDataType())
                .variable("varB", new IntegerDataType())
                .name("approval")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one)
                .name("start")
                .done()
                .humanTaskNode(two)
                .name("Step 1")
                .taskName("Approval")
                .inMapping("varA", "sharedInput") // String
                .done()
                .humanTaskNode(three)
                .name("Step 2")
                .taskName("Approval")
                .inMapping("varB", "sharedInput") // Integer
                .done()
                .endNode(four)
                .name("end")
                .terminate(false)
                .done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        WorkflowProcess process = factory.validate().getProcess();

        assertThatThrownBy(() -> ProcessToExecModelGenerator.INSTANCE.generateWorkItemModel(process))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format(
                        CONFLICTING_TYPES_ERROR_TEMPLATE,
                        "input", "sharedInput", "Approval", "demo.approval", "java.lang.String", "java.lang.Integer"));
    }

    @Test
    public void testCustomWorkItemsGroupedByTaskName() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.customtasks");
        factory
                .name("customtasks")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one)
                .name("start")
                .done()
                .workItemNode(two)
                .name("Check Make offer status")
                .workName("SignatureDataNode")
                .workParameter("TaskName", "SignatureDataNode")
                .done()
                .workItemNode(three)
                .name("Check Decision status")
                .workName("SignatureDataNode")
                .workParameter("TaskName", "SignatureDataNode")
                .done()
                .endNode(four)
                .name("end")
                .terminate(false)
                .done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        WorkflowProcess process = factory.validate().getProcess();

        List<WorkItemModelMetaData> workItemModels = ProcessToExecModelGenerator.INSTANCE.generateWorkItemModel(process);
        assertThat(workItemModels).hasSize(1);

        WorkItemModelMetaData model = workItemModels.get(0);
        assertThat(model.getName()).isEqualTo("SignatureDataNode");
        assertThat(model.getInputModelClassName()).isEqualTo("com.myspace.demo.Customtasks_SignatureDataNode_TaskInput");
        assertThat(model.getOutputModelClassName()).isEqualTo("com.myspace.demo.Customtasks_SignatureDataNode_TaskOutput");
        assertThat(model.getTaskModelClassName()).isEqualTo("com.myspace.demo.Customtasks_SignatureDataNode_TaskModel");

        var switchEntry = model.getModelSwitchEntry();
        List<String> switchLabels = switchEntry.getLabels().stream()
                .map(l -> l.asStringLiteralExpr().getValue())
                .toList();
        assertThat(switchLabels).containsExactlyInAnyOrder("two", "three");
    }

    @Test
    public void testDifferentWorkItemNamesRemainSeparate() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.differentservices");
        factory
                .name("differentservices")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one)
                .name("start")
                .done()
                .workItemNode(two)
                .name("LogService")
                .workName("Log")
                .done()
                .workItemNode(three)
                .name("EmailService")
                .workName("Email")
                .done()
                .endNode(four)
                .name("end")
                .terminate(false)
                .done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        WorkflowProcess process = factory.validate().getProcess();

        List<WorkItemModelMetaData> workItemModels = ProcessToExecModelGenerator.INSTANCE.generateWorkItemModel(process);
        assertThat(workItemModels).hasSize(2);

        WorkItemModelMetaData model1 = workItemModels.get(0);
        WorkItemModelMetaData model2 = workItemModels.get(1);

        assertThat(model1.getName()).isEqualTo("LogService");
        assertThat(model2.getName()).isEqualTo("EmailService");
    }

    @Test
    public void testDuplicateTaskNameConflictingOutputTypesThrowsException() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.conflictingoutput");
        factory
                .variable("outA", new StringDataType())
                .variable("outB", new IntegerDataType())
                .name("conflictingoutput")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one)
                .name("start")
                .done()
                .humanTaskNode(two)
                .name("Step 1")
                .taskName("Approval")
                .outMapping("sharedOutput", "outA") // String
                .done()
                .humanTaskNode(three)
                .name("Step 2")
                .taskName("Approval")
                .outMapping("sharedOutput", "outB") // Integer
                .done()
                .endNode(four)
                .name("end")
                .terminate(false)
                .done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        WorkflowProcess process = factory.validate().getProcess();

        assertThatThrownBy(() -> ProcessToExecModelGenerator.INSTANCE.generateWorkItemModel(process))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format(
                        CONFLICTING_TYPES_ERROR_TEMPLATE,
                        "output", "sharedOutput", "Approval", "demo.conflictingoutput", "java.lang.String", "java.lang.Integer"));
    }

    @Test
    public void testConflictingWorkItemDefinitionTypesThrowsException() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.mixeddefinitions");
        factory
                .name("mixeddefinitions")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one)
                .name("start")
                .done()
                .humanTaskNode(two)
                .name("First Step")
                .taskName("SharedAction")
                .done()
                .workItemNode(three)
                .name("SharedAction")
                .workName("SignatureDataNode")
                .done()
                .endNode(four)
                .name("end")
                .terminate(false)
                .done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        WorkflowProcess process = factory.validate().getProcess();

        assertThatThrownBy(() -> ProcessToExecModelGenerator.INSTANCE.generateWorkItemModel(process))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format(
                        MISMATCHED_DEFINITION_ERROR_TEMPLATE,
                        "SharedAction", "Human Task", "SignatureDataNode", "demo.mixeddefinitions"));
    }

    @Test
    public void testDifferentCustomWorkItemDefinitionTypesCollidingThrowsException() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.collidingdefinitions");
        factory
                .name("collidingdefinitions")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one)
                .name("start")
                .done()
                .workItemNode(two)
                .name("Node1")
                .workName("Rest")
                .workParameter("TaskName", "SharedCustomTask")
                .done()
                .workItemNode(three)
                .name("Node2")
                .workName("Email")
                .workParameter("TaskName", "SharedCustomTask")
                .done()
                .endNode(four)
                .name("end")
                .terminate(false)
                .done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        WorkflowProcess process = factory.validate().getProcess();

        assertThatThrownBy(() -> ProcessToExecModelGenerator.INSTANCE.generateWorkItemModel(process))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format(
                        MISMATCHED_DEFINITION_ERROR_TEMPLATE,
                        "SharedCustomTask", "Rest", "Email", "demo.collidingdefinitions"));
    }

    @Test
    public void testCompatibleCustomWorkItemsParameterUnion() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("demo.customunion");
        factory
                .variable("varA", new StringDataType())
                .variable("varB", new StringDataType())
                .variable("outA", new StringDataType())
                .variable("outB", new StringDataType())
                .name("customunion")
                .packageName("com.myspace.demo")
                .dynamic(false)
                .version("1.0")
                .startNode(one)
                .name("start")
                .done()
                .workItemNode(two)
                .name("Step 1")
                .workName("SignatureDataNode")
                .workParameter("TaskName", "SignatureTask")
                .inMapping("varA", "signatureDocA")
                .outMapping("signedDocA", "outA")
                .done()
                .workItemNode(three)
                .name("Step 2")
                .workName("SignatureDataNode")
                .workParameter("TaskName", "SignatureTask")
                .inMapping("varB", "signatureDocB")
                .outMapping("signedDocB", "outB")
                .done()
                .endNode(four)
                .name("end")
                .terminate(false)
                .done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);

        WorkflowProcess process = factory.validate().getProcess();

        List<WorkItemModelMetaData> workItemModels = ProcessToExecModelGenerator.INSTANCE.generateWorkItemModel(process);
        assertThat(workItemModels).hasSize(1);

        WorkItemModelMetaData model = workItemModels.get(0);
        assertThat(model.getName()).isEqualTo("SignatureTask");
        assertThat(model.getWorkName()).isEqualTo("SignatureDataNode");

        CompilationUnit inputUnit = StaticJavaParser.parse(model.generateInput());
        List<String> inputFields = inputUnit.findAll(FieldDeclaration.class).stream()
                .flatMap(f -> f.getVariables().stream())
                .map(NodeWithSimpleName::getNameAsString)
                .toList();
        assertThat(inputFields).containsExactlyInAnyOrder("signatureDocA", "signatureDocB");

        CompilationUnit outputUnit = StaticJavaParser.parse(model.generateOutput());
        List<String> outputFields = outputUnit.findAll(FieldDeclaration.class).stream()
                .flatMap(f -> f.getVariables().stream())
                .map(NodeWithSimpleName::getNameAsString)
                .toList();
        assertThat(outputFields).containsExactlyInAnyOrder("signedDocA", "signedDocB");
    }
}
