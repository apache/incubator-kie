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

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.kie.kogito.Model;
import org.kie.kogito.app.audit.quarkus.AbstractDataAuditDataIsolationIT;
import org.kie.kogito.event.EventPublisher;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.Processes;
import org.kie.kogito.testcontainers.springboot.PostgreSqlSpringBootTestResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "server_port=0", "kogito.persistence.data-isolation.enabled=true" })
@ContextConfiguration(initializers = PostgreSqlSpringBootTestResource.class)
@ActiveProfiles("postgresql")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class PostgreSQLDataAuditDataIsolationIT extends AbstractDataAuditDataIsolationIT {

    @LocalServerPort
    private Integer port;

    @Autowired
    EventPublisher publisher;

    @MockitoBean
    Processes processes;

    private final AtomicBoolean dataPublished = new AtomicBoolean(false);

    @BeforeEach
    public void setupMock() throws Exception {
        Process<? extends Model> allowedProcess = mock(Process.class);
        when(allowedProcess.id()).thenReturn(ALLOWED_PROCESS_ID);
        when(allowedProcess.version()).thenReturn(ALLOWED_PROCESS_VERSION);
        when(processes.processes()).thenReturn(Collections.singletonList(allowedProcess));
        when(processes.processIds()).thenReturn(Set.of(ALLOWED_PROCESS_ID));

        if (dataPublished.compareAndSet(false, true)) {
            publishTestData();
        }
    }

    @Override
    protected void publishTestData() throws Exception {
        doPublishProcessInstanceEvents(publisher);
    }

    @Override
    protected RequestSpecification request() {
        return given().port(port);
    }
}
