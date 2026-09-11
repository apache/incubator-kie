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
package org.kie.kogito.jobs.service.repository.impl;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.kie.kogito.jobs.service.model.JobDetails;
import org.kie.kogito.jobs.service.repository.ReactiveJobRepository;
import org.kie.kogito.jobs.service.stream.JobEventPublisher;

import io.vertx.core.Vertx;

public abstract class BaseReactiveJobRepository implements ReactiveJobRepository {

    private Vertx vertx;

    private JobEventPublisher jobEventPublisher;

    protected BaseReactiveJobRepository(Vertx vertx, JobEventPublisher jobEventPublisher) {
        this.vertx = vertx;
        this.jobEventPublisher = jobEventPublisher;
    }

    public <T> CompletionStage<T> runAsync(Supplier<T> function) {
        // a throwing supplier fails the returned future instead of leaving callers waiting forever
        return vertx.executeBlocking(function::get).toCompletionStage();
    }

    @Override
    public CompletionStage<JobDetails> save(JobDetails job) {
        return doSave(job)
                .thenApply(jobEventPublisher::publishJobStatusChange);
    }

    public abstract CompletionStage<JobDetails> doSave(JobDetails job);

    @Override
    public CompletionStage<JobDetails> delete(JobDetails job) {
        return delete(job.getId())
                .thenApply(j -> jobEventPublisher.publishJobStatusChange(job));
    }

    @Override
    public CompletionStage<JobDetails> merge(String id, JobDetails jobToMerge) {
        return Optional.ofNullable(id)
                //do validations
                .filter(StringUtils::isNotBlank)
                .filter(s -> StringUtils.isBlank(jobToMerge.getId()) || s.equals(jobToMerge.getId()))
                //perform merge
                .map(jobId -> this.get(jobId)
                        .thenApply(Optional::ofNullable)
                        .thenApply(j -> j.map(currentJob -> doMerge(jobToMerge, currentJob)))
                        .thenCompose(j -> j.map(this::save).orElse(CompletableFuture.completedFuture(null))))//save it
                .orElseThrow(() -> new IllegalArgumentException("Id is empty or not equals to Job.id : " + id));
    }

    private JobDetails doMerge(JobDetails toMerge, JobDetails current) {
        return JobDetails.builder()
                .of(current)
                .merge(toMerge)
                .build();
    }
}
