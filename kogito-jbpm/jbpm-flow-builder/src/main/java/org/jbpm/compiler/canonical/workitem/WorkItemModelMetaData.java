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
package org.jbpm.compiler.canonical.workitem;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.core.datatype.DataType;
import org.jbpm.process.core.datatype.DataTypeResolver;
import org.jbpm.util.PatternConstants;
import org.jbpm.workflow.core.node.HumanTaskNode;
import org.jbpm.workflow.core.node.WorkItemNode;
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
import static org.kie.kogito.internal.utils.ConversionUtils.sanitizeJavaName;

public abstract class WorkItemModelMetaData {

    protected static final Logger LOGGER = LoggerFactory.getLogger(WorkItemModelMetaData.class);

    protected static final String TASK_INPUT_CLASS_SUFFIX = "TaskInput";
    protected static final String TASK_OUTPUT_CLASS_SUFFIX = "TaskOutput";
    protected static final String TASK_MODEL_CLASS_SUFFIX = "TaskModel";
    protected static final String TASK_NAME = "TaskName";
    protected static final String WORK_ITEM = "workItem";
    protected static final String PARAMS = "params";

    public record ParameterDefinition(String originalKey, String sanitizedName, String type) {
    }

    protected final String packageName;
    protected final VariableScope processVariableScope;
    protected final String taskName;
    protected final String processId;

    protected final String inputModelClassName;
    protected final String inputModelClassSimpleName;
    protected final String outputModelClassName;
    protected final String outputModelClassSimpleName;
    protected final String taskModelClassName;
    protected final String taskModelClassSimpleName;

    protected final Map<String, ParameterDefinition> inputParamDefs = new LinkedHashMap<>();
    protected final Map<String, ParameterDefinition> outputParamDefs = new LinkedHashMap<>();

    protected WorkItemModelMetaData(String packageName, VariableScope processVariableScope, String taskName, String classPrefix, String processId) {
        this.packageName = packageName;
        this.processVariableScope = processVariableScope;
        this.taskName = taskName;
        this.processId = processId;

        this.inputModelClassSimpleName = classPrefix + "_" + TASK_INPUT_CLASS_SUFFIX;
        this.inputModelClassName = packageName + '.' + inputModelClassSimpleName;

        this.outputModelClassSimpleName = classPrefix + "_" + TASK_OUTPUT_CLASS_SUFFIX;
        this.outputModelClassName = packageName + '.' + outputModelClassSimpleName;

        this.taskModelClassSimpleName = classPrefix + "_" + TASK_MODEL_CLASS_SUFFIX;
        this.taskModelClassName = packageName + '.' + taskModelClassSimpleName;
    }

    protected WorkItemModelMetaData(String packageName, VariableScope processVariableScope, String taskName, String classPrefix, String processId,
            Map<String, ParameterDefinition> inputParamDefs, Map<String, ParameterDefinition> outputParamDefs) {
        this(packageName, processVariableScope, taskName, classPrefix, processId);
        if (inputParamDefs != null) {
            this.inputParamDefs.putAll(inputParamDefs);
        }
        if (outputParamDefs != null) {
            this.outputParamDefs.putAll(outputParamDefs);
        }
    }

    public static Map<String, ParameterDefinition> extractInputParameters(WorkItemNode workItemNode, VariableScope processVariableScope,
            VariableScope nodeVariableScope, boolean isHumanTask) {
        Map<String, ParameterDefinition> inputParamDefs = new LinkedHashMap<>();
        Map<String, String> inputTypes = workItemNode.getIoSpecification().getInputTypes();

        for (Entry<String, String> entry : workItemNode.getIoSpecification().getInputMapping().entrySet()) {
            if (isHumanTask && HumanTaskNode.TASK_PARAMETERS.contains(entry.getKey())) {
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
                LOGGER.warn("{} input variable '{}' is declared as java.lang.Object. " +
                        "Object type variables are persisted using Java serialization. " +
                        "Make sure the stored value implements java.io.Serializable.",
                        isHumanTask ? "User task" : "Work item", entry.getKey());
            }

            String fieldName = sanitizeJavaName(entry.getKey());
            inputParamDefs.put(fieldName, new ParameterDefinition(entry.getKey(), fieldName, variableType));
        }

        for (Entry<String, Object> entry : workItemNode.getWork().getParameters().entrySet()) {
            if (entry.getValue() == null || (isHumanTask && HumanTaskNode.TASK_PARAMETERS.contains(entry.getKey()))) {
                continue;
            }

            String fieldName = sanitizeJavaName(entry.getKey());
            inputParamDefs.put(fieldName, new ParameterDefinition(entry.getKey(), fieldName, entry.getValue().getClass().getCanonicalName()));
        }

        return inputParamDefs;
    }

    public static Map<String, ParameterDefinition> extractOutputParameters(WorkItemNode workItemNode, VariableScope processVariableScope,
            VariableScope nodeVariableScope, boolean isHumanTask) {
        Map<String, ParameterDefinition> outputParamDefs = new LinkedHashMap<>();
        Map<String, String> outputTypes = workItemNode.getIoSpecification().getOutputTypes();

        for (Entry<String, String> entry : workItemNode.getIoSpecification().getOutputMappingBySources().entrySet()) {
            if (entry.getValue() == null || (isHumanTask && HumanTaskNode.TASK_PARAMETERS.contains(entry.getKey()))) {
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
                LOGGER.warn("{} output variable '{}' is declared as java.lang.Object. " +
                        "Object type variables are persisted using Java serialization. " +
                        "Make sure the stored value implements java.io.Serializable.",
                        isHumanTask ? "User task" : "Work item", entry.getKey());
            }

            String fieldName = sanitizeJavaName(entry.getKey());
            outputParamDefs.put(fieldName, new ParameterDefinition(entry.getKey(), fieldName, variableType));
        }

        return outputParamDefs;
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

    public abstract boolean isAdHoc();

    public abstract SwitchEntry getModelSwitchEntry();

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
