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
package org.kie.kogito.index.mongodb.model;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.kogito.index.model.CancelType;
import org.kie.kogito.index.model.Milestone;
import org.kie.kogito.index.model.NodeInstance;
import org.kie.kogito.index.model.ProcessInstance;
import org.kie.kogito.index.model.ProcessInstanceError;
import org.kie.kogito.persistence.mongodb.model.ModelUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.persistence.mongodb.model.ModelUtils.MAPPER;
import static org.kie.kogito.persistence.mongodb.model.ModelUtils.MONGO_ID;
import static org.kie.kogito.persistence.mongodb.model.ModelUtils.jsonNodeToDocument;
import static org.kie.kogito.persistence.mongodb.model.ModelUtils.zonedDateTimeToInstant;

class ProcessInstanceEntityMapperTest {

    ProcessInstanceEntityMapper processInstanceEntityMapper = new ProcessInstanceEntityMapper();

    static ProcessInstance processInstance;

    static ProcessInstanceEntity processInstanceEntity;

    @BeforeAll
    static void setup() {
        String nodeInstanceId = "testNodeInstanceId";
        String nodeInstanceName = "testNodeInstanceName";
        String nodeInstanceNodeId = "testNodeInstanceNodeId";
        String nodeInstanceType = "testNodeInstanceType";
        String nodeInstanceDefinitionId = "testNodeInstanceDefinitionId";
        CancelType cancelType = CancelType.ABORTED;

        String processInstanceErrorMessage = "testProcessInstanceErrorMessage";
        String processInstanceErrorNodeDefinitionId = "testProcessInstanceErrorNodeDefinitionId";

        String milestoneId = "testMilestone";
        String milestoneName = "testMilestoneName";
        String milestoneStatus = "testMilestoneStatus";

        String testId = "testId";
        String processId = "testProcessId";
        Set<String> roles = Set.of("testRoles");
        ObjectNode variables = MAPPER.createObjectNode();
        variables.put("test", "testValue");
        String endpoint = "testEndpoint";
        Integer state = 2;
        ZonedDateTime time = ZonedDateTime.now();
        String rootProcessId = "testRootProcessId";
        String rootProcessInstanceId = "testRootProcessInstanceId";
        String parentProcessInstanceId = "testParentProcessInstanceId";
        String processName = "testProcessName";
        Set<String> addons = Set.of("testAddons");
        String businessKey = "testBusinessKey";
        String createdBy = "testCreatedBy";
        String updatedBy = "testUpdatedBy";

        NodeInstance nodeInstance = new NodeInstance();
        nodeInstance.setId(nodeInstanceId);
        nodeInstance.setDefinitionId(nodeInstanceDefinitionId);
        nodeInstance.setExit(time);
        nodeInstance.setEnter(time);
        nodeInstance.setType(nodeInstanceType);
        nodeInstance.setNodeId(nodeInstanceNodeId);
        nodeInstance.setName(nodeInstanceName);
        nodeInstance.setCancelType(cancelType);

        ProcessInstanceError processInstanceError = new ProcessInstanceError();
        processInstanceError.setMessage(processInstanceErrorMessage);
        processInstanceError.setNodeDefinitionId(processInstanceErrorNodeDefinitionId);

        Milestone milestone = new Milestone();
        milestone.setId(milestoneId);
        milestone.setName(milestoneName);
        milestone.setStatus(milestoneStatus);

        processInstance = new ProcessInstance();
        processInstance.setId(testId);
        processInstance.setProcessId(processId);
        processInstance.setRoles(roles);
        processInstance.setVariables(variables);
        processInstance.setEndpoint(endpoint);
        processInstance.setNodes(List.of(nodeInstance));
        processInstance.setState(state);
        processInstance.setStart(time);
        processInstance.setEnd(time);
        processInstance.setRootProcessId(rootProcessId);
        processInstance.setRootProcessInstanceId(rootProcessInstanceId);
        processInstance.setParentProcessInstanceId(parentProcessInstanceId);
        processInstance.setProcessName(processName);
        processInstance.setError(processInstanceError);
        processInstance.setAddons(addons);
        processInstance.setLastUpdate(time);
        processInstance.setBusinessKey(businessKey);
        processInstance.setMilestones(List.of(milestone));
        processInstance.setCreatedBy(createdBy);
        processInstance.setUpdatedBy(updatedBy);

        ProcessInstanceEntity.NodeInstanceEntity nodeInstanceEntity = new ProcessInstanceEntity.NodeInstanceEntity();
        nodeInstanceEntity.setId(nodeInstanceId);
        nodeInstanceEntity.setDefinitionId(nodeInstanceDefinitionId);
        nodeInstanceEntity.setEnter(zonedDateTimeToInstant(time));
        nodeInstanceEntity.setExit(zonedDateTimeToInstant(time));
        nodeInstanceEntity.setName(nodeInstanceName);
        nodeInstanceEntity.setNodeId(nodeInstanceNodeId);
        nodeInstanceEntity.setType(nodeInstanceType);
        nodeInstanceEntity.setCancelType(cancelType);

        ProcessInstanceEntity.ProcessInstanceErrorEntity processInstanceErrorEntity = new ProcessInstanceEntity.ProcessInstanceErrorEntity();
        processInstanceErrorEntity.setMessage(processInstanceErrorMessage);
        processInstanceErrorEntity.setNodeDefinitionId(processInstanceErrorNodeDefinitionId);

        ProcessInstanceEntity.MilestoneEntity milestoneEntity = new ProcessInstanceEntity.MilestoneEntity();
        milestoneEntity.setId(milestoneId);
        milestoneEntity.setName(milestoneName);
        milestoneEntity.setStatus(milestoneStatus);

        processInstanceEntity = new ProcessInstanceEntity();
        processInstanceEntity.setId(testId);
        processInstanceEntity.setProcessId(processId);
        processInstanceEntity.setRoles(roles);
        processInstanceEntity.setVariables(jsonNodeToDocument(variables));
        processInstanceEntity.setEndpoint(endpoint);
        processInstanceEntity.setNodes(List.of(nodeInstanceEntity));
        processInstanceEntity.setState(state);
        processInstanceEntity.setStart(zonedDateTimeToInstant(time));
        processInstanceEntity.setEnd(zonedDateTimeToInstant(time));
        processInstanceEntity.setRootProcessId(rootProcessId);
        processInstanceEntity.setRootProcessInstanceId(rootProcessInstanceId);
        processInstanceEntity.setParentProcessInstanceId(parentProcessInstanceId);
        processInstanceEntity.setProcessName(processName);
        processInstanceEntity.setError(processInstanceErrorEntity);
        processInstanceEntity.setAddons(addons);
        processInstanceEntity.setLastUpdate(zonedDateTimeToInstant(time));
        processInstanceEntity.setBusinessKey(businessKey);
        processInstanceEntity.setMilestones(List.of(milestoneEntity));
        processInstanceEntity.setCreatedBy(createdBy);
        processInstanceEntity.setUpdatedBy(updatedBy);
    }

