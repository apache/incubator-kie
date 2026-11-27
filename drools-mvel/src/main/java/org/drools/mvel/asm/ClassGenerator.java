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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.drools.util.TypeResolver;
import org.kie.memorycompiler.JavaCompiler;
import org.kie.memorycompiler.WritableClassLoader;
import org.mvel2.asm.ClassWriter;
import org.mvel2.asm.MethodVisitor;
import org.mvel2.asm.Type;

import static java.lang.reflect.Modifier.isAbstract;
import static org.drools.wiring.api.util.ClassUtils.convertFromPrimitiveType;
import static org.drools.wiring.api.util.ClassUtils.convertPrimitiveNameToType;
import static org.drools.wiring.api.util.ClassUtils.convertToPrimitiveType;
import static org.mvel2.asm.Opcodes.AASTORE;
import static org.mvel2.asm.Opcodes.ACC_PUBLIC;
import static org.mvel2.asm.Opcodes.ACC_STATIC;
import static org.mvel2.asm.Opcodes.ACC_SUPER;
import static org.mvel2.asm.Opcodes.ACONST_NULL;
import static org.mvel2.asm.Opcodes.ALOAD;
import static org.mvel2.asm.Opcodes.ANEWARRAY;
import static org.mvel2.asm.Opcodes.ARETURN;
import static org.mvel2.asm.Opcodes.CHECKCAST;
import static org.mvel2.asm.Opcodes.D2F;
import static org.mvel2.asm.Opcodes.D2I;
import static org.mvel2.asm.Opcodes.D2L;
import static org.mvel2.asm.Opcodes.DUP;
import static org.mvel2.asm.Opcodes.F2D;
import static org.mvel2.asm.Opcodes.F2I;
import static org.mvel2.asm.Opcodes.F2L;
import static org.mvel2.asm.Opcodes.GETFIELD;
import static org.mvel2.asm.Opcodes.GETSTATIC;
import static org.mvel2.asm.Opcodes.I2B;
import static org.mvel2.asm.Opcodes.I2C;
import static org.mvel2.asm.Opcodes.I2D;
import static org.mvel2.asm.Opcodes.I2F;
import static org.mvel2.asm.Opcodes.I2L;
import static org.mvel2.asm.Opcodes.I2S;
import static org.mvel2.asm.Opcodes.ILOAD;
import static org.mvel2.asm.Opcodes.INSTANCEOF;
import static org.mvel2.asm.Opcodes.INVOKEINTERFACE;
import static org.mvel2.asm.Opcodes.INVOKESPECIAL;
import static org.mvel2.asm.Opcodes.INVOKESTATIC;
import static org.mvel2.asm.Opcodes.INVOKEVIRTUAL;
import static org.mvel2.asm.Opcodes.ISTORE;
import static org.mvel2.asm.Opcodes.L2D;
import static org.mvel2.asm.Opcodes.L2F;
import static org.mvel2.asm.Opcodes.L2I;
import static org.mvel2.asm.Opcodes.NEW;
import static org.mvel2.asm.Opcodes.NEWARRAY;
import static org.mvel2.asm.Opcodes.PUTFIELD;
import static org.mvel2.asm.Opcodes.PUTSTATIC;
import static org.mvel2.asm.Opcodes.RETURN;
import static org.mvel2.asm.Opcodes.T_BOOLEAN;
import static org.mvel2.asm.Opcodes.T_BYTE;
import static org.mvel2.asm.Opcodes.T_CHAR;
import static org.mvel2.asm.Opcodes.T_DOUBLE;
import static org.mvel2.asm.Opcodes.T_FLOAT;
import static org.mvel2.asm.Opcodes.T_INT;
import static org.mvel2.asm.Opcodes.T_LONG;
import static org.mvel2.asm.Opcodes.T_SHORT;

public class ClassGenerator {

    private final String className;
    private final TypeResolver typeResolver;
    private final WritableClassLoader writableClassLoader;

    private int access = ACC_PUBLIC + ACC_SUPER;
    private String signature;
    private Class superClass = Object.class;
    private Class<?>[] interfaces;

