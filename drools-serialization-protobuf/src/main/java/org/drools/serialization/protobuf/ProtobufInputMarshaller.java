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
package org.drools.serialization.protobuf;

import com.google.protobuf.ExtensionRegistry;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.base.time.Trigger;
import org.drools.core.SessionConfiguration;
import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.common.ActivationsFilter;
import org.drools.core.common.AgendaGroupQueueImpl;
import org.drools.core.common.DefaultEventHandle;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.EqualityKey;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalAgendaGroup;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.ObjectStore;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.PropagationContextFactory;
import org.drools.core.common.QueryElementFactHandle;
import org.drools.core.common.TruthMaintenanceSystem;
import org.drools.core.common.TruthMaintenanceSystemFactory;
import org.drools.core.impl.WorkingMemoryReteExpireAction;
import org.drools.core.marshalling.MarshallerReaderContext;
import org.drools.core.marshalling.TupleKey;
import org.drools.core.phreak.PhreakTimerNode.Scheduler;
import org.drools.core.phreak.RuleAgendaItem;
import org.drools.core.phreak.RuleExecutor;
import org.drools.core.process.WorkItem;
import org.drools.core.reteoo.ObjectTypeConf;
import org.drools.core.reteoo.RuntimeComponentFactory;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.Tuple;
import org.drools.core.rule.accessor.FactHandleFactory;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.core.time.impl.CompositeMaxDurationTrigger;
import org.drools.core.time.impl.CronTrigger;
import org.drools.core.time.impl.IntervalTrigger;
import org.drools.core.time.impl.PointInTimeTrigger;
import org.drools.core.time.impl.PseudoClockScheduler;
import org.drools.kiesession.entrypoints.NamedEntryPoint;
import org.drools.kiesession.factory.PhreakWorkingMemoryFactory;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.drools.serialization.protobuf.ProtobufMessages.FactHandle;
import org.drools.serialization.protobuf.ProtobufMessages.ObjectTypeConfiguration;
import org.drools.serialization.protobuf.ProtobufMessages.RuleData;
import org.drools.serialization.protobuf.ProtobufMessages.Timers.Timer;
import org.drools.serialization.protobuf.marshalling.ActivationKey;
import org.drools.serialization.protobuf.marshalling.KieSessionInitializer;
import org.drools.serialization.protobuf.marshalling.ProcessMarshaller;
import org.drools.serialization.protobuf.marshalling.ProcessMarshallerFactory;
import org.drools.tms.TruthMaintenanceSystemEqualityKey;
import org.drools.tms.TruthMaintenanceSystemImpl;
import org.kie.api.marshalling.ObjectMarshallingStrategy;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.Match;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * An input marshaller that uses protobuf. 
 * 
 */
public class ProtobufInputMarshaller {
    // NOTE: all variables prefixed with _ (underscore) are protobuf structs

    private static final ProcessMarshaller PROCESS_MARSHALLER = createProcessMarshaller();

    private static ProcessMarshaller createProcessMarshaller() {
        try {
            return ProcessMarshallerFactory.newProcessMarshaller();
        } catch ( IllegalArgumentException e ) {
            return null;
        }
    }

