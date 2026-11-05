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
package org.drools.modelcompiler.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.drools.base.factmodel.AccessibleFact;
import org.drools.base.factmodel.AnnotationDefinition;
import org.drools.base.factmodel.ClassDefinition;
import org.drools.base.factmodel.FieldDefinition;
import org.drools.base.rule.TypeDeclaration;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.base.util.PropertyReactivityUtil;
import org.drools.base.util.TimeIntervalParser;
import org.drools.model.AnnotationValue;
import org.drools.model.TypeMetaData;
import org.drools.modelcompiler.constraints.LambdaFieldReader;
import org.drools.modelcompiler.constraints.LambdaReadAccessor;
import org.drools.util.ClassUtils;
import org.drools.util.TypeResolver;
import org.kie.api.definition.type.Expires;
import org.kie.api.definition.type.Position;
import org.kie.api.definition.type.Role;
import org.kie.internal.builder.conf.PropertySpecificOption;

import static org.drools.base.rule.TypeDeclaration.createTypeDeclarationForBean;

public class TypeDeclarationUtil {

    private static final Set<String> KNOWN_ANNOTATIONS = Set.of(
        "role", "duration", "timestamp", "expires", "propertyReactive", "classReactive",
        "propertyChangeSupport"
    );

    private static final Map<String, String> KNOWN_ANNOTATIONS_BY_FQN = Map.of(
        "org.kie.api.definition.type.Role", "role",
        "org.kie.api.definition.type.Duration", "duration",
        "org.kie.api.definition.type.Timestamp", "timestamp",
        "org.kie.api.definition.type.Expires", "expires",
        "org.kie.api.definition.type.PropertyReactive", "propertyReactive",
        "org.kie.api.definition.type.ClassReactive", "classReactive",
        "org.kie.api.definition.type.PropertyChangeSupport", "propertyChangeSupport"
    );

    private static final Map<String, String> KNOWN_ANNOTATIONS_BY_LOWER_NAME = KNOWN_ANNOTATIONS.stream()
            .collect(Collectors.toMap(name -> name.toLowerCase(Locale.ROOT), name -> name));

    /**
     * Canonical name of a built-in type annotation as written in a declare block: the DRL
     * parser also accepts the Java (capitalized) form of these annotations, e.g. {@code @Role}
     * and {@code @Expires}, and the classic compiler resolves both spellings — so an unqualified
     * name matches its built-in case-insensitively and maps to the canonical spelling. A
     * qualified name only maps when it is a built-in's own FQN; any other qualified annotation
     * stays itself (custom metadata).
     */
    private static String canonicalAnnotationName(String name) {
        if (name.indexOf('.') >= 0) {
            return KNOWN_ANNOTATIONS_BY_FQN.getOrDefault(name, name);
        }
        return KNOWN_ANNOTATIONS_BY_LOWER_NAME.getOrDefault(name.toLowerCase(Locale.ROOT), name);
    }

    public static TypeDeclaration createTypeDeclaration(TypeMetaData metaType, PropertySpecificOption propertySpecificOption, TypeResolver typeResolver) {
        Class<?> typeClass = metaType.getType();

        TypeDeclaration typeDeclaration = createTypeDeclarationForBean( typeClass, propertySpecificOption );
        typeDeclaration.setTypeClassDef( AccessibleFact.class.isAssignableFrom( typeClass ) ?
                new AccessibleClassDefinition( typeClass, typeResolver ) :
                new DynamicClassDefinition( typeClass ) );

        wireTimeAccessors( typeDeclaration );
        wireMetaTypeAnnotations( metaType, typeDeclaration );
        wireFields(typeClass, typeDeclaration);

        return typeDeclaration;
    }

    // accepts the same forms as the DRL compiler: event, EVENT, Role.Type.EVENT
    private static Role.Type parseRole( String value ) {
        return Role.Type.valueOf( value.substring( value.lastIndexOf( '.' ) + 1 ).toUpperCase( Locale.ROOT ) );
    }

