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
package org.kie.kogito.trusty.service.common;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kie.kogito.explainability.api.CounterfactualDomainRange;

import com.fasterxml.jackson.databind.node.IntNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.trusty.service.common.CounterfactualParameterValidation.isStructureIdentical;
import static org.kie.kogito.trusty.service.common.TypedValueTestUtils.buildInputStructure;
import static org.kie.kogito.trusty.service.common.TypedValueTestUtils.buildInputUnit;
import static org.kie.kogito.trusty.service.common.TypedValueTestUtils.buildSearchDomainStructure;
import static org.kie.kogito.trusty.service.common.TypedValueTestUtils.buildSearchDomainUnit;

public class CounterfactualParameterValidationIdenticalTest {

    @Test
    public void testSearchDomains_NullNull() {
        assertThat(isStructureIdentical(
                null,
                null)).isTrue();
    }

    @Test
    public void testSearchDomains_EmptyEmpty() {
        assertThat(isStructureIdentical(
                Collections.emptyList(),
                Collections.emptyList())).isTrue();
    }

    @Test
    public void testSearchDomains_NullEmpty() {
        assertThat(isStructureIdentical(
                null,
                Collections.emptyList())).isFalse();
    }

    @Test
    public void testSearchDomains_EmptyNull() {
        assertThat(isStructureIdentical(
                Collections.emptyList(),
                null)).isFalse();
    }

    @Test
    public void testSearchDomains_UnitNull() {
        assertThat(isStructureIdentical(
                List.of(buildInputUnit("age", "integer", new IntNode(18))),
                null)).isFalse();
    }

    @Test
    public void testSearchDomains_UnitEmpty() {
        assertThat(isStructureIdentical(
                List.of(buildInputUnit("age", "integer", new IntNode(18))),
                Collections.emptyList())).isFalse();
    }

    @Test
    public void testSearchDomains_NullUnit() {
        assertThat(isStructureIdentical(
                null,
                List.of(buildSearchDomainUnit("age", "integer", new CounterfactualDomainRange(new IntNode(18), new IntNode(65)))))).isFalse();
    }

    @Test
    public void testSearchDomains_EmptyUnit() {
        assertThat(isStructureIdentical(
                Collections.emptyList(),
                List.of(buildSearchDomainUnit("age", "integer", new CounterfactualDomainRange(new IntNode(18), new IntNode(65)))))).isFalse();
    }

    @Test
    public void testSearchDomains_UnitUnit() {
        assertThat(isStructureIdentical(
                List.of(buildInputUnit("age", "integer", new IntNode(18))),
                List.of(buildSearchDomainUnit("age", "integer", new CounterfactualDomainRange(new IntNode(18), new IntNode(65)))))).isTrue();
    }

    @Test
    public void testSearchDomains_UnitUnits() {
        assertThat(isStructureIdentical(
                List.of(buildInputUnit("age", "integer", new IntNode(18))),
                List.of(buildSearchDomainUnit("age", "integer", new CounterfactualDomainRange(new IntNode(18), new IntNode(65))),
                        buildSearchDomainUnit("salary", "integer", new CounterfactualDomainRange(new IntNode(5000), new IntNode(100000)))))).isFalse();
    }

    @Test
    public void testSearchDomains_UnitsUnit() {
        assertThat(isStructureIdentical(
                List.of(buildInputUnit("age", "integer", new IntNode(18)),
                        buildInputUnit("salary", "integer", new IntNode(10000))),
                List.of(buildSearchDomainUnit("age", "integer", new CounterfactualDomainRange(new IntNode(18), new IntNode(65)))))).isFalse();
    }

    @Test
    public void testSearchDomains_UnitsUnits() {
        assertThat(isStructureIdentical(
                List.of(buildInputUnit("age", "integer", new IntNode(18)),
                        buildInputUnit("salary", "integer", new IntNode(10000))),
                List.of(buildSearchDomainUnit("age", "integer", new CounterfactualDomainRange(new IntNode(18), new IntNode(65))),
                        buildSearchDomainUnit("salary", "integer", new CounterfactualDomainRange(new IntNode(5000), new IntNode(100000)))))).isTrue();
    }