    /**
     * Stream the data into an existing session
     * 
     * @param session
     * @param context
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static void readSession(StatefulKnowledgeSessionImpl session, ProtobufMarshallerReaderContext context)
            throws IOException, ClassNotFoundException {

        ProtobufMessages.KnowledgeSession _session = loadAndParseSession( context );
        InternalAgenda agenda = resetSession( session, context, _session );
        readSession( _session, session, agenda, context );
    }

    public static ReadSessionResult readSession( ProtobufMarshallerReaderContext context,
                                                  int id,
                                                  Environment environment,
                                                  SessionConfiguration config,
                                                  KieSessionInitializer initializer) throws IOException, ClassNotFoundException {

        ProtobufMessages.KnowledgeSession _session = loadAndParseSession( context );

        StatefulKnowledgeSessionImpl session = createAndInitializeSession( context,
                                                                           id,
                                                                           environment,
                                                                           config,
                                                                           _session );
        // Initialize the session before unmarshalling data
        if (initializer != null) {
            initializer.init( session );
        }

        return new ReadSessionResult(readSession(_session,
                                                 session,
                                                 session.getAgenda(),
                                                 context),
                                     _session);
    }

    private static InternalAgenda resetSession(StatefulKnowledgeSessionImpl session,
                                              ProtobufMarshallerReaderContext context,
                                              ProtobufMessages.KnowledgeSession _session) {
        session.reset( _session.getRuleData().getLastId(),
                       _session.getRuleData().getLastRecency(),
                       1 );
        InternalAgenda agenda = session.getAgenda();

        readAgenda( context,
                    _session.getRuleData(),
                    agenda );
        return agenda;
    }

    private static StatefulKnowledgeSessionImpl createAndInitializeSession( ProtobufMarshallerReaderContext context,
                                                                            int id,
                                                                            Environment environment,
                                                                            SessionConfiguration config,
                                                                            ProtobufMessages.KnowledgeSession _session) throws IOException {
        FactHandleFactory handleFactory = context.getKnowledgeBase().newFactHandleFactory( _session.getRuleData().getLastId(),
                                                                                 _session.getRuleData().getLastRecency() );

        StatefulKnowledgeSessionImpl session = ( StatefulKnowledgeSessionImpl ) PhreakWorkingMemoryFactory.getInstance()
                .createWorkingMemory( id, context.getKnowledgeBase(), handleFactory,
                                      1, // pCTx starts at 1, as InitialFact is 0
                                      config, environment );

        readAgenda( context, _session.getRuleData(), session.getAgenda() );

        return session;
    }

    private static ProtobufMessages.KnowledgeSession loadAndParseSession( MarshallerReaderContext context) throws IOException,
                                                                                                         ClassNotFoundException {
        ExtensionRegistry registry = PersisterHelper.buildRegistry( context, PROCESS_MARSHALLER);

        ProtobufMessages.Header _header = PersisterHelper.readFromStreamWithHeaderPreloaded( context, registry );

        return ProtobufMessages.KnowledgeSession.parseFrom( _header.getPayload(), registry );
    }

    public static StatefulKnowledgeSessionImpl readSession( ProtobufMessages.KnowledgeSession _session,
                                                            StatefulKnowledgeSessionImpl session,
                                                            InternalAgenda agenda,
                                                            ProtobufMarshallerReaderContext context) throws IOException,
                                                                                    ClassNotFoundException {
        GlobalResolver globalResolver = (GlobalResolver) context.env.get( EnvironmentName.GLOBALS );
        if ( globalResolver != null ) {
            session.setGlobalResolver( globalResolver );
        }

        if ( session.getTimerService() instanceof PseudoClockScheduler ) {
            PseudoClockScheduler clock = (PseudoClockScheduler) session.getTimerService();
            clock.advanceTime( _session.getTime(),
                               TimeUnit.MILLISECONDS );
        }

        context.setWorkingMemory( session );

        // need to read node memories before reading the fact handles
        // because this data is required during fact propagation 
        readNodeMemories( context,
                          _session.getRuleData() );

        List<PropagationContext> pctxs = new ArrayList<>();

        if ( _session.getRuleData().hasInitialFact() ) {
            session.setInitialFactHandle( session.initInitialFact(context) );
            context.getHandles().put( session.getInitialFactHandle().getId(), session.getInitialFactHandle() );
        }

        for ( ProtobufMessages.EntryPoint _ep : _session.getRuleData().getEntryPointList() ) {
            WorkingMemoryEntryPoint wmep = context.getWorkingMemory().getEntryPoint(_ep.getEntryPointId());
            readFactHandles( context, _ep, wmep.getObjectStore(), pctxs );

            context.getWorkingMemory().getFactHandleFactory().doRecycleIds( context.getHandles().keySet() );

            readTruthMaintenanceSystem( session, context, wmep, _ep, pctxs );

        }

        context.getFilter().evaluateRNEAs( context.getWorkingMemory() );
        cleanReaderContexts( pctxs );
        context.getWorkingMemory().getFactHandleFactory().stopRecycleIds();

        readActionQueue( context, _session.getRuleData() );

        if ( PROCESS_MARSHALLER != null ) {
            if ( _session.hasProcessData() ) {
                context.setParameterObject( _session.getProcessData() );
                PROCESS_MARSHALLER.readProcessInstances( context );

                context.setParameterObject( _session.getProcessData() );
                PROCESS_MARSHALLER.readWorkItems( context );

                // This actually does ALL timers, due to backwards compatability issues
                // It will read in old JBPM binaries, but always write to the new binary format.
                context.setParameterObject( _session.getProcessData() );
                PROCESS_MARSHALLER.readProcessTimers( context );
            }
        } else {
            if ( _session.hasProcessData() ) {
                throw new IllegalStateException( "No process marshaller, unable to unmarshall process data." );
            }
        }

        if ( _session.hasTimers() ) {
            for ( ProtobufMessages.Timers.Timer _timer : _session.getTimers().getTimerList() ) {
                readTimer( context, _timer );
            }
        }

        // need to process any eventual left over timer node timers
        if ( ! context.timerNodeSchedulers.isEmpty() ) {
            for ( Map<TupleKey, Scheduler> schedulers : context.timerNodeSchedulers.values() ) {
                for ( Scheduler scheduler : schedulers.values() ) {
                    scheduler.schedule( scheduler.getTrigger() );
                }
            }
            context.timerNodeSchedulers.clear();
        }

        context.getFilter().removeEmptyRNEAs( context.getWorkingMemory() );

        // remove the activations filter
        agenda.setActivationsFilter( null );

        return session;
    }

    private static void readNodeMemories( ProtobufMarshallerReaderContext context,
                                          RuleData _session) {
        for ( ProtobufMessages.NodeMemory _node : _session.getNodeMemoryList() ) {
            Object memory = null;
            switch ( _node.getNodeType() ) {
                // ACCUMULATE, RIA and FROM memories are no longer serialized, so the following 3 cases are useless for
                // new serialized session, but are still necessary for sessions serialized before the marshalling refactor
                case ACCUMULATE : {
                    Map<TupleKey, ProtobufMessages.FactHandle> map = new HashMap<>();
                    for ( ProtobufMessages.NodeMemory.AccumulateNodeMemory.AccumulateContext _ctx : _node.getAccumulate().getContextList() ) {
                        map.put( PersisterHelper.createTupleKey( _ctx.getTuple() ), _ctx.getResultHandle() );
                    }
                    context.withSerializedNodeMemories();
                    memory = map;
                    break;
                }
                case RIA : {
                    Map<TupleKey, ProtobufMessages.FactHandle> map = new HashMap<>();
                    for ( ProtobufMessages.NodeMemory.RIANodeMemory.RIAContext _ctx : _node.getRia().getContextList() ) {
                        map.put( PersisterHelper.createTupleKey( _ctx.getTuple() ), _ctx.getResultHandle() );
                    }
                    context.withSerializedNodeMemories();
                    memory = map;
                    break;
                }
                case FROM : {
                    Map<TupleKey, List<ProtobufMessages.FactHandle>> map = new HashMap<>();
                    for ( ProtobufMessages.NodeMemory.FromNodeMemory.FromContext _ctx : _node.getFrom().getContextList() ) {
                        // have to instantiate a modifiable list
                        map.put( PersisterHelper.createTupleKey( _ctx.getTuple() ), new LinkedList<>(_ctx.getHandleList()) );
                    }
                    context.withSerializedNodeMemories();
                    memory = map;
                    break;
                }
                case QUERY_ELEMENT : {
                    Map<TupleKey, QueryElementContext> map = new HashMap<>();
                    for ( ProtobufMessages.NodeMemory.QueryElementNodeMemory.QueryContext _ctx : _node.getQueryElement().getContextList() ) {
                        // we have to use a "cloned" query element context as we need to write on it during deserialization process and the 
                        // protobuf one is read-only
                        map.put( PersisterHelper.createTupleKey( _ctx.getTuple() ), new QueryElementContext( _ctx ) );
                    }
                    memory = map;
                    break;
                }
                default : {
                    throw new IllegalArgumentException( "Unknown node type " + _node.getNodeType() + " while deserializing session." );
                }
            }
            context.getNodeMemories().put( _node.getNodeId(), memory );
        }
    }

    public static class QueryElementContext {
        public final ProtobufMessages.FactHandle             handle;
        public final LinkedList<ProtobufMessages.FactHandle> results;

        public QueryElementContext(ProtobufMessages.NodeMemory.QueryElementNodeMemory.QueryContext _ctx) {
            this.handle = _ctx.getHandle();
            this.results = new LinkedList<>( _ctx.getResultList() );
        }
    }

    public static void readAgenda( ProtobufMarshallerReaderContext context,
                                   RuleData _ruleData,
                                   InternalAgenda agenda) {
        ProtobufMessages.Agenda _agenda = _ruleData.getAgenda();

        for ( ProtobufMessages.Agenda.AgendaGroup _agendaGroup : _agenda.getAgendaGroupList() ) {
            AgendaGroupQueueImpl group = (AgendaGroupQueueImpl) agenda.getAgendaGroup( _agendaGroup.getName() );
            group.setActive( _agendaGroup.getIsActive() );
            group.setAutoDeactivate( _agendaGroup.getIsAutoDeactivate() );
            group.setClearedForRecency( _agendaGroup.getClearedForRecency() );
            group.hasRuleFlowListener( _agendaGroup.getHasRuleFlowLister() );
            group.setActivatedForRecency( _agendaGroup.getActivatedForRecency() );

            for ( ProtobufMessages.Agenda.AgendaGroup.NodeInstance _nodeInstance : _agendaGroup.getNodeInstanceList() ) {
                group.addNodeInstance( _nodeInstance.hasProcessInstanceId() ? _nodeInstance.getProcessInstanceId() : _nodeInstance.getProcessInstanceStringId(),
                                       _nodeInstance.getNodeInstanceId() );
            }
            agenda.getAgendaGroupsManager().putOnAgendaGroupsMap( group.getName(), group );
        }
        
        for ( String _groupName : _agenda.getFocusStack().getGroupNameList() ) {
            agenda.getAgendaGroupsManager().addAgendaGroupOnStack( (InternalAgendaGroup) agenda.getAgendaGroup( _groupName ) );
        }
        
        for ( ProtobufMessages.Agenda.RuleFlowGroup _ruleFlowGroup : _agenda.getRuleFlowGroupList() ) {
            AgendaGroupQueueImpl group = (AgendaGroupQueueImpl) agenda.getAgendaGroup( _ruleFlowGroup.getName() );
            group.setActive( _ruleFlowGroup.getIsActive() );
            group.setAutoDeactivate( _ruleFlowGroup.getIsAutoDeactivate() );
            

            for ( ProtobufMessages.Agenda.RuleFlowGroup.NodeInstance _nodeInstance : _ruleFlowGroup.getNodeInstanceList() ) {
                group.addNodeInstance( _nodeInstance.getProcessInstanceId(),
                                       _nodeInstance.getNodeInstanceId() );
            }
            agenda.getAgendaGroupsManager().putOnAgendaGroupsMap( group.getName(), group );
            if (group.isActive()) {
                agenda.getAgendaGroupsManager().addAgendaGroupOnStack( (InternalAgendaGroup) agenda.getAgendaGroup( group.getName() ) );
            }
        }

        

        readActivations( context,
                         _agenda.getMatchList(),
                         _agenda.getRuleActivationList() );
        agenda.setActivationsFilter( context.getFilter() );
    }

    public static void readActionQueue( ProtobufMarshallerReaderContext context,
                                        RuleData _session) throws IOException,
                                                         ClassNotFoundException {
        StatefulKnowledgeSessionImpl wm = (StatefulKnowledgeSessionImpl) context.getWorkingMemory();
        for ( ProtobufMessages.ActionQueue.Action _action : _session.getActionQueue().getActionList() ) {
            wm.addPropagation(PersisterHelper.deserializeWorkingMemoryAction(context, _action));
        }
    }

    public static void readFactHandles( ProtobufMarshallerReaderContext context,
                                        ProtobufMessages.EntryPoint _ep,
                                        ObjectStore objectStore,
                                        List<PropagationContext> pctxs) throws IOException,
                                                                          ClassNotFoundException {
        InternalWorkingMemory wm = context.getWorkingMemory();

        EntryPoint entryPoint = context.getWorkingMemory().getEntryPoint(_ep.getEntryPointId());
        
        // load the handles
        for ( ProtobufMessages.FactHandle _handle : _ep.getHandleList() ) {
            InternalFactHandle handle = readFactHandle( context, entryPoint, _handle );

            context.getHandles().put( handle.getId(), handle );

            if ( !_handle.getIsJustified() ) {
                // BeliefSystem handles the Object type 
                if ( handle.getObject() != null ) {
                    objectStore.addHandle( handle, handle.getObject() );
                }

                // add handle to object type node
                assertHandleIntoOTN( context, wm, handle, pctxs );
            }

            reattachPropertyChangeListener( entryPoint, handle, _handle.getIsDynamic() );

            if (handle.isExpired()) {
                wm.addPropagation(new WorkingMemoryReteExpireAction((DefaultEventHandle) handle));
            }
        }
    }

    /**
     * Re-registers the entry point as the fact's JavaBeans {@code PropertyChangeListener}, which
     * is what makes a fact dynamic. Marshalling cannot carry that registration: the listener is
     * the entry point itself, which is not serializable, so {@link java.beans.PropertyChangeSupport}
     * drops it and the restored fact reaches the engine with an empty listener list. Without this
     * the fact still holds the right values, but a setter no longer notifies the session and the
     * rules matching it are never re-evaluated.
     *
     * <p>Only facts that were dynamic in the marshalled session are re-registered: those inserted
     * with {@link org.kie.api.runtime.rule.EntryPoint#insert(Object)}'s dynamic variant, recorded
     * per handle in the blob, and those whose type is declared {@code @propertyChangeSupport},
     * which is derivable from the knowledge base. This mirrors what
     * {@code NamedEntryPoint.insert} does on a live insertion.</p>
     */
    private static void reattachPropertyChangeListener( EntryPoint entryPoint,
                                                        InternalFactHandle handle,
                                                        boolean dynamic ) {
        Object object = handle.getObject();
        if ( object == null || !(entryPoint instanceof NamedEntryPoint) ) {
            return;
        }
        NamedEntryPoint namedEntryPoint = (NamedEntryPoint) entryPoint;
        ObjectTypeConf typeConf = namedEntryPoint.getObjectTypeConfigurationRegistry()
                .getOrCreateObjectTypeConf( namedEntryPoint.getEntryPoint(), object );
        if ( dynamic || typeConf.isDynamic() ) {
            namedEntryPoint.addPropertyChangeListener( handle, dynamic );
        }
    }

