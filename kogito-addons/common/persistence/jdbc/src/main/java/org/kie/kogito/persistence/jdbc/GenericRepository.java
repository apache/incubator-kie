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
package org.kie.kogito.persistence.jdbc;

import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.sql.DataSource;

import org.kie.kogito.Model;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.Processes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Arrays.stream;

public class GenericRepository extends Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericRepository.class);
    private static final String ID = "id";
    private static final String PROCESS_ID = "process_id";
    private static final String PROCESS_VERSION = "process_version";
    private static final String ROOT_PROCESS_ID = "root_process_id";
    private static final String ROOT_PROCESS_VERSION = "root_process_version";
    private static final String PAYLOAD = "payload";
    private static final String VERSION = "version";

    private final DataSource dataSource;
    private final Processes processes;

    public GenericRepository(DataSource dataSource) {
        this(dataSource, null);
    }

    public GenericRepository(DataSource dataSource, Processes processes) {
        this.dataSource = dataSource;
        this.processes = processes;
    }

    protected boolean isFilterByLocalProcess() {
        Collection<Process<? extends Model>> processIds = getProcessIdsForFiltering();
        return processIds != null && !processIds.isEmpty();
    }

    /**
     * Gets the collection of process IDs for data isolation filtering.
     * If Processes bean is available, returns all process IDs from it.
     * Otherwise, returns null to skip data isolation filtering.
     *
     * @return Collection of process IDs for CTE filtering, or null to skip filtering
     */
    private Collection<Process<? extends Model>> getProcessIdsForFiltering() {
        if (processes != null) {
            Collection<Process<? extends Model>> processIds = processes.processes();
            LOGGER.debug("Using process IDs from Processes bean for data isolation: {}", processIds);
            return processIds;
        }
        LOGGER.debug("No Processes bean available, skipping data isolation filtering");
        return null;
    }

    /**
     * Wraps a base SQL query with a CTE for data isolation filtering.
     * The CTE creates an allowed_processes table with multiple process IDs.
     *
     * @param baseQuery The base SQL query to wrap
     * @param processIds Collection of process IDs to include in the CTE
     * @return The wrapped query with CTE and data isolation filtering
     */
    private String buildQueryWithProcessFiltering(String baseQuery, Collection<Process<? extends Model>> processIds, String fromTable) {
        if (!isFilterByLocalProcess()) {
            return baseQuery;
        }

        String anchorBlock = "WITH anchor_row AS (SELECT MIN(id) as target_id FROM " + fromTable + ")";
        String unionSelects = java.util.stream.IntStream.range(0, processIds.size())
                .mapToObj(i -> {
                    if (i == 0) {
                        return "SELECT CAST(? AS VARCHAR(255)), CAST(? AS VARCHAR(255)) FROM " + fromTable + " WHERE id = (SELECT target_id FROM anchor_row)";
                    } else {
                        return "SELECT ?, ? FROM " + fromTable + " WHERE id = (SELECT target_id FROM anchor_row)";
                    }
                })
                .collect(Collectors.joining(" UNION ALL "));

        String cte = anchorBlock + ", allowed_processes (process_id, process_version) AS (" + unionSelects + ") ";

        // Determine if we need WHERE or AND
        String whereClause = baseQuery.toLowerCase().contains(" where ") ? " AND " : " WHERE ";

        // Add data isolation filter using EXISTS pattern
        String isolationFilter = whereClause +
                "EXISTS (SELECT 1 FROM allowed_processes ap WHERE " +
                "process_instances.process_version IS NULL " +
                "OR (ap.process_id = process_instances.root_process_id AND (ap.process_version IS NULL AND process_instances.root_process_version IS NULL OR ap.process_version = process_instances.root_process_version)) "
                +
                "OR (process_instances.root_process_id IS NULL AND ap.process_id = process_instances.process_id AND (ap.process_version IS NULL AND process_instances.process_version IS NULL OR ap.process_version = process_instances.process_version)))";

        return cte + baseQuery + isolationFilter;
    }

    /**
     * Binds process IDs to the prepared statement for CTE filtering.
     *
     * @param statement The prepared statement
     * @param processIds Collection of process IDs
     * @param startIndex Starting parameter index (1-based)
     * @return The next available parameter index
     */
    private int bindProcessIds(PreparedStatement statement, Collection<Process<? extends Model>> processIds, int startIndex) throws SQLException {
        if (!isFilterByLocalProcess()) {
            return startIndex;
        }

        int index = startIndex;
        for (Process<? extends Model> process : processIds) {
            statement.setString(index++, process.id());
            if (process.version() == null) {
                statement.setNull(index++, Types.VARCHAR);
            } else {
                statement.setString(index++, process.version());
            }
        }
        return index;
    }

    @Override
    void insertInternal(String processId, String processVersion, String rootProcessId, String rootProcessVersion,
            String rootProcessInstanceId, UUID id, byte[] payload, String businessKey, String[] eventTypes) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT);
                PreparedStatement eventStatement = connection.prepareStatement(DELETE_ALL_WAITING_FOR_EVENT_TYPE);
                PreparedStatement insertEventStatement = connection.prepareStatement(INSERT_WAITING_FOR_EVENT_TYPE)) {

            eventStatement.setString(1, id.toString());
            eventStatement.executeUpdate();

            for (String eventType : eventTypes) {
                insertEventStatement.setString(1, id.toString());
                insertEventStatement.setString(2, eventType);
                insertEventStatement.executeUpdate();
            }

            String processInstanceId = id.toString();
            statement.setString(1, processInstanceId);
            statement.setBytes(2, payload);
            statement.setString(3, processId);
            statement.setString(4, processVersion);
            statement.setString(5, rootProcessId);
            statement.setString(6, rootProcessVersion);
            statement.setString(7, rootProcessInstanceId);
            statement.setLong(8, 0L);
            statement.executeUpdate();
            if (businessKey != null) {
                try (PreparedStatement businessKeyStmt = connection.prepareStatement(INSERT_BUSINESS_KEY)) {
                    businessKeyStmt.setString(1, businessKey);
                    businessKeyStmt.setString(2, processInstanceId);
                    businessKeyStmt.executeUpdate();
                }
            }
        } catch (Exception e) {
            throw uncheckedException(e, "Error inserting process instance id: %s, processId: %s processVersion: %s business key: %s", id, processId, processVersion, businessKey);
        }
    }

    @Override
    void updateInternal(String processId, String processVersion, UUID id, byte[] payload, String[] eventTypes) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sqlIncludingVersion(UPDATE, processVersion));
                PreparedStatement eventStatement = connection.prepareStatement(DELETE_ALL_WAITING_FOR_EVENT_TYPE);
                PreparedStatement insertEventStatement = connection.prepareStatement(INSERT_WAITING_FOR_EVENT_TYPE)) {

            eventStatement.setString(1, id.toString());
            eventStatement.executeUpdate();

            for (String eventType : eventTypes) {
                insertEventStatement.setString(1, id.toString());
                insertEventStatement.setString(2, eventType);
                insertEventStatement.executeUpdate();
            }

            statement.setBytes(1, payload);
            statement.setString(2, processId);
            statement.setString(3, id.toString());
            if (processVersion != null) {
                statement.setString(4, processVersion);
            }
            statement.executeUpdate();
        } catch (Exception e) {
            throw uncheckedException(e, "Error updating process instance %s", id);
        }
    }

    @Override
    boolean updateWithLock(String processId, String processVersion, UUID id, byte[] payload, long version, String[] eventTypes) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sqlIncludingVersion(UPDATE_WITH_LOCK, processVersion));
                PreparedStatement eventStatement = connection.prepareStatement(DELETE_ALL_WAITING_FOR_EVENT_TYPE);
                PreparedStatement insertEventStatement = connection.prepareStatement(INSERT_WAITING_FOR_EVENT_TYPE)) {

            eventStatement.setString(1, id.toString());
            eventStatement.executeUpdate();

            for (String eventType : eventTypes) {
                insertEventStatement.setString(1, id.toString());
                insertEventStatement.setString(2, eventType);
                insertEventStatement.executeUpdate();
            }

            statement.setBytes(1, payload);
            statement.setLong(2, version + 1);
            statement.setString(3, processId);
            statement.setString(4, id.toString());
            statement.setLong(5, version);
            if (processVersion != null) {
                statement.setString(6, processVersion);
            }
            int count = statement.executeUpdate();
            return count == 1;
        } catch (Exception e) {
            throw uncheckedException(e, "Error updating with lock process instance %s", id);
        }
    }

    @Override
    boolean deleteInternal(String processId, String processVersion, UUID id) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sqlIncludingVersion(DELETE, processVersion));
                PreparedStatement eventStatement = connection.prepareStatement(DELETE_ALL_WAITING_FOR_EVENT_TYPE)) {

            eventStatement.setString(1, id.toString());
            eventStatement.executeUpdate();

            statement.setString(1, processId);
            statement.setString(2, id.toString());
            if (processVersion != null) {
                statement.setString(3, processVersion);
            }
            int count = statement.executeUpdate();
            return count == 1;
        } catch (Exception e) {
            throw uncheckedException(e, "Error deleting process instance %s", id);
        }
    }

    private Record from(ResultSet rs) throws SQLException {
        return new Record(rs.getString(ID), rs.getString(PROCESS_ID), rs.getString(PROCESS_VERSION),
                rs.getString(ROOT_PROCESS_ID), rs.getString(ROOT_PROCESS_VERSION), rs.getLong(VERSION), rs.getBytes(PAYLOAD));
    }

    @Override
    Optional<Record> findByIdInternal(String processId, String processVersion, UUID id) {
        Collection<Process<? extends Model>> processIds = getProcessIdsForFiltering();
        String baseQuery = sqlIncludingVersion(FIND_BY_ID, processVersion);
        String query = buildQueryWithProcessFiltering(baseQuery, processIds, "process_instances");

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            int paramIndex = bindProcessIds(statement, processIds, 1);
            statement.setString(paramIndex++, processId);
            statement.setString(paramIndex++, id.toString());
            if (processVersion != null) {
                statement.setString(paramIndex, processVersion);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(from(resultSet));
                }
            }
        } catch (Exception e) {
            throw uncheckedException(e, "Error finding process instance %s", id);
        }
        return Optional.empty();
    }

    @Override
    Stream<Record> findAllInternalWaitingFor(String processId, String processVersion, String eventType) {
        Collection<Process<? extends Model>> processIds = getProcessIdsForFiltering();
        String baseQuery = sqlIncludingVersion(FIND_ALL_WAITING_FOR_EVENT_TYPE, processVersion);
        String query = buildQueryWithProcessFiltering(baseQuery, processIds, "process_instances");

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            int paramIndex = bindProcessIds(statement, processIds, 1);
            statement.setString(paramIndex++, processId);
            statement.setString(paramIndex++, eventType);
            if (processVersion != null) {
                statement.setString(paramIndex, processVersion);
            }

            List<Record> data = new ArrayList<>();
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                data.add(from(resultSet));
            }
            resultSet.close();
            return data.stream();
        } catch (SQLException e) {
            throw uncheckedException(e, "Error finding all process instances, for processId %s waiting for %s", processId, eventType);
        }
    }

    @Override
    Optional<Record> findByBusinessKey(String processId, String processVersion, String businessKey) {
        Collection<Process<? extends Model>> processIds = getProcessIdsForFiltering();
        String baseQuery = sqlIncludingVersion(FIND_BY_BUSINESS_KEY, processVersion);
        String query = buildQueryWithProcessFiltering(baseQuery, processIds, "process_instances");

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            int paramIndex = bindProcessIds(statement, processIds, 1);
            statement.setString(paramIndex++, businessKey);
            statement.setString(paramIndex++, processId);
            if (processVersion != null) {
                statement.setString(paramIndex, processVersion);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(from(resultSet)) : Optional.empty();
            }
        } catch (Exception e) {
            throw uncheckedException(e, "Error finding process instance. Business key: %s, Process Id: %s, Process version: %s", businessKey, processId, processVersion);
        }
    }

    private static class CloseableWrapper implements Runnable {

        private Deque<AutoCloseable> wrapped = new ArrayDeque<>();

        public <T extends AutoCloseable> T nest(T c) {
            wrapped.addFirst(c);
            return c;
        }

        @Override
        public void run() {
            try {
                close();
            } catch (Exception ex) {
                throw new RuntimeException("Error closing resources", ex);
            }
        }

        public void close() throws Exception {
            Exception exception = null;
            for (AutoCloseable wrap : wrapped) {
                try {
                    wrap.close();
                } catch (Exception ex) {
                    if (exception != null) {
                        ex.addSuppressed(exception);
                    }
                    exception = ex;
                }
            }
            if (exception != null) {
                throw exception;
            }
        }
    }

    @Override
    Stream<Record> findAllInternal(String processId, String processVersion) {
        Collection<Process<? extends Model>> processIds = getProcessIdsForFiltering();
        String baseQuery = sqlIncludingVersion(FIND_ALL, processVersion);
        String query = buildQueryWithProcessFiltering(baseQuery, processIds, "process_instances");

        CloseableWrapper close = new CloseableWrapper();
        try {
            Connection connection = close.nest(dataSource.getConnection());
            PreparedStatement statement = close.nest(connection.prepareStatement(query));
            int paramIndex = bindProcessIds(statement, processIds, 1);
            statement.setString(paramIndex++, processId);
            if (processVersion != null) {
                statement.setString(paramIndex, processVersion);
            }
            ResultSet resultSet = close.nest(statement.executeQuery());
            return StreamSupport.stream(new Spliterators.AbstractSpliterator<Record>(Long.MAX_VALUE, Spliterator.ORDERED) {
                @Override
                public boolean tryAdvance(Consumer<? super Record> action) {
                    try {
                        boolean hasNext = resultSet.next();
                        if (hasNext) {
                            action.accept(from(resultSet));
                        } else {
                            try {
                                close.close();
                            } catch (Exception e) {
                                throw uncheckedException(e, "Error finding all process instances, for processId %s", processId);
                            }
                        }
                        return hasNext;
                    } catch (SQLException e) {
                        throw uncheckedException(e, "Error finding all process instances, for processId %s", processId);
                    }
                }
            }, false).onClose(close);
        } catch (SQLException e) {
            try {
                close.close();
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            throw uncheckedException(e, "Error finding all process instances, for processId %s", processId);
        }
    }

    private static String sqlIncludingVersion(String statement, String processVersion) {
        return statement + " " + (processVersion == null ? PROCESS_VERSION_IS_NULL : PROCESS_VERSION_EQUALS_TO);
    }

    private static String sqlIncludingRootVersion(String statement, String rootProcessVersion) {
        return statement + (rootProcessVersion == null ? ROOT_PROCESS_VERSION_IS_NULL : ROOT_PROCESS_VERSION_EQUALS_TO);
    }

    private void migrateRootCascade(Connection connection,
            String processId, String processVersion,
            String targetProcessId, String targetProcessVersion,
            String[] processInstanceIds) throws SQLException {
        final String cascadeSql;
        if (processInstanceIds == null) {
            cascadeSql = sqlIncludingRootVersion(MIGRATE_ROOT_BULK, processVersion);
        } else {
            String placeholders = stream(processInstanceIds).map(x -> "?").collect(Collectors.joining(", "));
            cascadeSql = MIGRATE_ROOT_INSTANCES_SQL_TEMPLATE.formatted(placeholders);
        }

        try (PreparedStatement cascadeStmt = connection.prepareStatement(cascadeSql)) {
            cascadeStmt.setString(1, targetProcessId);
            cascadeStmt.setString(2, targetProcessVersion);
            if (processInstanceIds == null) {
                cascadeStmt.setString(3, processId);
                if (processVersion != null) {
                    cascadeStmt.setString(4, processVersion);
                }
            } else {
                int i = 3;
                for (String id : processInstanceIds) {
                    cascadeStmt.setString(i++, id);
                }
            }
            cascadeStmt.executeUpdate();
        }
    }

    @Override
    long migrate(String processId, String processVersion, String targetProcessId, String targetProcessVersion) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sqlIncludingVersion(Repository.MIGRATE_BULK, processVersion))) {
            statement.setString(1, targetProcessId);
            statement.setString(2, targetProcessVersion);
            statement.setString(3, processId);
            if (processVersion != null) {
                statement.setString(4, processVersion);
            }
            long count = statement.executeUpdate();
            migrateRootCascade(connection, processId, processVersion, targetProcessId, targetProcessVersion, null);
            return count;
        } catch (Exception e) {
            throw uncheckedException(e, "Error updating process instance %s-%s", processId, processVersion);
        }
    }

    @Override
    void migrate(String processId, String processVersion, String targetProcessId, String targetProcessVersion, String[] processInstanceIds) {
        String sqlParamsPlaceHolders = stream(processInstanceIds).map(processInstanceId -> "?")
                .collect(Collectors.joining(", "));

        String migrateProcessInstancesSQLStatement = MIGRATE_INSTANCES_SQL_TEMPLATE.formatted(sqlParamsPlaceHolders);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sqlIncludingVersion(migrateProcessInstancesSQLStatement, processVersion))) {

            statement.setString(1, targetProcessId);
            statement.setString(2, targetProcessVersion);
            statement.setString(3, processId);

            int i = 4;
            for (String processInstanceId : processInstanceIds) {
                statement.setString(i++, processInstanceId);
            }

            if (processVersion != null) {
                statement.setString(i, processVersion);
            }
            statement.executeUpdate();
            migrateRootCascade(connection, processId, processVersion, targetProcessId, targetProcessVersion, processInstanceIds);
        } catch (Exception e) {
            throw uncheckedException(e, "Error updating process instance %s-%s", processId, processVersion);
        }
    }

}
