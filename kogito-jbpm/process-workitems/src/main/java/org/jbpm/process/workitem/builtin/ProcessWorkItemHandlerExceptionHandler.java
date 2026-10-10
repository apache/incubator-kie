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

import org.kie.api.runtime.process.ProcessWorkItemHandlerException;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;

public class ProcessWorkItemHandlerExceptionHandler extends AbstractExceptionHandlingTaskHandler {

    public static final String ERROR_HANDLER_PROCESS_ID = "ErrorHandlerProcessId";
    public static final String ERROR_HANDLING_STRATEGY = "ErrorHandlingStrategy";
    public static final String ERROR_HANDLING_RETRIES = "ErrorHandlingRetries";

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

        String processId = (String) workItem.getParameter(ERROR_HANDLER_PROCESS_ID);
        String strategyStr = (String) workItem.getParameter(ERROR_HANDLING_STRATEGY);
        Object retriesObj = workItem.getParameter(ERROR_HANDLING_RETRIES);

        if (processId != null && !processId.trim().isEmpty() && strategyStr != null && !strategyStr.trim().isEmpty()) {
            ProcessWorkItemHandlerException.HandlingStrategy strategy;
            try {
                strategy = ProcessWorkItemHandlerException.HandlingStrategy.valueOf(strategyStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid " + ERROR_HANDLING_STRATEGY + " value: '" + strategyStr + "'. Expected RETRY, COMPLETE, ABORT, or RETHROW.", e);
            }

            int retries = 0;
            if (retriesObj instanceof Number) {
                retries = ((Number) retriesObj).intValue();
            } else if (retriesObj instanceof String && !((String) retriesObj).trim().isEmpty()) {
                try {
                    retries = Integer.parseInt(((String) retriesObj).trim());
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Invalid " + ERROR_HANDLING_RETRIES + " value: '" + retriesObj + "'. Expected an integer.", nfe);
                }
            }

            throw new ProcessWorkItemHandlerException(
                    processId.trim(),
                    strategy,
                    cause instanceof Exception ? (Exception) cause : new RuntimeException(cause),
                    retries);
        }

        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
        throw new RuntimeException(cause);
    }
}
