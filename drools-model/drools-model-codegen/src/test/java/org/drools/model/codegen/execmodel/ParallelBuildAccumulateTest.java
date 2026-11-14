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
package org.drools.model.codegen.execmodel;

import org.drools.model.codegen.ExecutableModelProject;
import org.drools.model.codegen.execmodel.domain.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kie.api.KieServices;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.io.ResourceType;
import org.kie.internal.builder.conf.ParallelRulesBuildThresholdOption;
import org.kie.internal.utils.KieHelper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfSystemProperty(named = "runTurtleTests", matches = "true")
class ParallelBuildAccumulateTest {

    private static final int BUILD_ATTEMPTS = 50;
    private static final int RULE_COUNT = 8;
    private static final int PARALLEL_RULES_BUILD_THRESHOLD = 4;

    @Test
    void buildExecutableModelWithParallelInlineAccumulates() {
        assumeTrue(Runtime.getRuntime().availableProcessors() > 1,
                   "Parallel rules build requires more than one available processor");

        assertBuildDoesNotThrow(createInlineAccumulateDrl(RULE_COUNT));
    }

    @Test
    void buildExecutableModelWithParallelBuiltInAccumulates() {
        assumeTrue(Runtime.getRuntime().availableProcessors() > 1,
                   "Parallel rules build requires more than one available processor");

        assertBuildDoesNotThrow(createBuiltInAccumulateDrl(RULE_COUNT));
    }

    private void assertBuildDoesNotThrow(String drl) {
        for (int i = 0; i < BUILD_ATTEMPTS; i++) {
            int buildAttempt = i;
            assertThatCode(() -> buildExecutableModel(drl)).as("Build attempt %d should not throw an exception", buildAttempt).doesNotThrowAnyException();
        }
    }

    private void buildExecutableModel(String drl) {
        new KieHelper()
                .setKieModuleModel(kieModuleModelWithParallelRulesBuildThreshold())
                .addContent(drl, ResourceType.DRL)
                .build(ExecutableModelProject.class);
    }

    private KieModuleModel kieModuleModelWithParallelRulesBuildThreshold() {
        KieModuleModel kieModuleModel = KieServices.Factory.get().newKieModuleModel();
        kieModuleModel.setConfigurationProperty(ParallelRulesBuildThresholdOption.PROPERTY_NAME,
                                                String.valueOf(PARALLEL_RULES_BUILD_THRESHOLD));
        return kieModuleModel;
    }

    private String createInlineAccumulateDrl(int ruleCount) {
        StringBuilder drl = new StringBuilder();
        drl.append("package org.drools.model.codegen.execmodel;\n")
                .append("import ").append(Person.class.getCanonicalName()).append(";\n")
                .append("global java.util.List list;\n\n");

        for (int i = 0; i < ruleCount; i++) {
            int initialValue = i * 3;
            drl.append("rule \"accumulate").append(i).append("\"\n")
                    .append("when\n")
                    .append("    $person : Person(name == \"").append(i).append("\")\n")
                    .append("    $totalAge : Number() from accumulate(Person($age : age),\n")
                    .append("                              init(double total = ").append(initialValue).append(";),\n")
                    .append("                              action(total += $age;),\n")
                    .append("                              reverse(total -= $age;),\n")
                    .append("                              result(total))\n")
                    .append("then\n")
                    .append("    list.add(drools.getRule().getName());\n")
                    .append("end\n\n");
        }

        return drl.toString();
    }

    private String createBuiltInAccumulateDrl(int ruleCount) {
        StringBuilder drl = new StringBuilder();
        drl.append("package org.drools.model.codegen.execmodel;\n")
                .append("import ").append(Person.class.getCanonicalName()).append(";\n")
                .append("global java.util.List list;\n\n");

        for (int i = 0; i < ruleCount; i++) {
            drl.append("rule \"builtInAccumulate").append(i).append("\"\n")
                    .append("when\n")
                    .append("    $person : Person(name == \"").append(i).append("\")\n")
                    .append("    $totalAge : Number() from accumulate(Person($age : age), sum($age))\n")
                    .append("then\n")
                    .append("    list.add(drools.getRule().getName());\n")
                    .append("end\n\n");
        }

        return drl.toString();
    }
}
