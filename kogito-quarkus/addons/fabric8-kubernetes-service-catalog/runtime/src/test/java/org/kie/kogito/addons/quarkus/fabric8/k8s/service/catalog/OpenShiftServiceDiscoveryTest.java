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
import org.kie.kogito.addons.quarkus.k8s.test.utils.KubernetesMockServerTestResource; // Change import

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(KubernetesMockServerTestResource.class) // Changed to use KubernetesMockServerTestResource
public class OpenShiftServiceDiscoveryTest {

    private final String namespace = "serverless-workflow-greeting-quarkus";

    @Inject
    OpenShiftResourceDiscovery kubeResourceDiscovery;

    @Inject
    OpenShiftClient client;

    @Test
    void testNotFoundDeploymentConfig() {
        DeploymentConfig deploymentConfig = client.deploymentConfigs().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deploymentConfig/deployment-config.yaml")).item();
        deploymentConfig.getMetadata().setName("test");

        KubeTestUtils.createWithStatusPreserved(client, deploymentConfig, namespace, DeploymentConfig.class);

        assertThat(kubeResourceDiscovery.query(KubernetesResourceUri.parse("deploymentconfigs.v1.apps.openshift.io/" + namespace + "/invalid"))).isEqualTo(Optional.empty());
    }

    @Test
    void testDeploymentConfigWithService() {
        var kubeURI = KubernetesResourceUri.parse("deploymentconfigs.v1.apps.openshift.io/" + namespace + "/example-dc-with-service");

        DeploymentConfig deploymentConfig = client.deploymentConfigs().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deploymentConfig/deployment-config.yaml")).item();
        KubeTestUtils.createWithStatusPreserved(client, deploymentConfig, namespace, DeploymentConfig.class);

        Service service = client.services().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deploymentConfig/deployment-config-service.yaml")).item();
        KubeTestUtils.createWithStatusPreserved(client, service, namespace, Service.class);

        Optional<String> url = kubeResourceDiscovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://10.10.10.12:80");
    }

    @Test
    void testDeploymentConfigWithoutService() {
        var kubeURI = KubernetesResourceUri.parse("deploymentconfigs.v1.apps.openshift.io/" + namespace + "/example-dc-no-service");

        DeploymentConfig deploymentConfig = client.deploymentConfigs().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deploymentConfig/deployment-config-no-service.yaml")).item();
        deploymentConfig = KubeTestUtils.createWithStatusPreserved(client, deploymentConfig, namespace, DeploymentConfig.class);

        ReplicationController rc = client.replicationControllers().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deploymentConfig/replication-controller-dc-no-svc.yaml")).item();
        rc.getMetadata().getOwnerReferences().get(0).setUid(deploymentConfig.getMetadata().getUid());
        rc = KubeTestUtils.createWithStatusPreserved(client, rc, namespace, ReplicationController.class);

        Pod pod = client.pods().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("deploymentConfig/pod-deployment-config-no-service.yaml")).item();
        pod.getMetadata().setName("example-dc-no-service-1-phlx4");
        pod.getMetadata().getOwnerReferences().get(0).setUid(rc.getMetadata().getUid());
        KubeTestUtils.createWithStatusPreserved(client, pod, namespace, Pod.class);

        Optional<String> url = kubeResourceDiscovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://172.17.25.190:8080");
    }

    @Test
    void testNotFoundRoute() {
        Route route = client.routes().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("route/route.yaml")).item();

        KubeTestUtils.createWithStatusPreserved(client, route, namespace, Route.class);

        assertThat(kubeResourceDiscovery.query(KubernetesResourceUri.parse("routes.v1.route.openshift.io/" + namespace + "/invalid"))).isEqualTo(Optional.empty());
    }

    @Test
    void testRoute() {
        var kubeURI = KubernetesResourceUri.parse("routes.v1.route.openshift.io/" + namespace + "/test-route");

        Route route = client.routes().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("route/route.yaml")).item();

        KubeTestUtils.createWithStatusPreserved(client, route, namespace, Route.class);

        Optional<String> url = kubeResourceDiscovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("http://test-route.org:80");
    }

    @Test
    void testRouteTLS() {
        var kubeURI = KubernetesResourceUri.parse("routes.v1.route.openshift.io/" + namespace + "/test-route-tls");

        Route route = client.routes().inNamespace(namespace)
                .load(getClass().getClassLoader().getResourceAsStream("route/route-tls.yaml")).item();

        KubeTestUtils.createWithStatusPreserved(client, route, namespace, Route.class);

        Optional<String> url = kubeResourceDiscovery.query(kubeURI).map(URI::toString);
        assertThat(url.get()).isEqualTo("https://secure-test-route-tls:443");
    }
}