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
package org.kie.kogito.addons.quarkus.k8s;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.kie.kogito.addons.k8s.Endpoint;
import org.kie.kogito.addons.k8s.EndpointDiscovery;
import org.kie.kogito.addons.quarkus.k8s.test.utils.KubernetesMockServerTestResource;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@QuarkusTestResource(KubernetesMockServerTestResource.class)
public class KubernetesServiceEndpointDiscoveryTest {

    @Inject
    KubernetesClient client;

    @Named("default")
    @Inject
    EndpointDiscovery endpointDiscovery;

    private void createServiceIfNotExist(final String name, Map<String, String> labels, Integer... ports) {
        if (client.services().inNamespace("test").withName(name).get() != null) {
            return;
        }

        final List<ServicePort> sPorts = new ArrayList<>();
        for (Integer portNumber : ports) {
            final ServicePort port = new ServicePort();
            port.setPort(portNumber);
            sPorts.add(port);
        }

        final Service svc = new ServiceBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace("test")
                .withLabels(labels)
                .endMetadata()
                .withSpec(new ServiceSpec())
                .build();

        svc.getSpec().setClusterIP("127.0.0.1");
        svc.getSpec().setPorts(sPorts);

        client.resource(svc)
                .inNamespace("test")
                .createOr(existing -> client.resource(svc).inNamespace("test").update());
    }

    @Test
    public void testGetURLOnStandardPort() {
        createServiceIfNotExist("svc1", Collections.emptyMap(), 80, 8776);
        final Optional<Endpoint> endpoint = endpointDiscovery.findEndpoint("test", "svc1");
        assertThat(endpoint.isPresent()).isTrue();
        assertThat(endpoint.get().getUrl().isEmpty()).isFalse();
        try {
            new URL(endpoint.get().getUrl());
        } catch (MalformedURLException e) {
            fail("The generated URL " + endpoint.get().getUrl() + " is invalid");
        }
    }

    @Test
    public void testGetURLOnRandomPort() {
        createServiceIfNotExist("svc2", Collections.singletonMap("app", "test1"), 8778);
        final List<Endpoint> endpoints = endpointDiscovery.findEndpoint("test", Collections.singletonMap("app", "test1"));
        assertThat(endpoints.isEmpty()).isFalse();
        assertThat(endpoints.get(0).getUrl().isEmpty()).isFalse();
        try {
            new URL(endpoints.get(0).getUrl());
        } catch (MalformedURLException e) {
            fail("The generated URL " + endpoints.get(0).getUrl() + " is invalid");
        }
    }
}
