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
import org.kie.api.runtime.process.ProcessRuntime;
import org.kie.api.runtime.rule.RuleRuntime;
import org.kie.api.time.SessionClock;

/**
 * KieRuntime provides runtime capabilities for rule and process execution.
 * Extends RuntimeSession for common session operations.
 * @apiNote This is the primary interface for stateful rule sessions. For stateless
 *          execution, use StatelessKieSession instead.
 */
public interface KieRuntime extends RuntimeSession, RuleRuntime, ProcessRuntime, KieRuntimeEventManager {

    /**
     * @return the session clock instance assigned to this session
     */
    <T extends SessionClock> T getSessionClock();

    Object getGlobal(String identifier);

    Calendars getCalendars();

    Environment getEnvironment();

    KieSessionConfiguration getSessionConfiguration();
}
