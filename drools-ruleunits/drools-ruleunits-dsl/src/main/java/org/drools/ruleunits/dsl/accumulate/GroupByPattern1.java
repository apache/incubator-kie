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
package org.drools.ruleunits.dsl.accumulate;

import org.drools.model.DSL;
import org.drools.model.Variable;
import org.drools.model.functions.Function1;
import org.drools.model.functions.temporal.TemporalPredicate;
import org.drools.model.view.ExprViewItem;
import org.drools.model.view.ViewItem;
import org.drools.ruleunits.dsl.patterns.InternalPatternDef;
import org.drools.ruleunits.dsl.patterns.Pattern1DefImpl;
import org.drools.ruleunits.dsl.patterns.Pattern2DefImpl;
import org.drools.ruleunits.dsl.util.RuleDefinition;

import static org.drools.model.DSL.accFunction;
import static org.drools.model.DSL.declarationOf;
import static org.drools.ruleunits.dsl.accumulate.AccumulatePattern1.bindAccVar;

public class GroupByPattern1<A, K, V> extends Pattern2DefImpl<K, V> {

    private final InternalPatternDef pattern;
    private final Function1<A, K> groupingFunction;
    private final Accumulator1<A, V> acc;

    public GroupByPattern1(RuleDefinition rule, InternalPatternDef pattern, Function1<A, K> groupingFunction, Accumulator1<A, V> acc) {
        super(rule, new Pattern1DefImpl(rule, declarationOf( Object.class )), new Pattern1DefImpl(rule, declarationOf( Object.class )));
        this.pattern = pattern;
        this.groupingFunction = groupingFunction;
        this.acc = acc;
    }

    @Override
    protected Pattern2DefImpl<K, V> addTemporalConstraint(TemporalPredicate temporalPredicate) {
        throw new UnsupportedOperationException("Temporal constraints are not supported on groupBy results");
    }

    @Override
    public ViewItem toExecModelItem() {
        return createGroupByItem(pattern, groupingFunction, acc, patternA.getVariable(), patternB.getVariable());
    }

    static ExprViewItem createGroupByItem(InternalPatternDef pattern, Function1 groupingFunction, Accumulator1 acc, Variable keyVar, Variable valueVar) {
        ViewItem patternDef = pattern.toExecModelItem();
        Variable boundVar = declarationOf( acc.getAccClass() );
        bindAccVar(acc, patternDef, boundVar);
        return DSL.groupBy(
                // Patterns
                patternDef,
                // Grouping Function
                patternDef.getFirstVariable(), keyVar, groupingFunction,
                // Accumulate Result
                accFunction(acc.getAccFuncSupplier(), boundVar).as(valueVar));
    }
}
