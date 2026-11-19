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
package org.kie.kogito.trusty.service.common.api;

import java.text.ParseException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kie.kogito.trusty.service.common.TrustyService;
import org.kie.kogito.trusty.service.common.models.MatchedExecutionHeaders;
import org.kie.kogito.trusty.service.common.responses.ExecutionsResponse;
import org.kie.kogito.trusty.storage.api.model.Execution;
import org.kie.kogito.trusty.storage.api.model.decision.DMNModelMetadata;
import org.kie.kogito.trusty.storage.api.model.decision.DMNModelWithMetadata;
import org.kie.kogito.trusty.storage.api.model.decision.Decision;
import org.mockito.Mockito;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
class ExecutionsApiV1IT {

    @InjectMock
    TrustyService executionService;

    @Test
    void givenRequestWithoutLimitAndOffsetParametersWhenExecutionEndpointIsCalledThenTheDefaultValuesAreCorrect() {
        Mockito.when(executionService.getExecutionHeaders(any(OffsetDateTime.class), any(OffsetDateTime.class), any(Integer.class), any(Integer.class), any(String.class)))
                .thenReturn(new MatchedExecutionHeaders(new ArrayList<>(), 0));
        ExecutionsResponse response = given().contentType(ContentType.JSON).when().get("/executions?from=2000-01-01T00:00:00Z&to=2021-01-01T00:00:00Z").as(ExecutionsResponse.class);

        assertThat(response.getLimit()).isEqualTo(100);
        assertThat(response.getOffset()).isEqualTo(0);
        assertThat(response.getHeaders().size()).isEqualTo(0);
    }

    @Test
    void givenARequestWithoutTimeRangeParametersWhenExecutionEndpointIsCalledThenTheDefaultValuesAreUsed() {
        Mockito.when(executionService.getExecutionHeaders(any(OffsetDateTime.class), any(OffsetDateTime.class), any(Integer.class), any(Integer.class), any(String.class)))
                .thenReturn(new MatchedExecutionHeaders(new ArrayList<>(), 0));
        given().when().get("/executions").then().statusCode(200);
        given().when().get("/executions?from=2000-01-01").then().statusCode(200);
        given().when().get("/executions?to=2000-01-01").then().statusCode(200);
        given().when().get("/executions?from=2000-01-01T00:00:00Z").then().statusCode(200);
        given().when().get("/executions?to=2000-01-01T00:00:00Z").then().statusCode(200);
    }

    @Test
    void givenARequestWithoutTimeZoneInformationWhenExecutionEndpointIsCalledThenBadRequestIsReturned() {
        given().when().get("/executions?to=2000-01-01T00:00:00&from=2000-01-01T00:00:00Z").then().statusCode(400);
    }

    @Test
    void givenARequestWithInvalidPaginationParametersWhenExecutionEndpointIsCalledThenBadRequestIsReturned() {
        given().when().get("/executions?from=2000-01-01T00:00:00Z&to=2021-01-01T00:00:00Z&offset=-1").then().statusCode(400);
        given().when().get("/executions?from=2000-01-01T00:00:00Z&to=2021-01-01T00:00:00Z&limit=-1").then().statusCode(400);
    }

    @Test
    void givenARequestWithMalformedTimeRangeWhenExecutionEndpointIsCalledThenBadRequestIsReturned() {
        given().contentType(ContentType.JSON).when().get("/executions?from=2000-13-01&to=2021-01-01T00:00:00Z").then().statusCode(400);
        given().contentType(ContentType.JSON).when().get("/executions?from=2000-01-01T00:00:00Z&to=2021-13-01").then().statusCode(400);
        given().contentType(ContentType.JSON).when().get("/executions?from=NotADate&to=2021-01-01T00:00:00Z").then().statusCode(400);
        given().contentType(ContentType.JSON).when().get("/executions?from=2000-13-01T00:00:00Z&to=NotADate").then().statusCode(400);
    }

