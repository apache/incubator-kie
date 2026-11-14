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
package org.kie.kogito.trusty.service.common.handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.explainability.api.ExplainabilityStatus;
import org.kie.kogito.explainability.api.FeatureImportanceModel;
import org.kie.kogito.explainability.api.LIMEExplainabilityResult;
import org.kie.kogito.explainability.api.SaliencyModel;
import org.kie.kogito.tracing.typedvalue.UnitValue;
import org.kie.kogito.trusty.service.common.TrustyService;
import org.kie.kogito.trusty.service.common.responses.SalienciesResponse;
import org.kie.kogito.trusty.service.common.responses.SaliencyResponse;
import org.kie.kogito.trusty.storage.api.model.decision.Decision;
import org.kie.kogito.trusty.storage.api.model.decision.DecisionOutcome;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.node.IntNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LIMESaliencyConverterTest {

    private static final String EXECUTION_ID = "executionId";

    private LIMESaliencyConverter converter;

    @Mock
    private TrustyService trustyService;

    @BeforeEach
    public void setup() {
        converter = new LIMESaliencyConverter(trustyService);
    }

    @Test
    public void testFromResult_DecisionExists() {
        LIMEExplainabilityResult result = LIMEExplainabilityResult.buildSucceeded(EXECUTION_ID,
                List.of(new SaliencyModel("outcomeName1",
                        List.of(new FeatureImportanceModel("feature1a", 1.0),
                                new FeatureImportanceModel("feature1b", 2.0))),
                        new SaliencyModel("outcomeName2",
                                List.of(new FeatureImportanceModel("feature2", 3.0)))));

        Decision decision = new Decision(EXECUTION_ID,
                "sourceUrl",
                "serviceUrl",
                0L,
                true,
                "executorName",
                "executorModelName",
                "executorModelNamespace",
                new ArrayList<>(),
                new ArrayList<>());
        decision.getOutcomes().add(new DecisionOutcome("outcomeId1",
                "outcomeName1",
                ExplainabilityStatus.SUCCEEDED.name(),
                new UnitValue("type", new IntNode(1)),
                Collections.emptyList(),
                Collections.emptyList()));
        decision.getOutcomes().add(new DecisionOutcome("outcomeId2",
                "outcomeName2",
                ExplainabilityStatus.SUCCEEDED.name(),
                new UnitValue("type2", new IntNode(2)),
                Collections.emptyList(),
                Collections.emptyList()));
        when(trustyService.getDecisionById(eq(EXECUTION_ID))).thenReturn(decision);

        SalienciesResponse response = converter.fromResult(EXECUTION_ID, result);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ExplainabilityStatus.SUCCEEDED.name());
        assertThat(response.getSaliencies().size()).isEqualTo(2);

        List<SaliencyResponse> saliencyResponses = new ArrayList<>(response.getSaliencies());

        SaliencyResponse saliencyResponse1 = saliencyResponses.get(0);
        assertThat(saliencyResponse1.getOutcomeId()).isEqualTo("outcomeId1");
        assertThat(saliencyResponse1.getOutcomeName()).isEqualTo("outcomeName1");
        assertThat(saliencyResponse1.getFeatureImportance().size()).isEqualTo(2);

        Optional<FeatureImportanceModel> oFeatureImportance1Model1 = saliencyResponse1.getFeatureImportance().stream().filter(fim -> fim.getFeatureName().equals("feature1a")).findFirst();
        assertThat(oFeatureImportance1Model1.isPresent()).isTrue();
        assertThat(oFeatureImportance1Model1.get().getFeatureScore()).isEqualTo(1.0);
        Optional<FeatureImportanceModel> oFeatureImportance2Model1 = saliencyResponse1.getFeatureImportance().stream().filter(fim -> fim.getFeatureName().equals("feature1b")).findFirst();
        assertThat(oFeatureImportance2Model1.isPresent()).isTrue();
        assertThat(oFeatureImportance2Model1.get().getFeatureScore()).isEqualTo(2.0);

        SaliencyResponse saliencyResponse2 = saliencyResponses.get(1);
        assertThat(saliencyResponse2.getOutcomeId()).isEqualTo("outcomeId2");
        assertThat(saliencyResponse2.getOutcomeName()).isEqualTo("outcomeName2");
        assertThat(saliencyResponse2.getFeatureImportance().size()).isEqualTo(1);

        Optional<FeatureImportanceModel> oFeatureImportance1Model2 = saliencyResponse2.getFeatureImportance().stream().filter(fim -> fim.getFeatureName().equals("feature2")).findFirst();
        assertThat(oFeatureImportance1Model2.isPresent()).isTrue();
        assertThat(oFeatureImportance1Model2.get().getFeatureScore()).isEqualTo(3.0);
    }

    @Test
    public void testFromResult_DecisionExists_WhenOutcomeNameNotFound() {
        LIMEExplainabilityResult result = LIMEExplainabilityResult.buildSucceeded(EXECUTION_ID,
                List.of(new SaliencyModel("outcomeName1",
                        List.of(new FeatureImportanceModel("feature1", 1.0))),
                        new SaliencyModel("outcomeName2",
                                List.of(new FeatureImportanceModel("feature2", 2.0)))));

        Decision decision = new Decision(EXECUTION_ID,
                "sourceUrl",
                "serviceUrl",
                0L,
                true,
                "executorName",
                "executorModelName",
                "executorModelNamespace",
                new ArrayList<>(),
                new ArrayList<>());
        decision.getOutcomes().add(new DecisionOutcome("outcomeId1",
                "outcomeName1",
                ExplainabilityStatus.SUCCEEDED.name(),
                new UnitValue("type", new IntNode(1)),
                Collections.emptyList(),
                Collections.emptyList()));
        decision.getOutcomes().add(new DecisionOutcome("outcomeId2",
                "outcomeNameX",
                ExplainabilityStatus.SUCCEEDED.name(),
                new UnitValue("type2", new IntNode(2)),
                Collections.emptyList(),
                Collections.emptyList()));
        when(trustyService.getDecisionById(eq(EXECUTION_ID))).thenReturn(decision);

        SalienciesResponse response = converter.fromResult(EXECUTION_ID, result);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ExplainabilityStatus.SUCCEEDED.name());
        assertThat(response.getSaliencies().size()).isEqualTo(1);

        List<SaliencyResponse> saliencyResponses = new ArrayList<>(response.getSaliencies());

        SaliencyResponse saliencyResponse1 = saliencyResponses.get(0);
        assertThat(saliencyResponse1.getOutcomeId()).isEqualTo("outcomeId1");
        assertThat(saliencyResponse1.getOutcomeName()).isEqualTo("outcomeName1");
        assertThat(saliencyResponse1.getFeatureImportance().size()).isEqualTo(1);

        Optional<FeatureImportanceModel> oFeatureImportance1Model1 = saliencyResponse1.getFeatureImportance().stream().filter(fim -> fim.getFeatureName().equals("feature1")).findFirst();
        assertThat(oFeatureImportance1Model1.isPresent()).isTrue();
        assertThat(oFeatureImportance1Model1.get().getFeatureScore()).isEqualTo(1.0);
    }

    @Test
    public void testFromResult_DecisionNotExists() {
        LIMEExplainabilityResult result = LIMEExplainabilityResult.buildSucceeded(EXECUTION_ID,
                List.of(new SaliencyModel("outcomeName1",
                        List.of(new FeatureImportanceModel("feature1a", 1.0),
                                new FeatureImportanceModel("feature1b", 2.0))),
                        new SaliencyModel("outcomeName2",
                                List.of(new FeatureImportanceModel("feature2", 3.0)))));

        when(trustyService.getDecisionById(eq(EXECUTION_ID))).thenThrow(new IllegalArgumentException());

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> converter.fromResult(EXECUTION_ID, result));
    }
}