    private static void assertHandleIntoOTN( ProtobufMarshallerReaderContext context,
                                             InternalWorkingMemory wm,
                                             InternalFactHandle handle,
                                             List<PropagationContext> pctxs) {
        Object object = handle.getObject();
        WorkingMemoryEntryPoint ep = handle.getEntryPoint(wm);
        ObjectTypeConf typeConf = ep.getObjectTypeConfigurationRegistry().getOrCreateObjectTypeConf( ep.getEntryPoint(), object );

        PropagationContextFactory pctxFactory = RuntimeComponentFactory.get().getPropagationContextFactory();

        PropagationContext propagationContext = pctxFactory.createPropagationContext(wm.getNextPropagationIdCounter(), PropagationContext.Type.INSERTION, null, null, handle, ep.getEntryPoint(), context);
        // keeping this list for a later cleanup is necessary because of the lazy propagations that might occur
        pctxs.add( propagationContext );

        ep.getEntryPointNode().assertObject( handle,
                                             propagationContext,
                                             typeConf,
                                             wm );

        wm.flushPropagations();
    }

    private static void cleanReaderContexts(List<PropagationContext> pctxs) {
        for ( PropagationContext ctx : pctxs ) {
            ctx.cleanReaderContext();
        }
    }

    public static InternalFactHandle readFactHandle( ProtobufMarshallerReaderContext context,
                                                     EntryPoint entryPoint,
                                                     FactHandle _handle) throws IOException,
                                                                       ClassNotFoundException {
        Object object = null;
        ObjectMarshallingStrategy strategy = null;
        if ( _handle.hasStrategyIndex() ) {
            strategy = context.getUsedStrategies().get( _handle.getStrategyIndex() );
            object = strategy.unmarshal( context.getStrategyContexts().get( strategy ),
                                         context,
                                         _handle.getObject().toByteArray(),
                                         (context.getKnowledgeBase() == null) ? null : context.getKnowledgeBase().getRootClassLoader() );
        }


        InternalFactHandle handle;
        switch ( _handle.getType() ) {
            case FACT : {
                handle = new DefaultFactHandle( _handle.getId(),
                                                object,
                                                _handle.getRecency(),
                                                (WorkingMemoryEntryPoint) entryPoint );
                break;
            }
            case QUERY : {
                handle = new QueryElementFactHandle( object,
                                                     _handle.getId(),
                                                     _handle.getRecency() );
                break;
            }
            case EVENT : {
                handle = new DefaultEventHandle(_handle.getId(),
                                                object,
                                                _handle.getRecency(),
                                                _handle.getTimestamp(),
                                                _handle.getDuration(),
                                                (WorkingMemoryEntryPoint) entryPoint );
                ((DefaultEventHandle) handle).setExpired(_handle.getIsExpired());
                ((DefaultEventHandle) handle).setOtnCount(_handle.getOtnCount());
                // the event is re-propagated through the network, so the activations counter will be recalculated
                //((EventFactHandle) handle).setActivationsCount( _handle.getActivationsCount() );
                break;
            }
            default : {
                throw new IllegalStateException( "Unable to marshal FactHandle, as type does not exist:" + _handle.getType() );
            }
        }
        return handle;
    }

