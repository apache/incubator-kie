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
import java.util.LinkedHashSet;
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

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

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
import static org.kie.kogito.internal.utils.ConversionUtils.sanitizeClassName;

public class ProcessVisitor extends AbstractVisitor {

    public static final String DEFAULT_VERSION = "1.0";

    private NodeVisitorBuilderService nodeVisitorService;

    private ReturnValueEvaluatorBuilderService returnValueEvaluatorBuilderService;

    public ProcessVisitor(ClassLoader contextClassLoader) {
        nodeVisitorService = new NodeVisitorBuilderService(contextClassLoader);
        returnValueEvaluatorBuilderService = ReturnValueEvaluatorBuilderService.instance(contextClassLoader);
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

        // Collect imports declared at the process level (e.g. java.util.List) and
        // from the process template (e.g. KieFunctions).  These need to appear in
        // every node builder class CU so that lambdas / script actions can use them.
        Set<String> processLevelImports = new LinkedHashSet<>();
        if (process instanceof org.jbpm.process.core.Process coreProcess) {
            Set<String> declared = coreProcess.getImports();
            if (declared != null) {
                processLevelImports.addAll(declared);
            }
        }
        // KieFunctions is always added by the ProcessTemplate – include it in every node CU
        processLevelImports.add(org.drools.core.util.KieFunctions.class.getCanonicalName());

        visitNodesAsBuilderClasses(processNodes, body, variableScope, metadata, processLevelImports);
        //exception scope – only root-process scope; sub-process scopes are handled per-node
        visitExceptionScope(process, body);
        visitConnectionsAsBuilderClass(process.getNodes(), body, metadata);

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
            visitor.visitNodeEntryPoint(null, node, body, variableScope, metadata);
        }
    }

    @SuppressWarnings("unchecked")
    private <U extends org.kie.api.definition.process.Node> void visitNodesAsBuilderClasses(
            List<U> nodes, BlockStmt body, VariableScope variableScope, ProcessMetaData metadata,
            Set<String> processLevelImports) {
        String processBaseClassName = metadata.getProcessBaseClassName();
        String packageName = metadata.getPackageName();

        for (U node : nodes) {
            AbstractNodeVisitor<U> visitor = (AbstractNodeVisitor<U>) this.nodeVisitorService.findNodeVisitor(node.getClass());
            if (visitor == null) {
                throw new IllegalStateException("No visitor found for node " + node.getClass().getName());
            }

            String nodeClassName = processBaseClassName + "_node_" + sanitizeClassName(node.getId().toExternalFormat());

            CompilationUnit cu = new CompilationUnit();
            if (packageName != null) {
                cu.setPackageDeclaration(packageName);
            }
            cu.addImport(org.jbpm.ruleflow.core.RuleFlowProcessFactory.class.getCanonicalName());
            cu.addImport(org.jbpm.ruleflow.core.WorkflowElementIdentifierFactory.class.getCanonicalName());
            // Propagate process-level and template imports so that lambdas / script actions compile
            for (String imp : processLevelImports) {
                cu.addImport(new ImportDeclaration(imp, false, false));
            }

            ClassOrInterfaceDeclaration nodeClass = cu.addClass(nodeClassName);
            nodeClass.setModifiers(); // package-private

            // Attach the build() method with an empty body to the class BEFORE visiting the node,
            // so that tryAddImportToParentCompilationUnit() calls inside visitors propagate up to `cu`.
            MethodDeclaration buildMethod = nodeClass.addMethod("build");
            buildMethod.setModifiers(); // package-private
            buildMethod.setType("void");
            buildMethod.addParameter(new Parameter(
                    new ClassOrInterfaceType(null, org.jbpm.ruleflow.core.RuleFlowProcessFactory.class.getSimpleName()),
                    FACTORY_FIELD_NAME));
            BlockStmt helperBody = new BlockStmt();
            buildMethod.setBody(helperBody);

            visitor.visitNodeEntryPoint(null, node, helperBody, variableScope, metadata);

            // If this node is a composite context container (e.g. sub-process), inline its
            // exception scope handling into the same build() body so the local factory variable
            // is still in scope.  visitSubExceptionScope() in the main process() body skips these.
            if (node instanceof ContextContainer contextContainer) {
                org.jbpm.process.core.Context exScope =
                        contextContainer.getDefaultContext(ExceptionScope.EXCEPTION_SCOPE);
                if (exScope != null) {
                    visitContextExceptionScope(exScope, helperBody);
                }
            }

            // Build a lookup of producer field name -> fully-qualified type.
            // This must be done AFTER visitNodeEntryPoint because triggers are registered
            // inside the visitor via metadata.addTrigger().
            // Use MessageProducer<?> as the parameter type so the node builder class is
            // compatible with both the Quarkus path (generates <ProcessName>MessageProducer_<id>)
            // and the Maven plugin path (uses MessageProducer<DataType> directly).
            Map<String, String> producerFieldTypes = new java.util.HashMap<>();
            for (TriggerMetaData trigger : metadata.getTriggers()) {
                if (trigger.getType() == TriggerMetaData.TriggerType.ProduceMessage) {
                    String fieldName = "producer_" + trigger.getOwnerId();
                    String dataType = trigger.getDataType() != null ? trigger.getDataType() : "java.lang.Object";
                    String typeName = org.kie.kogito.event.impl.MessageProducer.class.getCanonicalName() + "<" + dataType + ">";
                    producerFieldTypes.put(fieldName, typeName);
                }
            }

            // Collect all bare NameExprs in the body that refer to outer-class fields.
            // Known outer-class fields that visitors can reference:
            //   "app"        - Application field (RuleSetNodeVisitor: DMN decisions, rule flow groups)
            //   "producer_*" - MessageProducer fields (AbstractNodeVisitor: ProduceMessage actions)
            // We use a LinkedHashMap to preserve insertion order and store fieldName -> type.
            java.util.LinkedHashMap<String, String> outerFields = new java.util.LinkedHashMap<>();
            helperBody.findAll(com.github.javaparser.ast.expr.NameExpr.class).stream()
                    .map(com.github.javaparser.ast.expr.NameExpr::getNameAsString)
                    .distinct()
                    .forEach(name -> {
                        if ("app".equals(name)) {
                            outerFields.put("app", org.kie.kogito.Application.class.getCanonicalName());
                        } else if (name.startsWith("producer_") && producerFieldTypes.containsKey(name)) {
                            outerFields.put(name, producerFieldTypes.get(name));
                        }
                    });

            if (!outerFields.isEmpty()) {
                // Add a private final field for each outer reference
                outerFields.forEach((fieldName, fieldType) -> nodeClass.addField(fieldType, fieldName, Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL));

                // Add a constructor that accepts them all – insert before the build() method
                com.github.javaparser.ast.body.ConstructorDeclaration ctor = nodeClass.addConstructor();
                BlockStmt ctorBody = new BlockStmt();
                outerFields.forEach((fieldName, fieldType) -> {
                    ctor.addParameter(new Parameter(new ClassOrInterfaceType(null, fieldType), fieldName));
                    ctorBody.addStatement(new AssignExpr(
                            new com.github.javaparser.ast.expr.FieldAccessExpr(new com.github.javaparser.ast.expr.ThisExpr(), fieldName),
                            new NameExpr(fieldName),
                            AssignExpr.Operator.ASSIGN));
                });
                ctor.setBody(ctorBody);
            }

            metadata.addNodeBuilderClass(cu);

            // Emit the dispatch call into process() — pass outer fields as constructor args
            com.github.javaparser.ast.NodeList<com.github.javaparser.ast.expr.Expression> ctorArgs = new com.github.javaparser.ast.NodeList<>();
            outerFields.keySet().forEach(fieldName -> ctorArgs.add(new NameExpr(fieldName)));
            body.addStatement(new ExpressionStmt(
                    new MethodCallExpr(
                            new ObjectCreationExpr(null, new ClassOrInterfaceType(null, nodeClassName), ctorArgs),
                            "build",
                            new com.github.javaparser.ast.NodeList<>(new NameExpr(FACTORY_FIELD_NAME)))));
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

    private void visitConnections(org.kie.api.definition.process.Node[] nodes, BlockStmt body) {

        List<Connection> connections = new ArrayList<>();
        for (org.kie.api.definition.process.Node node : nodes) {
            for (List<Connection> connectionList : node.getIncomingConnections().values()) {
                connections.addAll(connectionList);
            }
        }
        for (Connection connection : connections) {
            visitConnection(connection, body);
        }
    }

    private void visitConnectionsAsBuilderClass(org.kie.api.definition.process.Node[] nodes, BlockStmt body, ProcessMetaData metadata) {
        List<Connection> connections = new ArrayList<>();
        for (org.kie.api.definition.process.Node node : nodes) {
            for (List<Connection> connectionList : node.getIncomingConnections().values()) {
                connections.addAll(connectionList);
            }
        }
        if (connections.isEmpty()) {
            return;
        }

        String processBaseClassName = metadata.getProcessBaseClassName();
        String packageName = metadata.getPackageName();
        String connectionsClassName = processBaseClassName + "_connections";

        CompilationUnit cu = new CompilationUnit();
        if (packageName != null) {
            cu.setPackageDeclaration(packageName);
        }
        cu.addImport(org.jbpm.ruleflow.core.RuleFlowProcessFactory.class.getCanonicalName());
        cu.addImport(org.jbpm.ruleflow.core.WorkflowElementIdentifierFactory.class.getCanonicalName());

        ClassOrInterfaceDeclaration connectionsClass = cu.addClass(connectionsClassName);
        connectionsClass.setModifiers(); // package-private

        BlockStmt helperBody = new BlockStmt();
        for (Connection connection : connections) {
            visitConnection(connection, helperBody);
        }

        MethodDeclaration buildMethod = connectionsClass.addMethod("build");
        buildMethod.setModifiers(); // package-private
        buildMethod.setType("void");
        buildMethod.addParameter(new Parameter(
                new ClassOrInterfaceType(null, org.jbpm.ruleflow.core.RuleFlowProcessFactory.class.getSimpleName()),
                FACTORY_FIELD_NAME));
        buildMethod.setBody(helperBody);

        metadata.addNodeBuilderClass(cu);

        body.addStatement(new ExpressionStmt(
                new MethodCallExpr(
                        new ObjectCreationExpr(null, new ClassOrInterfaceType(null, connectionsClassName), new com.github.javaparser.ast.NodeList<>()),
                        "build",
                        new com.github.javaparser.ast.NodeList<>(new NameExpr(FACTORY_FIELD_NAME)))));
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
        //root process
        visitContextExceptionScope(context, body);
        //visit sub-processes
        visitSubExceptionScope(workflowProcess.getNodes(), body);
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
                // Skip CompositeNode sub-processes: their exception scopes are handled
                // inside the node's own builder class build() method, where the local
                // composite node factory variable is still in scope.
                .filter(container -> !(container instanceof CompositeNode))
                .map(container -> container.getDefaultContext(ExceptionScope.EXCEPTION_SCOPE))
                .forEach(context -> visitContextExceptionScope(context, body));
    }
}
