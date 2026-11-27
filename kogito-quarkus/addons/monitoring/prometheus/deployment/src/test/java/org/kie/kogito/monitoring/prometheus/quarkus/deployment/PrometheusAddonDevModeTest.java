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
package org.kie.kogito.monitoring.prometheus.quarkus.deployment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;

import static io.restassured.RestAssured.given;

/**
 * Dev-mode smoke test for the Prometheus monitoring add-on.
 *
 * With protobuf-java 3.25.5 dev mode fails in GrpcDevUIProcessor#collectMessagePrototypes
 * with NoSuchMethodError 'Descriptors$Descriptor Descriptors$FileDescriptor.getMessageType(int)'.
 * With protobuf-java 4.33.2 dev mode starts and /q/metrics returns 200.
 */
public class PrometheusAddonDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest().withEmptyApplication();

    @Test
    public void metricsMustBeReachableInDevMode() {
        given()
                .accept("text/plain") // request Prometheus text format, not OpenMetrics
                .when().get("/q/metrics")
                .then()
                .statusCode(200);
    }
}
