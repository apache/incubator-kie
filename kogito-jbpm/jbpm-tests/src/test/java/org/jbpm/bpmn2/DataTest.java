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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jbpm.bpmn2.activity.XPathProcessModel;
import org.jbpm.bpmn2.activity.XPathProcessProcess;
import org.jbpm.bpmn2.core.Association;
import org.jbpm.bpmn2.core.DataStore;
import org.jbpm.bpmn2.core.Definitions;
import org.jbpm.bpmn2.data.DataInputAssociationsLazyCreatingModel;
import org.jbpm.bpmn2.data.DataInputAssociationsLazyCreatingProcess;
import org.jbpm.bpmn2.data.DataInputAssociationsModel;
import org.jbpm.bpmn2.data.DataInputAssociationsProcess;
import org.jbpm.bpmn2.data.DataInputAssociationsStringModel;
import org.jbpm.bpmn2.data.DataInputAssociationsStringNoQuotesModel;
import org.jbpm.bpmn2.data.DataInputAssociationsStringNoQuotesProcess;
import org.jbpm.bpmn2.data.DataInputAssociationsStringObjectModel;
import org.jbpm.bpmn2.data.DataInputAssociationsStringObjectProcess;
import org.jbpm.bpmn2.data.DataInputAssociationsStringProcess;
import org.jbpm.bpmn2.data.DataInputAssociationsVariableModel;
import org.jbpm.bpmn2.data.DataInputAssociationsVariableProcess;
import org.jbpm.bpmn2.data.DataInputAssociationsXmlLiteralModel;
import org.jbpm.bpmn2.data.DataInputAssociationsXmlLiteralProcess;
import org.jbpm.bpmn2.data.DataObjectModel;
import org.jbpm.bpmn2.data.DataObjectProcess;
import org.jbpm.bpmn2.data.DataOutputAssociationsModel;
import org.jbpm.bpmn2.data.DataOutputAssociationsProcess;
import org.jbpm.bpmn2.data.DataOutputAssociationsXmlNodeModel;
import org.jbpm.bpmn2.data.DataOutputAssociationsXmlNodeProcess;
import org.jbpm.bpmn2.data.Evaluation2Model;
import org.jbpm.bpmn2.data.Evaluation2Process;
import org.jbpm.bpmn2.data.Evaluation3Model;
import org.jbpm.bpmn2.data.Evaluation3Process;
import org.jbpm.bpmn2.data.EvaluationModel;
import org.jbpm.bpmn2.data.EvaluationProcess;
import org.jbpm.bpmn2.data.ImportModel;
import org.jbpm.bpmn2.data.ImportProcess;
import org.jbpm.bpmn2.flow.DataOutputAssociationsHumanTaskModel;
import org.jbpm.bpmn2.flow.DataOutputAssociationsHumanTaskProcess;
import org.jbpm.bpmn2.objects.TestWorkItemHandler;
import org.jbpm.bpmn2.xml.ProcessHandler;
import org.jbpm.process.core.datatype.impl.type.ObjectDataType;
import org.jbpm.process.workitem.builtin.SystemOutWorkItemHandler;
import org.jbpm.test.utils.ProcessTestHelper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kie.kogito.Application;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcessInstance;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.process.workitem.WorkItemTransition;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.workitem.DefaultKogitoWorkItemHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

public class DataTest extends JbpmBpmn2TestCase {

