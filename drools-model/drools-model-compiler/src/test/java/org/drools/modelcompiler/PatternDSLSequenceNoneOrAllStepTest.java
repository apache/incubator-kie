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
import org.drools.model.PatternDSL;
import org.drools.model.Rule;
import org.drools.model.Variable;
import org.drools.model.impl.ModelImpl;
import org.drools.modelcompiler.domain.Person;
import org.drools.modelcompiler.domain.Toy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.drools.model.DSL.declarationOf;
import static org.drools.model.DSL.execute;
import static org.drools.model.PatternDSL.absent;
import static org.drools.model.PatternDSL.and;
import static org.drools.model.PatternDSL.or;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;
import static org.drools.model.PatternDSL.noneOrAll;
import static org.drools.model.PatternDSL.oneOf;

/**
 * Specification tests for noneOrAll() as a sequence step.
 *
 * Contract: zero or all of the noneOrAll() children must match before the positive trigger.
 * Write {@code sequence(..., noneOrAll(a, b), trigger)} — the compiler wraps the pair into
 * {@code and(noneOrAll(a, b), trigger)} automatically.
 */
public class PatternDSLSequenceNoneOrAllStepTest {

    private final Variable<Person> person = declarationOf(Person.class);
    private final Variable<Toy>    toy    = declarationOf(Toy.class);
    private final List<String>     results = new ArrayList<>();
    private KieSession             ksession;

    @Test
    public void noneOrAllNeitherMatch_fires() {
        // sequence(noneOrAll(alarmA, alarmB), ack): neither alarm inserted (0 matches) → XNOR matched → ack arrives → fires
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xnor-none").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                noneOrAll(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                ),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("ack")); // ack arrives with 0 alarm matches → fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void noneOrAllOnlyOneMatch_doesNotFire() {
        // sequence(noneOrAll(alarmA, alarmB), ack): exactly one alarm inserted → XNOR unmatched → ack arrives → does not fire
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xnor-one").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                noneOrAll(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                ),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // exactly one → XNOR transitions to unmatched
        insertAndFire(new Toy("ack"));    // ack arrives, but XNOR is unmatched → does not fire
        assertThat(results).isEmpty();
    }

    @Test
    public void noneOrAllBothMatch_fires() {
        // sequence(noneOrAll(alarmA, alarmB), ack): both alarms inserted → XNOR matched → ack arrives → fires
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xnor-both").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                noneOrAll(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                ),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // 1 match → unmatched
        insertAndFire(new Toy("alarmB")); // 2 matches → MATCHED again!
        insertAndFire(new Toy("ack"));    // ack arrives, XNOR matched → fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void noneOrAllBareTopLevel_throws() {
        // sequence(noneOrAll(a, b)) — bare XNOR as the trailing step is rejected during rule.build()
        // by ViewPatternBuilder's trailing-step guard.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        assertThatThrownBy(() -> rule("xnor-bare").build(
            pattern(person),
            sequence(
                noneOrAll(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                )
            ),
            execute(() -> results.add("fired"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing absent()/oneOf()/noneOrAll() requires a following positive step");
    }

    @Test
    public void noneOrAllBareFollowedByStep_sugarFiresOnNeitherMatch() {
        // sequence(noneOrAll(a, b), c) — same rule, different name to confirm rewrite is idempotent.
        // Neither alarm inserted (0 matches) → XNOR matched → ack arrives → fires.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xnor-bare-followed-fires").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                noneOrAll(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                ),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("ack")); // ack arrives with 0 alarm matches → fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void noneOrAllBareFollowedByStep_sugarDoesNotFireOnOneMatch() {
        // sequence(noneOrAll(a, b), c) sugar: exactly one alarm → XNOR unmatched → ack arrives but no fire.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xnor-bare-followed-no-fire").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                noneOrAll(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                ),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // exactly one → XNOR transitions to unmatched
        insertAndFire(new Toy("ack"));    // ack arrives but XNOR unmatched → does not fire
        assertThat(results).isEmpty();
    }

    private Rule buildNestedXnorRule(String ruleName) {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);

        return rule(ruleName).build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                noneOrAll(
                    noneOrAll(
                        pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                        pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                    ),
                    pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC"))
                ),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );
    }

