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
package org.kie.kogito.codegen.decision;

import java.io.ByteArrayInputStream;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.drools.codegen.common.GeneratedFile;
import org.junit.jupiter.api.Test;
import org.kie.api.io.ResourceType;
import org.kie.kogito.codegen.api.AddonsConfig;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.QuarkusKogitoBuildContext;
import org.kie.kogito.codegen.api.io.CollectedResource;
import org.kie.kogito.codegen.core.io.CollectedResourceProducer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

import static com.github.javaparser.StaticJavaParser.parse;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.codegen.common.GeneratedFileType.REST;
import static org.drools.codegen.common.GeneratedFileType.SOURCE;

public class DecisionModelResourcesProviderGeneratorTest {

    @Test
    public void generateDecisionModelResourcesProvider() {

        final KogitoBuildContext context = QuarkusKogitoBuildContext.builder()
                .withAddonsConfig(AddonsConfig.builder().withTracing(true).build())
                .build();

        final Collection<CollectedResource> collectedResources = CollectedResourceProducer.fromPaths(
                Paths.get("src/test/resources/decision/models/vacationDays").toAbsolutePath(),
                Paths.get("src/test/resources/decision/models/vacationDaysAlt").toAbsolutePath());

        final long numberOfModels = collectedResources.stream()
                .filter(r -> r.resource().getResourceType() == ResourceType.DMN)
                .count();

        final DecisionCodegen codeGenerator = DecisionCodegen
                .ofCollectedResources(context, collectedResources);

        final Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).hasSizeGreaterThanOrEqualTo(2); // the two resources below, see https://github.com/kiegroup/kogito-runtimes/commit/18ec525f530b1ff1bddcf18c0083f14f86aff171#diff-edd3a09d62dc627ee10fe37925944217R53

        // Align this FAI-215 test (#621) with unknown order of generated files (ie.: additional generated files might be present)
        //A Rest endpoint is always generated per model.
        List<GeneratedFile> generatedRESTFiles = generatedFiles.stream()
                .filter(gf -> gf.type().equals(REST))
                .collect(toList());
        assertThat(generatedRESTFiles.isEmpty()).isFalse();
        assertThat(generatedRESTFiles.size()).isEqualTo(numberOfModels);

        List<GeneratedFile> generatedCLASSFile = generatedFiles.stream()
                .filter(gf -> gf.type().equals(SOURCE))
                .collect(toList());
        assertThat(generatedCLASSFile.size()).isEqualTo(1);
        GeneratedFile classFile = generatedCLASSFile.get(0);
        assertThat(classFile.relativePath()).isEqualTo("org/kie/kogito/app/DecisionModelResourcesProvider.java");

        final CompilationUnit compilationUnit = parse(new ByteArrayInputStream(classFile.contents()));

        final ClassOrInterfaceDeclaration classDeclaration = compilationUnit
                .findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new NoSuchElementException("Compilation unit doesn't contain a class or interface declaration!"));

        assertThat(classDeclaration).isNotNull();

        final MethodDeclaration methodDeclaration = classDeclaration
                .findFirst(MethodDeclaration.class,
                        d -> d.getName().getIdentifier().equals("getResources"))
                .orElseThrow(() -> new NoSuchElementException("Class declaration doesn't contain a method named \"getResources\"!"));
        assertThat(methodDeclaration).isNotNull();
        assertThat(methodDeclaration.getBody().isPresent()).isTrue();

        final BlockStmt body = methodDeclaration.getBody().get();
        assertThat(body.getStatements().size() > 2).isTrue();
        assertThat(body.getStatements().get(1).isExpressionStmt()).isTrue();

        final ExpressionStmt expression = (ExpressionStmt) body.getStatements().get(1);
        assertThat((expression.getExpression() instanceof MethodCallExpr)).isTrue();

        final MethodCallExpr call = (MethodCallExpr) expression.getExpression();
        assertThat(call.getName().getIdentifier()).isEqualTo("add");
        assertThat(call.getScope().isPresent()).isTrue();
        assertThat(call.getScope().get().isNameExpr()).isTrue();

        final NameExpr nameExpr = call.getScope().get().asNameExpr();
        assertThat(nameExpr.getName().getIdentifier()).isEqualTo("resourcePaths");

        long numberOfAddStms = body.getStatements().stream()
                .filter(this::isAddStatement)
                .count();
        assertThat(numberOfAddStms).isEqualTo(numberOfModels);

        List<NodeList<Expression>> defaultDecisionModelResources = body.getStatements().stream()
                .filter(this::isAddStatement)
                .map(stm -> stm.asExpressionStmt().getExpression().asMethodCallExpr().getArguments())
                .collect(toList());

        // verify .add(..) number of parameters
        defaultDecisionModelResources.forEach(nodeList -> assertThat(nodeList).hasSize(1));

        Set<String> distinctDefaultDecisionModelResources = defaultDecisionModelResources.stream()
                .map(nodeList -> nodeList.get(0).toString())
                .collect(toSet());

        assertThat(distinctDefaultDecisionModelResources.size()).isEqualTo(defaultDecisionModelResources.size());
    }

    private boolean isAddStatement(Statement stm) {
        return stm.isExpressionStmt() &&
                stm.asExpressionStmt().getExpression().isMethodCallExpr() &&
                "add".equals(stm.asExpressionStmt().getExpression().asMethodCallExpr().getName().getIdentifier());
    }
}
