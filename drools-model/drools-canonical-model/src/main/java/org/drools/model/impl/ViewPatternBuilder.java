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
package org.drools.model.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.drools.model.Condition;
import org.drools.model.ConditionalConsequence;
import org.drools.model.Consequence;
import org.drools.model.PatternDSL.ExchangeDefImpl;
import org.drools.model.PatternDSL.PatternBindingImpl;
import org.drools.model.PatternDSL.PatternDefImpl;
import org.drools.model.PatternDSL.PatternExprImpl;
import org.drools.model.PatternDSL.PatternItem;
import org.drools.model.PatternDSL.SequenceViewItem;
import org.drools.model.RuleItem;
import org.drools.model.RuleItemBuilder;
import org.drools.model.SequenceStep;
import org.drools.model.SingleConstraint;
import org.drools.model.Variable;
import org.drools.model.consequences.ConditionalNamedConsequenceImpl;
import org.drools.model.consequences.NamedConsequenceImpl;
import org.drools.model.constraints.SingleConstraint1;
import org.drools.model.constraints.SingleConstraint10;
import org.drools.model.constraints.SingleConstraint11;
import org.drools.model.constraints.SingleConstraint12;
import org.drools.model.constraints.SingleConstraint13;
import org.drools.model.constraints.SingleConstraint2;
import org.drools.model.constraints.SingleConstraint3;
import org.drools.model.constraints.SingleConstraint4;
import org.drools.model.constraints.SingleConstraint5;
import org.drools.model.constraints.SingleConstraint6;
import org.drools.model.constraints.SingleConstraint7;
import org.drools.model.constraints.SingleConstraint8;
import org.drools.model.constraints.SingleConstraint9;
import org.drools.model.patterns.AccumulatePatternImpl;
import org.drools.model.patterns.CompositePatterns;
import org.drools.model.patterns.EvalImpl;
import org.drools.model.patterns.ExistentialPatternImpl;
import org.drools.model.patterns.GroupByPatternImpl;
import org.drools.model.patterns.PatternImpl;
import org.drools.model.patterns.QueryCallPattern;
import org.drools.model.patterns.SequenceConditionImpl;
import org.drools.model.view.AccumulateExprViewItem;
import org.drools.model.view.CombinedExprViewItem;
import org.drools.model.view.ExistentialExprViewItem;
import org.drools.model.view.Expr10ViewItemImpl;
import org.drools.model.view.Expr11ViewItemImpl;
import org.drools.model.view.Expr12ViewItemImpl;
import org.drools.model.view.Expr13ViewItemImpl;
import org.drools.model.view.Expr1ViewItemImpl;
import org.drools.model.view.Expr2ViewItemImpl;
import org.drools.model.view.Expr3ViewItemImpl;
import org.drools.model.view.Expr4ViewItemImpl;
import org.drools.model.view.Expr5ViewItemImpl;
import org.drools.model.view.Expr6ViewItemImpl;
import org.drools.model.view.Expr7ViewItemImpl;
import org.drools.model.view.Expr8ViewItemImpl;
import org.drools.model.view.Expr9ViewItemImpl;
import org.drools.model.view.ExprViewItem;
import org.drools.model.view.FixedValueItem;
import org.drools.model.view.GroupByExprViewItem;
import org.drools.model.view.QueryCallViewItem;
import org.drools.model.view.ViewItem;

import static java.util.stream.Collectors.toList;

import static org.drools.model.impl.NamesGenerator.generateName;

public class ViewPatternBuilder implements ViewBuilder {

    ViewPatternBuilder() { }

    public CompositePatterns apply( RuleItemBuilder<?>[] viewItemBuilders ) {
        List<RuleItem> ruleItems = Stream.of( viewItemBuilders ).map( RuleItemBuilder::get ).collect( toList() );
        Iterator<RuleItem> ruleItemIterator = ruleItems.iterator();

        List<Condition> conditions = new ArrayList<>();
        Map<String, Consequence> consequences = new LinkedHashMap<>();

        while (ruleItemIterator.hasNext()) {
            RuleItem ruleItem = ruleItemIterator.next();

            if (ruleItem instanceof Consequence) {
                Consequence consequence = (Consequence) ruleItem;
                String name = ruleItemIterator.hasNext() ? generateName("consequence") : RuleImpl.DEFAULT_CONSEQUENCE_NAME;
                consequences.put(name, consequence);
                conditions.add( new NamedConsequenceImpl( name, consequence.isBreaking() ) );
                continue;
            }

            if (ruleItem instanceof ConditionalConsequence ) {
                conditions.add( createConditionalNamedConsequence(consequences, (ConditionalConsequence) ruleItem) );
                continue;
            }

            conditions.add( ruleItem2Condition( ruleItem ) );
        }

        return new CompositePatterns( Condition.Type.AND, conditions, consequences );
    }

