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
package org.jbpm.flow.migration.model;

import java.util.Objects;

/**
 * A rule that changes a process instance variable while the instance is migrated.
 * Which fields are used depends on the {@link VariableMigrationAction}:
 * <ul>
 * <li>{@code rename}: {@code from} and {@code to}. An existing value in {@code to} is overwritten (a warning is logged).</li>
 * <li>{@code default}: {@code name} and {@code value}. Only applied when the variable has no value.</li>
 * <li>{@code remove}: {@code name}</li>
 * </ul>
 * {@code rename} and {@code remove} set the old variable to {@code null}; the variable name itself stays in the
 * process instance variables. Values written to a variable declared in the target process must match its type,
 * otherwise the migration fails.
 */
public class VariableMigrationPlan {

    private VariableMigrationAction action;
    private String name;
    private String from;
    private String to;
    private Object value;

    public VariableMigrationPlan() {
        // do nothing
    }

    public VariableMigrationPlan(VariableMigrationAction action, String name, String from, String to, Object value) {
        this.action = action;
        this.name = name;
        this.from = from;
        this.to = to;
        this.value = value;
    }

    public VariableMigrationAction getAction() {
        return action;
    }

    public void setAction(VariableMigrationAction action) {
        this.action = action;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    /**
     * Checks that the fields required by the action are present.
     *
     * @throws IllegalArgumentException if the action or one of its required fields is missing,
     *         or if a rename has the same {@code from} and {@code to}
     */
    public void validate() {
        if (action == null) {
            throw new IllegalArgumentException("Variable migration plan without action: " + this);
        }
        switch (action) {
            case RENAME:
                requireField("from", from);
                requireField("to", to);
                if (from.equals(to)) {
                    throw new IllegalArgumentException("Variable migration plan " + this + " renames a variable to itself");
                }
                break;
            case DEFAULT:
                requireField("name", name);
                if (value == null) {
                    throw new IllegalArgumentException("Variable migration plan " + this + " requires field value");
                }
                break;
            case REMOVE:
                requireField("name", name);
                break;
        }
    }

    private void requireField(String field, String fieldValue) {
        if (fieldValue == null || fieldValue.isBlank()) {
            throw new IllegalArgumentException("Variable migration plan " + this + " requires field " + field);
        }
    }

    @Override
    public String toString() {
        return "VariableMigrationPlan [action=" + action + ", name=" + name + ", from=" + from + ", to=" + to + ", value=" + value + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, name, from, to, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        VariableMigrationPlan other = (VariableMigrationPlan) obj;
        return action == other.action && Objects.equals(name, other.name) && Objects.equals(from, other.from) && Objects.equals(to, other.to)
                && Objects.equals(value, other.value);
    }

}
