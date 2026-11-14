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
package io.quarkus.it.kogito.process;

import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kie.kogito.test.utils.SocketUtils;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import net.jcip.annotations.NotThreadSafe;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;

@NotThreadSafe
public class HotReloadTest {

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private static final String PACKAGE = "io.quarkus.it.kogito.jbpm";
    private static final String PACKAGE_FOLDER = PACKAGE.replace('.', '/');
    private static final String RESOURCE_FILE = PACKAGE_FOLDER + "/text-process.bpmn";

    private static final String PROCESS_NAME = "text_process";

    final static int httpPort = SocketUtils.findAvailablePort();

    @RegisterExtension
    final static QuarkusDevModeTest test = new QuarkusDevModeTest()
            .setArchiveProducer(
                    () -> ShrinkWrap
                            .create(JavaArchive.class)
                            .addClass(HotReloadTestHelper.class)
                            .addAsResource(new StringAsset("quarkus.kogito.devservices.enabled=false\nquarkus.http.port=" + httpPort), "application.properties")
                            .addAsResource("text-process.bpmn", RESOURCE_FILE));

    @Test
    @SuppressWarnings("unchecked")
    public void testJavaFileChange() {
        String payload = "{\"mytext\": \"HeLlO\"}";

        String id =
                given()
                        .baseUri("http://localhost:" + httpPort)
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body(payload)
                        .when()
                        .post("/" + PROCESS_NAME)
                        .then()
                        .statusCode(201)
                        .header("Location", notNullValue())
                        .body("id", notNullValue())
                        .extract()
                        .path("id");

        Map<String, String> result = given()
                .baseUri("http://localhost:" + httpPort)
                .accept(ContentType.JSON)
                .when()
                .get("/" + PROCESS_NAME + "/{id}", id)
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get("mytext")).isEqualTo("HELLO");

        test.modifySourceFile(HotReloadTestHelper.class, s -> s.replace("toUpperCase", "toLowerCase"));

        id = given()
                .baseUri("http://localhost:" + httpPort)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(payload)
                .when()
                .post("/" + PROCESS_NAME)
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .header("Location", notNullValue())
                .extract()
                .path("id");

        result = given()
                .baseUri("http://localhost:" + httpPort)
                .accept(ContentType.JSON)
                .when()
                .get("/" + PROCESS_NAME + "/{id}", id)
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get("mytext")).isEqualTo("hello");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRenameProcess() {
        String payload = "{\"mytext\": \"HeLlO\"}";

        String id =
                given()
                        .baseUri("http://localhost:" + httpPort)
                        .contentType(ContentType.JSON)
                        .accept(ContentType.JSON)
                        .body(payload)
                        .when()
                        .post("/" + PROCESS_NAME)
                        .then()
                        .statusCode(201)
                        .header("Location", notNullValue())
                        .body("id", notNullValue())
                        .extract()
                        .path("id");

        Map<String, String> result = given()
                .baseUri("http://localhost:" + httpPort)
                .accept(ContentType.JSON)
                .when()
                .get("/" + PROCESS_NAME + "/{id}", id)
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get("mytext")).isEqualTo("HELLO");

        test.modifyResourceFile(RESOURCE_FILE, s -> s.replaceAll(PROCESS_NAME, "new_" + PROCESS_NAME));

        id = given()
                .baseUri("http://localhost:" + httpPort)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(payload)
                .when()
                .post("/new_" + PROCESS_NAME)
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .header("Location", notNullValue())
                .extract()
                .path("id");

        result = given()
                .baseUri("http://localhost:" + httpPort)
                .accept(ContentType.JSON)
                .when()
                .get("/new_" + PROCESS_NAME + "/{id}", id)
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get("mytext")).isEqualTo("HELLO");
    }

    @Test
    public void testProcessJsonSchema() {
        String jsonSchema = given()
                .baseUri("http://localhost:" + httpPort)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .when()
                .get("/" + PROCESS_NAME + "/schema")
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(jsonSchema).isNotNull();
        assertThat(jsonSchema.isEmpty()).isFalse();

        test.modifyResourceFile(RESOURCE_FILE, s -> s.replaceAll(PROCESS_NAME, "new_" + PROCESS_NAME));

        // old endpoint should not work anymore
        given()
                .baseUri("http://localhost:" + httpPort)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .when()
                .get("/" + PROCESS_NAME + "/schema")
                .then()
                .statusCode(404);

        String newJsonSchema = given()
                .baseUri("http://localhost:" + httpPort)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .when()
                .get("/new_" + PROCESS_NAME + "/schema")
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(newJsonSchema).isNotNull();
        assertThat(newJsonSchema.isEmpty()).isFalse();

        assertThat(newJsonSchema).isEqualTo(jsonSchema);
    }

    @Test
    public void testUserTaskJsonSchema() {
        String jsonSchema = given()
                .baseUri("http://localhost:" + httpPort)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .when()
                .get("/" + PROCESS_NAME + "/Task/schema")
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(jsonSchema).isNotNull();
        assertThat(jsonSchema.isEmpty()).isFalse();

        // rename Task name (<![CDATA[Task]]>)
        test.modifyResourceFile(RESOURCE_FILE, s -> s.replaceAll("\\[Task]", "[Task1]"));

        // old endpoint should not work anymore
        given()
                .baseUri("http://localhost:" + httpPort)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .when()
                .get("/" + PROCESS_NAME + "/Task/schema")
                .then()
                .statusCode(404);

        String newJsonSchema = given()
                .baseUri("http://localhost:" + httpPort)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .when()
                .get("/" + PROCESS_NAME + "/Task1/schema")
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(newJsonSchema).isNotNull();
        assertThat(newJsonSchema.isEmpty()).isFalse();

        assertThat(newJsonSchema).isEqualTo(jsonSchema);
    }

}