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
package org.kie.dmn.core.compiler;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.api.io.Resource;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.api.core.DMNType;
import org.kie.dmn.api.core.DMNVersion;
import org.kie.dmn.core.api.DMNExpressionEvaluator;
import org.kie.dmn.core.ast.DMNBaseNode;
import org.kie.dmn.core.ast.DMNConditionalEvaluator;
import org.kie.dmn.core.impl.DMNModelImpl;
import org.kie.dmn.core.internal.utils.DMNRuntimeBuilder;
import org.kie.dmn.feel.FEEL;
import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.lang.impl.FEELImpl;
import org.kie.dmn.model.api.Conditional;
import org.kie.dmn.model.api.DMNModelInstrumentedBase;
import org.kie.dmn.model.api.LiteralExpression;
import org.kie.dmn.model.v1_5.TLiteralExpression;
import org.kie.internal.io.ResourceFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kie.dmn.core.ast.DMNConditionalEvaluator.EvaluatorType.ELSE;
import static org.kie.dmn.core.ast.DMNConditionalEvaluator.EvaluatorType.IF;
import static org.kie.dmn.core.ast.DMNConditionalEvaluator.EvaluatorType.THEN;
import static org.kie.dmn.core.compiler.DMNEvaluatorCompiler.getEvaluatorIdentifier;
import static org.kie.dmn.core.compiler.DMNEvaluatorCompiler.getEvaluatorIdentifierMap;

class DMNEvaluatorCompilerTest {

    private static final FEELDialect DEFAULT_FEEL_DIALECT = FEELDialect.FEEL;
    private static final FEELDialect B_FEEL_DIALECT = FEELDialect.BFEEL;
    private static final String IF_ELEMENT_ID = "IF_ELEMENT_ID";
    private static final String THEN_ELEMENT_ID = "THEN_ELEMENT_ID";
    private static final String ELSE_ELEMENT_ID = "ELSE_ELEMENT_ID";
    private static DMNCompilerContext DEFAULT_DMN_COMPILER_CONTEXT;
    private static DMNCompilerContext B_FEEL_DMN_COMPILER_CONTEXT;
    private static DMNEvaluatorCompiler dmnEvaluatorCompiler;
    private static DMNFEELHelper DEFAULT_DMN_FEEL_HELPER;
    private static DMNFEELHelper B_FEEL_DMN_FEEL_HELPER;

    @BeforeAll
    static void setUp() {
        DEFAULT_DMN_FEEL_HELPER = new DMNFEELHelper(Collections.emptyList(), DEFAULT_FEEL_DIALECT, DMNVersion.getLatest());
        DEFAULT_DMN_COMPILER_CONTEXT = new DMNCompilerContext(DEFAULT_DMN_FEEL_HELPER);
        B_FEEL_DMN_FEEL_HELPER = new DMNFEELHelper(Collections.emptyList(), B_FEEL_DIALECT, DMNVersion.getLatest());
        B_FEEL_DMN_COMPILER_CONTEXT = new DMNCompilerContext(B_FEEL_DMN_FEEL_HELPER);
        DMNCompilerImpl compiler = new DMNCompilerImpl();
        dmnEvaluatorCompiler = new DMNEvaluatorCompiler(compiler);
    }

    @Test
    void getFEELDialectAdaptedFEELNoExpressionLanguage() {
        String expressionLanguage = null;
        LiteralExpression expression = getLiteralExpression(expressionLanguage);
        FEEL retrieved = DMNEvaluatorCompiler.getFEELDialectAdaptedFEEL(DEFAULT_DMN_COMPILER_CONTEXT, expression, expressionLanguage);
        assertThat(retrieved).isNotNull().isInstanceOf(FEELImpl.class);
        assertThat(((FEELImpl) retrieved).getFeelDialect()).isEqualTo(DEFAULT_FEEL_DIALECT);
        //
        retrieved = DMNEvaluatorCompiler.getFEELDialectAdaptedFEEL(B_FEEL_DMN_COMPILER_CONTEXT, expression, expressionLanguage);
        assertThat(retrieved).isNotNull().isInstanceOf(FEELImpl.class);
        assertThat(((FEELImpl) retrieved).getFeelDialect()).isEqualTo(B_FEEL_DIALECT);
    }

