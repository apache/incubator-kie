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
package org.drools.mvel.asm;

import org.drools.base.base.BaseClassFieldReader;
import org.drools.base.base.ClassFieldInspector;
import org.drools.base.base.ValueType;
import org.drools.core.base.*;
import org.drools.core.base.ClassFieldAccessorCache.CacheEntry;
import org.drools.base.base.extractors.BaseObjectClassFieldReader;
import org.drools.base.base.extractors.SelfReferenceClassFieldReader;
import org.drools.base.rule.accessor.GlobalResolver;
import org.drools.base.util.Drools;
import org.drools.mvel.accessors.*;
import org.drools.wiring.api.util.ByteArrayClassLoader;
import org.mvel2.asm.*;

import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.Map;

import static org.drools.mvel.accessors.ClassFieldAccessorStore.getClassFieldInspector;
import static org.drools.mvel.asm.ClassGenerator.createClassWriter;

/**
 * This generates subclasses of BaseClassFieldExtractor to provide field extractors.
 * This should not be used directly, but via ClassFieldExtractor (which ensures that it is 
 * all nicely serializable).
 */

public class ClassFieldAccessorFactory implements FieldAccessorFactory {

    private static final String                        BASE_PACKAGE         = "org/drools/base";

    private static final String                        SELF_REFERENCE_FIELD = "this";

    private static final ProtectionDomain              PROTECTION_DOMAIN;

    static {
        PROTECTION_DOMAIN = Drools.isNativeImage() ? null : AccessController.doPrivileged((PrivilegedAction<ProtectionDomain>) ClassFieldAccessorFactory.class::getProtectionDomain);
    }

    @Override
    public BaseClassFieldReader getClassFieldReader(Class< ? > clazz, String fieldName, CacheEntry cache) {
        try {
            // if it is a self reference
            if ( SELF_REFERENCE_FIELD.equals( fieldName ) ) {
                // then just create an instance of the special class field extractor
                return new SelfReferenceClassFieldReader( clazz );
            } else {
                // otherwise, bytecode generate a specific extractor
                ClassFieldInspector inspector = getClassFieldInspector( clazz, cache );
                Method getterMethod = inspector.getGetterMethods().get( fieldName );
                Integer index = inspector.getFieldNames().get( fieldName );

                Class< ? > fieldType = inspector.getFieldType( fieldName );
                if ( fieldType == null && fieldName.length() > 1 && Character.isLowerCase( fieldName.charAt( 0 ) ) && Character.isUpperCase( fieldName.charAt(1) ) ) {
                    // it might be that odd case of javabeans naming conventions that does not use lower case first letters if the second is uppercase
                    String altFieldName = Character.toUpperCase( fieldName.charAt( 0 ) ) + fieldName.substring( 1 );
                    fieldType = inspector.getFieldType( altFieldName );
                    if( fieldType != null ) {
                        // it seems it is the corner case indeed.
                        getterMethod = inspector.getGetterMethods().get( altFieldName );
                        index = inspector.getFieldNames().get( altFieldName );
                    }
                }
                if ( fieldType != null && getterMethod != null ) {
                    final String className = ClassFieldAccessorFactory.BASE_PACKAGE + "/" + Type.getInternalName( clazz ) + Math.abs( (long) System.identityHashCode( clazz ) ) + "$" + getterMethod.getName();

                    // generating byte array to create target class
                    final byte[] bytes = dumpReader( clazz,
                                                     className,
                                                     getterMethod,
                                                     fieldType );
                    // use bytes to get a class 
                    ByteArrayClassLoader byteArrayClassLoader = cache.getByteArrayClassLoader();
                    final Class< ? > newClass = byteArrayClassLoader.defineClass( className.replace( '/',
                                                                                                     '.' ),
                                                                                  bytes,
                                                                                  PROTECTION_DOMAIN );
                    // instantiating target class
                    final ValueType valueType = ValueType.determineValueType( fieldType );
                    final Object[] params = {index, fieldType, valueType};
                    return (BaseClassFieldReader) newClass.getConstructors()[0].newInstance( params );
                } else {
                    // must be a public field
                    return null;
                }
            }
        } catch ( final RuntimeException e ) {
            throw e;
        } catch ( final Exception e ) {
            throw new RuntimeException( e );
        }
    }

