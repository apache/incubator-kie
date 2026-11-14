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
package org.kie.kogito.persistence.reporting.database;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaGenerationActionTest {

    @ParameterizedTest
    @MethodSource("parameters")
    void testConversion(final String jpaText,
            final SchemaGenerationAction expectedStrategy,
            final String expectedJpaText) {
        final SchemaGenerationAction actualStrategy = SchemaGenerationAction.fromString(jpaText);
        assertThat(actualStrategy).isEqualTo(expectedStrategy);
        assertThat(actualStrategy.getActionString()).isEqualTo(expectedJpaText);
    }

    private static Stream<Arguments> parameters() {
        return Stream.of(
                Arguments.of("none", SchemaGenerationAction.NONE, "none"),
                Arguments.of("NONE", SchemaGenerationAction.NONE, "none"),
                Arguments.of("create", SchemaGenerationAction.CREATE, "create"),
                Arguments.of("CREATE", SchemaGenerationAction.CREATE, "create"),
                Arguments.of("drop-and-create", SchemaGenerationAction.DROP_AND_CREATE, "drop-and-create"),
                Arguments.of("DROP-AND-CREATE", SchemaGenerationAction.DROP_AND_CREATE, "drop-and-create"),
                Arguments.of("drop", SchemaGenerationAction.DROP, "drop"),
                Arguments.of("DROP", SchemaGenerationAction.DROP, "drop"),
                Arguments.of("unknown", SchemaGenerationAction.NONE, "none"));
    }
}
