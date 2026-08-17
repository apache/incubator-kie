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
package org.kie.kogito.pmml;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.drools.compiler.builder.impl.errors.ErrorHandler;
import org.kie.api.pmml.PMML4Result;
import org.kie.kogito.Application;

public abstract class AbstractPMMLRestResource {

    protected Object result(Application application, String fileName, String modelName, Map<String, Object> variables) {
        org.kie.kogito.prediction.PredictionModel prediction = application.get(org.kie.kogito.prediction.PredictionModels.class).getPredictionModel(fileName, modelName);
        org.kie.api.pmml.PMML4Result pmml4Result = prediction.evaluateAll(prediction.newContext(variables));
        return java.util.Collections.singletonMap(pmml4Result.getResultObjectName(), pmml4Result.getResultVariables().get(pmml4Result.getResultObjectName()));
    }

    protected PMML4Result descriptive(Application application, String fileName, String modelName, Map<String, Object> variables) {
        org.kie.kogito.prediction.PredictionModel prediction = application.get(org.kie.kogito.prediction.PredictionModels.class).getPredictionModel(fileName, modelName);
        return prediction.evaluateAll(prediction.newContext(variables));
    }

    public static String getJsonErrorMessage(Exception e) {
        String rawMessage = e.getMessage() != null ? e.getMessage() : "";
        String sanitizedMessage = rawMessage.replaceAll("\\R+", "_");
        String errorMessage = String.format("%s: %s",
                e.getClass().getName(), sanitizedMessage);

        // Build a sanitized exception with no cause chain and no reference to the
        // original object. The stack trace frames (class/method/file/line metadata
        // only — no message text) are copied for debugging. The original exception e
        // is never passed to the log sink in any form.
        Exception sanitized = new Exception(errorMessage);
        sanitized.setStackTrace(e.getStackTrace());
        Logger.getLogger(ErrorHandler.class.getName()).log(Level.SEVERE, errorMessage, sanitized);

        return String.format("{\"exception\": \"%s\"}", e.getClass().getSimpleName());
    }
}
