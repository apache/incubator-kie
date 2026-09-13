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
package org.drools.compiler.rule.builder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.drools.base.base.AcceptsClassObjectType;
import org.drools.base.base.ClassObjectType;
import org.drools.base.base.ObjectType;
import org.drools.base.base.ValueType;
import org.drools.base.definitions.InternalKnowledgePackage;
import org.drools.base.definitions.rule.impl.QueryImpl;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.factmodel.AnnotationDefinition;
import org.drools.base.factmodel.ClassDefinition;
import org.drools.base.factmodel.FieldDefinition;
import org.drools.base.prototype.PrototypeFieldExtractor;
import org.drools.base.prototype.PrototypeObjectType;
import org.drools.base.reteoo.SortDeclarations;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.PatternSource;
import org.drools.base.rule.RuleConditionElement;
import org.drools.base.rule.TypeDeclaration;
import org.drools.base.rule.XpathBackReference;
import org.drools.base.rule.accessor.AcceptsReadAccessor;
import org.drools.base.rule.accessor.DeclarationScopeResolver;
import org.drools.base.rule.accessor.Evaluator;
import org.drools.base.rule.accessor.FieldValue;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.base.rule.constraint.Constraint;
import org.drools.base.rule.constraint.NegConstraint;
import org.drools.base.rule.constraint.XpathConstraint;
import org.drools.base.time.TimeUtils;
import org.drools.base.util.index.ConstraintTypeOperator;
import org.drools.compiler.builder.impl.TypeDeclarationContext;
import org.drools.compiler.compiler.AnalysisResult;
import org.drools.compiler.compiler.BoundIdentifiers;
import org.drools.compiler.compiler.DescrBuildError;
import org.drools.compiler.compiler.Dialect;
import org.drools.compiler.compiler.DroolsErrorWrapper;
import org.drools.compiler.compiler.DroolsWarningWrapper;
import org.drools.compiler.compiler.PackageRegistry;
import org.drools.compiler.lang.DescrDumper;
import org.drools.compiler.lang.DumperContext;
import org.drools.compiler.rule.builder.EvaluatorDefinition.Target;
import org.drools.drl.parser.lang.XpathAnalysis;
import org.drools.drl.parser.lang.XpathAnalysis.XpathPart;
import org.drools.compiler.rule.builder.util.ConstraintUtil;
import org.drools.core.base.FieldNameSupplier;
import org.drools.core.rule.BehaviorRuntime;
import org.drools.core.rule.SlidingLengthWindow;
import org.drools.core.rule.SlidingTimeWindow;
import org.drools.drl.ast.descr.AnnotationDescr;
import org.drools.drl.ast.descr.AtomicExprDescr;
import org.drools.drl.ast.descr.BaseDescr;
import org.drools.drl.ast.descr.BehaviorDescr;
import org.drools.drl.ast.descr.BindingDescr;
import org.drools.drl.ast.descr.ConnectiveType;
import org.drools.drl.ast.descr.ConstraintConnectiveDescr;
import org.drools.drl.ast.descr.EntryPointDescr;
import org.drools.drl.ast.descr.ExprConstraintDescr;
import org.drools.drl.ast.descr.ExpressionDescr;
import org.drools.drl.ast.descr.FromDescr;
import org.drools.drl.ast.descr.LiteralRestrictionDescr;
import org.drools.drl.ast.descr.MVELExprDescr;
import org.drools.drl.ast.descr.OperatorDescr;
import org.drools.drl.ast.descr.PatternDescr;
import org.drools.drl.ast.descr.PredicateDescr;
import org.drools.drl.ast.descr.RelationalExprDescr;
import org.drools.drl.ast.descr.ReturnValueRestrictionDescr;
import org.drools.drl.ast.descr.RuleDescr;
import org.drools.drl.parser.Drl6ExprParser;
import org.drools.drl.parser.DrlExprParser;
import org.drools.drl.parser.DrlExprParserFactory;
import org.drools.drl.parser.DroolsParserException;
import org.drools.util.ClassUtils;
import org.drools.util.StringUtils;
import org.drools.util.TypeResolver;
import org.kie.api.definition.rule.Watch;
import org.kie.api.definition.type.Role;
import org.kie.api.prototype.Prototype;
import org.kie.internal.builder.KnowledgeBuilderResult;
import org.kie.internal.builder.ResultSeverity;
import org.kie.internal.builder.conf.LanguageLevelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drools.compiler.lang.DescrDumper.normalizeEval;
import static org.drools.compiler.rule.builder.util.AnnotationFactory.getTypedAnnotation;
import static org.drools.compiler.rule.builder.util.PatternBuilderUtil.getNormalizeDate;
import static org.drools.compiler.rule.builder.util.PatternBuilderUtil.normalizeEmptyKeyword;
import static org.drools.compiler.rule.builder.util.PatternBuilderUtil.normalizeStringOperator;
import static org.drools.util.StringUtils.doesFirstPropHaveListMapAccessor;
import static org.drools.util.StringUtils.extractFirstIdentifier;
import static org.drools.util.StringUtils.isIdentifier;

/**
 * A builder for patterns
 */
public class PatternBuilder implements RuleConditionBuilder<PatternDescr> {

    private static final Logger LOG = LoggerFactory.getLogger(PatternBuilder.class);

    private static final java.util.regex.Pattern evalRegexp = java.util.regex.Pattern.compile("^eval\\s*\\(",
                                                                                              java.util.regex.Pattern.MULTILINE);

    private static final java.util.regex.Pattern identifierRegexp = java.util.regex.Pattern.compile("([\\p{L}_$][\\p{L}\\p{N}_$]*)");

    private static final java.util.regex.Pattern getterRegexp = java.util.regex.Pattern.compile("get([\\p{L}_][\\p{L}\\p{N}_]*)\\(\\s*\\)");

    public PatternBuilder() {
    }

    public RuleConditionElement build(RuleBuildContext context,
                                      PatternDescr descr) {
        boolean typeSafe = context.isTypesafe();
        try {
            return this.build(context,
                              descr,
                              null);
        } finally {
            context.setTypesafe(typeSafe);
        }
    }

    /**
     * Build a pattern for the given descriptor in the current
     * context and using the given utils object
     */
    public RuleConditionElement build(RuleBuildContext context,
                                      PatternDescr patternDescr,
                                      Pattern prefixPattern) {
        Declaration xpathStartDeclaration = null;
        if (patternDescr.getObjectType() == null) {
            xpathStartDeclaration = lookupObjectType(context, patternDescr);
        }

        if (patternDescr.getObjectType() == null || patternDescr.getObjectType().equals("")) {
            registerDescrBuildError(context, patternDescr, "ObjectType not correctly defined");
            return null;
        }

        ObjectType objectType = getObjectType(context, patternDescr);
        if (objectType == null) { // if the objectType doesn't exist it has to be query
            return buildQuery(context, patternDescr, patternDescr);
        }

        Pattern pattern = buildPattern(context, patternDescr, xpathStartDeclaration, objectType);
        processClassObjectType(context, objectType, pattern);
        processAnnotations(context, patternDescr, pattern);
        processSource(context, patternDescr, pattern);
        processConstraintsAndBinds(context, patternDescr, xpathStartDeclaration, pattern);
        processBehaviors(context, patternDescr, pattern);

        if (!pattern.hasNegativeConstraint() && "on".equals(System.getProperty("drools.negatable"))) {
            // this is a non-negative pattern, so we must inject the constraint
            pattern.addConstraint(new NegConstraint(false));
        }

        // poping the pattern
        context.getDeclarationResolver().popBuildStack();

        return pattern;
    }

    private Declaration lookupObjectType(RuleBuildContext context, PatternDescr patternDescr) {
        List<? extends BaseDescr> descrs = patternDescr.getConstraint().getDescrs();
        if (descrs.size() != 1 || !(descrs.get(0) instanceof ExprConstraintDescr)) {
            return null;
        }

        ExprConstraintDescr descr = (ExprConstraintDescr) descrs.get(0);
        String expr = descr.getExpression();
        if (expr.charAt(0) != '/') {
            return null;
        }

        XpathAnalysis xpathAnalysis = XpathAnalysis.analyze(expr);
        if (xpathAnalysis.hasError()) {
            registerDescrBuildError(context, patternDescr,
                                    "Invalid xpath expression '" + expr + "': " + xpathAnalysis.getError());
            return null;
        }

        XpathPart firstXpathChunk = xpathAnalysis.getPart(0);
        String identifier = firstXpathChunk.getField();
        DeclarationScopeResolver resolver = context.getDeclarationResolver();

        if (resolver.hasDataSource(identifier)) {
            patternDescr.setObjectType(findObjectType(context, firstXpathChunk, identifier));
            FromDescr fromDescr = new FromDescr();
            fromDescr.setDataSource(new MVELExprDescr(identifier));
            patternDescr.setSource(fromDescr);

            patternDescr.removeAllConstraint();
            firstXpathChunk.getConstraints()
                    .forEach(s -> patternDescr.addConstraint(new ExprConstraintDescr(s)));
            if (!xpathAnalysis.isSinglePart()) {
                String xpathExpr = (patternDescr.getIdentifier() == null ? "" : patternDescr.getIdentifier() + " : ") + expr.substring(xpathAnalysis.getPart(1).getStart());
                patternDescr.addConstraint(new ExprConstraintDescr(xpathExpr));
                patternDescr.setIdentifier("$void$");
            }
        } else {
            Declaration declr = resolver.getDeclaration(identifier);
            if (declr == null) {
                registerDescrBuildError(context, patternDescr,
                                        "The identifier '" + identifier + "' is not in scope");
                return null;
            }
            patternDescr.setObjectType(declr.getExtractor().getExtractToClassName());
            expr = (patternDescr.getIdentifier() != null ? patternDescr.getIdentifier() + (patternDescr.isUnification() ? " := " : " : ") : "")
                    + expr.substring(identifier.length() + 1);
            descr.setExpression(expr);
            return declr;
        }
        return null;
    }

