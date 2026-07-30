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
package org.kie.kogito.integrationtests.quarkus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.acme.travels.Traveller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcess;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusIntegrationTest
class BasicRestIT {

    private static final String VERSION = System.getProperty("kogito.version");

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @BeforeEach
    void resetEventListener() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .delete("/events")
                .then()
                .statusCode(204);
    }

    void assertExpectedUnitOfWorkEvents(Integer events) {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/events")
                .then()
                .body("start", is(events))
                .body("end", is(events))
                .body("abort", is(0))
                .statusCode(200);
    }

    private Map<String, Object> getParams() {
        return Map.of("var1", "Kermit", "var2", 34);
    }

    @Test
    void testGeneratedId() {
        String id = given()
                .contentType(ContentType.JSON)
                .when()
                .body(getParams())
                .post("/AdHocFragments")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .body("var1", equalTo("Kermit"))
                .header("Location", not(emptyOrNullString()))
                .extract()
                .path("id");

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/AdHocFragments/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("var1", equalTo("Kermit"))
                .body("var2", equalTo(34));

        assertExpectedUnitOfWorkEvents(1);
    }

    @Test
    void testWithInaccurateModel() {

        Traveller traveller = new Traveller("Javierito", "Dimequienes", "pepe@pepe.com", "Spanish");
        String processId = given()
                .contentType(ContentType.JSON)
                .body(traveller)
                .when()
                .post("/approvals")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .contentType(ContentType.JSON)
                .queryParam("user", "admin")
                .queryParam("group", "managers")
                .pathParam("processId", processId)
                .when()
                .get("/approvals/{processId}/tasks")
                .then()
                .statusCode(200);

        assertExpectedUnitOfWorkEvents(1);

        given()
                .contentType(ContentType.JSON)
                .when()
                .delete("/approvals/{processId}", processId)
                .then()
                .statusCode(200);
    }

    @Test
    void testWithBusinessKey() {
        String businessKey = "some business key";
        Map<String, String> params = new HashMap<>();
        params.put("var1", "Kermit");

        String id = given()
                .contentType(ContentType.JSON)
                .queryParam("businessKey", businessKey)
                .when()
                .body(params)
                .post("/AdHocFragments")
                .then()
                .statusCode(201)
                .header("Location", not(emptyOrNullString()))
                .body("id", not(emptyOrNullString()))
                .body("var1", equalTo("Kermit"))
                .extract()
                .path("id");

        // UUID is no longer the BusinessKey or generated from it
        String unexpectedId = UUID.nameUUIDFromBytes(businessKey.getBytes()).toString();
        assertNotEquals(businessKey, id);
        assertNotEquals(unexpectedId, id);

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/AdHocFragments/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("var1", equalTo("Kermit"));

        assertExpectedUnitOfWorkEvents(1);
    }

    @Test
    void testIdNotFound() {
        Map<String, String> params = new HashMap<>();
        params.put("var1", "Kermit");

        given()
                .contentType(ContentType.JSON)
                .when()
                .body(params)
                .get("/AdHocFragments/FOO")
                .then()
                .statusCode(404);

        assertExpectedUnitOfWorkEvents(0);
    }

    @Test
    void testUpdate() {
        String id = given()
                .contentType(ContentType.JSON)
                .when()
                .body(getParams())
                .post("/AdHocFragments")
                .then()
                .statusCode(201)
                .header("Location", not(emptyOrNullString()))
                .body("id", not(emptyOrNullString()))
                .body("var1", equalTo("Kermit"))
                .body("var2", equalTo(34))
                .extract()
                .path("id");

        // Update the previously model
        given()
                .contentType(ContentType.JSON)
                .when()
                .body(Collections.singletonMap("var1", "Gonzo"))
                .put("/AdHocFragments/{customId}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("var1", equalTo("Gonzo"))
                .body("var2", nullValue());

        assertExpectedUnitOfWorkEvents(2);
    }

    @Test
    void testPatch() {
        String id = given()
                .contentType(ContentType.JSON)
                .when()
                .body(getParams())
                .post("/AdHocFragments")
                .then()
                .statusCode(201)
                .header("Location", not(emptyOrNullString()))
                .body("id", not(emptyOrNullString()))
                .body("var1", equalTo("Kermit"))
                .body("var2", equalTo(34))
                .extract()
                .path("id");

        // Update the previously model
        given()
                .contentType(ContentType.JSON)
                .when()
                .body(Collections.singletonMap("var1", "Gonzo"))
                .patch("/AdHocFragments/{customId}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("var1", equalTo("Gonzo"))
                .body("var2", is(34));

        assertExpectedUnitOfWorkEvents(2);
    }

    @Test
    void testPatchWithNullValue() {
        String id = given()
                .contentType(ContentType.JSON)
                .when()
                .body(getParams())
                .post("/AdHocFragments")
                .then()
                .statusCode(201)
                .header("Location", not(emptyOrNullString()))
                .body("id", not(emptyOrNullString()))
                .body("var1", equalTo("Kermit"))
                .body("var2", equalTo(34))
                .extract()
                .path("id");

        // var1 set to null must be cleared, unlike an omitted field
        given()
                .contentType(ContentType.JSON)
                .when()
                .body("{\"var1\": null}")
                .patch("/AdHocFragments/{customId}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("var1", nullValue())
                .body("var2", is(34));

        assertExpectedUnitOfWorkEvents(2);
    }

    @Test
    void testPatchAndPutSequence() {
        String id = given()
                .contentType(ContentType.JSON)
                .when()
                .body(getParams())
                .post("/AdHocFragments")
                .then()
                .statusCode(201)
                .header("Location", not(emptyOrNullString()))
                .body("id", not(emptyOrNullString()))
                .body("var1", equalTo("Kermit"))
                .body("var2", equalTo(34))
                .extract()
                .path("id");

        // PATCH: var1 explicitly nulled, var2 untouched
        given()
                .contentType(ContentType.JSON)
                .when()
                .body("{\"var1\": null}")
                .patch("/AdHocFragments/{customId}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("var1", nullValue())
                .body("var2", is(34));

        // PUT right after a PATCH on the same resource: var2 omitted -> must still be nulled out
        given()
                .contentType(ContentType.JSON)
                .when()
                .body("{\"var1\": \"Gonzo\"}")
                .put("/AdHocFragments/{customId}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("var1", equalTo("Gonzo"))
                .body("var2", nullValue());

        // PATCH right after a PUT: empty body must leave both fields untouched
        given()
                .contentType(ContentType.JSON)
                .when()
                .body("{}")
                .patch("/AdHocFragments/{customId}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("var1", equalTo("Gonzo"))
                .body("var2", nullValue());

        assertExpectedUnitOfWorkEvents(4);
    }

    @Test
    void testDelete() {
        Map<String, String> params = new HashMap<>();
        params.put("var1", "Kermit");

        String id = given()
                .contentType(ContentType.JSON)
                .when()
                .body(params)
                .post("/AdHocFragments")
                .then()
                .statusCode(201)
                .header("Location", not(emptyOrNullString()))
                .body("id", not(emptyOrNullString()))
                .body("var1", equalTo("Kermit"))
                .extract()
                .path("id");

        given()
                .contentType(ContentType.JSON)
                .when()
                .body(params)
                .delete("/AdHocFragments/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("var1", equalTo("Kermit"));

        //Resource already deleted
        given()
                .contentType(ContentType.JSON)
                .when()
                .body(params)
                .delete("/AdHocFragments/{id}", id)
                .then()
                .statusCode(404);

        assertExpectedUnitOfWorkEvents(3);
    }

    @Test
    void testGetTasks() {
        Map<String, String> params = new HashMap<>();
        params.put("var1", "Kermit");

        String id = given()
                .contentType(ContentType.JSON)
                .when()
                .body(params)
                .post("/AdHocFragments")
                .then()
                .statusCode(201)
                .header("Location", not(emptyOrNullString()))
                .extract()
                .path("id");

        given()
                .queryParam("user", "john")
                .queryParam("group", "manager")
                .when()
                .get("/AdHocFragments/{id}/tasks", id)
                .then()
                .statusCode(200)
                .body("$.size()", is(1))
                .body("[0].name", is("Task"));

        assertExpectedUnitOfWorkEvents(1);
    }

    @Test
    void testCustomErrorStrategy() {
        Map<String, String> params = Collections.singletonMap("inout", "javierito");

        given()
                .contentType(ContentType.JSON)
                .when()
                .body(params)
                .post("/exce_proc")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .body("inout", is("pepito"))
                .header("Location", not(emptyOrNullString()));
    }

    @Test
    void testWorkflowType() {
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("/approvalsdetails")
                .then()
                .statusCode(200)
                .body("type", is(KogitoWorkflowProcess.BPMN_TYPE));
    }

    @Test
    void testVersion() {
        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("/approvalsdetails")
                .then()
                .statusCode(200)
                .body("version", is(VERSION));
    }
}
