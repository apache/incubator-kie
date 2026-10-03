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
package org.jbpm.workflow.core.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;

import org.jbpm.process.instance.impl.AssignmentAction;
import org.jbpm.process.instance.impl.AssignmentProducer;
import org.jbpm.util.PatternConstants;
import org.jbpm.workflow.instance.impl.MVELProcessHelper;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.impl.ImmutableDefaultFactory;
import org.mvel2.integration.impl.SimpleValueResolver;

public class InputExpressionAssignment implements AssignmentAction {

    private DataDefinition from;
    private DataDefinition to;

    public InputExpressionAssignment(DataDefinition from, DataDefinition to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public void execute(Function<String, Object> sourceResolver, Function<String, Object> targetResolver, AssignmentProducer producer) throws Exception {
        // producer in this case is void
        ImmutableDefaultFactory immutableDefaultFactory = new ImmutableDefaultFactory() {

            @Override
            public boolean isResolveable(String name) {
                return sourceResolver.apply(name) != null;
            }

            @Override
            public VariableResolver getVariableResolver(String name) {
                return new SimpleValueResolver(sourceResolver.apply(name));
            }

        };
        producer.accept(to.getLabel(), evalInput(immutableDefaultFactory, from.getExpression()));
    }

    private Object evalInput(ImmutableDefaultFactory factory, String expression) {
        Matcher matcher = PatternConstants.PARAMETER_MATCHER.matcher(expression);
        // keyed by the full #{token} string so we can do a direct lookup below
        Map<String, Object> values = new HashMap<>();
        while (matcher.find()) {
            String paramName = matcher.group(1);
            try {
                Object value = MVELProcessHelper.evaluator().eval(paramName, factory);
                if (value != null) {
                    values.put("#{" + paramName + "}", value);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot resolve expression variable '" + paramName + "' in '" + expression + "'", e);
            }
        }

        // Sole-token passthrough: return the typed value directly to avoid toString() coercion.
        // Template expressions (e.g. {"message-value": "#{token}"}) fall through to string replacement.
        Object result = values.get(expression);
        if (result != null) {
            return result;
        }

        String outcome = expression;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            outcome = outcome.replace(entry.getKey(), entry.getValue().toString());
        }
        return outcome;
    }
}