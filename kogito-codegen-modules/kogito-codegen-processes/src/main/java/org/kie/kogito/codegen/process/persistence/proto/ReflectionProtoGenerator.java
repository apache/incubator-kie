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

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.drools.codegen.common.GeneratedFile;
import org.infinispan.protostream.annotations.ProtoEnumValue;
import org.kie.kogito.Model;
import org.kie.kogito.codegen.Generated;
import org.kie.kogito.codegen.VariableInfo;
import org.kie.kogito.codegen.process.persistence.ExclusionTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;

public class ReflectionProtoGenerator extends AbstractProtoGenerator<Class<?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReflectionProtoGenerator.class);

    private ReflectionProtoGenerator(Collection<Class<?>> modelClasses, Collection<Class<?>> dataClasses) {
        super(modelClasses, dataClasses);
    }

    @Override
    protected boolean isEnum(Class<?> dataModel) {
        return dataModel.isEnum();
    }

    @Override
    protected ProtoMessage messageFromClass(Proto proto, Set<String> alreadyGenerated, Class<?> clazz, String messageComment, String fieldComment) throws Exception {

        if (!shouldGenerateProto(clazz)) {
            LOGGER.info("Skipping generating reflection proto for class {}", clazz);
            return null;
        }
        LOGGER.debug("Generating reflection proto for class {}", clazz);

        String clazzName = extractName(clazz).get();
        ProtoMessage message = new ProtoMessage(clazzName, clazz.getPackage().getName());
        Predicate<PropertyDescriptor> validPropertyFilter = property -> this.isValidProperty(clazz, property);
        List<PropertyDescriptor> propertiesDescriptor = List.of(Introspector.getBeanInfo(clazz).getPropertyDescriptors()).stream().filter(validPropertyFilter).toList();
        for (PropertyDescriptor pd : propertiesDescriptor) {

            Field propertyField = getFieldFromClass(clazz, pd.getName());

            // By default, only index id field from Model generated class
            String completeFieldComment = "id".equals(pd.getName()) && Model.class.isAssignableFrom(clazz) ? fieldComment.replace("Index.NO", "Index.YES") : fieldComment;

            VariableInfo varInfo = propertyField.getAnnotation(VariableInfo.class);
            if (varInfo != null) {
                completeFieldComment = fieldComment + "\n @VariableInfo(tags=\"" + varInfo.tags() + "\")";
            }

            String fieldTypeString = pd.getPropertyType().getCanonicalName();
            Class<?> fieldType = pd.getPropertyType();
            LOGGER.debug("Resolving proto type for field '{}' of type '{}' in '{}'", pd.getName(), fieldTypeString, clazz.getName());
            String protoType;
            if (pd.getPropertyType().isArray() && !pd.getPropertyType().getComponentType().isPrimitive()) {
                fieldTypeString = ARRAY;
                fieldType = pd.getPropertyType().getComponentType();
                protoType = protoType(fieldType.getCanonicalName());
            } else if (Collection.class.isAssignableFrom(pd.getPropertyType())) {
                fieldTypeString = COLLECTION;
                Type type = propertyField.getGenericType();
                if (type instanceof ParameterizedType) {
                    ParameterizedType ptype = (ParameterizedType) type;
                    fieldType = (Class<?>) ptype.getActualTypeArguments()[0];
                    protoType = protoType(fieldType.getCanonicalName());
                } else {
                    throw new IllegalArgumentException("Field " + propertyField.getName() + " of class " + clazz.getName() + " uses collection without type information");
                }
            } else {
                protoType = protoType(fieldTypeString);
            }

            if (protoType == null) {

                // recursive call to visit the type
                Optional<String> optionalProtoType = internalGenerate(proto, alreadyGenerated, messageComment, fieldComment, fieldType);
                if (!optionalProtoType.isPresent()) {
                    return message;
                }

                protoType = optionalProtoType.get();
            }

            ProtoField protoField = message.addField(computeCardinalityModifier(fieldTypeString), protoType, pd.getName());
            protoField.setComment(completeFieldComment);
            if (KOGITO_SERIALIZABLE.equals(protoType)) {
                protoField.setOption(format("[%s = \"%s\"]", KOGITO_JAVA_CLASS_OPTION, pd.getPropertyType().getCanonicalName()));

            } else if ("java.lang.Boolean".equals(fieldTypeString)) {
                protoField.setOption(format("[%s = \"%s\"]", KOGITO_JAVA_TYPE_BOOLEAN_OBJECT_OPTION, fieldTypeString));
            }

        }
        message.setComment(messageComment);
        proto.addMessage(message);
        return message;
    }

    protected boolean shouldGenerateProto(Class<?> clazz) {
        return extractName(clazz).isPresent();
    }

    @Override
    protected Optional<String> extractName(Class<?> clazz) {
        try {
            // builtins should not generate proto files
            BeanInfo beanInfo = Introspector.getBeanInfo(clazz);
            String name = beanInfo.getBeanDescriptor().getBeanClass().getSimpleName();

            Predicate<String> typeExclusions = ExclusionTypeUtils.createTypeExclusions();
            if (typeExclusions.test(clazz.getCanonicalName())) {
                return Optional.empty();
            }
            Generated generatedData = clazz.getAnnotation(Generated.class);
            if (generatedData != null) {
                name = generatedData.name().isEmpty() ? name : generatedData.name();
                if (generatedData.hidden()) {
                    // since class is marked as hidden skip processing of that class
                    return Optional.empty();
                }
            }
            return Optional.of(name);
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    protected String modelClassName(Class<?> dataModel) {
        return dataModel.getName();
    }

    private Field getFieldFromClass(Class<?> clazz, String name) {
        try {
            return clazz.getDeclaredField(name);
        } catch (Exception e) {
            if (clazz.getSuperclass() != null && !clazz.getSuperclass().equals(Object.class)) {
                return getFieldFromClass(clazz.getSuperclass(), name);
            } else {
                throw new IllegalArgumentException("Impossible to find field " + name + " in class " + clazz.getName());
            }
        }
    }

    @Override
    protected ProtoEnum enumFromClass(Proto proto, Class<?> clazz) throws Exception {
        try {
            return extractName(clazz)
                    .map(name -> {
                        ProtoEnum modelEnum = new ProtoEnum(name, clazz.getPackage().getName());
                        Stream.of(clazz.getDeclaredFields())
                                .filter(Field::isEnumConstant)
                                .sorted(Comparator.comparing(Field::getName))
                                .forEach(f -> addEnumField(f, modelEnum));
                        proto.addEnum(modelEnum);
                        return modelEnum;
                    }).orElse(null);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed class " + clazz.getName() + " " + e.getMessage(), e);
        }
    }

    private void addEnumField(Field field, ProtoEnum pEnum) {
        ProtoEnumValue protoEnumValue = field.getAnnotation(ProtoEnumValue.class);
        Integer ordinal = null;
        boolean sortedWithAnnotation = false;
        if (protoEnumValue != null) {
            sortedWithAnnotation = true;
            ordinal = protoEnumValue.number();
        }
        if (ordinal == null) {
            ordinal = Enum.valueOf((Class<Enum>) field.getType(), field.getName()).ordinal();
        }
        pEnum.addField(field.getName(), ordinal, sortedWithAnnotation);
    }

    @Override
    protected Optional<GeneratedFile> generateModelClassProto(Class<?> modelClazz) {

        Generated generatedData = modelClazz.getAnnotation(Generated.class);
        if (generatedData != null) {

            String processId = generatedData.reference();
            Proto modelProto = generate("@Indexed",
                    INDEX_COMMENT,
                    modelClazz.getPackage().getName() + "." + processId, modelClazz,
                    "import \"kogito-index.proto\";",
                    "import \"kogito-types.proto\";",
                    "option kogito_model = \"" + generatedData.name() + "\";",
                    "option kogito_id = \"" + processId + "\";");
            if (modelProto.getMessages().isEmpty()) {
                // no messages, nothing to do
                return Optional.empty();
            }
            ProtoMessage modelMessage = modelProto.getMessages().stream().filter(msg -> msg.getName().equals(generatedData.name())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Unable to find model message"));
            modelMessage.addField("optional", "org.kie.kogito.index.model.KogitoMetadata", "metadata").setComment(INDEX_COMMENT);

            return Optional.of(generateProtoFiles(processId, modelProto));
        }
        return Optional.empty();
    }

    public static Builder<Class<?>, ReflectionProtoGenerator> builder() {
        return new ReflectionProtoGeneratorBuilder();
    }

    private static class ReflectionProtoGeneratorBuilder extends AbstractProtoGeneratorBuilder<Class<?>, ReflectionProtoGenerator> {

        private static final Logger LOGGER = LoggerFactory.getLogger(ReflectionProtoGeneratorBuilder.class);

        private ReflectionProtoGeneratorBuilder() {
        }

        @Override
        protected Collection<Class<?>> extractDataClasses(Collection<Class<?>> modelClasses) {
            if (dataClasses != null || modelClasses == null) {
                LOGGER.info("Using provided dataClasses instead of extracting from modelClasses. This should happen only during tests.");
                return dataClasses;
            }
            Set<Class<?>> dataModelClasses = new HashSet<>();
            try {
                for (Class<?> modelClazz : modelClasses) {

                    BeanInfo beanInfo = Introspector.getBeanInfo(modelClazz);
                    for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
                        Class<?> propertyType = pd.getPropertyType();
                        if (propertyType.getCanonicalName().startsWith("java.lang")
                                || propertyType.getCanonicalName().equals(Date.class.getCanonicalName())
                                || propertyType.isPrimitive()
                                || propertyType.isInterface()) {
                            continue;
                        }

                        dataModelClasses.add(propertyType);
                    }
                }
                return dataModelClasses;
            } catch (IntrospectionException e) {
                throw new IllegalStateException("Error during bean introspection", e);
            }
        }

        @Override
        public ReflectionProtoGenerator build(Collection<Class<?>> modelClasses) {
            return new ReflectionProtoGenerator(modelClasses, extractDataClasses(modelClasses));
        }
    }

    private boolean isValidProperty(Class<?> clazz, PropertyDescriptor propertyDescriptor) {
        try {
            if (propertyDescriptor.getName().equals("class")) {
                return false;
            }

            Field propertyField = getFieldFromClass(clazz, propertyDescriptor.getName());

            // ignore static and/or transient fields
            int mod = propertyField.getModifiers();
            if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) {
                return false;
            }

            return true;
        } catch (IllegalArgumentException ex) {
            LOGGER.warn(ex.getMessage());
            // a method starting with get or set without a corresponding backing field makes java beans to
            // still generate a property descriptor, it should be ignored
            return false;
        }
    }
}
