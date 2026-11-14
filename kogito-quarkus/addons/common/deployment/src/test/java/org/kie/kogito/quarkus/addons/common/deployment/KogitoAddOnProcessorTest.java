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
package org.kie.kogito.quarkus.addons.common.deployment;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.quarkus.deployment.Capabilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class KogitoAddOnProcessorTest {

    @Test
    void verifyRequiredCapabilitiesWhenNotPresent() {
        final RequireEngineAddonProcessor requireEngineAddonProcessor = new RequireEngineAddonProcessor();
        final Capabilities capabilities = new Capabilities(Collections.emptySet());
        assertThatThrownBy(() -> requireEngineAddonProcessor.verifyCapabilities(capabilities))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(KogitoCapability.DECISIONS.getCapability())
                .hasMessageContaining(KogitoCapability.PREDICTIONS.getCapability());
    }

    @Test
    void verifyAnyEngineRequiredButNotPresent() {
        final AnyEngineAddonProcessorImpl anyEngineAddonProcessorImpl = new AnyEngineAddonProcessorImpl();
        assertThatThrownBy(() -> anyEngineAddonProcessorImpl.verifyCapabilities(new Capabilities(Collections.emptySet())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(KogitoCapability.DECISIONS.getCapability())
                .hasMessageContaining(KogitoCapability.PREDICTIONS.getCapability())
                .hasMessageContaining(KogitoCapability.PROCESSES.getCapability())
                .hasMessageContaining(KogitoCapability.RULES.getCapability());
    }

    @Test
    void verifyRequiredCapabilitiesWhenPresent() {
        final RequireEngineAddonProcessor requireEngineAddonProcessor = new RequireEngineAddonProcessor();
        final Set<String> capabilities = new HashSet<>();
        capabilities.add(KogitoCapability.DECISIONS.getCapability());
        capabilities.add(KogitoCapability.PREDICTIONS.getCapability());
        assertThatCode(() -> requireEngineAddonProcessor.verifyCapabilities(new Capabilities(capabilities))).doesNotThrowAnyException();
    }

    @Test
    void verifyOneRequiredCapabilitiesWhenPresent() {
        final RequireOneEngineAddonProcessor requireEngineAddonProcessor = new RequireOneEngineAddonProcessor();
        final Set<String> capabilities = new HashSet<>();
        capabilities.add(KogitoCapability.PROCESSES.getCapability());
        assertThatCode(() -> requireEngineAddonProcessor.verifyCapabilities(new Capabilities(capabilities))).doesNotThrowAnyException();
    }

}
