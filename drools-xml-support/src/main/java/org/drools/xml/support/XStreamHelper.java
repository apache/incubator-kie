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
package org.drools.xml.support;

import com.thoughtworks.xstream.XStream;
import org.drools.commands.runtime.AdvanceSessionTimeCommand;
import org.drools.commands.runtime.BatchExecutionCommandImpl;
import org.drools.commands.runtime.DisposeCommand;
import org.drools.commands.runtime.GetGlobalCommand;
import org.drools.commands.runtime.GetSessionTimeCommand;
import org.drools.commands.runtime.SetGlobalCommand;
import org.drools.commands.runtime.process.AbortWorkItemCommand;
import org.drools.commands.runtime.process.CompleteWorkItemCommand;
import org.drools.commands.runtime.process.SignalEventCommand;
import org.drools.commands.runtime.process.StartProcessCommand;
import org.drools.commands.runtime.rule.ActivateRuleFlowGroupCommand;
import org.drools.commands.runtime.rule.AgendaGroupSetFocusCommand;
import org.drools.commands.runtime.rule.ClearActivationGroupCommand;
import org.drools.commands.runtime.rule.ClearAgendaCommand;
import org.drools.commands.runtime.rule.ClearAgendaGroupCommand;
import org.drools.commands.runtime.rule.ClearRuleFlowGroupCommand;
import org.drools.commands.runtime.rule.DeleteCommand;
import org.drools.commands.runtime.rule.FireAllRulesCommand;
import org.drools.commands.runtime.rule.FireUntilHaltCommand;
import org.drools.commands.runtime.rule.GetFactHandlesCommand;
import org.drools.commands.runtime.rule.GetObjectCommand;
import org.drools.commands.runtime.rule.GetObjectsCommand;
import org.drools.commands.runtime.rule.InsertElementsCommand;
import org.drools.commands.runtime.rule.InsertObjectCommand;
import org.drools.commands.runtime.rule.ModifyCommand;
import org.drools.commands.runtime.rule.ModifyCommand.SetterImpl;
import org.drools.commands.runtime.rule.QueryCommand;
import org.drools.commands.runtime.rule.UpdateCommand;
import org.drools.core.common.DefaultFactHandle;
import org.drools.commands.runtime.ExecutionResultImpl;
import org.drools.commands.runtime.FlatQueryResults;

public class XStreamHelper {
    public static void setAliases(XStream xstream) {
        xstream.alias( "batch-execution",
                       BatchExecutionCommandImpl.class );
        xstream.alias( "insert",
                       InsertObjectCommand.class );
        xstream.alias( "modify",
                       ModifyCommand.class );
        xstream.alias( "update",
                       UpdateCommand.class );
        xstream.alias( "setters",
                       SetterImpl.class );
        // TODO retract is deprecated and should be removed
        xstream.alias( "retract",
                       DeleteCommand.class );
        xstream.alias( "delete",
                       DeleteCommand.class );
        xstream.alias( "insert-elements",
                       InsertElementsCommand.class );
        xstream.alias( "start-process",
                       StartProcessCommand.class );
        xstream.alias( "signal-event",
                       SignalEventCommand.class );
        xstream.alias( "complete-work-item",
                       CompleteWorkItemCommand.class );
        xstream.alias( "abort-work-item",
                       AbortWorkItemCommand.class );
        xstream.alias( "set-global",
                       SetGlobalCommand.class );
        xstream.alias( "get-global",
                       GetGlobalCommand.class );
        xstream.alias( "get-object",
                       GetObjectCommand.class );
        xstream.alias( "get-objects",
                       GetObjectsCommand.class );
        xstream.alias( "get-fact-handles",
                       GetFactHandlesCommand.class );
        xstream.alias( "execution-results",
                       ExecutionResultImpl.class );
        xstream.alias( "fire-all-rules",
                       FireAllRulesCommand.class );
        xstream.alias( "dispose",
                       DisposeCommand.class );
        xstream.alias( "fire-until-halt",
                       FireUntilHaltCommand.class );
        xstream.alias( "query",
                       QueryCommand.class );
        xstream.alias( "query-results",
                       FlatQueryResults.class );
        xstream.alias( "fact-handle",
                       DefaultFactHandle.class );
        xstream.alias( "set-focus",
                       AgendaGroupSetFocusCommand.class );
        xstream.alias( "activate-ruleflow-group",
                       ActivateRuleFlowGroupCommand.class );
        xstream.alias( "clear-activation-group",
                       ClearActivationGroupCommand.class );
        xstream.alias( "clear-agenda",
                       ClearAgendaCommand.class );
        xstream.alias( "clear-agenda-group",
                       ClearAgendaGroupCommand.class );
        xstream.alias( "clear-ruleflow-group",
                       ClearRuleFlowGroupCommand.class );
        xstream.alias( "get-session-time",
                       GetSessionTimeCommand.class );
        xstream.alias( "advance-session-time",
                       AdvanceSessionTimeCommand.class );
    }
}
