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
package org.kie.kogito.persistence.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.kie.flyway.initializer.KieFlywayInitializer;
import org.kie.kogito.Application;
import org.kie.kogito.Model;
import org.kie.kogito.auth.IdentityProviders;
import org.kie.kogito.auth.SecurityPolicy;
import org.kie.kogito.internal.process.workitem.Policy;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.Processes;
import org.kie.kogito.process.SignalFactory;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.process.bpmn2.BpmnProcess;
import org.kie.kogito.process.bpmn2.BpmnProcessInstance;
import org.kie.kogito.process.bpmn2.BpmnVariables;
import org.kie.kogito.process.bpmn2.StaticApplicationAssembler;
import org.kie.kogito.process.impl.AbstractProcessInstance;
import org.kie.kogito.process.impl.StaticProcessConfig;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.kie.kogito.internal.process.runtime.KogitoProcessInstance.STATE_ACTIVE;
import static org.kie.kogito.internal.process.runtime.KogitoProcessInstance.STATE_COMPLETED;
import static org.kie.kogito.test.utils.ProcessInstancesTestUtils.abort;
import static org.kie.kogito.test.utils.ProcessInstancesTestUtils.assertEmpty;
import static org.kie.kogito.test.utils.ProcessInstancesTestUtils.assertOne;
import static org.kie.kogito.test.utils.ProcessInstancesTestUtils.getFirst;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

abstract class AbstractProcessInstancesIT {

    public static final String TEST_ID = "02ac3854-46ee-42b7-8b63-5186c9889d96";
    public static Policy securityPolicy = SecurityPolicy.of(IdentityProviders.of("john"));

    public static void initMigration(DataSource dataSource) {
        KieFlywayInitializer.builder()
                .withDatasource(dataSource)
                .build()
                .migrate();
    }

    /** Loads multiple BPMN files into a single application context so that call-activity/subprocess wiring works. */
    private Map<String, BpmnProcess> createProcesses(DataSource dataSource, Boolean lock, String... fileNames) {
        Map<String, BpmnProcess> processMap = new HashMap<>();
        Processes processesSelfRef = new Processes() {
            @Override
            public Collection<String> processIds() {
                return processMap.keySet();
            }

            @Override
            public Collection<Process<? extends Model>> processes() {
                return Collections.unmodifiableCollection(processMap.values());
            }

            @Override
            public Process<? extends Model> processById(String processId) {
                return processMap.get(processId);
            }
        };

        StaticProcessConfig processConfig = StaticProcessConfig.newStaticProcessConfigBuilder()
                .withWorkItemHandler("Human Task", new DefaultKogitoWorkItemHandler())
                .build();

        Application application = StaticApplicationAssembler.instance().newStaticApplication(
                new TestProcessInstancesFactory(dataSource, lock, processesSelfRef), processConfig, fileNames);

        Processes container = application.get(Processes.class);
        for (String processId : container.processIds()) {
            BpmnProcess p = (BpmnProcess) container.processById(processId);
            processMap.put(processId, p);
            abort(p.instances());
        }
        return processMap;
    }

    private BpmnProcess createProcess(DataSource dataSource, Boolean lock, String fileName) {
        StaticProcessConfig processConfig = StaticProcessConfig.newStaticProcessConfigBuilder()
                .withWorkItemHandler("Human Task", new DefaultKogitoWorkItemHandler())
                .build();

        Application application = StaticApplicationAssembler.instance().newStaticApplication(new TestProcessInstancesFactory(dataSource, lock), processConfig, fileName);

        Processes container = application.get(Processes.class);
        String processId = container.processIds().stream().findFirst().get();
        Process<? extends Model> process = container.processById(processId);

        abort(process.instances());
        BpmnProcess compiledProcess = (BpmnProcess) process;
        return compiledProcess;
    }

    boolean lock() {
        return false;
    }

    abstract DataSource getDataSource();

