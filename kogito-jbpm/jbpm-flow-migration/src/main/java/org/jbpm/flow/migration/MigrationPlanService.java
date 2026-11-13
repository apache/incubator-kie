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

import java.util.HashMap;
import java.util.Map;

import org.jbpm.flow.migration.model.MigrationPlan;
import org.jbpm.flow.migration.model.ProcessDefinitionMigrationPlan;
import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.jbpm.workflow.instance.impl.NodeInstanceImpl;
import org.kie.kogito.Model;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcessInstance;
import org.kie.kogito.process.Processes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Functions.identity;
import static java.util.stream.Collectors.toMap;

/**
 * The migration system is limited in this way.
 * Cannot have more that one identifier process deployed (version is fixed)
 * if there are several migration plans defined for the same source only one is taken into account
 */
public class MigrationPlanService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationPlanService.class);

    private MigrationPlanProvider migrationPlanProvider;
    private Map<ProcessDefinitionMigrationPlan, MigrationPlan> migrations;

    public MigrationPlanService() {
        this(MigrationPlanProvider.newMigrationPlanProviderBuilder().withEnvironmentDefaults().build());
    }

    public MigrationPlanService(MigrationPlanProvider migrationPlanProvider) {
        this.migrationPlanProvider = migrationPlanProvider;
        this.migrations = new HashMap<>();
        this.migrations.putAll(this.migrationPlanProvider.findMigrationPlans().stream().collect(toMap(MigrationPlan::getSource, identity())));
    }

    public void migrateProcessElement(Processes processes, KogitoWorkflowProcessInstance processInstance) {
        migrateProcessElement(null, processes, processInstance);
    }

    /**
     * @param runtime runtime used to fire variable changed events for migrated variables; when {@code null} no events are fired
     */
    public void migrateProcessElement(KogitoProcessRuntime runtime, Processes processes, KogitoWorkflowProcessInstance processInstance) {
        MigrationPlan plan = getMigrationPlan(processes, processInstance);
        if (plan != null) {
            // the process will have to do nothing as it is done by the engine itself
            LOGGER.info("Process instance {} will be migrated from {} to {} with plan {}",
                    processInstance.getStringId(),
                    plan.getProcessMigrationPlan().getSourceProcessDefinition(),
                    plan.getProcessMigrationPlan().getTargetProcessDefinition(),
                    plan.getName());

            RuleFlowProcessInstance ruleFlowProcessInstance = (RuleFlowProcessInstance) processInstance;
            ruleFlowProcessInstance.setProcessId(plan.getProcessMigrationPlan().getTargetProcessDefinition().getProcessId());
            ruleFlowProcessInstance.setProcessVersion(plan.getProcessMigrationPlan().getTargetProcessDefinition().getProcessVersion());
            VariableMigrator.migrate(runtime, ruleFlowProcessInstance, plan.getProcessMigrationPlan().getVariableMigrationPlan());
        } else {
            LOGGER.debug("Process instance {} won't be migrated", processInstance);
        }
    }

    public void migrateNodeElement(Processes processes, KogitoNodeInstance nodeInstance) {
        KogitoWorkflowProcessInstance pi = (KogitoWorkflowProcessInstance) nodeInstance.getProcessInstance();
        MigrationPlan plan = getMigrationPlan(processes, pi);
        if (plan == null) {
            return;
        }

        LOGGER.debug("Migration node element {}", nodeInstance);
        NodeInstanceImpl impl = (NodeInstanceImpl) nodeInstance;
        impl.setNodeId(plan.getProcessMigrationPlan().getNodeMigratedFor(nodeInstance));
    }

    // we check the target deployed in the container is the same as the target in the migration plan
    private MigrationPlan getMigrationPlan(Processes processes, KogitoWorkflowProcessInstance processInstance) {
        // first check if we need a migration as the process being set should be not be the same as the process set in the 
        // process being loaded.
        String currentProcessId = processInstance.getProcess().getId();
        String currentVersion = processInstance.getProcess().getVersion();
        ProcessDefinitionMigrationPlan currentProcessDefinition = new ProcessDefinitionMigrationPlan(currentProcessId, currentVersion);

        RuleFlowProcessInstance pi = (RuleFlowProcessInstance) processInstance;
        ProcessDefinitionMigrationPlan processStateDefinition = new ProcessDefinitionMigrationPlan(pi.getProcessId(), pi.getProcessVersion());

        // check if definition and state match. we don't need to perform any migration.
        if (currentProcessDefinition.equals(processStateDefinition)) {
            return null;
        }

        // there is no migration plan define for the source
        MigrationPlan plan = migrations.get(processStateDefinition);
        if (plan == null) {
            LOGGER.debug("No migration plan defined for process state {}.", processStateDefinition);
            return null;
        }

        // current process definition matches the target process of the migration plan
        ProcessDefinitionMigrationPlan targetDefinition = plan.getProcessMigrationPlan().getTargetProcessDefinition();
        if (!targetDefinition.equals(currentProcessDefinition)) {
            LOGGER.debug("Migration plan found for {} does not match target definition {}, Found plan to {}.", processStateDefinition, currentProcessDefinition, targetDefinition);
            return null;
        }

        // target process not being deployed
        if (!processes.processIds().contains(targetDefinition.getProcessId())) {
            LOGGER.debug("No migration target defintion deployed in this container {} for migrating {}.", targetDefinition, processStateDefinition);
            return null;
        }

        // target process not matching version
        org.kie.kogito.process.Process<? extends Model> process = processes.processById(targetDefinition.getProcessId());
        ProcessDefinitionMigrationPlan targetDeployed =
                new ProcessDefinitionMigrationPlan(process.id(), process.version());

        return targetDeployed.equals(targetDefinition) ? plan : null;
    }

    public boolean isEqualVersion(Processes processes, KogitoWorkflowProcessInstance processInstance) {
        String currentProcessId = processInstance.getProcess().getId();
        String currentVersion = processInstance.getProcess().getVersion();
        ProcessDefinitionMigrationPlan currentProcessDefinition = new ProcessDefinitionMigrationPlan(currentProcessId, currentVersion);

        RuleFlowProcessInstance pi = (RuleFlowProcessInstance) processInstance;
        ProcessDefinitionMigrationPlan processStateDefinition = new ProcessDefinitionMigrationPlan(pi.getProcessId(), pi.getProcessVersion());

        // check if definition and state match. we don't need to perform any migration.
        return currentProcessDefinition.equals(processStateDefinition);
    }

    public boolean hasMigrationPlan(Processes processes, KogitoWorkflowProcessInstance processInstance) {
        return getMigrationPlan(processes, processInstance) != null;
    }
}
