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
package org.kie.kogito.codegen.process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.drools.io.FileSystemResource;
import org.jbpm.process.core.timer.Timer;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.workflow.core.Constraint;
import org.jbpm.workflow.core.DroolsAction;
import org.jbpm.workflow.core.impl.ConnectionRef;
import org.jbpm.workflow.core.impl.DataAssociation;
import org.jbpm.workflow.core.impl.ExtendedNodeImpl;
import org.jbpm.workflow.core.impl.NodeImpl;
import org.jbpm.workflow.core.node.ActionNode;
import org.jbpm.workflow.core.node.Assignment;
import org.jbpm.workflow.core.node.BoundaryEventNode;
import org.jbpm.workflow.core.node.CompositeNode;
import org.jbpm.workflow.core.node.EndNode;
import org.jbpm.workflow.core.node.EventNode;
import org.jbpm.workflow.core.node.HumanTaskNode;
import org.jbpm.workflow.core.node.Join;
import org.jbpm.workflow.core.node.MilestoneNode;
import org.jbpm.workflow.core.node.RuleSetNode;
import org.jbpm.workflow.core.node.Split;
import org.jbpm.workflow.core.node.StartNode;
import org.jbpm.workflow.core.node.StateBasedNode;
import org.jbpm.workflow.core.node.Trigger;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.definition.process.Connection;
import org.kie.api.definition.process.Node;
import org.kie.kogito.Application;
import org.kie.kogito.Model;
import org.kie.kogito.codegen.AbstractCodegenIT;
import org.kie.kogito.codegen.api.io.CollectedResource;
import org.kie.kogito.internal.SupportedExtensions;
import org.kie.kogito.process.Processes;
import org.kie.kogito.process.impl.AbstractProcess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.jbpm.ruleflow.core.Metadata.ACTION;
import static org.jbpm.ruleflow.core.Metadata.TRIGGER_REF;
import static org.jbpm.workflow.core.Node.CONNECTION_DEFAULT_TYPE;
import static org.jbpm.workflow.core.impl.ExtendedNodeImpl.EVENT_NODE_ENTER;
import static org.jbpm.workflow.core.impl.ExtendedNodeImpl.EVENT_NODE_EXIT;
import static org.jbpm.workflow.instance.WorkflowProcessParameters.WORKFLOW_PARAM_TRANSACTIONS;

/**
 * ProcessGenerationTest iterates over all the process files in the project except the
 * ones listed in process-generation-test.skip.txt
 * <p>
 * For each process the test will:
 * <ul>
 * <li>Parse the XML and generate an instance of the process: Expected</li>
 * <li>Generate the code from this process and instantiate a process definition using the generated code: Current</li>
 * <li>Iterate over all the process fields and metadata and assert that current and expected are equivalent</li>
 * <li>Iterate over all the process' nodes fields and metadata and assert that current and expected are equivalent</li>
 * </ul>
 * <p>
 * Exceptions:
 * <ul>
 * <li>Version is not set by default in Expected</li>
 * <li>The node name has a default value for current when not set</li>
 * <li>Node constraints are ignored</li>
 * <li>OnEntry/OnExit actions are not yet implemented</li>
 * <li>Timer Actions are generated differently</li>
 * </ul>
 */
public class ProcessGenerationIT extends AbstractCodegenIT {

    private static final Collection<String> IGNORED_PROCESS_META =
            Arrays.asList("Definitions", "BPMN.Connections", "BPMN.Associations", "ItemDefinitions", WORKFLOW_PARAM_TRANSACTIONS.getName());
    private static final Path BASE_PATH = Paths.get("src/test/resources");

    static Stream<String> processesProvider() throws IOException {
        Set<String> ignoredFiles = Files.lines(BASE_PATH.resolve("org/kie/kogito/codegen/process/process-generation-test.skip.txt"))
                .filter(it -> {
                    try {
                        Path.of(it);
                        return true;
                    } catch (InvalidPathException ipe) {
                        return false;
                    }
                })
                .collect(Collectors.toSet());
        return Files.find(BASE_PATH, 10, ((path, basicFileAttributes) -> basicFileAttributes.isRegularFile()
                && SupportedExtensions.isSourceFile(path)))
                .map(BASE_PATH::relativize)
                .filter(p -> ignoredFiles.stream().noneMatch(ignored -> p.startsWith(ignored)))
                .map(Path::toString);
    }

