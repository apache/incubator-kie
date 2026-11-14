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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.kie.kogito.app.audit.api.DataAuditStoreProxyService;
import org.kie.kogito.app.audit.api.SubsystemConstants;
import org.kie.kogito.app.audit.spi.DataAuditContextFactory;
import org.kie.kogito.event.EventPublisher;
import org.kie.kogito.event.job.JobInstanceDataEvent;
import org.kie.kogito.event.process.ProcessInstanceStateDataEvent;
import org.kie.kogito.event.process.ProcessInstanceStateEventBody;
import org.kie.kogito.jobs.service.model.JobStatus;
import org.kie.kogito.process.ProcessInstance;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.deriveNewState;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newJobEvent;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newProcessInstanceStateEvent;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.wrapQuery;

@QuarkusTest
@TestInstance(Lifecycle.PER_CLASS)
public class QuarkusAuditQueryRegistryServiceTest {

    @Inject
    EventPublisher publisher;

    @Inject
    DataAuditContextFactory contextFactory;

    @BeforeAll
    public void init() throws Exception {

        ProcessInstanceStateDataEvent processInstanceEvent = newProcessInstanceStateEvent("processId1", "1", ProcessInstance.STATE_ACTIVE, "rootI1", "rootP1", "parent1", "identity",
                ProcessInstanceStateEventBody.EVENT_TYPE_STARTED);
        publisher.publish(processInstanceEvent);

        JobInstanceDataEvent jobEvent;
        jobEvent = newJobEvent("job1", "nodeInstanceId1", 1, "processId1", "processInstanceId1", 100L, 10, "rootProcessId1", "rootProcessInstanceId1", JobStatus.SCHEDULED, 0);
        publisher.publish(jobEvent);

        jobEvent = deriveNewState(jobEvent, 1, JobStatus.EXECUTED);
        publisher.publish(jobEvent);

        jobEvent = newJobEvent("job2", "nodeInstanceId1", 1, "processId1", "processInstanceId2", 100L, 10, "rootProcessId1", "rootProcessInstanceId1", JobStatus.SCHEDULED, 0);
        publisher.publish(jobEvent);

        jobEvent = newJobEvent("job3", "nodeInstanceId1", 1, "processId1", "processInstanceId3", 100L, 10, "rootProcessId1", "rootProcessInstanceId1", JobStatus.SCHEDULED, 0);
        publisher.publish(jobEvent);

        jobEvent = deriveNewState(jobEvent, 1, JobStatus.CANCELED);
        publisher.publish(jobEvent);

        jobEvent = newJobEvent("job4", "nodeInstanceId1", 1, "processId1", "processInstanceId4", 100L, 10, "rootProcessId1", "rootProcessInstanceId1", JobStatus.SCHEDULED, 0);
        publisher.publish(jobEvent);

        jobEvent = deriveNewState(jobEvent, 1, JobStatus.RETRY);
        publisher.publish(jobEvent);

        jobEvent = deriveNewState(jobEvent, 2, JobStatus.EXECUTED);
        publisher.publish(jobEvent);

        jobEvent = newJobEvent("job5", "nodeInstanceId1", 1, "processId1", "processInstanceI51", 100L, 10, "rootProcessId1", "rootProcessInstanceId1", JobStatus.SCHEDULED, 0);
        publisher.publish(jobEvent);

        jobEvent = deriveNewState(jobEvent, 1, JobStatus.ERROR);
        publisher.publish(jobEvent);
    }

    @Test
    public void testRegisterQueryComplexType() {

        String body = "{"
                + "\"identifier\" : \"testsComplex\", "
                + "\"graphQLDefinition\" : \"type EventTest { jobId : String, processInstanceId: String} type Query { testsComplex (pagination: Pagination) : [ EventTest ] } \","
                + "\"query\" : \" SELECT o.job_id, o.process_instance_id FROM job_execution_log o \""
                + "}";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(SubsystemConstants.DATA_AUDIT_REGISTRY_PATH)
                .then()
                .log()
                .body()
                .assertThat()
                .statusCode(200);

        String query = "{ testsComplex { jobId, processInstanceId } }";
        query = wrapQuery(query);

        List<Map<String, Object>> response = given()
                .contentType(ContentType.JSON)
                .body(query)
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .log()
                .body()
                .assertThat()
                .statusCode(200)
                .and()
                .extract().path("data.testsComplex");

        assertThat(response)
                .hasSize(10);

    }

    @Test
    public void testRegisterQuerySimpleType() {

        String body = "{"
                + "\"identifier\" : \"testSimple\", "
                + "\"graphQLDefinition\" : \"type Query { testSimple (pagination: Pagination) : [ String ] } \","
                + "\"query\" : \" SELECT o.job_id FROM job_execution_log o \""
                + "}";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(SubsystemConstants.DATA_AUDIT_REGISTRY_PATH)
                .then()
                .log()
                .body()
                .assertThat()
                .statusCode(200);

        String query = "{ testSimple }";
        query = wrapQuery(query);

        List<Map<String, Object>> response = given()
                .contentType(ContentType.JSON)
                .body(query)
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .log()
                .body()
                .assertThat()
                .statusCode(200)
                .and()
                .extract().path("data.testSimple");

        assertThat(response)
                .hasSize(10);

    }

