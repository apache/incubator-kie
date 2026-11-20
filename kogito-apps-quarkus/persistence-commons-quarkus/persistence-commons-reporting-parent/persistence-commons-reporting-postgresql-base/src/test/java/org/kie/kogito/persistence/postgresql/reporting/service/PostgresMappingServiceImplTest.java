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
package org.kie.kogito.persistence.postgresql.reporting.service;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.persistence.api.Storage;
import org.kie.kogito.persistence.postgresql.PostgresStorageService;
import org.kie.kogito.persistence.postgresql.reporting.model.JsonType;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresField;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresJsonField;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresMapping;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresMappingDefinition;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresPartitionField;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostgresMappingServiceImplTest {

    @Mock
    private PostgresStorageService storageService;

    @Mock
    private Storage<String, PostgresMappingDefinition> storage;

    private PostgresMappingServiceImpl service;

    @BeforeEach
    public void setup() {
        this.service = new PostgresMappingServiceImpl(storageService);
        when(storageService.getCache(PostgresMappingServiceImpl.CACHE_NAME, PostgresMappingDefinition.class)).thenReturn(storage);
    }

    @Test
    void testGetAllMappingDefinitions() {
        final PostgresMappingDefinition definition = new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(new PostgresField("key")),
                List.of(new PostgresPartitionField("sourceTablePartitionFieldName", "sourceTablePartitionName")),
                "targetTableName",
                List.of(new PostgresMapping("sourceJsonPath",
                        new PostgresJsonField("targetFieldName",
                                JsonType.STRING))));
        when(storage.entries()).thenReturn(Map.of("mappingId", definition));

        final List<PostgresMappingDefinition> definitions = service.getAllMappingDefinitions();
        assertThat(definitions.size()).isEqualTo(1);
        assertThat(definitions.get(0)).isEqualTo(definition);
    }

    @Test
    void testGetMappingDefinitionByIdWhenFound() {
        final PostgresMappingDefinition definition = new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(new PostgresField("key")),
                List.of(new PostgresPartitionField("sourceTablePartitionFieldName", "sourceTablePartitionName")),
                "targetTableName",
                List.of(new PostgresMapping("sourceJsonPath",
                        new PostgresJsonField("targetFieldName",
                                JsonType.STRING))));
        when(storage.containsKey(anyString())).thenReturn(true);
        when(storage.get(anyString())).thenReturn(definition);

        final PostgresMappingDefinition result = service.getMappingDefinitionById("mappingId");
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(definition);
    }

    @Test
    void testGetMappingDefinitionByIdWhenNotFound() {
        when(storage.containsKey(anyString())).thenReturn(false);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> service.getMappingDefinitionById("mappingId"));
    }

    @Test
    void testSaveMappingDefinitionWhenAlreadyExists() {
        final PostgresMappingDefinition definition = new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(new PostgresField("key")),
                List.of(new PostgresPartitionField("sourceTablePartitionFieldName", "sourceTablePartitionName")),
                "targetTableName",
                List.of(new PostgresMapping("sourceJsonPath",
                        new PostgresJsonField("targetFieldName",
                                JsonType.STRING))));

        when(storage.containsKey(anyString())).thenThrow(new IllegalArgumentException());

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> service.saveMappingDefinition(definition));
    }

    @Test
    void testSaveMappingDefinitionWhenNotAlreadyExists() {
        final PostgresMappingDefinition definition = new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(new PostgresField("key")),
                List.of(new PostgresPartitionField("sourceTablePartitionFieldName", "sourceTablePartitionName")),
                "targetTableName",
                List.of(new PostgresMapping("sourceJsonPath",
                        new PostgresJsonField("targetFieldName",
                                JsonType.STRING))));

        when(storage.containsKey(anyString())).thenReturn(false);

        service.saveMappingDefinition(definition);

        verify(storage).put("mappingId", definition);
    }

    @Test
    void testDeleteMappingDefinitionByIdWhenFound() {
        final PostgresMappingDefinition definition = new PostgresMappingDefinition("mappingId",
                "sourceTableName",
                "sourceTableJsonFieldName",
                List.of(new PostgresField("key")),
                List.of(new PostgresPartitionField("sourceTablePartitionFieldName", "sourceTablePartitionName")),
                "targetTableName",
                List.of(new PostgresMapping("sourceJsonPath",
                        new PostgresJsonField("targetFieldName",
                                JsonType.STRING))));

        when(storage.containsKey(anyString())).thenReturn(true);
        when(storage.remove(anyString())).thenReturn(definition);

        final PostgresMappingDefinition deleted = service.deleteMappingDefinitionById("mappingId");
        assertThat(deleted).isEqualTo(definition);

        verify(storage).remove("mappingId");
    }

    @Test
    void testDeleteMappingDefinitionByIdWhenNotFound() {
        when(storage.containsKey(anyString())).thenReturn(false);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> service.deleteMappingDefinitionById("mappingId"));
    }

}
