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

import java.util.function.Supplier;

import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.kie.kogito.internal.process.runtime.HeadersPersistentConfig;
import org.kie.kogito.process.Process;

public final class MarshallerContextName<T> {

    public static final MarshallerContextName<ObjectMarshallerStrategy[]> OBJECT_MARSHALLING_STRATEGIES =
            new MarshallerContextName<>("OBJECT_MARSHALLING_STRATEGIES", () -> new ObjectMarshallerStrategy[0]);
    public static final MarshallerContextName<String> MARSHALLER_FORMAT = new MarshallerContextName<>("FORMAT");
    public static final MarshallerContextName<Process<?>> MARSHALLER_PROCESS = new MarshallerContextName<>("PROCESS");
    public static final MarshallerContextName<RuleFlowProcessInstance> MARSHALLER_PROCESS_INSTANCE = new MarshallerContextName<>("PROCESS_INSTANCE");
    public static final MarshallerContextName<Boolean> MARSHALLER_INSTANCE_READ_ONLY = new MarshallerContextName<>("READ_ONLY");
    public static final MarshallerContextName<Boolean> MARSHALLER_INSTANCE_EAGER = new MarshallerContextName<>("EAGER", () -> Boolean.FALSE);
    public static final MarshallerContextName<HeadersPersistentConfig> MARSHALLER_HEADERS_CONFIG = new MarshallerContextName<HeadersPersistentConfig>("HEADER_CONFIG");
    public static final MarshallerContextName<ProcessInstanceMarshallerListener[]> MARSHALLER_INSTANCE_LISTENER =
            new MarshallerContextName<>("MARSHALLER_INSTANCE_LISTENERS", () -> new ProcessInstanceMarshallerListener[0]);
    public static final MarshallerContextName<NodeInstanceReader[]> MARSHALLER_NODE_INSTANCE_READER = new MarshallerContextName<>("MARSHALLER_NODE_INSTANCE_READER", () -> new NodeInstanceReader[0]);
    public static final MarshallerContextName<NodeInstanceWriter[]> MARSHALLER_NODE_INSTANCE_WRITER = new MarshallerContextName<>("MARSHALLER_NODE_INSTANCE_WRITER", () -> new NodeInstanceWriter[0]);

    public static final String MARSHALLER_FORMAT_JSON = "json";

    private String name;
    private Supplier<T> defaultValue;

    private MarshallerContextName(String name) {
        this(name, () -> null);
    }

    private MarshallerContextName(String name, Supplier<T> defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    public String name() {
        return this.name;
    }

    @SuppressWarnings("unchecked")
    public T cast(Object value) {
        return (T) value;
    }

    public T defaultValue() {
        return defaultValue.get();
    }
}
