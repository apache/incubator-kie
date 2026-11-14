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

import org.junit.jupiter.api.Test;
import org.kie.kogito.persistence.mongodb.mock.MockMongoEntityMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.persistence.mongodb.model.ModelUtils.MONGO_ID;

class MongoEntityMapperTest {

    MockMongoEntityMapper mockMongoEntityMapper = new MockMongoEntityMapper();

    @Test
    void testConvertToMongoAttribute() {
        String testAttribute = "test";
        assertThat(mockMongoEntityMapper.convertToMongoAttribute(testAttribute)).isEqualTo(testAttribute);

        String idAttribute = ModelUtils.ID;
        assertThat(mockMongoEntityMapper.convertToMongoAttribute(idAttribute)).isEqualTo(MONGO_ID);
    }

    @Test
    void testConvertToModelAttribute() {
        String idAttribute = MONGO_ID;
        assertThat(mockMongoEntityMapper.convertToModelAttribute(idAttribute)).isEqualTo(ModelUtils.ID);

        String testAttribute = "nodes";
        assertThat(mockMongoEntityMapper.convertToModelAttribute(testAttribute)).isEqualTo(testAttribute);

        String testSubAttribute = "nodes.name.go";
        assertThat(mockMongoEntityMapper.convertToModelAttribute(testSubAttribute)).isEqualTo("go");
    }
}