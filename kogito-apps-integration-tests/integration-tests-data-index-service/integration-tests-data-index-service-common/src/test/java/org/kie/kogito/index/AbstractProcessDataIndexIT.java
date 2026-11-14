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
package org.kie.kogito.index;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.JsonConfig;
import io.restassured.http.ContentType;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.greaterThan;

public abstract class AbstractProcessDataIndexIT {

    protected static Duration TIMEOUT = Duration.ofSeconds(30);

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        JsonConfig jsonConfig = JsonConfig.jsonConfig().numberReturnType(JsonPathConfig.NumberReturnType.DOUBLE);

        RestAssured.config = RestAssured.config().jsonConfig(jsonConfig);
    }

    RequestSpecification spec;

    ObjectMapper mapper = new ObjectMapper();

    public abstract String getDataIndexURL();

    public boolean validateDomainData() {
        return true;
    }

    public boolean validateGetProcessInstanceSource() {
        return false;
    }

    public RequestSpecification dataIndexSpec() {
        if (spec == null) {
            spec = new RequestSpecBuilder().setBaseUri(getDataIndexURL()).build();
        }
        return spec;
    }

    protected ValidatableResponse addCheck(ValidatableResponse res) {
        return res.body("data.ProcessDefinitions[0].source", notNullValue());
    }

    @Test
    public void testProcessInstanceEvents() throws IOException {
        String pId = given()
                .contentType(ContentType.JSON)
                .body("{\"traveller\" : {\"firstName\" : \"Darth\",\"lastName\" : \"Vader\",\"email\" : \"darth.vader@deathstar.com\",\"nationality\" : \"Tatooine\", " +
                        "\"testDate\" : \"2022-03-09T23:00:00Z\", " + "    \"testInstant\": \"2022-03-10T16:15:50Z\", " +
                        " \"testInteger\":  2147483641 ," + " \"testLong\": 8223372036854775802," +
                        " \"testFloat\": 12028234663852423984636272836465776.837366," +
                        " \"testDouble\" : 21348234663852886984636272864657746.234566" + "}}")
                .when()
                .post("/approvals")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .extract()
                .path("id");

        String flTaskId = given()
                .contentType(ContentType.JSON)
                .queryParam("user", "admin")
                .queryParam("group", "managers")
                .when()
                .get("/usertasks/instance")
                .then()
                .statusCode(200)
                .log().body()
                .body("$.size()", is(1))
                .body("[0].taskName", is("firstLineApproval"))
                .body("[0].id", notNullValue())
                .extract()
                .path("[0].id");

        if (validateDomainData()) {

            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body("{ \"query\" : \"{Approvals{ id, traveller { firstName, lastName, testDate, " +
                                    "testInstant, testInteger, testLong, testFloat, testDouble}, " +
                                    "metadata { processInstances { id, state }, userTasks { id, name, state } } } }\" }")
                            .when().post("/graphql")
                            .then().statusCode(200)
                            .body("data.Approvals.size()", is(1))
                            .body("data.Approvals[0].id", is(pId))
                            .body("data.Approvals[0].traveller.firstName", is("Darth"))
                            .body("data.Approvals[0].traveller.lastName", is("Vader"))
                            .body("data.Approvals[0].traveller.testDate", is("2022-03-09T23:00:00Z"))
                            .body("data.Approvals[0].traveller.testInstant", is("2022-03-10T16:15:50Z"))
                            .body("data.Approvals[0].traveller.testInteger", is(2147483641))
                            .body("data.Approvals[0].traveller.testLong", is(Long.valueOf("8223372036854775802")))
                            .body("data.Approvals[0].traveller.testFloat", is(1.2028235E34))
                            .body("data.Approvals[0].traveller.testDouble", is(2.134823466385289E34))
                            .body("data.Approvals[0].metadata.processInstances", is(notNullValue()))
                            .body("data.Approvals[0].metadata.processInstances.size()", is(1))
                            .body("data.Approvals[0].metadata.processInstances[0].id", is(pId))
                            .body("data.Approvals[0].metadata.processInstances[0].state", is("ACTIVE"))
                            .body("data.Approvals[0].metadata.userTasks", is(notNullValue()))
                            .body("data.Approvals[0].metadata.userTasks.size()", is(1))
                            .body("data.Approvals[0].metadata.userTasks[0].id", is(flTaskId))
                            .body("data.Approvals[0].metadata.userTasks[0].name", is("firstLineApproval"))
                            .body("data.Approvals[0].metadata.userTasks[0].state", is("Reserved")));
        }

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ProcessInstances{ id, processId, state, createdBy} }\" }")
                        .when().post("/graphql")
                        .then().statusCode(200)
                        .body("data.ProcessInstances.size()", is(1))
                        .body("data.ProcessInstances[0].id", is(pId))
                        .body("data.ProcessInstances[0].processId", is("approvals"))
                        .body("data.ProcessInstances[0].state", is("ACTIVE"))
                        .body("data.ProcessInstances[0].createdBy", nullValue()));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ProcessInstances(where:{slaDueDate:{isNull: false}})" +
                                "{ id, processId, state, createdBy, slaDueDate} }\" }")
                        .when().post("/graphql")
                        .then().statusCode(200)
                        .body("data.ProcessInstances.size()", is(1))
                        .body("data.ProcessInstances[0].id", is(pId))
                        .body("data.ProcessInstances[0].processId", is("approvals"))
                        .body("data.ProcessInstances[0].state", is("ACTIVE"))
                        .body("data.ProcessInstances[0].createdBy", nullValue())
                        .body("data.ProcessInstances[0].slaDueDate", notNullValue()));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> addCheck(given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ProcessDefinitions{ id, version, name, source} }\" }")
                        .when().post("/graphql")
                        .then().statusCode(200)
                        .body("data.ProcessDefinitions.find { it.id == 'approvals' }.id", is("approvals"))
                        .body("data.ProcessDefinitions.find { it.id == 'approvals' }.version", is("1.0"))
                        .body("data.ProcessDefinitions.find { it.id == 'approvals' }.name", is("approvals"))));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{UserTaskInstances{ id, name, state } }\" }")
                        .when().post("/graphql")
                        .then().statusCode(200)
                        .body("data.UserTaskInstances.size()", is(1))
                        .body("data.UserTaskInstances[0].id", is(flTaskId))
                        .body("data.UserTaskInstances[0].name", is("firstLineApproval"))
                        .body("data.UserTaskInstances[0].state", is("Reserved")));

        String workItemId = given()
                .contentType(ContentType.JSON)
                .queryParam("user", "admin")
                .queryParam("group", "managers")
                .pathParam("processId", pId)
                .when()
                .get("/approvals/{processId}/tasks")
                .then()
                .statusCode(200)
                .body("$.size()", is(1))
                .body("[0].name", is("firstLineApproval"))
                .body("[0].id", notNullValue())
                .extract()
                .path("[0].id");

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().contentType(ContentType.JSON)
                        .when()
                        .queryParam("user", "admin")
                        .queryParam("group", "managers")
                        .pathParam("processId", pId)
                        .pathParam("taskId", workItemId)
                        .body(singletonMap("approved", true))
                        .post("/approvals/{processId}/firstLineApproval/{taskId}")
                        .then()
                        .statusCode(200)
                        .body("firstLineApproval", is(true)));

        String slTaskId = given()
                .contentType(ContentType.JSON)
                .queryParam("user", "manager")
                .queryParam("group", "managers")
                .pathParam("processId", pId)
                .when()
                .get("/approvals/{processId}/tasks")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].name", is("secondLineApproval"))
                .body("[0].id", notNullValue())
                .extract()
                .path("[0].id");

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().contentType(ContentType.JSON)
                        .when()
                        .queryParam("user", "manager")
                        .queryParam("group", "managers")
                        .pathParam("processId", pId)
                        .pathParam("taskId", slTaskId)
                        .body(singletonMap("approved", true))
                        .post("/approvals/{processId}/secondLineApproval/{taskId}")
                        .then()
                        .statusCode(200)
                        .body("secondLineApproval", is(true)));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given()
                        .contentType(ContentType.JSON)
                        .when()
                        .pathParam("processId", pId)
                        .get("/approvals/{processId}")
                        .then()
                        .statusCode(404));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ProcessInstances{ id, processId, state } }\" }")
                        .when().post("/graphql")
                        .then().statusCode(200)
                        .body("data.ProcessInstances.size()", is(1))
                        .body("data.ProcessInstances[0].id", is(pId))
                        .body("data.ProcessInstances[0].processId", is("approvals"))
                        .body("data.ProcessInstances[0].state", is("COMPLETED")));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{UserTaskInstances{ id, name, state } }\" }")
                        .when().post("/graphql")
                        .then().statusCode(200)
                        .body("data.UserTaskInstances.size()", is(2)));

        if (validateDomainData()) {
            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body("{ \"query\" : \"{Approvals{ id, firstLineApproval, secondLineApproval, metadata { processInstances { id, state }, userTasks { id, name, state } } } }\" }")
                            .when().post("/graphql")
                            .then().statusCode(200)
                            .body("data.Approvals.size()", is(1))
                            .body("data.Approvals[0].id", is(pId))
                            .body("data.Approvals[0].firstLineApproval", is(true))
                            .body("data.Approvals[0].secondLineApproval", is(true))
                            .body("data.Approvals[0].metadata.processInstances", is(notNullValue()))
                            .body("data.Approvals[0].metadata.processInstances.size()", is(1))
                            .body("data.Approvals[0].metadata.processInstances[0].id", is(pId))
                            .body("data.Approvals[0].metadata.processInstances[0].state", is("COMPLETED"))
                            .body("data.Approvals[0].metadata.userTasks", is(notNullValue()))
                            .body("data.Approvals[0].metadata.userTasks.size()", is(2)));
        }
        testProcessGatewayAPI();
    }

    public void testProcessGatewayAPI() throws IOException {
        String pId2 = createTestProcessInstance();

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> getProcessInstanceById(pId2, "ACTIVE"));

        if (validateGetProcessInstanceSource()) {
            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body("{ \"query\" : \"{ ProcessInstances (where: { id: {equal: \\\"" + pId2 + "\\\"}}) { source } }\"}")
                            .when().post("/graphql")
                            .then()
                            .statusCode(200)
                            .body("data.ProcessInstances[0].source", is(getTestFileContentByFilename("approval.bpmn"))));
        }

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ ProcessInstances (where: { id: {equal: \\\"" + pId2
                                + "\\\"}}) { nodeDefinitions { id, name, type, uniqueId, metadata { UniqueId } } nodes { name, definitionId }} }\"}")
                        .when().post("/graphql")
                        .then()
                        .statusCode(200)
                        .body("data.ProcessInstances[0].nodeDefinitions", notNullValue())
                        .body("data.ProcessInstances[0].nodeDefinitions.size()", is(4))
                        .body("data.ProcessInstances[0].nodeDefinitions[0].id", is("_8B62D3CA-5D03-4B2B-832B-126469288BB4"))
                        .body("data.ProcessInstances[0].nodeDefinitions[0].name", is("First Line Approval"))
                        .body("data.ProcessInstances[0].nodeDefinitions[0].type", is("HumanTaskNode"))
                        .body("data.ProcessInstances[0].nodeDefinitions[0].uniqueId", is("_8B62D3CA-5D03-4B2B-832B-126469288BB4"))
                        .body("data.ProcessInstances[0].nodeDefinitions[0].metadata.UniqueId", is("_8B62D3CA-5D03-4B2B-832B-126469288BB4"))
                        .body("data.ProcessInstances[0].nodes.size()", is(2))
                        .body("data.ProcessInstances[0].nodes.name", hasItem("First Line Approval"))
                        .body("data.ProcessInstances[0].nodes.definitionId", hasItem("_8B62D3CA-5D03-4B2B-832B-126469288BB4")));

        final String taskId = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"{ UserTaskInstances (where: { processInstanceId: {equal: \\\"" + pId2 + "\\\"}}) { id description potentialGroups } }\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .log().body()
                .body("data.UserTaskInstances[0].description", nullValue())
                .body("data.UserTaskInstances[0].potentialGroups[0]", equalTo("managers"))
                .extract().path("data.UserTaskInstances[0].id");

        String taskSchema = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"{ UserTaskInstances (where: {id: {equal:\\\"" + taskId + "\\\" }}){ " +
                        "schema ( user: \\\"manager\\\", groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"] )" +
                        "}}\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .log().body()
                .body("errors", nullValue())
                .extract().path("data.UserTaskInstances[0].schema");
        checkExpectedTaskSchema(taskSchema);

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"mutation{ UserTaskInstanceUpdate(" +
                                "taskId: \\\"" + taskId + "\\\", " +
                                "user: \\\"manager\\\", " +
                                "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                                "description: \\\"NewDescription\\\", " +
                                "priority: \\\"low\\\" " +
                                ")}\"}")
                        .when().post("/graphql")
                        .then()
                        .statusCode(200)
                        .body("errors", nullValue()));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ UserTaskInstances (where: { processInstanceId: {equal: \\\"" + pId2 + "\\\"}}) { " +
                                "id description priority potentialGroups comments {id} attachments {id}} }\"}")
                        .when().post("/graphql")
                        .then()
                        .statusCode(200)
                        .body("data.UserTaskInstances[0].description", equalTo("NewDescription"))
                        .body("data.UserTaskInstances[0].priority", equalTo("low"))
                        .body("data.UserTaskInstances[0].comments.size()", is(0))
                        .body("data.UserTaskInstances[0].attachments.size()", is(0))
                        .body("data.UserTaskInstances[0].potentialGroups[0]", equalTo("managers")));

        testProcessGatewayAPIComments(taskId, pId2);
        testProcessGatewayAPIAttachments(taskId, pId2);

        Map<String, Object> vars = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"{ ProcessInstances (where: { id: {equal: \\\"" + pId2 + "\\\"}}) { variables} }\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200).extract().path("data.ProcessInstances[0].variables");

        if (vars != null) {
            ((Map<String, String>) vars.get("traveller")).put("firstName", "Anakin");
            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body("{ \"query\" : \"mutation{ ProcessInstanceUpdateVariables(id:\\\"" + pId2 + "\\\", variables:\\\"" +
                                    mapper.writeValueAsString(vars).replace("\"", "\\\\\\\"")
                                    + "\\\")}\"}")
                            .when().post("/graphql")
                            .then()
                            .statusCode(200)
                            .body("errors", nullValue()));
            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body("{ \"query\" : \"{ ProcessInstances (where: { id: {equal: \\\"" + pId2 + "\\\"}}) { variables} }\"}")
                            .when().post("/graphql")
                            .then()
                            .statusCode(200)
                            .body("data.ProcessInstances[0].variables.traveller.firstName", containsString("Anakin")));
        }
        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ NodeInstanceTrigger(id:\\\"" + pId2 + "\\\", nodeId:\\\"_8B62D3CA-5D03-4B2B-832B-126469288BB4\\\")}\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("errors", nullValue());

        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ NodeInstanceRetrigger(id:\\\"" + pId2 + "\\\", nodeInstanceId:\\\"_8B62D3CA-5D03-4B2B-832B-126469288BB4\\\")}\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("errors[0].message", containsString("FAILED: Retrigger NodeInstance"));

        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ NodeInstanceCancel(id:\\\"" + pId2 + "\\\", nodeInstanceId:\\\"_8B62D3CA-5D03-4B2B-832B-126469288BB4\\\")}\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("errors[0].message", notNullValue());

        await()
                .atMost(TIMEOUT).untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"mutation {ProcessInstanceAbort( id: \\\"" + pId2 + "\\\")}\"}")
                        .when().post("/graphql")
                        .then()
                        .statusCode(200)
                        .body("errors", nullValue()));
        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> getProcessInstanceById(pId2, "ABORTED"));

        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ ProcessInstanceRetry( id: \\\"" + pId2 + "\\\")}\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("data.ProcessInstanceRetry", nullValue());

        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ ProcessInstanceSkip( id: \\\"" + pId2 + "\\\")}\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("errors[0].message", containsString("FAILED: SKIP"));

        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ UndefinedMutation( id: \\\"" + pId2 + "\\\")}\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("errors[0].message", containsString("Field 'UndefinedMutation' in type 'Mutation' is undefined"));

    }

    public void testProcessGatewayAPIComments(String taskId, String processInstanceId) throws IOException {
        String commentContent = "NewTaskComment";
        String commentCreationResult = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ UserTaskInstanceCommentCreate(" +
                        "taskId: \\\"" + taskId + "\\\", " +
                        "user: \\\"manager\\\", " +
                        "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                        "comment: \\\"" + commentContent + "\\\" " +
                        ")}\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("errors", nullValue())
                .extract().path("data.UserTaskInstanceCommentCreate");

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().contentType(ContentType.JSON)
                        .when()
                        .queryParam("user", "manager")
                        .queryParam("group", "managers")
                        .pathParam("taskId", taskId)
                        .get("/usertasks/instance/{taskId}/comments")
                        .then()
                        .statusCode(200)
                        .body("size()", is(1))
                        .body("[0].content", is(commentContent)));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ UserTaskInstances (where: { processInstanceId: {equal: \\\"" + processInstanceId + "\\\"}}) { " +
                                "id description priority potentialGroups comments {id content updatedBy updatedAt} } }\"}")
                        .when().post("/graphql")
                        .then()
                        .statusCode(200)
                        .body("data.UserTaskInstances[0].comments", notNullValue())
                        .body("data.UserTaskInstances[0].comments.size()", is(1))
                        .extract().jsonPath().getMap("data.UserTaskInstances[0].comments[0]"));

        Map<String, String> commentMap = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"{ UserTaskInstances (where: { processInstanceId: {equal: \\\"" + processInstanceId + "\\\"}}) { " +
                        "id description priority potentialGroups comments {id content updatedBy updatedAt} } }\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("data.UserTaskInstances[0].description", equalTo("NewDescription"))
                .body("data.UserTaskInstances[0].priority", equalTo("low"))
                .body("data.UserTaskInstances[0].potentialGroups[0]", equalTo("managers"))
                .body("data.UserTaskInstances[0].comments.size()", is(1))
                .extract().jsonPath().getMap("data.UserTaskInstances[0].comments[0]");

        checkExpectedCreatedItemData(commentCreationResult, commentMap);
        String commentNewContent = "commentNewContent";
        String commentUpdateResult = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation { UserTaskInstanceCommentUpdate ( " +
                        "user: \\\"manager\\\", " +
                        "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                        "commentId:  \\\"" + commentMap.get("id") + "\\\"" +
                        "comment:  \\\"" + commentNewContent + "\\\"" +
                        ")}\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("errors", nullValue())
                .extract().path("data.UserTaskInstanceCommentUpdate");

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ UserTaskInstances (where: { processInstanceId: {equal: \\\"" + processInstanceId + "\\\"}}) { " +
                                "comments {id content updatedBy updatedAt} } }\"}")
                        .when().post("/graphql")
                        .then()
                        .statusCode(200)
                        .body("data.UserTaskInstances[0].comments[0].content", equalTo(commentNewContent)));

        Map<String, String> comment2Map = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"{ UserTaskInstances (where: { processInstanceId: {equal: \\\"" + processInstanceId + "\\\"}}) { " +
                        "comments {id content updatedBy updatedAt} } }\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("data.UserTaskInstances[0].comments.size()", is(1))
                .extract().jsonPath().getMap("data.UserTaskInstances[0].comments[0]");

        checkExpectedCreatedItemData(commentUpdateResult, comment2Map);

        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation { UserTaskInstanceCommentDelete ( " +
                        "user: \\\"manager\\\", " +
                        "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                        "commentId:  \\\"" + commentMap.get("id") + "\\\"" +
                        ")}\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("errors", nullValue());

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ UserTaskInstances (where: { processInstanceId: {equal: \\\"" + processInstanceId + "\\\"}}) { " +
                                "comments {id} } }\"}")
                        .when().post("/graphql")
                        .then()
                        .statusCode(200)
                        .body("data.UserTaskInstances[0].comments.size()", is(0)));
    }

    public void testProcessGatewayAPIAttachments(String taskId, String processInstanceId) throws IOException {
        String attachmentName = "NewTaskAttachmentName";
        String attachmentCreationResult = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ UserTaskInstanceAttachmentCreate(" +
                        "taskId: \\\"" + taskId + "\\\", " +
                        "user: \\\"manager\\\", " +
                        "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                        "name: \\\"" + attachmentName + "\\\", " +
                        "uri: \\\"https://drive.google.com/file/d/1Z_Lipg2jzY9TNewTaskAttachmentUri\\\", " +
                        ")}\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("errors", nullValue())
                .extract().path("data.UserTaskInstanceAttachmentCreate");
        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().contentType(ContentType.JSON)
                        .when()
                        .queryParam("user", "manager")
                        .queryParam("group", "managers")
                        .pathParam("taskId", taskId)
                        .get("/usertasks/instance/{taskId}/attachments")
                        .then()
                        .statusCode(200)
                        .body("size()", is(1))
                        .body("[0].name", is(attachmentName)));

        AtomicReference<Map<String, String>> attachmentMapRef = new AtomicReference<>();
        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> attachmentMapRef.set(given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ UserTaskInstances (where: { processInstanceId: {equal: \\\"" + processInstanceId + "\\\"}}) { " +
                                "id description priority potentialGroups attachments {id name content updatedBy updatedAt} } }\"}")
                        .when().post("/graphql")
                        .then()
                        .statusCode(200)
                        .body("data.UserTaskInstances[0].description", equalTo("NewDescription"))
                        .body("data.UserTaskInstances[0].priority", equalTo("low"))
                        .body("data.UserTaskInstances[0].potentialGroups[0]", equalTo("managers"))
                        .body("data.UserTaskInstances[0].attachments.size()", is(1))
                        .body("data.UserTaskInstances[0].attachments[0].name", equalTo(attachmentName))
                        .extract().jsonPath().getMap("data.UserTaskInstances[0].attachments[0]")));

        Map<String, String> attachmentMap = attachmentMapRef.get();

        checkExpectedCreatedItemData(attachmentCreationResult, attachmentMap);

        String updatedAttachmentName = "newAttachmentContent";
        String updatedAttachmentUri = "https://drive.google.com/file/d/1Z_Lipg2jzY9TUpdatedTaskAttachmentUri";
        String attachmentUpdateResult = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation { UserTaskInstanceAttachmentUpdate ( " +
                        "user: \\\"manager\\\", " +
                        "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                        "attachmentId:  \\\"" + attachmentMap.get("id") + "\\\"" +
                        "name:  \\\"" + updatedAttachmentName + "\\\"" +
                        "uri:  \\\"" + updatedAttachmentUri + "\\\"" +
                        ")}\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("errors", nullValue())
                .extract().path("data.UserTaskInstanceAttachmentUpdate");

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ UserTaskInstances (where: { processInstanceId: {equal: \\\"" + processInstanceId + "\\\"}}) { " +
                                "attachments {id name content updatedBy updatedAt} } }\"}")
                        .when().post("/graphql")
                        .then()
                        .statusCode(200)
                        .body("data.UserTaskInstances[0].attachments[0].content", equalTo(updatedAttachmentUri)));

        Map<String, String> attachmentMap2 = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"{ UserTaskInstances (where: { processInstanceId: {equal: \\\"" + processInstanceId + "\\\"}}) { " +
                        "attachments {id name content updatedBy updatedAt} } }\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("data.UserTaskInstances[0].attachments.size()", is(1))
                .body("data.UserTaskInstances[0].attachments[0].name", equalTo(updatedAttachmentName))
                .extract().jsonPath().getMap("data.UserTaskInstances[0].attachments[0]");

        checkExpectedCreatedItemData(attachmentUpdateResult, attachmentMap2);

        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation { UserTaskInstanceAttachmentDelete ( " +
                        "user: \\\"manager\\\", " +
                        "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                        "attachmentId:  \\\"" + attachmentMap.get("id") + "\\\"" +
                        ")}\"}")
                .when().post("/graphql")
                .then()
                .statusCode(200)
                .body("errors", nullValue());

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ UserTaskInstances (where: { processInstanceId: {equal: \\\"" + processInstanceId + "\\\"}}) { " +
                                "attachments {id} } }\"}")
                        .when().post("/graphql")
                        .then()
                        .statusCode(200)
                        .body("data.UserTaskInstances[0].attachments.size()", is(0)));
    }

    protected String createTestProcessInstance() {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"traveller\" : {\"firstName\" : \"Darth\",\"lastName\" : \"Vader\",\"email\" : \"darth.vader@deathstar.com\",\"nationality\" : \"Tatooine\"}}")
                .when()
                .post("/approvals")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .extract()
                .path("id");
    }

    protected ValidatableResponse getProcessInstanceById(String processInstanceId, String state) {
        return given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"{ProcessInstances(where: {  id: {  equal : \\\"" + processInstanceId + "\\\"}}){ id, processId, state, executionSummary } }\" }")
                .when().post("/graphql")
                .then().statusCode(200)
                .body("data.ProcessInstances.size()", is(1))
                .body("data.ProcessInstances[0].id", is(processInstanceId))
                .body("data.ProcessInstances[0].processId", is("approvals"))
                .body("data.ProcessInstances[0].state", is(state))
                .body("data.ProcessInstances[0].executionSummary", is(notNullValue()))
                .body("data.ProcessInstances[0].executionSummary.size()", greaterThan(0));
    }

    private void checkExpectedCreatedItemData(String creationData, Map<String, String> resultMap) throws IOException {
        JsonNode creationJsonNode = mapper.readTree(creationData);
        assertThat(creationJsonNode.at("/updatedBy").asText()).isEqualTo(resultMap.get("updatedBy"));
        assertThat(ZonedDateTime.parse(creationJsonNode.at("/updatedAt").asText()).withZoneSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS))
                .isEqualTo(ZonedDateTime.parse(resultMap.get("updatedAt")).withZoneSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
        assertThat(creationJsonNode.at("/content").asText()).isEqualTo(resultMap.get("content"));
    }

    private void checkExpectedTaskSchema(String taskSchema) throws IOException {
        JsonNode schemaJsonNode = mapper.readTree(taskSchema);
        assertThat(schemaJsonNode.at("/type").asText()).isEqualTo("object");

        // Check Schema phases
        assertThat(schemaJsonNode.at("/phases").size()).isEqualTo(3);
        assertThat(schemaJsonNode.get("phases").toString().contains("abort")).isTrue();
        assertThat(schemaJsonNode.get("phases").toString().contains("skip")).isTrue();
        assertThat(schemaJsonNode.get("phases").toString().contains("complete")).isTrue();

        // Check Schema properties
        assertThat(schemaJsonNode.at("/properties").size()).isEqualTo(2);

        assertThat(schemaJsonNode.at("/properties/approved/output").asText()).isEqualTo("true");
        assertThat(schemaJsonNode.at("/properties/approved/type").asText()).isEqualTo("boolean");
        assertThat(schemaJsonNode.at("/properties/traveller/$ref").asText()).isEqualTo("#/$defs/Traveller");
        assertThat(schemaJsonNode.at("/properties/traveller/input").asText()).isEqualTo("true");

        // Check Schema definitions
        assertThat(schemaJsonNode.at("/$defs").size()).isEqualTo(2);

        assertThat(schemaJsonNode.at("/$defs/Traveller/type").asText()).isEqualTo("object");
        assertThat(schemaJsonNode.at("/$defs/Traveller/properties").size()).isEqualTo(12);
        assertThat(schemaJsonNode.at("/$defs/Traveller/properties/address/$ref").asText()).isEqualTo("#/$defs/Address");
        assertThat(schemaJsonNode.at("/$defs/Traveller/properties/email/type").asText()).isEqualTo("string");
        assertThat(schemaJsonNode.at("/$defs/Traveller/properties/firstName/type").asText()).isEqualTo("string");
        assertThat(schemaJsonNode.at("/$defs/Traveller/properties/lastName/type").asText()).isEqualTo("string");
        assertThat(schemaJsonNode.at("/$defs/Traveller/properties/nationality/type").asText()).isEqualTo("string");
        assertThat(schemaJsonNode.at("/$defs/Traveller/properties/processed/type").asText()).isEqualTo("boolean");

        assertThat(schemaJsonNode.at("/$defs/Address/properties").size()).isEqualTo(4);
        assertThat(schemaJsonNode.at("/$defs/Address/properties/city/type").asText()).isEqualTo("string");
        assertThat(schemaJsonNode.at("/$defs/Address/properties/country/type").asText()).isEqualTo("string");
        assertThat(schemaJsonNode.at("/$defs/Address/properties/street/type").asText()).isEqualTo("string");
        assertThat(schemaJsonNode.at("/$defs/Address/properties/zipCode/type").asText()).isEqualTo("string");

    }

    public static String readFileContent(String file) throws URISyntaxException, IOException {
        Path path = Paths.get(Thread.currentThread().getContextClassLoader().getResource(file).toURI());
        return Files.readString(path);
    }

    public String getTestFileContentByFilename(String fileName) throws Exception {
        return readFileContent(fileName);
    }
}