    public static void readTruthMaintenanceSystem( StatefulKnowledgeSessionImpl session,
                                                   ProtobufMarshallerReaderContext context,
                                                   EntryPoint wmep,
                                                   ProtobufMessages.EntryPoint _ep,
                                                   List<PropagationContext> pctxs) throws IOException,
                                                                                     ClassNotFoundException {
        TruthMaintenanceSystem tms = TruthMaintenanceSystemFactory.get().getOrCreateTruthMaintenanceSystem((NamedEntryPoint) wmep);
        
        boolean wasOTCSerialized = _ep.getOtcCount() > 0; // if 0, then the OTC was not serialized (older versions of drools)
        Set<String> tmsEnabled = new HashSet<>();
        for ( ObjectTypeConfiguration _otc : _ep.getOtcList() ) {
        	if ( _otc.getTmsEnabled() ) {
            	tmsEnabled.add( _otc.getType() );
        	}
        }

        ProtobufMessages.TruthMaintenanceSystem _tms = _ep.getTms();

        for ( ProtobufMessages.EqualityKey _key : _tms.getKeyList() ) {
            InternalFactHandle handle = context.getHandles().get( _key.getHandleId() );

            // ObjectTypeConf state is not marshalled, so it needs to be re-determined
            ObjectTypeConf typeConf = context.getWorkingMemory().getObjectTypeConfigurationRegistry().getOrCreateObjectTypeConf( handle.getEntryPointId(),
                                                                                                         handle.getObject() );
            if ( !typeConf.isTMSEnabled() && (!wasOTCSerialized || tmsEnabled.contains(typeConf.getTypeName()) ) ) {
                typeConf.enableTMS();
            }

            EqualityKey key = new TruthMaintenanceSystemEqualityKey( handle, _key.getStatus() );
            handle.setEqualityKey( key );

            if ( key.getStatus() == EqualityKey.JUSTIFIED ) {
                // not yet added to the object stores
                handle.getEntryPoint((( NamedEntryPoint ) wmep).getReteEvaluator()).getObjectStore()
                        .addHandle( handle, handle.getObject() );
                // add handle to object type node
                assertHandleIntoOTN( context,
                                     context.getWorkingMemory(),
                                     handle,
                                     pctxs );
            }

            for ( Long factHandleId : _key.getOtherHandleList() ) {
                handle = context.getHandles().get( factHandleId );
                key.addFactHandle( handle );
                handle.setEqualityKey( key );
            }
            tms.put( key );

            context.getFilter().fireRNEAs( context.getWorkingMemory() );
            readBeliefSet( context, tms, _key );
        }

        if ( tms.getEqualityKeysSize() != 0 ) {
            session.enableTMS();
        }
    }

