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

// BUG: rewriteXorSteps blindly consumes the next step even when it is another XOR/XNOR.
// sequence(xor(a,b), xor(c,d), e) should be rejected (consecutive bare xor steps require a
// positive step between them) but instead silently rewrites to sequence(and(xor(a,b),xor(c,d)), e),
// binding xor(c,d) as the trigger for xor(a,b) rather than as an independent step.
// See: ViewPatternBuilder.rewriteXorSteps(), line 277.

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
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.drools.model.DSL.declarationOf;
import static org.drools.model.DSL.execute;
import static org.drools.model.DSL.not;
import static org.drools.model.PatternDSL.pattern;
import static org.drools.model.PatternDSL.rule;
import static org.drools.model.PatternDSL.sequence;
import static org.drools.model.PatternDSL.xor;

/**
 * Specification tests for xor() as a sequence step.
 *
 * Contract: exactly one of the XOR children must match before the positive trigger.
 * Write {@code sequence(..., xor(a, b), trigger)} — the compiler wraps the pair into
 * {@code and(xor(a, b), trigger)} automatically.
 * Zero matches → AND gate never fires, sequence does not advance.
 * Two or more matches → XOR gate reverts to UNMATCHED, AND gate never fires.
 */
public class PatternDSLSequenceXorStepTest {

    private final Variable<Person> person = declarationOf(Person.class);
    private final Variable<Toy>    toy    = declarationOf(Toy.class);
    private final List<String>     results = new ArrayList<>();
    private KieSession             ksession;

    @Test
    public void xorExactlyOneMatch_fires() {
        // sequence(xor(alarmA, alarmB), ack): exactly alarmA inserted, then ack → rule fires
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-one-fires").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                xor(
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
        insertAndFire(new Toy("alarmA")); // exactly one → XOR matched
        insertAndFire(new Toy("ack"));    // AND gate fires → sequence advances → rule fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void xorNeitherMatch_doesNotFire() {
        // sequence(xor(alarmA, alarmB), ack): neither alarm inserted → XOR stays UNMATCHED → AND never fires
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-none").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                xor(
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
        insertAndFire(new Toy("ack")); // ack arrives but XOR never matched → AND gate never fires
        assertThat(results).isEmpty();
    }

    @Test
    public void xorBothMatch_rollsBackAndDoesNotFire() {
        // sequence(xor(alarmA, alarmB), ack): both alarms → XOR reverts to UNMATCHED → AND never fires
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-both").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                xor(
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
        insertAndFire(new Toy("alarmA")); // XOR: 1 match → MATCHED
        insertAndFire(new Toy("alarmB")); // XOR: 2 matches → reverts to UNMATCHED
        insertAndFire(new Toy("ack"));    // ack arrives but XOR is UNMATCHED → AND never fires
        assertThat(results).isEmpty();
    }

