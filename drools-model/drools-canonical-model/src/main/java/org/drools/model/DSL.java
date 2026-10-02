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

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.drools.model.consequences.ConsequenceBuilder;
import org.drools.model.functions.Block0;
import org.drools.model.functions.Block1;
import org.drools.model.functions.Function0;
import org.drools.model.functions.Function1;
import org.drools.model.functions.Function10;
import org.drools.model.functions.Function11;
import org.drools.model.functions.Function2;
import org.drools.model.functions.Function3;
import org.drools.model.functions.Function4;
import org.drools.model.functions.Function5;
import org.drools.model.functions.Function6;
import org.drools.model.functions.Function7;
import org.drools.model.functions.Function8;
import org.drools.model.functions.Function9;
import org.drools.model.functions.Operator;
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
import org.drools.model.functions.accumulate.AccumulateFunction;
import org.drools.model.functions.temporal.AbstractTemporalPredicate;
import org.drools.model.functions.temporal.AfterPredicate;
import org.drools.model.functions.temporal.BeforePredicate;
import org.drools.model.functions.temporal.CoincidesPredicate;
import org.drools.model.functions.temporal.DuringPredicate;
import org.drools.model.functions.temporal.FinishedbyPredicate;
import org.drools.model.functions.temporal.FinishesPredicate;
import org.drools.model.functions.temporal.IncludesPredicate;
import org.drools.model.functions.temporal.MeetsPredicate;
import org.drools.model.functions.temporal.MetbyPredicate;
import org.drools.model.functions.temporal.OverlappedbyPredicate;
import org.drools.model.functions.temporal.OverlapsPredicate;
import org.drools.model.functions.temporal.StartedbyPredicate;
import org.drools.model.functions.temporal.StartsPredicate;
import org.drools.model.functions.temporal.TemporalPredicate;
import org.drools.model.impl.AnnotationValueImpl;
import org.drools.model.impl.DeclarationImpl;
import org.drools.model.impl.EntryPointImpl;
import org.drools.model.impl.Exchange;
import org.drools.model.impl.From0Impl;
import org.drools.model.impl.From10Impl;
import org.drools.model.impl.From11Impl;
import org.drools.model.impl.From1Impl;
import org.drools.model.impl.From2Impl;
import org.drools.model.impl.From3Impl;
import org.drools.model.impl.From4Impl;
import org.drools.model.impl.From5Impl;
import org.drools.model.impl.From6Impl;
import org.drools.model.impl.From7Impl;
import org.drools.model.impl.From8Impl;
import org.drools.model.impl.From9Impl;
import org.drools.model.impl.GlobalImpl;
import org.drools.model.impl.TypeMetaDataImpl;
import org.drools.model.impl.UnitDataImpl;
import org.drools.model.impl.ValueImpl;
import org.drools.model.impl.WindowImpl;
import org.drools.model.impl.WindowReferenceImpl;
import org.drools.model.view.AccumulateExprViewItem;
import org.drools.model.view.CombinedExprViewItem;
import org.drools.model.view.ExistentialExprViewItem;
import org.drools.model.view.Expr10ViewItemImpl;
import org.drools.model.view.Expr11ViewItemImpl;
import org.drools.model.view.Expr12ViewItemImpl;
import org.drools.model.view.Expr13ViewItemImpl;
import org.drools.model.view.Expr14ViewItemImpl;
import org.drools.model.view.Expr15ViewItemImpl;
import org.drools.model.view.Expr16ViewItemImpl;
import org.drools.model.view.Expr17ViewItemImpl;
import org.drools.model.view.Expr18ViewItemImpl;
import org.drools.model.view.Expr1ViewItem;
import org.drools.model.view.Expr1ViewItemImpl;
import org.drools.model.view.Expr2ViewItem;
import org.drools.model.view.Expr2ViewItemImpl;
import org.drools.model.view.Expr3ViewItem;
import org.drools.model.view.Expr3ViewItemImpl;
import org.drools.model.view.Expr4ViewItem;
import org.drools.model.view.Expr4ViewItemImpl;
import org.drools.model.view.Expr5ViewItem;
import org.drools.model.view.Expr5ViewItemImpl;
import org.drools.model.view.Expr6ViewItemImpl;
import org.drools.model.view.Expr7ViewItemImpl;
import org.drools.model.view.Expr8ViewItemImpl;
import org.drools.model.view.Expr9ViewItemImpl;
import org.drools.model.view.ExprViewItem;
import org.drools.model.view.FixedValueItem;
import org.drools.model.view.GroupByExprViewItem;
import org.drools.model.view.ViewItem;
import org.drools.model.view.ViewItemBuilder;


import static org.drools.model.functions.FunctionUtils.toFunctionN;

public class DSL {

    // -- TypeMetaData --

    public static TypeMetaDataImpl typeMetaData( Class<?> type ) {
        return new TypeMetaDataImpl(type);
    }

    public static AnnotationValue annotationValue(String key, String value) {
        return new AnnotationValueImpl( key, value );
    }

    // -- Variable --

    public static <T> Variable<T> any(Class<T> type) {
        return declarationOf( type );
    }

    public static <T> Exchange<T> exchangeOf( Class<T> type ) {
        return new Exchange<>( type );
    }

