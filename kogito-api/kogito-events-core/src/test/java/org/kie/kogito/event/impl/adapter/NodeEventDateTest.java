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
package org.kie.kogito.event.impl.adapter;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.kogito.Addons;
import org.kie.kogito.event.impl.adapter.DataEventAdapter.DataEventAdapterConfig;
import org.kie.kogito.event.process.ProcessInstanceNodeDataEvent;
import org.kie.kogito.internal.process.runtime.KogitoNode;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcess;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcessInstance;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Node events are adapted after the node logic has run, so their timestamps must come from the
 * times recorded by the node instance and not from the time of the adaptation.
 */
class NodeEventDateTest {

    private static final long ENTER_MILLIS = 1_000_000L;
    private static final long LEAVE_MILLIS = ENTER_MILLIS + 20_000L;

    private KogitoWorkflowProcessInstance processInstance;
    private KogitoNodeInstance nodeInstance;
    private ProcessNodeEnteredEventDataEventAdapter enteredAdapter;
    private ProcessNodeLeftEventDataEventAdapter leftAdapter;

    @BeforeEach
    void setUp() {
        KogitoWorkflowProcess process = Mockito.mock(KogitoWorkflowProcess.class);
        KogitoNode node = Mockito.mock(KogitoNode.class);
        processInstance = Mockito.mock(KogitoWorkflowProcessInstance.class);
        nodeInstance = Mockito.mock(KogitoNodeInstance.class);
        Mockito.when(processInstance.getProcess()).thenReturn(process);
        Mockito.when(nodeInstance.getNode()).thenReturn(node);

        DataEventAdapterConfig config = new DataEventAdapterConfig("", Addons.EMTPY);
        enteredAdapter = new ProcessNodeEnteredEventDataEventAdapter();
        enteredAdapter.setup(config);
        leftAdapter = new ProcessNodeLeftEventDataEventAdapter();
        leftAdapter.setup(config);
    }

    @Test
    void enterEventUsesNodeTriggerTime() {
        Mockito.when(nodeInstance.getTriggerTime()).thenReturn(new Date(ENTER_MILLIS));

        ProcessInstanceNodeDataEvent event = (ProcessInstanceNodeDataEvent) enteredAdapter.adapt(triggeredEvent());

        assertThat(event.getData().getEventDate()).isEqualTo(new Date(ENTER_MILLIS));
        assertThat(event.getTime().toInstant().toEpochMilli()).isEqualTo(ENTER_MILLIS);
    }

    @Test
    void exitEventUsesNodeLeaveTime() {
        Mockito.when(nodeInstance.getTriggerTime()).thenReturn(new Date(ENTER_MILLIS));
        Mockito.when(nodeInstance.getLeaveTime()).thenReturn(new Date(LEAVE_MILLIS));

        ProcessInstanceNodeDataEvent event = (ProcessInstanceNodeDataEvent) leftAdapter.adapt(leftEvent());

        assertThat(event.getData().getEventDate()).isEqualTo(new Date(LEAVE_MILLIS));
        assertThat(event.getTime().toInstant().toEpochMilli()).isEqualTo(LEAVE_MILLIS);
    }

    @Test
    void enterAndExitEventsKeepTheDurationBetweenTriggerAndLeave() {
        Mockito.when(nodeInstance.getTriggerTime()).thenReturn(new Date(ENTER_MILLIS));
        Mockito.when(nodeInstance.getLeaveTime()).thenReturn(new Date(LEAVE_MILLIS));

        ProcessInstanceNodeDataEvent enter = (ProcessInstanceNodeDataEvent) enteredAdapter.adapt(triggeredEvent());
        ProcessInstanceNodeDataEvent exit = (ProcessInstanceNodeDataEvent) leftAdapter.adapt(leftEvent());

        assertThat(exit.getTime().toInstant().toEpochMilli() - enter.getTime().toInstant().toEpochMilli()).isEqualTo(20_000L);
    }

    @Test
    void fallsBackToCurrentTimeWhenNodeTimesAreNotAvailable() {
        long before = System.currentTimeMillis();

        ProcessInstanceNodeDataEvent enter = (ProcessInstanceNodeDataEvent) enteredAdapter.adapt(triggeredEvent());
        ProcessInstanceNodeDataEvent exit = (ProcessInstanceNodeDataEvent) leftAdapter.adapt(leftEvent());

        long after = System.currentTimeMillis();
        assertThat(enter.getData().getEventDate().getTime()).isBetween(before, after);
        assertThat(exit.getData().getEventDate().getTime()).isBetween(before, after);
        assertThat(enter.getTime().toInstant().toEpochMilli()).isBetween(before, after);
        assertThat(exit.getTime().toInstant().toEpochMilli()).isBetween(before, after);
    }

    private ProcessNodeTriggeredEvent triggeredEvent() {
        ProcessNodeTriggeredEvent event = Mockito.mock(ProcessNodeTriggeredEvent.class);
        Mockito.when(event.getProcessInstance()).thenReturn(processInstance);
        Mockito.when(event.getNodeInstance()).thenReturn(nodeInstance);
        return event;
    }

    private ProcessNodeLeftEvent leftEvent() {
        ProcessNodeLeftEvent event = Mockito.mock(ProcessNodeLeftEvent.class);
        Mockito.when(event.getProcessInstance()).thenReturn(processInstance);
        Mockito.when(event.getNodeInstance()).thenReturn(nodeInstance);
        return event;
    }
}
