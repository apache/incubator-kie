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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kie.kogito.tracing.typedvalue.StructureValue;
import org.kie.kogito.tracing.typedvalue.UnitValue;

import com.fasterxml.jackson.databind.node.IntNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.trusty.service.common.CounterfactualParameterValidation.isStructureSubset;
import static org.kie.kogito.trusty.service.common.TypedValueTestUtils.buildGoalStructure;
import static org.kie.kogito.trusty.service.common.TypedValueTestUtils.buildGoalUnit;
import static org.kie.kogito.trusty.service.common.TypedValueTestUtils.buildOutcomeStructure;
import static org.kie.kogito.trusty.service.common.TypedValueTestUtils.buildOutcomeUnit;

public class CounterfactualParameterValidationSubsetTest {

    @Test
    public void testGoals_NullNull() {
        assertThat(isStructureSubset(
                null,
                null)).isTrue();
    }

    @Test
    public void testGoals_EmptyEmpty() {
        assertThat(isStructureSubset(
                Collections.emptyList(),
                Collections.emptyList())).isTrue();
    }

    @Test
    public void testGoals_NullEmpty() {
        assertThat(isStructureSubset(
                null,
                Collections.emptyList())).isFalse();
    }

    @Test
    public void testGoals_EmptyNull() {
        assertThat(isStructureSubset(
                Collections.emptyList(),
                null)).isFalse();
    }

