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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;

public class SubnetworkSegmentSplitIntegrationTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    public static class Container {
        private final List<String> items;
        public Container(String... items) { this.items = new ArrayList<>(Arrays.asList(items)); }
        public List<String> getItems() { return items; }
    }

    public static class Person {
        private final String name;
        public Person(String name) { this.name = name; }
        public String getName() { return name; }
    }

    // =======================================================================
    // TP-01: Mixed exists + not sharing on cross-package subnetwork
    // =======================================================================
    private static final String DRL_TP01_PKG_A =
            "package repro.tp01.a;\n" +
            "import " + Container.class.getCanonicalName() + ";\n" +
            "global java.util.List results;\n" +
            "rule \"not-rule\"\n" +
            "when\n" +
            "    not ( $c : Container() and String( this == \"match\" ) from $c.items )\n" +
            "then\n" +
            "    results.add(\"not-rule\");\n" +
            "end\n";

    private static final String DRL_TP01_PKG_B =
            "package repro.tp01.b;\n" +
            "import " + Container.class.getCanonicalName() + ";\n" +
            "global java.util.List results;\n" +
            "rule \"exists-rule\"\n" +
            "when\n" +
            "    exists ( $c : Container() and String( this == \"match\" ) from $c.items )\n" +
            "then\n" +
            "    results.add(\"exists-rule\");\n" +
            "end\n";

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTP01_mixedExistsAndNot_crossPackageSharing(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("tp01", cfg, DRL_TP01_PKG_A, DRL_TP01_PKG_B);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);

            // Initial state: no Container inserted.
            // not-rule must fire (no matching items), exists-rule must not fire.
            ks.fireAllRules();
            assertThat(results).as("Empty session: not-rule fires, exists does not").containsExactly("not-rule");
            results.clear();

            // Insert Container with matching item
            FactHandle fh = ks.insert(new Container("match"));
            ks.fireAllRules();
            assertThat(results).as("With matching container: exists-rule fires, not does not").containsExactly("exists-rule");
            results.clear();

            // Retract Container
            ks.delete(fh);
            ks.fireAllRules();
            assertThat(results).as("After retraction: not-rule fires again, exists does not").containsExactly("not-rule");
        } finally {
            ks.dispose();
        }
    }

    // =======================================================================
    // TP-02: Cascading 3+ package subnetwork split
    // =======================================================================
    private static final String DRL_TP02_PKG_A =
            "package repro.tp02.a;\n" +
            "import " + Container.class.getCanonicalName() + ";\n" +
            "global java.util.List results;\n" +
            "rule \"ruleA\"\n" +
            "when\n" +
            "    not ( $c : Container() and String( this == \"tagA\" ) from $c.items )\n" +
            "then\n" +
            "    results.add(\"ruleA\");\n" +
            "end\n";

    private static final String DRL_TP02_PKG_B =
            "package repro.tp02.b;\n" +
            "import " + Container.class.getCanonicalName() + ";\n" +
            "global java.util.List results;\n" +
            "rule \"ruleB\"\n" +
            "when\n" +
            "    not ( $c : Container() and String( this == \"tagB\" ) from $c.items )\n" +
            "then\n" +
            "    results.add(\"ruleB\");\n" +
            "end\n";

    private static final String DRL_TP02_PKG_C =
            "package repro.tp02.c;\n" +
            "import " + Container.class.getCanonicalName() + ";\n" +
            "import " + Person.class.getCanonicalName() + ";\n" +
            "global java.util.List results;\n" +
            "rule \"ruleC\"\n" +
            "when\n" +
            "    not ( $c : Container() and $p : Person() and String( this == $p.name ) from $c.items )\n" +
            "then\n" +
            "    results.add(\"ruleC\");\n" +
            "end\n";

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTP02_cascadingThreePackageSplit(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("tp02", cfg, DRL_TP02_PKG_A, DRL_TP02_PKG_B, DRL_TP02_PKG_C);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);

            // Insert Container matching only ruleA ("tagA") and Person for ruleC
            FactHandle fhC = ks.insert(new Container("tagA"));
            FactHandle fhP = ks.insert(new Person("other"));
            ks.fireAllRules();

            // ruleA is blocked ("tagA" matches).
            // ruleB fires (no "tagB").
            // ruleC fires (Person is "other", not in items).
            assertThat(results).as("Initial fire with Container(tagA)").containsExactlyInAnyOrder("ruleB", "ruleC");
            results.clear();

            // Retract container -> all not-rules re-activate and fire because the blocking/shared container is gone
            ks.delete(fhC);
            ks.fireAllRules();
            assertThat(results).as("After retraction of Container(tagA)").containsExactlyInAnyOrder("ruleA", "ruleB", "ruleC");
        } finally {
            ks.dispose();
        }
    }

    // =======================================================================
    // TP-03: Incremental rule removal on split subnetwork
    // =======================================================================
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTP03_ruleRemovalOnSplitSubnetwork(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("tp03", cfg, DRL_TP01_PKG_A, DRL_TP01_PKG_B);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);

            // Initial insert
            FactHandle fh = ks.insert(new Container("match"));
            ks.fireAllRules();
            assertThat(results).containsExactly("exists-rule");
            results.clear();

            // Remove exists-rule from package b
            kbase.removeRule("repro.tp01.b", "exists-rule");

            // Retract container -> not-rule must still fire even after sibling rule was removed
            ks.delete(fh);
            ks.fireAllRules();
            assertThat(results).as("not-rule must fire on retraction after sibling exists-rule removed").containsExactly("not-rule");
        } finally {
            ks.dispose();
        }
    }

    // =======================================================================
    // TP-04: removeRule cleans up stale eager prototype after segment merge
    // (Toshiya's comment on PR #7116)
    // =======================================================================
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testTP04_removeSiblingRule_afterTopLevelNegationSegmentSplit(
            KieBaseTestConfiguration kieBaseTestConfiguration) {

        final String drlSubject =
                "package repro.b;\n" +
                "import java.util.ArrayList;\n" +
                "import java.util.LinkedList;\n" +
                "rule \"subject\"\n" +
                "    when\n" +
                "        String()\n" +
                "        ArrayList()\n" +
                "        not LinkedList()\n" +
                "    then\n" +
                "end\n";

        final String drlSibling =
                "package repro.c;\n" +
                "import java.util.ArrayList;\n" +
                "import java.util.HashMap;\n" +
                "rule \"sibling\"\n" +
                "    when\n" +
                "        String()\n" +
                "        ArrayList()\n" +
                "        HashMap()\n" +
                "    then\n" +
                "end\n";

        final InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.getKieBaseFromKieModuleFromDrl(
                "remove-sibling-top-level-not-test", kieBaseTestConfiguration, drlSubject);
        final KieBase siblingBase = KieBaseUtil.getKieBaseFromKieModuleFromDrl(
                "remove-sibling-top-level-not-sibling", kieBaseTestConfiguration, drlSibling);

        // Add the sibling after the subject's segment exists, forcing a split that makes the NotNode segment eager.
        kbase.addPackages(siblingBase.getKiePackages());
        // Merging the segments again must remove the obsolete eager prototype before a new session is created.
        kbase.removeRule("repro.c", "sibling");

        final KieSession session = kbase.newKieSession();
        try {
            session.insert("start");
            session.insert(new ArrayList<>());
            assertThat(session.fireAllRules())
                    .as("After removing sibling and merging segments, subject must still fire")
                    .isEqualTo(1);
        } finally {
            session.dispose();
        }
    }
}
