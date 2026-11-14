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
import java.util.Properties;
import java.util.function.Consumer;

import org.drools.codegen.common.GeneratedFile;
import org.junit.jupiter.api.Test;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.JavaKogitoBuildContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DecisionValidationTest {

    private DecisionCodegen codeGenerator(String path, Consumer<Properties> codeGenContextProperties) throws Exception {
        Properties props = new Properties();
        codeGenContextProperties.accept(props);

        KogitoBuildContext context = JavaKogitoBuildContext.builder()
                .withApplicationProperties(props)
                .build();

        return DecisionCodegen.ofPath(context, Paths.get(path).toAbsolutePath());
    }

    @Test
    public void testDefault() throws Exception {
        DecisionCodegen codeGenerator = codeGenerator("src/test/resources/decision-validation-duplicateName",
                p -> {
                });
        assertThatThrownBy(codeGenerator::generate)
                .as("Expected Validation would have failed for defaults.")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void testIgnore() throws Exception {
        DecisionCodegen codeGenerator = codeGenerator("src/test/resources/decision-validation-duplicateName",
                p -> p.setProperty(DecisionCodegen.VALIDATION_CONFIGURATION_KEY, "IGNORE"));
        Collection<GeneratedFile> files = codeGenerator.generate();
        assertThat(files).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    public void testDisabled() throws Exception {
        DecisionCodegen codeGenerator = codeGenerator("src/test/resources/decision-validation-duplicateName",
                p -> p.setProperty(DecisionCodegen.VALIDATION_CONFIGURATION_KEY, "DISABLED"));
        Collection<GeneratedFile> files = codeGenerator.generate();
        assertThat(files).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    public void testDTAnalysisDefault() throws Exception {
        DecisionCodegen codeGenerator = codeGenerator("src/test/resources/decision-validation-DTsemanticError",
                p -> {
                });
        assertThatThrownBy(codeGenerator::generate)
                .as("Expected Validation would have failed for defaults.")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void testDTAnalysisIgnore() throws Exception {
        DecisionCodegen codeGenerator = codeGenerator("src/test/resources/decision-validation-DTsemanticError",
                p -> p.setProperty(DecisionCodegen.VALIDATION_CONFIGURATION_KEY, "IGNORE"));
        Collection<GeneratedFile> files = codeGenerator.generate();
        assertThat(files).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    public void testDTAnalysisDisabled() throws Exception {
        DecisionCodegen codeGenerator = codeGenerator("src/test/resources/decision-validation-DTsemanticError",
                p -> p.setProperty(DecisionCodegen.VALIDATION_CONFIGURATION_KEY, "DISABLED"));
        Collection<GeneratedFile> files = codeGenerator.generate();
        assertThat(files).hasSizeGreaterThanOrEqualTo(1);
    }
}