    private String findObjectType(RuleBuildContext context, XpathPart firstXpathChunk, String identifier) {
        if (firstXpathChunk.getInlineCast() != null) {
            return firstXpathChunk.getInlineCast();
        }
        return context.getPkg().getRuleUnitDescriptionLoader()
                .getDescription(context.getRule())
                .flatMap(ruDescr -> ruDescr.getDatasourceType(identifier))
                .map(Class::getCanonicalName)
                .orElse(null);
    }

    private Pattern buildPattern(RuleBuildContext context, PatternDescr patternDescr, Declaration xpathStartDeclaration, ObjectType objectType) {
        String patternIdentifier = patternDescr.getIdentifier();
        boolean duplicateBindings = patternIdentifier != null && objectType instanceof ClassObjectType &&
                context.getDeclarationResolver().isDuplicated(context.getRule(),
                                                              patternIdentifier,
                                                              objectType.getClassName());

        Pattern pattern;
        if (!StringUtils.isEmpty(patternIdentifier) && !duplicateBindings) {

            pattern = new Pattern(context.getNextPatternId(),
                                  0, // offset is 0 by default
                                  0,  // offset is 0 by default
                                  objectType,
                                  patternIdentifier,
                                  isInternalFact(patternDescr, context));
            if (objectType instanceof ClassObjectType) {
                // make sure PatternExtractor is wired up to correct ClassObjectType and set as a target for rewiring
                context.getPkg().wireObjectType(objectType, (AcceptsClassObjectType) pattern.getDeclaration().getExtractor());
            }
        } else {
            pattern = new Pattern(context.getNextPatternId(),
                                  0, // tupleIndex is 0 by default
                                  0, // patternIndex is 0 by default
                                  objectType,
                                  null);
        }
        pattern.setPassive(isPassivePattern(patternDescr, context));

        // adding the newly created pattern to the build stack this is necessary in case of local declaration usage
        context.getDeclarationResolver().pushOnBuildStack(pattern);

        if (duplicateBindings) {
            processDuplicateBindings(patternDescr.isUnification(), patternDescr, xpathStartDeclaration, pattern, patternDescr, "this", patternDescr.getIdentifier(), context);
        }
        return pattern;
    }

    public boolean isInternalFact(PatternDescr patternDescr, RuleBuildContext context) {
        return !(patternDescr.getSource() == null || patternDescr.getSource() instanceof EntryPointDescr ||
                (patternDescr.getSource() instanceof FromDescr) && context.getEntryPointId(((FromDescr) patternDescr.getSource()).getExpression()).isPresent());
    }

    private boolean isPassivePattern(PatternDescr patternDescr, RuleBuildContext context) {
        // when the source is a FromDesc also check that it isn't the datasource of a ruleunit
        return patternDescr.isQuery() || (patternDescr.getSource() instanceof FromDescr && context.getEntryPointId(( (FromDescr) patternDescr.getSource() ).getDataSource().getText()).isEmpty());
    }

    private void processClassObjectType(RuleBuildContext context, ObjectType objectType, Pattern pattern) {
        if (objectType instanceof ClassObjectType) {
            // make sure the Pattern is wired up to correct ClassObjectType and set as a target for rewiring
            context.getPkg().wireObjectType(objectType, pattern);
            Class<?> cls = ((ClassObjectType) objectType).getClassType();
            if (cls.getPackage() != null && !cls.getPackage().getName().equals("java.lang")) {
                // register the class in its own package unless it is primitive or belongs to java.lang
                TypeDeclaration typeDeclr = context.getKnowledgeBuilder().getAndRegisterTypeDeclaration(cls, cls.getPackage().getName());
                context.setTypesafe(typeDeclr == null || typeDeclr.isTypesafe());
            } else {
                context.setTypesafe(true);
            }
        }
    }

    private ObjectType getObjectType(RuleBuildContext context, PatternDescr patternDescr) {
        return getObjectType(context, patternDescr, patternDescr.getObjectType());
    }

    private ObjectType getObjectType(RuleBuildContext context, PatternDescr patternDescr, String objectType) {
        Prototype prototype = context.getPkg().getPrototype(objectType);
        if (prototype != null) {
            return new PrototypeObjectType(prototype);
        } else {
            try {
                final Class<?> userProvidedClass = context.getDialect().getTypeResolver().resolveType(objectType);
                if (!Modifier.isPublic(userProvidedClass.getModifiers())) {
                    registerDescrBuildError(context, patternDescr,
                                            "The class '" + objectType + "' is not public");
                    return null;
                }
                return new ClassObjectType(userProvidedClass, isEvent(context, userProvidedClass));
            } catch (final ClassNotFoundException e) {
                // swallow as we'll do another check in a moment and then record the problem
            }
        }
        return null;
    }

    private boolean isEvent(RuleBuildContext context, Class<?> userProvidedClass) {
        TypeDeclaration typeDeclaration = getTypeDeclaration(context, userProvidedClass);

        if (typeDeclaration != null) {
            return typeDeclaration.getRole() == Role.Type.EVENT;
        }
        Role role = userProvidedClass.getAnnotation(Role.class);
        return role != null && role.value() == Role.Type.EVENT;
    }

    private TypeDeclaration getTypeDeclaration(RuleBuildContext context, Class<?> userProvidedClass) {
        String packageName = ClassUtils.getPackage(userProvidedClass);
        TypeDeclarationContext kbuilder = context.getKnowledgeBuilder();
        PackageRegistry pkgr = kbuilder.getPackageRegistry(packageName);
        TypeDeclaration typeDeclaration = pkgr != null ? pkgr.getPackage().getTypeDeclaration(userProvidedClass) : null;
        if (typeDeclaration == null && kbuilder.getKnowledgeBase() != null) {
            // check if the type declaration is contained only in the already existing kbase (possible during incremental compilation)
            InternalKnowledgePackage pkg = kbuilder.getKnowledgeBase().getPackage(packageName);
            typeDeclaration = pkg != null ? pkg.getTypeDeclaration(userProvidedClass) : null;
        }
        if (typeDeclaration == null) {
            typeDeclaration = context.getPkg().getTypeDeclaration(userProvidedClass);
        }
        return typeDeclaration;
    }

    private void processSource(RuleBuildContext context, PatternDescr patternDescr, Pattern pattern) {
        if (patternDescr.getSource() != null) {
            // we have a pattern source, so build it
            RuleConditionBuilder builder = (RuleConditionBuilder) context.getDialect().getBuilder(patternDescr.getSource().getClass());
            pattern.setSource((PatternSource) builder.build(context, patternDescr.getSource(), pattern));
        }
    }

    private void processBehaviors(RuleBuildContext context, PatternDescr patternDescr, Pattern pattern) {
        for (BehaviorDescr behaviorDescr : patternDescr.getBehaviors()) {
            if (pattern.getObjectType().isEvent()) {
                BehaviorRuntime window = createWindow(behaviorDescr);
                if (window != null) {
                    pattern.addBehavior(window);
                    context.setNeedStreamMode();
                } else {
                    registerDescrBuildError(context, patternDescr,
                                            "Unknown window type '" + behaviorDescr.getSubType() + "'");
                }
            } else {
                // Some behaviors can only be assigned to patterns declared as events
                registerDescrBuildError(context, patternDescr,
                                        "A Sliding Window can only be assigned to types declared with @role( event ). The type '" + pattern.getObjectType() + "' in '" + context.getRule().getName()
                                                + "' is not declared as an Event.");
            }
        }
    }

    private BehaviorRuntime createWindow(BehaviorDescr behaviorDescr) {
        if (BehaviorRuntime.BehaviorType.TIME_WINDOW.matches(behaviorDescr.getSubType())) {
            return new SlidingTimeWindow(TimeUtils.parseTimeString(behaviorDescr.getParameters().get(0)));
        }
        if (BehaviorRuntime.BehaviorType.LENGTH_WINDOW.matches(behaviorDescr.getSubType())) {
            return new SlidingLengthWindow(Integer.parseInt(behaviorDescr.getParameters().get(0)));
        }
        return null;
    }

    private RuleConditionElement buildQuery(RuleBuildContext context, PatternDescr descr, PatternDescr patternDescr) {
        RuleConditionElement rce = null;
        // it might be a recursive query, so check for same names
        if (context.getRule().getName().equals(patternDescr.getObjectType())) {
            // it's a query so delegate to the QueryElementBuilder
            rce = buildQueryElement(context, descr, (QueryImpl) context.getRule());
        }

        if (rce == null) {
            // look up the query in the current package
            RuleImpl rule = context.getPkg().getRule(patternDescr.getObjectType());
            if (rule instanceof QueryImpl) {
                // it's a query so delegate to the QueryElementBuilder
                rce = buildQueryElement(context, descr, (QueryImpl) rule);
            }
        }

        if (rce == null) {
            // the query may have been imported, so try package imports
            for (String importName : context.getDialect().getTypeResolver().getImports()) {
                importName = importName.trim();
                int pos = importName.indexOf('*');
                if (pos >= 0) {
                    String pkgName = importName.substring(0,
                                                          pos - 1);
                    PackageRegistry pkgReg = context.getKnowledgeBuilder().getPackageRegistry(pkgName);
                    if (pkgReg != null) {
                        RuleImpl rule = pkgReg.getPackage().getRule(patternDescr.getObjectType());
                        if (rule instanceof QueryImpl) {
                            // it's a query so delegate to the QueryElementBuilder
                            rce = buildQueryElement(context, descr, (QueryImpl) rule);
                            break;
                        }
                    }
                }
            }
        }

        if (rce == null) {
            // this isn't a query either, so log an error
            registerDescrBuildError(context, patternDescr,
                                    "Unable to resolve ObjectType '" + patternDescr.getObjectType() + "'");
        }
        return rce;
    }

