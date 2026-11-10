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
package org.jbpm.flow.serialization.impl.marshallers;

import java.io.InvalidClassException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.jbpm.flow.serialization.DeserializationFilterTestSupport;
import org.jbpm.flow.serialization.ProcessInstanceMarshallerException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.protobuf.Any;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProtobufObjectMarshallerStrategyTest {

    static class UnlistedValue implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private final DeserializationFilterTestSupport filterSupport = new DeserializationFilterTestSupport();

    private final ProtobufObjectMarshallerStrategy strategy = new ProtobufObjectMarshallerStrategy();

    @BeforeEach
    void setUp() {
        filterSupport.setUp();
    }

    @AfterEach
    void tearDown() {
        filterSupport.tearDown();
    }

    @Test
    void unmarshallReturnsAllowedValue() {
        List<String> original = new ArrayList<>(List.of("a", "b"));
        Any marshalled = strategy.marshall(original);
        assertThat(strategy.acceptForUnmarshalling(marshalled)).isTrue();
        assertThat(strategy.unmarshall(marshalled)).isEqualTo(original);
    }

    @Test
    void unmarshallRejectsClassNotInAllowlist() {
        Any marshalled = strategy.marshall(new UnlistedValue());
        assertThatThrownBy(() -> strategy.unmarshall(marshalled))
                .isInstanceOf(ProcessInstanceMarshallerException.class)
                .hasCauseInstanceOf(InvalidClassException.class);
    }

    @Test
    void unmarshallReturnsClassMatchingConfiguredPattern() {
        filterSupport.allow(UnlistedValue.class.getName());
        Any marshalled = strategy.marshall(new UnlistedValue());
        assertThat(strategy.unmarshall(marshalled)).isInstanceOf(UnlistedValue.class);
    }
}
