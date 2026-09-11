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
package org.kie.kogito.quarkus.decisions.hotreload;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kie.kogito.test.utils.SocketUtils;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

public class SimpleModifyHotReloadIT {

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private static final String PACKAGE = "org.kie.kogito.quarkus.decisions.hotreload";
    private static final String RESOURCE_FILE_PATH = PACKAGE.replace('.', '/');
    private static final String DMN_RESOURCE_FILE = RESOURCE_FILE_PATH + "/TrafficViolation.dmn";

    // Pick a free port up front and hand it to the dev-mode app through the archive's
    // application.properties; since Quarkus 3.33 dev mode no longer writes its resolved
    // port back for tests to read.
    final static int httpPort = SocketUtils.findAvailablePort();

    @RegisterExtension
    final static QuarkusDevModeTest test =
            new QuarkusDevModeTest().setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addAsResource(new StringAsset("kogito.decisions.stronglytyped=true\nquarkus.kogito.devservices.enabled=false\nquarkus.http.port=" + httpPort),
                                    "application.properties")
                            .addAsResource("TrafficViolation.txt", DMN_RESOURCE_FILE));

    @Test
    void simpleHotReloadTest() {
        executeTest("No");

        // --- Change #1
        test.modifyResourceFile(DMN_RESOURCE_FILE, s -> s.replaceAll("if Total Points >= 20 then \"Yes\" else \"No\"",
                "if Total Points >= 2 then \"Yes\" else \"No\""));

        executeTest("Yes");

        // --- Change #2
        test.modifyResourceFile(DMN_RESOURCE_FILE, s -> s.replaceAll("if Total Points >= 2 then \"Yes\" else \"No\"",
                "if Total Points >= 20 then \"Yes\" else \"No\""));
        executeTest("No");
    }

    private void executeTest(String result) {
        ValidatableResponse response = given()
                .baseUri("http://localhost:" + httpPort)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("{\n" +
                        "    \"Driver\": {\n" +
                        "        \"Points\": 2\n" +
                        "    },\n" +
                        "    \"Violation\": {\n" +
                        "        \"Type\": \"speed\",\n" +
                        "        \"Actual Speed\": 120,\n" +
                        "        \"Speed Limit\": 100\n" +
                        "    }\n" +
                        "}")
                .contentType(ContentType.JSON)
                .when()
                .post("/Traffic Violation")
                .then();

        response.statusCode(200)
                .body("'Should the driver be suspended?'", is(result));
    }

}