    @Override
    public BaseClassFieldWriter getClassFieldWriter( Class< ? > clazz, String fieldName, CacheEntry cache) {
        ByteArrayClassLoader byteArrayClassLoader = cache.getByteArrayClassLoader();
        Map<Class< ? >, ClassFieldInspector> inspectors = cache.getInspectors();
        
        try {
            // otherwise, bytecode generate a specific extractor
            ClassFieldInspector inspector = inspectors.get( clazz );
            if ( inspector == null ) {
                inspector = new ClassFieldInspectorImpl( clazz );
                inspectors.put( clazz,
                                inspector );
            }
            Method setterMethod = inspector.getSetterMethods().get( fieldName );
            Integer index = inspector.getFieldNames().get( fieldName );
            if ( setterMethod == null && fieldName.length() > 1 && Character.isLowerCase( fieldName.charAt( 0 ) ) && Character.isUpperCase( fieldName.charAt(1) ) ) {
                // it might be that odd case of javabeans naming conventions that does not use lower case first letters if the second is uppercase
                String altFieldName = Character.toUpperCase( fieldName.charAt( 0 ) ) + fieldName.substring( 1 );
                setterMethod = inspector.getSetterMethods().get( altFieldName );
                index = inspector.getFieldNames().get( altFieldName );
            }
            if ( setterMethod != null ) {
                final Class< ? > fieldType = setterMethod.getParameterTypes()[0];
                final String className = ClassFieldAccessorFactory.BASE_PACKAGE + "/" + Type.getInternalName( clazz ) + Math.abs( (long) System.identityHashCode( clazz ) ) + "$" + setterMethod.getName();

                // generating byte array to create target class
                final byte[] bytes = dumpWriter( clazz,
                                                 className,
                                                 setterMethod,
                                                 fieldType );
                // use bytes to get a class 

                final Class< ? > newClass = byteArrayClassLoader.defineClass( className.replace( '/',
                                                                                                 '.' ),
                                                                              bytes,
                                                                              PROTECTION_DOMAIN );
                // instantiating target class
                final ValueType valueType = ValueType.determineValueType( fieldType );
                final Object[] params = {index, fieldType, valueType};
                return (BaseClassFieldWriter) newClass.getConstructors()[0].newInstance( params );
            } else {
                if ( inspector.getFieldNames().containsKey( fieldName ) ) {
                    return null;
                } else {
                    throw new RuntimeException( "Field/method '" + fieldName + "' not found for class '" + clazz.getName() + "'" );
                }
            }
        } catch ( final RuntimeException e ) {
            throw e;
        } catch ( final Exception e ) {
            throw new RuntimeException( e );
        }
    }

    private static byte[] dumpReader(final Class< ? > originalClass,
                                     final String className,
                                     final Method getterMethod,
                                     final Class< ? > fieldType) throws Exception {

        final Class< ? > superClass = getReaderSuperClassFor( fieldType );
        final ClassWriter cw = buildClassHeader( superClass, className );

        build3ArgConstructor( superClass,
                              className,
                              cw );

        buildGetMethod( originalClass,
                        className,
                        superClass,
                        getterMethod,
                        cw );

        cw.visitEnd();

        return cw.toByteArray();
    }

    private static byte[] dumpWriter(final Class< ? > originalClass,
                                     final String className,
                                     final Method getterMethod,
                                     final Class< ? > fieldType) throws Exception {


        final Class< ? > superClass = getWriterSuperClassFor( fieldType );
        final ClassWriter cw = buildClassHeader( superClass, className );

        build3ArgConstructor( superClass,
                              className,
                              cw );

        buildSetMethod( originalClass,
                        className,
                        superClass,
                        getterMethod,
                        fieldType,
                        cw );

        cw.visitEnd();

        return cw.toByteArray();
    }

    /**
     * Builds the class header
     */
    protected static ClassWriter buildClassHeader(Class< ? > superClass, String className) {

        ClassWriter cw = createClassWriter( superClass.getClassLoader(),
                                            Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
                                            className,
                                            null,
                                            Type.getInternalName( superClass ),
                                            null );

        cw.visitSource( null,
                        null );

        return cw;
    }