    @Test
    void getFEELDialectAdaptedFEELNoExpressionLanguageDefault() {
        String expressionLanguage = null;
        LiteralExpression expression = getLiteralExpression(expressionLanguage);
        FEEL retrieved = DMNEvaluatorCompiler.getFEELDialectAdaptedFEEL(DEFAULT_DMN_COMPILER_CONTEXT, expression, expressionLanguage);
        assertThat(retrieved).isNotNull().isInstanceOf(FEELImpl.class);
        assertThat(((FEELImpl) retrieved).getFeelDialect()).isEqualTo(DEFAULT_FEEL_DIALECT);
    }

    @Test
    void getFEELDialectAdaptedFEELNoExpressionLanguageBFEEL() {
        String expressionLanguage = null;
        LiteralExpression expression = getLiteralExpression(expressionLanguage);
        FEEL retrieved = DMNEvaluatorCompiler.getFEELDialectAdaptedFEEL(B_FEEL_DMN_COMPILER_CONTEXT, expression, expressionLanguage);
        assertThat(retrieved).isNotNull().isInstanceOf(FEELImpl.class);
        assertThat(((FEELImpl) retrieved).getFeelDialect()).isEqualTo(B_FEEL_DIALECT);
    }


    @Test
    void getFEELDialectAdaptedFEELFEELURIExpressionLanguage() {
        LiteralExpression expression = getLiteralExpression(null);
        String expressionLanguage = expression.getURIFEEL();
        FEEL retrieved = DMNEvaluatorCompiler.getFEELDialectAdaptedFEEL(DEFAULT_DMN_COMPILER_CONTEXT, expression, expressionLanguage);
        assertThat(retrieved).isNotNull().isInstanceOf(FEELImpl.class);
        assertThat(((FEELImpl) retrieved).getFeelDialect()).isEqualTo(DEFAULT_FEEL_DIALECT);
    }

    @Test
    void getFEELDialectAdaptedFEELBFEELExpressionLanguage() {
        String expressionLanguage = FEELDialect.BFEEL.getNamespace();
        LiteralExpression expression = getLiteralExpression(expressionLanguage);
        FEEL retrieved = DMNEvaluatorCompiler.getFEELDialectAdaptedFEEL(DEFAULT_DMN_COMPILER_CONTEXT, expression, expressionLanguage);
        assertThat(retrieved).isNotNull().isInstanceOf(FEELImpl.class);
        assertThat(((FEELImpl) retrieved).getFeelDialect()).isEqualTo(FEELDialect.BFEEL);
    }

    @Test
    void getFEELDialectAdaptedFEELSFEELExpressionLanguage() {
        String expressionLanguage = "https://www.omg.org/spec/DMN/20230324/FEEL/";
        LiteralExpression expression = getLiteralExpression(expressionLanguage);
        FEEL retrieved = DMNEvaluatorCompiler.getFEELDialectAdaptedFEEL(B_FEEL_DMN_COMPILER_CONTEXT, expression, expressionLanguage);
        assertThat(retrieved).isNotNull().isInstanceOf(FEELImpl.class);
        assertThat(((FEELImpl) retrieved).getFeelDialect()).isEqualTo(FEELDialect.FEEL);
    }