    public static <T> Exchange<T> exchangeOf( Class<T> type, String name ) {
        return new Exchange<>( type, name );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type ) {
        return new DeclarationImpl<>( type );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, String name ) {
        return new DeclarationImpl<>( type, name );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, DeclarationSource source ) {
        return new DeclarationImpl<T>( type ).setSource( source );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, String name, DeclarationSource source ) {
        return new DeclarationImpl<T>( type, name ).setSource( source );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, Window window ) {
        return new DeclarationImpl<T>( type ).setWindow( window );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, String name, Window window ) {
        return new DeclarationImpl<T>( type, name ).setWindow( window );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, DeclarationSource source, Window window ) {
        return new DeclarationImpl<T>( type ).setSource( source ).setWindow( window );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, String name, DeclarationSource source, Window window ) {
        return new DeclarationImpl<T>( type, name ).setSource( source ).setWindow( window );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, DomainClassMetadata metadata ) {
        return new DeclarationImpl<T>( type ).setMetadata( metadata );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, DomainClassMetadata metadata, String name ) {
        return new DeclarationImpl<T>( type, name ).setMetadata( metadata );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, DomainClassMetadata metadata, DeclarationSource source ) {
        return new DeclarationImpl<T>( type ).setMetadata( metadata ).setSource( source );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, DomainClassMetadata metadata, String name, DeclarationSource source ) {
        return new DeclarationImpl<T>( type, name ).setMetadata( metadata ).setSource( source );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, DomainClassMetadata metadata, Window window ) {
        return new DeclarationImpl<T>( type ).setMetadata( metadata ).setWindow( window );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, DomainClassMetadata metadata, String name, Window window ) {
        return new DeclarationImpl<T>( type, name ).setMetadata( metadata ).setWindow( window );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, DomainClassMetadata metadata, DeclarationSource source, Window window ) {
        return new DeclarationImpl<T>( type ).setMetadata( metadata ).setSource( source ).setWindow( window );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, DomainClassMetadata metadata, String name, DeclarationSource source, Window window ) {
        return new DeclarationImpl<T>( type, name ).setMetadata( metadata ).setSource( source ).setWindow( window );
    }

    public static <T> Global<T> globalOf( Class<T> type, String pkg ) {
        return new GlobalImpl<>( type, pkg );
    }

    public static <T> Global<T> globalOf( Class<T> type, String pkg, String name ) {
        return new GlobalImpl<>( type, pkg, name );
    }

    public static <T> Global<T> globalOf( TypeReference<T> type, String pkg, String name ) {
        return new GlobalImpl<>( type.getType(), pkg, name );
    }

    public static EntryPoint entryPoint( String name ) {
        return new EntryPointImpl( name );
    }

    public static Window window( Window.Type type, long value ) {
        return new WindowImpl(type, value);
    }

    public static Window window( Window.Type type, long value, TimeUnit timeUnit ) {
        return new WindowImpl(type, value, timeUnit);
    }

    public static <T> WindowReference<T> window( Window.Type type, long value, Class<T> patternType, Predicate1<T>... predicates ) {
        return new WindowReferenceImpl( type, value, patternType, getPredicateForWindow( predicates ) );
    }

    public static <T> WindowReference<T> window( Window.Type type, long value, TimeUnit timeUnit, Class<T> patternType, Predicate1<T>... predicates ) {
        return new WindowReferenceImpl( type, value, timeUnit, patternType, getPredicateForWindow( predicates ) );
    }

    public static <T> WindowReference<T> window( Window.Type type, long value, Class<T> patternType, EntryPoint entryPoint, Predicate1<T>... predicates ) {
        return new WindowReferenceImpl( type, value, patternType, entryPoint, getPredicateForWindow( predicates ) );
    }

    public static <T> WindowReference<T> window( Window.Type type, long value, TimeUnit timeUnit, Class<T> patternType, EntryPoint entryPoint, Predicate1<T>... predicates ) {
        return new WindowReferenceImpl( type, value, timeUnit, patternType, entryPoint, getPredicateForWindow( predicates ) );
    }

    private static <T> Predicate1<T>[] getPredicateForWindow( Predicate1<T>[] predicates ) {
        Predicate1<T>[] ps = new Predicate1[predicates.length];
        for (int i = 0; i < predicates.length; i++) {
            ps[i] = new Predicate1.Impl<>( predicates[i] );
        }
        return ps;
    }

    public static UnitData<?> unitData( String name ) {
        return new UnitDataImpl( name );
    }

    public static <T> UnitData<T> unitData( Class<T> type, String name ) {
        return new UnitDataImpl( type, name );
    }

    public static <T> From<T> from( T value ) {
        return from( () -> value );
    }

    public static <T> From<T> from( Variable<T> variable ) {
        return new From1Impl<>( variable );
    }

    public static <T> From<T> from( Function0<T> provider ) {
        return new From0Impl<>( provider );
    }

    public static <T> From<T> from( Variable<T> variable, Function1<T, ?> provider ) {
        return new From1Impl<>( variable, new Function1.Impl<>(provider) );
    }

    public static <A,B> From<A> from( Variable<A> var1, Variable<B> var2, Function2<A, B, ?> provider ) {
        return new From2Impl<>( var1, var2, new Function2.Impl<>(provider) );
    }

    public static <A,B,C> From<A> from( Variable<A> var1, Variable<B> var2, Variable<C> var3, Function3<A, B, C, ?> provider ) {
        return new From3Impl<>( var1, var2, var3, new Function3.Impl<>(provider) );
    }

    public static <A,B,C,D> From<A> from( Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Function4<A, B, C, D, ?> provider ) {
        return new From4Impl<>(var1, var2, var3, var4, new Function4.Impl<>(provider) );
    }

    public static <A, B, C, D, E> From<A> from(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Function5<A, B, C, D, E, ?> provider) {
        return new From5Impl<>(var1, var2, var3, var4, var5, new Function5.Impl<>(provider));
    }

