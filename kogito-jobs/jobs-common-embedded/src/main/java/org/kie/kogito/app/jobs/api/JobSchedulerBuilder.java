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
package org.kie.kogito.app.jobs.api;

import org.kie.kogito.app.jobs.impl.VertxJobScheduler;
import org.kie.kogito.app.jobs.integrations.JobExceptionDetailsExtractor;
import org.kie.kogito.app.jobs.spi.JobContextFactory;
import org.kie.kogito.app.jobs.spi.JobStore;
import org.kie.kogito.app.jobs.spi.TransactionRollbackMarker;
import org.kie.kogito.event.EventPublisher;

import io.vertx.core.Vertx;

public interface JobSchedulerBuilder {

    static JobSchedulerBuilder newJobSchedulerBuilder() {
        return VertxJobScheduler.builder();
    }

    JobScheduler build();

    JobSchedulerBuilder withJobStore(JobStore jobStore);

    JobSchedulerBuilder withJobExecutors(JobExecutor... jobExecutors);

    JobSchedulerBuilder withJobContextFactory(JobContextFactory jobContextFactory);

    JobSchedulerBuilder withEventPublishers(EventPublisher... eventPublishers);

    JobSchedulerBuilder withJobEventAdapters(JobDetailsEventAdapter... jobEventAdapters);

    JobSchedulerBuilder withMaxNumberOfRetries(Integer maxNumberOfRetries);

    JobSchedulerBuilder withRefreshJobsInterval(Long refreshJobsInterval);

    JobSchedulerBuilder withMaxRefreshJobsIntervalWindow(Long maxRefreshsJobsIntervalWindow);

    JobSchedulerBuilder withJobSchedulerListeners(JobSchedulerListener... jobSchedulerListeners);

    JobSchedulerBuilder withRetryInterval(Long retryInterval);

    JobSchedulerBuilder withTimeoutInterceptor(JobTimeoutInterceptor... interceptors);

    JobSchedulerBuilder withNumberOfWorkerThreads(Integer numberOfWorkerThreads);

    /**
     * Supplies the platform-managed Vert.x instance to use as the execution runtime.
     * If not called, the scheduler creates and owns a standalone instance.
     */
    JobSchedulerBuilder withVertx(Vertx vertx);

    JobSchedulerBuilder withJobSynchronization(JobSynchronization jobSynchronization);

    JobSchedulerBuilder withJobDescriptorMergers(JobDescriptionMerger... jobDescriptionMergers);

    JobSchedulerBuilder withExceptionDetailsExtractor(JobExceptionDetailsExtractor exceptionDetailsExtractor);

    JobSchedulerBuilder withTransactionRollbackMarker(TransactionRollbackMarker transactionRollbackMarker);
}
