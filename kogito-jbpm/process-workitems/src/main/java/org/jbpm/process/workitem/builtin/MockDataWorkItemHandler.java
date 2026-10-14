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
package org.jbpm.process.workitem.builtin;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.kie.kogito.process.workitem.DefaultKogitoWorkItemHandler;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.process.workitem.WorkItemTransition;

/**
 * Simple work item handler that allows to provide output data or supplier
 * that can provide data based on supplied function. It can reason on top
 * of provided input data.
 *
 */
public class MockDataWorkItemHandler extends DefaultKogitoWorkItemHandler {
    private Function<Map<String, Object>, Map<String, Object>> outputDataSupplier;

    /**
     * Create handler that will always complete work items with exact same map of data.
     *
     * @param outputData data to be used when completing work items
     */
    public MockDataWorkItemHandler(Map<String, Object> outputData) {
        this.outputDataSupplier = inputData -> outputData;
    }

    /**
     * Create handler with custom function that will supply output data. It can use
     * input data to change the output data returned if needed.
     *
     * @param outputDataSupplier function responsible to provide output data
     */
    public MockDataWorkItemHandler(Function<Map<String, Object>, Map<String, Object>> outputDataSupplier) {
        this.outputDataSupplier = outputDataSupplier;
    }

    @Override
    public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
        return Optional.of(this.workItemLifeCycle.newTransition("complete", workItem.getPhaseStatus(), outputDataSupplier.apply(workItem.getParameters())));
    }

}
