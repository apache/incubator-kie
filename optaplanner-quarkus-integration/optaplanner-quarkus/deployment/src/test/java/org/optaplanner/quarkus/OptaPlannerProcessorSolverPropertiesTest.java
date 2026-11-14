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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.optaplanner.core.api.domain.common.DomainAccessType;
import org.optaplanner.core.api.score.buildin.simple.SimpleScore;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.solver.EnvironmentMode;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.quarkus.testdata.normal.constraints.TestdataQuarkusConstraintProvider;
import org.optaplanner.quarkus.testdata.normal.domain.TestdataQuarkusEntity;
import org.optaplanner.quarkus.testdata.normal.domain.TestdataQuarkusSolution;

import io.quarkus.test.QuarkusUnitTest;

class OptaPlannerProcessorSolverPropertiesTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.optaplanner.solver.environment-mode", "FULL_ASSERT")
            .overrideConfigKey("quarkus.optaplanner.solver.daemon", "true")
            .overrideConfigKey("quarkus.optaplanner.solver.move-thread-count", "2")
            .overrideConfigKey("quarkus.optaplanner.solver.domain-access-type", "REFLECTION")
            .overrideConfigKey("quarkus.optaplanner.solver.termination.spent-limit", "4h")
            .overrideConfigKey("quarkus.optaplanner.solver.termination.unimproved-spent-limit", "5h")
            .overrideConfigKey("quarkus.optaplanner.solver.termination.best-score-limit", "0")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestdataQuarkusEntity.class, TestdataQuarkusSolution.class,
                            TestdataQuarkusConstraintProvider.class));

    @Inject
    SolverConfig solverConfig;
    @Inject
    SolverFactory<TestdataQuarkusSolution> solverFactory;

    @Test
    void solverProperties() {
        assertThat(solverConfig.getEnvironmentMode()).isEqualTo(EnvironmentMode.FULL_ASSERT);
        assertThat(solverConfig.getDaemon()).isTrue();
        assertThat(solverConfig.getMoveThreadCount()).isEqualTo("2");
        assertThat(solverConfig.getDomainAccessType()).isEqualTo(DomainAccessType.REFLECTION);
        assertThat(solverConfig.getScoreDirectorFactoryConfig().getConstraintStreamImplType()).isNull();

        assertThat(solverFactory).isNotNull();
    }

    @Test
    void terminationProperties() {
        assertThat(solverConfig.getTerminationConfig().getSpentLimit()).isEqualTo(Duration.ofHours(4));
        assertThat(solverConfig.getTerminationConfig().getUnimprovedSpentLimit()).isEqualTo(Duration.ofHours(5));
        assertThat(solverConfig.getTerminationConfig().getBestScoreLimit()).isEqualTo(SimpleScore.of(0).toString());
    }
}
