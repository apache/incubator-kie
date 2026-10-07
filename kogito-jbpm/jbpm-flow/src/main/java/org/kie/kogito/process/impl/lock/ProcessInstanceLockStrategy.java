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

public interface ProcessInstanceLockStrategy {

    <T> T executeOperation(String processInstanceId, WorkflowAtomicExecutor<T> executor);

    /**
     * Executes a write operation under the lock, optionally deferring the lock release to after
     * the surrounding transaction commits.
     *
     * <p>
     * When {@code transactionRegistrar} is non-null, implementations should arrange for the
     * lock to be released only after the surrounding transaction commits, by passing an unlock
     * {@link Runnable} to the registrar. The registrar must run the action immediately when no
     * active transaction is present, so this is safe in non-transactional environments too.
     *
     * <p>
     * The default implementation ignores {@code transactionRegistrar} and delegates to
     * {@link #executeOperation}, making it safe for non-transactional or test environments.
     *
     * @param processInstanceId the process instance id
     * @param executor the operation to execute
     * @param transactionRegistrar accepts an unlock {@link Runnable} and arranges for it to run
     *        after the surrounding transaction commits; {@code null} means
     *        unlock immediately in the {@code finally} block
     * @param <T> the return type
     * @return the result of the operation
     */
    default <T> T executeWriteOperation(String processInstanceId, WorkflowAtomicExecutor<T> executor,
            Consumer<Runnable> transactionRegistrar) {
        return executeOperation(processInstanceId, executor);
    }

    /**
     * Checks if the current thread already holds the lock for the given process instance.
     *
     * @param processInstanceId the process instance id
     * @return true if the current thread already holds the lock, false otherwise
     */
    boolean isLockedByCurrentThread(String processInstanceId);

}
