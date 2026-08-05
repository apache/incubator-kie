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
package org.kie.dmn.feel.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XQueryImplUtilTest {

    @ParameterizedTest
    @MethodSource("executeMatchesFunctionTestData")
    void executeMatchesFunctionTest(String input, String pattern, String flags, boolean expected) {
        assertThat(XQueryImplUtil.executeMatchesFunction(input, pattern, flags)).isEqualTo(expected);
    }

    private static Object[][] executeMatchesFunctionTestData() {
        return new Object[][] {
                { "test", "^test", "i", true },
                { "fo\nbar", "o.b", null, false },
                { "TEST", "test", "i", true },
                // flags
                { "test", "^test", "", true },                          // flags = "" explicitly
                { "FO\nBAR", "fo.bar", "si", true },                   // dotall + case-insensitive
                { "hello\nworld", "^WORLD", "mi", true },              // multiline + case-insensitive
                // XML special characters in input — exercises the escape→embed→evaluate pipeline
                { "it's", "it.s", "", true },                          // single quote in input
                { "say \"hi\"", "say .hi.", "", true },                // double quote in input
                { "a&b", "a.b", "", true },                            // ampersand in input
                { "a<b", "a.b", "", true },                            // less-than in input
                { "a>b", "a.b", "", true },                            // greater-than in input
                // XML special characters in pattern
                { "<tag>", "<tag>", "", true },                        // angle brackets in pattern
        };
    }

    @ParameterizedTest
    @MethodSource("executeMatchesFunctionInvokingExceptionTestData")
    void executeMatchesFunctionInvokingExceptionTest(String input, String pattern, String flags,
                                                     String exceptionMessage) {
        assertThatThrownBy(() -> XQueryImplUtil.executeMatchesFunction(input, pattern, flags))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(exceptionMessage);
    }

    private static Object[][] executeMatchesFunctionInvokingExceptionTestData() {
        return new Object[][] {
                { "test", "^test", "g", "Unrecognized flag" },
                { "test", "(?=\\s)", null, "No expression before quantifier" },
                { "test", "(.)\\2", "i", "invalid backreference \\2" },
        };
    }

    @ParameterizedTest
    @MethodSource("executeReplaceFunctionTestData")
    void executeReplaceFunctionTest(String input, String pattern, String replacement, String flags, String expected) {
        assertThat(XQueryImplUtil.executeReplaceFunction(input, pattern, replacement, flags)).isEqualTo(expected);
    }

    private static Object[][] executeReplaceFunctionTestData() {
        return new Object[][] {
                { "testString", "^test", "ttt", "", "tttString" },
                { "fo\nbar", "o.b", "ttt", "s", "ftttar" },
                // flags
                { "FO\nBAR", "fo.bar", "X", "si", "X" },              // dotall + case-insensitive
                // XML special characters in input — exercises the escape→embed→evaluate pipeline
                { "a&b", "a.b", "X", "", "X" },                       // ampersand in input
                { "it's", "it.s", "X", "", "X" },                     // single quote in input
                // XML special characters in replacement
                { "hello", "hello", "a&b", "", "a&b" },               // ampersand in replacement
                { "hello", "hello", "it's", "", "it's" },             // single quote in replacement
                // backreference in replacement
                { "hello", "(h)", "$1$1", "", "hhello" },             // $1 backreference
        };
    }

    @ParameterizedTest
    @MethodSource("executeReplaceFunctionInvokingExceptionTestData")
    void executeReplaceFunctionInvokingExceptionTest(String input, String pattern, String replacement, String flags,
                                                     Class<?> expectedException, String exceptionMessage) {
        assertThatThrownBy(() -> XQueryImplUtil.executeReplaceFunction(input, pattern, replacement, flags))
                .isInstanceOf(expectedException).hasMessageContaining(exceptionMessage);
    }

    private static Object[][] executeReplaceFunctionInvokingExceptionTestData() {
        return new Object[][] {
                { "fo\nbar", "o.b", "ttt", "g", IllegalArgumentException.class, "Unrecognized flag" },
                { "test", "(?=\\s)", "ttt", null, IllegalArgumentException.class, "No expression before quantifier" },
                { "test", "(.)\\2", "ttt", null, IllegalArgumentException.class, "invalid backreference \\2" },
        };
    }

    @ParameterizedTest
    @MethodSource("evaluateXQueryExpressionValidParametersTestData")
    void evaluateXQueryExpressionValidParametersTest(String expression, Class<?> returnTypeClass, Object expectedResult) {
        assertThat(XQueryImplUtil.evaluateXQueryExpression(expression, returnTypeClass)).isEqualTo(expectedResult);

    }

    private static Object[][] evaluateXQueryExpressionValidParametersTestData() {
        return new Object[][] {
                { "matches('test', '^test', 'i')", Boolean.class, true },
                { "matches('fo\\nbar', 'o.b', '')", Boolean.class, false },
                { "replace('testString', '^test', 'ttt', '')", String.class, "tttString" }
        };
    }

    @ParameterizedTest
    @MethodSource("evaluateXQueryExpressionInvalidParametersTestData")
    void evaluateXQueryExpressionInvalidParametersTest(String expression, Class<?> returnTypeClass) {
        assertThatThrownBy(() -> XQueryImplUtil.evaluateXQueryExpression(expression, returnTypeClass))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static Object[][] evaluateXQueryExpressionInvalidParametersTestData() {
        return new Object[][] {
                { "matches('test', '^test', 'i')", Integer.class },
                { "replace('testString', '^test', 'ttt', '')", Double.class },
        };
    }

    @ParameterizedTest
    @MethodSource("escapeXmlCharactersReferencesForXPathTestData")
    void escapeXmlCharactersReferencesForXPathTest(String expression, String expectedResult) {
        assertThat(XQueryImplUtil.escapeXmlCharactersReferencesForXPath(expression)).isEqualTo(expectedResult);
    }

    private static Object[][] escapeXmlCharactersReferencesForXPathTestData() {
        return new Object[][] {
                // null / empty
                { null, null },
                { "", "" },
                // no special chars — original reference must be returned unchanged
                { "lolASD", "lolASD" },
                // each of the five special characters in isolation (single-char string)
                { "&", "&amp;" },
                { "\"", "&quot;" },
                { "'", "&apos;" },
                { "<", "&lt;" },
                { ">", "&gt;" },
                // mixed: < and > (no &, no quotes)
                { "List<String>", "List&lt;String&gt;" },
                // mixed: " only
                { "\"Mr.Y\"", "&quot;Mr.Y&quot;" },
                // mixed: all five present — ' < & > ' (missing " in a multi-char mix)
                { "'<&>'", "&apos;&lt;&amp;&gt;&apos;" },
                // mixed: all five chars including " alongside others
                { "a&b\"c'<d>", "a&amp;b&quot;c&apos;&lt;d&gt;" },
                // special char first, last, and in the middle
                { "&start", "&amp;start" },
                { "end&", "end&amp;" },
                { "mid&dle", "mid&amp;dle" },
        };
    }

    /**
     * Verifies the Javadoc guarantee: when no special characters are present,
     * the original String reference is returned (no allocation).
     */
    @org.junit.jupiter.api.Test
    void escapeXmlCharactersReferencesForXPathReturnsSameReferenceWhenNoEscapingNeeded() {
        String input = "no special chars here 1234";
        assertThat(XQueryImplUtil.escapeXmlCharactersReferencesForXPath(input)).isSameAs(input);
    }

}