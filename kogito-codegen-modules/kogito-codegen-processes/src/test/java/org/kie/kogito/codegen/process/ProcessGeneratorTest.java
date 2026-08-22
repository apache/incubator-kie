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

import java.io.File;
import java.util.Collection;

import org.drools.io.FileSystemResource;
import org.jbpm.compiler.canonical.ProcessToExecModelGenerator;
import org.junit.jupiter.api.Test;
import org.kie.api.definition.process.Process;
import org.kie.kogito.codegen.api.AddonsConfig;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.JavaKogitoBuildContext;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcess;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessGeneratorTest {

    private static final String TEST_PROCESS_FILE = "src/test/resources/startsignal/StartSignalEventNoPayload.bpmn2";

    @Test
    void classDeclarationExposesProcessEntryPoint() {
        ClassOrInterfaceDeclaration cls = generateClassDeclaration(buildExecModelGenerator());

        assertThat(cls.getMethodsByName("process")).hasSize(1);
        MethodDeclaration processMethod = cls.getMethodsByName("process").get(0);
        assertThat(processMethod.getModifiers()).containsExactly(Modifier.protectedModifier());
        assertThat(processMethod.getType().asString()).isEqualTo(Process.class.getCanonicalName());
    }

    @Test
    void classDeclarationTransplantsEveryGeneratedMethodNotJustTheFirst() {
        ClassOrInterfaceDeclaration cls = generateClassDeclaration(buildExecModelGenerator());

        assertThat(cls.getMethodsByName("process")).hasSize(1);
        assertThat(cls.getMethodsByName("initVariables")).hasSize(1);
        assertThat(cls.getMethodsByName("initMetadata")).hasSize(1);
        assertThat(cls.getMethodsByName("initNodes")).hasSize(1);
        assertThat(cls.getMethodsByName("initConnections")).hasSize(1);
    }

    @Test
    void classDeclarationDoesNotChunkNodesForASmallProcess() {
        ClassOrInterfaceDeclaration cls = generateClassDeclaration(buildExecModelGenerator());

        assertThat(cls.getMethodsByName("initNodes")).hasSize(1);
        assertThat(cls.getMethodsByName("initNodes_0")).isEmpty();
    }

    private ClassOrInterfaceDeclaration generateClassDeclaration(ProcessExecutableModelGenerator execModelGen) {
        KogitoBuildContext context = buildContext();
        KogitoWorkflowProcess process = execModelGen.process();
        ProcessGenerator generator = new ProcessGenerator(
                context,
                process,
                execModelGen,
                execModelGen.className(),
                new ModelClassGenerator(context, process).className(),
                context.getPackageName() + ".Application");
        return generator.classDeclaration();
    }

    private ProcessExecutableModelGenerator buildExecModelGenerator() {
        KogitoBuildContext context = buildContext();
        KogitoWorkflowProcess process = parseProcess(TEST_PROCESS_FILE);
        return new ProcessExecutableModelGenerator(process, new ProcessToExecModelGenerator(context.getClassLoader()));
    }

    private KogitoWorkflowProcess parseProcess(String fileName) {
        Collection<Process> processes = ProcessCodegen.parseProcessFile(new FileSystemResource(new File(fileName)));
        assertThat(processes).hasSize(1);
        Process process = processes.stream().findAny().orElseThrow();
        assertThat(process).isInstanceOf(KogitoWorkflowProcess.class);
        return (KogitoWorkflowProcess) process;
    }

    private KogitoBuildContext buildContext() {
        AddonsConfig addonsConfig = AddonsConfig.builder()
                .withMonitoring(false)
                .withPrometheusMonitoring(false)
                .build();
        return JavaKogitoBuildContext.builder().withAddonsConfig(addonsConfig).build();
    }
}
