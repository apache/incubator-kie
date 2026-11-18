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
package org.jbpm.process.workitem.builtin;

import java.util.Arrays;
import java.util.Set;

import org.kie.api.runtime.process.ProcessWorkItemHandlerException;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;

public class ProcessWorkItemHandlerExceptionHandler extends AbstractExceptionHandlingTaskHandler {

    public static final String ERROR_HANDLER_PROCESS_ID = "ErrorHandlerProcessId";
    public static final String ERROR_HANDLING_STRATEGY = "ErrorHandlingStrategy";
    public static final String ERROR_HANDLING_RETRIES = "ErrorHandlingRetries";

    /**
     * Internal parameters that configure exception handling. They are not part of a task's data contract.
     */
    public static final Set<String> ERROR_HANDLING_PARAMETERS = Set.of(
            ERROR_HANDLER_PROCESS_ID,
            ERROR_HANDLING_STRATEGY,
            ERROR_HANDLING_RETRIES);

    public static boolean canDecorate(KogitoWorkItemHandler handler, KogitoWorkItem workItem) {
        if (handler == null || handler instanceof AbstractExceptionHandlingTaskHandler || workItem == null) {
            return false;
        }
        Object processId = workItem.getParameter(ERROR_HANDLER_PROCESS_ID);
        Object strategy = workItem.getParameter(ERROR_HANDLING_STRATEGY);
        return processId != null && !processId.toString().trim().isEmpty()
                && strategy != null && !strategy.toString().trim().isEmpty();
    }

    public static KogitoWorkItemHandler wrapIfNeeded(KogitoWorkItemHandler handler, KogitoWorkItem workItem) {
        if (canDecorate(handler, workItem)) {
            return new ProcessWorkItemHandlerExceptionHandler(handler);
        }
        return handler;
    }

    public ProcessWorkItemHandlerExceptionHandler(KogitoWorkItemHandler originalTaskHandler) {
        super(originalTaskHandler);
    }

    public ProcessWorkItemHandlerExceptionHandler(Class<? extends KogitoWorkItemHandler> originalTaskHandlerClass) {
        super(originalTaskHandlerClass);
    }

    @Override
    public void handleException(KogitoWorkItemManager manager, KogitoWorkItemHandler originalTaskHandler, KogitoWorkItem workItem, WorkItemTransition transition, Throwable cause) {
        if (cause instanceof ProcessWorkItemHandlerException) {
            throw (ProcessWorkItemHandlerException) cause;
        }

        Object processIdObj = workItem.getParameter(ERROR_HANDLER_PROCESS_ID);
        Object strategyObj = workItem.getParameter(ERROR_HANDLING_STRATEGY);
        Object retriesObj = workItem.getParameter(ERROR_HANDLING_RETRIES);

        String processId = processIdObj != null ? processIdObj.toString().trim() : null;
        String strategyStr = strategyObj != null ? strategyObj.toString().trim() : null;

        if (processId != null && !processId.isEmpty() && strategyStr != null && !strategyStr.isEmpty()) {
            ProcessWorkItemHandlerException.HandlingStrategy strategy;
            if (strategyObj instanceof ProcessWorkItemHandlerException.HandlingStrategy) {
                strategy = (ProcessWorkItemHandlerException.HandlingStrategy) strategyObj;
            } else {
                try {
                    strategy = ProcessWorkItemHandlerException.HandlingStrategy.valueOf(strategyStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid " + ERROR_HANDLING_STRATEGY + " value: '" + strategyObj + "'. Expected one of: "
                            + Arrays.toString(ProcessWorkItemHandlerException.HandlingStrategy.values()) + ".", e);
                }
            }

            int retries = 0;
            if (retriesObj instanceof Number) {
                retries = ((Number) retriesObj).intValue();
            } else if (retriesObj instanceof String) {
                String retriesStr = ((String) retriesObj).trim();
                if (!retriesStr.isEmpty()) {
                    try {
                        retries = Integer.parseInt(retriesStr);
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException("Invalid " + ERROR_HANDLING_RETRIES + " value: '" + retriesObj + "'. Expected an integer.", nfe);
                    }
                }
            } else if (retriesObj != null) {
                throw new IllegalArgumentException("Invalid " + ERROR_HANDLING_RETRIES + " value: '" + retriesObj + "'. Expected an integer.");
            }

            throw new ProcessWorkItemHandlerException(
                    processId,
                    strategy,
                    cause instanceof Exception ? (Exception) cause : new RuntimeException(cause),
                    retries);
        }

        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        throw new RuntimeException(cause);
    }
}
