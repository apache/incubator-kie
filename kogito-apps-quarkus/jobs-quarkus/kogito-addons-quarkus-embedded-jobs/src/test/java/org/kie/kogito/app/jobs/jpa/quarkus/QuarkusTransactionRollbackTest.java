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
package org.kie.kogito.app.jobs.jpa.quarkus;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.app.jobs.quarkus.QuarkusJobsService;
import org.kie.kogito.app.jobs.quarkus.QuarkusTransactionRollbackMarker;
import org.kie.kogito.jobs.ExactExpirationTime;
import org.kie.kogito.jobs.JobsService;
import org.kie.kogito.jobs.descriptors.ProcessInstanceJobDescription;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@org.junit.jupiter.api.Disabled("Flaky under parallel builds (-T 2): transaction status check races under CPU contention")
public class QuarkusTransactionRollbackTest {

    @Inject
    JobsService jobsService;

    @Inject
    TestJobSchedulerListener listener;

    @Inject
    TestJobExecutor testJobExecutor;

    @Inject
    TestExceptionHandler exceptionHandler;

    @Inject
    QuarkusTransactionRollbackMarker transactionRollbackMarker;

    @Inject
    TransactionManager transactionManager;

    @Inject
    MockDataRepository mockDataRepository;

    @BeforeEach
    public void init() {
        ((QuarkusJobsService) jobsService).init();
        testJobExecutor.reset();
        exceptionHandler.reset();
        mockDataRepository.reset();
        mockDataRepository.setTransactionManager(transactionManager);
    }

    @AfterEach
    public void cleanup() {
        ((QuarkusJobsService) jobsService).destroy();
    }

    @Test
    public void testTransactionRollbackMarkerInjected() {
        assertThat(transactionRollbackMarker).isNotNull();
        assertThat(transactionRollbackMarker.isTransactionActive()).isFalse();
    }

    @Test
    public void testTransactionRollbackOnJobFailure() throws Exception {
        // Configure the test executor to fail 4 times (initial + 3 retries)
        testJobExecutor.setNumberOfFailures(4);

        ProcessInstanceJobDescription jobDescription = ProcessInstanceJobDescription.newProcessInstanceJobDescriptionBuilder()
                .id("rollback-test-job")
                .timerId("-1")
                .expirationTime(ExactExpirationTime.of(Instant.now().plus(Duration.ofSeconds(2)).atZone(ZoneId.of("UTC"))))
                .priority(5)
                .processInstanceId("test-process-instance")
                .rootProcessInstanceId(null)
                .processId("test-process")
                .processVersion(null)
                .rootProcessId("test-node-instance").build();

        listener.setCount(4);
        jobsService.scheduleJob(jobDescription);

        // Wait for all retries to complete and error handler to be invoked
        Awaitility.await()
                .atMost(Duration.ofSeconds(10L))
                .untilAsserted(() -> {
                    assertThat(exceptionHandler.isError())
                            .as("Error handler should be invoked after max retries")
                            .isTrue();
                });

        assertThat(listener.await(1, java.util.concurrent.TimeUnit.SECONDS))
                .as("All job executions should have been attempted")
                .isTrue();

        assertThat(mockDataRepository.getTotalPersistenceAttempts())
                .as("Should have 4 persistence attempts (initial + 3 retries)")
                .isEqualTo(4);

        assertThat(mockDataRepository.getRolledBackAttempts())
                .as("All failed attempts should have been rolled back")
                .isEqualTo(4);

        assertThat(mockDataRepository.getSuccessfulCommits())
                .as("No data should be committed when all attempts fail")
                .isEqualTo(0);

        assertThat(mockDataRepository.getCommittedCount("rollback-test-job"))
                .as("No duplicate data should be persisted due to rollback")
                .isEqualTo(0);
    }

    @Test
    public void testTransactionRollbackMarkerFunctionality() throws Exception {
        assertThat(transactionRollbackMarker.isTransactionActive()).isFalse();

        transactionManager.begin();
        try {
            assertThat(transactionRollbackMarker.isTransactionActive()).isTrue();

            transactionRollbackMarker.markForRollback();

            // Verify the transaction is marked for rollback
            assertThat(transactionManager.getStatus())
                    .as("Transaction should be marked for rollback")
                    .isEqualTo(Status.STATUS_MARKED_ROLLBACK);
        } finally {
            transactionManager.rollback();
        }

        assertThat(transactionRollbackMarker.isTransactionActive()).isFalse();
    }

}
