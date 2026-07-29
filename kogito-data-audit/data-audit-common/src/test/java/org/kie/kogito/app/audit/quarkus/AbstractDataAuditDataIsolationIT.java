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
import org.kie.kogito.event.process.ProcessInstanceStateEventBody;
import org.kie.kogito.process.ProcessInstance;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newProcessInstanceStateEvent;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newProcessInstanceStateEventWithVersion;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.wrapQuery;

@TestInstance(Lifecycle.PER_CLASS)
public abstract class AbstractDataAuditDataIsolationIT {

    protected static final String ALLOWED_PROCESS_ID = "itAllowedProcess";
    protected static final String ALLOWED_PROCESS_VERSION = "1.0";
    protected static final String OTHER_PROCESS_ID = "itOtherProcess";
    protected static final String WRONG_PROCESS_VERSION = "2.0";

    protected abstract void publishTestData() throws Exception;

    protected abstract RequestSpecification request();

    @Test
    public void testGetAllProcessInstancesState_foreignProcessIdInvisible() {
        assertThat(queryProcessInstances("GetAllProcessInstancesState"))
                .extracting(r -> r.get("processInstanceId"))
                .doesNotContain("iso-pi-2");
    }

    @Test
    public void testGetAllProcessInstancesState_allowedProcessIdVisible() {
        assertThat(queryProcessInstances("GetAllProcessInstancesState"))
                .extracting(r -> r.get("processInstanceId"))
                .contains("iso-pi-1");
    }

    @Test
    public void testGetAllProcessInstancesState_wrongVersionInvisible() {
        assertThat(queryProcessInstances("GetAllProcessInstancesState"))
                .extracting(r -> r.get("processInstanceId"))
                .doesNotContain("iso-pi-4");
    }

    @Test
    public void testGetAllProcessInstancesState_correctVersionVisible() {
        assertThat(queryProcessInstances("GetAllProcessInstancesState"))
                .extracting(r -> r.get("processInstanceId"))
                .contains("iso-pi-3");
    }

    @Test
    public void testGetAllProcessInstancesState_subProcessVisibleViaRootProcessId() {
        assertThat(queryProcessInstances("GetAllProcessInstancesState"))
                .extracting(r -> r.get("processInstanceId"))
                .contains("iso-pi-5");
    }

    @Test
    public void testGetAllProcessInstancesStateByProcessId_foreignProcessIdFiltered() {
        List<Map<String, Object>> result = request()
                .contentType(ContentType.JSON)
                .body(wrapQuery("{ GetAllProcessInstancesStateByProcessId(processId: \\\"" + OTHER_PROCESS_ID + "\\\") { processId processInstanceId } }"))
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .statusCode(200)
                .extract()
                .<List<Map<String, Object>>> path("data.GetAllProcessInstancesStateByProcessId");

        assertThat(result).isEmpty();
    }

    @Test
    public void testGetAllProcessInstancesStateByProcessId_allowedProcessReturnsResults() {
        List<Map<String, Object>> result = request()
                .contentType(ContentType.JSON)
                .body(wrapQuery("{ GetAllProcessInstancesStateByProcessId(processId: \\\"" + ALLOWED_PROCESS_ID + "\\\") { processId processInstanceId } }"))
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .statusCode(200)
                .extract()
                .<List<Map<String, Object>>> path("data.GetAllProcessInstancesStateByProcessId");

        assertThat(result)
                .isNotEmpty()
                .allSatisfy(row -> assertThat(row.get("processId")).isEqualTo(ALLOWED_PROCESS_ID));
    }

    @Test
    public void testGetAllProcessInstancesStateByProcessId_wrongVersionExcluded() {
        List<Map<String, Object>> result = request()
                .contentType(ContentType.JSON)
                .body(wrapQuery("{ GetAllProcessInstancesStateByProcessId(processId: \\\"" + ALLOWED_PROCESS_ID + "\\\") { processId processInstanceId } }"))
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .statusCode(200)
                .extract()
                .<List<Map<String, Object>>> path("data.GetAllProcessInstancesStateByProcessId");

        assertThat(result)
                .extracting(r -> r.get("processInstanceId"))
                .doesNotContain("iso-pi-4");
    }

    protected void doPublishProcessInstanceEvents(org.kie.kogito.event.EventPublisher publisher) throws Exception {
        publisher.publish(newProcessInstanceStateEvent(
                ALLOWED_PROCESS_ID, "iso-pi-1",
                ProcessInstance.STATE_ACTIVE,
                null, null, null, "testUser",
                ProcessInstanceStateEventBody.EVENT_TYPE_STARTED));

        publisher.publish(newProcessInstanceStateEvent(
                OTHER_PROCESS_ID, "iso-pi-2",
                ProcessInstance.STATE_ACTIVE,
                null, null, null, "testUser",
                ProcessInstanceStateEventBody.EVENT_TYPE_STARTED));

        publisher.publish(newProcessInstanceStateEventWithVersion(
                ALLOWED_PROCESS_ID, ALLOWED_PROCESS_VERSION, "iso-pi-3",
                ProcessInstance.STATE_ACTIVE,
                null, null, null, null, "testUser",
                ProcessInstanceStateEventBody.EVENT_TYPE_STARTED));

        publisher.publish(newProcessInstanceStateEventWithVersion(
                ALLOWED_PROCESS_ID, WRONG_PROCESS_VERSION, "iso-pi-4",
                ProcessInstance.STATE_ACTIVE,
                null, null, null, null, "testUser",
                ProcessInstanceStateEventBody.EVENT_TYPE_STARTED));

        publisher.publish(newProcessInstanceStateEventWithVersion(
                ALLOWED_PROCESS_ID, ALLOWED_PROCESS_VERSION, "iso-pi-5",
                ProcessInstance.STATE_ACTIVE,
                "iso-pi-1", ALLOWED_PROCESS_ID, ALLOWED_PROCESS_VERSION, null, "testUser",
                ProcessInstanceStateEventBody.EVENT_TYPE_STARTED));
    }

    private List<Map<String, Object>> queryProcessInstances(String queryName) {
        return request()
                .contentType(ContentType.JSON)
                .body(wrapQuery("{ " + queryName + " { processId processInstanceId } }"))
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .statusCode(200)
                .extract()
                .path("data." + queryName);
    }
}
