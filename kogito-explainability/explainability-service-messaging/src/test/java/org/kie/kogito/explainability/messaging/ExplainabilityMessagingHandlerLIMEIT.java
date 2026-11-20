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
package org.kie.kogito.explainability.messaging;

import java.util.Collections;
import java.util.function.Consumer;

import org.kie.kogito.explainability.api.BaseExplainabilityRequest;
import org.kie.kogito.explainability.api.BaseExplainabilityResult;
import org.kie.kogito.explainability.api.LIMEExplainabilityRequest;
import org.kie.kogito.explainability.api.LIMEExplainabilityResult;
import org.kie.kogito.testcontainers.quarkus.KafkaQuarkusTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(KafkaQuarkusTestResource.class)
class ExplainabilityMessagingHandlerLIMEIT extends BaseExplainabilityMessagingHandlerIT {

    @Override
    protected BaseExplainabilityRequest buildRequest() {
        return new LIMEExplainabilityRequest(EXECUTION_ID,
                SERVICE_URL,
                MODEL_IDENTIFIER,
                Collections.emptyList(),
                Collections.emptyList());
    }

    @Override
    protected BaseExplainabilityResult buildResult() {
        return LIMEExplainabilityResult.buildSucceeded(EXECUTION_ID, Collections.emptyList());
    }

    @Override
    protected void assertResult(BaseExplainabilityResult result) {
        assertThat((result instanceof LIMEExplainabilityResult)).isTrue();
    }

    @Override
    protected int getTotalExpectedEventCountWithIntermediateResults() {
        return 1;
    }

    @Override
    protected void mockExplainAsyncInvocationWithIntermediateResults(Consumer<BaseExplainabilityResult> callback) {
        //LIME does not support intermediate results; so nothing to do!
    }
}
