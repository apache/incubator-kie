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
package org.kie.kogito.persistence.postgresql.reporting.database.sqlbuilders;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.persistence.postgresql.reporting.database.BasePostgresDatabaseManagerImpl;
import org.kie.kogito.persistence.postgresql.reporting.model.JsonType;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresField;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresJsonField;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresMapping;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresMappingDefinition;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresPartitionField;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
abstract class BaseSqlBuilderImplTest {

    protected static final PostgresMappingDefinition DEFINITION = new PostgresMappingDefinition("mappingId",
            "sourceTableName",
            "sourceTableJsonFieldName",
            List.of(new PostgresField("id"), new PostgresField("key")),
            List.of(new PostgresPartitionField("partition", "chunk"), new PostgresPartitionField("partition2", "chunk2")),
            "targetTableName",
            List.of(new PostgresMapping("root",
                    new PostgresJsonField("field1",
                            JsonType.STRING)),
                    new PostgresMapping("root.child",
                            new PostgresJsonField("field2",
                                    JsonType.STRING)),
                    new PostgresMapping("root.child.collection[].child",
                            new PostgresJsonField("field3",
                                    JsonType.STRING)),
                    new PostgresMapping("root.child.sibling",
                            new PostgresJsonField("field4",
                                    JsonType.STRING))));

    @Mock
    private EntityManager entityManager;

    @Mock
    private PostgresIndexesSqlBuilder mockIndexesSqlBuilder;

    @Mock
    private PostgresTableSqlBuilder mockTableSqlBuilder;

    @Mock
    private PostgresTriggerDeleteSqlBuilder mockTriggerDeleteSqlBuilder;

    @Mock
    private PostgresTriggerInsertSqlBuilder mockTriggerInsertSqlBuilder;

    @Mock
    private PostgresApplyMappingSqlBuilder mockApplyMappingSqlBuilder;

    protected BasePostgresDatabaseManagerImpl manager;

    @BeforeEach
    void setup() {
        this.manager = new BasePostgresDatabaseManagerImpl(getIndexesBuilder(),
                getTableBuilder(),
                getTriggerDeleteBuilder(),
                getTriggerInsertBuilder(),
                getApplyMappingSqlBuilder()) {
            @Override
            protected EntityManager getEntityManager(final String sourceTableName) {
                return entityManager;
            }

            @Override
            protected Map<String, String> getSourceTableFieldTypes(String sourceTableName) {
                return Map.of("id", "text", "partition", "text");
            }
        };
    }

    protected PostgresIndexesSqlBuilder getIndexesBuilder() {
        return mockIndexesSqlBuilder;
    }

    protected PostgresTableSqlBuilder getTableBuilder() {
        return mockTableSqlBuilder;
    }

    protected PostgresTriggerDeleteSqlBuilder getTriggerDeleteBuilder() {
        return mockTriggerDeleteSqlBuilder;
    }

    protected PostgresTriggerInsertSqlBuilder getTriggerInsertBuilder() {
        return mockTriggerInsertSqlBuilder;
    }

    protected PostgresApplyMappingSqlBuilder getApplyMappingSqlBuilder() {
        return mockApplyMappingSqlBuilder;
    }

    protected abstract String getCreateSql(final PostgresContext context);

    protected abstract String getDestroySql(final PostgresContext context);

    protected abstract void assertCreateSql(final String sql);

    protected abstract void assertDestroySql(final String sql);

    @Test
    void testCreate() {
        final PostgresContext context = manager.createContext(DEFINITION);

        final String sql = getCreateSql(context);

        assertCreateSql(sql);
    }

    @Test
    void testDestroy() {
        final PostgresContext context = manager.createContext(DEFINITION);

        final String sql = getDestroySql(context);

        assertDestroySql(sql);
    }

    protected void assertSequentialContent(final String actual,
            final String... expected) {
        if (Objects.isNull(expected)) {
            return;
        }
        if (expected.length == 0) {
            return;
        }
        int idx = 0;
        for (String line : expected) {
            idx = actual.indexOf(line, idx);
            assertThat(idx > -1).as(String.format("Line '%s' not found in '%s'", line, actual)).isTrue();
        }
    }
}
