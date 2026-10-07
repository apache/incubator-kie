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
package org.kie.api.event.process;

import java.util.EventListener;

/**
 * <b>Process Event Listener</b> extension point.
 *
 * <p>Listens for process execution lifecycle events, including process start and completion,
 * node entry and exit, variable mutations, SLA violations, signals, and error boundaries.
 */
public interface ProcessEventListener
    extends
    EventListener {

    /**
     * Invoked immediately before a process instance is started.
     *
     * @param event the non-null {@link ProcessStartedEvent}
     */
    void beforeProcessStarted(ProcessStartedEvent event);

    /**
     * Invoked immediately after a process instance is started.
     *
     * @param event the non-null {@link ProcessStartedEvent}
     */
    void afterProcessStarted(ProcessStartedEvent event);

    /**
     * Invoked immediately before a process instance is completed or aborted.
     *
     * @param event the non-null {@link ProcessCompletedEvent}
     */
    void beforeProcessCompleted(ProcessCompletedEvent event);

    /**
     * Invoked immediately after a process instance is completed or aborted.
     *
     * @param event the non-null {@link ProcessCompletedEvent}
     */
    void afterProcessCompleted(ProcessCompletedEvent event);

    /**
     * Invoked immediately before a node instance is triggered (node entry).
     *
     * @param event the non-null {@link ProcessNodeTriggeredEvent}
     */
    void beforeNodeTriggered(ProcessNodeTriggeredEvent event);

    /**
     * Invoked immediately after a node instance is triggered (node entry).
     *
     * @param event the non-null {@link ProcessNodeTriggeredEvent}
     */
    void afterNodeTriggered(ProcessNodeTriggeredEvent event);

    /**
     * Invoked immediately before a node instance is left (node exit).
     *
     * @param event the non-null {@link ProcessNodeLeftEvent}
     */
    void beforeNodeLeft(ProcessNodeLeftEvent event);

    /**
     * Invoked immediately after a node instance is left (node exit).
     *
     * @param event the non-null {@link ProcessNodeLeftEvent}
     */
    void afterNodeLeft(ProcessNodeLeftEvent event);

    /**
     * Invoked immediately before a process variable value is modified.
     *
     * @param event the non-null {@link ProcessVariableChangedEvent}
     */
    void beforeVariableChanged(ProcessVariableChangedEvent event);

    /**
     * Invoked immediately after a process variable value has been modified.
     *
     * @param event the non-null {@link ProcessVariableChangedEvent}
     */
    void afterVariableChanged(ProcessVariableChangedEvent event);
    
    /**
     * Invoked immediately before an SLA deadline on a process or node instance is violated.
     *
     * @param event the non-null {@link SLAViolatedEvent}
     */
    default void beforeSLAViolated(SLAViolatedEvent event) {}

    /**
     * Invoked immediately after an SLA deadline on a process or node instance is violated.
     *
     * @param event the non-null {@link SLAViolatedEvent}
     */
    default void afterSLAViolated(SLAViolatedEvent event) {}

    /**
     * Invoked when a signal is dispatched.
     *
     * @param event the non-null {@link SignalEvent}
     */
    default void onSignal(SignalEvent event) {}

    /**
     * Invoked when a process instance migration occurs across.
     *
     * @param event the non-null {@link ProcessMigrationEvent}
     */
    default void onMigration(ProcessMigrationEvent event) { }

    /**
     * Invoked when a message event is sent.
     *
     * @param event the non-null {@link MessageEvent}
     */
    default void onMessage(MessageEvent event) {}
    
    /**
     * Invoked when an error is captured during process execution.
     *
     * @param event the non-null {@link ErrorEvent}
     */
    default void onError (ErrorEvent event) {}

    /**
     * Invoked right after a process instance is modified.
     *
     * @param event the non-null {@link ProcessStateChangeEvent}
     */
    default void onProcessStateChanged(ProcessStateChangeEvent event) {}

    /**
     * Invoked right after a node in a process instance is modified.
     *
     * @param event the non-null {@link ProcessNodeStateChangeEvent}
     */
    default void onNodeStateChanged(ProcessNodeStateChangeEvent event) {}
}
