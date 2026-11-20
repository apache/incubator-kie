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

import org.apache.commons.math3.util.Pair;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.reteoo.RuleTerminalNodeLeftTuple;
import org.drools.model.codegen.execmodel.domain.Child;
import org.drools.model.codegen.execmodel.domain.Parent;
import org.drools.model.codegen.execmodel.domain.Person;
import org.drools.model.functions.accumulate.GroupKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.Match;
import org.kie.internal.event.rule.RuleEventListener;
import org.kie.internal.event.rule.RuleEventManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class GroupByTest extends BaseModelTest {

    public void assertSessionHasProperties(RUN_TYPE testRunType, String ruleString, Consumer<KieSession> kieSessionConsumer) {
        if (testRunType.isExecutableModel()) {
            KieSession ksession = getKieSession(testRunType, ruleString);
            kieSessionConsumer.accept(ksession);
        } else {
            assertThatCode(() -> {
                        KieSession ksession = getKieSession(testRunType, "dialect \"java\";\n" + ruleString);
                        kieSessionConsumer.accept(ksession);
                    }).doesNotThrowAnyException();
            assertThatCode(() -> {
                        KieSession ksession = getKieSession(testRunType, "dialect \"mvel\";\n" + ruleString);
                        kieSessionConsumer.accept(ksession);
                    }).doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void providedInstance(RUN_TYPE runType) {
        String str = "import " + Person.class.getCanonicalName() + ";\n" +
                "import " + Map.class.getCanonicalName() + ";\n" +
                "global Map results;\n" +
                "rule X when\n" +
                "groupby( $p: Person (); " +
                "$key : $p.getName().substring(0, 1); " +
                "$sumOfAges : sum($p.getAge()); " +
                "$sumOfAges > 36)" +
                "then\n" +
                "  results.put($key, $sumOfAges);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {
            Map results = new HashMap();
            ksession.setGlobal( "results", results );

            ksession.insert(new Person("Mark", 42));
            ksession.insert(new Person("Edson", 38));
            FactHandle meFH = ksession.insert(new Person("Mario", 45));
            ksession.insert(new Person("Maciej", 39));
            ksession.insert(new Person("Edoardo", 33));
            FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(2);
            assertThat(results.get("E")).isEqualTo(71);
            assertThat(results.get("M")).isEqualTo(126);
            results.clear();

            ksession.delete( meFH );
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(1);
            assertThat(results.get("M")).isEqualTo(81);
            results.clear();

            ksession.update(geoffreyFH, new Person("Geoffrey", 40));
            ksession.insert(new Person("Matteo", 38));
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(2);
            assertThat(results.get("G")).isEqualTo(40);
            assertThat(results.get("M")).isEqualTo(119);
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testSumPersonAgeGroupByInitialWithAcc(RUN_TYPE runType) {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + GroupKey.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "import " + org.drools.core.base.accumulators.IntegerSumAccumulateFunction.class.getCanonicalName() + ";" +
                "\n" +
                "global Map results\n" +
                "rule R1 when\n" +
                "    Person($initial: name.substring(0,1))" +
                "    not GroupKey(key == $initial)" +
                "then\n" +
                "    insert(new GroupKey(\"a\", $initial));\n" +
                "end" +
                "\n" +
                "rule R2 when\n" +
                "    $k: GroupKey(topic == \"a\", $key: key)" +
                "    not Person($key == name.substring(0, 1))" +
                "then\n" +
                "    delete($k);\n" +
                "end\n" +
                "rule R3 when\n" +
                "    GroupKey(topic == \"a\", $key: key)" +
                "    accumulate($p: Person($age: age, $key == name.substring(0, 1)); $sumOfAges: sum($age); $sumOfAges > 10)" +
                "then\n" +
                "    results.put($key, $sumOfAges);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {
            Map results = new HashMap();
            ksession.setGlobal( "results", results );

            ksession.insert(new Person("Mark", 42));
            ksession.insert(new Person("Edson", 38));
            FactHandle meFH = ksession.insert(new Person("Mario", 45));
            ksession.insert(new Person("Maciej", 39));
            ksession.insert(new Person("Edoardo", 33));
            FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(3);
            assertThat(results.get("G")).isEqualTo(35);
            assertThat(results.get("E")).isEqualTo(71);
            assertThat(results.get("M")).isEqualTo(126);
            results.clear();

            ksession.delete( meFH );
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(1);
            assertThat(results.get("M")).isEqualTo(81);
            results.clear();

            ksession.update(geoffreyFH, new Person("Geoffrey", 40));
            ksession.insert(new Person("Matteo", 38));
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(2);
            assertThat(results.get("G")).isEqualTo(40);
            assertThat(results.get("M")).isEqualTo(119);
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testGroupPersonsInitial(RUN_TYPE runType) {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "\n" +
                "global List results\n" +
                "rule R1 when\n" +
                "    groupby( $p: Person(); $key : $p.getName().substring(0, 1); count() )\n" +
                "then\n" +
                "    results.add($key);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {
            List<String> results = new ArrayList<>();
            ksession.setGlobal( "results", results );
    
            ksession.insert(new Person("Mark", 42));
            ksession.insert(new Person("Edson", 38));
            FactHandle meFH = ksession.insert(new Person("Mario", 45));
            ksession.insert(new Person("Maciej", 39));
            ksession.insert(new Person("Edoardo", 33));
            FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
            ksession.fireAllRules();
    
            assertThat(results.size()).isEqualTo(3);
            assertThat(results)
                    .containsExactlyInAnyOrder("G", "E", "M");
            results.clear();
    
            ksession.delete( meFH );
            ksession.fireAllRules();
    
            assertThat(results.size()).isEqualTo(1);
            assertThat(results)
                    .containsExactlyInAnyOrder("M");
            results.clear();
    
            ksession.update(geoffreyFH, new Person("Geoffrey", 40));
            ksession.insert(new Person("Matteo", 38));
            ksession.fireAllRules();
    
            assertThat(results.size()).isEqualTo(2);
            assertThat(results)
                    .containsExactlyInAnyOrder("G", "M");
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testSumPersonAgeGroupByInitial(RUN_TYPE runType) {
        // Note: this appears to be a duplicate of providedInstance
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "global Map results;\n" +
                "rule X when\n" +
                "groupby( $p: Person (); " +
                "$key : $p.getName().substring(0, 1); " +
                "$sumOfAges : sum($p.getAge()); " +
                "$sumOfAges > 36)" +
                "then\n" +
                "  results.put($key, $sumOfAges);\n" +
                "end";
        assertSessionHasProperties(runType, str, ksession -> {

        Map results = new HashMap();
        ksession.setGlobal( "results", results );

        ksession.insert(new Person("Mark", 42));
        ksession.insert(new Person("Edson", 38));
        FactHandle meFH = ksession.insert(new Person("Mario", 45));
        ksession.insert(new Person("Maciej", 39));
        ksession.insert(new Person("Edoardo", 33));
        FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get("E")).isEqualTo(71);
        assertThat(results.get("M")).isEqualTo(126);
        results.clear();

        ksession.delete( meFH );
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get("M")).isEqualTo(81);
        results.clear();

        ksession.update(geoffreyFH, new Person("Geoffrey", 40));
        ksession.insert(new Person("Matteo", 38));
        ksession.fireAllRules();

        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get("G")).isEqualTo(40);
        assertThat(results.get("M")).isEqualTo(119);
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testSumPersonAgeGroupByInitialWithBetaFilter(RUN_TYPE runType) {
        // TODO: For some reason, the compiled class expression thinks $p should be an integer?
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "global Map results;\n" +
                "rule X when\n" +
                "groupby( $p: Person ( $age: age ); " +
                "$key : $p.getName().substring(0, 1); " +
                "$sum : sum($age); " +
                "$sum > $key.length())" +
                "then\n" +
                "  results.put($key, $sum);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            Map results = new HashMap();
            ksession.setGlobal("results", results);

            ksession.insert(new Person("Mark", 42));
            ksession.insert(new Person("Edson", 38));
            FactHandle meFH = ksession.insert(new Person("Mario", 45));
            ksession.insert(new Person("Maciej", 39));
            ksession.insert(new Person("Edoardo", 33));
            FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(3);
            assertThat(results.get("G")).isEqualTo(35);
            assertThat(results.get("E")).isEqualTo(71);
            assertThat(results.get("M")).isEqualTo(126);
            results.clear();

            ksession.delete(meFH);
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(1);
            assertThat(results.get("M")).isEqualTo(81);
            results.clear();

            ksession.update(geoffreyFH, new Person("Geoffrey", 40));
            ksession.insert(new Person("Matteo", 38));
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(2);
            assertThat(results.get("G")).isEqualTo(40);
            assertThat(results.get("M")).isEqualTo(119);
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testSumPersonAgeGroupByInitialWithExists(RUN_TYPE runType) {
        // TODO: For some reason, the compiled class expression thinks $p should be an integer?
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "global Map results;\n" +
                "rule X when\n" +
                "groupby( $p: Person ( $age : age, $initial : getName().substring(0, 1) ) " +
                "and exists( String( this == $initial ) ); " +
                "$key : $p.getName().substring(0, 1); " +
                "$sum : sum($age); " +
                "$sum > 10)" +
                "then\n" +
                "  results.put($key, $sum);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            Map results = new HashMap();
            ksession.setGlobal("results", results);

            ksession.insert(new Person("Mark", 42));
            ksession.insert(new Person("Edson", 38));
            FactHandle meFH = ksession.insert(new Person("Mario", 45));
            ksession.insert(new Person("Maciej", 39));
            ksession.insert(new Person("Edoardo", 33));
            FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));

            ksession.insert("G");
            ksession.insert("M");
            ksession.insert("X");

            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(2);
            assertThat(results.get("G")).isEqualTo(35);
            assertThat(results.get("E")).isNull();
            assertThat(results.get("M")).isEqualTo(126);
            results.clear();

            ksession.delete(meFH);
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(1);
            assertThat(results.get("M")).isEqualTo(81);
            results.clear();

            ksession.update(geoffreyFH, new Person("Geoffrey", 40));
            ksession.insert(new Person("Matteo", 38));
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(2);
            assertThat(results.get("G")).isEqualTo(40);
            assertThat(results.get("M")).isEqualTo(119);
        });
    }

    public static final class MyType {

        private final AtomicInteger counter;
        private final MyType nested;

        public MyType(AtomicInteger counter, MyType nested) {
            this.counter = counter;
            this.nested = nested;
        }

        public MyType getNested() {
            counter.getAndIncrement();
            return nested;
        }
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testWithNull(RUN_TYPE runType) {
        String str =
                "import " + MyType.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "import " + AtomicInteger.class.getCanonicalName() + ";" +
                "global List result;\n" +
                "global AtomicInteger mappingFunctionCallCounter;\n" +
                "rule X when\n" +
                "groupby( $p: MyType ( nested != null ) ;" +
                "$key : $p.getNested(); " +
                "$count : count())" +
                "then\n" +
                "  result.add($key);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {
            AtomicInteger mappingFunctionCallCounter = new AtomicInteger();
            List<Object> result = new ArrayList<>();
            ksession.setGlobal("mappingFunctionCallCounter", mappingFunctionCallCounter);
            ksession.setGlobal("result", result);

            MyType objectWithoutNestedObject = new MyType(mappingFunctionCallCounter, null);
            MyType objectWithNestedObject = new MyType(mappingFunctionCallCounter, objectWithoutNestedObject);
            ksession.insert(objectWithNestedObject);
            ksession.insert(objectWithoutNestedObject);
            ksession.fireAllRules();

            // Side issue: this number is unusually high. Perhaps we should try to implement some cache for this?
            System.out.println("GroupKey mapping function was called " + mappingFunctionCallCounter.get() + " times.");

            assertThat(result).containsOnly(objectWithoutNestedObject);
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testWithGroupByAfterExists(RUN_TYPE runType) {
        String str =
                "import " + Map.class.getCanonicalName() + ";" +
                "import " + Math.class.getCanonicalName() + ";" +
                "global Map glob;\n" +
                "rule X when\n" +
                "groupby($i: Integer() and exists String();\n" +
                "$key : Math.abs($i); " +
                "$count : count())" +
                "then\n" +
                "  glob.put($key, $count.intValue());\n" +
                "end";

        assertSessionHasProperties(runType, str, session -> {
            Map<Integer, Integer> global = new HashMap<>();
            session.setGlobal("glob", global);

            session.insert("Something");
            session.insert(-1);
            session.insert(1);
            session.insert(2);
            session.fireAllRules();

            assertThat(global.size()).isEqualTo(2);
            assertThat((int) global.get(1)).isEqualTo(2); // -1 and 1 will map to the same key, and count twice.
            assertThat((int) global.get(2)).isEqualTo(1); // 2 maps to a key, and counts once.
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testWithGroupByAfterExistsWithFrom(RUN_TYPE runType) {
        // Note: this looks exactly the same as testWithGroupByAfterExists
        String str =
                "import " + Map.class.getCanonicalName() + ";" +
                "import " + Math.class.getCanonicalName() + ";" +
                "global Map glob;\n" +
                "rule X when\n" +
                "groupby($i: Integer() and exists String();\n" +
                "$key : Math.abs($i); " +
                "$count : count())" +
                "then\n" +
                "  glob.put($key, $count.intValue());\n" +
                "end";

        assertSessionHasProperties(runType, str, session -> {
            Map<Integer, Integer> global = new HashMap<>();
            session.setGlobal("glob", global);

            session.insert("Something");
            session.insert(-1);
            session.insert(1);
            session.insert(2);
            session.fireAllRules();

            assertThat(global.size()).isEqualTo(2);
            assertThat((int) global.get(1)).isEqualTo(2); // -1 and 1 will map to the same key, and count twice.
            assertThat((int) global.get(2)).isEqualTo(1); // 2 maps to a key, and counts once.
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testGroupBy2Vars(RUN_TYPE runType) {
        // TODO: For some reason, the compiled class expression thinks $p should be an integer?
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "global Map results;\n" +
                "rule X when\n" +
                "groupby ( $p : Person ( $age : age ) and $s : String( $l : length );\n" +
                "          $key : $p.getName().substring(0, 1) + $l;\n" +
                "          $sum : sum( $age ); $sum > 10 )" +
                "then\n" +
                "  results.put($key, $sum);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            Map results = new HashMap();
            ksession.setGlobal("results", results);

            ksession.insert("test");
            ksession.insert("check");
            ksession.insert(new Person("Mark", 42));
            ksession.insert(new Person("Edson", 38));
            FactHandle meFH = ksession.insert(new Person("Mario", 45));
            ksession.insert(new Person("Maciej", 39));
            ksession.insert(new Person("Edoardo", 33));
            FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(6);
            assertThat(results.get("G4")).isEqualTo(35);
            assertThat(results.get("E4")).isEqualTo(71);
            assertThat(results.get("M4")).isEqualTo(126);
            assertThat(results.get("G5")).isEqualTo(35);
            assertThat(results.get("E5")).isEqualTo(71);
            assertThat(results.get("M5")).isEqualTo(126);
            results.clear();

            ksession.delete(meFH);
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(2);
            assertThat(results.get("M4")).isEqualTo(81);
            assertThat(results.get("M5")).isEqualTo(81);
            results.clear();

            ksession.update(geoffreyFH, new Person("Geoffrey", 40));
            ksession.insert(new Person("Matteo", 38));
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(4);
            assertThat(results.get("G4")).isEqualTo(40);
            assertThat(results.get("M4")).isEqualTo(119);
            assertThat(results.get("G5")).isEqualTo(40);
            assertThat(results.get("M5")).isEqualTo(119);
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testUnexpectedRuleMatch(RUN_TYPE runType) {
        String str =
                "import " + Parent.class.getCanonicalName() + ";" +
                "import " + Child.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "import " + Arrays.class.getCanonicalName() + ";" +
                "global List results;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        $a: Parent() and exists Child($a.getChild() == this);" +
                "        $child: $a.getChild();" +
                "        $count: count()" +
                "    )" +
                "then\n" +
                "  results.add(Arrays.asList($child, $count));\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            List results = new ArrayList();
            ksession.setGlobal("results", results);

            Child child1 = new Child("Child1", 1);
            Parent parent1 = new Parent("Parent1", child1);
            Child child2 = new Child("Child2", 2);
            Parent parent2 = new Parent("Parent2", child2);

            ksession.insert(parent1);
            ksession.insert(parent2);
            FactHandle toRemove = ksession.insert(child1);
            ksession.insert(child2);

            // Remove child1, therefore it does not exist, therefore there should be no groupBy matches for the child.
            ksession.delete(toRemove);

            // Yet, we still get (Child1, 0).
            ksession.fireAllRules();
            assertThat(results)
                    .containsOnly(Arrays.asList(child2, 1L));
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testCompositeKey(RUN_TYPE runType) {
        // TODO: For some reason, the compiled class expression thinks $p should be an integer?
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + CompositeKey.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "global Map results;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        $p : Person ( $age : age );" +
                "        $key : new CompositeKey( $p.getName().substring(0, 1), 1 );" +
                "        $sum : sum( $age );" +
                "        $sum > 10" +
                "    )\n" +
                "    $key1: Object() from $key.getKey1()" +
                "then\n" +
                "  results.put($key1, $sum);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {
            Map results = new HashMap();
            ksession.setGlobal("results", results);

            ksession.insert(new Person("Mark", 42));
            ksession.insert(new Person("Edson", 38));
            FactHandle meFH = ksession.insert(new Person("Mario", 45));
            ksession.insert(new Person("Maciej", 39));
            ksession.insert(new Person("Edoardo", 33));
            FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(3);
            assertThat(results.get("G")).isEqualTo(35);
            assertThat(results.get("E")).isEqualTo(71);
            assertThat(results.get("M")).isEqualTo(126);
            results.clear();

            ksession.delete(meFH);
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(1);
            assertThat(results.get("M")).isEqualTo(81);
            results.clear();

            ksession.update(geoffreyFH, new Person("Geoffrey", 40));
            ksession.insert(new Person("Matteo", 38));
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(2);
            assertThat(results.get("G")).isEqualTo(40);
            assertThat(results.get("M")).isEqualTo(119);
        });
    }

    public static class CompositeKey {
        public final Object key1;
        public final Object key2;

        public CompositeKey( Object key1, Object key2 ) {
            this.key1 = key1;
            this.key2 = key2;
        }

        public Object getKey1() {
            return key1;
        }

        public Object getKey2() {
            return key2;
        }

        @Override
        public boolean equals( Object o ) {
            if ( this == o ) return true;
            if ( o == null || getClass() != o.getClass() ) return false;
            CompositeKey that = ( CompositeKey ) o;
            return Objects.equals( key1, that.key1 ) &&
                   Objects.equals( key2, that.key2 );
        }

        @Override
        public int hashCode() {
            return Objects.hash( key1, key2 );
        }

        @Override
        public String toString() {
            return "CompositeKey{" +
                   "key1=" + key1 +
                   ", key2=" + key2 +
                   '}';
        }
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testTwoExpressionsOnSecondPattern(RUN_TYPE runType) {
        // DROOLS-5704
        // TODO: For some reason, the compiled class expression thinks $p should be an integer?
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Set.class.getCanonicalName() + ";" +
                "global Set results;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        $p1 : Person ( $age1 : age ) and $p2 : Person ( $age2 : age, $age1 > $age2);" +
                "        $key : $age1 + $age2;" +
                "        count()" +
                "    )\n" +
                "then\n" +
                "  results.add($key);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            Set<Integer> results = new LinkedHashSet<>();
            ksession.setGlobal("results", results);

            ksession.insert(new Person("Mark", 42));
            ksession.insert(new Person("Edson", 38));
            ksession.insert(new Person("Edoardo", 33));
            ksession.fireAllRules();

            assertThat(results).contains(80, 75, 71);
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testFromAfterGroupBy(RUN_TYPE runType) {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Set.class.getCanonicalName() + ";" +
                "import " + Consumer.class.getCanonicalName() + ";" +
                "global Set results;\n" +
                "global Consumer typeChecker;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        $p : Person ( name != null );" +
                "        $key : $p.getName();" +
                "        $count : count()" +
                "    )\n" +
                "    $remappedKey: Object() from $key\n" +
                "    $remappedCount: Long() from $count\n" +
                "then\n" +
                "    typeChecker.accept($remappedKey);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            Set<Integer> results = new LinkedHashSet<>();
            ksession.setGlobal("results", results);
            ksession.setGlobal("typeChecker", (Consumer) ($remappedKey -> {
                if (!($remappedKey instanceof String)) {
                    throw new IllegalStateException( "Name not String, but " + $remappedKey.getClass() );
                }
            }));

            ksession.insert(new Person("Mark", 42));
            ksession.insert(new Person("Edson", 38));
            ksession.insert(new Person("Edoardo", 33));
            int fireCount = ksession.fireAllRules();
            assertThat(fireCount).isGreaterThan(0);
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testBindingRemappedAfterGroupBy(RUN_TYPE runType) {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Set.class.getCanonicalName() + ";" +
                "import " + Consumer.class.getCanonicalName() + ";" +
                "global Set results;\n" +
                "global Consumer typeChecker;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        $p : Person ( name != null );" +
                "        $key : $p.getName();" +
                "        $count : count()" +
                "    )\n" +
                "    $remappedKey: Object() from $key\n" +
                "    $remappedCount: Long() from $count\n" +
                "then\n" +
                "    typeChecker.accept($remappedKey);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            Set<Integer> results = new LinkedHashSet<>();
            ksession.setGlobal("results", results);
            ksession.setGlobal("typeChecker", (Consumer) ($remappedKey -> {
                if (!($remappedKey instanceof String)) {
                    throw new IllegalStateException( "Name not String, but " + $remappedKey.getClass() );
                }
            }));

            ksession.insert(new Person("Mark", 42));
            ksession.insert(new Person("Edson", 38));
            ksession.insert(new Person("Edoardo", 33));
            int fireCount = ksession.fireAllRules();
            assertThat(fireCount).isGreaterThan(0);
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testGroupByUpdatingKey(RUN_TYPE runType) {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "global Map results;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        $p : Person ();" +
                "        $key : $p.getName().substring(0, 1);" +
                "        $sumOfAges : sum($p.getAge()), $countOfPersons : count();" +
                "        $sumOfAges > 10" +
                "    )\n" +
                "then\n" +
                "    results.put($key, $sumOfAges + $countOfPersons.intValue());" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {
            Map results = new HashMap();
            ksession.setGlobal("results", results);

            Person me = new Person("Mario", 45);
            FactHandle meFH = ksession.insert(me);

            ksession.insert(new Person("Mark", 42));
            ksession.insert(new Person("Edson", 38));
            ksession.insert(new Person("Maciej", 39));
            ksession.insert(new Person("Edoardo", 33));
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(2);
            assertThat(results.get("E")).isEqualTo(73);
            assertThat(results.get("M")).isEqualTo(129);
            results.clear();

            me.setName("EMario");
            ksession.update(meFH, me);
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(2);
            assertThat(results.get("E")).isEqualTo(119);
            assertThat(results.get("M")).isEqualTo(83);
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void doesNotRemoveProperly(RUN_TYPE runType) {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Set.class.getCanonicalName() + ";" +
                "global Set results;\n" +
                "rule R1 when\n" +
                "    groupby(" +
                "        $p : Person (name != null);" +
                "        $key : $p.getAge();" +
                "        count()" +
                "    )\n" +
                "then\n" +
                "    results.add($key);" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            Set<Integer> results = new LinkedHashSet<>();

            ((RuleEventManager) ksession).addEventListener(new RuleEventListener() {
                @Override
                public void onDeleteMatch(Match match) {
                    if (!match.getRule().getName().equals("R1")) {
                        return;
                    }
                    RuleTerminalNodeLeftTuple tuple = (RuleTerminalNodeLeftTuple) match;
                    InternalFactHandle handle = tuple.getFactHandle();
                    Object[] array = (Object[]) handle.getObject();
                    results.remove(array[array.length - 1]);
                }

                @Override
                public void onUpdateMatch(Match match) {
                    onDeleteMatch(match);
                }
            });
            ksession.setGlobal("results", results);

            ksession.insert(new Person("Mark", 42));
            ksession.insert(new Person("Edson", 38));
            int edoardoAge = 33;
            FactHandle fh1 = ksession.insert(new Person("Edoardo", edoardoAge));
            FactHandle fh2 = ksession.insert(new Person("Edoardo's clone", edoardoAge));
            ksession.fireAllRules();
            assertThat(results).contains(edoardoAge);

            // Remove first Edoardo. Nothing should happen, because age 33 is still present.
            ksession.delete(fh1);
            ksession.fireAllRules();
            assertThat(results).contains(edoardoAge);

            // Remove Edoardo's clone. The group for age 33 should be undone.
            ksession.delete(fh2);
            ksession.fireAllRules();
            System.out.println(results);
            assertThat(results).doesNotContain(edoardoAge);
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testTwoGroupBy(RUN_TYPE runType) {
        // DROOLS-5697
        // TODO: For some reason, the compiled class expression thinks $p should be an integer?
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Group.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "global Map results;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        groupby(" +
                "            $p : Person ($age: age);" +
                "            $key1 : $p.getName().substring(0, 3);" +
                "            $sumOfAges : sum($age)" +
                "        ) and $g1: Group() from new Group($key1, $sumOfAges) and " +
                "        $g1SumOfAges: Integer() from $g1.getValue();" +
                "        $key : ((String) ($g1.getKey())).substring(0, 2);" +
                "        $maxOfValues : max($g1SumOfAges)" +
                "    )\n" +
                "then\n" +
                "    System.out.println($key + \" -> \" + $maxOfValues);\n" +
                "    results.put($key, $maxOfValues);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            Map results = new HashMap();
            ksession.setGlobal("results", results);

            ksession.insert(new Person("Mark", 42));
            ksession.insert(new Person("Edoardo", 33));
            FactHandle meFH = ksession.insert(new Person("Mario", 45));
            ksession.insert(new Person("Maciej", 39));
            ksession.insert(new Person("Edson", 38));
            FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
            ksession.fireAllRules();
            System.out.println("-----");

            /*
             * In the first groupBy:
             *   Mark+Mario become "(Mar, 87)"
             *   Maciej becomes "(Mac, 39)"
             *   Geoffrey becomes "(Geo, 35)"
             *   Edson becomes "(Eds, 38)"
             *   Edoardo becomes "(Edo, 33)"
             *
             * Then in the second groupBy:
             *   "(Mar, 87)" and "(Mac, 39)" become "(Ma, 87)"
             *   "(Eds, 38)" and "(Edo, 33)" become "(Ed, 38)"
             *   "(Geo, 35)" becomes "(Ge, 35)"
             */

            assertThat(results.size()).isEqualTo(3);
            assertThat(results.get("Ma")).isEqualTo(87);
            assertThat(results.get("Ed")).isEqualTo(38);
            assertThat(results.get("Ge")).isEqualTo(35);
            results.clear();

            ksession.delete(meFH);
            ksession.fireAllRules();
            System.out.println("-----");

            // No Mario anymore, so "(Mar, 42)" instead of "(Mar, 87)".
            // Therefore "(Ma, 42)".
            assertThat(results.size()).isEqualTo(1);
            assertThat(results.get("Ma")).isEqualTo(42);
            results.clear();

            // "(Geo, 35)" is gone.
            // "(Mat, 38)" is added, but Mark still wins, so "(Ma, 42)" stays.
            ksession.delete(geoffreyFH);
            ksession.insert(new Person("Matteo", 38));
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(1);
            assertThat(results.get("Ma")).isEqualTo(42);
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testTwoGroupByUsingBindings(RUN_TYPE runType) {
        // DROOLS-5697
        // TODO: For some reason, the compiled class expression thinks $p should be an integer?
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Group.class.getCanonicalName() + ";" +
                "import " + Map.class.getCanonicalName() + ";" +
                "global Map results;\n" +
                "rule R1 when\n" +
                "    groupby(" +
                "        groupby(" +
                "            $p : Person ($age: age);" +
                "            $key1 : $p.getName().substring(0, 3);" +
                "            $sumOfAges : sum($age)" +
                "        ) and $g1: Group() from new Group($key1, $sumOfAges) and " +
                "        $g1SumOfAges: Integer() from $g1.getValue();" +
                "        $key : ((String) ($g1.getKey())).substring(0, 2);" +
                "        $maxOfValues : max($g1SumOfAges)" +
                "    )\n" +
                "then\n" +
                "    System.out.println($key + \" -> \" + $maxOfValues);\n" +
                "    results.put($key, $maxOfValues);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            Map results = new HashMap();
            ksession.setGlobal("results", results);

            ksession.insert(new Person("Mark", 42));
            ksession.insert(new Person("Edoardo", 33));
            FactHandle meFH = ksession.insert(new Person("Mario", 45));
            ksession.insert(new Person("Maciej", 39));
            ksession.insert(new Person("Edson", 38));
            FactHandle geoffreyFH = ksession.insert(new Person("Geoffrey", 35));
            ksession.fireAllRules();
            System.out.println("-----");

            /*
             * In the first groupBy:
             *   Mark+Mario become "(Mar, 87)"
             *   Maciej becomes "(Mac, 39)"
             *   Geoffrey becomes "(Geo, 35)"
             *   Edson becomes "(Eds, 38)"
             *   Edoardo becomes "(Edo, 33)"
             *
             * Then in the second groupBy:
             *   "(Mar, 87)" and "(Mac, 39)" become "(Ma, 87)"
             *   "(Eds, 38)" and "(Edo, 33)" become "(Ed, 38)"
             *   "(Geo, 35)" becomes "(Ge, 35)"
             */

            assertThat(results.size()).isEqualTo(3);
            assertThat(results.get("Ma")).isEqualTo(87);
            assertThat(results.get("Ed")).isEqualTo(38);
            assertThat(results.get("Ge")).isEqualTo(35);
            results.clear();

            ksession.delete(meFH);
            ksession.fireAllRules();
            System.out.println("-----");

            // No Mario anymore, so "(Mar, 42)" instead of "(Mar, 87)".
            // Therefore "(Ma, 42)".
            assertThat(results.size()).isEqualTo(1);
            assertThat(results.get("Ma")).isEqualTo(42);
            results.clear();

            // "(Geo, 35)" is gone.
            // "(Mat, 38)" is added, but Mark still wins, so "(Ma, 42)" stays.
            ksession.delete(geoffreyFH);
            ksession.insert(new Person("Matteo", 38));
            ksession.fireAllRules();

            assertThat(results.size()).isEqualTo(1);
            assertThat(results.get("Ma")).isEqualTo(42);
        });
    }

    public static class Group {
        private final Object key;
        private final Object value;

        public Group( Object key, Object value ) {
            this.key = key;
            this.value = value;
        }

        public Object getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEmptyPatternOnGroupByKey(RUN_TYPE runType) {
        // DROOLS-6031
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "global List results;\n" +
                "rule R1 when\n" +
                "    groupby(" +
                "        $p : Person ();" +
                "        $key : $p.getName().substring(0, 1);" +
                "        count()" +
                "    )\n" +
                "    String() from $key\n" +
                "then\n" +
                "    results.add($key);" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            List<String> results = new ArrayList<String>();
            ksession.setGlobal("results", results);

            ksession.insert("A");
            ksession.insert("test");
            ksession.insert(new Person("Mark", 42));

            assertThat(ksession.fireAllRules()).isEqualTo(1);
            assertThat(results.size()).isEqualTo(1);
            assertThat(results.get(0)).isEqualTo("M");
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testFilterOnGroupByKey(RUN_TYPE runType) {
        // DROOLS-6031
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "global List results;\n" +
                "rule R1 when\n" +
                "    groupby(" +
                "        $p : Person ();" +
                "        $key : $p.getName().substring(0, 1);" +
                "        count()" +
                "    )\n" +
                "    eval($key.length() > 0)\n" +
                "    eval($key.length() < 2)\n" +
                "then\n" +
                "    results.add($key);" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            List<String> results = new ArrayList<String>();
            ksession.setGlobal("results", results);

            ksession.insert("A");
            ksession.insert("test");
            ksession.insert(new Person("Mark", 42));

            assertThat(ksession.fireAllRules()).isEqualTo(1);
            assertThat(results.size()).isEqualTo(1);
            assertThat(results.get(0)).isEqualTo("M");
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testDecomposedGroupByKey(RUN_TYPE runType) {
        // DROOLS-6031
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "import " + Pair.class.getCanonicalName() + ";" +
                "global List results;\n" +
                "rule R1 when\n" +
                "    groupby(" +
                "        $p : Person ();" +
                "        $key : Pair.create($p.getName().substring(0, 1), $p.getName().substring(1, 2));" +
                "        count()" +
                "    )\n" +
                "    $subkeyA: String() from $key.getKey()\n" +
                "    $subkeyB: String() from $key.getValue()\n" +
                "then\n" +
                "    results.add($subkeyA);" +
                "    results.add($subkeyB);" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            final List<String> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            ksession.insert("A");
            ksession.insert("test");
            ksession.insert(new Person("Mark", 42));
            assertThat(ksession.fireAllRules()).isEqualTo(1);
            assertThat(results.size()).isEqualTo(2);
            assertThat(results.get(0)).isEqualTo("M");
            assertThat(results.get(1)).isEqualTo("a");
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testDecomposedGroupByKeyAndAccumulate(RUN_TYPE runType) {
        // DROOLS-6031
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Pair.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "global List results;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        $p : Person ();" +
                "        $key : Pair.create($p.getName().substring(0, 1), $p.getName().substring(1, 2));" +
                "        $accresult : count()" +
                "    )\n" +
                "    $subkeyA : Object() from $key.getKey()\n" +
                "    $subkeyB : Object() from $key.getValue()\n" +
                "    eval($accresult > 0)" +
                "then\n" +
                "    results.add($subkeyA);\n" +
                "    results.add($subkeyB);\n" +
                "    results.add($accresult);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            final List<Object> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            ksession.insert("A");
            ksession.insert("test");
            ksession.insert(new Person("Mark", 42));
            assertThat(ksession.fireAllRules()).isEqualTo(1);
            assertThat(results.size()).isEqualTo(3);
            assertThat(results.get(0)).isEqualTo("M");
            assertThat(results.get(1)).isEqualTo("a");
            assertThat(results.get(2)).isEqualTo(1L);
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testDecomposedGroupByKeyAnd2Accumulates(RUN_TYPE runType) {
        // DROOLS-6031
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Pair.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "global List results;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        $p : Person () and $p2 : Person () and $accumulate : Pair() from Pair.create($p, $p2);" +
                "        $key : Pair.create($p.getName().substring(0, 1), $p.getName().substring(1, 2));" +
                "        $accresult : collectList(), $accresult2 : collectList()" +
                "    )\n" +
                "    $subkeyA : Object() from $key.getKey()\n" +
                "    $subkeyB : Object() from $key.getValue()\n" +
                "    eval($accresult != null)" +
                "then\n" +
                "    results.add($subkeyA);\n" +
                "    results.add($subkeyB);\n" +
                "    results.add($accresult);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            final List<Object> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            ksession.insert("A");
            ksession.insert("test");
            ksession.insert(new Person("Mark", 42));
            assertThat(ksession.fireAllRules()).isEqualTo(1);
            assertThat(results.size()).isEqualTo(3);
            assertThat(results.get(0)).isEqualTo("M");
            assertThat(results.get(1)).isEqualTo("a");
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testDecomposedGroupByKeyAnd2AccumulatesInConsequence(RUN_TYPE runType) {
        // DROOLS-6031
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Pair.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "global List results;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        $p : Person () and $p2 : Person () and $accumulate : Pair() from Pair.create($p, $p2);" +
                "        $key : Pair.create($p.getName().substring(0, 1), $p.getName().substring(1, 2));" +
                "        $accresult : collectList(), $accresult2 : collectList()" +
                "    )\n" +
                "    eval($accresult2 != null)" +
                "then\n" +
                "    results.add($key);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            final List<Object> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            ksession.insert("A");
            ksession.insert("test");
            ksession.insert(new Person("Mark", 42));
            assertThat(ksession.fireAllRules()).isEqualTo(1);
            assertThat(results.size()).isEqualTo(1);
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testNestedGroupBy1a(RUN_TYPE runType) {
        // DROOLS-6045
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "global List results;\n" +
                "rule X when\n" +
                "    accumulate(" +
                "        groupby($p: Person (); $key: $p.getAge(); count()) and eval($key > 0);" +
                "        $accresult : collectList($key)" +
                "    )\n" +
                "then\n" +
                "    results.add($accresult);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            final List<Object> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            ksession.insert(new Person("Mark", 42));
            assertThat(ksession.fireAllRules()).isEqualTo(1);
            System.out.println(results);
            assertThat(results).containsOnly(Collections.singletonList(42));
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testNestedGroupBy1b(RUN_TYPE runType) {
        // DROOLS-6045
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "global List results;\n" +
                "rule R1 when\n" +
                "    accumulate(" +
                "        groupby($p: Person (); $key: $p.getAge(); count()) and Integer() from $key;" +
                "        $accresult : collectList($key)" +
                "    )\n" +
                "then\n" +
                "    results.add($accresult);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            final List<Object> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            ksession.insert(new Person("Mark", 42));
            assertThat(ksession.fireAllRules()).isEqualTo(1);
            assertThat(results).containsOnly(Collections.singletonList(42));
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testNestedGroupBy2(RUN_TYPE runType) {
        // DROOLS-6045
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "global List results;\n" +
                "rule R1 when\n" +
                "    groupby(" +
                "        groupby($p: Person (); $key: $p.getAge(); count()) and eval($key > 0);" +
                "        $keyOuter: (int)($key * 2);" + // MVEL thinks type(int * 2) == Double,
                                                        // meaning we need to cast for it to get the
                                                        // correct type
                "        $accresult : collectList($keyOuter)" +
                "    )\n" +
                "then\n" +
                "    results.add($accresult);\n" +
                "end";
        assertSessionHasProperties(runType, str, ksession -> {

            final List<Object> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            ksession.insert("A");
            ksession.insert("test");
            ksession.insert(new Person("Mark", 42));
            assertThat(ksession.fireAllRules()).isEqualTo(1);
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testNestedGroupBy3(RUN_TYPE runType) {
        // DROOLS-6045
        // TODO: For some reason, the compiled class expression thinks $p should be an integer?
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Pair.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "global List results;\n" +
                "rule R1 when\n" +
                "    groupby(" +
                "        groupby($p: Person (); $key: $p.getName(); $accresult: count()) and eval($accresult > 0);" +
                "        $keyOuter: Pair.create($key, $accresult);" +
                "        count()" +
                "    )\n" +
                "then\n" +
                "    results.add($keyOuter);\n" +
                "end";
        assertSessionHasProperties(runType, str, ksession -> {

            final List<Object> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            ksession.insert("A");
            ksession.insert("test");
            ksession.insert(new Person("Mark", 42));
            assertThat(ksession.fireAllRules()).isEqualTo(1);
            assertThat(results).containsOnly(Pair.create("Mark", 1L));
        });
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testFilterOnAccumulateResultWithDecomposedGroupByKey(RUN_TYPE runType) {
        // DROOLS-6045
        // TODO: For some reason, the compiled class expression thinks $p should be an integer?
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Pair.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "global List results;\n" +
                "rule X when\n" +
                "    groupby(" +
                "        $p : Person ($age: age, this != null);" +
                "        $key : Pair.create($p.getName().substring(0, 1), $p.getName().substring(1, 2));" +
                "        $accresult : sum($age)" +
                "    )\n" +
                "    $subkeyA : Object() from $key.getKey()\n" +
                "    $subkeyB : Object() from $key.getValue()\n" +
                "    eval($accresult != null && $subkeyA != null && $subkeyB != null)" +
                "then\n" +
                "    results.add($subkeyA);\n" +
                "    results.add($subkeyB);\n" +
                "    results.add($accresult);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            final List<Object> results = new ArrayList<>();
            ksession.setGlobal("results", results);

            ksession.insert("A");
            ksession.insert("test");
            ksession.insert(new Person("Mark", 42));
            assertThat(ksession.fireAllRules()).isEqualTo(1);
            assertThat(results.size()).isEqualTo(3);
            assertThat(results.get(0)).isEqualTo("M");
            assertThat(results.get(1)).isEqualTo("a");
            assertThat(results.get(2)).isEqualTo(42);
        });
    }
// These two test are commented out, until we figure out the correct way to do this and limitations.
// If no correct way can be found, the tests can be deleted.
//    @Test
//    public void testErrorOnCompositeBind() {
//        Global<Map> var_results = D.globalOf(Map.class, "defaultpkg", "results");
//
//        Variable<String> var_$key_1 = D.declarationOf(String.class);
//        Variable<Person> var_$p = D.declarationOf(Person.class);
//        Variable<Integer> var_$age = D.declarationOf(Integer.class);
//        Variable<Integer> var_$sumOfAges = D.declarationOf(Integer.class);
//        Variable<Group> var_$g1 = D.declarationOf(Group.class);
//        Variable<Integer> var_$g1_value = D.declarationOf(Integer.class);
//        Variable<String> var_$key_2 = D.declarationOf(String.class);
//        Variable<Integer> var_$maxOfValues = D.declarationOf(Integer.class);
//
//        Rule rule1 = D.rule("R1").build(
//              D.groupBy(
//                    D.pattern(var_$p).bind(var_$age, person -> person.getAge()),
//                    var_$p, var_$key_1, groupResult -> var_$p.getName().substring(0, 2),
//                    D.accFunction( IntegerMaxAccumulateFunction::new, var_$age).as(var_$maxOfValues)),
//              D.pattern(var_$key_1).bind(var_$g1, var_$maxOfValues, (k, s) -> new Group(k, s)),
//              D.on(var_$key_1)
//               .execute($key -> { System.out.println($key); }));
//
//        try {
//            Model      model    = new ModelImpl().addRule(rule1).addGlobal(var_results);
//            KieSession ksession = KieBaseBuilder.createKieBaseFromModel(model).newKieSession();
//            fail("Composite Bindings are not allowed");
//        } catch(Exception e) {
//
//        }
//    }
//
//    @Test
//    public void testErrorOnNestedCompositeBind() {
//        Global<Map> var_results = D.globalOf(Map.class, "defaultpkg", "results");
//
//        Variable<String> var_$key_1 = D.declarationOf(String.class);
//        Variable<Person> var_$p = D.declarationOf(Person.class);
//        Variable<Integer> var_$age = D.declarationOf(Integer.class);
//        Variable<Integer> var_$sumOfAges = D.declarationOf(Integer.class);
//        Variable<Group> var_$g1 = D.declarationOf(Group.class);
//        Variable<Integer> var_$g1_value = D.declarationOf(Integer.class);
//        Variable<String> var_$key_2 = D.declarationOf(String.class);
//        Variable<Integer> var_$maxOfValues = D.declarationOf(Integer.class);
//
//        Rule rule1 = D.rule("R1").build(
//              D.groupBy(
//                    D.and(
//                          D.groupBy(
//                                D.pattern(var_$p).bind(var_$age, person -> person.getAge()),
//                                var_$p, var_$key_1, person -> person.getName().substring(0, 3),
//                                D.accFunction( IntegerSumAccumulateFunction::new, var_$age).as(var_$sumOfAges)),
//                          D.pattern(var_$key_1).bind(var_$g1, var_$sumOfAges, (k, s) -> new Group(k, s))), // this should fail, due to two declarations
//                    var_$g1, var_$key_2, groupResult -> ((String)groupResult.getKey()).substring(0, 2),
//                    D.accFunction( IntegerMaxAccumulateFunction::new, var_$g1_value).as(var_$maxOfValues)),
//              D.on(var_$key_1)
//               .execute($key -> { System.out.println($key); }));
//
//        try {
//            Model      model    = new ModelImpl().addRule(rule1).addGlobal(var_results);
//            KieSession ksession = KieBaseBuilder.createKieBaseFromModel(model).newKieSession();
//            fail("Composite Bindings are not allowed");
//        } catch(Exception e) {
//
//        }
//    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testNestedRewrite(RUN_TYPE runType) {
        // DROOLS-5697
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "import " + Pair.class.getCanonicalName() + ";" +
                "import " + List.class.getCanonicalName() + ";" +
                "global List results;\n" +
                "function Integer eval( Integer value ){ return value; }\n" +
                "rule R1 when\n" +
                "    accumulate(" +
                "        accumulate($p : Person ($age : age); $sumOfAges : sum($age)) " +
                "        and $g1 : Integer() from eval($sumOfAges + 1) ;" +
                "        $maxOfValues : max($g1)" +
                "    )\n" +
                "then\n" +
                "    System.out.println($maxOfValues);\n" +
                "    results.add($maxOfValues);\n" +
                "end";

        assertSessionHasProperties(runType, str, ksession -> {

            List results = new ArrayList();
            ksession.setGlobal("results", results);

            FactHandle fhMark = ksession.insert(new Person("Mark", 42));
            FactHandle fhEdoardo = ksession.insert(new Person("Edoardo", 33));
            ksession.fireAllRules();
            assertThat(results.contains(76)).isTrue();

            ksession.insert(new Person("Edson", 38));
            ksession.fireAllRules();
            assertThat(results.contains(114)).isTrue();

            ksession.delete(fhEdoardo);
            ksession.fireAllRules();
            assertThat(results.contains(81)).isTrue();

            ksession.update(fhMark, new Person("Mark", 45));
            ksession.fireAllRules();
            assertThat(results.contains(84)).isTrue();
        });
    }
}
