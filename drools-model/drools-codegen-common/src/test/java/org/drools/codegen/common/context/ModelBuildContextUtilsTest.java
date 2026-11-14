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
package org.drools.codegen.common.context;

import org.drools.util.FileUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.drools.codegen.common.DroolsModelBuildContext.APPLICATION_PROPERTIES_YAML_FILE_NAME;
import static org.drools.codegen.common.DroolsModelBuildContext.APPLICATION_PROPERTIES_YML_FILE_NAME;

class ModelBuildContextUtilsTest {

    @MethodSource("testData")
    @ParameterizedTest
    void loadYmlPropertiesByFile(String fileName) {
        File ymlFile = FileUtils.getFile(fileName);
        assertThat(ymlFile.exists()).isTrue();
        Properties properties = new Properties();
        ModelBuildContextUtils.loadYmlProperties(ymlFile, properties);
        assertThat(properties.getProperty("this.is.a")).isEqualTo("test");
        assertThat(properties.getProperty("this.is.b")).isEqualTo("notNull");
        assertThat(properties.getProperty("this.was.a")).isEqualTo("test");
        assertThat(properties.getProperty("this.was.b")).isEqualTo("notNull");
        assertThat(properties.getProperty("that.is.a")).isEqualTo("test");
        assertThat(properties.getProperty("that.is.b")).isEqualTo("notNull");
        assertThat(properties.getProperty("that.was.a")).isEqualTo("test");
        assertThat(properties.getProperty("that.was.b")).isEqualTo("notNull");
    }

    @MethodSource("testData")
    @ParameterizedTest
    void loadYmlPropertiesByInputStream(String fileName) {
        File ymlFile = FileUtils.getFile(fileName);
        assertThat(ymlFile.exists()).isTrue();
        Properties properties = new Properties();
        try (InputStream ymlResourceStream = new FileInputStream(ymlFile)) {
            ModelBuildContextUtils.loadYmlProperties(ymlResourceStream, properties);
            assertThat(properties.getProperty("this.is.a")).isEqualTo("test");
            assertThat(properties.getProperty("this.is.b")).isEqualTo("notNull");
            assertThat(properties.getProperty("this.was.a")).isEqualTo("test");
            assertThat(properties.getProperty("this.was.b")).isEqualTo("notNull");
            assertThat(properties.getProperty("that.is.a")).isEqualTo("test");
            assertThat(properties.getProperty("that.is.b")).isEqualTo("notNull");
            assertThat(properties.getProperty("that.was.a")).isEqualTo("test");
            assertThat(properties.getProperty("that.was.b")).isEqualTo("notNull");
        } catch (Exception e) {
            fail("Unexpected exception while loading yml string map from file: " + fileName, e);
        }

    }

    @MethodSource("testData")
    @ParameterizedTest
    void loadYmlStringMap(String fileName) {
        File ymlFile = FileUtils.getFile(fileName);
        assertThat(ymlFile.exists()).isTrue();
        try (InputStream ymlResourceStream = new FileInputStream(ymlFile)) {
            Map<String, String> retrieved = ModelBuildContextUtils.loadYmlStringMap(ymlResourceStream);
            assertThat(retrieved).isNotNull();
            commonCheck(retrieved);
        } catch (Exception e) {
            fail("Unexpected exception while loading yml string map from file: " + fileName, e);
        }
    }

    @MethodSource("testData")
    @ParameterizedTest
    void loadYmlMap(String fileName) {
        File ymlFile = FileUtils.getFile(fileName);
        assertThat(ymlFile.exists()).isTrue();
        try (InputStream ymlResourceStream = new FileInputStream(ymlFile)) {
            TreeMap<String, Object> retrieved = ModelBuildContextUtils.loadYmlMap(ymlResourceStream);
            assertThat(retrieved).isNotNull();
            assertThat(retrieved.containsKey("this")).isTrue();
        } catch (Exception e) {
            fail("Unexpected exception while loading yml string map from file: " + fileName, e);
        }
    }


    @MethodSource("testData")
    @ParameterizedTest
    void convertYamlObjectToMap(String fileName) {
        File ymlFile = FileUtils.getFile(fileName);
        assertThat(ymlFile.exists()).isTrue();
        try (InputStream ymlResourceStream = new FileInputStream(ymlFile)) {
            TreeMap<String, Object> ymlMap = ModelBuildContextUtils.loadYmlMap(ymlResourceStream);
            assertThat(ymlMap).isNotNull();
            Map<String, String> retrieved = ModelBuildContextUtils.convertYamlObjectToMap(ymlMap);
            commonCheck(retrieved);
        } catch (Exception e) {
            fail("Unexpected exception while loading yml string map from file: " + fileName, e);
        }
    }

    private static Collection<String> testData() {
        return java.util.List.of(APPLICATION_PROPERTIES_YML_FILE_NAME, APPLICATION_PROPERTIES_YAML_FILE_NAME);
    }

    private void commonCheck(Map<String, String> retrieved) {
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.containsKey("this.is.a")).isTrue();
        assertThat(retrieved.get("this.is.a")).isEqualTo("test");
        assertThat(retrieved.containsKey("this.is.b")).isTrue();
        assertThat(retrieved.get("this.is.b")).isEqualTo("notNull");
        assertThat(retrieved.containsKey("this.was.a")).isTrue();
        assertThat(retrieved.get("this.was.a")).isEqualTo("test");
        assertThat(retrieved.containsKey("this.was.b")).isTrue();
        assertThat(retrieved.get("this.was.b")).isEqualTo("notNull");

        assertThat(retrieved.containsKey("that.is.a")).isTrue();
        assertThat(retrieved.get("that.is.a")).isEqualTo("test");
        assertThat(retrieved.containsKey("that.is.b")).isTrue();
        assertThat(retrieved.get("that.is.b")).isEqualTo("notNull");
        assertThat(retrieved.containsKey("that.was.a")).isTrue();
        assertThat(retrieved.get("that.was.a")).isEqualTo("test");
        assertThat(retrieved.containsKey("that.was.b")).isTrue();
        assertThat(retrieved.get("that.was.b")).isEqualTo("notNull");
    }

}