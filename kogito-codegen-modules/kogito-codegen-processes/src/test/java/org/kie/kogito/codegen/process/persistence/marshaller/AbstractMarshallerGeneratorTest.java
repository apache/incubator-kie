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
package org.kie.kogito.codegen.process.persistence.marshaller;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.drools.compiler.compiler.io.memory.MemoryFileSystem;
import org.junit.jupiter.api.Test;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.JavaKogitoBuildContext;
import org.kie.kogito.codegen.data.Answer;
import org.kie.kogito.codegen.data.AnswerWithAnnotations;
import org.kie.kogito.codegen.data.Person;
import org.kie.kogito.codegen.data.PersonWithAddress;
import org.kie.kogito.codegen.data.PersonWithAddresses;
import org.kie.kogito.codegen.data.PersonWithBooleanGetAccessor;
import org.kie.kogito.codegen.data.PersonWithList;
import org.kie.kogito.codegen.data.Question;
import org.kie.kogito.codegen.data.QuestionWithAnnotatedEnum;
import org.kie.kogito.codegen.process.persistence.proto.AbstractProtoGenerator;
import org.kie.kogito.codegen.process.persistence.proto.Proto;
import org.kie.kogito.codegen.process.persistence.proto.ProtoGenerator;
import org.kie.memorycompiler.CompilationResult;
import org.kie.memorycompiler.JavaCompiler;
import org.kie.memorycompiler.JavaCompilerFactory;
import org.kie.memorycompiler.JavaConfiguration;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.kie.kogito.codegen.api.context.ContextAttributesConstants.KOGITO_CODEGEN_BOOLEAN_OBJECT_ACCESSOR_BEHAVIOUR;

public abstract class AbstractMarshallerGeneratorTest<T> {

    KogitoBuildContext context = JavaKogitoBuildContext.builder().build();

    private static final JavaCompiler JAVA_COMPILER = JavaCompilerFactory.loadCompiler(JavaConfiguration.CompilerType.NATIVE, "1.8");

    protected abstract MarshallerGenerator generator(KogitoBuildContext context, Collection<T> rawDataClasses);

    protected abstract ProtoGenerator.Builder<T, ? extends AbstractProtoGenerator<T>> protoGeneratorBuilder();

    protected abstract T convertToType(Class<?> clazz);

    protected Collection<T> convertTypes(Class<?>... classes) {
        return Arrays.stream(classes).map(c -> convertToType(c)).collect(toList());
    }

    protected MarshallerGenerator withGenerator(Class<?>... classes) {
        return generator(context, convertTypes(classes));
    }

    @Test
    void testPersonMarshallers() throws Exception {
        ProtoGenerator generator = protoGeneratorBuilder().withDataClasses(convertTypes(Person.class)).build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test", "import \"kogito-types.proto\";");
        assertThat(proto).isNotNull();
        assertThat(proto.getMessages()).hasSize(1);

        MarshallerGenerator marshallerGenerator = withGenerator(Person.class);
        List<CompilationUnit> classes = marshallerGenerator.generate(proto.serialize());
        assertThat(classes).isNotNull().hasSize(1);

        Optional<ClassOrInterfaceDeclaration> marshallerClass = classes.get(0).getClassByName("PersonMessageMarshaller");
        assertThat(marshallerClass).isPresent();