    @ParameterizedTest
    @MethodSource("processesProvider")
    public void testProcessGeneration(String processFile) throws Exception {
        // for some tests this needs to be set to true
        System.setProperty("jbpm.enable.multi.con", "true");
        ProcessCodegen processCodeGen =
                ProcessCodegen.ofCollectedResources(newContext(), Collections.singletonList(new CollectedResource(BASE_PATH, new FileSystemResource(new File(BASE_PATH.toString(), processFile)))));
        RuleFlowProcess expected = (RuleFlowProcess) processCodeGen.processes().iterator().next();

        Application app = generateCodeProcessesOnly(processFile);
        AbstractProcess<? extends Model> process = (AbstractProcess<? extends Model>) app.get(Processes.class).processById(expected.getId());
        assertThat(process).isNotNull().isSameAs(app.get(Processes.class).processById(expected.getId()));

        RuleFlowProcess current = (RuleFlowProcess) process.get();

        assertThat(current).isNotNull();
        assertThat(current.getId()).as("Id").isEqualTo(expected.getId());
        assertThat(current.getName()).as("Name").isEqualTo(expected.getName());
        assertThat(current.getPackageName()).as("PackageName").isEqualTo(expected.getPackageName());
        assertThat(current.getVisibility()).as("Visibility").isEqualTo(expected.getVisibility());
        assertThat(current.getType()).as("Type").isEqualTo(expected.getType());
        assertThat(current.isAutoComplete()).as("AutoComplete").isEqualTo(expected.isAutoComplete());
        assertThat(current.isDynamic()).as("Dynamic").isEqualTo(expected.isDynamic());
        if (expected.getVersion() != null) {
            assertThat(current.getVersion()).isEqualTo(expected.getVersion());
        } else {
            assertThat(current.getVersion()).isEqualTo("1.0");
        }
        assertThat(current.getImports()).as("Imports").isEqualTo(expected.getImports());
        assertThat(current.getFunctionImports()).as("FunctionImports").isEqualTo(expected.getFunctionImports());
        assertMetadata(expected.getMetaData(), current.getMetaData(), IGNORED_PROCESS_META);

        assertNodes(expected.getNodes(), current.getNodes());
        System.clearProperty("jbpm.enable.multi.con");
    }

    @Test
    public void testInvalidProcess() throws Exception {
        try {
            testProcessGeneration("messageevent/EventNodeMalformed.bpmn2");
            fail("Expected ProcessCodegenException");
        } catch (ProcessCodegenException e) {
            assertThat(e).isNotNull();
        }
    }

    @Test
    public void testDifferentLinkProcess() throws Exception {
        Assertions.assertThatThrownBy(() -> testProcessGeneration("links/DifferentLinkProcess.bpmn2")).isInstanceOf(
                ProcessCodegenException.class);
    }

    @Test
    public void testMultipleCatchLink() throws Exception {
        Assertions.assertThatThrownBy(() -> testProcessGeneration("links/MultipleCatchLinkProcess.bpmn2")).isInstanceOf(
                ProcessCodegenException.class);
    }

    @Test
    public void testEmptyLinkProcess() throws Exception {
        Assertions.assertThatThrownBy(() -> testProcessGeneration("links/EmptyLinkProcess.bpmn2")).isInstanceOf(
                ProcessCodegenException.class);
    }

    @Test
    public void testMissingLinkProcess() throws Exception {
        Assertions.assertThatThrownBy(() -> testProcessGeneration("links/UnconnectedLinkProcess.bpmn2")).isInstanceOf(
                ProcessCodegenException.class);
    }

