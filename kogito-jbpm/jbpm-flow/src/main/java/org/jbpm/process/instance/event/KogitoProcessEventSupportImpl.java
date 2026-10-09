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
package org.jbpm.process.instance.event;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.kie.api.event.process.*;
import org.kie.api.runtime.KieRuntime;
import org.kie.internal.runtime.Closeable;
import org.kie.kogito.auth.IdentityProvider;
import org.kie.kogito.internal.process.event.KogitoProcessEventListener;
import org.kie.kogito.internal.process.event.KogitoProcessEventSupport;
import org.kie.kogito.internal.process.event.ProcessWorkItemTransitionEvent;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.process.workitem.KogitoWorkItem;
import org.kie.kogito.process.workitem.WorkItemTransition;

public class KogitoProcessEventSupportImpl implements KogitoProcessEventSupport {

    private final List<KogitoProcessEventListener> listeners = new CopyOnWriteArrayList<>();

    private final IdentityProvider identityProvider;

    /**
     * Do not use this constructor. It should be used just by deserialization.
     */
    public KogitoProcessEventSupportImpl(IdentityProvider identityProvider) {
        this.identityProvider = identityProvider;
    }

    private void notifyAllListeners(Consumer<KogitoProcessEventListener> consumer) {
        this.listeners.forEach(l -> consumer.accept(l));
    }

    @Override
    public final synchronized void addEventListener(KogitoProcessEventListener listener) {
        if (!this.listeners.contains(listener)) {
            this.listeners.add(listener);
        }
    }

    @Override
    public final void removeEventListener(KogitoProcessEventListener listener) {
        this.listeners.remove(listener);
    }

    public List<KogitoProcessEventListener> getEventListeners() {
        return Collections.unmodifiableList(this.listeners);
    }

    private void clear() {
        this.listeners.forEach(l -> {
            if (l instanceof Closeable) {
                ((Closeable) l).close();
            }
        });

        this.listeners.clear();
    }

    @Override
    public void fireBeforeProcessStarted(final KogitoProcessInstance instance, KieRuntime kruntime) {
        final ProcessStartedEvent event = new ProcessStartedEventImpl(instance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.beforeProcessStarted(event));
    }

    @Override
    public void fireAfterProcessStarted(final KogitoProcessInstance instance, KieRuntime kruntime) {
        final ProcessStartedEvent event = new ProcessStartedEventImpl(instance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.afterProcessStarted(event));
    }

    @Override
    public void fireBeforeProcessCompleted(final KogitoProcessInstance instance, KieRuntime kruntime) {
        final ProcessCompletedEvent event = new ProcessCompletedEventImpl(instance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.beforeProcessCompleted(event));
    }

    @Override
    public void fireAfterProcessCompleted(final KogitoProcessInstance instance, KieRuntime kruntime) {
        final ProcessCompletedEvent event = new ProcessCompletedEventImpl(instance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.afterProcessCompleted(event));
    }

    @Override
    public void fireOnProcessStateChanged(KogitoProcessInstance instance, KieRuntime kruntime) {
        final ProcessStateChangeEvent event = new ProcessStateChangeEventImpl(instance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.onProcessStateChanged(event));
    }

    @Override
    public void fireBeforeNodeTriggered(final KogitoNodeInstance nodeInstance, KieRuntime kruntime) {
        final ProcessNodeTriggeredEvent event = new KogitoProcessNodeTriggeredEventImpl(nodeInstance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.beforeNodeTriggered(event));
    }

    @Override
    public void fireAfterNodeTriggered(final KogitoNodeInstance nodeInstance, KieRuntime kruntime) {
        final ProcessNodeTriggeredEvent event = new KogitoProcessNodeTriggeredEventImpl(nodeInstance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.afterNodeTriggered(event));
    }

    @Override
    public void fireBeforeNodeLeft(final KogitoNodeInstance nodeInstance, KieRuntime kruntime) {
        final ProcessNodeLeftEvent event = new KogitoProcessNodeLeftEventImpl(nodeInstance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.beforeNodeLeft(event));
    }

    @Override
    public void fireAfterNodeLeft(final KogitoNodeInstance nodeInstance, KieRuntime kruntime) {
        final ProcessNodeLeftEvent event = new KogitoProcessNodeLeftEventImpl(nodeInstance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.afterNodeLeft(event));
    }

    @Override
    public void fireOnNodeStateChanged(KogitoNodeInstance nodeInstance, KieRuntime kruntime) {
        final ProcessNodeStateChangeEvent event = new KogitoProcessNodeStateChangeEventImpl(nodeInstance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.onNodeStateChanged(event));
    }

