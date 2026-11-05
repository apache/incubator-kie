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
package org.drools.model;

public interface Drools extends DroolsEntryPoint {

    <T> T getRuntime(Class<T> runtimeClass);

    <T> T getContext(Class<T> contextClass);

    DroolsEntryPoint getEntryPoint(String name);

    void halt();

    /**
     * This method will be deprecated in a future version.
     * Use {@link #activateRuleFlowGroup(String)} instead.
     */
    void setFocus(String focus);

    /**
     * Activates the given ruleflow-group, equivalent to
     * {@code getKieRuntime().getAgenda().getRuleFlowGroup(name).activate()}.
     */
    void activateRuleFlowGroup(String name);

    Channel getChannel(String name);

    void logicalInsert(Object object);

    void insertAsync(Object object);
}
