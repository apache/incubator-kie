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
package org.kie.kogito.test.utils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.test.utils.JsonSchemaMatchers.matchesJsonSchema;

class JsonSchemaMatchersTest {

    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2019-09/schema",
              "type": "object",
              "properties": { "name": { "type": "string" } },
              "required": ["name"]
            }
            """;

    private static Matcher<String> matcher() {
        return matchesJsonSchema(new ByteArrayInputStream(SCHEMA.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void validDocumentMatches() {
        assertThat(matcher().matches("{\"name\":\"pepe\"}")).isTrue();
    }

    @Test
    void invalidDocumentDoesNotMatch() {
        assertThat(matcher().matches("{\"name\":1}")).isFalse();
        assertThat(matcher().matches("{}")).isFalse();
    }
}
