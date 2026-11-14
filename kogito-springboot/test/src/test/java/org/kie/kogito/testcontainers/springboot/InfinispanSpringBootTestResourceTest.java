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
package org.kie.kogito.testcontainers.springboot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.testcontainers.KogitoInfinispanContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.kie.kogito.testcontainers.Constants.CONTAINER_NAME_PREFIX;
import static org.mockito.Mockito.spy;

public class InfinispanSpringBootTestResourceTest {

    private static final String IMAGE = "my-infinispan-image";

    private InfinispanSpringBootTestResource resource;

    @BeforeEach
    public void setup() {
        System.setProperty(CONTAINER_NAME_PREFIX + KogitoInfinispanContainer.NAME, IMAGE);
        givenResource();
    }

    @Test
    public void shouldGetProperty() {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> resource.getProperties().get(InfinispanSpringBootTestResource.KOGITO_INFINISPAN_PROPERTY));
    }

    @Test
    public void shouldConditionalBeDisabledByDefault() {
        givenResource();
        thenConditionalIsDisabled();
    }

    @Test
    public void shouldConditionalBeEnabled() {
        givenConditionalResource();
        thenConditionalIsEnabled();
    }

    private void givenResource() {
        resource = new InfinispanSpringBootTestResource();
    }

    private void givenConditionalResource() {
        resource = spy(new InfinispanSpringBootTestResource.Conditional());
    }

    private void thenConditionalIsEnabled() {
        assertThat(resource.isConditionalEnabled()).isTrue();
    }

    private void thenConditionalIsDisabled() {
        assertThat(resource.isConditionalEnabled()).isFalse();
    }
}
