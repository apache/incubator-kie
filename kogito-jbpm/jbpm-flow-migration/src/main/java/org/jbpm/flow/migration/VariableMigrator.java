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
package org.jbpm.flow.migration;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jbpm.flow.migration.model.VariableMigrationPlan;
import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the variable migration rules of a migration plan to one process instance.
 * <p>
 * Each change fires the before / after variable changed events (when a runtime is given) so that listeners
 * such as data-index and data-audit see the migrated values.
 * <p>
 * The variable scope has no way to remove an entry, so {@code rename} and {@code remove} set the old variable to
 * {@code null}: the name stays in the variable map with a {@code null} value.
 */
final class VariableMigrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(VariableMigrator.class);

    private final KogitoProcessRuntime runtime;
    private final RuleFlowProcessInstance processInstance;
    private final VariableScopeInstance variables;
    private final VariableScope targetDefinition;

    private VariableMigrator(KogitoProcessRuntime runtime, RuleFlowProcessInstance processInstance, VariableScopeInstance variables) {
        this.runtime = runtime;
        this.processInstance = processInstance;
        this.variables = variables;
        this.targetDefinition = processInstance.getProcess() instanceof ContextContainer
                ? (VariableScope) ((ContextContainer) processInstance.getProcess()).getDefaultContext(VariableScope.VARIABLE_SCOPE)
                : null;
    }

    /**
     * @param runtime runtime used to fire variable changed events; when {@code null} no events are fired
     */
    static void migrate(KogitoProcessRuntime runtime, RuleFlowProcessInstance processInstance, List<VariableMigrationPlan> plans) {
        if (plans.isEmpty()) {
            return;
        }
        VariableScopeInstance variables = (VariableScopeInstance) processInstance.getContextInstance(VariableScope.VARIABLE_SCOPE);
        if (variables == null) {
            LOGGER.warn("Process instance {} has no variable scope, {} variable migration rule(s) not applied", processInstance.getStringId(), plans.size());
            return;
        }
        VariableMigrator migrator = new VariableMigrator(runtime, processInstance, variables);
        plans.forEach(migrator::apply);
    }

    private void apply(VariableMigrationPlan plan) {
        LOGGER.debug("Applying {} to process instance {}", plan, processInstance.getStringId());
        switch (plan.getAction()) {
            case RENAME:
                rename(plan.getFrom(), plan.getTo());
                break;
            case DEFAULT:
                applyDefault(plan.getName(), plan.getValue());
                break;
            case REMOVE:
                change(plan.getName(), null);
                break;
        }
    }

    /**
     * Moves the value of {@code from} to {@code to}. An existing value in {@code to} is overwritten.
     */
    private void rename(String from, String to) {
        Object value = valueOf(from);
        if (value == null || from.equals(to)) {
            return;
        }
        if (valueOf(to) != null) {
            LOGGER.warn("Process instance {}: renaming variable '{}' overwrites the existing value of variable '{}'", processInstance.getStringId(), from, to);
        }
        verifyTargetType(to, value);
        change(to, value);
        change(from, null);
    }

    private void applyDefault(String name, Object value) {
        if (valueOf(name) == null) {
            verifyTargetType(name, value);
            change(name, value);
        }
    }

    private void change(String name, Object newValue) {
        Object oldValue = valueOf(name);
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        List<String> tags = targetDefinition != null ? targetDefinition.tags(name) : Collections.emptyList();
        if (runtime != null) {
            runtime.getProcessEventSupport().fireBeforeVariableChanged(name, name, oldValue, newValue, tags, processInstance, null, runtime.getKieRuntime());
        }
        variables.internalSetVariable(name, newValue);
        if (runtime != null) {
            runtime.getProcessEventSupport().fireAfterVariableChanged(name, name, oldValue, newValue, tags, processInstance, null, runtime.getKieRuntime());
        }
    }

    private Object valueOf(String name) {
        return variables.getVariables().get(name);
    }

    /**
     * Fails the migration when the value does not match the type declared for the variable in the target process.
     * Variables not declared in the target process are not checked.
     */
    private void verifyTargetType(String name, Object value) {
        Variable variable = targetDefinition != null ? targetDefinition.findVariable(name) : null;
        if (value != null && variable != null && variable.getType() != null && !variable.getType().verifyDataType(value)) {
            throw new IllegalArgumentException(String.format(
                    "Cannot migrate process instance %s: variable '%s' in target process %s expects type %s but the value is of type %s",
                    processInstance.getStringId(), name, processInstance.getProcessId(), variable.getType().getStringType(), value.getClass().getName()));
        }
    }
}
