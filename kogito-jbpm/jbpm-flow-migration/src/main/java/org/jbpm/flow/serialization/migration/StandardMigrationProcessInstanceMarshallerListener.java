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
package org.jbpm.flow.serialization.migration;

import org.jbpm.flow.migration.MigrationPlanService;
import org.jbpm.flow.serialization.ProcessInstanceMarshallerListener;
import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcessInstance;
import org.kie.kogito.process.Processes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class acts as a bridge between migration service and serialization system
 */
public class StandardMigrationProcessInstanceMarshallerListener implements ProcessInstanceMarshallerListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(StandardMigrationProcessInstanceMarshallerListener.class);

    private MigrationPlanService migrationPlanService;

    public StandardMigrationProcessInstanceMarshallerListener() {
        this.migrationPlanService = new MigrationPlanService();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void afterUnmarshallProcess(KogitoProcessRuntime runtime, KogitoWorkflowProcessInstance processInstance) {
        if (!migrationPlanService.hasMigrationPlan(runtime.getApplication().get(Processes.class), processInstance)) {
            if (!this.migrationPlanService.isEqualVersion(runtime.getApplication().get(Processes.class), processInstance)) {
                LOGGER.debug("Process State version and process container mismatch. Migrating process without plan.");
                RuleFlowProcessInstance ruleFlowProcessInstance = (RuleFlowProcessInstance) processInstance;
                ruleFlowProcessInstance.setProcess(ruleFlowProcessInstance.getProcess());
            }
            return;
        }
        LOGGER.debug("Migration processInstance state {}-{} and definition {}-{}",
                processInstance.getProcessId(), processInstance.getProcessVersion(), processInstance.getProcess().getId(), processInstance.getProcess().getVersion());
        migrationPlanService.migrateProcessElement(runtime, runtime.getApplication().get(Processes.class), processInstance);
        runtime.getProcessEventSupport().fireOnMigration(processInstance, runtime.getKieRuntime());

    }

    @Override
    public void afterUnmarshallNode(KogitoProcessRuntime runtime, KogitoNodeInstance nodeInstance) {
        if (!migrationPlanService.hasMigrationPlan(runtime.getApplication().get(Processes.class), (KogitoWorkflowProcessInstance) nodeInstance.getProcessInstance())) {
            return;
        }
        LOGGER.debug("Migration nodeInstance {}", nodeInstance);
        migrationPlanService.migrateNodeElement(runtime.getApplication().get(Processes.class), nodeInstance);
    }

}
