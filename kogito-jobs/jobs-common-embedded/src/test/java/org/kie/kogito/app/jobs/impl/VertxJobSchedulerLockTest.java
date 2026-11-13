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

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.app.jobs.api.JobDescriptionMerger;
import org.kie.kogito.app.jobs.api.JobDetailsEventAdapter;
import org.kie.kogito.app.jobs.api.JobExecutor;
import org.kie.kogito.app.jobs.api.JobScheduler;
import org.kie.kogito.app.jobs.api.JobSchedulerBuilder;
import org.kie.kogito.app.jobs.api.JobTimeoutInterceptor;
import org.kie.kogito.app.jobs.integrations.ProcessInstanceJobDescriptionJobInstanceEventAdapter;
import org.kie.kogito.app.jobs.integrations.ProcessInstanceJobDescriptionMerger;
import org.kie.kogito.app.jobs.integrations.ProcessJobDescriptionJobInstanceEventAdapter;
import org.kie.kogito.app.jobs.integrations.ProcessJobDescriptionMerger;
import org.kie.kogito.app.jobs.integrations.UserTaskInstanceJobDescriptionJobInstanceEventAdapter;
import org.kie.kogito.app.jobs.integrations.UserTaskInstanceJobDescriptorMerger;
import org.kie.kogito.app.jobs.spi.JobContext;
import org.kie.kogito.app.jobs.spi.JobStore;
import org.kie.kogito.app.jobs.spi.memory.MemoryJobContextFactory;
import org.kie.kogito.app.jobs.spi.memory.MemoryJobStore;
import org.kie.kogito.jobs.ExactExpirationTime;
import org.kie.kogito.jobs.JobDescription;
import org.kie.kogito.jobs.descriptors.ProcessInstanceJobDescription;
import org.kie.kogito.jobs.descriptors.ProcessJobDescription;
import org.kie.kogito.jobs.descriptors.UserTaskInstanceJobDescription;
import org.kie.kogito.jobs.service.model.JobDetails;
import org.kie.kogito.process.impl.lock.ProcessInstanceAtomicLockStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class VertxJobSchedulerLockTest {

    private static final String SERVICE_URL = "http://localhost:8080";

    private JobScheduler jobScheduler;

    @AfterEach
    void closeScheduler() {
        if (jobScheduler != null) {
            jobScheduler.close();
            jobScheduler = null;
        }
    }

    /**
     * Records what the Vert.x worker thread sees when a transaction-like interceptor starts.
     */
    private static class LockObservation {
        final AtomicBoolean executed = new AtomicBoolean();
        final AtomicBoolean lockedKey = new AtomicBoolean();
        final AtomicBoolean lockedOtherKey = new AtomicBoolean();
    }

    private JobScheduler newScheduler(JobStore jobStore, JobDetailsEventAdapter adapter, JobDescriptionMerger merger,
            LatchExecutionJobSchedulerListener listener, JobExecutor executor, JobTimeoutInterceptor... interceptors) {
        jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withJobExecutors(executor)
                .withJobEventAdapters(adapter)
                .withEventPublishers(new TestEventPublisher())
                .withJobContextFactory(new MemoryJobContextFactory())
                .withJobStore(jobStore)
                .withJobSchedulerListeners(listener)
                .withJobDescriptorMergers(merger)
                .withTimeoutInterceptor(interceptors)
                .build();
        jobScheduler.init();
        return jobScheduler;
    }

    /**
     * Schedules the job and observes, from the worker thread and at the point where a transaction interceptor would start,
     * whether the lock for {@code key} and for {@code otherKey} is held. Then verifies the lock for {@code key} was released.
     */
    private LockObservation runAndObserveLocks(JobDescription jobDescription, JobDetailsEventAdapter adapter, JobDescriptionMerger merger,
            String key, String otherKey) throws Exception {
        LockObservation observation = new LockObservation();
        ProcessInstanceAtomicLockStrategy locks = ProcessInstanceAtomicLockStrategy.instance();

        JobTimeoutInterceptor observingInterceptor = callable -> () -> {
            observation.lockedKey.set(locks.isLockedByCurrentThread(key));
            observation.lockedOtherKey.set(locks.isLockedByCurrentThread(otherKey));
            return callable.call();
        };

        LatchExecutionJobSchedulerListener listener = new LatchExecutionJobSchedulerListener();
        TestJobExecutor executor = new TestJobExecutor();
        newScheduler(new MemoryJobStore(), adapter, merger, listener, executor, observingInterceptor);

        jobScheduler.schedule(jobDescription);
        listener.waitForExecution(3000L);
        observation.executed.set(listener.isExecuted() && executor.getJobsExecuted().contains(jobDescription.id()));

        // the lock must not leak: it can be acquired from another thread once the timeout task finished
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> locks.executeOperation(key, () -> null));
        return observation;
    }

    private static ProcessInstanceJobDescription processInstanceJob(String jobId, String processInstanceId, ZonedDateTime when) {
        return ProcessInstanceJobDescription.newProcessInstanceJobDescriptionBuilder()
                .id(jobId)
                .timerId("timer-" + jobId)
                .processId("testProcess")
                .processInstanceId(processInstanceId)
                .expirationTime(ExactExpirationTime.of(when))
                .build();
    }

    /**
     * The lock for the process instance must already be held by the worker thread when the transaction interceptor starts.
     */
    @Test
    void testProcessInstanceJobLocksBeforeInterceptorRuns() throws Exception {
        final String jobId = "lock-order-job-1";
        final String processInstanceId = "pi-lock-order-123";

        LockObservation observation = runAndObserveLocks(
                processInstanceJob(jobId, processInstanceId, ZonedDateTime.now().plus(Duration.ofMillis(50))),
                new ProcessInstanceJobDescriptionJobInstanceEventAdapter(SERVICE_URL),
                new ProcessInstanceJobDescriptionMerger(),
                processInstanceId, jobId);

        assertThat(observation.executed).isTrue();
        assertThat(observation.lockedKey).as("process instance lock must be held when the transaction interceptor starts").isTrue();
        assertThat(observation.lockedOtherKey).as("job id must not be used as lock key").isFalse();
    }

    /**
     * User task timers must lock on the owning process instance id, not on the job id
     * (which is what the job correlationId holds for this kind of job).
     */
    @Test
    void testUserTaskJobLocksOnProcessInstanceId() throws Exception {
        final String jobId = "usertask-job-4";
        final String processInstanceId = "pi-usertask-789";

        UserTaskInstanceJobDescription jobDescription = UserTaskInstanceJobDescription.newUserTaskInstanceJobDescriptionBuilder()
                .id(jobId)
                .userTaskInstanceId("ut-1")
                .metadata(new HashMap<>())
                .processId("testProcess")
                .processInstanceId(processInstanceId)
                .expirationTime(ExactExpirationTime.of(ZonedDateTime.now().plus(Duration.ofMillis(50))))
                .build();

        LockObservation observation = runAndObserveLocks(jobDescription,
                new UserTaskInstanceJobDescriptionJobInstanceEventAdapter(SERVICE_URL),
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
    void testProcessDefinitionJobIsNotLocked() throws Exception {
        final String processId = "testProcessDefinition";

        ProcessJobDescription jobDescription = ProcessJobDescription.of(
                ExactExpirationTime.of(ZonedDateTime.now().plus(Duration.ofMillis(50))), processId, "1.0");

        LockObservation observation = runAndObserveLocks(jobDescription,
                new ProcessJobDescriptionJobInstanceEventAdapter(SERVICE_URL),
                new ProcessJobDescriptionMerger(),
                processId, jobDescription.id());

        assertThat(observation.executed).isTrue();
        assertThat(observation.lockedKey).as("process definition jobs must not lock on the process id").isFalse();
        assertThat(observation.lockedOtherKey).as("process definition jobs must not lock on the job id").isFalse();
    }

    /**
     * Jobs that are not related to processes at all must not take any lock.
     */
    @Test
    void testJobWithoutProcessInstanceIsNotLocked() throws Exception {
        final String jobId = "no-process-job-3";

        LockObservation observation = runAndObserveLocks(
                new TestJobDescription(jobId, ZonedDateTime.now().plus(Duration.ofMillis(50))),
                new TestJobDetailsEventAdapter(),
                new TestJobDescriptionMerger(),
                jobId, jobId);

        assertThat(observation.executed).isTrue();
        assertThat(observation.lockedKey).as("jobs without a process instance must not be locked").isFalse();
    }

    /**
     * While another thread holds the process instance lock (e.g. an incoming REST request), the timer thread must wait for it
     * BEFORE the transaction interceptor starts, so the transaction observes the latest committed state rather than a stale snapshot.
     */
    @Test
    void testConcurrentLockContentionGuaranteesFreshTransactionSnapshot() throws Exception {
        final String jobId = "concurrent-lock-job-2";
        final String processInstanceId = "pi-concurrent-456";

        final AtomicInteger simulatedDbVersion = new AtomicInteger(1);
        final AtomicInteger versionObservedAtTxStart = new AtomicInteger(-1);
        final CountDownLatch jobExecuted = new CountDownLatch(1);
        // the periodic job store sync goes through the interceptors too, so the job itself is what tells us which transaction is relevant
        final ThreadLocal<Integer> versionAtCurrentTxStart = new ThreadLocal<>();

        JobTimeoutInterceptor mockTransactionInterceptor = callable -> () -> {
            // the DB version observed at the exact moment the transaction starts
            versionAtCurrentTxStart.set(simulatedDbVersion.get());
            try {
                return callable.call();
            } finally {
                versionAtCurrentTxStart.remove();
            }
        };

        JobExecutor executor = new JobExecutor() {
            @Override
            public boolean accept(JobDetails jobDetails) {
                return true;
            }

            @Override
            public void execute(JobDetails jobDetails) {
                versionObservedAtTxStart.set(versionAtCurrentTxStart.get());
                jobExecuted.countDown();
            }
        };

        LatchExecutionJobSchedulerListener listener = new LatchExecutionJobSchedulerListener();
        newScheduler(new MemoryJobStore(), new ProcessInstanceJobDescriptionJobInstanceEventAdapter(SERVICE_URL),
                new ProcessInstanceJobDescriptionMerger(), listener, executor, mockTransactionInterceptor);

        CountDownLatch externalLockAcquired = new CountDownLatch(1);
        CountDownLatch externalRelease = new CountDownLatch(1);

        ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
        try {
            Future<?> external = backgroundExecutor.submit(() -> ProcessInstanceAtomicLockStrategy.instance().executeOperation(processInstanceId, () -> {
                externalLockAcquired.countDown();
                try {
                    externalRelease.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // update the state/version before releasing the lock
                simulatedDbVersion.set(2);
                return null;
            }));

            assertThat(externalLockAcquired.await(2, TimeUnit.SECONDS)).isTrue();

            // the timer fires while the external thread still holds the lock
            jobScheduler.schedule(processInstanceJob(jobId, processInstanceId, ZonedDateTime.now().plus(Duration.ofMillis(10))));

            // the job (and so its transaction) must not run while the lock is held by someone else
            assertThat(jobExecuted.await(300, TimeUnit.MILLISECONDS))
                    .as("job must not run while the process instance lock is held by another thread")
                    .isFalse();

            externalRelease.countDown();
            external.get(3, TimeUnit.SECONDS);
        } finally {
            externalRelease.countDown();
            backgroundExecutor.shutdownNow();
        }

        assertThat(jobExecuted.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(versionObservedAtTxStart.get())
                .as("transaction must start AFTER the lock is acquired, observing the latest state (version 2)")
                .isEqualTo(2);
    }

    /**
     * A timer that finds its job should not run (e.g. it was rescheduled in the meantime) must only drop its own entry
     * from the scheduled jobs, not the entry of the timer that replaced it under the same key.
     */
    @Test
    void testShouldNotRunDoesNotRemoveEntryOfRescheduledTimer() throws Exception {
        final String jobId = "reschedule-job-5";
        final String processInstanceId = "pi-reschedule-321";

        CountDownLatch shouldRunEntered = new CountDownLatch(1);
        CountDownLatch shouldRunRelease = new CountDownLatch(1);
        AtomicBoolean firstCall = new AtomicBoolean(true);

        // the first timeout is held inside shouldRun and then told not to run, later calls behave normally
        JobStore blockingStore = new MemoryJobStore() {
            @Override
            public boolean shouldRun(JobContext jobContext, String id) {
                if (firstCall.compareAndSet(true, false)) {
                    shouldRunEntered.countDown();
                    try {
                        shouldRunRelease.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return false;
                }
                return super.shouldRun(jobContext, id);
            }
        };

        newScheduler(blockingStore, new ProcessInstanceJobDescriptionJobInstanceEventAdapter(SERVICE_URL),
                new ProcessInstanceJobDescriptionMerger(), new LatchExecutionJobSchedulerListener(), new TestJobExecutor());

        jobScheduler.schedule(processInstanceJob(jobId, processInstanceId, ZonedDateTime.now().plus(Duration.ofMillis(50))));
        assertThat(shouldRunEntered.await(3, TimeUnit.SECONDS)).isTrue();

        // while the first timer is still in flight, the job is rescheduled and gets a new timer under the same key
        jobScheduler.reschedule(processInstanceJob(jobId, processInstanceId, ZonedDateTime.now().plus(Duration.ofMinutes(10))));
        shouldRunRelease.countDown();

        Map<String, ?> jobsScheduled = jobsScheduled();
        String mapKey = jobId + "-0";
        assertThat(jobsScheduled).containsKey(mapKey);
        // the first timeout has now completed its "should not run" handling, the entry of the rescheduled timer must survive
        await().during(500, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(jobsScheduled).containsKey(mapKey));
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> jobsScheduled() throws Exception {
        Field field = VertxJobScheduler.class.getDeclaredField("jobsScheduled");
        field.setAccessible(true);
        return (Map<String, ?>) field.get(jobScheduler);
    }
}
