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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.builder.KieModule;
import org.kie.api.definition.KiePackage;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests that verify isolated class loading of enum types from an external JAR.
 * Extracted from DynamicRulesTest in test-compiler-integration.
 */
public class DynamicRulesWithPrebuiltJarTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(false).stream();
    }

    /**
     * Extracted from DynamicRulesTest#testIsolatedClassLoaderWithEnumsPkgBuilder.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testIsolatedClassLoaderWithEnumsPkgBuilder(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        try {
            // Creates first class loader and use it to load fact classes
            ClassLoader loader1 = new SubvertedClassLoader(new URL[]{getClass().getResource("/testEnum.jar")},
                                                           this.getClass().getClassLoader());
            loader1.loadClass("org.drools.Primitives");
            loader1.loadClass("org.drools.TestEnum");

            // create a builder with the given classloader
            Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResourcesWithClassLoaderForKieBuilder(
                    "test", getClass(), loader1, kieBaseTestConfiguration, "test_EnumSerialization.drl").getKiePackages();

            // adding original packages to a kbase just to make sure they are fine
            KieBaseConfiguration kbaseConf = RuleBaseFactory.newKnowledgeBaseConfiguration(null, loader1);
            final KieModule kieModule = KieUtil.getKieModuleFromResourcesWithClassLoaderForKieBuilder("test", loader1, kieBaseTestConfiguration);
            InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.newKieBaseFromReleaseId(kieModule.getReleaseId(), kbaseConf);

            kbase.addPackages(kpkgs);
            KieSession ksession = kbase.newKieSession();
            List list = new ArrayList();
            ksession.setGlobal("list", list);
            assertThat(ksession.fireAllRules()).isEqualTo(1);
            assertThat(list.size()).isEqualTo(1);

            // now, create another classloader and make sure it has access to the classes
            ClassLoader loader2 = new SubvertedClassLoader(new URL[]{getClass().getResource("/testEnum.jar")},
                                                           this.getClass().getClassLoader());
            loader2.loadClass("org.drools.Primitives");
            loader2.loadClass("org.drools.TestEnum");

            // create another kbase
            KieBaseConfiguration kbaseConf2 = RuleBaseFactory.newKnowledgeBaseConfiguration(null, loader2);
            final KieModule kieModule2 = KieUtil.getKieModuleFromResourcesWithClassLoaderForKieBuilder("test2", loader2, kieBaseTestConfiguration);
            InternalKnowledgeBase kbase2 = (InternalKnowledgeBase) KieBaseUtil.newKieBaseFromReleaseId(kieModule2.getReleaseId(), kbaseConf2);

            kbase2.addPackages(kpkgs);
            ksession = kbase2.newKieSession();
            list = new ArrayList();
            ksession.setGlobal("list", list);
            assertThat(ksession.fireAllRules()).isEqualTo(1);
            assertThat(list.size()).isEqualTo(1);

        } catch (ClassCastException cce) {
            cce.printStackTrace();
            fail("No ClassCastException should be raised.");
        }
    }

    /**
     * Extracted from DynamicRulesTest#testIsolatedClassLoaderWithEnumsContextClassloader.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    @Timeout(10000)
    public void testIsolatedClassLoaderWithEnumsContextClassloader(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        try {
            // Creates first class loader and use it to load fact classes
            ClassLoader loader1 = new SubvertedClassLoader(new URL[]{getClass().getResource("/testEnum.jar")},
                                                           this.getClass().getClassLoader());
            loader1.loadClass("org.drools.Primitives");
            loader1.loadClass("org.drools.TestEnum");

            // Build it using the current context
            ClassLoader ccl = Thread.currentThread().getContextClassLoader();
            Collection<KiePackage> kpkgs;
            try {
                Thread.currentThread().setContextClassLoader(loader1);
                // create a builder with the given classloader
                kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", getClass(), kieBaseTestConfiguration, "test_EnumSerialization.drl").getKiePackages();

                // adding original packages to a kbase just to make sure they are fine
                InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration);
                kbase.addPackages(kpkgs);

                KieSession ksession = kbase.newKieSession();
                List list = new ArrayList();
                ksession.setGlobal("list", list);
                assertThat(ksession.fireAllRules()).isEqualTo(1);
                assertThat(list.size()).isEqualTo(1);
            } finally {
                Thread.currentThread().setContextClassLoader(ccl);
            }

            // now, create another classloader and make sure it has access to the classes
            ClassLoader loader2 = new SubvertedClassLoader(new URL[]{getClass().getResource("/testEnum.jar")},
                                                           this.getClass().getClassLoader());
            loader2.loadClass("org.drools.Primitives");
            loader2.loadClass("org.drools.TestEnum");

            // set context classloader and use it
            ccl = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(loader2);

                // Note: This test originally serialize/deserialize kpkgs with different context classloaders.
                // Now exec-model doesn't support package serialization so we may remove this test.

                // create another kbase
                InternalKnowledgeBase kbase2 = (InternalKnowledgeBase) KieBaseUtil.getKieBaseFromKieModuleFromDrl("test2", kieBaseTestConfiguration);
                kbase2.addPackages(kpkgs);

                KieSession ksession = kbase2.newKieSession();
                List list = new ArrayList();
                ksession.setGlobal("list", list);
                assertThat(ksession.fireAllRules()).isEqualTo(1);
                assertThat(list.size()).isEqualTo(1);
            } finally {
                Thread.currentThread().setContextClassLoader(ccl);
            }

        } catch (ClassCastException cce) {
            cce.printStackTrace();
            fail("No ClassCastException should be raised.");
        }
    }

    public class SubvertedClassLoader extends URLClassLoader {

        private static final long serialVersionUID = 510l;

        public SubvertedClassLoader(final URL[] urls, final ClassLoader parentClassLoader) {
            super(urls, parentClassLoader);
        }

        protected synchronized Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // First, check if the class has already been loaded
            Class c = findLoadedClass(name);
            if (c == null) {
                try {
                    c = findClass(name);
                } catch (ClassNotFoundException e) {
                    c = super.loadClass(name, resolve);
                }
            }
            return c;
        }
    }
}
