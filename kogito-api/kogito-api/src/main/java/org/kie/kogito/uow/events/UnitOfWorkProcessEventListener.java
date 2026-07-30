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
package org.kie.kogito.uow.events;

import org.kie.api.event.process.*;
import org.kie.kogito.internal.process.event.DefaultKogitoProcessEventListener;
import org.kie.kogito.internal.process.event.ProcessWorkItemTransitionEvent;
import org.kie.kogito.uow.UnitOfWorkManager;
import org.kie.kogito.uow.WorkUnit;

public class UnitOfWorkProcessEventListener extends DefaultKogitoProcessEventListener {

    UnitOfWorkManager unitOfWorkManager;

    public UnitOfWorkProcessEventListener(UnitOfWorkManager unitOfWorkManager) {
        this.unitOfWorkManager = unitOfWorkManager;
    }

    private void intercept(ProcessEvent event) {
        unitOfWorkManager.currentUnitOfWork().intercept(WorkUnit.create(event, e -> {
        }));
    }

    @Override
    public void beforeProcessStarted(ProcessStartedEvent event) {
    }

    @Override
    public void afterProcessStarted(ProcessStartedEvent event) {
        intercept(event);
    }

    @Override
    public void beforeProcessCompleted(ProcessCompletedEvent event) {

    }

    @Override
    public void afterProcessCompleted(ProcessCompletedEvent event) {
        intercept(event);
    }

    @Override
    public void onProcessStateChanged(ProcessStateChangeEvent event) {
        intercept(event);
    }

    @Override
    public void onNodeStateChanged(ProcessNodeStateChangeEvent event) {
        intercept(event);
    }

    @Override
    public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {

    }

    @Override
    public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
        intercept(event);
    }

    @Override
    public void beforeNodeLeft(ProcessNodeLeftEvent event) {

    }

    @Override
    public void afterNodeLeft(ProcessNodeLeftEvent event) {
        intercept(event);
    }

    @Override
    public void beforeVariableChanged(ProcessVariableChangedEvent event) {

    }

    @Override
    public void afterVariableChanged(ProcessVariableChangedEvent event) {
        intercept(event);
    }

    @Override
    public void beforeSLAViolated(SLAViolatedEvent event) {
        intercept(event);
    }

    @Override
    public void afterSLAViolated(SLAViolatedEvent event) {

    }

    @Override
    public void beforeWorkItemTransition(ProcessWorkItemTransitionEvent event) {

    }

    @Override
    public void afterWorkItemTransition(ProcessWorkItemTransitionEvent event) {
        intercept(event);
    }

    @Override
    public void onSignal(SignalEvent event) {
        intercept(event);
    }

    @Override
    public void onMessage(MessageEvent event) {
        intercept(event);
    }

    @Override
    public void onMigration(ProcessMigrationEvent event) {
        intercept(event);
    }

    @Override
    public void onError(ErrorEvent event) {
        intercept(event);
    }

    @Override
    public void onProcessRetriggered(ProcessRetriggeredEvent event) {
        intercept(event);
    }

}
