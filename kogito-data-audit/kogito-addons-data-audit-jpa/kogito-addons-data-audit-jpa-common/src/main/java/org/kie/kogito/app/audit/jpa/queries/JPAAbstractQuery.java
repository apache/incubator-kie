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
package org.kie.kogito.app.audit.jpa.queries;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.kie.kogito.process.Processes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

public abstract class JPAAbstractQuery<R> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JPAAbstractQuery.class);

    record ProcessKey(String processId, String processVersion) {
    }

    protected <T> List<T> executeWithNamedQueryEntityManager(EntityManager entityManager, String queryName, Class<T> clazz) {
        return executeWithNamedQueryEntityManager("META-INF/data-audit-orm.xml", entityManager, queryName);
    }

    @SuppressWarnings("unchecked")
    protected <T> List<T> executeWithNamedQueryEntityManager(String file, EntityManager entityManager, String queryName) {
        String query = MappingFile.findInFile(file, entityManager, queryName);
        return entityManager.createNativeQuery(query).getResultList();
    }

    protected <T> List<T> executeWithNamedQueryEntityManager(EntityManager entityManager, String query) {
        return executeWithNamedQueryEntityManagerAndArguments(entityManager, query, Collections.emptyMap());
    }

    protected <T> List<T> executeWithNamedQueryEntityManagerAndArguments(EntityManager entityManager, String query, Map<String, Object> arguments) {
        return executeWithNamedQueryEntityManagerAndArguments("META-INF/data-audit-orm.xml", entityManager, query, arguments);
    }

    protected <T> List<T> executeWithNamedQueryEntityManagerAndArguments(String file, EntityManager entityManager, String queryName, Map<String, Object> arguments) {
        String query = MappingFile.findInFile(file, entityManager, queryName);
        return executeWithQueryEntityManagerAndArguments(entityManager, query, arguments);
    }

    protected <T> List<T> executeWithQueryEntityManagerAndArguments(EntityManager entityManager, String query) {
        return executeWithQueryEntityManagerAndArguments(entityManager, query, Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    protected <T> List<T> executeWithQueryEntityManagerAndArguments(EntityManager entityManager, String query, Map<String, Object> arguments) {
        LOGGER.debug("About to execute native query {} with arguments {}", query, arguments);
        Query jpaQuery = entityManager.createNativeQuery(query);

        Map<String, Object> parameters = new HashMap<>(arguments);
        Map<String, Object> pagination = (Map<String, Object>) parameters.remove("pagination");
        parameters.forEach(jpaQuery::setParameter);
        if (pagination != null) {
            if (pagination.get("limit") != null) {
                jpaQuery.setMaxResults((Integer) pagination.get("limit"));
            } else {
                jpaQuery.setMaxResults(10);
            }
            if (pagination.get("offset") != null) {
                jpaQuery.setFirstResult((Integer) pagination.get("offset"));
            } else {
                jpaQuery.setFirstResult(0);
            }
        } else {
            jpaQuery.setFirstResult(0);
            jpaQuery.setMaxResults(10);
        }

        return jpaQuery.getResultList();
    }

    @SuppressWarnings("unchecked")
    protected <T> List<T> executeIsolated(
            EntityManager entityManager,
            String queryName,
            Map<String, Object> arguments,
            Optional<Processes> processesOpt,
            String processIdColumn,
            String processVersionColumn,
            String rootProcessIdColumn,
            String rootProcessVersionColumn) {

        String baseQuery = MappingFile.findInFile("META-INF/data-audit-orm.xml", entityManager, queryName);
        return executeIsolatedQuery(entityManager, baseQuery, arguments, processesOpt,
                processIdColumn, processVersionColumn, rootProcessIdColumn, rootProcessVersionColumn);
    }

    @SuppressWarnings("unchecked")
    protected <T> List<T> executeIsolatedQuery(
            EntityManager entityManager,
            String baseQuery,
            Map<String, Object> arguments,
            Optional<Processes> processesOpt,
            String processIdColumn,
            String processVersionColumn,
            String rootProcessIdColumn,
            String rootProcessVersionColumn) {

        Map<String, Object> params = new HashMap<>(arguments);
        Map<String, Object> pagination = (Map<String, Object>) params.remove("pagination");

        List<ProcessKey> allowedKeys = resolveAllowedKeys(processesOpt);
        String finalQuery = applyIsolationFilter(baseQuery, allowedKeys,
                processIdColumn, processVersionColumn, rootProcessIdColumn, rootProcessVersionColumn);

        LOGGER.debug("About to execute isolated native query {} with arguments {}", finalQuery, params);
        Query jpaQuery = entityManager.createNativeQuery(finalQuery);

        params.forEach(jpaQuery::setParameter);

        bindIsolationParameters(jpaQuery, allowedKeys);

        if (pagination != null) {
            jpaQuery.setMaxResults(pagination.get("limit") != null ? (Integer) pagination.get("limit") : 10);
            jpaQuery.setFirstResult(0);
        } else {
            jpaQuery.setFirstResult(0);
            jpaQuery.setMaxResults(10);
        }

        return jpaQuery.getResultList();
    }

    private List<ProcessKey> resolveAllowedKeys(Optional<Processes> processesOpt) {
        if (processesOpt.isEmpty()) {
            return Collections.emptyList();
        }
        return processesOpt.get().processes().stream()
                .map(p -> new ProcessKey(p.id(), p.version()))
                .collect(Collectors.toList());
    }

    private String applyIsolationFilter(
            String baseQuery,
            List<ProcessKey> allowedKeys,
            String processIdColumn,
            String processVersionColumn,
            String rootProcessIdColumn,
            String rootProcessVersionColumn) {

        if (allowedKeys.isEmpty()) {
            return baseQuery;
        }

        String valueRows = IntStream.range(0, allowedKeys.size())
                .mapToObj(i -> "(:processId" + i + ", :processVersion" + i + ")")
                .collect(Collectors.joining(", "));

        String cte = "WITH _allowed_processes (processId, processVersion) AS (VALUES " + valueRows + ") ";

        String isolationPredicate;
        if (rootProcessIdColumn != null) {
            String preFilter = "("
                    + processIdColumn + " IN (SELECT ap.processId FROM _allowed_processes ap)"
                    + " OR " + rootProcessIdColumn + " IN (SELECT ap.processId FROM _allowed_processes ap)"
                    + ")";
            String versionCheck = "("
                    + processVersionColumn + " IS NULL"
                    + " OR EXISTS (SELECT 1 FROM _allowed_processes ap WHERE ap.processId = " + rootProcessIdColumn
                    + " AND ap.processVersion = " + rootProcessVersionColumn + ")"
                    + " OR (" + rootProcessIdColumn + " IS NULL"
                    + " AND EXISTS (SELECT 1 FROM _allowed_processes ap WHERE ap.processId = " + processIdColumn
                    + " AND ap.processVersion = " + processVersionColumn + "))"
                    + ")";
            isolationPredicate = preFilter + " AND " + versionCheck;
        } else {
            isolationPredicate = processIdColumn + " IN (SELECT ap.processId FROM _allowed_processes ap)"
                    + " AND ("
                    + processVersionColumn + " IS NULL"
                    + " OR EXISTS (SELECT 1 FROM _allowed_processes ap WHERE ap.processId = " + processIdColumn
                    + " AND ap.processVersion = " + processVersionColumn + ")"
                    + ")";
        }

        String upperQuery = baseQuery.toUpperCase();
        int orderByIdx = upperQuery.lastIndexOf("ORDER BY");
        if (orderByIdx >= 0) {
            return cte + baseQuery.substring(0, orderByIdx).stripTrailing()
                    + " AND " + isolationPredicate + " "
                    + baseQuery.substring(orderByIdx);
        }
        return cte + baseQuery.stripTrailing() + " AND " + isolationPredicate;
    }

    private void bindIsolationParameters(Query jpaQuery, List<ProcessKey> allowedKeys) {
        for (int i = 0; i < allowedKeys.size(); i++) {
            jpaQuery.setParameter("processId" + i, allowedKeys.get(i).processId());
            jpaQuery.setParameter("processVersion" + i, allowedKeys.get(i).processVersion());
        }
    }
}
