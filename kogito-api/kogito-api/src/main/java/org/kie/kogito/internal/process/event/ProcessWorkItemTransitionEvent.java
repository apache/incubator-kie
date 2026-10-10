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
package org.kie.kogito.internal.process.event;

import org.kie.api.event.process.ProcessEvent;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.WorkItemTransition;

/**
 * An event when a work item has transition between life cycle phases
 */
public interface ProcessWorkItemTransitionEvent extends ProcessEvent {

    /**
     * Returns work item being transitioned
     * 
     * @return work item
     */
    KogitoWorkItem getWorkItem();

    /**
     * Returns transition that is applied to the work item
     * 
     * @return transition
     */
    WorkItemTransition getTransition();

    /**
     * Indicated is the transition has already been done.
     * 
     * @return true if transition has already been done, otherwise false
     */
    boolean isTransitioned();
}