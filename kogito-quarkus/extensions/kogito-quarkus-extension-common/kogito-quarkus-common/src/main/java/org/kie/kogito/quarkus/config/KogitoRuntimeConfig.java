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
package org.kie.kogito.quarkus.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "kogito")
public interface KogitoRuntimeConfig {

    /**
     * The service URL needed to connect to the runtime endpoint from outside the service.
     * <p>
     */
    @WithName("service.url")
    Optional<String> serviceUrl();

    /**
     * Eventing runtime configuration
     */
    @WithName("quarkus.events")
    KogitoEventingRuntimeConfig eventingConfig();

    /**
     * Maximum number of process instance to be returned by GET api
     */
    @WithName("process.instances.limit")
    @WithDefault("1000")
    short processInstanceLimit();

    /**
     * Auth Configuration
     */
    @WithName("security.auth")
    KogitoAuthRuntimeConfig authConfig();

    /**
     * Maximum number of HTTP connections the REST work item handler keeps open per target host.
     * When not set, the Vert.x default applies.
     */
    @WithName("workitem.rest.max-pool-size")
    Optional<Integer> workitemRestMaxPoolSize();
}
