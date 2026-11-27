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
package org.drools.mvel.compiler.rule.builder.dialect.asm;

import org.drools.mvel.asm.ClassGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mvel2.asm.MethodVisitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mvel2.asm.Opcodes.ACC_FINAL;
import static org.mvel2.asm.Opcodes.ACC_PRIVATE;
import static org.mvel2.asm.Opcodes.ACC_PUBLIC;
import static org.mvel2.asm.Opcodes.ALOAD;
import static org.mvel2.asm.Opcodes.ARETURN;
import static org.mvel2.asm.Opcodes.IRETURN;
import static org.mvel2.asm.Opcodes.RETURN;

public class ClassGeneratorTest {

    @Test
    public void testGenerateBean() {
        final String MY_NAME = "myName";
        ClassGenerator generator = new ClassGenerator("pkg.Bean1", getClass().getClassLoader())
                .addField(ACC_PRIVATE | ACC_FINAL, "name", String.class);

        generator.addDefaultConstructor(new ClassGenerator.MethodBody() {
            public void body(MethodVisitor mv) {
                mv.visitVarInsn(ALOAD, 0);// read local variable 0 (initialized to this) and push it on the stack
                mv.visitLdcInsn(MY_NAME); // push the String MY_NAME on the stack
                putFieldInThis("name", String.class);
                mv.visitInsn(RETURN);
            }
        }).addMethod(ACC_PUBLIC, "toString", generator.methodDescr(String.class), new ClassGenerator.MethodBody() {
            public void body(MethodVisitor mv) {
                getFieldFromThis("name", String.class);
                mv.visitInsn(ARETURN); // return the first object on the stack
            }
        });

        Object instance = generator.newInstance();
        assertThat(instance.toString()).isEqualTo(MY_NAME);
    }

    @Test
    public void testGenerateWithConstructorArg() {
        final String MY_NAME = "myName";
        ClassGenerator generator = new ClassGenerator("pkg.Bean2", getClass().getClassLoader())
                .addField(ACC_PRIVATE | ACC_FINAL, "name", String.class);

        generator.addDefaultConstructor(new ClassGenerator.MethodBody() {
            public void body(MethodVisitor mv) {
                putFieldInThisFromRegistry("name", String.class, 1);
                mv.visitInsn(RETURN);
            }
        }, String.class);

        generator.addMethod(ACC_PUBLIC, "toString", generator.methodDescr(String.class), new ClassGenerator.MethodBody() {
            public void body(MethodVisitor mv) {
                getFieldFromThis("name", String.class);
                mv.visitInsn(ARETURN); // return the first object on the stack
            }
        });

        Object instance = generator.newInstance(String.class, MY_NAME);
        assertThat(instance.toString()).isEqualTo(MY_NAME);
    }

    public interface CharSupplier {
        char getChar();
    }

    @ParameterizedTest(name = "char={0}")
    @ValueSource(chars = { 'a', (char) 127, (char) 128, 'ä', '€', (char) 65535 })
    public void pushCharConstantPreservesUnsignedValue(char expectedChar) throws Exception {
        ClassGenerator generator = new ClassGenerator("pkg.CharProbe" + (int) expectedChar, getClass().getClassLoader())
                .setInterfaces(CharSupplier.class);

        generator.addDefaultConstructor();

        generator.addMethod(ACC_PUBLIC, "getChar", generator.methodDescr(char.class), new ClassGenerator.MethodBody() {
            @Override
            public void body(MethodVisitor mv) {
                push(expectedChar, char.class);
                mv.visitInsn(IRETURN);
            }
        });

        CharSupplier supplier = (CharSupplier) generator.newInstance();
        assertThat(supplier.getChar())
                .as("Character constant 0x%04X ('%c') must not be sign-extended or truncated", (int) expectedChar, expectedChar)
                .isEqualTo(expectedChar);
    }
}
