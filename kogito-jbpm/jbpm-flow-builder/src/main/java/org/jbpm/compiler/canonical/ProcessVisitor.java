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
package org.jbpm.compiler.canonical;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.jbpm.compiler.canonical.builtin.ReturnValueEvaluatorBuilderService;
import org.jbpm.compiler.canonical.descriptors.ExpressionUtils;
import org.jbpm.compiler.canonical.node.NodeVisitorBuilderService;
import org.jbpm.process.core.Context;
import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.core.Work;
import org.jbpm.process.core.context.exception.ActionExceptionHandler;
import org.jbpm.process.core.context.exception.ExceptionScope;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.core.correlation.Correlation;
import org.jbpm.process.core.correlation.CorrelationManager;
import org.jbpm.process.core.correlation.CorrelationProperties;
import org.jbpm.process.core.correlation.Message;
import org.jbpm.process.instance.impl.ReturnValueEvaluator;
import org.jbpm.process.instance.impl.actions.SignalProcessInstanceAction;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.NodeContainer;
import org.jbpm.workflow.core.impl.ConnectionImpl;
import org.jbpm.workflow.core.node.CompositeNode;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.kie.api.definition.process.Connection;
import org.kie.api.definition.process.Process;
import org.kie.api.definition.process.WorkflowProcess;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcess;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.VoidType;

import static org.jbpm.ruleflow.core.Metadata.ASSOCIATION;
import static org.jbpm.ruleflow.core.RuleFlowNodeContainerFactory.METHOD_ASSOCIATION;
import static org.jbpm.ruleflow.core.RuleFlowNodeContainerFactory.METHOD_CONNECTION;
import static org.jbpm.ruleflow.core.RuleFlowProcessFactory.METHOD_DYNAMIC;
import static org.jbpm.ruleflow.core.RuleFlowProcessFactory.METHOD_ERROR_EXCEPTION_HANDLER;
import static org.jbpm.ruleflow.core.RuleFlowProcessFactory.METHOD_GLOBAL;
import static org.jbpm.ruleflow.core.RuleFlowProcessFactory.METHOD_IMPORTS;
import static org.jbpm.ruleflow.core.RuleFlowProcessFactory.METHOD_NAME;
import static org.jbpm.ruleflow.core.RuleFlowProcessFactory.METHOD_PACKAGE_NAME;
import static org.jbpm.ruleflow.core.RuleFlowProcessFactory.METHOD_TYPE;
import static org.jbpm.ruleflow.core.RuleFlowProcessFactory.METHOD_VALIDATE;
import static org.jbpm.ruleflow.core.RuleFlowProcessFactory.METHOD_VERSION;
import static org.jbpm.ruleflow.core.RuleFlowProcessFactory.METHOD_VISIBILITY;

public class ProcessVisitor extends AbstractVisitor {

    public static final String DEFAULT_VERSION = "1.0";

    private NodeVisitorBuilderService nodeVisitorService;

    private ReturnValueEvaluatorBuilderService returnValueEvaluatorBuilderService;

    public ProcessVisitor(ClassLoader contextClassLoader) {
        nodeVisitorService = new NodeVisitorBuilderService(contextClassLoader);
        returnValueEvaluatorBuilderService = ReturnValueEvaluatorBuilderService.instance(contextClassLoader);
    }

    /**
     * Name of the factory parameter used in every per-node and per-connection helper method.
     * Using a constant avoids repeated string literals and keeps signatures consistent.
     */
    private static final String FACTORY_PARAM_NAME = "factory";

    /**
     * Builds a {@code private void} helper method that accepts a single
     * {@link RuleFlowProcessFactory} parameter and executes the given body statements.
     *
     * <p>
     * The method is intentionally <em>not</em> {@code static} so that lambda bodies
     * inside node initialisations (e.g. {@code () -> producer_X}) can capture instance
     * fields of the generated {@code XxxProcess} class without a compile error.
     */
    private static MethodDeclaration buildHelperMethod(String name, BlockStmt helperBody) {
        return new MethodDeclaration()
                .setModifiers(Modifier.Keyword.PRIVATE)
                .setType(new VoidType())
                .setName(name)
                .addParameter(new Parameter(
                        new ClassOrInterfaceType(null, RuleFlowProcessFactory.class.getSimpleName()),
                        FACTORY_PARAM_NAME))
                .setBody(helperBody);
    }

