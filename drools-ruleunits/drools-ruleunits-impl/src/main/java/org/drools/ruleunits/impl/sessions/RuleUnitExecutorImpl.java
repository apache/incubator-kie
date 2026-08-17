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
package org.drools.ruleunits.impl.sessions;

import org.drools.base.beliefsystem.Mode;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.factmodel.traits.Thing;
import org.drools.base.factmodel.traits.TraitableBean;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.core.EntryPointsManager;
import org.drools.core.QueryResultsImpl;
import org.drools.core.RuleSessionConfiguration;
import org.drools.core.SessionConfiguration;
import org.drools.core.WorkingMemory;
import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.base.CalendarsImpl;
import org.drools.core.base.DroolsQueryImpl;
import org.drools.core.base.MapGlobalResolver;
import org.drools.core.base.NonCloningQueryViewListener;
import org.drools.core.base.QueryRowWithSubruleIndex;
import org.drools.core.common.ActivationsManager;
import org.drools.core.common.ConcurrentNodeMemories;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemoryEntryPoint;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.NodeMemories;
import org.drools.core.common.PhreakPropagationContext;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.PropagationContextFactory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.SegmentMemorySupport;
import org.drools.core.common.SuperCacheFixer;
import org.drools.core.event.AgendaEventSupport;
import org.drools.core.event.RuleEventListenerSupport;
import org.drools.core.event.RuleRuntimeEventSupport;
import org.drools.core.impl.ActivationsManagerImpl;
import org.drools.core.impl.InternalRuleBase;
import org.drools.base.phreak.PropagationEntry;
import org.drools.core.phreak.actions.ExecuteQuery;
import org.drools.core.phreak.RuleNetworkEvaluator;
import org.drools.core.phreak.RuleNetworkEvaluatorImpl;
import org.drools.core.phreak.SegmentMemorySupportImpl;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.RuntimeComponentFactory;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.Tuple;
import org.drools.core.rule.accessor.FactHandleFactory;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.core.rule.consequence.KnowledgeHelper;
import org.drools.core.time.TimerService;
import org.drools.util.bitmask.BitMask;
import org.drools.kiesession.consequence.DefaultKnowledgeHelper;
import org.drools.kiesession.consequence.StatefulKnowledgeSessionForRHS;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.ruleunits.api.RuleUnits;
import org.drools.ruleunits.impl.facthandles.RuleUnitDefaultFactHandle;
import org.kie.api.KieBase;
import org.kie.api.runtime.Calendars;
import org.kie.api.runtime.Channel;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.Match;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.time.SessionClock;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.drools.base.base.ClassObjectType.InitialFact_ObjectType;

public class RuleUnitExecutorImpl implements ReteEvaluator {

    private final RuleRuntimeEventSupport ruleRuntimeEventSupport = new RuleRuntimeEventSupport();
    private final AtomicLong propagationIdCounter = new AtomicLong(1);

    private final InternalRuleBase ruleBase;
    private final long identifier;

    private final SessionConfiguration sessionConfiguration;
    private final FactHandleFactory handleFactory;

    private final NodeMemories nodeMemories;
    
    private final SegmentMemorySupport segmentMemorySupport;

    private final ActivationsManager activationsManager;
    private final EntryPointsManager entryPointsManager;

    private final RuleEventListenerSupport ruleEventListenerSupport = new RuleEventListenerSupport();

    private final GlobalResolver globalResolver = new MapGlobalResolver();

    private final TimerService timerService;
    
    private final RuleNetworkEvaluator ruleNetworkEvaluator;
    
    private Calendars calendars;

    private RuleUnits ruleUnits;

    private boolean tmsEnabled;

    public RuleUnitExecutorImpl(InternalRuleBase knowledgeBase) {
        this(knowledgeBase, knowledgeBase.getSessionConfiguration().as(SessionConfiguration.KEY));
    }

    public RuleUnitExecutorImpl(InternalRuleBase knowledgeBase, SessionConfiguration sessionConfiguration) {
        this.ruleBase = knowledgeBase;
        this.identifier = ((InternalKnowledgeBase) ruleBase).nextWorkingMemoryCounter();
        this.sessionConfiguration = sessionConfiguration;

        this.handleFactory = knowledgeBase.newFactHandleFactory();
        this.nodeMemories = new ConcurrentNodeMemories(ruleBase, this);

        this.activationsManager = new ActivationsManagerImpl(ruleBase, this, handleFactory);
        this.entryPointsManager = RuntimeComponentFactory.get().getEntryPointFactory().createEntryPointsManager(ruleBase, this, handleFactory);
        
        this.segmentMemorySupport = new SegmentMemorySupportImpl(nodeMemories, ruleBase.getSegmentPrototypeRegistry(), entryPointsManager.getDefaultEntryPoint());
        this.timerService = sessionConfiguration.createTimerService();
        this.ruleNetworkEvaluator = new RuleNetworkEvaluatorImpl(this, nodeMemories, segmentMemorySupport);

        initInitialFact();
    }

