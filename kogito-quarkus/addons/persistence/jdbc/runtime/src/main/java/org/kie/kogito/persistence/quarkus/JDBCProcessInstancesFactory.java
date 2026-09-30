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
package org.kie.kogito.persistence.quarkus;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kie.kogito.internal.process.runtime.HeadersPersistentConfig;
import org.kie.kogito.persistence.jdbc.AbstractProcessInstancesFactory;
import org.kie.kogito.persistence.jdbc.JDBCProcessInstances;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.Processes;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionSynchronizationRegistry;

@ApplicationScoped
public class JDBCProcessInstancesFactory extends AbstractProcessInstancesFactory {

    @Inject
    Instance<TransactionSynchronizationRegistry> txSyncRegistry;

    @Inject
    public JDBCProcessInstancesFactory(DataSource dataSource,
            @ConfigProperty(name = "kogito.persistence.optimistic.lock", defaultValue = "false") Boolean lock,
            @ConfigProperty(name = "kogito.persistence.headers.enabled", defaultValue = "false") boolean headersEnabled,
            @ConfigProperty(name = "kogito.persistence.headers.excluded") Optional<List<String>> headersExcluded,
            Instance<Processes> processes,
            @ConfigProperty(name = "kogito.persistence.data-isolation.enabled", defaultValue = "false") Boolean dataIsolationEnabled) {
        super(dataSource, lock, HeadersPersistentConfig.of(headersEnabled, headersExcluded), dataIsolationEnabled && processes.isResolvable() ? processes.get() : null);
    }

    public JDBCProcessInstancesFactory() {
    }

    @Override
    public JDBCProcessInstances<?> createProcessInstances(Process<?> process) {
        JDBCProcessInstances<?> instances = super.createProcessInstances(process);
        if (txSyncRegistry != null && txSyncRegistry.isResolvable()) {
            TransactionSynchronizationRegistry registry = txSyncRegistry.get();
            instances.setTransactionRegistrar(unlockAction -> {
                if (registry.getTransactionStatus() != jakarta.transaction.Status.STATUS_NO_TRANSACTION) {
                    registry.registerInterposedSynchronization(new jakarta.transaction.Synchronization() {
                        @Override
                        public void beforeCompletion() {
                        }

                        @Override
                        public void afterCompletion(int status) {
                            unlockAction.run();
                        }
                    });
                } else {
                    unlockAction.run();
                }
            });
        }
        return instances;
    }

}
