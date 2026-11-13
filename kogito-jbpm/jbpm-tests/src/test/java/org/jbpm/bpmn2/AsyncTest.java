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
package org.jbpm.bpmn2;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.jbpm.bpmn2.async.AsyncServiceTaskRetryModel;
import org.jbpm.bpmn2.async.AsyncServiceTaskRetryProcess;
import org.jbpm.bpmn2.async.ComplexAsyncModel;
import org.jbpm.bpmn2.async.ComplexAsyncProcess;
import org.jbpm.bpmn2.objects.HelloService;
import org.jbpm.test.utils.ProcessTestHelper;
import org.jbpm.workflow.core.impl.WorkflowProcessImpl;
import org.junit.jupiter.api.Test;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.kogito.Application;
import org.kie.kogito.handlers.HelloService_hello_RetryServiceTask_Handler;
import org.kie.kogito.handlers.StatusTrackerService_createStatusTracker__2B159DC3_EA7B_46A3_A632_7952108A565D_Handler;
import org.kie.kogito.handlers.StatusTrackerService_createStatusTracker__657C59C4_205E_4800_8BEB_B63703D1008B_Handler;
import org.kie.kogito.handlers.StatusTrackerService_createStatusTracker__6CC31E0B_5FDE_40E0_927B_978EB98C5406_Handler;
import org.kie.kogito.handlers.StatusTrackerService_createStatusTracker__F0324356_BD8B_433F_AAB4_4959E21F6163_Handler;
import org.kie.kogito.handlers.StatusTrackerService_createStatusTracker__F2291D5C_7BD6_4FE0_A7A1_A1A411F00AA9_Handler;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.jobs.JobDescription;
import org.kie.kogito.jobs.JobsService;
import org.kie.kogito.jobs.descriptors.ProcessInstanceJobDescription;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstanceExecutionException;
import org.kie.kogito.process.Processes;
import org.kie.kogito.process.impl.AbstractProcess;
import org.kie.kogito.process.impl.AbstractProcessInstance;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;
import org.kie.kogito.services.jobs.impl.InMemoryJobContext;
import org.kie.kogito.services.jobs.impl.InMemoryProcessJobExecutorFactory;
import org.kie.kogito.services.jobs.impl.JobExecutorFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.jbpm.workflow.instance.WorkflowProcessParameters.WORKFLOW_PARAM_TRANSACTIONS;
import static org.kie.kogito.services.jobs.impl.StaticJobService.staticJobService;
import static org.kie.kogito.services.uow.StaticUnitOfWorkManger.staticUnitOfWorkManager;

public class AsyncTest {

