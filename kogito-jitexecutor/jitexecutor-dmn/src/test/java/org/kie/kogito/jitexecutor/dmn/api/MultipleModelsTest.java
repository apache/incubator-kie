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
package org.kie.kogito.jitexecutor.dmn.api;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.dmn.api.core.DMNDecisionResult;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNResult;
import org.kie.kogito.jitexecutor.common.requests.MultipleResourcesPayload;
import org.kie.kogito.jitexecutor.common.requests.ResourceWithURI;
import org.kie.kogito.jitexecutor.dmn.requests.JITDMNPayload;
import org.kie.kogito.jitexecutor.dmn.responses.JITDMNDecisionResult;
import org.kie.kogito.jitexecutor.dmn.responses.JITDMNMessage;
import org.kie.kogito.jitexecutor.dmn.responses.JITDMNResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ResponseBody;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.kie.kogito.jitexecutor.dmn.TestingUtils.getModelFromIoUtils;
import static org.kie.kogito.jitexecutor.dmn.api.JITDMNResourceTest.EVALUATION_HIT_IDS_FIELD_NAME;
import static org.kie.kogito.jitexecutor.dmn.api.JITDMNResourceTest.buildMultipleHitContext;

@QuarkusTest
public class MultipleModelsTest {
    private static final Logger LOG = LoggerFactory.getLogger(MultipleModelsTest.class);

    private static final String IMPORTING_MODEL_URI = "invalid_models/DMNv1_x/multiple/importing.dmn";
    private static final String STDLIB_MODEL_URI = "valid_models/DMNv1_x/multiple/stdlib.dmn";
    private static final String SAMPLE_MODEL_URI = "valid_models/DMNv1_5/Sample.dmn";
    private static final String MULTIPLE_HIT_MODEL_URI = "valid_models/DMNv1_5/MultipleHitRules.dmn";
    private static ResourceWithURI importingModel;
    private static ResourceWithURI stdLibModel;
    private static ResourceWithURI sampleModel;
    private static ResourceWithURI multipleHitModel;

    private static final String XAIURI1 = "invalid_models/DMNv1_x/test.dmn";
    private static ResourceWithURI xaimodel1;

    private static final String CH11URI1 = "invalid_models/DMNv1_x/multiple/Chapter 11 Example.dmn";
    private static final String CH11URI2 = "valid_models/DMNv1_x/multiple/Financial.dmn";
    private static ResourceWithURI ch11model1;
    private static ResourceWithURI ch11model2;

