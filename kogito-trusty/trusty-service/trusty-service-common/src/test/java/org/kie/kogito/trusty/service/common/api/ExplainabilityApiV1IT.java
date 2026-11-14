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
package org.kie.kogito.trusty.service.common.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.CompareToBuilder;
import org.junit.jupiter.api.Test;
import org.kie.kogito.explainability.api.BaseExplainabilityResult;
import org.kie.kogito.explainability.api.CounterfactualDomainCategorical;
import org.kie.kogito.explainability.api.CounterfactualDomainRange;
import org.kie.kogito.explainability.api.CounterfactualExplainabilityRequest;
import org.kie.kogito.explainability.api.CounterfactualExplainabilityResult;
import org.kie.kogito.explainability.api.CounterfactualSearchDomain;
import org.kie.kogito.explainability.api.CounterfactualSearchDomainValue;
import org.kie.kogito.explainability.api.ExplainabilityStatus;
import org.kie.kogito.explainability.api.FeatureImportanceModel;
import org.kie.kogito.explainability.api.LIMEExplainabilityResult;
import org.kie.kogito.explainability.api.ModelIdentifier;
import org.kie.kogito.explainability.api.NamedTypedValue;
import org.kie.kogito.explainability.api.SaliencyModel;
import org.kie.kogito.tracing.typedvalue.TypedValue;
import org.kie.kogito.tracing.typedvalue.UnitValue;
import org.kie.kogito.trusty.service.common.TrustyService;
import org.kie.kogito.trusty.service.common.responses.CounterfactualRequestResponse;
import org.kie.kogito.trusty.service.common.responses.CounterfactualResultsResponse;
import org.kie.kogito.trusty.service.common.responses.SalienciesResponse;
import org.kie.kogito.trusty.storage.api.model.decision.Decision;
import org.kie.kogito.trusty.storage.api.model.decision.DecisionOutcome;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;

