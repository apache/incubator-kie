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
package org.kie.kogito.quarkus.common.deployment;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;

import org.drools.codegen.common.AppPaths;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkus.bootstrap.model.PathsCollection;
import io.quarkus.paths.PathCollection;

import static org.assertj.core.api.Assertions.assertThat;

class KogitoAssetsProcessorTest {

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void getRootPathsWithoutClasses(boolean withGradle) {
        String projectDirPath = "projectDir";
        Path projectDir = Path.of(projectDirPath);
        AppPaths.BuildTool bt;
        if (withGradle) {
            bt = AppPaths.BuildTool.GRADLE;
        } else {
            bt = AppPaths.BuildTool.MAVEN;
        }
        Path outputTarget = Path.of(bt.OUTPUT_DIRECTORY);
        Iterable<Path> paths = Arrays.asList(projectDir, outputTarget);
        PathCollection resolvedPaths = PathsCollection.from(paths);
        PathCollection retrieved = KogitoAssetsProcessor.getRootPaths(resolvedPaths, bt);
        assertThat(retrieved.size()).isEqualTo(resolvedPaths.size());
        paths.forEach(expected -> assertThat(retrieved).contains(expected));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void getRootPathsWithClasses(boolean withGradle) {
        String projectDirPath = "projectDir";
        AppPaths.BuildTool bt;
        if (withGradle) {
            bt = AppPaths.BuildTool.GRADLE;
        } else {
            bt = AppPaths.BuildTool.MAVEN;
        }
        String outputTargetPathClasses = String.format("%s/%s", projectDirPath, bt.CLASSES_PATH.toString()).replace("./", "").replace("/", File.separator);
        Path projectDir = Path.of(projectDirPath);
        Path outputTarget = Path.of(outputTargetPathClasses);
        Iterable<Path> paths = Arrays.asList(projectDir, outputTarget);

        PathCollection resolvedPaths = PathsCollection.from(paths);
        PathCollection retrieved = KogitoAssetsProcessor.getRootPaths(resolvedPaths, bt);
        int expectedSize = withGradle ? 1 : resolvedPaths.size();
        assertThat(retrieved.size()).isEqualTo(expectedSize);
        String expectedPath = String.format("%s/%s", projectDirPath, bt.CLASSES_PATH).replace("/", File.separator);
        assertThat(retrieved.contains(Path.of(expectedPath))).isTrue();
    }
}
