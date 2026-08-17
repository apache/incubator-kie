/**
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
 parser grammar DRL10Parser;

options { tokenVocab=DRL10Lexer; }

import DRL10Expressions, JavaParser;

    /*
     * statement := importStatement
     *           |  globalStatement
     *           |  declare
     *           |  rule
     *           |  ruleAttribute
     *           |  function
     *           |  query
     *           ;
     */
compilationUnit : packagedef? unitdef? drlStatementdef* EOF ;

drlStatementdef
    : importdef SEMI?
    | globaldef SEMI?
    | declaredef SEMI?
    | ruledef SEMI?
    | attributes SEMI?
    | functiondef SEMI?
    | querydef SEMI?
    ;

packagedef : PACKAGE name=drlQualifiedName SEMI? ;

unitdef : DRL_UNIT name=drlQualifiedName SEMI? ;

importdef : IMPORT (DRL_FUNCTION|STATIC)? drlQualifiedName (DOT MUL)?          #importStandardDef
          | IMPORT (DRL_ACCUMULATE|DRL_ACC) drlQualifiedName drlIdentifier     #importAccumulateDef
          ;

globaldef : DRL_GLOBAL type drlIdentifier ;

/**
 * declare := DECLARE
 *               | (ENTRY-POINT) => entryPointDeclaration
 *               | (WINDOW) => windowDeclaration
 *               | (TRAIT) => typeDeclaration (trait)
 *               | (ENUM) => enumDeclaration
 *               | typeDeclaration (class)
 *            END
 */

declaredef : DRL_DECLARE (
                         | entryPointDeclaration
                         | windowDeclaration
                         | typeDeclaration
                         | enumDeclaration
                         )
                         ; // DRL_END belongs to entryPointDeclaration etc.

/*
 * typeDeclaration := [TYPE] qualifiedIdentifier (EXTENDS qualifiedIdentifier)?
 *                         annotation*
 *                         field*
 *                     END
 */

typeDeclaration : DRL_TRAIT? DRL_TYPE? name=drlQualifiedName (EXTENDS superTypes+=drlQualifiedName (COMMA superTypes+=drlQualifiedName)* )? drlAnnotation* field* DRL_END ;

// entryPointDeclaration := ENTRY-POINT stringId annotation* END

entryPointDeclaration : DRL_ENTRY_POINT name=stringId drlAnnotation* DRL_END ;

// windowDeclaration := WINDOW ID annotation* lhsPatternBind END

windowDeclaration : DRL_WINDOW name=drlIdentifier drlAnnotation* lhsPatternBind DRL_END ;

// (enum)typeDeclaration := [ENUM] qualifiedIdentifier annotation* enumerative+ field* END

enumDeclaration : ENUM name=drlQualifiedName drlAnnotation* enumeratives SEMI field* DRL_END ;

enumeratives : enumerative (COMMA enumerative)* ;

// enumerative := ID ( LEFT_PAREN expression (COMMA expression)* RIGHT_PAREN )?

enumerative: drlIdentifier ( LPAREN expression ( COMMA expression )* RPAREN )? ;

// field := label fieldType (EQUALS_ASSIGN conditionalExpression)? annotation* SEMICOLON?

field : label type (ASSIGN initExpr=conditionalOrExpression)? drlAnnotation* SEMI? ;

// rule := RULE stringId (EXTENDS stringId)? annotation* attributes? lhs? rhs END

ruledef : DRL_RULE name=stringId (EXTENDS parentName=stringId)? drlAnnotation* attributes? lhs? rhs DRL_RHS_END ;

// query := QUERY stringId parameters? annotation* lhsExpression END

querydef : DRL_QUERY name=stringId parameters? drlAnnotation* queryLhs DRL_END ;

// parameters := LEFT_PAREN ( parameter ( COMMA parameter )* )? RIGHT_PAREN
parameters : LPAREN ( parameter ( COMMA parameter )* )? RPAREN ;

