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
package org.kie.kogito.quarkus.workflow.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.drools.codegen.common.GeneratedFile;
import org.infinispan.protostream.annotations.ProtoEnumValue;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.jboss.jandex.Type.Kind;
import org.kie.kogito.Model;
import org.kie.kogito.codegen.Generated;
import org.kie.kogito.codegen.VariableInfo;
import org.kie.kogito.codegen.process.persistence.proto.AbstractProtoGenerator;
import org.kie.kogito.codegen.process.persistence.proto.Proto;
import org.kie.kogito.codegen.process.persistence.proto.ProtoEnum;
import org.kie.kogito.codegen.process.persistence.proto.ProtoField;
import org.kie.kogito.codegen.process.persistence.proto.ProtoMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public class JandexProtoGenerator extends AbstractProtoGenerator<ClassInfo> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JandexProtoGenerator.class);
    private static final DotName ENUM_VALUE_ANNOTATION = DotName.createSimple(ProtoEnumValue.class.getName());
    private static final DotName generatedAnnotation = DotName.createSimple(Generated.class.getCanonicalName());
    private static final DotName variableInfoAnnotation = DotName.createSimple(VariableInfo.class.getCanonicalName());
    private static final DotName objectClass = DotName.createSimple(Object.class.getCanonicalName());
    private static final DotName modelClazz = DotName.createSimple(Model.class.getCanonicalName());
    private final IndexView index;

    JandexProtoGenerator(Collection<ClassInfo> modelClasses, Collection<ClassInfo> dataClasses, IndexView index) {
        super(modelClasses, dataClasses);
        this.index = index;
    }

    @Override
    protected boolean isEnum(ClassInfo dataModel) {
        return dataModel.superName() != null && Enum.class.getName().equals(dataModel.superName().toString());
    }

    @Override
    protected String modelClassName(ClassInfo dataModel) {
        return dataModel.asClass().name().toString();
    }

    @Override
    protected Optional<String> extractName(ClassInfo clazz) {
        if (isHidden(clazz)) {
            // since class is marked as hidden skip processing of that class
            return Optional.empty();
        }

        String name = clazz.simpleName();
        String altName = getReferenceOfModel(clazz, "name");
        if (altName != null) {
            name = altName;
        }
        return Optional.of(name);
    }

    protected Optional<String> fqn(ClassInfo dataModel) {
        if (isHidden(dataModel)) {
            // since class is marked as hidden skip processing of that class
            return Optional.empty();
        }

        String name = dataModel.simpleName();
        String altName = getReferenceOfModel(dataModel, "name");
        if (altName != null) {
            name = altName;
        }
        return Optional.of(dataModel.name().packagePrefix() + "." + name);
    }

    @Override
    protected ProtoMessage messageFromClass(Proto proto, Set<String> alreadyGenerated, ClassInfo clazz, String messageComment, String fieldComment) throws Exception {
        if (!shouldGenerateProto(clazz)) {
            LOGGER.info("Skipping generating jandex proto for class {}", clazz);
            return null;
        }
        LOGGER.debug("Generating reflection proto for class {}", clazz);

        String name = extractName(clazz).get();

        ProtoMessage message = new ProtoMessage(name, clazz.name().prefix().toString());
        for (FieldInfo pd : extractAllFields(clazz)) {
            // ignore static and/or transient fields
            if (Modifier.isStatic(pd.flags()) || Modifier.isTransient(pd.flags())) {
                continue;
            }

            // By default, only index id field from Model generated class
            String completeFieldComment =
                    "id".equals(pd.name()) && clazz.interfaceTypes().stream().anyMatch(t -> t.name().equals(modelClazz)) ? fieldComment.replace("Index.NO", "Index.YES") : fieldComment;

            AnnotationInstance variableInfo = pd.annotation(variableInfoAnnotation);

            if (variableInfo != null) {
                completeFieldComment = fieldComment + "\n @VariableInfo(tags=\"" + variableInfo.value("tags").asString()
                        + "\")";
            }

            String fieldTypeString = pd.type().name().toString();

            DotName fieldType = pd.type().name();
            LOGGER.debug("Resolving proto type for field '{}' of type '{}' in '{}'", pd.name(), fieldTypeString, clazz.name());
            String protoType;
            if (isArray(pd)) {
                fieldTypeString = ARRAY;
                fieldType = pd.type().asArrayType().component().name();
                protoType = protoType(fieldType.toString());
            } else if (isCollection(pd)) {
                fieldTypeString = COLLECTION;

                List<Type> typeParameters = pd.type().kind() == Kind.CLASS ? emptyList() : pd.type().asParameterizedType().arguments();
                if (typeParameters.isEmpty()) {
                    throw new IllegalArgumentException("Field " + pd.name() + " of class " + clazz.name().toString()
                            + " uses collection without type information");
                }
                fieldType = typeParameters.get(0).name();
                protoType = protoType(fieldType.toString());
            } else {
                protoType = protoType(fieldTypeString);
            }

            if (protoType == null) {
                ClassInfo classInfo = index.getClassByName(fieldType);
                if (classInfo == null) {
                    throw new IllegalStateException("Cannot find class info in jandex index for " + fieldType);
                }

                // recursive call to visit the type
                Optional<String> optionalProtoType = internalGenerate(proto, alreadyGenerated, messageComment, fieldComment, classInfo);
                if (!optionalProtoType.isPresent()) {
                    return message;
                }

                protoType = optionalProtoType.get();
            }

            ProtoField protoField = message.addField(computeCardinalityModifier(fieldTypeString), protoType, pd.name());
            protoField.setComment(completeFieldComment);
            if (KOGITO_SERIALIZABLE.equals(protoType)) {
                protoField.setOption(format("[%s = \"%s\"]", KOGITO_JAVA_CLASS_OPTION, fieldTypeString.equals(ARRAY) ? pd.type().toString() : pd.type().name().toString()));
            } else if ("java.lang.Boolean".equals(fieldTypeString)) {
                protoField.setOption(format("[%s = \"%s\"]", KOGITO_JAVA_TYPE_BOOLEAN_OBJECT_OPTION, fieldTypeString));
            }
        }
        message.setComment(messageComment);
        proto.addMessage(message);
        return message;
    }

    protected boolean shouldGenerateProto(ClassInfo clazz) {
        return extractName(clazz).isPresent();
    }

    private boolean isCollection(FieldInfo pd) {
        if (pd.type().kind() == Kind.PARAMETERIZED_TYPE || pd.type().kind() == Kind.CLASS) {
            try {
                Class<?> clazz = Class.forName(pd.type().name().toString());
                return Collection.class.isAssignableFrom(clazz);
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
        return false;
    }

    private boolean isArray(FieldInfo pd) {
        return pd.type().kind() == Kind.ARRAY && pd.type().asArrayType().component().kind() != Kind.PRIMITIVE;
    }

    /**
     * ClassInfo.fields() returns only fields of current class so this method fetch fields from all hierarchy
     *
     * @param clazz
     * @return
     */
    private Collection<FieldInfo> extractAllFields(ClassInfo clazz) {
        Collection<FieldInfo> toReturn = new ArrayList<>(clazz.fields());
        DotName superClass = clazz.superName();
        if (superClass != null && !superClass.equals(objectClass)) {
            toReturn.addAll(extractAllFields(index.getClassByName(superClass)));
        }
        return toReturn;
    }

    @Override
    protected ProtoEnum enumFromClass(Proto proto, ClassInfo clazz) {
        try {
            return extractName(clazz)
                    .map(name -> {
                        ProtoEnum modelEnum = new ProtoEnum(name, clazz.name().prefix().toString());
                        clazz.fields().stream()
                                .filter(FieldInfo::isEnumConstant)
                                .sorted(Comparator.comparing(FieldInfo::name))
                                .forEach(f -> addEnumField(f, modelEnum));
                        proto.addEnum(modelEnum);
                        return modelEnum;
                    }).orElse(null);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed class " + clazz.name() + " " + e.getMessage(), e);
        }
    }

    private void addEnumField(FieldInfo field, ProtoEnum pEnum) {
        AnnotationInstance annotation = field.annotation(ENUM_VALUE_ANNOTATION);
        Integer ordinal = null;
        boolean sortedWithAnnotation = false;
        if (annotation != null) {
            AnnotationValue number = annotation.value("number");
            if (number != null) {
                sortedWithAnnotation = true;
                ordinal = number.asInt();
            }
        }
        if (ordinal == null) {
            String clazzName = field.type().name().toString();
            ClassInfo classByName = index.getClassByName(DotName.createSimple(clazzName));
            if (!classByName.isEnum()) {
                throw new IllegalArgumentException(format("Unsupported type, class %s, is not an enum.", clazzName));
            }
            List<FieldInfo> constants = classByName.unsortedFields().stream().filter(FieldInfo::isEnumConstant).collect(toList());
            ordinal = constants.indexOf(field);
            if (ordinal == -1) {
                throw new IllegalArgumentException(format("Can not find enum field ordinal for %s.%s", clazzName, field.name()));
            }
        }
        pEnum.addField(field.name(), ordinal, sortedWithAnnotation);
    }

    @Override
    protected Optional<GeneratedFile> generateModelClassProto(ClassInfo modelClazz) {

        String processId = getReferenceOfModel(modelClazz, "reference");
        String name = getReferenceOfModel(modelClazz, "name");

        if (processId != null) {

            Proto modelProto = generate("@Indexed",
                    INDEX_COMMENT,
                    modelClazz.name().prefix().toString() + "." + processId, modelClazz,
                    "import \"kogito-index.proto\";",
                    "import \"kogito-types.proto\";",
                    "option kogito_model = \"" + name + "\";",
                    "option kogito_id = \"" + processId + "\";");

            if (modelProto.getMessages().isEmpty()) {
                // no messages, nothing to do
                return Optional.empty();
            }

            ProtoMessage modelMessage = modelProto.getMessages().stream().filter(msg -> msg.getName().equals(name)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Unable to find model message"));
            modelMessage.addField("optional", "org.kie.kogito.index.model.KogitoMetadata", "metadata")
                    .setComment(INDEX_COMMENT);

            return Optional.of(generateProtoFiles(processId, modelProto));
        }

        return Optional.empty();
    }

    protected String getReferenceOfModel(ClassInfo modelClazz, String name) {
        AnnotationInstance generatedData = modelClazz.classAnnotation(generatedAnnotation);

        if (generatedData != null) {

            return generatedData.value(name).asString();
        }

        return null;
    }

    protected boolean isHidden(ClassInfo modelClazz) {
        AnnotationInstance generatedData = modelClazz.classAnnotation(generatedAnnotation);

        if (generatedData != null) {
            return generatedData.value("hidden").asBoolean();
        }

        return false;
    }

    public static Builder<ClassInfo, JandexProtoGenerator> builder(IndexView index) {
        return new JandexProtoGeneratorBuilder(index);
    }

    private static class JandexProtoGeneratorBuilder extends AbstractProtoGeneratorBuilder<ClassInfo, JandexProtoGenerator> {

        private static final Logger LOGGER = LoggerFactory.getLogger(JandexProtoGeneratorBuilder.class);
        private final IndexView index;

        private JandexProtoGeneratorBuilder(IndexView index) {
            this.index = index;
        }

        @Override
        protected Collection<ClassInfo> extractDataClasses(Collection<ClassInfo> modelClasses) {
            if (dataClasses != null || modelClasses == null) {
                LOGGER.info("Using provided dataClasses instead of extracting from modelClasses. This should happen only during tests.");
                return dataClasses;
            }
            Set<ClassInfo> dataModelClasses = new HashSet<>();
            for (ClassInfo modelClazz : modelClasses) {

                for (FieldInfo pd : modelClazz.fields()) {

                    if (pd.type().name().toString().startsWith("java.lang")
                            || pd.type().name().toString().startsWith("java.util")
                            || pd.type().name().toString().equals(Date.class.getCanonicalName())) {
                        continue;
                    }
                    ClassInfo clazzInfo = index.getClassByName(pd.type().name());
                    if (clazzInfo != null) {
                        dataModelClasses.add(clazzInfo);
                    }
                }
            }
            return dataModelClasses;
        }

        @Override
        public JandexProtoGenerator build(Collection<ClassInfo> modelClasses) {
            return new JandexProtoGenerator(modelClasses, extractDataClasses(modelClasses), index);
        }
    }
}
