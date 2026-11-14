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
package org.kie.kogito.trusty.service.common;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.explainability.api.CounterfactualDomain;
import org.kie.kogito.explainability.api.CounterfactualDomainCategorical;
import org.kie.kogito.explainability.api.CounterfactualDomainRange;
import org.kie.kogito.explainability.api.CounterfactualExplainabilityRequest;
import org.kie.kogito.explainability.api.CounterfactualExplainabilityResult;
import org.kie.kogito.explainability.api.CounterfactualSearchDomain;
import org.kie.kogito.explainability.api.ExplainabilityStatus;
import org.kie.kogito.explainability.api.FeatureImportanceModel;
import org.kie.kogito.explainability.api.LIMEExplainabilityResult;
import org.kie.kogito.explainability.api.NamedTypedValue;
import org.kie.kogito.explainability.api.SaliencyModel;
import org.kie.kogito.tracing.typedvalue.UnitValue;
import org.kie.kogito.trusty.service.common.models.MatchedExecutionHeaders;
import org.kie.kogito.trusty.storage.api.model.decision.DMNModelMetadata;
import org.kie.kogito.trusty.storage.api.model.decision.DMNModelWithMetadata;
import org.kie.kogito.trusty.storage.api.model.decision.Decision;
import org.kie.kogito.trusty.storage.api.model.decision.DecisionInput;
import org.kie.kogito.trusty.storage.api.model.decision.DecisionOutcome;
import org.kie.kogito.trusty.storage.common.TrustyStorageService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.kie.kogito.trusty.service.common.TypedValueTestUtils.buildGoalUnit;
import static org.kie.kogito.trusty.service.common.TypedValueTestUtils.buildSearchDomainUnit;

public abstract class AbstractTrustyServiceIT {

    @Inject
    TrustyService trustyService;

    @Inject
    TrustyStorageService trustyStorageService;

    @BeforeEach
    public void setup() {
        trustyStorageService.getCounterfactualRequestStorage().clear();
        trustyStorageService.getCounterfactualResultStorage().clear();
        trustyStorageService.getLIMEResultStorage().clear();
        trustyStorageService.getDecisionsStorage().clear();
        trustyStorageService.getModelStorage(DMNModelWithMetadata.class).clear();
    }

    @Test
    public void testStoreAndRetrieveExecution() {
        storeExecution("myExecution", 1591692958000L);

        OffsetDateTime from = OffsetDateTime.ofInstant(Instant.ofEpochMilli(1591692957000L), ZoneOffset.UTC);
        OffsetDateTime to = OffsetDateTime.ofInstant(Instant.ofEpochMilli(1591692959000L), ZoneOffset.UTC);
        MatchedExecutionHeaders result = trustyService.getExecutionHeaders(from, to, 100, 0, "");
        assertThat(result.getExecutions().size()).isEqualTo(1);
        assertThat(result.getExecutions().get(0).getExecutionId()).isEqualTo("myExecution");
    }

    @Test
    public void givenTwoExecutionsWhenTheQueryExcludesOneExecutionThenOnlyOneExecutionIsReturned() {
        storeExecution("myExecution", 1591692950000L);
        storeExecution("executionId2", 1591692958000L);

        OffsetDateTime from = OffsetDateTime.ofInstant(Instant.ofEpochMilli(1591692940000L), ZoneOffset.UTC);
        OffsetDateTime to = OffsetDateTime.ofInstant(Instant.ofEpochMilli(1591692955000L), ZoneOffset.UTC);
        MatchedExecutionHeaders result = trustyService.getExecutionHeaders(from, to, 100, 0, "");
        assertThat(result.getExecutions().size()).isEqualTo(1);
        assertThat(result.getExecutions().get(0).getExecutionId()).isEqualTo("myExecution");
    }

