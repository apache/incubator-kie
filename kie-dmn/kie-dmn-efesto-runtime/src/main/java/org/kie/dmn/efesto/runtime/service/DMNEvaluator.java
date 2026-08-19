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

package org.kie.dmn.efesto.runtime.service;

import org.kie.api.io.Resource;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.core.internal.utils.DMNRuntimeBuilder;
import org.kie.dmn.core.internal.utils.DynamicDMNContextBuilder;
import org.kie.internal.io.ResourceFactory;

import java.io.StringReader;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class DMNEvaluator {

    private final DMNModel dmnModel;
    private final DMNRuntime dmnRuntime;

    public static DMNEvaluator fromXML(String modelXML, List<String> importedModelSources) {
        List<Resource> modelResources =
                Stream.concat(Stream.of(modelXML), importedModelSources.stream()).map(source -> ResourceFactory.newReaderResource(new StringReader(modelXML), "UTF-8")).toList();

        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(modelResources).getOrElseThrow(RuntimeException::new);
        DMNModel dmnModel = dmnRuntime.getModels().get(0);
        return new DMNEvaluator(dmnModel, dmnRuntime);
    }

    public static DMNEvaluator fromDMNModel(DMNModel dmnModel, Collection<DMNModel> additionalModels) {

        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .withModels(additionalModels);

        return new DMNEvaluator(dmnModel, dmnRuntime);
    }

    private DMNEvaluator(DMNModel dmnModel, DMNRuntime dmnRuntime) {
        this.dmnModel = dmnModel;
        this.dmnRuntime = dmnRuntime;
    }

    public DMNModel getDmnModel() {
        return dmnModel;
    }

    public String getNamespace() {
        return dmnModel.getNamespace();
    }

    public String getName() {
        return dmnModel.getName();
    }

    public Collection<DMNModel> getAllDMNModels() {
        return dmnRuntime.getModels();
    }

    public DMNResult evaluate(Map<String, Object> context) {
        DMNContext dmnContext = new DynamicDMNContextBuilder(dmnRuntime.newContext(), dmnModel).populateContextWith(context);
        return dmnRuntime.evaluateAll(dmnModel, dmnContext);
    }

}
