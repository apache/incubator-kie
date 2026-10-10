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
import org.drools.modelcompiler.domain.Toy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.drools.model.DSL.declarationOf;
import static org.drools.model.DSL.execute;
import static org.drools.model.DSL.not;
import static org.drools.model.PatternDSL.nor;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.xor;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;

/**
 * Tests for the continuous absence guard inside sequence().
 *
 * Contract: a not(P) or nor(P) step activates a live filter adapter.
 * Any matching P inserted AFTER activation and BEFORE the following positive
 * step vetoes the sequence — the rule must not fire.
 */
public class PatternDSLSequenceNotStepTest {

    private final Variable<Toy>    toy     = declarationOf(Toy.class);
    private final Variable<Toy>    blocker = declarationOf(Toy.class);
    private final List<String>     results = new ArrayList<>();
    private KieSession             ksession;

    @Test
    public void notStepAdvancesWhenNoBlocker() {
        // sequence: ball → not(blocker) → bat
        // No blocker ever inserted → rule fires.
        ksession = makeKSession(buildThreeStepRule());
        insertAndFire(new Toy("ball"));
        insertAndFire(new Toy("bat"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void notStepVetoesWhenBlockerArrivesAfterActivation() {
        // sequence: ball → not(blocker) → bat
        // Blocker inserted AFTER ball (i.e., after the absence guard activates)
        // but BEFORE bat → rule must NOT fire.
        ksession = makeKSession(buildThreeStepRule());
        insertAndFire(new Toy("ball"));              // step 1: positive, activates absence guard
        insertAndFire(new Toy("blocker-toy"));       // fires into absence guard → veto
        insertAndFire(new Toy("bat"));               // step 3 never activates
        assertThat(results).isEmpty();
    }

    @Test
    public void notStepIgnoresBlockerInsertedBeforeStepActivated() {
        // sequence: ball → not(blocker) → bat
        // Blocker is already in WM when ball advances the sequence to the not-step.
        // Arrival order governs: the blocker arrived before this step was active, so it is
        // invisible to the absence guard — the rule must fire.
        ksession = makeKSession(buildThreeStepRule());
        insertAndFire(new Toy("blocker-toy"), new Toy("ball")); // blocker present before guard activates
        insertAndFire(new Toy("bat"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void norStepBehavesLikeNotStep() {
        // nor(P) is an alias for not(P) at standalone step position.
        // sequence: ball → nor(blocker) → bat
        // Blocker inserted after activation → rule must NOT fire.
        Variable<Toy> norBlocker = declarationOf(Toy.class);

        Rule rule = rule("nor-guard").build(
            sequence(
                pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                nor(pattern(norBlocker).expr("isBlocker", t -> t.getName().equals("blocker-toy"))),
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
    public void notStepInMiddleVetoesSequence() {
        // sequence: A → not(blocker) → B → C
        // Blocker arrives between A and B → rule must NOT fire.
        Variable<Toy> midBlocker = declarationOf(Toy.class);
        Variable<Toy> toyB       = declarationOf(Toy.class);
        Variable<Toy> toyC       = declarationOf(Toy.class);

        Rule rule = rule("not-in-middle").build(
            sequence(
                pattern(toy).expr("isA", t -> t.getName().equals("A")),
                not(pattern(midBlocker).expr("isBlocker", t -> t.getName().equals("blocker-toy"))),
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
    public void twoConsecutiveNotGuards_bothMustBeAbsent_fires() {
        // sequence(not(blockerA), not(blockerB), done):
        // Both absence guards are active while waiting for "done".
        // Neither blocker inserted → rule fires.
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);

        Rule rule = rule("two-not-guards-fires").build(
            sequence(
                not(pattern(blockerA).expr("isBlockerA", t -> t.getName().equals("blockerA"))),
                not(pattern(blockerB).expr("isBlockerB", t -> t.getName().equals("blockerB"))),
                pattern(toy).expr("isDone", t -> t.getName().equals("done"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Toy("done")); // neither blocker ever appeared → rule fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void twoConsecutiveNotGuards_firstBlockerVetoes() {
        // sequence(start, not(blockerA), not(blockerB), done):
        // start advances the sequence to the not-guards step.
        // blockerA inserted after start (while the not-guards step is active) → veto + reset.
        // Rule must not fire: done arrives but the sequence is back at step 0, waiting for start again.
        Variable<Toy> toyStart  = declarationOf(Toy.class);
        Variable<Toy> blockerA  = declarationOf(Toy.class);
        Variable<Toy> blockerB  = declarationOf(Toy.class);
        Variable<Toy> toyDone   = declarationOf(Toy.class);

        Rule rule = rule("two-not-guards-first-vetoes").build(
            sequence(
                pattern(toyStart).expr("isStart",   t -> t.getName().equals("start")),
                not(pattern(blockerA).expr("isBlockerA", t -> t.getName().equals("blockerA"))),
                not(pattern(blockerB).expr("isBlockerB", t -> t.getName().equals("blockerB"))),
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
    public void twoConsecutiveNotGuards_secondBlockerVetoes() {
        // sequence(start, not(blockerA), not(blockerB), done):
        // start advances to the not-guards step.
        // blockerB inserted after start → veto + reset.
        // Rule must not fire: done arrives but the sequence is back at step 0.
        Variable<Toy> toyStart  = declarationOf(Toy.class);
        Variable<Toy> blockerA  = declarationOf(Toy.class);
        Variable<Toy> blockerB  = declarationOf(Toy.class);
        Variable<Toy> toyDone   = declarationOf(Toy.class);

        Rule rule = rule("two-not-guards-second-vetoes").build(
            sequence(
                pattern(toyStart).expr("isStart",   t -> t.getName().equals("start")),
                not(pattern(blockerA).expr("isBlockerA", t -> t.getName().equals("blockerA"))),
                not(pattern(blockerB).expr("isBlockerB", t -> t.getName().equals("blockerB"))),
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
        return rule("not-guard").build(
            sequence(
                pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                not(pattern(blocker).expr("isBlocker", t -> t.getName().equals("blocker-toy"))),
                pattern(toy).expr("isBat", t -> t.getName().equals("bat"))
            ),
            execute(() -> results.add("fired"))
        );
    }

    @Test
    public void trailingNorWithoutCompleteWithinIsRejected() {
        Variable<Toy> toyA = declarationOf(Toy.class);
        Variable<Toy> toyB = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("trailing-nor").build(
                sequence(
                    pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                    nor(
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
    public void trailingNotWithoutCompleteWithinIsRejected() {
        Variable<Toy> toyV = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("trailing-not").build(
                sequence(
                    pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                    not(pattern(toyV).expr("isBlocker", t -> t.getName().equals("blocker-toy")))
                ),
                execute(() -> {})
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("trailing");
    }

    @Test
    public void standaloneNotIsRejected() {
        Variable<Toy> toyV = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("standalone-not").build(
                sequence(not(pattern(toyV).expr("isBlocker", t -> t.getName().equals("blocker-toy")))),
                execute(() -> {})
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("trailing");
    }

    @Test
    public void norTwoPatternsBothAbsent_fires() {
        // nor(blockerA, blockerB): neither inserted → rule fires
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);

        Rule rule = rule("nor-both-absent").build(
            sequence(
                pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                nor(
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
    public void norTwoPatternsFirstVetoes() {
        // nor(blockerA, blockerB): blockerA inserted after ball → veto
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);

        Rule rule = rule("nor-first-vetoes").build(
            sequence(
                pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                nor(
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
    public void norTwoPatternsSecondVetoes() {
        // nor(blockerA, blockerB): blockerB inserted after ball → veto
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);

        Rule rule = rule("nor-second-vetoes").build(
            sequence(
                pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                nor(
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
    public void norTwoPatternsBlockerAlreadyPresent_ignored() {
        // nor(blockerA, blockerB): blockerA is in WM before ball advances to the nor-step.
        // Arrival order governs: the blocker arrived before this step was active, so it is
        // invisible to the absence guard — the rule must fire.
        Variable<Toy> blockerA = declarationOf(Toy.class);
        Variable<Toy> blockerB = declarationOf(Toy.class);

        Rule rule = rule("nor-preexisting").build(
            sequence(
                pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                nor(
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
    public void trailingXorWithoutCompleteWithinIsRejected() {
        Variable<Toy> toyA = declarationOf(Toy.class);
        Variable<Toy> toyB = declarationOf(Toy.class);

        assertThatThrownBy(() ->
            rule("trailing-xor").build(
                sequence(
                    pattern(toy).expr("isBall", t -> t.getName().equals("ball")),
                    xor(
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
     * Regression test for: not() re-vetoes on a pre-existing blocker after a sequence reset.
     *
     * sequence: ball → not(blocker-toy) → bat
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
    public void notStepDoesNotRevetoOnPreExistingBlockerAfterReset() {
        ksession = makeKSession(buildThreeStepRule());

        // Run 1: advance to not-step, then veto.
        insertAndFire(new Toy("ball"));
        FactHandle blockerHandle = ksession.insert(new Toy("blocker-toy"));
        ksession.fireAllRules(); // veto fires, sequence resets to step 0

        // Run 2: advance to not-step again — blocker is still in WM.
        insertAndFire(new Toy("ball"));

        // Now retract the blocker and complete the sequence.
        // With the bug: activate() already re-vetoed on the second ball insert, so
        // the sequence is back at step 0 and bat never satisfies the not-step — rule silent.
        // With the fix: activate() did not pre-check WM, sequence is at the not-step,
        // retract clears the live absence filter, and bat completes the sequence.
        ksession.retract(blockerHandle);
        ksession.fireAllRules();
        insertAndFire(new Toy("bat"));

        assertThat(results)
                .as("rule should fire once after blocker retracted and bat inserted on second run")
                .containsExactly("fired");
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
