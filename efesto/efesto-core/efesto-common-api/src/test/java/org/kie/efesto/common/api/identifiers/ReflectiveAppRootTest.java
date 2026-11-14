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
package org.kie.efesto.common.api.identifiers;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.efesto.common.api.identifiers.componentroots.ComponentRootA;
import org.kie.efesto.common.api.identifiers.componentroots.LocalComponentIdA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReflectiveAppRootTest {

    private static AppRoot appRoot;

    @BeforeAll
    public static void setup() {
        appRoot = new ReflectiveAppRoot("testing");
    }

    @Test
    void constructorNoName() {
        ReflectiveAppRoot retrieved = new ReflectiveAppRoot();
        assertThat(retrieved.name()).isEqualTo("efesto-app");
    }

    @Test
    void constructorName() {
        String name = "name";
        ReflectiveAppRoot retrieved = new ReflectiveAppRoot(name);
        assertThat(retrieved.name()).isEqualTo(name);
    }

    @Test
    void get() {
        String fileName = "fileName";
        String name = "name";
        LocalUri retrieved = appRoot.get(ComponentRootA.class)
                .get(fileName, name)
                .toLocalId()
                .asLocalUri();

        appRoot.get(ComponentRootA.class)
                .get(fileName, name)
                .toLocalId();

        assertThat(retrieved).isNotNull();
        String expected = String.format("/%1$s/%2$s/%3$s", LocalComponentIdA.PREFIX, fileName, name);
        assertThat(retrieved.path()).isEqualTo(expected);
    }

    @Test
    void getPrivateConstructorImplementation() {
        assertThatThrownBy(() -> appRoot.get(ComponentRootPrivateConstructor.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("java.lang.NoSuchMethodException");
    }

    @Test
    void getNoDefaultConstructorImplementation() {
        assertThatThrownBy(() -> appRoot.get(ComponentRootNoDefaultConstructor.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("java.lang.NoSuchMethodException");
    }

    private static class ComponentRootPrivateConstructor implements ComponentRoot {

        private ComponentRootPrivateConstructor() {
        }
    }

    private static class ComponentRootNoDefaultConstructor implements ComponentRoot {

        private final String arg;
        public ComponentRootNoDefaultConstructor(String arg) {
            this.arg = arg;
        }
    }
}