    private void initInitialFact() {
        WorkingMemoryEntryPoint defaultEntryPoint = entryPointsManager.getDefaultEntryPoint();
        InternalFactHandle handle = handleFactory.newInitialFactHandle(defaultEntryPoint);

        ObjectTypeNode otn = defaultEntryPoint.getEntryPointNode().getObjectTypeNodes().get( InitialFact_ObjectType );
        if (otn != null) {
            PropagationContextFactory ctxFact = RuntimeComponentFactory.get().getPropagationContextFactory();
            PropagationContext pctx = ctxFact.createPropagationContext( 0, PropagationContext.Type.INSERTION, null,
                    null, handle, defaultEntryPoint.getEntryPoint(), null );
            otn.assertInitialFact( handle, pctx, this );
        }
    }

    @Override
    public long getIdentifier() {
        return this.identifier;
    }

    @Override
    public RuleNetworkEvaluator getRuleNetworkEvaluator() {
        return ruleNetworkEvaluator;
    }
    
    @Override
    public ActivationsManager getActivationsManager() {
        return activationsManager;
    }

    @Override
    public InternalRuleBase getKnowledgeBase() {
        return ruleBase;
    }

    @Override
    public InternalRuleBase getRuleBase() {
        return ruleBase;
    }

    @Override
    public Collection<? extends EntryPoint> getEntryPoints() {
        return this.entryPointsManager.getEntryPoints();
    }

    @Override
    public WorkingMemoryEntryPoint getEntryPoint(String name) {
        return entryPointsManager.getEntryPoint(name);
    }

    @Override
    public <T extends Memory> T getNodeMemory(MemoryFactory<T> node) {
        return nodeMemories.getNodeMemory( node );
    }
    
    @Override
    public SegmentMemorySupport getSegmentMemorySupport() {
        return segmentMemorySupport;
    }

    @Override
    public NodeMemories getNodeMemories() {
        return nodeMemories;
    }

    @Override
    public GlobalResolver getGlobalResolver() {
        return globalResolver;
    }

    @Override
    public FactHandleFactory getFactHandleFactory() {
        return handleFactory;
    }

    @Override
    public InternalFactHandle getFactHandle(Object object) {
        return (InternalFactHandle) this.entryPointsManager.getDefaultEntryPoint().getFactHandle(object);
    }

    @Override
    public TimerService getTimerService() {
        return timerService;
    }

    @Override
    public void addPropagation(PropagationEntry<ReteEvaluator> propagationEntry) {
        activationsManager.addPropagation( propagationEntry );
    }

    @Override
    public long getNextPropagationIdCounter() {
        return this.propagationIdCounter.incrementAndGet();
    }

    @Override
    public SessionConfiguration getSessionConfiguration() {
        return sessionConfiguration;
    }

    @Override
    public RuleSessionConfiguration getRuleSessionConfiguration() {
        return sessionConfiguration.as(RuleSessionConfiguration.KEY);
    }

    @Override
    public RuleEventListenerSupport getRuleEventSupport() {
        return ruleEventListenerSupport;
    }

    @Override
    public RuleRuntimeEventSupport getRuleRuntimeEventSupport() {
        return ruleRuntimeEventSupport;
    }

    @Override
    public AgendaEventSupport getAgendaEventSupport() {
        return activationsManager.getAgendaEventSupport();
    }


    @Override
    public long getCurrentTime() {
        return timerService.getCurrentTime();
    }

    @Override
    public Calendars getCalendars() {
        if (this.calendars == null) {
            this.calendars = new CalendarsImpl();
        }
        return this.calendars;
    }

    @Override
    public SessionClock getSessionClock() {
        return (SessionClock) this.timerService;
    }

    @Override
    public int fireAllRules() {
        return fireAllRules( null, -1 );
    }

    @Override
    public int fireAllRules(int fireLimit) {
        return fireAllRules( null, fireLimit );
    }

