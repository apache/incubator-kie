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
package org.kie.kogito.jbpm.usertask.handler;

import java.util.HashMap;
import java.util.Map;

import org.kie.kogito.process.Processes;
import org.kie.kogito.usertask.UserTaskEventListener;
import org.kie.kogito.usertask.events.UserTaskStateEvent;
import org.kie.kogito.usertask.impl.lifecycle.DefaultUserTaskLifeCycle;
import org.kie.kogito.usertask.lifecycle.UserTaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTaskKogitoWorkItemHandlerProcessListener implements UserTaskEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(UserTaskKogitoWorkItemHandlerProcessListener.class);

    private Processes processes;

    public UserTaskKogitoWorkItemHandlerProcessListener(Processes processes) {
        this.processes = processes;
    }

    @Override
    public void onUserTaskState(UserTaskStateEvent event) {
        UserTaskState userTaskState = event.getNewStatus();
        if (!userTaskState.isTerminate()) {
            return;
        }

        // we check first that the work item is not finished to convey the signal
        Boolean notifyListeners = (Boolean) event.getUserTaskInstance().getMetadata().get(DefaultUserTaskLifeCycle.PARAMETER_NOTIFY_LISTENERS);
        if (notifyListeners != null && !notifyListeners) {
            return;
        }

        LOG.debug("onUserTaskState {} on complete work item", event);
        String processInstanceId = event.getUserTaskInstance().getProcessInfo().getProcessInstanceId();

        processes.processByProcessInstanceId(processInstanceId).get().instances().findById(processInstanceId).ifPresent(pi -> {
            Map<String, Object> data = new HashMap<>(event.getUserTaskInstance().getOutputs());
            data.put("ActorId", event.getUserTaskInstance().getActualOwner());
            data.put(UserTaskKogitoWorkItemHandler.PARAMETER_NOTIFY_PROCESS, false);
            pi.completeWorkItem(event.getUserTaskInstance().getExternalReferenceId(), data);
        });

    }
}
