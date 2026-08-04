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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Stream;

import org.drools.base.rule.MapBackedClassLoader;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.io.ResourceType;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderConfiguration;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that setter-overload classes loaded from an external JAR compile without errors.
 * Extracted from JBRULESTest#testGUVNOR578_2 in test-compiler-integration.
 */
public class SetterOverloadWithPrebuiltJarTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    /**
     * Extracted from JBRULESTest#testGUVNOR578_2.
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testGUVNOR578_2(KieBaseTestConfiguration kieBaseTestConfiguration) throws Exception {
        // An internal specific test case so not enhanced for executable-model
        final MapBackedClassLoader loader = new MapBackedClassLoader(this.getClass().getClassLoader());

        InputStream is = this.getClass().getResourceAsStream("/setter-overload.jar");
        assertThat(is).as("Make sure to build test-integration-prebuilt-jars first")
                .isNotNull();
        final JarInputStream jis = new JarInputStream(is);

        JarEntry entry;
        final byte[] buf = new byte[1024];
        int len;
        while ((entry = jis.getNextJarEntry()) != null) {
            if (!entry.isDirectory()) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                while ((len = jis.read(buf)) >= 0) {
                    out.write(buf, 0, len);
                }
                loader.addResource(entry.getName(), out.toByteArray());
            }
        }

        final List<JarInputStream> jarInputStreams = new ArrayList<JarInputStream>();
        jarInputStreams.add(jis);

        final KnowledgeBuilderConfiguration conf = KnowledgeBuilderFactory.newKnowledgeBuilderConfiguration(null, loader);
        final KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder(conf);

        final String header = "import org.drools.compiler.integrationtests.setter.overload.SetterOverload\n";

        kbuilder.add(ResourceFactory.newByteArrayResource(header.getBytes()), ResourceType.DRL);
        assertThat(kbuilder.hasErrors()).isFalse();

        final String passingRule = "rule \"rule1\"\n"
                + "dialect \"mvel\"\n"
                + "when\n"
                + "SetterOverload( echelle == \"abc\" )"
                + "then\n"
                + "end\n";

        final String failingRule = "rule \"rule2\"\n"
                + "dialect \"mvel\"\n"
                + "when\n"
                + "SetterOverload( quotiteRemuneration == 123 , echelle == \"abc\" )"
                + "then\n"
                + "end\n";

        kbuilder.add(ResourceFactory.newByteArrayResource(passingRule.getBytes()), ResourceType.DRL);
        assertThat(kbuilder.hasErrors()).isFalse();

        kbuilder.add(ResourceFactory.newByteArrayResource(failingRule.getBytes()), ResourceType.DRL);
        assertThat(kbuilder.hasErrors()).isFalse();
    }
}
