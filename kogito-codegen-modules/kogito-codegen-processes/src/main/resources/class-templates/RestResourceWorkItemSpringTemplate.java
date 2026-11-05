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

import org.jbpm.util.JsonSchemaUtil;
import org.kie.kogito.auth.SecurityPolicy;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.process.SignalFactory;
import org.kie.kogito.process.workitem.Comment;
import org.kie.kogito.process.workitem.TaskMetaInfo;
import org.kie.kogito.services.uow.UnitOfWorkExecutor;
import org.kie.kogito.usertask.UserTaskService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.beans.factory.annotation.Autowired;

public class $Type$Resource {
    
    @PostMapping(value = "/{id}/$taskName$/trigger", produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity signal(@PathVariable("id") final String id,
            @RequestParam("user") final String user,
            @RequestParam("group") final List<String> groups,
            final UriComponentsBuilder uriComponentsBuilder) {

        return processService.signalWorkItem(process, id, "$taskNodeName$", SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)))
                .map(task -> ResponseEntity
                        .created(uriComponentsBuilder
                                .path("/$name$/{id}/$taskName$/{taskId}")
                                .buildAndExpand(id, task.getId()).toUri())
                        .body(task))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping(value = "/{id}/$taskName$/{taskId}/phases/{phase}", produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public $Type$Output completeTask(@PathVariable("id") final String id,
            @PathVariable("taskId") final String taskId,
            @PathVariable("phase") final String phase,
            @RequestParam("user") final String user,
            @RequestParam("group") final List<String> groups,
            @RequestBody(required = false) final $TaskOutput$ model) {
        return processService.transitionWorkItem(process, id, taskId, phase, SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)), model)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping(value = "/{id}/$taskName$/{taskId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public $TaskOutput$ saveTask(@PathVariable("id") final String id,
            @PathVariable("taskId") final String taskId,
            @RequestParam(value = "user", required = false) final String user,
            @RequestParam(value = "group", required = false) final List<String> groups,
            @RequestBody(required = false) final $TaskOutput$ model) {
        return processService.setWorkItemOutput(process, id, taskId, SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)), model, $TaskOutput$::fromMap)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping(value = "/{id}/$taskName$/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public $Type$Output taskTransition(@PathVariable("id") final String id,
            @PathVariable("taskId") final String taskId,
            @RequestParam(value = "phase", required = false,
                    defaultValue = "complete") final String phase,
            @RequestParam(value = "user",
                    required = false) final String user,
            @RequestParam(value = "group",
                    required = false) final List<String> groups,
            @RequestBody(required = false) final $TaskOutput$ model) {
        return processService.transitionWorkItem(process, id, taskId, phase, SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)), model)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping(value = "/{id}/$taskName$/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public $TaskModel$ getTask(@PathVariable("id") String id,
            @PathVariable("taskId") String taskId,
            @RequestParam(value = "user", required = false) final String user,
            @RequestParam(value = "group",
                    required = false) final List<String> groups) {
        return processService.getWorkItem(process, id, taskId, SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)), $TaskModel$::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @DeleteMapping(value = "/{id}/$taskName$/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public $Type$Output abortTask(@PathVariable("id") final String id,
            @PathVariable("taskId") final String taskId,
            @RequestParam(value = "phase", required = false,
                    defaultValue = "abort") final String phase,
            @RequestParam(value = "user", required = false) final String user,
            @RequestParam(value = "group",
                    required = false) final List<String> groups) {
        return processService.transitionWorkItem(process, id, taskId, phase, SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)), null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping(value = "$taskName$/schema", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getSchema() {
        return JsonSchemaUtil.load(this.getClass().getClassLoader(), process.id(), "$taskName$");
    }

    @GetMapping(value = "/{id}/$taskName$/{taskId}/schema", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getSchemaAndPhases(@PathVariable("id") final String id,
            @PathVariable("taskId") final String taskId,
            @RequestParam(value = "user", required = false) final String user,
            @RequestParam(value = "group",
                    required = false) final List<String> groups) {
        return processService.getWorkItemSchemaAndPhases(process, id, taskId, "$taskName$", SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)));
    }

}
