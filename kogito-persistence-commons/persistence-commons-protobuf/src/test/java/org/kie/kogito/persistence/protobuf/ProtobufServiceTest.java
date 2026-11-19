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
package org.kie.kogito.persistence.protobuf;

import org.infinispan.protostream.FileDescriptorSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.persistence.api.schema.ProcessDescriptor;
import org.kie.kogito.persistence.api.schema.SchemaDescriptor;
import org.kie.kogito.persistence.api.schema.SchemaRegisteredEvent;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.enterprise.event.Event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.kie.kogito.persistence.protobuf.ProtobufService.SCHEMA_TYPE;
import static org.kie.kogito.persistence.protobuf.TestUtils.getTestFileContent;
import static org.kie.kogito.persistence.protobuf.TestUtils.getValidEntityIndexDescriptors;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProtobufServiceTest {

    FileDescriptorSource kogitoDescriptors;

    @Mock
    Event<FileDescriptorRegisteredEvent> domainModelEvent;

    @Mock
    Event<SchemaRegisteredEvent> schemaEvent;

    @InjectMocks
    ProtobufService protobufService;

    @BeforeEach
    void prepare() {
        kogitoDescriptors = new FileDescriptorSource();
        protobufService.kogitoDescriptors = kogitoDescriptors;
    }

    @Test
    void onStart() {
        String content = getTestFileContent();
        kogitoDescriptors.addProtoFile("test", content);

        protobufService.onStart(null);

        verify(schemaEvent).fire(eq(new SchemaRegisteredEvent(new SchemaDescriptor("test", content, getValidEntityIndexDescriptors(true), null), SCHEMA_TYPE)));
    }

    @Test
    void registerProtoBufferTypeWithInvalidKogitoDescriptors() {
        kogitoDescriptors.addProtoFile("test", "test");
        String content = TestUtils.getTestFileContent();

        String exceptionMessage = "";

        try {
            protobufService.registerProtoBufferType(content);
        } catch (ProtobufValidationException e) {
            exceptionMessage = e.getMessage();
        }

        assertThat(exceptionMessage.contains("Syntax error in test")).isTrue();

        verify(schemaEvent, never()).fire(any(SchemaRegisteredEvent.class));
        verify(domainModelEvent, never()).fire(any(FileDescriptorRegisteredEvent.class));
    }

    @Test
    void registerProtoBufferTypeWithInvalidProtoFile() {
        String content = TestUtils.getTestFileInvalidContent();

        String exceptionMessage = "";

        try {
            protobufService.registerProtoBufferType(content);
        } catch (ProtobufValidationException e) {
            exceptionMessage = e.getMessage();
        }

        assertThat(exceptionMessage.contains("Could not find metadata attribute in proto message")).isTrue();

        verify(schemaEvent, never()).fire(any(SchemaRegisteredEvent.class));
        verify(domainModelEvent, never()).fire(any(FileDescriptorRegisteredEvent.class));
    }

    @Test
    void registerProtoBufferTypeSchemaRegistrationFailed() {
        String testExceptionMessage = "test schema registration fail";
        doThrow(new RuntimeException(testExceptionMessage)).when(schemaEvent).fire(any(SchemaRegisteredEvent.class));
        String content = TestUtils.getTestFileContent();

        String exceptionMessage = "";

        try {
            protobufService.registerProtoBufferType(content);
        } catch (ProtobufValidationException e) {
            exceptionMessage = e.getMessage();
        }

        assertThat(exceptionMessage).isEqualTo(testExceptionMessage);

        verify(schemaEvent, times(1)).fire(new SchemaRegisteredEvent(
                new SchemaDescriptor(TestUtils.PROCESS_ID + ".proto", content, getValidEntityIndexDescriptors(true), new ProcessDescriptor(TestUtils.PROCESS_ID, TestUtils.PROCESS_TYPE)),
                SCHEMA_TYPE));
        verify(domainModelEvent, never()).fire(any(FileDescriptorRegisteredEvent.class));
    }

    @Test
    void registerProtoBufferType() {
        String content = TestUtils.getTestFileContent();

        try {
            protobufService.registerProtoBufferType(content);
        } catch (ProtobufValidationException e) {
            fail("RegisterProtoBufferType failed", e);
        }

        verify(schemaEvent, times(1)).fire(new SchemaRegisteredEvent(
                new SchemaDescriptor(TestUtils.PROCESS_ID + ".proto", content, getValidEntityIndexDescriptors(true), new ProcessDescriptor(TestUtils.PROCESS_ID, TestUtils.PROCESS_TYPE)),
                SCHEMA_TYPE));
        verify(domainModelEvent, times(1)).fire(any(FileDescriptorRegisteredEvent.class));
    }
}
