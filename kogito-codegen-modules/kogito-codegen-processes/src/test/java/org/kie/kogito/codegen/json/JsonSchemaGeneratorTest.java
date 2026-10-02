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
package org.kie.kogito.codegen.json;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.drools.codegen.common.GeneratedFile;
import org.jbpm.util.JsonSchemaUtil;
import org.junit.jupiter.api.Test;
import org.kie.kogito.ProcessInput;
import org.kie.kogito.UserTask;
import org.kie.kogito.UserTaskParam;
import org.kie.kogito.codegen.VariableInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.victools.jsonschema.generator.SchemaVersion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.kie.kogito.codegen.json.JsonSchemaGenerator.DEFAULT_SCHEMA_VERSION;

public class JsonSchemaGeneratorTest {

    private static final String ALL_OF = "allOf";
    private static final String REF = "$ref";
    private static final String INPUT = "input";
    private static final String OUTPUT = "output";

    private enum Color {
        GREEN,
        WHITE
    }

    @UserTask(taskName = "test", processName = "org.jbpm.test")
    private static class PersonInputParams {

        @UserTaskParam(UserTaskParam.ParamType.INPUT)
        private String name;

        @UserTaskParam(UserTaskParam.ParamType.INPUT)
        private Address address;

        @UserTaskParam(UserTaskParam.ParamType.INPUT)
        private Color color;
    }

    @UserTask(taskName = "test", processName = "org.jbpm.test")
    private static class PersonOutputParams {

        @UserTaskParam(UserTaskParam.ParamType.OUTPUT)
        private int age;

        @UserTaskParam(UserTaskParam.ParamType.OUTPUT)
        private String name;

        @UserTaskParam(UserTaskParam.ParamType.OUTPUT)
        private Address address;

        @SuppressWarnings("unused")
        private String ignored;
    }

    @UserTask(taskName = "test", processName = "InputOutput")
    private static class PersonInputOutputParams {

        @UserTaskParam(UserTaskParam.ParamType.OUTPUT)
        private int age;

        @UserTaskParam(UserTaskParam.ParamType.INPUT)
        private String name;

        @UserTaskParam(UserTaskParam.ParamType.INPUT)
        private Address address;

        @UserTaskParam(UserTaskParam.ParamType.INPUT)
        private Color color;
    }

    @UserTask(taskName = "name with spaces", processName = "InputOutput")
    private static class WhitespacesTask {

        @UserTaskParam(UserTaskParam.ParamType.OUTPUT)
        private int age;

        @UserTaskParam(UserTaskParam.ParamType.INPUT)
        private String name;
    }

    @UserTask(taskName = "manager approval", processName = "com.example.process")
    private static class TaskWithDotsInProcessName {

        @UserTaskParam(UserTaskParam.ParamType.INPUT)
        private String requestId;

        @UserTaskParam(UserTaskParam.ParamType.OUTPUT)
        private boolean approved;
    }

    @ProcessInput(processName = "com.example.workflow")
    private static class ProcessWithDotsInName {

        @VariableInfo
        private String workflowId;

        @VariableInfo
        private int priority;
    }

    @ProcessInput(processName = "processName")
    private static class ProcessInputModel {

        @VariableInfo
        private Color color;

        @VariableInfo
        private Person person;
    }

    @ProcessInput(processName = "emptyProcessName")
    private static class EmptyProcessInputModel {

        private Color color;

        private Person person;
    }

    @ProcessInput(processName = "objectVarProcess")
    private static class ProcessInputModelWithObjectVar {

        @VariableInfo
        private String name;

        @VariableInfo
        private Object data;
    }

    @UserTask(taskName = "objectVarTask", processName = "org.jbpm.test")
    private static class UserTaskWithObjectVar {

        @UserTaskParam(UserTaskParam.ParamType.INPUT)
        private String name;

        @UserTaskParam(UserTaskParam.ParamType.INPUT)
        private Object payload;
    }

    private static class HolderWithObjectField {

        private String label;

