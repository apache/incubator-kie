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
package org.kie.kogito.codegen.process.persistence.proto;

import java.io.IOException;
import java.util.*;

import org.drools.codegen.common.GeneratedFile;
import org.junit.jupiter.api.Test;
import org.kie.kogito.codegen.data.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractProtoGeneratorTest<T> {

    protected abstract ProtoGenerator.Builder<T, ? extends AbstractProtoGenerator<T>> protoGeneratorBuilder();

    protected abstract T convertToType(Class<?> clazz);

    @Test
    void testTravelsProtoFile() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .build(Collections.emptyList());

        Proto proto = generator.generate("@Indexed", ProtoGenerator.INDEX_COMMENT, "org.kie.kogito.test", convertToType(Travels.class));
        assertThat(proto).isNotNull();

        assertThat(proto.getPackageName()).isEqualTo("org.kie.kogito.test");
        assertThat(proto.getSyntax()).isEqualTo("proto2");
        assertThat(proto.getMessages()).hasSize(2);

        ProtoMessage person = proto.getMessages().get(0);
        asserPersonProto(person);

        ProtoMessage travel = proto.getMessages().get(1);
        assertThat(travel).isNotNull();
        assertThat(travel.getName()).isEqualTo("Travels");
        assertThat(travel.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(travel.getFields()).hasSize(2);

        ProtoField field = travel.getFields().get(0);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("id");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");
    }

    @Test
    void testJsonNode() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .build(Collections.singleton(convertToType(JacksonData.class)));
        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test");
        assertThat(proto).isNotNull();
        // there is no messages as there is not classes
        assertThat(proto.getMessages()).isEmpty();
    }

    @Test
    void testPersonProtoFile() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .build(Collections.emptyList());

        Proto proto = generator.generate("@Indexed", ProtoGenerator.INDEX_COMMENT, "org.kie.kogito.test", convertToType(Person.class));
        assertThat(proto).isNotNull();

        assertThat(proto.getPackageName()).isEqualTo("org.kie.kogito.test");
        assertThat(proto.getSyntax()).isEqualTo("proto2");
        assertThat(proto.getMessages()).hasSize(1);

        ProtoMessage person = proto.getMessages().get(0);
        asserPersonProto(person);
    }

    @Test
    void testPersonWithAddressProtoFile() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .withDataClasses(Collections.singleton(convertToType(PersonWithAddress.class)))
                .build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test");
        assertThat(proto).isNotNull();

        assertThat(proto.getPackageName()).isEqualTo("org.kie.kogito.test");
        assertThat(proto.getSyntax()).isEqualTo("proto2");
        assertThat(proto.getMessages()).hasSize(2);

        ProtoMessage address = proto.getMessages().get(0);
        assertThat(address).isNotNull();
        assertThat(address.getName()).isEqualTo("Address");
        assertThat(address.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(address.getFields()).hasSize(4);

        ProtoField field = address.getFields().get(0);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("city");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = address.getFields().get(1);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("country");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = address.getFields().get(2);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("street");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = address.getFields().get(3);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("zipCode");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");

        ProtoMessage person = proto.getMessages().get(1);
        assertThat(person).isNotNull();
        assertThat(person.getName()).isEqualTo("PersonWithAddress");
        assertThat(person.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(person.getFields()).hasSize(4);

        field = person.getFields().get(0);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("address");
        assertThat(field.getType()).isEqualTo("Address");
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = person.getFields().get(1);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("adult");
        assertThat(field.getType()).isEqualTo("bool");
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = person.getFields().get(2);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("age");
        assertThat(field.getType()).isEqualTo("int32");
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = person.getFields().get(3);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("name");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");
    }

    @Test
    void testPersonWithListProtoFile() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .withDataClasses(Collections.singleton(convertToType(PersonWithList.class)))
                .build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test");
        assertThat(proto).isNotNull();

        assertThat(proto.getPackageName()).isEqualTo("org.kie.kogito.test");
        assertThat(proto.getSyntax()).isEqualTo("proto2");
        assertThat(proto.getMessages()).hasSize(1);

        ProtoMessage address = proto.getMessages().get(0);
        assertThat(address).isNotNull();
        assertThat(address.getName()).isEqualTo("PersonWithList");
        assertThat(address.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(address.getFields()).hasSize(7);

        ProtoField field = address.getFields().get(0);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("adult");
        assertThat(field.getType()).isEqualTo("bool");
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = address.getFields().get(1);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("age");
        assertThat(field.getType()).isEqualTo("int32");
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = address.getFields().get(2);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("booleanList");
        assertThat(field.getType()).isEqualTo("bool");
        assertThat(field.getApplicability()).isEqualTo("repeated");

        field = address.getFields().get(3);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("integerList");
        assertThat(field.getType()).isEqualTo("int32");
        assertThat(field.getApplicability()).isEqualTo("repeated");

        field = address.getFields().get(4);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("longList");
        assertThat(field.getType()).isEqualTo("int64");
        assertThat(field.getApplicability()).isEqualTo("repeated");

        field = address.getFields().get(5);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("name");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = address.getFields().get(6);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("stringList");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("repeated");
    }

    @Test
    void testPersonWithAddressesProtoFile() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .withDataClasses(Collections.singleton(convertToType(PersonWithAddresses.class)))
                .build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test");
        assertThat(proto).isNotNull();

        assertThat(proto.getPackageName()).isEqualTo("org.kie.kogito.test");
        assertThat(proto.getSyntax()).isEqualTo("proto2");
        assertThat(proto.getMessages()).hasSize(2);

        ProtoMessage address = proto.getMessages().get(0);
        assertThat(address).isNotNull();
        assertThat(address.getName()).isEqualTo("Address");
        assertThat(address.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(address.getFields()).hasSize(4);

        ProtoField field = address.getFields().get(0);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("city");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = address.getFields().get(1);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("country");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = address.getFields().get(2);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("street");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = address.getFields().get(3);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("zipCode");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");

        ProtoMessage person = proto.getMessages().get(1);
        assertThat(person).isNotNull();
        assertThat(person.getName()).isEqualTo("PersonWithAddresses");
        assertThat(person.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(person.getFields()).hasSize(4);

        field = person.getFields().get(0);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("addresses");
        assertThat(field.getType()).isEqualTo("Address");
        assertThat(field.getApplicability()).isEqualTo("repeated");

        field = person.getFields().get(1);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("adult");
        assertThat(field.getType()).isEqualTo("bool");
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = person.getFields().get(2);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("age");
        assertThat(field.getType()).isEqualTo("int32");
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = person.getFields().get(3);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("name");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");
    }

    @Test
    void testPersonAsModelProtoFile() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .build(Collections.emptyList());

        Proto proto = generator.generate("@Indexed", ProtoGenerator.INDEX_COMMENT, "org.kie.kogito.test.persons", convertToType(Person.class));
        assertThat(proto).isNotNull();

        assertThat(proto.getPackageName()).isEqualTo("org.kie.kogito.test.persons");
        assertThat(proto.getSyntax()).isEqualTo("proto2");
        assertThat(proto.getMessages()).hasSize(1);

        ProtoMessage person = proto.getMessages().get(0);
        asserPersonProto(person);
    }

    private void asserPersonProto(ProtoMessage person) {
        assertThat(person).isNotNull();
        assertThat(person.getName()).isEqualTo("Person");
        assertThat(person.getComment()).isEqualTo("@Indexed");
        assertThat(person.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(person.getFields()).hasSize(18);

        int index = 0;

        ProtoField field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("adult");
        assertThat(field.getType()).isEqualTo("bool");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("age");
        assertThat(field.getType()).isEqualTo("int32");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("bigDecimal");
        assertThat(field.getType()).isEqualTo("kogito.Serializable");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");
        assertThat(field.getOption()).isEqualTo("[kogito_java_class = \"java.math.BigDecimal\"]");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("bytes");
        assertThat(field.getType()).isEqualTo("bytes");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("date");
        assertThat(field.getType()).isEqualTo("kogito.Date");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("duration");
        assertThat(field.getType()).isEqualTo("kogito.Serializable");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");
        assertThat(field.getOption()).isEqualTo("[kogito_java_class = \"java.time.Duration\"]");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("earnings");
        assertThat(field.getType()).isEqualTo("kogito.Serializable");
        assertThat(field.getApplicability()).isEqualTo("repeated");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");
        assertThat(field.getOption()).isEqualTo("[kogito_java_class = \"org.kie.kogito.codegen.data.Money[]\"]");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("expenses");
        assertThat(field.getType()).isEqualTo("kogito.Serializable");
        assertThat(field.getApplicability()).isEqualTo("repeated");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");
        assertThat(field.getOption()).isEqualTo("[kogito_java_class = \"java.util.List\"]");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("id");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("instant");
        assertThat(field.getType()).isEqualTo("kogito.Instant");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("localDate");
        assertThat(field.getType()).isEqualTo("kogito.Serializable");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");
        assertThat(field.getOption()).isEqualTo("[kogito_java_class = \"java.time.LocalDate\"]");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("localDateTime");
        assertThat(field.getType()).isEqualTo("kogito.Serializable");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");
        assertThat(field.getOption()).isEqualTo("[kogito_java_class = \"java.time.LocalDateTime\"]");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("name");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("offsetDateTime");
        assertThat(field.getType()).isEqualTo("kogito.Serializable");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");
        assertThat(field.getOption()).isEqualTo("[kogito_java_class = \"java.time.OffsetDateTime\"]");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("parent");
        assertThat(field.getType()).isEqualTo("Person");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("relatives");
        assertThat(field.getType()).isEqualTo("Person");
        assertThat(field.getApplicability()).isEqualTo("repeated");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("salary");
        assertThat(field.getType()).isEqualTo("kogito.Serializable");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");
        assertThat(field.getOption()).isEqualTo("[kogito_java_class = \"org.kie.kogito.codegen.data.Money\"]");

        field = person.getFields().get(index++);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("zonedDateTime");
        assertThat(field.getType()).isEqualTo("kogito.Serializable");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");
        assertThat(field.getOption()).isEqualTo("[kogito_java_class = \"java.time.ZonedDateTime\"]");
    }

    @Test
    void testPersonWithVariableInfoAsModelProtoFile() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .build(Collections.emptyList());

        Proto proto = generator.generate("@Indexed", ProtoGenerator.INDEX_COMMENT, "org.kie.kogito.test.persons", convertToType(PersonVarInfo.class));
        assertThat(proto).isNotNull();

        assertThat(proto.getPackageName()).isEqualTo("org.kie.kogito.test.persons");
        assertThat(proto.getSyntax()).isEqualTo("proto2");
        assertThat(proto.getMessages()).hasSize(1);

        ProtoMessage person = proto.getMessages().get(0);
        assertThat(person).isNotNull();
        assertThat(person.getName()).isEqualTo("PersonVarInfo");
        assertThat(person.getComment()).isEqualTo("@Indexed");
        assertThat(person.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(person.getFields()).hasSize(3);

        ProtoField field = person.getFields().get(0);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("adult");
        assertThat(field.getType()).isEqualTo("bool");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");

        field = person.getFields().get(1);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("age");
        assertThat(field.getType()).isEqualTo("int32");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField");

        field = person.getFields().get(2);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("name");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");
        assertThat(field.getComment()).isEqualTo("@Field(index = Index.YES, store = Store.YES) @SortableField\n @VariableInfo(tags=\"test\")");
    }

    @Test
    void testAnswerProtoFile() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .withDataClasses(Collections.singleton(convertToType(Answer.class)))
                .build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test.persons");
        assertThat(proto).isNotNull();

        assertThat(proto.getPackageName()).isEqualTo("org.kie.kogito.test.persons");
        assertThat(proto.getSyntax()).isEqualTo("proto2");
        assertThat(proto.getEnums()).hasSize(1);

        ProtoEnum answer = proto.getEnums().get(0);
        assertThat(answer).isNotNull();
        assertThat(answer.getName()).isEqualTo("Answer");
        assertThat(answer.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(answer.getFields()).hasSize(3);

        Map<String, Integer> fields = answer.getFields();
        assertThat(fields).isNotNull()
                .containsEntry("YES", 0)
                .containsEntry("MAYBE", 1)
                .containsEntry("NO", 2);
    }

    @Test
    void testWrongEnumStatus() {
        AbstractProtoGenerator<T> generatorBroken = protoGeneratorBuilder()
                .withDataClasses(Collections.singleton(convertToType(AnswerBroken.class)))
                .build(null);

        assertThatThrownBy(() -> generatorBroken.protoOfDataClasses("org.kie.kogito.test.persons"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot mix annotation");

        AbstractProtoGenerator<T> generatorBroken2 = protoGeneratorBuilder()
                .withDataClasses(Collections.singleton(convertToType(AnswerBrokenV2.class)))
                .build(null);

        assertThatThrownBy(() -> generatorBroken2.protoOfDataClasses("org.kie.kogito.test.persons"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot mix annotation");
    }

    @Test
    void testDataClassNameConflict() {
        AbstractProtoGenerator<T> generatorBroken = protoGeneratorBuilder()
                .withDataClasses(Collections.singleton(convertToType(Hello.class)))
                .build(Collections.singleton(convertToType(HelloModel.class)));

        assertThatThrownBy(() -> generatorBroken.generateProtoFiles())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(
                        "The data class 'org.kie.kogito.codegen.data.Hello' name, used as process variable, conflicts with the generated process model classes for Data Index protobuf. Please rename either the process 'Hello' or the Java class.");
    }

    @Test
    void testAnswerWithAnnotationsProtoFile() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .withDataClasses(Collections.singleton(convertToType(AnswerWithAnnotations.class)))
                .build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test.persons");
        assertThat(proto).isNotNull();

        assertThat(proto.getPackageName()).isEqualTo("org.kie.kogito.test.persons");
        assertThat(proto.getSyntax()).isEqualTo("proto2");
        assertThat(proto.getEnums()).hasSize(1);

        ProtoEnum answer = proto.getEnums().get(0);
        assertThat(answer).isNotNull();
        assertThat(answer.getName()).isEqualTo(AnswerWithAnnotations.class.getSimpleName());
        assertThat(answer.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(answer.getFields()).hasSize(3);

        Map<String, Integer> fields = answer.getFields();
        assertThat(fields).isNotNull()
                .containsEntry("YES", 1)
                .containsEntry("MAYBE", 2)
                .containsEntry("NO", 3);
    }

    @Test
    void testAnswerWithVariableInfoProtoFile() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .build(Collections.emptyList());

        Proto proto = generator.generate("@Indexed", ProtoGenerator.INDEX_COMMENT, "org.kie.kogito.test.persons", convertToType(Answer.class));
        assertThat(proto).isNotNull();

        assertThat(proto.getPackageName()).isEqualTo("org.kie.kogito.test.persons");
        assertThat(proto.getSyntax()).isEqualTo("proto2");
        assertThat(proto.getEnums()).hasSize(1);

        ProtoEnum answer = proto.getEnums().get(0);
        assertThat(answer).isNotNull();
        assertThat(answer.getName()).isEqualTo("Answer");
        assertThat(answer.getComment()).isBlank();
        assertThat(answer.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(answer.getFields()).hasSize(3);

        Map<String, Integer> fields = answer.getFields();
        assertThat(fields).isNotNull()
                .containsEntry("YES", 0)
                .containsEntry("MAYBE", 1)
                .containsEntry("NO", 2);
    }

    @Test
    void testQuestionWithEnumProtoFile() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .withDataClasses(Collections.singleton(convertToType(Question.class)))
                .build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test.persons");
        assertThat(proto).isNotNull();

        assertThat(proto.getPackageName()).isEqualTo("org.kie.kogito.test.persons");
        assertThat(proto.getSyntax()).isEqualTo("proto2");
        assertThat(proto.getMessages()).hasSize(1);

        ProtoMessage question = proto.getMessages().get(0);
        assertThat(question).isNotNull();
        assertThat(question.getName()).isEqualTo("Question");
        assertThat(question.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(question.getFields()).hasSize(2);

        ProtoField field = question.getFields().get(0);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("answer");
        assertThat(field.getType()).isEqualTo("Answer");
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = question.getFields().get(1);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("question");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");
    }

    @Test
    void testQuestionWithAnnotatedEnumProtoFile() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .withDataClasses(Collections.singleton(convertToType(QuestionWithAnnotatedEnum.class)))
                .build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test.persons");
        assertThat(proto).isNotNull();

        assertThat(proto.getPackageName()).isEqualTo("org.kie.kogito.test.persons");
        assertThat(proto.getSyntax()).isEqualTo("proto2");
        assertThat(proto.getMessages()).hasSize(1);

        ProtoMessage question = proto.getMessages().get(0);
        assertThat(question).isNotNull();
        assertThat(question.getName()).isEqualTo(QuestionWithAnnotatedEnum.class.getSimpleName());
        assertThat(question.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(question.getFields()).hasSize(2);

        ProtoField field = question.getFields().get(0);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("answer");
        assertThat(field.getType()).isEqualTo(AnswerWithAnnotations.class.getSimpleName());
        assertThat(field.getApplicability()).isEqualTo("optional");

        field = question.getFields().get(1);
        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("question");
        assertThat(field.getType()).isEqualTo("string");
        assertThat(field.getApplicability()).isEqualTo("optional");
    }

    @Test
    void checkGeneratedProtoBufAndListing() throws IOException {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .build(null);
        List<GeneratedFile> generatedFiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final Proto proto = new Proto("org.acme.test");
            generatedFiles.add(generator.generateProtoFiles("protofile." + i, proto));
        }
        generator.generateProtoListingFile(generatedFiles).ifPresent(generatedFiles::add);

        GeneratedFile listFile = generatedFiles.stream().filter(x -> x.relativePath().endsWith("list.json")).findFirst().get();
        byte[] list = listFile.contents();
        final ObjectMapper mapper = new ObjectMapper();
        List<String> files = mapper.readValue(list, List.class);
        assertThat(files).isNotEmpty().hasAtLeastOneElementOfType(String.class)
                .contains("protofile.0.proto")
                .hasSize(5);
    }

    @Test
    void builderTest() {
        T generatedPojo = convertToType(GeneratedPOJO.class);
        T address = convertToType(Address.class);
        T person = convertToType(Person.class);

        // empty
        AbstractProtoGenerator<T> emptyGenerator = protoGeneratorBuilder()
                .build(null);
        assertThat(emptyGenerator.getDataClasses()).isEmpty();
        assertThat(emptyGenerator.getModelClasses()).isEmpty();

        // persistence class
        AbstractProtoGenerator<T> persistenceClassGenerator = protoGeneratorBuilder()
                .build(null);
        assertThat(persistenceClassGenerator.getDataClasses()).isEmpty();
        assertThat(persistenceClassGenerator.getModelClasses()).isEmpty();

        // explicit data class
        AbstractProtoGenerator<T> dataClassGenerator = protoGeneratorBuilder()
                .withDataClasses(Collections.singleton(person))
                .build(null);
        assertThat(dataClassGenerator.getDataClasses()).hasSize(1);
        assertThat(dataClassGenerator.getModelClasses()).isEmpty();

        // retrieve data classes
        AbstractProtoGenerator<T> modelClassGenerator = protoGeneratorBuilder()
                .build(Collections.singleton(generatedPojo));
        assertThat(modelClassGenerator.getDataClasses()).hasSize(1);
        assertThat(modelClassGenerator.getModelClasses()).hasSize(1);

        // explicit data classes win
        AbstractProtoGenerator<T> dataClassAndModelClassGenerator = protoGeneratorBuilder()
                .withDataClasses(Arrays.asList(person, address))
                .build(Collections.singleton(generatedPojo));
        assertThat(dataClassAndModelClassGenerator.getDataClasses()).hasSize(2);
        assertThat(dataClassAndModelClassGenerator.getModelClasses()).hasSize(1);
    }

    @Test
    void testProtoOfDataClasses() {
        List<T> dataClasses = new ArrayList<>();

        dataClasses.add(convertToType(Answer.class));
        dataClasses.add(convertToType(Address.class));

        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .withDataClasses(dataClasses)
                .build(null);
        Proto proto = generator.protoOfDataClasses("com.acme");
        assertThat(proto.getEnums()).hasSize(1);
        assertThat(proto.getEnums().get(0).getName()).isEqualTo(Answer.class.getSimpleName());
        assertThat(proto.getMessages()).hasSize(1);
        assertThat(proto.getMessages().get(0).getName()).isEqualTo(Address.class.getSimpleName());
    }

    @Test
    void recursiveProto() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .withDataClasses(Collections.singleton(convertToType(Person.class)))
                .build(null);

        Proto proto = generator.protoOfDataClasses("defaultPkg");
        assertThat(proto).isNotNull();
    }

    @Test
    void collectionWithoutTypeProto() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .withDataClasses(Collections.singleton(convertToType(ListWithoutType.class)))
                .build(null);

        assertThatThrownBy(() -> generator.protoOfDataClasses("defaultPkg"))
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Error while generating proto for model class " + ListWithoutType.class.getName()
                                + " Field attribute of class " + ListWithoutType.class.getName()
                                + " uses collection without type information");
    }

    @Test
    void fieldFromClassHierarchy() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .withDataClasses(Arrays.asList(convertToType(Person.class), convertToType(PersonSubClass.class)))
                .build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test.persons");
        assertThat(proto).isNotNull();

        assertThat(proto.getPackageName()).isEqualTo("org.kie.kogito.test.persons");
        assertThat(proto.getSyntax()).isEqualTo("proto2");
        assertThat(proto.getMessages()).hasSize(2);

        ProtoMessage person = proto.getMessages().get(0);
        assertThat(person).isNotNull();
        assertThat(person.getName()).isEqualTo("Person");
        assertThat(person.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(person.getFields()).hasSize(18);

        ProtoMessage personSubClass = proto.getMessages().get(1);
        assertThat(personSubClass).isNotNull();
        assertThat(personSubClass.getName()).isEqualTo("PersonSubClass");
        assertThat(personSubClass.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(personSubClass.getFields()).hasSize(19);

        assertClassIsIncludedInSubclass(person, personSubClass);
    }

    @Test
    void pojoWithObjectField() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .withDataClasses(Collections.singleton(convertToType(ObjectHolder.class)))
                .build(null);

        Proto proto = generator.protoOfDataClasses("org.kie.kogito.test.persons");
        assertThat(proto).isNotNull();

        assertThat(proto.getPackageName()).isEqualTo("org.kie.kogito.test.persons");
        assertThat(proto.getSyntax()).isEqualTo("proto2");
        assertThat(proto.getMessages()).hasSize(1);

        ProtoMessage protoMessage = proto.getMessages().get(0);
        assertThat(protoMessage).isNotNull();
        assertThat(protoMessage.getName()).isEqualTo("ObjectHolder");
        assertThat(protoMessage.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");
        assertThat(protoMessage.getFields()).hasSize(1)
                .element(0)
                .hasFieldOrPropertyWithValue("name", "value")
                .hasFieldOrPropertyWithValue("type", "kogito.Serializable");
    }

    @Test
    void modelWithObjectField() {
        AbstractProtoGenerator<T> generator = protoGeneratorBuilder()
                .build(Collections.singleton(convertToType(ObjectProcessModel.class)));

        Proto proto = generator.generate("comment", ProtoGenerator.INDEX_COMMENT, "org.kie.kogito.test",
                convertToType(ObjectProcessModel.class));

        assertThat(proto.getPackageName()).isEqualTo("org.kie.kogito.test");
        assertThat(proto.getSyntax()).isEqualTo("proto2");
        assertThat(proto.getMessages()).hasSize(1);

        ProtoMessage message = proto.getMessages().get(0);
        assertThat(message.getName()).isEqualTo("ObjectProcess");
        assertThat(message.getJavaPackageOption()).isEqualTo("org.kie.kogito.codegen.data");

        assertThat(message.getFields()).hasSize(2);

        ProtoField valueField = message.getFields().stream()
                .filter(f -> f.getName().equals("value"))
                .findFirst()
                .orElseThrow();
        assertThat(valueField.getType()).isEqualTo(ProtoGenerator.KOGITO_SERIALIZABLE);
        assertThat(valueField.getOption()).isEqualTo("[kogito_java_class = \"java.lang.Object\"]");
    }

    private void assertClassIsIncludedInSubclass(ProtoMessage superClass, ProtoMessage subClass) {
        for (ProtoField field : superClass.getFields()) {
            assertThat(field).isNotNull();

            boolean found = false;
            for (ProtoField subClassField : subClass.getFields()) {
                assertThat(subClassField).isNotNull();
                if (field.getName().equals(subClassField.getName())) {
                    assertThat(field.getType()).isEqualTo(subClassField.getType());
                    found = true;
                    break;
                }
            }
            assertThat(found).withFailMessage("Impossible to find field " + field.getName() + " in subclass").isTrue();
        }
    }
}
