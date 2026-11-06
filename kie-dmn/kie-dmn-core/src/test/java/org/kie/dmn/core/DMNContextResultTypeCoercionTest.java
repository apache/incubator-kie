/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.dmn.core;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNMessageType;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.backend.marshalling.v1x.DMNMarshallerFactory;
import org.kie.dmn.core.api.DMNFactory;
import org.kie.dmn.core.compiler.DMNCompilerImpl;
import org.kie.dmn.core.compiler.RuntimeTypeCheckOption;
import org.kie.dmn.core.impl.DMNRuntimeImpl;
import org.kie.dmn.core.impl.VoidDMNRuntimeKB;
import org.kie.dmn.core.util.DMNRuntimeUtil;
import org.kie.dmn.model.api.Context;
import org.kie.dmn.model.api.DMNModelInstrumentedBase;
import org.kie.dmn.model.api.Definitions;

import static org.assertj.core.api.Assertions.assertThat;

class DMNContextResultTypeCoercionTest extends BaseInterpretedVsCompiledTest {

    private static final String MODEL = "valid_models/DMNv1_6/ContextResultTypeCoercion.dmn";
    private static final String NAMESPACE = "https://github.com/kiegroup/drools/kie-dmn";
    private static final String NAME = "ContextResultTypeCoercion";

    private static final List<String> DOUBLED = List.of("aa", "bb", "cc");

