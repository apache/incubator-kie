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
package org.kie.kogito.codegen.tests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kie.kogito.Application;
import org.kie.kogito.Model;
import org.kie.kogito.auth.SecurityPolicy;
import org.kie.kogito.codegen.AbstractCodegenIT;
import org.kie.kogito.codegen.data.Address;
import org.kie.kogito.codegen.data.Person;
import org.kie.kogito.codegen.data.PersonWithAddress;
import org.kie.kogito.internal.process.workitem.Policy;
import org.kie.kogito.jbpm.usertask.handler.UserTaskKogitoWorkItemHandler;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.Processes;
import org.kie.kogito.process.WorkItem;

import static org.assertj.core.api.Assertions.assertThat;

public class CallActivityTaskIT extends AbstractCodegenIT {

    private Policy securityPolicy = SecurityPolicy.of("john", Collections.emptyList());

    @Test
    public void testBasicCallActivityTask() throws Exception {

        Application app = generateCodeProcessesOnly("subprocess/CallActivity.bpmn2", "subprocess/CallActivitySubProcess.bpmn2");
        assertThat(app).isNotNull();

        Process<? extends Model> p = app.get(Processes.class).processById("ParentProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("x", "a");
        parameters.put("y", "b");
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        Model result = (Model) processInstance.variables();
        assertThat(result.toMap()).hasSize(2).containsKeys("x", "y")
                .containsEntry("y", "new value")
                .containsEntry("x", "a");
    }

    @Test
    public void testBasicCallActivityTaskWithTypeInfo() throws Exception {

        Application app = generateCodeProcessesOnly("subprocess/CallActivityWithTypeInfo.bpmn2", "subprocess/CallActivitySubProcess.bpmn2");
        assertThat(app).isNotNull();

        Process<? extends Model> p = app.get(Processes.class).processById("ParentProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("x", "a");
        parameters.put("y", "b");
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        Model result = (Model) processInstance.variables();
        assertThat(result.toMap()).hasSize(2).containsKeys("x", "y")
                .containsEntry("y", "new value")
                .containsEntry("x", "a");
    }

    @Test
    public void testCallActivityTaskMultiInstance() throws Exception {

        Application app = generateCodeProcessesOnly("subprocess/CallActivityMI.bpmn2", "subprocess/CallActivitySubProcess.bpmn2");
        assertThat(app).isNotNull();

        Process<? extends Model> p = app.get(Processes.class).processById("ParentProcess");

        List<String> list = new ArrayList<String>();
        list.add("first");
        list.add("second");
        List<String> listOut = new ArrayList<String>();

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("list", list);
        parameters.put("listOut", listOut);
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        Model result = (Model) processInstance.variables();
        assertThat(result.toMap()).hasSize(4).containsKeys("x", "y", "list", "listOut");
        assertThat((List<?>) result.toMap().get("listOut")).hasSize(2);

    }

    @Test
    public void testCallActivityTaskWithExpressionsForIO() throws Exception {

        Application app = generateCodeProcessesOnly("subprocess/CallActivityWithIOexpression.bpmn2", "subprocess/CallActivitySubProcess.bpmn2");
        assertThat(app).isNotNull();

        Process<? extends Model> p = app.get(Processes.class).processById("ParentProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("person", new Person("john", 0));
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        Model result = (Model) processInstance.variables();
        assertThat(result.toMap()).hasSize(1).containsKeys("person");

        Person person = (Person) result.toMap().get("person");
        assertThat(person.getName()).isEqualTo("new value");

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        WorkItem wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("MyTask");
        assertThat(wi.getPhaseStatus()).isEqualTo(UserTaskKogitoWorkItemHandler.ACTIVATED.getName());

        processInstance.completeWorkItem(wi.getId(), Collections.emptyMap());

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testCallActivityTaskWithExpressionsForIONested() throws Exception {

        Application app = generateCodeProcessesOnly("subprocess/CallActivityWithIOexpressionNested.bpmn2", "subprocess/CallActivitySubProcess.bpmn2");
        assertThat(app).isNotNull();

        Process<? extends Model> p = app.get(Processes.class).processById("ParentProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        PersonWithAddress pa = new PersonWithAddress("john", 0);
        pa.setAddress(new Address("test", null, null, null));
        parameters.put("person", pa);
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        Model result = (Model) processInstance.variables();
        assertThat(result.toMap()).hasSize(1).containsKeys("person");

        PersonWithAddress person = (PersonWithAddress) result.toMap().get("person");
        assertThat(person.getName()).isEqualTo("john");
        assertThat(person.getAddress().getStreet()).isEqualTo("test");
        assertThat(person.getAddress().getCity()).isEqualTo("new value");

        List<WorkItem> workItems = processInstance.workItems(securityPolicy);
        assertThat(workItems).hasSize(1);
        WorkItem wi = workItems.get(0);
        assertThat(wi.getName()).isEqualTo("MyTask");

        processInstance.completeWorkItem(wi.getId(), Collections.emptyMap());
        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testBasicCallActivityTaskWithSingleVarExpression() throws Exception {

        Application app = generateCodeProcessesOnly("subprocess/CallActivityVarIOExpression.bpmn2", "subprocess/CallActivitySubProcess.bpmn2");
        assertThat(app).isNotNull();

        Process<? extends Model> p = app.get(Processes.class).processById("ParentProcess");

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("x", "a");
        parameters.put("y", "b");
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        Model result = (Model) processInstance.variables();
        assertThat(result.toMap()).hasSize(2).containsKeys("x", "y")
                .containsEntry("y", "new value")
                .containsEntry("x", "a");
    }

    /**
     * Verifies that a subprocess whose process id contains hyphens
     */
    @Test
    public void testCallActivityWithHyphenatedSubProcessId() throws Exception {

        Application app = generateCodeProcessesOnly(
                "subprocess/CallActivityHyphenated.bpmn2",
                "subprocess/CallActivityHyphenatedSubProcess.bpmn2");
        assertThat(app).isNotNull();

        // The hyphenated subprocess must be reachable by its original id
        assertThat(app.get(Processes.class).processById("call-activity-sub-process")).isNotNull();

        Process<? extends Model> p = app.get(Processes.class).processById("ParentProcessHyphenated");
        assertThat(p).isNotNull();

        Model m = p.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("x", "inputValue");
        parameters.put("y", "");
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = p.createInstance(m);
        processInstance.start();

        assertThat(processInstance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        Model result = (Model) processInstance.variables();
        // The child sets subY = "hyphenated result"; it is mapped back into parent's y
        assertThat(result.toMap())
                .containsEntry("y", "hyphenated result")
                .containsEntry("x", "inputValue");
    }

}
