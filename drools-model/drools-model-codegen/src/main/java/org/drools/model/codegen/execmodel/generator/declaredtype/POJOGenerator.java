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
package org.drools.model.codegen.execmodel.generator.declaredtype;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.drools.compiler.builder.PackageRegistryManager;
import org.drools.compiler.builder.impl.BuildResultCollector;
import org.drools.compiler.builder.impl.BuildResultCollectorImpl;
import org.drools.compiler.builder.impl.processors.CompilationPhase;
import org.drools.drl.ast.descr.AnnotationDescr;
import org.drools.drl.ast.descr.EnumDeclarationDescr;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.drl.ast.descr.TypeDeclarationDescr;
import org.drools.compiler.rule.builder.ConstraintBuilder;
import org.drools.util.TypeResolver;
import org.drools.base.definitions.InternalKnowledgePackage;
import org.drools.base.factmodel.AccessibleFact;
import org.drools.base.factmodel.GeneratedFact;
import org.drools.model.codegen.execmodel.GeneratedClassWithPackage;
import org.drools.model.codegen.execmodel.PackageModel;
import org.drools.model.codegen.execmodel.errors.DuplicatedDeclarationError;
import org.drools.model.codegen.execmodel.errors.InvalidExpressionErrorResult;
import org.drools.model.codegen.execmodel.generator.declaredtype.generator.GeneratedClassDeclaration;
import org.kie.internal.builder.KnowledgeBuilderResult;

import static org.drools.base.util.Drools.hasMvel;
import static org.drools.model.codegen.execmodel.JavaParserCompiler.compileAll;
import static org.drools.model.codegen.execmodel.generator.DrlxParseUtil.toStringLiteral;
import static org.drools.model.codegen.execmodel.generator.DslMethodNames.ADD_ANNOTATION_CALL;
import static org.drools.model.codegen.execmodel.generator.DslMethodNames.ANNOTATION_VALUE_CALL;
import static org.drools.model.codegen.execmodel.generator.DslMethodNames.TYPE_META_DATA_CALL;
import static org.drools.model.codegen.execmodel.generator.DslMethodNames.createDslTopLevelMethod;

public class POJOGenerator implements CompilationPhase {

    private final static List<Class<?>> MARKER_INTERFACES = Arrays.asList(GeneratedFact.class, AccessibleFact.class);

    private BuildResultCollector builder;
    private InternalKnowledgePackage pkg;
    private PackageDescr packageDescr;
    private PackageModel packageModel;
    private final Collection<? extends PackageDescr> allPackages;
    private final PackageRegistryManager pkgRegistryManager;

    private static final List<String> exprAnnotations = Arrays.asList("duration", "timestamp");

    /**
     * Maps a built-in type annotation's FQN to the canonical name the generated model registers it
     * under. Mirrors the known-annotation names in
     * {@code org.drools.modelcompiler.util.TypeDeclarationUtil}.
     */
    private static final Map<String, String> BUILTIN_TYPE_ANNOTATIONS_BY_FQN = Map.of(
            "org.kie.api.definition.type.Role", "role",
            "org.kie.api.definition.type.Duration", "duration",
            "org.kie.api.definition.type.Timestamp", "timestamp",
            "org.kie.api.definition.type.Expires", "expires",
            "org.kie.api.definition.type.PropertyReactive", "propertyReactive",
            "org.kie.api.definition.type.ClassReactive", "classReactive",
            "org.kie.api.definition.type.PropertyChangeSupport", "propertyChangeSupport");

    public POJOGenerator(BuildResultCollector builder, InternalKnowledgePackage pkg, PackageDescr packageDescr, PackageModel packageModel,
                         Collection<? extends PackageDescr> allPackages, PackageRegistryManager pkgRegistryManager) {
        this.builder = builder;
        this.pkg = pkg;
        this.packageDescr = packageDescr;
        this.packageModel = packageModel;
        this.allPackages = allPackages;
        this.pkgRegistryManager = pkgRegistryManager;
        packageModel.addImports(pkg.getTypeResolver().getImports());
    }

    public POJOGenerator(BuildResultCollector builder, InternalKnowledgePackage pkg, PackageDescr packageDescr, PackageModel packageModel) {
        this(builder, pkg, packageDescr, packageModel, null, null);
    }

    public POJOGenerator(InternalKnowledgePackage pkg, PackageDescr packageDescr, PackageModel packageModel) {
        this(new BuildResultCollectorImpl(), pkg, packageDescr, packageModel, null, null);
    }