    private static void readBeliefSet( MarshallerReaderContext context,
                                       TruthMaintenanceSystem tms,
                                       ProtobufMessages.EqualityKey _key) throws IOException,
                                                                            ClassNotFoundException {
        if( _key.hasBeliefSet() ) {
            ProtobufMessages.BeliefSet _beliefSet = _key.getBeliefSet();
            InternalFactHandle handle = (InternalFactHandle) context.getHandles().get( _key.getHandleId() );
            // phreak might serialize empty belief sets, so he have to handle it during deserialization 
            if( _beliefSet.getLogicalDependencyCount() > 0 ) {
                for ( ProtobufMessages.LogicalDependency _logicalDependency : _beliefSet.getLogicalDependencyList() ) {
                    ProtobufMessages.Activation _activation = _logicalDependency.getActivation();
                    ActivationKey activationKey = getActivationKey( context, _activation );
                    InternalMatch internalMatch = (InternalMatch) ((PBActivationsFilter)context.getFilter()).getTuplesCache().get(activationKey);

                    Object object = null;
                    ObjectMarshallingStrategy strategy = null;
                    if ( _logicalDependency.hasObjectStrategyIndex() ) {
                        strategy = context.getUsedStrategies().get( _logicalDependency.getObjectStrategyIndex() );
                        object = strategy.unmarshal( context.getStrategyContexts().get( strategy ),
                                                     ( ObjectInputStream ) context,
                                                     _logicalDependency.getObject().toByteArray(),
                                                     (context.getKnowledgeBase() == null) ? null : context.getKnowledgeBase().getRootClassLoader() );
                    }

                    Object value = null;
                    if ( _logicalDependency.hasValueStrategyIndex() ) {
                        strategy = context.getUsedStrategies().get( _logicalDependency.getValueStrategyIndex() );
                        value = strategy.unmarshal( context.getStrategyContexts().get( strategy ),
                                                    ( ObjectInputStream ) context,
                                                    _logicalDependency.getValue().toByteArray(),
                                                    (context.getKnowledgeBase() == null) ? null : context.getKnowledgeBase().getRootClassLoader() );
                    }

                    ObjectTypeConf typeConf = context.getWorkingMemory().getObjectTypeConfigurationRegistry().getOrCreateObjectTypeConf( handle.getEntryPointId(),
                                                                                                                 handle.getObject() );
                    tms.readLogicalDependency(handle, object, value, internalMatch, typeConf);
                }
            } else {
                ((TruthMaintenanceSystemEqualityKey)handle.getEqualityKey()).setBeliefSet( ((TruthMaintenanceSystemImpl)tms).getBeliefSystem().newBeliefSet( handle ) );
            }
        }
    }

