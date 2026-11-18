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
package org.drools.mvel.asm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.drools.compiler.compiler.DescrBuildError;
import org.drools.drl.ast.descr.BaseDescr;
import org.drools.drl.ast.descr.FunctionDescr;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.compiler.rule.builder.RuleBuildContext;
import org.drools.compiler.rule.builder.dialect.java.parser.JavaBlockDescr;
import org.drools.compiler.rule.builder.dialect.java.parser.JavaInterfacePointsDescr;
import org.drools.compiler.rule.builder.dialect.java.parser.JavaLocalDeclarationDescr;
import org.drools.compiler.rule.builder.dialect.java.parser.JavaModifyBlockDescr;
import org.drools.base.factmodel.ClassDefinition;
import org.drools.base.rule.ConsequenceMetaData;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.TypeDeclaration;
import org.drools.core.rule.consequence.KnowledgeHelper;
import org.drools.util.ClassUtils;
import org.drools.util.bitmask.AllSetBitMask;
import org.drools.util.bitmask.BitMask;
import org.drools.mvel.builder.MVELAnalysisResult;
import org.drools.mvel.builder.MVELDialect;
import org.drools.mvel.java.JavaAnalysisResult;
import org.kie.api.definition.type.FactField;
import org.mvel2.CompileException;
import org.mvel2.asm.MethodVisitor;
import org.mvel2.asm.Opcodes;

import static org.drools.compiler.rule.builder.dialect.DialectUtil.buildBlockDescrs;
import static org.drools.compiler.rule.builder.dialect.DialectUtil.findClassByName;
import static org.drools.base.reteoo.PropertySpecificUtil.allSetButTraitBitMask;
import static org.drools.base.reteoo.PropertySpecificUtil.getEmptyPropertyReactiveMask;
import static org.drools.base.reteoo.PropertySpecificUtil.setPropertyOnMask;
import static org.drools.util.ClassUtils.getter2property;
import static org.drools.util.ClassUtils.setter2property;
import static org.drools.util.StringUtils.extractFirstIdentifier;
import static org.drools.util.StringUtils.findEndOfMethodArgsIndex;
import static org.drools.util.StringUtils.splitArgumentsList;
import static org.drools.util.StringUtils.splitStatementsAcrossBlocks;


public final class AsmUtil {
    private static final Pattern LINE_BREAK_FINDER = Pattern.compile( "\\r\\n|\\r|\\n" );

    public static String fixBlockDescr(final RuleBuildContext context,
                                       final JavaAnalysisResult analysis,
                                       Map<String, Declaration> decls) {
        // This is a list of all the non container blocks, which initially are in tree form.
        List<JavaBlockDescr> blocks = buildBlockDescrs(new ArrayList<>(), analysis.getBlockDescrs());

        return fixBlockDescr(context, analysis, decls, blocks);
    }

    public static String fixBlockDescr(final RuleBuildContext context,
                                       final JavaAnalysisResult analysis,
                                       Map<String, Declaration> decls,
                                       List<JavaBlockDescr> blocks) {

        String originalCode = analysis.getAnalyzedExpr();

        // sorting exit points for correct order iteration
        blocks.sort( Comparator.comparingInt( JavaBlockDescr::getStart ) );

        StringBuilder consequence = new StringBuilder();
        int lastAdded = 0;

        for (JavaBlockDescr block : blocks) {
            if (block.getEnd() == 0 || block.getEnd() > originalCode.length() ) {
                // do nothing, it was incorrectly parsed, but this error should be picked up else where
                continue;
            }

            // adding chunk
            consequence.append( originalCode, lastAdded, block.getStart() - 1 );

            lastAdded = block.getEnd();

            switch (block.getType()) {
                case MODIFY:
                case UPDATE:
                case DELETE:
                    rewriteDescr(context, consequence, block, analysis, decls);
                    break;
                case ENTRY:
                case EXIT:
                case CHANNEL:
                    rewriteInterfacePoint(context, originalCode, consequence, ( JavaInterfacePointsDescr ) block);
                    break;
                case INSERT:
                    parseInsertDescr(context, block);
                default:
                    consequence.append( originalCode, block.getStart() - 1, lastAdded );
            }
        }
        consequence.append(originalCode.substring(lastAdded));

        return consequence.toString();
    }