    public void visitProcess(WorkflowProcess process, MethodDeclaration processMethod, ProcessMetaData metadata) {
        BlockStmt body = new BlockStmt();
        processMethod.setBody(body);

        ClassOrInterfaceType processFactoryType = new ClassOrInterfaceType(null, RuleFlowProcessFactory.class.getSimpleName());

        // create local variable factory and assign new fluent process to it
        VariableDeclarationExpr factoryField = new VariableDeclarationExpr(processFactoryType, FACTORY_FIELD_NAME);
        MethodCallExpr assignFactoryMethod = new MethodCallExpr(new NameExpr(processFactoryType.getName().asString()), "createProcess");
        assignFactoryMethod.addArgument(new StringLiteralExpr(process.getId()));
        if (process instanceof org.jbpm.workflow.core.WorkflowProcess) {
            assignFactoryMethod.addArgument(new BooleanLiteralExpr(((org.jbpm.workflow.core.WorkflowProcess) process).isAutoComplete()));
        }

        body.addStatement(new AssignExpr(factoryField, assignFactoryMethod, AssignExpr.Operator.ASSIGN));

        // item definitions
        Set<String> visitedVariables = new HashSet<>();
        VariableScope variableScope = (VariableScope) ((org.jbpm.process.core.Process) process).getDefaultContext(VariableScope.VARIABLE_SCOPE);

        visitVariableScope(FACTORY_FIELD_NAME, variableScope, body, visitedVariables, metadata.getProcessClassName());
        visitSubVariableScopes(process.getNodes(), body, visitedVariables);

        if (process instanceof org.jbpm.workflow.core.WorkflowProcess) {
            org.jbpm.workflow.core.WorkflowProcess processImpl = (org.jbpm.workflow.core.WorkflowProcess) process;
            if (processImpl.getExpressionLanguage() != null) {
                body.addStatement(getFactoryMethod(FACTORY_FIELD_NAME, "expressionLanguage", new StringLiteralExpr(processImpl.getExpressionLanguage())));
            }
        }

        visitInterfaces(process.getNodes());

        metadata.setDynamic(((org.jbpm.workflow.core.WorkflowProcess) process).isDynamic());
        // the process itself
        body.addStatement(getFactoryMethod(FACTORY_FIELD_NAME, METHOD_NAME, new StringLiteralExpr(process.getName())))
                .addStatement(getFactoryMethod(FACTORY_FIELD_NAME, METHOD_PACKAGE_NAME, new StringLiteralExpr(process.getPackageName())))
                .addStatement(getFactoryMethod(FACTORY_FIELD_NAME, METHOD_DYNAMIC, new BooleanLiteralExpr(metadata.isDynamic())))
                .addStatement(getFactoryMethod(FACTORY_FIELD_NAME, METHOD_VERSION, new StringLiteralExpr(getOrDefault(process.getVersion(), DEFAULT_VERSION))))
                .addStatement(getFactoryMethod(FACTORY_FIELD_NAME, METHOD_TYPE, new StringLiteralExpr(getOrDefault(process.getType(), KogitoWorkflowProcess.BPMN_TYPE))))
                .addStatement(getFactoryMethod(FACTORY_FIELD_NAME, METHOD_VISIBILITY,
                        new StringLiteralExpr(getOrDefault(((KogitoWorkflowProcess) process).getVisibility(), KogitoWorkflowProcess.PUBLIC_VISIBILITY))));

        ((org.jbpm.workflow.core.WorkflowProcess) process).getInputValidator().ifPresent(
                v -> body.addStatement(getFactoryMethod(FACTORY_FIELD_NAME, "inputValidator", ExpressionUtils.getLiteralExpr(v))));
        ((org.jbpm.workflow.core.WorkflowProcess) process).getOutputValidator().ifPresent(
                v -> body.addStatement(getFactoryMethod(FACTORY_FIELD_NAME, "outputValidator", ExpressionUtils.getLiteralExpr(v))));

        visitMetaData(process.getMetaData(), body, FACTORY_FIELD_NAME);
        visitCollaboration(process, body);
        visitCompensationScope(process, body);
        visitHeader(process, body);

        List<Node> processNodes = new ArrayList<>();
        for (org.kie.api.definition.process.Node procNode : process.getNodes()) {
            processNodes.add((Node) procNode);
        }
        visitNodes(processNodes, body, variableScope, metadata);
        //exception scope
        visitExceptionScope(process, body);
        visitConnections(process.getNodes(), body, metadata);

        body.addStatement(getFactoryMethod(FACTORY_FIELD_NAME, METHOD_VALIDATE));

        MethodCallExpr getProcessMethod = new MethodCallExpr(new NameExpr(FACTORY_FIELD_NAME), "getProcess");
        body.addStatement(new ReturnStmt(getProcessMethod));
    }

