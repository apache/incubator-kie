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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.kie.kogito.internal.process.workitem.WorkItemTerminationType;

/**
 * Defines work item life cycle phase transition.
 * Including data and policies to be enforced during transition.
 */
public interface WorkItemTransition {

    /**
     * Returns id phase where work item should be transitioned
     * 
     * @return target life cycle phase
     */
    String id();

    /**
     * Optional data to be associated with the transition.
     * This usually means appending given data into the work item.
     * 
     * @return data if given otherwise null
     */
    Map<String, Object> data();

    /**
     * Optional list of policies to be enforced during transition
     * 
     * @return list of policies or an empty list, should never be null
     */
    List<Policy> policies();

    Optional<WorkItemTerminationType> termination();
}
