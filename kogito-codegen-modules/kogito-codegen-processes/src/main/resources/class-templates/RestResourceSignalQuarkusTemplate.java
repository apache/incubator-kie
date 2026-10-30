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

import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;

import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;

public class $Type$Resource {

    Process<$Type$> process;

    @POST
    @Path("/$signalPath$")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response signalProcess(@Context HttpHeaders httpHeaders,
                                  @Context UriInfo uriInfo,
                                  @QueryParam("businessKey") @DefaultValue("") String businessKey,
                                  $signalType$ data) {
        $Type$ model = new $Type$();
        model.set$SetModelMethodName$(data);
        this.processService.createProcessInstance(process,
                                                  businessKey,
                                                  model,
                                                  httpHeaders.getRequestHeaders(),
                                                  httpHeaders.getHeaderString("X-KOGITO-StartFromNode"),
                                                  "$signalName$",
                                                  httpHeaders.getHeaderString("X-KOGITO-ReferenceId"),
                                                  null);
        return Response.accepted().build();
    }

    @POST
    @Path("/{id}/$signalPath$")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @org.eclipse.microprofile.openapi.annotations.parameters.RequestBody(content = @org.eclipse.microprofile.openapi.annotations.media.Content(schema = @org.eclipse.microprofile.openapi.annotations.media.Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.OBJECT)))
    public $Type$Output signalInstance(
                    @PathParam("id") final String id,
                    final com.fasterxml.jackson.databind.JsonNode data) {

            return processService.signalProcessInstance(
                            process,
                            id,
                            data != null ? org.kie.kogito.jackson.utils.JsonObjectUtils.toJavaValue(data) : null, "$signalName$")
                            .orElseThrow(() -> new ClientErrorException(
                                            String.format("Process instance %s is not currently accepting signal '%s'",
                                                            id, "$signalName$"),
                                            Response.Status.PRECONDITION_FAILED));
    }

    @POST
    @Path("/{id}/signal/{signalName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @org.eclipse.microprofile.openapi.annotations.parameters.RequestBody(
            content = @org.eclipse.microprofile.openapi.annotations.media.Content(
                    schema = @org.eclipse.microprofile.openapi.annotations.media.Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.OBJECT)))
    public $Type$Output dynamicSignalInstance(
            @PathParam("id") final String id,
            @PathParam("signalName") final String signalName,
            final com.fasterxml.jackson.databind.JsonNode data) {

        return processService.signalProcessInstance(
                process, id, data != null ? org.kie.kogito.jackson.utils.JsonObjectUtils.toJavaValue(data) : null, signalName)
                .orElseThrow(() -> new ClientErrorException(
                        String.format(
                                "Process instance %s is not currently accepting signal '%s'",
                                id, signalName),
                        Response.Status.PRECONDITION_FAILED));
    }

}
