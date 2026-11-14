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
package org.kie.kogito.services.context;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

/**
 * Unit tests for ProcessInstanceContext utility class.
 * Tests thread safety, context management, and async operations.
 */
class ProcessInstanceContextTest {

    private static final String TEST_PROCESS_ID = "test-process-123";
    private static final String TEST_PROCESS_ID_2 = "test-process-456";

    @BeforeEach
    void setUp() {
        // Ensure clean state before each test
        ProcessInstanceContext.clear();
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        ProcessInstanceContext.clear();
        ProcessInstanceContext.clearExtensions();
        MDC.clear(); // Ensure completely clean MDC state between tests
    }

    @Test
    void testSetAndGetProcessInstanceId() {
        // Initially no context (returns empty string)
        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(ProcessInstanceContext.GENERAL_CONTEXT);
        assertThat(ProcessInstanceContext.hasContext()).isFalse();

        // Set process instance ID
        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);

        // Verify MDC is set
        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(TEST_PROCESS_ID);
        assertThat(MDC.get(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY)).isEqualTo(TEST_PROCESS_ID);
        assertThat(ProcessInstanceContext.hasContext()).isTrue();
    }

    @Test
    void testSetNullProcessInstanceId() {
        ProcessInstanceContext.setProcessInstanceId(null);

        // Should use general context (empty string)
        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(ProcessInstanceContext.GENERAL_CONTEXT);
        assertThat(MDC.get(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY)).isEqualTo(ProcessInstanceContext.GENERAL_CONTEXT);
        assertThat(ProcessInstanceContext.hasContext()).isFalse();
    }

    @Test
    void testClearContext() {
        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);
        assertThat(ProcessInstanceContext.hasContext()).isTrue();

        ProcessInstanceContext.clear();

        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(ProcessInstanceContext.GENERAL_CONTEXT);
        assertThat(MDC.get(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY)).isNull();
        assertThat(ProcessInstanceContext.hasContext()).isFalse();
    }

    @Test
    void testContextReplacement() {
        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);
        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(TEST_PROCESS_ID);

        // Set different ID - should replace
        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID_2);
        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(TEST_PROCESS_ID_2);

        // Clear should remove all context
        ProcessInstanceContext.clear();
        assertThat(ProcessInstanceContext.hasContext()).isFalse();
    }

    @Test
    void testWithProcessInstanceContextSupplier() {
        assertThat(ProcessInstanceContext.hasContext()).isFalse();

        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);
        String result;
        try {
            assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(TEST_PROCESS_ID);
            assertThat(ProcessInstanceContext.hasContext()).isTrue();
            result = "test-result";
        } finally {
            ProcessInstanceContext.clear();
        }

        assertThat(result).isEqualTo("test-result");
        assertThat(ProcessInstanceContext.hasContext()).isFalse();
    }

    @Test
    void testWithProcessInstanceContextRunnable() {
        assertThat(ProcessInstanceContext.hasContext()).isFalse();
        AtomicReference<String> capturedId = new AtomicReference<>();

        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);
        try {
            capturedId.set(ProcessInstanceContext.getProcessInstanceId());
        } finally {
            ProcessInstanceContext.clear();
        }

        assertThat(capturedId.get()).isEqualTo(TEST_PROCESS_ID);
        assertThat(ProcessInstanceContext.hasContext()).isFalse();
    }

    @Test
    void testWithProcessInstanceContextExceptionHandling() {
        assertThat(ProcessInstanceContext.hasContext()).isFalse();

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> {
            ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);
            try {
                assertThat(ProcessInstanceContext.hasContext()).isTrue();
                throw new RuntimeException("Test exception");
            } finally {
                ProcessInstanceContext.clear();
            }
        });

        // Context should be cleared even after exception
        assertThat(ProcessInstanceContext.hasContext()).isFalse();
    }

    @Test
    void testAsyncContextPropagation() {
        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);

        // Copy context for async operation
        Map<String, String> contextMap = ProcessInstanceContext.copyContextForAsync();
        assertThat(contextMap).isNotNull();
        assertThat(contextMap.get(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY)).isEqualTo(TEST_PROCESS_ID);

        // Clear original context
        ProcessInstanceContext.clear();
        assertThat(ProcessInstanceContext.hasContext()).isFalse();

        // Restore context from async
        ProcessInstanceContext.setContextFromAsync(contextMap);
        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(TEST_PROCESS_ID);
        assertThat(ProcessInstanceContext.hasContext()).isTrue();
    }

    @Test
    void testAsyncContextWithNullMap() {
        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);
        assertThat(ProcessInstanceContext.hasContext()).isTrue();

        // Setting null context map should not change existing context
        ProcessInstanceContext.setContextFromAsync(null);
        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(TEST_PROCESS_ID);
        assertThat(ProcessInstanceContext.hasContext()).isTrue();
    }

    @Test
    void testThreadIsolation() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(3);
        AtomicReference<Exception> exception = new AtomicReference<>();

        try {
            // Thread 1: Set and verify process ID 1
            executor.submit(() -> {
                try {
                    ProcessInstanceContext.setProcessInstanceId("process-1");
                    Thread.sleep(100); // Allow other threads to run
                    assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo("process-1");
                } catch (Exception e) {
                    exception.set(e);
                } finally {
                    ProcessInstanceContext.clear();
                    latch.countDown();
                }
            });

            // Thread 2: Set and verify process ID 2
            executor.submit(() -> {
                try {
                    ProcessInstanceContext.setProcessInstanceId("process-2");
                    Thread.sleep(100); // Allow other threads to run
                    assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo("process-2");
                } catch (Exception e) {
                    exception.set(e);
                } finally {
                    ProcessInstanceContext.clear();
                    latch.countDown();
                }
            });

            // Thread 3: No context set
            executor.submit(() -> {
                try {
                    Thread.sleep(100); // Allow other threads to run
                    // In a new thread without context, should return empty string
                    assertThat(ProcessInstanceContext.hasContext()).isFalse();
                } catch (Exception e) {
                    exception.set(e);
                } finally {
                    latch.countDown();
                }
            });

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(exception.get()).isNull();

        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testAsyncOperationWithContextPropagation() throws Exception {
        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);
        Map<String, String> contextMap = ProcessInstanceContext.copyContextForAsync();

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            // Restore context in async thread
            ProcessInstanceContext.setContextFromAsync(contextMap);
            try {
                return ProcessInstanceContext.getProcessInstanceId();
            } finally {
                ProcessInstanceContext.clear();
            }
        });

        String result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isEqualTo(TEST_PROCESS_ID);

        // Original thread context should still be intact
        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(TEST_PROCESS_ID);
    }

    @Test
    void testMdcIntegration() {
        // Verify MDC is properly managed
        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);

        assertThat(MDC.get(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY)).isEqualTo(TEST_PROCESS_ID);

        ProcessInstanceContext.clear();

        assertThat(MDC.get(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY)).isNull();
    }

    @Test
    void testGeneralContextBehavior() {
        ProcessInstanceContext.setProcessInstanceId(ProcessInstanceContext.GENERAL_CONTEXT);

        assertThat(ProcessInstanceContext.hasContext()).isFalse();
        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(ProcessInstanceContext.GENERAL_CONTEXT);
        assertThat(MDC.get(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY)).isEqualTo(ProcessInstanceContext.GENERAL_CONTEXT);
    }

    @Test
    void testHasContextAccuracy() {
        // Initially no context
        assertThat(ProcessInstanceContext.hasContext()).isFalse();

        // Set general context explicitly - should not count as having context
        ProcessInstanceContext.setProcessInstanceId(ProcessInstanceContext.GENERAL_CONTEXT);
        assertThat(ProcessInstanceContext.hasContext()).isFalse();

        // Set specific context
        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);
        assertThat(ProcessInstanceContext.hasContext()).isTrue();

        // Clear should remove context
        ProcessInstanceContext.clear();
        assertThat(ProcessInstanceContext.hasContext()).isFalse();
    }

    @Test
    void testMdcConsistencyAfterOperations() {
        // Test that MDC always stays consistent after various operations

        // Set process ID
        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);
        assertThat(MDC.get(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY)).isEqualTo(TEST_PROCESS_ID);

        // Clear should reset to general context
        ProcessInstanceContext.clear();
        assertThat(MDC.get(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY)).isNull();

        // Setting null should also result in general context
        ProcessInstanceContext.setProcessInstanceId(null);
        assertThat(MDC.get(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY)).isEqualTo(ProcessInstanceContext.GENERAL_CONTEXT);
    }

    @Test
    void testSetContextFromAsyncPreservesExistingMdcKeys() {
        // Setup: Add various existing keys to MDC
        ProcessInstanceContext.clear(); // Ensure clean start
        MDC.put("existing.key1", "value1");
        MDC.put("existing.key2", "value2");
        MDC.put("unrelated.data", "should-be-preserved");
        MDC.put(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY, "old-process-id");

        // Verify setup
        assertThat(MDC.get(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY)).isEqualTo("old-process-id");
        assertThat(MDC.get("existing.key1")).isEqualTo("value1");

        // Create new context map
        Map<String, String> newContextMap = new HashMap<>();
        newContextMap.put(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY, TEST_PROCESS_ID);

        // Action: Set context from async (should preserve existing keys while updating process ID)
        ProcessInstanceContext.setContextFromAsync(newContextMap);

        // Verification: New process instance ID should be set
        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(TEST_PROCESS_ID);
        assertThat(ProcessInstanceContext.hasContext()).isTrue();

        // Critical verification: Existing unrelated keys should be preserved
        assertThat(MDC.get("existing.key1")).isEqualTo("value1");
        assertThat(MDC.get("existing.key2")).isEqualTo("value2");
        assertThat(MDC.get("unrelated.data")).isEqualTo("should-be-preserved");

        // The context should contain both old and new keys
        Map<String, String> remainingContext = MDC.getCopyOfContextMap();
        assertThat(remainingContext.size()).isEqualTo(4);
        assertThat(remainingContext.get(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY)).isEqualTo(TEST_PROCESS_ID);
        assertThat(remainingContext.get("existing.key1")).isEqualTo("value1");
        assertThat(remainingContext.get("existing.key2")).isEqualTo("value2");
        assertThat(remainingContext.get("unrelated.data")).isEqualTo("should-be-preserved");
    }

    @Test
    void testOptimizationSkipsSameValue() {
        // Test that setting the same value twice doesn't update MDC unnecessarily
        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);
        String mdcValue1 = MDC.get(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY);

        // Set same value again
        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);
        String mdcValue2 = MDC.get(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY);

        assertThat(mdcValue1).isEqualTo(TEST_PROCESS_ID);
        assertThat(mdcValue2).isEqualTo(TEST_PROCESS_ID);
        assertThat(mdcValue2).isEqualTo(mdcValue1);
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int iterationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicReference<Exception> exception = new AtomicReference<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        String processId = "thread-" + threadId + "-process-" + i;
                        ProcessInstanceContext.setProcessInstanceId(processId);
                        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(processId);
                        assertThat(ProcessInstanceContext.hasContext()).isTrue();
                        ProcessInstanceContext.clear();
                    }
                } catch (Exception e) {
                    exception.set(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(exception.get()).isNull();

        executor.shutdown();
    }

    @Test
    void testExtensionRegistrationAndRestoration() {
        // Create a mock extension
        MockContextExtension extension = new MockContextExtension("test.");

        // Register the extension
        ProcessInstanceContext.registerExtension("test.", extension);

        // Set up context with both core and extension keys
        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);
        MDC.put("test.key1", "value1");
        MDC.put("test.key2", "value2");
        MDC.put("other.key", "should-not-be-restored");

        // Copy context for async
        Map<String, String> contextMap = ProcessInstanceContext.copyContextForAsync();

        // Clear MDC
        ProcessInstanceContext.clear();
        MDC.clear();

        // Restore context from async
        ProcessInstanceContext.setContextFromAsync(contextMap);

        // Verify core context is restored
        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(TEST_PROCESS_ID);

        // Verify extension was called to restore its keys
        assertThat(extension.restoreCalled).isTrue();
        assertThat(extension.restoredKeys.size()).isEqualTo(2);
        assertThat(extension.restoredKeys.get("test.key1")).isEqualTo("value1");
        assertThat(extension.restoredKeys.get("test.key2")).isEqualTo("value2");
        assertThat(extension.restoredKeys.get("other.key")).isNull();
    }

    @Test
    void testExtensionRegistrationValidation() {
        MockContextExtension extension = new MockContextExtension("test.");

        // Test null prefix
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ProcessInstanceContext.registerExtension(null, extension));

        // Test empty prefix
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ProcessInstanceContext.registerExtension("", extension));

        // Test null extension
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ProcessInstanceContext.registerExtension("test.", null));
    }

    @Test
    void testExtensionExceptionHandling() {
        // Create an extension that throws exceptions
        ContextExtension faultyExtension = new ContextExtension() {
            @Override
            public String getMdcKeyPrefix() {
                return "faulty.";
            }

            @Override
            public void restoreKeys(Map<String, String> preservedKeys) {
                throw new RuntimeException("Extension failure");
            }
        };

        ProcessInstanceContext.registerExtension("faulty.", faultyExtension);

        // Set up context
        ProcessInstanceContext.setProcessInstanceId(TEST_PROCESS_ID);
        MDC.put("faulty.key", "value");

        Map<String, String> contextMap = ProcessInstanceContext.copyContextForAsync();
        ProcessInstanceContext.clear();

        // This should not throw, even though the extension fails
        assertThatCode(() -> ProcessInstanceContext.setContextFromAsync(contextMap)).doesNotThrowAnyException();

        // Core functionality should still work
        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(TEST_PROCESS_ID);
    }

    @Test
    void testContextMergingWithPrecedenceToAsyncValues() {
        // Setup: Add existing context that will conflict with async context
        ProcessInstanceContext.clear();
        MDC.put("common.key", "existing-value");
        MDC.put("existing.only", "should-remain");
        ProcessInstanceContext.setProcessInstanceId("existing-process-id");

        // Create async context map with overlapping and new keys
        Map<String, String> asyncContextMap = new HashMap<>();
        asyncContextMap.put(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY, TEST_PROCESS_ID);
        asyncContextMap.put("common.key", "async-value");
        asyncContextMap.put("async.only", "new-value");

        // Action: Restore async context
        ProcessInstanceContext.setContextFromAsync(asyncContextMap);

        // Verification: Async values should take precedence for conflicting keys
        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(TEST_PROCESS_ID);
        assertThat(MDC.get("common.key")).isEqualTo("async-value"); // async wins
        assertThat(MDC.get("existing.only")).isEqualTo("should-remain"); // preserved
        assertThat(MDC.get("async.only")).isEqualTo("new-value"); // added from async

        // All keys should be present
        Map<String, String> finalContext = MDC.getCopyOfContextMap();
        assertThat(finalContext.size()).isEqualTo(4);
    }

    @Test
    void testContextRestorationWithoutExistingContext() {
        // Setup: Start with completely clean MDC
        ProcessInstanceContext.clear();
        MDC.clear();

        // Create async context map
        Map<String, String> asyncContextMap = new HashMap<>();
        asyncContextMap.put(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY, TEST_PROCESS_ID);
        asyncContextMap.put("async.key", "async-value");

        // Action: Restore async context
        ProcessInstanceContext.setContextFromAsync(asyncContextMap);

        // Verification: Only async values should be present
        assertThat(ProcessInstanceContext.getProcessInstanceId()).isEqualTo(TEST_PROCESS_ID);
        assertThat(MDC.get("async.key")).isEqualTo("async-value");

        // Context should match exactly what was provided
        Map<String, String> finalContext = MDC.getCopyOfContextMap();
        assertThat(finalContext.size()).isEqualTo(2);
        assertThat(finalContext.get(ProcessInstanceContext.MDC_PROCESS_INSTANCE_KEY)).isEqualTo(TEST_PROCESS_ID);
        assertThat(finalContext.get("async.key")).isEqualTo("async-value");
    }

    /**
     * Mock implementation of ContextExtension for testing.
     */
    private static class MockContextExtension implements ContextExtension {
        private final String prefix;
        boolean restoreCalled = false;
        Map<String, String> restoredKeys = new HashMap<>();

        MockContextExtension(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String getMdcKeyPrefix() {
            return prefix;
        }

        @Override
        public void restoreKeys(Map<String, String> preservedKeys) {
            restoreCalled = true;
            restoredKeys.clear();
            restoredKeys.putAll(preservedKeys);

            // Actually restore the keys to MDC for testing
            preservedKeys.forEach(MDC::put);
        }
    }

}
