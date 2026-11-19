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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mvel2.asm.ClassWriter;
import org.mvel2.asm.MethodVisitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mvel2.asm.Opcodes.ACC_PUBLIC;
import static org.mvel2.asm.Opcodes.ACC_STATIC;
import static org.mvel2.asm.Opcodes.ACC_SUPER;
import static org.mvel2.asm.Opcodes.IRETURN;

public class AsmUtilTest {

    private static final String PROBE_CLASS_NAME = "org.drools.mvel.asm.PushIntProbe";

    @ParameterizedTest(name = "value={0}")
    @ValueSource(ints = { Integer.MIN_VALUE, Short.MIN_VALUE - 1, Short.MIN_VALUE, Byte.MIN_VALUE - 1, Byte.MIN_VALUE, -1,
                          0, 5, 6, Byte.MAX_VALUE, Byte.MAX_VALUE + 1, Short.MAX_VALUE, Short.MAX_VALUE + 1, Integer.MAX_VALUE })
    public void pushIntPushesTheGivenValue(int value) throws Exception {
        ClassWriter cw = ClassGenerator.createClassWriter(getClass().getClassLoader(),
                                                          ACC_PUBLIC | ACC_SUPER,
                                                          PROBE_CLASS_NAME.replace('.', '/'),
                                                          null,
                                                          "java/lang/Object",
                                                          null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "value", "()I", null, null);
        mv.visitCode();
        AsmUtil.pushInt(mv, value);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();

        Class<?> probe = new ProbeClassLoader(getClass().getClassLoader()).define(PROBE_CLASS_NAME, cw.toByteArray());

        assertThat(probe.getMethod("value").invoke(null)).isEqualTo(value);
    }

    private static class ProbeClassLoader extends ClassLoader {

        ProbeClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
