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
package org.kie.kogito.quarkus.runtime;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.arc.DefaultBean;
import io.vertx.ext.web.client.WebClientOptions;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import static org.kogito.workitem.rest.RestWorkItemHandlerUtils.MAX_POOL_SIZE_PROPERTY;
import static org.kogito.workitem.rest.RestWorkItemHandlerUtils.sslWebClientOptions;

@ApplicationScoped
public class SSLWebClientOptionsProducer {

    private final Optional<Integer> maxPoolSize;

    @Inject
    public SSLWebClientOptionsProducer(@ConfigProperty(name = MAX_POOL_SIZE_PROPERTY) Optional<Integer> maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    @Produces
    @DefaultBean
    public WebClientOptions webClientOptions() {
        WebClientOptions options = sslWebClientOptions();
        maxPoolSize.ifPresent(options::setMaxPoolSize);
        return options;
    }
}
