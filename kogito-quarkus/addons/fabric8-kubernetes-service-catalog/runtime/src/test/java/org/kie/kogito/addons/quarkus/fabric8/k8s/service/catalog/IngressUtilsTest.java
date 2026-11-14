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
package org.kie.kogito.addons.quarkus.fabric8.k8s.service.catalog;

import java.net.URI;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.kie.kogito.addons.quarkus.k8s.test.utils.KubeTestUtils;
import org.kie.kogito.addons.quarkus.k8s.test.utils.KubernetesMockServerTestResource;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test covers the queryIngressByName method from {@link IngressUtils}
 */
@QuarkusTest
@QuarkusTestResource(KubernetesMockServerTestResource.class)
public class IngressUtilsTest {

    private final String namespace = "serverless-workflow-greeting-quarkus";

    @Inject
    KubernetesResourceDiscovery discovery;

    @Inject
    KubernetesClient client;

    @Test
    void testIngressNotFound() {
        Ingress ingress = client.network().v1().ingresses()
                .inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("ingress/ingress-with-ip.yaml"))
                .item();

        KubeTestUtils.createWithStatusPreserved(client, ingress, namespace, Ingress.class);

        assertThat(discovery.query(KubernetesResourceUri.parse("ingresses.v1.networking.k8s.io/" + namespace + "/invalid"))).isEqualTo(Optional.empty());
    }

    @Test
    void testIngressWithIP() {
        var kubeURI = KubernetesResourceUri.parse("ingresses.v1.networking.k8s.io/" + namespace + "/process-quarkus-ingress");

        Ingress ingress = client.network().v1().ingresses()
                .inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("ingress/ingress-with-ip.yaml"))
                .item();

        KubeTestUtils.createWithStatusPreserved(client, ingress, namespace, Ingress.class);

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://80.80.25.9:80");
    }

    @Test
    void testIngressWithTLS() {
        var kubeURI = KubernetesResourceUri.parse("ingresses.v1.networking.k8s.io/" + namespace + "/hello-app-ingress-tls");

        Ingress ingress = client.network().v1().ingresses()
                .inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("ingress/ingress-with-tls-and-host.yaml"))
                .item();

        KubeTestUtils.createWithStatusPreserved(client, ingress, namespace, Ingress.class);

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("https://80.80.25.9:443");
    }
}
