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
package org.jbpm.process.core.context.exception;

import java.io.IOException;
import java.util.Collection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kie.kogito.process.workitem.WorkItemExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionHandlerPolicyTest {

    static private Collection<ExceptionHandlerPolicy> policies;

    @BeforeAll
    static void setup() {
        policies = ExceptionHandlerPolicyFactory.getHandlerPolicies();
    }

    @ParameterizedTest
    @ValueSource(strings = { "java.lang.RuntimeException", "Unknown error", "(?i)Status code 400", "(.*)code 4[0-9]{2}", "code 4[0-9]{2}" })
    void testExceptionHandlerPolicyFactory(String errorString) {
        Throwable exception = new IllegalStateException("Unknown error, status code 400");
        assertThat(test(errorString, exception)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "java.lang.RuntimeException", "Unknown error", "(?i)Status code 400", "(.*)code 4[0-9]{2}", "code 4[0-9]{2}" })
    void testExceptionChainPolicyFactory(String errorString) {
        Throwable exception = new IOException(new RuntimeException("Unknown error, status code 400"));
        assertThat(test(errorString, exception)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "[" })
    void testInvalidRegexExceptionHandlerPolicyFactory(String errorString) {
        Throwable exception = new IllegalStateException("Unknown error, status code 400");
        assertThat(test(errorString, exception)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "HTTP:500", "500" })
    void testWebExceptionHandlerPolicyFactory(String errorString) {
        Throwable exception = new WorkItemExecutionException("500", "Unknown error");
        assertThat(test(errorString, exception)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "HTTP:xyz", "xyz" })
    void testWebExceptionHandlerPolicyFactoryIncorrectFormat(String errorString) {
        Throwable exception = new WorkItemExecutionException("500", "Unknown error");
        assertThat(test(errorString, exception)).isFalse();
    }

    private boolean test(String className, Throwable exception) {
        return policies.stream().anyMatch(p -> p.test(className, exception));
    }
}
