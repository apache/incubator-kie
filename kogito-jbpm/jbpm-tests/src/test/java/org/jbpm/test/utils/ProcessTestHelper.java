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
package org.jbpm.test.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jbpm.bpmn2.support.InMemoryProcessInstancesFactory;
import org.jbpm.bpmn2.support.TrackStateProcessListener;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.kie.api.event.process.ProcessNodeEvent;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.kogito.Addons;
import org.kie.kogito.Application;
import org.kie.kogito.Model;
import org.kie.kogito.StaticApplication;
import org.kie.kogito.StaticConfig;
import org.kie.kogito.auth.SecurityPolicy;
import org.kie.kogito.internal.process.event.KogitoEventListener;
import org.kie.kogito.internal.process.event.KogitoProcessEventListener;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.ProcessConfig;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.process.bpmn2.BpmnProcesses;
import org.kie.kogito.process.impl.AbstractProcessInstance;
import org.kie.kogito.process.impl.DefaultProcessEventListenerConfig;
import org.kie.kogito.process.impl.DefaultWorkItemHandlerConfig;
import org.kie.kogito.process.impl.StaticProcessConfig;
import org.kie.kogito.services.jobs.impl.InMemoryJobContext;
import org.kie.kogito.services.jobs.impl.InMemoryProcessJobExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.emptyList;
import static org.kie.kogito.services.jobs.impl.StaticJobService.staticJobService;
import static org.kie.kogito.services.uow.StaticUnitOfWorkManger.staticUnitOfWorkManager;

public class ProcessTestHelper {

    private static Logger LOGGER = LoggerFactory.getLogger(ProcessTestHelper.class);

    public static Application newApplication() {
        return newApplication(new StaticProcessConfig());
    }

    public static Application newApplication(ProcessConfig staticConfig) {
        BpmnProcesses bpmnProcesses = new BpmnProcesses();
        bpmnProcesses.setProcessInstancesFactory(new InMemoryProcessInstancesFactory());
        InMemoryJobContext context = new InMemoryJobContext(null, staticUnitOfWorkManager(), bpmnProcesses, null);
        staticJobService().clearJobExecutorFactories();
        staticJobService().registerJobExecutorFactory(new InMemoryProcessJobExecutorFactory(context));
        Application app = new StaticApplication(new StaticConfig(Addons.EMTPY, staticConfig), bpmnProcesses);
        ((DefaultProcessEventListenerConfig) app.config().get(ProcessConfig.class).processEventListeners()).register(new TrackStateProcessListener());
        return app;
    }

    public static Optional<WorkflowProcessInstance> findRemovedInstance(Application app, String processInstanceId) {
        TrackStateProcessListener trackStateProcessListener = (TrackStateProcessListener) ((DefaultProcessEventListenerConfig) app.config()
                .get(ProcessConfig.class).processEventListeners()).listeners().stream()
                        .filter(TrackStateProcessListener.class::isInstance).findFirst().get();
        return Optional.ofNullable(trackStateProcessListener.findCompletedProcessInstance(processInstanceId));
    }

    public static <R, T extends Model> R executeInWorkflowState(ProcessInstance<T> processInstance, Function<WorkflowProcessInstanceImpl, R> executionUnit) {
        return ((AbstractProcessInstance<T>) processInstance).executeInWorkflowProcessInstanceWrite(executionUnit);
    }

    public static void registerProcessEventListener(Application app, KogitoProcessEventListener kogitoProcessEventListener) {
        ((DefaultProcessEventListenerConfig) app.config().get(ProcessConfig.class).processEventListeners()).register(kogitoProcessEventListener);
    }

    public static void registerHandler(Application app, KogitoWorkItemHandler handler) {
        ((DefaultWorkItemHandlerConfig) app.config().get(ProcessConfig.class).workItemHandlers()).register(handler.getName(), handler);
    }

