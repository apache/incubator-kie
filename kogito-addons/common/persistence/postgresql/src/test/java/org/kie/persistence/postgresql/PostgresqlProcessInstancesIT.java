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
package org.kie.persistence.postgresql;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.flyway.initializer.KieFlywayInitializer;
import org.kie.kogito.Application;
import org.kie.kogito.Model;
import org.kie.kogito.auth.IdentityProviders;
import org.kie.kogito.auth.SecurityPolicy;
import org.kie.kogito.internal.process.runtime.HeadersPersistentConfig;
import org.kie.kogito.persistence.postgresql.AbstractProcessInstancesFactory;
import org.kie.kogito.persistence.postgresql.PostgresqlProcessInstances;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstanceReadMode;
import org.kie.kogito.process.SignalFactory;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.process.bpmn2.BpmnProcess;
import org.kie.kogito.process.bpmn2.BpmnProcessInstance;
import org.kie.kogito.process.bpmn2.BpmnVariables;
import org.kie.kogito.process.bpmn2.StaticApplicationAssembler;
import org.kie.kogito.process.impl.AbstractProcessInstance;
import org.kie.kogito.process.impl.StaticProcessConfig;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;
import org.kie.kogito.testcontainers.KogitoPostgreSqlContainer;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.internal.process.runtime.KogitoProcessInstance.STATE_ACTIVE;
import static org.kie.kogito.internal.process.runtime.KogitoProcessInstance.STATE_COMPLETED;
import static org.kie.kogito.test.utils.ProcessInstancesTestUtils.abort;
import static org.kie.kogito.test.utils.ProcessInstancesTestUtils.assertEmpty;
import static org.kie.kogito.test.utils.ProcessInstancesTestUtils.assertOne;
import static org.kie.kogito.test.utils.ProcessInstancesTestUtils.getFirst;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers
class PostgresqlProcessInstancesIT {

    @Container
    final static KogitoPostgreSqlContainer container = new KogitoPostgreSqlContainer();

    private static PgPool client;
    private SecurityPolicy securityPolicy = SecurityPolicy.of(IdentityProviders.of("john"));

    @BeforeAll
    public static void startContainerAndPublicPortIsAvailable() {
        container.start();

        client = client();

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(container.getJdbcUrl());
        ds.setUser(container.getUsername());
        ds.setPassword(container.getPassword());

        KieFlywayInitializer.builder()
                .withDatasource(ds)
                .build()
                .migrate();
    }

    @AfterAll
    public static void close() {
        container.stop();
    }

    boolean lock() {
        return false;
    }

    private BpmnProcess createProcess(String fileName) {
        StaticProcessConfig processConfig = StaticProcessConfig.newStaticProcessConfigBuilder()
                .withWorkItemHandler("Human Task", new DefaultKogitoWorkItemHandler())
                .build();

        Application application =
                StaticApplicationAssembler.instance().newStaticApplication(new PostgreProcessInstancesFactory(client, lock(), new HeadersPersistentConfig(true, null)), processConfig, fileName);

        org.kie.kogito.process.Processes container = application.get(org.kie.kogito.process.Processes.class);
        String processId = container.processIds().stream().findFirst().get();
        org.kie.kogito.process.Process<? extends Model> process = container.processById(processId);

        abort(process.instances());
        BpmnProcess compiledProcess = (BpmnProcess) process;
        return compiledProcess;
    }

    private static PgPool client() {
        return PgPool.pool(container.getReactiveUrl());
    }

