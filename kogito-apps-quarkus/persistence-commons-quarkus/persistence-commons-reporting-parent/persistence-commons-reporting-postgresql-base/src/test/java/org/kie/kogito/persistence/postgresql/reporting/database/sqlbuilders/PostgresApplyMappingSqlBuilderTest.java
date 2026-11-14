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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PostgresApplyMappingSqlBuilderTest extends BaseSqlBuilderImplTest {

    private final PostgresApplyMappingSqlBuilder applyMappingSqlBuilder = new PostgresApplyMappingSqlBuilder();

    @Override
    protected PostgresApplyMappingSqlBuilder getApplyMappingSqlBuilder() {
        return applyMappingSqlBuilder;
    }

    @Override
    protected String getCreateSql(final PostgresContext context) {
        return getApplyMappingSqlBuilder().apply(context);
    }

    @Override
    protected void assertCreateSql(final String sql) {
        assertThat(sql).isNotNull();
        assertSequentialContent(sql,
                "UPDATE sourceTableName ",
                "SET id = id, ",
                "key = key ",
                "WHERE ",
                "partition = 'chunk' AND ",
                "partition2 = 'chunk2");
    }

    @Override
    @Disabled("There is no semantic equivalent for this SqlBuilder.")
    void testDestroy() {
    }

    @Override
    protected String getDestroySql(final PostgresContext context) {
        return null;
    }

    @Override
    protected void assertDestroySql(final String sql) {
    }

}
