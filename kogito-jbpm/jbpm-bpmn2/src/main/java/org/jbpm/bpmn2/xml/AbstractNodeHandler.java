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
package org.jbpm.bpmn2.xml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;

import org.drools.mvel.java.JavaDialect;
import org.jbpm.bpmn2.core.Association;
import org.jbpm.bpmn2.core.Definitions;
import org.jbpm.bpmn2.core.Error;
import org.jbpm.bpmn2.core.ItemDefinition;
import org.jbpm.bpmn2.core.Lane;
import org.jbpm.bpmn2.core.SequenceFlow;
import org.jbpm.bpmn2.core.Signal;
import org.jbpm.bpmn2.core.TextAnnotation;
import org.jbpm.compiler.xml.Handler;
import org.jbpm.compiler.xml.Parser;
import org.jbpm.compiler.xml.ProcessBuildData;
import org.jbpm.compiler.xml.compiler.XmlDumper;
import org.jbpm.compiler.xml.core.BaseAbstractHandler;
import org.jbpm.compiler.xml.core.ExtensibleXmlParser;
import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.core.datatype.DataTypeResolver;
import org.jbpm.process.instance.impl.MVELInterpretedReturnValueEvaluator;
import org.jbpm.process.instance.impl.ReturnValueEvaluator;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.ruleflow.core.WorkflowElementIdentifierFactory;
import org.jbpm.util.PatternConstants;
import org.jbpm.workflow.core.DroolsAction;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.NodeContainer;
import org.jbpm.workflow.core.impl.DataAssociation;
import org.jbpm.workflow.core.impl.DataAssociation.DataAssociationType;
import org.jbpm.workflow.core.impl.DataDefinition;
import org.jbpm.workflow.core.impl.DroolsConsequenceAction;
import org.jbpm.workflow.core.impl.ExtendedNodeImpl;
import org.jbpm.workflow.core.impl.IOSpecification;
import org.jbpm.workflow.core.impl.MultiInstanceSpecification;
import org.jbpm.workflow.core.impl.NodeImpl;
import org.jbpm.workflow.core.node.ActionNode;
import org.jbpm.workflow.core.node.Assignment;
import org.jbpm.workflow.core.node.CatchLinkNode;
import org.jbpm.workflow.core.node.CompositeContextNode;
import org.jbpm.workflow.core.node.EndNode;
import org.jbpm.workflow.core.node.EventNode;
import org.jbpm.workflow.core.node.FaultNode;
import org.jbpm.workflow.core.node.ForEachNode;
import org.jbpm.workflow.core.node.StateNode;
import org.jbpm.workflow.core.node.TimerNode;
import org.jbpm.workflow.core.node.Transformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import static java.lang.Thread.currentThread;
import static org.jbpm.process.core.datatype.DataTypeResolver.fromType;
import static org.jbpm.ruleflow.core.Metadata.COMPLETION_CONDITION;
import static org.jbpm.ruleflow.core.Metadata.MAPPING_VARIABLE;
import static org.jbpm.ruleflow.core.Metadata.MAPPING_VARIABLE_INPUT;
import static org.jbpm.ruleflow.core.Metadata.VARIABLE;

