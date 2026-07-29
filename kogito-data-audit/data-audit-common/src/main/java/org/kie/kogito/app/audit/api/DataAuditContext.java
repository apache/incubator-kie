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
package org.kie.kogito.app.audit.api;

import java.util.Optional;

import org.kie.kogito.process.Processes;

public class DataAuditContext {

    private Object context;
    private Optional<Processes> processes;

    public DataAuditContext(Object context) {
        this(context, Optional.empty());
    }

    public DataAuditContext(Object context, Optional<Processes> processes) {
        this.context = context;
        this.processes = processes;
    }

    public static DataAuditContext newDataAuditContext(Object context) {
        return new DataAuditContext(context);
    }

    public static DataAuditContext newDataAuditContext(Object context, Processes processes) {
        return new DataAuditContext(context, Optional.ofNullable(processes));
    }

    @SuppressWarnings("unchecked")
    public <T> T getContext() {
        return (T) context;
    }

    public Optional<Processes> getProcesses() {
        return processes;
    }

}