    @Override
    public void fireBeforeVariableChanged(final String id, final String instanceId,
            final Object oldValue, final Object newValue,
            final List<String> tags,
            final KogitoProcessInstance processInstance, KogitoNodeInstance nodeInstance, KieRuntime kruntime) {
        final ProcessVariableChangedEvent event = new KogitoProcessVariableChangedEventImpl(
                id, instanceId, oldValue, newValue, tags, processInstance, nodeInstance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.beforeVariableChanged(event));
    }

    @Override
    public void fireAfterVariableChanged(final String name, final String id,
            final Object oldValue, final Object newValue,
            final List<String> tags,
            final KogitoProcessInstance processInstance, KogitoNodeInstance nodeInstance, KieRuntime kruntime) {
        final ProcessVariableChangedEvent event = new KogitoProcessVariableChangedEventImpl(
                name, id, oldValue, newValue, tags, processInstance, nodeInstance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.afterVariableChanged(event));
    }

    @Override
    public void fireBeforeSLAViolated(final KogitoProcessInstance instance, KieRuntime kruntime) {
        final SLAViolatedEvent event = new SLAViolatedEventImpl(instance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.beforeSLAViolated(event));
    }

    @Override
    public void fireAfterSLAViolated(final KogitoProcessInstance instance, KieRuntime kruntime) {
        final SLAViolatedEvent event = new SLAViolatedEventImpl(instance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.afterSLAViolated(event));
    }

    @Override
    public void fireBeforeSLAViolated(final KogitoProcessInstance instance, KogitoNodeInstance nodeInstance, KieRuntime kruntime) {
        final SLAViolatedEvent event = new SLAViolatedEventImpl(instance, nodeInstance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.beforeSLAViolated(event));
    }

    @Override
    public void fireAfterSLAViolated(final KogitoProcessInstance instance, KogitoNodeInstance nodeInstance, KieRuntime kruntime) {
        final SLAViolatedEvent event = new SLAViolatedEventImpl(instance, nodeInstance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.afterSLAViolated(event));
    }

    @Override
    public void fireBeforeWorkItemTransition(final KogitoProcessInstance instance, KogitoWorkItem workitem, WorkItemTransition transition, KieRuntime kruntime) {
        final ProcessWorkItemTransitionEvent event = new KogitoProcessWorkItemTransitionEventImpl(instance, workitem, transition, kruntime, false, identityProvider.getName());
        notifyAllListeners(l -> l.beforeWorkItemTransition(event));
    }

    @Override
    public void fireAfterWorkItemTransition(final KogitoProcessInstance instance, KogitoWorkItem workitem, WorkItemTransition transition, KieRuntime kruntime) {
        final ProcessWorkItemTransitionEvent event = new KogitoProcessWorkItemTransitionEventImpl(instance, workitem, transition, kruntime, true, identityProvider.getName());
        notifyAllListeners(l -> l.afterWorkItemTransition(event));
    }

    @Override
    public void fireOnSignal(final KogitoProcessInstance instance, KogitoNodeInstance nodeInstance, KieRuntime kruntime, String signalName, Object signalObject) {
        final SignalEvent event = new SignalEventImpl(instance, kruntime, nodeInstance, signalName, signalObject, identityProvider.getName());
        notifyAllListeners(l -> l.onSignal(event));
    }

    @Override
    public void fireOnMessage(final KogitoProcessInstance instance, KogitoNodeInstance nodeInstance, KieRuntime kruntime, String messageName, Object messageObject) {
        final MessageEvent event = new MessageEventImpl(instance, kruntime, nodeInstance, messageName, messageObject, identityProvider.getName());
        notifyAllListeners(l -> l.onMessage(event));
    }

    @Override
    public void fireOnMigration(final KogitoProcessInstance processInstance, KieRuntime kruntime) {
        ProcessMigrationEventImpl event = new ProcessMigrationEventImpl(processInstance, kruntime, "System");
        notifyAllListeners(l -> l.onMigration(event));
    }

    @Override
    public void reset() {
        this.clear();
    }

    @Override
    public void fireOnError(KogitoProcessInstance instance, KogitoNodeInstance nodeInstance, KieRuntime kruntime, Exception exception) {
        ErrorEventImpl event = new ErrorEventImpl(instance, kruntime, nodeInstance, exception);
        notifyAllListeners(l -> l.onError(event));
    }

    @Override
    public void fireProcessRetriggered(KogitoProcessInstance instance, KieRuntime kruntime) {
        final ProcessRetriggeredEvent event = new ProcessRetriggeredEventImpl(instance, kruntime, identityProvider.getName());
        notifyAllListeners(l -> l.onProcessRetriggered(event));
    }

}
