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

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newUserTaskInstanceStateEventWithProcessId;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.wrapQuery;

@TestInstance(Lifecycle.PER_CLASS)
public abstract class AbstractDataAuditUserTaskIsolationIT {

    protected static final String ALLOWED_PROCESS_ID = "itUserTaskProcess";
    protected static final String ALLOWED_PROCESS_VERSION = "1.0";
    protected static final String OTHER_PROCESS_ID = "itOtherUserTaskProcess";

    protected abstract void publishTestData() throws Exception;

    protected abstract RequestSpecification request();

    @Test
    public void testGetAllUserTaskInstanceState_foreignProcessIdInvisible() {
        assertThat(queryUserTaskInstances())
                .extracting(r -> r.get("userTaskInstanceId"))
                .doesNotContain("iso-ut-2");
    }

    @Test
    public void testGetAllUserTaskInstanceState_allowedProcessIdVisible() {
        assertThat(queryUserTaskInstances())
                .extracting(r -> r.get("userTaskInstanceId"))
                .contains("iso-ut-1");
    }

    @Test
    public void testGetAllUserTaskInstanceState_wrongVersionInvisible() {
        assertThat(queryUserTaskInstances())
                .extracting(r -> r.get("userTaskInstanceId"))
                .doesNotContain("iso-ut-3");
    }

    @Test
    public void testGetAllUserTaskInstanceState_correctVersionVisible() {
        assertThat(queryUserTaskInstances())
                .extracting(r -> r.get("userTaskInstanceId"))
                .contains("iso-ut-4");
    }

    @Test
    public void testGetAllUserTaskInstanceState_nullVersionVisible() {
        assertThat(queryUserTaskInstances())
                .extracting(r -> r.get("userTaskInstanceId"))
                .contains("iso-ut-1");
    }

    protected void doPublishUserTaskEvents(org.kie.kogito.event.EventPublisher publisher) throws Exception {
        // allowed process, no version (null version is always visible)
        publisher.publish(newUserTaskInstanceStateEventWithProcessId(
                ALLOWED_PROCESS_ID, null, "testUser", "taskDef1", "iso-ut-1",
                "Task 1", "ACTIVATE", "Ready", null, "pi-1"));

        // foreign process - must be invisible
        publisher.publish(newUserTaskInstanceStateEventWithProcessId(
                OTHER_PROCESS_ID, null, "testUser", "taskDef2", "iso-ut-2",
                "Task 2", "ACTIVATE", "Ready", null, "pi-2"));

        // allowed process, wrong version - must be invisible
        publisher.publish(newUserTaskInstanceStateEventWithProcessId(
                ALLOWED_PROCESS_ID, "9.9", "testUser", "taskDef3", "iso-ut-3",
                "Task 3", "ACTIVATE", "Ready", null, "pi-3"));

        // allowed process, correct version - must be visible
        publisher.publish(newUserTaskInstanceStateEventWithProcessId(
                ALLOWED_PROCESS_ID, ALLOWED_PROCESS_VERSION, "testUser", "taskDef4", "iso-ut-4",
                "Task 4", "ACTIVATE", "Ready", null, "pi-4"));
    }

    private List<Map<String, Object>> queryUserTaskInstances() {
        return request()
                .contentType(ContentType.JSON)
                .body(wrapQuery("{ GetAllUserTaskInstanceState { userTaskInstanceId } }"))
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .statusCode(200)
                .extract()
                .path("data.GetAllUserTaskInstanceState");
    }
}
