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
package org.jbpm.workflow.instance.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;

import org.drools.core.common.InternalWorkingMemory;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.ProcessInstance;
import org.jbpm.util.PatternConstants;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.jbpm.workflow.instance.impl.MVELProcessHelper;
import org.jbpm.workflow.instance.impl.NodeInstanceImpl;
import org.jbpm.workflow.instance.impl.ProcessInstanceResolverFactory;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.kie.api.command.ExecutableCommand;
import org.kie.api.definition.process.Process;
import org.kie.api.runtime.Context;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.ExecutableRunner;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.api.runtime.process.WorkItem;
import org.kie.internal.KieInternalServices;
import org.kie.internal.command.RegistryContext;
import org.kie.internal.process.CorrelationKey;
import org.kie.internal.process.CorrelationKeyFactory;
import org.kie.internal.runtime.CommandBasedStatefulKnowledgeSession;
import org.kie.internal.runtime.StatefulKnowledgeSession;
import org.kie.kogito.internal.process.event.KogitoProcessEventSupport;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitems.InternalKogitoWorkItemManager;
import org.kie.kogito.process.workitems.impl.KogitoWorkItemImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jbpm.process.instance.InternalProcessRuntime.asKogitoProcessRuntime;

public class DynamicUtils {

    private static final Logger logger = LoggerFactory.getLogger(DynamicUtils.class);

    public static void addDynamicWorkItem(
            final DynamicNodeInstance dynamicContext,
            KieRuntime ksession,
            String workItemName,
            Map<String, Object> parameters) {
        final WorkflowProcessInstance processInstance = dynamicContext.getProcessInstance();
        internalAddDynamicWorkItem(processInstance,
                dynamicContext,
                ksession,
                workItemName,
                parameters);
    }

    public static KogitoWorkItem addDynamicWorkItem(
            final org.kie.api.runtime.process.ProcessInstance dynamicProcessInstance,
            KieRuntime ksession,
            String workItemName,
            Map<String, Object> parameters) {
        return internalAddDynamicWorkItem((WorkflowProcessInstance) dynamicProcessInstance,
                null,
                ksession,
                workItemName,
                parameters);
    }

    private static KogitoWorkItem internalAddDynamicWorkItem(
            final WorkflowProcessInstance processInstance,
            final DynamicNodeInstance dynamicContext,
            KieRuntime ksession,
            String workItemName,
            Map<String, Object> parameters) {
        final KogitoWorkItemImpl workItem = new KogitoWorkItemImpl();
        workItem.setId(UUID.randomUUID().toString());
        workItem.setState(WorkItem.ACTIVE);
        workItem.setProcessInstanceId(processInstance.getStringId());
        workItem.setProcessInstance(processInstance);
        workItem.setDeploymentId((String) ksession.getEnvironment().get(EnvironmentName.DEPLOYMENT_ID));
        workItem.setName(workItemName);
        workItem.setParameters(parameters);

        for (Map.Entry<String, Object> entry : workItem.getParameters().entrySet()) {
            if (entry.getValue() instanceof String) {
                String s = (String) entry.getValue();
                Object variableValue = null;
                Matcher matcher = PatternConstants.PARAMETER_MATCHER.matcher(s);
                while (matcher.find()) {
                    String paramName = matcher.group(1);
                    variableValue = processInstance.getVariable(paramName);
                    if (variableValue == null) {
                        try {
                            variableValue = MVELProcessHelper.evaluator().eval(paramName,
                                    new ProcessInstanceResolverFactory(processInstance));
                        } catch (Exception t) {
                            logger.error("Could not find variable scope for variable {}",
                                    paramName);
                            logger.error("when trying to replace variable in string for Dynamic Work Item {}",
                                    workItemName);
                            logger.error("Continuing without setting parameter.");
                        }
                    }
                }
                if (variableValue != null) {
                    workItem.setParameter(entry.getKey(),
                            variableValue);
                }
            }
        }

        final WorkItemNodeInstance workItemNodeInstance = new WorkItemNodeInstance();
        workItemNodeInstance.setProcessInstance(processInstance);
        workItemNodeInstance.internalSetWorkItem(workItem);
        workItemNodeInstance.setMetaData("NodeType",
                workItemName);
        workItem.setNodeInstanceId(workItemNodeInstance.getStringId());
        if (ksession instanceof CommandBasedStatefulKnowledgeSession) {
            ExecutableRunner runner = ((CommandBasedStatefulKnowledgeSession) ksession).getRunner();
            runner.execute(new ExecutableCommand<Void>() {
                private static final long serialVersionUID = 5L;

                public Void execute(Context context) {
                    StatefulKnowledgeSession ksession = (StatefulKnowledgeSession) ((RegistryContext) context).lookup(KieSession.class);
                    KogitoProcessRuntime kruntime = asKogitoProcessRuntime(ksession);
                    WorkflowProcessInstance realProcessInstance = (WorkflowProcessInstance) kruntime.getProcessInstance(processInstance.getStringId());
                    workItemNodeInstance.setProcessInstance(realProcessInstance);
                    if (dynamicContext == null) {
                        workItemNodeInstance.setNodeInstanceContainer(realProcessInstance);
                    } else {
                        DynamicNodeInstance realDynamicContext = findDynamicContext(realProcessInstance,
                                dynamicContext.getUniqueId());
                        workItemNodeInstance.setNodeInstanceContainer(realDynamicContext);
                    }
                    workItemNodeInstance.addEventListeners();
                    executeWorkItem(ksession, workItem, workItemNodeInstance);
                    return null;
                }
            });
        } else {
            workItemNodeInstance.setProcessInstance(processInstance);
            workItemNodeInstance.setNodeInstanceContainer(dynamicContext == null ? processInstance : dynamicContext);
            workItemNodeInstance.addEventListeners();
            executeWorkItem(ksession,
                    workItem,
                    workItemNodeInstance);

        }
        return workItem;
    }

