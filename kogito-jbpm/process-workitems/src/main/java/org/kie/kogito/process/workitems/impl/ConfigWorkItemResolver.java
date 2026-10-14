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
package org.kie.kogito.process.workitems.impl;

import org.kie.kogito.process.workitem.KogitoWorkItem;

public class ConfigWorkItemResolver<T> implements WorkItemParamResolver<T> {

    private final String key;
    private final Class<T> clazz;
    private final T defaultValue;

    @SuppressWarnings("unchecked")
    public ConfigWorkItemResolver(String key) {
        this(key, (Class<T>) String.class, null);
    }

    public ConfigWorkItemResolver(String key, Class<T> clazz, T defaultValue) {
        this.key = key;
        this.clazz = clazz;
        this.defaultValue = defaultValue;
    }

    @Override
    public T apply(KogitoWorkItem workitem) {
        return ConfigResolverHolder.getConfigResolver().getConfigProperty(key, clazz).orElse(defaultValue);
    }

    public String getKey() {
        return key;
    }

    public Class<T> getClazz() {
        return clazz;
    }

    public T getDefaultValue() {
        return defaultValue;
    }
}
