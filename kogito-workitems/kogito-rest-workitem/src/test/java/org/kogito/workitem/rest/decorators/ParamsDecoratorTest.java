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
package org.kogito.workitem.rest.decorators;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.mutiny.ext.web.client.HttpRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ParamsDecoratorTest {

    @Test
    void testPrefixParamsDecorator() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("HEADER_pepe", "pepa");
        parameters.put("QUERY_javierito", "real betis balompie");
        testParamDecorator(new PrefixParamsDecorator(), parameters);
    }

    @Test
    void testCollectionParamsDecorator() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("pepe", "pepa");
        parameters.put("javierito", "real betis balompie");
        testParamDecorator(new CollectionParamsDecorator(Collections.singleton("pepe"), Collections.singleton("javierito")), parameters);
    }

    private void testParamDecorator(ParamsDecorator decorator, Map<String, Object> parameters) {
        HttpRequest<?> request = mock(HttpRequest.class);
        decorator.decorate(mock(KogitoWorkItem.class), parameters, request);
        verify(request).addQueryParam("javierito", "real betis balompie");
        verify(request).putHeader("pepe", "pepa");
    }

}
