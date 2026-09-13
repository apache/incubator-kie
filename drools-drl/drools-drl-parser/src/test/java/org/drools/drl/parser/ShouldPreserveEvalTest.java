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
package org.drools.drl.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShouldPreserveEvalTest {

    @Test
    void ternaryOperator() {
        assertThat(Drl6ExprParser.shouldPreserveEval("\"foo\" == \"foo\" ? \"foo\" == flag1 : \"foo\" == flag2")).isTrue();
    }

    @Test
    void simpleTernary() {
        assertThat(Drl6ExprParser.shouldPreserveEval("x > 0 ? y : z")).isTrue();
    }

    @Test
    void noTernary() {
        assertThat(Drl6ExprParser.shouldPreserveEval("age > 10")).isFalse();
    }

    @Test
    void equalityExpression() {
        assertThat(Drl6ExprParser.shouldPreserveEval("length == 4")).isFalse();
    }

    @Test
    void questionMarkInsideStringLiteral() {
        assertThat(Drl6ExprParser.shouldPreserveEval("name == \"what?\"")).isFalse();
    }

    @Test
    void escapedQuoteBeforeTernary() {
        assertThat(Drl6ExprParser.shouldPreserveEval("\"val\\\"ue\" == flag ? x : y")).isTrue();
    }

    @Test
    void questionMarkInsideStringWithEscapedQuote() {
        assertThat(Drl6ExprParser.shouldPreserveEval("name == \"is\\\"this?real\"")).isFalse();
    }

    @Test
    void singleQuotedStringWithQuestionMark() {
        assertThat(Drl6ExprParser.shouldPreserveEval("name == 'what?'")).isFalse();
    }

    @Test
    void questionMarkWithoutColon() {
        assertThat(Drl6ExprParser.shouldPreserveEval("x?")).isTrue();
    }

    @Test
    void colonInsideStringAfterQuestionMark() {
        assertThat(Drl6ExprParser.shouldPreserveEval("x ? \":\"")).isTrue();
    }

    @Test
    void emptyExpression() {
        assertThat(Drl6ExprParser.shouldPreserveEval("")).isFalse();
    }

    @Test
    void complexTernaryWithLogicalOperators() {
        assertThat(Drl6ExprParser.shouldPreserveEval("a > 0 && b < 10 ? c : d")).isTrue();
    }

    @Test
    void parenthesizedTernary() {
        assertThat(Drl6ExprParser.shouldPreserveEval("(x ? y : z)")).isTrue();
    }

    @Test
    void parenthesizedTernaryInEquality() {
        assertThat(Drl6ExprParser.shouldPreserveEval("(x ? y : z) == true")).isTrue();
    }

    @Test
    void nestedTernary() {
        assertThat(Drl6ExprParser.shouldPreserveEval("x ? y ? a : b : z")).isTrue();
    }

    @Test
    void parenthesizedComparisonTernary() {
        assertThat(Drl6ExprParser.shouldPreserveEval("((x > 0 ? y : z)) == true")).isTrue();
    }

    @Test
    void ternaryInMethodArgument() {
        assertThat(Drl6ExprParser.shouldPreserveEval("check(x > 0 ? y : z)")).isTrue();
    }

    @Test
    void singleQuotedTernary() {
        assertThat(Drl6ExprParser.shouldPreserveEval("'foo' == flag ? 'yes' : 'no'")).isTrue();
    }

    @Test
    void ternaryImmediatelyFollowedByComment() {
        assertThat(Drl6ExprParser.shouldPreserveEval("x?/* comment */y:z")).isTrue();
    }

    @Test
    void questionMarkInsideBlockComment() {
        assertThat(Drl6ExprParser.shouldPreserveEval("length /* ?: */ == 4")).isFalse();
    }

    @Test
    void questionMarkInsideLineComment() {
        assertThat(Drl6ExprParser.shouldPreserveEval("length == 4 // ?: comment")).isFalse();
    }

    @Test
    void drlNullSafeOperatorWithoutQuestionMark() {
        assertThat(Drl6ExprParser.shouldPreserveEval("address!.city == 'London'")).isFalse();
    }

    @Test
    void ternaryWithTrailingTokens() {
        assertThat(Drl6ExprParser.shouldPreserveEval("x ? y : z garbage")).isTrue();
    }

    @Test
    void unterminatedString() {
        assertThat(Drl6ExprParser.shouldPreserveEval("name == 'unterminated")).isTrue();
    }

    @Test
    void unterminatedComment() {
        assertThat(Drl6ExprParser.shouldPreserveEval("length == 4 /* unterminated")).isTrue();
    }
}