    private static void assertNodes(Node[] expected, Node[] current) {
        assertThat(current).hasSameSizeAs(expected);
        Stream.of(expected).forEach(eNode -> {
            Optional<Node> cNode = Stream.of(current).filter(c -> c.getId().equals(eNode.getId())).findFirst();
            assertThat(cNode).as("Missing node " + eNode.getName()).isPresent();
            assertNode(eNode, cNode.get());
        });
    }

    private static final BiConsumer<Node, Node> nodeAsserter = (expected, current) -> {
        assertThat(current.getId()).isEqualTo(expected.getId());
        if (!StringUtils.isBlank(expected.getName())) {
            assertThat(current.getName()).isEqualTo(expected.getName());
        } else {
            assertThat(current.getName()).as(current.getClass().getName()).isNotNull();
        }
        assertConnections(expected.getIncomingConnections(), current.getIncomingConnections());
        assertConnections(expected.getOutgoingConnections(), current.getOutgoingConnections());
        assertConstraints((NodeImpl) expected, (NodeImpl) current);
    };

    private static final BiConsumer<Node, Node> extendedNodeAsserter = (eNode, cNode) -> {
        assertThat(ExtendedNodeImpl.class.isAssignableFrom(eNode.getClass())).isTrue();
        assertThat(ExtendedNodeImpl.class.isAssignableFrom(cNode.getClass())).isTrue();
        ExtendedNodeImpl expected = (ExtendedNodeImpl) eNode;
        ExtendedNodeImpl current = (ExtendedNodeImpl) cNode;
        assertActions(eNode, expected, current);
    };

    // onEntry and onExit actions are not yet supported - KOGITO-1709
    private static void assertActions(Node eNode, ExtendedNodeImpl expected, ExtendedNodeImpl current) {
        for (String actionType : expected.getActionTypes()) {
            List<DroolsAction> expectedActions = expected.getActions(actionType);
            if (eNode instanceof EndNode && expected.getMetaData(TRIGGER_REF) != null) {
                // Generated lambda to publish event for the given variable
                if (expectedActions == null) {
                    expectedActions = new ArrayList<>();
                }
                expectedActions.add(new DroolsAction());
            }
            try {
                if (expected.getActions(actionType) == null) {
                    assertThat(current.getActions(actionType)).isNull();
                } else if (!EVENT_NODE_ENTER.equals(actionType) && !EVENT_NODE_EXIT.equals(actionType)) {
                    assertThat(current.getActions(actionType)).isNotNull();
                    // onEntry and onExit actions are not yet supported
                    //                    assertEquals(expected.getActions(actionType).size(), current.getActions(actionType).size());
                    //                    assertActions(expected.getActions(actionType), current.getActions(actionType));
                }
            } catch (Throwable e) {
                fail("Actions are not equal for type: " + actionType, e);
            }
        }
    }

    private static final BiConsumer<Node, Node> startNodeAsserter = (eNode, cNode) -> {
        assertThat(eNode.getClass()).isEqualTo(StartNode.class);
        assertThat(cNode.getClass()).isEqualTo(StartNode.class);
        StartNode expected = (StartNode) eNode;
        StartNode current = (StartNode) cNode;
        assertThat(current.isInterrupting()).as("Interrupting").isEqualTo(expected.isInterrupting());
        assertTriggers(expected.getTriggers(), current.getTriggers());
    };

    private static final BiConsumer<Node, Node> endNodeAsserter = (eNode, cNode) -> {
        assertThat(eNode.getClass()).isEqualTo(EndNode.class);
        assertThat(cNode.getClass()).isEqualTo(EndNode.class);
        EndNode expected = (EndNode) eNode;
        EndNode current = (EndNode) cNode;
        assertThat(current.isTerminate()).as("Terminate").isEqualTo(expected.isTerminate());
    };

