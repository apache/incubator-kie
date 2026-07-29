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
package org.kie.kogito.app.audit.quarkus;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kie.kogito.app.audit.api.DataAuditContext;
import org.kie.kogito.app.audit.spi.DataAuditContextFactory;
import org.kie.kogito.process.Processes;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class QuarkusJPADataAuditContextFactory implements DataAuditContextFactory {

    @PersistenceContext
    EntityManager entityManager;

    @Inject
    Instance<Processes> processesInstance;

    @ConfigProperty(name = "kogito.persistence.data-isolation.enabled", defaultValue = "false")
    boolean dataIsolationEnabled;

    @Override
    public DataAuditContext newDataAuditContext() {
        Processes processes = (dataIsolationEnabled && processesInstance.isResolvable())
                ? processesInstance.get()
                : null;
        return DataAuditContext.newDataAuditContext(entityManager, processes);
    }

}
