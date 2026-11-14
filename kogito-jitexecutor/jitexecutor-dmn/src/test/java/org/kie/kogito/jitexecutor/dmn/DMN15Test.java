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
package org.kie.kogito.jitexecutor.dmn;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.kogito.jitexecutor.common.requests.MultipleResourcesPayload;
import org.kie.kogito.jitexecutor.common.requests.ResourceWithURI;
import org.kie.kogito.jitexecutor.dmn.requests.JITDMNPayload;
import org.kie.kogito.jitexecutor.dmn.responses.JITDMNMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.type.CollectionType;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.kie.kogito.jitexecutor.dmn.TestingUtils.MAPPER;
import static org.kie.kogito.jitexecutor.dmn.TestingUtils.getModel;
import static org.kie.kogito.jitexecutor.dmn.TestingUtils.getModelFromIoUtils;

@QuarkusTest
class DMN15Test {

    private static final Logger LOG = LoggerFactory.getLogger(DMN15Test.class);

    private static final CollectionType LIST_OF_MSGS = MAPPER.getTypeFactory()
            .constructCollectionType(List.class,
                    JITDMNMessage.class);

    @BeforeAll
    public static void setup() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void unnamedImportWithHrefNamespace() throws IOException {
        commonUnnamedImport("valid_models/DMNv1_5/Importing_EmptyNamed_Model_With_Href_Namespace.dmn",
                "valid_models/DMNv1_5/Imported_Model_Unamed.dmn");
    }

    @Test
    void unnamedImportWithoutHrefNamespace() throws IOException {
        commonUnnamedImport("valid_models/DMNv1_5/Importing_EmptyNamed_Model_Without_Href_Namespace.dmn",
                "valid_models/DMNv1_5/Imported_Model_Unamed.dmn");
    }

    @Test
    void collectionTypeConstraintSucceed() throws IOException {
        String modelRef = "valid_models/DMNv1_5/TypeConstraintsChecks.dmn";
        ResourceWithURI model = new ResourceWithURI(modelRef, getModelFromIoUtils(modelRef));
        Map<String, Object> context =
                Map.of("p1", Map.of("Name", "P3", "Interests", Arrays.asList("Ski")));
        JITDMNPayload jitdmnpayload = new JITDMNPayload(modelRef, List.of(model),
                context);
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn")
                .then()
                .statusCode(200)
                .body("'MyDecision'", is("The Person P3 likes 1 thing(s)."));
    }

    @Test
    void collectionTypeConstraintFails() throws IOException {
        String modelRef = "valid_models/DMNv1_5/TypeConstraintsChecks.dmn";
        ResourceWithURI model = new ResourceWithURI(modelRef, getModelFromIoUtils(modelRef));
        Map<String, Object> context =
                Map.of("p1", Map.of("Name", "P3", "Interests", Arrays.asList("Ski", "Golf")));
        JITDMNPayload jitdmnpayload = new JITDMNPayload(modelRef, List.of(model),
                context);
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn")
                .then()
                .statusCode(200)
                .body("'MyDecision'", nullValue());
    }

    @Test
    void collectionAllowedValuesSucceed() throws IOException {
        String modelRef = "valid_models/DMNv1_5/AllowedValuesChecksInsideCollection.dmn";
        ResourceWithURI model = new ResourceWithURI(modelRef, getModelFromIoUtils(modelRef));
        Map<String, Object> context =
                Map.of("p1", Map.of("Name", "P3", "Interests", Arrays.asList("Golf")));
        JITDMNPayload jitdmnpayload = new JITDMNPayload(modelRef, List.of(model),
                context);
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn")
                .then()
                .statusCode(200)
                .body("'MyDecision'", is("The Person P3 likes 1 thing(s)."));

        context =
                Map.of("p1", Map.of("Name", "P3", "Interests", Arrays.asList("Golf", "Jogging")));
        jitdmnpayload = new JITDMNPayload(modelRef, List.of(model),
                context);
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn")
                .then()
                .statusCode(200)
                .body("'MyDecision'", is("The Person P3 likes 2 thing(s)."));
    }

    @Test
    void collectionAllowedValuesFails() throws IOException {
        String modelRef = "valid_models/DMNv1_5/AllowedValuesChecksInsideCollection.dmn";
        ResourceWithURI model = new ResourceWithURI(modelRef, getModelFromIoUtils(modelRef));
        Map<String, Object> context =
                Map.of("p1", Map.of("Name", "P3", "Interests", Arrays.asList("Ski")));
        JITDMNPayload jitdmnpayload = new JITDMNPayload(modelRef, List.of(model),
                context);
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn")
                .then()
                .statusCode(200)
                .body("'MyDecision'", nullValue());
    }

