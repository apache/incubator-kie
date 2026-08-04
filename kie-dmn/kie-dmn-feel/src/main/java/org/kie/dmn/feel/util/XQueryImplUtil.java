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

import java.util.regex.Pattern;

public class XQueryImplUtil {

    private static final Pattern XML_CHARACTER_REFERENCES_PATTERN = Pattern.compile("['\"&<>]");

    /**
     * Single Saxon Processor instance. Processor is thread-safe and expensive to construct
     * (it initialises the Saxon Configuration and performs a license check). One instance
     * per JVM is the Saxon-recommended pattern.
     */
    private static final Processor PROCESSOR = new Processor(false);

    /**
     * Single XQueryCompiler instance. XQueryCompiler is thread-safe and reusable.
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
     * It replaces all the XML Character References (&, ", ', <, >) in a given input string with their "escaping" characters.
     * This is required to run XPath functions containing XML Character References.
     * @param input A string input representing one of the parameter of managed functions
     * @return A sanitized string
     */
    static String escapeXmlCharactersReferencesForXPath(String input) {
        if (input != null && XML_CHARACTER_REFERENCES_PATTERN.matcher(input).find()) {
            input = input.contains("&") ? input.replace("&", "&amp;") : input;
            input = input.contains("\"") ? input.replace("\"",  "&quot;") : input;
            input = input.contains("'") ? input.replace("'",  "&apos;") : input;
            input = input.contains("<") ? input.replace("<",  "&lt;") : input;
            input = input.contains(">") ? input.replace(">",  "&gt;") : input;
        }
        return input;
    }
}
