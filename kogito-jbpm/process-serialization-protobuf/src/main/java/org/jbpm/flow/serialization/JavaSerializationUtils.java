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
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.Set;

import org.drools.core.util.DeserializationFilterHelper;
import org.drools.core.util.KeyStoreConstants;
import org.kie.api.runtime.process.ProcessWorkItemHandlerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JavaSerializationUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaSerializationUtils.class);

    /**
     * Process variable types outside the packages {@link DeserializationFilterHelper} allows by default:
     * the JDBC date and time types, and the exception that {@code WorkItemNodeInstance} stores in the
     * {@code Error} variable of a work item error handling process.
     */
    private static final Set<String> ALLOWED_CLASSES = Set.of(
            java.sql.Date.class.getName(),
            java.sql.Time.class.getName(),
            java.sql.Timestamp.class.getName(),
            ProcessWorkItemHandlerException.class.getName(),
            ProcessWorkItemHandlerException.HandlingStrategy.class.getName());

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
     * <p>
     * The classes that can be read are limited by an {@link ObjectInputFilter} that shares its
     * allowlist with Drools session marshalling ({@link DeserializationFilterHelper}): the
     * {@code java.lang}, {@code java.util}, {@code java.math} and {@code java.time} packages, the
     * {@code java.sql} date and time types, {@link ProcessWorkItemHandlerException}, and the patterns
     * listed in the {@value KeyStoreConstants#PROP_ALLOWED_DESER_CLASS_PATTERNS} system property. Application
     * classes stored in process variables through Java serialization must be added to that
     * property, for example {@code -Ddrools.serialization.allowedDeserializationClassPatterns=com.example.model.*}.
     * The filter can be turned off with {@code -D}{@value KeyStoreConstants#PROP_ENABLE_DESER_FILTER}{@code =false}.
     * A JVM-wide filter set with {@code jdk.serialFilter} keeps applying as well.
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
            if (DeserializationFilterHelper.isDeserializationFilterEnabled()) {
                ois.setObjectInputFilter(createDeserializationFilter());
            }
            return ois.readObject();
        }
    }

    private static ObjectInputFilter createDeserializationFilter() {
        ObjectInputFilter filter = JavaSerializationUtils::checkInput;
        ObjectInputFilter jvmWideFilter = ObjectInputFilter.Config.getSerialFilter();
        return jvmWideFilter == null ? filter : ObjectInputFilter.merge(filter, jvmWideFilter);
    }

    private static ObjectInputFilter.Status checkInput(ObjectInputFilter.FilterInfo filterInfo) {
        Class<?> clazz = filterInfo.serialClass();
        if (clazz == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        while (clazz.isArray()) {
            clazz = clazz.getComponentType();
        }
        if (clazz.isPrimitive()) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        String className = clazz.getName();
        if (ALLOWED_CLASSES.contains(className) || DeserializationFilterHelper.isClassAllowed(className)) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        LOGGER.warn("Deserialization of class '{}' was rejected by the ObjectInputFilter. "
                + "If this is a legitimate process variable type, add it to the allowlist via -D{}=<pattern>",
                className, KeyStoreConstants.PROP_ALLOWED_DESER_CLASS_PATTERNS);
        return ObjectInputFilter.Status.REJECTED;
    }
}
