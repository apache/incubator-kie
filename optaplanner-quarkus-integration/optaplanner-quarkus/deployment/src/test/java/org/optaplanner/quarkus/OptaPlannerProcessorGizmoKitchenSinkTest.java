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

import java.util.Collections;
import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.optaplanner.core.api.score.ScoreManager;
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.api.score.buildin.simple.SimpleScore;
import org.optaplanner.core.api.solver.SolutionManager;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverJob;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.impl.solver.DefaultSolutionManager;
import org.optaplanner.core.impl.solver.DefaultSolverFactory;
import org.optaplanner.core.impl.solver.DefaultSolverManager;
import org.optaplanner.quarkus.testdata.gizmo.DummyConstraintProvider;
import org.optaplanner.quarkus.testdata.gizmo.DummyVariableListener;
import org.optaplanner.quarkus.testdata.gizmo.TestDataKitchenSinkEntity;
import org.optaplanner.quarkus.testdata.gizmo.TestDataKitchenSinkSolution;

import io.quarkus.test.QuarkusUnitTest;

class OptaPlannerProcessorGizmoKitchenSinkTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.optaplanner.solver.termination.best-score-limit", "0hard/0soft")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestDataKitchenSinkEntity.class,
                            TestDataKitchenSinkSolution.class,
                            DummyConstraintProvider.class,
                            DummyVariableListener.class));

    @Inject
    SolverFactory<TestDataKitchenSinkSolution> solverFactory;
    @Inject
    SolverManager<TestDataKitchenSinkSolution, Long> solverManager;
    @Inject
    ScoreManager<TestDataKitchenSinkSolution, SimpleScore> scoreManager;
    @Inject
    SolutionManager<TestDataKitchenSinkSolution, SimpleScore> solutionManager;

    @Test
    void singletonSolverFactory() {
        assertThat(solverFactory).isNotNull();
        assertThat(scoreManager).isNotNull();
        // There is only one ScoreDirectorFactory instance
        assertThat(((DefaultSolutionManager<?, ?>) solutionManager).getScoreDirectorFactory())
                .isSameAs(((DefaultSolverFactory<?>) solverFactory).getScoreDirectorFactory());
        assertThat(solverManager).isNotNull();
        // There is only one SolverFactory instance
        assertThat(((DefaultSolverManager<TestDataKitchenSinkSolution, Long>) solverManager).getSolverFactory())
                .isSameAs(solverFactory);
    }

    @Test
    void solve() throws ExecutionException, InterruptedException {
        TestDataKitchenSinkSolution problem = new TestDataKitchenSinkSolution(
                new TestDataKitchenSinkEntity(),
                Collections.emptyList(),
                "Test",
                Collections.emptyList(),
                HardSoftLongScore.ZERO);

        SolverJob<TestDataKitchenSinkSolution, Long> solverJob = solverManager.solve(1L, problem);
        TestDataKitchenSinkSolution solution = solverJob.getFinalBestSolution();
        assertThat(solution).isNotNull();
        assertThat(solution.getPlanningEntityProperty().testGetIntVariable()).isEqualTo(1);
        assertThat(solution.getPlanningEntityProperty().testGetStringVariable()).isEqualTo("A");
    }

}