    private RuleConditionElement buildQueryElement(RuleBuildContext context, BaseDescr descr, QueryImpl rule) {
        if (context.getRule() != rule) {
            context.getRule().addUsedQuery(rule);
        }
        return QueryElementBuilder.getInstance().build(context, descr, rule);
    }

    private void processDuplicateBindings( boolean isUnification,
                                           PatternDescr patternDescr,
                                           Declaration xpathStartDeclaration,
                                           Pattern pattern,
                                           BaseDescr original,
                                           String leftExpression,
                                           String rightIdentifier,
                                           RuleBuildContext context) {

        if (isUnification) {
            // rewrite existing bindings into == constraints, so it unifies
            build(context, patternDescr, xpathStartDeclaration,
                  pattern, original, leftExpression + " == " + rightIdentifier);
        } else {
            // This declaration already exists, so throw an Exception
            registerDescrBuildError(context, patternDescr,
                                    "Duplicate declaration for variable '" + rightIdentifier + "' in the rule '" + context.getRule().getName() + "'");
        }
    }

    protected void processAnnotations(final RuleBuildContext context,
                                      final PatternDescr patternDescr,
                                      final Pattern pattern) {
        processListenedPropertiesAnnotation(context, patternDescr, pattern);
        processMetadataAnnotations(patternDescr, pattern, context.getDialect().getTypeResolver());
    }

    protected void processMetadataAnnotations(PatternDescr patternDescr, Pattern pattern, TypeResolver typeResolver) {
        for (AnnotationDescr ann : patternDescr.getAnnotations()) {
            String annFQN = ann.getFullyQualifiedName();
            if (!Watch.class.getCanonicalName().equals(annFQN)) {
                AnnotationDefinition def = buildAnnotationDef(ann, typeResolver);
                pattern.getAnnotations().put(annFQN, def);
            }
        }
    }

    private AnnotationDefinition buildAnnotationDef(AnnotationDescr annotationDescr, TypeResolver resolver) {
        try {
            return AnnotationDefinition.build(resolver.resolveType(annotationDescr.getFullyQualifiedName()),
                                              annotationDescr.getValueMap(),
                                              resolver);
        } catch (Exception e) {
            LOG.error("Exception", e);
            AnnotationDefinition annotationDefinition = new AnnotationDefinition(annotationDescr.getFullyQualifiedName());
            for (String propKey : annotationDescr.getValueMap().keySet()) {
                Object value = annotationDescr.getValue(propKey);
                if (value instanceof AnnotationDescr) {
                    value = buildAnnotationDef((AnnotationDescr) value, resolver);
                }
                annotationDefinition.getValues().put(propKey, new AnnotationDefinition.AnnotationPropertyVal(propKey, null, value, null));
            }
            return annotationDefinition;
        }
    }

    protected void processListenedPropertiesAnnotation(RuleBuildContext context, PatternDescr patternDescr, Pattern pattern) {
        String watchedValues = null;
        try {
            Watch watch = getTypedAnnotation(patternDescr, Watch.class);
            watchedValues = watch == null ? null : watch.value();
        } catch (Exception e) {
            registerDescrBuildError(context, patternDescr, e.getMessage());
        }

        if (watchedValues == null) {
            return;
        }

        Collection<String> settableProperties = getSettableProperties(context, patternDescr, pattern);

        List<String> listenedProperties = new ArrayList<>();
        for (String propertyName : watchedValues.split(",")) {
            propertyName = propertyName.trim();
            if (propertyName.equals("*") || propertyName.equals("!*")) {
                if (listenedProperties.contains("*") || listenedProperties.contains("!*")) {
                    registerDescrBuildError(context, patternDescr,
                                            "Duplicate usage of wildcard * in @" + Watch.class.getSimpleName() + " annotation");
                } else {
                    listenedProperties.add(propertyName);
                }
                continue;
            }
            boolean isNegative = propertyName.startsWith("!");
            propertyName = isNegative ? propertyName.substring(1).trim() : propertyName;
            if (settableProperties != null && !settableProperties.contains(propertyName)) {
                registerDescrBuildError(context, patternDescr,
                                        "Unknown property " + propertyName + " in @" + Watch.class.getSimpleName() + " annotation");
            } else if (listenedProperties.contains(propertyName) || listenedProperties.contains("!" + propertyName)) {
                registerDescrBuildError(context, patternDescr,
                                        "Duplicate property " + propertyName + " in @" + Watch.class.getSimpleName() + " annotation");
            } else {
                listenedProperties.add(isNegative ? "!" + propertyName : propertyName);
            }
        }

        pattern.addWatchedProperties(listenedProperties);
    }

    protected Collection<String> getSettableProperties(RuleBuildContext context, PatternDescr patternDescr, Pattern pattern) {
        ObjectType patternType = pattern.getObjectType();
        if (patternType.isPrototype()) {
            return ((PrototypeObjectType) patternType).getFieldNames();
        }

        Class<?> patternClass = ((ClassObjectType) patternType).getClassType();
        TypeDeclaration typeDeclaration = getTypeDeclaration(pattern, context);
        if (!typeDeclaration.isPropertyReactive()) {
            registerDescrBuildError(context, patternDescr,
                                    "Wrong usage of @" + Watch.class.getSimpleName() + " annotation on class " + patternClass.getName() + " that is not annotated as @PropertyReactive");
        }
        typeDeclaration.setTypeClass(patternClass);
        return typeDeclaration.getAccessibleProperties();
    }

    /**
     * Process all constraints and bindings on this pattern
     */
    private void processConstraintsAndBinds( RuleBuildContext context,
                                             PatternDescr patternDescr,
                                             Declaration xpathStartDeclaration,
                                             Pattern pattern) {

        DumperContext mvelCtx = new DumperContext().setRuleContext(context);
        for (BaseDescr b : patternDescr.getDescrs()) {
            String expression;
            boolean isPositional = false;
            if (b instanceof BindingDescr) {
                BindingDescr bind = (BindingDescr) b;
                expression = bind.getVariable() + (bind.isUnification() ? " := " : " : ") + bind.getExpression();
            } else if (b instanceof ExprConstraintDescr) {
                ExprConstraintDescr descr = (ExprConstraintDescr) b;
                expression = descr.getExpression();
                isPositional = descr.getType() == ExprConstraintDescr.Type.POSITIONAL;
            } else {
                expression = b.getText();
            }

            ConstraintConnectiveDescr result = parseExpression(context, patternDescr, b, expression);
            if (result == null) {
                return;
            }

            result.setNegated( b.isNegated() );
            isPositional &= !(result.getDescrs().size() == 1 && result.getDescrs().get(0) instanceof BindingDescr);

            if (isPositional) {
                processPositional(context, patternDescr, xpathStartDeclaration, pattern, (ExprConstraintDescr) b);
            } else {
                // need to build the actual constraint
                List<Constraint> constraints = build(context, patternDescr, xpathStartDeclaration, pattern, result, mvelCtx);
                pattern.addConstraints(constraints);
            }
        }

        TypeDeclaration typeDeclaration = getTypeDeclaration(pattern, context);
        if (typeDeclaration != null && typeDeclaration.isPropertyReactive()) {
            for (String field : lookAheadFieldsOfIdentifier(context.getRuleDescr(), patternDescr)) {
                addFieldToPatternWatchlist(pattern, typeDeclaration, field);
            }
        }
    }

    public static Collection<String> lookAheadFieldsOfIdentifier( RuleDescr ruleDescr, PatternDescr patternDescr ) {
        String identifier = patternDescr.getIdentifier();
        if (identifier == null) {
            return Collections.emptyList();
        }

        Collection<String> props = new HashSet<>();
        for (PatternDescr pattern : ruleDescr.getLhs().getAllPatternDescr()) {
            if (pattern == patternDescr) {
                continue;
            }
            if (pattern != null) {
                for (BaseDescr expr : pattern.getDescrs()) {
                    if (expr instanceof ExprConstraintDescr) {
                        String text = expr.getText();
                        int pos = text.indexOf( identifier + "." );
                        if ( pos == 0 || ( pos > 0 && !Character.isJavaIdentifierPart(text.charAt( pos-1 ))) ) {
                            String prop = StringUtils.extractFirstIdentifier(text, pos + identifier.length() + 1);
                            String propFromGetter = ClassUtils.getter2property(prop);
                            props.add(propFromGetter != null ? propFromGetter : StringUtils.lcFirst(prop));
                        }
                    }
                }
                if (isPassThroughPattern(pattern, identifier)) {
                    props.addAll(collectProps(pattern));
                }

            }
        }
        return props;
    }

    private static boolean isPassThroughPattern(PatternDescr pattern, String identifier) {
        // e.g. $p1 : Person()
        //      $p2 : Person(age > 10) from $p1
        // This use of "from" means the 2nd pattern is the same pattern as the 1st pattern.
        // We should add properties in the 2nd pattern to create a correct property reactivity mask.
        if (pattern.getSource() instanceof FromDescr) {
            FromDescr from = (FromDescr)pattern.getSource();
            String expr = from.getExpression().trim();
            return identifier.equals(expr);
        }
        return false;
    }

