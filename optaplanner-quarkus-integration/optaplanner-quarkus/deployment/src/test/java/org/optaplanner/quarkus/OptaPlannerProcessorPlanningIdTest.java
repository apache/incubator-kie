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

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.quarkus.testdata.superclass.constraints.DummyConstraintProvider;
import org.optaplanner.quarkus.testdata.superclass.domain.TestdataEntity;
import org.optaplanner.quarkus.testdata.superclass.domain.TestdataSolution;

import io.quarkus.test.QuarkusUnitTest;

class OptaPlannerProcessorPlanningIdTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.optaplanner.solver.termination.best-score-limit", "0")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage("org.optaplanner.quarkus.testdata.superclass.domain") // Cannot reference a non-public class.
                    .addClasses(DummyConstraintProvider.class));

    @Inject
    SolverFactory<TestdataSolution> solverFactory;

    @Test
    void buildSolver() {
        TestdataSolution problem = new TestdataSolution();
        problem.setValueList(IntStream.range(1, 3)
                .mapToObj(i -> "v" + i)
                .collect(Collectors.toList()));
        problem.setEntityList(IntStream.range(1, 3)
                .mapToObj(i -> new TestdataEntity(i))
                .collect(Collectors.toList()));

        TestdataSolution solution = solverFactory.buildSolver().solve(problem);
        assertThat(solution).isNotNull();
    }
}
