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

import java.util.List;

import org.kie.api.runtime.KieRuntime;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.WorkItemTransition;

public interface KogitoProcessEventSupport {

    void fireBeforeProcessStarted(KogitoProcessInstance instance, KieRuntime kruntime);

    void fireAfterProcessStarted(KogitoProcessInstance instance, KieRuntime kruntime);

    void fireProcessRetriggered(KogitoProcessInstance instance, KieRuntime kruntime);

    void fireBeforeProcessCompleted(KogitoProcessInstance instance, KieRuntime kruntime);

    void fireAfterProcessCompleted(KogitoProcessInstance instance, KieRuntime kruntime);

    void fireOnProcessStateChanged(KogitoProcessInstance instance, KieRuntime kruntime);

    void fireBeforeNodeTriggered(KogitoNodeInstance nodeInstance, KieRuntime kruntime);

    void fireAfterNodeTriggered(KogitoNodeInstance nodeInstance, KieRuntime kruntime);

    void fireBeforeNodeLeft(KogitoNodeInstance nodeInstance, KieRuntime kruntime);

    void fireAfterNodeLeft(KogitoNodeInstance nodeInstance, KieRuntime kruntime);

    void fireOnNodeStateChanged(KogitoNodeInstance nodeInstance, KieRuntime kruntime);

    void fireBeforeVariableChanged(String id, String instanceId, Object oldValue, Object newValue, List<String> tags,
            KogitoProcessInstance processInstance, KogitoNodeInstance nodeInstance, KieRuntime kruntime);

    void fireAfterVariableChanged(String name, String id, Object oldValue, Object newValue, List<String> tags,
            KogitoProcessInstance processInstance, KogitoNodeInstance nodeInstance, KieRuntime kruntime);

    void fireBeforeSLAViolated(KogitoProcessInstance instance, KieRuntime kruntime);

    void fireAfterSLAViolated(KogitoProcessInstance instance, KieRuntime kruntime);

    void fireBeforeSLAViolated(KogitoProcessInstance instance, KogitoNodeInstance nodeInstance, KieRuntime kruntime);

    void fireAfterSLAViolated(KogitoProcessInstance instance, KogitoNodeInstance nodeInstance, KieRuntime kruntime);

    void fireBeforeWorkItemTransition(KogitoProcessInstance instance, KogitoWorkItem workitem, WorkItemTransition transition, KieRuntime kruntime);

    void fireAfterWorkItemTransition(KogitoProcessInstance instance, KogitoWorkItem workitem, WorkItemTransition transition, KieRuntime kruntime);

    void fireOnSignal(KogitoProcessInstance instance, KogitoNodeInstance nodeInstance, KieRuntime kruntime, String signalName, Object signalObject);

    void fireOnMessage(KogitoProcessInstance instance, KogitoNodeInstance nodeInstance, KieRuntime kruntime, String messageName, Object messageObject);

    void fireOnError(KogitoProcessInstance instance, KogitoNodeInstance nodeInstance, KieRuntime kruntime, Exception exception);

    void fireOnMigration(KogitoProcessInstance processInstance, KieRuntime runtime);

    void reset();

    void addEventListener(KogitoProcessEventListener listener);

    void removeEventListener(KogitoProcessEventListener listener);
}
