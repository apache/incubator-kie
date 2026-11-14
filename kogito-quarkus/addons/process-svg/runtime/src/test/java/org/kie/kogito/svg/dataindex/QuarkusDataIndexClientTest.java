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
package org.kie.kogito.svg.dataindex;

import java.net.MalformedURLException;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

public class QuarkusDataIndexClientTest {

    private final static String jsonString = "{\n" +
            "  \"data\": {\n" +
            "    \"ProcessInstances\": [\n" +
            "      {\n" +
            "        \"id\": \"piId\",\n" +
            "        \"processId\": \"processId\",\n" +
            "        \"nodes\": [\n" +
            "          {\n" +
            "            \"definitionId\": \"_9861B686-DF6B-4B1C-B370-F9898EEB47FD\",\n" +
            "            \"exit\": \"2020-10-11T06:49:47.26Z\"\n" +
            "          },\n" +
            "          {\n" +
            "            \"definitionId\": \"_8B62D3CA-5D03-4B2B-832B-126469288BB4\",\n" +
            "            \"exit\": null\n" +
            "          }\n" +
            "        ]\n" +
            "      } " +
            "    ]\n" +
            "  }\n" +
            "}";

    QuarkusDataIndexClient client = new QuarkusDataIndexClient(null, null, null);

    @Test
    public void testGetNodeInstancesFromResponse() {
        JsonObject response = (JsonObject) Json.decodeValue(jsonString);
        List<NodeInstance> nodes = client.getNodeInstancesFromResponse(response);
        assertThat(nodes).hasSize(2).containsExactly(
                new NodeInstance(true, "_9861B686-DF6B-4B1C-B370-F9898EEB47FD"),
                new NodeInstance(false, "_8B62D3CA-5D03-4B2B-832B-126469288BB4"));
    }

    @Test
    public void testGetEmptyNodeInstancesFromResponse() {
        JsonObject response = new JsonObject().put("data", new JsonObject().put("ProcessInstances", new JsonArray()));
        List<NodeInstance> nodes = client.getNodeInstancesFromResponse(response);
        assertThat(nodes).isEmpty();
    }

    @Test
    public void testWebClientToURLOptionsMalformedURL() {
        assertThatExceptionOfType(MalformedURLException.class).isThrownBy(() -> client.getWebClientToURLOptions("malformedURL"));
    }

    @Test
    public void testSetupMalformedURL() {
        QuarkusDataIndexClient testClient = new QuarkusDataIndexClient("malformedURL", null, null);
        assertThatExceptionOfType(MalformedURLException.class).isThrownBy(() -> testClient.setup());
    }

    @Test
    public void testWebClientToURLOptions() throws MalformedURLException {
        String defaultHost = "localhost";
        int defaultPort = 8180;
        WebClientOptions webClientOptions = client.getWebClientToURLOptions("http://" + defaultHost + ":" + defaultPort);
        assertThat(webClientOptions.getDefaultHost()).isEqualTo(defaultHost);
        assertThat(webClientOptions.getDefaultPort()).isEqualTo(defaultPort);
    }

    @Test
    public void testWebClientToURLOptionsWithoutPort() throws MalformedURLException {
        String dataindexurl = "http://dataIndex.com";
        WebClientOptions webClientOptions = client.getWebClientToURLOptions(dataindexurl);
        assertThat(webClientOptions.getDefaultPort()).isEqualTo(80);
        assertThat(webClientOptions.getDefaultHost()).isEqualTo("dataIndex.com");
        assertThat(webClientOptions.isSsl()).isFalse();
    }

    @Test
    public void testWebClientToURLOptionsWithoutPortSSL() throws MalformedURLException {
        String dataindexurl = "https://dataIndex.com";
        WebClientOptions webClientOptions = client.getWebClientToURLOptions(dataindexurl);
        assertThat(webClientOptions.getDefaultPort()).isEqualTo(443);
        assertThat(webClientOptions.getDefaultHost()).isEqualTo("dataIndex.com");
        assertThat(webClientOptions.isSsl()).isTrue();
    }

}
