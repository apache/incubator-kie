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

package org.kie.kogito.codegen.process.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import org.drools.codegen.common.AppPaths;
import org.drools.compiler.kie.builder.impl.KieBuilderSetImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.definition.process.Process;
import org.kie.kogito.codegen.api.SourceFileCodegenBindNotifier;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.QuarkusKogitoBuildContext;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcess;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;

import static com.github.javaparser.StaticJavaParser.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.codegen.process.ProcessCodegen.SOURCE_FILE_PROVIDER_PRODUCER;
import static org.kie.kogito.codegen.process.ProcessGenerationUtils.parseProcesses;
import static org.kie.kogito.codegen.process.util.SourceFilesProviderProducerUtil.addSourceFilesToProvider;
import static org.kie.kogito.codegen.process.util.SourceFilesProviderProducerUtil.getResourceRelativePath;

public class SourceFilesProviderProducerUtilTest {
    private static final String WORKFLOW_RELATIVE_PATH = "org/kie/kogito/process.bpmn";

    private AppPaths appPaths;
    private KogitoBuildContext context;

    @BeforeEach
    public void setup() {
        this.appPaths = AppPaths.fromTestDir(new File("").getAbsoluteFile().toPath());
        this.context = QuarkusKogitoBuildContext.builder()
                .withAppPaths(appPaths)
                .withSourceFileProcessBindNotifier(new SourceFileCodegenBindNotifier())
                .build();
    }

    @Test
    public void testGetResourceRelativePathWithResourceWithRelativePath() {
        String calculatedRelativePath = getResourceRelativePath(context, new KieBuilderSetImpl.DummyResource(WORKFLOW_RELATIVE_PATH));
        assertThat(calculatedRelativePath).isEqualTo(WORKFLOW_RELATIVE_PATH);
    }

    @Test
    public void testGetResourceRelativePath() {
        for (Path appResourcePath : appPaths.getResourcePaths()) {
            String fullWorkflowPath = appResourcePath.resolve(WORKFLOW_RELATIVE_PATH).toString();
            String calculatedRelativePath = getResourceRelativePath(context, new KieBuilderSetImpl.DummyResource(fullWorkflowPath));
            assertThat(Path.of(calculatedRelativePath)).isEqualTo(Path.of(WORKFLOW_RELATIVE_PATH));
        }
    }

    @Test
    public void testAddSourceFilesToProvider() {
        // Loading Process from test resources
        final Path testResourcesPath = Paths.get("src/test/resources/usertask").toAbsolutePath();
        File[] testProcessFiles = testResourcesPath.toFile().listFiles(pathname -> pathname.getName().endsWith(".bpmn2"));
        List<Process> testProcesses = parseProcesses(List.of(testProcessFiles));

        Map<String, KogitoWorkflowProcess> workflows = testProcesses.stream()
                .collect(Collectors.toMap(Process::getId, KogitoWorkflowProcess.class::cast));

        CompilationUnit compilationUnit = getCompilationUnit();

        addSourceFilesToProvider(compilationUnit, workflows, context);

        ClassOrInterfaceDeclaration producerClass = compilationUnit.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();

        InitializerDeclaration staticInit = producerClass.findFirst(InitializerDeclaration.class)
                .filter(InitializerDeclaration::isStatic)
                .stream()
                .findFirst()
                .orElseThrow();

        BlockStmt staticInitBody = staticInit.getBody();

        assertThat(staticInitBody.getStatements())
                .hasSize(4);

        List<MethodCallExpr> initStatements = staticInitBody.findAll(MethodCallExpr.class);

        assertThat(initStatements)
                .hasSize(4);

        initStatements.forEach(initStatement -> {
            assertThat(initStatement.getScope())
                    .isPresent()
                    .get()
                    .extracting(Node::toString)
                    .isEqualTo("INSTANCE");

            assertThat(initStatement.getNameAsString())
                    .isEqualTo("addSourceFile");

            assertThat(initStatement.getArguments())
                    .hasSize(2);

            String workflowId = initStatement.getArguments().get(0).asStringLiteralExpr().asString();

            KogitoWorkflowProcess kogitoWorkflow = workflows.get(workflowId);

            assertThat(kogitoWorkflow)
                    .isNotNull();

            ObjectCreationExpr sourceCreationExpr = initStatement.getArguments().get(1).asObjectCreationExpr();

            assertThat(sourceCreationExpr.getType().toString())
                    .isEqualTo("SourceFile");

            String expectedResourceRelativePath = getResourceRelativePath(context, kogitoWorkflow.getResource());

            assertThat(sourceCreationExpr.getArguments())
                    .hasSize(1)
                    .extracting(argumentExpr -> argumentExpr.asStringLiteralExpr().asString())
                    .contains(expectedResourceRelativePath);
        });
    }

    @Test
    public void testAddSourceFilesToProviderWithEmptyWorkflows() {
        CompilationUnit compilationUnit = getCompilationUnit();

        addSourceFilesToProvider(compilationUnit, Map.of(), context);

        ClassOrInterfaceDeclaration producerClass = compilationUnit.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();

        Optional<InitializerDeclaration> staticInit = producerClass.findFirst(InitializerDeclaration.class)
                .filter(InitializerDeclaration::isStatic)
                .stream().findFirst();

        assertThat(staticInit).isEmpty();
    }

    CompilationUnit getCompilationUnit() {
        return parse(this.getClass().getResourceAsStream("/class-templates/producer/" + SOURCE_FILE_PROVIDER_PRODUCER + "Template.java"));
    }
}