    @Test
    void getFEELDialectAdaptedFEELWrongExpressionLanguage() {
        String expressionLanguage = "something-else";
        LiteralExpression expression = getLiteralExpression(expressionLanguage);
        String expectedMessage = String.format("Unsupported FEEL language '%s'; allowed values are `null`, %s, %s", expressionLanguage, expression.getURIFEEL(), FEELDialect.BFEEL.getNamespace());
        assertThatThrownBy(() -> DMNEvaluatorCompiler.getFEELDialectAdaptedFEEL(DEFAULT_DMN_COMPILER_CONTEXT, expression, expressionLanguage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    @Test
    void testGetEvaluatorIdentifierMap() {
        String ifExprName = "testExpression [if]" ;
        String thenExprName = "testExpression [then]";
        String elseExprName = "testExpression [else]";
        DMNConditionalEvaluator.EvaluatorIdentifier ifIdentifier = new DMNConditionalEvaluator.EvaluatorIdentifier("_96D34F2E-3CC0-45A6-9455-2F960361A9CC", DMNConditionalEvaluator.EvaluatorType.IF);
        DMNConditionalEvaluator.EvaluatorIdentifier thenIdentifier = new DMNConditionalEvaluator.EvaluatorIdentifier("_F9D2FA33-4604-4AAA-8FF1-5A4AC5055385", DMNConditionalEvaluator.EvaluatorType.THEN);
        DMNConditionalEvaluator.EvaluatorIdentifier elseIdentifier = new DMNConditionalEvaluator.EvaluatorIdentifier("_7C843AB8-961C-4A95-83B3-2D1593DF297C", DMNConditionalEvaluator.EvaluatorType.ELSE);

        Resource resource = ResourceFactory.newClassPathResource("valid_models/DMNv1_5/ConditionalEvent.dmn");
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(resource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        String nameSpace = "https://kie.org/dmn/_5B448C78-0DBF-4554-92A4-8C0247EB01FD";

        final DMNModel dmnModel = dmnRuntime.getModel(nameSpace, "DMN_00DF4B93-0243-4813-BA70-A1894AC723BE");
        assertThat(dmnModel).isNotNull();
        DMNModelInstrumentedBase retrieved = getNodeById(dmnModel, "_096DC616-A4D5-449C-A350-491E42F3C8FB");
        assertThat(retrieved).isNotNull();
        Conditional expr = (Conditional) retrieved;
        DMNBaseNode dmnBaseNode = getNodeByName(dmnModel, "B");
        DMNType numType = dmnBaseNode.getType();
        DMNCompilerContext compilerContext = new DMNCompilerContext(DEFAULT_DMN_FEEL_HELPER);;
        compilerContext.setVariable("num", numType);
        DMNExpressionEvaluator ifEvaluator = dmnEvaluatorCompiler.compileExpression(compilerContext, (DMNModelImpl) dmnModel, dmnBaseNode, ifExprName, expr.getIf().getExpression());
        DMNExpressionEvaluator thenEvaluator = dmnEvaluatorCompiler.compileExpression(compilerContext, (DMNModelImpl) dmnModel, dmnBaseNode, thenExprName, expr.getThen().getExpression());
        DMNExpressionEvaluator elseEvaluator = dmnEvaluatorCompiler.compileExpression(compilerContext, (DMNModelImpl) dmnModel, dmnBaseNode, elseExprName, expr.getElse().getExpression());

        Map<DMNConditionalEvaluator.EvaluatorIdentifier, DMNExpressionEvaluator> result = getEvaluatorIdentifierMap(expr, ifEvaluator, thenEvaluator, elseEvaluator);
        assertThat(result).hasSize(3);
        assertThat(result.get(ifIdentifier)).isEqualTo(ifEvaluator);
        assertThat(result.get(thenIdentifier)).isEqualTo(thenEvaluator);
        assertThat(result.get(elseIdentifier)).isEqualTo(elseEvaluator);
    }

    @Test
    void testGetEvaluatorIdentifier() {
        DMNConditionalEvaluator.EvaluatorIdentifier ifIdentifier = new DMNConditionalEvaluator.EvaluatorIdentifier(IF_ELEMENT_ID, IF);
        DMNConditionalEvaluator.EvaluatorIdentifier thenIdentifier = new DMNConditionalEvaluator.EvaluatorIdentifier(THEN_ELEMENT_ID, THEN);
        DMNConditionalEvaluator.EvaluatorIdentifier elseIdentifier = new DMNConditionalEvaluator.EvaluatorIdentifier(ELSE_ELEMENT_ID, ELSE);
        DMNConditionalEvaluator.EvaluatorIdentifier ifEvaluatorIdentifier = getEvaluatorIdentifier(IF_ELEMENT_ID, IF);
        DMNConditionalEvaluator.EvaluatorIdentifier thenEvaluatorIdentifier = getEvaluatorIdentifier(THEN_ELEMENT_ID, THEN);
        DMNConditionalEvaluator.EvaluatorIdentifier elseEvaluatorIdentifier = getEvaluatorIdentifier(ELSE_ELEMENT_ID, ELSE);

        assertThat(ifEvaluatorIdentifier).isEqualTo(ifIdentifier);
        assertThat(thenEvaluatorIdentifier).isEqualTo(thenIdentifier);
        assertThat(elseEvaluatorIdentifier).isEqualTo(elseIdentifier);
    }

    /**
     * DMN 1.6 models with typeRef on inputValues must load without UnsupportedOperationException from AbstractTUnaryTests.setTypeRef.
     */
    @Test
    void inputValuesWithTypeRefLoadsOnDMNv1_6() {
        Resource resource = ResourceFactory.newClassPathResource("valid_models/DMNv1_6/inputValuesTypeRef.dmn");
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(resource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        assertThat(dmnRuntime.getModels()).hasSize(1);
        assertThat(dmnRuntime.getModels().get(0).hasErrors()).isFalse();
    }

    @Test
    void testCompileConditional() {
        String exprName = "testExpression";
        Resource resource = ResourceFactory.newClassPathResource("valid_models/DMNv1_5/ConditionalEvent.dmn");
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(resource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        String nameSpace = "https://kie.org/dmn/_5B448C78-0DBF-4554-92A4-8C0247EB01FD";

        final DMNModel dmnModel = dmnRuntime.getModel(nameSpace, "DMN_00DF4B93-0243-4813-BA70-A1894AC723BE");
        assertThat(dmnModel).isNotNull();
        DMNModelInstrumentedBase retrieved = getNodeById(dmnModel, "_096DC616-A4D5-449C-A350-491E42F3C8FB");
        assertThat(retrieved).isNotNull();
        DMNBaseNode dmnBaseNode = getNodeByName(dmnModel, "B");
        DMNType numType = dmnBaseNode.getType();
        DMNCompilerContext compilerContext = new DMNCompilerContext(DEFAULT_DMN_FEEL_HELPER);
        compilerContext.setVariable("num", numType);
        DMNExpressionEvaluator result = dmnEvaluatorCompiler.compileConditional(compilerContext, (DMNModelImpl) dmnModel, dmnBaseNode, exprName, (Conditional) retrieved);
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(DMNConditionalEvaluator.class);
    }

    private LiteralExpression getLiteralExpression(String expressionLanguage) {
        LiteralExpression toReturn = new TLiteralExpression();
        toReturn.setExpressionLanguage(expressionLanguage);
        return toReturn;
    }

    private DMNModelInstrumentedBase getNodeById(DMNModel dmnModel, String id) {
        return dmnModel.getDefinitions().getChildren().stream().map(child -> getNodeById(child, id))
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private DMNModelInstrumentedBase getNodeById(DMNModelInstrumentedBase dmnModelInstrumentedBase, String id) {
        if (dmnModelInstrumentedBase.getIdentifierString().equals(id)) {
            return dmnModelInstrumentedBase;
        }
        return dmnModelInstrumentedBase.getChildren().stream().map(child -> getNodeById(child, id))
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private DMNBaseNode getNodeByName(DMNModel dmnModel, String nodeName) {
        return (DMNBaseNode) dmnModel.getDecisions().stream()
                .filter(node -> node.getName().equals(nodeName))
                .findFirst().orElse(null);
    }
}