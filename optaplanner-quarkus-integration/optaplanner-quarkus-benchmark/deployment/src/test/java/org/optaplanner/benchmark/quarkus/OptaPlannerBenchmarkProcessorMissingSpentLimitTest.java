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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.optaplanner.benchmark.config.PlannerBenchmarkConfig;
import org.optaplanner.benchmark.quarkus.config.OptaPlannerBenchmarkRuntimeConfig;
import org.optaplanner.benchmark.quarkus.testdata.normal.constraints.TestdataQuarkusConstraintProvider;
import org.optaplanner.benchmark.quarkus.testdata.normal.domain.TestdataQuarkusEntity;
import org.optaplanner.benchmark.quarkus.testdata.normal.domain.TestdataQuarkusSolution;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.test.QuarkusUnitTest;

class OptaPlannerBenchmarkProcessorMissingSpentLimitTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.test.flat-class-path", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestdataQuarkusEntity.class,
                            TestdataQuarkusSolution.class, TestdataQuarkusConstraintProvider.class));

    @Inject
    OptaPlannerBenchmarkRuntimeConfig optaPlannerBenchmarkRuntimeConfig;

    @Test
    void benchmark() throws ExecutionException, InterruptedException {
        assertThatThrownBy(() -> new OptaPlannerBenchmarkRecorder(new RuntimeValue<>(optaPlannerBenchmarkRuntimeConfig))
                .benchmarkConfigSupplier(new PlannerBenchmarkConfig()).get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("At least one of the properties quarkus.optaplanner.benchmark.solver.termination.spent-limit, quarkus.optaplanner.benchmark.solver.termination.best-score-limit, quarkus.optaplanner.benchmark.solver.termination.unimproved-spent-limit is required if termination is not configured in the inherited solver benchmark config and solverBenchmarkBluePrint is used.");
    }

}
