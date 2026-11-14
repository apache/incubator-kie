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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.persistence.mongodb.client.MongoClientManager;
import org.kie.kogito.persistence.mongodb.mock.MockMongoEntityMapper;
import org.kie.kogito.persistence.mongodb.model.MongoEntityMapper;
import org.kie.kogito.testcontainers.quarkus.MongoDBQuarkusTestResource;

import com.mongodb.client.MongoCollection;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static com.mongodb.client.model.Filters.eq;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.persistence.mongodb.model.ModelUtils.MONGO_ID;
import static org.kie.kogito.persistence.mongodb.storage.MongoStorage.OPERATION_TYPE;
import static org.kie.kogito.persistence.mongodb.storage.StorageUtils.watchCollectionEntries;
import static org.kie.kogito.persistence.mongodb.storage.StorageUtils.watchCollectionKeys;

@QuarkusTest
@QuarkusTestResource(MongoDBQuarkusTestResource.class)
class StorageUtilsIT {

    @Inject
    MongoClientManager mongoClientManager;

    @AfterEach
    void tearDown() {
        mongoClientManager.getCollection("test").drop();
    }

    @Test
    void testWatchCollection_insert() throws Exception {
        MongoCollection<Document> mongoCollection = mongoClientManager.getCollection("test");
        MongoEntityMapper<String, Document> mongoEntityMapper = new MockMongoEntityMapper();

        TestListener testListenerInsert1 = new TestListener(2);
        watchCollectionEntries(mongoCollection, eq(OPERATION_TYPE, "insert"), mongoEntityMapper).subscribe().with(testListenerInsert1::add);

        TestListener testListenerInsert2 = new TestListener(2);
        watchCollectionEntries(mongoCollection, eq(OPERATION_TYPE, "insert"), mongoEntityMapper).subscribe().with(testListenerInsert2::add);

        mongoCollection.insertOne(mongoEntityMapper.mapToEntity("testKey1", "testValue1"));
        mongoCollection.insertOne(mongoEntityMapper.mapToEntity("testKey2", "testValue2"));

        testListenerInsert1.await();
        testListenerInsert2.await();
        assertThat(testListenerInsert1.items.size()).isEqualTo(2);
        assertThat(testListenerInsert1.items.keySet().containsAll(asList("testValue1", "testValue2"))).isTrue();
        assertThat(testListenerInsert2.items.size()).isEqualTo(2);
        assertThat(testListenerInsert2.items.keySet().containsAll(asList("testValue1", "testValue2"))).isTrue();
    }

    @Test
    void testWatchCollection_update() throws Exception {
        MongoCollection<Document> mongoCollection = mongoClientManager.getCollection("test");
        MongoEntityMapper<String, Document> mongoEntityMapper = new MockMongoEntityMapper();

        TestListener testListenerUpdate1 = new TestListener(2);
        watchCollectionEntries(mongoCollection, eq(OPERATION_TYPE, "replace"), mongoEntityMapper).subscribe().with(testListenerUpdate1::add);

        TestListener testListenerUpdate2 = new TestListener(2);
        watchCollectionEntries(mongoCollection, eq(OPERATION_TYPE, "replace"), mongoEntityMapper).subscribe().with(testListenerUpdate2::add);

        mongoCollection.insertOne(mongoEntityMapper.mapToEntity("testKey1", "testValue1"));
        mongoCollection.insertOne(mongoEntityMapper.mapToEntity("testKey2", "testValue2"));

        mongoCollection.replaceOne(new Document(MONGO_ID, "testKey1"), mongoEntityMapper.mapToEntity("testKey1", "testValue3"));
        mongoCollection.replaceOne(new Document(MONGO_ID, "testKey2"), mongoEntityMapper.mapToEntity("testKey2", "testValue4"));

        testListenerUpdate1.await();
        testListenerUpdate2.await();
        assertThat(testListenerUpdate1.items.size()).isEqualTo(2);
        assertThat(testListenerUpdate1.items.keySet().containsAll(asList("testValue3", "testValue4"))).isTrue();
        assertThat(testListenerUpdate2.items.size()).isEqualTo(2);
        assertThat(testListenerUpdate2.items.keySet().containsAll(asList("testValue3", "testValue4"))).isTrue();
    }

    @Test
    void testWatchCollection_delete() throws Exception {
        MongoCollection<Document> mongoCollection = mongoClientManager.getCollection("test");
        MongoEntityMapper<String, Document> mongoEntityMapper = new MockMongoEntityMapper();

        TestListener testListenerRemove1 = new TestListener(2);
        watchCollectionKeys(mongoCollection, eq(OPERATION_TYPE, "delete")).subscribe().with(testListenerRemove1::add);

        TestListener testListenerRemove2 = new TestListener(2);
        watchCollectionKeys(mongoCollection, eq(OPERATION_TYPE, "delete")).subscribe().with(testListenerRemove2::add);

        mongoCollection.insertOne(mongoEntityMapper.mapToEntity("testKey1", "testValue1"));
        mongoCollection.insertOne(mongoEntityMapper.mapToEntity("testKey2", "testValue2"));

        mongoCollection.deleteOne(new Document(MONGO_ID, "testKey1"));
        mongoCollection.deleteOne(new Document(MONGO_ID, "testKey2"));

        testListenerRemove1.await();
        testListenerRemove2.await();
        assertThat(testListenerRemove1.items.size()).isEqualTo(2);
        assertThat(testListenerRemove1.items.keySet().containsAll(asList("testKey1", "testKey2"))).isTrue();
        assertThat(testListenerRemove2.items.size()).isEqualTo(2);
        assertThat(testListenerRemove2.items.keySet().containsAll(asList("testKey1", "testKey2"))).isTrue();
    }

    static class TestListener {

        volatile Map<String, String> items = new ConcurrentHashMap<>();
        CountDownLatch latch;

        TestListener(int count) {
            latch = new CountDownLatch(count);
        }

        void await() throws InterruptedException {
            latch.await(10L, TimeUnit.SECONDS);
        }

        void add(String item) {
            items.put(item, item);
            latch.countDown();
        }
    }
}
