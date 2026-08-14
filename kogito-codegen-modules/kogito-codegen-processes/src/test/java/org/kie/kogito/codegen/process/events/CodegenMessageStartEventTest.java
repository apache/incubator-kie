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
package org.kie.kogito.codegen.process.events;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.drools.codegen.common.GeneratedFile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.api.utils.KogitoCodeGenConstants;
import org.kie.kogito.codegen.core.io.CollectedResourceProducer;
import org.kie.kogito.codegen.process.ProcessCodegen;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.codegen.api.utils.KogitoContextTestUtils.mockClassAvailabilityResolver;

public class CodegenMessageStartEventTest {

    private static final Path BASE_PATH = Paths.get("src/test/resources/").toAbsolutePath();
    private static final String MESSAGE_START_EVENT_SOURCE = "messagestartevent/MessageStartEvent.bpmn2";
    private static final Path MESSAGE_START_EVENT_SOURCE_FULL_SOURCE = BASE_PATH.resolve(MESSAGE_START_EVENT_SOURCE);
    private static final String MESSAGE_END_EVENT_SOURCE = "messagestartevent/MessageEndEvent.bpmn2";
    private static final Path MESSAGE_END_EVENT_SOURCE_FULL_SOURCE = BASE_PATH.resolve(MESSAGE_END_EVENT_SOURCE);
    private static final String MESSAGE_START_END_EVENT_SOURCE = "messagestartevent/MessageStartAndEndEvent.bpmn2";
    private static final Path MESSAGE_START_END_EVENT_SOURCE_FULL_SOURCE = BASE_PATH.resolve(MESSAGE_START_END_EVENT_SOURCE);
    private static final String MESSAGE_START_EVENT_NO_MAPPING_SOURCE = "messagestartevent/MessageStartEventNoMapping.bpmn2";
    private static final Path MESSAGE_START_EVENT_NO_MAPPING_FULL_SOURCE = BASE_PATH.resolve(MESSAGE_START_EVENT_NO_MAPPING_SOURCE);
    private static final String MESSAGE_START_EVENT_EMPTY_STRUCTURE_REF_SOURCE = "messagestartevent/MessageStartEventEmptyStructureRef.bpmn2";
    private static final Path MESSAGE_START_EVENT_EMPTY_STRUCTURE_REF_FULL_SOURCE = BASE_PATH.resolve(MESSAGE_START_EVENT_EMPTY_STRUCTURE_REF_SOURCE);
    private static final String MESSAGE_END_EVENT_NO_MAPPING_SOURCE = "messagestartevent/MessageEndEventNoMapping.bpmn2";
    private static final Path MESSAGE_END_EVENT_NO_MAPPING_FULL_SOURCE = BASE_PATH.resolve(MESSAGE_END_EVENT_NO_MAPPING_SOURCE);

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    public void testRESTApiForMessageStartEvent(KogitoBuildContext.Builder contextBuilder) {
        contextBuilder
                .withClassAvailabilityResolver(mockClassAvailabilityResolver(singleton(KogitoCodeGenConstants.QUARKUS_TRANSACTION_MANAGER_CLASS), emptyList()));
        KogitoBuildContext context = contextBuilder.build();
        ProcessCodegen codeGenerator = ProcessCodegen.ofCollectedResources(
                context,
                CollectedResourceProducer.fromFiles(BASE_PATH, MESSAGE_START_EVENT_SOURCE_FULL_SOURCE.toFile()));

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).isNotEmpty();

        List<GeneratedFile> resources = generatedFiles.stream()
                .filter(generatedFile -> generatedFile.relativePath().endsWith("org/kie/kogito/test/MessageStartEventResource.java"))
                .collect(Collectors.toList());

        if (context.hasRESTForGenerator(codeGenerator)) {
            assertThat(resources).hasSize(1);

            CompilationUnit parsedResource = StaticJavaParser.parse(new String(resources.get(0).contents()));

            assertThat(parsedResource
                    .findFirst(MethodDeclaration.class, md -> md.getNameAsString().startsWith("createResource")))
                            .withFailMessage("For processes without none start event there should not be create resource method")
                            .isEmpty();
        } else {
            assertThat(resources).isEmpty();
        }

    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    public void testMessageStartEventWithoutItemRef(KogitoBuildContext.Builder contextBuilder) {
        contextBuilder
                .withClassAvailabilityResolver(mockClassAvailabilityResolver(singleton(KogitoCodeGenConstants.QUARKUS_TRANSACTION_MANAGER_CLASS), emptyList()));
        KogitoBuildContext context = contextBuilder.build();
        ProcessCodegen codeGenerator = ProcessCodegen.ofCollectedResources(
                context,
                CollectedResourceProducer.fromFiles(BASE_PATH, MESSAGE_START_EVENT_NO_MAPPING_FULL_SOURCE.toFile()));

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).isNotEmpty();

