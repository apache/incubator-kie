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
package org.kie.kogito.trusty.service.common.messaging.outgoing;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.kie.kogito.explainability.api.LIMEExplainabilityRequest;
import org.kie.kogito.explainability.api.ModelIdentifier;

import io.smallrye.mutiny.helpers.test.AssertSubscriber;

import mutiny.zero.flow.adapters.AdaptersToReactiveStreams;

import static org.assertj.core.api.Assertions.assertThat;

class ExplainabilityRequestProducerTest {

    @Test
    void test() {
        AssertSubscriber<String> subscriber = AssertSubscriber.create(1);

        ExplainabilityRequestProducer producer = new ExplainabilityRequestProducer();
        producer.getEventPublisher().subscribe(AdaptersToReactiveStreams.subscriber(subscriber));

        producer.sendEvent(new LIMEExplainabilityRequest(
                "executionId", "http://localhost:8080/model",
                new ModelIdentifier("dmn", "modelNamespace:model"),
                Collections.emptyList(),
                Collections.emptyList()));

        assertThat(subscriber.getItems().size()).isEqualTo(1);
    }
}