    @Test
    void forLoopDatesEvaluate() throws IOException {
        String modelFileName = "valid_models/DMNv1_5/ForLoopDatesEvaluate.dmn";
        validate(getModelFromIoUtils(modelFileName));
        String model = getModel(modelFileName);
        Map<String, Object> expectedValues = Map.of("forloopdates[0]", "2021-01-02",
                "forloopdates[1]", "2021-01-03",
                "forloopdates[2]", "2021-01-04");
        endpoint(model, expectedValues);
        expectedValues = Map.of("decisionResults.result[0][0]", "2021-01-02",
                "decisionResults.result[0][1]", "2021-01-03",
                "decisionResults.result[0][2]", "2021-01-04");
        result(model, expectedValues);
    }

    @Test
    void listReplaceEvaluate() throws IOException {
        String modelFileName = "valid_models/DMNv1_5/ListReplaceEvaluate.dmn";
        validate(getModelFromIoUtils(modelFileName));
        String model = getModel(modelFileName);
        Map<String, Object> expectedValues = Map.of("listreplacenumbers[0]", 2,
                "listreplacenumbers[1]", 4,
                "listreplacenumbers[2]", 6,
                "listreplacenumbers[3]", 8);
        endpoint(model, expectedValues);
        expectedValues = Map.of("decisionResults.result[0][0]", 2,
                "decisionResults.result[0][1]", 4,
                "decisionResults.result[0][2]", 6,
                "decisionResults.result[0][3]", 8);
        result(model, expectedValues);
    }

    @Test
    void negationOfDurationEvaluate() throws IOException {
        String modelFileName = "valid_models/DMNv1_5/NegationOfDurationEvaluate.dmn";
        validate(getModelFromIoUtils(modelFileName));
        String model = getModel(modelFileName);
        Map<String, Object> expectedValues = Map.of("durationnegation", true);
        endpoint(model, expectedValues);
        expectedValues = Map.of("decisionResults.result[0]", true);
        result(model, expectedValues);
    }

    @Test
    void dateToDateTimeFunction() throws IOException {
        String modelFileName = "valid_models/DMNv1_5/DateToDateTimeFunction.dmn";
        validate(getModelFromIoUtils(modelFileName));
        String model = getModel(modelFileName);
        Map<String, Object> expectedValues = Map.of("normal", "function normal( a, b )",
                "usingNormal", "2021-05-31T00:00:00Z");
        endpoint(model, expectedValues);
        result(model, Map.of("decisionResults.result[0]", "2021-05-31T00:00:00Z"));
    }

    private void commonUnnamedImport(String importingModelRef, String importedModelRef) throws IOException {
        ResourceWithURI model1 = new ResourceWithURI(importingModelRef, getModelFromIoUtils(importingModelRef));
        ResourceWithURI model2 = new ResourceWithURI(importedModelRef, getModelFromIoUtils(importedModelRef));
        Map<String, Object> context =
                Map.of("A Person", Map.of("name", "John", "age", 47));
        JITDMNPayload jitdmnpayload = new JITDMNPayload(importingModelRef, List.of(model1, model2),
                context);
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn")
                .then()
                .statusCode(200)
                .body("'Local Hello'", is("function Local Hello( Person )"))
                .body("'Say Hello'", is("function Say Hello( Person )"));

        String response = given()
                .contentType(ContentType.JSON)
                .body(new MultipleResourcesPayload(importingModelRef, List.of(model1, model2)))
                .when()
                .post("/jitdmn/validate")
                .then()
                .statusCode(200)
                .extract()
                .asString();
        LOG.debug("Validate response: {}", response);
        List<JITDMNMessage> messages = MAPPER.readValue(response, LIST_OF_MSGS);
        assertThat(messages.size()).isEqualTo(0);

        jitdmnpayload = new JITDMNPayload(importingModelRef, List.of(model1, model2), context);
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when()
                .post("/jitdmn/dmnresult")
                .then()
                .statusCode(200)
                .body("decisionResults.result[0]", is("Hello John!"))
                .body("decisionResults.result[1]", is("Local Hello John!"));
    }

    private void result(String model, Map<String, Object> expectedValues) {
        String payload = "{ \"model\": " + model + ", \"context\": {\n" +
                "}}";
        ValidatableResponse validatableResponse = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/jitdmn/dmnresult")
                .then()
                .statusCode(200);
        expectedValues.forEach((key, value) -> validatableResponse.body(key, is(value)));
    }

    private void endpoint(String model, Map<String, Object> expectedValues) {
        String payload = "{ \"model\": " + model + ", \"context\": {\n" +
                "}}";
        ValidatableResponse validatableResponse = given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(payload)
                .post("/jitdmn")
                .then()
                .statusCode(200);
        expectedValues.forEach((key, value) -> validatableResponse.body(key, is(value)));
    }

    private void validate(String model) throws IOException {
        String response = given()
                .contentType(ContentType.XML)
                .body(model)
                .when()
                .post("/jitdmn/validate")
                .then()
                .statusCode(200)
                .extract()
                .asString();
        LOG.debug("Validate response: {}", response);
        List<JITDMNMessage> messages = MAPPER.readValue(response, LIST_OF_MSGS);
        assertThat(messages.size()).isEqualTo(0);
    }
}
