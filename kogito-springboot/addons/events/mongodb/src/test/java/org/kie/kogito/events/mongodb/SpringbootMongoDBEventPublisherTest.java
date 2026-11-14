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
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.mongodb.transaction.AbstractTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { SpringbootMongoDBEventPublisher.class, SpringbootMongoDBEventPublisherTest.TestConfig.class },
        properties = { "kogito.events.processinstances.enabled=false", "kogito.events.usertasks.enabled=false", "kogito.events.variables.enabled=false",
                "kogito.events.processinstances.collection=testPICollection", "kogito.events.usertasks.collection=testUTCollection",
                "kogito.events.variables.collection=testVCollection", "kogito.events.database=testDB" })
class SpringbootMongoDBEventPublisherTest {

    @TestConfiguration
    public static class TestConfig {
        @Bean
        @Primary
        public MongoClient mockMongoClient() {
            MongoClient mongoClient = mock(MongoClient.class);
            MongoDatabase mongoDatabase = mock(MongoDatabase.class);
            MongoCollection mongoCollection = mock(MongoCollection.class);
            when(mongoClient.getDatabase(any())).thenReturn(mongoDatabase);
            when(mongoDatabase.withCodecRegistry(any())).thenReturn(mongoDatabase);
            when(mongoDatabase.getCollection(any(), any())).thenReturn(mongoCollection);
            when(mongoCollection.withCodecRegistry(any())).thenReturn(mongoCollection);
            return mongoClient;
        }
    }

    @Autowired
    MongoClient mongoClient;

    @MockitoBean
    AbstractTransactionManager transactionManager;

    @Autowired
    SpringbootMongoDBEventPublisher publisher;

    @Test
    void setup() {
        publisher.setupSpringbootMongoDBEventPublisher();
        verify(mongoClient, atLeastOnce()).getDatabase(eq("testDB"));
    }

    @Test
    void mongoClient() {
        assertThat(publisher.mongoClient()).isEqualTo(mongoClient);
    }

    @Test
    void setTransactionManager() {
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
