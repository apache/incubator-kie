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
package org.kie.api.runtime;

import org.kie.api.event.KieRuntimeEventManager;
import org.kie.api.runtime.process.StatelessProcessSession;
import org.kie.api.runtime.rule.StatelessRuleSession;

/**
 * StatelessKieSession provides a convenience API, wrapping KieSession. It avoids the need to
 * call dispose(). Stateless sessions do not support
 * iterative invocations, the act of calling execute(...) is a single
 * shot method that will internally instantiate a KieSession, add all the user data and execute user commands, call fireAllRules, and then
 * call dispose(). While the main way to work with this class is via the BatchExecution Command as supported by the CommandExecutor interface,
 * two convenience methods are provided for when simple object insertion is all that's required.
 * Extends RuntimeSession for common session operations.
 * @apiNote Use this for fire-and-forget rule execution where you don't need to maintain a session state
 *          between invocations. For stateful sessions with iterative rule execution, use KieSession instead.
 *
 * <p>
 * A simple example showing a stateless session executing for a given collection of java objects using the convenience api. It will iterate the collection inserting
 * each element in turn
 * </p>
 * <pre>
 * KieServices kieServices = KieServices.Factory.get();
 * KieContainer kContainer = kieServices.getKieClasspathContainer();
 * StatelessKieSession kSession = kContainer.newStatelessKieSession();
 * kSession.execute( collection );
 * </pre>
 *
 * <p>
 * If this was done as a single Command it would be as follows:
 * </p>
 * <pre>
 * KieCommands kieCommands = kieServices.getCommands();
 * kSession.execute( kieCommands.newInsertElements( collection ) );
 * </pre>
 *
 * <p>
 * Note if you wanted to insert the collection itself and not the iterating and insert the elements, then kieCommands.newInsert( collection ) would do the job.
 * </p>
 *
 * <p>
 * The KieCommands interface details the supported commands, all of which can be marshalled using XStream and the BatchExecutionHelper. BatchExecutionHelper provides details
 * on the XML format as well as how to use Drools Pipeline to automate the marshalling of BatchExecution and ExecutionResults.
 * </p>
 *
 * <p>
 * StatelessKieSession support globals, scoped in a number of ways. I'll cover the non-command way first,
 * as commands are scoped to a specific execution call. Globals can be resolved in three ways. The StatelessKieSession
 * supports getGlobals(), which returns a Globals instance. These globals are shared for ALL execution calls, so be especially careful of mutable
 * globals in these cases - as often execution calls can be executed simultaneously in different threads. Globals also supports a delegate, which
 * adds a second way of resolving globals. Calling of setGlobal(String, Object) will actually be set on an internal Collection, identifiers in this internal
 * Collection will have priority over supplied delegate, if one is added. If an identifier cannot be found in
 * the internal Collection, it will then check the delegate Globals, if one has been set.
 * </p>
 *
 * <p>Code snippet for setting a session scoped global:</p>
 * <pre>
 * StatelessKieSession kSession = kContainer.newStatelessKieSession();
 * kSession.setGlobal( "hbnSession", hibernateSession ); // sets a global hibernate session, that can be used for DB interactions in the rules.
 * kSession.execute( collection ); // this will now execute and will be able to resolve the "hbnSession" identifier.
 * </pre>
 *
 * <p>
 * The third way is execution scopped globals using the CommandExecutor and SetGlobal Commands:
 * </p>
 *
 * <pre>
 * List&lt;Command&gt; cmds = new ArrayList&lt;Command&gt;();
 * cmds.add( kieCommands.newSetGlobal( "list1", new ArrayList() ) );
 * cmds.add( kieCommands.newInsert( new Person( "jon", 102 ) ) );
 *
 * kSession.execute( kieCommands.newBatchExecution( cmds ) );
 * </pre>
 *
 * <p>
 * The CommandExecutor interface also supports the ability to export data via "out" parameters. Inserted facts, globals and query results can all be returned.
 * </p>
 *
 * <pre>
 * List&lt;Command&gt; cmds = new ArrayList&lt;Command&gt;();
 * cmds.add( kieCommands.newSetGlobal( "list1", new ArrayList(), true ) );
 * cmds.add( kieCommands.newInsert( new Person( "jon", 102 ), "person" ) );
 * cmds.add( kieCommands.newQuery( "Get People" "getPeople" );
 *
 * ExecutionResults results = ksession.execute( kieCommands.newBatchExecution( cmds ) );
 * results.getValue( "list1" ); // returns the ArrayList
 * results.getValue( "person" ); // returns the inserted fact Person
 * results.getValue( "Get People" );// returns the query as a QueryResults instance.
 * </pre>
 */
public interface StatelessKieSession extends StatelessRuleSession, StatelessProcessSession, RuntimeSession, KieRuntimeEventManager {

}
