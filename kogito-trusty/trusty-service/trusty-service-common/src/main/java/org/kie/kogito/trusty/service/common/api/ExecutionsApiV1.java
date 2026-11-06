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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.kie.kogito.trusty.service.common.TrustyService;
import org.kie.kogito.trusty.service.common.models.MatchedExecutionHeaders;
import org.kie.kogito.trusty.service.common.responses.ExecutionHeaderResponse;
import org.kie.kogito.trusty.service.common.responses.ExecutionsResponse;
import org.kie.kogito.trusty.service.common.responses.ResponseUtils;
import org.kie.kogito.trusty.storage.api.model.decision.DMNModelMetadata;
import org.kie.kogito.trusty.storage.api.model.decision.DMNModelWithMetadata;
import org.kie.kogito.trusty.storage.api.model.decision.Decision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The trusty api resource.
 */
@Path("executions")
public class ExecutionsApiV1 {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionsApiV1.class);

    @Inject
    TrustyService trustyService;

    /**
     * Gets all the headers of the executions that were evaluated within a specified time range.
     *
     * @param from The start datetime.
     * @param to The end datetime.
     * @param limit The maximum (non-negative) number of items to be returned.
     * @param offset The non-negative pagination offset.
     * @param prefix The executionId prefix to be matched in the search.
     * @return The execution headers that satisfy the time range, pagination and prefix conditions.
     */
    @GET
    @APIResponses(value = {
            @APIResponse(description = "Returns the execution headers.",
                    responseCode = "200",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(type = SchemaType.OBJECT, implementation = ExecutionsResponse.class))),
            @APIResponse(description = "Bad Request", responseCode = "400", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    @Operation(summary = "Gets the execution headers", description = "Gets the execution headers.")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getExecutions(
            @Parameter(
                    name = "from",
                    description = "Start datetime for the lookup. Date in the format \"yyyy-MM-dd'T'HH:mm:ssZ\"",
                    required = false,
                    schema = @Schema(implementation = String.class)) @DefaultValue("yesterday") @QueryParam("from") String from,
            @Parameter(
                    name = "to",
                    description = "End datetime for the lookup. Date in the format \"yyyy-MM-dd'T'HH:mm:ssZ\"",
                    required = false,
                    schema = @Schema(implementation = String.class)) @DefaultValue("now") @QueryParam("to") String to,
            @Parameter(
                    name = "limit",
                    description = "Maximum number of results to return.",
                    required = false,
                    schema = @Schema(implementation = Integer.class)) @DefaultValue("100") @QueryParam("limit") int limit,
            @Parameter(
                    name = "offset",
                    description = "Offset for the pagination.",
                    required = false,
                    schema = @Schema(implementation = Integer.class)) @DefaultValue("0") @QueryParam("offset") int offset,
            @Parameter(
                    name = "search",
                    description = "Execution ID prefix to be matched",
                    required = false,
                    schema = @Schema(implementation = String.class)) @DefaultValue("") @QueryParam("search") String prefix) {

        if (limit < 0 || offset < 0) {
            return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), "Pagination parameters can not have negative values.").build();
        }

        OffsetDateTime fromDate;
        OffsetDateTime toDate;
        try {
            fromDate = parseParameterDate(from, true);
            toDate = parseParameterDate(to, false);
        } catch (DateTimeParseException e) {
            LOGGER.warn("Invalid date", e);
            return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), "Date format should be yyyy-MM-dd'T'HH:mm:ssZ").build();
        }

        MatchedExecutionHeaders result = trustyService.getExecutionHeaders(fromDate, toDate, limit, offset, prefix);

        List<ExecutionHeaderResponse> headersResponses = new ArrayList<>();
        result.getExecutions().forEach(x -> headersResponses.add(ResponseUtils.executionHeaderResponseFrom(x)));
        return Response.ok(new ExecutionsResponse(result.getAvailableResults(), limit, offset, headersResponses)).build();
    }

    private OffsetDateTime parseParameterDate(String date, boolean localDateAtStartOfDay) {
        if (date.equals("yesterday")) {
            return OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        }
        if (date.equals("now")) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }

        try {
            return ZonedDateTime.parse(date, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toOffsetDateTime();
        } catch (DateTimeParseException e) {
            ZonedDateTime zonedDateTime = LocalDate.parse(date, DateTimeFormatter.ISO_DATE).atStartOfDay(ZoneId.systemDefault());
            return localDateAtStartOfDay
                    ? zonedDateTime.toOffsetDateTime()
                    : zonedDateTime.toOffsetDateTime().plusDays(1).minusNanos(1);
        }
    }

    /**
     * Gets the model associated with an execution.
     *
     * @param executionId The execution ID.
     * @return The model associated with the execution.
     */
    @GET
    @Path("/{executionId}/model")
    @APIResponses(value = {
            @APIResponse(description = "Gets the model associated with an execution.", responseCode = "200",
                    content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = SchemaType.OBJECT, implementation = DMNModelWithMetadata.class))),
            @APIResponse(description = "Bad Request", responseCode = "400", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    @Operation(summary = "Gets the model associated with an execution.")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getModel(
            @Parameter(
                    name = "executionId",
                    description = "The execution ID.",
                    required = true,
                    schema = @Schema(implementation = String.class)) @PathParam("executionId") String executionId) {
        return retrieveModel(executionId)
                .map(definition -> Response.ok(definition).build())
                .orElseGet(() -> Response.status(Response.Status.BAD_REQUEST.getStatusCode()).build());
    }

    private Optional<DMNModelWithMetadata> retrieveModel(String executionId) {
        try {
            Optional<Decision> decision = retrieveDecision(executionId);
            //TODO GAV components are provided but unused. See https://issues.redhat.com/browse/FAI-239
            return decision.map(d -> trustyService.getModelById(
                    new DMNModelMetadata(null, null, null, null, d.getExecutedModelName(),
                            d.getExecutedModelNamespace()),
                    DMNModelWithMetadata.class));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Optional<Decision> retrieveDecision(String executionId) {
        try {
            return Optional.ofNullable(trustyService.getDecisionById(executionId));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
