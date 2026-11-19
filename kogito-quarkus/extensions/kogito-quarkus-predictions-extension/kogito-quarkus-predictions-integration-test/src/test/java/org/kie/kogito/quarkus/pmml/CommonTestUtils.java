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
package org.kie.kogito.quarkus.pmml;

import java.util.Map;

import org.hamcrest.core.IsNull;

import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

public class CommonTestUtils {

    public static void testResult(final String inputData,
            final String path,
            final String targetField,
            final Object expectedResult) {
        given()
                .contentType(ContentType.JSON)
                .body(inputData)
                .when()
                .post(path)
                .then()
                .statusCode(200)
                .body(targetField, is(expectedResult));
    }

    public static void testDescriptive(final String inputData,
            final String basePath,
            final String targetField,
            final Map<String, Object> expectedResultMap) {
        String path = basePath + "/descriptive";
        Object resultVariables = given()
                .contentType(ContentType.JSON)
                .body(inputData)
                .when()
                .post(path)
                .then()
                .statusCode(200)
                .body("correlationId", is(new IsNull()))
                .body("segmentationId", is(new IsNull()))
                .body("segmentId", is(new IsNull()))
                .body("segmentIndex", is(0)) // as JSON is not schema aware, here we assert the RAW string
                .body("resultCode", is("OK"))
                .body("resultObjectName", is(targetField))
                .extract()
                .path("resultVariables");
        assertThat(resultVariables).isNotNull();
        assertThat(resultVariables).isInstanceOf(Map.class);
        Map<String, Object> mappedResultVariables = (Map) resultVariables;
        expectedResultMap.forEach((key, value) -> {
            assertThat(mappedResultVariables).containsKey(key);
            assertThat(mappedResultVariables.get(key)).isEqualTo(value);
        });

    }

}
