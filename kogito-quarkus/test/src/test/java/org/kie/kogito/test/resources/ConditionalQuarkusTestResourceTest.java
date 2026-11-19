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
package org.kie.kogito.test.resources;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.test.quarkus.QuarkusTestProperty;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.annotation.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConditionalQuarkusTestResourceTest {

    private static final int MAPPED_PORT = 8800;
    private static final String PROPERTY_KEY = "property-key";
    private static final String PROPERTY_VALUE = "localhost:" + MAPPED_PORT;
    private static final String PROPERTY_DEFAULT_VALUE = "PROPERTY_DEFAULT_VALUE";

    @Mock
    private TestResource resource;

    @Mock
    private ConditionHolder conditional;

    private ConditionalQuarkusTestResource instance;

    private Map<String, String> actualOutput;
    private TestInstance testInstance;

    @BeforeEach
    public void setup() {
        instance = new ConditionalQuarkusTestResource(resource, conditional) {

            @Override
            protected Map<String, String> getProperties() {
                return Collections.singletonMap(PROPERTY_KEY, PROPERTY_VALUE);
            }

        };
        actualOutput = null;
        testInstance = new TestInstance();
        lenient().when(resource.getMappedPort()).thenReturn(MAPPED_PORT);
    }

    @Test
    public void shouldReturnResource() {
        assertThat(instance.getTestResource()).isEqualTo(resource);
    }

    @Test
    public void shouldInvokeConditional() {
        instance.enableConditional();
        verify(conditional).enableConditional();
    }

    @Test
    public void shouldStartIfConditionalIsEnabled() {
        givenConditionalEnabled();
        whenStartInstance();
        thenResourceIsStarted();
        thenConfigMapIsUpdated();
    }

    @Test
    public void shouldNotStartIfConditionalIsDisabled() {
        givenConditionalDisabled();
        whenStartInstance();
        thenResourceIsNotUsed();
    }

    @Test
    public void shouldStopIfConditionalIsEnabled() {
        givenConditionalEnabled();
        whenStopInstance();
        thenResourceIsStopped();
    }

    @Test
    public void shouldNotStopIfConditionalIsDisabled() {
        givenConditionalDisabled();
        whenStopInstance();
        thenResourceIsNotUsed();
    }

    @Test
    public void shouldInjectQuarkusIntegrationTestProperty() {
        whenInjectTestInstance();
        thenPropertyValueIsUpdated();
        thenPropertyValueWithDefaultValueIsUpdated();
        thenOtherPropertyValueIsNotUpdated();
        thenResourceIsUpdated();
    }

    private void givenConditionalEnabled() {
        when(conditional.isEnabled()).thenReturn(true);
    }

    private void givenConditionalDisabled() {
        when(conditional.isEnabled()).thenReturn(false);
    }

    private void whenStartInstance() {
        actualOutput = instance.start();
    }

    private void whenStopInstance() {
        instance.stop();
    }

    private void whenInjectTestInstance() {
        instance.inject(testInstance);
    }

    private void thenResourceIsStarted() {
        verify(resource).start();
    }

    private void thenResourceIsStopped() {
        verify(resource).stop();
    }

    private void thenResourceIsNotUsed() {
        verifyNoInteractions(resource);
    }

    private void thenConfigMapIsUpdated() {
        String actual = actualOutput.get(PROPERTY_KEY);
        assertThat(actual).isEqualTo(PROPERTY_VALUE);
    }

    private void thenPropertyValueIsUpdated() {
        assertThat(testInstance.propertyValue).isEqualTo(PROPERTY_VALUE);
    }

    private void thenPropertyValueWithDefaultValueIsUpdated() {
        assertThat(testInstance.propertyWithDefaultValue).isEqualTo(PROPERTY_DEFAULT_VALUE);
    }

    private void thenOtherPropertyValueIsNotUpdated() {
        assertThat(testInstance.otherPropertyValue).isNull();
    }

    private void thenResourceIsUpdated() {
        assertThat(testInstance.resource).isEqualTo(instance);
    }

    private class TestInstance {

        @QuarkusTestProperty(name = "property-key")
        private String propertyValue;

        @QuarkusTestProperty(name = "property-key-with-default", defaultValue = PROPERTY_DEFAULT_VALUE)
        private String propertyWithDefaultValue;

        @QuarkusTestProperty(name = "other-property-key")
        private String otherPropertyValue;

        @Resource
        private ConditionalQuarkusTestResource resource;
    }
}
