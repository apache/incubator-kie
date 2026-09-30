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
package org.kie.kogito.codegen.process;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.drools.codegen.common.GeneratedFile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.kogito.codegen.api.AddonsConfig;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.JavaKogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.QuarkusKogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.SpringBootKogitoBuildContext;
import org.kie.kogito.codegen.core.DashboardGeneratedFileUtils;
import org.kie.kogito.codegen.core.io.CollectedResourceProducer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.codegen.process.ProcessCodegen.BUSINESS_CALENDAR_PRODUCER_TEMPLATE;
import static org.kie.kogito.grafana.utils.GrafanaDashboardUtils.DISABLED_OPERATIONAL_DASHBOARDS;

class ProcessCodegenTest {

    private static final Path BASE_PATH = Paths.get("src/test/resources/").toAbsolutePath();
    private static final String MESSAGE_USERTASK_SOURCE = "usertask/UserTasksProcess.bpmn2";
    private static final Path MESSAGE_USERTASK_SOURCE_FULL_SOURCE = BASE_PATH.resolve(MESSAGE_USERTASK_SOURCE);
    private static final String OBJECT_VARIABLE_SOURCE = "objectvariable/ObjectVariableProcess.bpmn2";
    private static final Path OBJECT_VARIABLE_SOURCE_FULL_PATH = BASE_PATH.resolve(OBJECT_VARIABLE_SOURCE);

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void isEmpty(KogitoBuildContext.Builder contextBuilder) {
        KogitoBuildContext context = contextBuilder.build();
        ProcessCodegen emptyCodeGenerator = ProcessCodegen.ofCollectedResources(context, Collections.emptyList());

        assertThat(emptyCodeGenerator.isEmpty()).isTrue();
        assertThat(emptyCodeGenerator.isEnabled()).isFalse();

        Collection<GeneratedFile> emptyGeneratedFiles = emptyCodeGenerator.generate();
        assertThat(emptyGeneratedFiles).isEmpty();

        ProcessCodegen codeGenerator = ProcessCodegen.ofCollectedResources(
                context,
                CollectedResourceProducer.fromFiles(BASE_PATH, MESSAGE_USERTASK_SOURCE_FULL_SOURCE.toFile()));

        assertThat(codeGenerator.isEmpty()).isFalse();
        assertThat(codeGenerator.isEnabled()).isTrue();

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).hasSizeGreaterThanOrEqualTo(10);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void whenMonitoringAndPrometheusEnabledGrafanaDashboardsAreGenerated(KogitoBuildContext.Builder contextBuilder) throws Exception {

        AddonsConfig addonsConfig = AddonsConfig.builder().withMonitoring(true).withPrometheusMonitoring(true).build();

        KogitoBuildContext context = contextBuilder
                .withAddonsConfig(addonsConfig)
                .build();
        ProcessCodegen codeGenerator = ProcessCodegen.ofCollectedResources(
                context,
                CollectedResourceProducer.fromFiles(BASE_PATH, MESSAGE_USERTASK_SOURCE_FULL_SOURCE.toFile()));

        generateTestDashboards(codeGenerator, 2);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void whenMonitoringAndPrometheusEnabledGrafanaDashboardsAreNotGenerated(KogitoBuildContext.Builder contextBuilder) throws Exception {

        AddonsConfig addonsConfig = AddonsConfig.builder().withMonitoring(true).withPrometheusMonitoring(true).build();

        KogitoBuildContext context = contextBuilder
                .withAddonsConfig(addonsConfig)
                .build();
        ProcessCodegen codeGenerator = ProcessCodegen.ofCollectedResources(
                context,
                CollectedResourceProducer.fromFiles(BASE_PATH, MESSAGE_USERTASK_SOURCE_FULL_SOURCE.toFile()));

        KogitoBuildContext build = contextBuilder.build();
        build.setApplicationProperty(DISABLED_OPERATIONAL_DASHBOARDS, "Global,UserTasksProcess");
        generateTestDashboards(codeGenerator, 0);
    }

    @ParameterizedTest
    @MethodSource("contextBuildersForBusinessCalendar")
    public void whenCalendarPropertiesFoundGenerateBusinessCalendar(KogitoBuildContext.Builder contextBuilder, String dependencyAnnotation) {
        KogitoBuildContext context = contextBuilder.build();
        StaticDependencyInjectionProducerGenerator staticDependencyInjectionProducerGenerator = StaticDependencyInjectionProducerGenerator.of(context);
        Map<String, String> businessCalendarProducerSourcesMap = staticDependencyInjectionProducerGenerator.generate(List.of(BUSINESS_CALENDAR_PRODUCER_TEMPLATE));
        String expectedKey = "org/kie/kogito/app/BusinessCalendarProducer.java";
        assertThat(businessCalendarProducerSourcesMap).hasSize(1).containsKey(expectedKey);
        String generatedContent = businessCalendarProducerSourcesMap.get(expectedKey);
        assertThat(generatedContent).isNotNull().isNotEmpty().contains(dependencyAnnotation);
    }

    @ParameterizedTest
    @MethodSource("contextBuildersNotDI")
    public void whenBuildingNotDIContext(KogitoBuildContext.Builder contextBuilder) {
        KogitoBuildContext context = contextBuilder.build();
        StaticDependencyInjectionProducerGenerator staticDependencyInjectionProducerGenerator = StaticDependencyInjectionProducerGenerator.of(context);
        Map<String, String> businessCalendarProducer = staticDependencyInjectionProducerGenerator.generate(List.of(BUSINESS_CALENDAR_PRODUCER_TEMPLATE));
        assertThat(businessCalendarProducer).isEmpty();
    }

    private List<GeneratedFile> generateTestDashboards(ProcessCodegen codeGenerator, int expectedDashboards) {
        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        List<GeneratedFile> dashboards = generatedFiles.stream()
                .filter(x -> x.type().equals(DashboardGeneratedFileUtils.DASHBOARD_TYPE))
                .collect(Collectors.toList());
        assertThat(dashboards).hasSize(expectedDashboards);
        return dashboards;
    }

    private static Stream<Arguments> contextBuildersForBusinessCalendar() {
        return Stream.of(
                Arguments.of(QuarkusKogitoBuildContext.builder(), "@Produces"),
                Arguments.of(SpringBootKogitoBuildContext.builder(), "@Bean"));
    }

    private static Stream<Arguments> contextBuildersNotDI() {
        return Stream.of(
                Arguments.of(JavaKogitoBuildContext.builder()));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void objectVariableProcessGeneratesWithoutError(KogitoBuildContext.Builder contextBuilder) {
        KogitoBuildContext context = contextBuilder.build();
        ProcessCodegen codeGenerator = ProcessCodegen.ofCollectedResources(
                context,
                CollectedResourceProducer.fromFiles(BASE_PATH, OBJECT_VARIABLE_SOURCE_FULL_PATH.toFile()));

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).isNotEmpty();
    }
}
