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

import org.junit.jupiter.api.Test;
import org.kie.kogito.explainability.api.LIMEExplainabilityResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LIMEExplainerServiceHandlerTest extends BaseExplainerServiceHandlerTest<LIMEExplainerServiceHandler, LIMEExplainabilityResult> {

    @Override
    protected LIMEExplainerServiceHandler getHandler() {
        return new LIMEExplainerServiceHandler(storageService);
    }

    @Override
    protected Class<LIMEExplainabilityResult> getResult() {
        return LIMEExplainabilityResult.class;
    }

    @Override
    protected void setupMockStorage() {
        when(storageService.getLIMEResultStorage()).thenReturn(storage);
    }

    @Test
    @Override
    public void testGetExplainabilityResultById_WhenStored() {
        when(storage.containsKey(anyString())).thenReturn(true);
        when(storage.get(eq(EXECUTION_ID))).thenReturn(result);

        assertThat(handler.getExplainabilityResultById(EXECUTION_ID)).isEqualTo(result);
    }

    @Test
    @Override
    public void testGetExplainabilityResultById_WhenNotStored() {
        when(storage.containsKey(anyString())).thenReturn(false);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> handler.getExplainabilityResultById(EXECUTION_ID));
    }

    @Test
    @Override
    public void testStoreExplainabilityResult_WhenAlreadyStored() {
        when(storage.containsKey(anyString())).thenReturn(true);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> handler.storeExplainabilityResult(EXECUTION_ID, result));
    }

    @Test
    @Override
    public void testStoreExplainabilityResultById_WhenNotAlreadyStored() {
        when(storage.containsKey(anyString())).thenReturn(false);

        handler.storeExplainabilityResult(EXECUTION_ID, result);

        verify(storage).put(eq(EXECUTION_ID), eq(result));
    }
}
