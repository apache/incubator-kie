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
package org.drools.compiler.integrationtests;

import java.util.stream.Stream;

import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.definition.type.FactType;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

public class ExistsWithNotSubnetworkCrossPackageTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    private static final String DRL_TYPES =
            "package repro.types;\n" +
            "declare Seed end\n" +
            "declare SharedFact end\n" +
            "declare NegatedFact end\n" +
            "declare OtherFact end\n" +
            "declare Result end\n";

    private static final String DRL_A =
            "package repro.a;\n" +
            "import repro.types.*;\n" +
            "rule \"produce-shared-fact\"\n" +
            "    when\n" +
            "        Seed()\n" +
            "    then\n" +
            "        insert(new SharedFact());\n" +
            "end\n";

    private static final String DRL_B =
            "package repro.b;\n" +
            "import repro.types.*;\n" +
            "rule \"subject\"\n" +
            "    when\n" +
            "        exists ( SharedFact() and not NegatedFact() )\n" +
            "    then\n" +
            "        insert(new Result());\n" +
            "end\n";

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testExistsWithNotSubnetwork_subjectAloneFiresCorrectly(
            KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl(
                "exists-not-solo-test", kieBaseTestConfiguration,
                DRL_TYPES, DRL_A, DRL_B);

        final KieSession ksession = kbase.newKieSession();
        try {
            final FactType seedType = kbase.getFactType("repro.types", "Seed");
            ksession.insert(seedType.newInstance());
            final int fired = ksession.fireAllRules();
            assertThat(fired)
                    .as("Without sibling, subject must fire (2 rules: produce-shared-fact + subject)")
                    .isEqualTo(2);
        } finally {
            ksession.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testExistsWithNotSubnetworkNotSuppressedByCrossPackageSibling(
            KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {

        final String drlC =
                "package repro.c;\n" +
                "import repro.types.*;\n" +
                "rule \"sibling\"\n" +
                "    when\n" +
                "        exists ( SharedFact() and OtherFact() )\n" +
                "    then\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl(
                "exists-not-cross-package-test", kieBaseTestConfiguration,
                DRL_TYPES, DRL_A, DRL_B, drlC);

        final KieSession ksession = kbase.newKieSession();
        try {
            final FactType seedType = kbase.getFactType("repro.types", "Seed");
            ksession.insert(seedType.newInstance());
            final int fired = ksession.fireAllRules();

            assertThat(fired)
                    .as("Expected 2 rule firings (produce-shared-fact + subject); sibling must not fire")
                    .isEqualTo(2);

            final FactType resultType = kbase.getFactType("repro.types", "Result");
            final long resultCount = ksession.getObjects().stream()
                    .filter(o -> resultType.getFactClass().isInstance(o))
                    .count();
            assertThat(resultCount)
                    .as("subject rule must insert exactly one Result")
                    .isEqualTo(1);
        } finally {
            ksession.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testMinimal_directSharedFactInsert(
            KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {

        final String drlB2 =
                "package repro.b;\n" +
                "import repro.types.*;\n" +
                "rule \"subject\"\n" +
                "    when\n" +
                "        exists ( SharedFact() and not NegatedFact() )\n" +
                "    then\n" +
                "        insert(new Result());\n" +
                "end\n";

        final String drlC2 =
                "package repro.c;\n" +
                "import repro.types.*;\n" +
                "rule \"sibling\"\n" +
                "    when\n" +
                "        exists ( SharedFact() and OtherFact() )\n" +
                "    then\n" +
                "end\n";

        final KieBase kbaseAlone = KieBaseUtil.getKieBaseFromKieModuleFromDrl(
                "minimal-alone-test", kieBaseTestConfiguration,
                DRL_TYPES, drlB2);
        final KieSession sessAlone = kbaseAlone.newKieSession();
        try {
            sessAlone.insert(kbaseAlone.getFactType("repro.types", "SharedFact").newInstance());
            final int firedAlone = sessAlone.fireAllRules();
            assertThat(firedAlone)
                    .as("Without sibling, subject fires directly on SharedFact insert: expected 1")
                    .isEqualTo(1);
        } finally {
            sessAlone.dispose();
        }

        final KieBase kbaseWith = KieBaseUtil.getKieBaseFromKieModuleFromDrl(
                "minimal-with-sibling-test", kieBaseTestConfiguration,
                DRL_TYPES, drlB2, drlC2);
        final KieSession sessWith = kbaseWith.newKieSession();
        try {
            sessWith.insert(kbaseWith.getFactType("repro.types", "SharedFact").newInstance());
            final int firedWith = sessWith.fireAllRules();
            assertThat(firedWith)
                    .as("With sibling (cross-pkg), subject must still fire: expected 1")
                    .isEqualTo(1);
        } finally {
            sessWith.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testExistsWithNotSubnetwork_samePkgSiblingDoesNotSuppressSubject(
            KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {

        final String drlCSamePkg =
                "package repro.b;\n" +
                "import repro.types.*;\n" +
                "rule \"sibling-same-pkg\"\n" +
                "    when\n" +
                "        exists ( SharedFact() and OtherFact() )\n" +
                "    then\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl(
                "exists-not-samepkg-test", kieBaseTestConfiguration,
                DRL_TYPES, DRL_A, DRL_B, drlCSamePkg);

        final KieSession ksession = kbase.newKieSession();
        try {
            final FactType seedType = kbase.getFactType("repro.types", "Seed");
            ksession.insert(seedType.newInstance());
            final int fired = ksession.fireAllRules();
            assertThat(fired)
                    .as("Same-pkg sibling must not suppress subject: expected 2 firings (produce-shared-fact + subject)")
                    .isEqualTo(2);
        } finally {
            ksession.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testMinimal_javaBeans_crossPackage(
            KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {

        final String drlBJava =
                "package repro.bj;\n" +
                "import " + java.util.ArrayList.class.getCanonicalName() + ";\n" +
                "import " + java.util.LinkedList.class.getCanonicalName() + ";\n" +
                "import " + java.util.HashMap.class.getCanonicalName() + ";\n" +
                "rule \"subjectJ\"\n" +
                "    when\n" +
                "        exists ( ArrayList() and not LinkedList() )\n" +
                "    then\n" +
                "end\n";

        final String drlCJava =
                "package repro.cj;\n" +
                "import " + java.util.ArrayList.class.getCanonicalName() + ";\n" +
                "import " + java.util.HashMap.class.getCanonicalName() + ";\n" +
                "rule \"siblingJ\"\n" +
                "    when\n" +
                "        exists ( ArrayList() and HashMap() )\n" +
                "    then\n" +
                "end\n";

        final KieBase aloneBase = KieBaseUtil.getKieBaseFromKieModuleFromDrl(
                "java-alone-test", kieBaseTestConfiguration, drlBJava);
        final KieSession aloneSession = aloneBase.newKieSession();
        try {
            aloneSession.insert(new java.util.ArrayList<>());
            assertThat(aloneSession.fireAllRules())
                    .as("Java classes, no sibling: subject must fire")
                    .isEqualTo(1);
        } finally {
            aloneSession.dispose();
        }

        final KieBase withBase = KieBaseUtil.getKieBaseFromKieModuleFromDrl(
                "java-with-sibling-test", kieBaseTestConfiguration, drlBJava, drlCJava);
        final KieSession withSession = withBase.newKieSession();
        try {
            withSession.insert(new java.util.ArrayList<>());
            assertThat(withSession.fireAllRules())
                    .as("Java classes, with cross-pkg sibling: subject must still fire")
                    .isEqualTo(1);
        } finally {
            withSession.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testRemoveSiblingRule_subjectStillFires(
            KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {

        final String drlB =
                "package repro.b;\n" +
                "import repro.types.*;\n" +
                "rule \"subject\"\n" +
                "    when\n" +
                "        exists ( SharedFact() and not NegatedFact() )\n" +
                "    then\n" +
                "        insert(new Result());\n" +
                "end\n";

        final String drlC =
                "package repro.c;\n" +
                "import repro.types.*;\n" +
                "rule \"sibling\"\n" +
                "    when\n" +
                "        exists ( SharedFact() and OtherFact() )\n" +
                "    then\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl(
                "remove-sibling-test", kieBaseTestConfiguration,
                DRL_TYPES, drlB, drlC);

        kbase.removeRule("repro.c", "sibling");

        final KieSession session = kbase.newKieSession();
        try {
            session.insert(kbase.getFactType("repro.types", "SharedFact").newInstance());
            final int fired = session.fireAllRules();
            assertThat(fired)
                    .as("After removing sibling, subject must still fire: expected 1")
                    .isEqualTo(1);
        } finally {
            session.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testNotWithNotSubnetwork_crossPackage(
            KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {

        final String drlB =
                "package repro.b;\n" +
                "import repro.types.*;\n" +
                "rule \"subject-not\"\n" +
                "    when\n" +
                "        not ( SharedFact() and not NegatedFact() )\n" +
                "    then\n" +
                "        insert(new Result());\n" +
                "end\n";

        final String drlC =
                "package repro.c;\n" +
                "import repro.types.*;\n" +
                "rule \"sibling\"\n" +
                "    when\n" +
                "        exists ( SharedFact() and OtherFact() )\n" +
                "    then\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl(
                "not-with-not-subnetwork-test", kieBaseTestConfiguration,
                DRL_TYPES, drlB, drlC);

        final KieSession session = kbase.newKieSession();
        try {
            final int fired = session.fireAllRules();
            assertThat(fired)
                    .as("No SharedFact inserted: not(SharedFact and not NegatedFact) must fire")
                    .isEqualTo(1);
        } finally {
            session.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testMultiJoinWithNegation_crossPackage(
            KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {

        final String drlB =
                "package repro.b;\n" +
                "import repro.types.*;\n" +
                "rule \"subject-multijoin\"\n" +
                "    when\n" +
                "        exists ( SharedFact() and OtherFact() and not NegatedFact() )\n" +
                "    then\n" +
                "        insert(new Result());\n" +
                "end\n";

        final String drlC =
                "package repro.c;\n" +
                "import repro.types.*;\n" +
                "rule \"sibling-shared-only\"\n" +
                "    when\n" +
                "        exists ( SharedFact() and Seed() )\n" +
                "    then\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl(
                "multijoin-not-test", kieBaseTestConfiguration,
                DRL_TYPES, drlB, drlC);

        final KieSession session = kbase.newKieSession();
        try {
            session.insert(kbase.getFactType("repro.types", "SharedFact").newInstance());
            session.insert(kbase.getFactType("repro.types", "OtherFact").newInstance());
            final int fired = session.fireAllRules();
            assertThat(fired)
                    .as("SharedFact + OtherFact inserted: subject-multijoin must fire")
                    .isEqualTo(1);
        } finally {
            session.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testMultipleChainedNegations_crossPackage(
            KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {

        final String drlB =
                "package repro.b;\n" +
                "import repro.types.*;\n" +
                "rule \"subject-double-not\"\n" +
                "    when\n" +
                "        exists ( SharedFact() and not NegatedFact() and not OtherFact() )\n" +
                "    then\n" +
                "        insert(new Result());\n" +
                "end\n";

        final String drlC =
                "package repro.c;\n" +
                "import repro.types.*;\n" +
                "rule \"sibling\"\n" +
                "    when\n" +
                "        exists ( SharedFact() and Seed() )\n" +
                "    then\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl(
                "double-not-test", kieBaseTestConfiguration,
                DRL_TYPES, drlB, drlC);

        final KieSession session = kbase.newKieSession();
        try {
            session.insert(kbase.getFactType("repro.types", "SharedFact").newInstance());
            final int fired = session.fireAllRules();
            assertThat(fired)
                    .as("SharedFact inserted without negated facts: subject-double-not must fire")
                    .isEqualTo(1);
        } finally {
            session.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testAccumulateWithNotSubnetwork_crossPackage(
            KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {

        final String drlB =
                "package repro.b;\n" +
                "import repro.types.*;\n" +
                "rule \"subject-accumulate\"\n" +
                "    when\n" +
                "        accumulate( SharedFact() and not NegatedFact(); $c : count() )\n" +
                "    then\n" +
                "        insert(new Result());\n" +
                "end\n";

        final String drlC =
                "package repro.c;\n" +
                "import repro.types.*;\n" +
                "rule \"sibling\"\n" +
                "    when\n" +
                "        exists ( SharedFact() and OtherFact() )\n" +
                "    then\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl(
                "accumulate-not-test", kieBaseTestConfiguration,
                DRL_TYPES, drlB, drlC);

        final KieSession session = kbase.newKieSession();
        try {
            session.insert(kbase.getFactType("repro.types", "SharedFact").newInstance());
            final int fired = session.fireAllRules();
            assertThat(fired)
                    .as("SharedFact inserted: accumulate should count 1 and fire")
                    .isEqualTo(1);
        } finally {
            session.dispose();
        }
    }
}
