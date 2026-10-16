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
package org.drools.mvel.integrationtests.sequential;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.drools.drl.parser.DroolsParserException;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.mvel.compiler.Address;
import org.drools.mvel.compiler.Cheese;
import org.drools.mvel.compiler.Message;
import org.drools.mvel.compiler.Person;
import org.drools.mvel.integrationtests.DynamicRulesTest;
import org.drools.mvel.integrationtests.phreak.A;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieModule;
import org.kie.api.conf.SequentialOption;
import org.kie.api.definition.KiePackage;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.AgendaGroupPoppedEvent;
import org.kie.api.event.rule.AgendaGroupPushedEvent;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.MatchCancelledEvent;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.event.rule.RuleFlowGroupActivatedEvent;
import org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.internal.command.CommandFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SequentialTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testSequentialPlusPhreakOperationComplex(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        String str = "";
        str += "package org.drools.mvel.compiler.test\n";
        str +="import " + A.class.getCanonicalName() + "\n";
        str +="global  " + List.class.getCanonicalName() + " list\n";


        // Focus is done as g1, g2, g1 to demonstrate that groups will not re-activate
        str +="rule r0 when\n";
        str +="then\n";
        str +="    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( 'g1' ).setFocus();\n";
        str +="    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( 'g2' ).setFocus();\n";
        str +="    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( 'g1' ).setFocus();\n";
        str +="end\n";
        str +="rule r1 agenda-group 'g1' when\n";
        str +="    a : A( object > 0 )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="    modify(a) { setObject( 3 ) };\n";
        str +="end\n";

        // r1_x is here to show they do not react when g2.r9 changes A o=2,
        // i.e. checking that re-activating g1 won't cause it to pick up previous non evaluated rules.
        // this is mostly checking that the no linking doesn't interfere with the expected results.
        str +="rule r1_x agenda-group 'g1' when\n";
        str +="    a : A( object == 2 )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        // r1_y is here to show it does not react when A is changed to o=5 in r3
        str +="rule r1_y agenda-group 'g1' when\n";
        str +="    a : A( object == 5 )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        str +="rule r2 agenda-group 'g1' when\n";
        str +="    a : A( object < 3 )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";
        str +="rule r3 agenda-group 'g1' when\n";
        str +="    a : A(object >= 3  )\n";
        str +="then\n";
        str +="    modify(a) { setObject( 5 ) };\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        // Checks that itself, f3 and r1_y do not react as they are higher up
        str +="rule r4 agenda-group 'g1' when\n";
        str +="    a : A(object >= 2  )\n";
        str +="then\n";
        str +="    modify(a) { setObject( 5 ) };\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        // Checks that while this at one point matches, it does not match by the time g2 is entered
        // nor does it react when r9 changes a o=2
        str +="rule r6 agenda-group 'g2' when\n";
        str +="    a : A(object < 5  )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        str +="rule r7 agenda-group 'g2' when\n";
        str +="    a : A(object >= 3  )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";
        str +="rule r8 agenda-group 'g2' when\n";
        str +="    a : A(object >= 5  )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        // This changes A o=2 to check if g1.r1_x incorrect reacts when g1 is re-entered
        str +="rule r9 agenda-group 'g2' when\n";
        str +="    a : A(object >= 5  )\n";
        str +="then\n";
        str +="    modify(a) { setObject( 2 ) };\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, str);
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();

        final List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );

        ksession.execute( CommandFactory.newInsertElements(Arrays.asList(new A(1))));

        assertThat(list.size()).isEqualTo(6);
        assertThat(list.get(0)).isEqualTo("r1");
        assertThat(list.get(1)).isEqualTo("r3");
        assertThat(list.get(2)).isEqualTo("r4");
        assertThat(list.get(3)).isEqualTo("r7");
        assertThat(list.get(4)).isEqualTo("r8");
        assertThat(list.get(5)).isEqualTo("r9");
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testSequentialPlusPhreakRevisitOriginallyEmptyGroup(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        String str = "";
        str += "package org.drools.mvel.compiler.test\n";
        str +="import " + A.class.getCanonicalName() + "\n";
        str +="global  " + List.class.getCanonicalName() + " list\n";

        // Focus is done as g1, g2, g1 to demonstrate that groups will not re-activate
        str +="rule r0 when\n";
        str +="then\n";
        str +="    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( 'g1' ).setFocus();\n";
        str +="    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( 'g2' ).setFocus();\n";
        str +="    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( 'g1' ).setFocus();\n";
        str +="end\n";

        // r1_x is here to show they do not react when g2.r9 changes A o=2,
        // i.e. checking that re-activating g1 won't cause it to pick up previous non evaluated rules.
        // this is mostly checking that the none linking doesn't interfere with the expected results.
        // additional checks this works if g1 never had any Matches on the first visit
        str +="rule r1_x agenda-group 'g1' when\n";
        str +="    a : A( object == 2 )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        // This changes A o=2 to check if g1.r1_x incorrect reacts when g1 is re-entered
        str +="rule r9 agenda-group 'g2' when\n";
        str +="    a : A(object >= 5  )\n";
        str +="then\n";
        str +="    modify(a) { setObject( 2 ) };\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, str);
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();
        final List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );

        ksession.execute( CommandFactory.newInsertElements(Arrays.asList(new A(5))));

        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0)).isEqualTo("r9");
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testBasicOperation(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        final KieModule kieModule = KieUtil.getKieModuleFromClasspathResources("test", getClass(), kieBaseTestConfiguration, "simpleSequential.drl");
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();
        final List list = new ArrayList();
        ksession.setGlobal( "list",
                           list );

        final Person p1 = new Person( "p1",
                                      "stilton" );
        final Person p2 = new Person( "p2",
                                      "cheddar" );
        final Person p3 = new Person( "p3",
                                      "stilton" );

        final Cheese stilton = new Cheese( "stilton",
                                           15 );
        final Cheese cheddar = new Cheese( "cheddar",
                                           15 );


        ksession.execute( CommandFactory.newInsertElements( Arrays.asList(p1, stilton, p2, cheddar, p3)));

        assertThat(list.size()).isEqualTo(3);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testSalience(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        final KieModule kieModule = KieUtil.getKieModuleFromClasspathResources("test", getClass(), kieBaseTestConfiguration, "simpleSalience.drl");
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();

        final List list = new ArrayList();
        ksession.setGlobal( "list",
                           list );

        ksession.execute( new Person( "pob")  );

        assertThat(list.size()).isEqualTo(3);

        assertThat(list.get(0)).isEqualTo("rule 3");
        assertThat(list.get(1)).isEqualTo("rule 2");
        assertThat(list.get(2)).isEqualTo("rule 1");
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testKnowledgeRuntimeAccess(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        String str = "";
        str += "package org.drools.mvel.compiler.test\n";
        str +="import org.drools.mvel.compiler.Message\n";
        str +="rule \"Hello World\"\n";
        str +="when\n";
        str +="    Message( )\n";
        str +="then\n";
        str +="    System.out.println( drools.getKieRuntime() );\n";
        str +="end\n";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, str);
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();

        ksession.execute( new Message( "help" ) );
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testEvents(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        String str = "";
        str += "package org.drools.mvel.compiler.test\n";
        str +="import org.drools.mvel.compiler.Message\n";
        str +="rule \"Hello World\"\n";
        str +="when\n";
        str +="    Message( )\n";
        str +="then\n";
        str +="    System.out.println( drools.getKieRuntime() );\n";
        str +="end\n";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, str);
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();

        final List list = new ArrayList();

        ksession.addEventListener( new AgendaEventListener() {

            public void matchCancelled( MatchCancelledEvent event ) {
                assertThat(event.getKieRuntime()).isNotNull();
                list.add( event );
            }

            public void matchCreated( MatchCreatedEvent event ) {
                assertThat(event.getKieRuntime()).isNotNull();
                list.add( event );
            }

            public void afterMatchFired( AfterMatchFiredEvent event ) {
                assertThat(event.getKieRuntime()).isNotNull();
                list.add( event );
            }

            public void agendaGroupPopped( AgendaGroupPoppedEvent event ) {
                assertThat(event.getKieRuntime()).isNotNull();
                list.add( event );
            }

            public void agendaGroupPushed( AgendaGroupPushedEvent event ) {
                assertThat(event.getKieRuntime()).isNotNull();
                list.add( event );
            }

            public void beforeMatchFired( BeforeMatchFiredEvent event ) {
                assertThat(event.getKieRuntime()).isNotNull();
                list.add( event );
            }

            public void beforeRuleFlowGroupActivated( RuleFlowGroupActivatedEvent event ) {
                assertThat(event.getKieRuntime()).isNotNull();
                list.add( event );
            }

            public void afterRuleFlowGroupActivated( RuleFlowGroupActivatedEvent event ) {
                assertThat(event.getKieRuntime()).isNotNull();
                list.add( event );
            }

            public void beforeRuleFlowGroupDeactivated( RuleFlowGroupDeactivatedEvent event ) {
                assertThat(event.getKieRuntime()).isNotNull();
                list.add( event );
            }

            public void afterRuleFlowGroupDeactivated( RuleFlowGroupDeactivatedEvent event ) {
                assertThat(event.getKieRuntime()).isNotNull();
                list.add( event );
            }

        } );

        ksession.addEventListener( new RuleRuntimeEventListener() {

            public void objectInserted( ObjectInsertedEvent event ) {
                assertThat(event.getKieRuntime()).isNotNull();
                list.add( event );
            }

            public void objectDeleted( ObjectDeletedEvent event ) {
                assertThat(event.getKieRuntime()).isNotNull();
                list.add( event );
            }

            public void objectUpdated( ObjectUpdatedEvent event ) {
                assertThat(event.getKieRuntime()).isNotNull();
                list.add( event );
            }

        } );

        ksession.execute( new Message( "help" ) );

        assertThat(list.size()).isEqualTo(4);
    }


    // JBRULES-1567 - ArrayIndexOutOfBoundsException in sequential execution after calling RuleBase.addPackage(..)
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testSequentialWithRulebaseUpdate(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        final KieModule kieModule = KieUtil.getKieModuleFromClasspathResources("test", getClass(), kieBaseTestConfiguration, "simpleSalience.drl");
        final InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();

        final List list = new ArrayList();
        ksession.setGlobal( "list", list );

        ksession.execute(new Person("pob"));

        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", DynamicRulesTest.class, kieBaseTestConfiguration, "test_Dynamic3.drl").getKiePackages();
        kbase.addPackages(kpkgs);

        ksession = kbase.newStatelessKieSession();
        ksession.setGlobal( "list", list );
        Person person  = new Person("bop");
        ksession.execute(person);

        assertThat(list.size()).isEqualTo(7);

        assertThat(list.get(0)).isEqualTo("rule 3");
        assertThat(list.get(1)).isEqualTo("rule 2");
        assertThat(list.get(2)).isEqualTo("rule 1");
        assertThat(list.get(3)).isEqualTo("rule 3");
        assertThat(list.get(4)).isEqualTo("rule 2");
        assertThat(list.get(5)).isEqualTo("rule 1");
        assertThat(list.get(6)).isEqualTo(person);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testProfileSequential(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {

        runTestProfileManyRulesAndFacts(kieBaseTestConfiguration, true, "Sequential mode", 0, "sequentialProfile.drl"  );
        runTestProfileManyRulesAndFacts(kieBaseTestConfiguration, true, "Sequential mode", 0, "sequentialProfile.drl"  );

        System.gc();
        Thread.sleep( 100 );
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testProfileRETE(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        runTestProfileManyRulesAndFacts(kieBaseTestConfiguration, false, "Normal RETE mode", 0, "sequentialProfile.drl"  );
        runTestProfileManyRulesAndFacts(kieBaseTestConfiguration, false, "Normal RETE mode", 0, "sequentialProfile.drl"  );

        System.gc();
        Thread.sleep( 100 );
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testNumberofIterationsSeq(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        //test throughput
        runTestProfileManyRulesAndFacts(kieBaseTestConfiguration, true,
                                         "SEQUENTIAL",
                                         2000, "sequentialProfile.drl" );
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testNumberofIterationsRETE(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        //test throughput
        runTestProfileManyRulesAndFacts(kieBaseTestConfiguration, false,
                                         "RETE",
                                         2000, "sequentialProfile.drl"  );

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testPerfJDT(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        runTestProfileManyRulesAndFacts(kieBaseTestConfiguration, true,
                                         "JDT",
                                         2000, "sequentialProfile.drl"  );

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testPerfMVEL(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        runTestProfileManyRulesAndFacts(kieBaseTestConfiguration, true,
                                         "MVEL",
                                         2000, "sequentialProfileMVEL.drl"  );

    }


    private void runTestProfileManyRulesAndFacts(KieBaseTestConfiguration kieBaseTestConfiguration, boolean sequentialMode,
                                                 String message,
                                                 int timetoMeasureIterations,
                                                 String file) throws DroolsParserException, IOException, Exception {
        SequentialOption opt;
        if ( sequentialMode ) {
            opt = SequentialOption.YES;
        }   else {
            opt = SequentialOption.NO;
        }
        final KieModule kieModule = KieUtil.getKieModuleFromClasspathResources("test", getClass(), kieBaseTestConfiguration, file);
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, opt);
        StatelessKieSession ksession = kbase.newStatelessKieSession();

        final List list = new ArrayList();
        ksession.setGlobal( "list",
                           list );

        Object[] data = new Object[50000];
        for ( int i = 0; i < data.length; i++ ) {

            if ( i % 2 == 0 ) {
                final Person p = new Person( "p" + i,
                                             "stilton" );
                data[i] = p;
            } else {
                data[i] = new Cheese( "cheddar",
                                      i );
            }
        }

        if ( timetoMeasureIterations == 0 ) {
            //one shot measure
            long start = System.currentTimeMillis();
            ksession.execute( CommandFactory.newInsertElements(Arrays.asList(data)));
            System.out.println( "Time for " + message + ":" + (System.currentTimeMillis() - start) );
            assertThat(list.size() > 0).isTrue();

        } else {
            //lots of shots
            //test throughput
            long start = System.currentTimeMillis();
            long end = start + timetoMeasureIterations;
            int count = 0;
            while ( System.currentTimeMillis() < end ) {
                StatelessKieSession sess2 = kbase.newStatelessKieSession();
                List list2 = new ArrayList();
                sess2.setGlobal( "list",
                                 list2 );

                sess2.execute( CommandFactory.newInsertElements(Arrays.asList(data)));
                //session.execute( data );
                count++;
            }
            System.out.println( "Iterations in for " + message + " : " + count );

        }

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testSequentialWithNoLoop(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        // BZ-1228098
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import \n" + Message.class.getCanonicalName() + ";" +
                "rule R1 no-loop when\n" +
                "    $s : String( )" +
                "    $m : Message( )\n" +
                "then\n" +
                "    modify($m) { setMessage($s) };\n" +
                "end\n";

        KieServices ks = KieServices.Factory.get();
        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, str);
        KieContainer kieContainer = ks.newKieContainer(kieModule.getReleaseId());
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession sequentialKsession = kbase.newStatelessKieSession();

        List result = (List) sequentialKsession.execute( CommandFactory.newInsertElements(Arrays.asList("test", new Message())));
        assertThat(result.size()).isEqualTo(2);

        StatelessKieSession ksession = kieContainer.getKieBase().newStatelessKieSession();
        result = (List) ksession.execute( CommandFactory.newInsertElements(Arrays.asList("test", new Message())));
        assertThat(result.size()).isEqualTo(2);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testSharedSegment(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        // BZ-1228313
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import \n" + Message.class.getCanonicalName() + ";" +
                "rule R1 when\n" +
                "    $s : String()\n" +
                "    $m : Message()\n" +
                "    $i : Integer( this < $s.length )\n" +
                "then\n" +
                "    modify($m) { setMessage($s) };\n" +
                "end\n" +
                "\n" +
                "rule R2 when\n" +
                "    $s : String()\n" +
                "    $m : Message()\n" +
                "    $i : Integer( this > $s.length )\n" +
                "then\n" +
                "end\n";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, str);
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();

        ksession.execute( CommandFactory.newInsertElements(Arrays.asList("test", new Message(), 3, 5)));
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testSequentialFromNodeUpdate(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // Immediate propagation flushes the from insert before update(). @watch(*) lets that
        // update reach the from node when property reactivity is enabled.
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Address.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule \"from-binding\" @Propagation(IMMEDIATE) when\n" +
                "    $p : Person() @watch(*)\n" +
                "    $a : Address() from $p.addresses\n" +
                "then\n" +
                "    list.add( $a.getStreet() );\n" +
                "end\n" +
                "rule \"update-person\" salience 100 when\n" +
                "    $p : Person( happy == false )\n" +
                "then\n" +
                "    $p.setHappy( true );\n" +
                "    update( $p );\n" +
                "    list.add( \"updated\" );\n" +
                "end\n";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, str);

        Person sequentialUpdate = new Person("updated");
        sequentialUpdate.addAddress(new Address("London"));
        final KieBase sequentialKbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession sequentialSession = sequentialKbase.newStatelessKieSession();
        sequentialSession.setGlobal("list", new ArrayList());
        assertThatThrownBy(() -> sequentialSession.execute(sequentialUpdate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("update()/modify() propagation through a from binding is unsupported when tuple memory is disabled by sequential mode");

        Person nonSequential = new Person("non-sequential");
        nonSequential.addAddress(new Address("London"));
        final KieBase nonSequentialKbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.NO);
        StatelessKieSession nonSequentialSession = nonSequentialKbase.newStatelessKieSession();
        final List nonSequentialList = new ArrayList();
        nonSequentialSession.setGlobal("list", nonSequentialList);
        nonSequentialSession.execute(nonSequential);
        assertThat(nonSequential.isHappy()).isTrue();
        assertThat(nonSequentialList).contains("updated", "London");

        Person insertOnly = new Person("insert-only");
        insertOnly.setHappy(true);
        insertOnly.addAddress(new Address("London"));
        StatelessKieSession insertOnlySession = sequentialKbase.newStatelessKieSession();
        final List insertOnlyList = new ArrayList();
        insertOnlySession.setGlobal("list", insertOnlyList);
        insertOnlySession.execute(insertOnly);
        assertThat(insertOnlyList).containsExactly("London");
    }
}
