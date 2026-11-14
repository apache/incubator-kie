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
package org.kie.kogito.tracing;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kie.kogito.test.utils.SocketUtils;
import org.kie.kogito.tracing.decision.TrustyConstants;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.http.ContentType;
import io.restassured.http.Headers;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.assertj.core.api.Assertions.assertThat;

public class QuarkusTracingAddonDevServicesIT {

    private static final String KOGITO_EXECUTION_ID_HEADER = "X-Kogito-execution-id";

    @RegisterExtension
    public static QuarkusDevModeTest test = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> {
                jar.addAsResource(new StringAsset(applicationProperties()),
                        "application.properties");
                jar.addAsResource(new StringAsset(loadResource("/LoanEligibility.dmn")),
                        "LoanEligibility.dmn");
            });

    private static String applicationProperties() {
        String loadedResource = loadResource("/application.properties");
        String replacement = String.format("quarkus.kogito.dev-services-trusty.port-to-use-in-test=%s",
                SocketUtils.findAvailablePort());
        String toReturn = loadedResource.replace("quarkus.kogito.dev-services-trusty.port-to-use-in-test=-1",
                replacement);
        return toReturn;
    }

    @Test
    public void testEvaluateLoanEligibility() {
        execute().then()
                .statusCode(200)
                .header(KOGITO_EXECUTION_ID_HEADER, notNullValue())
                .body("'Decide'", is(true));
    }

    @Test
    @Disabled("Not working, need debugging")
    public void testExecutionsAreStored() {
        final List<String> executionIds = new ArrayList<>();
        executionIds.add(executeAndGetExecutionId());
        executionIds.add(executeAndGetExecutionId());
        executionIds.add(executeAndGetExecutionId());

        final String trustyServiceEndpoint = System.getProperty(TrustyConstants.KOGITO_TRUSTY_SERVICE);

        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    final Response response = given().when().get(trustyServiceEndpoint + "/executions");
                    final String json = response.prettyPrint();
                    return executionIds.stream().allMatch(executionId -> json.contains("\"executionId\": \"" + executionId + "\""));
                });
    }

    private Response execute() {
        return given()
                .body("{" +
                        "    \"Client\": {" +
                        "        \"age\": 43," +
                        "        \"salary\": 1950," +
                        "        \"existing payments\": 100" +
                        "    }," +
                        "    \"Loan\": {" +
                        "        \"duration\": 15," +
                        "        \"installment\": 180" +
                        "    }," +
                        "    \"SupremeDirector\" : \"Yes\"," +
                        "    \"Bribe\": 1000" +
                        "}")
                .contentType(ContentType.JSON)
                .when()
                .post("/LoanEligibility");
    }

    private String executeAndGetExecutionId() {
        final Headers headers = execute().headers();

        assertThat(headers.hasHeaderWithName(KOGITO_EXECUTION_ID_HEADER)).isTrue();
        return headers.getValue(KOGITO_EXECUTION_ID_HEADER);
    }

    private static String loadResource(final String name) {
        String resource = null;
        try {
            final URL url = QuarkusTracingAddonDevServicesIT.class.getResource(name);
            if (Objects.nonNull(url)) {
                resource = Files.readString(Paths.get(url.toURI()), StandardCharsets.UTF_8);
            }
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException(String.format("Unable to load '%s'", name));
        }
        return resource;
    }
}