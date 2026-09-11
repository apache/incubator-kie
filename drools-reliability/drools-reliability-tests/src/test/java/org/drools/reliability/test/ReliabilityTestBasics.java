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
package org.drools.reliability.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.kie.api.runtime.ClassObjectFilter;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.drools.model.Model;
import org.drools.model.codegen.ExecutableModelProject;
import org.drools.modelcompiler.KieBaseBuilder;
import org.drools.reliability.core.ReliableGlobalResolver;
import org.drools.reliability.core.ReliableKieSession;
import org.drools.reliability.core.ReliableRuntimeComponentFactoryImpl;
import org.drools.reliability.core.StorageManagerFactory;
import org.drools.reliability.core.TestableStorageManager;
import org.drools.reliability.infinispan.InfinispanStorageManager;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.util.Version;
import org.infinispan.server.test.core.InfinispanContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.conf.KieBaseOption;
import org.kie.api.conf.Option;
import org.kie.api.io.ResourceType;
import org.kie.api.prototype.PrototypeEventInstance;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.KieSessionOption;
import org.kie.api.runtime.conf.PersistedSessionOption;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.time.SessionPseudoClock;
import org.kie.internal.utils.KieHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.test.domain.Person;

import static org.drools.reliability.infinispan.InfinispanStorageManagerFactory.INFINISPAN_STORAGE_MARSHALLER;
import static org.drools.reliability.infinispan.InfinispanStorageManagerFactory.INFINISPAN_STORAGE_MODE;
import static org.drools.reliability.test.util.PrototypeUtils.createEvent;
import static org.drools.reliability.test.util.TestConfigurationUtils.DROOLS_RELIABILITY_MODULE_TEST;
import static org.drools.util.Config.getConfig;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@ExtendWith(BeforeAllMethodExtension.class)
public abstract class ReliabilityTestBasics {

    public static final String RULE_TYPE_TAG = "RULE_TYPE";
    public static final String SYNTHETIC_RULE_TAG = "SYNTHETIC_RULE";
    private static final Logger LOG = LoggerFactory.getLogger(ReliabilityTestBasics.class);

    private final HashMap<String, KieBase> kieBaseCache = new HashMap<>();

    private InfinispanContainer container;

    protected final List<KieSession> sessions = new ArrayList<>();
    protected final HashMap<Long,Long> persistedSessionIds = new HashMap<>();

    protected PersistedSessionOption.SafepointStrategy safepointStrategy;

    static Stream<PersistedSessionOption.PersistenceStrategy> strategyProvider() {
        return Stream.of(PersistedSessionOption.PersistenceStrategy.STORES_ONLY, PersistedSessionOption.PersistenceStrategy.FULL);
    }

    static Stream<Arguments> strategyProviderStoresOnly() {
        return Stream.of(arguments(PersistedSessionOption.PersistenceStrategy.STORES_ONLY, PersistedSessionOption.SafepointStrategy.ALWAYS));
    }

    static Stream<Arguments> strategyProviderStoresOnlyWithExplicitSafepoints() {
        return Stream.of(
                arguments(PersistedSessionOption.PersistenceStrategy.STORES_ONLY, PersistedSessionOption.SafepointStrategy.ALWAYS),
                arguments(PersistedSessionOption.PersistenceStrategy.STORES_ONLY, PersistedSessionOption.SafepointStrategy.EXPLICIT)
        );
    }

    static Stream<Arguments> strategyProviderStoresOnlyWithExplicitSafepointsAndKieBaseCache() {
        return Stream.of(
                arguments(PersistedSessionOption.PersistenceStrategy.STORES_ONLY, PersistedSessionOption.SafepointStrategy.ALWAYS, true),
                arguments(PersistedSessionOption.PersistenceStrategy.STORES_ONLY, PersistedSessionOption.SafepointStrategy.ALWAYS, false),
                arguments(PersistedSessionOption.PersistenceStrategy.STORES_ONLY, PersistedSessionOption.SafepointStrategy.EXPLICIT, true),
                arguments(PersistedSessionOption.PersistenceStrategy.STORES_ONLY, PersistedSessionOption.SafepointStrategy.EXPLICIT, false)
        );
    }

