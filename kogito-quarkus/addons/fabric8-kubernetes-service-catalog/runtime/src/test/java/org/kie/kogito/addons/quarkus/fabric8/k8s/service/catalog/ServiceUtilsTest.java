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

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This tests also covers the findServicePort method
 * from {@link PortUtils}
 */
public class ServiceUtilsTest {

    @Test
    public void testExternalNameServiceKind() {
        String externalName = "kourier-internal.kourier-system.svc.cluster.local";
        var kubeURI = KubernetesResourceUri.parse("services.v1/serverless-workflow-greeting-quarkus/greeting-quarkus-cli");
        Service service = new ServiceBuilder()
                .withNewMetadata().withName("my-service").endMetadata()
                .withNewSpec()
                .addToSelector("app", "Myapp")
                .addNewPort().withName("http2").withProtocol("TCP").withPort(80).withTargetPort(new IntOrString(80)).endPort()
                .withType("ExternalName").withSessionAffinity("None")
                .withExternalName(externalName)
                .withInternalTrafficPolicy("Cluster")
                .endSpec()
                .build();
        assertThat(ServiceUtils.getURLFromService(service, kubeURI).get()).isEqualTo(URI.create("http://" + externalName + ":" + 80));
    }

    @Test
    public void testClusterIPServiceKind() {
        var kubeURI = KubernetesResourceUri.parse("services.v1/serverless-workflow-greeting-quarkus/greeting-quarkus-cli");
        Service service = new ServiceBuilder()
                .withNewMetadata().withName("my-service").endMetadata()
                .withNewSpec()
                .addToSelector("app", "Myapp")
                .addNewPort().withName("http").withProtocol("TCP").withPort(80).withTargetPort(new IntOrString(8112)).endPort()
                .withType("ClusterIP").withSessionAffinity("None")
                .withClusterIP("10.10.10.10")
                .withInternalTrafficPolicy("Cluster")
                .endSpec()
                .build();
        assertThat(ServiceUtils.getURLFromService(service, kubeURI).get()).isEqualTo(URI.create("http://10.10.10.10:80"));
    }

    @Test
    public void testClusterIPServiceKindWithSSL() {
        var kubeURI = KubernetesResourceUri.parse("services.v1/serverless-workflow-greeting-quarkus/greeting-quarkus-cli");
        Service service = new ServiceBuilder()
                .withNewMetadata().withName("my-service").endMetadata()
                .withNewSpec()
                .addToSelector("app", "Myapp")
                .addNewPort().withName("http").withProtocol("TCP").withPort(80).withTargetPort(new IntOrString(8112)).endPort()
                .addNewPort().withName("https").withProtocol("TCP").withPort(443).withTargetPort(new IntOrString(8443)).endPort()
                .withType("ClusterIP").withSessionAffinity("None")
                .withClusterIP("10.10.10.10")
                .withInternalTrafficPolicy("Cluster")
                .endSpec()
                .build();
        assertThat(ServiceUtils.getURLFromService(service, kubeURI).get()).isEqualTo(URI.create("https://10.10.10.10:443"));
    }

    @Test
    public void testClusterIPServiceKindWithWebNamedPort() {
        var kubeURI = KubernetesResourceUri.parse("services.v1/serverless-workflow-greeting-quarkus/greeting-quarkus-cli");
        Service service = new ServiceBuilder()
                .withNewMetadata().withName("my-service").endMetadata()
                .withNewSpec()
                .addToSelector("app", "Myapp")
                .addNewPort().withName("someothername").withProtocol("TCP").withPort(80).withTargetPort(new IntOrString(8112)).endPort()
                .addNewPort().withName("web").withProtocol("TCP").withPort(801).withTargetPort(new IntOrString(8112)).endPort()
                .withType("ClusterIP").withSessionAffinity("None")
                .withClusterIP("10.10.10.10")
                .withInternalTrafficPolicy("Cluster")
                .endSpec()
                .build();
        assertThat(ServiceUtils.getURLFromService(service, kubeURI).get()).isEqualTo(URI.create("http://10.10.10.10:801"));
    }