// parameter := ({requiresType}?=>type)? ID (LEFT_SQUARE RIGHT_SQUARE)*
parameter : type? drlIdentifier ; // type is optional. Removed (LEFT_SQUARE RIGHT_SQUARE)* as it doesn't make sense in the grammar

lhs : DRL_WHEN lhsExpression* ;

queryLhs : lhsExpression* ;

lhsExpression : LPAREN lhsExpression RPAREN                             #lhsExpressionEnclosed
              | DRL_OR lhsExpression+                                   #lhsOr
              | lhsExpression ((DRL_OR) lhsExpression)+              #lhsOr
              | DRL_AND lhsExpression+                                  #lhsAnd
              | lhsExpression ((DRL_AND) lhsExpression)+            #lhsAnd
              | lhsUnary                                                               #lhsUnarySingle
              ;

// lhsAnd is used as a label in lhsExpression rule. But some other rules explicitly use the def, so lhsAndDef is declared.
lhsAndDef : LPAREN lhsAndDef RPAREN
          | lhsUnary ((DRL_AND) lhsUnary)*
          | LPAREN DRL_AND lhsUnary+ RPAREN
          ;

/*
lhsUnary : ( lhsExists namedConsequence?
           | lhsNot namedConsequence?
           | lhsEval consequenceInvocation*
           | lhsForall
           | lhsAccumulate
           | LPAREN lhsOr RPAREN namedConsequence?
           | lhsPatternBind consequenceInvocation*
           ) SEMI? ;
*/

lhsUnary : (
           lhsExists namedConsequenceInvocation?
           | lhsNot namedConsequenceInvocation?
           | lhsEval consequenceInvocation*
           | lhsForall
           | lhsAccumulate
           | lhsGroupBy
           | LPAREN lhsExpression RPAREN namedConsequenceInvocation?
           | conditionalBranch // not in the above old parser definition, but actually implemented in the old parser
           | lhsPatternBind consequenceInvocation*
           ) SEMI? ;

lhsPatternBind : (label|unif)? ( LPAREN lhsPattern (DRL_OR lhsPattern)* RPAREN | lhsPattern ) ;

/*
lhsPattern : xpathPrimary (OVER patternFilter)? |
             ( QUESTION? qualifiedIdentifier LPAREN positionalConstraints? constraints? RPAREN (OVER patternFilter)? (FROM patternSource)? ) ;
*/

lhsPattern
  : xpathPrimary (DRL_OVER patternFilter)?
  | QUESTION? objectType=drlQualifiedName LPAREN positionalConstraints? constraints? RPAREN drlAnnotation* (DRL_OVER patternFilter)? (DRL_FROM patternSource)?
  ;
positionalConstraints : constraint (COMMA constraint)* SEMI ;
constraints : constraint (COMMA constraint)* ;
constraint : ( nestedConstraint | conditionalOrExpression ) ;
nestedConstraint : ( drlIdentifier ( DOT | NULL_SAFE_DOT | HASH ) )* drlIdentifier (DOT | NULL_SAFE_DOT ) LPAREN constraints RPAREN ;

// named consequence

// consequenceInvocation := conditionalBranch | namedConsequence
consequenceInvocation : conditionalBranch | namedConsequenceInvocation ;

// conditionalBranch := IF LEFT_PAREN conditionalExpression RIGHT_PAREN
//                      ( namedConsequence | breakingNamedConsequence )
//                      ( ELSE ( namedConsequence | breakingNamedConsequence | conditionalBranch ) )?
conditionalBranch : IF LPAREN conditionalOrExpression RPAREN
                    ( do1=namedConsequenceInvocation | break1=breakingNamedConsequenceInvocation )
                    ( ELSE ( do2=namedConsequenceInvocation | break2=breakingNamedConsequenceInvocation | conditionalBranch ) )? ;

// namedConsequence := DO LEFT_SQUARE ID RIGHT_SQUARE BREAK?
namedConsequenceInvocation : DO LBRACK drlIdentifier RBRACK ; // BREAK? is not actually implmented in the old parser

// breakingNamedConsequence := BREAK LEFT_SQUARE ID RIGHT_SQUARE
breakingNamedConsequenceInvocation : BREAK LBRACK drlIdentifier RBRACK ;