    private static void readActivations( ProtobufMarshallerReaderContext context,
                                         List<ProtobufMessages.Activation> _dormant,
                                         List<ProtobufMessages.Activation> _rneas) {

        for ( ProtobufMessages.Activation _activation : _dormant ) {
            // this is a dormant activation
            context.getFilter().addDormantActivation(getActivationKey( context, _activation ));
        }
    }

    private static ActivationKey getActivationKey( MarshallerReaderContext context, ProtobufMessages.Activation _activation ) {
        ProtobufMessages.Tuple _tuple = _activation.getTuple();
        if (!_tuple.getObjectList().isEmpty()) {
            Object[] objects = new Object[_tuple.getObjectList().size()];
            int i = 0;
            for (ProtobufMessages.SerializedObject _object : _tuple.getObjectList()) {
                ObjectMarshallingStrategy strategy = context.getUsedStrategies().get( _object.getStrategyIndex() );

                try {
                    objects[i++] = strategy.unmarshal( context.getStrategyContexts().get( strategy ),
                                                       (ObjectInputStream) context,
                                                       _object.getObject().toByteArray(),
                                                       (context.getKnowledgeBase() == null) ? null : context.getKnowledgeBase().getRootClassLoader() );
                } catch (IOException | ClassNotFoundException e) {
                    throw new RuntimeException( e );
                }
            }
            return PersisterHelper.createActivationKey( _activation.getPackageName(), _activation.getRuleName(), objects );
        }
        return PersisterHelper.createActivationKey( _activation.getPackageName(), _activation.getRuleName(), _tuple );
    }

