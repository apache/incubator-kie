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
import static org.assertj.core.api.Assertions.fail;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.optaplanner.core.api.score.ScoreManager;
import org.optaplanner.core.api.score.buildin.simple.SimpleScore;
import org.optaplanner.core.api.solver.SolutionManager;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.quarkus.testdata.invalid.inverserelation.constraints.TestdataInvalidQuarkusConstraintProvider;
import org.optaplanner.quarkus.testdata.invalid.inverserelation.domain.TestdataInvalidInverseRelationEntity;
import org.optaplanner.quarkus.testdata.invalid.inverserelation.domain.TestdataInvalidInverseRelationSolution;
import org.optaplanner.quarkus.testdata.invalid.inverserelation.domain.TestdataInvalidInverseRelationValue;

import io.quarkus.test.QuarkusUnitTest;

class OptaPlannerProcessorInvalidTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.optaplanner.solver.termination.best-score-limit", "0")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestdataInvalidInverseRelationSolution.class,
                            TestdataInvalidInverseRelationEntity.class,
                            TestdataInvalidInverseRelationValue.class,
                            TestdataInvalidQuarkusConstraintProvider.class))
            .assertException(exception -> {
                assertThat(exception).isInstanceOf(IllegalStateException.class);
                assertThat(exception.getMessage()).isEqualTo(
                        "The field (entityList) with a @InverseRelationShadowVariable annotation is" +
                                " in a class (org.optaplanner.quarkus.testdata.invalid.inverserelation.domain.TestdataInvalidInverseRelationValue)" +
                                " that does not have a @PlanningEntity annotation.\n" +
                                "Maybe add a @PlanningEntity annotation on the class (org.optaplanner.quarkus.testdata.invalid.inverserelation.domain.TestdataInvalidInverseRelationValue).");
            });

    @Inject
    SolverFactory<TestdataInvalidInverseRelationSolution> solverFactory;
    @Inject
    SolverManager<TestdataInvalidInverseRelationSolution, Long> solverManager;
    @Inject
    ScoreManager<TestdataInvalidInverseRelationSolution, SimpleScore> scoreManager;
    @Inject
    SolutionManager<TestdataInvalidInverseRelationSolution, SimpleScore> solutionManager;

    @Test
    void solve() {
        fail("Build should fail");
    }

}
