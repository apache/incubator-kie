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

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.drools.scenariosimulation.api.model.ScenarioSimulationModel;
import org.drools.scenariosimulation.api.model.Settings;
import org.drools.scenariosimulation.backend.runner.model.ScenarioRunnerDTO;
import org.drools.scenariosimulation.backend.util.ScenarioSimulationXMLPersistence;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;

import static org.drools.scenariosimulation.api.utils.ScenarioSimulationSharedUtils.FILE_EXTENSION;
import static org.drools.util.ResourceHelper.getResourcesByExtension;

/**
 * @deprecated This is the JUnit 4 implementation of Test Scenario, based on
 * {@link org.junit.runner.Runner} JUnit 4 API. Replaced by {@link TestScenarioActivator}
 * Please replace {@code @org.junit.runner.RunWith(ScenarioJunitActivator.class)} with
 * {@code @TestScenarioActivator}
 */
@Deprecated(since = "10.2.0", forRemoval = true)
public class ScenarioJunitActivator extends ParentRunner<ScenarioRunnerDTO> {

    public ScenarioJunitActivator(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    protected List<ScenarioRunnerDTO> getChildren() {
        return getResources().map(this::parseFile)
                .filter(item -> isNotSkipFromBuild(item.getSettings()))
                .collect(Collectors.toList());
    }

    @Override
    protected Description describeChild(ScenarioRunnerDTO child) {
        return AbstractScenarioRunner.getDescriptionForSimulation(Optional.of(child.getFileName()), child.getScenarioWithIndices());
    }

    @Override
    protected void runChild(ScenarioRunnerDTO child, RunNotifier notifier) {
        KieContainer kieClasspathContainer = getKieContainer();
        AbstractScenarioRunner scenarioRunner = newRunner(kieClasspathContainer, child);
        scenarioRunner.run(notifier);
    }

    protected ScenarioRunnerDTO parseFile(String path) {
        try (final Scanner scanner = new Scanner(new File(path))) {
            String rawFile = scanner.useDelimiter("\\Z").next();
            ScenarioSimulationModel scenarioSimulationModel = getXmlReader().unmarshal(rawFile);
            return new ScenarioRunnerDTO(scenarioSimulationModel, path);
        } catch (FileNotFoundException e) {
            throw new ScenarioException("File not found, this should not happen: " + path, e);
        } catch (Exception e) {
            throw new ScenarioException("Issue on parsing file: " + path, e);
        }
    }

    protected boolean isNotSkipFromBuild(Settings settings) {
        return !settings.isSkipFromBuild();
    }

    ScenarioSimulationXMLPersistence getXmlReader() {
        return ScenarioSimulationXMLPersistence.getInstance();
    }

    Stream<String> getResources() {
        return getResourcesByExtension(FILE_EXTENSION).stream();
    }

    KieContainer getKieContainer() {
        return KieServices.get().getKieClasspathContainer();
    }

    AbstractScenarioRunner newRunner(KieContainer kieContainer, ScenarioRunnerDTO scenarioRunnerDTO) {
        return AbstractScenarioRunner.getSpecificRunnerProvider(scenarioRunnerDTO.getSettings().getType())
                .create(kieContainer, scenarioRunnerDTO);
    }
}