    @Override
    public int fireAllRules(final AgendaFilter agendaFilter) {
        return fireAllRules( agendaFilter, -1 );
    }

    @Override
    public int fireAllRules(final AgendaFilter agendaFilter, int fireLimit) {
        try {
            startOperation(InternalOperationType.FIRE);
            return this.activationsManager.fireAllRules( agendaFilter, fireLimit );
        } finally {
            endOperation(InternalOperationType.FIRE);
        }
    }

    @Override
    public FactHandle insert(Object object) {
        return getDefaultEntryPoint().insert(object);
    }

    @Override
    public void dispose() {
        for (WorkingMemoryEntryPoint ep : this.entryPointsManager.getEntryPoints()) {
            ep.dispose();
        }

        this.activationsManager.getAgendaEventSupport().clear();
        this.ruleRuntimeEventSupport.clear();
        this.ruleEventListenerSupport.clear();

        this.timerService.shutdown();
    }

    @Override
    public QueryResults getQueryResults(String queryName, Object... arguments) {
        activationsManager.flushPropagations();

        DroolsQueryImpl queryObject = new DroolsQueryImpl(queryName, arguments, new NonCloningQueryViewListener(), false );

        InternalFactHandle handle = this.handleFactory.newFactHandle( queryObject, null, this, getDefaultEntryPoint() );

        final PropagationContext pCtx = new PhreakPropagationContext(getNextPropagationIdCounter(), PropagationContext.Type.INSERTION, null, null, handle, getDefaultEntryPointId());

        ExecuteQuery executeQuery = new ExecuteQuery( queryName, queryObject, handle, pCtx, false);
        addPropagation( executeQuery );
        TerminalNode[] terminalNodes = executeQuery.getResult();

        List<Map<String, Declaration>> decls = new ArrayList<>();
        if ( terminalNodes != null ) {
            for ( TerminalNode node : terminalNodes ) {
                decls.add( node.getSubRule().getOuterDeclarations() );
            }
        }

        this.handleFactory.destroyFactHandle( handle);

        return new QueryResultsImpl( (List<QueryRowWithSubruleIndex>) queryObject.getQueryResultCollector().getResults(),
                decls.toArray( new Map[decls.size()] ),
                this,
                ( queryObject.getQuery() != null ) ? queryObject.getQuery().getParameters()  : new Declaration[0] );
    }

    public RuleUnits getRuleUnits() {
        return ruleUnits;
    }

    public void setRuleUnits(RuleUnits ruleUnits) {
        this.ruleUnits = ruleUnits;
    }

    @Override
    public void enableTMS() {
        tmsEnabled = true;
    }

    @Override
    public boolean isTMSEnabled() {
        return tmsEnabled;
    }

	@Override
	public boolean isSequential() {
		return ruleBase.getRuleBaseConfiguration().isSequential();
	}
    
    @Override
    public KnowledgeHelper createKnowledgeHelper() {
        return new RuleUnitKnowledgeHelper((DefaultKnowledgeHelper) ReteEvaluator.super.createKnowledgeHelper(), this);
    }

    public static class RuleUnitKnowledgeHelper implements KnowledgeHelper {

        private final DefaultKnowledgeHelper knowledgeHelper;
        private final RuleUnitExecutorImpl reteEvaluator;

        public RuleUnitKnowledgeHelper(DefaultKnowledgeHelper knowledgeHelper, RuleUnitExecutorImpl reteEvaluator) {
            this.knowledgeHelper = knowledgeHelper;
            this.reteEvaluator = reteEvaluator;
        }

        @Override
        public void run(String ruleUnitName) {
            reteEvaluator.getRuleUnits().getRegisteredInstance(ruleUnitName).fire();
        }

        @Override
        public void update(final FactHandle handle, BitMask mask, Class modifiedClass) {
            InternalFactHandle h = (InternalFactHandle) handle;

            if (h instanceof RuleUnitDefaultFactHandle && ((RuleUnitDefaultFactHandle) h).getDataStore() != null) {
                // This handle has been insert from a datasource, so update it
                ((RuleUnitDefaultFactHandle) h).getDataStore().update((RuleUnitDefaultFactHandle) h,
                        h.getObject(),
                        mask,
                        modifiedClass,
                        knowledgeHelper.getActivation());
                return;
            }

            ((InternalWorkingMemoryEntryPoint) h.getEntryPoint(reteEvaluator)).update(h,
                    ((InternalFactHandle) handle).getObject(),
                    mask,
                    modifiedClass,
                    knowledgeHelper.getActivation());
            if (h.isTraitOrTraitable()) {
                knowledgeHelper.toStatefulKnowledgeSession().updateTraits(h, mask, modifiedClass, knowledgeHelper.getActivation());
            }
        }

