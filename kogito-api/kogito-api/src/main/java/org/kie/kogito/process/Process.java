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
package org.kie.kogito.process;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;

import org.kie.kogito.Model;
import org.kie.kogito.correlation.CompositeCorrelation;
import org.kie.kogito.correlation.CorrelationService;
import org.kie.kogito.internal.process.runtime.KogitoNode;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.Policy;
import org.kie.kogito.process.workitem.WorkItemTransition;

public interface Process<T> {

    ProcessInstance<T> createInstance(T workingMemory);

    ProcessInstance<T> createInstance(String businessKey, T workingMemory);

    ProcessInstance<T> createInstance(String businessKey, CompositeCorrelation correlation, T workingMemory);

    ProcessInstances<T> instances();

    Collection<KogitoNode> findNodes(Predicate<KogitoNode> filter);

    CorrelationService correlations();

    <S> void send(Signal<S> sig);

    T createModel();

    ProcessInstance<? extends Model> createInstance(Model m);

    ProcessInstance<? extends Model> createInstance(String businessKey, Model m);

    String id();

    String name();

    String version();

    String type();

    void activate();

    void deactivate();

    WorkItemTransition newTransition(WorkItem workItem, String transitionId, Map<String, Object> map, Policy... policy);

    KogitoWorkItemHandler getKogitoWorkItemHandler(String workItemHandlerName);
}
