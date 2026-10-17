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
package org.kie.kogito.process;

import java.util.List;
import java.util.Optional;

import org.jbpm.process.core.context.variable.VariableScope;
import org.kie.api.annotations.KieProperty;
import org.kie.kogito.VariableLegacyMode;
import org.kie.kogito.config.ConfigBean;
import org.kie.kogito.correlation.CorrelationService;
import org.kie.kogito.event.correlation.DefaultCorrelationService;
import org.kie.kogito.jobs.JobsService;
import org.kie.kogito.process.version.ProjectVersionProcessVersionResolver;
import org.kie.kogito.services.jobs.impl.InMemoryJobContext;
import org.kie.kogito.services.jobs.impl.InMemoryJobService;
import org.kie.kogito.services.jobs.impl.InMemoryProcessJobExecutorFactory;
import org.kie.kogito.services.uow.CollectingUnitOfWorkFactory;
import org.kie.kogito.services.uow.DefaultUnitOfWorkManager;
import org.kie.kogito.uow.UnitOfWorkManager;
import org.kie.kogito.usertask.UserTasks;
import org.kogito.workitem.rest.RestWorkItemHandlerUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;

import jakarta.annotation.PostConstruct;

@Configuration
public class KogitoBeanProducer {

    /**
     * (boolean) when true, undeclared variables are mapped into the runtime process instance (restores legacy behavior); default is false
     */
    @KieProperty(type = "boolean", defaultValue = "false")
    public static final String KOGITO_WORKFLOW_VARIABLE_LEGACY = VariableScope.KOGITO_VARIABLE_LEGACY_PROPERTY;

    ConfigBean configBean;

    @Value("${" + VariableScope.KOGITO_VARIABLE_LEGACY_PROPERTY + ":false}")
    private boolean variableLegacy;

    @Autowired
    public KogitoBeanProducer(ConfigBean configBean) {
        this.configBean = configBean;
    }

    @PostConstruct
    void init() {
        VariableLegacyMode.setEnabled(variableLegacy);
    }

    @Bean
    CorrelationService correlationService() {
        return new DefaultCorrelationService();
    }

    @Bean
    @ConditionalOnMissingBean(JobsService.class)
    JobsService jobsService(List<Processes> processes, List<UserTasks> userTasks, UnitOfWorkManager uowm) {
        InMemoryJobContext context = new InMemoryJobContext(null, uowm, !processes.isEmpty() ? processes.get(0) : null, !userTasks.isEmpty() ? userTasks.get(0) : null);
        InMemoryJobService inMemoryJobService = new InMemoryJobService();
        inMemoryJobService.registerJobExecutorFactory(new InMemoryProcessJobExecutorFactory(context));
        return inMemoryJobService;
    }

    @Bean
    @ConditionalOnProperty(value = "kogito.workflow.version-strategy", havingValue = "project")
    ProcessVersionResolver projectVersionResolver() {
        return new ProjectVersionProcessVersionResolver(configBean.getGav().orElseThrow(() -> new RuntimeException("Unable to use kogito.workflow.version-strategy without a project GAV")));
    }

    @Bean
    @ConditionalOnMissingBean(UnitOfWorkManager.class)
    UnitOfWorkManager unitOfWorkManager() {
        return new DefaultUnitOfWorkManager(new CollectingUnitOfWorkFactory());
    }

    @Bean
    @ConditionalOnMissingBean(WebClientOptions.class)
    WebClientOptions sslDefaultOptions(@Value("${" + RestWorkItemHandlerUtils.MAX_POOL_SIZE_PROPERTY + ":#{null}}") Optional<Integer> maxPoolSize) {
        WebClientOptions options = RestWorkItemHandlerUtils.sslWebClientOptions();
        maxPoolSize.ifPresent(options::setMaxPoolSize);
        return options;
    }

    @Bean
    @ConditionalOnMissingBean(Vertx.class)
    Vertx vertxMutiny() {
        return Vertx.vertx();
    }

    @Bean
    @ConditionalOnMissingBean(io.vertx.core.Vertx.class)
    io.vertx.core.Vertx vertx() {
        return io.vertx.core.Vertx.vertx();
    }
}
