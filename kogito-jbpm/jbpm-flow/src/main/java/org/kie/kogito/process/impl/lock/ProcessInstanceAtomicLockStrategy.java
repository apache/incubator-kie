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

    /**
     * Thread-local flag: set by {@link #signalUnlockDeferred} to suppress the automatic unlock
     * in the {@link #executeOperation} finally block when the lock release is being deferred to
     * after the surrounding transaction commits (see {@link TransactionAwareProcessInstanceLockStrategy}).
     */
    private final ThreadLocal<Boolean> deferUnlock = ThreadLocal.withInitial(() -> false);

    /**
     * Called from {@link TransactionAwareProcessInstanceLockStrategy#executeWriteOperation} (via
     * the executor lambda, before the lock's own finally block runs) to signal that
     * {@link #unlockAfterCommit} will release the lock post-commit instead.
     * Suppresses the automatic unlock in the {@link #executeOperation} finally block.
     */
    void signalUnlockDeferred(String processInstanceId) {
        deferUnlock.set(true);
        LOG.trace("Lock unlock deferred (post-commit) for {}", processInstanceId);
    }

    /**
     * Called from the post-commit callback registered by
     * {@link TransactionAwareProcessInstanceLockStrategy} to perform the actual unlock.
     * Releases the ReentrantLock and cleans up the holder entry.
     */
    void unlockAfterCommit(String processInstanceId) {
        ProcessInstanceLockHolder holder = locks.get(processInstanceId);
        if (holder != null) {
            holder.unlock();
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
        }
    }

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
            boolean deferred = deferUnlock.get();
            deferUnlock.remove();
            if (!deferred) {
                processInstanceLockHolder.unlock();
                if (!alreadyAcquired) {
                    LOG.trace("Lock released for {}", processInstanceId);
                }
            }

            // evaluate atomically if the lock is still in use before removing it.
            locks.computeIfPresent(processInstanceId, (pid, holder) -> {
                holder.removeReference();
                if (holder.isReferenced()) {
                    return holder;
                } else {
                    if (!deferred) {
                        LOG.trace("Removing lock {} from list as none is waiting for it by {}", holder.lock, pid);
                    }
                    return deferred ? holder : null;
                }
            });
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
