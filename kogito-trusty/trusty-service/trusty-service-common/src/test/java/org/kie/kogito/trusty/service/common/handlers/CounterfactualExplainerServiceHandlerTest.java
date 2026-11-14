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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.explainability.api.CounterfactualExplainabilityResult;
import org.kie.kogito.persistence.api.query.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CounterfactualExplainerServiceHandlerTest
        extends BaseExplainerServiceHandlerTest<CounterfactualExplainerServiceHandler, CounterfactualExplainabilityResult> {

    private static final String COUNTERFACTUAL_ID = "counterfactualId";

    private static final String SOLUTION_ID = "solutionId";

    private CounterfactualExplainabilityResultsManagerSlidingWindow explainabilityResultsManagerSlidingWindow;
    private CounterfactualExplainabilityResultsManagerDuplicates explainabilityResultsManagerDuplicates;

    @SuppressWarnings("rawtypes")
    private final Query query = mock(Query.class);

    @Override
    protected CounterfactualExplainerServiceHandler getHandler() {
        explainabilityResultsManagerSlidingWindow = mock(CounterfactualExplainabilityResultsManagerSlidingWindow.class);
        explainabilityResultsManagerDuplicates = mock(CounterfactualExplainabilityResultsManagerDuplicates.class);
        return new CounterfactualExplainerServiceHandler(storageService,
                explainabilityResultsManagerSlidingWindow,
                explainabilityResultsManagerDuplicates);
    }

    @Override
    protected Class<CounterfactualExplainabilityResult> getResult() {
        return CounterfactualExplainabilityResult.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void setupMockStorage() {
        when(storageService.getCounterfactualResultStorage()).thenReturn(storage);
        when(query.filter(any())).thenReturn(query);
        when(storage.query()).thenReturn(query);
    }

    @BeforeEach
    @Override
    public void setup() {
        super.setup();
        when(result.getStage()).thenReturn(CounterfactualExplainabilityResult.Stage.FINAL);
        when(result.getCounterfactualId()).thenReturn(COUNTERFACTUAL_ID);
        when(result.getSolutionId()).thenReturn(SOLUTION_ID);
    }

    @Test
    @Override
    public void testGetExplainabilityResultById_WhenStored() {
        when(query.execute()).thenReturn(List.of(result));

        assertThat(handler.getExplainabilityResultById(EXECUTION_ID)).isEqualTo(result);
    }

    @Test
    public void testGetExplainabilityResultById_WhenMultipleStored() {
        CounterfactualExplainabilityResult intermediate = mock(CounterfactualExplainabilityResult.class);
        when(intermediate.getExecutionId()).thenReturn(EXECUTION_ID);
        when(intermediate.getSolutionId()).thenReturn(SOLUTION_ID);
        when(intermediate.getStage()).thenReturn(CounterfactualExplainabilityResult.Stage.INTERMEDIATE);

        when(query.execute()).thenReturn(List.of(intermediate, result));

        assertThat(handler.getExplainabilityResultById(EXECUTION_ID)).isEqualTo(result);
    }

    @Test
    public void testGetExplainabilityResultById_WhenOnlyIntermediateStored() {
        CounterfactualExplainabilityResult intermediate = mock(CounterfactualExplainabilityResult.class);
        when(intermediate.getExecutionId()).thenReturn(EXECUTION_ID);
        when(intermediate.getSolutionId()).thenReturn(SOLUTION_ID);
        when(intermediate.getStage()).thenReturn(CounterfactualExplainabilityResult.Stage.INTERMEDIATE);

        when(query.execute()).thenReturn(List.of(intermediate));

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> handler.getExplainabilityResultById(EXECUTION_ID));
    }

    @Test
    @Override
    public void testGetExplainabilityResultById_WhenNotStored() {
        when(query.execute()).thenReturn(Collections.emptyList());

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

        verify(storage).put(eq(SOLUTION_ID), eq(result));
        verify(explainabilityResultsManagerSlidingWindow).purge(eq(COUNTERFACTUAL_ID), eq(storage));
    }
}
