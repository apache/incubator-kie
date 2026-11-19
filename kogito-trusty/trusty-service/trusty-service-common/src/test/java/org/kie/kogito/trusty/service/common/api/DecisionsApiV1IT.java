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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;
import org.kie.kogito.ModelDomain;
import org.kie.kogito.explainability.api.NamedTypedValue;
import org.kie.kogito.tracing.event.message.Message;
import org.kie.kogito.tracing.event.message.MessageCategory;
import org.kie.kogito.tracing.event.message.MessageExceptionField;
import org.kie.kogito.tracing.event.message.MessageLevel;
import org.kie.kogito.tracing.typedvalue.TypedValue;
import org.kie.kogito.tracing.typedvalue.UnitValue;
import org.kie.kogito.trusty.service.common.TrustyService;
import org.kie.kogito.trusty.service.common.responses.decision.DecisionHeaderResponse;
import org.kie.kogito.trusty.service.common.responses.decision.DecisionOutcomesResponse;
import org.kie.kogito.trusty.service.common.responses.decision.DecisionStructuredInputsResponse;
import org.kie.kogito.trusty.storage.api.model.decision.Decision;
import org.kie.kogito.trusty.storage.api.model.decision.DecisionInput;
import org.kie.kogito.trusty.storage.api.model.decision.DecisionOutcome;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class DecisionsApiV1IT {

    private static final String TEST_EXECUTION_ID = "executionId";
    private static final String TEST_MODEL_NAME = "testModel";
    private static final String TEST_MODEL_NAMESPACE = "testNamespace";
    private static final String TEST_SOURCE_URL = "http://localhost:8080/" + TEST_MODEL_NAME;
    private static final String TEST_OUTCOME_ID = "FirstOutcome";
    private static final long TEST_EXECUTION_TIMESTAMP = 1591692950000L;
    private static final OffsetDateTime TEST_EXECUTION_DATE =
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(TEST_EXECUTION_TIMESTAMP), ZoneId.of("UTC"));

    @InjectMock
    TrustyService trustyService;

    @Test
    void testGetExecutionById() throws Exception {
        assertGetExecutionByIdCorrectResponse(ListStatus.FULL, ListStatus.FULL);
        assertGetExecutionByIdCorrectResponse(ListStatus.FULL, ListStatus.EMPTY);
        assertGetExecutionByIdCorrectResponse(ListStatus.FULL, ListStatus.NULL);
        assertGetExecutionByIdCorrectResponse(ListStatus.EMPTY, ListStatus.FULL);
        assertGetExecutionByIdCorrectResponse(ListStatus.EMPTY, ListStatus.EMPTY);
        assertGetExecutionByIdCorrectResponse(ListStatus.EMPTY, ListStatus.NULL);
        assertGetExecutionByIdCorrectResponse(ListStatus.NULL, ListStatus.FULL);
        assertGetExecutionByIdCorrectResponse(ListStatus.NULL, ListStatus.EMPTY);
        assertGetExecutionByIdCorrectResponse(ListStatus.NULL, ListStatus.NULL);
        assertBadRequestWithoutDecision("");
    }

    @Test
    void testGetStructuredInputs() throws Exception {
        assertGetStructuredInputsCorrectFullResponse(ListStatus.FULL);
        assertGetStructuredInputsCorrectFullResponse(ListStatus.EMPTY);
        assertGetStructuredInputsCorrectFullResponse(ListStatus.NULL);
        assertGetStructuredInputsCorrectEmptyResponse(ListStatus.FULL);
        assertGetStructuredInputsCorrectEmptyResponse(ListStatus.EMPTY);
        assertGetStructuredInputsCorrectEmptyResponse(ListStatus.NULL);
        assertBadRequestWithDecision("/structuredInputs", ListStatus.NULL, ListStatus.FULL);
        assertBadRequestWithDecision("/structuredInputs", ListStatus.NULL, ListStatus.EMPTY);
        assertBadRequestWithDecision("/structuredInputs", ListStatus.NULL, ListStatus.NULL);
        assertBadRequestWithoutDecision("/structuredInputs");
    }

    @Test
    void testGetOutcomes() throws Exception {
        assertGetOutcomesCorrectFullResponse(ListStatus.FULL);
        assertGetOutcomesCorrectEmptyResponse(ListStatus.FULL);
        assertGetOutcomesCorrectNullResponse(ListStatus.FULL);
        assertGetOutcomesCorrectFullResponse(ListStatus.EMPTY);
        assertGetOutcomesCorrectEmptyResponse(ListStatus.EMPTY);
        assertGetOutcomesCorrectNullResponse(ListStatus.EMPTY);
        assertGetOutcomesCorrectFullResponse(ListStatus.NULL);
        assertGetOutcomesCorrectEmptyResponse(ListStatus.NULL);
        assertGetOutcomesCorrectNullResponse(ListStatus.NULL);
        assertBadRequestWithoutDecision("/structuredInputs");
    }

    @Test
    void testGetOutcomeById() throws Exception {
        assertGetOutcomeByIdCorrectResponse(ListStatus.FULL);
        assertBadRequestWithDecision("/outcomes/" + TEST_OUTCOME_ID, ListStatus.FULL, ListStatus.EMPTY);
        assertBadRequestWithDecision("/outcomes/" + TEST_OUTCOME_ID, ListStatus.FULL, ListStatus.NULL);
        assertGetOutcomeByIdCorrectResponse(ListStatus.EMPTY);
        assertBadRequestWithDecision("/outcomes/" + TEST_OUTCOME_ID, ListStatus.EMPTY, ListStatus.EMPTY);
        assertBadRequestWithDecision("/outcomes/" + TEST_OUTCOME_ID, ListStatus.EMPTY, ListStatus.NULL);
        assertGetOutcomeByIdCorrectResponse(ListStatus.NULL);
        assertBadRequestWithDecision("/outcomes/" + TEST_OUTCOME_ID, ListStatus.NULL, ListStatus.EMPTY);
        assertBadRequestWithDecision("/outcomes/" + TEST_OUTCOME_ID, ListStatus.NULL, ListStatus.NULL);
        assertBadRequestWithoutDecision("/structuredInputs");
    }

    private void assertBadRequestWithDecision(String path, ListStatus inputsStatus, ListStatus outcomesStatus) throws Exception {
        mockServiceWithDecision(inputsStatus, outcomesStatus);
        get(path).then().statusCode(400);
    }

    private void assertBadRequestWithoutDecision(String path) {
        mockServiceWithoutDecision();
        get(path).then().statusCode(400);
    }

    private void assertGetExecutionByIdCorrectResponse(ListStatus inputsStatus, ListStatus outcomesStatus) throws Exception {
        mockServiceWithDecision(inputsStatus, outcomesStatus);
        DecisionHeaderResponse response = get().as(DecisionHeaderResponse.class);
        assertExecutionHeaderResponse(buildExecutionHeaderResponse(), response);
    }

    private void assertGetOutcomeByIdCorrectResponse(ListStatus inputsStatus) throws Exception {
        mockServiceWithDecision(inputsStatus, ListStatus.FULL);
        DecisionOutcome response = get("/outcomes/" + TEST_OUTCOME_ID).as(DecisionOutcome.class);
        assertDecisionOutcomeResponse(buildDecisionOutcomeResponse(), response);
    }

    private void assertGetOutcomesCorrectEmptyResponse(ListStatus inputsStatus) throws Exception {
        mockServiceWithDecision(inputsStatus, ListStatus.EMPTY);
        DecisionOutcomesResponse response = get("/outcomes").as(DecisionOutcomesResponse.class);
        assertDecisionOutcomesResponse(buildDecisionOutcomesResponse(ListStatus.EMPTY), response);
    }

    private void assertGetOutcomesCorrectFullResponse(ListStatus inputsStatus) throws Exception {
        mockServiceWithDecision(inputsStatus, ListStatus.FULL);
        DecisionOutcomesResponse response = get("/outcomes").as(DecisionOutcomesResponse.class);
        assertDecisionOutcomesResponse(buildDecisionOutcomesResponse(ListStatus.FULL), response);
    }

    private void assertGetOutcomesCorrectNullResponse(ListStatus inputsStatus) throws Exception {
        mockServiceWithDecision(inputsStatus, ListStatus.NULL);
        DecisionOutcomesResponse response = get("/outcomes").as(DecisionOutcomesResponse.class);
        assertDecisionOutcomesResponse(buildDecisionOutcomesResponse(ListStatus.NULL), response);
    }

    private void assertGetStructuredInputsCorrectEmptyResponse(ListStatus outcomesStatus) throws Exception {
        mockServiceWithDecision(ListStatus.EMPTY, outcomesStatus);
        DecisionStructuredInputsResponse response = get("/structuredInputs").as(DecisionStructuredInputsResponse.class);
        assertDecisionStructuredInputResponse(buildDecisionStructuredInputsResponse(ListStatus.EMPTY), response);
    }

    private void assertGetStructuredInputsCorrectFullResponse(ListStatus outcomesStatus) throws Exception {
        mockServiceWithDecision(ListStatus.FULL, outcomesStatus);
        DecisionStructuredInputsResponse response = get("/structuredInputs").as(DecisionStructuredInputsResponse.class);
        assertDecisionStructuredInputResponse(buildDecisionStructuredInputsResponse(ListStatus.FULL), response);
    }

    private <T> void assertCollection(Collection<T> expected, Collection<T> actual, BiConsumer<T, T> itemAssertor) {
        if (expected == null) {
            assertThat(actual).isNull();
            return;
        }
        assertThat(actual.size()).isSameAs(expected.size());
        Iterator<T> itExpected = expected.iterator();
        Iterator<T> itActual = actual.iterator();
        while (itExpected.hasNext() && itActual.hasNext()) {
            itemAssertor.accept(itExpected.next(), itActual.next());
        }
    }

    private void assertDecisionOutcomeResponse(DecisionOutcome expected, DecisionOutcome actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.getOutcomeId()).isEqualTo(expected.getOutcomeId());
        assertThat(actual.getOutcomeName()).isEqualTo(expected.getOutcomeName());
        assertThat(actual.getEvaluationStatus()).isEqualTo(expected.getEvaluationStatus());
        assertTypedValueResponse(expected, actual);
        assertCollection(expected.getOutcomeInputs(), actual.getOutcomeInputs(), this::assertTypedValueResponse);
        assertCollection(expected.getMessages(), actual.getMessages(), this::assertMessageResponse);
    }

    private void assertDecisionOutcomesResponse(DecisionOutcomesResponse expected, DecisionOutcomesResponse actual) {
        assertThat(actual).isNotNull();
        assertExecutionHeaderResponse(expected.getHeader(), actual.getHeader());
        assertCollection(expected.getOutcomes(), actual.getOutcomes(), this::assertDecisionOutcomeResponse);
    }

    private void assertDecisionStructuredInputResponse(DecisionStructuredInputsResponse expected,
            DecisionStructuredInputsResponse actual) {
        assertThat(actual).isNotNull();
        assertCollection(expected.getInputs(), actual.getInputs(), this::assertTypedValueResponse);
    }

    private void assertExecutionHeaderResponse(DecisionHeaderResponse expected, DecisionHeaderResponse actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.getExecutionType()).isSameAs(expected.getExecutionType());
        assertThat(actual.getExecutionId()).isEqualTo(expected.getExecutionId());
        assertThat(actual.getExecutionDate()).isEqualTo(expected.getExecutionDate());
        assertThat(actual.getExecutorName()).isEqualTo(expected.getExecutorName());
        assertThat(actual.hasSucceeded()).isEqualTo(expected.hasSucceeded());
        assertThat(actual.getExecutedModelName()).isEqualTo(expected.getExecutedModelName());
        assertThat(actual.getExecutedModelNamespace()).isEqualTo(expected.getExecutedModelNamespace());
    }

    private void assertMessageResponse(Message expected, Message actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.getLevel()).isEqualTo(expected.getLevel());
        assertThat(actual.getCategory()).isEqualTo(expected.getCategory());
        assertThat(actual.getType()).isEqualTo(expected.getType());
        assertThat(actual.getSourceId()).isEqualTo(expected.getSourceId());
        assertThat(actual.getText()).isEqualTo(expected.getText());
        assertMessageExceptionField(expected.getException(), actual.getException());
    }

    private void assertMessageExceptionField(MessageExceptionField expected, MessageExceptionField actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.getMessage()).isEqualTo(expected.getMessage());
        assertThat(actual.getClassName()).isEqualTo(expected.getClassName());
        if (expected.getCause() == null) {
            assertThat(actual.getCause()).isNull();
        } else {
            assertMessageExceptionField(expected.getCause(), actual.getCause());
        }
    }

    private void assertTypedValueResponse(DecisionInput expected, DecisionInput actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.getName()).isEqualTo(expected.getName());
        assertThat(actual.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(actual.getValue().getKind()).isEqualTo(expected.getValue().getKind());
        assertThat(actual.getValue().getType()).isEqualTo(expected.getValue().getType());
        assertThat(actual.getValue().toUnit().getValue()).isEqualTo(expected.getValue().toUnit().getValue());
    }

    private void assertTypedValueResponse(DecisionOutcome expected, DecisionOutcome actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.getOutcomeName()).isEqualTo(expected.getOutcomeName());
        assertThat(actual.getOutcomeResult().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(actual.getOutcomeResult().getKind()).isEqualTo(expected.getOutcomeResult().getKind());
        assertThat(actual.getOutcomeResult().getType()).isEqualTo(expected.getOutcomeResult().getType());
        assertThat(actual.getOutcomeResult().toUnit().getValue()).isEqualTo(expected.getOutcomeResult().toUnit().getValue());
    }

    private void assertTypedValueResponse(NamedTypedValue expected, NamedTypedValue actual) {
        assertThat(actual).isNotNull();
        assertThat(actual.getName()).isEqualTo(expected.getName());
        assertThat(actual.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(actual.getValue().getKind()).isEqualTo(expected.getValue().getKind());
        assertThat(actual.getValue().getType()).isEqualTo(expected.getValue().getType());
        assertThat(actual.getValue().toUnit().getValue()).isEqualTo(expected.getValue().toUnit().getValue());
    }

    private Decision buildValidDecision(ListStatus inputsStatus, ListStatus outcomesStatus) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        Decision decision = new Decision();
        decision.setExecutionId(TEST_EXECUTION_ID);
        decision.setSourceUrl(TEST_SOURCE_URL);
        decision.setExecutionTimestamp(TEST_EXECUTION_TIMESTAMP);
        decision.setSuccess(true);
        decision.setExecutedModelName(TEST_MODEL_NAME);
        decision.setExecutedModelNamespace(TEST_MODEL_NAMESPACE);

        switch (inputsStatus) {
            case EMPTY:
                decision.setInputs(List.of());
                break;

            case FULL:
                decision.setInputs(List.of(
                        new DecisionInput("1", "first", new UnitValue("string", "string",
                                mapper.readTree("\"Hello\""))),
                        new DecisionInput("2", "second", new UnitValue("number", "number",
                                mapper.readTree("12345")))));
        }

        switch (outcomesStatus) {
            case EMPTY:
                decision.setOutcomes(List.of());
                break;

            case FULL:
                decision.setOutcomes(List.of(
                        new DecisionOutcome(
                                TEST_OUTCOME_ID, "ONE", "SUCCEEDED",
                                new UnitValue("string", "string", mapper.readTree("\"The First " +
                                        "Outcome\"")),
                                Collections.emptyList(),
                                List.of(getMessage(MessageLevel.WARNING,
                                        MessageCategory.INTERNAL, "TEST", "testSrc", "Test message",
                                        getMessageExceptionField("TestException", "Test exception message",
                                                getMessageExceptionField(
                                                        "TestExceptionCause",
                                                        "Test exception " +
                                                                "cause " +
                                                                "message",
                                                        null)))))));
        }

        return decision;
    }

    private DecisionOutcome buildDecisionOutcomeResponse() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return new DecisionOutcome(
                TEST_OUTCOME_ID, "ONE", "SUCCEEDED",
                new UnitValue("string", "string", mapper.readTree("\"The First " +
                        "Outcome\"")),
                Collections.emptyList(),
                List.of(getMessage(MessageLevel.WARNING,
                        MessageCategory.INTERNAL,
                        "TEST",
                        "testSrc",
                        "Test message",
                        getMessageExceptionField("TestException", "Test exception message",
                                getMessageExceptionField("TestExceptionCause", "Test " +
                                        "exception cause message", null)))));
    }

    private Message getMessage(MessageLevel messageLevel, MessageCategory messageCategory, String type,
            String sourceId, String text, MessageExceptionField exception) {
        return new Message(messageLevel,
                messageCategory, type, sourceId, text, exception,
                ModelDomain.DECISION) {
        };
    }

    private MessageExceptionField getMessageExceptionField(String className, String message,
            MessageExceptionField cause) {
        return new MessageExceptionField(className, message, cause);
    }

    private DecisionOutcomesResponse buildDecisionOutcomesResponse(ListStatus outcomesStatus) throws JsonProcessingException {
        switch (outcomesStatus) {
            case NULL:
                return new DecisionOutcomesResponse(buildExecutionHeaderResponse(), null);
            case EMPTY:
                return new DecisionOutcomesResponse(buildExecutionHeaderResponse(), Collections.emptyList());
            case FULL:
                return new DecisionOutcomesResponse(buildExecutionHeaderResponse(),
                        List.of(buildDecisionOutcomeResponse()));
        }
        throw new IllegalStateException();
    }

    private DecisionStructuredInputsResponse buildDecisionStructuredInputsResponse(ListStatus inputsStatus) throws JsonProcessingException {
        switch (inputsStatus) {
            case NULL:
                return new DecisionStructuredInputsResponse(null);
            case EMPTY:
                return new DecisionStructuredInputsResponse(Collections.emptyList());
            case FULL:
                ObjectMapper mapper = new ObjectMapper();
                return new DecisionStructuredInputsResponse(List.of(
                        new DecisionInput("first", "first",
                                new UnitValue("string", "string", mapper.readTree(
                                        "\"Hello\""))),
                        new DecisionInput("second", "second",
                                new UnitValue("number", "number", mapper.readTree(
                                        "12345")))));
        }
        throw new IllegalStateException();
    }

    private DecisionHeaderResponse buildExecutionHeaderResponse() {
        return new DecisionHeaderResponse(
                TEST_EXECUTION_ID,
                TEST_EXECUTION_DATE,
                true,
                null,
                TEST_MODEL_NAME,
                TEST_MODEL_NAMESPACE);
    }

    private Response get() {
        return get("");
    }

    private Response get(String endpoint) {
        return given()
                .filter(new ResponseLoggingFilter())
                .contentType(ContentType.JSON)
                .when()
                .get("/executions/decisions/" + TEST_EXECUTION_ID + endpoint);
    }

    private void mockServiceWithDecision(ListStatus inputsStatus, ListStatus outcomesStatus) throws Exception {
        when(trustyService.getDecisionById(eq(TEST_EXECUTION_ID))).thenReturn(buildValidDecision(inputsStatus,
                outcomesStatus));
    }

    private void mockServiceWithoutDecision() {
        when(trustyService.getDecisionById(anyString())).thenThrow(new IllegalArgumentException("Execution does not " +
                "exist."));
    }

    private enum ListStatus {
        FULL,
        EMPTY,
        NULL
    }
}
