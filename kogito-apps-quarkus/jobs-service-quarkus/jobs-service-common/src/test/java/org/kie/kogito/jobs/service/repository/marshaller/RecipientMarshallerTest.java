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
package org.kie.kogito.jobs.service.repository.marshaller;

import org.junit.jupiter.api.Test;
import org.kie.kogito.jobs.service.api.recipient.http.HttpRecipient;
import org.kie.kogito.jobs.service.model.Recipient;
import org.kie.kogito.jobs.service.model.RecipientInstance;

import io.vertx.core.json.JsonObject;

import static org.assertj.core.api.Assertions.assertThat;

class RecipientMarshallerTest {

    RecipientMarshaller marshaller = new RecipientMarshaller();

    @Test
    void marshall() {
        Recipient recipient = new RecipientInstance(HttpRecipient.builder().forStringPayload().url("test").build());
        JsonObject jsonObject = marshaller.marshall(recipient);
        assertThat(jsonObject).isEqualTo(buildRecipient());
    }

    private static JsonObject buildRecipient() {
        return JsonObject
                .mapFrom(HttpRecipient.builder().forStringPayload().url("test").build())
                .put(RecipientMarshaller.CLASS_TYPE, HttpRecipient.class.getName());
    }

    @Test
    void marshallNull() {
        JsonObject jsonObject = marshaller.marshall(null);
        assertThat(jsonObject).isNull();
    }

    @Test
    void unmarshall() {
        JsonObject jsonObject = buildRecipient();
        Recipient recipient = marshaller.unmarshall(jsonObject);
        assertThat(recipient.getRecipient()).isEqualTo(HttpRecipient.builder().forStringPayload().url("test").build());
    }

    @Test
    void unmarshallInvalid() {
        JsonObject jsonObject = new JsonObject();
        Recipient recipient = marshaller.unmarshall(jsonObject);
        assertThat(recipient).isNull();
    }

    @Test
    void unmarshallNull() {
        Recipient recipient = marshaller.unmarshall(null);
        assertThat(recipient).isNull();
    }
}