    @Test
    public void testImport() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<ImportModel> process = ImportProcess.newProcess(app);
        ImportModel model = process.createModel();
        ProcessInstance<ImportModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(org.jbpm.process.instance.ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testDataObject() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<DataObjectModel> process = DataObjectProcess.newProcess(app);
        DataObjectModel model = process.createModel();
        model.setEmployee("UserId-12345");
        ProcessInstance<DataObjectModel> processInstance = process.createInstance(model);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(org.jbpm.process.instance.ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testDataStore() throws Exception {
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/data/BPMN2-DataStore.bpmn2");
        KogitoProcessInstance processInstance = kruntime.startProcess("DataStore");
        Definitions def = (Definitions) processInstance.getProcess()
                .getMetaData().get("Definitions");
        assertThat(def.getDataStores()).isNotNull().hasSize(1);

        DataStore dataStore = def.getDataStores().get(0);
        assertThat(dataStore.getId()).isEqualTo("employee");
        assertThat(dataStore.getName()).isEqualTo("employeeStore");
        assertThat(((ObjectDataType) dataStore.getType()).getClassName()).isEqualTo(String.class.getCanonicalName());

    }

    @Test
    public void testAssociation() throws Exception {
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/data/BPMN2-Association.bpmn2");
        KogitoProcessInstance processInstance = kruntime.startProcess("Association");
        List<Association> associations = (List<Association>) processInstance.getProcess().getMetaData().get(ProcessHandler.ASSOCIATIONS);
        assertThat(associations).isNotNull().hasSize(1);

        Association assoc = associations.get(0);
        assertThat(assoc.getId()).isEqualTo("_1234");
        assertThat(assoc.getSourceRef()).isEqualTo("_1");
        assertThat(assoc.getTargetRef()).isEqualTo("_2");

    }

    @Test
    public void testEvaluationProcess() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        ProcessTestHelper.registerHandler(app, "RegisterRequest", new SystemOutWorkItemHandler());

        org.kie.kogito.process.Process<EvaluationModel> processDefinition = EvaluationProcess.newProcess(app);
        EvaluationModel model = processDefinition.createModel();
        model.setEmployee("UserId-12345");

        org.kie.kogito.process.ProcessInstance<EvaluationModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEvaluationProcess2() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());

        org.kie.kogito.process.Process<Evaluation2Model> processDefinition = Evaluation2Process.newProcess(app);
        Evaluation2Model model = processDefinition.createModel();
        model.setEmployee("UserId-12345");

        org.kie.kogito.process.ProcessInstance<Evaluation2Model> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEvaluationProcess3() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        ProcessTestHelper.registerHandler(app, "RegisterRequest", new SystemOutWorkItemHandler());

        org.kie.kogito.process.Process<Evaluation3Model> processDefinition = Evaluation3Process.newProcess(app);
        Evaluation3Model model = processDefinition.createModel();
        model.setEmployee("john2");

        org.kie.kogito.process.ProcessInstance<Evaluation3Model> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testXpathExpression() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<XPathProcessModel> process = XPathProcessProcess.newProcess(app);
        XPathProcessModel model = process.createModel();

        Document document = DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(
                        "<instanceMetadata><user approved=\"false\" /></instanceMetadata>"
                                .getBytes()));

