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
package org.kie.efesto.compilationmanager.api.model;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.efesto.common.api.exceptions.KieEfestoCommonException;
import org.kie.efesto.common.api.identifiers.LocalUri;
import org.kie.efesto.common.api.identifiers.ModelLocalUriId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EfestoRedirectOutputTest {

    private static ModelLocalUriId modelLocalUriId;

    @BeforeAll
    public static void setup() {
        String path = "/example/some-id/instances/some-instance-id";
        LocalUri parsed = LocalUri.parse(path);
        modelLocalUriId = new ModelLocalUriId(parsed);
    }

    @Test
    void constructor() {
        String targetEngine = "targetEngine";
        EfestoRedirectOutput retrieved = new EfestoRedirectOutput(modelLocalUriId, targetEngine, null) {};
        assertThat(retrieved.getModelLocalUriId()).isEqualTo(modelLocalUriId);
        assertThat(retrieved.getFullClassNames()).isNull();
    }
    @Test
    void constructorMissingRequiredTarget() {
        assertThatThrownBy(() -> new EfestoRedirectOutput(modelLocalUriId, null, "content") {})
                .isInstanceOf(KieEfestoCommonException.class)
                .hasMessage("Missing required target");
    }

    @Test
    void constructorEmptyTarget() {
        assertThatThrownBy(() -> new EfestoRedirectOutput(modelLocalUriId, "", "content") {})
                .isInstanceOf(KieEfestoCommonException.class)
                .hasMessage("Missing required target");
    }

    @Test
    void getTargetEngine() {
        String targetEngine = "targetEngine";
        EfestoRedirectOutput retrieved = new EfestoRedirectOutput(modelLocalUriId, targetEngine, null) {};
        assertThat(retrieved.getTargetEngine()).isEqualTo(targetEngine);
    }

    @Test
    void getContent() {
        String targetEngine = "targetEngine";
        Object content = "content";
        EfestoRedirectOutput retrieved = new EfestoRedirectOutput(modelLocalUriId, targetEngine, content) {};
        assertThat(retrieved.getContent()).isEqualTo(content);
    }

}