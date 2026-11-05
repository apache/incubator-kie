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
package org.drools.compiler.builder.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.drools.base.factmodel.ClassDefinition;
import org.drools.base.factmodel.FieldDefinition;
import org.drools.base.factmodel.traits.Thing;
import org.drools.base.rule.TypeDeclaration;
import org.drools.compiler.compiler.PackageRegistry;
import org.drools.compiler.compiler.TypeDeclarationError;
import org.drools.drl.ast.descr.BaseDescr;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.util.ClassUtils;
import org.kie.api.definition.type.Position;
import org.kie.api.io.Resource;
import org.kie.api.runtime.rule.Match;
import org.kie.internal.builder.conf.PropertySpecificOption;

import static org.drools.base.util.Drools.hasMvel;
import static org.drools.compiler.builder.impl.ClassDefinitionFactory.createClassDefinition;
import static org.drools.compiler.builder.impl.TypeDeclarationConfigurator.processMvelBasedAccessors;
import static org.drools.util.bitmask.BitMaskUtil.isSet;

public class TypeDeclarationCache {

    private final TypeDeclarationContext context;
    private final BuildResultCollector results;
    private final Map<String, TypeDeclaration> cacheTypes = new HashMap<>();

    TypeDeclarationCache(TypeDeclarationContext context, BuildResultCollector results) {
        this.context = context;
        this.results = results;
        if ( hasMvel() ) {
            initBuiltinTypeDeclarations();
        }
    }

    private void initBuiltinTypeDeclarations() {
        initBuiltinTypeDeclaration( Collection.class );
        initBuiltinTypeDeclaration( Map.class );
        initBuiltinTypeDeclaration( Match.class );
        initBuiltinTypeDeclaration(Thing.class).setKind( TypeDeclaration.Kind.TRAIT );
    }

    private TypeDeclaration initBuiltinTypeDeclaration(Class<?> cls) {
        TypeDeclaration type = new TypeDeclaration( cls.getSimpleName() );
        type.setTypesafe( false );
        type.setTypeClass( cls );
        setClassDefinitionOnTypeDeclaration( cls, type );
        cacheTypes.put( cls.getCanonicalName(), type );
        return type;
    }


    public TypeDeclaration getAndRegisterTypeDeclaration( Class<?> cls, String packageName ) {
        if (cls.isPrimitive() || cls.isArray()) {
            return null;
        }
        TypeDeclaration typeDeclaration = getCachedTypeDeclaration(cls);
        if (typeDeclaration != null) {
            registerTypeDeclaration(packageName, typeDeclaration);
            return typeDeclaration;
        }
        typeDeclaration = getExistingTypeDeclaration(cls);
        if (typeDeclaration != null) {
            initTypeDeclaration(cls, typeDeclaration);
            return typeDeclaration;
        }

        typeDeclaration = createTypeDeclarationForBean(cls);
        initTypeDeclaration( cls, typeDeclaration );
        registerTypeDeclaration( packageName, typeDeclaration );
        return typeDeclaration;
    }

    TypeDeclaration getTypeDeclaration( Class<?> cls ) {
        if (cls.isPrimitive() || cls.isArray()) {
            return null;
        }

        // If this class has already been accessed, it'll be in the cache
        TypeDeclaration tdecl = getCachedTypeDeclaration(cls);
        return tdecl != null ? tdecl : createTypeDeclaration(cls);
    }


    private void registerTypeDeclaration( String packageName,
                                          TypeDeclaration typeDeclaration ) {
        if (typeDeclaration.getNature() == TypeDeclaration.Nature.DECLARATION || packageName.equals( typeDeclaration.getTypeClass().getPackage().getName() )) {
            PackageRegistry packageRegistry = context.getOrCreatePackageRegistry(new PackageDescr(packageName, ""));
            packageRegistry.getPackage().addTypeDeclaration(typeDeclaration);
        }
    }


