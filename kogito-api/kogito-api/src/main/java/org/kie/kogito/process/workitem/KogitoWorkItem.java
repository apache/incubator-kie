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

import java.util.Date;
import java.util.Map;

import org.kie.api.runtime.process.WorkItem;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;

public interface KogitoWorkItem extends WorkItem {

    static final String PARAMETER_UNIQUE_TASK_ID = "UNIQUE_TASK_ID";

    @Override
    @Deprecated
    long getId();

    String getExternalReferenceId();

    String getActualOwner();

    String getStringId();

    /**
     * The id of the process instance that requested the execution of this
     * work item
     *
     * @return the id of the related process instance
     */
    String getProcessInstanceStringId();

    /**
     * Returns optional life cycle phase id associated with this work item
     * 
     * @return optional life cycle phase id
     */
    String getPhaseId();

    /**
     * Returns optional life cycle phase status associated with this work item
     * 
     * @return optional life cycle phase status
     */
    String getPhaseStatus();

    /**
     * Returns timestamp indicating the start date of this work item
     * 
     * @return start date
     */
    Date getStartDate();

    /**
     * Returns timestamp indicating the completion date of this work item
     * 
     * @return completion date
     */
    Date getCompleteDate();

    /**
     * The node instance that is associated with this
     * work item
     *
     * @return the related node instance
     */
    KogitoNodeInstance getNodeInstance();

    /**
     * The process instance that requested the execution of this
     * work item
     *
     * @return the related process instance
     */
    KogitoProcessInstance getProcessInstance();

    void removeOutput(String name);

    void setOutput(String name, Object value);

    default void setOutputs(Map<String, Object> outputs) {
        outputs.forEach(this::setOutput);
    }
}