        List<GeneratedFile> processes = generatedFiles.stream()
                .filter(generatedFile -> generatedFile.relativePath().endsWith("org/kie/kogito/test/MessageStartEventNoMappingProcess.java"))
                .collect(Collectors.toList());
        assertThat(processes).hasSize(1);

        CompilationUnit parsedProcess = StaticJavaParser.parse(new String(processes.get(0).contents()));

        List<MethodCallExpr> correlationMessages = parsedProcess.findAll(MethodCallExpr.class,
                methodCall -> "newCorrelationMessage".equals(methodCall.getNameAsString()));
        assertThat(correlationMessages)
                .withFailMessage("A message without itemRef must still be registered as a correlation message")
                .hasSize(1);
        assertThat(correlationMessages.get(0).getArgument(2).asStringLiteralExpr().getValue())
                .withFailMessage("A message without itemRef carries no data, so its type must default to java.lang.Object")
                .isEqualTo("java.lang.Object");
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    public void testMessageStartEventWithEmptyStructureRef(KogitoBuildContext.Builder contextBuilder) {
        contextBuilder
                .withClassAvailabilityResolver(mockClassAvailabilityResolver(singleton(KogitoCodeGenConstants.QUARKUS_TRANSACTION_MANAGER_CLASS), emptyList()));
        KogitoBuildContext context = contextBuilder.build();
        ProcessCodegen codeGenerator = ProcessCodegen.ofCollectedResources(
                context,
                CollectedResourceProducer.fromFiles(BASE_PATH, MESSAGE_START_EVENT_EMPTY_STRUCTURE_REF_FULL_SOURCE.toFile()));

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).isNotEmpty();

        List<GeneratedFile> processes = generatedFiles.stream()
                .filter(generatedFile -> generatedFile.relativePath().endsWith("org/kie/kogito/test/MessageStartEventEmptyStructureRefProcess.java"))
                .collect(Collectors.toList());
        assertThat(processes).hasSize(1);

        CompilationUnit parsedProcess = StaticJavaParser.parse(new String(processes.get(0).contents()));

        List<MethodCallExpr> correlationMessages = parsedProcess.findAll(MethodCallExpr.class,
                methodCall -> "newCorrelationMessage".equals(methodCall.getNameAsString()));
        assertThat(correlationMessages)
                .withFailMessage("A message pointing to an itemDefinition with an empty structureRef must still be registered as a correlation message")
                .hasSize(1);
        assertThat(correlationMessages.get(0).getArgument(2).asStringLiteralExpr().getValue())
                .withFailMessage("An empty structureRef carries no data, so the message type must default to java.lang.Object")
                .isEqualTo("java.lang.Object");
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    public void testMessageEndEventWithoutDataMapping(KogitoBuildContext.Builder contextBuilder) {
        contextBuilder
                .withClassAvailabilityResolver(mockClassAvailabilityResolver(singleton(KogitoCodeGenConstants.QUARKUS_TRANSACTION_MANAGER_CLASS), emptyList()));
        KogitoBuildContext context = contextBuilder.build();
        ProcessCodegen codeGenerator = ProcessCodegen.ofCollectedResources(
                context,
                CollectedResourceProducer.fromFiles(BASE_PATH, MESSAGE_END_EVENT_NO_MAPPING_FULL_SOURCE.toFile()));

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).isNotEmpty();

        List<GeneratedFile> processes = generatedFiles.stream()
                .filter(generatedFile -> generatedFile.relativePath().endsWith("org/kie/kogito/test/MessageEndEventNoMappingProcess.java"))
                .collect(Collectors.toList());
        assertThat(processes).hasSize(1);

        // ProduceEventAction is generated into a node builder class (MessageEndEventNoMappingProcess_node__*.java),
        // not the process class itself, so we search all files belonging to this process.
        List<GeneratedFile> processAndNodeBuilders = generatedFiles.stream()
                .filter(generatedFile -> {
                    String path = generatedFile.relativePath();
                    return path.contains("org/kie/kogito/test/MessageEndEventNoMappingProcess");
                })
                .collect(Collectors.toList());

        List<ObjectCreationExpr> producerActions = processAndNodeBuilders.stream()
                .flatMap(f -> StaticJavaParser.parse(new String(f.contents()))
                        .findAll(ObjectCreationExpr.class,
                                objectCreation -> objectCreation.getType().getNameAsString().equals("ProduceEventAction"))
                        .stream())
                .collect(Collectors.toList());
        assertThat(producerActions).hasSize(1);
        assertThat(producerActions.get(0).getArgument(1))
                .withFailMessage("A message end event without data mapping has no variable to send, so no variable name must be passed")
                .isInstanceOf(NullLiteralExpr.class);
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void testMessageConsumerForMessageStartEventWithoutDataMapping(KogitoBuildContext.Builder contextBuilder) {
        Properties properties = new Properties();
        properties.put("kogito.addon.cloudevents.kafka.kogito_outgoing_stream", "test-out");
        properties.put("kogito.addon.cloudevents.kafka.kogito_incoming_stream", "test-in");
        contextBuilder.withApplicationProperties(properties);

        KogitoBuildContext context = contextBuilder.build();
        ProcessCodegen codeGenerator = ProcessCodegen.ofCollectedResources(
                context,
                CollectedResourceProducer.fromFiles(BASE_PATH, MESSAGE_START_EVENT_NO_MAPPING_FULL_SOURCE.toFile()));

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).isNotEmpty();

