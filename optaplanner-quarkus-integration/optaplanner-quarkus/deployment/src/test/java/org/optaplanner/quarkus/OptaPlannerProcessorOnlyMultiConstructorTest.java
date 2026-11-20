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

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.quarkus.testdata.gizmo.OnlyMultiArgsConstructorEntity;
import org.optaplanner.quarkus.testdata.gizmo.PrivateNoArgsConstructorConstraintProvider;
import org.optaplanner.quarkus.testdata.gizmo.PrivateNoArgsConstructorEntity;
import org.optaplanner.quarkus.testdata.gizmo.PrivateNoArgsConstructorSolution;

import io.quarkus.test.QuarkusUnitTest;

class OptaPlannerProcessorOnlyMultiConstructorTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.optaplanner.solver.termination.best-score-limit", "0")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(PrivateNoArgsConstructorConstraintProvider.class,
                            PrivateNoArgsConstructorSolution.class,
                            PrivateNoArgsConstructorEntity.class,
                            OnlyMultiArgsConstructorEntity.class))
            .assertException(throwable -> assertThat(throwable.getMessage()).isEqualTo(
                    "Class (" + OnlyMultiArgsConstructorEntity.class.getName()
                            + ") must have a no-args constructor so it can be constructed by OptaPlanner."));

    @Inject
    SolverManager<PrivateNoArgsConstructorSolution, Long> solverManager;

    @Test
    void canConstructBeansWithPrivateConstructors() throws ExecutionException, InterruptedException {
    }

}
