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
package org.drools.compiler.builder.impl;

import java.util.Map;

import org.drools.base.definitions.InternalKnowledgePackage;
import org.drools.base.rule.TypeDeclaration;
import org.drools.compiler.compiler.AnalysisResult;
import org.drools.compiler.compiler.BoundIdentifiers;
import org.drools.compiler.compiler.Dialect;
import org.drools.compiler.compiler.PackageRegistry;
import org.drools.compiler.compiler.TypeDeclarationError;
import org.drools.compiler.rule.builder.PackageBuildContext;
import org.drools.drl.ast.descr.AbstractClassTypeDeclarationDescr;
import org.drools.drl.ast.descr.BaseDescr;
import org.kie.internal.builder.conf.PropertySpecificOption;

public class TypeDeclarationConfigurator {

    protected final TypeDeclarationContext context;
    private final BuildResultCollector results;

    public TypeDeclarationConfigurator( TypeDeclarationContext context, BuildResultCollector results ) {
        this.context = context;
        this.results = results;
    }

    public void finalizeConfigurator(TypeDeclaration type, AbstractClassTypeDeclarationDescr typeDescr, PackageRegistry pkgRegistry, Map<String, PackageRegistry> pkgRegistryMap, ClassHierarchyManager hierarchyManager ) {
        // prefer definitions where possible
        if ( type.getNature() == TypeDeclaration.Nature.DEFINITION ) {
            hierarchyManager.addDeclarationToPackagePreservingOrder( type, typeDescr, pkgRegistry.getPackage(), pkgRegistryMap );
        } else {
            TypeDeclaration oldType = pkgRegistry.getPackage().getTypeDeclaration( type.getTypeName() );
            if ( oldType == null ) {
                pkgRegistry.getPackage().addTypeDeclaration( type );
            } else {
                // the existing declaration is kept, so apply the annotations of this redeclaration to it
                TypeDeclarationFactory.processAnnotations( typeDescr, oldType, context.getBuilderConfiguration().getOption( PropertySpecificOption.KEY ) );
            }
        }
    }


    public boolean wireFieldAccessors( PackageRegistry pkgRegistry,
                                       AbstractClassTypeDeclarationDescr typeDescr,
                                       TypeDeclaration type ) {

        if ( type.getTypeClassDef() != null ) {
            try {
                pkgRegistry.getPackage().buildFieldAccessors( type );
            } catch ( Throwable e ) {
                results.addBuilderResult(new TypeDeclarationError(typeDescr,
                                                                   "Error creating field accessors for TypeDeclaration '" + type.getTypeName() +
                                                                   "' for type '" +
                                                                   type.getTypeName() +
                                                                   " : " + e.getMessage() +
                                                                   "'"));
                return false;
            }
        }

        processMvelBasedAccessors(context, results, pkgRegistry, typeDescr, type );
        return true;
    }

    static void processMvelBasedAccessors(TypeDeclarationContext context, BuildResultCollector results, PackageRegistry pkgRegistry, BaseDescr typeDescr, TypeDeclaration type ) {
        wireTimestampAccessor(context, results, typeDescr, type, pkgRegistry );
        wireDurationAccessor(context, results, typeDescr, type, pkgRegistry );
    }

    private static void wireTimestampAccessor(TypeDeclarationContext context, BuildResultCollector resultCollector, BaseDescr typeDescr, TypeDeclaration type, PackageRegistry pkgRegistry ) {
        String timestampField = type.getTimestampAttribute();
        if ( timestampField != null ) {
            InternalKnowledgePackage pkg = pkgRegistry.getPackage();
            AnalysisResult results = getMvelAnalysisResult(context, typeDescr, type, pkgRegistry, timestampField, pkg );
            if (results != null) {
                type.setTimestampExtractor(pkg.getFieldExtractor( type, timestampField, results.getReturnType() ));
            } else {
                resultCollector.addBuilderResult(new TypeDeclarationError(typeDescr,
                                                                   "Error creating field accessors for timestamp field '" + timestampField +
                                                                   "' for type '" + type.getTypeName() + "'"));
            }
        }
    }

    private static void wireDurationAccessor(TypeDeclarationContext context, BuildResultCollector resultCollector, BaseDescr typeDescr, TypeDeclaration type, PackageRegistry pkgRegistry ) {
        String durationField = type.getDurationAttribute();
        if (durationField != null) {
            InternalKnowledgePackage pkg = pkgRegistry.getPackage();
            AnalysisResult results = getMvelAnalysisResult(context, typeDescr, type, pkgRegistry, durationField, pkg );
            if (results != null) {
                type.setDurationExtractor(pkg.getFieldExtractor( type, durationField, results.getReturnType() ));
            } else {
                resultCollector.addBuilderResult(new TypeDeclarationError(typeDescr,
                                                                   "Error processing @duration for TypeDeclaration '" + type.getFullName() +
                                                                   "': cannot access the field '" + durationField + "'"));
            }
        }
    }

    private static AnalysisResult getMvelAnalysisResult(TypeDeclarationContext tdContext, BaseDescr typeDescr, TypeDeclaration type, PackageRegistry pkgRegistry, String durationField, InternalKnowledgePackage pkg ) {
        Dialect dialect = pkgRegistry.getDialectCompiletimeRegistry().getDialect("mvel");
        PackageBuildContext pkgContext = new PackageBuildContext();
        pkgContext.initContext(tdContext,
                pkg, typeDescr, pkgRegistry.getDialectCompiletimeRegistry(), dialect, null);
        if (!type.isTypesafe()) {
            pkgContext.setTypesafe(false);
        }

        return pkgContext.getDialect().analyzeExpression( pkgContext,
                                                       typeDescr,
                                                       durationField,
                                                       new BoundIdentifiers( type.getTypeClass() ) );
    }
}
