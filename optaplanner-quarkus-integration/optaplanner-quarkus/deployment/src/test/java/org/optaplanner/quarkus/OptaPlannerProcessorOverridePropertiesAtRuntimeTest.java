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

package org.optaplanner.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.SolverManagerConfig;
import org.optaplanner.quarkus.testdata.normal.constraints.TestdataQuarkusConstraintProvider;
import org.optaplanner.quarkus.testdata.normal.domain.TestdataQuarkusEntity;
import org.optaplanner.quarkus.testdata.normal.domain.TestdataQuarkusSolution;

import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusProdModeTest;
import io.restassured.RestAssured;

class OptaPlannerProcessorOverridePropertiesAtRuntimeTest {

    private static final String QUARKUS_VERSION = getRequiredProperty("version.io.quarkus");

    private static String getRequiredProperty(String name) {
        final String v = System.getProperty(name);
        if (v == null || v.isEmpty()) {
            throw new IllegalStateException("The system property (" + name + ") has not been set.");
        }
        return v;
    }

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-resteasy", QUARKUS_VERSION)))
            // We want to check if these are overridden at runtime
            .overrideConfigKey("quarkus.optaplanner.solver.termination.best-score-limit", "0")
            .overrideConfigKey("quarkus.optaplanner.solver.move-thread-count", "4")
            .overrideConfigKey("quarkus.optaplanner.solver-manager.parallel-solver-count", "1")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestdataQuarkusEntity.class,
                            TestdataQuarkusSolution.class,
                            TestdataQuarkusConstraintProvider.class,
                            OptaPlannerTestResource.class))
            .setRuntimeProperties(getRuntimeProperties())
            .setRun(true);

    private static Map<String, String> getRuntimeProperties() {
        Map<String, String> out = new HashMap<>();
        out.put("quarkus.optaplanner.solver.termination.best-score-limit", "7");
        out.put("quarkus.optaplanner.solver.move-thread-count", "3");
        out.put("quarkus.optaplanner.solver-manager.parallel-solver-count", "10");
        return out;
    }

    // Can't use injection, so we need a resource to fetch the properties
    @Path("/optaplanner/test")
    public static class OptaPlannerTestResource {
        @Inject
        SolverConfig solverConfig;

        @Inject
        SolverManagerConfig solverManagerConfig;

        @GET
        @Path("/solver-config")
        @Produces(MediaType.TEXT_PLAIN)
        public String getSolverConfig() {
            StringBuilder sb = new StringBuilder();
            sb.append("termination.bestScoreLimit=").append(solverConfig.getTerminationConfig().getBestScoreLimit())
                    .append("\n");
            sb.append("moveThreadCount=").append(solverConfig.getMoveThreadCount()).append("\n");
            return sb.toString();
        }

        @GET
        @Path("/solver-manager-config")
        @Produces(MediaType.TEXT_PLAIN)
        public String getSolverManagerConfig() {
            StringBuilder sb = new StringBuilder();
            sb.append("parallelSolverCount=").append(solverManagerConfig.getParallelSolverCount()).append("\n");
            return sb.toString();
        }
    }

    @Test
    void solverConfigPropertiesShouldBeOverwritten() throws IOException {
        Properties solverConfigProperties = new Properties();
        solverConfigProperties.load(RestAssured.given()
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.TEXT_PLAIN)
                .when()
                .get("/optaplanner/test/solver-config")
                .asInputStream());
        assertEquals("7", solverConfigProperties.get("termination.bestScoreLimit"));
        assertEquals("3", solverConfigProperties.get("moveThreadCount"));
    }

    @Test
    void solverManagerConfigPropertiesShouldBeOverwritten() throws IOException {
        Properties solverManagerProperties = new Properties();
        solverManagerProperties.load(RestAssured.given()
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.TEXT_PLAIN)
                .when()
                .get("/optaplanner/test/solver-manager-config")
                .asInputStream());
        assertEquals("10", solverManagerProperties.get("parallelSolverCount"));
    }

}