    public static Condition ruleItem2Condition(RuleItem ruleItem) {
        if ( ruleItem instanceof PatternDefImpl ) {
            PatternDefImpl<?> patternDef = ( PatternDefImpl ) ruleItem;
            Variable<?> patternVariable = patternDef.getFirstVariable();
            PatternImpl pattern = new PatternImpl( patternVariable, patternVariable instanceof Exchange ? Condition.Type.RECEIVER : Condition.Type.PATTERN );
            for (PatternItem patternItem : patternDef.getItems()) {
                if ( patternItem instanceof PatternExprImpl ) {
                    pattern.addConstraint( (( PatternExprImpl ) patternItem).asConstraint( patternDef ) );
                } else if ( patternItem instanceof PatternBindingImpl ) {
                    pattern.addBinding( (( PatternBindingImpl ) patternItem).asBinding( patternDef ) );
                } else {
                    throw new UnsupportedOperationException( "Unknown pattern item type: " + patternItem );
                }
            }
            pattern.addWatchedProps(patternDef.getWatch());
            pattern.setPassive(patternDef.isPassive());
            return pattern;
        }

        if (ruleItem instanceof FixedValueItem ) {
            return new EvalImpl( (( FixedValueItem ) ruleItem).isValue() );
        }

        if ( ruleItem instanceof QueryCallViewItem ) {
            return new QueryCallPattern( (QueryCallViewItem) ruleItem );
        }

        if ( ruleItem instanceof CombinedExprViewItem ) {
            CombinedExprViewItem combined = ( CombinedExprViewItem ) ruleItem;
            List<Condition> conditions = new ArrayList<>();
            for (ViewItem expr : combined.getExpressions()) {
                conditions.add(ruleItem2Condition( expr ));
            }
            return new CompositePatterns( combined.getType(), conditions );
        }

        if ( ruleItem instanceof ExistentialExprViewItem ) {
            ExistentialExprViewItem existential = (ExistentialExprViewItem) ruleItem;
            return new ExistentialPatternImpl( ruleItem2Condition( existential.getExpression() ), existential.getType() );
        }

        if ( ruleItem instanceof GroupByExprViewItem ) {
            GroupByExprViewItem groupBy = ( GroupByExprViewItem ) ruleItem;
            return new GroupByPatternImpl(ruleItem2Condition( groupBy.getExpr() ),
                    groupBy.getVars(), groupBy.getVarKey(), groupBy.getGroupingFunction(),
                    groupBy.getAccumulateFunctions());
        }

        if ( ruleItem instanceof AccumulateExprViewItem ) {
            AccumulateExprViewItem acc = (AccumulateExprViewItem) ruleItem;
            return new AccumulatePatternImpl(ruleItem2Condition( acc.getExpr() ), null, acc.getAccumulateFunctions());
        }

        if ( ruleItem instanceof ExprViewItem ) {
            return new EvalImpl( createConstraint( (ExprViewItem) ruleItem ) );
        }

        if ( ruleItem instanceof ExchangeDefImpl ) {
            ExchangeDefImpl<?> exchangeDef = ( ExchangeDefImpl ) ruleItem;
            return new PatternImpl( exchangeDef.getFirstVariable(), Condition.Type.SENDER );
        }

        if (ruleItem instanceof SequenceViewItem) {
            SequenceViewItem sv = (SequenceViewItem) ruleItem;
            SequenceStep[] rawSteps = rewriteRevertingGateSteps(flattenNonPositiveAndSteps(sv.getSteps()));
            if (rawSteps.length > 0) {
                SequenceStep last = rawSteps[rawSteps.length - 1];
                if (!hasPositiveTrigger(last)) {
                    throw new IllegalArgumentException(
                        "sequence(): trailing absent()/oneOf()/noneOrAll() requires a following positive step. " +
                        "Trailing absence with completeWithin is planned but not yet implemented.");
                }
                for (SequenceStep step : rawSteps) {
                    if (hasPositiveTrigger(step)) {
                        break; // reached a positive step — prefix is fine
                    }
                    if (isAbsenceStep(step)) {
                        throw new IllegalArgumentException(
                            "sequence(): leading absent() has no preceding positive step to define its " +
                            "activation window. The veto always resets to step 0 (the same state), so " +
                            "the absence guard is vacuous and the positive trigger always fires. " +
                            "Add a positive step before absent().");
                    }
                }
                for (SequenceStep step : rawSteps) {
                    validateActivationMatching((org.drools.model.view.ViewItem) step);
                }
            }
            List<Condition> steps = Arrays.stream(rawSteps)
                    .map(s -> ruleItem2Condition((RuleItem) s))
                    .collect(toList());
            return new SequenceConditionImpl(steps);
        }

        throw new UnsupportedOperationException( "Unknown " + ruleItem );
    }

