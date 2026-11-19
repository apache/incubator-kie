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
package org.kie.kogito.codegen.decision;

import java.nio.file.Paths;
import java.util.Collection;

import org.drools.codegen.common.GeneratedFile;
import org.junit.jupiter.api.Test;
import org.kie.kogito.codegen.api.AddonsConfig;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.QuarkusKogitoBuildContext;
import org.kie.kogito.codegen.api.io.CollectedResource;
import org.kie.kogito.codegen.core.io.CollectedResourceProducer;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionCloudEventMetaFactoryGeneratorTest {

    private static final String MODEL_PATH = "src/test/resources/decision/models/vacationDays";
    private static final int EXPECTED_FILES_WITH_CLOUDEVENTS = 7; // Now it includes IndexFile.dmn_json and dmnModelPaths.txt
    private static final int EXPECTED_FILES_WITHOUT_CLOUDEVENTS = 6; // Now it includes IndexFile.dmn_json and dmnModelPaths.txt
    private static final String EXPECTED_FILE_PATH = "org/kie/kogito/app/DecisionCloudEventMetaFactory.java";

    @Test
    void testGenerateWithCloudEventsEnabled() {
        DecisionCodegen codeGenerator = buildCodegen(true);
        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();

        assertThat(generatedFiles.size()).isEqualTo(EXPECTED_FILES_WITH_CLOUDEVENTS);
        assertThat(generatedFiles.stream().anyMatch(gf -> EXPECTED_FILE_PATH.equals(gf.relativePath()))).isTrue();
    }

    @Test
    void testGenerateWithCloudEventsDisabled() {
        DecisionCodegen codeGenerator = buildCodegen(false);
        Collection<GeneratedFile> generatedFiles = codeGenerator.generate();

        assertThat(generatedFiles.size()).isEqualTo(EXPECTED_FILES_WITHOUT_CLOUDEVENTS);
        assertThat(generatedFiles.stream().anyMatch(gf -> EXPECTED_FILE_PATH.equals(gf.relativePath()))).isFalse();
    }

    private static DecisionCodegen buildCodegen(boolean withCloudEvents) {
        KogitoBuildContext context = QuarkusKogitoBuildContext.builder()
                .withAddonsConfig(AddonsConfig.builder().withCloudEvents(withCloudEvents).build())
                .build();

        Collection<CollectedResource> collectedResources = CollectedResourceProducer
                .fromPaths(Paths.get(MODEL_PATH).toAbsolutePath());

        return DecisionCodegen.ofCollectedResources(context, collectedResources);
    }

}