    @Test
    public void testSearchDomains_UnitsUnits__WithDifferentOrder() {
        assertThat(isStructureIdentical(
                List.of(buildInputUnit("salary", "integer", new IntNode(10000)),
                        buildInputUnit("age", "integer", new IntNode(18))),
                List.of(buildSearchDomainUnit("age", "integer", new CounterfactualDomainRange(new IntNode(18), new IntNode(65))),
                        buildSearchDomainUnit("salary", "integer", new CounterfactualDomainRange(new IntNode(5000), new IntNode(100000)))))).isTrue();
    }

    @Test
    public void testSearchDomains_StructureUnit() {
        assertThat(isStructureIdentical(
                List.of(buildInputStructure("person", "tPerson",
                        List.of(buildInputUnit("age", "integer", new IntNode(18)),
                                buildInputUnit("salary", "integer", new IntNode(10000))))),
                List.of(buildSearchDomainUnit("age", "integer", new CounterfactualDomainRange(new IntNode(18), new IntNode(65)))))).isFalse();
    }

    @Test
    public void testSearchDomains_UnitStructure() {
        assertThat(isStructureIdentical(
                List.of(buildInputUnit("age", "integer", new IntNode(18))),
                List.of(buildSearchDomainStructure("person", "tPerson",
                        List.of(buildSearchDomainUnit("age", "integer", new CounterfactualDomainRange(new IntNode(18), new IntNode(65))),
                                buildSearchDomainUnit("salary", "integer", new CounterfactualDomainRange(new IntNode(5000), new IntNode(100000)))))))).isFalse();
    }

    @Test
    public void testSearchDomains_StructureStructure() {
        assertThat(isStructureIdentical(
                List.of(buildInputStructure("person", "tPerson",
                        List.of(buildInputUnit("age", "integer", new IntNode(18)),
                                buildInputUnit("salary", "integer", new IntNode(10000))))),
                List.of(buildSearchDomainStructure("person", "tPerson",
                        List.of(buildSearchDomainUnit("age", "integer", new CounterfactualDomainRange(new IntNode(18), new IntNode(65))),
                                buildSearchDomainUnit("salary", "integer", new CounterfactualDomainRange(new IntNode(5000), new IntNode(100000)))))))).isTrue();
    }

    @Test
    public void testSearchDomains_StructureStructure__WithDifferentOrder() {
        assertThat(isStructureIdentical(
                List.of(buildInputStructure("person", "tPerson",
                        List.of(buildInputUnit("salary", "integer", new IntNode(10000)),
                                buildInputUnit("age", "integer", new IntNode(18))))),
                List.of(buildSearchDomainStructure("person", "tPerson",
                        List.of(buildSearchDomainUnit("age", "integer", new CounterfactualDomainRange(new IntNode(18), new IntNode(65))),
                                buildSearchDomainUnit("salary", "integer", new CounterfactualDomainRange(new IntNode(5000), new IntNode(100000)))))))).isTrue();
    }

    @Test
    public void testSearchDomains_StructureWithStructureStructureWithStructure() {
        assertThat(isStructureIdentical(
                List.of(buildInputStructure("person", "tPerson",
                        List.of(buildInputUnit("age", "integer", new IntNode(18)),
                                buildInputStructure("income", "tIncome",
                                        List.of(buildInputUnit("salary", "integer", new IntNode(10000)),
                                                buildInputUnit("bonuses", "integer", new IntNode(50000))))))),
                List.of(buildSearchDomainStructure("person", "tPerson",
                        List.of(buildSearchDomainUnit("age", "integer", new CounterfactualDomainRange(new IntNode(18), new IntNode(65))),
                                buildSearchDomainStructure("income", "tIncome",
                                        List.of(buildSearchDomainUnit("salary", "integer", new CounterfactualDomainRange(new IntNode(5000), new IntNode(100000))),
                                                buildSearchDomainUnit("bonuses", "integer", new CounterfactualDomainRange(new IntNode(0), new IntNode(500000)))))))))).isTrue();
    }