    private static final ObjectMapper MAPPER;
    static {
        final var jitModule = new SimpleModule().addAbstractTypeMapping(DMNResult.class, JITDMNResult.class)
                .addAbstractTypeMapping(DMNDecisionResult.class, JITDMNDecisionResult.class)
                .addAbstractTypeMapping(DMNMessage.class, JITDMNMessage.class);

        MAPPER = new ObjectMapper()
                .registerModule(new Jdk8Module())
                .registerModule(jitModule);
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    private static final CollectionType LIST_OF_MSGS = MAPPER.getTypeFactory()
            .constructCollectionType(List.class,
                    JITDMNMessage.class);

    @BeforeAll
    public static void setup() throws IOException {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        importingModel = new ResourceWithURI(IMPORTING_MODEL_URI, getModelFromIoUtils(IMPORTING_MODEL_URI));
        stdLibModel = new ResourceWithURI(STDLIB_MODEL_URI, getModelFromIoUtils(STDLIB_MODEL_URI));
        sampleModel = new ResourceWithURI(SAMPLE_MODEL_URI, getModelFromIoUtils(SAMPLE_MODEL_URI));
        multipleHitModel = new ResourceWithURI(MULTIPLE_HIT_MODEL_URI, getModelFromIoUtils(MULTIPLE_HIT_MODEL_URI));
        xaimodel1 = new ResourceWithURI(XAIURI1, getModelFromIoUtils(XAIURI1));
        ch11model1 = new ResourceWithURI(CH11URI1, getModelFromIoUtils(CH11URI1));
        ch11model2 = new ResourceWithURI(CH11URI2, getModelFromIoUtils(CH11URI2));
    }

    @Test
    void testForm() {
        given()
                .contentType(ContentType.JSON)
                .body(new MultipleResourcesPayload(IMPORTING_MODEL_URI, List.of(importingModel, stdLibModel)))
                .when().post("/jitdmn/schema/form")
                .then()
                .statusCode(200)
                .body(containsString("InputSet"), containsString("x-dmn-type"), containsString("tPerson"));
    }

    @Test
    void testSchema() {
        given()
                .contentType(ContentType.JSON)
                .body(new MultipleResourcesPayload(IMPORTING_MODEL_URI, List.of(importingModel, stdLibModel)))
                .when().post("/jitdmn/schema")
                .then()
                .statusCode(200)
                .body(containsString("InputSet"), containsString("x-dmn-type"), containsString("tPerson"), containsString("mainURI"), containsString("URI"));
    }

    @Test
    void testjitEndpoint() {
        JITDMNPayload jitdmnpayload = new JITDMNPayload(IMPORTING_MODEL_URI, List.of(importingModel, stdLibModel), buildContext());
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn")
                .then()
                .statusCode(200)
                .body("'my decision'", is("Ciao, John Doe (age:47)."));
    }

    @Test
    void testjitdmnResultEndpoint() {
        JITDMNPayload jitdmnpayload = new JITDMNPayload(IMPORTING_MODEL_URI, List.of(importingModel, stdLibModel), buildContext());
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn/dmnresult")
                .then()
                .statusCode(200)
                .body("dmnContext.'my decision'", is("Ciao, John Doe (age:47)."));
    }

    @Test
    void testEvaluationHitIds() throws IOException {
        final String ruleId0 = "_1FA12B9F-288C-42E8-B77F-BE2D3702B7B6";
        final String ruleId1 = "_C8FA33B1-AF6E-4A59-B7B9-6FDF1F495C44";
        Map<String, Object> context = new HashMap<>();
        context.put("Credit Score", Map.of("FICO", 700));
        Map<String, Object> monthly = new HashMap<>();
        monthly.put("Income", 121233);
        monthly.put("Repayments", 33);
        monthly.put("Expenses", 123);
        monthly.put("Tax", 32);
        monthly.put("Insurance", 55);
        Map<String, Object> applicantData = new HashMap<>();
        applicantData.put("Age", 32);
        applicantData.put("Marital Status", "S");
        applicantData.put("Employment Status", "Employed");
        applicantData.put("Monthly", monthly);
        context.put("Applicant Data", applicantData);
        Map<String, Object> requestedProduct = new HashMap<>();
        requestedProduct.put("Type", "Special Loan");
        requestedProduct.put("Rate", 1);
        requestedProduct.put("Term", 2);
        requestedProduct.put("Amount", 333);
        context.put("Requested Product", requestedProduct);
        context.put("id", "_0A185BAC-7692-45FA-B722-7C86C626BD51");
        JITDMNPayload jitdmnpayload = new JITDMNPayload(SAMPLE_MODEL_URI, List.of(sampleModel), context);

        Response response = given().contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn/dmnresult");

        ResponseBody body = response.getBody();
        String responseString = body.asString();
        JsonNode retrieved = MAPPER.readTree(responseString);
        ArrayNode decisionResultsNode = (ArrayNode) retrieved.get("decisionResults");
        Iterable<JsonNode> iterable = () -> decisionResultsNode.elements();
        Stream<JsonNode> targetStream = StreamSupport.stream(iterable.spliterator(), false);
        ObjectNode decisionNode = (ObjectNode) targetStream.filter(node -> node.get("decisionName").asText().equals("Credit Score Rating")).findFirst().get();
        ObjectNode evaluationHitIdsNode = (ObjectNode) decisionNode.get(EVALUATION_HIT_IDS_FIELD_NAME);
        Assertions.assertThat(evaluationHitIdsNode).hasSize(1);
        Map<String, Integer> expectedEvaluationHitIds0 = Map.of(ruleId0, 1);
        evaluationHitIdsNode.fields().forEachRemaining(entry -> Assertions.assertThat(expectedEvaluationHitIds0).containsEntry(entry.getKey(), entry.getValue().asInt()));

        targetStream = StreamSupport.stream(iterable.spliterator(), false);
        decisionNode = (ObjectNode) targetStream.filter(node -> node.get("decisionName").asText().equals("Loan Pre-Qualification")).findFirst().get();
        evaluationHitIdsNode = (ObjectNode) decisionNode.get(EVALUATION_HIT_IDS_FIELD_NAME);
        Assertions.assertThat(evaluationHitIdsNode).hasSize(1);
        Map<String, Integer> expectedEvaluationHitIds1 = Map.of(ruleId1, 1);
        evaluationHitIdsNode.fields().forEachRemaining(entry -> Assertions.assertThat(expectedEvaluationHitIds1).containsEntry(entry.getKey(), entry.getValue().asInt()));
    }

    @Test
    void testjitdmnResultEndpointWithEvaluationHitIds() throws JsonProcessingException {
        JITDMNPayload jitdmnpayload = new JITDMNPayload(MULTIPLE_HIT_MODEL_URI, List.of(multipleHitModel), buildMultipleHitContext());
        final String rule0 = "_E5C380DA-AF7B-4401-9804-C58296EC09DD";
        final String rule1 = "_DFD65E8B-5648-4BFD-840F-8C76B8DDBD1A";
        final String rule2 = "_E80EE7F7-1C0C-4050-B560-F33611F16B05";
        String response = given().contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn/dmnresult")
                .then()
                .statusCode(200)
                .body(containsString("Statistics"),
                        containsString(EVALUATION_HIT_IDS_FIELD_NAME),
                        containsString(rule0),
                        containsString(rule1),
                        containsString(rule2))
                .extract()
                .asString();
        JsonNode retrieved = MAPPER.readTree(response);
        ArrayNode decisionResultsNode = (ArrayNode) retrieved.get("decisionResults");
        ObjectNode decisionNode = (ObjectNode) decisionResultsNode.get(0);
        ObjectNode evaluationHitIdsNode = (ObjectNode) decisionNode.get(EVALUATION_HIT_IDS_FIELD_NAME);
        Assertions.assertThat(evaluationHitIdsNode).hasSize(3);

        final Map<String, Integer> expectedEvaluationHitIds = Map.of(rule0, 3, rule1, 2, rule2, 1);
        evaluationHitIdsNode.fields().forEachRemaining(entry -> Assertions.assertThat(expectedEvaluationHitIds).containsEntry(entry.getKey(), entry.getValue().asInt()));
    }

    @Test
    void testValidation() throws IOException {
        String response = given()
                .contentType(ContentType.JSON)
                .body(new MultipleResourcesPayload(IMPORTING_MODEL_URI, List.of(importingModel, stdLibModel)))
                .when()
                .post("/jitdmn/validate")
                .then()
                .statusCode(200)
                .body(containsString("Variable named 'my decision' is missing its type reference"))
                .extract()
                .asString();
        LOG.info("Validate response: {}", response);
        List<JITDMNMessage> messages = MAPPER.readValue(response, LIST_OF_MSGS);
        assertThat(messages.size()).isEqualTo(1);
        assertThat(messages.get(0)).hasFieldOrPropertyWithValue("path", IMPORTING_MODEL_URI);
    }

    @Test
    void testjitExplainabilityEndpoint() {
        JITDMNPayload jitdmnpayload = new JITDMNPayload(XAIURI1, List.of(xaimodel1, importingModel, stdLibModel), Map.of("FICO Score", 800, "DTI Ratio", .1, "PITI Ratio", .1));
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn/evaluateAndExplain")
                .then()
                .statusCode(200)
                .body(containsString("dmnResult"), containsString("saliencies"), containsString("xls2dmn"), containsString("featureName"));
    }

    @Test
    void testjitEndpointCH11() {
        JITDMNPayload jitdmnpayload = new JITDMNPayload(CH11URI1, List.of(ch11model1, ch11model2), buildCH11Context());
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn")
                .then()
                .statusCode(200)
                .body("Strategy", is("THROUGH"));
    }

    @Test
    void testjitdmnResultEndpointCH11() {
        JITDMNPayload jitdmnpayload = new JITDMNPayload(CH11URI1, List.of(ch11model1, ch11model2), buildCH11Context());
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn/dmnresult")
                .then()
                .statusCode(200)
                .body("dmnContext.Strategy", is("THROUGH"));
    }

    @Test
    void testjitdmnResultEndpointCH11_withErrors() throws Exception {
        Map<String, Object> context = new HashMap<>(); // will omit `Applicant data` intentionally.
        context.put("Bureau data", Map.of("Bankrupt", false,
                "CreditScore", 600));
        context.put("Requested product", Map.of("ProductType", "STANDARD LOAN",
                "Rate", 0.08d,
                "Term", 36,
                "Amount", 100_00));
        JITDMNPayload jitdmnpayload = new JITDMNPayload(CH11URI1, List.of(ch11model1, ch11model2), context);
        String response = given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn/dmnresult")
                .then()
                .statusCode(200)
                .body("dmnContext.'Required monthly installment'", notNullValue())
                .extract()
                .asString();
        JITDMNResult result = MAPPER.readValue(response, JITDMNResult.class);
        assertThat(result.getMessages()).isNotEmpty().allMatch(m -> m.getPath().equals(CH11URI1));
    }

    private Map<String, Object> buildContext() {
        return Map.of("a person", Map.of("full name", "John Doe", "age", 47));
    }

    private Map<String, Object> buildCH11Context() {
        Map<String, Object> context = new HashMap<>();
        context.put("Applicant data", Map.of("Age", 51,
                "MartitalStatus", "M", // typo is present in DMNv1.3
                "EmploymentStatus", "EMPLOYED",
                "ExistingCustomer", false,
                "Monthly", Map.of("Income", 100_000,
                        "Repayments", 2_500,
                        "Expenses", 10_000)));
        context.put("Bureau data", Map.of("Bankrupt", false,
                "CreditScore", 600));
        context.put("Requested product", Map.of("ProductType", "STANDARD LOAN",
                "Rate", 0.08d,
                "Term", 36,
                "Amount", 100_00));
        context.put("Supporting documents", null);
        return context;
    }

}
