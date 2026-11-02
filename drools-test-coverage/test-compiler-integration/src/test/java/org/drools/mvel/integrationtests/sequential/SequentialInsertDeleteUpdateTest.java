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
package org.drools.mvel.integrationtests.sequential;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.drools.mvel.compiler.Cheese;
import org.drools.mvel.compiler.Person;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.builder.KieModule;
import org.kie.api.conf.EqualityBehaviorOption;
import org.kie.api.conf.SequentialOption;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.internal.command.CommandFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests how insert, delete and update in the RHS work in sequential mode.
 * <p>
 * In sequential mode, rules are evaluated one by one in salience and load order, and each rule is evaluated only once.
 * A rule which has not been evaluated yet sees the working memory changes made by previously fired rules.
 * A rule which has already been evaluated is not re-evaluated.
 *
 * @see <a href="https://github.com/apache/incubator-kie/issues/7221">incubator-kie#7221</a>
 */
public class SequentialInsertDeleteUpdateTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    // =====================================================================
    // insert tests
    // =====================================================================

    /**
     * R2 has not been evaluated yet, so it matches the Cheese inserted by R1.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertMatchedByLaterRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    insert( new Cheese( \"stilton\", 10 ) );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    list.add( \"R2:\" + $c.getType() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0));
        assertThat(list).containsExactly("R1", "R2:stilton");
    }

    /**
     * R1 has already been evaluated when R2 inserts a Cheese, so R1 is not re-evaluated.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertNotMatchedByEarlierRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    list.add( \"R1:\" + $c.getType() );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    insert( new Cheese( \"stilton\", 10 ) );\n" +
                "    list.add( \"R2\" );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0));
        assertThat(list).containsExactly("R2");
    }

    /**
     * R1 inserts a Person which matches R1 itself. R1 is not re-evaluated, so it doesn't loop even without no-loop.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertNotMatchedBySameRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    insert( new Person( $p.getName() + \"_child\", \"stilton\", 0 ) );\n" +
                "    list.add( \"R1:\" + $p.getName() );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    list.add( \"R2:\" + $p.getName() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0));
        assertThat(list).containsExactlyInAnyOrder("R1:p1", "R2:p1", "R2:p1_child");
        assertThat(list.get(0)).isEqualTo("R1:p1");
    }

    /**
     * Inserting an object which is already in the working memory doesn't create another fact.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertSameObjectAgain(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    insert( $c );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    list.add( \"R2:\" + $c.getType() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Cheese("stilton", 10));
        assertThat(list).containsExactly("R1", "R2:stilton");
    }

    /**
     * 2 Persons share a Cheese, and R1 inserts it for each Person. Only the first insert creates a fact.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertSharedObjectInRhs(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    insert( $p.getCheese() );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    list.add( \"R2:\" + $c.getType() );\n" +
                "end\n";

        Cheese cheese = new Cheese("stilton", 10);
        Person p1 = new Person("p1", "stilton", 0);
        p1.setCheese(cheese);
        Person p2 = new Person("p2", "stilton", 0);
        p2.setCheese(cheese);

        List<String> list = executeSequential(str, kieBaseTestConfiguration, p1, p2);
        assertThat(list).containsExactly("R2:stilton");
    }

    /**
     * The same object is passed twice as an initial fact. It doesn't depend on the RHS.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertSameObjectTwiceAsInitialFact(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    list.add( \"R1:\" + $c.getType() );\n" +
                "end\n";

        Cheese cheese = new Cheese("stilton", 10);
        List<String> list = executeSequential(str, kieBaseTestConfiguration, cheese, cheese);
        assertThat(list).containsExactly("R1:stilton");
    }

    /**
     * With equality behavior, an object equal to one in the working memory doesn't create another fact.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertEqualObjectWithEqualityBehavior(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    list.add( \"R1:\" + $c.getType() );\n" +
                "end\n";

        List<String> list = executeSequentialWithEquality(str, kieBaseTestConfiguration, new Cheese("stilton", 10), new Cheese("stilton", 10));
        assertThat(list).containsExactly("R1:stilton");
    }

    /**
     * JoinNode - right insert: R1 inserts a Cheese which joins with the existing Person in R2.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertJoinRight(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    insert( new Cheese( $p.getLikes(), 10 ) );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "    $c : Cheese( type == $p.likes )\n" +
                "then\n" +
                "    list.add( \"R2:\" + $p.getName() + \":\" + $c.getType() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0));
        assertThat(list).containsExactly("R1", "R2:p1:stilton");
    }

    /**
     * JoinNode - left insert: R1 inserts a Person which joins with the existing Cheese in R2.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertJoinLeft(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    insert( new Person( \"p1\", $c.getType(), 0 ) );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "    $c : Cheese( type == $p.likes )\n" +
                "then\n" +
                "    list.add( \"R2:\" + $p.getName() + \":\" + $c.getType() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Cheese("stilton", 10));
        assertThat(list).containsExactly("R1", "R2:p1:stilton");
    }

    /**
     * NotNode - right insert: R1 inserts a cheddar Cheese, which blocks R2's "not Cheese( type == 'cheddar' )".
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertWithNot(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    insert( new Cheese( \"cheddar\", 10 ) );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "    not Cheese( type == \"cheddar\" )\n" +
                "then\n" +
                "    list.add( \"R2\" );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0));
        assertThat(list).containsExactly("R1");
    }

    /**
     * ExistsNode - right insert: R1 inserts a stilton Cheese, which satisfies R2's "exists Cheese( type == 'stilton' )".
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertWithExists(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    insert( new Cheese( \"stilton\", 10 ) );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "    exists Cheese( type == \"stilton\" )\n" +
                "then\n" +
                "    list.add( \"R2\" );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0));
        assertThat(list).containsExactly("R1", "R2");
    }

    /**
     * AccumulateNode - right insert: R1 inserts a Cheese, which is included in R2's accumulate.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertWithAccumulate(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    insert( new Cheese( \"stilton\", 20 ) );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "    $total : Number() from accumulate(\n" +
                "        Cheese( type == $p.likes, $price : price ),\n" +
                "        sum( $price )\n" +
                "    )\n" +
                "then\n" +
                "    list.add( \"R2:\" + $total.intValue() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0), new Cheese("stilton", 10));
        assertThat(list).containsExactly("R1", "R2:30");
    }

    // =====================================================================
    // delete tests
    // =====================================================================

    /**
     * R2 has not been evaluated yet, so it doesn't match the Cheese deleted by R1.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testDeleteNotMatchedByLaterRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $c : Cheese( type == \"stilton\" )\n" +
                "then\n" +
                "    delete( $c );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    list.add( \"R2:\" + $c.getType() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Cheese("stilton", 10), new Cheese("cheddar", 10));
        assertThat(list).containsExactly("R1", "R2:cheddar");
    }

    /**
     * R1 has already fired for the Cheese when R2 deletes it. The deletion doesn't affect R1.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testDeleteAfterEarlierRuleFired(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    list.add( \"R1:\" + $c.getType() );\n" +
                "end\n" +
                "rule R2 salience 50 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    delete( $c );\n" +
                "    list.add( \"R2:\" + $c.getType() );\n" +
                "end\n" +
                "rule R3 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    list.add( \"R3:\" + $c.getType() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Cheese("stilton", 10));
        assertThat(list).containsExactly("R1:stilton", "R2:stilton");
    }

    /**
     * A single-use Coupon is deleted when it is applied. R1 has 2 matches (one per Order) with the same Coupon.
     * The first firing deletes the Coupon, but R1 is not re-evaluated, so the other match is not cancelled.
     * It differs from non-sequential mode, where the Coupon is applied to only 1 Order.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testDeleteFactOfPendingMatchOfSameRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Order.class.getCanonicalName() + "\n" +
                "import " + Coupon.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 when\n" +
                "    $o : Order( discounted == false )\n" +
                "    $c : Coupon()\n" +
                "then\n" +
                "    modify( $o ) { setTotal( $o.getTotal() - $c.getDiscount() ), setDiscounted( true ) };\n" +
                "    delete( $c );\n" +
                "    list.add( \"R1:\" + $o.getId() + \":\" + $c.getCode() );\n" +
                "end\n";

        Order o1 = new Order("o1", 100);
        Order o2 = new Order("o2", 100);
        List<String> list = executeSequential(str, kieBaseTestConfiguration, o1, o2, new Coupon("A", 10));
        assertThat(list).containsExactly("R1:o1:A", "R1:o2:A");
        assertThat(Stream.of(o1, o2).filter(Order::isDiscounted)).hasSize(2);
    }

    /**
     * JoinNode - right delete: R1 deletes the Cheese which is part of R2's match.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testDeleteJoinRight(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $c : Cheese( type == \"stilton\" )\n" +
                "then\n" +
                "    delete( $c );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "    $c : Cheese( type == $p.likes )\n" +
                "then\n" +
                "    list.add( \"R2:\" + $p.getName() + \":\" + $c.getType() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration,
                                              new Person("p1", "stilton", 0), new Person("p2", "cheddar", 0),
                                              new Cheese("stilton", 10), new Cheese("cheddar", 10));
        assertThat(list).containsExactly("R1", "R2:p2:cheddar");
    }

    /**
     * JoinNode - left delete: R1 deletes the Person which is part of R2's match.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testDeleteJoinLeft(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $p : Person( name == \"p1\" )\n" +
                "then\n" +
                "    delete( $p );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "    $c : Cheese( type == $p.likes )\n" +
                "then\n" +
                "    list.add( \"R2:\" + $p.getName() + \":\" + $c.getType() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration,
                                              new Person("p1", "stilton", 0), new Person("p2", "cheddar", 0),
                                              new Cheese("stilton", 10), new Cheese("cheddar", 10));
        assertThat(list).containsExactly("R1", "R2:p2:cheddar");
    }

    /**
     * NotNode - right delete: R1 deletes the cheddar Cheese, which unblocks R2's "not Cheese( type == 'cheddar' )".
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testDeleteWithNot(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $c : Cheese( type == \"cheddar\" )\n" +
                "then\n" +
                "    delete( $c );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "    not Cheese( type == \"cheddar\" )\n" +
                "then\n" +
                "    list.add( \"R2\" );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0), new Cheese("cheddar", 10));
        assertThat(list).containsExactly("R1", "R2");
    }

    /**
     * ExistsNode - right delete: R1 deletes the only stilton Cheese, so R2's "exists Cheese( type == 'stilton' )" fails.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testDeleteWithExists(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $c : Cheese( type == \"stilton\" )\n" +
                "then\n" +
                "    delete( $c );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "    exists Cheese( type == \"stilton\" )\n" +
                "then\n" +
                "    list.add( \"R2\" );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0), new Cheese("stilton", 10));
        assertThat(list).containsExactly("R1");
    }

    /**
     * AccumulateNode - right delete: R1 deletes a Cheese, which is excluded from R2's accumulate.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testDeleteWithAccumulate(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $c : Cheese( price == 20 )\n" +
                "then\n" +
                "    delete( $c );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "    $total : Number() from accumulate(\n" +
                "        Cheese( type == $p.likes, $price : price ),\n" +
                "        sum( $price )\n" +
                "    )\n" +
                "then\n" +
                "    list.add( \"R2:\" + $total.intValue() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration,
                                              new Person("p1", "stilton", 0), new Cheese("stilton", 10), new Cheese("stilton", 20));
        assertThat(list).containsExactly("R1", "R2:10");
    }

    /**
     * R1 inserts a Cheese and R2 deletes it. R3 doesn't match the Cheese.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testDeleteInsertedFact(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    insert( new Cheese( \"stilton\", 10 ) );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 salience 50 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    delete( $c );\n" +
                "    list.add( \"R2:\" + $c.getType() );\n" +
                "end\n" +
                "rule R3 when\n" +
                "    $p : Person()\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    list.add( \"R3:\" + $c.getType() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0));
        assertThat(list).containsExactly("R1", "R2:stilton");
    }

    // =====================================================================
    // update tests
    // =====================================================================

    /**
     * R1 changes the age with a setter and calls update(). R2 matches the new value and R3 doesn't match the old value.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testUpdateMatchedByLaterRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $p : Person( age == 0 )\n" +
                "then\n" +
                "    $p.setAge( 1 );\n" +
                "    update( $p );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 salience 50 when\n" +
                "    $p : Person( age == 1 )\n" +
                "then\n" +
                "    list.add( \"R2\" );\n" +
                "end\n" +
                "rule R3 when\n" +
                "    $p : Person( age == 0 )\n" +
                "then\n" +
                "    list.add( \"R3\" );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0));
        assertThat(list).containsExactly("R1", "R2");
    }

    /**
     * R1 has already been evaluated when R2 updates the Person, so R1 is not re-evaluated.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testUpdateNotMatchedByEarlierRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $p : Person( age == 1 )\n" +
                "then\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person( age == 0 )\n" +
                "then\n" +
                "    $p.setAge( 1 );\n" +
                "    update( $p );\n" +
                "    list.add( \"R2\" );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0));
        assertThat(list).containsExactly("R2");
    }

    /**
     * R1 updates the Person, which still matches R1 itself. R1 is not re-evaluated, so it doesn't loop even without no-loop.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testUpdateNotMatchedBySameRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 when\n" +
                "    $p : Person( age >= 0 )\n" +
                "then\n" +
                "    $p.setAge( $p.getAge() + 1 );\n" +
                "    update( $p );\n" +
                "    list.add( \"R1:\" + $p.getAge() );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    list.add( \"R2:\" + $p.getAge() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0));
        assertThat(list).containsExactly("R1:1", "R2:1");
    }

    /**
     * Only one Coupon can be applied to an Order. R1 has 2 matches (one per Coupon) with the same Order.
     * The first firing updates the Order to discounted, but R1 is not re-evaluated, so the other match is not cancelled.
     * It differs from non-sequential mode, where only 1 Coupon is applied.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testUpdateFactOfPendingMatchOfSameRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Order.class.getCanonicalName() + "\n" +
                "import " + Coupon.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 when\n" +
                "    $o : Order( discounted == false )\n" +
                "    $c : Coupon()\n" +
                "then\n" +
                "    modify( $o ) { setTotal( $o.getTotal() - $c.getDiscount() ), setDiscounted( true ) };\n" +
                "    list.add( \"R1:\" + $o.getId() + \":\" + $c.getCode() );\n" +
                "end\n";

        Order o1 = new Order("o1", 100);
        List<String> list = executeSequential(str, kieBaseTestConfiguration, o1, new Coupon("A", 10), new Coupon("B", 20));
        assertThat(list).containsExactly("R1:o1:A", "R1:o1:B");
        assertThat(o1.getTotal()).isEqualTo(70);
    }

    /**
     * JoinNode - right update: R1 updates the Cheese type, so R2 joins the Person with the updated Cheese.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testUpdateJoin(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $c : Cheese( type == \"stilton\" )\n" +
                "then\n" +
                "    $c.setType( \"cheddar\" );\n" +
                "    update( $c );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "    $c : Cheese( type == $p.likes )\n" +
                "then\n" +
                "    list.add( \"R2:\" + $p.getName() + \":\" + $c.getType() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration,
                                              new Person("p1", "stilton", 0), new Person("p2", "cheddar", 0), new Cheese("stilton", 10));
        assertThat(list).containsExactly("R1", "R2:p2:cheddar");
    }

    /**
     * NotNode / ExistsNode - right update: R1 updates the Cheese from stilton to cheddar.
     * R2's "not Cheese( type == 'stilton' )" is unblocked and R3's "exists Cheese( type == 'stilton' )" fails.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testUpdateWithNotAndExists(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $c : Cheese( type == \"stilton\" )\n" +
                "then\n" +
                "    $c.setType( \"cheddar\" );\n" +
                "    update( $c );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 salience 50 when\n" +
                "    $p : Person()\n" +
                "    not Cheese( type == \"stilton\" )\n" +
                "then\n" +
                "    list.add( \"R2\" );\n" +
                "end\n" +
                "rule R3 when\n" +
                "    $p : Person()\n" +
                "    exists Cheese( type == \"stilton\" )\n" +
                "then\n" +
                "    list.add( \"R3\" );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0), new Cheese("stilton", 10));
        assertThat(list).containsExactly("R1", "R2");
    }

    /**
     * AccumulateNode - right update: R1 updates the Cheese price, so R2's accumulate uses the updated price.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testUpdateWithAccumulate(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $c : Cheese( price == 20 )\n" +
                "then\n" +
                "    $c.setPrice( 50 );\n" +
                "    update( $c );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "    $total : Number() from accumulate(\n" +
                "        Cheese( type == $p.likes, $price : price ),\n" +
                "        sum( $price )\n" +
                "    )\n" +
                "then\n" +
                "    list.add( \"R2:\" + $total.intValue() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration,
                                              new Person("p1", "stilton", 0), new Cheese("stilton", 10), new Cheese("stilton", 20));
        assertThat(list).containsExactly("R1", "R2:60");
    }

    // =====================================================================
    // insert / delete / update combined tests
    // =====================================================================

    /**
     * R1 inserts, R2 updates and R3 deletes. Each rule sees the changes made by the previously fired rules.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertUpdateDeleteChain(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 salience 100 when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    insert( new Cheese( \"stilton\", 10 ) );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 salience 90 when\n" +
                "    $c : Cheese( price == 10 )\n" +
                "then\n" +
                "    modify( $c ) { setPrice( 20 ) };\n" +
                "    list.add( \"R2\" );\n" +
                "end\n" +
                "rule R3 salience 80 when\n" +
                "    $p : Person()\n" +
                "    $c : Cheese( type == $p.likes, price == 20 )\n" +
                "then\n" +
                "    list.add( \"R3:\" + $c.getPrice() );\n" +
                "end\n" +
                "rule R4 salience 70 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    delete( $c );\n" +
                "    list.add( \"R4\" );\n" +
                "end\n" +
                "rule R5 salience 60 when\n" +
                "    $p : Person()\n" +
                "    not Cheese()\n" +
                "then\n" +
                "    list.add( \"R5\" );\n" +
                "end\n" +
                "rule R6 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    list.add( \"R6\" );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0));
        assertThat(list).containsExactly("R1", "R2", "R3:20", "R4", "R5");
    }

    /**
     * Without salience, rules are evaluated in load order. R2 sees the Cheese inserted by R1,
     * but R1 doesn't see the Person inserted by R2.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertWithLoadOrder(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R1 when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    insert( new Cheese( \"stilton\", 10 ) );\n" +
                "    list.add( \"R1:\" + $p.getName() );\n" +
                "end\n" +
                "rule R2 when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    insert( new Person( \"p2\", \"stilton\", 0 ) );\n" +
                "    list.add( \"R2:\" + $c.getType() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0));
        assertThat(list).containsExactly("R1:p1", "R2:stilton");
    }

    // =====================================================================
    // agenda-group tests
    // =====================================================================

    /**
     * g1 has the focus first. A rule in g1 inserts a Cheese, which is matched by a rule in g2 evaluated later.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertMatchedByLaterAgendaGroup(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R0 when\n" +
                "then\n" +
                "    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( \"g2\" ).setFocus();\n" +
                "    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( \"g1\" ).setFocus();\n" +
                "end\n" +
                "rule R1 agenda-group \"g1\" when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    insert( new Cheese( \"stilton\", 10 ) );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 agenda-group \"g2\" when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    list.add( \"R2:\" + $c.getType() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0));
        assertThat(list).containsExactly("R1", "R2:stilton");
    }

    /**
     * g2 has the focus first. A rule in g1 inserts a Cheese, but g2 has already been visited, so the rule in g2 is not re-evaluated.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testInsertNotMatchedByVisitedAgendaGroup(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R0 when\n" +
                "then\n" +
                "    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( \"g1\" ).setFocus();\n" +
                "    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( \"g2\" ).setFocus();\n" +
                "end\n" +
                "rule R1 agenda-group \"g1\" when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    insert( new Cheese( \"stilton\", 10 ) );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 agenda-group \"g2\" when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    list.add( \"R2:\" + $c.getType() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Person("p1", "stilton", 0));
        assertThat(list).containsExactly("R1");
    }

    /**
     * g1 has the focus first. A rule in g1 deletes a Cheese, so a rule in g2 evaluated later doesn't match it.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testDeleteNotMatchedByLaterAgendaGroup(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import " + Cheese.class.getCanonicalName() + "\n" +
                "global " + List.class.getCanonicalName() + " list\n" +
                "rule R0 when\n" +
                "then\n" +
                "    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( \"g2\" ).setFocus();\n" +
                "    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( \"g1\" ).setFocus();\n" +
                "end\n" +
                "rule R1 agenda-group \"g1\" when\n" +
                "    $c : Cheese( type == \"stilton\" )\n" +
                "then\n" +
                "    delete( $c );\n" +
                "    list.add( \"R1\" );\n" +
                "end\n" +
                "rule R2 agenda-group \"g2\" when\n" +
                "    $c : Cheese()\n" +
                "then\n" +
                "    list.add( \"R2:\" + $c.getType() );\n" +
                "end\n";

        List<String> list = executeSequential(str, kieBaseTestConfiguration, new Cheese("stilton", 10), new Cheese("cheddar", 10));
        assertThat(list).containsExactly("R1", "R2:cheddar");
    }

    // =====================================================================
    // Helper methods
    // =====================================================================

    private List<String> executeSequential(String drl, KieBaseTestConfiguration kieBaseTestConfiguration, Object... facts) {
        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(
                kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        return execute(kbase, facts);
    }

    private List<String> executeSequentialWithEquality(String drl, KieBaseTestConfiguration kieBaseTestConfiguration, Object... facts) {
        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, drl);
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(
                kieModule, kieBaseTestConfiguration, SequentialOption.YES, EqualityBehaviorOption.EQUALITY);
        return execute(kbase, facts);
    }

    private List<String> execute(KieBase kbase, Object... facts) {
        StatelessKieSession ksession = kbase.newStatelessKieSession();

        final List<String> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        ksession.execute(CommandFactory.newInsertElements(Arrays.asList(facts)));
        return list;
    }

    // =====================================================================
    // Model classes
    // =====================================================================

    public static class Order {

        private final String id;
        private int total;
        private boolean discounted;

        public Order(String id, int total) {
            this.id = id;
            this.total = total;
        }

        public String getId() {
            return id;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public boolean isDiscounted() {
            return discounted;
        }

        public void setDiscounted(boolean discounted) {
            this.discounted = discounted;
        }
    }

    public static class Coupon {

        private final String code;
        private final int discount;

        public Coupon(String code, int discount) {
            this.code = code;
            this.discount = discount;
        }

        public String getCode() {
            return code;
        }

        public int getDiscount() {
            return discount;
        }
    }
}
