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
package org.kie.kogito.persistence.postgresql.reporting.api;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.kie.kogito.persistence.postgresql.reporting.model.JsonType;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresField;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresJsonField;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresMapping;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresMappingDefinition;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresMappingDefinitions;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresPartitionField;
import org.kie.kogito.persistence.reporting.model.MappingDefinition;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostgresMappingsApiV1IT {

    @Test
    @Order(1)
    void getAllMappingDefinitions() {
        PostgresMappingDefinitions response = given().contentType(ContentType.TEXT).when().get("/mappings").prettyPeek().as(PostgresMappingDefinitions.class);
        assertThat(response).isNotNull();
        assertMappingDefinitions(List.of("BasicTypeMappingId",
                "HierarchicalTypeMappingId",
                "ComplexHierarchicalTypeMappingId"));
    }

    @Test
    @Order(2)
    void getMappingDefinition() {
        PostgresMappingDefinition response = given().contentType(ContentType.TEXT).when().get("/mappings/BasicTypeMappingId").prettyPeek().as(PostgresMappingDefinition.class);
        assertThat(response).isNotNull();
        assertThat(response.getMappingId()).isEqualTo("BasicTypeMappingId");
        assertThat(response.getFieldMappings().size()).isEqualTo(2);
    }

    @Test
    @Order(3)
    void dynamicMappingDefinitionCreation() {
        final PostgresMappingDefinition definition = new PostgresMappingDefinition("ExampleMappingId",
                "kogito_data_cache",
                "json_value",
                List.of(new PostgresField("var_name")),
                List.of(new PostgresPartitionField("cache_name", "Example")),
                "Example",
                List.of(new PostgresMapping("field1",
                        new PostgresJsonField("field1", JsonType.STRING))));

        Response response = given().contentType(ContentType.JSON).body(definition).when().post("/mappings").prettyPeek();
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertMappingDefinitions(List.of("BasicTypeMappingId",
                "HierarchicalTypeMappingId",
                "ComplexHierarchicalTypeMappingId",
                "ExampleMappingId"));

        PostgresMappingDefinition newMappingDefinition = given().contentType(ContentType.TEXT).when().get("/mappings/ExampleMappingId").prettyPeek().as(PostgresMappingDefinition.class);
        assertThat(newMappingDefinition).isNotNull();
        assertThat(newMappingDefinition.getMappingId()).isEqualTo("ExampleMappingId");
        assertThat(newMappingDefinition.getFieldMappings().size()).isEqualTo(1);
    }

    @Test
    @Order(4)
    void dynamicMappingDefinitionDestruction() {
        assertMappingDefinitions(List.of("BasicTypeMappingId",
                "HierarchicalTypeMappingId",
                "ComplexHierarchicalTypeMappingId",
                "ExampleMappingId"));

        Response response = given().when().delete("/mappings/ExampleMappingId").prettyPeek();
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertMappingDefinitions(List.of("BasicTypeMappingId",
                "HierarchicalTypeMappingId",
                "ComplexHierarchicalTypeMappingId"));
    }

    private void assertMappingDefinitions(final List<String> expectedMappingIds) {
        PostgresMappingDefinitions response = given().contentType(ContentType.TEXT).when().get("/mappings").prettyPeek().as(PostgresMappingDefinitions.class);
        assertThat(response).isNotNull();
        assertThat(response.getMappingDefinitions().size()).isEqualTo(expectedMappingIds.size());
        assertThat(response.getMappingDefinitions().stream().map(MappingDefinition::getMappingId).collect(Collectors.toList()).containsAll(expectedMappingIds)).isTrue();
    }

}
