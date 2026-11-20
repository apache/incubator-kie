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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.drools.util.IoUtils;
import org.kie.dmn.api.core.DMNDecisionResult;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNResult;
import org.kie.kogito.jitexecutor.dmn.responses.JITDMNDecisionResult;
import org.kie.kogito.jitexecutor.dmn.responses.JITDMNMessage;
import org.kie.kogito.jitexecutor.dmn.responses.JITDMNResult;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import static org.assertj.core.api.Assertions.assertThat;

public class TestingUtils {

    public static final ObjectMapper MAPPER;

    static {
        final var jitModule = new SimpleModule().addAbstractTypeMapping(DMNResult.class, JITDMNResult.class)
                .addAbstractTypeMapping(DMNDecisionResult.class, JITDMNDecisionResult.class)
                .addAbstractTypeMapping(DMNMessage.class, JITDMNMessage.class);

        MAPPER = new ObjectMapper()
                .registerModule(new Jdk8Module())
                .registerModule(jitModule);
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static String getModelFromIoUtils(String modelFileName) throws IOException {
        return new String(IoUtils.readBytesFromInputStream(Thread.currentThread().getContextClassLoader().getResourceAsStream(modelFileName)));
    }

    public static String getModel(String modelFileName) throws IOException {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(modelFileName);
        assertThat(resource).isNotNull();
        try (InputStream is = resource.openStream()) {
            return MAPPER.writeValueAsString(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
