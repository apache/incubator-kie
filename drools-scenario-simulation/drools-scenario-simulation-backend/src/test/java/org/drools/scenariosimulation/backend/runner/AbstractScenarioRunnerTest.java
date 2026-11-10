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

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.drools.scenariosimulation.api.model.Scenario;
import org.drools.scenariosimulation.api.model.ScenarioSimulationModel;
import org.drools.scenariosimulation.api.model.Settings;
import org.drools.scenariosimulation.api.model.Simulation;
import org.drools.scenariosimulation.backend.expression.ExpressionEvaluatorFactory;
import org.drools.scenariosimulation.backend.runner.model.ScenarioRunnerDTO;
import org.drools.scenariosimulation.backend.runner.model.ScenarioRunnerData;
import org.drools.scenariosimulation.backend.util.ScenarioSimulationServerMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.kie.api.runtime.KieContainer;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@Deprecated
class AbstractScenarioRunnerTest {

    private final static int SCENARIO_DATA = 5;

    @Mock
    protected KieContainer kieContainerMock;

    protected AbstractScenarioRunner abstractScenarioRunnerLocal;
    protected Settings settingsLocal;
    private ScenarioRunnerDTO scenarioRunnerDTOLocal;

    @BeforeEach
    void setup() {
        settingsLocal = new Settings();
        scenarioRunnerDTOLocal = getScenarioRunnerDTO();
        abstractScenarioRunnerLocal = spy(
                new AbstractScenarioRunner(kieContainerMock,
                                           scenarioRunnerDTOLocal,
                                           ExpressionEvaluatorFactory.create(
                                                   this.getClass().getClassLoader(),
                                                   ScenarioSimulationModel.Type.RULE)) {
                    @Override
                    protected AbstractRunnerHelper newRunnerHelper() {
                        return null;
                    }
                });
    }

    @Test
    void getDescriptionForSimulationByClassNameAndSimulation() {
        Description retrieved = AbstractScenarioRunner.getDescriptionForSimulation(Optional.empty(), scenarioRunnerDTOLocal.getScenarioWithIndices());
        commonVerifyDescriptionForSimulation(retrieved, AbstractScenarioRunner.class.getSimpleName());
        retrieved = AbstractScenarioRunner.getDescriptionForSimulation(Optional.of("src/test/Test.scesim"), scenarioRunnerDTOLocal.getScenarioWithIndices());
        commonVerifyDescriptionForSimulation(retrieved, "Test");
    }

    @Test
    void getDescriptionForScenario() {
        final Scenario scenario = scenarioRunnerDTOLocal.getScenarioWithIndices().get(2).getScesimData();
        Description retrieved = AbstractScenarioRunner.getDescriptionForScenario(Optional.empty(), 1, scenario.getDescription());
        
        commonVerifyDescriptionForScenario(retrieved, 1, scenario.getDescription(), AbstractScenarioRunner.class.getSimpleName());

        retrieved = AbstractScenarioRunner.getDescriptionForScenario(Optional.of("src/test/Test.scesim"), 1, scenario.getDescription());
       
        commonVerifyDescriptionForScenario(retrieved, 1, scenario.getDescription(), "Test");
    }

    @Test
    void getSpecificRunnerProvider() {
        // all existing types should have a dedicated runner
    	assertThat(ScenarioSimulationModel.Type.values()).extracting(x -> AbstractScenarioRunner.getSpecificRunnerProvider(x)).isNotNull();
    }

