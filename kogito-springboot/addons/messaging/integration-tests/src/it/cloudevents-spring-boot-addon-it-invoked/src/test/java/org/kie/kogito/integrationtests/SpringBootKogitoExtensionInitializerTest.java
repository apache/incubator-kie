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
package org.kie.kogito.integrationtests;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.ExtensionProvider;
import org.junit.jupiter.api.Test;
import org.kie.kogito.app.KogitoSpringbootApplication;
import org.kie.kogito.event.cloudevents.utils.CloudEventUtils;
import org.kie.kogito.event.cloudevents.extension.KogitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = KogitoSpringbootApplication.class)
public class SpringBootKogitoExtensionInitializerTest {

    private static final String MODEL_NAME = "TestModelName";
    private static final String MODEL_NAMESPACE = "TestModelNamespace";

    @Test
    public void testKogitoExtension() {
        String eventJson = "" +
                "{\n" +
                "  \"specversion\": \"1.0\",\n" +
                "  \"id\": \"SomeEventId\",\n" +
                "  \"source\": \"SomeEventSource\",\n" +
                "  \"type\": \"SomeEventType\",\n" +
                "  \"" + KogitoExtension.KOGITO_DMN_MODEL_NAME + "\": \"" + MODEL_NAME + "\",\n" +
                "  \"" + KogitoExtension.KOGITO_DMN_MODEL_NAMESPACE + "\": \"" + MODEL_NAMESPACE + "\",\n" +
                "  \"data\": \"{}\"" +
                "}";

        CloudEvent event = CloudEventUtils.decode(eventJson).orElseThrow(IllegalStateException::new);
        KogitoExtension kogitoExtension = ExtensionProvider.getInstance().parseExtension(KogitoExtension.class, event);

        assertThat(kogitoExtension).as("KogitoExtension not registered, please make sure " +
                "bean org.kie.kogito.addon.cloudevents.spring.SpringBootKogitoExtensionInitializer has been loaded").isNotNull();
        assertThat(kogitoExtension.getDmnModelName()).isEqualTo(MODEL_NAME);
        assertThat(kogitoExtension.getDmnModelNamespace()).isEqualTo(MODEL_NAMESPACE);
    }
}
