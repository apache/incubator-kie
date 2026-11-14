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
package org.kie.kogito.index.jpa.model;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Abstract integration test to validate all JPA entities have registered data isolation key descriptors.
 *
 * This test uses the JPA metamodel to automatically discover all entity classes
 * and verify they are registered in DataIsolationKeyDescriptorRegistry with both processId and
 * processVersion paths. This ensures that when a new entity is added, the build
 * will fail if it's not registered.
 *
 * Concrete implementations in Quarkus/Spring Boot modules provide the EntityManager.
 */
public abstract class AbstractDataIsolationKeyDescriptorRegistryIT {

    EntityManager em;

    public AbstractDataIsolationKeyDescriptorRegistryIT(EntityManager em) {
        this.em = em;
    }

    /**
     * Validates that all JPA entities extending AbstractEntity have registered data isolation key descriptors.
     *
     * This test will fail at build time if:
     * - A new entity class is added but not registered in DataIsolationKeyDescriptorRegistry
     * - An entity is registered with an incorrect class reference
     * - An entity descriptor is missing processId or processVersion paths
     */
    @Test
    void allJpaEntitiesHaveDataIsolationKeyDescriptor() {

        // Get all JPA entities that extend AbstractEntity from the metamodel
        Set<Class<?>> jpaEntities = em.getMetamodel()
                .getEntities()
                .stream()
                .map(EntityType::getJavaType)
                .filter(AbstractEntity.class::isAssignableFrom)
                .collect(Collectors.toSet());

        // Verify each entity has a registered data isolation key descriptor with both paths
        for (Class<?> entityClass : jpaEntities) {
            assertThatCode(() -> {
                DataIsolationKeyDescriptor descriptor =
                        DataIsolationKeyDescriptorRegistry.getDescriptor((Class<? extends AbstractEntity>) entityClass);
                if (descriptor.processId() == null || descriptor.processId().isBlank()) {
                    throw new AssertionError("processId path is null or blank for entity: " +
                            entityClass.getSimpleName());
                }
            }).as(
                    "Missing or invalid data isolation key descriptor for entity: %s %s",
                    entityClass.getSimpleName(),
                    ". Add mapping to DataIsolationKeyDescriptorRegistry.DESCRIPTORS with both processId and processVersion paths")
                    .doesNotThrowAnyException();
        }
    }
}
