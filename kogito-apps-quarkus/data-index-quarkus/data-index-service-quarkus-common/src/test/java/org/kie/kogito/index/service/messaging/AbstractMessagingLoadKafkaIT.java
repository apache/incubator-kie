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
package org.kie.kogito.index.service.messaging;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.event.process.ProcessInstanceDataEvent;
import org.kie.kogito.event.usertask.UserTaskInstanceDataEvent;
import org.kie.kogito.index.model.ProcessInstanceState;
import org.kie.kogito.persistence.protobuf.ProtobufService;
import org.kie.kogito.test.quarkus.kafka.KafkaTestClient;
import org.kie.kogito.testcontainers.quarkus.KafkaQuarkusTestResource;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.restassured.http.ContentType;

import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.assertj.core.api.Assertions.fail;
import static org.kie.kogito.index.model.ProcessInstanceState.ACTIVE;
import static org.kie.kogito.index.model.ProcessInstanceState.COMPLETED;
import static org.kie.kogito.index.service.GraphQLUtils.getProcessDefinitionByIdAndVersion;
import static org.kie.kogito.index.service.GraphQLUtils.getProcessInstanceById;
import static org.kie.kogito.index.service.GraphQLUtils.getTravelsByProcessInstanceId;
import static org.kie.kogito.index.service.GraphQLUtils.getTravelsByUserTaskId;
import static org.kie.kogito.index.service.GraphQLUtils.getUserTaskInstanceByProcessInstanceId;
import static org.kie.kogito.index.test.TestUtils.getProcessCloudEvent;
import static org.kie.kogito.index.test.TestUtils.getUserTaskCloudEvent;

public abstract class AbstractMessagingLoadKafkaIT {

    @ConfigProperty(name = KafkaQuarkusTestResource.KOGITO_KAFKA_PROPERTY)
    String kafkaBootstrapServers;

    @ConfigProperty(name = "kogito.data-index.domain-indexing", defaultValue = "true")
    Boolean indexDomain;

    List<KafkaTestClient> kafkaClients;

    Duration timeout = Duration.ofMinutes(1);

    Integer producers = 1000;

    @Inject
    ObjectMapper mapper;

    @Inject
    ProtobufService protobufService;

    @BeforeEach
    void setup() throws Exception {
        if (indexDomain) {
            protobufService.registerProtoBufferType(getTestProtobufFileContent());
        }
        kafkaClients = Stream.generate(() -> new KafkaTestClient(kafkaBootstrapServers)).parallel().limit(producers).collect(Collectors.toList());
    }

    @AfterEach
    void close() {
        if (kafkaClients != null) {
            kafkaClients.parallelStream().forEach(client -> client.shutdown());
            kafkaClients.clear();
        }
    }

    @Test
    void testMessagingEvents() {
        if (indexDomain) {
            given().contentType(ContentType.JSON).body("{ \"query\" : \"{ Travels { id } }\" }")
                    .when().post("/graphql")
                    .then().log().ifValidationFails().statusCode(200)
                    .body("data.Travels", isA(Collection.class));
        }

        final String processId = "travels";

        kafkaClients.parallelStream().map(client -> {
            try {
                String processInstanceId = UUID.randomUUID().toString();
                String taskId = UUID.randomUUID().toString();

                ProcessInstanceDataEvent<?> startEvent = getProcessCloudEvent(processId, processInstanceId, ACTIVE, null, null, null, "currentUser");

                sendProcessInstanceEvent(client, startEvent);

                UserTaskInstanceDataEvent<?> userTaskEvent = getUserTaskCloudEvent(taskId, processId, processInstanceId, null, null, "InProgress");
                sendUserTaskEvent(client, userTaskEvent);

                userTaskEvent = getUserTaskCloudEvent(taskId, processId, processInstanceId, null, null, "Completed");
                sendUserTaskEvent(client, userTaskEvent);

                ProcessInstanceDataEvent<?> endEvent = getProcessCloudEvent(processId, processInstanceId, COMPLETED, null, null, null, "currentUser");

                sendProcessInstanceEvent(client, endEvent);

                return processInstanceId;
            } catch (Exception ex) {
                fail(ex.getMessage(), ex);
                throw new RuntimeException(ex);
            }
        }).forEach(pId -> {
            validateUserTaskInstance(pId, "Completed");
            validateProcessInstance(pId, COMPLETED);
            validateProcessDefinition(processId, "1.0");
        });
    }

