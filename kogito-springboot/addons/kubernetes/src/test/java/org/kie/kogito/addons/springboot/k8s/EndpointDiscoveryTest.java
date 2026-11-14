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
package org.kie.kogito.addons.springboot.k8s;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.kie.kogito.addons.k8s.Endpoint;
import org.kie.kogito.addons.k8s.EndpointDiscovery;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unlike Quarkus, Spring doesn't have support for an integrated Kubernetes client mock environment.
 * In this case, just to do a quick check in the internals, we manually set the mocked client into the bean factory.
 * We don't need a full check in the API since the Quarkus counterpart is already doing it.
 */
@EnableKubernetesMockClient(https = false)
public class EndpointDiscoveryTest {

    static KubernetesClient kubernetesClient;

    @Test
    void verifyKubernetesIntegration() {
        final EndpointDiscovery mockedEndpointDiscovery = new EndpointDiscoveryConfig().endpointDiscovery(kubernetesClient);
        final Optional<Endpoint> endpoint = mockedEndpointDiscovery.findEndpoint("test", "test");
        assertThat(endpoint.isEmpty()).isTrue(); // we haven't created anything
    }

}
