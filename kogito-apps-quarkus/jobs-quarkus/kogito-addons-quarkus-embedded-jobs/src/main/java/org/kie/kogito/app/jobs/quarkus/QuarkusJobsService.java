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
package org.kie.kogito.app.jobs.quarkus;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kie.kogito.app.jobs.api.JobExecutor;
import org.kie.kogito.app.jobs.api.JobScheduler;
import org.kie.kogito.app.jobs.api.JobSchedulerBuilder;
import org.kie.kogito.app.jobs.api.JobSchedulerListener;
import org.kie.kogito.app.jobs.api.JobSynchronization;
import org.kie.kogito.app.jobs.integrations.ErrorHandlingJobTimeoutInterceptor;
import org.kie.kogito.app.jobs.integrations.ProcessInstanceJobDescriptionJobInstanceEventAdapter;
import org.kie.kogito.app.jobs.integrations.ProcessJobDescriptionJobInstanceEventAdapter;
import org.kie.kogito.app.jobs.integrations.UserTaskInstanceJobDescriptionJobInstanceEventAdapter;
import org.kie.kogito.app.jobs.quarkus.resource.RestApiConstants;
import org.kie.kogito.app.jobs.spi.JobContextFactory;
import org.kie.kogito.app.jobs.spi.JobStore;
import org.kie.kogito.app.jobs.spi.TransactionRollbackMarker;
import org.kie.kogito.event.EventPublisher;
import org.kie.kogito.handler.ExceptionHandler;
import org.kie.kogito.jobs.JobDescription;
import org.kie.kogito.jobs.JobsService;

import io.quarkus.runtime.Startup;
import io.vertx.core.Vertx;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;

@Transactional
@Singleton
@Startup
public class QuarkusJobsService implements JobsService {

    protected JobScheduler jobScheduler;

    @Inject
    protected Instance<JobExecutor> jobExecutors;

    @Inject
    protected Instance<EventPublisher> eventPublisher;

    @Inject
    protected JobStore jobStore;

    @Inject
    protected JobContextFactory jobContextFactory;

    @Inject
    protected Instance<JobSchedulerListener> jobSchedulerListeners;

    @ConfigProperty(name = "kogito.jobs-service.numberOfWorkerThreads", defaultValue = "10")
    protected Integer numberOfWorkerThreads;

    @ConfigProperty(name = "kogito.jobs-service.maxNumberOfRetries", defaultValue = "3")
    protected Integer maxNumberOfRetries;

    @ConfigProperty(name = "kogito.jobs-service.retryMillis", defaultValue = "100")
    protected Long retryMillis;

    @ConfigProperty(name = "kogito.jobs-service.schedulerChunkInMinutes", defaultValue = "10")
    protected Long maxRefreshJobsIntervalWindow;

    @ConfigProperty(name = "kogito.service.url", defaultValue = "http://localhost:8080")
    protected String serviceURL;

    @Inject
    protected TransactionSynchronizationRegistry registry;

    @Inject
    Instance<ExceptionHandler> exceptionHandlers;

    @Inject
    WrappingConditionalJobExceptionDetailsExtractor exceptionDetailsExtractor;

    @Inject
    TransactionRollbackMarker transactionRollbackMarker;

    @Inject
    Vertx vertx;

    @PostConstruct
    public void init() {
        this.jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withEventPublishers(eventPublisher.stream().toArray(EventPublisher[]::new))
                .withJobSchedulerListeners(jobSchedulerListeners.stream().toArray(JobSchedulerListener[]::new))
                .withJobStore(jobStore)
                .withJobContextFactory(jobContextFactory)
                .withJobEventAdapters(
                        new ProcessInstanceJobDescriptionJobInstanceEventAdapter(serviceURL + RestApiConstants.JOBS_PATH),
                        new ProcessJobDescriptionJobInstanceEventAdapter(serviceURL + RestApiConstants.JOBS_PATH),
                        new UserTaskInstanceJobDescriptionJobInstanceEventAdapter(serviceURL + RestApiConstants.JOBS_PATH))
                .withJobExecutors(jobExecutors.stream().toArray(JobExecutor[]::new))
                .withMaxRefreshJobsIntervalWindow(maxRefreshJobsIntervalWindow * 60 * 1000L)
                .withRetryInterval(retryMillis)
                .withMaxNumberOfRetries(maxNumberOfRetries)
                .withRefreshJobsInterval(maxRefreshJobsIntervalWindow * 60 * 1000L)
                .withTimeoutInterceptor(
                        new TransactionJobTimeoutInterceptor(),
                        new ErrorHandlingJobTimeoutInterceptor(exceptionHandlers.stream().toList()))
                .withVertx(vertx)
                .withExceptionDetailsExtractor(exceptionDetailsExtractor)
                .withTransactionRollbackMarker(transactionRollbackMarker)
                .withNumberOfWorkerThreads(numberOfWorkerThreads)
                .withJobSynchronization(new JobSynchronization() {

                    @Override
                    public void synchronize(Runnable action) {
                        if (registry.getTransactionStatus() != Status.STATUS_ACTIVE) {
                            return;
                        }
                        registry.registerInterposedSynchronization(new Synchronization() {

                            @Override
                            public void beforeCompletion() {
                                // do nothing
                            }

                            @Override
                            public void afterCompletion(int status) {
                                if (status == Status.STATUS_COMMITTED) {
                                    action.run();
                                }
                            }

                        });

                    }
                })
                .build();
        this.jobScheduler.init();

    }

    @PreDestroy
    public void destroy() {
        this.jobScheduler.close();
    }

    @Override
    public String scheduleJob(JobDescription jobDescription) {
        return jobScheduler.schedule(jobDescription);
    }

    @Override
    public boolean cancelJob(String jobId) {
        jobScheduler.cancel(jobId);
        return true;
    }

    @Override
    public String rescheduleJob(JobDescription jobDescription) {
        return jobScheduler.reschedule(jobDescription);
    }

}
