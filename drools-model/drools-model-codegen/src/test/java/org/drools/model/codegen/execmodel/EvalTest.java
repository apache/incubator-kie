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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.drools.model.codegen.execmodel.domain.CalcFact;
import org.drools.model.codegen.execmodel.domain.Overloaded;
import org.drools.model.codegen.execmodel.domain.Person;
import org.drools.model.codegen.execmodel.domain.Result;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

public class EvalTest extends BaseModelTest {


    @ParameterizedTest
	@MethodSource("parameters")
    public void testEval(RUN_TYPE runType) {
        String str =
                "import " + Result.class.getCanonicalName() + ";" +
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R when\n" +
                "  $p : Person()\n" +
                "  eval( $p.getAge() == 40 )\n" +
                "then\n" +
                "  insert(new Result($p.getName()));\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert( new Person( "Mario", 40 ) );
        ksession.insert( new Person( "Mark", 37 ) );
        ksession.insert( new Person( "Edson", 35 ) );
        ksession.fireAllRules();

        Collection<Result> results = getObjectsIntoList( ksession, Result.class );
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.iterator().next().getValue()).isEqualTo("Mario");
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalWithMethodInvocation(RUN_TYPE runType) {
        String str =
                "import " + Result.class.getCanonicalName() + ";" +
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R when\n" +
                "  $p : Person()\n" +
                "  eval( $p.getName().startsWith(\"E\") )\n" +
                "then\n" +
                "  insert(new Result($p.getName()));\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert( new Person( "Mario", 40 ) );
        ksession.insert( new Person( "Mark", 37 ) );
        ksession.insert( new Person( "Edson", 35 ) );
        ksession.fireAllRules();

        Collection<Result> results = getObjectsIntoList( ksession, Result.class );
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.iterator().next().getValue()).isEqualTo("Edson");
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalTrue(RUN_TYPE runType) {
        String str =
                "rule R when\n" +
                "  eval( true )\n" +
                "then\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);
        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalFalse(RUN_TYPE runType) {
        String str =
                "rule R when\n" +
                "  eval( false )\n" +
                "then\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);
        assertThat(ksession.fireAllRules()).isEqualTo(0);
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalOr(RUN_TYPE runType) {
        String str =
                "rule R when\n" +
                "  eval( true ) or eval( false )\n" +
                "then\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);
        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalIdentity(RUN_TYPE runType) {
        String str =
                "rule R when\n" +
                        "  eval( 1 == 1 )\n" +
                        "then\n" +
                        "end";

        KieSession ksession = getKieSession(runType, str);
        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testFunction(RUN_TYPE runType) {
        String str =
                "import " + Result.class.getCanonicalName() + ";" +
                "import " + Person.class.getCanonicalName() + ";" +
                "function String hello(String name) {\n" +
                "    return \"Hello \"+name+\"!\";\n" +
                "}" +
                "rule R when\n" +
                "  $p : Person()\n" +
                "  eval( $p.getAge() == 40 )\n" +
                "then\n" +
                "  insert(new Result(hello($p.getName())));\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert( new Person( "Mario", 40 ) );
        ksession.insert( new Person( "Mark", 37 ) );
        ksession.insert( new Person( "Edson", 35 ) );
        ksession.fireAllRules();

        Collection<Result> results = getObjectsIntoList( ksession, Result.class );
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.iterator().next().getValue()).isEqualTo("Hello Mario!");
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testFunction2(RUN_TYPE runType) {
        String str =
                "import " + Result.class.getCanonicalName() + ";" +
                "import " + Person.class.getCanonicalName() + ";" +
                "function Boolean isFortyYearsOld(Person p, Boolean booleanParameter) {\n" +
                "    return p.getAge() == 40; \n"+
                "}" +
                "rule R when\n" +
                "  $p : Person()\n" +
                "  eval( isFortyYearsOld($p, true) )\n" +
                "then\n" +
                "  insert(new Result($p.getName()));\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert( new Person( "Mario", 40 ) );
        ksession.insert( new Person( "Mark", 37 ) );
        ksession.insert( new Person( "Edson", 35 ) );
        ksession.fireAllRules();

        Collection<Result> results = getObjectsIntoList( ksession, Result.class );
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.iterator().next().getValue()).isEqualTo("Mario");
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalWith2Bindings(RUN_TYPE runType) {
        String str =
                "import " + Result.class.getCanonicalName() + ";" +
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R when\n" +
                "  $p1 : Person( name == \"Mario\" )\n" +
                "  $p2 : Person( name == \"Mark\" )\n" +
                "  eval( $p1.getAge() == $p2.getAge() + 2 )\n" +
                "then\n" +
                "  insert(new Result($p1.getName()));\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert( new Person( "Mario", 40 ) );
        ksession.insert( new Person( "Mark", 38 ) );
        ksession.insert( new Person( "Edson", 35 ) );
        ksession.fireAllRules();

        Collection<Result> results = getObjectsIntoList( ksession, Result.class );
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.iterator().next().getValue()).isEqualTo("Mario");
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalWith2BindingsInvokingMethod(RUN_TYPE runType) {
        String str =
                "import " + Result.class.getCanonicalName() + ";" +
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R when\n" +
                "  $p1 : Person( age == 40 )\n" +
                "  $p2 : Person( age == 38 )\n" +
                "  eval( $p1.getName().equals( $p2.getName() ) )\n" +
                "then\n" +
                "  insert(new Result($p1.getName()));\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert( new Person( "Mario", 40 ) );
        ksession.insert( new Person( "Mario", 38 ) );
        ksession.insert( new Person( "Edson", 35 ) );
        ksession.fireAllRules();

        Collection<Result> results = getObjectsIntoList( ksession, Result.class );
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.iterator().next().getValue()).isEqualTo("Mario");
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalWithDeclaration(RUN_TYPE runType) {
        String str =
                "import " + Result.class.getCanonicalName() + ";" +
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R when\n" +
                "  $p1 : Person( $a1 : age )\n" +
                "  eval( $a1 > 39 )\n" +
                "then\n" +
                "  insert(new Result($p1.getName()));\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert( new Person( "Mario", 40 ) );
        ksession.insert( new Person( "Mark", 38 ) );
        ksession.insert( new Person( "Edson", 35 ) );
        ksession.fireAllRules();

        Collection<Result> results = getObjectsIntoList( ksession, Result.class );
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.iterator().next().getValue()).isEqualTo("Mario");
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalWith2Declarations(RUN_TYPE runType) {
        String str =
                "import " + Result.class.getCanonicalName() + ";" +
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R when\n" +
                "  $p1 : Person( name == \"Mario\", $a1 : age > 0 )\n" +
                "  $p2 : Person( name == \"Mark\", $a2 : age > 0 )\n" +
                "  eval( $a1 == $a2 + 2 )\n" +
                "then\n" +
                "  insert(new Result($p1.getName()));\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert( new Person( "Mario", 40 ) );
        ksession.insert( new Person( "Mark", 38 ) );
        ksession.insert( new Person( "Edson", 35 ) );
        ksession.fireAllRules();

        Collection<Result> results = getObjectsIntoList( ksession, Result.class );
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.iterator().next().getValue()).isEqualTo("Mario");
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalWithBinary(RUN_TYPE runType) {
        String str = "import " + Result.class.getCanonicalName() + ";" +
                     "import " + Person.class.getCanonicalName() + ";" +
                     "rule R when\n" +
                     "  $p : Person()\n" +
                     "  eval( $p.getAge() + 27 == 67 )\n" +
                     "then\n" +
                     "  insert(new Result($p.getName()));\n" +
                     "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert(new Person("Mario", 40));
        ksession.insert(new Person("Mark", 37));
        ksession.insert(new Person("Edson", 35));
        ksession.fireAllRules();

        Collection<Result> results = getObjectsIntoList(ksession, Result.class);
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.iterator().next().getValue()).isEqualTo("Mario");
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalInvokingMethod(RUN_TYPE runType) {
        String str = "import " + Overloaded.class.getCanonicalName() + ";" +
                     "rule OverloadedMethods\n" +
                     "when\n" +
                     "  o : Overloaded()\n" +
                     "  eval (o.method(5, 9, \"x\") == 15)\n" +
                     "then\n" +
                     "  insert(\"matched\");\n" +
                     "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert(new Overloaded());
        ksession.fireAllRules();

        Collection<String> results = getObjectsIntoList(ksession, String.class);
        assertThat(results.size()).isEqualTo(1);
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalInvokingMethod2(RUN_TYPE runType) {
        String str = "import " + Overloaded.class.getCanonicalName() + ";" +
                     "rule OverloadedMethods\n" +
                     "when\n" +
                     "  o : Overloaded()\n" +
                     "  eval (\"helloworld150.32\".equals(o.method2(\"hello\", \"world\", 15L, 0.32)))\n" +
                     "then\n" +
                     "  insert(\"matched\");\n" +
                     "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert(new Overloaded());
        ksession.fireAllRules();

        Collection<String> results = getObjectsIntoList(ksession, String.class);
        assertThat(results.size()).isEqualTo(1);
    }


    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalInvokingFunction(RUN_TYPE runType) {
        String str =
                "function boolean isPositive(int i){\n" +
                "  return i > 0;\n" +
                "}" +
                "rule OverloadedMethods\n when\n" +
                "  $i : Integer()\n" +
                "  eval (isPositive($i.intValue()))\n" +
                "then\n" +
                "  insert(\"matched\");\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert(42);
        ksession.fireAllRules();

        Collection<String> results = getObjectsIntoList(ksession, String.class);
        assertThat(results.size()).isEqualTo(1);
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalWithGlobal(RUN_TYPE runType) {
        final String drl1 =
                "import " + Result.class.getCanonicalName() + ";\n" +
                        "global java.lang.Integer globalInt\n" +
                        "rule R1 when\n" +
                        "	 Integer(this == eval(globalInt))\n" +
                        "then\n" +
                        "  insert(new Result(\"match\"));\n" +
                        "end\n";

        KieSession ksession = getKieSession(runType, drl1);

        ksession.setGlobal("globalInt", 1);
        ksession.insert(1);
        ksession.insert(2);
        ksession.insert(3);

        assertThat(ksession.fireAllRules()).isEqualTo(1);

        Collection<Result> results = getObjectsIntoList(ksession, Result.class);
        assertThat(results.iterator().next().getValue().toString()).isEqualTo("match");
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalWithGlobal2(RUN_TYPE runType) {
        final String drl1 =
                "import " + Result.class.getCanonicalName() + ";\n" +
                        "global java.lang.Integer globalInt\n" +
                        "rule R1 when\n" +
                        "	 eval(globalInt == globalInt)\n" +
                        "then\n" +
                        "  insert(new Result(\"match\"));\n" +
                        "end\n";

        KieSession ksession = getKieSession(runType, drl1);

        ksession.setGlobal("globalInt", 1);
        ksession.insert(1);
        ksession.insert(2);
        ksession.insert(3);

        assertThat(ksession.fireAllRules()).isEqualTo(1);

        Collection<Result> results = getObjectsIntoList(ksession, Result.class);
        assertThat(results.iterator().next().getValue().toString()).isEqualTo("match");

    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalExprWithFunctionCall(RUN_TYPE runType) {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                        "global " + GlobalFunctions.class.getCanonicalName() + " functions;" +
                        "rule R1 when\n" +
                        "  $p : Person($age : age)\n" +
                        "  eval( functions.add($age, -1).compareTo(10) < 0)\n" +
                        "then\n" +
                        "end\n";

        KieSession ksession = getKieSession(runType, str);
        GlobalFunctions gf = new GlobalFunctions();
        ksession.setGlobal("functions", gf);

        Person first = new Person("First", 10);
        ksession.insert(first);
        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    public static class GlobalFunctions {
        public Integer add(int a, int b) {
            return Integer.sum(a, b);
        }
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEvalCalculationWithParenthesis(RUN_TYPE runType) {
        String str =
                "import " + CalcFact.class.getCanonicalName() + ";" +
                "rule R when\n" +
                "  $p : CalcFact( $v1 : value1, $v2 : value2 )\n" +
                "  eval( ($v1 / ($v2 * 10) * 10) > 25 )\n" +
                "then\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert(new CalcFact(1.0d, 1.0d));
        // (1.0 / (1.0 * 10) * 10) is 1. So this rule should not fire

        int fired = ksession.fireAllRules();
        assertThat(fired).isEqualTo(0);
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testParseIntStringConcatenation(RUN_TYPE runType) throws Exception {
        String str =
                "rule R when\n" +
                     "  $s : String()\n" +
                     "  eval(Integer.parseInt('1' + $s) > 3)\n" +
                     "then\n" +
                     "end\n";

        KieSession ksession = getKieSession(runType, str);
        ksession.insert("5");
        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    public static class Dt1 implements java.io.Serializable{
        private java.lang.Integer a;

        public Integer getA() {
            return a;
        }

        public void setA(Integer a) {
            this.a = a;
        }
    }

    public static class Dt2 implements java.io.Serializable{
        private String t;

        public String getT() {
            return t;
        }

        public void setT(String t) {
            this.t = t;
        }
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testModifyEvalAfterJoinWithEmptyConstraint_withoutWatch_shouldNotFire(RUN_TYPE runType) {
        testModifyEvalAfterJoinWithEmptyConstraint(runType, "", false);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testModifyEvalAfterJoinWithEmptyConstraint_withWatch_shouldFire(RUN_TYPE runType) {
        testModifyEvalAfterJoinWithEmptyConstraint(runType, "@watch( t )", true);
    }

    private void testModifyEvalAfterJoinWithEmptyConstraint(RUN_TYPE runType, String watchAnnotation, boolean shouldFireR1) {
        // DROOLS-7255
        String str =
                "import " + Dt1.class.getCanonicalName() + ";" +
                        "import " + Dt2.class.getCanonicalName() + ";" +
                        "\n" +
                        "global java.util.List list;\n" +
                        "rule R1\n" +
                        "    when\n" +
                        "        dt1 : Dt1( )\n" +
                        "        dt2 : Dt2( ) " + watchAnnotation + "\n" + // without watch, JoinNode doesn't proceed on modify
                        "        eval(dt2.getT()==\"YES\")\n" +
                        "    then\n" +
                        "        list.add(\"R1\");\n" +
                        "end\n" +
                        "\n" +
                        "rule R2\n" +
                        "    when\n" +
                        "        dt2 : Dt2(  )\n" +
                        "        eval(dt2.getT()==\"YES\")\n" + // There is no JoinNode here, so this rule fires on modify
                        "    then\n" +
                        "        list.add(\"R2\");\n" +
                        "end\n" +
                        "\n" +
                        "rule ModifyingRule\n" +
                        "    when\n" +
                        "        dt1 : Dt1(a == 1)\n" +
                        "        dt2 : Dt2( )\n" +
                        "    then\n" +
                        "        list.add(\"ModifyingRule\");\n" +
                        "        modify( dt2 ) {\n" +
                        "            setT(\"YES\")\n" +
                        "        }\n" +
                        "end\n";

        KieSession ksession = getKieSession(runType, str);
        List<String> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        Dt1 dt1 = new Dt1();
        dt1.setA(1);
        ksession.insert(dt1);
        ksession.insert(new Dt2());

        ksession.fireAllRules();

        if (shouldFireR1) {
            assertThat(list).as("All rules should fire").containsExactlyInAnyOrder("ModifyingRule", "R1", "R2");
        } else {
            assertThat(list).as("R1 should not fire").containsExactlyInAnyOrder("ModifyingRule", "R2");
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testModifyEvalAfterJoinWithNonMatchingAlpha(RUN_TYPE runType) {
        // incubator-kie-drools/issues/6420
        String str =
                "import " + Person.class.getCanonicalName() + ";\n" +
                        "global java.util.List list;\n" +
                        "rule R1\n" +
                        "    when\n" +
                        "        String( )\n" +
                        "        $p : Person( name == \"John\")\n" + // obviously non-matching
                        "        eval($p.getAge() == 10)\n" +
                        "    then\n" +
                        "        list.add(\"R1 : \" + $p.getName());\n" +
                        "end\n" +
                        "rule ModifyingRule\n" +
                        "    when\n" +
                        "        String( this == \"go\" )\n" +
                        "        $p : Person( name == \"Paul\")\n" +
                        "    then\n" +
                        "        modify( $p ) {\n" +
                        "            setAge(10)\n" +
                        "        }\n" +
                        "        list.add(\"ModifyingRule\");\n" +
                        "end\n";

        KieSession ksession = getKieSession(runType, str);

        List<String> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        Person paul = new Person("Paul", 20);
        ksession.insert("go");
        ksession.insert(paul);

        ksession.fireAllRules();
        assertThat(list).as("R1 should not fire").containsExactly("ModifyingRule");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testModifyEvalAfterJoinWithMatchingAlpha_withoutWatch_shouldNotFire(RUN_TYPE runType) {
        testModifyEvalAfterJoinWithMatchingAlpha(runType, "", false);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testModifyEvalAfterJoinWithMatchingAlpha_withWatch_shouldFire(RUN_TYPE runType) {
        testModifyEvalAfterJoinWithMatchingAlpha(runType, "@watch( age )", true);
    }

    private void testModifyEvalAfterJoinWithMatchingAlpha(RUN_TYPE runType, String watchAnnotation, boolean shouldFireR1) {
        // incubator-kie-drools/issues/6420
        // This test is already successful. Just for coverage.
        String str =
                "import " + Person.class.getCanonicalName() + ";\n" +
                        "global java.util.List list;\n" +
                        "rule R1\n" +
                        "    when\n" +
                        "        String( )\n" +
                        "        $p : Person( name == \"Paul\") " + watchAnnotation + "\n" + // without watch, JoinNode doesn't proceed on modify
                        "        eval($p.getAge() == 10)\n" +
                        "    then\n" +
                        "        list.add(\"R1 : \" + $p.getName());\n" +
                        "end\n" +
                        "rule ModifyingRule\n" +
                        "    when\n" +
                        "        String( this == \"go\" )\n" +
                        "        $p : Person( name == \"Paul\")\n" +
                        "    then\n" +
                        "        modify( $p ) {\n" +
                        "            setAge(10)\n" +
                        "        }\n" +
                        "        list.add(\"ModifyingRule\");\n" +
                        "end\n";

        KieSession ksession = getKieSession(runType, str);
        List<String> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        Person paul = new Person("Paul", 20);
        ksession.insert("go");
        ksession.insert(paul);

        ksession.fireAllRules();

        if (shouldFireR1) {
            assertThat(list).as("Both rules should fire").containsExactly("ModifyingRule", "R1 : Paul");
        } else {
            assertThat(list).as("R1 should not fire").containsExactly("ModifyingRule");
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testModifyJoinWithNonMatchingAlpha(RUN_TYPE runType) {
        // This test doesn't include eval. But added here for coverage.
        String str =
                "import " + Person.class.getCanonicalName() + ";\n" +
                        "global java.util.List list;\n" +

                        "rule R1\n" +
                        "    when\n" +
                        "        String( )\n" +
                        "        $p : Person( name == \"John\")\n" +
                        "        Integer( )\n" +
                        "    then\n" +
                        "        list.add(\"R1 : \" + $p.getName());\n" +
                        "end\n" +
                        "rule ModifyingRule\n" +
                        "    when\n" +
                        "        String( this == \"go\" )\n" +
                        "        $p : Person( name == \"Paul\" )\n" +
                        "    then\n" +
                        "        modify( $p ) {\n" +
                        "            setAge(10)\n" +
                        "        }\n" +
                        "        list.add(\"ModifyingRule\");\n" +
                        "end\n";

        KieSession ksession = getKieSession(runType, str);
        List<String> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        Person paul = new Person("Paul", 20);
        ksession.insert("go");
        ksession.insert(paul);
        ksession.insert(0); // any Integer matches

        ksession.fireAllRules();
        assertThat(list).as("R1 should not fire").containsExactly("ModifyingRule");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testModifyEvalAfterJoinWithMatchingAlphaSharingJoin_withoutWatch_shouldNotFire(RUN_TYPE runType) {
        testModifyEvalAfterJoinWithMatchingAlphaSharingJoin(runType, "", false);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testModifyEvalAfterJoinWithMatchingAlphaSharingJoin_withWatch_shouldFire(RUN_TYPE runType) {
        testModifyEvalAfterJoinWithMatchingAlphaSharingJoin(runType, "@watch( age )", true);
    }

    private void testModifyEvalAfterJoinWithMatchingAlphaSharingJoin(RUN_TYPE runType, String watchAnnotation, boolean shouldFireR1) {
        // This rule is similar to testModifyEvalAfterJoinWithMatchingAlpha, but sharing JoinNode
        // If AlphaNode or JoinNode disables property reactive, this rule would cause an infinite loop
        String str =
                "import " + Person.class.getCanonicalName() + ";\n" +
                        "global java.util.List list;\n" +
                        "rule R1\n" +
                        "    when\n" +
                        "        String( this == \"go\" )\n" +
                        "        $p : Person( name == \"Paul\") " + watchAnnotation + "\n" + // without watch, JoinNode doesn't proceed on modify
                        "        eval($p.getAge() == 10) \n" +
                        "    then\n" +
                        "        list.add(\"R1 : \" + $p.getName());\n" +
                        "end\n" +
                        "rule ModifyingRule\n" +
                        "    when\n" +
                        "        String( this == \"go\" )\n" +
                        "        $p : Person( name == \"Paul\")\n" +
                        "    then\n" +
                        "        modify( $p ) {\n" +
                        "            setAge(10)\n" +
                        "        }\n" +
                        "        list.add(\"ModifyingRule\");\n" +
                        "end\n";

        KieSession ksession = getKieSession(runType, str);
        List<String> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        Person paul = new Person("Paul", 20);
        ksession.insert("go");
        ksession.insert(paul);
        ksession.insert(0);

        ksession.fireAllRules(10); // 10 doesn't affect the test execution, but just to avoid an unexpected loop for the future.

        if (shouldFireR1) {
            assertThat(list).as("Both rules should fire").containsExactly("ModifyingRule", "R1 : Paul");
        } else {
            assertThat(list).as("R1 should not fire").containsExactly("ModifyingRule");
        }
    }

    @ParameterizedTest
    @MethodSource("parametersStandardOnly") // standard only: inline eval with ternary is not supported in exec-model.
                                            // See https://github.com/apache/incubator-kie/issues/7097
    void testTernaryEvalInsidePattern(RUN_TYPE runType) {
        String str =
                "import " + MyPerson.class.getCanonicalName() + ";\n" +
                        "rule \"TernaryEvalLoop\"\n" +
                        "dialect \"mvel\"\n" +
                        "when\n" +
                        "  String(this == \"go\")\n" +
                        "  $p : MyPerson( eval(\"foo\" == \"foo\" ? \"foo\" == flag1 : \"foo\" == flag2) )\n" +
                        "then\n" +
                        "  modify($p) {\n" +
                        "    setOtherAttribute(\"done\")\n" +
                        "  }\n" +
                        "end";

        //-- 1st round

        KieSession ksession = getKieSession(runType, str);
        try {
            MyPerson person = new MyPerson();
            person.setFlag1("foo"); // matches the rule
            person.setFlag2("bar");

            ksession.insert("go");
            ksession.insert(person);
            int fired = ksession.fireAllRules(10);

            assertThat(fired).isEqualTo(1);
            assertThat(person.getOtherAttribute()).isEqualTo("done");
        } finally {
            ksession.dispose();
        }

        //-- 2nd round

        ksession = getKieSession(runType, str);
        try {
            MyPerson person = new MyPerson();
            person.setFlag1("bar"); // doesn't match the rule
            person.setFlag2("bar");

            ksession.insert("go");
            ksession.insert(person);
            int fired = ksession.fireAllRules(10); // do not fire

            assertThat(fired).isZero();
            assertThat(person.getOtherAttribute()).isNull();
        } finally {
            ksession.dispose();
        }
    }

    public static class MyPerson {
        private String flag1;
        private String flag2;
        private String otherAttribute;

        public String getFlag1() {
            return flag1;
        }

        public void setFlag1(String flag1) {
            this.flag1 = flag1;
        }

        public String getFlag2() {
            return flag2;
        }

        public void setFlag2(String flag2) {
            this.flag2 = flag2;
        }

        public String getOtherAttribute() {
            return otherAttribute;
        }

        public void setOtherAttribute(String otherAttribute) {
            this.otherAttribute = otherAttribute;
        }
    }

}