    private TypeDeclaration createTypeDeclaration(Class<?> cls) {
        TypeDeclaration typeDeclaration = getExistingTypeDeclaration(cls);

        if (typeDeclaration == null) {
            typeDeclaration = createTypeDeclarationForBean(cls);
        }

        initTypeDeclaration(cls, typeDeclaration);
        return typeDeclaration;
    }

    public TypeDeclaration getCachedTypeDeclaration(Class<?> cls) {
        return getCachedTypeDeclaration( cls.getName() );
    }

    public TypeDeclaration getCachedTypeDeclaration(String className) {
        return cacheTypes.get(className);
    }

    private TypeDeclaration getExistingTypeDeclaration(Class<?> cls) {
        TypeDeclaration typeDeclaration = null;
        PackageRegistry pkgReg = context.getPackageRegistry( ClassUtils.getPackage( cls ) );
        if (pkgReg != null) {
            String className = cls.getName();
            String typeName = className.substring(className.lastIndexOf( "." ) + 1 );
            typeDeclaration = pkgReg.getPackage().getTypeDeclaration(typeName);
        }
        return typeDeclaration;
    }

    private void initTypeDeclaration(Class<?> cls,
                                     TypeDeclaration typeDeclaration) {
        ClassDefinition clsDef = typeDeclaration.getTypeClassDef();
        if (clsDef == null) {
            clsDef = setClassDefinitionOnTypeDeclaration( cls, typeDeclaration );
        } else {
            processFieldsPosition( cls, clsDef, typeDeclaration );
        }

        if (typeDeclaration.isPropertyReactive()) {
            TypeDeclarationUtils.processModifiedProps(cls, clsDef);
        }


        // build up a set of all the super classes and interfaces
        Set<TypeDeclaration> tdecls = new LinkedHashSet<>();

        tdecls.add(typeDeclaration);
        buildTypeDeclarations(cls,
                              tdecls);

        // Iterate and for each typedeclr assign it's value if it's not already set
        // We start from the rear as those are the furthest away classes and interfaces
        TypeDeclaration[] tarray = tdecls.toArray(new TypeDeclaration[tdecls.size()]);
        for (int i = tarray.length - 1; i >= 0; i--) {
            TypeDeclaration currentTDecl = tarray[i];
            if (!isSet(typeDeclaration.getSetMask(),
                       TypeDeclaration.ROLE_BIT) && isSet(currentTDecl.getSetMask(),
                                                          TypeDeclaration.ROLE_BIT)) {
                typeDeclaration.setRole(currentTDecl.getRole());
            }
            if (!isSet(typeDeclaration.getSetMask(),
                       TypeDeclaration.FORMAT_BIT) && isSet(currentTDecl.getSetMask(),
                                                            TypeDeclaration.FORMAT_BIT)) {
                typeDeclaration.setFormat(currentTDecl.getFormat());
            }
            if (!isSet(typeDeclaration.getSetMask(),
                       TypeDeclaration.TYPESAFE_BIT) && isSet(currentTDecl.getSetMask(),
                                                              TypeDeclaration.TYPESAFE_BIT)) {
                typeDeclaration.setTypesafe(currentTDecl.isTypesafe());
            }
        }

        this.cacheTypes.put(cls.getName(),
                            typeDeclaration);
    }

    private ClassDefinition setClassDefinitionOnTypeDeclaration( Class<?> cls, TypeDeclaration typeDeclaration ) {
        ClassDefinition clsDef = createClassDefinition( cls, typeDeclaration.getResource() );
        typeDeclaration.setTypeClassDef(clsDef);
        return clsDef;
    }

