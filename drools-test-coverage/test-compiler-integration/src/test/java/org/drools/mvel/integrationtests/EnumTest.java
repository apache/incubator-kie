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

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.mvel.compiler.Triangle;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.Message;
import org.kie.api.definition.KiePackage;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for declared Enums
 */
public class EnumTest {

    @org.kie.api.definition.type.PropertyReactive
    public static class FieldHolder {
        public String name;
        public StringBuilder[] items;

        public StringBuilder[] getItems() {
            return items;
        }

        public void setItems(StringBuilder[] items) {
            this.items = items;
        }
    }

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testEnums(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        KieBase kbase = KieBaseUtil.getKieBaseFromClasspathResources(getClass(), kieBaseTestConfiguration, "test_Enums.drl");
        KieSession ksession = kbase.newKieSession();
        final java.util.List list = new java.util.ArrayList();
        ksession.setGlobal( "list", list );

        ksession.fireAllRules();

        assertThat(list.contains(4)).isTrue();
        assertThat(list.contains(5.976e+24)).isTrue();
        assertThat(list.contains("Mercury")).isTrue();
        assertThat(list.contains("TUESDAY")).isTrue();
        assertThat(list.contains(true)).isTrue();
        assertThat(list.contains("MONDAY")).isTrue();

        ksession.dispose();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testIndexedEnumValuesInConsequence(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl =
                "package org.test;\n" +
                "global java.util.List list;\n" +
                "declare enum Color\n" +
                "    RED, GREEN, BLUE;\n" +
                "end\n" +
                "rule R when\n" +
                "then\n" +
                "    list.add( Color.values()[1].name() );\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, drl);
        KieSession ksession = kbase.newKieSession();
        final List<Object> list = new java.util.ArrayList<>();
        ksession.setGlobal("list", list);

        ksession.fireAllRules();

        assertThat(list)
                .as("values()[1].name() must evaluate like Java, on every KieBase configuration")
                .containsExactly("GREEN");
        ksession.dispose();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testIndexedEnumValuesStatementBeforeUpdateOfPropertyReactiveFact(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl =
                "package org.test;\n" +
                "global java.util.List list;\n" +
                "declare enum Color\n" +
                "    RED, GREEN, BLUE;\n" +
                "end\n" +
                "declare P\n" +
                "    @propertyReactive\n" +
                "    name : String\n" +
                "end\n" +
                "rule Init when\n" +
                "then\n" +
                "    insert( new P() );\n" +
                "end\n" +
                "rule R no-loop when\n" +
                "    $p : P()\n" +
                "then\n" +
                "    Color.values()[1].name();\n" +
                "    update( $p );\n" +
                "    list.add( \"fired\" );\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, drl);
        KieSession ksession = kbase.newKieSession();
        final List<Object> list = new java.util.ArrayList<>();
        ksession.setGlobal("list", list);

        ksession.fireAllRules();

        assertThat(list)
                .as("a standalone values()[1].name() before update() of a property-reactive fact must not break rule compilation")
                .containsExactly("fired");
        ksession.dispose();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testIndexedCallRootedAtUpdatedFactBeforeUpdateOfPropertyReactiveFact(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl =
                "package org.test;\n" +
                "global java.util.List list;\n" +
                "declare P\n" +
                "    @propertyReactive\n" +
                "    items : String[]\n" +
                "end\n" +
                "rule Init when\n" +
                "then\n" +
                "    P p = new P();\n" +
                "    p.setItems( new String[] { \"a\" } );\n" +
                "    insert( p );\n" +
                "end\n" +
                "rule R no-loop when\n" +
                "    $p : P()\n" +
                "then\n" +
                "    $p.getItems()[0].length();\n" +
                "    update( $p );\n" +
                "    list.add( \"fired\" );\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, drl);
        KieSession ksession = kbase.newKieSession();
        final List<Object> list = new java.util.ArrayList<>();
        ksession.setGlobal("list", list);

        ksession.fireAllRules();

        assertThat(list)
                .as("an indexed call rooted at the updated fact must not break rule compilation")
                .containsExactly("fired");
        ksession.dispose();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testCastEnclosedIndexedCallRootedAtUpdatedFactWidensMask(KieBaseTestConfiguration kieBaseTestConfiguration) {
        Assumptions.assumeTrue(kieBaseTestConfiguration.isExecutableModel());
        final String drl =
                "package org.test;\n" +
                "global java.util.List list;\n" +
                "declare P\n" +
                "    @propertyReactive\n" +
                "    name  : String\n" +
                "    items : StringBuilder[]\n" +
                "end\n" +
                "rule Init salience 100 when\n" +
                "then\n" +
                "    P p = new P();\n" +
                "    p.setItems( new StringBuilder[] { new StringBuilder() } );\n" +
                "    insert( p );\n" +
                "end\n" +
                "rule ListenOnItems salience 50 when\n" +
                "    P( items != null )\n" +
                "then\n" +
                "    list.add( \"items\" );\n" +
                "end\n" +
                "rule Modify no-loop salience 0 when\n" +
                "    $p : P()\n" +
                "then\n" +
                "    ((P) $p).getItems()[0].append( \"x\" );\n" +
                "    $p.setName( \"n\" );\n" +
                "    update( $p );\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, drl);
        KieSession ksession = kbase.newKieSession();
        final List<Object> list = new java.util.ArrayList<>();
        ksession.setGlobal("list", list);

        ksession.fireAllRules();

        assertThat(list)
                .as("an indexed call rooted at the updated fact through a cast/parentheses must widen the "
                        + "update mask, so the rule listening on 'items' is re-evaluated by update()")
                .containsExactly("items", "items");
        ksession.dispose();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testIndexedCallRootedAtUpdatedFactAfterSetterDiscardsCollectedProps(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl =
                "package org.test;\n" +
                "global java.util.List list;\n" +
                "declare P\n" +
                "    @propertyReactive\n" +
                "    name  : String\n" +
                "    items : StringBuilder[]\n" +
                "end\n" +
                "rule Init salience 100 when\n" +
                "then\n" +
                "    P p = new P();\n" +
                "    p.setItems( new StringBuilder[] { new StringBuilder() } );\n" +
                "    insert( p );\n" +
                "end\n" +
                "rule ListenOnItems salience 50 when\n" +
                "    P( items != null )\n" +
                "then\n" +
                "    list.add( \"items\" );\n" +
                "end\n" +
                "rule Modify no-loop salience 0 when\n" +
                "    $p : P()\n" +
                "then\n" +
                "    $p.setName( \"n\" );\n" +
                "    $p.getItems()[0].append( \"x\" );\n" +
                "    update( $p );\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, drl);
        KieSession ksession = kbase.newKieSession();
        final List<Object> list = new java.util.ArrayList<>();
        ksession.setGlobal("list", list);

        ksession.fireAllRules();

        assertThat(list)
                .as("an unresolvable indexed call after a setter must discard the already collected "
                        + "properties and widen the update mask to all-set")
                .containsExactly("items", "items");
        ksession.dispose();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testEnclosedIndexedCallBeforeUpdateOfPropertyReactiveFact(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl =
                "package org.test;\n" +
                "global java.util.List list;\n" +
                "declare P\n" +
                "    @propertyReactive\n" +
                "    items : String[]\n" +
                "end\n" +
                "rule Init when\n" +
                "then\n" +
                "    P p = new P();\n" +
                "    p.setItems( new String[] { \"a\" } );\n" +
                "    insert( p );\n" +
                "end\n" +
                "rule R no-loop when\n" +
                "    $p : P()\n" +
                "then\n" +
                "    ($p.getItems()[0]).length();\n" +
                "    update( $p );\n" +
                "    list.add( \"fired\" );\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, drl);
        KieSession ksession = kbase.newKieSession();
        final List<Object> list = new java.util.ArrayList<>();
        ksession.setGlobal("list", list);

        ksession.fireAllRules();

        assertThat(list)
                .as("an enclosed indexed call before update() of a property-reactive fact must not break rule compilation")
                .containsExactly("fired");
        ksession.dispose();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testIndexedCallRootedAtUpdatedFactWithFieldAssignmentWidensMask(KieBaseTestConfiguration kieBaseTestConfiguration) {
        Assumptions.assumeTrue(kieBaseTestConfiguration.isExecutableModel());
        final String drl =
                "package org.test;\n" +
                "import " + FieldHolder.class.getCanonicalName() + ";\n" +
                "global java.util.List list;\n" +
                "rule Init salience 100 when\n" +
                "then\n" +
                "    FieldHolder h = new FieldHolder();\n" +
                "    h.setItems( new StringBuilder[] { new StringBuilder() } );\n" +
                "    insert( h );\n" +
                "end\n" +
                "rule ListenOnItems salience 50 when\n" +
                "    FieldHolder( items != null )\n" +
                "then\n" +
                "    list.add( \"items\" );\n" +
                "end\n" +
                "rule Modify no-loop salience 0 when\n" +
                "    $p : FieldHolder()\n" +
                "then\n" +
                "    $p.getItems()[0].append( \"x\" );\n" +
                "    $p.name = \"n\";\n" +
                "    update( $p );\n" +
                "end\n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, drl);
        KieSession ksession = kbase.newKieSession();
        final List<Object> list = new java.util.ArrayList<>();
        ksession.setGlobal("list", list);

        ksession.fireAllRules();

        assertThat(list)
                .as("an unresolvable indexed call combined with a direct field assignment must keep the "
                        + "update mask all-set, so the rule listening on 'items' is re-evaluated by update()")
                .containsExactly("items", "items");
        ksession.dispose();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testCrossEnumReferenceWithMisspelledConstant(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // Regression test for the diagnostic experience around the
        // declared-enum cross-reference feature: a misspelled constant name
        // must fail at compile time with a message that names the bad
        // constant, not at runtime via a buried ExceptionInInitializerError
        // on first enum access. The legacy ASM path additionally enriches the
        // message with the list of available constants; the executable-model
        // path relies on the underlying Java compiler (ECJ), whose default
        // "X cannot be resolved or is not a field" wording satisfies the
        // shared contract.
        final String drl =
                "package org.test;\n" +
                "declare enum Color\n" +
                "    RED, GREEN, BLUE;\n" +
                "end\n" +
                "declare enum Light\n" +
                "    STOP( Color.RED ),\n" +
                "    GO(   Color.GRENE );\n" +  // typo: GRENE → GREEN
                "    color: Color\n" +
                "end\n";

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromDrls(kieBaseTestConfiguration, false, drl);
        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
        assertThat(errors).as("expected a compile error naming the bad constant").isNotEmpty();
        assertThat(errors.toString()).contains("GRENE");
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testEnumsWithCompositeBuildingProcess(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        final String drl = "package org.test; " +
                     "" +
                     "declare enum DaysOfWeek " +
                     "    SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY;\n" +
                     "end\n" +

                     "declare Test " +
                     "  field: DaysOfWeek " +
                     "end";

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromDrls(kieBaseTestConfiguration, false, drl);
        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
        assertThat(errors.isEmpty()).as(errors.toString()).isTrue();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testQueryEnum(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String str = "package org.kie.test;\n" +
                "\n" +
                "declare enum Ennumm\n" +
                "  ONE, TWO;\n" +
                "end\n" +
                "\n" +
                "declare Bean\n" +
                "  fld : Ennumm\n" +
                "end\n" +
                "\n" +
                "query seeWhat( Ennumm $e, Bean $b )\n" +
                "  $b := Bean( $e == Ennumm.ONE )\n" +
                "end\n" +
                "\n" +
                "rule rool\n" +
                "when\n" +
                "then\n" +
                "  insert( new Bean( Ennumm.ONE ) );\n" +
                "end\n" +
                "\n" +
                "\n" +
                "rule rool2\n" +
                "when\n" +
                "  seeWhat( $ex, $bx ; )\n" +
                "then\n" +
                "  System.out.println( $bx );\n" +
                "end";

        final String str2 = "package org.drools.mvel.compiler.test2; \n" +
                        "" +
                        "declare Naeb \n" +
                        "   fld : String \n" +
                        "end \n";

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromDrls(kieBaseTestConfiguration, false, str);
        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
        assertThat(errors.isEmpty()).as(errors.toString()).isTrue();
        
        InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.getDefaultKieBaseFromKieBuilder(kieBuilder);

        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromKieModuleFromDrl("tmp", kieBaseTestConfiguration, str2).getKiePackages();
        kbase.addPackages(kpkgs);

        final KieSession ksession = kbase.newKieSession();
        ksession.fireAllRules();
        ksession.dispose();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInnerEnum(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        final StringBuilder rule = new StringBuilder();
        rule.append( "package org.drools.mvel.compiler\n" );
        rule.append( "rule X\n" );
        rule.append( "when\n" );
        rule.append( "    Triangle( type == Triangle.Type.UNCLASSIFIED )\n" );
        rule.append( "then\n" );
        rule.append( "end\n" );

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, rule.toString());
        KieSession ksession = kbase.newKieSession();

        ksession.insert(new Triangle());
        final int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(1);
        ksession.dispose();
    }

}

