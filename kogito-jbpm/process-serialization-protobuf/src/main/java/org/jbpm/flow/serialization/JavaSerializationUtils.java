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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;

public final class JavaSerializationUtils {

    private JavaSerializationUtils() {
    }

    /**
     * Serializes {@code value} to a byte array using Java serialization.
     * <p>
     * ObjectOutputStream must be closed before calling toByteArray(): close() flushes the final
     * block header; without it the byte array is truncated and causes EOFException on deserialization.
     */
    public static byte[] serialize(Object value) throws IOException {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            try (ObjectOutputStream out = new ObjectOutputStream(stream)) {
                out.writeObject(value);
            }
            return stream.toByteArray();
        }
    }

    /**
     * Deserializes {@code data} using Java serialization, resolving classes through the thread
     * context classloader first so that application classes are found in environments like Quarkus
     * where the bootstrap classloader does not see deployment classes.
     */
    public static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (InputStream is = new ByteArrayInputStream(data); ObjectInputStream ois = new ObjectInputStream(is) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                try {
                    return Class.forName(desc.getName(), false, Thread.currentThread().getContextClassLoader());
                } catch (ClassNotFoundException ex) {
                    return super.resolveClass(desc);
                }
            }
        }) {
            return ois.readObject();
        }
    }
}
