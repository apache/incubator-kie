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
package org.kie.kogito.persistence.redis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kie.kogito.persistence.api.query.AttributeFilter;
import org.kie.kogito.persistence.api.query.QueryFilterFactory;
import org.kie.kogito.persistence.api.query.SortDirection;
import org.mockito.Mockito;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.redisearch.Document;
import io.redisearch.Query;
import io.redisearch.SearchResult;
import io.redisearch.client.Client;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.kie.kogito.persistence.api.query.QueryFilterFactory.orderBy;
import static org.kie.kogito.persistence.redis.Constants.RAW_OBJECT_FIELD;
import static org.kie.kogito.persistence.redis.Person.NAME_PROPERTY;
import static org.kie.kogito.persistence.redis.TestContants.TEST_INDEX_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class RedisQueryTest {

    @Test
    public void multipleAttributeSortingIsNotSupported() {
        RedisQuery<Person> redisQuery = new RedisQuery<>(new RedisClientMock(), TEST_INDEX_NAME, Person.class);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> redisQuery.sort(asList(orderBy("first", SortDirection.DESC), orderBy("second", SortDirection.ASC))));
    }

    @Test
    public void executeTest() throws JsonProcessingException {
        Client client = Mockito.mock(Client.class);

        Person person = new Person("pippo", 20);

        // Add the raw object to the map
        Map<String, Object> map = JsonUtils.getMapper().convertValue(person, Map.class);
        map.put(RAW_OBJECT_FIELD, JsonUtils.getMapper().writeValueAsString(person));

        // Mock the response
        Document document = new Document("pippo", map);
        SearchResult searchResult = new SearchResult(singletonList(0L), false, false, false);
        searchResult.docs.add(document);
        when(client.search(any(Query.class))).thenReturn(searchResult);

        RedisQuery<Person> redisQuery = new RedisQuery<>(client, TEST_INDEX_NAME, Person.class);

        // Add a filter
        List<AttributeFilter<?>> filters = new ArrayList<>();
        filters.add(QueryFilterFactory.equalTo(NAME_PROPERTY, "pippo"));
        redisQuery.filter(filters);

        List<Person> result = redisQuery.execute();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0).getName()).isEqualTo("pippo");
        assertThat(result.get(0).getAge()).isEqualTo(20);
    }
}