    @Test
    void testBasicFlow() {
        BpmnProcess process = createProcess("BPMN2-UserTask.bpmn2");
        ProcessInstance<BpmnVariables> processInstance = process.createInstance(BpmnVariables.create(Collections.singletonMap("test", "test")));

        Map<String, List<String>> headers = Map.of("name", List.of("pepe"));
        processInstance.start(headers);

        assertThat(processInstance.status()).isEqualTo(STATE_ACTIVE);
        assertThat(processInstance.description()).isEqualTo("BPMN2-UserTask");

        PostgresqlProcessInstances processInstances = (PostgresqlProcessInstances) process.instances();
        assertOne(processInstances);
        assertThat(processInstances.exists(processInstance.id())).isTrue();

        ProcessInstance<?> readOnlyPI = process.instances().findById(processInstance.id(), ProcessInstanceReadMode.READ_ONLY).get();
        assertThat(readOnlyPI.status()).isEqualTo(STATE_ACTIVE);
        assertThat(((AbstractProcessInstance) readOnlyPI).hasHeader("name")).isTrue();
        assertOne(processInstances);

        verify(processInstances).create(any(), any());

        String testVar = (String) processInstance.variables().get("test");
        assertThat(testVar).isEqualTo("test");

        assertThat(processInstance.description()).isEqualTo("BPMN2-UserTask");

        assertThat(getFirst(process.instances()).workItems(securityPolicy)).hasSize(1);

        WorkItem workItem = processInstance.workItems(securityPolicy).get(0);
        assertThat(workItem).isNotNull();
        assertThat(workItem.getParameters()).containsEntry("ActorId", "john");
        processInstance.completeWorkItem(workItem.getId(), null, securityPolicy);
        assertThat(processInstance.status()).isEqualTo(STATE_COMPLETED);

        processInstances = (PostgresqlProcessInstances) process.instances();
        verify(processInstances, times(1)).remove(processInstance.id());

        assertEmpty(process.instances());
    }

    @Test
    void testMultipleProcesses() {
        BpmnProcess utProcess = createProcess("BPMN2-UserTask.bpmn2");
        ProcessInstance<BpmnVariables> utProcessInstance = utProcess.createInstance(BpmnVariables.create());
        utProcessInstance.start();

        BpmnProcess scriptProcess = createProcess("BPMN2-UserTask-Script.bpmn2");
        ProcessInstance<BpmnVariables> scriptProcessInstance = scriptProcess.createInstance(BpmnVariables.create());
        scriptProcessInstance.start();

        //Try to remove process instance from another process id
        ((PostgresqlProcessInstances) utProcess.instances()).remove(scriptProcessInstance.id());
        ((PostgresqlProcessInstances) scriptProcess.instances()).remove(utProcessInstance.id());

        assertOne(utProcess.instances());
        assertThat(utProcess.instances().findById(utProcessInstance.id())).isPresent();
        assertThat(utProcess.instances().findById(scriptProcessInstance.id())).isEmpty();

        assertOne(scriptProcess.instances());
        assertThat(scriptProcess.instances().findById(scriptProcessInstance.id())).isPresent();
        assertThat(scriptProcess.instances().findById(utProcessInstance.id())).isEmpty();

        ((PostgresqlProcessInstances) utProcess.instances()).remove(utProcessInstance.id());
        assertEmpty(utProcess.instances());
        assertThat(utProcess.instances().findById(utProcessInstance.id())).isEmpty();
        assertThat(utProcess.instances().findById(scriptProcessInstance.id())).isEmpty();

        ((PostgresqlProcessInstances) scriptProcess.instances()).remove(scriptProcessInstance.id());
        assertEmpty(scriptProcess.instances());
        assertThat(scriptProcess.instances().findById(scriptProcessInstance.id())).isEmpty();
        assertThat(scriptProcess.instances().findById(utProcessInstance.id())).isEmpty();
    }

    @Test
    public void testUpdate() {
        BpmnProcess process = createProcess("BPMN2-UserTask.bpmn2");
        ProcessInstance<BpmnVariables> processInstance = process.createInstance(BpmnVariables.create(Collections.singletonMap("test", "test")));
        processInstance.start();

        PostgresqlProcessInstances processInstances = (PostgresqlProcessInstances) process.instances();
        BpmnProcessInstance instanceOne = (BpmnProcessInstance) processInstances.findById(processInstance.id()).get();
        BpmnProcessInstance instanceTwo = (BpmnProcessInstance) processInstances.findById(processInstance.id()).get();
        assertThat(instanceOne.version()).isEqualTo(lock() ? 1L : 0);
        assertThat(instanceTwo.version()).isEqualTo(lock() ? 1L : 0);
        instanceOne.updateVariables(BpmnVariables.create(Collections.singletonMap("s", "test")));
        instanceOne = (BpmnProcessInstance) processInstances.findById(processInstance.id()).get();
        assertThat(instanceOne.version()).isEqualTo(lock() ? 2L : 0);

        processInstances.remove(processInstance.id());
        assertEmpty(process.instances());

    }

