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
package org.jbpm.process.core.datatype;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.jbpm.process.core.datatype.impl.type.BooleanDataType;
import org.jbpm.process.core.datatype.impl.type.EnumDataType;
import org.jbpm.process.core.datatype.impl.type.FloatDataType;
import org.jbpm.process.core.datatype.impl.type.IntegerDataType;
import org.jbpm.process.core.datatype.impl.type.ListDataType;
import org.jbpm.process.core.datatype.impl.type.ObjectDataType;
import org.jbpm.process.core.datatype.impl.type.StringDataType;

public class DataTypeResolver {

    public static final DataType defaultDataType = new ObjectDataType("java.lang.Object");

    private static final Map<Class<?>, DataType> dataTypes = new HashMap<>();

    private static final Map<String, Class<?>> string2Class = new HashMap<>();

    static {
        dataTypes.put(Boolean.class, new BooleanDataType());
        dataTypes.put(String.class, new StringDataType());
        dataTypes.put(Integer.class, new IntegerDataType());
        dataTypes.put(Float.class, new FloatDataType());
        dataTypes.put(Collection.class, new ListDataType());
        string2Class.put("java.lang.String", String.class);
        string2Class.put("java.lang.Boolean", Boolean.class);
        string2Class.put("java.lang.Integer", Integer.class);
        string2Class.put("java.lang.Float", Float.class);
    }

    private DataTypeResolver() {
    }

    public static DataType fromType(String type, ClassLoader cl) {
        return type == null || type.trim().isEmpty() ? defaultDataType : from(type, cl);
    }

    public static DataType fromClass(Class<?> clazz) {
        return from(clazz).orElse(newType(clazz));
    }

    private static DataType newType(Class<?> clazz) {
        return Enum.class.isAssignableFrom(clazz) ? new EnumDataType(clazz.asSubclass(Enum.class)) : new ObjectDataType(clazz);
    }

    public static DataType fromObject(Object value) {
        return fromObject(value, false);
    }

    public static DataType fromObject(Object value, boolean isExpr) {
        return value == null || isExpr ? defaultDataType : fromClass(value.getClass());
    }

    private static DataType from(String type, ClassLoader cl) {
        type = DataTypeUtils.ensureLangPrefix(type);
        Class<?> clazz = string2Class.get(type);
        if (clazz == null) {
            try {
                clazz = cl.loadClass(type);
            } catch (ClassNotFoundException ex) {
                // continue
            }
        }
        return clazz != null ? fromClass(clazz) : new ObjectDataType(type);
    }

    private static Optional<DataType> from(Class<?> type) {
        for (Entry<Class<?>, DataType> entry : dataTypes.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }
}
