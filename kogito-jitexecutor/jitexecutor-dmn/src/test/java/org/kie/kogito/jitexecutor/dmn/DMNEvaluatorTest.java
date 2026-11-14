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
package org.kie.kogito.jitexecutor.dmn;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.io.Resource;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.core.api.DMNFactory;
import org.kie.dmn.core.impl.DMNRuntimeImpl;
import org.kie.dmn.core.internal.utils.DMNRuntimeBuilder;
import org.kie.dmn.model.api.DMNModelInstrumentedBase;
import org.kie.internal.io.ResourceFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kie.kogito.jitexecutor.dmn.TestingUtils.getModelFromIoUtils;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DMNEvaluatorTest {

    private static String model;
    private static String invalidModel;

    @BeforeAll
    public static void setup() throws IOException {
        model = getModelFromIoUtils("invalid_models/DMNv1_x/test.dmn");
        invalidModel = getModelFromIoUtils("invalid_models/DMNv1_5/DMN-Invalid.dmn");
    }

    @Test
    void testFromXMLSuccessModel() {
        String modelXML = model;

        DMNEvaluator evaluator = DMNEvaluator.fromXML(modelXML);
        assertThat(evaluator).isNotNull();
    }

    @Test
    public void testFromXMLModelWithError() {
        String modelXML = invalidModel;

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> DMNEvaluator.fromXML(modelXML));
    }

    @Test
    void testValidateForErrorsThrowsException() {
        DMNModel dmnModel = mock(DMNModel.class);
        DMNRuntime dmnRuntime = mock(DMNRuntime.class);
        DMNMessage message = mock(DMNMessage.class);

        String errorMessage = "Error compiling FEEL expression 'Person Age >= 18' for name 'Can Drive?' on node 'Can Drive?': syntax error near 'Age'";
        when(message.getText()).thenReturn(errorMessage);
        when(dmnModel.hasErrors()).thenReturn(true);
        when(dmnModel.getMessages(DMNMessage.Severity.ERROR)).thenReturn(Collections.singletonList(message));

        assertThatThrownBy(() -> DMNEvaluator.validateForErrors(dmnModel, dmnRuntime))
                .as(errorMessage)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testValidateForErrors() {
        DMNModel dmnModel = mock(DMNModel.class);
        DMNRuntime dmnRuntime = mock(DMNRuntimeImpl.class);

        when(dmnModel.hasErrors()).thenReturn(false);
        DMNEvaluator evaluator = DMNEvaluator.validateForErrors(dmnModel, dmnRuntime);

        assertThat(evaluator).isNotNull();
    }

    @Test
    void testRetrieveInvalidElementPaths() throws IOException {
        Resource resource = ResourceFactory.newClassPathResource("invalid_models/DMNv1_5/DMN-MultipleInvalidElements.dmn");
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(resource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        String nameSpace = "https://kie.org/dmn/_79591DB5-1EE1-4CBD-AA5D-2E3EDF31150E";

        final DMNModel dmnModel = dmnRuntime.getModel(nameSpace, "DMN_8F7C4323-412A-4E0B-9AEF-0F24C8F55282");
        assertThat(dmnModel).isNotNull();
        DMNContext dmnContext = DMNFactory.newContext();
        dmnContext.set("id", "_7273EA2E-2CC3-4012-8F87-39E310C8DF3C");
        dmnContext.set("Conditional Input", 107);
        dmnContext.set("New Input Data", 8888);
        dmnContext.set("Score", 8);
        DMNResult dmnResult = dmnRuntime.evaluateAll(dmnModel, dmnContext);
        List<List<String>> invalidElementPaths = List.of(
                List.of("_3DC41DB9-BE1D-4289-A639-24AB57ED082D", "_2B147ECC-2457-4623-B841-3360D75F9F76", "_6F318F57-DA06-4F71-80AD-288E0BBB3A52", "_43236F2B-9857-454F-8EA0-39B37C7519CF"),
                List.of("_09186183-0646-4CD0-AD67-A159E9F87F5E", "_D386D137-582B-49F9-B6F9-F341C3AC4B3E", "_2E43C09D-011A-436C-B40B-9154405EAF3A"),
                List.of("_A40F3AA4-2832-4D98-83F0-7D604F9A090F", "_4AC1BD7D-5A8D-4A88-94F9-0B80BDF0D9B1"), List.of("_E9468D45-51EB-48DA-8B30-7D65696FDFB8"));

        List<List<String>> retrieved = DMNEvaluator.retrieveInvalidElementPaths(dmnResult.getMessages(), dmnModel);
        assertThat(retrieved).isNotNull();
        assertThat(invalidElementPaths.size()).isEqualTo(retrieved.size());
        assertThat(invalidElementPaths).isEqualTo(retrieved);
    }

    @Test
    void testGetPathToRoot() {
        Resource resource = ResourceFactory.newClassPathResource("invalid_models/DMNv1_5/InvalidElementPath.dmn");
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(resource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        String nameSpace = "https://kie.org/dmn/_608570C5-8344-42B6-9538-6E0EA9892C38";

        final DMNModel dmnModel = dmnRuntime.getModel(nameSpace, "DMN_039CBA90-29EC-4A15-B376-FC0FBC5F6807");
        assertThat(dmnModel).isNotNull();
        String id = "_8577FE15-1512-4BBE-885F-C30FD73ADC6B";
        List<String> invalidPath = List.of("_172F9901-0884-47C1-A5B4-3C09CC83D5B6", "_8577FE15-1512-4BBE-885F-C30FD73ADC6B");

        List<String> retrieved = DMNEvaluator.getPathToRoot(dmnModel, id);

        assertThat(retrieved).isNotNull();
        assertThat(invalidPath).isEqualTo(retrieved);
    }

    @Test
    void testGetNode() {
        DMNModelInstrumentedBase dmnModelInstrumentedBaseNode = mock(DMNModelInstrumentedBase.class);
        Resource resource = ResourceFactory.newClassPathResource("invalid_models/DMNv1_5/DMN-MultipleInvalidElements.dmn");
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(resource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        String nameSpace = "https://kie.org/dmn/_79591DB5-1EE1-4CBD-AA5D-2E3EDF31150E";

        final DMNModel dmnModel = dmnRuntime.getModel(nameSpace, "DMN_8F7C4323-412A-4E0B-9AEF-0F24C8F55282");
        assertThat(dmnModel).isNotNull();
        String id = "_43236F2B-9857-454F-8EA0-39B37C7519CF";
        when(dmnModelInstrumentedBaseNode.getIdentifierString()).thenReturn(id);

        DMNModelInstrumentedBase node = DMNEvaluator.getNodeById(dmnModel, id);

        assertThat(node).isNotNull();
        assertThat(dmnModelInstrumentedBaseNode.getIdentifierString()).isEqualTo(node.getIdentifierString());
    }

    @Test
    void testGetNodeById() {
        DMNModelInstrumentedBase dmnModelInstrumentedBaseNode = mock(DMNModelInstrumentedBase.class);
        Resource resource = ResourceFactory.newClassPathResource("invalid_models/DMNv1_5/InvalidElementPath.dmn");
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(resource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        String nameSpace = "https://kie.org/dmn/_608570C5-8344-42B6-9538-6E0EA9892C38";

        final DMNModel dmnModel = dmnRuntime.getModel(nameSpace, "DMN_039CBA90-29EC-4A15-B376-FC0FBC5F6807");
        assertThat(dmnModel).isNotNull();
        String id = "_8577FE15-1512-4BBE-885F-C30FD73ADC6B";
        when(dmnModelInstrumentedBaseNode.getIdentifierString()).thenReturn(id);

        DMNModelInstrumentedBase node = DMNEvaluator.getNodeById(dmnModelInstrumentedBaseNode, id);

        assertThat(node).isNotNull();
        assertThat(dmnModelInstrumentedBaseNode.getIdentifierString()).isEqualTo(node.getIdentifierString());
    }

    @ParameterizedTest
    @MethodSource("provideParametersForRemoveDuplicates")
    void testRemoveDuplicates(List<List<String>> input, List<List<String>> expected) {
        List<List<String>> retrieved = DMNEvaluator.removeDuplicates(input);
        assertThat(expected.size()).isEqualTo(retrieved.size());
        assertThat(expected).isEqualTo(retrieved);
    }

    private static Stream<Arguments> provideParametersForRemoveDuplicates() {
        return Stream.of(Arguments.of(Arrays.asList(List.of("A", "B", "D"), List.of("A", "B", "B", "D"), List.of("A", "B", "C", "D"), List.of("C", "B", "A"),
                List.of("A", "B", "C"), List.of("F", "G", "H", "I"), List.of("F", "H"), List.of("I", "H"), List.of("FG", "H", "I"), List.of("F", "GH")),
                Arrays.asList(List.of("A", "B", "B", "D"), List.of("A", "B", "C", "D"), List.of("F", "G", "H", "I"), List.of("A", "B", "D"), List.of("C", "B", "A"),
                        List.of("FG", "H", "I"), List.of("F", "H"), List.of("I", "H"), List.of("F", "GH"))),
                // subset
                Arguments.of(Arrays.asList(List.of("A", "B", "C", "D"), List.of("A", "B"), List.of("B", "C"), List.of("C", "D")),
                        List.of(List.of("A", "B", "C", "D"))),
                // all duplicates
                Arguments.of(Arrays.asList(List.of("A", "B", "C"), List.of("A", "B", "C"), List.of("A", "B", "C")),
                        List.of(List.of("A", "B", "C"))),
                // no duplicates
                Arguments.of(Arrays.asList(List.of("A", "B", "C"), List.of("X", "Y", "Z")),
                        Arrays.asList(List.of("A", "B", "C"), List.of("X", "Y", "Z"))),
                // one complete duplicate
                Arguments.of(Arrays.asList(List.of("A", "B", "C"), List.of("A", "B", "C"), List.of("X", "Y", "Z")),
                        Arrays.asList(List.of("A", "B", "C"), List.of("X", "Y", "Z"))));
    }

}
