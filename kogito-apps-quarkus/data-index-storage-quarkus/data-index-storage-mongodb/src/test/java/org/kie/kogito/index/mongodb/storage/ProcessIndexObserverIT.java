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
package org.kie.kogito.index.mongodb.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.index.mongodb.mock.MockIndexCreateOrUpdateEventListener;
import org.kie.kogito.index.mongodb.model.ProcessIdEntity;
import org.kie.kogito.index.mongodb.model.ProcessIdEntityMapper;
import org.kie.kogito.persistence.api.Storage;
import org.kie.kogito.persistence.api.schema.ProcessDescriptor;
import org.kie.kogito.persistence.mongodb.client.MongoClientManager;
import org.kie.kogito.persistence.mongodb.index.ProcessIndexEvent;
import org.kie.kogito.persistence.mongodb.storage.MongoStorage;
import org.kie.kogito.testcontainers.quarkus.MongoDBQuarkusTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.index.storage.Constants.PROCESS_ID_MODEL_STORAGE;

@QuarkusTest
@QuarkusTestResource(MongoDBQuarkusTestResource.class)
class ProcessIndexObserverIT {

    @Inject
    MongoClientManager mongoClientManager;

    @Inject
    MockIndexCreateOrUpdateEventListener mockIndexCreateOrUpdateEventListener;

    @Inject
    ProcessIndexObserver processIndexObserver;

    @BeforeEach
    void setup() {
        mockIndexCreateOrUpdateEventListener.reset();
    }

    @AfterEach
    void tearDown() {
        mockIndexCreateOrUpdateEventListener.reset();
    }

    @Test
    void testOnProcessIndexEvent() {
        String processId = "testProcess";
        String processType = "testProcessType";

        ProcessDescriptor processDescriptor = new ProcessDescriptor(processId, processType);
        ProcessIndexEvent processIndexEvent = new ProcessIndexEvent(processDescriptor);

        processIndexObserver.onProcessIndexEvent(processIndexEvent);

        Storage<String, String> processIdStorage = new MongoStorage<>(mongoClientManager.getCollection(PROCESS_ID_MODEL_STORAGE, ProcessIdEntity.class),
                String.class.getName(), new ProcessIdEntityMapper());
        assertThat(processIdStorage.containsKey(processId)).isTrue();
        assertThat(processIdStorage.get(processId)).isEqualTo(processType);

        mockIndexCreateOrUpdateEventListener.assertFire("testProcess_domain", processType);
    }
}