    private static boolean hasPositiveTrigger(SequenceStep step) {
        if (step instanceof ExistentialExprViewItem) {
            ExistentialExprViewItem ex = (ExistentialExprViewItem) step;
            if (ex.getType() == Condition.Type.NOT) {
                return false;
            }
            return true;
        }
        if (step instanceof CombinedExprViewItem) {
            CombinedExprViewItem comb = (CombinedExprViewItem) step;
            Condition.Type type = comb.getType();
            if (type == Condition.Type.NOT || type == Condition.Type.NOR
                    || type == Condition.Type.XOR || type == Condition.Type.XNOR) {
                return false;
            }
            if (type == Condition.Type.AND) {
                // In an AND composite, at least one child must be a positive trigger
                for (org.drools.model.view.ViewItem expr : comb.getExpressions()) {
                    if (expr instanceof SequenceStep) {
                        if (hasPositiveTrigger((SequenceStep) expr)) {
                            return true;
                        }
                    } else {
                        return true;
                    }
                }
                return false;
            }
            if (type == Condition.Type.OR) {
                // In an OR composite, all branches must have a positive trigger
                for (org.drools.model.view.ViewItem expr : comb.getExpressions()) {
                    if (expr instanceof SequenceStep) {
                        if (!hasPositiveTrigger((SequenceStep) expr)) {
                            return false;
                        }
                    }
                }
                return true;
            }
        }
        return true;
    }

    private static boolean isAbsenceStep(SequenceStep step) {
        if (step instanceof ExistentialExprViewItem) {
            return ((ExistentialExprViewItem) step).getType() == Condition.Type.NOT;
        }
        if (step instanceof CombinedExprViewItem) {
            Condition.Type type = ((CombinedExprViewItem) step).getType();
            return type == Condition.Type.NOT || type == Condition.Type.NOR;
        }
        throw new IllegalStateException("Unexpected SequenceStep type: " + step.getClass());
    }

    private static void validateActivationMatching(org.drools.model.view.ViewItem item) {
        if (item instanceof CombinedExprViewItem) {
            CombinedExprViewItem comb = (CombinedExprViewItem) item;
            Condition.Type type = comb.getType();
            for (org.drools.model.view.ViewItem expr : comb.getExpressions()) {
                validateActivationMatching(expr);
            }
            if (type == Condition.Type.XOR) {
                int matchedChildren = 0;
                for (org.drools.model.view.ViewItem expr : comb.getExpressions()) {
                    if (matchesOnActivation(expr)) {
                        matchedChildren++;
                    }
                }
                if (matchedChildren >= 2) {
                    throw new IllegalArgumentException(
                        "sequence(): oneOf() has " + matchedChildren + " children that match on step activation (0 inputs), " +
                        "causing an immediate over-match veto upon activation. oneOf() requires exactly one child to match.");
                }
            }
        }
    }

    private static boolean matchesOnActivation(org.drools.model.view.ViewItem item) {
        if (item instanceof CombinedExprViewItem) {
            CombinedExprViewItem comb = (CombinedExprViewItem) item;
            Condition.Type type = comb.getType();
            if (type == Condition.Type.XNOR) {
                int matched = 0;
                int total = comb.getExpressions().length;
                for (org.drools.model.view.ViewItem expr : comb.getExpressions()) {
                    if (matchesOnActivation(expr)) {
                        matched++;
                    }
                }
                return matched == 0 || matched == total;
            }
            if (type == Condition.Type.XOR) {
                int matched = 0;
                for (org.drools.model.view.ViewItem expr : comb.getExpressions()) {
                    if (matchesOnActivation(expr)) {
                        matched++;
                    }
                }
                return matched == 1;
            }
            if (type == Condition.Type.AND) {
                for (org.drools.model.view.ViewItem expr : comb.getExpressions()) {
                    if (!matchesOnActivation(expr)) {
                        return false;
                    }
                }
                return comb.getExpressions().length > 0;
            }
            if (type == Condition.Type.OR) {
                for (org.drools.model.view.ViewItem expr : comb.getExpressions()) {
                    if (matchesOnActivation(expr)) {
                        return true;
                    }
                }
                return false;
            }
            if (type == Condition.Type.NOT || type == Condition.Type.NOR) {
                return false;
            }
        }
        return false;
    }