    @Test
    void getSpecificRunnerProviderNullType() {
        settingsLocal.setType(null);
        assertThatThrownBy(() -> AbstractScenarioRunner.getSpecificRunnerProvider(null)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
     void testRun() {
        ArgumentCaptor<Failure> failureArgumentCaptor = ArgumentCaptor.forClass(Failure.class);

        doThrow(new ScenarioException("Failed assertion", true))
                .when(abstractScenarioRunnerLocal).internalRunScenario(eq(scenarioRunnerDTOLocal.getScenarioWithIndices().get(0)),
                                                                       isA(ScenarioRunnerData.class),
                                                                       any(), any());
        doThrow(new ScenarioException("Generic exception"))
                .when(abstractScenarioRunnerLocal).internalRunScenario(eq(scenarioRunnerDTOLocal.getScenarioWithIndices().get(1)),
                                                                       isA(ScenarioRunnerData.class),
                                                                       any(), any());
        doThrow(new ScenarioException(new IllegalArgumentException("Wrong argument")))
                .when(abstractScenarioRunnerLocal).internalRunScenario(eq(scenarioRunnerDTOLocal.getScenarioWithIndices().get(2)),
                                                                       isA(ScenarioRunnerData.class),
                                                                       any(), any());
        doThrow(new RuntimeException("Unknown exception"))
                .when(abstractScenarioRunnerLocal).internalRunScenario(eq(scenarioRunnerDTOLocal.getScenarioWithIndices().get(3)),
                                                                       isA(ScenarioRunnerData.class),
                                                                       any(), any());
        assertThat(abstractScenarioRunnerLocal.simulationRunMetadataBuilder).isNull();
        RunNotifier runNotifier = spy(new RunNotifier());

        abstractScenarioRunnerLocal.run(runNotifier);

        assertThat(abstractScenarioRunnerLocal.simulationRunMetadataBuilder).isNotNull();
        verify(runNotifier, times(SCENARIO_DATA + 2)).fireTestStarted(isA(Description.class));
        verify(runNotifier, times(SCENARIO_DATA)).fireTestFailure(failureArgumentCaptor.capture());
        verify(runNotifier, times(SCENARIO_DATA)).fireTestFinished(isA(Description.class));

        List<Failure> capturedFailures = failureArgumentCaptor.getAllValues();
        assertThat(capturedFailures.get(0).getException()).hasMessage(ScenarioSimulationServerMessages.getIndexedScenarioMessage("Failed assertion", 1, "INDEX-0", "test"));
        assertThat(capturedFailures.get(0).getException()).isInstanceOf(IndexedScenarioAssertionError.class);
        assertThat(capturedFailures.get(1).getException()).hasMessage(ScenarioSimulationServerMessages.getIndexedScenarioMessage("Generic exception", 2, "INDEX-1", "test"));
        assertThat(capturedFailures.get(1).getException()).isInstanceOf(IndexedScenarioException.class);
        assertThat(capturedFailures.get(2).getException()).hasMessage(ScenarioSimulationServerMessages.getIndexedScenarioMessage("Wrong argument", 3, "INDEX-2", "test"));
        assertThat(capturedFailures.get(2).getException()).isInstanceOf(IndexedScenarioException.class);
        assertThat(capturedFailures.get(3).getException()).hasMessage(ScenarioSimulationServerMessages.getIndexedScenarioMessage("Unknown exception", 4, "INDEX-3", "test"));
        assertThat(capturedFailures.get(3).getException()).isInstanceOf(IndexedScenarioException.class);
    }

    @Test
    void getScesimFileName() {
        assertThat(AbstractScenarioRunner.getScesimFileName("src/test/Test.scesim")).isEqualTo("Test");
        assertThat(AbstractScenarioRunner.getScesimFileName("Test.scesim")).isEqualTo("Test");
        assertThat(AbstractScenarioRunner.getScesimFileName("Test")).isEqualTo("Test");
        assertThat(AbstractScenarioRunner.getScesimFileName("src/test/Test.1.scesim")).isEqualTo("Test.1");
        assertThat(AbstractScenarioRunner.getScesimFileName(null)).isEqualTo(null);
    }

    private void commonVerifyDescriptionForSimulation(final Description retrieved, final String className) {
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getDisplayName()).isEqualTo(className);
        assertThat(retrieved.getChildren()).hasSize(SCENARIO_DATA);
        assertThat(retrieved.getTestClass()).isNull();
        assertThat(retrieved.getClassName()).isEqualTo(className);
        IntStream.range(0, SCENARIO_DATA).forEach(index -> {
            final Description description = retrieved.getChildren().get(index);
            commonVerifyDescriptionForScenario(description, index + 1, scenarioRunnerDTOLocal.getScenarioWithIndices().get(index).getScesimData().getDescription(), className);
        });
    }

    private void commonVerifyDescriptionForScenario(final Description description, int index, final String scenarioDescription, final String className) {
        String expected = String.format("#%1$d: %2$s(%3$s)", index, scenarioDescription, className);
        assertThat(description.getDisplayName()).isEqualTo(expected);
    }

    private ScenarioRunnerDTO getScenarioRunnerDTO() {

        Simulation simulation = new Simulation();
        IntStream.range(0, SCENARIO_DATA).forEach(index -> {
            Scenario scenario = simulation.addData();
            scenario.setDescription("INDEX-" + index);
        });

        ScenarioSimulationModel model = new ScenarioSimulationModel();
        model.setSimulation(simulation);

        return new ScenarioRunnerDTO(model, "test.scesim");
    }
}