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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.stream.Stream;

import org.drools.base.base.ValueResolver;
import org.drools.base.base.ValueType;
import org.drools.compiler.rule.builder.EvaluatorDefinition;
import org.drools.drl.parser.DrlParser;
import org.drools.drl.parser.impl.Operator;
import org.drools.base.rule.accessor.Evaluator;
import org.drools.base.rule.accessor.FieldValue;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.mvel.evaluators.BaseEvaluator;
import org.drools.mvel.evaluators.VariableRestriction;
import org.drools.testcoverage.common.model.Address;
import org.drools.testcoverage.common.model.Person;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.builder.conf.EvaluatorOption;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomOperatorTest {

    private static final String SUPERSET_OF = DrlParser.ANTLR4_PARSER_ENABLED ? "##supersetOf" : "supersetOf";

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testCustomOperatorUsingCollections(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String constraints =
                "    $alice : Person(name == \"Alice\")\n" +
                        "    $bob : Person(name == \"Bob\", addresses " + SUPERSET_OF + " $alice.addresses)\n";
        customOperatorUsingCollections(kieBaseTestConfiguration, constraints);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testCustomOperatorUsingCollectionsWithNot(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String constraints =
                "    $alice : Person(name == \"Alice\")\n" +
                        "    $bob : Person(name == \"Bob\", $alice.addresses not " + SUPERSET_OF + " this.addresses)\n";
        customOperatorUsingCollections(kieBaseTestConfiguration, constraints);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testNoOperatorInstancesCreatedAtRuntime(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String constraints =
                "    $alice : Person(name == \"Alice\")\n" +
                        "    $bob : Person(name == \"Bob\", addresses " + SUPERSET_OF + " $alice.addresses)\n" +
                        "    Person(name == \"Bob\", addresses " + SUPERSET_OF + " $alice.addresses)\n";

        customOperatorUsingCollections(kieBaseTestConfiguration, constraints);

        assertThat(SupersetOfEvaluatorDefinition.INSTANCES_COUNTER).isEqualTo(0);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testCustomOperatorUsingCollectionsInverted(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-6983
        String constraints =
                "    $bob : Person(name == \"Bob\")\n" +
                        "    $alice : Person(name == \"Alice\", $bob.addresses " + SUPERSET_OF + " this.addresses)\n";
        customOperatorUsingCollections(kieBaseTestConfiguration, constraints);
    }

    private void customOperatorUsingCollections(KieBaseTestConfiguration kieBaseTestConfiguration, String constraints) {
        final String drl =
                "import " + Address.class.getCanonicalName() + ";\n" +
                        "import " + Person.class.getCanonicalName() + ";\n" +
                        "rule R when\n" +
                        constraints +
                        "then\n" +
                        "end\n";

        System.setProperty(EvaluatorOption.PROPERTY_NAME + "supersetOf", SupersetOfEvaluatorDefinition.class.getName());
        try {
            final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("custom-operator-test", kieBaseTestConfiguration, drl);

            SupersetOfEvaluatorDefinition.INSTANCES_COUNTER = 0;

            final KieSession ksession = kbase.newKieSession();
            try {
                final Person alice = new Person("Alice", 30);
                alice.addAddress(new Address("Large Street", "BigTown", "12345"));
                final Person bob = new Person("Bob", 30);
                bob.addAddress(new Address("Large Street", "BigTown", "12345"));
                bob.addAddress(new Address("Long Street", "SmallTown", "54321"));

                ksession.insert(alice);
                ksession.insert(bob);

                assertThat(ksession.fireAllRules()).isEqualTo(1);
            } finally {
                ksession.dispose();
            }
        } finally {
            System.clearProperty(EvaluatorOption.PROPERTY_NAME + "supersetOf");
        }
    }

    public static class SupersetOfEvaluatorDefinition implements EvaluatorDefinition {

        public static final Operator SUPERSET_OF = Operator.addOperatorToRegistry("supersetOf", false);
        public static final Operator NOT_SUPERSET_OF = Operator.addOperatorToRegistry("supersetOf", true);
        private static final String[] SUPPORTED_IDS = {SUPERSET_OF.getOperatorString()};

        private Evaluator[] evaluator;

        static int INSTANCES_COUNTER = 0;

        public SupersetOfEvaluatorDefinition() {
            INSTANCES_COUNTER++;
        }

        @Override
        public String[] getEvaluatorIds() {
            return SupersetOfEvaluatorDefinition.SUPPORTED_IDS;
        }

        @Override
        public boolean isNegatable() {
            return true;
        }

        @Override
        public Evaluator getEvaluator(final ValueType type, final String operatorId, final boolean isNegated, final String parameterText, final Target leftTarget, final Target rightTarget) {
            return new SupersetOfEvaluator(type, isNegated);
        }

        @Override
        public Evaluator getEvaluator(final ValueType type, final String operatorId, final boolean isNegated, final String parameterText) {
            return getEvaluator(type, operatorId, isNegated, parameterText, Target.FACT, Target.FACT);
        }

        @Override
        public Evaluator getEvaluator(final ValueType type, final Operator operator, final String parameterText) {
            return this.getEvaluator(type, operator.getOperatorString(), operator.isNegated(), parameterText);
        }

        @Override
        public Evaluator getEvaluator(final ValueType type, final Operator operator) {
            return this.getEvaluator(type, operator.getOperatorString(), operator.isNegated(), null);
        }

        @Override
        public boolean supportsType(final ValueType vt) {
            return true;
        }

        @Override
        public Target getTarget() {
            return Target.FACT;
        }

        @Override
        public void writeExternal(final ObjectOutput out) throws IOException {
            out.writeObject(evaluator);
        }

        @Override
        public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
            evaluator = (Evaluator[]) in.readObject();
        }
    }

    public static class SupersetOfEvaluator extends BaseEvaluator {

        public SupersetOfEvaluator(final ValueType type, final boolean isNegated) {
            super(type, isNegated ? SupersetOfEvaluatorDefinition.NOT_SUPERSET_OF : SupersetOfEvaluatorDefinition.SUPERSET_OF);
        }

        // In this method, 'factHandle' is the left operand of the expression. 'value' is the right operand.
        @Override
        public boolean evaluate(final GlobalResolver globalResolver, final ReadAccessor extractor, final FactHandle factHandle, final FieldValue value) {
            final Object objectValue = extractor.getValue(globalResolver, factHandle);
            return evaluateExpression((Collection) objectValue, (Collection) value.getValue());
        }

        // In this method, 'left' and 'right' literally mean the left and right operands of the expression. No need to invert.
        // for example, [addresses supersetOf $alice.addresses]
        //     leftOperandFact.getObject() is addresses
        //     rightOperandFact.getObject() is $alice.addresses
        @Override
        public boolean evaluate(final GlobalResolver globalResolver, final ReadAccessor ira, final FactHandle leftOperandFact, final ReadAccessor ira1, final FactHandle rightOperandFact) {
            return evaluateExpression((Collection) leftOperandFact.getObject(), (Collection) rightOperandFact.getObject());
        }

        // In this method, 'left' means leftInput to the JoinNode. 'right' means RightInput to the JoinNode.
        // To evaluate the expression, RightInput is the left operand and leftInput is the right operand. So, need to invert.
        // for example, [addresses supersetOf $alice.addresses]
        //     valRight is addresses
        //     context.left is $alice.addresses
        @Override
        public boolean evaluateCachedLeft(final GlobalResolver globalResolver, final VariableRestriction.VariableContextEntry context, final FactHandle right) {
            final Object valRight = context.extractor.getValue(globalResolver, right.getObject());
            return evaluateExpression((Collection) valRight, (Collection) ((VariableRestriction.ObjectVariableContextEntry) context).left);
        }

        // In this method, 'left' means leftInput to the JoinNode. 'right' means RightInput to the JoinNode.
        // To evaluate the expression, RightInput is the left operand and leftInput is the right operand. So, need to invert.
        // for example, [addresses supersetOf $alice.addresses]
        //     context.right is addresses
        //     varLeft is $alice.addresses
        @Override
        public boolean evaluateCachedRight(final GlobalResolver globalResolver, final VariableRestriction.VariableContextEntry context, final FactHandle left) {
            final Object varLeft = context.declaration.getExtractor().getValue(globalResolver, left);
            return evaluateExpression((Collection) ((VariableRestriction.ObjectVariableContextEntry) context).right, (Collection) varLeft);
        }

        // In this method, 'left' and 'right' literally mean the left and right operands of the expression.
        // for example, [addresses supersetOf $alice.addresses]
        //     leftOperandCollection is addresses
        //     rightOperandCollection is $alice.addresses
        private boolean evaluateExpression(final Collection leftOperandCollection, final Collection rightOperandCollection) {
            return getOperator().isNegated() ^ leftOperandCollection.containsAll(rightOperandCollection);
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testCustomOperatorOnKieModule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl = "import " + Address.class.getCanonicalName() + ";\n" +
                "import " + Person.class.getCanonicalName() + ";\n" +
                "rule R when\n" +
                "    $alice : Person(name == \"Alice\")\n" +
                "    $bob : Person(name == \"Bob\", addresses " + SUPERSET_OF + " $alice.addresses)\n" +
                "then\n" +
                "end\n";

        System.setProperty(EvaluatorOption.PROPERTY_NAME + "supersetOf", SupersetOfEvaluatorDefinition.class.getName());
        try {
            final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("custom-operator-test", kieBaseTestConfiguration, drl);
            final KieSession ksession = kbase.newKieSession();
            try {
                final Person alice = new Person("Alice", 30);
                alice.addAddress(new Address("Large Street", "BigTown", "12345"));
                final Person bob = new Person("Bob", 30);
                bob.addAddress(new Address("Large Street", "BigTown", "12345"));
                bob.addAddress(new Address("Long Street", "SmallTown", "54321"));

                ksession.insert(alice);
                ksession.insert(bob);

                assertThat(ksession.fireAllRules()).isEqualTo(1);
            } finally {
                ksession.dispose();
            }
        } finally {
            System.clearProperty(EvaluatorOption.PROPERTY_NAME + "supersetOf");
        }
    }
}
