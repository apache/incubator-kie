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

import java.util.ArrayList;
import java.util.List;

import org.jbpm.bpmn2.objects.ExceptionOnPurposeHandler;
import org.jbpm.bpmn2.objects.TestWorkItemHandler;
import org.jbpm.bpmn2.support.InMemoryProcessInstancesFactory;
import org.jbpm.bpmn2.xml.XmlBPMNProcessDumper;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.jbpm.ruleflow.core.WorkflowElementIdentifierFactory;
import org.jbpm.test.util.NodeLeftCountDownProcessEventListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kie.api.definition.process.WorkflowElementIdentifier;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.io.Resource;
import org.kie.internal.io.ResourceFactory;
import org.kie.kogito.Application;
import org.kie.kogito.Model;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.kie.kogito.internal.process.event.KogitoProcessEventListener;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.process.bpmn2.StaticApplicationAssembler;
import org.kie.kogito.process.impl.StaticProcessConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jbpm.ruleflow.core.Metadata.CANCEL_ACTIVITY;
import static org.jbpm.ruleflow.core.Metadata.ERROR_EVENT;
import static org.jbpm.ruleflow.core.Metadata.ERROR_STRUCTURE_REF;
import static org.jbpm.ruleflow.core.Metadata.EVENT_TYPE_TIMER;
import static org.jbpm.ruleflow.core.Metadata.HAS_ERROR_EVENT;
import static org.jbpm.ruleflow.core.Metadata.TIME_CYCLE;
import static org.jbpm.ruleflow.core.Metadata.TIME_DURATION;

public class ProcessFactoryTest extends JbpmBpmn2TestCase {

    private static WorkflowElementIdentifier one = WorkflowElementIdentifierFactory.fromExternalFormat("one");
    private static WorkflowElementIdentifier two = WorkflowElementIdentifierFactory.fromExternalFormat("two");
    private static WorkflowElementIdentifier three = WorkflowElementIdentifierFactory.fromExternalFormat("three");
    private static WorkflowElementIdentifier four = WorkflowElementIdentifierFactory.fromExternalFormat("four");
    private static WorkflowElementIdentifier five = WorkflowElementIdentifierFactory.fromExternalFormat("five");

    @Test
    public void testProcessFactory() throws Exception {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("org.jbpm.process");
        factory
                // header
                .name("My process")
                .packageName("org.jbpm")
                // nodes
                .startNode(one)
                .name("Start")
                .done()
                .actionNode(two)
                .name("Action")
                .action("java",
                        "System.out.println(\"Action\");")
                .done()
                .endNode(three)
                .name("End")
                .done()
                // connections
                .connection(one, two)
                .connection(two, three);
        RuleFlowProcess process = factory.validate().getProcess();
        Resource res = ResourceFactory.newByteArrayResource(XmlBPMNProcessDumper.INSTANCE.dump(process).getBytes());
        res.setSourcePath("/tmp/processFactory.bpmn2"); // source path or target path must be set to be added into kbase
        kruntime = createKogitoProcessRuntime(res);
        kruntime.startProcess("org.jbpm.process");
    }

    @Test
    public void testCompositeNode() throws Exception {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("org.jbpm.process");
        factory
                // header
                .name("My process")
                .packageName("org.jbpm")
                // nodes
                .startNode(one)
                .name("Start")
                .done()
                .compositeContextNode(two)
                .name("SubProcess")
                .startNode(one)
                .name("SubProcess Start")
                .done()
                .actionNode(two)
                .name("SubProcess Action")
                .action("java",
                        "System.out.println(\"SubProcess Action\");")
                .done()
                .endNode(three)
                .name("SubProcess End")
                .terminate(true)
                .done()
                .connection(one, two)
                .connection(two, three)
                .done()
                .endNode(three)
                .name("End")
                .done()
                // connections
                .connection(one, two)
                .connection(two, three);
        RuleFlowProcess process = factory.validate().getProcess();

        assertThat(process.getNode(two).getName()).isEqualTo("SubProcess");

        Resource res = ResourceFactory.newByteArrayResource(XmlBPMNProcessDumper.INSTANCE.dump(process).getBytes());
        res.setSourcePath("/tmp/processFactory.bpmn2"); // source path or target path must be set to be added into kbase
        kruntime = createKogitoProcessRuntime(res);
        KogitoProcessInstance pi = kruntime.startProcess("org.jbpm.process");

        assertThat(pi.getState()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);

    }