    private static Collection<String> collectProps(PatternDescr pattern) {
        Collection<String> props = new HashSet<>();
        for (BaseDescr expr : pattern.getDescrs()) {
            if (expr instanceof ExprConstraintDescr) {
                String text = expr.getText();
                String prop = StringUtils.extractFirstIdentifier(text, 0);
                String propFromGetter = ClassUtils.getter2property(prop);
                props.add(propFromGetter != null ? propFromGetter : StringUtils.lcFirst(prop));
            }
        }
        return props;
    }

    private void processPositional( RuleBuildContext context,
                                    PatternDescr patternDescr,
                                    Declaration xpathStartDeclaration,
                                    Pattern pattern,
                                    ExprConstraintDescr descr) {
        if (descr.getType() == ExprConstraintDescr.Type.POSITIONAL && pattern.getObjectType() instanceof ClassObjectType) {
            TypeDeclaration tDecl = context.getKnowledgeBuilder().getTypeDeclaration(pattern.getObjectType());

            if (tDecl == null) {
                registerDescrBuildError(context, patternDescr,
                                        "Unable to find @positional definitions for :" + pattern.getObjectType() + "\n");
                return;
            }

            ClassDefinition clsDef = tDecl.getTypeClassDef();
            if (clsDef == null) {
                registerDescrBuildError(context, patternDescr,
                                        "Unable to find @Positional field " + descr.getPosition() + " for class " + tDecl.getTypeName() + "\n");
                return;
            }

            FieldDefinition field = clsDef.getField(descr.getPosition());
            if (field == null) {
                registerDescrBuildError(context, patternDescr,
                                        "Unable to find @Positional field " + descr.getPosition() + " for class " + tDecl.getTypeName() + "\n");
                return;
            }

            String expr = descr.getExpression();
            boolean isSimpleIdentifier = isIdentifier(expr);

            if (isSimpleIdentifier) {
                // create a binding
                BindingDescr binder = new BindingDescr();
                binder.setUnification(true);
                binder.setExpression(field.getName());
                binder.setVariable(descr.getExpression());
                buildRuleBindings(context, patternDescr, xpathStartDeclaration, pattern, binder);
            } else {
                // create a constraint
                build(context, patternDescr, xpathStartDeclaration, pattern, descr, field.getName() + " == " + descr.getExpression());
            }
        }
    }

    private void build( RuleBuildContext context,
                        PatternDescr patternDescr,
                        Declaration xpathStartDeclaration,
                        Pattern pattern,
                        BaseDescr original,
                        String expr) {

        ConstraintConnectiveDescr result = parseExpression(context, patternDescr, original, expr);
        if (result == null) {
            return;
        }

        result.copyLocation(original);
        DumperContext mvelCtx = new DumperContext().setRuleContext(context);
        List<Constraint> constraints = build(context, patternDescr, xpathStartDeclaration, pattern, result, mvelCtx);
        pattern.addConstraints(constraints);
    }

    private List<Constraint> build( RuleBuildContext context,
                                    PatternDescr patternDescr,
                                    Declaration xpathStartDeclaration,
                                    Pattern pattern,
                                    ConstraintConnectiveDescr descr,
                                    DumperContext mvelCtx) {

        List<Constraint> constraints = new ArrayList<>();

        List<BaseDescr> initialDescrs = new ArrayList<>(descr.getDescrs());
        for (BaseDescr d : initialDescrs) {
            boolean isXPath = isXPathDescr(d);
            if (isXPath && pattern.hasXPath()) {
                registerDescrBuildError(context, patternDescr,
                                        "More than a single oopath constraint is not allowed in the same pattern");
                return constraints;
            }

            context.setXpathOffsetadjustment(xpathStartDeclaration == null ? 0 : -1);

            Constraint constraint = isXPath ?
                    buildXPathDescr(context, patternDescr, xpathStartDeclaration, pattern, (ExpressionDescr)  d, mvelCtx) :
                    buildCcdDescr(context, patternDescr, xpathStartDeclaration, pattern, d, descr, mvelCtx);
            if (constraint != null) {
                Declaration declCorrXpath = getDeclarationCorrespondingToXpath(pattern, isXPath, constraint);
                if (declCorrXpath == null) {
                    constraints.add(constraint);
                } else {
                    // A constraint is using a declration bound to an xpath in the same pattern
                    // Move the constraint inside the last chunk of the xpath defining this declaration, rewriting it as 'this'
                    Pattern modifiedPattern = pattern.clone();
                    modifiedPattern.setObjectType( new ClassObjectType( declCorrXpath.getDeclarationClass() ) );
                    constraint = buildCcdDescr(context, patternDescr, xpathStartDeclaration, modifiedPattern,
                                               d.replaceVariable(declCorrXpath.getBindingName(), "this"), descr, mvelCtx);
                    if (constraint != null) {
                        pattern.getXpathConstraint().getChunks().getLast().addConstraint(constraint);
                    }
                }
            }
        }

        if (descr.getDescrs().size() > initialDescrs.size()) {
            // The initial build process may have generated other constraint descrs.
            // This happens when null-safe references or inline-casts are used
            // These additional constraints must be built, and added as
            List<BaseDescr> additionalDescrs = new ArrayList<>(descr.getDescrs());
            additionalDescrs.removeAll(initialDescrs);

            if (!additionalDescrs.isEmpty()) {
                List<Constraint> additionalConstraints = new ArrayList<>();
                for (BaseDescr d : additionalDescrs) {
                    Constraint constraint = buildCcdDescr(context, patternDescr, xpathStartDeclaration, pattern, d, descr, mvelCtx);
                    if (constraint != null) {
                        additionalConstraints.add(constraint);
                    }
                }
                constraints.addAll(0, additionalConstraints);
            }
        }

        return constraints;
    }

    private Declaration getDeclarationCorrespondingToXpath(Pattern pattern, boolean isXPath, Constraint constraint) {
        if (!isXPath && pattern.hasXPath()) {
            Declaration xPathDecl = pattern.getXPathDeclaration();
            if (xPathDecl != null) {
                for (Declaration decl : constraint.getRequiredDeclarations()) {
                    if (xPathDecl.equals(decl)) {
                        return decl;
                    }
                }
            }
        }
        return null;
    }

    private boolean isXPathDescr(BaseDescr descr) {
        return descr instanceof ExpressionDescr &&
                (((ExpressionDescr) descr).getExpression().startsWith("/") ||
                        ((ExpressionDescr) descr).getExpression().startsWith("?/"));
    }

    private Constraint buildXPathDescr(RuleBuildContext context,
                                       PatternDescr patternDescr,
                                       Declaration xpathStartDeclaration,
                                       Pattern pattern,
                                       ExpressionDescr descr,
                                       DumperContext mvelCtx) {

        String expression = descr.getExpression();
        XpathAnalysis xpathAnalysis = XpathAnalysis.analyze(expression);

        if (xpathAnalysis.hasError()) {
            registerDescrBuildError(context, patternDescr,
                    "Invalid xpath expression '" + expression + "': " + xpathAnalysis.getError());
            return null;
        }

        XpathConstraint xpathConstraint = new XpathConstraint();
        ObjectType objectType = pattern.getObjectType();

        if (objectType.isPrototype()) {
            throw new UnsupportedOperationException("xpath is not supported with fact templates");
        }

        Class<?> patternClass = ((ClassObjectType) objectType).getClassType();

        List<Class<?>> backReferenceClasses = new ArrayList<>();
        backReferenceClasses.add(patternClass);
        XpathBackReference backRef = new XpathBackReference(pattern, backReferenceClasses);
        pattern.setBackRefDeclarations(backRef);

        ObjectType originalType = pattern.getObjectType();
        ObjectType currentObjectType = originalType;
        mvelCtx.setInXpath(true);

        try {
            // If the OOPath is inside of a pattern it needs no adjustment, if the pattern is synthetic (i.e. a OOPath from a var)
            // then the xpathoffset must be adjusted by -1, as pattern is not actually added to the rete network
            for (XpathAnalysis.XpathPart part : xpathAnalysis) {
                XpathConstraint.XpathChunk xpathChunk = xpathConstraint.addChunck(patternClass, part.getField(), part.getIndex(), part.isIterate(), part.isLazy());

                // make sure the Pattern is wired up to correct ClassObjectType and set as a target for rewiring
                context.getPkg().wireObjectType(currentObjectType, xpathChunk);

                if (xpathChunk == null) {
                    registerDescrBuildError(context, patternDescr,
                            "Invalid xpath expression '" + expression + "': cannot access " + part.getField() + " on " + patternClass);
                    pattern.setObjectType(originalType);
                    return null;
                }

                if (part.getInlineCast() != null) {
                    try {
                        patternClass = context.getDialect().getTypeResolver().resolveType(part.getInlineCast());
                    } catch (ClassNotFoundException e) {
                        registerDescrBuildError(context, patternDescr,
                                "Unknown class " + part.getInlineCast() + " in xpath expression '" + expression + "'");
                        return null;
                    }
                    part.addInlineCastConstraint(patternClass);
                    currentObjectType = getObjectType(context, patternDescr, patternClass.getName());
                    xpathChunk.setReturnedType(currentObjectType);
                } else {
                    patternClass = xpathChunk.getReturnedClass();
                    currentObjectType = getObjectType(context, patternDescr, patternClass.getName());
                }

                context.increaseXpathChuckNr();
                pattern.setObjectType(currentObjectType);
                backReferenceClasses.add(0, patternClass);
                backRef.reset();

                for (String constraint : part.getConstraints()) {
                    ConstraintConnectiveDescr result = parseExpression(context, patternDescr, new ExprConstraintDescr(constraint), constraint);
                    if (result == null) {
                        continue;
                    }

                    // preserving this, as the recursive build() resets it
                    int chunkNbr = context.getXpathChuckNr();
                    for (Constraint c : build(context, patternDescr, xpathStartDeclaration, pattern, result, mvelCtx)) {
                        xpathChunk.addConstraint(c);
                    }
                    context.setXpathChuckNr(chunkNbr);
                }
            }

            xpathConstraint.setXpathStartDeclaration(xpathStartDeclaration);
            if (descr instanceof BindingDescr) {
                Declaration pathDeclr = pattern.addDeclaration(((BindingDescr) descr).getVariable());
                pathDeclr.setxPathOffset(context.getXpathChuckNr());
                xpathConstraint.setDeclaration(pathDeclr);

            }
        } finally {
            mvelCtx.setInXpath(false);
            pattern.setBackRefDeclarations(null);
            pattern.setObjectType(originalType);
            context.resetXpathChuckNr();
        }

        return xpathConstraint;
    }

