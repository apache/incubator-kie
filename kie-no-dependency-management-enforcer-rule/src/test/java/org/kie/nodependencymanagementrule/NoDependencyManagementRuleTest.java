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
package org.kie.nodependencymanagementrule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
class NoDependencyManagementRuleTest {

    private MavenProject project;

    private Model model;

    private NoDependencyManagementRule rule;

    @BeforeEach
    void setUp() {
        model = Mockito.mock(Model.class);
        project = Mockito.mock(MavenProject.class);
        rule = new NoDependencyManagementRule(project);
    }

    @MockitoSettings(strictness = Strictness.LENIENT)
    @Test
    void executeValidWithAllowedPoms() {
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setLocation("", new InputLocation(2,2));
        when(project.getDependencyManagement()).thenReturn(dependencyManagement);
        when(project.getGroupId()).thenReturn("org.kie");
        when(project.getArtifactId()).thenReturn("allowed-pom");
        Set<String> allowedPoms = Set.of("org.kie:allowed-pom");
        rule.setAllowedPoms(allowedPoms);
        assertThatCode(() -> rule.execute()).doesNotThrowAnyException();
    }

    @Test
    void executeValidWithoutAllowedPoms() {
        when(project.getDependencyManagement()).thenReturn(null);
        when(project.getModel()).thenReturn(model);
        when(project.getGroupId()).thenReturn("org.kie");
        when(project.getArtifactId()).thenReturn("not-allowed-pom");
        Set<String> allowedPoms = Set.of("org.kie:allowed-pom");
        rule.setAllowedPoms(allowedPoms);
        assertThatCode(() -> rule.execute()).doesNotThrowAnyException();
    }

    @Test
    void executeInvalidWithAllowedPoms() {
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setLocation("", new InputLocation(2,2));
        when(project.getDependencyManagement()).thenReturn(dependencyManagement);
        when(project.getGroupId()).thenReturn("org.kie");
        when(project.getArtifactId()).thenReturn("not-allowed-pom");
        Set<String> allowedPoms = Set.of("org.kie:allowed-pom");
        rule.setAllowedPoms(allowedPoms);
        assertThatExceptionOfType(EnforcerRuleException.class).isThrownBy(() -> rule.execute());
    }

    @MockitoSettings(strictness = Strictness.LENIENT)
    @Test
    void executeInvalidWithoutAllowedPoms() {
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setLocation("", new InputLocation(2,2));
        when(project.getDependencyManagement()).thenReturn(dependencyManagement);
        when(project.getModel()).thenReturn(model);
        when(project.getGroupId()).thenReturn("org.kie");
        when(project.getArtifactId()).thenReturn("not-allowed-pom");
        assertThatExceptionOfType(EnforcerRuleException.class).isThrownBy(() -> rule.execute());
    }

    @Test
    void checkDependencyManagementValid() {
        when(project.getModel()).thenReturn(model);
        assertThatCode(() -> rule.checkForDependencyManagement()).doesNotThrowAnyException();
        //---
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setLocation("some key", new InputLocation(2,2));
        when(project.getDependencyManagement()).thenReturn(dependencyManagement);
        assertThatCode(() -> rule.checkForDependencyManagement()).doesNotThrowAnyException();

        //--- /
        List<Profile> profiles = List.of(new Profile(), new Profile());
        when(model.getProfiles()).thenReturn(profiles);
        when(project.getDependencyManagement()).thenReturn(null);
        assertThatCode(() -> rule.checkForDependencyManagement()).doesNotThrowAnyException();
    }

    @Test
    void checkDependencyManagementInvalid() {
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setLocation("", new InputLocation(2,2));
        when(project.getDependencyManagement()).thenReturn(dependencyManagement);
        assertThatExceptionOfType(EnforcerRuleException.class).isThrownBy(() -> rule.checkForDependencyManagement());

        //--- /
        Profile profile = new Profile();
        profile.setDependencyManagement(dependencyManagement);
        List<Profile> profiles = List.of(new Profile(), profile);
        when(model.getProfiles()).thenReturn(profiles);
        when(project.getModel()).thenReturn(model);
        when(project.getDependencyManagement()).thenReturn(null);
        assertThatExceptionOfType(EnforcerRuleException.class).isThrownBy(() -> rule.checkForDependencyManagement());
    }

    @Test
    void checkDependencyManagementInProjectValid() {
        assertThatCode(() -> rule.checkDependencyManagementInProject()).doesNotThrowAnyException();
    }

    @Test
    void checkDependencyManagementInProjectInvalid() {
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setLocation("", new InputLocation(2,2));
        when(project.getDependencyManagement()).thenReturn(dependencyManagement);
        assertThatExceptionOfType(EnforcerRuleException.class).isThrownBy(() -> rule.checkDependencyManagementInProject());
    }

    @Test
    void checkDependencyManagementInProfilesValid() {
        List<Profile> profiles = List.of(new Profile(), new Profile());
        when(model.getProfiles()).thenReturn(profiles);
        when(project.getModel()).thenReturn(model);
        assertThatCode(() -> rule.checkDependencyManagementInProfiles()).doesNotThrowAnyException();
    }

    @Test
    void checkDependencyManagementInProfilesInvalid() {
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setLocation("", new InputLocation(2,2));
        Profile profile = new Profile();
        profile.setDependencyManagement(dependencyManagement);
        List<Profile> profiles = List.of(new Profile(), profile);
        when(model.getProfiles()).thenReturn(profiles);
        when(project.getModel()).thenReturn(model);
        assertThatExceptionOfType(EnforcerRuleException.class).isThrownBy(() -> rule.checkDependencyManagementInProfiles());
    }

    @Test
    void checkDependencyManagementInProfileValid() {
        Profile profile = new Profile();
        assertThatCode(() -> rule.checkDependencyManagementInProfile(profile)).doesNotThrowAnyException();
    }

    @Test
    void checkDependencyManagementInProfileInvalid() {
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setLocation("", new InputLocation(2,2));
        Profile profile = new Profile();
        profile.setDependencyManagement(dependencyManagement);
        assertThatExceptionOfType(EnforcerRuleException.class).isThrownBy(() -> rule.checkDependencyManagementInProfile(profile));
    }

    @Test
    void invalidDependencyManagementTrue() {
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setLocation("", new InputLocation(2,2));
        assertThat(rule.invalidDependencyManagement(dependencyManagement)).isTrue();
    }

    @Test
    void invalidDependencyManagementFalse() {
        DependencyManagement dependencyManagement = null;
        assertThat(rule.invalidDependencyManagement(dependencyManagement)).isFalse();
        dependencyManagement = new DependencyManagement();
        assertThat(rule.invalidDependencyManagement(dependencyManagement)).isFalse();
    }

    @Test
    void isAllowedTrue() {
        Set<String> allowedPoms = Set.of("org.kie:allowed-pom");
        rule.setAllowedPoms(allowedPoms);
        assertThat(rule.isAllowed("org.kie", "allowed-pom")).isTrue();
    }

    @Test
    void isAllowedFalse() {
        assertThat(rule.isAllowed("org.kie", "allowed-pom")).isFalse();
        Set<String> allowedPoms = Set.of("org.kie:allowed-pom");
        rule.setAllowedPoms(allowedPoms);
        assertThat(rule.isAllowed("org.kie", "not- allowed-pom")).isFalse();
    }
}