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
package org.kie.api.internal.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for KieService interface logic including priority-based comparison,
 * service tag handling, and default method implementations.
 */
class KieServiceTest {

    /**
     * Test that default servicePriority returns 0
     */
    @Test
    void testDefaultServicePriority() {
        KieService service = new TestKieService();
        assertThat(service.servicePriority()).isEqualTo(0);
    }

    /**
     * Test that default serviceTag returns UNDEFINED
     */
    @Test
    void testDefaultServiceTag() {
        KieService service = new TestKieService();
        assertThat(service.serviceTag()).isEqualTo(KieService.UNDEFINED);
    }

    /**
     * Test that custom servicePriority can be overridden
     */
    @Test
    void testCustomServicePriority() {
        KieService service = new TestKieServiceWithPriority(100);
        assertThat(service.servicePriority()).isEqualTo(100);
    }

    /**
     * Test that custom serviceTag can be overridden
     */
    @Test
    void testCustomServiceTag() {
        KieService service = new TestKieServiceWithTag("custom-tag");
        assertThat(service.serviceTag()).isEqualTo("custom-tag");
    }

    /**
     * Test compareTo with different priorities - lower priority should be "less than"
     */
    @Test
    void testCompareToWithDifferentPriorities() {
        KieService lowPriority = new TestKieServiceWithPriority(10);
        KieService highPriority = new TestKieServiceWithPriority(20);

        // Lower priority value should be "less than" higher priority value
        assertThat(lowPriority.compareTo(highPriority)).isNegative();
        assertThat(highPriority.compareTo(lowPriority)).isPositive();
    }

    /**
     * Test compareTo with same priorities throws IllegalStateException
     */
    @Test
    void testCompareToWithSamePriorityThrowsException() {
        KieService service1 = new TestKieServiceWithPriority(10);
        KieService service2 = new TestKieServiceWithPriority(10);

        assertThatThrownBy(() -> service1.compareTo(service2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Found 2 services with same priority (10)")
                .hasMessageContaining(service1.getClass().getCanonicalName())
                .hasMessageContaining(service2.getClass().getCanonicalName());
    }

    /**
     * Test compareTo with default priorities (both 0) throws IllegalStateException
     */
    @Test
    void testCompareToWithDefaultPrioritiesThrowsException() {
        KieService service1 = new TestKieService();
        KieService service2 = new TestKieService();

        assertThatThrownBy(() -> service1.compareTo(service2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Found 2 services with same priority (0)");
    }

    /**
     * Test compareTo ordering - services should be ordered by priority (ascending)
     */
    @Test
    void testCompareToOrdering() {
        KieService priority5 = new TestKieServiceWithPriority(5);
        KieService priority10 = new TestKieServiceWithPriority(10);
        KieService priority15 = new TestKieServiceWithPriority(15);

        // Verify ordering: 5 < 10 < 15
        assertThat(priority5.compareTo(priority10)).isNegative();
        assertThat(priority10.compareTo(priority15)).isNegative();
        assertThat(priority5.compareTo(priority15)).isNegative();

        // Verify reverse ordering: 15 > 10 > 5
        assertThat(priority15.compareTo(priority10)).isPositive();
        assertThat(priority10.compareTo(priority5)).isPositive();
        assertThat(priority15.compareTo(priority5)).isPositive();
    }

    /**
     * Test compareTo with negative priorities
     */
    @Test
    void testCompareToWithNegativePriorities() {
        KieService negativePriority = new TestKieServiceWithPriority(-10);
        KieService positivePriority = new TestKieServiceWithPriority(10);
        KieService zeroPriority = new TestKieServiceWithPriority(0);

        // Negative priority should have highest precedence
        assertThat(negativePriority.compareTo(zeroPriority)).isNegative();
        assertThat(negativePriority.compareTo(positivePriority)).isNegative();
        assertThat(zeroPriority.compareTo(positivePriority)).isNegative();
    }

    /**
     * Test that UNDEFINED constant has expected value
     */
    @Test
    void testUndefinedConstant() {
        assertThat(KieService.UNDEFINED).isEqualTo("undefined");
    }

    /**
     * Test service with both custom priority and tag
     */
    @Test
    void testServiceWithPriorityAndTag() {
        KieService service = new TestKieServiceWithPriorityAndTag(50, "production");
        assertThat(service.servicePriority()).isEqualTo(50);
        assertThat(service.serviceTag()).isEqualTo("production");
    }

    /**
     * Test compareTo is consistent with priority difference
     */
    @Test
    void testCompareToConsistency() {
        KieService service1 = new TestKieServiceWithPriority(10);
        KieService service2 = new TestKieServiceWithPriority(20);

        int result = service1.compareTo(service2);
        assertThat(result).isEqualTo(10 - 20); // Should be -10
    }

    // Test helper classes

    /**
     * Basic test implementation with default priority and tag
     */
    private static class TestKieService implements KieService {
    }

    /**
     * Test implementation with custom priority
     */
    private static class TestKieServiceWithPriority implements KieService {
        private final int priority;

        public TestKieServiceWithPriority(int priority) {
            this.priority = priority;
        }

        @Override
        public int servicePriority() {
            return priority;
        }
    }

    /**
     * Test implementation with custom tag
     */
    private static class TestKieServiceWithTag implements KieService {
        private final String tag;

        public TestKieServiceWithTag(String tag) {
            this.tag = tag;
        }

        @Override
        public String serviceTag() {
            return tag;
        }
    }

    /**
     * Test implementation with both custom priority and tag
     */
    private static class TestKieServiceWithPriorityAndTag implements KieService {
        private final int priority;
        private final String tag;

        public TestKieServiceWithPriorityAndTag(int priority, String tag) {
            this.priority = priority;
            this.tag = tag;
        }

        @Override
        public int servicePriority() {
            return priority;
        }

        @Override
        public String serviceTag() {
            return tag;
        }
    }
}