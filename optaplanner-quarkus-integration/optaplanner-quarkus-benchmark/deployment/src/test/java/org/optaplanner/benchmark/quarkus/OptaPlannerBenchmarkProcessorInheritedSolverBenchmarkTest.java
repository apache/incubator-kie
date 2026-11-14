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

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.optaplanner.benchmark.config.PlannerBenchmarkConfig;
import org.optaplanner.benchmark.config.SolverBenchmarkConfig;
import org.optaplanner.benchmark.quarkus.testdata.normal.constraints.TestdataQuarkusConstraintProvider;
import org.optaplanner.benchmark.quarkus.testdata.normal.domain.TestdataQuarkusEntity;
import org.optaplanner.benchmark.quarkus.testdata.normal.domain.TestdataQuarkusOtherEntity;
import org.optaplanner.benchmark.quarkus.testdata.normal.domain.TestdataQuarkusSolution;
import org.optaplanner.core.config.solver.SolverConfig;

import io.quarkus.test.QuarkusUnitTest;

class OptaPlannerBenchmarkProcessorInheritedSolverBenchmarkTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.optaplanner.benchmark.solver.termination.best-score-limit", "0")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestdataQuarkusEntity.class, TestdataQuarkusOtherEntity.class,
                            TestdataQuarkusSolution.class, TestdataQuarkusConstraintProvider.class)
                    .addAsResource("solverConfig.xml")
                    .addAsResource("solverBenchmarkConfigWithInheritedSolverBenchmark.xml", "solverBenchmarkConfig.xml"));

    @Inject
    SolverConfig solverConfig;

    @Inject
    PlannerBenchmarkConfig plannerBenchmarkConfig;

    @Test
    void inheritClassesFromSolverConfig() {
        assertThat(solverConfig.getSolutionClass()).isEqualTo(TestdataQuarkusSolution.class);
        assertThat(solverConfig.getEntityClassList().size()).isEqualTo(2);
        assertThat(solverConfig.getEntityClassList().contains(TestdataQuarkusEntity.class)).isTrue();
        assertThat(solverConfig.getEntityClassList().contains(TestdataQuarkusOtherEntity.class)).isTrue();
        assertThat(plannerBenchmarkConfig.getInheritedSolverBenchmarkConfig()
                .getSolverConfig().getTerminationConfig().getMillisecondsSpentLimit()).isEqualTo(5);
        assertThat(plannerBenchmarkConfig.getInheritedSolverBenchmarkConfig().getSolverConfig().getEntityClassList())
                .isEqualTo(List.of(TestdataQuarkusEntity.class));

        SolverBenchmarkConfig childBenchmarkConfig = plannerBenchmarkConfig.getSolverBenchmarkConfigList().get(0);
        assertThat(childBenchmarkConfig.getSolverConfig().getSolutionClass()).isEqualTo(TestdataQuarkusSolution.class);
        assertThat(childBenchmarkConfig.getSolverConfig().getEntityClassList()).isNull(); // inherited from inherited solver config
        assertThat(childBenchmarkConfig.getSolverConfig().getScoreDirectorFactoryConfig()
                .getConstraintProviderClass()).isEqualTo(TestdataQuarkusConstraintProvider.class);

        childBenchmarkConfig = plannerBenchmarkConfig.getSolverBenchmarkConfigList().get(1);
        assertThat(childBenchmarkConfig.getSolverConfig().getSolutionClass())
                .isEqualTo(TestdataQuarkusConstraintProvider.class);
        assertThat(childBenchmarkConfig.getSolverConfig().getEntityClassList()).isNull(); // inherited from inherited solver config
        assertThat(childBenchmarkConfig.getSolverConfig().getScoreDirectorFactoryConfig()
                .getConstraintProviderClass()).isEqualTo(TestdataQuarkusConstraintProvider.class);
    }

}
