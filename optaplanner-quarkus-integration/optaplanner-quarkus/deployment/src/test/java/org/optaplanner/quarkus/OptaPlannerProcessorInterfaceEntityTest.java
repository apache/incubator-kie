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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.quarkus.testdata.interfaceentity.domain.TestdataInterfaceEntity;
import org.optaplanner.quarkus.testdata.interfaceentity.domain.TestdataInterfaceEntityImplementation;
import org.optaplanner.quarkus.testdata.interfaceentity.domain.TestdataInterfaceEntitySolution;

import io.quarkus.test.QuarkusUnitTest;

class OptaPlannerProcessorInterfaceEntityTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.optaplanner.solver.termination.best-score-limit", "0")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackages(true, "org.optaplanner.quarkus.testdata.interfaceentity"));

    @Inject
    SolverFactory<TestdataInterfaceEntitySolution> solverFactory;

    @Test
    void buildSolver() {
        TestdataInterfaceEntitySolution problem = new TestdataInterfaceEntitySolution();
        List<TestdataInterfaceEntity> entityList = IntStream.range(1, 3)
                .mapToObj(i -> new TestdataInterfaceEntityImplementation())
                .collect(Collectors.toList());

        problem.setValueList(IntStream.range(0, 3)
                .boxed()
                .collect(Collectors.toList()));
        problem.setEntityList(entityList);

        TestdataInterfaceEntitySolution solution = solverFactory.buildSolver().solve(problem);
        assertThat(solution).isNotNull();

        assertThat(solution.getEntityList().size()).isEqualTo(entityList.size());
        for (int i = 0; i < entityList.size(); i++) {
            assertThat(solution.getEntityList().get(i)).isNotSameAs(entityList.get(i));
        }
    }
}
