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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.kie.kogito.explainability.api.CounterfactualExplainabilityRequest;
import org.kie.kogito.explainability.api.CounterfactualExplainabilityResult;
import org.kie.kogito.explainability.api.CounterfactualSearchDomain;
import org.kie.kogito.explainability.api.LIMEExplainabilityResult;
import org.kie.kogito.explainability.api.NamedTypedValue;
import org.kie.kogito.trusty.service.common.TrustyService;
import org.kie.kogito.trusty.service.common.handlers.LIMESaliencyConverter;
import org.kie.kogito.trusty.service.common.responses.CounterfactualRequestResponse;
import org.kie.kogito.trusty.service.common.responses.CounterfactualResultsResponse;
import org.kie.kogito.trusty.service.common.responses.SalienciesResponse;
import org.kie.kogito.trusty.service.common.responses.decision.DecisionStructuredInputsResponse;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("executions/decisions")
public class ExplainabilityApiV1 {

    @Inject
    TrustyService trustyService;

    @Inject
    LIMESaliencyConverter saliencyConverter;

    @GET
    @Path("/{executionId}/explanations/saliencies")
    @APIResponses(value = {
            @APIResponse(description = "Gets the local explanation of a decision.", responseCode = "200",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.OBJECT, implementation = SalienciesResponse.class))),
            @APIResponse(description = "Bad Request", responseCode = "400", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    @Operation(
            summary = "Returns the saliencies for a decision.",
            description = "Returns the saliencies for a particular decision calculated using the lime algorithm.")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSaliencies(
            @Parameter(
                    name = "executionId",
                    description = "The execution ID.",
                    required = true,
                    schema = @Schema(implementation = String.class)) @PathParam("executionId") String executionId) {
        return retrieveLIMEExplainabilityResult(executionId)
                .map(result -> saliencyConverter.fromResult(executionId, result))
                .map(result -> Response.ok(result).build())
                .orElseGet(() -> Response.status(Response.Status.BAD_REQUEST.getStatusCode()).build());
    }

    private Optional<LIMEExplainabilityResult> retrieveLIMEExplainabilityResult(String executionId) {
        try {
            return Optional.ofNullable(trustyService.getExplainabilityResultById(executionId, LIMEExplainabilityResult.class));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    @POST
    @Path("/{executionId}/explanations/counterfactuals")
    @APIResponses(value = {
            @APIResponse(description = "UUID, counterfactualId, for the calculation request.", responseCode = "200",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.OBJECT, implementation = CounterfactualRequestResponse.class))),
            @APIResponse(description = "Bad Request", responseCode = "400", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    @Operation(
            summary = "Request calculation of the counterfactuals for a decision.",
            description = "Requests calculation of the counterfactuals for a particular decision. Results of the calculation " +
                    "can be obtained by GETing /{executionId}/explanations/counterfactuals/{counterfactualId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response requestCounterfactuals(
            @Parameter(
                    name = "executionId",
                    description = "The execution ID.",
                    required = true,
                    schema = @Schema(implementation = String.class)) @PathParam("executionId") String executionId,
            @Parameter(
                    name = "Counterfactual request",
                    description = "The definition of a request to calculate a decision's Counterfactuals.",
                    required = true,
                    schema = @Schema(
                            implementation = org.kie.kogito.trusty.service.common.requests.CounterfactualRequest.class)) org.kie.kogito.trusty.service.common.requests.CounterfactualRequest request) {
        List<NamedTypedValue> goals = request.getGoals();
        List<CounterfactualSearchDomain> searchDomains = request.getSearchDomains();
        return requestCounterfactualsForExecution(executionId, goals, searchDomains)
                .map(obj -> new CounterfactualRequestResponse(obj.getExecutionId(),
                        obj.getCounterfactualId(),
                        obj.getMaxRunningTimeSeconds()))
                .map(Response::ok)
                .orElseGet(() -> Response.status(Response.Status.BAD_REQUEST.getStatusCode()))
                .build();
    }

    private Optional<CounterfactualExplainabilityRequest> requestCounterfactualsForExecution(String executionId,
            List<NamedTypedValue> goals,
            List<CounterfactualSearchDomain> searchDomains) {
        try {
            return Optional.ofNullable(trustyService.requestCounterfactuals(executionId, goals, searchDomains));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    @GET
    @Path("/{executionId}/explanations/counterfactuals")
    @APIResponses(value = {
            @APIResponse(description = "A summary of all counterfactuals for a decision.", responseCode = "200",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.OBJECT, implementation = DecisionStructuredInputsResponse.class))),
            @APIResponse(description = "Bad Request", responseCode = "400", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    @Operation(
            summary = "Returns a summary of all of the counterfactuals for a decision.",
            description = "Returns a summary of all of the counterfactuals for a particular decision.")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllCounterfactualsSummary(
            @Parameter(
                    name = "executionId",
                    description = "The execution ID.",
                    required = true,
                    schema = @Schema(implementation = String.class)) @PathParam("executionId") String executionId) {
        return getCounterfactualRequestsForExecution(executionId)
                .map(obj -> obj.stream().map(cf -> new CounterfactualRequestResponse(cf.getExecutionId(),
                        cf.getCounterfactualId(),
                        cf.getMaxRunningTimeSeconds())).collect(Collectors.toList()))
                .map(Response::ok)
                .orElseGet(() -> Response.status(Response.Status.BAD_REQUEST.getStatusCode()))
                .build();
    }

    private Optional<List<CounterfactualExplainabilityRequest>> getCounterfactualRequestsForExecution(String executionId) {
        try {
            return Optional.ofNullable(trustyService.getCounterfactualRequests(executionId));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    @GET
    @Path("/{executionId}/explanations/counterfactuals/{counterfactualId}")
    @APIResponses(value = {
            @APIResponse(description = "Details of a specific counterfactual explanation for a decision.", responseCode = "200",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.OBJECT, implementation = DecisionStructuredInputsResponse.class))),
            @APIResponse(description = "Bad Request", responseCode = "400", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    @Operation(
            summary = "Returns details of a specific counterfactual explanation for a decision.",
            description = "Returns details of a specific counterfactual explanation for a particular decision.")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCounterfactualDetails(
            @Parameter(
                    name = "executionId",
                    description = "The execution ID.",
                    required = true,
                    schema = @Schema(implementation = String.class)) @PathParam("executionId") String executionId,
            @Parameter(
                    name = "counterfactualId",
                    description = "The Counterfactual ID.",
                    required = true,
                    schema = @Schema(implementation = String.class)) @PathParam("counterfactualId") String counterfactualId) {
        return getCounterfactualRequestForExecution(executionId, counterfactualId)
                .map(request -> {
                    List<CounterfactualExplainabilityResult> results = trustyService.getCounterfactualResults(executionId, counterfactualId);
                    return new CounterfactualResultsResponse(executionId,
                            request.getServiceUrl(),
                            request.getModelIdentifier(),
                            counterfactualId,
                            request.getOriginalInputs(),
                            request.getGoals(),
                            request.getSearchDomains(),
                            request.getMaxRunningTimeSeconds(),
                            results);
                })
                .map(Response::ok)
                .orElseGet(() -> Response.status(Response.Status.BAD_REQUEST.getStatusCode()))
                .build();
    }

    private Optional<CounterfactualExplainabilityRequest> getCounterfactualRequestForExecution(String executionId, String counterfactualId) {
        try {
            return Optional.ofNullable(trustyService.getCounterfactualRequest(executionId, counterfactualId));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