    @Test
    public void givenTwoExecutionsWhenThePrefixIsUsedThenOnlyOneExecutionIsReturned() {
        storeExecution("myExecution", 1591692950000L);
        storeExecution("executionId2", 1591692958000L);

        OffsetDateTime from = OffsetDateTime.ofInstant(Instant.ofEpochMilli(1591692940000L), ZoneOffset.UTC);
        OffsetDateTime to = OffsetDateTime.ofInstant(Instant.ofEpochMilli(1591692959000L), ZoneOffset.UTC);
        MatchedExecutionHeaders result = trustyService.getExecutionHeaders(from, to, 100, 0, "my");
        assertThat(result.getExecutions().size()).isEqualTo(1);
        assertThat(result.getExecutions().get(0).getExecutionId()).isEqualTo("myExecution");
    }

    @Test
    public void givenAnExecutionWhenGetDecisionByIdIsCalledThenTheExecutionIsReturned() {
        String executionId = "myExecution";
        storeExecution(executionId, 1591692950000L);

        Decision result = trustyService.getDecisionById(executionId);
        assertThat(result.getExecutionId()).isEqualTo(executionId);
    }

    @Test
    public void givenAnExecutionWhenGetDecisionByIdThenTheComponentsInUnitTypesIsNull() {
        String executionId = "myExecution";
        storeExecution(executionId, 1591692950000L);

        Decision result = trustyService.getDecisionById(executionId);
        assertThat(result.getInputs().stream().findFirst().get().getValue().isUnit()).isTrue();
    }

