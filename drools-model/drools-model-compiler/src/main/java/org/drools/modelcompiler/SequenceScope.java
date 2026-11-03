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

import java.util.HashMap;
import java.util.Map;

import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.model.Variable;

/**
 * Build-time bookkeeping for the {@code sequence(...)} calls of one rule: where each step variable was
 * bound, and how many sequences precede each named consequence. Together these give the number of
 * SequencerMemory anchors a consequence must walk back to reach a variable's sequence.
 */
class SequenceScope {

    private record FilterPosition( int sequence, int filter ) { }

    private final Map<Variable, FilterPosition> filterPositions = new HashMap<>();
    private final Map<String, Integer> sequencesBeforeConsequence = new HashMap<>();
    private int sequenceCount = 0;

    int open() {
        return sequenceCount++;
    }

    void bindVariable( Variable variable, int sequence, int filter ) {
        filterPositions.put( variable, new FilterPosition( sequence, filter ) );
    }

    void registerNamedConsequence( String name ) {
        if (!RuleImpl.DEFAULT_CONSEQUENCE_NAME.equals( name )) {
            sequencesBeforeConsequence.put( name, sequenceCount );
        }
    }

    boolean hasVariables() {
        return !filterPositions.isEmpty();
    }

    int[] variableRef( String consequenceName, Variable variable ) {
        FilterPosition position = filterPositions.get( variable );
        if (position == null) {
            return null;
        }
        int inScope = sequencesBeforeConsequence.getOrDefault( consequenceName, sequenceCount );
        return new int[]{ inScope - 1 - position.sequence(), position.filter() };
    }
}