    private final String classDescriptor;
    private String superDescriptor;

    private List<ClassPartDescr> classParts = new ArrayList<>();
    private StaticInitializerDescr staticInitializer = null;

    private byte[] bytecode;
    private Class<?> clazz;

    public ClassGenerator(String className, ClassLoader classLoader) {
        this(className, classLoader, null);
    }

    public ClassGenerator(String className, ClassLoader classLoader, TypeResolver typeResolver) {
        this.className = className;
        this.classDescriptor = className.replace('.', '/');
        this.writableClassLoader = WritableClassLoader.asWritableClassLoader( classLoader );
        this.typeResolver = typeResolver == null ? new InternalTypeResolver(classLoader) : typeResolver;
    }

    private interface ClassPartDescr {

        void write(ClassGenerator cg, ClassWriter cw);
    }

    public byte[] generateBytecode() {
        if (bytecode == null) {
            ClassWriter cw = createClassWriter(writableClassLoader.asClassLoader(), access, getClassDescriptor(), signature, getSuperClassDescriptor(), toInteralNames(interfaces));
            for (int i = 0; i < classParts.size(); i++) { // don't use iterator to allow method visits to add more class fields and methods
                classParts.get(i).write(this, cw);
            }
            if (staticInitializer != null) {
                staticInitializer.write(this, cw);
            }
            cw.visitEnd();
            bytecode = cw.toByteArray();
            if (JavaCompiler.DUMP_GENERATED_CLASSES) {
                dumpGeneratedClass(bytecode);
            }
        }
        return bytecode;
    }

    private Class<?> generateClass() {
        if (clazz == null) {
            byte[] bytecode = generateBytecode();
            clazz = writableClassLoader.writeClass( className, bytecode );
        }
        return clazz;
    }

