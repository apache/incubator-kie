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
package org.kogito.workitem.rest;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.kie.api.annotations.KieProperty;
import org.kie.kogito.process.workitem.WorkItemExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;

import static org.kie.kogito.internal.utils.ConversionUtils.convert;

public class RestWorkItemHandlerUtils {

    /**
     * Maximum number of HTTP connections the REST work item handler keeps open per target host.
     * When not set, the Vert.x default applies.
     */
    @KieProperty(type = "integer")
    public static final String MAX_POOL_SIZE_PROPERTY = "kogito.workitem.rest.max-pool-size";

    private final static Logger logger = LoggerFactory.getLogger(RestWorkItemHandlerUtils.class);

    private RestWorkItemHandlerUtils() {
    }

    public static WebClientOptions sslWebClientOptions() {
        return new WebClientOptions().setSsl(true).setVerifyHost(false).setTrustAll(true);
    }

    /**
     * Options for the plain HTTP client, copying every setting from the given SSL options so that
     * one setting (e.g. the pool size) sizes both the HTTP and the HTTPS client, regardless of what
     * else {@link #sslWebClientOptions()} configures.
     */
    public static WebClientOptions httpWebClientOptions(WebClientOptions sslOptions) {
        return new WebClientOptions(sslOptions).setSsl(false);
    }

    public static String getParam(Map<String, Object> parameters, String paramName) {
        return getParam(parameters, paramName, String.class, null);
    }

    public static <T> T getParamSupply(Map<String, Object> parameters, String paramName, Class<T> type, Supplier<T> defaultValue) {
        Object value = parameters.remove(paramName);
        return value == null ? defaultValue.get() : convert(value, type, v -> v.toString().toUpperCase());
    }

    public static <T> T getParam(Map<String, Object> parameters, String paramName, Class<T> type, T defaultValue) {
        Object value = parameters.remove(paramName);
        logger.debug("Rest parameter name {} value is {}", paramName, value);
        return value == null ? defaultValue : convert(value, type, v -> v.toString().toUpperCase());
    }

    public static <T> Collection<T> getClassListParam(Map<String, Object> parameters, String paramName, Class<T> clazz, Collection<T> defaultValue, Map<String, T> instances) {
        Object param = parameters.remove(paramName);
        if (param == null) {
            return defaultValue;
        } else {
            return param instanceof Collection ? ((Collection<?>) param).stream().filter(Objects::nonNull).map(p -> getClassParam(p, clazz, instances)).collect(Collectors.toList())
                    : Collections.singletonList(getClassParam(param, clazz, instances));
        }
    }

    public static void checkStatusCode(HttpResponse<Buffer> response) {
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new WorkItemExecutionException(Integer.toString(statusCode), "Request failed with message: " + response.statusMessage());
        }
    }

    private static <T> T getClassParam(Object param, Class<T> clazz, Map<String, T> instances) {
        //check if an instance of RestWorkItemHandlerBodyBuilder was set and just return it
        if (clazz.isAssignableFrom(param.getClass())) {
            return clazz.cast(param);
        }
        //in case of String, try to load an instance by the FQN of a RestWorkItemHandlerBodyBuilder
        else if (param instanceof String) {
            return instances.computeIfAbsent(param.toString(), k -> loadClass(k, clazz));
        }
        throw new IllegalArgumentException(param + " is not a valid instance of class " + clazz);
    }

    public static <T> T getClassParam(Map<String, Object> parameters, String paramName, Class<T> clazz, T defaultValue, Map<String, T> instances) {
        Object param = parameters.remove(paramName);
        return param == null ? defaultValue : getClassParam(param, clazz, instances);
    }

    private static <T> T loadClass(String className, Class<T> clazz) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className).asSubclass(clazz).getConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalArgumentException("Problem loading class " + className, e);
        }
    }
}
