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

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.assertj.core.api.AbstractStringAssert;
import org.drools.codegen.common.GeneratedFile;
import org.drools.codegen.common.GeneratedFileType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.dmn.core.compiler.DMNProfile;
import org.kie.dmn.core.compiler.RuntimeTypeCheckOption;
import org.kie.kogito.codegen.api.AddonsConfig;
import org.kie.kogito.codegen.api.ApplicationSection;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.core.DashboardGeneratedFileUtils;
import org.kie.kogito.codegen.core.io.CollectedResourceProducer;
import org.kie.kogito.grafana.JGrafana;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kie.dmn.core.assembler.DMNAssemblerService.DMN_PROFILE_PREFIX;
import static org.kie.kogito.codegen.api.utils.KogitoContextTestUtils.mockClassAvailabilityResolver;
import static org.kie.kogito.grafana.utils.GrafanaDashboardUtils.DISABLED_DOMAIN_DASHBOARDS;
import static org.kie.kogito.grafana.utils.GrafanaDashboardUtils.DISABLED_OPERATIONAL_DASHBOARDS;

public class DecisionCodegenTest {

    static final String CUSTOM_PROFILES_PACKAGE = "org.kie.kogito.codegen.decision.test.customprofiles";

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void isEmpty(KogitoBuildContext.Builder contextBuilder) {
        KogitoBuildContext context = contextBuilder.build();
        DecisionCodegen emptyCodeGenerator = DecisionCodegen.ofCollectedResources(context, Collections.emptyList());

        assertThat(emptyCodeGenerator.isEmpty()).isTrue();
        assertThat(emptyCodeGenerator.isEnabled()).isFalse();

        Collection<GeneratedFile> emptyGeneratedFiles = emptyCodeGenerator.generate();
        assertThat(emptyGeneratedFiles).isEmpty();

        DecisionCodegen codeGenerator = getDecisionCodegen("src/test/resources/decision/models/vacationDays", contextBuilder);

        assertThat(codeGenerator.isEmpty()).isFalse();
        assertThat(codeGenerator.isEnabled()).isTrue();

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).hasSizeGreaterThan(0);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void generateAllFiles(KogitoBuildContext.Builder contextBuilder) {
        DecisionCodegen codeGenerator = getDecisionCodegen("src/test/resources/decision/models/vacationDays", contextBuilder);

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).hasSizeGreaterThanOrEqualTo(6);

        Collection<String> expectedResources = new ArrayList<>(Arrays.asList("decision/InputSet.java",
                "decision/OutputSet.java",
                "decision/TEmployee.java",
                "decision/TAddress.java",
                "decision/TPayroll.java",
                "org/kie/kogito/app/DecisionModelResourcesProvider.java"));

        if (contextBuilder.build().hasRESTForGenerator(codeGenerator)) {
            expectedResources.add("decision/VacationsResource.java");

            assertRestResource(codeGenerator);
        }

        assertThat(fileNames(generatedFiles)).containsAll(expectedResources);

        assertNotEmptySectionCompilationUnit(codeGenerator);

        // the DMN namespace is "decision":	
        Collection<String> expectedStronglyTypeClassesForReflection = Arrays.asList("decision.InputSet", "decision.TEmployee", "decision.OutputSet", "decision.TAddress", "decision.TPayroll");
        assertThat(((DecisionContainerGenerator) codeGenerator.section().get()).getClassesForManualReflection()).containsAll(expectedStronglyTypeClassesForReflection);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void doNotGenerateTypesafeInfo(KogitoBuildContext.Builder contextBuilder) {
        DecisionCodegen codeGenerator = getDecisionCodegen("src/test/resources/decision/alltypes/", contextBuilder);

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).hasSizeGreaterThanOrEqualTo(3);

