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
package org.kie.kogito.integrationtests.springboot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.acme.travels.Traveller;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.event.process.ProcessInstanceDataEvent;
import org.kie.kogito.test.springboot.kafka.KafkaTestClient;
import org.kie.kogito.testcontainers.springboot.KafkaSpringBootTestResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.cloudevents.jackson.JsonFormat;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = KogitoSpringbootApplication.class)
@ContextConfiguration(initializers = { KafkaSpringBootTestResource.class })
class ProcessEventIT extends BaseRestTest {

    public static final String KOGITO_PROCESSINSTANCES_EVENTS = "kogito-processinstances-events";
    public static final String KOGITO_USERTASKINSTANCES_EVENTS = "kogito-usertaskinstances-events";
    public static final String KOGITO_VARIABLE_EVENTS = "kogito-variables-events";

    private static Logger LOGGER = LoggerFactory.getLogger(ProcessEventIT.class);

    private static ObjectMapper mapper;

    @Autowired
    private KafkaTestClient kafkaClient;

    @BeforeAll
    static void init() {
        mapper = new ObjectMapper()
                .registerModule(JsonFormat.getCloudEventJacksonModule())
                .registerModule(new JavaTimeModule());
    }

    @Test
    void testSaveTask() throws Exception {
        Traveller traveller = new Traveller("pepe", "rubiales", "pepe.rubiales@gmail.com", "Spanish");
        final CountDownLatch countDownLatch = new CountDownLatch(6);
        final CompletableFuture future = new CompletableFuture();

        kafkaClient.consume(Set.of(KOGITO_PROCESSINSTANCES_EVENTS, KOGITO_USERTASKINSTANCES_EVENTS, KOGITO_VARIABLE_EVENTS), s -> {
            LOGGER.info("Received from kafka: {}", s);
            try {
                ProcessInstanceDataEvent event = mapper.readValue(s, ProcessInstanceDataEvent.class);
                LinkedHashMap data = (LinkedHashMap) event.getData();
                if ("handleApprovals".equals(data.get("processId"))) {
                    switch (event.getType()) {
                        case "ProcessInstanceStateDataEvent":
                            assertThat(event.getType()).isEqualTo("ProcessInstanceStateDataEvent");
                            assertThat(event.getSource().toString()).isEqualTo("/handleApprovals");
                            assertThat(data.get("processId")).isEqualTo("handleApprovals");
                            assertThat(event.getKogitoProcessVersion()).isEqualTo("1.0");
                            assertThat(data.get("processType")).isEqualTo("BPMN");
                            assertThat(event.getKogitoProcessType()).isEqualTo("BPMN");
                            break;
                        case "UserTaskInstanceStateDataEvent":
                            assertThat(event.getType()).isEqualTo("UserTaskInstanceEvent");
                            assertThat(event.getSource().toString()).isEqualTo("/handleApprovals");
                            assertThat(data.get("processId")).isEqualTo("handleApprovals");
                            assertThat(event.getKogitoProcessVersion()).isEqualTo("1.0");
                            break;
                        case "ProcessInstanceVariableDataEvent":
                            assertThat(event.getType()).isEqualTo("ProcessInstanceVariableDataEvent");
                            assertThat(event.getSource().toString()).isEqualTo("/handleApprovals");
                            assertThat(data.get("processId")).isEqualTo("handleApprovals");
                            assertThat(event.getKogitoProcessVersion()).isEqualTo("1.0");
                            break;
                    }
                }
                countDownLatch.countDown();
                if (countDownLatch.getCount() == 0) {
                    future.complete(null);
                }
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });

        String processId = given()
                .contentType(ContentType.JSON)
                .when()
                .body(Collections.singletonMap("traveller", traveller))
                .post("/handleApprovals")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        String taskId = given()
                .contentType(ContentType.JSON)
                .queryParam("user", "admin")
                .queryParam("group", "managers")
                .pathParam("processId", processId)
                .when()
                .get("/handleApprovals/{processId}/tasks")
                .then()
                .statusCode(200)
                .extract()
                .path("[0].id");

        Map<String, Object> model = Collections.singletonMap("approved", true);

        assertThat(given().contentType(ContentType.JSON)
                .when()
                .queryParam("user", "admin")
                .queryParam("group", "managers")
                .pathParam("processId", processId)
                .pathParam("taskId", taskId)
                .body(model)
                .put("/handleApprovals/{processId}/firstLineApproval/{taskId}")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class)).isEqualTo(model);

        Boolean approved = given().contentType(ContentType.JSON)
                .when()
                .queryParam("user", "admin")
                .queryParam("group", "managers")
                .pathParam("processId", processId)
                .pathParam("taskId", taskId)
                .get("/handleApprovals/{processId}/firstLineApproval/{taskId}")
                .then()
                .statusCode(200)
                .extract()
                .path("results.approved");
        assertThat(approved).isTrue();

        String humanTaskId = given()
                .contentType(ContentType.JSON)
                .queryParam("user", "admin")
                .queryParam("group", "managers")
                .pathParam("processId", processId)
                .when()
                .get("/handleApprovals/{processId}/tasks")
                .then()
                .statusCode(200)
                .extract()
                .path("[1].id");

        future.get(10, TimeUnit.SECONDS);
    }
}