public abstract class AbstractNodeHandler extends BaseAbstractHandler implements Handler {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractNodeHandler.class);

    static final String PROCESS_INSTANCE_SIGNAL_EVENT = "kcontext.getProcessInstance().signalEvent(";
    static final String RUNTIME_SIGNAL_EVENT = "kcontext.getKogitoProcessRuntime().signalEvent(";

    public static final String INPUT_TYPES = "BPMN.InputTypes";
    public static final String OUTPUT_TYPES = "BPMN.OutputTypes";

    protected static final String EOL = System.getProperty("line.separator");

    public AbstractNodeHandler() {
        initValidParents();
        initValidPeers();
        this.allowNesting = true;
    }

    protected void initValidParents() {
        this.validParents = new HashSet<Class<?>>();
        this.validParents.add(NodeContainer.class);
    }

    protected void initValidPeers() {
        this.validPeers = new HashSet<Class<?>>();
        this.validPeers.add(null);
        this.validPeers.add(Lane.class);
        this.validPeers.add(Variable.class);
        this.validPeers.add(Node.class);
        this.validPeers.add(SequenceFlow.class);
        this.validPeers.add(Lane.class);
        this.validPeers.add(Association.class);
        this.validPeers.add(TextAnnotation.class);
    }

    @Override
    public Object start(final String uri, final String localName, final Attributes attrs,
            final Parser parser) throws SAXException {
        parser.startElementBuilder(localName, attrs);

        String id = attrs.getValue("id");
        String name = attrs.getValue("name");

        Node node = createNode(attrs);
        node.setId(WorkflowElementIdentifierFactory.fromExternalFormat(id));
        node.setName(name);
        node.setMetaData(INPUT_TYPES, new HashMap<String, String>());
        node.setMetaData(OUTPUT_TYPES, new HashMap<String, String>());
        return node;
    }

    protected abstract Node createNode(Attributes attrs);

    @Override
    public Object end(final String uri, final String localName,
            final Parser parser) throws SAXException {
        final Element element = parser.endElementBuilder();
        Node node = (Node) parser.getCurrent();
        node = handleNode(node, element, uri, localName, parser);
        NodeContainer nodeContainer = (NodeContainer) parser.getParent();
        nodeContainer.addNode(node);
        ((ProcessBuildData) parser.getData()).addNode(node);
        return node;
    }

    protected Node handleNode(final Node node, final Element element, final String uri,
            final String localName, final Parser parser)
            throws SAXException {
        final String x = element.getAttribute("x");
        if (x != null && x.length() != 0) {
            try {
                node.setMetaData("x", Integer.parseInt(x));
            } catch (NumberFormatException exc) {
                throw new SAXParseException("<" + localName + "> requires an Integer 'x' attribute", parser.getLocator());
            }
        }
        final String y = element.getAttribute("y");
        if (y != null && y.length() != 0) {
            try {
                node.setMetaData("y", new Integer(y));
            } catch (NumberFormatException exc) {
                throw new SAXParseException("<" + localName + "> requires an Integer 'y' attribute", parser.getLocator());
            }
        }
        final String width = element.getAttribute("width");
        if (width != null && width.length() != 0) {
            try {
                node.setMetaData("width", new Integer(width));
            } catch (NumberFormatException exc) {
                throw new SAXParseException("<" + localName + "> requires an Integer 'width' attribute", parser.getLocator());
            }
        }
        final String height = element.getAttribute("height");
        if (height != null && height.length() != 0) {
            try {
                node.setMetaData("height", new Integer(height));
            } catch (NumberFormatException exc) {
                throw new SAXParseException("<" + localName + "> requires an Integer 'height' attribute", parser.getLocator());
            }
        }
        return node;
    }

    public abstract void writeNode(final Node node, final StringBuilder xmlDump,
            final int metaDataType);

    protected void writeNode(final String name, final Node node,
            final StringBuilder xmlDump, int metaDataType) {
        xmlDump.append("    <" + name + " ");
        xmlDump.append("id=\"" + XmlBPMNProcessDumper.getUniqueNodeId(node) + "\" ");
        if (node.getName() != null) {
            xmlDump.append("name=\"" + XmlBPMNProcessDumper.replaceIllegalCharsAttribute(node.getName()) + "\" ");
        }
        if (metaDataType == XmlBPMNProcessDumper.META_DATA_AS_NODE_PROPERTY) {
            Integer x = (Integer) node.getMetaData().get("x");
            Integer y = (Integer) node.getMetaData().get("y");
            Integer width = (Integer) node.getMetaData().get("width");
            Integer height = (Integer) node.getMetaData().get("height");
            if (x != null && x != 0) {
                xmlDump.append("g:x=\"" + x + "\" ");
            }
            if (y != null && y != 0) {
                xmlDump.append("g:y=\"" + y + "\" ");
            }
            if (width != null && width != -1) {
                xmlDump.append("g:width=\"" + width + "\" ");
            }
            if (height != null && height != -1) {
                xmlDump.append("g:height=\"" + height + "\" ");
            }
        }
    }

    protected void endNode(final StringBuilder xmlDump) {
        xmlDump.append("/>" + EOL);
    }

    protected void endNode(final String name, final StringBuilder xmlDump) {
        xmlDump.append("    </" + name + ">" + EOL);
    }

    protected void handleScript(final ExtendedNodeImpl node, final Element element, String type) {
        NodeList nodeList = element.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            if (nodeList.item(i) instanceof Element) {
                Element xmlNode = (Element) nodeList.item(i);
                String nodeName = xmlNode.getNodeName();
                if (nodeName.equals("extensionElements")) {
                    NodeList subNodeList = xmlNode.getChildNodes();
                    for (int j = 0; j < subNodeList.getLength(); j++) {
                        org.w3c.dom.Node subXmlNode = subNodeList.item(j);
                        if (subXmlNode.getNodeName().contains(type + "-script")) {
                            List<DroolsAction> actions = node.getActions(type);
                            if (actions == null) {
                                actions = new ArrayList<>();
                                node.setActions(type, actions);
                            }
                            DroolsAction action = extractScript((Element) subXmlNode);
                            actions.add(action);
                        }
                    }
                }
            }
        }
    }

    public static DroolsAction extractScript(Element xmlNode) {
        String dialect = "java";
        if ("http://www.mvel.org/2.0".equals(xmlNode.getAttribute("scriptFormat"))) {
            dialect = "mvel";
        }
        NodeList subNodeList = xmlNode.getChildNodes();
        for (int j = 0; j < subNodeList.getLength(); j++) {
            if (subNodeList.item(j) instanceof Element) {
                Element subXmlNode = (Element) subNodeList.item(j);
                if ("script".equals(subXmlNode.getNodeName())) {
                    String consequence = subXmlNode.getTextContent();
                    return new DroolsConsequenceAction(dialect, consequence);
                }
            }
        }
        return new DroolsConsequenceAction(dialect, "");
    }

    protected void writeMetaData(final Node node, final StringBuilder xmlDump) {
        XmlBPMNProcessDumper.writeMetaData(getMetaData(node), xmlDump);
    }

    protected Map<String, Object> getMetaData(Node node) {
        return XmlBPMNProcessDumper.getMetaData(node.getMetaData());
    }

    protected void writeExtensionElements(Node node, final StringBuilder xmlDump) {
        if (containsExtensionElements(node)) {
            xmlDump.append("      <extensionElements>" + EOL);
            if (node instanceof ExtendedNodeImpl) {
                writeScripts("onEntry", ((ExtendedNodeImpl) node).getActions("onEntry"), xmlDump);
                writeScripts("onExit", ((ExtendedNodeImpl) node).getActions("onExit"), xmlDump);
            }
            writeMetaData(node, xmlDump);
            xmlDump.append("      </extensionElements>" + EOL);
        }
    }

    protected boolean containsExtensionElements(Node node) {
        if (!getMetaData(node).isEmpty()) {
            return true;
        }
        if (node instanceof ExtendedNodeImpl && ((ExtendedNodeImpl) node).containsActions()) {
            return true;
        }
        return false;
    }

    protected void writeScripts(final String type, List<DroolsAction> actions, final StringBuilder xmlDump) {
        if (!actions.isEmpty()) {
            for (DroolsAction action : actions) {
                writeScript(action, type, xmlDump);
            }
        }
    }

    public static void writeScript(final DroolsAction action, String type, final StringBuilder xmlDump) {
        if (action instanceof DroolsConsequenceAction) {
            DroolsConsequenceAction consequenceAction = (DroolsConsequenceAction) action;
            xmlDump.append("        <tns:" + type + "-script");
            String name = consequenceAction.getName();
            if (name != null) {
                xmlDump.append(" name=\"" + name + "\"");
            }
            String dialect = consequenceAction.getDialect();
            if (JavaDialect.ID.equals(dialect)) {
                xmlDump.append(" scriptFormat=\"" + XmlBPMNProcessDumper.JAVA_LANGUAGE + "\"");
            }
            String consequence = consequenceAction.getConsequence();
            if (consequence != null) {
                xmlDump.append(">" + EOL +
                        "          <tns:script>" + XmlDumper.replaceIllegalChars(consequence.trim()) + "</tns:script>" + EOL);
                xmlDump.append("        </tns:" + type + "-script>" + EOL);
            } else {
                xmlDump.append("/>" + EOL);
            }
        } else {
            throw new ProcessParsingValidationException(
                    "Unknown action " + action);
        }
    }

    protected void setCatchVariable(IOSpecification ioSpecification, Node node) {
        NodeImpl nodeImpl = (NodeImpl) node;
        nodeImpl.setIoSpecification(ioSpecification);
        if (node instanceof EventNode) {
            EventNode eventNode = (EventNode) node;
            findSourceMappingVar(ioSpecification.getDataOutputAssociation()).ifPresent(var -> {
                eventNode.setInputVariableName(var.getLabel());
            });
            findTargetMappingVar(ioSpecification.getDataOutputAssociation()).ifPresent(var -> {
                eventNode.getMetaData().put(MAPPING_VARIABLE, var.getLabel());
                eventNode.setVariableName(var.getLabel());
            });
        } else if (node instanceof TimerNode || node instanceof StateNode || node instanceof CatchLinkNode) {
            findTargetMappingVar(ioSpecification.getDataOutputAssociation()).ifPresent(data -> {
                nodeImpl.getMetaData().put(MAPPING_VARIABLE, data.getLabel());
            });
        }
    }

    protected void setThrowVariable(IOSpecification ioSpecification, Node node) {
        ((NodeImpl) node).setIoSpecification(ioSpecification);
        if (node instanceof ActionNode || node instanceof FaultNode || node instanceof EndNode) {
            NodeImpl mapping = (NodeImpl) node;
            findSourceMappingVar(ioSpecification.getDataInputAssociation()).ifPresent(data -> {
                if (!data.hasExpression()) {
                    mapping.getMetaData().put(MAPPING_VARIABLE, data.getLabel());
                    mapping.getMetaData().put(VARIABLE, data.getLabel());
                } else {
                    mapping.getMetaData().put(VARIABLE, data.getExpression());
                }
            });
            findTargetMappingVar(ioSpecification.getDataInputAssociation()).ifPresent(data -> {
                mapping.getMetaData().put(MAPPING_VARIABLE_INPUT, data.getLabel());
            });
        }
    }

    /*
     * This parts introduces the input/output parsing
     */

    protected Optional<DataDefinition> findTargetMappingVar(List<DataAssociation> outputs) {
        if (outputs.isEmpty()) {
            return Optional.empty();
        }

        if (outputs.get(0).getTarget() != null) {
            return Optional.of(outputs.get(0).getTarget());
        }
        return Optional.empty();
    }

    protected Optional<DataDefinition> findSourceMappingVar(List<DataAssociation> inputs) {
        if (inputs.isEmpty()) {
            return Optional.empty();
        }

        if (inputs.get(0).getAssignments().isEmpty()) {
            return Optional.of(inputs.get(0).getSources().get(0));
        } else {
            return Optional.of(inputs.get(0).getAssignments().get(0).getFrom());
        }

    }

    protected DataDefinition getVariableDataSpec(Parser parser, String propertyIdRef) {
        RuleFlowProcess process = (RuleFlowProcess) ((ProcessBuildData) parser.getData()).getMetaData(ProcessHandler.CURRENT_PROCESS);
        Optional<Variable> var = process.getVariableScope().getVariables().stream().filter(e -> e.getId().equals(propertyIdRef)).findAny();
        if (var.isEmpty()) {
            return null;
        }
        Variable variable = var.get();
        return new DataDefinition(variable.getId(), variable.getName(), variable);
    }

    protected ItemDefinition getStructureRef(Parser parser, String id) {
        ProcessBuildData buildData = (ProcessBuildData) parser.getData();
        Map<String, ItemDefinition> itemDefinitions = (Map<String, ItemDefinition>) buildData.getMetaData("ItemDefinitions");
        return itemDefinitions.get(id);
    }

    protected IOSpecification readCatchSpecification(Parser parser, Element element) {
        IOSpecification ioSpec = new IOSpecification();
        ioSpec.getDataOutputs().addAll(readDataOutput(parser, element));
        org.w3c.dom.Node xmlNode = element.getFirstChild();
        while (xmlNode != null) {
            String nodeName = xmlNode.getNodeName();
            if ("dataOutputAssociation".equals(nodeName)) {
                readDataAssociation((Element) xmlNode, id -> ioSpec.getDataOutput().get(id), id -> getVariableDataSpec(parser, id)).ifPresent(e -> {
                    e.setType(DataAssociationType.OUTPUT);
                    ioSpec.getDataOutputAssociation().add(e);
                });
            }
            xmlNode = xmlNode.getNextSibling();
        }

        return ioSpec;
    }

    protected IOSpecification readThrowSpecification(Parser parser, Element element) {
        IOSpecification ioSpec = new IOSpecification();
        ioSpec.getDataInputs().addAll(readDataInput(parser, element));
        org.w3c.dom.Node xmlNode = element.getFirstChild();
        while (xmlNode != null) {
            String nodeName = xmlNode.getNodeName();
            if ("dataInputAssociation".equals(nodeName)) {
                readDataAssociation((Element) xmlNode, id -> getVariableDataSpec(parser, id), id -> ioSpec.getDataInput().get(id)).ifPresent(e -> {
                    e.setType(DataAssociationType.INPUT);
                    ioSpec.getDataInputAssociation().add(e);
                });
            }
            xmlNode = xmlNode.getNextSibling();
        }
        return ioSpec;
    }

    protected IOSpecification readIOEspecification(Parser parser, Element element) {
        org.w3c.dom.Node xmlNode = element.getFirstChild();
        IOSpecification ioSpec = new IOSpecification();
        while (xmlNode != null) {

            String nodeName = xmlNode.getNodeName();
            if ("ioSpecification".equals(nodeName)) {
                ioSpec.getDataInputs().addAll(readDataInput(parser, xmlNode));
                ioSpec.getDataOutputs().addAll(readDataOutput(parser, xmlNode));
            } else if ("dataInputAssociation".equals(nodeName)) {
                readDataAssociation((Element) xmlNode, id -> getVariableDataSpec(parser, id), id -> ioSpec.getDataInput().get(id)).ifPresent(e -> {
                    e.setType(DataAssociationType.INPUT);
                    ioSpec.getDataInputAssociation().add(e);
                });
            } else if ("dataOutputAssociation".equals(nodeName)) {
                readDataAssociation((Element) xmlNode, id -> ioSpec.getDataOutput().get(id), id -> getVariableDataSpec(parser, id)).ifPresent(e -> {
                    e.setType(DataAssociationType.OUTPUT);
                    ioSpec.getDataOutputAssociation().add(e);
                });
            }
            xmlNode = xmlNode.getNextSibling();
        }
        return ioSpec;
    }

    /*
     * given a parent node it reads all data inputs and creates the dataSpec input/output for that node
     */
    protected List<DataDefinition> readDataInput(Parser parser, org.w3c.dom.Node parent) {
        return readData(parser, parent, "dataInput");
    }

    protected List<DataDefinition> readDataOutput(Parser parser, org.w3c.dom.Node parent) {
        return readData(parser, parent, "dataOutput");
    }

    /*
     * this read an data definition (input or output depending on the tag)
     * dtype is used for backward compatibility
     */
    protected List<DataDefinition> readData(Parser parser, org.w3c.dom.Node parent, String tag) {
        List<DataDefinition> dataSet = new ArrayList<>();
        readChildrenElementsByTag(parent, tag).forEach(element -> {
            String id = element.getAttribute("id");
            String label = element.getAttribute("name");
            String type = null;
            String typeRef = element.getAttribute("itemSubjectRef");
            if (typeRef.isEmpty()) {
                logger.debug("{} with id {} is not pointing out to a itemSubjectRef", tag, id);
                type = element.getAttribute("dtype");
                type = type.isEmpty() ? null : type;
                if (type != null) {
                    logger.debug("{} with id {} is using an old dtype. Please use itemSubjectRef", tag, id);
                }
            } else if (getStructureRef(parser, typeRef) != null) {
                type = getStructureRef(parser, typeRef).getStructureRef();
            }
            if (type == null) {
                type = "java.lang.Object";
                logger.debug("{} with id {} is not pointing out to a valid itemSubjectRef. falling back to {}", tag, id, type);
            }
            dataSet.add(new DataDefinition(id, label, type));
        });
        return dataSet;
    }

    protected List<Element> readChildrenElementsByTag(org.w3c.dom.Node parent, String tag) {
        List<Element> elements = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int idx = 0; idx < children.getLength(); idx++) {
            org.w3c.dom.Node currentNode = children.item(idx);
            if (!(currentNode instanceof Element) || !tag.equals(((Element) currentNode).getNodeName())) {
                continue;
            }
            elements.add((Element) currentNode);
        }
        return elements;
    }

    protected Optional<Element> readSingleChildElementByTag(org.w3c.dom.Node parent, String tag) {
        List<Element> elements = readChildrenElementsByTag(parent, tag);
        return !elements.isEmpty() ? Optional.of(elements.get(0)) : Optional.empty();
    }

    protected Optional<DataAssociation> readDataAssociation(Element element, Function<String, DataDefinition> sourceResolver,
            Function<String, DataDefinition> targetResolver) {
        List<DataDefinition> sources = readSources(element, sourceResolver);
        DataDefinition target = readTarget(element, targetResolver);
        List<Assignment> assignments = readAssignments(element,
                src -> {
                    if (".".equals(src)) {
                        return sources.get(0);
                    }
                    return sourceResolver.apply(src);
                },
                dst -> {
                    if (".".equals(dst)) {
                        return target;
                    }
                    return targetResolver.apply(dst);
                });
        Transformation transformation = readTransformation(element);
        DataAssociation da = new DataAssociation(sources, target, assignments, transformation);
        if (da.getTarget() != null && da.getSources().isEmpty() && da.getAssignments().isEmpty()) {
            // incomplete description we ignore it
            logger.debug("Read incomplete data association, will be ignored\n{}", da);
            return Optional.empty();
        }

        return Optional.of(da);
    }

    private Transformation readTransformation(Element parent) {
        Optional<Element> element = readSingleChildElementByTag(parent, "transformation");
        if (element.isEmpty()) {
            return null;
        }
        String lang = element.get().getAttribute("language");
        String expression = element.get().getTextContent();

        ReturnValueEvaluator evaluator = null;
        if (lang.toLowerCase().contains("mvel")) {
            evaluator = new MVELInterpretedReturnValueEvaluator(expression);
        }
        return new Transformation(lang, expression, evaluator);
    }

    protected List<DataDefinition> readSources(org.w3c.dom.Node parent, Function<String, DataDefinition> variableResolver) {
        List<DataDefinition> sources = new ArrayList<>();
        readChildrenElementsByTag(parent, "sourceRef").forEach(element -> {
            String varRef = element.getTextContent().trim();
            DataDefinition varResolved = variableResolver.apply(varRef);
            sources.add(varResolved != null ? varResolved : DataDefinition.toSimpleDefinition(varRef));
        });
        return sources;
    }

    protected DataDefinition readTarget(org.w3c.dom.Node parent, Function<String, DataDefinition> variableResolver) {
        Optional<Element> element = readSingleChildElementByTag(parent, "targetRef");
        if (element.isEmpty()) {
            return null;
        } else {
            String varRef = element.get().getTextContent().trim();
            DataDefinition varResolved = variableResolver.apply(varRef);
            return varResolved != null ? varResolved : DataDefinition.toSimpleDefinition(varRef);
        }
    }

    private List<Assignment> readAssignments(Element parent, Function<String, DataDefinition> sourceResolver, Function<String, DataDefinition> targetResolver) {
        List<Assignment> assignments = new ArrayList<>();
        readChildrenElementsByTag(parent, "assignment").forEach(element -> {
            Optional<Element> from = readSingleChildElementByTag(element, "from");
            Optional<Element> to = readSingleChildElementByTag(element, "to");
            String language = element.getAttribute("expressionLanguage");
            if (language == null || language.isEmpty()) {
                language = element.getAttribute("language");
            }
            String source = from.get().getTextContent();
            String sourceId = from.get().getAttribute("id");
            String target = to.get().getTextContent();
            String targetId = to.get().getAttribute("id");
            if (!language.isEmpty()) {
                assignments.add(new Assignment(language, toDataExpression(sourceId, source), toDataExpression(targetId, target)));
            } else {
                source = cleanUp(source);
                target = cleanUp(target);
                DataDefinition sourceDataSpec = isExpr(source) ? toDataExpression(sourceId, source) : sourceResolver.apply(source);
                if (sourceDataSpec == null) {
                    sourceDataSpec = toDataExpression(sourceId, source); // it is constant source
                }
                DataDefinition targetDataSpec = isExpr(target) ? toDataExpression(targetId, target) : targetResolver.apply(target);
                if (targetDataSpec == null) {
                    targetDataSpec = toDataExpression(targetId, target);
                }
                logger.debug("No language set for assignment {} to {}. Applying heuristics", sourceDataSpec, targetDataSpec);
                assignments.add(new Assignment(language.isEmpty() ? null : language, sourceDataSpec, targetDataSpec));
            }
        });
        return assignments;
    }

    /**
     * Simplifies variable expression in order to improve performance
     *
     * If the expression contains just once variable, we can skip MVEL expression evaluation
     * 
     * @param expression MVEL expression to evaluate
     * @return Variable name if expression evaluation is not needed, original expression otherwise
     */
    private String cleanUp(String expression) {
        Matcher matcher = PatternConstants.SINGLE_PARAMETER_MATCHER.matcher(expression);
        if (matcher.matches()) {
            if (!matcher.group(1).contains(".")) {
                return matcher.group(1);
            }
        }

        return expression;
    }

    private DataDefinition toDataExpression(String id, String expression) {
        DataDefinition dataSpec = new DataDefinition(id, "EXPRESSION (" + expression + ")", (String) null);
        dataSpec.setExpression(expression);
        return dataSpec;
    }

    private boolean isExpr(String mvelExpression) {
        return mvelExpression != null && mvelExpression.contains("#{");
    }

    protected NodeImpl decorateMultiInstanceSpecificationSubProcess(CompositeContextNode nodeTarget, MultiInstanceSpecification multiInstanceSpecification) {
        ForEachNode forEachNode = decorateMultiInstanceSpecification(nodeTarget, multiInstanceSpecification);
        forEachNode.setMetaData(ProcessHandler.CONNECTIONS, nodeTarget.getMetaData(ProcessHandler.CONNECTIONS));
        forEachNode.setAutoComplete(nodeTarget.isAutoComplete());
        // nodeTarget/subprocess is invalidated by this. we get all the content and added to the for each nodes 
        // within the composite
        for (org.kie.api.definition.process.Node subNode : nodeTarget.getNodes()) {
            forEachNode.addNode(subNode);
        }

        // this is the context of each subprocess
        VariableScope subProcessVariableScope = ((VariableScope) forEachNode.getCompositeNode().getDefaultContext(VariableScope.VARIABLE_SCOPE));

        // we setup the property/data objects of the subprocess to the foreach scope of the subprocess
        // the subprocess itself scope has no effect as nodes were included in the new scope (not the old one. Look
        // at the previous for each.
        VariableScope oldSubProcessVariables = (VariableScope) nodeTarget.getDefaultContext(VariableScope.VARIABLE_SCOPE);
        oldSubProcessVariables.getVariables().forEach(subProcessVariableScope::addVariable);

        // item is the element within the collection so Collection (E1,E2....En) -> Subcontext 1 (item - E1), Subcontext 2 (item - E2)
        DataDefinition inputItem = multiInstanceSpecification.getInputDataItem();
        if (inputItem != null) {
            Variable var = new Variable();
            var.setId(inputItem.getId());
            var.setName(inputItem.getLabel());
            var.setType(DataTypeResolver.fromType(inputItem.getType(), Thread.currentThread().getContextClassLoader()));
            subProcessVariableScope.addVariable(var);
        }

        return forEachNode;
    }

    protected NodeImpl decorateMultiInstanceSpecificationActivity(NodeImpl nodeTarget, MultiInstanceSpecification multiInstanceSpecification) {
        ForEachNode forEachNode = decorateMultiInstanceSpecification(nodeTarget, multiInstanceSpecification);
        String uniqueId = nodeTarget.getUniqueId();
        nodeTarget.setId(WorkflowElementIdentifierFactory.fromExternalFormat(uniqueId + "_1"));
        forEachNode.addNode(nodeTarget);
        forEachNode.linkIncomingConnections(Node.CONNECTION_DEFAULT_TYPE, nodeTarget.getId(), Node.CONNECTION_DEFAULT_TYPE);
        forEachNode.linkOutgoingConnections(nodeTarget.getId(), Node.CONNECTION_DEFAULT_TYPE, Node.CONNECTION_DEFAULT_TYPE);
        return forEachNode;
    }

    protected ForEachNode decorateMultiInstanceSpecification(NodeImpl nodeTarget, MultiInstanceSpecification multiInstanceSpecification) {
        ForEachNode forEachNode = new ForEachNode(nodeTarget.getId());
        forEachNode.setName(nodeTarget.getName());
        nodeTarget.setMetaData("hidden", true);
        forEachNode.setIoSpecification(nodeTarget.getIoSpecification());

        DataDefinition dataInput = multiInstanceSpecification.getInputDataItem();
        DataDefinition dataOutput = multiInstanceSpecification.getOutputDataItem();
        if (dataInput != null) {
            forEachNode.setInputRef(dataInput.getLabel());
            forEachNode.addContextVariable(dataInput.getId(), dataInput.getLabel(), fromType(dataInput.getType(), currentThread().getContextClassLoader()));
            forEachNode.getIoSpecification().getDataInputAssociation().stream().filter(e -> !e.getSources().isEmpty() && e.getSources().get(0).getId().equals(dataInput.getId())).forEach(da -> {
                da.getSources().clear();
                da.getSources().add(dataInput);
            });
        }
        if (dataOutput != null) {
            forEachNode.setOutputRef(dataOutput.getLabel());
            forEachNode.addContextVariable(dataOutput.getId(), dataOutput.getLabel(), fromType(dataOutput.getType(), currentThread().getContextClassLoader()));

            forEachNode.getIoSpecification().getDataOutputAssociation().stream().filter(e -> e.getTarget().getId().equals(dataOutput.getId())).forEach(da -> {
                da.setTarget(dataOutput);
            });
        }

        if (multiInstanceSpecification.hasLoopDataInputRef()) {
            DataDefinition dataInputRef = multiInstanceSpecification.getLoopDataInputRef();
            // inputs and outputs are still processes so we need to get rid of the input of belonging to the 
            // loop
            nodeTarget.getMetaData().put("MICollectionInput", dataInputRef.getLabel());

            // this is a correction as the input collection is the source of the expr (target)
            // so target is the input collection of the node
            // so we look in the source of the data input a target is equal to the data input getting the source we get the source
            // collection at context level (subprocess or activity)
            forEachNode.getIoSpecification().getDataInputAssociation().stream().filter(e -> e.getTarget().getId().equals(dataInputRef.getId())).findAny().ifPresent(pVar -> {
                String expr = pVar.getSources().get(0).getLabel();
                forEachNode.setCollectionExpression(expr);
            });
        }

        if (multiInstanceSpecification.hasLoopDataOutputRef()) {
            // same correction as input
            // we determine the output ref and locate the source. if set the target we get the variable at that level.
            DataDefinition dataOutputRef = multiInstanceSpecification.getLoopDataOutputRef();
            nodeTarget.getMetaData().put("MICollectionOutput", dataOutputRef.getLabel());
            forEachNode.getIoSpecification().getDataOutputAssociation().stream().filter(e -> e.getSources().get(0).getId().equals(dataOutputRef.getId())).findAny().ifPresent(e -> {
                forEachNode.setOutputCollectionExpression(e.getTarget().getLabel());
            });

            // another correction colletion output is not being stored in the composite context multiinstance
            // we use foreach_output
            Iterator<DataAssociation> iterator = forEachNode.getIoSpecification().getDataOutputAssociation().iterator();
            while (iterator.hasNext()) {
                DataAssociation current = iterator.next();
                if (!current.getSources().isEmpty() && current.getSources().get(0).equals(dataOutputRef)) {
                    iterator.remove();
                }
            }
        }
        // this is just an expression
        ReturnValueEvaluator evaluator = null;
        String completionConditionLang = multiInstanceSpecification.getCompletionConditionLang();
        if ((completionConditionLang == null || completionConditionLang.isBlank() || completionConditionLang.toLowerCase().contains("mvel"))
                && multiInstanceSpecification.getCompletionCondition() != null) {
            evaluator = new MVELInterpretedReturnValueEvaluator(multiInstanceSpecification.getCompletionCondition());
        }
        forEachNode.setCompletionConditionExpression(evaluator);
        forEachNode.setMultiInstanceSpecification(multiInstanceSpecification);

        // This variable is used for adding items computed by each subcontext.
        // after foreach is finished it will be moved to the data output ref collection of the multiinstance
        // this is the context of each subprocess
        VariableScope foreachContext = ((VariableScope) forEachNode.getCompositeNode().getDefaultContext(VariableScope.VARIABLE_SCOPE));
        Variable forEach = new Variable();
        forEach.setId("foreach_output");
        forEach.setName("foreach_output");
        forEach.setType(DataTypeResolver.fromType(Collection.class.getCanonicalName(), Thread.currentThread().getContextClassLoader()));
        foreachContext.addVariable(forEach);

        return forEachNode;

    }

    // this is only for compiling purposes
    protected MultiInstanceSpecification readMultiInstanceSpecification(Parser parser, org.w3c.dom.Node parent, IOSpecification ioSpecification) {
        MultiInstanceSpecification multiInstanceSpecification = new MultiInstanceSpecification();
        Optional<Element> multiInstanceParent = readSingleChildElementByTag(parent, "multiInstanceLoopCharacteristics");
        if (multiInstanceParent.isEmpty()) {
            return multiInstanceSpecification;
        }
        Element multiInstanceNode = multiInstanceParent.get();
        multiInstanceSpecification.setSequential(Boolean.parseBoolean(multiInstanceNode.getAttribute("isSequential")));
        readSingleChildElementByTag(multiInstanceNode, "inputDataItem").ifPresent(inputDataItem -> {
            String id = inputDataItem.getAttribute("id");
            String name = inputDataItem.getAttribute("name");
            String itemSubjectRef = inputDataItem.getAttribute("itemSubjectRef");
            ItemDefinition itemDefinition = getStructureRef(parser, itemSubjectRef);
            String structureRef = itemDefinition != null ? itemDefinition.getStructureRef() : null;
            DataDefinition input = new DataDefinition(id, name, structureRef);
            multiInstanceSpecification.setInputDataItem(input);
            if (!ioSpecification.containsInputLabel(input.getLabel())) {
                ioSpecification.getDataInputs().add(input);
            }
        });

        readSingleChildElementByTag(multiInstanceNode, "outputDataItem").ifPresent(outputDataItem -> {
            String id = outputDataItem.getAttribute("id");
            String name = outputDataItem.getAttribute("name");
            String itemSubjectRef = outputDataItem.getAttribute("itemSubjectRef");
            ItemDefinition itemDefinition = getStructureRef(parser, itemSubjectRef);
            String structureRef = itemDefinition != null ? itemDefinition.getStructureRef() : null;
            DataDefinition output = new DataDefinition(id, name, structureRef);
            multiInstanceSpecification.setOutputDataItem(output);
            if (!ioSpecification.containsOutputLabel(output.getLabel())) {
                ioSpecification.getDataOutputs().add(output);
            }
        });

        readSingleChildElementByTag(multiInstanceNode, "loopDataOutputRef").ifPresent(loopDataOutputRef -> {
            String expressiontOutput = loopDataOutputRef.getTextContent();
            if (expressiontOutput != null && !expressiontOutput.isEmpty()) {
                multiInstanceSpecification.setLoopDataOutputRef(ioSpecification.getDataOutput().get(expressiontOutput));
            }
        });

        readSingleChildElementByTag(multiInstanceNode, "loopDataInputRef").ifPresent(loopDataInputRef -> {
            String expressionInput = loopDataInputRef.getTextContent();
            if (expressionInput != null && !expressionInput.isEmpty()) {
                multiInstanceSpecification.setLoopDataInputRef(ioSpecification.getDataInput().get(expressionInput));
            }
        });

        readSingleChildElementByTag(multiInstanceNode, COMPLETION_CONDITION).ifPresent(completeCondition -> {
            String completion = completeCondition.getTextContent();
            if (completion != null && !completion.isEmpty()) {
                multiInstanceSpecification.setCompletionCondition(completion);
                multiInstanceSpecification.setCompletionConditionLang(completeCondition.getAttribute("language"));
            }
        });
        return multiInstanceSpecification;
    }

    protected String getErrorIdForErrorCode(String errorCode, Node node) {
        org.kie.api.definition.process.NodeContainer parent = node.getParentContainer();
        while (!(parent instanceof RuleFlowProcess) && parent instanceof Node) {
            parent = ((Node) parent).getParentContainer();
        }
        if (!(parent instanceof RuleFlowProcess)) {
            throw new RuntimeException("This should never happen: !(parent instanceof RuleFlowProcess): parent is " + parent.getClass().getSimpleName());
        }
        List<Error> errors = ((Definitions) ((RuleFlowProcess) parent).getMetaData("Definitions")).getErrors();
        Error error = null;
        for (Error listError : errors) {
            if (errorCode.equals(listError.getErrorCode())) {
                error = listError;
                break;
            } else if (errorCode.equals(listError.getId())) {
                error = listError;
                break;
            }
        }
        if (error == null) {
            throw new ProcessParsingValidationException("Could not find error with errorCode " + errorCode);
        }
        return error.getId();
    }

    protected void handleThrowCompensationEventNode(final Node node, final Element element,
            final String uri, final String localName, final Parser parser) {
        org.w3c.dom.Node xmlNode = element.getFirstChild();
        if (!(node instanceof ActionNode || node instanceof EndNode)) {
            throw new IllegalArgumentException("Node is neither an ActionNode nor an EndNode but a " + node.getClass().getSimpleName());
        }
        while (xmlNode != null) {
            if ("compensateEventDefinition".equals(xmlNode.getNodeName())) {
                String activityRef = ((Element) xmlNode).getAttribute("activityRef");
                if (activityRef == null) {
                    activityRef = "";
                }
                node.setMetaData("compensation-activityRef", activityRef);

                /**
                 * waitForCompletion:
                 * BPMN 2.0 Spec, p. 304:
                 * "By default, compensation is triggered synchronously, that is the compensation throw event
                 * waits for the completion of the triggered compensation handler.
                 * Alternatively, compensation can be triggered without waiting for its completion,
                 * by setting the throw compensation event's waitForCompletion attribute to false."
                 */
                String nodeId = (String) node.getUniqueId();
                String waitForCompletionString = ((Element) xmlNode).getAttribute("waitForCompletion");
                boolean waitForCompletion = true;
                if (waitForCompletionString != null && waitForCompletionString.length() > 0) {
                    waitForCompletion = Boolean.parseBoolean(waitForCompletionString);
                }
                if (!waitForCompletion) {
                    throw new ProcessParsingValidationException("Asynchronous compensation [" + nodeId + ", " + node.getName()
                            + "] is not yet supported!");
                }

            }
            xmlNode = xmlNode.getNextSibling();
        }
    }

    protected void writeThrow(IOSpecification ioSpecification, StringBuilder xmlDump) {
        for (DataDefinition input : ioSpecification.getDataInput().values()) {
            xmlDump.append("        <dataInput id=\"" + input.getId() + "\" name=\"" + input.getLabel() + "\" />" + EOL);
        }
        for (DataAssociation input : ioSpecification.getDataInputAssociation()) {
            xmlDump.append("      <dataInputAssociation>" + EOL);
            writeDataAssociation(input, xmlDump);
            xmlDump.append("      </dataInputAssociation>" + EOL);
        }
    }

    protected void writeCatchIO(IOSpecification ioSpecification, StringBuilder xmlDump) {
        for (DataDefinition output : ioSpecification.getDataOutput().values()) {
            xmlDump.append("        <dataOutput id=\"" + output.getId() + "\" name=\"" + output.getLabel() + "\" />" + EOL);
        }
        for (DataAssociation output : ioSpecification.getDataOutputAssociation()) {
            xmlDump.append("      <dataOutputAssociation>" + EOL);
            writeDataAssociation(output, xmlDump);
            xmlDump.append("      </dataOutputAssociation>" + EOL);
        }
    }

    protected void writeIO(IOSpecification ioSpecification, StringBuilder xmlDump) {
        xmlDump.append("      <ioSpecification>" + EOL);

        for (DataDefinition input : ioSpecification.getDataInput().values()) {
            xmlDump.append("        <dataInput id=\"" + input.getId() + "\" name=\"" + input.getLabel() + "\" />" + EOL);
        }

        for (DataDefinition output : ioSpecification.getDataOutput().values()) {
            xmlDump.append("        <dataOutput id=\"" + output.getId() + "\" name=\"" + output.getLabel() + "\" />" + EOL);
        }

        for (DataAssociation input : ioSpecification.getDataInputAssociation()) {
            xmlDump.append("      <dataInputAssociation>" + EOL);
            writeDataAssociation(input, xmlDump);
            xmlDump.append("      </dataInputAssociation>" + EOL);
        }
        for (DataAssociation output : ioSpecification.getDataOutputAssociation()) {
            xmlDump.append("      <dataOutputAssociation>" + EOL);
            writeDataAssociation(output, xmlDump);
            xmlDump.append("      </dataOutputAssociation>" + EOL);
        }
        xmlDump.append("      </ioSpecification>" + EOL);
    }

    protected void writeDataAssociation(DataAssociation input, StringBuilder xmlDump) {
        for (DataDefinition source : input.getSources()) {
            xmlDump.append("        <sourceRef>" + source.getId() + "</sourceRef>" + EOL);
        }
        if (input.getTarget() != null) {
            xmlDump.append("<targetRef>" + input.getTarget().getId() + "</targetRef>" + EOL);
        }
        if (!input.getAssignments().isEmpty()) {
            for (Assignment assignment : input.getAssignments()) {
                xmlDump.append("        <assignment>" + EOL);
                xmlDump.append("             <from xsi:type=\"tFormalExpression\">" + assignment.getFrom().getExpression() + "</from>");
                xmlDump.append("             <to xsi:type=\"tFormalExpression\">" + assignment.getTo().getExpression() + "</to>");
                xmlDump.append("        </assignment>" + EOL);
            }
        }
    }

    protected void writeMultiInstance(MultiInstanceSpecification ioSpecification, StringBuilder xmlDump) {
        if (!ioSpecification.hasLoopDataInputRef()) {
            return;
        }

        xmlDump.append("<multiInstanceLoopCharacteristics>" + EOL);

        if (ioSpecification.hasLoopDataInputRef()) {
            xmlDump.append("<loopDataInputRef>" + ioSpecification.getLoopDataInputRef() + "</loopDataInputRef>" + EOL);
        }

        if (ioSpecification.hasLoopDataOutputRef()) {
            xmlDump.append("<loopDataOutputRef>" + ioSpecification.getLoopDataOutputRef() + "</loopDataOutputRef>" + EOL);
        }

        DataDefinition input = ioSpecification.getInputDataItem();
        if (input != null) {
            xmlDump.append("<inputDataItem id=\"" + input.getId() + "\" name=\"" + input.getLabel() + "\" structureRef=\"" + input.getType() + "\" />");
        }

        DataDefinition output = ioSpecification.getOutputDataItem();
        if (output != null) {
            xmlDump.append("<outputDataItem id=\"" + output.getId() + "\" name=\"" + output.getLabel() + "\" structureRef=\"" + output.getType() + "\" />");
        }

        xmlDump.append("</multiInstanceLoopCharacteristics>" + EOL);
    }

    private static final String SIGNAL_NAMES = "signalNames";

    protected String checkSignalAndConvertToRealSignalNam(Parser parser, String signalName) {

        Signal signal = findSignalByName(parser, signalName);
        if (signal != null) {
            signalName = signal.getName();
            if (signalName == null) {
                throw new ProcessParsingValidationException("Signal definition must have a name attribute");
            }
        }

        return signalName;
    }

    protected Signal findSignalByName(Parser parser, String signalName) {
        ProcessBuildData buildData = ((ProcessBuildData) parser.getData());

        Set<String> signalNames = (Set<String>) buildData.getMetaData(SIGNAL_NAMES);
        if (signalNames == null) {
            signalNames = new HashSet<>();
            buildData.setMetaData(SIGNAL_NAMES, signalNames);
        }
        signalNames.add(signalName);

        Map<String, Signal> signals = (Map<String, Signal>) buildData.getMetaData("Signals");
        if (signals != null) {
            return signals.get(signalName);
        }

        return null;
    }

    protected String retrieveDataType(String itemSubjectRef, String dtype, Parser parser) {
        if (dtype != null && !dtype.isEmpty()) {
            return dtype;
        }

        if (itemSubjectRef != null && !itemSubjectRef.isEmpty()) {
            Map<String, ItemDefinition> itemDefinitions = (Map<String, ItemDefinition>) ((ProcessBuildData) parser.getData()).getMetaData("ItemDefinitions");

            return itemDefinitions.get(itemSubjectRef).getStructureRef();
        }

        return null;
    }

    /**
     * Finds the right variable by its name to make sure that when given as id it will be also matched
     *
     * @param variableName name or id of the variable
     * @param parser parser instance
     * @return returns found variable name or given 'variableName' otherwise
     */
    protected String findVariable(String variableName, final Parser parser) {
        if (variableName == null) {
            return null;
        }
        Collection<?> parents = ((ExtensibleXmlParser) parser).getParents();

        for (Object parent : parents) {
            if (parent instanceof ContextContainer) {
                ContextContainer contextContainer = (ContextContainer) parent;
                VariableScope variableScope = (VariableScope) contextContainer.getDefaultContext(VariableScope.VARIABLE_SCOPE);
                return variableScope.getVariables().stream().filter(v -> v.matchByIdOrName(variableName)).map(v -> v.getName()).findFirst().orElse(variableName);
            }
        }

        return variableName;
    }

}