    private void dumpGeneratedClass(byte[] bytecode) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(className + ".class");
            fos.write(bytecode);
            fos.flush();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public <T> T newInstance() {
        try {
            return (T) generateClass().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T newInstance(Class paramType, Object param) {
        try {
            return (T) generateClass().getConstructor(paramType).newInstance(param);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T newInstance(Class paramType1, Object param1, Class paramType2, Object param2) {
        try {
            return (T) generateClass().getConstructor(paramType1, paramType2).newInstance(param1, param2);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Accessors

    public String getClassDescriptor() {
        return classDescriptor;
    }

    public String getSuperClassDescriptor() {
        if (superDescriptor == null) superDescriptor = toInteralName(superClass);
        return superDescriptor;
    }

    public ClassGenerator setAccess(int access) {
        this.access = access;
        return this;
    }

    public ClassGenerator setSignature(String signature) {
        this.signature = signature;
        return this;
    }

    public ClassGenerator setSuperClass(Class superClass) {
        this.superClass = superClass;
        return this;
    }

    public ClassGenerator setInterfaces(Class<?>... interfaces) {
        this.interfaces = interfaces;
        return this;
    }

    // Utility

    private Map<Class<?>, String> descriptorsCache = new HashMap<>();

    private String descriptorOf(Class<?> type) {
        String descriptor = descriptorsCache.get(type);
        if (descriptor == null) {
            descriptor = Type.getDescriptor(type);
            descriptorsCache.put(type, descriptor);
        }
        return descriptor;
    }

    public String methodDescr(Class<?> type, Class<?>... args) {
        StringBuilder desc = new StringBuilder("(");
        if (args != null) for (Class<?> arg : args) desc.append(descriptorOf(arg));
        desc.append(")").append(type == null ? "V" : descriptorOf(type));
        return desc.toString();
    }

    private Type toType(Class<?> clazz) {
        return toType(clazz.getName());
    }

    private Type toType(String typeName) {
        return Type.getType(toTypeDescriptor(typeName));
    }

    public String toTypeDescriptor(Class<?> clazz) {
        return descriptorOf(clazz);
    }

    public String toTypeDescriptor(String className) {
        final StringBuilder arrayPrefix = new StringBuilder();
        while (className.endsWith("[]")) {
            arrayPrefix.append("[");
            className = className.substring(0, className.length() - 2);
        }
        String typeDescriptor;
        try {
            typeDescriptor = toTypeDescriptor(typeResolver.resolveType(className));
        } catch (ClassNotFoundException e) {
            typeDescriptor = "L" + className.replace('.', '/') + ";";
        }
        return arrayPrefix.toString() + typeDescriptor;
    }

    public String toInteralName(Class<?> clazz) {
        return clazz.isPrimitive() ? descriptorOf(clazz) : Type.getType(clazz).getInternalName();
    }

    public String toInteralName(String className) {
        final StringBuilder arrayPrefix = new StringBuilder();
        while (className.endsWith("[]")) {
            arrayPrefix.append("[");
            className = className.substring(0, className.length() - 2);
        }
        String typeDescriptor;
        boolean isPrimitive = false;
        try {
            Class<?> clazz = typeResolver.resolveType(className);
            isPrimitive = clazz.isPrimitive();
            typeDescriptor = toInteralName(clazz);
        } catch (ClassNotFoundException e) {
            typeDescriptor = className.replace('.', '/');
        }
        if (!isPrimitive && arrayPrefix.length() > 0) typeDescriptor = "L" + typeDescriptor + ";";
        return arrayPrefix.toString() + typeDescriptor;
    }

    public String getClassName() {
        return className;
    }

    private String[] toInteralNames(Class<?>[] classes) {
        if (classes == null) return null;
        String[] internals = new String[classes.length];
        for (int i = 0; i < classes.length; i++) internals[i] = toInteralName(classes[i]);
        return internals;
    }

    // FieldDescr

    public ClassGenerator addField(int access, String name, Class<?> type) {
        return addField(access, name, type, null, null);
    }

    public ClassGenerator addField(int access, String name, Class<?> type, String signature) {
        return addField(access, name, type, signature, null);
    }

    public ClassGenerator addStaticField(int access, String name, Class<?> type, Object value) {
        return addField(access + ACC_STATIC, name, type, null, value);
    }

    public ClassGenerator addStaticField(int access, String name, Class<?> type, String signature, Object value) {
        return addField(access + ACC_STATIC, name, type, signature, value);
    }

    private ClassGenerator addField(int access, String name, Class<?> type, String signature, Object value) {
        classParts.add(new FieldDescr(access, name, descriptorOf(type), signature, value));
        return this;
    }

    private static class FieldDescr implements ClassPartDescr {

        private final int access;
        private final String name;
        private final String desc;
        private final String signature;
        private final Object value;

        FieldDescr(int access, String name, String desc, String signature, Object value) {
            this.access = access;
            this.name = name;
            this.desc = desc;
            this.signature = signature;
            this.value = value;
        }

        public void write(ClassGenerator cg, ClassWriter cw) {
            cw.visitField(access, name, desc, signature, value).visitEnd();
        }
    }

    public ClassGenerator addDefaultConstructor() {
        return addDefaultConstructor(EMPTY_METHOD_BODY);
    }

    public ClassGenerator addDefaultConstructor(final MethodBody body, Class<?>... args) {
        MethodBody constructorBody = new MethodBody() {
            public void body(MethodVisitor mv) {
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, getClassGenerator().getSuperClassDescriptor(), "<init>", "()V"); // super()
                body.writeBody(getClassGenerator(), mv);
            }
        };

        return addMethod(ACC_PUBLIC, "<init>", methodDescr(null, args), constructorBody);
    }

    public ClassGenerator addMethod(int access, String name, String desc) {
        return addMethod(access, name, desc, null, null, EMPTY_METHOD_BODY);
    }

    public ClassGenerator addMethod(int access, String name, String desc, MethodBody body) {
        return addMethod(access, name, desc, null, null, body);
    }

    public ClassGenerator addMethod(int access, String name, String desc, String signature, MethodBody body) {
        return addMethod(access, name, desc, signature, null, body);
    }

    public ClassGenerator addMethod(int access, String name, String desc, String[] exceptions, MethodBody body) {
        return addMethod(access, name, desc, null, exceptions, body);
    }

    public ClassGenerator addMethod(int access, String name, String desc, String signature, String[] exceptions, MethodBody body) {
        classParts.add(new MethodDescr(access, name, desc, signature, exceptions, body));
        return this;
    }

    public ClassGenerator addStaticInitBlock(MethodBody body) {
        if (staticInitializer == null) {
            staticInitializer = new StaticInitializerDescr();
        }
        staticInitializer.addInitializer(body);
        return this;
    }

    private static final MethodBody EMPTY_METHOD_BODY = new MethodBody() {
        protected final void body(MethodVisitor mv) {
            mv.visitInsn(RETURN); // return
        }
    };

    // MethodBody

    public abstract static class MethodBody {

        private ClassGenerator classGenerator;
        protected MethodVisitor mv;
        private Map<Integer, Type> storedTypes;

        protected abstract void body(MethodVisitor mv);

        public final void writeBody(ClassGenerator classGenerator, MethodVisitor mv) {
            this.classGenerator = classGenerator;
            this.mv = mv;
            try {
                body(mv);
            } finally {
                this.classGenerator = null;
                this.mv = null;
            }
        }

        protected ClassGenerator getClassGenerator() {
            return classGenerator;
        }

        protected final int getCodeForType(Class<?> typeClass, int opcode) {
            return Type.getType(typeClass).getOpcode(opcode);
        }

        protected final int store(int registry, Class<?> typeClass) {
            return store(registry, Type.getType(typeClass));
        }

        protected final int store(int registry, String typeName) {
            return store(registry, classGenerator.toType(typeName));
        }

        protected final int store(int registry, Type t) {
            if (storedTypes == null) storedTypes = new HashMap<>();
            mv.visitVarInsn(t.getOpcode(ISTORE), registry);
            storedTypes.put(registry, t);
            return t.getSize();
        }

        protected final void load(int registry) {
            mv.visitVarInsn(storedTypes.get(registry).getOpcode(ILOAD), registry);
        }

        protected final void loadAsObject(int registry) {
            Type type = storedTypes.get(registry);
            mv.visitVarInsn(type.getOpcode(ILOAD), registry);
            String typeName = type.getClassName();
            convertPrimitiveToObject(typeName);
        }

        protected void convertPrimitiveToObject(Class<?> primitiveClass) {
            convertPrimitiveToObject(primitiveClass.getName());
        }

        private void convertPrimitiveToObject(String typeName) {
            if (typeName.equals("int"))
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
            else if (typeName.equals("boolean"))
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
            else if (typeName.equals("char"))
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;");
            else if (typeName.equals("byte"))
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;");
            else if (typeName.equals("short"))
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;");
            else if (typeName.equals("float"))
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
            else if (typeName.equals("long"))
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
            else if (typeName.equals("double"))
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
        }

        protected final void print(String msg) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn(msg);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V");
        }

        protected final void println(String msg) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn(msg);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V");
        }

        protected final void printRegistryValue(int reg) {
            Type type = storedTypes.get(reg);
            if (type == null) {
                printRegistryValue(reg, Object.class);
                return;
            }

            printRegistryValue(reg, convertPrimitiveNameToType(type.getClassName()));
        }

        protected final void printRegistryValue(int reg, Class<?> clazz) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitVarInsn(Type.getType(clazz).getOpcode(ILOAD), reg);
            invokeVirtual(PrintStream.class, "print", null, clazz);
        }

        protected final void printLastRegistry(Class<?> clazz) {
            Type t = Type.getType(clazz);
            mv.visitVarInsn(t.getOpcode(ISTORE), 100);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitVarInsn(t.getOpcode(ILOAD), 100);
            invokeVirtual(PrintStream.class, "print", null, clazz);
        }

        protected final void printStack() {
            mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/RuntimeException", "printStackTrace", "()V");
            mv.visitInsn(RETURN);
        }

        protected final <T> void returnAsArray(T[] array) {
            createArray(array.getClass().getComponentType(), array.length);
            for (int i = 0; i < array.length; i++) {
                mv.visitInsn(DUP);
                push(i);
                push(array[i]);
                mv.visitInsn(AASTORE);
            }
            mv.visitInsn(ARETURN);
        }

        protected final <T> void returnAsArray(Collection<T> collection, Class<T> clazz) {
            createArray(clazz, collection.size());
            int i = 0;
            for (T item : collection) {
                mv.visitInsn(DUP);
                push(i++);
                push(item);
                mv.visitInsn(AASTORE);
            }
            mv.visitInsn(ARETURN);
        }

        protected final void createArray(Class<?> componentType, int size) {
            mv.visitLdcInsn(size);
            if (componentType.isPrimitive()) {
                int newPrimitiveArrayType = T_BOOLEAN;
                if (componentType == int.class) {
                    newPrimitiveArrayType = T_INT;
                } else if (componentType == long.class) {
                    newPrimitiveArrayType = T_LONG;
                } else if (componentType == double.class) {
                    newPrimitiveArrayType = T_DOUBLE;
                } else if (componentType == float.class) {
                    newPrimitiveArrayType = T_FLOAT;
                } else if (componentType == char.class) {
                    newPrimitiveArrayType = T_CHAR;
                } else if (componentType == short.class) {
                    newPrimitiveArrayType = T_SHORT;
                } else if (componentType == byte.class) {
                    newPrimitiveArrayType = T_BYTE;
                }
                mv.visitIntInsn(NEWARRAY, newPrimitiveArrayType);
            } else {
                mv.visitTypeInsn(ANEWARRAY, internalName(componentType));
            }
        }

        protected final void push(Object obj) {
            if (obj instanceof Boolean) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", (Boolean) obj ? "TRUE" : "FALSE", "Ljava/lang/Boolean;");
            } else {
                mv.visitLdcInsn(obj);
            }
        }

        protected final void push(Object obj, Class<?> type) {
            if (obj == null) {
                mv.visitInsn(ACONST_NULL);
                return;
            }

            if (type == String.class || type == Object.class) {
                mv.visitLdcInsn(obj);
            } else if (type == char.class) {
                AsmUtil.pushInt(mv, ((Character) obj).charValue());
            } else if (type.isPrimitive()) {
                if (obj instanceof String) {
                    obj = coerceStringToPrimitive(type, (String) obj);
                } else {
                    obj = coercePrimitiveToPrimitive(type, obj);
                }
                mv.visitLdcInsn(obj);
            } else if (type == Class.class) {
                mv.visitLdcInsn(classGenerator.toType((Class<?>) obj));
            } else if (type == Character.class) {
                invokeConstructor(Character.class, new Object[]{obj.toString().charAt(0)}, char.class);
            } else if (type.isInterface() || isAbstract(type.getModifiers())) {
                push(obj, obj.getClass());
            } else {
                invokeConstructor(type, new Object[]{obj.toString()}, String.class);
            }
        }

        private Object coercePrimitiveToPrimitive(Class<?> primitiveType, Object value) {
            if (primitiveType == long.class) {
                return ((Number) value).longValue();
            }
            if (primitiveType == double.class) {
                return ((Number) value).doubleValue();
            }
            if (primitiveType == float.class) {
                return ((Number) value).floatValue();
            }
            return value;
        }

        private Object coerceStringToPrimitive(Class<?> primitiveType, String value) {
            if (primitiveType == boolean.class) {
                return Boolean.valueOf(value);
            }
            if (primitiveType == int.class) {
                return Integer.valueOf(value);
            }
            if (primitiveType == long.class) {
                return Long.valueOf(value);
            }
            if (primitiveType == float.class) {
                return Float.valueOf(value);
            }
            if (primitiveType == double.class) {
                return Double.valueOf(value);
            }
            if (primitiveType == char.class) {
                return Character.valueOf(value.charAt(0));
            }
            if (primitiveType == short.class) {
                return Short.valueOf(value);
            }
            if (primitiveType == byte.class) {
                return Byte.valueOf(value);
            }
            throw new RuntimeException("Unexpected type: " + primitiveType);
        }

        protected final void cast(Class<?> from, Class<?> to) {
            if (to.isAssignableFrom(from)) {
                return;
            }
            if (from.isPrimitive()) {
                if (to.isPrimitive()) {
                    castPrimitiveToPrimitive(from, to);
                } else if (to == Object.class) {
                    castFromPrimitive(from);
                } else {
                    Class toPrimitive = convertToPrimitiveType(to);
                    castPrimitiveToPrimitive(convertToPrimitiveType(from), toPrimitive);
                    castFromPrimitive(toPrimitive);
                }
            } else {
                if (to.isPrimitive()) {
                    Class<?> primitiveFrom = convertToPrimitiveType(from);
                    castToPrimitive(primitiveFrom);
                    castPrimitiveToPrimitive(primitiveFrom, to);
                } else {
                    cast(to);
                }
            }
        }

        protected final void cast(Class<?> clazz) {
            mv.visitTypeInsn(CHECKCAST, internalName(clazz));
        }

        protected final void instanceOf(Class<?> clazz) {
            mv.visitTypeInsn(INSTANCEOF, internalName(clazz));
        }

        protected final void castPrimitiveToPrimitive(Class<?> from, Class<?> to) {
            if (from == to) return;
            if (from == int.class) {
                if (to == long.class) mv.visitInsn(I2L);
                else if (to == float.class) mv.visitInsn(I2F);
                else if (to == double.class) mv.visitInsn(I2D);
                else if (to == byte.class) mv.visitInsn(I2B);
                else if (to == char.class) mv.visitInsn(I2C);
                else if (to == short.class) mv.visitInsn(I2S);
            } else if (from == long.class) {
                if (to == int.class) mv.visitInsn(L2I);
                else if (to == float.class) mv.visitInsn(L2F);
                else if (to == double.class) mv.visitInsn(L2D);
            } else if (from == float.class) {
                if (to == int.class) mv.visitInsn(F2I);
                else if (to == long.class) mv.visitInsn(F2L);
                else if (to == double.class) mv.visitInsn(F2D);
            } else if (from == double.class) {
                if (to == int.class) mv.visitInsn(D2I);
                else if (to == long.class) mv.visitInsn(D2L);
                else if (to == float.class) mv.visitInsn(D2F);
            }
        }

        protected final void castFromPrimitive(Class<?> clazz) {
            Class<?> boxedType = convertFromPrimitiveType(clazz);
            invokeStatic(boxedType, "valueOf", boxedType, clazz);
        }

        protected final void castToPrimitive(Class<?> clazz) {
            if (clazz == boolean.class) {
                cast(Boolean.class);
                invokeVirtual(Boolean.class, "booleanValue", boolean.class);
            } else if (clazz == char.class) {
                cast(Character.class);
                invokeVirtual(Character.class, "charValue", char.class);
            } else {
                cast(Number.class);
                invokeVirtual(Number.class, clazz.getName() + "Value", clazz);
            }
        }

        protected final void invoke(Method method) {
            if ((method.getModifiers() & Modifier.STATIC) > 0) {
                invokeStatic(method.getDeclaringClass(), method.getName(), method.getReturnType(), method.getParameterTypes());
            } else if (method.getDeclaringClass().isInterface()) {
                invokeInterface(method.getDeclaringClass(), method.getName(), method.getReturnType(), method.getParameterTypes());
            } else {
                invokeVirtual(method.getDeclaringClass(), method.getName(), method.getReturnType(), method.getParameterTypes());
            }
        }

        protected final void invokeThis(String methodName, Class<?> returnedType, Class<?>... paramsType) {
            mv.visitMethodInsn(INVOKEVIRTUAL, classDescriptor(), methodName, methodDescr(returnedType, paramsType));
        }

        protected final void invokeStatic(Class<?> clazz, String methodName, Class<?> returnedType, Class<?>... paramsType) {
            invoke(INVOKESTATIC, clazz, methodName, returnedType, paramsType);
        }

        protected final void invokeVirtual(Class<?> clazz, String methodName, Class<?> returnedType, Class<?>... paramsType) {
            invoke(INVOKEVIRTUAL, clazz, methodName, returnedType, paramsType);
        }

        protected final void invokeInterface(Class<?> clazz, String methodName, Class<?> returnedType, Class<?>... paramsType) {
            invoke(INVOKEINTERFACE, clazz, methodName, returnedType, paramsType);
        }

        protected final void invokeConstructor(Class<?> clazz) {
            invokeConstructor(clazz, null);
        }

        protected final void invokeConstructor(Class<?> clazz, Object[] params, Class<?>... paramsType) {
            mv.visitTypeInsn(NEW, internalName(clazz));
            mv.visitInsn(DUP);
            if (params != null) {
                for (Object param : params) mv.visitLdcInsn(param);
            }
            invokeSpecial(clazz, "<init>", null, paramsType);
        }

        protected final void invokeSpecial(Class<?> clazz, String methodName, Class<?> returnedType, Class<?>... paramsType) {
            invoke(INVOKESPECIAL, clazz, methodName, returnedType, paramsType);
        }

        protected final void invoke(int opCode, Class<?> clazz, String methodName, Class<?> returnedType, Class<?>... paramsType) {
            mv.visitMethodInsn(opCode, internalName(clazz), methodName, methodDescr(returnedType, paramsType));
        }

        protected final void invokeBigDecimalArithmeticOperation(String operation) throws NoSuchMethodException {
            mv.visitFieldInsn(GETSTATIC, "java/math/MathContext", "DECIMAL128", "Ljava/math/MathContext;");
            invoke(BigDecimal.class.getMethod(operation, BigDecimal.class, MathContext.class));
        }

        protected final void putStaticField(String name, Class<?> type) {
            mv.visitFieldInsn(PUTSTATIC, classDescriptor(), name, classGenerator.descriptorOf(type));
        }

        protected final void getStaticField(String name, Class<?> type) {
            mv.visitFieldInsn(GETSTATIC, classDescriptor(), name, classGenerator.descriptorOf(type));
        }

        protected final void putFieldInThisFromRegistry(String name, Class<?> type, int regNr) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, regNr);
            putFieldInThis(name, type);
        }

