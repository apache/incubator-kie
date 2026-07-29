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
package org.kie.kogito.app.audit.quarkus;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.kie.kogito.app.audit.api.SubsystemConstants;
import org.kie.kogito.jobs.service.model.JobStatus;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newJobEvent;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newJobEventWithVersion;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.wrapQuery;

@TestInstance(Lifecycle.PER_CLASS)
public abstract class AbstractDataAuditJobIsolationIT {

    protected static final String ALLOWED_PROCESS_ID = "itJobProcess";
    protected static final String ALLOWED_PROCESS_VERSION = "1.0";

    protected abstract void publishTestData() throws Exception;

    protected abstract RequestSpecification request();

    @Test
    public void testGetAllScheduledJobs_foreignProcessInvisible() {
        assertThat(queryJobs("GetAllScheduledJobs"))
                .extracting(e -> e.get("jobId"))
                .doesNotContain("iso-job2");
    }

    @Test
    public void testGetAllJobs_foreignProcessInvisible() {
        assertThat(queryJobs("GetAllJobs"))
                .extracting(e -> e.get("jobId"))
                .doesNotContain("iso-job2");
    }

    @Test
    public void testGetAllPendingJobs_foreignProcessInvisible() {
        assertThat(queryJobs("GetAllPendingJobs"))
                .extracting(e -> e.get("jobId"))
                .doesNotContain("iso-job2");
    }

    @Test
    public void testGetAllJobs_subProcessVisibleViaRootProcessId() {
        assertThat(queryJobs("GetAllJobs"))
                .extracting(e -> e.get("jobId"))
                .contains("iso-job3");
    }

    @Test
    public void testGetAllScheduledJobs_ownAndSubProcessVisible() {
        assertThat(queryJobs("GetAllScheduledJobs"))
                .extracting(e -> e.get("jobId"))
                .contains("iso-job1", "iso-job3");
    }

    @Test
    public void testGetAllJobs_correctVersionVisible() {
        assertThat(queryJobs("GetAllJobs"))
                .extracting(e -> e.get("jobId"))
                .contains("iso-job4");
    }

    @Test
    public void testGetAllJobs_wrongVersionInvisible() {
        assertThat(queryJobs("GetAllJobs"))
                .extracting(e -> e.get("jobId"))
                .doesNotContain("iso-job5");
    }

    @Test
    public void testGetAllScheduledJobs_wrongVersionInvisible() {
        assertThat(queryJobs("GetAllScheduledJobs"))
                .extracting(e -> e.get("jobId"))
                .doesNotContain("iso-job5");
    }

    protected void doPublishJobEvents(org.kie.kogito.event.EventPublisher publisher) throws Exception {
        publisher.publish(newJobEvent(
                "iso-job1", "node1", 1,
                ALLOWED_PROCESS_ID, "piId1", 100L, 10,
                null, null,
                JobStatus.SCHEDULED, 0));

        publisher.publish(newJobEvent(
                "iso-job2", "node1", 1,
                "foreignProcess", "piId2", 100L, 10,
                null, null,
                JobStatus.SCHEDULED, 0));

        publisher.publish(newJobEvent(
                "iso-job3", "node1", 1,
                "child-process", "piId3", 100L, 10,
                ALLOWED_PROCESS_ID, "piId1",
                JobStatus.SCHEDULED, 0));

        publisher.publish(newJobEventWithVersion(
                "iso-job4", "node1", 1,
                ALLOWED_PROCESS_ID, ALLOWED_PROCESS_VERSION, "piId4", 100L, 10,
                null, null, null,
                JobStatus.SCHEDULED, 0));

        publisher.publish(newJobEventWithVersion(
                "iso-job5", "node1", 1,
                ALLOWED_PROCESS_ID, "2.0", "piId5", 100L, 10,
                null, null, null,
                JobStatus.SCHEDULED, 0));
    }

    private List<Map<String, Object>> queryJobs(String queryName) {
        return request()
                .contentType(ContentType.JSON)
                .body(wrapQuery("{ " + queryName + " { jobId, status, processInstanceId } }"))
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .statusCode(200)
                .extract()
                .path("data." + queryName);
    }
}
