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

import org.junit.jupiter.api.BeforeAll;
import org.kie.kogito.event.EventPublisher;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;

import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(value = H2DatabaseTestResource.class, restrictToAnnotatedClass = true)
@TestProfile(DataAuditITTestProfile.class)
public class H2DataAuditUserTaskIsolationIT extends AbstractDataAuditUserTaskIsolationIT {

    @Inject
    EventPublisher publisher;

    @BeforeAll
    @Override
    protected void publishTestData() throws Exception {
        doPublishUserTaskEvents(publisher);
    }

    @Override
    protected RequestSpecification request() {
        return given();
    }
}
