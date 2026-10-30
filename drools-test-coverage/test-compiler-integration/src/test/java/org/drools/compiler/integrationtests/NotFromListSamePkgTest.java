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
 * Same-package counterpart of {@link NotFromListCrossPkgTest}.
 *
 * <p>All rules live in a single package, so no cross-package segment split occurs.
 * These tests establish the baseline NOT-subnetwork semantics.</p>
 */
public class NotFromListSamePkgTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    public static class Container {
        private final List<String> items;
        public Container(String... items) { this.items = new ArrayList<>(Arrays.asList(items)); }
        public List<String> getItems() { return items; }
    }

    private static final String DRL_SINGLE =
            "package repro.single;\n" +
            "import " + Container.class.getCanonicalName() + ";\n" +
            "global java.util.List results;\n" +
            "rule \"sibling\"\n" +
            "when\n" +
            "    not ( $c : Container() and String( this == \"special\" ) from $c.items )\n" +
            "then\n" +
            "    results.add(\"sibling\");\n" +
            "end\n";

    private static final String DRL_TWO_RULES =
            "package repro.same;\n" +
            "import " + Container.class.getCanonicalName() + ";\n" +
            "global java.util.List results;\n" +
            "rule \"subject\"\n" +
            "when\n" +
            "    not ( $c : Container() and String( this == \"blocked\" ) from $c.items )\n" +
            "then\n" +
            "    results.add(\"subject\");\n" +
            "end\n" +
            "rule \"sibling\"\n" +
            "when\n" +
            "    not ( $c : Container() and String( this == \"special\" ) from $c.items )\n" +
            "then\n" +
            "    results.add(\"sibling\");\n" +
            "end\n";

    // -----------------------------------------------------------------------
    // TRUE→TRUE: non-matching Container insert/retract — no re-fire expected
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void singleRule_trueTrue_multipleCycles(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("single-tt", cfg, DRL_SINGLE);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);

            FactHandle fh1 = ks.insert(new Container("blocked"));
            ks.fireAllRules();
            assertThat(results).containsExactly("sibling");

            results.clear();
            ks.delete(fh1);
            int fired1 = ks.fireAllRules();
            assertThat(fired1).as("TRUE→TRUE: no re-fire on retract").isZero();

            results.clear();
            FactHandle fh2 = ks.insert(new Container("blocked"));
            int fired2 = ks.fireAllRules();
            assertThat(fired2).as("TRUE→TRUE: no re-fire on cycle2 insert").isZero();

            results.clear();
            ks.delete(fh2);
            int fired3 = ks.fireAllRules();
            assertThat(fired3).as("TRUE→TRUE: no re-fire on cycle2 retract").isZero();
        } finally {
            ks.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void twoRules_trueTrue_multipleCycles(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("two-tt", cfg, DRL_TWO_RULES);
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
    public void singleRule_trueFalseTrue_multipleCycles(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("single-tft", cfg, DRL_SINGLE);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);

            ks.fireAllRules();
            assertThat(results).containsExactly("sibling");

            results.clear();
            FactHandle fh1 = ks.insert(new Container("special"));
            ks.fireAllRules();
            assertThat(results).as("NOT becomes FALSE").isEmpty();

            results.clear();
            ks.delete(fh1);
            ks.fireAllRules();
            assertThat(results).as("FALSE→TRUE: must re-fire").containsExactly("sibling");

            results.clear();
            FactHandle fh2 = ks.insert(new Container("special"));
            ks.fireAllRules();
            assertThat(results).as("NOT becomes FALSE again").isEmpty();

            results.clear();
            ks.delete(fh2);
            ks.fireAllRules();
            assertThat(results).as("FALSE→TRUE cycle2: must re-fire").containsExactly("sibling");
        } finally {
            ks.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void twoRules_trueFalseTrue_multipleCycles(KieBaseTestConfiguration cfg) {
        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("two-tft", cfg, DRL_TWO_RULES);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);

            ks.fireAllRules();
            assertThat(results).containsExactlyInAnyOrder("subject", "sibling");

            results.clear();
            FactHandle fh1 = ks.insert(new Container("blocked"));
            ks.fireAllRules();
            assertThat(results).as("subject blocked, sibling unaffected").isEmpty();

            results.clear();
            ks.delete(fh1);
            ks.fireAllRules();
            assertThat(results).as("subject FALSE→TRUE; sibling TRUE→TRUE").containsExactly("subject");

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
