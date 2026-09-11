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
package org.kie.kogito.process;

import java.util.Optional;
import java.util.stream.Stream;

public interface ProcessInstances<T> {

    default Optional<ProcessInstance<T>> findById(String id) {
        return findById(id, ProcessInstanceReadMode.MUTABLE);
    }

    Optional<ProcessInstance<T>> findById(String id, ProcessInstanceReadMode mode);

    default Optional<ProcessInstance<T>> findByBusinessKey(String id) {
        return findByBusinessKey(id, ProcessInstanceReadMode.READ_ONLY);
    }

    default void migrateProcessInstances(String targetProcessId, String targetProcessVersion, String... processIds) {
        throw new UnsupportedOperationException();
    }

    default long migrateAll(String targetProcessId, String targetProcessVersion) {
        throw new UnsupportedOperationException();
    }

    default Optional<ProcessInstance<T>> findByBusinessKey(String id, ProcessInstanceReadMode mode) {
        return stream(mode).filter(pi -> id.equals(pi.businessKey())).findAny();
    }

    Stream<ProcessInstance<T>> stream(ProcessInstanceReadMode mode);

    default Stream<ProcessInstance<T>> stream() {
        return stream(ProcessInstanceReadMode.READ_ONLY);
    }

    default Stream<ProcessInstance<T>> waitingForEventType(String eventType) {
        return waitingForEventType(eventType, ProcessInstanceReadMode.READ_ONLY);
    }

    Stream<ProcessInstance<T>> waitingForEventType(String eventType, ProcessInstanceReadMode mode);
}
