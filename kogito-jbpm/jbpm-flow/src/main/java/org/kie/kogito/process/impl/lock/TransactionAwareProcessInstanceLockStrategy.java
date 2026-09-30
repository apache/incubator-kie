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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A decorator around {@link ProcessInstanceAtomicLockStrategy} that aligns the in-process lock
 * lifetime with the surrounding transaction boundary for write operations.
 *
 * <p>
 * Problem being solved: when two threads process the same process-instance concurrently,
 * the following race can occur without this class:
 * <ol>
 * <li>Thread A acquires the lock, executes, persists the UPDATE, and releases the lock.</li>
 * <li>Thread B acquires the lock <em>before</em> the UPDATE from step 1 is committed.</li>
 * <li>Thread B reads the old optimistic-lock version from the DB and later fails with
 * {@code ProcessInstanceOptimisticLockingException}.</li>
 * </ol>
 *
 * <p>
 * Solution: for non-reentrant write operations ({@link #executeWriteOperation}), this
 * strategy defers the lock release until after the surrounding transaction commits. The caller
 * supplies a {@code transactionRegistrar} — a {@code Consumer<Runnable>} that accepts the
 * unlock action and arranges for it to run in an {@code afterCompletion} (JTA) or
 * {@code afterCommit} (Spring) callback. When no active transaction is present the registrar
 * must run the action immediately, making this safe in non-transactional environments too.
 *
 * <p>
 * Read operations ({@link #executeOperation}) are unaffected: the lock is released
 * immediately in the {@code finally} block as usual.
 */
public class TransactionAwareProcessInstanceLockStrategy implements ProcessInstanceLockStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionAwareProcessInstanceLockStrategy.class);

    private final ProcessInstanceAtomicLockStrategy delegate;

    /**
     * Accepts an unlock {@link Runnable} and registers it so it runs after the surrounding
     * transaction commits (or immediately when there is no active transaction).
     */
    private final Consumer<Runnable> transactionRegistrar;

    public TransactionAwareProcessInstanceLockStrategy(ProcessInstanceAtomicLockStrategy delegate, Consumer<Runnable> transactionRegistrar) {
        this.delegate = delegate;
        this.transactionRegistrar = transactionRegistrar;
    }

    /**
     * Read path — delegates directly; lock is always released in the {@code finally} block.
     */
    @Override
    public <T> T executeOperation(String processInstanceId, WorkflowAtomicExecutor<T> executor) {
        return delegate.executeOperation(processInstanceId, executor);
    }

    /**
     * Write path — for non-reentrant calls the lock release is deferred to after the
     * surrounding transaction commits via the {@code transactionRegistrar}.
     */
    @Override
    public <T> T executeWriteOperation(String processInstanceId, WorkflowAtomicExecutor<T> executor) {
        boolean isReentrant = delegate.isLockedByCurrentThread(processInstanceId);
        return delegate.executeOperation(processInstanceId, () -> {
            T outcome = executor.execute();
            if (!isReentrant) {
                LOG.trace("Deferring lock release to post-commit for {}", processInstanceId);
                transactionRegistrar.accept(() -> {
                    LOG.trace("Lock released (post-commit) for {}", processInstanceId);
                    delegate.unlockAfterCommit(processInstanceId);
                });
                delegate.signalUnlockDeferred(processInstanceId);
            }
            return outcome;
        });
    }

    @Override
    public boolean isLockedByCurrentThread(String processInstanceId) {
        return delegate.isLockedByCurrentThread(processInstanceId);
    }
}
