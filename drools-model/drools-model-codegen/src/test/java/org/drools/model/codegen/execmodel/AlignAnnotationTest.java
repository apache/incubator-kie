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

import org.drools.base.rule.TypeDeclaration;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.impl.InternalRuleBase;
import org.drools.model.codegen.execmodel.domain.AnnotatedEvent;
import org.drools.model.codegen.execmodel.domain.Person;
import org.drools.model.codegen.execmodel.domain.PropertyChangeSupportFact;
import org.drools.model.codegen.execmodel.domain.PropertyReactiveFact;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieServices;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.definition.type.Role;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.conf.PropertySpecificOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Reproduces the annotation-precedence discrepancy between classic and executable
 * models when an empty DRL {@code declare} block exists for a Java class.
 *
 * <p>{@code @TypeSafe} is not covered here: neither model reads it from Java annotations
 * (only from DRL {@code @typesafe}), so there is no inheritance discrepancy to test.
 *
 * @see <a href="https://github.com/apache/incubator-kie/issues/7185">incubator-kie#7185</a>
 */
public class AlignAnnotationTest extends BaseModelTest {

    private static final String ANNOTATED_EVENT_NO_DECLARE_DRL =
            "import " + AnnotatedEvent.class.getCanonicalName() + ";\n" +
            "rule R when AnnotatedEvent() then end\n";

    private static final String ANNOTATED_EVENT_EMPTY_DECLARE_DRL =
            "import " + AnnotatedEvent.class.getCanonicalName() + ";\n" +
            "declare AnnotatedEvent end\n" +
            "rule R when AnnotatedEvent() then end\n";

    private static final String ANNOTATED_EVENT_OVERRIDE_DRL =
            "import " + AnnotatedEvent.class.getCanonicalName() + ";\n" +
            "declare AnnotatedEvent\n" +
            "  @role(FACT)\n" +
            "  @expires(5s)\n" +
            "  @propertyReactive\n" +
            "end\n" +
            "rule R when AnnotatedEvent() then end\n";

    // #7252: capitalized (Java-name) annotations must override the Java class
    // annotations exactly like the lowercase forms do.
    private static final String ANNOTATED_EVENT_CAPITAL_OVERRIDE_DRL =
            "import " + AnnotatedEvent.class.getCanonicalName() + ";\n" +
            "declare AnnotatedEvent\n" +
            "  @Role(FACT)\n" +
            "  @Expires(5s)\n" +
            "end\n" +
            "rule R when AnnotatedEvent() then end\n";

    private TypeDeclaration getTypeDeclaration(RUN_TYPE runType, String drl, Class<?> clazz) {
        return getTypeDeclaration(runType, null, drl, clazz);
    }

    private TypeDeclaration getTypeDeclaration(RUN_TYPE runType, KieModuleModel model, String drl, Class<?> clazz) {
        KieSession ksession = getKieSession(runType, model, drl);
        return ((InternalRuleBase) ksession.getKieBase()).getTypeDeclaration(clazz);
    }

    // --- Baseline: no declare block — both models should honor Java annotations ---