    @Test
    public void testRegisterQueryWithDateTime() {

        String body = "{\n"
                + "    \"identifier\": \"GetWithTime\",\n"
                + "    \"graphQLDefinition\": \"type GetWithTimeType {eventId: String, processInstanceId: String, processId: String, state: String, eventType: String, eventDate: DateTime } type Query {GetWithTime(pagination:Pagination) : [GetWithTimeType]}\",\n"
                + "    \"query\": \"SELECT log.event_id as eventId, log.process_instance_id as processInstanceId, log.process_id as processId, log.state as state, log.event_type as eventType, log.event_date as eventDate FROM Process_Instance_State_Log log group by log.event_id, log.event_type, log.event_date, log.process_id, log.process_instance_id, log.state order by processInstanceId, eventDate\" }";

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(SubsystemConstants.DATA_AUDIT_REGISTRY_PATH)
                .then()
                .log()
                .body()
                .assertThat()
                .statusCode(200);

        String query = "{ GetWithTime  {eventId, eventDate, processInstanceId, state} }";
        query = wrapQuery(query);

        List<Map<String, Object>> response = given()
                .contentType(ContentType.JSON)
                .body(query)
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .log()
                .body()
                .assertThat()
                .statusCode(200)
                .and()
                .extract().path("data.GetWithTime");

        assertThat(response)
                .extracting("eventDate")
                .allSatisfy(OffsetDateTime.class::isInstance);
    }

    @Test
    public void testQuerySQLRegistrationFailure() {

        String q = " ".repeat(6000);

        String body = "{\n"
                + "    \"identifier\": \"ErrorSQLQuery\",\n"
                + "    \"graphQLDefinition\": \"type ErrorSQLQuery {eventId: String, processInstanceId: String, processId: String, state: String, eventType: String, eventDate: DateTime } type Query {GetAllStates(pagination:Pagination) : [AllStates]}\",\n"
                + "    \"query\": \"" + q + "\" }";

        String graphQLBefore = given()
                .contentType(ContentType.JSON)
                .when()
                .get(SubsystemConstants.DATA_AUDIT_REGISTRY_PATH)
                .then()
                .log()
                .body()
                .assertThat()
                .statusCode(200)
                .extract().asString();

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(SubsystemConstants.DATA_AUDIT_REGISTRY_PATH)
                .then()
                .log()
                .body()
                .assertThat()
                .statusCode(400);

        String graphQLAfter = given()
                .contentType(ContentType.JSON)
                .when()
                .get(SubsystemConstants.DATA_AUDIT_REGISTRY_PATH)
                .then()
                .log()
                .body()
                .assertThat()
                .statusCode(200)
                .extract().asString();

        assertThat(graphQLBefore).isEqualTo(graphQLAfter);
        Assertions.assertThat(graphQLAfter).doesNotContain("ErrorSQLQuery");

        Assertions.assertThat(DataAuditStoreProxyService.newAuditStoreService().findQueries(contextFactory.newDataAuditContext()))
                .extracting(e -> e.getIdentifier())
                .doesNotContain("ErrorSQLQuery");

    }

    @Test
    public void testQueryGraphQLRegistrationFailure() {

        String q = " ".repeat(1000);

        String body = "{\n"
                + "    \"identifier\": \"ErrorGraphQLQuery\",\n"
                + "    \"graphQLDefinition\": \"type ErrorGraphQLQuery {eventId: processInstanceId: String, processId: String, state: String, eventType: String, eventDate: DateTime } type Query {GetAllStates(pagination:Pagination) : [AllStates]}\",\n"
                + "    \"query\": \"" + q + "\" }";

        String graphQLBefore = given()
                .contentType(ContentType.JSON)
                .when()
                .get(SubsystemConstants.DATA_AUDIT_REGISTRY_PATH)
                .then()
                .log()
                .body()
                .assertThat()
                .statusCode(200)
                .extract().asString();

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(SubsystemConstants.DATA_AUDIT_REGISTRY_PATH)
                .then()
                .log()
                .body()
                .assertThat()
                .statusCode(400);

        String graphQLAfter = given()
                .contentType(ContentType.JSON)
                .when()
                .get(SubsystemConstants.DATA_AUDIT_REGISTRY_PATH)
                .then()
                .log()
                .body()
                .assertThat()
                .statusCode(200)
                .extract().asString();

        assertThat(graphQLBefore).isEqualTo(graphQLAfter);
        Assertions.assertThat(graphQLAfter).doesNotContain("ErrorGraphQLQuery");

        Assertions.assertThat(DataAuditStoreProxyService.newAuditStoreService().findQueries(contextFactory.newDataAuditContext()))
                .extracting(e -> e.getIdentifier())
                .doesNotContain("ErrorGraphQLQuery");
    }

    @Test
    public void testPrintSchema() {
        given()
                .when()
                .get(SubsystemConstants.DATA_AUDIT_REGISTRY_PATH)
                .then()
                .log()
                .body()
                .assertThat()
                .statusCode(200)
                .body(Matchers.notNullValue());
    }
}
