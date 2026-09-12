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
package org.drools.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.drools.model.consequences.ConditionalConsequenceBuilder;
import org.drools.model.constraints.AndConstraints;
import org.drools.model.constraints.FixedTemporalConstraint;
import org.drools.model.constraints.OrConstraints;
import org.drools.model.constraints.ReactivitySpecs;
import org.drools.model.constraints.SingleConstraint1;
import org.drools.model.constraints.SingleConstraint10;
import org.drools.model.constraints.SingleConstraint11;
import org.drools.model.constraints.SingleConstraint12;
import org.drools.model.constraints.SingleConstraint13;
import org.drools.model.constraints.SingleConstraint14;
import org.drools.model.constraints.SingleConstraint15;
import org.drools.model.constraints.SingleConstraint16;
import org.drools.model.constraints.SingleConstraint17;
import org.drools.model.constraints.SingleConstraint18;
import org.drools.model.constraints.SingleConstraint2;
import org.drools.model.constraints.SingleConstraint3;
import org.drools.model.constraints.SingleConstraint4;
import org.drools.model.constraints.SingleConstraint5;
import org.drools.model.constraints.SingleConstraint6;
import org.drools.model.constraints.SingleConstraint7;
import org.drools.model.constraints.SingleConstraint8;
import org.drools.model.constraints.SingleConstraint9;
import org.drools.model.constraints.VariableTemporalConstraint;
import org.drools.model.functions.Function0;
import org.drools.model.functions.Function1;
import org.drools.model.functions.Function2;
import org.drools.model.functions.Function3;
import org.drools.model.functions.Function4;
import org.drools.model.functions.Predicate1;
import org.drools.model.functions.Predicate10;
import org.drools.model.functions.Predicate11;
import org.drools.model.functions.Predicate12;
import org.drools.model.functions.Predicate13;
import org.drools.model.functions.Predicate14;
import org.drools.model.functions.Predicate15;
import org.drools.model.functions.Predicate16;
import org.drools.model.functions.Predicate17;
import org.drools.model.functions.Predicate18;
import org.drools.model.functions.Predicate2;
import org.drools.model.functions.Predicate3;
import org.drools.model.functions.Predicate4;
import org.drools.model.functions.Predicate5;
import org.drools.model.functions.Predicate6;
import org.drools.model.functions.Predicate7;
import org.drools.model.functions.Predicate8;
import org.drools.model.functions.Predicate9;
import org.drools.model.functions.temporal.TemporalPredicate;
import org.drools.model.impl.DeclarationImpl;
import org.drools.model.impl.Exchange;
import org.drools.model.impl.Query0DefImpl;
import org.drools.model.impl.Query10DefImpl;
import org.drools.model.impl.Query1DefImpl;
import org.drools.model.impl.Query2DefImpl;
import org.drools.model.impl.Query3DefImpl;
import org.drools.model.impl.Query4DefImpl;
import org.drools.model.impl.Query5DefImpl;
import org.drools.model.impl.Query6DefImpl;
import org.drools.model.impl.Query7DefImpl;
import org.drools.model.impl.Query8DefImpl;
import org.drools.model.impl.Query9DefImpl;
import org.drools.model.impl.RuleBuilder;
import org.drools.model.impl.ViewBuilder;
import org.drools.model.index.AlphaIndexImpl;
import org.drools.model.index.BetaIndex2Impl;
import org.drools.model.index.BetaIndex3Impl;
import org.drools.model.index.BetaIndex4Impl;
import org.drools.model.index.BetaIndexImpl;
import org.drools.model.view.BindViewItem1;
import org.drools.model.view.BindViewItem2;
import org.drools.model.view.BindViewItem3;
import org.drools.model.view.BindViewItem4;
import org.drools.model.view.CombinedExprViewItem;
import org.drools.model.view.ExprViewItem;
import org.drools.model.view.ViewItem;
import org.drools.model.view.ViewItemBuilder;