    private static void parseInsertDescr(RuleBuildContext context, JavaBlockDescr block) {
        String expr = block.getTargetExpression();
        if (expr.startsWith("new ")) {
            int argsStart = expr.indexOf('(');
            if (argsStart > 0) {
                String className = expr.substring(4, argsStart).trim();
                Class<?> typeClass = findClassByName(context, className);
                TypeDeclaration typeDeclaration = typeClass == null ? null : context.getKnowledgeBuilder().getTypeDeclaration(typeClass);
                if (typeDeclaration != null) {
                    ConsequenceMetaData.Statement statement = new ConsequenceMetaData.Statement(ConsequenceMetaData.Statement.Type.INSERT, typeClass);
                    context.getRule().getConsequenceMetaData().addStatement(statement);

                    String constructorParams = expr.substring(argsStart+1, expr.indexOf(')')).trim();
                    List<String> args = splitArgumentsList(constructorParams);
                    ClassDefinition classDefinition = typeDeclaration.getTypeClassDef();
                    List<FactField> fields = classDefinition.getFields();
                    if (args.size() == fields.size()) {
                        for (int i = 0; i < args.size(); i++) {
                            statement.addField(fields.get(i).getName(), args.get(i));
                        }
                    }
                }
            }
        }
    }

    private static void rewriteInterfacePoint(final RuleBuildContext context,
                                              final String originalCode,
                                              final StringBuilder consequence,
                                              final JavaInterfacePointsDescr ep) {
        // rewriting it for proper exitPoints access
        consequence.append("drools.get");
        if (ep.getType() == JavaBlockDescr.BlockType.EXIT) {
            consequence.append("ExitPoint( ");
        } else if (ep.getType() == JavaBlockDescr.BlockType.ENTRY) {
            consequence.append("EntryPoint( ");
        } else if (ep.getType() == JavaBlockDescr.BlockType.CHANNEL) {
            consequence.append("Channel( ");
        } else {
            context.addError(new DescrBuildError(context.getParentDescr(),
                    context.getRuleDescr(),
                    ep,
                    "Unable to rewrite code block: " + ep + "\n"));

            return;
        }

        consequence.append(ep.getId());
        consequence.append(" )");

        // the following is a hack to preserve line breaks.
        String originalBlock = originalCode.substring(ep.getStart() - 1,
                ep.getEnd());
        int end = originalBlock.indexOf("]");
        addLineBreaks(consequence, originalBlock.substring(0, end));
    }

