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
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the bug described in
 * https://github.com/apache/incubator-kie/pull/7116#issuecomment-5714791182
 *
 * <p>Pattern: {@code not( X( $list: things ) and Y() from $list )} shared between two rules
 * in different packages.  The cross-package sibling causes the inner subnetwork segment
 * {@code [JoinNode(X), FromNode(Y from $list), TupleToObjectNode]} to be split into
 * {@code [JoinNode(X)]} and {@code [FromNode, TTON]}.  After the split the TTON's
 * PathMemory segment-memories array slot for the new {@code [FromNode, TTON]} segment
 * is left null, so when a fact is later retracted the {@code not}-subnetwork loses its
 * link and the rule permanently stops firing.</p>
 *
 * <p>Two packages are required so that {@code KnowledgeBaseImpl.addPackages()} adds them
 * one at a time, which is the only path that invokes
 * {@code EagerPhreakBuilder.Add.processSplit} and triggers the segment split.</p>
 *
 * @see NotFromListSamePkgTest for same-package baseline
 */
public class NotFromListCrossPkgTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    public static class Container {
        private final List<String> items;
        public Container(String... items) { this.items = new ArrayList<>(Arrays.asList(items)); }
        public List<String> getItems() { return items; }
    }

    // Package B: not( Container($list: items) and String("blocked") from $list )
    private static final String DRL_B =
            "package repro.b;\n" +
            "import " + Container.class.getCanonicalName() + ";\n" +
            "global java.util.List results;\n" +
            "rule \"subject\"\n" +
            "when\n" +
            "    not ( $c : Container() and String( this == \"blocked\" ) from $c.items )\n" +
            "then\n" +
            "    results.add(\"subject\");\n" +
            "end\n";

    // Package C: adjacent rule — shares the Container OTN, different tag.
    // Its addition triggers a segment split inside the not-subnetwork.
    private static final String DRL_C =
            "package repro.c;\n" +
            "import " + Container.class.getCanonicalName() + ";\n" +
            "global java.util.List results;\n" +
            "rule \"sibling\"\n" +
            "when\n" +
            "    not ( $c : Container() and String( this == \"special\" ) from $c.items )\n" +
            "then\n" +
            "    results.add(\"sibling\");\n" +
            "end\n";

    // Package D: third rule — triggers a second processSplit call, creating a second lazy slot.
    private static final String DRL_D =
            "package repro.d;\n" +
            "import " + Container.class.getCanonicalName() + ";\n" +
            "global java.util.List results;\n" +
            "rule \"cousin\"\n" +
            "when\n" +
            "    not ( $c : Container() and String( this == \"extra\" ) from $c.items )\n" +
            "then\n" +
            "    results.add(\"cousin\");\n" +
            "end\n";

    // -----------------------------------------------------------------------
    // Control: single-package baseline — no segment split, must always pass
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void subjectAlone_firesCorrectly(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("not-from-alone", cfg, DRL_B);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);
            assertThat(ks.fireAllRules()).as("no Container: subject must fire").isEqualTo(1);
            assertThat(results).containsExactly("subject");
        } finally {
            ks.dispose();
        }
    }

    // -----------------------------------------------------------------------
    // Cross-package: both rules must fire before and after a retract
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void bothRulesFire_noContainer_crossPackage(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("not-from-both", cfg, DRL_B, DRL_C);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);
            assertThat(ks.fireAllRules()).as("no Container: both rules must fire").isEqualTo(2);
            assertThat(results).containsExactlyInAnyOrder("subject", "sibling");
        } finally {
            ks.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void subjectSuppressedByBlockedItem_crossPackage(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("not-from-suppress", cfg, DRL_B, DRL_C);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);
            ks.insert(new Container("blocked"));
            ks.fireAllRules();
            assertThat(results).as("blocked item suppresses subject; sibling must still fire")
                               .containsExactly("sibling");
        } finally {
            ks.dispose();
        }
    }

    /**
     * Insert a blocking Container, then retract it.
     * subject's NOT transitions FALSE→TRUE so it must re-fire.
     * sibling's NOT stays TRUE→TRUE so it must not re-fire.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void subjectRefiresAfterBlockingContainerIsRetracted_crossPackage(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("not-from-retract", cfg, DRL_B, DRL_C);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);

            // Insert a Container with a "blocked" item: subject suppressed, sibling fires
            FactHandle fh = ks.insert(new Container("blocked"));
            ks.fireAllRules();
            assertThat(results).as("only sibling fires while blocked item is present")
                               .containsExactly("sibling");

            // Retract: subject FALSE→TRUE must fire; sibling TRUE→TRUE must not re-fire
            results.clear();
            ks.delete(fh);
            ks.fireAllRules();
            assertThat(results)
                    .as("subject FALSE->TRUE must fire; sibling TRUE->TRUE must not re-fire")
                    .containsExactly("subject");
        } finally {
            ks.dispose();
        }
    }

    // -----------------------------------------------------------------------
    // Three-package: second processSplit call creates a second lazy slot
    // -----------------------------------------------------------------------

    /**
     * Three packages sharing the same subnetwork.  Adding DRL_D causes
     * {@code processSplit} to be called a second time.
     * After retract, only subject (FALSE→TRUE) must fire;
     * sibling and cousin (TRUE→TRUE) must not re-fire.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void threePackages_onlyBlockedRuleRefiresAfterRetract(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("not-from-three", cfg, DRL_B, DRL_C, DRL_D);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);

            // Insert a Container that blocks only "subject"; the other two rules fire
            FactHandle fh = ks.insert(new Container("blocked"));
            ks.fireAllRules();
            assertThat(results).as("blocked item suppresses subject; sibling and cousin must fire")
                               .containsExactlyInAnyOrder("sibling", "cousin");

            // Retract: subject FALSE→TRUE must fire; sibling/cousin TRUE→TRUE must not re-fire
            results.clear();
            ks.delete(fh);
            ks.fireAllRules();
            assertThat(results)
                    .as("subject FALSE->TRUE must fire; sibling/cousin TRUE->TRUE must not re-fire")
                    .containsExactly("subject");
        } finally {
            ks.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void insertNonMatchingThenRetract_crossPackage(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("not-from-nonmatch", cfg, DRL_B, DRL_C);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);

            // Container with no matching items: both fire
            FactHandle fh = ks.insert(new Container("other"));
            ks.fireAllRules();
            assertThat(results).containsExactlyInAnyOrder("subject", "sibling");

            // Retract: neither should re-fire (TRUE→TRUE for both)
            results.clear();
            ks.delete(fh);
            ks.fireAllRules();
            assertThat(results).isEmpty();
        } finally {
            ks.dispose();
        }
    }

    // -----------------------------------------------------------------------
    // TRUE→TRUE: non-matching Container multiple cycles — no re-fire expected
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void crossPkg_trueTrue_multipleCycles(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("xpkg-tt", cfg, DRL_B, DRL_C);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);

            FactHandle fh1 = ks.insert(new Container("blocked"));
            ks.fireAllRules();
            assertThat(results).containsExactly("sibling");

            results.clear();
            ks.delete(fh1);
            ks.fireAllRules();
            assertThat(results).containsExactly("subject");

            results.clear();
            FactHandle fh2 = ks.insert(new Container("blocked"));
            int fired = ks.fireAllRules();
            assertThat(fired).as("TRUE→TRUE: no re-fire on cycle2 insert").isZero();

            results.clear();
            ks.delete(fh2);
            ks.fireAllRules();
            assertThat(results).containsExactly("subject");
        } finally {
            ks.dispose();
        }
    }

    // -----------------------------------------------------------------------
    // TRUE→FALSE→TRUE: matching Container blocks then unblocks — must re-fire
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void crossPkg_trueFalseTrue_siblingBlocked(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("xpkg-tft-sib", cfg, DRL_B, DRL_C);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);

            ks.fireAllRules();
            assertThat(results).containsExactlyInAnyOrder("subject", "sibling");

            results.clear();
            FactHandle fh1 = ks.insert(new Container("special"));
            ks.fireAllRules();
            assertThat(results).as("sibling blocked by 'special'").isEmpty();

            results.clear();
            ks.delete(fh1);
            ks.fireAllRules();
            assertThat(results).as("sibling FALSE→TRUE must re-fire").containsExactly("sibling");

            results.clear();
            FactHandle fh2 = ks.insert(new Container("special"));
            ks.fireAllRules();
            assertThat(results).isEmpty();

            results.clear();
            ks.delete(fh2);
            ks.fireAllRules();
            assertThat(results).as("sibling FALSE→TRUE cycle2").containsExactly("sibling");
        } finally {
            ks.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void crossPkg_trueFalseTrue_subjectBlocked(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("xpkg-tft-sub", cfg, DRL_B, DRL_C);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);

            ks.fireAllRules();
            assertThat(results).containsExactlyInAnyOrder("subject", "sibling");

            results.clear();
            FactHandle fh1 = ks.insert(new Container("blocked"));
            ks.fireAllRules();
            assertThat(results).as("subject blocked by 'blocked'").isEmpty();

            results.clear();
            ks.delete(fh1);
            ks.fireAllRules();
            assertThat(results).as("subject FALSE→TRUE must re-fire").containsExactly("subject");

            results.clear();
            FactHandle fh2 = ks.insert(new Container("blocked"));
            ks.fireAllRules();
            assertThat(results).isEmpty();

            results.clear();
            ks.delete(fh2);
            ks.fireAllRules();
            assertThat(results).as("subject FALSE→TRUE cycle2").containsExactly("subject");
        } finally {
            ks.dispose();
        }
    }
}
