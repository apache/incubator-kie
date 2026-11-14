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
package org.kie.kogito.codegen.rules;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.drools.codegen.common.GeneratedFile;
import org.drools.drl.extensions.DecisionTableFactory;
import org.drools.drl.extensions.DecisionTableProvider;
import org.drools.model.codegen.project.MissingDecisionTableDependencyError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.internal.utils.KieService;
import org.kie.kogito.codegen.api.AddonsConfig;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.core.DashboardGeneratedFileUtils;
import org.kie.kogito.codegen.core.io.CollectedResourceProducer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.stmt.ReturnStmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kie.kogito.codegen.api.utils.KogitoContextTestUtils.withLegacyApi;
import static org.kie.kogito.grafana.utils.GrafanaDashboardUtils.DISABLED_DOMAIN_DASHBOARDS;
import static org.kie.kogito.grafana.utils.GrafanaDashboardUtils.DISABLED_OPERATIONAL_DASHBOARDS;

public class RuleCodegenTest {

    private static final String RESOURCE_PATH = "src/test/resources";

    @BeforeEach
    public void setup() {
        DecisionTableFactory.setDecisionTableProvider(KieService.load(DecisionTableProvider.class));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void isEmpty(KogitoBuildContext.Builder contextBuilder) {
        withLegacyApi(contextBuilder);

        KogitoBuildContext context = contextBuilder.build();
        RuleCodegen emptyCodeGenerator = RuleCodegen.ofCollectedResources(context, Collections.emptyList());

        assertThat(emptyCodeGenerator.isEmpty()).isTrue();
        assertThat(emptyCodeGenerator.isEnabled()).isFalse();

        Collection<GeneratedFile> emptyGeneratedFiles = emptyCodeGenerator.generate();
        assertThat(emptyGeneratedFiles).isEmpty();

        RuleCodegen codeGenerator = getIncrementalRuleCodegenFromFiles(
                contextBuilder,
                new File(RESOURCE_PATH + "/org/kie/kogito/codegen/rules/pkg1/file1.drl"));

        assertThat(codeGenerator.isEmpty()).isFalse();
        assertThat(codeGenerator.isEnabled()).isTrue();

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).hasSizeGreaterThan(0);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void generateSingleFile(KogitoBuildContext.Builder contextBuilder) {
        withLegacyApi(contextBuilder);

        RuleCodegen incrementalRuleCodegen = getIncrementalRuleCodegenFromFiles(
                contextBuilder,
                new File(RESOURCE_PATH + "/org/kie/kogito/codegen/rules/pkg1/file1.drl"));

        Collection<GeneratedFile> generatedFiles = incrementalRuleCodegen.withHotReloadMode().generate();
        assertHasLegacyApiFiles(generatedFiles);
        assertRules(5, 1, generatedFiles.size());
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void generateSinglePackage(KogitoBuildContext.Builder contextBuilder) {
        withLegacyApi(contextBuilder);

        RuleCodegen incrementalRuleCodegen = getIncrementalRuleCodegenFromFiles(
                contextBuilder,
                new File(RESOURCE_PATH + "/org/kie/kogito/codegen/rules/pkg1").listFiles());

        Collection<GeneratedFile> generatedFiles = incrementalRuleCodegen.withHotReloadMode().generate();
        assertHasLegacyApiFiles(generatedFiles);
        assertRules(7, 1, generatedFiles.size());
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void generateSinglePackageSingleUnit(KogitoBuildContext.Builder contextBuilder) {
        RuleCodegen incrementalRuleCodegen = getIncrementalRuleCodegenFromFiles(
                contextBuilder,
                new File(RESOURCE_PATH + "/org/kie/kogito/codegen/rules/multiunit").listFiles());

        Collection<GeneratedFile> generatedFiles = incrementalRuleCodegen.withHotReloadMode().generate();
        assertThat(generatedFiles.stream()).anyMatch(f -> f.relativePath().equals("org/kie/kogito/codegen/rules/multiunit/MultiUnitRuleUnit.java"));
        assertThat(generatedFiles.stream()).anyMatch(f -> f.relativePath().equals("org/kie/kogito/codegen/rules/multiunit/MultiUnitRuleUnitInstance.java"));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void generateDirectoryRecursively(KogitoBuildContext.Builder contextBuilder) {
        RuleCodegen incrementalRuleCodegen = getIncrementalRuleCodegenFromPaths(
                contextBuilder,
                Paths.get(RESOURCE_PATH + "/org/kie/kogito/codegen/rules"));

        Collection<GeneratedFile> generatedFiles = incrementalRuleCodegen.withHotReloadMode().generate();
        assertThat(generatedFiles.stream()).anyMatch(f -> f.relativePath().equals("org/kie/kogito/codegen/rules/multiunit/MultiUnitRuleUnit.java"));
        assertThat(generatedFiles.stream()).anyMatch(f -> f.relativePath().equals("org/kie/kogito/codegen/rules/multiunit/MultiUnitRuleUnitInstance.java"));
        assertThat(generatedFiles.stream()).anyMatch(f -> f.relativePath().equals("org/kie/kogito/codegen/rules/myunit/MyUnitRuleUnit.java"));
        assertThat(generatedFiles.stream()).anyMatch(f -> f.relativePath().equals("org/kie/kogito/codegen/rules/myunit/MyUnitRuleUnitInstance.java"));
        assertThat(generatedFiles.stream()).anyMatch(f -> f.relativePath().equals("org/kie/kogito/codegen/rules/singleton/SingletonRuleUnit.java"));
        assertThat(generatedFiles.stream()).anyMatch(f -> f.relativePath().equals("org/kie/kogito/codegen/rules/singleton/SingletonRuleUnitInstance.java"));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void generateSingleDtable(KogitoBuildContext.Builder contextBuilder) {
        withLegacyApi(contextBuilder);

        RuleCodegen incrementalRuleCodegen = getIncrementalRuleCodegenFromFiles(
                contextBuilder,
                new File(RESOURCE_PATH + "/org/drools/simple/candrink/CanDrink.drl.xls"));

        Collection<GeneratedFile> generatedFiles = incrementalRuleCodegen.withHotReloadMode().generate();
        assertHasLegacyApiFiles(generatedFiles);
        int externalizedLambda = 5;
        int legacyApiFiles = 2;
        assertRules(2, 1, generatedFiles.size() - externalizedLambda - legacyApiFiles);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void generateSingleUnit(KogitoBuildContext.Builder contextBuilder) {
        RuleCodegen incrementalRuleCodegen = getIncrementalRuleCodegenFromFiles(
                contextBuilder,
                new File(RESOURCE_PATH + "/org/kie/kogito/codegen/rules/myunit").listFiles());

        Collection<GeneratedFile> generatedFiles = incrementalRuleCodegen.withHotReloadMode().generate();
        assertThat(generatedFiles.stream()).anyMatch(f -> f.relativePath().equals("org/kie/kogito/codegen/rules/myunit/MyUnitRuleUnit.java"));
        assertThat(generatedFiles.stream()).anyMatch(f -> f.relativePath().equals("org/kie/kogito/codegen/rules/myunit/MyUnitRuleUnitInstance.java"));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void generateCepRule(KogitoBuildContext.Builder contextBuilder) {
        withLegacyApi(contextBuilder);

        RuleCodegen incrementalRuleCodegen = getIncrementalRuleCodegenFromFiles(
                contextBuilder,
                new File(RESOURCE_PATH + "/org/drools/simple/cep/cep.drl"));

        Collection<GeneratedFile> generatedFiles = incrementalRuleCodegen.withHotReloadMode().generate();
        assertHasLegacyApiFiles(generatedFiles);
        int externalizedLambda = 2;
        int legacyApiFiles = 2;
        assertRules(2, 1, generatedFiles.size() - externalizedLambda - legacyApiFiles);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void raiseErrorOnSyntaxError(KogitoBuildContext.Builder contextBuilder) {
        RuleCodegen incrementalRuleCodegen = getIncrementalRuleCodegenFromFiles(
                contextBuilder,
                new File(RESOURCE_PATH + "/org/drools/simple/broken.drl"));
        assertThatThrownBy(incrementalRuleCodegen.withHotReloadMode()::generate).isInstanceOf(RuleCodegenError.class);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void raiseErrorOnBadOOPath(KogitoBuildContext.Builder contextBuilder) {
        RuleCodegen incrementalRuleCodegen = getIncrementalRuleCodegenFromFiles(
                contextBuilder,
                new File(RESOURCE_PATH + "/org/kie/kogito/codegen/brokenrules/brokenunit/ABrokenUnit.drl"));

        assertThatThrownBy(incrementalRuleCodegen.withHotReloadMode()::generate).isInstanceOf(RuleCodegenError.class);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void throwWhenDtableDependencyMissing(KogitoBuildContext.Builder contextBuilder) {
        DecisionTableFactory.setDecisionTableProvider(null);
        RuleCodegen incrementalRuleCodegen = getIncrementalRuleCodegenFromFiles(
                contextBuilder,
                new File(RESOURCE_PATH + "/org/drools/simple/candrink/CanDrink.drl.xls"));
        assertThatThrownBy(incrementalRuleCodegen.withHotReloadMode()::generate).isInstanceOf(MissingDecisionTableDependencyError.class);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void generateGrafanaDashboards(KogitoBuildContext.Builder contextBuilder) {
        contextBuilder.withAddonsConfig(AddonsConfig.builder()
                .withPrometheusMonitoring(true)
                .withMonitoring(true)
                .build());

        RuleCodegen incrementalRuleCodegen = getIncrementalRuleCodegenFromFiles(
                contextBuilder,
                new File(RESOURCE_PATH + "/org/kie/kogito/codegen/unit/RuleUnitQuery.drl"));

        int expectedDashboards = contextBuilder.build().hasRESTForGenerator(incrementalRuleCodegen) ? 3 : 1; // The domain dashboard to monitor the RuleUnit is always generated.

        Collection<GeneratedFile> generatedFiles = incrementalRuleCodegen.withHotReloadMode().generate();
        assertThat(generatedFiles.stream().filter(x -> x.type().equals(DashboardGeneratedFileUtils.DASHBOARD_TYPE))).hasSize(expectedDashboards);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void generateGrafanaDashboardsExcluded(KogitoBuildContext.Builder contextBuilder) {
        contextBuilder.withAddonsConfig(AddonsConfig.builder()
                .withPrometheusMonitoring(true)
                .withMonitoring(true)
                .build());

        KogitoBuildContext context = contextBuilder.build();
        context.setApplicationProperty(DISABLED_OPERATIONAL_DASHBOARDS, "find-adults,find-adults-age");
        context.setApplicationProperty(DISABLED_DOMAIN_DASHBOARDS, "AdultUnit");

        RuleCodegen incrementalRuleCodegen = getIncrementalRuleCodegenFromFiles(
                context,
                new File(RESOURCE_PATH + "/org/kie/kogito/codegen/unit/RuleUnitQuery.drl"));

        Collection<GeneratedFile> generatedFiles = incrementalRuleCodegen.withHotReloadMode().generate();
        assertThat(generatedFiles.stream().filter(x -> x.type().equals(DashboardGeneratedFileUtils.DASHBOARD_TYPE))).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void elapsedTimeMonitoringIsWrappingEveryMethod(KogitoBuildContext.Builder contextBuilder) {
        contextBuilder.withAddonsConfig(AddonsConfig.builder()
                .withPrometheusMonitoring(true)
                .withMonitoring(true)
                .build())
                .build();

        RuleCodegen incrementalRuleCodegen = getIncrementalRuleCodegenFromFiles(
                contextBuilder,
                new File(RESOURCE_PATH + "/org/kie/kogito/codegen/unit/RuleUnitQuery.drl"));
        Collection<GeneratedFile> generatedFiles = incrementalRuleCodegen.withHotReloadMode().generate();

        List<String> endpointClasses = generatedFiles
                .stream()
                .filter(x -> x.relativePath().contains("Endpoint"))
                .map(x -> new String(x.contents()))
                .collect(Collectors.toList());

        for (String endpointClass : endpointClasses) {
            assertMonitoringEndpoints(endpointClass);
        }
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void noObjectMapperWhenNoRest(KogitoBuildContext.Builder contextBuilder) {
        RuleCodegen incrementalRuleCodegen = getIncrementalRuleCodegenFromFiles(
                contextBuilder.build(),
                new File(RESOURCE_PATH + "/org/kie/kogito/codegen/rules/myunit").listFiles());

        Collection<GeneratedFile> generatedFiles = incrementalRuleCodegen.withHotReloadMode().generate();
        assertThat(generatedFiles).hasSizeGreaterThan(0);
        if (contextBuilder.build().hasRest()) {
            assertThat(generatedFiles.stream()).anyMatch(f -> f.relativePath().endsWith("ObjectMapper.java"));
        } else {
            assertThat(generatedFiles.stream()).noneMatch(f -> f.relativePath().endsWith("ObjectMapper.java"));
        }
    }

    private void assertHasLegacyApiFiles(Collection<GeneratedFile> generatedFiles) {
        assertThat(generatedFiles.stream()).anyMatch(f -> f.relativePath().endsWith("/ProjectModel.java"));
        assertThat(generatedFiles.stream()).anyMatch(f -> f.relativePath().endsWith("/ProjectRuntime.java"));
    }

    private RuleCodegen getIncrementalRuleCodegenFromFiles(KogitoBuildContext.Builder contextBuilder, File... resources) {
        return getIncrementalRuleCodegenFromFiles(contextBuilder.build(), resources);
    }

    private RuleCodegen getIncrementalRuleCodegenFromFiles(KogitoBuildContext context, File... resources) {
        return RuleCodegen.ofCollectedResources(
                context,
                CollectedResourceProducer.fromFiles(Paths.get(RESOURCE_PATH), resources));
    }

    private RuleCodegen getIncrementalRuleCodegenFromPaths(KogitoBuildContext.Builder contextBuilder, Path... paths) {
        return RuleCodegen.ofCollectedResources(
                contextBuilder.build(),
                CollectedResourceProducer.fromPaths(paths));
    }

    private static void assertMonitoringEndpoints(String endpointClass) {
        CompilationUnit cu = StaticJavaParser.parse(endpointClass);

        ClassOrInterfaceDeclaration clazz = cu.findFirst(ClassOrInterfaceDeclaration.class).get();
        ReturnStmt executeQueryReturnStmt = clazz.getMethodsByName("executeQuery")
                .get(0)
                .getBody()
                .orElseThrow(() -> new RuntimeException("No body found for executeQuery method"))
                .findFirst(ReturnStmt.class)
                .orElseThrow(() -> new RuntimeException("No return statement for executeQuery method. Template has changed."));
        ReturnStmt executeQueryFirstReturnStmt = clazz.getMethodsByName("executeQueryFirst")
                .get(0)
                .getBody()
                .orElseThrow(() -> new RuntimeException("No body found for executeQueryFirst method"))
                .findFirst(ReturnStmt.class)
                .orElseThrow(() -> new RuntimeException("No return statement for executeQueryFirst method. Template has changed."));

        // SystemMetricsCollectorProvider field has been created
        assertThat(clazz.getFieldByName("systemMetricsCollectorProvider").isPresent()).isTrue();

        // Return expression should not be a call, otherwise the elapsed time would not be calculated properly
        assertThat(executeQueryReturnStmt.getExpression().get().isMethodCallExpr()).isFalse();
        assertThat(executeQueryFirstReturnStmt.getExpression().get().isMethodCallExpr()).isFalse();

        // The monitoring code is generated
        String statementsExecuteQuery = clazz.getMethodsByName("executeQuery").get(0).getBody().get().getStatements().toString();
        assertThat(statementsExecuteQuery.contains("startTime")).isTrue();
        assertThat(statementsExecuteQuery.contains("endTime")).isTrue();
        assertThat(statementsExecuteQuery.contains("registerElapsedTimeSampleMetrics")).isTrue();

        String statementsExecuteQueryFirst = clazz.getMethodsByName("executeQueryFirst").get(0).getBody().get().getStatements().toString();
        assertThat(statementsExecuteQueryFirst.contains("startTime")).isTrue();
        assertThat(statementsExecuteQueryFirst.contains("endTime")).isTrue();
        assertThat(statementsExecuteQueryFirst.contains("registerElapsedTimeSampleMetrics")).isTrue();
    }

    private static void assertRules(int expectedRules, int expectedPackages, int actualGeneratedFiles) {
        int expectedFiles = expectedRules +
                expectedPackages * 2; // package descriptor for rules + package metadata
        assertThat(actualGeneratedFiles).isEqualTo(expectedFiles);
    }
}
