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
package org.kie.kogito.index.mongodb.storage;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.index.model.Job;
import org.kie.kogito.index.model.ProcessDefinition;
import org.kie.kogito.index.model.ProcessInstance;
import org.kie.kogito.index.model.UserTaskInstance;
import org.kie.kogito.index.mongodb.mock.MockIndexCreateOrUpdateEventListener;
import org.kie.kogito.index.mongodb.model.DomainEntityMapper;
import org.kie.kogito.index.mongodb.model.JobEntity;
import org.kie.kogito.index.mongodb.model.JobEntityMapper;
import org.kie.kogito.index.mongodb.model.ProcessDefinitionEntity;
import org.kie.kogito.index.mongodb.model.ProcessDefinitionEntityMapper;
import org.kie.kogito.index.mongodb.model.ProcessIdEntity;
import org.kie.kogito.index.mongodb.model.ProcessIdEntityMapper;
import org.kie.kogito.index.mongodb.model.ProcessInstanceEntity;
import org.kie.kogito.index.mongodb.model.ProcessInstanceEntityMapper;
import org.kie.kogito.index.mongodb.model.UserTaskInstanceEntity;
import org.kie.kogito.index.mongodb.model.UserTaskInstanceEntityMapper;
import org.kie.kogito.testcontainers.quarkus.MongoDBQuarkusTestResource;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.index.storage.Constants.JOBS_STORAGE;
import static org.kie.kogito.index.storage.Constants.PROCESS_DEFINITIONS_STORAGE;
import static org.kie.kogito.index.storage.Constants.PROCESS_ID_MODEL_STORAGE;
import static org.kie.kogito.index.storage.Constants.PROCESS_INSTANCES_STORAGE;
import static org.kie.kogito.index.storage.Constants.USER_TASK_INSTANCES_STORAGE;

@QuarkusTest
@QuarkusTestResource(MongoDBQuarkusTestResource.class)
class MongoModelServiceImplIT {

    @Inject
    MockIndexCreateOrUpdateEventListener mockIndexCreateOrUpdateEventListener;

    @Inject
    MongoModelServiceImpl mongoModelServiceImpl;

    @BeforeEach
    void setup() {
        // Make sure MongoModelServiceImpl is initialized
        assertThat(mongoModelServiceImpl.getEntityMapper(PROCESS_INSTANCES_STORAGE)).isNotNull();
        mockIndexCreateOrUpdateEventListener.reset();
    }

    @AfterEach
    void tearDown() {
        mockIndexCreateOrUpdateEventListener.reset();
    }

    @Test
    void testInit() {
        mongoModelServiceImpl.init();

        mockIndexCreateOrUpdateEventListener.assertFire(PROCESS_DEFINITIONS_STORAGE, ProcessDefinition.class.getName());
        mockIndexCreateOrUpdateEventListener.assertFire(PROCESS_INSTANCES_STORAGE, ProcessInstance.class.getName());
        mockIndexCreateOrUpdateEventListener.assertFire(USER_TASK_INSTANCES_STORAGE, UserTaskInstance.class.getName());
        mockIndexCreateOrUpdateEventListener.assertFire(JOBS_STORAGE, Job.class.getName());
    }

    @Test
    void testGetEntityMapper() {
        assertThat((mongoModelServiceImpl.<Job, JobEntity> getEntityMapper(JOBS_STORAGE) instanceof JobEntityMapper)).isTrue();
        assertThat((mongoModelServiceImpl.<ProcessDefinition, ProcessDefinitionEntity> getEntityMapper(PROCESS_DEFINITIONS_STORAGE) instanceof ProcessDefinitionEntityMapper)).isTrue();
        assertThat((mongoModelServiceImpl.<ProcessInstance, ProcessInstanceEntity> getEntityMapper(PROCESS_INSTANCES_STORAGE) instanceof ProcessInstanceEntityMapper)).isTrue();
        assertThat((mongoModelServiceImpl.<UserTaskInstance, UserTaskInstanceEntity> getEntityMapper(USER_TASK_INSTANCES_STORAGE) instanceof UserTaskInstanceEntityMapper)).isTrue();
        assertThat((mongoModelServiceImpl.<String, ProcessIdEntity> getEntityMapper(PROCESS_ID_MODEL_STORAGE) instanceof ProcessIdEntityMapper)).isTrue();
        assertThat((mongoModelServiceImpl.<ObjectNode, Document> getEntityMapper("test_domain") instanceof DomainEntityMapper)).isTrue();
    }
}
