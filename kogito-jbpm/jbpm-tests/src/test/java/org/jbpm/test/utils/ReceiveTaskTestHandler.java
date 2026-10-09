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
package org.jbpm.test.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitem.DefaultKogitoWorkItemHandler;

public class ReceiveTaskTestHandler extends DefaultKogitoWorkItemHandler {

    // TODO: use correlation instead of message id
    private Map<String, String> waiting = new HashMap<>();

    private KogitoWorkItemManager manager;

    public void messageReceived(String messageId, Object message) {
        String workItemId = waiting.get(messageId);
        if (workItemId == null) {
            return;
        }
        Map<String, Object> results = new HashMap<>();
        results.put("Message", message);
        manager.completeWorkItem(workItemId, results);
    }

    @Override
    public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
        this.manager = manager;
        String messageId = (String) workItem.getParameter("MessageId");
        waiting.put(messageId, workItem.getStringId());
        return Optional.empty();
    }

    @Override
    public Optional<WorkItemTransition> abortWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler, KogitoWorkItem workItem, WorkItemTransition transition) {
        String messageId = (String) workItem.getParameter("MessageId");
        waiting.remove(messageId);
        return Optional.empty();
    }

}
