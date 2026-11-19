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
package org.kie.kogito.explainability.handlers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.explainability.PredictionProviderFactory;
import org.kie.kogito.explainability.api.BaseExplainabilityRequest;
import org.kie.kogito.explainability.api.BaseExplainabilityResult;
import org.kie.kogito.explainability.api.ExplainabilityStatus;
import org.kie.kogito.explainability.api.LIMEExplainabilityRequest;
import org.kie.kogito.explainability.api.LIMEExplainabilityResult;
import org.kie.kogito.explainability.api.ModelIdentifier;
import org.kie.kogito.explainability.api.NamedTypedValue;
import org.kie.kogito.explainability.api.SaliencyModel;
import org.kie.kogito.explainability.local.lime.LimeExplainer;
import org.kie.kogito.explainability.model.Feature;
import org.kie.kogito.explainability.model.FeatureImportance;
import org.kie.kogito.explainability.model.Output;
import org.kie.kogito.explainability.model.Prediction;
import org.kie.kogito.explainability.model.PredictionProvider;
import org.kie.kogito.explainability.model.Saliency;
import org.kie.kogito.explainability.model.SimplePrediction;
import org.kie.kogito.explainability.model.Type;
import org.kie.kogito.explainability.model.Value;
import org.kie.kogito.tracing.typedvalue.CollectionValue;
import org.kie.kogito.tracing.typedvalue.StructureValue;
import org.kie.kogito.tracing.typedvalue.UnitValue;

