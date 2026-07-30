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
package org.kie.kogito.app.audit.quarkus;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.kie.kogito.app.audit.api.SubsystemConstants;
import org.kie.kogito.event.EventPublisher;
import org.kie.kogito.event.usertask.UserTaskInstanceAssignmentDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceAttachmentDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceAttachmentEventBody;
import org.kie.kogito.event.usertask.UserTaskInstanceCommentDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceCommentEventBody;
import org.kie.kogito.event.usertask.UserTaskInstanceDeadlineDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceStateDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceVariableDataEvent;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newUserTaskInstanceAssignmentEvent;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newUserTaskInstanceAttachmentEvent;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newUserTaskInstanceCommentEvent;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newUserTaskInstanceDeadlineEvent;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newUserTaskInstanceStateEvent;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.newUserTaskInstanceVariableEvent;
import static org.kie.kogito.app.audit.quarkus.DataAuditTestUtils.wrapQuery;

@QuarkusTest
@TestInstance(Lifecycle.PER_CLASS)
public class QuarkusAuditUserTaskInstanceServiceTest {

    @Inject
    EventPublisher publisher;

    class Pojo {
        public Pojo(Integer value) {
            this.value = value;
        }

        Integer value;

        public Integer getValue() {
            return value;
        }

        public void setValue(Integer value) {
            this.value = value;
        }
    }

    @BeforeAll
    public void init() {

        UserTaskInstanceStateDataEvent uEvent;
        uEvent = newUserTaskInstanceStateEvent("eventUser", "utd1", "1", "utn1", "1", "utd1", "utp1", "utrn1", "Ready", "owner", "1");
        publisher.publish(uEvent);

        uEvent = newUserTaskInstanceStateEvent("eventUser", "utd1", "1", "utn1", "1", "utd1", "utp1", "utrn1", "Claimed", "owner", "1");
        publisher.publish(uEvent);

        UserTaskInstanceVariableDataEvent vEvent;
        vEvent = newUserTaskInstanceVariableEvent(uEvent, "eventUser", "varId1", "varName1", "INPUT", new Pojo(1));
        publisher.publish(vEvent);

        vEvent = newUserTaskInstanceVariableEvent(uEvent, "eventUser", "varId1", "varName1", "INPUT", new Pojo(2));
        publisher.publish(vEvent);

        vEvent = newUserTaskInstanceVariableEvent(uEvent, "eventUser", "varId2", "varName2", "OUTPUT", new Pojo(1));
        publisher.publish(vEvent);

        vEvent = newUserTaskInstanceVariableEvent(uEvent, "eventUser", "varId3", "varName3", "OUTPUT", new Pojo(1));
        publisher.publish(vEvent);

        UserTaskInstanceAssignmentDataEvent aEvent;
        aEvent = newUserTaskInstanceAssignmentEvent(uEvent, "eventUser", "POT_OWNERS", "user1", "user2", "user3");
        publisher.publish(aEvent);

        aEvent = newUserTaskInstanceAssignmentEvent(uEvent, "eventUser", "ADMINISTRATORS", "user1", "user2", "user3");
        publisher.publish(aEvent);

        UserTaskInstanceAttachmentDataEvent attEvent;
        attEvent = newUserTaskInstanceAttachmentEvent(uEvent, "eventUser", "att1", "attName1", URI.create("http://localhost:8080/att1"),
                UserTaskInstanceAttachmentEventBody.EVENT_TYPE_ADDED);
        publisher.publish(attEvent);

        attEvent = newUserTaskInstanceAttachmentEvent(uEvent, "eventUser", "att2", "attName2", null,
                UserTaskInstanceAttachmentEventBody.EVENT_TYPE_DELETED);
        publisher.publish(attEvent);

        UserTaskInstanceCommentDataEvent commentEvent;
        commentEvent = newUserTaskInstanceCommentEvent(uEvent, "eventUser", "att1", "attName1", UserTaskInstanceCommentEventBody.EVENT_TYPE_ADDED);
        publisher.publish(commentEvent);

        UserTaskInstanceDeadlineDataEvent deadlineEvent;
        deadlineEvent = newUserTaskInstanceDeadlineEvent(uEvent, "eventUser", Collections.singletonMap("input1", "value1"), Collections.singletonMap("notification1", "notificationValue"));
        publisher.publish(deadlineEvent);

        uEvent = newUserTaskInstanceStateEvent("eventUser", "utd2", "2", "utn2", "1", "utd2", "utp2", "utrn2", "Claimed", "owner", "1");
        publisher.publish(uEvent);

    }