    static Stream<Arguments> strategyProviderStoresOnlyWithAllSafepoints() {
        return Stream.of(
                arguments(PersistedSessionOption.PersistenceStrategy.STORES_ONLY, PersistedSessionOption.SafepointStrategy.ALWAYS),
                arguments(PersistedSessionOption.PersistenceStrategy.STORES_ONLY, PersistedSessionOption.SafepointStrategy.EXPLICIT),
                arguments(PersistedSessionOption.PersistenceStrategy.STORES_ONLY, PersistedSessionOption.SafepointStrategy.AFTER_FIRE)
        );
    }

    static Stream<Arguments> strategyProviderStoresOnlyWithAllSafepointsWithActivationKey() {
        return Stream.of(
                arguments(PersistedSessionOption.PersistenceStrategy.STORES_ONLY, PersistedSessionOption.SafepointStrategy.ALWAYS, PersistedSessionOption.ActivationStrategy.ACTIVATION_KEY),
                arguments(PersistedSessionOption.PersistenceStrategy.STORES_ONLY, PersistedSessionOption.SafepointStrategy.EXPLICIT, PersistedSessionOption.ActivationStrategy.ACTIVATION_KEY),
                arguments(PersistedSessionOption.PersistenceStrategy.STORES_ONLY, PersistedSessionOption.SafepointStrategy.AFTER_FIRE, PersistedSessionOption.ActivationStrategy.ACTIVATION_KEY)
        );
    }

    static boolean isRemoteInfinispan() {
        return "INFINISPAN".equalsIgnoreCase(getConfig(DROOLS_RELIABILITY_MODULE_TEST))
                && "REMOTE".equalsIgnoreCase(getConfig(INFINISPAN_STORAGE_MODE));
    }

    static boolean isRemoteProtoInfinispan() {
        return "INFINISPAN".equalsIgnoreCase(getConfig(DROOLS_RELIABILITY_MODULE_TEST))
                && "REMOTE".equalsIgnoreCase(getConfig(INFINISPAN_STORAGE_MODE))
                && "PROTOSTREAM".equalsIgnoreCase(getConfig(INFINISPAN_STORAGE_MARSHALLER));
    }

    static Stream<PersistedSessionOption.PersistenceStrategy> strategyProviderFull() {
        return Stream.of(PersistedSessionOption.PersistenceStrategy.FULL);
    }

    @BeforeEach
    public void setUp() {
        if (((TestableStorageManager) StorageManagerFactory.get().getStorageManager()).isRemote()) {
            LOG.info("Starting InfinispanContainer");
            container = new InfinispanContainer(InfinispanContainer.IMAGE_BASENAME + ":" + Version.getMajorMinor());
            container.withFileSystemBind("infinispan-remote-config", "/user-config")
                    .withCommand("-c /user-config/infinispan-local.xml");
            container.start();
            LOG.info("InfinispanContainer started"); // takes about 10 seconds
            InfinispanStorageManager cacheManager = (InfinispanStorageManager) StorageManagerFactory.get().getStorageManager();
            RemoteCacheManager remoteCacheManager = container.getRemoteCacheManager(cacheManager.provideAdditionalRemoteConfigurationBuilder());
            cacheManager.setRemoteCacheManager(remoteCacheManager);
        }
    }

    @AfterEach
    public void tearDown() {
        if (((TestableStorageManager) StorageManagerFactory.get().getStorageManager()).isRemote()) {
            StorageManagerFactory.get().getStorageManager().removeAllSessionStorages();
            StorageManagerFactory.get().getStorageManager().close(); // close remoteCacheManager
            container.stop(); // stop remote infinispan
        } else {
            // clean up embedded Infinispan including GlobalState and FireStore so that test methods can be isolated
            ((TestableStorageManager) StorageManagerFactory.get().getStorageManager()).restartWithCleanUp();
        }
        ReliableRuntimeComponentFactoryImpl.resetCounter();
    }

