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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceAtomicLockStrategy implements ProcessInstanceLockStrategy {

    private class ProcessInstanceLockHolder {
        Integer counter;
        ReentrantLock lock;

        public ProcessInstanceLockHolder() {
            counter = 0;
            lock = new ReentrantLock();
        }

        void lock() {
            lock.lock();
        }

        void unlock() {
            lock.unlock();
        }

        boolean isReferenced() {
            return counter > 0;
        }

        boolean isHeldByCurrentThread() {
            return lock.isHeldByCurrentThread();
        }

        public void addReference() {
            counter++;
        }

        public void removeReference() {
            counter--;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ProcessInstanceAtomicLockStrategy.class);

    private static ProcessInstanceAtomicLockStrategy INSTANCE;

    private Map<String, ProcessInstanceLockHolder> locks = new ConcurrentHashMap<>();

    @Override
    public <T> T executeOperation(String processInstanceId, WorkflowAtomicExecutor<T> executor) {
        // This is a bit tricky. To avoid resource memory leak of the reentrant lock and proper reuse we need to compute how many times
        // the lock has being referenced. We avoid that way to compute incorrectly when to release it.
        // compute and compute if present are thread safe and atomic to the bucket being computed meaning that the creation and obtaining this will rise
        // the proper counter

        ProcessInstanceLockHolder processInstanceLockHolder = locks.compute(processInstanceId, (pid, holder) -> {
            ProcessInstanceLockHolder newHolder = holder;
            if (newHolder == null) {
                newHolder = new ProcessInstanceLockHolder();
            }
            newHolder.addReference();
            LOG.trace("Creating lock {} from list as none is waiting for it by {}", newHolder.lock, pid);
            return newHolder;
        });

        // At this point this is a safe ask as if we invoked prior to this point the hold it will always return properly
        boolean alreadyAcquired = processInstanceLockHolder.isHeldByCurrentThread();
        try {
            if (!alreadyAcquired) {
                LOG.trace("About to acquire lock for {}", processInstanceId);
            }
            processInstanceLockHolder.lock();
            if (!alreadyAcquired) {
                LOG.trace("Lock acquired for {}", processInstanceId);
            }
            return executor.execute();
        } finally {
            processInstanceLockHolder.unlock();
            if (!alreadyAcquired) {
                LOG.trace("Lock released for {}", processInstanceId);
            }

            // evaluate atomically if the lock is still in use before removing it.
            locks.computeIfPresent(processInstanceId, (pid, holder) -> {
                holder.removeReference();
                if (holder.isReferenced()) {
                    return holder;
                } else {
                    LOG.trace("Removing lock {} from list as none is waiting for it by {}", holder.lock, pid);
                    return null;
                }
            });
        }
    }

    /**
     * Write path with optional deferred unlock.
     *
     * <p>
     * When {@code transactionRegistrar} is non-null and the current thread does not already
     * hold the lock (non-reentrant call), the lock release is deferred to after the surrounding
     * transaction commits. The registrar receives an unlock {@link Runnable} and must arrange
     * for it to run in an {@code afterCompletion}/{@code afterCommit} callback; when no active
     * transaction is present the registrar must run the action immediately.
     *
     * <p>
     * Reentrant calls (the lock is already held by the current thread) always release
     * immediately in the {@code finally} block, regardless of {@code transactionRegistrar},
     * because the outermost non-reentrant frame is responsible for the deferred release.
     *
     * <p>
     * On the exception path the lock is always released immediately so that other threads
     * are not blocked behind a failed operation.
     */
    @Override
    public <T> T executeWriteOperation(String processInstanceId, WorkflowAtomicExecutor<T> executor,
            Consumer<Runnable> transactionRegistrar) {
        if (transactionRegistrar == null) {
            return executeOperation(processInstanceId, executor);
        }

        boolean alreadyHeld = isLockedByCurrentThread(processInstanceId);

        ProcessInstanceLockHolder holder = locks.compute(processInstanceId, (pid, h) -> {
            ProcessInstanceLockHolder newHolder = h == null ? new ProcessInstanceLockHolder() : h;
            newHolder.addReference();
            LOG.trace("Creating lock {} from list as none is waiting for it by {}", newHolder.lock, pid);
            return newHolder;
        });

        boolean alreadyAcquired = holder.isHeldByCurrentThread();
        boolean deferred = false;
        try {
            if (!alreadyAcquired) {
                LOG.trace("About to acquire lock for {}", processInstanceId);
            }
            holder.lock();
            if (!alreadyAcquired) {
                LOG.trace("Lock acquired for {}", processInstanceId);
            }

            T result = executor.execute();

            if (!alreadyHeld) {
                // Defer lock release to after the surrounding transaction commits.
                // The registrar captures the holder reference so the post-commit callback
                // can unlock it even after this stack frame has returned.
                final ProcessInstanceLockHolder deferredHolder = holder;
                LOG.trace("Deferring lock release to post-commit for {}", processInstanceId);
                transactionRegistrar.accept(() -> {
                    deferredHolder.unlock();
                    LOG.trace("Lock released (post-commit) for {}", processInstanceId);
                    locks.computeIfPresent(processInstanceId, (pid, h) -> {
                        h.removeReference();
                        if (h.isReferenced()) {
                            return h;
                        } else {
                            LOG.trace("Removing lock {} from list as none is waiting for it by {}", h.lock, pid);
                            return null;
                        }
                    });
                });
                deferred = true;
            }
            return result;
        } finally {
            if (!deferred) {
                // Reentrant call, or exception path: release immediately.
                holder.unlock();
                if (!alreadyAcquired) {
                    LOG.trace("Lock released for {}", processInstanceId);
                }
                locks.computeIfPresent(processInstanceId, (pid, h) -> {
                    h.removeReference();
                    if (h.isReferenced()) {
                        return h;
                    } else {
                        LOG.trace("Removing lock {} from list as none is waiting for it by {}", h.lock, pid);
                        return null;
                    }
                });
            }
        }
    }

    @Override
    public boolean isLockedByCurrentThread(String processInstanceId) {
        ProcessInstanceLockHolder holder = locks.get(processInstanceId);
        return holder != null && holder.isHeldByCurrentThread();
    }

    public static synchronized ProcessInstanceAtomicLockStrategy instance() {
        if (INSTANCE == null) {
            INSTANCE = new ProcessInstanceAtomicLockStrategy();
        }
        return INSTANCE;
    }

}
