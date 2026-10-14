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

import java.util.HashMap;
import java.util.Map;

import org.kie.kogito.internal.process.workitem.WorkItemHandlerRuntimeException;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.InternalKogitoWorkItemManager;

/**
 * This class will wrap a {@link KogitoWorkItemHandler} instance so that an event (signal, error or other) can be sent to the process
 * instance if and when the wrapped {@link KogitoWorkItemHandler} instance throws an exception (during a
 * {@link KogitoWorkItemHandler#executeWorkItem(KogitoWorkItem, KogitoWorkItemManager)} or
 * {@link KogitoWorkItemHandler#abortWorkItem(KogitoWorkItem, KogitoWorkItemManager)} method.
 * 
 * </p>
 * In order to prevent an endless loop, the signal will only be sent once. If the signal should be sent the next time the same
 * wrapped {@link KogitoWorkItemHandler} instance throws an exception, the {@link SignallingTaskHandlerDecorator} instance must either be
 * reset via the {@link SignallingTaskHandlerDecorator#clear()} or {@link SignallingTaskHandlerDecorator#clearProcessInstance(Long)}
 * methods.
 * <p>
 * Otherwise, the number of exceptions handled can be changed via the {@link KogitoWorkItemHandler#setExceptionCountLimit} method.
 * 
 * </p>
 * This class is <b>not</b> thread-safe.
 */
public class SignallingTaskHandlerDecorator extends AbstractExceptionHandlingTaskHandler {

    private final String eventType;

    private String workItemExceptionParameterName = "jbpm.workitem.exception";

    private final Map<String, Integer> processInstanceExceptionMap = new HashMap<>();
    private int exceptionCountLimit = 1;

    /**
     * Constructs an instance that uses the given <code>eventType</code> parameter to signal the process instance using the given
     * {@link org.kie.kogito.internal.process.runtime.KogitoProcessRuntime} <code>kruntime</code> parameter when an instance of the class specified by the
     * <code>originalTaskHandlerClass</code> throws an exception upon {@link KogitoWorkItemHandler#executeWorkItem(KogitoWorkItem, KogitoWorkItemManager)}
     *
     * @param originalTaskHandlerClass
     * @param eventType
     */
    public SignallingTaskHandlerDecorator(Class<? extends KogitoWorkItemHandler> originalTaskHandlerClass, String eventType) {
        super(originalTaskHandlerClass);
        this.eventType = eventType;
    }

    public SignallingTaskHandlerDecorator(KogitoWorkItemHandler originalTaskHandler, String eventType) {
        super(originalTaskHandler);
        this.eventType = eventType;
    }

    public SignallingTaskHandlerDecorator(Class<? extends KogitoWorkItemHandler> originalTaskHandlerClass, String eventType, int exceptionCountLimit) {
        super(originalTaskHandlerClass);
        this.eventType = eventType;
        this.exceptionCountLimit = exceptionCountLimit;
    }

    public SignallingTaskHandlerDecorator(KogitoWorkItemHandler originalTaskHandler, String eventType, int exceptionCountLimit) {
        super(originalTaskHandler);
        this.eventType = eventType;
        this.exceptionCountLimit = exceptionCountLimit;
    }

    public void setWorkItemExceptionParameterName(String parameterName) {
        this.workItemExceptionParameterName = parameterName;
    }

    public String getWorkItemExceptionParameterName() {
        return this.workItemExceptionParameterName;
    }

    @Override
    public void handleException(KogitoWorkItemManager manager, KogitoWorkItemHandler originalTaskHandler, KogitoWorkItem workItem, WorkItemTransition transition, Throwable cause) {
        switch (transition.id()) {
            case "activate": {
                if (getAndIncreaseExceptionCount(workItem.getProcessInstanceStringId()) < exceptionCountLimit) {
                    workItem.getParameters().put(this.workItemExceptionParameterName, cause);
                    ((InternalKogitoWorkItemManager) manager).signalEvent(this.eventType, workItem, workItem.getProcessInstanceStringId());
                } else {
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    } else {
                        throw new WorkItemHandlerRuntimeException(cause,
                                "Signalling process instance " + workItem.getProcessInstanceId() + " with '" + this.eventType + "' resulted this exception.");
                    }
                }
                break;
            }
            case "abort": {
                if (getAndIncreaseExceptionCount(workItem.getProcessInstanceStringId()) < exceptionCountLimit) {
                    workItem.getParameters().put(this.workItemExceptionParameterName, cause);
                    ((InternalKogitoWorkItemManager) manager).signalEvent(this.eventType, workItem, workItem.getProcessInstanceId());
                }
                break;
            }
        }

    }

    private int getAndIncreaseExceptionCount(String processInstanceId) {
        Integer count = processInstanceExceptionMap.get(processInstanceId);
        if (count == null) {
            count = 0;
        }
        processInstanceExceptionMap.put(processInstanceId, ++count);
        return (count - 1);
    }

    public void setExceptionCountLimit(int limit) {
        this.exceptionCountLimit = limit;
    }

    public void clear() {
        processInstanceExceptionMap.clear();
    }
}