relationalOperator
    : EQUAL
    | NOTEQUAL
    | LE
    | GE
    | GT
    | LT
    | drlRelationalOperator
    | temporalOperator
    ;

drlRelationalOperator : DRL_NOT? builtInOperator ;

/* function := FUNCTION type? ID parameters(typed) chunk_{_} */
functiondef : DRL_FUNCTION typeTypeOrVoid? drlIdentifier formalParameters drlBlock ;


/* extending JavaParser qualifiedName */
drlQualifiedName
    : drlIdentifier (DOT drlIdentifier)*
    ;

/* extending JavaParser expression */
drlExpression
    : drlPrimary
    | drlExpression bop=DOT
      (
         drlIdentifier
       | drlMethodCall
       | THIS
       | NEW nonWildcardTypeArguments? innerCreator
       | SUPER superSuffix
       | explicitGenericInvocation
       | inlineCast
      )
    | drlExpression NULL_SAFE_DOT ( drlIdentifier | drlMethodCall )
    | drlExpression LBRACK drlExpression RBRACK
    | DRL_EVAL LPAREN conditionalOrExpression RPAREN
    | drlMethodCall
    | NEW drlCreator
    | LPAREN annotation* typeType (BITAND typeType)* RPAREN drlExpression
    | drlExpression postfix=(INC | DEC)
    | prefix=(ADD|SUB|INC|DEC) drlExpression
    | prefix=(TILDE|BANG) drlExpression
    | drlExpression bop=(MUL|DIV|MOD) drlExpression
    | drlExpression bop=(ADD|SUB) drlExpression
    | drlExpression (LT LT | GT GT GT | GT GT) drlExpression
    | drlExpression bop=INSTANCEOF (typeType | pattern)
    | drlExpression relationalOperator drlExpression
    | drlExpression bop=DRL_UNIFY drlExpression
    | drlExpression bop=BITAND drlExpression
    | drlExpression bop=CARET drlExpression
    | drlExpression bop=BITOR drlExpression
    | drlExpression bop=AND drlExpression
    | drlExpression bop=OR drlExpression
    | <assoc=right> drlExpression bop=QUESTION drlExpression COLON drlExpression
    | <assoc=right> drlExpression
      bop=(ASSIGN | ADD_ASSIGN | SUB_ASSIGN | MUL_ASSIGN | DIV_ASSIGN | AND_ASSIGN | OR_ASSIGN | XOR_ASSIGN | RSHIFT_ASSIGN | URSHIFT_ASSIGN | LSHIFT_ASSIGN | MOD_ASSIGN)
      drlExpression
    | lambdaExpression // Java8
    | switchExpression // Java17

    // Java 8 methodReference
    | drlExpression COLONCOLON typeArguments? drlIdentifier
    | typeType COLONCOLON (typeArguments? drlIdentifier | NEW)
    | classType COLONCOLON typeArguments? NEW

    // OOPath
    | xpathPrimary
    | backReferenceExpression
    ;

backReferenceExpression : (DOT DOT DIV)+  drlExpression ;


/* extending JavaParser methodCall in order to accept drl keywords as method name */
  drlMethodCall
      : drlIdentifier LPAREN drlExpressionList? RPAREN
      | THIS LPAREN drlExpressionList? RPAREN
      | SUPER LPAREN drlExpressionList? RPAREN
      ;

  drlExpressionList
      : drlExpression (COMMA drlExpression)*
      ;


temporalOperator : DRL_NOT? bop=(DRL_AFTER | DRL_BEFORE | DRL_COINCIDES | DRL_DURING | DRL_INCLUDES | DRL_FINISHES | DRL_FINISHED_BY | DRL_MEETS | DRL_MET_BY | DRL_OVERLAPS | DRL_OVERLAPPED_BY | DRL_STARTS | DRL_STARTED_BY) timeAmount? ;

