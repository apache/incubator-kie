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
package org.kie.efesto.compilationmanager.core.utils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.kie.efesto.common.api.identifiers.LocalUri;
import org.kie.efesto.common.api.identifiers.ModelLocalUriId;
import org.kie.efesto.common.api.io.IndexFile;
import org.kie.efesto.common.api.model.GeneratedClassResource;
import org.kie.efesto.common.api.model.GeneratedExecutableResource;
import org.kie.efesto.common.api.model.GeneratedResource;
import org.kie.efesto.common.api.model.GeneratedResources;
import org.kie.efesto.compilationmanager.api.model.EfestoCallableOutputClassesContainer;
import org.kie.efesto.common.api.model.EfestoCompilationContext;
import org.kie.efesto.compilationmanager.api.model.EfestoResource;
import org.kie.efesto.compilationmanager.api.service.KieCompilerService;
import org.kie.efesto.compilationmanager.core.mocks.MockEfestoInputF;
import org.kie.efesto.compilationmanager.core.mocks.MockEfestoRedirectOutputE;
import org.kie.efesto.compilationmanager.core.mocks.MockKieCompilerServiceE;
import org.kie.efesto.compilationmanager.core.mocks.MockKieCompilerServiceF;
import org.kie.efesto.compilationmanager.core.model.EfestoCompilationContextUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.efesto.common.core.utils.JSONUtils.getGeneratedResourcesObject;
import static org.kie.efesto.common.core.utils.JSONUtils.writeGeneratedResourcesObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CompilationManagerUtilsTest {

    private final static String modelType = "test";
    private final static ModelLocalUriId MODEL_LOCAL_URI_ID = new ModelLocalUriId(LocalUri.parse("/" + modelType + "/this/is/fri"));
    private final static ModelLocalUriId NOT_EXISTING_MODEL_LOCAL_URI_ID =  new ModelLocalUriId(LocalUri.parse("/notexisting/this/is/fri"));
    private final static Map<String, byte[]> compiledClassMap = IntStream.range(0, 3).boxed().collect(Collectors.toMap(integer -> "class_" + integer, integer -> new byte[0]));
    private final static EfestoCallableOutputClassesContainer finalOutput = getEfestoFinalOutputClassesContainer(MODEL_LOCAL_URI_ID);

    @Test
    void processResourcesWithoutRedirect() {
        KieCompilerService kieCompilerServiceMock = mock(MockKieCompilerServiceE.class);
        EfestoResource toProcess = new MockEfestoRedirectOutputE();
        EfestoCompilationContext context =
                EfestoCompilationContextUtils.buildWithParentClassLoader(Thread.currentThread().getContextClassLoader());
        CompilationManagerUtils.processResources(kieCompilerServiceMock, toProcess, context);
        verify(kieCompilerServiceMock, times(1)).processResource(toProcess, context);
    }

    @Test
    void processResourcesWithRedirect() {
        KieCompilerService kieCompilerServiceMock = mock(MockKieCompilerServiceF.class);
        EfestoResource toProcess = new MockEfestoInputF();
        EfestoCompilationContext context =
                EfestoCompilationContextUtils.buildWithParentClassLoader(Thread.currentThread().getContextClassLoader());
        CompilationManagerUtils.processResources(kieCompilerServiceMock, toProcess, context);
        verify(kieCompilerServiceMock, times(1)).processResource(toProcess, context);
    }

    @Test
    void getIndexFileExisting() {
        IndexFile retrieved = CompilationManagerUtils.getIndexFile(finalOutput);
        assertThat(retrieved).isNotNull();
        String expectedName = "IndexFile.test_json";
        assertThat(retrieved.getName()).isEqualTo(expectedName);
    }

    @Test
    void getIndexFileNotExisting() {
        EfestoCallableOutputClassesContainer notExistingOutput = getEfestoFinalOutputClassesContainer(NOT_EXISTING_MODEL_LOCAL_URI_ID);
        IndexFile retrieved = CompilationManagerUtils.getIndexFile(notExistingOutput);
        assertThat(retrieved).isNotNull();
        String expectedName = "IndexFile.notexisting_json";
        assertThat(retrieved.getName()).isEqualTo(expectedName);
    }

    @Test
    void populateIndexFile() throws Exception {
        IndexFile toPopulate = CompilationManagerUtils.getIndexFile(finalOutput);
        GeneratedResources originalGeneratedResources = getGeneratedResourcesObject(toPopulate);
        int expectedResources = 2; // 1 final resource + 1 intermediate resources
        assertThat(originalGeneratedResources).hasSize(expectedResources);
        CompilationManagerUtils.populateIndexFile(toPopulate, finalOutput);
        GeneratedResources generatedResources = getGeneratedResourcesObject(toPopulate);
        expectedResources = 6; // 2 final resource + 4 class resources
        assertThat(generatedResources).hasSize(expectedResources);
        List<GeneratedExecutableResource> executableResources = generatedResources.stream().filter(generatedResource -> generatedResource instanceof GeneratedExecutableResource).map(GeneratedExecutableResource.class::cast).collect(Collectors.toList());
        expectedResources = 2; // 2 final resource
        assertThat(executableResources).hasSize(expectedResources);

        GeneratedExecutableResource finalResource = executableResources.stream().filter(generatedExecutableResource -> finalOutput.getModelLocalUriId().equals(generatedExecutableResource.getModelLocalUriId())).findFirst().orElse(null);

        commonEvaluateGeneratedExecutableResource(finalResource);
        List<GeneratedClassResource> classResources = generatedResources.stream().filter(generatedResource -> generatedResource instanceof GeneratedClassResource).map(GeneratedClassResource.class::cast).collect(Collectors.toList());
        expectedResources = 4; // 4 class resources
        assertThat(classResources).hasSize(expectedResources);

        List<GeneratedResource> classResourcesGenerated = classResources.stream().filter(generatedResource -> !generatedResource.getFullClassName().equals("type")).collect(Collectors.toList());
        commonEvaluateGeneratedIntermediateResources(classResourcesGenerated);

        // restore clean situation
        writeGeneratedResourcesObject(originalGeneratedResources, toPopulate);
    }

    @Test
    void populateGeneratedResources() {
        GeneratedResources toPopulate = new GeneratedResources();
        assertThat(toPopulate).isEmpty();
        CompilationManagerUtils.populateGeneratedResources(toPopulate, finalOutput);
        int expectedResources = 4; // 1 final resource + 3 intermediate resources
        assertThat(toPopulate).hasSize(expectedResources);
        GeneratedResource finalResource = toPopulate.stream().filter(generatedResource -> generatedResource instanceof GeneratedExecutableResource).findFirst().orElse(null);
        commonEvaluateGeneratedExecutableResource(finalResource);
        List<GeneratedResource> classResources = toPopulate.stream().filter(generatedResource -> generatedResource instanceof GeneratedClassResource).map(GeneratedClassResource.class::cast).collect(Collectors.toList());
        commonEvaluateGeneratedIntermediateResources(classResources);
    }

    @Test
    void getGeneratedResource() {
        GeneratedResource retrieved = CompilationManagerUtils.getGeneratedResource(finalOutput);
        commonEvaluateGeneratedExecutableResource(retrieved);
    }

    @Test
    void getGeneratedResources() {
        List<GeneratedResource> retrieved = CompilationManagerUtils.getGeneratedResources(finalOutput);
        commonEvaluateGeneratedIntermediateResources(retrieved);
    }

    @Test
    void getGeneratedIntermediateResource() {
        String className = "className";
        GeneratedClassResource retrieved = CompilationManagerUtils.getGeneratedClassResource(className);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getFullClassName()).isEqualTo(className);
    }

    private void commonEvaluateGeneratedExecutableResource(GeneratedResource generatedResource) {
        assertThat(generatedResource).isNotNull();
        assertThat(generatedResource instanceof GeneratedExecutableResource).isTrue();
        assertThat(((GeneratedExecutableResource) generatedResource).getModelLocalUriId()).isEqualTo(finalOutput.getModelLocalUriId());
    }

    private void commonEvaluateGeneratedIntermediateResources(List<GeneratedResource> retrieved) {
        assertThat(retrieved).isNotNull();
        compiledClassMap.keySet().forEach(fullClassName -> assertThat(retrieved.stream().filter(GeneratedClassResource.class::isInstance).map(GeneratedClassResource.class::cast).anyMatch(generatedResource -> generatedResource.getFullClassName().equals(fullClassName))).isTrue());
    }

    private static EfestoCallableOutputClassesContainer getEfestoFinalOutputClassesContainer(ModelLocalUriId modelLocalUriId) {
        return new EfestoCallableOutputClassesContainer(modelLocalUriId, modelLocalUriId.model() + "Resources", compiledClassMap) {
        };
    }
}