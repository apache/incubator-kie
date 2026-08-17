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
package org.drools.kiesession.session;

import org.drools.base.RuleBase;
import org.drools.base.beliefsystem.Mode;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.factmodel.traits.Thing;
import org.drools.base.factmodel.traits.TraitableBean;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.core.FlowSessionConfiguration;
import org.drools.core.QueryResultsImpl;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.RuleSessionConfiguration;
import org.drools.core.SessionConfiguration;
import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.base.CalendarsImpl;
import org.drools.core.base.DroolsQueryImpl;
import org.drools.core.base.InternalViewChangedEventListener;
import org.drools.core.base.NonCloningQueryViewListener;
import org.drools.core.base.QueryRowWithSubruleIndex;
import org.drools.core.base.StandardQueryViewChangedEventListener;
import org.drools.core.common.ActivationsManager;
import org.drools.core.common.ConcurrentNodeMemories;
import org.drools.core.common.EndOperationListener;
import org.drools.core.common.EventSupport;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.core.common.InternalWorkingMemoryActions;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.NodeMemories;
import org.drools.core.common.ObjectStore;
import org.drools.core.common.ObjectStoreWrapper;
import org.drools.core.common.ObjectTypeConfigurationRegistry;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.PropagationContextFactory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.SegmentMemorySupport;
import org.drools.core.common.SuperCacheFixer;
import org.drools.core.event.AgendaEventSupport;
import org.drools.core.event.RuleEventListenerSupport;
import org.drools.core.event.RuleRuntimeEventSupport;
import org.drools.core.impl.AbstractRuntime;
import org.drools.core.impl.EnvironmentFactory;
import org.drools.core.management.DroolsManagementAgent;
import org.drools.core.marshalling.MarshallerReaderContext;
import org.drools.base.phreak.PropagationEntry;
import org.drools.base.phreak.actions.AbstractPropagationEntry;
import org.drools.core.phreak.actions.ExecuteQuery;
import org.drools.core.phreak.actions.PropagationEntryWithResult;
import org.drools.core.phreak.RuleAgendaItem;
import org.drools.core.phreak.RuleNetworkEvaluator;
import org.drools.core.phreak.RuleNetworkEvaluatorImpl;
import org.drools.core.phreak.SegmentMemorySupportImpl;
import org.drools.core.reteoo.AsyncReceiveNode;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.QueryTerminalNode;
import org.drools.core.reteoo.RuntimeComponentFactory;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.rule.accessor.FactHandleFactory;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.core.runtime.process.InternalProcessRuntime;
import org.drools.core.runtime.rule.impl.LiveQueryImpl;
import org.drools.core.runtime.rule.impl.OpenQueryViewChangedEventListenerAdapter;
import org.drools.core.time.TimerService;
import org.drools.util.bitmask.BitMask;
import org.drools.kiesession.entrypoints.NamedEntryPointsManager;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.kie.api.KieBase;
import org.kie.api.command.BatchExecutionCommand;
import org.kie.api.command.Command;
import org.kie.api.conf.MBeansOption;
import org.kie.api.event.KieRuntimeEventManager;
import org.kie.api.event.kiebase.KieBaseEventListener;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.process.ProcessEventManager;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.marshalling.Marshaller;
import org.kie.api.marshalling.ObjectMarshallingStrategy;
import org.kie.api.runtime.Calendars;
import org.kie.api.runtime.Channel;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.ExecutableRunner;
import org.kie.api.runtime.Globals;
import org.kie.api.runtime.KieRuntimeFactory;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.RequestContext;
import org.kie.api.runtime.conf.QueryListenerOption;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.LiveQuery;
import org.kie.api.runtime.rule.ViewChangedEventListener;
import org.kie.api.time.SessionClock;
import org.kie.internal.event.rule.RuleEventListener;
import org.kie.internal.event.rule.RuleEventManager;
import org.kie.internal.marshalling.MarshallerFactory;
import org.kie.internal.process.CorrelationAwareProcessRuntime;
import org.kie.internal.process.CorrelationKey;
import org.kie.internal.runtime.StatefulKnowledgeSession;

import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;
import static org.drools.base.base.ClassObjectType.InitialFact_ObjectType;
import static org.drools.base.reteoo.PropertySpecificUtil.allSetButTraitBitMask;
import static org.drools.util.ClassUtils.rawType;



