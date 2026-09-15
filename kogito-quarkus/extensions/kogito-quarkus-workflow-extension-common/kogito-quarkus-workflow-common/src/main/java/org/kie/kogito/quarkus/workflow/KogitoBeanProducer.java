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
package org.kie.kogito.quarkus.workflow;

import java.util.concurrent.ScheduledExecutorService;

import org.kie.api.annotations.KieProperty;
import org.kie.kogito.config.ConfigBean;
import org.kie.kogito.correlation.CorrelationService;
import org.kie.kogito.event.correlation.DefaultCorrelationService;
import org.kie.kogito.jobs.JobsService;
import org.kie.kogito.process.ProcessVersionResolver;
import org.kie.kogito.process.Processes;
import org.kie.kogito.process.version.ProjectVersionProcessVersionResolver;
import org.kie.kogito.services.jobs.impl.InMemoryJobContext;
import org.kie.kogito.services.jobs.impl.InMemoryJobService;
import org.kie.kogito.services.jobs.impl.InMemoryProcessJobExecutorFactory;
import org.kie.kogito.services.uow.CollectingUnitOfWorkFactory;
import org.kie.kogito.services.uow.DefaultUnitOfWorkManager;
import org.kie.kogito.uow.UnitOfWorkManager;
import org.kie.kogito.usertask.UserTasks;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.properties.IfBuildProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class KogitoBeanProducer {

    /**
     * (string) strategy to resolve a Process version to use; possible values: project, workflow; if "project", requires project GAV; default is workflow
     */
    @KieProperty(type = "string", defaultValue = "workflow", allowedValues = "project,workflow")
    public static final String KOGITO_WORKFLOW_VERSION_STRATEGY = "kogito.workflow.version-strategy";

    @DefaultBean
    @Produces
    CorrelationService correlationService() {
        return new DefaultCorrelationService();
    }

    // Must stay a per-application singleton: a JVM-wide static outlives a dev-mode reload. See incubator-kie#7106.
    @DefaultBean
    @Produces
    @Singleton
    UnitOfWorkManager unitOfWorkManager() {
        return new DefaultUnitOfWorkManager(new CollectingUnitOfWorkFactory());
    }

    @DefaultBean
    @Produces
    JobsService jobsService(Instance<Processes> processes, Instance<UserTasks> userTasks, UnitOfWorkManager uowm, ScheduledExecutorService executor) {
        InMemoryJobContext context = new InMemoryJobContext(null, uowm, processes.isResolvable() ? processes.get() : null, userTasks.isResolvable() ? userTasks.get() : null);
        InMemoryJobService inMemoryJobService = new InMemoryJobService(executor);
        inMemoryJobService.registerJobExecutorFactory(new InMemoryProcessJobExecutorFactory(context));
        return inMemoryJobService;
    }

    @Produces
    @IfBuildProperty(name = "kogito.workflow.version-strategy", stringValue = "project")
    ProcessVersionResolver projectVersionResolver(ConfigBean configBean) {
        return new ProjectVersionProcessVersionResolver(configBean.getGav().orElseThrow(() -> new RuntimeException("Unable to use kogito.workflow.version-strategy without a project GAV")));
    }
}
