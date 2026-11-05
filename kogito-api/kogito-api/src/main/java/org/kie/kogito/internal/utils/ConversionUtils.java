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
package org.kie.kogito.internal.utils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConversionUtils {

    private static final Logger logger = LoggerFactory.getLogger(ConversionUtils.class);

    private ConversionUtils() {
    }

    public static <T> T convert(Object value, Class<T> clazz) {
        return convert(value, clazz, Object::toString);
    }

    /**
     * Converts a string into a list of objects using `,` as a separator
     *
     * @param <T>
     * @param value object to be converted into list
     * @param clazz the item target class
     * @return a collection
     */
    public static <T> Collection<T> convertToCollection(Object value, Class<T> clazz) {
        return convertToCollection(value, clazz, ",");
    }

    /**
     * Converts a string into a list of objects
     *
     * @param <T>
     * @param value object to be converted into list
     * @param clazz the item target class
     * @param separator the separator of values
     * @return a collection
     */
    public static <T> Collection<T> convertToCollection(Object value, Class<T> clazz, String separator) {
        if (value == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.toString().split(separator)).map(v -> ConversionUtils.convert(v, clazz)).collect(Collectors.toList());
    }

    /**
     * Converts an object to an instance of the provided class
     *
     * @param <T>
     * @param value
     * @param clazz
     * @param stringConverter
     * @return
     */
    public static <T> T convert(Object value, Class<T> clazz, Function<Object, String> stringConverter) {
        if (value == null || clazz.isAssignableFrom(value.getClass())) {
            return clazz.cast(value);
        } else if (isConvertibleFromStringJavaBaseType(clazz)) {
            return convertFromStringJavaBaseType(clazz, stringConverter.apply(value));
        } else {
            Method convert = getConvertMethod(clazz);
            if (convert != null) {
                try {
                    return clazz.cast(convert.invoke(null, stringConverter.apply(value)));
                } catch (ReflectiveOperationException e) {
                    logger.info("Execution of method {} failed. Trying different approach", convert.getName(), e);
                }
            }

            try {
                return clazz.getConstructor(String.class).newInstance(stringConverter.apply(value));
            } catch (ReflectiveOperationException e) {
                logger.info("Cannot use string constructor to perform conversion", e);
            }
        }
        throw new IllegalArgumentException(value + " cannot be converted to " + clazz.getName());
    }

    private static Method getConvertMethod(Class<?> clazz) {
        try {
            return clazz.getMethod("valueOf", String.class);
        } catch (NoSuchMethodException ex) {
            for (Method method : clazz.getMethods()) {
                int modifiers = method.getModifiers();
                if (Modifier.isStatic(modifiers) && method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(String.class) && clazz.isAssignableFrom(method.getReturnType())) {
                    return method;
                }
            }
        }
        return null;
    }

    /**
     * @return true if the given type corresponds to a java base type that has the method valueOf(String value).
     */
    private static boolean isConvertibleFromStringJavaBaseType(Class<?> clazz) {
        return clazz == Short.class || clazz == Integer.class || clazz == Long.class || clazz == Byte.class ||
                clazz == Float.class || clazz == Double.class ||
                clazz == Boolean.class;
    }

    /**
     * @return the value created by applying the static method valueOf(String) on the given class. We use this method on
     *         these base java types to ensure conversion continues working on code generated by the quarkus native build,
     *         since it was detected that valueOf is not discovered by reflection on these classes after the native build.
     */
    private static <T> T convertFromStringJavaBaseType(Class<T> clazz, String value) throws NumberFormatException {
        if (clazz == Short.class) {
            return (T) Short.valueOf(value);
        }
        if (clazz == Integer.class) {
            return (T) Integer.valueOf(value);
        }
        if (clazz == Long.class) {
            return (T) Long.valueOf(value);
        }
        if (clazz == Byte.class) {
            return (T) Byte.valueOf(value);
        }
        if (clazz == Float.class) {
            return (T) Float.valueOf(value);
        }
        if (clazz == Double.class) {
            return (T) Double.valueOf(value);
        }
        if (clazz == Boolean.class) {
            return (T) Boolean.valueOf(value);
        }
        throw new IllegalArgumentException("This method can not be applied to this class: " + clazz);
    }

    /**
     * Convert to camel case
     *
     * @param text
     * @return
     */
    public static String toCamelCase(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        boolean convertNextCharToUpper = false;

        for (int i = 0; i < text.length(); i++) {
            char currentChar = text.charAt(i);
            if (!Character.isLetterOrDigit(currentChar)) {
                convertNextCharToUpper = true;
            } else if (convertNextCharToUpper) {
                builder.append(Character.toUpperCase(currentChar));
                convertNextCharToUpper = false;
            } else {
                builder.append(currentChar);
                convertNextCharToUpper = false;
            }
        }
        if (builder.length() > 0) {
            builder.setCharAt(0, Character.toLowerCase(builder.charAt(0)));
        }
        return builder.toString();
    }

    /**
     * Concatenate two paths using / as separator
     *
     * @param onePath
     * @param anotherPath
     * @return
     */
    public static String concatPaths(String onePath, String anotherPath) {
        return concatPaths(onePath, anotherPath, "/");
    }

    /**
     * Concatenate two paths using a separator
     *
     * @param onePath
     * @param anotherPath
     * @param concatChars separator to use
     * @return
     */
    public static String concatPaths(String onePath, String anotherPath, String concatChars) {
        if (anotherPath.startsWith(concatChars)) {
            if (onePath.endsWith(concatChars)) {
                return onePath.concat(anotherPath.substring(concatChars.length()));
            } else {
                return onePath.concat(anotherPath);
            }
        } else {
            if (onePath.endsWith(concatChars)) {
                return onePath.concat(anotherPath);
            } else {
                return onePath.concat(concatChars).concat(anotherPath);
            }
        }
    }

    /**
     * Check empty string
     *
     * @param value
     * @return
     */
    public static boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Check not empty string
     *
     * @param value
     * @return
     */
    public static boolean isNotEmpty(String value) {
        return !isEmpty(value);
    }

    public static String sanitizeString(String string) {
        return string.replaceAll("\"", "\\\\\"");
    }

    public static String sanitizeName(String name) {
        if (isEmpty(name)) {
            return name;
        }
        return name.replaceAll("\\s", "_");
    }

    public static String sanitizeClassName(String className) {
        return sanitizeJavaName(className, true);
    }

    public static String sanitizeJavaName(String name) {
        return sanitizeJavaName(name, false);
    }

    public static String sanitizeJavaName(String name, boolean capitalize) {
        if (isEmpty(name)) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(capitalize ? Character.toUpperCase(name.charAt(0)) : name.charAt(0));
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            sb.append(Character.isJavaIdentifierPart(c) ? c : "_");
        }
        return sb.toString();
    }

    /**
     * Receives a String possibly with FQDN org.acme.ProcessTest1 and returns a simple name like ProcessTest1
     * 
     * @param processId a possible FQDN
     * @return simple name
     */
    public static String sanitizeToSimpleName(String processId) {
        if (Objects.isNull(processId)) {
            return null;
        }
        return processId.substring(processId.lastIndexOf('.') + 1);
    }
}
