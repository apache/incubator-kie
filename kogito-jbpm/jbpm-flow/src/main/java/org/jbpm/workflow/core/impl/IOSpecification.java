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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jbpm.workflow.core.node.Assignment;

public class IOSpecification implements Serializable {

    private static final long serialVersionUID = 7380526115340461267L;

    private List<DataDefinition> dataInputs = new ArrayList<>();
    private List<DataDefinition> dataOutputs = new ArrayList<>();
    private List<DataAssociation> dataInputAssociation = new ArrayList<>();
    private List<DataAssociation> dataOutputAssociation = new ArrayList<>();

    public List<DataDefinition> getDataInputs() {
        return dataInputs;
    }

    public Map<String, String> getInputTypes() {
        return getDataInputs().stream().collect(Collectors.toMap(DataDefinition::getLabel, DataDefinition::getType));
    }

    public Map<String, String> getOutputTypes() {
        return getDataOutputs().stream().collect(Collectors.toMap(DataDefinition::getLabel, DataDefinition::getType));
    }

    public List<DataDefinition> getDataOutputs() {
        return dataOutputs;
    }

    public Map<String, DataDefinition> getDataInput() {
        return dataInputs.stream().collect(Collectors.toMap(DataDefinition::getId, Function.identity()));
    }

    public Map<String, DataDefinition> getDataOutput() {
        return dataOutputs.stream().collect(Collectors.toMap(DataDefinition::getId, Function.identity()));
    }

    public List<DataAssociation> getDataInputAssociation() {
        return dataInputAssociation;
    }

    public List<DataAssociation> getDataOutputAssociation() {
        return dataOutputAssociation;
    }

    @Override
    public String toString() {
        return "IOSpecification [dataInputs=" + dataInputs + ", dataOutputs=" + dataOutputs + ", dataInputAssociation=" +
                dataInputAssociation + ", dataOutputAssociation=" + dataOutputAssociation + "]";
    }

    public Map<String, String> getInputMapping() {
        Map<String, String> mapping = new HashMap<>();
        for (DataAssociation da : dataInputAssociation) {
            if (da.getAssignments().isEmpty()) {
                mapping.put(da.getTarget().getLabel(), da.getSources().get(0).getLabel());
            } else if (da.getAssignments().get(0).getDialect() == null) {
                DataDefinition from = da.getAssignments().get(0).getFrom();
                mapping.put(da.getAssignments().get(0).getTo().getLabel(), from.hasExpression() ? from.getExpression() : from.getLabel());
            }
        }
        return mapping;
    }

    public Map<String, String> getOutputMapping() {
        Map<String, String> mapping = new HashMap<>();
        for (DataAssociation da : dataOutputAssociation) {
            if (da.getAssignments().isEmpty()) {
                mapping.put(da.getTarget().getLabel(), da.getSources().get(0).getLabel());
            } else if (da.getAssignments().get(0).getDialect() == null) {
                DataDefinition to = da.getAssignments().get(0).getTo();
                mapping.put(to.hasExpression() ? to.getExpression() : to.getLabel(), da.getAssignments().get(0).getFrom().getLabel());
            }
        }
        return mapping;
    }

    public Map<String, String> getOutputMappingBySources() {
        Map<String, String> mapping = new HashMap<>();
        for (DataAssociation da : dataOutputAssociation) {
            if (da.getAssignments().isEmpty()) {
                mapping.put(da.getSources().get(0).getLabel(), da.getTarget().getLabel());
            } else if (da.getAssignments().get(0).getDialect() == null) {
                DataDefinition to = da.getAssignments().get(0).getTo();
                mapping.put(da.getAssignments().get(0).getFrom().getLabel(), to.hasExpression() ? to.getExpression() : to.getLabel());
            }
        }
        return mapping;
    }

    public void addInputMapping(String source, String target) {
        DataDefinition defSource = DataDefinition.toSimpleDefinition(source);
        DataDefinition defTarget = DataDefinition.toSimpleDefinition(target);
        dataInputs.add(defTarget);
        if (defSource.hasExpression()) {
            dataInputAssociation.add(new DataAssociation(buildAssigment(new Assignment(null, defSource, defTarget))));
        } else {
            dataInputAssociation.add(new DataAssociation(defSource, defTarget, null, null));
        }
    }

    public void addOutputMapping(String source, String target) {
        DataDefinition defSource = DataDefinition.toSimpleDefinition(source);
        DataDefinition defTarget = DataDefinition.toSimpleDefinition(target);
        dataOutputs.add(defSource);
        if (defSource.hasExpression() || defTarget.hasExpression()) {
            dataOutputAssociation.add(new DataAssociation(buildAssigment(new Assignment(null, defSource, defTarget))));
        } else {
            dataOutputAssociation.add(new DataAssociation(defSource, defTarget, null, null));
        }
    }

    private Assignment buildAssigment(Assignment assignment) {
        if (assignment.getFrom().hasExpression()) {
            assignment.setMetaData("Action", new InputExpressionAssignment(assignment.getFrom(), assignment.getTo()));
        } else if (assignment.getTo().hasExpression()) {
            assignment.setMetaData("Action", new OutputExpressionAssignment(assignment.getFrom(), assignment.getTo()));
        }
        return assignment;
    }

    public boolean containsInputLabel(String label) {
        return dataInputs.stream().anyMatch(e -> e.getLabel().equals(label));
    }

    public boolean containsOutputLabel(String label) {
        return dataOutputs.stream().anyMatch(e -> e.getLabel().equals(label));
    }

}