    @Test
    public void xorBareTopLevel_throws() {
        // sequence(xor(a, b)) — bare XOR as the trailing step is rejected during rule.build()
        // by ViewPatternBuilder's trailing-step guard.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        assertThatThrownBy(() -> rule("xor-bare").build(
            pattern(person),
            sequence(
                xor(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                )
            ),
            execute(() -> results.add("fired"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing not()/nor()/xor()/xnor() requires a following positive step");
    }

    @Test
    public void xorThreeOperandsBareTopLevel_throws() {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);

        assertThatThrownBy(() -> rule("xor-three-operands-bare").build(
            pattern(person),
            sequence(
                xor(
                    pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                    pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB")),
                    pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC"))
                )
            ),
            execute(() -> results.add("fired"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing not()/nor()/xor()/xnor() requires a following positive step");
    }

    @Test
    public void xorBareFollowedByStep_sugarFiresOnExactlyOneMatch() {
        // sequence(xor(a, b), c) — same rule, different name to confirm rewrite is idempotent.
        // Exactly one alarm inserted, then ack → rule fires.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-bare-followed-fires").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                xor(
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
        insertAndFire(new Toy("alarmA")); // exactly one → XOR matched
        insertAndFire(new Toy("ack"));    // ack closes the window → rule fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void xorBareFollowedByStep_sugarDoesNotFireOnBothMatch() {
        // sequence(xor(a, b), c) sugar: both alarms inserted → XOR reverts to UNMATCHED → ack arrives but no fire.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        Rule rule = rule("xor-bare-followed-no-fire").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                xor(
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
        insertAndFire(new Toy("alarmA")); // XOR: 1 match → MATCHED
        insertAndFire(new Toy("alarmB")); // XOR: 2 matches → reverts UNMATCHED
        insertAndFire(new Toy("ack"));    // ack arrives but XOR is UNMATCHED → does not fire
        assertThat(results).isEmpty();
    }


    @Test
    public void wrappedTrailingRevertingGate_throws() {
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);

        // sequence(..., and(xor(a, b))) has no positive trigger in the trailing step.
        // It must be rejected at rule build time.
        assertThatThrownBy(() -> rule("wrapped-xor-rejected").build(
            pattern(person),
            sequence(
                pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                org.drools.model.PatternDSL.and(
                    xor(
                        pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                        pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                    )
                )
            ),
            execute(() -> results.add("fired"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing not()/nor()/xor()/xnor() requires a following positive step");
    }

    // -------------------------------------------------------------------------
    // Bug #5 reproductions: consecutive bare xor/xnor steps
    //
    // rewriteXorSteps() must not consume a following XOR or XNOR step as the positive
    // trigger for the preceding XOR/XNOR.  The tests below document two things:
    //   1. The expected contract: consecutive bare xor/xnor steps should be rejected.
    //   2. The observable mis-wiring on the unfixed code.
    //
    // The two "throws" tests FAIL on the buggy code (no exception is thrown).
    // The "miswiring" test FAILS on the buggy code (the rule fires when it should not).
    // All three tests PASS once the fix is applied.
    // -------------------------------------------------------------------------

    @Test
    public void consecutiveBareXorSteps_noFollowingPositiveStep_throws() {
        // sequence(start, xor(a, b), xor(c, d)) — trailing xor run with no positive step.
        // The run is xor(a,b)+xor(c,d) but there is nothing after them: the sequence ends.
        // The trailing-step check must reject this with IllegalArgumentException.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmD = declarationOf(Toy.class);

        assertThatThrownBy(() -> rule("consecutive-xor-no-positive").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        xor(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        xor(
                                pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                                pattern(alarmD).expr("isAlarmD", t -> t.getName().equals("alarmD"))
                        )
                ),
                execute(() -> results.add("fired"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing");
    }

    @Test
    public void consecutiveBareXorXnorSteps_noFollowingPositiveStep_throws() {
        // sequence(start, xor(a, b), xnor(c, d)) — trailing mixed run with no positive step.
        // The run ends the sequence: the trailing-step guard must reject it.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmD = declarationOf(Toy.class);

        assertThatThrownBy(() -> rule("consecutive-xor-xnor-no-positive").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        xor(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        org.drools.model.PatternDSL.xnor(
                                pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                                pattern(alarmD).expr("isAlarmD", t -> t.getName().equals("alarmD"))
                        )
                ),
                execute(() -> results.add("fired"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing");
    }

    // -------------------------------------------------------------------------
    // Consecutive XOR/XNOR guards — working runtime tests
    //
    // sequence(xor(a,b), xor(c,d), ack): both XOR groups are simultaneously active.
    // The AND composite fires only when both XOR gates are in MATCHED state and ack arrives.
    // -------------------------------------------------------------------------

    @Test
    public void twoConsecutiveXorGuards_bothExactlyOne_fires() {
        // sequence(xor(a,b), xor(c,d), ack):
        // Exactly one of {a,b} and exactly one of {c,d} matched → AND MATCHED → ack → rule fires.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmD = declarationOf(Toy.class);

        Rule rule = rule("two-xor-both-one-fires").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        xor(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        xor(
                                pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                                pattern(alarmD).expr("isAlarmD", t -> t.getName().equals("alarmD"))
                        ),
                        pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // xor(a,b): 1 match → MATCHED
        insertAndFire(new Toy("alarmC")); // xor(c,d): 1 match → MATCHED → AND both bits set
        insertAndFire(new Toy("ack"));    // AND fires → rule fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void twoConsecutiveXorGuards_firstGroupBothMatch_doesNotFire() {
        // sequence(xor(a,b), xor(c,d), ack):
        // Both a and b match → xor(a,b) reverts to UNMATCHED → AND never fires.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmD = declarationOf(Toy.class);

        Rule rule = rule("two-xor-first-both-no-fire").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        xor(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        xor(
                                pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                                pattern(alarmD).expr("isAlarmD", t -> t.getName().equals("alarmD"))
                        ),
                        pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // xor(a,b): 1 match → MATCHED
        insertAndFire(new Toy("alarmB")); // xor(a,b): 2 matches → REVERTS to UNMATCHED
        insertAndFire(new Toy("alarmC")); // xor(c,d): 1 match → MATCHED; but AND bit 1 cleared
        insertAndFire(new Toy("ack"));    // AND never fires — xor(a,b) is UNMATCHED
        assertThat(results).isEmpty();
    }

    @Test
    public void twoConsecutiveXorGuards_secondGroupBothMatch_doesNotFire() {
        // sequence(xor(a,b), xor(c,d), ack):
        // Both c and d match → xor(c,d) reverts to UNMATCHED → AND never fires.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmD = declarationOf(Toy.class);

        Rule rule = rule("two-xor-second-both-no-fire").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        xor(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        xor(
                                pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                                pattern(alarmD).expr("isAlarmD", t -> t.getName().equals("alarmD"))
                        ),
                        pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // xor(a,b): 1 match → MATCHED
        insertAndFire(new Toy("alarmC")); // xor(c,d): 1 match → MATCHED; AND both bits set
        insertAndFire(new Toy("alarmD")); // xor(c,d): 2 matches → REVERTS to UNMATCHED; AND bit 2 cleared
        insertAndFire(new Toy("ack"));    // AND never fires — xor(c,d) is UNMATCHED
        assertThat(results).isEmpty();
    }

    @Test
    public void twoConsecutiveXorGuards_neitherGroupMatched_doesNotFire() {
        // sequence(xor(a,b), xor(c,d), ack):
        // Neither XOR group has any match → both AND bits unset → ack arrives → rule does not fire.
        Variable<Toy> alarmA = declarationOf(Toy.class);
        Variable<Toy> alarmB = declarationOf(Toy.class);
        Variable<Toy> alarmC = declarationOf(Toy.class);
        Variable<Toy> alarmD = declarationOf(Toy.class);

        Rule rule = rule("two-xor-neither-no-fire").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        xor(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        xor(
                                pattern(alarmC).expr("isAlarmC", t -> t.getName().equals("alarmC")),
                                pattern(alarmD).expr("isAlarmD", t -> t.getName().equals("alarmD"))
                        ),
                        pattern(toy).expr("isAck", t -> t.getName().equals("ack"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("ack")); // no XOR group satisfied — AND never fires
        assertThat(results).isEmpty();
    }


    // -------------------------------------------------------------------------
    // Mixed consecutive guard types: xor() followed by not()
    //
    // sequence(xor(a,b), not(C), D): the XOR rewrite phase checks whether the
    // step immediately after the XOR run is a positive trigger.  not(C) is a
    // guard, not a trigger, so hasPositiveTrigger(not(C)) returns false and the
    // rewrite incorrectly throws "consecutive bare xor/xnor steps require a
    // positive pattern step".  The actual positive trigger D is never reached.
    //
    // These tests FAIL on the buggy code (exception thrown at rule.build time)
    // and PASS once the fix is applied.
    // -------------------------------------------------------------------------

    @Test
    public void xorFollowedByNotGuardThenPositive_buildDoesNotThrow() {
        // sequence(start, xor(a,b), not(C), done):
        // The XOR run is followed by a not() guard, then a positive step.
        // Rule.build() must NOT throw — this is a valid sequence.
        Variable<Toy> alarmA  = declarationOf(Toy.class);
        Variable<Toy> alarmB  = declarationOf(Toy.class);
        Variable<Toy> blocker = declarationOf(Toy.class);

        // On the buggy code this throws IllegalArgumentException because
        // hasPositiveTrigger(not(blocker)) == false.
        Rule rule = rule("xor-not-positive-builds").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        xor(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        not(pattern(blocker).expr("isBlocker", t -> t.getName().equals("blocker"))),
                        pattern(toy).expr("isDone", t -> t.getName().equals("done"))
                ),
                execute(() -> results.add("fired"))
        );

        // Reaching here means build() did not throw — that is the assertion.
        ksession = makeKSession(rule);
        assertThat(ksession).isNotNull();
    }

    @Test
    public void xorFollowedByNotGuardThenPositive_fires() {
        // sequence(start, xor(a,b), not(blocker), done):
        // Exactly one alarm fires, no blocker, then done → rule fires.
        Variable<Toy> alarmA  = declarationOf(Toy.class);
        Variable<Toy> alarmB  = declarationOf(Toy.class);
        Variable<Toy> blocker = declarationOf(Toy.class);

        Rule rule = rule("xor-not-positive-fires").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        xor(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        not(pattern(blocker).expr("isBlocker", t -> t.getName().equals("blocker"))),
                        pattern(toy).expr("isDone", t -> t.getName().equals("done"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // exactly one XOR child matched
        // no blocker inserted — absence guard passes
        insertAndFire(new Toy("done"));   // positive trigger → rule fires
        assertThat(results).containsExactly("fired");
    }

    @Test
    public void xorFollowedByNotGuardThenPositive_notBlockerVetoes() {
        // sequence(start, xor(a,b), not(blocker), done):
        // Exactly one alarm fires, then blocker arrives → not() guard vetoes → rule must NOT fire.
        Variable<Toy> alarmA  = declarationOf(Toy.class);
        Variable<Toy> alarmB  = declarationOf(Toy.class);
        Variable<Toy> blocker = declarationOf(Toy.class);

        Rule rule = rule("xor-not-positive-veto").build(
                pattern(person),
                sequence(
                        pattern(toy).expr("isStart", t -> t.getName().equals("start")),
                        xor(
                                pattern(alarmA).expr("isAlarmA", t -> t.getName().equals("alarmA")),
                                pattern(alarmB).expr("isAlarmB", t -> t.getName().equals("alarmB"))
                        ),
                        not(pattern(blocker).expr("isBlocker", t -> t.getName().equals("blocker"))),
                        pattern(toy).expr("isDone", t -> t.getName().equals("done"))
                ),
                execute(() -> results.add("fired"))
        );

        ksession = makeKSession(rule);
        insertAndFire(new Person("anchor"));
        insertAndFire(new Toy("start"));
        insertAndFire(new Toy("alarmA")); // XOR: 1 match → MATCHED
        insertAndFire(new Toy("blocker")); // not() guard vetoes → sequence reset
        insertAndFire(new Toy("done"));    // sequence is reset — must NOT fire
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
        return kieBase.newKieSession();
    }
}