    private static void executeWorkItem(KieRuntime ksession,
            KogitoWorkItemImpl workItem,
            WorkItemNodeInstance workItemNodeInstance) {
        KogitoProcessRuntime kruntime = asKogitoProcessRuntime(ksession);
        KogitoProcessEventSupport eventSupport = kruntime.getProcessEventSupport();
        eventSupport.fireBeforeNodeTriggered(workItemNodeInstance, ksession);
        ((InternalKogitoWorkItemManager) kruntime.getKogitoWorkItemManager()).internalExecuteWorkItem(workItem);
        workItemNodeInstance.internalSetWorkItemId(workItem.getStringId());
        eventSupport.fireAfterNodeTriggered(workItemNodeInstance, ksession);
    }

    private static DynamicNodeInstance findDynamicContext(WorkflowProcessInstance processInstance,
            String uniqueId) {
        for (NodeInstance nodeInstance : processInstance.getNodeInstances(true)) {
            if (uniqueId.equals(((NodeInstanceImpl) nodeInstance).getUniqueId())) {
                return (DynamicNodeInstance) nodeInstance;
            }
        }
        throw new IllegalArgumentException("Could not find node instance " + uniqueId);
    }

    public static String addDynamicSubProcess(
            final DynamicNodeInstance dynamicContext,
            KieRuntime ksession,
            final String processId,
            final Map<String, Object> parameters) {
        final WorkflowProcessInstance processInstance = dynamicContext.getProcessInstance();
        return internalAddDynamicSubProcess(processInstance,
                dynamicContext,
                ksession,
                processId,
                parameters);
    }

    public static String addDynamicSubProcess(
            final org.kie.api.runtime.process.ProcessInstance processInstance,
            KieRuntime ksession,
            final String processId,
            final Map<String, Object> parameters) {
        return internalAddDynamicSubProcess((WorkflowProcessInstance) processInstance,
                null,
                ksession,
                processId,
                parameters);
    }

