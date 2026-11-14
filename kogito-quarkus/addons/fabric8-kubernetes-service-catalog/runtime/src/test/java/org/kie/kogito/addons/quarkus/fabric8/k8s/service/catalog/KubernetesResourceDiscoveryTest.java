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

import io.fabric8.knative.client.KnativeClient;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test also covers the queryServiceByName method from {@link ServiceUtils}
 */
@QuarkusTest
@QuarkusTestResource(KubernetesMockServerTestResource.class)
public class KubernetesResourceDiscoveryTest {

    private final String namespace = "serverless-workflow-greeting-quarkus";

    @Inject
    KubernetesClient client;

    @Inject
    KubernetesResourceDiscovery kubernetesResourceDiscovery;

    @Test
    public void testServiceNodePort() {
        var kubeURI = KubernetesResourceUri.parse("services.v1/" + namespace + "/process-quarkus-example-pod-service");

        Service service = client.services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("service/service-node-port.yaml")).item();

        KubeTestUtils.createWithStatusPreserved(client, service, namespace, Service.class);

        Optional<String> url = kubernetesResourceDiscovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://10.10.10.10:80");
    }

    @Test
    public void testServiceNodePortCustomPortName() {
        var kubeURI = KubernetesResourceUri.parse("services.v1/" + namespace + "/custom-port-name-service?port-name=my-custom-port");

        Service service = client.services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("service/service-node-port.yaml")).item();
        service.getMetadata().setName("custom-port-name-service");
        service.getSpec().getPorts().get(0).setName("my-custom-port");
        service.getSpec().getPorts().get(0).setPort(8089);

        KubeTestUtils.createWithStatusPreserved(client, service, namespace, Service.class);

        Optional<String> url = kubernetesResourceDiscovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://10.10.10.10:8089");
    }

    @Test
    public void testServiceClusterIP() {
        var kubeURI = KubernetesResourceUri.parse("services.v1/" + namespace + "/process-quarkus-example-pod-clusterip-svc");

        Service service = client.services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("service/service-clusterip.yaml")).item();

        KubeTestUtils.createWithStatusPreserved(client, service, namespace, Service.class);

        Optional<String> url = kubernetesResourceDiscovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://10.10.10.10:80");
    }

    @Test
    public void testServiceExternalName() {
        var kubeURI = KubernetesResourceUri.parse("services.v1/" + namespace + "/process-quarkus-example-pod");

        Service service = client.services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("service/service-external-name.yaml")).item();

        KubeTestUtils.createWithStatusPreserved(client, service, namespace, Service.class);

        Optional<String> url = kubernetesResourceDiscovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://my-public.domain.org:80");
    }

    @Test
    public void testNotFoundService() {
        Service service = client.services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("service/service-clusterip.yaml")).item();

        KubeTestUtils.createWithStatusPreserved(client, service, namespace, Service.class);

        assertThat(kubernetesResourceDiscovery.query(KubernetesResourceUri.parse("services.v1/" + namespace + "/service-1"))).isEqualTo(Optional.empty());
    }

    @Test
    public void testNotSupportedTypeService() {
        Service service = client.services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("service/service-clusterip.yaml")).item();
        service.getSpec().setType(KubeConstants.LOAD_BALANCER_TYPE);

        KubeTestUtils.createWithStatusPreserved(client, service, namespace, Service.class);

        assertThat(kubernetesResourceDiscovery.query(KubernetesResourceUri.parse("services.v1/" + namespace + "/process-quarkus-example-pod-clusterip-svc"))).isEqualTo(Optional.empty());
    }

    @Test
    public void testServiceWithoutNamespace() {
        var kubeURI = KubernetesResourceUri.parse("services.v1/svc-no-port");

        Service service = client.services().inNamespace("default")
                .load(getClass().getClassLoader().getResourceAsStream("service/service-node-port.yaml")).item();
        service.getMetadata().setName("svc-no-port");

        KubeTestUtils.createWithStatusPreserved(client, service, "default", Service.class);

        Optional<String> url = kubernetesResourceDiscovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://10.10.10.10:80");
    }

    @Test
    public void testNotFoundKnativeService() {
        KnativeClient knativeClient = client.adapt(KnativeClient.class);

        io.fabric8.knative.serving.v1.Service service = knativeClient.services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("knative/quarkus-greeting.yaml")).item();
        service.getMetadata().setName("test");

        KubeTestUtils.createWithStatusPreserved(client, service, namespace, io.fabric8.knative.serving.v1.Service.class);

        assertThat(kubernetesResourceDiscovery.query(KubernetesResourceUri.parse("services.v1.serving.knative.dev/" + namespace + "/invalid"))).isEqualTo(Optional.empty());
    }

    @Test
    public void testKnativeService() {
        var kubeURI = KubernetesResourceUri.parse("services.v1.serving.knative.dev/" + namespace + "/serverless-workflow-greeting-quarkus");

        io.fabric8.knative.serving.v1.Service kService = client.adapt(KnativeClient.class).services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("knative/quarkus-greeting.yaml")).item();

        KubeTestUtils.createWithStatusPreserved(client, kService, namespace, io.fabric8.knative.serving.v1.Service.class);

        Optional<String> url = kubernetesResourceDiscovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://serverless-workflow-greeting-quarkus.default.10.99.154.147.sslip.io");
    }
}
