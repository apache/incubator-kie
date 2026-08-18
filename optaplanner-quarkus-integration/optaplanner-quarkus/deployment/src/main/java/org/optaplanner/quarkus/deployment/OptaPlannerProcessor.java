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

package org.optaplanner.quarkus.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;
import org.optaplanner.core.api.domain.common.DomainAccessType;
import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.score.calculator.EasyScoreCalculator;
import org.optaplanner.core.api.score.calculator.IncrementalScoreCalculator;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.ConstraintStreamImplType;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.score.director.ScoreDirectorFactoryConfig;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.SolverManagerConfig;
import org.optaplanner.core.impl.domain.solution.descriptor.SolutionDescriptor;
import org.optaplanner.core.impl.io.jaxb.SolverConfigIO;
import org.optaplanner.core.impl.score.director.ScoreDirectorFactoryService;
import org.optaplanner.core.impl.score.stream.JoinerService;
import org.optaplanner.quarkus.OptaPlannerRecorder;
import org.optaplanner.quarkus.bean.DefaultOptaPlannerBeanProvider;
import org.optaplanner.quarkus.bean.UnavailableOptaPlannerBeanProvider;
import org.optaplanner.quarkus.config.OptaPlannerRuntimeConfig;
import org.optaplanner.quarkus.deployment.config.OptaPlannerBuildTimeConfig;
import org.optaplanner.quarkus.devui.OptaPlannerDevUIPropertiesRPCService;
import org.optaplanner.quarkus.devui.OptaPlannerDevUIRecorder;
import org.optaplanner.quarkus.devui.SolverConfigText;
import org.optaplanner.quarkus.gizmo.OptaPlannerGizmoBeanFactory;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.deployment.pkg.steps.NativeBuild;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.deployment.util.ServiceUtil;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.InterfaceMethodDesc;
import io.quarkus.gizmo2.desc.MethodDesc;
import io.quarkus.runtime.configuration.ConfigurationException;

class OptaPlannerProcessor {

    private static final Logger log = Logger.getLogger(OptaPlannerProcessor.class.getName());

