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
package org.drools.testcoverage.functional;

import java.io.File;
import java.io.IOException;

import org.drools.testcoverage.common.util.TestConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.io.KieResources;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests correct behavior of KieContainer in specific cases, not covered by other tests.
 */
public class KieContainerTest {

    private static final String DRL = "package defaultKBase;\n rule testRule when then end\n";

    private static final String SESSION_NAME = "defaultKSession";

    private static final ReleaseId RELEASE_ID = KieServices.Factory.get().newReleaseId(
            TestConstants.PACKAGE_TESTCOVERAGE,
            "kie-container-test",
            "1.0.0");

    private KieServices kieServices;

    @BeforeEach
    public void initialize() throws IOException {
        kieServices = KieServices.Factory.get();
    }

    /**
     * Tests not disposing a KieSession created from the same KieContainer with the same name.
     */
    @Test
    public void testNotDisposingAnotherKieSession() {
        this.createKieModule(RELEASE_ID);

        final KieContainer kieContainer = kieServices.newKieContainer(RELEASE_ID);

        // get a new KieSession with specified name
        final KieSession firstKSession = kieContainer.newKieSession(SESSION_NAME);

        // get another KieSession with the same name - it should not dispose the former
        final KieSession secondKSession = kieContainer.newKieSession(SESSION_NAME);
        try {
            // session should not already be disposed
            firstKSession.fireAllRules();
        } catch (IllegalStateException e) {
            fail("KieSession should not have been already disposed.", e);
        } finally {
            firstKSession.dispose();
            secondKSession.dispose();
        }
    }

    @Test
    public void testFileSystemResourceBuilding() {
        // DROOLS-2339
        KieServices kieServices = KieServices.Factory.get();
        KieResources kieResources = kieServices.getResources();
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();

        File drlFile = new File(
                "src/test/resources/org/drools/testcoverage/functional/parser/drl/asterisk-imports.drl");
        kieFileSystem.write(kieResources.newFileSystemResource(drlFile, "UTF-8"));


        KieModuleModel kmodel = kieServices.newKieModuleModel();
        kieFileSystem.writeKModuleXML(kmodel.toXML());

        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        KieContainer kieContainer = kieServices.newKieContainer(kieBuilder.getKieModule().getReleaseId());
        KieBaseConfiguration kieBaseConfiguration = kieServices.newKieBaseConfiguration();
        KieBase kieBase = kieContainer.newKieBase(kieBaseConfiguration);
        assertThat(kieBase.getKiePackages()).isNotEmpty();
    }

    /**
     * Helper method creating simple KieModule with given ReleaseId.
     */
    private void createKieModule(final ReleaseId releaseId) {
        final KieModuleModel kmodule = kieServices.newKieModuleModel();
        kmodule.newKieBaseModel("defaultKBase").setDefault(true).newKieSessionModel(SESSION_NAME);

        final KieFileSystem kfs = kieServices.newKieFileSystem();
        kfs.generateAndWritePomXML(releaseId);
        kfs.writeKModuleXML(kmodule.toXML());

        kfs.write("src/main/resources/defaultKBase/test.drl",
                kieServices.getResources().newByteArrayResource(DRL.getBytes()));

        final KieBuilder kbuilder = kieServices.newKieBuilder(kfs);
        kbuilder.buildAll();
    }

}