    public void failover() {
        if (safepointStrategy == PersistedSessionOption.SafepointStrategy.EXPLICIT) {
            this.sessions.stream().map(ReliableKieSession.class::cast).forEach(ReliableKieSession::safepoint);
        }
        sessions.clear();
        kieBaseCache.clear();

        if (((TestableStorageManager) StorageManagerFactory.get().getStorageManager()).isRemote()) {
            // fail-over means restarting Drools instance. Assuming remote infinispan keeps alive
            StorageManagerFactory.get().getStorageManager().close(); // close remoteCacheManager
            // Reclaim RemoteCacheManager
            InfinispanStorageManager cacheManager = (InfinispanStorageManager) StorageManagerFactory.get().getStorageManager();
            RemoteCacheManager remoteCacheManager = container.getRemoteCacheManager(cacheManager.provideAdditionalRemoteConfigurationBuilder());
            cacheManager.setRemoteCacheManager(remoteCacheManager);
        } else {
            ((TestableStorageManager) StorageManagerFactory.get().getStorageManager()).restart(); // restart embedded infinispan cacheManager. GlobalState and FireStore are kept
        }
        ReliableRuntimeComponentFactoryImpl.refreshCounterUsingStorage();
    }

    protected FactHandle insert(Object obj) {
        return insert(sessions.get(0), obj);
    }

    protected FactHandle insert(KieSession session, Object obj) {
        return session.insert(obj);
    }

    protected void update(FactHandle fh, Object obj) {
        update(sessions.get(0), fh, obj);
    }

    protected void update(KieSession session, FactHandle fh, Object obj) {
        session.update(fh, obj);
    }

    protected void delete(FactHandle fh) {
        delete(sessions.get(0), fh);
    }

    protected void delete(KieSession session, FactHandle fh) {
        session.delete(fh);
    }

    protected FactHandle insertMatchingPerson(String name) {
        return insertMatchingPerson(sessions.get(0), name, 20); // for rules don't care about age
    }

    protected FactHandle insertMatchingPerson(String name, Integer age) {
        return insertMatchingPerson(sessions.get(0), name, age);
    }

    protected FactHandle insertMatchingPerson(KieSession session, String name, Integer age) {
        return session.insert(new Person(name, age));
    }

    protected void updateWithMatchingPerson(FactHandle nonMatching, Object matching) {
        updateWithMatchingPerson(sessions.get(0), nonMatching, matching);
    }

    protected void updateWithMatchingPerson(KieSession session, FactHandle nonMatching, Object matching) {
        session.update(nonMatching,matching);
    }

    protected void updateWithNonMatchingPerson(FactHandle matching, Object nonMatching) {
        updateWithNonMatchingPerson(sessions.get(0), matching, nonMatching);
    }

    protected void updateWithNonMatchingPerson(KieSession session, FactHandle matching, Object nonMatching) {
        session.update(matching, nonMatching);
    }

    protected FactHandle insertNonMatchingPerson(String name, Integer age) {
        return insertNonMatchingPerson(sessions.get(0), name, age);
    }

    protected FactHandle insertNonMatchingPerson(KieSession session, String name, Integer age) {
        return session.insert(new Person(name, age));
    }

    protected List<Object> getResults() {
        return getResults(sessions.get(0));
    }

    protected List<Object> getResults(KieSession session) {
        return (List<Object>) session.getGlobal("results");
    }

    protected void clearResults() {
        clearResults(sessions.get(0));
    }

    protected void clearResults(KieSession session) {
        ((List<Object>) session.getGlobal("results")).clear();
        ((ReliableGlobalResolver)((StatefulKnowledgeSessionImpl) session).getGlobalResolver()).updateStorage();
    }

    protected KieSession createSession(String drl, PersistedSessionOption.PersistenceStrategy persistenceStrategy, Option... options) {
        return createSession(drl, persistenceStrategy, PersistedSessionOption.SafepointStrategy.ALWAYS, options);
    }

    protected KieSession createSession(String drl, PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy, Option... options) {
        return getKieSession(drl, persistenceStrategy != null ? PersistedSessionOption.newSession().withPersistenceStrategy(persistenceStrategy).withSafepointStrategy(safepointStrategy) : null, options);
    }