    @Test
    public void nestedXnor_onlyFinalTriggerInserted_doesNotFire() {
        // Circuit: and(noneOrAll(noneOrAll(alarmA, alarmB), alarmC), ack)
        // Neither alarmA nor alarmB inserted -> inner XNOR matches (0 matches out of 2 is true for XNOR)
        // Outer XNOR receives signal from inner XNOR, so outer XNOR has 1 match (inner XNOR) and 1 unmatch (alarmC).
        // 1 match out of 2 is false for outer XNOR -> outer XNOR is UNMATCHED.
        // Therefore, when ack arrives, the sequence must NOT fire.
        ksession = makeKSession(buildNestedXnorRule("nested-xnor-no-alarms"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("ack")); // ack arrives with only trigger, nested XNOR should be false -> does not fire
        assertThat(results).isEmpty();
    }

    @Test
    public void nestedXnor_onlyAlarmCInserted_fires() {
        // Circuit: and(noneOrAll(noneOrAll(alarmA, alarmB), alarmC), ack)
        // Inner XNOR matches on activation (0 out of 2 matched).
        // alarmC is inserted -> outer XNOR now has 2 matches out of 2 (inner XNOR + alarmC) -> MATCHED!
        // When ack arrives, the rule fires.
        ksession = makeKSession(buildNestedXnorRule("nested-xnor-alarmC-only"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmC")); // outer XNOR has inner=true and alarmC=true -> 2 of 2 matched
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void nestedXnor_allAlarmsInserted_fires() {
        // Circuit: and(noneOrAll(noneOrAll(alarmA, alarmB), alarmC), ack)
        // Both alarmA and alarmB inserted -> inner XNOR matches (2 out of 2 matched).
        // alarmC is inserted -> outer XNOR has 2 matches out of 2 (inner XNOR + alarmC) -> MATCHED!
        // When ack arrives, the rule fires.
        ksession = makeKSession(buildNestedXnorRule("nested-xnor-all-alarms"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("alarmB"));
        insertAndFire(new Toy("alarmC"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void nestedXnor_bothInnerAlarmsInserted_doesNotFire() {
        // Circuit: and(noneOrAll(noneOrAll(alarmA, alarmB), alarmC), ack)
        // Both alarmA and alarmB inserted -> inner XNOR matches (2 out of 2 matched).
        // alarmC is NOT inserted -> outer XNOR has 1 match out of 2 (inner XNOR matched, alarmC not) -> UNMATCHED!
        // When ack arrives, the rule must NOT fire.
        ksession = makeKSession(buildNestedXnorRule("nested-xnor-inner-both-alarms"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("alarmB"));
        insertAndFire(new Toy("ack"));
        assertThat(results).isEmpty();
    }

    @Test
    public void nestedXnor_oneInnerAlarmAndAlarmCInserted_doesNotFire() {
        // Circuit: and(noneOrAll(noneOrAll(alarmA, alarmB), alarmC), ack)
        // Exactly alarmA inserted -> inner XNOR is UNMATCHED (1 out of 2 matched).
        // alarmC is inserted -> outer XNOR has 1 match out of 2 (inner XNOR unmatched, alarmC matched) -> UNMATCHED!
        // When ack arrives, the rule must NOT fire.
        ksession = makeKSession(buildNestedXnorRule("nested-xnor-one-inner-and-alarmC"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("alarmC"));
        insertAndFire(new Toy("ack"));
        assertThat(results).isEmpty();
    }

    @Test
    public void nestedXnor_onlyInnerAlarmA_fires() {
        // Circuit: and(noneOrAll(noneOrAll(alarmA, alarmB), alarmC), ack)
        // Inner XNOR matches vacuously on activation and feeds the outer XNOR (1 of 2 -> UNMATCHED).
        // alarmA arrives -> inner XNOR reverts (1 of 2). The outer XNOR now sees 0 of 2 inputs
        // matched, which noneOrAll() defines as MATCHED, so ack must fire the rule.
        // Contract: a reverting child must make its parent re-evaluate its predicate,
        // not merely force the parent to UNMATCHED.
        ksession = makeKSession(buildNestedXnorRule("nested-xnor-alarmA-only"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("ack"));
        assertThat(results)
                .as("outer xnor must re-evaluate to MATCHED (0 of 2) after inner xnor reverts")
                .containsExactly("fired");
    }

    // ---------------------------------------------------------------------------------------------
    // Deeper nesting: noneOrAll(noneOrAll(noneOrAll(a, b), c), d)
    // ---------------------------------------------------------------------------------------------

    private Rule buildTripleNestedXnorRule(String ruleName) {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmD = declarationOf(Toy.class);

        return rule(ruleName).build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                noneOrAll(
                    noneOrAll(
                        noneOrAll(toyNamed(alarmA, "alarmA"), toyNamed(alarmB, "alarmB")),
                        toyNamed(alarmC, "alarmC")
                    ),
                    toyNamed(alarmD, "alarmD")
                ),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );
    }

    @Test
    public void tripleNestedXnor_noAlarms_fires() {
        // level1 = noneOrAll(A,B)      : 0 of 2 -> MATCHED
        // level2 = noneOrAll(level1,C) : level1 only -> 1 of 2 -> UNMATCHED
        // level3 = noneOrAll(level2,D) : 0 of 2 -> MATCHED
        ksession = makeKSession(buildTripleNestedXnorRule("triple-xnor-none"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void tripleNestedXnor_onlyAlarmD_doesNotFire() {
        // level3 = noneOrAll(level2,D): level2 UNMATCHED, D matched -> 1 of 2 -> UNMATCHED
        ksession = makeKSession(buildTripleNestedXnorRule("triple-xnor-D"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmD"));
        insertAndFire(new Toy("ack"));
        assertThat(results).isEmpty();
    }

    @Test
    public void tripleNestedXnor_alarmCAndD_fires() {
        // level1 MATCHED, C matched -> level2 2 of 2 MATCHED; D matched -> level3 2 of 2 MATCHED
        ksession = makeKSession(buildTripleNestedXnorRule("triple-xnor-C-D"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmC"));
        insertAndFire(new Toy("alarmD"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void tripleNestedXnor_oneInnermostAlarm_revertsThroughAllLevels() {
        // alarmA alone: level1 UNMATCHED (1 of 2); level2 = noneOrAll(level1,C) -> 0 of 2 -> MATCHED;
        // level3 = noneOrAll(level2,D) -> 1 of 2 -> UNMATCHED. Must not fire.
        ksession = makeKSession(buildTripleNestedXnorRule("triple-xnor-A"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("ack"));
        assertThat(results).isEmpty();
    }

    @Test
    public void tripleNestedXnor_alarmAAndD_fires() {
        // level1 UNMATCHED; level2 = noneOrAll(level1,C) 0 of 2 -> MATCHED; level3 = noneOrAll(level2,D) 2 of 2 -> MATCHED
        ksession = makeKSession(buildTripleNestedXnorRule("triple-xnor-A-D"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("alarmD"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    // ---------------------------------------------------------------------------------------------
    // xnor inside and/or/xor
    // ---------------------------------------------------------------------------------------------

    private Rule buildXnorInsideAndRule(String ruleName) {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        return rule(ruleName).build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                and(
                    noneOrAll(toyNamed(alarmA, "alarmA"), toyNamed(alarmB, "alarmB")),
                    pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
                )
            ),
            execute(() -> results.add("fired"))
        );
    }

    @Test
    public void noneOrAllInsideExplicitAnd_neitherMatch_fires() {
        ksession = makeKSession(buildXnorInsideAndRule("and-xnor-none"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void noneOrAllInsideExplicitAnd_oneMatch_doesNotFire() {
        ksession = makeKSession(buildXnorInsideAndRule("and-xnor-one"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("ack"));
        assertThat(results).isEmpty();
    }

    @Test
    public void noneOrAllInsideExplicitAnd_bothMatch_fires() {
        ksession = makeKSession(buildXnorInsideAndRule("and-xnor-both"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("alarmB"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void andXnorRecovery_xnorOscillatesThenSiblingArrives_fires() {
        // Circuit: and(noneOrAll(alarmA, alarmB), ack)
        // Event order: alarmA, ack, alarmB
        //
        // Step activates: XNOR is MATCHED (0 of 2). AND: xnor-bit=1, ack-bit=0.
        // alarmA: XNOR → UNMATCHED (1 of 2). clearParentBit → AND xnor-bit=0.
        //   AND does not fire. No propagation up.
        // ack: AND sets ack-bit=1. xnor-bit=0 → AND does NOT fire yet.
        // alarmB: XNOR → MATCHED (2 of 2). Re-propagates to AND.
        //   AND: xnor-bit=1, ack-bit=1 → both bits set → AND fires → rule fires (count=1).
        //
        // Bug: clearParentBit from XNOR reversion also clears the AND's ack-bit (sibling input),
        // so when alarmB re-sets xnor-bit the ack-bit is gone → AND never fires.
        // Expected: 1. Current behaviour: 0.
        ksession = makeKSession(buildXnorInsideAndRule("and-xnor-recovery-xnor-first"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // XNOR reverts → AND loses xnor-bit; ack-bit still 0
        insertAndFire(new Toy("ack"));    // AND gains ack-bit; xnor-bit=0 → no fire yet
        insertAndFire(new Toy("alarmB")); // XNOR recovers → AND gains xnor-bit; both bits → fires
        assertThat(results)
                .as("AND must retain ack-bit across XNOR oscillation and fire when XNOR re-matches")
                .containsExactly("fired");
    }

    @Test
    public void andAndXnorRecovery_xnorOscillatesThenSiblingsArrive_fires() {
        // Circuit: and(and(noneOrAll(alarmA, alarmB), flagC), flagD)
        // Event order: alarmA, flagC, alarmB, flagD
        //
        // Step activates: inner XNOR MATCHED (0 of 2).
        //   Inner AND: xnor-bit=1, c-bit=0. Outer AND: inner-bit=0, d-bit=0.
        // alarmA: inner XNOR → UNMATCHED. clearParentBit → inner AND xnor-bit=0.
        //   Inner AND does not fire. No propagation to outer AND.
        // flagC: inner AND gains c-bit=1. xnor-bit=0 → inner AND does NOT fire yet.
        // alarmB: inner XNOR → MATCHED. Re-propagates to inner AND.
        //   Inner AND: xnor-bit=1, c-bit=1 → fires. Outer AND inner-bit=1.
        // flagD: outer AND gains d-bit=1; inner-bit=1 → outer AND fires → rule fires (count=1).
        //
        // Bug: clearParentBit from inner XNOR reversion also clears the inner AND's c-bit,
        // so when alarmB re-sets xnor-bit the c-bit is gone → inner AND never fires →
        // outer AND inner-bit never set → flagD alone can't fire outer AND → count=0.
        // Expected: 1. Current behaviour: 0.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> flagC  = declarationOf(Toy.class);
        Variable<Toy> flagD  = declarationOf(Toy.class);

        Rule rule = rule("and-and-xnor-recovery-oscillate-first").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                and(
                    and(
                        noneOrAll(toyNamed(alarmA, "alarmA"), toyNamed(alarmB, "alarmB")),
                        toyNamed(flagC, "flagC")
                    ),
                    toyNamed(flagD, "flagD")
                )
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // inner XNOR reverts → inner AND loses xnor-bit
        insertAndFire(new Toy("flagC"));  // inner AND gains c-bit; xnor-bit=0 → no fire yet
        insertAndFire(new Toy("alarmB")); // inner XNOR recovers → inner AND: both bits → fires; outer AND inner-bit=1
        insertAndFire(new Toy("flagD"));  // outer AND gains d-bit; inner-bit=1 → fires
        assertThat(results)
                .as("nested AND must retain flagC-bit across XNOR oscillation and fire when all inputs resolve")
                .containsExactly("fired");
    }

    private Rule buildXnorInsideOrRule(String ruleName) {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> other  = declarationOf(Toy.class);

        return rule(ruleName).build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                or(
                    noneOrAll(toyNamed(alarmA, "alarmA"), toyNamed(alarmB, "alarmB")),
                    toyNamed(other, "other")
                ),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );
    }

    @Test
    public void noneOrAllInsideOr_noneOrAllMatchesOnActivation_stepAdvancesAndFires() {
        // or(noneOrAll(A,B), other): xnor is MATCHED on activation (0 of 2) -> OR matched -> step advances.
        ksession = makeKSession(buildXnorInsideOrRule("or-xnor-none"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void noneOrAllInsideOr_noneOrAllRevertsAfterOrAlreadyAdvanced_stillFires() {
        // The OR has already matched via the xnor branch when the step activated, so the step has
        // advanced to ack. A later alarmA reverting the xnor must not un-advance the sequence.
        ksession = makeKSession(buildXnorInsideOrRule("or-xnor-reverts-late"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    private Rule buildXnorInsideXorRule(String ruleName) {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);

        return rule(ruleName).build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                oneOf(
                    noneOrAll(toyNamed(alarmA, "alarmA"), toyNamed(alarmB, "alarmB")),
                    toyNamed(alarmC, "alarmC")
                ),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );
    }

    @Test
    public void noneOrAllInsideOneOf_noAlarms_fires() {
        // noneOrAll(A,B) MATCHED (0 of 2), C unmatched -> exactly one -> xor MATCHED.
        ksession = makeKSession(buildXnorInsideXorRule("xor-xnor-none"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void noneOrAllInsideOneOf_onlyAlarmC_doesNotFire() {
        // noneOrAll(A,B) MATCHED, C matched -> two of two -> xor UNMATCHED.
        ksession = makeKSession(buildXnorInsideXorRule("xor-xnor-C"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmC"));
        insertAndFire(new Toy("ack"));
        assertThat(results).isEmpty();
    }

    @Test
    public void noneOrAllInsideOneOf_onlyAlarmA_doesNotFire() {
        // noneOrAll(A,B) reverts to UNMATCHED (1 of 2), C unmatched -> zero -> xor UNMATCHED.
        ksession = makeKSession(buildXnorInsideXorRule("xor-xnor-A"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("ack"));
        assertThat(results).isEmpty();
    }

    @Test
    public void noneOrAllInsideOneOf_alarmAAndC_fires() {
        // noneOrAll(A,B) reverts to UNMATCHED, C matched -> exactly one -> xor MATCHED.
        ksession = makeKSession(buildXnorInsideXorRule("xor-xnor-A-C"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("alarmC"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void noneOrAllInsideOneOf_alarmAThenB_fires() {
        // A: xnor reverts to UNMATCHED, so xor sees zero matched inputs.
        // B: xnor re-matches (2 of 2), so xor sees exactly one (xnor) -> MATCHED -> fires.
        ksession = makeKSession(buildXnorInsideXorRule("xor-xnor-A-B"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("alarmB"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    // ---------------------------------------------------------------------------------------------
    // xor inside xnor: noneOrAll(oneOf(A,B), C)
    //
    // Outer XNOR receives the XOR gate state as one input and pattern C as the other.
    // XNOR fires when both children are in the same state (both matched or both unmatched).
    //
    // XOR(A,B) state transitions:
    //   no A, no B → UNMATCHED (0 of 2)
    //   exactly one → MATCHED   (1 of 2)
    //   both        → UNMATCHED (XOR reverts on over-match)
    //
    // XNOR(xor, C) is MATCHED when |matched inputs| ∈ {0, 2}.
    // ---------------------------------------------------------------------------------------------

    private Rule buildXorInsideXnorRule(String ruleName) {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);

        return rule(ruleName).build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                noneOrAll(
                    oneOf(toyNamed(alarmA, "alarmA"), toyNamed(alarmB, "alarmB")),
                    toyNamed(alarmC, "alarmC")
                ),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );
    }

    @Test
    public void xorInsideXnor_noAlarms_fires() {
        // XOR(A,B): 0 of 2 → UNMATCHED; C: unmatched → outer XNOR sees 0 of 2 matched → MATCHED.
        ksession = makeKSession(buildXorInsideXnorRule("xnor-xor-none"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void xorInsideXnor_onlyAlarmA_doesNotFire() {
        // XOR(A,B): 1 of 2 → MATCHED; C: unmatched → outer XNOR sees 1 of 2 matched → UNMATCHED.
        ksession = makeKSession(buildXorInsideXnorRule("xnor-xor-A"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("ack"));
        assertThat(results).isEmpty();
    }

    @Test
    public void xorInsideXnor_onlyAlarmC_doesNotFire() {
        // XOR(A,B): 0 of 2 → UNMATCHED; C: matched → outer XNOR sees 1 of 2 matched → UNMATCHED.
        ksession = makeKSession(buildXorInsideXnorRule("xnor-xor-C"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmC"));
        insertAndFire(new Toy("ack"));
        assertThat(results).isEmpty();
    }

    @Test
    public void xorInsideXnor_alarmAAndC_fires() {
        // XOR(A,B): 1 of 2 → MATCHED; C: matched → outer XNOR sees 2 of 2 matched → MATCHED.
        ksession = makeKSession(buildXorInsideXnorRule("xnor-xor-A-C"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("alarmC"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void xorInsideXnor_alarmAandB_xorOvermatch_resetsSequence_doesNotFire() {
        // XOR(A,B): A makes it MATCHED (step advances to ack), then B over-matches.
        // Per ADR-0004: XOR MATCHED→UNMATCHED transition fires VetoSignalProcessor,
        // which resets the enclosing sequence to step 0.
        // The outer XNOR never gets a chance to re-evaluate — the sequence is already reset.
        // ack then arrives at step 0 (waiting for start) → rule must NOT fire.
        ksession = makeKSession(buildXorInsideXnorRule("xnor-xor-A-B"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // XOR: 1 of 2 → MATCHED → sequence advances to ack step
        insertAndFire(new Toy("alarmB")); // XOR: over-match → veto → sequence resets to step 0
        insertAndFire(new Toy("ack"));    // sequence at step 0, ack has no effect → does not fire
        assertThat(results).isEmpty();
    }

    @Test
    public void xorInsideXnor_alarmAandBThenC_xorOvermatch_resetsSequence_doesNotFire() {
        // XOR(A,B): A → MATCHED (sequence advances); B → over-match → veto → sequence resets to step 0.
        // Per ADR-0004 the reset happens before alarmC or ack can advance anything.
        // alarmC and ack then arrive at step 0 (waiting for start) → rule does NOT fire.
        ksession = makeKSession(buildXorInsideXnorRule("xnor-xor-A-B-C"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // XOR MATCHED → sequence advances to ack step
        insertAndFire(new Toy("alarmB")); // XOR over-match → veto → sequence resets to step 0
        insertAndFire(new Toy("alarmC")); // arrives at step 0 — no effect
        insertAndFire(new Toy("ack"));    // arrives at step 0 — no effect → does not fire
        assertThat(results).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // xnor with the veto / absent() path
    // ---------------------------------------------------------------------------------------------

    private Rule buildXnorWithAbsentRule(String ruleName) {
        Variable<Toy> alarmA  = declarationOf(Toy.class);
        Variable<Toy> alarmB  = declarationOf(Toy.class);
        Variable<Toy> blocker = declarationOf(Toy.class);

        return rule(ruleName).build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                noneOrAll(toyNamed(alarmA, "alarmA"), toyNamed(alarmB, "alarmB")),
                absent(toyNamed(blocker, "blocker")),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );
    }

    @Test
    public void noneOrAllWithAbsent_noAlarmsNoBlocker_fires() {
        ksession = makeKSession(buildXnorWithAbsentRule("xnor-absent-none"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void noneOrAllWithAbsent_noAlarmsBlockerArrives_doesNotFire() {
        ksession = makeKSession(buildXnorWithAbsentRule("xnor-absent-blocked"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("blocker"));
        insertAndFire(new Toy("ack"));
        assertThat(results).isEmpty();
    }

    @Test
    public void noneOrAllWithAbsent_oneAlarmNoBlocker_doesNotFire() {
        ksession = makeKSession(buildXnorWithAbsentRule("xnor-absent-one"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("ack"));
        assertThat(results).isEmpty();
    }

    @Test
    public void noneOrAllWithAbsent_bothAlarmsNoBlocker_fires() {
        ksession = makeKSession(buildXnorWithAbsentRule("xnor-absent-both"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("alarmB"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void noneOrAllWithAbsent_bothAlarmsThenBlocker_doesNotFire() {
        ksession = makeKSession(buildXnorWithAbsentRule("xnor-absent-both-blocked"));
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA"));
        insertAndFire(new Toy("alarmB"));
        insertAndFire(new Toy("blocker"));
        insertAndFire(new Toy("ack"));
        assertThat(results).isEmpty();
    }

    @Test
    public void nestedXnorWithAbsent_alarmCOnlyNoBlocker_fires() {
        // sequence(start, noneOrAll(noneOrAll(A,B), C), absent(blocker), ack): alarmC only -> outer MATCHED.
        Variable<Toy> alarmA  = declarationOf(Toy.class);
        Variable<Toy> alarmB  = declarationOf(Toy.class);
        Variable<Toy> alarmC  = declarationOf(Toy.class);
        Variable<Toy> blocker = declarationOf(Toy.class);

        Rule rule = rule("nested-xnor-absent").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                noneOrAll(
                    noneOrAll(toyNamed(alarmA, "alarmA"), toyNamed(alarmB, "alarmB")),
                    toyNamed(alarmC, "alarmC")
                ),
                absent(toyNamed(blocker, "blocker")),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmC"));
        insertAndFire(new Toy("ack"));
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void nestedXnorWithAbsent_alarmCOnlyThenBlocker_doesNotFire() {
        Variable<Toy> alarmA  = declarationOf(Toy.class);
        Variable<Toy> alarmB  = declarationOf(Toy.class);
        Variable<Toy> alarmC  = declarationOf(Toy.class);
        Variable<Toy> blocker = declarationOf(Toy.class);

        Rule rule = rule("nested-xnor-absent-blocked").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                noneOrAll(
                    noneOrAll(toyNamed(alarmA, "alarmA"), toyNamed(alarmB, "alarmB")),
                    toyNamed(alarmC, "alarmC")
                ),
                absent(toyNamed(blocker, "blocker")),
                pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
            ),
            execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmC"));
        insertAndFire(new Toy("blocker"));
        insertAndFire(new Toy("ack"));
        assertThat(results).isEmpty();
    }

    @Test
    public void wrappedTrailingRevertingGate_throws() {
        // sequence(..., and(noneOrAll(a, b))) has no positive trigger in the trailing step.
        // and(noneOrAll(...)) wraps a reverting gate; it must be rejected at rule build time.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        assertThatThrownBy(() -> rule("wrapped-xnor-rejected").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                and(
                    noneOrAll(
                        pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                        pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                    )
                )
            ),
            execute(() -> results.add("fired"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing absent()/oneOf()/noneOrAll() requires a following positive step");
    }

    private static PatternDSL.PatternDef<Toy> toyNamed(Variable<Toy> var, String name) {
        return pattern(var).expr("is" + name, t -> t.getName().equals(name));
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
        return kieBase.newKieSession();
    }
}
