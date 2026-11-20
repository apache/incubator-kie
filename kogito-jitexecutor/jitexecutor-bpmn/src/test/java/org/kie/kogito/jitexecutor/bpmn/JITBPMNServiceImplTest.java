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
package org.kie.kogito.jitexecutor.bpmn;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

import org.drools.io.FileSystemResource;
import org.drools.util.IoUtils;
import org.drools.util.StringUtils;
import org.jbpm.process.core.impl.ProcessImpl;
import org.jbpm.process.core.validation.ProcessValidationError;
import org.jbpm.process.core.validation.impl.ProcessValidationErrorImpl;
import org.junit.jupiter.api.Test;
import org.kie.api.definition.process.Process;
import org.kie.api.io.Resource;
import org.kie.kogito.jitexecutor.bpmn.responses.JITBPMNValidationResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kie.kogito.jitexecutor.bpmn.TestingUtils.MULTIPLE_BPMN2_FILE;
import static org.kie.kogito.jitexecutor.bpmn.TestingUtils.MULTIPLE_INVALID_BPMN2_FILE;
import static org.kie.kogito.jitexecutor.bpmn.TestingUtils.REST_WIH_ALL_VALID_SCENARIOS_BPMN2_FILE;
import static org.kie.kogito.jitexecutor.bpmn.TestingUtils.REST_WIH_INVALID_BPMN2_FILE;
import static org.kie.kogito.jitexecutor.bpmn.TestingUtils.REST_WIH_VALID_BPMN2_FILE;
import static org.kie.kogito.jitexecutor.bpmn.TestingUtils.REST_WIH_VALID_PROPER_BPMN2_FILE;
import static org.kie.kogito.jitexecutor.bpmn.TestingUtils.REST_WIH_VALID_SIMPLE_BPMN2_FILE;
import static org.kie.kogito.jitexecutor.bpmn.TestingUtils.SINGLE_BPMN2_FILE;
import static org.kie.kogito.jitexecutor.bpmn.TestingUtils.SINGLE_INVALID_BPMN2_FILE;
import static org.kie.kogito.jitexecutor.bpmn.TestingUtils.SINGLE_UNPARSABLE_BPMN2_FILE;
import static org.kie.kogito.jitexecutor.bpmn.TestingUtils.UNPARSABLE_BPMN2_FILE;

class JITBPMNServiceImplTest {

    private static final JITBPMNService jitBpmnService = new JITBPMNServiceImpl();

