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
package org.jbpm.process.instance;

import java.util.Optional;

import org.jbpm.process.instance.impl.DefaultProcessInstanceManager;
import org.junit.jupiter.api.Test;
import org.kie.kogito.internal.process.event.KogitoProcessEventSupport;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.WorkItemNotFoundException;
import org.kie.kogito.internal.process.workitem.WorkItemTerminationType;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.InternalKogitoWorkItem;
import org.kie.kogito.signal.SignalManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LightWorkItemManagerTest {

    private static final String WORK_ITEM_ID = "work-item-1";
    private static final String PROCESS_INSTANCE_ID = "process-1";
    private static final String WORK_ITEM_NAME = "Human Task";

    @Test
    void testInternalAbortWorkItemWithNullProcessInstance() {
        DefaultProcessInstanceManager processInstanceManager = new DefaultProcessInstanceManager();

        KogitoProcessEventSupport eventSupport = mock(KogitoProcessEventSupport.class);

        LightWorkItemManager workItemManager = new LightWorkItemManager(
                processInstanceManager,
                mock(SignalManager.class),
                eventSupport);

        KogitoWorkItemHandler handler = mock(KogitoWorkItemHandler.class);

        InternalKogitoWorkItem workItem = mock(InternalKogitoWorkItem.class);

        WorkItemTransition transition = mock(WorkItemTransition.class);

        when(workItem.getStringId()).thenReturn(WORK_ITEM_ID);
        when(workItem.getName()).thenReturn(WORK_ITEM_NAME);
        when(workItem.getProcessInstanceStringId())
                .thenReturn(PROCESS_INSTANCE_ID);
        when(workItem.getPhaseStatus()).thenReturn("Activated");

        when(handler.abortTransition("Activated"))
                .thenReturn(transition);

        when(handler.transitionToPhase(
                eq(workItemManager),
                eq(workItem),
                eq(transition)))
                        .thenReturn(Optional.empty());

        when(transition.termination())
                .thenReturn(Optional.of(WorkItemTerminationType.ABORT));

        workItemManager.registerWorkItemHandler(
                WORK_ITEM_NAME, handler);

        workItemManager.internalAddWorkItem(workItem);

        // Simulate process instance removal during termination.
        assertThat(processInstanceManager.getProcessInstance(
                PROCESS_INSTANCE_ID)).isNull();

        // Should not throw NullPointerException.
        assertThatCode(() -> workItemManager.internalAbortWorkItem(WORK_ITEM_ID))
                .doesNotThrowAnyException();

        // Verify the abort transition was executed.
        verify(handler).abortTransition("Activated");

        verify(handler).transitionToPhase(
                workItemManager, workItem, transition);

        // Verify the terminated work item was removed.
        assertThatThrownBy(() -> workItemManager.getWorkItem(WORK_ITEM_ID))
                .isInstanceOf(WorkItemNotFoundException.class);

        // Verify before/after transition listeners executed.
        verify(eventSupport).fireBeforeWorkItemTransition(
                any(), eq(workItem), eq(transition), any());

        verify(eventSupport).fireAfterWorkItemTransition(
                any(), eq(workItem), eq(transition), any());
    }

    @Test
    void testInternalAbortWorkItemWithValidProcessInstance() {
        DefaultProcessInstanceManager processInstanceManager = new DefaultProcessInstanceManager();

        KogitoProcessEventSupport eventSupport = mock(KogitoProcessEventSupport.class);

        LightWorkItemManager workItemManager = new LightWorkItemManager(
                processInstanceManager,
                mock(SignalManager.class),
                eventSupport);

        KogitoProcessInstance processInstance = mock(KogitoProcessInstance.class);

        when(processInstance.getStringId())
                .thenReturn(PROCESS_INSTANCE_ID);

        processInstanceManager.addProcessInstance(processInstance);

        KogitoWorkItemHandler handler = mock(KogitoWorkItemHandler.class);

        InternalKogitoWorkItem workItem = mock(InternalKogitoWorkItem.class);

        WorkItemTransition transition = mock(WorkItemTransition.class);

        when(workItem.getStringId()).thenReturn(WORK_ITEM_ID);
        when(workItem.getName()).thenReturn(WORK_ITEM_NAME);
        when(workItem.getProcessInstanceStringId())
                .thenReturn(PROCESS_INSTANCE_ID);
        when(workItem.getPhaseStatus()).thenReturn("Activated");

        when(handler.abortTransition("Activated"))
                .thenReturn(transition);

        when(handler.transitionToPhase(
                eq(workItemManager),
                eq(workItem),
                eq(transition)))
                        .thenReturn(Optional.empty());

        when(transition.termination())
                .thenReturn(Optional.of(WorkItemTerminationType.ABORT));

        workItemManager.registerWorkItemHandler(
                WORK_ITEM_NAME, handler);

        workItemManager.internalAddWorkItem(workItem);

        assertThat(processInstanceManager.getProcessInstance(
                PROCESS_INSTANCE_ID)).isNotNull();

        assertThatCode(() -> workItemManager.internalAbortWorkItem(WORK_ITEM_ID))
                .doesNotThrowAnyException();

        // The existing transition signal must still be emitted.
        verify(processInstance).signalEvent(
                "workItemTransition", transition);

        // Internal abort uses signal=false.
        verify(processInstance, never()).signalEvent(
                eq("workItemAborted"), any());

        assertThatThrownBy(() -> workItemManager.getWorkItem(WORK_ITEM_ID))
                .isInstanceOf(WorkItemNotFoundException.class);
    }
}
