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
package org.kie.kogito.process.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.NoSuchElementException;

import org.kie.kogito.process.WorkItemHandlerConfig;
import org.kie.kogito.process.workitem.KogitoWorkItemHandler;

public class MultiWorkItemHandlerConfig implements WorkItemHandlerConfig {

    private final Iterable<WorkItemHandlerConfig> workItemHandlerConfigs;

    public MultiWorkItemHandlerConfig(Iterable<WorkItemHandlerConfig> workItemHandlerConfigs) {
        this.workItemHandlerConfigs = workItemHandlerConfigs;
    }

    @Override
    public KogitoWorkItemHandler forName(String name) {
        RuntimeException trackException = null;
        for (WorkItemHandlerConfig workItemHandlerConfig : workItemHandlerConfigs) {
            try {
                return workItemHandlerConfig.forName(name);
            } catch (RuntimeException ex) {
                trackException = ex;
            }
        }
        throw trackException != null ? trackException : new NoSuchElementException("Cannot find work item for name " + name);
    }

    @Override
    public Collection<String> names() {
        Collection<String> names = new HashSet<>();
        workItemHandlerConfigs.forEach(w -> names.addAll(w.names()));
        return names;
    }
}
