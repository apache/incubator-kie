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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.drools.util.StringUtils;
import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.workflow.core.impl.WorkflowProcessImpl;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.kie.api.definition.process.Node;
import org.kie.api.definition.process.WorkflowProcess;
import org.kie.kogito.ProcessInput;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcess;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import static com.github.javaparser.StaticJavaParser.parse;
import static com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType;
import static org.kie.kogito.internal.utils.ConversionUtils.sanitizeClassName;

public class ProcessToExecModelGenerator {

    public static final ProcessToExecModelGenerator INSTANCE = new ProcessToExecModelGenerator(
            ProcessToExecModelGenerator.class.getClassLoader());

    private static final String PROCESS_CLASS_SUFFIX = "Process";
    private static final String MODEL_CLASS_SUFFIX = "Model";
    private static final String PROCESS_TEMPLATE_FILE = "ProcessTemplate.java";
    private static final String PROCESS_NAME_PARAM = "processName";

    private final ProcessVisitor processVisitor;
    private ClassLoader contextClassLoader;
    private String classTemplate;

    public ProcessToExecModelGenerator(ClassLoader contextClassLoader) {
        this(PROCESS_TEMPLATE_FILE, contextClassLoader);
    }

    public ProcessToExecModelGenerator(String classTemplate, ClassLoader contextClassLoader) {
        this.classTemplate = classTemplate;
        this.contextClassLoader = contextClassLoader;
        this.processVisitor = new ProcessVisitor(contextClassLoader);
    }

    public ProcessMetaData generate(WorkflowProcess process) {
        CompilationUnit parsedClazzFile = parse(TemplateHelper.findTemplate(contextClassLoader, this.classTemplate));
        if (process instanceof RuleFlowProcess ruleFlowProcess) {
            List<String> currentImports = parsedClazzFile.getImports().stream().map(e -> e.getNameAsString()).toList();
            Set<String> processImports = new HashSet<>(ruleFlowProcess.getImports());
            processImports.removeAll(currentImports);
            processImports.forEach(i -> parsedClazzFile.addImport(new ImportDeclaration(i, false, false)));
        }

        parsedClazzFile.setPackageDeclaration(process.getPackageName());
        Optional<ClassOrInterfaceDeclaration> processClazzOptional = parsedClazzFile.findFirst(
                ClassOrInterfaceDeclaration.class,
                sl -> true);

        String extractedProcessId = extractProcessId(process.getId());

        if (!processClazzOptional.isPresent()) {
            throw new NoSuchElementException("Cannot find class declaration in the template");
        }
        ClassOrInterfaceDeclaration processClazz = processClazzOptional.get();
        processClazz.setName(sanitizeClassName(extractedProcessId + PROCESS_CLASS_SUFFIX));
        String packageName = parsedClazzFile.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse(null);
        ProcessMetaData metadata =
                new ProcessMetaData(process.getId(), extractedProcessId, process.getName(), process.getVersion(),
                        packageName, processClazz.getNameAsString());

        Optional<MethodDeclaration> processMethod = parsedClazzFile.findFirst(MethodDeclaration.class, sl -> sl
                .getName()
                .asString()
                .equals("process"));

        processVisitor.visitProcess(process, processMethod.get(), metadata);

        metadata.setGeneratedClassModel(parsedClazzFile);
        return metadata;
    }

    public ModelMetaData generateModel(WorkflowProcess process) {
        String packageName = process.getPackageName();
        String name = extractModelClassName(process.getId());
        VariableScope variableScope = getVariableScope(process);
        String toModelClassName = extractModelClassName(process.getId()) + "Output";
        return new ModelMetaData(process.getId(),
                packageName,
                name,
                ((KogitoWorkflowProcess) process).getVisibility(),
                VariableDeclarations.of(variableScope),
                false,
                "/class-templates/ModelTemplate.java",
                new AddMethodConsumer("toModel", toModelClassName,
                        VariableDeclarations.ofOutput(variableScope), true));
    }

    public ModelMetaData generateInputModel(WorkflowProcess process) {
        String packageName = process.getPackageName();
        String modelName = extractModelClassName(process.getId());
        String name = modelName + "Input";
        VariableDeclarations inputVars = VariableDeclarations.ofInput(getVariableScope(process));
        return new ModelMetaData(process.getId(),
                packageName, name,
                ((KogitoWorkflowProcess) process).getVisibility(),
                inputVars,
                true,
                "/class-templates/ModelNoIDTemplate.java",
                new AddMethodConsumer("toModel", modelName, inputVars, false),
                new AddProcessAnnotation(process.getId()));
    }

    public ModelMetaData generateOutputModel(WorkflowProcess process) {
        String packageName = process.getPackageName();
        String modelName = extractModelClassName(process.getId());
        String name = modelName + "Output";
        VariableScope variableScope = getVariableScope(process);
        return new ModelMetaData(process.getId(),
                packageName,
                name,
                ((KogitoWorkflowProcess) process).getVisibility(),
                VariableDeclarations.ofOutput(variableScope),
                true,
                "/class-templates/ModelTemplate.java",
                new AddMethodConsumer("toModel", modelName,
                        VariableDeclarations.ofOutput(variableScope), true));
    }

