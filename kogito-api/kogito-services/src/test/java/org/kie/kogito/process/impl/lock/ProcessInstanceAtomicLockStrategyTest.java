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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessInstanceAtomicLockStrategyTest {

    private ProcessInstanceAtomicLockStrategy strategy;
    private static final String PROCESS_INSTANCE_ID = "testProcessInstanceId-1";

    @BeforeEach
    void setUp() {
        strategy = (ProcessInstanceAtomicLockStrategy) ProcessInstanceAtomicLockStrategy.instance();
    }

    @Test
    void testProcessInstanceIsNotLockedByCurrentThread() {
        boolean isLocked = strategy.isLockedByCurrentThread(PROCESS_INSTANCE_ID);

        assertThat(isLocked).isFalse();
    }

    @Test
    void testProcessInstanceIsLockedByCurrentThread() {
        strategy.executeOperation(PROCESS_INSTANCE_ID, () -> {
            boolean isLocked = strategy.isLockedByCurrentThread(PROCESS_INSTANCE_ID);
            assertThat(isLocked).isTrue();
            return null;
        });
    }

    @Test
    void testProcessInstanceIsLockedByCurrentThreadReentrantCalls() {

        // Verifying that process instance is locked in nested operations
        strategy.executeOperation(PROCESS_INSTANCE_ID, () -> {

            assertThat(strategy.isLockedByCurrentThread(PROCESS_INSTANCE_ID)).isTrue();

            return strategy.executeOperation(PROCESS_INSTANCE_ID, () -> {

                assertThat(strategy.isLockedByCurrentThread(PROCESS_INSTANCE_ID)).isTrue();

                return strategy.executeOperation(PROCESS_INSTANCE_ID, () -> {
                    assertThat(strategy.isLockedByCurrentThread(PROCESS_INSTANCE_ID)).isTrue();
                    return "success";
                });
            });
        });

        // After all operations complete, lock should be released
        assertThat(strategy.isLockedByCurrentThread(PROCESS_INSTANCE_ID)).isFalse();
    }

    @Test
    void testDifferentProcessInstancesIsLockedByCurrentThread() {
        String processInstanceId2 = "testProcessInstanceId-2";

        strategy.executeOperation(PROCESS_INSTANCE_ID, () -> {
            // verify that only testProcessInstanceId-1 is locked
            assertThat(strategy.isLockedByCurrentThread(PROCESS_INSTANCE_ID)).isTrue();

            assertThat(strategy.isLockedByCurrentThread(processInstanceId2)).isFalse();
            return null;
        });
    }
}
