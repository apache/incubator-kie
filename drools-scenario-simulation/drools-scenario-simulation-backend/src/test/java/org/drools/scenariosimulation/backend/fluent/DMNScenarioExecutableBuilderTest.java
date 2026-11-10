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
package org.drools.scenariosimulation.backend.fluent;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.drools.scenariosimulation.backend.util.DMNSimulationUtils;
import org.drools.util.ResourceHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.identifiers.DmnIdFactory;
import org.kie.dmn.api.identifiers.KieDmnComponentRoot;
import org.kie.efesto.common.api.identifiers.EfestoAppRoot;
import org.kie.efesto.common.api.identifiers.ModelLocalUriId;
import org.kie.efesto.common.api.model.GeneratedModelResource;
import org.kie.efesto.common.api.model.GeneratedResources;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("rawtypes")
class DMNScenarioExecutableBuilderTest {

    private static final Logger LOG = LoggerFactory.getLogger(DMNScenarioExecutableBuilderTest.class);

    @Test
    void testCreateBuilderNoException() {
        Map<String, GeneratedResources> generatedResourcesMap = new HashMap<>();
        Collection<File> dmnFiles = new ArrayList<>();
        Collection<File> pmmlFiles = new ArrayList<>();
        try (MockedStatic<DMNSimulationUtils> mockDMNSimulationUtils = Mockito.mockStatic(DMNSimulationUtils.class);
             MockedStatic<ResourceHelper> mockResourceHelper = Mockito.mockStatic(ResourceHelper.class)) {
            mockResourceHelper.when(() -> ResourceHelper.getFileResourcesByExtension("dmn")).thenAnswer(invocation -> {
                LOG.debug("Mocked static Collection<File> getFileResourcesByExtension dmn!");
                return dmnFiles;
            });
            mockResourceHelper.when(() -> ResourceHelper.getFileResourcesByExtension("pmml")).thenAnswer(invocation -> {
                LOG.debug("Mocked static Collection<File> getFileResourcesByExtension pmml!");
                return pmmlFiles;
            });
            mockDMNSimulationUtils.when(() -> DMNSimulationUtils.compileModels(dmnFiles)).thenAnswer(invocation -> {
                LOG.debug("Mocked static Map compileModels!");
                return generatedResourcesMap;
            });
            DMNScenarioExecutableBuilder builder = DMNScenarioExecutableBuilder.createBuilder();
            mockResourceHelper.verify(() -> ResourceHelper.getFileResourcesByExtension("dmn"), times(1));
            mockDMNSimulationUtils.verify(() -> DMNSimulationUtils.compileModels(dmnFiles), times(1));
            assertThat(builder).isNotNull();
            assertThat(builder.generatedResourcesMap).isEqualTo(generatedResourcesMap);
        }
    }

    @Test
    void testCreateBuilderException() {
        Collection<File> dmnFiles = new ArrayList<>();
        Collection<File> pmmlFiles = new ArrayList<>();
        try (MockedStatic<DMNSimulationUtils> mockDMNSimulationUtils = Mockito.mockStatic(DMNSimulationUtils.class);
             MockedStatic<ResourceHelper> mockResourceHelper = Mockito.mockStatic(ResourceHelper.class)) {
                mockResourceHelper.when(() -> ResourceHelper.getFileResourcesByExtension("dmn")).thenAnswer(invocation -> {
                LOG.debug("Mocked static Collection<File> getFileResourcesByExtension dmn!");
                return dmnFiles;
            });
                mockResourceHelper.when(() -> ResourceHelper.getFileResourcesByExtension("pmml")).thenAnswer(invocation -> {
                LOG.debug("Mocked static Collection<File> getFileResourcesByExtension pmml!");
                return pmmlFiles;
            });
            mockDMNSimulationUtils.when(() -> DMNSimulationUtils.compileModels(dmnFiles)).thenThrow(RuntimeException.class);
            assertThrows(IllegalStateException.class, DMNScenarioExecutableBuilder::createBuilder);
            mockResourceHelper.verify(() -> ResourceHelper.getFileResourcesByExtension("dmn"), times(1));
            mockDMNSimulationUtils.verify(() -> DMNSimulationUtils.compileModels(dmnFiles), times(1));
        }
    }

    @Test
    void testSetActiveModelNoException() {
        String fileName = "/this/is/filename";
        String modelName = "modelName";
        ModelLocalUriId modelLocalUriId = new EfestoAppRoot()
                .get(KieDmnComponentRoot.class)
                .get(DmnIdFactory.class)
                .get(fileName, modelName);
        GeneratedModelResource mockGeneratedModelResource = mock(GeneratedModelResource.class);
        DMNModel mockDMNModel = mock(DMNModel.class);
        when(mockGeneratedModelResource.getModelLocalUriId()).thenReturn(modelLocalUriId);
        when(mockGeneratedModelResource.getCompiledModel()).thenReturn(mockDMNModel);

        GeneratedResources generatedResources = new GeneratedResources();
        generatedResources.add(mockGeneratedModelResource);
        Map<String, GeneratedResources> generatedResourcesMap = new HashMap<>();
        generatedResourcesMap.put("dmn", generatedResources);
        try (MockedStatic<DMNSimulationUtils> mockDMNSimulationUtils = Mockito.mockStatic(DMNSimulationUtils.class)) {
            mockDMNSimulationUtils.when(() -> DMNSimulationUtils.compileModels(Mockito.any())).thenAnswer(invocation -> {
                LOG.debug("Mocked static Map compileModels!");
                return generatedResourcesMap;
            });
            DMNScenarioExecutableBuilder builder = DMNScenarioExecutableBuilder.createBuilder();
            assertThat(builder).isNotNull();
            assertThat(builder.generatedResourcesMap).isEqualTo(generatedResourcesMap);
            builder.setActiveModel(fileName, modelName);
            assertThat(builder.dmnModelLocalUriId).isEqualTo(modelLocalUriId);
            assertThat(builder.dmnModel).isEqualTo(mockDMNModel);
        }
    }

    @Test
    void testSetActiveModelException() {
        String fileName = "/this/is/filename";
        String modelName = "modelName";
        ModelLocalUriId modelLocalUriId = new EfestoAppRoot()
                .get(KieDmnComponentRoot.class)
                .get(DmnIdFactory.class)
                .get(fileName, modelName);
        GeneratedModelResource mockGeneratedModelResource = mock(GeneratedModelResource.class);
        when(mockGeneratedModelResource.getModelLocalUriId()).thenReturn(modelLocalUriId);

        GeneratedResources generatedResources = new GeneratedResources();
        generatedResources.add(mockGeneratedModelResource);
        Map<String, GeneratedResources> generatedResourcesMap = new HashMap<>();
        generatedResourcesMap.put("dmn", generatedResources);
        try (MockedStatic<DMNSimulationUtils> mockDMNSimulationUtils = Mockito.mockStatic(DMNSimulationUtils.class)) {
            mockDMNSimulationUtils.when(() -> DMNSimulationUtils.compileModels(Mockito.any())).thenAnswer(invocation -> {
                LOG.debug("Mocked static Map compileModels!");
                return generatedResourcesMap;
            });
            DMNScenarioExecutableBuilder builder = DMNScenarioExecutableBuilder.createBuilder();
            assertThat(builder).isNotNull();
            assertThat(builder.generatedResourcesMap).isEqualTo(generatedResourcesMap);
            assertThrows(IllegalStateException.class, () -> builder.setActiveModel("not-filename", "not-model-name"));
            assertThat(builder.dmnModelLocalUriId).isNull();
            assertThat(builder.dmnModel).isNull();
        }
    }
}