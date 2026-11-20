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

import org.junit.jupiter.api.Test;
import org.kie.kogito.addons.k8s.EndpointDiscovery;
import org.kie.kogito.addons.k8s.workitems.AbstractDiscoveredEndpointCaller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Very basic check to verify that our beans are being created.
 * No actions to perform since we don't have support to the Kubernetes mocked server natively on SB.
 */
@SpringBootTest(classes = { App.class })
public class BeansCreationSanityTest {

    @Autowired
    EndpointDiscovery endpointDiscovery;

    @Autowired
    AbstractDiscoveredEndpointCaller discoveredEndpointCaller;

    @Test
    void verifyBeansCreation() {
        assertThat(endpointDiscovery).isNotNull();
        assertThat(discoveredEndpointCaller).isNotNull();
    }

}
