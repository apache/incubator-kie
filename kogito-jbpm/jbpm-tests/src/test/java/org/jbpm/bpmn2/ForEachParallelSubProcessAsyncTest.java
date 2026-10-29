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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jbpm.bpmn2.foreach.MiChildProcessProcess;
import org.jbpm.bpmn2.foreach.MiParentProcessModel;
import org.jbpm.bpmn2.foreach.MiParentProcessProcess;
import org.jbpm.test.utils.ProcessTestHelper;
import org.junit.jupiter.api.Test;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.kogito.Application;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that a parallel multi-instance call activity whose child subprocess
 * contains an async node (e.g. a {@code customAsync} script task) and wait-state
 * user tasks launches all child instances in parallel, rather than falling back
 * to sequential execution.
 *
 * <p>
 * Previously, {@code ForEachNodeInstance} fell back to sequential mode when
 * child instances contained asynchronous nodes.
 */
public class ForEachParallelSubProcessAsyncTest {

    @Test
    public void testParallelMISubProcessWithAsyncNodeCreatesAllChildInstancesUpFront() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler());

        final List<String> childInstanceIds = new ArrayList<>();
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void beforeProcessStarted(ProcessStartedEvent event) {
                if ("MiChildProcess".equals(event.getProcessInstance().getProcessId())) {
                    childInstanceIds.add(((KogitoProcessInstance) event.getProcessInstance()).getStringId());
                }
            }
        });

        MiChildProcessProcess.newProcess(app);
        Process<MiParentProcessModel> parentProcess = MiParentProcessProcess.newProcess(app);

        MiParentProcessModel model = parentProcess.createModel();
        model.setItems(new ArrayList<>(Arrays.asList(1, 2, 3)));

        ProcessInstance<MiParentProcessModel> parentInstance = parentProcess.createInstance(model);
        parentInstance.start();

        // All 3 child instances must have been started in parallel before any
        // human task is completed. With the old (broken) behaviour the ForEach
        // would serialise and only 1 child would be active at this point.
        assertThat(childInstanceIds)
                .as("All 3 child subprocess instances should be started in parallel")
                .hasSize(3);

        assertThat(parentInstance.status())
                .as("Parent should still be active, waiting for child subprocesses")
                .isEqualTo(ProcessInstance.STATE_ACTIVE);
    }
}