    private Constraint buildCcdDescr( RuleBuildContext context,
                                      PatternDescr patternDescr,
                                      Declaration xpathStartDeclaration,
                                      Pattern pattern,
                                      BaseDescr d,
                                      ConstraintConnectiveDescr ccd,
                                      DumperContext mvelCtx) {
        d.copyLocation(patternDescr);

        mvelCtx.clear();
        String expr = DescrDumper.getInstance().dump(d, ccd, mvelCtx);
        Map<String, OperatorDescr> aliases = mvelCtx.getAliases();

        // create bindings
        TypeDeclaration typeDeclaration = getTypeDeclaration(pattern, context);
        for (BindingDescr bind : mvelCtx.getBindings()) {
            buildRuleBindings(context, patternDescr, xpathStartDeclaration, pattern, bind, typeDeclaration);
        }

        if (expr.length() == 0) {
            return null;
        }

        // check if it is an atomic expression
        Constraint constraint = processAtomicExpression(context, pattern, d, expr, aliases);
        // otherwise check if it is a simple expression
        return constraint != null ? constraint : buildExpression(context, pattern, d, expr, aliases, ccd.isNegated());
    }

    private Constraint buildExpression(final RuleBuildContext context,
                                       final Pattern pattern,
                                       final BaseDescr d,
                                       final String expr,
                                       final Map<String, OperatorDescr> aliases,
                                       boolean negated) {
        if ("_.neg".equals(expr)) {
            pattern.setHasNegativeConstraint(true);
            return new NegConstraint();
        } else if ("!_.neg".equals(expr)) {
            pattern.setHasNegativeConstraint(true);
            return new NegConstraint(false);
        }

        RelationalExprDescr relDescr = d instanceof RelationalExprDescr ? (RelationalExprDescr) d : null;
        boolean simple = isSimpleExpr(relDescr);

        if (simple && // simple means also relDescr is != null
                !ClassObjectType.Map_ObjectType.isAssignableFrom(pattern.getObjectType()) &&
                !ClassObjectType.Match_ObjectType.isAssignableFrom(pattern.getObjectType())) {
            String normalizedExpr = normalizeExpression(context, pattern, relDescr, expr);
            if (negated) {
                normalizedExpr = normalizeNegatedExpr(normalizedExpr, relDescr.getOperator());
                relDescr.getOperatorDescr().setNegated( !relDescr.getOperatorDescr().isNegated() );
            }
            return buildRelationalExpression(context, pattern, relDescr, normalizedExpr, aliases);
        }

        // Either it's a complex expression, so do as predicate
        // Or it's a Map and we have to treat it as a special case

        String rewrittenExpr = rewriteOrExpressions(context, pattern, d, expr);
        if (simple) { // simple means also relDescr is != null
            rewrittenExpr = ConstraintUtil.inverseExpression(relDescr, expr, findLeftExpressionValue(relDescr), findRightExpressionValue(relDescr), relDescr.getOperator(), pattern);
        }
        if (negated) {
            rewrittenExpr = "!(" + rewrittenExpr + ")";
        }
        return createAndBuildPredicate(context, pattern, d, rewrittenExpr, aliases);
    }

    private String normalizeNegatedExpr(String expr, String operator) {
        ConstraintTypeOperator constraintType = ConstraintTypeOperator.decode(operator);
        return constraintType.getOperator() != null ?
                expr.replace( constraintType.getOperator(), constraintType.negate().getOperator() ) :
                "!(" + expr + ")";
    }

    private String rewriteOrExpressions(RuleBuildContext context, Pattern pattern, BaseDescr d, String expr) {
        if (d instanceof ConstraintConnectiveDescr && ((ConstraintConnectiveDescr) d).getConnective() == ConnectiveType.OR) {
            String rewrittenExpr = rewriteCompositeExpressions(context, pattern, (ConstraintConnectiveDescr) d);
            if (rewrittenExpr != null) {
                return rewrittenExpr;
            }
        }
        return expr;
    }

    private String rewriteCompositeExpressions(RuleBuildContext context, Pattern pattern, ConstraintConnectiveDescr d) {
        int i = 0;
        StringBuilder sb = new StringBuilder();
        for (BaseDescr subDescr : d.getDescrs()) {
            if (subDescr instanceof BindingDescr) {
                continue;
            }
            if (i++ > 0) {
                sb.append(" ").append(d.getConnective().getConnective()).append(" ");
            }

            String normalizedExpr;
            if (subDescr instanceof RelationalExprDescr && isSimpleExpr((RelationalExprDescr) subDescr)) {
                RelationalExprDescr relDescr = (RelationalExprDescr) subDescr;
                if (relDescr.getExpression() != null) {
                    normalizedExpr = normalizeExpression(context, pattern, relDescr, relDescr.getExpression());
                } else {
                    i--;
                    normalizedExpr = "";
                }
            } else if (subDescr instanceof ConstraintConnectiveDescr) {
                String rewrittenExpr = rewriteCompositeExpressions(context, pattern, (ConstraintConnectiveDescr) subDescr);
                if (rewrittenExpr == null) {
                    return null;
                }
                normalizedExpr = "(" + rewrittenExpr + ")";
            } else if (subDescr instanceof AtomicExprDescr) {
                normalizedExpr = ((AtomicExprDescr) subDescr).getRewrittenExpression();
            } else {
                return null;
            }
            sb.append(normalizedExpr);
        }

        return sb.toString();
    }

    private String normalizeExpression(RuleBuildContext context, Pattern pattern, RelationalExprDescr subDescr, String subExpr) {
        String leftValue = findLeftExpressionValue(subDescr);
        String rightValue = findRightExpressionValue(subDescr);
        String operator = subDescr.getOperator();

        subExpr = ConstraintUtil.inverseExpression(subDescr, subExpr, leftValue, rightValue, operator, pattern);

        ValueType valueType = getValueType(context, pattern, leftValue);
        if (valueType != null && valueType.isDate()) {
            FieldValue fieldValue = ConstraintBuilder.get().getMvelFieldValue(context, valueType, rightValue);
            if (fieldValue != null) {
                subExpr = subExpr.replace(rightValue, getNormalizeDate(valueType, fieldValue));
            }
            return subExpr;
        }

        if (operator.equals("str")) {
            return normalizeStringOperator(leftValue, rightValue, new LiteralRestrictionDescr(operator,
                                                                                              subDescr.isNegated(),
                                                                                              subDescr.getParameters(),
                                                                                              rightValue,
                                                                                              LiteralRestrictionDescr.TYPE_STRING));
        }

        // resolve ambiguity between mvel's "empty" keyword and constraints like: List(empty == ...)
        return normalizeEmptyKeyword(subExpr, operator);
    }

    private ValueType getValueType(RuleBuildContext context, Pattern pattern, String leftValue) {
        Declaration declaration = pattern.getDeclarations().get(leftValue);
        if (declaration != null && declaration.getExtractor() != null) {
            return declaration.getValueType();
        }

        ObjectType objectType = pattern.getObjectType();
        if (objectType.isPrototype()) {
            return ValueType.determineValueType( ( (PrototypeObjectType) objectType).getPrototype().getField(leftValue).getType() );
        }

        Class<?> clazz = ((ClassObjectType) objectType).getClassType();
        Class<?> fieldType = context.getPkg().getFieldType(clazz, leftValue);
        return fieldType != null ? ValueType.determineValueType(fieldType) : null;
    }

    protected Constraint buildRelationalExpression(final RuleBuildContext context,
                                                   final Pattern pattern,
                                                   final RelationalExprDescr relDescr,
                                                   final String expr,
                                                   final Map<String, OperatorDescr> aliases) {
        String[] values = new String[2];
        boolean usesThisRef = findExpressionValues(relDescr, values);

        ExprBindings value1Expr = getExprBindings(context, pattern, values[0]);
        ExprBindings value2Expr = getExprBindings(context, pattern, values[1]);

        // build a predicate if it is a constant expression or at least has a constant on the left side
        // or as a fallback when the building of a constraint fails
        if (!usesThisRef && value1Expr.isConstant()) {
            return createAndBuildPredicate(context, pattern, relDescr, expr, aliases);
        }

        Constraint constraint = buildConstraintForPattern(context, pattern, relDescr, expr, values[0], values[1], value2Expr.isConstant(), aliases);
        return constraint != null ? constraint : createAndBuildPredicate(context, pattern, relDescr, expr, aliases);
    }