    @Test
    public void testComplexAsyncProcess() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler());
        ProcessTestHelper.registerHandler(app, new StatusTrackerService_createStatusTracker__2B159DC3_EA7B_46A3_A632_7952108A565D_Handler());
        ProcessTestHelper.registerHandler(app, new StatusTrackerService_createStatusTracker__657C59C4_205E_4800_8BEB_B63703D1008B_Handler());
        ProcessTestHelper.registerHandler(app, new StatusTrackerService_createStatusTracker__6CC31E0B_5FDE_40E0_927B_978EB98C5406_Handler());
        ProcessTestHelper.registerHandler(app, new StatusTrackerService_createStatusTracker__F0324356_BD8B_433F_AAB4_4959E21F6163_Handler());
        ProcessTestHelper.registerHandler(app, new StatusTrackerService_createStatusTracker__F2291D5C_7BD6_4FE0_A7A1_A1A411F00AA9_Handler());

        org.kie.kogito.process.Process<ComplexAsyncModel> processDefinition = ComplexAsyncProcess.newProcess(app);
        ComplexAsyncModel model = processDefinition.createModel();
        org.kie.kogito.process.ProcessInstance<ComplexAsyncModel> instance = processDefinition.createInstance(model);
        instance.start();
        await().until(() -> instance.workItems().size() > 0);
        ProcessTestHelper.completeWorkItem(instance, Collections.emptyMap());
        await().until(() -> instance.workItems().size() > 0);
        ProcessTestHelper.completeWorkItem(instance, Collections.emptyMap());
        await().until(() -> instance.workItems().size() > 0);
        ProcessTestHelper.completeWorkItem(instance, Collections.emptyMap());
        await().until(() -> instance.workItems().size() > 0);
        ProcessTestHelper.completeWorkItem(instance, Collections.emptyMap());
        await().until(() -> instance.workItems().size() > 0);
        ProcessTestHelper.completeWorkItem(instance, Collections.emptyMap());
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    /**
     * The job scheduled for an async node must have its own id and must reference the node instance that is later
     * reported in the node events of the process instance.
     */
    @Test
    public void testAsyncJobReferencesTheActualNodeInstance() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler());
        ProcessTestHelper.registerHandler(app, new StatusTrackerService_createStatusTracker__2B159DC3_EA7B_46A3_A632_7952108A565D_Handler());
        ProcessTestHelper.registerHandler(app, new StatusTrackerService_createStatusTracker__657C59C4_205E_4800_8BEB_B63703D1008B_Handler());
        ProcessTestHelper.registerHandler(app, new StatusTrackerService_createStatusTracker__6CC31E0B_5FDE_40E0_927B_978EB98C5406_Handler());
        ProcessTestHelper.registerHandler(app, new StatusTrackerService_createStatusTracker__F0324356_BD8B_433F_AAB4_4959E21F6163_Handler());
        ProcessTestHelper.registerHandler(app, new StatusTrackerService_createStatusTracker__F2291D5C_7BD6_4FE0_A7A1_A1A411F00AA9_Handler());

        // record the job id and the node instance id of every job scheduled for a process instance
        List<String> jobIds = new CopyOnWriteArrayList<>();
        List<String> jobNodeInstanceIds = new CopyOnWriteArrayList<>();
        JobExecutorFactory delegate = new InMemoryProcessJobExecutorFactory(new InMemoryJobContext(null, staticUnitOfWorkManager(),
                app.get(Processes.class), null));
        staticJobService().registerJobExecutorFactory(new JobExecutorFactory() {
            @Override
            public Set<Class<? extends JobDescription>> types() {
                return delegate.types();
            }

            @Override
            public Runnable createNewRunnable(JobsService jobService, JobDescription jobDescription) {
                record(jobDescription);
                return delegate.createNewRunnable(jobService, jobDescription);
            }

            @Override
            public Runnable createNewRepeteableRunnable(JobsService jobService, JobDescription jobDescription) {
                record(jobDescription);
                return delegate.createNewRepeteableRunnable(jobService, jobDescription);
            }

            private void record(JobDescription jobDescription) {
                if (jobDescription instanceof ProcessInstanceJobDescription processInstanceJobDescription) {
                    jobIds.add(processInstanceJobDescription.id());
                    jobNodeInstanceIds.add(processInstanceJobDescription.nodeInstanceId());
                }
            }
        });

        // record the node instance ids reported by the node events
        List<String> eventNodeInstanceIds = new CopyOnWriteArrayList<>();
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                eventNodeInstanceIds.add(((KogitoNodeInstance) event.getNodeInstance()).getStringId());
            }
        });

        org.kie.kogito.process.Process<ComplexAsyncModel> processDefinition = ComplexAsyncProcess.newProcess(app);
        org.kie.kogito.process.ProcessInstance<ComplexAsyncModel> instance = processDefinition.createInstance(processDefinition.createModel());
        instance.start();
        for (int i = 0; i < 5; i++) {
            await().until(() -> instance.workItems().size() > 0);
            ProcessTestHelper.completeWorkItem(instance, Collections.emptyMap());
        }
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

        assertThat(jobNodeInstanceIds).as("jobs scheduled for the async nodes").isNotEmpty().doesNotContainNull();
        assertThat(eventNodeInstanceIds).containsAll(jobNodeInstanceIds);
        assertThat(jobIds).as("job ids are not the node instance ids").doesNotContainAnyElementsOf(jobNodeInstanceIds);
    }

    /**
     * A failing async service task, executed in a transactional environment, is rolled back and the error is recorded
     * afterwards (see ExceptionHandlerTransaction). The error must reference the persisted async node instance, so that
     * retriggering the process instance replaces it. Otherwise the obsolete async node instance prevents the process
     * instance from completing after a successful retry.
     */
    @Test
    public void testAsyncServiceTaskRetriggerAfterFailure() {
        Application app = ProcessTestHelper.newApplication();

        // the first execution of the service fails, any later one succeeds
        AtomicInteger invocations = new AtomicInteger();
        ProcessTestHelper.registerHandler(app, new HelloService_hello_RetryServiceTask_Handler(new HelloService() {
            @Override
            public String hello(String name) {
                if (invocations.incrementAndGet() == 1) {
                    throw new IllegalStateException("simulated service failure");
                }
                return super.hello(name);
            }
        }));
        registerTransactionalJobExecutor(app);

        org.kie.kogito.process.Process<AsyncServiceTaskRetryModel> processDefinition = AsyncServiceTaskRetryProcess.newProcess(app);
        ((WorkflowProcessImpl) ((AbstractProcess<AsyncServiceTaskRetryModel>) processDefinition).get()).setMetaData(WORKFLOW_PARAM_TRANSACTIONS.getName(), "true");

        ProcessInstance<AsyncServiceTaskRetryModel> instance = processDefinition.createInstance(processDefinition.createModel());
        instance.start();
        String id = instance.id();

        // the failed job leaves the process instance in error
        await().until(() -> processDefinition.instances().findById(id).map(ProcessInstance::status).orElse(-1) == ProcessInstance.STATE_ERROR);
        assertThat(invocations).hasValue(1);

        ProcessInstance<AsyncServiceTaskRetryModel> failed = processDefinition.instances().findById(id).orElseThrow();
        String failedNodeInstanceId = failed.error().orElseThrow().failedNodeInstanceId();
        assertThat(failed.findNodes(node -> node.getStringId().equals(failedNodeInstanceId)))
                .as("the error references a node instance of the persisted process instance")
                .isNotEmpty();

        failed.error().orElseThrow().retrigger();

        // the retried job succeeds and the process instance can complete, no obsolete async node instance remains
        await().until(() -> ProcessTestHelper.findRemovedInstance(app, id).isPresent());
        assertThat(invocations).hasValue(2);
        assertThat(processDefinition.instances().findById(id)).isEmpty();
    }

    /**
     * Executes the jobs as the transactional runtime does: when the job fails the transaction is rolled back, so that
     * nothing of the execution is persisted, and then the error is recorded in a new transaction.
     */
    private static void registerTransactionalJobExecutor(Application app) {
        JobExecutorFactory delegate = new InMemoryProcessJobExecutorFactory(new InMemoryJobContext(null, staticUnitOfWorkManager(), app.get(Processes.class), null));
        staticJobService().registerJobExecutorFactory(new JobExecutorFactory() {
            @Override
            public Set<Class<? extends JobDescription>> types() {
                return delegate.types();
            }

            @Override
            public Runnable createNewRunnable(JobsService jobService, JobDescription jobDescription) {
                return recordingErrors(delegate.createNewRunnable(jobService, jobDescription));
            }

            @Override
            public Runnable createNewRepeteableRunnable(JobsService jobService, JobDescription jobDescription) {
                return recordingErrors(delegate.createNewRepeteableRunnable(jobService, jobDescription));
            }

            private Runnable recordingErrors(Runnable job) {
                return () -> {
                    try {
                        job.run();
                    } catch (ProcessInstanceExecutionException e) {
                        String processInstanceId = e.getProcessInstanceId();
                        app.get(Processes.class).processByProcessInstanceId(processInstanceId)
                                .ifPresent(definition -> definition.instances().findById(processInstanceId)
                                        .ifPresent(failed -> ((AbstractProcessInstance<?>) failed).executeInWorkflowProcessInstanceWrite(pi -> {
                                            pi.internalSetError(e);
                                            return null;
                                        })));
                    }
                };
            }
        });
    }
}
