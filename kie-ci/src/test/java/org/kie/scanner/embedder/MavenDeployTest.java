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
package org.kie.scanner.embedder;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieScanner;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.maven.integration.embedder.MavenSettings;
import org.kie.scanner.AbstractKieCiTest;
import org.kie.scanner.KieMavenRepository;

import static org.kie.maven.integration.embedder.MavenSettings.CUSTOM_SETTINGS_PROPERTY;
import static org.kie.scanner.KieMavenRepository.getKieMavenRepository;

@Disabled("UnsupportedClassVersionError under -T 4 parallel build")
public class MavenDeployTest extends AbstractKieCiTest {

    @Test
    public void testDeploy() throws IOException {
        KieServices ks = KieServices.Factory.get();
        ReleaseId releaseId = ks.newReleaseId( "org.kie", "scanner-test-deploy", "1.0-SNAPSHOT" );

        Path m2Folder = Files.createTempDirectory( "temp-m2" );
        Path settingsXmlPath = generateSettingsXml( m2Folder );

        String oldSettingsXmlPath = System.getProperty( CUSTOM_SETTINGS_PROPERTY );
        try {
            System.setProperty( CUSTOM_SETTINGS_PROPERTY, settingsXmlPath.toString() );
            MavenSettings.reinitSettings();

            InternalKieModule kJar1 = createKieJar( ks, releaseId, "rule1", "rule2" );
            KieContainer kieContainer = ks.newKieContainer( releaseId );

            KieMavenRepository repository = getKieMavenRepository();
            RemoteRepository remote = createRemoteRepository( m2Folder );
            repository.deployArtifact(remote, releaseId, kJar1, createKPom(m2Folder, releaseId).toFile());

            // create a ksesion and check it works as expected
            KieSession ksession = kieContainer.newKieSession( "KSession1" );
            checkKSession(ksession, "rule1", "rule2");

            // create a new kjar
            InternalKieModule kJar2 = createKieJar(ks, releaseId, "rule2", "rule3");

            // deploy it on maven
            repository.deployArtifact(remote, releaseId, kJar2, createKPom(m2Folder, releaseId).toFile());

            // since I am not calling start() on the scanner it means it won't have automatic scheduled scanning
            KieScanner scanner = ks.newKieScanner( kieContainer );

            // scan the maven repo to get the new kjar version and deploy it on the kcontainer
            scanner.scanNow();

            // create a ksesion and check it works as expected
            KieSession ksession2 = kieContainer.newKieSession("KSession1");
            checkKSession(ksession2, "rule2", "rule3");

            ks.getRepository().removeKieModule(releaseId);
        } finally {
            if (oldSettingsXmlPath == null) {
                System.clearProperty( CUSTOM_SETTINGS_PROPERTY );
            } else {
                System.setProperty( CUSTOM_SETTINGS_PROPERTY, oldSettingsXmlPath );
            }
            MavenSettings.reinitSettings();
        }
    }

    private static Path generateSettingsXml( Path m2Folder ) throws IOException {
        String settingsXml =
                "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"\n" +
                "      xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "      xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0\n" +
                "                          http://maven.apache.org/xsd/settings-1.0.0.xsd\">\n" +
                "  <profiles>\n" +
                "    <profile>\n" +
                "      <id>repos</id>\n" +
                "      <activation>\n" +
                "        <activeByDefault>true</activeByDefault>\n" +
                "      </activation>\n" +
                "      <repositories>\n" +
                "        <repository>\n" +
                "          <id>myTestRepo</id>\n" +
                "          <name>My Test Repo</name>\n" +
                "          <url>" + m2Folder.toUri().toURL().toExternalForm() + "</url>\n" +
                "          <releases><enabled>true</enabled></releases>\n" +
                "          <snapshots><enabled>true</enabled></snapshots>\n" +
                "        </repository>\n" +
                "    </repositories>\n" +
                "    </profile>\n" +
                "  </profiles>\n" +
                "</settings>\n";

        Path settingsXmlPath = Files.createTempFile( m2Folder, "settings", ".xml" );
        Files.write( settingsXmlPath, settingsXml.getBytes() );
        return settingsXmlPath;
    }