    private static final BiConsumer<Node, Node> stateBasedNodeAsserter = (eNode, cNode) -> {
        assertThat(StateBasedNode.class.isAssignableFrom(eNode.getClass())).isTrue();
        assertThat(StateBasedNode.class.isAssignableFrom(cNode.getClass())).isTrue();
        StateBasedNode expected = (StateBasedNode) eNode;
        StateBasedNode current = (StateBasedNode) cNode;
        assertThat(current.getBoundaryEvents()).as("BoundaryEvents").isEqualTo(expected.getBoundaryEvents());
        assertTimers(expected.getTimers(), current.getTimers());
    };

    private static final BiConsumer<Node, Node> workItemNodeAsserter = (eNode, cNode) -> {
        assertThat(WorkItemNode.class.isAssignableFrom(eNode.getClass())).isTrue();
        assertThat(WorkItemNode.class.isAssignableFrom(cNode.getClass())).isTrue();
        WorkItemNode expected = (WorkItemNode) eNode;
        WorkItemNode current = (WorkItemNode) cNode;
        assertThat(current.isWaitForCompletion()).as("WaitForCompletion").isEqualTo(expected.isWaitForCompletion());
        assertThat(current.getInMappings()).as("inMappings").hasSameSizeAs(expected.getInMappings());
        expected.getInMappings().forEach((k, v) -> assertThat(current.getInMapping(k)).as("inMapping " + k).isEqualTo(v));
        assertThat(current.getOutMappings()).as("outMappings").hasSameSizeAs(expected.getOutMappings());
        expected.getOutMappings().forEach((k, v) -> assertThat(current.getOutMapping(k)).as("outMapping " + k).isEqualTo(v));

    };

    private static final BiConsumer<Node, Node> humanTaskNodeAsserter = (eNode, cNode) -> {
        assertThat(eNode.getClass()).isEqualTo(HumanTaskNode.class);
        assertThat(cNode.getClass()).isEqualTo(HumanTaskNode.class);
        HumanTaskNode expected = (HumanTaskNode) eNode;
        HumanTaskNode current = (HumanTaskNode) cNode;
        assertThat(current.getSwimlane()).as("Swimlane").isEqualTo(expected.getSwimlane());
    };

    private static final BiConsumer<Node, Node> eventNodeAsserter = (eNode, cNode) -> {
        assertThat(EventNode.class.isAssignableFrom(eNode.getClass())).isTrue();
        assertThat(EventNode.class.isAssignableFrom(cNode.getClass())).isTrue();
        EventNode expected = (EventNode) eNode;
        EventNode current = (EventNode) cNode;
        assertThat(current.getScope()).as("Scope").isEqualTo(expected.getScope());
        assertThat(current.getType()).as("Type").isEqualTo(expected.getType());
        assertThat(current.getVariableName()).as("VariableName").isEqualTo(expected.getVariableName());
        assertThat(current.getEventFilters()).as("EventFilters").hasSameSizeAs(expected.getEventFilters());
    };

    private static final BiConsumer<Node, Node> boundaryEventNodeAsserter = (eNode, cNode) -> {
        assertThat(eNode.getClass()).isEqualTo(BoundaryEventNode.class);
        assertThat(cNode.getClass()).isEqualTo(BoundaryEventNode.class);
        BoundaryEventNode expected = (BoundaryEventNode) eNode;
        BoundaryEventNode current = (BoundaryEventNode) cNode;
        assertThat(current.getAttachedToNodeId()).as("AttachedToNodeId").isEqualTo(expected.getAttachedToNodeId());
    };

    private static final BiConsumer<Node, Node> splitNodeAsserter = (eNode, cNode) -> {
        assertThat(eNode.getClass()).isEqualTo(Split.class);
        assertThat(cNode.getClass()).isEqualTo(Split.class);
        Split expected = (Split) eNode;
        Split current = (Split) cNode;
        assertThat(current.getType()).as("Type").isEqualTo(expected.getType());
    };

    private static final BiConsumer<Node, Node> joinNodeAsserter = (eNode, cNode) -> {
        assertThat(eNode.getClass()).isEqualTo(Join.class);
        assertThat(cNode.getClass()).isEqualTo(Join.class);
        Join expected = (Join) eNode;
        Join current = (Join) cNode;
        assertThat(current.getType()).as("Type").isEqualTo(expected.getType());
        assertThat(current.getN()).as("N").isEqualTo(expected.getN());
    };