    @Test
    @Timeout(10)
    public void testBoundaryTimerTimeCycle() throws Exception {
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("BoundaryTimerEvent",
                1);
        String timeCycle = "1s###5s";
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("org.jbpm.process");
        factory
                // header
                .name("My process")
                .packageName("org.jbpm")
                // nodes
                .startNode(one)
                .name("Start")
                .done()
                .humanTaskNode(two)
                .name("Task")
                .actorId("john")
                .taskName("MyTask")
                .done()
                .endNode(three)
                .name("End1")
                .terminate(false)
                .done()
                .boundaryEventNode(four)
                .name("BoundaryTimerEvent")
                .attachedTo(two)
                .metaData(TIME_CYCLE, timeCycle)
                .metaData(CANCEL_ACTIVITY, false)
                .eventType(EVENT_TYPE_TIMER, timeCycle)
                .done()
                .endNode(five)
                .name("End2")
                .terminate(false)
                .done()
                // connections
                .connection(one, two)
                .connection(two, three)
                .connection(four, five);
        RuleFlowProcess process = factory.validate().getProcess();

        Resource res = ResourceFactory.newByteArrayResource(XmlBPMNProcessDumper.INSTANCE.dump(process).getBytes());
        res.setSourcePath("/tmp/processFactory.bpmn2"); // source path or target path must be set to be added into kbase
        kruntime = createKogitoProcessRuntime(res);
        TestWorkItemHandler testHandler = new TestWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task",
                testHandler);
        kruntime.getProcessEventManager().addEventListener(countDownListener);

        KogitoProcessInstance pi = kruntime.startProcess("org.jbpm.process");
        assertProcessInstanceActive(pi);

        countDownListener.waitTillCompleted(); // wait for boundary timer firing

        assertNodeTriggered(pi.getStringId(),
                "End2");
        assertProcessInstanceActive(pi); // still active because CancelActivity = false

