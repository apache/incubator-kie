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

import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(KubernetesMockServerTestResource.class)
public class DeploymentUtilsTest {

    private final String namespace = "serverless-workflow-greeting-quarkus";
    @Inject
    KubernetesClient client;
    @Inject
    KubernetesResourceDiscovery discovery;

    @Test
    public void testNotFoundDeployment() {
        Deployment deployment = client.apps().deployments().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deployment/deployment.yaml")).item();
        deployment.getMetadata().setName("test");

        KubeTestUtils.createWithStatusPreserved(client, deployment, namespace, Deployment.class);

        assertThat(discovery.query(KubernetesResourceUri.parse("deployments.v1.apps/" + namespace + "/invalid"))).isEqualTo(Optional.empty());
    }

    @Test
    public void testDeploymentWithService() {
        var kubeURI = KubernetesResourceUri.parse("deployments.v1.apps/" + namespace + "/example-deployment-with-service");

        Deployment deployment = client.apps().deployments().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deployment/deployment.yaml")).item();
        KubeTestUtils.createWithStatusPreserved(client, deployment, namespace, Deployment.class);

        Service service = client.services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deployment/deployment-service.yaml")).item();
        KubeTestUtils.createWithStatusPreserved(client, service, namespace, Service.class);

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://10.10.10.11:80");
    }

    @Test
    public void testDeploymentWithServiceWithCustomPortName() {
        var kubeURI = KubernetesResourceUri.parse("deployments.v1.apps/" + namespace + "/custom-port-deployment?port-name=my-custom-port");

        Deployment deployment = client.apps().deployments().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deployment/deployment.yaml")).item();
        deployment.getMetadata().setName("custom-port-deployment");
        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts()
                .add(new ContainerPortBuilder().withName("test-port").withContainerPort(4000).build());
        KubeTestUtils.createWithStatusPreserved(client, deployment, namespace, Deployment.class);

        Service service = client.services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deployment/deployment-service.yaml")).item();
        service.getMetadata().setName("custom-port-name-service");
        service.getSpec().getPorts().add(new ServicePortBuilder()
                .withName("my-custom-port")
                .withTargetPort(new IntOrString("test-port"))
                .withPort(4009).build());
        KubeTestUtils.createWithStatusPreserved(client, service, namespace, Service.class);

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://10.10.10.11:4009");
    }

    @Test
    public void testDeploymentNoService() {
        var kubeURI = KubernetesResourceUri.parse("deployments.v1.apps/" + namespace + "/example-deployment-no-service");

        Deployment deployment = client.apps().deployments().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deployment/deployment-no-service.yaml")).item();
        deployment = KubeTestUtils.createWithStatusPreserved(client, deployment, namespace, Deployment.class);

        ReplicaSet rs = client.apps().replicaSets().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deployment/replica-set-deployment-no-service.yaml")).item();
        rs.getMetadata().getOwnerReferences().get(0).setUid(deployment.getMetadata().getUid());
        rs = KubeTestUtils.createWithStatusPreserved(client, rs, namespace, ReplicaSet.class);

        Pod pod = client.pods().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deployment/pod-deployment-no-service.yaml")).item();
        pod.getMetadata().setName("pod-deployment-no-service");
        pod.getMetadata().getOwnerReferences().get(0).setUid(rs.getMetadata().getUid());
        KubeTestUtils.createWithStatusPreserved(client, pod, namespace, Pod.class);

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://172.17.0.11:8080");
    }

    @Test
    public void testDeploymentNoService2Replicas() {
        var kubeURI = KubernetesResourceUri.parse("deployments.v1.apps/" + namespace + "/example-deployment-no-service-2-replicas");

        Deployment deployment = client.apps().deployments().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deployment/deployment-no-service.yaml")).item();
        deployment.getMetadata().setName("example-deployment-no-service-2-replicas");
        deployment.getStatus().setReplicas(2);
        KubeTestUtils.createWithStatusPreserved(client, deployment, namespace, Deployment.class);

        ReplicaSet rs = client.apps().replicaSets().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deployment/replica-set-deployment-no-service.yaml")).item();
        rs.getMetadata().setName("rs-2-replicas");
        rs.getMetadata().getOwnerReferences().get(0).setUid(deployment.getMetadata().getUid());
        KubeTestUtils.createWithStatusPreserved(client, rs, namespace, ReplicaSet.class);

        Pod pod = client.pods().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deployment/pod-deployment-no-service.yaml")).item();
        pod.getMetadata().setName("pod-2-replicas");
        pod.getMetadata().getOwnerReferences().get(0).setUid(rs.getMetadata().getUid());
        KubeTestUtils.createWithStatusPreserved(client, pod, namespace, Pod.class);

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.isEmpty()).isTrue();
    }

    @Test
    public void testDeploymentNoServiceCustomPortName() {
        var kubeURI = KubernetesResourceUri.parse("deployments.v1.apps/" + namespace + "/custom-port-deployment-1?port-name=my-custom-port");

        Deployment deployment = client.apps().deployments().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deployment/deployment-no-service.yaml")).item();
        deployment.getMetadata().setName("custom-port-deployment-1");
        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts()
                .add(new ContainerPortBuilder().withName("test-port").withContainerPort(4000).build());
        deployment = KubeTestUtils.createWithStatusPreserved(client, deployment, namespace, Deployment.class);

        ReplicaSet rs = client.apps().replicaSets().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deployment/replica-set-deployment-no-service.yaml")).item();
        rs.getMetadata().setName("custom-port-rs");
        rs.getMetadata().getOwnerReferences().get(0).setUid(deployment.getMetadata().getUid());
        rs = KubeTestUtils.createWithStatusPreserved(client, rs, namespace, ReplicaSet.class);

        Pod pod = client.pods().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deployment/pod-deployment-no-service.yaml")).item();
        pod.getMetadata().getOwnerReferences().get(0).setUid(rs.getMetadata().getUid());
        pod.getSpec().getContainers().get(0).getPorts()
                .add(new ContainerPortBuilder().withName("my-custom-port").withContainerPort(4009).build());
        KubeTestUtils.createWithStatusPreserved(client, pod, namespace, Pod.class);

        Optional<String> url = discovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://172.17.0.11:4009");
    }
}
