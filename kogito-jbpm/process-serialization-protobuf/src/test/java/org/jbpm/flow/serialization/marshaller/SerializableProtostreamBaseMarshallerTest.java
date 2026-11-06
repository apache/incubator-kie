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
package org.jbpm.flow.serialization.marshaller;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.Serializable;
import java.math.BigDecimal;

import org.infinispan.protostream.MessageMarshaller.ProtoStreamReader;
import org.jbpm.flow.serialization.DeserializationFilterTestSupport;
import org.jbpm.flow.serialization.JavaSerializationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SerializableProtostreamBaseMarshallerTest {

    static class UnlistedValue implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private final DeserializationFilterTestSupport filterSupport = new DeserializationFilterTestSupport();

    private final SerializableProtostreamBaseMarshaller marshaller = new SerializableProtostreamBaseMarshaller();

    @BeforeEach
    void setUp() {
        filterSupport.setUp();
    }

    @AfterEach
    void tearDown() {
        filterSupport.tearDown();
    }

    @Test
    void readFromReturnsAllowedValue() throws IOException {
        ProtoStreamReader reader = readerOf(BigDecimal.TEN);
        assertThat(marshaller.readFrom(reader)).isEqualTo(BigDecimal.TEN);
    }

    @Test
    void readFromRejectsClassNotInAllowlist() throws IOException {
        ProtoStreamReader reader = readerOf(new UnlistedValue());
        assertThatThrownBy(() -> marshaller.readFrom(reader))
                .isInstanceOf(InvalidClassException.class);
    }

    @Test
    void readFromReturnsClassMatchingConfiguredPattern() throws IOException {
        filterSupport.allow(UnlistedValue.class.getName());
        ProtoStreamReader reader = readerOf(new UnlistedValue());
        assertThat(marshaller.readFrom(reader)).isInstanceOf(UnlistedValue.class);
    }

    private static ProtoStreamReader readerOf(Serializable value) throws IOException {
        ProtoStreamReader reader = mock(ProtoStreamReader.class);
        when(reader.readBytes("data")).thenReturn(JavaSerializationUtils.serialize(value));
        return reader;
    }
}
