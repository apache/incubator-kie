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
package org.jbpm.process.core.context.variable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jbpm.process.core.Context;
import org.jbpm.process.core.context.AbstractContext;
import org.kie.kogito.VariableLegacyMode;
import org.kie.kogito.internal.utils.KogitoTags;

public class VariableScope extends AbstractContext {

    public static final String VARIABLE_STRICT_ENABLED_PROPERTY = "org.jbpm.variable.strict";
    private static boolean variableStrictEnabled = Boolean.parseBoolean(System.getProperty(VARIABLE_STRICT_ENABLED_PROPERTY, Boolean.FALSE.toString()));

    public static final String KOGITO_VARIABLE_LEGACY_PROPERTY = "kogito.workflow.variable-legacy-mode";

    public static final String VARIABLE_SCOPE = "VariableScope";

    private static final long serialVersionUID = 510l;

    private List<Variable> variables;

    public VariableScope() {
        this.variables = new ArrayList<>();
    }

    public String getType() {
        return VariableScope.VARIABLE_SCOPE;
    }

    public List<Variable> getVariables() {
        return this.variables;
    }

    public void setVariables(final List<Variable> variables) {
        if (variables == null) {
            throw new IllegalArgumentException("Variables is null");
        }
        this.variables = variables;
    }

    public String[] getVariableNames() {
        return variables.stream()
                .map(Variable::getName)
                .toArray(String[]::new);
    }

    public Variable findVariable(String variableName) {
        for (Variable variable : getVariables()) {
            if (variable.getName().equals(variableName)) {
                return variable;
            }
        }
        return null;
    }

    public Context resolveContext(Object param) {
        if (param instanceof String) {
            return findVariable((String) param) == null ? null : this;
        }
        throw new IllegalArgumentException(
                "VariableScopes can only resolve variable names: " + param);
    }

    public void validateVariable(String processName, String name, Object value) {
        if (!variableStrictEnabled) {
            return;
        }
        Variable var = findVariable(name);
        if (var == null) {
            if (VariableLegacyMode.isEnabled()) {
                return;
            }
            throw new IllegalArgumentException("Variable '" + name + "' is not defined in process " + processName);
        }
        if (var.getType() != null && value != null) {
            boolean isValidType = var.getType().verifyDataType(value);
            if (!isValidType) {
                throw new IllegalArgumentException("Variable '" + name + "' has incorrect data type expected:"
                        + var.getType().getStringType() + " actual:" + value.getClass().getName());
            }
        }
    }

    /*
     * mainly for test coverage to easily switch between settings
     */
    public static void setVariableStrictOption(boolean turnedOn) {
        variableStrictEnabled = turnedOn;
    }

    public static boolean isVariableStrictEnabled() {
        return variableStrictEnabled;
    }

    public boolean isReadOnly(String name) {
        Variable v = findVariable(name);

        if (v != null) {
            return v.hasTag(KogitoTags.READONLY_TAG);
        }
        return false;
    }

    public boolean isRequired(String name) {
        Variable v = findVariable(name);

        if (v != null) {
            return v.hasTag(KogitoTags.REQUIRED_TAG);
        }
        return false;
    }

    public List<String> tags(String name) {
        Variable v = findVariable(name);

        if (v != null) {
            return v.getTags();
        }
        return Collections.emptyList();
    }

    public void addVariable(Variable variable) {
        if (this.variables.stream().anyMatch(v -> v.getName().equals(variable.getName()))) {
            return;
        }
        this.variables.add(variable);
    }
}
