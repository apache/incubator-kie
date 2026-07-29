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
package org.kie.kogito.app.audit.springboot;

import org.kie.kogito.app.audit.api.DataAuditContext;
import org.kie.kogito.app.audit.spi.DataAuditContextFactory;
import org.kie.kogito.process.Processes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;

@Component
public class SpringbootJPADataAuditContextFactory implements DataAuditContextFactory {

    @Autowired
    EntityManager entityManager;

    @Autowired(required = false)
    Processes processes;

    @Value("${kogito.persistence.data-isolation.enabled:false}")
    private boolean dataIsolationEnabled;

    @Override
    public DataAuditContext newDataAuditContext() {
        return DataAuditContext.newDataAuditContext(entityManager, dataIsolationEnabled ? processes : null);
    }

}
