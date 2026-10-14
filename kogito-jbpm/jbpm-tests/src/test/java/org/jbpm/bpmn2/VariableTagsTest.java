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
package org.jbpm.bpmn2;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jbpm.bpmn2.objects.TestWorkItemHandler;
import org.jbpm.bpmn2.support.InMemoryProcessInstancesFactory;
import org.jbpm.bpmn2.tags.ApprovalWithCustomVariableTagsModel;
import org.jbpm.bpmn2.tags.ApprovalWithCustomVariableTagsProcess;
import org.jbpm.bpmn2.tags.ApprovalWithReadonlyVariableTagsModel;
import org.jbpm.bpmn2.tags.ApprovalWithReadonlyVariableTagsProcess;
import org.jbpm.bpmn2.tags.ApprovalWithRequiredVariableTagsModel;
import org.jbpm.bpmn2.tags.ApprovalWithRequiredVariableTagsProcess;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.test.utils.ProcessTestHelper;
import org.junit.jupiter.api.Test;
import org.kie.api.event.process.ProcessVariableChangedEvent;
import org.kie.kogito.Application;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.VariableViolationException;
import org.kie.kogito.process.bpmn2.BpmnProcess;
import org.kie.kogito.process.bpmn2.BpmnVariables;
import org.kie.kogito.process.bpmn2.StaticApplicationAssembler;
import org.kie.kogito.process.impl.StaticProcessConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;

public class VariableTagsTest extends JbpmBpmn2TestCase {

    @Test
    public void testVariableMultipleMetadata() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ApprovalWithRequiredVariableTagsProcess processDefinition = (ApprovalWithRequiredVariableTagsProcess) ApprovalWithRequiredVariableTagsProcess.newProcess(app);

        RuleFlowProcess processEngine = (RuleFlowProcess) processDefinition.process();
        Optional<Variable> var = processEngine.getVariableScope().getVariables().stream().filter(e -> "approver".equals(e.getName())).findAny();
        assertThat(var).isPresent();
        assertThat(var.get().getMetaData()).hasSize(3);
        assertThat(var.get().getMetaData()).containsExactly(entry("approver", "approver"), entry("customTags", "required"), entry("ItemSubjectRef", "ItemDefinition_9"));

    }

    @Test
    public void testProcessWithMissingRequiredVariable() {
        Application app = ProcessTestHelper.newApplication();
        ApprovalWithRequiredVariableTagsProcess processDefinition = (ApprovalWithRequiredVariableTagsProcess) ApprovalWithRequiredVariableTagsProcess.newProcess(app);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        ApprovalWithRequiredVariableTagsModel model = processDefinition.createModel();

        assertThatExceptionOfType(VariableViolationException.class).isThrownBy(() -> {
            org.kie.kogito.process.ProcessInstance<ApprovalWithRequiredVariableTagsModel> instance = processDefinition.createInstance(model);
            instance.start();
        });
    }

    @Test
    public void testProcessWithRequiredVariable() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<ApprovalWithRequiredVariableTagsModel> processDefinition = ApprovalWithRequiredVariableTagsProcess.newProcess(app);
        ApprovalWithRequiredVariableTagsModel model = processDefinition.createModel();
        model.setApprover("john");
        org.kie.kogito.process.ProcessInstance<ApprovalWithRequiredVariableTagsModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        instance.completeWorkItem(workItem.getStringId(), null);
        workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        instance.completeWorkItem(workItem.getStringId(), null);
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testProcessWithReadonlyVariable() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<ApprovalWithReadonlyVariableTagsModel> processDefinition = ApprovalWithReadonlyVariableTagsProcess.newProcess(app);
        ApprovalWithReadonlyVariableTagsModel model = processDefinition.createModel();
        model.setApprover("john");
        org.kie.kogito.process.ProcessInstance<ApprovalWithReadonlyVariableTagsModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ACTIVE);
        KogitoWorkItem workItem = workItemHandler.getWorkItem();
        assertThat(workItem).isNotNull();
        assertThatExceptionOfType(VariableViolationException.class)
                .isThrownBy(() -> instance.completeWorkItem(workItem.getStringId(), Collections.singletonMap("ActorId", "john")));
        instance.abort();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_ABORTED);
    }

    @Test
    public void testProcessWithCustomVariableTag() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        DefaultKogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void beforeVariableChanged(ProcessVariableChangedEvent event) {
                if (event.hasTag("onlyAdmin")) {
                    throw new VariableViolationException(event.getProcessInstance().getId(), event.getVariableId(), "Variable can only be set by admins");
                }
            }
        };
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<ApprovalWithCustomVariableTagsModel> processDefinition = ApprovalWithCustomVariableTagsProcess.newProcess(app);
        ApprovalWithCustomVariableTagsModel model = processDefinition.createModel();
        model.setApprover("john");

        assertThatExceptionOfType(VariableViolationException.class).isThrownBy(() -> {
            org.kie.kogito.process.ProcessInstance<ApprovalWithCustomVariableTagsModel> instance = processDefinition.createInstance(model);
            instance.start();
        });
    }

    @Test
    public void testRequiredVariableFiltering() {
        StaticProcessConfig processConfig = StaticProcessConfig.newStaticProcessConfigBuilder()
                .withWorkItemHandler("Human Task", new TestWorkItemHandler())
                .build();

        Application application =
                StaticApplicationAssembler.instance().newStaticApplication(new InMemoryProcessInstancesFactory(),
                        processConfig,
                        "org/jbpm/bpmn2/tags/BPMN2-ApprovalWithCustomVariableTags.bpmn2");

        org.kie.kogito.process.Processes container = application.get(org.kie.kogito.process.Processes.class);

        BpmnProcess process = (BpmnProcess) container.processById("ApprovalWithCustomVariableTags");
        Map<String, Object> params = new HashMap<>();
        params.put("approver", "john");
        org.kie.kogito.process.ProcessInstance<BpmnVariables> instance = process.createInstance(BpmnVariables.create(params));
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        assertThat(instance.variables().toMap()).hasSize(1);
        assertThat(instance.variables().toMap(BpmnVariables.OUTPUTS_ONLY)).isEmpty();
        assertThat(instance.variables().toMap(BpmnVariables.INPUTS_ONLY)).isEmpty();
        assertThat(instance.variables().toMap(BpmnVariables.INTERNAL_ONLY)).isEmpty();
        assertThat(instance.variables().toMap(v -> v.hasTag("onlyAdmin"))).hasSize(1).containsEntry("approver", "john");
        instance.abort();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ABORTED);
    }

}