timeAmount : LBRACK (TIME_INTERVAL | DECIMAL_LITERAL | MUL | SUB MUL) (COMMA (TIME_INTERVAL | DECIMAL_LITERAL | MUL | SUB MUL))* RBRACK ;

/* extending JavaParser primary */
drlPrimary
    : LPAREN drlExpression RPAREN
    | THIS
    | SUPER
    | NEW drlCreator
    | drlLiteral
    | drlIdentifier
    | typeTypeOrVoid DOT CLASS
    | nonWildcardTypeArguments (explicitGenericInvocationSuffix | THIS arguments)
    | inlineListExpression
    | inlineMapExpression
    | inlineCast
    ;

inlineCast : drlIdentifier HASH drlIdentifier ;

/* extending JavaParser literal */
drlLiteral
    : integerLiteral
    | floatLiteral
    | DRL_BIG_DECIMAL_LITERAL
    | DRL_BIG_INTEGER_LITERAL
    | CHAR_LITERAL
    | DRL_STRING_LITERAL
    | BOOL_LITERAL
    | NULL_LITERAL
    | TEXT_BLOCK // Java17
    | TIME_INTERVAL
    ;

inlineListExpression
    :   LBRACK expressionList? RBRACK
    ;

inlineMapExpression
    :	LBRACK mapExpressionList RBRACK
    ;

mapExpressionList
    :	mapEntry (COMMA mapEntry)*
    ;

mapEntry
    :	drlExpression COLON drlExpression
    ;

/*
 patternFilter :=   OVER filterDef
 filterDef := label ID LEFT_PAREN parameters RIGHT_PAREN
*/
patternFilter : DRL_WINDOW COLON drlIdentifier LPAREN expressionList RPAREN ;

/*
 patternSource := FROM
                ( fromAccumulate
                | fromCollect
                | fromEntryPoint
                | fromWindow
                | fromExpression )
*/
patternSource : fromAccumulate
              | fromCollect
              | fromEntryPoint
              | fromWindow
              | fromExpression
              ;

fromExpression : conditionalOrExpression ;


/*
fromAccumulate := ACCUMULATE LEFT_PAREN lhsAnd (COMMA|SEMICOLON)
                        ( INIT chunk_(_) COMMA ACTION chunk_(_) COMMA
                          ( REVERSE chunk_(_) COMMA)? RESULT chunk_(_)
                        | accumulateFunction
                        ) RIGHT_PAREN
*/
fromAccumulate : (DRL_ACCUMULATE|DRL_ACC) LPAREN lhsAndDef (COMMA|SEMI)
                   ( DRL_INIT LPAREN initBlockStatements=chunk? RPAREN COMMA? DRL_ACTION LPAREN actionBlockStatements=chunk? RPAREN COMMA? DRL_REVERSE LPAREN reverseBlockStatements=chunk? RPAREN COMMA? DRL_RESULT LPAREN resultBlockStatements=chunk RPAREN
                   | DRL_INIT LPAREN initBlockStatements=chunk? RPAREN COMMA? DRL_ACTION LPAREN actionBlockStatements=chunk? RPAREN COMMA? DRL_RESULT LPAREN resultBlockStatements=chunk RPAREN
                   | accumulateFunction
                   )
                 RPAREN (SEMI)?
                 ;

blockStatements : drlBlockStatement* ;

/*
accumulateFunction := label? ID parameters
*/
accumulateFunction : (label|unif)? drlIdentifier conditionalExpressions ;

// parameters := LEFT_PAREN (conditionalExpression (COMMA conditionalExpression)* )? RIGHT_PAREN
conditionalExpressions : LPAREN (conditionalExpression (COMMA conditionalExpression)* )? RPAREN ;

// fromCollect := COLLECT LEFT_PAREN lhsPatternBind RIGHT_PAREN

fromCollect : DRL_COLLECT LPAREN lhsPatternBind RPAREN ;

fromEntryPoint : DRL_ENTRY_POINT stringId ;

// fromWindow := WINDOW ID
fromWindow : DRL_WINDOW drlIdentifier ;