    private DMNResult evaluate(String decisionName, boolean typeCheck) {
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime(MODEL, this.getClass());
        ((DMNRuntimeImpl) runtime).setOption(new RuntimeTypeCheckOption(typeCheck));
        final DMNModel dmnModel = runtime.getModel(NAMESPACE, NAME);
        assertThat(dmnModel).isNotNull();
        assertThat(dmnModel.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnModel.getMessages())).isFalse();
        return runtime.evaluateByName(dmnModel, DMNFactory.newContext(), decisionName);
    }

    private Object resultOf(String decisionName, boolean typeCheck) {
        final DMNResult dmnResult = evaluate(decisionName, typeCheck);
        assertThat(dmnResult.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult.getMessages())).isFalse();
        return dmnResult.getDecisionResultByName(decisionName).getResult();
    }

    // a decision typed as a collection, with a for returning a context: the elements must not be wrapped in a list

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void forReturnContextEntry(boolean typeCheck) {
        init(false);
        assertThat(resultOf("For Return Context Entry", typeCheck)).isEqualTo(DOUBLED);
    }

    @ParameterizedTest
    @MethodSource("params")
    void forReturnContextGetValue(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        assertThat(resultOf("For Return Context Get Value", true)).isEqualTo(DOUBLED);
    }

    @ParameterizedTest
    @MethodSource("params")
    void forReturnContextStructEntry(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        assertThat(resultOf("For Return Context Struct Entry", true))
                .isEqualTo(List.of(Map.of("name", "a"), Map.of("name", "b"), Map.of("name", "c")));
    }

    @ParameterizedTest
    @MethodSource("params")
    void forContextInTypedEntry(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        assertThat(resultOf("For Context In Typed Entry", true)).isEqualTo(DOUBLED);
    }

    @ParameterizedTest
    @MethodSource("params")
    void bkmForReturnContext(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        assertThat(resultOf("Call For Context BKM", true)).isEqualTo(DOUBLED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void forReturnContextWrongType(boolean typeCheck) {
        init(false);
        // the elements are strings, while the decision is typed as a collection of numbers
        final DMNResult dmnResult = evaluate("For Return Context Wrong Type", typeCheck);
        if (typeCheck) {
            assertThat(dmnResult.getMessages(DMNMessage.Severity.ERROR))
                    .anyMatch(m -> m.getMessageType() == DMNMessageType.ERROR_EVAL_NODE);
            assertThat(dmnResult.getDecisionResultByName("For Return Context Wrong Type").getResult()).isNull();
        } else {
            assertThat(dmnResult.hasErrors()).isFalse();
            assertThat(dmnResult.getDecisionResultByName("For Return Context Wrong Type").getResult()).isEqualTo(List.of("a", "b"));
        }
    }

    // other nested contexts

    @ParameterizedTest
    @MethodSource("params")
    void listOfContexts(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        assertThat(resultOf("List Of Contexts", true)).isEqualTo(List.of("a", "b"));
    }

    @ParameterizedTest
    @MethodSource("params")
    void contextAsInvocationBinding(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        assertThat(resultOf("Context As Invocation Binding", true)).isEqualTo(List.of("a!"));
    }

    @ParameterizedTest
    @MethodSource("params")
    void contextInNestedFunctionBody(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        assertThat(resultOf("Context In Nested Function Body", true)).isEqualTo(List.of("a!"));
    }

    // a context nested two levels deep

    @ParameterizedTest
    @MethodSource("params")
    void forInForReturnContext(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        assertThat(resultOf("Nested For In For Context", true))
                .isEqualTo(List.of(List.of("a1", "a2"), List.of("b1", "b2")));
    }

    // the context produces the result of the node: the conversion to a singleton list is preserved

    @ParameterizedTest
    @MethodSource("params")
    void rootContext(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        assertThat(resultOf("Root Context", true)).isEqualTo(List.of("a"));
    }

    @ParameterizedTest
    @MethodSource("params")
    void rootConditionalThenContext(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        assertThat(resultOf("Root Conditional Of Contexts", true)).isEqualTo(List.of("a"));
    }

    @ParameterizedTest
    @MethodSource("params")
    void rootConditionalElseContext(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        assertThat(resultOf("Root Conditional Else Context", true)).isEqualTo(List.of("a"));
    }

    @ParameterizedTest
    @MethodSource("params")
    void typedEntryContext(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        // the context takes the type of the entry
        assertThat(resultOf("Typed Entry Context", true)).isEqualTo(List.of("b"));
    }

    @ParameterizedTest
    @MethodSource("params")
    void bkmRootContext(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        assertThat(resultOf("Call Root Context BKM", true)).isEqualTo(List.of("a"));
    }

    @ParameterizedTest
    @MethodSource("params")
    void bkmRootConditionalOfContexts(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        assertThat(resultOf("Call Root Conditional BKM", true)).isEqualTo(List.of("a"));
    }

    // the result type of a context must not depend on its parent link

    @ParameterizedTest
    @MethodSource("params")
    void contextsWithoutParentLink(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final Definitions definitions = DMNMarshallerFactory.newDefaultMarshaller()
                .unmarshal(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(MODEL), StandardCharsets.UTF_8));
        definitions.getDrgElement().forEach(DMNContextResultTypeCoercionTest::removeParentLinkOfContexts);
        final DMNModel dmnModel = new DMNCompilerImpl().compile(definitions, Collections.emptyList());
        assertThat(dmnModel.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnModel.getMessages())).isFalse();
        final DMNRuntimeImpl runtime = new DMNRuntimeImpl(new VoidDMNRuntimeKB());
        runtime.setOption(new RuntimeTypeCheckOption(true));

        final Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("For Return Context Entry", DOUBLED);
        expected.put("List Of Contexts", List.of("a", "b"));
        expected.put("Root Context", List.of("a"));
        expected.put("Root Conditional Of Contexts", List.of("a"));
        expected.put("Call Root Context BKM", List.of("a"));
        expected.put("Call Root Conditional BKM", List.of("a"));
        expected.put("Call For Context BKM", DOUBLED);
        expected.forEach((decisionName, expectedResult) -> {
            final DMNResult dmnResult = runtime.evaluateByName(dmnModel, DMNFactory.newContext(), decisionName);
            assertThat(dmnResult.hasErrors()).as(decisionName + " " + DMNRuntimeUtil.formatMessages(dmnResult.getMessages())).isFalse();
            assertThat(dmnResult.getDecisionResultByName(decisionName).getResult()).as(decisionName).isEqualTo(expectedResult);
        });
    }

    private static void removeParentLinkOfContexts(DMNModelInstrumentedBase element) {
        element.getChildren().forEach(child -> {
            removeParentLinkOfContexts(child);
            if (child instanceof Context) {
                child.setParent(null);
            }
        });
    }
}
