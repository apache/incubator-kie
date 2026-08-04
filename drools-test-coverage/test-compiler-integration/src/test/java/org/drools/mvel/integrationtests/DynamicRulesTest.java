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
package org.drools.mvel.integrationtests;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.drools.core.RuleBaseConfiguration;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.impl.EnvironmentFactory;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.core.marshalling.ClassObjectMarshallingStrategyAcceptor;
import org.drools.serialization.protobuf.marshalling.IdentityPlaceholderResolverStrategy;
import org.drools.mvel.compiler.Cheese;
import org.drools.mvel.compiler.FactA;
import org.drools.mvel.compiler.FactB;
import org.drools.mvel.compiler.Order;
import org.drools.mvel.compiler.OrderItem;
import org.drools.mvel.compiler.Person;
import org.drools.mvel.compiler.PersonInterface;
import org.drools.mvel.compiler.Precondition;
import org.drools.mvel.compiler.StockTick;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.builder.KieModule;
import org.kie.api.definition.KiePackage;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.kie.api.marshalling.ObjectMarshallingStrategy;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class DynamicRulesTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        // TODO: EM failed with some tests. File JIRAs
        return TestParametersUtil2.getKieBaseCloudConfigurations(false).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testDynamicRuleAdditions(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        InternalKnowledgeBase kbase = (InternalKnowledgeBase)KieBaseUtil.getKieBaseFromClasspathResources("test", getClass(), kieBaseTestConfiguration, "test_Dynamic1.drl");
        KieSession workingMemory = kbase.newKieSession();
        workingMemory.setGlobal("total",
                                new Integer(0));

        final List< ? > list = new ArrayList<Object>();
        workingMemory.setGlobal( "list",
                                 list );

        // Adding person in advance. There is no Person() object
        // type node in memory yet, but the rule engine is supposed
        // to handle that correctly
        final PersonInterface bob = new Person( "bob",
                                                "stilton" );
        bob.setStatus( "Not evaluated" );
        workingMemory.insert( bob );

        final Cheese stilton = new Cheese( "stilton",
                                           5 );
        workingMemory.insert( stilton );

        final Cheese cheddar = new Cheese( "cheddar",
                                           5 );
        workingMemory.insert(cheddar);
        workingMemory.fireAllRules();

        assertThat(list.size()).isEqualTo(1);

        assertThat(list.get(0)).isEqualTo("stilton");

        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_Dynamic2.drl").getKiePackages();
        kbase.addPackages(kpkgs);

        workingMemory.fireAllRules();
        assertThat(list.size()).isEqualTo(5);

        assertThat(list.get(0)).isEqualTo("stilton");

        assertThat("cheddar".equals(list.get(1)) || "cheddar".equals(list.get(2))).isTrue();

        assertThat("stilton".equals(list.get(1)) || "stilton".equals(list.get(2))).isTrue();

        list.clear();

        kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_Dynamic3.drl").getKiePackages();
        kbase.addPackages(kpkgs);

        // Package 3 has a rule working on Person instances.
        // As we added person instance in advance, rule should fire now
        workingMemory.fireAllRules();

        assertThat(bob.getStatus()).as("Rule from package 3 should have been fired").isEqualTo("match Person ok");

        assertThat(list.size()).isEqualTo(1);

        assertThat(list.get(0)).isEqualTo(bob);

        kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_Dynamic4.drl").getKiePackages();
        kbase.addPackages(kpkgs);
        workingMemory.fireAllRules();

        assertThat(bob.getStatus()).as("Rule from package 4 should have been fired").isEqualTo("Who likes Stilton ok");

        assertThat(list.size()).isEqualTo(2);

        assertThat(list.get(1)).isEqualTo(bob);

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testDynamicRuleRemovals(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        InternalKnowledgeBase kbase = (InternalKnowledgeBase)KieBaseUtil.getKieBaseFromClasspathResources("test", getClass(), kieBaseTestConfiguration, "test_Dynamic1.drl", "test_Dynamic3.drl", "test_Dynamic4.drl");

        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_Dynamic2.drl").getKiePackages();
        kbase.addPackages(kpkgs);

        KieSession wm = kbase.newKieSession();
//        AgendaEventListener ael = mock( AgendaEventListener.class );
//        wm.addEventListener( ael );

        final List< ? > list = new ArrayList<Object>();
        wm.setGlobal( "list",
                                 list );

        final PersonInterface bob = new Person( "bob",
                                                "stilton" );
        bob.setStatus( "Not evaluated" );
        FactHandle fh0 = wm.insert( bob );

        final Cheese stilton1 = new Cheese( "stilton",
                                            5 );
        FactHandle fh1 = wm.insert( stilton1 );

        final Cheese stilton2 = new Cheese( "stilton",
                                            3 );
        FactHandle fh2 = wm.insert( stilton2 );

        final Cheese stilton3 = new Cheese( "stilton",
                                            1 );
        FactHandle fh3 = wm.insert( stilton3 );

        final Cheese cheddar = new Cheese( "cheddar",
                                           5 );
        FactHandle fh4 = wm.insert( cheddar );

        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(15);
        list.clear();

        kbase.removeRule("org.drools.mvel.compiler.test",
                         "Who likes Stilton");

        wm.update(fh0, bob);
        wm.update(fh1, stilton1);
        wm.update(fh2, stilton2);
        wm.update(fh3, stilton3);
        wm.update(fh4, cheddar);
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(12);
        list.clear();

        kbase.removeRule("org.drools.mvel.compiler.test",
                         "like cheese");

        wm.update( fh0, bob);
        wm.update(fh1, stilton1);
        wm.update(fh2, stilton2);
        wm.update(fh3, stilton3);
        wm.update(fh4, cheddar);
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(8);
        list.clear();

        final Cheese muzzarela = new Cheese( "muzzarela",
                                             5 );
        wm.insert( muzzarela );
        wm.fireAllRules();

        assertThat(list.size()).isEqualTo(1);
        list.clear();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testDynamicRuleRemovalsUnusedWorkingMemory(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.getKieBaseFromClasspathResources("test", getClass(), kieBaseTestConfiguration, "test_Dynamic1.drl",
                                                                                                           "test_Dynamic2.drl",
                                                                                                           "test_Dynamic3.drl",
                                                                                                           "test_Dynamic4.drl");

        KieSession workingMemory = kbase.newKieSession();

        assertThat(kbase.getKiePackages().size()).isEqualTo(2);

        KiePackage knowledgePackage = null;
        for (KiePackage pkg : kbase.getKiePackages()) {
            if ( pkg.getName().equals( "org.drools.mvel.compiler.test" ) ) {
                knowledgePackage = pkg;
                break;
            }
        }

        assertThat(knowledgePackage.getRules().size()).isEqualTo(5);

        kbase.removeRule( "org.drools.mvel.compiler.test",
                          "Who likes Stilton" );
        assertThat(knowledgePackage.getRules().size()).isEqualTo(4);

        kbase.removeRule( "org.drools.mvel.compiler.test",
                          "like cheese" );
        assertThat(knowledgePackage.getRules().size()).isEqualTo(3);

        kbase.removeKiePackage( "org.drools.mvel.compiler.test" );
        assertThat(kbase.getKiePackages().size()).isEqualTo(1);
    }

    @Disabled("Fails with standard-drl after changing to new API. See DROOLS-6060")
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testDynamicFunction(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        //JBRULES-1258 serialising a package breaks function removal -- left the serialisation commented out for now

        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicFunction1.drl").getKiePackages();

        InternalKnowledgeBase kbase = (InternalKnowledgeBase)KieBaseUtil.getKieBaseFromClasspathResources("test", getClass(), kieBaseTestConfiguration);

        kbase.addPackages( kpkgs );
//        kbase = SerializationHelper.serializeObject( kbase );

        KieSession workingMemory = kbase.newKieSession();

        final List<?> list = new ArrayList<Object>();
        workingMemory.setGlobal( "list",
                                 list );

        final Cheese stilton = new Cheese( "stilton",
                                           5 );
        workingMemory.insert( stilton );

        workingMemory.fireAllRules();

        assertThat(list.get(0)).isEqualTo(new Integer( 5 ));

        // Check a function can be removed from a package.
        // Once removed any efforts to use it should throw an Exception
        kbase.removeFunction( "org.drools.mvel.compiler.test",
                              "addFive" );

        final Cheese cheddar = new Cheese( "cheddar",
                                           5 );
        workingMemory.insert( cheddar );

        assertThatThrownBy(() -> workingMemory.fireAllRules()).isInstanceOf(Throwable.class);

        // Check a new function can be added to replace an old function
        Collection<KiePackage> kpkgs2 = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicFunction2.drl").getKiePackages();
        kbase.addPackages( kpkgs2 );

        final Cheese brie = new Cheese( "brie",
                                        5 );
        workingMemory.insert( brie );

        workingMemory.fireAllRules();

        assertThat(list.get(1)).isEqualTo(new Integer( 6 ));

        Collection<KiePackage> kpkgs3 = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicFunction3.drl").getKiePackages();

        kbase.addPackages( kpkgs3 );

        final Cheese feta = new Cheese( "feta",
                                        5 );
        workingMemory.insert( feta );

        workingMemory.fireAllRules();

        assertThat(list.get(2)).isEqualTo(new Integer( 5 ));
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testRemovePackage(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_RemovePackage.drl").getKiePackages();
        final String packageName = kpkgs.iterator().next().getName();
        InternalKnowledgeBase kbase = (InternalKnowledgeBase)KieBaseUtil.getKieBaseFromClasspathResources("test", getClass(), kieBaseTestConfiguration);

        kbase.addPackages( kpkgs );

        KieSession session = kbase.newKieSession();

        session.insert( new Precondition( "genericcode",
                                          "genericvalue" ) );
        session.fireAllRules();

        InternalKnowledgeBase ruleBaseWM = (InternalKnowledgeBase) session.getKieBase();
        ruleBaseWM.removeKiePackage( packageName );

        kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_RemovePackage.drl").getKiePackages();

        ruleBaseWM.addPackages( kpkgs );

        session = SerializationHelper.getSerialisedStatefulKnowledgeSession( session, 
                                                                             true );
        session.fireAllRules();

        ruleBaseWM.removeKiePackage(packageName);
        ruleBaseWM.addPackages( SerializationHelper.serializeObject( kpkgs ) );

        ruleBaseWM.removeKiePackage( packageName );
        ruleBaseWM.addPackages(SerializationHelper.serializeObject(kpkgs));
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testDynamicRules(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        InternalKnowledgeBase kbase = (InternalKnowledgeBase)KieBaseUtil.getKieBaseFromClasspathResources("test", getClass(), kieBaseTestConfiguration);
        KieSession session = kbase.newKieSession();

        final Cheese a = new Cheese( "stilton",
                                     10 );
        final Cheese b = new Cheese( "stilton",
                                     15 );
        final Cheese c = new Cheese( "stilton",
                                     20 );
        session.insert( a );
        session.insert( b );
        session.insert(c);

        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicRules.drl").getKiePackages();
        kbase.addPackages(kpkgs);

        session = SerializationHelper.getSerialisedStatefulKnowledgeSession( session, 
                                                                             true );

        session.fireAllRules();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testDynamicRules2(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        InternalKnowledgeBase kbase = (InternalKnowledgeBase)KieBaseUtil.getKieBaseFromClasspathResources("test", getClass(), kieBaseTestConfiguration);
        KieSession session = kbase.newKieSession();

        // Assert some simple facts
        final FactA a = new FactA( "hello",
                                   new Integer( 1 ),
                                   new Float( 3.14 ) );
        final FactB b = new FactB( "hello",
                                   new Integer( 2 ),
                                   new Float( 6.28 ) );
        session.insert( a );
        session.insert( b );

        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicRules2.drl").getKiePackages();
        kbase.addPackages( kpkgs );

        session = SerializationHelper.getSerialisedStatefulKnowledgeSession( session, 
                                                                             true );

        session.fireAllRules();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testRuleBaseAddRemove(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        InternalKnowledgeBase kbase = (InternalKnowledgeBase)KieBaseUtil.getKieBaseFromClasspathResources("test", getClass(), kieBaseTestConfiguration);

        //add and remove
        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_Dynamic1.drl").getKiePackages();
        String pkgName = kpkgs.iterator().next().getName();
        kbase.addPackages( kpkgs );
        kbase.removeKiePackage( pkgName );

        //add and remove again
        kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_Dynamic1.drl").getKiePackages();
        pkgName = kpkgs.iterator().next().getName();
        kbase.addPackages( kpkgs );
        kbase.removeKiePackage( pkgName );
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testClassLoaderSwitchsUsingConf(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        try {
            // Creates first class loader and use it to load fact classes
            ClassLoader loader1 = new SubvertedClassLoader( new URL[]{getClass().getResource( "/" )},
                                                            this.getClass().getClassLoader() );
            Class cheeseClass = loader1.loadClass( "org.drools.mvel.compiler.Cheese" );

            InternalKnowledgeBase kbase = (InternalKnowledgeBase)KieBaseUtil.getKieBaseFromClasspathResourcesWithClassLoaderForKieBuilder("test", getClass(), loader1, kieBaseTestConfiguration, "test_Dynamic1.drl");
            KieSession wm = kbase.newKieSession();

            wm.insert( cheeseClass.newInstance() );
            wm.fireAllRules();

            // Creates second class loader and use it to load fact classes
            ClassLoader loader2 = new SubvertedClassLoader( new URL[]{getClass().getResource( "/" )},
                                                            this.getClass().getClassLoader() );
            cheeseClass = loader2.loadClass( "org.drools.mvel.compiler.Cheese" );

            kbase = (InternalKnowledgeBase)KieBaseUtil.getKieBaseFromClasspathResourcesWithClassLoaderForKieBuilder("test", getClass(), loader2, kieBaseTestConfiguration, "test_Dynamic1.drl");
            wm = kbase.newKieSession();
            wm.insert(cheeseClass.newInstance());
            wm.fireAllRules();
        } catch ( ClassCastException cce ) {
            cce.printStackTrace();
            fail( "No ClassCastException should be raised." );
        }

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testClassLoaderSwitchsUsingContext(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        try {
            // Creates first class loader and use it to load fact classes
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            ClassLoader loader1 = new SubvertedClassLoader( new URL[]{getClass().getResource( "/" )},
                                                            this.getClass().getClassLoader() );
            Thread.currentThread().setContextClassLoader( loader1 );
            Class cheeseClass = loader1.loadClass("org.drools.mvel.compiler.Cheese");

            KieBase kbase = KieBaseUtil.getKieBaseFromClasspathResources(getClass(), kieBaseTestConfiguration, "test_Dynamic1.drl");
            KieSession wm = kbase.newKieSession();

            wm.insert( cheeseClass.newInstance() );
            wm.fireAllRules();

            // Creates second class loader and use it to load fact classes
            ClassLoader loader2 = new SubvertedClassLoader( new URL[]{getClass().getResource( "/" )},
                                                            this.getClass().getClassLoader() );
            Thread.currentThread().setContextClassLoader( loader2 );
            cheeseClass = loader2.loadClass( "org.drools.mvel.compiler.Cheese" );

            kbase = KieBaseUtil.getKieBaseFromClasspathResources(getClass(), kieBaseTestConfiguration, "test_Dynamic1.drl");
            wm = kbase.newKieSession();

            wm.insert( cheeseClass.newInstance() );
            wm.fireAllRules();

            Thread.currentThread().setContextClassLoader(original);
        } catch ( ClassCastException cce ) {
            cce.printStackTrace();
            fail( "No ClassCastException should be raised." );
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testCollectDynamicRules(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        checkCollectWithDynamicRules( kieBaseTestConfiguration, "test_CollectDynamicRules1.drl" );
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testCollectDynamicRulesWithExistingOTN(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        checkCollectWithDynamicRules( kieBaseTestConfiguration, "test_CollectDynamicRules1a.drl" );
    }

    private void checkCollectWithDynamicRules(KieBaseTestConfiguration kieBaseTestConfiguration, String originalDrl) throws java.io.IOException, ClassNotFoundException {
        InternalKnowledgeBase kbase = (InternalKnowledgeBase)KieBaseUtil.getKieBaseFromClasspathResources(getClass(), kieBaseTestConfiguration, originalDrl);
        KieSession session = kbase.newKieSession();

        List<?> list = new ArrayList<Object>();
        session.setGlobal( "results",
                                 list );

        session.insert( new Cheese( "stilton",
                                          10 ) );
        session.insert( new Cheese( "brie",
                                          10 ) );
        session.insert( new Cheese( "stilton",
                                          10 ) );
        session.insert( new Cheese( "muzzarela",
                                          10 ) );

        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_CollectDynamicRules2.drl").getKiePackages();
        kbase.addPackages(kpkgs);

        session.fireAllRules();

        // fire all rules is automatic
        assertThat(list.size()).isEqualTo(1);
        assertThat(((List<?>) list.get(0)).size()).isEqualTo(2);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testDynamicNotNode(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        InternalKnowledgeBase kbase = (InternalKnowledgeBase)KieBaseUtil.getKieBaseFromClasspathResources(getClass(), kieBaseTestConfiguration, "test_CollectDynamicRules1.drl");
        Environment env = EnvironmentFactory.newEnvironment();
        env.set( EnvironmentName.OBJECT_MARSHALLING_STRATEGIES, new ObjectMarshallingStrategy[]{
                 new IdentityPlaceholderResolverStrategy( ClassObjectMarshallingStrategyAcceptor.DEFAULT )} );
        KieSession ksession = kbase.newKieSession( null, env );
        List<?> results = new ArrayList<Object>();
        ksession.setGlobal( "results",
                            results );

        final Cheese a = new Cheese( "stilton",
                                     10 );
        final Cheese b = new Cheese( "stilton",
                                     15 );
        final Cheese c = new Cheese( "stilton",
                                     20 );
        ksession.insert( a );
        ksession.insert( b );
        ksession.insert( c );

        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicNotNode.drl").getKiePackages();
        kbase.addPackages( kpkgs );

        ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession( ksession,
                                                                              false );

        results = (List) ksession.getGlobal( "results" );

        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(0);

        kbase.removeKiePackage( "org.drools.mvel.compiler" );

        ksession.retract( ksession.getFactHandle( b ) );

        kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicNotNode.drl").getKiePackages();

        kbase.addPackages( kpkgs );

        ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession( ksession,
                                                                              false );

        results = (List<?>) ksession.getGlobal( "results" );
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testDynamicRulesAddRemove(KieBaseTestConfiguration kieBaseTestConfiguration) {
        try {
            InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.getKieBaseFromClasspathResources(getClass(), kieBaseTestConfiguration, "test_DynamicRulesTom.drl");
            KieSession session = kbase.newKieSession();

            List<?> results = new ArrayList<Object>();
            session.setGlobal( "results",
                               results );

            InternalFactHandle h1 = (InternalFactHandle) session.insert( new Person( "tom",
                                                                                     1 ) );
            InternalFactHandle h2 = (InternalFactHandle) session.insert( new Person( "fred",
                                                                                     2 ) );
            InternalFactHandle h3 = (InternalFactHandle) session.insert( new Person( "harry",
                                                                                     3 ) );
            InternalFactHandle h4 = (InternalFactHandle) session.insert( new Person( "fred",
                                                                                     4 ) );
            InternalFactHandle h5 = (InternalFactHandle) session.insert( new Person( "ed",
                                                                                     5 ) );
            InternalFactHandle h6 = (InternalFactHandle) session.insert( new Person( "tom",
                                                                                     6 ) );
            InternalFactHandle h7 = (InternalFactHandle) session.insert( new Person( "sreeni",
                                                                                     7 ) );
            InternalFactHandle h8 = (InternalFactHandle) session.insert( new Person( "jill",
                                                                                     8 ) );
            InternalFactHandle h9 = (InternalFactHandle) session.insert( new Person( "ed",
                                                                                     9 ) );
            InternalFactHandle h10 = (InternalFactHandle) session.insert( new Person( "tom",
                                                                                      10 ) );

            session.fireAllRules();

            assertThat(results.size()).isEqualTo(3);
            assertThat(results.contains(h1.getObject())).isTrue();
            assertThat(results.contains(h6.getObject())).isTrue();
            assertThat(results.contains(h10.getObject())).isTrue();
            results.clear();

            Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicRulesFred.drl").getKiePackages();
            kbase.addPackages(kpkgs);
            session.fireAllRules();

            assertThat(results.size()).isEqualTo(2);
            assertThat(results.contains(h2.getObject())).isTrue();
            assertThat(results.contains(h4.getObject())).isTrue();
            results.clear();

            kbase.removeKiePackage( "tom" );

            kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicRulesEd.drl").getKiePackages();
            kbase.addPackages(kpkgs);
            session.fireAllRules();

            assertThat(results.size()).isEqualTo(2);
            assertThat(results.contains(h5.getObject())).isTrue();
            assertThat(results.contains(h9.getObject())).isTrue();
            results.clear();

            ((Person) h3.getObject()).setName( "ed" );
            session.update( h3,
                            h3.getObject() );
            session.fireAllRules();

            assertThat(results.size()).isEqualTo(1);
            assertThat(results.contains(h3.getObject())).isTrue();
        } catch ( Exception e ) {
            e.printStackTrace();
            fail( "Should not raise any exception: " + e.getMessage() );
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testDynamicRuleRemovalsSubNetwork(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicRulesWithSubnetwork1.drl",
                                                                                    "test_DynamicRulesWithSubnetwork.drl").getKiePackages();
        
        InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration);
        kbase.addPackages( kpkgs );
        
        kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicRulesWithSubnetwork2.drl").getKiePackages();
        kbase.addPackages( kpkgs );

        KieSession session = kbase.newKieSession();

        final List<?> list = new ArrayList<Object>();
        session.setGlobal( "results",
                           list );

        Order order = new Order();

        OrderItem item1 = new OrderItem( order,
                                         1,
                                         "Adventure Guide Brazil",
                                         OrderItem.TYPE_BOOK,
                                         24 );
        order.addItem( item1 );
        FactHandle item1Fh = session.insert( item1 );

        OrderItem item2 = new OrderItem( order,
                                         2,
                                         "Prehistoric Britain",
                                         OrderItem.TYPE_BOOK,
                                         15 );
        order.addItem( item2 );
        FactHandle item2Fh = session.insert( item2 );

        OrderItem item3 = new OrderItem( order,
                                         3,
                                         "Holiday Music",
                                         OrderItem.TYPE_CD,
                                         9 );
        order.addItem( item3 );
        FactHandle item3Fh = session.insert( item3 );

        OrderItem item4 = new OrderItem( order,
                                         4,
                                         "Very Best of Mick Jagger",
                                         OrderItem.TYPE_CD,
                                         11 );
        order.addItem( item4 );
        FactHandle item4Fh = session.insert( item4 );

        session.insert( order );
        session.fireAllRules();
        assertThat(list.size()).isEqualTo(11);

        kbase.removeRule( "org.drools.mvel.compiler",
                          "Apply Discount on all books" );

        list.clear();
        session.update( item1Fh, item1 );
        session.update( item2Fh, item2 );
        session.update( item3Fh, item3 );
        session.update( item4Fh, item4 );
        session.fireAllRules();

        assertThat(list.size()).isEqualTo(10);

        kbase.removeRule( "org.drools.mvel.compiler",
                          "like book" );
        list.clear();
        session.update( item1Fh, item1 );
        session.update( item2Fh, item2 );
        session.update( item3Fh, item3 );
        session.update( item4Fh, item4 );
        session.fireAllRules();

        assertThat(list.size()).isEqualTo(8);

        final OrderItem item5 = new OrderItem( order, 5, "Sinatra : Vegas", OrderItem.TYPE_CD, 5 );
        FactHandle item5Fh = session.insert( item5 );

        session.fireAllRules();

        assertThat(list.size()).isEqualTo(10);

        kbase.removeKiePackage( "org.drools.mvel.compiler" );
        list.clear();
        session.update( item1Fh, item1 );
        session.update( item2Fh, item2 );
        session.update( item3Fh, item3 );
        session.update( item4Fh, item4 );
        session.update( item5Fh, item5 );
        session.fireAllRules();

        assertThat(list.size()).isEqualTo(0);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testDynamicRuleRemovalsUnusedWorkingMemorySubNetwork(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.getKieBaseFromClasspathResources(getClass(), kieBaseTestConfiguration,
                                                                                                           "test_DynamicRulesWithSubnetwork1.drl",
                                                                                                           "test_DynamicRulesWithSubnetwork2.drl",
                                                                                                           "test_DynamicRulesWithSubnetwork.drl");

        assertThat(kbase.getKiePackages().size()).isEqualTo(2);
        assertThat(kbase.getPackagesMap().get("org.drools.mvel.compiler").getRules().size()).isEqualTo(4);

        kbase.removeRule( "org.drools.mvel.compiler", "Apply Discount on all books" );
        assertThat(kbase.getPackagesMap().get("org.drools.mvel.compiler").getRules().size()).isEqualTo(3);

        kbase.removeRule( "org.drools.mvel.compiler", "like book" );
        assertThat(kbase.getPackagesMap().get("org.drools.mvel.compiler").getRules().size()).isEqualTo(2);

        kbase.removeKiePackage( "org.drools.mvel.compiler" );
        assertThat(kbase.getKiePackages().size()).isEqualTo(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testRemovePackageSubNetwork(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        KieBase kbase = KieBaseUtil.getKieBaseFromClasspathResources(getClass(), kieBaseTestConfiguration, "test_DynamicRulesWithSubnetwork.drl");
        String packageName = kbase.getKiePackages().iterator().next().getName();

        KieSession workingMemory = kbase.newKieSession();

        List<?> results = new ArrayList<Object>();
        workingMemory.setGlobal( "results",
                                 results );

        Order order = new Order();

        OrderItem item1 = new OrderItem( order,
                                         1,
                                         "Adventure Guide Brazil",
                                         OrderItem.TYPE_BOOK,
                                         24 );
        OrderItem item2 = new OrderItem( order,
                                         2,
                                         "Prehistoric Britain",
                                         OrderItem.TYPE_BOOK,
                                         15 );
        OrderItem item3 = new OrderItem( order,
                                         3,
                                         "Holiday Music",
                                         OrderItem.TYPE_CD,
                                         9 );
        OrderItem item4 = new OrderItem( order,
                                         4,
                                         "Very Best of Mick Jagger",
                                         OrderItem.TYPE_CD,
                                         11 );
        OrderItem item5 = new OrderItem( order,
                                         5,
                                         "The Master and Margarita",
                                         OrderItem.TYPE_BOOK,
                                         29 );

        order.addItem( item1 );
        order.addItem( item2 );
        order.addItem( item3 );
        order.addItem( item4 );
        order.addItem( item5 );

        workingMemory.insert( order );
        workingMemory.fireAllRules();
        assertThat(results.size()).isEqualTo(1);
        assertThat(((List) results.get(0)).size()).isEqualTo(3);
        results.clear();

        InternalKnowledgeBase ruleBaseWM = (InternalKnowledgeBase) workingMemory.getKieBase();
        ruleBaseWM.removeKiePackage( packageName );

        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicRulesWithSubnetwork.drl").getKiePackages();
        ruleBaseWM.addPackages(kpkgs);
        
        workingMemory.fireAllRules();
        results = (List) workingMemory.getGlobal( "results" );
        assertThat(results.size()).isEqualTo(1);
        assertThat(((List) results.get(0)).size()).isEqualTo(3);
        results.clear();

        ruleBaseWM.removeKiePackage( packageName );
        ruleBaseWM.addPackages(kpkgs);
        workingMemory.fireAllRules();
        assertThat(results.size()).isEqualTo(1);
        assertThat(((List) results.get(0)).size()).isEqualTo(3);
        results.clear();

        ruleBaseWM.removeKiePackage( packageName );
        ruleBaseWM.addPackages(kpkgs);
        workingMemory.fireAllRules();
        assertThat(results.size()).isEqualTo(1);
        assertThat(((List) results.get(0)).size()).isEqualTo(3);
        results.clear();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testRuleBaseAddRemoveSubNetworks(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        try {
            //add and remove
            InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration);
            Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicRulesWithSubnetwork.drl").getKiePackages();

            KiePackage kpkg = (KiePackage) kpkgs.toArray()[0];
            kbase.addPackages(kpkgs);
            kbase.removeKiePackage(kpkg.getName());

            //add and remove again
            kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicRulesWithSubnetwork.drl").getKiePackages();
            kpkg = ( KiePackage ) kpkgs.toArray()[0];
            kbase.addPackages(kpkgs);
            kbase.removeKiePackage(kpkg.getName());
        } catch ( Exception e ) {
            e.printStackTrace();
            fail( "Should not raise any exception" );
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testDynamicRuleAdditionsWithEntryPoints(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration);
        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicWithEntryPoint.drl").getKiePackages();

        KieSession ksession = kbase.newKieSession();

        // now lets add some knowledge to the kbase
        kbase.addPackages( kpkgs );

        List<StockTick> results = new ArrayList<StockTick>();
        ksession.setGlobal( "results",
                            results );

        EntryPoint ep = ksession.getEntryPoint( "in-channel" );
        ep.insert( new StockTick( 1,
                                  "RHT",
                                  20,
                                  10000 ) );
        ep.insert( new StockTick( 2,
                                  "RHT",
                                  21,
                                  15000 ) );
        ep.insert( new StockTick( 3,
                                  "RHT",
                                  22,
                                  20000 ) );

        ksession.fireAllRules();
        assertThat(results.size()).isEqualTo(3);

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testDynamicRuleRemovalsSubNetworkAndNot(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.getKieBaseFromClasspathResources(getClass(), kieBaseTestConfiguration, "test_DynamicRulesWithNotSubnetwork.drl");
        KieSession ksession = kbase.newKieSession();


        final AgendaEventListener alistener = mock( AgendaEventListener.class );
        ksession.addEventListener( alistener );

        // pattern does not match, so do not activate
        ksession.insert( new Person( "toni" ) );
        ksession.fireAllRules();
        verify( alistener,
                never() ).matchCreated(any(org.kie.api.event.rule.MatchCreatedEvent.class));

        // pattern matches, so create activation
        ksession.insert( new Person( "bob" ) );
        ksession.fireAllRules();
        verify( alistener,
                times( 1 ) ).matchCreated(any(org.kie.api.event.rule.MatchCreatedEvent.class));

        // already active, so no new activation should be created
        ksession.insert( new Person( "mark" ) );
        ksession.fireAllRules();
        verify( alistener,
                times( 1 ) ).matchCreated(any(org.kie.api.event.rule.MatchCreatedEvent.class));

        kbase.removeKiePackage( "org.drools.mvel.compiler" );

        assertThat(kbase.getKiePackages().size()).isEqualTo(0);

        // lets re-compile and add it again
        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_DynamicRulesWithNotSubnetwork.drl").getKiePackages();
        kbase.addPackages( kpkgs );
        ksession.fireAllRules();

                // rule should be reactivated, since data is still in the session
        verify( alistener,
                times( 2 ) ).matchCreated(any(org.kie.api.event.rule.MatchCreatedEvent.class));

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testSharedLIANodeRemoval(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        // it's not a true share, but the liaNode will have two sinks, due to subnetwork.
        String str = "global java.util.List list;\n";
        str += "rule \"test\"\n";
        str += "when\n";
        str += "  exists(eval(true))\n";
        str += "then\n";
        str += " list.add(\"fired\");\n";
        str += "end\n";

        InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration);
        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromKieModuleFromDrl("tmp", kieBaseTestConfiguration, str).getKiePackages();

        // Add once ...
        kbase.addPackages( kpkgs );

        // This one works
        List list = new ArrayList();
        KieSession session = kbase.newKieSession();
        session.setGlobal( "list",
                           list );
        session.fireAllRules();
        assertThat(list.size()).isEqualTo(1);

        list.clear();
        // ... remove ...
        KiePackage kpkg = ( KiePackage ) kpkgs.toArray()[0];
        kbase.removeKiePackage( kpkg.getName() );
        kbase.addPackages( kpkgs );
        session = kbase.newKieSession();
        session.setGlobal( "list",
                           list );
        session.fireAllRules();
        assertThat(list.size()).isEqualTo(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testDynamicRulesWithTypeDeclarations(KieBaseTestConfiguration kieBaseTestConfiguration) {
        
        // Note: This test originally use "kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder( kbase );" which is not possible with KieBuilder
        // Probably this new test is not valid for exec-model and we can remove this test
        
        String type = "package com.sample\n" +
                      "declare type Foo\n" +
                      "  id : int\n" +
                      "end\n";

        String r1 = "package com.sample\n" +
                    "rule R1 when\n" +
                    "  not Foo()\n" +
                    "then\n" +
                    "  insert( new Foo(1) );\n" +
                    "end\n";

        String r2 = "package com.sample\n" +
                "rule R2 when\n" +
                "  $f : Foo()\n" +
                "then\n" +
                "  $f.setId( 2 );\n" +
                "end\n";

        InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration);
        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromKieModuleFromDrl("tmp", kieBaseTestConfiguration, type).getKiePackages();
        kbase.addPackages(kpkgs);
        
        KieSession ksession = kbase.newKieSession();

        AgendaEventListener ael = mock( AgendaEventListener.class );
        ksession.addEventListener( ael );
        
        ksession.fireAllRules();
        verify( ael, never() ).afterMatchFired( any( AfterMatchFiredEvent.class ) );

        kpkgs = KieBaseUtil.getKieBaseFromKieModuleFromDrl("tmp", kieBaseTestConfiguration, type, r1).getKiePackages();
        kbase.addPackages(kpkgs);
        
        ksession.fireAllRules();
        ArgumentCaptor<AfterMatchFiredEvent> capt = ArgumentCaptor.forClass( AfterMatchFiredEvent.class );
        verify( ael, times(1) ).afterMatchFired( capt.capture() );
        assertThat(capt.getValue().getMatch().getRule().getName()).isEqualTo("R1");

        kpkgs = KieBaseUtil.getKieBaseFromKieModuleFromDrl("tmp", kieBaseTestConfiguration, type, r2).getKiePackages();
        kbase.addPackages(kpkgs);
        
        ksession.fireAllRules();
        verify( ael, times(2) ).afterMatchFired( capt.capture() );
        assertThat(capt.getAllValues().get(2).getMatch().getRule().getName()).isEqualTo("R2");
        
        ksession.dispose();
        
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testJBRULES_2206(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieBaseConfiguration config = RuleBaseFactory.newKnowledgeBaseConfiguration();
        config.as(RuleBaseConfiguration.KEY).setRuleBaseUpdateHandler( null );

        final KieModule kieModule = KieUtil.getKieModuleFromResources(KieUtil.generateReleaseId("test"), kieBaseTestConfiguration);
        InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.newKieBaseFromReleaseId(kieModule.getReleaseId(), config);
        KieSession session = kbase.newKieSession();

        AgendaEventListener ael = mock( AgendaEventListener.class );
        session.addEventListener( ael );

        for ( int i = 0; i < 5; i++ ) {
            session.insert( new Cheese() );
        }

        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_JBRULES_2206_1.drl").getKiePackages();
        kbase.addPackages(kpkgs);

        ((InternalAgenda) session.getAgenda()).evaluateEagerList();

        // two matching rules were added, so 2 activations should have been created 
        verify( ael, times( 2 ) ).matchCreated(any(MatchCreatedEvent.class));
        int fireCount = session.fireAllRules();
        // both should have fired
        assertThat(fireCount).isEqualTo(2);

        kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_JBRULES_2206_2.drl").getKiePackages();
        kbase.addPackages(kpkgs);
        ((InternalAgenda) session.getAgenda()).evaluateEagerList();

        // one rule was overridden and should activate 
        verify( ael, times( 3 ) ).matchCreated(any(MatchCreatedEvent.class));
        fireCount = session.fireAllRules();
        // that rule should fire again
        assertThat(fireCount).isEqualTo(1);

        session.dispose();
    }

    public class SubvertedClassLoader extends URLClassLoader {

        private static final long serialVersionUID = 510l;

        public SubvertedClassLoader(final URL[] urls,
                                    final ClassLoader parentClassLoader) {
            super( urls,
                   parentClassLoader );
        }

        protected synchronized Class loadClass(String name,
                                               boolean resolve) throws ClassNotFoundException {
            // First, check if the class has already been loaded
            Class c = findLoadedClass( name );
            if ( c == null ) {
                try {
                    c = findClass( name );
                } catch ( ClassNotFoundException e ) {
                    c = super.loadClass( name,
                                         resolve );
                }
            }
            return c;
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testSegmentMerging(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String drl1 = "global java.util.List list\n" +
                      "rule R1 when\n" +
                      "  $s : String()\n" +
                      "  $i : Integer( this == $s.length() )\n" +
                      "  $j : Integer( this == $i * 2 )\n" +
                      "then\n" +
                      "  list.add( $j );\n" +
                      "end\n";

        String drl2 = "global java.util.List list\n" +
                      "rule R2 when\n" +
                      "  $s : String()\n" +
                      "  $i : Integer( this == $s.length() )\n" +
                      "  $j : Integer( this == $i * 3 )\n" +
                      "then\n" +
                      "  list.add( $j );\n" +
                      "end\n";

        InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration);

        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromKieModuleFromDrl("tmp", kieBaseTestConfiguration, drl1).getKiePackages();
        kbase.addPackages(kpkgs);

        KieSession ksession = kbase.newKieSession();
        List<Integer> list = new ArrayList<Integer>();
        ksession.setGlobal("list", list);

        ksession.insert("test");
        ksession.insert(4);
        ksession.insert(8);
        ksession.insert(12);

        ksession.fireAllRules();
        assertThat((int) list.get(0)).isEqualTo(8);
        list.clear();

        kpkgs = KieBaseUtil.getKieBaseFromKieModuleFromDrl("tmp", kieBaseTestConfiguration, drl2).getKiePackages();
        kbase.addPackages(kpkgs);

        kbase.removeRule("defaultpkg", "R1");

        ksession.fireAllRules();
        assertThat((int) list.get(0)).isEqualTo(12);
    }
}
