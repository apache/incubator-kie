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
package org.kie.kogito.quarkus.dmn;

import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartData.QuarkusDataKey;
import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;

import static io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog.Language.JAVA;

public class KogitoDMNCodeCodestartIT {

    static final Properties properties = new Properties();
    static {
        try {
            properties.load(KogitoDMNCodeCodestartIT.class.getClassLoader().getResourceAsStream("project.properties"));
        } catch (Exception e) {
            throw new RuntimeException("project.properties version unknown");
        }
    }

    public static String assertjVersion() {
        return properties.getProperty("version.assertj");
    }

    public static String projectVersion() {
        return properties.getProperty("version");
    }

    @RegisterExtension
    public static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
            .setupStandaloneExtensionTest("org.drools:drools-quarkus-decisions")
            .extension(ArtifactKey.fromString("io.quarkus:quarkus-rest-jackson")) // account for KOGITO-5817
            .extension(ArtifactCoords.fromString("org.assertj:assertj-core:" + assertjVersion()))
            .putData(QuarkusDataKey.APP_CONFIG, Map.of("quarkus.http.test-port", "0"))
            .languages(JAVA)
            .build();

    @Test
    void testContent() throws Throwable {
        codestartTest.checkGeneratedTestSource("org.acme.PricingTest");
    }

    @Test
    void testBuild() throws Throwable {
        codestartTest.buildAllProjects();
    }
}