        model.setInstanceMetadata(document);
        ProcessInstance<XPathProcessModel> processInstance = process.createInstance(model);
        processInstance.start();
        assertThat(processInstance).extracting(ProcessInstance::status).isEqualTo(ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testDataInputAssociations() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler() {

            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                assertThat(workItem.getParameter("coId")).isEqualTo("hello world");
                return Optional.empty();
            }

        });

        Document document = DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream("<user hello='hello world' />".getBytes()));

        org.kie.kogito.process.Process<DataInputAssociationsModel> processDefinition = DataInputAssociationsProcess.newProcess(app);
        DataInputAssociationsModel model = processDefinition.createModel();
        model.setInstanceMetadata(document.getFirstChild());

        org.kie.kogito.process.ProcessInstance<DataInputAssociationsModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testDataInputAssociationsWithStringObject() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                assertThat(workItem.getParameter("coId")).isEqualTo("hello");
                return Optional.empty();
            }

        });

        org.kie.kogito.process.Process<DataInputAssociationsStringObjectModel> processDefinition = DataInputAssociationsStringObjectProcess.newProcess(app);
        DataInputAssociationsStringObjectModel model = processDefinition.createModel();
        model.setInstanceMetadata("hello");

        org.kie.kogito.process.ProcessInstance<DataInputAssociationsStringObjectModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    /**
     * TODO testDataInputAssociationsWithLazyLoading
     */
    @Test
    public void testDataInputAssociationsWithLazyLoading() throws Exception {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler() {

            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                Element coIdParamObj = (Element) workItem.getParameter("coId");
                assertThat(coIdParamObj.getNodeName()).isEqualTo("mynode");
                assertThat(coIdParamObj.getFirstChild().getNodeName()).isEqualTo("user");
                assertThat(coIdParamObj.getFirstChild().getAttributes().getNamedItem("hello").getNodeValue()).isEqualTo("hello world");
                return Optional.empty();
            }

        });

        Document document = DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream("<mydoc><mynode><user hello='hello world' /></mynode></mydoc>".getBytes()));

        org.kie.kogito.process.Process<DataInputAssociationsLazyCreatingModel> processDefinition = DataInputAssociationsLazyCreatingProcess.newProcess(app);
        DataInputAssociationsLazyCreatingModel model = processDefinition.createModel();
        model.setInstanceMetadata(document);

        org.kie.kogito.process.ProcessInstance<DataInputAssociationsLazyCreatingModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testDataInputAssociationsWithString() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                assertThat(workItem.getParameter("coId")).isEqualTo("hello");
                return Optional.empty();
            }

        });

        org.kie.kogito.process.Process<DataInputAssociationsStringModel> processDefinition = DataInputAssociationsStringProcess.newProcess(app);
        DataInputAssociationsStringModel model = processDefinition.createModel();

        org.kie.kogito.process.ProcessInstance<DataInputAssociationsStringModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testDataInputAssociationsWithStringWithoutQuotes() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler() {

            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                assertThat(workItem.getParameter("coId")).isEqualTo("hello");
                return Optional.empty();
            }

        });

        org.kie.kogito.process.Process<DataInputAssociationsStringNoQuotesModel> processDefinition = DataInputAssociationsStringNoQuotesProcess.newProcess(app);
        DataInputAssociationsStringNoQuotesModel model = processDefinition.createModel();

        org.kie.kogito.process.ProcessInstance<DataInputAssociationsStringNoQuotesModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testDataInputAssociationsWithVariable() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                assertThat(workItem.getParameter("taskInput")).isEqualTo("variableValue123");
                return Optional.empty();
            }
        });

        org.kie.kogito.process.Process<DataInputAssociationsVariableModel> processDefinition = DataInputAssociationsVariableProcess.newProcess(app);
        DataInputAssociationsVariableModel model = processDefinition.createModel();
        model.setMyVar("variableValue123");

        org.kie.kogito.process.ProcessInstance<DataInputAssociationsVariableModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testDataInputAssociationsWithXMLLiteral() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                assertThat(((org.w3c.dom.Node) workItem.getParameter("coId")).getNodeName()).isEqualTo("id");
                assertThat(((org.w3c.dom.Node) workItem.getParameter("coId")).getFirstChild().getTextContent()).isEqualTo("some text");
                return Optional.empty();
            }

        });

        org.kie.kogito.process.Process<DataInputAssociationsXmlLiteralModel> processDefinition = DataInputAssociationsXmlLiteralProcess.newProcess(app);
        DataInputAssociationsXmlLiteralModel model = processDefinition.createModel();

        org.kie.kogito.process.ProcessInstance<DataInputAssociationsXmlLiteralModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    /**
     * TODO testDataInputAssociationsWithTwoAssigns
     */
    @Test
    @Disabled
    public void testDataInputAssociationsWithTwoAssigns() throws Exception {
        kruntime = createKogitoProcessRuntime("BPMN2-DataInputAssociations-two-assigns.bpmn2");
        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task", new DefaultKogitoWorkItemHandler() {

            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                assertThat(((Element) workItem.getParameter("Comment")).getNodeName()).isEqualTo("foo");
                return Optional.empty();
            }

        });
        Document document = DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream("<user hello='hello world' />"
                        .getBytes()));
        Map<String, Object> params = new HashMap<>();
        params.put("instanceMetadata", document.getFirstChild());
        KogitoProcessInstance processInstance = kruntime.startProcess("process", params);
        assertThat(processInstance.getState()).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testDataOutputAssociationsforHumanTask() {
        Application app = ProcessTestHelper.newApplication();
        List<org.w3c.dom.Document> documents = new ArrayList<>();
        List<KogitoWorkItem> workItems = new ArrayList<>();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler() {

            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder;
                try {
                    builder = factory.newDocumentBuilder();
                } catch (ParserConfigurationException e) {
                    throw new RuntimeException(e);
                }
                final Map<String, Object> results = new HashMap<>();
                // process metadata
                org.w3c.dom.Document processMetadaDoc = builder.newDocument();
                org.w3c.dom.Element processMetadata = processMetadaDoc.createElement("previoustasksowner");
                processMetadaDoc.appendChild(processMetadata);
                processMetadata.setAttribute("primaryname", "my_result");
                documents.add(processMetadaDoc);
                results.put("output", processMetadata);

                workItems.add(workItem);

                return Optional.of(this.workItemLifeCycle.newTransition("complete", workItem.getPhaseStatus(), results));
            }

        });

        org.kie.kogito.process.Process<DataOutputAssociationsHumanTaskModel> processDefinition = DataOutputAssociationsHumanTaskProcess.newProcess(app);
        DataOutputAssociationsHumanTaskModel model = processDefinition.createModel();

        org.kie.kogito.process.ProcessInstance<DataOutputAssociationsHumanTaskModel> instance = processDefinition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
        assertThat(documents.size()).isEqualTo(1);
        NodeList nodeList = documents.get(0).getElementsByTagName("previoustasksowner");
        assertThat(nodeList.getLength()).isEqualTo(1);
        assertThat(nodeList.item(0).getAttributes().getNamedItem("primaryname")).isNotNull();
        assertThat(nodeList.item(0).getAttributes().getNamedItem("primaryname").getNodeValue()).isEqualTo("my_result");
        assertThat(workItems.size()).isGreaterThanOrEqualTo(1);
        KogitoWorkItem workItem = workItems.get(0);
        assertThat(workItem.getResults().get("output")).isInstanceOf(org.w3c.dom.Node.class);
        assertThat((org.w3c.dom.Node) (workItem.getResults().get("output"))).isEqualTo(nodeList.item(0));

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testDataOutputAssociations() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                try {
                    Document document = DocumentBuilderFactory
                            .newInstance()
                            .newDocumentBuilder()
                            .parse(new ByteArrayInputStream("<user hello='hello world' />".getBytes()));
                    Map<String, Object> params = new HashMap<>();
                    params.put("output", document.getFirstChild());
                    return Optional.of(this.workItemLifeCycle.newTransition("complete", workItem.getPhaseStatus(), params));
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }

        });

        org.kie.kogito.process.Process<DataOutputAssociationsModel> processDefinition = DataOutputAssociationsProcess.newProcess(app);
        DataOutputAssociationsModel model = processDefinition.createModel();

        org.kie.kogito.process.ProcessInstance<DataOutputAssociationsModel> instance = processDefinition.createInstance(model);
        instance.start();
        assertThat(instance.variables()).isNotNull();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testDataOutputAssociationsXmlNode() {
        Application app = ProcessTestHelper.newApplication();
        List<org.w3c.dom.Document> documents = new ArrayList<>();
        List<KogitoWorkItem> workItems = new ArrayList<>();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler() {

            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
                try {
                    Document document = DocumentBuilderFactory
                            .newInstance()
                            .newDocumentBuilder()
                            .parse(new ByteArrayInputStream("<user hello='hello world' />".getBytes()));
                    Map<String, Object> params = new HashMap<>();
                    params.put("output", document.getFirstChild());
                    documents.add(document);
                    workItems.add(workItem);
                    return Optional.of(this.workItemLifeCycle.newTransition("complete", workItem.getPhaseStatus(), params));

                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }

        });

        org.kie.kogito.process.Process<DataOutputAssociationsXmlNodeModel> processDefinition = DataOutputAssociationsXmlNodeProcess.newProcess(app);
        DataOutputAssociationsXmlNodeModel model = processDefinition.createModel();

        org.kie.kogito.process.ProcessInstance<DataOutputAssociationsXmlNodeModel> instance = processDefinition.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
        assertThat(workItems.size()).isGreaterThanOrEqualTo(1);
        KogitoWorkItem workItem = workItems.get(0);
        assertThat(workItem).isNotNull();
        assertThat(documents.size()).isGreaterThanOrEqualTo(1);
        org.w3c.dom.Node node = documents.get(0).getFirstChild();
        assertThat(workItem.getResults().get("output")).isInstanceOf(org.w3c.dom.Node.class);
        assertThat((org.w3c.dom.Node) (workItem.getResults().get("output"))).isEqualTo(node);

        assertThat(instance.variables().getInstanceMetadata()).isNotNull();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);

    }

    @Test
    public void testDefaultProcessVariableValue() throws Exception {

        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/data/BPMN2-CorrelationKey.bpmn2");

        kruntime.getKogitoWorkItemManager().registerWorkItemHandler("Human Task", new TestWorkItemHandler());
        Map<String, Object> parameters = new HashMap<String, Object>();

        KogitoWorkflowProcessInstance processInstance = (KogitoWorkflowProcessInstance) kruntime.startProcess("CorrelationKey", parameters);

        assertThat(processInstance.getVariable("procVar")).isEqualTo("defaultProc");
        assertThat(processInstance.getVariable("intVar")).isEqualTo(1);
        assertThat(processInstance.getState()).isEqualTo(ProcessInstance.STATE_ACTIVE);

    }

}