import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class PatternDSL extends DSL {

    private static final ViewBuilder VIEW_BUILDER = ViewBuilder.PATTERN;

    public static <T> PatternDef<T> pattern( Variable<T> var ) {
        return new PatternDefImpl<>( var );
    }

    public static <T> PatternDef<T> pattern(Variable<T> var, DeclarationSource source) {
        (( DeclarationImpl<T> ) var).setSource( source );
        return new PatternDefImpl<>( var );
    }

    public static <T, U> AlphaIndex<T, U> alphaIndexedBy( Class<U> indexedClass, Index.ConstraintType constraintType, int indexId, Function1<T, U> leftOperandExtractor, U rightValue ) {
        return new AlphaIndexImpl<>( indexedClass, constraintType, indexId, leftOperandExtractor, rightValue );
    }

    public static <T, U, V> BetaIndex<T, U, V> betaIndexedBy( Class<V> indexedClass, Index.ConstraintType constraintType, int indexId, Function1<T, V> leftOperandExtractor, Function1<U, ?> rightOperandExtractor ) {
        return new BetaIndexImpl<>( indexedClass, constraintType, indexId, leftOperandExtractor, rightOperandExtractor );
    }

    public static <T, U, V> BetaIndex<T, U, V> betaIndexedBy( Class<V> indexedClass, Index.ConstraintType constraintType, int indexId, Function1<T, V> leftOperandExtractor, Function1<U, ?> rightOperandExtractor, Class<?> rightReturnType ) {
        return new BetaIndexImpl<>( indexedClass, constraintType, indexId, leftOperandExtractor, rightOperandExtractor, rightReturnType );
    }

    public static <T, A, B, V> BetaIndex2<T, A, B, V> betaIndexedBy( Class<V> indexedClass, Index.ConstraintType constraintType, int indexId, Function1<T, V> leftOperandExtractor, Function2<A, B, ?> rightOperandExtractor, Class<?> rightReturnType ) {
        return new BetaIndex2Impl<>( indexedClass, constraintType, indexId, leftOperandExtractor, rightOperandExtractor, rightReturnType );
    }

    public static <T, A, B, C, V> BetaIndex3<T, A, B, C, V> betaIndexedBy( Class<V> indexedClass, Index.ConstraintType constraintType, int indexId, Function1<T, V> leftOperandExtractor, Function3<A, B, C, ?> rightOperandExtractor, Class<?> rightReturnType ) {
        return new BetaIndex3Impl<>( indexedClass, constraintType, indexId, leftOperandExtractor, rightOperandExtractor, rightReturnType );
    }

    public static <T, A, B, C, D, V> BetaIndex4<T, A, B, C, D, V> betaIndexedBy( Class<V> indexedClass, Index.ConstraintType constraintType, int indexId, Function1<T, V> leftOperandExtractor, Function4<A, B, C, D, ?> rightOperandExtractor, Class<?> rightReturnType ) {
        return new BetaIndex4Impl<>( indexedClass, constraintType, indexId, leftOperandExtractor, rightOperandExtractor, rightReturnType );
    }

    public static ReactOn reactOn( String... reactOn ) {
        return new ReactOn( reactOn );
    }

    public interface PatternDef<T> extends ViewItem<T>, SequenceStep {
        PatternDef<T> and();
        PatternDef<T> or();
        PatternDef<T> endAnd();
        PatternDef<T> endOr();

        PatternDef<T> expr( Predicate1<T> predicate );

        PatternDef<T> expr( String exprId, Predicate1<T> predicate );

        PatternDef<T> expr( String exprId, Predicate1<T> predicate, AlphaIndex<T, ?> index );

        PatternDef<T> expr( String exprId, Predicate1<T> predicate, ReactOn reactOn );

        PatternDef<T> expr( String exprId, Predicate1<T> predicate, AlphaIndex<T, ?> index, ReactOn reactOn );

        <U> PatternDef<T> expr( Variable<U> var2, Predicate2<T, U> predicate );

        <U> PatternDef<T> expr( String exprId, Variable<U> var2, Predicate2<T, U> predicate );

        <U> PatternDef<T> expr( String exprId, Variable<U> var2, Predicate2<T, U> predicate, BetaIndex<T, U, ?> index );

        <U> PatternDef<T> expr( String exprId, Variable<U> var2, Predicate2<T, U> predicate, ReactOn reactOn );

        <U> PatternDef<T> expr( String exprId, Variable<U> var2, Predicate2<T, U> predicate, BetaIndex<T, U, ?> index, ReactOn reactOn );

        <A, B> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Predicate3<T, A, B> predicate );
        <A, B> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Predicate3<T, A, B> predicate, BetaIndex2<T, A, B, ?> index );
        <A, B> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Predicate3<T, A, B> predicate, ReactOn reactOn );
        <A, B> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Predicate3<T, A, B> predicate, BetaIndex2<T, A, B, ?> index, ReactOn reactOn );

        <A, B, C> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Predicate4<T, A, B, C> predicate );
        <A, B, C> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Predicate4<T, A, B, C> predicate, BetaIndex3<T, A, B, C, ?> index );
        <A, B, C> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Predicate4<T, A, B, C> predicate, ReactOn reactOn );
        <A, B, C> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Predicate4<T, A, B, C> predicate, BetaIndex3<T, A, B, C, ?> index, ReactOn reactOn );

        <A, B, C, D> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Predicate5<T, A, B, C, D> predicate );
        <A, B, C, D> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Predicate5<T, A, B, C, D> predicate, BetaIndex4<T, A, B, C, D, ?> index );
        <A, B, C, D> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Predicate5<T, A, B, C, D> predicate, ReactOn reactOn );
        <A, B, C, D> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Predicate5<T, A, B, C, D> predicate, BetaIndex4<T, A, B, C, D, ?> index, ReactOn reactOn );

        <A, B, C, D, E> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Predicate6<T, A, B, C, D, E> predicate );
        <A, B, C, D, E> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Predicate6<T, A, B, C, D, E> predicate, ReactOn reactOn );

        <A, B, C, D, E, F> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7, Predicate7<T, A, B, C, D, E, F> predicate );
        <A, B, C, D, E, F> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7, Predicate7<T, A, B, C, D, E, F> predicate, ReactOn reactOn );

        <A, B, C, D, E, F, G> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                 Variable<G> var8,
                                                 Predicate8<T, A, B, C, D, E, F, G> predicate );

        <A, B, C, D, E, F, G> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                 Variable<G> var8,
                                                 Predicate8<T, A, B, C, D, E, F, G> predicate, ReactOn reactOn );

        <A, B, C, D, E, F, G, H> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                    Variable<G> var8, Variable<H> var9,
                                                    Predicate9<T, A, B, C, D, E, F, G, H> predicate );

        <A, B, C, D, E, F, G, H> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                    Variable<G> var8, Variable<H> var9,
                                                    Predicate9<T, A, B, C, D, E, F, G, H> predicate, ReactOn reactOn );

        <A, B, C, D, E, F, G, H, I> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                       Variable<G> var8, Variable<H> var9, Variable<I> var10,
                                                       Predicate10<T, A, B, C, D, E, F, G, H, I> predicate );

        <A, B, C, D, E, F, G, H, I> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                       Variable<G> var8, Variable<H> var9, Variable<I> var10,
                                                       Predicate10<T, A, B, C, D, E, F, G, H, I> predicate, ReactOn reactOn );

        <A, B, C, D, E, F, G, H, I, J> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                          Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11,
                                                          Predicate11<T, A, B, C, D, E, F, G, H, I, J> predicate );

        <A, B, C, D, E, F, G, H, I, J> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                          Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11,
                                                          Predicate11<T, A, B, C, D, E, F, G, H, I, J> predicate, ReactOn reactOn );

        <A, B, C, D, E, F, G, H, I, J, K> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12,
                                                             Predicate12<T, A, B, C, D, E, F, G, H, I, J, K> predicate );

        <A, B, C, D, E, F, G, H, I, J, K> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12,
                                                             Predicate12<T, A, B, C, D, E, F, G, H, I, J, K> predicate, ReactOn reactOn );

        <A, B, C, D, E, F, G, H, I, J, K, L> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                                                                Predicate13<T, A, B, C, D, E, F, G, H, I, J, K, L> predicate );

        <A, B, C, D, E, F, G, H, I, J, K, L> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                                                                Predicate13<T, A, B, C, D, E, F, G, H, I, J, K, L> predicate, ReactOn reactOn );
        <A, B, C, D, E, F, G, H, I, J, K, L, M> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                   Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                                                                   Variable<M> var14, Predicate14<T, A, B, C, D, E, F, G, H, I, J, K, L, M> predicate );

        <A, B, C, D, E, F, G, H, I, J, K, L, M> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                   Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                                                                   Variable<M> var14, Predicate14<T, A, B, C, D, E, F, G, H, I, J, K, L, M> predicate, ReactOn reactOn );

        <A, B, C, D, E, F, G, H, I, J, K, L, M, N> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                      Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                                                                      Variable<M> var14, Variable<N> var15, Predicate15<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N> predicate );

        <A, B, C, D, E, F, G, H, I, J, K, L, M, N> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                      Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                                                                      Variable<M> var14, Variable<N> var15, Predicate15<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N> predicate, ReactOn reactOn );

        <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                         Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                                                                         Variable<M> var14, Variable<N> var15, Variable<O> var16, Predicate16<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> predicate );

        <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                         Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                                                                         Variable<M> var14, Variable<N> var15, Variable<O> var16, Predicate16<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> predicate, ReactOn reactOn );

        <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                            Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                                                                            Variable<M> var14, Variable<N> var15, Variable<O> var16, Variable<P> var17, Predicate17<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> predicate );

        <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                            Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                                                                            Variable<M> var14, Variable<N> var15, Variable<O> var16, Variable<P> var17, Predicate17<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> predicate, ReactOn reactOn );

        <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                               Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                                                                               Variable<M> var14, Variable<N> var15, Variable<O> var16, Variable<P> var17, Variable<Q> var18, Predicate18<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> predicate );

        <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                               Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                                                                               Variable<M> var14, Variable<N> var15, Variable<O> var16, Variable<P> var17, Variable<Q> var18, Predicate18<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> predicate, ReactOn reactOn );

        <U> PatternDef<T> expr( String exprId, Variable<U> var1, TemporalPredicate temporalPredicate );
        PatternDef<T> expr( String exprId, long value, TemporalPredicate temporalPredicate );
        PatternDef<T> expr( String exprId, Function1<T, ?> f, long value, TemporalPredicate temporalPredicate );

        <U> PatternDef<T> expr( String exprId, Variable<U> var1, Function1<U,?> fVar, TemporalPredicate temporalPredicate );
        <U> PatternDef<T> expr( String exprId, Function1<T,?> fThis, Variable<U> var1, TemporalPredicate temporalPredicate );
        PatternDef<T> expr( String exprId, Function1<T,?> fThis, Function1<T,?> fVar, TemporalPredicate temporalPredicate );
        <U> PatternDef<T> expr( String exprId, Function1<T,?> fThis, Variable<U> var1, Function1<U,?> fVar, TemporalPredicate temporalPredicate );

        <A> PatternDef<T> bind( Variable<A> boundVar, Function1<T, A> f );

        <A> PatternDef<T> bind( Variable<A> boundVar, Function1<T, A> f, ReactOn reactOn );

        <A, U> PatternDef<T> bind( Variable<A> boundVar, Variable<U> otherVar, Function1<U, A> f );

        <A, U> PatternDef<T> bind( Variable<A> boundVar, Variable<U> otherVar, Function1<U, A> f, ReactOn reactOn );

        <A, U> PatternDef<T> bind( Variable<A> boundVar, Variable<U> otherVar, Function2<T, U, A> f );

        <A, U> PatternDef<T> bind( Variable<A> boundVar, Variable<U> otherVar, Function2<T, U, A> f, ReactOn reactOn );

        <A, U, V> PatternDef<T> bind( Variable<A> boundVar, Variable<U> otherVar1, Variable<V> otherVar2, Function3<T, U, V, A> f );

        <A, U, V> PatternDef<T> bind( Variable<A> boundVar, Variable<U> otherVar1, Variable<V> otherVar2, Function3<T, U, V, A> f, ReactOn reactOn );

        <A, U, V, W> PatternDef<T> bind( Variable<A> boundVar, Variable<U> otherVar1, Variable<V> otherVar2, Variable<W> otherVar3, Function4<T, U, V, W, A> f );

        <A, U, V, W> PatternDef<T> bind( Variable<A> boundVar, Variable<U> otherVar1, Variable<V> otherVar2, Variable<W> otherVar3, Function4<T, U, V, W, A> f, ReactOn reactOn );

        PatternDef<T> watch( String... watch );

        PatternDef<T> passive();
    }

    public static class PatternDefImpl<T> implements PatternDef<T> {
        protected final Variable<T> variable;
        protected final List<PatternItem<T>> items = new ArrayList<>();

        private String[] watch;
        private boolean passive;

        public PatternDefImpl( Variable<T> variable ) {
            this.variable = variable;
        }

        @Override
        public Variable<T> getFirstVariable() {
            return variable;
        }

        public DomainClassMetadata getDomainClassMetadata() {
            return variable instanceof Declaration ? (( Declaration<T> ) variable).getMetadata() : null;
        }

        public List<PatternItem<T>> getItems() {
            return items;
        }

        public String[] getWatch() {
            return watch;
        }

        public boolean isPassive() {
            return passive;
        }

        @Override
        public Variable<?>[] getVariables() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PatternDef<T> and() {
            return new SubPatternDefImpl<>( this, LogicalCombiner.AND );
        }

        @Override
        public PatternDef<T> or() {
            return new SubPatternDefImpl<>( this, LogicalCombiner.OR );
        }

        @Override
        public PatternDef<T> endAnd() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PatternDef<T> endOr() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PatternDef<T> expr( Predicate1<T> predicate ) {
            items.add( new PatternExpr1<>( new Predicate1.Impl<>( predicate ), null, null ) );
            return this;
        }

        @Override
        public PatternDef<T> expr( String exprId, Predicate1<T> predicate ) {
            items.add( new PatternExpr1<>( exprId, new Predicate1.Impl<>( predicate ), null, null ) );
            return this;
        }

        @Override
        public PatternDef<T> expr( String exprId, Predicate1<T> predicate, AlphaIndex<T, ?> index ) {
            items.add( new PatternExpr1<>( exprId, new Predicate1.Impl<>( predicate ), index, null ) );
            return this;
        }

        @Override
        public PatternDef<T> expr( String exprId, Predicate1<T> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr1<>( exprId, new Predicate1.Impl<>( predicate ), null, reactOn ) );
            return this;
        }

        @Override
        public PatternDef<T> expr( String exprId, Predicate1<T> predicate, AlphaIndex<T, ?> index, ReactOn reactOn ) {
            items.add( new PatternExpr1<>( exprId, new Predicate1.Impl<>( predicate ), index, reactOn ) );
            return this;
        }

        @Override
        public <U> PatternDef<T> expr( Variable<U> var2, Predicate2<T, U> predicate ) {
            items.add( new PatternExpr2<>( var2, new Predicate2.Impl<>( predicate ), null, null ) );
            return this;
        }

        @Override
        public <U> PatternDef<T> expr( String exprId, Variable<U> var2, Predicate2<T, U> predicate ) {
            items.add( new PatternExpr2<>( exprId, var2, new Predicate2.Impl<>( predicate ), null, null ) );
            return this;
        }

        @Override
        public <U> PatternDef<T> expr( String exprId, Variable<U> var2, Predicate2<T, U> predicate, BetaIndex<T, U, ?> index ) {
            items.add( new PatternExpr2<>( exprId, var2, new Predicate2.Impl<>( predicate ), index, null ) );
            return this;
        }

        @Override
        public <U> PatternDef<T> expr( String exprId, Variable<U> var2, Predicate2<T, U> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr2<>( exprId, var2, new Predicate2.Impl<>( predicate ), null, reactOn ) );
            return this;
        }

        @Override
        public <U> PatternDef<T> expr( String exprId, Variable<U> var2, Predicate2<T, U> predicate, BetaIndex<T, U, ?> index, ReactOn reactOn ) {
            items.add( new PatternExpr2<>( exprId, var2, new Predicate2.Impl<>( predicate ), index, reactOn ) );
            return this;
        }

        @Override
        public <A, B> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Predicate3<T, A, B> predicate ) {
            items.add( new PatternExpr3<>( exprId, var2, var3, new Predicate3.Impl<>( predicate ), null, null ) );
            return this;
        }

        @Override
        public <A, B> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Predicate3<T, A, B> predicate, BetaIndex2<T, A, B, ?> index ) {
            items.add( new PatternExpr3<>( exprId, var2, var3, new Predicate3.Impl<>( predicate ), index, null ) );
            return this;
        }

        @Override
        public <A, B> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Predicate3<T, A, B> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr3<>( exprId, var2, var3, new Predicate3.Impl<>( predicate ), null, reactOn ) );
            return this;
        }

        @Override
        public <A, B> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Predicate3<T, A, B> predicate, BetaIndex2<T, A, B, ?> index, ReactOn reactOn ) {
            items.add( new PatternExpr3<>( exprId, var2, var3, new Predicate3.Impl<>( predicate ), index, reactOn ) );
            return this;
        }

        @Override
        public <A, B, C> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Predicate4<T, A, B, C> predicate ) {
            items.add( new PatternExpr4<>( exprId, var2, var3, var4, new Predicate4.Impl<>( predicate ), null, null ) );
            return this;
        }

        @Override
        public <A, B, C> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Predicate4<T, A, B, C> predicate, BetaIndex3<T, A, B, C, ?> index ) {
            items.add( new PatternExpr4<>( exprId, var2, var3, var4, new Predicate4.Impl<>( predicate ), index, null ) );
            return this;
        }

        @Override
        public <A, B, C> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Predicate4<T, A, B, C> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr4<>( exprId, var2, var3, var4, new Predicate4.Impl<>( predicate ), null, reactOn ) );
            return this;
        }

        @Override
        public <A, B, C> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Predicate4<T, A, B, C> predicate, BetaIndex3<T, A, B, C, ?> index, ReactOn reactOn ) {
            items.add( new PatternExpr4<>( exprId, var2, var3, var4, new Predicate4.Impl<>( predicate ), index, reactOn ) );
            return this;
        }

        @Override
        public <A, B, C, D> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Predicate5<T, A, B, C, D> predicate ) {
            items.add( new PatternExpr5<>( exprId, var2, var3, var4, var5, new Predicate5.Impl<>( predicate ), null, null ) );
            return this;
        }

        @Override
        public <A, B, C, D> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Predicate5<T, A, B, C, D> predicate, BetaIndex4<T, A, B, C, D, ?> index ) {
            items.add( new PatternExpr5<>( exprId, var2, var3, var4, var5, new Predicate5.Impl<>( predicate ), index, null ) );
            return this;
        }

        @Override
        public <A, B, C, D> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Predicate5<T, A, B, C, D> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr5<>( exprId, var2, var3, var4, var5, new Predicate5.Impl<>( predicate ), null, reactOn ) );
            return this;
        }

        @Override
        public <A, B, C, D> PatternDef<T> expr( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Predicate5<T, A, B, C, D> predicate, BetaIndex4<T, A, B, C, D, ?> index, ReactOn reactOn ) {
            items.add( new PatternExpr5<>( exprId, var2, var3, var4, var5, new Predicate5.Impl<>( predicate ), index, reactOn ) );
            return this;
        }

        @Override
        public <A, B, C, D, E> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Predicate6<T, A, B, C, D, E> predicate) {
            items.add( new PatternExpr6<>( exprId, var2, var3, var4, var5, var6, new Predicate6.Impl<>(predicate), null ) );
            return this;
        }

        @Override
        public <A, B, C, D, E> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Predicate6<T, A, B, C, D, E> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr6<>( exprId, var2, var3, var4, var5, var6, new Predicate6.Impl<>(predicate), reactOn ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7, Predicate7<T, A, B, C, D, E, F> predicate) {
            items.add( new PatternExpr7<>( exprId, var2, var3, var4, var5, var6, var7, new Predicate7.Impl<>(predicate), null ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7, Predicate7<T, A, B, C, D, E, F> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr7<>( exprId, var2, var3, var4, var5, var6, var7, new Predicate7.Impl<>(predicate), reactOn ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                        Variable<G> var8,
                                                        Predicate8<T, A, B, C, D, E, F, G> predicate ) {
            items.add( new PatternExpr8<>( exprId, var2, var3, var4, var5, var6, var7, var8, new Predicate8.Impl<>(predicate), null ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                        Variable<G> var8,
                                                        Predicate8<T, A, B, C, D, E, F, G> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr8<>( exprId, var2, var3, var4, var5, var6, var7, var8, new Predicate8.Impl<>(predicate), reactOn ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                           Variable<G> var8, Variable<H> var9,
                                                           Predicate9<T, A, B, C, D, E, F, G, H> predicate ) {
            items.add( new PatternExpr9<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, new Predicate9.Impl<>(predicate), null ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                           Variable<G> var8, Variable<H> var9,
                                                           Predicate9<T, A, B, C, D, E, F, G, H> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr9<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, new Predicate9.Impl<>(predicate), reactOn ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                              Variable<G> var8, Variable<H> var9, Variable<I> var10,
                                                              Predicate10<T, A, B, C, D, E, F, G, H, I> predicate ) {
            items.add( new PatternExpr10<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, new Predicate10.Impl<>(predicate), null ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                              Variable<G> var8, Variable<H> var9, Variable<I> var10,
                                                              Predicate10<T, A, B, C, D, E, F, G, H, I> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr10<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, new Predicate10.Impl<>(predicate), reactOn ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I, J> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                 Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11,
                                                                 Predicate11<T, A, B, C, D, E, F, G, H, I, J> predicate ) {
            items.add( new PatternExpr11<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, new Predicate11.Impl<>(predicate), null ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I, J> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                 Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11,
                                                                 Predicate11<T, A, B, C, D, E, F, G, H, I, J> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr11<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, new Predicate11.Impl<>(predicate), reactOn ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I, J, K> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                    Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12,
                                                                    Predicate12<T, A, B, C, D, E, F, G, H, I, J, K> predicate ) {
            items.add( new PatternExpr12<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, new Predicate12.Impl<>(predicate), null ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I, J, K> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                    Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12,
                                                                    Predicate12<T, A, B, C, D, E, F, G, H, I, J, K> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr12<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, new Predicate12.Impl<>(predicate), reactOn ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I, J, K, L> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                       Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                                                                       Predicate13<T, A, B, C, D, E, F, G, H, I, J, K, L> predicate ) {
            items.add( new PatternExpr13<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, new Predicate13.Impl<>(predicate), null ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I, J, K, L> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                       Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                                                                       Predicate13<T, A, B, C, D, E, F, G, H, I, J, K, L> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr13<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, new Predicate13.Impl<>(predicate), reactOn ) );
            return this;
        }
        @Override
        public <A, B, C, D, E, F, G, H, I, J, K, L, M> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                          Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13, Variable<M> var14,
                                                                          Predicate14<T, A, B, C, D, E, F, G, H, I, J, K, L, M> predicate ) {
            items.add( new PatternExpr14<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, new Predicate14.Impl<>(predicate), null ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I, J, K, L, M> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                          Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13, Variable<M> var14,
                                                                          Predicate14<T, A, B, C, D, E, F, G, H, I, J, K, L, M> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr14<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, new Predicate14.Impl<>(predicate), reactOn ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I, J, K, L, M, N> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13, Variable<M> var14, Variable<N> var15,
                                                                             Predicate15<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N> predicate ) {
            items.add( new PatternExpr15<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, new Predicate15.Impl<>(predicate), null ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I, J, K, L, M, N> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13, Variable<M> var14, Variable<N> var15,
                                                                             Predicate15<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr15<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, new Predicate15.Impl<>(predicate), reactOn ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                                Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13, Variable<M> var14, Variable<N> var15, Variable<O> var16,
                                                                                Predicate16<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> predicate ) {
            items.add( new PatternExpr16<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, new Predicate16.Impl<>(predicate), null ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                                Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13, Variable<M> var14, Variable<N> var15, Variable<O> var16,
                                                                                Predicate16<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr16<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, new Predicate16.Impl<>(predicate), reactOn ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                                   Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13, Variable<M> var14, Variable<N> var15, Variable<O> var16, Variable<P> var17,
                                                                                   Predicate17<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> predicate ) {
            items.add( new PatternExpr17<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, var17, new Predicate17.Impl<>(predicate), null ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                                   Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13, Variable<M> var14, Variable<N> var15, Variable<O> var16, Variable<P> var17,
                                                                                   Predicate17<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr17<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, var17, new Predicate17.Impl<>(predicate), reactOn ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                                      Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13, Variable<M> var14, Variable<N> var15, Variable<O> var16, Variable<P> var17, Variable<Q> var18,
                                                                                      Predicate18<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> predicate ) {
            items.add( new PatternExpr18<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, var17, var18, new Predicate18.Impl<>(predicate), null ) );
            return this;
        }

        @Override
        public <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> PatternDef<T> expr(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                                                                                      Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13, Variable<M> var14, Variable<N> var15, Variable<O> var16, Variable<P> var17, Variable<Q> var18,
                                                                                      Predicate18<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> predicate, ReactOn reactOn ) {
            items.add( new PatternExpr18<>( exprId, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, var17, var18, new Predicate18.Impl<>(predicate), reactOn ) );
            return this;
        }


        @Override
        public <U> PatternDef<T> expr( String exprId, Variable<U> var1, TemporalPredicate temporalPredicate ) {
            items.add( new TemporalPatternExpr<>( exprId, null, var1, null, temporalPredicate) );
            return this;
        }

        @Override
        public PatternDef<T> expr( String exprId, long value, TemporalPredicate temporalPredicate ) {
            items.add( new FixedTemporalPatternExpr<>( exprId, null, value, temporalPredicate) );
            return this;
        }

        @Override
        public PatternDef<T> expr( String exprId, Function1<T, ?> f, long value, TemporalPredicate temporalPredicate ) {
            items.add( new FixedTemporalPatternExpr<>( exprId, new Function1.Impl<>( f ), value, temporalPredicate) );
            return this;
        }

        @Override
        public <U> PatternDef<T> expr( String exprId, Variable<U> var1, Function1<U, ?> fVar, TemporalPredicate temporalPredicate ) {
            items.add( new TemporalPatternExpr<>( exprId, null, var1, new Function1.Impl<>( fVar ), temporalPredicate) );
            return this;
        }

        @Override
        public <U> PatternDef<T> expr( String exprId, Function1<T, ?> fThis, Variable<U> var1, TemporalPredicate temporalPredicate ) {
            items.add( new TemporalPatternExpr<>( exprId, new Function1.Impl<>( fThis ), var1, null, temporalPredicate) );
            return this;
        }

        @Override
        public PatternDef<T> expr( String exprId, Function1<T, ?> fThis, Function1<T, ?> fVar, TemporalPredicate temporalPredicate ) {
            items.add( new TemporalPatternExpr<>( exprId, new Function1.Impl<>( fThis ), getFirstVariable(), new Function1.Impl<>( fVar ), temporalPredicate) );
            return this;
        }

        @Override
        public <U> PatternDef<T> expr( String exprId, Function1<T, ?> fThis, Variable<U> var1, Function1<U, ?> fVar, TemporalPredicate temporalPredicate ) {
            items.add( new TemporalPatternExpr<>( exprId, new Function1.Impl<>( fThis ), var1, new Function1.Impl<>( fVar ), temporalPredicate) );
            return this;
        }

        @Override
        public <A> PatternDef<T> bind( Variable<A> boundVar, Function1<T, A> f ) {
            items.add( new PatternBinding1<>( boundVar, f, null ) );
            return this;
        }

        @Override
        public <A> PatternDef<T> bind( Variable<A> boundVar, Function1<T, A> f, ReactOn reactOn ) {
            items.add( new PatternBinding1<>( boundVar, f, reactOn ) );
            return this;
        }

        @Override
        public <A, U> PatternDef<T> bind( Variable<A> boundVar, Variable<U> otherVar, Function1<U, A> f ) {
            items.add( new PatternBinding2<>( boundVar, otherVar, ( t, u ) -> f.apply( u ), null ) );
            return this;
        }

        @Override
        public <A, U> PatternDef<T> bind( Variable<A> boundVar, Variable<U> otherVar, Function1<U, A> f, ReactOn reactOn ) {
            items.add( new PatternBinding2<>( boundVar, otherVar, ( t, u ) -> f.apply( u ), reactOn ) );
            return this;
        }

        @Override
        public <A, U> PatternDef<T> bind( Variable<A> boundVar, Variable<U> otherVar, Function2<T, U, A> f ) {
            items.add( new PatternBinding2<>( boundVar, otherVar, f, null ) );
            return this;
        }

        @Override
        public <A, U> PatternDef<T> bind( Variable<A> boundVar, Variable<U> otherVar, Function2<T, U, A> f, ReactOn reactOn ) {
            items.add( new PatternBinding2<>( boundVar, otherVar, f, reactOn ) );
            return this;
        }

        @Override
        public <A, U, V> PatternDef<T> bind(Variable<A> boundVar, Variable<U> otherVar1, Variable<V> otherVar2, Function3<T, U, V, A> f) {
            items.add( new PatternBinding3<>( boundVar, otherVar1, otherVar2, f, null) );
            return this;
        }

        @Override
        public <A, U, V> PatternDef<T> bind(Variable<A> boundVar, Variable<U> otherVar1, Variable<V> otherVar2, Function3<T, U, V, A> f, ReactOn reactOn) {
            items.add( new PatternBinding3<>( boundVar, otherVar1, otherVar2, f, reactOn) );
            return this;
        }

        @Override
        public <A, U, V, W> PatternDef<T> bind(Variable<A> boundVar, Variable<U> otherVar1, Variable<V> otherVar2, Variable<W> otherVar3, Function4<T, U, V, W, A> f) {
            items.add( new PatternBinding4<>( boundVar, otherVar1, otherVar2, otherVar3, f, null) );
            return this;
        }

        @Override
        public <A, U, V, W> PatternDef<T> bind(Variable<A> boundVar, Variable<U> otherVar1, Variable<V> otherVar2, Variable<W> otherVar3, Function4<T, U, V, W, A> f, ReactOn reactOn) {
            items.add( new PatternBinding4<>( boundVar, otherVar1, otherVar2, otherVar3, f, reactOn) );
            return this;
        }

        @Override
        public PatternDef<T> watch( String... watch ) {
            this.watch = watch;
            return this;
        }

        @Override
        public PatternDef<T> passive() {
            this.passive = true;
            return this;
        }
    }

    public enum LogicalCombiner {
        AND( "&&" ), OR( "||" );

        private final String symbol;

        LogicalCombiner( String symbol ) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    public static class SubPatternDefImpl<T> extends PatternDefImpl<T> {
        private final PatternDefImpl<T> parent;
        private final LogicalCombiner combiner;

        public SubPatternDefImpl( PatternDefImpl<T> parent, LogicalCombiner combiner ) {
            super(parent.variable);
            this.parent = parent;
            this.combiner = combiner;
        }

        @Override
        public PatternDef<T> endAnd() {
            if (combiner == LogicalCombiner.OR) {
                throw new UnsupportedOperationException();
            }
            parent.items.add( new CombinedPatternExprItem<>( combiner, this.getItems() ));
            return parent;
        }

        @Override
        public PatternDef<T> endOr() {
            if (combiner == LogicalCombiner.AND) {
                throw new UnsupportedOperationException();
            }
            parent.items.add( new CombinedPatternExprItem<>( combiner, this.getItems() ));
            return parent;
        }
    }

    public interface PatternItem<T> { }

    public static abstract class PatternExprImpl<T> implements PatternItem<T> {
        private final String exprId;
        private final ReactOn reactOn;

        public PatternExprImpl( String exprId, ReactOn reactOn ) {
            this.exprId = exprId;
            this.reactOn = reactOn;
        }

        public String getExprId() {
            return exprId;
        }

        private String[] getReactOn() {
            return reactOn != null ? reactOn.getStrings() : null;
        }

        public ReactivitySpecs getReactivitySpecs(DomainClassMetadata metadata) {
            return reactOn != null ? new ReactivitySpecs( metadata, reactOn.getStrings() ) : ReactivitySpecs.EMPTY;
        }

        public abstract Constraint asConstraint( PatternDefImpl patternDef );
    }

    public static class CombinedPatternExprItem<T> extends PatternExprImpl<T> {
        private final LogicalCombiner combiner;
        private final List<PatternItem<T>> items;

        public CombinedPatternExprItem( LogicalCombiner combiner, List<PatternItem<T>> items ) {
            super(null, null);
            this.combiner = combiner;
            this.items = items;
        }

        @Override
        public String getExprId() {
            return items.stream().map( PatternExprImpl.class::cast ).map( PatternExprImpl::getExprId ).collect( joining( combiner.getSymbol() ) );
        }

        @Override
        public ReactivitySpecs getReactivitySpecs(DomainClassMetadata metadata) {
            String[] strings = items.stream().map( PatternExprImpl.class::cast ).flatMap( e -> Stream.of( e.getReactOn() ) ).toArray( String[]::new );
            return new ReactivitySpecs(metadata, strings);
        }

        @Override
        public Constraint asConstraint( PatternDefImpl patternDef ) {
            List<Constraint> combined = items.stream().map( PatternExprImpl.class::cast ).map( e -> e.asConstraint( patternDef ) ).collect( toList() );
            return combiner == LogicalCombiner.OR ? new OrConstraints( combined ) : new AndConstraints( combined );
        }
    }

    public static class PatternExpr1<T> extends PatternExprImpl<T> {

        private final Predicate1<T> predicate;

        private final AlphaIndex<T, ?> index;

        public PatternExpr1( Predicate1<T> predicate, AlphaIndex<T, ?> index, ReactOn reactOn ) {
            this( randomUUID().toString(), predicate, index, reactOn );
        }

        public PatternExpr1( String exprId, Predicate1<T> predicate, AlphaIndex<T, ?> index, ReactOn reactOn ) {
            super( exprId, reactOn );
            this.predicate = predicate;
            this.index = index;
        }

        public Predicate1<T> getPredicate() {
            return predicate;
        }

        public AlphaIndex<T, ?> getIndex() {
            return index;
        }

        @Override
        public Constraint asConstraint( PatternDefImpl patternDef ) {
            SingleConstraint1 constraint = new SingleConstraint1( getExprId(), patternDef.getFirstVariable(), getPredicate() );
            constraint.setIndex( getIndex() );
            constraint.setReactivitySpecs( getReactivitySpecs( patternDef.getDomainClassMetadata() ) );
            return constraint;
        }
    }

    public static class PatternExpr2<T, U> extends PatternExprImpl<T> {
        private final Variable<U> var2;
        private final Predicate2<T, U> predicate;

        private final BetaIndex<T, U, ?> index;

        public PatternExpr2( Variable<U> var2, Predicate2<T, U> predicate, BetaIndexImpl<T, U, ?> index, ReactOn reactOn ) {
            this( randomUUID().toString(), var2, predicate, index, reactOn );
        }

        public PatternExpr2( String exprId, Variable<U> var2, Predicate2<T, U> predicate, BetaIndex<T, U, ?> index, ReactOn reactOn ) {
            super( exprId, reactOn );
            this.var2 = var2;
            this.predicate = predicate;
            this.index = index;
        }

        public Predicate2<T, U> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint( PatternDefImpl patternDef ) {
            SingleConstraint2 constraint = new SingleConstraint2( getExprId(), patternDef.getFirstVariable(), var2, getPredicate() );
            constraint.setIndex( index );
            constraint.setReactivitySpecs( getReactivitySpecs( patternDef.getDomainClassMetadata() ) );
            return constraint;
        }
    }

    public static class PatternExpr3<T, A, B> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Predicate3<T, A, B> predicate;

        private final BetaIndex2<T, A, B, ?> index;

        public PatternExpr3( Variable<A> var2, Variable<B> var3, Predicate3<T, A, B> predicate, BetaIndex2<T, A, B, ?> index, ReactOn reactOn ) {
            this( randomUUID().toString(), var2, var3, predicate, index, reactOn );
        }

        public PatternExpr3( String exprId, Variable<A> var2, Variable<B> var3, Predicate3<T, A, B> predicate, BetaIndex2<T, A, B, ?> index, ReactOn reactOn ) {
            super( exprId, reactOn );
            this.var2 = var2;
            this.var3 = var3;
            this.predicate = predicate;
            this.index = index;
        }

        public Predicate3<T, A, B> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint( PatternDefImpl patternDef ) {
            SingleConstraint3 constraint = new SingleConstraint3( getExprId(), patternDef.getFirstVariable(), var2, var3, getPredicate() );
            constraint.setIndex( index );
            constraint.setReactivitySpecs( getReactivitySpecs( patternDef.getDomainClassMetadata() ) );
            return constraint;
        }
    }

    public static class PatternExpr4<T, A, B, C> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Variable<C> var4;
        private final Predicate4<T, A, B, C> predicate;

        private final BetaIndex3<T, A, B, C, ?> index;

        public PatternExpr4( Variable<A> var2, Variable<B> var3, Variable<C> var4, Predicate4<T, A, B, C> predicate, BetaIndex3<T, A, B, C, ?> index, ReactOn reactOn ) {
            this( randomUUID().toString(), var2, var3, var4, predicate, index, reactOn );
        }

        public PatternExpr4( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Predicate4<T, A, B, C> predicate, BetaIndex3<T, A, B, C, ?> index, ReactOn reactOn ) {
            super( exprId, reactOn );
            this.var2 = var2;
            this.var3 = var3;
            this.var4 = var4;
            this.predicate = predicate;
            this.index = index;
        }

        public Predicate4<T, A, B, C> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint( PatternDefImpl patternDef ) {
            SingleConstraint4 constraint = new SingleConstraint4( getExprId(), patternDef.getFirstVariable(), var2, var3, var4, getPredicate() );
            constraint.setIndex( index );
            constraint.setReactivitySpecs( getReactivitySpecs( patternDef.getDomainClassMetadata() ) );
            return constraint;
        }
    }

    public static class PatternExpr5<T, A, B, C, D> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Variable<C> var4;
        private final Variable<D> var5;
        private final Predicate5<T, A, B, C, D> predicate;

        private final BetaIndex4<T, A, B, C, D, ?> index;

        public PatternExpr5( Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Predicate5<T, A, B, C, D> predicate, BetaIndex4<T, A, B, C, D, ?> index, ReactOn reactOn ) {
            this( randomUUID().toString(), var2, var3, var4, var5, predicate, index, reactOn );
        }

        public PatternExpr5( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Predicate5<T, A, B, C, D> predicate, BetaIndex4<T, A, B, C, D, ?> index, ReactOn reactOn ) {
            super( exprId, reactOn );
            this.var2 = var2;
            this.var3 = var3;
            this.var4 = var4;
            this.var5 = var5;
            this.predicate = predicate;
            this.index = index;
        }

        public Predicate5<T, A, B, C, D> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint( PatternDefImpl patternDef ) {
            SingleConstraint5 constraint = new SingleConstraint5( getExprId(), patternDef.getFirstVariable(), var2, var3, var4, var5, getPredicate() );
            constraint.setIndex( index );
            constraint.setReactivitySpecs( getReactivitySpecs( patternDef.getDomainClassMetadata() ) );
            return constraint;
        }
    }

    public static class PatternExpr6<T, A, B, C, D, E> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Variable<C> var4;
        private final Variable<D> var5;
        private final Variable<E> var6;
        private final Predicate6<T, A, B, C, D, E> predicate;

        public PatternExpr6(Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Predicate6<T, A, B, C, D, E> predicate, ReactOn reactOn) {
            this(randomUUID().toString(), var2, var3, var4, var5, var6, predicate, reactOn);
        }

        public PatternExpr6( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Predicate6<T, A, B, C, D, E> predicate, ReactOn reactOn ) {
            super( exprId, reactOn );
            this.var2 = var2;
            this.var3 = var3;
            this.var4 = var4;
            this.var5 = var5;
            this.var6 = var6;
            this.predicate = predicate;
        }

        public Predicate6<T, A, B, C, D, E> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint(PatternDefImpl patternDef) {
            SingleConstraint6 constraint = new SingleConstraint6(getExprId(), patternDef.getFirstVariable(), var2, var3, var4, var5, var6, getPredicate());
            constraint.setReactivitySpecs( getReactivitySpecs( patternDef.getDomainClassMetadata() ) );
            return constraint;
        }
    }

    public static class PatternExpr7<T, A, B, C, D, E, F> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Variable<C> var4;
        private final Variable<D> var5;
        private final Variable<E> var6;
        private final Variable<F> var7;
        private final Predicate7<T, A, B, C, D, E, F> predicate;

        public PatternExpr7(Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7, Predicate7<T, A, B, C, D, E, F> predicate, ReactOn reactOn) {
            this(randomUUID().toString(), var2, var3, var4, var5, var6, var7, predicate, reactOn);
        }

        public PatternExpr7( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7, Predicate7<T, A, B, C, D, E, F> predicate, ReactOn reactOn ) {
            super( exprId, reactOn );
            this.var2 = var2;
            this.var3 = var3;
            this.var4 = var4;
            this.var5 = var5;
            this.var6 = var6;
            this.var7 = var7;
            this.predicate = predicate;
        }

        public Predicate7<T, A, B, C, D, E, F> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint(PatternDefImpl patternDef) {
            SingleConstraint7 constraint = new SingleConstraint7(getExprId(), patternDef.getFirstVariable(), var2, var3, var4, var5, var6, var7, getPredicate());
            constraint.setReactivitySpecs( getReactivitySpecs( patternDef.getDomainClassMetadata() ) );
            return constraint;
        }
    }

    public static class PatternExpr8<T, A, B, C, D, E, F, G, H> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Variable<C> var4;
        private final Variable<D> var5;
        private final Variable<E> var6;
        private final Variable<F> var7;
        private final Variable<G> var8;
        private final Predicate8<T, A, B, C, D, E, F, G> predicate;

        public PatternExpr8( Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8,
                             Predicate8<T, A, B, C, D, E, F, G> predicate, ReactOn reactOn) {
            this(randomUUID().toString(), var2, var3, var4, var5, var6, var7, var8, predicate, reactOn);
        }

        public PatternExpr8( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8,
                             Predicate8<T, A, B, C, D, E, F, G> predicate, ReactOn reactOn ) {
            super( exprId, reactOn );
            this.var2 = var2;
            this.var3 = var3;
            this.var4 = var4;
            this.var5 = var5;
            this.var6 = var6;
            this.var7 = var7;
            this.var8 = var8;
            this.predicate = predicate;
        }

        public Predicate8<T, A, B, C, D, E, F, G> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint(PatternDefImpl patternDef) {
            SingleConstraint8 constraint = new SingleConstraint8(getExprId(), patternDef.getFirstVariable(), var2, var3, var4, var5, var6, var7, var8, getPredicate());
            constraint.setReactivitySpecs( getReactivitySpecs( patternDef.getDomainClassMetadata() ) );
            return constraint;
        }
    }

    public static class PatternExpr9<T, A, B, C, D, E, F, G, H> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Variable<C> var4;
        private final Variable<D> var5;
        private final Variable<E> var6;
        private final Variable<F> var7;
        private final Variable<G> var8;
        private final Variable<H> var9;
        private final Predicate9<T, A, B, C, D, E, F, G, H> predicate;

        public PatternExpr9( Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8, Variable<H> var9,
                             Predicate9<T, A, B, C, D, E, F, G, H> predicate, ReactOn reactOn) {
            this(randomUUID().toString(), var2, var3, var4, var5, var6, var7, var8, var9, predicate, reactOn);
        }

        public PatternExpr9( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8, Variable<H> var9,
                             Predicate9<T, A, B, C, D, E, F, G, H> predicate, ReactOn reactOn ) {
            super( exprId, reactOn );
            this.var2 = var2;
            this.var3 = var3;
            this.var4 = var4;
            this.var5 = var5;
            this.var6 = var6;
            this.var7 = var7;
            this.var8 = var8;
            this.var9 = var9;
            this.predicate = predicate;
        }

        public Predicate9<T, A, B, C, D, E, F, G, H> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint(PatternDefImpl patternDef) {
            SingleConstraint9 constraint = new SingleConstraint9(getExprId(), patternDef.getFirstVariable(), var2, var3, var4, var5, var6, var7, var8, var9, getPredicate());
            constraint.setReactivitySpecs( getReactivitySpecs( patternDef.getDomainClassMetadata() ) );
            return constraint;
        }
    }

    public static class PatternExpr10<T, A, B, C, D, E, F, G, H, I> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Variable<C> var4;
        private final Variable<D> var5;
        private final Variable<E> var6;
        private final Variable<F> var7;
        private final Variable<G> var8;
        private final Variable<H> var9;
        private final Variable<I> var10;
        private final Predicate10<T, A, B, C, D, E, F, G, H, I> predicate;

        public PatternExpr10( Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                              Variable<G> var8, Variable<H> var9, Variable<I> var10,
                              Predicate10<T, A, B, C, D, E, F, G, H, I> predicate, ReactOn reactOn) {
            this(randomUUID().toString(), var2, var3, var4, var5, var6, var7, var8, var9, var10, predicate, reactOn);
        }

        public PatternExpr10( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                              Variable<G> var8, Variable<H> var9, Variable<I> var10,
                              Predicate10<T, A, B, C, D, E, F, G, H, I> predicate, ReactOn reactOn ) {
            super( exprId, reactOn );
            this.var2 = var2;
            this.var3 = var3;
            this.var4 = var4;
            this.var5 = var5;
            this.var6 = var6;
            this.var7 = var7;
            this.var8 = var8;
            this.var9 = var9;
            this.var10 = var10;
            this.predicate = predicate;
        }

        public Predicate10<T, A, B, C, D, E, F, G, H, I> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint(PatternDefImpl patternDef) {
            SingleConstraint10 constraint = new SingleConstraint10(getExprId(), patternDef.getFirstVariable(), var2, var3, var4, var5, var6, var7, var8, var9, var10, getPredicate());
            constraint.setReactivitySpecs( getReactivitySpecs( patternDef.getDomainClassMetadata() ) );
            return constraint;
        }
    }

    public static class PatternExpr11<T, A, B, C, D, E, F, G, H, I, J> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Variable<C> var4;
        private final Variable<D> var5;
        private final Variable<E> var6;
        private final Variable<F> var7;
        private final Variable<G> var8;
        private final Variable<H> var9;
        private final Variable<I> var10;
        private final Variable<J> var11;
        private final Predicate11<T, A, B, C, D, E, F, G, H, I, J> predicate;

        public PatternExpr11(Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11,
                             Predicate11<T, A, B, C, D, E, F, G, H, I, J> predicate, ReactOn reactOn) {
            this(randomUUID().toString(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, predicate, reactOn);
        }

        public PatternExpr11( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                              Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11,
                              Predicate11<T, A, B, C, D, E, F, G, H, I, J> predicate, ReactOn reactOn ) {
            super( exprId, reactOn );
            this.var2 = var2;
            this.var3 = var3;
            this.var4 = var4;
            this.var5 = var5;
            this.var6 = var6;
            this.var7 = var7;
            this.var8 = var8;
            this.var9 = var9;
            this.var10 = var10;
            this.var11 = var11;
            this.predicate = predicate;
        }

        public Predicate11<T, A, B, C, D, E, F, G, H, I, J> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint(PatternDefImpl patternDef) {
            SingleConstraint11 constraint = new SingleConstraint11(getExprId(), patternDef.getFirstVariable(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, getPredicate());
            constraint.setReactivitySpecs( getReactivitySpecs( patternDef.getDomainClassMetadata() ) );
            return constraint;
        }
    }

    public static class PatternExpr12<T, A, B, C, D, E, F, G, H, I, J, K> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Variable<C> var4;
        private final Variable<D> var5;
        private final Variable<E> var6;
        private final Variable<F> var7;
        private final Variable<G> var8;
        private final Variable<H> var9;
        private final Variable<I> var10;
        private final Variable<J> var11;
        private final Variable<K> var12;
        private final Predicate12<T, A, B, C, D, E, F, G, H, I, J, K> predicate;

        public PatternExpr12(Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12,
                             Predicate12<T, A, B, C, D, E, F, G, H, I, J, K> predicate, ReactOn reactOn) {
            this(randomUUID().toString(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, predicate, reactOn);
        }

        public PatternExpr12( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                              Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12,
                              Predicate12<T, A, B, C, D, E, F, G, H, I, J, K> predicate, ReactOn reactOn ) {
            super( exprId, reactOn );
            this.var2 = var2;
            this.var3 = var3;
            this.var4 = var4;
            this.var5 = var5;
            this.var6 = var6;
            this.var7 = var7;
            this.var8 = var8;
            this.var9 = var9;
            this.var10 = var10;
            this.var11 = var11;
            this.var12 = var12;
            this.predicate = predicate;
        }

        public Predicate12<T, A, B, C, D, E, F, G, H, I, J, K> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint(PatternDefImpl patternDef) {
            SingleConstraint12 constraint = new SingleConstraint12(getExprId(), patternDef.getFirstVariable(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, getPredicate());
            constraint.setReactivitySpecs( getReactivitySpecs( patternDef.getDomainClassMetadata() ) );
            return constraint;
        }
    }

    public static class PatternExpr13<T, A, B, C, D, E, F, G, H, I, J, K, L> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Variable<C> var4;
        private final Variable<D> var5;
        private final Variable<E> var6;
        private final Variable<F> var7;
        private final Variable<G> var8;
        private final Variable<H> var9;
        private final Variable<I> var10;
        private final Variable<J> var11;
        private final Variable<K> var12;
        private final Variable<L> var13;
        private final Predicate13<T, A, B, C, D, E, F, G, H, I, J, K, L> predicate;

        public PatternExpr13(Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                             Predicate13<T, A, B, C, D, E, F, G, H, I, J, K, L> predicate, ReactOn reactOn) {
            this(randomUUID().toString(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, predicate, reactOn);
        }

        public PatternExpr13( String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                              Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                              Predicate13<T, A, B, C, D, E, F, G, H, I, J, K, L> predicate, ReactOn reactOn ) {
            super( exprId, reactOn );
            this.var2 = var2;
            this.var3 = var3;
            this.var4 = var4;
            this.var5 = var5;
            this.var6 = var6;
            this.var7 = var7;
            this.var8 = var8;
            this.var9 = var9;
            this.var10 = var10;
            this.var11 = var11;
            this.var12 = var12;
            this.var13 = var13;
            this.predicate = predicate;
        }

        public Predicate13<T, A, B, C, D, E, F, G, H, I, J, K, L> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint(PatternDefImpl patternDef) {
            SingleConstraint13 constraint = new SingleConstraint13(getExprId(), patternDef.getFirstVariable(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, getPredicate());
            constraint.setReactivitySpecs( getReactivitySpecs( patternDef.getDomainClassMetadata() ) );
            return constraint;
        }
    }
    public static class PatternExpr14<T, A, B, C, D, E, F, G, H, I, J, K, L, M> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Variable<C> var4;
        private final Variable<D> var5;
        private final Variable<E> var6;
        private final Variable<F> var7;
        private final Variable<G> var8;
        private final Variable<H> var9;
        private final Variable<I> var10;
        private final Variable<J> var11;
        private final Variable<K> var12;
        private final Variable<L> var13;
        private final Variable<M> var14;
        private final Predicate14<T, A, B, C, D, E, F, G, H, I, J, K, L, M> predicate;

        public PatternExpr14(Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                             Variable<M> var14, Predicate14<T, A, B, C, D, E, F, G, H, I, J, K, L, M> predicate, ReactOn reactOn) {
            this(randomUUID().toString(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, predicate, reactOn);
        }

        public PatternExpr14(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                             Variable<M> var14, Predicate14<T, A, B, C, D, E, F, G, H, I, J, K, L, M> predicate, ReactOn reactOn) {
            super(exprId, reactOn);
            this.var2 = var2;
            this.var3 = var3;
            this.var4 = var4;
            this.var5 = var5;
            this.var6 = var6;
            this.var7 = var7;
            this.var8 = var8;
            this.var9 = var9;
            this.var10 = var10;
            this.var11 = var11;
            this.var12 = var12;
            this.var13 = var13;
            this.var14 = var14;
            this.predicate = predicate;
        }

        public Predicate14<T, A, B, C, D, E, F, G, H, I, J, K, L, M> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint(PatternDefImpl patternDef) {
            SingleConstraint14 constraint = new SingleConstraint14(getExprId(), patternDef.getFirstVariable(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, getPredicate());
            constraint.setReactivitySpecs(getReactivitySpecs(patternDef.getDomainClassMetadata()));
            return constraint;
        }
    }

    public static class PatternExpr15<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Variable<C> var4;
        private final Variable<D> var5;
        private final Variable<E> var6;
        private final Variable<F> var7;
        private final Variable<G> var8;
        private final Variable<H> var9;
        private final Variable<I> var10;
        private final Variable<J> var11;
        private final Variable<K> var12;
        private final Variable<L> var13;
        private final Variable<M> var14;
        private final Variable<N> var15;
        private final Predicate15<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N> predicate;

        public PatternExpr15(Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                             Variable<M> var14, Variable<N> var15, Predicate15<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N> predicate, ReactOn reactOn) {
            this(randomUUID().toString(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, predicate, reactOn);
        }

        public PatternExpr15(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                             Variable<M> var14, Variable<N> var15, Predicate15<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N> predicate, ReactOn reactOn) {
            super(exprId, reactOn);
            this.var2 = var2;
            this.var3 = var3;
            this.var4 = var4;
            this.var5 = var5;
            this.var6 = var6;
            this.var7 = var7;
            this.var8 = var8;
            this.var9 = var9;
            this.var10 = var10;
            this.var11 = var11;
            this.var12 = var12;
            this.var13 = var13;
            this.var14 = var14;
            this.var15 = var15;
            this.predicate = predicate;
        }

        public Predicate15<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint(PatternDefImpl patternDef) {
            SingleConstraint15 constraint = new SingleConstraint15(getExprId(), patternDef.getFirstVariable(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, getPredicate());
            constraint.setReactivitySpecs(getReactivitySpecs(patternDef.getDomainClassMetadata()));
            return constraint;
        }
    }

    public static class PatternExpr16<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Variable<C> var4;
        private final Variable<D> var5;
        private final Variable<E> var6;
        private final Variable<F> var7;
        private final Variable<G> var8;
        private final Variable<H> var9;
        private final Variable<I> var10;
        private final Variable<J> var11;
        private final Variable<K> var12;
        private final Variable<L> var13;
        private final Variable<M> var14;
        private final Variable<N> var15;
        private final Variable<O> var16;
        private final Predicate16<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> predicate;

        public PatternExpr16(Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                             Variable<M> var14, Variable<N> var15, Variable<O> var16, Predicate16<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> predicate, ReactOn reactOn) {
            this(randomUUID().toString(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, predicate, reactOn);
        }

        public PatternExpr16(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                             Variable<M> var14, Variable<N> var15, Variable<O> var16, Predicate16<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> predicate, ReactOn reactOn) {
            super(exprId, reactOn);
            this.var2 = var2;
            this.var3 = var3;
            this.var4 = var4;
            this.var5 = var5;
            this.var6 = var6;
            this.var7 = var7;
            this.var8 = var8;
            this.var9 = var9;
            this.var10 = var10;
            this.var11 = var11;
            this.var12 = var12;
            this.var13 = var13;
            this.var14 = var14;
            this.var15 = var15;
            this.var16 = var16;
            this.predicate = predicate;
        }

        public Predicate16<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint(PatternDefImpl patternDef) {
            SingleConstraint16 constraint = new SingleConstraint16(getExprId(), patternDef.getFirstVariable(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, getPredicate());
            constraint.setReactivitySpecs(getReactivitySpecs(patternDef.getDomainClassMetadata()));
            return constraint;
        }
    }

    public static class PatternExpr17<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Variable<C> var4;
        private final Variable<D> var5;
        private final Variable<E> var6;
        private final Variable<F> var7;
        private final Variable<G> var8;
        private final Variable<H> var9;
        private final Variable<I> var10;
        private final Variable<J> var11;
        private final Variable<K> var12;
        private final Variable<L> var13;
        private final Variable<M> var14;
        private final Variable<N> var15;
        private final Variable<O> var16;
        private final Variable<P> var17;
        private final Predicate17<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> predicate;

        public PatternExpr17(Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                             Variable<M> var14, Variable<N> var15, Variable<O> var16, Variable<P> var17, Predicate17<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> predicate, ReactOn reactOn) {
            this(randomUUID().toString(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, var17, predicate, reactOn);
        }

        public PatternExpr17(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                             Variable<M> var14, Variable<N> var15, Variable<O> var16, Variable<P> var17, Predicate17<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> predicate, ReactOn reactOn) {
            super(exprId, reactOn);
            this.var2 = var2;
            this.var3 = var3;
            this.var4 = var4;
            this.var5 = var5;
            this.var6 = var6;
            this.var7 = var7;
            this.var8 = var8;
            this.var9 = var9;
            this.var10 = var10;
            this.var11 = var11;
            this.var12 = var12;
            this.var13 = var13;
            this.var14 = var14;
            this.var15 = var15;
            this.var16 = var16;
            this.var17 = var17;
            this.predicate = predicate;
        }

        public Predicate17<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint(PatternDefImpl patternDef) {
            SingleConstraint17 constraint = new SingleConstraint17(getExprId(), patternDef.getFirstVariable(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, var17, getPredicate());
            constraint.setReactivitySpecs(getReactivitySpecs(patternDef.getDomainClassMetadata()));
            return constraint;
        }
    }

    public static class PatternExpr18<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> extends PatternExprImpl<T> {
        private final Variable<A> var2;
        private final Variable<B> var3;
        private final Variable<C> var4;
        private final Variable<D> var5;
        private final Variable<E> var6;
        private final Variable<F> var7;
        private final Variable<G> var8;
        private final Variable<H> var9;
        private final Variable<I> var10;
        private final Variable<J> var11;
        private final Variable<K> var12;
        private final Variable<L> var13;
        private final Variable<M> var14;
        private final Variable<N> var15;
        private final Variable<O> var16;
        private final Variable<P> var17;
        private final Variable<Q> var18;
        private final Predicate18<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> predicate;

        public PatternExpr18(Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                             Variable<M> var14, Variable<N> var15, Variable<O> var16, Variable<P> var17, Variable<Q> var18, Predicate18<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> predicate, ReactOn reactOn) {
            this(randomUUID().toString(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, var17, var18, predicate, reactOn);
        }

        public PatternExpr18(String exprId, Variable<A> var2, Variable<B> var3, Variable<C> var4, Variable<D> var5, Variable<E> var6, Variable<F> var7,
                             Variable<G> var8, Variable<H> var9, Variable<I> var10, Variable<J> var11, Variable<K> var12, Variable<L> var13,
                             Variable<M> var14, Variable<N> var15, Variable<O> var16, Variable<P> var17, Variable<Q> var18, Predicate18<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> predicate, ReactOn reactOn) {
            super(exprId, reactOn);
            this.var2 = var2;
            this.var3 = var3;
            this.var4 = var4;
            this.var5 = var5;
            this.var6 = var6;
            this.var7 = var7;
            this.var8 = var8;
            this.var9 = var9;
            this.var10 = var10;
            this.var11 = var11;
            this.var12 = var12;
            this.var13 = var13;
            this.var14 = var14;
            this.var15 = var15;
            this.var16 = var16;
            this.var17 = var17;
            this.var18 = var18;
            this.predicate = predicate;
        }

        public Predicate18<T, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> getPredicate() {
            return predicate;
        }

        @Override
        public Constraint asConstraint(PatternDefImpl patternDef) {
            SingleConstraint18 constraint = new SingleConstraint18(getExprId(), patternDef.getFirstVariable(), var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, var17, var18, getPredicate());
            constraint.setReactivitySpecs(getReactivitySpecs(patternDef.getDomainClassMetadata()));
            return constraint;
        }
    }

    public static class TemporalPatternExpr<T, U> extends PatternExprImpl<T> {
        private final Function1<T, ?> fThis;
        private final Function1<U, ?> fVar;
        private final Variable<U> var1;
        private final TemporalPredicate temporalPredicate;

        public TemporalPatternExpr(Variable<U> var2, TemporalPredicate temporalPredicate) {
            this(randomUUID().toString(), null, var2, null, temporalPredicate);
        }

        public TemporalPatternExpr( String exprId, Function1<T, ?> fThis, Variable<U> var1, Function1<U, ?> fVar, TemporalPredicate temporalPredicate) {
            super( exprId, null );
            this.fThis = fThis;
            this.var1 = var1;
            this.fVar = fVar;
            this.temporalPredicate = temporalPredicate;
        }

        @Override
        public Constraint asConstraint(PatternDefImpl patternDef) {
            return new VariableTemporalConstraint(getExprId(), patternDef.getFirstVariable(), fThis, var1, fVar, temporalPredicate);
        }
    }

    public static class FixedTemporalPatternExpr<T> extends PatternExprImpl<T> {
        private final Function1<?,?> func;
        private final long value;
        private final TemporalPredicate temporalPredicate;

        public FixedTemporalPatternExpr(Function1<?,?> func, long value, TemporalPredicate temporalPredicate) {
            this(randomUUID().toString(), func, value, temporalPredicate);
        }

        public FixedTemporalPatternExpr( String exprId, Function1<?,?> func, long value, TemporalPredicate temporalPredicate) {
            super( exprId, null );
            this.func = func;
            this.value = value;
            this.temporalPredicate = temporalPredicate;
        }

        @Override
        public Constraint asConstraint(PatternDefImpl patternDef) {
            return new FixedTemporalConstraint(getExprId(), patternDef.getFirstVariable(), func, value, temporalPredicate);
        }
    }

    public static class ReactOn {
        private final String[] strings;

        public ReactOn( String... strings ) {
            this.strings = strings;
        }

        private String[] getStrings() {
            return strings;
        }
    }

    public static class SequenceViewItem implements RuleItemBuilder<SequenceViewItem>, RuleItem {
        private final SequenceStep[] steps;

        public SequenceViewItem(SequenceStep... steps) {
            this.steps = steps;
        }

        public SequenceStep[] getSteps() {
            return steps;
        }

        @Override
        public SequenceViewItem get() {
            return this;
        }
    }

    public static abstract class PatternBindingImpl<T, A> implements PatternItem<T> {
        protected final Variable<A> boundVar;
        protected final ReactOn reactOn;

        public PatternBindingImpl( Variable<A> boundVar, ReactOn reactOn ) {
            this.boundVar = boundVar;
            this.reactOn = reactOn;
        }

        public String[] getReactOn() {
            return reactOn != null ? reactOn.getStrings() : new String[0];
        }

        public abstract Binding asBinding( PatternDefImpl patternDef );
    }

    public static class PatternBinding1<T, A> extends PatternBindingImpl<T, A> {
        private final Function1<T, A> f;

        public PatternBinding1( Variable<A> boundVar, Function1<T, A> f, ReactOn reactOn ) {
            super( boundVar, reactOn );
            this.f = new Function1.Impl<>( f );
        }

        @Override
        public Binding asBinding( PatternDefImpl patternDef ) {
            return new BindViewItem1( boundVar, f, patternDef.getFirstVariable(), getReactOn(), null );
        }
    }

    public static class PatternBinding2<T, U, A> extends PatternBindingImpl<T, A> {
        private final Variable<U> otherVar;
        private final Function2<T, U, A> f;

        public PatternBinding2( Variable<A> boundVar, Variable<U> otherVar, Function2<T, U, A> f, ReactOn reactOn ) {
            super( boundVar, reactOn );
            this.f = new Function2.Impl<>( f );
            this.otherVar = otherVar;
        }

        @Override
        public Binding asBinding( PatternDefImpl patternDef ) {
            return new BindViewItem2( boundVar, f, patternDef.getFirstVariable(), otherVar, getReactOn(), null );
        }
    }

    public static class PatternBinding3<T, U, V, A> extends PatternBindingImpl<T, A> {
        private final Variable<U> otherVar1;
        private final Variable<V> otherVar2;
        private final Function3<T, U, V, A> f;

        public PatternBinding3( Variable<A> boundVar, Variable<U> otherVar1, Variable<V> otherVar2, Function3<T, U, V, A> f, ReactOn reactOn ) {
            super( boundVar, reactOn );
            this.f = new Function3.Impl<>( f );
            this.otherVar1 = otherVar1;
            this.otherVar2 = otherVar2;
        }

        @Override
        public Binding asBinding( PatternDefImpl patternDef ) {
            return new BindViewItem3( boundVar, f, patternDef.getFirstVariable(), otherVar1, otherVar2, getReactOn(), null );
        }
    }

    public static class PatternBinding4<T, U, V, W, A> extends PatternBindingImpl<T, A> {
        private final Variable<U> otherVar1;
        private final Variable<V> otherVar2;
        private final Variable<W> otherVar3;
        private final Function4<T, U, V, W, A> f;

        public PatternBinding4( Variable<A> boundVar, Variable<U> otherVar1, Variable<V> otherVar2, Variable<W> otherVar3, Function4<T, U, V, W, A> f, ReactOn reactOn ) {
            super( boundVar, reactOn );
            this.f = new Function4.Impl<>( f );
            this.otherVar1 = otherVar1;
            this.otherVar2 = otherVar2;
            this.otherVar3 = otherVar3;
        }

        @Override
        public Binding asBinding( PatternDefImpl patternDef ) {
            return new BindViewItem4( boundVar, f, patternDef.getFirstVariable(), otherVar1, otherVar2, otherVar3, getReactOn(), null );
        }
    }

    // -- Conditional Named Consequnce --

    public static <A> ConditionalConsequenceBuilder when( Variable<A> var, Predicate1<A> predicate ) {
        return when( expr( var, predicate ) );
    }

    public static <A> ConditionalConsequenceBuilder when( String exprId, Variable<A> var, Predicate1<A> predicate ) {
        return when( expr( exprId, var, predicate ) );
    }

    public static <A, B> ConditionalConsequenceBuilder when( Variable<A> var1, Variable<B> var2, Predicate2<A, B> predicate ) {
        return when( expr( var1, var2, predicate ) );
    }

    public static <A, B> ConditionalConsequenceBuilder when( String exprId, Variable<A> var1, Variable<B> var2, Predicate2<A, B> predicate ) {
        return when( expr( exprId, var1, var2, predicate ) );
    }

    public static ConditionalConsequenceBuilder when( ExprViewItem expr ) {
        return new ConditionalConsequenceBuilder( expr );
    }

    public static ConditionalConsequenceBuilder when() {
        return new ConditionalConsequenceBuilder(null); // the condition is always true
    }

    // -- rule --

    public static SequenceViewItem sequence(SequenceStep... steps) {
        if (steps == null || steps.length == 0) {
            throw new IllegalArgumentException("sequence() requires at least one step");
        }
        return new SequenceViewItem(steps);
    }


    public static CombinedExprViewItem or(ViewItemBuilder<?> first, ViewItemBuilder<?>... rest) {
        return new CombinedExprViewItem(Condition.Type.OR, viewItems(first, rest));
    }

    public static CombinedExprViewItem and(ViewItemBuilder<?> first, ViewItemBuilder<?>... rest) {
        return new CombinedExprViewItem(Condition.Type.AND, viewItems(first, rest));
    }

    private static ViewItem[] viewItems(ViewItemBuilder<?> first, ViewItemBuilder<?>[] rest) {
        ViewItem[] all = new ViewItem[1 + rest.length];
        all[0] = first.get();
        for (int i = 0; i < rest.length; i++) {
            all[1 + i] = rest[i].get();
        }
        return all;
    }


    public static RuleBuilder rule( String name ) {
        return new RuleBuilder( VIEW_BUILDER, name );
    }

    public static RuleBuilder rule( String pkg, String name ) {
        return new RuleBuilder( VIEW_BUILDER, pkg, name );
    }

    // -- query --

    public static Query0Def query( String name ) {
        return new Query0DefImpl( VIEW_BUILDER, name );
    }

    public static Query0Def query( String pkg, String name ) {
        return new Query0DefImpl( VIEW_BUILDER, pkg, name );
    }

    public static <T1> Query1Def<T1> query(String name, Class<T1> type1) {
        return new Query1DefImpl<>(VIEW_BUILDER, name, type1);
    }

    public static <T1> Query1Def<T1> query(String pkg, String name, Class<T1> type1) {
        return new Query1DefImpl<>(VIEW_BUILDER, pkg, name, type1);
    }

    public static <T1> Query1Def<T1> query(String name, Class<T1> type1, String arg1name) {
        return new Query1DefImpl<>(VIEW_BUILDER, name, type1, arg1name);
    }

    public static <T1> Query1Def<T1> query(String pkg, String name, Class<T1> type1, String arg1name) {
        return new Query1DefImpl<>(VIEW_BUILDER, pkg, name, type1, arg1name);
    }

    public static <T1, T2> Query2Def<T1, T2> query(String name, Class<T1> type1, Class<T2> type2) {
        return new Query2DefImpl<>(VIEW_BUILDER, name, type1, type2);
    }

    public static <T1, T2> Query2Def<T1, T2> query(String pkg, String name, Class<T1> type1, Class<T2> type2) {
        return new Query2DefImpl<>(VIEW_BUILDER, pkg, name, type1, type2);
    }

    public static <T1, T2> Query2Def<T1, T2> query(String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name) {
        return new Query2DefImpl<>(VIEW_BUILDER, name, type1, arg1name, type2, arg2name);
    }

    public static <T1, T2> Query2Def<T1, T2> query(String pkg, String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name) {
        return new Query2DefImpl<>(VIEW_BUILDER, pkg, name, type1, arg1name, type2, arg2name);
    }

    public static <T1, T2, T3> Query3Def<T1, T2, T3> query(String name, Class<T1> type1, Class<T2> type2, Class<T3> type3) {
        return new Query3DefImpl<>(VIEW_BUILDER, name, type1, type2, type3);
    }

    public static <T1, T2, T3> Query3Def<T1, T2, T3> query(String pkg, String name, Class<T1> type1, Class<T2> type2, Class<T3> type3) {
        return new Query3DefImpl<>(VIEW_BUILDER, pkg, name, type1, type2, type3);
    }

    public static <T1, T2, T3> Query3Def<T1, T2, T3> query(String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name) {
        return new Query3DefImpl<>(VIEW_BUILDER, name, type1, arg1name, type2, arg2name, type3, arg3name);
    }

    public static <T1, T2, T3> Query3Def<T1, T2, T3> query(String pkg, String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name) {
        return new Query3DefImpl<>(VIEW_BUILDER, pkg, name, type1, arg1name, type2, arg2name, type3, arg3name);
    }

    public static <T1, T2, T3, T4> Query4Def<T1, T2, T3, T4> query(String name, Class<T1> type1, Class<T2> type2, Class<T3> type3, Class<T4> type4) {
        return new Query4DefImpl<>(VIEW_BUILDER, name, type1, type2, type3, type4);
    }

    public static <T1, T2, T3, T4> Query4Def<T1, T2, T3, T4> query(String pkg, String name, Class<T1> type1, Class<T2> type2, Class<T3> type3, Class<T4> type4) {
        return new Query4DefImpl<>(VIEW_BUILDER, pkg, name, type1, type2, type3, type4);
    }

    public static <T1, T2, T3, T4> Query4Def<T1, T2, T3, T4> query(String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name, Class<T4> type4, String arg4name) {
        return new Query4DefImpl<>(VIEW_BUILDER, name, type1, arg1name, type2, arg2name, type3, arg3name, type4, arg4name);
    }

    public static <T1, T2, T3, T4> Query4Def<T1, T2, T3, T4> query(String pkg, String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name, Class<T4> type4, String arg4name) {
        return new Query4DefImpl<>(VIEW_BUILDER, pkg, name, type1, arg1name, type2, arg2name, type3, arg3name, type4, arg4name);
    }

    public static <T1, T2, T3, T4, T5> Query5Def<T1, T2, T3, T4, T5> query(String name, Class<T1> type1, Class<T2> type2, Class<T3> type3, Class<T4> type4, Class<T5> type5) {
        return new Query5DefImpl<>(VIEW_BUILDER, name, type1, type2, type3, type4, type5);
    }

    public static <T1, T2, T3, T4, T5> Query5Def<T1, T2, T3, T4, T5> query(String pkg, String name, Class<T1> type1, Class<T2> type2, Class<T3> type3, Class<T4> type4, Class<T5> type5) {
        return new Query5DefImpl<>(VIEW_BUILDER, pkg, name, type1, type2, type3, type4, type5);
    }

    public static <T1, T2, T3, T4, T5> Query5Def<T1, T2, T3, T4, T5> query(String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name, Class<T4> type4, String arg4name, Class<T5> type5, String arg5name) {
        return new Query5DefImpl<>(VIEW_BUILDER, name, type1, arg1name, type2, arg2name, type3, arg3name, type4, arg4name, type5, arg5name);
    }

    public static <T1, T2, T3, T4, T5> Query5Def<T1, T2, T3, T4, T5> query(String pkg, String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name, Class<T4> type4, String arg4name, Class<T5> type5, String arg5name) {
        return new Query5DefImpl<>(VIEW_BUILDER, pkg, name, type1, arg1name, type2, arg2name, type3, arg3name, type4, arg4name, type5, arg5name);
    }

    public static <T1, T2, T3, T4, T5, T6> Query6Def<T1, T2, T3, T4, T5, T6> query(String name, Class<T1> type1, Class<T2> type2, Class<T3> type3, Class<T4> type4, Class<T5> type5, Class<T6> type6) {
        return new Query6DefImpl<>(VIEW_BUILDER, name, type1, type2, type3, type4, type5, type6);
    }

    public static <T1, T2, T3, T4, T5, T6> Query6Def<T1, T2, T3, T4, T5, T6> query(String pkg, String name, Class<T1> type1, Class<T2> type2, Class<T3> type3, Class<T4> type4, Class<T5> type5, Class<T6> type6) {
        return new Query6DefImpl<>(VIEW_BUILDER, pkg, name, type1, type2, type3, type4, type5, type6);
    }

    public static <T1, T2, T3, T4, T5, T6> Query6Def<T1, T2, T3, T4, T5, T6> query(String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name, Class<T4> type4, String arg4name, Class<T5> type5, String arg5name, Class<T6> type6, String arg6name) {
        return new Query6DefImpl<>(VIEW_BUILDER, name, type1, arg1name, type2, arg2name, type3, arg3name, type4, arg4name, type5, arg5name, type6, arg6name);
    }

    public static <T1, T2, T3, T4, T5, T6> Query6Def<T1, T2, T3, T4, T5, T6> query(String pkg, String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name, Class<T4> type4, String arg4name, Class<T5> type5, String arg5name, Class<T6> type6, String arg6name) {
        return new Query6DefImpl<>(VIEW_BUILDER, pkg, name, type1, arg1name, type2, arg2name, type3, arg3name, type4, arg4name, type5, arg5name, type6, arg6name);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> Query7Def<T1, T2, T3, T4, T5, T6, T7> query(String name, Class<T1> type1, Class<T2> type2, Class<T3> type3, Class<T4> type4, Class<T5> type5, Class<T6> type6, Class<T7> type7) {
        return new Query7DefImpl<>(VIEW_BUILDER, name, type1, type2, type3, type4, type5, type6, type7);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> Query7Def<T1, T2, T3, T4, T5, T6, T7> query(String pkg, String name, Class<T1> type1, Class<T2> type2, Class<T3> type3, Class<T4> type4, Class<T5> type5, Class<T6> type6, Class<T7> type7) {
        return new Query7DefImpl<>(VIEW_BUILDER, pkg, name, type1, type2, type3, type4, type5, type6, type7);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> Query7Def<T1, T2, T3, T4, T5, T6, T7> query(String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name, Class<T4> type4, String arg4name, Class<T5> type5, String arg5name, Class<T6> type6, String arg6name, Class<T7> type7, String arg7name) {
        return new Query7DefImpl<>(VIEW_BUILDER, name, type1, arg1name, type2, arg2name, type3, arg3name, type4, arg4name, type5, arg5name, type6, arg6name, type7, arg7name);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> Query7Def<T1, T2, T3, T4, T5, T6, T7> query(String pkg, String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name, Class<T4> type4, String arg4name, Class<T5> type5, String arg5name, Class<T6> type6, String arg6name, Class<T7> type7, String arg7name) {
        return new Query7DefImpl<>(VIEW_BUILDER, pkg, name, type1, arg1name, type2, arg2name, type3, arg3name, type4, arg4name, type5, arg5name, type6, arg6name, type7, arg7name);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> Query8Def<T1, T2, T3, T4, T5, T6, T7, T8> query(String name, Class<T1> type1, Class<T2> type2, Class<T3> type3, Class<T4> type4, Class<T5> type5, Class<T6> type6, Class<T7> type7, Class<T8> type8) {
        return new Query8DefImpl<>(VIEW_BUILDER, name, type1, type2, type3, type4, type5, type6, type7, type8);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> Query8Def<T1, T2, T3, T4, T5, T6, T7, T8> query(String pkg, String name, Class<T1> type1, Class<T2> type2, Class<T3> type3, Class<T4> type4, Class<T5> type5, Class<T6> type6, Class<T7> type7, Class<T8> type8) {
        return new Query8DefImpl<>(VIEW_BUILDER, pkg, name, type1, type2, type3, type4, type5, type6, type7, type8);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> Query8Def<T1, T2, T3, T4, T5, T6, T7, T8> query(String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name, Class<T4> type4, String arg4name, Class<T5> type5, String arg5name, Class<T6> type6, String arg6name, Class<T7> type7, String arg7name, Class<T8> type8, String arg8name) {
        return new Query8DefImpl<>(VIEW_BUILDER, name, type1, arg1name, type2, arg2name, type3, arg3name, type4, arg4name, type5, arg5name, type6, arg6name, type7, arg7name, type8, arg8name);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> Query8Def<T1, T2, T3, T4, T5, T6, T7, T8> query(String pkg, String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name, Class<T4> type4, String arg4name, Class<T5> type5, String arg5name, Class<T6> type6, String arg6name, Class<T7> type7, String arg7name, Class<T8> type8, String arg8name) {
        return new Query8DefImpl<>(VIEW_BUILDER, pkg, name, type1, arg1name, type2, arg2name, type3, arg3name, type4, arg4name, type5, arg5name, type6, arg6name, type7, arg7name, type8, arg8name);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> Query9Def<T1, T2, T3, T4, T5, T6, T7, T8, T9> query(String name, Class<T1> type1, Class<T2> type2, Class<T3> type3, Class<T4> type4, Class<T5> type5, Class<T6> type6, Class<T7> type7, Class<T8> type8, Class<T9> type9) {
        return new Query9DefImpl<>(VIEW_BUILDER, name, type1, type2, type3, type4, type5, type6, type7, type8, type9);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> Query9Def<T1, T2, T3, T4, T5, T6, T7, T8, T9> query(String pkg, String name, Class<T1> type1, Class<T2> type2, Class<T3> type3, Class<T4> type4, Class<T5> type5, Class<T6> type6, Class<T7> type7, Class<T8> type8, Class<T9> type9) {
        return new Query9DefImpl<>(VIEW_BUILDER, pkg, name, type1, type2, type3, type4, type5, type6, type7, type8, type9);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> Query9Def<T1, T2, T3, T4, T5, T6, T7, T8, T9> query(String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name, Class<T4> type4, String arg4name, Class<T5> type5, String arg5name, Class<T6> type6, String arg6name, Class<T7> type7, String arg7name, Class<T8> type8, String arg8name, Class<T9> type9, String arg9name) {
        return new Query9DefImpl<>(VIEW_BUILDER, name, type1, arg1name, type2, arg2name, type3, arg3name, type4, arg4name, type5, arg5name, type6, arg6name, type7, arg7name, type8, arg8name, type9, arg9name);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> Query9Def<T1, T2, T3, T4, T5, T6, T7, T8, T9> query(String pkg, String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name, Class<T4> type4, String arg4name, Class<T5> type5, String arg5name, Class<T6> type6, String arg6name, Class<T7> type7, String arg7name, Class<T8> type8, String arg8name, Class<T9> type9, String arg9name) {
        return new Query9DefImpl<>(VIEW_BUILDER, pkg, name, type1, arg1name, type2, arg2name, type3, arg3name, type4, arg4name, type5, arg5name, type6, arg6name, type7, arg7name, type8, arg8name, type9, arg9name);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> Query10Def<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> query(String name, Class<T1> type1, Class<T2> type2, Class<T3> type3, Class<T4> type4, Class<T5> type5, Class<T6> type6, Class<T7> type7, Class<T8> type8, Class<T9> type9, Class<T10> type10) {
        return new Query10DefImpl<>(VIEW_BUILDER, name, type1, type2, type3, type4, type5, type6, type7, type8, type9, type10);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> Query10Def<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> query(String pkg, String name, Class<T1> type1, Class<T2> type2, Class<T3> type3, Class<T4> type4, Class<T5> type5, Class<T6> type6, Class<T7> type7, Class<T8> type8, Class<T9> type9, Class<T10> type10) {
        return new Query10DefImpl<>(VIEW_BUILDER, pkg, name, type1, type2, type3, type4, type5, type6, type7, type8, type9, type10);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> Query10Def<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> query(String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name, Class<T4> type4, String arg4name, Class<T5> type5, String arg5name, Class<T6> type6, String arg6name, Class<T7> type7, String arg7name, Class<T8> type8, String arg8name, Class<T9> type9, String arg9name, Class<T10> type10, String arg10name) {
        return new Query10DefImpl<>(VIEW_BUILDER, name, type1, arg1name, type2, arg2name, type3, arg3name, type4, arg4name, type5, arg5name, type6, arg6name, type7, arg7name, type8, arg8name, type9, arg9name, type10, arg10name);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> Query10Def<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> query(String pkg, String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name, Class<T3> type3, String arg3name, Class<T4> type4, String arg4name, Class<T5> type5, String arg5name, Class<T6> type6, String arg6name, Class<T7> type7, String arg7name, Class<T8> type8, String arg8name, Class<T9> type9, String arg9name, Class<T10> type10, String arg10name) {
        return new Query10DefImpl<>(VIEW_BUILDER, pkg, name, type1, arg1name, type2, arg2name, type3, arg3name, type4, arg4name, type5, arg5name, type6, arg6name, type7, arg7name, type8, arg8name, type9, arg9name, type10, arg10name);
    }

    // -- async --

    public static <T> PatternDSL.ExchangeDef<T> send( Exchange<T> exchange ) {
        return new ExchangeDefImpl<>( exchange );
    }

    public interface ExchangeDef<T> extends ViewItem<T> {
        ExchangeDef<T> message( Function0<T> supplier );
    }

    public static class ExchangeDefImpl<T> implements ExchangeDef<T> {

        private final Exchange<T> exchange;

        public ExchangeDefImpl( Exchange<T> exchange ) {
            this.exchange = exchange;
        }

        @Override
        public ExchangeDef<T> message( Function0<T> supplier ) {
            exchange.setMessageSupplier( supplier );
            return this;
        }

        @Override
        public Exchange<T> getFirstVariable() {
            return exchange;
        }

        @Override
        public Variable<?>[] getVariables() {
            throw new UnsupportedOperationException();
        }
    }

    public static <T> PatternDSL.PatternDef<T> receive(Exchange<T> exchange) {
        return pattern(exchange);
    }
}
