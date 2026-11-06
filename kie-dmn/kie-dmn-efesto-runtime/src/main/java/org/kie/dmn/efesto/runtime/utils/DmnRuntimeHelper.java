/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.dmn.efesto.runtime.utils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.efesto.runtime.model.EfestoOutputDMN;
import org.kie.dmn.efesto.runtime.service.DMNEvaluator;
import org.kie.efesto.common.api.identifiers.ModelLocalUriId;
import org.kie.efesto.common.api.model.GeneratedModelResource;
import org.kie.efesto.common.api.model.GeneratedResources;
import org.kie.efesto.runtimemanager.api.model.EfestoInput;
import org.kie.efesto.runtimemanager.api.model.EfestoLocalRuntimeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.kie.efesto.runtimemanager.api.utils.GeneratedResourceUtils.*;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class DmnRuntimeHelper {

    private static final Logger logger = LoggerFactory.getLogger(DmnRuntimeHelper.class.getName());

    private DmnRuntimeHelper() {
    }

    public static boolean canManage(EfestoInput toEvaluate, EfestoLocalRuntimeContext context) {
        return isPresentExecutableOrModelOrRedirect(toEvaluate.getModelLocalUriId(), context);
    }

    public static Optional<EfestoOutputDMN> execute(EfestoInput<Map<String, Object>> toEvaluate, EfestoLocalRuntimeContext runtimeContext) {
        ModelLocalUriId modelLocalUriId = toEvaluate.getModelLocalUriId();
        Map<String, GeneratedResources> generatedResourcesMap = runtimeContext.getGeneratedResourcesMap();
        Stream<GeneratedModelResource> additionalModelResources = getAllGeneratedModelResources(generatedResourcesMap.get(modelLocalUriId.model())).stream()
                .filter(generatedModelResource -> !modelLocalUriId.equals(generatedModelResource.getModelLocalUriId()));

        Optional<GeneratedModelResource> generatedModelResource = getGeneratedModelResource(modelLocalUriId, generatedResourcesMap);
        return generatedModelResource.map(it -> {
            if (it.getCompiledModel() != null) {
                return execute((DMNModel) it.getCompiledModel(), modelLocalUriId, toEvaluate.getInputData(), additionalModelResources);
            } else {
                return execute(it.getModelSource(), modelLocalUriId, toEvaluate.getInputData(), additionalModelResources);
            }
        });
    }

    static EfestoOutputDMN execute(DMNModel model, ModelLocalUriId modelLocalUriId, Map<String, Object> inputData, Stream<GeneratedModelResource> additionalModelResources) {
        try {
            List<DMNModel> importedModels = additionalModelResources.map(GeneratedModelResource::getCompiledModel)
                    .filter(Objects::nonNull).map(DMNModel.class::cast).toList();
            DMNEvaluator dmnEvaluator = DMNEvaluator.fromDMNModel(model, importedModels);
            DMNResult dmnResult = dmnEvaluator.evaluate(inputData);
            return new EfestoOutputDMN(modelLocalUriId, dmnResult);
        } catch (Exception e) {
            logger.error("Failed to evaluate {}", inputData, e);
            return null;
        }
    }

    static EfestoOutputDMN execute(String modelSource, ModelLocalUriId modelLocalUriId, Map<String, Object> inputData, Stream<GeneratedModelResource> additionalModelResources) {
        try {
            List<String> additionalModelSources = additionalModelResources.map(GeneratedModelResource::getModelSource).toList();
            DMNEvaluator dmnEvaluator = DMNEvaluator.fromXML(modelSource, additionalModelSources);
            DMNResult dmnResult = dmnEvaluator.evaluate(inputData);
            return new EfestoOutputDMN(modelLocalUriId, dmnResult);
        } catch (Exception e) {
            logger.error("Failed to evaluate {}", inputData, e);
            return null;
        }
    }

}
