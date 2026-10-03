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
package org.jbpm.workflow.core.impl;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class InputExpressionAssignmentTest {

    // Helper: runs the assignment and returns whatever the producer received.
    // Uses toExpression() for `from` so that expression is always set (even when there are no #{} tokens).
    private Object eval(String fromExpr, String toLabel, Function<String, Object> sourceResolver) throws Exception {
        DataDefinition from = DataDefinition.toExpression(fromExpr);
        DataDefinition to = DataDefinition.toSimpleDefinition(toLabel);
        InputExpressionAssignment assignment = new InputExpressionAssignment(from, to);

        AtomicReference<Object> result = new AtomicReference<>();
        assignment.execute(sourceResolver, ignored -> null, (label, value) -> result.set(value));
        return result.get();
    }

    // -------------------------------------------------------------------------
    // Sole-token cases — value must be returned as-is (no toString coercion)
    // -------------------------------------------------------------------------

    @Test
    void soleToken_stringValue_returnsString() throws Exception {
        Object result = eval("#{message}", "target", name -> "hello");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void soleToken_mapValue_returnsMapNotToString() throws Exception {
        Map<String, Object> payload = Map.of("key", "val");
        Object result = eval("#{payload}", "target", name -> payload);
        // Must be the original Map, not its toString representation
        assertThat(result).isSameAs(payload);
    }

    @Test
    void soleToken_integerValue_returnsInteger() throws Exception {
        Object result = eval("#{count}", "target", name -> 42);
        assertThat(result).isEqualTo(42);
    }

    // -------------------------------------------------------------------------
    // Template cases — surrounding text forces string interpolation
    // -------------------------------------------------------------------------

    @Test
    void template_singleTokenEmbedded_interpolatesIntoString() throws Exception {
        // Expression: {"message-value": "#{message}"}  →  {"message-value": "hello"}
        Object result = eval("{\"message-value\": \"#{message}\"}", "target", name -> "hello");
        assertThat(result).isEqualTo("{\"message-value\": \"hello\"}");
    }

    @Test
    void template_multipleTokens_allInterpolated() throws Exception {
        // Expression: "#{first}-#{second}"  →  "foo-bar"
        Object result = eval("#{first}-#{second}", "target", name -> {
            if ("first".equals(name))
                return "foo";
            if ("second".equals(name))
                return "bar";
            return null;
        });
        assertThat(result).isEqualTo("foo-bar");
    }

    // -------------------------------------------------------------------------
    // Unresolvable token — variable not present in source context → controlled failure
    // -------------------------------------------------------------------------

    @Test
    void soleToken_unresolvable_throwsIllegalArgument() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> eval("#{missing}", "target", name -> null))
                .withMessageContaining("missing")
                .withMessageContaining("#{missing}");
    }

    @Test
    void template_unresolvableToken_throwsIllegalArgument() {
        // #{b} is unresolvable even when #{a} resolves — fail fast rather than silently embed garbage
        assertThatIllegalArgumentException()
                .isThrownBy(() -> eval("#{a}-#{b}", "target", name -> "a".equals(name) ? "X" : null))
                .withMessageContaining("b");
    }

    // -------------------------------------------------------------------------
    // No-token case — plain literal, nothing to resolve or replace
    // -------------------------------------------------------------------------

    @Test
    void noToken_plainLiteralReturnedAsIs() throws Exception {
        // Expression has no #{} tokens → values map stays empty → outcome = expression unchanged
        Object result = eval("{\"message-value\": \"value\"}", "target", name -> null);
        assertThat(result).isEqualTo("{\"message-value\": \"value\"}");
    }

}
