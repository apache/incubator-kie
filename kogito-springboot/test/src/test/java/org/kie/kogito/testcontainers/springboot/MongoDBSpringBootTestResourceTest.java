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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.testcontainers.Constants;
import org.kie.kogito.testcontainers.KogitoMongoDBContainer;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
class MongoDBSpringBootTestResourceTest {

    @Mock
    private KogitoMongoDBContainer container;

    private MongoDBSpringBootTestResource resource;

    @Test
    void shouldGetProperty() {
        givenResource();
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> resource.getProperties().get(MongoDBSpringBootTestResource.MONGODB_CONNECTION_PROPERTY));
    }

    @Test
    void shouldConditionalBeDisabledByDefault() {
        givenResource();
        thenConditionalIsDisabled();
    }

    @Test
    void shouldConditionalBeEnabled() {
        givenConditionalResource();
        thenConditionalIsEnabled();
    }

    @Test
    void shouldGetDockerImageName() {
        givenResource();
        assertThat(resource.getTestResource().getDockerImageName())
                .isEqualTo(System.getenv("TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX") + System.getProperty(Constants.CONTAINER_NAME_PREFIX + KogitoMongoDBContainer.NAME));
    }

    private void givenConditionalResource() {
        resource = spy(new MongoDBSpringBootTestResource.Conditional());
    }

    private void givenResource() {
        resource = spy(new MongoDBSpringBootTestResource());
    }

    private void thenConditionalIsEnabled() {
        assertThat(resource.isConditionalEnabled()).isTrue();
    }

    private void thenConditionalIsDisabled() {
        assertThat(resource.isConditionalEnabled()).isFalse();
    }
}
