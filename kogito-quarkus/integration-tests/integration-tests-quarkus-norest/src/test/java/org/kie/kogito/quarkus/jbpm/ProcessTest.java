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
package org.kie.kogito.quarkus.jbpm;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kie.kogito.Model;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class ProcessTest {

    @Inject
    @Named("tests")
    Process<? extends Model> process;

    @Test
    public void testProcess() {
        Model m = process.createModel();
        Map<String, Object> parameters = new HashMap<>();
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = process.createInstance(m);
        processInstance.start();
        assertThat(processInstance.status()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
    }
}