        protected final void putFieldInThis(String name, Class<?> type) {
            mv.visitFieldInsn(PUTFIELD, classDescriptor(), name, classGenerator.descriptorOf(type));
        }

        protected final void getFieldFromThis(String name, Class<?> type) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, classDescriptor(), name, classGenerator.descriptorOf(type));
        }

        protected final void readField(Field field) {
            boolean isStatic = (field.getModifiers() & Modifier.STATIC) != 0;
            mv.visitFieldInsn(isStatic ? GETSTATIC : GETFIELD, field.getDeclaringClass().getName().replace('.', '/'), field.getName(), classGenerator.descriptorOf(field.getType()));
        }

        // ClassGenerator delegates

        public String classDescriptor() {
            return classGenerator.getClassDescriptor();
        }

        public String superClassDescriptor() {
            return classGenerator.getSuperClassDescriptor();
        }

        public String methodDescr(Class<?> type, Class<?>... args) {
            return classGenerator.methodDescr(type, args);
        }

        private Type type(String typeName) {
            return classGenerator.toType(typeName);
        }

        public String typeDescr(Class<?> clazz) {
            return classGenerator.toTypeDescriptor(clazz);
        }

        public String typeDescr(String className) {
            return classGenerator.toTypeDescriptor(className);
        }

        public String internalName(Class<?> clazz) {
            return classGenerator.toInteralName(clazz);
        }

        public String internalName(String className) {
            return classGenerator.toInteralName(className);
        }
    }

    // MethodDescr

    private static class MethodDescr implements ClassPartDescr {

        private final int access;
        private final String name;
        private final String desc;
        private final String signature;
        private final String[] exceptions;
        private final MethodBody body;

        private MethodDescr(int access, String name, String desc, String signature, String[] exceptions, MethodBody body) {
            this.access = access;
            this.name = name;
            this.desc = desc;
            this.signature = signature;
            this.exceptions = exceptions;
            this.body = body;
        }

        public void write(ClassGenerator cg, ClassWriter cw) {
            MethodVisitor mv = cw.visitMethod(access, name, desc, signature, exceptions);
            mv.visitCode();

            try {
                body.writeBody(cg, mv);
                mv.visitMaxs(0, 0);
            } catch (Exception e) {
                throw new RuntimeException("Error writing method " + name, e);
            }

            mv.visitEnd();
        }
    }

    private static class StaticInitializerDescr implements ClassPartDescr {

        private final List<MethodBody> initializerBodies = new ArrayList<>();

        public void write(ClassGenerator cg, ClassWriter cw) {
            MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitCode();

            try {
                for (MethodBody initializerBody : initializerBodies) {
                    initializerBody.writeBody(cg, mv);
                }
            } catch (Exception e) {
                throw new RuntimeException("Error writing method static class initializer", e);
            }

            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        private void addInitializer(MethodBody initBlock) {
            initializerBodies.add(initBlock);
        }
    }

    // InternalTypeResolver

    private static class InternalTypeResolver implements TypeResolver {

        public static final Map<String, Class<?>> primitiveClassMap = new HashMap<String, Class<?>>() {{
            put("int", int.class);
            put("boolean", boolean.class);
            put("float", float.class);
            put("long", long.class);
            put("short", short.class);
            put("byte", byte.class);
            put("double", double.class);
            put("char", char.class);
        }};

        private final ClassLoader classLoader;

        private InternalTypeResolver(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        public Set<String> getImports() {
            throw new RuntimeException("Not Implemented");
        }

        public void addImport(String importEntry) {
            throw new RuntimeException("Not Implemented");
        }

        public void addImplicitImport(String importEntry) {
            throw new RuntimeException("Not Implemented");
        }

        public Class<?> resolveType(String className) throws ClassNotFoundException {
            Class<?> primitiveClassName = primitiveClassMap.get(className);
            return primitiveClassName != null ? primitiveClassName : Class.forName(className, true, classLoader);
        }

        public Class<?> resolveType(String className, ClassFilter classFilter) throws ClassNotFoundException {
            throw new RuntimeException("Not Implemented");
        }

        public String getFullTypeName(String shortName) throws ClassNotFoundException {
            throw new RuntimeException("Not Implemented");
        }

        public void registerClass(String className, Class<?> clazz) {
            throw new RuntimeException("Not Implemented");
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }
    }

    public static class InternalClassWriter extends ClassWriter {

        private ClassLoader classLoader;

        public InternalClassWriter(ClassLoader classLoader, int flags) {
            super(flags);
            this.classLoader = classLoader;
        }

        protected String getCommonSuperClass(final String type1, final String type2) {
            Class c, d;
            try {
                c = Class.forName(type1.replace('/', '.'), false, classLoader);
                d = Class.forName(type2.replace('/', '.'), false, classLoader);
            } catch (Exception e) {
                throw new RuntimeException(e.toString());
            }
            if (c.isAssignableFrom(d)) {
                return type1;
            }
            if (d.isAssignableFrom(c)) {
                return type2;
            }
            if (c.isInterface() || d.isInterface()) {
                return "java/lang/Object";
            } else {
                do {
                    c = c.getSuperclass();
                } while (!c.isAssignableFrom(d));
                return c.getName().replace('.', '/');
            }
        }
    }

    public static ClassWriter createClassWriter(ClassLoader classLoader, int access, String name, String signature, String superName, String[] interfaces) {
        ClassWriter cw = new InternalClassWriter(classLoader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(ClassLevel.getJavaVersion(classLoader), access, name, signature, superName, interfaces);
        return cw;
    }
}