    /**
     * Rewrites runs of revertible gate steps (such as {@code oneOf()} / XOR and {@code noneOrAll()} / XNOR)
     * by folding them together with their immediately following positive trigger into an {@code and(...)}
     * composite step.
     * <p>
     * Standalone XOR/XNOR gates are non-propagating on their own within the sequence pipeline and require
     * a following positive pattern step to act as the activation trigger. This transformation:
     * <ul>
     *   <li>Separates and emits any interleaved absence guards ({@code NOT}/{@code NOR}/{@code absent()})
     *       before the folded composite so that compiler accumulation loops build them as independent
     *       veto side-channels.</li>
     *   <li>Folds consecutive XOR/XNOR gate steps and the subsequent positive trigger step into a single
     *       {@link CombinedExprViewItem} of type {@link Condition.Type#AND}.</li>
     * </ul>
     *
     * @param steps the original sequence steps
     * @return the rewritten sequence steps with folded gate composites
     */
    private static SequenceStep[] rewriteRevertingGateSteps(SequenceStep[] steps) {
        boolean hasXor = false;
        for (int i = 0; i < steps.length; i++) {
            SequenceStep step = steps[i];
            if (step instanceof CombinedExprViewItem) {
                Condition.Type t = ((CombinedExprViewItem) step).getType();
                if ((t == Condition.Type.XOR || t == Condition.Type.XNOR) && i + 1 < steps.length) {
                    hasXor = true;
                    break;
                }
            }
        }
        if (!hasXor) {
            return steps;
        }
        List<SequenceStep> result = new ArrayList<>();
        for (int i = 0; i < steps.length; i++) {
            SequenceStep step = steps[i];
            if (step instanceof CombinedExprViewItem) {
                Condition.Type t = ((CombinedExprViewItem) step).getType();
                if (t == Condition.Type.XOR || t == Condition.Type.XNOR) {
                    // Collect the full run of consecutive XOR/XNOR gate steps.
                    // Interleaved absent() absence guards are held in a separate list —
                    // they will be emitted BEFORE the folded and(...) composite so that
                    // KiePackagesBuilder's top-level NOT accumulation loop handles them as
                    // independent veto side-channels, which is the same circuit it builds for
                    // standalone absent() steps.
                    List<ViewItem> xorRun = new ArrayList<>();
                    List<SequenceStep> absenceGuards = new ArrayList<>();
                    xorRun.add((ViewItem) step);
                    while (i + 1 < steps.length) {
                        SequenceStep next = steps[i + 1];
                        if (next instanceof CombinedExprViewItem) {
                            Condition.Type nt = ((CombinedExprViewItem) next).getType();
                            if (nt == Condition.Type.XOR || nt == Condition.Type.XNOR) {
                                xorRun.add((ViewItem) next);
                                i++;
                                continue;
                            }
                            if (nt == Condition.Type.NOT || nt == Condition.Type.NOR) {
                                absenceGuards.add(next);
                                i++;
                                continue;
                            }
                        } else if (next instanceof ExistentialExprViewItem
                                && ((ExistentialExprViewItem) next).getType() == Condition.Type.NOT) {
                            absenceGuards.add(next);
                            i++;
                            continue;
                        }
                        break;
                    }
                    // The step that follows the run must be a positive trigger.
                    if (i + 1 >= steps.length) {
                        // No following step at all — the trailing-gate check in the caller handles this.
                        result.addAll(xorRun.stream().map(v -> (SequenceStep) v).collect(java.util.stream.Collectors.toList()));
                        result.addAll(absenceGuards);
                        continue;
                    }
                    SequenceStep trigger = steps[i + 1];
                    if (!hasPositiveTrigger(trigger)) {
                        throw new IllegalArgumentException(
                                "sequence(): consecutive bare oneOf()/noneOrAll() steps require a positive " +
                                "pattern step between them. Found " + xorRun.size() + " consecutive " +
                                "oneOf/noneOrAll step(s) followed by another revertible guard. " +
                                "Each oneOf()/noneOrAll() run must be immediately followed by a positive step.");
                    }
                    // Emit absence guards first so KiePackagesBuilder's NOT accumulation loop
                    // picks them up as veto side-channels before the positive trigger.
                    result.addAll(absenceGuards);
                    // Fold the xor/xnor run + trigger into and(xor(...), ..., trigger).
                    List<ViewItem> run = new ArrayList<>(xorRun);
                    run.add((ViewItem) trigger);
                    CombinedExprViewItem wrapped = new CombinedExprViewItem(
                            Condition.Type.AND,
                            run.toArray(new ViewItem[0]));
                    result.add(wrapped);
                    i++;
                    continue;
                }
            }
            result.add(step);
        }
        return result.toArray(new SequenceStep[0]);
    }

