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
package org.kie.kogito.persistence.postgresql.reporting.model;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresMappingDefinitionsTest {

    static final PostgresMappingDefinitions DEFINITIONS = new PostgresMappingDefinitions(List.of(PostgresMappingDefinitionTest.DEFINITION));
    static final int DEFINITIONS_HASHCODE = DEFINITIONS.hashCode();

    @Test
    void testEquality() {
        assertThat(DEFINITIONS).isEqualTo(DEFINITIONS);
        assertThat(new PostgresMappingDefinitions(List.of(new PostgresMappingDefinition("different",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(PostgresFieldTest.FIELD),
                List.of(PostgresPartitionFieldTest.PARTITION_FIELD),
                "targetTableName",
                List.of(PostgresMappingTest.MAPPING))))).isNotEqualTo(DEFINITIONS);
        assertThat(new PostgresMappingDefinitions(List.of(new PostgresMappingDefinition("mappingId",
                "different",
                "sourceTableJsonFieldName",
                List.of(PostgresFieldTest.FIELD),
                List.of(PostgresPartitionFieldTest.PARTITION_FIELD),
                "targetTableName",
                List.of(PostgresMappingTest.MAPPING))))).isNotEqualTo(DEFINITIONS);
        assertThat(new PostgresMappingDefinitions(List.of(new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "different",
                List.of(PostgresFieldTest.FIELD),
                List.of(PostgresPartitionFieldTest.PARTITION_FIELD),
                "targetTableName",
                List.of(PostgresMappingTest.MAPPING))))).isNotEqualTo(DEFINITIONS);
        assertThat(new PostgresMappingDefinitions(List.of(new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(PostgresFieldTest.FIELD, PostgresFieldTest.FIELD),
                List.of(PostgresPartitionFieldTest.PARTITION_FIELD),
                "targetTableName",
                List.of(PostgresMappingTest.MAPPING))))).isNotEqualTo(DEFINITIONS);
        assertThat(new PostgresMappingDefinitions(List.of(new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(PostgresFieldTest.FIELD),
                List.of(PostgresPartitionFieldTest.PARTITION_FIELD, PostgresPartitionFieldTest.PARTITION_FIELD),
                "targetTableName",
                List.of(PostgresMappingTest.MAPPING))))).isNotEqualTo(DEFINITIONS);
        assertThat(new PostgresMappingDefinitions(List.of(new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(PostgresFieldTest.FIELD),
                List.of(PostgresPartitionFieldTest.PARTITION_FIELD),
                "different",
                List.of(PostgresMappingTest.MAPPING))))).isNotEqualTo(DEFINITIONS);
        assertThat(new PostgresMappingDefinitions(List.of(new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(PostgresFieldTest.FIELD),
                List.of(PostgresPartitionFieldTest.PARTITION_FIELD),
                "targetTableName",
                List.of(PostgresMappingTest.MAPPING, PostgresMappingTest.MAPPING))))).isNotEqualTo(DEFINITIONS);
        assertThat(new PostgresMappingDefinitions(List.of(PostgresMappingDefinitionTest.DEFINITION,
                PostgresMappingDefinitionTest.DEFINITION))).isNotEqualTo(DEFINITIONS);
    }

    @Test
    void testHashCode() {
        assertThat(DEFINITIONS.hashCode()).isEqualTo(DEFINITIONS_HASHCODE);
        assertThat(new PostgresMappingDefinitions(List.of(new PostgresMappingDefinition("different",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(PostgresFieldTest.FIELD),
                List.of(PostgresPartitionFieldTest.PARTITION_FIELD),
                "targetTableName",
                List.of(PostgresMappingTest.MAPPING)))).hashCode()).isNotEqualTo(DEFINITIONS_HASHCODE);
        assertThat(new PostgresMappingDefinitions(List.of(new PostgresMappingDefinition("mappingId",
                "different",
                "sourceTableJsonFieldName",
                List.of(PostgresFieldTest.FIELD),
                List.of(PostgresPartitionFieldTest.PARTITION_FIELD),
                "targetTableName",
                List.of(PostgresMappingTest.MAPPING)))).hashCode()).isNotEqualTo(DEFINITIONS_HASHCODE);
        assertThat(new PostgresMappingDefinitions(List.of(new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "different",
                List.of(PostgresFieldTest.FIELD),
                List.of(PostgresPartitionFieldTest.PARTITION_FIELD),
                "targetTableName",
                List.of(PostgresMappingTest.MAPPING)))).hashCode()).isNotEqualTo(DEFINITIONS_HASHCODE);
        assertThat(new PostgresMappingDefinitions(List.of(new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(PostgresFieldTest.FIELD, PostgresFieldTest.FIELD),
                List.of(PostgresPartitionFieldTest.PARTITION_FIELD),
                "targetTableName",
                List.of(PostgresMappingTest.MAPPING)))).hashCode()).isNotEqualTo(DEFINITIONS_HASHCODE);
        assertThat(new PostgresMappingDefinitions(List.of(new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(PostgresFieldTest.FIELD),
                List.of(PostgresPartitionFieldTest.PARTITION_FIELD, PostgresPartitionFieldTest.PARTITION_FIELD),
                "targetTableName",
                List.of(PostgresMappingTest.MAPPING)))).hashCode()).isNotEqualTo(DEFINITIONS_HASHCODE);
        assertThat(new PostgresMappingDefinitions(List.of(new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(PostgresFieldTest.FIELD),
                List.of(PostgresPartitionFieldTest.PARTITION_FIELD),
                "different",
                List.of(PostgresMappingTest.MAPPING)))).hashCode()).isNotEqualTo(DEFINITIONS_HASHCODE);
        assertThat(new PostgresMappingDefinitions(List.of(new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(PostgresFieldTest.FIELD),
                List.of(PostgresPartitionFieldTest.PARTITION_FIELD),
                "targetTableName",
                List.of(PostgresMappingTest.MAPPING, PostgresMappingTest.MAPPING)))).hashCode()).isNotEqualTo(DEFINITIONS_HASHCODE);
        assertThat(new PostgresMappingDefinitions(List.of(PostgresMappingDefinitionTest.DEFINITION,
                PostgresMappingDefinitionTest.DEFINITION)).hashCode()).isNotEqualTo(DEFINITIONS_HASHCODE);
    }
}
