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
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kie.kogito.app.jobs.api.JobScheduler;
import org.kie.kogito.app.jobs.api.JobSchedulerBuilder;
import org.kie.kogito.app.jobs.integrations.DefaultJobExceptionDetailsExtractor;
import org.kie.kogito.app.jobs.spi.JobContextFactory;
import org.kie.kogito.app.jobs.spi.JobStore;
import org.kie.kogito.app.jobs.spi.memory.MemoryJobContextFactory;
import org.kie.kogito.app.jobs.spi.memory.MemoryJobStore;
import org.kie.kogito.jobs.DurationExpirationTime;
import org.kie.kogito.jobs.ExactExpirationTime;
import org.kie.kogito.jobs.ExpirationTime;
import org.kie.kogito.jobs.service.model.JobDetails;
import org.kie.kogito.jobs.service.model.JobStatus;
import org.kie.kogito.jobs.service.model.ScheduledJob;
import org.kie.kogito.timer.impl.SimpleTimerTrigger;

import static org.assertj.core.api.Assertions.assertThat;

public class VertxJobSchedulerTest {

    @Test
    public void testBasicExactTimeFlow() throws Exception {
        final String jobId = "1";
        LatchExecutionJobSchedulerListener latchExecutionJobSchedulerListener = new LatchExecutionJobSchedulerListener();
        TestJobExecutor latchJobExecutor = new TestJobExecutor();
        JobStore memoryJobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();
        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withJobExecutors(latchJobExecutor)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(new TestEventPublisher())
                .withJobContextFactory(jobContextFactory)
                .withJobStore(memoryJobStore)
                .withJobSchedulerListeners(latchExecutionJobSchedulerListener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .build();
        jobScheduler.init();
        jobScheduler.schedule(new TestJobDescription(jobId, ZonedDateTime.now().plus(Duration.ofSeconds(1))));
        latchExecutionJobSchedulerListener.waitForExecution();
        assertThat(latchJobExecutor.getJobsExecuted()).hasSize(1);
        assertThat(memoryJobStore.find(jobContextFactory.newContext(), jobId)).isNull();
        assertThat(latchExecutionJobSchedulerListener.isExecuted()).isTrue();
        jobScheduler.close();
    }

    @Test
    public void testBasicPeriodicLimitedTimeFlow() throws Exception {
        final String jobId = "1";
        JobStore memoryJobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();
        LatchExecutionJobSchedulerListener latchExecutionJobSchedulerListener = new LatchExecutionJobSchedulerListener(3);
        TestJobExecutor latchJobExecutor = new TestJobExecutor();
        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withJobExecutors(latchJobExecutor)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(new TestEventPublisher())
                .withJobContextFactory(jobContextFactory)
                .withJobStore(memoryJobStore)
                .withJobSchedulerListeners(latchExecutionJobSchedulerListener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .build();
        jobScheduler.init();
        ExpirationTime expirationTime = DurationExpirationTime.repeat(0, 1000L, 3);
        jobScheduler.schedule(new TestJobDescription("1", expirationTime));

        latchExecutionJobSchedulerListener.waitForExecution();
        // there should not be any job left in database
        assertThat(latchJobExecutor.getJobsExecuted()).hasSize(3);
        assertThat(memoryJobStore.find(jobContextFactory.newContext(), jobId)).isNull();
        assertThat(latchExecutionJobSchedulerListener.isExecuted()).isTrue();
        jobScheduler.close();
    }

    @Test
    public void testBasicPeriodicUnlimitedTimeFlow() throws Exception {
        final String jobId = "1";
        JobStore memoryJobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();
        LatchExecutionJobSchedulerListener latchExecutionJobSchedulerListener = new LatchExecutionJobSchedulerListener(6);
        TestJobExecutor latchJobExecutor = new TestJobExecutor();
        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withJobExecutors(latchJobExecutor)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(new TestEventPublisher())
                .withJobContextFactory(jobContextFactory)
                .withJobStore(memoryJobStore)
                .withJobSchedulerListeners(latchExecutionJobSchedulerListener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .build();
        jobScheduler.init();
        ExpirationTime expirationTime = DurationExpirationTime.repeat(0, 1000L, SimpleTimerTrigger.INDEFINITELY);
        jobScheduler.schedule(new TestJobDescription(jobId, expirationTime));

        latchExecutionJobSchedulerListener.waitForExecution();
        assertThat(latchJobExecutor.getJobsExecuted()).hasSize(6);
        assertThat(memoryJobStore.find(jobContextFactory.newContext(), jobId)).isNotNull();
        assertThat(latchExecutionJobSchedulerListener.isExecuted()).isTrue();
        jobScheduler.close();
    }

    @Test
    public void testCancelJob() throws Exception {
        final String jobId = "1";
        JobStore memoryJobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();
        TestJobExecutor latchJobExecutor = new TestJobExecutor();
        LatchExecutionJobSchedulerListener latchExecutionJobSchedulerListener = new LatchExecutionJobSchedulerListener();
        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder().withJobExecutors(latchJobExecutor)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(new TestEventPublisher())
                .withJobContextFactory(jobContextFactory)
                .withJobStore(memoryJobStore)
                .withJobSchedulerListeners(latchExecutionJobSchedulerListener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .build();
        jobScheduler.init();

        jobScheduler.schedule(new TestJobDescription(jobId, ZonedDateTime.now().plus(Duration.ofSeconds(10))));
        assertThat(memoryJobStore.find(jobContextFactory.newContext(), jobId)).isNotNull();
        jobScheduler.cancel(jobId);
        assertThat(memoryJobStore.find(jobContextFactory.newContext(), jobId)).isNull();
        assertThat(latchExecutionJobSchedulerListener.isExecuted()).isFalse();
        jobScheduler.close();
    }

    @Test
    public void testRescheduleJob() throws Exception {
        final String jobId = "1";
        JobStore memoryJobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();
        TestJobExecutor latchJobExecutor = new TestJobExecutor();
        LatchExecutionJobSchedulerListener latchExecutionJobSchedulerListener = new LatchExecutionJobSchedulerListener();
        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder().withJobExecutors(latchJobExecutor)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(new TestEventPublisher())
                .withJobContextFactory(jobContextFactory)
                .withJobStore(memoryJobStore)
                .withJobSchedulerListeners(latchExecutionJobSchedulerListener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .build();
        jobScheduler.init();

        jobScheduler.schedule(new TestJobDescription(jobId, ZonedDateTime.now().plus(Duration.ofSeconds(10))));
        assertThat(memoryJobStore.find(jobContextFactory.newContext(), jobId)).isNotNull();
        jobScheduler.reschedule(new TestJobDescription(jobId, ZonedDateTime.now().plus(Duration.ofSeconds(1))));
        latchExecutionJobSchedulerListener.waitForExecution(2000L);
        assertThat(memoryJobStore.find(jobContextFactory.newContext(), jobId)).isNull();
        assertThat(latchExecutionJobSchedulerListener.isExecuted()).isTrue();
        jobScheduler.close();
    }

    @Test
    public void testRetryTillErrorJob() throws Exception {
        final String jobId = "1";
        JobStore memoryJobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();
        TestFailureJobExecutor latchJobExecutor = new TestFailureJobExecutor(3);
        LatchExecutionJobSchedulerListener latchExecutionJobSchedulerListener = new LatchExecutionJobSchedulerListener();
        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withJobExecutors(latchJobExecutor)
                .withRetryInterval(1000L)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(new TestEventPublisher())
                .withJobContextFactory(jobContextFactory)
                .withJobStore(memoryJobStore)
                .withJobSchedulerListeners(latchExecutionJobSchedulerListener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .build();
        jobScheduler.init();

        jobScheduler.schedule(new TestJobDescription(jobId, ZonedDateTime.now().plus(Duration.ofSeconds(1))));
        assertThat(memoryJobStore.find(jobContextFactory.newContext(), jobId)).isNotNull();
        latchExecutionJobSchedulerListener.waitForExecution(5000L);
        JobDetails jobDetails = memoryJobStore.find(jobContextFactory.newContext(), jobId);
        assertThat(jobDetails).isNull();
        assertThat(latchExecutionJobSchedulerListener.isExecuted()).isTrue();
        jobScheduler.close();
    }

    @Test
    public void testRetryBeforeErrorJob() throws Exception {
        final String jobId = "1";
        JobStore memoryJobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();
        TestFailureJobExecutor latchJobExecutor = new TestFailureJobExecutor(2);
        LatchExecutionJobSchedulerListener latchExecutionJobSchedulerListener = new LatchExecutionJobSchedulerListener();
        TestEventPublisher eventPublisher = new TestEventPublisher();
        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withJobExecutors(latchJobExecutor)
                .withRetryInterval(1000L)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(eventPublisher)
                .withJobContextFactory(jobContextFactory)
                .withJobStore(memoryJobStore)
                .withJobSchedulerListeners(latchExecutionJobSchedulerListener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .build();
        jobScheduler.init();

        jobScheduler.schedule(new TestJobDescription(jobId, ZonedDateTime.now().plus(Duration.ofSeconds(1))));
        latchExecutionJobSchedulerListener.waitForExecution();
        assertThat(memoryJobStore.find(jobContextFactory.newContext(), jobId)).isNull();
        assertThat(latchExecutionJobSchedulerListener.isExecuted()).isTrue();
        jobScheduler.close();
    }

    @Test
    @org.junit.jupiter.api.Disabled("Flaky under parallel builds (-T 2): timing-sensitive exact time assertion")
    public void testExactTime() throws Exception {
        final String jobId = "1";
        LatchExecutionJobSchedulerListener latchExecutionJobSchedulerListener = new LatchExecutionJobSchedulerListener();
        TestJobExecutor latchJobExecutor = new TestJobExecutor();
        JobStore memoryJobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();
        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withJobExecutors(latchJobExecutor)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(new TestEventPublisher())
                .withJobContextFactory(jobContextFactory)
                .withJobStore(memoryJobStore)
                .withJobSchedulerListeners(latchExecutionJobSchedulerListener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .withRefreshJobsInterval(100000L)
                .build();
        jobScheduler.init();
        ExpirationTime expirationTime = ExactExpirationTime.of(ZonedDateTime.now().plus(1, ChronoUnit.MILLIS));
        jobScheduler.schedule(new TestJobDescription(jobId, expirationTime));
        latchExecutionJobSchedulerListener.waitForExecution(1000L);
        assertThat(latchJobExecutor.getJobsExecuted()).hasSize(1);
        assertThat(memoryJobStore.find(jobContextFactory.newContext(), jobId)).isNull();
        assertThat(latchExecutionJobSchedulerListener.isExecuted()).isTrue();
        jobScheduler.close();

    }

    @Test
    public void testNumberOfRetries() throws Exception {
        final int NUMBER_OF_FAILURES = 4; // first execution + number of retries
        final int NUMBER_OF_RETRIES = NUMBER_OF_FAILURES - 1;

        final String jobId = "1";
        JobStore memoryJobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();
        TestFailureJobExecutor latchJobExecutor = new TestFailureJobExecutor(NUMBER_OF_FAILURES);
        LatchFailureJobSchedulerListener latchExecutionJobSchedulerListener = new LatchFailureJobSchedulerListener(NUMBER_OF_FAILURES);
        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withMaxNumberOfRetries(NUMBER_OF_RETRIES)
                .withJobExecutors(latchJobExecutor)
                .withRetryInterval(1000L)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(new TestEventPublisher())
                .withJobContextFactory(jobContextFactory)
                .withJobStore(memoryJobStore)
                .withJobSchedulerListeners(latchExecutionJobSchedulerListener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .build();

        jobScheduler.init();

        jobScheduler.schedule(new TestJobDescription(jobId, ZonedDateTime.now().plus(Duration.ofSeconds(1))));
        latchExecutionJobSchedulerListener.waitForExecution();
        assertThat(memoryJobStore.find(jobContextFactory.newContext(), jobId)).isNotNull().extracting(JobDetails::getStatus).isEqualTo(JobStatus.ERROR);

        jobScheduler.close();
    }

    @Test
    public void testEventPublishedOnErrorWithNoRetries() throws Exception {

        int EXPECTED_EVENTS = 3; // SCHEDULED, RUNNING, ERROR

        final String jobId = "1";
        JobStore memoryJobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();
        TestFailureJobExecutor latchJobExecutor = new TestFailureJobExecutor(1);
        TestEventPublisher eventPublisher = new TestEventPublisher();
        LatchFailureJobSchedulerListener latchExecutionJobSchedulerListener = new LatchFailureJobSchedulerListener(1);
        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withMaxNumberOfRetries(0)
                .withJobExecutors(latchJobExecutor)
                .withRetryInterval(1000L)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(eventPublisher)
                .withJobContextFactory(jobContextFactory)
                .withJobStore(memoryJobStore)
                .withJobSchedulerListeners(latchExecutionJobSchedulerListener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .build();

        jobScheduler.init();

        jobScheduler.schedule(new TestJobDescription(jobId, ZonedDateTime.now().plus(Duration.ofSeconds(1))));
        latchExecutionJobSchedulerListener.waitForExecution();
        assertThat(eventPublisher.getPublishedEventsCount()).isEqualTo(EXPECTED_EVENTS);
        assertThat(memoryJobStore.find(jobContextFactory.newContext(), jobId)).isNotNull().extracting(JobDetails::getStatus).isEqualTo(JobStatus.ERROR);

        jobScheduler.close();
    }

    @Test
    public void testEventPublishedOnErrorWithRetry() throws Exception {
        final int NUMBER_OF_FAILURES = 2; // first execution + number of retries
        final int NUMBER_OF_RETRIES = NUMBER_OF_FAILURES - 1;
        int EXPECTED_EVENTS = 5; // SCHEDULED, RUNNING, RETRY, RUNNING, ERROR

        final String jobId = "1";
        JobStore memoryJobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();
        TestFailureJobExecutor latchJobExecutor = new TestFailureJobExecutor(NUMBER_OF_FAILURES);
        TestEventPublisher eventPublisher = new TestEventPublisher();
        LatchFailureJobSchedulerListener latchExecutionJobSchedulerListener = new LatchFailureJobSchedulerListener(NUMBER_OF_FAILURES);
        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withMaxNumberOfRetries(NUMBER_OF_RETRIES)
                .withJobExecutors(latchJobExecutor)
                .withRetryInterval(1000L)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(eventPublisher)
                .withJobContextFactory(jobContextFactory)
                .withJobStore(memoryJobStore)
                .withJobSchedulerListeners(latchExecutionJobSchedulerListener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .build();

        jobScheduler.init();

        jobScheduler.schedule(new TestJobDescription(jobId, ZonedDateTime.now().plus(Duration.ofSeconds(1))));
        latchExecutionJobSchedulerListener.waitForExecution();
        assertThat(eventPublisher.getPublishedEventsCount()).isEqualTo(EXPECTED_EVENTS);
        assertThat(memoryJobStore.find(jobContextFactory.newContext(), jobId)).isNotNull().extracting(JobDetails::getStatus).isEqualTo(JobStatus.ERROR);

        jobScheduler.close();
    }

    @Test
    public void testBasicOverdueTime() throws Exception {
        final String jobId = "1";
        JobStore memoryJobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();

        TestJobDescription testDescription = new TestJobDescription(jobId, ZonedDateTime.now().minus(Duration.ofSeconds(1)));
        memoryJobStore.persist(jobContextFactory.newContext(), JobDetailsHelper.newScheduledJobDetails(testDescription));

        LatchExecutionJobSchedulerListener latchExecutionJobSchedulerListener = new LatchExecutionJobSchedulerListener();
        TestJobExecutor latchJobExecutor = new TestJobExecutor();

        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withJobExecutors(latchJobExecutor)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(new TestEventPublisher())
                .withJobContextFactory(jobContextFactory)
                .withJobStore(memoryJobStore)
                .withJobSchedulerListeners(latchExecutionJobSchedulerListener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .build();
        jobScheduler.init();
        latchExecutionJobSchedulerListener.waitForExecution();
        assertThat(latchJobExecutor.getJobsExecuted()).hasSize(1);
        assertThat(memoryJobStore.find(jobContextFactory.newContext(), jobId)).isNull();
        assertThat(latchExecutionJobSchedulerListener.isExecuted()).isTrue();
        jobScheduler.close();
    }

    @Test
    public void testExceptionDetailsPropagationThroughEvents() throws Exception {
        // Given: A job scheduler configured with event capture and retry
        final String jobId = "exception-test-1";
        final int NUMBER_OF_FAILURES = 2; // First execution + 1 retry
        final int NUMBER_OF_RETRIES = NUMBER_OF_FAILURES - 1;

        JobStore memoryJobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();
        TestFailureJobExecutor failureExecutor = new TestFailureJobExecutor(NUMBER_OF_FAILURES);
        TestEventPublisherWithCapture eventPublisher = new TestEventPublisherWithCapture();
        LatchFailureJobSchedulerListener latchListener = new LatchFailureJobSchedulerListener(NUMBER_OF_FAILURES);

        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withMaxNumberOfRetries(NUMBER_OF_RETRIES)
                .withJobExecutors(failureExecutor)
                .withRetryInterval(1000L)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(eventPublisher)
                .withJobContextFactory(jobContextFactory)
                .withJobStore(memoryJobStore)
                .withJobSchedulerListeners(latchListener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .withExceptionDetailsExtractor(new DefaultJobExceptionDetailsExtractor())
                .build();

        jobScheduler.init();

        // When: Schedule a job that will fail twice (initial + 1 retry)
        jobScheduler.schedule(new TestJobDescription(jobId, ZonedDateTime.now().plus(Duration.ofSeconds(1))));
        latchListener.waitForExecution();

        // Then: Verify the job ended in ERROR status
        JobDetails jobDetails = memoryJobStore.find(jobContextFactory.newContext(), jobId);
        assertThat(jobDetails).isNotNull();
        assertThat(jobDetails.getStatus()).isEqualTo(JobStatus.ERROR);
        assertThat(jobDetails.getExceptionDetails()).isNotNull();
        assertThat(jobDetails.getExceptionDetails().exceptionMessage()).contains("RuntimeException");
        assertThat(jobDetails.getExceptionDetails().exceptionDetails()).contains("Failure expected");

        // And: Verify exception details were propagated through events
        List<ScheduledJob> capturedJobs = eventPublisher.getCapturedJobs();
        assertThat(capturedJobs).isNotEmpty();

        // Find RETRY status events - should have exception details
        List<ScheduledJob> retryJobs = capturedJobs.stream()
                .filter(job -> "RETRY".equals(job.getStatus().name()))
                .toList();

        assertThat(retryJobs).isNotEmpty()
                .as("Should have at least one RETRY event");

        for (ScheduledJob retryJob : retryJobs) {
            assertThat(retryJob.getExceptionDetails())
                    .as("RETRY status event should contain exception details")
                    .isNotNull();
            assertThat(retryJob.getExceptionMessage())
                    .as("RETRY event exception message should be propagated")
                    .contains("RuntimeException");
            assertThat(retryJob.getExceptionDetails())
                    .as("RETRY event exception details should be propagated")
                    .contains("Failure expected");
        }

        // Find the ERROR status event - should have exception details
        ScheduledJob errorJob = capturedJobs.stream()
                .filter(job -> "ERROR".equals(job.getStatus().name()))
                .findFirst()
                .orElse(null);

        assertThat(errorJob).as("Should have an ERROR status event").isNotNull();
        assertThat(errorJob.getExceptionDetails())
                .as("ERROR status event should contain exception details")
                .isNotNull();
        assertThat(errorJob.getExceptionMessage())
                .as("ERROR event exception message should be propagated")
                .contains("RuntimeException");
        assertThat(errorJob.getExceptionDetails())
                .as("ERROR event exception details should be propagated")
                .contains("Failure expected");

        jobScheduler.close();
    }

    @Test
    public void testExceptionDetailsClearedOnSuccessfulRetry() throws Exception {
        // Given: A job scheduler configured with exception details extraction and retries
        final String jobId = "retry-success-test";
        final int NUMBER_OF_FAILURES = 2; // Fail twice, then succeed on third attempt

        JobStore memoryJobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();
        TestFailureJobExecutor failureExecutor = new TestFailureJobExecutor(NUMBER_OF_FAILURES);
        TestEventPublisherWithCapture eventPublisher = new TestEventPublisherWithCapture();
        LatchExecutionJobSchedulerListener latchListener = new LatchExecutionJobSchedulerListener();

        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withMaxNumberOfRetries(3) // Allow enough retries for success
                .withJobExecutors(failureExecutor)
                .withRetryInterval(1000L)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(eventPublisher)
                .withJobContextFactory(jobContextFactory)
                .withJobStore(memoryJobStore)
                .withJobSchedulerListeners(latchListener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .withExceptionDetailsExtractor(new DefaultJobExceptionDetailsExtractor())
                .build();

        jobScheduler.init();

        // When: Schedule a job that will fail twice then succeed
        jobScheduler.schedule(new TestJobDescription(jobId, ZonedDateTime.now().plus(Duration.ofSeconds(1))));
        latchListener.waitForExecution();

        // Then: Verify the job completed successfully (removed from store)
        JobDetails jobDetails = memoryJobStore.find(jobContextFactory.newContext(), jobId);
        assertThat(jobDetails).as("Job should be removed from store after successful execution").isNull();

        // And: Verify exception details lifecycle through events
        List<ScheduledJob> capturedJobs = eventPublisher.getCapturedJobs();
        assertThat(capturedJobs).isNotEmpty();

        // Verify SCHEDULED event has no exception details
        List<ScheduledJob> scheduledJobs = capturedJobs.stream()
                .filter(job -> "SCHEDULED".equals(job.getStatus().name()))
                .toList();
        assertThat(scheduledJobs).hasSize(1);
        assertThat(scheduledJobs.get(0).getExceptionDetails())
                .as("SCHEDULED status should not have exception details")
                .isNull();

        // Verify first RUNNING event has no exception details
        List<ScheduledJob> runningJobs = capturedJobs.stream()
                .filter(job -> "RUNNING".equals(job.getStatus().name()))
                .toList();
        assertThat(runningJobs)
                .as("Should have at least 2 RUNNING events (initial + retry)")
                .hasSizeGreaterThanOrEqualTo(2);

        for (ScheduledJob runningJob : runningJobs) {
            assertThat(runningJob.getExceptionDetails())
                    .as("RUNNING status should not have exception details (cleared on each attempt)")
                    .isNull();
        }

        // Verify RETRY events have exception details
        List<ScheduledJob> retryJobs = capturedJobs.stream()
                .filter(job -> "RETRY".equals(job.getStatus().name()))
                .toList();
        assertThat(retryJobs)
                .as("Should have " + NUMBER_OF_FAILURES + " RETRY events")
                .hasSize(NUMBER_OF_FAILURES);

        for (ScheduledJob retryJob : retryJobs) {
            assertThat(retryJob.getExceptionDetails())
                    .as("RETRY status should have exception details")
                    .isNotNull();
            assertThat(retryJob.getExceptionMessage())
                    .as("RETRY event should have exception message")
                    .contains("RuntimeException");
        }

        // Verify EXECUTED event has no exception details
        List<ScheduledJob> executedJobs = capturedJobs.stream()
                .filter(job -> "EXECUTED".equals(job.getStatus().name()))
                .toList();
        assertThat(executedJobs)
                .as("Should have at least one EXECUTED event")
                .isNotEmpty();

        for (ScheduledJob executedJob : executedJobs) {
            assertThat(executedJob.getExceptionDetails())
                    .as("EXECUTED status should not have exception details after successful execution")
                    .isNull();
            assertThat(executedJob.getExceptionMessage())
                    .as("EXECUTED status should not have exception message after successful execution")
                    .isNull();
        }

        jobScheduler.close();
    }

    @Test
    public void testExceptionDetailsClearedOnSuccessfulPeriodicJobRetry() throws Exception {
        // Given: A periodic job scheduler with exception details extraction
        final String jobId = "periodic-retry-success-test";
        final int NUMBER_OF_FAILURES = 1; // Fail once, then succeed

        JobStore memoryJobStore = new MemoryJobStore();
        JobContextFactory jobContextFactory = new MemoryJobContextFactory();
        TestFailureJobExecutor failureExecutor = new TestFailureJobExecutor(NUMBER_OF_FAILURES);
        TestEventPublisherWithCapture eventPublisher = new TestEventPublisherWithCapture();
        LatchExecutionJobSchedulerListener latchListener = new LatchExecutionJobSchedulerListener(2); // Wait for 2 executions

        JobScheduler jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withMaxNumberOfRetries(2)
                .withJobExecutors(failureExecutor)
                .withRetryInterval(1000L)
                .withJobEventAdapters(new TestJobDetailsEventAdapter())
                .withEventPublishers(eventPublisher)
                .withJobContextFactory(jobContextFactory)
                .withJobStore(memoryJobStore)
                .withJobSchedulerListeners(latchListener)
                .withJobDescriptorMergers(new TestJobDescriptionMerger())
                .withExceptionDetailsExtractor(new DefaultJobExceptionDetailsExtractor())
                .build();

        jobScheduler.init();

        // When: Schedule a periodic job that fails once then succeeds
        ExpirationTime expirationTime = DurationExpirationTime.repeat(1, 2000L, 2); // 2 executions
        jobScheduler.schedule(new TestJobDescription(jobId, expirationTime));
        latchListener.waitForExecution();

        // Then: Verify the job completed all executions (removed from store)
        JobDetails jobDetails = memoryJobStore.find(jobContextFactory.newContext(), jobId);
        assertThat(jobDetails).as("Periodic job should be removed after all executions complete").isNull();

        // And: Verify exception details are cleared when rescheduling after successful execution
        List<ScheduledJob> capturedJobs = eventPublisher.getCapturedJobs();
        assertThat(capturedJobs).isNotEmpty();

        // Find all SCHEDULED events (initial + after first successful execution)
        List<ScheduledJob> scheduledJobs = capturedJobs.stream()
                .filter(job -> "SCHEDULED".equals(job.getStatus().name()))
                .toList();
        assertThat(scheduledJobs)
                .as("Should have at least 2 SCHEDULED events for periodic job")
                .hasSizeGreaterThanOrEqualTo(2);

        // Verify the second SCHEDULED event (after retry and success) has no exception details
        if (scheduledJobs.size() >= 2) {
            ScheduledJob rescheduledJob = scheduledJobs.get(1);
            assertThat(rescheduledJob.getExceptionDetails())
                    .as("Rescheduled job after successful execution should not have exception details")
                    .isNull();
            assertThat(rescheduledJob.getExceptionMessage())
                    .as("Rescheduled job after successful execution should not have exception message")
                    .isNull();
        }

        jobScheduler.close();
    }

}
