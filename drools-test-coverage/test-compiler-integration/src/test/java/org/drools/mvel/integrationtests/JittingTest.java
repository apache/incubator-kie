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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.drools.mvel.compiler.Person;
import org.drools.mvel.integrationtests.facts.AnEnum;
import org.drools.mvel.integrationtests.facts.FactWithEnum;
import org.drools.mvel.integrationtests.facts.FactWithMap;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.builder.KieModule;
import org.kie.api.runtime.KieSession;
import org.kie.internal.conf.ConstraintJittingThresholdOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class JittingTest {

    // This test is basically written for Mvel Jitting. But it has good edge cases which are useful for testing even with exec-model.

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJitConstraintInvokingConstructor(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // JBRULES-3628
        final String str = "import org.drools.mvel.compiler.Person;\n" +
                "rule R1 when\n" +
                "   Person( new Integer( ageAsInteger ) < 40 ) \n" +
                "then\n" +
                "end";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        final KieSession ksession = kbase.newKieSession();

        ksession.insert(new Person("Mario", 38));

        assertThat(ksession.fireAllRules()).isEqualTo(1);
        ksession.dispose();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJittingConstraintWithInvocationOnLiteral(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String str = "package com.sample\n" +
                "import org.drools.mvel.compiler.Person\n" +
                "rule XXX when\n" +
                "  Person( name.toString().toLowerCase().contains( \"mark\".toString().toLowerCase() ) )\n" +
                "then\n" +
                "end\n";

        testJitting(kieBaseTestConfiguration, str);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJittingMethodWithCharSequenceArg(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String str = "package com.sample\n" +
                "import org.drools.mvel.compiler.Person\n" +
                "rule XXX when\n" +
                "  Person( $n : name, $n.contains( \"mark\" ) )\n" +
                "then\n" +
                "end\n";

        testJitting(kieBaseTestConfiguration, str);
    }

    private void testJitting(KieBaseTestConfiguration kieBaseTestConfiguration, final String drl) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();

        ksession.insert(new Person("mark", 37));
        ksession.insert(new Person("mario", 38));

        ksession.fireAllRules();
        ksession.dispose();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJittingEnum(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl = "import " + AnEnum.class.getCanonicalName() + ";\n" +
                " rule R1 \n" +
                " when \n" +
                "    $enumFact: AnEnum(this == AnEnum.FIRST)\n" +
                " then \n" +
                " end ";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kieBase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, ConstraintJittingThresholdOption.get(0));
        final KieSession kieSession = kieBase.newKieSession();

        kieSession.insert(AnEnum.FIRST);
        assertThat(kieSession.fireAllRules()).isEqualTo(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJittingEnumAttribute(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl = "import " + AnEnum.class.getCanonicalName() + ";\n" +
                "import " + FactWithEnum.class.getCanonicalName() + ";\n" +
                " rule R1 \n" +
                " when \n" +
                "    $factWithEnum: FactWithEnum(enumValue == AnEnum.FIRST) \n" +
                " then \n" +
                " end ";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kieBase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, ConstraintJittingThresholdOption.get(0));

        final KieSession kieSession = kieBase.newKieSession();
        kieSession.insert(new FactWithEnum(AnEnum.FIRST));
        assertThat(kieSession.fireAllRules()).isEqualTo(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testMvelJitDivision(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-2928
        String drl = "import " + Person.class.getName() + ";\n"
                + "rule R1 when\n"
                + "  Person( name == \"John\", $age1 : age )\n"
                + "  Person( name == \"Paul\", age > ((2*$age1)/3) )\n"
                + "then end\n";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kieBase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, ConstraintJittingThresholdOption.get(0));
        final KieSession ksession = kieBase.newKieSession();

        Person john = new Person("John", 20);
        ksession.insert(john);
        Person paul = new Person("Paul", 20);
        ksession.insert(paul);

        int fired = ksession.fireAllRules();

        assertThat(fired).isEqualTo(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJitMemberOf(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-3794
        String drl =
                "import java.util.ArrayList;\n" +
                "import java.util.List;\n" +
                "\n" +
                "declare Foo\n" +
                "  barNames : List\n" +
                "end\n" +
                "\n" +
                "declare Bar\n" +
                "  name : String\n" +
                "end\n" +
                "\n" +
                "rule \"Init\"\n" +
                "  when\n" +
                "    not (Foo ())\n" +
                "  then\n" +
                "    List list = new ArrayList<String>();" +
                "    list.add(null);" +
                "    insert(new Foo(list));\n" +
                "    insert(new Bar(null));\n" +
                "end\n" +
                "\n" +
                "rule \"Add name\"\n" +
                "  when\n" +
                "    foo : Foo()\n" +
                "    bar : Bar(name memberOf foo.barNames)\n" +
                "  then\n" +
                "end";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kieBase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, ConstraintJittingThresholdOption.get(0));
        final KieSession ksession = kieBase.newKieSession();

        int fired = ksession.fireAllRules();

        assertThat(fired).isEqualTo(2);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJitMapCoercion(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-4334
        checkJitMapCoercion(kieBaseTestConfiguration, "status < $map.get(\"key\")", true, 0);
        checkJitMapCoercion(kieBaseTestConfiguration, "$map.get(\"key\") > status", true, 0);
        checkJitMapCoercion(kieBaseTestConfiguration, "status > $map.get(\"key\")", true, 1);
        checkJitMapCoercion(kieBaseTestConfiguration, "$map.get(\"key\") < status", true, 1);

        checkJitMapCoercion(kieBaseTestConfiguration, "status < $map.get(\"key\")", false, 1);
        checkJitMapCoercion(kieBaseTestConfiguration, "$map.get(\"key\") > status", false, 1);
        checkJitMapCoercion(kieBaseTestConfiguration, "status > $map.get(\"key\")", false, 0);
        checkJitMapCoercion(kieBaseTestConfiguration, "$map.get(\"key\") < status", false, 0);

        // DROOLS-5596
        checkJitMapCoercion(kieBaseTestConfiguration, "$map.get(\"key\") < 10", true, 1);
        checkJitMapCoercion(kieBaseTestConfiguration, "$map.get(\"key\") < \"10\"", true, 1);
        checkJitMapCoercion(kieBaseTestConfiguration, "10 > $map.get(\"key\")", true, 1);
        checkJitMapCoercion(kieBaseTestConfiguration, "\"10\" > $map.get(\"key\")", true, 1);
    }

    public void checkJitMapCoercion(KieBaseTestConfiguration kieBaseTestConfiguration, String constraint, boolean useInt, int expectedFires) {
        String drl =
                "package com.sample\n" +
                "import " + Map.class.getCanonicalName() + ";\n" +
                "import " + Person.class.getCanonicalName() + ";\n" +
                "rule R when\n" +
                "  $map : Map()\n" +
                "  Person( " + constraint + " )\n" +
                "then\n" +
                "end";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kieBase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, ConstraintJittingThresholdOption.get(0));
        final KieSession ksession = kieBase.newKieSession();

        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("key", useInt ? 5 : "a");
        ksession.insert(valueMap);

        Person person = new Person();
        person.setStatus("10");
        ksession.insert(person);

        assertThat(ksession.fireAllRules()).isEqualTo(expectedFires);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJitContainsOnStringTypedAsObject(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // The build resolves itemsMap.get() to String from the generics; the jitter sees the erased Object
        checkJitContainsOnStringTypedAsObject(kieBaseTestConfiguration, "itemsMap.get(1) contains \"T80011\"", 1);
        checkJitContainsOnStringTypedAsObject(kieBaseTestConfiguration, "itemsMap.get(1) contains \"T99999\"", 0);
        checkJitContainsOnStringTypedAsObject(kieBaseTestConfiguration, "itemsMap.get(1) not contains \"T80011\"", 0);
        checkJitContainsOnStringTypedAsObject(kieBaseTestConfiguration, "itemsMap.get(1) not contains \"T99999\"", 1);
    }

    private void checkJitContainsOnStringTypedAsObject(KieBaseTestConfiguration kieBaseTestConfiguration, String constraint, int expectedFires) {
        String drl =
                "import " + FactWithMap.class.getCanonicalName() + ";\n" +
                "rule R when\n" +
                "    FactWithMap( " + constraint + " )\n" +
                "then\n" +
                "end\n";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kieBase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, ConstraintJittingThresholdOption.get(0));
        final KieSession ksession = kieBase.newKieSession();
        try {
            ksession.insert(new FactWithMap(1, "FAILURE.T80011,FAILURE.T80012"));
            assertThat(ksession.fireAllRules()).as(constraint).isEqualTo(expectedFires);
        } finally {
            ksession.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJittingBigDecimalAdd(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // RHDM-1635
        final String drl =
                "import " + BigDecimalFact.class.getCanonicalName() + ";\n" +
                " rule R1 \n" +
                " when \n" +
                "    $fact : BigDecimalFact( $value : (value + 10) == 20 )\n" +
                " then \n" +
                " end ";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kieBase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, ConstraintJittingThresholdOption.get(0));
        final KieSession kieSession = kieBase.newKieSession();

        kieSession.insert(new BigDecimalFact(new BigDecimal(10)));
        kieSession.insert(new BigDecimalFact(new BigDecimal(11)));
        assertThat(kieSession.fireAllRules()).isEqualTo(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJittingBigDecimalRemainder(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // RHDM-1635
        final String drl =
                "import " + BigDecimalFact.class.getCanonicalName() + ";\n" +
                " rule R1 \n" +
                " when \n" +
                "    $fact : BigDecimalFact( $value : (value % 10) == 0 )\n" +
                " then \n" +
                " end ";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kieBase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, ConstraintJittingThresholdOption.get(0));
        final KieSession kieSession = kieBase.newKieSession();

        kieSession.insert(new BigDecimalFact(new BigDecimal(10)));
        kieSession.insert(new BigDecimalFact(new BigDecimal(11)));
        assertThat(kieSession.fireAllRules()).isEqualTo(1);
    }

    public class BigDecimalFact {
        private BigDecimal value;

        public BigDecimalFact(BigDecimal value) {
            this.value = value;
        }

        public BigDecimal getValue() {
            return value;
        }

        public void setValue(BigDecimal value) {
            this.value = value;
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testBigDecimalConstructorCoercion(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-6729
        final String drl =
                "import " + BigDecimal.class.getCanonicalName() + "\n" +
                           "import " + Person.class.getCanonicalName() + "\n" +
                           " rule R1 when\n" +
                           "     Person($bd : new BigDecimal(30), bigDecimal.compareTo(new BigDecimal($bd)) < 0)\n" +
                           " then end\n";

        if (!kieBaseTestConfiguration.isExecutableModel()) {
            // It's valid to fail with exec-model because newBigDecimal(BigDecimal bd) doesn't exist.
            // This test is just to confirm the fix of mvel getBestConstructorCandidate()
            // Also note that the issue depends on JDK native Class#getConstructors() and occurs randomly
            final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
            final KieBase kieBase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, ConstraintJittingThresholdOption.get(0));
            final KieSession kieSession = kieBase.newKieSession();

            Person person = new Person("John");
            person.setBigDecimal(new BigDecimal(20));
            kieSession.insert(person);
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testJittingVarargsCall(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-6841
        final String drl =
                " rule R1 \n" +
                " when \n" +
                "    String( java.util.Arrays.asList(\"CI,CV\".split(\",\")).contains(this) )\n" +
                " then \n" +
                " end ";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kieBase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, ConstraintJittingThresholdOption.get(0));
        final KieSession kieSession = kieBase.newKieSession();

        kieSession.insert("CV");
        kieSession.insert("CX");
        assertThat(kieSession.fireAllRules()).isEqualTo(1);
    }

     public static class Tester {
        public String get(String x) {
            return "test";
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInvokeVarargsConstructor(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-7095
        String drl =
                "package test\n" +
                "import " + Tester.class.getCanonicalName() + "\n" +
                "\n" +
                "rule R1 when \n" +
                "    Tester( \"test\" == get( new String() ) )\n" +
                "then \n" +
                "end";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kieBase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, ConstraintJittingThresholdOption.get(0));
        KieSession ksession = kieBase.newKieSession();
        ksession.insert(new Tester());
        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    void nonTerminatingDecimal(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // incubator-kie-drools/issues/6249
        String drl =
                "package test\n" +
                        "import " + Person.class.getCanonicalName() + "\n" +
                        "\n" +
                        "rule R1 when \n" +
                        "    Person( bigDecimal / 7B >= 0 )\n" +
                        "then \n" +
                        "end";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kieBase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, ConstraintJittingThresholdOption.get(0));
        KieSession ksession = kieBase.newKieSession();
        Person person = new Person();
        person.setBigDecimal(new BigDecimal("15"));
        ksession.insert(person);
        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    private static Stream<String> accuracyConstraintsForDECIMAL128() {
        // These constraints require more than MathContext.DECIMAL128 accuracy.
        // Because Drools/Mvel BigDecimal calculation is based on MathContext.DECIMAL128, these constraints shouldn't match.
        return Stream.of(
                "(bigDecimal + $bd1) == 11111111101111111110111111111011111110B",
                "(bigDecimal - $bd1) == 8641975320864197532086419753208641976B",
                "(bigDecimal * $bd1) == 12193263113702179522618503273386678850399329371828684651861743636654061881B"
        );
    }

    private static Stream<Arguments> argumentsForAccuracyTest() {
        List<KieBaseTestConfiguration> configurations = parameters().toList();
        List<String> constraints = accuracyConstraintsForDECIMAL128().toList();
        List<ConstraintJittingThresholdOption> jittingThresholds = Stream.of(0, 1).map(ConstraintJittingThresholdOption::get).toList();
        return configurations.stream()
                .flatMap(config -> constraints.stream()
                        .flatMap(constraint -> jittingThresholds.stream()
                                .map(jittingThreshold -> Arguments.of(config, constraint, jittingThreshold))));
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("argumentsForAccuracyTest")
    void bigDecimalAccuracyForDECIMAL128(KieBaseTestConfiguration kieBaseTestConfiguration, String constraint, ConstraintJittingThresholdOption jittingThreshold) {
        //
        String drl =
                "package test\n" +
                        "import " + Person.class.getCanonicalName() + "\n" +
                        "rule R1 when \n" +
                        "    Person( name == \"person1\", $bd1 : bigDecimal )\n" +
                        "    Person( name == \"person2\", " + constraint + " )\n" +
                        "then \n" +
                        "end";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kieBase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, jittingThreshold);
        KieSession ksession = kieBase.newKieSession();

        BigDecimal bd1 = new BigDecimal("1234567890123456789012345678901234567");
        Person person1 = new Person("person1");
        person1.setBigDecimal(bd1);

        BigDecimal bd2 = new BigDecimal("9876543210987654321098765432109876543");
        Person person2 = new Person("person2");
        person2.setBigDecimal(bd2);

        ksession.insert(person1);
        ksession.insert(person2);
        assertThat(ksession.fireAllRules()).isZero();
    }

    private static Stream<Arguments> argumentsArithmetic() {
        List<KieBaseTestConfiguration> configurations = parameters().toList();
        List<String> operators = Stream.of("+", "-", "*", "/").toList();
        return configurations.stream()
                .flatMap(config -> operators.stream()
                        .map(operator -> Arguments.of(config, operator)));
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("argumentsArithmetic")
    void bigIntegerArithmetic(KieBaseTestConfiguration kieBaseTestConfiguration, String operator) {
        assumeFalse(kieBaseTestConfiguration.isExecutableModel(),
                    "BigInteger arithmetic with exec-model is skipped " +
                            "until incubator-kie-drools/issues/6253 is fixed");

        String drl =
                "package test\n" +
                        "import " + Person.class.getCanonicalName() + "\n" +
                        "\n" +
                        "rule R1 when \n" +
                        "    Person( bigInteger " + operator + " 7I >= 0 )\n" +
                        "then \n" +
                        "end";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kieBase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, ConstraintJittingThresholdOption.get(0));
        KieSession ksession = kieBase.newKieSession();
        Person person = new Person();
        person.setBigInteger(new BigInteger("15"));
        ksession.insert(person);
        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    void jitStrOperatorWithVariable(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // incubator-kie-drools#6711
        // The 'str' operator with a variable binding creates an MVELConstraint with
        // non-empty EvaluatorWrapper[] on a typesafe POJO (isDynamic=false).
        // With jit threshold 0, the ASM jitter generates bytecode calling
        // EvaluatorHelper.initOperators with Tuple.class instead of BaseTuple.class.

        String drl =
                "declare Item\n" +
                "    name : String\n" +
                "end\n" +
                "rule Init when then insert(new Item(\"hello\")); end\n" +
                "rule R when\n" +
                "    $prefix : String()\n" +
                "    Item( name str[startsWith] $prefix )\n" +
                "then\n" +
                "end\n";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kieBase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(
                kieModule, kieBaseTestConfiguration, ConstraintJittingThresholdOption.get(0));
        final KieSession ksession = kieBase.newKieSession();
        try {
            ksession.insert("hel");
            assertThat(ksession.fireAllRules()).isEqualTo(2);
        } finally {
            ksession.dispose();
        }
    }
}
