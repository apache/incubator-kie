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

import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.SignalFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

public class $Type$Resource {

    Process<$Type$> process;
    @PostMapping(value = "/$signalName$", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<$Type$Output> signalProcess(@RequestHeader HttpHeaders httpHeaders,
                                                      @RequestParam(value = "businessKey", required = false) String businessKey,
                                                      @RequestBody(required = false) $signalType$ data,
                                                      UriComponentsBuilder uriComponentsBuilder) {

        $Type$ model = new $Type$();
        model.set$SetModelMethodName$(data);

        this.processService.createProcessInstance(process,
                                                  businessKey,
                                                  model,
                                                  httpHeaders.asMultiValueMap(),
                                                  httpHeaders.getOrEmpty("X-KOGITO-StartFromNode").stream().findFirst().orElse(null),
                                                  "$signalName$",
                                                  httpHeaders.getOrEmpty("X-KOGITO-ReferenceId").stream().findFirst().orElse(null),
                                                  null);

        return ResponseEntity.accepted().build();
    }

    @PostMapping(value = "/{id}/$signalPath$", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "object")))
    public $Type$Output signalInstance(
            @PathVariable("id") final String id,
            @RequestBody(required = false) final com.fasterxml.jackson.databind.JsonNode data) {

        return processService.signalProcessInstance(
                process,
                id,
                data != null
                        ? org.kie.kogito.jackson.utils.JsonObjectUtils.toJavaValue(data)
                        : null,
                "$signalName$")
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.PRECONDITION_FAILED,
                        String.format(
                                "Process instance %s is not currently accepting signal '%s'",
                                id, "$signalName$")));
    }

    @PostMapping(value = "/{id}/signal/{signalName}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "object")))
    public $Type$Output dynamicSignalInstance(@PathVariable("id") final String id,
            @PathVariable("signalName") final String signalName,
            final @RequestBody(required = false) com.fasterxml.jackson.databind.JsonNode data) {
        return processService.signalProcessInstance(process, id,
                data != null ? org.kie.kogito.jackson.utils.JsonObjectUtils.toJavaValue(data) : null, signalName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                        String.format("Process instance %s is not currently accepting signal '%s'", id, signalName)));
    }
}
