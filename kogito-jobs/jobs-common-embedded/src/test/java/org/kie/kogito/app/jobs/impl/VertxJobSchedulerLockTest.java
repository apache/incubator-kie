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

package org.kie.kogito.app.jobs.impl;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.kie.kogito.app.jobs.api.JobDescriptionMerger;
import org.kie.kogito.app.jobs.api.JobDetailsEventAdapter;
import org.kie.kogito.app.jobs.api.JobScheduler;
import org.kie.kogito.app.jobs.api.JobSchedulerBuilder;
import org.kie.kogito.app.jobs.api.JobTimeoutExecution;
import org.kie.kogito.app.jobs.api.JobTimeoutInterceptor;
import org.kie.kogito.app.jobs.integrations.ProcessInstanceJobDescriptionJobInstanceEventAdapter;
import org.kie.kogito.app.jobs.integrations.ProcessInstanceJobDescriptionMerger;
import org.kie.kogito.app.jobs.integrations.ProcessJobDescriptionJobInstanceEventAdapter;
import org.kie.kogito.app.jobs.integrations.ProcessJobDescriptionMerger;
import org.kie.kogito.app.jobs.integrations.UserTaskInstanceJobDescriptionJobInstanceEventAdapter;
import org.kie.kogito.app.jobs.integrations.UserTaskInstanceJobDescriptorMerger;
import org.kie.kogito.app.jobs.spi.JobContextFactory;
import org.kie.kogito.app.jobs.spi.JobStore;
import org.kie.kogito.app.jobs.spi.memory.MemoryJobContextFactory;
import org.kie.kogito.app.jobs.spi.memory.MemoryJobStore;
import org.kie.kogito.jobs.ExactExpirationTime;
import org.kie.kogito.jobs.JobDescription;
import org.kie.kogito.jobs.descriptors.ProcessInstanceJobDescription;
import org.kie.kogito.jobs.descriptors.ProcessJobDescription;
import org.kie.kogito.jobs.descriptors.UserTaskInstanceJobDescription;
import org.kie.kogito.process.impl.lock.ProcessInstanceAtomicLockStrategy;

import static org.assertj.core.api.Assertions.assertThat;

public class VertxJobSchedulerLockTest {

    /**
     * Interceptor Lock-Order Verification Test.
     * <p>
     * Verifies that the in-memory ProcessInstanceAtomicLockStrategy lock is acquired
     * by the timer thread BEFORE any JobTimeoutInterceptor (such as a transaction interceptor)
     * is invoked, and that the lock is cleanly released afterwards.
     */
    @Test
    public void testLockAcquiredBeforeInterceptorRuns() throws Exception {
        final String jobId = "lock-order-job-1";
        final String processInstanceId = "pi-lock-order-123";

        final AtomicBoolean lockHeldDuringInterceptor = new AtomicBoolean(false);
        final AtomicBoolean interceptorExecuted = new AtomicBoolean(false);

        JobTimeoutInterceptor mockTransactionInterceptor = new JobTimeoutInterceptor() {
            @Override
            public Callable<JobTimeoutExecution> chainIntercept(Callable<JobTimeoutExecution> callable) {
                return () -> {
                    // Simulates transaction begin: at this moment, ProcessInstanceAtomicLockStrategy lock
                    // MUST already be held by the current thread
                    boolean isLocked = ProcessInstanceAtomicLockStrategy.instance()
                            .isLockedByCurrentThread(processInstanceId);
                    lockHeldDuringInterceptor.set(isLocked);
                    interceptorExecuted.set(true);
                    return callable.call();
                };
            }
        };

        LatchExecutionJobSchedulerListener listener = new LatchExecutionJobSchedulerListener();
        TestJobExecutor executor = new TestJobExecutor();
        JobStore jobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();

        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withJobExecutors(executor)
                .withJobEventAdapters(new ProcessInstanceJobDescriptionJobInstanceEventAdapter("http://localhost:8080"))
                .withEventPublishers(new TestEventPublisher())
                .withJobContextFactory(jobContextFactory)
                .withJobStore(jobStore)
                .withJobSchedulerListeners(listener)
                .withJobDescriptorMergers(new ProcessInstanceJobDescriptionMerger())
                .withTimeoutInterceptor(mockTransactionInterceptor)
                .build();

        jobScheduler.init();

        ProcessInstanceJobDescription jobDescription = ProcessInstanceJobDescription.newProcessInstanceJobDescriptionBuilder()
                .id(jobId)
                .timerId("timer-1")
                .processId("testProcess")
                .processInstanceId(processInstanceId)
                .expirationTime(ExactExpirationTime.of(ZonedDateTime.now().plus(Duration.ofMillis(50))))
                .build();

        jobScheduler.schedule(jobDescription);
        listener.waitForExecution(3000L);

        assertThat(listener.isExecuted()).isTrue();
        assertThat(executor.getJobsExecuted()).contains(jobId);
        assertThat(interceptorExecuted.get())
                .as("Transaction interceptor must have been executed")
                .isTrue();
        assertThat(lockHeldDuringInterceptor.get())
                .as("ProcessInstance lock must already be held by current thread when transaction interceptor starts")
                .isTrue();
        assertThat(ProcessInstanceAtomicLockStrategy.instance().isLockedByCurrentThread(processInstanceId))
                .as("ProcessInstance lock must be released after timeout execution finishes")
                .isFalse();

        jobScheduler.close();
    }

