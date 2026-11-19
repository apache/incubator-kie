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
package org.kie.kogito.persistence.postgresql.reporting;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.persistence.api.Storage;
import org.kie.kogito.persistence.postgresql.PostgresStorageService;
import org.kie.kogito.persistence.postgresql.model.CacheEntityRepository;
import org.kie.kogito.testcontainers.quarkus.PostgreSqlQuarkusTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityResult;
import jakarta.persistence.FieldResult;
import jakarta.persistence.Id;
import jakarta.persistence.SqlResultSetMapping;
import jakarta.transaction.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(PostgreSqlQuarkusTestResource.class)
class ComplexHierarchicalTypeMappingIT {

    private static final String CACHE_NAME = "ComplexHierarchicalType";

    private static final String SQL = "SELECT " +
            "ROW_NUMBER() OVER (ORDER BY cache_name, var_name) as id, " +
            "cache_name, " +
            "var_name, " +
            "root, " +
            "nestedBasicMappedField, " +
            "nestedComplexCollectionMappedField1, " +
            "nestedComplexCollectionMappedSubField1 " +
            "FROM " +
            "ComplexHierarchicalTypeExtract";

    @Inject
    PostgresStorageService storageService;

    @Inject
    CacheEntityRepository repository;

    @BeforeEach
    @Transactional
    public void setup() {
        storageService.getCache(CACHE_NAME).clear();
    }

    @Test
    @Transactional
    void testComplexHierarchicalTypeMapping() {
        final ComplexHierarchicalType cht1 = new ComplexHierarchicalType("root1",
                new BasicType(1, 2L, "A"),
                List.of(new ComplexHierarchicalType("Aa",
                        new BasicType(null, null, "field3a"),
                        List.of(new ComplexHierarchicalType("Ba",
                                new BasicType(null, null, "field3a-1"),
                                null),
                                new ComplexHierarchicalType("Bb",
                                        new BasicType(null, null, "field3a-2"),
                                        null))),
                        new ComplexHierarchicalType("Ab",
                                new BasicType(null, null, "field3b"),
                                List.of(new ComplexHierarchicalType("Ca", null, null)))));

        final Storage<String, ComplexHierarchicalType> cache = storageService.getCache(CACHE_NAME, ComplexHierarchicalType.class);
        cache.put("key1", cht1);

        @SuppressWarnings("unchecked")
        final List<ComplexHierarchicalTypeExtractRow> results = repository
                .getEntityManager()
                .createNativeQuery(SQL, "ComplexHierarchicalTypeMapping")
                .getResultList();

        assertThat(results).hasSize(3);
        final ComplexHierarchicalTypeExtractRow row0 = results.get(0);
        assertThat(row0.key).isEqualTo("key1");
        assertThat(row0.name).isEqualTo(CACHE_NAME);
        assertThat(row0.root).isEqualTo("root1");
        assertThat(row0.nestedBasicMappedField).isEqualTo("A");
        assertThat(row0.nestedComplexCollectionMappedField1).isEqualTo("Aa");
        assertThat(row0.nestedComplexCollectionMappedSubField1).isEqualTo("Ba");

        final ComplexHierarchicalTypeExtractRow row1 = results.get(1);
        assertThat(row1.key).isEqualTo("key1");
        assertThat(row1.name).isEqualTo(CACHE_NAME);
        assertThat(row1.root).isEqualTo("root1");
        assertThat(row1.nestedBasicMappedField).isEqualTo("A");
        assertThat(row1.nestedComplexCollectionMappedField1).isEqualTo("Aa");
        assertThat(row1.nestedComplexCollectionMappedSubField1).isEqualTo("Bb");

        final ComplexHierarchicalTypeExtractRow row2 = results.get(2);
        assertThat(row2.key).isEqualTo("key1");
        assertThat(row2.name).isEqualTo(CACHE_NAME);
        assertThat(row2.root).isEqualTo("root1");
        assertThat(row2.nestedBasicMappedField).isEqualTo("A");
        assertThat(row2.nestedComplexCollectionMappedField1).isEqualTo("Ab");
        assertThat(row2.nestedComplexCollectionMappedSubField1).isEqualTo("Ca");
    }

