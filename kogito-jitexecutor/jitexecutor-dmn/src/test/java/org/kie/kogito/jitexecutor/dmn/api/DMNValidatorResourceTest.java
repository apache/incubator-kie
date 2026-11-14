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
package org.kie.kogito.jitexecutor.dmn.api;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kie.dmn.api.core.DMNMessageType;
import org.kie.kogito.jitexecutor.dmn.responses.JITDMNMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.kie.kogito.jitexecutor.dmn.TestingUtils.getModelFromIoUtils;

@QuarkusTest
public class DMNValidatorResourceTest {

    private static final Logger LOG = LoggerFactory.getLogger(DMNValidatorResourceTest.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    static {
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    private static final CollectionType LIST_OF_MSGS = MAPPER.getTypeFactory()
            .constructCollectionType(List.class,
                    JITDMNMessage.class);

    @Test
    public void test() throws IOException {
        final String MODEL = getModelFromIoUtils("valid_models/DMNv1_x/loan.dmn");
        String response = given()
                .contentType(ContentType.XML)
                .body(MODEL)
                .when()
                .post("/jitdmn/validate")
                .then()
                .statusCode(200)
                .body(containsString("Decision Table Analysis of table 'Preapproval' finished with no messages to be reported."))
                .extract()
                .asString();

        LOG.debug("Validate response: {}", response);
        List<JITDMNMessage> messages = MAPPER.readValue(response, LIST_OF_MSGS);
        assertThat(messages.size()).isEqualTo(1);
        assertThat(messages.stream().anyMatch(m -> m.getSourceId().equals("_E7994A2B-1189-4BE5-9382-891D48E87D47") &&
                m.getMessage().equals("Decision Table Analysis of table 'Preapproval' finished with no messages to be reported."))).isTrue();
    }

    @Test
    public void testOverlap() throws IOException {
        final String MODEL = getModelFromIoUtils("invalid_models/DMNv1_x/loan_withOverlap.dmn");
        String response = given()
                .contentType(ContentType.XML)
                .body(MODEL)
                .when()
                .post("/jitdmn/validate")
                .then()
                .statusCode(200)
                .body(containsString("Overlap detected"))
                .extract()
                .asString();

        LOG.debug("Validate response: {}", response);
        List<JITDMNMessage> messages = MAPPER.readValue(response, LIST_OF_MSGS);
        assertThat(messages.isEmpty()).isFalse();
        assertThat(messages.stream().anyMatch(m -> m.getSourceId().equals("_E7994A2B-1189-4BE5-9382-891D48E87D47") &&
                m.getMessageType().equals(DMNMessageType.DECISION_TABLE_OVERLAP_HITPOLICY_UNIQUE))).isTrue();
    }
}
