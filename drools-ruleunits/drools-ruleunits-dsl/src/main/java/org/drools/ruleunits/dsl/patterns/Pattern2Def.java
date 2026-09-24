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

import java.util.concurrent.TimeUnit;

import org.drools.model.Index;
import org.drools.model.functions.Block1;
import org.drools.model.functions.Block2;
import org.drools.model.functions.Block3;
import org.drools.model.functions.Function1;
import org.drools.model.functions.Predicate2;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.dsl.RuleFactory;
import org.drools.ruleunits.impl.datasources.ConsequenceDataStore;

public interface Pattern2Def<A, B> extends PatternDef {

    Pattern2Def<A, B> filter(Predicate2<A, B> predicate);

    Pattern2Def<A, B> filter(Index.ConstraintType constraintType, Function1<A, B> rightExtractor);

    <V> Pattern2Def<A, B> filter(Function1<B, V> leftExtractor, Index.ConstraintType constraintType, Function1<A, V> rightExtractor);

    <V> Pattern2Def<A, B> filter(String fieldName, Function1<B, V> leftExtractor, Index.ConstraintType constraintType, Function1<A, V> rightExtractor);

    /**
     * Constrains pattern B to occur after pattern A.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> after();

    /**
     * Constrains pattern B to occur after pattern A with at least {@code min} interval.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> after(long min, TimeUnit unit);

    /**
     * Constrains pattern B to occur after pattern A within inclusive bounds {@code [min, max]}
     * converted to the given time unit. The gap is measured from {@code end(A)} to {@code start(B)}.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     * Supported time units: MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS.
     */
    Pattern2Def<A, B> after(long min, long max, TimeUnit unit);

    /**
     * Constrains pattern B to occur before pattern A.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> before();

    /**
     * Constrains pattern B to occur before pattern A with at least {@code min} interval.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> before(long min, TimeUnit unit);

    /**
     * Constrains pattern B to occur before pattern A within inclusive bounds {@code [min, max]}
     * converted to the given time unit. The gap is measured from {@code end(B)} to {@code start(A)}.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     * Supported time units: MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS.
     */
    Pattern2Def<A, B> before(long min, long max, TimeUnit unit);

    /**
     * Constrains pattern B and pattern A to start and end at the same time.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> coincides();

    /**
     * Constrains pattern B and pattern A to start and end at the same time within allowed deviation {@code dev}.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> coincides(long dev, TimeUnit devUnit);

    /**
     * Constrains pattern B and pattern A to start and end at the same time within allowed start and end deviations.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> coincides(long startDev, TimeUnit startDevUnit, long endDev, TimeUnit endDevUnit);

    /**
     * Constrains pattern B to occur during pattern A.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> during();

    /**
     * Constrains pattern B to occur during pattern A with max distance.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> during(long max, TimeUnit maxUnit);

    /**
     * Constrains pattern B to occur during pattern A within [min, max] distance.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> during(long min, TimeUnit minUnit, long max, TimeUnit maxUnit);

    /**
     * Constrains pattern B to include pattern A.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> includes();

    /**
     * Constrains pattern B to include pattern A with max distance.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> includes(long max, TimeUnit maxUnit);

    /**
     * Constrains pattern B to include pattern A within [min, max] distance.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> includes(long min, TimeUnit minUnit, long max, TimeUnit maxUnit);

    /**
     * Constrains pattern B to overlap pattern A.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> overlaps();

    /**
     * Constrains pattern B to overlap pattern A with max deviation.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> overlaps(long maxDev, TimeUnit maxDevTimeUnit);

    /**
     * Constrains pattern B to overlap pattern A within [minDev, maxDev].
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> overlaps(long minDev, TimeUnit minDevTimeUnit, long maxDev, TimeUnit maxDevTimeUnit);

    /**
     * Constrains pattern B to be overlapped by pattern A.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> overlappedby();

    /**
     * Constrains pattern B to be overlapped by pattern A with max deviation.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> overlappedby(long maxDev, TimeUnit maxDevTimeUnit);

    /**
     * Constrains pattern B to be overlapped by pattern A within [minDev, maxDev].
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> overlappedby(long minDev, TimeUnit minDevTimeUnit, long maxDev, TimeUnit maxDevTimeUnit);

    /**
     * Constrains pattern B to meet pattern A.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> meets();

    /**
     * Constrains pattern B to meet pattern A with allowed deviation.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> meets(long dev, TimeUnit devUnit);

    /**
     * Constrains pattern B to be met by pattern A.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> metby();

    /**
     * Constrains pattern B to be met by pattern A with allowed deviation.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> metby(long dev, TimeUnit devUnit);

    /**
     * Constrains pattern B to start pattern A.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> starts();

    /**
     * Constrains pattern B to start pattern A with allowed deviation.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> starts(long dev, TimeUnit devUnit);

    /**
     * Constrains pattern B to be started by pattern A.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> startedby();

    /**
     * Constrains pattern B to be started by pattern A with allowed deviation.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> startedby(long dev, TimeUnit devUnit);

    /**
     * Constrains pattern B to finish pattern A.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> finishes();

    /**
     * Constrains pattern B to finish pattern A with allowed deviation.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> finishes(long dev, TimeUnit devUnit);

    /**
     * Constrains pattern B to be finished by pattern A.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> finishedby();

    /**
     * Constrains pattern B to be finished by pattern A with allowed deviation.
     * Both operands must be events annotated with {@code @Role(Role.Type.EVENT)}.
     */
    Pattern2Def<A, B> finishedby(long dev, TimeUnit devUnit);

    <C> Pattern3Def<A, B, C> on(DataSource<C> dataSource);

    <C> Pattern3Def<A, B, C> join(Function1<RuleFactory, Pattern1Def<C>> patternBuilder);

    Pattern2DefImpl<A, B> exists(Function1<Pattern2Def<A, B>, PatternDef> patternBuilder);

    Pattern2DefImpl<A, B> not(Function1<Pattern2Def<A, B>, PatternDef> patternBuilder);

    void execute(Block2<A, B> block);

    <G> void execute(G globalObject, Block3<G, A, B> block);

    <T> void executeOnDataStore(DataStore<T> dataStore, Block1<ConsequenceDataStore<T>> block);

    <T> void executeOnDataStore(DataStore<T> dataStore, Block3<ConsequenceDataStore<T>, A, B> block);
}
