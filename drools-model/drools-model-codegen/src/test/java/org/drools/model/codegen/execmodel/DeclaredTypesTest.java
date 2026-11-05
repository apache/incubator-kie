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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.drools.base.rule.IndexableConstraint;
import org.drools.base.rule.constraint.AlphaNodeFieldConstraint;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.AlphaNode;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.model.codegen.execmodel.domain.Address;
import org.drools.model.codegen.execmodel.domain.Person;
import org.drools.model.codegen.execmodel.domain.PlainParent;
import org.drools.model.codegen.execmodel.domain.PositionalParent;
import org.drools.model.codegen.execmodel.domain.Result;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.definition.type.FactField;
import org.kie.api.definition.type.FactType;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

public class DeclaredTypesTest extends BaseModelTest {

    @ParameterizedTest
	@MethodSource("parameters")
    public void testPojo(RUN_TYPE runType) throws Exception {
        String str =
                "import " + Result.class.getCanonicalName() + ";" +
                "import " + Person.class.getCanonicalName() + ";" +
                "\n" +
                "declare POJOPerson\n" +
                "    name : String\n" +
                "    surname : String\n" +
                "    age :  int\n" +
                "end\n" +
                "rule R when\n" +
                "  $p : Person( name.length == 4 )\n" +
                "then\n" +
                "   POJOPerson p = new POJOPerson();\n" +
                "   p.setName($p.getName());\n" +
                "   insert(new Result(p));\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert(new Person("Mario", 40));
        ksession.insert(new Person("Mark", 37));
        ksession.insert(new Person("Edson", 35));
        ksession.fireAllRules();

        Collection<Result> results = getObjectsIntoList(ksession, Result.class);
        assertThat(results.size()).isEqualTo(1);
        Result r = results.iterator().next();
        Object result = r.getValue();
        Class<?> resultClass = result.getClass();
        Method name = resultClass.getMethod("getName");
        assertThat(resultClass.getName()).isEqualTo("defaultpkg.POJOPerson");
        assertThat(name.invoke(result)).isEqualTo("Mark");

        Constructor<?>[] constructors = resultClass.getConstructors();
        assertThat(constructors.length).isEqualTo(2);

        Object instance1 = resultClass.newInstance();
        Constructor<?> ctor = resultClass.getConstructor(String.class, String.class, int.class);
        Object luca = ctor.newInstance("Luca", null, 32);
        Method getName = resultClass.getMethod("getName");
        Method getAge = resultClass.getMethod("getAge");

        assertThat(getName.invoke(luca)).isEqualTo("Luca");
        assertThat(getAge.invoke(luca)).isEqualTo(32);

        assertThat(luca.toString()).isEqualTo("POJOPerson( name=Luca, surname=null, age=32 )");
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testPojoInDifferentPackages(RUN_TYPE runType) throws Exception {
        String ruleWithPojo =
                "package org.drools.pojo.model;" +
                "\n" +
                "declare POJOPerson\n" +
                "    name : String\n" +
                "    surname : String\n" +
                "    age :  int\n" +
                "end\n";

        String rule =
                "package org.drools.pojo;\n" +
                "import " + Result.class.getCanonicalName() + ";" +
                "import " + Person.class.getCanonicalName() + ";" +
                "import org.drools.pojo.model.*;" +
                "\n" +
                "rule R when\n" +
                "  $p : Person( name.length == 4 )\n" +
                "then\n" +
                "   POJOPerson p = new POJOPerson();\n" +
                "   p.setName($p.getName());\n" +
                "   insert(p);\n" +
                "end\n" +
                "rule R2 when\n" +
                "  $p : POJOPerson( name.length == 4 )\n" +
                "then\n" +
                "   insert(new Result($p));\n" +
                "end\n";

        KieSession ksession = getKieSession(runType, rule, ruleWithPojo);

        ksession.insert(new Person("Mario", 40));
        ksession.insert(new Person("Mark", 37));
        ksession.insert(new Person("Edson", 35));
        ksession.fireAllRules();

        Collection<Result> results = getObjectsIntoList(ksession, Result.class);
        assertThat(results.size()).isEqualTo(1);
        Result r = results.iterator().next();
        Object result = r.getValue();
        Class<?> resultClass = result.getClass();
        Method name = resultClass.getMethod("getName");
        assertThat(resultClass.getName()).isEqualTo("org.drools.pojo.model.POJOPerson");
        assertThat(name.invoke(result)).isEqualTo("Mark");

        Constructor<?>[] constructors = resultClass.getConstructors();
        assertThat(constructors.length).isEqualTo(2);

        Object instance1 = resultClass.newInstance();
        Constructor<?> ctor = resultClass.getConstructor(String.class, String.class, int.class);
        Object luca = ctor.newInstance("Luca", null, 32);
        Method getName = resultClass.getMethod("getName");
        Method getAge = resultClass.getMethod("getAge");

        assertThat(getName.invoke(luca)).isEqualTo("Luca");
        assertThat(getAge.invoke(luca)).isEqualTo(32);

        assertThat(luca.toString()).isEqualTo("POJOPerson( name=Luca, surname=null, age=32 )");
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testPojoReferencingEachOthers(RUN_TYPE runType) throws Exception {
        String factA =
                "package org.kie.test;" +
                        "\n" +
                        "declare FactA\n" +
                        "    fieldB: FactB\n" +
                        "end\n";

        String factB =
                "package org.kie.test;" +
                        "\n" +
                        "declare FactB\n" +
                        "    fieldA: FactA\n" +
                        "end\n";

        String rule =
                "package org.kie.test\n" +
                        "rule R1 when\n" +
                        "   $fieldA : FactA( $fieldB : fieldB )\n" +
                        "   FactB( this == $fieldB, fieldA == $fieldA )\n" +
                        "then\n" +
                        "end";

        KieSession ksession = getKieSession(runType, rule, factA, factB);

        ksession.fireAllRules();
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testDeclaredTypeInLhs(RUN_TYPE runType) throws Exception {
        String str =
                "import " + Result.class.getCanonicalName() + ";" +
                "import " + Person.class.getCanonicalName() + ";" +
                "\n" +
                "declare POJOPerson\n" +
                "    name : String\n" +
                "    surname : String\n" +
                "    age :  int\n" +
                "end\n" +
                "rule R1 when\n" +
                "  $p : Person( name.length == 4 )\n" +
                "then\n" +
                "   POJOPerson p = new POJOPerson();\n" +
                "   p.setName($p.getName());\n" +
                "   insert(p);\n" +
                "end\n" +
                "rule R2 when\n" +
                "  $p : POJOPerson( name.length == 4 )\n" +
                "then\n" +
                "   insert(new Result($p));\n" +
                "end\n";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert(new Person("Mario", 40));
        ksession.insert(new Person("Mark", 37));
        ksession.insert(new Person("Edson", 35));
        ksession.fireAllRules();

        Collection<Result> results = getObjectsIntoList(ksession, Result.class);
        assertThat(results.size()).isEqualTo(1);
        Result r = results.iterator().next();
        Object result = r.getValue();
        Class<?> resultClass = result.getClass();
        Method name = resultClass.getMethod("getName");
        assertThat(resultClass.getName()).isEqualTo("defaultpkg.POJOPerson");
        assertThat(name.invoke(result)).isEqualTo("Mark");

        Constructor<?>[] constructors = resultClass.getConstructors();
        assertThat(constructors.length).isEqualTo(2);

        Object instance1 = resultClass.newInstance();
        Constructor<?> ctor = resultClass.getConstructor(String.class, String.class, int.class);
        Object luca = ctor.newInstance("Luca", null, 32);
        Method getName = resultClass.getMethod("getName");
        Method getAge = resultClass.getMethod("getAge");

        assertThat(getName.invoke(luca)).isEqualTo("Luca");
        assertThat(getAge.invoke(luca)).isEqualTo(32);

        assertThat(luca.toString()).isEqualTo("POJOPerson( name=Luca, surname=null, age=32 )");
    }

    public static class MyNumber {

        private final int value;

        public MyNumber(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public boolean isEven() {
            return value % 2 == 0;
        }

        @Override
        public String toString() {
            return "MyNumber [value=" + value + "]";
        }
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testPojoPredicateIsUsedAsConstraint(RUN_TYPE runType) {
        String str = "import " + MyNumber.class.getCanonicalName() + ";" +
                     "rule R when\n" +
                     "  MyNumber(even, $value : value)" +
                     "then\n" +
                     "  insert($value);\n" +
                     "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert(new MyNumber(2));
        ksession.fireAllRules();

        Collection<Integer> results = getObjectsIntoList(ksession, Integer.class);
        assertThat(results.contains(2)).isTrue();

        ksession.insert(new MyNumber(1));
        ksession.fireAllRules();

        results = getObjectsIntoList(ksession, Integer.class);
        assertThat(results.contains(2)).isTrue();
        assertThat(results.contains(1)).isFalse(); // This is because MyNumber(1) would fail for "even" predicate/getter used here in pattern as a constraint.
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testPojoPredicateIsUsedAsConstraintOK(RUN_TYPE runType) {
        String str = "import " + MyNumber.class.getCanonicalName() + ";" +
                     "rule R when\n" +
                     "  $n : MyNumber(even, $value : value)" +
                     "then\n" +
                     "  insert($value);\n" +
                     "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert(new MyNumber(2));
        ksession.fireAllRules();

        Collection<Integer> results = getObjectsIntoList(ksession, Integer.class);
        assertThat(results.contains(2)).isTrue();

        ksession.insert(new MyNumber(1));
        ksession.fireAllRules();

        results = getObjectsIntoList(ksession, Integer.class);
        assertThat(results.contains(2)).isTrue();
        assertThat(results.contains(1)).isFalse(); // This is because MyNumber(1) would fail for "even" predicate/getter used here in pattern as a constraint.
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testBindingOfPredicateIsNotUsedAsConstraint(RUN_TYPE runType) {
        String str = "import " + MyNumber.class.getCanonicalName() + ";" +
                     "rule R when\n" +
                     "  MyNumber($even : even, $value : value)" +
                     "then\n" +
                     "  insert($value);\n" +
                     "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert(new MyNumber(2));
        ksession.fireAllRules();

        Collection<Integer> results = getObjectsIntoList(ksession, Integer.class);
        assertThat(results.contains(2)).isTrue();

        ksession.insert(new MyNumber(1));
        ksession.fireAllRules();

        results = getObjectsIntoList(ksession, Integer.class);
        assertThat(results.contains(2)).isTrue();
        assertThat(results.contains(1)).isTrue(); // This is because MyNumber(1) would simply bind for "even" predicate/getter to $even variable, and not used as a constraint.
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testDeclaredWithAllPrimitives(RUN_TYPE runType) {
        String str = "declare DeclaredAllPrimitives\n" +
                     "    my_byte    : byte    \n" +
                     "    my_short   : short   \n" +
                     "    my_int     : int     \n" +
                     "    my_long    : long    \n" +
                     "    my_float   : float   \n" +
                     "    my_double  : double  \n" +
                     "    my_char    : char    \n" +
                     "    my_boolean : boolean \n" +
                     "end\n" +
                     "rule R\n" +
                     "when\n" +
                     "then\n" +
                     "  insert(new DeclaredAllPrimitives((byte) 1, (short) 1, 1, 1L, 1f, 1d, 'x', true));\n" +
                     "end";

        KieSession ksession = getKieSession(runType, str);

        ksession.fireAllRules();

        List<Object> results = getObjectsIntoList(ksession, Object.class);
        assertThat(results.size()).isEqualTo(1);
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testFactType(RUN_TYPE runType) throws Exception {
        // DROOLS-4784
        String str =
                "package org.test;\n" +
                "import " + Person.class.getCanonicalName() + ";" +
                "declare Name\n" +
                "    VALUE : String\n" +
                "end\n" +
                "rule R when\n" +
                "    Name($v : VALUE == \"Mario\")\n" +
                "then\n" +
                "    insert($v);" +
                "end";

        KieSession ksession = getKieSession(runType, str);

        FactType nameType = ksession.getKieBase().getFactType("org.test", "Name");
        Object name = nameType.newInstance();
        nameType.set(name, "VALUE", "Mario");

        ksession.insert(name);
        ksession.fireAllRules();

        assertThat(nameType.get(name, "VALUE")).isEqualTo("Mario");

        Collection<String> results = getObjectsIntoList(ksession, String.class);
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.iterator().next()).isEqualTo("Mario");

        EntryPointNode epn = ((InternalRuleBase) ksession.getKieBase()).getRete().getEntryPointNodes().values().iterator().next();
        Iterator<ObjectTypeNode> otns = epn.getObjectTypeNodes().values().iterator();
        ObjectTypeNode otn = otns.next();
        if (otn.toString().contains( "InitialFact" )) {
            otn = otns.next();
        }
        AlphaNode alpha = (AlphaNode)otn.getSinks()[0];
        AlphaNodeFieldConstraint constraint = alpha.getConstraint();
        int index = (( IndexableConstraint ) constraint).getFieldExtractor().getIndex();
        assertThat(index >= 0).isTrue();
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testFactTypeNotUsedInRule(RUN_TYPE runType) throws Exception {
        String str =
                "package org.test;\n" +
                "import " + Person.class.getCanonicalName() + ";" +
                "declare Name\n" +
                "    value : String\n" +
                "end\n" +
                "declare ExtendedName extends Name\n" +
                "end\n" +
                "rule R when\n" +
                "    Name($v : value == \"Mario\")\n" +
                "then\n" +
                "    insert($v);" +
                "end";

        KieSession ksession = getKieSession(runType, str);

        FactType nameType = ksession.getKieBase().getFactType("org.test", "ExtendedName");
        Object name = nameType.newInstance();
        nameType.set(name, "value", "Mario");

        ksession.insert(name);
        ksession.fireAllRules();

        assertThat(nameType.get(name, "value")).isEqualTo("Mario");

        Collection<String> results = getObjectsIntoList(ksession, String.class);
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.iterator().next()).isEqualTo("Mario");
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testTypeDeclarationsInheritance(RUN_TYPE runType) throws Exception {
        String str =
                "declare Person\n" +
                "    id : int @key\n" +
                "    name : String\n" +
                "end\n" +
                "declare Employee extends Person\n" +
                "    salary : double\n" +
                "end\n" +
                "rule \"TestConstructors\" when\n" +
                "    then\n" +
                "        Person p1 = new Person();\n" +
                "        Person p2 = new Person(9);\n" +
                "        Person p3 = new Person(99, \"myname\");\n" +
                "\n" +
                "        Employee e1 = new Employee();\n" +
                "        Employee e2 = new Employee(9);\n" +
                "        Employee e3 = new Employee(99, \"myname\", 100.00);\n" +
                "\n" +
                "        Person pe = new Employee();\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testEnum(RUN_TYPE runType) {
        String str =
                "import " + Result.class.getCanonicalName() + ";" +
                "import " + Person.class.getCanonicalName() + ";" +
                "declare enum PersonAge\n" +
                "    ELEVEN(11);\n" +
                "\n" +
                "    key: int\n" +
                "end\n" +
                "\n" +
                "rule \"0_SomeRule\"\n" +
                "    when\n" +
                "            $p : Person ()\n" +
                "    then\n" +
                "            $p.setAge(PersonAge.ELEVEN.getKey());\n" +
                "            insert(new Result($p));\n" +
                "end\n";

        KieSession ksession = getKieSession(runType, str);

        ksession.insert(new Person("Mario"));
        ksession.fireAllRules();

        Collection<Result> results = getObjectsIntoList(ksession, Result.class);
        assertThat(results.size()).isEqualTo(1);
        Person p = (Person) results.iterator().next().getValue();
        assertThat(p.getAge()).isEqualTo(11);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testEnumWithMisspelledConstantFailsAtCompileTime(RUN_TYPE runType) {
        // Diagnostic experience: a typo in a cross-enum reference must fail at
        // compile time (with KieBuilder error messages), not at runtime via a
        // mysterious ExceptionInInitializerError on first enum access.
        String str =
                "package org.example;\n" +
                "declare enum Color\n" +
                "    RED, GREEN, BLUE;\n" +
                "end\n" +
                "declare enum Light\n" +
                "    STOP( Color.RED ),\n" +
                "    GO(   Color.GRENE );\n" +  // typo
                "    color: Color\n" +
                "end\n";

        Results results = createKieBuilder(runType, str).getResults();
        List<Message> errors = results.getMessages(Message.Level.ERROR);
        assertThat(errors).as("expected a compile error naming the bad constant").isNotEmpty();
        assertThat(errors.toString()).contains("GRENE");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testEnumWithDeclaredEnumField(RUN_TYPE runType) {
        String str =
                "package org.example;\n" +
                "import " + Result.class.getCanonicalName() + ";\n" +
                "\n" +
                "declare enum Category\n" +
                "    TARGET(\"target\"),\n" +
                "    NON_TARGET(\"non-target\"),\n" +
                "    NEW_LESION(\"new\");\n" +
                "\n" +
                "    code: String\n" +
                "end\n" +
                "\n" +
                "declare enum LesionState\n" +
                "    TARGET_PRESENT(    org.example.Category.TARGET, \"present\" ),\n" +    // fully-qualified
                "    NON_TARGET_ABSENT( Category.NON_TARGET,          \"absent\"  ),\n" +   // simple (same package)
                "    NEW_PRESENT(       Category.NEW_LESION,          \"present\" );\n" +   // simple (same package)
                "\n" +
                "    category: org.example.Category\n" +
                "    nominal: String\n" +
                "end\n" +
                "\n" +
                "rule \"emit\"\n" +
                "    when\n" +
                "    then\n" +
                "        insert(new Result(LesionState.TARGET_PRESENT.getCategory().getCode()));\n" +
                "        insert(new Result(LesionState.NON_TARGET_ABSENT.getCategory() == Category.NON_TARGET));\n" +
                "end\n";

        KieSession ksession = getKieSession(runType, str);
        ksession.fireAllRules();

        Collection<Result> results = getObjectsIntoList(ksession, Result.class);
        assertThat(results).extracting(Result::getValue).contains("target", true);
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testDeclaredSlidingWindowOnEventInTypeDeclaration(RUN_TYPE runType) throws Exception {
        String str =
                "package org.test;\n" +
                "declare MyPojo\n" +
                "  @serialVersionUID( 42 )\n" +
                "end\n" +
                "rule R when then insert(new MyPojo()); end\n";

        KieSession ksession = getKieSession(runType, str);
        ksession.fireAllRules();

        Object pojo = getObjectsIntoList(ksession, Object.class).iterator().next();
        Field f = pojo.getClass().getDeclaredField( "serialVersionUID" );
        f.setAccessible( true );
        assertThat((long) f.get(pojo)).isEqualTo(42L);
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testNestedDateConstraint(RUN_TYPE runType) throws Exception {
        String str =
                "package org.test;\n" +
                "declare Fact\n" +
                "    n : Nested\n" +
                "end\n" +
                "declare Nested\n" +
                "    d : java.util.Date\n" +
                "end\n" +
                "\n" +
                "rule \"with nested date\" when\n" +
                "    Fact(n.d >= \"01-Jan-2020\")\n" +
                "then\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);
        KieBase kbase = ksession.getKieBase();

        FactType factType = kbase.getFactType("org.test", "Fact");
        FactType nestedType = kbase.getFactType("org.test", "Nested");

        Object f1 = factType.newInstance();
        Object n1 = nestedType.newInstance();

        SimpleDateFormat df = new SimpleDateFormat("dd-MMM-yyyy", Locale.UK);

        nestedType.set(n1, "d", df.parse("01-Jan-2020"));
        factType.set(f1, "n", n1);

        ksession.insert(f1);
        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testExtendPojo(RUN_TYPE runType) throws Exception {
        String str =
                "package org.test;\n" +
                "import " + Person.class.getCanonicalName() + ";" +
                "declare MyPerson extends Person\n" +
                "    style : String\n" +
                "end\n" +
                "\n" +
                "rule \"with nested date\" when\n" +
                "    MyPerson(name == \"Mario\", style == \"Steampunk\")\n" +
                "then\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);
        KieBase kbase = ksession.getKieBase();

        FactType factType = kbase.getFactType("org.test", "MyPerson");
        assertThat(factType.getField("name").getType()).isEqualTo(String.class);
        assertThat(factType.getField("style").getType()).isEqualTo(String.class);

        Object f1 = factType.newInstance();
        factType.set(f1, "name", "Mario");
        factType.set(f1, "style", "Steampunk");

        ksession.insert(f1);
        assertThat(ksession.fireAllRules()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("parametersPatternOnly")
    public void testExtendPojoInheritedFieldsConstructor(RUN_TYPE runType) throws Exception {
        // A declared type extending a Java (classpath) class must generate a full-argument
        // constructor that also includes the superclass @Position fields and forwards them to
        // super(...). PositionalParent has @Position(0) name, @Position(1) age, and @Position(2) salary,
        // so Child should expose Child(String name, int age, long salary, String dept). The executable
        // model previously omitted the inherited fields, generating only Child(String dept), which
        // failed to compile the 'new Child("Mario", 40, 50L, "Sales")' consequent.
        // Note that in case of non-exec-model, a generated constructor arg order doesn't respect @Position,
        // which means non-deterministic. So this test is only for exec-model.
        String str =
                "package org.test;\n" +
                "import " + PositionalParent.class.getCanonicalName() + ";\n" +
                "import " + Result.class.getCanonicalName() + ";\n" +
                "declare PositionalParent end\n" +
                "declare Child extends PositionalParent\n" +
                "    dept : String\n" +
                "end\n" +
                "rule Init when\n" +
                "then\n" +
                "    insert( new Child(\"Mario\", 40, 50L, \"Sales\") );\n" +
                "end\n" +
                "rule Check when\n" +
                "    $c : Child( name == \"Mario\", age == 40, dept == \"Sales\" )\n" +
                "then\n" +
                "    insert( new Result($c.getName()) );\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);
        assertThat(ksession.fireAllRules()).isEqualTo(2);

        Collection<Result> results = getObjectsIntoList(ksession, Result.class);
        assertThat(results).hasSize(1);
        assertThat(results.iterator().next().getValue()).isEqualTo("Mario");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testDeclaredTypeExtendingDeclaredTypeInDifferentPackage(RUN_TYPE runType) {
        // A declared type that extends a declared type in a DIFFERENT package must generate a
        // full-argument constructor that includes the inherited fields and forwards them to super(...).
        // Here Child (com.test.childpkg) extends Parent (com.test.parentpkg), so the consequent
        // 'new Child("Mario", 40, "Sales")' must compile and the inherited name/age must be populated.
        String parentDrl =
                "package com.test.parentpkg;\n" +
                "declare Parent\n" +
                "  name : String\n" +
                "  age : int\n" +
                "end\n";
        String childDrl =
                "package com.test.childpkg;\n" +
                "import com.test.parentpkg.Parent;\n" +
                "import " + Result.class.getCanonicalName() + ";\n" +
                "declare Child extends Parent\n" +
                "  dept : String\n" +
                "end\n" +
                "rule Init when\n" +
                "then insert( new Child(\"Mario\", 40, \"Sales\") ); end\n" +
                "rule Chk when\n" +
                "  Child( name == \"Mario\", age == 40, dept == \"Sales\" )\n" +
                "then insert( new Result(\"ok\") ); end";
        KieSession ksession = getKieSession(runType, parentDrl, childDrl);
        assertThat(ksession.fireAllRules()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testDeclaredExtendsAmbiguousSimpleNameResolvedByImport(RUN_TYPE runType) {
        // Two packages declare a type with the same simple name (Base). The child explicitly imports
        // one of them, so its 'extends Base' must resolve to that package's Base - not to another
        // package's same-named declaration. Here Sub imports b.pkg.Base, whose only field is 'city',
        // so the constructor is Sub(String city, String dept). Resolving to a.pkg.Base (name/age)
        // instead would make the consequent 'new Sub("NYC", "Sales")' fail to compile. Runs under both
        // engines: the legacy path already resolved this correctly; exec-model (#6782) previously
        // matched the first same-named declaration found while scanning packages.
        String aBaseDrl =
                "package a.pkg;\n" +
                "declare Base\n" +
                "  name : String\n" +
                "  age : int\n" +
                "end\n";
        String bBaseDrl =
                "package b.pkg;\n" +
                "declare Base\n" +
                "  city : String\n" +
                "end\n";
        String subDrl =
                "package sub;\n" +
                "import b.pkg.Base;\n" +
                "import " + Result.class.getCanonicalName() + ";\n" +
                "declare Sub extends Base\n" +
                "  dept : String\n" +
                "end\n" +
                "rule Init when\n" +
                "then insert( new Sub(\"NYC\", \"Sales\") ); end\n" +
                "rule Chk when\n" +
                "  Sub( city == \"NYC\", dept == \"Sales\" )\n" +
                "then insert( new Result(\"ok\") ); end";
        KieSession ksession = getKieSession(runType, aBaseDrl, bBaseDrl, subDrl);
        assertThat(ksession.fireAllRules()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("parametersPatternOnly")
    public void testExtendPojoWithoutPositionUsesNoArgSuper(RUN_TYPE runType) {
        // Executable-model regression for #6779: a declared type extending a Java class that has NO
        // @Position fields must not pull the superclass's instance fields into the generated
        // constructor - doing so would emit a super(...) call with no matching superclass constructor
        // and fail to compile ("The constructor Child(String) is undefined"). Only the declared type's
        // own fields are constructor parameters, and a no-arg super() is used. PlainParent has a single
        // (unannotated) field and only a no-arg constructor. (Exec-model codegen only; the classic
        // compiler handles declared-extends-POJO via a different path.)
        String str =
                "package org.test;\n" +
                "import " + PlainParent.class.getCanonicalName() + ";\n" +
                "import " + Result.class.getCanonicalName() + ";\n" +
                "declare Child extends PlainParent\n" +
                "    dept : String\n" +
                "end\n" +
                "rule Init when\n" +
                "then\n" +
                "    insert( new Child(\"Sales\") );\n" +
                "end\n" +
                "rule Check when\n" +
                "    $c : Child( dept == \"Sales\" )\n" +
                "then\n" +
                "    insert( new Result($c.getDept()) );\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);
        assertThat(ksession.fireAllRules()).isEqualTo(2);

        Collection<Result> results = getObjectsIntoList(ksession, Result.class);
        assertThat(results).hasSize(1);
        assertThat(results.iterator().next().getValue()).isEqualTo("Sales");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testNonDefinedCustomAnnotation(RUN_TYPE runType) {
        String str = "package org.example.custom; \n" +
                "import " + Date.class.getCanonicalName() + ";\n" +
                "import " + Address.class.getCanonicalName() + ";\n" +
                "\n" +
                "declare Person\n" +
                "    @xxxAuthor( Bob )\n" +
                "    @role(event)\n" + // @role is a built-in annotation
                "\n" +
                "    name : String @key @xxxMaxLength( 30 )\n" + // @key is a built-in annotation
                "    dateOfBirth : Date\n" +
                "    address : Address\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);
        KieBase kBase = ksession.getKieBase();

        FactType person = kBase.getFactType( "org.example.custom", "Person" );

        Map<String, Object> classMetaData = person.getMetaData();
        assertThat(classMetaData).isNotNull();
        assertThat(classMetaData.get("xxxAuthor")).isEqualTo("Bob");
        assertThat(classMetaData.containsKey("role")).isTrue();
        assertThat(classMetaData.get("event")).isNull();

        FactField field = person.getField("name" );
        Map<String, Object> fieldMetaData = field.getMetaData();
        assertThat(fieldMetaData).isNotNull();
        assertThat(fieldMetaData.get("xxxMaxLength")).isEqualTo("30");
        assertThat(fieldMetaData.containsKey("key")).isTrue();
        assertThat(fieldMetaData.get("key")).isNull();
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testDefinedCustomAnnotation(RUN_TYPE runType) throws Exception {
        String str = "package org.example.custom; \n" +
                "import " + Date.class.getCanonicalName() + ";\n" +
                "import " + Address.class.getCanonicalName() + ";\n" +
                "import " + MyAuthor.class.getCanonicalName() + ";\n" +
                "import " + MyMaxLength.class.getCanonicalName() + ";\n" +
                "\n" +
                "declare Person\n" +
                "    @MyAuthor( \"Bob\" )\n" +
                "\n" +
                "    name : String @MyMaxLength( 30 )\n" +
                "    dateOfBirth : Date\n" +
                "    address : Address\n" +
                "end";

        KieSession ksession = getKieSession(runType, str);
        KieBase kBase = ksession.getKieBase();

        FactType person = kBase.getFactType( "org.example.custom", "Person" );

        Class<?> factClass = person.getFactClass();
        MyAuthor classAnnotation = factClass.getAnnotation(MyAuthor.class);
        assertThat(classAnnotation).isNotNull();
        assertThat(classAnnotation.value()).isEqualTo("Bob");

        Field factField = factClass.getDeclaredField("name");
        MyMaxLength fieldAnnotation = factField.getAnnotation(MyMaxLength.class);
        assertThat(fieldAnnotation).isNotNull();
        assertThat(fieldAnnotation.value()).isEqualTo(30);

        Map<String, Object> classMetaData = person.getMetaData();
        assertThat(classMetaData).as("Defined custom annotation should not be stored in metaData").isNull();

        FactField field = person.getField("name" );
        Map<String, Object> fieldMetaData = field.getMetaData();
        assertThat(fieldMetaData).as("Defined custom annotation should not be stored in metaData").isNull();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface MyAuthor {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface MyMaxLength {
        int value();
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testDefinedCustomAnnotationWithUnknownProperty(RUN_TYPE runType) {
        String str = "package org.example.custom; \n" +
                "import " + Date.class.getCanonicalName() + ";\n" +
                "import " + Address.class.getCanonicalName() + ";\n" +
                "import " + MyAuthor.class.getCanonicalName() + ";\n" +
                "import " + MyMaxLength.class.getCanonicalName() + ";\n" +
                "\n" +
                "declare Person\n" +
                "    @MyAuthor( yyy = \"Bob\" )\n" +
                "\n" +
                "    name : String @MyMaxLength( zzz = 30 )\n" +
                "    dateOfBirth : Date\n" +
                "    address : Address\n" +
                "end";

        Results results = createKieBuilder(runType, str).getResults();

        assertThat(results.getMessages(Message.Level.ERROR))
                .hasSize(2)
                .extracting(Message::getText)
                .containsExactlyInAnyOrder("Unknown annotation property yyy",
                                           "Unknown annotation property zzz");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testInvalidAnnotationValueReportsBuildError(RUN_TYPE runType) {
        String str = "package org.example.invalid; \n" +
                "declare Foo\n" +
                "    @role( event )\n" +
                "    @expires( abc )\n" +
                "    name : String\n" +
                "end";

        Results results = createKieBuilder(runType, str).getResults();

        assertThat(results.getMessages(Message.Level.ERROR))
                .extracting(Message::getText)
                .anyMatch(text -> text.contains("Error parsing time string: [ abc ]"));
    }
}