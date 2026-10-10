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
package org.kie.kogito.internal.process.workitem;

import java.util.Optional;

import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.process.workitem.WorkItemTransition;

/**
 * Definition of the life cycle phase that work item can be connected to.
 *
 */
public interface WorkItemLifeCyclePhase {

    /**
     * Returns unique id of this life cycle phase
     *
     * @return phase id
     */
    String id();

    /**
     * The work item status source from which this transition can start
     * 
     * @return
     */
    WorkItemPhaseState sourceStatus();

    /**
     * the target source in which this transition will end
     *
     * @return phase status
     */
    WorkItemPhaseState targetStatus();

    boolean isStartingPhase();

    /**
     * execute this life cycle phase
     *
     */
    Optional<WorkItemTransition> execute(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workitem, WorkItemTransition transition);
}
