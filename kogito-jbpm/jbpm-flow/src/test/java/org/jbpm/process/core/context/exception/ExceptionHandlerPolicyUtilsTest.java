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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jbpm.process.core.context.exception.ExceptionHandlerPolicyUtils.isException;
import static org.jbpm.process.core.context.exception.ExceptionHandlerPolicyUtils.isExceptionErrorCode;

public class ExceptionHandlerPolicyUtilsTest {

    @Test
    void testIsException() {
        assertThat(isException("java.lang.RuntimeException", RuntimeException.class)).isTrue();
        assertThat(isException("java.lang.Exception", RuntimeException.class)).isFalse();
        assertThat(isException("java.lang.runtimeException", RuntimeException.class)).isFalse();
    }

    @Test
    void testIsExceptionClass() {
        assertThat(isExceptionErrorCode("java.lang.RuntimeException")).isTrue();
        assertThat(isExceptionErrorCode("java.lang.Exception")).isTrue();
        assertThat(isExceptionErrorCode("pepe@hotmail.com")).isFalse();
        assertThat(isExceptionErrorCode("java.lang.runtimeException pepe")).isFalse();
    }
}