    private static void rewriteDescr(final RuleBuildContext context,
                                     final StringBuilder consequence,
                                     final JavaBlockDescr d,
                                     final JavaAnalysisResult analysis,
                                     final Map<String, Declaration> decls) {
        if ( d.getEnd() == 0 ) {
            // do nothing, it was incorrectly parsed, but this error should be picked up else where
            return;
        }

        boolean typeSafety = context.isTypesafe();
        context.setTypesafe( false ); // we have to analyse in dynamic mode for now, as we cannot safely determine all input vars

        Map<String, Class<?>> localTypes = d.getInputs();
        if( d.getInScopeLocalVars() != null && ! d.getInScopeLocalVars().isEmpty() ) {
            localTypes = new HashMap<>( d.getInputs() != null ? d.getInputs() : Collections.emptyMap() );
            for( JavaLocalDeclarationDescr local : d.getInScopeLocalVars() ) {
                // these are variables declared in the code itself that are in the scope for this expression
                try {
                    Class<?> type = context.getDialect( "java" ).getPackageRegistry().getTypeResolver().resolveType( local.getRawType() );
                    for( JavaLocalDeclarationDescr.IdentifierDescr id : local.getIdentifiers() ) {
                        localTypes.put( id.getIdentifier(), type );
                    }
                } catch ( ClassNotFoundException e ) {
                    context.addError(new DescrBuildError(context.getRuleDescr(),
                            context.getParentDescr(),
                            null,
                            "Unable to resolve type " + local.getRawType() + ":\n" + e.getMessage()));
                }
            }
        }

        MVELDialect mvel = (MVELDialect) context.getDialect("mvel");
        MVELAnalysisResult mvelAnalysis = ( MVELAnalysisResult ) mvel.analyzeBlock( context,
                d.getTargetExpression(),
                analysis.getBoundIdentifiers(),
                localTypes,
                "drools",
                KnowledgeHelper.class);
        context.setTypesafe( typeSafety );
        if ( mvelAnalysis == null ) {
            // something bad happened, issue already logged in errors
            return;
        }

        Class<?> ret = mvelAnalysis.getReturnType();

        if ( ret == null ) {
            // not possible to evaluate expression return value
            context.addError(new DescrBuildError(context.getParentDescr(),
                    context.getRuleDescr(),
                    analysis.getAnalyzedExpr(),
                    "Unable to determine the resulting type of the expression: " + d.getTargetExpression() + "\n"));

            return;
        }

        // adding modify expression
        String retString = ClassUtils.canonicalName( ret );
        String declrString;
        if (d.getTargetExpression().charAt( 0 ) == '(' ) {
            declrString = d.getTargetExpression().substring( 1,d.getTargetExpression().length() -1 ).trim();
        } else {
            declrString = d.getTargetExpression();
        }
        String obj = declrString;
        Declaration declr = decls.get( declrString );

        consequence.append( "{ " );

        if ( declr == null ) {
            obj = "__obj__";
            consequence.append( retString );
            consequence.append( " " );
            consequence.append( obj);
            consequence.append( " = " );
            consequence.append( d.getTargetExpression() );
            consequence.append( "; " );
        }

        if ( declr == null || declr.isInternalFact() ) {
            consequence.append( "org.kie.api.runtime.rule.FactHandle " );
            consequence.append( obj );
            consequence.append( "__Handle2__ = drools.getFactHandle(" );
            consequence.append( obj );
            consequence.append( ");" );
        }

        // the following is a hack to preserve line breaks.
        String originalBlock = analysis.getAnalyzedExpr().substring( d.getStart() - 1, d.getEnd() );

        switch (d.getType()) {
            case MODIFY:
                rewriteModifyDescr(context, d, analysis, originalBlock, consequence, declr, obj);
                break;
            case UPDATE:
                rewriteUpdateDescr(context, d, analysis, consequence, declr, obj);
                break;
            case DELETE:
                rewriteDeleteDescr( context, d, consequence, declr, obj );
                break;
        }
    }

