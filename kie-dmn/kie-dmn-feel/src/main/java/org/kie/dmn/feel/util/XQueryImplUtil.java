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

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.SaxonApiException;

public class XQueryImplUtil {

    /** Shared across all calls. Thread-safe; expensive to construct. One instance per JVM is sufficient.
     * See Saxon s9api {@link net.sf.saxon.s9api.Processor} Javadoc. */
    private static final Processor PROCESSOR = new Processor(false);

    /**
     * Shared across all calls. Concurrent use is permitted, but error messages may not be
     * attributed to the correct thread under concurrent error conditions.
     * See Saxon s9api {@link net.sf.saxon.s9api.XQueryCompiler} Javadoc.
     */
    private static final XQueryCompiler COMPILER = PROCESSOR.newXQueryCompiler();

    private XQueryImplUtil() {
        // Util class with static methods only.
    }

    public static Boolean executeMatchesFunction(String input, String pattern, String flags) {
        flags = flags == null ? "" : flags;
        String xQueryExpression = String.format("matches('%s', '%s', '%s')", escapeXmlCharactersReferencesForXPath(input), escapeXmlCharactersReferencesForXPath(pattern), flags);
        return evaluateXQueryExpression(xQueryExpression, Boolean.class);
    }

    public static String executeReplaceFunction(String input, String pattern, String replacement, String flags) {
        flags = flags == null ? "" : flags;
        String xQueryExpression = String.format("replace('%s', '%s', '%s', '%s')", escapeXmlCharactersReferencesForXPath(input), escapeXmlCharactersReferencesForXPath(pattern), escapeXmlCharactersReferencesForXPath(replacement), flags);
        return evaluateXQueryExpression(xQueryExpression, String.class);
    }

     static <T> T evaluateXQueryExpression(String expression, Class<T> expectedTypeResult) {
         try {
             XQueryExecutable executable = COMPILER.compile(expression);
             XQueryEvaluator queryEvaluator = executable.load();
             XdmItem resultItem = queryEvaluator.evaluateSingle();

             Object value = switch (expectedTypeResult.getSimpleName()) {
                 case "Boolean" -> ((XdmAtomicValue) resultItem).getBooleanValue();
                 case "String" -> resultItem.getStringValue();
                 default -> throw new UnsupportedOperationException("Type " + expectedTypeResult.getSimpleName() + " is not managed.");
             };

             return expectedTypeResult.cast(value);
         } catch (SaxonApiException e) {
             throw new IllegalArgumentException(e);
         }
     }

    /**
     * Escapes XML special characters ({@code & " ' < >}) so the value is safe to embed
     * as an XPath string literal. Returns {@code null} unchanged; returns the original
     * reference if no escaping is needed.
     */
    static String escapeXmlCharactersReferencesForXPath(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder sb = null;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            String replacement = switch (ch) {
                case '&'  -> "&amp;";
                case '"'  -> "&quot;";
                case '\'' -> "&apos;";
                case '<'  -> "&lt;";
                case '>'  -> "&gt;";
                default   -> null;
            };
            if (replacement != null) {
                if (sb == null) {
                    sb = new StringBuilder(input.length() + 16);
                    sb.append(input, 0, i);
                }
                sb.append(replacement);
            } else if (sb != null) {
                sb.append(ch);
            }
        }
        return sb != null ? sb.toString() : input;
    }
}
