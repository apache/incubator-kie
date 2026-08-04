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
package org.drools.testcoverage.integrationtests.prebuilt;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.KieRepository;
import org.kie.api.builder.ReleaseId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests loading KJARs from the classpath via URLClassLoader.
 * Extracted from KieRepositoryTest in test-compiler-integration.
 */
public class KieRepositoryWithPrebuiltJarTest {

    @Test
    public void testLoadKjarFromClasspath() {
        // DROOLS-1335
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        URL simpleKjar = this.getClass().getResource("/kie-project-simple-1.0.0.jar");
        assertThat(simpleKjar).as("Make sure to build test-integration-prebuilt-jars first")
                .isNotNull();
        URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{simpleKjar});
        Thread.currentThread().setContextClassLoader(urlClassLoader);

        try {
            KieServices ks = KieServices.Factory.get();
            KieRepository kieRepository = ks.getRepository();
            ReleaseId releaseId = ks.newReleaseId("org.drools.testcoverage", "kie-project-simple", "1.0.0");
            KieModule kieModule = kieRepository.getKieModule(releaseId);
            assertThat(kieModule).isNotNull();
            assertThat(kieModule.getReleaseId()).isEqualTo(releaseId);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    @Test
    public void testTryLoadNotExistingKjarFromClasspath() {
        // DROOLS-1335
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        URL simpleKjar = this.getClass().getResource("/kie-project-simple-1.0.0.jar");
        assertThat(simpleKjar).as("Make sure to build test-integration-prebuilt-jars first")
                .isNotNull();
        URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{simpleKjar});
        Thread.currentThread().setContextClassLoader(urlClassLoader);

        try {
            KieServices ks = KieServices.Factory.get();
            KieRepository kieRepository = ks.getRepository();
            ReleaseId releaseId = ks.newReleaseId("org.drools.testcoverage", "kie-project-simple", "1.0.1");
            KieModule kieModule = kieRepository.getKieModule(releaseId);
            assertThat(kieModule).isNull();
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    @Test
    public void testLoadingNotAKJar() {
        // DROOLS-1351
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        URL pojoJar = this.getClass().getResource("/only-jar-pojo-not-kjar-no-kmodule-1.0.0.jar");
        assertThat(pojoJar).as("Make sure to build test-integration-prebuilt-jars first")
                .isNotNull();
        URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{pojoJar});
        Thread.currentThread().setContextClassLoader(urlClassLoader);

        try {
            KieServices ks = KieServices.Factory.get();
            KieRepository kieRepository = ks.getRepository();
            ReleaseId releaseId = ks.newReleaseId("org.drools.testcoverage", "only-jar-pojo-not-kjar-no-kmodule", "1.0.0");
            KieModule kieModule = kieRepository.getKieModule(releaseId);
            assertThat(kieModule).isNull();
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }
}
