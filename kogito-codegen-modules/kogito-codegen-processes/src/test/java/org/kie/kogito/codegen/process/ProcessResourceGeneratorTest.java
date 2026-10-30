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

import java.io.File;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.assertj.core.api.ListAssert;
import org.drools.io.FileSystemResource;
import org.jbpm.compiler.canonical.ProcessMetaData;
import org.jbpm.compiler.canonical.ProcessToExecModelGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.definition.process.Process;
import org.kie.kogito.codegen.api.AddonsConfig;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.JavaKogitoBuildContext;
import org.kie.kogito.codegen.faultTolerance.FaultToleranceAnnotator;
import org.kie.kogito.codegen.faultTolerance.FaultToleranceUtil;
import org.kie.kogito.codegen.process.config.WorkItemRestConfig;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcess;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import static org.assertj.core.api.Assertions.*;
import static org.kie.kogito.codegen.process.ProcessResourceGenerator.INVALID_CONTEXT_TEMPLATE;
import static org.kie.kogito.internal.utils.ConversionUtils.sanitizeClassName;

public class ProcessResourceGeneratorTest {

    private static final String SIGNAL_METHOD = "signal_";
    private static final List<String> JAVA_AND_QUARKUS_REST_ANNOTATIONS = List.of("DELETE", "GET", "POST");
    private static final List<String> SPRING_BOOT_REST_ANNOTATIONS = List.of("DeleteMapping", "GetMapping", "PostMapping");