        List<GeneratedFile> consumers = generatedFiles.stream()
                .filter(generatedFile -> generatedFile.relativePath().endsWith("org/kie/kogito/test/MessageStartEventNoMappingMessageConsumer_StartEvent_1.java"))
                .collect(Collectors.toList());
        assertThat(consumers).hasSize(1);

        CompilationUnit parsedConsumer = StaticJavaParser.parse(new String(consumers.get(0).contents()));

        assertThat(parsedConsumer.findAll(MethodCallExpr.class, mc -> mc.getNameAsString().contains("$SetModelMethodName$")))
                .withFailMessage("A message without data mapping has no variable to set on the model")
                .isEmpty();

        if (context.hasDI()) {
            assertThat(parsedConsumer.findFirst(MethodDeclaration.class, md -> "getModelConverter".equals(md.getNameAsString())))
                    .withFailMessage("The model converter must be kept, otherwise the message would not start the process")
                    .isPresent();
        }
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#restContextBuilders")
    public void testRESTApiForMessageEndEvent(KogitoBuildContext.Builder contextBuilder) {
        contextBuilder
                .withClassAvailabilityResolver(mockClassAvailabilityResolver(singleton(KogitoCodeGenConstants.QUARKUS_TRANSACTION_MANAGER_CLASS), emptyList()));
        KogitoBuildContext context = contextBuilder.build();
        ProcessCodegen codeGenerator = ProcessCodegen.ofCollectedResources(
                context,
                CollectedResourceProducer.fromFiles(BASE_PATH, MESSAGE_END_EVENT_SOURCE_FULL_SOURCE.toFile()));

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).isNotEmpty();

        List<GeneratedFile> resources = generatedFiles.stream()
                .filter(generatedFile -> generatedFile.relativePath().endsWith("org/kie/kogito/test/MessageStartEventResource.java"))
                .collect(Collectors.toList());

        if (context.hasRESTForGenerator(codeGenerator)) {
            assertThat(resources).hasSize(1);

            CompilationUnit parsedResource = StaticJavaParser.parse(new String(resources.get(0).contents()));

            assertThat(parsedResource
                    .findAll(MethodDeclaration.class, md -> md.getNameAsString().startsWith("createResource")))
                            .withFailMessage("Must have method with name 'createResource'")
                            .hasSize(1);
        } else {
            assertThat(resources).isEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    public void testMessageProducerForMessageEndEvent(KogitoBuildContext.Builder contextBuilder) {
        Properties properties = new Properties();
        properties.put("kogito.addon.cloudevents.kafka.kogito_outgoing_stream", "test-out");
        properties.put("kogito.addon.cloudevents.kafka.kogito_incoming_stream", "test-in");
        contextBuilder.withApplicationProperties(properties);

        KogitoBuildContext context = contextBuilder.build();
        ProcessCodegen codeGenerator = ProcessCodegen.ofCollectedResources(
                context,
                CollectedResourceProducer.fromFiles(BASE_PATH, MESSAGE_START_END_EVENT_SOURCE_FULL_SOURCE.toFile()));

        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();
        assertThat(generatedFiles).isNotEmpty();

        // class name is with suffix that represents node id as there might be multiple end message events
        List<GeneratedFile> resources = generatedFiles.stream()
                .filter(generatedFile -> generatedFile.relativePath().endsWith("org/kie/kogito/test/MessageStartEventMessageProducer_EndEvent_1.java"))
                .collect(Collectors.toList());
        assertThat(resources).hasSize(1);

        CompilationUnit parsedResource = StaticJavaParser.parse(new String(resources.get(0).contents()));

        if (context.hasDI()) {
            assertThat(parsedResource
                    .findAll(ClassOrInterfaceDeclaration.class, cd -> cd.getExtendedTypes().stream().anyMatch(et -> et.getNameAsString().endsWith("AbstractMessageProducer"))))
                            .withFailMessage("Must extends class 'AbstractMessageProducer'")
                            .hasSize(1);
        } else {
            assertThat(parsedResource
                    .findAll(MethodDeclaration.class, md -> md.getNameAsString().equals("produce")))
                            .withFailMessage("Must have method with name 'produce'")
                            .hasSize(1);
        }
    }
}
