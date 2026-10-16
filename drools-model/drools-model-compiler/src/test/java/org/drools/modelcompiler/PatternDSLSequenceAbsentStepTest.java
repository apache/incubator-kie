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
import static org.drools.model.PatternDSL.oneOf;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.on;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;

/**
 * Tests for the continuous absence guard inside sequence().
 *
 * Contract: an absent(P) step activates a live filter adapter.
 * Any matching P inserted AFTER activation and BEFORE the following positive
 * step vetoes the sequence — the rule must not fire.
 */
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
    public void absentSinglePatternVetoesLikeNotStep() {
        // absent(P) at standalone step position vetoes when P arrives.
        // sequence: ball → absent(blocker) → bat
        // Blocker inserted after activation → rule must NOT fire.
        Variable<Toy> absentBlocker = declarationOf(Toy.class);

        Rule rule = rule("absent-guard").build(
            sequence(
                pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                absent(pattern(absentBlocker).expr("isBlocker", t -> t.getName().equals("blocker-toy"))),
                pattern(toy).expr("isBat", t -> t.getName().equals("bat"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("ball"));
        insertAndFire(new Toy("blocker-toy"));  // veto
        insertAndFire(new Toy("bat"));
        assertThat(results).isEmpty();
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
    public void twoConsecutiveAbsentGuards_bothMustBeAbsent_fires() {
        // sequence(absent(blockerA), absent(blockerB), done):
        // Both absence guards are active while waiting for "done".
        // Neither blocker inserted → rule fires.
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);

        Rule rule = rule("two-absent-guards-fires").build(
            sequence(
                absent(pattern(blockerA).expr("isBlockerA", t -> t.getName().equals("blockerA"))),
                absent(pattern(blockerB).expr("isBlockerB", t -> t.getName().equals("blockerB"))),
                pattern(toy).expr("isDone", t -> t.getName().equals("done"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("done")); // neither blocker ever appeared → rule fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void twoConsecutiveAbsentGuards_firstBlockerVetoes() {
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
    public void twoConsecutiveAbsentGuards_secondBlockerVetoes() {
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

    // ---- helper: three-step rule used by the first three tests ----

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
    public void absentTwoPatternsBothAbsent_fires() {
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
    public void absentTwoPatternsBlockerAlreadyPresent_ignored() {
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
    public void wrappedTrailingAbsent_throws() {
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
    public void trailingOneOfWithoutCompleteWithinIsRejected() {
        Variable<Toy> toyA = declarationOf(Toy.class);
        Variable<Toy> toyB = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("trailing-oneOf").build(
                sequence(
                    pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                    oneOf(
                        pattern(toyA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                        pattern(toyB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                    )
                ),
                execute(() -> {})
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("trailing");
    }

    /**
     * Regression test for: absent() re-vetoes on a pre-existing blocker after a sequence reset.
     *
     * sequence: ball → absent(blocker-toy) → bat
     *
     * Run 1: insert ball (advance), insert blocker-toy (veto → reset to step 0) — OK.
     * Run 2: insert ball again (advance to step 1). The blocker-toy is still in WM.
     *        Bug: LogicCircuitStep.activate() finds the old fact and fires the veto
     *        immediately, resetting back to step 0 without waiting for any new insert.
     *        When the blocker is then retracted and bat is inserted, the sequence is at
     *        step 0 (not step 1), so it never reaches the consequence — rule does not fire.
     *
     * Expected (fix): activate() ignores pre-existing WM facts. After retract + bat,
     *                 the rule fires.
     * Actual   (bug): activate() re-vetoes silently. Sequence resets to step 0. Rule
     *                 does not fire even after retract + bat.
     */
    @Test
    public void absentStepDoesNotRevetoOnPreExistingBlockerAfterReset() {
        ksession = makeKSession(buildThreeStepRule());

        // Run 1: advance to absent-step, then veto.
        insertAndFire(new Toy("ball"));
        FactHandle blockerHandle = ksession.insert(new Toy("blocker-toy"));
        ksession.fireAllRules(); // veto fires, sequence resets to step 0

        // Run 2: advance to absent-step again — blocker is still in WM.
        insertAndFire(new Toy("ball"));

        // Now retract the blocker and complete the sequence.
        // With the bug: activate() already re-vetoed on the second ball insert, so
        // the sequence is back at step 0 and bat never satisfies the absent-step — rule silent.
        // With the fix: activate() did not pre-check WM, sequence is at the absent-step,
        // retract clears the live absence filter, and bat completes the sequence.
        ksession.retract(blockerHandle);
        ksession.fireAllRules();
        insertAndFire(new Toy("bat"));

        assertThat(results)
                .as("rule should fire once after blocker retracted and bat inserted on second run")
                .containsExactly("fired");
    }

    /**
     * A veto reset with several anchors must terminate (tkobayas, "multiple-anchors").
     *
     * rule: anchor : String(startsWith "anchor"), sequence(absent("x"), "d")
     *
     * Each anchor starts a sequence instance with a live absence adapter on "x". Vetoing an
     * instance resets it to step 0, which re-registers its adapter on the filter that is still
     * delivering "x". That re-registered adapter must not see the same "x" again.
     */
    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void vetoResetWithMultipleAnchorsTerminatesAndEachAnchorStillCompletes() {
        assertVetoResetWithAnchors("anchor1", "anchor2");
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void vetoResetWithThreeAnchorsResetsEachOnceAndEachStillCompletes() {
        assertVetoResetWithAnchors("anchor1", "anchor2", "anchor3");
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void vetoResetWithFourAnchorsResetsEachOnceAndEachStillCompletes() {
        assertVetoResetWithAnchors("anchor1", "anchor2", "anchor3", "anchor4");
    }

    private void assertVetoResetWithAnchors(String... anchors) {
        Variable<String> anchor = declarationOf(String.class);
        Variable<String> veto   = declarationOf(String.class);
        Variable<String> done   = declarationOf(String.class);

        Rule rule = rule("multiple-anchors").build(
            pattern(anchor).expr("isAnchor", s -> s.startsWith("anchor")),
            sequence(
                absent(pattern(veto).expr("isX", s -> s.equals("x"))),
                pattern(done).expr("isD", s -> s.equals("d"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire((Object[]) anchors);   // one sequence instance per anchor, each guarding against "x"
        insertAndFire("x");                  // veto every instance; must return
        insertAndFire("d");                  // each reset instance still completes normally

        assertThat(results).hasSize(anchors.length);
    }

    /**
     * Beta branch of DynamicFilter.assertObject: the absence expr references the anchor, so
     * getRequiredDeclarations().length != 0. The blocker matches every anchor, so every adapter
     * on the shared filter vetoes and re-registers; delivery must still terminate.
     */
    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void betaAbsenceVetoResetWithMultipleAnchorsTerminates() {
        Variable<Person> person  = declarationOf(Person.class);
        Variable<Toy>    blocker = declarationOf(Toy.class);
        Variable<Toy>    done    = declarationOf(Toy.class);

        Rule rule = rule("beta-multiple-anchors").build(
            pattern(person),
            sequence(
                absent(pattern(blocker).expr("blockerForAnyAnchor", person,
                        (t, p) -> p.getName() != null && t.getName().equals("blocker-toy"))),
                pattern(done).expr("isDone", t -> t.getName().equals("done"))
            ),
            on(person).execute(p -> results.add("fired:" + p.getName()))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("alice"), new Person("bob"));
        insertAndFire(new Toy("blocker-toy"));   // vetoes both instances; must return
        insertAndFire(new Toy("done"));          // each reset instance still completes

        assertThat(results).containsExactlyInAnyOrder("fired:alice", "fired:bob");
    }

    /**
     * Beta branch: the blocker must veto only the anchor it correlates with.
     */
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
    public void absentThreePatternsBothAbsent_fires() {
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
        ks.fireAllRules(); // process InitialFact so the sequence is started before test facts arrive
        return ks;
    }
}
