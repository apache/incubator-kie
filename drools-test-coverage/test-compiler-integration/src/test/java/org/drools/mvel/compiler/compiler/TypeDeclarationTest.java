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
package org.drools.mvel.compiler.compiler;

import java.io.StringReader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.drools.compiler.builder.impl.KnowledgeBuilderImpl;
import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.core.common.DefaultEventHandle;
import org.drools.base.definitions.impl.KnowledgePackageImpl;
import org.drools.base.rule.TypeDeclaration;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestConstants;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.type.Annotation;
import org.kie.api.definition.type.FactField;
import org.kie.api.definition.type.FactType;
import org.kie.api.definition.type.Role;
import org.kie.api.io.KieResources;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.builder.KnowledgeBuilderResults;
import org.kie.internal.builder.ResultSeverity;
import org.kie.internal.io.ResourceFactory;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

public class TypeDeclarationTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testClassNameClashing(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str = "";
        str += "package org.kie \n" +
        		"declare org.kie.Character \n" +
        		"    name : String \n" +
        		"end \n";

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromDrls(kieBaseTestConfiguration, false, str);
        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
        assertThat(errors.isEmpty()).as(errors.toString()).isTrue();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testAnnotationReDefinition(){
        String str1 = "";
        str1 += "package org.kie \n" +
        		"declare org.kie.EventA \n" +
        		"    name : String \n" +
        		"    duration : Long \n" +
        		"end \n";

        String str2 = "";
        str2 += "package org.kie \n" +
        		"declare org.kie.EventA \n" +
        		"    @role (event) \n" +
        		"    @duration (duration) \n" +
        		"end \n";

        // Note: Didn't applied new APIs because KnowledgePackageImpl.getTypeDeclaration() doesn't return an expected TypeDeclaration
        // TODO: File a JIRA to revisit and write a valid test for new APIs and exec-model

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();

        kbuilder.add( ResourceFactory.newByteArrayResource( str1.getBytes() ),
                      ResourceType.DRL );

        kbuilder.add( ResourceFactory.newByteArrayResource( str2.getBytes() ),
                      ResourceType.DRL );

        if (kbuilder.hasErrors() ) {
           fail( kbuilder.getErrors().toString() );
        }

        //No Warnings
        KnowledgeBuilderResults warnings = kbuilder.getResults(ResultSeverity.WARNING);
        assertThat(warnings.size()).isEqualTo(0);

        //just 1 package was created
        assertThat(kbuilder.getKnowledgePackages().size()).isEqualTo(1);

        //Get the Fact Type for org.kie.EventA
        FactType factType = ((KnowledgePackageImpl)kbuilder.getKnowledgePackages().iterator().next()).getFactType("org.kie.EventA");
        assertThat(factType).isNotNull();

        //'name' field must still be there
        FactField field = factType.getField("name");
        assertThat(field).isNotNull();

        //'duration' field must still be there
        field = factType.getField("duration");
        assertThat(field).isNotNull();

        //New Annotations must be there too
        TypeDeclaration typeDeclaration = ((KnowledgePackageImpl)kbuilder.getKnowledgePackages().iterator().next()).getTypeDeclaration("EventA");

        assertThat(typeDeclaration.getRole()).isEqualTo(Role.Type.EVENT);
        assertThat(typeDeclaration.getDurationAttribute()).isEqualTo("duration");

    }

    private static final String EVENT_WITH_DURATION =
            "package org.kie \n" +
            "declare org.kie.EventA \n" +
            "    @role (event) \n" +
            "    @duration (duration) \n" +
            "    name : String \n" +
            "    duration : Long \n" +
            "    duration2 : Long \n" +
            "end \n";

    @Test
    public void testDurationRedeclarationWithSameDuration() {
        KnowledgeBuilder kbuilder = buildTwice(EVENT_WITH_DURATION, EVENT_WITH_DURATION);

        assertThat(kbuilder.hasErrors()).as(kbuilder.getErrors().toString()).isFalse();
        TypeDeclaration typeDeclaration = ((KnowledgePackageImpl)kbuilder.getKnowledgePackages().iterator().next()).getTypeDeclaration("EventA");
        assertThat(typeDeclaration.getDurationAttribute()).isEqualTo("duration");
    }

    @Test
    public void testDurationRedeclarationWithoutDurationKeepsIt() {
        String str2 = EVENT_WITH_DURATION.replace("    @duration (duration) \n", "");

        KnowledgeBuilder kbuilder = buildTwice(EVENT_WITH_DURATION, str2);

        assertThat(kbuilder.hasErrors()).as(kbuilder.getErrors().toString()).isFalse();
        TypeDeclaration typeDeclaration = ((KnowledgePackageImpl)kbuilder.getKnowledgePackages().iterator().next()).getTypeDeclaration("EventA");
        assertThat(typeDeclaration.getDurationAttribute()).isEqualTo("duration");
    }

    @Test
    public void testDurationRedeclarationWithDifferentDuration() {
        String str2 = EVENT_WITH_DURATION.replace("@duration (duration)", "@duration (duration2)");

        KnowledgeBuilder kbuilder = buildTwice(EVENT_WITH_DURATION, str2);

        assertThat(kbuilder.getErrors().toString()).contains("has a different duration: duration2 != duration");
    }

    private static final String FACT_FOO =
            "package org.kie \n" +
            "declare org.kie.Foo \n" +
            "    name : String \n" +
            "end \n";

    private static String redeclareFoo(String annotations) {
        return "package org.kie \n" +
               "declare org.kie.Foo \n" +
               "    " + annotations + " \n" +
               "end \n";
    }

    private static TypeDeclaration getFooAfterRedeclaration(String drl1, String drl2) {
        KnowledgeBuilder kbuilder = buildTwice(drl1, drl2);
        assertThat(kbuilder.hasErrors()).as(kbuilder.getErrors().toString()).isFalse();
        return ((KnowledgePackageImpl)kbuilder.getKnowledgePackages().iterator().next()).getTypeDeclaration("Foo");
    }

    @Test
    public void testRedeclarationWithClassReactive() {
        TypeDeclaration typeDeclaration = getFooAfterRedeclaration(FACT_FOO, redeclareFoo("@classReactive"));
        assertThat(typeDeclaration.isPropertyReactive()).isFalse();
    }

    @Test
    public void testRedeclarationWithPropertyChangeSupport() {
        TypeDeclaration typeDeclaration = getFooAfterRedeclaration(FACT_FOO, redeclareFoo("@propertyChangeSupport"));
        assertThat(typeDeclaration.isDynamic()).isTrue();
    }

    @Test
    public void testRedeclarationWithRoleFact() {
        String eventFoo = FACT_FOO.replace("declare org.kie.Foo \n", "declare org.kie.Foo \n    @role (event) \n");
        TypeDeclaration typeDeclaration = getFooAfterRedeclaration(eventFoo, redeclareFoo("@role(fact)"));
        assertThat(typeDeclaration.getRole()).isEqualTo(Role.Type.FACT);
    }

    @Test
    public void testRedeclarationWithExpiresWithoutRole() {
        TypeDeclaration typeDeclaration = getFooAfterRedeclaration(FACT_FOO, redeclareFoo("@expires(10s)"));
        assertThat(typeDeclaration.getExpirationOffset()).isEqualTo(10_000L);
    }

    private static KnowledgeBuilder buildTwice(String drl1, String drl2) {
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newByteArrayResource( drl1.getBytes() ), ResourceType.DRL );
        kbuilder.add( ResourceFactory.newByteArrayResource( drl2.getBytes() ), ResourceType.DRL );
        return kbuilder;
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testNoAnnotationUpdateIfError(){
        String str1 = "";
        str1 += "package org.drools.mvel.compiler \n" +
        		"declare org.drools.EventA \n" +
        		"    name : String \n" +
        		"    duration : Long \n" +
        		"end \n";

        String str2 = "";
        str2 += "package org.drools.mvel.compiler \n" +
        		"declare org.drools.EventA \n" +
        		"    @role (event) \n" +
        		"    @duration (duration) \n" +
        		"    anotherField : String \n" +
        		"end \n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();

        kbuilder.add( ResourceFactory.newByteArrayResource(str1.getBytes()),
                      ResourceType.DRL );

        kbuilder.add( ResourceFactory.newByteArrayResource( str2.getBytes() ),
                      ResourceType.DRL );

        if (!kbuilder.hasErrors() ) {
           fail("Errors Expected");
        }

        //No Warnings
        KnowledgeBuilderResults warnings = kbuilder.getResults(ResultSeverity.WARNING);
        assertThat(warnings.size()).isEqualTo(0);

        //just 1 package was created
        assertThat(kbuilder.getKnowledgePackages().size()).isEqualTo(0);

        // TODO: Odd behavior with new APIs (an error found for single test method. But no error for running the test class or parameterized)
        // File a JIRA to revisit and write a valid test for new APIs and exec-model

//        final Resource drlResource1 = KieServices.Factory.get().getResources().newReaderResource(new StringReader(str1));
//        drlResource1.setSourcePath(TestConstants.TEST_RESOURCES_FOLDER + "rule1x.drl");
//        final Resource drlResource2 = KieServices.Factory.get().getResources().newReaderResource(new StringReader(str2));
//        drlResource2.setSourcePath(TestConstants.TEST_RESOURCES_FOLDER + "rule2x.drl");
//
//        KieBuilder kieBuilder = KieUtil.getKieBuilderFromResources(kieBaseTestConfiguration, false, drlResource1, drlResource2);
//        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
//        System.out.println(errors);
//        assertFalse("Errors Expected", errors.isEmpty());
    }

    /**
     * The same resource (containing a type declaration) is added twice in the
     * kbuilder.
     */
    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testDuplicatedTypeDeclarationWith2FieldsInSameResource(KieBaseTestConfiguration kieBaseTestConfiguration) {
        //same package, different resources
        String str1 = "";
        str1 += "package org.drools.mvel.compiler \n" +
        		"declare org.drools.ClassA \n" +
        		"    name : String \n" +
        		"    lastName : String \n" +
        		"end \n";

        final Resource drlResource = KieServices.Factory.get().getResources().newReaderResource(new StringReader(str1));
        drlResource.setSourcePath(TestConstants.TEST_RESOURCES_FOLDER + "rule1.drl");

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromResources(kieBaseTestConfiguration, false, drlResource, drlResource); // same resource
        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
        assertThat(errors.isEmpty()).as(errors.toString()).isTrue();
    }

    /**
     * 2 resources (containing a the same type declaration) are added to the
     * kbuilder.
     * The expectation here is to silently discard the second type declaration.
     */
    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testDuplicatedTypeDeclarationInDifferentResources(KieBaseTestConfiguration kieBaseTestConfiguration) {
        //same package, different resources
        String str1 = "";
        str1 += "package org.drools.mvel.compiler \n" +
        		"declare org.drools.ClassA \n" +
        		"    name : String \n" +
        		"end \n";

        final Resource drlResource1 = KieServices.Factory.get().getResources().newReaderResource(new StringReader(str1));
        drlResource1.setSourcePath(TestConstants.TEST_RESOURCES_FOLDER + "rule1.drl");
        final Resource drlResource2 = KieServices.Factory.get().getResources().newReaderResource(new StringReader(str1));
        drlResource2.setSourcePath(TestConstants.TEST_RESOURCES_FOLDER + "rule2.drl");

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromResources(kieBaseTestConfiguration, false, drlResource1, drlResource2); // different resources
        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
        assertThat(errors.isEmpty()).as(errors.toString()).isTrue();
    }


    /**
     * 2 resources (containing different declarations of the same type ) are added
     * to the kbuilder.
     * The expectation here is that compilation fails because we are changing
     * the type of a field
     */
    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testClashingTypeDeclarationInDifferentResources(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // Note: Didn't applied new APIs because KieUtil.getKieBuilderFromResources() didn't reproduce the issue.
        // TODO: File a JIRA to revisit and write a valid test for new APIs and exec-model

        //same package, different resources
        String str1 = "";
        str1 += "package org.drools.mvel.compiler \n" +
        		"declare org.drools.ClassA \n" +
        		"    name : String \n" +
        		"    age : Integer \n" +
        		"end \n";

        String str2 = "";
        str2 += "package org.drools.mvel.compiler \n" +
        		"declare org.drools.ClassA \n" +
        		"    name : String \n" +
        		"    age : String \n" +
        		"end \n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();

        kbuilder.add( ResourceFactory.newByteArrayResource( str1.getBytes() ),
                      ResourceType.DRL );

        kbuilder.add( ResourceFactory.newByteArrayResource( str2.getBytes() ),
                      ResourceType.DRL );

        if (!kbuilder.hasErrors() ) {
           fail( "An error should have been generated, redefinition of ClassA is not allowed" );
        }
    }

    /**
     * 2 resources (containing different declarations of the same type ) are added
     * to the kbuilder.
     * The expectation here is to silently discard the second type declaration.
     * This is because the new definition has less fields that the original
     * UPDATE : any use of the full-arg constructor in the second DRL will fail,
     * so we generate an error anyway
     */
    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testNotSoHarmlessTypeReDeclaration(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // Note: Didn't applied new APIs because KieUtil.getKieBuilderFromResources() didn't reproduce the issue.
        // TODO: File a JIRA to revisit and write a valid test for new APIs and exec-model

        //same package, different resources
        String str1 = "";
        str1 += "package org.drools.mvel.compiler \n" +
        		"declare org.drools.ClassA \n" +
        		"    name : String \n" +
        		"    age : Integer \n" +
        		"end \n";

        String str2 = "";
        str2 += "package org.drools.mvel.compiler \n" +
        		"declare org.drools.ClassA \n" +
        		"    name : String \n" +
        		"end \n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();

        kbuilder.add( ResourceFactory.newByteArrayResource( str1.getBytes() ),
                      ResourceType.DRL );

        kbuilder.add( ResourceFactory.newByteArrayResource( str2.getBytes() ),
                      ResourceType.DRL );

        if ( ! kbuilder.hasErrors() ) {
           fail( "An error should have been generated, redefinition of ClassA is not allowed" );
        }

        /*
        //1 Warning
        KnowledgeBuilderResults warnings = kbuilder.getResults( ResultSeverity.WARNING );
        Assert.assertEquals(1, warnings.size());
        System.out.println(warnings.iterator().next().getMessage());

        //just 1 package was created
        Assert.assertEquals(1, kbuilder.getKnowledgePackages().size());

        //Get the Fact Type for org.drools.ClassA
        FactType factType = ((KnowledgePackageImp)kbuilder.getKnowledgePackages().iterator().next()).pkg.getFactType("org.drools.ClassA");
        Assert.assertThat(factType).isNotNull();

        //'age' field must still be there
        FactField field = factType.getField("age");
        Assert.assertThat(field).isNotNull();

        //Assert that the 'name' field must be String and not Long
        Assert.assertEquals(Integer.class, field.getType());
        */
    }

    /**
     * 2 resources (containing different declarations of the same type ) are added
     * to the kbuilder.
     * The expectation here is that the compilation fails because we are
     * adding a new field to the declared Type
     */
    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testTypeReDeclarationWithExtraField(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // Note: Didn't applied new APIs because KieUtil.getKieBuilderFromResources() didn't reproduce the issue.
        // TODO: File a JIRA to revisit and write a valid test for new APIs and exec-model

        //same package, different resources
        String str1 = "";
        str1 += "package org.drools.mvel.compiler \n" +
        		"declare org.drools.ClassA \n" +
        		"    name : String \n" +
        		"    age : Integer \n" +
        		"end \n";

        String str2 = "";
        str2 += "package org.drools.mvel.compiler \n" +
        		"declare org.drools.ClassA \n" +
        		"    name : String \n" +
        		"    lastName : String \n" +
        		"end \n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();

        kbuilder.add( ResourceFactory.newByteArrayResource( str1.getBytes() ),
                      ResourceType.DRL );

        kbuilder.add( ResourceFactory.newByteArrayResource( str2.getBytes() ),
                      ResourceType.DRL );

        if ( ! kbuilder.hasErrors() ) {
           fail( kbuilder.getErrors().toString() );
        }
    }

    /**
     * 2 resources (containing different declarations of the same type ) are added
     * to the kbuilder.
     * The expectation here is that the compilation fails because we are
     * trying to add an incompatible re-definition of the declared type:
     * it introduces a new field 'lastName'
     */
    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testTypeReDeclarationWithExtraField2(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // Note: Didn't applied new APIs because KieUtil.getKieBuilderFromResources() didn't reproduce the issue.
        // TODO: File a JIRA to revisit and write a valid test for new APIs and exec-model

        //same package, different resources
        String str1 = "";
        str1 += "package org.drools.mvel.compiler \n" +
        		"declare org.drools.ClassA \n" +
        		"    name : String \n" +
        		"    age : Integer \n" +
        		"end \n";

        String str2 = "";
        str2 += "package org.drools.mvel.compiler \n" +
        		"declare org.drools.ClassA \n" +
        		"    name : String \n" +
        		"    lastName : String \n" +
        		"end \n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();

        kbuilder.add( ResourceFactory.newByteArrayResource( str1.getBytes() ),
                      ResourceType.DRL );

        kbuilder.add( ResourceFactory.newByteArrayResource( str2.getBytes() ),
                      ResourceType.DRL );

        if (!kbuilder.hasErrors() ) {
           fail( kbuilder.getErrors().toString() );
        }
    }



    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testDuplicateDeclaration(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str = "";
        str += "package org.drools.mvel.compiler \n" +
                "declare Bean \n" +
                "    name : String \n" +
                "end \n" +
                "declare Bean \n" +
                "    age : int \n" +
                "end \n";

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromDrls(kieBaseTestConfiguration, false, str);
        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
        assertThat(errors.isEmpty()).as("Two definitions with the same name are not allowed, but it was not detected!").isFalse();
    }



    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.TYPE)
    public static @interface KlassAnnotation {
        String value();
    }

    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.FIELD)
    public static @interface FieldAnnotation {
        String prop();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testTypeDeclarationMetadata(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str = "";
        str += "package org.drools.mvel.compiler.test; \n" +
                "import org.drools.mvel.compiler.compiler.TypeDeclarationTest.KlassAnnotation; \n" +
                "import org.drools.mvel.compiler.compiler.TypeDeclarationTest.FieldAnnotation; \n" +
                "import org.drools.mvel.compiler.Person\n" +
                "\n" +
                "declare Bean \n" +
                "@role(event) \n" +
                "@expires( 1s ) \n" +
                "@KlassAnnotation( \"klass\" )" +
                "" +
                "    name : String\n" +
                "end \n" +
                "declare Person @role(event) end";

        KieBase kBase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);

        FactType bean = kBase.getFactType( "org.drools.mvel.compiler.test", "Bean" );
        FactType pers = kBase.getFactType( "org.drools", "Person" );
        assertThat(bean.getName()).isEqualTo("org.drools.mvel.compiler.test.Bean");
        assertThat(bean.getSimpleName()).isEqualTo("Bean");
        assertThat(bean.getPackageName()).isEqualTo("org.drools.mvel.compiler.test");

        assertThat(bean.getClassAnnotations().size()).isEqualTo(3);
        Annotation ann = bean.getClassAnnotations().get( 0 );
        if (!ann.getName().equals("org.drools.mvel.compiler.compiler.TypeDeclarationTest$KlassAnnotation")) {
            ann = bean.getClassAnnotations().get( 1 );
        }
        if (!ann.getName().equals("org.drools.mvel.compiler.compiler.TypeDeclarationTest$KlassAnnotation")) {
            ann = bean.getClassAnnotations().get( 2 );
        }
        assertThat(ann.getName()).isEqualTo("org.drools.mvel.compiler.compiler.TypeDeclarationTest$KlassAnnotation");
        assertThat(ann.getPropertyValue("value")).isEqualTo("klass");
        assertThat(ann.getPropertyType("value")).isEqualTo(String.class);

        assertThat(bean.getMetaData().size()).isEqualTo(2);
        assertThat(bean.getMetaData().get("role")).isEqualTo("event");

        FactField field = bean.getField( "name" );
        assertThat(field).isNotNull();
    }

    public static class EventBar {
        public static class Foo {

        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testTypeDeclarationWithInnerClasses(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-150
        String str = "";
        str += "package org.drools.mvel.compiler;\n" +
               "\n" +
               "import org.drools.mvel.compiler.compiler.TypeDeclarationTest.EventBar.*;\n" +
               "" +
               "declare Foo\n" +
               " @role( event )\n" +
               "end\n" +
               "" +
               "rule R when Foo() then end";

        KieBase kBase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession knowledgeSession = kBase.newKieSession();
        FactHandle handle = knowledgeSession.insert( new EventBar.Foo() );

        assertThat(handle instanceof DefaultEventHandle).isTrue();

    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testTypeDeclarationWithInnerClassesImport(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-150
        String str = "";
        str += "package org.drools.mvel.compiler;\n" +
               "\n" +
               "import org.drools.mvel.compiler.compiler.TypeDeclarationTest.EventBar.Foo;\n" +
               "" +
               "declare Foo\n" +
               " @role( event )\n" +
               "end\n" +
               "" +
               "rule R when Foo() then end";

        KieBase kBase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        KieSession knowledgeSession = kBase.newKieSession();
        FactHandle handle = knowledgeSession.insert( new EventBar.Foo() );

        assertThat(handle instanceof DefaultEventHandle).isTrue();

    }


    public static class ClassC {
        private String name;
        private Integer age;

        public String getName() {
            return name;
        }

        public void setName( String name ) {
            this.name = name;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge( Integer age ) {
            this.age = age;
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testMultipleTypeReDeclaration() {
        // Note: Didn't applied new APIs because KieUtil.getKieBuilderFromResources() didn't reproduce the issue.
        // TODO: File a JIRA to revisit and write a valid test for new APIs and exec-model

        //same package, different resources
        String str1 = "";
        str1 += "package org.drools \n" +
                "declare org.drools.ClassC \n" +
                "    name : String \n" +
                "    age : Integer \n" +
                "end \n";

        String str2 = "";
        str2 += "package org.drools \n" +
                "declare org.drools.ClassC \n" +
                "    name : String \n" +
                "    age : Integer \n" +
                "end \n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();

        kbuilder.add( ResourceFactory.newByteArrayResource( str1.getBytes() ),
                      ResourceType.DRL );

        kbuilder.add( ResourceFactory.newByteArrayResource( str2.getBytes() ),
                      ResourceType.DRL );

        if ( kbuilder.hasErrors() ) {
            fail( kbuilder.getErrors().toString() );
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testDeclareFieldArray(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str1 = "" +
                      "package org.drools " +

                      "declare Test end " +

                      "declare Pet " +
                      "    owners : Owner[] " +
                      "    twoDimArray : Foo[][] " +
                      "    friends : Pet[] " +
                      "    ages : int[] " +
                      "end " +

                      "declare Owner " +
                      "     name : String " +
                      "end " +

                      "declare Foo end " +

                      "";

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromDrls(kieBaseTestConfiguration, false, str1);
        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
        assertThat(errors.isEmpty()).as(errors.toString()).isTrue();

        InternalKieModule kieModule = (InternalKieModule)kieBuilder.getKieModule();
        KnowledgeBuilderImpl kbuilder = (KnowledgeBuilderImpl)kieModule.getKnowledgeBuilderForKieBase(KieBaseTestConfiguration.KIE_BASE_MODEL_NAME);

        for( KiePackage kp : kbuilder.getKnowledgePackages() ) {
            if ( kp.getName().equals( "org.drools" ) ) {
                Collection<FactType> types = kp.getFactTypes();
                for ( FactType type : types ) {
                    if ( "org.drools.Pet".equals( type.getName() ) ) {
                        assertThat(type.getFields().size()).isEqualTo(4);
                        FactField owners = type.getField( "owners" );
                        assertThat(owners != null && owners.getType().getSimpleName().equals("Owner[]") && owners.getType().isArray()).isTrue();
                        FactField twoDim = type.getField( "twoDimArray" );
                        assertThat(twoDim != null && twoDim.getType().getSimpleName().equals("Foo[][]") && twoDim.getType().isArray()).isTrue();
                        FactField friends = type.getField( "friends" );
                        assertThat(friends != null && friends.getType().getSimpleName().equals("Pet[]") && friends.getType().isArray()).isTrue();
                        FactField ages = type.getField( "ages" );
                        assertThat(ages != null && ages.getType().getSimpleName().equals("int[]") && ages.getType().isArray()).isTrue();
                    }
                }
            }
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testPreventReflectionAPIsOnJavaClasses(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String drl = "package org.test; " +

                     // existing java class
                     "declare org.drools.mvel.compiler.Person " +
                     "  @role(event) " +
                     "end \n" +

                     "";

        KieBuilder kieBuilder = build(kieBaseTestConfiguration, drl);

        assertThat(kieBuilder.getResults().hasMessages(Message.Level.ERROR)).isFalse();
        KieBase kieBase = KieServices.Factory.get().newKieContainer( kieBuilder.getKieModule().getReleaseId() ).getKieBase();

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> kieBase.getFactType( "org.drools.mvel.compiler", "Person" ));

    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testCrossPackageDeclares(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // TODO: enable exec-model

        String pkg1 =
                "package org.drools.mvel.compiler.test1; " +
                "import org.drools.mvel.compiler.test2.GrandChild; " +
                "import org.drools.mvel.compiler.test2.Child; " +
                "import org.drools.mvel.compiler.test2.BarFuu; " +

                "declare FuBaz foo : String end " +

                "declare Parent " +
                "   unknown : BarFuu " +
                "end " +

                "declare GreatChild extends GrandChild " +
                "   father : Child " +
                "end "
                ;

        String pkg2 =
                "package org.drools.mvel.compiler.test2; " +
                "import org.drools.mvel.compiler.test1.Parent; " +
                "import org.drools.mvel.compiler.test1.FuBaz; " +

                "declare BarFuu " +
                "   baz : FuBaz " +
                "end " +

                "declare Child extends Parent " +
                "end " +

                "declare GrandChild extends Child " +
                "   notknown : FuBaz " +
                "end "
                ;

        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();

        kfs.generateAndWritePomXML( ks.newReleaseId( "test", "foo", "1.0" ) );
        KieModuleModel km = ks.newKieModuleModel();
        km.newKieBaseModel( "rules" )
                .addPackage( "org.drools.mvel.compiler.test2" )
                .addPackage( "org.drools.mvel.compiler.test1" );
        kfs.writeKModuleXML( km.toXML() );

        KieResources kr = ks.getResources();
        Resource r1 = kr.newByteArrayResource( pkg1.getBytes() )
                .setResourceType( ResourceType.DRL )
                .setSourcePath( "org/drools/compiler/test1/p1.drl" );
        Resource r2 = kr.newByteArrayResource( pkg2.getBytes() )
                .setResourceType( ResourceType.DRL )
                .setSourcePath( "org/drools/compiler/test2/p2.drl" );

        kfs.write( r1 );
        kfs.write( r2 );

        KieBuilder builder = ks.newKieBuilder( kfs );
        builder.buildAll();

        assertThat(builder.getResults().getMessages(Message.Level.ERROR)).isEqualTo(Collections.emptyList());

        KieContainer kc = ks.newKieContainer(builder.getKieModule().getReleaseId());
        FactType ft = kc.getKieBase( "rules" ).getFactType( "org.drools.mvel.compiler.test2", "Child" );

        assertThat(ft).isNotNull();
        assertThat(ft.getFactClass()).isNotNull();
        assertThat(ft.getFactClass().getSuperclass().getName()).isEqualTo("org.drools.mvel.compiler.test1.Parent");
    }


    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testUnknownField(KieBaseTestConfiguration kieBaseTestConfiguration) throws InstantiationException, IllegalAccessException {
        // DROOLS-546
        String drl = "package org.test; " +
                     "declare Pet" +
                     " " +
                     "end \n";

        KieBuilder kieBuilder = build(kieBaseTestConfiguration, drl);

        assertThat(kieBuilder.getResults().hasMessages(Message.Level.ERROR)).isFalse();
        KieBase kieBase = KieServices.Factory.get().newKieContainer( kieBuilder.getKieModule().getReleaseId() ).getKieBase();

        FactType factType = kieBase.getFactType("org.test", "Pet");
        Object instance = factType.newInstance();
        factType.get(instance, "unknownField");
        factType.set(instance, "unknownField", "myValue");
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testPositionalArguments(KieBaseTestConfiguration kieBaseTestConfiguration) throws InstantiationException, IllegalAccessException {
        String drl = "package org.test;\n" +
                     "global java.util.List names;\n" +
                     "declare Person\n" +
                     "    name : String\n" +
                     "    age : int\n" +
                     "end\n" +
                     "rule R when \n" +
                     "    $p : Person( \"Mark\", 37; )\n" +
                     "then\n" +
                     "    names.add( $p.getName() );\n" +
                     "end\n";

        KieBuilder kieBuilder = build(kieBaseTestConfiguration, drl);

        assertThat(kieBuilder.getResults().hasMessages(Message.Level.ERROR)).isFalse();
        KieBase kieBase = KieServices.Factory.get().newKieContainer( kieBuilder.getKieModule().getReleaseId() ).getKieBase();

        FactType factType = kieBase.getFactType( "org.test", "Person" );
        Object instance = factType.newInstance();
        factType.set(instance, "name", "Mark");
        factType.set(instance, "age", 37);

        List<String> names = new ArrayList<String>();
        KieSession ksession = kieBase.newKieSession();
        ksession.setGlobal("names", names);

        ksession.insert(instance);
        ksession.fireAllRules();

        assertThat(names.size()).isEqualTo(1);
        assertThat(names.get(0)).isEqualTo("Mark");
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testExplictPositionalArguments(KieBaseTestConfiguration kieBaseTestConfiguration) throws InstantiationException, IllegalAccessException {
        String drl = "package org.test;\n" +
                     "global java.util.List names;\n" +
                     "declare Person\n" +
                     "    name : String @Position(1)\n" +
                     "    age : int @Position(0)\n" +
                     "end\n" +
                     "rule R when \n" +
                     "    $p : Person( 37, \"Mark\"; )\n" +
                     "then\n" +
                     "    names.add( $p.getName() );\n" +
                     "end\n";

        KieBuilder kieBuilder = build(kieBaseTestConfiguration, drl);

        assertThat(kieBuilder.getResults().hasMessages(Message.Level.ERROR)).isFalse();
        KieBase kieBase = KieServices.Factory.get().newKieContainer( kieBuilder.getKieModule().getReleaseId() ).getKieBase();

        FactType factType = kieBase.getFactType("org.test", "Person");
        Object instance = factType.newInstance();
        factType.set(instance, "name", "Mark");
        factType.set(instance, "age", 37);

        List<String> names = new ArrayList<String>();
        KieSession ksession = kieBase.newKieSession();
        ksession.setGlobal("names", names);

        ksession.insert(instance);
        ksession.fireAllRules();

        assertThat(names.size()).isEqualTo(1);
        assertThat(names.get(0)).isEqualTo("Mark");
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testTooManyPositionalArguments(KieBaseTestConfiguration kieBaseTestConfiguration) throws InstantiationException, IllegalAccessException {
        // DROOLS-559
        String drl = "package org.test;\n" +
                     "global java.util.List names;\n" +
                     "declare Person\n" +
                     "    name : String\n" +
                     "    age : int\n" +
                     "end\n" +
                     "rule R when \n" +
                     "    $p : Person( \"Mark\", 37, 42; )\n" +
                     "then\n" +
                     "    names.add( $p.getName() );\n" +
                     "end\n";

        KieBuilder kieBuilder = build(kieBaseTestConfiguration, drl);

        assertThat(kieBuilder.getResults().hasMessages(Message.Level.ERROR)).isTrue();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testOutOfRangePositions(KieBaseTestConfiguration kieBaseTestConfiguration) throws InstantiationException, IllegalAccessException {
        // DROOLS-559
        String drl = "package org.test;\n" +
                     "global java.util.List names;\n" +
                     "declare Person\n" +
                     "    name : String @Position(3)\n" +
                     "    age : int @Position(1)\n" +
                     "end\n" +
                     "rule R when \n" +
                     "    $p : Person( 37, \"Mark\"; )\n" +
                     "then\n" +
                     "    names.add( $p.getName() );\n" +
                     "end\n";

        KieBuilder kieBuilder = build(kieBaseTestConfiguration, drl);

        assertThat(kieBuilder.getResults().hasMessages(Message.Level.ERROR)).isTrue();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testDuplicatedPositions(KieBaseTestConfiguration kieBaseTestConfiguration) throws InstantiationException, IllegalAccessException {
        // DROOLS-559
        String drl = "package org.test;\n" +
                     "global java.util.List names;\n" +
                     "declare Person\n" +
                     "    name : String @Position(1)\n" +
                     "    age : int @Position(1)\n" +
                     "end\n" +
                     "rule R when \n" +
                     "    $p : Person( 37, \"Mark\"; )\n" +
                     "then\n" +
                     "    names.add( $p.getName() );\n" +
                     "end\n";

        KieBuilder kieBuilder = build(kieBaseTestConfiguration, drl);

        assertThat(kieBuilder.getResults().hasMessages(Message.Level.ERROR)).isTrue();
    }

    private KieBuilder build(KieBaseTestConfiguration kieBaseTestConfiguration, String drl) {
        return KieUtil.getKieBuilderFromDrls(kieBaseTestConfiguration, false, drl);
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testMultipleAnnotationDeclarations(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str1 = "";
        str1 += "package org.kie1 " +
                "" +
               "declare Foo \n" +
                "   @role(event) " +
               "    name : String " +
               "    age : int " +
               "end ";

        String str2 = "" +
               "package org.kie3 " +
               "" +
               "declare org.kie1.Foo " +
               "    @propertyReactive " +
               "end ";

        String str3 = "" +
                      "package org.kie4; " +
                      "import org.kie1.Foo; " +
                      "" +
                      "rule Check " +
                      "when " +
                      " $f : Foo( name == 'bar' ) " +
                      "then " +
                      " modify( $f ) { setAge( 99 ); } " +
                      "end ";

        KieBase kieBase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str1, str2, str3);

        FactType type = kieBase.getFactType( "org.kie1", "Foo" );
        assertThat(type.getFields().size()).isEqualTo(2);

        Object foo = null;
        try {
            foo = type.newInstance();
            type.set( foo, "name", "bar" );
            assertThat(type.get(foo, "name")).isEqualTo("bar");
        } catch ( InstantiationException e ) {
            fail( e.getMessage() );
        } catch ( IllegalAccessException e ) {
            fail( e.getMessage() );
        }

        KieSession session = kieBase.newKieSession();
        FactHandle handle = session.insert( foo );
        int n = session.fireAllRules( 5 );

        assertThat(handle instanceof DefaultEventHandle).isTrue();
        assertThat(n).isEqualTo(1);
        assertThat(type.get(foo, "age")).isEqualTo(99);
    }

    public static interface Base {
        public Object getFld();
        public void setFld( Object x );
    }

    public static interface Ext extends Base {
        public String getFld();
        public void setFld( String s );
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testDeclareWithExtensionAndOverride(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String s1 = "package test; " +
                          "global java.util.List list; " +

                          "declare Sub extends Sup " +
                          " fld : String " +
                          "end " +

                          "declare Sup " +
                          " fld : Object " +
                          "end " +

                          "rule Init when " +
                          "then insert( new Sub( 'aa' ) ); end " +

                          "rule CheckSup when " +
                          " $s : Sup( $f : fld == 'aa' ) " +
                          "then " +
                          "  list.add( \"Sup\" + $f );  " +
                          "end " +

                          "rule CheckSub when " +
                          " $s : Sub( $f : fld == 'aa' ) " +
                          "then " +
                          "  list.add( \"Sub\" + $f );  " +
                          "end ";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, s1);
        KieSession ks = kbase.newKieSession();

        List list = new ArrayList();
        ks.setGlobal( "list", list );
        ks.fireAllRules();

        assertThat(list.size()).isEqualTo(2);
        assertThat(list.containsAll(asList("Supaa", "Subaa"))).isTrue();

        FactType sup = ks.getKieBase().getFactType( "test", "Sup" );
        FactType sub = ks.getKieBase().getFactType( "test", "Sub" );

        try {
            Method m1 = sup.getFactClass().getMethod( "getFld" );
            assertThat(m1).isNotNull();
            assertThat(m1.getReturnType()).isEqualTo(Object.class);

            Method m2 = sub.getFactClass().getMethod( "getFld" );
            assertThat(m2).isNotNull();
            assertThat(m2.getReturnType()).isEqualTo(String.class);

            assertThat(sub.getFactClass().getFields().length).isEqualTo(0);
            assertThat(sub.getFactClass().getDeclaredFields().length).isEqualTo(0);
            assertThat(sup.getFactClass().getDeclaredFields().length).isEqualTo(1);
        } catch ( Exception e ) {
            e.printStackTrace();
            fail( e.getMessage() );
        }

    }

    public static class BeanishClass {
        private int foo;
        public int getFoo() { return foo; }
        public void setFoo( int x ) { foo = x; }
        public void setFooAsString( String x ) { foo = Integer.parseInt( x ); }
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testDeclarationOfClassWithNonStandardSetter(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String s1 = "package test; " +
                          "import " + BeanishClass.class.getCanonicalName() + "; " +

                          "declare " + BeanishClass.class.getSimpleName() + " @propertyReactive end " +

                          "rule Check when BeanishClass() @Watch( foo ) then end ";

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromDrls(kieBaseTestConfiguration, false, s1);
        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
        assertThat(errors.isEmpty()).as(errors.toString()).isTrue();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testDeclarationOfClassWithNonStandardSetterAndCanonicalName(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-815
        final String s1 = "package test; " +
                          "import " + BeanishClass.class.getCanonicalName() + "; " +

                          "declare " + BeanishClass.class.getCanonicalName() + " @propertyReactive end " +

                          "rule Check when BeanishClass() @Watch( foo ) then end ";

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromDrls(kieBaseTestConfiguration, false, s1);
        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
        assertThat(errors.isEmpty()).as(errors.toString()).isTrue();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testDeclarationOfClassWithNonStandardSetterAndFulllName(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String s1 = "package test; " +
                          "import " + BeanishClass.class.getCanonicalName() + "; " +

                          "declare " + BeanishClass.class.getName() + " @propertyReactive end " +

                          "rule Check when BeanishClass() @watch( foo ) then end ";

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromDrls(kieBaseTestConfiguration, false, s1);
        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
        assertThat(errors.isEmpty()).as(errors.toString()).isTrue();
    }

}
