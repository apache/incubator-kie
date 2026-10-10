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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.kie.kogito.MapOutput;
import org.kie.kogito.MappableToModel;
import org.kie.kogito.Model;
import org.kie.kogito.correlation.CompositeCorrelation;
import org.kie.kogito.process.workitem.Policy;

public interface ProcessService {

    default <T extends Model> ProcessInstance<T> createProcessInstance(Process<T> process, String businessKey,
            T model,
            String startFromNodeId) {
        return createProcessInstance(process, businessKey, model, Collections.emptyMap(), startFromNodeId);
    }

    <T extends Model> ProcessInstance<T> createProcessInstance(Process<T> process, String businessKey,
            T model, Map<String, List<String>> headers,
            String startFromNodeId);

    default <T extends Model> ProcessInstance<T> createProcessInstance(Process<T> process, String businessKey, T model,
            String startFromNodeId,
            String trigger,
            String kogitoReferenceId,
            CompositeCorrelation correlation) {
        return createProcessInstance(process, businessKey, model, Collections.emptyMap(), startFromNodeId, trigger, kogitoReferenceId, correlation);
    }

    <T extends Model> ProcessInstance<T> createProcessInstance(Process<T> process, String businessKey, T model,
            Map<String, List<String>> headers,
            String startFromNodeId,
            String trigger,
            String kogitoReferenceId,
            CompositeCorrelation correlation);

    <T extends MappableToModel<R>, R> List<R> getProcessInstanceOutput(Process<T> process);

    <T extends MappableToModel<R>, R> Optional<R> findById(Process<T> process, String id);

    <T extends MappableToModel<R>, R> Optional<R> signalProcessInstance(Process<T> process, String id, Object data, String signalName);

    <T> void migrateProcessInstances(Process<T> process, String targetProcessId, String targetProcessVersion, String... id) throws UnsupportedOperationException;

    <T> long migrateAll(Process<T> process, String targetProcessId, String targetProcessVersion) throws UnsupportedOperationException;

    <T extends MappableToModel<R>, R> Optional<R> delete(Process<T> process, String id);

    <T extends MappableToModel<R>, R> Optional<R> update(Process<T> process, String id, T resource);

    <T extends MappableToModel<R>, R> Optional<R> updatePartial(Process<T> process, String id, T resource);

    <T extends Model> Optional<List<WorkItem>> getWorkItems(Process<T> process, String id, Policy... policy);

    <T extends Model> Optional<WorkItem> signalWorkItem(Process<T> process, String id, String taskNodeName, Policy... policy);

    <T extends Model, R extends MapOutput> Optional<R> setWorkItemOutput(Process<T> process,
            String id,
            String taskId,
            Policy policy,
            MapOutput model,
            Function<Map<String, Object>, R> mapper);

    <T extends MappableToModel<R>, R> Optional<R> transitionWorkItem(
            Process<T> process,
            String id,
            String taskId,
            String phase,
            Policy policy,
            MapOutput model);

    <T extends MappableToModel<?>, R> Optional<R> getWorkItem(Process<T> process,
            String id,
            String taskId,
            Policy policy,
            Function<WorkItem, R> mapper);

    //Schema
    <T extends Model> Map<String, Object> getWorkItemSchemaAndPhases(Process<T> process,
            String id,
            String taskId,
            String taskName,
            Policy policy);

}