    private void visitCollaboration(WorkflowProcess process, BlockStmt body) {
        RuleFlowProcess ruleFlowProcess = (RuleFlowProcess) process;
        CorrelationManager correlationManager = ruleFlowProcess.getCorrelationManager();

        for (String messageId : correlationManager.getMessagesId()) {
            Message message = correlationManager.findMessageById(messageId);
            body.addStatement(getFactoryMethod(FACTORY_FIELD_NAME, "newCorrelationMessage",
                    new StringLiteralExpr(message.getMessageRef()), new StringLiteralExpr(message.getMessageName()), new StringLiteralExpr(message.getMessageType())));
        }

        for (String correlationId : correlationManager.getCorrelationsId()) {
            Correlation correlation = correlationManager.findCorrelationById(correlationId);
            body.addStatement(getFactoryMethod(FACTORY_FIELD_NAME, "newCorrelationKey",
                    new StringLiteralExpr(correlation.getId()), new StringLiteralExpr(correlation.getName())));

            for (String messageId : correlationManager.getMessagesId()) {
                CorrelationProperties properties = correlation.getMessageCorrelationFor(messageId);
                for (String propertyName : properties.names()) {
                    ReturnValueEvaluator evaluator = properties.getExpressionFor(propertyName);
                    Expression returnValueEvaluator = returnValueEvaluatorBuilderService.build(ruleFlowProcess, evaluator.dialect(), evaluator.expression());
                    body.addStatement(getFactoryMethod(FACTORY_FIELD_NAME, "newCorrelationProperty",
                            new StringLiteralExpr(correlation.getId()), new StringLiteralExpr(messageId), new StringLiteralExpr(propertyName), returnValueEvaluator));
                }
            }
            CorrelationProperties subscriptions = correlation.getProcessSubscription();
            for (String propertyName : subscriptions.names()) {
                ReturnValueEvaluator evaluator = subscriptions.getExpressionFor(propertyName);
                Expression returnValueEvaluator = returnValueEvaluatorBuilderService.build(ruleFlowProcess, evaluator.dialect(), evaluator.expression());
                body.addStatement(getFactoryMethod(FACTORY_FIELD_NAME, "newCorrelationSubscription",
                        new StringLiteralExpr(correlation.getId()), new StringLiteralExpr(propertyName), returnValueEvaluator));
            }
        }
    }

    private void visitSubVariableScopes(org.kie.api.definition.process.Node[] nodes, BlockStmt body, Set<String> visitedVariables) {
        for (org.kie.api.definition.process.Node node : nodes) {
            if (node instanceof ContextContainer) {
                VariableScope variableScope = (VariableScope) ((ContextContainer) node).getDefaultContext(VariableScope.VARIABLE_SCOPE);
                if (variableScope != null) {
                    visitVariableScope(FACTORY_FIELD_NAME, variableScope, body, visitedVariables, node.getClass().getName());
                }
            }
            if (node instanceof NodeContainer) {
                visitSubVariableScopes(((NodeContainer) node).getNodes(), body, visitedVariables);
            }
        }
    }

