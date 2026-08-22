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
package org.kie.kogito.quarkus.common.deployment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.drools.codegen.common.AppPaths;
import org.drools.codegen.common.DroolsModelBuildContext;
import org.drools.codegen.common.GeneratedFile;
import org.drools.codegen.common.GeneratedFileType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jboss.logging.Logger;
import org.kie.efesto.quarkus.deployment.EfestoGeneratedClassBuildItem;
import org.kie.kogito.KogitoGAV;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.core.utils.ApplicationGeneratorDiscovery;
import org.kie.kogito.quarkus.KogitoRecorder;

import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ArchiveRootBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.index.IndexingUtil;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.paths.PathCollection;
import io.quarkus.paths.PathList;
import io.quarkus.resteasy.reactive.spi.GeneratedJaxRsResourceBuildItem;
import io.quarkus.vertx.http.deployment.spi.AdditionalStaticResourceBuildItem;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static org.drools.codegen.common.GeneratedFileType.COMPILED_CLASS;
import static org.drools.codegen.common.GeneratedFileType.REST;
import static org.drools.quarkus.util.deployment.DroolsQuarkusResourceUtils.compileGeneratedSources;
import static org.drools.quarkus.util.deployment.DroolsQuarkusResourceUtils.makeBuildItems;
import static org.kie.efesto.common.api.constants.Constants.INDEXFILE_DIRECTORY_PROPERTY;
import static org.kie.kogito.codegen.api.context.ContextAttributesConstants.KOGITO_FAULT_TOLERANCE_ENABLED;
import static org.kie.kogito.quarkus.common.deployment.KogitoQuarkusResourceUtils.HOT_RELOAD_SUPPORT_PATH;
import static org.kie.kogito.quarkus.common.deployment.KogitoQuarkusResourceUtils.dumpFilesToDisk;
import static org.kie.kogito.quarkus.common.deployment.KogitoQuarkusResourceUtils.getHotReloadSupportSource;
import static org.kie.kogito.quarkus.common.deployment.KogitoQuarkusResourceUtils.kogitoBuildContext;
import static org.kie.kogito.quarkus.common.deployment.KogitoQuarkusResourceUtils.registerResources;
import static org.kie.kogito.quarkus.common.deployment.KogitoQuarkusResourceUtils.toClassName;

/**
 * Main class of the Kogito extension
 */
public class KogitoAssetsProcessor {

    private static final Logger LOGGER = Logger.getLogger(KogitoAssetsProcessor.class);

    @Inject
    ArchiveRootBuildItem root;
    @Inject
    LiveReloadBuildItem liveReload;
    @Inject
    CurateOutcomeBuildItem curateOutcomeBuildItem;
    @Inject
    CombinedIndexBuildItem combinedIndexBuildItem;
    @Inject
    OutputTargetBuildItem outputTargetBuildItem;