    protected KieSession createSession(String drl, PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy,
                                       PersistedSessionOption.PersistenceObjectsStrategy persistenceObjectsStrategy, Option... options) {
        return getKieSession(drl, persistenceStrategy != null ? PersistedSessionOption.newSession().withPersistenceStrategy(persistenceStrategy)
                .withSafepointStrategy(safepointStrategy).withPersistenceObjectsStrategy(persistenceObjectsStrategy) : null, options);
    }

    protected KieSession createSession(String drl, PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy, PersistedSessionOption.ActivationStrategy activationStrategy, Option... options) {
        return getKieSession(drl, persistenceStrategy != null ? PersistedSessionOption.newSession().withPersistenceStrategy(persistenceStrategy).withSafepointStrategy(safepointStrategy).withActivationStrategy(activationStrategy) : null, options);
    }

    protected KieSession createSession(String drl, PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy, boolean useKieBaseCache, Option... options) {
        return getKieSession(drl, persistenceStrategy != null ? PersistedSessionOption.newSession().withPersistenceStrategy(persistenceStrategy).withSafepointStrategy(safepointStrategy) : null, useKieBaseCache, options);
    }

    protected KieSession createSession(Model ruleModel, PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy, Option... options) {
        return getKieSession(ruleModel, persistenceStrategy != null ? PersistedSessionOption.newSession().withPersistenceStrategy(persistenceStrategy).withSafepointStrategy(safepointStrategy) : null, options);
    }

    protected KieSession restoreSession(String drl, PersistedSessionOption.PersistenceStrategy persistenceStrategy, Option... options) {
        return restoreSession(drl, persistenceStrategy, PersistedSessionOption.SafepointStrategy.ALWAYS, options);
    }

    protected KieSession restoreSession(String drl, PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy, Option... options) {
        Long sessionIdToRestoreFrom = (Long)this.persistedSessionIds.values().toArray()[0];
        return restoreSession(sessionIdToRestoreFrom, drl, persistenceStrategy, safepointStrategy, options);
    }

    protected KieSession restoreSession(String drl, PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy,
                                        PersistedSessionOption.PersistenceObjectsStrategy persistenceObjectsStrategy, Option... options) {
        Long sessionIdToRestoreFrom = (Long) this.persistedSessionIds.values().toArray()[0];
        return restoreSession(sessionIdToRestoreFrom, drl, persistenceStrategy, safepointStrategy, persistenceObjectsStrategy, options);
    }

    protected KieSession restoreSession(String drl, PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy, PersistedSessionOption.ActivationStrategy activationStrategy, Option... options) {
        Long sessionIdToRestoreFrom = (Long)this.persistedSessionIds.values().toArray()[0];
        return restoreSession(sessionIdToRestoreFrom, drl, persistenceStrategy, safepointStrategy, activationStrategy, options);
    }

    protected KieSession restoreSession(Long sessionId, String drl, PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy, Option... options) {
        Long sessionIdToRestoreFrom = this.persistedSessionIds.get(sessionId);
        return getKieSession(drl, PersistedSessionOption.fromSession(sessionIdToRestoreFrom).withPersistenceStrategy(persistenceStrategy).withSafepointStrategy(safepointStrategy), options);
    }

    protected KieSession restoreSession(Long sessionId, String drl, PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy,
                                        PersistedSessionOption.PersistenceObjectsStrategy persistenceObjectsStrategy, Option... options) {
        Long sessionIdToRestoreFrom = this.persistedSessionIds.get(sessionId);
        return getKieSession(drl, PersistedSessionOption.fromSession(sessionIdToRestoreFrom).withPersistenceStrategy(persistenceStrategy)
                .withSafepointStrategy(safepointStrategy).withPersistenceObjectsStrategy(persistenceObjectsStrategy), options);
    }

    protected KieSession restoreSession(Long sessionId, String drl, PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy, PersistedSessionOption.ActivationStrategy activationStrategy, Option... options) {
        Long sessionIdToRestoreFrom = this.persistedSessionIds.get(sessionId);
        return getKieSession(drl, PersistedSessionOption.fromSession(sessionIdToRestoreFrom).withPersistenceStrategy(persistenceStrategy).withSafepointStrategy(safepointStrategy).withActivationStrategy(activationStrategy), options);
    }

