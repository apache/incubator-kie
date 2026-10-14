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
package org.kie.kogito.process.impl;

import org.kie.kogito.auth.AuthTokenProvider;
import org.kie.kogito.auth.IdentityProvider;
import org.kie.kogito.auth.impl.NoOpAuthTokenProvider;
import org.kie.kogito.calendar.BusinessCalendar;
import org.kie.kogito.internal.process.event.KogitoProcessEventListener;
import org.kie.kogito.jobs.JobsService;
import org.kie.kogito.process.ProcessConfig;
import org.kie.kogito.process.ProcessEventListenerConfig;
import org.kie.kogito.process.ProcessVersionResolver;
import org.kie.kogito.process.WorkItemHandlerConfig;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.services.identity.NoOpIdentityProvider;
import org.kie.kogito.services.signal.DefaultSignalManagerHub;
import org.kie.kogito.signal.SignalManagerHub;
import org.kie.kogito.uow.UnitOfWorkManager;

import static org.kie.kogito.services.jobs.impl.StaticJobService.staticJobService;
import static org.kie.kogito.services.uow.StaticUnitOfWorkManger.staticUnitOfWorkManager;

public class StaticProcessConfig implements ProcessConfig {

    private WorkItemHandlerConfig workItemHandlerConfig;
    private ProcessEventListenerConfig processEventListenerConfig;
    private SignalManagerHub signalManager;
    private UnitOfWorkManager unitOfWorkManager;
    private JobsService jobsService;
    private ProcessVersionResolver versionResolver;

    private IdentityProvider identityProvider;
    private AuthTokenProvider authTokenProvider;
    private BusinessCalendar businessCalendar;

    public StaticProcessConfig(JobsService jobService) {
        this(new DefaultWorkItemHandlerConfig(),
                new DefaultProcessEventListenerConfig(),
                staticUnitOfWorkManager(),
                jobService,
                null,
                new NoOpIdentityProvider(),
                null);
    }

    public StaticProcessConfig(
            WorkItemHandlerConfig workItemHandlerConfig,
            ProcessEventListenerConfig processEventListenerConfig,
            UnitOfWorkManager unitOfWorkManager) {
        this(workItemHandlerConfig, processEventListenerConfig, unitOfWorkManager, staticJobService());
    }

    public StaticProcessConfig(
            WorkItemHandlerConfig workItemHandlerConfig,
            ProcessEventListenerConfig processEventListenerConfig,
            UnitOfWorkManager unitOfWorkManager,
            JobsService jobsService) {
        this(workItemHandlerConfig, processEventListenerConfig, unitOfWorkManager, jobsService, null, new NoOpIdentityProvider(), null);
    }

    public StaticProcessConfig(
            WorkItemHandlerConfig workItemHandlerConfig,
            ProcessEventListenerConfig processEventListenerConfig,
            UnitOfWorkManager unitOfWorkManager,
            JobsService jobsService,
            ProcessVersionResolver versionResolver,
            IdentityProvider identityProvider,
            BusinessCalendar calendar) {
        this(workItemHandlerConfig, processEventListenerConfig, unitOfWorkManager, jobsService, versionResolver, identityProvider, null, calendar);
    }

    public StaticProcessConfig(
            WorkItemHandlerConfig workItemHandlerConfig,
            ProcessEventListenerConfig processEventListenerConfig,
            UnitOfWorkManager unitOfWorkManager,
            JobsService jobsService,
            ProcessVersionResolver versionResolver,
            IdentityProvider identityProvider,
            AuthTokenProvider authTokenProvider,
            BusinessCalendar calendar) {
        this.unitOfWorkManager = unitOfWorkManager;
        this.workItemHandlerConfig = workItemHandlerConfig;
        this.processEventListenerConfig = processEventListenerConfig;
        this.signalManager = new DefaultSignalManagerHub();
        this.jobsService = jobsService;
        this.versionResolver = versionResolver;
        this.identityProvider = identityProvider;
        this.authTokenProvider = authTokenProvider;
        this.businessCalendar = calendar;
    }

    public StaticProcessConfig() {
        this(new DefaultWorkItemHandlerConfig(),
                new DefaultProcessEventListenerConfig(),
                staticUnitOfWorkManager(),
                staticJobService(),
                null,
                new NoOpIdentityProvider(),
                null);
    }

    @Override
    public WorkItemHandlerConfig workItemHandlers() {
        return this.workItemHandlerConfig;
    }

    @Override
    public ProcessEventListenerConfig processEventListeners() {
        return this.processEventListenerConfig;
    }

    @Override
    public SignalManagerHub signalManagerHub() {
        return this.signalManager;
    }

    @Override
    public UnitOfWorkManager unitOfWorkManager() {
        return this.unitOfWorkManager;
    }

    @Override
    public JobsService jobsService() {
        return jobsService;
    }

    @Override
    public ProcessVersionResolver versionResolver() {
        return versionResolver;
    }

    @Override
    public IdentityProvider identityProvider() {
        return identityProvider;
    }

    @Override
    public AuthTokenProvider authTokenProvider() {
        return authTokenProvider;
    }

    @Override
    public BusinessCalendar getBusinessCalendar() {
        return this.businessCalendar;
    }

    public static StaticProcessConfigBuilder newStaticProcessConfigBuilder() {
        return new StaticProcessConfig().new StaticProcessConfigBuilder();
    }

    public class StaticProcessConfigBuilder {
        public StaticProcessConfigBuilder() {
            StaticProcessConfig.this.unitOfWorkManager = staticUnitOfWorkManager();
            StaticProcessConfig.this.workItemHandlerConfig = new DefaultWorkItemHandlerConfig();
            StaticProcessConfig.this.processEventListenerConfig = new DefaultProcessEventListenerConfig();
            StaticProcessConfig.this.signalManager = new DefaultSignalManagerHub();
            StaticProcessConfig.this.jobsService = staticJobService();
            StaticProcessConfig.this.versionResolver = process -> process.version();
            StaticProcessConfig.this.identityProvider = new NoOpIdentityProvider();
            StaticProcessConfig.this.authTokenProvider = new NoOpAuthTokenProvider();
            StaticProcessConfig.this.businessCalendar = null;
        }

        public StaticProcessConfig build() {
            return StaticProcessConfig.this;
        }

        public StaticProcessConfigBuilder withWorkItemHandler(String name, KogitoWorkItemHandler testWorkItemHandler) {
            ((DefaultWorkItemHandlerConfig) StaticProcessConfig.this.workItemHandlerConfig).register(name, testWorkItemHandler);
            return this;
        }

        public StaticProcessConfigBuilder withProcessListener(KogitoProcessEventListener listener) {
            ((DefaultProcessEventListenerConfig) StaticProcessConfig.this.processEventListenerConfig).register(listener);
            return this;
        }

        public StaticProcessConfigBuilder withCalendar(BusinessCalendar businessCalendar) {
            StaticProcessConfig.this.businessCalendar = businessCalendar;
            return this;
        }
    }
}
