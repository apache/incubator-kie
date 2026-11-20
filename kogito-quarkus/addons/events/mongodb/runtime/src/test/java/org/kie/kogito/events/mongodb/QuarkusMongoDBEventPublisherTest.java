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
package org.kie.kogito.events.mongodb;

import org.junit.jupiter.api.Test;
import org.kie.kogito.mongodb.transaction.AbstractTransactionManager;

import com.mongodb.client.MongoClient;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@QuarkusTest
class QuarkusMongoDBEventPublisherTest {

    @Inject
    QuarkusMongoDBEventPublisher publisher;

    @InjectSpy
    MongoClient mongoClient;

    @Inject
    AbstractTransactionManager transactionManager;

    @Test
    void setup() {
        publisher.setupQuarkusMongoDBEventPublisher();
        verify(mongoClient, atLeastOnce()).getDatabase(eq("testDB"));
    }

    @Test
    void mongoClient() {
        assertThat(publisher.mongoClient().getDatabase("testDB")).isEqualTo(mongoClient.getDatabase("testDB"));
    }

    @Test
    void transactionManager() {
        assertThat(publisher.transactionManager()).isEqualTo(transactionManager);
    }

    @Test
    void processInstancesEvents() {
        assertThat(publisher.processInstancesEvents()).isFalse();
    }

    @Test
    void userTasksEvents() {
        assertThat(publisher.userTasksEvents()).isFalse();
    }

    @Test
    void eventsDatabaseName() {
        assertThat(publisher.eventsDatabaseName()).isEqualTo("testDB");
    }

    @Test
    void processInstancesEventsCollection() {
        assertThat(publisher.processInstancesEventsCollection()).isEqualTo("testPICollection");
    }

    @Test
    void userTasksEventsCollection() {
        assertThat(publisher.userTasksEventsCollection()).isEqualTo("testUTCollection");
    }

}