    private ExprBindings getExprBindings(RuleBuildContext context, Pattern pattern, String value) {
        ExprBindings valueExpr = new ExprBindings();
        ConstraintBuilder.get().setExprInputs( context, valueExpr,
                                               (pattern.getObjectType() instanceof ClassObjectType) ?
                                                       ((ClassObjectType) pattern.getObjectType()).getClassType() :
                                                       Prototype.class,
                                               value);
        if (!isIdentifierLiteral(value)) {
            String identifier = StringUtils.extractFirstIdentifier(value, 0);
            if (identifier.length() > 0) {
                valueExpr.setWithJavaIdentifier(true);
            }
        }
        return valueExpr;
    }

    private boolean isIdentifierLiteral(String expr) {
        return expr.equals("true") || expr.equals("false") || expr.equals("null") || expr.endsWith(".class");
    }

    private boolean findExpressionValues(RelationalExprDescr relDescr, String[] values) {
        values[0] = findLeftExpressionValue(relDescr);
        values[1] = findRightExpressionValue(relDescr);

        return "this".equals(values[1]) || values[1].startsWith("this.") || values[1].contains(")this).") ||
                "this".equals(values[0]) || values[0].startsWith("this.") || values[0].contains(")this).");
    }

    private String findLeftExpressionValue(RelationalExprDescr relDescr) {
        return relDescr.getLeft() instanceof AtomicExprDescr ?
                ((AtomicExprDescr) relDescr.getLeft()).getRewrittenExpression() :
                ((BindingDescr) relDescr.getLeft()).getExpression();
    }

    private String findRightExpressionValue(RelationalExprDescr relDescr) {
        return relDescr.getRight() instanceof AtomicExprDescr ?
                ((AtomicExprDescr) relDescr.getRight()).getRewrittenExpression().trim() :
                ((BindingDescr) relDescr.getRight()).getExpression().trim();
    }

    protected Constraint buildConstraintForPattern(final RuleBuildContext context,
                                                   final Pattern pattern,
                                                   final RelationalExprDescr relDescr,
                                                   String expr,
                                                   String value1,
                                                   String value2,
                                                   boolean isConstant,
                                                   Map<String, OperatorDescr> aliases) {

        ReadAccessor extractor = getFieldReadAccessor(context, relDescr, pattern, value1, null, true);
        if (extractor == null) {
            return null;
        }

        if ("contains".equals( relDescr.getOperator() ) && !isTypeCompatibleWithContainsOperator( extractor.getExtractToClass() )) {
            registerDescrBuildError(context, relDescr, "Cannot use contains on " + extractor.getExtractToClass() + " in expression '" + expr + "'");
            return null;
        }

        int dotPos = value1.indexOf('.');
        if (dotPos > 0) {
            String part0 = value1.substring(0, dotPos).trim();
            if ("this".equals(part0.trim())) {
                value1 = value1.substring(dotPos + 1);
            } else if (pattern.getDeclaration() != null && part0.equals(pattern.getDeclaration().getIdentifier())) {
                value1 = value1.substring(dotPos + 1);
                expr = expr.substring(dotPos + 1);
            }
        }

        LiteralRestrictionDescr restrictionDescr = buildLiteralRestrictionDescr(context, relDescr, value2, isConstant);

        if (restrictionDescr != null) {
            ValueType vtype = extractor.getValueType();
            FieldValue field = ConstraintBuilder.get().getMvelFieldValue(context, vtype, restrictionDescr.getText().trim());
            if (field != null) {
                Constraint constraint = getConstraintBuilder()
                        .buildLiteralConstraint(context, pattern, vtype, field, expr,
                                value1, relDescr.getOperator(), relDescr.isNegated(), value2,
                                extractor, restrictionDescr, aliases);
                if (constraint != null) {
                    return constraint;
                }
            }
        }

        value2 = context.getDeclarationResolver().normalizeValueForUnit(value2);

        Declaration declr = null;
        if (value2.indexOf('(') < 0 && value2.indexOf('.') < 0 && value2.indexOf('[') < 0) {
            declr = context.getDeclarationResolver().getDeclaration(value2);

            if (declr == null) {
                // trying to create implicit declaration
                final Pattern thisPattern = (Pattern) context.getDeclarationResolver().peekBuildStack();
                declr = createDeclarationObject(context, value2, thisPattern);
            }
        }

        Declaration[] declarations = null;
        if (declr == null) {
            String[] parts = value2.split("\\.");
            if (parts.length == 2) {
                if ("this".equals(parts[0].trim())) {
                    declr = createDeclarationObject(context, parts[1].trim(), (Pattern) context.getDeclarationResolver().peekBuildStack());
                    value2 = parts[1].trim();
                } else {
                    declr = context.getDeclarationResolver().getDeclaration(parts[0].trim());
                    // if a declaration exists, then it may be a variable direct property access
                    if (declr != null) {
                        if (declr.isPatternDeclaration()) {
                            // TODO: no need to extract inner declaration when using mvel constraint
                            declarations = new Declaration[]{declr};
                            declr = createDeclarationObject(context, parts[1].trim(), declr.getPattern());
                            value2 = parts[1].trim();
                        } else {
                            // we will later fallback to regular predicates, so don't raise error
                            return null;
                        }
                    }
                }
            }
        }

        if (declarations == null) {
            if (declr != null) {
                declarations = new Declaration[]{declr};
            } else {
                declarations = getDeclarationsForReturnValue(context, relDescr, value2);
                if (declarations == null) {
                    return null;
                }
            }
        }

        return getConstraintBuilder().buildVariableConstraint(context, pattern, expr, declarations, value1, relDescr.getOperatorDescr(), value2, extractor, declr, relDescr, aliases);
    }

    private boolean isTypeCompatibleWithContainsOperator(Class<?> type) {
        return Collection.class.isAssignableFrom( type ) || type.isArray() || type == String.class;
    }

    private Declaration[] getDeclarationsForReturnValue(RuleBuildContext context, RelationalExprDescr relDescr, String value2) {
        Pattern pattern = (Pattern) context.getDeclarationResolver().peekBuildStack();
        ReturnValueRestrictionDescr returnValueRestrictionDescr = new ReturnValueRestrictionDescr(relDescr.getOperator(), relDescr, value2);

        AnalysisResult analysis = context.getDialect().analyzeExpression(context,
                                                                         returnValueRestrictionDescr,
                                                                         returnValueRestrictionDescr.getContent(),
                                                                         new BoundIdentifiers(pattern, context, null, pattern.getObjectType()));
        if (analysis == null) {
            // something bad happened
            return null;
        }
        final BoundIdentifiers usedIdentifiers = analysis.getBoundIdentifiers();

        final List<Declaration> tupleDeclarations = new ArrayList<>();
        final List<Declaration> factDeclarations = new ArrayList<>();
        for (String id : usedIdentifiers.getDeclrClasses().keySet()) {
            Declaration decl = context.getDeclarationResolver().getDeclaration(id);
            if (decl.getPattern() == pattern) {
                factDeclarations.add(decl);
            } else {
                tupleDeclarations.add(decl);
            }
        }
        createImplicitBindings(context, pattern, analysis.getNotBoundedIdentifiers(), usedIdentifiers, factDeclarations);

        final Declaration[] previousDeclarations = tupleDeclarations.toArray(new Declaration[tupleDeclarations.size()]);
        final Declaration[] localDeclarations = factDeclarations.toArray(new Declaration[factDeclarations.size()]);

        Arrays.sort(previousDeclarations, SortDeclarations.instance);
        Arrays.sort(localDeclarations, SortDeclarations.instance);

        final String[] requiredGlobals = usedIdentifiers.getGlobals().keySet().toArray(new String[usedIdentifiers.getGlobals().size()]);

        Declaration[] requiredDeclarations = new Declaration[previousDeclarations.length + localDeclarations.length];
        System.arraycopy(previousDeclarations,
                         0,
                         requiredDeclarations,
                         0,
                         previousDeclarations.length);
        System.arraycopy(localDeclarations,
                         0,
                         requiredDeclarations,
                         previousDeclarations.length,
                         localDeclarations.length);

        Declaration[] declarations = new Declaration[requiredDeclarations.length + requiredGlobals.length];
        int i = 0;
        for (Declaration requiredDeclaration : requiredDeclarations) {
            declarations[i++] = requiredDeclaration;
        }
        for (String requiredGlobal : requiredGlobals) {
            declarations[i++] = context.getDeclarationResolver().getDeclaration(requiredGlobal);
        }
        return declarations;
    }

    protected LiteralRestrictionDescr buildLiteralRestrictionDescr(RuleBuildContext context,
                                                                   RelationalExprDescr exprDescr,
                                                                   String rightValue,
                                                                   boolean isRightLiteral) {
        // is it a literal? Does not include enums
        if (isRightLiteral) {
            return new LiteralRestrictionDescr(exprDescr.getOperator(), exprDescr.isNegated(), exprDescr.getParameters(), rightValue, LiteralRestrictionDescr.TYPE_STRING);
        }

        // is it an enum or final?
        int dotPos = rightValue.lastIndexOf('.');
        if (dotPos >= 0) {
            final String mainPart = rightValue.substring(0,
                                                         dotPos);
            String lastPart = rightValue.substring(dotPos + 1);
            try {
                Class<?> clazz = context.getDialect().getTypeResolver().resolveType(mainPart);
                if (lastPart.indexOf('(') < 0 && lastPart.indexOf('.') < 0 && lastPart.indexOf('[') < 0) {
                    Field field = ClassUtils.getField(clazz, lastPart);
                    if (field != null && (field.isEnumConstant() || Modifier.isFinal(field.getModifiers()))) {
                        return new LiteralRestrictionDescr(exprDescr.getOperator(), exprDescr.isNegated(), exprDescr.getParameters(), rightValue, LiteralRestrictionDescr.TYPE_STRING);
                    }
                }
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                // do nothing as this is just probing to see if it was a class, which we now know it isn't :)
            }
        }

        return null;
    }