    @Test
    public void testSearchDomains_ComplexComplex() {
        assertThat(isStructureIdentical(
                List.of(buildInputUnit("hatSize", "integer", new IntNode(16)),
                        buildInputStructure("person", "tPerson",
                                List.of(buildInputUnit("age", "integer", new IntNode(18)),
                                        buildInputStructure("income", "tIncome",
                                                List.of(buildInputUnit("salary", "integer", new IntNode(10000)),
                                                        buildInputUnit("bonuses", "integer", new IntNode(50000))))))),
                List.of(buildSearchDomainUnit("hatSize", "integer", new CounterfactualDomainRange(new IntNode(12), new IntNode(22))),
                        buildSearchDomainStructure("person", "tPerson",
                                List.of(buildSearchDomainUnit("age", "integer", new CounterfactualDomainRange(new IntNode(18), new IntNode(65))),
                                        buildSearchDomainStructure("income", "tIncome",
                                                List.of(buildSearchDomainUnit("salary", "integer", new CounterfactualDomainRange(new IntNode(5000), new IntNode(100000))),
                                                        buildSearchDomainUnit("bonuses", "integer",
                                                                new CounterfactualDomainRange(new IntNode(0), new IntNode(500000)))))))))).isTrue();
    }

    @Test
    public void testSearchDomains_ComplexComplex__WithDifferentOrder() {
        assertThat(isStructureIdentical(
                List.of(buildInputStructure("person", "tPerson",
                        List.of(buildInputStructure("income", "tIncome",
                                List.of(buildInputUnit("salary", "integer", new IntNode(10000)),
                                        buildInputUnit("bonuses", "integer", new IntNode(50000)))),
                                buildInputUnit("age", "integer", new IntNode(18)))),
                        buildInputUnit("hatSize", "integer", new IntNode(16))),
                List.of(buildSearchDomainUnit("hatSize", "integer", new CounterfactualDomainRange(new IntNode(12), new IntNode(22))),
                        buildSearchDomainStructure("person", "tPerson",
                                List.of(buildSearchDomainUnit("age", "integer", new CounterfactualDomainRange(new IntNode(18), new IntNode(65))),
                                        buildSearchDomainStructure("income", "tIncome",
                                                List.of(buildSearchDomainUnit("salary", "integer", new CounterfactualDomainRange(new IntNode(5000), new IntNode(100000))),
                                                        buildSearchDomainUnit("bonuses", "integer",
                                                                new CounterfactualDomainRange(new IntNode(0), new IntNode(500000)))))))))).isTrue();
    }

    @Test
    public void testSearchDomains_ComplexComplex__WithDifferentOrder_WithDifference() {
        assertThat(isStructureIdentical(
                List.of(buildInputStructure("person", "tPerson",
                        List.of(buildInputStructure("income", "tIncome",
                                List.of(buildInputUnit("salary", "integer", new IntNode(10000)))),
                                buildInputUnit("age", "integer", new IntNode(18)))),
                        buildInputUnit("hatSize", "integer", new IntNode(16))),
                List.of(buildSearchDomainUnit("hatSize", "integer", new CounterfactualDomainRange(new IntNode(12), new IntNode(22))),
                        buildSearchDomainStructure("person", "tPerson",
                                List.of(buildSearchDomainUnit("age", "integer", new CounterfactualDomainRange(new IntNode(18), new IntNode(65))),
                                        buildSearchDomainStructure("income", "tIncome",
                                                List.of(buildSearchDomainUnit("salary", "integer", new CounterfactualDomainRange(new IntNode(5000), new IntNode(100000))),
                                                        buildSearchDomainUnit("bonuses", "integer",
                                                                new CounterfactualDomainRange(new IntNode(0), new IntNode(500000)))))))))).isFalse();
    }
}
