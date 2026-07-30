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
package org.kie.kogito;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Models {

    private Models() {
    }

    @SuppressWarnings("squid:S3011")
    public static Map<String, Object> toMap(MapOutput m) {
        Set<String> modifiedFields = (m instanceof Model model) ? model.getModifiedFields() : null;
        Map<String, Object> map = new LinkedHashMap<>();
        for (Field field : m.getClass().getDeclaredFields()) {
            JsonProperty jsonAnnotation = field.getAnnotation(JsonProperty.class);
            if (jsonAnnotation != null) {
                String name = jsonAnnotation.value();
                // null = not tracking (include all); non-null = include only modified fields
                if (modifiedFields != null && !modifiedFields.contains(name)) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    map.put(name, field.get(m));
                } catch (ReflectiveOperationException e) {
                    throw new ReflectiveModelAccessException(e);
                }
            }
        }
        return map;
    }

    public static <T> T fromMap(T m, String id, Map<String, Object> map) {
        setId(m, id);
        return fromMap(m, map);
    }

    @SuppressWarnings("squid:S3011")
    public static <T> T fromMap(T m, Map<String, Object> map) {
        for (Field field : m.getClass().getDeclaredFields()) {
            JsonProperty jsonAnnotation = field.getAnnotation(JsonProperty.class);
            if (jsonAnnotation != null) {
                String name = jsonAnnotation.value();
                if (map.containsKey(name)) {
                    field.setAccessible(true);
                    try {
                        field.set(m, map.get(name));
                    } catch (ReflectiveOperationException e) {
                        throw new ReflectiveModelAccessException(e);
                    }
                }
            }
        }
        return m;
    }

    public static void setId(Object m, String id) {
        try {
            m.getClass().getMethod("setId", String.class).invoke(m, id);
        } catch (NoSuchMethodException e) {
            // do nothing
        } catch (ReflectiveOperationException e) {
            throw new ReflectiveModelAccessException(e);
        }
    }
}