    private void validateProcessDefinition(String processId, String version) {
        await()
                .atMost(timeout)
                .untilAsserted(() -> {
                    given().contentType(ContentType.JSON).body(getProcessDefinitionByIdAndVersion(processId, version))
                            .when().post("/graphql")
                            .then().statusCode(200)
                            .body("data.ProcessDefinitions.size()", is(1))
                            .body("data.ProcessDefinitions[0].id", is(processId))
                            .body("data.ProcessDefinitions[0].version", is(version));
                });
    }

    private void validateProcessInstance(String processInstanceId, ProcessInstanceState state) {
        await()
                .atMost(timeout)
                .untilAsserted(() -> {
                    given().contentType(ContentType.JSON).body(getProcessInstanceById(processInstanceId))
                            .when().post("/graphql")
                            .then().statusCode(200)
                            .body("data.ProcessInstances.size()", is(1))
                            .body("data.ProcessInstances[0].id", is(processInstanceId))
                            .body("data.ProcessInstances[0].state", is(state.toString()));
                });

        if (indexDomain) {
            await()
                    .atMost(timeout)
                    .untilAsserted(() -> {
                        given().contentType(ContentType.JSON).body(getTravelsByProcessInstanceId(processInstanceId))
                                .when().post("/graphql")
                                .then().statusCode(200)
                                .body("data.Travels.size()", is(1))
                                .body("data.Travels[0].id", is(processInstanceId))
                                .body("data.Travels[0].metadata.processInstances.size()", is(1))
                                .body("data.Travels[0].metadata.processInstances[0].id", is(processInstanceId))
                                .body("data.Travels[0].metadata.processInstances[0].state", is(state.toString()));
                    });
        }
    }

    private void validateUserTaskInstance(String processInstanceId, String state) {
        await()
                .atMost(timeout)
                .untilAsserted(() -> {
                    String taskId = given().contentType(ContentType.JSON).body(getUserTaskInstanceByProcessInstanceId(processInstanceId))
                            .when().post("/graphql")
                            .then().statusCode(200)
                            .body("data.UserTaskInstances.size()", is(1))
                            .body("data.UserTaskInstances[0].processInstanceId", is(processInstanceId))
                            .body("data.UserTaskInstances[0].state", is(state))
                            .extract().body().path("data.UserTaskInstances[0].id");

                    if (indexDomain) {
                        given().contentType(ContentType.JSON).body(getTravelsByUserTaskId(taskId))
                                .when().post("/graphql")
                                .then().statusCode(200)
                                .body("data.Travels.size()", is(1))
                                .body("data.Travels[0].id", is(processInstanceId))
                                .body("data.Travels[0].metadata.userTasks.size()", is(1))
                                .body("data.Travels[0].metadata.userTasks[0].id", is(taskId))
                                .body("data.Travels[0].metadata.userTasks[0].state", is(state));
                    }
                });

    }

    private void sendProcessInstanceEvent(KafkaTestClient client, ProcessInstanceDataEvent event) throws Exception {
        client.produce(mapper.writeValueAsString(event), "kogito-processinstances-events");
    }

    private void sendUserTaskEvent(KafkaTestClient client, UserTaskInstanceDataEvent event) throws Exception {
        client.produce(mapper.writeValueAsString(event), "kogito-usertaskinstances-events");
    }

    protected String getTestProtobufFileContent() throws Exception {
        return null;
    }
}
