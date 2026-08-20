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
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessToExecModelGeneratorTest {

    private static final Logger logger = LoggerFactory.getLogger(ProcessToExecModelGeneratorTest.class);

    private static WorkflowElementIdentifier one = WorkflowElementIdentifierFactory.fromExternalFormat("one");
    private static WorkflowElementIdentifier two = WorkflowElementIdentifierFactory.fromExternalFormat("two");
    private static WorkflowElementIdentifier three = WorkflowElementIdentifierFactory.fromExternalFormat("three");
    private static WorkflowElementIdentifier four = WorkflowElementIdentifierFactory.fromExternalFormat("four");

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
    public void testScriptVariablewithDefaultValue() {

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
}