/*
 lhsExists := EXISTS
           ( (LEFT_PAREN (or_key|and_key))=> lhsOr  // prevents '((' for prefixed and/or
           | LEFT_PAREN lhsOr RIGHT_PAREN
           | lhsPatternBind
           )
*/
// Use lhsExpression instead of lhsOr because lhsExpression has good enough structure
lhsExists : DRL_EXISTS ( LPAREN lhsExpression RPAREN | lhsPatternBind ) ;

/*
 lhsNot := NOT
           ( (LEFT_PAREN (or_key|and_key))=> lhsOr  // prevents '((' for prefixed and/or
           | LEFT_PAREN lhsOr RIGHT_PAREN
           | lhsPatternBind
           )
*/
// Use lhsExpression instead of lhsOr because lhsExpression has good enough structure
lhsNot : DRL_NOT ( LPAREN lhsExpression RPAREN | lhsPatternBind ) ;

/**
 * lhsEval := EVAL LEFT_PAREN conditionalExpression RIGHT_PAREN
 */
lhsEval : DRL_EVAL LPAREN conditionalOrExpression RPAREN ;

/**
 * lhsForall := FORALL LEFT_PAREN lhsPatternBind+ RIGHT_PAREN
 */

lhsForall : DRL_FORALL LPAREN lhsPatternBind+ RPAREN ;

/**
 * lhsAccumulate := (ACCUMULATE|ACC) LEFT_PAREN lhsAnd (COMMA|SEMICOLON)
 *                      accumulateFunctionBinding (COMMA accumulateFunctionBinding)*
 *                      (SEMICOLON constraints)?
 *                  RIGHT_PAREN SEMICOLON?
 */

lhsAccumulate : (DRL_ACCUMULATE|DRL_ACC) LPAREN lhsAndDef (COMMA|SEMI)
                   accumulateFunction (COMMA accumulateFunction)*
                   (SEMI constraints)?
                 RPAREN (SEMI)?
                 ;

lhsGroupBy : DRL_GROUPBY LPAREN lhsAndDef (COMMA|SEMI)
               groupByKeyBinding SEMI
               accumulateFunction (COMMA accumulateFunction)*
               (SEMI constraints)?
             RPAREN (SEMI)?
             ;

groupByKeyBinding : label? conditionalExpression ;

rhs : DRL_THEN consequenceBody namedConsequence* ;

consequenceBody : ( RHS_COMMENT | RHS_LINE_COMMENT | RHS_STRING_LITERAL | RHS_CHUNK )* ;

// THEN LEFT_SQUARE ID RIGHT_SQUARE chunk
namedConsequence : RHS_NAMED_CONSEQUENCE_THEN consequenceBody ;

stringId : ( drlIdentifier | DRL_STRING_LITERAL ) ;

//type := ID typeArguments? ( DOT ID typeArguments? )* (LEFT_SQUARE RIGHT_SQUARE)*
//typeArguments : LT typeArgument (COMMA typeArgument)* GT ;
//typeArgument : QUESTION (( EXTENDS | SUPER ) type )? |  type ;

drlArguments : LPAREN drlArgument (COMMA drlArgument)* RPAREN ;
drlArgument : ( stringId | floatLiteral | BOOL_LITERAL | NULL_LITERAL ) ;

drlAnnotation
    // TODO actions can be removed once there is a DRL6ExpressionsVisitorImpl.
    : {boolean buildState = buildDescr; buildDescr = true;} anno=fullAnnotation[null] {buildDescr = buildState;} // either standard Java annotation
    | AT name=drlQualifiedName (LPAREN chunk RPAREN)? ; // or support @watch(!*, age) etc.

