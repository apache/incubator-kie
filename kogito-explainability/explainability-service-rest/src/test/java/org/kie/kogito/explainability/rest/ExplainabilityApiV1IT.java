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
package org.kie.kogito.explainability.rest;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.kie.kogito.explainability.api.BaseExplainabilityResult;
import org.kie.kogito.explainability.api.LIMEExplainabilityRequest;
import org.kie.kogito.explainability.api.ModelIdentifier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ExplainabilityApiV1IT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String executionId = UUID.randomUUID().toString();
    private static final String serviceUrl = "http://localhost:8080";

    @Test
    void testEndpointWithRequest() throws JsonProcessingException {
        ModelIdentifier modelIdentifier = new ModelIdentifier("dmn", "namespace:name");

        String body = MAPPER.writeValueAsString(new LIMEExplainabilityRequest(executionId,
                serviceUrl,
                modelIdentifier,
                Collections.emptyList(),
                Collections.emptyList()));

        BaseExplainabilityResult result = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/v1/explain")
                .as(BaseExplainabilityResult.class);

        assertThat(result.getExecutionId()).isEqualTo(executionId);
    }

    @Test
    void testEndpointWithBadRequests() throws JsonProcessingException {
        LIMEExplainabilityRequest[] badRequests = new LIMEExplainabilityRequest[] {
                new LIMEExplainabilityRequest(executionId,
                        serviceUrl,
                        new ModelIdentifier("", "test"),
                        Collections.emptyList(),
                        Collections.emptyList()),
                new LIMEExplainabilityRequest(executionId,
                        serviceUrl,
                        new ModelIdentifier("test", ""),
                        Collections.emptyList(),
                        Collections.emptyList()),
                new LIMEExplainabilityRequest(executionId,
                        "",
                        new ModelIdentifier("test", "test"),
                        Collections.emptyList(),
                        Collections.emptyList()),
        };

        for (int i = 0; i < badRequests.length; i++) {
            String body = MAPPER.writeValueAsString(badRequests[i]);

            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post("/v1/explain")
                    .then()
                    .statusCode(400);
        }
    }
}