    public static String internalAddDynamicSubProcess(
            final WorkflowProcessInstance processInstance,
            final DynamicNodeInstance dynamicContext,
            KieRuntime ksession,
            final String processId,
            final Map<String, Object> parameters) {
        final SubProcessNodeInstance subProcessNodeInstance = new SubProcessNodeInstance();
        subProcessNodeInstance.setNodeInstanceContainer(dynamicContext == null ? processInstance : dynamicContext);
        subProcessNodeInstance.setProcessInstance(processInstance);
        subProcessNodeInstance.setMetaData("NodeType",
                "SubProcessNode");
        if (ksession instanceof CommandBasedStatefulKnowledgeSession) {
            ExecutableRunner commandService = ((CommandBasedStatefulKnowledgeSession) ksession).getRunner();
            return commandService.execute(new ExecutableCommand<String>() {
                private static final long serialVersionUID = 5L;

                public String execute(Context context) {
                    StatefulKnowledgeSession ksession = (StatefulKnowledgeSession) ((RegistryContext) context).lookup(KieSession.class);
                    KogitoProcessRuntime kruntime = asKogitoProcessRuntime(ksession);
                    WorkflowProcessInstance realProcessInstance = (WorkflowProcessInstance) kruntime.getProcessInstance(processInstance.getStringId());
                    subProcessNodeInstance.setProcessInstance(realProcessInstance);
                    if (dynamicContext == null) {
                        subProcessNodeInstance.setNodeInstanceContainer(realProcessInstance);
                    } else {
                        DynamicNodeInstance realDynamicContext = findDynamicContext(realProcessInstance,
                                dynamicContext.getUniqueId());
                        subProcessNodeInstance.setNodeInstanceContainer(realDynamicContext);
                    }
                    return executeSubProcess(kruntime,
                            processId,
                            parameters,
                            processInstance,
                            subProcessNodeInstance);
                }
            });
        } else {
            return executeSubProcess(asKogitoProcessRuntime(ksession),
                    processId,
                    parameters,
                    processInstance,
                    subProcessNodeInstance);
        }
    }

    private static String executeSubProcess(KogitoProcessRuntime kruntime,
            String processId,
            Map<String, Object> parameters,
            ProcessInstance processInstance,
            SubProcessNodeInstance subProcessNodeInstance) {
        Process process = kruntime.getKieSession().getKieBase().getProcess(processId);
        if (process == null) {
            logger.error("Could not find process {}",
                    processId);
            throw new IllegalArgumentException("No process definition found with id: " + processId);
        } else {
            KogitoProcessEventSupport eventSupport = (((InternalProcessRuntime) ((InternalWorkingMemory) kruntime.getKieSession()).getProcessRuntime())).getProcessEventSupport();
            eventSupport.fireBeforeNodeTriggered(subProcessNodeInstance, kruntime.getKieSession());

            ProcessInstance subProcessInstance = null;
            if (((WorkflowProcessInstanceImpl) processInstance).getCorrelationKey() != null) {
                List<String> businessKeys = new ArrayList<>();
                businessKeys.add(((WorkflowProcessInstanceImpl) processInstance).getCorrelationKey());
                businessKeys.add(processId);
                businessKeys.add(String.valueOf(System.currentTimeMillis()));
                CorrelationKeyFactory correlationKeyFactory = KieInternalServices.Factory.get().newCorrelationKeyFactory();
                CorrelationKey subProcessCorrelationKey = correlationKeyFactory.newCorrelationKey(businessKeys);
                subProcessInstance = (ProcessInstance) (((InternalWorkingMemory) kruntime.getKieSession()).getProcessRuntime()).createProcessInstance(processId, subProcessCorrelationKey, parameters);
            } else {
                subProcessInstance = (ProcessInstance) kruntime.getKieSession().createProcessInstance(processId, parameters);
            }

            subProcessInstance.setMetaData("ParentProcessInstanceId",
                    processInstance.getStringId());
            subProcessInstance.setParentProcessInstanceId(processInstance.getStringId());

            String subProcessInstanceId = subProcessInstance.getStringId();
            subProcessInstance = (ProcessInstance) asKogitoProcessRuntime(kruntime.getKieSession()).startProcessInstance(subProcessInstanceId);
            subProcessNodeInstance.internalSetProcessInstanceId(subProcessInstanceId);

            eventSupport.fireAfterNodeTriggered(subProcessNodeInstance, kruntime.getKieSession());
            if (subProcessInstance.getState() == KogitoProcessInstance.STATE_COMPLETED) {
                subProcessNodeInstance.triggerCompleted();
            } else {

                subProcessNodeInstance.addEventListeners();
            }

            return subProcessInstanceId;
        }
    }

    private DynamicUtils() {
        // It is not allowed to create instances of util classes.
    }
}
