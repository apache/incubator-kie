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
package org.kie.api.command;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.kie.api.internal.utils.KieService;
import org.kie.api.runtime.ExecutionResults;
import org.kie.api.runtime.ObjectFilter;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.rule.FactHandle;

import static java.util.Arrays.asList;

/**
 * KieCommands is a factory for Commands that can be used by classes that implement CommandExecutor. Typically more than one Command
 * will want to be executed, where is where the BatchExecution comes in, which takes a List of commands, think of it as CompositeCommand.
 */
public interface KieCommands extends KieService {
    Command newInsert(Object object);

    Command newDispose();

    Command newInsert(Object object, String outIdentifier);

    Command newInsert(Object object, String outIdentifier, boolean returnObject, String entryPoint);

    Command newInsertElements(Iterable objects);

    Command newInsertElements(Iterable objects, String outIdentifier);

    Command newInsertElements(Iterable objects, String outIdentifier, boolean returnObject, String entryPoint);

    Command newDelete(FactHandle factHandle);

    Command newDeleteObject(Object object, String entryPoint);

    Setter newSetter(String accessor,
                     String value);

    Command newModify(FactHandle factHandle,
                      List<Setter> setters);

    Command newFireAllRules();

    Command newFireAllRules(int max);

    Command newFireAllRules(String outidentifier);

    Command newGetFactHandle(Object object);

    Command newGetFactHandleInEntryPoint(Object object, String entryPoint);

    Command newGetObject(FactHandle factHandle);

    Command newGetObject(FactHandle factHandle, String outIdentifier);

    Command newGetObjects();

    Command newGetObjects(String outIdentifier);

    Command newGetObjects(ObjectFilter filter);

    Command newGetObjects(ObjectFilter filter, String outIdentifier);

    Command newSetGlobal(String identifie,
                         Object object);

    Command newSetGlobal(String identifier,
                         Object object,
                         boolean out);

    Command newSetGlobal(String identifier,
                         Object object,
                         String outIdentifier);

    Command newGetGlobal(String identifier);

    Command newGetGlobal(String identifier,
                         String outIdentifier);

    Command newStartProcess(String processId);

    Command newStartProcess(String processId,
                            Map<String, Object> parameters);

    Command newSignalEvent(String type,
                           Object event);

    Command newSignalEvent(String processInstanceId,
                           String type,
                           Object event);

    Command newQuery(String identifier,
                     String name);

    Command newQuery(String identifier,
                     String name,
                     Object[] arguments);

    /**
     * Creates a batch execution command from varargs.
     * @param commands the commands to execute
     * @return the batch execution command
     * @since 10.1.0
     * @apiNote Convenience method for varargs instead of List
     */
    default BatchExecutionCommand newBatchExecution(Command... commands) {
        return newBatchExecution( asList(commands) );
    }

    BatchExecutionCommand newBatchExecution(List< ? extends Command> commands);

    BatchExecutionCommand newBatchExecution(List< ? extends Command> commands, String lookup);

    Command newRegisterWorkItemHandlerCommand(WorkItemHandler handler, String workItemName);

    Command newAbortWorkItem(long workItemId);

    Command newCompleteWorkItem(long workItemId,
                                Map<String, Object> results);

    Command<FactHandle> fromExternalFactHandleCommand(String factHandleExternalForm);

    Command<FactHandle> fromExternalFactHandleCommand(String factHandleExternalForm, boolean disconnected);

    Command newAgendaGroupSetFocus(String name);
    
    Command newGetFactHandles();

    Command newGetFactHandles(String outIdentifier);

    Command newGetFactHandles(ObjectFilter filter);

    Command newGetFactHandles(ObjectFilter filter, String outIdentifier);

    Command<Long> newGetSessionTime();
    Command<Long> newGetSessionTime(String outIdentifier);

    Command<Long> newAdvanceSessionTime(long amount, TimeUnit unit);
    Command<Long> newAdvanceSessionTime(long amount, TimeUnit unit, String outIdentifier);

    /**
     * Creates a command to apply a PMML model.
     * @param request the PMML request data as a Map
     * @return the command
     * @since 10.2.0
     * @apiNote Replaces deprecated newApplyPmmlModel(PMMLRequestData). Convert PMMLRequestData to Map before calling.
     */
    Command newApplyPmmlModel(Map<String, Object> request);

    ExecutionResults newExecutionResults();
}