    private void processFieldsPosition( Class<?> cls,
                                        ClassDefinition clsDef,
                                        TypeDeclaration typeDeclaration ) {
        // it's a new type declaration, so generate the @Position for it
        Collection<Field> fields = new ArrayList<>();
        Class<?> tempKlass = cls;
        while (tempKlass != null && tempKlass != Object.class) {
            Collections.addAll( fields, tempKlass.getDeclaredFields() );
            tempKlass = tempKlass.getSuperclass();
        }

        FieldDefinition[] orderedFields = new FieldDefinition[ fields.size() ];

        for (Field fld : fields) {
            Position pos = fld.getAnnotation(Position.class);
            if (pos != null) {
                if (pos.value() < 0 || pos.value() >= fields.size()) {
                    results.addBuilderResult(new TypeDeclarationError(typeDeclaration,
                                                                       "Out of range position " + pos.value() + " for field '" + fld.getName() + "' on class " + cls.getName()));
                    continue;
                }
                if (orderedFields[pos.value()] != null) {
                    results.addBuilderResult(new TypeDeclarationError(typeDeclaration,
                                                                       "Duplicated position " + pos.value() + " for field '" + fld.getName() + "' on class " + cls.getName()));
                    continue;
                }
                FieldDefinition fldDef = clsDef.getField(fld.getName());
                if (fldDef == null) {
                    fldDef = new FieldDefinition(fld.getName(), fld.getType().getName());
                }
                fldDef.setIndex(pos.value());
                orderedFields[ pos.value() ] = fldDef;
            }
        }
        for (FieldDefinition fld : orderedFields) {
            if (fld != null) {
                // it's null if there is no @Position
                clsDef.addField(fld);
            }
        }
    }

    private TypeDeclaration createTypeDeclarationForBean(Class<?> cls) {
        TypeDeclaration typeDeclaration = TypeDeclaration.createTypeDeclarationForBean(cls, context.getBuilderConfiguration().getOption(PropertySpecificOption.KEY));

        String namespace = ClassUtils.getPackage( cls );
        PackageRegistry pkgRegistry = context.getOrCreatePackageRegistry( new PackageDescr(namespace) );

        processMvelBasedAccessors(context, results, pkgRegistry, new BaseDescr(), typeDeclaration );
        return typeDeclaration;
    }

    private void buildTypeDeclarations(Class<?> cls,
                                       Set<TypeDeclaration> tdecls) {
        // Process current interfaces
        Class<?>[] intfs = cls.getInterfaces();
        for (Class<?> intf : intfs) {
            buildTypeDeclarationInterfaces(intf,
                                           tdecls);
        }

        // Process super classes and their interfaces
        cls = cls.getSuperclass();
        while (cls != null && cls != Object.class) {
            if (!buildTypeDeclarationInterfaces(cls,
                                                tdecls)) {
                break;
            }
            cls = cls.getSuperclass();
        }

    }

    private boolean buildTypeDeclarationInterfaces(Class cls,
                                                   Set<TypeDeclaration> tdecls) {
        PackageRegistry pkgReg;

        TypeDeclaration tdecl = this.cacheTypes.get((cls.getName()));
        if (tdecl == null) {
            pkgReg = context.getPackageRegistry(ClassUtils.getPackage(cls));
            if (pkgReg != null) {
                tdecl = pkgReg.getPackage().getTypeDeclaration(cls.getSimpleName());
            }
        }
        if (tdecl != null) {
            if (!tdecls.add(tdecl)) {
                return false; // the interface already exists, return to stop recursion
            }
        }

        Class<?>[] intfs = cls.getInterfaces();
        for (Class<?> intf : intfs) {
            pkgReg = context.getPackageRegistry(ClassUtils.getPackage(intf));
            if (pkgReg != null) {
                tdecl = pkgReg.getPackage().getTypeDeclaration(intf.getSimpleName());
            }
            if (tdecl != null) {
                tdecls.add(tdecl);
            }
        }

        for (Class<?> intf : intfs) {
            if (!buildTypeDeclarationInterfaces(intf,
                                                tdecls)) {
                return false;
            }
        }

        return true;

    }


    Collection<String> removeTypesGeneratedFromResource(Resource resource) {
        List<String> typesToBeRemoved = new ArrayList<>();
        for (Map.Entry<String, TypeDeclaration> type : cacheTypes.entrySet()) {
            if (resource.equals(type.getValue().getResource())) {
                typesToBeRemoved.add(type.getKey());
            }
        }
        for (String type : typesToBeRemoved) {
            cacheTypes.remove(type);
        }
        return typesToBeRemoved;
    }
}
