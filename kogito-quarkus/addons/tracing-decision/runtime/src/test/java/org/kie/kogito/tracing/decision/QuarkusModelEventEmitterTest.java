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
package org.kie.kogito.tracing.decision;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kie.kogito.KogitoGAV;
import org.kie.kogito.decision.DecisionModelMetadata;
import org.kie.kogito.decision.DecisionModelResource;
import org.kie.kogito.decision.DecisionModelResourcesProvider;
import org.kie.kogito.event.cloudevents.utils.CloudEventUtils;

import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class QuarkusModelEventEmitterTest {

    @Test
    public void testEmitEvent() {
        final AssertSubscriber<String> subscriber = AssertSubscriber.create(2);
        final List<DecisionModelResource> models = Arrays.asList(makeModel(), makeModel());
        final DecisionModelResourcesProvider mockedDecisionModelResourcesProvider = () -> models;

        final QuarkusModelEventEmitter eventEmitter = new QuarkusModelEventEmitter(mockedDecisionModelResourcesProvider);
        eventEmitter.getEventPublisher().subscribe(subscriber);
        eventEmitter.publishDecisionModels();

        subscriber.assertNotTerminated();

        List<String> items = subscriber.getItems();
        assertThat(items).hasSize(2);
        final String rawCloudEvent1 = items.get(0);
        final String rawCloudEvent2 = items.get(1);
        final CloudEvent cloudEvent1 = CloudEventUtils.decode(rawCloudEvent1).orElseThrow(IllegalStateException::new);
        final CloudEvent cloudEvent2 = CloudEventUtils.decode(rawCloudEvent2).orElseThrow(IllegalStateException::new);

        assertThat(cloudEvent1.getId()).isEqualTo("id");
        assertThat(cloudEvent2.getId()).isEqualTo("id");
    }

    private DecisionModelResource makeModel() {
        final DecisionModelResource model = mock(DecisionModelResource.class);
        when(model.getGav()).thenReturn(new KogitoGAV("groupId", "artifactId", "version"));
        when(model.getModelName()).thenReturn("name");
        when(model.getNamespace()).thenReturn("namespace");
        when(model.getModelMetadata()).thenReturn(
                new DecisionModelMetadata(
                        "http://www.omg.org/spec/DMN/20151101/dmn.xsd"));
        when(model.get()).thenReturn("model");
        return model;
    }
}
