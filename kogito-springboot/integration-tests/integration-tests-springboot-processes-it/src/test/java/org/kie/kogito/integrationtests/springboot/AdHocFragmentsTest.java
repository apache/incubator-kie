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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = KogitoSpringbootApplication.class)
class AdHocFragmentsTest extends BaseRestTest {

    @Test
    void testUserTaskProcess() {
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

        String taskId = extractID(given()
                .contentType(ContentType.JSON)
                .queryParam("user", "john")
                .queryParam("group", "manager")
                .when()
                .post("/AdHocFragments/{pid}/AdHocTask1/trigger", id)
                .then()
                .statusCode(201)
                .header("Location", not(emptyOrNullString()))
                .extract()
                .header("Location"));

        params = new HashMap<>();
        params.put("newVar1", "Gonzo");
        given()
                .contentType(ContentType.JSON)
                .when()
                .body(params)
                .queryParam("user", "john")
                .post("/AdHocFragments/{id}/AdHocTask1/{taskId}", id, taskId)
                .then()
                .statusCode(200)
                .body("var1", equalTo("Gonzo"));
    }

    @Test
    void testServiceTaskProcess() {
        Map<String, String> params = new HashMap<>();
        params.put("var1", "Kermit");

        String pid = given()
                .contentType(ContentType.JSON)
                .when()
                .body(params)
                .post("/AdHocFragments")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/AdHocFragments/{pid}/Service_Task", pid)
                .then()
                .statusCode(200)
                .body("var1", equalTo("Hello Kermit 5!"));
    }

    @Test
    void testNonAdHocUserTaskProcess() {
        Map<String, String> params = new HashMap<>();
        params.put("var1", "Kermit");

        String pid = given()
                .contentType(ContentType.JSON)
                .when()
                .body(params)
                .post("/AdHocFragments")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .contentType(ContentType.JSON)
                .queryParam("user", "john")
                .when()
                .post("/AdHocFragments/{pid}/Task", pid)
                .then()
                .statusCode(404);
    }
}