    /**
     * Creates a constructor for the field extractor receiving
     * the index, field type and value type
     */
    private static void build3ArgConstructor(final Class< ? > superClazz,
                                             final String className,
                                             final ClassWriter cw) {
        MethodVisitor mv;

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC,
                            "<init>",
                            Type.getMethodDescriptor(Type.VOID_TYPE,
                                                     Type.getType(int.class), Type.getType(Class.class), Type.getType(ValueType.class)),
                            null,
                            null);
        mv.visitCode();
        final Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitVarInsn(Opcodes.ALOAD,
                        0);
        mv.visitVarInsn(Opcodes.ILOAD,
                        1);
        mv.visitVarInsn(Opcodes.ALOAD,
                        2);
        mv.visitVarInsn(Opcodes.ALOAD,
                        3);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                           Type.getInternalName(superClazz),
                           "<init>",
                           Type.getMethodDescriptor(Type.VOID_TYPE,
                                                    Type.getType(int.class), Type.getType(Class.class), Type.getType(ValueType.class)));
        final Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitInsn(Opcodes.RETURN);
        final Label l2 = new Label();
        mv.visitLabel(l2);
        mv.visitLocalVariable("this",
                              "L" + className + ";",
                              null,
                              l0,
                              l2,
                              0);
        mv.visitLocalVariable("index",
                              Type.getDescriptor(int.class),
                              null,
                              l0,
                              l2,
                              1);
        mv.visitLocalVariable("fieldType",
                              Type.getDescriptor(Class.class),
                              null,
                              l0,
                              l2,
                              2);
        mv.visitLocalVariable("valueType",
                              Type.getDescriptor(ValueType.class),
                              null,
                              l0,
                              l2,
                              3);
        mv.visitMaxs(0,
                     0);
        mv.visitEnd();

    }

    /**
     * Creates the proxy reader method for the given method
     */
    protected static void buildGetMethod(final Class< ? > originalClass,
                                         final String className,
                                         final Class< ? > superClass,
                                         final Method getterMethod,
                                         final ClassWriter cw) {

        final Class< ? > fieldType = getterMethod.getReturnType();
        Method overridingMethod;
        try {
            overridingMethod = superClass.getMethod( getOverridingGetMethodName( fieldType ), GlobalResolver.class, Object.class );
        } catch ( final Exception e ) {
            throw new RuntimeException( "This is a bug. Please report back to JBoss Rules team.",
                                        e );
        }
        final MethodVisitor mv = cw.visitMethod( Opcodes.ACC_PUBLIC,
                                                 overridingMethod.getName(),
                                                 Type.getMethodDescriptor( overridingMethod ),
                                                 null,
                                                 null );

        mv.visitCode();

        final Label l0 = new Label();
        mv.visitLabel( l0 );
        mv.visitVarInsn( Opcodes.ALOAD,
                         2 );
        mv.visitTypeInsn( Opcodes.CHECKCAST,
                          Type.getInternalName( originalClass ) );

        if ( originalClass.isInterface() ) {
            mv.visitMethodInsn( Opcodes.INVOKEINTERFACE,
                                Type.getInternalName( originalClass ),
                                getterMethod.getName(),
                                Type.getMethodDescriptor( getterMethod ),
                                true);
        } else {
            mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL,
                                Type.getInternalName( originalClass ),
                                getterMethod.getName(),
                                Type.getMethodDescriptor( getterMethod ),
                                false);
        }
        if (getterMethod.getReturnType() != overridingMethod.getReturnType()) {
            if (getterMethod.getReturnType() == float.class && overridingMethod.getReturnType() == double.class) {
                mv.visitInsn(Opcodes.F2D); // Convert float to double
                // Clean up precision artifacts from float-to-double conversion
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                    "org/drools/util/FloatHelper", 
                    "cleanDouble", 
                    "(D)D", 
                    false);
            } else if (getterMethod.getReturnType() == double.class && overridingMethod.getReturnType() == float.class) {
                mv.visitInsn(Opcodes.D2F); // Convert double to float
            } else if (getterMethod.getReturnType() == byte.class && overridingMethod.getReturnType() == long.class) {
                mv.visitInsn(Opcodes.I2L); // Convert byte to long (byte->int->long handled by JVM)
            } else if (getterMethod.getReturnType() == short.class && overridingMethod.getReturnType() == long.class) {
                mv.visitInsn(Opcodes.I2L); // Convert short to long (short->int->long handled by JVM)
            } else if (getterMethod.getReturnType() == int.class && overridingMethod.getReturnType() == long.class) {
                mv.visitInsn(Opcodes.I2L); // Convert int to long
            } else if (getterMethod.getReturnType() == char.class && overridingMethod.getReturnType() == long.class) {
                mv.visitInsn(Opcodes.I2L); // Convert char to long (char->int->long handled by JVM)
            }
        }
        mv.visitInsn( Type.getType( overridingMethod.getReturnType() ).getOpcode( Opcodes.IRETURN ) );
        final Label l1 = new Label();
        mv.visitLabel( l1 );
        mv.visitLocalVariable( "this",
                               "L" + className + ";",
                               null,
                               l0,
                               l1,
                               0 );
        mv.visitLocalVariable( "workingMemory",
                               Type.getDescriptor( GlobalResolver.class ),
                               null,
                               l0,
                               l1,
                               1 );
        mv.visitLocalVariable( "object",
                               Type.getDescriptor( Object.class ),
                               null,
                               l0,
                               l1,
                               2 );
        mv.visitMaxs( 0,
                      0 );
        mv.visitEnd();
    }

    /**
     * Creates the set method for the given field definition
     */
    protected static void buildSetMethod(final Class< ? > originalClass,
                                         final String className,
                                         final Class< ? > superClass,
                                         final Method setterMethod,
                                         final Class< ? > fieldType,
                                         final ClassWriter cw) {
        MethodVisitor mv;
        // set method
        Method overridingMethod;
        try {
            Class<?> parameterType = getParameterType(fieldType);
            overridingMethod = superClass.getMethod( getOverridingSetMethodName( fieldType ),
                                                     Object.class, parameterType );
        } catch ( final Exception e ) {
            throw new RuntimeException( "This is a bug. Please report back to JBoss Rules team.",
                                        e );
        }

        mv = cw.visitMethod( Opcodes.ACC_PUBLIC,
                             overridingMethod.getName(),
                             Type.getMethodDescriptor( overridingMethod ),
                             null,
                             null );

        mv.visitCode();
        final Label l0 = new Label();
        mv.visitLabel( l0 );

        mv.visitVarInsn( Opcodes.ALOAD,
                         1 );
        mv.visitTypeInsn( Opcodes.CHECKCAST,
                          Type.getInternalName( originalClass ) );

        Class<?> parameterType = getParameterType(fieldType);
        mv.visitVarInsn( Type.getType( parameterType ).getOpcode( Opcodes.ILOAD),
                         2 );

        // If field is float but we're implementing setDecimalValue, convert double back to float for the bean setter
        if ( fieldType == float.class && getOverridingSetMethodName( fieldType ).equals("setDecimalValue") ) {
            mv.visitInsn(Opcodes.D2F); // Convert double parameter to float for the bean setter
        }
        // If field is byte/short/int/char but we're implementing setWholeNumberValue, convert long back to appropriate type for the bean setter
        if ( fieldType == byte.class && getOverridingSetMethodName( fieldType ).equals("setWholeNumberValue") ) {
            mv.visitInsn(Opcodes.L2I); // Convert long parameter to int first
            mv.visitInsn(Opcodes.I2B); // Convert int to byte for the bean setter
        }
        if ( fieldType == short.class && getOverridingSetMethodName( fieldType ).equals("setWholeNumberValue") ) {
            mv.visitInsn(Opcodes.L2I); // Convert long parameter to int first
            mv.visitInsn(Opcodes.I2S); // Convert int to short for the bean setter
        }
        if ( fieldType == int.class && getOverridingSetMethodName( fieldType ).equals("setWholeNumberValue") ) {
            mv.visitInsn(Opcodes.L2I); // Convert long parameter to int for the bean setter
        }
        if ( fieldType == char.class && getOverridingSetMethodName( fieldType ).equals("setWholeNumberValue") ) {
            mv.visitInsn(Opcodes.L2I); // Convert long parameter to int for the bean setter
        }

        if ( !fieldType.isPrimitive() ) {
            mv.visitTypeInsn( Opcodes.CHECKCAST,
                              Type.getInternalName( fieldType ) );
        }

        if ( originalClass.isInterface() ) {
            mv.visitMethodInsn( Opcodes.INVOKEINTERFACE,
                                Type.getInternalName( originalClass ),
                                setterMethod.getName(),
                                Type.getMethodDescriptor( setterMethod ),
                                true);
        } else {
            mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL,
                                Type.getInternalName( originalClass ),
                                setterMethod.getName(),
                                Type.getMethodDescriptor( setterMethod ),
                                false);
        }

        mv.visitInsn( Opcodes.RETURN );

        final Label l1 = new Label();
        mv.visitLabel( l1 );
        mv.visitLocalVariable( "this",
                               "L" + className + ";",
                               null,
                               l0,
                               l1,
                               0 );
        mv.visitLocalVariable( "bean",
                               Type.getDescriptor( Object.class ),
                               null,
                               l0,
                               l1,
                               1 );
        Class<?> localVarType = getLocalVarType(fieldType);
        mv.visitLocalVariable( "value",
                               Type.getDescriptor( localVarType ),
                               null,
                               l0,
                               l1,
                               2 );
        mv.visitMaxs( 0,
                      0 );
        mv.visitEnd();
    }

    private static Class<?> getLocalVarType(Class<?> fieldType) {
        if ((fieldType == byte.class || fieldType == short.class || fieldType == int.class || fieldType == char.class) && getOverridingSetMethodName(fieldType).equals("setWholeNumberValue")) {
            return long.class;
        } else if (fieldType == float.class && getOverridingSetMethodName(fieldType).equals("setDecimalValue")) {
            return double.class;
        } else {
            return fieldType;
        }
    }

    private static Class<?> getParameterType(Class<?> fieldType) {
        if (fieldType.isPrimitive()) {
            if (fieldType == float.class) {
                return double.class;
            } else if (fieldType == byte.class || fieldType == short.class || fieldType == int.class || fieldType == char.class) {
                return long.class;
            } else return fieldType;
        } else {
            return Object.class;
        }
    }

    private static String getOverridingGetMethodName(final Class< ? > fieldType) {
        String ret = null;
        if ( fieldType.isPrimitive() ) {
            if ( fieldType == char.class || fieldType == byte.class || fieldType == short.class || fieldType == int.class || fieldType == long.class ) {
                ret = "getWholeNumberValue";
            } else if ( fieldType == float.class || fieldType == double.class ) {
                ret = "getDecimalValue";
            } else if ( fieldType == boolean.class ) {
                ret = "getBooleanValue";
            }
        } else {
            ret = "getValue";
        }
        return ret;
    }

    private static String getOverridingSetMethodName(final Class< ? > fieldType) {
        String ret = null;
        if ( fieldType.isPrimitive() ) {
            if ( fieldType == char.class || fieldType == byte.class || fieldType == short.class || fieldType == int.class || fieldType == long.class ) {
                ret = "setWholeNumberValue";
            } else if ( fieldType == double.class ||  fieldType == float.class ) {
                ret = "setDecimalValue";
            } else if ( fieldType == boolean.class ) {
                ret = "setBooleanValue";
            }
        } else {
            ret = "setValue";
        }
        return ret;
    }

    /**
     * Returns the appropriate Base class field extractor class
     * for the given fieldType
     */
    private static Class< ? > getReaderSuperClassFor(final Class< ? > fieldType) {
        Class< ? > ret = null;
        if ( fieldType.isPrimitive() ) {
            if ( fieldType == byte.class || fieldType == short.class || fieldType == int.class || fieldType == long.class || fieldType == char.class ) {
                ret = BaseWholeNumberClassFieldReader.class;
            } else if ( fieldType == float.class ||fieldType == double.class ) {
                ret = BaseDecimalClassFieldReader.class;
            } else if ( fieldType == boolean.class ) {
                ret = BaseBooleanClassFieldReader.class;
            }
        } else {
            ret = BaseObjectClassFieldReader.class;
        }
        return ret;
    }

    /**
     * Returns the appropriate Base class field extractor class
     * for the given fieldType
     */
    private static Class< ? > getWriterSuperClassFor(final Class< ? > fieldType) {
        Class< ? > ret = null;
        if ( fieldType.isPrimitive() ) {
            if ( fieldType == byte.class || fieldType == short.class || fieldType == int.class || fieldType == long.class || fieldType == char.class ) {
                ret = BaseWholeNumberClassFieldWriter.class;
            } else if ( fieldType == double.class || fieldType == float.class ) {
                ret = BaseDecimalClassFieldWriter.class;
            } else if ( fieldType == boolean.class ) {
                ret = BaseBooleanClassFieldWriter.class;
            }
        } else {
            ret = BaseObjectClassFieldWriter.class;
        }
        return ret;
    }
}