    private static void rewriteModifyDescr( RuleBuildContext context,
                                            JavaBlockDescr d,
                                            JavaAnalysisResult analysis,
                                            String originalBlock,
                                            StringBuilder consequence,
                                            Declaration declr,
                                            String obj ) {
        List<String> settableProperties = null;

        Class<?> typeClass = findModifiedClass(context, d, declr);
        TypeDeclaration typeDeclaration = typeClass == null ? null : context.getKnowledgeBuilder().getTypeDeclaration(typeClass);
        boolean isPropertyReactive = typeDeclaration != null && typeDeclaration.isPropertyReactive();
        if (isPropertyReactive) {
            settableProperties = typeDeclaration.getAccessibleProperties();
        }

        ConsequenceMetaData.Statement statement = null;
        if (typeDeclaration != null) {
            statement = new ConsequenceMetaData.Statement(ConsequenceMetaData.Statement.Type.MODIFY, typeClass);
            context.getRule().getConsequenceMetaData().addStatement(statement);
        }
        BitMask modificationMask = isPropertyReactive ? getEmptyPropertyReactiveMask(settableProperties.size()) : allSetButTraitBitMask();
        if (isPropertyReactive) {
            // collect modification outside modify block
            modificationMask = getModificationMask( analysis.getAnalyzedExpr(), obj, modificationMask, typeDeclaration, settableProperties, statement, false );
        }

        int end = originalBlock.indexOf("{");
        if (end == -1) {
            // no block
            context.addError(new DescrBuildError(context.getParentDescr(),
                    context.getRuleDescr(),
                    null,
                    "Block missing after modify" + d.getTargetExpression() + " ?\n"));
            return;
        }

        addLineBreaks(consequence, originalBlock.substring(0, end));

        int start = end + 1;
        // adding each of the expressions:
        for (String exprStr : (( JavaModifyBlockDescr ) d).getExpressions()) {
            end = originalBlock.indexOf(exprStr, start);
            addLineBreaks(consequence, originalBlock.substring(start, end));
            consequence.append(obj).append(".");
            consequence.append(exprStr);
            consequence.append("; ");
            start = end + exprStr.length();

            if (typeDeclaration != null) {
                // collect modification inside modify block
                modificationMask = parseModifiedProperties(statement, settableProperties, typeDeclaration, isPropertyReactive, modificationMask, exprStr);
            }
        }

        addLineBreaks(consequence, originalBlock.substring(end));

        appendUpdateStatement(consequence, declr, obj, modificationMask, typeClass);
    }

    private static void rewriteUpdateDescr( RuleBuildContext context,
                                            JavaBlockDescr d,
                                            JavaAnalysisResult analysis,
                                            StringBuilder consequence,
                                            Declaration declr,
                                            String obj) {

        if (analysis.getAssignedVariables().contains( obj )) {
            consequence.insert( 0, "drools.delete(" + obj + ");\n" );
            consequence.append( "drools.insert(" ).append( obj ).append( ");\n}" );
            return;
        }

        BitMask modificationMask = AllSetBitMask.get();
        Class<?> typeClass = findModifiedClass(context, d, declr);
        TypeDeclaration typeDeclaration = typeClass == null ? null : context.getKnowledgeBuilder().getTypeDeclaration(typeClass);

        if (typeDeclaration != null) {
            boolean isPropertyReactive = typeDeclaration.isPropertyReactive();
            List<String> settableProperties = null;
            if (isPropertyReactive) {
                settableProperties = typeDeclaration.getAccessibleProperties();
                modificationMask = getEmptyPropertyReactiveMask(settableProperties.size());
            }

            ConsequenceMetaData.Statement statement = new ConsequenceMetaData.Statement(ConsequenceMetaData.Statement.Type.MODIFY, typeClass);
            context.getRule().getConsequenceMetaData().addStatement(statement);

            if (isPropertyReactive) {
                modificationMask = getModificationMask( analysis.getAnalyzedExpr(), obj, modificationMask, typeDeclaration, settableProperties, statement, true );
            }
        }

        appendUpdateStatement(consequence, declr, obj, modificationMask, typeClass);
    }

    private static BitMask getModificationMask( String originalConsequence, String obj, BitMask modificationMask, TypeDeclaration typeDeclaration, List<String> settableProperties, ConsequenceMetaData.Statement statement, boolean isUpdate ) {
        boolean parsedExprOnce = false;
        // a late optimization to include this for-loop within this if
        for (String expr : splitStatementsAcrossBlocks( originalConsequence )) {
            String updateExpr = expr.replaceFirst("^\\Q" + obj + "\\E\\s*\\.", "");
            if (!updateExpr.equals(expr)) {
                parsedExprOnce = true;
                modificationMask = parseModifiedProperties( statement, settableProperties, typeDeclaration, true, modificationMask, updateExpr);
                if ( modificationMask == allSetButTraitBitMask() ) {
                    // opt: if we were unable to detect the property in the mask is all set, so avoid the rest of the cycle
                    break;
                }
            }
        }
        if ( isUpdate && !parsedExprOnce ) {
            // never called parseModifiedProperties(), hence never had the opportunity to "miss" the property and set mask to All-set; doing so here:
            modificationMask = allSetButTraitBitMask();
        }
        return modificationMask;
    }

