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
package org.kie.kogito.process.impl.lock;

import java.util.function.Consumer;

import org.kie.kogito.services.context.ProcessInstanceContext;

/**
 * A context-aware wrapper for ProcessInstanceLockStrategy that automatically manages
 * process instance context (logging MDC) for all operations executed through the lock strategy.
 *
 * This implementation follows the Decorator pattern to add context management capabilities
 * without modifying the core process instance execution logic, adhering to the Single
 * Responsibility Principle.
 *
 * Context management (setting and clearing the process instance ID in MDC) is handled
 * transparently by this wrapper, ensuring that all log messages within the operation
 * execution include the correct process instance ID.
 */
public class ContextAwareProcessInstanceLockStrategy implements ProcessInstanceLockStrategy {

    private final ProcessInstanceLockStrategy delegate;

    /**
     * Creates a new context-aware lock strategy that wraps an existing lock strategy.
     *
     * @param delegate the underlying lock strategy to delegate to
     */
    public ContextAwareProcessInstanceLockStrategy(ProcessInstanceLockStrategy delegate) {
        this.delegate = delegate;
    }

    /**
     * Executes the given operation within the lock and process instance context.
     * The process instance ID is automatically set in the MDC before execution
     * and cleared after execution completes (even if an exception is thrown).
     *
     * @param processInstanceId the process instance ID for context and locking
     * @param operation the operation to execute
     * @param <T> the return type of the operation
     * @return the result of the operation
     */
    @Override
    public <T> T executeOperation(String processInstanceId, WorkflowAtomicExecutor<T> operation) {
        return delegate.executeOperation(processInstanceId, () -> {
            ProcessInstanceContext.setProcessInstanceId(processInstanceId);
            try {
                return operation.execute();
            } finally {
                ProcessInstanceContext.clear();
            }
        });
    }

    @Override
    public <T> T executeWriteOperation(String processInstanceId, WorkflowAtomicExecutor<T> operation,
            Consumer<Runnable> transactionRegistrar) {
        return delegate.executeWriteOperation(processInstanceId, () -> {
            ProcessInstanceContext.setProcessInstanceId(processInstanceId);
            try {
                return operation.execute();
            } finally {
                ProcessInstanceContext.clear();
            }
        }, transactionRegistrar);
    }

    @Override
    public boolean isLockedByCurrentThread(String processInstanceId) {
        return delegate.isLockedByCurrentThread(processInstanceId);
    }
}