import jakarta.ws.rs.core.MediaType;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.trusty.service.common.TrustyServiceTestUtils.getCounterfactualJsonRequest;
import static org.kie.kogito.trusty.service.common.TrustyServiceTestUtils.getCounterfactualWithStructuredModelJsonRequest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class ExplainabilityApiV1IT {

    private static final String TEST_EXECUTION_ID = "executionId";

    private static final String TEST_SERVICE_URL = "serviceUrl";

    private static final String TEST_COUNTERFACTUAL_ID = "counterfactualId";

    private static final Long TEST_MAX_RUNNING_TIME_SECONDS = 60L;

    private static final CounterfactualExplainabilityResult SOLUTION1 = new CounterfactualExplainabilityResult(TEST_EXECUTION_ID,
            TEST_COUNTERFACTUAL_ID,
            "solution1",
            0L,
            ExplainabilityStatus.SUCCEEDED,
            "",
            true,
            CounterfactualExplainabilityResult.Stage.INTERMEDIATE,
            Collections.emptyList(),
            Collections.emptyList());
    private static final CounterfactualExplainabilityResult SOLUTION2 = new CounterfactualExplainabilityResult(TEST_EXECUTION_ID,
            TEST_COUNTERFACTUAL_ID,
            "solution2",
            1L,
            ExplainabilityStatus.SUCCEEDED,
            "",
            true,
            CounterfactualExplainabilityResult.Stage.FINAL,
            Collections.emptyList(),
            Collections.emptyList());

    @InjectMock
    TrustyService executionService;

    private static BaseExplainabilityResult buildValidExplainabilityResult() {
        return new LIMEExplainabilityResult(
                TEST_EXECUTION_ID,
                ExplainabilityStatus.SUCCEEDED,
                null,
                List.of(
                        new SaliencyModel("Output1",
                                List.of(
                                        new FeatureImportanceModel("Feature1", 0.49384),
                                        new FeatureImportanceModel("Feature2", -0.1084))),
                        new SaliencyModel("Output2",
                                List.of(
                                        new FeatureImportanceModel("Feature1", 0.0),
                                        new FeatureImportanceModel("Feature2", 0.70293)))));
    }

    private static CounterfactualExplainabilityRequest buildValidCounterfactual() {
        return new CounterfactualExplainabilityRequest(TEST_EXECUTION_ID,
                TEST_SERVICE_URL,
                new ModelIdentifier("resourceType", "resourceIdentifier"),
                TEST_COUNTERFACTUAL_ID,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                TEST_MAX_RUNNING_TIME_SECONDS);
    }

    private static List<CounterfactualExplainabilityResult> buildValidCounterfactualResults() {
        return List.of(SOLUTION1, SOLUTION2);
    }

    @Test
    void testSalienciesWithExplainabilityResult() {
        mockServiceWithExplainabilityResult();

        Decision decision = new Decision(TEST_EXECUTION_ID,
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
                "Output1",
                ExplainabilityStatus.SUCCEEDED.name(),
                new UnitValue("type", new IntNode(1)),
                Collections.emptyList(),
                Collections.emptyList()));
        decision.getOutcomes().add(new DecisionOutcome("outcomeId2",
                "Output2",
                ExplainabilityStatus.SUCCEEDED.name(),
                new UnitValue("type2", new IntNode(2)),
                Collections.emptyList(),
                Collections.emptyList()));
        when(executionService.getDecisionById(eq(TEST_EXECUTION_ID))).thenReturn(decision);

        SalienciesResponse response = given().filter(new ResponseLoggingFilter())
                .when().get("/executions/decisions/" + TEST_EXECUTION_ID + "/explanations/saliencies")
                .as(SalienciesResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getSaliencies()).isNotNull();
        assertThat(response.getSaliencies().size()).isSameAs(2);

        List<SaliencyModel> sortedSaliencies = response.getSaliencies().stream()
                .sorted((s1, s2) -> new CompareToBuilder().append(s1.getOutcomeName(), s2.getOutcomeName()).toComparison())
                .collect(Collectors.toList());

        assertThat(sortedSaliencies.get(0)).isNotNull();
        assertThat(sortedSaliencies.get(0).getOutcomeName()).isEqualTo("Output1");
        assertThat(sortedSaliencies.get(0).getFeatureImportance()).isNotNull();
        assertThat(sortedSaliencies.get(0).getFeatureImportance().size()).isSameAs(2);
        assertThat(sortedSaliencies.get(0).getFeatureImportance().get(0).getFeatureName()).isEqualTo("Feature1");
        assertThat(sortedSaliencies.get(0).getFeatureImportance().get(0).getFeatureScore()).isEqualTo(0.49384);
        assertThat(sortedSaliencies.get(0).getFeatureImportance().get(1).getFeatureName()).isEqualTo("Feature2");
        assertThat(sortedSaliencies.get(0).getFeatureImportance().get(1).getFeatureScore()).isEqualTo(-0.1084);

        assertThat(sortedSaliencies.get(1)).isNotNull();
        assertThat(sortedSaliencies.get(1).getOutcomeName()).isEqualTo("Output2");
        assertThat(sortedSaliencies.get(1).getFeatureImportance()).isNotNull();
        assertThat(sortedSaliencies.get(1).getFeatureImportance().size()).isSameAs(2);
        assertThat(sortedSaliencies.get(1).getFeatureImportance().get(0).getFeatureName()).isEqualTo("Feature1");
        assertThat(sortedSaliencies.get(1).getFeatureImportance().get(0).getFeatureScore()).isEqualTo(0.0);
        assertThat(sortedSaliencies.get(1).getFeatureImportance().get(1).getFeatureName()).isEqualTo("Feature2");
        assertThat(sortedSaliencies.get(1).getFeatureImportance().get(1).getFeatureScore()).isEqualTo(0.70293);
    }

    @Test
    void testSalienciesWithNullExplainabilityResult() {
        mockServiceWithNullExplainabilityResult();

        given().filter(new ResponseLoggingFilter())
                .when().get("/executions/decisions/" + TEST_EXECUTION_ID + "/explanations/saliencies")
                .then().statusCode(400);
    }

    @Test
    void testSalienciesWithoutExplainabilityResult() {
        mockServiceWithoutExplainabilityResult();

        given().filter(new ResponseLoggingFilter())
                .when().get("/executions/decisions/" + TEST_EXECUTION_ID + "/explanations/saliencies")
                .then().statusCode(400);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCounterfactualRequest() {
        ArgumentCaptor<List<NamedTypedValue>> goalsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<CounterfactualSearchDomain>> searchDomainsCaptor = ArgumentCaptor.forClass(List.class);

        mockServiceWithCounterfactualRequest();

        CounterfactualRequestResponse response = given()
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter())
                .contentType(MediaType.APPLICATION_JSON)
                .body(getCounterfactualJsonRequest())
                .when()
                .post("/executions/decisions/" + TEST_EXECUTION_ID + "/explanations/counterfactuals")
                .as(CounterfactualRequestResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getExecutionId()).isNotNull();
        assertThat(response.getCounterfactualId()).isNotNull();
        assertThat(response.getExecutionId()).isEqualTo(TEST_EXECUTION_ID);
        assertThat(response.getCounterfactualId()).isEqualTo(TEST_COUNTERFACTUAL_ID);

        verify(executionService).requestCounterfactuals(eq(TEST_EXECUTION_ID), goalsCaptor.capture(), searchDomainsCaptor.capture());
        List<NamedTypedValue> goalsParameter = goalsCaptor.getValue();
        assertThat(goalsParameter).isNotNull();
        assertThat(goalsParameter.size()).isEqualTo(2);

        NamedTypedValue goal1 = goalsParameter.get(0);
        assertThat(goal1.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(goal1.getName()).isEqualTo("deposit");
        assertThat(goal1.getValue().getType()).isEqualTo("number");
        assertThat(goal1.getValue().toUnit().getValue().asInt()).isEqualTo(5000);

        NamedTypedValue goal2 = goalsParameter.get(1);
        assertThat(goal2.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(goal2.getName()).isEqualTo("approved");
        assertThat(goal2.getValue().getType()).isEqualTo("boolean");
        assertThat(goal2.getValue().toUnit().getValue().asBoolean()).isEqualTo(Boolean.TRUE);

        List<CounterfactualSearchDomain> searchDomainsParameter = searchDomainsCaptor.getValue();
        assertThat(searchDomainsParameter).isNotNull();
        assertThat(searchDomainsParameter.size()).isEqualTo(3);

        CounterfactualSearchDomain domain1 = searchDomainsParameter.get(0);
        assertThat(domain1.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(domain1.getValue().toUnit().isFixed()).isTrue();
        assertThat(domain1.getName()).isEqualTo("age");
        assertThat(domain1.getValue().getType()).isEqualTo("number");
        assertThat(domain1.getValue().toUnit().getDomain()).isNull();

        CounterfactualSearchDomain domain2 = searchDomainsParameter.get(1);
        assertThat(domain2.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(domain2.getValue().toUnit().isFixed()).isFalse();
        assertThat(domain2.getName()).isEqualTo("income");
        assertThat(domain2.getValue().getType()).isEqualTo("number");
        assertThat(domain2.getValue().toUnit().getDomain()).isNotNull();
        assertThat((domain2.getValue().toUnit().getDomain() instanceof CounterfactualDomainRange)).isTrue();
        CounterfactualDomainRange domain2Def = (CounterfactualDomainRange) domain2.getValue().toUnit().getDomain();
        assertThat(domain2Def.getLowerBound().asInt()).isEqualTo(0);
        assertThat(domain2Def.getUpperBound().asInt()).isEqualTo(1000);

        CounterfactualSearchDomain domain3 = searchDomainsParameter.get(2);
        assertThat(domain3.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(domain3.getValue().toUnit().isFixed()).isFalse();
        assertThat(domain3.getName()).isEqualTo("taxCode");
        assertThat(domain3.getValue().getType()).isEqualTo("string");
        assertThat(domain3.getValue().toUnit().getDomain()).isNotNull();
        assertThat((domain3.getValue().toUnit().getDomain() instanceof CounterfactualDomainCategorical)).isTrue();
        CounterfactualDomainCategorical domain3Def = (CounterfactualDomainCategorical) domain3.getValue().toUnit().getDomain();
        assertThat(domain3Def.getCategories().size()).isEqualTo(3);
        assertThat(domain3Def.getCategories().stream().map(JsonNode::asText).collect(Collectors.toList()).containsAll(Arrays.asList("A", "B", "C"))).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCounterfactualRequestWithStructuredModel() {
        ArgumentCaptor<List<NamedTypedValue>> goalsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<CounterfactualSearchDomain>> searchDomainsCaptor = ArgumentCaptor.forClass(List.class);

        mockServiceWithCounterfactualRequest();

        CounterfactualRequestResponse response = given()
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter())
                .contentType(MediaType.APPLICATION_JSON)
                .body(getCounterfactualWithStructuredModelJsonRequest())
                .when()
                .post("/executions/decisions/" + TEST_EXECUTION_ID + "/explanations/counterfactuals")
                .as(CounterfactualRequestResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getExecutionId()).isNotNull();
        assertThat(response.getCounterfactualId()).isNotNull();
        assertThat(response.getExecutionId()).isEqualTo(TEST_EXECUTION_ID);
        assertThat(response.getCounterfactualId()).isEqualTo(TEST_COUNTERFACTUAL_ID);

        verify(executionService).requestCounterfactuals(eq(TEST_EXECUTION_ID), goalsCaptor.capture(), searchDomainsCaptor.capture());
        List<NamedTypedValue> goalsParameter = goalsCaptor.getValue();
        assertThat(goalsParameter).isNotNull();
        assertThat(goalsParameter.size()).isEqualTo(1);

        NamedTypedValue goal1 = goalsParameter.get(0);
        assertThat(goal1.getValue().getKind()).isEqualTo(TypedValue.Kind.STRUCTURE);
        assertThat(goal1.getName()).isEqualTo("Fine");
        assertThat(goal1.getValue().getType()).isEqualTo("tFine");
        assertThat(goal1.getValue().toStructure().getValue().size()).isEqualTo(2);

        Iterator<Map.Entry<String, TypedValue>> goal1ChildIterator = goal1.getValue().toStructure().getValue().entrySet().iterator();
        Map.Entry<String, TypedValue> goal1Child1 = goal1ChildIterator.next();
        Map.Entry<String, TypedValue> goal1Child2 = goal1ChildIterator.next();

        assertThat(goal1Child1.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(goal1Child1.getKey()).isEqualTo("Amount");
        assertThat(goal1Child1.getValue().getType()).isEqualTo("number");
        assertThat(goal1Child1.getValue().toUnit().getValue().asInt()).isEqualTo(100);

        assertThat(goal1Child2.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(goal1Child2.getKey()).isEqualTo("Points");
        assertThat(goal1Child2.getValue().getType()).isEqualTo("number");
        assertThat(goal1Child2.getValue().toUnit().getValue().asInt()).isEqualTo(0);

        List<CounterfactualSearchDomain> searchDomainsParameter = searchDomainsCaptor.getValue();
        assertThat(searchDomainsParameter).isNotNull();
        assertThat(searchDomainsParameter.size()).isEqualTo(1);

        CounterfactualSearchDomain domain1 = searchDomainsParameter.get(0);
        assertThat(domain1.getValue().getKind()).isEqualTo(TypedValue.Kind.STRUCTURE);
        assertThat(domain1.getName()).isEqualTo("Violation");
        assertThat(domain1.getValue().getType()).isEqualTo("tViolation");
        assertThat(domain1.getValue().toStructure().getValue().size()).isEqualTo(3);

        Iterator<Map.Entry<String, CounterfactualSearchDomainValue>> domain1ChildIterator = domain1.getValue().toStructure().getValue().entrySet().iterator();
        Map.Entry<String, CounterfactualSearchDomainValue> domain1Child1 = domain1ChildIterator.next();
        Map.Entry<String, CounterfactualSearchDomainValue> domain1Child2 = domain1ChildIterator.next();
        Map.Entry<String, CounterfactualSearchDomainValue> domain1Child3 = domain1ChildIterator.next();

        assertThat(domain1Child1.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(domain1Child1.getValue().toUnit().isFixed()).isFalse();
        assertThat(domain1Child1.getKey()).isEqualTo("Type");
        assertThat(domain1Child1.getValue().getType()).isEqualTo("string");
        assertThat(domain1Child1.getValue().toUnit().getDomain()).isNotNull();
        assertThat((domain1Child1.getValue().toUnit().getDomain() instanceof CounterfactualDomainCategorical)).isTrue();
        CounterfactualDomainCategorical domain1Child1Def = (CounterfactualDomainCategorical) domain1Child1.getValue().toUnit().getDomain();
        assertThat(domain1Child1Def.getCategories().size()).isEqualTo(2);
        assertThat(domain1Child1Def.getCategories().stream().map(JsonNode::asText).collect(Collectors.toList()).containsAll(Arrays.asList("speed", "driving under the influence"))).isTrue();

        assertThat(domain1Child2.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(domain1Child2.getValue().toUnit().isFixed()).isFalse();
        assertThat(domain1Child2.getKey()).isEqualTo("Actual Speed");
        assertThat(domain1Child2.getValue().getType()).isEqualTo("number");
        assertThat(domain1Child2.getValue().toUnit().getDomain()).isNotNull();
        assertThat((domain1Child2.getValue().toUnit().getDomain() instanceof CounterfactualDomainRange)).isTrue();
        CounterfactualDomainRange domain1Child2Def = (CounterfactualDomainRange) domain1Child2.getValue().toUnit().getDomain();
        assertThat(domain1Child2Def.getLowerBound().asInt()).isEqualTo(0);
        assertThat(domain1Child2Def.getUpperBound().asInt()).isEqualTo(100);

        assertThat(domain1Child3.getValue().getKind()).isEqualTo(TypedValue.Kind.UNIT);
        assertThat(domain1Child3.getValue().toUnit().isFixed()).isFalse();
        assertThat(domain1Child3.getKey()).isEqualTo("Speed Limit");
        assertThat(domain1Child3.getValue().getType()).isEqualTo("number");
        assertThat(domain1Child3.getValue().toUnit().getDomain()).isNotNull();
        assertThat((domain1Child3.getValue().toUnit().getDomain() instanceof CounterfactualDomainRange)).isTrue();
        CounterfactualDomainRange domain1Child3Def = (CounterfactualDomainRange) domain1Child3.getValue().toUnit().getDomain();
        assertThat(domain1Child3Def.getLowerBound().asInt()).isEqualTo(0);
        assertThat(domain1Child3Def.getUpperBound().asInt()).isEqualTo(100);
    }

    @Test
    void testCounterfactualResultsWithRequest() {
        mockServiceWithCounterfactualRequest();
        mockServiceWithCounterfactualResults();

        given()
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter())
                .contentType(MediaType.APPLICATION_JSON)
                .body(getCounterfactualJsonRequest())
                .when()
                .post("/executions/decisions/" + TEST_EXECUTION_ID + "/explanations/counterfactuals")
                .as(CounterfactualRequestResponse.class);

        CounterfactualResultsResponse resultsResponse = given()
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter())
                .when()
                .get("/executions/decisions/" + TEST_EXECUTION_ID + "/explanations/counterfactuals/" + TEST_COUNTERFACTUAL_ID)
                .as(CounterfactualResultsResponse.class);

        assertThat(resultsResponse).isNotNull();
        assertThat(resultsResponse.getExecutionId()).isNotNull();
        assertThat(resultsResponse.getCounterfactualId()).isNotNull();
        assertThat(resultsResponse.getExecutionId()).isEqualTo(TEST_EXECUTION_ID);
        assertThat(resultsResponse.getCounterfactualId()).isEqualTo(TEST_COUNTERFACTUAL_ID);
        assertThat(resultsResponse.getSolutions().size()).isEqualTo(2);
    }

    @Test
    void testCounterfactualResultsWithRequestWithoutResults() {
        mockServiceWithCounterfactualRequest();

        given()
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter())
                .contentType(MediaType.APPLICATION_JSON)
                .body(getCounterfactualJsonRequest())
                .when()
                .post("/executions/decisions/" + TEST_EXECUTION_ID + "/explanations/counterfactuals")
                .as(CounterfactualRequestResponse.class);

        CounterfactualResultsResponse resultsResponse = given()
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter())
                .when()
                .get("/executions/decisions/" + TEST_EXECUTION_ID + "/explanations/counterfactuals/" + TEST_COUNTERFACTUAL_ID)
                .as(CounterfactualResultsResponse.class);

        assertThat(resultsResponse).isNotNull();
        assertThat(resultsResponse.getExecutionId()).isNotNull();
        assertThat(resultsResponse.getCounterfactualId()).isNotNull();
        assertThat(resultsResponse.getExecutionId()).isEqualTo(TEST_EXECUTION_ID);
        assertThat(resultsResponse.getCounterfactualId()).isEqualTo(TEST_COUNTERFACTUAL_ID);
        assertThat(resultsResponse.getSolutions().isEmpty()).isTrue();
    }

    @Test
    void testCounterfactualResultsWithoutRequest() {
        given().filter(new ResponseLoggingFilter())
                .when()
                .get("/executions/decisions/" + TEST_EXECUTION_ID + "/explanations/counterfactuals/" + TEST_COUNTERFACTUAL_ID)
                .then().statusCode(400);
    }

    private void mockServiceWithExplainabilityResult() {
        when(executionService.getExplainabilityResultById(eq(TEST_EXECUTION_ID), any()))
                .thenReturn(buildValidExplainabilityResult());
    }

    private void mockServiceWithNullExplainabilityResult() {
        when(executionService.getExplainabilityResultById(anyString(), any()))
                .thenReturn(null);
    }

    private void mockServiceWithoutExplainabilityResult() {
        when(executionService.getExplainabilityResultById(anyString(), any()))
                .thenThrow(new IllegalArgumentException("Explainability result does not exist."));
    }

    private void mockServiceWithCounterfactualRequest() {
        when(executionService.requestCounterfactuals(eq(TEST_EXECUTION_ID), any(), any()))
                .thenReturn(buildValidCounterfactual());
        when(executionService.getCounterfactualRequest(eq(TEST_EXECUTION_ID), eq(TEST_COUNTERFACTUAL_ID)))
                .thenReturn(buildValidCounterfactual());
    }

    private void mockServiceWithCounterfactualResults() {
        when(executionService.getCounterfactualResults(eq(TEST_EXECUTION_ID), eq(TEST_COUNTERFACTUAL_ID)))
                .thenReturn(buildValidCounterfactualResults());
    }
}
