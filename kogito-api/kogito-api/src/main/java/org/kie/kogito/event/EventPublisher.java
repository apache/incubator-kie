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
package org.kie.kogito.event;

import java.util.Collection;

/**
 * <b>Event Publisher</b> extension point.
 *
 * <p>
 * Publishes workflow engine lifecycle events (such as process instance state changes, user task events,
 * and definition updates) for consumption to the "outside world".
 */
public interface EventPublisher {

    String PROCESS_INSTANCES_TOPIC_NAME = "kogito-processinstances-events";
    String USER_TASK_INSTANCES_TOPIC_NAME = "kogito-usertaskinstances-events";
    String PROCESS_DEFINITIONS_TOPIC_NAME = "kogito-processdefinitions-events";

    /**
     * Publishes a single workflow data event.
     *
     * @param event the non-null {@link DataEvent} to publish
     */
    void publish(DataEvent<?> event);

    /**
     * Publishes a collection of workflow data events.
     *
     * @param events the non-null {@link Collection} of {@link DataEvent} instances to publish
     */
    void publish(Collection<DataEvent<?>> events);
}
