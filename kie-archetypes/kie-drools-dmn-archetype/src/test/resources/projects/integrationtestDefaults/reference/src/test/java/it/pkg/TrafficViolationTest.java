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

package it.pkg;

import static org.assertj.core.api.Assertions.assertThat;
import static utils.shim.Map.of;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieRuntimeFactory;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNDecisionResult.DecisionEvaluationStatus;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrafficViolationTest {
    static final Logger LOG = LoggerFactory.getLogger(TrafficViolationTest.class);

    private DMNRuntime dmnRuntime;
    private DMNModel dmnModelUT;
    
    @BeforeEach
    void init() {
        KieServices kieServices = KieServices.Factory.get();
        KieContainer kieContainer = kieServices.getKieClasspathContainer();
        dmnRuntime = KieRuntimeFactory.of(kieContainer.getKieBase()).get(DMNRuntime.class);

        final String namespace = "https://github.com/kiegroup/drools/kie-dmn/_A4BCA8B8-CF08-433F-93B2-A2598F19ECFF";
        final String modelName = "Traffic Violation";
        dmnModelUT = dmnRuntime.getModel(namespace, modelName);
    }
    
    @Test
    void test() {
        Map<String, ?> driver = of("Points", 2);
        Map<String, ?> violation = of("Type", "speed", "Actual Speed", 120, "Speed Limit", 100);
        
        DMNContext dmnContext = dmnRuntime.newContext();
        dmnContext.set("Driver", driver);
        dmnContext.set("Violation", violation);

        LOG.info("Evaluating DMN model");
        DMNResult dmnResult = dmnRuntime.evaluateAll(dmnModelUT, dmnContext);

        LOG.info("Checking results: {}", dmnResult);
        assertThat(dmnResult.hasErrors()).isFalse();

        assertThat(dmnResult.getDecisionResultByName("Should the driver be suspended?").getEvaluationStatus()).isEqualTo(DecisionEvaluationStatus.SUCCEEDED);
        assertThat(dmnResult.getDecisionResultByName("Should the driver be suspended?").getResult()).isEqualTo("No");
    }
}