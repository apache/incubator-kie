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
package org.kie.kogito.codegen.process.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.QuarkusKogitoBuildContext;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkItemRestConfigTest {

    private KogitoBuildContext context;

    @BeforeEach
    public void setup() {
        this.context = QuarkusKogitoBuildContext.builder().build();
    }

    @Test
    void disabledByDefault() {
        WorkItemRestConfig config = WorkItemRestConfig.fromContext(context);

        assertThat(config.isRestEnabled("Rest")).isFalse();
        assertThat(config.configuredNames()).isEmpty();
    }

    @Test
    void enabledWithUnquotedName() {
        context.setApplicationProperty("kogito.codegen.process.workitems.Rest.rest.enabled", "true");

        WorkItemRestConfig config = WorkItemRestConfig.fromContext(context);

        assertThat(config.isRestEnabled("Rest")).isTrue();
        assertThat(config.isRestEnabled("Email")).isFalse();
    }

    @Test
    void enabledWithQuotedNameContainingSpacesAndDots() {
        context.setApplicationProperty("kogito.codegen.process.workitems.\"Service Task\".rest.enabled", "true");
        context.setApplicationProperty("kogito.codegen.process.workitems.\"my.custom.work\".rest.enabled", "true");

        WorkItemRestConfig config = WorkItemRestConfig.fromContext(context);

        assertThat(config.isRestEnabled("Service Task")).isTrue();
        assertThat(config.isRestEnabled("my.custom.work")).isTrue();
    }

    @Test
    void explicitlyDisabled() {
        context.setApplicationProperty("kogito.codegen.process.workitems.Rest.rest.enabled", "false");

        WorkItemRestConfig config = WorkItemRestConfig.fromContext(context);

        assertThat(config.isRestEnabled("Rest")).isFalse();
        assertThat(config.configuredNames()).containsExactly("Rest");
    }

    @Test
    void nameIsCaseSensitive() {
        context.setApplicationProperty("kogito.codegen.process.workitems.Rest.rest.enabled", "true");

        WorkItemRestConfig config = WorkItemRestConfig.fromContext(context);

        assertThat(config.isRestEnabled("rest")).isFalse();
        assertThat(config.isRestEnabled("REST")).isFalse();
    }

    @Test
    void noGlobalSwitch() {
        context.setApplicationProperty("kogito.codegen.process.workitems.rest.enabled", "true");
        context.setApplicationProperty("kogito.codegen.process.rest.enabled", "true");

        WorkItemRestConfig config = WorkItemRestConfig.fromContext(context);

        assertThat(config.configuredNames()).isEmpty();
        assertThat(config.isRestEnabled("Rest")).isFalse();
    }

    @Test
    void unrelatedPropertiesAreIgnored() {
        context.setApplicationProperty("kogito.codegen.process.workitems.Rest.other", "true");
        context.setApplicationProperty("kogito.codegen.rest.enabled", "true");

        WorkItemRestConfig config = WorkItemRestConfig.fromContext(context);

        assertThat(config.configuredNames()).isEmpty();
    }

    @Test
    void nullWorkNameReturnsFalse() {
        WorkItemRestConfig config = WorkItemRestConfig.fromContext(context);

        assertThat(config.isRestEnabled(null)).isFalse();
    }

    @Test
    void humanTaskEnabledByDefault() {
        WorkItemRestConfig config = WorkItemRestConfig.fromContext(context);

        assertThat(config.isRestEnabled("Human Task")).isTrue();
    }

    @Test
    void humanTaskCanBeExplicitlyDisabled() {
        context.setApplicationProperty("kogito.codegen.process.workitems.\"Human Task\".rest.enabled", "false");

        WorkItemRestConfig config = WorkItemRestConfig.fromContext(context);

        assertThat(config.isRestEnabled("Human Task")).isFalse();
    }

    @Test
    void mixedConfiguration() {
        context.setApplicationProperty("kogito.codegen.process.workitems.\"Human Task\".rest.enabled", "false");
        context.setApplicationProperty("kogito.codegen.process.workitems.Rest.rest.enabled", "true");
        context.setApplicationProperty("kogito.codegen.process.workitems.Email.rest.enabled", "false");

        WorkItemRestConfig config = WorkItemRestConfig.fromContext(context);

        assertThat(config.isRestEnabled("Human Task")).isFalse();
        assertThat(config.isRestEnabled("Rest")).isTrue();
        assertThat(config.isRestEnabled("Email")).isFalse();
        assertThat(config.isRestEnabled("Decision Task")).isFalse();
        assertThat(config.configuredNames()).containsExactlyInAnyOrder("Human Task", "Rest", "Email");
    }
}
