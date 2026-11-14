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
package org.drools.ruleunits.impl;

import org.drools.ruleunits.api.RuleUnitProvider;
import org.drools.ruleunits.impl.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.builder.CompilationErrorsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnsupportedMethodTest {

    @Test
    public void insert_shouldBuildError() {
        UnsupportedMethodUnit unit = new UnsupportedMethodUnit();
        unit.getPersons().add(new Person("John", 30));

        assertThatThrownBy(() -> RuleUnitProvider.get().createRuleUnitInstance(unit))
                .isInstanceOfSatisfying(CompilationErrorsException.class, exception -> {
                    assertThat(exception.getErrorMessages()).hasSize(2);
                    assertThat(exception.getErrorMessages())
                            .allSatisfy(error -> assertThat(error.getText()).contains("is not supported with RuleUnit"));
                });
    }
}
