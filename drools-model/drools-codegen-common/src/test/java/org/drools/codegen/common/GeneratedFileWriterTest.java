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
package org.drools.codegen.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@Execution(SAME_THREAD)
class GeneratedFileWriterTest {

    @ParameterizedTest
    @EnumSource(value = AppPaths.BuildTool.class, names = {"GRADLE", "MAVEN"})
    void builderWithConfig(AppPaths.BuildTool bt) {
        String resourcesDirectoryProperty = "resource.property";
        String sourcesDirectoryProperty = "source.property";
        String finalPath = "final/destination/";
        String resourcesDirectory = "custom/resources/path";
        String sourcesDirectory = "custom/sources/path";
        System.setProperty(resourcesDirectoryProperty, resourcesDirectory);
        System.setProperty(sourcesDirectoryProperty, sourcesDirectory);
        GeneratedFileWriter.Builder retrieved = GeneratedFileWriter.builder(finalPath, resourcesDirectoryProperty,
                                                                            sourcesDirectoryProperty, bt);
        assertThat(retrieved.classesDir).isEqualTo(bt.CLASSES_PATH.toString());
        assertThat(retrieved.resourcesDir).isEqualTo(resourcesDirectory);
        assertThat(retrieved.sourcesDir).isEqualTo(sourcesDirectory);
        System.clearProperty(resourcesDirectoryProperty);
        System.clearProperty(sourcesDirectoryProperty);
    }

    @ParameterizedTest
    @EnumSource(value = AppPaths.BuildTool.class, names = {"GRADLE", "MAVEN"})
    void builderWithoutConfig(AppPaths.BuildTool bt) {
        String resourcesDirectoryProperty = "resource.property";
        String sourcesDirectoryProperty = "source.property";
        String finalPath = "final";
        GeneratedFileWriter.Builder retrieved = GeneratedFileWriter.builder(finalPath, resourcesDirectoryProperty,
                                                                            sourcesDirectoryProperty, bt);
        assertThat(retrieved.classesDir).isEqualTo(bt.CLASSES_PATH.toString());
        String expected = String.format("%s/%s", bt.GENERATED_RESOURCES_PATH.toString(), finalPath).replace("/",
                                                                                                            File.separator);
        assertThat(retrieved.resourcesDir).isEqualTo(expected);
        expected = String.format("%s/%s", bt.GENERATED_SOURCES_PATH.toString(), finalPath).replace("/", File.separator);
        assertThat(retrieved.sourcesDir).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("btAndGeneratedFileCategoryProvider")
    void write(AppPaths.BuildTool bt, GeneratedFileType.Category category) throws IOException {
        String relativePath = "relative/path";
        GeneratedFile generatedFile = new GeneratedFile(GeneratedFileType.of(category), relativePath, "");
        GeneratedFileWriter spiedWriter = spy(new GeneratedFileWriter(bt.CLASSES_PATH, bt.GENERATED_RESOURCES_PATH,
                                                                      bt.GENERATED_SOURCES_PATH));
        spiedWriter.write(generatedFile);
        Path location = switch (category) {
            case INTERNAL_RESOURCE, STATIC_HTTP_RESOURCE, COMPILED_CLASS -> bt.CLASSES_PATH;
            case SOURCE -> bt.GENERATED_SOURCES_PATH;
        };
        verify(spiedWriter).writeGeneratedFile(generatedFile, location);
    }

    static Stream<Arguments> btAndGeneratedFileCategoryProvider() {
        Stream.Builder<Arguments> argumentBuilder = Stream.builder();
        for (AppPaths.BuildTool bt : AppPaths.BuildTool.values()) {
            for (GeneratedFileType.Category category : GeneratedFileType.Category.values()) {
                argumentBuilder.add(Arguments.of(bt, category));
            }
        }
        return argumentBuilder.build();
    }
}