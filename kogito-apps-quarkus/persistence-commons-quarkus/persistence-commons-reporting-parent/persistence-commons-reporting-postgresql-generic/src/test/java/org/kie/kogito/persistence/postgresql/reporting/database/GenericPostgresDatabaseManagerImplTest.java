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
package org.kie.kogito.persistence.postgresql.reporting.database;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.persistence.postgresql.model.CacheEntityRepository;
import org.kie.kogito.persistence.postgresql.reporting.database.sqlbuilders.PostgresApplyMappingSqlBuilder;
import org.kie.kogito.persistence.postgresql.reporting.database.sqlbuilders.PostgresIndexesSqlBuilder;
import org.kie.kogito.persistence.postgresql.reporting.database.sqlbuilders.PostgresTableSqlBuilder;
import org.kie.kogito.persistence.postgresql.reporting.database.sqlbuilders.PostgresTriggerDeleteSqlBuilder;
import org.kie.kogito.persistence.postgresql.reporting.database.sqlbuilders.PostgresTriggerInsertSqlBuilder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenericPostgresDatabaseManagerImplTest {

    @Mock
    private CacheEntityRepository repository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private PostgresIndexesSqlBuilder indexesSqlBuilder;

    @Mock
    private PostgresTableSqlBuilder tableSqlBuilder;

    @Mock
    private PostgresTriggerDeleteSqlBuilder triggerDeleteSqlBuilder;

    @Mock
    private PostgresTriggerInsertSqlBuilder triggerInsertSqlBuilder;

    @Mock
    private PostgresApplyMappingSqlBuilder applyMappingSqlBuilder;

    private GenericPostgresDatabaseManagerImpl manager;

    @BeforeEach
    public void setup() {
        this.manager = new GenericPostgresDatabaseManagerImpl(repository,
                indexesSqlBuilder,
                tableSqlBuilder,
                triggerDeleteSqlBuilder,
                triggerInsertSqlBuilder,
                applyMappingSqlBuilder);
    }

    @Test
    void testGetEntityManager() {
        when(repository.getEntityManager()).thenReturn(entityManager);

        assertThat(manager.getEntityManager("does-not-matter")).isEqualTo(entityManager);
    }

}
