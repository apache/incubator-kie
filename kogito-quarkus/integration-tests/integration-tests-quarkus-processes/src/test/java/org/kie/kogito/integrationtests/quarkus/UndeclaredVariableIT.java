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

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;

/**
 * Verifies that a process can accept an undeclared variable as input (kogito.workflow.variable-legacy-mode=true, opt-in).
 * The BPMN "undeclaredVariable" has no declared process variables.
 * When started with a "payload" key in the body the process enters a signal-wait state and stays ACTIVE.
 */
@QuarkusIntegrationTest
class UndeclaredVariableIT {

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void testUndeclaredVariableKeepsProcessActive() {
        // Start the process with an undeclared variable – must succeed (variable-legacy-mode compatibility mode is on)
        String pid = given()
                .contentType(ContentType.JSON)
                .body(Collections.singletonMap("payload", "someValue"))
                .when()
                .post("/undeclaredVariable")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .extract()
                .path("id");

        // The process should still be ACTIVE (waiting at the signal catch event)
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/undeclaredVariable/{pid}", pid)
                .then()
                .statusCode(200)
                .body("id", equalTo(pid));

        // Clean up – abort the process instance
        given()
                .contentType(ContentType.JSON)
                .when()
                .delete("/undeclaredVariable/{pid}", pid)
                .then()
                .statusCode(200);
    }
}
