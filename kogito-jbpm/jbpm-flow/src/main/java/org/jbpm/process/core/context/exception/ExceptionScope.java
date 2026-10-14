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
package org.jbpm.process.core.context.exception;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.jbpm.process.core.Context;
import org.jbpm.process.core.context.AbstractContext;
import org.kie.kogito.process.workitem.WorkItemExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionScope extends AbstractContext {

    private static final long serialVersionUID = 510l;

    private static final Logger logger = LoggerFactory.getLogger(ExceptionScope.class);

    public static final String EXCEPTION_SCOPE = "ExceptionScope";

    protected Map<String, ExceptionHandler> exceptionHandlers = new HashMap<>();
    private transient Collection<ExceptionHandlerPolicy> policies = ExceptionHandlerPolicyFactory.getHandlerPolicies();

    @Override
    public String getType() {
        return EXCEPTION_SCOPE;
    }

    public void setExceptionHandler(String exception, ExceptionHandler exceptionHandler) {
        this.exceptionHandlers.put(exception, exceptionHandler);
    }

    public ExceptionHandler getExceptionHandler(String exception) {
        ExceptionHandler result = exceptionHandlers.get(exception);
        if (result == null) {
            result = exceptionHandlers.get(null);
        }
        return result;
    }

    protected ExceptionHandler getHandlerFromPolicies(Throwable exception) {
        for (ExceptionHandlerPolicy policy : policies) {
            for (Entry<String, ExceptionHandler> handler : exceptionHandlers.entrySet()) {
                String className = handler.getKey();
                if (className != null && policy.test(className, exception)) {
                    logger.debug("Policy {} matches handler {}", policy.getClass().getSimpleName(), handler.getKey());
                    return handler.getValue();
                }
            }
        }
        return null;
    }

    public ExceptionHandler getExceptionHandler(Throwable exception) {
        ExceptionHandler handler = getHandlerFromPolicies(exception);
        if (handler == null && exception instanceof WorkItemExecutionException) {
            handler = exceptionHandlers.get(((WorkItemExecutionException) exception).getErrorCode());
        }
        if (handler == null) {
            handler = exceptionHandlers.get(null);
        }
        return handler;
    }

    public void removeExceptionHandler(String exception) {
        this.exceptionHandlers.remove(exception);
    }

    public Map<String, ExceptionHandler> getExceptionHandlers() {
        return exceptionHandlers;
    }

    public void setExceptionHandlers(Map<String, ExceptionHandler> exceptionHandlers) {
        if (exceptionHandlers == null) {
            throw new IllegalArgumentException("Exception handlers are null");
        }
        this.exceptionHandlers = exceptionHandlers;
    }

    @Override
    public Context resolveContext(Object param) {
        if (param instanceof String) {
            return getExceptionHandler((String) param) == null ? null : this;
        } else if (param instanceof Throwable) {
            return getExceptionHandler((Throwable) param) == null ? null : this;
        }
        throw new IllegalArgumentException(
                "ExceptionScopes can only resolve exception names: " + param);
    }

}
