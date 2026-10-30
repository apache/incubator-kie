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
package org.kie.kogito.usertask.impl.lifecycle;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kie.kogito.auth.IdentityProviders;
import org.kie.kogito.internal.usertask.event.KogitoUserTaskEventSupport;
import org.kie.kogito.jobs.JobsService;
import org.kie.kogito.usertask.UserTask;
import org.kie.kogito.usertask.UserTaskAssignmentStrategy;
import org.kie.kogito.usertask.UserTaskInstances;
import org.kie.kogito.usertask.impl.DefaultUserTaskInstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultUserTaskLifeCycleTest {

    @Test
    public void testSkipSetsOutputsAndMetadata() {
        DefaultUserTaskLifeCycle lifeCycle = new DefaultUserTaskLifeCycle();
        DefaultUserTaskInstance taskInstance = new DefaultUserTaskInstance();
        taskInstance.setStatus(DefaultUserTaskLifeCycle.ACTIVE);
        taskInstance.setUserTaskLifeCycle(lifeCycle);
        taskInstance.setUserTaskEventSupport(mock(KogitoUserTaskEventSupport.class));
        taskInstance.setInstances(mock(UserTaskInstances.class));
        taskInstance.setJobsService(mock(JobsService.class));

        UserTask userTask = mock(UserTask.class);
        UserTaskAssignmentStrategy assignmentStrategy = mock(UserTaskAssignmentStrategy.class);
        when(userTask.getAssignmentStrategy()).thenReturn(assignmentStrategy);
        taskInstance.setUserTask(userTask);

        Map<String, Object> data = Map.of(
                "outputKey1", "outputVal1",
                "outputKey2", 123,
                DefaultUserTaskLifeCycle.PARAMETER_NOTIFY_LISTENERS, Boolean.FALSE);

        taskInstance.transition(DefaultUserTaskLifeCycle.SKIP, data, IdentityProviders.of(DefaultUserTaskLifeCycle.WORKFLOW_ENGINE_USER));

        assertThat(taskInstance.getStatus()).isEqualTo(DefaultUserTaskLifeCycle.OBSOLETE);
        assertThat(taskInstance.getOutputs())
                .containsEntry("outputKey1", "outputVal1")
                .containsEntry("outputKey2", 123)
                .doesNotContainKey(DefaultUserTaskLifeCycle.PARAMETER_NOTIFY_LISTENERS);
        assertThat(taskInstance.getMetadata())
                .containsEntry(DefaultUserTaskLifeCycle.PARAMETER_NOTIFY_LISTENERS, Boolean.FALSE);
    }
}
