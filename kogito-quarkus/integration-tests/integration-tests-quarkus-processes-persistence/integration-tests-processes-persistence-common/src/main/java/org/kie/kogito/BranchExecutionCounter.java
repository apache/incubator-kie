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
package org.kie.kogito;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Singleton;

/**
 * Non-transactional attempt counter for the asyncsplit integration test.
 * Incremented by BranchCounterWorkItemHandler on every execution attempt,
 * including those whose surrounding JTA transaction is later rolled back.
 * Because this state lives in a @Singleton CDI bean (not a DB row), it is
 * never rolled back and therefore reliably reflects the total number of
 * branch executions, not just committed ones.
 */
@Singleton
public class BranchExecutionCounter {

    private final AtomicInteger count = new AtomicInteger(0);

    public int increment() {
        return count.incrementAndGet();
    }

    public int get() {
        return count.get();
    }

    public void reset() {
        count.set(0);
    }
}