    private static void wireMetaTypeAnnotations( TypeMetaData metaType, TypeDeclaration typeDeclaration ) {
        ClassDefinition classDef = typeDeclaration.getTypeClassDef();
        for (Map.Entry<String, AnnotationValue[]> ann : metaType.getAnnotations().entrySet()) {
            String annotationName = canonicalAnnotationName(ann.getKey());
            boolean isKnownAnnotation = KNOWN_ANNOTATIONS.contains(annotationName);
            
            switch (annotationName) {
                case "role":
                    for (AnnotationValue annVal : ann.getValue()) {
                        if (annVal.getKey().equals( "value" )) {
                            typeDeclaration.setRole( parseRole( annVal.getValue().toString() ) );
                        }
                    }
                    break;
                case "duration":
                    for (AnnotationValue annVal : ann.getValue()) {
                        if (annVal.getKey().equals( "value" )) {
                            wireDurationAccessor( annVal.getValue().toString(), typeDeclaration );
                        }
                    }
                    break;
                case "timestamp":
                    for (AnnotationValue annVal : ann.getValue()) {
                        if (annVal.getKey().equals( "value" )) {
                            wireTimestampAccessor( annVal.getValue().toString(), typeDeclaration );
                        }
                    }
                    break;
                case "expires":
                    for (AnnotationValue annVal : ann.getValue()) {
                        if (annVal.getKey().equals( "value" )) {
                            long offset = TimeIntervalParser.parseSingle( annVal.getValue().toString() );
                            typeDeclaration.setExpirationOffset(offset == -1L ? Long.MAX_VALUE : offset);
                            typeDeclaration.setExpirationType( Expires.Policy.TIME_HARD );
                        } else if (annVal.getKey().equals( "policy" )) {
                            typeDeclaration.setExpirationType( Enum.valueOf( Expires.Policy.class, annVal.getValue().toString() ) );
                        }
                    }
                    break;
                case "propertyReactive":
                    typeDeclaration.setPropertyReactive( true );
                    break;
                case "classReactive":
                    typeDeclaration.setPropertyReactive( false );
                    break;
                case "propertyChangeSupport":
                    typeDeclaration.setDynamic( true );
                    break;
            }
            
            // For non-defined custom annotations, add them as metadata to the ClassDefinition
            if (!isKnownAnnotation && classDef != null) {
                // Get the value from the annotation values
                Object value = null;
                for (AnnotationValue annVal : ann.getValue()) {
                    if (annVal.getKey().equals("value")) {
                        value = annVal.getValue();
                        break;
                    }
                }
                classDef.addMetaData(ann.getKey(), value);
            }
        }

        // Process field metadata from TypeMetaData
        if (classDef != null) {
            for (TypeMetaData.FieldMetaData fieldMetaData : metaType.getFields()) {
                String fieldName = fieldMetaData.getFieldName();
                FieldDefinition fieldDef = classDef.getField(fieldName);

                if (fieldDef != null) {
                    // Add field metadata to the FieldDefinition
                    for (Map.Entry<String, AnnotationValue[]> fieldAnn : fieldMetaData.getAnnotations().entrySet()) {
                        Object value = null;
                        for (AnnotationValue annVal : fieldAnn.getValue()) {
                            if (annVal.getKey().equals("value")) {
                                value = annVal.getValue();
                                break;
                            }
                        }
                        fieldDef.addMetaData(fieldAnn.getKey(), value);
                    }
                }
            }
        }
    }

    // the attribute names were already set from the class annotations by createTypeDeclarationForBean()
    private static void wireTimeAccessors( TypeDeclaration typeDeclaration ) {
        if (typeDeclaration.getDurationAttribute() != null) {
            wireDurationAccessor( typeDeclaration.getDurationAttribute(), typeDeclaration );
        }
        if (typeDeclaration.getTimestampAttribute() != null) {
            wireTimestampAccessor( typeDeclaration.getTimestampAttribute(), typeDeclaration );
        }
    }

    private static void wireFields(Class<?> typeClass, TypeDeclaration typeDeclaration) {
        ClassDefinitionForModel typeClassDef = (ClassDefinitionForModel) typeDeclaration.getTypeClassDef();
        List<String> properties = PropertyReactivityUtil.getAccessibleProperties(typeClass);
        for (String property : properties) {
            typeClassDef.getField(property); // populates fields
        }
    }

    public static TypeDeclaration createTypeDeclaration(Class<?> cls, PropertySpecificOption propertySpecificOption) {
        TypeDeclaration typeDeclaration = createTypeDeclarationForBean( cls, propertySpecificOption );
        wireTimeAccessors( typeDeclaration );
        return typeDeclaration;
    }

    private static void wireDurationAccessor( String durationField, TypeDeclaration type ) {
        type.setDurationAttribute(durationField);
        type.setDurationExtractor(getFieldExtractor( type, durationField, long.class ));
    }

    private static void wireTimestampAccessor( String timestampField, TypeDeclaration type ) {
        type.setTimestampAttribute(timestampField);
        type.setTimestampExtractor(getFieldExtractor( type, timestampField, long.class ));
    }

    private static ReadAccessor getFieldExtractor( TypeDeclaration type, String field, Class<?> returnType ) {
        return new LambdaReadAccessor( returnType, new LambdaFieldReader( type.getTypeClass(), field ) );
    }

