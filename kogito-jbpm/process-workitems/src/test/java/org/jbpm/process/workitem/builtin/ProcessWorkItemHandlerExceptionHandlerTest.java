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
package org.jbpm.process.workitem.builtin;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.kie.api.runtime.process.ProcessWorkItemHandlerException;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;
import org.kie.kogito.process.workitems.impl.KogitoWorkItemImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProcessWorkItemHandlerExceptionHandlerTest {

    private static class SuccessfulWorkItemHandler extends DefaultKogitoWorkItemHandler {
        @Override
        public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
            return Optional.of(this.workItemLifeCycle.newTransition("complete", workItem.getPhaseStatus(), Collections.emptyMap()));
        }
    }

    private static class FailingWorkItemHandler extends DefaultKogitoWorkItemHandler {
        private final RuntimeException exception;

        public FailingWorkItemHandler(RuntimeException exception) {
            this.exception = exception;
        }

        @Override
        public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
            throw exception;
        }
    }

    @Test
    public void testSuccessfulExecution() {
        SuccessfulWorkItemHandler originalHandler = new SuccessfulWorkItemHandler();
        ProcessWorkItemHandlerExceptionHandler handler = new ProcessWorkItemHandlerExceptionHandler(originalHandler);
        KogitoWorkItemImpl workItem = new KogitoWorkItemImpl();
        Optional<WorkItemTransition> result = handler.transitionToPhase(null, workItem, handler.startingTransition(Collections.emptyMap()));
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("complete");
    }

    @Test
    public void testFailingExecutionWithoutErrorConfig() {
        RuntimeException originalException = new IllegalStateException("Service unavailable");
        FailingWorkItemHandler originalHandler = new FailingWorkItemHandler(originalException);
        ProcessWorkItemHandlerExceptionHandler handler = new ProcessWorkItemHandlerExceptionHandler(originalHandler);
        KogitoWorkItemImpl workItem = new KogitoWorkItemImpl();

        assertThatThrownBy(() -> handler.transitionToPhase(null, workItem, handler.startingTransition(Collections.emptyMap())))
                .isSameAs(originalException);
    }

    @Test
    public void testFailingExecutionWithPartialConfig() {
        RuntimeException originalException = new IllegalStateException("Service unavailable");
        FailingWorkItemHandler originalHandler = new FailingWorkItemHandler(originalException);
        ProcessWorkItemHandlerExceptionHandler handler = new ProcessWorkItemHandlerExceptionHandler(originalHandler);

        // Only process id
        KogitoWorkItemImpl workItemOnlyProcessId = new KogitoWorkItemImpl();
        workItemOnlyProcessId.setParameter(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLER_PROCESS_ID, "error_handling");
        assertThatThrownBy(() -> handler.transitionToPhase(null, workItemOnlyProcessId, handler.startingTransition(Collections.emptyMap())))
                .isSameAs(originalException);

        // Only strategy
        KogitoWorkItemImpl workItemOnlyStrategy = new KogitoWorkItemImpl();
        workItemOnlyStrategy.setParameter(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLING_STRATEGY, "RETRY");
        assertThatThrownBy(() -> handler.transitionToPhase(null, workItemOnlyStrategy, handler.startingTransition(Collections.emptyMap())))
                .isSameAs(originalException);
    }

    @Test
    public void testFailingExecutionWithErrorConfig() {
        RuntimeException originalException = new IllegalStateException("Service unavailable");
        FailingWorkItemHandler originalHandler = new FailingWorkItemHandler(originalException);
        ProcessWorkItemHandlerExceptionHandler handler = new ProcessWorkItemHandlerExceptionHandler(originalHandler);

        KogitoWorkItemImpl workItem = new KogitoWorkItemImpl();
        workItem.setParameter(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLER_PROCESS_ID, "error_handling");
        workItem.setParameter(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLING_STRATEGY, "retry");
        workItem.setParameter(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLING_RETRIES, 3);

        assertThatThrownBy(() -> handler.transitionToPhase(null, workItem, handler.startingTransition(Collections.emptyMap())))
                .isInstanceOf(ProcessWorkItemHandlerException.class)
                .satisfies(e -> {
                    ProcessWorkItemHandlerException ex = (ProcessWorkItemHandlerException) e;
                    assertThat(ex.getProcessId()).isEqualTo("error_handling");
                    assertThat(ex.getStrategy()).isEqualTo(ProcessWorkItemHandlerException.HandlingStrategy.RETRY);
                    assertThat(ex.getRetries()).isEqualTo(3);
                    assertThat(ex.getCause()).isSameAs(originalException);
                });
    }

    @Test
    public void testFailingExecutionWithStringRetries() {
        RuntimeException originalException = new IllegalStateException("Service unavailable");
        FailingWorkItemHandler originalHandler = new FailingWorkItemHandler(originalException);
        ProcessWorkItemHandlerExceptionHandler handler = new ProcessWorkItemHandlerExceptionHandler(originalHandler);

        KogitoWorkItemImpl workItem = new KogitoWorkItemImpl();
        workItem.setParameter(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLER_PROCESS_ID, "error_handling");
        workItem.setParameter(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLING_STRATEGY, "COMPLETE");
        workItem.setParameter(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLING_RETRIES, " 5 ");

        assertThatThrownBy(() -> handler.transitionToPhase(null, workItem, handler.startingTransition(Collections.emptyMap())))
                .isInstanceOf(ProcessWorkItemHandlerException.class)
                .satisfies(e -> {
                    ProcessWorkItemHandlerException ex = (ProcessWorkItemHandlerException) e;
                    assertThat(ex.getProcessId()).isEqualTo("error_handling");
                    assertThat(ex.getStrategy()).isEqualTo(ProcessWorkItemHandlerException.HandlingStrategy.COMPLETE);
                    assertThat(ex.getRetries()).isEqualTo(5);
                    assertThat(ex.getCause()).isSameAs(originalException);
                });
    }

    @Test
    public void testFailingExecutionWithInvalidStrategy() {
        RuntimeException originalException = new IllegalStateException("Service unavailable");
        FailingWorkItemHandler originalHandler = new FailingWorkItemHandler(originalException);
        ProcessWorkItemHandlerExceptionHandler handler = new ProcessWorkItemHandlerExceptionHandler(originalHandler);

        KogitoWorkItemImpl workItem = new KogitoWorkItemImpl();
        workItem.setParameter(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLER_PROCESS_ID, "error_handling");
        workItem.setParameter(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLING_STRATEGY, "INVALID_STRATEGY");

        assertThatThrownBy(() -> handler.transitionToPhase(null, workItem, handler.startingTransition(Collections.emptyMap())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ErrorHandlingStrategy value: 'INVALID_STRATEGY'");
    }

    @Test
    public void testFailingExecutionWithInvalidRetries() {
        RuntimeException originalException = new IllegalStateException("Service unavailable");
        FailingWorkItemHandler originalHandler = new FailingWorkItemHandler(originalException);
        ProcessWorkItemHandlerExceptionHandler handler = new ProcessWorkItemHandlerExceptionHandler(originalHandler);

        KogitoWorkItemImpl workItem = new KogitoWorkItemImpl();
        workItem.setParameter(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLER_PROCESS_ID, "error_handling");
        workItem.setParameter(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLING_STRATEGY, "RETRY");
        workItem.setParameter(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLING_RETRIES, "not-a-number");

        assertThatThrownBy(() -> handler.transitionToPhase(null, workItem, handler.startingTransition(Collections.emptyMap())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ErrorHandlingRetries value: 'not-a-number'");
    }

    @Test
    public void testDoNotDoubleWrapProcessWorkItemHandlerException() {
        ProcessWorkItemHandlerException originalException = new ProcessWorkItemHandlerException("sub_process", ProcessWorkItemHandlerException.HandlingStrategy.ABORT, new RuntimeException());
        FailingWorkItemHandler originalHandler = new FailingWorkItemHandler(originalException);
        ProcessWorkItemHandlerExceptionHandler handler = new ProcessWorkItemHandlerExceptionHandler(originalHandler);

        KogitoWorkItemImpl workItem = new KogitoWorkItemImpl();
        workItem.setParameter(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLER_PROCESS_ID, "error_handling");
        workItem.setParameter(ProcessWorkItemHandlerExceptionHandler.ERROR_HANDLING_STRATEGY, "RETRY");

        assertThatThrownBy(() -> handler.transitionToPhase(null, workItem, handler.startingTransition(Collections.emptyMap())))
                .isSameAs(originalException);
    }
}
