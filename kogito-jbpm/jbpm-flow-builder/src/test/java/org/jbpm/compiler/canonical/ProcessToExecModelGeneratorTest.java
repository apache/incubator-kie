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

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;

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
    public void testLargeProcessDoesNotOverflowMethodSize() {
        int nodeCount = 200;

        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("com.example.largeProcess");
        factory.name("largeProcess")
                .packageName("com.example")
                .dynamic(false)
                .version("1.0");

        WorkflowElementIdentifier startId = WorkflowElementIdentifierFactory.fromExternalFormat("start");
        factory.startNode(startId).name("start").done();

        WorkflowElementIdentifier prevId = startId;
        for (int i = 0; i < nodeCount; i++) {
            WorkflowElementIdentifier actionId = WorkflowElementIdentifierFactory.fromExternalFormat("action_" + i);
            factory.actionNode(actionId)
                    .name("Action " + i)
                    .action("java", "System.out.println(\"step " + i + "\");")
                    .done();
            factory.connection(prevId, actionId);
            prevId = actionId;
        }

        WorkflowElementIdentifier endId = WorkflowElementIdentifierFactory.fromExternalFormat("end");
        factory.endNode(endId).name("end").terminate(false).done();
        factory.connection(prevId, endId);

        WorkflowProcess process = factory.validate().getProcess();

        ProcessMetaData processMetadata = ProcessToExecModelGenerator.INSTANCE.generate(process);
        assertThat(processMetadata).isNotNull();

        // 200 node builder classes + 1 connections class
        List<CompilationUnit> nodeBuilderClasses = processMetadata.getNodeBuilderClasses();
        assertThat(nodeBuilderClasses).as("Expected one builder class per node (200 action + 1 start + 1 end) plus 1 connections class")
                .hasSize(nodeCount + 2 + 1); // start + 200 actions + end = 202 nodes + 1 connections = 203

        // The main process() method should dispatch via new XxxNode().build(factory) calls
        MethodDeclaration processMethod = processMetadata.getGeneratedClassModel()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("process"))
                .orElseThrow(() -> new AssertionError("process() method not found"));

        long dispatchCallCount = processMethod.getBody().orElseThrow()
                .getStatements().stream()
                .filter(stmt -> stmt instanceof ExpressionStmt)
                .map(stmt -> ((ExpressionStmt) stmt).getExpression())
                .filter(expr -> expr instanceof com.github.javaparser.ast.expr.MethodCallExpr)
                .map(expr -> (com.github.javaparser.ast.expr.MethodCallExpr) expr)
                .filter(call -> call.getScope().isPresent() && call.getScope().get() instanceof ObjectCreationExpr)
                .count();

        // 200 action nodes + 1 start + 1 end + 1 connections class = 203 dispatch calls
        assertThat(dispatchCallCount)
                .as("process() body should contain one new XxxNode().build(factory) call per node class plus one for connections")
                .isEqualTo(nodeCount + 2 + 1);
    }
}
