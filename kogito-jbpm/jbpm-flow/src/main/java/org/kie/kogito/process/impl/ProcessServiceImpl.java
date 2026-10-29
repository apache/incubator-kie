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
package org.kie.kogito.process.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jbpm.util.JsonSchemaUtil;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.jbpm.workflow.instance.node.WorkItemNodeInstance;
import org.kie.kogito.Application;
import org.kie.kogito.MapOutput;
import org.kie.kogito.MappableToModel;
import org.kie.kogito.Model;
import org.kie.kogito.config.ConfigBean;
import org.kie.kogito.correlation.CompositeCorrelation;
import org.kie.kogito.internal.process.runtime.KogitoNode;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.Policy;
import org.kie.kogito.internal.process.workitem.WorkItemNotFoundException;
import org.kie.kogito.internal.utils.ConversionUtils;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstanceNotFoundException;
import org.kie.kogito.process.ProcessInstanceReadMode;
import org.kie.kogito.process.ProcessService;
import org.kie.kogito.process.SignalFactory;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.services.uow.UnitOfWorkExecutor;

import static java.util.Collections.emptyMap;

public class ProcessServiceImpl implements ProcessService {

    private final Application application;
    private final short processInstanceLimit;

    public ProcessServiceImpl(Application application) {
        this.application = application;
        this.processInstanceLimit = application.config().get(ConfigBean.class).processInstanceLimit();
    }

    @Override
    public <T extends Model> ProcessInstance<T> createProcessInstance(Process<T> process, String businessKey,
            T model, Map<String, List<String>> headers,
            String startFromNodeId) {
        return UnitOfWorkExecutor.executeInUnitOfWork(application.unitOfWorkManager(), () -> {
            ProcessInstance<T> pi = process.createInstance(businessKey, model);
            if (startFromNodeId != null) {
                pi.startFrom(startFromNodeId, headers);
            } else {
                pi.start(headers);
            }
            return pi;
        });
    }

    @Override
    public <T extends Model> ProcessInstance<T> createProcessInstance(Process<T> process, String businessKey,
            T model,
            Map<String, List<String>> headers,
            String startFromNodeId,
            String trigger,
            String kogitoReferenceId,
            CompositeCorrelation correlation) {
        return UnitOfWorkExecutor.executeInUnitOfWork(application.unitOfWorkManager(), () -> {
            ProcessInstance<T> pi = process.createInstance(businessKey, correlation, model);
            if (startFromNodeId != null) {
                pi.startFrom(startFromNodeId, kogitoReferenceId, headers);
            } else {
                pi.start(trigger, kogitoReferenceId, headers);
            }
            return pi;
        });
    }