    private static void appendUpdateStatement(StringBuilder consequence, Declaration declr, String obj, BitMask modificationMask, Class<?> typeClass) {
        boolean isInternalFact = declr == null || declr.isInternalFact();
        consequence
                .append("drools.update( ")
                .append(obj)
                .append(isInternalFact ? "__Handle2__, " : "__Handle__, ")
                .append(modificationMask.getInstancingStatement())
                .append(", ")
                .append(typeClass != null ? typeClass.getCanonicalName() : "java.lang.Object")
                .append(".class")
                .append(" ); }");
    }

    private static BitMask parseModifiedProperties( ConsequenceMetaData.Statement statement,
                                                    List<String> settableProperties,
                                                    TypeDeclaration typeDeclaration,
                                                    boolean propertyReactive,
                                                    BitMask modificationMask,
                                                    String exprStr) {
        int endMethodName = exprStr.indexOf('(');
        if (endMethodName >= 0) {
            String methodName = exprStr.substring(0, endMethodName).trim();
            String propertyName = setter2property(methodName);

            int endMethodArgs = findEndOfMethodArgsIndex(exprStr, endMethodName);
            String methodParams = exprStr.substring(endMethodName+1, endMethodArgs).trim();
            List<String> args = splitArgumentsList(methodParams);
            int argsNr = args.size();

            if (propertyName == null && exprStr.length() > endMethodArgs+1 && exprStr.substring(endMethodArgs+1).trim().startsWith(".")) {
                propertyName = getter2property(methodName);
            }

            if (propertyName != null) {
                modificationMask = updateModificationMask(settableProperties, propertyReactive, modificationMask, propertyName);
                statement.addField(propertyName, argsNr > 0 ? args.get(0) : null);
            }

            List<String> modifiedProps = typeDeclaration.getTypeClassDef().getModifiedPropsByMethod( methodName, argsNr );
            if (modifiedProps != null) {
                for (String modifiedProp : modifiedProps) {
                    modificationMask = updateModificationMask(settableProperties, propertyReactive, modificationMask, modifiedProp);
                    statement.addField(modifiedProp, argsNr > 0 ? args.get(0) : null);
                }
            }

            if ( propertyReactive && propertyName == null && modifiedProps == null ) {
                // I'm property reactive, but I was unable to infer which properties was modified, setting all bit in bitmask
                modificationMask = allSetButTraitBitMask();
            }
        } else {
            String propertyName = extractFirstIdentifier(exprStr, 0);
            modificationMask = updateModificationMask(settableProperties, propertyReactive, modificationMask, propertyName);
            int equalPos = exprStr.indexOf('=');
            if (equalPos >= 0) {
                String value = exprStr.substring(equalPos+1).trim();
                statement.addField(propertyName, value);
            }
        }
        return modificationMask;
    }

    private static BitMask updateModificationMask( List<String> settableProperties,
                                                   boolean propertyReactive,
                                                   BitMask modificationMask,
                                                   String propertyName) {
        if (propertyReactive) {
            int index = settableProperties.indexOf(propertyName);
            if (index >= 0) {
                modificationMask = setPropertyOnMask(modificationMask, index);
            }
        }
        return modificationMask;
    }