        private Object nested;
    }

    @ProcessInput(processName = "nestedObjectVarProcess")
    private static class ProcessInputModelWithNestedObjectVar {

        @VariableInfo
        private String name;

        @VariableInfo
        private HolderWithObjectField holder;
    }

    private static class Person {

        @SuppressWarnings("unused")
        private String name;
        @SuppressWarnings("unused")
        private int age;
        @SuppressWarnings("unused")
        private Address address;
        @SuppressWarnings("unused")
        private Person parent;
    }

    private static class Address {

        @SuppressWarnings("unused")
        private String street;
        @SuppressWarnings("unused")
        private Date date;
    }

    private static class IgnoredClass {

        @UserTaskParam(UserTaskParam.ParamType.OUTPUT)
        private int age;
    }

    @Test
    public void testJsonSchemaGenerator() throws IOException {
        Collection<GeneratedFile> files =
                new JsonSchemaGenerator.ClassBuilder(
                        Stream.of(EmptyProcessInputModel.class, PersonInputParams.class, ProcessInputModel.class, PersonOutputParams.class, IgnoredClass.class))
                                .build().generate();
        assertThat(files).hasSize(3);
        Iterator<GeneratedFile> iterator = files.iterator();
        assertEmptyProcessSchema("emptyProcessName.json", iterator.next(), SchemaVersion.DRAFT_2019_09);
        assertProcessSchema("processName.json", iterator.next(), SchemaVersion.DRAFT_2019_09);
        assertTaskSchema("org#jbpm#test_test.json", iterator.next(), SchemaVersion.DRAFT_2019_09, Arrays.asList("name", "address", "color"), Arrays.asList("name", "address", "age"));
    }

