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
package org.kie.kogito.jitexecutor.dmn;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.kie.kogito.jitexecutor.dmn.api.DMNResourceHelper;

import jakarta.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DMNResourceHelperTest {

    @Test
    public void testManageResponseWithSuccess() {
        Supplier<Response> responseSupplier = () -> Response.ok("Success").build();
        try (Response response = DMNResourceHelper.manageResponse(responseSupplier)) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
            assertThat(response.getEntity()).isEqualTo("Success");
        }

    }

    @Test
    public void testManageResponseWithFailure() {
        Supplier<Response> responseSupplier = mock(Supplier.class);
        when(responseSupplier.get()).thenThrow(new IllegalStateException("Error : Failed to validate"));
        try (Response response = DMNResourceHelper.manageResponse(responseSupplier)) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
            assertThat(response.getEntity()).isEqualTo("Error : Failed to validate");
        }

    }

}
