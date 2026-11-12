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
package org.drools.model.codegen.tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.drools.codegen.common.DroolsModelBuildContext;
import org.drools.compiler.builder.PackageRegistryManager;
import org.drools.compiler.builder.impl.BuildResultCollector;
import org.drools.compiler.builder.impl.BuildResultCollectorImpl;
import org.drools.compiler.builder.impl.GlobalVariableContext;
import org.drools.compiler.builder.impl.GlobalVariableContextImpl;
import org.drools.compiler.builder.impl.KnowledgeBuilderConfigurationImpl;
import org.drools.compiler.builder.impl.PackageRegistryManagerImpl;
import org.drools.compiler.builder.impl.TypeDeclarationContext;
import org.drools.compiler.builder.impl.TypeDeclarationContextImpl;
import org.drools.compiler.builder.impl.processors.AccumulateFunctionCompilationPhase;
import org.drools.compiler.builder.impl.processors.CompilationPhase;
import org.drools.compiler.builder.impl.processors.FunctionCompilationPhase;
import org.drools.compiler.builder.impl.processors.ImmutableGlobalCompilationPhase;
import org.drools.compiler.builder.impl.processors.IteratingPhase;
import org.drools.compiler.builder.impl.processors.RuleValidator;
import org.drools.compiler.builder.impl.processors.SinglePackagePhaseFactory;
import org.drools.compiler.builder.impl.processors.WindowDeclarationCompilationPhase;
import org.drools.compiler.lang.descr.CompositePackageDescr;
import org.drools.model.codegen.execmodel.CanonicalModelBuildContext;
import org.drools.model.codegen.execmodel.PackageModel;
import org.drools.model.codegen.execmodel.PackageModelManager;
import org.drools.model.codegen.execmodel.PackageSourceManager;
import org.drools.model.codegen.execmodel.PackageSources;
import org.drools.model.codegen.execmodel.generator.DRLIdGenerator;
import org.drools.model.codegen.execmodel.generator.declaredtype.POJOGenerator;
import org.drools.model.codegen.execmodel.processors.DeclaredTypeDeregistrationPhase;
import org.drools.model.codegen.execmodel.processors.DeclaredTypeRegistrationPhase;
import org.drools.model.codegen.execmodel.processors.GeneratedPojoCompilationPhase;
import org.drools.model.codegen.execmodel.processors.ModelGeneratorPhase;
import org.drools.model.codegen.execmodel.processors.PojoStoragePhase;
import org.drools.model.codegen.execmodel.processors.SourceCodeGenerationPhase;
import org.kie.internal.builder.conf.PropertySpecificOption;
import org.kie.util.maven.support.ReleaseIdImpl;

/**
 * An alternative compilation flow that generates code starting
 * from a collection of {@link CompositePackageDescr}s,
 * skipping the {@link org.kie.internal.builder.KnowledgeBuilder} entirely.
 *
 * It explicitly invokes the necessary {@link CompilationPhase}s
 * one after the other and collects the error.
 *
 * @param <T> a subclass of PackageSources to retrieve the generated results
 */
public class ExplicitCanonicalModelCompiler<T extends PackageSources> {

    private final Collection<CompositePackageDescr> packages;
    private final PackageRegistryManager pkgRegistryManager;
    private final PackageModelManager packageModelManager;
    private final CanonicalModelBuildContext buildContext;
    private final KnowledgeBuilderConfigurationImpl configuration;
    private final BuildResultCollector results;
    private final TypeDeclarationContext typeDeclarationContext;
    private final GlobalVariableContext globalVariableContext;
    private final PackageSourceManager<T> packageSourceManager;
    private final Function<PackageModel, T> sourceDumpFunction;
    private final boolean hasMvel = false;
    private final boolean oneClassPerRule = true;

    private DroolsModelBuildContext context;

    public static <T extends PackageSources> ExplicitCanonicalModelCompiler<T> of(
            Collection<CompositePackageDescr> packages,
            KnowledgeBuilderConfigurationImpl configuration,
            Function<PackageModel, T> sourceDumpFunction) {
        PackageRegistryManagerImpl pkgRegistryManager =
                new PackageRegistryManagerImpl(configuration, configuration::getClassLoader, () -> null);
        GlobalVariableContextImpl globalVariableContext = new GlobalVariableContextImpl();
        return new ExplicitCanonicalModelCompiler<>(
                packages,
                pkgRegistryManager,
                new PackageModelManager(configuration, new ReleaseIdImpl("org.drools:dummy:1.0-SNAPSHOT"), new DRLIdGenerator()),
                new CanonicalModelBuildContext(),
                configuration,
                new BuildResultCollectorImpl(),
                new TypeDeclarationContextImpl(configuration, pkgRegistryManager, globalVariableContext),
                globalVariableContext,
                new PackageSourceManager<>(),
                sourceDumpFunction);
    }