    OptaPlannerBuildTimeConfig optaPlannerBuildTimeConfig;

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("optaplanner-solver");
    }

    @BuildStep
    void registerSpi(BuildProducer<ServiceProviderBuildItem> services) {
        Stream.of(ScoreDirectorFactoryService.class, JoinerService.class)
                .forEach(service -> registerSpi(service, services));
    }

    private static void registerSpi(Class<?> serviceClass, BuildProducer<ServiceProviderBuildItem> services) {
        String serviceName = serviceClass.getName();
        String service = "META-INF/services/" + serviceName;
        try {
            // Find out all the provider implementation classes listed in the service files.
            Set<String> implementations =
                    ServiceUtil.classNamesNamedIn(Thread.currentThread().getContextClassLoader(), service);
            // Register every listed implementation class, so they can be instantiated in native-image at run-time.
            services.produce(new ServiceProviderBuildItem(serviceName, implementations.toArray(new String[0])));
        } catch (IOException e) {
            throw new IllegalStateException("Impossible state: Failed registering service " + serviceClass.getCanonicalName(),
                    e);
        }
    }

    @BuildStep
    HotDeploymentWatchedFileBuildItem watchSolverConfigXml() {
        String solverConfigXML = optaPlannerBuildTimeConfig.solverConfigXml()
                .orElse(OptaPlannerBuildTimeConfig.DEFAULT_SOLVER_CONFIG_URL);
        return new HotDeploymentWatchedFileBuildItem(solverConfigXML);
    }

    @BuildStep
    HotDeploymentWatchedFileBuildItem watchConstraintsDrl() {
        String constraintsDrl =
                optaPlannerBuildTimeConfig.scoreDrl().orElse(OptaPlannerBuildTimeConfig.DEFAULT_CONSTRAINTS_DRL_URL);
        return new HotDeploymentWatchedFileBuildItem(constraintsDrl);
    }

    @BuildStep
    IndexDependencyBuildItem indexDependencyBuildItem() {
        // Add @PlanningEntity and other annotations in the Jandex index for Gizmo
        return new IndexDependencyBuildItem("org.optaplanner", "optaplanner-core-impl");
    }

    @BuildStep(onlyIf = NativeBuild.class)
    void makeGizmoBeanFactoryUnremovable(BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {
        unremovableBeans.produce(UnremovableBeanBuildItem.beanTypes(OptaPlannerGizmoBeanFactory.class));
    }

    @BuildStep(onlyIfNot = NativeBuild.class)
    DetermineIfNativeBuildItem ifNotNativeBuild() {
        return new DetermineIfNativeBuildItem(false);
    }

    @BuildStep(onlyIf = NativeBuild.class)
    DetermineIfNativeBuildItem ifNativeBuild() {
        return new DetermineIfNativeBuildItem(true);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    @Record(STATIC_INIT)
    public CardPageBuildItem registerDevUICard(
            OptaPlannerDevUIRecorder devUIRecorder,
            SolverConfigBuildItem solverConfigBuildItem,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {
        CardPageBuildItem cardPageBuildItem = new CardPageBuildItem();
        SolverConfig solverConfig = solverConfigBuildItem.getSolverConfig();
        if (solverConfig != null) {
            StringWriter effectiveSolverConfigWriter = new StringWriter();
            SolverConfigIO solverConfigIO = new SolverConfigIO();
            solverConfigIO.write(solverConfig, effectiveSolverConfigWriter);
            syntheticBeans.produce(SyntheticBeanBuildItem.configure(SolverConfigText.class)
                    .scope(ApplicationScoped.class)
                    .supplier(devUIRecorder.solverConfigTextSupplier(effectiveSolverConfigWriter.toString()))
                    .done());
        } else {
            syntheticBeans.produce(SyntheticBeanBuildItem.configure(SolverConfigText.class)
                    .scope(ApplicationScoped.class)
                    .supplier(devUIRecorder.solverConfigTextSupplier(""))
                    .done());
        }
        cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                .title("Configuration")
                .icon("font-awesome-solid:wrench")
                .componentLink("config-component.js"));

        cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                .title("Model")
                .icon("font-awesome-solid:wrench")
                .componentLink("model-component.js"));

        cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                .title("Constraints")
                .icon("font-awesome-solid:wrench")
                .componentLink("constraints-component.js"));

        return cardPageBuildItem;
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    public JsonRPCProvidersBuildItem registerRPCService() {
        return new JsonRPCProvidersBuildItem("OptaPlanner Solver", OptaPlannerDevUIPropertiesRPCService.class);
    }

    /**
     * The DevConsole injects the SolverFactory bean programmatically, which is not detected by ArC. As a result,
     * the bean is removed as unused unless told otherwise via the {@link UnremovableBeanBuildItem}.
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    void makeSolverFactoryUnremovableInDevMode(BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {
        unremovableBeans.produce(UnremovableBeanBuildItem.beanTypes(SolverFactory.class));
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    SolverConfigBuildItem recordAndRegisterBeans(OptaPlannerRecorder recorder, RecorderContext recorderContext,
            DetermineIfNativeBuildItem determineIfNative, CombinedIndexBuildItem combinedIndex,
            OptaPlannerRuntimeConfig optaplannerRuntimeConfig,
            BuildProducer<ReflectiveHierarchyBuildItem> reflectiveHierarchyClass,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            BuildProducer<GeneratedClassBuildItem> generatedClasses,
            BuildProducer<BytecodeTransformerBuildItem> transformers) {
        IndexView indexView = combinedIndex.getIndex();

        // Only skip this extension if everything is missing. Otherwise, if some parts are missing, fail fast later.
        if (indexView.getAnnotations(DotNames.PLANNING_SOLUTION).isEmpty()
                && indexView.getAnnotations(DotNames.PLANNING_ENTITY).isEmpty()) {
            log.warn("Skipping OptaPlanner extension because there are no @" + PlanningSolution.class.getSimpleName()
                    + " or @" + PlanningEntity.class.getSimpleName() + " annotated classes."
                    + "\nIf your domain classes are located in a dependency of this project, maybe try generating"
                    + " the Jandex index by using the jandex-maven-plugin in that dependency, or by adding"
                    + "application.properties entries (quarkus.index-dependency.<name>.group-id"
                    + " and quarkus.index-dependency.<name>.artifact-id).");
            additionalBeans.produce(new AdditionalBeanBuildItem(UnavailableOptaPlannerBeanProvider.class));
            return new SolverConfigBuildItem(null);
        }

        // Quarkus extensions must always use getContextClassLoader()
        // Internally, OptaPlanner defaults the ClassLoader to getContextClassLoader() too
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        SolverConfig solverConfig;
        if (optaPlannerBuildTimeConfig.solverConfigXml().isPresent()) {
            String solverConfigXML = optaPlannerBuildTimeConfig.solverConfigXml().get();
            if (classLoader.getResource(solverConfigXML) == null) {
                throw new ConfigurationException("Invalid quarkus.optaplanner.solverConfigXML property ("
                        + solverConfigXML + "): that classpath resource does not exist.");
            }
            solverConfig = SolverConfig.createFromXmlResource(solverConfigXML);
        } else if (classLoader.getResource(OptaPlannerBuildTimeConfig.DEFAULT_SOLVER_CONFIG_URL) != null) {
            solverConfig = SolverConfig.createFromXmlResource(
                    OptaPlannerBuildTimeConfig.DEFAULT_SOLVER_CONFIG_URL);
        } else {
            solverConfig = new SolverConfig();
        }

        applySolverProperties(indexView, solverConfig);
        assertNoMemberAnnotationWithoutClassAnnotation(indexView);
        assertDrlDisabledInNative(solverConfig, determineIfNative);

        if (solverConfig.getSolutionClass() != null) {
            // Need to register even when using GIZMO so annotations are preserved
            Type jandexType = Type.create(DotName.createSimple(solverConfig.getSolutionClass().getName()), Type.Kind.CLASS);
            reflectiveHierarchyClass.produce(new ReflectiveHierarchyBuildItem.Builder()
                    .type(jandexType)
                    // Ignore only the packages from optaplanner-core
                    // (Can cause a hard to diagnose issue when creating a test/example
                    // in the package "org.optaplanner").
                    .ignoreTypePredicate(
                            dotName -> ReflectiveHierarchyBuildItem.DefaultIgnoreTypePredicate.INSTANCE.test(dotName)
                                    || dotName.toString().startsWith("org.optaplanner.api")
                                    || dotName.toString().startsWith("org.optaplanner.config")
                                    || dotName.toString().startsWith("org.optaplanner.impl"))
                    .build());
        }

        if (determineIfNative.isNative()) {
            // DroolsAlphaNetworkCompilationEnabled is a three-state boolean (null, true, false); if it not
            // null, ScoreDirectorFactoryFactory will throw an error if Drools isn't use (i.e. BAVET or Easy/Incremental)
            if (solverConfig.getScoreDirectorFactoryConfig().getConstraintProviderClass() != null && solverConfig
                    .getScoreDirectorFactoryConfig().getConstraintStreamImplType() != ConstraintStreamImplType.BAVET) {
                disableANC(solverConfig);
            } else if (solverConfig.getScoreDirectorFactoryConfig().getScoreDrlList() != null
                    || solverConfig.getScoreDirectorFactoryConfig().getScoreDrlFileList() == null) {
                disableANC(solverConfig);
            }
        }

        Set<Class<?>> reflectiveClassSet = new LinkedHashSet<>();

        registerClassesFromAnnotations(indexView, reflectiveClassSet);
        registerCustomClassesFromSolverConfig(solverConfig, reflectiveClassSet);
        generateConstraintVerifier(solverConfig, syntheticBeanBuildItemBuildProducer);
        GeneratedGizmoClasses generatedGizmoClasses = generateDomainAccessors(solverConfig, indexView, generatedBeans,
                generatedClasses, transformers, reflectiveClassSet);

        SolverManagerConfig solverManagerConfig = new SolverManagerConfig();

        syntheticBeanBuildItemBuildProducer.produce(SyntheticBeanBuildItem.configure(SolverConfig.class)
                .scope(Singleton.class)
                .defaultBean()
                .setRuntimeInit()
                .supplier(recorder.solverConfigSupplier(solverConfig,
                        GizmoMemberAccessorEntityEnhancer.getGeneratedGizmoMemberAccessorMap(recorderContext,
                                generatedGizmoClasses.generatedGizmoMemberAccessorClassSet),
                        GizmoMemberAccessorEntityEnhancer.getGeneratedSolutionClonerMap(recorderContext,
                                generatedGizmoClasses.generatedGizmoSolutionClonerClassSet)))
                .done());

        syntheticBeanBuildItemBuildProducer.produce(SyntheticBeanBuildItem.configure(SolverManagerConfig.class)
                .scope(Singleton.class)
                .defaultBean()
                .setRuntimeInit()
                .supplier(recorder.solverManagerConfig(solverManagerConfig)).done());

        additionalBeans.produce(new AdditionalBeanBuildItem(DefaultOptaPlannerBeanProvider.class));
        unremovableBeans.produce(UnremovableBeanBuildItem.beanTypes(OptaPlannerRuntimeConfig.class));
        return new SolverConfigBuildItem(solverConfig);
    }

    private void disableANC(SolverConfig solverConfig) {
        if (solverConfig.getScoreDirectorFactoryConfig().getDroolsAlphaNetworkCompilationEnabled() != null
                && solverConfig.getScoreDirectorFactoryConfig().getDroolsAlphaNetworkCompilationEnabled()) {
            log.warn("Disabling Drools Alpha Network Compiler since this is a native build.");
        }
        solverConfig.getScoreDirectorFactoryConfig().setDroolsAlphaNetworkCompilationEnabled(false);
    }

    private void generateConstraintVerifier(SolverConfig solverConfig,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer) {
        String constraintVerifierClassName = DotNames.CONSTRAINT_VERIFIER.toString();
        if (solverConfig.getScoreDirectorFactoryConfig().getConstraintProviderClass() != null &&
                isClassDefined(constraintVerifierClassName)) {
            final Class<?> constraintProviderClass = solverConfig.getScoreDirectorFactoryConfig().getConstraintProviderClass();
            final Class<?> planningSolutionClass = solverConfig.getSolutionClass();
            final List<Class<?>> planningEntityClasses = solverConfig.getEntityClassList();
            // TODO Don't duplicate defaults by using ConstraintVerifier.create(solverConfig) instead
            final ConstraintStreamImplType constraintStreamImplType =
                    solverConfig.getScoreDirectorFactoryConfig().getConstraintStreamImplType();
            Boolean droolsAlphaNetworkCompilationEnabled =
                    solverConfig.getScoreDirectorFactoryConfig().isDroolsAlphaNetworkCompilationEnabled();
            syntheticBeanBuildItemBuildProducer.produce(SyntheticBeanBuildItem.configure(DotNames.CONSTRAINT_VERIFIER)
                    .scope(Singleton.class)
                    .creator(cg -> {
                        BlockCreator bc = cg.createMethod();
                        ClassDesc constraintVerifierDesc = ClassDesc.of(constraintVerifierClassName);
                        Expr constraintProvider = bc.new_(ConstructorDesc.of(constraintProviderClass));

                        Expr planningEntityClassesArray = bc.newArray(Class.class, planningEntityClasses, Const::of);

                        // Got incompatible class change error when trying to invoke static method on
                        // ConstraintVerifier.build(ConstraintProvider, Class, Class...)
                        Expr solutionDescriptor = bc.invokeStatic(
                                MethodDesc.of(SolutionDescriptor.class, "buildSolutionDescriptor",
                                        SolutionDescriptor.class, Class.class, Class[].class),
                                Const.of(planningSolutionClass), planningEntityClassesArray);
                        LocalVar constraintVerifier = bc.localVar("constraintVerifier", bc.new_(
                                ConstructorDesc.of(
                                        ClassDesc.of("org.optaplanner.test.impl.score.stream.DefaultConstraintVerifier"),
                                        ConstraintProvider.class, SolutionDescriptor.class),
                                constraintProvider, solutionDescriptor));

                        if (constraintStreamImplType != null) { // Use the default if not specified.
                            bc.set(constraintVerifier, bc.invokeInterface(
                                    InterfaceMethodDesc.of(constraintVerifierDesc,
                                            "withConstraintStreamImplType",
                                            constraintVerifierDesc,
                                            ClassDesc.of(ConstraintStreamImplType.class.getName())),
                                    constraintVerifier,
                                    Const.of(Enum.EnumDesc.of(
                                            ClassDesc.of(ConstraintStreamImplType.class.getName()),
                                            constraintStreamImplType.name()))));
                        }

                        if (droolsAlphaNetworkCompilationEnabled != null) { // Use the default if not specified.
                            bc.set(constraintVerifier, bc.invokeInterface(
                                    InterfaceMethodDesc.of(constraintVerifierDesc,
                                            "withDroolsAlphaNetworkCompilationEnabled",
                                            constraintVerifierDesc,
                                            ConstantDescs.CD_boolean),
                                    constraintVerifier,
                                    Const.of(droolsAlphaNetworkCompilationEnabled)));
                        }
                        bc.return_(constraintVerifier);
                    })
                    .addType(ParameterizedType.create(DotNames.CONSTRAINT_VERIFIER,
                            new Type[] {
                                    Type.create(DotName.createSimple(constraintProviderClass.getName()), Type.Kind.CLASS),
                                    Type.create(DotName.createSimple(planningSolutionClass.getName()), Type.Kind.CLASS)
                            }, null))
                    .forceApplicationClass()
                    .defaultBean()
                    .done());
        }
    }

    private void applySolverProperties(IndexView indexView, SolverConfig solverConfig) {
        if (solverConfig.getSolutionClass() == null) {
            solverConfig.setSolutionClass(findSolutionClass(indexView));
        }
        if (solverConfig.getEntityClassList() == null) {
            solverConfig.setEntityClassList(findEntityClassList(indexView));
        }
        applyScoreDirectorFactoryProperties(indexView, solverConfig);
        optaPlannerBuildTimeConfig.solver().environmentMode().ifPresent(solverConfig::setEnvironmentMode);
        optaPlannerBuildTimeConfig.solver().daemon().ifPresent(solverConfig::setDaemon);
        optaPlannerBuildTimeConfig.solver().domainAccessType().ifPresent(solverConfig::setDomainAccessType);
        optaPlannerBuildTimeConfig.solver().constraintStreamImplType().ifPresent(solverConfig::withConstraintStreamImplType);

        if (solverConfig.getDomainAccessType() == null) {
            solverConfig.setDomainAccessType(DomainAccessType.GIZMO);
        }
        // Termination properties are set at runtime
    }

    private Class<?> findSolutionClass(IndexView indexView) {
        Collection<AnnotationInstance> annotationInstances = indexView.getAnnotations(DotNames.PLANNING_SOLUTION);
        if (annotationInstances.size() > 1) {
            throw new IllegalStateException("Multiple classes (" + convertAnnotationInstancesToString(annotationInstances)
                    + ") found with a @" + PlanningSolution.class.getSimpleName() + " annotation.");
        }
        if (annotationInstances.isEmpty()) {
            throw new IllegalStateException("No classes (" + convertAnnotationInstancesToString(annotationInstances)
                    + ") found with a @" + PlanningSolution.class.getSimpleName() + " annotation.");
        }
        AnnotationTarget solutionTarget = annotationInstances.iterator().next().target();
        if (solutionTarget.kind() != AnnotationTarget.Kind.CLASS) {
            throw new IllegalStateException("A target (" + solutionTarget
                    + ") with a @" + PlanningSolution.class.getSimpleName() + " must be a class.");
        }
        return convertClassInfoToClass(solutionTarget.asClass());
    }

    private List<Class<?>> findEntityClassList(IndexView indexView) {
        Collection<AnnotationInstance> annotationInstances = indexView.getAnnotations(DotNames.PLANNING_ENTITY);
        if (annotationInstances.isEmpty()) {
            throw new IllegalStateException("No classes (" + convertAnnotationInstancesToString(annotationInstances)
                    + ") found with a @" + PlanningEntity.class.getSimpleName() + " annotation.");
        }
        List<AnnotationTarget> targetList = annotationInstances.stream()
                .map(AnnotationInstance::target)
                .collect(Collectors.toList());
        if (targetList.stream().anyMatch(target -> target.kind() != AnnotationTarget.Kind.CLASS)) {
            throw new IllegalStateException("All targets (" + targetList
                    + ") with a @" + PlanningEntity.class.getSimpleName() + " must be a class.");
        }
        return targetList.stream()
                .map(target -> (Class<?>) convertClassInfoToClass(target.asClass()))
                .collect(Collectors.toList());
    }

    private void assertNoMemberAnnotationWithoutClassAnnotation(IndexView indexView) {
        Collection<AnnotationInstance> optaplannerFieldAnnotations = new HashSet<>();

        for (DotName annotationName : DotNames.PLANNING_ENTITY_FIELD_ANNOTATIONS) {
            optaplannerFieldAnnotations.addAll(indexView.getAnnotations(annotationName));
        }

        for (AnnotationInstance annotationInstance : optaplannerFieldAnnotations) {
            AnnotationTarget annotationTarget = annotationInstance.target();
            ClassInfo declaringClass;
            String prefix;
            switch (annotationTarget.kind()) {
                case FIELD:
                    prefix = "The field (" + annotationTarget.asField().name() + ") ";
                    declaringClass = annotationTarget.asField().declaringClass();
                    break;
                case METHOD:
                    prefix = "The method (" + annotationTarget.asMethod().name() + ") ";
                    declaringClass = annotationTarget.asMethod().declaringClass();
                    break;
                default:
                    throw new IllegalStateException(
                            "Member annotation @" + annotationInstance.name().withoutPackagePrefix() + " is on ("
                                    + annotationTarget +
                                    "), which is an invalid target type (" + annotationTarget.kind() +
                                    ") for @" + annotationInstance.name().withoutPackagePrefix() + ".");
            }

            if (!declaringClass.annotationsMap().containsKey(DotNames.PLANNING_ENTITY)) {
                throw new IllegalStateException(prefix + "with a @" +
                        annotationInstance.name().withoutPackagePrefix() +
                        " annotation is in a class (" + declaringClass.name()
                        + ") that does not have a @" + PlanningEntity.class.getSimpleName() +
                        " annotation.\n" +
                        "Maybe add a @" + PlanningEntity.class.getSimpleName() +
                        " annotation on the class (" + declaringClass.name() + ").");
            }
        }
    }

    private void assertDrlDisabledInNative(SolverConfig solverConfig, DetermineIfNativeBuildItem determineIfNative) {
        if (!determineIfNative.isNative()) {
            return;
        }
        if (solverConfig.getScoreDirectorFactoryConfig().getScoreDrlList() == null) {
            return;
        }
        throw new IllegalStateException("Score DRL is not supported during native build.\n" +
                "Consider switching to Constraint Streams.");
    }

    private void registerClassesFromAnnotations(IndexView indexView, Set<Class<?>> reflectiveClassSet) {
        for (DotNames.BeanDefiningAnnotations beanDefiningAnnotation : DotNames.BeanDefiningAnnotations.values()) {
            for (AnnotationInstance annotationInstance : indexView
                    .getAnnotations(beanDefiningAnnotation.getAnnotationDotName())) {
                for (String parameterName : beanDefiningAnnotation.getParameterNames()) {
                    AnnotationValue value = annotationInstance.value(parameterName);

                    // We don't care about the default/null type.
                    if (value != null) {
                        Type type = value.asClass();
                        try {
                            Class<?> beanClass = Class.forName(type.name().toString(), false,
                                    Thread.currentThread().getContextClassLoader());
                            reflectiveClassSet.add(beanClass);
                        } catch (ClassNotFoundException e) {
                            throw new IllegalStateException("Cannot find bean class (" + type.name() +
                                    ") referenced in annotation (" + annotationInstance + ").");
                        }
                    }
                }
            }
        }
    }

    protected void applyScoreDirectorFactoryProperties(IndexView indexView, SolverConfig solverConfig) {
        Optional<String> constraintsDrlFromProperty = constraintsDrl();
        Optional<String> defaultConstraintsDrl = defaultConstraintsDrl();
        Optional<String> effectiveConstraintsDrl = constraintsDrlFromProperty.or(() -> defaultConstraintsDrl);
        if (solverConfig.getScoreDirectorFactoryConfig() == null) {
            ScoreDirectorFactoryConfig scoreDirectorFactoryConfig =
                    defaultScoreDirectoryFactoryConfig(indexView, effectiveConstraintsDrl);
            solverConfig.setScoreDirectorFactoryConfig(scoreDirectorFactoryConfig);
        } else {
            ScoreDirectorFactoryConfig scoreDirectorFactoryConfig = solverConfig.getScoreDirectorFactoryConfig();
            if (constraintsDrlFromProperty.isPresent()) {
                scoreDirectorFactoryConfig.setScoreDrlList(Collections.singletonList(constraintsDrlFromProperty.get()));
            } else {
                if (scoreDirectorFactoryConfig.getScoreDrlList() == null) {
                    defaultConstraintsDrl.ifPresent(resolvedConstraintsDrl -> scoreDirectorFactoryConfig
                            .setScoreDrlList(Collections.singletonList(resolvedConstraintsDrl)));
                }
            }
        }

        if (solverConfig.getScoreDirectorFactoryConfig().getKieBaseConfigurationProperties() != null) {
            throw new IllegalStateException("Using kieBaseConfigurationProperties ("
                    + solverConfig.getScoreDirectorFactoryConfig().getKieBaseConfigurationProperties()
                    + ") in Quarkus, which is unsupported.");
        }
    }

    private boolean isClassDefined(String className) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    protected Optional<String> constraintsDrl() {
        if (optaPlannerBuildTimeConfig.scoreDrl().isPresent()) {
            String constraintsDrl = optaPlannerBuildTimeConfig.scoreDrl().get();
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader.getResource(constraintsDrl) == null) {
                throw new IllegalStateException("Invalid " + OptaPlannerBuildTimeConfig.CONSTRAINTS_DRL_PROPERTY
                        + " property (" + constraintsDrl + "): that classpath resource does not exist.");
            }
        }
        return optaPlannerBuildTimeConfig.scoreDrl();
    }

    protected Optional<String> defaultConstraintsDrl() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader.getResource(OptaPlannerBuildTimeConfig.DEFAULT_CONSTRAINTS_DRL_URL) != null
                ? Optional.of(OptaPlannerBuildTimeConfig.DEFAULT_CONSTRAINTS_DRL_URL)
                : Optional.empty();
    }

    private ScoreDirectorFactoryConfig defaultScoreDirectoryFactoryConfig(IndexView indexView, Optional<String> constrainsDrl) {
        ScoreDirectorFactoryConfig scoreDirectorFactoryConfig = new ScoreDirectorFactoryConfig();
        scoreDirectorFactoryConfig.setEasyScoreCalculatorClass(
                findImplementingClass(DotNames.EASY_SCORE_CALCULATOR, indexView));
        scoreDirectorFactoryConfig.setConstraintProviderClass(
                findImplementingClass(DotNames.CONSTRAINT_PROVIDER, indexView));
        scoreDirectorFactoryConfig.setIncrementalScoreCalculatorClass(
                findImplementingClass(DotNames.INCREMENTAL_SCORE_CALCULATOR, indexView));
        constrainsDrl.ifPresent(value -> scoreDirectorFactoryConfig.setScoreDrlList(Collections.singletonList(value)));
        if (scoreDirectorFactoryConfig.getEasyScoreCalculatorClass() == null
                && scoreDirectorFactoryConfig.getConstraintProviderClass() == null
                && scoreDirectorFactoryConfig.getIncrementalScoreCalculatorClass() == null
                && scoreDirectorFactoryConfig.getScoreDrlList() == null) {
            throw new IllegalStateException("No classes found that implement "
                    + EasyScoreCalculator.class.getSimpleName() + ", "
                    + ConstraintProvider.class.getSimpleName() + " or "
                    + IncrementalScoreCalculator.class.getSimpleName() + ".\n"
                    + "Neither was a property " + OptaPlannerBuildTimeConfig.CONSTRAINTS_DRL_PROPERTY + " defined, nor a "
                    + OptaPlannerBuildTimeConfig.DEFAULT_CONSTRAINTS_DRL_URL + " resource found.\n");
        }
        return scoreDirectorFactoryConfig;
    }

    private <T> Class<? extends T> findImplementingClass(DotName targetDotName, IndexView indexView) {
        Collection<ClassInfo> classInfos = indexView.getAllKnownImplementors(targetDotName);
        if (classInfos.size() > 1) {
            throw new IllegalStateException("Multiple classes (" + convertClassInfosToString(classInfos)
                    + ") found that implement the interface " + targetDotName + ".");
        }
        if (classInfos.isEmpty()) {
            return null;
        }
        ClassInfo classInfo = classInfos.iterator().next();
        return convertClassInfoToClass(classInfo);
    }

    private String convertAnnotationInstancesToString(Collection<AnnotationInstance> annotationInstances) {
        return "[" + annotationInstances.stream().map(instance -> instance.target().toString())
                .collect(Collectors.joining(", ")) + "]";
    }

    private String convertClassInfosToString(Collection<ClassInfo> classInfos) {
        return "[" + classInfos.stream().map(instance -> instance.name().toString())
                .collect(Collectors.joining(", ")) + "]";
    }

    private <T> Class<? extends T> convertClassInfoToClass(ClassInfo classInfo) {
        String className = classInfo.name().toString();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            return (Class<? extends T>) classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("The class (" + className
                    + ") cannot be created during deployment.", e);
        }
    }

    private GeneratedGizmoClasses generateDomainAccessors(SolverConfig solverConfig, IndexView indexView,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            BuildProducer<GeneratedClassBuildItem> generatedClasses,
            BuildProducer<BytecodeTransformerBuildItem> transformers, Set<Class<?>> reflectiveClassSet) {
        // Use mvn quarkus:dev -Dquarkus.debug.generated-classes-dir=dump-classes
        // to dump generated classes
        ClassOutput classOutput = new GeneratedClassGizmoAdaptor(generatedClasses, true);
        ClassOutput beanClassOutput = new GeneratedBeanGizmoAdaptor(generatedBeans);

        Set<String> generatedMemberAccessorsClassNameSet = new HashSet<>();
        Set<String> gizmoSolutionClonerClassNameSet = new HashSet<>();

        GizmoMemberAccessorEntityEnhancer entityEnhancer = new GizmoMemberAccessorEntityEnhancer();
        if (solverConfig.getDomainAccessType() == DomainAccessType.GIZMO) {
            Collection<AnnotationInstance> membersToGeneratedAccessorsFor = new ArrayList<>();

            for (DotName dotName : DotNames.GIZMO_MEMBER_ACCESSOR_ANNOTATIONS) {
                membersToGeneratedAccessorsFor.addAll(indexView.getAnnotations(dotName));
            }
            membersToGeneratedAccessorsFor.removeIf(this::shouldIgnoreMember);

            for (AnnotationInstance annotatedMember : membersToGeneratedAccessorsFor) {
                switch (annotatedMember.target().kind()) {
                    case FIELD: {
                        FieldInfo fieldInfo = annotatedMember.target().asField();
                        ClassInfo classInfo = fieldInfo.declaringClass();

                        try {
                            generatedMemberAccessorsClassNameSet.add(
                                    entityEnhancer.generateFieldAccessor(annotatedMember, classOutput, fieldInfo,
                                            transformers));
                        } catch (ClassNotFoundException | NoSuchFieldException e) {
                            throw new IllegalStateException("Fail to generate member accessor for field (" +
                                    fieldInfo.name() + ") of the class( " +
                                    classInfo.name().toString() + ").", e);
                        }
                        break;
                    }
                    case METHOD: {
                        MethodInfo methodInfo = annotatedMember.target().asMethod();
                        ClassInfo classInfo = methodInfo.declaringClass();

                        try {
                            generatedMemberAccessorsClassNameSet.add(entityEnhancer.generateMethodAccessor(annotatedMember,
                                    classOutput, classInfo, methodInfo, transformers));
                        } catch (ClassNotFoundException | NoSuchMethodException e) {
                            throw new IllegalStateException("Failed to generate member accessor for the method (" +
                                    methodInfo.name() + ") of the class (" +
                                    classInfo.name() + ").", e);
                        }
                        break;
                    }
                    default: {
                        throw new IllegalStateException("The member (" + annotatedMember + ") is not on a field or method.");
                    }
                }
            }
            // Using REFLECTION domain access type so OptaPlanner doesn't try to generate GIZMO code
            SolutionDescriptor solutionDescriptor = SolutionDescriptor.buildSolutionDescriptor(DomainAccessType.REFLECTION,
                    solverConfig.getSolutionClass(), null, null, solverConfig.getEntityClassList());
            gizmoSolutionClonerClassNameSet
                    .add(entityEnhancer.generateSolutionCloner(solutionDescriptor, classOutput, indexView, transformers));
        }

        entityEnhancer.generateGizmoBeanFactory(beanClassOutput, reflectiveClassSet, transformers);
        return new GeneratedGizmoClasses(generatedMemberAccessorsClassNameSet, gizmoSolutionClonerClassNameSet);
    }

    private boolean shouldIgnoreMember(AnnotationInstance annotationInstance) {
        switch (annotationInstance.target().kind()) {
            case FIELD:
                return (annotationInstance.target().asField().flags() & Modifier.STATIC) != 0;
            case METHOD:
                return (annotationInstance.target().asMethod().flags() & Modifier.STATIC) != 0;
            default:
                throw new IllegalArgumentException(
                        "Annotation (" + annotationInstance.name() + ") can only be applied to methods and fields.");
        }
    }

    private void registerCustomClassesFromSolverConfig(SolverConfig solverConfig, Set<Class<?>> reflectiveClassSet) {
        solverConfig.visitReferencedClasses(clazz -> {
            if (clazz != null) {
                reflectiveClassSet.add(clazz);
            }
        });
    }

}
