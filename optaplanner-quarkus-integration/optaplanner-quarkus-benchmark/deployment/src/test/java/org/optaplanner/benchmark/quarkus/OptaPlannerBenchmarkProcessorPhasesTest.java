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

package org.optaplanner.benchmark.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.optaplanner.benchmark.config.PlannerBenchmarkConfig;
import org.optaplanner.benchmark.quarkus.testdata.normal.constraints.TestdataQuarkusConstraintProvider;
import org.optaplanner.benchmark.quarkus.testdata.normal.domain.TestdataQuarkusEntity;
import org.optaplanner.benchmark.quarkus.testdata.normal.domain.TestdataQuarkusSolution;

import io.quarkus.test.QuarkusUnitTest;

class OptaPlannerBenchmarkProcessorPhasesTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.optaplanner.benchmark.solver.termination.best-score-limit", "0")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestdataQuarkusEntity.class,
                            TestdataQuarkusSolution.class, TestdataQuarkusConstraintProvider.class)
                    .addAsResource("solverConfigWithPhases.xml", "solverConfig.xml")
                    .addAsResource("solverBenchmarkConfigWithPhases.xml", "solverBenchmarkConfig.xml"));

    @Inject
    PlannerBenchmarkConfig plannerBenchmarkConfig;

    @Test
    void doesNotInheritPhasesFromSolverConfig() {
        assertThat(plannerBenchmarkConfig.getSolverBenchmarkConfigList().get(0).getSolverConfig()
                .getPhaseConfigList().size()).isEqualTo(2);
        assertThat(plannerBenchmarkConfig.getSolverBenchmarkConfigList().get(1).getSolverConfig()
                .getPhaseConfigList().size()).isEqualTo(3);
    }

}
