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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

public class SegmentPosMaskOverflowTest {

    private static final int RULE_COUNT = 40;

    private static final int NOT_PATTERN_INDEX = 32;

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    private static String buildDrl() {
        StringBuilder drl = new StringBuilder();
        drl.append("package org.drools.compiler.integrationtests.segmentposmask;\n");
        drl.append("global java.util.List results;\n");
        for (int rule = 0; rule < RULE_COUNT; rule++) {
            drl.append("rule \"rule").append(rule).append("\"\n");
            drl.append("when\n");
            for (int pattern = 0; pattern <= rule; pattern++) {
                if (pattern == 0) {
                    drl.append("    $first : Integer( this == 0 )\n");
                } else if (pattern == NOT_PATTERN_INDEX) {
                    drl.append("    not Long( this == $first )\n");
                } else {
                    drl.append("    Integer( this == ").append(pattern).append(" )\n");
                }
            }
            drl.append("then\n");
            drl.append("    results.add(\"rule").append(rule).append("\");\n");
            drl.append("end\n");
        }
        return drl.toString();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testRuleWithMoreThan32SegmentsFires(KieBaseTestConfiguration cfg) {
        String drl = buildDrl();
        System.out.println("=== DRL (" + cfg + ") ===");
        System.out.println(drl);

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("segment-pos-mask-overflow", cfg, drl);
        KieSession ks = kbase.newKieSession();
        try {
            List<String> results = new ArrayList<>();
            ks.setGlobal("results", results);

            for (int i = 0; i < RULE_COUNT; i++) {
                if (i != NOT_PATTERN_INDEX) {
                    ks.insert(i);
                }
            }
            ks.fireAllRules();

            Set<String> expected = new HashSet<>();
            for (int rule = 0; rule < RULE_COUNT; rule++) {
                expected.add("rule" + rule);
            }

            Set<String> missing = new LinkedHashSet<>();
            for (int rule = 0; rule < RULE_COUNT; rule++) {
                if (!results.contains("rule" + rule)) {
                    missing.add("rule" + rule);
                }
            }
            System.out.println("=== Fired rules (" + results.size() + "/" + RULE_COUNT + ") ===");
            System.out.println(results);
            System.out.println("=== Rules that did NOT fire (" + missing.size() + ") ===");
            System.out.println(missing);

            assertThat(results)
                    .as("Every rule must fire once its patterns are satisfied.")
                    .containsExactlyInAnyOrderElementsOf(expected);
        } finally {
            ks.dispose();
        }
    }
}
