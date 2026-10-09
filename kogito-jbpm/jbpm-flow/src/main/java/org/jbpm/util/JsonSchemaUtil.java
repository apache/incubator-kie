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
package org.jbpm.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstanceNotFoundException;
import org.kie.kogito.process.ProcessInstanceReadMode;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.Policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonSchemaUtil {

    private JsonSchemaUtil() {
    }

    private static ObjectMapper mapper = new ObjectMapper();
    private static String jsonDir = "META-INF/jsonSchema/";

    public static String getJsonSchemaName(String id) {
        return id.replace('.', '#').replaceAll("\\s", "_");
    }

    public static String getJsonSchemaName(String processId, String taskName) {
        return getJsonSchemaName(processId + "_" + taskName);
    }

    public static Path getJsonDir() {
        return Path.of(jsonDir);
    }

    public static String getFileName(String key) {
        return key + ".json";
    }

    public static Map<String, Object> load(ClassLoader cl, String processId) {
        return loadSchema(cl, getJsonSchemaName(processId));
    }

    public static Map<String, Object> load(ClassLoader cl, String processId, String taskName) {
        return loadSchema(cl, getJsonSchemaName(processId, taskName));
    }

    private static Map<String, Object> loadSchema(ClassLoader cl, String schemaId) {
        String jsonFile = pathFor(schemaId);
        try (InputStream in = cl.getResourceAsStream(jsonFile)) {
            if (in == null) {
                throw new IllegalArgumentException("Cannot find file " + jsonFile + " in classpath");
            }
            return load(in);
        } catch (IOException io) {
            throw new IllegalStateException("Error loading schema " + jsonFile, io);
        }
    }

    public static Map<String, Object> load(InputStream in) throws IOException {
        return mapper.readValue(in, new TypeReference<Map<String, Object>>() {
        });
    }

    public static <T> Map<String, Object> addPhases(Process<T> process,
            KogitoWorkItemHandler workItemHandler,
            String processInstanceId,
            String workItemId,
            Policy[] policies,
            Map<String, Object> jsonSchema) {
        return process.instances().findById(processInstanceId, ProcessInstanceReadMode.READ_ONLY).map(pi -> {
            WorkItem workItem = pi.workItem(workItemId, policies);
            Set<String> transitions = workItemHandler.allowedTransitions(workItem.getPhaseStatus());
            jsonSchema.put("phases", transitions);
            return jsonSchema;
        }).orElseThrow(() -> new ProcessInstanceNotFoundException(processInstanceId));
    }

    public static String pathFor(String key) {
        return jsonDir + getFileName(key);
    }
}
