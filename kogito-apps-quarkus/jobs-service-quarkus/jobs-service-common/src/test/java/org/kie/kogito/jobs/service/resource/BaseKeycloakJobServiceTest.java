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
package org.kie.kogito.jobs.service.resource;

import java.io.IOException;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.jobs.api.Job;
import org.kie.kogito.jobs.api.JobBuilder;
import org.kie.kogito.jobs.service.model.JobStatus;
import org.kie.kogito.jobs.service.model.ScheduledJob;
import org.kie.kogito.jobs.service.utils.DateUtil;
import org.kie.kogito.testcontainers.KogitoKeycloakContainer;
import org.kie.kogito.testcontainers.quarkus.KeycloakQuarkusTestResource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.jobs.service.health.HealthCheckUtils.awaitReadyHealthCheck;
import static org.kie.kogito.jobs.service.resource.BaseJobResourceTest.NODE_INSTANCE_ID;
import static org.kie.kogito.jobs.service.resource.BaseJobResourceTest.PRIORITY;
import static org.kie.kogito.jobs.service.resource.BaseJobResourceTest.PROCESS_ID;
import static org.kie.kogito.jobs.service.resource.BaseJobResourceTest.PROCESS_INSTANCE_ID;
import static org.kie.kogito.jobs.service.resource.BaseJobResourceTest.ROOT_PROCESS_ID;
import static org.kie.kogito.jobs.service.resource.BaseJobResourceTest.ROOT_PROCESS_INSTANCE_ID;

public abstract class BaseKeycloakJobServiceTest {

    public static final int OK_CODE = 200;
    public static final int UNAUTHORIZED_CODE = 403;
    public static final int FORBIDDEN_CODE = 401;

    @ConfigProperty(name = KeycloakQuarkusTestResource.KOGITO_KEYCLOAK_PROPERTY)
    private String keycloakURL;

    @Inject
    private ObjectMapper objectMapper;

    @BeforeAll
    public static void setup() throws Exception {
        System.setProperty("quarkus.http.auth.policy.role-policy1.roles-allowed", "confidential");
        System.setProperty("quarkus.http.auth.permission.roles1.paths", "/*");
        System.setProperty("quarkus.http.auth.permission.roles1.policy", "role-policy1");
    }

    @BeforeEach
    public void init() throws Exception {
        //health check - wait to be ready
        awaitReadyHealthCheck(2, MINUTES);
    }

    @Test
    void create() throws Exception {
        final Job job = getJob("1");
        createJob(jobToJson(job), getAccessToken("alice"), UNAUTHORIZED_CODE);
        createJob(jobToJson(job), "", FORBIDDEN_CODE);
        final ScheduledJob response = createJob(jobToJson(job), getAccessToken("jdoe"), OK_CODE)
                .extract()
                .as(ScheduledJob.class);

        // Resetting the date to set the right UTC ZoneId. This is required due to recent changes on jackson-datatype-jsr310
        response.setExpirationTime(DateUtil.instantToZonedDateTime(response.getExpirationTime().toInstant()));

        assertThat(response).isEqualTo(job);
    }

    private ValidatableResponse createJob(String body, String token, int statusCode) throws IOException {
        RequestSpecification requestSpecification = given();
        if (token != null && !token.isEmpty()) {
            requestSpecification = requestSpecification.given().auth().oauth2(token);
        }
        return requestSpecification
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(RestApiConstants.JOBS_PATH)
                .then()
                .statusCode(statusCode);
    }

    private String jobToJson(Job job) throws JsonProcessingException {
        return objectMapper.writeValueAsString(job);
    }

    private Job getJob(String id) {
        return JobBuilder
                .builder()
                .id(id)
                .expirationTime(DateUtil.now().plusSeconds(10))
                .callbackEndpoint("http://localhost:8081/callback")
                .processId(PROCESS_ID)
                .processInstanceId(PROCESS_INSTANCE_ID)
                .rootProcessId(ROOT_PROCESS_ID)
                .rootProcessInstanceId(ROOT_PROCESS_INSTANCE_ID)
                .nodeInstanceId(NODE_INSTANCE_ID)
                .repeatInterval(0L)
                .repeatLimit(0)
                .priority(PRIORITY)
                .build();
    }

