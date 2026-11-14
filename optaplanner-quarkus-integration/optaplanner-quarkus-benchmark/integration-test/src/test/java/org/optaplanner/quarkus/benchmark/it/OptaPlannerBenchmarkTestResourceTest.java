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

package org.optaplanner.quarkus.benchmark.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.path.xml.XmlPath;

/**
 * Test various OptaPlanner benchmarking operations running in Quarkus
 */

@QuarkusTest
class OptaPlannerBenchmarkTestResourceTest {

    @Test
    @Timeout(600)
    void benchmark() throws Exception {
        String benchmarkResultDirectory = RestAssured.given()
                .header("Content-Type", "text/plain;charset=UTF-8")
                .when()
                .post("/optaplanner/test/benchmark")
                .body().asString();
        assertThat(benchmarkResultDirectory).isNotNull();
        Path benchmarkResultDirectoryPath = Path.of(benchmarkResultDirectory);
        assertThat(Files.isDirectory(benchmarkResultDirectoryPath)).isTrue();
        Path benchmarkResultPath = Files.walk(benchmarkResultDirectoryPath, 2)
                .filter(path -> path.endsWith("plannerBenchmarkResult.xml")).findFirst().orElseThrow();
        assertThat(Files.isRegularFile(benchmarkResultPath)).isTrue();
        XmlPath xmlPath = XmlPath.from(benchmarkResultPath.toFile());
        assertThat(xmlPath.getBoolean(
                "plannerBenchmarkResult.solverBenchmarkResult.singleBenchmarkResult.subSingleBenchmarkResult.succeeded"))
                .isTrue();
    }

}