    @Test
    void testBasicTaskFlow() {
        BpmnProcess process = createProcess(getDataSource(), lock(), "BPMN2-UserTask.bpmn2");
        ProcessInstance<BpmnVariables> processInstance = process.createInstance(BpmnVariables.create(singletonMap("test", "test")));
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(STATE_ACTIVE);
        assertThat(processInstance.description()).isEqualTo("BPMN2-UserTask");

        JDBCProcessInstances processInstances = (JDBCProcessInstances) process.instances();
        assertThat(processInstances.exists(processInstance.id())).isTrue();
        verify(processInstances).create(any(), any());

        String testVar = (String) processInstance.variables().get("test");
        assertThat(testVar).isEqualTo("test");

        assertThat(processInstance.description()).isEqualTo("BPMN2-UserTask");

        assertThat(getFirst(processInstances).workItems(securityPolicy)).hasSize(1);

        WorkItem workItem = processInstance.workItems(securityPolicy).get(0);
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameters()).containsEntry("ActorId", "john");
        processInstance.completeWorkItem(workItem.getId(), null, securityPolicy);
        assertThat(processInstance.status()).isEqualTo(STATE_COMPLETED);

        processInstances = (JDBCProcessInstances) process.instances();
        verify(processInstances, times(1)).remove(processInstance.id());
        assertEmpty(process.instances());
    }

    @Test
    void testMultipleProcesses() {
        BpmnProcess utProcess = createProcess(getDataSource(), lock(), "BPMN2-UserTask.bpmn2");
        ProcessInstance<BpmnVariables> utProcessInstance = utProcess.createInstance(BpmnVariables.create());
        utProcessInstance.start();

        BpmnProcess scriptProcess = createProcess(getDataSource(), lock(), "BPMN2-UserTask-Script.bpmn2");
        ProcessInstance<BpmnVariables> scriptProcessInstance = scriptProcess.createInstance(BpmnVariables.create());
        scriptProcessInstance.start();

        //Try to remove process instance from another process id
        ((JDBCProcessInstances) utProcess.instances()).remove(scriptProcessInstance.id());
        ((JDBCProcessInstances) scriptProcess.instances()).remove(utProcessInstance.id());

        assertOne(utProcess.instances());
        assertThat(utProcess.instances().findById(utProcessInstance.id())).isPresent();
        assertThat(utProcess.instances().findById(scriptProcessInstance.id())).isEmpty();

        assertOne(scriptProcess.instances());
        assertThat(scriptProcess.instances().findById(scriptProcessInstance.id())).isPresent();
        assertThat(scriptProcess.instances().findById(utProcessInstance.id())).isEmpty();

        ((JDBCProcessInstances) utProcess.instances()).remove(utProcessInstance.id());
        assertEmpty(utProcess.instances());
        assertThat(utProcess.instances().findById(utProcessInstance.id())).isEmpty();
        assertThat(utProcess.instances().findById(scriptProcessInstance.id())).isEmpty();

        ((JDBCProcessInstances) scriptProcess.instances()).remove(scriptProcessInstance.id());
        assertEmpty(scriptProcess.instances());
        assertThat(scriptProcess.instances().findById(scriptProcessInstance.id())).isEmpty();
        assertThat(scriptProcess.instances().findById(utProcessInstance.id())).isEmpty();
    }

    @Test
    void testBasicFlow() {
        final String businessKey = "manolo";
        BpmnProcess process = createProcess(getDataSource(), lock(), "BPMN2-UserTask.bpmn2");
        ProcessInstance<BpmnVariables> processInstance = process.createInstance(businessKey, BpmnVariables.create(singletonMap("test", "test")));
        processInstance.start();

        JDBCProcessInstances processInstances = (JDBCProcessInstances) process.instances();
        Optional<?> foundOne = processInstances.findByBusinessKey(businessKey);
        BpmnProcessInstance instanceOne = (BpmnProcessInstance) foundOne.get();
        assertThat(instanceOne).extracting(ProcessInstance::businessKey).isEqualTo(businessKey);
        assertThat(instanceOne).extracting(ProcessInstance::id).isEqualTo(processInstance.id());

        assertThat(processInstances.exists(TEST_ID)).isFalse();
        Optional<?> foundTwo = processInstances.findById(TEST_ID);
        assertThat(foundTwo).isEmpty();

        processInstances.remove(processInstance.id());
        assertEmpty(process.instances());
    }

    @Test
    public void testUpdate() {
        BpmnProcess process = createProcess(getDataSource(), lock(), "BPMN2-UserTask.bpmn2");
        ProcessInstance<BpmnVariables> processInstance = process.createInstance(BpmnVariables.create(singletonMap("test", "test")));
        processInstance.start();

        JDBCProcessInstances processInstances = (JDBCProcessInstances) process.instances();

        BpmnProcessInstance instanceOne = (BpmnProcessInstance) processInstances.findById(processInstance.id()).get();

        BpmnProcessInstance instanceTwo = (BpmnProcessInstance) processInstances.findById(processInstance.id()).get();
        assertThat(instanceOne.version()).isEqualTo(lock() ? 1L : 0);
        assertThat(instanceTwo.version()).isEqualTo(lock() ? 1L : 0);

        instanceOne.updateVariables(BpmnVariables.create(singletonMap("s", "test")));
        instanceOne = (BpmnProcessInstance) processInstances.findById(processInstance.id()).get();
        assertThat(instanceOne.version()).isEqualTo(lock() ? 2L : 0);

        processInstances.remove(processInstance.id());
        assertEmpty(process.instances());
    }

    @Test
    public void testMigrateAll() throws Exception {
        BpmnProcess process = createProcess(getDataSource(), lock(), "BPMN2-UserTask.bpmn2");
        ProcessInstance<BpmnVariables> processInstance1 = process.createInstance(BpmnVariables.create(Collections.singletonMap("test", "test")));
        processInstance1.start();

        ProcessInstance<BpmnVariables> processInstance2 = process.createInstance(BpmnVariables.create(Collections.singletonMap("test", "test")));
        processInstance2.start();

        process.instances().migrateAll("migrated", "2");

        DataSource dataSource = getDataSource();
        try (Connection connection = dataSource.getConnection();
                ResultSet resultSet = connection.createStatement().executeQuery("SELECT process_id, process_version FROM process_instances")) {

            while (resultSet.next()) {
                assertEquals(resultSet.getString(1), "migrated");
                assertEquals(resultSet.getString(2), "2");
            }
        }
    }

    @Test
    public void testMigrateSingle() throws Exception {
        BpmnProcess process = createProcess(getDataSource(), lock(), "BPMN2-UserTask.bpmn2");
        ProcessInstance<BpmnVariables> processInstance1 = process.createInstance(BpmnVariables.create(Collections.singletonMap("test", "test")));
        processInstance1.start();

        ProcessInstance<BpmnVariables> processInstance2 = process.createInstance(BpmnVariables.create(Collections.singletonMap("test", "test")));
        processInstance2.start();

        process.instances().migrateProcessInstances("migrated", "2", processInstance1.id());

        DataSource dataSource = getDataSource();
        try (Connection connection = dataSource.getConnection();
                ResultSet resultSet = connection.createStatement().executeQuery("SELECT process_id, process_version FROM process_instances WHERE id = '" + processInstance1.id() + "'")) {

            while (resultSet.next()) {
                assertEquals(resultSet.getString(1), "migrated");
                assertEquals(resultSet.getString(2), "2");
            }
        }

        try (Connection connection = dataSource.getConnection();
                ResultSet resultSet = connection.createStatement().executeQuery("SELECT process_id, process_version FROM process_instances WHERE id = '" + processInstance2.id() + "'")) {

            while (resultSet.next()) {
                assertEquals(resultSet.getString(1), "BPMN2_UserTask");
                assertEquals(resultSet.getString(2), "1.0");
            }
        }

    }

    @Test
    void testMigrateTaskFlow() {
        BpmnProcess processV1 = createProcess(getDataSource(), lock(), "BPMN2-UserTask.bpmn2");
        BpmnProcess processV2 = createProcess(getDataSource(), lock(), "BPMN2-UserTask-v2.bpmn2");
        ProcessInstance<BpmnVariables> processInstance = processV1.createInstance(BpmnVariables.create(singletonMap("test", "test")));
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(STATE_ACTIVE);
        assertThat(processInstance.description()).isEqualTo("BPMN2-UserTask");

        JDBCProcessInstances processInstancesV1 = (JDBCProcessInstances) processV1.instances();
        assertThat(processInstancesV1.exists(processInstance.id())).isTrue();
        verify(processInstancesV1).create(any(), any());

        assertThat(processInstance.description()).isEqualTo("BPMN2-UserTask");

        processV1.instances().migrateProcessInstances("BPMN2_UserTask", "2.0", processInstance.id());

        JDBCProcessInstances processInstancesV2 = (JDBCProcessInstances) processV2.instances();
        assertThat(processInstancesV2.exists(processInstance.id())).isTrue();

        processInstance = (ProcessInstance<BpmnVariables>) processInstancesV2.findById(processInstance.id()).get();
        WorkItem workItem = processInstance.workItems(securityPolicy).get(0);
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameters()).containsEntry("ActorId", "john");

        processInstance.completeWorkItem(workItem.getId(), null, securityPolicy);
        assertThat(processInstance.status()).isEqualTo(STATE_COMPLETED);

        processInstancesV2 = (JDBCProcessInstances) processV2.instances();
        verify(processInstancesV2, times(1)).remove(processInstance.id());
        assertEmpty(processV2.instances());
    }

    @Test
    public void testRemove() {
        BpmnProcess process = createProcess(getDataSource(), lock(), "BPMN2-UserTask.bpmn2");
        ProcessInstance<BpmnVariables> processInstance = process.createInstance(BpmnVariables.create(singletonMap("test", "test")));
        processInstance.start();

        JDBCProcessInstances processInstances = (JDBCProcessInstances) process.instances();
        assertOne(processInstances);
        BpmnProcessInstance instanceOne = (BpmnProcessInstance) processInstances.findById(processInstance.id()).get();
        BpmnProcessInstance instanceTwo = (BpmnProcessInstance) processInstances.findById(processInstance.id()).get();
        assertThat(instanceOne.version()).isEqualTo(lock() ? 1L : 0);
        assertThat(instanceTwo.version()).isEqualTo(lock() ? 1L : 0);

        processInstances.remove(instanceOne.id());
        processInstances.remove(instanceTwo.id());
        assertEmpty(processInstances);
    }

    @Test
    void testProcessWithDifferentVersion() {
        BpmnProcess processV1 = createProcess(getDataSource(), lock(), "BPMN2-UserTask.bpmn2");
        BpmnProcess processV2 = createProcess(getDataSource(), lock(), "BPMN2-UserTask-v2.bpmn2");

        assertThat(processV1.process().getVersion()).isEqualTo("1.0");
        assertThat(processV2.process().getVersion()).isEqualTo("2.0");

        ProcessInstance<BpmnVariables> processInstanceV1 = processV1.createInstance(BpmnVariables.create(singletonMap("test", "test")));
        processInstanceV1.start();

        JDBCProcessInstances processInstancesV1 = (JDBCProcessInstances) processV1.instances();
        JDBCProcessInstances processInstancesV2 = (JDBCProcessInstances) processV2.instances();

        assertOne(processInstancesV1);
        assertThat(processInstancesV1.findById(processInstanceV1.id())).isPresent();

        assertEmpty(processInstancesV2);
        ProcessInstance<BpmnVariables> processInstanceV2 = processV2.createInstance(BpmnVariables.create(singletonMap("test", "test")));
        processInstanceV2.start();
        assertOne(processInstancesV2);
        assertThat(processInstancesV2.findById(processInstanceV2.id())).isPresent();

        processInstancesV1.remove(processInstanceV1.id());
        assertEmpty(processInstancesV1);

        assertOne(processInstancesV2);
        processInstancesV2.remove(processInstanceV2.id());
        assertEmpty(processInstancesV2);
    }

    @Test
    public void testMigrateRootCascade() throws Exception {
        // Load the call-activity (no version) and its subprocess so that child rows are written with
        // root_process_id = "BPMN2_CallActivity" and root_process_version = null.
        Map<String, BpmnProcess> processes = createProcesses(getDataSource(), lock(),
                "BPMN2-CallActivity.bpmn2", "BPMN2-CallActivity-v2.bpmn2", "BPMN2-UserTask.bpmn2");
        BpmnProcess callActivityProcess = processes.get("BPMN2_CallActivity");
        BpmnProcess userTaskProcess = processes.get("BPMN2_UserTask");

        // Start the call-activity instance — this spawns a BPMN2_UserTask subprocess
        ProcessInstance<BpmnVariables> parentInstance = callActivityProcess.createInstance(
                BpmnVariables.create(singletonMap("test", "cascade")));
        parentInstance.start();

        // Sanity: subprocess must be present in the BPMN2_UserTask instances table
        assertThat(userTaskProcess.instances().stream().count())
                .as("subprocess should have been created").isGreaterThanOrEqualTo(1);

        // Capture the subprocess instance id from the DB before migration
        String childId;
        try (Connection connection = getDataSource().getConnection();
                ResultSet rs = connection.createStatement().executeQuery(
                        "SELECT id FROM process_instances WHERE process_id = 'BPMN2_UserTask' AND root_process_id = 'BPMN2_CallActivity'")) {
            assertThat(rs.next()).as("child row should exist").isTrue();
            childId = rs.getString(1);
        }

        // Migrate the call-activity process to a new id/version
        callActivityProcess.instances().migrateAll("BPMN2_CallActivity_v2", "2.0");

        // The child subprocess row must have its root_process_id/root_process_version updated
        try (Connection connection = getDataSource().getConnection();
                ResultSet rs = connection.createStatement().executeQuery(
                        "SELECT root_process_id, root_process_version FROM process_instances WHERE id = '" + childId + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).as("child root_process_id must be updated").isEqualTo("BPMN2_CallActivity_v2");
            assertThat(rs.getString(2)).as("child root_process_version must be updated").isEqualTo("2.0");
        }

        // The in-memory unmarshalled instance must reflect the migrated root columns,
        // not the stale values baked into the payload before migration.
        AbstractProcessInstance<?> childInstance = (AbstractProcessInstance<?>) userTaskProcess.instances()
                .findById(childId).get();
        childInstance.executeInWorkflowProcessInstanceRead(pi -> {
            assertThat(pi.getRootProcessId())
                    .as("unmarshalled child rootProcessId must be overridden from DB column")
                    .isEqualTo("BPMN2_CallActivity_v2");
            assertThat(pi.getRootProcessVersion())
                    .as("unmarshalled child rootProcessVersion must be overridden from DB column")
                    .isEqualTo("2.0");
            return null;
        });
    }

    @Test
    public void testMigrateRootCascadeVersioned() throws Exception {
        // Same as testMigrateRootCascade but uses the versioned call-activity (drools:version="1.0")
        // so that root_process_version is stored as "1.0" rather than null.
        Map<String, BpmnProcess> processes = createProcesses(getDataSource(), lock(),
                "BPMN2-CallActivity.bpmn2", "BPMN2-CallActivity-v2.bpmn2", "BPMN2-UserTask.bpmn2");
        BpmnProcess callActivityProcess = processes.get("BPMN2_CallActivity");
        BpmnProcess userTaskProcess = processes.get("BPMN2_UserTask");

        ProcessInstance<BpmnVariables> parentInstance = callActivityProcess.createInstance(
                BpmnVariables.create(singletonMap("test", "cascade-versioned")));
        parentInstance.start();

        assertThat(userTaskProcess.instances().stream().count())
                .as("subprocess should have been created").isGreaterThanOrEqualTo(1);

        String childId;
        try (Connection connection = getDataSource().getConnection();
                ResultSet rs = connection.createStatement().executeQuery(
                        "SELECT id FROM process_instances WHERE process_id = 'BPMN2_UserTask' AND root_process_id = 'BPMN2_CallActivity'")) {
            assertThat(rs.next()).as("child row should exist").isTrue();
            childId = rs.getString(1);
        }

        callActivityProcess.instances().migrateAll("BPMN2_CallActivity_v2", "2.0");

        try (Connection connection = getDataSource().getConnection();
                ResultSet rs = connection.createStatement().executeQuery(
                        "SELECT root_process_id, root_process_version FROM process_instances WHERE id = '" + childId + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).as("child root_process_id must be updated").isEqualTo("BPMN2_CallActivity_v2");
            assertThat(rs.getString(2)).as("child root_process_version must be updated").isEqualTo("2.0");
        }

        // The in-memory unmarshalled instance must reflect the migrated root columns,
        // not the stale values baked into the payload before migration.
        AbstractProcessInstance<?> childInstance = (AbstractProcessInstance<?>) userTaskProcess.instances()
                .findById(childId).get();
        childInstance.executeInWorkflowProcessInstanceRead(pi -> {
            assertThat(pi.getRootProcessId())
                    .as("unmarshalled child rootProcessId must be overridden from DB column")
                    .isEqualTo("BPMN2_CallActivity_v2");
            assertThat(pi.getRootProcessVersion())
                    .as("unmarshalled child rootProcessVersion must be overridden from DB column")
                    .isEqualTo("2.0");
            return null;
        });
    }

    @Test
    public void testMigrateRootCascadeWithExplicitList() throws Exception {
        // Uses the unversioned call-activity (root_process_version = null).
        Map<String, BpmnProcess> processes = createProcesses(getDataSource(), lock(),
                "BPMN2-CallActivity.bpmn2", "BPMN2-CallActivity-v2.bpmn2", "BPMN2-UserTask.bpmn2");
        BpmnProcess callActivityProcess = processes.get("BPMN2_CallActivity");
        BpmnProcess userTaskProcess = processes.get("BPMN2_UserTask");

        // Start TWO call-activity instances – each spawns a BPMN2_UserTask child.
        ProcessInstance<BpmnVariables> parentInstance1 = callActivityProcess.createInstance(
                BpmnVariables.create(singletonMap("test", "cascade-explicit-1")));
        parentInstance1.start();

        ProcessInstance<BpmnVariables> parentInstance2 = callActivityProcess.createInstance(
                BpmnVariables.create(singletonMap("test", "cascade-explicit-2")));
        parentInstance2.start();

        // Sanity: both children must exist.
        assertThat(userTaskProcess.instances().stream().count())
                .as("two subprocesses should have been created").isGreaterThanOrEqualTo(2);

        // Resolve the child ids by their parent root_process_instance_id.
        String childId1;
        String childId2;
        try (Connection connection = getDataSource().getConnection();
                ResultSet rs = connection.createStatement().executeQuery(
                        "SELECT id, root_process_instance_id FROM process_instances"
                                + " WHERE process_id = 'BPMN2_UserTask' AND root_process_id = 'BPMN2_CallActivity'")) {
            assertThat(rs.next()).as("first child row should exist").isTrue();
            String firstChildId = rs.getString(1);
            String firstRootInstanceId = rs.getString(2);

            assertThat(rs.next()).as("second child row should exist").isTrue();
            String secondChildId = rs.getString(1);
            String secondRootInstanceId = rs.getString(2);

            // Map child IDs back to parentInstance1 / parentInstance2.
            if (firstRootInstanceId.equals(parentInstance1.id())) {
                childId1 = firstChildId;
                childId2 = secondChildId;
            } else {
                childId1 = secondChildId;
                childId2 = firstChildId;
            }
        }

        // Migrate ONLY parentInstance1 using the explicit-list overload.
        callActivityProcess.instances().migrateProcessInstances("BPMN2_CallActivity_v2", "2.0", parentInstance1.id());

        // The child of the migrated parent must have its root columns updated.
        try (Connection connection = getDataSource().getConnection();
                ResultSet rs = connection.createStatement().executeQuery(
                        "SELECT root_process_id, root_process_version FROM process_instances WHERE id = '" + childId1 + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).as("migrated child root_process_id must be updated").isEqualTo("BPMN2_CallActivity_v2");
            assertThat(rs.getString(2)).as("migrated child root_process_version must be updated").isEqualTo("2.0");
        }

        // The migrated child's in-memory instance must reflect the updated root columns.
        AbstractProcessInstance<?> migratedChild = (AbstractProcessInstance<?>) userTaskProcess.instances()
                .findById(childId1).get();
        migratedChild.executeInWorkflowProcessInstanceRead(pi -> {
            assertThat(pi.getRootProcessId())
                    .as("unmarshalled migrated child rootProcessId must be overridden from DB column")
                    .isEqualTo("BPMN2_CallActivity_v2");
            assertThat(pi.getRootProcessVersion())
                    .as("unmarshalled migrated child rootProcessVersion must be overridden from DB column")
                    .isEqualTo("2.0");
            return null;
        });

        // The child of the NOT-migrated parent must be left intact (root_process_version = null — process has no version).
        try (Connection connection = getDataSource().getConnection();
                ResultSet rs = connection.createStatement().executeQuery(
                        "SELECT root_process_id, root_process_version FROM process_instances WHERE id = '" + childId2 + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).as("untouched child root_process_id must remain unchanged").isEqualTo("BPMN2_CallActivity");
            assertThat(rs.getString(2)).as("untouched child root_process_version must remain unchanged").isNull();
        }
    }

    @Test
    public void testMigrateRootCascadeWithExplicitListVersioned() throws Exception {
        // Same as testMigrateRootCascadeWithExplicitList but uses the versioned call-activity
        // (drools:version="1.0") so that root_process_version is "1.0" rather than null.
        Map<String, BpmnProcess> processes = createProcesses(getDataSource(), lock(),
                "BPMN2-CallActivity-v1.bpmn2", "BPMN2-CallActivity-v2.bpmn2", "BPMN2-UserTask.bpmn2");
        BpmnProcess callActivityProcess = processes.get("BPMN2_CallActivity");
        BpmnProcess userTaskProcess = processes.get("BPMN2_UserTask");

        // Start TWO call-activity instances – each spawns a BPMN2_UserTask child.
        ProcessInstance<BpmnVariables> parentInstance1 = callActivityProcess.createInstance(
                BpmnVariables.create(singletonMap("test", "cascade-explicit-versioned-1")));
        parentInstance1.start();

        ProcessInstance<BpmnVariables> parentInstance2 = callActivityProcess.createInstance(
                BpmnVariables.create(singletonMap("test", "cascade-explicit-versioned-2")));
        parentInstance2.start();

        // Sanity: both children must exist.
        assertThat(userTaskProcess.instances().stream().count())
                .as("two subprocesses should have been created").isGreaterThanOrEqualTo(2);

        // Resolve the child ids by their parent root_process_instance_id.
        String childId1;
        String childId2;
        try (Connection connection = getDataSource().getConnection();
                ResultSet rs = connection.createStatement().executeQuery(
                        "SELECT id, root_process_instance_id FROM process_instances"
                                + " WHERE process_id = 'BPMN2_UserTask' AND root_process_id = 'BPMN2_CallActivity'")) {
            assertThat(rs.next()).as("first child row should exist").isTrue();
            String firstChildId = rs.getString(1);
            String firstRootInstanceId = rs.getString(2);

            assertThat(rs.next()).as("second child row should exist").isTrue();
            String secondChildId = rs.getString(1);
            String secondRootInstanceId = rs.getString(2);

            // Map child IDs back to parentInstance1 / parentInstance2.
            if (firstRootInstanceId.equals(parentInstance1.id())) {
                childId1 = firstChildId;
                childId2 = secondChildId;
            } else {
                childId1 = secondChildId;
                childId2 = firstChildId;
            }
        }

        // Migrate ONLY parentInstance1 using the explicit-list overload.
        callActivityProcess.instances().migrateProcessInstances("BPMN2_CallActivity_v2", "2.0", parentInstance1.id());

        // The child of the migrated parent must have its root columns updated.
        try (Connection connection = getDataSource().getConnection();
                ResultSet rs = connection.createStatement().executeQuery(
                        "SELECT root_process_id, root_process_version FROM process_instances WHERE id = '" + childId1 + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).as("migrated child root_process_id must be updated").isEqualTo("BPMN2_CallActivity_v2");
            assertThat(rs.getString(2)).as("migrated child root_process_version must be updated").isEqualTo("2.0");
        }

        // The migrated child's in-memory instance must reflect the updated root columns.
        AbstractProcessInstance<?> migratedChild = (AbstractProcessInstance<?>) userTaskProcess.instances()
                .findById(childId1).get();
        migratedChild.executeInWorkflowProcessInstanceRead(pi -> {
            assertThat(pi.getRootProcessId())
                    .as("unmarshalled migrated child rootProcessId must be overridden from DB column")
                    .isEqualTo("BPMN2_CallActivity_v2");
            assertThat(pi.getRootProcessVersion())
                    .as("unmarshalled migrated child rootProcessVersion must be overridden from DB column")
                    .isEqualTo("2.0");
            return null;
        });

        // The child of the NOT-migrated parent must be left intact (root_process_version = "1.0").
        try (Connection connection = getDataSource().getConnection();
                ResultSet rs = connection.createStatement().executeQuery(
                        "SELECT root_process_id, root_process_version FROM process_instances WHERE id = '" + childId2 + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).as("untouched child root_process_id must remain unchanged").isEqualTo("BPMN2_CallActivity");
            assertThat(rs.getString(2)).as("untouched child root_process_version must remain unchanged").isEqualTo("1.0");
        }
    }

    @Test
    public void testSignalStorage() {
        BpmnProcess process = createProcess(getDataSource(), lock(), "BPMN2-IntermediateCatchEventSignal.bpmn2");
        JDBCProcessInstances fsInstances = (JDBCProcessInstances) process.instances();
        ProcessInstance<BpmnVariables> pi1 = process.createInstance(BpmnVariables.create(Collections.singletonMap("name", "sig1")));
        ProcessInstance<BpmnVariables> pi2 = process.createInstance(BpmnVariables.create(Collections.singletonMap("name", "sig2")));
        pi1.start();
        pi2.start();

        pi1.workItems().forEach(wi -> pi1.completeWorkItem(wi.getId(), Collections.emptyMap()));
        pi2.workItems().forEach(wi -> pi2.completeWorkItem(wi.getId(), Collections.emptyMap()));
        process.send(SignalFactory.of("sig1", "SomeValue"));
        process.send(SignalFactory.of("sig2", "SomeValue"));
        assertThat(process.instances().stream().count()).isEqualTo(0);
    }
}