    @Test
    void givenARequestWhenExecutionEndpointIsCalledThenTheExecutionHeaderIsReturned() throws ParseException {
        Execution execution = new Decision("test1",
                "http://localhost:8081/model/service",
                "http://localhost:8081",
                OffsetDateTime.parse("2020-01-01T00:00:00Z",
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli(),
                true,
                "name",
                "model",
                "namespace",
                Collections.emptyList(),
                Collections.emptyList());
        Mockito.when(executionService.getExecutionHeaders(any(OffsetDateTime.class), any(OffsetDateTime.class),
                any(Integer.class), any(Integer.class), any(String.class)))
                .thenReturn(new MatchedExecutionHeaders(List.of(execution), 1));

        ExecutionsResponse response = given().contentType(ContentType.JSON).when().get("/executions?from=2000-01" +
                "-01T00:00:00Z&to=2021" +
                "-01-01T00:00:00Z").as(ExecutionsResponse.class);

        assertThat(response.getHeaders().size()).isEqualTo(1);
    }

    @Test
    void givenMoreResultsThanQueryLimitThenPaginationIsCorrect() throws ParseException {
        List<Execution> executions = generateExecutions(15);

        mockGetExecutionHeaders(executions, 0, 10);
        mockGetExecutionHeaders(executions, 5, 10);
        mockGetExecutionHeaders(executions, 10, 10);

        ExecutionsResponse response = given().contentType(ContentType.JSON)
                .when()
                .get("/executions?from=2000-01-01T00:00:00Z&to=2021-01-01T00:00:00Z&limit=10")
                .as(ExecutionsResponse.class);

        assertThat(response.getHeaders().size()).isEqualTo(10);
        assertThat(response.getTotal()).isEqualTo(15);
        assertThat(response.getOffset()).isEqualTo(0);
        assertThat(response.getLimit()).isEqualTo(10);

        response = given().contentType(ContentType.JSON)
                .when()
                .get("/executions?from=2000-01-01T00:00:00Z&to=2021-01-01T00:00:00Z&limit=10&offset=5")
                .as(ExecutionsResponse.class);

        assertThat(response.getHeaders().size()).isEqualTo(10);
        assertThat(response.getTotal()).isEqualTo(15);
        assertThat(response.getOffset()).isEqualTo(5);
        assertThat(response.getLimit()).isEqualTo(10);

        response = given().contentType(ContentType.JSON)
                .when()
                .get("/executions?from=2000-01-01T00:00:00Z&to=2021-01-01T00:00:00Z&limit=10&offset=10")
                .as(ExecutionsResponse.class);

        assertThat(response.getHeaders().size()).isEqualTo(5);
        assertThat(response.getTotal()).isEqualTo(15);
        assertThat(response.getOffset()).isEqualTo(10);
        assertThat(response.getLimit()).isEqualTo(10);
    }

    @Test
    void givenARequestWithExistingModelWhenModelEndpointIsCalledThenTheModelIsReturned() {
        DMNModelWithMetadata dmnModelWithMetadata = new DMNModelWithMetadata("groupId", "artifactId", "modelVersion",
                "dmnVersion", "name", "namespace", "definition");
        final Decision decision = mock(Decision.class);
        when(decision.getExecutedModelName()).thenReturn("name");
        when(decision.getExecutedModelNamespace()).thenReturn("namespace");
        when(executionService.getDecisionById(anyString())).thenReturn(decision);
        when(executionService.getModelById(any(DMNModelMetadata.class), eq(DMNModelWithMetadata.class))).thenReturn(dmnModelWithMetadata);

        DMNModelWithMetadata response = given().contentType(ContentType.TEXT).when().get("/executions/123/model").as(DMNModelWithMetadata.class);
        assertThat(response.getModel()).isEqualTo(dmnModelWithMetadata.getModel());
        assertThat(response.getGroupId()).isEqualTo(dmnModelWithMetadata.getGroupId());
        assertThat(response.getNamespace()).isEqualTo(dmnModelWithMetadata.getNamespace());
        assertThat(response.getName()).isEqualTo(dmnModelWithMetadata.getName());
        assertThat(response.getArtifactId()).isEqualTo(dmnModelWithMetadata.getArtifactId());
    }

    @Test
    void givenARequestWithoutExistingDecisionWhenModelEndpointIsCalledThenBadRequestIsReturned() {
        when(executionService.getDecisionById(anyString())).thenThrow(new IllegalArgumentException("Execution does not exist."));

        given().contentType(ContentType.TEXT).when().get("/executions/123/model").then().statusCode(400);
    }

    @Test
    void givenARequestWithoutExistingModelWhenModelEndpointIsCalledThenBadRequestIsReturned() {
        when(executionService.getModelById(any(DMNModelMetadata.class), eq(DMNModelWithMetadata.class))).thenThrow(new IllegalArgumentException("Model does not exist."));

        given().contentType(ContentType.TEXT).when().get("/executions/123/model").then().statusCode(400);
    }

    private List<Execution> generateExecutions(int size) {
        ArrayList<Execution> executions = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            executions.add(new Decision(String.format("test-%d", i),
                    "http://localhost:8081/model/service", "http://localhost:8081",
                    OffsetDateTime.parse("2020-01-01T00:00:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME).plusDays(i).toInstant().toEpochMilli(),
                    true,
                    "name",
                    "model",
                    "namespace",
                    Collections.emptyList(),
                    Collections.emptyList()));
        }
        return executions;
    }

    private void mockGetExecutionHeaders(List<Execution> executions, int offset, int limit) {
        Mockito.when(executionService.getExecutionHeaders(any(OffsetDateTime.class), any(OffsetDateTime.class), eq(limit), eq(offset), any(String.class)))
                .thenReturn(new MatchedExecutionHeaders(executions.subList(offset, Math.min(offset + limit, executions.size())), executions.size()));
    }
}
