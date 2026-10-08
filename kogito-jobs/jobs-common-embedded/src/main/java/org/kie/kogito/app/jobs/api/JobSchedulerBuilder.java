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

/**
 * Fluent builder for configuring a {@link JobScheduler}.
 * <p>
 * The type parameter {@code B} is the concrete builder type, enabling every
 * method to return the concrete subtype so callers get a fully fluent chain
 * regardless of method call order — including implementation-specific methods
 * such as {@link VertxJobScheduler.VertxJobSchedulerBuilder#withVertx}.
 * <p>
 * Use {@link #newJobSchedulerBuilder()} to obtain the default Vert.x-based builder.
 */
public interface JobSchedulerBuilder<B extends JobSchedulerBuilder<B>> {

    static VertxJobScheduler.VertxJobSchedulerBuilder newJobSchedulerBuilder() {
        return new VertxJobScheduler().new VertxJobSchedulerBuilder();
    }

    JobScheduler build();

    B withJobStore(JobStore jobStore);

    B withJobExecutors(JobExecutor... jobExecutors);

    B withJobContextFactory(JobContextFactory jobContextFactory);

    B withEventPublishers(EventPublisher... eventPublishers);

    B withJobEventAdapters(JobDetailsEventAdapter... jobEventAdapters);

    B withMaxNumberOfRetries(Integer maxNumberOfRetries);

    B withRefreshJobsInterval(Long refreshJobsInterval);

    B withMaxRefreshJobsIntervalWindow(Long maxRefreshsJobsIntervalWindow);

    B withJobSchedulerListeners(JobSchedulerListener... jobSchedulerListeners);

    B withRetryInterval(Long retryInterval);

    B withTimeoutInterceptor(JobTimeoutInterceptor... interceptors);

    B withNumberOfWorkerThreads(Integer numberOfWorkerThreads);

    B withJobSynchronization(JobSynchronization jobSynchronization);

    B withJobDescriptorMergers(JobDescriptionMerger... jobDescriptionMergers);

    B withExceptionDetailsExtractor(JobExceptionDetailsExtractor exceptionDetailsExtractor);

    B withTransactionRollbackMarker(TransactionRollbackMarker transactionRollbackMarker);
}
