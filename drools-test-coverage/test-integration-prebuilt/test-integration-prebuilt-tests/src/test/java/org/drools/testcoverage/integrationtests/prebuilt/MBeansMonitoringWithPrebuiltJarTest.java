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

import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLClassLoader;

import javax.management.JMX;
import javax.management.MBeanServer;

import org.drools.compiler.kie.builder.impl.KieServicesImpl;
import org.drools.core.management.DroolsManagementAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.KieRepository;
import org.kie.api.builder.ReleaseId;
import org.kie.api.conf.MBeansOption;
import org.kie.api.management.KieContainerMonitorMXBean;
import org.kie.api.runtime.KieContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that JMX MBeans are correctly registered when loading a KJAR from the classpath.
 * Extracted from MBeansMonitoringTest#testLoadKjarFromClasspath in test-compiler-integration.
 */
public class MBeansMonitoringWithPrebuiltJarTest {

    private String mbeansprop;

    @BeforeEach
    public void setUp() {
        ((KieServicesImpl) KieServices.Factory.get()).nullKieClasspathContainer();
        ((KieServicesImpl) KieServices.Factory.get()).nullAllContainerIds();
        mbeansprop = System.getProperty(MBeansOption.PROPERTY_NAME);
        System.setProperty(MBeansOption.PROPERTY_NAME, "enabled");
    }

    @AfterEach
    public void tearDown() {
        ((KieServicesImpl) KieServices.Factory.get()).nullKieClasspathContainer();
        ((KieServicesImpl) KieServices.Factory.get()).nullAllContainerIds();
        if (mbeansprop != null) {
            System.setProperty(MBeansOption.PROPERTY_NAME, mbeansprop);
        } else {
            System.setProperty(MBeansOption.PROPERTY_NAME, MBeansOption.DISABLED.toString());
        }
    }

    /**
     * Extracted from MBeansMonitoringTest#testLoadKjarFromClasspath (DROOLS-1335).
     * Verifies that KieContainerMonitorMXBean reflects the correct ReleaseId after loading
     * a KJAR from a URLClassLoader-backed classpath.
     */
    @Test
    public void testLoadKjarFromClasspath() {
        // DROOLS-1335
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        URL simpleKjar = this.getClass().getResource("/kie-project-simple-1.0.0.jar");
        assertThat(simpleKjar).as("Make sure to build test-integration-prebuilt-jars first")
                .isNotNull();
        URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{simpleKjar});
        Thread.currentThread().setContextClassLoader(urlClassLoader);

        MBeanServer mbserver = ManagementFactory.getPlatformMBeanServer();

        try {
            KieServices ks = KieServices.Factory.get();
            KieRepository kieRepository = ks.getRepository();
            ReleaseId releaseId = ks.newReleaseId("org.drools.testcoverage", "kie-project-simple", "1.0.0");
            KieModule kieModule = kieRepository.getKieModule(releaseId);
            assertThat(kieModule).isNotNull();
            assertThat(kieModule.getReleaseId()).isEqualTo(releaseId);

            KieContainer kc = ks.newKieContainer("myID", releaseId);

            KieContainerMonitorMXBean c1Monitor = JMX.newMXBeanProxy(
                    mbserver,
                    DroolsManagementAgent.createObjectNameBy("myID"),
                    KieContainerMonitorMXBean.class);

            assertThat(c1Monitor.getConfiguredReleaseId().sameGAVof(releaseId)).isTrue();
            assertThat(c1Monitor.getResolvedReleaseId().sameGAVof(releaseId)).isTrue();

            kc.dispose();
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }
}
