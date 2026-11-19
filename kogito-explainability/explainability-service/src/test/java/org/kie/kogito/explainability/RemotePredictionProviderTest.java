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
package org.kie.kogito.explainability;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kie.kogito.explainability.model.Feature;
import org.kie.kogito.explainability.model.PredictionOutput;
import org.kie.kogito.explainability.model.Type;
import org.kie.kogito.explainability.model.Value;

import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.explainability.TestUtils.LIME_REQUEST;

class RemotePredictionProviderTest {

    RemotePredictionProvider predictionProvider = new RemotePredictionProvider(LIME_REQUEST.getServiceUrl(),
            LIME_REQUEST.getModelIdentifier(),
            LIME_REQUEST.getOutputs(),
            null,
            null,
            null) {
        @Override
        protected WebClient getClient(Vertx vertx, URI uri) {
            return null;
        }
    };

    @Test
    void toPredictionOutput() {
        assertThat(predictionProvider.toPredictionOutput(null)).isNull();
        assertThat(predictionProvider.toPredictionOutput(new JsonObject(emptyMap()))).isNull();
        Map<String, Object> predictionMap = singletonMap("result", emptyMap());

        PredictionOutput predictionOutput = predictionProvider.toPredictionOutput(new JsonObject(predictionMap));
        assertThat(predictionOutput).isNotNull();
        assertThat(predictionOutput.getOutputs().size()).isEqualTo(LIME_REQUEST.getOutputs().size());
        assertThat(predictionOutput.getOutputs().get(0).getType()).isEqualTo(Type.UNDEFINED);
    }

    @Test
    void toMap() {
        // simple test
        Feature simple = new Feature("simple", Type.NUMBER, new Value(10));
        Feature undefined = new Feature("undefined", Type.UNDEFINED, new Value(simple));
        Feature composite = new Feature("composite", Type.COMPOSITE, new Value(asList(simple, undefined)));
        List<Feature> features1 = asList(simple, undefined, composite);

        Map<String, Object> result1 = predictionProvider.toMap(features1);

        assertThat(result1).isNotNull();
        assertThat(result1.size()).isEqualTo(features1.size());
        assertThat(result1.containsKey("simple")).isTrue();
        assertThat(result1.get("simple")).isEqualTo(10);
        assertThat(result1.containsKey("undefined")).isTrue();
        assertThat(result1.containsKey("composite")).isTrue();
        assertThat((result1.get("composite") instanceof Map)).isTrue();

        // context test
        Feature context = new Feature("context", Type.COMPOSITE, new Value(singletonList(simple)));
        Feature simple2 = new Feature("simple2", Type.BOOLEAN, new Value(true));
        List<Feature> features2 = asList(simple2, context);

        Map<String, Object> result2 = predictionProvider.toMap(features2);

        assertThat(result2).isNotNull();
        assertThat(result2.size()).isEqualTo(1);
        assertThat(result2.containsKey("simple")).isTrue();
        assertThat(result2.containsKey("simple2")).isFalse();

        // multiple nesting test
        Feature nestedComposite = new Feature("nestedComposite", Type.COMPOSITE, new Value(asList(simple, composite)));
        List<Feature> features3 = asList(simple, nestedComposite);
        Map<String, Object> result3 = predictionProvider.toMap(features3);

        assertThat(result3).isNotNull();
        assertThat(result3.size()).isEqualTo(features3.size());
        assertThat(result3.containsKey("simple")).isTrue();
        assertThat(result3.get("simple")).isEqualTo(10);
        assertThat(result3.containsKey("nestedComposite")).isTrue();
        assertThat((result3.get("nestedComposite") instanceof Map)).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedCompositeMap = (Map<String, Object>) result3.get("nestedComposite");

        assertThat(nestedCompositeMap.size()).isEqualTo(2);
        assertThat(nestedCompositeMap.containsKey("simple")).isTrue();
        assertThat(nestedCompositeMap.get("simple")).isEqualTo(10);
        assertThat(nestedCompositeMap.containsKey("composite")).isTrue();
        assertThat((nestedCompositeMap.get("composite") instanceof Map)).isTrue();
    }
}