    @BuildStep
    public KogitoBuildContextBuildItem generateKogitoBuildContext(List<KogitoBuildContextAttributeBuildItem> attributes) {
        // configure the application generator
        PathCollection rootPaths = getRootPaths(root.getResolvedPaths(), AppPaths.BT);
        KogitoBuildContext context =
                kogitoBuildContext(rootPaths,
                        combinedIndexBuildItem.getIndex(),
                        curateOutcomeBuildItem.getApplicationModel().getAppArtifact());
        attributes.forEach(attribute -> context.addContextAttribute(attribute.getName(), attribute.getValue()));
        return new KogitoBuildContextBuildItem(context);
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    public SyntheticBeanBuildItem runtimeConfigBuildStep(KogitoRecorder recorder) {
        Dependency appModel = curateOutcomeBuildItem.getApplicationModel().getAppArtifact();

        return SyntheticBeanBuildItem.configure(KogitoGAV.class)
                .scope(Singleton.class)
                .supplier(recorder.kogitoGAVSupplier(appModel.getGroupId(), appModel.getArtifactId(), appModel.getVersion())).done();
    }

    @BuildStep
    public KogitoGeneratedSourcesBuildItem generateSources(
            Capabilities capabilities,
            List<KogitoAddonsPreGeneratedSourcesBuildItem> extraSources,
            KogitoBuildContextBuildItem contextBuildItem) {

        final KogitoBuildContext context = contextBuildItem.getKogitoBuildContext();

        validateAvailableCapabilities(context, capabilities);

        // TODO to be removed with DROOLS-7090
        boolean indexFileDirectorySet = false;
        if (System.getProperty(INDEXFILE_DIRECTORY_PROPERTY) == null) {
            System.setProperty(INDEXFILE_DIRECTORY_PROPERTY, context.getAppPaths().getOutputTarget().toString());
            indexFileDirectorySet = true;
        }
        Collection<GeneratedFile> generatedFiles = generateFiles(context);
        // TODO to be removed with DROOLS-7090
        if (indexFileDirectorySet) {
            System.clearProperty(INDEXFILE_DIRECTORY_PROPERTY);
        }

        // The HotReloadSupportClass has to be generated only during the first model generation
        // During actual hot reloads it will be regenerated by the compilation providers in order to retrigger this build step
        if (!liveReload.isLiveReload()) {
            generatedFiles.add(new GeneratedFile(GeneratedFileType.SOURCE, HOT_RELOAD_SUPPORT_PATH + ".java", getHotReloadSupportSource()));
        }
        return new KogitoGeneratedSourcesBuildItem(generatedFiles);
    }

    @BuildStep
    public List<KogitoGeneratedClassesBuildItem> generateModel(
            KogitoGeneratedSourcesBuildItem sources,
            List<KogitoAddonsPreGeneratedSourcesBuildItem> addonsPreSources,
            List<KogitoAddonsPostGeneratedSourcesBuildItem> addonsPostSources,
            KogitoBuildContextBuildItem contextBuildItem,
            Capabilities capabilities,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            BuildProducer<GeneratedJaxRsResourceBuildItem> jaxrsProducer,
            BuildProducer<AdditionalStaticResourceBuildItem> staticResProducer,
            BuildProducer<NativeImageResourceBuildItem> resource,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<GeneratedResourceBuildItem> genResBI) throws IOException {

        final KogitoBuildContext context = contextBuildItem.getKogitoBuildContext();

        Collection<GeneratedFile> generatedFiles = collectGeneratedFiles(sources, addonsPreSources, addonsPostSources);

        Map<GeneratedFileType, List<GeneratedFile>> mappedGeneratedFiles = generatedFiles.stream()
                .collect(Collectors.groupingBy(GeneratedFile::type));

        Collection<GeneratedFile> generatedJavaSourcesFiles = mappedGeneratedFiles.entrySet()
                .stream()
                .filter(entry -> entry.getKey() != COMPILED_CLASS)
                .flatMap((Function<Map.Entry<GeneratedFileType, List<GeneratedFile>>, Stream<GeneratedFile>>) generatedFileTypeListEntry -> generatedFileTypeListEntry.getValue().stream())
                .collect(Collectors.toList());

        dumpFilesToDisk(context.getAppPaths(), generatedJavaSourcesFiles);

        Collection<GeneratedBeanBuildItem> generatedBeanBuildItems = createGeneratedBeanBuildItemsFromJavaSources(
                context,
                generatedJavaSourcesFiles,
                liveReload.isLiveReload());

        Collection<GeneratedBeanBuildItem> buildItemsFromCompiledClasses = createGeneratedBeanBuildItemsFromCompiledClasses(mappedGeneratedFiles.getOrDefault(COMPILED_CLASS, Collections.emptyList()));
        generatedBeanBuildItems.addAll(buildItemsFromCompiledClasses);

        // build Java source code and register the generated beans
        Optional<KogitoGeneratedClassesBuildItem> optionalIndex = indexGeneratedBeanBuildItemWithRestResources(
                context,
                capabilities,
                generatedJavaSourcesFiles,
                generatedBeanBuildItems,
                generatedBeans,
                jaxrsProducer);

        registerDataEventsForReflection(optionalIndex.map(KogitoGeneratedClassesBuildItem::getIndexedClasses),
                context, reflectiveClass);
        registerKogitoIncubationAPI(reflectiveClass);

        registerResources(generatedFiles, staticResProducer, resource, genResBI);

        return optionalIndex
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    @BuildStep
    public EfestoGeneratedClassBuildItem reflectiveEfestoGeneratedClassBuildItem(KogitoGeneratedSourcesBuildItem kogitoGeneratedSourcesBuildItem) {
        LOGGER.debugf("reflectiveEfestoGeneratedClassBuildItem %s", kogitoGeneratedSourcesBuildItem);
        return new EfestoGeneratedClassBuildItem(kogitoGeneratedSourcesBuildItem.getGeneratedFiles());
    }

    static PathCollection getRootPaths(PathCollection resolvedPaths, AppPaths.BuildTool bt) {
        // Needed hack because during MAVEN build, resolvedPaths point to root of project,
        // while during GRADLE build, resolved paths contains {root_project}/build/classes/java/main and
        // {root_project}/build/resources/main
        switch (bt) {
            case GRADLE -> {
                AtomicReference<PathCollection> toReturnRef = new AtomicReference<>(resolvedPaths);
                Optional<Path> optClassesPath =
                        resolvedPaths.stream().filter(path -> {
                            String fullPath = path.toString();
                            String lookingFor = "build/classes/java/main".replace("/", File.separator);
                            return fullPath.endsWith(lookingFor);
                        }).findFirst();
                optClassesPath.ifPresent(classesPath -> toReturnRef.set(PathList.of(classesPath)));
                return toReturnRef.get();
            }
            default -> {
                return resolvedPaths;
            }
        }
    }

    private Collection<GeneratedFile> collectGeneratedFiles(KogitoGeneratedSourcesBuildItem sources, List<KogitoAddonsPreGeneratedSourcesBuildItem> preSources,
            List<KogitoAddonsPostGeneratedSourcesBuildItem> postSources) {
        Map<String, GeneratedFile> map = new HashMap<>();

        addGeneratedFiles(preSources, map);

        for (GeneratedFile generatedFile : sources.getGeneratedFiles()) {
            map.put(generatedFile.relativePath(), generatedFile);
        }

        addGeneratedFiles(postSources, map);

        return map.values();
    }

    private static void addGeneratedFiles(List<? extends KogitoAddonsGeneratedSourcesBuildItem> items, Map<String, GeneratedFile> map) {
        for (KogitoAddonsGeneratedSourcesBuildItem item : items) {
            for (GeneratedFile generatedFile : item.getGeneratedFiles()) {
                map.put(generatedFile.relativePath(), generatedFile);
            }
        }
    }

    void validateAvailableCapabilities(KogitoBuildContext context, Capabilities capabilities) {
        boolean hasOptaPlannerCapability = capabilities.isCapabilityWithPrefixPresent("org.optaplanner");
        boolean hasRestCapabilities = capabilities.isPresent(Capability.RESTEASY) && capabilities.isPresent(Capability.RESTEASY_JSON_JACKSON)
                || capabilities.isPresent(Capability.RESTEASY_REACTIVE) && capabilities.isPresent(Capability.RESTEASY_REACTIVE_JSON_JACKSON);

        // disable REST if OptaPlanner capability is available but REST is not (user can override via property)
        if (hasOptaPlannerCapability && !hasRestCapabilities &&
                kogitoGenerateRest(context).isEmpty()) {
            context.setApplicationProperty(DroolsModelBuildContext.KOGITO_GENERATE_REST, "false");
            LOGGER.info("Disabling Kogito REST generation because OptaPlanner extension is available, specify `kogito.generate.rest = true` to re-enable it");
        }

        if (!hasRestCapabilities && kogitoGenerateRest(context).orElse(true)) {
            throw new MissingRestCapabilityException();
        }

        // disable Fault Tolerance if SmallRye Fault Tolerance extension is not available (user can override via property)
        if (!capabilities.isPresent(Capability.SMALLRYE_FAULT_TOLERANCE)) {
            context.setApplicationProperty(KOGITO_FAULT_TOLERANCE_ENABLED, "false");
            LOGGER.info("Disabling Kogito Fault Tolerance because SmallRye Fault Tolerance extension is not available");
        }

    }

    private Optional<Boolean> kogitoGenerateRest(KogitoBuildContext context) {
        return context.getApplicationProperty(DroolsModelBuildContext.KOGITO_GENERATE_REST)
                .map("true"::equalsIgnoreCase);
    }

    private Collection<GeneratedFile> generateFiles(KogitoBuildContext context) {
        return ApplicationGeneratorDiscovery
                .discover(context)
                .generate();
    }

    private Collection<GeneratedBeanBuildItem> createGeneratedBeanBuildItemsFromJavaSources(
            KogitoBuildContext context,
            Collection<GeneratedFile> generatedFiles,
            boolean useDebugSymbols) throws IOException {

        Collection<ResolvedDependency> dependencies =
                curateOutcomeBuildItem.getApplicationModel().getRuntimeDependencies();
        return compileGeneratedSources(context, dependencies, generatedFiles, useDebugSymbols);
    }

    private Collection<GeneratedBeanBuildItem> createGeneratedBeanBuildItemsFromCompiledClasses(
            Collection<GeneratedFile> generatedFiles) {
        Map<String, byte[]> compiledClassesMap = new HashMap<>();

        generatedFiles.forEach(generatedFile -> compiledClassesMap.put(generatedFile.relativePath(), generatedFile.contents()));
        return makeBuildItems(compiledClassesMap);
    }

    private Optional<KogitoGeneratedClassesBuildItem> indexGeneratedBeanBuildItemWithRestResources(
            KogitoBuildContext context,
            Capabilities capabilities,
            Collection<GeneratedFile> generatedFiles,
            Collection<GeneratedBeanBuildItem> generatedBeanBuildItems,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            BuildProducer<GeneratedJaxRsResourceBuildItem> jaxrsProducer) throws IOException {

        Set<String> restResourceClassNameSet = generatedFiles.stream()
                .filter(file -> file.type().equals(REST))
                .map(file -> toClassName(file.path().toString()))
                .collect(Collectors.toSet());
        // Reactive REST re-registers JaxRs resources as beans (duplicate-class check); classic ignores that channel, so REST
        // classes need bean registration there. Capability.REST comes from BOTH stacks; only RESTEASY_REACTIVE is reactive-specific.
        boolean reactiveRestPresent = capabilities.isPresent(Capability.RESTEASY_REACTIVE);
        generatedBeanBuildItems.stream()
                .filter(b -> !reactiveRestPresent || !restResourceClassNameSet.contains(b.getName()))
                .forEach(generatedBeans::produce);
        generatedBeanBuildItems.stream()
                .filter(b -> restResourceClassNameSet.contains(b.getName()))
                .forEach(b -> jaxrsProducer.produce(new GeneratedJaxRsResourceBuildItem(b.getName(), b.getData())));
        return Optional.of(indexBuildItems(context, generatedBeanBuildItems));
    }

    private void registerKogitoIncubationAPI(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.incubation.common.EmptyDataContext"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.incubation.common.EmptyMetaDataContext"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.incubation.common.ExtendedDataContext"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.incubation.common.MapDataContext"));
    }

