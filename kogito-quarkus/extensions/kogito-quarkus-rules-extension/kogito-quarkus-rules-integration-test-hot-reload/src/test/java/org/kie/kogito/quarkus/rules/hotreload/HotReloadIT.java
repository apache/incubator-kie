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
package org.kie.kogito.quarkus.rules.hotreload;

import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kie.kogito.test.utils.SocketUtils;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HotReloadIT {

    private static final String PACKAGE = "org.kie.kogito.quarkus.rules.hotreload";
    private static final String RESOURCE_FILE = PACKAGE.replace('.', '/') + "/adult.drl";

    // Pick a free port up front and hand it to the dev-mode app through the archive's
    // application.properties; since Quarkus 3.33 dev mode no longer writes its resolved
    // port back for tests to read.
    final static int httpPort = SocketUtils.findAvailablePort();

    @RegisterExtension
    final static QuarkusDevModeTest test = new QuarkusDevModeTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addAsResource(new StringAsset("quarkus.kogito.devservices.enabled=false\nquarkus.http.port=" + httpPort), "application.properties")
                            .addAsResource("adult.txt", RESOURCE_FILE));

    @Test
    public void testServletChange() {
        String personsPayload = "{\"persons\":[{\"name\":\"Mario\",\"age\":45,\"adult\":false},{\"name\":\"Sofia\",\"age\":17,\"adult\":false}]}";

        List<String> names = given()
                .baseUri("http://localhost:" + httpPort)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(personsPayload)
                .when()
                .post("/find-adult-names")
                .then()
                .statusCode(200)
                .extract()
                .as(List.class);

        assertEquals(1, names.size());
        assertEquals("Mario", names.get(0));

        test.modifyResourceFile(RESOURCE_FILE, s -> s.replaceAll("18", "16"));

        names = given()
                .baseUri("http://localhost:" + httpPort)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(personsPayload).when()
                .post("/find-adult-names")
                .then()
                .statusCode(200)
                .extract().as(List.class);

        assertEquals(2, names.size());
        assertTrue(names.contains("Mario"));
        assertTrue(names.contains("Sofia"));
    }
}
