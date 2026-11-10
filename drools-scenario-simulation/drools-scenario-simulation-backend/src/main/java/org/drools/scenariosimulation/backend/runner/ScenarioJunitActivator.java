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
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.drools.scenariosimulation.api.model.ScenarioSimulationModel;
import org.drools.scenariosimulation.api.model.Settings;
import org.drools.scenariosimulation.backend.runner.model.ScenarioRunnerDTO;
import org.drools.scenariosimulation.backend.util.ScenarioSimulationXMLPersistence;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;

import static org.drools.scenariosimulation.api.utils.ScenarioSimulationSharedUtils.FILE_EXTENSION;
import static org.drools.util.ResourceHelper.getResourcesByExtension;

/**
 * Programmatic facade that discovers {@code *.scesim} files and executes them via
 * {@link AbstractScenarioRunner}. For Surefire/IDE discovery prefer annotating a test
 * class with {@link TestScenarioActivator} so {@link TestScenarioEngine} runs them.
 */
public class ScenarioJunitActivator {

    public List<ScenarioRunnerDTO> getChildren() {
        return getResources().map(this::parseFile)
                .filter(item -> isNotSkipFromBuild(item.getSettings()))
                .collect(Collectors.toList());
    }

    public void runChild(ScenarioRunnerDTO child, ScenarioExecutionListener listener) {
        KieContainer kieClasspathContainer = getKieContainer();
        AbstractScenarioRunner scenarioRunner = newRunner(kieClasspathContainer, child);
        scenarioRunner.run(listener);
    }

    public void runAll(ScenarioExecutionListener listener) {
        for (ScenarioRunnerDTO child : getChildren()) {
            runChild(child, listener);
        }
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