    public POJOGenerator(InternalKnowledgePackage pkg, PackageDescr packageDescr, PackageModel packageModel,
                         Collection<? extends PackageDescr> allPackages, PackageRegistryManager pkgRegistryManager) {
        this(new BuildResultCollectorImpl(), pkg, packageDescr, packageModel, allPackages, pkgRegistryManager);
    }

    public static Map<String, Class<?>> compileType(BuildResultCollector resultAccumulator,
                                                    ClassLoader packageClassLoader,
                                                    List<GeneratedClassWithPackage> classesWithPackage) {
        return compileAll(resultAccumulator, packageClassLoader, classesWithPackage);
    }

    private void findPOJOorGenerate() {
        TypeResolver typeResolver = pkg.getTypeResolver();
        Set<String> generatedPojos = new HashSet<>();
        for (TypeDeclarationDescr typeDescr : packageDescr.getTypeDeclarations()) {
            if (!generatedPojos.add(typeDescr.getFullTypeName())) {
                builder.addBuilderResult( new DuplicatedDeclarationError(typeDescr.getFullTypeName()) );
                continue;
            }
            try {
                Class<?> type = typeResolver.resolveType(typeDescr.getFullTypeName());
                checkRedeclarationCompatibility(type, typeDescr);
                processTypeMetadata(type, typeDescr.getAnnotations());
            } catch (ClassNotFoundException e) {
                createPOJO(typeDescr);
            }
        }

        for (EnumDeclarationDescr enumDescr : packageDescr.getEnumDeclarations()) {
            try {
                Class<?> type = typeResolver.resolveType(enumDescr.getFullTypeName());
                processTypeMetadata(type, enumDescr.getAnnotations());
            } catch (ClassNotFoundException e) {
                TypeDeclaration generatedEnum = new EnumGenerator()
                        .generate(enumDescr);
                packageModel.addGeneratedPOJO(generatedEnum);
                addTypeMetadata(enumDescr.getTypeName());
            }
        }
    }

    @Override
    public void process() {
        findPOJOorGenerate();
    }

    @Override
    public Collection<? extends KnowledgeBuilderResult> getResults() {
        return builder.getAllResults();
    }

    static class SafeTypeResolver implements org.drools.model.codegen.execmodel.generator.declaredtype.api.TypeResolver {

        private final TypeResolver typeResolver;

        public SafeTypeResolver(TypeResolver typeResolver) {
            this.typeResolver = typeResolver;
        }

        @Override
        public Optional<Class<?>> resolveType(String className) {
            try {
                return Optional.ofNullable(this.typeResolver.resolveType(className));
            } catch(ClassNotFoundException e) {
                return Optional.empty();
            }
        }
    }

    private void createPOJO(TypeDeclarationDescr typeDescr) {
        SafeTypeResolver typeResolver = new SafeTypeResolver(pkg.getTypeResolver());
        DescrTypeDefinition descrDeclaredTypeDefinition = new DescrTypeDefinition(packageDescr, typeDescr, typeResolver, allPackages, pkgRegistryManager);
        descrDeclaredTypeDefinition.getErrors().forEach(builder::addBuilderResult);

        // Implemented types should be probably in
        ClassOrInterfaceDeclaration generatedClass = new GeneratedClassDeclaration(descrDeclaredTypeDefinition,
                                                                                   MARKER_INTERFACES)
                .toClassDeclaration();
        packageModel.addGeneratedPOJO(generatedClass);
        
        // Add type metadata including custom annotations
        String fullTypeName = pkg.getName() + "." + typeDescr.getTypeName();
        MethodCallExpr typeMetaDataCall = registerTypeMetaData(fullTypeName);
        
        // Add custom metadata from non-defined annotations
        Map<String, Object> classMetaData = descrDeclaredTypeDefinition.getClassMetaData();
        for (Map.Entry<String, Object> entry : classMetaData.entrySet()) {
            typeMetaDataCall = new MethodCallExpr(typeMetaDataCall, ADD_ANNOTATION_CALL);
            typeMetaDataCall.addArgument(toStringLiteral(entry.getKey()));
            addAnnotationValueIfNotNull(typeMetaDataCall, entry.getValue());
        }

        // Add field metadata
        for (DescrFieldDefinition field : descrDeclaredTypeDefinition.getFields()) {
            Map<String, Object> fieldMetaData = field.getFieldMetaData();
            if (!fieldMetaData.isEmpty()) {
                // First, add the field to the TypeMetaData
                typeMetaDataCall = new MethodCallExpr(typeMetaDataCall, "withField");
                typeMetaDataCall.addArgument(toStringLiteral(field.getFieldName()));

                // Then add each annotation as field metadata
                for (Map.Entry<String, Object> entry : fieldMetaData.entrySet()) {
                    typeMetaDataCall = new MethodCallExpr(typeMetaDataCall, "withFieldAnnotation");
                    typeMetaDataCall.addArgument(toStringLiteral(field.getFieldName()));
                    typeMetaDataCall.addArgument(toStringLiteral(entry.getKey()));
                    addAnnotationValueIfNotNull(typeMetaDataCall, entry.getValue());
                }
            }
        }
        
        packageModel.addTypeMetaDataExpressions(typeMetaDataCall);
    }

