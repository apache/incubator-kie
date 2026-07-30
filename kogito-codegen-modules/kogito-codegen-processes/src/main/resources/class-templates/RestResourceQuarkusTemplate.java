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
package com.myspace.demo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.io.InputStream;
import java.util.Scanner;

import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.HeaderParam;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import org.jbpm.util.JsonSchemaUtil;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessService;
import org.kie.kogito.process.workitem.TaskModel;
import org.kie.kogito.auth.IdentityProviderFactory;
import org.kie.kogito.auth.SecurityPolicy;

import jakarta.ws.rs.core.StreamingOutput;


@Path("/$name$")
@Tag(name = "Process - $name$", description = "$documentation$")
public class $Type$Resource {

    Process<$Type$> process;

    @Inject
    ProcessService processService;

    @Inject
    IdentityProviderFactory identityProviderFactory;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createProcessInstance_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    @APIResponse(responseCode = "400", description = "Bad param")
    @APIResponse(responseCode = "201", description = "Process instance created", content={@Content(schema=@Schema(implementation=$Type$Output.class))})
    public Response createResource_$name$(@Context HttpHeaders httpHeaders,
                                          @Context UriInfo uriInfo,
                                          @QueryParam("businessKey") @DefaultValue("") String businessKey,
                                          $Type$Input resource) {
        ProcessInstance<$Type$> pi = processService.createProcessInstance(process,
                                                                          businessKey,
                                                                          Optional.ofNullable(resource).orElse(new $Type$Input()).toModel(),
                                                                          httpHeaders.getRequestHeaders(),
                                                                          httpHeaders.getHeaderString("X-KOGITO-StartFromNode"),
                                                                          null,
                                                                          httpHeaders.getHeaderString("X-KOGITO-ReferenceId"),
                                                                          null);
        return Response.created(uriInfo.getAbsolutePathBuilder().path(pi.id()).build())
                .entity(pi.checkError().variables().toModel())
                .build();
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_HTML})
    @Operation(operationId = "getAllProcessInstances_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    public Response getResources_$name$(@Context HttpHeaders headers) {
        List<$Type$Output> out = processService.getProcessInstanceOutput(process);
        boolean wantsHtml = headers.getAcceptableMediaTypes()
            .stream()
            .anyMatch(mt -> mt.isCompatible(MediaType.TEXT_HTML_TYPE) && !mt.isWildcardType() && !mt.isWildcardSubtype());

        if (wantsHtml) {
            String path = "/META-INF/resources/index.html";

        if (getClass().getResource(path) == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("HTML resource not found")
            .build();
        }

        StreamingOutput stream = os -> {
            try (InputStream inputStream = getClass().getResourceAsStream(path)) {
                inputStream.transferTo(os);
            }             
        };

        return Response.ok(stream, MediaType.TEXT_HTML_TYPE).build();
        }

        return Response.ok(out, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @GET
    @Path("schema")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getResourceSchema_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    public Map<String, Object> getResourceSchema_$name$() {
        return JsonSchemaUtil.load(this.getClass().getClassLoader(), process.id());
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getProcessInstance_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    public $Type$Output getResource_$name$(@PathParam("id") String id) {
        return processService.findById(process, id).orElseThrow(NotFoundException::new);
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "deleteProcessInstance_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    public $Type$Output deleteResource_$name$(@PathParam("id") final String id) {
        return processService.delete(process, id).orElseThrow(NotFoundException::new);
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateProcessInstance_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    public $Type$Output updateModel_$name$(@PathParam("id") String id, $Type$Input resource) {
        $Type$ model = resource.toModel();
        model.clearModifiedFields();
        return processService.update(process, id, model).orElseThrow(NotFoundException::new);
    }
    
    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "patchProcessInstance_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    public $Type$Output updateModelPartial_$name$(@PathParam("id") String id, $Type$Input resource) {
        return processService.updatePartial(process, id, resource.toModel()).orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("/{id}/tasks")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getTasksInstance_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    public List<TaskModel> getTasks_$name$(@PathParam("id") String id,
                                          @QueryParam("user") final String user,
                                          @QueryParam("group") final List<String> groups) {
        return processService.getWorkItems(process, id, SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)))
                .orElseThrow(NotFoundException::new)
                .stream()
                .map($TaskModelFactory$::from)
                .collect(Collectors.toList());
    }
}