    public static void readTimer( MarshallerReaderContext inCtx, Timer _timer) {
        TimersInputMarshaller reader = (TimersInputMarshaller) inCtx.getReaderForInt( _timer.getType().getNumber() );
        reader.deserialize( inCtx, _timer );
    }

    public static Trigger readTrigger( MarshallerReaderContext inCtx,
                                       ProtobufMessages.Trigger _trigger) {
        switch ( _trigger.getType() ) {
            case CRON : {
                ProtobufMessages.Trigger.CronTrigger _cron = _trigger.getCron();
                CronTrigger trigger = new CronTrigger();
                trigger.setStartTime( new Date( _cron.getStartTime() ) );
                if ( _cron.hasEndTime() ) {
                    trigger.setEndTime( new Date( _cron.getEndTime() ) );
                }
                trigger.setRepeatLimit( _cron.getRepeatLimit() );
                trigger.setRepeatCount( _cron.getRepeatCount() );
                trigger.setCronExpression( _cron.getCronExpression() );
                if ( _cron.hasNextFireTime() ) {
                    trigger.setNextFireTime( new Date( _cron.getNextFireTime() ) );
                }
                String[] calendarNames = new String[_cron.getCalendarNameCount()];
                for ( int i = 0; i < calendarNames.length; i++ ) {
                    calendarNames[i] = _cron.getCalendarName( i );
                }
                trigger.setCalendarNames( calendarNames );
                return trigger;
            }
            case INTERVAL : {
                ProtobufMessages.Trigger.IntervalTrigger _interval = _trigger.getInterval();
                IntervalTrigger trigger = new IntervalTrigger();
                trigger.setStartTime( new Date( _interval.getStartTime() ) );
                if ( _interval.hasEndTime() ) {
                    trigger.setEndTime( new Date( _interval.getEndTime() ) );
                }
                trigger.setRepeatLimit( _interval.getRepeatLimit() );
                trigger.setRepeatCount( _interval.getRepeatCount() );
                if ( _interval.hasNextFireTime() ) {
                    long now = inCtx.getWorkingMemory().getSessionClock().getCurrentTime() + _interval.getPeriod();
                    long nextFireTime = Math.max(now, _interval.getNextFireTime());
                    trigger.setNextFireTime( new Date( nextFireTime ) );
                }
                trigger.setPeriod( _interval.getPeriod() );
                String[] calendarNames = new String[_interval.getCalendarNameCount()];
                for ( int i = 0; i < calendarNames.length; i++ ) {
                    calendarNames[i] = _interval.getCalendarName( i );
                }
                trigger.setCalendarNames( calendarNames );
                return trigger;
            }
            case POINT_IN_TIME : {
                return PointInTimeTrigger.createPointInTimeTrigger( _trigger.getPit().getNextFireTime(), null );
            }
            case COMPOSITE_MAX_DURATION : {
                ProtobufMessages.Trigger.CompositeMaxDurationTrigger _cmdTrigger = _trigger.getCmdt();
                CompositeMaxDurationTrigger trigger = new CompositeMaxDurationTrigger();
                if ( _cmdTrigger.hasMaxDurationTimestamp() ) {
                    trigger.setMaxDurationTimestamp( new Date( _cmdTrigger.getMaxDurationTimestamp() ) );
                }
                if ( _cmdTrigger.hasTimerCurrentDate() ) {
                    trigger.setTimerCurrentDate( new Date( _cmdTrigger.getTimerCurrentDate() ) );
                }
                if ( _cmdTrigger.hasTimerTrigger() ) {
                    trigger.setTimerTrigger( readTrigger( inCtx, _cmdTrigger.getTimerTrigger() ) );
                }
                return trigger;
            }
        }
        throw new RuntimeException( "Unable to deserialize Trigger for type: " + _trigger.getType() );

    }