    private void visitHeader(WorkflowProcess process, BlockStmt body) {
        Set<String> imports = ((org.jbpm.process.core.Process) process).getImports();
        if (imports != null) {
            for (String s : imports) {
                body.addStatement(getFactoryMethod(FACTORY_FIELD_NAME, METHOD_IMPORTS, new StringLiteralExpr(s)));
            }
        }
        Map<String, String> globals = ((org.jbpm.process.core.Process) process).getGlobals();
        if (globals != null) {
            for (Map.Entry<String, String> global : globals.entrySet()) {
                body.addStatement(getFactoryMethod(FACTORY_FIELD_NAME, METHOD_GLOBAL, new StringLiteralExpr(global.getKey()), new StringLiteralExpr(global.getValue())));
            }
        }
    }

    private <U extends org.kie.api.definition.process.Node> void visitNodes(List<U> nodes, BlockStmt body, VariableScope variableScope, ProcessMetaData metadata) {
        for (U node : nodes) {
            @SuppressWarnings("unchecked")
            AbstractNodeVisitor<U> visitor = (AbstractNodeVisitor<U>) this.nodeVisitorService.findNodeVisitor(node.getClass());
            if (visitor == null) {
                throw new IllegalStateException("No visitor found for node " + node.getClass().getName());
            }

            // Collect all statements for this node into a dedicated helper method so
            // that process() never grows large enough to exceed the JVM 64KB method limit.
            BlockStmt nodeBody = new BlockStmt();
            visitor.visitNodeEntryPoint(null, node, nodeBody, variableScope, metadata);

            // Exception scope statements for sub-process nodes reference a local variable
            // (e.g. compositeContextNodeSubProcess_1) that is declared inside this nodeBody.
            // They must therefore be appended here, before the helper method is closed,
            // not written into process() where that variable is out of scope.
            if (node instanceof ContextContainer) {
                Context exceptionContext = ((ContextContainer) node).getDefaultContext(ExceptionScope.EXCEPTION_SCOPE);
                visitContextExceptionScope(exceptionContext, nodeBody);
            }
            if (node instanceof NodeContainer) {
                visitSubExceptionScope(((NodeContainer) node).getNodes(), nodeBody);
            }

            String helperName = "initNode_" + node.getId().toSanitizeString();
            metadata.addProcessHelperMethod(buildHelperMethod(helperName, nodeBody));

            // process() just calls the helper, passing the factory local variable
            body.addStatement(new MethodCallExpr(null, helperName)
                    .addArgument(new NameExpr(FACTORY_FIELD_NAME)));
        }
    }

    @SuppressWarnings("unchecked")
    private String getFieldName(ContextContainer contextContainer) {
        AbstractNodeVisitor visitor = null;
        if (contextContainer instanceof CompositeNode) {
            visitor = this.nodeVisitorService.findNodeVisitor(contextContainer.getClass());
        }
        return visitor != null ? visitor.getNodeId(((Node) contextContainer)) : FACTORY_FIELD_NAME;
    }

    private static final String INIT_CONNECTIONS_METHOD = "initConnections";

    private void visitConnections(org.kie.api.definition.process.Node[] nodes, BlockStmt body, ProcessMetaData metadata) {

        List<Connection> connections = new ArrayList<>();
        for (org.kie.api.definition.process.Node node : nodes) {
            for (List<Connection> connectionList : node.getIncomingConnections().values()) {
                connections.addAll(connectionList);
            }
        }

        if (connections.isEmpty()) {
            return;
        }

        // All connection statements are one-liners (one factory.connection() call each),
        // so they all fit safely in a single helper method — even for large BPMNs.
        // 755 connections × ~40 bytecode bytes = ~28 KB, well within the 64 KB limit.
        BlockStmt connectionsBody = new BlockStmt();
        for (Connection connection : connections) {
            visitConnection(connection, connectionsBody);
        }

        metadata.addProcessHelperMethod(buildHelperMethod(INIT_CONNECTIONS_METHOD, connectionsBody));

        body.addStatement(new MethodCallExpr(null, INIT_CONNECTIONS_METHOD)
                .addArgument(new NameExpr(FACTORY_FIELD_NAME)));
    }