// attributes := (ATTRIBUTES COLON?)? [ attribute ( COMMA? attribute )* ]
attributes : (DRL_ATTRIBUTES COLON?)? attribute ( COMMA? attribute )* ;
attribute : name=( DRL_SALIENCE | DRL_ENABLED ) conditionalAttributeValue #expressionAttribute
          | name=( DRL_NO_LOOP | DRL_AUTO_FOCUS | DRL_LOCK_ON_ACTIVE ) BOOL_LITERAL? #booleanAttribute
          | name=( DRL_ACTIVATION_GROUP | DRL_RULEFLOW_GROUP | DRL_DATE_EFFECTIVE | DRL_DATE_EXPIRES | DRL_DIALECT ) DRL_STRING_LITERAL #stringAttribute
          | name=DRL_CALENDARS DRL_STRING_LITERAL ( COMMA DRL_STRING_LITERAL )* #stringListAttribute
          | name=DRL_TIMER ( DECIMAL_LITERAL | LPAREN chunk RPAREN ) #intOrChunkAttribute
          | name=DRL_DURATION ( DECIMAL_LITERAL | LPAREN chunk RPAREN ) #intOrChunkAttribute
          ;

conditionalAttributeValue : ( LPAREN conditionalExpression RPAREN | conditionalExpression ) ;

chunk : .+?;

assignmentOperator : ASSIGN
                   |   ADD_ASSIGN
                   |   SUB_ASSIGN
                   |   MUL_ASSIGN
                   |   DIV_ASSIGN
                   |   AND_ASSIGN
                   |   OR_ASSIGN
                   |   XOR_ASSIGN
                   |   MOD_ASSIGN
                   |   LT LT ASSIGN ;

label : drlIdentifier COLON ;
unif : drlIdentifier DRL_UNIFY ;

/* extending JavaParser variableInitializer */
drlVariableInitializer
    : arrayInitializer
    | drlExpression
    ;

 drlCreator
     : nonWildcardTypeArguments createdName classCreatorRest
     | createdName (drlArrayCreatorRest | classCreatorRest)
     ;

 drlArrayCreatorRest
     : LBRACK (RBRACK (LBRACK RBRACK)* drlArrayInitializer | expression RBRACK (LBRACK expression RBRACK)* (LBRACK RBRACK)*)
     ;

 drlArrayInitializer
     : LBRACE (drlVariableInitializer (COMMA drlVariableInitializer)* (COMMA)? )? RBRACE
     ;

/* extending JavaParser block */
drlBlock
    : LBRACE drlBlockStatement* RBRACE
    ;
/* extending JavaParser blockStatement */
drlBlockStatement
    : drlLocalVariableDeclaration SEMI?
    | drlStatement
    | localTypeDeclaration
    ;

/* extending JavaParser statement */
drlStatement
    : blockLabel=drlBlock
    | ASSERT drlExpression (COLON drlExpression)? SEMI
    | IF parExpression drlStatement (ELSE drlStatement)?
    | FOR LPAREN forControl RPAREN drlStatement
    | WHILE parExpression drlStatement
    | DO drlStatement WHILE parExpression SEMI
    | TRY drlBlock (catchClause+ finallyBlock? | finallyBlock)
    | TRY resourceSpecification drlBlock catchClause* finallyBlock?
    | SWITCH parExpression LBRACE switchBlockStatementGroup* switchLabel* RBRACE
    | SYNCHRONIZED parExpression drlBlock
    | RETURN drlExpression? SEMI
    | THROW drlExpression SEMI
    | BREAK drlIdentifier? SEMI
    | CONTINUE drlIdentifier? SEMI
    | YIELD drlExpression SEMI // Java17
    | SEMI
    | statementExpression=drlExpression SEMI
    | switchExpression SEMI? // Java17
    | identifierLabel=drlIdentifier COLON drlStatement
    ;

/* extending JavaParser localVariableDeclaration */
drlLocalVariableDeclaration
    : variableModifier* (typeType drlVariableDeclarators | VAR drlIdentifier ASSIGN expression)
    ;

/* extending JavaParser variableDeclarators */
drlVariableDeclarators
    : drlVariableDeclarator (COMMA drlVariableDeclarator)*
    ;

/* extending JavaParser variableDeclarator */
drlVariableDeclarator
    : drlVariableDeclaratorId (ASSIGN drlVariableInitializer)?
    ;

/* extending JavaParser variableDeclaratorId */
drlVariableDeclaratorId
    : drlIdentifier (LBRACK RBRACK)*
    ;
