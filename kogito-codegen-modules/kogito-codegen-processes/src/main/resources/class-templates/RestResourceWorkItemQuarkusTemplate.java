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

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.kie.kogito.auth.SecurityPolicy;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstanceReadMode;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.process.SignalFactory;
import org.kie.kogito.services.uow.UnitOfWorkExecutor;
import org.kie.kogito.usertask.UserTaskService;

import jakarta.inject.Inject;

public class $Type$Resource {
    
    @POST
    @Path("/{id}/$taskName$/trigger")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response signal(@PathParam("id") final String id,
            @QueryParam("user") final String user,
            @QueryParam("group") final List<String> groups,
            @Context UriInfo uriInfo) {
        return processService.signalWorkItem(process, id, "$taskNodeName$", SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)))
                .map(task -> Response
                        .created(uriInfo.getAbsolutePathBuilder().path(task.getId()).build())
                        .entity(task)
                        .build())
                .orElseThrow(NotFoundException::new);
    }

    @POST
    @Path("/{id}/$taskName$/{taskId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public $Type$Output completeTask(@PathParam("id") final String id,
            @PathParam("taskId") final String taskId,
            @QueryParam("phase") @DefaultValue("complete") final String phase,
            @QueryParam("user") final String user,
            @QueryParam("group") final List<String> groups,
            final $TaskOutput$ model) {
        return processService.transitionWorkItem(process, id, taskId, phase, SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)), model)
                .orElseThrow(NotFoundException::new);
    }

    @PUT
    @Path("/{id}/$taskName$/{taskId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public $TaskOutput$ saveTask(@PathParam("id") final String id,
            @PathParam("taskId") final String taskId,
            @QueryParam("user") final String user,
            @QueryParam("group") final List<String> groups,
            final $TaskOutput$ model) {
        return processService.setWorkItemOutput(process, id, taskId, SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)), model, $TaskOutput$::fromMap)
                .orElseThrow(NotFoundException::new);
    }

    @POST
    @Path("/{id}/$taskName$/{taskId}/phases/{phase}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public $Type$Output taskTransition(
            @PathParam("id") final String id,
            @PathParam("taskId") final String taskId,
            @PathParam("phase") final String phase,
            @QueryParam("user") final String user,
            @QueryParam("group") final List<String> groups,
            final $TaskOutput$ model) {
        return processService.transitionWorkItem(process, id, taskId, phase, SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)), model)
                .orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("/{id}/$taskName$/{taskId}")
    @Produces(MediaType.APPLICATION_JSON)
    public $TaskModel$ getWorkItem(@PathParam("id") String id,
            @PathParam("taskId") String taskId,
            @QueryParam("user") final String user,
            @QueryParam("group") final List<String> groups) {
        return processService.getWorkItem(process, id, taskId, SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)), $TaskModel$::from)
                .orElseThrow(NotFoundException::new);
    }

    @DELETE
    @Path("/{id}/$taskName$/{taskId}")
    @Produces(MediaType.APPLICATION_JSON)
    public $Type$Output abortTask(@PathParam("id") final String id,
            @PathParam("taskId") final String taskId,
            @QueryParam("phase") @DefaultValue("abort") final String phase,
            @QueryParam("user") final String user,
            @QueryParam("group") final List<String> groups) {
        return processService.transitionWorkItem(process, id, taskId, phase, SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)), null)
                .orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("$taskName$/schema")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getSchema() {
        return JsonSchemaUtil.load(this.getClass().getClassLoader(), process.id(), "$taskName$");
    }

    @GET
    @Path("/{id}/$taskName$/{taskId}/schema")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getSchemaAndPhases(@PathParam("id") final String id,
            @PathParam("taskId") final String taskId,
            @QueryParam("user") final String user,
            @QueryParam("group") final List<String> groups) {
        return processService.getWorkItemSchemaAndPhases(process, id, taskId, "$taskName$", SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)));
    }

}