    private void addTypeMetadata(String typeName) {
        packageModel.addTypeMetaDataExpressions(registerTypeMetaData(pkg.getName() + "." + typeName));
    }

    private void checkRedeclarationCompatibility(Class<?> type, TypeDeclarationDescr typeDescr) {
        if (!typeDescr.getFields().isEmpty() && type.getDeclaredFields().length != typeDescr.getFields().size()) {
            builder.addBuilderResult( new InvalidExpressionErrorResult("Wrong redeclaration of type " + typeDescr.getFullTypeName()) );
        }
    }

    private void processTypeMetadata(Class<?> type, Collection<AnnotationDescr> annotations) {
        MethodCallExpr typeMetaDataCall = registerTypeMetaData(type.getCanonicalName());

        for (AnnotationDescr ann : annotations) {
            typeMetaDataCall = new MethodCallExpr(typeMetaDataCall, ADD_ANNOTATION_CALL);
            typeMetaDataCall.addArgument(toStringLiteral(typeMetadataAnnotationName(ann)));
            for (Map.Entry<String, Object> entry : ann.getValueMap().entrySet()) {
                MethodCallExpr annotationValueCall = createDslTopLevelMethod(ANNOTATION_VALUE_CALL);
                annotationValueCall.addArgument(toStringLiteral(entry.getKey()));
                String expr = entry.getValue().toString();
                if (hasMvel() && exprAnnotations.contains(ann.getName()) && ConstraintBuilder.get().analyzeExpression(type, expr) == null) {
                    builder.addBuilderResult(new InvalidExpressionErrorResult("Unable to analyze expression '" + expr + "' for " + ann.getName() + " attribute"));
                }
                annotationValueCall.addArgument(quote(expr));
                typeMetaDataCall.addArgument(annotationValueCall);
            }
        }

        packageModel.addTypeMetaDataExpressions(typeMetaDataCall);
    }

    private static MethodCallExpr registerTypeMetaData(String className) {
        MethodCallExpr typeMetaDataCall = createDslTopLevelMethod(TYPE_META_DATA_CALL);
        typeMetaDataCall.addArgument(className + ".class");
        return typeMetaDataCall;
    }

    /**
     * The name a type annotation is registered under in the generated model. {@code AnnotationNormalizer}
     * resolved the annotation's identity before codegen, so when it resolved to a built-in the canonical
     * (lowercase) name is emitted and {@code TypeDeclarationUtil} recognizes it no matter which spelling
     * the DRL used ({@code @Role} or {@code @role}). Anything else keeps the name as written, so a custom
     * annotation whose simple name collides with a built-in (e.g. an imported {@code com.acme.ClassReactive})
     * stays custom metadata.
     */
    private static String typeMetadataAnnotationName(AnnotationDescr ann) {
        String canonical = ann.getFullyQualifiedName() == null
                ? null
                : BUILTIN_TYPE_ANNOTATIONS_BY_FQN.get(ann.getFullyQualifiedName());
        return canonical != null ? canonical : ann.getName();
    }

    private static void addAnnotationValueIfNotNull(MethodCallExpr typeMetaDataCall, Object value) {
        if (value != null) {
            MethodCallExpr annotationValueCall = createDslTopLevelMethod(ANNOTATION_VALUE_CALL);
            annotationValueCall.addArgument(toStringLiteral("value"));
            annotationValueCall.addArgument(quote(value.toString()));
            typeMetaDataCall.addArgument(annotationValueCall);
        }
    }

    public static String quote(String str) {
        return "\"" + str.replace("\"", "\\\"") + "\"";
    }
}
