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
package org.kie.kogito.trusty.service.common.handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.explainability.api.BaseExplainabilityResult;
import org.kie.kogito.persistence.api.Storage;
import org.kie.kogito.trusty.storage.api.model.decision.Decision;
import org.kie.kogito.trusty.storage.common.TrustyStorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public abstract class BaseExplainerServiceHandlerTest<H extends ExplainerServiceHandler<R>, R extends BaseExplainabilityResult> {

    protected static final String EXECUTION_ID = "executionId";

    protected H handler;

    protected TrustyStorageService storageService;

    protected Storage<String, R> storage;

    protected R result;

    protected Decision decision;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setup() {
        this.storage = mock(Storage.class);
        this.storageService = mock(TrustyStorageService.class);
        this.handler = getHandler();
        this.result = mock(getResult());
        this.decision = mock(Decision.class);

        setupMockStorage();
    }

    protected abstract H getHandler();

    protected abstract Class<R> getResult();

    protected abstract void setupMockStorage();

    @Test
    public void testExplainabilityResult() {
        assertThat(handler.supports(getResult())).isTrue();
        assertThat(handler.supports(BaseExplainabilityResult.class)).isFalse();
    }

    @Test
    public abstract void testGetExplainabilityResultById_WhenStored();

    @Test
    public abstract void testGetExplainabilityResultById_WhenNotStored();

    @Test
    public abstract void testStoreExplainabilityResult_WhenAlreadyStored();

    @Test
    public abstract void testStoreExplainabilityResultById_WhenNotAlreadyStored();
}