    private static void rewriteDeleteDescr( RuleBuildContext context,
                                            JavaBlockDescr d,
                                            StringBuilder consequence,
                                            Declaration declr,
                                            String obj ) {
        Class<?> typeClass = findModifiedClass(context, d, declr);
        if (typeClass != null) {
            ConsequenceMetaData.Statement statement = new ConsequenceMetaData.Statement(ConsequenceMetaData.Statement.Type.RETRACT, typeClass);
            context.getRule().getConsequenceMetaData().addStatement(statement);
        }

        if (declr != null && !declr.isInternalFact()) {
            consequence.append("drools.delete( ").append(obj).append("__Handle__ ); }");
        } else {
            consequence.append("drools.delete( ").append(obj).append("__Handle2__ ); }");
        }
    }

    private static void addLineBreaks(StringBuilder consequence,
                                      String chunk) {
        Matcher m = LINE_BREAK_FINDER.matcher(chunk);
        while (m.find()) {
            consequence.append("\n");
        }
    }

    private static Class<?> findModifiedClass(RuleBuildContext context, JavaBlockDescr d, Declaration declr) {
        if (declr != null) {
            return declr.getDeclarationClass();
        }

        String targetId = d.getTargetExpression().trim();
        while (targetId.charAt(0) == '(' && targetId.charAt(targetId.length()-1) == ')') {
            targetId = targetId.substring(1, targetId.length()-1).trim();
        }

        if (targetId.charAt(0) == '(') {
            int endCast = targetId.indexOf(')');
            if (endCast > 0) {
                String castName = targetId.substring(1, endCast).trim();
                Class<?> cast = findClassByName(context, castName);
                if (cast != null) {
                    return cast;
                }
                targetId = targetId.substring(endCast+1).trim();
            }
        }

        return targetId.contains("(") ? findFunctionReturnedClass(context, targetId) : findDeclarationClass(context, d, targetId);
    }

    private static Class<?> findDeclarationClass(RuleBuildContext context, JavaBlockDescr d, String statement) {
        Class<?> inputClass = d.getInputs() == null ? null : d.getInputs().get(statement);
        if (inputClass != null) {
            return inputClass;
        }

        List<JavaLocalDeclarationDescr> localDeclarationDescrs = d.getInScopeLocalVars();
        if (localDeclarationDescrs == null) {
            return null;
        }

        String className = null;
        for (JavaLocalDeclarationDescr localDeclr : localDeclarationDescrs) {
            for (JavaLocalDeclarationDescr.IdentifierDescr idDescr : localDeclr.getIdentifiers()) {
                if (statement.equals(idDescr.getIdentifier())) {
                    className = localDeclr.getType();
                    break;
                }
            }
            if (className != null) {
                break;
            }
        }

        return findClassByName(context, className);
    }

    private static Class<?> findFunctionReturnedClass(RuleBuildContext context, String statement) {
        String functionName = statement.substring(0, statement.indexOf('('));
        FunctionDescr function = lookupFunction(context, functionName);
        return function == null ? null : findClassByName(context, function.getReturnType());
    }

    private static FunctionDescr lookupFunction(RuleBuildContext context, String functionName) {
        String packageName = context.getRule().getPackageName();
        List<PackageDescr> pkgDescrs = context.getKnowledgeBuilder().getPackageDescrs(packageName);
        for (PackageDescr pkgDescr : pkgDescrs) {
            for (FunctionDescr function : pkgDescr.getFunctions()) {
                if (function.getName().equals(functionName)) {
                    return function;
                }
            }
        }
        return null;
    }

    public static void copyErrorLocation(Exception e, BaseDescr descr) {
        if (e instanceof CompileException ) {
            CompileException compileException = (CompileException)e;
            compileException.setLineNumber(descr.getLine());
            compileException.setColumn(descr.getColumn());
        }
    }