    private static VariableScope getVariableScope(WorkflowProcess process) {
        return (VariableScope) ((org.jbpm.process.core.Process) process).getDefaultContext(VariableScope.VARIABLE_SCOPE);
    }

    private static class AddMethodConsumer implements Consumer<CompilationUnit> {

        private String methodName;
        private String returnClassName;
        private VariableDeclarations vars;
        private boolean includeId;

        public AddMethodConsumer(String methodName, String returnClassName, VariableDeclarations vars,
                boolean includeId) {
            this.methodName = methodName;
            this.returnClassName = returnClassName;
            this.vars = vars;
            this.includeId = includeId;
        }

        @Override
        public void accept(CompilationUnit cu) {
            Optional<ClassOrInterfaceDeclaration> clazz = cu.findFirst(ClassOrInterfaceDeclaration.class);
            if (!clazz.isPresent()) {
                throw new NoSuchElementException("Cannot find class declaration in the template");
            }
            ClassOrInterfaceType type = parseClassOrInterfaceType(returnClassName);
            final String resultVarName = "result";

            //Setting the Class type in the interface implementation
            clazz.get().findAll(ClassOrInterfaceType.class)
                    .stream()
                    .filter(t -> t.getNameAsString().equals("$modelClass$"))
                    .forEach(t -> t.setName(returnClassName));

            //Adding the Method itself
            MethodDeclaration method = clazz.get()
                    .addMethod(methodName, Modifier.Keyword.PUBLIC)
                    .setType(type)
                    .addAnnotation(Override.class);
            BlockStmt body = new BlockStmt();
            VariableDeclarationExpr returnVar = new VariableDeclarationExpr(type, resultVarName);
            body.addStatement(new AssignExpr(returnVar, new ObjectCreationExpr(null, type, NodeList.nodeList()),
                    AssignExpr.Operator.ASSIGN));
            NameExpr returnName = new NameExpr(resultVarName);
            // fill id
            if (includeId) {
                body.addStatement(new MethodCallExpr(returnName, "setId").addArgument(new MethodCallExpr(null,
                        "getId")));
            }
            for (Variable var : vars.getTypes().values()) {
                final String fieldName = StringUtils.ucFirst(var.getSanitizedName());
                body.addStatement(new MethodCallExpr(returnName, "set" + fieldName).addArgument(new MethodCallExpr(null,
                        "get" +
                                fieldName)));
            }
            body.addStatement(new MethodCallExpr(returnName, "addModifiedFields",
                    NodeList.nodeList(new MethodCallExpr(null, "getModifiedFields"))));
            body.addStatement(new ReturnStmt(returnName));
            method.setBody(body);
        }
    }

    private static class AddProcessAnnotation implements Consumer<CompilationUnit> {

        private final String processId;

        public AddProcessAnnotation(String processId) {
            this.processId = processId;
        }

        @Override
        public void accept(CompilationUnit cu) {
            ClassOrInterfaceDeclaration clazz = cu.findFirst(ClassOrInterfaceDeclaration.class)
                    .orElseThrow(() -> new NoSuchElementException("Cannot find class declaration in the template"));

            clazz.addAndGetAnnotation(ProcessInput.class)
                    .addPair(PROCESS_NAME_PARAM, new StringLiteralExpr(processId));
        }
    }

    public static String extractModelClassName(String processId) {
        return sanitizeClassName(extractProcessId(processId) + MODEL_CLASS_SUFFIX);
    }

    public List<WorkItemModelMetaData> generateWorkItemModel(WorkflowProcess process) {
        String packageName = process.getPackageName();
        List<WorkItemModelMetaData> workItemTaskModels = new ArrayList<>();

        VariableScope variableScope = (VariableScope) ((org.jbpm.process.core.Process) process).getDefaultContext(
                VariableScope.VARIABLE_SCOPE);

        for (Node node : ((WorkflowProcessImpl) process).getNodesRecursively()) {
            if (node instanceof WorkItemNode workItemNode) {
                VariableScope nodeVariableScope = (VariableScope) ((ContextContainer) workItemNode
                        .getParentContainer()).getDefaultContext(VariableScope.VARIABLE_SCOPE);
                if (nodeVariableScope == null) {
                    nodeVariableScope = variableScope;
                }
                workItemTaskModels.add(new WorkItemModelMetaData(packageName, variableScope, nodeVariableScope,
                        workItemNode, process.getId()));
            }
        }

        return workItemTaskModels;
    }

    public static String extractProcessId(String processId) {
        return processId.contains(".") ? processId.substring(processId.lastIndexOf('.') + 1) : processId;
    }
}