    private static final BiConsumer<Node, Node> actionNodeAsserter = (eNode, cNode) -> {
        assertThat(eNode.getClass()).isEqualTo(ActionNode.class);
        assertThat(cNode.getClass()).isEqualTo(ActionNode.class);
        ActionNode expected = (ActionNode) eNode;
        ActionNode current = (ActionNode) cNode;
        if (expected.getAction() != null) {
            assertThat(current.getAction()).isNotNull();
            assertThat(current.getAction().getName()).as(ACTION).isEqualTo(expected.getAction().getName());
        }
    };

    private static final BiConsumer<Node, Node> milestoneNodeAsserter = (eNode, cNode) -> {
        assertThat(eNode.getClass()).isEqualTo(MilestoneNode.class);
        assertThat(cNode.getClass()).isEqualTo(MilestoneNode.class);
    };

    private static final BiConsumer<Node, Node> compositeNodeAsserter = (eNode, cNode) -> {
        assertThat(CompositeNode.class.isAssignableFrom(eNode.getClass())).isTrue();
        assertThat(CompositeNode.class.isAssignableFrom(cNode.getClass())).isTrue();
        CompositeNode expected = (CompositeNode) eNode;
        CompositeNode current = (CompositeNode) cNode;
        assertNodes(expected.getNodes(), current.getNodes());
    };

    private static final BiConsumer<Node, Node> ruleSetNodeAsserter = (eNode, cNode) -> {
        assertThat(eNode.getClass()).isEqualTo(RuleSetNode.class);
        assertThat(cNode.getClass()).isEqualTo(RuleSetNode.class);
        RuleSetNode expected = (RuleSetNode) eNode;
        RuleSetNode current = (RuleSetNode) cNode;
        expected.getInMappings()
                .forEach((k, eMapping) -> assertThat(current.getInMapping(k)).as("inMapping: " + k).isEqualTo(eMapping));
        expected.getOutMappings()
                .forEach((k, eMapping) -> assertThat(current.getOutMapping(k)).as("outMapping: " + k).isEqualTo(eMapping));
        expected.getParameters()
                .forEach((k, eParam) -> assertThat(current.getParameter(k)).as("parameter: " + k).isEqualTo(eParam));
    };

    private static final Map<Class<? extends Node>, BiConsumer<Node, Node>> nodeAsserters = new HashMap<>();

    static {
        nodeAsserters.put(NodeImpl.class, nodeAsserter);
        nodeAsserters.put(ExtendedNodeImpl.class, extendedNodeAsserter);
        nodeAsserters.put(StartNode.class, startNodeAsserter);
        nodeAsserters.put(EndNode.class, endNodeAsserter);
        nodeAsserters.put(Split.class, splitNodeAsserter);
        nodeAsserters.put(Join.class, joinNodeAsserter);
        nodeAsserters.put(StateBasedNode.class, stateBasedNodeAsserter);
        nodeAsserters.put(WorkItemNode.class, workItemNodeAsserter);
        nodeAsserters.put(HumanTaskNode.class, humanTaskNodeAsserter);
        nodeAsserters.put(EventNode.class, eventNodeAsserter);
        nodeAsserters.put(BoundaryEventNode.class, boundaryEventNodeAsserter);
        nodeAsserters.put(ActionNode.class, actionNodeAsserter);
        nodeAsserters.put(MilestoneNode.class, milestoneNodeAsserter);
        nodeAsserters.put(CompositeNode.class, compositeNodeAsserter);
        nodeAsserters.put(RuleSetNode.class, ruleSetNodeAsserter);
    }

    private static void assertNode(Node expected, Node current) {
        nodeAsserters.keySet()
                .stream()
                .filter(clazz -> clazz.isAssignableFrom(expected.getClass()))
                .forEach(clazz -> {
                    try {
                        nodeAsserters.get(clazz).accept(expected, current);
                    } catch (Throwable e) {
                        fail(String.format("[%s] nodes with name [%s] are not equal", expected.getClass().getSimpleName(), current.getName()), e);
                    }
                });
    }