    @Test
    public void givenADuplicatedDecisionWhenTheDecisionIsStoredThenAnExceptionIsRaised() {
        String executionId = "myExecution";
        storeExecution(executionId, 1591692950000L);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> storeExecution(executionId, 1591692950000L));
    }

    @Test
    public void givenNoExecutionsWhenADecisionIsRetrievedThenAnExceptionIsRaised() {
        String executionId = "myExecution";
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> trustyService.getDecisionById(executionId));
    }

    @Test
    public void givenAModelWhenGetModelByIdIsCalledThenTheModelIsReturned() {
        String model = "definition";
        storeModel(model);

        DMNModelWithMetadata result = getModel();
        assertThat(result.getModel()).isEqualTo(model);
    }

    @Test
    public void givenADuplicatedModelWhenTheModelIsStoredThenAnExceptionIsRaised() {
        String model = "definition";
        storeModel(model);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> storeModel(model));
    }

    @Test
    public void givenNoModelsWhenAModelIsRetrievedThenAnExceptionIsRaised() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(this::getModel);
    }

    @Test
    public void searchExecutionsByPrefixTest() {
        String executionId = "da8ad1e9-a679-4ded-a6d5-53fd019e7002";
        long executionTimestamp = 1617270053L;
        Instant instant = Instant.ofEpochMilli(executionTimestamp);
        storeExecution(executionId, executionTimestamp);

        MatchedExecutionHeaders executionHeaders = trustyService.getExecutionHeaders(
                OffsetDateTime.ofInstant(instant.minusMillis(1), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(instant.plusMillis(1), ZoneOffset.UTC),
                10,
                0,
                "da8ad1e9-a679");

        assertThat(executionHeaders.getExecutions().size()).isEqualTo(1);
    }

    @Test
    public void testCounterfactuals_StoreSingleAndRetrieveSingleWithEmptyDefinition() {
        String executionId = "myCFExecution1";
        storeExecution(executionId, 0L);

        // The Goals structures must be comparable to the original decisions outcomes.
        // The Search Domain structures must be identical those of the original decision inputs.
        CounterfactualSearchDomain searchDomain = buildSearchDomainUnit("test",
                "number",
                new CounterfactualDomainRange(new IntNode(1), new IntNode(2)));

        CounterfactualExplainabilityRequest request = trustyService.requestCounterfactuals(executionId, Collections.emptyList(), Collections.singletonList(searchDomain));

        assertThat(request).isNotNull();
        assertThat(executionId).isEqualTo(request.getExecutionId());
        assertThat(request.getCounterfactualId()).isNotNull();

        CounterfactualExplainabilityRequest result = trustyService.getCounterfactualRequest(executionId, request.getCounterfactualId());
        assertThat(result).isNotNull();
        assertThat(result.getExecutionId()).isEqualTo(request.getExecutionId());
        assertThat(result.getCounterfactualId()).isEqualTo(request.getCounterfactualId());
    }

    @Test
    public void testCounterfactuals_StoreMultipleAndRetrieveAllWithEmptyDefinition() {
        String executionId = "myCFExecution2";
        storeExecution(executionId, 0L);

        // The Goals structures must be comparable to the original decisions outcomes.
        // The Search Domain structures must be identical those of the original decision inputs.
        CounterfactualSearchDomain searchDomain = buildSearchDomainUnit("test",
                "number",
                new CounterfactualDomainRange(new IntNode(1), new IntNode(2)));

        CounterfactualExplainabilityRequest request1 = trustyService.requestCounterfactuals(executionId, Collections.emptyList(), Collections.singletonList(searchDomain));
        CounterfactualExplainabilityRequest request2 = trustyService.requestCounterfactuals(executionId, Collections.emptyList(), Collections.singletonList(searchDomain));

        List<CounterfactualExplainabilityRequest> result = trustyService.getCounterfactualRequests(executionId);
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.stream().anyMatch(c -> c.getCounterfactualId().equals(request1.getCounterfactualId()))).isTrue();
        assertThat(result.stream().anyMatch(c -> c.getCounterfactualId().equals(request2.getCounterfactualId()))).isTrue();
    }

    @Test
    public void testCounterfactuals_StoreMultipleAndRetrieveSingleWithEmptyDefinition() {
        String executionId = "myCFExecution3";
        storeExecution(executionId, 0L);

        // The Goals structures must be comparable to the original decisions outcomes.
        // The Search Domain structures must be identical those of the original decision inputs.
        CounterfactualSearchDomain searchDomain = buildSearchDomainUnit("test",
                "number",
                new CounterfactualDomainRange(new IntNode(1), new IntNode(2)));

        CounterfactualExplainabilityRequest request1 = trustyService.requestCounterfactuals(executionId, Collections.emptyList(), Collections.singletonList(searchDomain));
        CounterfactualExplainabilityRequest request2 = trustyService.requestCounterfactuals(executionId, Collections.emptyList(), Collections.singletonList(searchDomain));

        CounterfactualExplainabilityRequest result1 = trustyService.getCounterfactualRequest(executionId, request1.getCounterfactualId());
        assertThat(result1).isNotNull();
        assertThat(result1.getCounterfactualId()).isEqualTo(request1.getCounterfactualId());

        CounterfactualExplainabilityRequest result2 = trustyService.getCounterfactualRequest(executionId, request2.getCounterfactualId());
        assertThat(result2).isNotNull();
        assertThat(result2.getCounterfactualId()).isEqualTo(request2.getCounterfactualId());
    }

    @Test
    public void testCounterfactuals_StoreMultipleForMultipleExecutionsAndRetrieveSingleWithEmptyDefinition() {
        String executionId1 = "myCFExecution1";
        storeExecution(executionId1, 0L);

        String executionId2 = "myCFExecution2";
        storeExecution(executionId2, 0L);

        // The Goals structures must be comparable to the original decisions outcomes.
        // The Search Domain structures must be identical those of the original decision inputs.
        CounterfactualSearchDomain searchDomain = buildSearchDomainUnit("test",
                "number",
                new CounterfactualDomainRange(new IntNode(1), new IntNode(2)));

        CounterfactualExplainabilityRequest request1 = trustyService.requestCounterfactuals(executionId1, Collections.emptyList(), Collections.singletonList(searchDomain));
        assertThat(request1).isNotNull();
        assertThat(executionId1).isEqualTo(request1.getExecutionId());
        assertThat(request1.getCounterfactualId()).isNotNull();

        CounterfactualExplainabilityRequest request2 = trustyService.requestCounterfactuals(executionId2, Collections.emptyList(), Collections.singletonList(searchDomain));
        assertThat(request2).isNotNull();
        assertThat(executionId2).isEqualTo(request2.getExecutionId());
        assertThat(request2.getCounterfactualId()).isNotNull();

        CounterfactualExplainabilityRequest result1 = trustyService.getCounterfactualRequest(executionId1, request1.getCounterfactualId());
        assertThat(result1).isNotNull();
        assertThat(result1.getExecutionId()).isEqualTo(request1.getExecutionId());
        assertThat(result1.getCounterfactualId()).isEqualTo(request1.getCounterfactualId());
    }

    @Test
    public void testCounterfactuals_StoreSingleAndRetrieveSingleWithGoals() {
        String executionId = "myCFExecution1";
        storeExecutionWithOutcomes(executionId, 0L);

        // The Goals structures must be comparable to the original decisions outcomes.
        // The Search Domain structures must be identical those of the original decision inputs.
        CounterfactualSearchDomain searchDomain = buildSearchDomainUnit("test",
                "number",
                new CounterfactualDomainRange(new IntNode(1), new IntNode(2)));

        NamedTypedValue goal1 = buildGoalUnit("outcome1", "number", new IntNode(25));
        NamedTypedValue goal2 = buildGoalUnit("outcome2", "string", new TextNode("cheese"));

        CounterfactualExplainabilityRequest request = trustyService.requestCounterfactuals(executionId, List.of(goal1, goal2), Collections.singletonList(searchDomain));

        assertThat(request).isNotNull();
        assertThat(executionId).isEqualTo(request.getExecutionId());
        assertThat(request.getCounterfactualId()).isNotNull();
        assertThat(request.getGoals().size()).isEqualTo(2);
        List<NamedTypedValue> requestGoals = new ArrayList<>(request.getGoals());
        assertCounterfactualGoal(goal1, requestGoals.get(0));
        assertCounterfactualGoal(goal2, requestGoals.get(1));

        CounterfactualExplainabilityRequest result = trustyService.getCounterfactualRequest(executionId, request.getCounterfactualId());
        assertThat(result).isNotNull();
        assertThat(result.getExecutionId()).isEqualTo(request.getExecutionId());
        assertThat(result.getCounterfactualId()).isEqualTo(request.getCounterfactualId());
        assertThat(result.getGoals().size()).isEqualTo(2);
        List<NamedTypedValue> resultGoals = new ArrayList<>(request.getGoals());
        assertCounterfactualGoal(goal1, resultGoals.get(0));
        assertCounterfactualGoal(goal2, resultGoals.get(1));
    }

    private void assertCounterfactualGoal(NamedTypedValue expectedGoal, NamedTypedValue actualGoal) {
        assertThat(actualGoal.getName()).isEqualTo(expectedGoal.getName());
        assertThat(actualGoal.getValue().getType()).isEqualTo(expectedGoal.getValue().getType());
        assertThat(actualGoal.getValue().toUnit().getValue()).isEqualTo(expectedGoal.getValue().toUnit().getValue());
    }

    @Test
    public void testCounterfactuals_StoreSingleAndRetrieveSingleWithSearchDomainRange() {
        String executionId = "myCFExecution1";
        storeExecution(executionId, 0L);

        // The Goals structures must be comparable to the original decisions outcomes.
        // The Search Domain structures must be identical those of the original decision inputs.
        CounterfactualSearchDomain searchDomain = buildSearchDomainUnit("test",
                "number",
                new CounterfactualDomainRange(new IntNode(1), new IntNode(2)));

        CounterfactualExplainabilityRequest request = trustyService.requestCounterfactuals(executionId, Collections.emptyList(), Collections.singletonList(searchDomain));

        assertThat(request).isNotNull();
        assertThat(executionId).isEqualTo(request.getExecutionId());
        assertThat(request.getCounterfactualId()).isNotNull();
        assertThat(request.getSearchDomains().size()).isEqualTo(1);
        List<CounterfactualSearchDomain> requestSearchDomains = new ArrayList<>(request.getSearchDomains());
        assertCounterfactualSearchDomainRange(searchDomain, requestSearchDomains.get(0));

        CounterfactualExplainabilityRequest result = trustyService.getCounterfactualRequest(executionId, request.getCounterfactualId());
        assertThat(result).isNotNull();
        assertThat(result.getExecutionId()).isEqualTo(request.getExecutionId());
        assertThat(result.getCounterfactualId()).isEqualTo(request.getCounterfactualId());
        assertThat(result.getSearchDomains().size()).isEqualTo(1);
        List<CounterfactualSearchDomain> resultSearchDomains = new ArrayList<>(result.getSearchDomains());
        assertCounterfactualSearchDomainRange(searchDomain, resultSearchDomains.get(0));
    }

    private void assertCounterfactualSearchDomainRange(CounterfactualSearchDomain expectedSearchDomain, CounterfactualSearchDomain actualSearchDomain) {
        assertThat(actualSearchDomain.getName()).isEqualTo(expectedSearchDomain.getName());
        assertThat(actualSearchDomain.getValue().getType()).isEqualTo(expectedSearchDomain.getValue().getType());
        assertThat(actualSearchDomain.getValue().toUnit().isFixed()).isEqualTo(expectedSearchDomain.getValue().toUnit().isFixed());
        assertCounterfactualDomainRange(expectedSearchDomain.getValue().toUnit().getDomain(), actualSearchDomain.getValue().toUnit().getDomain());
    }

    private void assertCounterfactualDomainRange(CounterfactualDomain expectedDomain, CounterfactualDomain actualDomain) {
        assertThat((expectedDomain instanceof CounterfactualDomainRange)).isTrue();
        assertThat((actualDomain instanceof CounterfactualDomainRange)).isTrue();

        CounterfactualDomainRange expectedDomainRange = (CounterfactualDomainRange) expectedDomain;
        CounterfactualDomainRange actualDomainRange = (CounterfactualDomainRange) actualDomain;

        assertThat(actualDomainRange.getLowerBound()).isEqualTo(expectedDomainRange.getLowerBound());
        assertThat(actualDomainRange.getUpperBound()).isEqualTo(expectedDomainRange.getUpperBound());
    }

    @Test
    public void testCounterfactuals_StoreSingleAndRetrieveSingleWithSearchDomainCategorical() {
        String executionId = "myCFExecution1";
        storeExecution(executionId, 0L);

        // The Goals structures must be comparable to the original decisions outcomes.
        // The Search Domain structures must be identical those of the original decision inputs.
        Collection<JsonNode> categories = List.of(new TextNode("1"), new TextNode("2"));
        CounterfactualSearchDomain searchDomain = buildSearchDomainUnit("test",
                "number",
                new CounterfactualDomainCategorical(categories));

        CounterfactualExplainabilityRequest request = trustyService.requestCounterfactuals(executionId, Collections.emptyList(), Collections.singletonList(searchDomain));

        assertThat(request).isNotNull();
        assertThat(executionId).isEqualTo(request.getExecutionId());
        assertThat(request.getCounterfactualId()).isNotNull();
        assertThat(request.getSearchDomains().size()).isEqualTo(1);
        List<CounterfactualSearchDomain> requestSearchDomains = new ArrayList<>(request.getSearchDomains());
        assertCounterfactualSearchDomainCategorical(searchDomain, requestSearchDomains.get(0));

        CounterfactualExplainabilityRequest result = trustyService.getCounterfactualRequest(executionId, request.getCounterfactualId());
        assertThat(result).isNotNull();
        assertThat(result.getExecutionId()).isEqualTo(request.getExecutionId());
        assertThat(result.getCounterfactualId()).isEqualTo(request.getCounterfactualId());
        assertThat(result.getSearchDomains().size()).isEqualTo(1);
        List<CounterfactualSearchDomain> resultSearchDomains = new ArrayList<>(result.getSearchDomains());
        assertCounterfactualSearchDomainCategorical(searchDomain, resultSearchDomains.get(0));
    }

    private void assertCounterfactualSearchDomainCategorical(CounterfactualSearchDomain expectedSearchDomain, CounterfactualSearchDomain actualSearchDomain) {
        assertThat(actualSearchDomain.getName()).isEqualTo(expectedSearchDomain.getName());
        assertThat(actualSearchDomain.getValue().getType()).isEqualTo(expectedSearchDomain.getValue().getType());
        assertThat(actualSearchDomain.getValue().toUnit().isFixed()).isEqualTo(expectedSearchDomain.getValue().toUnit().isFixed());
        assertCounterfactualDomainCategorical(expectedSearchDomain.getValue().toUnit().getDomain(), actualSearchDomain.getValue().toUnit().getDomain());
    }

    private void assertCounterfactualDomainCategorical(CounterfactualDomain expectedDomain, CounterfactualDomain actualDomain) {
        assertThat((expectedDomain instanceof CounterfactualDomainCategorical)).isTrue();
        assertThat((actualDomain instanceof CounterfactualDomainCategorical)).isTrue();

        CounterfactualDomainCategorical expectedDomainCategorical = (CounterfactualDomainCategorical) expectedDomain;
        CounterfactualDomainCategorical actualDomainCategorical = (CounterfactualDomainCategorical) actualDomain;

        assertThat(actualDomainCategorical.getCategories().size()).isEqualTo(expectedDomainCategorical.getCategories().size());
        assertThat(expectedDomainCategorical.getCategories().containsAll(actualDomainCategorical.getCategories())).isTrue();
    }

    @Test
    public void testCounterfactuals_WithDisparateSearchDomainStructure() {
        String executionId = "myCFExecution1";
        storeExecution(executionId, 0L);

        // The Goals structures must be comparable to the original decisions outcomes.
        // The Search Domain structures must be identical those of the original decision inputs.
        // For this test we're providing an incorrect structure for searchDomains but the correct structure for goals.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> trustyService.requestCounterfactuals(executionId, Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    public void testCounterfactuals_WithDisparateGoalsStructure() {
        String executionId = "myCFExecution1";
        storeExecutionWithOutcomes(executionId, 0L);

        // The Goals structures must be comparable to the original decisions outcomes.
        // The Search Domain structures must be identical those of the original decision inputs.
        // For this test we're providing the correct structure for searchDomains but a sub-structure for goals.
        CounterfactualSearchDomain searchDomain = buildSearchDomainUnit("test",
                "number",
                new CounterfactualDomainRange(new IntNode(1), new IntNode(2)));

        CounterfactualExplainabilityRequest request = trustyService.requestCounterfactuals(executionId, Collections.emptyList(), Collections.singletonList(searchDomain));

        assertThat(request).isNotNull();
        assertThat(executionId).isEqualTo(request.getExecutionId());
        assertThat(request.getCounterfactualId()).isNotNull();
        assertThat(request.getGoals().isEmpty()).isTrue();

        CounterfactualExplainabilityRequest result = trustyService.getCounterfactualRequest(executionId, request.getCounterfactualId());
        assertThat(result).isNotNull();
        assertThat(result.getExecutionId()).isEqualTo(request.getExecutionId());
        assertThat(result.getCounterfactualId()).isEqualTo(request.getCounterfactualId());
        assertThat(result.getGoals().isEmpty()).isTrue();
    }

    @Test
    public void testStoreExplainabilityResult_LIME() {
        String executionId = "myLIMEExecution1Store";

        trustyService.storeExplainabilityResult(executionId,
                new LIMEExplainabilityResult(executionId,
                        ExplainabilityStatus.SUCCEEDED,
                        "status",
                        List.of(new SaliencyModel("outcomeName",
                                List.of(new FeatureImportanceModel("feature", 1.0))))));

        LIMEExplainabilityResult result = trustyService.getExplainabilityResultById(executionId, LIMEExplainabilityResult.class);
        assertThat(result).isNotNull();
    }

    @Test
    public void testStoreExplainabilityResult_Counterfactual() {
        String executionId = "myCFExecution1Store";

        NamedTypedValue input1 = new NamedTypedValue("field1",
                new UnitValue("typeRef1", "typeRef1", new IntNode(25)));
        NamedTypedValue input2 = new NamedTypedValue("field2",
                new UnitValue("typeRef2", "typeRef2", new IntNode(99)));
        NamedTypedValue output1 = new NamedTypedValue("field3",
                new UnitValue("typeRef3", "typeRef3", new IntNode(200)));
        NamedTypedValue output2 = new NamedTypedValue("field4",
                new UnitValue("typeRef4", "typeRef4", new IntNode(1000)));

        trustyService.storeExplainabilityResult(executionId,
                new CounterfactualExplainabilityResult(executionId,
                        "counterfactualId",
                        "solutionId",
                        0L,
                        ExplainabilityStatus.SUCCEEDED,
                        "status",
                        true,
                        CounterfactualExplainabilityResult.Stage.FINAL,
                        List.of(input1, input2),
                        List.of(output1, output2)));

        CounterfactualExplainabilityResult result = trustyService.getExplainabilityResultById(executionId, CounterfactualExplainabilityResult.class);
        assertThat(result).isNotNull();
    }

    @Test
    public void testStoreExplainabilityResult_Counterfactual_DuplicateRemoval_FinalThenIntermediate() {
        String executionId = "myCFExecution1Store";
        String counterfactualId = "myCFCounterfactualId";

        NamedTypedValue input1 = new NamedTypedValue("field1",
                new UnitValue("typeRef1", "typeRef1", new IntNode(25)));
        NamedTypedValue input2 = new NamedTypedValue("field2",
                new UnitValue("typeRef2", "typeRef2", new IntNode(99)));
        NamedTypedValue output1 = new NamedTypedValue("field3",
                new UnitValue("typeRef3", "typeRef3", new IntNode(200)));
        NamedTypedValue output2 = new NamedTypedValue("field4",
                new UnitValue("typeRef4", "typeRef4", new IntNode(1000)));

        //First solution is the FINAL (for whatever reason, e.g. messaging delays, the INTERMEDIATE is received afterwards)
        trustyService.storeExplainabilityResult(executionId,
                new CounterfactualExplainabilityResult(executionId,
                        counterfactualId,
                        "solutionId1",
                        0L,
                        ExplainabilityStatus.SUCCEEDED,
                        "status",
                        true,
                        CounterfactualExplainabilityResult.Stage.FINAL,
                        List.of(input1, input2),
                        List.of(output1, output2)));

        List<CounterfactualExplainabilityResult> result1 = trustyService.getCounterfactualResults(executionId, counterfactualId);
        assertThat(result1).isNotNull();
        assertThat(result1.size()).isEqualTo(1);
        assertThat(result1.get(0).getSolutionId()).isEqualTo("solutionId1");
        assertThat(result1.get(0).getStage()).isEqualTo(CounterfactualExplainabilityResult.Stage.FINAL);

        trustyService.storeExplainabilityResult(executionId,
                new CounterfactualExplainabilityResult(executionId,
                        counterfactualId,
                        "solutionId2",
                        0L,
                        ExplainabilityStatus.SUCCEEDED,
                        "status",
                        true,
                        CounterfactualExplainabilityResult.Stage.INTERMEDIATE,
                        List.of(input1, input2),
                        List.of(output1, output2)));

        List<CounterfactualExplainabilityResult> result2 = trustyService.getCounterfactualResults(executionId, counterfactualId);
        assertThat(result2).isNotNull();
        assertThat(result1.size()).isEqualTo(1);
        assertThat(result1.get(0).getSolutionId()).isEqualTo("solutionId1");
        assertThat(result1.get(0).getStage()).isEqualTo(CounterfactualExplainabilityResult.Stage.FINAL);
    }

    @Test
    public void testStoreExplainabilityResult_Counterfactual_DuplicateRemoval_IntermediateThenFinal() {
        String executionId = "myCFExecution1Store";
        String counterfactualId = "myCFCounterfactualId";

        NamedTypedValue input1 = new NamedTypedValue("field1",
                new UnitValue("typeRef1", "typeRef1", new IntNode(25)));
        NamedTypedValue input2 = new NamedTypedValue("field2",
                new UnitValue("typeRef2", "typeRef2", new IntNode(99)));
        NamedTypedValue output1 = new NamedTypedValue("field3",
                new UnitValue("typeRef3", "typeRef3", new IntNode(200)));
        NamedTypedValue output2 = new NamedTypedValue("field4",
                new UnitValue("typeRef4", "typeRef4", new IntNode(1000)));

        //First solution is the INTERMEDIATE then the FINAL
        trustyService.storeExplainabilityResult(executionId,
                new CounterfactualExplainabilityResult(executionId,
                        counterfactualId,
                        "solutionId1",
                        0L,
                        ExplainabilityStatus.SUCCEEDED,
                        "status",
                        true,
                        CounterfactualExplainabilityResult.Stage.INTERMEDIATE,
                        List.of(input1, input2),
                        List.of(output1, output2)));

        List<CounterfactualExplainabilityResult> result1 = trustyService.getCounterfactualResults(executionId, counterfactualId);
        assertThat(result1).isNotNull();
        assertThat(result1.size()).isEqualTo(1);
        assertThat(result1.get(0).getSolutionId()).isEqualTo("solutionId1");
        assertThat(result1.get(0).getStage()).isEqualTo(CounterfactualExplainabilityResult.Stage.INTERMEDIATE);

        trustyService.storeExplainabilityResult(executionId,
                new CounterfactualExplainabilityResult(executionId,
                        counterfactualId,
                        "solutionId2",
                        0L,
                        ExplainabilityStatus.SUCCEEDED,
                        "status",
                        true,
                        CounterfactualExplainabilityResult.Stage.FINAL,
                        List.of(input1, input2),
                        List.of(output1, output2)));

        List<CounterfactualExplainabilityResult> result2 = trustyService.getCounterfactualResults(executionId, counterfactualId);
        assertThat(result2).isNotNull();
        assertThat(result2.size()).isEqualTo(1);
        assertThat(result2.get(0).getSolutionId()).isEqualTo("solutionId2");
        assertThat(result2.get(0).getStage()).isEqualTo(CounterfactualExplainabilityResult.Stage.FINAL);
    }

    private void storeExecution(String executionId, Long timestamp) {
        DecisionInput decisionInput = new DecisionInput();
        decisionInput.setId("inputId");
        decisionInput.setName("test");
        decisionInput.setValue(new UnitValue("number", "number", JsonNodeFactory.instance.numberNode(10)));

        Decision decision = new Decision();
        decision.setExecutionId(executionId);
        decision.setExecutionTimestamp(timestamp);
        decision.setServiceUrl("serviceUrl");
        decision.setExecutedModelNamespace("executedModelNamespace");
        decision.setExecutedModelName("executedModelName");
        decision.setInputs(Collections.singletonList(decisionInput));

        trustyService.storeDecision(decision.getExecutionId(), decision);
    }

    private void storeExecutionWithOutcomes(String executionId, Long timestamp) {
        DecisionInput decisionInput = new DecisionInput();
        decisionInput.setId("inputId");
        decisionInput.setName("test");
        decisionInput.setValue(new UnitValue("number", "number", JsonNodeFactory.instance.numberNode(10)));

        DecisionOutcome decisionOutcome1 = new DecisionOutcome();
        decisionOutcome1.setOutcomeId("outcomeId1");
        decisionOutcome1.setOutcomeName("outcome1");
        decisionOutcome1.setOutcomeResult(new UnitValue("number", "number", JsonNodeFactory.instance.numberNode(20)));

        DecisionOutcome decisionOutcome2 = new DecisionOutcome();
        decisionOutcome2.setOutcomeId("outcomeId2");
        decisionOutcome2.setOutcomeName("outcome2");
        decisionOutcome2.setOutcomeResult(new UnitValue("string", "string", JsonNodeFactory.instance.textNode("food")));

        Decision decision = new Decision();
        decision.setExecutionId(executionId);
        decision.setExecutionTimestamp(timestamp);
        decision.setServiceUrl("serviceUrl");
        decision.setExecutedModelNamespace("executedModelNamespace");
        decision.setExecutedModelName("executedModelName");
        decision.setInputs(Collections.singletonList(decisionInput));
        decision.setOutcomes(List.of(decisionOutcome1, decisionOutcome2));

        trustyService.storeDecision(decision.getExecutionId(), decision);
    }

    private void storeModel(String model) {
        DMNModelWithMetadata dmnModelWithMetadata = new DMNModelWithMetadata("groupId", "artifactId", "modelVersion", "dmnVersion", "name", "namespace", model);
        trustyService.storeModel(dmnModelWithMetadata);
    }

    private DMNModelWithMetadata getModel() {
        DMNModelMetadata identifier = new DMNModelMetadata("groupId",
                "artifactId",
                "version",
                "dmnVersion",
                "name",
                "namespace");
        return trustyService.getModelById(identifier, DMNModelWithMetadata.class);
    }
}
