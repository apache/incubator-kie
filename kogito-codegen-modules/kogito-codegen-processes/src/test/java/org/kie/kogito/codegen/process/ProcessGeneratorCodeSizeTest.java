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
import org.drools.codegen.common.GeneratedFileType.Category;
import org.drools.compiler.compiler.io.memory.MemoryFileSystem;
import org.junit.jupiter.api.Test;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.JavaKogitoBuildContext;
import org.kie.kogito.codegen.core.io.CollectedResourceProducer;
import org.kie.memorycompiler.CompilationResult;
import org.kie.memorycompiler.JavaCompiler;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessGeneratorCodeSizeTest {

    private static final Path BASE_PATH = Paths.get("src/test/resources/").toAbsolutePath();

    private static final JavaCompiler JAVA_COMPILER = JavaCompiler.createNativeCompiler();

    @Test
    void largeBpmnCompilesSuccessfully() {
        Path bpmnFile = BASE_PATH.resolve("processcodesize/LargeSingleProcess.bpmn");

        KogitoBuildContext context = JavaKogitoBuildContext.builder()
                .withApplicationProperties(bpmnFile.getParent().toFile())
                .build();

        ProcessCodegen codegen = ProcessCodegen.ofCollectedResources(
                context,
                CollectedResourceProducer.fromFiles(BASE_PATH, bpmnFile.toFile()));

        Collection<GeneratedFile> generatedFiles = codegen.generate();
        assertThat(generatedFiles).isNotEmpty();

        List<GeneratedFile> processClassFiles = generatedFiles.stream()
                .filter(f -> f.type().category() == Category.SOURCE)
                .filter(f -> f.relativePath().endsWith("Process.java"))
                .toList();

        assertThat(processClassFiles)
                .as("Code generation must produce at least one XxxProcess.java source file")
                .isNotEmpty();

        MemoryFileSystem srcMfs = new MemoryFileSystem();
        MemoryFileSystem trgMfs = new MemoryFileSystem();
        String[] sourceNames = new String[processClassFiles.size()];
        for (int i = 0; i < processClassFiles.size(); i++) {
            GeneratedFile f = processClassFiles.get(i);
            sourceNames[i] = f.relativePath();
            srcMfs.write(f.relativePath(), f.contents());
        }

        CompilationResult result = JAVA_COMPILER.compile(
                sourceNames, srcMfs, trgMfs, getClass().getClassLoader());

        long codeTooLargeErrors = java.util.Arrays.stream(result.getErrors())
                .filter(e -> e.getMessage() != null && e.getMessage().contains("code too large"))
                .count();

        assertThat(codeTooLargeErrors)
                .as("The generated process() method must not exceed the JVM 64 KB bytecode limit")
                .isZero();
    }

    @Test
    void processMetaDataCarriesHelperMethodsForEachNodeAndConnection() {
        List<ProcessExecutableModelGenerator> generators =
                ProcessGenerationUtils.execModelFromProcessFile("/processcodesize/LargeSingleProcess.bpmn");

        assertThat(generators).hasSize(1);

        org.jbpm.compiler.canonical.ProcessMetaData metadata = generators.get(0).generate();

        assertThat(metadata.getProcessHelperMethods())
                .as("756 node helpers + 1 buildConnections helper = 757 total")
                .hasSize(757);

        // Every helper must be private, non-static, void, with exactly one parameter.
        metadata.getProcessHelperMethods().forEach(m -> {
            assertThat(m.getNameAsString())
                    .matches("build[A-Z].*");
            assertThat(m.isPrivate()).isTrue();
            assertThat(m.isStatic()).isFalse();
            assertThat(m.getParameters()).hasSize(1);
        });

        // Exactly one buildConnections helper.
        long connectionsHelperCount = metadata.getProcessHelperMethods().stream()
                .filter(m -> m.getNameAsString().equals("buildConnections"))
                .count();
        assertThat(connectionsHelperCount)
                .as("All connections must be grouped into exactly one buildConnections helper")
                .isEqualTo(1);

        // The generated process() method body must consist entirely of helper call
        // statements — each node delegated to buildXxx(factory) — plus a final
        // buildConnections(factory) call. The method body is the sole MethodDeclaration
        // in the ProcessMetaData's CompilationUnit.
        String processBody = metadata.getGeneratedClassModel()
                .findFirst(com.github.javaparser.ast.body.MethodDeclaration.class)
                .flatMap(com.github.javaparser.ast.body.MethodDeclaration::getBody)
                .map(Object::toString)
                .orElse("");

        // Body must contain delegate calls: buildXxx(factory)
        assertThat(processBody)
                .as("process() body must contain per-node helper invocations")
                .containsPattern("build[A-Z]\\w+\\(factory\\)");

        // Body must contain the connection-building delegate call
        assertThat(processBody)
                .as("process() body must delegate connections to buildConnections(factory)")
                .contains("buildConnections(factory)");
    }
}
