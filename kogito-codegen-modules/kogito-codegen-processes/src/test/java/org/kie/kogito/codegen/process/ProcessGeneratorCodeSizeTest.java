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
package org.kie.kogito.codegen.process;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import org.drools.codegen.common.GeneratedFile;
import org.junit.jupiter.api.Test;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.JavaKogitoBuildContext;
import org.kie.kogito.codegen.core.io.CollectedResourceProducer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class ProcessGeneratorCodeSizeTest {

    private static final Path BASE_PATH = Paths.get("src/test/resources/").toAbsolutePath();

    @Test
    void largeBpmnCompilesSuccessfully() {
        Path bpmnFile = BASE_PATH.resolve("codetoolarge/repro-fails.bpmn");

        KogitoBuildContext context = JavaKogitoBuildContext.builder()
                .withApplicationProperties(bpmnFile.getParent().toFile())
                .build();

        ProcessCodegen codegen = ProcessCodegen.ofCollectedResources(
                context,
                CollectedResourceProducer.fromFiles(BASE_PATH, bpmnFile.toFile()));

        assertThatCode(() -> {
            Collection<GeneratedFile> generatedFiles = codegen.generate();
            assertThat(generatedFiles).isNotEmpty();
        }).as("Code generation of a large BPMN with so many nodes must not throw a 'code too large' error")
                .doesNotThrowAnyException();
    }

    @Test
    void processMetaDataCarriesHelperMethodsForEachNodeAndConnection() {
        List<ProcessExecutableModelGenerator> generators =
                ProcessGenerationUtils.execModelFromProcessFile("/codetoolarge/repro-fails.bpmn");

        assertThat(generators).hasSize(1);

        org.jbpm.compiler.canonical.ProcessMetaData metadata = generators.get(0).generate();

        assertThat(metadata.getProcessHelperMethods())
                .as("Each node must have its own helper method, plus one initConnections helper")
                .hasSizeGreaterThan(750);

        // Every helper must be private void with a RuleFlowProcessFactory parameter.
        metadata.getProcessHelperMethods().forEach(m -> {
            assertThat(m.getNameAsString())
                    .matches("initNode_.*|initConnections");
            assertThat(m.isPrivate()).isTrue();
            assertThat(m.isStatic()).isFalse();
            assertThat(m.getParameters()).hasSize(1);
        });

        // Exactly one initConnections helper
        long connectionsHelperCount = metadata.getProcessHelperMethods().stream()
                .filter(m -> m.getNameAsString().equals("initConnections"))
                .count();
        assertThat(connectionsHelperCount)
                .as("All connections must be grouped into exactly one initConnections helper")
                .isEqualTo(1);

        // The process() template body must contain only helper call statements
        String processBody = metadata.getGeneratedClassModel()
                .findFirst(com.github.javaparser.ast.body.MethodDeclaration.class)
                .map(com.github.javaparser.ast.body.MethodDeclaration::toString)
                .orElse("");

        assertThat(processBody).contains("initNode_");
        assertThat(processBody).contains("initConnections(");
    }
}
