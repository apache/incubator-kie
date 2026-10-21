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
package org.jbpm.process.core.datatype;

import java.util.Arrays;

import org.jbpm.process.core.datatype.impl.type.BooleanDataType;
import org.jbpm.process.core.datatype.impl.type.EnumDataType;
import org.jbpm.process.core.datatype.impl.type.FloatDataType;
import org.jbpm.process.core.datatype.impl.type.IntegerDataType;
import org.jbpm.process.core.datatype.impl.type.ListDataType;
import org.jbpm.process.core.datatype.impl.type.ObjectDataType;
import org.jbpm.process.core.datatype.impl.type.StringDataType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DataTypeResolverTest {

    private enum Champions {
        BETIS,
        DEPORTIVO,
        REAL_SOCIEDAD,
        VALENCIA
    }

    private enum Others {
        SEVILLA,
        REAL_MADRID,
        F_C_BARCELONA,
        OSASUNA
    }

    @Test
    public void testDataType() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        assertThat(DataTypeResolver.fromObject("pepe")).isInstanceOf(StringDataType.class);
        assertThat(DataTypeResolver.fromObject(true)).isInstanceOf(BooleanDataType.class);
        assertThat(DataTypeResolver.fromObject(4)).isInstanceOf(IntegerDataType.class);
        assertThat(DataTypeResolver.fromObject(23.2f)).isInstanceOf(FloatDataType.class);
        assertThat(DataTypeResolver.fromObject(Arrays.asList("1", "2", "3"))).isInstanceOf(ListDataType.class);
        assertThat(DataTypeResolver.fromObject(new byte[0])).isInstanceOf(ObjectDataType.class);
        DataType enumDataType = DataTypeResolver.fromObject(Champions.BETIS);
        assertThat(enumDataType).isInstanceOf(EnumDataType.class);
        assertThat(enumDataType.readValue("BETIS")).isEqualTo(Champions.BETIS);
        assertThat(enumDataType.verifyDataType(Champions.DEPORTIVO)).isTrue();
        assertThat(enumDataType.getStringType()).isEqualTo(Champions.class.getName());
        assertThat(enumDataType.getObjectClass()).isEqualTo(Champions.class);
        assertThat(enumDataType.verifyDataType(Others.F_C_BARCELONA)).isFalse();
        assertThat(DataTypeResolver.fromType("String", cl)).isInstanceOf(StringDataType.class);
        assertThat(DataTypeResolver.fromType("Boolean", cl)).isInstanceOf(BooleanDataType.class);
        assertThat(DataTypeResolver.fromType("Integer", cl)).isInstanceOf(IntegerDataType.class);
        assertThat(DataTypeResolver.fromType("Float", cl)).isInstanceOf(FloatDataType.class);
        assertThat(DataTypeResolver.fromType("java.util.List", cl)).isInstanceOf(ListDataType.class);
        assertThat(DataTypeResolver.fromType("Object", cl)).isInstanceOf(ObjectDataType.class);
        assertThat(DataTypeResolver.fromType("", cl).getStringType()).isEqualTo("java.lang.Object");
        assertThat(DataTypeResolver.fromType(Others.class.getName(), cl)).isInstanceOf(EnumDataType.class);
    }
}
