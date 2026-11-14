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
package org.kie.kogito.codegen.prediction.config;

import org.drools.codegen.common.GeneratedFile;
import org.junit.jupiter.api.Test;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.QuarkusKogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.SpringBootKogitoBuildContext;

import static org.assertj.core.api.Assertions.assertThat;

class PredictionConfigGeneratorTest {

    private final static String PACKAGE_NAME = "PACKAGENAME";

    @Test
    void compilationUnitWithCDI() {
        KogitoBuildContext context = QuarkusKogitoBuildContext.builder().withPackageName(PACKAGE_NAME).build();
        PredictionConfigGenerator predictionConfigGenerator = new PredictionConfigGenerator(context);
        GeneratedFile retrieved = predictionConfigGenerator.generate();
        assertThat(retrieved).isNotNull();
        String retrievedContent = new String(retrieved.contents());
        String expected = "@jakarta.inject.Singleton";
        assertThat(retrievedContent.contains(expected)).isTrue();
        expected = "@jakarta.inject.Inject";
        assertThat(retrievedContent.contains(expected)).isTrue();
        String unexpected = "@org.springframework.stereotype.Component";
        assertThat(retrievedContent.contains(unexpected)).isFalse();
        unexpected = "@org.springframework.beans.factory.annotation.Autowired";
        assertThat(retrievedContent.contains(unexpected)).isFalse();
    }

    @Test
    void compilationUnitWithSpring() {
        KogitoBuildContext context = SpringBootKogitoBuildContext.builder().withPackageName(PACKAGE_NAME).build();
        PredictionConfigGenerator predictionConfigGenerator = new PredictionConfigGenerator(context);
        GeneratedFile retrieved = predictionConfigGenerator.generate();
        assertThat(retrieved).isNotNull();
        String retrievedContent = new String(retrieved.contents());
        String expected = "@org.springframework.stereotype.Component";
        assertThat(retrievedContent.contains(expected)).isTrue();
        expected = "@org.springframework.beans.factory.annotation.Autowired";
        assertThat(retrievedContent.contains(expected)).isTrue();
        String unexpected = "@jakarta.inject.Singleton";
        assertThat(retrievedContent.contains(unexpected)).isFalse();
        unexpected = "@jakarta.inject.Inject";
        assertThat(retrievedContent.contains(unexpected)).isFalse();
    }
}