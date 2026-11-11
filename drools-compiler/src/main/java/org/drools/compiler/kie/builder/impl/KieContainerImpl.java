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
package org.drools.compiler.kie.builder.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.ObjectName;

import org.drools.base.RuleBase;
import org.drools.compiler.builder.InternalKnowledgeBuilder;
import org.drools.compiler.builder.impl.KnowledgeBuilderConfigurationImpl;
import org.drools.compiler.kie.builder.MaterializedLambda;
import org.drools.compiler.kie.util.KieJarChangeSet;
import org.drools.compiler.kproject.models.KieBaseModelImpl;
import org.drools.compiler.kproject.models.KieSessionModelImpl;
import org.drools.compiler.management.KieContainerMonitor;
import org.drools.core.SessionConfiguration;
import org.drools.core.impl.InternalKieContainer;
import org.drools.core.management.DroolsManagementAgent;
import org.drools.core.management.DroolsManagementAgent.CBSKey;
import org.drools.core.reteoo.RuntimeComponentFactory;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.drools.kiesession.session.StatefulSessionPool;
import org.drools.kiesession.session.StatelessKnowledgeSessionImpl;
import org.drools.util.ClassUtils;
import org.drools.wiring.api.classloader.ProjectClassLoader;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.KieRepository;
import org.kie.api.builder.Message;
import org.kie.api.builder.Message.Level;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.Results;
import org.kie.api.builder.model.FileLoggerModel;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieSessionModel;
import org.kie.api.conf.MBeansOption;
import org.kie.api.event.KieRuntimeEventManager;
import org.kie.api.internal.utils.KieService;
import org.kie.api.io.ResourceType;
import org.kie.api.logger.KieLoggers;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.KieContainerSessionsPool;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.api.time.Calendar;
import org.kie.internal.builder.ChangeType;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.builder.ResourceChange;
import org.kie.internal.builder.ResourceChangeSet;
import org.kie.internal.builder.conf.AlphaNetworkCompilerOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;
import static org.drools.base.util.Drools.isJndiAvailable;
import static org.drools.compiler.kie.util.InjectionHelper.wireSessionComponents;
import static org.drools.util.ClassUtils.convertResourceToClassName;

