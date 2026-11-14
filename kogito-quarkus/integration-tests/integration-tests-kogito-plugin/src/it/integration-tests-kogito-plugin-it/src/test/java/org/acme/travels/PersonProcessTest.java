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
package org.acme.travels;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.ClientErrorException;

import org.junit.jupiter.api.Test;
import org.kie.kogito.Model;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.WorkItem;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class PersonProcessTest {

    @Inject
    @Named("persons")
    Process<? extends Model> personProcess;

    @Test
    public void testAdult() {

        Model m = personProcess.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("person", new Person("John Doe", 20));
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = personProcess.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        Model result = (Model) processInstance.variables();
        assertThat(result.toMap()).hasSize(1);
        assertThat(((Person) result.toMap().get("person")).isAdult()).isTrue();
    }

    @Test
    public void testChild() {
        Model m = personProcess.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("person", new Person("Jenny Quark", 14));
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = personProcess.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        Model result = (Model) processInstance.variables();
        assertThat(result.toMap()).hasSize(1);
        assertThat(((Person) result.toMap().get("person")).isAdult()).isFalse();

        List<WorkItem> workItems = processInstance.workItems();
        assertThat(workItems).hasSize(1);

        processInstance.completeWorkItem(workItems.get(0).getId(), null);

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }
}
