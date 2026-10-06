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
package org.kie.flyway.quarkus;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class KieFlywayQuarkusRuntimeConfigTest {

    private static SmallRyeConfig buildConfig(String... keyValuePairs) {
        Map<String, String> props = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            props.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return new SmallRyeConfigBuilder()
                .withMapping(KieFlywayQuarkusRuntimeConfig.class, "kie.flyway")
                .withSources(new PropertiesConfigSource(props, "test"))
                .build();
    }

    @Test
    void globalEnabledDefaultsToFalse() {
        KieFlywayQuarkusRuntimeConfig runtimeConfig = buildConfig()
                .getConfigMapping(KieFlywayQuarkusRuntimeConfig.class);

        assertThat(runtimeConfig.enabled()).isFalse();
    }

    @Test
    void globalEnabledCanBeSetToTrue() {
        KieFlywayQuarkusRuntimeConfig runtimeConfig = buildConfig("kie.flyway.enabled", "true")
                .getConfigMapping(KieFlywayQuarkusRuntimeConfig.class);

        assertThat(runtimeConfig.enabled()).isTrue();
    }

    @Test
    void moduleEnabledTrue() {
        KieFlywayQuarkusRuntimeConfig runtimeConfig = buildConfig("kie.flyway.modules.\"test-module\".enabled", "true")
                .getConfigMapping(KieFlywayQuarkusRuntimeConfig.class);

        assertThat(runtimeConfig.modules().get("test-module").enabled()).isTrue();
    }

    @Test
    void moduleEnabledFalse() {
        KieFlywayQuarkusRuntimeConfig runtimeConfig = buildConfig("kie.flyway.modules.\"test-module\".enabled", "false")
                .getConfigMapping(KieFlywayQuarkusRuntimeConfig.class);

        assertThat(runtimeConfig.modules().get("test-module").enabled()).isFalse();
    }
}