    public static class ClassDefinitionForModel extends ClassDefinition {

        public ClassDefinitionForModel() { }

        public ClassDefinitionForModel( Class<?> cls ) {
            super(cls);
        }

        @Override
        public final FieldDefinition getField(final String fieldName) {
            return fields.computeIfAbsent( fieldName, name -> {
                java.lang.reflect.Field f = ClassUtils.getField( getDefinedClass(), name );
                return f == null ? null : new FieldDefinitionForModel( this, f );
            });
        }

        @Override
        public Map<String, Object> getAsMap(Object bean) {
            Map<String, Object> m = new HashMap<>(fields.size());
            for (String field : fields.keySet()) {
                m.put(field, get(bean, field));
            }
            return m;
        }
    }

    public static class DynamicClassDefinition extends ClassDefinitionForModel {

        public DynamicClassDefinition() { }

        public DynamicClassDefinition( Class<?> cls ) {
            super( cls );
        }

        @Override
        public Object get(Object bean, String field) {
            java.lang.reflect.Field f = ClassUtils.getField(getDefinedClass(), field );
            if (f != null) {
                f.setAccessible( true );
                try {
                    return f.get( bean );
                } catch (IllegalAccessException e) {
                    throw new RuntimeException( e );
                }
            }
            return null;
        }

        @Override
        public void set(Object bean, String field, Object value) {
            java.lang.reflect.Field f = ClassUtils.getField( getDefinedClass(), field );
            if (f != null) {
                f.setAccessible( true );
                try {
                    f.set( bean, value );
                } catch (IllegalAccessException e) {
                    throw new RuntimeException( e );
                }
            }
        }
    }

    public static class AccessibleClassDefinition extends ClassDefinitionForModel {
        public AccessibleClassDefinition() { }

        public AccessibleClassDefinition( Class<?> cls, TypeResolver typeResolver ) {
            super( cls );
            processAnnotations( cls, typeResolver );
        }

        private void processAnnotations( Class<?> cls, TypeResolver typeResolver ) {
            for (Annotation ann: cls.getAnnotations()) {
                try {
                    Map<String, Object> valueMap = new HashMap<>();
                    Class<?> annotationClass = null;
                    Object value = null;
                    for (Method m : ann.getClass().getMethods()) {
                        if (m.getParameterCount() == 0 && m.getReturnType() != Void.class && m.getDeclaringClass() != Object.class &&
                                !m.getName().equals( "hashCode" ) && !m.getName().equals( "toString" )) {
                            if (m.getName().equals( "annotationType" )) {
                                annotationClass = (Class<?>) m.invoke( ann );
                            } else {
                                valueMap.put( m.getName(), m.invoke( ann ) );
                                if (m.getName().equals( "value" )) {
                                    value = m.invoke( ann );
                                }
                            }
                        }
                    }
                    if (annotationClass != null) {
                        addAnnotation( AnnotationDefinition.build( annotationClass, valueMap, typeResolver ) );
                        if ( value != null && annotationClass.getCanonicalName().startsWith( "org.kie.api.definition.type" ) ) {
                            addMetaData( annotationClass.getSimpleName().toLowerCase(), value.toString().toLowerCase() );
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException( e );
                }
            }
        }

        @Override
        public Object get(Object bean, String field) {
            return (( AccessibleFact ) bean).getValue( field );
        }

        @Override
        public void set(Object bean, String field, Object value) {
            (( AccessibleFact ) bean).setValue( field, value );
        }
    }

    public static class FieldDefinitionForModel extends FieldDefinition {

        private ClassDefinitionForModel classDef;

        private java.lang.reflect.Field field;

        public FieldDefinitionForModel() { }

        public FieldDefinitionForModel(ClassDefinitionForModel classDef, java.lang.reflect.Field field) {
            super(field.getName(), field.getGenericType().getTypeName());
            this.classDef = classDef;
            this.field = field;

            Position position = field.getAnnotation(Position.class);
            if (position != null) {
                setIndex(position.value());
            }
        }

        @Override
        public Class<?> getType() {
            return field.getType();
        }

        @Override
        public Object getValue(Object bean) {
            return this.classDef.get(bean, field.getName());
        }

        @Override
        public void setValue(Object bean, Object value) {
            this.classDef.set(bean, field.getName(), value);
        }

        @Override
        public Object get(Object bean) {
            return this.classDef.get(bean, field.getName());
        }

        @Override
        public void set(Object bean, Object value) {
            this.classDef.set(bean, field.getName(), value);
        }
    }

    private TypeDeclarationUtil() {
        // It si not allowed to create instances of util classes.
    }
}
