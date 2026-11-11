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
package org.drools.mvel.integrationtests.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.drools.core.common.EventSupport;
import org.drools.core.event.DefaultAgendaEventListener;
import org.drools.core.event.DefaultRuleRuntimeEventListener;
import org.drools.mvel.compiler.FactA;
import org.drools.mvel.compiler.FactB;
import org.drools.mvel.compiler.FactC;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieSessionTestConfiguration;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.builder.KieModule;
import org.kie.api.command.Command;
import org.kie.api.conf.SequentialOption;
import org.kie.api.conf.SessionsPoolOption;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieContainerSessionsPool;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionsPool;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.internal.command.CommandFactory;
import org.kie.internal.event.rule.RuleEventListener;
import org.kie.internal.event.rule.RuleEventManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class SessionsPoolTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testKieSessionsPool(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieContainerSessionsPool pool = getKieContainer(kieBaseTestConfiguration).newKieSessionsPool( 1 );

        KieSession ksession = pool.newKieSession();
        try {
            checkKieSession( ksession );
        } finally {
            ksession.dispose();
        }

        try {
            ksession.insert( "test2" );
            fail("it shouldn't be possible to operate on a disposed session even if created from a pool");
        } catch (Exception e) { }

        KieSession ksession2 = pool.newKieSession();

        // using a pool with only one session so it should return the same one as before
        assertThat(ksession2).isSameAs(ksession);
        assertThat(ksession2.getGlobal("list")).isNull();
        checkKieSession( ksession2 );

        pool.shutdown();

        try {
            ksession.insert( "test3" );
            fail("after pool shutdown all sessions created from it should be disposed");
        } catch (IllegalStateException e) { }

        try {
            pool.newKieSession();
            fail("after pool shutdown it shouldn't be possible to get sessions from it");
        } catch (IllegalStateException e) { }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testPooledKieBase(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieBaseConfiguration kbConf = KieServices.get().newKieBaseConfiguration();
        kbConf.setOption(SessionsPoolOption.get(1));
        KieBase kBase = getKieContainer(kieBaseTestConfiguration).newKieBase(kbConf);

        KieSession ksession = kBase.newKieSession();
        try {
            checkKieSession( ksession );
        } finally {
            ksession.dispose();
        }

        try {
            ksession.insert( "test2" );
            fail("it shouldn't be possible to operate on a disposed session even if created from a pool");
        } catch (Exception e) { }

        KieSession ksession2 = kBase.newKieSession();

        // using a pool with only one session so it should return the same one as before
        assertThat(ksession2).isSameAs(ksession);
        assertThat(ksession2.getGlobal("list")).isNull();
        checkKieSession( ksession2 );
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testKieSessionsPoolInMultithreadEnv(KieBaseTestConfiguration kieBaseTestConfiguration) throws InterruptedException, ExecutionException {
        KieContainerSessionsPool pool = getKieContainer(kieBaseTestConfiguration).newKieSessionsPool( 4 );

        final int THREAD_NR = 10;
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_NR, r -> {
            final Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        try {
            CompletionService<Boolean> ecs = new ExecutorCompletionService<>( executor );
            for (int i = 0; i < THREAD_NR; i++) {
                ecs.submit( () -> {
                    try {
                        KieSession ksession = pool.newKieSession();
                        try {
                            checkKieSession( ksession );
                        } finally {
                            ksession.dispose();
                        }
                        return true;
                    } catch (final Exception e) {
                        return false;
                    }
                } );
            }
            boolean success = true;
            for (int i = 0; i < THREAD_NR; i++) {
                success = ecs.take().get() && success;
            }
            assertThat(success).isTrue();
        } finally {
            executor.shutdown();
        }

        pool.shutdown();
        try {
            pool.newKieSession();
            fail("after pool shutdown it shouldn't be possible to get sessions from it");
        } catch (IllegalStateException e) { }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testStatelessKieSessionsPool(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieContainerSessionsPool pool = getKieContainer(kieBaseTestConfiguration).newKieSessionsPool( 1 );
        StatelessKieSession session = pool.newStatelessKieSession();

        List<String> list = new ArrayList<>();
        session.setGlobal( "list", list );
        session.execute( "test" );
        assertThat(list.size()).isEqualTo(1);

        list.clear();
        session.execute( "test" );
        assertThat(list.size()).isEqualTo(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testStatelessKieSessionsPoolWithConf(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieServices kieServices = KieServices.get();

        KieSessionsPool pool = getKieContainer(kieBaseTestConfiguration).getKieBase().newKieSessionsPool( 1 );
        StatelessKieSession session = pool.newStatelessKieSession(kieServices.newKieSessionConfiguration());

        List<String> list = new ArrayList<>();
        session.setGlobal( "list", list );
        session.execute( "test" );
        assertThat(list.size()).isEqualTo(1);

        list.clear();
        session.execute( "test" );
        assertThat(list.size()).isEqualTo(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testStatelessKieSessionsPoolWithStatelessKieSessionModel(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // incubator-kie#6938
        String drl =
                "global java.util.List list\n" +
                        "rule R1 when\n" +
                        "  $s: String()\n" +
                        "then\n" +
                        "  list.add($s);\n" +
                        "end\n";
        final KieModule kieModule = KieUtil.getKieModuleFromDrls(KieUtil.generateReleaseId("test"), kieBaseTestConfiguration,
                                                                 KieSessionTestConfiguration.STATELESS_REALTIME, new HashMap<>(), drl);
        KieContainerSessionsPool pool = KieServices.get().newKieContainer(kieModule.getReleaseId()).newKieSessionsPool( 1 );
        try {
            StatelessKieSession session = pool.newStatelessKieSession( KieSessionTestConfiguration.KIE_SESSION_MODEL_NAME );

            List<String> list = new ArrayList<>();
            session.setGlobal( "list", list );
            session.execute( "test" );
            assertThat(list).containsExactly("test");

            list.clear();
            session.execute( "test" );
            assertThat(list).containsExactly("test");
        } finally {
            pool.shutdown();
        }
    }

    private KieContainer getKieContainer(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String drl =
                "global java.util.List list\n" +
                        "rule R1 when\n" +
                        "  $s: String()\n" +
                        "then\n" +
                        "  list.add($s);\n" +
                        "end\n";
        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        return KieServices.get().newKieContainer(kieModule.getReleaseId());
    }

    private void checkKieSession( KieSession ksession ) {
        List<String> list = new ArrayList<>();
        ksession.setGlobal( "list", list );
        ksession.insert( "test" );
        ksession.fireAllRules();
        assertThat(list.size()).isEqualTo(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testSegmentMemoriesReset(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-3228
        String drl =
                "import " + AtomicInteger.class.getCanonicalName() + ";\n" +
                "global java.util.List list\n" +
                "rule R1 when\n" +
                "  String()\n" +
                "  $i : AtomicInteger()\n" +
                "  not Boolean()\n" +
                "then\n" +
                "  insert(true);\n" +
                "  insert($i.incrementAndGet());\n" +
                "end\n" +
                "\n" +
                "rule R2 when \n" +
                "  String()\n" +
                "  $i : AtomicInteger()\n" +
                "then\n" +
                "end\n" +
                "\n" +
                "rule R3 when\n" +
                "  Integer( this > 2 )\n" +
                "then\n" +
                "  list.add(\"OK\");\n" +
                "end";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        KieContainer kcontainer = KieServices.get().newKieContainer(kieModule.getReleaseId());
        KieSessionsPool pool = kcontainer.newKieSessionsPool( 1 );

        AtomicInteger i = new AtomicInteger(1);

        KieSession ksession = pool.newKieSession();
        List<String> list = new ArrayList<>();
        ksession.setGlobal( "list", list );

        ksession.insert( i );
        ksession.insert( "test" );
        ksession.fireAllRules();

        ksession.dispose();

        assertThat(list.size()).isEqualTo(0);

        ksession = pool.newKieSession();

        ksession.setGlobal( "list", list );
        ksession.insert( i );
        ksession.insert( "test" );
        ksession.fireAllRules();

        ksession.dispose();

        assertThat(list.size()).isEqualTo(1);

        pool.shutdown();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testSegmentMemoriesResetWithNotNodeInTheMiddle(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String drl =
                "import " + FactA.class.getCanonicalName() + ";\n" +
                "import " + FactB.class.getCanonicalName() + ";\n" +
                "import " + FactC.class.getCanonicalName() + ";\n" +
                "rule R1\n" +
                "when\n" +
                "  $factA : FactA( field1 == \"code1\")\n" +
                "  not FactC( f2 == 1 && f1 == \"code1\")\n" +
                "  $factB : FactB( f2 == 1 )\n" +
                "then\n" +
                "end\n" +
                "rule R2\n" +
                "when\n" +
                "  $factA : FactA( field1 == \"code1\")\n" +
                "  not FactC( f2 == 1 && f1 == \"code1\")\n" +
                "  $factB : FactB( f2 == 3 )\n" +
                "then\n" +
                "end\n" +
                "rule R3\n" +
                "when\n" +
                "  $factA: FactA( field1 == \"code1\")\n" +
                "  $factC: FactC( f1 == \"code1\")\n" +
                "then\n" +
                "end";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        KieContainer kcontainer = KieServices.get().newKieContainer(kieModule.getReleaseId());
        KieSessionsPool pool = kcontainer.newKieSessionsPool( 1 );

        KieSession ksession = pool.newKieSession();

        try {
            createFactAndInsert(ksession);
            assertThat(ksession.fireAllRules()).isEqualTo(1); // R1 is fired
        } finally {
            ksession.dispose();
        }

        ksession = pool.newKieSession();

        try {
            createFactAndInsert(ksession);
            assertThat(ksession.fireAllRules()).isEqualTo(1);
        } finally {
            ksession.dispose();
        }

        pool.shutdown();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testSegmentMemoriesResetWithNotNodeInTheMiddle2(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // FactB constrains in R1 and R2 are different from testSegmentMemoriesResetWithNotNodeInTheMiddle()
        String drl =
                "import " + FactA.class.getCanonicalName() + ";\n" +
                "import " + FactB.class.getCanonicalName() + ";\n" +
                "import " + FactC.class.getCanonicalName() + ";\n" +
                "rule R1\n" +
                "when\n" +
                "  $factA : FactA( field1 == \"code1\")\n" +
                "  not FactC( f2 == 1 && f1 == \"code1\")\n" +
                "  $factB : FactB( f2 == 3 )\n" +
                "then\n" +
                "end\n" +
                "rule R2\n" +
                "when\n" +
                "  $factA : FactA( field1 == \"code1\")\n" +
                "  not FactC( f2 == 1 && f1 == \"code1\")\n" +
                "  $factB : FactB( f2 == 1 )\n" +
                "then\n" +
                "end\n" +
                "rule R3\n" +
                "when\n" +
                "  $factA: FactA( field1 == \"code1\")\n" +
                "  $factC: FactC( f1 == \"code1\")\n" +
                "then\n" +
                "end";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        KieContainer kcontainer = KieServices.get().newKieContainer(kieModule.getReleaseId());
        KieSessionsPool pool = kcontainer.newKieSessionsPool( 1 );

        KieSession ksession = pool.newKieSession();

        try {
            createFactAndInsert(ksession);
            assertThat(ksession.fireAllRules()).isEqualTo(1); // R2 is fired
        } finally {
            ksession.dispose();
        }

        ksession = pool.newKieSession();

        try {
            createFactAndInsert(ksession);
            assertThat(ksession.fireAllRules()).isEqualTo(1);
        } finally {
            ksession.dispose();
        }

        pool.shutdown();
    }

    private void createFactAndInsert(KieSession ksession) {
        FactA factA = new FactA();
        factA.setField1("code1");
        ksession.insert(factA);

        FactB factB = new FactB();
        factB.setF2(1);
        ksession.insert(factB);

        FactC factC = new FactC();
        factC.setF1("code3");
        factC.setF2(2);
        ksession.insert(factC);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testStatelessSequential(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-3228
        String drl =
                "import " + AtomicInteger.class.getCanonicalName() + ";\n" +
                "global java.util.List list\n" +
                "rule R1 when\n" +
                "  String()\n" +
                "  Integer()\n" +
                "then\n" +
                "  list.add(\"OK\");\n" +
                "end";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        KieSessionsPool pool = kbase.newKieSessionsPool( 1 );

        StatelessKieSession ksession = pool.newStatelessKieSession();

        List<String> list = new ArrayList<>();

        List<Command> commands = new ArrayList<>(5);
        commands.add(CommandFactory.newSetGlobal("list", list));
        commands.add(CommandFactory.newInsert("test"));
        commands.add(CommandFactory.newInsert(1));
        commands.add(CommandFactory.newFireAllRules());
        ksession.execute(CommandFactory.newBatchExecution(commands));

        assertThat(list.size()).isEqualTo(1);

        list.clear();

        ksession.execute(CommandFactory.newBatchExecution(commands));

        assertThat(list.size()).isEqualTo(1);

        pool.shutdown();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testListenersReset(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final KieContainerSessionsPool pool = getKieContainer(kieBaseTestConfiguration).newKieSessionsPool( 1 );
        KieSession ksession = pool.newKieSession();
        try {
            ksession.addEventListener(new DefaultAgendaEventListener());
            ksession.addEventListener(new DefaultRuleRuntimeEventListener());
            ((RuleEventManager) ksession).addEventListener(new RuleEventListener() {});
        } finally {
            ksession.dispose();
        }
        ksession = pool.newKieSession();
        try {
            assertThat(ksession.getAgendaEventListeners()).hasSize(0);
            assertThat(ksession.getRuleRuntimeEventListeners()).hasSize(0);
            assertThat(((EventSupport) ksession).getRuleEventSupport().getEventListeners()).hasSize(0);
        } finally {
            ksession.dispose();
        }
    }
}
