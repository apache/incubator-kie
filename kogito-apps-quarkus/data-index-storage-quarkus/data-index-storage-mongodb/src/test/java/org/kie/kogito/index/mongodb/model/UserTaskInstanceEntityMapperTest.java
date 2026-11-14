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
import org.kie.kogito.index.model.Attachment;
import org.kie.kogito.index.model.Comment;
import org.kie.kogito.index.model.UserTaskInstance;
import org.kie.kogito.persistence.mongodb.model.ModelUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.persistence.mongodb.model.ModelUtils.MAPPER;
import static org.kie.kogito.persistence.mongodb.model.ModelUtils.MONGO_ID;
import static org.kie.kogito.persistence.mongodb.model.ModelUtils.jsonNodeToDocument;
import static org.kie.kogito.persistence.mongodb.model.ModelUtils.zonedDateTimeToInstant;

class UserTaskInstanceEntityMapperTest {

    UserTaskInstanceEntityMapper userTaskInstanceEntityMapper = new UserTaskInstanceEntityMapper();

    static UserTaskInstance userTaskInstance;

    static UserTaskInstanceEntity userTaskInstanceEntity;

    @BeforeAll
    static void setup() {
        String testId = "testId";
        String description = "testDescription";
        String name = "testName";
        String priority = "10";
        String processInstanceId = "testProcessInstanceId";
        String state = "testState";
        String actualOwner = "testActualOwner";
        Set<String> adminGroups = Set.of("testAdminGroups");
        Set<String> adminUsers = Set.of("testAdminUsers");
        ZonedDateTime time = ZonedDateTime.now();
        Set<String> excludedUsers = Set.of("testExcludedUsers");
        Set<String> potentialGroups = Set.of("testPotentialGroups");
        Set<String> potentialUsers = Set.of("testPotentialUsers");
        String referenceName = "testReferenceName";
        String processId = "testProcessId";
        String rootProcessId = "testRootProcessId";
        String rootProcessInstanceId = "testRootProcessInstanceId";
        String externalReferenceId = "testExternalReferenceId";
        ObjectNode object = MAPPER.createObjectNode();
        object.put("test", "testValue");
        ObjectNode inputs = object;
        ObjectNode outputs = object;
        String commentId = "testCommentId";
        String comment_content = "testCommentContent";
        String comment_updatedBy = "testCommentUpdatedBy";
        Comment comment = Comment.builder().id(commentId).updatedAt(time).updatedBy(comment_updatedBy).content(comment_content).build();
        UserTaskInstanceEntity.CommentEntity commentEntity = new UserTaskInstanceEntity.CommentEntity();
        commentEntity.setId(commentId);
        commentEntity.setContent(comment_content);
        commentEntity.setUpdatedAt(zonedDateTimeToInstant(time));
        commentEntity.setUpdatedBy(comment_updatedBy);

        String attachmentId = "testAttachmentId";
        String attachment_name = "testCommentName";
        String attachment_content = "testCommentContent";
        String attachment_updatedBy = "testCommentUpdatedBy";
        Attachment attachment = Attachment.builder().id(attachmentId).updatedAt(time).updatedBy(attachment_updatedBy)
                .content(attachment_content).name(attachment_name).build();
        UserTaskInstanceEntity.AttachmentEntity attachmentEntity = new UserTaskInstanceEntity.AttachmentEntity();
        attachmentEntity.setId(commentId);
        attachmentEntity.setContent(comment_content);
        attachmentEntity.setUpdatedAt(zonedDateTimeToInstant(time));
        attachmentEntity.setUpdatedBy(comment_updatedBy);

        userTaskInstance = new UserTaskInstance();
        userTaskInstance.setId(testId);
        userTaskInstance.setDescription(description);
        userTaskInstance.setName(name);
        userTaskInstance.setPriority(priority);
        userTaskInstance.setProcessInstanceId(processInstanceId);
        userTaskInstance.setState(state);
        userTaskInstance.setActualOwner(actualOwner);
        userTaskInstance.setAdminGroups(adminGroups);
        userTaskInstance.setAdminUsers(adminUsers);
        userTaskInstance.setCompleted(time);
        userTaskInstance.setStarted(time);
        userTaskInstance.setExcludedUsers(excludedUsers);
        userTaskInstance.setPotentialGroups(potentialGroups);
        userTaskInstance.setPotentialUsers(potentialUsers);
        userTaskInstance.setReferenceName(referenceName);
        userTaskInstance.setLastUpdate(time);
        userTaskInstance.setProcessId(processId);
        userTaskInstance.setRootProcessId(rootProcessId);
        userTaskInstance.setRootProcessInstanceId(rootProcessInstanceId);
        userTaskInstance.setInputs(inputs);
        userTaskInstance.setOutputs(outputs);
        userTaskInstance.setComments(List.of(comment));
        userTaskInstance.setAttachments(List.of(attachment));
        userTaskInstance.setExternalReferenceId(externalReferenceId);
        userTaskInstance.setSlaDueDate(time);

        userTaskInstanceEntity = new UserTaskInstanceEntity();
        userTaskInstanceEntity.setId(testId);
        userTaskInstanceEntity.setDescription(description);
        userTaskInstanceEntity.setName(name);
        userTaskInstanceEntity.setPriority(priority);
        userTaskInstanceEntity.setProcessInstanceId(processInstanceId);
        userTaskInstanceEntity.setState(state);
        userTaskInstanceEntity.setActualOwner(actualOwner);
        userTaskInstanceEntity.setAdminGroups(adminGroups);
        userTaskInstanceEntity.setAdminUsers(adminUsers);
        userTaskInstanceEntity.setCompleted(zonedDateTimeToInstant(time));
        userTaskInstanceEntity.setStarted(zonedDateTimeToInstant(time));
        userTaskInstanceEntity.setExcludedUsers(excludedUsers);
        userTaskInstanceEntity.setPotentialGroups(potentialGroups);
        userTaskInstanceEntity.setPotentialUsers(potentialUsers);
        userTaskInstanceEntity.setReferenceName(referenceName);
        userTaskInstanceEntity.setLastUpdate(zonedDateTimeToInstant(time));
        userTaskInstanceEntity.setProcessId(processId);
        userTaskInstanceEntity.setRootProcessId(rootProcessId);
        userTaskInstanceEntity.setRootProcessInstanceId(rootProcessInstanceId);
        userTaskInstanceEntity.setInputs(jsonNodeToDocument(inputs));
        userTaskInstanceEntity.setOutputs(jsonNodeToDocument(outputs));
        userTaskInstanceEntity.setComments(List.of(commentEntity));
        userTaskInstanceEntity.setAttachments(List.of(attachmentEntity));
        userTaskInstanceEntity.setExternalReferenceId(externalReferenceId);
        userTaskInstanceEntity.setSlaDueDate(zonedDateTimeToInstant(time));
    }

