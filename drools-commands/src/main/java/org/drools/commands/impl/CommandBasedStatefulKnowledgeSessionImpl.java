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
package org.drools.commands.impl;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.drools.commands.GetSessionClockCommand;
import org.drools.commands.runtime.AddEventListenerCommand;
import org.drools.commands.runtime.DestroySessionCommand;
import org.drools.commands.runtime.DisposeCommand;
import org.drools.commands.runtime.GetCalendarsCommand;
import org.drools.commands.runtime.GetChannelsCommand;
import org.drools.commands.runtime.GetEnvironmentCommand;
import org.drools.commands.runtime.GetFactCountCommand;
import org.drools.commands.runtime.GetGlobalCommand;
import org.drools.commands.runtime.GetGlobalsCommand;
import org.drools.commands.runtime.GetIdCommand;
import org.drools.commands.runtime.GetKieBaseCommand;
import org.drools.commands.runtime.RegisterChannelCommand;
import org.drools.commands.runtime.RemoveEventListenerCommand;
import org.drools.commands.runtime.SetGlobalCommand;
import org.drools.commands.runtime.UnregisterChannelCommand;
import org.drools.commands.runtime.process.AbortProcessInstanceCommand;
import org.drools.commands.runtime.process.AbortWorkItemCommand;
import org.drools.commands.runtime.process.CompleteWorkItemCommand;
import org.drools.commands.runtime.process.CreateCorrelatedProcessInstanceCommand;
import org.drools.commands.runtime.process.CreateProcessInstanceCommand;
import org.drools.commands.runtime.process.GetProcessEventListenersCommand;
import org.drools.commands.runtime.process.GetProcessInstanceByCorrelationKeyCommand;
import org.drools.commands.runtime.process.GetProcessInstanceCommand;
import org.drools.commands.runtime.process.GetProcessInstancesCommand;
import org.drools.commands.runtime.process.GetWorkItemCommand;
import org.drools.commands.runtime.process.ReTryWorkItemCommand;
import org.drools.commands.runtime.process.RegisterWorkItemHandlerCommand;
import org.drools.commands.runtime.process.SignalEventCommand;
import org.drools.commands.runtime.process.StartCorrelatedProcessCommand;
import org.drools.commands.runtime.process.StartProcessCommand;
import org.drools.commands.runtime.process.StartProcessFromNodeIdsCommand;
import org.drools.commands.runtime.process.StartProcessInstanceCommand;
import org.drools.commands.runtime.rule.ActivateRuleFlowGroupCommand;
import org.drools.commands.runtime.rule.AgendaGroupSetFocusCommand;
import org.drools.commands.runtime.rule.ClearActivationGroupCommand;
import org.drools.commands.runtime.rule.ClearAgendaCommand;
import org.drools.commands.runtime.rule.ClearAgendaGroupCommand;
import org.drools.commands.runtime.rule.ClearRuleFlowGroupCommand;
import org.drools.commands.runtime.rule.DeleteCommand;
import org.drools.commands.runtime.rule.FireAllRulesCommand;
import org.drools.commands.runtime.rule.FireUntilHaltCommand;
import org.drools.commands.runtime.rule.GetAgendaEventListenersCommand;
import org.drools.commands.runtime.rule.GetEntryPointCommand;
import org.drools.commands.runtime.rule.GetEntryPointsCommand;
import org.drools.commands.runtime.rule.GetFactHandleCommand;
import org.drools.commands.runtime.rule.GetFactHandlesCommand;
import org.drools.commands.runtime.rule.GetObjectCommand;
import org.drools.commands.runtime.rule.GetObjectsCommand;
import org.drools.commands.runtime.rule.GetRuleRuntimeEventListenersCommand;
import org.drools.commands.runtime.rule.HaltCommand;
import org.drools.commands.runtime.rule.InsertObjectCommand;
import org.drools.commands.runtime.rule.QueryCommand;
import org.drools.commands.runtime.rule.UpdateCommand;
import org.drools.core.impl.AbstractRuntime;
import org.drools.core.process.WorkItem;
import org.drools.core.process.WorkItemManager;
import org.drools.base.rule.EntryPointId;
import org.kie.api.KieBase;
import org.kie.api.command.Command;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.runtime.Calendars;
import org.kie.api.runtime.Channel;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.ExecutableRunner;
import org.kie.api.runtime.Globals;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.ObjectFilter;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.rule.ActivationGroup;
import org.kie.api.runtime.rule.Agenda;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.AgendaGroup;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.LiveQuery;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.RuleFlowGroup;
import org.kie.api.runtime.rule.ViewChangedEventListener;
import org.kie.api.time.SessionClock;
import org.kie.internal.command.RegistryContext;
import org.kie.internal.process.CorrelationAwareProcessRuntime;
import org.kie.internal.process.CorrelationKey;
import org.kie.internal.runtime.CommandBasedStatefulKnowledgeSession;