    @Test
    void testGetEntityClass() {
        assertThat(processInstanceEntityMapper.getEntityClass()).isEqualTo(ProcessInstanceEntity.class);
    }

    @Test
    void testMapToEntity() {
        ProcessInstanceEntity result = processInstanceEntityMapper.mapToEntity(processInstance.getId(), processInstance);
        assertThat(result).isEqualTo(processInstanceEntity);
    }

    @Test
    void testMapToModel() {
        ProcessInstance result = processInstanceEntityMapper.mapToModel(processInstanceEntity);
        assertThat(result).isEqualTo(processInstance);
    }

    @Test
    void testConvertToMongoAttribute() {
        assertThat(processInstanceEntityMapper.convertToMongoAttribute(ModelUtils.ID)).isEqualTo(MONGO_ID);

        assertThat(processInstanceEntityMapper.convertToMongoAttribute(ProcessInstanceEntityMapper.NODES_ID_ATTRIBUTE)).isEqualTo(ProcessInstanceEntityMapper.MONGO_NODES_ID_ATTRIBUTE);

        assertThat(processInstanceEntityMapper.convertToMongoAttribute(ProcessInstanceEntityMapper.MILESTONES_ID_ATTRIBUTE)).isEqualTo(ProcessInstanceEntityMapper.MONGO_MILESTONES_ID_ATTRIBUTE);

        String testAttribute = "testAttribute";
        assertThat(processInstanceEntityMapper.convertToMongoAttribute(testAttribute)).isEqualTo(testAttribute);
    }

    @Test
    void testConvertToModelAttribute() {
        assertThat(processInstanceEntityMapper.convertToModelAttribute(MONGO_ID)).isEqualTo(ModelUtils.ID);

        assertThat(processInstanceEntityMapper.convertToModelAttribute(ProcessInstanceEntityMapper.MONGO_NODES_ID_ATTRIBUTE)).isEqualTo(ModelUtils.ID);

        assertThat(processInstanceEntityMapper.convertToModelAttribute(ProcessInstanceEntityMapper.MONGO_MILESTONES_ID_ATTRIBUTE)).isEqualTo(ModelUtils.ID);

        String testAttribute = "test.attribute.go";
        assertThat(processInstanceEntityMapper.convertToModelAttribute(testAttribute)).isEqualTo("go");
    }
}
