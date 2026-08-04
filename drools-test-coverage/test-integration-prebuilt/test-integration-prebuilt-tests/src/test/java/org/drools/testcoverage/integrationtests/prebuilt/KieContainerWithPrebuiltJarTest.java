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

import java.io.File;
import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.rule.Rule;
import org.kie.api.runtime.KieContainer;
import org.kie.maven.integration.MavenRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that a KieContainer can be updated with a new KJAR version from the Maven repository.
 * Extracted from KieContainerTest#testKieContainerBeforeAndAfterDeployOfSnapshot in test-suite.
 */
public class KieContainerWithPrebuiltJarTest {

    @Test
    public void testKieContainerBeforeAndAfterDeployOfSnapshot() throws Exception {
        // BZ-1007977
        KieServices ks = KieServices.Factory.get();

        String group = "org.kie.test";
        String artifact = "test-module";
        String version = "1.0.0-SNAPSHOT";

        ReleaseId releaseId = ks.newReleaseId(group, artifact, version);

        File kjar = new File("src/test/resources/kjar/kjar-module-before.jar");
        assertThat(kjar).as("Make sure to build test-integration-prebuilt-jars first")
                .exists();
        File pom = new File("src/test/resources/kjar/pom-kjar.xml");
        MavenRepository repository = MavenRepository.getMavenRepository();
        repository.installArtifact(releaseId, kjar, pom);

        KieContainer kContainer = ks.newKieContainer(releaseId);
        KieBase kbase = kContainer.getKieBase();
        assertThat(kbase).isNotNull();
        Collection<KiePackage> packages = kbase.getKiePackages();
        assertThat(packages).isNotNull();
        assertThat(packages.size()).isEqualTo(1);
        Collection<Rule> rules = packages.iterator().next().getRules();
        assertThat(rules.size()).isEqualTo(2);

        ks.getRepository().removeKieModule(releaseId);

        // deploy new version
        File kjar1 = new File("src/test/resources/kjar/kjar-module-after.jar");
        assertThat(kjar1).as("Make sure to build test-integration-prebuilt-jars first")
                .exists();
        File pom1 = new File("src/test/resources/kjar/pom-kjar.xml");

        repository.installArtifact(releaseId, kjar1, pom1);

        KieContainer kContainer2 = ks.newKieContainer(releaseId);
        KieBase kbase2 = kContainer2.getKieBase();
        assertThat(kbase2).isNotNull();
        Collection<KiePackage> packages2 = kbase2.getKiePackages();
        assertThat(packages2).isNotNull();
        assertThat(packages2.size()).isEqualTo(1);
        Collection<Rule> rules2 = packages2.iterator().next().getRules();
        assertThat(rules2.size()).isEqualTo(4);

        ks.getRepository().removeKieModule(releaseId);
    }
}
