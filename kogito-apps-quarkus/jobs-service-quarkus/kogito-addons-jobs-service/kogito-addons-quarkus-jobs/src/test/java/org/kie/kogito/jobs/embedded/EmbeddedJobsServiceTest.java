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
package org.kie.kogito.jobs.embedded;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.kie.kogito.event.DataEvent;
import org.kie.kogito.event.job.JobInstanceDataEvent;
import org.kie.kogito.jobs.DurationExpirationTime;
import org.kie.kogito.jobs.JobsService;
import org.kie.kogito.jobs.descriptors.ProcessInstanceJobDescription;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class EmbeddedJobsServiceTest {

    private static final String PROCESS_ID = "processId";
    private static final String PROCESS_INSTANCE_ID = "1";
    private static final String NODE_INSTANCE_ID = "node_1";
    private static final String ROOT_PROCESS_ID = "rootProcess";
    private static final String ROOT_PROCESS_INSTANCE_ID = "0";

    @Inject
    JobsService jobService;

    @Inject
    TestEventPublisher publisher;

    @Test
    public void testJobService() throws Exception {

        // testing only when we have the full lifecycle
        CountDownLatch latch = new CountDownLatch(8);
        publisher.setLatch(latch);

        ProcessInstanceJobDescription description = ProcessInstanceJobDescription.newProcessInstanceJobDescriptionBuilder()
                .generateId()
                .timerId("-1")
                .expirationTime(DurationExpirationTime.now())
                .processInstanceId(PROCESS_INSTANCE_ID)
                .rootProcessInstanceId(null)
                .processId(PROCESS_ID)
                .rootProcessId(null)
                .nodeInstanceId(NODE_INSTANCE_ID)
                .build();
        jobService.scheduleJob(description);

        ProcessInstanceJobDescription descriptionWRootProcess = ProcessInstanceJobDescription.newProcessInstanceJobDescriptionBuilder()
                .generateId()
                .timerId("-1")
                .expirationTime(DurationExpirationTime.now())
                .processInstanceId(PROCESS_INSTANCE_ID)
                .rootProcessInstanceId(ROOT_PROCESS_INSTANCE_ID)
                .processId(PROCESS_ID)
                .rootProcessId(ROOT_PROCESS_ID)
                .nodeInstanceId(NODE_INSTANCE_ID)
                .build();
        jobService.scheduleJob(descriptionWRootProcess);

        latch.await();

        List<DataEvent<?>> events = new ArrayList<>(publisher.getEvents());

        assertThat(events.size()).isEqualTo(8);

        Consumer<DataEvent<?>> noRootProcess = e -> assertThat(e)
                .hasFieldOrPropertyWithValue("kogitoRootProcessInstanceId", null)
                .hasFieldOrPropertyWithValue("kogitoRootProcessId", null);

        Consumer<DataEvent<?>> withRootProcess = e -> assertThat(e)
                .hasFieldOrPropertyWithValue("kogitoRootProcessInstanceId", ROOT_PROCESS_INSTANCE_ID)
                .hasFieldOrPropertyWithValue("kogitoRootProcessId", ROOT_PROCESS_ID);

        events.forEach(event -> {
            assertThat(event)
                    .isInstanceOf(JobInstanceDataEvent.class)
                    .hasFieldOrPropertyWithValue("kogitoProcessId", PROCESS_ID)
                    .hasFieldOrPropertyWithValue("kogitoProcessInstanceId", PROCESS_INSTANCE_ID)
                    .satisfiesAnyOf(noRootProcess, withRootProcess);
        });
    }

}