    public static <A, B, C, D, E, F> From<A> from(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Function6<A, B, C, D, E, F, ?> provider) {
        return new From6Impl<>(var1, var2, var3, var4, var5, var6, new Function6.Impl<>(provider));
    }

    public static <A, B, C, D, E, F, G> From<A> from(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7, Function7<A, B, C, D, E, F, G, ?> provider) {
        return new From7Impl<>(var1, var2, var3, var4, var5, var6, var7, new Function7.Impl<>(provider));
    }

    public static <A, B, C, D, E, F, G, H> From<A> from(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7, Variable<H> var8, Function8<A, B, C, D, E, F, G, H, ?> provider) {
        return new From8Impl<>(var1, var2, var3, var4, var5, var6, var7, var8, new Function8.Impl<>(provider));
    }

    public static <A, B, C, D, E, F, G, H, I> From<A> from(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7, Variable<H> var8, Variable<I> var9, Function9<A, B, C, D, E, F, G, H, I, ?> provider) {
        return new From9Impl<>(var1, var2, var3, var4, var5, var6, var7, var8, var9, new Function9.Impl<>(provider));
    }

    public static <A, B, C, D, E, F, G, H, I, J> From<A> from(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7, Variable<H> var8, Variable<I> var9, Variable<J> var10, Function10<A, B, C, D, E, F, G, H, I, J, ?> provider) {
        return new From10Impl<>(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, new Function10.Impl<>(provider));
    }
    public static <A, B, C, D, E, F, G, H, I, J, K> From<A> from(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7, Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11, Function11<A, B, C, D, E, F, G, H, I, J, K, ?> provider) {
        return new From11Impl<>(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, new Function11.Impl<>(provider));
    }

    public static <T> From<T> reactiveFrom( Variable<T> variable, Function1<T, ?> provider ) {
        return new From1Impl<>( variable, provider, true );
    }

    public static ViewItem or( ViewItemBuilder<?> expression, ViewItemBuilder<?>... expressions) {
        if (expressions == null || expressions.length == 0) {
            return expression.get();
        }
        return new CombinedExprViewItem(Condition.Type.OR, combineExprs( expression, expressions ) );
    }

    public static ViewItem and(ViewItemBuilder<?> expression, ViewItemBuilder<?>... expressions) {
        if (expressions == null || expressions.length == 0) {
            return expression.get();
        }
        return new CombinedExprViewItem(Condition.Type.AND, combineExprs( expression, expressions ) );
    }

    protected static ViewItem[] combineExprs( ViewItemBuilder<?> expression, ViewItemBuilder<?>... expressions ) {
        ViewItem[] andExprs = new ViewItem[expressions.length+1];
        andExprs[0] = expression.get();
        for (int i = 0; i < expressions.length; i++) {
            andExprs[i+1] = expressions[i].get();
        }
        return andExprs;
    }

    // -- Expressions --

    public static Expr1ViewItem<Boolean> expr( String exprId, Variable<Boolean> var ) {
        return new Expr1ViewItemImpl<>( exprId, var, new Predicate1.Impl<>( x -> x != null ? x : false ) );
    }

    public static <T> Expr1ViewItem<T> expr( Variable<T> var, Predicate1<T> predicate ) {
        return new Expr1ViewItemImpl<>( var, new Predicate1.Impl<>(predicate) );
    }

    public static <T, U> Expr2ViewItem<T, U> expr( Variable<T> var1, Variable<U> var2, Predicate2<T, U> predicate) {
        return new Expr2ViewItemImpl<>( var1, var2, new Predicate2.Impl<>(predicate) );
    }

    public static <T, U, X> ExprViewItem<T> expr(Variable<T> var1, Variable<U> var2, Variable<X> var3, Predicate3<T, U, X> predicate) {
        return new Expr3ViewItemImpl<>(var1, var2, var3, new Predicate3.Impl<>(predicate));
    }

    public static <T> Expr1ViewItem<T> expr(String exprId, Variable<T> var, Predicate1<T> predicate) {
        return new Expr1ViewItemImpl<>( exprId, var, new Predicate1.Impl<>(predicate));
    }

    public static <T, U> Expr2ViewItem<T, U> expr( String exprId, Variable<T> var1, Variable<U> var2, Predicate2<T, U> predicate ) {
        return new Expr2ViewItemImpl<>( exprId, var1, var2, new Predicate2.Impl<>(predicate));
    }

    public static <T, U, X> Expr3ViewItem<T, U, X> expr( String exprId, Variable<T> var1, Variable<U> var2, Variable<X> var3, Predicate3<T, U, X> predicate) {
        return new Expr3ViewItemImpl<>(exprId, var1, var2, var3, new Predicate3.Impl<>(predicate));
    }

    public static <A, B, C, D> Expr4ViewItem<A, B, C, D> expr( Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Predicate4<A, B, C, D> predicate) {
        return new Expr4ViewItemImpl<>(var1, var2, var3, var4, new Predicate4.Impl<>(predicate));
    }

    public static <A, B, C, D> Expr4ViewItem<A, B, C, D> expr(String exprId, Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Predicate4<A, B, C, D> predicate) {
        return new Expr4ViewItemImpl<>(exprId, var1, var2, var3, var4, new Predicate4.Impl<>(predicate));
    }

    public static <A, B, C, D, E> Expr5ViewItem<A, B, C, D, E> expr( Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Predicate5<A, B, C, D, E> predicate) {
        return new Expr5ViewItemImpl<>(var1, var2, var3, var4, var5, new Predicate5.Impl<>(predicate));
    }