    @Override
    public <T extends MappableToModel<R>, R> List<R> getProcessInstanceOutput(Process<T> process) {
        try (Stream<ProcessInstance<T>> stream = process.instances().stream().limit(processInstanceLimit)) {
            return stream.map(ProcessInstance::variables)
                    .map(MappableToModel::toModel)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public <T extends MappableToModel<R>, R> Optional<R> findById(Process<T> process, String id) {
        Optional<ProcessInstance<T>> instance = process.instances()
                .findById(id, ProcessInstanceReadMode.READ_ONLY);
        Optional<T> mappable = instance.map(ProcessInstance::variables);
        return mappable.map(MappableToModel::toModel);
    }

    @Override
    public <T> void migrateProcessInstances(Process<T> process, String targetProcessId, String targetProcessVersion, String... processIds) throws UnsupportedOperationException {
        process.instances().migrateProcessInstances(targetProcessId, targetProcessVersion, processIds);
    }

    @Override
    public <T> long migrateAll(Process<T> process, String targetProcessId, String targetProcessVersion) throws UnsupportedOperationException {
        return process.instances().migrateAll(targetProcessId, targetProcessVersion);
    }

    @Override
    public <T extends MappableToModel<R>, R> Optional<R> delete(Process<T> process, String id) {
        return UnitOfWorkExecutor.executeInUnitOfWork(
                application.unitOfWorkManager(),
                () -> process
                        .instances()
                        .findById(id)
                        .map(pi -> {
                            pi.abort();
                            return pi;
                        })
                        .map(ProcessInstance::variables)
                        .map(MappableToModel::toModel));
    }

    @Override
    public <T extends MappableToModel<R>, R> Optional<R> update(Process<T> process, String id, T resource) {
        return UnitOfWorkExecutor.executeInUnitOfWork(
                application.unitOfWorkManager(),
                () -> process
                        .instances()
                        .findById(id)
                        .map(pi -> pi.updateVariables(resource))
                        .map(MappableToModel::toModel));
    }

    @Override
    public <T extends MappableToModel<R>, R> Optional<R> updatePartial(Process<T> process, String id, T resource) {
        return UnitOfWorkExecutor.executeInUnitOfWork(
                application.unitOfWorkManager(),
                () -> process
                        .instances()
                        .findById(id)
                        .map(pi -> pi.updateVariablesPartially(resource))
                        .map(MappableToModel::toModel));
    }

    @Override
    public <T extends MappableToModel<R>, R> Optional<R> signalProcessInstance(Process<T> process, String id, Object data, String signalName) {
        Objects.requireNonNull(signalName, "signalName must not be null");
        return UnitOfWorkExecutor.executeInUnitOfWork(
                application.unitOfWorkManager(),
                () -> process.instances()
                        .findById(id)
                        .filter(pi -> {
                            if (pi.events().stream().anyMatch(e -> signalName.equals(e.getEvent()))) {
                                return true;
                            }
                            return pi.adHocFragments()
                                    .stream()
                                    .anyMatch(f -> f.getName().equals(signalName));
                        })
                        .map(pi -> {
                            pi.send(SignalFactory.of(signalName, data));
                            return pi.checkError().variables().toModel();
                        }));
    }

    @Override
    public <T extends Model> void signalProcess(Process<T> process, Object data, String signalName) {
        Objects.requireNonNull(signalName, "signalName must not be null");
        UnitOfWorkExecutor.executeInUnitOfWork(
                application.unitOfWorkManager(),
                () -> {
                    process.send(SignalFactory.of(signalName, data));
                    return null;
                });
    }

    @Override
    public <T extends Model> Optional<List<WorkItem>> getWorkItems(Process<T> process, String id, Policy... policy) {
        return process.instances()
                .findById(id, ProcessInstanceReadMode.READ_ONLY)
                .map(pi -> pi.workItems(WorkItemNodeInstance.class::isInstance, policy));
    }

    @Override
    public <T extends Model> Optional<WorkItem> signalWorkItem(Process<T> process, String id, String taskNodeName, Policy... policy) {
        return UnitOfWorkExecutor.executeInUnitOfWork(application.unitOfWorkManager(), () -> {
            Optional<ProcessInstance<T>> piFound = process.instances().findById(id);
            if (piFound.isEmpty()) {
                return Optional.empty();
            }

            ProcessInstance<T> pi = piFound.get();
            return findWorkItem(pi, taskNodeName, policy);
        });
    }

    private <T extends Model> Optional<WorkItem> findWorkItem(ProcessInstance<T> pi, String name, Policy... policy) {
        Collection<KogitoNode> nodes = pi.process().findNodes(node -> {
            if (!(node instanceof WorkItemNode workItemNode)) {
                return false;
            }
            String taskName = (String) workItemNode.getWork().getParameters().getOrDefault("TaskName", node.getName());
            return name.equals(taskName) || name.equals(ConversionUtils.sanitizeName(taskName));
        });
        if (nodes.isEmpty()) {
            return Optional.empty();
        }
        nodes.stream().map(KogitoNode::getName).distinct().forEach(nodeName -> pi.send(SignalFactory.of(nodeName, emptyMap())));
        return getWorkItemByNodes(pi, nodes, policy);
    }

    private <T extends Model> Optional<WorkItem> getWorkItemByNodes(ProcessInstance<T> pi, Collection<KogitoNode> nodes, Policy... policies) {
        return pi.workItems(policies)
                .stream()
                .filter(wi -> nodes.stream().anyMatch(node -> node.getId().equals(wi.getNodeId())))
                .findFirst();
    }

    @Override
    public <T extends MappableToModel<R>, R> Optional<R> transitionWorkItem(
            Process<T> process,
            String processInstanceId,
            String workItemId,
            String phaseId,
            Policy policy,
            MapOutput model) {

        return UnitOfWorkExecutor.executeInUnitOfWork(application.unitOfWorkManager(), () -> {
            return process.instances()
                    .findById(processInstanceId)
                    .map(pi -> {
                        WorkItem workItem = pi.workItem(workItemId, policy);
                        pi.transitionWorkItem(workItemId, process.newTransition(workItem, phaseId, model.toMap(), policy));
                        return pi.variables().toModel();
                    });
        });
    }

    @Override
    public <T extends MappableToModel<?>, R> Optional<R> getWorkItem(Process<T> process,
            String id,
            String taskId,
            Policy policy,
            Function<WorkItem, R> mapper) {
        return process.instances()
                .findById(id, ProcessInstanceReadMode.READ_ONLY)
                .map(pi -> pi.workItem(taskId, policy))
                .map(mapper);
    }

    @Override
    public <T extends Model, R extends MapOutput> Optional<R> setWorkItemOutput(Process<T> process,
            String id,
            String taskId,
            Policy policy,
            MapOutput model,
            Function<Map<String, Object>, R> mapper) {
        return UnitOfWorkExecutor.executeInUnitOfWork(application.unitOfWorkManager(), () -> {
            return process.instances().findById(id)
                    .map(pi -> pi.updateWorkItem(taskId, wi -> {
                        wi.setOutputs(model.toMap());
                        return model.toMap();
                    }, policy)).map(mapper);
        });
    }

    //Schema
    @Override
    public <T extends Model> Map<String, Object> getWorkItemSchemaAndPhases(Process<T> process,
            String processInstanceId,
            String workItemId,
            String workItemTaskName,
            Policy policy) {
        // try to find work item handler
        ProcessInstance<T> pi = process.instances().findById(processInstanceId).orElseThrow(() -> new ProcessInstanceNotFoundException(processInstanceId));
        WorkItem workItem = pi.workItems(policy).stream()
                .filter(wi -> wi.getId().equals(workItemId))
                .findFirst()
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));
        KogitoWorkItemHandler handler = process.getKogitoWorkItemHandler(workItem.getWorkItemHandlerName());
        // we compute the phases
        return JsonSchemaUtil.addPhases(
                process,
                handler,
                processInstanceId,
                workItemId,
                new Policy[] { policy },
                JsonSchemaUtil.load(Thread.currentThread().getContextClassLoader(), process.id(), workItemTaskName));
    }

}