    @Test
    public void testMigrateAll() throws Exception {
        BpmnProcess process = createProcess("BPMN2-UserTask.bpmn2");
        ProcessInstance<BpmnVariables> processInstance1 = process.createInstance(BpmnVariables.create(Collections.singletonMap("test", "test")));
        processInstance1.start();

        ProcessInstance<BpmnVariables> processInstance2 = process.createInstance(BpmnVariables.create(Collections.singletonMap("test", "test")));
        processInstance2.start();

        process.instances().migrateAll("migrated", "2");
        RowSet<Row> rows = client.preparedQuery("SELECT process_id, process_version FROM process_instances").execute().toCompletionStage().toCompletableFuture().get();
        for (Row row : rows) {
            assertThat(row.get(String.class, 0)).isEqualTo("migrated");
            assertThat(row.get(String.class, 1)).isEqualTo("2");
        }
    }

    @Test
    public void testMigrateSingle() throws Exception {
        BpmnProcess process = createProcess("BPMN2-UserTask.bpmn2");
        ProcessInstance<BpmnVariables> processInstance1 = process.createInstance(BpmnVariables.create(Collections.singletonMap("test", "test")));
        processInstance1.start();

        ProcessInstance<BpmnVariables> processInstance2 = process.createInstance(BpmnVariables.create(Collections.singletonMap("test", "test")));
        processInstance2.start();

        process.instances().migrateProcessInstances("migrated", "2", processInstance1.id());
        RowSet<Row> rows = null;
        rows = client.preparedQuery("SELECT process_id, process_version FROM process_instances WHERE id = $1").execute(Tuple.of(processInstance1.id())).toCompletionStage().toCompletableFuture().get();
        for (Row row : rows) {
            assertThat(row.get(String.class, 0)).isEqualTo("migrated");
            assertThat(row.get(String.class, 1)).isEqualTo("2");
        }
        rows = client.preparedQuery("SELECT process_id, process_version FROM process_instances WHERE id = $1").execute(Tuple.of(processInstance2.id())).toCompletionStage().toCompletableFuture().get();
        for (Row row : rows) {
            assertThat(row.get(String.class, 0)).isEqualTo("BPMN2_UserTask");
            assertThat(row.get(String.class, 1)).isEqualTo("1.0");
        }
    }

    @Test
    public void testRemove() {
        BpmnProcess process = createProcess("BPMN2-UserTask.bpmn2");
        ProcessInstance<BpmnVariables> processInstance = process.createInstance(BpmnVariables.create(Collections.singletonMap("test", "test")));
        processInstance.start();

        PostgresqlProcessInstances processInstances = (PostgresqlProcessInstances) process.instances();
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
        BpmnProcess processV1 = createProcess("BPMN2-UserTask.bpmn2");
        BpmnProcess processV2 = createProcess("BPMN2-UserTask-v2.bpmn2");

        assertThat(processV1.process().getVersion()).isEqualTo("1.0");
        assertThat(processV2.process().getVersion()).isEqualTo("2.0");

        ProcessInstance<BpmnVariables> processInstanceV1 = processV1.createInstance(BpmnVariables.create(singletonMap("test", "test")));
        processInstanceV1.start();

        PostgresqlProcessInstances processInstancesV1 = (PostgresqlProcessInstances) processV1.instances();
        PostgresqlProcessInstances processInstancesV2 = (PostgresqlProcessInstances) processV2.instances();

        assertThat(processInstancesV1.findById(processInstanceV1.id())).isPresent();

        assertEmpty(processInstancesV2);
        ProcessInstance<BpmnVariables> processInstanceV2 = processV2.createInstance(BpmnVariables.create(singletonMap("test", "test")));
        processInstanceV2.start();
        assertThat(processInstancesV2.findById(processInstanceV2.id())).isPresent();

        processInstancesV1.remove(processInstanceV1.id());
        assertEmpty(processV1.instances());

        processInstancesV2.remove(processInstanceV2.id());
        assertEmpty(processInstancesV2);
    }

    @Test
    public void testSignalStorage() {
        BpmnProcess process = createProcess("BPMN2-IntermediateCatchEventSignal.bpmn2");
        PostgresqlProcessInstances fsInstances = (PostgresqlProcessInstances) process.instances();
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

    private class PostgreProcessInstancesFactory extends AbstractProcessInstancesFactory {

        public PostgreProcessInstancesFactory(PgPool client, boolean lock, HeadersPersistentConfig headersConfig) {
            super(client, 10000l, lock, headersConfig);
        }

        @Override
        public PostgresqlProcessInstances createProcessInstances(Process<?> process) {
            return spy(super.createProcessInstances(process));
        }

    }
}
