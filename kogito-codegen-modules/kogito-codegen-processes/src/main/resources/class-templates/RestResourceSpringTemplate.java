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

import org.jbpm.util.JsonSchemaUtil;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessService;
import org.kie.kogito.process.workitem.TaskModel;
import org.kie.kogito.auth.IdentityProviderFactory;
import org.kie.kogito.auth.SecurityPolicy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/$name$")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Process - $name$", description = "$documentation$")
public class $Type$Resource {

    Process<$Type$> process;

    @Autowired
    ProcessService processService;

    @Autowired
    IdentityProviderFactory identityProviderFactory;

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createProcessInstance_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    public ResponseEntity<$Type$Output> createResource_$name$(@RequestHeader HttpHeaders httpHeaders,
                                                              @RequestParam(value = "businessKey", required = false) String businessKey,
                                                              @RequestBody(required = false) $Type$Input resource,
                                                              UriComponentsBuilder uriComponentsBuilder) {
        ProcessInstance<$Type$> pi = processService.createProcessInstance(process,
                                                                          businessKey,
                                                                          Optional.ofNullable(resource).orElse(new $Type$Input()).toModel(),
                                                                          httpHeaders.asMultiValueMap(),
                                                                          httpHeaders.getOrEmpty("X-KOGITO-StartFromNode").stream().findFirst().orElse(null),
                                                                          null,
                                                                          httpHeaders.getOrEmpty("X-KOGITO-ReferenceId").stream().findFirst().orElse(null),
                                                                          null);
        return ResponseEntity.created(uriComponentsBuilder.path("/$name$/{id}").buildAndExpand(pi.id()).toUri())
                .body(pi.checkError().variables().toModel());
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getAllProcessInstances_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    public List<$Type$Output> getResources_$name$() {
        return processService.getProcessInstanceOutput(process);
    }

    @GetMapping(value = "/schema", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getResourceSchema_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    public Map<String, Object> getResourceSchema_$name$() {
        return JsonSchemaUtil.load(this.getClass().getClassLoader(), process.id());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getProcessInstance_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    public $Type$Output getResource_$name$(@PathVariable("id") String id) {
        return processService.findById(process, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "deleteProcessInstance_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    public $Type$Output deleteResource_$name$(@PathVariable("id") final String id) {
        return processService.delete(process, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateProcessInstance_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    public $Type$Output updateModel_$name$(@PathVariable("id") String id, @RequestBody(required = false) $Type$Input resource) {
        $Type$ model = resource.toModel();
        model.clearModifiedFields();
        return processService.update(process, id, model).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    
    @PatchMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "patchProcessInstance_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    public $Type$Output updateModelPartial_$name$(@PathVariable("id") String id, @RequestBody(required = false) $Type$Input resource) {
        return processService.updatePartial(process, id, resource.toModel()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping(value = "/{id}/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getTasksInstance_$name$", summary = "$documentation$", description = "$processInstanceDescription$")
    public List<TaskModel> getTasks_$name$(@PathVariable("id") String id,
                                           @RequestParam(value = "user", required = false) final String user,
                                           @RequestParam(value = "group", required = false) final List<String> groups) {
        return processService.getWorkItems(process, id, SecurityPolicy.of(identityProviderFactory.getOrImpersonateIdentity(user, groups)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))
                .stream()
                .map($TaskModelFactory$::from)
                .collect(Collectors.toList());
    }
}
