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
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

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
}
