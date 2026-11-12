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
package org.kie.kogito.codegen.process.config;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.kie.api.annotations.KieInternal;
import org.kie.api.annotations.KieProperty;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;

/**
 * Configuration for work item and human task REST endpoint generation.
 * <p>
 * Controls endpoint generation per work item registration name (<code>Work.getName()</code>).
 * Enabled by default for <code>Human Task</code> and disabled for all other work items.
 */
public final class WorkItemRestConfig {

    /**
     * (boolean) enable/disable REST endpoint generation for a given work item registration name (<code>Work.getName()</code>); default to false (true for "Human Task")
     */
    @KieProperty(type = "boolean", defaultValue = "false", allowedValues = "true,false")
    public static final String REST_ENABLED_PROPERTY = "kogito.codegen.process.workitems.<workitem_name>.rest.enabled";

    @KieInternal
    public static final String CONFIG_PREFIX = "kogito.codegen.process.workitems.";

    @KieInternal
    public static final String CONFIG_SUFFIX = ".rest.enabled";

    @KieInternal
    static final String HUMAN_TASK_WORK_NAME = "Human Task";

    private static final Set<String> DEFAULT_ENABLED_WORK_NAMES = Set.of(HUMAN_TASK_WORK_NAME);

    private final Map<String, Boolean> enabledByWorkName;

    private WorkItemRestConfig(Map<String, Boolean> enabledByWorkName) {
        this.enabledByWorkName = enabledByWorkName;
    }

    public static WorkItemRestConfig fromContext(KogitoBuildContext context) {
        Map<String, Boolean> enabledByWorkName = new HashMap<>();
        for (String key : context.getApplicationProperties()) {
            if (key.startsWith(CONFIG_PREFIX) && key.endsWith(CONFIG_SUFFIX) && key.length() > CONFIG_PREFIX.length() + CONFIG_SUFFIX.length()) {
                String workName = unquote(key.substring(CONFIG_PREFIX.length(), key.length() - CONFIG_SUFFIX.length()));
                boolean enabled = context.getApplicationProperty(key).map(Boolean::parseBoolean).orElse(false);
                enabledByWorkName.put(workName, enabled);
            }
        }
        return new WorkItemRestConfig(enabledByWorkName);
    }

    private static String unquote(String name) {
        if (name.length() > 1 && name.startsWith("\"") && name.endsWith("\"")) {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }

    /**
     * @param workName the work definition name, as in <code>Work.getName()</code>
     * @return true if endpoints are enabled for this work definition (defaults to true for Human Task, false for others)
     */
    public boolean isRestEnabled(String workName) {
        if (workName == null) {
            return false;
        }
        return enabledByWorkName.getOrDefault(workName, DEFAULT_ENABLED_WORK_NAMES.contains(workName));
    }

    /**
     * @return the work definition names that have been configured, whatever their value
     */
    public Set<String> configuredNames() {
        return new LinkedHashSet<>(enabledByWorkName.keySet());
    }
}
