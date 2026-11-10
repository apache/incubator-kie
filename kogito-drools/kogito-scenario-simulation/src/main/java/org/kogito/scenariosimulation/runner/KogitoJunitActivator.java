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
package org.kogito.scenariosimulation.runner;

import org.drools.scenariosimulation.backend.runner.ScenarioJunitActivator;
import org.drools.scenariosimulation.backend.runner.TestScenarioActivator;
import org.junit.runners.model.InitializationError;

/**
 * @deprecated This is the JUnit 4 implementation of Test Scenario, based on
 *             {@link org.junit.runner.Runner} JUnit 4 API. Replaced by {@link TestScenarioActivator}
 *             Please replace {@code @org.junit.runner.RunWith(ScenarioJunitActivator.class)} with
 *             {@code @TestScenarioActivator}
 */
@Deprecated(since = "10.2", forRemoval = true)
public class KogitoJunitActivator extends ScenarioJunitActivator {

    public KogitoJunitActivator(Class<?> testClass) throws InitializationError {
        super(testClass);
    }
}
