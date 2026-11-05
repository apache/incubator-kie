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
package org.jbpm.flow.serialization;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.runtime.process.ProcessWorkItemHandlerException;
import org.kie.api.runtime.process.ProcessWorkItemHandlerException.HandlingStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JavaSerializationUtilsTest {

    // -- test data ----------------------------------------------------------

    static class Person implements Serializable {
        private static final long serialVersionUID = 1L;
        final String name;
        final int age;

        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Person))
                return false;
            Person p = (Person) o;
            return age == p.age && Objects.equals(name, p.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age);
        }
    }

    static class NotSerializable {
        // intentionally does not implement Serializable
    }

    /**
     * Not covered by the default allowlist. Records whether its readObject ran.
     */
    static class UnlistedValue implements Serializable {
        private static final long serialVersionUID = 1L;
        static volatile boolean readObjectInvoked;

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            readObjectInvoked = true;
        }
    }

    static class UnlistedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private final DeserializationFilterTestSupport filterSupport = new DeserializationFilterTestSupport();

    @BeforeEach
    void setUp() {
        filterSupport.setUp();
        UnlistedValue.readObjectInvoked = false;
    }

    @AfterEach
    void tearDown() {
        filterSupport.tearDown();
    }

    // -- tests --------------------------------------------------------------

    @Test
    void roundTripString() throws Exception {
        String original = "hello world";
        byte[] bytes = JavaSerializationUtils.serialize(original);
        assertThat(bytes).isNotEmpty();
        assertThat(JavaSerializationUtils.deserialize(bytes)).isEqualTo(original);
    }

    @Test
    void roundTripCustomObject() throws Exception {
        filterSupport.allow(Person.class.getName());
        Person original = new Person("Alice", 30);
        byte[] bytes = JavaSerializationUtils.serialize(original);
        assertThat(bytes).isNotEmpty();
        assertThat(JavaSerializationUtils.deserialize(bytes)).isEqualTo(original);
    }

    @Test
    void roundTripNull() throws Exception {
        byte[] bytes = JavaSerializationUtils.serialize(null);
        assertThat(bytes).isNotEmpty(); // OOS writes a null token, not empty bytes
        assertThat(JavaSerializationUtils.deserialize(bytes)).isNull();
    }

    @Test
    void roundTripCollection() throws Exception {
        List<String> original = List.of("a", "b", "c");
        byte[] bytes = JavaSerializationUtils.serialize(original);
        assertThat(JavaSerializationUtils.deserialize(bytes)).isEqualTo(original);
    }

    @Test
    void serializeBytesAreCompleteAndDeserializable() throws Exception {
        // Regression: toByteArray() called before OOS.close() produced truncated bytes
        // causing EOFException on deserialization. Verify bytes are always fully flushed.
        filterSupport.allow(Person.class.getName());
        Person original = new Person("Bob", 42);
        byte[] bytes = JavaSerializationUtils.serialize(original);
        // A valid Java serialization stream starts with the magic bytes 0xACED
        assertThat(bytes[0]).isEqualTo((byte) 0xAC);
        assertThat(bytes[1]).isEqualTo((byte) 0xED);
        // And can be deserialized without EOFException
        assertThat(JavaSerializationUtils.deserialize(bytes)).isEqualTo(original);
    }

    @Test
    void serializeNonSerializableThrows() {
        assertThatThrownBy(() -> JavaSerializationUtils.serialize(new NotSerializable()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void deserializeCorruptBytesThrows() {
        byte[] garbage = new byte[] { 0x00, 0x01, 0x02, 0x03 };
        assertThatThrownBy(() -> JavaSerializationUtils.deserialize(garbage))
                .isInstanceOf(IOException.class);
    }

    @Test
    void deserializeUsesThreadContextClassLoader() throws Exception {
        // Verify that deserialize resolves classes through the thread-context classloader.
        // We swap in a classloader that can see Person, confirm round-trip still works.
        filterSupport.allow(Person.class.getName());
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(Person.class.getClassLoader());
            Person person = new Person("Carol", 25);
            byte[] bytes = JavaSerializationUtils.serialize(person);
            assertThat(JavaSerializationUtils.deserialize(bytes)).isEqualTo(person);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    // -- deserialization filter ---------------------------------------------

    static Stream<Arguments> jdkValueTypes() {
        return Stream.of(
                Arguments.of(BigDecimal.valueOf(10L)),
                Arguments.of(LocalDate.of(2026, 1, 31)),
                Arguments.of(new Timestamp(1_000L)),
                Arguments.of(new java.sql.Date(1_000L)),
                Arguments.of(new Time(1_000L)),
                Arguments.of(new HashMap<>(Map.of("key", new ArrayList<>(List.of("a", "b"))))),
                Arguments.of((Object) new int[] { 1, 2, 3 }),
                Arguments.of((Object) new String[][] { { "a" }, { "b" } }));
    }

    @ParameterizedTest
    @MethodSource("jdkValueTypes")
    void deserializeAllowsJdkValueTypesByDefault(Object original) throws Exception {
        byte[] bytes = JavaSerializationUtils.serialize(original);
        assertThat(JavaSerializationUtils.deserialize(bytes)).isEqualTo(original);
    }

    @Test
    void deserializeAllowsWorkItemHandlerExceptionByDefault() throws Exception {
        ProcessWorkItemHandlerException original =
                new ProcessWorkItemHandlerException("errorHandlingProcess", HandlingStrategy.RETRY, new IllegalStateException("handler failed"), 2);
        byte[] bytes = JavaSerializationUtils.serialize(original);

        Object deserialized = JavaSerializationUtils.deserialize(bytes);

        assertThat(deserialized).isInstanceOfSatisfying(ProcessWorkItemHandlerException.class, exception -> {
            assertThat(exception.getProcessId()).isEqualTo("errorHandlingProcess");
            assertThat(exception.getStrategy()).isEqualTo(HandlingStrategy.RETRY);
            assertThat(exception.getRetries()).isEqualTo(2);
            assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class).hasMessage("handler failed");
        });
    }

    @Test
    void deserializeRejectsWorkItemHandlerExceptionWithCauseNotInAllowlist() throws Exception {
        ProcessWorkItemHandlerException original =
                new ProcessWorkItemHandlerException("errorHandlingProcess", HandlingStrategy.ABORT, new UnlistedException());
        byte[] bytes = JavaSerializationUtils.serialize(original);

        assertThatThrownBy(() -> JavaSerializationUtils.deserialize(bytes))
                .isInstanceOf(InvalidClassException.class);
    }

    @Test
    void deserializeRejectsClassNotInAllowlist() throws Exception {
        byte[] bytes = JavaSerializationUtils.serialize(new UnlistedValue());
        assertThatThrownBy(() -> JavaSerializationUtils.deserialize(bytes))
                .isInstanceOf(InvalidClassException.class);
        assertThat(UnlistedValue.readObjectInvoked).isFalse();
    }

    @Test
    void deserializeRejectsClassNotInAllowlistInsideAllowedCollection() throws Exception {
        byte[] bytes = JavaSerializationUtils.serialize(new ArrayList<>(List.of("value", new UnlistedValue())));
        assertThatThrownBy(() -> JavaSerializationUtils.deserialize(bytes))
                .isInstanceOf(InvalidClassException.class);
        assertThat(UnlistedValue.readObjectInvoked).isFalse();
    }

    @Test
    void deserializeRejectsArrayOfClassNotInAllowlist() throws Exception {
        byte[] bytes = JavaSerializationUtils.serialize(new UnlistedValue[] { new UnlistedValue() });
        assertThatThrownBy(() -> JavaSerializationUtils.deserialize(bytes))
                .isInstanceOf(InvalidClassException.class);
        assertThat(UnlistedValue.readObjectInvoked).isFalse();
    }

    @Test
    void deserializeAllowsClassMatchingConfiguredPattern() throws Exception {
        filterSupport.allow("com.example.unrelated.*", JavaSerializationUtilsTest.class.getPackageName() + ".*");
        byte[] bytes = JavaSerializationUtils.serialize(new UnlistedValue());
        assertThat(JavaSerializationUtils.deserialize(bytes)).isInstanceOf(UnlistedValue.class);
        assertThat(UnlistedValue.readObjectInvoked).isTrue();
    }

    @Test
    void deserializeAcceptsAnyClassWhenFilterIsDisabled() throws Exception {
        filterSupport.disableFilter();
        byte[] bytes = JavaSerializationUtils.serialize(new UnlistedValue());
        assertThat(JavaSerializationUtils.deserialize(bytes)).isInstanceOf(UnlistedValue.class);
        assertThat(UnlistedValue.readObjectInvoked).isTrue();
    }
}