        @Override
        public void delete(FactHandle handle, FactHandle.State fhState) {
            InternalFactHandle h = (InternalFactHandle) handle;

            if (h instanceof RuleUnitDefaultFactHandle && ((RuleUnitDefaultFactHandle) h).getDataStore() != null) {
                // This handle has been insert from a datasource, so remove from it
                ((RuleUnitDefaultFactHandle) h).getDataStore().delete((RuleUnitDefaultFactHandle) h,
                                                                      knowledgeHelper.getActivation().getRule(),
                                                                      SuperCacheFixer.asTerminalNode(knowledgeHelper.getActivation().getTuple()),
                                                                      fhState);
                return;
            }

            if (h.isTraiting()) {
                delete(((Thing) h.getObject()).getCore());
                return;
            }

            h.getEntryPoint(reteEvaluator).delete(handle,
                    knowledgeHelper.getActivation().getRule(),
                    SuperCacheFixer.asTerminalNode(knowledgeHelper.getActivation().getTuple()),
                    fhState);
        }

        @Override
        public Declaration[] getRequiredDeclarations() {
            return knowledgeHelper.getRequiredDeclarations();
        }

        @Override
        public RuleImpl getRule() {
            return knowledgeHelper.getRule();
        }

        @Override
        public Tuple getTuple() {
            return knowledgeHelper.getTuple();
        }

        @Override
        public WorkingMemory getWorkingMemory() {
            return knowledgeHelper.getWorkingMemory();
        }

        @Override
        public KieRuntime getKnowledgeRuntime() {
            return knowledgeHelper.getKnowledgeRuntime();
        }

        public StatefulKnowledgeSessionForRHS toStatefulKnowledgeSession() {
            return knowledgeHelper.toStatefulKnowledgeSession();
        }

        @Override
        public InternalMatch getMatch() {
            return knowledgeHelper.getMatch();
        }

        @Override
        public void setFocus(String focus) {
            knowledgeHelper.setFocus(focus);
        }

        @Override
        public Object get(Declaration declaration) {
            return knowledgeHelper.get(declaration);
        }

        @Override
        public Declaration getDeclaration(String identifier) {
            return knowledgeHelper.getDeclaration(identifier);
        }

        @Override
        public void halt() {
            knowledgeHelper.halt();
        }

        @Override
        public EntryPoint getEntryPoint(String id) {
            return knowledgeHelper.getEntryPoint(id);
        }

        @Override
        public Channel getChannel(String id) {
            return knowledgeHelper.getChannel(id);
        }

        @Override
        public Map<String, Channel> getChannels() {
            return knowledgeHelper.getChannels();
        }

        public static InternalFactHandle getFactHandleFromWM(ReteEvaluator reteEvaluator, Object object) {
            return DefaultKnowledgeHelper.getFactHandleFromWM(reteEvaluator, object);
        }

        @Override
        public <T> T getContext(Class<T> contextClass) {
            return knowledgeHelper.getContext(contextClass);
        }

        @Override
        public KieRuntime getKieRuntime() {
            return knowledgeHelper.getKieRuntime();
        }

        public <T, K> T don(Thing<K> core, Class<T> trait, boolean logical, Mode... modes) {
            return knowledgeHelper.don(core, trait, logical, modes);
        }

        @Override
        public <T, K> T don(K core, Class<T> trait) {
            return knowledgeHelper.don(core, trait);
        }

        @Override
        public <T, K> T don(Thing<K> core, Class<T> trait) {
            return knowledgeHelper.don(core, trait);
        }

        @Override
        public <T, K> T don(K core, Collection<Class<? extends Thing>> traits) {
            return knowledgeHelper.don(core, traits);
        }

        @Override
        public <T, K> Thing<K> shed(Thing<K> thing, Class<T> trait) {
            return knowledgeHelper.shed(thing, trait);
        }

        @Override
        public <T, K> T don(K core, Collection<Class<? extends Thing>> traits, Mode... modes) {
            return knowledgeHelper.don(core, traits, modes);
        }

        @Override
        public <T, K> T don(K core, Collection<Class<? extends Thing>> traits, boolean logical) {
            return knowledgeHelper.don(core, traits, logical);
        }

