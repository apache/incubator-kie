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

import org.jbpm.bpmn2.async.ComplexAsyncModel;
import org.jbpm.bpmn2.async.ComplexAsyncProcess;
import org.jbpm.test.utils.ProcessTestHelper;
import org.junit.jupiter.api.Test;
import org.kie.kogito.Application;
import org.kie.kogito.handlers.StatusTrackerService_createStatusTracker__2B159DC3_EA7B_46A3_A632_7952108A565D_Handler;
import org.kie.kogito.handlers.StatusTrackerService_createStatusTracker__657C59C4_205E_4800_8BEB_B63703D1008B_Handler;
import org.kie.kogito.handlers.StatusTrackerService_createStatusTracker__6CC31E0B_5FDE_40E0_927B_978EB98C5406_Handler;
import org.kie.kogito.handlers.StatusTrackerService_createStatusTracker__F0324356_BD8B_433F_AAB4_4959E21F6163_Handler;
import org.kie.kogito.handlers.StatusTrackerService_createStatusTracker__F2291D5C_7BD6_4FE0_A7A1_A1A411F00AA9_Handler;
import org.kie.kogito.process.workitem.DefaultKogitoWorkItemHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
}
