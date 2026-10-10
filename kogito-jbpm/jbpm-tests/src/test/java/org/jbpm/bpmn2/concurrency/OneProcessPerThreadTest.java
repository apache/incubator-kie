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
package org.jbpm.bpmn2.concurrency;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jbpm.bpmn2.objects.Status;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitem.DefaultKogitoWorkItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Disabled("This test takes time and resources, please only run it locally")
public class OneProcessPerThreadTest {

    private static final int THREAD_COUNT = 1000;
    private static volatile AtomicInteger started = new AtomicInteger(0);
    private static volatile AtomicInteger done = new AtomicInteger(0);

    private static final Logger logger = LoggerFactory.getLogger(OneProcessPerThreadTest.class);

    @Test
    public void testMultiThreadProcessInstanceWorkItem() throws Exception {
        final ConcurrentHashMap<String, Long> workItems = new ConcurrentHashMap<String, Long>();

        try {
            final KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
            kbuilder.add(ResourceFactory.newClassPathResource("BPMN2-MultiThreadServiceProcess.bpmn"), ResourceType.BPMN2);
            KieBase kbase = kbuilder.newKieBase();

            KogitoProcessRuntime kruntime = InternalProcessRuntime.asKogitoProcessRuntime(kbase.newKieSession());

            kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Log", new DefaultKogitoWorkItemHandler() {

                @Override
                public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                    Long threadId = (Long) workItem.getParameter("id");
                    workItems.put(workItem.getProcessInstanceStringId(), threadId);
                    return Optional.empty();
                }

            });

            startThreads(kruntime);
            assertThat(workItems).hasSize(THREAD_COUNT);
        } catch (Throwable t) {
            t.printStackTrace();
            fail("Should not raise any exception: " + t.getMessage());
        }

        int i = 0;
        while (started.get() > done.get()) {
            logger.info("{} > {}", started, done);
            Thread.sleep(10 * 1000);
            if (++i > 10) {
                fail("Not all threads completed.");
            }
        }
    }

    private static void startThreads(KogitoProcessRuntime kruntime) throws Throwable {
        boolean success = true;
        final Thread[] t = new Thread[THREAD_COUNT];

        final ProcessInstanceStartRunner[] r = new ProcessInstanceStartRunner[THREAD_COUNT];
        for (int i = 0; i < t.length; i++) {
            r[i] = new ProcessInstanceStartRunner(kruntime, i, "org.drools.integrationtests.multithread");
            t[i] = new Thread(r[i], "thread-" + i);
            try {
                t[i].start();
            } catch (Throwable fault) {
                fail("Unable to complete test: " + fault.getMessage());
            }
        }
        for (int i = 0; i < t.length; i++) {
            t[i].join();
            if (r[i].getStatus() == Status.FAIL) {
                success = false;
            }
        }
        if (!success) {
            fail("Multithread test failed. Look at the stack traces for details. ");
        }
    }

    public static class ProcessInstanceStartRunner implements Runnable {

        private KogitoProcessRuntime kruntime;
        private String processId;
        private long id;
        private Status status;

        public ProcessInstanceStartRunner(KogitoProcessRuntime kruntime, int id, String processId) {
            this.kruntime = kruntime;
            this.id = id;
            this.processId = processId;
        }

        public void run() {
            started.incrementAndGet();
            try {
                Map<String, Object> params = new HashMap<String, Object>();
                params.put("id", id);
                kruntime.startProcess(processId, params);
            } catch (Throwable t) {
                this.status = Status.FAIL;
                logger.error("{} failed: {}", Thread.currentThread().getName(), t.getMessage());
                t.printStackTrace();
            }
            done.incrementAndGet();
        }

        public long getId() {
            return id;
        }

        public Status getStatus() {
            return status;
        }
    }

}
