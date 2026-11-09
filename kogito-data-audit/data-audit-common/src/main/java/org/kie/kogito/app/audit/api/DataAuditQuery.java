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

public class DataAuditQuery {

    private String identifier;

    private String graphQLDefinition;

    private String query;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getGraphQLDefinition() {
        return graphQLDefinition;
    }

    public void setGraphQLDefinition(String graphQLDefinition) {
        this.graphQLDefinition = graphQLDefinition;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    @Override
    public String toString() {
        return "DataAuditQuery [identifier="
                + sanitize(identifier)
                + ", graphQLDefinition="
                + sanitize(graphQLDefinition)
                + ", query="
                + sanitize(query)
                + "]";
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        // Replace all control characters (CR, LF, tab, null byte, etc.) to prevent
        // CWE-117 Log Forging via user-supplied input reaching the log sink.
        return value.replaceAll("[\\p{Cntrl}]+", "_");
    }

}
