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
package org.drools.modelcompiler.consequence;

import org.drools.base.base.ValueResolver;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.reteoo.BaseTuple;
import org.drools.core.WorkingMemory;
import org.drools.core.common.InternalWorkingMemoryEntryPoint;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.core.rule.consequence.KnowledgeHelper;
import org.drools.model.BitMask;
import org.drools.model.Channel;
import org.drools.model.Drools;
import org.drools.model.DroolsEntryPoint;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.Match;

import static org.drools.kiesession.entrypoints.NamedEntryPoint.calculateUpdateBitMask;
import static org.drools.modelcompiler.util.EvaluationUtil.adaptBitMask;

public class DroolsImpl implements Drools, org.kie.api.runtime.rule.RuleContext {
    private final KnowledgeHelper knowledgeHelper;
    private final ReteEvaluator reteEvaluator;

    private final FactHandleLookup fhLookup;

    DroolsImpl(KnowledgeHelper knowledgeHelper, ValueResolver valueResolver, FactHandleLookup fhLookup) {
        this.reteEvaluator = (ReteEvaluator) valueResolver;
        this.knowledgeHelper = knowledgeHelper;
        this.fhLookup = fhLookup;
    }

    @Override
    public void insert(Object object) {
        insert( object, false );
    }

    @Deprecated(since = "5.0.1", forRemoval = false)
    @Override
    public void insert(Object object, boolean dynamic) {
        TerminalNode terminalNode = ((InternalMatch)getMatch()).getTerminalNode();
        ((InternalWorkingMemoryEntryPoint)reteEvaluator.getDefaultEntryPoint()).insert(object, dynamic, getRule(), terminalNode);
    }

    @Override
    public void logicalInsert(Object object) {
        knowledgeHelper.insertLogical( object );
    }

    @Override
    public void insertAsync(Object object) {
        ((InternalWorkingMemoryEntryPoint) reteEvaluator.getDefaultEntryPoint()).insertAsync(object);
    }

    @Override
    public RuleImpl getRule() {
        return (RuleImpl) knowledgeHelper.getRule();
    }

    @Override
    public Match getMatch() {
        return knowledgeHelper.getMatch();
    }

    @Override
    public FactHandle insertLogical( Object object ) {
        return knowledgeHelper.insertLogical(object);
    }

    public FactHandle insertLogical( Object object, Object value ) {
        return knowledgeHelper.insertLogical(object, value);
    }

    @Override
    public FactHandle insertLogical( EntryPoint ep, Object object ) {
        return knowledgeHelper.insertLogical(ep, object);
    }

    @Override
    public void blockMatch( Match match ) {
        knowledgeHelper.blockMatch(match);
    }

    @Override
    public void unblockAllMatches( Match match ) {
        knowledgeHelper.unblockAllMatches(match);
    }

    @Override
    public void cancelMatch( Match match ) {
        knowledgeHelper.cancelMatch(match);
    }

    @Override
    public void update(Object object, String... modifiedProperties) {
        org.drools.util.bitmask.BitMask mask = calculateUpdateBitMask(reteEvaluator.getKnowledgeBase(), object, modifiedProperties);
        knowledgeHelper.update( getFactHandleForObject( object ), mask, object.getClass() );
    }

    private FactHandle getFactHandleForObject( Object object ) {
        FactHandle fh = fhLookup.get(object);
        return fh != null ? fh : reteEvaluator.getFactHandle( object );
    }

    @Override
    public void update(Object object, BitMask modifiedProperties ) {
        Class<?> modifiedClass = modifiedProperties.getPatternClass();
        knowledgeHelper.update( getFactHandleForObject( object ), adaptBitMask(modifiedProperties), modifiedClass);
    }

    public void update(FactHandle handle, Object newObject) {
        knowledgeHelper.update( handle, newObject );
    }

    @Override
    public void delete(Object object) {
        knowledgeHelper.delete( getFactHandleForObject( object ) );
    }

    @Override
    public <T> T getRuntime(Class<T> runtimeClass) {
        return (T)knowledgeHelper.getKieRuntime();
    }

    @Override
    public <T> T getContext(Class<T> contextClass) {
        return knowledgeHelper.getContext(contextClass);
    }

    @Override
    public DroolsEntryPoint getEntryPoint( String name) {
        return new DroolsEntryPointImpl( knowledgeHelper.getEntryPoint( name ), fhLookup );
    }

    @Override
    public void halt() {
        knowledgeHelper.halt();
    }

    @Override
    public void setFocus( String focus ) {
        knowledgeHelper.setFocus( focus );
    }

    @Override
    public void activateRuleFlowGroup( String name ) {
        knowledgeHelper.activateRuleFlowGroup( name );
    }

    @Override
    public KieBase getKieBase() {
        return (KieBase) reteEvaluator.getKnowledgeBase();
    }

    @Override
    public KieRuntime getKieRuntime() {
        return knowledgeHelper.getKieRuntime();
    }

    @Override
    public KieRuntime getKnowledgeRuntime() {
        return knowledgeHelper.getKieRuntime();
    }

    public KnowledgeHelper asKnowledgeHelper() {
        return knowledgeHelper;
    }

    @Override
    public Channel getChannel(String name) {
        return new ChannelImpl(knowledgeHelper.getChannel(name));
    }

    // Additional getters for non-exec-model compatibility (DefaultKnowledgeHelper)

    public WorkingMemory getWorkingMemory() {
        return knowledgeHelper.getWorkingMemory();
    }

    public BaseTuple getTuple() {
        return knowledgeHelper.getTuple();
    }
}
