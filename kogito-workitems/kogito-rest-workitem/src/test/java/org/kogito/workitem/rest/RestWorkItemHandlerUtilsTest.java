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
package org.kogito.workitem.rest;

import org.junit.jupiter.api.Test;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.ext.web.client.WebClientOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kogito.workitem.rest.RestWorkItemHandlerUtils.httpWebClientOptions;
import static org.kogito.workitem.rest.RestWorkItemHandlerUtils.sslWebClientOptions;

class RestWorkItemHandlerUtilsTest {

    @Test
    void testHttpOptionsKeepVertxDefaultPoolSize() {
        assertThat(httpWebClientOptions(sslWebClientOptions()).getMaxPoolSize()).isEqualTo(HttpClientOptions.DEFAULT_MAX_POOL_SIZE);
    }

    @Test
    void testHttpOptionsTakePoolSizeFromSslOptions() {
        WebClientOptions sslOptions = sslWebClientOptions().setMaxPoolSize(20);
        assertThat(httpWebClientOptions(sslOptions).getMaxPoolSize()).isEqualTo(20);
    }

    @Test
    void testHttpOptionsAreNotSsl() {
        assertThat(httpWebClientOptions(sslWebClientOptions()).isSsl()).isFalse();
    }

    @Test
    void testHttpOptionsMirrorOtherSslSettingsBesidesPoolSize() {
        WebClientOptions sslOptions = sslWebClientOptions().setMaxPoolSize(20).setConnectTimeout(12345).setKeepAlive(false);
        WebClientOptions httpOptions = httpWebClientOptions(sslOptions);

        assertThat(httpOptions.getConnectTimeout()).isEqualTo(12345);
        assertThat(httpOptions.isKeepAlive()).isFalse();
    }

    @Test
    void testHttpOptionsAreIndependentFromSslOptions() {
        WebClientOptions sslOptions = sslWebClientOptions().setMaxPoolSize(20);
        WebClientOptions httpOptions = httpWebClientOptions(sslOptions);

        sslOptions.setMaxPoolSize(40);

        assertThat(httpOptions.getMaxPoolSize()).isEqualTo(20);
    }
}