    // KOGITO-1882 Finish implementation or delete completely
    private void visitInterfaces(org.kie.api.definition.process.Node[] nodes) {
        for (org.kie.api.definition.process.Node node : nodes) {
            if (node instanceof WorkItemNode) {
                Work work = ((WorkItemNode) node).getWork();
                if (work != null) {
                    // TODO - finish this method
                }
            }
        }
    }

    private void visitConnection(Connection connection, BlockStmt body) {
        String method = METHOD_CONNECTION;
        Object association = ((ConnectionImpl) connection).getMetaData(ASSOCIATION);
        if (association != null && ((Boolean) association)) {
            method = METHOD_ASSOCIATION;
        }
        body.addStatement(getFactoryMethod(FACTORY_FIELD_NAME, method,
                getWorkflowElementConstructor(connection.getFrom().getId()),
                getWorkflowElementConstructor(connection.getTo().getId()),
                new StringLiteralExpr(getOrDefault(connection.getUniqueId(), ""))));
    }

    private void visitCompensationScope(Process process, BlockStmt body) {
        if (process instanceof RuleFlowProcess) {
            visitCompensationScope((RuleFlowProcess) process, body, FACTORY_FIELD_NAME);
        }
    }

    private void visitExceptionScope(Process process, BlockStmt body) {
        if (!(process instanceof org.jbpm.workflow.core.WorkflowProcess)) {
            return;
        }
        org.jbpm.workflow.core.WorkflowProcess workflowProcess = (org.jbpm.workflow.core.WorkflowProcess) process;
        Context context = workflowProcess.getDefaultContext(ExceptionScope.EXCEPTION_SCOPE);
        // Root process exception scope only — sub-process node exception scopes are
        // handled inside visitNodes() where the node variable is still in scope.
        visitContextExceptionScope(context, body);
    }

    private void visitContextExceptionScope(Context context, BlockStmt body) {
        if (context instanceof ExceptionScope) {
            ((ExceptionScope) context).getExceptionHandlers().entrySet().stream().forEach(e -> {
                String faultCode = e.getKey();
                ActionExceptionHandler handler = (ActionExceptionHandler) e.getValue();
                Optional<String> faultVariable = Optional.ofNullable(handler.getFaultVariable());
                SignalProcessInstanceAction action = (SignalProcessInstanceAction) handler.getAction().getMetaData("Action");
                String signalName = action.getSignalName();
                body.addStatement(getFactoryMethod(getFieldName(context.getContextContainer()), METHOD_ERROR_EXCEPTION_HANDLER,
                        new StringLiteralExpr(signalName),
                        faultCode != null ? new StringLiteralExpr(faultCode) : new NullLiteralExpr(),
                        faultVariable.<Expression> map(StringLiteralExpr::new).orElse(new NullLiteralExpr())));

            });
        }
    }

    private void visitSubExceptionScope(org.kie.api.definition.process.Node[] nodes, BlockStmt body) {
        Stream.of(nodes)
                .peek(node -> {
                    //recursively handle subprocesses exception scope
                    if (node instanceof NodeContainer) {
                        visitSubExceptionScope(((NodeContainer) node).getNodes(), body);
                    }
                })
                .filter(ContextContainer.class::isInstance)
                .map(ContextContainer.class::cast)
                .map(container -> container.getDefaultContext(ExceptionScope.EXCEPTION_SCOPE))
                .forEach(context -> visitContextExceptionScope(context, body));
    }
}
