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
package org.kie.kogito.persistence.postgresql.reporting.api;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.kie.kogito.persistence.postgresql.reporting.database.GenericPostgresDatabaseManagerImpl;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresMappingDefinition;
import org.kie.kogito.persistence.postgresql.reporting.model.PostgresMappingDefinitions;
import org.kie.kogito.persistence.postgresql.reporting.service.PostgresMappingServiceImpl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("mappings")
@ApplicationScoped
public class PostgresMappingsApiV1
        extends BasePostgresMappingsApiV1 {

    public PostgresMappingsApiV1() {
        //CDI proxies
    }

    @Inject
    public PostgresMappingsApiV1(final PostgresMappingServiceImpl mappingService,
            final GenericPostgresDatabaseManagerImpl databaseManager) {
        super(mappingService, databaseManager);
    }

    @GET
    @APIResponses(value = {
            @APIResponse(description = "Returns all Mapping Definitions.",
                    responseCode = "200",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(type = SchemaType.OBJECT, implementation = PostgresMappingDefinitions.class))),
            @APIResponse(description = "Bad Request", responseCode = "400", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    @Operation(summary = "Gets all Mapping Definitions.", description = "Gets all Mapping Definitions.")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Response getAllMappingDefinitions() {
        return super.getAllMappingDefinitions();
    }

    @GET
    @Path("/{mappingId}")
    @APIResponses(value = {
            @APIResponse(description = "Gets the Mapping Definition by ID.", responseCode = "200",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.OBJECT, implementation = PostgresMappingDefinition.class))),
            @APIResponse(description = "Bad Request", responseCode = "400", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    @Operation(summary = "Gets the Mapping Definition by ID.", description = "Gets the Mapping Definition by ID.")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Response getMappingDefinitionById(@Parameter(
            name = "mappingId",
            description = "The Mapping Definition ID.",
            required = true,
            schema = @Schema(implementation = String.class)) @PathParam("mappingId") final String mappingId) {
        return super.getMappingDefinitionById(mappingId);
    }

    @POST
    @APIResponses(value = {
            @APIResponse(description = "Creates a new Mapping Definition.",
                    responseCode = "200",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(type = SchemaType.OBJECT, implementation = PostgresMappingDefinition.class))),
            @APIResponse(description = "Bad Request", responseCode = "400", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    @Operation(summary = "Creates a new Mapping Definition.", description = "Creates a new Mapping Definition.")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Response createMappingDefinition(PostgresMappingDefinition definition) {
        return super.createMappingDefinition(definition);
    }

    @DELETE
    @Path("/{mappingId}")
    @APIResponses(value = {
            @APIResponse(description = "Deleted an existing Mapping Definition.",
                    responseCode = "200",
                    content = @Content(mediaType = MediaType.TEXT_PLAIN)),
            @APIResponse(description = "Bad Request", responseCode = "400", content = @Content(mediaType = MediaType.TEXT_PLAIN))
    })
    @Operation(summary = "Deleted an existing Mapping Definition.", description = "Deleted an existing Mapping Definition.")
    @Produces(MediaType.TEXT_PLAIN)
    @Override
    public Response deleteMappingDefinitionById(@Parameter(
            name = "mappingId",
            description = "The Mapping Definition ID.",
            required = true,
            schema = @Schema(implementation = String.class)) @PathParam("mappingId") final String mappingId) {
        return super.deleteMappingDefinitionById(mappingId);
    }

    @Override
    protected PostgresMappingDefinitions buildMappingDefinitions(final List<PostgresMappingDefinition> definitions) {
        return new PostgresMappingDefinitions(definitions);
    }
}
