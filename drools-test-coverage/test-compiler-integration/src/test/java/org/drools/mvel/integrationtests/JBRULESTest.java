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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.drools.mvel.compiler.Bar;
import org.drools.mvel.compiler.Cheese;
import org.drools.mvel.compiler.FactA;
import org.drools.mvel.compiler.FactB;
import org.drools.mvel.compiler.Foo;
import org.drools.mvel.compiler.Message;
import org.drools.mvel.compiler.Person;
import org.drools.mvel.compiler.Primitives;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.Message.Level;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.mockito.ArgumentCaptor;
import org.mvel2.MVEL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


public class JBRULESTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJBRules2055(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieBase kbase = KieBaseUtil.getKieBaseFromClasspathResources(this.getClass(), kieBaseTestConfiguration, "test_JBRules2055.drl");
        KieSession ksession = kbase.newKieSession();
        final List<String> results = new ArrayList<String>();
        ksession.setGlobal("results", results);
        ksession.insert(new Cheese("stilton"));
        ksession.insert(new Cheese("brie"));
        ksession.insert(new Cheese("muzzarella"));
        ksession.insert(new Person("bob", "stilton"));
        ksession.fireAllRules();
        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get(0)).isEqualTo("stilton");
        assertThat(results.get(1)).isEqualTo("brie");

    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJBRules2369(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieBase kbase = KieBaseUtil.getKieBaseFromClasspathResources(this.getClass(), kieBaseTestConfiguration, "test_JBRules2369.drl");
        KieSession ksession = kbase.newKieSession();
        final List<String> results = new ArrayList<String>();
        ksession.setGlobal("results", results);

        final FactA a = new FactA();
        final FactB b = new FactB(Integer.valueOf(0));

        final FactHandle aHandle = ksession.insert(a);
        final FactHandle bHandle = ksession.insert(b);

        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(1);

        ksession.update(aHandle, a);

        ksession.fireAllRules();
        assertThat(results.size()).isEqualTo(2);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJBRules2140(KieBaseTestConfiguration kieBaseTestConfiguration) {
        KieBase kbase = KieBaseUtil.getKieBaseFromClasspathResources(this.getClass(), kieBaseTestConfiguration, "test_JBRules2140.drl");
        KieSession ksession = kbase.newKieSession();
        final List<String> results = new ArrayList<String>();
        ksession.setGlobal("results", results);
        ksession.fireAllRules();
        assertThat(results.size()).isEqualTo(2);
        assertThat(results.contains("java")).isTrue();
        assertThat(results.contains("mvel")).isTrue();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJBRULES_2995(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String str = "package org.drools.mvel.compiler\n" +
                "rule r1\n" +
                "when\n" +
                "    Primitives( classAttr == java.lang.String.class, \n" +
                "                eval(classAttr.equals( java.lang.String.class ) ),\n" +
                "                classAttr == String.class )\n" +
                "then\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();
        final Primitives primitives = new Primitives();
        primitives.setClassAttr(String.class);
        ksession.insert(primitives);
        final int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(1);
    }

    @DisabledIfSystemProperty(named = "drools.drl.antlr4.parser.enabled", matches = "true")
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJBRULES2872(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String str = "package org.drools.mvel.compiler.test\n" +
                "import org.drools.mvel.compiler.FactA\n" +
                "rule X\n" +
                "when\n" +
                "    FactA( enumVal == TestEnum.ONE || == TestEnum.TWO )\n" +
                "then\n" +
                "end\n";

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromDrls(kieBaseTestConfiguration, false, str);
        List<org.kie.api.builder.Message> errors = kieBuilder.getResults().getMessages(Level.ERROR);
        assertThat(errors.size()).isEqualTo(1);
        final org.kie.api.builder.Message error = errors.get(0);
        assertThat(error.getLine()).isEqualTo(5);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJBRULES3030(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String str = "package org.drools.mvel.compiler\n" +
                "rule X\n" +
                "when\n" +
                "    $gp : GrandParent()" +
                "    $ch : ChildHolder( child == $gp )\n" +
                "then\n" +
                "end\n";

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromDrls(kieBaseTestConfiguration, false, str);
        List<org.kie.api.builder.Message> errors = kieBuilder.getResults().getMessages(Level.ERROR);
        assertThat(!errors.isEmpty()).isFalse();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJBRULES3111(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String str = "package org.drools.compiler\n" +
                "declare Bool123\n" +
                "    bool1 : boolean\n" +
                "    bool2 : boolean\n" +
                "    bool3 : boolean\n" +
                "end\n" +
                "declare Thing\n" +
                "    name : String\n" +
                "    bool123 : Bool123\n" +
                "end\n" +
                "rule kickOff\n" +
                "when\n" +
                "then\n" +
                "    insert( new Thing( \"one\", new Bool123( true, false, false ) ) );\n" +
                "    insert( new Thing( \"two\", new Bool123( false, false, false ) ) );\n" +
                "    insert( new Thing( \"three\", new Bool123( false, false, false ) ) );\n" +
                "end\n" +
                "rule r1\n" +
                "when\n" +
                "    $t: Thing( bool123.bool1 == true )\n" +
                "then\n" +
                "end\n" +
                "rule r2\n" +
                "when\n" +
                "    $t: Thing( bool123.bool2 == true )\n" +
                "then\n" +
                "end\n" +
                "rule r3\n" +
                "when\n" +
                "    $t: Thing( bool123.bool3 == true )\n" +
                "then\n" +
                "end";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();

        final org.kie.api.event.rule.AgendaEventListener ael = mock(org.kie.api.event.rule.AgendaEventListener.class);
        ksession.addEventListener(ael);

        final int rulesFired = ksession.fireAllRules();
        assertThat(rulesFired).isEqualTo(2);

        final ArgumentCaptor<AfterMatchFiredEvent> captor = ArgumentCaptor.forClass(org.kie.api.event.rule.AfterMatchFiredEvent.class);
        verify(ael, times(2)).afterMatchFired(captor.capture());
        final List<org.kie.api.event.rule.AfterMatchFiredEvent> aafe = captor.getAllValues();

        assertThat(aafe.get(0).getMatch().getRule().getName()).isEqualTo("kickOff");
        assertThat(aafe.get(1).getMatch().getRule().getName()).isEqualTo("r1");

        final Object value = aafe.get(1).getMatch().getDeclarationValue("$t");
        final String name = (String) MVEL.eval("$t.name", Collections.singletonMap("$t", value));

        assertThat(name).isEqualTo("one");
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJBRULES3323(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {

        //adding rules. it is important to add both since they reciprocate
        final StringBuilder rule = new StringBuilder();
        rule.append( "package de.orbitx.accumulatetesettest;\n" );
        rule.append( "import java.util.Set;\n" );
        rule.append( "import java.util.HashSet;\n" );
        rule.append( "import org.drools.mvel.compiler.Foo;\n" );
        rule.append( "import org.drools.mvel.compiler.Bar;\n" );

        rule.append( "rule \"Sub optimal foo parallelism - this rule is causing NPE upon reverse\"\n" );
        rule.append( "when\n" );
        rule.append( "$foo : Foo($leftId : id, $leftBar : bar != null)\n" );
        rule.append( "$fooSet : Set()\n" );
        rule.append( "from accumulate ( Foo(id > $leftId, bar != null && bar != $leftBar, $bar : bar),\n" );
        rule.append( "collectSet( $bar ) )\n" );
        rule.append( "then\n" );
        rule.append( "//System.out.println(\"ok\");\n" );
        rule.append( "end\n" );

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule.toString());
        KieSession ksession = kbase.newKieSession();

        //adding test data
        final Bar[] barList = new Bar[3];
        for (int i = 0; i < barList.length; i++) {
            barList[i] = new Bar(String.valueOf(i));
        }

        final Foo[] fooList = new Foo[4];
        for (int i = 0; i < fooList.length; i++) {
            fooList[i] = new Foo(String.valueOf(i), i == 3 ? barList[2] : barList[i]);
        }

        for (final Foo foo : fooList) {
            ksession.insert(foo);
        }

        //the NPE is caused by exactly this sequence. of course there are more sequences but this
        //appears to be the most short one
        final int[] magicFoos = new int[]{3, 3, 1, 1, 0, 0, 2, 2, 1, 1, 0, 0, 3, 3, 2, 2, 3, 1, 1};
        final int[] magicBars = new int[]{1, 2, 0, 1, 1, 0, 1, 2, 2, 1, 2, 0, 0, 2, 0, 2, 0, 0, 1};

        //upon final rule firing an NPE will be thrown in org.drools.core.rule.Accumulate
        for (int i = 0; i < magicFoos.length; i++) {
            final Foo tehFoo = fooList[magicFoos[i]];
            final FactHandle fooFactHandle = ksession.getFactHandle(tehFoo);
            tehFoo.setBar(barList[magicBars[i]]);
            ksession.update(fooFactHandle, tehFoo);
            int fired = ksession.fireAllRules();
            assertThat(fired > 0).isTrue(); // it's fine if it doesn't throw NPE
        }
        ksession.dispose();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJBRULES3326(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        final StringBuilder rule = new StringBuilder();
        rule.append("package org.drools.mvel.compiler\n");
        rule.append("rule X\n");
        rule.append("when\n");
        rule.append("    Message(!!!false)\n");
        rule.append("then\n");
        rule.append("end\n");

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule.toString());
        KieSession ksession = kbase.newKieSession();

        ksession.insert(new Message("test"));
        final int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(1);
        ksession.dispose();
    }

}
