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
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.explainability.PredictionProviderFactory;
import org.kie.kogito.explainability.api.BaseExplainabilityRequest;
import org.kie.kogito.explainability.api.BaseExplainabilityResult;
import org.kie.kogito.explainability.api.CounterfactualDomainRange;
import org.kie.kogito.explainability.api.CounterfactualExplainabilityRequest;
import org.kie.kogito.explainability.api.CounterfactualExplainabilityResult;
import org.kie.kogito.explainability.api.CounterfactualSearchDomain;
import org.kie.kogito.explainability.api.CounterfactualSearchDomainCollectionValue;
import org.kie.kogito.explainability.api.CounterfactualSearchDomainStructureValue;
import org.kie.kogito.explainability.api.CounterfactualSearchDomainUnitValue;
import org.kie.kogito.explainability.api.ExplainabilityStatus;
import org.kie.kogito.explainability.api.ModelIdentifier;
import org.kie.kogito.explainability.api.NamedTypedValue;
import org.kie.kogito.explainability.local.counterfactual.CounterfactualExplainer;
import org.kie.kogito.explainability.local.counterfactual.CounterfactualResult;
import org.kie.kogito.explainability.local.counterfactual.entities.CounterfactualEntity;
import org.kie.kogito.explainability.local.counterfactual.entities.DoubleEntity;
import org.kie.kogito.explainability.model.CounterfactualPrediction;
import org.kie.kogito.explainability.model.Feature;
import org.kie.kogito.explainability.model.Output;
import org.kie.kogito.explainability.model.Prediction;
import org.kie.kogito.explainability.model.PredictionOutput;
import org.kie.kogito.explainability.model.PredictionProvider;
import org.kie.kogito.explainability.model.Type;
import org.kie.kogito.explainability.model.Value;
import org.kie.kogito.explainability.model.domain.EmptyFeatureDomain;
import org.kie.kogito.explainability.model.domain.NumericalFeatureDomain;
import org.kie.kogito.tracing.typedvalue.CollectionValue;
import org.kie.kogito.tracing.typedvalue.StructureValue;
import org.kie.kogito.tracing.typedvalue.TypedValue;
import org.kie.kogito.tracing.typedvalue.UnitValue;

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CounterfactualExplainerServiceHandlerTest {

    private static final String EXECUTION_ID = UUID.randomUUID().toString();

    private static final String COUNTERFACTUAL_ID = UUID.randomUUID().toString();

    private static final String SOLUTION_ID = UUID.randomUUID().toString();

    private static final String SERVICE_URL = "serviceURL";

    private static final ModelIdentifier MODEL_IDENTIFIER = new ModelIdentifier("resourceType", "resourceId");

    private static final Long MAX_RUNNING_TIME_SECONDS = 60L;

    private static final Long MAX_RUNNING_TIME_MILLISECONDS = MAX_RUNNING_TIME_SECONDS * 1000;

    private CounterfactualExplainer explainer;

    private CounterfactualExplainerServiceHandler handler;

    @BeforeEach
    public void setup() {
        PredictionProviderFactory predictionProviderFactory = mock(PredictionProviderFactory.class);

        this.explainer = mock(CounterfactualExplainer.class);
        this.handler = new CounterfactualExplainerServiceHandler(explainer,
                predictionProviderFactory,
                MAX_RUNNING_TIME_MILLISECONDS);
    }

    @Test
    public void testSupports() {
        assertThat(handler.supports(CounterfactualExplainabilityRequest.class)).isTrue();
        assertThat(handler.supports(BaseExplainabilityRequest.class)).isFalse();
    }

    @Test
    public void testGetPredictionWithEmptyDefinition() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                MAX_RUNNING_TIME_SECONDS);

        Prediction prediction = handler.getPrediction(request);
        assertThat((prediction instanceof CounterfactualPrediction)).isTrue();
        CounterfactualPrediction counterfactualPrediction = (CounterfactualPrediction) prediction;

        assertThat(counterfactualPrediction.getInput().getFeatures().isEmpty()).isTrue();

        assertThat(request.getMaxRunningTimeSeconds()).isEqualTo(counterfactualPrediction.getMaxRunningTimeSeconds());
    }

    @Test
    public void testGetPredictionWithFlatInputModel() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                List.of(new NamedTypedValue("input1",
                        new UnitValue("number", new IntNode(20)))),
                Collections.emptyList(),
                Collections.emptyList(),
                MAX_RUNNING_TIME_SECONDS);

        Prediction prediction = handler.getPrediction(request);
        assertThat((prediction instanceof CounterfactualPrediction)).isTrue();
        CounterfactualPrediction counterfactualPrediction = (CounterfactualPrediction) prediction;

        assertThat(counterfactualPrediction.getInput().getFeatures().size()).isEqualTo(1);
        Optional<Feature> oInput1 = counterfactualPrediction.getInput().getFeatures().stream().filter(f -> f.getName().equals("input1")).findFirst();
        assertThat(oInput1.isPresent()).isTrue();
        Feature input1 = oInput1.get();
        assertThat(input1.getType()).isEqualTo(Type.NUMBER);
        assertThat(input1.getValue().asNumber()).isEqualTo(20);
        assertThat(counterfactualPrediction.getInput().getFeatures().stream().allMatch(f -> f.getDomain().isEmpty())).isTrue();
        assertThat(counterfactualPrediction.getInput().getFeatures().stream().allMatch(Feature::isConstrained)).isTrue();

        assertThat(counterfactualPrediction.getOutput().getOutputs().isEmpty()).isTrue();

        assertThat(request.getMaxRunningTimeSeconds()).isEqualTo(counterfactualPrediction.getMaxRunningTimeSeconds());
    }

    @Test
    public void testGetPredictionWithStructuredInputModel() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                List.of(new NamedTypedValue("input1",
                        new StructureValue("number", Map.of("input2b", new UnitValue("number", new IntNode(55)))))),
                Collections.emptyList(),
                Collections.emptyList(),
                MAX_RUNNING_TIME_SECONDS);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> handler.getPrediction(request));
    }

    @Test
    public void testGetPredictionWithCollectionInputModel() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                List.of(new NamedTypedValue("input1",
                        new CollectionValue("number", List.of(new UnitValue("number", new IntNode(100)))))),
                Collections.emptyList(),
                Collections.emptyList(),
                MAX_RUNNING_TIME_SECONDS);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> handler.getPrediction(request));
    }

    @Test
    public void testGetPredictionWithFlatOutputModel() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                Collections.emptyList(),
                List.of(new NamedTypedValue("output1",
                        new UnitValue("number", new IntNode(20)))),
                Collections.emptyList(),
                MAX_RUNNING_TIME_SECONDS);

        Prediction prediction = handler.getPrediction(request);
        assertThat((prediction instanceof CounterfactualPrediction)).isTrue();
        CounterfactualPrediction counterfactualPrediction = (CounterfactualPrediction) prediction;

        assertThat(counterfactualPrediction.getOutput().getOutputs().size()).isEqualTo(1);
        Optional<Output> oOutput1 = counterfactualPrediction.getOutput().getOutputs().stream().filter(f -> f.getName().equals("output1")).findFirst();
        assertThat(oOutput1.isPresent()).isTrue();
        Output output1 = oOutput1.get();
        assertThat(output1.getType()).isEqualTo(Type.NUMBER);
        assertThat(output1.getValue().asNumber()).isEqualTo(20);

        assertThat(counterfactualPrediction.getInput().getFeatures().isEmpty()).isTrue();

        assertThat(request.getMaxRunningTimeSeconds()).isEqualTo(counterfactualPrediction.getMaxRunningTimeSeconds());
    }

    @Test
    public void testGetPredictionWithFlatOutputModelReordered() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                Collections.emptyList(),
                List.of(new NamedTypedValue("inputsAreValid",
                        new UnitValue("boolean", BooleanNode.FALSE)),
                        new NamedTypedValue("canRequestLoan",
                                new UnitValue("booelan", BooleanNode.TRUE)),
                        new NamedTypedValue("my-scoring-function",
                                new UnitValue("number", new DoubleNode(0.85)))),
                Collections.emptyList(),
                MAX_RUNNING_TIME_SECONDS);

        Prediction prediction = handler.getPrediction(request);
        assertThat((prediction instanceof CounterfactualPrediction)).isTrue();
        CounterfactualPrediction counterfactualPrediction = (CounterfactualPrediction) prediction;

        List<Output> outputs = counterfactualPrediction.getOutput().getOutputs();
        assertThat(outputs.size()).isEqualTo(3);
        Output output1 = outputs.get(0);
        assertThat(output1.getName()).isEqualTo("my-scoring-function");
        assertThat(output1.getType()).isEqualTo(Type.NUMBER);
        assertThat(output1.getValue().asNumber()).isEqualTo(0.85);

        Output output2 = outputs.get(1);
        assertThat(output2.getName()).isEqualTo("inputsAreValid");
        assertThat(output2.getType()).isEqualTo(Type.BOOLEAN);
        assertThat(output2.getValue().getUnderlyingObject()).isEqualTo(Boolean.FALSE);

        Output output3 = outputs.get(2);
        assertThat(output3.getName()).isEqualTo("canRequestLoan");
        assertThat(output3.getType()).isEqualTo(Type.BOOLEAN);
        assertThat(output3.getValue().getUnderlyingObject()).isEqualTo(Boolean.TRUE);

        assertThat(counterfactualPrediction.getInput().getFeatures().isEmpty()).isTrue();

        assertThat(request.getMaxRunningTimeSeconds()).isEqualTo(counterfactualPrediction.getMaxRunningTimeSeconds());
    }

    @Test
    public void testGetPredictionWithStructuredOutputModel() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                Collections.emptyList(),
                List.of(new NamedTypedValue("input1",
                        new StructureValue("number", Map.of("input2b", new UnitValue("number", new IntNode(55)))))),
                Collections.emptyList(),
                MAX_RUNNING_TIME_SECONDS);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> handler.getPrediction(request));
    }

    @Test
    public void testGetPredictionWithCollectionOutputModel() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                Collections.emptyList(),
                List.of(new NamedTypedValue("input1",
                        new CollectionValue("number", List.of(new UnitValue("number", new IntNode(100)))))),
                Collections.emptyList(),
                MAX_RUNNING_TIME_SECONDS);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> handler.getPrediction(request));
    }

    @Test
    public void testGetPredictionWithFlatSearchDomainsFixed() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                List.of(new NamedTypedValue("output1", new UnitValue("number", new IntNode(25)))),
                Collections.emptyList(),
                List.of(new CounterfactualSearchDomain("output1",
                        new CounterfactualSearchDomainUnitValue("number",
                                "number",
                                true,
                                new CounterfactualDomainRange(new IntNode(10), new IntNode(20))))),
                MAX_RUNNING_TIME_SECONDS);

        Prediction prediction = handler.getPrediction(request);
        assertThat((prediction instanceof CounterfactualPrediction)).isTrue();
        CounterfactualPrediction counterfactualPrediction = (CounterfactualPrediction) prediction;

        assertThat(counterfactualPrediction.getInput().getFeatures().size()).isEqualTo(1);
        Feature feature1 = counterfactualPrediction.getInput().getFeatures().get(0);
        assertThat((feature1.getDomain() instanceof EmptyFeatureDomain)).isTrue();

        assertThat(request.getMaxRunningTimeSeconds()).isEqualTo(counterfactualPrediction.getMaxRunningTimeSeconds());
    }

    @Test
    public void testGetPredictionWithFlatSearchDomainsNotFixed() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                List.of(new NamedTypedValue("output1", new UnitValue("number", new IntNode(25)))),
                Collections.emptyList(),
                List.of(new CounterfactualSearchDomain("output1",
                        new CounterfactualSearchDomainUnitValue("number",
                                "number",
                                false,
                                new CounterfactualDomainRange(new IntNode(10), new IntNode(20))))),
                MAX_RUNNING_TIME_SECONDS);

        Prediction prediction = handler.getPrediction(request);
        assertThat((prediction instanceof CounterfactualPrediction)).isTrue();
        CounterfactualPrediction counterfactualPrediction = (CounterfactualPrediction) prediction;

        assertThat(counterfactualPrediction.getInput().getFeatures().size()).isEqualTo(1);
        Feature feature1 = counterfactualPrediction.getInput().getFeatures().get(0);
        assertThat((feature1.getDomain() instanceof NumericalFeatureDomain)).isTrue();

        final NumericalFeatureDomain domain = (NumericalFeatureDomain) feature1.getDomain();
        assertThat(domain.getLowerBound()).isEqualTo(10);
        assertThat(domain.getUpperBound()).isEqualTo(20);

        assertThat(request.getMaxRunningTimeSeconds()).isEqualTo(counterfactualPrediction.getMaxRunningTimeSeconds());
    }

    @Test
    public void testGetPredictionWithStructuredSearchDomains() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(new CounterfactualSearchDomain("input1",
                        new CounterfactualSearchDomainStructureValue("number",
                                Map.of("input2b",
                                        new CounterfactualSearchDomainUnitValue("number",
                                                "number",
                                                true,
                                                new CounterfactualDomainRange(new IntNode(10), new IntNode(20))))))),
                MAX_RUNNING_TIME_SECONDS);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> handler.getPrediction(request));
    }

    @Test
    public void testGetPredictionWithCollectionSearchDomains() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(new CounterfactualSearchDomain("input1",
                        new CounterfactualSearchDomainCollectionValue("number",
                                List.of(new CounterfactualSearchDomainUnitValue("number",
                                        "number",
                                        true,
                                        new CounterfactualDomainRange(new IntNode(10), new IntNode(20))))))),
                MAX_RUNNING_TIME_SECONDS);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> handler.getPrediction(request));
    }

    @Test
    public void testCreateSucceededResult() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                MAX_RUNNING_TIME_SECONDS);

        List<CounterfactualEntity> entities = List.of(DoubleEntity.from(new Feature("input1", Type.NUMBER, new Value(123.0d)), 0, 1000));
        CounterfactualResult counterfactuals = new CounterfactualResult(entities,
                entities.stream().map(CounterfactualEntity::asFeature).collect(Collectors.toList()),
                List.of(new PredictionOutput(List.of(new Output("output1", Type.NUMBER, new Value(555.0d), 1.0)))),
                true,
                UUID.fromString(SOLUTION_ID),
                UUID.fromString(EXECUTION_ID),
                0);

        BaseExplainabilityResult base = handler.createSucceededResult(request, counterfactuals);
        assertThat((base instanceof CounterfactualExplainabilityResult)).isTrue();
        CounterfactualExplainabilityResult result = (CounterfactualExplainabilityResult) base;

        assertThat(result.getStatus()).isEqualTo(ExplainabilityStatus.SUCCEEDED);
        assertThat(result.getStage()).isEqualTo(CounterfactualExplainabilityResult.Stage.FINAL);
        assertThat(result.getExecutionId()).isEqualTo(EXECUTION_ID);
        assertThat(result.getCounterfactualId()).isEqualTo(COUNTERFACTUAL_ID);
        assertThat(result.getInputs().size()).isEqualTo(1);
        assertThat(result.getInputs().stream().anyMatch(i -> i.getName().equals("input1"))).isTrue();
        NamedTypedValue input1 = result.getInputs().iterator().next();
        assertThat(input1.getValue().getType()).isEqualTo(Double.class.getSimpleName());
        assertThat(input1.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(input1.getValue().toUnit().getValue().asDouble()).isEqualTo(123.0);

        assertThat(result.getOutputs().size()).isEqualTo(1);
        assertThat(result.getOutputs().stream().anyMatch(o -> o.getName().equals("output1"))).isTrue();
        NamedTypedValue output1 = result.getOutputs().iterator().next();
        assertThat(output1.getValue().getType()).isEqualTo(Double.class.getSimpleName());
        assertThat(output1.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(output1.getValue().toUnit().getValue().asDouble()).isEqualTo(555.0);
    }

    @Test
    public void testCreateSucceededResultWithNullPredictions() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                MAX_RUNNING_TIME_SECONDS);

        CounterfactualResult counterfactuals = new CounterfactualResult(Collections.emptyList(),
                Collections.emptyList(),
                null,
                true,
                UUID.fromString(SOLUTION_ID),
                UUID.fromString(EXECUTION_ID),
                0);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> handler.createSucceededResult(request, counterfactuals));
    }

    @Test
    public void testCreateSucceededResultWithEmptyPredictions() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                MAX_RUNNING_TIME_SECONDS);

        CounterfactualResult counterfactuals = new CounterfactualResult(Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                true,
                UUID.fromString(SOLUTION_ID),
                UUID.fromString(EXECUTION_ID),
                0);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> handler.createSucceededResult(request, counterfactuals));
    }

    @Test
    public void testCreateSucceededResultWithMoreThanOnePrediction() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                MAX_RUNNING_TIME_SECONDS);

        CounterfactualResult counterfactuals = new CounterfactualResult(Collections.emptyList(), Collections.emptyList(),
                List.of(new PredictionOutput(List.of(new Output("output1", Type.NUMBER, new Value(555.0d), 1.0))),
                        new PredictionOutput(List.of(new Output("output2", Type.NUMBER, new Value(777.0d), 2.0)))),
                true,
                UUID.fromString(SOLUTION_ID),
                UUID.fromString(EXECUTION_ID),
                0);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> handler.createSucceededResult(request, counterfactuals));
    }

    @Test
    public void testCreateIntermediateResult() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                MAX_RUNNING_TIME_SECONDS);

        List<CounterfactualEntity> entities = List.of(DoubleEntity.from(new Feature("input1", Type.NUMBER, new Value(123.0d)), 0, 1000));
        CounterfactualResult counterfactuals = new CounterfactualResult(entities, entities.stream().map(
                CounterfactualEntity::asFeature).collect(
                        Collectors.toList()),
                List.of(new PredictionOutput(List.of(new Output("output1", Type.NUMBER, new Value(555.0d), 1.0)))),
                true,
                UUID.fromString(SOLUTION_ID),
                UUID.fromString(EXECUTION_ID),
                0);

        BaseExplainabilityResult base = handler.createIntermediateResult(request, counterfactuals);
        assertThat((base instanceof CounterfactualExplainabilityResult)).isTrue();
        CounterfactualExplainabilityResult result = (CounterfactualExplainabilityResult) base;

        assertThat(result.getStatus()).isEqualTo(ExplainabilityStatus.SUCCEEDED);
        assertThat(result.getStage()).isEqualTo(CounterfactualExplainabilityResult.Stage.INTERMEDIATE);
        assertThat(result.getExecutionId()).isEqualTo(EXECUTION_ID);
        assertThat(result.getCounterfactualId()).isEqualTo(COUNTERFACTUAL_ID);
        assertThat(result.getInputs().size()).isEqualTo(1);
        assertThat(result.getInputs().stream().anyMatch(i -> i.getName().equals("input1"))).isTrue();
        NamedTypedValue input1 = result.getInputs().iterator().next();
        assertThat(input1.getValue().getType()).isEqualTo(Double.class.getSimpleName());
        assertThat(input1.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(input1.getValue().toUnit().getValue().asDouble()).isEqualTo(123.0);

        assertThat(result.getOutputs().size()).isEqualTo(1);
        assertThat(result.getOutputs().stream().anyMatch(o -> o.getName().equals("output1"))).isTrue();
        NamedTypedValue output1 = result.getOutputs().iterator().next();
        assertThat(output1.getValue().getType()).isEqualTo(Double.class.getSimpleName());
        assertThat(output1.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(output1.getValue().toUnit().getValue().asDouble()).isEqualTo(555.0);
    }

    @Test
    public void testCreateFailedResult() {
        CounterfactualExplainabilityRequest request = new CounterfactualExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                COUNTERFACTUAL_ID,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                MAX_RUNNING_TIME_SECONDS);

        BaseExplainabilityResult base = handler.createFailedResult(request, new NullPointerException("Something went wrong"));
        assertThat((base instanceof CounterfactualExplainabilityResult)).isTrue();
        CounterfactualExplainabilityResult result = (CounterfactualExplainabilityResult) base;

        assertThat(result.getStatus()).isEqualTo(ExplainabilityStatus.FAILED);
        assertThat(result.getStatusDetails()).isEqualTo("Something went wrong");
        assertThat(result.getExecutionId()).isEqualTo(EXECUTION_ID);
        assertThat(result.getCounterfactualId()).isEqualTo(COUNTERFACTUAL_ID);
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
        Consumer<CounterfactualResult> callback = mock(Consumer.class);

        handler.explainAsync(prediction, predictionProvider, callback);

        verify(explainer).explainAsync(eq(prediction), eq(predictionProvider), eq(callback));
    }
}