    public static <A, B, C, D, E> Expr5ViewItem<A, B, C, D, E> expr(String exprId, Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Predicate5<A, B, C, D, E> predicate) {
        return new Expr5ViewItemImpl<>(exprId, var1, var2, var3, var4, var5, new Predicate5.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F> ExprViewItem<A> expr(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Predicate6<A, B, C, D, E, F> predicate) {
        return new Expr6ViewItemImpl<>(var1, var2, var3, var4, var5, var6, new Predicate6.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F> ExprViewItem<A> expr(String exprId, Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Predicate6<A, B, C, D, E, F> predicate) {
        return new Expr6ViewItemImpl<>(exprId, var1, var2, var3, var4, var5, var6, new Predicate6.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G> ExprViewItem<A> expr(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7, Predicate7<A, B, C, D, E, F, G> predicate) {
        return new Expr7ViewItemImpl<>(var1, var2, var3, var4, var5, var6, var7, new Predicate7.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G> ExprViewItem<A> expr(String exprId, Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7, Predicate7<A, B, C, D, E, F, G> predicate) {
        return new Expr7ViewItemImpl<>(exprId, var1, var2, var3, var4, var5, var6, var7, new Predicate7.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H> ExprViewItem<A> expr(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                Variable<H> var8,
                                                                Predicate8<A, B, C, D, E, F, G, H> predicate) {
        return new Expr8ViewItemImpl<>(var1, var2, var3, var4, var5, var6, var7, var8, new Predicate8.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H> ExprViewItem<A> expr(String exprId, Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                Variable<H> var8,
                                                                Predicate8<A, B, C, D, E, F, G, H> predicate) {
        return new Expr8ViewItemImpl<>(exprId, var1, var2, var3, var4, var5, var6, var7, var8, new Predicate8.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H, I> ExprViewItem<A> expr(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                   Variable<H> var8, Variable<I> var9,
                                                                   Predicate9<A, B, C, D, E, F, G, H, I> predicate) {
        return new Expr9ViewItemImpl<>(var1, var2, var3, var4, var5, var6, var7, var8, var9, new Predicate9.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H, I> ExprViewItem<A> expr(String exprId, Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                   Variable<H> var8, Variable<I> var9,
                                                                   Predicate9<A, B, C, D, E, F, G, H, I> predicate) {
        return new Expr9ViewItemImpl<>(exprId, var1, var2, var3, var4, var5, var6, var7, var8, var9, new Predicate9.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H, I, J> ExprViewItem<A> expr(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                      Variable<H> var8, Variable<I> var9, Variable<J> var10,
                                                                      Predicate10<A, B, C, D, E, F, G, H, I, J> predicate) {
        return new Expr10ViewItemImpl<>(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, new Predicate10.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H, I, J> ExprViewItem<A> expr(String exprId, Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                      Variable<H> var8, Variable<I> var9, Variable<J> var10,
                                                                      Predicate10<A, B, C, D, E, F, G, H, I, J> predicate) {
        return new Expr10ViewItemImpl<>(exprId, var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, new Predicate10.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H, I, J, K> ExprViewItem<A> expr(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                         Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11,
                                                                         Predicate11<A, B, C, D, E, F, G, H, I, J, K> predicate) {
        return new Expr11ViewItemImpl<>(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, new Predicate11.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H, I, J, K> ExprViewItem<A> expr(String exprId, Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                         Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11,
                                                                         Predicate11<A, B, C, D, E, F, G, H, I, J, K> predicate) {
        return new Expr11ViewItemImpl<>(exprId, var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, new Predicate11.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H, I, J, K, L> ExprViewItem<A> expr(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                            Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11, Variable<L> var12,
                                                                            Predicate12<A, B, C, D, E, F, G, H, I, J, K, L> predicate) {
        return new Expr12ViewItemImpl<>(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, new Predicate12.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H, I, J, K, L> ExprViewItem<A> expr(String exprId, Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                            Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11, Variable<L> var12,
                                                                            Predicate12<A, B, C, D, E, F, G, H, I, J, K, L> predicate) {
        return new Expr12ViewItemImpl<>(exprId, var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, new Predicate12.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H, I, J, K, L, M> ExprViewItem<A> expr(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                               Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11, Variable<L> var12, Variable<M> var13,
                                                                               Predicate13<A, B, C, D, E, F, G, H, I, J, K, L, M> predicate) {
        return new Expr13ViewItemImpl<>(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, new Predicate13.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H, I, J, K, L, M> ExprViewItem<A> expr(String exprId, Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                               Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11, Variable<L> var12, Variable<M> var13,
                                                                               Predicate13<A, B, C, D, E, F, G, H, I, J, K, L, M> predicate) {
        return new Expr13ViewItemImpl<>(exprId, var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, new Predicate13.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H, I, J, K, L, M, N> ExprViewItem<A> expr(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                                  Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11, Variable<L> var12, Variable<M> var13, Variable<N> var14,
                                                                                  Predicate14<A, B, C, D, E, F, G, H, I, J, K, L, M, N> predicate) {
        return new Expr14ViewItemImpl<>(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, new Predicate14.Impl<>(predicate));
    }
    public static <A, B, C, D, E, F, G, H, I, J, K, L, M, N> ExprViewItem<A> expr(String exprId, Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                                  Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11, Variable<L> var12, Variable<M> var13, Variable<N> var14,
                                                                                  Predicate14<A, B, C, D, E, F, G, H, I, J, K, L, M, N> predicate) {
        return new Expr14ViewItemImpl<>(exprId, var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, new Predicate14.Impl<>(predicate));
    }
    public static <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> ExprViewItem<A> expr(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                                     Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11, Variable<L> var12, Variable<M> var13, Variable<N> var14, Variable<O> var15,
                                                                                     Predicate15<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> predicate) {
        return new Expr15ViewItemImpl<>(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, new Predicate15.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> ExprViewItem<A> expr(String exprId, Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                                     Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11, Variable<L> var12, Variable<M> var13, Variable<N> var14, Variable<O> var15,
                                                                                     Predicate15<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O> predicate) {
        return new Expr15ViewItemImpl<>(exprId, var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, new Predicate15.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> ExprViewItem<A> expr(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                                        Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11, Variable<L> var12, Variable<M> var13, Variable<N> var14, Variable<O> var15, Variable<P> var16,
                                                                                        Predicate16<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> predicate) {
        return new Expr16ViewItemImpl<>(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, new Predicate16.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> ExprViewItem<A> expr(String exprId, Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                                        Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11, Variable<L> var12, Variable<M> var13, Variable<N> var14, Variable<O> var15, Variable<P> var16,
                                                                                        Predicate16<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> predicate) {
        return new Expr16ViewItemImpl<>(exprId, var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, new Predicate16.Impl<>(predicate));
    }

    public static <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> ExprViewItem<A> expr(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                                           Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11, Variable<L> var12, Variable<M> var13, Variable<N> var14, Variable<O> var15, Variable<P> var16, Variable<Q> var17,
                                                                                           Predicate17<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> predicate) {
        return new Expr17ViewItemImpl<>(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, var17, new Predicate17.Impl<>(predicate));
    }
    public static <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> ExprViewItem<A> expr(String exprId, Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                                           Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11, Variable<L> var12, Variable<M> var13, Variable<N> var14, Variable<O> var15, Variable<P> var16, Variable<Q> var17,
                                                                                           Predicate17<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> predicate) {
        return new Expr17ViewItemImpl<>(exprId, var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, var17, new Predicate17.Impl<>(predicate));
    }
    public static <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R> ExprViewItem<A> expr(Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                                              Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11, Variable<L> var12, Variable<M> var13, Variable<N> var14, Variable<O> var15, Variable<P> var16, Variable<Q> var17, Variable<R> var18,
                                                                                              Predicate18<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R> predicate) {
        return new Expr18ViewItemImpl<>(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, var17, var18, new Predicate18.Impl<>(predicate));
    }
    public static <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R> ExprViewItem<A> expr(String exprId, Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<E> var5, Variable<F> var6, Variable<G> var7,
                                                                                              Variable<H> var8, Variable<I> var9, Variable<J> var10, Variable<K> var11, Variable<L> var12, Variable<M> var13, Variable<N> var14, Variable<O> var15, Variable<P> var16, Variable<Q> var17, Variable<R> var18,
                                                                                              Predicate18<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R> predicate) {
        return new Expr18ViewItemImpl<>(exprId, var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, var17, var18, new Predicate18.Impl<>(predicate));
    }

    public static FixedValueItem expr( boolean value ) {
        return new FixedValueItem( null, value );
    }

    public static FixedValueItem expr( String exprId, boolean value ) {
        return new FixedValueItem( exprId, value );
    }

    // -- Existential operator --

    public static ExistentialExprViewItem not( ViewItemBuilder<?> expression, ViewItemBuilder<?>... expressions) {
        return new ExistentialExprViewItem( Condition.Type.NOT, and( expression, expressions) );
    }

    public static ExprViewItem exists(ViewItemBuilder<?> expression, ViewItemBuilder<?>... expressions) {
        return new ExistentialExprViewItem( Condition.Type.EXISTS, and( expression, expressions) );
    }

    public static ExprViewItem forall(ViewItemBuilder<?> expression, ViewItemBuilder<?>... expressions) {
        return new ExistentialExprViewItem( Condition.Type.FORALL, and( expression, expressions) );
    }

    // -- Accumulate Functions --

    public static <T> ExprViewItem<T> accumulate(ViewItem<?> viewItem, AccumulateFunction firstFunction, AccumulateFunction... otherFunctions) {
        return new AccumulateExprViewItem(viewItem, accumulateFunctionsOf( firstFunction, otherFunctions ));
    }

    private static AccumulateFunction[] accumulateFunctionsOf( AccumulateFunction firstFunction, AccumulateFunction[] otherFunctions ) {
        AccumulateFunction[] functions = new AccumulateFunction[otherFunctions.length+1];
        functions[0] = firstFunction;
        System.arraycopy( otherFunctions, 0, functions, 1, otherFunctions.length );
        return functions;
    }

    // Legay case - source is defined in the generated Invoker class
    public static AccumulateFunction accFunction( Class<?> accFunctionClass) {
        return accFunction( accFunctionClass, null );
    }

    public static AccumulateFunction accFunction( Class<?> accFunctionClass, Argument source) {
        return accFunction( classToSupplier( accFunctionClass ), source);
    }

    public static AccumulateFunction accFunction( Supplier<?> functionSupplier ) {
        return accFunction(functionSupplier, null);
    }

    public static AccumulateFunction accFunction( Supplier<?> functionSupplier, Argument source) {
        return new AccumulateFunction(source, functionSupplier);
    }

    private static Supplier<?> classToSupplier(Class<?> cls) {
        return () -> {
            try {
                return cls.getConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException( e );
            }
        };
    }

    // -- GroupBy --

    public static <T, A, K> ExprViewItem<T> groupBy(ViewItem<T> viewItem,
                                                    Variable<A> var1, Variable<K> varKey, Function1<A, K> groupingFunction,
                                                    AccumulateFunction... accFunctions) {
        return new GroupByExprViewItem<>( viewItem, new Variable[] { var1 }, varKey, toFunctionN( groupingFunction ), accFunctions );
    }

    public static <T, A, B, K> ExprViewItem<T> groupBy(ViewItem<T> viewItem,
                                                       Variable<A> var1, Variable<B> var2, Variable<K> varKey, Function2<A, B, K> groupingFunction,
                                                       AccumulateFunction... accFunctions) {
        return new GroupByExprViewItem<>( viewItem, new Variable[] { var1, var2 }, varKey, toFunctionN( groupingFunction ), accFunctions );
    }

    public static <T, A, B, C, K> ExprViewItem<T> groupBy(ViewItem<T> viewItem,
                                                          Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<K> varKey, Function3<A, B, C, K> groupingFunction,
                                                          AccumulateFunction... accFunctions) {
        return new GroupByExprViewItem<>( viewItem, new Variable[] { var1, var2, var3 }, varKey, toFunctionN( groupingFunction ), accFunctions );
    }

    public static <T, A, B, C, D, K> ExprViewItem<T> groupBy(ViewItem<T> viewItem,
                                                             Variable<A> var1, Variable<B> var2, Variable<C> var3, Variable<D> var4, Variable<K> varKey, Function4<A, B, C, D, K> groupingFunction,
                                                             AccumulateFunction... accFunctions) {
        return new GroupByExprViewItem<>( viewItem, new Variable[] { var1, var2, var3, var4 }, varKey, toFunctionN( groupingFunction ), accFunctions );
    }

    // -- Temporal Constraints --

    public static TemporalPredicate not(TemporalPredicate predicate) {
        return (( AbstractTemporalPredicate ) predicate).setNegated( true );
    }

    public static TemporalPredicate after() {
        return new AfterPredicate();
    }

    public static TemporalPredicate after( long lowerBound, long upperBound ) {
        return after(lowerBound, TimeUnit.MILLISECONDS, upperBound, TimeUnit.MILLISECONDS);
    }

    public static TemporalPredicate after( long lowerBound, TimeUnit lowerUnit ) {
        return after(lowerBound, lowerUnit, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    public static TemporalPredicate after( long lowerBound, TimeUnit lowerUnit, long upperBound, TimeUnit upperUnit ) {
        return new AfterPredicate( lowerBound, lowerUnit, upperBound, upperUnit );
    }

    public static TemporalPredicate before() {
        return new BeforePredicate();
    }

    public static TemporalPredicate before(long lowerBound, long upperBound) {
        return before(lowerBound, TimeUnit.MILLISECONDS, upperBound, TimeUnit.MILLISECONDS);
    }

    public static TemporalPredicate before( long lowerBound, TimeUnit lowerUnit ) {
        return before(lowerBound, lowerUnit, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    public static TemporalPredicate before( long lowerBound, TimeUnit lowerUnit, long upperBound, TimeUnit upperUnit ) {
        return new BeforePredicate( lowerBound, lowerUnit, upperBound, upperUnit );
    }

    public static TemporalPredicate coincides() {
        return coincides( 0, TimeUnit.MILLISECONDS );
    }

    public static TemporalPredicate coincides( long dev, TimeUnit devUnit ) {
        return new CoincidesPredicate( dev, devUnit );
    }

    public static TemporalPredicate coincides( long startDev, TimeUnit startDevUnit, long endDev, TimeUnit endDevUnit ) {
        return new CoincidesPredicate( startDev, startDevUnit, endDev, endDevUnit );
    }

    public static TemporalPredicate during() {
        return new DuringPredicate();
    }

    public static TemporalPredicate during(long max, TimeUnit maxUnit) {
        return new DuringPredicate(max, maxUnit);
    }

    public static TemporalPredicate during(long min, TimeUnit minUnit, long max, TimeUnit maxUnit) {
        return new DuringPredicate(min, minUnit, max, maxUnit);
    }

    public static TemporalPredicate finishedby() {
        return new FinishedbyPredicate();
    }

    public static TemporalPredicate finishedby( long dev, TimeUnit devUnit) {
        return new FinishedbyPredicate(dev, devUnit );
    }

    public static TemporalPredicate finishes() {
        return new FinishesPredicate();
    }

    public static TemporalPredicate finishes( long dev, TimeUnit devUnit) {
        return new FinishesPredicate(dev, devUnit);
    }

    public static TemporalPredicate includes() {
        return new IncludesPredicate();
    }

    public static TemporalPredicate includes(long max, TimeUnit maxUnit) {
        return new IncludesPredicate(max, maxUnit);
    }

    public static TemporalPredicate includes(long min, TimeUnit minUnit, long max, TimeUnit maxUnit) {
        return new IncludesPredicate(min, minUnit, max, maxUnit);
    }

    public static TemporalPredicate metby() {
        return new MetbyPredicate();
    }

    public static TemporalPredicate metby( long dev, TimeUnit devUnit ) {
        return new MetbyPredicate(dev, devUnit );
    }

    public static TemporalPredicate meets() {
        return new MeetsPredicate();
    }

    public static TemporalPredicate meets( long dev, TimeUnit devUnit ) {
        return new MeetsPredicate(dev, devUnit );
    }

    public static TemporalPredicate overlappedby() {
        return new OverlappedbyPredicate();
    }

    public static TemporalPredicate overlappedby( long dev, TimeUnit devUnit ) {
        return new OverlappedbyPredicate(dev, devUnit);
    }

    public static TemporalPredicate overlappedby( long minDev, TimeUnit minDevTimeUnit, long maxDev, TimeUnit maxDevTimeUnit ) {
        return new OverlappedbyPredicate(minDev, minDevTimeUnit, maxDev, maxDevTimeUnit);
    }

    public static TemporalPredicate overlaps() {
        return new OverlapsPredicate();
    }

    public static TemporalPredicate overlaps( long maxDev, TimeUnit maxDevTimeUnit ) {
        return new OverlapsPredicate(maxDev, maxDevTimeUnit);
    }

    public static TemporalPredicate overlaps( long minDev, TimeUnit minDevTimeUnit, long maxDev, TimeUnit maxDevTimeUnit ) {
        return new OverlapsPredicate(minDev, minDevTimeUnit, maxDev, maxDevTimeUnit);
    }

    public static TemporalPredicate startedby() {
        return new StartedbyPredicate();
    }

    public static TemporalPredicate startedby( long dev, TimeUnit devUnit) {
        return new StartedbyPredicate(dev, devUnit);
    }

    public static TemporalPredicate starts() {
        return new StartsPredicate();
    }

    public static TemporalPredicate starts( long dev, TimeUnit devUnit) {
        return new StartsPredicate(dev, devUnit);
    }

    // -- RHS --

    public static ConsequenceBuilder._0 execute( Block0 block) {
        return new ConsequenceBuilder._0(block);
    }

    public static ConsequenceBuilder._0 execute(Block1<Drools> block) {
        return new ConsequenceBuilder._0(block);
    }

    public static ConsequenceBuilder._0 executeScript(String language, Class<?> ruleClass, String script) {
        return new ConsequenceBuilder._0(language, ruleClass, script);
    }

    public static <T1> ConsequenceBuilder._1<T1> on(Variable<T1> arg1) {
        return new ConsequenceBuilder._1(arg1);
    }

    public static <T1, T2> ConsequenceBuilder._2<T1, T2> on(Variable<T1> arg1, Variable<T2> arg2) {
        return new ConsequenceBuilder._2(arg1, arg2);
    }

    public static <T1, T2, T3> ConsequenceBuilder._3<T1, T2, T3> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3) {
        return new ConsequenceBuilder._3(arg1, arg2, arg3);
    }

    public static <T1, T2, T3, T4> ConsequenceBuilder._4<T1, T2, T3, T4> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4) {
        return new ConsequenceBuilder._4(arg1, arg2, arg3, arg4);
    }

    public static <T1, T2, T3, T4, T5> ConsequenceBuilder._5<T1, T2, T3, T4, T5> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5) {
        return new ConsequenceBuilder._5(arg1, arg2, arg3, arg4, arg5);
    }

    public static <T1, T2, T3, T4, T5, T6> ConsequenceBuilder._6<T1, T2, T3, T4, T5, T6> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6) {
        return new ConsequenceBuilder._6(arg1, arg2, arg3, arg4, arg5, arg6);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> ConsequenceBuilder._7<T1, T2, T3, T4, T5, T6, T7> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7) {
        return new ConsequenceBuilder._7(arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> ConsequenceBuilder._8<T1, T2, T3, T4, T5, T6, T7, T8> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8) {
        return new ConsequenceBuilder._8(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> ConsequenceBuilder._9<T1, T2, T3, T4, T5, T6, T7, T8, T9> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9) {
        return new ConsequenceBuilder._9(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> ConsequenceBuilder._10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10) {
        return new ConsequenceBuilder._10(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> ConsequenceBuilder._11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11) {
        return new ConsequenceBuilder._11(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> ConsequenceBuilder._12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12) {
        return new ConsequenceBuilder._12(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> ConsequenceBuilder._13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13) {
        return new ConsequenceBuilder._13(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> ConsequenceBuilder._14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14) {
        return new ConsequenceBuilder._14(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> ConsequenceBuilder._15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15) {
        return new ConsequenceBuilder._15(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> ConsequenceBuilder._16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15, Variable<T16> arg16) {
        return new ConsequenceBuilder._16(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> ConsequenceBuilder._17<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15, Variable<T16> arg16, Variable<T17> arg17) {
        return new ConsequenceBuilder._17(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> ConsequenceBuilder._18<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15, Variable<T16> arg16, Variable<T17> arg17, Variable<T18> arg18) {
        return new ConsequenceBuilder._18(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> ConsequenceBuilder._19<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15, Variable<T16> arg16, Variable<T17> arg17, Variable<T18> arg18, Variable<T19> arg19) {
        return new ConsequenceBuilder._19(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> ConsequenceBuilder._20<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15, Variable<T16> arg16, Variable<T17> arg17, Variable<T18> arg18, Variable<T19> arg19, Variable<T20> arg20) {
        return new ConsequenceBuilder._20(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21> ConsequenceBuilder._21<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15, Variable<T16> arg16, Variable<T17> arg17, Variable<T18> arg18, Variable<T19> arg19, Variable<T20> arg20, Variable<T21> arg21) {
        return new ConsequenceBuilder._21(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22> ConsequenceBuilder._22<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15, Variable<T16> arg16, Variable<T17> arg17, Variable<T18> arg18, Variable<T19> arg19, Variable<T20> arg20, Variable<T21> arg21, Variable<T22> arg22) {
        return new ConsequenceBuilder._22(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23> ConsequenceBuilder._23<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15, Variable<T16> arg16, Variable<T17> arg17, Variable<T18> arg18, Variable<T19> arg19, Variable<T20> arg20, Variable<T21> arg21, Variable<T22> arg22, Variable<T23> arg23) {
        return new ConsequenceBuilder._23(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22, arg23);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23, T24> ConsequenceBuilder._24<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23, T24> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15, Variable<T16> arg16, Variable<T17> arg17, Variable<T18> arg18, Variable<T19> arg19, Variable<T20> arg20, Variable<T21> arg21, Variable<T22> arg22, Variable<T23> arg23, Variable<T24> arg24) {
        return new ConsequenceBuilder._24(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22, arg23, arg24);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23, T24, T25>  ConsequenceBuilder._25<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23, T24, T25>  on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15, Variable<T16> arg16, Variable<T17> arg17, Variable<T18> arg18, Variable<T19> arg19, Variable<T20> arg20, Variable<T21> arg21, Variable<T22> arg22, Variable<T23> arg23, Variable<T24> arg24, Variable<T25> arg25) {
        return new ConsequenceBuilder._25(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22, arg23, arg24, arg25);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23, T24, T25, T26> ConsequenceBuilder._26<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23, T24, T25, T26> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15, Variable<T16> arg16, Variable<T17> arg17, Variable<T18> arg18, Variable<T19> arg19, Variable<T20> arg20, Variable<T21> arg21, Variable<T22> arg22, Variable<T23> arg23, Variable<T24> arg24, Variable<T25> arg25, Variable<T26> arg26) {
        return new ConsequenceBuilder._26(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22, arg23, arg24, arg25, arg26);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23, T24, T25, T26, T27> ConsequenceBuilder._27<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23, T24, T25, T26, T27> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15, Variable<T16> arg16, Variable<T17> arg17, Variable<T18> arg18, Variable<T19> arg19, Variable<T20> arg20, Variable<T21> arg21, Variable<T22> arg22, Variable<T23> arg23, Variable<T24> arg24, Variable<T25> arg25, Variable<T26> arg26, Variable<T27> arg27) {
        return new ConsequenceBuilder._27(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22, arg23, arg24, arg25, arg26, arg27);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23, T24, T25, T26, T27, T28> ConsequenceBuilder._28<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23, T24, T25, T26, T27, T28> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15, Variable<T16> arg16, Variable<T17> arg17, Variable<T18> arg18, Variable<T19> arg19, Variable<T20> arg20, Variable<T21> arg21, Variable<T22> arg22, Variable<T23> arg23, Variable<T24> arg24, Variable<T25> arg25, Variable<T26> arg26, Variable<T27> arg27, Variable<T28> arg28) {
        return new ConsequenceBuilder._28(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22, arg23, arg24, arg25, arg26, arg27, arg28);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23, T24, T25, T26, T27, T28, T29> ConsequenceBuilder._29<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23, T24, T25, T26, T27, T28, T29> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15, Variable<T16> arg16, Variable<T17> arg17, Variable<T18> arg18, Variable<T19> arg19, Variable<T20> arg20, Variable<T21> arg21, Variable<T22> arg22, Variable<T23> arg23, Variable<T24> arg24, Variable<T25> arg25, Variable<T26> arg26, Variable<T27> arg27, Variable<T28> arg28, Variable<T29> arg29) {
        return new ConsequenceBuilder._29(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22, arg23, arg24, arg25, arg26, arg27, arg28, arg29);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23, T24, T25, T26, T27, T28, T29, T30> ConsequenceBuilder._30<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T23, T24, T25, T26, T27, T28, T29, T30> on(Variable<T1> arg1, Variable<T2> arg2, Variable<T3> arg3, Variable<T4> arg4, Variable<T5> arg5, Variable<T6> arg6, Variable<T7> arg7, Variable<T8> arg8, Variable<T9> arg9, Variable<T10> arg10, Variable<T11> arg11, Variable<T12> arg12, Variable<T13> arg13, Variable<T14> arg14, Variable<T15> arg15, Variable<T16> arg16, Variable<T17> arg17, Variable<T18> arg18, Variable<T19> arg19, Variable<T20> arg20, Variable<T21> arg21, Variable<T22> arg22, Variable<T23> arg23, Variable<T24> arg24, Variable<T25> arg25, Variable<T26> arg26, Variable<T27> arg27, Variable<T28> arg28, Variable<T29> arg29, Variable<T30> arg30) {
        return new ConsequenceBuilder._30(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22, arg23, arg24, arg25, arg26, arg27, arg28, arg29, arg30);
    }


    public static ConsequenceBuilder._N on(Variable... declarations) {
        return new ConsequenceBuilder._N(declarations);
    }

    public static <T> Value<T> valueOf(T value) {
        return new ValueImpl<>( value );
    }


    public static boolean eval( String op, Object obj, Object... args ) {
        return eval(Operator.Register.getOperator(op ), obj, args );
    }

    public static boolean eval( Operator op, Object obj, Object... args ) {
        try {
            return op.test( obj, args );
        } catch (Exception e) {
            throw new RuntimeException( e );
        }
    }

    public static <R> DynamicValueSupplier<R> supply( R value ) {
        return new DynamicValueSupplier._0( () -> value );
    }

    public static <R> DynamicValueSupplier<R> supply( Function0<R> f ) {
        return new DynamicValueSupplier._0( f );
    }

    public static <A, R> DynamicValueSupplier<R> supply( Variable<A> var1, Function1<A, R> f ) {
        return new DynamicValueSupplier._1( var1, f );
    }

    public static <A, B, R> DynamicValueSupplier<R> supply( Variable<A> var1, Variable<B> var2, Function2<A, B, R> f ) {
        return new DynamicValueSupplier._2( var1, var2, f );
    }
}