    protected Constraint processAtomicExpression(RuleBuildContext context,
                                                 Pattern pattern,
                                                 BaseDescr d,
                                                 String expr,
                                                 Map<String, OperatorDescr> aliases) {
        if (d instanceof AtomicExprDescr) {
            Matcher m = evalRegexp.matcher(((AtomicExprDescr) d).getExpression());
            if (m.find()) {
                // MVELDumper already stripped the eval
                // this will build the eval using the specified dialect
                PredicateDescr pdescr = new PredicateDescr(context.getRuleDescr().getResource(), expr);
                pdescr.copyLocation(d);
                return buildEval(context, pattern, pdescr, aliases, expr, true);
            }
        }
        return null;
    }

    protected boolean isSimpleExpr(final RelationalExprDescr relDescr) {
        boolean simple = false;
        if (relDescr != null) {
            if ((relDescr.getLeft() instanceof AtomicExprDescr || relDescr.getLeft() instanceof BindingDescr) &&
                    (relDescr.getRight() instanceof AtomicExprDescr || relDescr.getRight() instanceof BindingDescr)) {
                simple = true;
            }
        }
        return simple;
    }

    protected Constraint createAndBuildPredicate(RuleBuildContext context,
                                                 Pattern pattern,
                                                 BaseDescr base,
                                                 String expr,
                                                 Map<String, OperatorDescr> aliases) {
        Dialect dialect = context.getDialect();
        context.setDialect(context.getDialect("mvel"));

        PredicateDescr pdescr = new PredicateDescr(context.getRuleDescr().getResource(), expr);
        pdescr.copyParameters(base);
        pdescr.copyLocation(base);
        Constraint evalConstraint = buildEval(context, pattern, pdescr, aliases, expr, false);

        // fall back to original dialect
        context.setDialect(dialect);
        return evalConstraint;
    }

    public static class ExprBindings {

        protected Set<String> globalBindings;
        protected Set<String> ruleBindings;
        protected Set<String> fieldAccessors;
        protected boolean withJavaIdentifier;

        public ExprBindings() {
            this.globalBindings = new HashSet<>();
            this.ruleBindings = new HashSet<>();
            this.fieldAccessors = new HashSet<>();
            this.withJavaIdentifier = false;
        }

        public Set<String> getGlobalBindings() {
            return globalBindings;
        }

        public Set<String> getRuleBindings() {
            return ruleBindings;
        }

        public Set<String> getFieldAccessors() {
            return fieldAccessors;
        }

        public boolean isWithJavaIdentifier() {
            return withJavaIdentifier;
        }

        public void setWithJavaIdentifier(boolean withJavaIdentifier) {
            this.withJavaIdentifier = withJavaIdentifier;
        }

        public boolean isConstant() {
            return !withJavaIdentifier && this.globalBindings.isEmpty() && this.ruleBindings.isEmpty() && this.fieldAccessors.size() <= 1; // field accessors might contain the "this" reference
        }
    }

    protected void buildRuleBindings(RuleBuildContext context,
                                     PatternDescr patternDescr,
                                     Declaration xpathStartDeclaration,
                                     Pattern pattern,
                                     BindingDescr fieldBindingDescr) {
        buildRuleBindings(context, patternDescr, xpathStartDeclaration, pattern, fieldBindingDescr, getTypeDeclaration(pattern, context));
    }

    protected void buildRuleBindings(RuleBuildContext context,
                                     PatternDescr patternDescr,
                                     Declaration xpathStartDeclaration,
                                     Pattern pattern,
                                     BindingDescr fieldBindingDescr,
                                     TypeDeclaration typeDeclaration) {

        if (context.getDeclarationResolver().isDuplicated(context.getRule(),
                                                          fieldBindingDescr.getVariable(),
                                                          null)) {
            processDuplicateBindings(fieldBindingDescr.isUnification(),
                                     patternDescr,
                                     xpathStartDeclaration,
                                     pattern,
                                     fieldBindingDescr,
                                     fieldBindingDescr.getBindingField(),
                                     fieldBindingDescr.getVariable(),
                                     context);
            if (fieldBindingDescr.isUnification()) {
                return;
            }
        }

        Declaration declr = pattern.addDeclaration(fieldBindingDescr.getVariable());
        if (context.isInXpath()) {
            declr.setxPathOffset( context.getXpathChuckNr() );
        }

        ReadAccessor extractor = getFieldReadAccessor(context, fieldBindingDescr, pattern, fieldBindingDescr.getBindingField(), declr, true);

        if (extractor == null) {
            registerDescrBuildError(context, patternDescr,
                                    "Field Reader does not exist for declaration '" + fieldBindingDescr.getVariable() + "' in '" + fieldBindingDescr + "' in the rule '" + context.getRule().getName() + "'");
            return;
        }

        declr.setReadAccessor(extractor);

        if (!declr.isFromXpathChunk() && typeDeclaration != null) {
            if (extractor instanceof FieldNameSupplier) {
                addFieldToPatternWatchlist(pattern, typeDeclaration, ((FieldNameSupplier) extractor).getFieldName());
            } else if (doesFirstPropHaveListMapAccessor(fieldBindingDescr.getBindingField())) {
                addFieldToPatternWatchlist(pattern, typeDeclaration, extractFirstIdentifier(fieldBindingDescr.getBindingField(), 0)); // extractor is MVELObjectClassFieldReader
            }
        }
    }

    private void addFieldToPatternWatchlist(Pattern pattern, TypeDeclaration typeDeclaration, String fieldName) {
        if (typeDeclaration.getAccessibleProperties().contains(fieldName)) {
            pattern.addBoundProperty( fieldName );
        }
    }

    private TypeDeclaration getTypeDeclaration(Pattern pattern, RuleBuildContext context) {
        return context.getKnowledgeBuilder().getTypeDeclaration( pattern.getObjectType() );
    }

    protected Constraint buildEval(final RuleBuildContext context,
                                   final Pattern pattern,
                                   final PredicateDescr predicateDescr,
                                   final Map<String, OperatorDescr> aliases,
                                   final String expr,
                                   final boolean isEvalExpression) {

        AnalysisResult analysis = buildAnalysis(context, pattern, predicateDescr, aliases);

        if (analysis == null) {
            // something bad happened
            return null;
        }

        Declaration[][] usedDeclarations = getUsedDeclarations(context, pattern, analysis);
        Declaration[] previousDeclarations = usedDeclarations[0];
        Declaration[] localDeclarations = usedDeclarations[1];

        BoundIdentifiers usedIdentifiers = analysis.getBoundIdentifiers();

        Arrays.sort(previousDeclarations, SortDeclarations.instance);
        Arrays.sort(localDeclarations, SortDeclarations.instance);

        String[] requiredGlobals = usedIdentifiers.getGlobals().keySet().toArray(new String[usedIdentifiers.getGlobals().size()]);
        Declaration[] mvelDeclarations = new Declaration[previousDeclarations.length + localDeclarations.length + requiredGlobals.length];
        int i = 0;
        for (Declaration d : previousDeclarations) {
            mvelDeclarations[i++] = d;
        }
        for (Declaration d : localDeclarations) {
            mvelDeclarations[i++] = d;
        }
        for (String global : requiredGlobals) {
            mvelDeclarations[i++] = context.getDeclarationResolver().getDeclaration(global);
        }

        boolean isDynamic = pattern.getObjectType().isPrototype() ||
                ( !((ClassObjectType) pattern.getObjectType()).getClassType().isArray() &&
                        !context.getKnowledgeBuilder().getTypeDeclaration(pattern.getObjectType()).isTypesafe() );

        return getConstraintBuilder().buildMvelConstraint(context.getPkg().getName(), expr, mvelDeclarations, getOperators(usedIdentifiers.getOperators()),
                context, previousDeclarations, localDeclarations, predicateDescr, analysis, isDynamic);
    }

    public static EvaluatorWrapper[] getOperators(Map<String, EvaluatorWrapper> operatorMap) {
        EvaluatorWrapper[] operators = new EvaluatorWrapper[operatorMap.size()];
        int i = 0;
        for (Map.Entry<String, EvaluatorWrapper> entry : operatorMap.entrySet()) {
            EvaluatorWrapper evaluator = entry.getValue();
            evaluator.setBindingName(entry.getKey());
            operators[i++] = evaluator;
        }
        return operators;
    }

    public static Declaration[][] getUsedDeclarations(RuleBuildContext context, Pattern pattern, AnalysisResult analysis) {
        BoundIdentifiers usedIdentifiers = analysis.getBoundIdentifiers();
        final List<Declaration> tupleDeclarations = new ArrayList<>();
        final List<Declaration> factDeclarations = new ArrayList<>();

        for (String id : usedIdentifiers.getDeclrClasses().keySet()) {
            Declaration decl = context.getDeclarationResolver().getDeclaration(id);
            if (decl.getPattern() == pattern) {
                factDeclarations.add(decl);
            } else {
                tupleDeclarations.add(decl);
            }
        }

        createImplicitBindings(context,
                               pattern,
                               analysis.getNotBoundedIdentifiers(),
                               analysis.getBoundIdentifiers(),
                               factDeclarations);

        Declaration[][] usedDeclarations = new Declaration[2][];
        usedDeclarations[0] = tupleDeclarations.toArray(new Declaration[tupleDeclarations.size()]);
        usedDeclarations[1] = factDeclarations.toArray(new Declaration[factDeclarations.size()]);
        return usedDeclarations;
    }

