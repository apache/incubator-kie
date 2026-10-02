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
package org.jbpm.flow.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jbpm.flow.serialization.impl.ProtobufMarshallerReaderContext;
import org.jbpm.flow.serialization.impl.ProtobufProcessInstanceReader;
import org.jbpm.flow.serialization.impl.ProtobufProcessInstanceWriter;
import org.jbpm.flow.serialization.impl.ProtobufProcessMarshallerWriteContext;
import org.jbpm.flow.serialization.impl.ProtobufVariableReader;
import org.jbpm.flow.serialization.impl.ProtobufVariableWriter;
import org.jbpm.flow.serialization.protobuf.KogitoTypesProtobuf;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.ruleflow.core.WorkflowElementIdentifierFactory;
import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.impl.ConnectionImpl;
import org.jbpm.workflow.core.impl.WorkflowProcessImpl;
import org.jbpm.workflow.core.node.EndNode;
import org.jbpm.workflow.core.node.HumanTaskNode;
import org.jbpm.workflow.core.node.StartNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.kie.kogito.Model;
import org.kie.kogito.internal.process.runtime.KogitoProcessRuntime;
import org.kie.kogito.jackson.utils.ObjectMapperFactory;
import org.kie.kogito.process.ProcessInstanceReadMode;
import org.kie.kogito.process.impl.AbstractProcess;
import org.kie.kogito.process.impl.AbstractProcessInstance;
import org.w3c.dom.Document;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.annotation.XmlRootElement;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.internal.process.runtime.KogitoWorkflowProcess.RULEFLOW_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProcessInstanceMarshallTest {
    private static final String PROCESS_DESCRIPTION = "The description";
    private static final String PROCESS_INSTANCE_ID = "1";
    private static final String ROOT_PROCESS_ID = "rootProcess";
    private static final String ROOT_PROCESS_VERSION = "v1";
    private static final String ROOT_PROCESS_INSTANCE_ID = "0";
    private static final String PARENT_PROCESS_ID = "2";

    private static AbstractProcess<?> process;

    private static WorkflowProcessImpl workflow;

    @BeforeAll
    public static void init() {
        workflow = new WorkflowProcessImpl();

        workflow.setId("processId");
        workflow.setVersion("1.0");
        workflow.setType(RULEFLOW_TYPE);

        Node endNode = new EndNode();
        endNode.setId(WorkflowElementIdentifierFactory.fromExternalFormat("one"));
        endNode.setName("end node");

        Node taskNode = new HumanTaskNode();
        taskNode.setId(WorkflowElementIdentifierFactory.fromExternalFormat("two"));
        taskNode.setName("human task");
        new ConnectionImpl(taskNode, Node.CONNECTION_DEFAULT_TYPE, endNode, Node.CONNECTION_DEFAULT_TYPE);

        Node startNode = new StartNode();
        startNode.setId(WorkflowElementIdentifierFactory.fromExternalFormat("three"));
        startNode.setName("start node");
        new ConnectionImpl(startNode, Node.CONNECTION_DEFAULT_TYPE, taskNode, Node.CONNECTION_DEFAULT_TYPE);

        workflow.addNode(startNode);
        workflow.addNode(taskNode);
        workflow.addNode(endNode);

        process = mock(AbstractProcess.class);
        when(process.getProcessRuntime()).thenReturn(mock(KogitoProcessRuntime.class));
        when(process.get()).thenReturn(workflow);
    }

    @XmlRootElement
    public static class MarshableObject implements Serializable {

        private static final long serialVersionUID = 1481370154514125687L;

        private String name;

        public MarshableObject() {
        }

        public MarshableObject(String name) {
            this.name = name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MarshableObject) {
                return this.name.equals(((MarshableObject) obj).name);
            }
            return false;
        }
    }

    private static Stream<Arguments> testProcessInstanceMarshalling() {
        return Stream.of(Arguments.of(buildInstance(false)),
                (Arguments.of(buildInstance(true))));
    }

    private static RuleFlowProcessInstance buildInstance(boolean orphan) {
        RuleFlowProcessInstance instance = new RuleFlowProcessInstance();
        instance.setId(PROCESS_INSTANCE_ID);
        instance.setStartDate(new Date());
        instance.setDescription(PROCESS_DESCRIPTION);

        if (!orphan) {
            instance.setRootProcessInstanceId(ROOT_PROCESS_INSTANCE_ID);
            instance.setRootProcessId(ROOT_PROCESS_ID);
            instance.setRootProcessVersion(ROOT_PROCESS_VERSION);
            instance.setParentProcessInstanceId(PARENT_PROCESS_ID);
        }

        instance.setProcess(workflow);
        instance.setContextInstance(VariableScope.VARIABLE_SCOPE, new VariableScopeInstance());

        return instance;
    }

    private static Stream<Arguments> testRoundTrip() throws Exception {
        MarshableObject marshableObject = new MarshableObject("henry");
        JAXBContext jaxbContext = JAXBContext.newInstance(MarshableObject.class);
        jakarta.xml.bind.Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

        jaxbMarshaller.setProperty(jakarta.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT, true);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.newDocument();
        jaxbMarshaller.marshal(marshableObject, document);

        return Stream.of(
                Arguments.of(1),
                Arguments.of("hello"),
                Arguments.of(Boolean.TRUE),
                Arguments.of(2f),
                Arguments.of(3d),
                Arguments.of(5l),
                Arguments.of(BigDecimal.valueOf(10l)),
                Arguments.of(new MarshableObject("henry")),
                Arguments.of(ObjectMapperFactory.listenerAware().readTree("{ \"key\" : \"value\" }")),
                Arguments.of(ObjectMapperFactory.listenerAware().valueToTree(marshableObject)),
                Arguments.of(new Date()),
                Arguments.of(Instant.now()),
                Arguments.of(OffsetDateTime.now()),
                Arguments.of(LocalDateTime.now()),
                Arguments.of(LocalDate.now()),
                Arguments.of(ZonedDateTime.now()),
                Arguments.of(new Timestamp(System.currentTimeMillis())),
                Arguments.of(Duration.ofDays(1)),
                Arguments.of(document));
    }

    @ParameterizedTest
    @MethodSource
    public void testProcessInstanceMarshalling(RuleFlowProcessInstance toMarshall) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProtobufProcessMarshallerWriteContext ctxOut = new ProtobufProcessMarshallerWriteContext(out);
        ctxOut.set(MarshallerContextName.OBJECT_MARSHALLING_STRATEGIES, ObjectMarshallerStrategyHelper.defaultStrategies());
        ctxOut.set(MarshallerContextName.MARSHALLER_PROCESS, process);

        ProtobufProcessInstanceWriter writer = new ProtobufProcessInstanceWriter(ctxOut);

        writer.writeProcessInstance(toMarshall, out);

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        ProtobufMarshallerReaderContext ctxIn = new ProtobufMarshallerReaderContext(in);
        ctxIn.set(MarshallerContextName.OBJECT_MARSHALLING_STRATEGIES, ObjectMarshallerStrategyHelper.defaultStrategies());
        ctxIn.set(MarshallerContextName.MARSHALLER_PROCESS, process);
        ProtobufProcessInstanceReader reader = new ProtobufProcessInstanceReader(ctxIn);
        RuleFlowProcessInstance unmarshalled = reader.read(in);

        assertThat(unmarshalled)
                .hasFieldOrPropertyWithValue("id", toMarshall.getId())
                .hasFieldOrPropertyWithValue("state", toMarshall.getState())
                .hasFieldOrPropertyWithValue("startDate", toMarshall.getStartDate())
                .hasFieldOrPropertyWithValue("processId", toMarshall.getProcessId())
                .hasFieldOrPropertyWithValue("processVersion", toMarshall.getProcessVersion())
                .hasFieldOrPropertyWithValue("description", toMarshall.getDescription())
                .hasFieldOrPropertyWithValue("rootProcessInstanceId", toMarshall.getRootProcessInstanceId())
                .hasFieldOrPropertyWithValue("rootProcessId", toMarshall.getRootProcessId())
                .hasFieldOrPropertyWithValue("rootProcessVersion", toMarshall.getRootProcessVersion())
                .hasFieldOrPropertyWithValue("parentProcessInstanceId", toMarshall.getParentProcessInstanceId())
                .hasFieldOrPropertyWithValue("process", toMarshall.getProcess());
    }

    @ParameterizedTest
    @MethodSource
    @NullSource
    public void testRoundTrip(Object toMarshall) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProtobufProcessMarshallerWriteContext ctxOut = new ProtobufProcessMarshallerWriteContext(out);
        ctxOut.set(MarshallerContextName.OBJECT_MARSHALLING_STRATEGIES, ObjectMarshallerStrategyHelper.defaultStrategies());
        ctxOut.set(MarshallerContextName.MARSHALLER_PROCESS, process);
        ProtobufVariableWriter writer = new ProtobufVariableWriter(ctxOut);
        List<KogitoTypesProtobuf.Variable> variables = writer.buildVariables(singletonMap("var", toMarshall).entrySet().stream().collect(Collectors.toList()));

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        ProtobufMarshallerReaderContext ctxIn = new ProtobufMarshallerReaderContext(in);
        ctxIn.set(MarshallerContextName.OBJECT_MARSHALLING_STRATEGIES, ObjectMarshallerStrategyHelper.defaultStrategies());
        ctxIn.set(MarshallerContextName.MARSHALLER_PROCESS, process);
        ProtobufVariableReader reader = new ProtobufVariableReader(ctxIn);
        List<Variable> unmarshalledVars = reader.buildVariables(variables);
        assertThat(unmarshalledVars).hasSize(1);
        assertThat(unmarshalledVars.get(0).getValue())
                .usingComparatorForType(new DocumentComparator(), Document.class)
                .usingRecursiveComparison()
                .isEqualTo(toMarshall);
    }

    public class DocumentComparator implements Comparator<Document> {

        @Override
        public int compare(Document doc1, Document doc2) {
            return toXml(doc1).compareTo(toXml(doc2));
        }
    }

    private String toXml(Document document) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer trans = tf.newTransformer();
            StringWriter sw = new StringWriter();
            trans.transform(new DOMSource(document), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testUnmarshallProcessInstanceLoadsStateLazily() throws Exception {
        assertThat(unmarshallProcessInstance(ProcessInstanceReadMode.MUTABLE).internalGetProcessInstance()).isNull();
    }

    @Test
    public void testUnmarshallProcessInstanceKeepsStateWhenEager() throws Exception {
        assertThat(unmarshallProcessInstance(ProcessInstanceReadMode.MUTABLE_EAGER).internalGetProcessInstance()).isNotNull();
    }

    @SuppressWarnings("unchecked")
    private AbstractProcessInstance<?> unmarshallProcessInstance(ProcessInstanceReadMode mode) throws Exception {
        AbstractProcess<TestModel> testProcess = mock(AbstractProcess.class);
        when(testProcess.get()).thenReturn(workflow);
        when(testProcess.getProcessRuntime()).thenReturn(mock(KogitoProcessRuntime.class));
        when(testProcess.createInstance(any(WorkflowProcessInstance.class)))
                .thenAnswer(invocation -> new TestProcessInstance(testProcess, new TestModel(), mock(InternalProcessRuntime.class), invocation.getArgument(0)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProtobufProcessMarshallerWriteContext ctxOut = new ProtobufProcessMarshallerWriteContext(out);
        ctxOut.set(MarshallerContextName.OBJECT_MARSHALLING_STRATEGIES, ObjectMarshallerStrategyHelper.defaultStrategies());
        ctxOut.set(MarshallerContextName.MARSHALLER_PROCESS, testProcess);
        new ProtobufProcessInstanceWriter(ctxOut).writeProcessInstance(buildInstance(false), out);

        ProcessInstanceMarshallerService marshaller = ProcessInstanceMarshallerService.newBuilder().withDefaultObjectMarshallerStrategies().build();
        return (AbstractProcessInstance<?>) marshaller.unmarshallProcessInstance(out.toByteArray(), testProcess, mode);
    }

    static class TestProcessInstance extends AbstractProcessInstance<TestModel> {

        public TestProcessInstance(AbstractProcess<TestModel> process, TestModel variables, InternalProcessRuntime rt, WorkflowProcessInstance wpi) {
            super(process, variables, rt, wpi);
        }
    }

    static class TestModel implements Model {

        @Override
        public void update(Map<String, Object> params) {
            fromMap(params);
        }

        @Override
        public Map<String, Object> toMap() {
            return null;
        }

        @Override
        public TestModel fromMap(Map<String, Object> params) {
            return this;
        }
    }

}
