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
package org.drools.compiler.integrationtests.drl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.drools.core.common.InternalAgenda;
import org.drools.testcoverage.common.model.Cheese;
import org.drools.testcoverage.common.model.Person;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.command.KieCommands;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.RuleFlowGroupActivatedEvent;
import org.kie.api.runtime.ExecutionResults;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

public class RuleFlowGroupTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testRuleFlowGroupWithLockOnActivate(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // JBRULES-3590
        final String drl = "import " + Person.class.getCanonicalName() + ";\n" +
                "import " + Cheese.class.getCanonicalName() + ";\n" +
                "rule R1\n" +
                "ruleflow-group \"group1\"\n" +
                "lock-on-active true\n" +
                "when\n" +
                "   $p : Person()\n" +
                "then\n" +
                "   $p.setName(\"John\");\n" +
                "   update ($p);\n" +
                "end\n" +
                "rule R2\n" +
                "ruleflow-group \"group1\"\n" +
                "lock-on-active true\n" +
                "when\n" +
                "   $p : Person( name == null )\n" +
                "   forall ( Cheese ( type == \"cheddar\" ))\n" +
                "then\n" +
                "end";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("ruleflowgroup-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            ksession.insert(new Person());
            ksession.insert(new Cheese("gorgonzola"));
            ((InternalAgenda) ksession.getAgenda()).activateRuleFlowGroup("group1");
            assertThat(ksession.fireAllRules()).isEqualTo(1);
        } finally {
            ksession.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testActivateFromJava(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl =
                "import " + Person.class.getCanonicalName() + ";\n" +
                "rule R1\n" +
                "ruleflow-group \"group1\"\n" +
                "when\n" +
                "   $p : Person()\n" +
                "then\n" +
                "   $p.setName(\"activated\");\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("ruleflowgroup-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            final Person person = new Person();
            ksession.insert(person);
            ksession.getAgenda().getRuleFlowGroup("group1").activate();
            assertThat(ksession.fireAllRules()).isEqualTo(1);
            assertThat(person.getName()).isEqualTo("activated");
        } finally {
            ksession.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testActivateFiresRuleFlowGroupActivatedEvent(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl =
                "import " + Person.class.getCanonicalName() + ";\n" +
                "rule R1\n" +
                "ruleflow-group \"group1\"\n" +
                "when\n" +
                "   $p : Person()\n" +
                "then\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("ruleflowgroup-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            final List<String> events = new ArrayList<>();
            ksession.addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {
                    events.add("before:" + event.getRuleFlowGroup().getName());
                }
                @Override
                public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {
                    events.add("after:" + event.getRuleFlowGroup().getName());
                }
            });

            ksession.insert(new Person());
            ksession.getAgenda().getRuleFlowGroup("group1").activate();
            assertThat(events).containsExactly("before:group1", "after:group1");
        } finally {
            ksession.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testActivateTwoGroupsStackOrder(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl =
                "import " + Person.class.getCanonicalName() + ";\n" +
                "global java.util.List results;\n" +
                "rule R1\n" +
                "ruleflow-group \"group1\"\n" +
                "when\n" +
                "   $p : Person()\n" +
                "then\n" +
                "   results.add(\"group1\");\n" +
                "end\n" +
                "rule R2\n" +
                "ruleflow-group \"group2\"\n" +
                "when\n" +
                "   $p : Person()\n" +
                "then\n" +
                "   results.add(\"group2\");\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("ruleflowgroup-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            final List<String> results = new ArrayList<>();
            ksession.setGlobal("results", results);
            ksession.insert(new Person());
            ksession.getAgenda().getRuleFlowGroup("group1").activate();
            ksession.getAgenda().getRuleFlowGroup("group2").activate();
            ksession.fireAllRules();
            // group2 was pushed last, so it fires first (stack order)
            assertThat(results).containsExactly("group2", "group1");
        } finally {
            ksession.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testActivateRuleFlowGroupFromRHS(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl =
                "import " + Person.class.getCanonicalName() + ";\n" +
                "global java.util.List results;\n" +
                "rule R1\n" +
                "ruleflow-group \"group1\"\n" +
                "when\n" +
                "   $p : Person()\n" +
                "then\n" +
                "   results.add(\"R1\");\n" +
                "   drools.activateRuleFlowGroup(\"group2\");\n" +
                "end\n" +
                "rule R2\n" +
                "ruleflow-group \"group2\"\n" +
                "when\n" +
                "   $p : Person()\n" +
                "then\n" +
                "   results.add(\"R2\");\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("ruleflowgroup-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            final List<String> results = new ArrayList<>();
            ksession.setGlobal("results", results);
            ksession.insert(new Person());
            ksession.getAgenda().getRuleFlowGroup("group1").activate();
            ksession.fireAllRules();
            assertThat(results).containsExactly("R1", "R2");
        } finally {
            ksession.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testActivateRuleFlowGroupFromRHSWithMvelDialect(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl =
                "import " + Person.class.getCanonicalName() + ";\n" +
                "global java.util.List results;\n" +
                "rule R1\n" +
                "ruleflow-group \"group1\"\n" +
                "dialect \"mvel\"\n" +
                "when\n" +
                "   $p : Person()\n" +
                "then\n" +
                "   results.add(\"R1\");\n" +
                "   drools.activateRuleFlowGroup(\"group2\");\n" +
                "end\n" +
                "rule R2\n" +
                "ruleflow-group \"group2\"\n" +
                "when\n" +
                "   $p : Person()\n" +
                "then\n" +
                "   results.add(\"R2\");\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("ruleflowgroup-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            final List<String> results = new ArrayList<>();
            ksession.setGlobal("results", results);
            ksession.insert(new Person());
            ksession.getAgenda().getRuleFlowGroup("group1").activate();
            ksession.fireAllRules();
            assertThat(results).containsExactly("R1", "R2");
        } finally {
            ksession.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testActivateRuleFlowGroupCommandInBatch(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl =
                "import " + Person.class.getCanonicalName() + ";\n" +
                "rule R1\n" +
                "ruleflow-group \"group1\"\n" +
                "when\n" +
                "   $p : Person()\n" +
                "then\n" +
                "   $p.setName(\"activated\");\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("ruleflowgroup-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            final KieCommands commands = KieServices.get().getCommands();
            final Person person = new Person();
            final ExecutionResults results = ksession.execute(commands.newBatchExecution(Arrays.asList(
                    commands.newInsert(person),
                    commands.newActivateRuleFlowGroup("group1"),
                    commands.newFireAllRules("fired"))));
            assertThat(results.getValue("fired")).isEqualTo(1);
            assertThat(person.getName()).isEqualTo("activated");
        } finally {
            ksession.dispose();
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testClearOnActivatedGroup(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl =
                "import " + Person.class.getCanonicalName() + ";\n" +
                "rule R1\n" +
                "ruleflow-group \"group1\"\n" +
                "when\n" +
                "   $p : Person()\n" +
                "then\n" +
                "   $p.setName(\"fired\");\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("ruleflowgroup-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            final Person person = new Person();
            ksession.insert(person);
            ksession.getAgenda().getRuleFlowGroup("group1").activate();
            ksession.getAgenda().getRuleFlowGroup("group1").clear();
            assertThat(ksession.fireAllRules()).isEqualTo(0);
            assertThat(person.getName()).isNotEqualTo("fired");
        } finally {
            ksession.dispose();
        }
    }

}