    @ParameterizedTest
    @MethodSource("parameters")
    public void testRoleWithNoDeclare(RUN_TYPE runType) {
        TypeDeclaration typeDecl = getTypeDeclaration(runType, ANNOTATED_EVENT_NO_DECLARE_DRL, AnnotatedEvent.class);
        assertThat(typeDecl.getRole()).isEqualTo(Role.Type.EVENT);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testExpiresWithNoDeclare(RUN_TYPE runType) {
        TypeDeclaration typeDecl = getTypeDeclaration(runType, ANNOTATED_EVENT_NO_DECLARE_DRL, AnnotatedEvent.class);
        assertThat(typeDecl.getExpirationOffset()).isEqualTo(10_000L);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testTimestampWithNoDeclare(RUN_TYPE runType) {
        TypeDeclaration typeDecl = getTypeDeclaration(runType, ANNOTATED_EVENT_NO_DECLARE_DRL, AnnotatedEvent.class);
        assertThat(typeDecl.getTimestampAttribute()).isEqualTo("start");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testDurationWithNoDeclare(RUN_TYPE runType) {
        TypeDeclaration typeDecl = getTypeDeclaration(runType, ANNOTATED_EVENT_NO_DECLARE_DRL, AnnotatedEvent.class);
        assertThat(typeDecl.getDurationAttribute()).isEqualTo("length");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testClassReactiveWithNoDeclare(RUN_TYPE runType) {
        TypeDeclaration typeDecl = getTypeDeclaration(runType, ANNOTATED_EVENT_NO_DECLARE_DRL, AnnotatedEvent.class);
        assertThat(typeDecl.isPropertyReactive()).isFalse();
    }

    // --- Empty declare block — should preserve Java annotations ---

    @ParameterizedTest
    @MethodSource("parameters")
    public void testRoleWithEmptyDeclare(RUN_TYPE runType) {
        TypeDeclaration typeDecl = getTypeDeclaration(runType, ANNOTATED_EVENT_EMPTY_DECLARE_DRL, AnnotatedEvent.class);
        assertThat(typeDecl.getRole()).isEqualTo(Role.Type.EVENT);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testExpiresWithEmptyDeclare(RUN_TYPE runType) {
        TypeDeclaration typeDecl = getTypeDeclaration(runType, ANNOTATED_EVENT_EMPTY_DECLARE_DRL, AnnotatedEvent.class);
        assertThat(typeDecl.getExpirationOffset()).isEqualTo(10_000L);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testTimestampWithEmptyDeclare(RUN_TYPE runType) {
        TypeDeclaration typeDecl = getTypeDeclaration(runType, ANNOTATED_EVENT_EMPTY_DECLARE_DRL, AnnotatedEvent.class);
        assertThat(typeDecl.getTimestampAttribute()).isEqualTo("start");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testDurationWithEmptyDeclare(RUN_TYPE runType) {
        TypeDeclaration typeDecl = getTypeDeclaration(runType, ANNOTATED_EVENT_EMPTY_DECLARE_DRL, AnnotatedEvent.class);
        assertThat(typeDecl.getDurationAttribute()).isEqualTo("length");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testClassReactiveWithEmptyDeclare(RUN_TYPE runType) {
        TypeDeclaration typeDecl = getTypeDeclaration(runType, ANNOTATED_EVENT_EMPTY_DECLARE_DRL, AnnotatedEvent.class);
        // @ClassReactive with PropertySpecificOption.ALWAYS (default) means NOT property reactive
        assertThat(typeDecl.isPropertyReactive()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testPropertyReactiveWithEmptyDeclare(RUN_TYPE runType) {
        String drl =
                "import " + PropertyReactiveFact.class.getCanonicalName() + ";\n" +
                "declare PropertyReactiveFact end\n" +
                "rule R when PropertyReactiveFact() then end\n";

        KieModuleModel model = KieServices.get().newKieModuleModel();
        model.setConfigurationProperty(PropertySpecificOption.PROPERTY_NAME, PropertySpecificOption.ALLOWED.name());

        TypeDeclaration typeDecl = getTypeDeclaration(runType, model, drl, PropertyReactiveFact.class);
        assertThat(typeDecl.isPropertyReactive()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testPropertyChangeSupportWithEmptyDeclare(RUN_TYPE runType) {
        String drl =
                "import " + PropertyChangeSupportFact.class.getCanonicalName() + ";\n" +
                "declare PropertyChangeSupportFact end\n" +
                "rule R when PropertyChangeSupportFact() then end\n";

        TypeDeclaration typeDecl = getTypeDeclaration(runType, drl, PropertyChangeSupportFact.class);
        assertThat(typeDecl.isDynamic()).isTrue();
    }

    // --- Explicit DRL override — DRL annotation should take precedence over Java ---

    @ParameterizedTest
    @MethodSource("parameters")
    public void testRoleExplicitOverride(RUN_TYPE runType) {
        TypeDeclaration typeDecl = getTypeDeclaration(runType, ANNOTATED_EVENT_OVERRIDE_DRL, AnnotatedEvent.class);
        assertThat(typeDecl.getRole()).isEqualTo(Role.Type.FACT);
    }

    // --- DRL @role on a class without a Java @Role ---

    @ParameterizedTest
    @MethodSource("parameters")
    public void testRoleEventDeclaredInDrlOnPlainClass(RUN_TYPE runType) {
        for (String roleValue : new String[]{"event", "EVENT", "Role.Type.EVENT"}) {
            String drl =
                    "import " + Person.class.getCanonicalName() + ";\n" +
                    "declare Person\n" +
                    "  @role(" + roleValue + ")\n" +
                    "end\n" +
                    "rule R when Person() then end\n";

            KieSession ksession = getKieSession(runType, drl);
            try {
                TypeDeclaration typeDecl = ((InternalRuleBase) ksession.getKieBase()).getTypeDeclaration(Person.class);
                assertThat(typeDecl.getRole()).as("@role(%s)", roleValue).isEqualTo(Role.Type.EVENT);

                InternalFactHandle handle = (InternalFactHandle) ksession.insert(new Person("Mario", 40));
                assertThat(handle.isEvent()).as("@role(%s)", roleValue).isTrue();
            } finally {
                ksession.dispose();
            }
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testExpiresExplicitOverride(RUN_TYPE runType) {
        TypeDeclaration typeDecl = getTypeDeclaration(runType, ANNOTATED_EVENT_OVERRIDE_DRL, AnnotatedEvent.class);
        assertThat(typeDecl.getExpirationOffset()).isEqualTo(5_000L);
    }

    // --- #7252: capitalized DRL annotations must override Java class annotations ---

    @ParameterizedTest
    @MethodSource("parameters")
    public void testCapitalizedRoleExplicitOverride(RUN_TYPE runType) {
        TypeDeclaration typeDecl = getTypeDeclaration(runType, ANNOTATED_EVENT_CAPITAL_OVERRIDE_DRL, AnnotatedEvent.class);
        assertThat(typeDecl.getRole()).as("@Role(FACT)").isEqualTo(Role.Type.FACT);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testCapitalizedExpiresExplicitOverride(RUN_TYPE runType) {
        TypeDeclaration typeDecl = getTypeDeclaration(runType, ANNOTATED_EVENT_CAPITAL_OVERRIDE_DRL, AnnotatedEvent.class);
        assertThat(typeDecl.getExpirationOffset()).as("@Expires(5s)").isEqualTo(5_000L);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testPropertyReactiveExplicitOverride(RUN_TYPE runType) {
        TypeDeclaration typeDecl = getTypeDeclaration(runType, ANNOTATED_EVENT_OVERRIDE_DRL, AnnotatedEvent.class);
        // DRL @propertyReactive overrides Java @ClassReactive
        assertThat(typeDecl.isPropertyReactive()).isTrue();
    }

    // --- #7252: capitalized ClassReactive on a plain class means the built-in, not a custom annotation ---

    @ParameterizedTest
    @MethodSource("parameters")
    public void testCapitalizedClassReactiveOnPlainClass(RUN_TYPE runType) {
        String drl =
                "import " + Person.class.getCanonicalName() + ";\n" +
                "declare Person\n" +
                "  @ClassReactive\n" +
                "end\n" +
                "rule R when Person() then end\n";

        TypeDeclaration typeDecl = getTypeDeclaration(runType, drl, Person.class);
        assertThat(typeDecl.isPropertyReactive()).as("@ClassReactive").isFalse();
    }

    // --- #7270: an imported custom annotation whose simple name collides with a built-in stays custom ---

    @ParameterizedTest
    @MethodSource("parameters")
    public void testImportedAnnotationWithCollidingSimpleNameStaysCustom(RUN_TYPE runType) {
        String drl =
                "import " + org.drools.model.codegen.execmodel.domain.ClassReactive.class.getCanonicalName() + ";\n" +
                "import " + Person.class.getCanonicalName() + ";\n" +
                "declare Person\n" +
                "  @ClassReactive(\"custom-marker\")\n" +
                "end\n" +
                "rule R when Person() then end\n";

        TypeDeclaration typeDecl = getTypeDeclaration(runType, drl, Person.class);
        // The custom annotation must not act as the built-in @classReactive: property reactivity
        // keeps its default in both models...
        assertThat(typeDecl.isPropertyReactive()).as("custom @ClassReactive must not switch off property reactivity").isTrue();
        // ...and PATTERN_DSL keeps it as custom metadata under its own name. (STANDARD_FROM_DRL does
        // not populate a ClassDefinition metadata map for custom annotations on an existing class.)
        assumeTrue(runType == RUN_TYPE.PATTERN_DSL);
        assertThat(typeDecl.getTypeClassDef().getMetaData()).as("custom annotation is kept as custom metadata")
                .containsKey(org.drools.model.codegen.execmodel.domain.ClassReactive.class.getSimpleName());
    }
}
