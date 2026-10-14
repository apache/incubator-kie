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
package org.kie.kogito.internal.process.workitem;

import java.util.HashMap;
import java.util.Map;

import org.kie.api.definition.process.Node;
import org.kie.api.definition.process.NodeContainer;
import org.kie.api.definition.process.Process;
import org.kie.kogito.internal.process.runtime.KogitoNode;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.process.workitem.KogitoWorkItem;

public class WorkItemRecordParameters {

    private static final String INPUT_ARGS_METADATA = "inputArgs";
    private static final String OUTPUT_ARGS_METADATA = "outputArgs";
    public static final String RECORD_ARGS = "recordArgs";

    public static void recordInputParameters(KogitoWorkItem workItem, Map<String, Object> inputArgs) {
        recordInputParameters(workItem.getNodeInstance(), inputArgs);
    }

    public static void recordInputParameters(KogitoWorkItem workItem, String... argNames) {
        // Map.of does not support null values
        if (argNames.length > 0) {
            Map<String, Object> inputArgs = new HashMap<>();
            for (String argName : argNames) {
                inputArgs.put(argName, workItem.getParameter(argName));
            }
            recordInputParameters(workItem.getNodeInstance(), inputArgs);
        }
    }

    public static void recordOutputParameters(KogitoWorkItem workItem, Map<String, Object> outputArgs) {
        recordOutputParameters(workItem.getNodeInstance(), outputArgs.getOrDefault("Result", outputArgs));
    }

    public static void recordInputParameters(KogitoNodeInstance nodeInstance, Object inputArgs) {
        recordParameters(nodeInstance, inputArgs, INPUT_ARGS_METADATA);
    }

    public static void recordOutputParameters(KogitoNodeInstance nodeInstance, Object outputArgs) {
        recordParameters(nodeInstance, outputArgs, OUTPUT_ARGS_METADATA);
    }

    private static void recordParameters(KogitoNodeInstance nodeInstance, Object args, String key) {
        if (shouldRecordParameters(nodeInstance)) {
            nodeInstance.getMetaData().put(key, args);
        }
    }

    public static Object getInputParameters(KogitoNodeInstance nodeInstance) {
        return nodeInstance.getMetaData().get(INPUT_ARGS_METADATA);
    }

    public static Object getOutputParameters(KogitoNodeInstance nodeInstance) {
        return nodeInstance.getMetaData().get(OUTPUT_ARGS_METADATA);
    }

    private static boolean shouldRecordParameters(KogitoNodeInstance nodeInstance) {
        return nodeInstance != null && shouldRecordParameters(nodeInstance.getNode());
    }

    private static boolean shouldRecordParameters(Node node) {
        if (node == null) {
            return false;
        }

        // Check node-level metadata (highest priority)
        Object nodeValue = node.getMetaData().get(RECORD_ARGS);
        if (nodeValue != null) {
            return shouldRecordParameters(nodeValue);
        }

        // Check process-level metadata by traversing up the container hierarchy
        if (node instanceof KogitoNode kogitoNode) {
            Process process = findProcess(kogitoNode);
            if (process != null) {
                Object processValue = process.getMetaData().get(RECORD_ARGS);
                if (processValue != null) {
                    return shouldRecordParameters(processValue);
                }
            }
        }

        // Default to false
        return false;
    }

    private static Process findProcess(KogitoNode node) {
        NodeContainer container = node.getParentContainer();
        while (container != null) {
            if (container instanceof Process process) {
                return process;
            }
            // Traverse up if the container is also a KogitoNode (e.g., subprocess)
            if (container instanceof KogitoNode kogitoContainerNode) {
                container = kogitoContainerNode.getParentContainer();
            } else {
                break;
            }
        }
        return null;
    }

    private static boolean shouldRecordParameters(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        } else if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        } else {
            return false;
        }
    }

    private WorkItemRecordParameters() {
    }
}
