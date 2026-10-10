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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.kie.kogito.internal.process.workitem.WorkItemHandlerRuntimeException;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.DefaultKogitoWorkItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceTaskHandler extends DefaultKogitoWorkItemHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServiceTaskHandler.class);

    private String resultVarName;

    public ServiceTaskHandler() {
        this("Result");
    }

    public ServiceTaskHandler(String resultVarName) {
        this.resultVarName = resultVarName;
    }

    @Override
    public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {

        String service = (String) workItem.getParameter("Interface");
        String interfaceImplementationRef = (String) workItem.getParameter("interfaceImplementationRef");
        String operation = (String) workItem.getParameter("Operation");
        String parameterType = (String) workItem.getParameter("ParameterType");
        Object parameter = workItem.getParameter("Parameter");

        String[] services = { service, interfaceImplementationRef };
        Class<?> c = null;

        for (String serv : services) {
            try {
                c = Class.forName(serv);
                break;
            } catch (ClassNotFoundException cnfe) {
                if (serv.compareTo(services[services.length - 1]) == 0) {
                    handleException(cnfe, service, interfaceImplementationRef, operation, parameterType, parameter);
                }
            }
        }

        try {
            Object instance = c.getDeclaredConstructor().newInstance();
            Class<?>[] classes = null;
            Object[] params = null;
            if (parameterType != null) {
                classes = new Class<?>[] {
                        Class.forName(parameterType)
                };
                params = new Object[] {
                        parameter
                };
            }
            Method method = c.getMethod(operation, classes);
            Object result = method.invoke(instance, params);
            Map<String, Object> results = new HashMap<>();
            results.put(resultVarName, result);
            return Optional.of(this.workItemLifeCycle.newTransition("complete", workItem.getPhaseStatus(), results));
        } catch (Exception cnfe) {
            handleException(cnfe, service, interfaceImplementationRef, operation, parameterType, parameter);
            return Optional.empty();
        }

    }

    private void handleException(Throwable cause, String service, String interfaceImplementationRef, String operation, String paramType, Object param) {
        logger.debug("Handling exception {} inside service {} or {} and operation {} with param type {} and value {}",
                cause.getMessage(), service, interfaceImplementationRef, operation, paramType, param);
        Throwable realCause = cause instanceof InvocationTargetException ? cause.getCause() : cause;
        // Map.of does not accept null values, therefore we need to use the legacy way to build the map 
        Map<String, Object> info = new HashMap<>();
        info.put("Interface", service);
        info.put("InterfaceImplementationRef", interfaceImplementationRef);
        info.put("Operation", operation);
        info.put("ParameterType", paramType);
        info.put("Parameter", param);
        info.put(WorkItemHandlerRuntimeException.WORKITEMHANDLERTYPE, this.getClass().getSimpleName());
        WorkItemHandlerRuntimeException wihRe = new WorkItemHandlerRuntimeException(realCause, info);
        wihRe.setStackTrace(realCause.getStackTrace());
        throw wihRe;
    }

}