import com.fasterxml.jackson.databind.node.IntNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class LimeExplainerServiceHandlerTest {

    private static final String EXECUTION_ID = "executionId";

    private static final String SERVICE_URL = "serviceURL";

    private static final ModelIdentifier MODEL_IDENTIFIER = new ModelIdentifier("resourceType", "resourceId");

    private LimeExplainer explainer;

    private LimeExplainerServiceHandler handler;

    @BeforeEach
    public void setup() {
        PredictionProviderFactory predictionProviderFactory = mock(PredictionProviderFactory.class);

        this.explainer = mock(LimeExplainer.class);
        this.handler = new LimeExplainerServiceHandler(explainer, predictionProviderFactory);
    }

    @Test
    public void testSupports() {
        assertThat(handler.supports(LIMEExplainabilityRequest.class)).isTrue();
        assertThat(handler.supports(BaseExplainabilityRequest.class)).isFalse();
    }

    @Test
    public void testGetPredictionWithEmptyDefinition() {
        LIMEExplainabilityRequest request = new LIMEExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                Collections.emptyList(),
                Collections.emptyList());

        Prediction prediction = handler.getPrediction(request);
        assertThat((prediction instanceof SimplePrediction)).isTrue();
        SimplePrediction simplePrediction = (SimplePrediction) prediction;

        assertThat(simplePrediction.getInput().getFeatures().isEmpty()).isTrue();
        assertThat(simplePrediction.getOutput().getOutputs().isEmpty()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetPredictionWithNonEmptyDefinition() {
        LIMEExplainabilityRequest request = new LIMEExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                List.of(new NamedTypedValue("input1",
                        new UnitValue("number", "number", new IntNode(20))),
                        new NamedTypedValue("input2",
                                new StructureValue("number", Map.of("input2b", new UnitValue("number", new IntNode(55))))),
                        new NamedTypedValue("input3",
                                new CollectionValue("number", List.of(new UnitValue("number", new IntNode(100)))))),
                List.of(new NamedTypedValue("output1",
                        new UnitValue("number", "number", new IntNode(20))),
                        new NamedTypedValue("output2",
                                new StructureValue("number", Map.of("output2b", new UnitValue("number", new IntNode(55))))),
                        new NamedTypedValue("output3",
                                new CollectionValue("number", List.of(new UnitValue("number", new IntNode(100)))))));

        Prediction prediction = handler.getPrediction(request);

        //Inputs
        assertThat(prediction.getInput().getFeatures().size()).isEqualTo(3);
        Optional<Feature> oInput1 = prediction.getInput().getFeatures().stream().filter(f -> f.getName().equals("input1")).findFirst();
        assertThat(oInput1.isPresent()).isTrue();
        Feature input1 = oInput1.get();
        assertThat(input1.getType()).isEqualTo(Type.NUMBER);
        assertThat(input1.getValue().asNumber()).isEqualTo(20);

        Optional<Feature> oInput2 = prediction.getInput().getFeatures().stream().filter(f -> f.getName().equals("input2")).findFirst();
        assertThat(oInput2.isPresent()).isTrue();
        Feature input2 = oInput2.get();
        assertThat(input2.getType()).isEqualTo(Type.COMPOSITE);
        assertThat((input2.getValue().getUnderlyingObject() instanceof List)).isTrue();
        List<Feature> input2Object = (List<Feature>) input2.getValue().getUnderlyingObject();
        assertThat(input2Object.size()).isEqualTo(1);

        Optional<Feature> oInput2Child = input2Object.stream().filter(f -> f.getName().equals("input2b")).findFirst();
        assertThat(oInput2Child.isPresent()).isTrue();
        Feature input2Child = oInput2Child.get();
        assertThat(input2Child.getType()).isEqualTo(Type.NUMBER);
        assertThat(input2Child.getValue().asNumber()).isEqualTo(55);

        Optional<Feature> oInput3 = prediction.getInput().getFeatures().stream().filter(f -> f.getName().equals("input3")).findFirst();
        assertThat(oInput3.isPresent()).isTrue();
        Feature input3 = oInput3.get();
        assertThat(input3.getType()).isEqualTo(Type.COMPOSITE);
        assertThat((input3.getValue().getUnderlyingObject() instanceof List)).isTrue();
        List<Feature> input3Object = (List<Feature>) input3.getValue().getUnderlyingObject();
        assertThat(input3Object.size()).isEqualTo(1);

        Feature input3Child = input3Object.get(0);
        assertThat(input3Child.getType()).isEqualTo(Type.NUMBER);
        assertThat(input3Child.getValue().asNumber()).isEqualTo(100);

        //Outputs
        assertThat(prediction.getOutput().getOutputs().size()).isEqualTo(3);
        Optional<Output> oOutput1 = prediction.getOutput().getOutputs().stream().filter(o -> o.getName().equals("output1")).findFirst();
        assertThat(oOutput1.isPresent()).isTrue();
        Output output1 = oOutput1.get();
        assertThat(output1.getType()).isEqualTo(Type.NUMBER);
        assertThat(output1.getValue().asNumber()).isEqualTo(20);

        Optional<Output> oOutput2 = prediction.getOutput().getOutputs().stream().filter(o -> o.getName().equals("output2")).findFirst();
        assertThat(oOutput2.isPresent()).isTrue();
        Output output2 = oOutput2.get();
        assertThat(input2.getType()).isEqualTo(Type.COMPOSITE);
        assertThat((output2.getValue().getUnderlyingObject() instanceof List)).isTrue();
        List<Output> output2Object = (List<Output>) output2.getValue().getUnderlyingObject();
        assertThat(output2Object.size()).isEqualTo(1);

        Optional<Output> oOutput2Child = output2Object.stream().filter(f -> f.getName().equals("output2b")).findFirst();
        assertThat(oOutput2Child.isPresent()).isTrue();
        Output output2Child = oOutput2Child.get();
        assertThat(output2Child.getType()).isEqualTo(Type.NUMBER);
        assertThat(output2Child.getValue().asNumber()).isEqualTo(55);

        Optional<Output> oOutput3 = prediction.getOutput().getOutputs().stream().filter(o -> o.getName().equals("output3")).findFirst();
        assertThat(oOutput3.isPresent()).isTrue();
        Output output3 = oOutput3.get();
        assertThat(output3.getType()).isEqualTo(Type.COMPOSITE);
        assertThat((output3.getValue().getUnderlyingObject() instanceof List)).isTrue();
        List<Output> output3Object = (List<Output>) output3.getValue().getUnderlyingObject();
        assertThat(output3Object.size()).isEqualTo(1);

        Output output3Child = output3Object.get(0);
        assertThat(output3Child.getType()).isEqualTo(Type.NUMBER);
        assertThat(output3Child.getValue().asNumber()).isEqualTo(100);
    }

    @Test
    public void testCreateSucceededResult() {
        LIMEExplainabilityRequest request = new LIMEExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                Collections.emptyList(),
                Collections.emptyList());

        Map<String, Saliency> saliencies = Map.of("s1",
                new Saliency(new Output("salary", Type.NUMBER),
                        List.of(new FeatureImportance(new Feature("age", Type.NUMBER, new Value(25.0)), 5.0),
                                new FeatureImportance(new Feature("dependents", Type.NUMBER, new Value(2)), -11.0))));

        BaseExplainabilityResult base = handler.createSucceededResult(request, saliencies);
        assertThat((base instanceof LIMEExplainabilityResult)).isTrue();
        LIMEExplainabilityResult result = (LIMEExplainabilityResult) base;

        assertThat(result.getStatus()).isEqualTo(ExplainabilityStatus.SUCCEEDED);
        assertThat(result.getExecutionId()).isEqualTo(EXECUTION_ID);
        assertThat(result.getSaliencies().size()).isEqualTo(1);

        SaliencyModel saliencyModel = result.getSaliencies().iterator().next();
        assertThat(saliencyModel.getFeatureImportance().size()).isEqualTo(2);
        assertThat(saliencyModel.getFeatureImportance().get(0).getFeatureName()).isEqualTo("age");
        assertThat(saliencyModel.getFeatureImportance().get(0).getFeatureScore()).isEqualTo(5.0);
        assertThat(saliencyModel.getFeatureImportance().get(1).getFeatureName()).isEqualTo("dependents");
        assertThat(saliencyModel.getFeatureImportance().get(1).getFeatureScore()).isEqualTo(-11.0);
    }

    @Test
    public void testCreateIntermediateResult() {
        LIMEExplainabilityRequest request = new LIMEExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                Collections.emptyList(),
                Collections.emptyList());

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> handler.createIntermediateResult(request, null));
    }

    @Test
    public void testCreateFailedResult() {
        LIMEExplainabilityRequest request = new LIMEExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                Collections.emptyList(),
                Collections.emptyList());

        BaseExplainabilityResult base = handler.createFailedResult(request, new NullPointerException("Something went wrong"));
        assertThat((base instanceof LIMEExplainabilityResult)).isTrue();
        LIMEExplainabilityResult result = (LIMEExplainabilityResult) base;

        assertThat(result.getStatus()).isEqualTo(ExplainabilityStatus.FAILED);
        assertThat(result.getStatusDetails()).isEqualTo("Something went wrong");
        assertThat(result.getExecutionId()).isEqualTo(EXECUTION_ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testExplainAsyncDelegation() {
        Prediction prediction = mock(Prediction.class);
        PredictionProvider predictionProvider = mock(PredictionProvider.class);

        handler.explainAsync(prediction, predictionProvider);

        verify(explainer).explainAsync(eq(prediction), eq(predictionProvider), any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testExplainAsyncWithConsumerDelegation() {
        Prediction prediction = mock(Prediction.class);
        PredictionProvider predictionProvider = mock(PredictionProvider.class);
        Consumer<Map<String, Saliency>> callback = mock(Consumer.class);

        handler.explainAsync(prediction, predictionProvider, callback);

        verify(explainer).explainAsync(eq(prediction), eq(predictionProvider), eq(callback));
    }

}