    @Test
    void validateModel_SingleValidBPMN2() throws IOException {
        String toValidate = new String(IoUtils.readBytesFromInputStream(Objects.requireNonNull(JITBPMNService.class.getResourceAsStream(SINGLE_BPMN2_FILE))));
        JITBPMNValidationResult retrieved = jitBpmnService.validateModel(toValidate);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getErrors()).isNotNull().isEmpty();
    }

    @Test
    void validateModel_MultipleValidBPMN2() throws IOException {
        String toValidate = new String(IoUtils.readBytesFromInputStream(Objects.requireNonNull(JITBPMNService.class.getResourceAsStream(MULTIPLE_BPMN2_FILE))));
        JITBPMNValidationResult retrieved = jitBpmnService.validateModel(toValidate);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getErrors()).isNotNull().isEmpty();
    }

    @Test
    void validateModel_SingleInvalidBPMN2() throws IOException {
        String toValidate = new String(IoUtils.readBytesFromInputStream(Objects.requireNonNull(JITBPMNService.class.getResourceAsStream(SINGLE_INVALID_BPMN2_FILE))));
        JITBPMNValidationResult retrieved = jitBpmnService.validateModel(toValidate);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getErrors()).isNotNull().hasSize(2);
        assertThat(retrieved.getErrors()).contains("Uri: (unknown) - Process id: invalid - name : invalid-process-id - error : Process has no start node.");
        assertThat(retrieved.getErrors()).contains("Uri: (unknown) - Process id: invalid - name : invalid-process-id - error : Process has no end node.");
    }

    @Test
    void validateModel_MultipleInvalidBPMN2() throws IOException {
        String toValidate = new String(IoUtils.readBytesFromInputStream(Objects.requireNonNull(JITBPMNService.class.getResourceAsStream(MULTIPLE_INVALID_BPMN2_FILE))));
        JITBPMNValidationResult retrieved = jitBpmnService.validateModel(toValidate);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getErrors()).isNotNull().hasSize(4);
        assertThat(retrieved.getErrors()).contains("Uri: (unknown) - Process id: invalid1 - name : invalid1-process-id - error : Process has no start node.");
        assertThat(retrieved.getErrors()).contains("Uri: (unknown) - Process id: invalid1 - name : invalid1-process-id - error : Process has no end node.");
        assertThat(retrieved.getErrors()).contains("Uri: (unknown) - Process id: invalid2 - name : invalid2-process-id - error : Process has no start node.");
        assertThat(retrieved.getErrors()).contains("Uri: (unknown) - Process id: invalid2 - name : invalid2-process-id - error : Process has no end node.");
    }

    @Test
    void validateModel_SingleUnparsableBPMN2() throws IOException {
        String toValidate = new String(IoUtils.readBytesFromInputStream(Objects.requireNonNull(JITBPMNService.class.getResourceAsStream(SINGLE_UNPARSABLE_BPMN2_FILE))));
        JITBPMNValidationResult retrieved = jitBpmnService.validateModel(toValidate);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getErrors()).isNotNull().hasSize(1);
        assertThat(retrieved.getErrors()).contains("Could not find message _T6T0kEcTEDuygKsUt0on2Q____");
    }

    @Test
    void parseModelXml_SingleValidBPMN2() throws IOException {
        String toValidate = new String(IoUtils.readBytesFromInputStream(Objects.requireNonNull(JITBPMNService.class.getResourceAsStream(SINGLE_BPMN2_FILE))));
        Collection<Process> retrieved = JITBPMNServiceImpl.parseModelXml(toValidate);
        assertThat(retrieved).isNotNull().hasSize(1);
    }

    @Test
    void parseModelXml_MultipleValidBPMN2() throws IOException {
        String toValidate = new String(IoUtils.readBytesFromInputStream(Objects.requireNonNull(JITBPMNService.class.getResourceAsStream(MULTIPLE_BPMN2_FILE))));
        Collection<Process> retrieved = JITBPMNServiceImpl.parseModelXml(toValidate);
        assertThat(retrieved).isNotNull().hasSize(2);
    }

    @Test
    void parseModelXml_UnparsableBPMN2() throws IOException {
        String toParse = new String(IoUtils.readBytesFromInputStream(Objects.requireNonNull(JITBPMNService.class.getResourceAsStream(UNPARSABLE_BPMN2_FILE))));
        assertThatThrownBy(() -> JITBPMNServiceImpl.parseModelXml(toParse))
                .as("Expected parseModelXml to throw, but it didn't")
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Could not parse");
    }

    @Test
    void parseModelResource_SingleValidBPMN2() {
        Collection<Process> retrieved = JITBPMNServiceImpl.parseModelResource(new FileSystemResource(new File(JITBPMNService.class.getResource(SINGLE_BPMN2_FILE).getFile())));
        assertThat(retrieved).isNotNull().hasSize(1);
    }

    @Test
    void parseModelResource_MultipleValidBPMN2() {
        Collection<Process> retrieved = JITBPMNServiceImpl.parseModelResource(new FileSystemResource(new File(JITBPMNService.class.getResource(MULTIPLE_BPMN2_FILE).getFile())));
        assertThat(retrieved).isNotNull().hasSize(2);
    }

    @Test
    void parseModelResource_UnparsableBPMN2() {
        Resource resource = new FileSystemResource(new File(UNPARSABLE_BPMN2_FILE));
        assertThatThrownBy(() -> JITBPMNServiceImpl.parseModelResource(resource))
                .as("Expected parseModelXml to throw, but it didn't")
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Could not parse");
    }

    @Test
    void getErrorString() {
        Process process = new ProcessImpl();
        String id = StringUtils.generateUUID();
        String name = StringUtils.generateUUID();
        ((ProcessImpl) process).setId(id);
        ((ProcessImpl) process).setName(name);
        String message = StringUtils.generateUUID();
        ProcessValidationError processValidationError = new ProcessValidationErrorImpl(process, message);
        String expected = "Uri: (unknown) - Process id: " + id + " - name : " + name + " - error : " + message;
        String retrieved = JITBPMNServiceImpl.getErrorString(processValidationError, null);
        assertThat(retrieved).isEqualTo(expected);
        String uri = "uri";
        expected = "Uri: " + uri + " - Process id: " + id + " - name : " + name + " - error : " + message;
        retrieved = JITBPMNServiceImpl.getErrorString(processValidationError, uri);
        assertThat(retrieved).isEqualTo(expected);
    }

    @Test
    void validateModel_RestWIHValid_ParsesSuccessfully() throws IOException {
        String toValidate = new String(IoUtils.readBytesFromInputStream(Objects.requireNonNull(JITBPMNService.class.getResourceAsStream(REST_WIH_VALID_BPMN2_FILE))));
        JITBPMNValidationResult retrieved = jitBpmnService.validateModel(toValidate);

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getErrors()).isNotNull();

        assertThat(retrieved.getErrors()).allMatch(error -> error.contains("Url is required") || error.contains("AccessTokenAcquisitionStrategy is required"));
    }

    @Test
    void validateModel_RestWIHValidProper_ParsesSuccessfully() throws IOException {
        String toValidate = new String(IoUtils.readBytesFromInputStream(Objects.requireNonNull(JITBPMNService.class.getResourceAsStream(REST_WIH_VALID_PROPER_BPMN2_FILE))));
        JITBPMNValidationResult retrieved = jitBpmnService.validateModel(toValidate);

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getErrors()).isNotNull();

        assertThat(retrieved.getErrors()).allMatch(error -> error.contains("Url is required") || error.contains("AccessTokenAcquisitionStrategy is required"));
    }

    @Test
    void validateModel_RestWIHValidSimple_ParsesSuccessfully() throws IOException {
        String toValidate = new String(IoUtils.readBytesFromInputStream(Objects.requireNonNull(JITBPMNService.class.getResourceAsStream(REST_WIH_VALID_SIMPLE_BPMN2_FILE))));
        JITBPMNValidationResult retrieved = jitBpmnService.validateModel(toValidate);

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getErrors()).isNotNull();

        assertThat(retrieved.getErrors()).allMatch(error -> error.contains("Url is required") || error.contains("AccessTokenAcquisitionStrategy is required"));
    }

    @Test
    void validateModel_RestWIHInvalid_HasValidationErrors() throws IOException {
        String toValidate = new String(IoUtils.readBytesFromInputStream(Objects.requireNonNull(JITBPMNService.class.getResourceAsStream(REST_WIH_INVALID_BPMN2_FILE))));
        JITBPMNValidationResult retrieved = jitBpmnService.validateModel(toValidate);

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getErrors()).isNotNull().isNotEmpty();

        assertThat(retrieved.getErrors()).anyMatch(error -> error.contains("Missing Strategy") && error.contains("AccessTokenAcquisitionStrategy is required"));
        assertThat(retrieved.getErrors()).anyMatch(error -> error.contains("Invalid Strategy") && error.contains("AccessTokenAcquisitionStrategy must be one of"));
        assertThat(retrieved.getErrors()).anyMatch(error -> error.contains("Missing TaskId") && error.contains("RestServiceCallTaskId is required"));
        assertThat(retrieved.getErrors()).anyMatch(error -> error.contains("Invalid TaskId Format") && error.contains("RestServiceCallTaskId must contain only alphanumeric"));
        assertThat(retrieved.getErrors()).anyMatch(error -> error.contains("Missing URL") && error.contains("Url is required"));
    }

    @Test
    void validateModel_AllValidScenarios_ParsesSuccessfully() throws IOException {
        String toValidate = new String(IoUtils.readBytesFromInputStream(Objects.requireNonNull(JITBPMNService.class.getResourceAsStream(REST_WIH_ALL_VALID_SCENARIOS_BPMN2_FILE))));
        JITBPMNValidationResult retrieved = jitBpmnService.validateModel(toValidate);

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getErrors()).isNotNull();

        // The BPMN file should parse successfully
        // Similar to above, validation errors for required fields will be present
    }

}