    protected KieSession restoreSession(Long sessionId, String drl, PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy, boolean useKieBaseCache, Option... options) {
        Long sessionIdToRestoreFrom = this.persistedSessionIds.get(sessionId);
        return getKieSession(drl, PersistedSessionOption.fromSession(sessionIdToRestoreFrom).withPersistenceStrategy(persistenceStrategy).withSafepointStrategy(safepointStrategy), useKieBaseCache, options);
    }

    protected int fireAllRules() {
        return fireAllRules(sessions.get(0));
    }

    protected int fireAllRules(int max) {
        return sessions.get(0).fireAllRules(max);
    }

    protected int fireAllRules(KieSession session) {
        return session.fireAllRules();
    }

    protected KieSession restoreSession(Model ruleModel, PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy, Option... options) {
        Long sessionIdToRestoreFrom = (Long)this.persistedSessionIds.values().toArray()[0];
        return getKieSession(ruleModel, PersistedSessionOption.fromSession(sessionIdToRestoreFrom).withPersistenceStrategy(persistenceStrategy).withSafepointStrategy(safepointStrategy), options);
    }

    protected void disposeSession() {
        disposeSession(sessions.get(0));
    }

    protected void disposeSession(KieSession session) {
        session.dispose();
    }

    protected SessionPseudoClock getSessionClock() {
        return getSessionClock(sessions.get(0));
    }

    protected SessionPseudoClock getSessionClock(KieSession session) {
        return session.getSessionClock();
    }

    protected Collection<FactHandle> getFactHandles() {
        return getFactHandles(sessions.get(0));
    }

    protected Collection<FactHandle> getFactHandles(KieSession session) {
        return session.getFactHandles();
    }

    protected void safepoint() {
        safepoint(sessions.get(0));
    }

    protected void safepoint(KieSession session) {
        ((ReliableKieSession) session).safepoint();
    }

    protected long getSessionIdentifier() {
        return sessions.get(0).getIdentifier();
    }

    protected KieSession getKieSession(String drl, PersistedSessionOption persistedSessionOption, Option... options) {
        return getKieSession(drl, persistedSessionOption, true, options);
    }

    protected KieSession getKieSession(String drl, PersistedSessionOption persistedSessionOption, boolean useKieBaseCache, Option... options) {
        OptionsFilter optionsFilter = new OptionsFilter(options);
        KieBase kbase;
        if (useKieBaseCache) {
            kbase = kieBaseCache.computeIfAbsent(drl, k -> createKieBase(drl, optionsFilter));
        } else {
            kbase = createKieBase(drl, optionsFilter);
        }
        return getKieSessionFromKieBase(kbase, persistedSessionOption, optionsFilter);
    }

    private static KieBase createKieBase(String drl, OptionsFilter optionsFilter) {
        return new KieHelper().addContent(drl, ResourceType.DRL).build(ExecutableModelProject.class, optionsFilter.getKieBaseOptions());
    }

    protected KieSession getKieSession(Model ruleModel, PersistedSessionOption persistedSessionOption, Option... options) {
        OptionsFilter optionsFilter = new OptionsFilter(options);
        KieBase kbase = KieBaseBuilder.createKieBaseFromModel(ruleModel, optionsFilter.getKieBaseOptions());
        return getKieSessionFromKieBase(kbase, persistedSessionOption, optionsFilter);
    }

    private KieSession getKieSessionFromKieBase(KieBase kbase, PersistedSessionOption persistedSessionOption, OptionsFilter optionsFilter) {
        KieSessionConfiguration conf = KieServices.get().newKieSessionConfiguration();
        if (persistedSessionOption != null) {
            conf.setOption(persistedSessionOption);
            safepointStrategy = persistedSessionOption.getSafepointStrategy();
        }
        Stream.of(optionsFilter.getKieSessionOption()).forEach(conf::setOption);
        KieSession session = kbase.newKieSession(conf, null);
        if (persistedSessionOption == null || persistedSessionOption.isNewSession()) {
            List<Object> results = new ArrayList<>();
            session.setGlobal("results", results);
        }
        sessions.add(session);
        persistedSessionIds.put(session.getIdentifier(),persistedSessionOption == null || persistedSessionOption.isNewSession() ? session.getIdentifier() : persistedSessionOption.getSessionId());
        return session;
    }

