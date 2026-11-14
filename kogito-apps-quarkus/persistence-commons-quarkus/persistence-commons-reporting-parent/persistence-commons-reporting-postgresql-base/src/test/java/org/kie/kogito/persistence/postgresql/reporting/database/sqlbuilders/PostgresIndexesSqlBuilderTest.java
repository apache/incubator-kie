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

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PostgresIndexesSqlBuilderTest extends BaseSqlBuilderImplTest {

    private final PostgresIndexesSqlBuilder indexesSqlBuilder = new PostgresIndexesSqlBuilder();

    @Override
    protected PostgresIndexesSqlBuilder getIndexesBuilder() {
        return indexesSqlBuilder;
    }

    @Override
    protected String getCreateSql(final PostgresContext context) {
        return getIndexesBuilder().createTableIndexesSql(context);
    }

    @Override
    protected String getDestroySql(final PostgresContext context) {
        return getIndexesBuilder().dropTableIndexesSql(context);
    }

    @Override
    protected void assertCreateSql(final String sql) {
        assertThat(sql).isNotNull();
        assertSequentialContent(sql,
                "CREATE INDEX idx_targetTableName_0",
                "sourceTableJsonFieldName->'root'",
                "CREATE INDEX idx_targetTableName_1",
                "sourceTableJsonFieldName->'root'->'child'",
                "CREATE INDEX idx_targetTableName_2",
                "sourceTableJsonFieldName->'root'->'child'->'collection'",
                "CREATE INDEX idx_targetTableName_3",
                "sourceTableJsonFieldName->'root'->'child'->'collection'->'child'",
                "CREATE INDEX idx_targetTableName_4",
                "sourceTableJsonFieldName->'root'->'child'->'sibling'");
    }

    @Override
    protected void assertDestroySql(final String sql) {
        assertThat(sql).isNotNull();
        assertSequentialContent(sql,
                "DROP INDEX IF EXISTS idx_targetTableName_0",
                "DROP INDEX IF EXISTS idx_targetTableName_1",
                "DROP INDEX IF EXISTS idx_targetTableName_2",
                "DROP INDEX IF EXISTS idx_targetTableName_3",
                "DROP INDEX IF EXISTS idx_targetTableName_4");
    }
}