    @Test
    void testGetEntityClass() {
        assertThat(userTaskInstanceEntityMapper.getEntityClass()).isEqualTo(UserTaskInstanceEntity.class);
    }

    @Test
    void testMapToEntity() {
        UserTaskInstanceEntity result = userTaskInstanceEntityMapper.mapToEntity(userTaskInstance.getId(), userTaskInstance);
        assertThat(result).isEqualTo(userTaskInstanceEntity);
    }

    @Test
    void testMapToModel() {
        UserTaskInstance result = userTaskInstanceEntityMapper.mapToModel(userTaskInstanceEntity);
        assertThat(result).isEqualTo(userTaskInstance);
    }

    @Test
    void testConvertToMongoAttribute() {
        assertThat(userTaskInstanceEntityMapper.convertToMongoAttribute(ModelUtils.ID)).isEqualTo(MONGO_ID);

        assertThat(userTaskInstanceEntityMapper.convertToMongoAttribute(UserTaskInstanceEntityMapper.COMMENTS_ID_ATTRIBUTE)).isEqualTo(UserTaskInstanceEntityMapper.MONGO_COMMENTS_ID_ATTRIBUTE);

        assertThat(userTaskInstanceEntityMapper.convertToMongoAttribute(UserTaskInstanceEntityMapper.ATTACHMENTS_ID_ATTRIBUTE)).isEqualTo(UserTaskInstanceEntityMapper.MONGO_ATTACHMENTS_ID_ATTRIBUTE);

        String testAttribute = "testAttribute";
        assertThat(userTaskInstanceEntityMapper.convertToMongoAttribute(testAttribute)).isEqualTo(testAttribute);
    }

    @Test
    void testConvertToModelAttribute() {
        assertThat(userTaskInstanceEntityMapper.convertToModelAttribute(MONGO_ID)).isEqualTo(ModelUtils.ID);

        assertThat(userTaskInstanceEntityMapper.convertToModelAttribute(UserTaskInstanceEntityMapper.MONGO_COMMENTS_ID_ATTRIBUTE)).isEqualTo(ModelUtils.ID);

        assertThat(userTaskInstanceEntityMapper.convertToModelAttribute(UserTaskInstanceEntityMapper.MONGO_ATTACHMENTS_ID_ATTRIBUTE)).isEqualTo(ModelUtils.ID);

        String testAttribute = "test.attribute.go";
        assertThat(userTaskInstanceEntityMapper.convertToModelAttribute(testAttribute)).isEqualTo("go");
    }
}
