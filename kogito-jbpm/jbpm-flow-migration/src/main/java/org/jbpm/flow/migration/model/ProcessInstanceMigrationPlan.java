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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.kie.api.definition.process.WorkflowElementIdentifier;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;

public class ProcessInstanceMigrationPlan {

    private ProcessDefinitionMigrationPlan sourceProcessDefinition;
    private ProcessDefinitionMigrationPlan targetProcessDefinition;

    private List<NodeInstanceMigrationPlan> nodeInstanceMigrationPlan;

    private List<VariableMigrationPlan> variableMigrationPlan;

    public List<VariableMigrationPlan> getVariableMigrationPlan() {
        return variableMigrationPlan != null ? variableMigrationPlan : Collections.emptyList();
    }

    public void setVariableMigrationPlan(List<VariableMigrationPlan> variableMigrationPlan) {
        this.variableMigrationPlan = variableMigrationPlan;
    }

    public List<NodeInstanceMigrationPlan> getNodeInstanceMigrationPlan() {
        return nodeInstanceMigrationPlan;
    }

    public void setNodeInstanceMigrationPlan(List<NodeInstanceMigrationPlan> nodeInstanceMigrationPlan) {
        this.nodeInstanceMigrationPlan = nodeInstanceMigrationPlan;
    }

    public WorkflowElementIdentifier getNodeMigratedFor(KogitoNodeInstance instance) {
        List<NodeInstanceMigrationPlan> plans = nodeInstanceMigrationPlan.stream().filter(e -> e.getSourceNodeId().equals(instance.getNodeId())).toList();
        if (plans.isEmpty()) {
            return instance.getNodeId();
        } else if (plans.size() > 1) {
            throw new IllegalArgumentException("more than one node migration plan found for " + instance);
        }
        return plans.get(0).getTargetNodeId();
    }

    public ProcessDefinitionMigrationPlan getSourceProcessDefinition() {
        return sourceProcessDefinition;
    }

    public void setSourceProcessDefinition(ProcessDefinitionMigrationPlan source) {
        this.sourceProcessDefinition = source;
    }

    public ProcessDefinitionMigrationPlan getTargetProcessDefinition() {
        return targetProcessDefinition;
    }

    public void setTargetProcessDefinition(ProcessDefinitionMigrationPlan target) {
        this.targetProcessDefinition = target;
    }

    @Override
    public String toString() {
        return "ProcessMigrationPlan [source=" + sourceProcessDefinition + ", target=" + targetProcessDefinition + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeInstanceMigrationPlan, variableMigrationPlan, sourceProcessDefinition, targetProcessDefinition);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ProcessInstanceMigrationPlan other = (ProcessInstanceMigrationPlan) obj;
        return Objects.equals(nodeInstanceMigrationPlan, other.nodeInstanceMigrationPlan) && Objects.equals(variableMigrationPlan, other.variableMigrationPlan)
                && Objects.equals(sourceProcessDefinition, other.sourceProcessDefinition)
                && Objects.equals(targetProcessDefinition, other.targetProcessDefinition);
    }

}
