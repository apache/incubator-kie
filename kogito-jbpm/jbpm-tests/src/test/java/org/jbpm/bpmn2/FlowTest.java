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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.jbpm.bpmn2.flow.ConditionalFlowWithoutGatewayModel;
import org.jbpm.bpmn2.flow.ConditionalFlowWithoutGatewayProcess;
import org.jbpm.bpmn2.flow.ExclusiveSplitDefaultModel;
import org.jbpm.bpmn2.flow.ExclusiveSplitDefaultNoConditionModel;
import org.jbpm.bpmn2.flow.ExclusiveSplitDefaultNoConditionProcess;
import org.jbpm.bpmn2.flow.ExclusiveSplitDefaultProcess;
import org.jbpm.bpmn2.flow.ExclusiveSplitModel;
import org.jbpm.bpmn2.flow.ExclusiveSplitPriorityModel;
import org.jbpm.bpmn2.flow.ExclusiveSplitPriorityProcess;
import org.jbpm.bpmn2.flow.ExclusiveSplitProcess;
import org.jbpm.bpmn2.flow.ExclusiveSplitXPathAdvancedModel;
import org.jbpm.bpmn2.flow.ExclusiveSplitXPathAdvancedProcess;
import org.jbpm.bpmn2.flow.ExclusiveSplitXPathAdvancedVarsNotSignaledModel;
import org.jbpm.bpmn2.flow.ExclusiveSplitXPathAdvancedVarsNotSignaledProcess;
import org.jbpm.bpmn2.flow.ExclusiveSplitXPathAdvancedWithVarsModel;
import org.jbpm.bpmn2.flow.ExclusiveSplitXPathAdvancedWithVarsProcess;
import org.jbpm.bpmn2.flow.GatewayTestModel;
import org.jbpm.bpmn2.flow.GatewayTestProcess;
import org.jbpm.bpmn2.flow.InclusiveGatewayNestedModel;
import org.jbpm.bpmn2.flow.InclusiveGatewayNestedProcess;
import org.jbpm.bpmn2.flow.InclusiveGatewayWithDefaultModel;
import org.jbpm.bpmn2.flow.InclusiveGatewayWithDefaultProcess;
import org.jbpm.bpmn2.flow.InclusiveGatewayWithHumanTasksProcessModel;
import org.jbpm.bpmn2.flow.InclusiveGatewayWithHumanTasksProcessProcess;
import org.jbpm.bpmn2.flow.InclusiveGatewayWithLoopInsideModel;
import org.jbpm.bpmn2.flow.InclusiveGatewayWithLoopInsideProcess;
import org.jbpm.bpmn2.flow.InclusiveGatewayWithLoopInsideSubprocessModel;
import org.jbpm.bpmn2.flow.InclusiveGatewayWithLoopInsideSubprocessProcess;
import org.jbpm.bpmn2.flow.InclusiveNestedInParallelNestedInExclusiveModel;
import org.jbpm.bpmn2.flow.InclusiveNestedInParallelNestedInExclusiveProcess;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinEmbeddedModel;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinEmbeddedProcess;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinExtraPathModel;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinExtraPathProcess;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinLoop2Model;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinLoop2Process;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinLoopModel;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinLoopProcess;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinModel;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinNestedModel;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinNestedProcess;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinProcess;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinWithEndModel;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinWithEndProcess;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinWithParallelModel;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinWithParallelProcess;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinWithTimerModel;
import org.jbpm.bpmn2.flow.InclusiveSplitAndJoinWithTimerProcess;
import org.jbpm.bpmn2.flow.InclusiveSplitDefaultModel;
import org.jbpm.bpmn2.flow.InclusiveSplitDefaultProcess;
import org.jbpm.bpmn2.flow.InclusiveSplitModel;
import org.jbpm.bpmn2.flow.InclusiveSplitProcess;
import org.jbpm.bpmn2.flow.MultiConnEnabledModel;
import org.jbpm.bpmn2.flow.MultiConnEnabledProcess;
import org.jbpm.bpmn2.flow.MultiInstanceLoopCharacteristicsProcessModel;
import org.jbpm.bpmn2.flow.MultiInstanceLoopCharacteristicsProcessProcess;
import org.jbpm.bpmn2.flow.MultiInstanceLoopCharacteristicsProcessWithORgatewayModel;
import org.jbpm.bpmn2.flow.MultiInstanceLoopCharacteristicsProcessWithORgatewayProcess;
import org.jbpm.bpmn2.flow.MultiInstanceLoopCharacteristicsProcessWithOutputCmpCondModel;
import org.jbpm.bpmn2.flow.MultiInstanceLoopCharacteristicsProcessWithOutputCmpCondProcess;
import org.jbpm.bpmn2.flow.MultiInstanceLoopCharacteristicsProcessWithOutputModel;
import org.jbpm.bpmn2.flow.MultiInstanceLoopCharacteristicsProcessWithOutputProcess;
import org.jbpm.bpmn2.flow.MultiInstanceLoopNumberingModel;
import org.jbpm.bpmn2.flow.MultiInstanceLoopNumberingProcess;
import org.jbpm.bpmn2.flow.MultiInstanceProcessWithOutputOnTaskModel;
import org.jbpm.bpmn2.flow.MultiInstanceProcessWithOutputOnTaskProcess;
import org.jbpm.bpmn2.flow.MultipleGatewaysProcessModel;
import org.jbpm.bpmn2.flow.MultipleGatewaysProcessProcess;
import org.jbpm.bpmn2.loop.MultiInstanceLoopCharacteristicsProcessWithOutputAndScriptsModel;
import org.jbpm.bpmn2.loop.MultiInstanceLoopCharacteristicsProcessWithOutputAndScriptsProcess;
import org.jbpm.bpmn2.loop.MultiInstanceLoopCharacteristicsTaskModel;
import org.jbpm.bpmn2.loop.MultiInstanceLoopCharacteristicsTaskProcess;
import org.jbpm.bpmn2.objects.TestUserTaskWorkItemHandler;
import org.jbpm.bpmn2.objects.TestWorkItemHandler;
import org.jbpm.bpmn2.timer.ParallelSplitWithTimerProcessModel;
import org.jbpm.bpmn2.timer.ParallelSplitWithTimerProcessProcess;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.process.workitem.builtin.SystemOutWorkItemHandler;
import org.jbpm.test.util.NodeLeftCountDownProcessEventListener;
import org.jbpm.test.utils.EventTrackerProcessListener;
import org.jbpm.test.utils.ProcessTestHelper;
import org.jbpm.workflow.instance.impl.NodeInstanceImpl;
import org.jbpm.workflow.instance.node.CompositeContextNodeInstance;
import org.jbpm.workflow.instance.node.ForEachNodeInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.kie.api.command.ExecutableCommand;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.runtime.Context;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.internal.command.RegistryContext;
import org.kie.kogito.Application;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.SignalFactory;
import org.kie.kogito.process.workitems.impl.KogitoWorkItemImpl;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class FlowTest extends JbpmBpmn2TestCase {

    @BeforeAll
    public static void setup() throws Exception {
        VariableScope.setVariableStrictOption(true);
    }

    @AfterEach
    public void clearProperties() {
        System.clearProperty("jbpm.enable.multi.con");
    }

    @Test
    public void testExclusiveSplitWithNoConditions() throws Exception {
        try {
            createKogitoProcessRuntime("org/jbpm/bpmn2/flow/BPMN2-ExclusiveGatewayWithNoConditionsDefined.bpmn2");
            fail("Should fail as XOR gateway does not have conditions defined");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("does not have a constraint for Connection");
        }

    }

    @Test
    public void testExclusiveSplit() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Email", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<ExclusiveSplitModel> definition = ExclusiveSplitProcess.newProcess(app);
        ExclusiveSplitModel model = definition.createModel();
        model.setX("First");
        model.setY("Second");
        ProcessInstance<ExclusiveSplitModel> processInstance = definition.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testExclusiveSplitXPathAdvanced() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Email", new SystemOutWorkItemHandler());

        Process<ExclusiveSplitXPathAdvancedModel> definition = ExclusiveSplitXPathAdvancedProcess.newProcess(app);
        ExclusiveSplitXPathAdvancedModel model = definition.createModel();

        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().newDocument();
        Element hi = doc.createElement("hi");
        Element ho = doc.createElement("ho");
        hi.appendChild(ho);
        Attr attr = doc.createAttribute("value");
        ho.setAttributeNode(attr);
        attr.setValue("a");

        model.setX(hi);
        model.setY("Second");

        ProcessInstance<ExclusiveSplitXPathAdvancedModel> instance = definition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testExclusiveSplitXPathAdvanced2() throws Exception {

        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Email", new SystemOutWorkItemHandler());

        Process<ExclusiveSplitXPathAdvancedVarsNotSignaledModel> definition = ExclusiveSplitXPathAdvancedVarsNotSignaledProcess.newProcess(app);
        ExclusiveSplitXPathAdvancedVarsNotSignaledModel model = definition.createModel();

        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().newDocument();
        Element hi = doc.createElement("hi");
        Element ho = doc.createElement("ho");
        hi.appendChild(ho);
        Attr attr = doc.createAttribute("value");
        ho.setAttributeNode(attr);
        attr.setValue("a");

        model.setX(hi);
        model.setY("Second");

        ProcessInstance<ExclusiveSplitXPathAdvancedVarsNotSignaledModel> instance = definition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testExclusiveSplitXPathAdvancedWithVars() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Email", new SystemOutWorkItemHandler());

        Process<ExclusiveSplitXPathAdvancedWithVarsModel> definition = ExclusiveSplitXPathAdvancedWithVarsProcess.newProcess(app);
        ExclusiveSplitXPathAdvancedWithVarsModel model = definition.createModel();

        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().newDocument();
        Element hi = doc.createElement("hi");
        Element ho = doc.createElement("ho");
        hi.appendChild(ho);
        Attr attr = doc.createAttribute("value");
        ho.setAttributeNode(attr);
        attr.setValue("a");

        model.setX(hi);
        model.setY("Second");

        ProcessInstance<ExclusiveSplitXPathAdvancedWithVarsModel> instance = definition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testExclusiveSplitPriority() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Email", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<ExclusiveSplitPriorityModel> definition = ExclusiveSplitPriorityProcess.newProcess(app);
        ExclusiveSplitPriorityModel model = definition.createModel();
        model.setX("First");
        model.setY("Second");
        ProcessInstance<ExclusiveSplitPriorityModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testExclusiveSplitDefault() throws Exception {

        Application app = ProcessTestHelper.newApplication();

        ProcessTestHelper.registerHandler(app, "Email", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<ExclusiveSplitDefaultModel> definition = ExclusiveSplitDefaultProcess.newProcess(app);
        ExclusiveSplitDefaultModel model = definition.createModel();
        model.setX("NotFirst");
        model.setY("Second");
        org.kie.kogito.process.ProcessInstance<ExclusiveSplitDefaultModel> instance = definition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testExclusiveXORGateway() throws Exception {
        Document document = DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(
                        "<instanceMetadata><user approved=\"false\" /></instanceMetadata>"
                                .getBytes()));

        Application app = ProcessTestHelper.newApplication();

        org.kie.kogito.process.Process<GatewayTestModel> definition = GatewayTestProcess.newProcess(app);
        GatewayTestModel model = definition.createModel();
        model.setInstanceMetadata(document);
        model.setStartMessage(DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(
                        "<task subject='foobar2'/>".getBytes()))
                .getFirstChild());
        org.kie.kogito.process.ProcessInstance<GatewayTestModel> instance = definition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testInclusiveSplit() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<InclusiveSplitModel> definition = InclusiveSplitProcess.newProcess(app);
        InclusiveSplitModel model = definition.createModel();
        model.setX(15);
        ProcessInstance<InclusiveSplitModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testInclusiveSplitDefaultConnection() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<InclusiveGatewayWithDefaultModel> definition = InclusiveGatewayWithDefaultProcess.newProcess(app);
        InclusiveGatewayWithDefaultModel model = definition.createModel();
        model.setTest("c");
        ProcessInstance<InclusiveGatewayWithDefaultModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testInclusiveSplitAndJoin() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<InclusiveSplitAndJoinModel> definition = InclusiveSplitAndJoinProcess.newProcess(app);
        InclusiveSplitAndJoinModel model = definition.createModel();
        model.setX(15);
        ProcessInstance<InclusiveSplitAndJoinModel> processInstance = definition.createInstance(model);
        processInstance.start();
        List<KogitoWorkItem> activeWorkItems = workItemHandler.getWorkItems();
        assertThat(activeWorkItems).hasSize(2);
        for (KogitoWorkItem wi : activeWorkItems) {
            processInstance.completeWorkItem(wi.getStringId(), null);
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testInclusiveSplitAndJoinLoop() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<InclusiveSplitAndJoinLoopModel> definition = InclusiveSplitAndJoinLoopProcess.newProcess(app);
        InclusiveSplitAndJoinLoopModel model = definition.createModel();
        model.setX(21);
        ProcessInstance<InclusiveSplitAndJoinLoopModel> processInstance = definition.createInstance(model);
        processInstance.start();
        List<KogitoWorkItem> activeWorkItems = workItemHandler.getWorkItems();
        assertThat(activeWorkItems).hasSize(3);
        for (KogitoWorkItem wi : activeWorkItems) {
            processInstance.completeWorkItem(wi.getStringId(), null);
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testInclusiveSplitAndJoinLoop2() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<InclusiveSplitAndJoinLoop2Model> definition = InclusiveSplitAndJoinLoop2Process.newProcess(app);
        InclusiveSplitAndJoinLoop2Model model = definition.createModel();
        model.setX(21);
        ProcessInstance<InclusiveSplitAndJoinLoop2Model> processInstance = definition.createInstance(model);
        processInstance.start();
        List<KogitoWorkItem> activeWorkItems = workItemHandler.getWorkItems();
        assertThat(activeWorkItems).hasSize(3);
        for (KogitoWorkItem wi : activeWorkItems) {
            processInstance.completeWorkItem(wi.getStringId(), null);
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testInclusiveSplitAndJoinNested() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<InclusiveSplitAndJoinNestedModel> definition = InclusiveSplitAndJoinNestedProcess.newProcess(app);
        InclusiveSplitAndJoinNestedModel model = definition.createModel();
        model.setX(15);
        ProcessInstance<InclusiveSplitAndJoinNestedModel> processInstance = definition.createInstance(model);
        processInstance.start();
        List<KogitoWorkItem> activeWorkItems = workItemHandler.getWorkItems();
        assertThat(activeWorkItems).hasSize(2);
        for (KogitoWorkItem wi : activeWorkItems) {
            processInstance.completeWorkItem(wi.getStringId(), null);
        }
        activeWorkItems = workItemHandler.getWorkItems();
        assertThat(activeWorkItems).hasSize(2);
        for (KogitoWorkItem wi : activeWorkItems) {
            processInstance.completeWorkItem(wi.getStringId(), null);
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testInclusiveSplitAndJoinEmbedded() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<InclusiveSplitAndJoinEmbeddedModel> definition = InclusiveSplitAndJoinEmbeddedProcess.newProcess(app);
        InclusiveSplitAndJoinEmbeddedModel model = definition.createModel();
        model.setX(15);
        ProcessInstance<InclusiveSplitAndJoinEmbeddedModel> processInstance = definition.createInstance(model);
        processInstance.start();
        List<KogitoWorkItem> activeWorkItems = workItemHandler.getWorkItems();
        assertThat(activeWorkItems).hasSize(2);
        for (KogitoWorkItem wi : activeWorkItems) {
            processInstance.completeWorkItem(wi.getStringId(), null);
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testInclusiveSplitAndJoinWithParallel() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<InclusiveSplitAndJoinWithParallelModel> definition = InclusiveSplitAndJoinWithParallelProcess.newProcess(app);
        InclusiveSplitAndJoinWithParallelModel model = definition.createModel();
        model.setX(25);
        ProcessInstance<InclusiveSplitAndJoinWithParallelModel> processInstance = definition.createInstance(model);
        processInstance.start();
        List<KogitoWorkItem> activeWorkItems = workItemHandler.getWorkItems();
        assertThat(activeWorkItems).hasSize(4);
        for (KogitoWorkItem wi : activeWorkItems) {
            processInstance.completeWorkItem(wi.getStringId(), null);
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testInclusiveSplitAndJoinWithEnd() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<InclusiveSplitAndJoinWithEndModel> definition = InclusiveSplitAndJoinWithEndProcess.newProcess(app);
        InclusiveSplitAndJoinWithEndModel model = definition.createModel();
        model.setX(25);
        ProcessInstance<InclusiveSplitAndJoinWithEndModel> processInstance = definition.createInstance(model);
        processInstance.start();
        List<KogitoWorkItem> activeWorkItems = workItemHandler.getWorkItems();
        assertThat(activeWorkItems).hasSize(3);
        for (int i = 0; i < 2; i++) {
            processInstance.completeWorkItem(activeWorkItems.get(i).getStringId(), null);
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.completeWorkItem(activeWorkItems.get(2).getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    @Timeout(10)
    public void testInclusiveSplitAndJoinWithTimer() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("timer", 2);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<InclusiveSplitAndJoinWithTimerModel> definition = InclusiveSplitAndJoinWithTimerProcess.newProcess(app);
        InclusiveSplitAndJoinWithTimerModel model = definition.createModel();
        model.setX(15);
        ProcessInstance<InclusiveSplitAndJoinWithTimerModel> processInstance = definition.createInstance(model);
        processInstance.start();
        List<KogitoWorkItem> activeWorkItems = workItemHandler.getWorkItems();
        assertThat(activeWorkItems).hasSize(1);
        processInstance.completeWorkItem(activeWorkItems.get(0).getStringId(), null);
        countDownListener.waitTillCompleted();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        activeWorkItems = workItemHandler.getWorkItems();
        assertThat(activeWorkItems).hasSize(2);
        processInstance.completeWorkItem(activeWorkItems.get(0).getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.completeWorkItem(activeWorkItems.get(1).getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testInclusiveSplitAndJoinExtraPath() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<InclusiveSplitAndJoinExtraPathModel> definition = InclusiveSplitAndJoinExtraPathProcess.newProcess(app);
        InclusiveSplitAndJoinExtraPathModel model = definition.createModel();
        model.setX(25);
        ProcessInstance<InclusiveSplitAndJoinExtraPathModel> processInstance = definition.createInstance(model);
        processInstance.start();
        processInstance.send(SignalFactory.of("signal", null));
        List<KogitoWorkItem> activeWorkItems = workItemHandler.getWorkItems();
        assertThat(activeWorkItems).hasSize(4);
        for (int i = 0; i < 3; i++) {
            processInstance.completeWorkItem(activeWorkItems.get(i).getStringId(), null);
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.completeWorkItem(activeWorkItems.get(3).getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testInclusiveSplitDefault() throws Exception {
        Application app = ProcessTestHelper.newApplication();

        org.kie.kogito.process.Process<InclusiveSplitDefaultModel> definition = InclusiveSplitDefaultProcess.newProcess(app);
        InclusiveSplitDefaultModel model = definition.createModel();
        model.setX(-5);
        org.kie.kogito.process.ProcessInstance<InclusiveSplitDefaultModel> instance = definition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testInclusiveParallelExclusiveSplitNoLoop() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "testWI", new SystemOutWorkItemHandler());
        ProcessTestHelper.registerHandler(app, "testWI2", new SystemOutWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                Integer x = (Integer) workItem.getParameter("input1");
                x++;
                Map<String, Object> results = new HashMap<>();
                results.put("output1", x);
                return Optional.of(this.workItemLifeCycle.newTransition("complete", workItem.getPhaseStatus(), results));
            }
        });
        final Map<String, Integer> nodeInstanceExecutionCounter = new HashMap<>();
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                Integer value = nodeInstanceExecutionCounter.get(event.getNodeInstance().getNodeName());
                if (value == null) {
                    value = 0;
                }
                value++;
                nodeInstanceExecutionCounter.put(event.getNodeInstance().getNodeName(), value);
            }
        });
        org.kie.kogito.process.Process<InclusiveNestedInParallelNestedInExclusiveModel> definition = InclusiveNestedInParallelNestedInExclusiveProcess.newProcess(app);
        InclusiveNestedInParallelNestedInExclusiveModel model = definition.createModel();
        model.setX(0);
        ProcessInstance<InclusiveNestedInParallelNestedInExclusiveModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(nodeInstanceExecutionCounter).hasSize(12);
        assertThat((int) nodeInstanceExecutionCounter.get("Start")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("XORGateway-converging")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("ANDGateway-diverging")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("ORGateway-diverging")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("testWI3")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("testWI2")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("ORGateway-converging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("Script")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("XORGateway-diverging")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("ANDGateway-converging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("testWI6")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("End")).isEqualTo(1);
    }

    @Test
    public void testInclusiveParallelExclusiveSplitLoop() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "testWI", new SystemOutWorkItemHandler());
        ProcessTestHelper.registerHandler(app, "testWI2", new SystemOutWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                Integer x = (Integer) workItem.getParameter("input1");
                x++;
                Map<String, Object> results = new HashMap<>();
                results.put("output1", x);
                return Optional.of(this.workItemLifeCycle.newTransition("complete", workItem.getPhaseStatus(), results));
            }
        });
        final Map<String, Integer> nodeInstanceExecutionCounter = new HashMap<>();
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                Integer value = nodeInstanceExecutionCounter.get(event.getNodeInstance().getNodeName());
                if (value == null) {
                    value = 0;
                }
                value++;
                nodeInstanceExecutionCounter.put(event.getNodeInstance().getNodeName(), value);
            }
        });
        org.kie.kogito.process.Process<InclusiveNestedInParallelNestedInExclusiveModel> definition = InclusiveNestedInParallelNestedInExclusiveProcess.newProcess(app);
        InclusiveNestedInParallelNestedInExclusiveModel model = definition.createModel();
        model.setX(-1);
        ProcessInstance<InclusiveNestedInParallelNestedInExclusiveModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(nodeInstanceExecutionCounter).hasSize(12);
        assertThat((int) nodeInstanceExecutionCounter.get("Start")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("XORGateway-converging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("ANDGateway-diverging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("ORGateway-diverging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("testWI3")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("testWI2")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("ORGateway-converging")).isEqualTo(4);
        assertThat((int) nodeInstanceExecutionCounter.get("Script")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("XORGateway-diverging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("ANDGateway-converging")).isEqualTo(4);
        assertThat((int) nodeInstanceExecutionCounter.get("testWI6")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("End")).isEqualTo(1);
    }

    @Test
    public void testInclusiveParallelExclusiveSplitNoLoopAsync() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "testWI", handler);
        ProcessTestHelper.registerHandler(app, "testWI2", new SystemOutWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                Integer x = (Integer) workItem.getParameter("input1");
                x++;
                Map<String, Object> results = new HashMap<>();
                results.put("output1", x);
                return Optional.of(this.workItemLifeCycle.newTransition("complete", workItem.getPhaseStatus(), results));
            }
        });
        final Map<String, Integer> nodeInstanceExecutionCounter = new HashMap<>();
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                Integer value = nodeInstanceExecutionCounter.get(event.getNodeInstance().getNodeName());
                if (value == null) {
                    value = 0;
                }
                value++;
                nodeInstanceExecutionCounter.put(event.getNodeInstance().getNodeName(), value);
            }
        });
        org.kie.kogito.process.Process<InclusiveNestedInParallelNestedInExclusiveModel> definition = InclusiveNestedInParallelNestedInExclusiveProcess.newProcess(app);
        InclusiveNestedInParallelNestedInExclusiveModel model = definition.createModel();
        model.setX(0);
        ProcessInstance<InclusiveNestedInParallelNestedInExclusiveModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(2);
        for (KogitoWorkItem workItem : workItems) {
            processInstance.completeWorkItem(workItem.getStringId(), null);
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(1);
        for (KogitoWorkItem workItem : workItems) {
            processInstance.completeWorkItem(workItem.getStringId(), null);
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(nodeInstanceExecutionCounter).hasSize(12);
        assertThat((int) nodeInstanceExecutionCounter.get("Start")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("XORGateway-converging")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("ANDGateway-diverging")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("ORGateway-diverging")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("testWI3")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("testWI2")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("ORGateway-converging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("Script")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("XORGateway-diverging")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("ANDGateway-converging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("testWI6")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("End")).isEqualTo(1);
    }

    @Test
    public void testInclusiveParallelExclusiveSplitLoopAsync() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "testWI", handler);
        ProcessTestHelper.registerHandler(app, "testWI2", new SystemOutWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                Integer x = (Integer) workItem.getParameter("input1");
                x++;
                Map<String, Object> results = new HashMap<>();
                results.put("output1", x);
                return Optional.of(this.workItemLifeCycle.newTransition("complete", workItem.getPhaseStatus(), results));
            }
        });
        final Map<String, Integer> nodeInstanceExecutionCounter = new HashMap<>();
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                Integer value = nodeInstanceExecutionCounter.get(event.getNodeInstance().getNodeName());
                if (value == null) {
                    value = 0;
                }
                value++;
                nodeInstanceExecutionCounter.put(event.getNodeInstance().getNodeName(), value);
            }
        });
        org.kie.kogito.process.Process<InclusiveNestedInParallelNestedInExclusiveModel> definition = InclusiveNestedInParallelNestedInExclusiveProcess.newProcess(app);
        InclusiveNestedInParallelNestedInExclusiveModel model = definition.createModel();
        model.setX(-1);
        ProcessInstance<InclusiveNestedInParallelNestedInExclusiveModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(2);
        for (KogitoWorkItem workItem : workItems) {
            processInstance.completeWorkItem(workItem.getStringId(), null);
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(2);
        for (KogitoWorkItem workItem : workItems) {
            processInstance.completeWorkItem(workItem.getStringId(), null);
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(1);
        for (KogitoWorkItem workItem : workItems) {
            processInstance.completeWorkItem(workItem.getStringId(), null);
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(nodeInstanceExecutionCounter).hasSize(12);
        assertThat((int) nodeInstanceExecutionCounter.get("Start")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("XORGateway-converging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("ANDGateway-diverging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("ORGateway-diverging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("testWI3")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("testWI2")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("ORGateway-converging")).isEqualTo(4);
        assertThat((int) nodeInstanceExecutionCounter.get("Script")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("XORGateway-diverging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("ANDGateway-converging")).isEqualTo(4);
        assertThat((int) nodeInstanceExecutionCounter.get("testWI6")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("End")).isEqualTo(1);
    }

    @Test
    public void testInclusiveSplitNested() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        TestWorkItemHandler handler2 = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "testWI", handler);
        ProcessTestHelper.registerHandler(app, "testWI2", handler2);
        org.kie.kogito.process.Process<InclusiveGatewayNestedModel> definition = InclusiveGatewayNestedProcess.newProcess(app);
        InclusiveGatewayNestedModel model = definition.createModel();
        ProcessInstance<InclusiveGatewayNestedModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.completeWorkItem(handler.getWorkItem().getStringId(), null);
        processInstance.completeWorkItem(handler2.getWorkItem().getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(2);
        for (KogitoWorkItem wi : workItems) {
            assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
            processInstance.completeWorkItem(wi.getStringId(), null);
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.completeWorkItem(handler.getWorkItem().getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testInclusiveSplitWithLoopInside() {
        Application app = ProcessTestHelper.newApplication();
        final Map<String, Integer> nodeInstanceExecutionCounter = new HashMap<>();

        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                logger.info("{} {}", event.getNodeInstance().getNodeName(), ((NodeInstanceImpl) event.getNodeInstance()).getLevel());
                Integer value = nodeInstanceExecutionCounter.get(event.getNodeInstance().getNodeName());
                if (value == null) {
                    value = 0;
                }
                value++;
                nodeInstanceExecutionCounter.put(event.getNodeInstance().getNodeName(), value);
            }
        });

        TestWorkItemHandler handler = new TestWorkItemHandler();
        TestWorkItemHandler handler2 = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "testWI", handler);
        ProcessTestHelper.registerHandler(app, "testWI2", handler2);

        org.kie.kogito.process.Process<InclusiveGatewayWithLoopInsideModel> processDefinition = InclusiveGatewayWithLoopInsideProcess.newProcess(app);
        InclusiveGatewayWithLoopInsideModel model = processDefinition.createModel();
        model.setX(-1);
        ProcessInstance<InclusiveGatewayWithLoopInsideModel> processInstance = processDefinition.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);

        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(2);

        for (KogitoWorkItem wi : workItems) {
            assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
            processInstance.completeWorkItem(wi.getStringId(), null);
        }

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.completeWorkItem(handler2.getWorkItem().getStringId(), null);

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.completeWorkItem(handler2.getWorkItem().getStringId(), null);

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.completeWorkItem(handler.getWorkItem().getStringId(), null);

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

        assertThat(nodeInstanceExecutionCounter).hasSize(10);
        assertThat((int) nodeInstanceExecutionCounter.get("Start")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("OR diverging")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("tareaWorkflow3")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("tareaWorkflow2")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("OR converging")).isEqualTo(3);
        assertThat((int) nodeInstanceExecutionCounter.get("tareaWorkflow6")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("Script")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("XOR diverging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("XOR converging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("End")).isEqualTo(1);
    }

    @Test
    public void testInclusiveSplitWithLoopInsideSubprocess() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        final Map<String, Integer> nodeInstanceExecutionCounter = new HashMap<>();
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {

            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                logger.info("{} {}", event.getNodeInstance().getNodeName(), ((NodeInstanceImpl) event.getNodeInstance()).getLevel());
                Integer value = nodeInstanceExecutionCounter.get(event.getNodeInstance().getNodeName());
                if (value == null) {
                    value = 0;
                }

                value++;
                nodeInstanceExecutionCounter.put(event.getNodeInstance().getNodeName(), value);
            }

        });

        TestWorkItemHandler handler = new TestWorkItemHandler();
        TestWorkItemHandler handler2 = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "testWI", handler);
        ProcessTestHelper.registerHandler(app, "testWI2", handler2);

        Process<InclusiveGatewayWithLoopInsideSubprocessModel> process = InclusiveGatewayWithLoopInsideSubprocessProcess.newProcess(app);
        InclusiveGatewayWithLoopInsideSubprocessModel model = process.createModel();
        model.setX(-1);
        ProcessInstance<InclusiveGatewayWithLoopInsideSubprocessModel> instance = process.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(2);
        for (KogitoWorkItem wi : workItems) {
            assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
            instance.completeWorkItem(wi.getStringId(), null);
        }
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        instance.completeWorkItem(handler2.getWorkItem().getStringId(), null);
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        instance.completeWorkItem(handler2.getWorkItem().getStringId(), null);
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        instance.completeWorkItem(handler.getWorkItem().getStringId(), null);
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(nodeInstanceExecutionCounter).hasSize(13);
        assertThat((int) nodeInstanceExecutionCounter.get("Start")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("Sub Process 1")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("sb-start")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("sb-end")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("OR diverging")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("tareaWorkflow3")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("tareaWorkflow2")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("OR converging")).isEqualTo(3);
        assertThat((int) nodeInstanceExecutionCounter.get("tareaWorkflow6")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("Script")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("XOR diverging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("XOR converging")).isEqualTo(2);
        assertThat((int) nodeInstanceExecutionCounter.get("End")).isEqualTo(1);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsProcessWithORGateway() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<MultiInstanceLoopCharacteristicsProcessWithORgatewayModel> definition = MultiInstanceLoopCharacteristicsProcessWithORgatewayProcess.newProcess(app);
        MultiInstanceLoopCharacteristicsProcessWithORgatewayModel model = definition.createModel();
        List<Integer> myList = new ArrayList<>();
        myList.add(12);
        myList.add(15);
        model.setList(myList);
        ProcessInstance<MultiInstanceLoopCharacteristicsProcessWithORgatewayModel> processInstance = definition.createInstance(model);
        processInstance.start();
        List<KogitoWorkItem> workItems = workItemHandler.getWorkItems();
        assertThat(workItems).hasSize(4);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        Collection<KogitoNodeInstance> nodeInstances = processInstance.findNodes(node -> node instanceof ForEachNodeInstance);
        assertThat(nodeInstances).hasSize(1);
        KogitoNodeInstance nodeInstance = nodeInstances.iterator().next();
        assertThat(nodeInstance).isInstanceOf(ForEachNodeInstance.class);
        Collection<KogitoNodeInstance> nodeInstancesChild = ((ForEachNodeInstance) nodeInstance).getNodeInstances().stream()
                .map(n -> (KogitoNodeInstance) n)
                .collect(Collectors.toList());
        assertThat(nodeInstancesChild).hasSize(2);
        assertThat(nodeInstancesChild).allMatch(CompositeContextNodeInstance.class::isInstance).hasSize(2);

        processInstance.completeWorkItem(workItems.get(0).getStringId(), null);
        processInstance.completeWorkItem(workItems.get(1).getStringId(), null);

        nodeInstances = processInstance.findNodes(node -> node instanceof ForEachNodeInstance);
        assertThat(nodeInstances).hasSize(1);
        nodeInstance = nodeInstances.iterator().next();
        assertThat(nodeInstance).isInstanceOf(ForEachNodeInstance.class);
        nodeInstancesChild = ((ForEachNodeInstance) nodeInstance).getNodeInstances().stream()
                .map(n -> (KogitoNodeInstance) n)
                .collect(Collectors.toList());
        assertThat(nodeInstancesChild).allMatch(CompositeContextNodeInstance.class::isInstance).hasSize(1);
        processInstance.completeWorkItem(workItems.get(2).getStringId(), null);
        processInstance.completeWorkItem(workItems.get(3).getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testInclusiveJoinWithLoopAndHumanTasks() {
        Application app = ProcessTestHelper.newApplication();
        final Map<String, Integer> nodeInstanceExecutionCounter = new HashMap<>();
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                Integer value = nodeInstanceExecutionCounter.get(event.getNodeInstance().getNodeName());
                if (value == null) {
                    value = 0;
                }
                value++;
                nodeInstanceExecutionCounter.put(event.getNodeInstance().getNodeName(), value);
            }
        });
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        org.kie.kogito.process.Process<InclusiveGatewayWithHumanTasksProcessModel> definition = InclusiveGatewayWithHumanTasksProcessProcess.newProcess(app);
        InclusiveGatewayWithHumanTasksProcessModel model = definition.createModel();
        model.setFirstXor(true);
        model.setSecondXor(true);
        model.setThirdXor(true);
        ProcessInstance<InclusiveGatewayWithHumanTasksProcessModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.completeWorkItem(handler.getWorkItem().getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(2);
        KogitoWorkItem remainingWork = null;
        for (KogitoWorkItem wi : workItems) {
            assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
            if (wi.getParameter("NodeName").equals("HT Form2")) {
                processInstance.completeWorkItem(wi.getStringId(), null);
            } else {
                remainingWork = wi;
            }
        }
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        assertThat(remainingWork).isNotNull();
        processInstance.completeWorkItem(remainingWork.getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.completeWorkItem(handler.getWorkItem().getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(nodeInstanceExecutionCounter).hasSize(13);
        assertThat((int) nodeInstanceExecutionCounter.get("Start")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("HT Form1")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("and1")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("HT Form2")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("xor1")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("xor2")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("HT Form3")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("Koniec")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("xor 3")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("HT Form4")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("xor4")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("Koniec2")).isEqualTo(1);
        assertThat((int) nodeInstanceExecutionCounter.get("or1")).isEqualTo(1);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsProcess() {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", workItemHandler);
        org.kie.kogito.process.Process<MultiInstanceLoopCharacteristicsProcessModel> definition = MultiInstanceLoopCharacteristicsProcessProcess.newProcess(app);
        MultiInstanceLoopCharacteristicsProcessModel model = definition.createModel();
        List<String> myList = new ArrayList<>();
        myList.add("First Item");
        myList.add("Second Item");
        model.setList(myList);
        ProcessInstance<MultiInstanceLoopCharacteristicsProcessModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMultiInstanceLoopNumberTest() {
        Application app = ProcessTestHelper.newApplication();
        final Map<String, String> nodeIdNodeNameMap = new HashMap<>();
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                NodeInstance nodeInstance = event.getNodeInstance();
                String uniqId = ((NodeInstanceImpl) nodeInstance).getUniqueId();
                String nodeName = nodeInstance.getNode().getName();
                String prevNodeName = nodeIdNodeNameMap.put(uniqId, nodeName);
                if (prevNodeName != null) {
                    assertThat(prevNodeName).as(uniqId + " is used for more than one node instance: ").isEqualTo(nodeName);
                }
            }
        });
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        org.kie.kogito.process.Process<MultiInstanceLoopNumberingModel> definition = MultiInstanceLoopNumberingProcess.newProcess(app);
        MultiInstanceLoopNumberingModel model = definition.createModel();
        ProcessInstance<MultiInstanceLoopNumberingModel> processInstance = definition.createInstance(model);
        processInstance.start();
        List<KogitoWorkItem> workItems = handler.getWorkItems();
        logger.debug("COMPLETING TASKS.");
        processInstance.completeWorkItem(workItems.remove(0).getStringId(), null);
        processInstance.completeWorkItem(workItems.remove(0).getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsProcess2() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);
        org.kie.kogito.process.Process<MultiInstanceProcessWithOutputOnTaskModel> processDefinition = MultiInstanceProcessWithOutputOnTaskProcess.newProcess(app);
        MultiInstanceProcessWithOutputOnTaskModel model = processDefinition.createModel();
        List<String> myList = new ArrayList<>();
        myList.add("John");
        myList.add("Mary");
        model.setMiinput(myList);

        ProcessInstance<MultiInstanceProcessWithOutputOnTaskModel> processInstance = processDefinition.createInstance(model);
        processInstance.start();
        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(2);

        assertThat(processInstance.variables().getMioutput()).isNull();

        Map<String, Object> results = new HashMap<>();
        results.put("reply", "Hello John");
        processInstance.completeWorkItem(workItems.get(0).getStringId(), results);
        assertThat(processInstance.variables().getMioutput()).isNull();

        results = new HashMap<>();
        results.put("reply", "Hello Mary");
        processInstance.completeWorkItem(workItems.get(1).getStringId(), results);

        assertThat(processInstance.variables().getMioutput()).isNotNull().hasSize(2).contains("Hello John", "Hello Mary");

        processInstance.completeWorkItem(handler.getWorkItem().getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsProcessWithOutput() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<MultiInstanceLoopCharacteristicsProcessWithOutputModel> definition = MultiInstanceLoopCharacteristicsProcessWithOutputProcess.newProcess(app);
        MultiInstanceLoopCharacteristicsProcessWithOutputModel model = definition.createModel();
        List<String> myList = new ArrayList<>();
        List<String> myListOut = new ArrayList<>();
        myList.add("First Item");
        myList.add("Second Item");
        model.setList(myList);
        model.setListOut(myListOut);
        assertThat(myListOut).isEmpty();
        ProcessInstance<MultiInstanceLoopCharacteristicsProcessWithOutputModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(model.getListOut()).hasSize(2);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsProcessWithOutputCompletionCondition() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<MultiInstanceLoopCharacteristicsProcessWithOutputCmpCondModel> definition = MultiInstanceLoopCharacteristicsProcessWithOutputCmpCondProcess.newProcess(app);
        MultiInstanceLoopCharacteristicsProcessWithOutputCmpCondModel model = definition.createModel();
        List<String> myList = new ArrayList<>();
        List<String> myListOut = new ArrayList<>();
        myList.add("First Item");
        myList.add("Second Item");
        model.setList(myList);
        model.setListOut(myListOut);
        assertThat(myListOut).isEmpty();
        ProcessInstance<MultiInstanceLoopCharacteristicsProcessWithOutputCmpCondModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(model.getListOut()).hasSize(1);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsProcessWithOutputAndScripts() {
        Application app = ProcessTestHelper.newApplication();
        List<String> myList = new ArrayList<>();
        List<String> myListOut = new ArrayList<>();
        List<String> scriptList = new ArrayList<>();
        myList.add("First Item");
        myList.add("Second Item");
        assertThat(myListOut).isEmpty();
        org.kie.kogito.process.Process<MultiInstanceLoopCharacteristicsProcessWithOutputAndScriptsModel> processDefinition =
                MultiInstanceLoopCharacteristicsProcessWithOutputAndScriptsProcess.newProcess(app);
        MultiInstanceLoopCharacteristicsProcessWithOutputAndScriptsModel model = processDefinition.createModel();
        model.setList(myList);
        model.setListOut(myListOut);
        model.setScriptList(scriptList);
        ProcessInstance<MultiInstanceLoopCharacteristicsProcessWithOutputAndScriptsModel> processInstance = processDefinition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(myListOut).hasSize(2);
        assertThat(scriptList).hasSize(2);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsTaskWithOutput() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task",
                new SystemOutWorkItemHandler());
        List<String> myList = new ArrayList<>();
        myList.add("First Item");
        myList.add("Second Item");
        Process<MultiInstanceLoopCharacteristicsProcessWithOutputModel> process = MultiInstanceLoopCharacteristicsProcessWithOutputProcess.newProcess(app);
        MultiInstanceLoopCharacteristicsProcessWithOutputModel model = process.createModel();
        model.setList(myList);
        ProcessInstance<MultiInstanceLoopCharacteristicsProcessWithOutputModel> instance = process.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.api.runtime.process.ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getListOut()).hasSize(2);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsTaskWithOutputCompletionCondition() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task",
                new SystemOutWorkItemHandler());
        List<String> myList = new ArrayList<>();
        myList.add("First Item");
        myList.add("Second Item");
        Process<MultiInstanceLoopCharacteristicsProcessWithOutputCmpCondModel> process = MultiInstanceLoopCharacteristicsProcessWithOutputCmpCondProcess.newProcess(app);
        MultiInstanceLoopCharacteristicsProcessWithOutputCmpCondModel model = process.createModel();
        model.setList(myList);
        ProcessInstance<MultiInstanceLoopCharacteristicsProcessWithOutputCmpCondModel> instance = process.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(org.kie.api.runtime.process.ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getListOut()).hasSize(1);
    }

    @Test
    @Disabled("On Exit not supported, see https://issues.redhat.com/browse/KOGITO-2067")
    public void testMultiInstanceLoopCharacteristicsTaskWithOutputCompletionCondition2()
            throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-MultiInstanceLoopCharacteristicsTaskWithOutputCmpCond2.bpmn2");
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task",
                new SystemOutWorkItemHandler());
        Map<String, Object> params = new HashMap<>();
        List<String> myList = new ArrayList<>();
        List<String> myListOut = new ArrayList<>();
        myList.add("approved");
        myList.add("rejected");
        myList.add("approved");
        myList.add("approved");
        myList.add("rejected");
        params.put("list", myList);
        params.put("listOut", myListOut);
        assertThat(myListOut).isEmpty();
        KogitoProcessInstance processInstance = kruntime.startProcess(
                "MultiInstanceLoopCharacteristicsTask", params);
        assertProcessInstanceCompleted(processInstance);
        // only two approved outcomes are required to complete multiinstance and since there was reject in between we should have
        // three elements in the list
        assertThat(myListOut).hasSize(3);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsTask() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        TestUserTaskWorkItemHandler handler = new TestUserTaskWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "Human Task", handler);

        org.kie.kogito.process.Process<MultiInstanceLoopCharacteristicsTaskModel> definition = MultiInstanceLoopCharacteristicsTaskProcess.newProcess(app);
        MultiInstanceLoopCharacteristicsTaskModel model = definition.createModel();
        model.setList(new ArrayList<>(List.of("First Item", "Second Item")));

        org.kie.kogito.process.ProcessInstance<MultiInstanceLoopCharacteristicsTaskModel> instance = definition.createInstance(model);
        instance.start();

        List<KogitoWorkItem> workItems = handler.getWorkItems();
        assertThat(workItems).isNotNull().hasSize(2);
        assertThat(workItems.get(0).getParameter("Item")).isEqualTo("First Item");
        assertThat(workItems.get(1).getParameter("Item")).isEqualTo("Second Item");
        ProcessTestHelper.completeWorkItem(instance, "john", Collections.emptyMap());
        ProcessTestHelper.completeWorkItem(instance, "john", Collections.emptyMap());

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testMultipleInOutgoingSequenceFlows() throws Exception {
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("timer", 1);
        System.setProperty("jbpm.enable.multi.con", "true");
        kruntime = createKogitoProcessRuntime("BPMN2-MultipleInOutgoingSequenceFlows.bpmn2");
        kruntime.getProcessEventManager().addEventListener(countDownListener);
        final List<String> list = new ArrayList<>();
        kruntime.getProcessEventManager().addEventListener(new DefaultKogitoProcessEventListener() {
            public void beforeProcessStarted(ProcessStartedEvent event) {
                list.add(((KogitoProcessInstance) event.getProcessInstance()).getStringId());
            }
        });

        assertThat(list).isEmpty();

        countDownListener.waitTillCompleted();

        assertThat(list).hasSize(1);
        System.clearProperty("jbpm.enable.multi.con");

    }

    @Test
    public void testMultipleIncomingFlowToEndNode() throws Exception {
        System.setProperty("jbpm.enable.multi.con", "true");

        kruntime = createKogitoProcessRuntime("BPMN2-MultipleFlowEndNode.bpmn2");

        KogitoProcessInstance processInstance = kruntime.startProcess("MultipleFlowEndNode");
        assertProcessInstanceCompleted(processInstance);
        System.clearProperty("jbpm.enable.multi.con");
    }

    @Test
    public void testMultipleEnabledOnSingleConditionalSequenceFlow() throws Exception {
        System.setProperty("jbpm.enable.multi.con", "true");
        Application app = ProcessTestHelper.newApplication();
        final List<String> list = new ArrayList<>();
        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
                if ("Task2".equals(event.getNodeInstance().getNodeName())) {
                    list.add(String.valueOf(event.getNodeInstance().getNodeId()));
                }
            }
        });
        org.kie.kogito.process.Process<MultiConnEnabledModel> definition = MultiConnEnabledProcess.newProcess(app);
        ProcessInstance<MultiConnEnabledModel> processInstance = definition.createInstance(definition.createModel());
        assertThat(list).isEmpty();
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        processInstance.send(SignalFactory.of("signal", null));
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(list).hasSize(1);
        System.clearProperty("jbpm.enable.multi.con");
    }

    @Test
    public void testMultipleInOutgoingSequenceFlowsDisable() throws Exception {
        try {
            createKogitoProcessRuntime("BPMN2-MultipleInOutgoingSequenceFlows.bpmn2");
            fail("Should fail as multiple outgoing and incoming connections are disabled by default");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("This type of node [ScriptTask_1, Script Task] cannot have more than one outgoing connection!");
        }
    }

    @Test
    public void testConditionalFlow() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        EventTrackerProcessListener listener = new EventTrackerProcessListener();
        ProcessTestHelper.registerProcessEventListener(app, listener);
        org.kie.kogito.process.Process<ConditionalFlowWithoutGatewayModel> definition = ConditionalFlowWithoutGatewayProcess.newProcess(app);
        org.kie.kogito.process.ProcessInstance<ConditionalFlowWithoutGatewayModel> instance = definition.createInstance(definition.createModel());
        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
        assertThat(listener.tracked())
                .anyMatch(ProcessTestHelper.triggered("start"))
                .anyMatch(ProcessTestHelper.triggered("script"))
                .anyMatch(ProcessTestHelper.triggered("end1"));
        assertThat(listener.tracked())
                .noneMatch(ProcessTestHelper.triggered("end2"));
    }

    @Test
    public void testLane() throws Exception {
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/flow/BPMN2-Lane.bpmn2");

        TestUserTaskWorkItemHandler workItemHandler = new TestUserTaskWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        KogitoProcessInstance processInstance = kruntime.startProcess("Lane");
        assertThat(processInstance.getState()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);

        KogitoWorkItem KogitoWorkItem = workItemHandler.getWorkItem();
        assertThat(KogitoWorkItem).isNotNull();
        assertThat(KogitoWorkItem.getParameter("ActorId")).isEqualTo("john");
        Map<String, Object> results = new HashMap<>();
        ((KogitoWorkItemImpl) KogitoWorkItem).setParameter("ActorId", "mary");
        kruntime.getKogitoWorkItemManager().completeWorkItem(KogitoWorkItem.getStringId(),
                results);
        KogitoWorkItem = workItemHandler.getWorkItem();
        assertThat(KogitoWorkItem).isNotNull();
        assertThat(KogitoWorkItem.getParameter("SwimlaneActorId")).isEqualTo("mary");
        kruntime.getKogitoWorkItemManager().completeWorkItem(KogitoWorkItem.getStringId(), null);
        assertProcessInstanceFinished(processInstance, kruntime);
    }

    @Test
    public void testExclusiveSplitDefaultNoCondition() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<ExclusiveSplitDefaultNoConditionModel> definition = ExclusiveSplitDefaultNoConditionProcess.newProcess(app);
        org.kie.kogito.process.ProcessInstance<ExclusiveSplitDefaultNoConditionModel> instance = definition.createInstance(definition.createModel());

        instance.start();

        assertThat(instance.status()).isEqualTo(org.kie.kogito.process.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMultipleGatewaysProcess() {
        Application app = ProcessTestHelper.newApplication();

        ProcessTestHelper.registerProcessEventListener(app, new DefaultKogitoProcessEventListener() {
            KogitoProcessInstance pi;

            @Override
            public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
                if (event.getNodeInstance().getNodeName().equals("CreateAgent")) {
                    pi.signalEvent("Signal_1", null);
                }
            }

            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                logger.info("Before Node triggered event received for node: {}", event.getNodeInstance().getNodeName());
            }

            @Override
            public void beforeProcessStarted(ProcessStartedEvent event) {
                pi = (KogitoProcessInstance) event.getProcessInstance();

            }
        });
        org.kie.kogito.process.Process<MultipleGatewaysProcessModel> definition = MultipleGatewaysProcessProcess.newProcess(app);
        MultipleGatewaysProcessModel model = definition.createModel();
        model.setAction("CreateAgent");
        ProcessInstance<MultipleGatewaysProcessModel> processInstance = definition.createInstance(model);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testTimerAndGateway() {
        Application app = ProcessTestHelper.newApplication();
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("timer", 1);
        ProcessTestHelper.registerProcessEventListener(app, countDownListener);
        TestWorkItemHandler handler1 = new TestWorkItemHandler();
        TestWorkItemHandler handler2 = new TestWorkItemHandler();
        ProcessTestHelper.registerHandler(app, "task1", handler1);
        ProcessTestHelper.registerHandler(app, "task2", handler2);
        org.kie.kogito.process.Process<ParallelSplitWithTimerProcessModel> definition = ParallelSplitWithTimerProcessProcess.newProcess(app);
        ProcessInstance<ParallelSplitWithTimerProcessModel> processInstance = definition.createInstance(definition.createModel());
        processInstance.start();
        KogitoWorkItem workItem1 = handler1.getWorkItem();
        assertThat(workItem1).isNotNull();
        assertThat(handler1.getWorkItem()).isNull();
        processInstance.completeWorkItem(workItem1.getStringId(), null);
        countDownListener.waitTillCompleted();
        KogitoWorkItem workItem2 = handler2.getWorkItem();
        assertThat(workItem2).isNotNull();
        assertThat(handler2.getWorkItem()).isNull();
        processInstance.completeWorkItem(workItem2.getStringId(), null);
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    private static class GetProcessVariableCommand implements ExecutableCommand<Object> {

        private String processInstanceId;
        private String variableName;

        public GetProcessVariableCommand(String processInstanceId, String variableName) {
            this.processInstanceId = processInstanceId;
            this.variableName = variableName;
        }

        public Object execute(Context context) {
            KogitoProcessRuntime kruntime = InternalProcessRuntime.asKogitoProcessRuntime(((RegistryContext) context).lookup(KieSession.class));

            org.jbpm.process.instance.ProcessInstance processInstance =
                    (org.jbpm.process.instance.ProcessInstance) kruntime.getProcessInstance(processInstanceId);

            VariableScopeInstance variableScope =
                    (VariableScopeInstance) processInstance.getContextInstance(VariableScope.VARIABLE_SCOPE);

            Object variable = variableScope.getVariable(variableName);

            return variable;
        }

    }
}