    private static void assertMetadata(Map<String, Object> expected, Map<String, Object> current, Collection<String> ignoredKeys) {
        if (expected == null) {
            assertThat(current).isNull();
            return;
        }
        expected.remove("CorrelationSubscriptions");
        Predicate<String> precicateIgnoredKeys = Predicate.not(ignoredKeys::contains);

        List<String> currentKeys = current.keySet().stream().filter(precicateIgnoredKeys).toList();
        List<String> expectedKeys = expected.keySet().stream().filter(precicateIgnoredKeys).toList();
        assertThat(currentKeys).containsExactlyElementsOf(expectedKeys);

        expected.keySet()
                .stream()
                .filter(precicateIgnoredKeys)
                .forEach(k -> assertThat(current).as("Metadata " + k).containsEntry(k, expected.get(k)));
    }

    private static void assertConnections(Map<String, List<Connection>> expectedConnections, Map<String, List<Connection>> currentConnections) {
        assertThat(currentConnections).hasSameSizeAs(expectedConnections);
        expectedConnections.forEach((type, expectedByType) -> {
            assertThat(currentConnections).as("Node does not have connections of type: " + type).containsKey(type);
            List<Connection> currentByType = currentConnections.get(type);
            expectedByType.forEach(expected -> {
                Optional<Connection> current = currentByType
                        .stream()
                        .filter(c -> equalConnectionId(expected, c))
                        .findFirst();
                assertThat(current).as("Connection is present for " + expected.getMetaData().get("UniqueId")).isPresent();
                assertThat(current.get().getFromType()).as("FromType").isEqualTo(expected.getFromType());
                assertThat(current.get().getFrom().getId()).as("From.Id").isEqualTo(expected.getFrom().getId());
                assertThat(current.get().getToType()).as("ToType").isEqualTo(expected.getToType());
                assertThat(current.get().getTo().getId()).as("To.Id").isEqualTo(expected.getTo().getId());
            });
        });
    }

    private static boolean equalConnectionId(Connection expected, Connection current) {
        if (expected.getMetaData().isEmpty()) {
            return current.getMetaData().isEmpty();
        }
        String expectedId = expected.getUniqueId();
        if (expectedId == null) {
            expectedId = "";
        }
        return Objects.equals(expectedId, current.getUniqueId());
    }

    private static void assertTriggers(List<Trigger> expected, List<Trigger> current) {
        try {
            if (expected == null) {
                assertThat(current).isNull();
                return;
            }
            assertThat(current).hasSameSizeAs(expected);
            for (int i = 0; i < expected.size(); i++) {
                Trigger e = expected.get(i);
                Trigger c = current.get(i);
                e.getInMappings().forEach((k, v) -> assertThat(c.getInMapping(k)).as("InMapping for " + k).isEqualTo(v));
                assertDataAssociations(e.getInAssociations(), c.getInAssociations());
            }
        } catch (Throwable e) {
            fail("Triggers are not equal", e);
        }
    }

    private static void assertDataAssociations(List<DataAssociation> expected, List<DataAssociation> current) {
        if (expected == null) {
            assertThat(current).isNull();
            return;
        }
        if (expected.isEmpty()) {
            assertThat(current).isEmpty();
        } else {
            assertThat(current).hasSameSizeAs(expected);
            for (int i = 0; i < expected.size(); i++) {
                assertThat(current.get(i).getSources()).as("Sources").isEqualTo(expected.get(i).getSources());
                assertThat(current.get(i).getTarget()).as("Target").isEqualTo(expected.get(i).getTarget());
                assertThat(current.get(i).getTransformation()).as("Transformation").isEqualTo(expected.get(i).getTransformation());
                assertAssignments(expected.get(i).getAssignments(), current.get(i).getAssignments());
            }
        }
    }

