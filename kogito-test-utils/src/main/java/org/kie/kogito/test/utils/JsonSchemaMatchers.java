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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Set;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Hamcrest matchers validating a JSON document against a JSON Schema (draft 2019-09).
 * <p>
 * Replacement for {@code io.restassured.module.jsv.JsonSchemaValidator}, which depends on the
 * {@code com.github.java-json-tools} libraries (jackson-coreutils, json-patch) affected by unresolved CVEs.
 */
public final class JsonSchemaMatchers {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSchemaMatchers() {
    }

    /**
     * Matches a JSON string that is valid against the schema read from the given stream.
     * Reading the schema from a stream (instead of a URI) also works for the {@code quarkus://} class loader scheme.
     */
    public static Matcher<String> matchesJsonSchema(InputStream schema) {
        return new JsonSchemaMatcher(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909).getSchema(schema));
    }

    /**
     * Matches a JSON string that is valid against the schema found in the classpath at the given location.
     */
    public static Matcher<String> matchesJsonSchemaInClasspath(String classpathResource) {
        try (InputStream schema = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathResource)) {
            if (schema == null) {
                throw new IllegalArgumentException("JSON schema not found in classpath: " + classpathResource);
            }
            return matchesJsonSchema(schema);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class JsonSchemaMatcher extends TypeSafeMatcher<String> {

        private final JsonSchema schema;

        private JsonSchemaMatcher(JsonSchema schema) {
            this.schema = schema;
        }

        @Override
        protected boolean matchesSafely(String json) {
            return validate(json).isEmpty();
        }

        @Override
        protected void describeMismatchSafely(String json, Description mismatchDescription) {
            mismatchDescription.appendText("JSON does not match the schema: ").appendValueList("", "; ", "", validate(json));
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("a JSON document valid against the JSON schema");
        }

        private Set<ValidationMessage> validate(String json) {
            try {
                return schema.validate(MAPPER.readTree(json));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
