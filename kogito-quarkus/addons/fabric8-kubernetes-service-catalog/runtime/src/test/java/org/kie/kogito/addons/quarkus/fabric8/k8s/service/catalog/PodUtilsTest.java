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
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.kie.kogito.addons.quarkus.k8s.test.utils.KubeTestUtils;
import org.kie.kogito.addons.quarkus.k8s.test.utils.KubernetesMockServerTestResource;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(KubernetesMockServerTestResource.class)
public class PodUtilsTest {

    private final String namespace = "serverless-workflow-greeting-quarkus";

    @Inject
    KubernetesClient client;

    @Inject
    KubernetesResourceDiscovery discovery;

    @Test
    void testPodNotFound() {
        Pod pod = client.pods().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("pod/pod-no-service.yaml")).item();
        pod.getMetadata().setName("test-pod");

        KubeTestUtils.createWithStatusPreserved(client, pod, namespace, Pod.class);

        assertThat(discovery.query(KubernetesResourceUri.parse("pods.v1/" + namespace + "/hello"))).isEqualTo(Optional.empty());
    }

    @Test
    void testPodWithNoService() {
        final String tempNamespace = "temp-namespace";
        var kubeURI = KubernetesResourceUri.parse("pods.v1/" + tempNamespace + "/test-pod-no-svc");

        Pod pod = client.pods().inNamespace(tempNamespace)
                .load(getClass().getClassLoader().getResourceAsStream("pod/pod-no-service.yaml")).item();
        pod.getMetadata().setName("test-pod-no-svc");

        KubeTestUtils.createWithStatusPreserved(client, pod, tempNamespace, Pod.class);

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://172.17.0.21:8080");
    }

    @Test
    void testPodWithNoServiceCustomPortName() {
        var kubeURI = KubernetesResourceUri.parse("pods.v1/" + namespace + "/pod-no-service-custom-port?port-name=my-custom-port");

        Pod pod = client.pods().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("pod/pod-no-service-custom-port-name.yaml")).item();

        KubeTestUtils.createWithStatusPreserved(client, pod, namespace, Pod.class);

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://172.17.0.22:52485");
    }

    @Test
    void testPodWithService() {
        var kubeURI = KubernetesResourceUri.parse("pods.v1/" + namespace + "/test-pod-with-service");

        Pod pod = client.pods().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("pod/pod-no-service.yaml")).item();
        pod.getMetadata().setName("test-pod-with-service");

        KubeTestUtils.createWithStatusPreserved(client, pod, namespace, Pod.class);

        Service service = client.services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("service/service-clusterip.yaml")).item();

        KubeTestUtils.createWithStatusPreserved(client, service, namespace, Service.class);

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://10.10.10.10:80");
    }

    @Test
    void testPodWithServiceWithCustomLabel() {
        var kubeURI = KubernetesResourceUri.parse("pods.v1/" + namespace + "/test-pod-with-service-custom-label?labels=label-name=test-label;other-label=other-value");

        Pod pod = client.pods().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("pod/pod-no-service.yaml")).item();
        pod.getMetadata().setName("test-pod-with-service-custom-label");

        KubeTestUtils.createWithStatusPreserved(client, pod, namespace, Pod.class);

        Service service = client.services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("service/service-clusterip.yaml")).item();
        service.getMetadata().setName("process-quarkus-example-pod-clusterip-svc-custom-label");

        KubeTestUtils.createWithStatusPreserved(client, service, namespace, Service.class);

        Service service1 = client.services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("service/service-clusterip.yaml")).item();
        Map<String, String> labels = service1.getMetadata().getLabels();
        labels.put("label-name", "test-label");
        service1.getMetadata().setLabels(labels);
        service1.getMetadata().setName("second-service");
        service1.getSpec().setClusterIP("20.20.20.20");

        KubeTestUtils.createWithStatusPreserved(client, service1, namespace, Service.class);

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://20.20.20.20:80");
    }
}
