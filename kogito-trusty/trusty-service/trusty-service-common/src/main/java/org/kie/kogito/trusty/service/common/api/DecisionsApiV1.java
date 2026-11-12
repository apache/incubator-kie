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

import java.util.Optional;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.kie.kogito.trusty.service.common.TrustyService;
import org.kie.kogito.trusty.service.common.responses.ExecutionHeaderResponse;
import org.kie.kogito.trusty.service.common.responses.ResponseUtils;
import org.kie.kogito.trusty.service.common.responses.decision.DecisionHeaderResponse;
import org.kie.kogito.trusty.service.common.responses.decision.DecisionOutcomesResponse;
import org.kie.kogito.trusty.service.common.responses.decision.DecisionStructuredInputsResponse;
import org.kie.kogito.trusty.storage.api.model.decision.Decision;
import org.kie.kogito.trusty.storage.api.model.decision.DecisionOutcome;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("executions/decisions")
public class DecisionsApiV1 {

    @Inject
    TrustyService trustyService;

    /**
     * Gets an execution header by ID.
     *
     * @param executionId The execution ID.
     * @return The execution header.
     */
    @GET
    @Path("/{executionId}")
    @APIResponses(value = {
            @APIResponse(description = "Gets the decision detail header.", responseCode = "200",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.OBJECT, implementation = ExecutionHeaderResponse.class))),
            @APIResponse(description = "Bad Request", responseCode = "400", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    @Operation(summary = "Gets The decision header with details.", description = "Gets the decision detail header.")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getExecutionById(
            @Parameter(
                    name = "executionId",
                    description = "The execution ID.",
                    required = true,
                    schema = @Schema(implementation = String.class)) @PathParam("executionId") String executionId) {
        return retrieveDecision(executionId)
                .map(obj -> Response.ok(ResponseUtils.executionHeaderResponseFrom(obj)).build())
                .orElseGet(this::buildBadRequestResponse);
    }

    @GET
    @Path("/{executionId}/structuredInputs")
    @APIResponses(value = {
            @APIResponse(description = "Gets the decision structured inputs.", responseCode = "200",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.OBJECT, implementation = DecisionStructuredInputsResponse.class))),
            @APIResponse(description = "Bad Request", responseCode = "400", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    @Operation(summary = "Gets the decision structured inputs.")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStructuredInputs(
            @Parameter(
                    name = "executionId",
                    description = "The execution ID.",
                    required = true,
                    schema = @Schema(implementation = String.class)) @PathParam("executionId") String executionId) {

        return retrieveDecision(executionId)
                .map(this::extractStructuredInputsResponse)
                .orElseGet(this::buildBadRequestResponse);
    }

    @GET
    @Path("/{executionId}/outcomes")
    @APIResponses(value = {
            @APIResponse(description = "Gets the decision outcomes.", responseCode = "200",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.OBJECT, implementation = DecisionOutcomesResponse.class))),
            @APIResponse(description = "Bad Request", responseCode = "400", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    @Operation(summary = "Gets the decision outcomes.")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOutcomes(
            @Parameter(
                    name = "executionId",
                    description = "The execution ID.",
                    required = true,
                    schema = @Schema(implementation = String.class)) @PathParam("executionId") String executionId) {
        return retrieveDecision(executionId)
                .map(obj -> Response.ok(new DecisionOutcomesResponse((DecisionHeaderResponse) ResponseUtils.executionHeaderResponseFrom(obj), obj.getOutcomes())).build())
                .orElseGet(this::buildBadRequestResponse);
    }

    @GET
    @Path("/{executionId}/outcomes/{outcomeId}")
    @APIResponses(value = {
            @APIResponse(description = "Gets a specific outcome of a decision.", responseCode = "200",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.OBJECT, implementation = DecisionOutcome.class))),
            @APIResponse(description = "Bad Request", responseCode = "400", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    @Operation(summary = "Gets a specific outcome of a decision.")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOutcomeById(
            @Parameter(
                    name = "executionId",
                    description = "The execution ID.",
                    required = true,
                    schema = @Schema(implementation = String.class)) @PathParam("executionId") String executionId,
            @Parameter(
                    name = "outcomeId",
                    description = "The outcome ID.",
                    required = true,
                    schema = @Schema(implementation = String.class)) @PathParam("outcomeId") String outcomeId) {
        return retrieveDecision(executionId)
                .map(obj -> extractOutcomeByIdResponse(obj, outcomeId))
                .orElseGet(this::buildBadRequestResponse);
    }

    private Response extractStructuredInputsResponse(Decision decision) {
        if (decision.getInputs() != null) {
            return Response.ok(new DecisionStructuredInputsResponse(decision.getInputs())).build();
        }
        return buildBadRequestResponse();
    }

    private Response extractOutcomeByIdResponse(Decision decision, String outcomeId) {
        if (decision.getOutcomes() != null) {
            Optional<DecisionOutcome> decisionOutcome = decision.getOutcomes()
                    .stream()
                    .filter(outcome -> outcomeId != null && outcomeId.equals(outcome.getOutcomeId()))
                    .findFirst();
            if (decisionOutcome.isPresent()) {
                return Response.ok(decisionOutcome).build();
            }
        }
        return buildBadRequestResponse();
    }

    private Response buildBadRequestResponse() {
        return Response.status(Response.Status.BAD_REQUEST.getStatusCode()).build();
    }

    private Optional<Decision> retrieveDecision(String executionId) {
        try {
            return Optional.of(trustyService.getDecisionById(executionId));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