    @Test
    public void testGoals_UnitNull() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeUnit("age", "integer", new IntNode(18))),
                null)).isFalse();
    }

    @Test
    public void testGoals_UnitEmpty() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeUnit("age", "integer", new IntNode(18))),
                Collections.emptyList())).isTrue();
    }

    @Test
    public void testGoals_NullUnit() {
        assertThat(isStructureSubset(
                null,
                List.of(buildGoalUnit("age", "integer", new IntNode(18))))).isFalse();
    }

    @Test
    public void testGoals_EmptyUnit() {
        assertThat(isStructureSubset(
                Collections.emptyList(),
                List.of(buildGoalUnit("age", "integer", new IntNode(18))))).isFalse();
    }

    @Test
    public void testGoals_UnitUnit() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeUnit("age", "integer", new IntNode(18))),
                List.of(buildGoalUnit("age", "integer", new IntNode(18))))).isTrue();
    }

    @Test
    public void testGoals_UnitUnits() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeUnit("age", "integer", new IntNode(18))),
                List.of(buildGoalUnit("age", "integer", new IntNode(18)),
                        buildGoalUnit("salary", "integer", new IntNode(5000))))).isFalse();
    }

    @Test
    public void testGoals_UnitsUnit() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeUnit("age", "integer", new IntNode(18)),
                        buildOutcomeUnit("salary", "integer", new IntNode(10000))),
                List.of(buildGoalUnit("age", "integer", new IntNode(18))))).isTrue();
    }

    @Test
    public void testGoals_UnitsUnits() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeUnit("age", "integer", new IntNode(18)),
                        buildOutcomeUnit("salary", "integer", new IntNode(10000))),
                List.of(buildGoalUnit("age", "integer", new IntNode(18)),
                        buildGoalUnit("salary", "integer", new IntNode(100000))))).isTrue();
    }

    @Test
    public void testGoals_UnitsUnits__WithDifferentOrder() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeUnit("salary", "integer", new IntNode(10000)),
                        buildOutcomeUnit("age", "integer", new IntNode(18))),
                List.of(buildGoalUnit("age", "integer", new IntNode(18)),
                        buildGoalUnit("salary", "integer", new IntNode(100000))))).isTrue();
    }

    @Test
    public void testGoals_StructureUnit() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeStructure("person", "tPerson",
                        Map.of("age", new UnitValue("integer", "integer", new IntNode(18)),
                                "salary", new UnitValue("integer", "integer", new IntNode(10000))))),
                List.of(buildGoalUnit("age", "integer", new IntNode(18))))).isFalse();
    }

    @Test
    public void testGoals_UnitStructure() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeUnit("age", "integer", new IntNode(18))),
                List.of(buildGoalStructure("person", "tPerson",
                        Map.of("age", new UnitValue("integer", "integer", new IntNode(18)),
                                "salary", new UnitValue("integer", "integer", new IntNode(100000))))))).isFalse();
    }

    @Test
    public void testGoals_StructureStructure() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeStructure("person", "tPerson",
                        Map.of("age", new UnitValue("integer", "integer", new IntNode(18)),
                                "salary", new UnitValue("integer", "integer", new IntNode(10000))))),
                List.of(buildGoalStructure("person", "tPerson",
                        Map.of("age", new UnitValue("integer", "integer", new IntNode(18)),
                                "salary", new UnitValue("integer", "integer", new IntNode(100000))))))).isTrue();
    }

    @Test
    public void testGoals_StructureStructureSubset() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeStructure("person", "tPerson",
                        Map.of("age", new UnitValue("integer", "integer", new IntNode(18)),
                                "salary", new UnitValue("integer", "integer", new IntNode(10000))))),
                List.of(buildGoalStructure("person", "tPerson",
                        Map.of("age", new UnitValue("integer", "integer", new IntNode(18))))))).isTrue();
    }

    @Test
    public void testGoals_StructureStructure__WithDifferentOrder() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeStructure("person", "tPerson",
                        Map.of("salary", new UnitValue("integer", "integer", new IntNode(10000)),
                                "age", new UnitValue("integer", "integer", new IntNode(18))))),
                List.of(buildGoalStructure("person", "tPerson",
                        Map.of("age", new UnitValue("integer", "integer", new IntNode(18)),
                                "salary", new UnitValue("integer", "integer", new IntNode(100000))))))).isTrue();
    }

    @Test
    public void testGoals_StructureWithStructureStructureWithStructure() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeStructure("person", "tPerson",
                        Map.of("age", new UnitValue("integer", "integer", new IntNode(18)),
                                "income", new StructureValue("tIncome",
                                        Map.of("salary", new UnitValue("integer", "integer", new IntNode(10000)),
                                                "bonuses", new UnitValue("integer", "integer", new IntNode(50000))))))),
                List.of(buildGoalStructure("person", "tPerson",
                        Map.of("age", new UnitValue("integer", "integer", new IntNode(18)),
                                "income", new StructureValue("tIncome",
                                        Map.of("salary", new UnitValue("integer", "integer", new IntNode(100000)),
                                                "bonuses", new UnitValue("integer", "integer", new IntNode(500000))))))))).isTrue();
    }

    @Test
    public void testGoals_ComplexComplex() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeUnit("hatSize", "integer", new IntNode(16)),
                        buildOutcomeStructure("person", "tPerson",
                                Map.of("age", new UnitValue("integer", "integer", new IntNode(18)),
                                        "income", new StructureValue("tIncome",
                                                Map.of("salary", new UnitValue("integer", "integer", new IntNode(10000)),
                                                        "bonuses", new UnitValue("integer", "integer", new IntNode(50000))))))),
                List.of(buildGoalUnit("hatSize", "integer", new IntNode(12)),
                        buildGoalStructure("person", "tPerson",
                                Map.of("age", new UnitValue("integer", "integer", new IntNode(18)),
                                        "income", new StructureValue("tIncome",
                                                Map.of("salary", new UnitValue("integer", "integer", new IntNode(100000)),
                                                        "bonuses", new UnitValue("integer", "integer", new IntNode(500000))))))))).isTrue();
    }

    @Test
    public void testGoals_ComplexComplex__WithDifferentOrder() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeStructure("person", "tPerson",
                        Map.of("income", new StructureValue("tIncome",
                                Map.of("salary", new UnitValue("integer", "integer", new IntNode(10000)),
                                        "bonuses", new UnitValue("integer", "integer", new IntNode(50000)))),
                                "age", new UnitValue("integer", "integer", new IntNode(18)))),
                        buildOutcomeUnit("hatSize", "integer", new IntNode(16))),
                List.of(buildGoalUnit("hatSize", "integer", new IntNode(12)),
                        buildGoalStructure("person", "tPerson",
                                Map.of("age", new UnitValue("integer", "integer", new IntNode(18)),
                                        "income", new StructureValue("tIncome",
                                                Map.of("salary", new UnitValue("integer", "integer", new IntNode(100000)),
                                                        "bonuses", new UnitValue("integer", "integer", new IntNode(500000))))))))).isTrue();
    }

    @Test
    public void testGoals_ComplexComplex__WithDifferentOrder_WithDifference() {
        assertThat(isStructureSubset(
                List.of(buildOutcomeStructure("person", "tPerson",
                        Map.of("income", new StructureValue("tIncome",
                                Map.of("salary", new UnitValue("integer", "integer", new IntNode(10000)))),
                                "age", new UnitValue("integer", "integer", new IntNode(18)))),
                        buildOutcomeUnit("hatSize", "integer", new IntNode(16))),
                List.of(buildGoalUnit("hatSize", "integer", new IntNode(12)),
                        buildGoalStructure("person", "tPerson",
                                Map.of("age", new UnitValue("integer", "integer", new IntNode(18)),
                                        "income", new StructureValue("tIncome",
                                                Map.of("salary", new UnitValue("integer", "integer", new IntNode(100000)),
                                                        "bonuses", new UnitValue("integer", "integer", new IntNode(500000))))))))).isFalse();
    }
}
