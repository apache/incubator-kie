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
package org.kie.kogito.event.cloudevents.utils;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.cloudevents.SpecVersion;

import static io.cloudevents.core.v03.CloudEventV03.DATACONTENTTYPE;
import static io.cloudevents.core.v03.CloudEventV03.ID;
import static io.cloudevents.core.v03.CloudEventV03.SOURCE;
import static io.cloudevents.core.v03.CloudEventV03.SPECVERSION;
import static io.cloudevents.core.v03.CloudEventV03.TIME;
import static io.cloudevents.core.v03.CloudEventV03.TYPE;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.kie.kogito.event.cloudevents.utils.CloudEventUtils.DATA;

class CloudEventValidatorV03Test extends BaseCloudEventValidatorTest<CloudEventValidatorV03> {

    private static final CloudEventValidatorV03 CLOUD_EVENT_VALIDATOR = CloudEventValidatorV03.getInstance();

    CloudEventValidatorV03Test() {
        super(CLOUD_EVENT_VALIDATOR, SpecVersion.V03);
    }

    @Override
    protected Map<String, Object> createValidCloudEvent() {
        Map<String, Object> cloudEvent = new HashMap<>();
        cloudEvent.put(SPECVERSION, SpecVersion.V03.toString());
        cloudEvent.put(ID, "abc-123");
        cloudEvent.put(SOURCE, "/myapp");
        cloudEvent.put(TYPE, "com.example.someevent");
        cloudEvent.put(DATACONTENTTYPE, "application/json");
        cloudEvent.put(TIME, "2023-05-03T12:34:56Z");
        cloudEvent.put(DATA, "{\"foo\":\"bar\"}");
        return cloudEvent;
    }

    @Test
    void emptySourceShouldBeValid() {
        Map<String, Object> cloudEvent = createValidCloudEvent();
        cloudEvent.put(SOURCE, "");

        assertThatCode(() -> CLOUD_EVENT_VALIDATOR.validateCloudEvent(createValidCloudEvent()))
                .doesNotThrowAnyException();
    }
}