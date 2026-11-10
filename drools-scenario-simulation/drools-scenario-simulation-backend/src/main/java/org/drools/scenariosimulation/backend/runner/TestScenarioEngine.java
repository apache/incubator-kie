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
package org.drools.scenariosimulation.backend.runner;

import org.drools.scenariosimulation.api.model.ScenarioSimulationModel;
import org.drools.scenariosimulation.api.utils.ConstantsHolder;
import org.drools.scenariosimulation.backend.expression.ExpressionEvaluatorFactory;
import org.drools.scenariosimulation.backend.runner.model.ScenarioRunnerDTO;
import org.drools.scenariosimulation.backend.runner.model.ScenarioRunnerData;
import org.drools.scenariosimulation.backend.util.ScenarioSimulationXMLPersistence;
import org.junit.platform.commons.support.AnnotationSupport;

import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

/**
 * It implements a TestEngine that discovers and executes Test Scenario files that live in the final
 * user project. A Java Class in the src/test/java directory with the {@link TestScenarioActivator} annotation
 * must be present to enable the scesim tests execution.
 */
public class TestScenarioEngine implements TestEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestScenarioEngine.class);
    private static final String TEST_ENGINE_ID = "kie-test-scenario";
    private static final String TEST_ENGINE_DESCRIPTION = "Apache KIE Test Scenario engine";
    private static final ScenarioSimulationXMLPersistence XML_READER = ScenarioSimulationXMLPersistence.getInstance();

    @Override
    public String getId() {
        return TEST_ENGINE_ID;
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
        LOGGER.debug("Discovering scesim files for {}", TEST_ENGINE_DESCRIPTION);

        EngineDescriptor engineDescriptor = new EngineDescriptor(uniqueId, TEST_ENGINE_DESCRIPTION);

        discoveryRequest.getSelectorsByType(ClassSelector.class).forEach(selector ->
            appendTestSuites(selector.getJavaClass(), engineDescriptor)
        );

        LOGGER.debug("Discovered {} scesim files", engineDescriptor.getChildren().size());
        return engineDescriptor;
    }

    @Override
    public void execute(ExecutionRequest executionRequest) {
        LOGGER.debug("Executing {} Test Scenarios", executionRequest.getRootTestDescriptor().getChildren().size());

        EngineExecutionListener listener = executionRequest.getEngineExecutionListener();
        executionRequest.getRootTestDescriptor().getChildren().stream()
                .map(TestScenarioTestSuiteDescriptor.class::cast)
                .forEach(testSuiteDescriptor -> {
                    LOGGER.debug("Executing {} Test Scenario which contains {} scenarios", testSuiteDescriptor.getDisplayName(),
                            testSuiteDescriptor.getChildren().size());
                    ScenarioRunnerDTO scenarioRunnerDTO = testSuiteDescriptor.getScenarioRunnerDTO();
                    if (scenarioRunnerDTO.getSettings().isSkipFromBuild()) {
                        LOGGER.debug("Skipping {} scenario", testSuiteDescriptor.getDisplayName());
                        listener.executionSkipped(testSuiteDescriptor, testSuiteDescriptor.getDisplayName() + " skipped from the build");
                        return;
                    }
                    listener.executionStarted(testSuiteDescriptor);
                    AbstractRunnerHelper runnerHelper = getRunnerHelper(testSuiteDescriptor.getTestScenarioType());

                    testSuiteDescriptor.getChildren().stream()
                            .map(TestScenarioTestDescriptor.class::cast)
                            .forEach(testDescriptor -> {
                                LOGGER.debug("Executing {} scenario", testSuiteDescriptor.getDisplayName());
                                listener.executionStarted(testDescriptor);

                                KieContainer kieContainer = KieServices.get().getKieClasspathContainer();
                                ExpressionEvaluatorFactory expressionEvaluatorFactory = ExpressionEvaluatorFactory.create(
                                        kieContainer.getClassLoader(),
                                        scenarioRunnerDTO.getSettings().getType());

                                try {
                                    runnerHelper.run(kieContainer,
                                            scenarioRunnerDTO.getSimulationModelDescriptor(),
                                            testDescriptor.getScenarioWithIndex(),
                                            expressionEvaluatorFactory,
                                            kieContainer.getClassLoader(),
                                            new ScenarioRunnerData(),
                                            scenarioRunnerDTO.getSettings(),
                                            scenarioRunnerDTO.getBackground());

                                    LOGGER.debug("{} scenario successful", testSuiteDescriptor.getDisplayName());
                                    listener.executionFinished(testDescriptor, TestExecutionResult.successful());
                                } catch (Exception e) {
                                    LOGGER.debug("{} scenario failed", testSuiteDescriptor.getDisplayName());
                                    listener.executionFinished(testDescriptor,
                                            TestExecutionResult.failed(defineFailureException(e,
                                                    testDescriptor.getScenarioWithIndex().getIndex(),
                                                    testDescriptor.getScenarioWithIndex().getScesimData().getDescription(),
                                                    testSuiteDescriptor.getDisplayName())));
                                }
                            });
                    LOGGER.debug("{} Test Scenario suite executed", testSuiteDescriptor.getDisplayName());
                    listener.executionFinished(testSuiteDescriptor, TestExecutionResult.successful());
                });
    }

    static void appendTestSuites(Class<?> javaClass, TestDescriptor engineDescriptor) {
        if (AnnotationSupport.isAnnotated(javaClass, TestScenarioActivator.class)) {
            LOGGER.debug("A class with @TestScenarioActivator annotation found: {}", javaClass.getCanonicalName());

            getScesimFileAbsolutePaths().stream().map(TestScenarioEngine::parseFile).forEach(scenarioRunnerDTO -> {
                String fileName = getScesimFileName(scenarioRunnerDTO.getFileName());
                TestScenarioTestSuiteDescriptor suite = new TestScenarioTestSuiteDescriptor(engineDescriptor, javaClass, fileName, scenarioRunnerDTO);
                engineDescriptor.addChild(suite);
                scenarioRunnerDTO.getScenarioWithIndices().forEach(scenarioWithIndex ->
                        suite.addChild(new TestScenarioTestDescriptor(suite, javaClass, fileName, scenarioWithIndex))
                );
            });
        }
    }

    static List<String> getScesimFileAbsolutePaths() {
        try (Stream<Path> fileStream = Files.walk(Paths.get("."))) {
            LOGGER.debug("Scanning Test Scenario (*.scesim) files");
            List<String> scesimFileAbsolutePaths = fileStream.filter(TestScenarioEngine::filterResource)
                    .map(Path::normalize)
                    .map(Path::toFile)
                    .map(File::getAbsolutePath)
                    .toList();

            LOGGER.debug("Found Test Scenario (*.scesim) {} files", scesimFileAbsolutePaths.size());
            scesimFileAbsolutePaths.forEach(LOGGER::debug);

            return scesimFileAbsolutePaths;
        } catch (IOException e) {
            LOGGER.error("Error scanning Test Scenario (*.scesim)", e);
            return Collections.emptyList();
        }
    }

    static String getScesimFileName(String fileFullPath) {
        int idx = fileFullPath.replace("\\", "/").lastIndexOf('/');
        String fileName = idx >= 0 ? fileFullPath.substring(idx + 1) : fileFullPath;
        return fileName.endsWith(ConstantsHolder.SCESIM_EXTENSION) ?
                fileName.substring(0, fileName.lastIndexOf(ConstantsHolder.SCESIM_EXTENSION)) :
                fileName;
    }

    static boolean filterResource(Path path) {
        return path.toString().endsWith(".scesim") &&
                !path.toAbsolutePath().toString().replace("\\", "/").contains("/target/") &&
                Files.isRegularFile(path);
    }

    static ScenarioRunnerDTO parseFile(String path) {
        try (final Scanner scanner = new Scanner(new File(path))) {
            String rawFile = scanner.useDelimiter("\\Z").next();
            ScenarioSimulationModel scenarioSimulationModel = XML_READER.unmarshal(rawFile);
            return new ScenarioRunnerDTO(scenarioSimulationModel, path);
        } catch (FileNotFoundException e) {
            throw new ScenarioException("File not found, this should not happen: " + path, e);
        } catch (Exception e) {
            throw new ScenarioException("Issue on parsing file: " + path, e);
        }
    }

    static AbstractRunnerHelper getRunnerHelper(ScenarioSimulationModel.Type type) {
        if (ScenarioSimulationModel.Type.RULE.equals(type)) {
            return new RuleScenarioRunnerHelper();
        } else if (ScenarioSimulationModel.Type.DMN.equals(type)) {
            return new DMNScenarioRunnerHelper();
        } else {
            throw new IllegalArgumentException("Impossible to run simulation of type " + type);
        }
    }

    /**
     * This is required to differentiate tests FAILURES with test ERRORS. In case of a Test failure, an
     * AssertionError should be thrown, in case of a Test Error, an Exception should be thrown.
     */
    static Throwable defineFailureException(Exception e, int index, String scenarioName, String fileName) {
        if (e instanceof ScenarioException scenarioException && scenarioException.isFailedAssertion()) {
            return new IndexedScenarioAssertionError(index, scenarioName, fileName, e);
        } else {
            return new IndexedScenarioException(index, scenarioName, fileName, e);
        }
    }
}
