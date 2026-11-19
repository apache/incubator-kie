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
package org.kie.kogito.it.trusty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.kie.kogito.explainability.api.CounterfactualSearchDomain;
import org.kie.kogito.explainability.api.CounterfactualSearchDomainCollectionValue;
import org.kie.kogito.explainability.api.CounterfactualSearchDomainStructureValue;
import org.kie.kogito.explainability.api.CounterfactualSearchDomainUnitValue;
import org.kie.kogito.explainability.api.CounterfactualSearchDomainValue;
import org.kie.kogito.explainability.api.NamedTypedValue;
import org.kie.kogito.testcontainers.ExplainabilityServiceMessagingContainer;
import org.kie.kogito.testcontainers.InfinispanTrustyServiceContainer;
import org.kie.kogito.testcontainers.KogitoInfinispanContainer;
import org.kie.kogito.testcontainers.KogitoKafkaContainer;
import org.kie.kogito.testcontainers.KogitoKeycloakContainer;
import org.kie.kogito.testcontainers.KogitoServiceContainer;
import org.kie.kogito.tracing.typedvalue.TypedValue;
import org.kie.kogito.trusty.service.common.requests.CounterfactualRequest;
import org.kie.kogito.trusty.service.common.responses.CounterfactualRequestResponse;
import org.kie.kogito.trusty.service.common.responses.CounterfactualResultsResponse;
import org.kie.kogito.trusty.service.common.responses.ExecutionsResponse;
import org.kie.kogito.trusty.service.common.responses.SalienciesResponse;
import org.kie.kogito.trusty.service.common.responses.decision.DecisionOutcomesResponse;
import org.kie.kogito.trusty.service.common.responses.decision.DecisionStructuredInputsResponse;
import org.kie.kogito.trusty.storage.api.model.decision.DecisionInput;
import org.kie.kogito.trusty.storage.api.model.decision.DecisionOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractTrustyExplainabilityEnd2EndIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTrustyExplainabilityEnd2EndIT.class);

    private static final String EXPL_SERVICE_ALIAS = "expl-service";
    private static final int EXPL_SERVICE_SAMPLES = 10;

    private static final String INFINISPAN_ALIAS = "infinispan";
    private static final String INFINISPAN_SERVER_LIST = INFINISPAN_ALIAS + ":11222";

    private static final String KAFKA_ALIAS = "kafka";
    private static final String KAFKA_BOOTSTRAP_SERVERS = KAFKA_ALIAS + ":29092";

    private static final String KEYCLOAK_ALIAS = "keycloak";
    private static final String KEYCLOAK_DB_VENDOR_VARIABLE = "DB_VENDOR";
    private static final String KEYCLOAK_DB_VENDOR_VALUE = "h2";
    private static final String KEYCLOAK_ACCESS_TOKEN_PATH = "/realms/kogito/protocol/openid-connect/token";
    private static final String KEYCLOAK_GRANT_TYPE_PARAM_NAME = "grant_type";
    private static final String KEYCLOAK_GRANT_TYPE_PARAM_VALUE = "password";
    private static final String KEYCLOAK_USERNAME_PARAM_NAME = "username";
    private static final String KEYCLOAK_USERNAME_PARAM_VALUE = "jdoe";
    private static final String KEYCLOAK_PASSWORD_PARAM_NAME = "password";
    private static final String KEYCLOAK_PASSWORD_PARAM_VALUE = "jdoe";
    private static final String KEYCLOAK_CLIENT_ID_PARAM_NAME = "client_id";
    private static final String KEYCLOAK_CLIENT_ID_PARAM_VALUE = KogitoKeycloakContainer.CLIENT_ID;
    private static final String KEYCLOAK_CLIENT_SECRET_PARAM_NAME = "client_secret";
    private static final String KEYCLOAK_CLIENT_SECRET_PARAM_VALUE = KogitoKeycloakContainer.CLIENT_SECRET;

    private static final String KOGITO_SERVICE_ALIAS = "kogito-service";
    private static final String KOGITO_SERVICE_URL = "http://" + KOGITO_SERVICE_ALIAS + ":8080";
    private static final List<String> KOGITO_SERVICE_PAYLOADS = List.of(
            "{\"Driver\":{\"Age\":25,\"Points\":13},\"Violation\":{\"Type\":\"speed\",\"Actual Speed\":105,\"Speed Limit\":100}}",
            "{\"Driver\":{\"Age\":37,\"Points\":20},\"Violation\":{\"Type\":\"speed\",\"Actual Speed\":135,\"Speed Limit\":100}}",
            "{\"Driver\":{\"Age\":18,\"Points\": 0},\"Violation\":{\"Type\":\"speed\",\"Actual Speed\": 85,\"Speed Limit\": 70}}",
            "{\"Driver\":{\"Age\":56,\"Points\":13},\"Violation\":{\"Type\":\"speed\",\"Actual Speed\": 35,\"Speed Limit\": 25}}",
            "{\"Driver\":{\"Age\":40,\"Points\":13},\"Violation\":{\"Type\":\"speed\",\"Actual Speed\":215,\"Speed Limit\":120}}");

    private static final String TRUSTY_SERVICE_ALIAS = "trusty-service";
    private static final String TRUSTY_SERVICE_OIDC_AUTH_SERVER_URL_VARIABLE = "QUARKUS_OIDC_AUTH_SERVER_URL";
    private static final String TRUSTY_SERVICE_OIDC_AUTH_SERVER_URL_VALUE = "http://" + KEYCLOAK_ALIAS + ":8080/auth/realms/kogito";
    private static final String TRUSTY_SERVICE_OIDC_CLIENT_ID_VARIABLE = "QUARKUS_OIDC_CLIENT_ID";
    private static final String TRUSTY_SERVICE_OIDC_CLIENT_ID_VALUE = "kogito-trusty-service";

    private final BiFunction<String, String, KogitoServiceContainer> kogitoServiceContainerProducer;

    protected AbstractTrustyExplainabilityEnd2EndIT(BiFunction<String, String, KogitoServiceContainer> kogitoServiceContainerProducer) {
        this.kogitoServiceContainerProducer = kogitoServiceContainerProducer;
    }

    @Test
    public void doTest() {
        try (
                final Network network = Network.newNetwork();

                final KogitoInfinispanContainer infinispan = new KogitoInfinispanContainer()
                        .withNetwork(network)
                        .withNetworkAliases(INFINISPAN_ALIAS);

                final KogitoKafkaContainer kafka = new KogitoKafkaContainer()
                        .withNetwork(network)
                        .withNetworkAliases(KAFKA_ALIAS);

                final KogitoKeycloakContainer keycloak = new KogitoKeycloakContainer()
                        .withEnv(KEYCLOAK_DB_VENDOR_VARIABLE, KEYCLOAK_DB_VENDOR_VALUE)
                        .withNetwork(network)
                        .withNetworkAliases(KEYCLOAK_ALIAS);

                final ExplainabilityServiceMessagingContainer explService = new ExplainabilityServiceMessagingContainer(KAFKA_BOOTSTRAP_SERVERS, EXPL_SERVICE_SAMPLES)
                        .withLogConsumer(new Slf4jLogConsumer(LOGGER))
                        .withNetwork(network)
                        .withNetworkAliases(EXPL_SERVICE_ALIAS);

                final InfinispanTrustyServiceContainer trustyService = new InfinispanTrustyServiceContainer(INFINISPAN_SERVER_LIST, KAFKA_BOOTSTRAP_SERVERS, true)
                        .withEnv(TRUSTY_SERVICE_OIDC_AUTH_SERVER_URL_VARIABLE, TRUSTY_SERVICE_OIDC_AUTH_SERVER_URL_VALUE)
                        .withEnv(TRUSTY_SERVICE_OIDC_CLIENT_ID_VARIABLE, TRUSTY_SERVICE_OIDC_CLIENT_ID_VALUE)
                        .withEnv("INFINISPAN_USE_AUTH", "FALSE")
                        .withLogConsumer(new Slf4jLogConsumer(LOGGER))
                        .withNetwork(network)
                        .withNetworkAliases(TRUSTY_SERVICE_ALIAS);

                final KogitoServiceContainer kogitoService = kogitoServiceContainerProducer.apply(KAFKA_BOOTSTRAP_SERVERS, KOGITO_SERVICE_URL)
                        .withLogConsumer(new Slf4jLogConsumer(LOGGER))
                        .withNetwork(network)
                        .withNetworkAliases(KOGITO_SERVICE_ALIAS)) {
            infinispan.start();
            assertThat(infinispan.isRunning()).isTrue();

            kafka.start();
            assertThat(kafka.isRunning()).isTrue();

            keycloak.start();
            assertThat(keycloak.isRunning()).isTrue();

            explService.start();
            assertThat(explService.isRunning()).isTrue();

            trustyService.start();
            assertThat(trustyService.isRunning()).isTrue();

            kogitoService.start();
            assertThat(kogitoService.isRunning()).isTrue();

            final String accessToken = given()
                    .port(keycloak.getFirstMappedPort())
                    .param(KEYCLOAK_GRANT_TYPE_PARAM_NAME, KEYCLOAK_GRANT_TYPE_PARAM_VALUE)
                    .param(KEYCLOAK_USERNAME_PARAM_NAME, KEYCLOAK_USERNAME_PARAM_VALUE)
                    .param(KEYCLOAK_PASSWORD_PARAM_NAME, KEYCLOAK_PASSWORD_PARAM_VALUE)
                    .param(KEYCLOAK_CLIENT_ID_PARAM_NAME, KEYCLOAK_CLIENT_ID_PARAM_VALUE)
                    .param(KEYCLOAK_CLIENT_SECRET_PARAM_NAME, KEYCLOAK_CLIENT_SECRET_PARAM_VALUE)
                    .when()
                    .post(KEYCLOAK_ACCESS_TOKEN_PATH)
                    .jsonPath().get("access_token");

            assertThat(accessToken).isNotNull();

            final List<String> executionIds = new ArrayList<>();
            final int expectedExecutions = KOGITO_SERVICE_PAYLOADS.size();

            LOGGER.info("Invoke Decision endpoint to generate LIME explanations...");
            KOGITO_SERVICE_PAYLOADS.forEach(json -> given()
                    .port(kogitoService.getFirstMappedPort())
                    .contentType("application/json")
                    .body(json)
                    .when().post("/Traffic Violation")
                    .then().statusCode(200));

            LOGGER.info("Check Decisions executed...");
            await()
                    .atLeast(5, SECONDS)
                    .atMost(30, SECONDS)
                    .with().pollInterval(5, SECONDS)
                    .untilAsserted(() -> {
                        ExecutionsResponse executionsResponse = given()
                                .port(trustyService.getFirstMappedPort())
                                .auth().oauth2(accessToken)
                                .when().get(String.format("/executions?limit=%d", expectedExecutions))
                                .then().statusCode(200)
                                .extract().as(ExecutionsResponse.class);

                        assertThat(executionsResponse.getHeaders()).hasSize(expectedExecutions);
                        executionsResponse.getHeaders().forEach(h -> executionIds.add(h.getExecutionId()));
                    });

            LOGGER.info("Check LIME explanations generated...");
            await()
                    .atLeast(5, SECONDS)
                    .atMost(60, SECONDS)
                    .with().pollInterval(5, SECONDS)
                    .untilAsserted(() -> executionIds.forEach(executionId -> {
                        SalienciesResponse salienciesResponse = given()
                                .port(trustyService.getFirstMappedPort())
                                .auth().oauth2(accessToken)
                                .when().get("/executions/decisions/" + executionId + "/explanations/saliencies")
                                .then().statusCode(200)
                                .extract().as(SalienciesResponse.class);

                        assertThat(salienciesResponse.getStatus()).isEqualTo("SUCCEEDED");
                    }));

            LOGGER.info("Request Counterfactuals for each execution and check responses generated...");
            executionIds.forEach(executionId -> {
                await()
                        .atLeast(500, MILLISECONDS)
                        .atMost(60, SECONDS)
                        .with().pollInterval(500, MILLISECONDS)
                        .until(doCounterfactualRequests(trustyService, accessToken, executionId));
                await()
                        .atLeast(500, MILLISECONDS)
                        .atMost(60, SECONDS)
                        .with().pollInterval(500, MILLISECONDS)
                        .until(doCounterfactualResponses(trustyService, accessToken, executionId));
            });
        }
    }

    private Callable<Boolean> doCounterfactualRequests(final InfinispanTrustyServiceContainer trustyService,
            final String accessToken,
            final String executionId) {
        LOGGER.info(String.format("Reading Decision [%s]'s Inputs...", executionId));
        DecisionStructuredInputsResponse inputs = given()
                .port(trustyService.getFirstMappedPort())
                .auth().oauth2(accessToken)
                .when().get("/executions/decisions/" + executionId + "/structuredInputs")
                .then().statusCode(200)
                .extract().as(DecisionStructuredInputsResponse.class);

        LOGGER.info(String.format("Reading Decision [%s]'s Outputs...", executionId));
        DecisionOutcomesResponse outcomes = given()
                .port(trustyService.getFirstMappedPort())
                .auth().oauth2(accessToken)
                .when().get("/executions/decisions/" + executionId + "/outcomes")
                .then().statusCode(200)
                .extract().as(DecisionOutcomesResponse.class);

        //Debugging output (handy to keep).
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
        StringBuilder sb = new StringBuilder();
        sb.append("== INPUTS ==>\n");
        inputs.getInputs().forEach(i -> {
            try {
                sb.append(writer.writeValueAsString(i)).append("\n");
            } catch (JsonProcessingException jpe) {
                //Swallow
            }
        });
        sb.append("== OUTPUTS ==>\n");
        outcomes.getOutcomes().forEach(o -> {
            try {
                sb.append(writer.writeValueAsString(o.getOutcomeResult())).append("\n");
            } catch (JsonProcessingException jpe) {
                //Swallow
            }
        });
        LOGGER.debug(sb.toString());

        return () -> {
            LOGGER.info(String.format("Checking Decision [%s]'s Counterfactual request was successful...", executionId));
            // The Goals and Search Domain structures must match those of the original decision
            // See https://issues.redhat.com/browse/FAI-486
            CounterfactualRequestResponse counterfactualRequestResponse = given()
                    .port(trustyService.getFirstMappedPort())
                    .auth().oauth2(accessToken)
                    .when()
                    .contentType(ContentType.JSON)
                    .body(new CounterfactualRequest(
                            outcomes.getOutcomes().stream().map(AbstractTrustyExplainabilityEnd2EndIT::toCounterfactualGoal).collect(Collectors.toList()),
                            inputs.getInputs().stream().map(AbstractTrustyExplainabilityEnd2EndIT::toCounterfactualSearchDomain).collect(Collectors.toList())))
                    .post("/executions/decisions/" + executionId + "/explanations/counterfactuals")
                    .then().statusCode(200)
                    .extract().as(CounterfactualRequestResponse.class);

            return Objects.nonNull(counterfactualRequestResponse)
                    && Objects.equals(executionId, counterfactualRequestResponse.getExecutionId())
                    && Objects.nonNull(counterfactualRequestResponse.getCounterfactualId());
        };
    }

    private static NamedTypedValue toCounterfactualGoal(DecisionOutcome outcome) {
        TypedValue value = outcome.getOutcomeResult();
        return new NamedTypedValue(outcome.getOutcomeName(), value);
    }

    private static CounterfactualSearchDomain toCounterfactualSearchDomain(DecisionInput input) {
        TypedValue value = input.getValue();
        return new CounterfactualSearchDomain(input.getName(), toCounterfactualSearchDomainValue(value));
    }

    private static CounterfactualSearchDomainValue toCounterfactualSearchDomainValue(TypedValue value) {
        switch (value.getKind()) {
            case COLLECTION:
                Collection<CounterfactualSearchDomainValue> cfCollectionValues = value.toCollection()
                        .getValue()
                        .stream()
                        .map(AbstractTrustyExplainabilityEnd2EndIT::toCounterfactualSearchDomainValue)
                        .collect(Collectors.toList());
                return new CounterfactualSearchDomainCollectionValue(value.getType(), cfCollectionValues);
            case STRUCTURE:
                Map<String, CounterfactualSearchDomainValue> cfStructureValues = value.toStructure()
                        .getValue()
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                e -> toCounterfactualSearchDomainValue(e.getValue())));

                return new CounterfactualSearchDomainStructureValue(value.getType(), cfStructureValues);
            case UNIT:
                return new CounterfactualSearchDomainUnitValue(value.getType(),
                        value.getType(),
                        Boolean.TRUE,
                        null);
        }
        throw new IllegalArgumentException("An unexpected TypedValue.Kind detected. Unable to process.");
    }

    private Callable<Boolean> doCounterfactualResponses(final InfinispanTrustyServiceContainer trustyService,
            final String accessToken,
            final String executionId) {
        return () -> {
            LOGGER.info(String.format("Checking Decision [%s] has only one Counterfactual request...", executionId));
            // Get all counterfactual requests for an execution
            List<CounterfactualRequestResponse> counterfactualRequests = Arrays.asList(given()
                    .port(trustyService.getFirstMappedPort())
                    .auth().oauth2(accessToken)
                    .when()
                    .contentType(ContentType.JSON)
                    .get("/executions/decisions/" + executionId + "/explanations/counterfactuals")
                    .then().statusCode(200)
                    .extract().as(CounterfactualRequestResponse[].class));

            // We should only have one per execution
            assertThat(counterfactualRequests).isNotNull().hasSize(1);

            // Verify there are counterfactual results available
            String counterfactualId = counterfactualRequests.get(0).getCounterfactualId();

            LOGGER.info(String.format("Checking Decision [%s] Counterfactual results exist...", executionId));
            CounterfactualResultsResponse details = given()
                    .port(trustyService.getFirstMappedPort())
                    .auth().oauth2(accessToken)
                    .when()
                    .contentType(ContentType.JSON)
                    .get("/executions/decisions/" + executionId + "/explanations/counterfactuals/" + counterfactualId)
                    .then().statusCode(200)
                    .extract().as(CounterfactualResultsResponse.class);

            return Objects.nonNull(details)
                    && Objects.equals(executionId, details.getExecutionId())
                    && Objects.equals(counterfactualId, details.getCounterfactualId())
                    && !details.getSolutions().isEmpty();
        };
    }
}