    /**
     * Simulated Concurrent Lock Contention & Transaction Snapshot Isolation Test.
     * <p>
     * Verifies that when a concurrent thread holds the ProcessInstanceAtomicLockStrategy lock
     * and modifies the state (e.g. increments version), the Vert.x timer thread waits for the lock
     * BEFORE opening its transaction/interceptor, guaranteeing that the transaction observes
     * the latest committed state rather than a stale snapshot.
     */
    @Test
    public void testConcurrentLockContentionGuaranteesFreshTransactionSnapshot() throws Exception {
        final String jobId = "concurrent-lock-job-2";
        final String processInstanceId = "pi-concurrent-456";

        final AtomicInteger simulatedDbVersion = new AtomicInteger(1);
        final AtomicInteger versionObservedAtTxStart = new AtomicInteger(-1);

        JobTimeoutInterceptor mockTransactionInterceptor = new JobTimeoutInterceptor() {
            @Override
            public Callable<JobTimeoutExecution> chainIntercept(Callable<JobTimeoutExecution> callable) {
                return () -> {
                    // Record DB version observed at the exact moment the transaction starts
                    versionObservedAtTxStart.set(simulatedDbVersion.get());
                    return callable.call();
                };
            }
        };

        LatchExecutionJobSchedulerListener listener = new LatchExecutionJobSchedulerListener();
        TestJobExecutor executor = new TestJobExecutor();
        JobStore jobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();

        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withJobExecutors(executor)
                .withJobEventAdapters(new ProcessInstanceJobDescriptionJobInstanceEventAdapter("http://localhost:8080"))
                .withEventPublishers(new TestEventPublisher())
                .withJobContextFactory(jobContextFactory)
                .withJobStore(jobStore)
                .withJobSchedulerListeners(listener)
                .withJobDescriptorMergers(new ProcessInstanceJobDescriptionMerger())
                .withTimeoutInterceptor(mockTransactionInterceptor)
                .build();

        jobScheduler.init();

        CountDownLatch externalLockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch externalHoldLatch = new CountDownLatch(1);

        ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
        backgroundExecutor.submit(() -> {
            // Concurrent external thread (e.g. incoming REST request on same process instance)
            ProcessInstanceAtomicLockStrategy.instance().executeOperation(processInstanceId, () -> {
                externalLockAcquiredLatch.countDown();
                try {
                    // Hold lock and simulate external work
                    externalHoldLatch.await(2000L, TimeUnit.MILLISECONDS);
                    // Update state/version before releasing lock
                    simulatedDbVersion.set(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        // Wait until external thread holds the lock
        assertThat(externalLockAcquiredLatch.await(2000L, TimeUnit.MILLISECONDS)).isTrue();

        // Schedule timer to fire immediately while external thread is still holding lock
        ProcessInstanceJobDescription jobDescription = ProcessInstanceJobDescription.newProcessInstanceJobDescriptionBuilder()
                .id(jobId)
                .timerId("timer-2")
                .processId("testProcess")
                .processInstanceId(processInstanceId)
                .expirationTime(ExactExpirationTime.of(ZonedDateTime.now().plus(Duration.ofMillis(10))))
                .build();

        jobScheduler.schedule(jobDescription);

        // Sleep briefly to ensure timer fires and reaches the lock acquisition step
        Thread.sleep(100);

        // Allow external thread to update version to 2 and release the lock
        externalHoldLatch.countDown();
        backgroundExecutor.shutdown();
        backgroundExecutor.awaitTermination(2000L, TimeUnit.MILLISECONDS);

        // Wait for job execution to complete
        listener.waitForExecution(3000L);

        assertThat(listener.isExecuted()).isTrue();
        assertThat(executor.getJobsExecuted()).contains(jobId);

        // Verification: The timer thread must have waited for the lock BEFORE starting the transaction interceptor.
        // Thus, when the transaction started, it observed the updated version (2), NOT the stale version (1).
        assertThat(versionObservedAtTxStart.get())
                .as("Transaction must start AFTER lock is acquired, observing latest state (version 2)")
                .isEqualTo(2);

        jobScheduler.close();
    }

    /**
     * Verifies that jobs without correlationId (e.g. standard non-process jobs)
     * continue to execute normally without lock strategy overhead.
     */
    @Test
    public void testJobWithoutCorrelationIdExecutesNormally() throws Exception {
        final String jobId = "no-correlation-job-3";
        LatchExecutionJobSchedulerListener listener = new LatchExecutionJobSchedulerListener();
        TestJobExecutor executor = new TestJobExecutor();
        JobStore jobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();

        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withJobExecutors(executor)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(new TestEventPublisher())
                .withJobContextFactory(jobContextFactory)
                .withJobStore(jobStore)
                .withJobSchedulerListeners(listener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .build();

        jobScheduler.init();
        jobScheduler.schedule(new TestJobDescription(jobId, ZonedDateTime.now().plus(Duration.ofMillis(50))));
        listener.waitForExecution(3000L);

        assertThat(listener.isExecuted()).isTrue();
        assertThat(executor.getJobsExecuted()).contains(jobId);

        jobScheduler.close();
    }

    /**
     * User task timers must lock on the owning process instance id, not on the job id
     * (which is what the job correlationId holds for this kind of job).
     */
    @Test
    public void testUserTaskJobLocksOnProcessInstanceId() throws Exception {
        final String jobId = "usertask-job-4";
        final String processInstanceId = "pi-usertask-789";

        UserTaskInstanceJobDescription jobDescription = UserTaskInstanceJobDescription.newUserTaskInstanceJobDescriptionBuilder()
                .id(jobId)
                .userTaskInstanceId("ut-1")
                .metadata(new java.util.HashMap<>())
                .processId("testProcess")
                .processInstanceId(processInstanceId)
                .expirationTime(ExactExpirationTime.of(ZonedDateTime.now().plus(Duration.ofMillis(50))))
                .build();

        LockObservation observation = runAndObserveLocks(jobDescription,
                new UserTaskInstanceJobDescriptionJobInstanceEventAdapter("http://localhost:8080"),
                new UserTaskInstanceJobDescriptorMerger(),
                processInstanceId, jobId);

        assertThat(observation.executed).isTrue();
        assertThat(observation.lockedKey).as("process instance lock must be held for user task jobs").isTrue();
        assertThat(observation.lockedOtherKey).as("job id must not be used as lock key").isFalse();
    }

    /**
     * Process definition timers (e.g. start timers) are not bound to any process instance,
     * so no lock must be taken, in particular not on the process id.
     */
    @Test
    public void testProcessDefinitionJobIsNotLocked() throws Exception {
        final String processId = "testProcessDefinition";

        ProcessJobDescription jobDescription = ProcessJobDescription.of(
                ExactExpirationTime.of(ZonedDateTime.now().plus(Duration.ofMillis(50))), processId, "1.0");

        LockObservation observation = runAndObserveLocks(jobDescription,
                new ProcessJobDescriptionJobInstanceEventAdapter("http://localhost:8080"),
                new ProcessJobDescriptionMerger(),
                processId, jobDescription.id());

        assertThat(observation.executed).isTrue();
        assertThat(observation.lockedKey).as("process definition jobs must not lock on the process id").isFalse();
        assertThat(observation.lockedOtherKey).as("process definition jobs must not lock on the job id").isFalse();
    }

    private static class LockObservation {
        boolean executed;
        boolean lockedKey;
        boolean lockedOtherKey;
    }

    private LockObservation runAndObserveLocks(JobDescription jobDescription, JobDetailsEventAdapter adapter, JobDescriptionMerger merger,
            String key, String otherKey) throws Exception {
        LockObservation observation = new LockObservation();

        JobTimeoutInterceptor observingInterceptor = callable -> () -> {
            observation.lockedKey = ProcessInstanceAtomicLockStrategy.instance().isLockedByCurrentThread(key);
            observation.lockedOtherKey = ProcessInstanceAtomicLockStrategy.instance().isLockedByCurrentThread(otherKey);
            return callable.call();
        };

        LatchExecutionJobSchedulerListener listener = new LatchExecutionJobSchedulerListener();
        TestJobExecutor executor = new TestJobExecutor();

        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withJobExecutors(executor)
                .withJobEventAdapters(adapter)
                .withEventPublishers(new TestEventPublisher())
                .withJobContextFactory(new MemoryJobContextFactory())
                .withJobStore(new MemoryJobStore())
                .withJobSchedulerListeners(listener)
                .withJobDescriptorMergers(merger)
                .withTimeoutInterceptor(observingInterceptor)
                .build();

        try {
            jobScheduler.init();
            jobScheduler.schedule(jobDescription);
            listener.waitForExecution(3000L);
            observation.executed = listener.isExecuted() && executor.getJobsExecuted().contains(jobDescription.id());
        } finally {
            jobScheduler.close();
        }
        return observation;
    }
}
