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
package org.drools.modelcompiler;

import org.drools.model.Variable;
import org.drools.model.impl.RuleImpl;
import org.drools.modelcompiler.domain.Toy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.drools.model.DSL.declarationOf;

public class SequenceScopeTest {

    private final Variable<Toy> toyV = declarationOf(Toy.class);

    @Test
    public void defaultConsequenceUsesTotalSequenceCount() {
        SequenceScope scope = new SequenceScope();
        int first = scope.open();
        scope.bindVariable(toyV, first, 0);
        scope.open();

        // two sequences open, variable in the first: skip one anchor
        assertThat(scope.variableRef(RuleImpl.DEFAULT_CONSEQUENCE_NAME, toyV)).containsExactly(1, 0);
    }

    @Test
    public void registeredNamedConsequenceUsesSnapshotTakenAtRegistration() {
        SequenceScope scope = new SequenceScope();
        int first = scope.open();
        scope.bindVariable(toyV, first, 0);
        scope.registerNamedConsequence("named");
        scope.open();

        // only one sequence precedes "named": no anchor to skip
        assertThat(scope.variableRef("named", toyV)).containsExactly(0, 0);
    }

    @Test
    public void unregisteredNamedConsequenceIsRejectedInsteadOfFallingBackToTotal() {
        SequenceScope scope = new SequenceScope();
        int first = scope.open();
        scope.bindVariable(toyV, first, 0);
        scope.open();

        assertThatThrownBy(() -> scope.variableRef("never-registered", toyV))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("never-registered");
    }

    @Test
    public void nonSequenceVariableIsNotResolvedEvenForUnregisteredConsequence() {
        SequenceScope scope = new SequenceScope();
        scope.open();

        assertThat(scope.variableRef("never-registered", toyV)).isNull();
    }
}