    private static SequenceStep[] flattenNonPositiveAndSteps(SequenceStep[] steps) {
        List<SequenceStep> flattened = new ArrayList<>();
        for (SequenceStep step : steps) {
            if (step instanceof CombinedExprViewItem) {
                CombinedExprViewItem comb = (CombinedExprViewItem) step;
                if (comb.getType() == Condition.Type.AND && !hasPositiveTrigger(step)) {
                    List<SequenceStep> childSteps = new ArrayList<>();
                    for (ViewItem expr : comb.getExpressions()) {
                        if (expr instanceof SequenceStep) {
                            childSteps.add((SequenceStep) expr);
                        }
                    }
                    flattened.addAll(Arrays.asList(flattenNonPositiveAndSteps(childSteps.toArray(new SequenceStep[0]))));
                    continue;
                }
            }
            flattened.add(step);
        }
        return flattened.toArray(new SequenceStep[0]);
    }



    private static ConditionalNamedConsequenceImpl createConditionalNamedConsequence( Map<String, Consequence> consequences, ConditionalConsequence cond) {
        return new ConditionalNamedConsequenceImpl( createConstraint( cond.getExpr() ),
                createNamedConsequence( consequences, cond.getThen() ),
                cond.getElse() != null ? createConditionalNamedConsequence( consequences, cond.getElse() ) : null );
    }

    private static SingleConstraint createConstraint( ExprViewItem expr ) {
        if (expr instanceof Expr1ViewItemImpl ) {
            return new SingleConstraint1( (Expr1ViewItemImpl) expr );
        }
        if (expr instanceof Expr2ViewItemImpl ) {
            return new SingleConstraint2( (Expr2ViewItemImpl) expr );
        }
        if (expr instanceof Expr3ViewItemImpl ) {
            return new SingleConstraint3( (Expr3ViewItemImpl) expr );
        }
        if (expr instanceof Expr4ViewItemImpl ) {
            return new SingleConstraint4( (Expr4ViewItemImpl) expr );
        }
        if (expr instanceof Expr5ViewItemImpl ) {
            return new SingleConstraint5( (Expr5ViewItemImpl) expr );
        }
        if (expr instanceof Expr6ViewItemImpl ) {
            return new SingleConstraint6( (Expr6ViewItemImpl) expr );
        }
        if (expr instanceof Expr7ViewItemImpl ) {
            return new SingleConstraint7( (Expr7ViewItemImpl) expr );
        }
        if (expr instanceof Expr8ViewItemImpl ) {
            return new SingleConstraint8( (Expr8ViewItemImpl) expr );
        }
        if (expr instanceof Expr9ViewItemImpl ) {
            return new SingleConstraint9( (Expr9ViewItemImpl) expr );
        }
        if (expr instanceof Expr10ViewItemImpl ) {
            return new SingleConstraint10( (Expr10ViewItemImpl) expr );
        }
        if (expr instanceof Expr11ViewItemImpl ) {
            return new SingleConstraint11( (Expr11ViewItemImpl) expr );
        }
        if (expr instanceof Expr12ViewItemImpl ) {
            return new SingleConstraint12( (Expr12ViewItemImpl) expr );
        }
        if (expr instanceof Expr13ViewItemImpl ) {
            return new SingleConstraint13( (Expr13ViewItemImpl) expr );
        }
        return null;
    }

    private static NamedConsequenceImpl createNamedConsequence( Map<String, Consequence> consequences, Consequence consequence ) {
        if (consequence == null) {
            return null;
        }
        String name = generateName("consequence");
        consequences.put(name, consequence);
        return new NamedConsequenceImpl( name, consequence.isBreaking() );
    }
}
