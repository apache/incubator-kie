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
package org.kie.kogito.app.audit.quarkus;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.kie.kogito.Model;
import org.kie.kogito.correlation.CompositeCorrelation;
import org.kie.kogito.correlation.CorrelationService;
import org.kie.kogito.internal.process.runtime.KogitoNode;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.Policy;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstances;
import org.kie.kogito.process.Processes;
import org.kie.kogito.process.Signal;
import org.kie.kogito.process.WorkItem;

import io.quarkus.test.junit.QuarkusTestProfile;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

public class DataAuditITTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("kogito.persistence.data-isolation.enabled", "true");
    }

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(MockProcessesBean.class);
    }

    @ApplicationScoped
    @Alternative
    public static class MockProcessesBean implements Processes {

        @Override
        public Collection<Process<? extends Model>> processes() {
            return List.of(
                    new StubProcess("itAllowedProcess", "1.0"),
                    new StubProcess("itJobProcess", "1.0"),
                    new StubProcess("itUserTaskProcess", "1.0"));
        }

        @Override
        public Process<? extends Model> processById(String processId) {
            return processes().stream()
                    .filter(p -> p.id().equals(processId))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public Collection<String> processIds() {
            return List.of("itAllowedProcess", "itJobProcess", "itUserTaskProcess");
        }
    }

    static class StubProcess implements Process<Model> {

        private final String id;
        private final String version;

        StubProcess(String id, String version) {
            this.id = id;
            this.version = version;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public String name() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String type() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Model createModel() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessInstance<Model> createInstance(Model workItem) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessInstance<Model> createInstance(String businessKey, Model workItem) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessInstance<Model> createInstance(String businessKey, CompositeCorrelation correlation, Model workItem) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessInstances<Model> instances() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<KogitoNode> findNodes(Predicate<KogitoNode> filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CorrelationService correlations() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S> void send(Signal<S> signal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void activate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deactivate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorkItemTransition newTransition(WorkItem workItem, String phase,
                Map<String, Object> data, Policy... policies) {
            throw new UnsupportedOperationException();
        }

        @Override
        public KogitoWorkItemHandler getKogitoWorkItemHandler(String name) {
            throw new UnsupportedOperationException();
        }
    }
}
