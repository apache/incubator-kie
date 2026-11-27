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
package org.kie.dmn.core.internal.utils;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.drools.io.ClassPathResource;
import org.drools.io.ResourceConfigurationImpl;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.dmn.api.core.DMNCompiler;
import org.kie.dmn.api.core.DMNCompilerConfiguration;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.api.core.event.DMNRuntimeEventListener;
import org.kie.dmn.api.marshalling.DMNMarshaller;
import org.kie.dmn.backend.marshalling.v1x.DMNMarshallerFactory;
import org.kie.dmn.core.assembler.DMNAssemblerService;
import org.kie.dmn.core.assembler.DMNResource;
import org.kie.dmn.core.assembler.DMNResourceDependenciesSorter;
import org.kie.dmn.core.compiler.DMNCompilerConfigurationImpl;
import org.kie.dmn.core.compiler.DMNCompilerImpl;
import org.kie.dmn.core.compiler.DMNDecisionLogicCompilerFactory;
import org.kie.dmn.core.compiler.DMNProfile;
import org.kie.dmn.core.compiler.RuntimeTypeCheckOption;
import org.kie.dmn.core.compiler.profiles.ExtendedDMNProfile;
import org.kie.dmn.core.impl.DMNRuntimeImpl;
import org.kie.dmn.core.impl.DMNRuntimeKB;
import org.kie.dmn.feel.util.Either;
import org.kie.dmn.model.api.Definitions;
import org.kie.internal.io.ResourceWithConfigurationImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal Utility class.
 */