        @Override
        public <T, K> T don(K core, Class<T> trait, boolean logical) {
            return knowledgeHelper.don(core, trait, logical);
        }

        @Override
        public <T, K> T don(K core, Class<T> trait, Mode... modes) {
            return knowledgeHelper.don(core, trait, modes);
        }

        @Override
        public <T, K, X extends TraitableBean> Thing<K> shed(TraitableBean<K, X> core, Class<T> trait) {
            return knowledgeHelper.shed(core, trait);
        }

        @Override
        public ClassLoader getProjectClassLoader() {
            return knowledgeHelper.getProjectClassLoader();
        }

        @Override
        public KieBase getKieBase() {
            return knowledgeHelper.getKieBase();
        }

        @Override
        public void run(Object ruleUnit) {
            knowledgeHelper.run(ruleUnit);
        }

        @Override
        public void run(Class<?> ruleUnitClass) {
            knowledgeHelper.run(ruleUnitClass);
        }

        @Override
        public void guard(Object ruleUnit) {
            knowledgeHelper.guard(ruleUnit);
        }

        @Override
        public void guard(Class<?> ruleUnitClass) {
            knowledgeHelper.guard(ruleUnitClass);
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            knowledgeHelper.readExternal(in);
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            knowledgeHelper.writeExternal(out);
        }

        @Override
        public void setActivation(InternalMatch internalMatch) {
            knowledgeHelper.setActivation(internalMatch);
        }

        public InternalMatch getActivation() {
            return knowledgeHelper.getActivation();
        }

        @Override
        public void reset() {
            knowledgeHelper.reset();
        }

        @Override
        public void blockMatch(Match act) {
            knowledgeHelper.blockMatch(act);
        }

        @Override
        public void unblockAllMatches(Match act) {
            knowledgeHelper.unblockAllMatches(act);
        }

        @Override
        public FactHandle insertAsync(Object object) {
            return knowledgeHelper.insertAsync(object);
        }

        @Override
        public FactHandle insert(Object object) {
            return knowledgeHelper.insert(object);
        }

        @Override
        public FactHandle insert(Object object, boolean dynamic) {
            return knowledgeHelper.insert(object, dynamic);
        }

        @Override
        public FactHandle insertLogical(Object object, Mode belief) {
            return knowledgeHelper.insertLogical(object, belief);
        }

        @Override
        public FactHandle insertLogical(Object object, Mode... beliefs) {
            return knowledgeHelper.insertLogical(object, beliefs);
        }

        @Override
        public FactHandle insertLogical(Object object) {
            return knowledgeHelper.insertLogical(object);
        }

        @Override
        public FactHandle insertLogical(Object object, Object value) {
            return knowledgeHelper.insertLogical(object, value);
        }

        @Override
        public FactHandle insertLogical(EntryPoint ep, Object object) {
            return knowledgeHelper.insertLogical(ep, object);
        }

        @Override
        public FactHandle bolster(Object object) {
            return knowledgeHelper.bolster(object);
        }

        @Override
        public FactHandle bolster(Object object, Object value) {
            return knowledgeHelper.bolster(object, value);
        }

        @Override
        public void cancelMatch(Match act) {
            knowledgeHelper.cancelMatch(act);
        }

        @Override
        public FactHandle getFactHandle(Object object) {
            return knowledgeHelper.getFactHandle(object);
        }

        @Override
        public FactHandle getFactHandle(FactHandle handle) {
            return knowledgeHelper.getFactHandle(handle);
        }

        @Override
        public void update(FactHandle handle, Object newObject) {
            knowledgeHelper.update(handle, newObject);
        }

        @Override
        public void update(FactHandle handle) {
            knowledgeHelper.update(handle);
        }

        @Override
        public void update(Object object) {
            knowledgeHelper.update(object);
        }

        @Override
        public void update(Object object, BitMask mask, Class<?> modifiedClass) {
            knowledgeHelper.update(object, mask, modifiedClass);
        }

        @Override
        public void retract(Object object) {
            knowledgeHelper.retract(object);
        }

        @Override
        public void retract(FactHandle handle) {
            knowledgeHelper.retract(handle);
        }

        @Override
        public void delete(Object object) {
            knowledgeHelper.delete(object);
        }

        @Override
        public void delete(Object object, FactHandle.State fhState) {
            knowledgeHelper.delete(object, fhState);
        }

        @Override
        public void delete(FactHandle handle) {
            knowledgeHelper.delete(handle);
        }
    }
}