        assertThat(compile(classes).getErrors()).isEmpty();
    }

    @Test
    void testPersonWithBooleanIsAccessorMarshallers() throws Exception {
        context.setApplicationProperty(KOGITO_CODEGEN_BOOLEAN_OBJECT_ACCESSOR_BEHAVIOUR, "isPrefix");
        ProtoGenerator generator = protoGeneratorBuilder().withDataClasses(convertTypes(Person.class)).build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test", "import \"kogito-types.proto\";");
        assertThat(proto).isNotNull();
        assertThat(proto.getMessages()).hasSize(1);

        MarshallerGenerator marshallerGenerator = withGenerator(Person.class);
        List<CompilationUnit> classes = marshallerGenerator.generate(proto.serialize());
        assertThat(classes).isNotNull().hasSize(1);

        Optional<ClassOrInterfaceDeclaration> marshallerClass = classes.get(0).getClassByName("PersonMessageMarshaller");
        assertThat(marshallerClass).isPresent();

        assertThat(compile(classes).getErrors()).isEmpty();
    }

    @Test
    void testPersonWithBooleanGetAccessorMarshallers() throws Exception {
        context.setApplicationProperty(KOGITO_CODEGEN_BOOLEAN_OBJECT_ACCESSOR_BEHAVIOUR, "javaBeans");
        ProtoGenerator generator = protoGeneratorBuilder().withDataClasses(convertTypes(PersonWithBooleanGetAccessor.class)).build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test", "import \"kogito-types.proto\";");
        assertThat(proto).isNotNull();
        assertThat(proto.getMessages()).hasSize(1);

        MarshallerGenerator marshallerGenerator = withGenerator(PersonWithBooleanGetAccessor.class);
        List<CompilationUnit> classes = marshallerGenerator.generate(proto.serialize());
        assertThat(classes).isNotNull().hasSize(1);

        Optional<ClassOrInterfaceDeclaration> marshallerClass = classes.get(0).getClassByName("PersonWithBooleanGetAccessorMessageMarshaller");
        assertThat(marshallerClass).isPresent();

        assertThat(compile(classes).getErrors()).isEmpty();
    }

    @Test
    void testInvalidBooleanAccessorThrowsMarshaller() throws Exception {
        context.setApplicationProperty(KOGITO_CODEGEN_BOOLEAN_OBJECT_ACCESSOR_BEHAVIOUR, "get");
        ProtoGenerator generator = protoGeneratorBuilder().withDataClasses(convertTypes(Person.class)).build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test", "import \"kogito-types.proto\";");
        assertThat(proto).isNotNull();
        assertThat(proto.getMessages()).hasSize(1);

        MarshallerGenerator marshallerGenerator = withGenerator(PersonWithBooleanGetAccessor.class);
        assertThatThrownBy(() -> marshallerGenerator.generate(proto.serialize()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Property " + KOGITO_CODEGEN_BOOLEAN_OBJECT_ACCESSOR_BEHAVIOUR + " defined but does not contain proper value: expected 'isPrefix' or 'javaBeans'");

    }

    @Test
    void testPersonWithListMarshallers() throws Exception {
        ProtoGenerator generator = protoGeneratorBuilder().withDataClasses(convertTypes(PersonWithList.class)).build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test");
        assertThat(proto).isNotNull();
        assertThat(proto.getMessages()).hasSize(1);

        MarshallerGenerator marshallerGenerator = withGenerator(PersonWithList.class);

        List<CompilationUnit> classes = marshallerGenerator.generate(proto.serialize());
        assertThat(classes).isNotNull().hasSize(1);

        Optional<ClassOrInterfaceDeclaration> marshallerClass = classes.get(0).getClassByName("PersonWithListMessageMarshaller");
        assertThat(marshallerClass).isPresent();

        assertThat(compile(classes).getErrors()).isEmpty();
    }

    @Test
    void testPersonWithAddressMarshallers() throws Exception {
        ProtoGenerator generator = protoGeneratorBuilder().withDataClasses(convertTypes(PersonWithAddress.class)).build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test");
        assertThat(proto).isNotNull();
        assertThat(proto.getMessages()).hasSize(2);

        MarshallerGenerator marshallerGenerator = withGenerator(PersonWithAddresses.class);

        List<CompilationUnit> classes = marshallerGenerator.generate(proto.serialize());
        assertThat(classes).isNotNull().hasSize(2);

        Optional<ClassOrInterfaceDeclaration> marshallerClass = classes.get(0).getClassByName("AddressMessageMarshaller");
        assertThat(marshallerClass).isPresent();
        marshallerClass = classes.get(1).getClassByName("PersonWithAddressMessageMarshaller");
        assertThat(marshallerClass).isPresent();

        assertThat(compile(classes).getErrors()).isEmpty();
    }

    @Test
    void testPersonWithAddressesMarshallers() throws Exception {
        ProtoGenerator generator = protoGeneratorBuilder().withDataClasses(convertTypes(PersonWithAddresses.class)).build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test");
        assertThat(proto).isNotNull();
        assertThat(proto.getMessages()).hasSize(2);

        MarshallerGenerator marshallerGenerator = withGenerator(PersonWithAddresses.class);

        List<CompilationUnit> classes = marshallerGenerator.generate(proto.serialize());
        assertThat(classes).isNotNull().hasSize(2);

        Optional<ClassOrInterfaceDeclaration> marshallerClass = classes.get(0).getClassByName("AddressMessageMarshaller");
        assertThat(marshallerClass).isPresent();
        marshallerClass = classes.get(1).getClassByName("PersonWithAddressesMessageMarshaller");
        assertThat(marshallerClass).isPresent();

        assertThat(compile(classes).getErrors()).isEmpty();
    }

    @Test
    void testEnumInPojosMarshallers() {
        Stream.of(Question.class, QuestionWithAnnotatedEnum.class).forEach(c -> {
            ProtoGenerator generator = protoGeneratorBuilder().withDataClasses(convertTypes(c)).build(null);

            Proto proto = generator.protoOfDataClasses("org.kie.kogito.test");
            assertThat(proto).isNotNull();
            assertThat(proto.getMessages()).hasSize(1);

            MarshallerGenerator marshallerGenerator = withGenerator(c);

            List<CompilationUnit> classes = null;
            try {
                classes = marshallerGenerator.generate(proto.serialize());
            } catch (IOException e) {
                fail("Error generating marshaller for " + c.getName(), e);
            }
            assertThat(classes).isNotNull();
            assertThat(classes).hasSize(2);

            Optional<ClassOrInterfaceDeclaration> marshallerClass = classes.get(0).getClassByName(c.getSimpleName() + "MessageMarshaller");
            assertThat(marshallerClass).isPresent();
            String answerType = null;
            try {
                answerType = c.getDeclaredField("answer").getType().getSimpleName();
            } catch (NoSuchFieldException e) {
                fail("Unable to get answer field type for " + c.getName(), e);
            }
            marshallerClass = classes.get(1).getClassByName(answerType + "EnumMarshaller");
            assertThat(marshallerClass).isPresent();

            assertThat(compile(classes).getErrors()).isEmpty();
        });
    }

    @Test
    void testEnumMarshallers() {
        Stream.of(Answer.class, AnswerWithAnnotations.class).forEach(e -> {
            ProtoGenerator generator = protoGeneratorBuilder().withDataClasses(convertTypes(e)).build(null);

            Proto proto = generator.protoOfDataClasses("org.kie.kogito.test");
            assertThat(proto).isNotNull();
            assertThat(proto.getEnums()).hasSize(1);

            MarshallerGenerator marshallerGenerator = withGenerator(e);

            List<CompilationUnit> classes = null;
            try {
                classes = marshallerGenerator.generate(proto.serialize());
            } catch (IOException ex) {
                fail("Error generating marshaller for " + e.getName(), e);
            }
            assertThat(classes).isNotNull();
            assertThat(classes).hasSize(1);

            Optional<ClassOrInterfaceDeclaration> marshallerClass = classes.get(0).getClassByName(e.getSimpleName() + "EnumMarshaller");
            assertThat(marshallerClass).isPresent();

            assertThat(compile(classes).getErrors()).isEmpty();
        });
    }

    private CompilationResult compile(List<CompilationUnit> classes) {
        MemoryFileSystem srcMfs = new MemoryFileSystem();
        MemoryFileSystem trgMfs = new MemoryFileSystem();

        String[] sources = new String[classes.size()];
        int index = 0;
        for (CompilationUnit clazz : classes) {
            String fileName = className(clazz).replaceAll("\\.", "/") + ".java";
            sources[index++] = fileName;

            srcMfs.write(fileName, clazz.toString().getBytes());
        }

        return JAVA_COMPILER.compile(sources, srcMfs, trgMfs, this.getClass().getClassLoader());
    }

    private String className(CompilationUnit clazz) {
        return clazz.getType(0).getFullyQualifiedName().get();
    }
}
