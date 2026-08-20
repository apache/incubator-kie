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

import org.kie.api.KieBase;

import java.util.Map;

/**
 * Common interface for runtime session operations shared by stateful and stateless sessions.
 * @since 10.1.0
 * @apiNote Use this interface when writing code that works with both KieRuntime
 *          and StatelessKieSession to avoid coupling to specific session types.
 */
public interface RuntimeSession extends CommandExecutor {

    /**
     * @return the Globals store
     */
    Globals getGlobals();

    /**
     * Sets a global value on the globals store
     *
     * @param identifier the global identifier
     * @param value the value assigned to the global identifier
     */
    void setGlobal(String identifier,
                   Object value);


    /**
     * Registers a channel with the given name
     *
     * @param name the name of the channel
     * @param channel the channel instance. It has to be thread safe.
     */
    void registerChannel(String name,
                         Channel channel);

    /**
     * Unregisters the channel with the given name
     *
     * @param name
     */
    void unregisterChannel(String name);

    /**
     * @return a map with all registered channels.
     */
    Map<String, Channel> getChannels();

    /**
     * @return the KieBase reference from which this stateless session was created.
     */
    KieBase getKieBase();
}
