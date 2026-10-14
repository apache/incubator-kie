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
package org.kie.kogito.process.workitem;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.kie.kogito.process.WorkItemHandlerConfig;

/**
 * Simple registry-backed implementation of {@link WorkItemHandlerConfig}.
 * Extend this class and call {@link #register(String, KogitoWorkItemHandler)} in the
 * constructor (Spring Boot) or initialiser block (Quarkus CDI) to wire handlers.
 *
 * <pre>
 * {
 *     &#64;code
 *     // Quarkus
 *     &#64;ApplicationScoped
 *     public class MyHandlerConfig extends CachedWorkItemHandlerConfig {
 *         {
 *             register("MyTask", new MyWorkItemHandler());
 *         }
 *     }
 *
 *     // Spring Boot
 *     &#64;Component
 *     public class MyHandlerConfig extends CachedWorkItemHandlerConfig {
 *         public MyHandlerConfig() {
 *             register("MyTask", new MyWorkItemHandler());
 *         }
 *     }
 * }
 * </pre>
 */
public class CachedWorkItemHandlerConfig implements WorkItemHandlerConfig {

    private final Map<String, KogitoWorkItemHandler> workItemHandlers = new HashMap<>();

    public CachedWorkItemHandlerConfig register(String name, KogitoWorkItemHandler handler) {
        workItemHandlers.put(name, handler);
        return this;
    }

    @Override
    public KogitoWorkItemHandler forName(String name) {
        KogitoWorkItemHandler handler = workItemHandlers.get(name);
        if (handler == null) {
            throw new NoSuchElementException(name);
        }
        return handler;
    }

    @Override
    public Collection<String> names() {
        return workItemHandlers.keySet();
    }
}
