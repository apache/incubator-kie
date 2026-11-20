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
package org.kie.kogito.trusty.storage.redis;

import org.junit.jupiter.api.Test;
import org.kie.kogito.persistence.redis.RedisClientManager;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.trusty.storage.common.TrustyStorageService.COUNTERFACTUAL_REQUESTS_STORAGE;
import static org.kie.kogito.trusty.storage.common.TrustyStorageService.COUNTERFACTUAL_RESULTS_STORAGE;
import static org.kie.kogito.trusty.storage.common.TrustyStorageService.DECISIONS_STORAGE;
import static org.kie.kogito.trusty.storage.common.TrustyStorageService.LIME_RESULTS_STORAGE;
import static org.kie.kogito.trusty.storage.common.TrustyStorageService.MODELS_STORAGE;

public class IndexProviderTest {

    @Test
    public void indexesAreCreated() {
        RedisIndexManagerMock redisIndexManager = new RedisIndexManagerMock(Mockito.mock(RedisClientManager.class));
        IndexProvider indexProvider = new IndexProvider(redisIndexManager);

        indexProvider.createIndexes();

        assertThat(redisIndexManager.getIndexNames().size()).isEqualTo(5);
        assertThat(redisIndexManager.getIndexNames().contains(DECISIONS_STORAGE)).isTrue();
        assertThat(redisIndexManager.getIndexNames().contains(MODELS_STORAGE)).isTrue();
        assertThat(redisIndexManager.getIndexNames().contains(LIME_RESULTS_STORAGE)).isTrue();
        assertThat(redisIndexManager.getIndexNames().contains(COUNTERFACTUAL_REQUESTS_STORAGE)).isTrue();
        assertThat(redisIndexManager.getIndexNames().contains(COUNTERFACTUAL_RESULTS_STORAGE)).isTrue();
    }
}