public class KieContainerImpl
        implements
        InternalKieContainer {

    private static final Logger log = LoggerFactory.getLogger( KieContainerImpl.class );

    private KieProject kProject;

    private final Map<String, KieBase> kBases = new ConcurrentHashMap<>();

    private final Map<String, KieSession> kSessions = new ConcurrentHashMap<>();
    private final Map<String, StatelessKieSession> statelessKSessions = new ConcurrentHashMap<>();

    private final KieRepository        kr;

    private ReleaseId configuredReleaseId;
    private ReleaseId containerReleaseId;

    private final String containerId;

    private final Map<String, KieSessionConfiguration> sessionConfsCache = new ConcurrentHashMap<>();

    public KieModule getMainKieModule() {
        return kr.getKieModule(getReleaseId());
    }

    /**
     * Please note: the recommended way of getting a KieContainer is relying on {@link org.kie.api.KieServices KieServices} API,
     * for example: {@link org.kie.api.KieServices#newKieContainer(ReleaseId) KieServices.newKieContainer(...)}.
     * The direct manual call to KieContainerImpl constructor instead would not guarantee the consistency of the supplied containerId.
     */
    public KieContainerImpl(KieProject kProject, KieRepository kr) {
        this("impl"+UUID.randomUUID(), kProject, kr);
    }

    /**
     * Please note: the recommended way of getting a KieContainer is relying on {@link org.kie.api.KieServices KieServices} API,
     * for example: {@link org.kie.api.KieServices#newKieContainer(ReleaseId) KieServices.newKieContainer(...)}.
     * The direct manual call to KieContainerImpl constructor instead would not guarantee the consistency of the supplied containerId.
     */
    public KieContainerImpl(KieProject kProject, KieRepository kr, ReleaseId containerReleaseId) {
        this("impl"+UUID.randomUUID(), kProject, kr, containerReleaseId);
    }

    /**
     * Please note: the recommended way of getting a KieContainer is relying on {@link org.kie.api.KieServices KieServices} API,
     * for example: {@link org.kie.api.KieServices#newKieContainer(ReleaseId) KieServices.newKieContainer(...)}.
     * The direct manual call to KieContainerImpl constructor instead would not guarantee the consistency of the supplied containerId.
     */
    public KieContainerImpl(String containerId, KieProject kProject, KieRepository kr) {
        this.kr = kr;
        this.kProject = kProject;
        this.containerId = containerId;
        kProject.init();
        initMBeans(containerId);
    }

    /**
     * Please note: the recommended way of getting a KieContainer is relying on {@link org.kie.api.KieServices KieServices} API,
     * for example: {@link org.kie.api.KieServices#newKieContainer(ReleaseId) KieServices.newKieContainer(...)}.
     * The direct manual call to KieContainerImpl constructor instead would not guarantee the consistency of the supplied containerId.
     */
    public KieContainerImpl(String containerId, KieProject kProject, KieRepository kr, ReleaseId containerReleaseId) {
        this(containerId, kProject, kr);
        this.configuredReleaseId = containerReleaseId;
        this.containerReleaseId = containerReleaseId;
    }

    private void initMBeans(String containerId) {
        if ( isMBeanOptionEnabled() ) {
            KieContainerMonitor monitor = new KieContainerMonitor(this);
            ObjectName on = DroolsManagementAgent.createObjectNameBy(containerId);
            DroolsManagementAgent.getInstance().registerMBean( this, monitor, on );
        }
    }

    @Override
    public String getContainerId() {
        return this.containerId;
    }

    @Override
    public ReleaseId getConfiguredReleaseId() {
        return configuredReleaseId;
    }

    @Override
    public ReleaseId getResolvedReleaseId() {
        return getReleaseId();
    }

    public ReleaseId getReleaseId() {
        return kProject.getGAV();
    }

    public InputStream getPomAsStream() {
        return kProject.getPomAsStream();
    }

    public long getCreationTimestamp() {
        return kProject.getCreationTimestamp();
    }

    @Override
    public ReleaseId getContainerReleaseId() {
        return containerReleaseId != null ? containerReleaseId : getReleaseId();
    }

    public Results updateToVersion(ReleaseId newReleaseId) {
        checkNotClasspathKieProject();
        Results results = update(((KieModuleKieProject) kProject).getInternalKieModule(), newReleaseId);
        if (results != null) {
            containerReleaseId = newReleaseId;
        } else {
            results = new ResultsImpl();
            ( (ResultsImpl) results ).addMessage( Message.Level.ERROR, null, "Cannot find KieModule with ReleaseId: " + newReleaseId );
        }
        return results;
    }

    public Results updateToKieModule(InternalKieModule newKM) {
        checkNotClasspathKieProject();
        Results results = update(((KieModuleKieProject) kProject).getInternalKieModule(), newKM);
        containerReleaseId = newKM.getReleaseId();
        return results;
    }

    public Results updateDependencyToVersion(ReleaseId currentReleaseId, ReleaseId newReleaseId) {
        ReleaseId installedReleaseId = getReleaseId();
        if (currentReleaseId.getGroupId().equals(installedReleaseId.getGroupId()) &&
                currentReleaseId.getArtifactId().equals(installedReleaseId.getArtifactId())) {
            // upgrading the kProject itself: taking the kmodule from there
            return updateToVersion(newReleaseId);
        }

        checkNotClasspathKieProject();
        // upgrading a transitive dependency: taking the kmodule from the krepo
        // if the new and the current release are equal (a snapshot) check if there is an older version with the same releaseId
        InternalKieModule currentKM = currentReleaseId.equals(newReleaseId) ?
                (InternalKieModule) ((KieRepositoryImpl) kr).getOldKieModule(currentReleaseId) :
                (InternalKieModule) kr.getKieModule(currentReleaseId);
        return update(currentKM, newReleaseId);
    }

    private void checkNotClasspathKieProject() {
        if( kProject instanceof ClasspathKieProject) {
            throw new UnsupportedOperationException( "It is not possible to update a classpath container to a new version." );
        }
    }

    private Results update(final InternalKieModule currentKM, final ReleaseId newReleaseId) {
        final InternalKieModule newKM = (InternalKieModule) kr.getKieModule( newReleaseId );
        return newKM == null ? null : update( currentKM, newKM );
    }

    private Results update( InternalKieModule currentKM, InternalKieModule newKM ) {
        final KieJarChangeSet cs = currentKM.getChanges( newKM );
        List<String> modifiedClassNames = getModifiedClasses(cs);
        final boolean modifyingUsedClass = isModifyingUsedClass( modifiedClassNames, getClassLoader() ) || isModifyingUsedFunction(cs);
        final Collection<Class<?>> modifiedClasses = reinitModifiedClasses( newKM, modifiedClassNames, getClassLoader(), modifyingUsedClass );
        final Collection<String> unchangedResources = getUnchangedResources( newKM, cs );

        Map<String, KieBaseModel> currentKieBaseModels = ((KieModuleKieProject ) kProject).updateToModule( newKM );

        final ResultsImpl results = new ResultsImpl();

        currentKM.updateKieModule(newKM);

        List<String> kbasesToRemove = new ArrayList<>();
        for ( Entry<String, KieBase> kBaseEntry : kBases.entrySet() ) {
            String kbaseName = kBaseEntry.getKey();
            KieBaseModelImpl newKieBaseModel = (KieBaseModelImpl) kProject.getKieBaseModel( kbaseName );
            KieBaseModelImpl currentKieBaseModel = (KieBaseModelImpl) currentKieBaseModels.get( kbaseName );
            // if a kbase no longer exists, just remove it from the cache
            if ( newKieBaseModel == null ) {
                // have to save for later removal to avoid iteration errors
                kbasesToRemove.add( kbaseName );
            } else {
                final InternalKnowledgeBase kBase = (InternalKnowledgeBase) kBaseEntry.getValue();

                // share Knowledge Builder among updater as it's computationally expensive to create this
                KnowledgeBuilderConfigurationImpl builderConfiguration =
                        (KnowledgeBuilderConfigurationImpl) newKM.createBuilderConfiguration(newKieBaseModel, kBase.getRootClassLoader());
                InternalKnowledgeBuilder kbuilder =
                        (InternalKnowledgeBuilder) KnowledgeBuilderFactory.newKnowledgeBuilder(kBase, builderConfiguration);

                KieBaseUpdaterImplContext context = new KieBaseUpdaterImplContext(kProject, kBase, currentKM, newKM,
                                                                                  cs, modifiedClasses, modifyingUsedClass, unchangedResources,
                                                                                  results, newKieBaseModel, currentKieBaseModel, kbuilder);

                // Multiple updaters are required to be merged together in a single Runnable
                // to avoid a deadlock while using .fireUntilHalt()
                // see IncrementalCompilationTest.testMultipleIncrementalCompilationsWithFireUntilHalt
                // with multiple updaters (such as Alpha NetworkCompilerUpdater)
                CompositeRunnable compositeUpdater = new CompositeRunnable();
                KieBaseUpdater kieBaseUpdater = currentKM.createKieBaseUpdater(context);

                compositeUpdater.add(kieBaseUpdater);

                KieBaseUpdaterOptions kieBaseUpdaterOptions = new KieBaseUpdaterOptions(new KieBaseUpdaterOptions.OptionEntry(
                        AlphaNetworkCompilerOption.class, builderConfiguration.getOption(AlphaNetworkCompilerOption.KEY)));

                KieBaseUpdaters updaters = KieService.load(KieBaseUpdaters.class);
                updaters.getChildren()
                        .stream()
                        .map(kbu -> kbu.create(new KieBaseUpdatersContext(kieBaseUpdaterOptions,
                                                                          context.kBase.getRete(),
                                                                          context.kBase.getRootClassLoader()
                                                                          )))
                        .forEach(compositeUpdater::add);

                kBase.enqueueModification(compositeUpdater);

            }
        }

        for (String kbaseToRemove : kbasesToRemove) {
            kBases.remove(kbaseToRemove);
        }

        // remove sessions that no longer exist
        this.kSessions.entrySet().removeIf( ksession -> kProject.getKieSessionModel( ksession.getKey() ) == null );
        this.statelessKSessions.entrySet().removeIf( ksession -> kProject.getKieSessionModel( ksession.getKey() ) == null );

        return results;
    }

    public static class CompositeRunnable implements Runnable {

        private final List<Runnable> runnables = new ArrayList<>();

        public void add(Runnable runnable) {
            runnables.add( runnable );
        }

        void addAll(List<Runnable> runnableList) {
            runnables.addAll( runnableList );
        }

        @Override
        public void run() {
            runnables.forEach( Runnable::run );
        }
    }

    private boolean isModifyingUsedFunction(KieJarChangeSet cs) {
        return cs.getChanges().values()
                .stream()
                .flatMap(resourceChangeSet -> resourceChangeSet.getChanges().stream())
                .anyMatch(change -> change.getType() == ResourceChange.Type.FUNCTION && change.getChangeType() == ChangeType.UPDATED);
    }

    private Collection<String> getUnchangedResources( InternalKieModule newKM, KieJarChangeSet cs ) {
        List<String> dslFiles = new ArrayList<>();
        for (String file : newKM.getFileNames()) {
            if ( includeIfUnchanged( file ) && !cs.contains( file ) ) {
                dslFiles.add(file);
            }
        }
        return dslFiles;
    }

    private static final ResourceType[] TYPES_TO_BE_INCLUDED = new ResourceType[] { ResourceType.DSL, ResourceType.GDRL };

    private boolean includeIfUnchanged( String file ) {
        for (ResourceType type : TYPES_TO_BE_INCLUDED ) {
            if (type.matchesExtension( file )) {
                return true;
            }
        }
        return false;
    }

    private boolean isModifyingUsedClass( List<String> modifiedClasses, ClassLoader classLoader ) {
        return modifiedClasses.stream().anyMatch( c -> isClassInUse( classLoader, convertResourceToClassName(c) ) );
    }

    private boolean isClassInUse(ClassLoader rootClassLoader, String className) {
        return !(rootClassLoader instanceof ProjectClassLoader) || ((ProjectClassLoader) rootClassLoader).isClassInUse(className, MaterializedLambda.class);
    }

    private Collection<Class<?>> reinitModifiedClasses( InternalKieModule newKM, List<String> modifiedClasses, ClassLoader classLoader, boolean modifyingUsedClass ) {
        if (modifiedClasses.isEmpty() || !(classLoader instanceof ProjectClassLoader)) {
            return Collections.emptyList();
        }

        Set<String> reloadedClasses = new HashSet<>(modifiedClasses);

        ProjectClassLoader projectClassLoader = (ProjectClassLoader) classLoader;
        projectClassLoader.clearStore();
        if (modifyingUsedClass) {
            reloadedClasses.addAll( projectClassLoader.reinitTypes().stream().map( ClassUtils::convertClassToResourcePath ).collect( toList() ) );
        }

        List<Class<?>> classes = new ArrayList<>();
        for (String resourceName : reloadedClasses) {
            String className = convertResourceToClassName( resourceName );
            byte[] bytes = newKM.getBytes(resourceName);
            if (bytes != null) {
                Class<?> clazz = projectClassLoader.defineClass( className, resourceName, bytes );
                classes.add( clazz );
            }
        }
        return classes;
    }

    private List<String> getModifiedClasses(KieJarChangeSet cs) {
        List<String> modifiedClasses = new ArrayList<>();
        for ( ResourceChangeSet rcs : cs.getChanges().values() ) {
            if ( rcs.getChangeType() != ChangeType.REMOVED ) {
                String resourceName = rcs.getResourceName();
                if ( resourceName.endsWith( ".class" ) ) {
                    modifiedClasses.add(resourceName);
                }
            }
        }
        return modifiedClasses;
    }

    public Collection<String> getKieBaseNames() {
        return kProject.getKieBaseNames();
    }

    public Collection<String> getKieSessionNamesInKieBase(String kBaseName) {
        KieBaseModel kieBaseModel = kProject.getKieBaseModel(kBaseName);
        return kieBaseModel != null ? kieBaseModel.getKieSessionModels().keySet() : Collections.<String>emptySet();
    }

    public KieBase getKieBase() {
        KieBaseModel defaultKieBaseModel = kProject.getDefaultKieBaseModel();
        if (defaultKieBaseModel == null) {
            throw new RuntimeException("Cannot find a default KieBase");
        }
        return getKieBase( defaultKieBaseModel.getName() );
    }

    public Results verify() {
        return isVerifiable() ? this.kProject.verify() : new ResultsImpl();
    }

    public Results verify(String... kModelNames) {
        return isVerifiable() ? this.kProject.verify(kModelNames) : new ResultsImpl();
    }

    private boolean isVerifiable() {
        if (kProject instanceof KieModuleKieProject) {
            InternalKieModule internalKieModule = ((KieModuleKieProject)kProject).getInternalKieModule();
            if (!internalKieModule.isVerifiable()) {
                log.info("{} is a result module of a successful build, so verify returns an empty successful result message", internalKieModule.getClass().getSimpleName());
                return false;
            }
        }
        return true;
    }

    public KieBase getKieBase(String kBaseName) {
        KieBase kBase = kBases.get( kBaseName );
        if ( kBase == null ) {
            KieBaseModelImpl kBaseModel = getKieBaseModelImpl(kBaseName);
            synchronized (kBaseModel) {
                kBase = kBases.get( kBaseName );
                if ( kBase == null ) {
                    BuildContext buildContext = new BuildContext();
                    kBase = createKieBase(kBaseModel, kProject, buildContext, null);
                    if (kBase == null) {
                        // build error, throw runtime exception
                        throw new RuntimeException("Error while creating KieBase" + buildContext.getMessages().filterMessages(Level.ERROR));
                    }
                    kBases.put(kBaseName, kBase);
                }
            }
        }
        return kBase;
    }

    public KieBase newKieBase(KieBaseConfiguration conf) {
        KieBaseModel defaultKieBaseModel = kProject.getDefaultKieBaseModel();
        if (defaultKieBaseModel == null) {
            throw new RuntimeException("Cannot find a default KieBase");
        }
        return newKieBase(defaultKieBaseModel.getName(), conf);
    }

    public KieBase newKieBase(String kBaseName, KieBaseConfiguration conf) {
        BuildContext buildContext = new BuildContext();
        KieBase kBase = createKieBase(getKieBaseModelImpl(kBaseName), kProject, buildContext, conf);
        if ( kBase == null ) {
            // build error, throw runtime exception
            throw new RuntimeException( "Error while creating KieBase" + buildContext.getMessages().filterMessages( Level.ERROR  ) );
        }
        return kBase;
    }

    private KieBase createKieBase(KieBaseModelImpl kBaseModel, KieProject kieProject, BuildContext buildContext, KieBaseConfiguration conf) {
        if (log.isInfoEnabled()) {
            log.info( "Start creation of KieBase: " + kBaseModel.getName() );
        }

        InternalKieModule kModule = kieProject.getKieModuleForKBase( kBaseModel.getName() );
        InternalKnowledgeBase kBase = kModule.createKieBase(kBaseModel, kieProject, buildContext, conf);
        kModule.afterKieBaseCreationUpdate(kBaseModel.getName(), kBase);

        if ( kBase == null ) {
            return null;
        }
        kBase.setResolvedReleaseId(containerReleaseId);
        kBase.setContainerId(containerId);
        kBase.setKieContainer(this);
        kBase.initMBeans();

        if (log.isInfoEnabled()) {
            log.info( "End creation of KieBase: " + kBaseModel.getName() );
        }

        return kBase;
    }

    private KieBaseModelImpl getKieBaseModelImpl(String kBaseName) {
        KieBaseModelImpl kBaseModel = (KieBaseModelImpl) kProject.getKieBaseModel(kBaseName);
        if (kBaseModel == null) {
            throw new RuntimeException( "The requested KieBase \"" + kBaseName + "\" does not exist" );
        }
        return kBaseModel;
    }

    public KieSession newKieSession() {
        return newKieSession((Environment)null, null);
    }

    public KieSession getKieSession() {
        KieSessionModel defaultKieSessionModel = findKieSessionModel(false);
        return getKieSession(defaultKieSessionModel.getName());
    }

    public KieSession newKieSession(KieSessionConfiguration conf) {
        return newKieSession((Environment)null, conf);
    }

    public KieSession newKieSession(Environment environment) {
        return newKieSession(environment, null);
    }

    public KieSession newKieSession(Environment environment, KieSessionConfiguration conf) {
        return newKieSession(null, environment, conf);
    }

    public KieContainerSessionsPool newKieSessionsPool( int initialSize) {
        return new KieContainerSessionsPoolImpl(this, initialSize);
    }

    StatefulSessionPool createKieSessionsPool(String kSessionName, KieSessionConfiguration conf, Environment env, int initialSize, boolean stateless) {
        KieSessionModel kSessionModel = kSessionName != null ? getKieSessionModel(kSessionName) : findKieSessionModel(false);
        if ( kSessionModel == null ) {
            log.error("Unknown KieSession name: " + kSessionName);
            return null;
        }
        InternalKnowledgeBase kBase = (InternalKnowledgeBase) getKieBaseFromKieSessionModel(kSessionModel, stateless);
        return kBase == null ? null : new StatefulSessionPool(kBase, initialSize, () -> {
            SessionConfiguration sessConf = conf != null ? conf.as(SessionConfiguration.KEY) : kBase.getSessionConfiguration().as(SessionConfiguration.KEY);
            StatefulKnowledgeSessionImpl kSession = stateless ?
                    ((StatefulKnowledgeSessionImpl) RuntimeComponentFactory.get().createStatefulSession(kBase, env, sessConf, false)).setStateless( true ) :
                    (StatefulKnowledgeSessionImpl) kBase.newKieSession( sessConf, env );
            registerNewKieSession( kSessionModel, kBase, kSession );
            return kSession;
        });
    }

    private KieSessionModel findKieSessionModel(boolean stateless) {
        KieSessionModel defaultKieSessionModel = stateless ? kProject.getDefaultStatelessKieSession() : kProject.getDefaultKieSession();
        if (defaultKieSessionModel == null) {
            throw new RuntimeException(stateless ? "Cannot find a default StatelessKieSession" : "Cannot find a default KieSession");
        }
        return defaultKieSessionModel;
    }

    public StatelessKieSession newStatelessKieSession() {
        return newStatelessKieSession((KieSessionConfiguration)null);
    }

    public StatelessKieSession newStatelessKieSession(KieSessionConfiguration conf) {
        KieSessionModel defaultKieSessionModel = findKieSessionModel(true);
        return newStatelessKieSession(defaultKieSessionModel.getName(), conf);
    }

    public StatelessKieSession getStatelessKieSession() {
        KieSessionModel defaultKieSessionModel = findKieSessionModel(true);
        return getStatelessKieSession(defaultKieSessionModel.getName());
    }

    public KieSession newKieSession(String kSessionName) {
        return newKieSession(kSessionName, null, null);
    }

    public KieSession getKieSession(String kSessionName) {
        KieSession kieSession = kSessions.get(kSessionName);
        if (kieSession instanceof StatefulKnowledgeSessionImpl && !((StatefulKnowledgeSessionImpl)kieSession).isAlive()) {
            kSessions.remove(kSessionName);
            kieSession = null;
        }
        return kieSession != null ? kieSession : newKieSession(kSessionName);
    }

    public KieSession newKieSession(String kSessionName, Environment environment) {
        return newKieSession(kSessionName, environment, null);
    }

    public KieSession newKieSession(String kSessionName, KieSessionConfiguration conf) {
        return newKieSession(kSessionName, null, conf);
    }

    public KieSession newKieSession(String kSessionName, Environment environment, KieSessionConfiguration conf) {
        KieSessionModelImpl kSessionModel = kSessionName != null ?
                (KieSessionModelImpl) getKieSessionModel(kSessionName) :
                (KieSessionModelImpl) findKieSessionModel(false);

        if ( kSessionModel == null ) {
            log.error("Unknown KieSession name: " + kSessionName);
            return null;
        }

        KieBase kBase = getKieBaseFromKieSessionModel( kSessionModel, false );
        if ( kBase == null ) {
            return null;
        }

        KieSession kSession = kBase.newKieSession( conf != null ? conf : getKieSessionConfiguration( kSessionModel ), environment );
        registerNewKieSession(kSessionModel, (InternalKnowledgeBase) kBase, kSession);
        return kSession;
    }

    private void registerNewKieSession(KieSessionModel kSessionModel, InternalKnowledgeBase kBase, KieSession kSession) {
        if (isJndiAvailable()) {
            wireSessionComponents( kSessionModel, kSession );
        }
        registerLoggers(kSessionModel, kSession);
        registerCalendars(kSessionModel, kSession);

        ((StatefulKnowledgeSessionImpl ) kSession).initMBeans(containerId, kBase.getId(), kSessionModel.getName());

        kSessions.put(kSessionModel.getName(), kSession);
    }

    private KieBase getKieBaseFromKieSessionModel( KieSessionModel kSessionModel, boolean stateless ) {
        if (!stateless && kSessionModel.getType() == KieSessionModel.KieSessionType.STATELESS) {
            throw new RuntimeException("Trying to create a stateful KieSession from a stateless KieSessionModel: " + kSessionModel.getName());
        }
        KieBase kBase = getKieBase( kSessionModel.getKieBaseModel().getName() );
        if ( kBase == null ) {
            log.error("Unknown KieBase name: " + kSessionModel.getKieBaseModel().getName());
            return null;
        }
        return kBase;
    }

    private void registerLoggers(KieSessionModel kSessionModel, KieRuntimeEventManager kSession) {
        KieLoggers kieLoggers = KieServices.Factory.get().getLoggers();
        if (kSessionModel.getConsoleLogger() != null) {
            kieLoggers.newConsoleLogger(kSession);
        }
        FileLoggerModel fileLogger = kSessionModel.getFileLogger();
        if (fileLogger != null) {
            if (fileLogger.isThreaded()) {
                kieLoggers.newThreadedFileLogger(kSession, fileLogger.getFile(), fileLogger.getInterval());
            } else {
                kieLoggers.newFileLogger(kSession, fileLogger.getFile());
            }
        }
    }

    private void registerCalendars(KieSessionModel kSessionModel, KieSession kSession) {
        for (Map.Entry<String, String> entry : kSessionModel.getCalendars().entrySet()) {
            try {
                Calendar calendar = (Calendar) getClassLoader().loadClass( entry.getValue() ).newInstance();
                kSession.getCalendars().set( entry.getKey(), calendar );
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                log.error( "Cannot instance calendar " + entry.getKey(), e );
            }
        }
    }

    public StatelessKieSession newStatelessKieSession(String kSessionName) {
        return newStatelessKieSession(kSessionName, null);
    }

    public StatelessKieSession newStatelessKieSession(String kSessionName, KieSessionConfiguration conf) {
        KieSessionModelImpl kSessionModel = kSessionName != null ?
                (KieSessionModelImpl) getKieSessionModel(kSessionName) :
                (KieSessionModelImpl) findKieSessionModel(true);

        if ( kSessionModel == null ) {
            log.error("Unknown KieSession name: " + kSessionName);
            return null;
        }
        if (kSessionModel.getType() == KieSessionModel.KieSessionType.STATEFUL) {
            throw new RuntimeException("Trying to create a stateless KieSession from a stateful KieSessionModel: " + kSessionModel.getName());
        }
        KieBase kBase = getKieBase( kSessionModel.getKieBaseModel().getName() );
        if ( kBase == null ) {
            log.error("Unknown KieBase name: " + kSessionModel.getKieBaseModel().getName());
            return null;
        }

        StatelessKieSession statelessKieSession = kBase.newStatelessKieSession( conf != null ? conf : getKieSessionConfiguration( kSessionModel ) );
        if (isJndiAvailable()) {
            wireSessionComponents( kSessionModel, statelessKieSession );
        }
        registerLoggers(kSessionModel, statelessKieSession);

        ((StatelessKnowledgeSessionImpl) statelessKieSession).initMBeans(containerId, ((InternalKnowledgeBase) kBase).getId(), kSessionModel.getName());

        statelessKSessions.put(kSessionModel.getName(), statelessKieSession);
        return statelessKieSession;
    }

    public StatelessKieSession getStatelessKieSession(String kSessionName) {
        StatelessKieSession kieSession = statelessKSessions.get(kSessionName);
        return kieSession != null ? kieSession : newStatelessKieSession(kSessionName);

    }

    public KieSessionConfiguration getKieSessionConfiguration() {
        return getKieSessionConfiguration( kProject.getDefaultKieSession() );
    }

    public KieSessionConfiguration getKieSessionConfiguration( String kSessionName ) {
        KieSessionModelImpl kSessionModel = (KieSessionModelImpl) kProject.getKieSessionModel( kSessionName );
        if ( kSessionModel == null ) {
            log.error("Unknown KieSession name: " + kSessionName);
            return null;
        }
        return getKieSessionConfiguration( kSessionModel );
    }

    private KieSessionConfiguration getKieSessionConfiguration( KieSessionModel kSessionModel ) {
        KieSessionConfiguration ksConf = sessionConfsCache.computeIfAbsent(kSessionModel.getName(),
                                                                           k -> new KieServicesImpl().newKieSessionConfiguration(null, kProject.getClassLoader()) );
        ksConf.setOption( kSessionModel.getClockType() );
        ksConf.setOption( kSessionModel.getBeliefSystem() );
        return ksConf;
    }

    public void dispose() {
        sessionConfsCache.clear();
        kBases.values().forEach( kb -> ( (InternalKnowledgeBase) kb ).setKieContainer(null));

        Set<DroolsManagementAgent.CBSKey> cbskeys = new HashSet<>();
        if ( isMBeanOptionEnabled() ) {
            for (Entry<String, KieSession> kv : kSessions.entrySet()) {
                cbskeys.add(new DroolsManagementAgent.CBSKey(containerId, ((RuleBase) kv.getValue().getKieBase()).getId(), kv.getKey()));
            }
            for (Entry<String, StatelessKieSession> kv : statelessKSessions.entrySet()) {
                cbskeys.add(new DroolsManagementAgent.CBSKey(containerId, ((RuleBase) kv.getValue().getKieBase()).getId(), kv.getKey()));
            }
        }

        for (KieSession kieSession : kSessions.values()) {
            kieSession.dispose();
        }
        kSessions.clear();
        statelessKSessions.clear();

        if ( isMBeanOptionEnabled() ) {
            for (CBSKey c : cbskeys) {
                DroolsManagementAgent.getInstance().unregisterKnowledgeSessionBean(c);
            }
            for (KieBase kb : kBases.values()) {
                DroolsManagementAgent.getInstance().unregisterKnowledgeBase((RuleBase) kb);
            }
            DroolsManagementAgent.getInstance().unregisterMBeansFromOwner(this);
        }

        ((InternalKieServices) KieServices.Factory.get()).clearRefToContainerId(this.containerId, this);
    }

    @Override
    public void disposeSession(KieSession kieSession) {
        if (!isMBeanOptionEnabled()) {
            kSessions.values().remove( kieSession );
        }
    }

    private boolean isMBeanOptionEnabled() {
        return MBeansOption.isEnabled( System.getProperty( MBeansOption.PROPERTY_NAME, MBeansOption.DISABLED.toString() ) );
    }

    public KieProject getKieProject() {
        return kProject;
    }

    public KieModule getKieModuleForKBase(String kBaseName) {
        return kProject.getKieModuleForKBase( kBaseName );
    }

    public KieBaseModel getKieBaseModel(String kBaseName) {
        return kProject.getKieBaseModel(kBaseName);
    }

    public KieSessionModel getKieSessionModel(String kSessionName) {
        return kProject.getKieSessionModel(kSessionName);
    }

    @Override
    public ClassLoader getClassLoader() {
        return this.kProject.getClassLoader();
    }
}
