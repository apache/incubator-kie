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
package org.jbpm.workflow.core.impl;

import java.util.Collections;
import java.util.Map;

import org.jbpm.workflow.core.node.Assignment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IOSpecificationTest {

    /**
     * Single variable mapping via {@code <assignment>} without expression.
     * Should resolve the mapping from label / variable name (e.g. "var1").
     */
    @Test
    void testInputMappingWithAssignmentsNoExpression() {
        IOSpecification ioSpec = new IOSpecification();
        DataAssociation da = new DataAssociation(
                Collections.emptyList(),
                null,
                Collections.singletonList(new Assignment(null, new DataDefinition("_var1", "var1", "String"), new DataDefinition("_in1", "Input1", "String"))),
                null);
        ioSpec.getDataInputAssociation().add(da);

        Map<String, String> inputMapping = ioSpec.getInputMapping();
        assertThat(inputMapping).containsEntry("Input1", "var1");
    }

    /**
     * Expression / static constant mapping via {@code <assignment>} with an expression.
     * Should resolve the mapping from expression (e.g. "#{var1}" or constant value).
     */
    @Test
    void testInputMappingWithAssignmentsHavingExpression() {
        IOSpecification ioSpec = new IOSpecification();
        DataDefinition fromExpr = new DataDefinition("_expr1", "EXPRESSION (#{var1})", "String", "#{var1}");
        DataAssociation da = new DataAssociation(
                Collections.emptyList(),
                null,
                Collections.singletonList(new Assignment(null, fromExpr, new DataDefinition("_in1", "Input1", "String"))),
                null);
        ioSpec.getDataInputAssociation().add(da);

        Map<String, String> inputMapping = ioSpec.getInputMapping();
        assertThat(inputMapping).containsEntry("Input1", "#{var1}");
    }

    /**
     * Single variable mapping via direct {@code <sourceRef>} (no assignment).
     * Should resolve the mapping from label / variable name (e.g. "var1").
     */
    @Test
    void testInputMappingWithSourcesOnly() {
        IOSpecification ioSpec = new IOSpecification();
        DataAssociation da = new DataAssociation(
                Collections.singletonList(new DataDefinition("_var1", "var1", "String")),
                new DataDefinition("_in1", "Input1", "String"),
                Collections.emptyList(),
                null);
        ioSpec.getDataInputAssociation().add(da);

        Map<String, String> inputMapping = ioSpec.getInputMapping();
        assertThat(inputMapping).containsEntry("Input1", "var1");
    }

    /**
     * Single variable mapping via {@code <assignment>} without expression.
     * Should resolve the mapping from label / variable name (e.g. "var1").
     */
    @Test
    void testOutputMappingWithAssignmentsNoExpression() {
        IOSpecification ioSpec = new IOSpecification();
        DataAssociation da = new DataAssociation(
                Collections.emptyList(),
                null,
                Collections.singletonList(new Assignment(null, new DataDefinition("_out1", "Output1", "String"), new DataDefinition("_var1", "var1", "String"))),
                null);
        ioSpec.getDataOutputAssociation().add(da);

        Map<String, String> outputMapping = ioSpec.getOutputMapping();
        assertThat(outputMapping).containsEntry("var1", "Output1");

        Map<String, String> outputMappingBySources = ioSpec.getOutputMappingBySources();
        assertThat(outputMappingBySources).containsEntry("Output1", "var1");
    }

    /**
     * Expression mapping via {@code <assignment>} with an expression.
     * Should resolve the mapping from expression (e.g. "#{var1}").
     */
    @Test
    void testOutputMappingWithAssignmentsHavingExpression() {
        IOSpecification ioSpec = new IOSpecification();
        DataDefinition toExpr = new DataDefinition("_expr1", "EXPRESSION (#{var1})", "String", "#{var1}");
        DataAssociation da = new DataAssociation(
                Collections.emptyList(),
                null,
                Collections.singletonList(new Assignment(null, new DataDefinition("_out1", "Output1", "String"), toExpr)),
                null);
        ioSpec.getDataOutputAssociation().add(da);

        Map<String, String> outputMapping = ioSpec.getOutputMapping();
        assertThat(outputMapping).containsEntry("#{var1}", "Output1");

        Map<String, String> outputMappingBySources = ioSpec.getOutputMappingBySources();
        assertThat(outputMappingBySources).containsEntry("Output1", "#{var1}");
    }

    /**
     * Single variable mapping via direct {@code <sourceRef>} (no assignment).
     * Should resolve the mapping from label / variable name (e.g. "var1").
     */
    @Test
    void testOutputMappingWithSourcesOnly() {
        IOSpecification ioSpec = new IOSpecification();
        DataAssociation da = new DataAssociation(
                Collections.singletonList(new DataDefinition("_out1", "Output1", "String")),
                new DataDefinition("_var1", "var1", "String"),
                Collections.emptyList(),
                null);
        ioSpec.getDataOutputAssociation().add(da);

        Map<String, String> outputMapping = ioSpec.getOutputMapping();
        assertThat(outputMapping).containsEntry("var1", "Output1");

        Map<String, String> outputMappingBySources = ioSpec.getOutputMappingBySources();
        assertThat(outputMappingBySources).containsEntry("Output1", "var1");
    }
}
