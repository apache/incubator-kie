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
package org.kie.kogito.persistence.mongodb.storage;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.persistence.mongodb.client.MongoClientManager;
import org.kie.kogito.persistence.mongodb.mock.MockMongoEntityMapper;
import org.kie.kogito.testcontainers.quarkus.MongoDBQuarkusTestResource;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.persistence.mongodb.mock.MockMongoEntityMapper.TEST_ATTRIBUTE;
import static org.kie.kogito.persistence.mongodb.model.ModelUtils.MONGO_ID;

@QuarkusTest
@QuarkusTestResource(MongoDBQuarkusTestResource.class)
class MongoStorageIT {

    @Inject
    MongoClientManager mongoClientManager;

    MongoStorage<String, Document> storage;

    MongoCollection<Document> collection;

    @BeforeEach
    void setup() {
        collection = mongoClientManager.getCollection("test", Document.class);
        storage = new MongoStorage(collection, String.class.getName(), new MockMongoEntityMapper());
    }

    @AfterEach
    void tearDown() {
        collection.drop();
    }

    @Test
    void testContainsKey() {
        String testId = "testContains";
        collection.insertOne(new Document(MONGO_ID, testId));
        assertThat(storage.containsKey(testId)).isTrue();
    }

    @Test
    void testGet() {
        String testId = "testGet";
        String testValue = "testValue";
        collection.insertOne(new Document(MONGO_ID, testId).append(TEST_ATTRIBUTE, testValue));
        assertThat(storage.get(testId)).isEqualTo(testValue);
    }

    @Test
    void testPut() {
        String testId = "testPut";
        String testValue = "testValue";
        storage.put(testId, testValue);
        FindIterable<Document> findIterable = collection.find(new Document(MONGO_ID, testId));
        Document document = findIterable.first();
        assertThat(document).isNotNull();
        assertThat(document.get(TEST_ATTRIBUTE)).isEqualTo(testValue);
    }

    @Test
    void testClear() {
        String testId = "testClear";
        String testValue = "testValue";
        collection.insertOne(new Document(MONGO_ID, testId).append(TEST_ATTRIBUTE, testValue));
        storage.clear();
        assertThat(collection.countDocuments()).isEqualTo(0);
    }

    @Test
    void testRemove() {
        String testId = "testRemove";
        String testValue = "testValue";
        collection.insertOne(new Document(MONGO_ID, testId).append(TEST_ATTRIBUTE, testValue));
        storage.remove(testId);
        assertThat(collection.countDocuments()).isEqualTo(0);
    }
}
