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

import java.util.Collections;
import java.util.List;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.RecognizerSharedState;
import org.antlr.runtime.Token;
import org.drools.drl.ast.descr.BaseDescr;
import org.drools.drl.ast.descr.ConstraintConnectiveDescr;
import org.drools.drl.parser.lang.DRL6Lexer;
import org.drools.drl.parser.lang.DRLExpressions;
import org.drools.drl.parser.lang.DRLLexer;
import org.drools.drl.parser.lang.ParserHelper;
import org.kie.internal.builder.conf.LanguageLevelOption;

/**
 * This is a helper class that provides helper methods to parse expressions
 * using both the DRLExpressions parser and the DRLExprTree parser.
 */
public class Drl6ExprParser implements DrlExprParser {

    private ParserHelper helper = null;

    private final LanguageLevelOption languageLevel;

    public Drl6ExprParser(LanguageLevelOption languageLevel) {
        this.languageLevel = languageLevel;
    }

    /** Parse an expression from text */
    public ConstraintConnectiveDescr parse( final String text ) {
        ConstraintConnectiveDescr constraint = null;
        try {
            DRLLexer lexer = DRLFactory.getDRLLexer(new ANTLRStringStream(text), languageLevel);
            CommonTokenStream input = new CommonTokenStream( lexer );
            RecognizerSharedState state = new RecognizerSharedState();
            helper = new ParserHelper( input, state, languageLevel );
            DRLExpressions parser = DRLFactory.getDRLExpressions(input, state, helper, languageLevel);
            parser.setBuildDescr( true );
            parser.setLeftMostExpr( null ); // setting initial value just in case
            BaseDescr expr = parser.conditionalOrExpression();
            if ( expr != null && !parser.hasErrors() ) {
                constraint = ConstraintConnectiveDescr.newAnd();
                constraint.addOrMerge( expr );
            }
        } catch ( RecognitionException e ) {
            helper.reportError( e );
        }
        return constraint;
    }
    
    /**
     * Returns whether to preserve the eval wrapper around the supplied contents.
     * The constraint parser enters at conditionalOrExpression, which is below
     * ternaryExpression in the grammar, so a top-level ternary's branches are
     * silently discarded. Question-mark tokens at any nesting depth may indicate
     * such a ternary. Strings and comments are ignored.
     * Lexer errors also preserve the wrapper, leaving validation to compilation.
     * This is a conservative check, not validation of ternary syntax.
     */
    public static boolean shouldPreserveEval(String expression) {
        DRL6Lexer lexer = new DRL6Lexer(new ANTLRStringStream(expression));
        for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
            // QUESTION_DIV also covers a ternary immediately followed by a comment: x?/*...*/y:z.
            if (token.getType() == DRL6Lexer.QUESTION || token.getType() == DRL6Lexer.QUESTION_DIV) {
                return true;
            }
        }
        return !lexer.getErrors().isEmpty();
    }

    public String getLeftMostExpr() {
        return helper != null ? helper.getLeftMostExpr() : null;
    }

    /**
     * @return true if there were parser errors.
     */
    public boolean hasErrors() {
        return helper != null && helper.hasErrors();
    }

    /**
     * @return a list of errors found while parsing. 
     */
    @SuppressWarnings("unchecked")
    public List<DroolsParserException> getErrors() {
        return helper != null ? helper.getErrors() : Collections.EMPTY_LIST;
    }

}
