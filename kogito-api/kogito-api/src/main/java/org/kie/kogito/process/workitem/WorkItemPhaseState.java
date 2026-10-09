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
package org.kie.kogito.process.workitem;

import java.util.Optional;

public class WorkItemPhaseState {

    private String name;
    private Optional<WorkItemTerminationType> termination;

    protected WorkItemPhaseState(String name, WorkItemTerminationType termination) {
        this.name = name;
        this.termination = Optional.ofNullable(termination);
    }

    public static WorkItemPhaseState of(String name) {
        return new WorkItemPhaseState(name, null);
    }

    public static WorkItemPhaseState of(String name, WorkItemTerminationType termination) {
        return new WorkItemPhaseState(name, termination);
    }

    public String getName() {
        return name;
    }

    public Optional<WorkItemTerminationType> getTermination() {
        return termination;
    }

    public static WorkItemPhaseState initialized() {
        return new WorkItemPhaseState(null, null);
    }
}
