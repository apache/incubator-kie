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
package org.kie.kogito.index.postgresql.reporting;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.kie.kogito.event.process.ProcessInstanceVariableDataEvent;
import org.kie.kogito.index.jpa.storage.ProcessInstanceEntityStorage;
import org.kie.kogito.index.test.TestUtils;
import org.kie.kogito.testcontainers.quarkus.PostgreSqlQuarkusTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityResult;
import jakarta.persistence.FieldResult;
import jakarta.persistence.Id;
import jakarta.persistence.SqlResultSetMapping;
import jakarta.transaction.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(PostgreSqlQuarkusTestResource.class)
class ProcessInstanceVariableMappingIT {

    private static final String SQL = "SELECT " +
            "id, " +
            "firstName," +
            "lastName " +
            "FROM " +
            "ProcessInstanceVariableExtract";

    @Inject
    EntityManager em;

    @Inject
    ProcessInstanceEntityStorage storage;

    @Test
    @Transactional
    void testProcessInstanceVariableMapping() {

        ProcessInstanceVariableDataEvent event1 = TestUtils.createProcessInstanceVariableEvent("pi0",
                "process0",
                "Michael",
                "Anstis");
        ProcessInstanceVariableDataEvent event2 = TestUtils.createProcessInstanceVariableEvent("pi1",
                "process0",
                "Keith",
                "Flint");
        ProcessInstanceVariableDataEvent event3 = TestUtils.createProcessInstanceVariableEvent("pi2",
                "process1",
                "Javier",
                "Ito");

        storage.indexVariable(event1);
        storage.indexVariable(event2);
        storage.indexVariable(event3);

        @SuppressWarnings("unchecked")
        List<ProcessInstanceVariableExtract> results = em
                .createNativeQuery(SQL, "ProcessInstanceVariableMappingMapping")
                .getResultList();

        assertThat(results).hasSize(2);
        final ProcessInstanceVariableExtract row0 = results.get(0);
        assertThat(row0.id).isEqualTo("pi0");
        assertThat(row0.firstName).isEqualTo("Michael");
        assertThat(row0.lastName).isEqualTo("Anstis");

        final ProcessInstanceVariableExtract row1 = results.get(1);
        assertThat(row1.id).isEqualTo("pi1");
        assertThat(row1.firstName).isEqualTo("Keith");
        assertThat(row1.lastName).isEqualTo("Flint");
    }

    @Entity
    @SqlResultSetMapping(
            name = "ProcessInstanceVariableMappingMapping",
            entities = {
                    @EntityResult(
                            entityClass = ProcessInstanceVariableExtract.class,
                            fields = { @FieldResult(name = "id", column = "id"),
                                    @FieldResult(name = "firstName", column = "firstName"),
                                    @FieldResult(name = "lastName", column = "lastName") })
            })
    public static class ProcessInstanceVariableExtract {

        @Id
        @Column(nullable = false)
        private String id;

        @Column
        private String firstName;

        @Column
        private String lastName;
    }
}
