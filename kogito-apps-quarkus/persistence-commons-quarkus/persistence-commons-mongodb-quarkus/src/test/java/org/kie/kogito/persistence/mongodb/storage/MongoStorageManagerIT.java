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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.persistence.api.Storage;
import org.kie.kogito.testcontainers.quarkus.MongoDBQuarkusTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(MongoDBQuarkusTestResource.class)
class MongoStorageManagerIT {

    @Inject
    MongoStorageManager mongoStorageManager;

    Storage storage;

    @AfterEach
    void tearDown() {
        ((MongoStorage) storage).mongoCollection.drop();
    }

    @Test
    void testGetCache() {
        String storageName = "testCache";
        storage = mongoStorageManager.getCache(storageName);

        assertThat((storage instanceof MongoStorage)).isTrue();
        assertThat(((MongoStorage) storage).mongoCollection.getNamespace().getCollectionName()).isEqualTo(storageName);
    }

    @Test
    void testGetCacheWithClass() {
        String storageName = "testCacheWithClass";
        storage = mongoStorageManager.getCache(storageName, String.class);

        assertThat((storage instanceof MongoStorage)).isTrue();
        assertThat(((MongoStorage) storage).mongoCollection.getNamespace().getCollectionName()).isEqualTo(storageName);
    }

    @Test
    void getCacheWithDataFormat() {
        String storageName = "testCacheWithDataFormat";
        storage = mongoStorageManager.getCache(storageName, String.class, "type");

        assertThat((storage instanceof MongoStorage)).isTrue();
        assertThat(((MongoStorage) storage).mongoCollection.getNamespace().getCollectionName()).isEqualTo(storageName);
    }
}
