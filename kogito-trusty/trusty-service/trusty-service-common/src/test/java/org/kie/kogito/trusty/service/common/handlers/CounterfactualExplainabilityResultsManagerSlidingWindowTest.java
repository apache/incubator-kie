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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.explainability.api.CounterfactualExplainabilityResult;
import org.kie.kogito.explainability.api.ExplainabilityStatus;
import org.kie.kogito.persistence.api.Storage;
import org.kie.kogito.persistence.api.query.Query;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CounterfactualExplainabilityResultsManagerSlidingWindowTest {

    private static final String EXECUTION_ID = "executionId";

    private static final String COUNTERFACTUAL_ID = "counterfactualId";

    private static final int WINDOW_LENGTH = 2;

    private Storage<String, CounterfactualExplainabilityResult> storage;

    private Query query;

    private CounterfactualExplainabilityResultsManagerSlidingWindow manager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setup() {
        this.storage = mock(Storage.class);
        this.query = mock(Query.class);
        this.manager = new CounterfactualExplainabilityResultsManagerSlidingWindow(WINDOW_LENGTH);

        when(storage.query()).thenReturn(query);
        when(query.sort(any())).thenReturn(query);
        when(query.filter(any())).thenReturn(query);
    }

    @Test
    public void testInstantiationWithInvalidWindowSizeOfZero() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new CounterfactualExplainabilityResultsManagerSlidingWindow(0));
    }

    @Test
    public void testInstantiationWithInvalidWindowSizeNegative() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new CounterfactualExplainabilityResultsManagerSlidingWindow(-1));
    }

    @Test
    public void testPurgeWhenResultSetSizeIsSmallerThanWindowSize() {
        when(query.execute()).thenReturn(Collections.emptyList());

        manager.purge(COUNTERFACTUAL_ID, storage);

        verify(storage, never()).remove(anyString());
    }

    @Test
    public void testPurgeWhenResultSetSizeIsGreaterThanWindowSize() {
        CounterfactualExplainabilityResult result0 = makeResult(0);
        CounterfactualExplainabilityResult result1 = makeResult(1);
        CounterfactualExplainabilityResult result2 = makeResult(2);
        CounterfactualExplainabilityResult result3 = makeResult(3);
        when(query.execute()).thenReturn(List.of(result0, result1, result2, result3));

        manager.purge(COUNTERFACTUAL_ID, storage);

        verify(storage, times(2)).remove(anyString());
        verify(storage).remove(result0.getSolutionId());
        verify(storage).remove(result1.getSolutionId());
    }

    private CounterfactualExplainabilityResult makeResult(long sequenceId) {
        return new CounterfactualExplainabilityResult(EXECUTION_ID,
                COUNTERFACTUAL_ID,
                UUID.randomUUID().toString(),
                sequenceId,
                ExplainabilityStatus.SUCCEEDED,
                null,
                true,
                CounterfactualExplainabilityResult.Stage.INTERMEDIATE,
                Collections.emptyList(),
                Collections.emptyList());
    }
}
