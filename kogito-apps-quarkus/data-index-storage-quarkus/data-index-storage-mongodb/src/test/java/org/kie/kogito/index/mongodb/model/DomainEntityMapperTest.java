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

import java.util.HashMap;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.index.mongodb.model.DomainEntityMapper.ID;
import static org.kie.kogito.persistence.mongodb.model.ModelUtils.MAPPER;
import static org.kie.kogito.persistence.mongodb.model.ModelUtils.MONGO_ID;

class DomainEntityMapperTest {

    DomainEntityMapper domainEntityMapper = new DomainEntityMapper();

    @Test
    void testGetEntityClass() {
        assertThat(domainEntityMapper.getEntityClass()).isEqualTo(Document.class);
    }

    @Test
    void testMapToEntity() {
        String testId = "testId";

        Map<String, String> objectMap = new HashMap<>();
        objectMap.put(ID, testId);
        objectMap.put("testKey", "testValue");
        ObjectNode object = MAPPER.valueToTree(objectMap);

        Document document = new Document(MONGO_ID, testId).append("testKey", "testValue");

        Document result = domainEntityMapper.mapToEntity(testId, object);
        assertThat(result).isEqualTo(document);
    }

    @Test
    void testMapToModel() {
        String testId = "testId";

        Map<String, String> objectMap = new HashMap<>();
        objectMap.put(ID, testId);
        objectMap.put("testKey", "testValue");
        ObjectNode object = MAPPER.valueToTree(objectMap);

        Document document = new Document(MONGO_ID, testId).append("testKey", "testValue");

        ObjectNode result = domainEntityMapper.mapToModel(document);
        assertThat(result).isEqualTo(object);
    }
}
