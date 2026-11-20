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
package org.kie.kogito.persistence.mongodb.model;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.kie.kogito.persistence.mongodb.mock.MockMongoEntityMapper;

import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.persistence.mongodb.model.ModelUtils.MAPPER;
import static org.kie.kogito.persistence.mongodb.model.ModelUtils.MONGO_ID;

class ModelUtilsTest {

    @Test
    void testInstantToZonedDateTime() {
        ZonedDateTime time = ZonedDateTime.now();
        assertThat(ModelUtils.instantToZonedDateTime(time.toInstant().toEpochMilli()).toInstant().toEpochMilli()).isEqualTo(time.toInstant().toEpochMilli());
    }

    @Test
    void testZonedDateTimeToInstant() {
        ZonedDateTime time = ZonedDateTime.now();
        assertThat(ModelUtils.zonedDateTimeToInstant(time)).isEqualTo(time.toInstant().toEpochMilli());
    }

    @Test
    void testDocumentToJsonNode() {
        Map<String, String> objectMap = new HashMap<>();
        objectMap.put("testKey1", "testValue1");
        objectMap.put("testKey2", "testValue2");
        ObjectNode object = MAPPER.valueToTree(objectMap);

        Document document = new Document().append("testKey1", "testValue1")
                .append("testKey2", "testValue2");

        assertThat(ModelUtils.documentToJsonNode(document)).isEqualTo(object);
    }

    @Test
    void testJsonNodeToDocument() {
        Map<String, String> objectMap = new HashMap<>();
        objectMap.put("testKey1", "testValue1");
        objectMap.put("testKey2", "testValue2");
        ObjectNode object = MAPPER.valueToTree(objectMap);

        Document document = new Document().append("testKey1", "testValue1")
                .append("testKey2", "testValue2");

        assertThat(ModelUtils.jsonNodeToDocument(object)).isEqualTo(document);
    }

    @Test
    void testDocumentToObject() {
        Map<String, String> objectMap = new HashMap<>();
        objectMap.put(ModelUtils.ID, "testId");
        objectMap.put("testKey1", "testValue1");
        objectMap.put("testKey2", "testValue2");

        Document document = new Document()
                .append(MONGO_ID, "testId")
                .append("testKey1", "testValue1")
                .append("testKey2", "testValue2");

        assertThat(ModelUtils.documentToObject(document, HashMap.class, new MockMongoEntityMapper()::convertToModelAttribute)).isEqualTo(objectMap);
    }

    @Test
    void testConvertAttributes() {
        Map<String, String> subMap = new HashMap<>();
        subMap.put("subTestKey1", "subTestValue1");
        subMap.put("subTestKey2", "subTestValue2");

        Map objectMap = new HashMap();
        objectMap.put(MONGO_ID, "testId");
        objectMap.put("testKey1", "testValue1");
        objectMap.put("testKey2", "testValue2");
        objectMap.put("subMapKey", subMap);
        ObjectNode object = MAPPER.valueToTree(objectMap);

        Map expectedMap = new HashMap();
        expectedMap.put(ModelUtils.ID, "testId");
        expectedMap.put("testKey1", "testValue1");
        expectedMap.put("testKey2", "testValue2");
        expectedMap.put("subMapKey", subMap);
        ObjectNode expected = MAPPER.valueToTree(expectedMap);

        assertThat(ModelUtils.convertAttributes(object, Optional.empty(), new MockMongoEntityMapper()::convertToModelAttribute)).isEqualTo(expected);
    }
}
