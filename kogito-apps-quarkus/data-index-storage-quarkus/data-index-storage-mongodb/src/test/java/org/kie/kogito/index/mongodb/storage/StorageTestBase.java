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

import org.kie.kogito.persistence.api.Storage;

import static org.assertj.core.api.Assertions.assertThat;

class StorageTestBase<K, V> {

    void testStorage(Storage<K, V> storage, K key, V value1, V value2) {
        assertThat(storage.get(key)).isNull();
        assertThat(storage.containsKey(key)).isFalse();

        storage.put(key, value1);
        assertThat(storage.get(key)).isEqualTo(value1);
        assertThat(storage.containsKey(key)).isTrue();

        storage.put(key, value2);
        assertThat(storage.get(key)).isEqualTo(value2);
        assertThat(storage.containsKey(key)).isTrue();

        storage.remove(key);
        assertThat(storage.get(key)).isNull();
        assertThat(storage.containsKey(key)).isFalse();
    }
}
