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

import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisExhaustedPoolException;

import static org.assertj.core.api.Assertions.assertThat;

public class RedisStorageExceptionsProviderImplTest {
    @Test
    public void testConnectionExceptions() {
        RedisStorageExceptionsProviderImpl redisStorageExceptionsProvider = new RedisStorageExceptionsProviderImpl();
        assertThat(redisStorageExceptionsProvider.isConnectionException(new JedisConnectionException("I'm a connection exception"))).isTrue();
        assertThat(redisStorageExceptionsProvider.isConnectionException(new JedisExhaustedPoolException("I'm a connection exception"))).isTrue();
        assertThat(redisStorageExceptionsProvider.isConnectionException(new RuntimeException("I'm not a connection exception"))).isFalse();
    }
}
