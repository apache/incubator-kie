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
package org.jbpm.tools.maven;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.maven.plugin.MojoExecutionException;
import org.drools.io.FileSystemResource;
import org.jbpm.bpmn2.xml.BPMNDISemanticModule;
import org.jbpm.bpmn2.xml.BPMNExtensionsSemanticModule;
import org.jbpm.bpmn2.xml.BPMNSemanticModule;
import org.jbpm.compiler.canonical.ModelMetaData;
import org.jbpm.compiler.canonical.ProcessMetaData;
import org.jbpm.compiler.canonical.ProcessToExecModelGenerator;
import org.jbpm.compiler.canonical.TemplateHelper;
import org.jbpm.compiler.canonical.TriggerMetaData;
import org.jbpm.compiler.xml.XmlProcessReader;
import org.jbpm.compiler.xml.core.SemanticModules;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.Node.TreeTraversal;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.Printer;

public class ProcessCodeGenerationSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessCodeGenerationSupport.class);

    private SemanticModules bpmnSemanticModules;

    private ProcessToExecModelGenerator processCodeGenerator;

    private Printer printer;

    private Path rootSourceFolder;

    private Path rootOutputFolder;

    public ProcessCodeGenerationSupport(Path sourceFolder, Path outputFolder, ClassLoader classLoader) {
        this.printer = new DefaultPrettyPrinter();
        this.bpmnSemanticModules = new SemanticModules();
        this.bpmnSemanticModules.addSemanticModule(new BPMNSemanticModule());
        this.bpmnSemanticModules.addSemanticModule(new BPMNExtensionsSemanticModule());
        this.bpmnSemanticModules.addSemanticModule(new BPMNDISemanticModule());
        this.processCodeGenerator = new ProcessToExecModelGenerator("StaticProcessTemplate.java", classLoader);
        this.rootSourceFolder = sourceFolder;
        this.rootOutputFolder = outputFolder;
    }

    public void execute() throws MojoExecutionException {

        try {
            if (!Files.exists(rootSourceFolder)) {
                LOG.info("Workflow folder {} does not exists! Skipping", rootSourceFolder);
                return;
            }

            List<Path> sources = findWorkflowFiles(rootSourceFolder);
            if (sources.isEmpty()) {
                LOG.info("Workflow folder {} is empty! Skipping", rootSourceFolder);
                return;
            }

            File outputJavaDirectory = rootOutputFolder.toFile();
            outputJavaDirectory.mkdirs();
            if (!outputJavaDirectory.exists()) {
                throw new MojoExecutionException("Could not create source directory! " + outputJavaDirectory);
            }

            List<JavaCodeResult> outcome = sources.stream().map(this::generateJavaCode).toList();
            List<JavaCodeResult> errors = outcome.stream().filter(JavaCodeResult::notValid).toList();
            errors.forEach(e -> {
                LOG.info("Error {}", e.path());
            });
            LOG.info("Number of compiled files: {}, Number of errors: {}", outcome.size() - errors.size(), errors.size());
        } catch (IOException e) {
            throw new MojoExecutionException("Could not generate Java source code!", e);
        }
    }

    private List<Path> findWorkflowFiles(Path bpmnSources) throws IOException {
        Predicate<Path> isBpmn2 = e -> e.getFileName().toString().endsWith(".bpmn2") || e.getFileName().toString().endsWith(".bpmn");
        return Files.walk(bpmnSources).filter(Files::isRegularFile).filter(isBpmn2).toList();
    }

    record JavaCodeResult(Path path, boolean valid) {

        boolean notValid() {
            return !valid();
        }
    }

    private JavaCodeResult generateJavaCode(Path workflow) {
        FileSystemResource resource = new FileSystemResource(workflow.toFile());
        try (Reader reader = resource.getReader()) {
            XmlProcessReader xmlReader = new XmlProcessReader(bpmnSemanticModules, Thread.currentThread().getContextClassLoader());
            List<KogitoWorkflowProcess> processes = xmlReader.read(reader).stream().map(KogitoWorkflowProcess.class::cast).toList();
            for (KogitoWorkflowProcess process : processes) {
                this.generateJavaCode(process);
            }
            return new JavaCodeResult(workflow, true);
        } catch (Exception e) {
            LOG.error("problem found when trying to process {}", workflow, e);
            return new JavaCodeResult(workflow, false);
        }

    }

    private void generateJavaCode(KogitoWorkflowProcess process) throws IOException {
        ProcessMetaData metadata = processCodeGenerator.generate(process);
        ModelMetaData modelMetadata = processCodeGenerator.generateModel(process);
        ModelMetaData inputMdelMetadata = processCodeGenerator.generateInputModel(process);
        ModelMetaData outputModelMetadata = processCodeGenerator.generateOutputModel(process);

        // process
        CompilationUnit processUnit = metadata.getGeneratedClassModel();
        addMembers(metadata, processUnit);

        RuleFlowProcess ruleFlowProcess = (RuleFlowProcess) process;
        for (String importClass : ruleFlowProcess.getImports()) {
            processUnit.addImport(importClass);
        }

        ClassOrInterfaceDeclaration processType = processUnit.findFirst(ClassOrInterfaceDeclaration.class).get();
        ClassOrInterfaceType modelType = StaticJavaParser.parseClassOrInterfaceType(modelMetadata.getModelClassName());
        processType.getExtendedTypes(0).setTypeArguments(modelType);
        processType.findAll(ObjectCreationExpr.class, e -> "BpmnProcessInstance".equals(e.getType().getNameAsString())).forEach(e -> e.setType(processType.getNameAsString() + "Instance"));
        processType.findAll(ObjectCreationExpr.class, e -> "ThisIsJustTemplate".equals(e.getType().getNameAsString())).forEach(e -> e.setType(processType.getNameAsString()));
        processType.findAll(ObjectCreationExpr.class, e -> "BpmnVariables".equals(e.getType().getNameAsString())).forEach(e -> e.setType(modelType.getNameAsString()));
        processType.findAll(VariableDeclarationExpr.class, e -> "BpmnVariables".equals(e.getVariable(0).getTypeAsString())).forEach(e -> {
            if (e.getVariables().isNonEmpty()) {
                e.getVariable(0).setType(modelType.getNameWithScope());
            }
        });
        processType.findAll(MethodDeclaration.class, TreeTraversal.DIRECT_CHILDREN).forEach(e -> {
            if ("BpmnVariables".equals(e.getTypeAsString())) {
                e.setType(modelType);
            }
            e.getParameterByType("BpmnVariables").ifPresent(t -> t.setType(modelType));

            if (!(e.getType() instanceof ClassOrInterfaceType)) {
                return;
            }

            ClassOrInterfaceType returnType = (ClassOrInterfaceType) e.getType();
            if ("org.kie.kogito.process.Process".equals(returnType.getNameWithScope())) {
                returnType.setTypeArguments(modelType);
            } else if ("ProcessInstance".equals(returnType.getNameAsString())) {
                returnType.setTypeArguments(modelType);
            }

        });
        processType.findAll(ConstructorDeclaration.class).forEach(e -> {
            e.setName(processType.getNameAsString());
        });

        // process instance
        CompilationUnit instance = StaticJavaParser.parse(TemplateHelper.findTemplate("ModelProcessInstance.java"));
        instance.setPackageDeclaration(processUnit.getPackageDeclaration().get());
        ClassOrInterfaceDeclaration instanceType = instance.findFirst(ClassOrInterfaceDeclaration.class).get();
        instanceType.getExtendedTypes(0).setTypeArguments(modelType);
        instanceType.findAll(ClassOrInterfaceType.class, e -> "AbstractProcess".equals(e.getNameAsString())).forEach(e -> e.setTypeArguments(modelType));
        instanceType.setName(processType.getNameAsString() + "Instance");
        instanceType.findAll(ConstructorDeclaration.class).forEach(e -> {
            e.setName(instanceType.getNameAsString());
            e.getParameterByType("BpmnVariables").ifPresent(t -> t.setType(modelType));

        });
        instanceType.findAll(MethodDeclaration.class, TreeTraversal.DIRECT_CHILDREN).forEach(e -> {
            if ("BpmnVariables".equals(e.getTypeAsString())) {
                e.setType(modelType);
            }
            e.getParameterByType("BpmnVariables").ifPresent(t -> t.setType(modelType));
        });

        // write to disk
        writeCompilationUnit(processUnit);
        writeCompilationUnit(instance);
        writeCompilationUnit(modelMetadata.generateUnit());
        writeCompilationUnit(inputMdelMetadata.generateUnit());
        writeCompilationUnit(outputModelMetadata.generateUnit());
        for (CompilationUnit handler : metadata.getGeneratedHandlers().values()) {
            writeCompilationUnit(handler);
        }
        for (CompilationUnit nodeBuilder : metadata.getNodeBuilderClasses()) {
            writeCompilationUnit(nodeBuilder);
        }
    }

    private void writeCompilationUnit(CompilationUnit unit) throws IOException {
        Optional<PackageDeclaration> packageDeclaration = unit.getPackageDeclaration();
        String folder = "";
        if (packageDeclaration.isPresent()) {
            folder = packageDeclaration.get().getNameAsString().replaceAll("\\.", "/");
        }

        Path outputFolder = Paths.get(rootOutputFolder.toString(), folder);
        outputFolder.toFile().mkdirs();

        String modelSource = printer.print(unit);
        ClassOrInterfaceDeclaration modelType = unit.findFirst(ClassOrInterfaceDeclaration.class).get();
        LOG.debug("{} generated", modelType.getNameAsString());
        Files.write(Paths.get(outputFolder.toString(), modelType.getName().asString() + ".java"), modelSource.getBytes());
    }

    private void addMembers(ProcessMetaData processMetaData, CompilationUnit compilationUnit) {
        ClassOrInterfaceDeclaration cls = compilationUnit.findFirst(ClassOrInterfaceDeclaration.class).get();
        if (!processMetaData.getTriggers().isEmpty()) {

            for (TriggerMetaData trigger : processMetaData.getTriggers()) {
                // add message produces as field
                if (trigger.getType().equals(TriggerMetaData.TriggerType.ProduceMessage)) {
                    ClassOrInterfaceType type = StaticJavaParser.parseClassOrInterfaceType(org.kie.kogito.event.impl.MessageProducer.class.getName() + "<" + trigger.getDataType() + ">");
                    String producerFieldName = "producer_" + trigger.getOwnerId();
                    String producerFieldNameCapitalized = "Producer_" + trigger.getOwnerId();

                    FieldDeclaration producerFieldDeclaration = new FieldDeclaration()
                            .addVariable(new VariableDeclarator(type, producerFieldName));
                    cls.addMember(producerFieldDeclaration);

                    MethodDeclaration setMethod = new MethodDeclaration();
                    setMethod.addParameter(type, producerFieldName);
                    setMethod.setModifier(Keyword.PUBLIC, true);
                    setMethod.setName("set" + producerFieldNameCapitalized);
                    setMethod.setType(StaticJavaParser.parseType("void"));
                    AssignExpr assignExpr = new AssignExpr(
                            new FieldAccessExpr(new ThisExpr(), producerFieldName),
                            new NameExpr(producerFieldName),
                            AssignExpr.Operator.ASSIGN);

                    BlockStmt body = new BlockStmt();
                    body.addStatement(assignExpr);
                    setMethod.setBody(body);

                    cls.addMember(setMethod);
                }
            }
        }
    }
}