    @Test
    void testProcessResourceGeneratorForJava() {
        KogitoBuildContext.Builder contextBuilder = JavaKogitoBuildContext.builder();
        String fileName = "src/test/resources/startsignal/StartSignalEventNoPayload.bpmn2"; // not relevant
        boolean transactionEnabled = true; // not relevant
        String expectedMessage = String.format(INVALID_CONTEXT_TEMPLATE, JavaKogitoBuildContext.CONTEXT_NAME);
        assertThatThrownBy(() -> getProcessResourceGenerator(contextBuilder, fileName,
                transactionEnabled))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining(expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testGenerateProcessWithDocumentation(KogitoBuildContext.Builder contextBuilder) {
        String fileName = "src/test/resources/ProcessWithDocumentation.bpmn";
        String expectedSummary = "This is the documentation";
        String expectedDescription = "This is the process instance description";

        testOpenApiDocumentation(contextBuilder, fileName, expectedSummary, expectedDescription);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testGenerateProcessWithoutDocumentation(KogitoBuildContext.Builder contextBuilder) {
        String fileName = "src/test/resources/ProcessWithoutDocumentation.bpmn";
        String expectedSummary = "ProcessWithoutDocumentation";
        String expectedDescription = "";

        testOpenApiDocumentation(contextBuilder, fileName, expectedSummary, expectedDescription);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testDynamicSignalEndpointGeneration(KogitoBuildContext.Builder contextBuilder) {
        String fileName = "src/test/resources/signalevent/IntermediateCatchEventSignalWithVariable.bpmn2";

        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder, fileName);

        // 1. No static endpoint for the raw "#{signalName}" literal must be present.
        boolean hasRawDynamicEndpoint = classDeclaration.getMethods().stream()
                .filter(m -> isRestMethod(m) && m.getNameAsString().startsWith(SIGNAL_METHOD))
                .anyMatch(m -> m.toString().contains("#{signalName}"));
        assertThat(hasRawDynamicEndpoint)
                .as("No static REST endpoint should be generated for a dynamic signal name #{signalName}")
                .isFalse();

        // 2. Exactly one dynamic endpoint (signal_dynamic) must be present.
        List<MethodDeclaration> dynamicMethods = classDeclaration.getMethods().stream()
                .filter(m -> isRestMethod(m) && m.getNameAsString().equals(SIGNAL_METHOD + "dynamic"))
                .toList();
        assertThat(dynamicMethods)
                .as("Exactly one dynamic signal endpoint must be generated")
                .hasSize(1);

        MethodDeclaration dynamicMethod = dynamicMethods.get(0);

        // 3. The method must accept a 'signalName' path parameter.
        Optional<Parameter> signalNameParam = dynamicMethod.getParameters().stream()
                .filter(p -> p.getNameAsString().equals("signalName"))
                .findFirst();
        assertThat(signalNameParam)
                .as("Dynamic signal endpoint must declare a 'signalName' path parameter")
                .isPresent();

        // 4. The 'signalName' parameter must carry a path-binding annotation (e.g. @PathParam or @PathVariable) pointing to "signalName".
        boolean hasPathAnnotation = signalNameParam.get().getAnnotations().stream()
                .anyMatch(a -> {
                    String s = a.toString();
                    return s.contains("signalName");
                });
        assertThat(hasPathAnnotation)
                .as("The 'signalName' path parameter must be annotated with its path binding (e.g. @PathParam(\"signalName\"))")
                .isTrue();

        // 5. The endpoint URL path must contain "signal/{signalName}".
        boolean hasCorrectPath = dynamicMethod.getAnnotations().stream()
                .anyMatch(a -> a.toString().contains("signal/{signalName}"));
        assertThat(hasCorrectPath)
                .as("The dynamic signal endpoint path must contain 'signal/{signalName}'")
                .isTrue();

        // 6. The method body must pass the 'signalName' variable (not a string literal) as the signal name argument.
        boolean hasVariableSignalName = dynamicMethod.getBody()
                .map(body -> body.findAll(NameExpr.class).stream()
                        .anyMatch(n -> "signalName".equals(n.getNameAsString())))
                .orElse(false);
        assertThat(hasVariableSignalName)
                .as("The dynamic signal endpoint body must reference the 'signalName' variable, not a literal")
                .isTrue();

        boolean hasLiteralDynamicSignal = dynamicMethod.getBody()
                .map(body -> body.findAll(StringLiteralExpr.class).stream()
                        .anyMatch(s -> s.getValue().contains("#{signalName}")))
                .orElse(false);
        assertThat(hasLiteralDynamicSignal)
                .as("The dynamic signal endpoint body must NOT contain a literal '#{signalName}'")
                .isFalse();
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testGenerateBoundarySignalEventOnTask(KogitoBuildContext.Builder contextBuilder) {
        String fileName = "src/test/resources/signalevent/BoundarySignalEventOnTask.bpmn2";

        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder, fileName);

        KogitoWorkflowProcess process = parseProcess(fileName);

        String outputType = new ModelClassGenerator(contextBuilder.build(), process).simpleName() + "Output";

        classDeclaration.getMethods().stream()
                .filter(method -> isRestMethod(method) && method.getNameAsString().startsWith(SIGNAL_METHOD))
                .forEach(method -> assertMethodOutputModelType(method, outputType));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testDuplicateTaskNameUserTasksEndpointsSingleEndpointSet(KogitoBuildContext.Builder contextBuilder) {
        String fileName = "src/test/resources/usertask/DuplicateTaskNamesProcess.bpmn";

        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder, fileName);

        List<String> methodNames = classDeclaration.getMethods().stream()
                .map(MethodDeclaration::getNameAsString)
                .toList();

        // Exactly one completeTask_SharedTaskName method in the class
        assertThat(methodNames).anyMatch(name -> name.matches("completeTask_SharedTaskName_\\d+"));

        long completeTaskCount = methodNames.stream()
                .filter(name -> name.matches("completeTask_SharedTaskName_\\d+"))
                .count();
        assertThat(completeTaskCount).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testWorkItemsHaveNoEndpointsByDefault(KogitoBuildContext.Builder contextBuilder) {
        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder, "src/test/resources/workitem/DuplicateWorkItemWithoutTaskNameInput.bpmn");

        assertThat(methodNames(classDeclaration)).noneMatch(name -> name.startsWith("completeTask_"));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testRegularWorkItemsAreNeverGroupedAndUseTheirNodeName(KogitoBuildContext.Builder contextBuilder) {
        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder, "src/test/resources/workitem/DuplicateWorkItemWithoutTaskNameInput.bpmn", restEnabled("Rest"));

        List<String> methodNames = methodNames(classDeclaration);

        assertThat(methodNames).anyMatch(name -> name.matches("completeTask_Rest_Call_1_\\d+"));
        assertThat(methodNames).anyMatch(name -> name.matches("completeTask_Rest_Call_2_\\d+"));
        assertThat(pathsOf(classDeclaration)).anyMatch(path -> path.contains("/Rest_Call_1/")).anyMatch(path -> path.contains("/Rest_Call_2/"));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testRegularWorkItemsSharingTaskNameInputAreNotGrouped(KogitoBuildContext.Builder contextBuilder) {
        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder,
                "src/test/resources/workitem/DuplicateWorkItemWithSharedTaskNameInput.bpmn", restEnabled("Service Task"));

        List<String> methodNames = methodNames(classDeclaration);

        assertThat(methodNames).anyMatch(name -> name.matches("completeTask_LogVariable1_\\d+"));
        assertThat(methodNames).anyMatch(name -> name.matches("completeTask_LogVariable2_\\d+"));
        assertThat(methodNames).noneMatch(name -> name.contains("SharedTaskName"));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testEndpointsOnlyForEnabledWorkItemType(KogitoBuildContext.Builder contextBuilder) {
        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder, "src/test/resources/workitem/DuplicateWorkItemWithoutTaskNameInput.bpmn", restEnabled("Email"));

        assertThat(methodNames(classDeclaration)).noneMatch(name -> name.startsWith("completeTask_"));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testQuotedWorkItemConfigurationIsAccepted(KogitoBuildContext.Builder contextBuilder) {
        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder, "src/test/resources/workitem/DuplicateWorkItemWithoutTaskNameInput.bpmn",
                Map.of("kogito.codegen.process.workitems.\"Rest\".rest.enabled", "true"));

        assertThat(methodNames(classDeclaration)).anyMatch(name -> name.matches("completeTask_Rest_Call_1_\\d+"));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testHumanTaskEndpointsAreAlwaysGenerated(KogitoBuildContext.Builder contextBuilder) {
        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder, "src/test/resources/usertask/DuplicateTaskNamesProcess.bpmn");

        assertThat(methodNames(classDeclaration)).anyMatch(name -> name.matches("completeTask_SharedTaskName_\\d+"));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testHumanTaskEndpointsCanBeExplicitlyDisabled(KogitoBuildContext.Builder contextBuilder) {
        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder,
                "src/test/resources/usertask/DuplicateTaskNamesProcess.bpmn",
                Map.of("kogito.codegen.process.workitems.\"Human Task\".rest.enabled", "false"));

        assertThat(methodNames(classDeclaration)).noneMatch(name -> name.startsWith("completeTask_"));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testWorkItemsWithoutEndpointsKeepTheirTaskModelFactoryEntry(KogitoBuildContext.Builder contextBuilder) {
        KogitoBuildContext context = createContext(contextBuilder);
        ProcessResourceGenerator generator = getProcessResourceGenerator(context, parseProcess("src/test/resources/workitem/DuplicateWorkItemWithoutTaskNameInput.bpmn"), true, false);

        assertThat(generator.generate()).doesNotContain("completeTask_Rest");
        String factory = generator.getTaskModelFactory();
        assertThat(factory).contains("DuplicateWorkItemsProcess_").contains("_TaskModel.from");
        assertThat(factory.split("case \"", -1).length - 1).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testRegularWorkItemNameClashingWithAnotherFailsFast(KogitoBuildContext.Builder contextBuilder) {
        assertThatThrownBy(() -> getResourceClassDeclaration(contextBuilder, "src/test/resources/workitem/SameNameRestWorkItems.bpmn", restEnabled("Rest")))
                .isInstanceOf(ProcessCodegenException.class)
                .hasMessageContaining("Rest_Call");
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testRegularWorkItemNameClashingWithHumanTaskFailsFast(KogitoBuildContext.Builder contextBuilder) {
        assertThatThrownBy(() -> getResourceClassDeclaration(contextBuilder, "src/test/resources/workitem/HumanTaskAndRestWorkItemSameName.bpmn", restEnabled("Rest")))
                .isInstanceOf(ProcessCodegenException.class)
                .hasMessageContaining("Rest_Call");
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testNameClashesAreIgnoredWhenEndpointsAreDisabled(KogitoBuildContext.Builder contextBuilder) {
        ClassOrInterfaceDeclaration sameName = getResourceClassDeclaration(contextBuilder, "src/test/resources/workitem/SameNameRestWorkItems.bpmn");
        ClassOrInterfaceDeclaration humanAndRest = getResourceClassDeclaration(contextBuilder, "src/test/resources/workitem/HumanTaskAndRestWorkItemSameName.bpmn");

        assertThat(methodNames(sameName)).noneMatch(name -> name.startsWith("completeTask_"));
        // only the human task has endpoints
        assertThat(methodNames(humanAndRest).stream().filter(name -> name.startsWith("completeTask_")).count()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testTriggerIsGeneratedOnlyForAdHocWorkItems(KogitoBuildContext.Builder contextBuilder) {
        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder,
                "src/test/resources/workitem/AdHocRestWorkItem.bpmn", restEnabled("Rest"));

        List<String> methodNames = methodNames(classDeclaration);

        // "Rest Call 1" is reached from the start event, "Rest Call 2" has no incoming connection
        assertThat(methodNames).noneMatch(name -> name.matches("signal_Rest_Call_1_\\d+"));
        assertThat(methodNames).anyMatch(name -> name.matches("signal_Rest_Call_2_\\d+"));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testTriggerSignalsTheRealNodeNameWhilePathIsSanitized(KogitoBuildContext.Builder contextBuilder) {
        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder,
                "src/test/resources/workitem/AdHocRestWorkItem.bpmn", restEnabled("Rest"));

        MethodDeclaration trigger = classDeclaration.getMethods().stream()
                .filter(m -> m.getNameAsString().matches("signal_Rest_Call_2_\\d+"))
                .findFirst()
                .orElseThrow();

        assertThat(trigger.toString())
                .contains("/Rest_Call_2/trigger")
                .contains("signalWorkItem(process, id, \"Rest Call 2\"");
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testTriggerIsGeneratedForAdHocWorkItemsEvenWhenRestDisabled(KogitoBuildContext.Builder contextBuilder) {
        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder, "src/test/resources/workitem/AdHocRestWorkItem.bpmn");

        List<String> methodNames = methodNames(classDeclaration);
        // Non-ad-hoc work item ("Rest Call 1") has neither trigger nor lifecycle endpoints
        assertThat(methodNames).noneMatch(name -> name.matches("signal_Rest_Call_1_\\d+"));
        assertThat(methodNames).noneMatch(name -> name.matches("completeTask_Rest_Call_1_\\d+"));

        // Ad-hoc work item ("Rest Call 2") has trigger endpoint, but no lifecycle endpoints
        assertThat(methodNames).anyMatch(name -> name.matches("signal_Rest_Call_2_\\d+"));
        assertThat(methodNames).noneMatch(name -> name.matches("completeTask_Rest_Call_2_\\d+"));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testDuplicatedHumanTasksWithSharedTaskNameInputGenerateSingleEndpointSet(KogitoBuildContext.Builder contextBuilder) {
        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder, "src/test/resources/usertask/DuplicateTaskNamesProcess.bpmn");

        assertThat(methodNames(classDeclaration).stream().filter(name -> name.matches("completeTask_SharedTaskName_\\d+")).count()).isEqualTo(1);
    }

    private static Map<String, String> restEnabled(String... workNames) {
        Map<String, String> properties = new HashMap<>();
        for (String workName : workNames) {
            properties.put(WorkItemRestConfig.CONFIG_PREFIX + workName + WorkItemRestConfig.CONFIG_SUFFIX, "true");
        }
        return properties;
    }

    private static List<String> methodNames(ClassOrInterfaceDeclaration classDeclaration) {
        return classDeclaration.getMethods().stream().map(MethodDeclaration::getNameAsString).toList();
    }

    private static List<String> pathsOf(ClassOrInterfaceDeclaration classDeclaration) {
        return classDeclaration.getMethods().stream()
                .flatMap(m -> m.getAnnotations().stream())
                .filter(a -> a.getNameAsString().equals("Path") || a.getNameAsString().endsWith("Mapping"))
                .map(AnnotationExpr::toString)
                .toList();
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testGenerateStartSignalEventStringPayload(KogitoBuildContext.Builder contextBuilder) {
        String fileName = "src/test/resources/startsignal/StartSignalEventStringPayload.bpmn2";
        String signalName = "start";

        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder, fileName);

        KogitoWorkflowProcess process = parseProcess(fileName);

        MethodDeclaration startSignalMethod = classDeclaration.getMethods().stream()
                .filter(method -> isRestMethod(method) && method.getNameAsString().equals(SIGNAL_METHOD + signalName))
                .findFirst()
                .orElseThrow();

        assertThat(startSignalMethod.isPublic()).isTrue();
        assertThat(startSignalMethod.getParameters()).hasSize(4);
        assertThat(startSignalMethod.getParameters().stream()
                .filter(parameter -> parameter.getNameAsString().equals("data"))
                .findFirst()
                .get()).matches(parameter -> parameter.getTypeAsString().equals("java.lang.String"));

        assertThat(startSignalMethod.getBody().get().getChildNodes()
                .stream()
                .anyMatch(child -> child.toString().equals("model.setMessage(data);"))).isTrue();

        String outputType = new ModelClassGenerator(contextBuilder.build(), process).simpleName() + "Output";

        classDeclaration.getMethods().stream()
                .filter(method -> isRestMethod(method) && method.getNameAsString().startsWith(SIGNAL_METHOD + "0"))
                .forEach(method -> assertMethodOutputModelType(method, outputType));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testGenerateStartSignalEventNoPayload(KogitoBuildContext.Builder contextBuilder) {
        String fileName = "src/test/resources/startsignal/StartSignalEventNoPayload.bpmn2";
        String signalName = "start";

        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder, fileName);

        KogitoWorkflowProcess process = parseProcess(fileName);

        MethodDeclaration startSignalMethod = classDeclaration.getMethods().stream()
                .filter(method -> isRestMethod(method) && method.getNameAsString().equals(SIGNAL_METHOD + signalName))
                .findFirst()
                .orElseThrow();

        assertThat(startSignalMethod.isPublic()).isTrue();
        assertThat(startSignalMethod.getParameters()).hasSize(3);
        assertThat(startSignalMethod.getParameters()
                .stream()
                .anyMatch(parameter -> parameter.getNameAsString().equals("data"))).isFalse();

        assertThat(startSignalMethod.getBody().get().getChildNodes()
                .stream()
                .anyMatch(child -> child.toString().equals("model.setMessage(data);"))).isFalse();

        String outputType = new ModelClassGenerator(contextBuilder.build(), process).simpleName() + "Output";

        classDeclaration.getMethods().stream()
                .filter(method -> isRestMethod(method) && method.getNameAsString().startsWith(SIGNAL_METHOD + "0"))
                .forEach(method -> assertMethodOutputModelType(method, outputType));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testManageTransactionalEnabled(KogitoBuildContext.Builder contextBuilder) {
        String fileName = "src/test/resources/startsignal/StartSignalEventNoPayload.bpmn2";

        boolean transactionEnabled = true;
        ProcessResourceGenerator processResourceGenerator = getProcessResourceGenerator(contextBuilder, fileName,
                transactionEnabled);
        CompilationUnit compilationUnit =
                processResourceGenerator.createCompilationUnit(processResourceGenerator.createTemplatedGeneratorBuilder());
        assertThat(compilationUnit).isNotNull();
        KogitoBuildContext kogitoBuildContext = contextBuilder.build();
        Collection<MethodDeclaration> restEndpoints = processResourceGenerator.getRestMethods(compilationUnit);
        // before processResourceGenerator.manageTransactional, the annotation is not there
        testTransactionAnnotationIsPresent(restEndpoints, kogitoBuildContext, false);
        processResourceGenerator.manageTransactional(compilationUnit);
        // the annotation is (conditionally) added after processResourceGenerator.manageTransactional
        testTransactionAnnotationIsPresent(restEndpoints, kogitoBuildContext, transactionEnabled);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testManageTransactionalDisabled(KogitoBuildContext.Builder contextBuilder) {
        String fileName = "src/test/resources/startsignal/StartSignalEventNoPayload.bpmn2";
        boolean transactionEnabled = false;
        ProcessResourceGenerator processResourceGenerator = getProcessResourceGenerator(contextBuilder, fileName,
                transactionEnabled);
        CompilationUnit compilationUnit =
                processResourceGenerator.createCompilationUnit(processResourceGenerator.createTemplatedGeneratorBuilder());
        assertThat(compilationUnit).isNotNull();
        KogitoBuildContext kogitoBuildContext = contextBuilder.build();
        Collection<MethodDeclaration> restEndpoints = processResourceGenerator.getRestMethods(compilationUnit);
        // before processResourceGenerator.manageTransactional, the annotation is not there
        testTransactionAnnotationIsPresent(restEndpoints, kogitoBuildContext, false);
        processResourceGenerator.manageTransactional(compilationUnit);
        // the annotation is (conditionally) added after processResourceGenerator.manageTransactional
        testTransactionAnnotationIsPresent(restEndpoints, kogitoBuildContext, transactionEnabled);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testProcessRestResourceGenerationWithFaultToleranceDisabled(KogitoBuildContext.Builder contextBuilder) {
        String fileName = "src/test/resources/startsignal/StartSignalEventNoPayload.bpmn2";
        ProcessResourceGenerator processResourceGenerator = getProcessResourceGenerator(contextBuilder, fileName, false, false);
        CompilationUnit compilationUnit =
                processResourceGenerator.createCompilationUnit(processResourceGenerator.createTemplatedGeneratorBuilder());
        assertThat(compilationUnit).isNotNull();

        KogitoBuildContext context = contextBuilder.build();
        Collection<MethodDeclaration> restEndpoints = processResourceGenerator.getRestMethods(compilationUnit);

        // before processResourceGenerator.manageFaultTolerance, the annotation is not there
        testFaultToleranceAnnotationIsPresent(restEndpoints, context, false);

        processResourceGenerator.manageFaultTolerance(compilationUnit);
        // the annotation is (conditionally) added after processResourceGenerator.manageFaultTolerance
        testFaultToleranceAnnotationIsPresent(restEndpoints, context, false);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testProcessRestResourceGenerationWithFaultToleranceEnabled(KogitoBuildContext.Builder contextBuilder) {
        String fileName = "src/test/resources/startsignal/StartSignalEventNoPayload.bpmn2";
        ProcessResourceGenerator processResourceGenerator = getProcessResourceGenerator(contextBuilder, fileName, true, true);
        CompilationUnit compilationUnit =
                processResourceGenerator.createCompilationUnit(processResourceGenerator.createTemplatedGeneratorBuilder());
        assertThat(compilationUnit).isNotNull();

        KogitoBuildContext context = contextBuilder.build();
        Collection<MethodDeclaration> restEndpoints = processResourceGenerator.getRestMethods(compilationUnit);

        // before processResourceGenerator.manageFaultTolerance, the annotation is not there
        testFaultToleranceAnnotationIsPresent(restEndpoints, context, false);

        processResourceGenerator.manageFaultTolerance(compilationUnit);
        // the annotation is (conditionally) added after processResourceGenerator.manageFaultTolerance
        testFaultToleranceAnnotationIsPresent(restEndpoints, context, true);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    void testProcessRestResourceGenerationWithFaultToleranceEnabledWithTransactionsDisabled(KogitoBuildContext.Builder contextBuilder) {
        String fileName = "src/test/resources/startsignal/StartSignalEventNoPayload.bpmn2";
        ProcessResourceGenerator processResourceGenerator = getProcessResourceGenerator(contextBuilder, fileName, false, true);
        CompilationUnit compilationUnit =
                processResourceGenerator.createCompilationUnit(processResourceGenerator.createTemplatedGeneratorBuilder());
        assertThat(compilationUnit).isNotNull();

        assertThatThrownBy(() -> {
            processResourceGenerator.manageFaultTolerance(compilationUnit);
        }).isInstanceOf(ProcessCodegenException.class)
                .hasMessageContaining("Fault tolerance is enabled, but transactions are disabled. Please enable transactions before fault tolerance.");
    }

    public static void testFaultToleranceAnnotationIsPresent(Collection<MethodDeclaration> restEndpoints, KogitoBuildContext context, boolean shouldAnnotationBeThere) {
        FaultToleranceAnnotator faultToleranceAnnotator = FaultToleranceUtil.lookFaultToleranceAnnotatorForContext(context);

        for (MethodDeclaration method : restEndpoints) {
            if (shouldAnnotationBeThere) {
                assertThat(findAnnotationExpr(method, faultToleranceAnnotator.getRetryAnnotationName())).isPresent();
            } else {
                assertThat(findAnnotationExpr(method, faultToleranceAnnotator.getRetryAnnotationName())).isNotPresent();
            }
        }
    }

    public static Optional<AnnotationExpr> findAnnotationExpr(MethodDeclaration method, String fqn) {
        for (AnnotationExpr expr : method.getAnnotations()) {
            if (expr.getNameAsString().equals(fqn)) {
                return Optional.of(expr);
            }
        }
        return Optional.empty();
    }

    public static void testTransactionAnnotationIsPresent(Collection<MethodDeclaration> restEndpoints,
            KogitoBuildContext kogitoBuildContext,
            boolean enabled) {
        String transactionalAnnotation =
                kogitoBuildContext.getDependencyInjectionAnnotator().getTransactionalAnnotation();
        restEndpoints.forEach(methodDeclaration -> {
            ListAssert<?> transactionAnnotationAssert = assertThat(
                    methodDeclaration.getAnnotations().stream().filter(annotationExpr -> annotationExpr.getNameAsString().equals(transactionalAnnotation)));
            if (enabled) {
                transactionAnnotationAssert.hasSize(1);
            } else {
                transactionAnnotationAssert.isEmpty();
            }
            if (methodDeclaration.getName().toString().startsWith("createResource_")) {
                ListAssert<?> stmtsAsserts = assertThat(
                        methodDeclaration.getBody()
                                .get()
                                .getStatements());
                stmtsAsserts.hasSize(2);
            }
        });
    }

    void testOpenApiDocumentation(KogitoBuildContext.Builder contextBuilder, String fileName, String expectedSummary, String expectedDescription) {
        ClassOrInterfaceDeclaration classDeclaration = getResourceClassDeclaration(contextBuilder, fileName);

        classDeclaration.getMethods().stream()
                .filter(this::isRestMethod)
                .forEach(method -> assertThatMethodHasOpenApiDocumentation(method, fileName, expectedSummary, expectedDescription));
    }

    private ClassOrInterfaceDeclaration getResourceClassDeclaration(KogitoBuildContext.Builder contextBuilder, String fileName) {
        return getResourceClassDeclaration(contextBuilder, fileName, Map.of());
    }

    private ClassOrInterfaceDeclaration getResourceClassDeclaration(KogitoBuildContext.Builder contextBuilder, String fileName, Map<String, String> properties) {
        KogitoWorkflowProcess process = parseProcess(fileName);
        KogitoBuildContext context = createContext(contextBuilder);
        properties.forEach(context::setApplicationProperty);
        CompilationUnit compilationUnit = StaticJavaParser.parse(getProcessResourceGenerator(context, process, true, false).generate());
        String className = sanitizeClassName(process.getId() + "Resource");
        Optional<ClassOrInterfaceDeclaration> classDeclaration = compilationUnit.getClassByName(className);
        assertThat(classDeclaration).isNotEmpty();
        return classDeclaration.orElseThrow();
    }

    private ProcessResourceGenerator getProcessResourceGenerator(KogitoBuildContext.Builder contextBuilder,
            String fileName, boolean withTransaction) {
        return getProcessResourceGenerator(contextBuilder, parseProcess(fileName), withTransaction, false);
    }

    private ProcessResourceGenerator getProcessResourceGenerator(KogitoBuildContext.Builder contextBuilder,
            String fileName, boolean withTransaction, boolean wihtFaultTolerance) {
        return getProcessResourceGenerator(contextBuilder, parseProcess(fileName), withTransaction, wihtFaultTolerance);
    }

    private ProcessResourceGenerator getProcessResourceGenerator(KogitoBuildContext.Builder contextBuilder,
            KogitoWorkflowProcess process,
            boolean withTransaction,
            boolean withFaultTolerance) {
        return getProcessResourceGenerator(createContext(contextBuilder), process, withTransaction, withFaultTolerance);
    }

    private ProcessResourceGenerator getProcessResourceGenerator(KogitoBuildContext context,
            KogitoWorkflowProcess process,
            boolean withTransaction,
            boolean withFaultTolerance) {
        ProcessExecutableModelGenerator execModelGen =
                new ProcessExecutableModelGenerator(process, new ProcessToExecModelGenerator(context.getClassLoader()));

        KogitoWorkflowProcess workFlowProcess = execModelGen.process();
        ProcessResourceGenerator toReturn = new ProcessResourceGenerator(
                context,
                workFlowProcess,
                new ModelClassGenerator(context, workFlowProcess).className(),
                execModelGen.className(),
                context.getPackageName() + ".Application");

        ProcessMetaData metaData = execModelGen.generate();

        toReturn
                .withWorkItems(new WorkItemModelClassGenerator(workFlowProcess).generate())
                .withSignals(metaData.getSignals())
                .withTriggers(metaData.isStartable(), metaData.isDynamic(), metaData.getTriggers())
                .withTransaction(withTransaction)
                .withFaultTolerance(withFaultTolerance);
        return toReturn;
    }

    private void assertThatMethodHasOpenApiDocumentation(MethodDeclaration method, String fileName, String summary, String description) {
        String operationId = "";
        String nameWithoutExt = Paths.get(fileName).getFileName().toString().replaceFirst("[.][^.]+$", "");
        if (method.getNameAsString().startsWith("createResource_")) {
            operationId = String.format("createProcessInstance_%s", nameWithoutExt);
        } else if (method.getNameAsString().startsWith("getResources_")) {
            operationId = String.format("getAllProcessInstances_%s", nameWithoutExt);
        } else if (method.getNameAsString().startsWith("getResourceSchema_")) {
            operationId = String.format("getResourceSchema_%s", nameWithoutExt);
        } else if (method.getNameAsString().startsWith("getResource_")) {
            operationId = String.format("getProcessInstance_%s", nameWithoutExt);
        } else if (method.getNameAsString().startsWith("deleteResource_")) {
            operationId = String.format("deleteProcessInstance_%s", nameWithoutExt);
        } else if (method.getNameAsString().startsWith("updateModel_")) {
            operationId = String.format("updateProcessInstance_%s", nameWithoutExt);
        } else if (method.getNameAsString().startsWith("updateModelPartial_")) {
            operationId = String.format("patchProcessInstance_%s", nameWithoutExt);
        } else if (method.getNameAsString().startsWith("getTasks_")) {
            operationId = String.format("getTasksInstance_%s", nameWithoutExt);
        }

        Optional<AnnotationExpr> annotation = method.getAnnotationByName("Operation");
        assertThat(annotation).isNotEmpty();
        assertThatAnnotationHasSummaryAndDescription(annotation.orElseThrow(), summary, description, operationId);
    }

    private void assertMethodOutputModelType(MethodDeclaration method, String outputType) {
        assertThat(method.getType()).isNotNull();
        assertThat(method.getType().asString()).isEqualTo(outputType);
    }

    private void assertThatAnnotationHasSummaryAndDescription(AnnotationExpr annotation, String summary, String description, String operationId) {
        NodeList<MemberValuePair> pairs = ((NormalAnnotationExpr) annotation).getPairs();

        assertThat(pairs).containsExactlyInAnyOrder(
                new MemberValuePair("summary", new StringLiteralExpr(summary)),
                new MemberValuePair("description", new StringLiteralExpr(description)),
                new MemberValuePair("operationId", new StringLiteralExpr(operationId)));
    }

    private boolean isRestMethod(MethodDeclaration method) {
        return method.getAnnotations().stream()
                .anyMatch(isOpenApiAnnotation().or(isSwaggerAnnotation()));
    }

    private Predicate<AnnotationExpr> isOpenApiAnnotation() {
        return annotation -> JAVA_AND_QUARKUS_REST_ANNOTATIONS.contains(annotation.getNameAsString());
    }

    private Predicate<AnnotationExpr> isSwaggerAnnotation() {
        return annotation -> SPRING_BOOT_REST_ANNOTATIONS.contains(annotation.getNameAsString());
    }

    private KogitoWorkflowProcess parseProcess(String fileName) {
        Collection<Process> processes = ProcessCodegen.parseProcessFile(new FileSystemResource(new File(fileName)));
        assertThat(processes).hasSize(1);
        Process process = processes.stream().findAny().orElseThrow();
        assertThat(process).isInstanceOf(KogitoWorkflowProcess.class);
        return (KogitoWorkflowProcess) process;
    }

    private KogitoBuildContext createContext(KogitoBuildContext.Builder contextBuilder) {
        AddonsConfig addonsConfig = AddonsConfig.builder()
                .withMonitoring(false)
                .withPrometheusMonitoring(false)
                .build();

        return contextBuilder.withAddonsConfig(addonsConfig).build();
    }
}