    @Test
    public void testGetAllUserTaskInstanceState() {

        String query =
                "{ GetAllUserTaskInstanceState { eventId, eventDate, userTaskDefinitionId, userTaskInstanceId, processInstanceId, businessKey, name, description, actualUser, state, eventType } }";
        query = wrapQuery(query);
        List<Map<String, Object>> data = given()
                .contentType(ContentType.JSON)
                .body(query)
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .extract().path("data.GetAllUserTaskInstanceState");

        assertThat(data).hasSize(2);

    }

    @Test
    public void testGetAllUserTaskInstanceAssignments() {
        String query =
                "{ GetAllUserTaskInstanceAssignments (userTaskInstanceId : \\\"1\\\") { eventId, eventDate, userTaskDefinitionId, userTaskInstanceId, processInstanceId, businessKey, userTaskName, assignmentType, users } }";

        query = wrapQuery(query);
        List<Map<String, Object>> data = given()
                .contentType(ContentType.JSON)
                .body(query)
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .extract().path("data.GetAllUserTaskInstanceAssignments");

        assertThat(data).hasSize(2);

    }

    @Test
    public void testGetAllUserTaskInstanceAttachments() {
        String query =
                "{ GetAllUserTaskInstanceAttachments (userTaskInstanceId : \\\"1\\\") { eventId, eventDate, userTaskDefinitionId, userTaskInstanceId, processInstanceId, businessKey, attachmentId, attachmentName, attachmentURI, eventType } }";
        query = wrapQuery(query);
        List<Map<String, Object>> data = given()
                .contentType(ContentType.JSON)
                .body(query)
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .extract().path("data.GetAllUserTaskInstanceAttachments");

        assertThat(data).hasSize(2);

    }

    @Test
    public void testGetAllUserTaskInstanceComment() {
        String query =
                "{ GetAllUserTaskInstanceComments (userTaskInstanceId : \\\"1\\\") { eventId, eventDate, userTaskDefinitionId, userTaskInstanceId, processInstanceId, businessKey, commentId, commentContent, eventType } }";
        query = wrapQuery(query);
        List<Map<String, Object>> data = given()
                .contentType(ContentType.JSON)
                .body(query)
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .extract().path("data.GetAllUserTaskInstanceComments");

        assertThat(data).hasSize(1);

    }

    @Test
    public void testGetAllUserTaskInstanceDeadline() {
        String query =
                "{ GetAllUserTaskInstanceDeadlines (userTaskInstanceId : \\\"1\\\") { eventId, eventDate, userTaskDefinitionId, userTaskInstanceId, processInstanceId, businessKey, eventType, notification } }";
        query = wrapQuery(query);
        List<Map<String, Object>> data = given()
                .contentType(ContentType.JSON)
                .body(query)
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .extract().path("data.GetAllUserTaskInstanceDeadlines");

        assertThat(data).hasSize(1);

    }

    @Test
    public void testGetAllUserTaskInstanceVariable() {
        String query =
                "{ GetAllUserTaskInstanceVariables (userTaskInstanceId : \\\"1\\\") {  eventId, eventDate, userTaskDefinitionId, userTaskInstanceId, processInstanceId, businessKey, variableId, variableName, variableValue, variableType } }";
        query = wrapQuery(query);
        List<Map<String, Object>> data = given()
                .contentType(ContentType.JSON)
                .body(query)
                .when()
                .post(SubsystemConstants.DATA_AUDIT_QUERY_PATH)
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .extract().path("data.GetAllUserTaskInstanceVariables");

        assertThat(data).hasSize(3);

    }
}
