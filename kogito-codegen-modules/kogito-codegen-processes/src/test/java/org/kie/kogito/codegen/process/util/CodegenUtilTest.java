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
package org.kie.kogito.codegen.process.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.QuarkusKogitoBuildContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kie.kogito.codegen.api.context.ContextAttributesConstants.KOGITO_CODEGEN_BOOLEAN_OBJECT_ACCESSOR_BEHAVIOUR;
import static org.kie.kogito.codegen.core.utils.CodegenUtil.*;

public class CodegenUtilTest {

    private KogitoBuildContext context;

    @BeforeEach
    public void setup() {
        this.context = QuarkusKogitoBuildContext.builder().build();
    }

    @Test
    public void testGetBooleanObjectAccessor() {
        assertThat(getBooleanObjectAccessor(context)).isEqualTo("is");

        context.setApplicationProperty(KOGITO_CODEGEN_BOOLEAN_OBJECT_ACCESSOR_BEHAVIOUR, "javaBeans");
        assertThat(getBooleanObjectAccessor(context)).isEqualTo("get");

        context.setApplicationProperty(KOGITO_CODEGEN_BOOLEAN_OBJECT_ACCESSOR_BEHAVIOUR, "isPrefix");
        assertThat(getBooleanObjectAccessor(context)).isEqualTo("is");

        context.setApplicationProperty(KOGITO_CODEGEN_BOOLEAN_OBJECT_ACCESSOR_BEHAVIOUR, "get1");
        assertThatThrownBy(() -> getBooleanObjectAccessor(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Property " + KOGITO_CODEGEN_BOOLEAN_OBJECT_ACCESSOR_BEHAVIOUR + " defined but does not contain proper value: expected 'isPrefix' or 'javaBeans'");
    }
}