public class DMNRuntimeBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(DMNRuntimeBuilder.class);
    private final DMNRuntimeBuilderCtx ctx;

    private DMNRuntimeBuilder() {
        this.ctx = new DMNRuntimeBuilderCtx();
    }

    private static class DMNRuntimeBuilderCtx {

        public final DMNCompilerConfigurationImpl cc;
        public final List<DMNProfile> dmnProfiles = new ArrayList<>();
        private RelativeImportResolver relativeResolver;

        public DMNRuntimeBuilderCtx() {
            this.cc = new DMNCompilerConfigurationImpl();
        }

        public void setRelativeResolver(RelativeImportResolver relativeResolver) {
            this.relativeResolver = relativeResolver;
        }
    }

    @FunctionalInterface
    public static interface RelativeImportResolver {

        Reader resolve(String modelNamespace, String modelName, String locationURI);
    }

    /**
     * Internal Utility class.
     */
    public static DMNRuntimeBuilder fromDefaults() {
        DMNRuntimeBuilder dmnRuntimeBuilder = new DMNRuntimeBuilder();
        dmnRuntimeBuilder.addProfile(new ExtendedDMNProfile());
        return dmnRuntimeBuilder;
    }

    public DMNRuntimeBuilder addProfile(DMNProfile dmnProfile) {
        ctx.dmnProfiles.add(dmnProfile);
        ctx.cc.addExtensions(dmnProfile.getExtensionRegisters());
        ctx.cc.addDRGElementCompilers(dmnProfile.getDRGElementCompilers());
        ctx.cc.addFEELProfile(dmnProfile);
        return this;
    }

    public DMNRuntimeBuilder setOption(RuntimeTypeCheckOption option) {
        ctx.cc.setProperty(option.getPropertyName(), "" + option.isRuntimeTypeCheck());
        return this;
    }

    public DMNRuntimeBuilder setRootClassLoader(ClassLoader classLoader) {
        ctx.cc.setRootClassLoader(classLoader);
        return this;
    }

    public DMNRuntimeBuilder setRelativeImportResolver(RelativeImportResolver relativeResolver) {
        ctx.setRelativeResolver(relativeResolver);
        return this;
    }

    public DMNRuntimeBuilder setDecisionLogicCompilerFactory(DMNDecisionLogicCompilerFactory factory) {
        ctx.cc.setProperty(DMNAssemblerService.DMN_DECISION_LOGIC_COMPILER, factory.getClass().getCanonicalName());
        ctx.cc.setDecisionLogicCompilerFactory(factory);
        return this;
    }

    /**
     * Internal Utility class.
     */
    public static DMNRuntimeBuilderConfigured usingStrict() {
        DMNRuntimeBuilder dmnRuntimeBuilder = new DMNRuntimeBuilder();
        dmnRuntimeBuilder.setRootClassLoader(null);
        dmnRuntimeBuilder.setOption(new RuntimeTypeCheckOption(true));
        return dmnRuntimeBuilder.buildConfiguration();
    }

    public DMNRuntimeBuilderConfigured buildConfiguration() {
        return buildConfigurationUsingCustomCompiler(DMNCompilerImpl::new);
    }

    public DMNRuntimeBuilderConfigured buildConfigurationUsingCustomCompiler(Function<DMNCompilerConfiguration, DMNCompiler> dmnCompilerFn) {
        return new DMNRuntimeBuilderConfigured(ctx, dmnCompilerFn.apply(ctx.cc));
    }

    public static class DMNRuntimeBuilderConfigured {

        private static final Logger LOG = LoggerFactory.getLogger(DMNRuntimeBuilderConfigured.class);

        private final DMNRuntimeBuilderCtx ctx;
        private final DMNCompiler dmnCompiler;

        private DMNRuntimeBuilderConfigured(DMNRuntimeBuilderCtx ctx, DMNCompiler dmnCompiler) {
            this.ctx = ctx;
            this.dmnCompiler = dmnCompiler;
        }

        public DMNRuntime withModels(Collection<DMNModel> models) {
            return new DMNRuntimeImpl(new DMNRuntimeKBStatic(ctx.cc.getRootClassLoader(), models,
                    ctx.dmnProfiles));
        }

        public Either<Exception, DMNRuntime> fromClasspathResource(final String resourceName, final Class<?> testClass) {
            return fromResources(List.of(new ClassPathResource(resourceName, testClass)));
        }

        public Either<Exception, DMNRuntime> fromClasspathResources(final String resourceName, final Class<?> testClass, final String... additionalResources) {
            List<Resource> resources = new ArrayList<>();
            resources.add(new ClassPathResource(resourceName, testClass));
            for (String ar : additionalResources) {
                resources.add(new ClassPathResource(ar, testClass));
            }
            return fromResources(resources);
        }

        public Either<Exception, DMNRuntime> fromResources(Collection<Resource> resources) {
            List<DMNResource> dmnResources = new ArrayList<>();
            for (Resource r : resources) {
                Definitions definitions;
                try {
                    definitions = getMarshaller().unmarshal(r.getReader());
                } catch (IOException e) {
                    return Either.ofLeft(e);
                }
                ResourceConfigurationImpl rc = new ResourceConfigurationImpl();
                rc.setResourceType(ResourceType.DMN);
                DMNResource dmnResource = new DMNResource(definitions, new ResourceWithConfigurationImpl(r, rc, b -> {
                }, a -> {
                }));
                dmnResources.add(dmnResource);
            }
            DMNAssemblerService.enrichDMNResourcesWithImportsDependencies(dmnResources, Collections.emptyList());
            List<DMNResource> sortedDmnResources = DMNResourceDependenciesSorter.sort(dmnResources);

            List<DMNModel> dmnModels = new ArrayList<>();
            for (DMNResource dmnRes : sortedDmnResources) {
                DMNModel dmnModel;
                if (ctx.relativeResolver != null) {
                    if (dmnCompiler instanceof DMNCompilerImpl) {
                        dmnModel = ((DMNCompilerImpl) dmnCompiler).compile(dmnRes.getDefinitions(),
                                                                           dmnModels,
                                                                           dmnRes.getResAndConfig().getResource(),
                                                                           relativeURI -> ctx.relativeResolver.resolve(dmnRes.getDefinitions().getNamespace(),
                                                                                                                       dmnRes.getDefinitions().getName(),
                                                                                                                       relativeURI));
                    } else {
                        throw new IllegalStateException("specified a RelativeImportResolver but the compiler is not org.kie.dmn.core.compiler.DMNCompilerImpl");
                    }
                } else {
                    dmnModel = dmnCompiler.compile(dmnRes.getDefinitions(), dmnRes.getResAndConfig().getResource(), dmnModels);
                }
                if (dmnModel != null) {
                    dmnModels.add(dmnModel);
                } else {
                    LOG.error("Unable to compile DMN model for the resource {}", dmnRes.getResAndConfig().getResource());
                    return Either.ofLeft(new IllegalStateException("Unable to compile DMN model for the resource " + dmnRes.getResAndConfig().getResource()));
                }
            }
            return Either.ofRight(new DMNRuntimeImpl(new DMNRuntimeKBStatic(ctx.cc.getRootClassLoader(), dmnModels,
                                                                            ctx.dmnProfiles)));
        }

        private DMNMarshaller getMarshaller() {
            if (!ctx.cc.getRegisteredExtensions().isEmpty()) {
                return DMNMarshallerFactory.newMarshallerWithExtensions(ctx.cc.getRegisteredExtensions());
            } else {
                return DMNMarshallerFactory.newDefaultMarshaller();
            }
        }
    }


    private static class DMNRuntimeKBStatic implements DMNRuntimeKB {

        private final ClassLoader rootClassLoader;
        private final List<DMNProfile> dmnProfiles;
        private final List<DMNModel> models;

        private DMNRuntimeKBStatic(ClassLoader rootClassLoader, Collection<DMNModel> models,
                                   Collection<DMNProfile> dmnProfiles) {
            this.rootClassLoader = rootClassLoader;
            LOG.trace("DMNRuntimeKBStatic rootClassLoader is set to {}", rootClassLoader);
            this.models = Collections.unmodifiableList(new ArrayList<>(models));
            this.dmnProfiles = Collections.unmodifiableList(new ArrayList<>(dmnProfiles));
        }

        @Override
        public List<DMNModel> getModels() {
            return models;
        }

        @Override
        public DMNModel getModel(String namespace, String modelName) {
            return models.stream().filter(m -> m.getNamespace().equals(namespace) && m.getName().equals(modelName)).findFirst().orElse(null);
        }

        @Override
        public DMNModel getModelById(String namespace, String modelId) {
            return models.stream().filter(m -> m.getNamespace().equals(namespace) && m.getDefinitions().getId().equals(modelId)).findFirst().orElse(null);
        }

        @Override
        public List<DMNProfile> getProfiles() {
            return dmnProfiles;
        }

        @Override
        public List<DMNRuntimeEventListener> getListeners() {
            return Collections.emptyList();
        }

        @Override
        public ClassLoader getRootClassLoader() {
            return rootClassLoader;
        }

        @Override
        public InternalKnowledgeBase getInternalKnowledgeBase() {
            throw new UnsupportedOperationException();
        }

    }
}
