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
package org.kie.kogito.app.audit.jpa.queries;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kie.kogito.app.audit.jpa.model.JobExecutionLog;
import org.kie.kogito.app.audit.jpa.model.ProcessInstanceStateLog;
import org.kie.kogito.app.audit.jpa.model.ProcessInstanceStateLog.ProcessStateLogType;
import org.kie.kogito.process.Processes;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JPAAbstractQueryDataIsolationTest {

    static class TestQuery extends JPAAbstractQuery<Object> {
        List<Object[]> runIsolated(EntityManager em, String queryName,
                Map<String, Object> args, Optional<Processes> processes,
                String pidCol, String pvCol, String rpidCol, String rpvCol) {
            return executeIsolated(em, queryName, args, processes, pidCol, pvCol, rpidCol, rpvCol);
        }

        List<Object[]> runPlain(EntityManager em, String queryName) {
            return executeWithNamedQueryEntityManagerAndArguments(em, queryName, Collections.emptyMap());
        }
    }

    private EntityManagerFactory emf;
    private EntityManager em;

    @BeforeAll
    void setup() throws Exception {
        emf = Persistence.createEntityManagerFactory("data-audit-test");
        em = emf.createEntityManager();
    }

    @AfterAll
    void teardown() {
        if (em != null && em.isOpen()) {
            em.close();
        }
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    @SuppressWarnings("unchecked")
    private Processes mockProcesses(String pid, String version) {
        org.kie.kogito.process.Process<? extends org.kie.kogito.Model> p =
                mock(org.kie.kogito.process.Process.class);
        when(p.id()).thenReturn(pid);
        when(p.version()).thenReturn(version);
        Processes processes = mock(Processes.class);
        when(processes.processes()).thenReturn(List.of(p));
        return processes;
    }

    private ProcessInstanceStateLog persistProcessInstance(String processId, String processVersion,
            String rootProcessId, String rootProcessVersion) {
        ProcessInstanceStateLog log = new ProcessInstanceStateLog();
        log.setEventId(UUID.randomUUID().toString());
        log.setEventDate(new Timestamp(System.currentTimeMillis()));
        log.setProcessId(processId);
        log.setProcessVersion(processVersion);
        log.setProcessInstanceId(UUID.randomUUID().toString());
        log.setProcessType("BPMN2");
        log.setRootProcessId(rootProcessId);
        log.setRootProcessVersion(rootProcessVersion);
        log.setRootProcessInstanceId(rootProcessId != null ? UUID.randomUUID().toString() : null);
        log.setEventType(ProcessStateLogType.ACTIVE);
        log.setBusinessKey("bk-" + processId);
        log.setRoles(Collections.emptySet());
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        em.persist(log);
        tx.commit();
        return log;
    }

    private JobExecutionLog persistJob(String jobId, String processId, String processVersion,
            String rootProcessId, String rootProcessVersion) {
        JobExecutionLog log = new JobExecutionLog();
        log.setJobId(jobId);
        log.setProcessInstanceId(UUID.randomUUID().toString());
        log.setStatus("SCHEDULED");
        log.setProcessId(processId);
        log.setProcessVersion(processVersion);
        log.setRootProcessId(rootProcessId);
        log.setRootProcessVersion(rootProcessVersion);
        log.setEventDate(new Timestamp(System.currentTimeMillis()));
        log.setPriority(1);
        log.setRetries(0);
        log.setExecutionCounter(0);
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        em.persist(log);
        tx.commit();
        return log;
    }

    private void clearTables() {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        em.createNativeQuery("DELETE FROM Process_Instance_State_Roles_Log").executeUpdate();
        em.createNativeQuery("DELETE FROM Process_Instance_State_Log").executeUpdate();
        em.createNativeQuery("DELETE FROM Job_Execution_Log").executeUpdate();
        tx.commit();
        em.clear();
    }

    static final String PI_PID_COL = "log.process_id";
    static final String PI_PV_COL = "log.process_version";
    static final String PI_RPID_COL = "log.root_process_id";
    static final String PI_RPV_COL = "log.root_process_version";

    /**
     * With no Processes present (isolation disabled), all rows from any service
     * and any version are returned.
     */
    @Test
    void testProcessInstance_noIsolation_allRowsVisible() {
        clearTables();
        persistProcessInstance("serviceA", "1.0", null, null);
        persistProcessInstance("serviceB", "2.0", null, null);
        persistProcessInstance("serviceA", "99.0", null, null); // wrong version — still visible

        TestQuery q = new TestQuery();
        List<Object[]> results = q.runPlain(em, "GetAllProcessInstancesState");

        assertThat(results).hasSize(3);
    }

    /**
     * With isolation enabled for serviceA/1.0, only that service+version is returned.
     * serviceB and the wrong version of serviceA are both invisible.
     */
    @Test
    void testProcessInstance_isolationEnabled_onlyMatchingServiceVisible() {
        clearTables();
        persistProcessInstance("serviceA", "1.0", null, null); // visible
        persistProcessInstance("serviceB", "2.0", null, null); // foreign service — invisible
        persistProcessInstance("serviceA", "99.0", null, null); // wrong version — invisible

        Processes processes = mockProcesses("serviceA", "1.0");

        TestQuery q = new TestQuery();
        List<Object[]> results = q.runIsolated(em, "GetAllProcessInstancesState",
                Collections.emptyMap(), Optional.of(processes),
                PI_PID_COL, PI_PV_COL, PI_RPID_COL, PI_RPV_COL);

        assertThat(results).hasSize(1);
        assertThat(results).allSatisfy(row -> assertThat(row[3]).isEqualTo("serviceA"));
    }

    /**
     * A subprocess whose root_process_id belongs to the allowed service is visible
     * even though its own process_id is a child process unknown to Processes.
     */
    @Test
    void testProcessInstance_subProcess_visibleViaRootProcessId() {
        clearTables();
        persistProcessInstance("serviceA", "1.0", null, null); // parent — visible
        // child: own process_id is "child-sub", but root is serviceA/1.0 → must be visible
        persistProcessInstance("child-sub", null, "serviceA", "1.0");
        persistProcessInstance("serviceB", "2.0", null, null); // foreign — invisible

        Processes processes = mockProcesses("serviceA", "1.0");

        TestQuery q = new TestQuery();
        List<Object[]> results = q.runIsolated(em, "GetAllProcessInstancesState",
                Collections.emptyMap(), Optional.of(processes),
                PI_PID_COL, PI_PV_COL, PI_RPID_COL, PI_RPV_COL);

        assertThat(results).hasSize(2);
        List<Object> pids = results.stream().map(r -> r[3]).toList();
        assertThat(pids).contains("serviceA", "child-sub");
        assertThat(pids).doesNotContain("serviceB");
    }

    /**
     * With isolation enabled for two services, both are returned and neither
     * leaks into the other's results.
     */
    @Test
    void testProcessInstance_twoAllowedServices_bothVisible() {
        clearTables();
        persistProcessInstance("serviceA", "1.0", null, null);
        persistProcessInstance("serviceB", "2.0", null, null);
        persistProcessInstance("serviceC", "3.0", null, null); // third — not allowed

        @SuppressWarnings("unchecked")
        org.kie.kogito.process.Process<? extends org.kie.kogito.Model> pA = mock(org.kie.kogito.process.Process.class);
        when(pA.id()).thenReturn("serviceA");
        when(pA.version()).thenReturn("1.0");
        @SuppressWarnings("unchecked")
        org.kie.kogito.process.Process<? extends org.kie.kogito.Model> pB = mock(org.kie.kogito.process.Process.class);
        when(pB.id()).thenReturn("serviceB");
        when(pB.version()).thenReturn("2.0");
        Processes processes = mock(Processes.class);
        when(processes.processes()).thenReturn(List.of(pA, pB));

        TestQuery q = new TestQuery();
        List<Object[]> results = q.runIsolated(em, "GetAllProcessInstancesState",
                Collections.emptyMap(), Optional.of(processes),
                PI_PID_COL, PI_PV_COL, PI_RPID_COL, PI_RPV_COL);

        assertThat(results).hasSize(2);
        List<Object> pids = results.stream().map(r -> r[3]).toList();
        assertThat(pids).containsExactlyInAnyOrder("serviceA", "serviceB");
        assertThat(pids).doesNotContain("serviceC");
    }

    /**
     * A row with a NULL version is always included regardless of the allowed version.
     * This covers legacy rows written before migration (permissive filter design).
     */
    @Test
    void testProcessInstance_nullVersion_permissivelyVisible() {
        clearTables();
        persistProcessInstance("serviceA", null, null, null); // null version → permissive
        persistProcessInstance("serviceA", "1.0", null, null); // correct version
        persistProcessInstance("serviceA", "99.0", null, null); // wrong version

        Processes processes = mockProcesses("serviceA", "1.0");

        TestQuery q = new TestQuery();
        List<Object[]> results = q.runIsolated(em, "GetAllProcessInstancesState",
                Collections.emptyMap(), Optional.of(processes),
                PI_PID_COL, PI_PV_COL, PI_RPID_COL, PI_RPV_COL);

        // null-version row + correct-version row = 2; wrong version excluded
        assertThat(results).hasSize(2);
        assertThat(results).noneMatch(row -> "99.0".equals(row[4]));
    }

    static final String JOB_PID_COL = "o1.process_id";
    static final String JOB_PV_COL = "o1.process_version";
    static final String JOB_RPID_COL = "o1.root_process_id";
    static final String JOB_RPV_COL = "o1.root_process_version";

    /**
     * With no Processes (isolation disabled), all jobs across all services
     * and versions are returned.
     */
    @Test
    void testJob_noIsolation_allJobsVisible() {
        clearTables();
        persistJob("j1", "serviceA", "1.0", null, null);
        persistJob("j2", "serviceB", "2.0", null, null);
        persistJob("j3", "serviceA", "99.0", null, null);

        TestQuery q = new TestQuery();
        List<Object[]> results = q.runPlain(em, "GetAllJobs");

        assertThat(results).hasSize(3);
    }

    /**
     * With isolation enabled for serviceA/1.0, jobs for serviceB and the
     * wrong version of serviceA are both invisible.
     */
    @Test
    void testJob_isolationEnabled_onlyMatchingServiceVisible() {
        clearTables();
        persistJob("j1", "serviceA", "1.0", null, null); // visible
        persistJob("j2", "serviceB", "2.0", null, null); // foreign — invisible
        persistJob("j3", "serviceA", "99.0", null, null); // wrong version — invisible

        Processes processes = mockProcesses("serviceA", "1.0");

        TestQuery q = new TestQuery();
        List<Object[]> results = q.runIsolated(em, "GetAllJobs",
                Collections.emptyMap(), Optional.of(processes),
                JOB_PID_COL, JOB_PV_COL, JOB_RPID_COL, JOB_RPV_COL);

        assertThat(results).hasSize(1);
        // column index 0 = job_id in GetAllJobs SELECT; assert via jobId
        assertThat(results).allSatisfy(row -> assertThat(row[0]).isEqualTo("j1"));
    }

    /**
     * A job for a subprocess rooted at the allowed service is visible
     * even when its own process_id is not in the allowed set.
     */
    @Test
    void testJob_subProcess_visibleViaRootProcessId() {
        clearTables();
        persistJob("j1", "serviceA", "1.0", null, null); // own job — visible
        persistJob("j2", "child-sub", null, "serviceA", "1.0"); // child of serviceA — visible
        persistJob("j3", "serviceB", "2.0", null, null); // foreign — invisible

        Processes processes = mockProcesses("serviceA", "1.0");

        TestQuery q = new TestQuery();
        List<Object[]> results = q.runIsolated(em, "GetAllJobs",
                Collections.emptyMap(), Optional.of(processes),
                JOB_PID_COL, JOB_PV_COL, JOB_RPID_COL, JOB_RPV_COL);

        assertThat(results).hasSize(2);
        // column 0 = job_id; verify by job id
        List<Object> jobIds = results.stream().map(r -> r[0]).toList();
        assertThat(jobIds).containsExactlyInAnyOrder("j1", "j2");
    }

    /**
     * With two allowed services, jobs from both are returned and the third
     * service's jobs are invisible.
     */
    @Test
    void testJob_twoAllowedServices_bothVisible() {
        clearTables();
        persistJob("j1", "serviceA", "1.0", null, null);
        persistJob("j2", "serviceB", "2.0", null, null);
        persistJob("j3", "serviceC", "3.0", null, null); // not allowed

        @SuppressWarnings("unchecked")
        org.kie.kogito.process.Process<? extends org.kie.kogito.Model> pA = mock(org.kie.kogito.process.Process.class);
        when(pA.id()).thenReturn("serviceA");
        when(pA.version()).thenReturn("1.0");
        @SuppressWarnings("unchecked")
        org.kie.kogito.process.Process<? extends org.kie.kogito.Model> pB = mock(org.kie.kogito.process.Process.class);
        when(pB.id()).thenReturn("serviceB");
        when(pB.version()).thenReturn("2.0");
        Processes processes = mock(Processes.class);
        when(processes.processes()).thenReturn(List.of(pA, pB));

        TestQuery q = new TestQuery();
        List<Object[]> results = q.runIsolated(em, "GetAllJobs",
                Collections.emptyMap(), Optional.of(processes),
                JOB_PID_COL, JOB_PV_COL, JOB_RPID_COL, JOB_RPV_COL);

        assertThat(results).hasSize(2);
        // column 0 = job_id; verify by job id
        List<Object> jobIds = results.stream().map(r -> r[0]).toList();
        assertThat(jobIds).containsExactlyInAnyOrder("j1", "j2");
    }
}