    public static void registerHandler(Application app, String handlerName, KogitoWorkItemHandler handler) {
        ((DefaultWorkItemHandlerConfig) app.config().get(ProcessConfig.class).workItemHandlers()).register(handlerName, handler);
    }

    /**
     * Will be removed in favor of the {@link #completeWorkItem(ProcessInstance, Map, String, String...)}
     */
    @Deprecated
    public static void completeWorkItem(ProcessInstance<? extends Model> processInstance, String userName, Map<String, Object> outputVars) {
        completeWorkItem(processInstance, outputVars, item -> {
        }, userName);
    }

    /**
     * Will be removed in favor of the {@link #completeWorkItem(ProcessInstance, Map, Consumer, String, String...)}
     */
    @Deprecated
    public static void completeWorkItem(ProcessInstance<? extends Model> processInstance, String userName, Map<String, Object> outputVars, Consumer<WorkItem> workItem) {
        completeWorkItem(processInstance, outputVars, workItem, userName);
    }

    public static void completeWorkItem(ProcessInstance<? extends Model> processInstance, Map<String, Object> outputVars) {
        completeWorkItem(processInstance, outputVars, item -> {
        }, null);
    }

    public static void completeWorkItem(ProcessInstance<? extends Model> processInstance, Map<String, Object> outputVars, String userName, String... groups) {
        completeWorkItem(processInstance, outputVars, item -> {
        }, userName, groups);
    }

    public static void completeWorkItem(ProcessInstance<? extends Model> processInstance, Map<String, Object> outputVars, Consumer<WorkItem> workItem, String userName, String... groups) {
        List<WorkItem> workItems = new ArrayList<>();
        if (userName != null) {
            workItems.addAll(processInstance.workItems(SecurityPolicy.of(userName, Arrays.asList(groups))));
        } else {
            workItems.addAll(processInstance.workItems());
        }
        if (workItems.isEmpty()) {
            return;
        }
        WorkItem e = workItems.get(0);
        workItem.accept(e);
        if (userName != null) {
            processInstance.completeWorkItem(e.getId(), outputVars, SecurityPolicy.of(userName, Arrays.asList(groups)));
        } else {
            processInstance.completeWorkItem(e.getId(), outputVars);
        }

    }

    public static WorkItem findWorkItem(ProcessInstance<? extends Model> processInstance, String userName) {
        List<WorkItem> workItems = processInstance.workItems(SecurityPolicy.of(userName, emptyList()));
        return workItems.stream().findFirst().get();
    }

    public static <T extends ProcessNodeEvent> Predicate<T> triggered(String nodeName) {
        return e -> {
            return e instanceof ProcessNodeTriggeredEvent && nodeName.equals(((ProcessNodeTriggeredEvent) e).getNodeInstance().getNodeName());
        };
    }

    public static <T extends ProcessNodeEvent> Predicate<T> left(String nodeName) {
        return e -> {
            return e instanceof ProcessNodeLeftEvent && nodeName.equals(((ProcessNodeLeftEvent) e).getNodeInstance().getNodeName());
        };
    }

    public static class CompletionKogitoEventListener implements KogitoEventListener {

        private String id;
        private CountDownLatch latch;

        public CompletionKogitoEventListener(String id) {
            this.id = id;
            this.latch = new CountDownLatch(1);
        }

        @Override
        public void signalEvent(String type, Object event) {
            latch.countDown();
            LOGGER.info("Completion was invoked");
        }

        @Override
        public String[] getEventTypes() {
            return new String[] { "processInstanceCompleted:" + id };
        }

        public void await() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                LOGGER.info("Completion was interrupted", e);
            }
        }

    }

    public static CompletionKogitoEventListener registerCompletionEventListener(AbstractProcessInstance<?> instance) {
        CompletionKogitoEventListener completionEventListener = new CompletionKogitoEventListener(instance.id());
        instance.internalGetProcessInstance().addEventListener("processInstanceCompleted:" + instance.id(), completionEventListener, false);
        return completionEventListener;
    }

}
