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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.addons.quarkus.k8s.test.utils.KubeTestUtils;
import org.kie.kogito.addons.quarkus.k8s.test.utils.KubernetesMockServerTestResource;

import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(KubernetesMockServerTestResource.class)
public class StatefulSetUtilsTest {

    private final String namespace = "serverless-workflow-greeting-quarkus";

    @Inject
    KubernetesClient client;

    @Inject
    KubernetesResourceDiscovery discovery;

    @BeforeEach
    public void removeResources() {
        client.apps().statefulSets().inNamespace(namespace).delete();
        client.services().inNamespace(namespace).delete();
    }

    @Test
    public void testNotFoundStatefulSet() {
        StatefulSet statefulSet = client.apps().statefulSets().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("statefulset/statefulset-no-service.yaml")).item();
        statefulSet.getMetadata().setName("test");

        client.resource(statefulSet).inNamespace(namespace).createOr(existing -> client.resource(statefulSet).inNamespace(namespace).update());

        assertThat(discovery.query(KubernetesResourceUri.parse("statefulsets.v1.apps/" + namespace + "/invalid"))).isEqualTo(Optional.empty());
    }

    @Test
    public void testStatefulSetWithService() {
        var kubeURI = KubernetesResourceUri.parse("statefulsets.v1.apps/" + namespace + "/example-statefulset-with-service");

        StatefulSet statefulSet = client.apps().statefulSets().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("statefulset/statefulset.yaml")).item();

        client.resource(statefulSet).inNamespace(namespace).createOr(existing -> client.resource(statefulSet).inNamespace(namespace).update());

        Service service = client.services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("statefulset/statefulset-service.yaml")).item();

        client.resource(service).inNamespace(namespace).createOr(existing -> client.resource(service).inNamespace(namespace).update());

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://10.10.10.11:80");
    }

    @Test
    public void testStatefulSetWithServiceWithCustomPortName() {
        var kubeURI = KubernetesResourceUri.parse("statefulsets.v1.apps/" + namespace + "/custom-port-statefulset?port-name=my-custom-port-stateful");

        StatefulSet statefulSet = client.apps().statefulSets().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("statefulset/statefulset.yaml")).item();
        statefulSet.getMetadata().setName("custom-port-statefulset");
        statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts()
                .add(new ContainerPortBuilder().withName("test-port").withContainerPort(4000).build());
        statefulSet.getMetadata().getLabels().put("app-custom", "custom-port-statefulset");
        statefulSet.getMetadata().getLabels().remove("app");

        client.resource(statefulSet).inNamespace(namespace).createOr(existing -> client.resource(statefulSet).inNamespace(namespace).update());

        Service service = client.services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("statefulset/statefulset-service.yaml")).item();
        service.getMetadata().setName("custom-port-name-service-statefulset");
        service.getSpec().getPorts().add(new ServicePortBuilder()
                .withName("my-custom-port-stateful")
                .withTargetPort(new IntOrString("test-port"))
                .withPort(4009).build());
        service.getSpec().getSelector().put("app-custom", "custom-port-statefulset");
        service.getSpec().getSelector().remove("app");

        client.resource(service).inNamespace(namespace).createOr(existing -> client.resource(service).inNamespace(namespace).update());

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://10.10.10.11:4009");
    }

    @Test
    public void testStatefulSetNoService() {
        var kubeURI = KubernetesResourceUri.parse("statefulsets.v1.apps/" + namespace + "/process-quarkus-example-statefulset-no-service");

        StatefulSet statefulSet = client.apps().statefulSets().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("statefulset/statefulset-no-service.yaml")).item();

        statefulSet = KubeTestUtils.createWithStatusPreserved(client, statefulSet, namespace, StatefulSet.class);

        Pod pod = client.pods().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("statefulset/statefulset-pod-no-service.yaml")).item();
        pod.getMetadata().setName("pod-deployment-no-service");
        pod.getMetadata().getOwnerReferences().get(0).setUid(statefulSet.getMetadata().getUid());
        KubeTestUtils.createWithStatusPreserved(client, pod, namespace, Pod.class);

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://172.17.0.11:8080");
    }

    @Test
    public void testStatefulSetNoService2Replicas() {
        var kubeURI = KubernetesResourceUri.parse("statefulsets.v1.apps/" + namespace + "/example-statefulset-no-service-2-replicas");

        StatefulSet statefulSet = client.apps().statefulSets().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("statefulset/statefulset-no-service.yaml")).item();
        statefulSet.getMetadata().setName("example-statefulset-no-service-2-replicas");
        statefulSet.getStatus().setReplicas(2);

        statefulSet = KubeTestUtils.createWithStatusPreserved(client, statefulSet, namespace, StatefulSet.class);

        Pod pod = client.pods().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("statefulset/statefulset-pod-no-service.yaml")).item();
        pod.getMetadata().setName("pod-2-replicas");
        pod.getMetadata().getOwnerReferences().get(0).setUid(statefulSet.getMetadata().getUid());

        KubeTestUtils.createWithStatusPreserved(client, pod, namespace, Pod.class);

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.isEmpty()).isTrue();
    }

    @Test
    public void testStatefulSetNoServiceCustomPortName() {
        var kubeURI = KubernetesResourceUri.parse("statefulsets.v1.apps/" + namespace + "/custom-port-statefulset-1?port-name=my-custom-port");

        StatefulSet statefulSet = client.apps().statefulSets().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("statefulset/statefulset-no-service.yaml")).item();
        statefulSet.getMetadata().setName("custom-port-statefulset-1");
        statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts()
                .add(new ContainerPortBuilder().withName("test-port").withContainerPort(4000).build());

        StatefulSet createdStatefulSet =
                client.resource(statefulSet).inNamespace(namespace).createOr(existing -> client.resource(statefulSet).inNamespace(namespace).update());

        Pod pod = client.pods().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("statefulset/statefulset-pod-no-service.yaml")).item();
        pod.getMetadata().getOwnerReferences().get(0).setUid(createdStatefulSet.getMetadata().getUid());
        pod.getSpec().getContainers().get(0).getPorts()
                .add(new ContainerPortBuilder().withName("my-custom-port").withContainerPort(4010).build());

        client.resource(pod).inNamespace(namespace).createOr(existing -> client.resource(pod).inNamespace(namespace).update());

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://172.17.0.11:4010");
    }
}