    @Test
    @Transactional
    void testComplexHierarchicalTypeMappingDelete() {
        final ComplexHierarchicalType cht1 = new ComplexHierarchicalType("root1",
                new BasicType(1, 2L, "A"),
                List.of(new ComplexHierarchicalType("Aa",
                        new BasicType(null, null, "field3a"),
                        List.of(new ComplexHierarchicalType("Aaa", null, null)))));
        final ComplexHierarchicalType cht2 = new ComplexHierarchicalType("root2",
                new BasicType(3, 4L, "B"),
                List.of(new ComplexHierarchicalType("Ba",
                        new BasicType(null, null, "field3b"),
                        List.of(new ComplexHierarchicalType("Baa", null, null)))));

        final Storage<String, ComplexHierarchicalType> cache = storageService.getCache(CACHE_NAME, ComplexHierarchicalType.class);
        cache.put("key1", cht1);
        cache.put("key2", cht2);

        assertResultSize(2);

        cache.remove("key1");

        assertResultSize(1);

        cache.remove("key2");

        assertResultSize(0);
    }

    @Test
    @Transactional
    void testComplexHierarchicalTypeMappingUpdate() {
        final Storage<String, ComplexHierarchicalType> cache = storageService.getCache(CACHE_NAME, ComplexHierarchicalType.class);
        cache.put("key1", new ComplexHierarchicalType("root1",
                new BasicType(1, 2L, "A"),
                List.of(new ComplexHierarchicalType("root1a",
                        new BasicType(1, 2L, "field3a"),
                        null),
                        new ComplexHierarchicalType("root1b",
                                new BasicType(3, 4L, "field3b"),
                                null))));
        assertResultSize(2);

        //Clear JPA cache to ensure we fetch queries from the database
        repository.getEntityManager().clear();

        cache.put("key1", new ComplexHierarchicalType("root1-1",
                new BasicType(1, 2L, "A-1"),
                null));

        assertResultSize(1);

        @SuppressWarnings("unchecked")
        final List<ComplexHierarchicalTypeExtractRow> results = repository
                .getEntityManager()
                .createNativeQuery(SQL, "ComplexHierarchicalTypeMapping")
                .getResultList();

        assertThat(results).hasSize(1);
        final ComplexHierarchicalTypeExtractRow row0 = results.get(0);
        assertThat(row0.key).isEqualTo("key1");
        assertThat(row0.name).isEqualTo(CACHE_NAME);
        assertThat(row0.root).isEqualTo("root1-1");
        assertThat(row0.nestedBasicMappedField).isEqualTo("A-1");
        assertThat(row0.nestedComplexCollectionMappedSubField1).isNull();
    }

    @SuppressWarnings("unchecked")
    private void assertResultSize(final int expected) {
        final List<BasicTypeMappingIT.BasicTypeExtractRow> results = repository
                .getEntityManager()
                .createNativeQuery(SQL, "ComplexHierarchicalTypeMapping")
                .getResultList();
        assertThat(results).hasSize(expected);
    }

    /**
     * Class to demonstrate "typed" access to the mapped result set
     */
    @Entity
    @SqlResultSetMapping(
            name = "ComplexHierarchicalTypeMapping",
            entities = {
                    @EntityResult(
                            entityClass = ComplexHierarchicalTypeExtractRow.class,
                            fields = { @FieldResult(name = "id", column = "id"),
                                    @FieldResult(name = "key", column = "var_name"),
                                    @FieldResult(name = "name", column = "cache_name"),
                                    @FieldResult(name = "root", column = "root"),
                                    @FieldResult(name = "nestedBasicMappedField", column = "nestedBasicMappedField"),
                                    @FieldResult(name = "nestedComplexCollectionMappedField1", column = "nestedComplexCollectionMappedField1"),
                                    @FieldResult(name = "nestedComplexCollectionMappedSubField1", column = "nestedComplexCollectionMappedSubField1")
                            })
            })
    public static class ComplexHierarchicalTypeExtractRow {

        @Id
        @Column(nullable = false)
        @SuppressWarnings("unused")
        private int id;

        @Column(nullable = false)
        private String key;

        @Column
        private String name;

        @Column(nullable = false)
        private String root;

        @Column
        private String nestedBasicMappedField;

        @Column
        private String nestedComplexCollectionMappedField1;

        @Column
        private String nestedComplexCollectionMappedSubField1;
    }
}