    public static AnalysisResult buildAnalysis(RuleBuildContext context, Pattern pattern, PredicateDescr predicateDescr, Map<String, OperatorDescr> aliases) {
        Map<String, EvaluatorWrapper> operators = aliases == null ? new HashMap<>() : buildOperators(context, pattern, predicateDescr, aliases);

        return context.getDialect().analyzeExpression(context,
                                                      predicateDescr,
                                                      predicateDescr.getContent(),
                                                      new BoundIdentifiers(pattern,
                                                                           context,
                                                                           operators,
                                                                           pattern.getObjectType()));
    }

    public static Map<String, EvaluatorWrapper> buildOperators(RuleBuildContext context,
                                                                  Pattern pattern,
                                                                  BaseDescr predicateDescr,
                                                                  Map<String, OperatorDescr> aliases) {
        if (aliases.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, EvaluatorWrapper> operators = new HashMap<>();
        for (Map.Entry<String, OperatorDescr> entry : aliases.entrySet()) {
            OperatorDescr op = entry.getValue();

            Declaration leftDecl = createDeclarationForOperator(context, pattern, op.getLeftString());
            Declaration rightDecl = createDeclarationForOperator(context, pattern, op.getRightString());

            Target left = leftDecl != null && leftDecl.isPatternDeclaration() ? Target.HANDLE : Target.FACT;
            Target right = rightDecl != null && rightDecl.isPatternDeclaration() ? Target.HANDLE : Target.FACT;

            op.setLeftIsHandle(left == Target.HANDLE);
            op.setRightIsHandle(right == Target.HANDLE);

            Evaluator evaluator = getConstraintBuilder().getEvaluator(context,
                                                                      predicateDescr,
                                                                      ValueType.OBJECT_TYPE,
                                                                      op.getOperator(),
                                                                      false, // the rewrite takes care of negation
                                                                      op.getParametersText(),
                                                                      left,
                                                                      right);

            EvaluatorWrapper wrapper = getConstraintBuilder().wrapEvaluator(evaluator, leftDecl, rightDecl);
            operators.put(entry.getKey(), wrapper);
        }
        return operators;
    }

    private static Declaration createDeclarationForOperator(RuleBuildContext context, Pattern pattern, String expr) {
        Declaration declaration;
        int dotPos = expr.indexOf('.');
        if (dotPos < 0) {
            if (!isIdentifier(expr)) {
                return null;
            }
            declaration = context.getDeclarationResolver().getDeclaration(expr);
            if (declaration == null) {
                if ("this".equals(expr)) {
                    declaration = createDeclarationObject(context, "this", pattern);
                } else {
                    declaration = new Declaration("this", pattern);
                    context.getPkg().getReader( pattern.getObjectType().getClassName(), expr, declaration );
                }
            }
        } else {
            String part1 = expr.substring(0, dotPos).trim();
            String part2 = expr.substring(dotPos + 1).trim();
            if ("this".equals(part1)) {
                declaration = createDeclarationObject(context, part2, (Pattern) context.getDeclarationResolver().peekBuildStack());
            } else {
                declaration = context.getDeclarationResolver().getDeclaration(part1);
                // if a declaration exists, then it may be a variable direct property access
                if (declaration != null) {
                    declaration = createDeclarationObject(context, part2, declaration.getPattern());
                }
            }
        }
        return declaration;
    }

    private static ConstraintBuilder getConstraintBuilder() {
        return ConstraintBuilder.get();
    }

    public static void createImplicitBindings(final RuleBuildContext context,
                                              final Pattern pattern,
                                              final Set<String> unboundIdentifiers,
                                              final BoundIdentifiers boundIdentifiers,
                                              final List<Declaration> factDeclarations) {
        for (Iterator<String> it = unboundIdentifiers.iterator(); it.hasNext(); ) {
            String identifier = it.next();
            Declaration declaration = createDeclarationObject(context,
                                                              identifier,
                                                              pattern);
            // the name may not be a local field, such as enums
            // maybe should have a safer way to detect this, as other issues may cause null too 
            // that we would need to know about
            if (declaration != null) {
                factDeclarations.add(declaration);
                // implicit bindings need to be added to "local" declarations, as they are nolonger unbound
                boundIdentifiers.getDeclrClasses().put(identifier,
                                                       declaration.getDeclarationClass());
                it.remove();
            }
        }
    }

    /**
     * Creates a declaration object for the field identified by the given identifier
     * on the give pattern object
     */
    protected static Declaration createDeclarationObject(final RuleBuildContext context,
                                                         final String identifier,
                                                         final Pattern pattern) {
        return createDeclarationObject(context, identifier, identifier, pattern);
    }

    protected static Declaration createDeclarationObject(final RuleBuildContext context,
                                                         final String identifier,
                                                         final String expr,
                                                         final Pattern pattern) {
        final BindingDescr implicitBinding = new BindingDescr(identifier, expr);

        final Declaration declaration = new Declaration(identifier,
                                                        null,
                                                        pattern,
                                                        true);

        ReadAccessor extractor = getFieldReadAccessor(context, implicitBinding, pattern, implicitBinding.getExpression(), declaration, false);

        if (extractor == null) {
            return null;
        }

        declaration.setReadAccessor(extractor);

        return declaration;
    }

    public static void registerReadAccessor(final RuleBuildContext context,
                                            final ObjectType objectType,
                                            final String fieldName,
                                            final AcceptsReadAccessor target) {
        if (!ValueType.PROTOTYPE_TYPE.equals(objectType.getValueType())) {
            context.getPkg().getReader(objectType.getClassName(), fieldName, target);
        }
    }

    public static ReadAccessor getFieldReadAccessor(final RuleBuildContext context,
                                                            final BaseDescr descr,
                                                            final Pattern pattern,
                                                            String fieldName,
                                                            final AcceptsReadAccessor target,
                                                            final boolean reportError) {
        return getFieldReadAccessor(context, descr, pattern, pattern.getObjectType(), fieldName, target, reportError);
    }

    public static ReadAccessor getFieldReadAccessor(final RuleBuildContext context,
                                                            final BaseDescr descr,
                                                            final Pattern pattern,
                                                            final ObjectType objectType,
                                                            String fieldName,
                                                            final AcceptsReadAccessor target,
                                                            final boolean reportError) {
        // reportError is needed as some times failure to build accessor is not a failure, just an indication that building is not possible so try something else.
        ReadAccessor reader;

        if (ValueType.PROTOTYPE_TYPE.equals(objectType.getValueType())) {
            //@todo use accessor cache            
            reader = new PrototypeFieldExtractor(((PrototypeObjectType) objectType).getPrototype(), fieldName);
            if (target != null) {
                target.setReadAccessor(reader);
            }

            return reader;
        }

        boolean isGetter = getterRegexp.matcher(fieldName).matches();
        if (isGetter) {
            fieldName = fieldName.substring(3, fieldName.indexOf('(')).trim();
        }

        if (isGetter || identifierRegexp.matcher(fieldName).matches()) {
            Declaration decl = context.getDeclarationResolver().getDeclarations(context.getRule()).get(fieldName);
            if (decl != null && decl.getExtractor() instanceof FieldNameSupplier && "this".equals(((FieldNameSupplier) decl.getExtractor()).getFieldName())) {
                return decl.getExtractor();
            }

            try {
                reader = context.getPkg().getReader(objectType.getClassName(), fieldName, target);
            } catch (final Exception e) {
                if (reportError && context.isTypesafe()) {
                    registerDescrBuildError(context, descr, e,
                                            "Unable to create Field Extractor for '" + fieldName + "'" + e.getMessage());
                }
                // if there was an error, set the reader back to null
                reader = null;
            } finally {

                if (reportError) {
                    Collection<KnowledgeBuilderResult> results = context.getPkg().getWiringResults(((ClassObjectType) objectType).getClassType(), fieldName);
                    if (!results.isEmpty()) {
                        for (KnowledgeBuilderResult res : results) {
                            if (res.getSeverity() == ResultSeverity.ERROR) {
                                context.addError(new DroolsErrorWrapper(res));
                            } else {
                                context.addWarning(new DroolsWarningWrapper(res));
                            }
                        }
                    }
                }
            }
        } else {
            // we need MVEL extractor for expressions
            reader = ConstraintBuilder.get().buildMvelFieldReadAccessor(context, descr, pattern, objectType, fieldName, reportError);
        }

        return reader;
    }

    protected ConstraintConnectiveDescr parseExpression(final RuleBuildContext context,
                                                        final PatternDescr patternDescr,
                                                        final BaseDescr original,
                                                        final String expression) {
        DrlExprParser parser = DrlExprParserFactory.getDrlExprParser(context.getConfiguration().getOption(LanguageLevelOption.KEY));
        String toParse = normalizeEval(expression);
        if (!toParse.equals(expression) && Drl6ExprParser.shouldPreserveEval(toParse)) {
            toParse = expression;
        }
        ConstraintConnectiveDescr result = parser.parse(toParse);

        if (parser.hasErrors()) {
            for (DroolsParserException error : parser.getErrors()) {
                registerDescrBuildError(context, patternDescr,
                                        "Unable to parse pattern expression:\n" + error.getMessage());
            }
            return null;
        }
        result.setResource(patternDescr.getResource());
        result.copyLocation(original);
        return result;
    }

    public static void registerDescrBuildError(RuleBuildContext context, BaseDescr patternDescr, String error) {
        registerDescrBuildError(context, patternDescr, null, error);
    }

    public static void registerDescrBuildError(RuleBuildContext context, BaseDescr patternDescr, Object object, String error) {
        context.addError(new DescrBuildError(context.getParentDescr(), patternDescr, object, error));
    }
}
