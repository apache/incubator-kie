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
package org.kie.kogito.codegen.tests;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.kogito.codegen.AbstractCodegenIT;
import org.kie.kogito.codegen.process.ProcessCodegenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

public class InvalidProcessIT extends AbstractCodegenIT {

    //Process Validations Tests
    static Stream<Arguments> invalidProcessesForTest() {
        return Stream.of(
                Arguments.of("invalid/parsing-more-than-one-start.bpmn2"),
                Arguments.of("invalid/validator-no-start.bpmn2"),
                Arguments.of("invalid/parsing-multi-connection-end.bpmn2"),
                Arguments.of("invalid/validator-no-end.bpmn2"),
                Arguments.of("invalid/parsing-service-task-no-impl.bpmn2"));
    }

    static Stream<Arguments> invalidDataMappingProcessesForTest() {
        return Stream.of(
                Arguments.of(new Object[] { "invalid/invalid-message-end-event.bpmn2",
                        "Node 'processedconsumers' [EndEvent_1] Source variable 'customerId':'java.lang.String' has different data type from 'event':'java.lang.Boolean' in data input assignment" }),
                Arguments.of(new Object[] { "invalid/invalid-message-start-event.bpmn2",
                        "Node 'StartProcess' [StartEvent_1] Target variable 'customerId':'java.lang.String' has different data type from 'event':'java.lang.Boolean' in data output assignment" }),
                Arguments.of(new Object[] { "invalid/intermediate-throw-event-message.bpmn2",
                        "Node 'Intermediate Throw Event 1' [IntermediateThrowEvent_1] Source variable 'customerId':'java.lang.String' has different data type from 'input':'java.lang.Float' in data input assignment" }),
                Arguments.of(new Object[] { "invalid/intermediate-catch-event-message.bpmn2",
                        "Node 'Intermediate Catch Event' [_4] Target variable 'customerId':'java.lang.String' has different data type from 'event':'org.acme.Customer' in data output assignment" }));
    }

    @Test
    public void testBasicUserTaskProcess() {
        assertThatCode(() -> generateCodeProcessesOnly("invalid/invalid-process-id.bpmn2")).doesNotThrowAnyException();
    }

    @Test
    public void testDuplicatedProcessId() throws Exception {
        final ProcessCodegenException exceptionBpmn =
                (ProcessCodegenException) catchThrowable(() -> generateCodeProcessesOnly("invalid/duplicated-process-id-1.bpmn2",
                        "invalid/duplicated-process-id-2.bpmn2"));
        assertThat(exceptionBpmn.getMessage()).isEqualTo("Duplicated item found with id duplicated. Please review the .bpmn files");
    }

    @ParameterizedTest
    @MethodSource("invalidProcessesForTest")
    public void testInvalidProcess(String processFile) {
        assertThatThrownBy(() -> generateCodeProcessesOnly(processFile))
                .isInstanceOf(ProcessCodegenException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidDataMappingProcessesForTest")
    public void testInvalidDataMappingProcesses(String processFile, String message) {
        assertThatThrownBy(() -> generateCodeProcessesOnly(processFile))
                .isInstanceOf(ProcessCodegenException.class)
                .hasMessageContaining(message);
    }
}
