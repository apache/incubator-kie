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
package org.kogito.workitem.rest.auth;

import java.util.Map;

import org.kie.kogito.process.workitem.KogitoWorkItem;

import io.vertx.mutiny.ext.web.client.HttpRequest;

import static org.kie.kogito.internal.utils.ConversionUtils.isEmpty;
import static org.kogito.workitem.rest.RestWorkItemHandlerUtils.getParam;

public class ApiKeyAuthDecorator implements AuthDecorator {

    public static final String KEY = "apiKey";
    public static final String KEY_PREFIX = "apiKeyPrefix";

    public enum Location {
        HEADER,
        QUERY,
        COOKIE
    }

    private final String paramName;
    private final Location location;

    public ApiKeyAuthDecorator() {
        this("X-API-KEY", Location.HEADER);
    }

    public ApiKeyAuthDecorator(String paramName, Location location) {
        this.paramName = paramName;
        this.location = location;
    }

    @Override
    public void decorate(KogitoWorkItem item, Map<String, Object> parameters, HttpRequest<?> request) {
        String apiKey = getApiKey(getParam(parameters, KEY_PREFIX, String.class, null), getParam(parameters, KEY, String.class, ""));
        if (!isEmpty(apiKey)) {
            switch (location) {
                case QUERY:
                    request.addQueryParam(paramName, apiKey);
                    break;
                default:
                case HEADER:
                    request.putHeader(paramName, apiKey);
            }
        }
    }

    private static String getApiKey(String apiKeyPrefix, String apiKey) {
        return apiKeyPrefix != null ? apiKeyPrefix + " " + apiKey : apiKey;
    }
}
