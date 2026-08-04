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
import java.util.List;
import java.util.stream.Stream;

import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that a KieBuilder can use a ClassLoader backed by an external JAR (surf).
 * Extracted from Misc2Test#testKieBuilderWithClassLoader in test-compiler-integration.
 */
public class KieBuilderWithPrebuiltJarTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(false).stream();
    }

    /**
     * Extracted from Misc2Test#testKieBuilderWithClassLoader (DROOLS-763).
     */
    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testKieBuilderWithClassLoader(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-763
        String drl =
                "import org.example.surf.Person\n" +
                "\n" +
                "global java.util.List list\n" +
                "\n" +
                "rule R1 when\n" +
                "    $i : Integer()\n" +
                "then\n" +
                "    Person p = new Person();\n" +
                "    p.setAge($i);\n" +
                "    insert(p);\n" +
                "end\n" +
                "\n" +
                "rule R2 when\n" +
                "    $p : Person()\n" +
                "then\n" +
                "    list.add($p.getAge());\n" +
                "end\n";

        URL simplejar = this.getClass().getResource("/surf.jar");
        assertThat(simplejar).as("Make sure to build test-integration-prebuilt-jars first")
                .isNotNull();
        URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{simplejar});

        InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.getKieBaseFromDrlWithClassLoaderForKieBuilder("test", urlClassLoader, kieBaseTestConfiguration, drl);
        KieSession ksession = kbase.newKieSession();

        List<Integer> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        ksession.insert(18);
        ksession.fireAllRules();

        assertThat(list.size()).isEqualTo(1);
        assertThat((int) list.get(0)).isEqualTo(18);
    }
}