        Collection<String> expectedResources = new ArrayList<>(Arrays.asList("http_58_47_47www_46trisotech_46com_47definitions_47__4f5608e9_454d74_454c22_45a47e_45ab657257fc9c/InputSet.java",
                "http_58_47_47www_46trisotech_46com_47definitions_47__4f5608e9_454d74_454c22_45a47e_45ab657257fc9c/OutputSet.java",
                "org/kie/kogito/app/DecisionModelResourcesProvider.java"));
        if (contextBuilder.build().hasRESTForGenerator(codeGenerator)) {
            expectedResources.add("http_58_47_47www_46trisotech_46com_47definitions_47__4f5608e9_454d74_454c22_45a47e_45ab657257fc9c/OneOfEachTypeResource.java");

            assertRestResource(codeGenerator);
        }

        assertThat(fileNames(generatedFiles)).containsAll(expectedResources);

        assertNotEmptySectionCompilationUnit(codeGenerator);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void givenADMNModelWhenMonitoringIsActiveThenGrafanaDashboardsAreGenerated(KogitoBuildContext.Builder contextBuilder) throws Exception {
        DecisionCodegen decisionCodeGenerator = getDecisionCodegen("src/test/resources/decision/models/vacationDays",
                AddonsConfig.builder().withMonitoring(true).withPrometheusMonitoring(true).build(),
                contextBuilder);

        int expectedDashboards = contextBuilder.build().hasRESTForGenerator(decisionCodeGenerator) ? 2 : 0;
        List<GeneratedFile> dashboards = generateTestDashboards(decisionCodeGenerator, expectedDashboards);

        if (contextBuilder.build().hasRESTForGenerator(decisionCodeGenerator)) {
            JGrafana vacationOperationalDashboard =
                    JGrafana.parse(new String(dashboards.stream().filter(x -> x.relativePath().contains("operational-dashboard-Vacations.json")).findFirst().get().contents()));

            assertThat(vacationOperationalDashboard.getDashboard().panels.size()).isEqualTo(6);
            assertThat(vacationOperationalDashboard.getDashboard().links.size()).isEqualTo(0);

            JGrafana vacationDomainDashboard =
                    JGrafana.parse(new String(dashboards.stream().filter(x -> x.relativePath().contains("domain-dashboard-Vacations.json")).findFirst().get().contents()));

            assertThat(vacationDomainDashboard.getDashboard().panels.size()).isEqualTo(1);
            assertThat(vacationDomainDashboard.getDashboard().links.size()).isEqualTo(0);

            assertRestResource(decisionCodeGenerator);
        }
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void givenADMNModelWhenMonitoringIsActiveButDashboardsDeactivatedThenGrafanaDashboardsAreNotGenerated(KogitoBuildContext.Builder contextBuilder) throws Exception {
        DecisionCodegen decisionCodeGenerator = getDecisionCodegen("src/test/resources/decision/models/vacationDays",
                AddonsConfig.builder().withMonitoring(true).withPrometheusMonitoring(true).build(),
                contextBuilder);
        KogitoBuildContext build = contextBuilder.build();
        build.setApplicationProperty(DISABLED_OPERATIONAL_DASHBOARDS, "Vacations");
        build.setApplicationProperty(DISABLED_DOMAIN_DASHBOARDS, "Vacations");
        generateTestDashboards(decisionCodeGenerator, 0);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void givenADMNModelWhenMonitoringAndTracingAreActiveThenTheGrafanaDashboardsContainsTheAuditUILink(KogitoBuildContext.Builder contextBuilder) throws Exception {
        DecisionCodegen decisionCodeGenerator = getDecisionCodegen("src/test/resources/decision/models/vacationDays",
                AddonsConfig.builder().withMonitoring(true).withPrometheusMonitoring(true).withTracing(true).build(),
                contextBuilder);

        int expectedDashboards = contextBuilder.build().hasRESTForGenerator(decisionCodeGenerator) ? 2 : 0;
        List<GeneratedFile> dashboards = generateTestDashboards(decisionCodeGenerator, expectedDashboards);

        if (contextBuilder.build().hasRESTForGenerator(decisionCodeGenerator)) {
            JGrafana vacationOperationalDashboard =
                    JGrafana.parse(new String(dashboards.stream().filter(x -> x.relativePath().contains("operational-dashboard-Vacations.json")).findFirst().get().contents()));

            assertThat(vacationOperationalDashboard.getDashboard().links.size()).isEqualTo(1);

            JGrafana vacationDomainDashboard =
                    JGrafana.parse(new String(dashboards.stream().filter(x -> x.relativePath().contains("domain-dashboard-Vacations.json")).findFirst().get().contents()));

            assertThat(vacationDomainDashboard.getDashboard().links.size()).isEqualTo(1);

            assertRestResource(decisionCodeGenerator);
        }
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void resilientToDuplicateDMNIDs(KogitoBuildContext.Builder contextBuilder) {
        DecisionCodegen codeGenerator = getDecisionCodegen("src/test/resources/decision-test20200507", contextBuilder);

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).hasSizeGreaterThanOrEqualTo(3);

        assertNotEmptySectionCompilationUnit(codeGenerator);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void emptyName(KogitoBuildContext.Builder contextBuilder) {
        DecisionCodegen codeGenerator = getDecisionCodegen("src/test/resources/decision-empty-name", contextBuilder);
        String expected = "DMN: Invalid name '': Name cannot be null or empty (DMN id: _9efe7fc6-f41b-422c-accd-95dcaaa67a39, The listed name is not a valid FEEL identifier)";
        assertThatThrownBy(codeGenerator::generate)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(expected);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void testNSEW_positive(KogitoBuildContext.Builder contextBuilder) {
        contextBuilder
                .withClassAvailabilityResolver(mockClassAvailabilityResolver(singleton("org.eclipse.microprofile.openapi.models.OpenAPI"), emptyList()));
        // This test is meant to check that IFF Eclipse MP OpenAPI annotations are available on Build/CP of Kogito application, annotation is used with codegen
        DecisionCodegen codeGenerator = getDecisionCodegen("src/test/resources/decision-NSEW", contextBuilder);

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).anyMatch(x -> x.relativePath().endsWith("InputSet.java"));
        GeneratedFile inputSetFile = generatedFiles.stream().filter(x -> x.relativePath().endsWith("InputSet.java")).findFirst().get();
        assertThat(new String(inputSetFile.contents())).containsPattern("@org\\.eclipse\\.microprofile\\.openapi\\.annotations\\.media\\.Schema\\(.*enumeration");
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void testNSEW_negative(KogitoBuildContext.Builder contextBuilder) {
        contextBuilder
                .withClassAvailabilityResolver(mockClassAvailabilityResolver(emptyList(), singleton("org.eclipse.microprofile.openapi.models.OpenAPI")));
        DecisionCodegen codeGenerator = getDecisionCodegen("src/test/resources/decision-NSEW", contextBuilder);

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).anyMatch(x -> x.relativePath().endsWith("InputSet.java"));
        GeneratedFile inputSetFile = generatedFiles.stream().filter(x -> x.relativePath().endsWith("InputSet.java")).findFirst().get();
        assertThat(new String(inputSetFile.contents())).doesNotContain("@org.eclipse.microprofile.openapi.annotations.media.Schema");
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void pmmlIntegrationTest(KogitoBuildContext.Builder contextBuilder) {
        // with PMML in the classpath
        contextBuilder
                .withClassAvailabilityResolver(mockClassAvailabilityResolver(singleton(DecisionContainerGenerator.PMML_ABSTRACT_CLASS), emptyList()));

        assertNotEmptySectionCompilationUnit("src/test/resources/decision/models/vacationDays", contextBuilder);

        // without PMML in the classpath
        contextBuilder
                .withClassAvailabilityResolver(mockClassAvailabilityResolver(emptyList(), singleton(DecisionContainerGenerator.PMML_ABSTRACT_CLASS)));

        assertNotEmptySectionCompilationUnit("src/test/resources/decision/models/vacationDays", contextBuilder)
                .doesNotContain(DecisionContainerGenerator.PMML_FUNCTION);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void getCustomDMNProfilesProperties(KogitoBuildContext.Builder contextBuilder) {
        Properties properties = new Properties();
        Set<String> dmnProfiles = new HashSet<>();
        IntStream.range(0, 3).forEach(index -> {
            String dmnProfileKey = String.format("%sProfile_%d", DMN_PROFILE_PREFIX, index);
            String dmnProfileValue = String.format("%s.Profile_%d", CUSTOM_PROFILES_PACKAGE, index);
            properties.put(dmnProfileKey, dmnProfileValue);
            dmnProfiles.add(dmnProfileValue);
        });
        DecisionCodegen codeGenerator = getDecisionCodegen("src/test/resources/decision/models/vacationDays", contextBuilder, properties);
        Set<String> retrieved = codeGenerator.getCustomDMNProfilesProperties();
        assertThat(retrieved).containsExactlyInAnyOrderElementsOf(dmnProfiles);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void getEnableRuntimeTypeCheckOption(KogitoBuildContext.Builder contextBuilder) {
        System.getProperties().remove(RuntimeTypeCheckOption.PROPERTY_NAME);
        DecisionCodegen codeGenerator = getDecisionCodegen("src/test/resources/decision/models/vacationDays", contextBuilder, new Properties());
        boolean retrieved = codeGenerator.getEnableRuntimeTypeCheckOption();
        assertThat(retrieved).isFalse();
        System.setProperty(RuntimeTypeCheckOption.PROPERTY_NAME, "false");
        retrieved = codeGenerator.getEnableRuntimeTypeCheckOption();
        assertThat(retrieved).isFalse();
        System.setProperty(RuntimeTypeCheckOption.PROPERTY_NAME, "true");
        retrieved = codeGenerator.getEnableRuntimeTypeCheckOption();
        assertThat(retrieved).isTrue();
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void getEnableRuntimeTypeCheckOptionFromProperties(KogitoBuildContext.Builder contextBuilder) {
        System.getProperties().remove(RuntimeTypeCheckOption.PROPERTY_NAME);
        Properties properties = new Properties();
        DecisionCodegen codeGenerator = getDecisionCodegen("src/test/resources/decision/models/vacationDays", contextBuilder, properties);
        boolean retrieved = codeGenerator.getEnableRuntimeTypeCheckOptionFromProperties();
        assertThat(retrieved).isFalse();
        properties.put(RuntimeTypeCheckOption.PROPERTY_NAME, "false");
        codeGenerator = getDecisionCodegen("src/test/resources/decision/models/vacationDays", contextBuilder, properties);
        retrieved = codeGenerator.getEnableRuntimeTypeCheckOptionFromProperties();
        assertThat(retrieved).isFalse();
        properties.put(RuntimeTypeCheckOption.PROPERTY_NAME, "true");
        codeGenerator = getDecisionCodegen("src/test/resources/decision/models/vacationDays", contextBuilder, properties);
        retrieved = codeGenerator.getEnableRuntimeTypeCheckOptionFromProperties();
        assertThat(retrieved).isTrue();
    }

    @Test
    public void getCustomDMNProfiles() {
        Set<String> customDMNProfiles = IntStream.range(0, 3)
                .mapToObj(index -> String.format("%s.Profile_%d", CUSTOM_PROFILES_PACKAGE, index))
                .collect(Collectors.toSet());
        Set<DMNProfile> retrieved = DecisionCodegen.getCustomDMNProfiles(customDMNProfiles, Thread.currentThread().getContextClassLoader());
        assertThat(retrieved).isNotNull().hasSize(3);
        Set<String> retrievedStrings = retrieved.stream().map(profile -> profile.getClass().getCanonicalName()).collect(Collectors.toSet());
        assertThat(retrievedStrings).containsExactlyInAnyOrderElementsOf(customDMNProfiles);
    }

    private KogitoBuildContext.Builder stronglyTypedContext(KogitoBuildContext.Builder builder) {
        Properties properties = new Properties();
        properties.put(DecisionCodegen.STRONGLY_TYPED_CONFIGURATION_KEY, Boolean.TRUE.toString());
        builder.withApplicationProperties(properties);
        return builder;
    }

    protected AbstractStringAssert<?> assertNotEmptySectionCompilationUnit(String sourcePath, KogitoBuildContext.Builder contextBuilder) {
        DecisionCodegen codeGenerator = getDecisionCodegen(sourcePath, contextBuilder);
        return assertNotEmptySectionCompilationUnit(codeGenerator);
    }

    protected AbstractStringAssert<?> assertNotEmptySectionCompilationUnit(DecisionCodegen codeGenerator) {
        Optional<ApplicationSection> optionalApplicationSection = codeGenerator.section();
        assertThat(optionalApplicationSection).isNotEmpty();
        CompilationUnit compilationUnit = optionalApplicationSection.get().compilationUnit();
        assertThat(compilationUnit).isNotNull();
        return assertThat(compilationUnit.toString());
    }

    protected void assertRestResource(DecisionCodegen codeGenerator) {
        codeGenerator.generate().stream()
                .filter(x -> x.type().equals(GeneratedFileType.of("REST", GeneratedFileType.Category.SOURCE, true, true)))
                .forEach(x -> assertRestResource(StaticJavaParser.parse(new String(x.contents()))));
    }

    protected void assertRestResource(CompilationUnit compilationUnit) {
        compilationUnit
                .findAll(MethodDeclaration.class, x -> x.getNameAsString().contains("_dmnresult"))
                .stream()
                .map(x -> x.findFirst(ReturnStmt.class).orElseThrow(() -> new NoSuchElementException("Could not find return statement")))
                .map(x -> x.findFirst(MethodCallExpr.class).orElseThrow(() -> new NoSuchElementException("Could not find method call")))
                .forEach(x -> assertThat(x.getNameAsString()).isEqualTo("buildDMNResultResponse"));
    }

    protected DecisionCodegen getDecisionCodegen(String sourcePath, KogitoBuildContext.Builder contextBuilder) {
        return getDecisionCodegen(sourcePath, AddonsConfig.DEFAULT, contextBuilder);
    }

    protected DecisionCodegen getDecisionCodegen(String sourcePath, KogitoBuildContext.Builder contextBuilder, Properties properties) {
        KogitoBuildContext context = contextBuilder
                .withApplicationProperties(properties)
                .build();
        return DecisionCodegen.ofCollectedResources(context,
                CollectedResourceProducer.fromPaths(Paths.get(sourcePath).toAbsolutePath()));
    }

    protected DecisionCodegen getDecisionCodegen(String sourcePath, AddonsConfig addonsConfig, KogitoBuildContext.Builder contextBuilder) {
        KogitoBuildContext context = stronglyTypedContext(contextBuilder)
                .withAddonsConfig(addonsConfig)
                .build();

        return DecisionCodegen.ofCollectedResources(context,
                CollectedResourceProducer.fromPaths(Paths.get(sourcePath).toAbsolutePath()));
    }

    private Collection<String> fileNames(Collection<GeneratedFile> generatedFiles) {
        return generatedFiles.stream().map(GeneratedFile::relativePath).collect(Collectors.toList());
    }

    private List<GeneratedFile> generateTestDashboards(DecisionCodegen codeGenerator, int expectedDashboards) {

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();

        List<GeneratedFile> dashboards = generatedFiles.stream()
                .filter(x -> x.type().equals(DashboardGeneratedFileUtils.DASHBOARD_TYPE))
                .collect(Collectors.toList());

        assertThat(dashboards.size()).isEqualTo(expectedDashboards);

        return dashboards;
    }
}