    @Test
    void deleteAfterCreate() throws Exception {
        final String id = "2";
        final Job job = getJob(id);
        createJob(jobToJson(job), getAccessToken("alice"), UNAUTHORIZED_CODE);
        createJob(jobToJson(job), "", FORBIDDEN_CODE);
        createJob(jobToJson(job), getAccessToken("jdoe"), OK_CODE);
        given().pathParam("id", id)
                .when()
                .delete(RestApiConstants.JOBS_PATH + "/{id}")
                .then()
                .statusCode(FORBIDDEN_CODE);
        given().auth().oauth2(getAccessToken("alice"))
                .pathParam("id", id)
                .when()
                .delete(RestApiConstants.JOBS_PATH + "/{id}")
                .then()
                .statusCode(UNAUTHORIZED_CODE);

        final ScheduledJob response = given().auth().oauth2(getAccessToken("jdoe"))
                .pathParam("id", id)
                .when()
                .delete(RestApiConstants.JOBS_PATH + "/{id}")
                .then()
                .statusCode(OK_CODE)
                .contentType(ContentType.JSON)
                .extract()
                .as(ScheduledJob.class);

        // Resetting the date to set the right UTC ZoneId. This is required due to recent changes on jackson-datatype-jsr310
        response.setExpirationTime(DateUtil.instantToZonedDateTime(response.getExpirationTime().toInstant()));

        assertThat(response).isEqualTo(job);
    }

    @Test
    void getAfterCreate() throws Exception {
        final String id = "3";
        final Job job = getJob(id);
        createJob(jobToJson(job), getAccessToken("jdoe"), OK_CODE);
        given().pathParam("id", id)
                .when()
                .get(RestApiConstants.JOBS_PATH + "/{id}")
                .then()
                .statusCode(FORBIDDEN_CODE);
        given().auth().oauth2(getAccessToken("alice"))
                .pathParam("id", id)
                .when()
                .get(RestApiConstants.JOBS_PATH + "/{id}")
                .then()
                .statusCode(UNAUTHORIZED_CODE);
        final ScheduledJob scheduledJob = given().auth().oauth2(getAccessToken("jdoe"))
                .pathParam("id", id)
                .when()
                .get(RestApiConstants.JOBS_PATH + "/{id}")
                .then()
                .statusCode(OK_CODE)
                .contentType(ContentType.JSON)
                .assertThat()
                .extract()
                .as(ScheduledJob.class);
        assertThat(job.getId()).isEqualTo(scheduledJob.getId());
    }

    @Test
    void executeTest() throws Exception {
        final String id = "4";
        final Job job = getJob(id);
        createJob(jobToJson(job), getAccessToken("jdoe"), OK_CODE);
        given().pathParam("id", id)
                .when()
                .get(RestApiConstants.JOBS_PATH + "/{id}")
                .then()
                .statusCode(FORBIDDEN_CODE);
        given().auth().oauth2(getAccessToken("alice"))
                .pathParam("id", id)
                .when()
                .get(RestApiConstants.JOBS_PATH + "/{id}")
                .then()
                .statusCode(UNAUTHORIZED_CODE);
        final ScheduledJob scheduledJob = given().auth().oauth2(getAccessToken("jdoe"))
                .pathParam("id", id)
                .when()
                .get(RestApiConstants.JOBS_PATH + "/{id}")
                .then()
                .statusCode(OK_CODE)
                .contentType(ContentType.JSON)
                .assertThat()
                .extract()
                .as(ScheduledJob.class);
        assertThat(job.getId()).isEqualTo(scheduledJob.getId());
        assertThat(scheduledJob.getRetries()).isEqualTo(0);
        assertThat(scheduledJob.getStatus()).isEqualTo(JobStatus.SCHEDULED);
        assertThat(scheduledJob.getScheduledId()).isNotNull();
    }

    private String getAccessToken(String userName) {
        return given()
                .param("grant_type", "password")
                .param("username", userName)
                .param("password", userName)
                .param("client_id", KogitoKeycloakContainer.CLIENT_ID)
                .param("client_secret", KogitoKeycloakContainer.CLIENT_SECRET)
                .when()
                .post(keycloakURL + "/protocol/openid-connect/token")
                .jsonPath().get("access_token");
    }
}
