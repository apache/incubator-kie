/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at
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
package org.kie.kogito.process.workitems.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.kie.kogito.internal.process.workitem.WorkItemTerminationType;
import org.kie.kogito.process.workitem.Policy;
import org.kie.kogito.process.workitem.WorkItemTransition;

public class DefaultWorkItemTransitionImpl implements WorkItemTransition {

    private String id;
    private Map<String, Object> data;
    private List<Policy> policies;
    private WorkItemTerminationType termination;

    public DefaultWorkItemTransitionImpl(String id, WorkItemTerminationType termination, Map<String, Object> data, Policy... policies) {
        this.id = id;
        this.data = new HashMap<>();
        this.policies = List.of(policies);
        this.termination = termination;
        if (data != null) {
            this.data.putAll(data);
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Map<String, Object> data() {
        return data;
    }

    @Override
    public List<Policy> policies() {
        return policies;
    }

    public void setTermination(WorkItemTerminationType termination) {
        this.termination = termination;
    }

    @Override
    public Optional<WorkItemTerminationType> termination() {
        return Optional.ofNullable(this.termination);
    }

    @Override
    public String toString() {
        return "DefaultWorkItemTransitionImpl [id=" + id + ", data=" + data + ", policies=" + policies + ", termination=" + termination + "]";
    }

}
