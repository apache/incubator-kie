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

import org.junit.jupiter.api.Test;
import org.kie.kogito.persistence.api.query.AttributeFilter;
import org.kie.kogito.persistence.api.query.QueryFilterFactory;
import org.mockito.Mockito;

import io.redisearch.Query;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.kie.kogito.persistence.redis.TestContants.TEST_INDEX_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RedisQueryFactoryTest {

    @Test
    public void inFilterShouldNotBeSupported() {
        testFilterShouldNotBeSupported(QueryFilterFactory.in("attribute", new ArrayList<>()));
    }

    @Test
    public void containsFilterShouldNotBeSupported() {
        testFilterShouldNotBeSupported(QueryFilterFactory.contains("attribute", "value"));
    }

    @Test
    public void containsAllFilterShouldNotBeSupported() {
        testFilterShouldNotBeSupported(QueryFilterFactory.containsAll("attribute", new ArrayList<>()));
    }

    @Test
    public void containsAnyFilterShouldNotBeSupported() {
        testFilterShouldNotBeSupported(QueryFilterFactory.containsAny("attribute", new ArrayList<>()));
    }

    @Test
    public void isNullFilterShouldNotBeSupported() {
        testFilterShouldNotBeSupported(QueryFilterFactory.isNull("attribute"));
    }

    @Test
    public void notNullFilterShouldNotBeSupported() {
        testFilterShouldNotBeSupported(QueryFilterFactory.notNull("attribute"));
    }

    @Test
    public void andFilterShouldNotBeSupported() {
        testFilterShouldNotBeSupported(QueryFilterFactory.and(new ArrayList<>()));
    }

    @Test
    public void orFilterShouldNotBeSupported() {
        testFilterShouldNotBeSupported(QueryFilterFactory.or(new ArrayList<>()));
    }

    @Test
    public void notFilterShouldNotBeSupported() {
        testFilterShouldNotBeSupported(QueryFilterFactory.not(QueryFilterFactory.equalTo("attribute", "test")));
    }

    @Test
    public void buildQueryBodyTest() {
        List<AttributeFilter<?>> filters = new ArrayList<>();
        filters.add(QueryFilterFactory.equalTo("firstAttribute", "firstValue"));
        filters.add(QueryFilterFactory.like("secondAttribute", "secondValue"));
        filters.add(QueryFilterFactory.between("shouldBeIgnored", 0, 1));
        String result = RedisQueryFactory.buildQueryBody(TEST_INDEX_NAME, filters);

        assertThat(result).isEqualTo("@indexName:myIndexName @firstAttribute:firstValue @secondAttribute:secondValue");
    }

    @Test
    public void addFiltersTest() {
        List<AttributeFilter<?>> filters = new ArrayList<>();
        filters.add(QueryFilterFactory.equalTo("shouldBeIgnored", 0));
        filters.add(QueryFilterFactory.like("shouldBeIgnoredAsWell", "test"));
        filters.add(QueryFilterFactory.between("betweenAttribute", 0, 1));
        filters.add(QueryFilterFactory.greaterThan("greaterThanAttribute", 0d));
        filters.add(QueryFilterFactory.greaterThanEqual("greaterThanEqualAttribute", 0));
        filters.add(QueryFilterFactory.lessThan("lessThanAttribute", 0d));
        filters.add(QueryFilterFactory.lessThanEqual("lessThanEqualAttribute", 0));

        Query query = Mockito.mock(Query.class);
        when(query.addFilter(any(io.redisearch.Query.NumericFilter.class))).thenReturn(query);

        RedisQueryFactory.addFilters(query, filters);

        verify(query, times(5)).addFilter(any(Query.Filter.class));
    }

    private void testFilterShouldNotBeSupported(AttributeFilter<?> filter) {
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> RedisQueryFactory.addFilters(new Query(""), singletonList(filter)));
    }
}
