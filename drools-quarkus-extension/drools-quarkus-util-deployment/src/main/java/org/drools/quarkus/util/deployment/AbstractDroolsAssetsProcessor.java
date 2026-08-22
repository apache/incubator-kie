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
package org.drools.quarkus.util.deployment;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.ArchiveRootBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.maven.dependency.DependencyFlags;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.resteasy.reactive.spi.GeneratedJaxRsResourceBuildItem;
import io.quarkus.vertx.http.deployment.spi.AdditionalStaticResourceBuildItem;
import org.drools.codegen.common.DroolsModelBuildContext;
import org.drools.codegen.common.GeneratedFile;
import org.drools.codegen.common.GeneratedFileType;
import org.drools.compiler.kproject.models.KieBaseModelImpl;
import org.drools.model.codegen.execmodel.PackageModel;
import org.drools.model.codegen.project.RuleCodegen;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieSessionModel;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.conf.KieBaseMutabilityOption;
import org.kie.api.conf.PrototypesOption;
import org.kie.api.conf.SessionsPoolOption;
import org.kie.api.io.Resource;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drools.model.codegen.project.RuleCodegen.ofResources;
import static org.drools.quarkus.util.deployment.DroolsQuarkusResourceUtils.HOT_RELOAD_SUPPORT_PATH;
import static org.drools.quarkus.util.deployment.DroolsQuarkusResourceUtils.compileGeneratedSources;
import static org.drools.quarkus.util.deployment.DroolsQuarkusResourceUtils.createDroolsBuildContext;
import static org.drools.quarkus.util.deployment.DroolsQuarkusResourceUtils.dumpFilesToDisk;
import static org.drools.quarkus.util.deployment.DroolsQuarkusResourceUtils.getHotReloadSupportSource;
import static org.drools.quarkus.util.deployment.DroolsQuarkusResourceUtils.getRuleUnitDefProducerSource;
import static org.drools.quarkus.util.deployment.DroolsQuarkusResourceUtils.registerResources;
import static org.drools.quarkus.util.deployment.DroolsQuarkusResourceUtils.toClassName;

public class AbstractDroolsAssetsProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDroolsAssetsProcessor.class);



    private static final String CONFIG_PREFIX = "drools.kbase.";

    private static final String ASSET_DEPENDENCIES_CONFIG = "drools.asset.dependencies";


    public void generateSources( ArchiveRootBuildItem root,
    	    LiveReloadBuildItem liveReload,
    	    CurateOutcomeBuildItem curateOutcomeBuildItem,
    	    CombinedIndexBuildItem combinedIndexBuildItem,
    	    Capabilities capabilities,
    		BuildProducer<GeneratedBeanBuildItem> generatedBeans,
                                 BuildProducer<NativeImageResourceBuildItem> resource,
                                 BuildProducer<AdditionalStaticResourceBuildItem> staticResProducer,
                                 BuildProducer<GeneratedResourceBuildItem> genResBI,
                                 BuildProducer<PatternsTypesBuildItem> otnClasesBI,
                                 BuildProducer<KmoduleKieBaseModelsBuiltItem> kbaseModelsBI,
                                 BuildProducer<GlobalsBuildItem> globalsBI,
                                 BuildProducer<GeneratedJaxRsResourceBuildItem> jaxrsProducer) {

        DroolsModelBuildContext context = createDroolsBuildContext(root.getResolvedPaths(), combinedIndexBuildItem.getIndex());

        Path[] assetPaths = getAssetPaths(context, curateOutcomeBuildItem);
        Collection<Resource> resources = ResourceCollector.fromPaths(assetPaths);

        RuleCodegen ruleCodegen = ofResources(context, resources).withKieBaseModels(readKieBaseModels());
        Collection<GeneratedFile> generatedFiles = ruleCodegen.generate();
        generatedFiles.addAll(getRuleUnitDefProducerSource(combinedIndexBuildItem.getIndex()));

        // The HotReloadSupportClass has to be generated only during the first model generation
        // During actual hot reloads it will be regenerated by the compilation providers in order to retrigger this build step
        if (!liveReload.isLiveReload()) {
            generatedFiles.add(new GeneratedFile(GeneratedFileType.SOURCE, HOT_RELOAD_SUPPORT_PATH + ".java", getHotReloadSupportSource()));
        }

        // dump files to disk
        dumpFilesToDisk(context.getAppPaths(), generatedFiles);

        Collection<ResolvedDependency> dependencies = curateOutcomeBuildItem.getApplicationModel().getRuntimeDependencies();

        // build Java source code and register the generated beans
        Collection<GeneratedBeanBuildItem> generatedBeanBuildItems =
                compileGeneratedSources(context, dependencies, generatedFiles, liveReload.isLiveReload());
        Set<String> restResourceClassNameSet = generatedFiles.stream()
                .filter(file -> file.type() == GeneratedFileType.REST)
                .map(file -> toClassName(file.path().toString()))
                .collect(Collectors.toSet());
        // Reactive REST re-registers JaxRs resources as beans (duplicate-class check); classic ignores that channel, so REST
        // classes need bean registration there. Capability.REST comes from BOTH stacks; only RESTEASY_REACTIVE is reactive-specific.
        boolean reactiveRestPresent = capabilities.isPresent(Capability.RESTEASY_REACTIVE);
        generatedBeanBuildItems.stream()
                .filter(b -> !reactiveRestPresent || !restResourceClassNameSet.contains(b.getName()))
                .forEach(generatedBeans::produce);

        registerResources(generatedFiles, staticResProducer, resource, genResBI);
        
        otnClasesBI.produce(new PatternsTypesBuildItem(ruleCodegen.getPackageModels().stream().collect(Collectors.toMap(PackageModel::getName, PackageModel::getOtnsClasses))));
        if (ruleCodegen.hasKieBaseModels()) {
            kbaseModelsBI.produce(new KmoduleKieBaseModelsBuiltItem(ruleCodegen.getKmoduleKieBaseModels()));
        }
        globalsBI.produce(new GlobalsBuildItem(ruleCodegen.getPackageModels().stream().collect(Collectors.toMap(PackageModel::getName, PackageModel::getGlobals))));

        generatedBeanBuildItems.stream()
                .filter(b -> restResourceClassNameSet.contains(b.getName()))
                .forEach(b -> jaxrsProducer.produce(new GeneratedJaxRsResourceBuildItem(b.getName(), b.getData())));
    }

    private Path[] getAssetPaths(DroolsModelBuildContext context, CurateOutcomeBuildItem curateOutcomeBuildItem) {
        Path[] appPaths = context.getAppPaths().getPaths();

        Config config = ConfigProvider.getConfig();
        Optional<List<String>> assetDependenciesOpt = config.getOptionalValues(ASSET_DEPENDENCIES_CONFIG, String.class);
        if (assetDependenciesOpt.isPresent()) {
            List<String> assetDependencies = assetDependenciesOpt.get().stream().map(String::trim).toList();
            Iterable<ResolvedDependency> directDependenciesIter = curateOutcomeBuildItem.getApplicationModel().getDependencies(DependencyFlags.DIRECT);
            List<ResolvedDependency> directDependencies = StreamSupport.stream(directDependenciesIter.spliterator(), false).toList();
            Path[] assetDependencyPaths = directDependencies.stream()
                    .filter(d -> assetDependencies.contains(d.getGroupId() + ":" + d.getArtifactId()))
                    .flatMap(d -> d.getResolvedPaths().stream())
                    .toArray(Path[]::new);
            LOGGER.debug("assetDependencyPaths: {}", Arrays.asList(assetDependencyPaths));
            return Stream.concat(Arrays.stream(assetDependencyPaths), Arrays.stream(appPaths)).toArray(Path[]::new);
        } else {
            return appPaths;
        }
    }

    private Map<String, KieBaseModel> readKieBaseModels() {
        Map<String, KieBaseModel> kieBaseModels = new HashMap<>();
        Config config = ConfigProvider.getConfig();

        for (String propertyName : config.getPropertyNames()) {
            if (!propertyName.startsWith(CONFIG_PREFIX)) {
                continue;
            }

            String[] splitProp = propertyName.substring(CONFIG_PREFIX.length()).split("\\.");
            if (splitProp.length < 2) {
                LOGGER.error("Malformed Drools property: " + propertyName);
                continue;
            }

            String kBaseName = splitProp[0];
            KieBaseModel kieBaseModel = kieBaseModels.computeIfAbsent(kBaseName, KieBaseModelImpl::new);
            switch (splitProp[1]) {
                case "packages":
                    for (String pkg : config.getValue(propertyName, String.class).split("\\,")) {
                        kieBaseModel.addPackage(pkg);
                    }
                    break;
                case "default":
                    kieBaseModel.setDefault( config.getValue(propertyName, Boolean.class) );
                    break;
                case "prototypes":
                    kieBaseModel.setPrototypes(PrototypesOption.determinePrototypesOption(config.getValue(propertyName, String.class)));
                    break;
                case "eventProcessingMode":
                    kieBaseModel.setEventProcessingMode(EventProcessingOption.determineEventProcessingMode(config.getValue(propertyName, String.class)));
                    break;
                case "mutability":
                    kieBaseModel.setMutability(KieBaseMutabilityOption.determineMutability(config.getValue(propertyName, String.class)));
                    break;
                case "sessionsPool":
                    kieBaseModel.setSessionsPool(SessionsPoolOption.get(config.getValue(propertyName, Integer.class)));
                    break;
                case "ksessions":
                    for (String ksession : config.getValue(propertyName, String.class).split("\\,")) {
                        kieBaseModel.newKieSessionModel(ksession);
                    }
                    break;
                case "ksession":
                    if (splitProp.length == 2) {
                        kieBaseModel.newKieSessionModel(config.getValue(propertyName, String.class));
                    } else {
                        if (splitProp.length < 4) {
                            LOGGER.error("Malformed Drools property: " + propertyName);
                            break;
                        }

                        String kSessionName = splitProp[2];
                        KieSessionModel kieSessionModel = kieBaseModel.getKieSessionModels().get(kSessionName);
                        if (kieSessionModel == null) {
                            kieSessionModel = kieBaseModel.newKieSessionModel(kSessionName);
                        }

                        switch (splitProp[3]) {
                            case "default":
                                kieSessionModel.setDefault( config.getValue(propertyName, Boolean.class) );
                                break;
                            case "stateless":
                                kieSessionModel.setType( config.getValue(propertyName, Boolean.class) ? KieSessionModel.KieSessionType.STATELESS : KieSessionModel.KieSessionType.STATEFUL );
                                break;
                            case "clockType":
                                kieSessionModel.setClockType( ClockTypeOption.get(config.getValue(propertyName, String.class) ) );
                                break;
                        }
                    }
                    break;
            }

        }

        return kieBaseModels;
    }
}
