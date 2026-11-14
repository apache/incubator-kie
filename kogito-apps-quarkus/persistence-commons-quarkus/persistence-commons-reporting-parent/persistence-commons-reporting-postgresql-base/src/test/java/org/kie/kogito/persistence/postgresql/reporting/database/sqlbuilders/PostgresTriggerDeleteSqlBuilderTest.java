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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PostgresTriggerDeleteSqlBuilderTest extends BaseSqlBuilderImplTest {

    private final PostgresTriggerDeleteSqlBuilder triggerDeleteSqlBuilder = new PostgresTriggerDeleteSqlBuilder();

    @Override
    protected PostgresTriggerDeleteSqlBuilder getTriggerDeleteBuilder() {
        return triggerDeleteSqlBuilder;
    }

    @Override
    protected String getCreateSql(final PostgresContext context) {
        return getTriggerDeleteBuilder().createDeleteTriggerSql(context);
    }

    @Override
    protected String getDestroySql(final PostgresContext context) {
        return getTriggerDeleteBuilder().dropDeleteTriggerSql(context);
    }

    @Override
    protected void assertCreateSql(final String sql) {
        assertThat(sql).isNotNull();
        assertSequentialContent(sql,
                "CREATE TRIGGER trgDelete_mappingId_DELETES AFTER DELETE ON sourceTableName",
                "FOR EACH ROW",
                "EXECUTE PROCEDURE spDelete_mappingId_DELETES()",
                "CREATE TRIGGER trgDelete_mappingId_UPDATES BEFORE UPDATE ON sourceTableName",
                "FOR EACH ROW",
                "EXECUTE PROCEDURE spDelete_mappingId_UPDATES()");
    }

    @Override
    protected void assertDestroySql(final String sql) {
        assertThat(sql).isNotNull();
        assertSequentialContent(sql,
                "DROP TRIGGER IF EXISTS trgDelete_mappingId_DELETES ON sourceTableName",
                "DROP TRIGGER IF EXISTS trgDelete_mappingId_UPDATES ON sourceTableName");
    }

    @Test
    void testCreateDeleteTriggerFunctionSql() {
        final PostgresContext context = manager.createContext(DEFINITION);

        final String sql = getTriggerDeleteBuilder().createDeleteTriggerFunctionSql(context);

        assertThat(sql).isNotNull();
        assertSequentialContent(sql,
                "CREATE FUNCTION spDelete_mappingId_DELETES() RETURNS trigger AS",
                "DELETE FROM targetTableName",
                "WHERE",
                "id = OLD.id",
                "RETURN OLD;",
                "CREATE FUNCTION spDelete_mappingId_UPDATES() RETURNS trigger AS",
                "DELETE FROM targetTableName",
                "WHERE",
                "id = NEW.id",
                "RETURN NEW;");
    }

    @Test
    void testDropDeleteTriggerFunctionSql() {
        final PostgresContext context = manager.createContext(DEFINITION);

        final String sql = getTriggerDeleteBuilder().dropDeleteTriggerFunctionSql(context);

        assertThat(sql).isNotNull();
        assertSequentialContent(sql,
                "DROP FUNCTION IF EXISTS spDelete_mappingId_DELETES",
                "DROP FUNCTION IF EXISTS spDelete_mappingId_UPDATES");
    }
}