    public static int returnType(String type) {
        if ( "byte".equals( type ) ) {
            return Opcodes.IRETURN;
        } else if ( "char".equals( type ) ) {
            return Opcodes.IRETURN;
        } else if ( "double".equals( type ) ) {
            return Opcodes.DRETURN;
        } else if ( "float".equals( type ) ) {
            return Opcodes.FRETURN;
        } else if ( "int".equals( type ) ) {
            return Opcodes.IRETURN;
        } else if ( "long".equals( type ) ) {
            return Opcodes.LRETURN;
        } else if ( "short".equals( type ) ) {
            return Opcodes.IRETURN;
        } else if ( "boolean".equals( type ) ) {
            return Opcodes.IRETURN;
        } else if ( "void".equals( type ) ) {
            return Opcodes.RETURN;
        } else {
            return Opcodes.ARETURN;
        }

    }

    public static int varType(String type) {
        if ( "byte".equals( type ) ) {
            return Opcodes.ILOAD;
        } else if ( "char".equals( type ) ) {
            return Opcodes.ILOAD;
        } else if ( "double".equals( type ) ) {
            return Opcodes.DLOAD;
        } else if ( "float".equals( type ) ) {
            return Opcodes.FLOAD;
        } else if ( "int".equals( type ) ) {
            return Opcodes.ILOAD;
        } else if ( "long".equals( type ) ) {
            return Opcodes.LLOAD;
        } else if ( "short".equals( type ) ) {
            return Opcodes.ILOAD;
        } else if ( "boolean".equals( type ) ) {
            return Opcodes.ILOAD;
        } else {
            return Opcodes.ALOAD;
        }
    }

    public static int storeType(String type) {
        if ( "byte".equals( type ) ) {
            return Opcodes.ISTORE;
        } else if ( "char".equals( type ) ) {
            return Opcodes.ISTORE;
        } else if ( "double".equals( type ) ) {
            return Opcodes.DSTORE;
        } else if ( "float".equals( type ) ) {
            return Opcodes.FSTORE;
        } else if ( "int".equals( type ) ) {
            return Opcodes.ISTORE;
        } else if ( "long".equals( type ) ) {
            return Opcodes.LSTORE;
        } else if ( "short".equals( type ) ) {
            return Opcodes.ISTORE;
        } else if ( "boolean".equals( type ) ) {
            return Opcodes.ISTORE;
        } else {
            return Opcodes.ASTORE;
        }
    }

    public static int zero( String type ) {
        if ( "byte".equals( type ) ) {
            return Opcodes.ICONST_0;
        } else if ( "char".equals( type ) ) {
            return Opcodes.ICONST_0;
        } else if ( "double".equals( type ) ) {
            return Opcodes.DCONST_0;
        } else if ( "float".equals( type ) ) {
            return Opcodes.FCONST_0;
        } else if ( "int".equals( type ) ) {
            return Opcodes.ICONST_0;
        } else if ( "long".equals( type ) ) {
            return Opcodes.LCONST_0;
        } else if ( "short".equals( type ) ) {
            return Opcodes.ICONST_0;
        } else if ( "boolean".equals( type ) ) {
            return Opcodes.ICONST_0;
        } else {
            return Opcodes.ACONST_NULL;
        }
    }

    public static void pushInt( MethodVisitor mv, int j) {
        switch ( j ) {
            case 0 : mv.visitInsn( Opcodes.ICONST_0 );
                break;
            case 1 : mv.visitInsn( Opcodes.ICONST_1 );
                break;
            case 2 : mv.visitInsn( Opcodes.ICONST_2 );
                break;
            case 3 : mv.visitInsn( Opcodes.ICONST_3 );
                break;
            case 4 : mv.visitInsn( Opcodes.ICONST_4 );
                break;
            case 5 : mv.visitInsn( Opcodes.ICONST_5 );
                break;
            default :
                if ( j >= Byte.MIN_VALUE && j <= Byte.MAX_VALUE ) {
                    mv.visitIntInsn( Opcodes.BIPUSH, j );
                } else if ( j >= Short.MIN_VALUE && j <= Short.MAX_VALUE ) {
                    mv.visitIntInsn( Opcodes.SIPUSH, j );
                } else {
                    mv.visitLdcInsn( j );
                }
        }
    }
}