public class StatefulKnowledgeSessionImpl extends AbstractRuntime
        implements
        StatefulKnowledgeSession,
        WorkingMemoryEntryPoint,
        InternalKnowledgeRuntime,
        KieSession,
        KieRuntimeEventManager,
        InternalWorkingMemoryActions,
        EventSupport,
        RuleEventManager,
        ProcessEventManager,
        CorrelationAwareProcessRuntime,
        Externalizable {

    public static final String ERRORMSG = "Illegal method call. This session was previously disposed.";

    public static final String DEFAULT_RULE_UNIT = "DEFAULT_RULE_UNIT";

    private static final long serialVersionUID = 510l;
    public    byte[] bytes;
    protected Long    id;

    private RuleNetworkEvaluator ruleNetworkEvaluator;
    
    /** The actual memory for the <code>JoinNode</code>s. */
    private NodeMemories nodeMemories;

    private SegmentMemorySupport segmentMemorySupport;
    
    /** Global values which are associated with this memory. */
    protected GlobalResolver globalResolver;

    protected Calendars   calendars;

    /** The eventSupport */
    protected RuleRuntimeEventSupport ruleRuntimeEventSupport;

    protected RuleEventListenerSupport ruleEventListenerSupport;

    protected AgendaEventSupport agendaEventSupport;

    protected List<KieBaseEventListener> kieBaseEventListeners;

    /** The <code>RuleBase</code> with which this memory is associated. */
    protected transient InternalKnowledgeBase kBase;

    protected FactHandleFactory handleFactory;

    /** Rule-firing agenda. */
    protected InternalAgenda agenda;

    protected ReentrantLock lock;

    /**
     * This must be thread safe as it is incremented and read via different
     * EntryPoints
     */
    private AtomicLong propagationIdCounter;

    private boolean sequential;

    private WorkItemManager workItemManager;

    private volatile TimerService timerService;

    protected InternalFactHandle initialFactHandle;

    private PropagationContextFactory pctxFactory;

    protected KieSessionConfiguration config;

    protected RuleSessionConfiguration ruleSessionConfig;

    private Map<String, Channel> channels;

    private Environment environment;

    // this is the timestamp of the end of the last operation, based on the session clock,
    // or -1 if there are operation being executed at this moment
    private AtomicLong lastIdleTimestamp;

    private volatile InternalProcessRuntime processRuntime;

    private transient KieRuntimeFactory runtimeFactory;

    private AtomicBoolean mbeanRegistered = new AtomicBoolean(false);
    private DroolsManagementAgent.CBSKey mbeanRegisteredCBSKey;

    private boolean stateless;

    private List<AsyncReceiveNode.AsyncReceiveMemory> receiveNodeMemories;

    private transient StatefulSessionPool pool;
    private transient boolean alive = true;

    // this is a counter of concurrent operations happening. When this counter is zero,
    // the engine is idle.
    private final AtomicInteger opCounter = new AtomicInteger(0);

    private NamedEntryPointsManager entryPointsManager;

    private Consumer<PropagationEntry<ReteEvaluator>> workingMemoryActionListener;

    private boolean tmsEnabled;
    
    

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------

    public StatefulKnowledgeSessionImpl() {

    }


    public StatefulKnowledgeSessionImpl(final long id,
                                        final InternalKnowledgeBase kBase) {
        this(id,
             kBase,
             true,
             (SessionConfiguration) kBase.getSessionConfiguration(),
             EnvironmentFactory.newEnvironment());
    }

    public StatefulKnowledgeSessionImpl(final long id,
                                        final InternalKnowledgeBase kBase,
                                        boolean initInitFactHandle,
                                        final SessionConfiguration config,
                                        final Environment environment) {
        this(id,
             kBase,
             kBase != null ? kBase.newFactHandleFactory() : null,
             initInitFactHandle,
             1,
             config,
             environment,
             new RuleRuntimeEventSupport(),
             new AgendaEventSupport(),
             new RuleEventListenerSupport());
    }

    public StatefulKnowledgeSessionImpl(final long id,
                                        final InternalKnowledgeBase kBase,
                                        final FactHandleFactory handleFactory,
                                        final long propagationContext,
                                        final SessionConfiguration config,
                                        final Environment environment) {
        this(id,
             kBase,
             handleFactory,
             false,
             propagationContext,
             config,
             environment,
             new RuleRuntimeEventSupport(),
             new AgendaEventSupport(),
             new RuleEventListenerSupport());
    }


    private StatefulKnowledgeSessionImpl(final long id,
                                         final InternalKnowledgeBase kBase,
                                         final FactHandleFactory handleFactory,
                                         final boolean initInitFactHandle,
                                         final long propagationContext,
                                         final SessionConfiguration config,
                                         final Environment environment,
                                         final RuleRuntimeEventSupport workingMemoryEventSupport,
                                         final AgendaEventSupport agendaEventSupport,
                                         final RuleEventListenerSupport ruleEventListenerSupport) {
        this.id = id;
        this.kBase = kBase;
        this.handleFactory = handleFactory;
        this.ruleRuntimeEventSupport = workingMemoryEventSupport;
        this.agendaEventSupport = agendaEventSupport;
        this.ruleEventListenerSupport = ruleEventListenerSupport;

        this.propagationIdCounter = new AtomicLong(propagationContext);
        this.config = config;
        this.ruleSessionConfig = config.as(RuleSessionConfiguration.KEY);
        this.environment = environment;

        this.propagationIdCounter = new AtomicLong( propagationContext);

        this.kieBaseEventListeners = new ArrayList<>();
        this.lock = new ReentrantLock();

        this.lastIdleTimestamp = new AtomicLong(-1);

        this.nodeMemories = new ConcurrentNodeMemories(kBase, this);
        registerReceiveNodes(kBase.getReceiveNodes());

        RuleBaseConfiguration conf = kBase.getRuleBaseConfiguration();
        this.pctxFactory = RuntimeComponentFactory.get().getPropagationContextFactory();

        this.agenda = RuntimeComponentFactory.get().getAgendaFactory( config ).createAgenda(kBase, this, handleFactory);

        this.entryPointsManager = (NamedEntryPointsManager) RuntimeComponentFactory.get().getEntryPointFactory().createEntryPointsManager(kBase, this, handleFactory);

        this.segmentMemorySupport = new SegmentMemorySupportImpl(nodeMemories, kBase.getSegmentPrototypeRegistry(), entryPointsManager.getDefaultEntryPoint());
        
        this.sequential = conf.isSequential();

        this.globalResolver = RuntimeComponentFactory.get().createGlobalResolver(this, this.environment);
        
        this.ruleNetworkEvaluator = new RuleNetworkEvaluatorImpl(this, nodeMemories, segmentMemorySupport);

        if (initInitFactHandle) {
            this.initialFactHandle = initInitialFact(null);
        }
    }

    public StatefulKnowledgeSessionImpl setStateless( boolean stateless ) {
        this.stateless = stateless;
        return this;
    }

    private void registerReceiveNodes( List<AsyncReceiveNode> nodes ) {
        receiveNodeMemories = nodes == null ? Collections.emptyList() : nodes.stream().map( this::getNodeMemory ).collect( toList() );
    }

    public void initMBeans(String containerId, String kbaseName, String ksessionName) {
        if (kBase.getRuleBaseConfiguration() != null && kBase.getConfiguration().getOption(MBeansOption.KEY).isEnabled() && mbeanRegistered.compareAndSet(false, true)) {
            this.mbeanRegisteredCBSKey = new DroolsManagementAgent.CBSKey( containerId, kbaseName, ksessionName );
            DroolsManagementAgent.getInstance().registerKnowledgeSessionUnderName( mbeanRegisteredCBSKey, this );
        }
    }

    @Override
    public FactHandleFactory getHandleFactory() {
        return handleFactory;
    }

    public void setHandleFactory( FactHandleFactory handleFactory ) {
        this.handleFactory = handleFactory;
    }

    public <T> T getKieRuntime( Class<T> cls) {
        return createRuntimeService(cls);
    }

    public synchronized <T> T createRuntimeService(Class<T> cls) {
        // This is sychronized to ensure that only ever one is created, using the two-tone pattern.
        if (runtimeFactory == null) {
            runtimeFactory = KieRuntimeFactory.of(getKieBase());
        }

        return runtimeFactory.get(cls);
    }

    public RuleNetworkEvaluator getRuleNetworkEvaluator() {
        return ruleNetworkEvaluator;
    }
    
    public WorkingMemoryEntryPoint getEntryPoint(String name) {
        return this.entryPointsManager.getEntryPoint(name);
    }

    public Collection<? extends org.kie.api.runtime.rule.EntryPoint> getEntryPoints() {
        return this.entryPointsManager.getEntryPoints();
    }

    public Collection<RuleRuntimeEventListener> getRuleRuntimeEventListeners() {
        return ruleRuntimeEventSupport.getEventListeners();
    }

    public Collection<AgendaEventListener> getAgendaEventListeners() {
        return this.agendaEventSupport.getEventListeners();
    }

    private InternalProcessRuntime createProcessRuntime() {
        InternalProcessRuntime processRuntime = ProcessRuntimeFactory.newProcessRuntime(this);
        if (processRuntime == null) {
            processRuntime = DUMMY_PROCESS_RUNTIME;
        }
        return processRuntime;
    }

    public InternalProcessRuntime getProcessRuntime() {
        if (processRuntime == null) {
            synchronized(this) {
                if (processRuntime == null) {
                    this.processRuntime = createProcessRuntime();
                }
            }
        }
        return this.processRuntime;
    }

    public InternalProcessRuntime internalGetProcessRuntime() {
        return this.processRuntime;
    }

    public void addEventListener(ProcessEventListener listener) {
        getProcessRuntime().addEventListener(listener);
    }

    public Collection<ProcessEventListener> getProcessEventListeners() {
        return getProcessRuntime().getProcessEventListeners();
    }

    public void removeEventListener(ProcessEventListener listener) {
        getProcessRuntime().removeEventListener(listener);
    }

    @Override
    public KieBase getKieBase() {
        return this.kBase;
    }

    @Override
    public Consumer<PropagationEntry<ReteEvaluator>> getWorkingMemoryActionListener() {
        return workingMemoryActionListener;
    }

    @Override
    public void setWorkingMemoryActionListener(Consumer<PropagationEntry<ReteEvaluator>> workingMemoryActionListener) {
        this.workingMemoryActionListener = workingMemoryActionListener;
    }

    StatefulKnowledgeSessionImpl fromPool(StatefulSessionPool pool) {
        this.pool = pool;
        alive = true;
        return this;
    }

    public void dispose() {
        alive = false;
        if (pool != null) {
            pool.release(this);
            return;
        }

        if (!agenda.dispose(this)) {
            return;
        }

        if (logger != null) {
            try {
                logger.close();
            } catch (Exception e) { /* the logger was already closed, swallow */ }
        }

        for (WorkingMemoryEntryPoint ep : this.entryPointsManager.getEntryPoints()) {
            ep.dispose();
        }
        for (AsyncReceiveNode.AsyncReceiveMemory receiveMemory : this.receiveNodeMemories) {
            receiveMemory.dispose();
        }

        this.ruleRuntimeEventSupport.clear();
        this.ruleEventListenerSupport.clear();
        this.agendaEventSupport.clear();
        for (KieBaseEventListener listener : kieBaseEventListeners) {
            this.kBase.removeEventListener(listener);
        }

        if (this.processRuntime != null) {
            this.processRuntime.dispose();
        }

        if (this.timerService != null) {
            this.timerService.shutdown();
        }

        if (this.workItemManager != null) {
            ((org.drools.core.process.WorkItemManager)this.workItemManager).dispose();
        }

        this.kBase.disposeStatefulSession( this );

        if (this.mbeanRegistered.get()) {
            DroolsManagementAgent.getInstance().unregisterKnowledgeSessionUnderName(mbeanRegisteredCBSKey, this);
        }
    }

    public boolean isAlive() {
        return alive && agenda.isAlive();
    }

    public void destroy() {
        dispose();
    }

    public void update(FactHandle factHandle) {
        this.update(factHandle,
                    ((InternalFactHandle) factHandle).getObject());
    }

    public void abortProcessInstance(String id) {
        this.getProcessRuntime().abortProcessInstance(id);
    }

    public void signalEvent(String type,
                            Object event) {
        this.getProcessRuntime().signalEvent( type, event );
    }

    public void signalEvent(String type,
                            Object event,
                            String processInstanceId) {
        this.getProcessRuntime().signalEvent( type, event, processInstanceId );
    }

    public Globals getGlobals() {
        return (Globals) this.getGlobalResolver();
    }

    public <T extends FactHandle> Collection<T> getFactHandles() {
        return new ObjectStoreWrapper( getObjectStore(),
                                       null,
                                       ObjectStoreWrapper.FACT_HANDLE );
    }

    public <T extends FactHandle> Collection<T> getFactHandles(org.kie.api.runtime.ObjectFilter filter) {
        return new ObjectStoreWrapper( getObjectStore(),
                                       filter,
                                       ObjectStoreWrapper.FACT_HANDLE );
    }

    public Collection<?> getObjects() {
        return new ObjectStoreWrapper( getObjectStore(),
                                       null,
                                       ObjectStoreWrapper.OBJECT );
    }

    public Collection<?> getObjects(org.kie.api.runtime.ObjectFilter filter) {
        return new ObjectStoreWrapper( getObjectStore(),
                                       filter,
                                       ObjectStoreWrapper.OBJECT );
    }

    public <T> T execute(Command<T> command) {
        ExecutableRunner<RequestContext> runner = ExecutableRunner.create();
        RequestContext context = runner.createContext().with( this.kBase ).with( this );

        if ( !(command instanceof BatchExecutionCommand) ) {
            return runner.execute( command, context );
        }

        try {
            startBatchExecution();
            return runner.execute( command, context );
        } finally {
            endBatchExecution();
            if (kBase.flushModifications() && !stateless) {
                fireAllRules();
            }
        }
    }

    public InternalFactHandle initInitialFact(MarshallerReaderContext context) {
        WorkingMemoryEntryPoint defaultEntryPoint = entryPointsManager.getDefaultEntryPoint();
        InternalFactHandle handle = handleFactory.newInitialFactHandle(defaultEntryPoint);

        ObjectTypeNode otn = defaultEntryPoint.getEntryPointNode().getObjectTypeNodes().get( InitialFact_ObjectType );
        if (otn != null) {
            PropagationContextFactory ctxFact = RuntimeComponentFactory.get().getPropagationContextFactory();
            PropagationContext pctx = ctxFact.createPropagationContext( 0, PropagationContext.Type.INSERTION, null,
                                                                        null, handle, defaultEntryPoint.getEntryPoint(), context );
            otn.assertInitialFact( handle, pctx, this );
        }

        return handle;
    }

    public String getEntryPointId() {
        return EntryPointId.DEFAULT.getEntryPointId();
    }
    
    /**
     * (This shall NOT be exposed on public API)  
     */
    public QueryResultsImpl getQueryResultsFromRHS(String queryName, Object... arguments) {
    	return internalGetQueryResult(true, queryName, arguments);
    }
    
    public QueryResultsImpl getQueryResults(String queryName, Object... arguments) {
    	return internalGetQueryResult(false, queryName, arguments);
    }

    protected QueryResultsImpl internalGetQueryResult(boolean calledFromRHS, String queryName, Object... arguments) {

        try {
            if (!calledFromRHS) {
                this.lock.lock();
            }

            this.kBase.executeQueuedActions();
            // it is necessary to flush the propagation queue twice to perform all the expirations
            // eventually enqueued by events that have been inserted when already expired
            if (calledFromRHS) {
                flushPropagations();
                flushPropagations();
            } else {
                agenda.executeFlush();
                agenda.executeFlush();
            }

            DroolsQueryImpl queryObject = new DroolsQueryImpl(queryName,
                                                              arguments,
                                                              getQueryListenerInstance(),
                                                              false );

            InternalFactHandle handle = this.handleFactory.newFactHandle( queryObject,
                                                                          null,
                                                                          this,
                                                                          this );

            final PropagationContext pCtx = pctxFactory.createPropagationContext(getNextPropagationIdCounter(), PropagationContext.Type.INSERTION,
                                                                                 null, null, handle, getEntryPoint());


            TerminalNode[] tnodes = evalQuery(queryName, queryObject, handle, pCtx, calledFromRHS);

            List<Map<String, Declaration>> decls = new ArrayList<>();
            if ( tnodes != null ) {
                for ( TerminalNode node : tnodes ) {
                    decls.add( node.getSubRule().getOuterDeclarations() );
                }
            }

            this.handleFactory.destroyFactHandle( handle);
            return new QueryResultsImpl( (List<QueryRowWithSubruleIndex>) queryObject.getQueryResultCollector().getResults(),
                                         decls.toArray( new Map[decls.size()] ),
                                         this,
                                         ( queryObject.getQuery() != null ) ? queryObject.getQuery().getParameters()  : new Declaration[0] );
        } finally {
            if (!calledFromRHS) {
                this.lock.unlock();
            }
        }
    }

    private InternalViewChangedEventListener getQueryListenerInstance() {
        switch ( this.config.getOption(QueryListenerOption.KEY)) {
            case STANDARD :
                return new StandardQueryViewChangedEventListener();
            case LIGHTWEIGHT :
                return new NonCloningQueryViewListener();
        }
        return null;
    }

    public LiveQuery openLiveQuery(final String query,
                                   final Object[] arguments,
                                   final ViewChangedEventListener listener) {

        try {
            this.lock.lock();

            this.kBase.executeQueuedActions();
            agenda.executeFlush();

            DroolsQueryImpl queryObject = new DroolsQueryImpl(query,
                                                              arguments,
                                                              new OpenQueryViewChangedEventListenerAdapter( listener ),
                                                              true);
            InternalFactHandle handle = this.handleFactory.newFactHandle(queryObject,
                                                                         null,
                                                                         this,
                                                                         this);

            final PropagationContext pCtx = pctxFactory.createPropagationContext(getNextPropagationIdCounter(), PropagationContext.Type.INSERTION,
                                                                                 null, null, handle, getEntryPoint());

            evalQuery( queryObject.getName(), queryObject, handle, pCtx, false );

            return new LiveQueryImpl( this,
                                      handle );
        } finally {
            this.lock.unlock();
        }
    }

    private QueryTerminalNode[] evalQuery(final String queryName, final DroolsQueryImpl queryObject, final InternalFactHandle handle, final PropagationContext pCtx, final boolean isCalledFromRHS) {
        ExecuteQuery executeQuery = new ExecuteQuery( queryName, queryObject, handle, pCtx, isCalledFromRHS);
        addPropagation( executeQuery );
        return executeQuery.getResult();
    }

    public void closeLiveQuery(final InternalFactHandle factHandle) {

        try {
            this.lock.lock();
            ExecuteCloseLiveQuery query = new ExecuteCloseLiveQuery( factHandle );
            addPropagation( query );
            query.getResult();
        } finally {
            this.lock.unlock();
        }
    }

    private class ExecuteCloseLiveQuery extends PropagationEntryWithResult<ReteEvaluator, Void> {

        private final InternalFactHandle factHandle;

        private ExecuteCloseLiveQuery( InternalFactHandle factHandle ) {
            this.factHandle = factHandle;
        }

        @Override
        public void internalExecute(ReteEvaluator reteEvaluator ) {
            LeftInputAdapterNode lian = (LeftInputAdapterNode) SuperCacheFixer.getLeftTupleSource(factHandle.getFirstLeftTuple());
            LeftInputAdapterNode.LiaNodeMemory lmem = getNodeMemory(lian);
            SegmentMemory lsmem = lmem.getSegmentMemory();

            TupleImpl childLeftTuple = factHandle.getFirstLeftTuple(); // there is only one, all other LTs are peers
            LeftInputAdapterNode.doDeleteObject( childLeftTuple, childLeftTuple.getPropagationContext(),  lsmem, StatefulKnowledgeSessionImpl.this, lian, false, lmem );

            for ( PathMemory rm : lmem.getSegmentMemory().getPathMemories() ) {
                RuleAgendaItem evaluator = agenda.createRuleAgendaItem( Integer.MAX_VALUE, rm, (TerminalNode) rm.getPathEndNode() );
                evaluator.getRuleExecutor().setDirty( true );
                evaluator.getRuleExecutor().evaluateNetworkAndFire( StatefulKnowledgeSessionImpl.this, null, 0, -1 );
            }

            handleFactory.destroyFactHandle( factHandle );
            done(null);
        }
    }

    public EntryPointId getEntryPoint() {
        return entryPointsManager.getDefaultEntryPoint().getEntryPoint();
    }

    @Override
    public ReteEvaluator getReteEvaluator() {
        return this;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        // all we do is create marshall to a byte[] and write to the stream
        StatefulKnowledgeSession ksession = (StatefulKnowledgeSession) getKnowledgeRuntime();

        Marshaller marshaller = MarshallerFactory.newMarshaller(ksession.getKieBase(), new ObjectMarshallingStrategy[]{MarshallerFactory.newSerializeMarshallingStrategy()});

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        marshaller.marshall( stream, (StatefulKnowledgeSession) getKnowledgeRuntime() );
        stream.close();

        byte[] bytes = stream.toByteArray();
        out.writeInt( bytes.length );
        out.write(bytes);
    }

    public void readExternal(ObjectInput in) throws IOException,
                                                    ClassNotFoundException {
        bytes = new byte[ in.readInt() ];
        in.readFully( bytes );
    }

    public static class GlobalsAdapter implements GlobalResolver {
        private final Globals globals;

        public GlobalsAdapter(Globals globals) {
            this.globals = globals;
        }

        public Object resolveGlobal(String identifier) {
            return this.globals.get( identifier );
        }

        public void setGlobal(String identifier,
                              Object value) {
            this.globals.set( identifier,
                              value );
        }

        public void removeGlobal(String identifier) {
            ((GlobalResolver)globals).removeGlobal( identifier );
        }

        @Override
        public void clear() {
            if (globals instanceof GlobalResolver) {
                ((GlobalResolver)globals).clear();
            }
        }
    }

    // ------------------------------------------------------------
    // Instance methods
    // ------------------------------------------------------------

    public void updateEntryPointsCache() {
        entryPointsManager.updateEntryPointsCache();
    }

    public RuleSessionConfiguration getRuleSessionConfiguration() {
        return this.ruleSessionConfig;
    }

    @Override public SessionConfiguration getSessionConfiguration() {
        return this.config.as(SessionConfiguration.KEY);
    }

    public void reset() {
        if (nodeMemories != null) {
            nodeMemories.resetAllMemories();
        }

        this.agenda.reset();

        this.globalResolver.clear();
        this.kieBaseEventListeners.clear();
        this.ruleRuntimeEventSupport.clear();
        this.ruleEventListenerSupport.clear();
        this.agendaEventSupport.clear();

        this.handleFactory.clear( 0, 0 );
        this.propagationIdCounter.set(0);
        this.lastIdleTimestamp.set( -1 );

        this.entryPointsManager.reset();

        if (this.timerService != null) {
            this.timerService.reset();
        }

        if (this.processRuntime != null) {
            this.processRuntime.dispose();
            this.processRuntime = null;
        }

        this.initialFactHandle = initInitialFact(null);
    }

    public void reset(long handleId,
                      long handleCounter,
                      long propagationCounter) {
        if (nodeMemories != null) {
            nodeMemories.clear();
        }
        this.agenda.clear();

        for ( WorkingMemoryEntryPoint ep : this.entryPointsManager.getEntryPoints() ) {
            // clear the state for each entry point
            ep.reset();
        }

        this.handleFactory.clear( handleId,
                                  handleCounter);

        this.propagationIdCounter = new AtomicLong( propagationCounter );
        this.lastIdleTimestamp.set(-1);

        // TODO should these be cleared?
        // we probably neeed to do CEP and Flow timers too
        // this.processInstanceManager.clear()
        // this.workItemManager.clear();
    }

    public void setRuleRuntimeEventSupport(RuleRuntimeEventSupport ruleRuntimeEventSupport) {
        this.ruleRuntimeEventSupport = ruleRuntimeEventSupport;
    }

    public void setAgendaEventSupport(AgendaEventSupport agendaEventSupport) {
        this.agendaEventSupport = agendaEventSupport;
    }

    public boolean isSequential() {
        return this.sequential;
    }

    public void addEventListener(final RuleRuntimeEventListener listener) {
        this.ruleRuntimeEventSupport.addEventListener( listener );
    }

    public void removeEventListener(final RuleRuntimeEventListener listener) {
        this.ruleRuntimeEventSupport.removeEventListener( listener );
    }

    public void addEventListener(final AgendaEventListener listener) {
        this.agendaEventSupport.addEventListener( listener );
    }

    public void removeEventListener(final AgendaEventListener listener) {
        this.agendaEventSupport.removeEventListener( listener );
    }

    public void addEventListener(KieBaseEventListener listener) {
        this.kBase.addEventListener(listener);
        this.kieBaseEventListeners.add(listener);
    }

    public Collection<KieBaseEventListener> getKieBaseEventListeners() {
        return Collections.unmodifiableCollection( kieBaseEventListeners );
    }

    public void removeEventListener(KieBaseEventListener listener) {
        this.kBase.removeEventListener( listener);
        this.kieBaseEventListeners.remove( listener );
    }

    public RuleEventListenerSupport getRuleEventSupport() {
        return ruleEventListenerSupport;
    }


    public void setRuleEventListenerSupport( RuleEventListenerSupport ruleEventListenerSupport ) {
        this.ruleEventListenerSupport = ruleEventListenerSupport;
    }

    public void addEventListener( final RuleEventListener listener ) {
        this.ruleEventListenerSupport.addEventListener( listener );
    }

    public void removeEventListener(final RuleEventListener listener) {
        this.ruleEventListenerSupport.removeEventListener(listener);
    }

    public FactHandleFactory getFactHandleFactory() {
        return handleFactory;
    }

    public void setGlobal(final String identifier,
                          final Object value) {
        // Cannot set null values
        if ( value == null ) {
            return;
        }

        try {
            this.kBase.readLock();
            startOperation(InternalOperationType.SET_GLOBAL);
            // Make sure the global has been declared in the RuleBase
            Type type = this.kBase.getGlobals().get( identifier );
            if ( (type == null) ) {
                throw new RuntimeException( "Unexpected global [" + identifier + "]" );
            } else if ( !rawType( type ).isInstance( value ) ) {
                throw new RuntimeException( "Illegal class for global. " + "Expected [" + type.getTypeName() + "], " + "found [" + value.getClass().getName() + "]." );

            } else {
                this.globalResolver.setGlobal( identifier, value );
            }
        } finally {
            endOperation(InternalOperationType.SET_GLOBAL);
            this.kBase.readUnlock();
        }
    }

    public void removeGlobal(String identifier) {
        this.globalResolver.removeGlobal( identifier );
    }

    public void setGlobalResolver(final GlobalResolver globalResolver) {
        try {
            this.lock.lock();
            this.globalResolver = globalResolver;
        } finally {
            this.lock.unlock();
        }
    }

    public GlobalResolver getGlobalResolver() {
        return this.globalResolver;
    }

    public Calendars getCalendars() {
        if (this.calendars == null) {
            this.calendars = new CalendarsImpl();
        }
        return this.calendars;
    }

    public int getId() {
        checkAlive();
        return this.id.intValue();
    }

    public long getIdentifier() {
        checkAlive();
        return this.id;
    }

    public void setIdentifier(long id) {
        checkAlive();
        this.id = id;
    }

    protected void checkAlive() {
        if (!isAlive()) {
            throw new IllegalStateException( ERRORMSG );
        }
    }

    public Object getGlobal(final String identifier) {
        return this.globalResolver.resolveGlobal( identifier );
    }

    public Environment getEnvironment() {
        return this.environment;
    }

    public InternalAgenda getAgenda() {
        return this.agenda;
    }

    public void clearAgenda() {
        this.agenda.clearAndCancel();
    }

    public void clearAgendaGroup(final String group) {
        this.agenda.clearAndCancelAgendaGroup(group);
    }

    public void clearActivationGroup(final String group) {
        this.agenda.clearAndCancelActivationGroup(group);
    }

    public void clearRuleFlowGroup(final String group) {
        this.agenda.clearAndCancelRuleFlowGroup(group);
    }

    @Override
    public ActivationsManager getActivationsManager() {
        return agenda;
    }

    public InternalKnowledgeBase getKnowledgeBase() {
        return this.kBase;
    }

    public void halt() {
        agenda.halt();
    }

    public int fireAllRules() {
        return fireAllRules( null, -1 );
    }

    public int fireAllRules(int fireLimit) {
        return fireAllRules( null, fireLimit );
    }

    public int fireAllRules(final AgendaFilter agendaFilter) {
        return fireAllRules( agendaFilter, -1 );
    }

    public int fireAllRules(final AgendaFilter agendaFilter,
                            int fireLimit) {
        checkAlive();
        try {
            startOperation(InternalOperationType.FIRE);
            return internalFireAllRules(agendaFilter, fireLimit);
        } finally {
            endOperation(InternalOperationType.FIRE);
        }
    }

    private int internalFireAllRules(AgendaFilter agendaFilter, int fireLimit) {
        int fireCount = 0;
        try {
            fireCount = this.agenda.fireAllRules( agendaFilter, fireLimit );
        } finally {
            if (kBase.flushModifications() && !stateless) {
                fireCount += internalFireAllRules(agendaFilter, fireLimit);
            }
        }
        return fireCount;
    }

    /**
     * Keeps firing activations until a halt is called. If in a given moment,
     * there is no activation to fire, it will wait for an activation to be
     * added to an active agenda group or rule flow group.
     *
     * @throws IllegalStateException
     *             if this method is called when running in sequential mode
     */
    public void fireUntilHalt() {
        fireUntilHalt( null );
    }

    /**
     * Keeps firing activations until a halt is called. If in a given moment,
     * there is no activation to fire, it will wait for an activation to be
     * added to an active agenda group or rule flow group.
     *
     * @param agendaFilter
     *            filters the activations that may fire
     *
     * @throws IllegalStateException
     *             if this method is called when running in sequential mode
     */
    public void fireUntilHalt(final AgendaFilter agendaFilter) {
        if ( isSequential() ) {
            throw new IllegalStateException( "fireUntilHalt() can not be called in sequential mode." );
        }

        try {
            startOperation(InternalOperationType.FIRE);
            agenda.fireUntilHalt( agendaFilter );
        } finally {
            endOperation(InternalOperationType.FIRE);
        }
    }

    /**
     * Returns the fact Object for the given <code>FactHandle</code>. It
     * actually attempts to return the value from the handle, before retrieving
     * it from objects map.
     *
     * @see org.drools.core.WorkingMemory
     *
     * @param handle
     *            The <code>FactHandle</code> reference for the
     *            <code>Object</code> lookup
     */
    public Object getObject(FactHandle handle) {
        // the handle might have been disconnected, so reconnect if it has
        if ( ((InternalFactHandle)handle).isDisconnected() ) {
            handle = this.entryPointsManager.getDefaultEntryPoint().getObjectStore().reconnect( (InternalFactHandle)handle );
        }
        return this.entryPointsManager.getDefaultEntryPoint().getObject(handle);
    }

    public ObjectStore getObjectStore() {
        return this.entryPointsManager.getDefaultEntryPoint().getObjectStore();
    }

    /**
     * @see org.drools.core.WorkingMemory
     */
    public InternalFactHandle getFactHandle(final Object object) {
        return (InternalFactHandle) this.entryPointsManager.getDefaultEntryPoint().getFactHandle(object);
    }

    /**
     * This class is not thread safe, changes to the working memory during
     * iteration may give unexpected results
     */
    public Iterator iterateObjects() {
        return getObjectStore().iterateObjects();
    }

    /**
     * This class is not thread safe, changes to the working memory during
     * iteration may give unexpected results
     */
    public Iterator iterateObjects(org.kie.api.runtime.ObjectFilter filter) {
        return getObjectStore().iterateObjects(filter);
    }

    /**
     * This class is not thread safe, changes to the working memory during
     * iteration may give unexpected results
     */
    public Iterator<InternalFactHandle> iterateFactHandles() {
        return getObjectStore().iterateFactHandles();
    }

    /**
     * This class is not thread safe, changes to the working memory during
     * iteration may give unexpected results
     */
    public Iterator<InternalFactHandle> iterateFactHandles(org.kie.api.runtime.ObjectFilter filter) {
        return getObjectStore().iterateFactHandles(filter);
    }

    public void setFocus(final String focus) {
        this.agenda.getAgendaGroup( focus ).setFocus();
    }

    public FactHandle insertAsync(final Object object) {
        checkAlive();
        return entryPointsManager.getDefaultEntryPoint().insertAsync( object );
    }

    @Override
    public void enableTMS() {
        tmsEnabled = true;
        agenda.resetKnowledgeHelper();
    }

    @Override
    public boolean isTMSEnabled() {
        return tmsEnabled;
    }

    /**
     * @see org.drools.core.WorkingMemory
     */
    public FactHandle insert(final Object object) {
        return insert( object, /* Not-Dynamic */
                       false );
    }

    public FactHandle insert(final Object object,
                             final boolean dynamic) {
        return insert( object,
                       dynamic,
                       null,
                       null );
    }

    public void submit(AtomicAction action) {
        agenda.addPropagation( new AbstractPropagationEntry<ReteEvaluator>() {
            @Override
            public void internalExecute(ReteEvaluator reteEvaluator ) {
                action.execute( (KieSession)reteEvaluator );
            }
        } );
    }

    @Override
    public void updateTraits( InternalFactHandle h, BitMask mask, Class<?> modifiedClass, InternalMatch internalMatch) {
        this.entryPointsManager.getDefaultEntryPoint().getTraitHelper().updateTraits(h, mask, modifiedClass, internalMatch);
    }

    @Override
    public <T, K, X extends TraitableBean> Thing<K> shed(InternalMatch internalMatch, TraitableBean<K, X> core, Class<T> trait) {
        return this.entryPointsManager.getDefaultEntryPoint().getTraitHelper().shed(core, trait, internalMatch);
    }

    @Override
    public <T, K> T don(InternalMatch internalMatch, K core, Collection<Class<? extends Thing>> traits, boolean b, Mode[] modes) {
        return this.entryPointsManager.getDefaultEntryPoint().getTraitHelper().don(internalMatch, core, traits, b, modes);
    }

    @Override
    public <T, K> T don(InternalMatch internalMatch, K core, Class<T> trait, boolean b, Mode[] modes) {
        return this.entryPointsManager.getDefaultEntryPoint().getTraitHelper().don(internalMatch, core, trait, b, modes);
    }

    public FactHandle insert(final Object object,
                             final boolean dynamic,
                             final RuleImpl rule,
                             final TerminalNode terminalNode) {
        checkAlive();
        return this.entryPointsManager.getDefaultEntryPoint().insert(object, dynamic, rule, terminalNode);
    }

    public void retract(FactHandle handle) {
        delete(handle);
    }

    public void delete(FactHandle handle) {
        delete(handle, null, null);
    }

    public void delete(FactHandle handle, FactHandle.State fhState) {
        delete(handle, null, null, fhState);
    }

    public void delete(final FactHandle factHandle,
                       final RuleImpl rule,
                       final TerminalNode terminalNode) {
        delete(factHandle, rule, terminalNode, FactHandle.State.ALL);
    }

    public void delete(FactHandle factHandle,
                       RuleImpl rule,
                       TerminalNode terminalNode,
                       FactHandle.State fhState ) {
        checkAlive();
        this.entryPointsManager.getDefaultEntryPoint().delete(factHandle, rule, terminalNode, fhState);
    }

    public EntryPointNode getEntryPointNode() {
        return this.entryPointsManager.getDefaultEntryPoint().getEntryPointNode();
    }

    public void update(final FactHandle handle,
                       final Object object) {
        update(handle,
               object,
               allSetButTraitBitMask(),
               Object.class,
               null);
    }

    public void update(FactHandle handle,
                       Object object,
                       String... modifiedProperties) {
        checkAlive();
        this.entryPointsManager.getDefaultEntryPoint().update(handle, object, modifiedProperties);
    }

    /**
     * modify is implemented as half way retract / assert due to the truth
     * maintenance issues.
     *
     * @see org.drools.core.WorkingMemory
     */
    public void update(FactHandle factHandle,
                       final Object object,
                       final BitMask mask,
                       Class<?> modifiedClass,
                       final InternalMatch internalMatch) {
        checkAlive();
        this.entryPointsManager.getDefaultEntryPoint().update(factHandle, object, mask, modifiedClass, internalMatch);
    }

    /**
     * Retrieve the <code>JoinMemory</code> for a particular
     * <code>JoinNode</code>.
     * @param node
     *            The <code>JoinNode</code> key.
     * @return The node's memory.
     */
    public <T extends Memory> T getNodeMemory(MemoryFactory<T> node) {
        return nodeMemories.getNodeMemory( node );
    }

    public void clearNodeMemory(final MemoryFactory node) {
        if (nodeMemories != null) nodeMemories.clearNodeMemory( node );
    }

    public NodeMemories getNodeMemories() {
        return nodeMemories;
    }

    
    public SegmentMemorySupport getSegmentMemorySupport() {
        return segmentMemorySupport;
    }
    
    public RuleRuntimeEventSupport getRuleRuntimeEventSupport() {
        return this.ruleRuntimeEventSupport;
    }

    @Override
    public AgendaEventSupport getAgendaEventSupport() {
        return this.agendaEventSupport;
    }

    public long getNextPropagationIdCounter() {
        return this.propagationIdCounter.incrementAndGet();
    }

    public Lock getLock() {
        return this.lock;
    }

    public ProcessInstance startProcess(String processId) {
        return getProcessRuntime().startProcess( processId );
    }

    public ProcessInstance startProcess(String processId,
                                        Map<String, Object> parameters) {
        return getProcessRuntime().startProcess( processId, parameters );
    }

    public ProcessInstance startProcess(String processId, AgendaFilter agendaFilter) {
        return getProcessRuntime().startProcess( processId, agendaFilter );
    }

    public ProcessInstance startProcess(String processId, Map<String, Object> parameters, AgendaFilter agendaFilter) {
        return getProcessRuntime().startProcess( processId, parameters, agendaFilter );
    }

    public ProcessInstance createProcessInstance(String processId,
                                                 Map<String, Object> parameters) {
        return getProcessRuntime().createProcessInstance( processId, parameters );
    }

    public ProcessInstance startProcessInstance(String processInstanceId) {
        return getProcessRuntime().startProcessInstance( processInstanceId );
    }

    public Collection<ProcessInstance> getProcessInstances() {
        return getProcessRuntime().getProcessInstances();
    }

    public ProcessInstance getProcessInstance(String processInstanceId) {
        return getProcessRuntime().getProcessInstance( processInstanceId );
    }

    @Override
    public ProcessInstance startProcess(String processId,
                                        CorrelationKey correlationKey, Map<String, Object> parameters) {

        return getProcessRuntime().startProcess( processId, correlationKey, parameters );
    }

    @Override
    public ProcessInstance createProcessInstance(String processId,
                                                 CorrelationKey correlationKey, Map<String, Object> parameters) {

        return getProcessRuntime().createProcessInstance( processId, correlationKey, parameters );
    }

    @Override
    public ProcessInstance getProcessInstance(CorrelationKey correlationKey) {
        return getProcessRuntime().getProcessInstance( correlationKey );
    }

    public ProcessInstance getProcessInstance(String processInstanceId, boolean readOnly) {
        return getProcessRuntime().getProcessInstance( processInstanceId, readOnly);
    }

    public WorkItemManager getWorkItemManager() {
        if (workItemManager == null) {
            FlowSessionConfiguration flowConf = config.as(FlowSessionConfiguration.KEY);
            workItemManager = flowConf.getWorkItemManagerFactory().createWorkItemManager(this.getKnowledgeRuntime());
            Map<String, Object> params = new HashMap<>();
            params.put("ksession", this.getKnowledgeRuntime());
            Map<String, WorkItemHandler> workItemHandlers = flowConf.getWorkItemHandlers(params);
            if (workItemHandlers != null) {
                for (Map.Entry<String, WorkItemHandler> entry : workItemHandlers.entrySet()) {
                    workItemManager.registerWorkItemHandler(entry.getKey(),
                                                            entry.getValue());
                }
            }
        }
        return workItemManager;
    }

    public ObjectTypeConfigurationRegistry getObjectTypeConfigurationRegistry() {
        return this.entryPointsManager.getDefaultEntryPoint().getObjectTypeConfigurationRegistry();
    }

    public InternalFactHandle getInitialFactHandle() {
        return this.initialFactHandle;
    }

    public void setInitialFactHandle( InternalFactHandle initialFactHandle ) {
        this.initialFactHandle = initialFactHandle;
    }

    public TimerService getTimerService() {
        if (this.timerService == null) {
            synchronized (this) {
                if (this.timerService == null) {
                    this.timerService = createTimerService();
                }
            }
        }

        return this.timerService;
    }

    protected TimerService createTimerService() {
        return RuntimeComponentFactory.get().createTimerService(this);
    }

    public SessionClock getSessionClock() {
        return (SessionClock) getTimerService();
    }

    public void startBatchExecution() {
        this.lock.lock();
    }

    public void endBatchExecution() {
        this.lock.unlock();
    }

    public InternalKnowledgeRuntime getKnowledgeRuntime() {
        return this;
    }

    public void registerChannel(String name,
                                Channel channel) {
        getChannels().put(name, channel);
    }

    public void unregisterChannel(String name) {
        if (channels != null) channels.remove(name);
    }

    public Map<String, Channel> getChannels() {
        if (channels == null) channels = new ConcurrentHashMap<>();
        return channels;
    }

    public long getFactCount() {
        return getObjectStore().size();
    }

    public long getTotalFactCount() {
        long result = 0;
        for (WorkingMemoryEntryPoint ep : this.entryPointsManager.getEntryPoints()) {
            result += ep.getFactCount();
        }
        return result;
    }

    /**
     * This method must be called before starting any new work in the engine,
     * like inserting a new fact or firing a new rule. It will reset the engine
     * idle time counter.
     *
     * This method must be extremely light to avoid contentions when called by
     * multiple threads/entry-points
     */
    @Override
    public void startOperation(InternalOperationType operationType) {
        if (getRuleSessionConfiguration().isThreadSafe() && this.opCounter.getAndIncrement() == 0 ) {
            // means the engine was idle, reset the timestamp
            this.lastIdleTimestamp.set(-1);
        }
    }

    private EndOperationListener endOperationListener;

    public void setEndOperationListener(EndOperationListener listener) {
        this.endOperationListener = listener;
    }

    /**
     * This method must be called after finishing any work in the engine,
     * like inserting a new fact or firing a new rule. It will reset the engine
     * idle time counter.
     *
     * This method must be extremely light to avoid contentions when called by
     * multiple threads/entry-points
     */
    @Override
    public void endOperation(InternalOperationType operationType) {
        if (getRuleSessionConfiguration().isThreadSafe() && this.opCounter.decrementAndGet() == 0 ) {
            // means the engine is idle, so, set the timestamp
            if (this.timerService != null) {
                this.lastIdleTimestamp.set(this.timerService.getCurrentTime());
            }
            if (this.endOperationListener != null) {
                this.endOperationListener.endOperation(this.getKnowledgeRuntime());
            }
        }
    }

    @Override
    public long getCurrentTime() {
        return this.getTimerService().getCurrentTime();
    }

    @Override
    public RuleBase getRuleBase() {
        return kBase;
    }

    /**
     * Returns the number of time units (usually ms) that the engine is idle
     * according to the session clock or -1 if it is not idle.
     *
     * This method is not synchronised and might return an approximate value.
     */
    public long getIdleTime() {
        long lastIdle = this.lastIdleTimestamp.get();
        return lastIdle > -1 && timerService != null ? timerService.getCurrentTime() - lastIdle : -1;
    }

    public long getLastIdleTimestamp() {
        return this.lastIdleTimestamp.get();
    }

    /**
     * Returns the number of time units (usually ms) to
     * the next scheduled job
     *
     * @return the number of time units until the next scheduled job or -1 if
     *         there is no job scheduled
     */
    public long getTimeToNextJob() {
        return getTimerService().getTimeToNextJob();
    }

    @Override
    public void addPropagation(PropagationEntry<ReteEvaluator> propagationEntry) {
        agenda.addPropagation( propagationEntry );
    }

    public void flushPropagations() {
        agenda.flushPropagations();
    }

    @Override
    public void notifyWaitOnRest() {
        agenda.notifyWaitOnRest();
    }

    @Override
    public Iterator<? extends PropagationEntry<ReteEvaluator>> getActionsIterator() {
        return agenda.getActionsIterator();
    }

    @Override
    public void activate() {
        agenda.activate();
    }

    @Override
    public void deactivate() {
        agenda.deactivate();
    }

    @Override
    public boolean tryDeactivate() {
        return agenda.tryDeactivate();
    }

    @Override
    public void cancelActivation(InternalMatch internalMatch, boolean declarativeAgenda) {
        if (declarativeAgenda && internalMatch.getActivationFactHandle() != null) {
            getEntryPointNode().retractActivation(internalMatch.getActivationFactHandle(), internalMatch.getPropagationContext(), this);
        }
    }

    @Override
    public String toString() {
        return "KieSession[" + id + "]";
    }

    public static final DummyInternalProcessRuntime DUMMY_PROCESS_RUNTIME = new DummyInternalProcessRuntime();

    public static class DummyInternalProcessRuntime implements InternalProcessRuntime {
        @Override
        public void dispose() { }

        @Override
        public void clearProcessInstances() {
            // do nothing.
        }

        @Override
        public void clearProcessInstancesState() {
            throw new UnsupportedOperationException( );
        }

        @Override
        public ProcessInstance startProcess( String processId, CorrelationKey correlationKey, Map<String, Object> parameters ) {
            throw new UnsupportedOperationException( );
        }

        @Override
        public ProcessInstance createProcessInstance( String processId, CorrelationKey correlationKey, Map<String, Object> parameters ) {
            throw new UnsupportedOperationException( );
        }

        @Override
        public ProcessInstance getProcessInstance( CorrelationKey correlationKey ) {
            throw new UnsupportedOperationException( );
        }

        @Override
        public void addEventListener( ProcessEventListener listener ) {
            // do nothing.
        }

        @Override
        public void removeEventListener( ProcessEventListener listener ) {
            // do nothing.
        }

        @Override
        public Collection<ProcessEventListener> getProcessEventListeners() {
            throw new UnsupportedOperationException( );
        }

        @Override
        public ProcessInstance startProcess( String processId ) {
            throw new UnsupportedOperationException( );
        }

        @Override
        public ProcessInstance startProcess( String processId, Map<String, Object> parameters ) {
            throw new UnsupportedOperationException( );
        }

        @Override
        public ProcessInstance startProcess( String processId, AgendaFilter agendaFilter ) {
            throw new UnsupportedOperationException( );
        }

        @Override
        public ProcessInstance startProcess( String processId, Map<String, Object> parameters, AgendaFilter agendaFilter ) {
            throw new UnsupportedOperationException( );
        }

        @Override
        public ProcessInstance createProcessInstance( String processId, Map<String, Object> parameters ) {
            throw new UnsupportedOperationException( );
        }

        @Override
        public ProcessInstance startProcessInstance( String processInstanceId ) {
            throw new UnsupportedOperationException( );
        }

        @Override
        public void signalEvent( String type, Object event ) {
            throw new UnsupportedOperationException( );
        }

        @Override
        public void signalEvent( String type, Object event, String processInstanceId ) {
            throw new UnsupportedOperationException( );
        }

        @Override
        public Collection<ProcessInstance> getProcessInstances() {
            throw new UnsupportedOperationException( );
        }

        @Override
        public ProcessInstance getProcessInstance( String processInstanceId ) {
            throw new UnsupportedOperationException( );
        }

        @Override
        public ProcessInstance getProcessInstance( String processInstanceId, boolean readonly ) {
            throw new UnsupportedOperationException( );
        }

        @Override
        public void abortProcessInstance( String processInstanceId ) {
            throw new UnsupportedOperationException( );
        }

        @Override
        public WorkItemManager getWorkItemManager() {
            throw new UnsupportedOperationException( );
        }

        @Override
        public ProcessInstance startProcessFromNodeIds(String processId, Map<String, Object> params, String... nodeInstancesIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessInstance startProcessFromNodeIds(String processId, CorrelationKey key, Map<String, Object> params, String... nodeIds) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public ProcessInstance startProcessFromNodeIds(String processId, Map<String, Object> params, String... nodeInstancesIds) {
        return getProcessRuntime().startProcessFromNodeIds(processId, params, nodeInstancesIds);
    }

    @Override
    public ProcessInstance startProcessFromNodeIds(String processId, CorrelationKey key, Map<String, Object> params, String... nodeIds) {
        return getProcessRuntime().startProcessFromNodeIds(processId, key, params, nodeIds);
    }
}
