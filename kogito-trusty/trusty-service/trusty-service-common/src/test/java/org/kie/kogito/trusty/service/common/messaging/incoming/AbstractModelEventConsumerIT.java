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
package org.kie.kogito.trusty.service.common.messaging.incoming;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.test.quarkus.kafka.KafkaTestClient;
import org.kie.kogito.testcontainers.quarkus.KafkaQuarkusTestResource;
import org.kie.kogito.trusty.service.common.TrustyService;
import org.kie.kogito.trusty.service.common.TrustyServiceTestUtils;
import org.kie.kogito.trusty.storage.api.model.decision.DMNModelWithMetadata;
import org.kie.kogito.trusty.storage.common.TrustyStorageService;

import jakarta.inject.Inject;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

public abstract class AbstractModelEventConsumerIT {

    @Inject
    TrustyService trustyService;

    @Inject
    TrustyStorageService trustyStorageService;

    KafkaTestClient kafkaClient;

    @ConfigProperty(name = KafkaQuarkusTestResource.KOGITO_KAFKA_PROPERTY)
    String kafkaBootstrapServers;

    @BeforeEach
    public void setup() {
        trustyStorageService.getModelStorage(DMNModelWithMetadata.class).clear();
        kafkaClient = new KafkaTestClient(kafkaBootstrapServers);
    }

    @AfterEach
    public void tearDown() {
        if (kafkaClient != null) {
            kafkaClient.shutdown();
        }
    }

    @Test
    void testCorrectCloudEvent() {
        kafkaClient.produce(TrustyServiceTestUtils.buildCloudEventJsonString(TrustyServiceTestUtils.buildCorrectModelEvent()),
                KafkaConstants.KOGITO_TRACING_MODEL_TOPIC);
        await()
                .atMost(5, SECONDS)
                .untilAsserted(() -> assertThatCode(() -> trustyService.getModelById(TrustyServiceTestUtils.getModelIdentifier(), DMNModelWithMetadata.class)).doesNotThrowAnyException());

        DMNModelWithMetadata storedDefinition =
                trustyService.getModelById(TrustyServiceTestUtils.getModelIdentifier(), DMNModelWithMetadata.class);
        assertThat(storedDefinition).isNotNull();
        assertThat(storedDefinition.getModel()).isEqualTo("definition");
        assertThat(storedDefinition.getName()).isEqualTo("name");
        assertThat(storedDefinition.getNamespace()).isEqualTo("namespace");
    }
}
