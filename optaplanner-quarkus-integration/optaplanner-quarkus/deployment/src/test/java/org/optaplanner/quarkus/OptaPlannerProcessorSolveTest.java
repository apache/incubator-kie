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

import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.optaplanner.core.api.score.ScoreManager;
import org.optaplanner.core.api.score.buildin.simple.SimpleScore;
import org.optaplanner.core.api.solver.SolutionManager;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverJob;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.impl.solver.DefaultSolutionManager;
import org.optaplanner.core.impl.solver.DefaultSolverFactory;
import org.optaplanner.core.impl.solver.DefaultSolverManager;
import org.optaplanner.quarkus.testdata.normal.constraints.TestdataQuarkusConstraintProvider;
import org.optaplanner.quarkus.testdata.normal.domain.TestdataQuarkusEntity;
import org.optaplanner.quarkus.testdata.normal.domain.TestdataQuarkusSolution;

import io.quarkus.test.QuarkusUnitTest;

class OptaPlannerProcessorSolveTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.optaplanner.solver.termination.best-score-limit", "0")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestdataQuarkusEntity.class,
                            TestdataQuarkusSolution.class, TestdataQuarkusConstraintProvider.class));

    @Inject
    SolverFactory<TestdataQuarkusSolution> solverFactory;
    @Inject
    SolverManager<TestdataQuarkusSolution, Long> solverManager;
    @Inject
    ScoreManager<TestdataQuarkusSolution, SimpleScore> scoreManager;
    @Inject
    SolutionManager<TestdataQuarkusSolution, SimpleScore> solutionManager;

    @Test
    void singletonSolverFactory() {
        assertThat(solverFactory).isNotNull();
        assertThat(((DefaultSolutionManager<TestdataQuarkusSolution, SimpleScore>) solutionManager).getScoreDirectorFactory())
                .isSameAs(((DefaultSolverFactory<TestdataQuarkusSolution>) solverFactory).getScoreDirectorFactory());
        assertThat(solverManager).isNotNull();
        // There is only one SolverFactory instance
        assertThat(((DefaultSolverManager<TestdataQuarkusSolution, Long>) solverManager).getSolverFactory())
                .isSameAs(solverFactory);
        assertThat(solutionManager).isNotNull();
    }

    @Test
    void solve() throws ExecutionException, InterruptedException {
        TestdataQuarkusSolution problem = new TestdataQuarkusSolution();
        problem.setValueList(IntStream.range(1, 3)
                .mapToObj(i -> "v" + i)
                .collect(Collectors.toList()));
        problem.setEntityList(IntStream.range(1, 3)
                .mapToObj(i -> new TestdataQuarkusEntity())
                .collect(Collectors.toList()));
        SolverJob<TestdataQuarkusSolution, Long> solverJob = solverManager.solve(1L, problem);
        TestdataQuarkusSolution solution = solverJob.getFinalBestSolution();
        assertThat(solution).isNotNull();
        assertThat(solution.getScore().score() >= 0).isTrue();
    }

}
