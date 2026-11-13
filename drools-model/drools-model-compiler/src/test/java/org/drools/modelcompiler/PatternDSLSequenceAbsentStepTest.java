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
package org.drools.modelcompiler;

import java.util.ArrayList;
import java.util.List;

import org.drools.model.Model;
import org.drools.model.Rule;
import org.drools.model.Variable;
import org.drools.model.impl.ModelImpl;
import org.drools.modelcompiler.domain.Person;
import org.drools.modelcompiler.domain.Toy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.drools.model.DSL.declarationOf;
import static org.drools.model.DSL.execute;
import static org.drools.model.PatternDSL.absent;
import static org.drools.model.PatternDSL.and;
import static org.drools.model.PatternDSL.or;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.on;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;

public class PatternDSLSequenceAbsentStepTest {

    private final Variable<Toy>    toy     = declarationOf(Toy.class);
    private final Variable<Toy>    blocker = declarationOf(Toy.class);
    private final List<String>     results = new ArrayList<>();
    private KieSession             ksession;

    @Test
    public void absentStepAdvancesWhenNoBlocker() {
        // sequence: ball → absent(blocker) → bat
        // No blocker ever inserted → rule fires.
        ksession = makeKSession(buildThreeStepRule());
        insertAndFire(new Toy("ball"));
        insertAndFire(new Toy("bat"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void absentStepVetoesWhenBlockerArrivesAfterActivation() {
        // sequence: ball → absent(blocker) → bat
        // Blocker inserted AFTER ball (i.e., after the absence guard activates)
        // but BEFORE bat → rule must NOT fire.
        ksession = makeKSession(buildThreeStepRule());
        insertAndFire(new Toy("ball"));              // step 1: positive, activates absence guard
        insertAndFire(new Toy("blocker-toy"));       // fires into absence guard → veto
        insertAndFire(new Toy("bat"));               // step 3 never activates
        assertThat(results).isEmpty();
    }

    @Test
    public void absentStepIgnoresBlockerInsertedBeforeStepActivated() {
        // sequence: ball → absent(blocker) → bat
        // Blocker is already in WM when ball advances the sequence to the absent-step.
        // Arrival order governs: the blocker arrived before this step was active, so it is
        // invisible to the absence guard — the rule must fire.
        ksession = makeKSession(buildThreeStepRule());
        insertAndFire(new Toy("blocker-toy"), new Toy("ball")); // blocker present before guard activates
        insertAndFire(new Toy("bat"));
        assertThat(results).containsExactly("fired");
    }
    
    @Test
    public void absentStepInMiddleVetoesSequence() {
        // sequence: A → absent(blocker) → B → C
        // Blocker arrives between A and B → rule must NOT fire.
        Variable<Toy> midBlocker = declarationOf(Toy.class);
        Variable<Toy> toyB       = declarationOf(Toy.class);
        Variable<Toy> toyC       = declarationOf(Toy.class);

        Rule rule = rule("absent-in-middle").build(
            sequence(
                pattern(toy).expr("isA", t -> t.getName().equals("A")),
                absent(pattern(midBlocker).expr("isBlocker", t -> t.getName().equals("blocker-toy"))),
                pattern(toyB).expr("isB", t -> t.getName().equals("B")),
                pattern(toyC).expr("isC", t -> t.getName().equals("C"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("A"));
        insertAndFire(new Toy("blocker-toy")); // veto fires here
        insertAndFire(new Toy("B"));
        insertAndFire(new Toy("C"));
        assertThat(results).isEmpty();
    }

    @Test
    public void twoConsecutiveAbsentGuardsFirstBlockerVetoes() {
        // sequence(start, absent(blockerA), absent(blockerB), done):
        // start advances the sequence to the absent-guards step.
        // blockerA inserted after start (while the absent-guards step is active) → veto + reset.
        // Rule must not fire: done arrives but the sequence is back at step 0, waiting for start again.
        Variable<Toy> toyStart  = declarationOf(Toy.class);
        Variable<Toy> blockerA  = declarationOf(Toy.class);
        Variable<Toy> blockerB  = declarationOf(Toy.class);
        Variable<Toy> toyDone   = declarationOf(Toy.class);

        Rule rule = rule("two-absent-guards-first-vetoes").build(
            sequence(
                pattern(toyStart).expr("isStart",   t -> t.getName().equals("start")),
                absent(pattern(blockerA).expr("isBlockerA", t -> t.getName().equals("blockerA"))),
                absent(pattern(blockerB).expr("isBlockerB", t -> t.getName().equals("blockerB"))),
                pattern(toyDone).expr("isDone",     t -> t.getName().equals("done"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("start"));    // advance to not-guards step
        insertAndFire(new Toy("blockerA")); // absence guard fires → veto → reset to step 0
        insertAndFire(new Toy("done"));     // step 0 waits for "start", not "done" → no fire
        assertThat(results).isEmpty();
    }

    @Test
    public void twoConsecutiveAbsentGuardsSecondBlockerVetoes() {
        // sequence(start, absent(blockerA), absent(blockerB), done):
        // start advances to the absent-guards step.
        // blockerB inserted after start → veto + reset.
        // Rule must not fire: done arrives but the sequence is back at step 0.
        Variable<Toy> toyStart  = declarationOf(Toy.class);
        Variable<Toy> blockerA  = declarationOf(Toy.class);
        Variable<Toy> blockerB  = declarationOf(Toy.class);
        Variable<Toy> toyDone   = declarationOf(Toy.class);

        Rule rule = rule("two-absent-guards-second-vetoes").build(
            sequence(
                pattern(toyStart).expr("isStart",   t -> t.getName().equals("start")),
                absent(pattern(blockerA).expr("isBlockerA", t -> t.getName().equals("blockerA"))),
                absent(pattern(blockerB).expr("isBlockerB", t -> t.getName().equals("blockerB"))),
                pattern(toyDone).expr("isDone",     t -> t.getName().equals("done"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("start"));    // advance to not-guards step
        insertAndFire(new Toy("blockerB")); // absence guard fires → veto → reset to step 0
        insertAndFire(new Toy("done"));     // step 0 waits for "start", not "done" → no fire
        assertThat(results).isEmpty();
    }

    private Rule buildThreeStepRule() {
        return rule("absent-guard").build(
            sequence(
                pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                absent(pattern(blocker).expr("isBlocker", t -> t.getName().equals("blocker-toy"))),
                pattern(toy).expr("isBat", t -> t.getName().equals("bat"))
            ),
            execute(() -> results.add("fired"))
        );
    }

    @Test
    public void trailingAbsentWithoutCompleteWithinIsRejected() {
        Variable<Toy> toyV = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("trailing-not").build(
                sequence(
                    pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                    absent(pattern(toyV).expr("isBlocker", t -> t.getName().equals("blocker-toy")))
                ),
                execute(() -> {})
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("trailing");
    }

    @Test
    public void standaloneAbsentIsRejected() {
        Variable<Toy> toyV = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("standalone-absent").build(
                sequence(absent(pattern(toyV).expr("isBlocker", t -> t.getName().equals("blocker-toy")))),
                execute(() -> {})
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("trailing");
    }

    @Test
    public void trailingAbsentMultiPatternWithoutCompleteWithinIsRejected() {
        Variable<Toy> toyA = declarationOf(Toy.class);
        Variable<Toy> toyB = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("trailing-absent-multi").build(
                sequence(
                    pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                    absent(
                        pattern(toyA).expr("isBlockerA", t -> t.getName().equals("blockerA")),
                        pattern(toyB).expr("isBlockerB", t -> t.getName().equals("blockerB"))
                    )
                ),
                execute(() -> {})
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("trailing");
    }

    @Test
    public void leadingAbsentIsRejected() {
        // sequence(absent(X), pattern(Y)) — absent() with no preceding positive step is vacuous:
        // the veto resets to step 0, which is the same state, so the positive trigger always fires.
        Variable<Toy> toyV = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("leading-absent").build(
                sequence(
                    absent(pattern(toyV).expr("isBlocker", t -> t.getName().equals("blocker-toy"))),
                    pattern(toy).expr("isBat", t -> t.getName().equals("bat"))
                ),
                execute(() -> {})
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("leading");
    }

    @Test
    public void leadingTwoConsecutiveAbsentGuardsAreRejected() {
        // sequence(absent(X), absent(Y), pattern(Z)) — all steps before the first positive trigger
        // are veto-only; the prefix is vacuous.
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("leading-two-absent").build(
                sequence(
                    absent(pattern(blockerA).expr("isBlockerA", t -> t.getName().equals("blockerA"))),
                    absent(pattern(blockerB).expr("isBlockerB", t -> t.getName().equals("blockerB"))),
                    pattern(toy).expr("isDone", t -> t.getName().equals("done"))
                ),
                execute(() -> {})
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("leading");
    }

    @Test
    public void leadingAbsentErrorMessageDistinguishesFromTrailingCase() {
        // The error for a leading absent() must say "leading", not "trailing".
        Variable<Toy> toyV = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("leading-absent-message").build(
                sequence(
                    absent(pattern(toyV).expr("isBlocker", t -> t.getName().equals("blocker-toy"))),
                    pattern(toy).expr("isBat", t -> t.getName().equals("bat"))
                ),
                execute(() -> {})
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("leading")
         .hasMessageNotContaining("trailing");
    }

    @Test
    public void middleAbsentStillCompiles() {
        // sequence(pattern(A), absent(X), pattern(B)) — absent() in the middle (after a positive step)
        // must NOT be rejected. Regression guard for the leading-absence check.
        Variable<Toy> toyV = declarationOf(Toy.class);

        Rule rule = rule("middle-absent").build(
            sequence(
                pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                absent(pattern(toyV).expr("isBlocker", t -> t.getName().equals("blocker-toy"))),
                pattern(toy).expr("isBat", t -> t.getName().equals("bat"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("ball"));
        insertAndFire(new Toy("bat"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void absentTwoPatternsBothAbsentFires() {
        // absent(blockerA, blockerB): neither inserted → rule fires
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);

        Rule rule = rule("absent-both-absent").build(
            sequence(
                pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                absent(
                    pattern(blockerA).expr("isA", t -> t.getName().equals("blockerA")),
                    pattern(blockerB).expr("isB", t -> t.getName().equals("blockerB"))
                ),
                pattern(toy).expr("isBat", t -> t.getName().equals("bat"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("ball"));
        insertAndFire(new Toy("bat"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void absentTwoPatternsFirstVetoes() {
        // absent(blockerA, blockerB): blockerA inserted after ball → veto
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);

        Rule rule = rule("absent-first-vetoes").build(
            sequence(
                pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                absent(
                    pattern(blockerA).expr("isA", t -> t.getName().equals("blockerA")),
                    pattern(blockerB).expr("isB", t -> t.getName().equals("blockerB"))
                ),
                pattern(toy).expr("isBat", t -> t.getName().equals("bat"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("ball"));
        insertAndFire(new Toy("blockerA")); // hits first veto gate → veto
        insertAndFire(new Toy("bat"));
        assertThat(results).isEmpty();
    }

    @Test
    public void absentTwoPatternsSecondVetoes() {
        // absent(blockerA, blockerB): blockerB inserted after ball → veto
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);

        Rule rule = rule("absent-second-vetoes").build(
            sequence(
                pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                absent(
                    pattern(blockerA).expr("isA", t -> t.getName().equals("blockerA")),
                    pattern(blockerB).expr("isB", t -> t.getName().equals("blockerB"))
                ),
                pattern(toy).expr("isBat", t -> t.getName().equals("bat"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("ball"));
        insertAndFire(new Toy("blockerB")); // hits second veto gate → veto
        insertAndFire(new Toy("bat"));
        assertThat(results).isEmpty();
    }

    @Test
    public void absentTwoPatternsBlockerAlreadyPresentIgnored() {
        // absent(blockerA, blockerB): blockerA in WM before ball advances to the absent-step.
        // Arrival order governs: the blocker arrived before this step was active, so it is
        // invisible to the absence guard — the rule must fire.
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);

        Rule rule = rule("absent-preexisting").build(
            sequence(
                pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                absent(
                    pattern(blockerA).expr("isA", t -> t.getName().equals("blockerA")),
                    pattern(blockerB).expr("isB", t -> t.getName().equals("blockerB"))
                ),
                pattern(toy).expr("isBat", t -> t.getName().equals("bat"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("blockerA"), new Toy("ball")); // blockerA present before guard activates
        insertAndFire(new Toy("bat"));
        assertThat(results).containsExactly("fired");
    }


    @Test
    public void wrappedTrailingAbsentThrows() {
        // sequence(..., and(absent(blocker))) has no positive trigger in the trailing step.
        // and(absent(...)) wraps a reverting gate; it must be rejected at rule build time.
        Variable<Toy> toyV = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("wrapped-absent-rejected").build(
                sequence(
                    pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                    and(
                        absent(pattern(toyV).expr("isBlocker", t -> t.getName().equals("blocker")))
                    )
                ),
                execute(() -> {})
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("trailing absent()/oneOf()/noneOrAll() requires a following positive step");
    }

    @Test
    public void absentStepDoesNotRevetoOnPreExistingBlockerAfterReset() {
        ksession = makeKSession(buildThreeStepRule());

        insertAndFire(new Toy("ball"));
        FactHandle blockerHandle = ksession.insert(new Toy("blocker-toy"));
        ksession.fireAllRules(); // veto fires, sequence resets to step 0

        insertAndFire(new Toy("ball"));

        ksession.retract(blockerHandle);
        ksession.fireAllRules();
        insertAndFire(new Toy("bat"));

        assertThat(results)
                .as("rule should fire once after blocker retracted and bat inserted on second run")
                .containsExactly("fired");
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void betaAbsenceVetoesOnlyTheCorrelatedAnchor() {
        Variable<Person> person  = declarationOf(Person.class);
        Variable<Toy>    start   = declarationOf(Toy.class);
        Variable<Toy>    blocker = declarationOf(Toy.class);
        Variable<Toy>    done    = declarationOf(Toy.class);

        Rule rule = rule("beta-correlated-veto").build(
            pattern(person),
            sequence(
                pattern(start).expr("isStart", t -> t.getName().equals("start")),
                absent(pattern(blocker).expr("ownedByAnchor", person, (t, p) -> p.getName().equals(t.getOwner()))),
                pattern(done).expr("isDone", t -> t.getName().equals("done"))
            ),
            on(person).execute(p -> results.add("fired:" + p.getName()))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("alice"), new Person("bob"));
        insertAndFire(new Toy("start"));     // both instances reach the absence guard
        Toy blockerToy = new Toy("blocker-toy");
        blockerToy.setOwner("alice");
        insertAndFire(blockerToy);           // correlates with alice only
        insertAndFire(new Toy("done"));

        assertThat(results)
                .as("only the anchor the blocker correlates with is vetoed")
                .containsExactly("fired:bob");
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void vetoOfOneAnchorDoesNotHideFactFromOtherAnchorsFilter() {
        // sequence(start, absent(x owned by anchor), d owned by anchor, x)
        // Two anchors, both reach the absence guard. A d owned by bob advances bob only.
        // x owned by alice vetoes alice; the same x must still reach bob's final x filter.
        Variable<Person> person  = declarationOf(Person.class);
        Variable<Toy>    start   = declarationOf(Toy.class);
        Variable<Toy>    blocker = declarationOf(Toy.class);
        Variable<Toy>    ownedD  = declarationOf(Toy.class);
        Variable<Toy>    finalX  = declarationOf(Toy.class);

        Rule rule = rule("veto-hides-fact-from-other-anchor").build(
            pattern(person),
            sequence(
                pattern(start).expr("isStart", t -> t.getName().equals("s")),
                absent(pattern(blocker).expr("isX", t -> t.getName().equals("x"))
                                       .expr("ownedByAnchor", person, (t, p) -> p.getName().equals(t.getOwner()))),
                pattern(ownedD).expr("isD", t -> t.getName().equals("d"))
                               .expr("dOwnedByAnchor", person, (t, p) -> p.getName().equals(t.getOwner())),
                pattern(finalX).expr("isFinalX", t -> t.getName().equals("x"))
            ),
            on(person).execute(p -> results.add("fired:" + p.getName()))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("alice"), new Person("bob"));
        insertAndFire(new Toy("s"));         // both anchors reach the absence guard
        Toy d = new Toy("d");
        d.setOwner("bob");
        insertAndFire(d);                    // bob advances to the final x step
        Toy x = new Toy("x");
        x.setOwner("alice");
        insertAndFire(x);                    // vetoes alice; must still complete bob

        assertThat(results)
                .as("a filter still active for the fact must receive it even when an earlier filter's anchor left its step")
                .containsExactly("fired:bob");
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void anchorLeavingStepDoesNotHideFactFromOtherAnchorsFilter() {
        // Same shape without a veto: sequence(start, or(x owned by anchor, d owned by anchor), x)
        // A d owned by bob advances bob to the final x step.
        // x owned by alice satisfies alice's or-step; the same x must still reach bob's final x filter.
        Variable<Person> person = declarationOf(Person.class);
        Variable<Toy>    start  = declarationOf(Toy.class);
        Variable<Toy>    ownedX = declarationOf(Toy.class);
        Variable<Toy>    ownedD = declarationOf(Toy.class);
        Variable<Toy>    finalX = declarationOf(Toy.class);

        Rule rule = rule("leaving-anchor-hides-fact-from-other-anchor").build(
            pattern(person),
            sequence(
                pattern(start).expr("isStart", t -> t.getName().equals("s")),
                or(
                    pattern(ownedX).expr("isX", t -> t.getName().equals("x"))
                                   .expr("xOwnedByAnchor", person, (t, p) -> p.getName().equals(t.getOwner())),
                    pattern(ownedD).expr("isD", t -> t.getName().equals("d"))
                                   .expr("dOwnedByAnchor", person, (t, p) -> p.getName().equals(t.getOwner()))
                ),
                pattern(finalX).expr("isFinalX", t -> t.getName().equals("x"))
            ),
            on(person).execute(p -> results.add("fired:" + p.getName()))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("alice"), new Person("bob"));
        insertAndFire(new Toy("s"));         // both anchors reach the or-step
        Toy d = new Toy("d");
        d.setOwner("bob");
        insertAndFire(d);                    // bob advances to the final x step
        Toy x = new Toy("x");
        x.setOwner("alice");
        insertAndFire(x);                    // alice leaves the or-step; must still complete bob

        assertThat(results)
                .as("a filter still active for the fact must receive it even when an earlier filter's anchor left its step")
                .containsExactly("fired:bob");
    }

    @Test
    public void absentThreePatternsBothAbsentFires() {
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);
        Variable<Toy> blockerC = declarationOf(Toy.class);

        Rule rule = rule("absent-three-absent").build(
            sequence(
                pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                absent(
                    pattern(blockerA).expr("isA", t -> t.getName().equals("blockerA")),
                    pattern(blockerB).expr("isB", t -> t.getName().equals("blockerB")),
                    pattern(blockerC).expr("isC", t -> t.getName().equals("blockerC"))
                ),
                pattern(toy).expr("isBat", t -> t.getName().equals("bat"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("ball"));
        insertAndFire(new Toy("bat"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void absentThreePatternsThirdVetoes() {
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);
        Variable<Toy> blockerC = declarationOf(Toy.class);

        Rule rule = rule("absent-three-third-vetoes").build(
            sequence(
                pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                absent(
                    pattern(blockerA).expr("isA", t -> t.getName().equals("blockerA")),
                    pattern(blockerB).expr("isB", t -> t.getName().equals("blockerB")),
                    pattern(blockerC).expr("isC", t -> t.getName().equals("blockerC"))
                ),
                pattern(toy).expr("isBat", t -> t.getName().equals("bat"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("ball"));
        insertAndFire(new Toy("blockerC")); // hits third veto gate (varargs) -> veto
        insertAndFire(new Toy("bat"));
        assertThat(results).isEmpty();
    }

    @Test
    public void leadingMultiArgAbsentIsRejected() {
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("leading-multi-absent").build(
                sequence(
                    absent(
                        pattern(blockerA).expr("isA", t -> t.getName().equals("blockerA")),
                        pattern(blockerB).expr("isB", t -> t.getName().equals("blockerB"))
                    ),
                    pattern(toy).expr("isBat", t -> t.getName().equals("bat"))
                ),
                execute(() -> {})
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .as("multi-argument absent() as a leading sequence step must be rejected just like single-argument absent()")
         .hasMessageContaining("leading");
    }

    @Test
    public void vetoingFactThatIsAlsoStepZeroFactSameShape() {
        // This test locks down a decision that the same fact can not veto and activate the same sequence
        Variable<Toy> first   = declarationOf(Toy.class);
        Variable<Toy> vetoer  = declarationOf(Toy.class);
        Variable<Toy> done    = declarationOf(Toy.class);

        Rule rule = rule("veto-also-step0-same-shape").build(
            sequence(
                pattern(first).expr("isS", t -> t.getName().equals("s")),
                absent(pattern(vetoer).expr("isS2", t -> t.getName().equals("s"))),
                pattern(done).expr("isD", t -> t.getName().equals("d"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("s"));
        insertAndFire(new Toy("s"));
        insertAndFire(new Toy("d"));

        assertThat(results).as("veto only: s, s, d").isEmpty();
    }

    @Test
    public void vetoingFactThatIsAlsoStepZeroFactDifferentPatterns() {
        // This test locks down a decision that the same fact can not veto and activate the same sequence
        Variable<Toy> first   = declarationOf(Toy.class);
        Variable<Toy> vetoer  = declarationOf(Toy.class);
        Variable<Toy> done    = declarationOf(Toy.class);

        Rule rule = rule("veto-also-step0-different-patterns").build(
            sequence(
                pattern(first).expr("isSOrX", t -> t.getName().equals("s") || t.getName().equals("x")),
                absent(pattern(vetoer).expr("isX", t -> t.getName().equals("x"))),
                pattern(done).expr("isD", t -> t.getName().equals("d"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("s"));
        insertAndFire(new Toy("x"));
        insertAndFire(new Toy("d"));

        assertThat(results).as("veto only: s, x, d").isEmpty();
    }

    @Test
    public void vetoingFactThatIsAlsoStepZeroFactPatternsBuiltInOtherOrder() {
        // This test locks down a decision that the same fact can not veto and activate the same sequence
        // Absence pattern and its variable are created before the step-0 pattern and variable.
        Variable<Toy> vetoer  = declarationOf(Toy.class);
        Variable<Toy> first   = declarationOf(Toy.class);
        Variable<Toy> done    = declarationOf(Toy.class);
        var absentPattern = absent(pattern(vetoer).expr("isS2", t -> t.getName().equals("s")));

        Rule rule = rule("veto-also-step0-other-order").build(
            sequence(
                pattern(first).expr("isS", t -> t.getName().equals("s")),
                absentPattern,
                pattern(done).expr("isD", t -> t.getName().equals("d"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("s"));
        insertAndFire(new Toy("s"));
        insertAndFire(new Toy("d"));

        assertThat(results).as("veto only: s, s, d, other build order").isEmpty();
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void vetoingFactDoesNotRestartAnchorStepZeroFilterDeliveredBeforeAbsenceFilter() {
        // This test locks down a decision that the same fact can not veto and activate the same sequence
        Variable<Person> person  = declarationOf(Person.class);
        Variable<Toy>    first   = declarationOf(Toy.class);
        Variable<Toy>    vetoer  = declarationOf(Toy.class);
        Variable<Toy>    done    = declarationOf(Toy.class);

        ksession = makeKSession(buildVetoOnlyAnchoredRule(person, first, vetoer, done));
        insertAndFire(new Person("alice"), new Person("bob"));
        insertAndFire(ownedToy("s", "alice"));   // alice -> absence guard; bob stays at step 0
        insertAndFire(ownedToy("s", "alice"));   // vetoes alice
        insertAndFire(new Toy("d"));

        assertThat(results)
                .as("veto only: the vetoing fact never starts the next attempt (filter list [step-0, absence])")
                .isEmpty();
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void vetoingFactDoesNotRestartAnchorAbsenceFilterDeliveredBeforeStepZeroFilter() {
        // This test locks down a decision that the same fact can not veto and activate the same sequence
        Variable<Person> person  = declarationOf(Person.class);
        Variable<Toy>    first   = declarationOf(Toy.class);
        Variable<Toy>    vetoer  = declarationOf(Toy.class);
        Variable<Toy>    done    = declarationOf(Toy.class);

        ksession = makeKSession(buildVetoOnlyAnchoredRule(person, first, vetoer, done));
        insertAndFire(new Person("alice"), new Person("bob"));
        insertAndFire(ownedToy("s", "alice"));   // alice -> absence guard
        insertAndFire(ownedToy("s", "bob"));     // bob -> absence guard; step-0 filter now empty
        insertAndFire(ownedToy("s", "bob"));     // vetoes bob; bob re-activates the step-0 filter
        insertAndFire(ownedToy("s", "alice"));   // vetoes alice
        insertAndFire(new Toy("d"));

        assertThat(results)
                .as("veto only: the vetoing fact never starts the next attempt (filter list [absence, step-0])")
                .isEmpty();
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void vetoingFactDoesNotRestartSequenceStepZeroAndVetoPatternsOfDifferentTypes() {
        // This test locks down a decision that the same fact can not veto and activate the same sequence
        Variable<Object> first  = declarationOf(Object.class);
        Variable<Toy>    vetoer = declarationOf(Toy.class);
        Variable<Toy>    done   = declarationOf(Toy.class);

        Rule rule = rule("veto-only-different-types").build(
            sequence(
                pattern(first).expr("isS", o -> o instanceof Toy && ((Toy) o).getName().equals("s")),
                absent(pattern(vetoer).expr("isS2", t -> t.getName().equals("s"))),
                pattern(done).expr("isD", t -> t.getName().equals("d"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("s"));
        insertAndFire(new Toy("s"));
        insertAndFire(new Toy("d"));

        assertThat(results).as("veto only: step-0 and veto patterns on different object types").isEmpty();
    }


    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void vetoingFactDoesNotRestartSequenceStepZeroOnToyVetoOnObject() {
        // This test locks down a decision that the same fact can not veto and activate the same sequence
        Variable<Toy>    first  = declarationOf(Toy.class);
        Variable<Object> vetoer = declarationOf(Object.class);
        Variable<Toy>    done   = declarationOf(Toy.class);

        Rule rule = rule("veto-only-different-types-reversed").build(
            sequence(
                pattern(first).expr("isS", t -> t.getName().equals("s")),
                absent(pattern(vetoer).expr("isS2", o -> o instanceof Toy && ((Toy) o).getName().equals("s"))),
                pattern(done).expr("isD", t -> t.getName().equals("d"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("s"));
        insertAndFire(new Toy("s"));
        insertAndFire(new Toy("d"));

        assertThat(results).as("veto only: step-0 on Toy, veto pattern on Object").isEmpty();
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void vetoingFactDoesNotRestartSequenceVetoTypeAdapterDeliversBeforeStepZeroTypeAdapter() {
        // This test locks down a decision that the same fact can not veto and activate the same sequence
        Variable<Object> anyObject = declarationOf(Object.class);
        Variable<Toy>    first     = declarationOf(Toy.class);
        Variable<Object> vetoer    = declarationOf(Object.class);
        Variable<Toy>    done      = declarationOf(Toy.class);

        Rule creatorOfObjectNode = rule("creates-object-otn").build(
            pattern(anyObject),
            execute(() -> {})
        );
        Rule rule = rule("veto-only-veto-adapter-first").build(
            sequence(
                pattern(first).expr("isS", t -> t.getName().equals("s")),
                absent(pattern(vetoer).expr("isS2", o -> o instanceof Toy && ((Toy) o).getName().equals("s"))),
                pattern(done).expr("isD", t -> t.getName().equals("d"))
            ),
            execute(() -> results.add("fired"))
        );

        final Model model = new ModelImpl().addRule(creatorOfObjectNode).addRule(rule);
        ksession = KieBaseBuilder.createKieBaseFromModel(model).newKieSession();
        ksession.fireAllRules();
        insertAndFire(new Toy("s"));
        insertAndFire(new Toy("s"));
        insertAndFire(new Toy("d"));

        assertThat(results)
                .as("veto only: the vetoing fact must not start a new attempt via a later object-type adapter")
                .isEmpty();
    }



    private Rule buildVetoOnlyAnchoredRule(Variable<Person> person,
                                           Variable<Toy> first,
                                           Variable<Toy> vetoer,
                                           Variable<Toy> done) {
        return rule("veto-only-anchored").build(
            pattern(person),
            sequence(
                pattern(first).expr("isS", t -> t.getName().equals("s"))
                              .expr("sOwnedByAnchor", person, (t, p) -> p.getName().equals(t.getOwner())),
                absent(pattern(vetoer).expr("isS2", t -> t.getName().equals("s"))
                                      .expr("vetoOwnedByAnchor", person, (t, p) -> p.getName().equals(t.getOwner()))),
                pattern(done).expr("isD", t -> t.getName().equals("d"))
            ),
            on(person).execute(p -> results.add("fired:" + p.getName()))
        );
    }

    private static Toy ownedToy(String name, String owner) {
        Toy toy = new Toy(name);
        toy.setOwner(owner);
        return toy;
    }

    @AfterEach
    public void tearDown() {
        results.clear();
        if (ksession != null) { ksession.dispose(); }
    }

    private void insertAndFire(Object... facts) {
        for (Object fact : facts) { ksession.insert(fact); }
        ksession.fireAllRules();
    }

    private KieSession makeKSession(Rule rule) {
        final Model model = new ModelImpl().addRule(rule);
        final KieBase kieBase = KieBaseBuilder.createKieBaseFromModel(model);
        final KieSession ks = kieBase.newKieSession();
        ks.fireAllRules();
        return ks;
    }
}