    public ExplicitCanonicalModelCompiler(Collection<CompositePackageDescr> packages,
                                          PackageRegistryManager pkgRegistryManager,
                                          PackageModelManager packageModelManager,
                                          CanonicalModelBuildContext buildContext,
                                          KnowledgeBuilderConfigurationImpl configuration,
                                          BuildResultCollector results,
                                          TypeDeclarationContext typeDeclarationContext,
                                          GlobalVariableContext globalVariableContext,
                                          PackageSourceManager<T> packageSourceManager,
                                          Function<PackageModel, T> sourceDumpFunction) {
        this.packages = packages;
        this.pkgRegistryManager = pkgRegistryManager;
        this.packageModelManager = packageModelManager;
        this.buildContext = buildContext;
        this.configuration = configuration;
        this.results = results;
        this.typeDeclarationContext = typeDeclarationContext;
        this.globalVariableContext = globalVariableContext;
        this.packageSourceManager = packageSourceManager;
        this.sourceDumpFunction = sourceDumpFunction;
    }

    public void process() {
        List<CompilationPhase> phases = new ArrayList<>();

        phases.add(iteratingPhase((reg, acc) -> new DeclaredTypeRegistrationPhase(reg, acc, pkgRegistryManager, configuration.getOption(PropertySpecificOption.KEY))));
        phases.add(iteratingPhase((reg, acc) ->
                new POJOGenerator(reg.getPackage(), acc, packageModelManager.getPackageModel(acc, reg, reg.getPackage().getName()), packages, pkgRegistryManager)));
        phases.add(new GeneratedPojoCompilationPhase(
                packageModelManager, buildContext, configuration.getClassLoader()));
        phases.add(new PojoStoragePhase(buildContext, pkgRegistryManager, packages));
        phases.add(iteratingPhase(AccumulateFunctionCompilationPhase::new));
        if (hasMvel) {
            phases.add(iteratingPhase((reg, acc) -> new WindowDeclarationCompilationPhase(reg, acc, typeDeclarationContext)));
        }
        phases.add(iteratingPhase((reg, acc) -> new FunctionCompilationPhase(reg, acc, configuration)));
        phases.add(iteratingPhase((reg, acc) -> new ImmutableGlobalCompilationPhase(reg, acc, globalVariableContext)));
        phases.add(new DeclaredTypeDeregistrationPhase(packages, pkgRegistryManager));

        // ---

        phases.add(iteratingPhase((reg, acc) -> new RuleValidator(reg, acc, configuration))); // validateUniqueRuleNames
        phases.add(iteratingPhase((reg, acc) -> new ModelGeneratorPhase(reg, acc, packageModelManager.getPackageModel(acc, reg, acc.getName()), typeDeclarationContext))); // validateUniqueRuleNames
        phases.add(iteratingPhase((reg, acc) -> new SourceCodeGenerationPhase<>(
                packageModelManager.getPackageModel(acc, reg, acc.getName()).setContext(context), packageSourceManager, sourceDumpFunction, oneClassPerRule))); // validateUniqueRuleNames


        for (CompilationPhase phase : phases) {
            phase.process();
            this.results.addAll(phase.getResults());
            if (results.hasErrors()) {
                break;
            }
        }

    }

    private IteratingPhase iteratingPhase(SinglePackagePhaseFactory phaseFactory) {
        return new IteratingPhase(packages, pkgRegistryManager, phaseFactory);
    }

    public BuildResultCollector getBuildResults() {
        return results;
    }

    public Collection<T> getPackageSources() {
        return packageSourceManager.getPackageSources();
    }
    
    public Collection<PackageModel> getPackageModels() {
        return packageModelManager.values();
    }

    public ExplicitCanonicalModelCompiler<T> setContext(DroolsModelBuildContext context) {
        this.context = context;
        return this;
    }
}
