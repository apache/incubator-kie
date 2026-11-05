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
package org.drools.base.rule.consequence;

import java.util.Collection;
import java.util.Map;

import org.drools.base.beliefsystem.Mode;
import org.drools.base.factmodel.traits.Thing;
import org.drools.base.factmodel.traits.TraitableBean;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.Declaration;
import org.drools.util.bitmask.BitMask;
import org.kie.api.definition.rule.Rule;
import org.kie.api.runtime.Channel;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.Match;
import org.kie.api.runtime.rule.RuleContext;

public interface ConsequenceContext extends RuleContext {

    void reset();

    /**
     * Asserts an object
     *
     * @param object -
     *            the object to be asserted
     */
    FactHandle insert(Object object) ;

    FactHandle insertAsync(Object object );

    /**
     * Asserts an object specifying that it implement the onPropertyChange
     * listener
     *
     * @param object -
     *            the object to be asserted
     * @param dynamic -
     *            specifies the object implements onPropertyChangeListener
     * @deprecated since 5.0.1, the per-instance dynamic flag is a legacy Drools 4 API.
     *             Use {@link #insert(Object)} and declare the fact type with
     *             {@code @propertyChangeSupport} instead.
     */
    @Deprecated(since = "5.0.1", forRemoval = false)
    FactHandle insert(Object object, boolean dynamic);

    FactHandle insertLogical(Object object) ;


    FactHandle insertLogical(Object object, Object value);

    FactHandle insertLogical(Object object, Mode belief) ;

    FactHandle insertLogical(Object object, Mode... beliefs) ;

    FactHandle getFactHandle(Object object);


    FactHandle getFactHandle(FactHandle handle);

    void update(FactHandle handle, Object newObject);

    void update(FactHandle newObject);
    void update(FactHandle newObject, BitMask mask, Class<?> modifiedClass);

    void update(Object newObject);
    void update(Object newObject, BitMask mask, Class<?> modifiedClass);

    /**
     * @deprecated Use delete
     */
    void retract(FactHandle handle) ;

    /**
     * @deprecated Use delete
     */
    void retract(Object handle);

    void delete(Object handle);
    void delete(Object object, FactHandle.State fhState);

    void delete(FactHandle handle);
    void delete(FactHandle handle, FactHandle.State fhState);

    Object get(Declaration declaration);

    /**
     * @return - The rule name
     */
    Rule getRule();

    BaseTuple getTuple();

    Declaration[] getRequiredDeclarations();

    Match getMatch();

    /**
     * This method will be deprecated in a future version.
     * Use {@link #activateRuleFlowGroup(String)} instead.
     */
    void setFocus(String focus);

    /**
     * Activates the given ruleflow-group, equivalent to
     * {@code getKieRuntime().getAgenda().getRuleFlowGroup(name).activate()}.
     */
    void activateRuleFlowGroup(String name);

    EntryPoint getEntryPoint(String id);

    Channel getChannel(String id);

    Map<String, Channel> getChannels();

    Declaration getDeclaration(String identifier);

    void halt();

    ClassLoader getProjectClassLoader();


    <T> T getContext(Class<T> contextClass);

    <T, K> T don( K core, Class<T> trait, boolean logical );

    <T, K> T don( K core, Class<T> trait, Mode... modes );

    <T, K> T don( K core, Class<T> trait );

    <T, K> T don(Thing<K> core, Class<T> trait);

    <T, K> T don(K core, Collection<Class<? extends Thing>> trait, boolean logical);

    <T, K> T don( K core, Collection<Class<? extends Thing>> trait, Mode... modes );

    <T, K> T don( K core, Collection<Class<? extends Thing>> trait );

    <T, K> Thing<K> shed( Thing<K> thing, Class<T> trait );

    <T, K, X extends TraitableBean> Thing<K> shed(TraitableBean<K,X> core, Class<T> trait);

    FactHandle bolster( Object object );

    FactHandle bolster( Object object, Object value );

    default void run(String ruleUnitName) {
        throw new UnsupportedOperationException();
    }

    default void run(Object ruleUnit) {
        throw new UnsupportedOperationException();
    }

    default void run(Class<?> ruleUnitClass) {
        throw new UnsupportedOperationException();
    }

    default void guard(Object ruleUnit) {
        throw new UnsupportedOperationException();
    }

    default void guard(Class<?> ruleUnitClass) {
        throw new UnsupportedOperationException();
    }
}
