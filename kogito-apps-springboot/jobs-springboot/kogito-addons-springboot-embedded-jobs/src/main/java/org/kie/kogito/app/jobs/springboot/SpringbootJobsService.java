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
package org.kie.kogito.app.jobs.springboot;

import java.util.Collections;
import java.util.List;

import org.kie.kogito.app.jobs.api.JobExecutor;
import org.kie.kogito.app.jobs.api.JobScheduler;
import org.kie.kogito.app.jobs.api.JobSchedulerBuilder;
import org.kie.kogito.app.jobs.api.JobSchedulerListener;
import org.kie.kogito.app.jobs.api.JobSynchronization;
import org.kie.kogito.app.jobs.integrations.ErrorHandlingJobTimeoutInterceptor;
import org.kie.kogito.app.jobs.integrations.ProcessInstanceJobDescriptionJobInstanceEventAdapter;
import org.kie.kogito.app.jobs.integrations.ProcessJobDescriptionJobInstanceEventAdapter;
import org.kie.kogito.app.jobs.integrations.UserTaskInstanceJobDescriptionJobInstanceEventAdapter;
import org.kie.kogito.app.jobs.spi.JobContextFactory;
import org.kie.kogito.app.jobs.spi.JobStore;
import org.kie.kogito.app.jobs.spi.TransactionRollbackMarker;
import org.kie.kogito.app.jobs.springboot.resource.RestApiConstants;
import org.kie.kogito.event.EventPublisher;
import org.kie.kogito.handler.ExceptionHandler;
import org.kie.kogito.jobs.JobDescription;
import org.kie.kogito.jobs.JobsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.vertx.core.Vertx;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
@Transactional
public class SpringbootJobsService implements JobsService {

    protected JobScheduler jobScheduler;

    @Autowired(required = false)
    protected List<JobExecutor> jobExecutors;

    @Autowired(required = false)
    protected List<EventPublisher> eventPublisher;

    @Autowired(required = false)
    protected List<JobSchedulerListener> jobSchedulerListeners;

    @Autowired
    protected JobStore jobStore;

    @Autowired
    protected JobContextFactory jobContextFactory;

    @Value("${kogito.jobs-service.numberOfWorkerThreads:10}")
    protected Integer numberOfWorkerThreads;

    @Value("${kogito.jobs-service.maxNumberOfRetries:3}")
    protected Integer maxNumberOfRetries;

    @Value("${kogito.jobs-service.retryMillis:100}")
    protected Long retryMillis;

    @Value("${kogito.jobs-service.schedulerChunkInMinutes:10}")
    protected Long maxRefreshJobsIntervalWindow;

    @Value("${kogito.service.url:http://localhost:8080}")
    protected String serviceURL;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    @Autowired(required = false)
    protected List<ExceptionHandler> exceptionHandlers;

    @Autowired
    protected WrappingConditionalJobExceptionDetailsExtractor exceptionDetailsExtractor;

    @Autowired
    protected TransactionRollbackMarker transactionRollbackMarker;

    @Autowired
    protected Vertx vertx;

    @PostConstruct
    public void init() {
        this.jobScheduler = JobSchedulerBuilder.newJobSchedulerBuilder()
                .withEventPublishers(ofNullable(eventPublisher).toArray(EventPublisher[]::new))
                .withJobSchedulerListeners(ofNullable(jobSchedulerListeners).stream().toArray(JobSchedulerListener[]::new))
                .withJobStore(jobStore)
                .withJobContextFactory(jobContextFactory)
                .withJobEventAdapters(
                        new ProcessInstanceJobDescriptionJobInstanceEventAdapter(serviceURL + RestApiConstants.JOBS_PATH),
                        new ProcessJobDescriptionJobInstanceEventAdapter(serviceURL + RestApiConstants.JOBS_PATH),
                        new UserTaskInstanceJobDescriptionJobInstanceEventAdapter(serviceURL + RestApiConstants.JOBS_PATH))
                .withJobExecutors(ofNullable(jobExecutors).toArray(JobExecutor[]::new))
                .withMaxRefreshJobsIntervalWindow(maxRefreshJobsIntervalWindow * 60 * 1000L)
                .withRetryInterval(retryMillis)
                .withMaxNumberOfRetries(maxNumberOfRetries)
                .withRefreshJobsInterval(maxRefreshJobsIntervalWindow * 60 * 1000L)
                .withTimeoutInterceptor(
                        new ErrorHandlingJobTimeoutInterceptor(ofNullable(exceptionHandlers).stream().toList()),
                        new TransactionJobTimeoutInterceptor(transactionManager))
                .withVertx(vertx)
                .withExceptionDetailsExtractor(exceptionDetailsExtractor)
                .withTransactionRollbackMarker(transactionRollbackMarker)
                .withNumberOfWorkerThreads(numberOfWorkerThreads)
                .withJobSynchronization(new JobSynchronization() {

                    @Override
                    public void synchronize(Runnable action) {
                        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                            return;
                        }
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                action.run();
                            }
                        });

                    }
                })
                .build();
        this.jobScheduler.init();

    }

    private <T> List<T> ofNullable(List<T> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }

    @PreDestroy()
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
