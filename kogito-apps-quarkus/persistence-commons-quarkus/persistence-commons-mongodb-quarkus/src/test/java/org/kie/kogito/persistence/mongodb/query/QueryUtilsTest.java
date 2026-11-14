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
package org.kie.kogito.persistence.mongodb.query;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.kie.kogito.persistence.mongodb.mock.MockMongoEntityMapper;

import com.mongodb.client.model.Filters;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.and;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.between;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.contains;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.containsAll;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.containsAny;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.equalTo;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.greaterThan;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.greaterThanEqual;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.in;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.isNull;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.lessThan;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.lessThanEqual;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.like;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.not;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.notNull;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.or;

class QueryUtilsTest {

    @Test
    void testGenerateQuery() {
        Optional<Bson> result = QueryUtils.generateQuery(List.of(contains("test", "testValue")), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo(Filters.and(Stream.of(Filters.eq("test", "testValue")).collect(toList())));
    }

    @Test
    void testGenerateSingleQuery_contains() {
        Bson result = QueryUtils.generateSingleQuery(contains("test", "testValue"), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.eq("test", "testValue"));
    }

    @Test
    void testGenerateSingleQuery_equalTo() {
        Bson result = QueryUtils.generateSingleQuery(equalTo("test", "testValue"), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.eq("test", "testValue"));
    }

    @Test
    void testGenerateSingleQuery_like() {
        Bson result = QueryUtils.generateSingleQuery(like("test", "testValue"), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.regex("test", "testValue"));
    }

    @Test
    void testGenerateSingleQuery_isNull() {
        Bson result = QueryUtils.generateSingleQuery(isNull("test"), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.exists("test", false));
    }

    @Test
    void testGenerateSingleQuery_notNull() {
        Bson result = QueryUtils.generateSingleQuery(notNull("test"), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.exists("test", true));
    }

    @Test
    void testGenerateSingleQuery_greaterThan() {
        Bson result = QueryUtils.generateSingleQuery(greaterThan("test", "testValue"), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.gt("test", "testValue"));
    }

    @Test
    void testGenerateSingleQuery_greatThanEqual() {
        Bson result = QueryUtils.generateSingleQuery(greaterThanEqual("test", "testValue"), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.gte("test", "testValue"));
    }

    @Test
    void testGenerateSingleQuery_lessThan() {
        Bson result = QueryUtils.generateSingleQuery(lessThan("test", "testValue"), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.lt("test", "testValue"));
    }

    @Test
    void testGenerateSingleQuery_lessThanEqual() {
        Bson result = QueryUtils.generateSingleQuery(lessThanEqual("test", "testValue"), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.lte("test", "testValue"));
    }

    @Test
    void testGenerateSingleQuery_between() {
        Bson result = QueryUtils.generateSingleQuery(between("test", "testValue1", "testValue2"), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.and(Filters.gte("test", "testValue1"), Filters.lte("test", "testValue2")));
    }

    @Test
    void testGenerateSingleQuery_in() {
        Bson result = QueryUtils.generateSingleQuery(in("test", List.of("testValue")), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.in("test", List.of("testValue")));
    }

    @Test
    void testGenerateSingleQuery_containsAll() {
        Bson result = QueryUtils.generateSingleQuery(containsAll("test", List.of("testValue")), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.all("test", List.of("testValue")));
    }

    @Test
    void testGenerateSingleQuery_containsAny() {
        Bson result = QueryUtils.generateSingleQuery(containsAny("test", List.of("testValue")), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.or(Filters.eq("test", "testValue")));
    }

    @Test
    void testGenerateSingleQuery_or() {
        Bson result = QueryUtils.generateSingleQuery(or(List.of(contains("test", "testValue"))), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.or(Filters.eq("test", "testValue")));
    }

    @Test
    void testGenerateSingleQuery_and() {
        Bson result = QueryUtils.generateSingleQuery(and(List.of(contains("test", "testValue"))), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.and(Filters.eq("test", "testValue")));
    }

    @Test
    void testGenerateSingleQuery_not() {
        Bson result = QueryUtils.generateSingleQuery(not(equalTo("test", "testValue")), new MockMongoEntityMapper()::convertToMongoAttribute);
        assertThat(result).isEqualTo(Filters.not(Filters.eq("test", "testValue")));
    }
}