    private void registerDataEventsForReflection(Optional<IndexView> optionalIndex, KogitoBuildContext context, BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.event.AbstractDataEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.ProcessDataEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.ProcessInstanceDataEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.VariableInstanceDataEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.impl.ProcessInstanceEventBody"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.impl.NodeInstanceEventBody"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.impl.ProcessErrorEventBody"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.impl.VariableInstanceEventBody"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.UserTaskInstanceDataEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.impl.UserTaskInstanceEventBody"));
        reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, "org.kie.kogito.event.usertask.UserTaskInstanceDeadlineDataEvent"));
        reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, "org.kie.kogito.event.usertask.UserTaskInstanceDeadlineEventBody"));

        if (context.getAddonsConfig().useMonitoring()) {
            registerMonitoringAddonClasses(reflectiveClass);
        }

        if (context.getAddonsConfig().useTracing()) {
            registerTracingAddonClasses(reflectiveClass);
        }

        if (context.getAddonsConfig().useExplainability()) {
            registerExplainabilityAddonClasses(reflectiveClass);
        }

        optionalIndex.ifPresent(index -> {
            // not sure there is any generated class directly inheriting from AbstractDataEvent, keeping just in case
            addChildrenClasses(index, "org.kie.kogito.event.AbstractDataEvent", reflectiveClass);
            addChildrenClasses(index, "org.kie.kogito.services.event.ProcessDataEvent", reflectiveClass);
        });
    }

    private void registerMonitoringAddonClasses(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.HdrHistogram.Histogram"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.HdrHistogram.ConcurrentHistogram"));
    }

    private void registerTracingAddonClasses(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.trace.TraceEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.trace.TraceEventType"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.trace.TraceExecutionStep"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.trace.TraceExecutionStepType"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.trace.TraceHeader"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.trace.TraceInputValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.trace.TraceOutputValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.trace.TraceType"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.trace.TraceResourceId"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.message.Message"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.message.MessageLevel"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.message.MessageCategory"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.message.MessageFEELEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.message.MessageFEELEventSeverity"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.message.MessageExceptionField"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.message.MessageFEELEventSeverity"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.message.MessageFEELEventSeverity"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.message.MessageFEELEventSeverity"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.trace.TraceInputValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.trace.TraceOutputValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.typedvalue.BaseTypedValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.typedvalue.TypedValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.typedvalue.UnitValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.typedvalue.CollectionValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.typedvalue.StructureValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.trace.TraceExecutionStep"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.trace.TraceExecutionStepType"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.model.ModelEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.KogitoGAV"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.decision.DecisionModelType"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.decision.DecisionModelMetadata"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.message.models.DecisionMessage"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.event.model.models.DecisionModelEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.event.ModelMetadata"));
    }

    private void registerExplainabilityAddonClasses(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.explainability.model.PredictOutput"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.explainability.model.PredictInput"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.explainability.model.ModelIdentifier"));
    }

    private void addChildrenClasses(IndexView index,
            String superClass,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        index.getAllKnownSubclasses(DotName.createSimple(superClass))
                .forEach(c -> reflectiveClass.produce(
                        new ReflectiveClassBuildItem(true, true, c.name().toString())));
    }

    private KogitoGeneratedClassesBuildItem indexBuildItems(KogitoBuildContext context, Collection<GeneratedBeanBuildItem> buildItems) {
        Indexer kogitoIndexer = new Indexer();
        Set<DotName> kogitoIndex = new HashSet<>();

        for (GeneratedBeanBuildItem generatedBeanBuildItem : buildItems) {
            IndexingUtil.indexClass(
                    generatedBeanBuildItem.getName(),
                    kogitoIndexer,
                    combinedIndexBuildItem.getIndex(),
                    kogitoIndex,
                    context.getClassLoader(),
                    generatedBeanBuildItem.getData());
        }

        Map<String, byte[]> generatedClasses = buildItems.stream().collect(Collectors.toMap(GeneratedBeanBuildItem::getName, GeneratedBeanBuildItem::getData));

        return new KogitoGeneratedClassesBuildItem(kogitoIndexer.complete(), generatedClasses);
    }

}
