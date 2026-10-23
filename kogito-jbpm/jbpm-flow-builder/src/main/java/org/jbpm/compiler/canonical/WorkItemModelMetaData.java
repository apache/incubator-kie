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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import org.jbpm.process.core.Work;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.core.datatype.DataType;
import org.jbpm.process.core.datatype.DataTypeResolver;
import org.jbpm.util.PatternConstants;
import org.jbpm.workflow.core.node.HumanTaskNode;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.kie.api.annotations.KieInternal;
import org.kie.kogito.UserTask;
import org.kie.kogito.UserTaskParam;
import org.kie.kogito.UserTaskParam.ParamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.nodeTypes.NodeWithVariables;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import static com.github.javaparser.StaticJavaParser.parse;
import static com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType;
import static org.jbpm.ruleflow.core.Metadata.CUSTOM_AUTO_START;
import static org.kie.kogito.internal.utils.ConversionUtils.sanitizeClassName;
import static org.kie.kogito.internal.utils.ConversionUtils.sanitizeJavaName;

public class WorkItemModelMetaData {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkItemModelMetaData.class);

    @KieInternal
    static final String CONFLICTING_TYPES_ERROR_TEMPLATE = "Conflicting types for %s parameter '%s' in task '%s' of process '%s': %s vs %s";
    @KieInternal
    static final String MISMATCHED_DEFINITION_ERROR_TEMPLATE = "Cannot share taskName '%s' between work items of different definition types ('%s' vs '%s') in process '%s'";
    @KieInternal
    static final String CONFLICTING_TASK_NAMES_ERROR_TEMPLATE = "Task names '%s' and '%s' in process '%s' resolve to the same generated model class '%s'. Use distinct task names";

    private static final String TASK_NAME_PARAMETER = "TaskName";

    private static final String TASK_INPUT_CLASS_SUFFIX = "TaskInput";
    private static final String TASK_OUTPUT_CLASS_SUFFIX = "TaskOutput";
    private static final String TASK_MODEL_CLASS_SUFFIX = "TaskModel";
    private static final String WORK_ITEM = "workItem";
    private static final String PARAMS = "params";

    public record ParameterDefinition(String originalKey, String sanitizedName, String type) {
    }

    public record WorkItemNodeContext(WorkItemNode workItemNode, VariableScope variableScope) {
    }

    private final String taskName;
    private final String processId;
    private final String packageName;
    private final VariableScope processVariableScope;
    private Work work;

    private final String inputModelClassName;
    private final String inputModelClassSimpleName;
    private final String outputModelClassName;
    private final String outputModelClassSimpleName;
    private final String taskModelClassName;
    private final String taskModelClassSimpleName;

    private final List<WorkItemNodeContext> nodes = new ArrayList<>();
    private final Map<String, ParameterDefinition> inputParamDefs = new LinkedHashMap<>();
    private final Map<String, ParameterDefinition> outputParamDefs = new LinkedHashMap<>();

    public WorkItemModelMetaData(String taskName, String processId, String packageName, VariableScope processVariableScope) {
        this.taskName = taskName;
        this.processId = processId;
        this.packageName = packageName;
        this.processVariableScope = processVariableScope;

        String classPrefix = sanitizeClassName(ProcessToExecModelGenerator.extractProcessId(processId) + "_" + sanitizeClassName(taskName));

        this.inputModelClassSimpleName = classPrefix + "_" + TASK_INPUT_CLASS_SUFFIX;
        this.inputModelClassName = packageName + '.' + inputModelClassSimpleName;

        this.outputModelClassSimpleName = classPrefix + "_" + TASK_OUTPUT_CLASS_SUFFIX;
        this.outputModelClassName = packageName + '.' + outputModelClassSimpleName;

        this.taskModelClassSimpleName = classPrefix + "_" + TASK_MODEL_CLASS_SUFFIX;
        this.taskModelClassName = packageName + '.' + taskModelClassSimpleName;
    }

    /**
     * Resolves the task name of a work item node: the <code>TaskName</code> work parameter if present,
     * the node name otherwise.
     */
    public static String resolveTaskName(WorkItemNode workItemNode) {
        return (String) workItemNode.getWork().getParameters().getOrDefault(TASK_NAME_PARAMETER, workItemNode.getName());
    }

    /**
     * Normalized task name, used to group work item nodes: task names that are normalized to the same value generate the same model classes.
     */
    public static String normalizeTaskName(String taskName) {
        return sanitizeClassName(taskName);
    }

    public WorkItemModelMetaData add(WorkItemNode workItemNode, VariableScope nodeVariableScope) {
        String nodeTaskName = resolveTaskName(workItemNode);
        if (!taskName.equals(nodeTaskName)) {
            throw new IllegalArgumentException(String.format(
                    CONFLICTING_TASK_NAMES_ERROR_TEMPLATE,
                    taskName, nodeTaskName, processId, taskModelClassName));
        }

        if (this.work == null) {
            this.work = Objects.requireNonNull(workItemNode.getWork(), "WorkItemNode work cannot be null");
        } else {
            String existingWorkName = this.work.getName();
            String newWorkName = workItemNode.getWork().getName();
            if (!existingWorkName.equals(newWorkName)) {
                throw new IllegalArgumentException(String.format(
                        MISMATCHED_DEFINITION_ERROR_TEMPLATE,
                        taskName, existingWorkName, newWorkName, processId));
            }
        }

        nodes.add(new WorkItemNodeContext(workItemNode, nodeVariableScope));

        Map<String, ParameterDefinition> nodeInputs = extractInputParameters(
                workItemNode, processVariableScope, nodeVariableScope);
        mergeParameters(inputParamDefs, nodeInputs, "input");

        Map<String, ParameterDefinition> nodeOutputs = extractOutputParameters(
                workItemNode, processVariableScope, nodeVariableScope);
        mergeParameters(outputParamDefs, nodeOutputs, "output");

        return this;
    }

    private void mergeParameters(Map<String, ParameterDefinition> target, Map<String, ParameterDefinition> source, String direction) {
        for (Entry<String, ParameterDefinition> entry : source.entrySet()) {
            ParameterDefinition existing = target.get(entry.getKey());
            if (existing != null) {
                if (!existing.type().equals(entry.getValue().type())) {
                    throw new IllegalArgumentException(String.format(
                            CONFLICTING_TYPES_ERROR_TEMPLATE,
                            direction, entry.getValue().originalKey(), taskName, processId, existing.type(), entry.getValue().type()));
                }
            } else {
                target.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public static Map<String, ParameterDefinition> extractInputParameters(WorkItemNode workItemNode, VariableScope processVariableScope,
            VariableScope nodeVariableScope) {
        Map<String, ParameterDefinition> inputParamDefs = new LinkedHashMap<>();
        Map<String, String> inputTypes = workItemNode.getIoSpecification().getInputTypes();

        for (Entry<String, String> entry : workItemNode.getIoSpecification().getInputMapping().entrySet()) {
            if (HumanTaskNode.TASK_PARAMETERS.contains(entry.getKey())) {
                continue;
            }

            Variable variable = Optional.ofNullable(nodeVariableScope.findVariable(entry.getValue()))
                    .orElse(processVariableScope.findVariable(entry.getValue()));

            if (variable == null) {
                variable = new Variable();
                variable.setName(entry.getKey());
                DataType type = DataTypeResolver.fromType(inputTypes.get(entry.getKey()), Thread.currentThread().getContextClassLoader());
                variable.setType(type);
                if (entry.getValue() != null && !PatternConstants.PARAMETER_MATCHER.matcher(entry.getValue()).find()) {
                    variable.setValue(type.readValue(entry.getValue()));
                }
            }

            String variableType = variable.getType().getStringType();
            if (Object.class.getCanonicalName().equals(variableType)) {
                LOGGER.warn("Input variable '{}' of work item '{}' is declared as java.lang.Object. " +
                        "Object type variables are persisted using Java serialization. " +
                        "Make sure the stored value implements java.io.Serializable.", entry.getKey(), workItemNode.getName());
            }

            String fieldName = sanitizeJavaName(entry.getKey());
            inputParamDefs.put(fieldName, new ParameterDefinition(entry.getKey(), fieldName, variableType));
        }

        for (Entry<String, Object> entry : workItemNode.getWork().getParameters().entrySet()) {
            if (entry.getValue() == null || HumanTaskNode.TASK_PARAMETERS.contains(entry.getKey())) {
                continue;
            }

            String fieldName = sanitizeJavaName(entry.getKey());
            inputParamDefs.put(fieldName, new ParameterDefinition(entry.getKey(), fieldName, entry.getValue().getClass().getCanonicalName()));
        }

        return inputParamDefs;
    }

    public static Map<String, ParameterDefinition> extractOutputParameters(WorkItemNode workItemNode, VariableScope processVariableScope,
            VariableScope nodeVariableScope) {
        Map<String, ParameterDefinition> outputParamDefs = new LinkedHashMap<>();
        Map<String, String> outputTypes = workItemNode.getIoSpecification().getOutputTypes();

        for (Entry<String, String> entry : workItemNode.getIoSpecification().getOutputMappingBySources().entrySet()) {
            if (entry.getValue() == null || HumanTaskNode.TASK_PARAMETERS.contains(entry.getKey())) {
                continue;
            }

            Variable variable = Optional.ofNullable(nodeVariableScope.findVariable(entry.getValue()))
                    .orElse(processVariableScope.findVariable(entry.getValue()));

            if (variable == null) {
                variable = new Variable();
                variable.setName(entry.getKey());
                DataType type = DataTypeResolver.fromType(outputTypes.get(entry.getKey()), Thread.currentThread().getContextClassLoader());
                variable.setType(type);
                if (entry.getValue() != null && !PatternConstants.PARAMETER_MATCHER.matcher(entry.getValue()).find()) {
                    variable.setValue(type.readValue(entry.getValue()));
                }
            }

            String variableType = variable.getType().getStringType();
            if (Object.class.getCanonicalName().equals(variableType)) {
                LOGGER.warn("Output variable '{}' of work item '{}' is declared as java.lang.Object. " +
                        "Object type variables are persisted using Java serialization. " +
                        "Make sure the stored value implements java.io.Serializable.", entry.getKey(), workItemNode.getName());
            }

            String fieldName = sanitizeJavaName(entry.getKey());
            outputParamDefs.put(fieldName, new ParameterDefinition(entry.getKey(), fieldName, variableType));
        }

        return outputParamDefs;
    }

    public List<WorkItemNodeContext> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public boolean isAdHoc() {
        return nodes.stream().anyMatch(nodeCtx -> isNodeAdHoc(nodeCtx.workItemNode()));
    }

    private boolean isNodeAdHoc(WorkItemNode workItemNode) {
        return !Boolean.parseBoolean((String) workItemNode.getMetaData(CUSTOM_AUTO_START))
                && (workItemNode.getIncomingConnections() == null || workItemNode.getIncomingConnections().isEmpty());
    }

    public SwitchEntry getModelSwitchEntry() {
        SwitchEntry entry = new SwitchEntry();
        NodeList<Expression> labels = new NodeList<>();
        for (WorkItemNodeContext nodeCtx : nodes) {
            labels.add(new StringLiteralExpr(nodeCtx.workItemNode().getId().toExternalFormat()));
        }
        entry.setLabels(labels);
        entry.addStatement(new ReturnStmt(new MethodCallExpr(new NameExpr(
                taskModelClassSimpleName), new SimpleName("from")).addArgument(WORK_ITEM)));
        return entry;
    }

    public String generateInput() {
        CompilationUnit modelClass = compilationUnitInput();
        return modelClass.toString();
    }

    public String generateOutput() {
        CompilationUnit modelClass = compilationUnitOutput();
        return modelClass.toString();
    }

    public String generateModel() {
        CompilationUnit modelClass = compilationUnitModel();
        return modelClass.toString();
    }

    public String getInputModelClassName() {
        return inputModelClassName;
    }

    public String getOutputModelClassName() {
        return outputModelClassName;
    }

    public String getTaskModelClassName() {
        return taskModelClassName;
    }

    public String getName() {
        return taskName;
    }

    public Work getWork() {
        return work;
    }

    public String getWorkName() {
        return work != null ? work.getName() : null;
    }

    private void addUserTaskAnnotation(ClassOrInterfaceDeclaration modelClass) {
        modelClass.addAndGetAnnotation(UserTask.class).addPair("taskName", new StringLiteralExpr(taskName)).addPair("processName", new StringLiteralExpr(processId));
    }

    private void addUserTaskParamAnnotation(FieldDeclaration fd, UserTaskParam.ParamType paramType) {
        fd.tryAddImportToParentCompilationUnit(ParamType.class);
        fd.addAndGetAnnotation(UserTaskParam.class).addPair("value", ParamType.class.getSimpleName() + '.' + paramType);
    }

    private RuntimeException cannotFindClass() {
        return new IllegalStateException("Cannot find class declaration in the template");
    }

    private CompilationUnit compilationUnitInput() {
        CompilationUnit compilationUnit = parse(this.getClass().getResourceAsStream("/class-templates/TaskInputTemplate.java"));
        compilationUnit.setPackageDeclaration(packageName);
        ClassOrInterfaceDeclaration modelClass = compilationUnit.findFirst(ClassOrInterfaceDeclaration.class,
                sl1 -> true).orElseThrow(this::cannotFindClass);
        addComment(compilationUnit, "Task input");
        addUserTaskAnnotation(modelClass);

        modelClass.setName(inputModelClassSimpleName);

        // setup of static from method body
        ClassOrInterfaceType modelType = new ClassOrInterfaceType(null, modelClass.getNameAsString());
        BlockStmt staticFromMap = new BlockStmt();
        VariableDeclarationExpr itemField = new VariableDeclarationExpr(modelType, "item");
        staticFromMap.addStatement(new AssignExpr(itemField, new ObjectCreationExpr(null, modelType, NodeList.nodeList()), AssignExpr.Operator.ASSIGN));
        NameExpr item = new NameExpr("item");

        for (ParameterDefinition paramDef : inputParamDefs.values()) {
            FieldDeclaration fd = new FieldDeclaration().addVariable(
                    new VariableDeclarator()
                            .setType(paramDef.type())
                            .setName(paramDef.sanitizedName()))
                    .addModifier(Modifier.Keyword.PRIVATE);

            modelClass.addMember(fd);
            addUserTaskParamAnnotation(fd, UserTaskParam.ParamType.INPUT);

            fd.createGetter();
            fd.createSetter();

            // from static method body — sanitised name for the identifier, original key for the runtime map lookup
            FieldAccessExpr field = new FieldAccessExpr(item, paramDef.sanitizedName());

            ClassOrInterfaceType type = parseClassOrInterfaceType(paramDef.type());
            staticFromMap.addStatement(new AssignExpr(field, new CastExpr(
                    type,
                    new MethodCallExpr(
                            new NameExpr(PARAMS),
                            "get")
                                    .addArgument(new StringLiteralExpr(paramDef.originalKey()))),
                    AssignExpr.Operator.ASSIGN));
        }

        Optional<MethodDeclaration> staticFromMethod = modelClass.findFirst(
                MethodDeclaration.class, sl -> sl.getName().asString().equals("fromMap") && sl.isStatic());
        if (staticFromMethod.isPresent()) {
            MethodDeclaration fromMethod = staticFromMethod.get();
            fromMethod.setType(modelClass.getNameAsString());
            staticFromMap.addStatement(new ReturnStmt(item));
            fromMethod.setBody(staticFromMap);
        }
        return compilationUnit;
    }

    private CompilationUnit compilationUnitOutput() {
        CompilationUnit compilationUnit = parse(this.getClass().getResourceAsStream("/class-templates/TaskOutputTemplate.java"));
        compilationUnit.setPackageDeclaration(packageName);
        ClassOrInterfaceDeclaration modelClass = compilationUnit.findFirst(ClassOrInterfaceDeclaration.class,
                sl1 -> true).orElseThrow(this::cannotFindClass);
        addComment(compilationUnit, "Task output");
        addUserTaskAnnotation(modelClass);

        modelClass.setName(outputModelClassSimpleName);

        // setup of toMap method body and fromMap method body
        BlockStmt toMapBody = new BlockStmt();
        BlockStmt fromMapBody = new BlockStmt();
        ClassOrInterfaceType modelType = new ClassOrInterfaceType(null, modelClass.getNameAsString());
        VariableDeclarationExpr resultField = new VariableDeclarationExpr(modelType, "result");
        fromMapBody.addStatement(new AssignExpr(resultField, new ObjectCreationExpr(null, modelType, NodeList.nodeList()), AssignExpr.Operator.ASSIGN));
        NameExpr result = new NameExpr("result");

        ClassOrInterfaceType toMapParamsType = new ClassOrInterfaceType(null, new SimpleName(Map.class.getSimpleName()), NodeList.nodeList(new ClassOrInterfaceType(null,
                String.class.getSimpleName()), new ClassOrInterfaceType(null, Object.class.getSimpleName())));
        VariableDeclarationExpr paramsField = new VariableDeclarationExpr(toMapParamsType, PARAMS);
        toMapBody.addStatement(new AssignExpr(paramsField, new ObjectCreationExpr(null, new ClassOrInterfaceType(null,
                HashMap.class.getSimpleName() + "<>"), NodeList.nodeList()), AssignExpr.Operator.ASSIGN));

        for (ParameterDefinition paramDef : outputParamDefs.values()) {
            FieldDeclaration fd = new FieldDeclaration().addVariable(
                    new VariableDeclarator()
                            .setType(paramDef.type())
                            .setName(paramDef.sanitizedName()))
                    .addModifier(Modifier.Keyword.PRIVATE);
            modelClass.addMember(fd);
            addUserTaskParamAnnotation(fd, UserTaskParam.ParamType.OUTPUT);

            fd.createGetter();
            fd.createSetter();

            // toMap method body — original key in the map, sanitised name for field access
            MethodCallExpr putVariable = new MethodCallExpr(paramsField.getVariable(0).getNameAsExpression(), "put");
            putVariable.addArgument(new StringLiteralExpr(paramDef.originalKey()));
            putVariable.addArgument(new FieldAccessExpr(new ThisExpr(), paramDef.sanitizedName()));
            toMapBody.addStatement(putVariable);

            // fromMap method body — original key for params.get(), sanitised name for field assignment
            FieldAccessExpr field = new FieldAccessExpr(result, paramDef.sanitizedName());
            ClassOrInterfaceType type = parseClassOrInterfaceType(paramDef.type());
            fromMapBody.addStatement(new AssignExpr(field, new CastExpr(
                    type,
                    new MethodCallExpr(
                            new NameExpr(PARAMS),
                            "get")
                                    .addArgument(new StringLiteralExpr(paramDef.originalKey()))),
                    AssignExpr.Operator.ASSIGN));
        }

        toMapBody.addStatement(new ReturnStmt(paramsField.getVariable(0).getNameAsExpression()));
        Optional<MethodDeclaration> toMapMethod = modelClass.findFirst(
                MethodDeclaration.class, sl -> sl.getName().asString().equals("toMap"));
        if (toMapMethod.isPresent()) {
            MethodDeclaration toMap = toMapMethod.get();
            toMap.setBody(toMapBody);
        }

        Optional<MethodDeclaration> staticFromMethod = modelClass.findFirst(
                MethodDeclaration.class, sl -> sl.getName().asString().equals("fromMap") && sl.isStatic());
        if (staticFromMethod.isPresent()) {
            MethodDeclaration fromMethod = staticFromMethod.get();
            fromMethod.setType(modelClass.getNameAsString());
            fromMapBody.addStatement(new ReturnStmt(result));
            fromMethod.setBody(fromMapBody);
        }

        return compilationUnit;
    }

    private CompilationUnit compilationUnitModel() {
        CompilationUnit compilationUnit = parse(this.getClass().getResourceAsStream("/class-templates/TaskModelTemplate.java"));
        compilationUnit.setPackageDeclaration(packageName);
        ClassOrInterfaceDeclaration modelClass = compilationUnit.findFirst(ClassOrInterfaceDeclaration.class,
                sl1 -> true).orElseThrow(this::cannotFindClass);
        addComment(compilationUnit, "Task model");
        addUserTaskAnnotation(modelClass);

        modelClass.setName(taskModelClassSimpleName);
        modelClass.getImplementedTypes().forEach(t -> t
                .setTypeArguments(
                        NodeList.nodeList(parseClassOrInterfaceType(inputModelClassName), parseClassOrInterfaceType(
                                outputModelClassName))));
        modelClass.findAll(NameExpr.class).forEach(this::templateReplacement);
        modelClass.findAll(VariableDeclarationExpr.class).forEach(this::templateReplacement);
        modelClass.findAll(FieldDeclaration.class).forEach(this::templateReplacement);
        modelClass.findAll(ObjectCreationExpr.class).forEach(this::templateReplacement);
        modelClass.findAll(MethodDeclaration.class).forEach(this::templateReplacement);
        modelClass.findAll(Parameter.class).forEach(this::templateReplacement);

        return compilationUnit;
    }

    private void addComment(CompilationUnit unit, String prefix) {
        unit.addOrphanComment(new LineComment(prefix + " for task '" + taskName + "' in process '" + processId + "'"));
    }

    private void templateReplacement(NameExpr name) {
        name.setName(templateReplacement(name.getNameAsString()));
    }

    private <T extends Node, R extends Type> void templateReplacement(NodeWithType<T, R> expr) {
        expr.setType(templateReplacement(expr.getTypeAsString()));
    }

    private <T extends Node> void templateReplacement(NodeWithVariables<T> expr) {
        for (VariableDeclarator variable : expr.getVariables()) {
            variable.setType(templateReplacement(variable.getTypeAsString()));
        }
    }

    public String templateReplacement(String template) {
        template = template.replace("$TaskInput$", inputModelClassName);
        template = template.replace("$TaskOutput$", outputModelClassName);
        template = template.replace("$TaskModel$", taskModelClassName);
        template = template.replace("$TaskName$", taskName);
        return template;
    }
}
