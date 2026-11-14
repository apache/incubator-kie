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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kie.kogito.addons.quarkus.fabric8.k8s.service.catalog.GVK.DEPLOYMENT;
import static org.kie.kogito.addons.quarkus.fabric8.k8s.service.catalog.GVK.DEPLOYMENT_CONFIG;
import static org.kie.kogito.addons.quarkus.fabric8.k8s.service.catalog.GVK.INGRESS;
import static org.kie.kogito.addons.quarkus.fabric8.k8s.service.catalog.GVK.KNATIVE_SERVICE;
import static org.kie.kogito.addons.quarkus.fabric8.k8s.service.catalog.GVK.POD;
import static org.kie.kogito.addons.quarkus.fabric8.k8s.service.catalog.GVK.ROUTE;
import static org.kie.kogito.addons.quarkus.fabric8.k8s.service.catalog.GVK.SERVICE;
import static org.kie.kogito.addons.quarkus.fabric8.k8s.service.catalog.GVK.STATEFUL_SET;

public class KubeURIParserGVKTest {

    @Test
    public void testValidGVK() {
        KubernetesResourceUri address = KubernetesResourceUri.parse("deployments.v1.apps/default/kogito-app-1");
        assertThat(address.getGvk()).isEqualTo(DEPLOYMENT);
        assertThat(address.getResourceName()).isEqualTo("kogito-app-1");

        KubernetesResourceUri url1 = KubernetesResourceUri.parse("deploymentconfigs.v1.apps.openshift.io/default/kogito-app-1");
        assertThat(url1.getGvk()).isEqualTo(DEPLOYMENT_CONFIG);
        assertThat(url1.getResourceName()).isEqualTo("kogito-app-1");

        KubernetesResourceUri address2 = KubernetesResourceUri.parse("statefulsets.v1.apps/namespace2/kogito-app-1");
        assertThat(address2.getGvk()).isEqualTo(STATEFUL_SET);
        assertThat(address2.getResourceName()).isEqualTo("kogito-app-1");

        KubernetesResourceUri address3 = KubernetesResourceUri.parse("statefulsets.v1.apps/namespace2/kogito-app-1");
        assertThat(address3.getGvk()).isEqualTo(STATEFUL_SET);
        assertThat(address3.getResourceName()).isEqualTo("kogito-app-1");

        KubernetesResourceUri address4 = KubernetesResourceUri.parse("services.v1/namespace2/kogito-app-1");
        assertThat(address4.getGvk()).isEqualTo(SERVICE);
        assertThat(address4.getResourceName()).isEqualTo("kogito-app-1");

        KubernetesResourceUri address5 = KubernetesResourceUri.parse("services.v1/namespace2/kogito-app-1");
        assertThat(address5.getGvk()).isEqualTo(SERVICE);
        assertThat(address5.getResourceName()).isEqualTo("kogito-app-1");

        KubernetesResourceUri address6 = KubernetesResourceUri.parse("routes.v1.route.openshift.io/namespace10/kogito-app-1");
        assertThat(address6.getGvk()).isEqualTo(ROUTE);
        assertThat(address6.getResourceName()).isEqualTo("kogito-app-1");

        KubernetesResourceUri address7 = KubernetesResourceUri.parse("ingresses.v1.networking.k8s.io/namespace9/kogito-app-1");
        assertThat(address7.getGvk()).isEqualTo(INGRESS);
        assertThat(address7.getResourceName()).isEqualTo("kogito-app-1");

        KubernetesResourceUri address8 = KubernetesResourceUri.parse("pods.v1/namespace9/kogito-app-1");
        assertThat(address8.getGvk()).isEqualTo(POD);
        assertThat(address8.getResourceName()).isEqualTo("kogito-app-1");

        KubernetesResourceUri url9 = KubernetesResourceUri.parse("deploymentconfigs.v1.apps.openshift.io/default/kogito-app-1");
        assertThat(url9.getGvk()).isEqualTo(DEPLOYMENT_CONFIG);
        assertThat(url9.getResourceName()).isEqualTo("kogito-app-1");

        KubernetesResourceUri url10 = KubernetesResourceUri.parse("services.v1.serving.knative.dev/default/knative-app-1");
        assertThat(url10.getGvk()).isEqualTo(KNATIVE_SERVICE);
        assertThat(url10.getResourceName()).isEqualTo("knative-app-1");
    }

    @Test
    public void testInvalidGVK() {
        assertThatThrownBy(() -> KubernetesResourceUri.parse("authorization.openshift.io/v1/roleBinding/default/kogito-app-3"))
                .isExactlyInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> KubernetesResourceUri.parse("v40/invalid/deployment/default/kogito-app-3"))
                .isExactlyInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> KubernetesResourceUri.parse("authorization.openshift.io/v1/roleBinding/kogito-app-3"))
                .isExactlyInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> KubernetesResourceUri.parse("deploymentconfigs.v1.apps.openshift.ios/default/kogito-app2"))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testValidGVKWithNoNamespace() {
        KubernetesResourceUri url = KubernetesResourceUri.parse("deployments.v1.apps/kogito-app-1");
        assertThat(url.getGvk()).isEqualTo(DEPLOYMENT);
        assertThat(url.getResourceName()).isEqualTo("kogito-app-1");

        KubernetesResourceUri url1 = KubernetesResourceUri.parse("services.v1/kogito-app-2");
        assertThat(url1.getGvk()).isEqualTo(SERVICE);
        assertThat(url1.getResourceName()).isEqualTo("kogito-app-2");

        KubernetesResourceUri url2 = KubernetesResourceUri.parse("services.v1/kogito-app-2");
        assertThat(url2.getGvk()).isEqualTo(SERVICE);
        assertThat(url2.getResourceName()).isEqualTo("kogito-app-2");

        KubernetesResourceUri url3 = KubernetesResourceUri.parse("deploymentconfigs.v1.apps.openshift.io/kogito-app-3");
        assertThat(url3.getGvk()).isEqualTo(DEPLOYMENT_CONFIG);
        assertThat(url3.getResourceName()).isEqualTo("kogito-app-3");

    }

    @Test
    public void testEmptyGVKValues() {
        assertThatThrownBy(() -> KubernetesResourceUri.parse("kubernetes:roleBinding/kogito-app-3"))
                .isExactlyInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> KubernetesResourceUri.parse("apps.openshift.io/deploymentconfig/kogito-app-3"))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }
}