    private static void assertAssignments(List<Assignment> expected, List<Assignment> current) {
        if (expected == null) {
            assertThat(current).isNull();
            return;
        }
        assertThat(current).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            assertThat(current.get(i).getFrom()).as("From").isEqualTo(expected.get(i).getFrom());
            assertThat(current.get(i).getDialect()).as("Dialect").isEqualTo(expected.get(i).getDialect());
            assertThat(current.get(i).getTo()).as("To").isEqualTo(expected.get(i).getTo());
        }
    }

    private static void assertTimers(Map<Timer, DroolsAction> expected, Map<Timer, DroolsAction> current) {
        if (expected == null) {
            assertThat(current).isNull();
            return;
        }
        assertThat(current).as("Size").hasSameSizeAs(expected);
        expected.forEach((expectedTimer, expectedAction) -> {
            Optional<Timer> currentTimer =
                    current.keySet().stream().filter(c -> Objects.equals(c.getDate(), expectedTimer.getDate()) && Objects.equals(c.getTimeType(), expectedTimer.getTimeType())).findFirst();
            assertThat(currentTimer).isPresent();
            assertThat(currentTimer.get().getPeriod()).as("Period").isEqualTo(expectedTimer.getPeriod());
            assertThat(currentTimer.get().getDate()).as("Date").isEqualTo(expectedTimer.getDate());
            assertThat(currentTimer.get().getDelay()).as("Delay").isEqualTo(expectedTimer.getDelay());
            assertThat(currentTimer.get().getTimeType()).as("TimeType").isEqualTo(expectedTimer.getTimeType());
            DroolsAction currentAction = current.get(currentTimer.get());
            if (expectedAction == null) {
                assertThat(currentAction).isNull();
                return;
            }
            assertThat(currentAction).isNotNull();
            assertThat(currentAction.getName()).as("DroolsAction name").isEqualTo(expectedAction.getName());
            if (expectedAction.getMetaData(ACTION) == null) {
                assertThat(currentAction.getMetaData(ACTION)).isNull();
            } else {
                assertThat(currentAction.getMetaData(ACTION)).isNotNull();
            }
        });
    }

    private static void assertConstraints(NodeImpl eNode, NodeImpl cNode) {
        if (eNode instanceof Split && ((Split) eNode).getType() != Split.TYPE_OR && ((Split) eNode).getType() != Split.TYPE_XOR) {
            return;
        }
        if (eNode.getConstraints() == null) {
            assertThat(cNode.getConstraints()).isNull();
            return;
        }
        Map<ConnectionRef, Collection<Constraint>> expected = eNode.getConstraints();
        Map<ConnectionRef, Collection<Constraint>> current = cNode.getConstraints();
        assertThat(current).hasSameSizeAs(expected);
        expected.forEach((conn, constraint) -> {
            Optional<Map.Entry<ConnectionRef, Collection<Constraint>>> currentEntry = current.entrySet()
                    .stream()
                    .filter(e -> e.getKey().getConnectionId() == null && conn.getConnectionId() == null ||
                            e.getKey().getConnectionId().equals(conn.getConnectionId()))
                    .findFirst();
            assertThat(currentEntry).isPresent();
            ConnectionRef currentConn = currentEntry.get().getKey();
            assertThat(currentConn.getNodeId()).isEqualTo(conn.getNodeId());
            assertThat(currentConn.getToType()).isEqualTo(conn.getToType());
            Collection<Constraint> constraints = currentEntry.get().getValue();
            if (constraint == null) {
                assertThat(constraints).isNull();
            } else {
                Constraint currentConstraint = constraints.iterator().next();
                Constraint expectedConstraint = constraint.iterator().next();
                assertThat(currentConstraint).isNotNull();
                assertThat(currentConstraint.getPriority()).isEqualTo(expectedConstraint.getPriority());
                assertThat(currentConstraint.getDialect()).isEqualTo(expectedConstraint.getDialect());
                assertThat(currentConstraint.getName()).isEqualTo(conn.getConnectionId());
                assertThat(currentConstraint.getType()).isEqualTo(CONNECTION_DEFAULT_TYPE);
            }
        });
    }
}