    public static WorkItem readWorkItem( MarshallerReaderContext context ) {
        return PROCESS_MARSHALLER.readWorkItem( context );
    }

    public static class PBActivationsFilter implements ActivationsFilter, AgendaFilter {

        private final Set<ActivationKey> dormantActivations = new HashSet<>();
        private final Map<ActivationKey, Tuple> tuplesCache = new HashMap<>();
        private final Queue<RuleAgendaItem> rneaToFire = new ConcurrentLinkedQueue<>();

        private boolean serializedNodeMemories = false;

        public void addDormantActivation(ActivationKey key) {
            this.dormantActivations.add( key );
        }

        @Override
        public boolean accept(Match match) {
            InternalMatch internalMatch = (InternalMatch) match;
            RuleImpl rule = internalMatch.getRule();
            TerminalNode rtn = internalMatch.getRuleAgendaItem().getTerminalNode();
            ActivationKey activationKey = PersisterHelper.hasNodeMemory( rtn ) && !serializedNodeMemories ?
                    PersisterHelper.createActivationKey(rule.getPackageName(), rule.getName(), internalMatch.getTuple().toObjects(true)) :
                    PersisterHelper.createActivationKey(rule.getPackageName(), rule.getName(), internalMatch.getTuple());

            this.tuplesCache.put(activationKey, internalMatch.getTuple());

            return !dormantActivations.contains(activationKey);
        }

        @Override
        public void accept(RuleAgendaItem activation) {
            rneaToFire.add( activation );
        }

        public Map<ActivationKey, Tuple> getTuplesCache() {
            return tuplesCache;
        }

        public void evaluateRNEAs(final InternalWorkingMemory wm) {
            for ( RuleAgendaItem rai : rneaToFire ) {
                rai.getRuleExecutor().evaluateNetworkIfDirty(wm);
            }
        }

        public void removeEmptyRNEAs(final InternalWorkingMemory wm) {
            for ( RuleAgendaItem rai : rneaToFire ) {
                rai.getRuleExecutor().removeRuleAgendaItemWhenEmpty( );
            }
            rneaToFire.clear();
        }

        public void fireRNEAs(final InternalWorkingMemory wm) {
            for ( RuleAgendaItem rai : rneaToFire ) {
                RuleExecutor ruleExecutor = rai.getRuleExecutor();
                ruleExecutor.evaluateNetworkIfDirty(wm);
                ruleExecutor.removeRuleAgendaItemWhenEmpty( );
            }
            rneaToFire.clear();
        }

        public void withSerializedNodeMemories() {
            serializedNodeMemories = true;
        }
    }
}