    protected Optional<Person> getPersonByName(String name) {
        return getPersonByName(sessions.get(0), name);
    }

    protected Optional<Person> getPersonByName(KieSession kieSession, String name) {
        return kieSession.getInstancesOf( Person.class )
                .stream()
                .map(Person.class::cast)
                .filter(p -> p.getName().equals(name) ).findFirst();
    }

    protected Optional<FactHandle> getFactHandle(Person person){
        return getFactHandle(sessions.get(0), person);
    }

    protected Optional<FactHandle> getFactHandle(KieSession kieSession, Person person){
        return kieSession.getFactHandles()
                .stream()
                .filter(p -> p.getObject() instanceof Person)
                .filter(p -> ( (Person) p.getObject()).getName().equals(person.getName()) )
                .filter(p -> ( (Person) p.getObject()).getAge()==person.getAge() ).findFirst();
    }

    protected Optional<Object> getObjectByType(Class objectClass){
        return getObjectByType(sessions.get(0), objectClass);
    }

    protected Optional<Object> getObjectByType(KieSession kieSession, Class objectClass){
        return (Optional<Object>) kieSession.getObjects(new ClassObjectFilter(objectClass)).stream().findFirst();
    }

    protected Optional<FactHandle> getFactHandleByType(Class objectClass){
        return getFactHandleByType(sessions.get(0), objectClass);
    }

    protected Optional<FactHandle> getFactHandleByType(KieSession kieSession, Class objectClass){
        return kieSession.getFactHandles()
                .stream()
                .filter(fh -> fh.getObject().getClass().equals(objectClass))
                .findFirst();
    }

    private static class OptionsFilter {
        private final Option[] options;

        OptionsFilter(Option[] options) {
            this.options = options;
        }

        KieBaseOption[] getKieBaseOptions() {
            return options == null ? new KieBaseOption[0] : Stream.of(options).filter(KieBaseOption.class::isInstance).toArray(KieBaseOption[]::new);
        }

        KieSessionOption[] getKieSessionOption() {
            return options == null ? new KieSessionOption[0] : Stream.of(options).filter(KieSessionOption.class::isInstance).toArray(KieSessionOption[]::new);
        }
    }

    protected void advanceTime(long amount, TimeUnit unit) {
        ((SessionPseudoClock) sessions.get(0).getSessionClock()).advanceTime(amount, unit);
    }

    protected void advanceTimeAndFire(long amount, TimeUnit unit) {
        ((SessionPseudoClock) sessions.get(0).getSessionClock()).advanceTime(amount, unit);
        sessions.get(0).fireAllRules();
    }

    protected void insertMatchingSensuEvent(String host, String type) {
        insertSensuEvent(host, type);
    }

    protected void insertNonMatchingSensuEvent(String host, String type) {
        insertSensuEvent(host, type);
    }

    private void insertSensuEvent(String host, String type) {
        PrototypeEventInstance sensu = createEvent();
        sensu.put("sensu.host", host);
        sensu.put("sensu.process.type", type);
        sessions.get(0).insert(sensu);
    }

    protected void insertMatchingSensuProcessStatusEvent(String status) {
        PrototypeEventInstance sensu = createEvent();
        sensu.put("sensu.process.status", status);
        sessions.get(0).insert(sensu);
    }

    protected void insertMatchingPingTimeoutEvent(boolean timeout) {
        PrototypeEventInstance ping = createEvent();
        ping.put("ping.timeout", timeout);
        sessions.get(0).insert(ping);
    }

    protected void insertMatchingSensuStoragePercentEvent(int percent) {
        PrototypeEventInstance sensu = createEvent();
        sensu.put("sensu.storage.percent", percent);
        sessions.get(0).insert(sensu);
    }
}
