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
package org.kie.kogito.app.audit.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataAuditQueryTest {

    @Test
    void testToStringWithNullFields() {
        DataAuditQuery query = new DataAuditQuery();
        assertThat(query.toString())
                .isEqualTo("DataAuditQuery [identifier=null, graphQLDefinition=null, query=null]");
    }

    @Test
    void testToStringWithCleanInput() {
        DataAuditQuery query = new DataAuditQuery();
        query.setIdentifier("audit-123");
        query.setGraphQLDefinition("query { getAudit }");
        query.setQuery("SELECT * FROM Audit");

        assertThat(query.toString())
                .isEqualTo("DataAuditQuery [identifier=audit-123, graphQLDefinition=query { getAudit }, query=SELECT * FROM Audit]");
    }

    @Test
    void testSanitizeSingleControlCharacters() {
        DataAuditQuery query = new DataAuditQuery();
        query.setIdentifier("id\nwith\nnewlines");
        query.setGraphQLDefinition("def\rwith\rcr");
        query.setQuery("query\twith\ttabs");

        assertThat(query.toString())
                .isEqualTo("DataAuditQuery [identifier=id_with_newlines, graphQLDefinition=def_with_cr, query=query_with_tabs]");
    }

    @Test
    void testSanitizeConsecutiveControlCharactersCollapsed() {
        DataAuditQuery query = new DataAuditQuery();
        query.setIdentifier("id\r\nwith\r\n\tcrlf");
        query.setGraphQLDefinition("def\n\n\nmultiple");
        query.setQuery("query\0null\r\nbyte");

        assertThat(query.toString())
                .isEqualTo("DataAuditQuery [identifier=id_with_crlf, graphQLDefinition=def_multiple, query=query_null_byte]");
    }

    @Test
    void testCwe117LogForgingInjectionAttempt() {
        DataAuditQuery query = new DataAuditQuery();
        String injectionPayload = "audit-1\r\n2023-01-01 10:00:00 [ERROR] Forged log message\r\nidentifier: audit-2";
        query.setIdentifier(injectionPayload);

        String result = query.toString();

        assertThat(result)
                .doesNotContain("\r")
                .doesNotContain("\n")
                .isEqualTo("DataAuditQuery [identifier=audit-1_2023-01-01 10:00:00 [ERROR] Forged log message_identifier: audit-2, graphQLDefinition=null, query=null]");
    }
}
