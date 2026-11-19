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
package org.kie.kogito.explainability.api;

import java.io.StringWriter;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;

import static org.assertj.core.api.Assertions.assertThat;

public class CounterfactualDomainSerialisationTest {

    private ObjectMapper mapper;
    private StringWriter writer;

    @BeforeEach
    public void setup() {
        this.mapper = new ObjectMapper();
        this.writer = new StringWriter();
    }

    @Test
    public void testCounterfactualSearchDomain_Range_RoundTrip() throws Exception {
        CounterfactualDomainRange domainRange = new CounterfactualDomainRange(new IntNode(18), new IntNode(65));
        CounterfactualSearchDomainUnitValue searchDomain = new CounterfactualSearchDomainUnitValue("integer",
                "integer",
                Boolean.TRUE,
                domainRange);

        mapper.writeValue(writer, searchDomain);
        String searchDomainJson = writer.toString();
        assertThat(searchDomainJson).isNotNull();

        CounterfactualSearchDomainValue roundTrippedSearchDomain = mapper.readValue(searchDomainJson, CounterfactualSearchDomainValue.class);
        assertThat((roundTrippedSearchDomain instanceof CounterfactualSearchDomainUnitValue)).isTrue();
        CounterfactualSearchDomainUnitValue roundTrippedSearchDomainUnit = (CounterfactualSearchDomainUnitValue) roundTrippedSearchDomain;

        assertThat(roundTrippedSearchDomainUnit.getKind()).isEqualTo(searchDomain.getKind());
        assertThat(roundTrippedSearchDomainUnit.getType()).isEqualTo(searchDomain.getType());
        assertThat(roundTrippedSearchDomainUnit.isFixed()).isEqualTo(searchDomain.isFixed());
        assertThat((roundTrippedSearchDomainUnit.getDomain() instanceof CounterfactualDomainRange)).isTrue();

        CounterfactualDomainRange roundTrippedDomainRange = (CounterfactualDomainRange) roundTrippedSearchDomainUnit.getDomain();
        assertThat(roundTrippedDomainRange.getLowerBound()).isEqualTo(domainRange.getLowerBound());
        assertThat(roundTrippedDomainRange.getUpperBound()).isEqualTo(domainRange.getUpperBound());
    }

    @Test
    public void testCounterfactualSearchDomain_Categorical_RoundTrip() throws Exception {
        CounterfactualDomainCategorical domainCategorical = new CounterfactualDomainCategorical(List.of(new TextNode("A"), new TextNode("B")));
        CounterfactualSearchDomainUnitValue searchDomain = new CounterfactualSearchDomainUnitValue("integer",
                "integer",
                Boolean.TRUE,
                domainCategorical);

        mapper.writeValue(writer, searchDomain);
        String searchDomainJson = writer.toString();
        assertThat(searchDomainJson).isNotNull();

        CounterfactualSearchDomainValue roundTrippedSearchDomain = mapper.readValue(searchDomainJson, CounterfactualSearchDomainValue.class);
        assertThat((roundTrippedSearchDomain instanceof CounterfactualSearchDomainUnitValue)).isTrue();
        CounterfactualSearchDomainUnitValue roundTrippedSearchDomainUnit = (CounterfactualSearchDomainUnitValue) roundTrippedSearchDomain;

        assertThat(roundTrippedSearchDomainUnit.getKind()).isEqualTo(searchDomain.getKind());
        assertThat(roundTrippedSearchDomainUnit.getType()).isEqualTo(searchDomain.getType());
        assertThat(roundTrippedSearchDomainUnit.isFixed()).isEqualTo(searchDomain.isFixed());
        assertThat((roundTrippedSearchDomainUnit.getDomain() instanceof CounterfactualDomainCategorical)).isTrue();

        CounterfactualDomainCategorical roundTrippedDomainCategorical = (CounterfactualDomainCategorical) roundTrippedSearchDomainUnit.getDomain();
        assertThat(roundTrippedDomainCategorical.getCategories().size()).isEqualTo(domainCategorical.getCategories().size());
        assertThat(roundTrippedDomainCategorical.getCategories().containsAll(domainCategorical.getCategories())).isTrue();
    }

}
