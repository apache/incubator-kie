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
package org.drools.ruleunits.dsl.patterns;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.drools.model.Condition;
import org.drools.model.DSL;
import org.drools.model.Index;
import org.drools.model.functions.temporal.TemporalPredicate;
import org.drools.model.functions.Block1;
import org.drools.model.functions.Block2;
import org.drools.model.functions.Block3;
import org.drools.model.functions.Function1;
import org.drools.model.functions.Predicate2;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.dsl.RuleFactory;
import org.drools.ruleunits.dsl.constraints.Beta1Constraint;
import org.drools.ruleunits.dsl.util.RuleDefinition;
import org.drools.ruleunits.impl.datasources.ConsequenceDataStore;
import org.drools.ruleunits.impl.datasources.ConsequenceDataStoreImpl;
import org.kie.api.runtime.rule.RuleContext;

import static org.drools.model.functions.Function1.identity;

public class Pattern2DefImpl<A, B> extends SinglePatternDef<B> implements Pattern2Def<A, B> {

    protected final Pattern1DefImpl<A> patternA;
    protected final Pattern1DefImpl<B> patternB;

    public Pattern2DefImpl(RuleDefinition rule, Pattern1DefImpl<A> patternA, Pattern1DefImpl<B> patternB) {
        super(rule, patternB.variable);
        this.patternA = patternA;
        this.patternB = patternB;
    }

    @Override
    public Pattern2DefImpl<A, B> filter(Predicate2<A, B> predicate) {
        patternB.constraints.add(patternDef -> patternDef.expr(UUID.randomUUID().toString(), patternA.variable, (b,a) -> predicate.test((A) a, (B) b)));
        return this;
    }

    @Override
    public Pattern2DefImpl<A, B> filter(Index.ConstraintType constraintType, Function1<A, B> rightExtractor) {
        return filter("this", identity(), constraintType, rightExtractor);
    }

    @Override
    public <V> Pattern2DefImpl<A, B> filter(Function1<B, V> leftExtractor, Index.ConstraintType constraintType, Function1<A, V> rightExtractor) {
        return filter(null, leftExtractor, constraintType, rightExtractor);
    }

    @Override
    public <V> Pattern2DefImpl<A, B> filter(String fieldName, Function1<B, V> leftExtractor, Index.ConstraintType constraintType, Function1<A, V> rightExtractor) {
        patternB.constraints.add(new Beta1Constraint<>(variable, fieldName, leftExtractor, constraintType, patternA.variable, rightExtractor));
        return this;
    }

    @Override
    public Pattern2DefImpl<A, B> after(long min, long max, TimeUnit unit) {
        return addTemporalConstraint(DSL.after(min, unit, max, unit));
    }

    @Override
    public Pattern2DefImpl<A, B> before(long min, long max, TimeUnit unit) {
        return addTemporalConstraint(DSL.before(min, unit, max, unit));
    }

    protected Pattern2DefImpl<A, B> addTemporalConstraint(TemporalPredicate temporalPredicate) {
        patternB.constraints.add(patternDef -> patternDef.expr(UUID.randomUUID().toString(), patternA.variable, temporalPredicate));
        return this;
    }

    @Override
    public <C> Pattern3Def<A, B, C> on(DataSource<C> dataSource) {
        return join(rule.on(dataSource));
    }

    @Override
    public <C> Pattern3Def<A, B, C> join(Function1<RuleFactory, Pattern1Def<C>> patternBuilder) {
        return join((Pattern1DefImpl) patternBuilder.apply(rule));
    }

    private <C> Pattern3Def<A, B, C> join(Pattern1DefImpl<C> other) {
        return new Pattern3DefImpl<>(rule, patternA, patternB, other);
    }

    @Override
    public Pattern2DefImpl<A, B> exists(Function1<Pattern2Def<A, B>, PatternDef> patternBuilder) {
        rule.addPattern( new ExistentialPatternDef( Condition.Type.EXISTS, rule.internalCreatePattern(this, patternBuilder) ) );
        return this;
    }

    @Override
    public Pattern2DefImpl<A, B> not(Function1<Pattern2Def<A, B>, PatternDef> patternBuilder) {
        rule.addPattern( new ExistentialPatternDef( Condition.Type.NOT, rule.internalCreatePattern(this, patternBuilder) ) );
        return this;
    }

    @Override
    public void execute(Block2<A, B> block) {
        rule.setConsequence( DSL.on(patternA.variable, variable).execute(block) );
    }

    @Override
    public <G> void execute(G globalObject, Block3<G, A, B> block) {
        rule.setConsequence( DSL.on(rule.asGlobal(globalObject), patternA.variable, variable).execute(block) );
    }

    @Override
    public <T> void executeOnDataStore(DataStore<T> dataStore, Block1<ConsequenceDataStore<T>> block) {
        rule.setConsequence( DSL.on(rule.asGlobal(dataStore)).execute( (drools, ds) -> block.execute(new ConsequenceDataStoreImpl<>((RuleContext) drools, (DataStore<T>) ds))) );
    }

    @Override
    public <T> void executeOnDataStore(DataStore<T> dataStore, Block3<ConsequenceDataStore<T>, A, B> block) {
        rule.setConsequence( DSL.on(rule.asGlobal(dataStore), patternA.variable, variable).execute( (drools, ds, a, b) -> block.execute(new ConsequenceDataStoreImpl<>((RuleContext) drools, (DataStore<T>) ds), (A) a, (B) b)) );
    }

    public Pattern1DefImpl<A> getPatternA() {
        return patternA;
    }

    public Pattern1DefImpl<B> getPatternB() {
        return patternB;
    }

    @Override
    public InternalPatternDef subPatternFrom(InternalPatternDef from) {
        if (from == patternA) {
            return patternB;
        }
        throw new IllegalArgumentException();
    }
}