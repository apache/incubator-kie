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
package org.drools.core.common;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;

import org.drools.base.base.ValueResolver;
import org.drools.base.rule.EntryPointId;
import org.drools.core.RuleSessionConfiguration;
import org.drools.core.SessionConfiguration;
import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.event.AgendaEventSupport;
import org.drools.core.event.RuleEventListenerSupport;
import org.drools.core.event.RuleRuntimeEventSupport;
import org.drools.core.impl.InternalRuleBase;
import org.drools.base.phreak.PropagationEntry;
import org.drools.core.phreak.RuleNetworkEvaluator;
import org.drools.core.reteoo.ObjectTypeConf;
import org.drools.core.reteoo.RuntimeComponentFactory;
import org.drools.core.rule.accessor.FactHandleFactory;
import org.drools.core.rule.consequence.KnowledgeHelper;
import org.drools.core.time.TimerService;
import org.drools.core.time.impl.TimerJobInstance;
import org.kie.api.runtime.Calendars;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.time.SessionClock;

public interface ReteEvaluator extends ValueResolver {

    enum InternalOperationType{ FIRE, INSERT, UPDATE, DELETE, SET_GLOBAL }

    long getIdentifier();

    RuleNetworkEvaluator getRuleNetworkEvaluator();
    
    ActivationsManager getActivationsManager();

    InternalRuleBase getKnowledgeBase();

    Collection<? extends EntryPoint> getEntryPoints();

    WorkingMemoryEntryPoint getEntryPoint(String name);
    default EntryPointId getDefaultEntryPointId() {
        return EntryPointId.DEFAULT;
    }
    default WorkingMemoryEntryPoint getDefaultEntryPoint() {
        return getEntryPoint(getDefaultEntryPointId().getEntryPointId());
    }

    <T extends Memory> T getNodeMemory(MemoryFactory<T> node);

    NodeMemories getNodeMemories();
    
    SegmentMemorySupport getSegmentMemorySupport();

    default Object getGlobal(String identifier) {
        return getGlobalResolver().resolveGlobal( identifier );
    }

    default void setGlobal(String identifier, Object value) {
        getGlobalResolver().setGlobal(identifier, value);
    }

    default InternalFactHandle createFactHandle(Object object, ObjectTypeConf conf, WorkingMemoryEntryPoint wmEntryPoint ) {
        return getFactHandleFactory().newFactHandle( object, conf, this, wmEntryPoint );
    }

    FactHandleFactory getFactHandleFactory();

    InternalFactHandle getFactHandle(Object object);

    TimerService getTimerService();

    default Collection<TimerJobInstance> getTimerJobInstances(long id) {
        TimerService timerService = getTimerService();
        return timerService != null ? timerService.getTimerJobInstances(id) : Collections.emptyList();
    }

    void addPropagation(PropagationEntry<ReteEvaluator> propagationEntry);

    long getNextPropagationIdCounter();

    default boolean isThreadSafe() {
        return true;
    }

    SessionConfiguration getSessionConfiguration();

    RuleSessionConfiguration getRuleSessionConfiguration();

    AgendaEventSupport getAgendaEventSupport();

    RuleEventListenerSupport getRuleEventSupport();

    RuleRuntimeEventSupport getRuleRuntimeEventSupport();

    Calendars getCalendars();

    SessionClock getSessionClock();

    boolean isSequential();

    default void startOperation(InternalOperationType operationType) { }
    default void endOperation(InternalOperationType operationType) { }

    boolean isTMSEnabled();
    void enableTMS();

    default KnowledgeHelper createKnowledgeHelper() {
        return RuntimeComponentFactory.get().createKnowledgeHelper(this);
    }

    FactHandle insert(Object object);

    QueryResults getQueryResults(String queryName, Object... arguments);

    void dispose();

    int fireAllRules();
    int fireAllRules(int max);
    int fireAllRules(AgendaFilter agendaFilter);
    int fireAllRules(AgendaFilter agendaFilter, int max);

    default void setWorkingMemoryActionListener(Consumer<PropagationEntry<ReteEvaluator>> listener) {
        throw new UnsupportedOperationException();
    }

    default Consumer<PropagationEntry<ReteEvaluator>> getWorkingMemoryActionListener() {
        return null;
    }

    default void onWorkingMemoryAction(PropagationEntry<ReteEvaluator> entry) {
        Consumer<PropagationEntry<ReteEvaluator>> listener = getWorkingMemoryActionListener();
        if (listener != null) {
            listener.accept(entry);
        }
    }
}
