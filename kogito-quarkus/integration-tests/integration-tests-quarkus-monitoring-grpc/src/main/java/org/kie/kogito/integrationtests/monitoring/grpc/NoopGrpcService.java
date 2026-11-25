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
package org.kie.kogito.integrationtests.monitoring.grpc;

import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.quarkus.grpc.GrpcService;

/**
 * Quarkus only starts the gRPC server, and with it the gRPC health service, when at least one
 * {@link BindableService} bean exists. This no-op service is that bean; it needs no .proto.
 */
@GrpcService
public class NoopGrpcService implements BindableService {

    @Override
    public ServerServiceDefinition bindService() {
        return ServerServiceDefinition.builder("noop.Noop").build();
    }
}
