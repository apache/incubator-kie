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

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.jbpm.flow.serialization.MarshallerContextName;
import org.jbpm.flow.serialization.ProcessInstanceMarshallerService;
import org.kie.kogito.Model;
import org.kie.kogito.internal.process.runtime.HeadersPersistentConfig;
import org.kie.kogito.process.MutableProcessInstances;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstanceOptimisticLockingException;
import org.kie.kogito.process.ProcessInstanceReadMode;
import org.kie.kogito.process.Processes;
import org.kie.kogito.process.impl.AbstractProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JDBCProcessInstances<T extends Model> implements MutableProcessInstances<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCProcessInstances.class);

    private final Process<?> process;
    private final ProcessInstanceMarshallerService marshaller;
    private final boolean lock;
    private final Repository repository;

    public JDBCProcessInstances(Process<?> process, DataSource dataSource, boolean lock) {
        this(process, dataSource, lock, null, null);
    }

    public JDBCProcessInstances(Process<?> process, DataSource dataSource, boolean lock, HeadersPersistentConfig headersConfig) {
        this(process, dataSource, lock, headersConfig, null);
    }

    public JDBCProcessInstances(Process<?> process, DataSource dataSource, boolean lock, HeadersPersistentConfig headersConfig, Processes processes) {
        this.process = process;
        this.lock = lock;
        this.marshaller = ProcessInstanceMarshallerService.newBuilder().withDefaultObjectMarshallerStrategies().withDefaultListeners()
                .withContextEntry(MarshallerContextName.MARSHALLER_HEADERS_CONFIG, headersConfig).build();
        this.repository = new GenericRepository(dataSource, processes);
    }

    @Override
    public boolean exists(String id) {
        return findById(id).isPresent();
    }

    private String[] getUniqueEvents(ProcessInstance<T> instance) {
        return Stream.of(((AbstractProcessInstance<T>) instance).internalGetProcessInstance().getEventTypes()).collect(Collectors.toCollection(HashSet::new)).toArray(String[]::new);
    }

    @Override
    public void create(String id, ProcessInstance<T> instance) {
        LOGGER.debug("Creating process instance id: {}, processId: {}, processVersion: {}", id, process.id(), process.version());
        if (isActive(instance) || instance.status() == ProcessInstance.STATE_PENDING) {
            String[] eventTypes = getUniqueEvents(instance);
            String rootProcessId = ((AbstractProcessInstance<T>) instance).internalGetProcessInstance().getRootProcessId();
            String rootProcessVersion = ((AbstractProcessInstance<T>) instance).internalGetProcessInstance().getRootProcessVersion();
            String rootProcessInstanceId = ((AbstractProcessInstance<T>) instance).internalGetProcessInstance().getRootProcessInstanceId();
            repository.insertInternal(process.id(), process.version(), rootProcessId, rootProcessVersion, rootProcessInstanceId,
                    UUID.fromString(id), marshaller.marshallProcessInstance(instance), instance.businessKey(), eventTypes);
            connectInstance(instance);
        } else {
            LOGGER.warn("Skipping create of process instance id: {}, state: {}", id, instance.status());
        }
    }

    @Override
    public void update(String id, ProcessInstance<T> instance) {
        LOGGER.debug("Updating process instance id: {}, processId: {}, processVersion: {}", id, process.id(), process.version());
        if (isActive(instance) || instance.status() == ProcessInstance.STATE_PENDING) {
            String[] eventTypes = getUniqueEvents(instance);
            if (lock) {
                boolean isUpdated = repository.updateWithLock(process.id(), process.version(), UUID.fromString(id), marshaller.marshallProcessInstance(instance), instance.version(), eventTypes);
                if (!isUpdated) {
                    throw new ProcessInstanceOptimisticLockingException(id);
                }
                ((AbstractProcessInstance<T>) instance).setVersion(instance.version() + 1);
            } else {
                repository.updateInternal(process.id(), process.version(), UUID.fromString(id), marshaller.marshallProcessInstance(instance), eventTypes);
            }
            connectInstance(instance);
        } else {
            LOGGER.warn("Process instance id: {}, state: {} is not active, skipping update", id, instance.status());
        }
    }

    @Override
    public long migrateAll(String targetProcessId, String targetProcessVersion) {
        return repository.migrate(process.id(), process.version(), targetProcessId, targetProcessVersion);
    }

    @Override
    public void migrateProcessInstances(String targetProcessId, String targetProcessVersion, String... processIds) {
        repository.migrate(process.id(), process.version(), targetProcessId, targetProcessVersion, processIds);
    }

    @Override
    public void remove(String id) {
        LOGGER.debug("Removing process instance id: {}, processId: {}", id, process.id());
        boolean isDeleted = repository.deleteInternal(process.id(), process.version(), UUID.fromString(id));
        LOGGER.debug("Deleted: {}", isDeleted);
    }

    @Override
    public Optional<ProcessInstance<T>> findById(String id, ProcessInstanceReadMode mode) {
        LOGGER.debug("Find process instance id: {}, mode: {}", id, mode);
        return repository.findByIdInternal(process.id(), process.version(), UUID.fromString(id)).map(r -> {
            return (AbstractProcessInstance<T>) unmarshall(r, mode);
        });
    }

    @Override
    public Stream<ProcessInstance<T>> waitingForEventType(String eventType, ProcessInstanceReadMode mode) {
        LOGGER.debug("Find process instance waiting for {} using mode: {}", eventType, mode);
        return repository.findAllInternalWaitingFor(process.id(), process.version(), eventType).map(r -> unmarshall(r, mode));
    }

    @Override
    public Optional<ProcessInstance<T>> findByBusinessKey(String businessKey, ProcessInstanceReadMode mode) {
        LOGGER.debug("Find process instance using business Key : {}", businessKey);
        return repository.findByBusinessKey(process.id(), process.version(), businessKey).map(r -> {
            return (AbstractProcessInstance<T>) unmarshall(r, mode);
        });
    }

    @Override
    public Stream<ProcessInstance<T>> stream(ProcessInstanceReadMode mode) {
        LOGGER.debug("Find process instance values using mode: {}", mode);
        return repository.findAllInternal(process.id(), process.version()).map(r -> unmarshall(r, mode));
    }

    private ProcessInstance<T> unmarshall(Repository.Record record, ProcessInstanceReadMode mode) {
        AbstractProcessInstance<T> instance = (AbstractProcessInstance<T>) marshaller.unmarshallProcessInstance(record.payload(), process, mode);
        instance.setVersion(record.version());
        connectInstance(instance);
        return instance;
    }

    @Override
    public boolean lock() {
        return this.lock;
    }

    private void connectInstance(ProcessInstance<?> instance) {
        ((AbstractProcessInstance<?>) instance).internalSetReloadSupplier(pi -> {
            Repository.Record r = repository.findByIdInternal(process.id(), process.version(), UUID.fromString(pi.id())).orElseThrow();
            pi.setVersion(r.version());
            marshaller.createdReloadFunction(r::payload).accept(pi);
            pi.internalGetProcessInstance().setRootProcessId(r.rootProcessId());
            pi.internalGetProcessInstance().setRootProcessVersion(r.rootProcessVersion());
        });
    }
}
