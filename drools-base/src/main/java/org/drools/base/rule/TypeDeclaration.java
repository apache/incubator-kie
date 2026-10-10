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
package org.drools.base.rule;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.List;

import org.drools.base.base.ClassObjectType;
import org.drools.base.base.ObjectType;
import org.drools.base.factmodel.ClassDefinition;
import org.drools.base.factmodel.GeneratedFact;
import org.drools.base.prototype.PrototypeObjectType;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.base.util.PropertyReactivityUtil;
import org.drools.base.util.TimeIntervalParser;
import org.drools.util.ClassUtils;
import org.kie.api.definition.type.ClassReactive;
import org.kie.api.definition.type.Expires;
import org.kie.api.definition.type.Expires.Policy;
import org.kie.api.definition.type.PropertyChangeSupport;
import org.kie.api.definition.type.PropertyReactive;
import org.kie.api.definition.type.Role;
import org.kie.api.io.Resource;
import org.kie.api.prototype.Prototype;
import org.kie.internal.builder.conf.PropertySpecificOption;
import org.kie.internal.definition.KnowledgeDefinition;

/**
 * The type declaration class stores all type's metadata
 * declared in source files.
 */
public class TypeDeclaration
    implements
    KnowledgeDefinition,
    Externalizable,
    Comparable<TypeDeclaration> {

    public static final long NEVER_EXPIRES = -1;

    public static final int ROLE_BIT                    = 1;
    public static final int TYPESAFE_BIT                = 2;
    public static final int FORMAT_BIT                  = 4;
    public static final int KIND_BIT                    = 8;
    public static final int NATURE_BIT                  = 16;

    public int setMask                                  = 0;

    public enum Kind {
        CLASS, TRAIT, ENUM
    }

    public enum Format {
        POJO, TEMPLATE
    }

    public enum Nature {
        /**
         * A DECLARATION is a Type Declaration that does not contain any
         * field definition and that is just used to add meta-data to an
         * DEFINITION.
         * A DEFINITION of an exiting DEFINITION is also considered a DECLARATION
         */
        DECLARATION,
        /**
         * A DEFINITION is:
         *  1.- Type Declaration containing field definitions.
         *  2.- A DECLARATION with no previous DEFINITION
         */
        DEFINITION;

        public static final String ID = "nature";

        public static Nature parseNature(String nature) {
            if ( "declaration".equalsIgnoreCase( nature ) ) {
                return DECLARATION;
            } else if ( "definition".equalsIgnoreCase( nature ) ) {
                return DEFINITION;
            }
            return null;
        }
    }

    private String                 typeName;
    private Role.Type              role;
    private Format                 format;
    private Kind                   kind;
    private Nature                 nature;
    private String                 timestampAttribute;
    private String                 durationAttribute;
    private ReadAccessor           durationExtractor;
    private ReadAccessor           timestampExtractor;
    private transient Class< ? >   typeClass;
    private String                 typeClassName;
    private Prototype              prototype;
    private ClassDefinition        typeClassDef;
    private Resource               resource;
    private boolean                dynamic;
    private boolean                typesafe;
    private boolean                novel;
    private boolean                valid;
    private boolean                propertyReactive;
    private boolean                javaBased;
    private transient List<String> accessibleProperties;

    private transient ObjectType   objectType;
    private long                   expirationOffset = NEVER_EXPIRES;
    private Expires.Policy         expirationPolicy;

    private int                    order;

    public TypeDeclaration() {

        role = Role.Type.FACT;
        format = Format.POJO;
        kind = Kind.CLASS;
        nature = Nature.DECLARATION;

        this.valid =  true;
    }

    private TypeDeclaration( Class< ? > typeClass ) {
        this(ClassUtils.getSimpleName(typeClass));
        setTypeClass(typeClass);
        javaBased = true;
        setTypeClassDef( new ClassDefinition( typeClass ) );

        Role role = typeClass.getAnnotation(Role.class);
        if (role != null) {
            setRole(role.value());
        }

        if (typeClass.getAnnotation(PropertyReactive.class) != null) {
            setPropertyReactive(true);
        }
    }

    public TypeDeclaration( String typeName ) {
        this.typeName = typeName;

        role = Role.Type.FACT;
        format = Format.POJO;
        kind = Kind.CLASS;
        nature = Nature.DECLARATION;

        this.durationAttribute = null;
        this.timestampAttribute = null;
        this.prototype = null;
        this.typesafe =  true;
        this.valid =  true;
    }

    public void readExternal( ObjectInput in ) throws IOException,
                                                      ClassNotFoundException {
        this.typeName = (String) in.readObject();
        this.role = (Role.Type) in.readObject();
        this.format = (Format) in.readObject();
        this.kind = (Kind) in.readObject();
        this.nature = (Nature) in.readObject();
        this.durationAttribute = (String) in.readObject();
        this.timestampAttribute = (String) in.readObject();
        this.typeClassName = (String) in.readObject();
        this.prototype = (Prototype) in.readObject();
        this.typeClassDef = (ClassDefinition) in.readObject();
        this.durationExtractor = (ReadAccessor) in.readObject();
        this.timestampExtractor = (ReadAccessor) in.readObject();
        this.resource = (Resource) in.readObject();
        this.expirationOffset = in.readLong();
        this.expirationPolicy = (Expires.Policy) in.readObject();
        this.dynamic = in.readBoolean();
        this.typesafe = in.readBoolean();
        this.propertyReactive = in.readBoolean();
        this.valid = in.readBoolean();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject( typeName );
        out.writeObject( role );
        out.writeObject( format );
        out.writeObject( kind );
        out.writeObject( nature );
        out.writeObject( durationAttribute );
        out.writeObject( timestampAttribute );
        out.writeObject( typeClassName );
        out.writeObject( prototype );
        out.writeObject( typeClassDef );
        out.writeObject( durationExtractor );
        out.writeObject( timestampExtractor );
        out.writeObject( resource );
        out.writeLong(expirationOffset);
        out.writeObject( expirationPolicy );
        out.writeBoolean(dynamic);
        out.writeBoolean( typesafe );
        out.writeBoolean(propertyReactive);
        out.writeBoolean(valid);
    }

    public int getSetMask() {
        return this.setMask;
    }

    /**
     * @return the type
     */
    public String getTypeName() {
        return typeName;
    }

    /**
     * @return the category
     */
    public Role.Type getRole() {
        return role;
    }

    /**
     * @param role the category to set
     */
    public void setRole(Role.Type role) {
        this.setMask = this.setMask | ROLE_BIT;
        this.role = role;
    }

    /**
     * @return the format
     */
    public Format getFormat() {
        return format;
    }

    /**
     * @param format the format to set
     */
    public void setFormat(Format format) {
        this.setMask = this.setMask | FORMAT_BIT;
        this.format = format;
    }

    /**
     * @return the kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * @param kind the kind to set
     */
    public void setKind(Kind kind) {
        this.setMask = this.setMask | KIND_BIT;
        this.kind = kind;
    }

    public Nature getNature() {
        return nature;
    }

    public void setNature( Nature nature ) {
        this.setMask = this.setMask | NATURE_BIT;
        this.nature = nature;
    }

    /**
     * @return the timestampAttribute
     */
    public String getTimestampAttribute() {
        return timestampAttribute;
    }

    /**
     * @param timestampAttribute the timestampAttribute to set
     */
    public void setTimestampAttribute(String timestampAttribute) {
        this.timestampAttribute = timestampAttribute;
    }

    /**
     * @return the durationAttribute
     */
    public String getDurationAttribute() {
        return durationAttribute;
    }

    /**
     * @param durationAttribute the durationAttribute to set
     */
    public void setDurationAttribute(String durationAttribute) {
        this.durationAttribute = durationAttribute;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    /**
     * @return the typeClass
     */
    public Class< ? > getTypeClass() {
        return typeClass;
    }

    /**
     * @param typeClass the typeClass to set
     */
    public void setTypeClass(Class< ? > typeClass) {
        this.typeClass = typeClass;
        if ( this.typeClassDef != null ) {
            this.typeClassDef.setDefinedClass( this.typeClass );
        }
        if ( this.typeClass != null ) {
            this.typeClassName = this.typeClass.getName();
        }
    }

    public boolean isDefinition() {
        return nature == TypeDeclaration.Nature.DEFINITION || isGeneratedFact();
    }

    public boolean isGeneratedFact() {
        return typeClass != null && GeneratedFact.class.isAssignableFrom( typeClass );
    }

    public Prototype getPrototype() {
        return prototype;
    }

    /**
     * Returns true if the given parameter matches this type declaration
     */
    public boolean matches(Object clazz) {
        return clazz instanceof Prototype ?
               this.prototype.equals( clazz ) :
               this.typeClass.isAssignableFrom( (Class< ? >) clazz );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((typeName == null) ? 0 : typeName.hashCode());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) {
            return true;
        }
        if ( obj == null ) {
            return false;
        }
        if ( getClass() != obj.getClass() ) {
            return false;
        }
        TypeDeclaration other = (TypeDeclaration) obj;
        if ( typeName == null ) {
            if ( other.typeName != null ) {
            return false;
            }
        } else if ( !typeName.equals( other.typeName ) ) {
            return false;
        }
        return true;
    }

    public ReadAccessor getDurationExtractor() {
        return durationExtractor;
    }

    public void setDurationExtractor(ReadAccessor durationExtractor) {
        this.durationExtractor = durationExtractor;
    }

    /**
     * @return the typeClassDef
     */
    public ClassDefinition getTypeClassDef() {
        return typeClassDef;
    }

    /**
     * @param typeClassDef the typeClassDef to set
     */
    public void setTypeClassDef(ClassDefinition typeClassDef) {
        this.typeClassDef = typeClassDef;
    }

    public ReadAccessor getTimestampExtractor() {
        return timestampExtractor;
    }

    public void setTimestampExtractor(ReadAccessor timestampExtractor) {
        this.timestampExtractor = timestampExtractor;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public ObjectType getObjectType() {
        if ( this.objectType == null ) {
            if ( this.getFormat() == Format.POJO ) {
                this.objectType = new ClassObjectType( this.getTypeClass() );
            } else {
                this.objectType = new PrototypeObjectType(this.getPrototype() );
            }
        }
        return this.objectType;
    }

    public long getExpirationOffset() {
        return this.expirationOffset;
    }

    public void setExpirationOffset(final long expirationOffset) {
        this.expirationOffset = expirationOffset;
    }

    public Policy getExpirationPolicy() {
        return expirationPolicy;
    }

    public void setExpirationType( Policy expirationPolicy ) {
        this.expirationPolicy = expirationPolicy;
    }

    public String getTypeClassName() {
        return typeClassName;
    }

    public void setTypeClassName(String typeClassName) {
        this.typeClassName = typeClassName;
    }

    public boolean isJavaBased() {
        return javaBased;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public void setDynamic(boolean dynamic) {
        this.dynamic = dynamic;
    }

    public boolean isTypesafe() {
        return typesafe;
    }

    public void setTypesafe(boolean typesafe) {
        this.setMask = this.setMask | TYPESAFE_BIT;
        this.typesafe = typesafe;
    }

    public boolean isPropertyReactive() {
        return propertyReactive;
    }

    public void setPropertyReactive(boolean propertyReactive) {
        this.propertyReactive = propertyReactive;
    }

    public boolean isNovel() {
        return novel;
    }

    public void setNovel(boolean novel) {
        this.novel = novel;
    }

    public List<String> getAccessibleProperties() {
        if ( accessibleProperties == null ) {
            accessibleProperties = propertyReactive ? PropertyReactivityUtil.getAccessibleProperties( getTypeClass() ) : Collections.emptyList();
        }
        return accessibleProperties;
    }

    public String toString() {
        return "TypeDeclaration{" +
                "typeName='" + typeName + '\'' +
                ", role=" + role +
                ", format=" + format +
                ", kind=" + kind +
                ", nature=" + nature +
                '}';
    }

    public KnowledgeType getKnowledgeType() {
        return KnowledgeType.TYPE;
    }

    public String getNamespace() {
        return this.typeClass != null ? this.typeClass.getPackage().getName() : "";
    }

    public String getFullName() {
        return getNamespace() + "." + getTypeName();
    }

    public String getId() {
        return getTypeName();
    }

    public int getOrder() {
        return order;
    }

    public void setOrder( int order ) {
        this.order = order;
    }

    @Override
    public int compareTo( TypeDeclaration o ) {
        return this.order - o.order;
    }

    public static TypeDeclaration createTypeDeclarationForBean(Class<?> cls) {
        return createTypeDeclarationForBean(cls, PropertySpecificOption.ALWAYS);
    }

    public static TypeDeclaration createTypeDeclarationForBean(Class<?> cls, PropertySpecificOption propertySpecificOption) {
        return createTypeDeclarationForBean(cls, new Annotated.ClassAdapter(cls), propertySpecificOption);
    }

    public static TypeDeclaration createTypeDeclarationForBean(Class<?> cls, Annotated annotated, PropertySpecificOption propertySpecificOption) {
        TypeDeclaration typeDeclaration = new TypeDeclaration(cls);
        processTypeAnnotations( typeDeclaration, annotated, propertySpecificOption );
        return typeDeclaration;
    }

    public static void processTypeAnnotations( TypeDeclaration type, Annotated annotated, PropertySpecificOption propertySpecificOption ) {
        configureExpirationOffset( type, annotated );
        configurePropertyReactivity( type, annotated, propertySpecificOption );
        configurePropertyChangeSupport( type, annotated );
    }

    private static void configureExpirationOffset( TypeDeclaration type, Annotated annotated ) {
        Expires expires = annotated.getTypedAnnotation(Expires.class);
        if (expires != null) {
            String expiration = expires.value();
            long offset = TimeIntervalParser.parseSingle(expiration);
            // @Expires( -1 ) means never expire
            type.setExpirationOffset(offset == -1L ? Long.MAX_VALUE : offset);
            type.setExpirationType( expires.policy() );
        }
    }

    private static void configurePropertyReactivity( TypeDeclaration type, Annotated annotated, PropertySpecificOption propertySpecificOption ) {
        boolean propertyReactive = propertySpecificOption.isPropSpecific( annotated.hasAnnotation( PropertyReactive.class ),
                                                                          annotated.hasAnnotation( ClassReactive.class ) );
        type.setPropertyReactive(propertyReactive);
    }

    private static void configurePropertyChangeSupport( TypeDeclaration type, Annotated annotated ) {
        // only ever switch it on: a DRL declaration may already have made the type dynamic
        if ( annotated.hasAnnotation( PropertyChangeSupport.class ) ) {
            type.setDynamic( true );
        }
    }
}
