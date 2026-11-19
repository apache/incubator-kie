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
package org.kie.kogito.explainability;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.kie.kogito.config.ConfigBean;
import org.kie.kogito.config.StaticConfigBean;
import org.kie.kogito.explainability.model.PredictOutput;
import org.kie.kogito.test.utils.SocketUtils;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;

import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Response;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.explainability.Constants.MODEL_NAME;
import static org.kie.kogito.explainability.Constants.MODEL_NAMESPACE;

@QuarkusIntegrationTest
public class QuarkusExplainableResourceIT {

    @Singleton
    public static ConfigBean configBeanProducer() {
        return new StaticConfigBean("http://localhost:" + SocketUtils.findAvailablePort(), true, null);
    }

    @Test
    void explainServiceTest() {
        String resourceId = String.format("%s:%s", MODEL_NAMESPACE, MODEL_NAME);
        String body = String.format(
                "[{\"request\" : {\"Driver\": {\"Age\": 25, \"Points\": 100}, \"Violation\": {\"Type\" : \"speed\", \"Actual Speed\": 120, \"Speed Limit\": 40}}," +
                        "\"modelIdentifier\": {\"resourceType\": \"dmn\",\"resourceId\": \"%s\"}}]",
                resourceId);

        List<PredictOutput> outputs = given()
                .contentType(ContentType.JSON)
                .when()
                .body(body)
                .post("/predict")
                .as(new TypeRef<List<PredictOutput>>() {
                });

        assertThat(outputs.size()).isEqualTo(1);

        PredictOutput output = outputs.get(0);

        assertThat(output).isNotNull();
        assertThat(output.getResult()).isNotNull();
        assertThat(output.getModelIdentifier()).isNotNull();
        Map<String, Object> result = output.getResult();

        assertThat(result.containsKey("Should the driver be suspended?")).isTrue();
        assertThat(result.get("Should the driver be suspended?")).isEqualTo("Yes");
        assertThat(result.containsKey("Fine")).isTrue();
        Map<String, Object> expectedFine = new HashMap<>();
        expectedFine.put("Points", 7);
        expectedFine.put("Amount", 1000);
        assertThat(result.get("Fine")).isEqualTo(expectedFine);
    }

    @Test
    void explainServiceTestMultipleInputs() {
        String resourceId = String.format("%s:%s", MODEL_NAMESPACE, MODEL_NAME);
        String body = String.format(
                "[{\"request\" : {\"Driver\": {\"Age\": 25, \"Points\": 100}, \"Violation\": {\"Type\" : \"speed\", \"Actual Speed\": 120, \"Speed Limit\": 40}}," +
                        "\"modelIdentifier\": {\"resourceType\": \"dmn\",\"resourceId\": \"%s\"}}, " +
                        "{\"request\" : {\"Driver\": {\"Age\": 25, \"Points\": 100}, \"Violation\": {\"Type\" : \"speed\", \"Actual Speed\": 120, \"Speed Limit\": 120}}," +
                        "\"modelIdentifier\": {\"resourceType\": \"dmn\",\"resourceId\": \"%s\"}}]",
                resourceId, resourceId);

        List<PredictOutput> outputs = given()
                .contentType(ContentType.JSON)
                .when()
                .body(body)
                .post("/predict")
                .as(new TypeRef<List<PredictOutput>>() {
                });

        assertThat(outputs.size()).isEqualTo(2);

        PredictOutput output = outputs.get(1);

        assertThat(output).isNotNull();
        assertThat(output.getResult()).isNotNull();
        assertThat(output.getModelIdentifier()).isNotNull();
        Map<String, Object> result = output.getResult();

        assertThat(result.containsKey("Should the driver be suspended?")).isTrue();
        assertThat(result.get("Should the driver be suspended?")).isEqualTo("No");
        assertThat(result.get("Fine")).isNull();
    }

    @Test
    void explainServiceTestNoInputs() {
        String body = "[]";

        List<PredictOutput> outputs = given()
                .contentType(ContentType.JSON)
                .when()
                .body(body)
                .post("/predict")
                .as(new TypeRef<List<PredictOutput>>() {
                });

        assertThat(outputs.size()).isEqualTo(0);
    }

    @Test
    void explainServiceFail() {
        String unknownResourceId = "unknown:model";
        String body = String.format(
                "[{\"request\" : {\"Driver\": {\"Age\": 25, \"Points\": 100}, \"Violation\": {\"Type\" : \"speed\", \"Actual Speed\": 120, \"Speed Limit\": 40}}," +
                        "\"modelIdentifier\": {\"resourceType\": \"dmn\",\"resourceId\": \"%s\"}}, " +
                        "{\"request\" : {\"Driver\": {\"Age\": 25, \"Points\": 100}, \"Violation\": {\"Type\" : \"speed\", \"Actual Speed\": 120, \"Speed Limit\": 120}}," +
                        "\"modelIdentifier\": {\"resourceType\": \"dmn\",\"resourceId\": \"%s\"}}]",
                unknownResourceId, unknownResourceId);

        given()
                .contentType(ContentType.JSON)
                .when()
                .body(body)
                .post("/predict")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body(Matchers.isA(String.class))
                .body(Matchers.containsString("DMN model 'model' not found with namespace 'unknown'"));
    }
}