    @Test
    public void testClusterIPServiceKindWithAleatoryNamePort() {
        var kubeURI = KubernetesResourceUri.parse("services.v1/serverless-workflow-greeting-quarkus/greeting-quarkus-cli");
        Service service = new ServiceBuilder()
                .withNewMetadata().withName("my-service").endMetadata()
                .withNewSpec()
                .addToSelector("app", "Myapp")
                .addNewPort().withName("someothername").withProtocol("TCP").withPort(820).withTargetPort(new IntOrString(8112)).endPort()
                .withType("ClusterIP").withSessionAffinity("None")
                .withClusterIP("10.10.10.10")
                .withInternalTrafficPolicy("Cluster")
                .endSpec()
                .build();
        assertThat(ServiceUtils.getURLFromService(service, kubeURI).get()).isEqualTo(URI.create("http://10.10.10.10:820"));
    }

    @Test
    public void testNodePortServiceKind() {
        var kubeURI = KubernetesResourceUri.parse("services.v1/serverless-workflow-greeting-quarkus/greeting-quarkus-cli");
        Service service = new ServiceBuilder()
                .withNewMetadata().withName("my-service").endMetadata()
                .withNewSpec()
                .addToSelector("app", "Myapp")
                .addNewPort().withName("http2").withProtocol("TCP").withPort(809).withTargetPort(new IntOrString(80)).withNodePort(30893).endPort()
                .withType("NodePort").withSessionAffinity("None")
                .withClusterIP("10.10.10.10")
                .withInternalTrafficPolicy("Cluster")
                .endSpec()
                .build();
        assertThat(ServiceUtils.getURLFromService(service, kubeURI).get()).isEqualTo(URI.create("http://10.10.10.10:809"));
    }

    @Test
    public void testClusterIPServiceKindWithCustomPortName() {
        var kubeURI = KubernetesResourceUri.parse("services.v1/serverless-workflow-greeting-quarkus/greeting-quarkus-cli?port-name=my-custom-port");
        Service service = new ServiceBuilder()
                .withNewMetadata().withName("my-service").endMetadata()
                .withNewSpec()
                .addToSelector("app", "Myapp")
                .addNewPort().withName("http").withProtocol("TCP").withPort(80).withTargetPort(new IntOrString(8112)).endPort()
                .addNewPort().withName("https").withProtocol("TCP").withPort(443).withTargetPort(new IntOrString(8443)).endPort()
                .addNewPort().withName("my-custom-port").withProtocol("TCP").withPort(8080).withTargetPort(new IntOrString(8009)).endPort()
                .withType("ClusterIP").withSessionAffinity("None")
                .withClusterIP("10.10.10.10")
                .withInternalTrafficPolicy("Cluster")
                .endSpec()
                .build();
        assertThat(ServiceUtils.getURLFromService(service, kubeURI).get()).isEqualTo(URI.create("http://10.10.10.10:8080"));
    }

    @Test
    public void testLoadBalancerServiceKind() {
        var kubeURI = KubernetesResourceUri.parse("services.v1/serverless-workflow-greeting-quarkus/greeting-quarkus-cli");
        Service service = new ServiceBuilder()
                .withNewMetadata().withName("my-service").endMetadata()
                .withNewSpec()
                .addToSelector("app", "Myapp")
                .addNewPort().withName("http2").withProtocol("TCP").withPort(80).withTargetPort(new IntOrString(80)).endPort()
                .withType(KubeConstants.LOAD_BALANCER_TYPE).withSessionAffinity("None")
                .withInternalTrafficPolicy("Cluster")
                .endSpec()
                .build();

        assertThat(ServiceUtils.getURLFromService(service, kubeURI)).isEqualTo(Optional.empty());
    }

    @Test
    public void testUnsupportedServiceKind() {
        Service service = new ServiceBuilder()
                .withNewMetadata().withName("my-service").endMetadata()
                .withNewSpec()
                .addToSelector("app", "Myapp")
                .addNewPort().withName("http2").withProtocol("TCP").withPort(80).withTargetPort(new IntOrString(80)).endPort()
                .withType("InvalidKind").withSessionAffinity("None")
                .withInternalTrafficPolicy("Cluster")
                .endSpec()
                .build();

        assertThat(ServiceUtils.getURLFromService(service, null)).isEqualTo(Optional.empty());
    }

}
