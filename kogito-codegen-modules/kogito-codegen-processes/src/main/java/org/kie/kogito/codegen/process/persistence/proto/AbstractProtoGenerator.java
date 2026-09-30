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
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.drools.codegen.common.GeneratedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static java.lang.String.format;

public abstract class AbstractProtoGenerator<T> implements ProtoGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractProtoGenerator.class);

    protected static final List<String> PROTO_BUILTINS = List.of(JsonNode.class.getName(), Document.class.getName());
    private static final String GENERATED_PROTO_RES_PATH = "persistence/protobuf/";
    private static final String LISTING_FILE = "list.json";

    protected final ObjectMapper mapper;
    protected final Collection<T> modelClasses;
    protected final Collection<T> dataClasses;

    protected AbstractProtoGenerator(Collection<T> rawModelClasses, Collection<T> rawDataClasses) {
        this.modelClasses = rawModelClasses == null ? Collections.emptyList() : rawModelClasses;
        this.dataClasses = rawDataClasses == null ? Collections.emptyList() : rawDataClasses;
        this.mapper = new ObjectMapper();
    }

    @Override
    public Proto protoOfDataClasses(String packageName, String... headers) {
        return generate(null, null, packageName, dataClasses, headers);
    }

    @Override
    public List<String> protoBuiltins() {
        return PROTO_BUILTINS;
    }

    @Override
    public Collection<GeneratedFile> generateProtoFiles() {
        validateClasses();

        List<GeneratedFile> generatedFiles = new ArrayList<>();

        modelClasses.stream()
                .map(this::generateModelClassProto)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(generatedFiles::add);

        try {
            this.generateProtoListingFile(generatedFiles).ifPresent(generatedFiles::add);
        } catch (IOException e) {
            throw new UncheckedIOException("Error during proto listing file creation", e);
        }

        return generatedFiles;
    }

    private void validateClasses() {
        Set<String> modelNames = modelClasses.stream()
                .map(this::extractName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        dataClasses.forEach(c -> extractName(c).ifPresent(name -> {
            if (modelNames.contains(name.toLowerCase())) {
                throw new IllegalStateException(
                        format("The data class '%s' name, used as process variable, conflicts with the generated process model classes for Data Index protobuf. Please rename either the process '%s' or the Java class.",
                                modelClassName(c), name));
            }
        }));
    }

    protected abstract boolean isEnum(T dataModel);

    protected Optional<String> fqn(T dataModel) {
        return extractName(dataModel);
    }

    protected abstract Optional<String> extractName(T dataModel);

    protected abstract ProtoEnum enumFromClass(Proto proto, T clazz) throws Exception;

    protected abstract ProtoMessage messageFromClass(Proto proto, Set<String> alreadyGenerated, T clazz, String messageComment, String fieldComment) throws Exception;

    protected abstract Optional<GeneratedFile> generateModelClassProto(T modelClazz);

    /**
     * Generates the proto files from the given model.
     */
    protected final GeneratedFile generateProtoFiles(final String processId, final Proto modelProto) {
        String protoFileName = processId + ".proto";
        return new GeneratedFile(PROTO_TYPE,
                GENERATED_PROTO_RES_PATH + protoFileName,
                modelProto.toString());
    }

    /**
     * Iterates over the generated files and extract all the proto files. Then it creates and add to the generated files collection
     * a listing file ({@link #LISTING_FILE}) from its content.
     *
     * @param generatedFiles The list of generated files.
     * @throws IOException if something wrong occurs during I/O
     */
    protected final Optional<GeneratedFile> generateProtoListingFile(Collection<GeneratedFile> generatedFiles) throws IOException {
        List<String> fileNames = generatedFiles.stream()
                .filter(x -> x.relativePath().contains(GENERATED_PROTO_RES_PATH))
                .map(x -> x.relativePath().substring(x.relativePath().lastIndexOf("/") + 1))
                .collect(Collectors.toList());

        if (!fileNames.isEmpty()) {
            return Optional.of(new GeneratedFile(PROTO_TYPE,
                    GENERATED_PROTO_RES_PATH + LISTING_FILE,
                    mapper.writeValueAsString(fileNames)));
        }
        return Optional.empty();
    }

    public Collection<T> getModelClasses() {
        return Collections.unmodifiableCollection(modelClasses);
    }

    public Collection<T> getDataClasses() {
        return Collections.unmodifiableCollection(dataClasses);
    }

    protected Proto generate(String messageComment, String fieldComment, String packageName, T dataModel, String... headers) {
        return generate(messageComment, fieldComment, packageName, Collections.singleton(dataModel), headers);
    }

    protected Proto generate(String messageComment, String fieldComment, String packageName, Collection<T> dataModels, String... headers) {
        Proto proto = new Proto(packageName, headers);
        Set<String> alreadyGenerated = new HashSet<>();
        alreadyGenerated.addAll(protoBuiltins());
        for (T dataModel : dataModels.stream().filter(this::filterDataModels).toList()) {
            try {
                LOGGER.debug("internal proto generation {}", fqn(dataModel));
                internalGenerate(
                        proto,
                        alreadyGenerated,
                        messageComment,
                        fieldComment,
                        dataModel);
            } catch (Exception e) {
                throw new RuntimeException("Error while generating proto for model class " + modelClassName(dataModel) + " " + e.getMessage(), e);
            }
        }
        return proto;
    }

    private boolean filterDataModels(T type) {
        Optional<String> stringType = fqn(type);
        if (stringType.isEmpty()) {
            return false;
        }

        return !protoBuiltins().contains(stringType.get());
    }

    protected abstract String modelClassName(T dataModel);

    protected Optional<String> internalGenerate(Proto proto, Set<String> alreadyGenerated, String messageComment, String fieldComment, T dataModel) throws Exception {
        String protoType;
        if (isEnum(dataModel)) {
            protoType = enumFromClass(proto, dataModel).getName();
        } else {

            Optional<String> optionalName = extractName(dataModel);
            if (!optionalName.isPresent()) {
                // skip if name is hidden
                return Optional.empty();
            }

            if (alreadyGenerated.contains(optionalName.get())) {
                // if already visited avoid infinite recursion
                return optionalName;
            }

            alreadyGenerated.add(optionalName.get());
            protoType = messageFromClass(proto, alreadyGenerated, dataModel, messageComment, fieldComment).getName();
        }
        return Optional.ofNullable(protoType);
    }

    protected abstract static class AbstractProtoGeneratorBuilder<E, T extends ProtoGenerator> implements Builder<E, T> {

        protected Collection<E> dataClasses;

        protected abstract Collection<E> extractDataClasses(Collection<E> modelClasses);

        @Override
        public Builder<E, T> withDataClasses(Collection<E> dataClasses) {
            this.dataClasses = dataClasses;
            return this;
        }
    }

    protected String computeCardinalityModifier(String type) {
        if (type.equals(COLLECTION) || type.equals(ARRAY)) {
            return "repeated";
        }

        return "optional";
    }

    protected String protoType(String type) {
        if (protoBuiltins().contains(type)) {
            return null;
        }
        LOGGER.debug("Computing proto type for {}", type);

        if (String.class.getCanonicalName().equals(type) || String.class.getSimpleName().equalsIgnoreCase(type)) {
            return "string";
        } else if (Integer.class.getCanonicalName().equals(type) || "int".equalsIgnoreCase(type)) {
            return "int32";
        } else if (Long.class.getCanonicalName().equals(type) || "long".equalsIgnoreCase(type)) {
            return "int64";
        } else if (Double.class.getCanonicalName().equals(type) || "double".equalsIgnoreCase(type)) {
            return "double";
        } else if (Float.class.getCanonicalName().equals(type) || "float".equalsIgnoreCase(type)) {
            return "float";
        } else if (Boolean.class.getCanonicalName().equals(type) || "boolean".equalsIgnoreCase(type)) {
            return "bool";
        } else if (Date.class.getCanonicalName().equals(type) || "date".equalsIgnoreCase(type)) {
            return "kogito.Date";
        } else if (byte[].class.getCanonicalName().equals(type) || "[B".equalsIgnoreCase(type)) {
            return "bytes";
        } else if (Instant.class.getCanonicalName().equals(type)) {
            return "kogito.Instant";
        } else if (type.startsWith("java.lang") || type.startsWith("java.util") || type.startsWith("java.time") || type.startsWith("java.math")) {
            try {
                Class<?> cls = Class.forName(type);
                if (cls.isInterface()) {
                    return null;
                }
                if (Object.class.getCanonicalName().equals(type)) {
                    LOGGER.warn("Process variable is declared as java.lang.Object. " +
                            "Object type variables are persisted using Java serialization. " +
                            "Make sure the stored value implements java.io.Serializable.");
                    return KOGITO_SERIALIZABLE;
                }
                boolean assignable = Serializable.class.isAssignableFrom(cls);
                if (assignable) {
                    return KOGITO_SERIALIZABLE;
                } else {
                    throw new IllegalArgumentException(format("Java type %s is not supported by Kogito persistence, please consider using a class that extends java.io.Serializable", type));
                }
            } catch (ClassNotFoundException e) {
                return null;
            }
        } else {
            try {
                Class<?> cls = Class.forName(type);
                if (cls.isEnum() || hasDefaultConstructor(cls)) {
                    return null;
                } else if (Serializable.class.isAssignableFrom(cls)) {
                    return KOGITO_SERIALIZABLE;
                } else {
                    throw new IllegalArgumentException(
                            format("Custom type %s is no supported by Kogito persistence, please consider using a class that extends java.io.Serializable and contains a no arg constructor", type));
                }
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    }

    protected boolean hasDefaultConstructor(Class<?> cls) {
        for (Constructor<?> c : cls.getConstructors()) {
            if (c.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

}
