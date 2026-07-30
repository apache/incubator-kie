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
package org.kie.kogito.integrationtests.quarkus;

import java.net.URL;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusIntegrationTest
class OASIT {

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @TestHTTPResource("/")
    URL rootUrl;

    @Test
    public void testOASisValid() {
        String url = rootUrl.toString() + "/q/openapi"; // default location since Quarkus v1.10
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(url, null, parseOptions);

        // Should fix https://issues.redhat.com/browse/KOGITO-5734
        assertThat(result.getMessages()).isEmpty();

        OpenAPI openAPI = result.getOpenAPI();
        PathItem p1 = openAPI.getPaths().get("/approvals");
        assertThat(p1).isNotNull();
        assertThat(p1.getGet()).isNotNull();
        assertThat(p1.getPost()).isNotNull();
    }

    @Test
    public void testApprovalsSchemaProperties() {
        String url = rootUrl.toString() + "/q/openapi";
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(url, null, parseOptions);

        OpenAPI openAPI = result.getOpenAPI();
        PathItem p1 = openAPI.getPaths().get("/approvals");
        assertThat(p1).isNotNull();

        Schema requestSchema = p1.getPost().getRequestBody().getContent().get("application/json").getSchema();
        String schemaName = requestSchema.get$ref().substring(requestSchema.get$ref().lastIndexOf('/') + 1);
        Schema resolvedSchema = openAPI.getComponents().getSchemas().get(schemaName);
        assertThat(resolvedSchema).isNotNull();

        assertThat(resolvedSchema.getProperties().keySet())
                .containsExactlyInAnyOrder("approver", "traveller", "firstLineApproval", "secondLineApproval");
    }

}