import static java.util.Arrays.stream;

public class CommandBasedStatefulKnowledgeSessionImpl extends AbstractRuntime
    implements CommandBasedStatefulKnowledgeSession, CorrelationAwareProcessRuntime {

    private ExecutableRunner runner;
    private transient WorkItemManager workItemManager;
    private transient Agenda          agenda;

    public CommandBasedStatefulKnowledgeSessionImpl(ExecutableRunner runner ) {
        this.runner = runner;
    }

    /**
     * Deprecated use {@link #getIdentifier()} instead
     */
    @Deprecated
    public int getId() {
        return runner.execute( new GetIdCommand() ).intValue();
    }
    public long getIdentifier() {
        return runner.execute( new GetIdCommand() );
    }

    public ProcessInstance getProcessInstance(String id) {
        GetProcessInstanceCommand command = new GetProcessInstanceCommand();
        command.setProcessInstanceId( id );
        return runner.execute( command );
    }

    public ProcessInstance getProcessInstance(String id, boolean readOnly) {
        GetProcessInstanceCommand command = new GetProcessInstanceCommand();
        command.setProcessInstanceId( id );
        command.setReadOnly( readOnly );
        return runner.execute( command );
    }

    public void abortProcessInstance(String id) {
        AbortProcessInstanceCommand command = new AbortProcessInstanceCommand();
        command.setProcessInstanceId( id );
        runner.execute( command );
    }

    public ExecutableRunner getRunner() {
        return runner;
    }

    public KieSession getKieSession() {
        return (( RegistryContext )getRunner().createContext()).lookup( KieSession.class );
    }

    public Collection<ProcessInstance> getProcessInstances() {
        return runner.execute( new GetProcessInstancesCommand() );
    }

    public WorkItemManager getWorkItemManager() {
        if ( workItemManager == null ) {
            workItemManager = new WorkItemManager() {
                public void completeWorkItem(long id,
                                             Map<String, Object> results) {
                    CompleteWorkItemCommand command = new CompleteWorkItemCommand();
                    command.setWorkItemId( id );
                    command.setResults( results );
                    runner.execute( command );
                }

                public void abortWorkItem(long id) {
                    AbortWorkItemCommand command = new AbortWorkItemCommand();
                    command.setWorkItemId( id );
                    runner.execute( command );
                }

                public void registerWorkItemHandler(String workItemName,
                                                    WorkItemHandler handler) {
                    RegisterWorkItemHandlerCommand command = new RegisterWorkItemHandlerCommand();
                    command.setWorkItemName( workItemName );
                    command.setHandler( handler );
                    runner.execute( command );
                }

                public WorkItem getWorkItem(long id) {
                    GetWorkItemCommand command = new GetWorkItemCommand();
                    command.setWorkItemId( id );
                    return runner.execute( command );
                }

                public void clear() {
                    throw new UnsupportedOperationException();
                }

                public Set<WorkItem> getWorkItems() {
                    throw new UnsupportedOperationException();
                }

                public void internalAbortWorkItem(long id) {
                    throw new UnsupportedOperationException();
                }

                public void internalAddWorkItem(WorkItem workItem) {
                    throw new UnsupportedOperationException();
                }

                public void internalExecuteWorkItem(WorkItem workItem) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void signalEvent(String type, Object event) {
                    SignalEventCommand command = new SignalEventCommand(type, event);
                    runner.execute(command);
                }

                @Override
                public void signalEvent(String type, Object event, String processInstanceId) {
                    SignalEventCommand command = new SignalEventCommand(processInstanceId, type, event);
                    runner.execute(command);
                }

                @Override
                public void dispose() {
                    // no-op
                }

                @Override
                public void retryWorkItem( Long workItemID, Map<String, Object> params ) {
                   ReTryWorkItemCommand command = new ReTryWorkItemCommand(workItemID,params);
                    runner.execute( command );
                }
            };
        }
        return workItemManager;
    }

    public void signalEvent(String type,
                            Object event) {
        SignalEventCommand command = new SignalEventCommand( type,
                                                             event );
        runner.execute( command );
    }

    public void signalEvent(String type,
                            Object event,
                            String processInstanceId) {
        SignalEventCommand command = new SignalEventCommand( processInstanceId,
                                                             type,
                                                             event );
        runner.execute( command );
    }

    public ProcessInstance startProcess(String processId) {
        return startProcess(processId, null, (AgendaFilter) null);
    }

    public ProcessInstance startProcess(String processId, Map<String, Object> parameters) {
        return startProcess(processId, parameters, null);
    }

    public ProcessInstance startProcess(String processId, AgendaFilter agendaFilter) {
        return startProcess(processId, null, agendaFilter);
    }

    public ProcessInstance startProcess(String processId, Map<String, Object> parameters, AgendaFilter agendaFilter) {
        StartProcessCommand command = new StartProcessCommand();
        command.setProcessId( processId );
        command.setParameters( parameters );
        command.setAgendaFilter( agendaFilter );
        return runner.execute( command );
    }

    public ProcessInstance createProcessInstance(String processId,
			                                     Map<String, Object> parameters) {
        CreateProcessInstanceCommand command = new CreateProcessInstanceCommand();
        command.setProcessId( processId );
        command.setParameters( parameters );
        return runner.execute( command );
	}

	public ProcessInstance startProcessInstance(String processInstanceId) {
        StartProcessInstanceCommand command = new StartProcessInstanceCommand();
        command.setProcessInstanceId( processInstanceId );
        return runner.execute( command );
	}

    public void dispose() {
        runner.execute( new DisposeCommand() );
    }

    public void destroy() {
        runner.execute( new DestroySessionCommand(runner));
    }

    public int fireAllRules() {
        return this.runner.execute( new FireAllRulesCommand() );
    }

    public int fireAllRules(int max) {
        return this.runner.execute( new FireAllRulesCommand( max ) );
    }

    public int fireAllRules(AgendaFilter agendaFilter) {
        return this.runner.execute( new FireAllRulesCommand( agendaFilter ) );
    }

    public int fireAllRules(AgendaFilter agendaFilter, int max) {
        return this.runner.execute( new FireAllRulesCommand( agendaFilter, max ) );
    }

    public void fireUntilHalt() {
        this.runner.execute( new FireUntilHaltCommand() );
    }

    public void fireUntilHalt(AgendaFilter agendaFilter) {
        this.runner.execute( new FireUntilHaltCommand( agendaFilter ) );
    }

    public KieBase getKieBase() {
        return this.runner.execute( new GetKieBaseCommand() );
    }

    public void registerChannel(String name,
                                Channel channel) {
        this.runner.execute( new RegisterChannelCommand( name,
                                                                 channel ) );
    }

    public void unregisterChannel(String name) {
        this.runner.execute( new UnregisterChannelCommand( name ) );
    }

    @SuppressWarnings("unchecked")
    public Map<String, Channel> getChannels() {
        return (Map<String, Channel>) this.runner.execute( new GetChannelsCommand() );
    }

    public Agenda getAgenda() {
        if ( agenda == null ) {
            agenda = new Agenda() {
                public void clear() {
                    ClearAgendaCommand command = new ClearAgendaCommand();
                    runner.execute( command );
                }

                public ActivationGroup getActivationGroup(final String name) {
                    return new ActivationGroup() {
                        public void clear() {
                            ClearActivationGroupCommand command = new ClearActivationGroupCommand();
                            command.setName( name );
                            runner.execute( command );
                        }

                        public String getName() {
                            return name;
                        }
                    };
                }

                public AgendaGroup getAgendaGroup(final String name) {
                    return new AgendaGroup() {
                        public void clear() {
                            ClearAgendaGroupCommand command = new ClearAgendaGroupCommand();
                            command.setName( name );
                            runner.execute( command );
                        }

                        public String getName() {
                            return name;
                        }

                        public void setFocus() {
                            AgendaGroupSetFocusCommand command = new AgendaGroupSetFocusCommand();
                            command.setName( name );
                            runner.execute( command );
                        }
                    };
                }

                public RuleFlowGroup getRuleFlowGroup(final String name) {
                    return new RuleFlowGroup() {
                        public void clear() {
                            ClearRuleFlowGroupCommand command = new ClearRuleFlowGroupCommand();
                            command.setName( name );
                            runner.execute( command );
                        }

                        public String getName() {
                            return name;
                        }

                        public void activate() {
                            ActivateRuleFlowGroupCommand command = new ActivateRuleFlowGroupCommand( name );
                            runner.execute( command );
                        }
                    };
                }
            };
        }
        return agenda;
    }

    public FactHandle getFactHandle(Object object) {
        return this.runner.execute( new GetFactHandleCommand( object ) );
    }

    public <T extends FactHandle> Collection<T> getFactHandles() {
        return (Collection<T>) this.runner.execute( new GetFactHandlesCommand() );

    }

    public <T extends FactHandle> Collection<T> getFactHandles(ObjectFilter filter) {
        return (Collection<T>) this.runner.execute( new GetFactHandlesCommand( filter ) );
    }

    public Collection<? extends Object> getObjects() {
        return getObjects( null );
    }

    public Collection<? extends Object> getObjects(ObjectFilter filter) {
        Collection result = runner.execute( new GetObjectsCommand( filter ) );
        return result;
    }

    @SuppressWarnings("unchecked")
    public <T extends SessionClock> T getSessionClock() {
        return (T) this.runner.execute( new GetSessionClockCommand() );
    }

    public EntryPoint getEntryPoint(String name) {
        return this.runner.execute( new GetEntryPointCommand( name ) );
    }

    public Collection< ? extends EntryPoint> getEntryPoints() {
        return this.runner.execute( new GetEntryPointsCommand() );
    }

    public void halt() {
        this.runner.execute( new HaltCommand() );
    }

    public FactHandle insert(Object object) {
        return runner.execute( new InsertObjectCommand( object ) );
    }

    public void submit( AtomicAction action ) {
        throw new UnsupportedOperationException( "It is not necessary to use submit with a command based session, commands are already atomic" );
    }

    @Override
    public <T> T getKieRuntime( Class<T> cls ) {
        throw new UnsupportedOperationException( "Retrieving runtimes is not supported  throught the command based session at this time." );
    }

    public void retract(FactHandle handle) {
        runner.execute( new DeleteCommand( handle ) );
    }

    public void delete(FactHandle handle) {
        runner.execute( new DeleteCommand( handle ) );
    }

    public void delete(FactHandle handle, FactHandle.State fhState) {
        runner.execute( new DeleteCommand( handle, fhState ) );
    }

    public void update(FactHandle handle,
                       Object object) {
        runner.execute( new UpdateCommand( handle,
                                                   object ) );
    }

    public void update(FactHandle handle,
                       Object object,
                       String... modifiedProperties) {
        runner.execute( new UpdateCommand( handle,
                                                   object,
                                                   modifiedProperties ) );
    }

    public void addEventListener(RuleRuntimeEventListener listener) {
        runner.execute(new AddEventListenerCommand(listener));
    }

    public void addEventListener(AgendaEventListener listener) {
        runner.execute( new AddEventListenerCommand( listener ) );
    }

    public Collection<AgendaEventListener> getAgendaEventListeners() {
        return runner.execute( new GetAgendaEventListenersCommand() );
    }

    public Collection<RuleRuntimeEventListener> getRuleRuntimeEventListeners() {
        return runner.execute( new GetRuleRuntimeEventListenersCommand() );
    }

    public void removeEventListener(RuleRuntimeEventListener listener) {
        runner.execute( new RemoveEventListenerCommand( listener ) );
    }

    public void removeEventListener(AgendaEventListener listener) {
        runner.execute( new RemoveEventListenerCommand( listener ) );
    }

    public void addEventListener(ProcessEventListener listener) {
        runner.execute( new AddEventListenerCommand( listener ) );
    }

    public Collection<ProcessEventListener> getProcessEventListeners() {
        return runner.execute( new GetProcessEventListenersCommand() );
    }

    public void removeEventListener(ProcessEventListener listener) {
        runner.execute( new RemoveEventListenerCommand( listener ) );
    }

    public Object getGlobal(String identifier) {
        return runner.execute( new GetGlobalCommand( identifier ) );
    }

    public void setGlobal(String identifier,
                          Object object) {
        this.runner.execute( new SetGlobalCommand( identifier,
                                                           object ) );
    }

    public Globals getGlobals() {
        return runner.execute( new GetGlobalsCommand() );
    }

    public Calendars getCalendars() {
        return runner.execute( new GetCalendarsCommand() );
    }

    public Object getObject(FactHandle factHandle) {
        return runner.execute( new GetObjectCommand( factHandle ) );
    }

    public Environment getEnvironment() {
        return runner.execute( new GetEnvironmentCommand() );
    }

    public <T> T execute(Command<T> command) {
        return this.runner.execute(command);
    }

    public QueryResults getQueryResults(String query,
                                        Object... arguments) {
        QueryCommand cmd = new QueryCommand(null,
                                            query,
                                            arguments );
        return this.runner.execute( cmd );
    }

    public String getEntryPointId() {
        return EntryPointId.DEFAULT.getEntryPointId();
    }

    public long getFactCount() {
        return runner.execute( new GetFactCountCommand() );
    }

    public LiveQuery openLiveQuery(String query,
                                   Object[] arguments,
                                   ViewChangedEventListener listener) {
        // TODO: implement thiss        
        return null;
    }
    
    public KieSessionConfiguration getSessionConfiguration() {
        return ((RegistryContext) runner.createContext()).lookup(KieSession.class ).getSessionConfiguration();
    }

    @Override
    public ProcessInstance startProcess(String processId,
            CorrelationKey correlationKey, Map<String, Object> parameters) {
        
        return this.runner.execute(new StartCorrelatedProcessCommand(processId, correlationKey, parameters));
    }

    @Override
    public ProcessInstance createProcessInstance(String processId,
            CorrelationKey correlationKey, Map<String, Object> parameters) {
        
        return this.runner.execute(
                new CreateCorrelatedProcessInstanceCommand(processId, correlationKey, parameters));
    }

    @Override
    public ProcessInstance getProcessInstance(CorrelationKey correlationKey) {
        
        return this.runner.execute(new GetProcessInstanceByCorrelationKeyCommand(correlationKey));
    }


    @Override
    public ProcessInstance startProcessFromNodeIds(String processId, CorrelationKey key, Map<String, Object> params, String... nodeIds) {
        StartProcessFromNodeIdsCommand command = new StartProcessFromNodeIdsCommand();
        command.setProcessId(processId);
        command.setParameters(params);
        command.setNodeIds(stream(nodeIds).collect(Collectors.toList()));
        command.setCorrelationKey(key);
        return runner.execute(command);
    }

    @Override
    public ProcessInstance startProcessFromNodeIds(String processId, Map<String, Object> params, String... nodeIds) {
        return startProcessFromNodeIds(processId, null, params, nodeIds);
    }
}
