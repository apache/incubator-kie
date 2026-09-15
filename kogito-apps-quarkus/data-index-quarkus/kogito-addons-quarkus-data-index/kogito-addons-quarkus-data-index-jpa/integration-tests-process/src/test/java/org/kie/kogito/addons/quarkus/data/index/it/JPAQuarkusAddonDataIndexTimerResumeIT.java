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
package org.kie.kogito.addons.quarkus.data.index.it;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Regression test for <a href="https://github.com/apache/incubator-kie-kogito-runtimes/issues/3869">#3869</a>:
 * events emitted after a persisted instance is resumed must reach the data index.
 */
@QuarkusIntegrationTest
class JPAQuarkusAddonDataIndexTimerResumeIT {

    private static final String PROCESS_ID = "timerResume";

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void testEventsAfterTimerResumeReachDataIndex() {
        String processInstanceId = given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .post("/" + PROCESS_ID)
                .then()
                .statusCode(201)
                .body("id", is(notNullValue()))
                .extract().path("id");

        // Parked on the timer: indexed, but not finished yet.
        queryState(processInstanceId).body("data.ProcessInstances.size()", is(1))
                .body("data.ProcessInstances[0].state", is("ACTIVE"));

        // The timer fires, the instance is resumed off the request thread and completes. If the
        // resuming path publishes into a UnitOfWorkManager that carries no publishers, the data
        // index never learns about it and this never becomes COMPLETED.
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> queryState(processInstanceId)
                        .body("data.ProcessInstances.size()", is(1))
                        .body("data.ProcessInstances[0].state", is("COMPLETED")));
    }

    private static ValidatableResponse queryState(String processInstanceId) {
        return given().contentType(ContentType.JSON)
                .body("{ \"query\" : \"{ProcessInstances(where: { id: {equal: \\\"" + processInstanceId
                        + "\\\"}}){ id, state, processId } }\" }")
                .when().post("/graphql")
                .then().statusCode(200);
    }
}