        kruntime.getKogitoWorkItemManager().completeWorkItem(testHandler.getWorkItem().getStringId(),
                null);
        assertProcessInstanceCompleted(pi);

    }

    @Test
    @Timeout(10)
    public void testBoundaryTimerTimeDuration() throws Exception {
        NodeLeftCountDownProcessEventListener countDownListener = new NodeLeftCountDownProcessEventListener("BoundaryTimerEvent",
                1);
        String timeDuration = "1s";
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("org.jbpm.process");
        factory
                // header
                .name("My process")
                .packageName("org.jbpm")
                // nodes
                .startNode(one)
                .name("Start")
                .done()
                .humanTaskNode(two)
                .name("Task")
                .actorId("john")
                .taskName("MyTask")
                .done()
                .endNode(three)
                .name("End1")
                .terminate(false)
                .done()
                .boundaryEventNode(four)
                .name("BoundaryTimerEvent")
                .attachedTo(two)
                .metaData(TIME_DURATION, timeDuration)
                .metaData(CANCEL_ACTIVITY, false)
                .eventType(EVENT_TYPE_TIMER, timeDuration)
                .done()
                .endNode(five)
                .name("End2")
                .terminate(false)
                .done()
                // connections
                .connection(one, two)
                .connection(two, three)
                .connection(four, five);
        RuleFlowProcess process = factory.validate().getProcess();

        Resource res = ResourceFactory.newByteArrayResource(XmlBPMNProcessDumper.INSTANCE.dump(process).getBytes());
        res.setSourcePath("/tmp/processFactory.bpmn2"); // source path or target path must be set to be added into kbase
        kruntime = createKogitoProcessRuntime(res);
        TestWorkItemHandler testHandler = new TestWorkItemHandler();
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task",
                testHandler);
        kruntime.getProcessEventManager().addEventListener(countDownListener);

        KogitoProcessInstance pi = kruntime.startProcess("org.jbpm.process");
        assertProcessInstanceActive(pi);

        countDownListener.waitTillCompleted(); // wait for boundary timer firing

        assertNodeTriggered(pi.getStringId(),
                "End2");
        assertProcessInstanceActive(pi); // still active because CancelActivity = false

        kruntime.getKogitoWorkItemManager().completeWorkItem(testHandler.getWorkItem().getStringId(),
                null);
        assertProcessInstanceCompleted(pi);

    }

    @Test
    @Timeout(10)
    public void testAdHocSimple() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("org.jbpm.process");
        factory
                .dynamic(true)
                .name("Event Process")
                .version("1")
                .packageName("org.jbpm");
        RuleFlowProcess process = factory.validate().getProcess();
        assertThat(process).isNotNull();
        assertThat(process.isDynamic()).isTrue();
    }

    @Test
    @Timeout(10)
    public void testSignalEvent() {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("org.jbpm.process");
        factory
                .name("Event Process")
                .version("1")
                .packageName("org.jbpm")
                .variable("eventData",
                        new org.jbpm.process.core.datatype.impl.type.StringDataType())
                .startNode(one)
                .name("Start")
                .done()
                .eventNode(two)
                .name("Event1")
                .eventType("testEvent")
                .variableName("eventData")
                .done()
                .actionNode(three)
                .name("simpleActionNode")
                .action("java",
                        "System.out.println(\"test event action\");")
                .done()
                .endNode(four)
                .name("End")
                .done()
                .connection(one, two)
                .connection(two, three)
                .connection(three, four);
        RuleFlowProcess process = factory.validate().getProcess();

        assertThat(process).isNotNull();

        Application application = StaticApplicationAssembler.instance()
                .newStaticApplication(new InMemoryProcessInstancesFactory(), StaticProcessConfig.newStaticProcessConfigBuilder().build(), process);

        org.kie.kogito.process.Process<? extends Model> processDefinition =
                application.get(org.kie.kogito.process.Processes.class).processById("org.jbpm.process");
        org.kie.kogito.process.ProcessInstance<? extends Model> processInstance =
                processDefinition.createInstance(processDefinition.createModel());
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);

        processInstance.send(org.kie.kogito.process.SignalFactory.of("testEvent", null));

        assertThat(processInstance.status()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
    }

    @Test
    @Timeout(10)
    public void testActionNodeIsDroolsAction() throws Exception {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("org.jbpm.process");
        factory
                .name("ActionNodeActionProcess")
                .version("1")
                .startNode(one)
                .name("Start")
                .done()
                .endNode(three)
                .name("End")
                .done()
                .actionNode(two)
                .name("printTextActionNode")
                .action("java",
                        "System.out.println(\"test print\");",
                        true)
                .done()
                .connection(one, two)
                .connection(two, three);
        RuleFlowProcess process = factory.validate().getProcess();

        assertThat(process).isNotNull();

        Resource res = ResourceFactory.newByteArrayResource(XmlBPMNProcessDumper.INSTANCE.dump(process).getBytes());
        res.setSourcePath("/tmp/processFactory.bpmn2");
        kruntime = createKogitoProcessRuntime(res);

        KogitoProcessInstance pi = kruntime.startProcess("org.jbpm.process");

        assertThat(pi).isNotNull();

        assertThat(pi.getState()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);

    }

    @ParameterizedTest
    @ValueSource(strings = { "java.lang.RuntimeException", "Unknown error", "(?i)Status code 400", "(.*)code 4[0-9]{2}", "code 4[0-9]{2}" })
    public void testBoundaryErrorEvent(String errorCode) throws Exception {
        final String boundaryErrorEvent = "BoundaryErrorEvent";
        final String processId = "myProcess";
        final RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess(processId);
        final String startNode = "Start";
        final String task = "Task";
        final String endOnError = "EndOnError";
        factory
                // header
                .name("My process")
                .packageName("org.kie.kogito")
                // nodes
                .startNode(one)
                .name(startNode)
                .done()
                .workItemNode(two)
                .name(task)
                .workName(task)
                .done()
                .endNode(three)
                .name("EndOnSuccess")
                .done()
                .boundaryEventNode(four)
                .name(boundaryErrorEvent)
                .attachedTo(two)
                .metaData(ERROR_EVENT, errorCode)
                .metaData(HAS_ERROR_EVENT, true)
                .metaData(ERROR_STRUCTURE_REF, null)
                .metaData("EventTpe", "error")
                .eventType("Error", errorCode)
                .done()
                .endNode(five)
                .name(endOnError)
                .terminate(true)
                .done()
                // connections
                .connection(one, two)
                .connection(two, three)
                .connection(four, five);

        RuleFlowProcess process = factory.validate().getProcess();

        List<String> completedNodes = new ArrayList<>();
        KogitoProcessEventListener listener = new DefaultKogitoProcessEventListener() {
            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                completedNodes.add(event.getNodeInstance().getNodeName());
                super.afterNodeLeft(event);
            }
        };

        StaticProcessConfig processConfig = StaticProcessConfig.newStaticProcessConfigBuilder()
                .withWorkItemHandler(task, new ExceptionOnPurposeHandler())
                .withProcessListener(listener)
                .build();

        Application application = StaticApplicationAssembler.instance().newStaticApplication(new InMemoryProcessInstancesFactory(), processConfig, process);

        org.kie.kogito.process.Processes container = application.get(org.kie.kogito.process.Processes.class);
        org.kie.kogito.process.Process<? extends Model> processDefinition = container.processById(processId);
        org.kie.kogito.process.ProcessInstance<? extends Model> processInstance = processDefinition.createInstance(processDefinition.createModel());

        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
        assertThat(completedNodes).contains(startNode, task, boundaryErrorEvent, endOnError);
    }
}
