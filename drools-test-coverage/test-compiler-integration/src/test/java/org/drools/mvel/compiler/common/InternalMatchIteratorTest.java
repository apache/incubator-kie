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
package org.drools.mvel.compiler.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.event.TrackingAgendaEventListener;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.base.util.Iterator;
import org.drools.serialization.protobuf.iterators.ActivationIterator;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.definition.rule.Rule;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.internal.runtime.conf.ForceEagerActivationFilter;
import org.kie.internal.runtime.conf.ForceEagerActivationOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class InternalMatchIteratorTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testSingleLian(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str = "package org.kie.test \n" +
                     "\n" +
                     "rule rule1 @Propagation(EAGER) when\n" +
                     "    $s : String( this != 'xx' )\n" +
                     "then\n" +
                     "end\n" +
                     "rule rule6 @Propagation(EAGER) when\n" +
                     "     java.util.Map()\n" +
                     "then\n" +
                     "end\n" +
                     "\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();

        for ( int i = 0; i < 5; i++ ) {
            ksession.insert( new String( "" + i ) );
        }

        evaluateEagerList(ksession);

        Iterator it = ActivationIterator.iterator( ksession );
        List list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s" ) + ":" + act.isQueued() );
        }
        assertContains( new String[]{"rule1:0:true", "rule1:1:true", "rule1:2:true", "rule1:3:true", "rule1:4:true"},
                        list );

        ksession.fireAllRules();

        it = ActivationIterator.iterator( ksession );
        list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s" ) + ":" + act.isQueued() );
        }
        assertContains( new String[]{"rule1:0:false", "rule1:1:false", "rule1:2:false", "rule1:3:false", "rule1:4:false"},
                        list );
    }

    private void evaluateEagerList(KieSession ksession) {
        ((InternalWorkingMemory) ksession).flushPropagations();
        ((InternalAgenda) ksession.getAgenda()).evaluateEagerList();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testLianPlusEvaln(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str = "package org.kie.test \n" +
                     "\n" +
                     "rule rule1 @Propagation(EAGER) when\n" +
                     "    $s : String( this != 'xx' )\n" +
                     "    eval( 1 == 1 ) \n" +
                     "then\n" +
                     "end\n" +
                     "rule rule6 @Propagation(EAGER) when\n" +
                     "     java.util.Map()\n" +
                     "then\n" +
                     "end\n" +
                     "\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();

        for ( int i = 0; i < 5; i++ ) {
            ksession.insert( new String( "" + i ) );
        }

        evaluateEagerList(ksession);

        Iterator it = ActivationIterator.iterator( ksession );
        List list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s" ) + ":" + act.isQueued() );
        }
        assertContains( new String[]{"rule1:0:true", "rule1:1:true", "rule1:2:true", "rule1:3:true", "rule1:4:true"},
                        list );

        ksession.fireAllRules();

        it = ActivationIterator.iterator( ksession );
        list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s" ) + ":" + act.isQueued() );
        }
        assertContains( new String[]{"rule1:0:false", "rule1:1:false", "rule1:2:false", "rule1:3:false", "rule1:4:false"},
                        list );
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testLianPlusEvalnWithSharing(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // Rule 0 single LiaNode
        // Rule 1 and 2 are shared
        // Rule 3 shares the LIANode with 1 and 2
        // Rule 4 Shares the eval with 3
        String str = "package org.kie.test \n" +
                     "\n" +
                     "rule rule0 @Propagation(EAGER) when\n" +
                     "    $s : String( this != 'xx' )\n" +
                     "then\n" +
                     "end\n" +
                     "rule rule1 @Propagation(EAGER) when\n" +
                     "    $s : String( this != 'xx' )\n" +
                     "    eval( Integer.parseInt( $s ) <= 2 ) \n" +
                     "then\n" +
                     "end\n" +
                     "rule rule2 @Propagation(EAGER) when\n" +
                     "    $s : String( this != 'xx' )\n" +
                     "    eval( Integer.parseInt( $s ) <= 2 ) \n" +
                     "then\n" +
                     "end\n" +
                     "rule rule3 @Propagation(EAGER) when\n" +
                     "    $s : String( this != 'xx' )\n" +
                     "    eval( Integer.parseInt( $s ) > 2 ) \n" +
                     "then\n" +
                     "end\n" +
                     "rule rule4 @Propagation(EAGER) when\n" +
                     "    $s : String( this != 'xx' )\n" +
                     "    eval( Integer.parseInt( $s ) > 2 ) \n" +
                     "    eval( Integer.parseInt( $s ) > 3 ) \n" +
                     "then\n" +
                     "end\n" +
                     "rule rule5 @Propagation(EAGER) when\n" +
                     "    $s : String( this != 'xx' )\n" +
                     "    eval( Integer.parseInt( $s ) > 2 ) \n" +
                     "    eval( Integer.parseInt( $s ) > 3 ) \n" +
                     "then\n" +
                     "end\n" +
                     "rule rule6 @Propagation(EAGER) when\n" +
                     "     java.util.Map()\n" +
                     "then\n" +
                     "end\n" +
                     "\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();

        for ( int i = 0; i < 5; i++ ) {
            ksession.insert( new String( "" + i ) );
        }

        evaluateEagerList(ksession);

        Iterator it = ActivationIterator.iterator( ksession );

        List list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s" ) + ":" + act.isQueued() );
        }
        assertContains( new String[]{"rule0:0:true", "rule0:1:true", "rule0:2:true", "rule0:3:true", "rule0:4:true",
                                "rule1:0:true", "rule1:1:true", "rule1:2:true", "rule2:0:true", "rule2:1:true", "rule2:2:true",
                                "rule3:3:true", "rule3:4:true",
                                "rule4:4:true", "rule5:4:true"},
                        list );

        ksession.fireAllRules();

        it = ActivationIterator.iterator( ksession );
        list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s" ) + ":" + act.isQueued() );
        }
        assertContains( new String[]{"rule0:0:false", "rule0:1:false", "rule0:2:false", "rule0:3:false", "rule0:4:false",
                                "rule1:0:false", "rule1:1:false", "rule1:2:false", "rule2:0:false", "rule2:1:false", "rule2:2:false",
                                "rule3:3:false", "rule3:4:false",
                                "rule4:4:false", "rule5:4:false"},
                        list );
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testLianPlusEvalnWithSharingWithMixedDormantAndActive(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // Rule 0 single LiaNode
        // Rule 1 and 2 are shared
        // Rule 3 shares the LIANode with 1 and 2
        // Rule 4 Shares the eval with 3
        String str = "package org.kie.test \n" +
                     "\n" +
                     "rule rule2 salience ( Integer.parseInt('1'+$s)) when\n" +
                     "    $s : String( this != 'xx' )\n" +
                     "    eval( Integer.parseInt( $s ) <= 2 ) \n" +
                     "then\n" +
                     "    kcontext.getKieRuntime().halt();\n" +
                     "end\n" +
                     "rule rule0 salience ( Integer.parseInt('1'+$s) ) when\n" +
                     "    $s : String( this != 'xx' )\n" +
                     "then\n" +
                     "end\n" +
                     "rule rule1 salience ( Integer.parseInt('1'+$s)) when\n" +
                     "    $s : String( this != 'xx' )\n" +
                     "    eval( Integer.parseInt( $s ) <= 2 ) \n" +
                     "then\n" +
                     "end\n" +
                     "rule rule3 salience ( Integer.parseInt('1'+$s)) when\n" +
                     "    $s : String( this != 'xx' )\n" +
                     "    eval( Integer.parseInt( $s ) > 2 ) \n" +
                     "then\n" +
                     "end\n" +
                     "rule rule4 salience ( Integer.parseInt('1'+$s) ) when\n" +
                     "    $s : String( this != 'xx' )\n" +
                     "    eval( Integer.parseInt( $s ) > 2 ) \n" +
                     "    eval( Integer.parseInt( $s ) > 3 ) \n" +
                     "then\n" +
                     "end\n" +
                     "\n" +
                     "rule rule6 when\n" +
                     "     java.util.Map()\n" +
                     "then\n" +
                     "end\n" +
                     "\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();

        for ( int i = 0; i < 5; i++ ) {
            ksession.insert( new String( "" + i ) );
        }
        ksession.fireAllRules();

        Iterator it = ActivationIterator.iterator( ksession );

        List list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s" ) + ":" + act.isQueued() );
        }
        assertContains( new String[]{"rule0:0:true", "rule0:1:true", "rule0:2:true", "rule0:3:false", "rule0:4:false",
                                "rule1:0:true", "rule1:1:true", "rule1:2:true", "rule2:0:true", "rule2:1:true", "rule2:2:false",
                                "rule3:3:false", "rule3:4:false",
                                "rule3:4:false"},
                        list );
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testSingleJoinNode(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str = "package org.kie.test \n" +
                     "\n" +
                     "rule rule1  @Propagation(EAGER)  when\n" +
                     "    $s1 : String( )\n" +
                     "    $s2 : String( )\n" +
                     "then\n" +
                     "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();

        for ( int i = 0; i < 2; i++ ) {
            ksession.insert( new String( "" + i ) );
        }
        evaluateEagerList(ksession);

        Iterator it = ActivationIterator.iterator( ksession );
        List list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.getDeclarationValue( "$s2" ) + ":" + act.isQueued() );
        }
        assertContains( new String[]{"rule1:0:1:true", "rule1:1:0:true", "rule1:1:1:true", "rule1:0:0:true"},
                        list );


        ksession.fireAllRules();
        it = ActivationIterator.iterator( ksession );
        list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.getDeclarationValue( "$s2" ) + ":" + act.isQueued() );
        }
        assertContains( new String[]{"rule1:0:1:false", "rule1:1:0:false", "rule1:1:1:false", "rule1:0:0:false"},
                        list );
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testSingleJoinNodePlusEvaln(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str = "package org.kie.test \n" +
                     "\n" +
                     "rule rule1 @Propagation(EAGER) when\n" +
                     "    $s1 : String( )\n" +
                     "    $s2 : String( )\n" +
                     "    eval( 1 == 1 ) \n" +
                     "then\n" +
                     "end\n" +
                     "\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();

        for ( int i = 0; i < 2; i++ ) {
            ksession.insert( new String( "" + i ) );
        }
        evaluateEagerList(ksession);

        Iterator it = ActivationIterator.iterator( ksession );
        List list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.getDeclarationValue( "$s2" ) + ":" + act.isQueued() );
        }
        assertContains( new String[]{"rule1:0:1:true", "rule1:1:0:true", "rule1:1:1:true", "rule1:0:0:true"},
                        list );

        ksession.fireAllRules();

        it = ActivationIterator.iterator( ksession );
        list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.getDeclarationValue( "$s2" ) + ":" + act.isQueued() );
        }
        assertContains( new String[]{"rule1:0:1:false", "rule1:1:0:false", "rule1:1:1:false", "rule1:0:0:false"},
                        list );
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testSingleJoinNodePlusEvalnWithSharing(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str = "package org.kie.test \n" +
                     "\n" +
                     "rule rule1  @Propagation(EAGER)  when\n" +
                     "    $s1 : String( )\n" +
                     "    $s2 : String( )\n" +
                     "    eval( 1 == 1 ) \n" +
                     "then\n" +
                     "end\n" +
                     "rule rule2  @Propagation(EAGER)  when\n" +
                     "    $s1 : String( )\n" +
                     "then\n" +
                     "end\n" +
                     "rule rule3  @Propagation(EAGER)  when\n" +
                     "    $s1 : String( )\n" +
                     "    $s2 : String( )\n" +
                     "    $s3 : String( )\n" +
                     "    eval( 1 == 1 ) \n" +
                     "then\n" +
                     "end\n" +
                     "\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();

        for ( int i = 0; i < 2; i++ ) {
            ksession.insert( new String( "" + i ) );
        }
        evaluateEagerList(ksession);

        Iterator it = ActivationIterator.iterator( ksession );
        List list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            if ( act.getRule().getName().equals( "rule3" ) ) {
                list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.getDeclarationValue( "$s2" ) + ":" + act.getDeclarationValue( "$s3" ) + ":" + act.isQueued() );
            } else if ( act.getRule().getName().equals( "rule1" ) ) {
                list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.getDeclarationValue( "$s2" ) + ":" + act.isQueued() );
            } else if ( act.getRule().getName().equals( "rule2" ) ) {
                list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.isQueued() );
            }
        }

        assertContains( new String[]{"rule1:0:0:true", "rule1:0:1:true", "rule1:1:0:true", "rule1:1:1:true",
                                "rule2:1:true", "rule2:0:true",
                                "rule3:0:0:0:true", "rule3:0:0:1:true", "rule3:1:0:0:true", "rule3:1:0:1:true", "rule3:0:1:0:true", "rule3:0:1:1:true", "rule3:1:1:0:true", "rule3:1:1:1:true"},
                        list );

        ksession.fireAllRules();

        it = ActivationIterator.iterator( ksession );
        list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            if ( act.getRule().getName().equals( "rule3" ) ) {
                list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.getDeclarationValue( "$s2" ) + ":" + act.getDeclarationValue( "$s3" ) + ":" + act.isQueued() );
            } else if ( act.getRule().getName().equals( "rule1" ) ) {
                list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.getDeclarationValue( "$s2" ) + ":" + act.isQueued() );
            } else if ( act.getRule().getName().equals( "rule2" ) ) {
                list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.isQueued() );
            }
        }
        assertContains( new String[]{"rule1:0:0:false", "rule1:0:1:false", "rule1:1:0:false", "rule1:1:1:false",
                                "rule2:1:false", "rule2:0:false",
                                "rule3:0:0:0:false", "rule3:0:0:1:false", "rule3:1:0:0:false", "rule3:1:0:1:false", "rule3:0:1:0:false", "rule3:0:1:1:false", "rule3:1:1:0:false", "rule3:1:1:1:false"},
                        list );
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testSingleJoinNodePlusEvalnWithSharingWithMixedDormantAndActive(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str = "package org.kie.test \n" +
                     "\n" +
                     "rule rule1  salience ( Integer.parseInt( '1'+$s1+'0'+$s2 ) ) when\n" +
                     "    $s1 : String( )\n" +
                     "    $s2 : String( )\n" +
                     "    eval( 1 == 1 ) \n" +
                     "then\n" +
                     "end\n" +
                     "rule rule2 salience (1020 + Integer.parseInt( $s1 ) ) when\n" +
                     "    $s1 : String( )\n" +
                     "then\n" +
                     "    kcontext.getKieRuntime().halt();\n" +
                     "end\n" +
                     "rule rule3  salience ( Integer.parseInt( '1'+$s1+'1'+$s2  ) ) when\n" +
                     "    $s1 : String( )\n" +
                     "    $s2 : String( )\n" +
                     "    $s3 : String( )\n" +
                     "    eval( 1 == 1 ) \n" +
                     "then\n" +
                     "end\n" +
                     "\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();

        for ( int i = 0; i < 2; i++ ) {
            ksession.insert( new String( "" + i ) );
        }

        ksession.fireAllRules();

        Iterator it = ActivationIterator.iterator( ksession );
        List list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            if ( act.getRule().getName().equals( "rule3" ) ) {
                list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.getDeclarationValue( "$s2" ) + ":" + act.getDeclarationValue( "$s3" ) + ":" + act.isQueued() );
            } else if ( act.getRule().getName().equals( "rule1" ) ) {
                list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.getDeclarationValue( "$s2" ) + ":" + act.isQueued() );
            } else if ( act.getRule().getName().equals( "rule2" ) ) {
                list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.isQueued() );
            }
        }

        assertContains( new String[]{"rule1:0:0:true", "rule1:0:1:true", "rule1:1:0:false", "rule1:1:1:false",
                                "rule2:1:false", "rule2:0:true",
                                "rule3:0:0:0:true", "rule3:0:0:1:true", "rule3:1:0:0:false", "rule3:1:0:1:false", "rule3:0:1:0:true", "rule3:0:1:1:true", "rule3:1:1:0:false", "rule3:1:1:1:false"},
                        list );
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testNotSharingWithMixedDormantAndActive(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str = "package org.kie.test \n" +
                     "\n" +
                     "rule rule1  @Propagation(EAGER)  salience 10 when\n" +
                     "    not String( this == '1' )\n" +
                     "then\n" +
                     "end\n" +
                     "rule rule2   @Propagation(EAGER)  salience ( Integer.parseInt( $s1+'1' ) ) when\n" +
                     "    not String( this == '1' )\n" +
                     "    $s1 : String( )\n" +
                     "    eval( 1 == 1 ) \n" +
                     "then\n" +
                     "end\n" +
                     "rule rule3  @Propagation(EAGER)  salience ( Integer.parseInt( $s1+'2' ) ) when\n" +
                     "    $s1 : String( )\n" +
                     "    not String( this == '1' )\n" +
                     "    eval( 1 == 1 ) \n" +
                     "    eval( 1 == 1 ) \n" +
                     "then\n" +
                     "    kcontext.getKieRuntime().halt();\n" +
                     "end\n" +
                     "\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();

        ksession.insert( "0" );
        ksession.insert( "2" );

        ksession.fireAllRules();

        Iterator it = ActivationIterator.iterator( ksession );
        List list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            if ( act.getRule().getName().equals( "rule3" ) ) {
                list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.isQueued() );
            } else if ( act.getRule().getName().equals( "rule1" ) ) {
                list.add( act.getRule().getName() + ":" + act.isQueued() );
            } else if ( act.getRule().getName().equals( "rule2" ) ) {
                list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.isQueued() );
            }
        }

        assertContains( new String[]{"rule1:true", "rule2:0:true", "rule2:2:true", "rule3:0:true", "rule3:2:false"},
                        list );
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testExistsSharingWithMixedDormantAndActive(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str = "package org.kie.test \n" +
                     "\n" +
                     "rule rule3  @Propagation(EAGER)  salience ( Integer.parseInt( $s1+'1' ) ) when\n" +
                     "    $s1 : String( )\n" +
                     "    exists String( this == '1' )\n" +
                     "    eval( 1 == 1 ) \n" +
                     "    eval( 1 == 1 ) \n" +
                     "then\n" +
                     "    kcontext.getKieRuntime().halt();\n" +
                     "end\n" +
                     "rule rule1  @Propagation(EAGER)  salience 100 when\n" +
                     "    exists String( this == '1' )\n" +
                     "then\n" +
                     "end\n" +
                     "rule rule2   @Propagation(EAGER)  salience ( Integer.parseInt( $s1+'1' ) ) when\n" +
                     "    exists String( this == '1' )\n" +
                     "    $s1 : String( )\n" +
                     "    eval( 1 == 1 ) \n" +
                     "then\n" +
                     "end\n" +
                     "\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();

        ksession.insert( "0" );
        ksession.insert( "1" );
        ksession.insert( "2" );

        ksession.fireAllRules();

        Iterator it = ActivationIterator.iterator( ksession );
        List list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            if ( act.getRule().getName().equals( "rule3" ) ) {
                list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.isQueued() );
            } else if ( act.getRule().getName().equals( "rule1" ) ) {
                list.add( act.getRule().getName() + ":" + act.isQueued() );
            } else if ( act.getRule().getName().equals( "rule2" ) ) {
                list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.isQueued() );
            }
        }

        assertContains( new String[]{"rule1:false", "rule2:0:true", "rule2:1:true", "rule2:2:true", "rule3:2:false"},
                        list );
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testFromnSharingWithMixedDormantAndActive(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str = "package org.kie.test \n" +
                     "global java.util.List list \n" +
                     "\n" +
                     "rule rule3 salience ( Integer.parseInt( $s1+'1' ) ) when\n" +
                     "    $s1 : String( ) from list  \n" +
                     "    eval( 1 == 1 ) \n" +
                     "    eval( 1 == 1 ) \n" +
                     "then\n" +
                     "    kcontext.getKieRuntime().halt();\n" +
                     "end\n" +
                     "rule rule1 salience ( Integer.parseInt( $s1+'1' ) )  when\n" +
                     "    $s1 : String( this == '1' )  from list\n" +
                     "then\n" +
                     "end\n" +
                     "rule rule2  salience ( Integer.parseInt( $s1+'1' ) ) when\n" +
                     "    $s1 : String( )  from list \n" +
                     "    eval( 1 == 1 ) \n" +
                     "then\n" +
                     "end\n" +
                     "\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();

        List list = new ArrayList();
        list.add( "0" );
        list.add( "1" );
        list.add( "2" );

        ksession.setGlobal( "list",
                            list );

        ksession.fireAllRules();

        Iterator it = ActivationIterator.iterator( ksession );
        list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.isQueued() );
        }

        assertContains(new String[]{"rule1:1:true", "rule2:0:true", "rule2:1:true", "rule2:2:true", "rule3:0:true", "rule3:1:true", "rule3:2:false"},
                       list);
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testAccnSharingWithMixedDormantAndActive(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str = "package org.kie.test \n" +
                     "\n" +
                     "rule rule1 @Propagation(EAGER) when\n" +
                     "    $s1 : Integer() from accumulate( $i : Integer(), sum ( $i ) )    " +
                     "then\n" +
                     "end\n" +
                     "rule rule2 @Propagation(EAGER) when\n" +
                     "    $s1 : Integer() from accumulate( $i : Integer(), sum ( $i ) )    " +
                     "    eval( 1 == 1 ) \n" +
                     "then\n" +
                     "end\n" +
                     "rule rule3  salience 10 when\n" +
                     "    eval( 1 == 1 ) \n" +
                     "    $s1 : Integer() from accumulate( $i : Integer(), sum ( $i ) )    " +
                     "    eval( 1 == 1 ) \n" +
                     "then\n" +
                     "    kcontext.getKieRuntime().halt();\n" +
                     "end\n" +
                     "\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();

        ksession.insert( 1 );
        ksession.insert( 2 );
        ksession.insert( 3 );

        ksession.fireAllRules();

        Iterator it = ActivationIterator.iterator( ksession );
        List list = new ArrayList();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            list.add( act.getRule().getName() + ":" + act.getDeclarationValue( "$s1" ) + ":" + act.isQueued() );
        }

        assertContains( new String[]{"rule1:6:true", "rule2:6:true", "rule3:6:false"},
                        list );
    }

    public void assertContains(Object[] objects,
                               List list) {
        for ( Object object : objects ) {
            if ( !list.contains( object ) ) {
                fail( "does not contain:" + object );
            }
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    @Timeout(10000)
    public void testEagerEvaluation(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        String str =
                "package org.simple \n" +
                "rule xxx @Propagation(EAGER) \n" +
                "when \n" +
                "  $s : String()\n" +
                "then \n" +
                "end  \n" +
                "rule yyy @Propagation(EAGER) \n" +
                "when \n" +
                "  $s : String()\n" +
                "then \n" +
                "end  \n";

        KieSessionConfiguration conf = RuleBaseFactory.newKnowledgeSessionConfiguration();
        conf.setOption( ForceEagerActivationOption.YES );

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession( conf, null );

        TrackingAgendaEventListener listener = new TrackingAgendaEventListener();
        ksession.addEventListener(listener);

        ksession.insert("test");

        assertThat(listener.getMatchCreated()).hasSize(2);
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    @Timeout(10000)
    public void testFilteredEagerEvaluation(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        String str =
                "package org.simple \n" +
                "rule xxx @Propagation(EAGER) \n" +
                "when \n" +
                "  $s : String()\n" +
                "then \n" +
                "end  \n" +
                "rule yyy @Propagation(EAGER) \n" +
                "when \n" +
                "  $s : String()\n" +
                "then \n" +
                "end  \n";

        KieSessionConfiguration conf = RuleBaseFactory.newKnowledgeSessionConfiguration();
        conf.setOption( new ForceEagerActivationOption.FILTERED( new ForceEagerActivationFilter() {
            @Override
            public boolean accept(Rule rule) {
                return rule.getName().equals("xxx");
            }
        }));

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession( conf, null );

        TrackingAgendaEventListener listener = new TrackingAgendaEventListener();
        ksession.addEventListener(listener);

        ksession.insert("test");
        ((InternalWorkingMemory) ksession).flushPropagations();

        assertThat(listener.getMatchCreated()).hasSize(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testCollectAndCountActivationsWithNodesSharing(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // KIE-1062
        String str =
                "import " + CartLineDetails.class.getCanonicalName() + ";" +
                        "\n" +
                        "rule R1 when\n" +
                        "        exists($cartLineDetails1 : CartLineDetails(cartLineProductId in (\"Product1\" ) &&\n" +
                        "                    cartLineProductCategoryId == \"Category1\"))\n" +
                        "        Number(doubleValue() > 1) from\n" +
                        "            accumulate ( CartLineDetails(cartLineProductId in (\"Product1\" , \"NotUsedProduct\" ) &&\n" +
                        "                            $qty : cartLineItemQuantity != null), sum($qty) )\n" +
                        "        Number(doubleValue() > 1) from\n" +
                        "            accumulate ( CartLineDetails(cartLineProductId in (\"Product2\" ) &&\n" +
                        "                            $qty : cartLineItemQuantity != null), sum($qty) )\n" +
                        "        Boolean(this == true)\n" +
                        "        $cartLineDetails4 : CartLineDetails(cartLineProductCategoryId == \"Category1\")\n" +
                        "    then\n" +
                        "end\n" +
                        "\n" +
                        "rule R2 when\n" +
                        "        exists($cartLineDetails1 : CartLineDetails(cartLineProductId in (\"Product1\" ) &&\n" +
                        "                    cartLineProductCategoryId == \"Category1\"))\n" +
                        "        Number(doubleValue() > 1) from\n" +
                        "            accumulate ( CartLineDetails(cartLineProductId in (\"Product1\" , \"NotUsedProduct\" ) &&\n" +
                        "                        $qty : cartLineItemQuantity != null), sum($qty) )\n" +
                        "        Number(doubleValue() > 1) from\n" +
                        "            accumulate ( CartLineDetails(cartLineProductId in (\"Product2\" ) &&\n" +
                        "                            $qty : cartLineItemQuantity != null), sum($qty) )\n" +
                        "        Boolean(this == false)\n" +
                        "        $cartLineDetails4 : CartLineDetails(cartLineProductCategoryId == \"Category1\")\n" +
                        "    then\n" +
                        "end";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession kieSession = kbase.newKieSession();

        int totalMatchingLineItems = 4;
        List<CartLineDetails> cartLineDetails = generateCartDetails(totalMatchingLineItems);

        for (CartLineDetails lineItem : cartLineDetails) {
            kieSession.insert(lineItem);
        }
        kieSession.insert(true);

        kieSession.fireAllRules();

        Iterator it = ActivationIterator.iterator( kieSession );
        List<InternalMatch> matches = new ArrayList<>();
        for (InternalMatch act = (InternalMatch) it.next(); act != null; act = (InternalMatch) it.next() ) {
            matches.add( act );
        }

        assertThat(matches.size()).isEqualTo(totalMatchingLineItems);
    }

    private List<CartLineDetails> generateCartDetails(int totalMatchingLineItems) {
        List<CartLineDetails> lineItemList = new ArrayList<>();
        for(int i = 1 ; i <= totalMatchingLineItems; i++){
            CartLineDetails lineItem = new CartLineDetails();
            lineItem.setCartLineProductId("Product" + i);
            lineItem.setCartLineProductCategoryId("Category" + "1");
            lineItem.setCartLineItemQuantity(10.0);
            lineItemList.add(lineItem);
        }
        return lineItemList;
    }

    public static class CartLineDetails implements Serializable {
        private static final long serialVersionUID = 1L;
        private String cartLineProductCategoryId;
        private Double cartLineItemQuantity;
        private String cartLineProductId;

        public String getCartLineProductCategoryId(){
            return cartLineProductCategoryId;
        }

        public void setCartLineProductCategoryId( String cartLineProductCategoryId ){
            this.cartLineProductCategoryId = cartLineProductCategoryId;
        }

        public Double getCartLineItemQuantity(){
            return cartLineItemQuantity;
        }

        public void setCartLineItemQuantity( Double cartLineItemQuantity ){
            this.cartLineItemQuantity = cartLineItemQuantity;
        }

        public String getCartLineProductId(){
            return cartLineProductId;
        }

        public void setCartLineProductId( String cartLineProductId ){
            this.cartLineProductId = cartLineProductId;
        }
    }
}