    private static RemoteRepository createRemoteRepository(Path m2Folder) throws MalformedURLException {
        String localRepositoryUrl = m2Folder.toUri().toURL().toExternalForm();
        return new RemoteRepository.Builder( "myTestRepo", "default", localRepositoryUrl ).build();
    }

    protected Path createKPom( Path m2Folder, ReleaseId releaseId ) throws IOException {
        Path pomXmlPath = Files.createTempFile( m2Folder, "pom", ".xml" );
        Files.write( pomXmlPath, getPom(releaseId).getBytes() );
        return pomXmlPath;
    }

    @Test
    public void testKScannerWithDeployUsingDistributionManagement() throws IOException {
        KieServices ks = KieServices.Factory.get();
        ReleaseId releaseId = ks.newReleaseId( "org.kie", "scanner-test-deploy-dist", "1.0-SNAPSHOT" );

        Path m2Folder = Files.createTempDirectory( "temp-m2-dist" );
        Path settingsXmlPath = generateSettingsXml( m2Folder );

        String oldSettingsXmlPath = System.getProperty( CUSTOM_SETTINGS_PROPERTY );
        try {
            System.setProperty( CUSTOM_SETTINGS_PROPERTY, settingsXmlPath.toString() );
            MavenSettings.reinitSettings();

            InternalKieModule kJar1 = createKieJar( ks, releaseId, "rule1", "rule2" );
            KieContainer kieContainer = ks.newKieContainer( releaseId );

            KieMavenRepository repository = getKieMavenRepository();
            repository.deployArtifact(releaseId, kJar1, createKPomWithDistributionManagement(m2Folder, releaseId).toFile());

            // create a ksesion and check it works as expected
            KieSession ksession = kieContainer.newKieSession( "KSession1" );
            checkKSession(ksession, "rule1", "rule2");

            // create a new kjar
            InternalKieModule kJar2 = createKieJar(ks, releaseId, "rule2", "rule3");

            // deploy it on maven
            repository.deployArtifact(releaseId, kJar2, createKPomWithDistributionManagement(m2Folder, releaseId).toFile());

            // since I am not calling start() on the scanner it means it won't have automatic scheduled scanning
            KieScanner scanner = ks.newKieScanner( kieContainer );

            // scan the maven repo to get the new kjar version and deploy it on the kcontainer
            scanner.scanNow();

            // create a ksesion and check it works as expected
            KieSession ksession2 = kieContainer.newKieSession("KSession1");
            checkKSession(ksession2, "rule2", "rule3");

            ks.getRepository().removeKieModule(releaseId);
        } finally {
            if (oldSettingsXmlPath == null) {
                System.clearProperty( CUSTOM_SETTINGS_PROPERTY );
            } else {
                System.setProperty( CUSTOM_SETTINGS_PROPERTY, oldSettingsXmlPath );
            }
            MavenSettings.reinitSettings();
        }
    }

    protected Path createKPomWithDistributionManagement( Path m2Folder, ReleaseId releaseId ) throws IOException {
        String localRepositoryUrl = m2Folder.toUri().toURL().toExternalForm();

        String pom =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "\n" +
                "  <groupId>" + releaseId.getGroupId() + "</groupId>\n" +
                "  <artifactId>" + releaseId.getArtifactId() + "</artifactId>\n" +
                "  <version>" + releaseId.getVersion() + "</version>\n" +
                "   <distributionManagement>\n" +
                "     <repository>\n" +
                "       <id>myTestRepo</id>\n" +
                "       <name>Releases Repository</name>\n" +
                "       <url>" + localRepositoryUrl + "</url>\n" +
                "    </repository>\n" +
                "    <snapshotRepository>\n" +
                "      <id>myTestRepo-snapshots</id>\n" +
                "      <name>Snapshot Repository</name>\n" +
                "       <url>" + localRepositoryUrl + "</url>\n" +
                "    </snapshotRepository>\n" +
                "  </distributionManagement>" +
                "</project>";

        Path pomXmlPath = Files.createTempFile( m2Folder, "pom", ".xml" );
        Files.write( pomXmlPath, pom.getBytes() );
        return pomXmlPath;
    }
}
