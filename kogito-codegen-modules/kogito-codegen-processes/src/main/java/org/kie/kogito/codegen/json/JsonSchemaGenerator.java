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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.drools.codegen.common.GeneratedFile;
import org.drools.codegen.common.GeneratedFileType;
import org.jbpm.util.JsonSchemaUtil;
import org.kie.kogito.ProcessInput;
import org.kie.kogito.UserTask;
import org.kie.kogito.UserTaskParam;
import org.kie.kogito.codegen.VariableInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.CustomDefinition.AttributeInclusion;
import com.github.victools.jsonschema.generator.CustomPropertyDefinition;
import com.github.victools.jsonschema.generator.FieldScope;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerationContext;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaKeyword;
import com.github.victools.jsonschema.generator.SchemaVersion;

public class JsonSchemaGenerator {

    public static final Logger logger = LoggerFactory.getLogger(JsonSchemaGenerator.class);
    public static final SchemaVersion DEFAULT_SCHEMA_VERSION = SchemaVersion.DRAFT_2019_09;
    private static final GeneratedFileType JSON_SCHEMA_TYPE = GeneratedFileType.of("JSON_SCHEMA", GeneratedFileType.Category.INTERNAL_RESOURCE, true, true);

    private final Map<String, List<Class<?>>> map;
    private final SchemaVersion schemaVersion;

    public static class ClassBuilder {

        private Stream<Class<?>> stream;
        private Function<? super Class<?>, String> getSchemaName = JsonSchemaGenerator::getSchemaName;
        private SchemaVersion schemaVersion = DEFAULT_SCHEMA_VERSION;

        public ClassBuilder(Stream<Class<?>> stream) {
            this.stream = stream;
        }

        public ClassBuilder withSchemaNameFunction(Function<? super Class<?>, String> getSchemaName) {
            this.getSchemaName = getSchemaName;
            return this;
        }

        public ClassBuilder withSchemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion == null ? DEFAULT_SCHEMA_VERSION : SchemaVersion.valueOf(schemaVersion.trim().toUpperCase());
            return this;
        }

        public JsonSchemaGenerator build() {
            Map<String, List<Class<?>>> map = stream
                    .filter(this::ensureHasAnnotations)
                    .collect(Collectors.groupingBy(getSchemaName));

            return new JsonSchemaGenerator(map, schemaVersion);
        }

        private boolean ensureHasAnnotations(Class<?> c) {
            boolean needsJsonSchema = c.isAnnotationPresent(UserTask.class) || c.isAnnotationPresent(ProcessInput.class);
            if (!needsJsonSchema) {
                logger.warn("Could not retrieve neither UserTask nor Process annotation from class {} but was expected. " +
                        "This may be a class loader bug. If JsonSchemas have been generated you may ignore this message.", c);
            }
            return needsJsonSchema;
        }
    }

    private JsonSchemaGenerator(Map<String, List<Class<?>>> map, SchemaVersion schemaVersion) {
        this.map = map;
        this.schemaVersion = schemaVersion;
    }

    public Collection<GeneratedFile> generate() throws IOException {

        SchemaGeneratorConfigBuilder builder = new SchemaGeneratorConfigBuilder(schemaVersion, OptionPreset.PLAIN_JSON);
        builder.with(Option.DEFINITIONS_FOR_ALL_OBJECTS);
        builder.forTypesInGeneral()
                .withStringFormatResolver(target -> target.getSimpleTypeDescription().equals("Date") ? "date-time" : null);
        builder.forFields()
                .withIgnoreCheck(JsonSchemaGenerator::checkFields)
                .withCustomDefinitionProvider(this::getInputOutput);
        SchemaGenerator generator = new SchemaGenerator(builder.build());
        ObjectWriter writer = new ObjectMapper().writer();

        Collection<GeneratedFile> files = new ArrayList<>();
        for (Map.Entry<String, List<Class<?>>> entry : map.entrySet()) {
            ObjectNode merged = null;
            for (Class<?> c : entry.getValue()) {
                ObjectNode read = generator.generateSchema(c);
                if (merged == null) {
                    merged = read;
                } else {
                    merged = (ObjectNode) JsonUtils.merge(read, merged);
                }
            }
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                writer.writeValue(outputStream, merged);
                files.add(new GeneratedFile(JSON_SCHEMA_TYPE, JsonSchemaUtil.pathFor(entry.getKey()), outputStream.toByteArray()));
            }
        }
        return files;
    }

    private CustomPropertyDefinition getInputOutput(FieldScope scope, SchemaGenerationContext context) {
        UserTaskParam param = scope.getAnnotation(UserTaskParam.class);

        if (param != null) {
            final ObjectNode refNode = context.createStandardDefinitionReference(scope.getDeclaredType(), null);

            ObjectNode rootNode = context.getGeneratorConfig().createObjectNode();
            ArrayNode allOfNode = rootNode.withArray(context.getKeyword(SchemaKeyword.TAG_ALLOF));
            allOfNode.add(refNode);
            allOfNode.addObject().put(param.value().toString().toLowerCase(), true);

            return new CustomPropertyDefinition(rootNode, AttributeInclusion.YES);
        }
        return null;
    }

    private static String getSchemaName(Class<?> c) {
        if (c.isAnnotationPresent(ProcessInput.class)) {
            ProcessInput process = c.getAnnotation(ProcessInput.class);
            return JsonSchemaUtil.getJsonSchemaName(process.processName());
        }
        if (c.isAnnotationPresent(UserTask.class)) {
            UserTask userTask = c.getAnnotation(UserTask.class);
            return JsonSchemaUtil.getJsonSchemaName(userTask.processName(), userTask.taskName());
        }
        throw new RuntimeException("Cannot create the schema name. Class must be have UserTask or ProcessInput annotation");
    }

    private static boolean checkFields(FieldScope fieldScope) {
        if (Object.class.equals(fieldScope.getDeclaredType().getErasedType())) {
            logger.warn("Field '{}' in '{}' is declared as java.lang.Object and will be excluded from the " +
                    "generated JSON schema. The UI cannot render a form widget for an untyped field. " +
                    "Use a concrete type to enable form rendering.",
                    fieldScope.getDeclaredName(),
                    fieldScope.getDeclaringType().getErasedType().getSimpleName());
            return true;
        }
        return (fieldScope.getDeclaringType().getErasedType().isAnnotationPresent(UserTask.class) && fieldScope.getAnnotation(UserTaskParam.class) == null)
                || (fieldScope.getDeclaringType().getErasedType().isAnnotationPresent(ProcessInput.class) && fieldScope.getAnnotation(VariableInfo.class) == null);
    }

}
