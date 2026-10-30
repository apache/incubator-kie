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
package org.kie.kogito.integrationtests.springboot;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.nullValue;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = KogitoSpringbootApplication.class)
class SignalProcessTest extends BaseRestTest {

    @Test
    void testSignalStartProcess() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .body("hello world")
                .post("/signalStart/start")
                .then()
                .statusCode(202);

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/signalStart")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].message", equalTo("hello world"));
    }

    @Test
    void testProcessSignals() {
        String pid = given()
                .contentType(ContentType.JSON)
                .when()
                .body(new HashMap<>())
                .post("/greetings")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .body("test", nullValue())
                .extract()
                .path("id");

        given()
                .contentType(ContentType.JSON)
                .when()
                .body("\"testvalue\"")
                .post("/greetings/{pid}/signalwithdata", pid)
                .then()
                .statusCode(200)
                .body("id", not(emptyOrNullString()))
                .body("test", is("testvalue"));

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/greetings/{pid}", pid)
                .then()
                .statusCode(200)
                .body("test", is("testvalue"));

        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/greetings/{pid}/signalwithoutdata", pid)
                .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/greetings/{pid}", pid)
                .then()
                .statusCode(404);
    }

    @Test
    void testProcessSequentialSignals() {
        String pid = given()
                .contentType(ContentType.JSON)
                .when()
                .body("{ \"name\": \"Martin\" }")
                .post("/sequentialsignals")
                .then()
                .statusCode(201)
                .body("hello", is("Hello Martin!"))
                .body("bye", nullValue())
                .extract()
                .path("id");

        // wrong signal invocation should return 412 response
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/sequentialsignals/{pid}/complete", pid)
                .then()
                .statusCode(412);

        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/sequentialsignals/{pid}/bye", pid)
                .then()
                .statusCode(200)
                .body("id", not(emptyOrNullString()));

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/sequentialsignals/{pid}", pid)
                .then()
                .statusCode(200)
                .body("hello", is("Hello Martin!"))
                .body("goodbye", is("Bye Martin!"));

        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/sequentialsignals/{pid}/complete", pid)
                .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/sequentialsignals/{pid}", pid)
                .then()
                .statusCode(404);
    }

    @Test
    void testProcessSequentialSignalsSameVarName() {
        String pid = given()
                .contentType(ContentType.JSON)
                .when()
                .body("{ \"name\": \"Martin\" }")
                .post("/sequentialsignalssamevarname")
                .then()
                .statusCode(201)
                .body("hello", is("Hello Martin!"))
                .body("bye", nullValue())
                .extract()
                .path("id");

        // wrong signal invocation should return 412 response
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/sequentialsignals/{pid}/complete", pid)
                .then()
                .statusCode(412);

        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/sequentialsignalssamevarname/{pid}/bye", pid)
                .then()
                .statusCode(200)
                .body("id", not(emptyOrNullString()));

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/sequentialsignalssamevarname/{pid}", pid)
                .then()
                .statusCode(200)
                .body("hello", is("Hello Martin!"))
                .body("bye", is("Bye Martin!"));

        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/sequentialsignalssamevarname/{pid}/complete", pid)
                .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/sequentialsignalssamevarname/{pid}", pid)
                .then()
                .statusCode(404);
    }

    @Test
    void testInvalidSignalRejected() {
        // Given: Process instance waiting for specific signal
        String pid = given()
                .contentType(ContentType.JSON)
                .when()
                .body(new HashMap<>())
                .post("/greetings")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .body("test", nullValue())
                .extract()
                .path("id");

        // When: Send invalid signal that doesn't exist for the process
        // Then: Should return 404 Bad Request
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/greetings/{pid}/invalidsignal", pid)
                .then()
                .statusCode(404);

        // Process should still be active
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/greetings/{pid}", pid)
                .then()
                .statusCode(200);
    }

    @Test
    void testAdHocFragmentSignaling() {
        // Given: Ad hoc process
        String pid = given()
                .contentType(ContentType.JSON)
                .when()
                .body(java.util.Collections.singletonMap("var1", "Kermit"))
                .post("/AdHocFragments")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .extract()
                .path("id");

        // When: Trigger ad hoc user task with user and group
        given()
                .contentType(ContentType.JSON)
                .queryParam("user", "john")
                .queryParam("group", "manager")
                .when()
                .post("/AdHocFragments/{pid}/AdHocTask1/trigger", pid)
                .then()
                .statusCode(201);

        // Then: Ad hoc user task and subsequent auto-started task should be created
        given()
                .contentType(ContentType.JSON)
                .queryParam("user", "john")
                .queryParam("group", "manager")
                .when()
                .get("/AdHocFragments/{pid}/tasks", pid)
                .then()
                .statusCode(200)
                .body("$.size()", is(2));
    }

    @Test
    void testAdHocServiceTaskSignaling() {
        // Given: Ad hoc process with initial data
        String pid = given()
                .contentType(ContentType.JSON)
                .when()
                .body(java.util.Collections.singletonMap("var1", "Kermit"))
                .post("/AdHocFragments")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .extract()
                .path("id");

        // When: Trigger ad hoc service task
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/AdHocFragments/{pid}/Service_Task", pid)
                .then()
                .statusCode(200)
                .body("var1", not(nullValue()));

        // Then: Verify service task executed and updated variables
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/AdHocFragments/{pid}", pid)
                .then()
                .statusCode(200)
                .body("var1", not(nullValue()));
    }

    @Test
    void testDynamicSignalEndpoint() {
        // Given: Process instance waiting for dynamic signal "Approved"
        String pid = given()
                .contentType(ContentType.JSON)
                .body("{\"signalName\":\"Approved\"}")
                .when()
                .post("/dynamicsignals")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .extract()
                .path("id");

        // When: Send an incorrect dynamic signal
        // Then: Should return 412 as the process is not accepting this signal
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/dynamicsignals/{pid}/signal/{signalName}", pid, "Rejected")
                .then()
                .statusCode(412);

        // Then: Process instance should still be active and waiting for "Approved"
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/dynamicsignals/{pid}", pid)
                .then()
                .statusCode(200)
                .body("signalName", is("Approved"));

        // When: Send the correct dynamic signal "Approved"
        // Then: Signal should be accepted and process should complete
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/dynamicsignals/{pid}/signal/{signalName}", pid, "Approved")
                .then()
                .statusCode(200);

        // Then: Completed process instance should no longer be available
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/dynamicsignals/{pid}", pid)
                .then()
                .statusCode(404);
    }
}