    @Test
    public void testJsonSchemaGeneratorNonExistingDraft() throws IOException {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            JsonSchemaGenerator.ClassBuilder builder =
                    new JsonSchemaGenerator.ClassBuilder(
                            Stream.of(PersonInputParams.class, PersonOutputParams.class, IgnoredClass.class))
                                    .withSchemaNameFunction(c -> "pepe")
                                    .withSchemaVersion("NON_EXISTING_DRAFT");
            builder.build().generate();
        });
    }

    @Test
    public void testJsonSchemaGeneratorDraft7() throws IOException {
        Collection<GeneratedFile> files =
                new JsonSchemaGenerator.ClassBuilder(
                        Stream.of(EmptyProcessInputModel.class, PersonInputParams.class, ProcessInputModel.class, PersonOutputParams.class, IgnoredClass.class))
                                .withSchemaVersion("DRAFT_7").build().generate();
        assertThat(files).hasSize(3);
        Iterator<GeneratedFile> iterator = files.iterator();
        assertEmptyProcessSchema("emptyProcessName.json", iterator.next(), SchemaVersion.DRAFT_7);
        assertProcessSchema("processName.json", iterator.next(), SchemaVersion.DRAFT_7);
        assertTaskSchema("org#jbpm#test_test.json", iterator.next(), SchemaVersion.DRAFT_7, Arrays.asList("name", "address", "color"), Arrays.asList("name", "address", "age"));
    }

    @Test
    public void testJsonSchemaGeneratorInputOutput() throws IOException {
        Collection<GeneratedFile> files = new JsonSchemaGenerator.ClassBuilder(Stream.of(PersonInputOutputParams.class)).build().generate();
        assertThat(files).hasSize(1);
        GeneratedFile file = files.iterator().next();

        assertTaskSchema("InputOutput_test.json", file, SchemaVersion.DRAFT_2019_09, Arrays.asList("name", "address", "color"), List.of("age"));
    }

    @Test
    public void testJsonSchemaGeneratorWithSpace() throws IOException {
        Collection<GeneratedFile> files = new JsonSchemaGenerator.ClassBuilder(Stream.of(WhitespacesTask.class)).build().generate();
        assertThat(files).hasSize(1);
        GeneratedFile file = files.iterator().next();
        assertThat(Path.of(file.relativePath())).isEqualTo(JsonSchemaUtil.getJsonDir().resolve("InputOutput_name_with_spaces.json"));
    }

    @Test
    public void testNothingToDo() throws IOException {
        Collection<GeneratedFile> files = new JsonSchemaGenerator.ClassBuilder(Stream.of(IgnoredClass.class)).build().generate();
        assertThat(files).isEmpty();
    }

    @Test
    public void testJsonSchemaGenerationWithDotsInProcessName() throws IOException {
        Collection<GeneratedFile> files = new JsonSchemaGenerator.ClassBuilder(Stream.of(ProcessWithDotsInName.class)).build().generate();
        assertThat(files).hasSize(1);
        GeneratedFile file = files.iterator().next();
        assertThat(file.relativePath()).isEqualTo(JsonSchemaUtil.getJsonDir().resolve("com#example#workflow.json").toString());
    }

    @Test
    public void testJsonSchemaGenerationForTaskWithDotsInProcessName() throws IOException {
        Collection<GeneratedFile> files = new JsonSchemaGenerator.ClassBuilder(Stream.of(TaskWithDotsInProcessName.class)).build().generate();
        assertThat(files).hasSize(1);
        GeneratedFile file = files.iterator().next();
        assertThat(file.relativePath()).isEqualTo(JsonSchemaUtil.getJsonDir().resolve("com#example#process_manager_approval.json").toString());
    }

    @Test
    public void testJsonSchemaGenerationForProcess() throws IOException {
        Collection<GeneratedFile> files = new JsonSchemaGenerator.ClassBuilder(Stream.of(ProcessInputModel.class)).build().generate();
        assertThat(files).hasSize(1);
        assertProcessSchema("processName.json", files.iterator().next(), SchemaVersion.DRAFT_2019_09);
    }

    @Test
    public void testJsonSchemaGenerationForEmptyProcessModel() throws IOException {
        Collection<GeneratedFile> files = new JsonSchemaGenerator.ClassBuilder(Stream.of(EmptyProcessInputModel.class)).build().generate();
        assertThat(files).hasSize(1);

        assertEmptyProcessSchema("emptyProcessName.json", files.iterator().next(), SchemaVersion.DRAFT_2019_09);
    }

    private void assertEmptyProcessSchema(String fileName, GeneratedFile file, SchemaVersion schemaVersion) throws IOException {
        assertThat(Path.of(file.relativePath())).isEqualTo(JsonSchemaUtil.getJsonDir().resolve(fileName));

        ObjectReader reader = new ObjectMapper().reader();
        JsonNode node = reader.readTree(file.contents());

        assertThat(node.get("$schema").asText()).isEqualTo(schemaVersion.getIdentifier());
        assertThat(node.get("type").asText()).isEqualTo("object");
        assertThat(node.get("properties")).isNull();
    }

    private void assertProcessSchema(String fileName, GeneratedFile file, SchemaVersion schemaVersion) throws IOException {
        assertThat(Path.of(file.relativePath())).isEqualTo(JsonSchemaUtil.getJsonDir().resolve(fileName));
        ObjectReader reader = new ObjectMapper().reader();
        JsonNode node = reader.readTree(file.contents());
        assertThat(node.get("$schema").asText()).isEqualTo(schemaVersion.getIdentifier());

        // assert definitions
        String definitionsPath = resolveDefinitionsProperty(schemaVersion);
        assertThat(node.get(definitionsPath).size()).isEqualTo(3);
        assertPersonNode(node.get(definitionsPath).get("Person"), definitionsPath);
        assertAddressNode(node.get(definitionsPath).get("Address"));
        assertColorNode(node.get(definitionsPath).get("Color"));

        assertThat(node.get("type").asText()).isEqualTo("object");
        JsonNode properties = node.get("properties");
        assertThat(properties.size()).isEqualTo(2);
        JsonNode color = properties.get("color");
        assertThat(color.get("$ref").asText()).isEqualTo("#/" + definitionsPath + "/Color");
        JsonNode address = properties.get("person");
        assertThat(address.get("$ref").asText()).isEqualTo("#/" + definitionsPath + "/Person");
    }

    private void assertTaskSchema(String fileName, GeneratedFile file, SchemaVersion schemaVersion, List<String> inputs, List<String> outputs) throws IOException {
        assertThat(Path.of(file.relativePath())).isEqualTo(JsonSchemaUtil.getJsonDir().resolve(fileName));
        ObjectReader reader = new ObjectMapper().reader();
        JsonNode node = reader.readTree(file.contents());
        assertThat(node.get("$schema").asText()).isEqualTo(schemaVersion.getIdentifier());

        // assert definitions
        String definitionsPath = resolveDefinitionsProperty(schemaVersion);
        assertThat(node.get(definitionsPath).size()).isEqualTo(2);
        assertAddressNode(node.get(definitionsPath).get("Address"));
        assertColorNode(node.get(definitionsPath).get("Color"));

        assertThat(node.get("type").asText()).isEqualTo("object");
        JsonNode properties = node.get("properties");
        assertThat(properties.size()).isEqualTo(4);
        assertBasicTaskField(properties, "age", "integer", inputs, outputs);
        assertBasicTaskField(properties, "name", "string", inputs, outputs);
        assertTaskFieldWithRef(properties, "color", "#/" + definitionsPath + "/Color", inputs, outputs);
        assertTaskFieldWithRef(properties, "address", "#/" + definitionsPath + "/Address", inputs, outputs);
    }

    private void assertBasicTaskField(JsonNode properties, String name, String type, List<String> inputs, List<String> outputs) {
        JsonNode property = properties.get(name);
        assertThat(property.get("type").asText()).isEqualTo(type);
        if (inputs.contains(name)) {
            checkNodeHasInputField(property);
        }
        if (outputs.contains(name)) {
            checkNodeHasOutputField(property);
        }
    }

    private void checkNodeHasInputField(JsonNode node) {
        assertThat(node.has(INPUT)).isTrue();
        assertThat(node.get(INPUT).asBoolean()).isTrue();
    }

    private void checkNodeHasOutputField(JsonNode node) {
        assertThat(node.has(OUTPUT)).isTrue();
        assertThat(node.get(OUTPUT).asBoolean()).isTrue();
    }

    private void assertChildAssignmentNode(ArrayNode arrayNode, String assignment) {
        Iterable<JsonNode> iterable = () -> arrayNode.iterator();
        JsonNode childAssignment = StreamSupport.stream(iterable.spliterator(), false)
                .filter(node -> node.has(assignment))
                .findFirst()
                .orElse(null);

        assertThat(childAssignment).isNotNull();
        assertThat(childAssignment.get(assignment).asBoolean()).isTrue();
    }

    private void assertTaskFieldWithRef(JsonNode properties, String name, String refPath, List<String> inputs, List<String> outputs) {
        JsonNode property = properties.get(name);
        if (property.has(ALL_OF)) {
            ArrayNode allOf = (ArrayNode) property.get(ALL_OF);
            assertThat(allOf.get(0).get(REF).asText()).isEqualTo(refPath);
            if (inputs.contains(name)) {
                assertChildAssignmentNode(allOf, INPUT);
            }
            if (outputs.contains(name)) {
                assertChildAssignmentNode(allOf, OUTPUT);
            }
        } else {
            assertThat(property.get("$ref").asText()).isEqualTo(refPath);
            if (inputs.contains(name)) {
                checkNodeHasInputField(property);
            }
            if (outputs.contains(name)) {
                checkNodeHasOutputField(property);
            }
        }

    }

    private void assertPersonNode(JsonNode personNode, String definitionsPath) {
        assertThat(personNode).isNotNull();

        assertThat(personNode.get("type").asText()).isEqualTo("object");
        JsonNode personProperties = personNode.get("properties");
        assertThat(personProperties.get("name").get("type").asText()).isEqualTo("string");
        assertThat(personProperties.get("age").get("type").asText()).isEqualTo("integer");
        assertThat(personProperties.get("address").get(REF).asText()).isEqualTo("#/" + definitionsPath + "/Address");
        assertThat(personProperties.get("parent").get(REF).asText()).isEqualTo("#/" + definitionsPath + "/Person");
    }

    private void assertAddressNode(JsonNode addressNode) {
        assertThat(addressNode).isNotNull();

        assertThat(addressNode.get("type").asText()).isEqualTo("object");
        JsonNode addressProperties = addressNode.get("properties");
        assertThat(addressProperties.get("street").get("type").asText()).isEqualTo("string");
        JsonNode dateNode = addressProperties.get("date");
        assertThat(dateNode.get("type").asText()).isEqualTo("string");
        assertThat(dateNode.get("format").asText()).isEqualTo("date-time");
    }

    private void assertColorNode(JsonNode colorNode) {
        assertThat(colorNode).isNotNull();

        assertThat(colorNode.get("type").asText()).isEqualTo("string");
        assertThat(colorNode.get("enum")).isInstanceOf(ArrayNode.class);
        ArrayNode colors = (ArrayNode) colorNode.get("enum");
        Set<Color> colorValues = EnumSet.noneOf(Color.class);
        colors.forEach(x -> colorValues.add(Color.valueOf(x.asText())));
        assertThat(colorValues.toArray()).containsExactly(Color.values());
    }

    private String resolveDefinitionsProperty(SchemaVersion schemaVersion) {
        return SchemaVersion.DRAFT_2019_09.equals(schemaVersion) ? "$defs" : "definitions";
    }

    @Test
    public void testObjectTypedFieldIsExcludedFromSchema() throws IOException {
        Collection<GeneratedFile> files = new JsonSchemaGenerator.ClassBuilder(
                Stream.of(ProcessInputModelWithObjectVar.class)).build().generate();
        assertThat(files).hasSize(1);

        ObjectReader reader = new ObjectMapper().reader();
        JsonNode node = reader.readTree(files.iterator().next().contents());

        JsonNode properties = node.get("properties");
        assertThat(properties).isNotNull();
        assertThat(properties.has("name")).isTrue();
        assertThat(properties.has("data")).isFalse();
    }

    @Test
    public void testObjectTypedUserTaskParamIsExcludedFromSchema() throws IOException {
        Collection<GeneratedFile> files = new JsonSchemaGenerator.ClassBuilder(
                Stream.of(UserTaskWithObjectVar.class)).build().generate();
        assertThat(files).hasSize(1);

        ObjectReader reader = new ObjectMapper().reader();
        JsonNode properties = reader.readTree(files.iterator().next().contents()).get("properties");
        assertThat(properties).isNotNull();
        assertThat(properties.has("name")).isTrue();
        assertThat(properties.has("payload")).isFalse();
    }

    @Test
    public void testNestedObjectTypedFieldIsExcludedFromSchema() throws IOException {
        Collection<GeneratedFile> files = new JsonSchemaGenerator.ClassBuilder(
                Stream.of(ProcessInputModelWithNestedObjectVar.class)).build().generate();
        assertThat(files).hasSize(1);

        ObjectReader reader = new ObjectMapper().reader();
        JsonNode node = reader.readTree(files.iterator().next().contents());

        // top-level: holder present, name present
        JsonNode properties = node.get("properties");
        assertThat(properties).isNotNull();
        assertThat(properties.has("name")).isTrue();
        assertThat(properties.has("holder")).isTrue();

        // nested: label present, but nested Object field excluded
        String definitionsKey = resolveDefinitionsProperty(DEFAULT_SCHEMA_VERSION);
        JsonNode holderDef = node.get(definitionsKey).get("HolderWithObjectField");
        assertThat(holderDef).isNotNull();
        assertThat(holderDef.get("properties").has("label")).isTrue();
        assertThat(holderDef.get("properties").has("nested")).isFalse();
    }
}
